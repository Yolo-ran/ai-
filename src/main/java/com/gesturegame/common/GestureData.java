package com.gesturegame.common;

import org.json.JSONObject;
/**
 * 手势数据类：单帧手部坐标 + 手势类型。
 *
 * <p>坐标全部归一化到 {@code 0.0~1.0}，左上角为原点（与 JavaFX Canvas 一致）。
 * 由 {@code network} 层从 WebSocket 收到的 JSON 解析而来，供大厅和游戏统一消费。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code handX/handY}         — 当前手坐标（归一化）</li>
 *   <li>{@code prevHandX/prevHandY} — 上一帧手坐标（用于轨迹/速度校验）</li>
 *   <li>{@code velocityX/velocityY} — 本帧与上帧的坐标差（正=向右/下）</li>
 *   <li>{@code gesture}             — 当前手势类型</li>
 *   <li>{@code confidence}          — 识别置信度 {@code 0.0~1.0}</li>
 *   <li>{@code handDetected}        — 本帧是否检测到手</li>
 * </ul>
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

    /** 无参构造，字段为安全默认值（手势 NONE，未检测到手）。 */
    public GestureData() {
        this.gesture = GestureType.NONE;
        this.handDetected = false;
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

    /**
     * 全参构造。
     *
     * @param handX         当前手 X 坐标
     * @param handY         当前手 Y 坐标
     * @param prevHandX     上一帧手 X 坐标
     * @param prevHandY     上一帧手 Y 坐标
     * @param velocityX     X 方向速度
     * @param velocityY     Y 方向速度
     * @param gesture       手势类型
     * @param confidence    识别置信度
     * @param handDetected  是否检测到手
     */
    public GestureData(double handX, double handY, double prevHandX, double prevHandY,
                       double velocityX, double velocityY, GestureType gesture,
                       double confidence, boolean handDetected) {
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

    /**
     * 从 WebSocket 收到的 JSON 字符串解析出 GestureData。
     *
     * <p>{@code gesture} 字段大小写不敏感（{@code "fist"}/{@code "FIST"} 均可）；
     * 缺失字段使用安全默认值，避免协议小幅变动时崩溃。
     *
     * @param json JSON 字符串
     * @return 解析后的 GestureData
     */
    public static GestureData fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        GestureType type = GestureType.fromString(obj.optString("gesture", "none"));
        return new GestureData(
                obj.optDouble("handX", 0.0),
                obj.optDouble("handY", 0.0),
                obj.optDouble("prevHandX", obj.optDouble("handX", 0.0)),
                obj.optDouble("prevHandY", obj.optDouble("handY", 0.0)),
                obj.optDouble("velocityX", 0.0),
                obj.optDouble("velocityY", 0.0),
                type,
                obj.optDouble("confidence", 0.0),
                obj.optBoolean("handDetected", false)
        );
    }

    /**
     * 序列化为 JSON 字符串，便于调试与 Java 端 mock 数据源使用。
     *
     * @return JSON 字符串
     */
    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("handX", handX);
        obj.put("handY", handY);
        obj.put("prevHandX", prevHandX);
        obj.put("prevHandY", prevHandY);
        obj.put("velocityX", velocityX);
        obj.put("velocityY", velocityY);
        obj.put("gesture", gesture == null ? "none" : gesture.name().toLowerCase());
        obj.put("confidence", confidence);
        obj.put("handDetected", handDetected);
        return obj.toString();
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
}
