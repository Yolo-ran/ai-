package com.gesturegame.common;

/**
 * 游戏难度等级。
 * 大厅选中游戏后进入难度选择界面，确认后传递给游戏。
 */
public enum Difficulty {
    EASY("简单", "⭐"),
    NORMAL("普通", "⭐⭐"),
    HARD("困难", "⭐⭐⭐"),
    ENDLESS("无尽", "♾️");

    private final String label;
    private final String stars;

    Difficulty(String label, String stars) {
        this.label = label;
        this.stars = stars;
    }

    public String getLabel() { return label; }
    public String getStars() { return stars; }
}
