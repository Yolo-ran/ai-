package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // === 状态机 ===

        if (state == RPSState.WAITING) {
            // 检测到手 → 进入倒计时
            if (gesture.isHandDetected()) {
                state = RPSState.COUNTDOWN;
                countdownFrames = 90;
                beepFrame = true;
            }
        } else if (state == RPSState.COUNTDOWN) {
            // 数字变化时触发"嘀"效果
            if (countdownFrames == 60 || countdownFrames == 30) {
                beepFrame = true;
            }
            // 45帧时电脑随机出拳
            if (countdownFrames == 45) {
                computerChoice = RANDOM.nextInt(3);
            }
            countdownFrames--;
            if (countdownFrames <= 0) {
                countdownFrames = 0;
                state = RPSState.JUDGE;
            }
        } else if (state == RPSState.JUDGE) {
            // 读取玩家手势并映射
            GestureType g = gesture.getGesture();
            int playerChoice = -1;
            if (g == GestureType.FIST) {
                playerChoice = 0;      // 石头
            } else if (g == GestureType.PEACE) {
                playerChoice = 1;      // 剪刀
            } else if (g == GestureType.OPEN) {
                playerChoice = 2;      // 布
            }

            if (playerChoice == -1) {
                // NONE → 本轮无效，回到 WAITING
                state = RPSState.WAITING;
            } else {
                playerGesture = g;
                // 如果电脑还没出拳（极端情况），补一个
                if (computerChoice == -1) {
                    computerChoice = RANDOM.nextInt(3);
                }
                // 判定胜负
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
                roundCount++;
                // 记录日志（最多3条）
                gameLog.add(roundResult);
                if (gameLog.size() > 3) {
                    gameLog.remove(0);
                }
                state = RPSState.RESULT;
                resultFrames = 60;
            }
        } else if (state == RPSState.RESULT) {
            resultFrames--;
            if (resultFrames <= 0) {
                // 五局三胜：5局或有人达到3胜
                if (roundCount >= 5 || playerScore >= 3 || computerScore >= 3) {
                    over = true;
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

        // 底部比分（五局三胜进度条）
        double barY = canvasHeight - 70;
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(14));
        gc.fillText("你", canvasWidth / 2.0 - 120, barY - 10);
        gc.fillText("电脑", canvasWidth / 2.0 + 90, barY - 10);
        for (int i = 0; i < 3; i++) {
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
            int countdownNumber = (countdownFrames + 29) / 30;
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(80));
            gc.fillText(String.valueOf(countdownNumber),
                    canvasWidth / 2.0 - 20, canvasHeight / 2.0 + 25);
        } else if (state == RPSState.JUDGE) {
            // 玩家手势 emoji
            String playerEmoji = "❓";
            if (playerGesture == GestureType.FIST) playerEmoji = "✊";
            else if (playerGesture == GestureType.PEACE) playerEmoji = "✌️";
            else if (playerGesture == GestureType.OPEN) playerEmoji = "✋";
            // 电脑手势 emoji
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
            Color resultColor;
            if (roundResult.contains("赢")) {
                resultColor = Color.LIME;
            } else if (roundResult.equals("平局")) {
                resultColor = Color.YELLOW;
            } else {
                resultColor = Color.RED;
            }
            gc.setFill(resultColor);
            gc.setFont(javafx.scene.text.Font.font(40));
            gc.fillText(roundResult, canvasWidth / 2.0 - 60, canvasHeight / 2.0 + 15);
        }

        if (over) {
            String finalResult = playerScore > computerScore ? "你赢了！🏆" : "电脑赢了 🤖";
            gc.setFill(playerScore > computerScore ? Color.GOLD : Color.RED);
            gc.fillText(finalResult, canvasWidth / 2.0 - 80, canvasHeight / 2.0);
            gc.setFill(Color.WHITE);
            gc.fillText("最终比分 " + playerScore + ":" + computerScore + "  握拳重新开始",
                    canvasWidth / 2.0 - 140, canvasHeight / 2.0 + 40);
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

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }
}
