package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🥁 手势节奏大师 — 多轨下落式
 *
 * 玩法：手势图标从顶部沿轨道下落，到达底部判定线时做出对应手势。
 *       EASY/NORMAL = 2轨（✊FIST + ✋OPEN），HARD = 3轨（+ ✌PEACE）
 *
 * 画面布局（3轨为例）：
 *   ┌──────────────────────────────────┐
 *   │         ✊        ✋        ✌      │  ← 图标从顶部掉落
 *   │         │        │        │      │
 *   │   ━━━━━━━━━━━━━━━━━━━━━━━━━━━   │  ← 底部判定线
 *   │      [FIST]   [OPEN]  [PEACE]   │  ← 轨道目标区
 *   │         ↑  手势光标  ↑           │  ← 手在哪一列
 *   └──────────────────────────────────┘
 */
public class RhythmMaster implements GameInterface {

    private static final Random RAND = new Random();

    // ===== 轨道配置 =====
    private int laneCount;          // EASY/NORMAL=2, HARD=3
    private double[] laneX;
    private double judgeLineY;
    private double laneWidth;
    private double targetZoneH;

    private List<Note> notes;
    private List<FloatText> floatTexts;
    private List<LaneHit> laneHits;
    private GestureType currentGesture = GestureType.NONE;
    private boolean handDetected;

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int combo;
    private int maxCombo;
    private boolean over;

    private int frameCount;
    private int perfectCount;
    private int greatCount;
    private int missCount;

    private double noteSpeed;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int perfectWindow;
    private int greatWindow;
    private int noteIntervalMin;
    private int noteIntervalMax;
    private int gameDurationFrames;
    private double[][] stars;
    private String performanceComment;

    // 手势光标
    private double handX = 0.5;
    private int activeLane = 0;
    private double cursorX;
    private double cursorAlpha;

    // 节拍调度
    private java.util.List<Integer> scheduledFrames;
    private int scheduleIdx;

    @Override public String getName() { return "节奏大师"; }
    @Override public String getDescription() { return "手势跳舞机，按时摆出正确姿势！"; }
    @Override public String getIcon() { return "🥁"; }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.combo = 0;
        this.maxCombo = 0;
        this.over = false;
        this.frameCount = 0;
        this.perfectCount = 0;
        this.greatCount = 0;
        this.missCount = 0;
        this.notes = new ArrayList<>();
        this.floatTexts = new ArrayList<>();
        this.laneHits = new ArrayList<>();
        this.currentGesture = GestureType.NONE;
        this.handDetected = false;

        applyDifficulty();  // 先设置 laneCount 等参数

        this.laneWidth = canvasWidth / (laneCount + 1.0);
        this.laneX = new double[laneCount];
        for (int i = 0; i < laneCount; i++) {
            laneX[i] = laneWidth * (i + 1);
        }

        this.judgeLineY = canvasHeight * 0.85;
        this.targetZoneH = canvasHeight * 0.08;

        this.handX = 0.5;
        this.activeLane = 0;
        this.cursorX = laneX[0];
        this.cursorAlpha = 0.0;

        generateBeatMap();
        initStars();
    }

    private void initStars() {
        stars = new double[100][3];
        for (int i = 0; i < 100; i++) {
            stars[i][0] = RAND.nextDouble() * canvasWidth;
            stars[i][1] = RAND.nextDouble() * canvasHeight;
            stars[i][2] = 0.3 + RAND.nextDouble() * 0.7;
        }
    }

    @Override
    public void setDifficulty(Difficulty d) { this.difficulty = d; }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY:
                laneCount = 2;
                noteSpeed = 3.0;
                perfectWindow = 30; greatWindow = 60;
                noteIntervalMin = 30; noteIntervalMax = 45;
                gameDurationFrames = 3600;
                break;
            case NORMAL:
                laneCount = 2;
                noteSpeed = 4.5;
                perfectWindow = 22; greatWindow = 48;
                noteIntervalMin = 20; noteIntervalMax = 30;
                gameDurationFrames = 5400;
                break;
            case HARD:
                laneCount = 3;
                noteSpeed = 6.5;
                perfectWindow = 15; greatWindow = 35;
                noteIntervalMin = 10; noteIntervalMax = 18;
                gameDurationFrames = 7200;
                break;
        }
    }

    @Override public Difficulty getDifficulty() { return difficulty; }
    @Override public boolean supportsDifficulty(Difficulty d) { return d != Difficulty.ENDLESS; }

    private void generateBeatMap() {
        int phase1End = gameDurationFrames / 3;
        int phase2End = gameDurationFrames * 2 / 3;
        int range = noteIntervalMax - noteIntervalMin;
        java.util.Set<Integer> beatFrames = new java.util.HashSet<>();
        int t = 60;
        while (t < phase1End) {
            beatFrames.add(t);
            t += noteIntervalMin + range + RAND.nextInt(range + 1);
        }
        while (t < phase2End) {
            beatFrames.add(t);
            t += noteIntervalMin + range / 2 + RAND.nextInt(range / 2 + 1);
        }
        while (t < gameDurationFrames) {
            beatFrames.add(t);
            t += noteIntervalMin + RAND.nextInt(range / 2 + 1);
        }
        this.scheduledFrames = new java.util.ArrayList<>(beatFrames);
        java.util.Collections.sort(this.scheduledFrames);
        this.scheduleIdx = 0;
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        frameCount++;
        currentGesture = gesture.getGesture();
        handDetected = gesture.isHandDetected();

        // 追踪手部 → 最近轨道
        if (handDetected) {
            handX = gesture.getHandX();
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < laneCount; i++) {
                double dist = Math.abs(handX * canvasWidth - laneX[i]);
                if (dist < minDist) { minDist = dist; activeLane = i; }
            }
            cursorAlpha = Math.min(1.0, cursorAlpha + 0.12);
        } else {
            cursorAlpha = Math.max(0.0, cursorAlpha - 0.06);
        }
        cursorX += (laneX[activeLane] - cursorX) * 0.2;

        // 1. 生成音符
        while (scheduleIdx < scheduledFrames.size() && scheduledFrames.get(scheduleIdx) == frameCount) {
            scheduleIdx++;
            int lane = RAND.nextInt(laneCount);
            GestureType[] types = getLaneGestureTypes();
            GestureType type = types[RAND.nextInt(types.length)];
            notes.add(new Note(type, lane, -60));
        }

        // 2. 更新音符位置
        for (Note note : notes) note.y += noteSpeed;

        // 3. 判定
        for (Note note : notes) {
            if (note.judged) continue;
            double dist = note.y - judgeLineY;

            if (dist > greatWindow) {
                note.judged = true;
                combo = 0; missCount++;
                floatTexts.add(new FloatText("MISS", laneX[note.lane], judgeLineY - 30, 30, Color.RED));
                laneHits.add(new LaneHit(note.lane, Color.RED));
            } else if (Math.abs(dist) <= perfectWindow &&
                       gesture.getGesture() == note.gestureType && gesture.getGesture() != GestureType.NONE) {
                note.judged = true;
                score += (int)(100 * getComboMultiplier());
                combo++; perfectCount++;
                floatTexts.add(new FloatText("PERFECT", laneX[note.lane], judgeLineY - 50, 30, Color.GOLD));
                laneHits.add(new LaneHit(note.lane, Color.GOLD));
            } else if (Math.abs(dist) <= greatWindow &&
                       gesture.getGesture() == note.gestureType && gesture.getGesture() != GestureType.NONE) {
                note.judged = true;
                score += (int)(50 * getComboMultiplier());
                combo++; greatCount++;
                floatTexts.add(new FloatText("GREAT", laneX[note.lane], judgeLineY - 50, 30, Color.LIME));
                laneHits.add(new LaneHit(note.lane, Color.LIME));
            }
        }

        if (combo > maxCombo) maxCombo = combo;

        // 4. 清理
        notes.removeIf(n -> n.y > canvasHeight + 80);
        for (FloatText ft : floatTexts) { ft.y -= 1.5; ft.life--; }
        floatTexts.removeIf(ft -> ft.life <= 0);
        for (LaneHit lh : laneHits) { lh.life--; }
        laneHits.removeIf(lh -> lh.life <= 0);

        // 5. 游戏结束
        if (frameCount >= gameDurationFrames) {
            over = true;
            performanceComment = generatePerformanceComment();
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        // 背景
        gc.setFill(Color.web("#05051a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        if (stars != null) {
            for (double[] s : stars) {
                gc.setFill(Color.rgb(180, 200, 255, s[2] * 0.5));
                gc.fillOval(s[0], s[1], 1.2, 1.2);
            }
        }

        // 轨道颜色
        Color[] laneColors = {
            Color.web("#ff4477"),  // FIST 红
            Color.web("#7c3aed"),  // OPEN 紫
            Color.web("#06b6d4"),  // PEACE 青
        };
        String[] laneLabels = getLaneLabels();

        // 1. 画轨道
        for (int i = 0; i < laneCount; i++) {
            double x = laneX[i];
            Color lc = laneColors[i];

            gc.setStroke(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.TRANSPARENT),
                    new Stop(0.3, lc.deriveColor(0, 1, 1, 0.6)),
                    new Stop(0.85, lc.deriveColor(0, 1, 1, 0.6)),
                    new Stop(1, Color.TRANSPARENT)));
            gc.setLineWidth(2);
            gc.setLineDashes(12, 8);
            gc.strokeLine(x, 0, x, canvasHeight);
            gc.setLineDashes(null);

            // 目标区
            double zoneAlpha = 0.2;
            for (Note n : notes) {
                if (!n.judged && n.lane == i && Math.abs(n.y - judgeLineY) < greatWindow * 2) {
                    zoneAlpha = 0.55; break;
                }
            }
            if (handDetected && activeLane == i) zoneAlpha = Math.max(zoneAlpha, 0.48);

            gc.setFill(lc.deriveColor(0, 1, 1, zoneAlpha));
            gc.fillRoundRect(x - laneWidth * 0.38, judgeLineY - targetZoneH,
                    laneWidth * 0.76, targetZoneH * 2, 12, 12);

            // 命中闪光
            for (LaneHit lh : laneHits) {
                if (lh.lane == i) {
                    gc.setFill(lh.color.deriveColor(0, 1, 1, lh.life / 12.0 * 0.6));
                    gc.fillRoundRect(x - laneWidth * 0.42, judgeLineY - targetZoneH * 1.2,
                            laneWidth * 0.84, targetZoneH * 2.4, 14, 14);
                }
            }

            // 标签
            gc.setFill(lc.deriveColor(0, 1, 1, 0.55));
            gc.setFont(Font.font(11));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(laneLabels[i], x, judgeLineY + targetZoneH + 16);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // 2. 判定线
        gc.setStroke(Color.rgb(255, 255, 255, 0.25));
        gc.setLineWidth(1.5);
        gc.strokeLine(laneWidth * 0.3, judgeLineY, canvasWidth - laneWidth * 0.3, judgeLineY);

        // 3. 手势光标
        if (cursorAlpha > 0.01) {
            double cx = cursorX, cy = judgeLineY + targetZoneH + 35, cr = 14;
            gc.setFill(Color.rgb(255, 255, 255, 0.18 * cursorAlpha));
            gc.fillOval(cx - cr - 12, cy - cr - 12, (cr + 12) * 2, (cr + 12) * 2);
            gc.setFill(Color.rgb(255, 255, 255, 0.7 * cursorAlpha));
            gc.fillOval(cx - cr, cy - cr, cr * 2, cr * 2);
            gc.setFill(Color.rgb(100, 200, 255, 0.8 * cursorAlpha));
            gc.fillOval(cx - cr * 0.5, cy - cr * 0.5, cr, cr);

            if (handDetected) {
                String emoji = gestureToEmoji(currentGesture);
                gc.setFill(Color.WHITE.deriveColor(0, 1, 1, cursorAlpha));
                gc.setFont(Font.font(16));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(emoji, cx, cy - 26);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }

        // 4. 音符
        for (Note note : notes) {
            if (note.judged) continue;
            double x = laneX[note.lane], y = note.y;
            Color nc = gestureToColor(note.gestureType);
            String emoji = gestureToEmoji(note.gestureType);

            double distToJudge = Math.abs(y - judgeLineY);
            double scale = 1.0;
            double nearby = greatWindow * 2;
            if (distToJudge < nearby) scale = 1.0 + 0.25 * (1.0 - distToJudge / nearby);

            double r = 26 * scale;
            gc.setFill(nc.deriveColor(0, 1, 1, 0.15));
            gc.fillOval(x - r - 8, y - r - 8, (r + 8) * 2, (r + 8) * 2);
            gc.setFill(nc.deriveColor(0, 1, 1, 0.85));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
            gc.setStroke(nc.deriveColor(0, 1, 1, 0.5));
            gc.setLineWidth(2);
            gc.strokeOval(x - r, y - r, r * 2, r * 2);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(24 * scale));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(emoji, x, y + 8 * scale);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // 5. 飘字
        for (FloatText ft : floatTexts) {
            gc.setFill(ft.color.deriveColor(0, 1, 1, Math.max(0, ft.life / 30.0)));
            gc.setFont(Font.font(22));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(ft.text, ft.x, ft.y);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // 6. 下一个预览
        Note nextNote = null;
        for (Note n : notes) {
            if (!n.judged && n.y < judgeLineY && (nextNote == null || n.y > nextNote.y)) nextNote = n;
        }
        if (nextNote != null) {
            gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.45));
            gc.setFont(Font.font(15));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("下一个 → " + gestureToEmoji(nextNote.gestureType), canvasWidth - 28, 50);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // 7. HUD
        gc.setFill(Color.color(0.04, 0.05, 0.16, 0.80));
        gc.fillRoundRect(22, 18, 235, 70, 18, 18);
        gc.setStroke(Color.color(0.58, 0.44, 1.0, 0.40));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(22, 18, 235, 70, 18, 18);
        gc.setFill(Color.web("#ddd6fe"));
        gc.setFont(Font.font("Microsoft YaHei UI", 20));
        gc.fillText("🥁  节奏大师", 40, 46);
        gc.setFill(Color.web("#a5b4fc"));
        gc.setFont(Font.font("Microsoft YaHei UI", 13));
        gc.fillText("得分  " + score + "    连击  " + combo, 40, 72);

        gc.setFill(Color.GOLD);  gc.setFont(Font.font(14));
        gc.fillText("P:" + perfectCount, canvasWidth - 150, 36);
        gc.setFill(Color.LIME);
        gc.fillText("G:" + greatCount, canvasWidth - 105, 36);
        gc.setFill(Color.RED);
        gc.fillText("M:" + missCount, canvasWidth - 60, 36);

        if (combo >= 10) {
            gc.setFill(Color.GOLD.deriveColor(0, 1, 1, 0.7));
            gc.setFont(Font.font(18));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("🔥 " + combo + " combo  x" + String.format("%.1f", getComboMultiplier()),
                    canvasWidth / 2.0, judgeLineY - targetZoneH - 50);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.4));
        gc.setFont(Font.font(14));
        gc.setTextAlign(TextAlignment.RIGHT);
        if (handDetected) {
            gc.fillText("当前手势: " + gestureToEmoji(currentGesture) + "  |  轨道: " + (activeLane + 1),
                    canvasWidth - 28, canvasHeight - 20);
        } else {
            gc.fillText("未检测到手", canvasWidth - 28, canvasHeight - 20);
        }
        gc.setTextAlign(TextAlignment.LEFT);

        // 8. 进度条
        double progress = (double) frameCount / gameDurationFrames;
        Color pc;
        if (progress < 0.5) pc = Color.web("#7c3aed");
        else if (progress < 0.8) pc = Color.web("#f59e0b");
        else pc = Color.web("#ef4444");
        gc.setFill(pc);
        gc.fillRect(0, 0, canvasWidth * progress, 3);
        int rem = Math.max(0, (gameDurationFrames - frameCount) / 60);
        gc.setFill(Color.rgb(200, 180, 255));
        gc.setFont(Font.font(14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("⏱ " + rem + "s", canvasWidth / 2.0, 24);
        gc.setTextAlign(TextAlignment.LEFT);

        // 9. 结束画面
        if (over) {
            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Microsoft YaHei", 36));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("🎵 演奏结束！", canvasWidth / 2.0, canvasHeight / 2.0 - 80);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", 24));
            gc.fillText("总分: " + score, canvasWidth / 2.0, canvasHeight / 2.0 - 20);
            gc.fillText("最大连击: " + maxCombo, canvasWidth / 2.0, canvasHeight / 2.0 + 15);
            gc.fillText("Perfect:" + perfectCount + "  Great:" + greatCount + "  Miss:" + missCount,
                    canvasWidth / 2.0, canvasHeight / 2.0 + 50);
            if (performanceComment != null) {
                gc.setFill(Color.web("#fbbf24"));
                gc.setFont(Font.font("Microsoft YaHei", 18));
                gc.fillText(performanceComment, canvasWidth / 2.0, canvasHeight / 2.0 + 90);
            }
            gc.setFont(Font.font("Microsoft YaHei", 18));
            gc.fillText("✊握拳 重新开始 | ✋张开 返回大厅", canvasWidth / 2.0, canvasHeight / 2.0 + 130);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    @Override public boolean isOver() { return over; }
    @Override public int getScore() { return score; }

    @Override public void reset() { init(canvasWidth, canvasHeight); }

    // ===== 辅助方法 =====

    /** 所有难度都使用3种手势 */
    private GestureType[] getLaneGestureTypes() {
        return new GestureType[]{GestureType.FIST, GestureType.OPEN, GestureType.PEACE};
    }

    /** 轨道标签（2轨用通用编号，3轨对应手势名） */
    private String[] getLaneLabels() {
        if (laneCount == 2) return new String[]{"左轨", "右轨"};
        return new String[]{"左轨", "中轨", "右轨"};
    }

    private Color gestureToColor(GestureType g) {
        if (g == GestureType.FIST) return Color.web("#ff4477");
        if (g == GestureType.OPEN) return Color.web("#7c3aed");
        return Color.web("#06b6d4");
    }

    private String gestureToEmoji(GestureType g) {
        if (g == GestureType.FIST) return "✊";
        if (g == GestureType.OPEN) return "✋";
        if (g == GestureType.PEACE) return "✌";
        return "👆";
    }

    private String generatePerformanceComment() {
        int total = perfectCount + greatCount + missCount;
        double acc = total > 0 ? (perfectCount * 1.0 + greatCount * 0.5) / total : 0;
        if (acc >= 0.9 && maxCombo >= 50) return "🌟 银河级演奏！你是星际最强的节奏大师！";
        if (acc >= 0.7 && maxCombo >= 20) return "🚀 不错的表演，宇宙已经听到了你的节拍！";
        if (acc >= 0.4) return "🌍 还行，再多练练就能飞出银河系了...";
        return "💫 再接再厉，星辰大海等你征服！";
    }

    private double getComboMultiplier() {
        if (combo >= 100) return 3.0;
        if (combo >= 50) return 2.0;
        if (combo >= 30) return 1.5;
        if (combo >= 10) return 1.2;
        return 1.0;
    }

    // ===== 内部类 =====

    private static class Note {
        GestureType gestureType; int lane; double y; boolean judged;
        Note(GestureType g, int lane, double y) { this.gestureType = g; this.lane = lane; this.y = y; }
    }

    private static class FloatText {
        String text; double x, y; int life; Color color;
        FloatText(String t, double x, double y, int l, Color c) {
            this.text = t; this.x = x; this.y = y; this.life = l; this.color = c;
        }
    }

    private static class LaneHit {
        int lane; int life = 12; Color color;
        LaneHit(int lane, Color c) { this.lane = lane; this.color = c; }
    }
}
