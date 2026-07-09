package com.gesturegame.common;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 手势数据合同对象，描述 Python 识别端发给 Java 端的一帧标准数据。
 */
public class GestureData {

    private double handX;
    private double handY;
    private double prevHandX;
    private double prevHandY;
    private double velocityX;
    private double velocityY;
    private GestureType gesture;
    private double confidence;
    private boolean handDetected;

    public GestureData() {
        this.gesture = GestureType.NONE;
    }

    public GestureData(double handX,
                       double handY,
                       double prevHandX,
                       double prevHandY,
                       double velocityX,
                       double velocityY,
                       GestureType gesture,
                       boolean handDetected) {
        this(handX, handY, prevHandX, prevHandY, velocityX, velocityY, gesture, 0.0, handDetected);
    }

    public GestureData(double handX,
                       double handY,
                       double prevHandX,
                       double prevHandY,
                       double velocityX,
                       double velocityY,
                       GestureType gesture,
                       double confidence,
                       boolean handDetected) {
        this.handX = handX;
        this.handY = handY;
        this.prevHandX = prevHandX;
        this.prevHandY = prevHandY;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.gesture = gesture;
        this.confidence = confidence;
        this.handDetected = handDetected;
    }

    public static GestureData fromJson(String json) {
        JSONObject object = new JSONObject(json);
        GestureData data = new GestureData();
        data.setHandX(object.optDouble("handX", 0.0));
        data.setHandY(object.optDouble("handY", 0.0));
        data.setPrevHandX(object.optDouble("prevHandX", data.getHandX()));
        data.setPrevHandY(object.optDouble("prevHandY", data.getHandY()));
        data.setVelocityX(object.optDouble("velocityX", 0.0));
        data.setVelocityY(object.optDouble("velocityY", 0.0));
        data.setGesture(parseGesture(object.optString("gesture", "none")));
        data.setConfidence(object.optDouble("confidence", 0.0));
        data.setHandDetected(object.optBoolean("handDetected", false));
        return data;
    }

    public double getHandX() {
        return handX;
    }

    public void setHandX(double handX) {
        this.handX = handX;
    }

    public double getHandY() {
        return handY;
    }

    public void setHandY(double handY) {
        this.handY = handY;
    }

    public double getPrevHandX() {
        return prevHandX;
    }

    public void setPrevHandX(double prevHandX) {
        this.prevHandX = prevHandX;
    }

    public double getPrevHandY() {
        return prevHandY;
    }

    public void setPrevHandY(double prevHandY) {
        this.prevHandY = prevHandY;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(double velocityX) {
        this.velocityX = velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(double velocityY) {
        this.velocityY = velocityY;
    }

    public GestureType getGesture() {
        return gesture;
    }

    public void setGesture(GestureType gesture) {
        this.gesture = gesture;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isHandDetected() {
        return handDetected;
    }

    public void setHandDetected(boolean handDetected) {
        this.handDetected = handDetected;
    }

    private static GestureType parseGesture(String rawGesture) {
        if (rawGesture == null || rawGesture.isBlank()) {
            return GestureType.NONE;
        }

        switch (rawGesture.trim().toLowerCase(Locale.ROOT)) {
            case "fist":
                return GestureType.FIST;
            case "open":
                return GestureType.OPEN;
            case "peace":
                return GestureType.PEACE;
            case "pointing":
                return GestureType.POINTING;
            default:
                return GestureType.NONE;
        }
    }
}
