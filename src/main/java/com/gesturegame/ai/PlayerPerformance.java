package com.gesturegame.ai;

import org.json.JSONObject;

/** 一局横版射击的玩家表现摘要，仅包含生成下一关所需的数据。 */
public record PlayerPerformance(
        int level,
        int score,
        double accuracy,
        int damageTaken,
        int enemiesDestroyed,
        int elapsedSeconds,
        boolean cleared) {

    public JSONObject toJson() {
        return new JSONObject()
                .put("level", level)
                .put("score", score)
                .put("accuracy", Math.round(accuracy * 1000.0) / 1000.0)
                .put("damageTaken", damageTaken)
                .put("enemiesDestroyed", enemiesDestroyed)
                .put("elapsedSeconds", elapsedSeconds)
                .put("cleared", cleared);
    }
}
