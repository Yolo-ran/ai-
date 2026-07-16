package com.gesturegame.game;

import com.gesturegame.ai.OpponentAI;
import com.gesturegame.audio.SystemSpeech;
import com.gesturegame.common.Difficulty;
import javafx.application.Platform;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.persistence.RpsCsvStatsStore;
import com.gesturegame.persistence.RpsCsvStatsStore.Outcome;
import com.gesturegame.persistence.RpsCsvStatsStore.Summary;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ✂️ 剪刀石头布游戏（中等 ⭐⭐）
 *
 * 玩法：摄像头前出手势，和电脑AI对战，五局三胜制。
 *
 * 你需要实现：
 * 1. 倒计时状态机：WAITING → COUNTDOWN(3→2→1) → JUDGE → RESULT
 * 2. 电脑随机出拳
 * 3. 根据 gesture.getGesture() 判定玩家出拳
 * 4. 判定规则：石头>剪刀>布>石头
 * 5. 五局三胜，每局之间2秒间隔
 * 6. 手势映射：FIST=石头, OPEN=布, PEACE=剪刀
 *
 * 可用的手势数据：
 * - gesture.getGesture()      : 手势类型(FIST/OPEN/PEACE/NONE)
 * - gesture.isHandDetected()  : 是否检测到手
 */
public class RPSGame implements GameInterface {

    private static final Logger LOGGER = Logger.getLogger(RPSGame.class.getName());
    private static final Random RANDOM = new Random();

    // 游戏状态枚举
    private enum RPSState {
        WAITING,      // 等待出拳
        COUNTDOWN,    // 倒计时中（3-2-1）
        JUDGE,        // 判定阶段
        RESULT        // 显示结果
    }

    private int canvasWidth;
    private int canvasHeight;
    private int playerScore;
    private int computerScore;
    private int roundCount;
    private boolean over;

    private RPSState state;
    private int countdownFrames;
    private int resultFrames;
    private GestureType playerGesture;
    private int computerChoice;
    private String roundResult;
    private List<String> gameLog;
    private boolean beepFrame;
    private boolean roundJudged;
    private boolean finalSpeechAnnounced;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int totalRounds;
    private int cpuSmartLevel;
    private int[] playerGestureHistory = new int[3];
    private int initialCountdown;
    private String opponentLine;  // AI 对手本局台词
    private final RpsCsvStatsStore statsStore = new RpsCsvStatsStore();
    private Summary historySummary = Summary.empty();
    private boolean matchSaveAttempted;
    private boolean matchSaved;

    // 对抗界面视觉状态。只用于绘制，不参与猜拳判定。
    private static final int ELECTRIC_SEGMENTS = 48;
    private static final double[] ELECTRIC_AMPS = {0.4, -0.8, 0.6};
    private static final double[] ELECTRIC_FREQS = {0.7, 2.7, 3.9};
    private static final double[] ELECTRIC_SPEEDS = {-1.32, 0.42, 0.95};
    private static final double SCORE_PRESSURE_PER_POINT = 7.0;
    private static final double ROUND_IMPACT_STRENGTH = 6.0;
    private final double[] dividerX = new double[ELECTRIC_SEGMENTS + 1];
    private final double[] dividerY = new double[ELECTRIC_SEGMENTS + 1];
    private GestureType liveGesture = GestureType.NONE;
    private boolean liveHandDetected;
    private double dividerPosition = 60.0;
    private double roundImpact;
    private double countdownBeat;
    private double electricTime;
    private long lastRenderNanos;
    private long visualStartNanos;

    @Override
    public String getName() {
        return "猜拳";
    }

    @Override
    public String getDescription() {
        return "剪刀石头布对决，体验三秒倒计时出拳";
    }

    @Override
    public String getIcon() {
        return "✂️";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.playerScore = 0;
        this.computerScore = 0;
        this.roundCount = 0;
        this.over = false;
        this.state = RPSState.WAITING;
        this.countdownFrames = 0;
        this.resultFrames = 0;
        this.playerGesture = GestureType.NONE;
        this.computerChoice = 0;
        this.roundResult = "";
        this.opponentLine = null;
        this.gameLog = new ArrayList<>();
        this.beepFrame = false;
        this.roundJudged = false;
        this.finalSpeechAnnounced = false;
        this.playerGestureHistory = new int[3];
        this.matchSaveAttempted = false;
        this.matchSaved = false;
        this.liveGesture = GestureType.NONE;
        this.liveHandDetected = false;
        this.dividerPosition = 60.0;
        this.roundImpact = 0.0;
        this.countdownBeat = 0.0;
        this.electricTime = 0.0;
        this.lastRenderNanos = 0L;
        this.visualStartNanos = System.nanoTime();
        loadHistorySummary();
        applyDifficulty();
    }

    private void applyDifficulty() {
        // 倒计时 / AI 统一，仅总局数不同
        countdownFrames = 180;     // 3秒
        cpuSmartLevel = 0;         // 纯随机
        switch (difficulty) {
            case EASY:
                totalRounds = 3;    // 三局两胜
                break;
            case NORMAL:
                totalRounds = 5;    // 五局三胜
                break;
            case HARD:
            default:
                totalRounds = 7;    // 七局四胜
                break;
        }
    }

    @Override
    public void setDifficulty(Difficulty d) { this.difficulty = d; }

    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean supportsDifficulty(Difficulty d) {
        return d != Difficulty.ENDLESS;
    }

    @Override
    public String getDifficultyLabel(Difficulty d) {
        switch (d) {
            case EASY: return "三局两胜";
            case NORMAL: return "五局三胜";
            case HARD: return "七局四胜";
            default: return d.getLabel();
        }
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        liveHandDetected = gesture != null && gesture.isHandDetected();
        if (liveHandDetected && isRpsGesture(gesture.getGesture())) {
            liveGesture = gesture.getGesture();
        } else if (!roundJudged) {
            liveGesture = GestureType.NONE;
        }

        // === 状态机 ===

        if (state == RPSState.WAITING) {
            if (gesture.isHandDetected()) {
                applyDifficulty();
                initialCountdown = countdownFrames;
                computerChoice = getComputerChoice();
                playerGesture = GestureType.NONE; // 重置本局手势
                opponentLine = null;
                roundJudged = false;
                state = RPSState.COUNTDOWN;
                beepFrame = true;
            }
        } else if (state == RPSState.COUNTDOWN) {
            // 数字跳变时闪一下背景
            int prevNumber = (countdownFrames + (int)(initialCountdown / 3.0) - 1)
                    / (int)(initialCountdown / 3.0);
            countdownFrames--;
            int currNumber = countdownFrames > 0
                    ? (countdownFrames + (int)(initialCountdown / 3.0) - 1)
                      / (int)(initialCountdown / 3.0)
                    : 0;
            if (currNumber != prevNumber) {
                beepFrame = true;
            }
            if (countdownFrames <= 0) {
                countdownFrames = 0;
                state = RPSState.JUDGE;
            }
        } else if (state == RPSState.JUDGE) {
            // 已判定过 → 只做展示倒计时，不再重复判定
            if (roundJudged) {
                resultFrames--;
                if (resultFrames <= 0) {
                    state = RPSState.RESULT;
                    resultFrames = 60;
                }
            } else {
                // 首次判定：读取手势
                GestureType g = gesture.getGesture();
                int playerChoice = -1;
                if (g == GestureType.FIST) playerChoice = 0;
                else if (g == GestureType.PEACE) playerChoice = 1;
                else if (g == GestureType.OPEN) playerChoice = 2;

                if (playerChoice >= 0) {
                    judgeRound(g, playerChoice);
                } else {
                    timeoutRound();
                }
            }
        } else if (state == RPSState.RESULT) {
            resultFrames--;
            if (resultFrames <= 0) {
                int winThreshold = (totalRounds / 2) + 1;
                if (playerScore >= winThreshold || computerScore >= winThreshold
                        || roundCount >= totalRounds) {
                    over = true;
                    persistCompletedMatch();
                    announceFinalResult();
                } else {
                    state = RPSState.WAITING;
                }
            }
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        renderBattleUi(gc);
    }

    private void renderBattleUi(GraphicsContext gc) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }

        long now = System.nanoTime();
        double dt = lastRenderNanos == 0L
                ? 1.0 / 60.0
                : Math.min(0.05, Math.max(0.0, (now - lastRenderNanos) / 1_000_000_000.0));
        lastRenderNanos = now;
        electricTime += dt;

        if (beepFrame) {
            countdownBeat = 1.0;
            beepFrame = false;
        } else {
            countdownBeat *= Math.exp(-7.5 * dt);
        }

        // 单回合胜负只形成短促冲击，随后回归由总比分决定的长期战线。
        roundImpact *= Math.exp(-1.15 * dt);
        if (Math.abs(roundImpact) < 0.02) {
            roundImpact = 0.0;
        }

        double target = getDividerTarget();
        dividerPosition += (target - dividerPosition) * (1.0 - Math.exp(-6.0 * dt));
        buildDividerGeometry();

        gc.save();
        try {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);

            // 原版先绘制紫色右侧，再用波浪斜边裁出蓝色左侧。
            drawArenaBackground(gc, false);
            drawOpponentHud(gc);

            gc.save();
            clipPlayerSide(gc);
            drawArenaBackground(gc, true);
            drawPlayerHud(gc);
            gc.restore();

            drawDividerAtmosphere(gc);
            drawElectricDivider(gc);
            drawTopHud(gc);
            drawCenterState(gc);

            if (over) {
                drawMatchFinishedOverlay(gc);
            }

            // 与原版 motion.div 一致的 0.6 秒黑场淡入。
            double fade = clamp((now - visualStartNanos) / 600_000_000.0, 0.0, 1.0);
            if (fade < 1.0) {
                gc.setFill(Color.rgb(2, 3, 9, 1.0 - fade));
                gc.fillRect(0, 0, canvasWidth, canvasHeight);
            }
        } finally {
            gc.restore();
        }
    }

    private void drawArenaBackground(GraphicsContext gc, boolean playerSide) {
        Color deep = playerSide ? Color.web("#030917") : Color.web("#080311");
        Color middle = playerSide ? Color.web("#071b46") : Color.web("#17052b");
        Color edge = playerSide ? Color.web("#0b347c") : Color.web("#3b0a62");
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, deep), new Stop(0.55, middle), new Stop(1.0, edge)));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        double glowX = playerSide ? 0.20 : 0.82;
        Color glow = playerSide ? Color.rgb(28, 117, 255, 0.30) : Color.rgb(157, 55, 255, 0.28);
        gc.setFill(new RadialGradient(0, 0, glowX, 0.46, 0.58, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, glow), new Stop(0.46, glow.deriveColor(0, 1, 1, 0.26)),
                new Stop(1.0, Color.TRANSPARENT)));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 低对比度网格与扫描线，保持背景有深度但不吃性能。
        double grid = Math.max(42.0, Math.min(canvasWidth, canvasHeight) * 0.066);
        gc.setStroke(playerSide ? Color.rgb(85, 164, 255, 0.055) : Color.rgb(194, 122, 255, 0.052));
        gc.setLineWidth(0.7);
        double drift = (electricTime * 8.0) % grid;
        for (double x = -grid + drift; x < canvasWidth + grid; x += grid) {
            gc.strokeLine(x, 0, x - canvasHeight * 0.16, canvasHeight);
        }
        for (double y = drift; y < canvasHeight; y += grid) {
            gc.strokeLine(0, y, canvasWidth, y);
        }

        gc.setStroke(playerSide ? Color.rgb(130, 196, 255, 0.032) : Color.rgb(225, 178, 255, 0.032));
        gc.setLineWidth(1.0);
        for (double y = 8; y < canvasHeight; y += 18) {
            gc.strokeLine(0, y, canvasWidth, y);
        }
        drawBackgroundStreaks(gc, playerSide);
    }

    private void drawBackgroundStreaks(GraphicsContext gc, boolean playerSide) {
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        Color streak = playerSide ? Color.rgb(69, 155, 255, 0.14) : Color.rgb(184, 99, 255, 0.13);
        gc.setStroke(streak);
        for (int i = 0; i < 11; i++) {
            double phase = electricTime * (18 + i * 0.6) + i * 97.0;
            double y = (phase % (canvasHeight + 180)) - 90;
            double length = canvasWidth * (0.07 + (i % 4) * 0.025);
            double x = playerSide ? canvasWidth * (0.02 + (i % 5) * 0.055)
                    : canvasWidth * (0.72 + (i % 5) * 0.052);
            gc.setLineWidth(i % 3 == 0 ? 1.7 : 0.8);
            gc.strokeLine(x, y, x + (playerSide ? length : -length), y - length * 0.25);
        }
        gc.restore();
    }

    private void drawPlayerHud(GraphicsContext gc) {
        double scale = uiScale();
        drawScoreBlock(gc, canvasWidth * 0.165, 91 * scale, playerScore, true);

        GestureType shown = roundJudged ? playerGesture : liveGesture;
        int choice = gestureToChoice(shown);
        double size = clamp(Math.min(canvasWidth, canvasHeight) * 0.205, 132, 210);
        double cx = canvasWidth * 0.255;
        double cy = canvasHeight * 0.48;
        drawGestureStage(gc, cx, cy, size, choice, true, false);

        String label;
        if (roundJudged && playerGesture == GestureType.NONE) {
            label = "TIMEOUT / 未完成出拳";
        } else if (shown == GestureType.NONE) {
            label = liveHandDetected ? "ANALYZING / 正在识别" : "NO SIGNAL / 等待手势";
        } else {
            label = "TRACKED / " + gestureToName(shown);
        }
        drawGestureLabel(gc, cx, cy + size * 0.72, label, Color.web("#78C7FF"));

        double panelW = Math.min(canvasWidth * 0.29, 390 * scale);
        double x = Math.max(34 * scale, canvasWidth * 0.055);
        double y = canvasHeight * 0.78;
        gc.setFill(Color.rgb(2, 12, 30, 0.58));
        gc.fillRoundRect(x, y, panelW, 66 * scale, 16 * scale, 16 * scale);
        gc.setStroke(Color.rgb(93, 177, 255, 0.28));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x, y, panelW, 66 * scale, 16 * scale, 16 * scale);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.rgb(151, 205, 255, 0.72));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.SEMI_BOLD, 10 * scale));
        gc.fillText("GESTURE INPUT", x + 18 * scale, y + 19 * scale);
        gc.setFill(Color.rgb(231, 246, 255, 0.90));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 13 * scale));
        gc.fillText("握拳 石头   ·   剪刀手 剪刀   ·   张开 布", x + 18 * scale, y + 43 * scale);
        gc.setTextAlign(TextAlignment.CENTER);
    }

    private void drawOpponentHud(GraphicsContext gc) {
        double scale = uiScale();
        drawScoreBlock(gc, canvasWidth * 0.835, 91 * scale, computerScore, false);

        boolean concealed = !roundJudged;
        double size = clamp(Math.min(canvasWidth, canvasHeight) * 0.205, 132, 210);
        double cx = canvasWidth * 0.765;
        double cy = canvasHeight * 0.48;
        drawGestureStage(gc, cx, cy, size, computerChoice, false, concealed);
        drawGestureLabel(gc, cx, cy + size * 0.72,
                concealed ? "ENCRYPTED / 选择已锁定" : "REVEALED / " + computerChoiceName(computerChoice),
                Color.web("#D5A2FF"));
        drawOpponentDialogue(gc);
    }

    private void drawScoreBlock(GraphicsContext gc, double cx, double y, int score, boolean playerSide) {
        double scale = uiScale();
        Color accent = playerSide ? Color.web("#76C9FF") : Color.web("#D19AFF");
        String name = playerSide ? "YOU // PLAYER" : "NEXUS // AI OPPONENT";

        gc.setFill(accent.deriveColor(0, 1, 1, 0.80));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 11 * scale));
        gc.fillText(name, cx, y - 26 * scale);
        gc.setFill(Color.rgb(248, 251, 255, 0.98));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 70 * scale));
        gc.fillText(String.valueOf(score), cx, y + 22 * scale);

        int winThreshold = Math.max(1, (totalRounds / 2) + 1);
        double gap = 18 * scale;
        double start = cx - (winThreshold - 1) * gap / 2.0;
        for (int i = 0; i < winThreshold; i++) {
            double px = start + i * gap;
            double py = y + 70 * scale;
            double r = 4.5 * scale;
            if (i < score) {
                gc.setFill(accent);
                gc.fillPolygon(new double[]{px, px + r, px, px - r},
                        new double[]{py - r, py, py + r, py}, 4);
            } else {
                gc.setStroke(accent.deriveColor(0, 0.5, 0.65, 0.38));
                gc.setLineWidth(1.0);
                gc.strokePolygon(new double[]{px, px + r, px, px - r},
                        new double[]{py - r, py, py + r, py}, 4);
            }
        }
    }

    private void drawGestureStage(GraphicsContext gc, double cx, double cy, double size,
                                  int choice, boolean playerSide, boolean concealed) {
        Color accent = playerSide ? Color.web("#65C4FF") : Color.web("#C77DFF");
        double pulse = 0.5 + 0.5 * Math.sin(electricTime * 2.4 + (playerSide ? 0 : 1.2));
        double radius = size * (0.57 + pulse * 0.008);

        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setFill(new RadialGradient(0, 0, cx, cy, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, accent.deriveColor(0, 0.85, 0.75, 0.13)),
                new Stop(0.56, accent.deriveColor(0, 0.75, 0.55, 0.055)),
                new Stop(1.0, Color.TRANSPARENT)));
        gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        gc.restore();

        gc.setStroke(accent.deriveColor(0, 0.72, 0.72, 0.23));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
        gc.setLineDashes(4.0, 10.0);
        gc.setStroke(accent.deriveColor(0, 0.8, 0.9, 0.52));
        gc.setLineWidth(1.3);
        double spin = (electricTime * (playerSide ? 25 : -31)) % 360;
        gc.strokeArc(cx - radius * 0.87, cy - radius * 0.87, radius * 1.74, radius * 1.74,
                spin, 118, ArcType.OPEN);
        gc.strokeArc(cx - radius * 1.08, cy - radius * 1.08, radius * 2.16, radius * 2.16,
                -spin + 75, 62, ArcType.OPEN);
        gc.setLineDashes();

        gc.setStroke(accent.deriveColor(0, 0.8, 0.8, 0.20));
        gc.setLineWidth(0.8);
        gc.strokeLine(cx - radius * 1.16, cy, cx - radius * 0.92, cy);
        gc.strokeLine(cx + radius * 0.92, cy, cx + radius * 1.16, cy);
        gc.strokeLine(cx, cy - radius * 1.16, cx, cy - radius * 0.92);
        gc.strokeLine(cx, cy + radius * 0.92, cx, cy + radius * 1.16);

        if (concealed) {
            drawConcealedOpponentCore(gc, cx, cy, size, accent);
        } else if (choice >= 0) {
            drawMechanicalHand(gc, cx, cy + size * 0.02, size, choice, accent, !playerSide);
        } else {
            drawNoSignal(gc, cx, cy, size, accent);
        }
    }

    private void drawConcealedOpponentCore(GraphicsContext gc, double cx, double cy,
                                            double size, Color accent) {
        double r = size * 0.22;
        double spin = electricTime * 38.0;
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(spin);
        gc.setFill(Color.rgb(17, 7, 31, 0.88));
        gc.setStroke(accent.deriveColor(0, 1, 1, 0.82));
        gc.setLineWidth(1.8);
        gc.fillPolygon(new double[]{0, r, 0, -r}, new double[]{-r, 0, r, 0}, 4);
        gc.strokePolygon(new double[]{0, r, 0, -r}, new double[]{-r, 0, r, 0}, 4);
        gc.rotate(-spin);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, size * 0.105));
        gc.fillText("AI", 0, 0);
        gc.restore();

        gc.setStroke(accent.deriveColor(0, 0.8, 1, 0.44));
        gc.setLineWidth(1.0);
        for (int i = 0; i < 6; i++) {
            double a = electricTime * 0.7 + i * Math.PI / 3.0;
            double rr = size * 0.34;
            double x = cx + Math.cos(a) * rr;
            double y = cy + Math.sin(a) * rr;
            gc.strokeLine(cx + Math.cos(a) * r, cy + Math.sin(a) * r, x, y);
            gc.fillOval(x - 2, y - 2, 4, 4);
        }
    }

    private void drawNoSignal(GraphicsContext gc, double cx, double cy, double size, Color accent) {
        double w = size * 0.36;
        gc.setStroke(accent.deriveColor(0, 0.6, 0.8, 0.42));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(cx - w / 2, cy - w * 0.30, w, w * 0.60, 12, 12);
        gc.setFill(accent.deriveColor(0, 0.7, 0.8, 0.55));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, size * 0.09));
        gc.fillText("— —", cx, cy);
    }

    private void drawMechanicalHand(GraphicsContext gc, double cx, double cy, double size,
                                    int choice, Color accent, boolean aiHand) {
        double s = size / 180.0;
        Color metal = aiHand ? Color.rgb(20, 9, 34, 0.96) : Color.rgb(5, 25, 52, 0.94);
        Color plate = aiHand ? Color.rgb(49, 18, 72, 0.90) : Color.rgb(10, 49, 86, 0.90);

        gc.save();
        gc.translate(cx, cy + 8 * s);
        gc.scale(s, s);
        if (choice == 0) {
            drawFist(gc, accent, metal, plate, aiHand);
        } else if (choice == 1) {
            drawScissors(gc, accent, metal, plate, aiHand);
        } else {
            drawOpenHand(gc, accent, metal, plate, aiHand);
        }
        gc.restore();
    }

    private void drawFist(GraphicsContext gc, Color accent, Color metal, Color plate, boolean aiHand) {
        drawPalm(gc, -47, -6, 94, 78, accent, metal, aiHand);
        for (int i = 0; i < 4; i++) {
            double x = -47 + i * 23.5;
            gc.setFill(i % 2 == 0 ? plate : metal);
            gc.setStroke(accent.deriveColor(0, 1, 1, 0.88));
            gc.setLineWidth(aiHand ? 2.2 : 1.8);
            gc.fillRoundRect(x, -43, 22, 39, 8, 8);
            gc.strokeRoundRect(x, -43, 22, 39, 8, 8);
            gc.setStroke(accent.deriveColor(0, 0.8, 1, 0.28));
            gc.strokeLine(x + 5, -24, x + 17, -24);
        }
        drawFinger(gc, -47, 22, 23, 55, -64, accent, metal, plate, aiHand);
        drawWrist(gc, accent, metal, aiHand);
    }

    private void drawScissors(GraphicsContext gc, Color accent, Color metal, Color plate, boolean aiHand) {
        drawPalm(gc, -44, 10, 88, 70, accent, metal, aiHand);
        drawFinger(gc, -14, 14, 20, 115, -17, accent, metal, plate, aiHand);
        drawFinger(gc, 14, 14, 20, 115, 17, accent, metal, plate, aiHand);
        drawFinger(gc, -38, 21, 20, 47, -62, accent, metal, plate, aiHand);
        for (int i = 0; i < 2; i++) {
            double x = 22 + i * 18;
            gc.setFill(plate);
            gc.setStroke(accent.deriveColor(0, 0.9, 1, 0.72));
            gc.setLineWidth(1.6);
            gc.fillRoundRect(x - 12, -2 + i * 6, 22, 30, 8, 8);
            gc.strokeRoundRect(x - 12, -2 + i * 6, 22, 30, 8, 8);
        }
        drawWrist(gc, accent, metal, aiHand);
    }

    private void drawOpenHand(GraphicsContext gc, Color accent, Color metal, Color plate, boolean aiHand) {
        drawPalm(gc, -47, 0, 94, 82, accent, metal, aiHand);
        double[] xs = {-38, -18, 2, 22};
        double[] lengths = {72, 91, 98, 84};
        double[] angles = {-5, -1, 2, 6};
        for (int i = 0; i < xs.length; i++) {
            drawFinger(gc, xs[i], 4, 18, lengths[i], angles[i], accent, metal, plate, aiHand);
        }
        drawFinger(gc, -45, 30, 20, 61, -58, accent, metal, plate, aiHand);
        drawWrist(gc, accent, metal, aiHand);
    }

    private void drawPalm(GraphicsContext gc, double x, double y, double w, double h,
                          Color accent, Color metal, boolean aiHand) {
        gc.setFill(metal);
        gc.fillRoundRect(x, y, w, h, 22, 22);
        gc.setStroke(accent.deriveColor(0, 1, 1, 0.28));
        gc.setLineWidth(aiHand ? 6.0 : 4.0);
        gc.strokeRoundRect(x, y, w, h, 22, 22);
        gc.setStroke(accent.deriveColor(0, 1, 1, 0.94));
        gc.setLineWidth(aiHand ? 2.2 : 1.7);
        gc.strokeRoundRect(x, y, w, h, 22, 22);

        gc.setStroke(accent.deriveColor(0, 0.8, 1, aiHand ? 0.64 : 0.42));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + 19, y + h * 0.55, x + w * 0.50, y + h * 0.55);
        gc.strokeLine(x + w * 0.50, y + h * 0.55, x + w - 18, y + h * 0.28);
        gc.setFill(accent.deriveColor(0, 0.85, 1, 0.88));
        gc.fillOval(x + w * 0.50 - 3, y + h * 0.55 - 3, 6, 6);
        if (aiHand) {
            gc.strokeOval(x + w * 0.31, y + h * 0.22, w * 0.38, w * 0.38);
        }
    }

    private void drawFinger(GraphicsContext gc, double baseX, double baseY, double width,
                            double length, double angle, Color accent, Color metal,
                            Color plate, boolean aiHand) {
        gc.save();
        gc.translate(baseX, baseY);
        gc.rotate(angle);
        double gap = 4.0;
        double lower = length * 0.48;
        double upper = length - lower - gap;
        gc.setFill(metal);
        gc.setStroke(accent.deriveColor(0, 1, 1, aiHand ? 0.88 : 0.74));
        gc.setLineWidth(aiHand ? 2.0 : 1.55);
        gc.fillRoundRect(-width / 2, -lower, width, lower, width * 0.55, width * 0.55);
        gc.strokeRoundRect(-width / 2, -lower, width, lower, width * 0.55, width * 0.55);
        gc.setFill(plate);
        gc.fillRoundRect(-width / 2, -length, width, upper, width * 0.55, width * 0.55);
        gc.strokeRoundRect(-width / 2, -length, width, upper, width * 0.55, width * 0.55);
        gc.setFill(accent.deriveColor(0, 0.8, 1, 0.82));
        gc.fillOval(-3.2, -lower - gap / 2 - 3.2, 6.4, 6.4);
        gc.restore();
    }

    private void drawWrist(GraphicsContext gc, Color accent, Color metal, boolean aiHand) {
        gc.setFill(metal);
        gc.fillRoundRect(-31, 65, 62, 38, 12, 12);
        gc.setStroke(accent.deriveColor(0, 1, 1, 0.84));
        gc.setLineWidth(aiHand ? 2.2 : 1.6);
        gc.strokeRoundRect(-31, 65, 62, 38, 12, 12);
        gc.setStroke(accent.deriveColor(0, 0.7, 1, 0.50));
        for (int i = 0; i < 3; i++) {
            gc.strokeLine(-18 + i * 18, 74, -18 + i * 18, 94);
        }
    }

    private void drawGestureLabel(GraphicsContext gc, double cx, double y, String text, Color accent) {
        double scale = uiScale();
        gc.setFill(Color.rgb(1, 6, 15, 0.60));
        gc.fillRoundRect(cx - 112 * scale, y - 16 * scale, 224 * scale, 32 * scale,
                16 * scale, 16 * scale);
        gc.setStroke(accent.deriveColor(0, 0.8, 1, 0.32));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(cx - 112 * scale, y - 16 * scale, 224 * scale, 32 * scale,
                16 * scale, 16 * scale);
        gc.setFill(accent.deriveColor(0, 0.8, 1, 0.94));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.SEMI_BOLD, 11 * scale));
        gc.fillText(text, cx, y);
    }

    private void drawOpponentDialogue(GraphicsContext gc) {
        double scale = uiScale();
        double width = Math.min(canvasWidth * 0.30, 430 * scale);
        double height = 112 * scale;
        double x = Math.min(canvasWidth - width - 36 * scale, canvasWidth * 0.665);
        double y = canvasHeight * 0.765;
        String line = opponentLine;
        if (line == null || line.isBlank()) {
            if (state == RPSState.COUNTDOWN) {
                line = "我的选择已经锁定。别眨眼。";
            } else if (state == RPSState.JUDGE) {
                line = "正在核验本回合结果……";
            } else {
                line = "抬手吧，我已经准备好了。";
            }
        }

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(10, 5, 22, 0.82)),
                new Stop(1, Color.rgb(35, 9, 56, 0.72))));
        gc.fillRoundRect(x, y, width, height, 20 * scale, 20 * scale);
        gc.setStroke(Color.rgb(203, 130, 255, 0.36));
        gc.setLineWidth(1.1);
        gc.strokeRoundRect(x, y, width, height, 20 * scale, 20 * scale);
        gc.setFill(Color.rgb(194, 112, 255, 0.10));
        gc.fillPolygon(new double[]{x + 28 * scale, x + 48 * scale, x + 41 * scale},
                new double[]{y, y, y - 13 * scale}, 3);

        double pulse = 0.55 + 0.45 * Math.sin(electricTime * 3.4);
        gc.setFill(Color.rgb(213, 153, 255, 0.25 + pulse * 0.35));
        gc.fillOval(x + 19 * scale, y + 18 * scale, 7 * scale, 7 * scale);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.rgb(213, 172, 255, 0.82));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 10 * scale));
        gc.fillText("NEXUS // TRANSMISSION", x + 36 * scale, y + 22 * scale);
        gc.setFill(Color.rgb(249, 244, 255, 0.94));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 15 * scale));
        drawWrappedText(gc, line, x + 20 * scale, y + 53 * scale,
                width - 40 * scale, 19 * scale, 2);
        gc.setTextAlign(TextAlignment.CENTER);
    }

    private void drawTopHud(GraphicsContext gc) {
        double scale = uiScale();
        int shownRound = over ? roundCount : Math.min(totalRounds, roundCount + 1);
        double centerX = canvasWidth * 0.50;
        double y = 44 * scale;

        gc.setFill(Color.rgb(2, 3, 10, 0.58));
        gc.fillRoundRect(centerX - 166 * scale, y - 22 * scale, 332 * scale, 44 * scale,
                22 * scale, 22 * scale);
        gc.setStroke(Color.rgb(255, 255, 255, 0.12));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(centerX - 166 * scale, y - 22 * scale, 332 * scale, 44 * scale,
                22 * scale, 22 * scale);
        gc.setFill(Color.rgb(246, 249, 255, 0.96));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12 * scale));
        gc.fillText("RPS // ELECTRIC DUEL", centerX, y - 5 * scale);
        gc.setFill(Color.rgb(190, 198, 218, 0.68));
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 9 * scale));
        gc.fillText(String.format(java.util.Locale.ROOT,
                "ROUND %02d / %02d    ·    CAREER WIN RATE %.1f%%",
                shownRound, totalRounds, historySummary.winRatePercent()), centerX, y + 12 * scale);
    }

    private void drawCenterState(GraphicsContext gc) {
        double scale = uiScale();
        double y = canvasHeight * 0.51;
        // 中央信息跟随电弧的平滑中轴，不再绑定每帧抖动的波形点。
        double x = dividerBaseAt(y);

        if (state == RPSState.COUNTDOWN) {
            int segment = Math.max(1, initialCountdown / 3);
            int number = countdownFrames > 0 ? (countdownFrames - 1) / segment + 1 : 0;
            double progress = initialCountdown <= 0 ? 0 : countdownFrames / (double) initialCountdown;
            double r = 66 * scale;
            gc.setFill(Color.rgb(2, 4, 12, 0.84));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
            gc.setStroke(Color.rgb(255, 255, 255, 0.16));
            gc.setLineWidth(2.0 * scale);
            gc.strokeOval(x - r, y - r, r * 2, r * 2);
            gc.setStroke(Color.rgb(225, 242, 255, 0.94));
            gc.setLineWidth(3.2 * scale);
            gc.strokeArc(x - r, y - r, r * 2, r * 2, 90, -360 * progress, ArcType.OPEN);

            if (countdownBeat > 0.01) {
                double beatProgress = 1.0 - countdownBeat;
                double beatRadius = r + (5.0 + beatProgress * 15.0) * scale;
                gc.setStroke(Color.rgb(178, 224, 255, 0.42 * countdownBeat));
                gc.setLineWidth((1.0 + countdownBeat * 1.4) * scale);
                gc.strokeOval(x - beatRadius, y - beatRadius,
                        beatRadius * 2.0, beatRadius * 2.0);
            }

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD,
                    70 * scale * (1.0 + countdownBeat * 0.035)));
            gc.fillText(String.valueOf(number), x, y - 4 * scale);
            gc.setFill(Color.rgb(205, 216, 235, 0.64));
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 9 * scale));
            gc.fillText("LOCK IN", x, y + 40 * scale);
        } else if (roundJudged && (state == RPSState.JUDGE || state == RPSState.RESULT)) {
            Color accent = getResultAccent();
            double width = 194 * scale;
            double height = 68 * scale;
            gc.setFill(Color.rgb(2, 3, 10, 0.88));
            gc.fillRoundRect(x - width / 2, y - height / 2, width, height,
                    20 * scale, 20 * scale);
            gc.setStroke(accent.deriveColor(0, 1, 1, 0.84));
            gc.setLineWidth(1.6 * scale);
            gc.strokeRoundRect(x - width / 2, y - height / 2, width, height,
                    20 * scale, 20 * scale);
            gc.setFill(accent);
            gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 26 * scale));
            gc.fillText(roundResult, x, y - 7 * scale);
            gc.setFill(Color.rgb(215, 221, 234, 0.68));
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 9 * scale));
            gc.fillText("ROUND VERIFIED", x, y + 21 * scale);
        } else {
            gc.setFill(Color.rgb(2, 4, 12, 0.72));
            gc.fillRoundRect(x - 51 * scale, y - 22 * scale, 102 * scale, 44 * scale,
                    22 * scale, 22 * scale);
            gc.setStroke(Color.rgb(255, 255, 255, 0.20));
            gc.strokeRoundRect(x - 51 * scale, y - 22 * scale, 102 * scale, 44 * scale,
                    22 * scale, 22 * scale);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 16 * scale));
            gc.fillText("VS", x, y - 4 * scale);
            gc.setFill(Color.rgb(201, 209, 224, 0.58));
            gc.setFont(Font.font("Arial", FontWeight.NORMAL, 8 * scale));
            gc.fillText("READY", x, y + 12 * scale);
        }
    }

    private void drawDividerAtmosphere(GraphicsContext gc) {
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);

        // 宽而稀疏的多层波带，模拟原 WebGL simplex 噪声的白色雾化层。
        double[] widths = {72, 44, 24, 12};
        double[] alphas = {0.020, 0.032, 0.052, 0.075};
        for (int layer = 0; layer < widths.length; layer++) {
            gc.setStroke(Color.rgb(205, 230, 255, alphas[layer]));
            gc.setLineWidth(widths[layer]);
            strokeDividerPath(gc, 0.0);
        }

        for (int ribbon = 0; ribbon < 9; ribbon++) {
            gc.setStroke(Color.rgb(231, 242, 255, 0.022 + (ribbon % 3) * 0.007));
            gc.setLineWidth(2.0 + (ribbon % 4) * 1.8);
            gc.beginPath();
            for (int i = 0; i <= ELECTRIC_SEGMENTS; i++) {
                double t = i / (double) ELECTRIC_SEGMENTS;
                double scatter = Math.sin(t * Math.PI * (2.2 + ribbon * 0.17)
                        + electricTime * (0.7 + ribbon * 0.08) + ribbon * 1.71)
                        * (9 + ribbon * 2.8);
                double px = dividerX[i] + scatter;
                if (i == 0) gc.moveTo(px, dividerY[i]); else gc.lineTo(px, dividerY[i]);
            }
            gc.stroke();
        }
        gc.restore();
    }

    private void drawElectricDivider(GraphicsContext gc) {
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setStroke(Color.rgb(173, 216, 230, 0.25));
        gc.setLineWidth(9.0);
        strokeDividerPath(gc, 0.0);
        gc.setStroke(Color.rgb(173, 216, 230, 0.75));
        gc.setLineWidth(3.0);
        strokeDividerPath(gc, 0.0);
        gc.setStroke(Color.rgb(135, 206, 250, 0.55));
        gc.setLineWidth(2.2);
        strokeDividerPath(gc, 0.0);
        gc.setStroke(Color.rgb(255, 255, 255, 0.95));
        gc.setLineWidth(1.2);
        strokeDividerPath(gc, 0.0);
        gc.restore();
    }

    private void strokeDividerPath(GraphicsContext gc, double xOffset) {
        gc.beginPath();
        gc.moveTo(dividerX[0] + xOffset, dividerY[0]);
        for (int i = 1; i <= ELECTRIC_SEGMENTS; i++) {
            gc.lineTo(dividerX[i] + xOffset, dividerY[i]);
        }
        gc.stroke();
    }

    private void clipPlayerSide(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(0, 0);
        for (int i = 0; i <= ELECTRIC_SEGMENTS; i++) {
            gc.lineTo(dividerX[i], dividerY[i]);
        }
        gc.lineTo(0, canvasHeight);
        gc.closePath();
        gc.clip();
    }

    private void buildDividerGeometry() {
        double topX = clamp(dividerPosition, 0, 100) * canvasWidth / 100.0;
        double bottomX = clamp(dividerPosition - 25.0, 0, 100) * canvasWidth / 100.0;
        for (int i = 0; i <= ELECTRIC_SEGMENTS; i++) {
            double t = i / (double) ELECTRIC_SEGMENTS;
            double base = topX * (1.0 - t) + bottomX * t;
            double offsetPercent = 0.0;
            for (int k = 0; k < ELECTRIC_AMPS.length; k++) {
                offsetPercent += ELECTRIC_AMPS[k] * Math.sin(2 * Math.PI
                        * (ELECTRIC_FREQS[k] * t + ELECTRIC_SPEEDS[k] * electricTime) + k * 1.3);
            }
            offsetPercent += 0.25 * Math.sin(2 * Math.PI * (8.5 * t + 4.2 * electricTime));
            dividerX[i] = clamp(base + offsetPercent * canvasWidth / 100.0, 0, canvasWidth);
            dividerY[i] = t * canvasHeight;
        }
    }

    private double dividerBaseAt(double y) {
        if (canvasHeight <= 0) return canvasWidth * 0.5;
        double t = clamp(y / canvasHeight, 0.0, 1.0);
        double topX = clamp(dividerPosition, 0, 100) * canvasWidth / 100.0;
        double bottomX = clamp(dividerPosition - 25.0, 0, 100) * canvasWidth / 100.0;
        return topX * (1.0 - t) + bottomX * t;
    }

    private double getDividerTarget() {
        int scoreLead = playerScore - computerScore;
        double scorePressure = scoreLead * SCORE_PRESSURE_PER_POINT;
        double scoreLine = 60.0 + scorePressure;

        // 1:0 -> 67%，2:0 -> 74%；比分追平后始终回到 60% 的均势线。
        return clamp(scoreLine + roundImpact, 40.0, 80.0);
    }

    private Color getResultAccent() {
        if (roundResult != null && roundResult.contains("赢")) {
            return Color.web("#67E8F9");
        }
        if ("平局".equals(roundResult)) {
            return Color.web("#F8D66D");
        }
        return Color.web("#F19BFF");
    }

    private void drawMatchFinishedOverlay(GraphicsContext gc) {
        double scale = uiScale();
        String finalResult;
        Color accent;
        if (playerScore > computerScore) {
            finalResult = "YOU WIN";
            accent = Color.web("#77E4FF");
        } else if (playerScore < computerScore) {
            finalResult = "NEXUS WINS";
            accent = Color.web("#E29CFF");
        } else {
            finalResult = "DRAW";
            accent = Color.web("#F8D66D");
        }

        gc.setFill(Color.rgb(1, 2, 7, 0.80));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        double cx = canvasWidth / 2.0;
        double cy = canvasHeight / 2.0;
        gc.setFill(accent);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 54 * scale));
        gc.fillText(finalResult, cx, cy - 42 * scale);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 28 * scale));
        gc.fillText(playerScore + "  :  " + computerScore, cx, cy + 16 * scale);
        gc.setFill(matchSaved ? Color.rgb(135, 235, 187, 0.78) : Color.rgb(255, 170, 190, 0.78));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 12 * scale));
        gc.fillText(matchSaved ? "对局记录已写入 CSV" : "CSV 保存失败，请检查日志",
                cx, cy + 62 * scale);
    }

    private void drawWrappedText(GraphicsContext gc, String text, double x, double y,
                                 double maxWidth, double lineHeight, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        double currentWidth = 0;
        double fontSize = gc.getFont().getSize();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String ch = new String(Character.toChars(codePoint));
            double charWidth = codePoint < 128 ? fontSize * 0.58 : fontSize;
            if (current.length() > 0 && currentWidth + charWidth > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
                if (lines.size() == maxLines) break;
            }
            current.append(ch);
            currentWidth += charWidth;
            offset += Character.charCount(codePoint);
        }
        if (lines.size() < maxLines && current.length() > 0) {
            lines.add(current.toString());
        }
        boolean truncated = lines.size() == maxLines
                && lines.stream().mapToInt(String::length).sum() < text.length();
        if (truncated && !lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            lines.set(lines.size() - 1, last.length() > 1
                    ? last.substring(0, last.length() - 1) + "…" : "…");
        }
        for (int i = 0; i < lines.size(); i++) {
            gc.fillText(lines.get(i), x, y + i * lineHeight);
        }
    }

    private double uiScale() {
        return clamp(Math.min(canvasWidth / 1440.0, canvasHeight / 900.0), 0.72, 1.35);
    }

    private static boolean isRpsGesture(GestureType gesture) {
        return gesture == GestureType.FIST || gesture == GestureType.PEACE || gesture == GestureType.OPEN;
    }

    private static int gestureToChoice(GestureType gesture) {
        if (gesture == GestureType.FIST) return 0;
        if (gesture == GestureType.PEACE) return 1;
        if (gesture == GestureType.OPEN) return 2;
        return -1;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isOver() {
        return over;
    }

    @Override
    public int getScore() {
        return playerScore * 100;  // 每赢一局100分
    }

    private void loadHistorySummary() {
        try {
            historySummary = statsStore.loadSummary();
        } catch (IOException ex) {
            historySummary = Summary.empty();
            LOGGER.log(Level.WARNING,
                    "读取猜拳历史记录失败: " + statsStore.getCsvPath(), ex);
        }
    }

    private void persistCompletedMatch() {
        if (matchSaveAttempted) {
            return;
        }
        matchSaveAttempted = true;
        Outcome outcome = playerScore > computerScore
                ? Outcome.WIN
                : playerScore < computerScore ? Outcome.LOSS : Outcome.DRAW;
        try {
            historySummary = statsStore.appendMatch(
                    difficulty.name(), totalRounds, roundCount,
                    playerScore, computerScore, outcome);
            matchSaved = true;
            LOGGER.info(() -> "猜拳对局已写入 CSV: " + statsStore.getCsvPath());
        } catch (IOException ex) {
            matchSaved = false;
            LOGGER.log(Level.WARNING,
                    "写入猜拳对局 CSV 失败: " + statsStore.getCsvPath(), ex);
        }
    }

    private void judgeRound(GestureType gesture, int playerChoice) {
        playerGesture = gesture;
        playerGestureHistory[playerChoice]++;
        if (computerChoice == -1) computerChoice = RANDOM.nextInt(3);
        if (playerChoice == computerChoice) {
            roundResult = "平局";
        } else if ((playerChoice == 0 && computerChoice == 1)
                || (playerChoice == 1 && computerChoice == 2)
                || (playerChoice == 2 && computerChoice == 0)) {
            roundResult = "你赢了";
            playerScore++;
        } else {
            roundResult = "你输了";
            computerScore++;
        }
        finishRound(roundResult);
    }

    private void timeoutRound() {
        playerGesture = GestureType.NONE;
        computerScore++;
        finishRound("超时判负");
    }

    private void finishRound(String result) {
        roundResult = result;
        if (result.contains("赢")) {
            roundImpact = ROUND_IMPACT_STRENGTH;
        } else if (result.equals("平局")) {
            roundImpact = 0.0;
        } else {
            roundImpact = -ROUND_IMPACT_STRENGTH;
        }
        roundCount++;
        gameLog.add(roundResult);
        if (gameLog.size() > 3) gameLog.remove(0);
        roundJudged = true;
        resultFrames = 120;
        SystemSpeech.speak("本回合" + result + "。比分 " + playerScore + " 比 " + computerScore);
        // AI 对手台词（本地秒回 + LLM异步覆盖）
        OpponentAI.getLine(result, gestureToName(playerGesture),
                computerChoiceName(computerChoice), playerScore, computerScore,
                line -> Platform.runLater(() -> {
                    this.opponentLine = line;
                    SystemSpeech.speak(line); // 语音念出台词
                }));
    }

    private static String gestureToName(GestureType g) {
        if (g == GestureType.FIST) return "石头";
        if (g == GestureType.PEACE) return "剪刀";
        if (g == GestureType.OPEN) return "布";
        return "未知";
    }

    private static String computerChoiceName(int c) {
        return c == 0 ? "石头" : c == 1 ? "剪刀" : "布";
    }

    private void announceFinalResult() {
        if (finalSpeechAnnounced) {
            return;
        }
        finalSpeechAnnounced = true;
        String result;
        if (playerScore > computerScore) {
            result = "本场比赛你赢了";
        } else if (playerScore < computerScore) {
            result = "本场比赛电脑获胜";
        } else {
            result = "本场比赛平局";
        }
        SystemSpeech.speak(result + "。最终比分 " + playerScore + " 比 " + computerScore);
    }

    private int getComputerChoice() {
        if (cpuSmartLevel == 0) {
            return RANDOM.nextInt(3);
        }
        if (cpuSmartLevel == 1 && RANDOM.nextDouble() < 0.3) {
            int mostUsed = 0;
            for (int i = 0; i < 3; i++)
                if (playerGestureHistory[i] > playerGestureHistory[mostUsed]) mostUsed = i;
            return (mostUsed + 2) % 3;
        }
        if (cpuSmartLevel == 2 && RANDOM.nextDouble() < 0.5) {
            int mostUsed = 0;
            for (int i = 0; i < 3; i++)
                if (playerGestureHistory[i] > playerGestureHistory[mostUsed]) mostUsed = i;
            return (mostUsed + 2) % 3;
        }
        return RANDOM.nextInt(3);
    }

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }
}
