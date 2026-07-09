package com.gesturegame.common;

/**
 * 手势类型枚举，作为 Python 与 Java 间通信协议的统一合同。
 *
 * <p>与 Python 端约定：Python 可发送小写或大写字符串（{@code "fist"}/{@code "FIST"} 等），
 * Java 端统一按大写匹配枚举名，避免大小写不一致导致识别失败。
 *
 * <ul>
 *   <li>{@link #NONE}     — 未检测到手</li>
 *   <li>{@link #FIST}     — 握拳（石头）</li>
 *   <li>{@link #OPEN}     — 张开手掌（布）</li>
 *   <li>{@link #PEACE}    — 两根手指（剪刀）</li>
 *   <li>{@link #POINTING} — 一根手指（指向）</li>
 * </ul>
 */
public enum GestureType {
    NONE,
    FIST,
    OPEN,
    PEACE,
    POINTING;

    /**
     * 按字符串解析手势类型，大小写不敏感；无法识别或为 null 时返回 {@link #NONE}。
     *
     * @param value 手势字符串（如 {@code "fist"} / {@code "FIST"} / {@code "open"}）
     * @return 对应的枚举值，未知返回 {@link #NONE}
     */
    public static GestureType fromString(String value) {
        if (value == null) {
            return NONE;
        }
        try {
            return GestureType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
