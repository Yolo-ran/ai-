package com.gesturegame.ai;

import com.gesturegame.common.Difficulty;

import java.util.ArrayList;
import java.util.List;

/** 无密钥、断网或 API 输出异常时的确定性关卡生成器。 */
public final class LocalShooterLevelGenerator {

    public ShooterLevelConfig generate(int level, Difficulty difficulty, PlayerPerformance performance) {
        double skill = performance == null ? 0.5
                : Math.max(0.0, Math.min(1.0, performance.accuracy() * 0.75
                + (performance.cleared() ? 0.2 : 0.0)
                - performance.damageTaken() * 0.025));
        double difficultyBoost = switch (difficulty) {
            case EASY -> -0.15;
            case HARD -> 0.2;
            case ENDLESS -> 0.3;
            default -> 0.0;
        };
        double pressure = Math.max(0.0, Math.min(1.0, skill + difficultyBoost + (level - 1) * 0.06));
        int duration = 36 + Math.min(18, level * 2);
        int waveCount = 4 + Math.min(3, level / 2);
        List<ShooterLevelConfig.Wave> waves = new ArrayList<>();
        for (int i = 0; i < waveCount; i++) {
            int start = 2 + i * Math.max(4, (duration - 5) / waveCount);
            int count = 4 + (i % 3) + (pressure > 0.65 ? 2 : 0);
            int hp = 1 + (int) Math.floor(pressure * 3.0) + (i == waveCount - 1 ? 1 : 0);
            double speed = 1.8 + pressure * 2.1 + i * 0.08;
            String formation = switch (i % 3) {
                case 1 -> "ZIGZAG";
                case 2 -> "RANDOM";
                default -> "LINE";
            };
            waves.add(new ShooterLevelConfig.Wave(start, count, hp, speed, formation,
                    i >= 1 && pressure > 0.25));
        }
        int playerHp = pressure > 0.75 ? 4 : 5;
        int fireInterval = (int) Math.round(115 - pressure * 55);
        int bossHp = 34 + level * 8 + (int) Math.round(pressure * 35);
        return new ShooterLevelConfig(level, "星际航线 · " + level, duration, playerHp,
                fireInterval, bossHp, waves, "本地动态生成");
    }
}
