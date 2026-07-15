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
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

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
        this.gameLog = new ArrayList<>();
        this.beepFrame = false;
        this.roundJudged = false;
        this.finalSpeechAnnounced = false;
        this.playerGestureHistory = new int[3];
        this.matchSaveAttempted = false;
        this.matchSaved = false;
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

        // === 状态机 ===

        if (state == RPSState.WAITING) {
            if (gesture.isHandDetected()) {
                applyDifficulty();
                initialCountdown = countdownFrames;
                computerChoice = getComputerChoice();
                playerGesture = GestureType.NONE; // 重置本局手势
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
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // === 通用元素 ===

        // "嘀"效果：短暂闪烁背景
        Color bgColor = Color.web("#0f172a");
        if (beepFrame) {
            bgColor = Color.web("#1e3a5f");
            beepFrame = false;
        }
        gc.setFill(bgColor);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 顶部标题
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(20));
        gc.fillText("石头剪刀布 ✂️", canvasWidth / 2.0 - 80, 40);

        gc.setFill(Color.web("#94a3b8"));
        gc.setFont(javafx.scene.text.Font.font(13));
        String historyText = String.format(java.util.Locale.ROOT,
                "历史 %d 场  胜 %d  负 %d  平 %d  胜率 %.1f%%",
                historySummary.totalMatches(), historySummary.wins(),
                historySummary.losses(), historySummary.draws(),
                historySummary.winRatePercent());
        gc.fillText(historyText, canvasWidth / 2.0 - 145, 64);

        // 底部比分（五局三胜进度条）
        double barY = canvasHeight - 70;
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(14));
        gc.fillText("你", canvasWidth / 2.0 - 120, barY - 10);
        gc.fillText("电脑", canvasWidth / 2.0 + 90, barY - 10);
        int winThreshold = (totalRounds / 2) + 1;
        for (int i = 0; i < winThreshold; i++) {
            double cxP = canvasWidth / 2.0 - 100 + i * 20;
            double cxC = canvasWidth / 2.0 + 80 + i * 20;
            if (i < playerScore) {
                gc.setFill(Color.LIME);
                gc.fillOval(cxP, barY, 12, 12);
            } else {
                gc.setStroke(Color.GRAY);
                gc.setLineWidth(2);
                gc.strokeOval(cxP, barY, 12, 12);
            }
            if (i < computerScore) {
                gc.setFill(Color.RED);
                gc.fillOval(cxC, barY, 12, 12);
            } else {
                gc.setStroke(Color.GRAY);
                gc.setLineWidth(2);
                gc.strokeOval(cxC, barY, 12, 12);
            }
        }

        // === 状态渲染 ===

        if (state == RPSState.WAITING) {
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(28));
            gc.fillText("请出手势", canvasWidth / 2.0 - 70, canvasHeight / 2.0 - 20);
            gc.setFill(Color.GRAY);
            gc.setFont(javafx.scene.text.Font.font(14));
            gc.fillText("握拳=石头 | 张开=布 | 剪刀手=剪刀",
                    canvasWidth / 2.0 - 140, canvasHeight / 2.0 + 30);
        } else if (state == RPSState.COUNTDOWN) {
            int countdownNumber = countdownFrames > 0
                    ? (countdownFrames - 1) / (initialCountdown / 3) + 1
                    : 0;
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(80));
            gc.fillText(String.valueOf(countdownNumber),
                    canvasWidth / 2.0 - 20, canvasHeight / 2.0 + 25);
        } else if (state == RPSState.JUDGE) {
            String playerEmoji = "❓";
            if (playerGesture == GestureType.FIST) playerEmoji = "✊";
            else if (playerGesture == GestureType.PEACE) playerEmoji = "✌️";
            else if (playerGesture == GestureType.OPEN) playerEmoji = "✋";
            String[] computerEmojis = {"✊", "✌️", "✋"};
            String computerEmoji = computerEmojis[computerChoice % 3];

            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(60));
            gc.fillText(playerEmoji, canvasWidth / 2.0 - 180, canvasHeight / 2.0 + 20);
            gc.setFont(javafx.scene.text.Font.font(30));
            gc.fillText("VS", canvasWidth / 2.0 - 25, canvasHeight / 2.0 + 10);
            gc.setFont(javafx.scene.text.Font.font(60));
            gc.fillText(computerEmoji, canvasWidth / 2.0 + 100, canvasHeight / 2.0 + 20);
        } else if (state == RPSState.RESULT) {
            Color resultColor = roundResult.contains("赢") ? Color.LIME
                    : roundResult.equals("平局") ? Color.YELLOW : Color.RED;
            gc.setFill(resultColor);
            gc.setFont(javafx.scene.text.Font.font(36));
            gc.fillText(roundResult, canvasWidth / 2.0 - 55, canvasHeight / 2.0 - 30);
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(16));
            gc.fillText("比分 " + playerScore + " : " + computerScore,
                    canvasWidth / 2.0 - 35, canvasHeight / 2.0 + 10);
            // AI 对手台词
            if (opponentLine != null && !opponentLine.isEmpty()) {
                gc.setFill(Color.web("#fbbf24"));
                gc.setFont(javafx.scene.text.Font.font(14));
                gc.fillText("\"" + opponentLine + "\"",
                        canvasWidth / 2.0 - 120, canvasHeight / 2.0 + 40);
            }
        }

        if (over) {
            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);
            String finalResult;
            Color finalColor;
            if (playerScore > computerScore) {
                finalResult = "🏆 你赢了！";
                finalColor = Color.GOLD;
            } else if (playerScore < computerScore) {
                finalResult = "🤖 电脑赢了";
                finalColor = Color.RED;
            } else {
                finalResult = "🤝 本场平局";
                finalColor = Color.YELLOW;
            }
            gc.setFill(finalColor);
            gc.setFont(javafx.scene.text.Font.font(40));
            gc.fillText(finalResult, canvasWidth / 2.0 - 100, canvasHeight / 2.0 - 20);
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(20));
            gc.fillText("最终比分 " + playerScore + " : " + computerScore,
                    canvasWidth / 2.0 - 60, canvasHeight / 2.0 + 30);
            gc.setFill(Color.web("#deff9a"));
            gc.setFont(javafx.scene.text.Font.font(16));
            gc.fillText("握拳重新开始 | 张开返回大厅",
                    canvasWidth / 2.0 - 95, canvasHeight / 2.0 + 60);
            gc.setFill(matchSaved ? Color.web("#86efac") : Color.web("#fca5a5"));
            gc.setFont(javafx.scene.text.Font.font(13));
            gc.fillText(matchSaved ? "对局记录已保存到 CSV" : "CSV 保存失败，请检查日志",
                    canvasWidth / 2.0 - 85, canvasHeight / 2.0 + 88);
        }
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
        roundCount++;
        gameLog.add(roundResult);
        if (gameLog.size() > 3) gameLog.remove(0);
        roundJudged = true;
        resultFrames = 120;
        SystemSpeech.speak("本回合" + result + "。比分 " + playerScore + " 比 " + computerScore);
        // AI 对手台词（本地秒回 + LLM异步覆盖）
        OpponentAI.getLine(result, gestureToName(playerGesture),
                computerChoiceName(computerChoice), playerScore, computerScore,
                line -> Platform.runLater(() -> { this.opponentLine = line; }));
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
