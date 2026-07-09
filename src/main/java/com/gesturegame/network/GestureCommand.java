package com.gesturegame.network;

/**
 * 统一的手势命令枚举，供 Socket 层和 UI 层共享。
 */
public enum GestureCommand {
    NONE,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    CONFIRM,
    BACK
}
