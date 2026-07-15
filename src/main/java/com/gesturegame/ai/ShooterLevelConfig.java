package com.gesturegame.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 经严格限幅后的横版射击关卡配置。 */
public final class ShooterLevelConfig {

    public record Wave(int startSecond, int count, int hp, double speed, String formation, boolean shooter) {}

    private final int levelNumber;
    private final String title;
    private final int durationSeconds;
    private final int playerHp;
    private final int fireIntervalFrames;
    private final int bossHp;
    private final List<Wave> waves;
    private final String source;

    public ShooterLevelConfig(int levelNumber, String title, int durationSeconds, int playerHp,
                              int fireIntervalFrames, int bossHp, List<Wave> waves, String source) {
        this.levelNumber = clamp(levelNumber, 1, 99);
        this.title = safeTitle(title);
        this.durationSeconds = clamp(durationSeconds, 25, 75);
        this.playerHp = clamp(playerHp, 3, 8);
        this.fireIntervalFrames = clamp(fireIntervalFrames, 35, 150);
        this.bossHp = clamp(bossHp, 20, 240);
        this.waves = List.copyOf(waves == null ? List.of() : waves.subList(0, Math.min(8, waves.size())));
        this.source = source == null || source.isBlank() ? "本地规则" : source;
    }

    public static ShooterLevelConfig fromJson(JSONObject json, int expectedLevel, String source) {
        int durationSeconds = clamp(json.optInt("durationSeconds", 42), 25, 75);
        JSONArray waveArray = json.optJSONArray("waves");
        List<Wave> parsed = new ArrayList<>();
        if (waveArray != null) {
            for (int i = 0; i < Math.min(8, waveArray.length()); i++) {
                JSONObject wave = waveArray.optJSONObject(i);
                if (wave == null) continue;
                String formation = wave.optString("formation", "LINE").toUpperCase();
                if (!formation.equals("LINE") && !formation.equals("ZIGZAG") && !formation.equals("RANDOM")) {
                    formation = "LINE";
                }
                parsed.add(new Wave(
                        clamp(wave.optInt("startSecond", 3 + i * 5), 1, durationSeconds - 3),
                        clamp(wave.optInt("count", 4), 2, 12),
                        clamp(wave.optInt("hp", 2), 1, 8),
                        clamp(wave.optDouble("speed", 2.4), 1.2, 6.5),
                        formation,
                        wave.optBoolean("shooter", i > 1)));
            }
        }
        if (parsed.size() < 3) {
            throw new IllegalArgumentException("关卡 JSON 至少需要 3 个有效波次");
        }
        return new ShooterLevelConfig(
                expectedLevel,
                json.optString("title", "星际航线 " + expectedLevel),
                durationSeconds,
                json.optInt("playerHp", 5),
                json.optInt("enemyFireIntervalFrames", 90),
                json.optInt("bossHp", 45),
                parsed,
                source);
    }

    public int levelNumber() { return levelNumber; }
    public String title() { return title; }
    public int durationSeconds() { return durationSeconds; }
    public int playerHp() { return playerHp; }
    public int fireIntervalFrames() { return fireIntervalFrames; }
    public int bossHp() { return bossHp; }
    public List<Wave> waves() { return waves; }
    public String source() { return source; }

    private static String safeTitle(String value) {
        String text = value == null || value.isBlank() ? "星际航线" : value.strip();
        return text.length() > 18 ? text.substring(0, 18) : text;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
