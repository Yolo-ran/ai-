package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🥁 手势节奏大师 — 跳舞机风格（困难 ⭐⭐⭐）
 *
 * 玩法：手势图标从画面底部向上滚动，到达判定圈时做出对应手势。
 *       就像跳舞机踩箭头，换成用手摆姿势。
 *
 * 画面布局：
 *   ┌──────────────────────────┐
 *   │                          │
 *   │      ✊  ← 下一个          │  手势图标从下往上飘
 *   │                          │
 *   │   ┌──────────┐           │
 *   │   │  ⭕ 判定圈  │  ← 固定   │  图标到达这里时做手势
 *   │   └──────────┘           │
 *   │                          │
 *   │   连击 x15  🔥            │
 *   │   分数: 12500             │
 *   └──────────────────────────┘
 *
 * 手势类型（3种就够了）：
 *   ✊ FIST     — 握拳
 *   ✋ OPEN     — 张开手掌
 *   👆 POINTING — 食指指向
 *
 * 你需要实现：
 * 1. Note 内部类（手势类型, y坐标, judged）
 * 2. 节拍序列：按时间顺序排队的手势列表
 * 3. 所有音符沿一条垂直轨道从下往上滚动
 * 4. 音符到达判定圈时检查玩家手势
 * 5. 判定：Perfect(±3帧) / Great(±6帧) / Miss
 * 6. Combo系统：连续命中加成
 *
 * 可用的手势数据：
 * - gesture.getGesture()   : 当前手势 FIST/OPEN/POINTING/NONE
 */
public class RhythmMaster implements GameInterface {

    private static final Random RANDOM = new Random();

    // ===== 画面配置 =====
    // ===== 判定窗口（由难度动态设置）=====

    // ===== 手势-图标映射 =====
    // FIST     → ✊ 握拳  （颜色：红色 #ef4444）
    // OPEN     → ✋ 张开  （颜色：蓝色 #3b82f6）
    // POINTING → 👆 指向  （颜色：绿色 #22c55e）

    private List<Note> notes;
    private List<FloatText> floatTexts;
    private java.util.Set<Integer> beatFrames;
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

    // ===== 判定圈和轨道参数 =====
    private double judgeCircleX;   // 判定圈X（画布中间）
    private double judgeCircleY;   // 判定圈Y
    private double judgeCircleR;   // 判定圈半径
    private double noteSpeed;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int gestureCount;      // 2或3种手势
    private int perfectWindow;     // Perfect判定窗口（帧）
    private int greatWindow;       // Great判定窗口（帧）
    private int noteIntervalMin;
    private int noteIntervalMax;
    private int gameDurationFrames;

    @Override
    public String getName() {
        return "节奏大师";
    }

    @Override
    public String getDescription() {
        return "手势跳舞机，按时摆出正确姿势！";
    }

    @Override
    public String getIcon() {
        return "🥁";
    }

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

        this.judgeCircleX = canvasWidth / 2.0;
        this.judgeCircleY = canvasHeight * 0.4;
        this.judgeCircleR = 50;

        this.notes = new ArrayList<>();
        this.floatTexts = new ArrayList<>();
        this.currentGesture = GestureType.NONE;
        this.handDetected = false;

        applyDifficulty();
        generateBeatMap();
    }

    @Override
    public void setDifficulty(Difficulty d) { this.difficulty = d; }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY:
                gestureCount = 2;
                noteSpeed = 2.1;
                perfectWindow = 8;
                greatWindow = 15;
                noteIntervalMin = 30; noteIntervalMax = 40;
                gameDurationFrames = 3600; // 60秒
                break;
            case NORMAL:
                gestureCount = 3;
                noteSpeed = 3.5;
                perfectWindow = 3;
                greatWindow = 6;
                noteIntervalMin = 18; noteIntervalMax = 25;
                gameDurationFrames = 5400; // 90秒
                break;
            case HARD:
                gestureCount = 3;
                noteSpeed = 5.0;
                perfectWindow = 2;
                greatWindow = 4;
                noteIntervalMin = 10; noteIntervalMax = 18;
                gameDurationFrames = 7200; // 120秒
                break;
        }
    }

    @Override
    public Difficulty getDifficulty() { return difficulty; }

    private void generateBeatMap() {
        // 生成节拍序列（记录每一帧是否生成音符）
        this.beatFrames = new java.util.HashSet<>();

        // 按难度生成节拍（三阶段递进）
        int phase1End = gameDurationFrames / 3;
        int phase2End = gameDurationFrames * 2 / 3;
        int range = noteIntervalMax - noteIntervalMin;
        int t = 0;
        while (t < phase1End) {
            beatFrames.add(t);
            t += noteIntervalMin + range + RANDOM.nextInt(range + 1); // 慢
        }
        while (t < phase2End) {
            beatFrames.add(t);
            t += noteIntervalMin + range / 2 + RANDOM.nextInt(range / 2 + 1); // 中
        }
        while (t < gameDurationFrames) {
            beatFrames.add(t);
            t += noteIntervalMin + RANDOM.nextInt(range / 2 + 1); // 快
        }
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        frameCount++;

        // 更新当前手势状态（供 render 使用）
        currentGesture = gesture.getGesture();
        handDetected = gesture.isHandDetected();

        // ===== 1. 生成新音符 =====
        if (beatFrames.contains(frameCount)) {
            GestureType[] types = {GestureType.FIST, GestureType.OPEN, GestureType.POINTING};
            GestureType type = types[RANDOM.nextInt(types.length)];
            Note note = new Note(type, canvasHeight + 50);
            notes.add(note);
        }

        // ===== 2. 更新所有音符 =====
        for (Note note : notes) {
            note.y -= noteSpeed;
        }

        // ===== 3. 判定逻辑 =====
        double perfectRange = perfectWindow * noteSpeed;
        double greatRange = greatWindow * noteSpeed;

        for (Note note : notes) {
            if (note.judged) continue;

            double dist = Math.abs(note.y - judgeCircleY);

            // Perfect判定：在判定圈附近且手势匹配
            if (dist <= perfectRange && gesture.getGesture() == note.gestureType) {
                note.judged = true;
                score += (int) (100 * getComboMultiplier());
                combo++;
                perfectCount++;
                floatTexts.add(new FloatText("PERFECT", judgeCircleX,
                        judgeCircleY - 40, 30, Color.GOLD));
            }
            // 飘过判定圈：Great 或 Miss
            else if (note.y < judgeCircleY - greatRange) {
                note.judged = true;
                if (gesture.getGesture() == note.gestureType) {
                    score += (int) (50 * getComboMultiplier());
                    combo++;
                    greatCount++;
                    floatTexts.add(new FloatText("GREAT", judgeCircleX,
                            judgeCircleY - 40, 30, Color.LIME));
                } else {
                    combo = 0;
                    missCount++;
                    floatTexts.add(new FloatText("MISS", judgeCircleX,
                            judgeCircleY - 40, 30, Color.RED));
                }
            }
        }

        // ===== 4. Combo加成 =====
        if (combo > maxCombo) maxCombo = combo;

        // ===== 5. 清除飞出屏幕的音符 =====
        notes.removeIf(n -> n.y < -100);

        // ===== 7. 更新飘字特效 =====
        for (FloatText ft : floatTexts) {
            ft.y -= 1.5;
            ft.life--;
        }
        floatTexts.removeIf(ft -> ft.life <= 0);

        if (frameCount >= gameDurationFrames) {
            over = true;
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // ===== 1. 画轨道背景 =====
        gc.setStroke(Color.rgb(255, 255, 255, 0.08));
        gc.setLineWidth(2);
        gc.strokeLine(judgeCircleX, canvasHeight, judgeCircleX, 0);

        // ===== 2. 画判定圈 =====
        boolean noteNearby = false;
        for (Note n : notes) {
            if (!n.judged && Math.abs(n.y - judgeCircleY) < noteSpeed * greatWindow * 3) {
                noteNearby = true;
                break;
            }
        }
        if (noteNearby) {
            gc.setStroke(Color.web("#deff9a"));
            gc.setLineWidth(5);
        } else {
            gc.setStroke(Color.web("#deff9a").deriveColor(0, 1, 1, 0.5));
            gc.setLineWidth(4);
        }
        gc.strokeOval(judgeCircleX - judgeCircleR, judgeCircleY - judgeCircleR,
                judgeCircleR * 2, judgeCircleR * 2);
        gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.6));
        gc.setFont(Font.font(14));
        gc.fillText("做手势！", judgeCircleX - 28, judgeCircleY + 5);

        // ===== 3. 画所有音符 =====
        double noteX = judgeCircleX;
        for (Note note : notes) {
            Color noteColor;
            String emoji;
            if (note.gestureType == GestureType.FIST) {
                noteColor = Color.web("#ef4444");
                emoji = "✊";
            } else if (note.gestureType == GestureType.OPEN) {
                noteColor = Color.web("#3b82f6");
                emoji = "✋";
            } else {
                noteColor = Color.web("#22c55e");
                emoji = "👆";
            }

            // 离判定圈越近越大
            double distToJudge = Math.abs(note.y - judgeCircleY);
            double scale = 1.0;
            double nearby = noteSpeed * greatWindow * 3;
            if (distToJudge < nearby) {
                scale = 1.0 + 0.3 * (1.0 - distToJudge / nearby);
            }

            double r = 30 * scale;
            gc.setFill(noteColor);
            gc.fillOval(noteX - r, note.y - r, r * 2, r * 2);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(28 * scale));
            gc.fillText(emoji, noteX - 14 * scale, note.y + 10 * scale);
        }

        // ===== 4. 画飘字特效 =====
        for (FloatText ft : floatTexts) {
            double alpha = Math.max(0.0, ft.life / 30.0);
            gc.setFill(ft.color.deriveColor(0, 1, 1, alpha));
            gc.setFont(Font.font(24));
            gc.fillText(ft.text, ft.x - 40, ft.y);
        }

        // ===== 5. 画音符预览 =====
        Note nextNote = null;
        for (Note n : notes) {
            if (!n.judged && n.y > judgeCircleY) {
                if (nextNote == null || n.y < nextNote.y) {
                    nextNote = n;
                }
            }
        }
        if (nextNote != null) {
            String previewEmoji;
            if (nextNote.gestureType == GestureType.FIST) previewEmoji = "✊";
            else if (nextNote.gestureType == GestureType.OPEN) previewEmoji = "✋";
            else previewEmoji = "👆";
            gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.5));
            gc.setFont(Font.font(16));
            gc.fillText("下一个 → " + previewEmoji, canvasWidth - 160, judgeCircleY - 80);
        }

        // ===== 6. HUD =====
        // 左上：分数 + combo
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(18));
        gc.fillText("分数: " + score, 20, 30);
        if (combo >= 10) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font(16));
            gc.fillText("🔥 " + combo + " combo  x" + String.format("%.1f", getComboMultiplier()),
                    20, 55);
        }

        // 右上：判定统计
        gc.setFill(Color.GOLD);
        gc.setFont(Font.font(16));
        gc.fillText("P:" + perfectCount, canvasWidth - 180, 30);
        gc.setFill(Color.LIME);
        gc.fillText("G:" + greatCount, canvasWidth - 120, 30);
        gc.setFill(Color.RED);
        gc.fillText("M:" + missCount, canvasWidth - 60, 30);

        // 底部：当前检测手势
        gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.5));
        gc.setFont(Font.font(16));
        if (handDetected) {
            String gestureName;
            if (currentGesture == GestureType.FIST) gestureName = "✊ FIST";
            else if (currentGesture == GestureType.OPEN) gestureName = "✋ OPEN";
            else if (currentGesture == GestureType.POINTING) gestureName = "👆 POINTING";
            else gestureName = "✋ 未知";
            gc.fillText("当前手势: " + gestureName, judgeCircleX - 60, canvasHeight - 30);
        } else {
            gc.fillText("未检测到手", judgeCircleX - 40, canvasHeight - 30);
        }

        // ===== 7. 进度条（顶部）=====
        double progress = (double) frameCount / gameDurationFrames;
        gc.setFill(Color.web("#deff9a"));
        gc.fillRect(0, 0, canvasWidth * progress, 3);

        // ===== 游戏结束画面 =====
        if (over) {
            // 半透明遮罩
            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);

            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Microsoft YaHei", 36));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("🎵 演奏结束！", canvasWidth / 2.0, canvasHeight / 2.0 - 60);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", 24));
            gc.fillText("总分: " + score, canvasWidth / 2.0, canvasHeight / 2.0);
            gc.fillText("最大连击: " + maxCombo, canvasWidth / 2.0, canvasHeight / 2.0 + 35);
            gc.fillText("Perfect:" + perfectCount + "  Great:" + greatCount + "  Miss:" + missCount,
                    canvasWidth / 2.0, canvasHeight / 2.0 + 70);

            gc.setFont(Font.font("Microsoft YaHei", 18));
            gc.fillText("✊握拳 重新开始 | ✋张开 返回大厅",
                    canvasWidth / 2.0, canvasHeight / 2.0 + 120);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    @Override
    public boolean isOver() {
        return over;
    }

    @Override
    public int getScore() {
        return score;
    }

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }

    // ==========================================
    // 内部类定义
    // ==========================================

    /** 音符：一个需要玩家在正确时机做出对应手势的音乐标记。 */
    private static class Note {
        GestureType gestureType;
        double y;
        boolean judged;

        Note(GestureType gestureType, double y) {
            this.gestureType = gestureType;
            this.y = y;
        }
    }

    /** 飘字特效：命中判定后向上飘动的文字。 */
    private static class FloatText {
        String text;
        double x, y;
        int life;
        Color color;

        FloatText(String text, double x, double y, int life, Color color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.life = life;
            this.color = color;
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /** Combo 分数倍率：10连1.2x, 30连1.5x, 50连2.0x, 100连3.0x。 */
    private double getComboMultiplier() {
        if (combo >= 100) return 3.0;
        if (combo >= 50) return 2.0;
        if (combo >= 30) return 1.5;
        if (combo >= 10) return 1.2;
        return 1.0;
    }
}
