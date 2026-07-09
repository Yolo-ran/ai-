package com.gesturegame.common;

/**
 * 手势检测结果数据对象，包含坐标、速度和当前手势类型。
 */
public class GestureData {

    private double handX;
    private double handY;
    private double prevHandX;
    private double prevHandY;
    private double velocityX;
    private double velocityY;
    private GestureType gesture;
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
        this.handX = handX;
        this.handY = handY;
        this.prevHandX = prevHandX;
        this.prevHandY = prevHandY;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.gesture = gesture;
        this.handDetected = handDetected;
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

    public boolean isHandDetected() {
        return handDetected;
    }

    public void setHandDetected(boolean handDetected) {
        this.handDetected = handDetected;
    }
}
