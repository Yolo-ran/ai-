package com.gesturegame.camera;

import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_core.inRange;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2HSV;
import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_ELLIPSE;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL;
import static org.bytedeco.opencv.global.opencv_imgproc.boundingRect;
import static org.bytedeco.opencv.global.opencv_imgproc.contourArea;
import static org.bytedeco.opencv.global.opencv_imgproc.convexHull;
import static org.bytedeco.opencv.global.opencv_imgproc.convexityDefects;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.erode;
import static org.bytedeco.opencv.global.opencv_imgproc.findContours;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

/**
 * 基于 OpenCV 的本地手势识别器。
 */
public class HandDetector {

    private static final Logger LOGGER = Logger.getLogger(HandDetector.class.getName());
    private static final double MIN_CONTOUR_AREA = 2200.0;
    private static final double MIN_DEFECT_DEPTH = 12.0;
    private static final int DETECT_WIDTH = 160;
    private static final int DETECT_HEIGHT = 120;

    private double prevHandX = 0.5;
    private double prevHandY = 0.5;
    private boolean previousDetected;

    public GestureData detect(BufferedImage frame) {
        if (frame == null) {
            return emptyData();
        }

        try {
            Mat source = bufferedImageToMat(frame);
            if (source == null || source.empty()) {
                return emptyData();
            }

            Mat detectFrame = new Mat();
            resize(source, detectFrame, new org.bytedeco.opencv.opencv_core.Size(DETECT_WIDTH, DETECT_HEIGHT));

            Mat hsv = new Mat();
            Mat mask = new Mat();
            Mat kernel = getStructuringElement(MORPH_ELLIPSE, new org.bytedeco.opencv.opencv_core.Size(5, 5));

            cvtColor(detectFrame, hsv, COLOR_BGR2HSV);
            Mat lowerBound = new Mat(hsv.rows(), hsv.cols(), hsv.type(), new Scalar(0, 20, 70, 0));
            Mat upperBound = new Mat(hsv.rows(), hsv.cols(), hsv.type(), new Scalar(20, 150, 255, 0));
            inRange(hsv, lowerBound, upperBound, mask);
            erode(mask, mask, kernel);
            dilate(mask, mask, kernel);
            dilate(mask, mask, kernel);
            erode(mask, mask, kernel);

            MatVector contours = new MatVector();
            findContours(mask.clone(), contours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

            int bestIndex = findLargestContourIndex(contours);
            if (bestIndex < 0) {
                return emptyData();
            }

            Mat contour = contours.get(bestIndex);
            double area = contourArea(contour);
            if (area < MIN_CONTOUR_AREA) {
                return emptyData();
            }

            Rect bounds = boundingRect(contour);
            double centerX = bounds.x() + bounds.width() / 2.0;
            double centerY = bounds.y() + bounds.height() / 2.0;
            double normalizedX = clamp(centerX / DETECT_WIDTH);
            double normalizedY = clamp(centerY / DETECT_HEIGHT);

            Mat hull = new Mat();
            convexHull(contour, hull, false, false);

            int validDefects = 0;
            if (hull.rows() >= 3) {
                Mat defects = new Mat();
                convexityDefects(contour, hull, defects);
                validDefects = countValidDefects(defects);
            }

            GestureType gestureType = classifyGesture(area, validDefects, bounds);

            double velocityX;
            double velocityY;
            if (previousDetected) {
                velocityX = (normalizedX - prevHandX) * frame.getWidth();
                velocityY = (normalizedY - prevHandY) * frame.getHeight();
            } else {
                velocityX = 0.0;
                velocityY = 0.0;
            }

            GestureData gestureData = new GestureData(
                    normalizedX,
                    normalizedY,
                    prevHandX,
                    prevHandY,
                    velocityX,
                    velocityY,
                    gestureType,
                    true
            );

            prevHandX = normalizedX;
            prevHandY = normalizedY;
            previousDetected = true;
            return gestureData;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "手势识别失败", e);
            return emptyData();
        }
    }

    private GestureType classifyGesture(double contourArea, int validDefects, Rect bounds) {
        double boundingArea = Math.max(1.0, bounds.width() * bounds.height());
        double fillRatio = contourArea / boundingArea;
        double aspectRatio = bounds.width() == 0 ? 1.0 : (double) bounds.height() / bounds.width();

        // 当前项目的核心交互主要依赖握拳确认，因此分类策略优先保证 FIST 可触发。
        if (validDefects >= 4 && fillRatio < 0.62) {
            return GestureType.OPEN;
        }
        if (validDefects == 2 || validDefects == 3) {
            if (fillRatio < 0.55) {
                return GestureType.OPEN;
            }
            return GestureType.PEACE;
        }
        if (validDefects <= 1) {
            if (aspectRatio > 2.05 && fillRatio < 0.50) {
                return GestureType.POINTING;
            }
            return GestureType.FIST;
        }
        return fillRatio < 0.52 ? GestureType.OPEN : GestureType.FIST;
    }

    private int countValidDefects(Mat defects) {
        if (defects == null || defects.empty()) {
            return 0;
        }

        int count = 0;
        for (int row = 0; row < defects.rows(); row++) {
            double depth = defects.ptr(row).getInt(3L * Integer.BYTES) / 256.0;
            if (depth > MIN_DEFECT_DEPTH) {
                count++;
            }
        }
        return count;
    }

    private int findLargestContourIndex(MatVector contours) {
        int bestIndex = -1;
        double bestArea = 0.0;
        for (int i = 0; i < contours.size(); i++) {
            double area = contourArea(contours.get(i));
            if (area > bestArea) {
                bestArea = area;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage bgrImage = image;
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage converted = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    BufferedImage.TYPE_3BYTE_BGR
            );
            java.awt.Graphics2D graphics = converted.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            bgrImage = converted;
        }

        byte[] pixels = ((DataBufferByte) bgrImage.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bgrImage.getHeight(), bgrImage.getWidth(), CV_8UC3);
        BytePointer dataPointer = mat.data();
        dataPointer.position(0).put(pixels);
        return mat;
    }

    private GestureData emptyData() {
        GestureData data = new GestureData(
                prevHandX,
                prevHandY,
                prevHandX,
                prevHandY,
                0.0,
                0.0,
                GestureType.NONE,
                false
        );
        previousDetected = false;
        return data;
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
