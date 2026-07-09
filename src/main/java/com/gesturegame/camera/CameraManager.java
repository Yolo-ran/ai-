package com.gesturegame.camera;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 本地摄像头管理器，负责打开默认摄像头并提供当前帧。
 */
public class CameraManager {

    private static final Logger LOGGER = Logger.getLogger(CameraManager.class.getName());
    private static final Dimension DEFAULT_RESOLUTION = WebcamResolution.QVGA.getSize();

    private Webcam webcam;

    public void start() {
        if (webcam != null && webcam.isOpen()) {
            return;
        }

        webcam = Webcam.getDefault();
        if (webcam == null) {
            LOGGER.warning("未检测到可用摄像头");
            return;
        }

        webcam.setViewSize(DEFAULT_RESOLUTION);
        webcam.open(true);
        LOGGER.info(() -> "本地摄像头已打开: " + webcam.getName());
    }

    public BufferedImage getFrame() {
        if (webcam == null || !webcam.isOpen()) {
            return null;
        }

        try {
            return webcam.getImage();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "读取摄像头画面失败", e);
            return null;
        }
    }

    public void stop() {
        if (webcam != null) {
            try {
                webcam.close();
                LOGGER.info("本地摄像头已关闭");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "关闭摄像头失败", e);
            }
        }
    }
}
