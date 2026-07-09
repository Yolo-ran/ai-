package com.gesturegame.network;

import java.util.Locale;

/**
 * 手势命令解析器，负责把视觉端上报的原始手势字符串映射为统一命令。
 */
public final class GestureCommandResolver {

    private GestureCommandResolver() {
    }

    public static GestureCommand resolve(String rawGesture) {
        if (rawGesture == null || rawGesture.isBlank()) {
            return GestureCommand.NONE;
        }

        String normalized = rawGesture
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        switch (normalized) {
            case "SWIPE_LEFT":
            case "LEFT_SWIPE":
            case "SWIPE_TO_LEFT":
            case "MOVE_LEFT":
            case "LEFT":
                return GestureCommand.SWIPE_LEFT;
            case "SWIPE_RIGHT":
            case "RIGHT_SWIPE":
            case "SWIPE_TO_RIGHT":
            case "MOVE_RIGHT":
            case "RIGHT":
                return GestureCommand.SWIPE_RIGHT;
            case "CONFIRM":
            case "GRAB":
            case "GRASP":
            case "SELECT":
            case "PICK":
            case "PICK_UP":
            case "FIST":
            case "PINCH":
                return GestureCommand.CONFIRM;
            case "BACK":
            case "RETURN":
            case "CANCEL":
            case "RELEASE":
            case "OPEN":
            case "OPEN_PALM":
            case "PALM":
                return GestureCommand.BACK;
            default:
                return GestureCommand.NONE;
        }
    }
}
