package com.gesturegame.ui;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureStreamServer.DualHandState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.logging.Logger;

/**
 * 游戏场景渲染器，同时作为 {@code Game.fxml} 的控制器。
 *
 * <p>职责：
 * <ul>
 *   <li>管理全屏 {@link Canvas} 与 {@link GraphicsContext}</li>
 *   <li>{@link #tick(GestureData, GameInterface)} 每帧由 {@code AnimationTimer} 驱动：
 *       GAME 态调用 {@code game.update/render}；GAME_OVER 态冻结最后一帧并显示结算</li>
 *   <li>游戏结束后等待握拳重玩，或双手入镜保持返回</li>
 *   <li>窗口缩放时重开当前局，保证画面与画布尺寸一致</li>
 * </ul>
 */
public class GameRenderer {

    private static final Logger LOGGER = Logger.getLogger(GameRenderer.class.getName());

    @FXML
    private Canvas gameCanvas;

    @FXML
    private Label gameNameLabel;

    @FXML
    private Label scoreLabel;

    @FXML
    private Label statusLabel;

    private AppStateManager appStateManager;
    private GraphicsContext gc;
    private GameInterface currentGame;
    private boolean gameOverHandled;
    private boolean settling;
    private double lastInitWidth;
    private double lastInitHeight;
    private Difficulty selectedDifficulty = Difficulty.NORMAL;
    private int compactHoldFrames;
    private int openHoldFrames;
    private int exitHoldFrames;
    private int exitDropoutFrames;
    private static final int HOLD_FRAMES = 72; // 1.2秒@60fps

    @FXML
    public void initialize() {
        gc = gameCanvas.getGraphicsContext2D();
        if (gameCanvas.getParent() instanceof Pane) {
            Pane parent = (Pane) gameCanvas.getParent();
            gameCanvas.widthProperty().bind(parent.widthProperty());
            gameCanvas.heightProperty().bind(parent.heightProperty());
        }
        gameCanvas.widthProperty().addListener((obs, oldW, newW) -> reinitOnResize());
        gameCanvas.heightProperty().addListener((obs, oldH, newH) -> reinitOnResize());
        clearCanvas();
    }

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    public Canvas getCanvas() {
        return gameCanvas;
    }

    /**
     * 游戏循环每帧调用：GAME 态更新+渲染并检测结束；GAME_OVER 态冻结画面。
     * 仅在 GAME / GAME_OVER 状态下由 MainApp 的 AnimationTimer 调用。
     */
    public void tick(GestureData gesture, GameInterface game) {
        tick(gesture, game, new DualHandState(false, false, 0.0, false, 0.0, 0.0));
    }

    public void tick(GestureData gesture, GameInterface game, DualHandState dualHands) {
        if (gc == null) {
            return;
        }

        String state = AppStateManager.getInstance().getCurrentState();
        if (AppStateManager.STATE_GAME_OVER.equals(state)) {
            updateExitHold(dualHands);
            if (exitHoldFrames >= HOLD_FRAMES) {
                exitHoldFrames = 0;
                exitToDifficulty();
                return;
            }
            // 只重绘、不更新游戏，确保退出进度环取消后不会残留在冻结画面上。
            if (game != null) {
                game.render(gc);
            }
            drawExitTarget(gc, gameCanvas.getWidth(), gameCanvas.getHeight());
            drawExitProgress(gesture, dualHands);
            return;
        }
        if (!AppStateManager.STATE_GAME.equals(state)) {
            return;
        }

        if (game == null) {
            clearCanvas();
            if (statusLabel != null) {
                statusLabel.setText("等待游戏加载...");
            }
            return;
        }

        if (game != currentGame) {
            currentGame = game;
            gameOverHandled = false;
            settling = false;
            initGame(game);
            if (statusLabel != null) {
                statusLabel.setText("塔罗牌".equals(game.getName()) ? "" : "双手入镜保持返回");
            }
        }

        updateExitHold(dualHands);
        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            exitToDifficulty();
            return;
        }

        // 单手基础手势完整交给游戏；双手系统模式只屏蔽输入，不暂停游戏动画。
        game.update(dualHands.captured() ? new GestureData() : gesture);
        game.render(gc);
        drawExitTarget(gc, gameCanvas.getWidth(), gameCanvas.getHeight());
        drawExitProgress(gesture, dualHands);

        boolean tarotMode = "塔罗牌".equals(game.getName());
        if (gameNameLabel != null) {
            gameNameLabel.setText(tarotMode ? "" : game.getIcon() + "  " + game.getName());
        }
        if (scoreLabel != null) {
            scoreLabel.setText(tarotMode ? "" : "分数: " + game.getScore());
        }
        if (statusLabel != null && tarotMode) {
            statusLabel.setText("");
        }

        if (game.isOver() && !gameOverHandled) {
            gameOverHandled = true;
            settling = true;
            if (statusLabel != null) {
                statusLabel.setText("游戏结束！得分: " + game.getScore() + "  ✊握拳重玩 | 双手保持返回");
            }
            LOGGER.info(() -> "[GameRenderer] 游戏结束: " + game.getName() + " 得分=" + game.getScore());
            if (appStateManager != null) {
                appStateManager.switchState(AppStateManager.STATE_GAME_OVER);
            }
        }
    }

    /**
     * 接收串流服务器派发的手势命令。
     * GAME 态仅响应 BACK（返回大厅）；GAME_OVER 态响应 CONFIRM（重玩）/BACK（回大厅）。
     * {@code settling} 标志保证结算态下仅处理一次命令，避免重复切换场景。
     */
    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            String state = AppStateManager.getInstance().getCurrentState();

            if (AppStateManager.STATE_GAME.equals(state)) {
                if (command == GestureCommand.BACK) {
                    LOGGER.info(() -> "[GameRenderer] 收到 BACK 指令，返回难度选择");
                    exitToDifficulty();
                }
                return;
            }

            if (AppStateManager.STATE_GAME_OVER.equals(state) && settling) {
                if (command == GestureCommand.CONFIRM) {
                    LOGGER.info(() -> "[GameRenderer] 结算态收到 CONFIRM，重玩当前游戏");
                    settling = false;
                    GameInterface game = AppStateManager.getInstance().getActiveGame();
                    if (game != null) {
                        game.reset();
                    }
                    currentGame = null;
                    gameOverHandled = false;
                    if (appStateManager != null) {
                        appStateManager.switchState(AppStateManager.STATE_GAME);
                    }
                } else if (command == GestureCommand.BACK) {
                    LOGGER.info(() -> "[GameRenderer] 结算态收到 BACK，返回难度选择");
                    settling = false;
                    exitToDifficulty();
                }
            }
        });
    }

    private void exitToDifficulty() {
        exitHoldFrames = 0;
        compactHoldFrames = 0;
        openHoldFrames = 0;
        currentGame = null;
        gameOverHandled = false;
        settling = false;
        if (gameNameLabel != null) gameNameLabel.setText("");
        if (scoreLabel != null) scoreLabel.setText("");
        clearCanvas();
        if (appStateManager != null) {
            appStateManager.switchState(AppStateManager.STATE_DIFFICULTY);
        }
    }

    private void exitToLobby() {
        exitHoldFrames = 0;
        compactHoldFrames = 0;
        openHoldFrames = 0;
        GameInterface game = AppStateManager.getInstance().getActiveGame();
        if (game != null) {
            game.reset();
        }
        currentGame = null;
        gameOverHandled = false;
        settling = false;
        if (appStateManager != null) {
            appStateManager.switchState(AppStateManager.STATE_LOBBY);
        }
    }

    private void initGame(GameInterface game) {
        int w = (int) Math.max(1, gameCanvas.getWidth());
        int h = (int) Math.max(1, gameCanvas.getHeight());
        game.init(w, h);
        lastInitWidth = w;
        lastInitHeight = h;
        LOGGER.info(() -> "[GameRenderer] 初始化游戏: " + game.getName() + " (" + w + "x" + h + ")");
    }

    /**
     * 窗口缩放时重开当前局，使游戏画面与新画布尺寸匹配。
     * 仅在 GAME 态且尺寸确实变化时触发，避免重复初始化。
     */
    private void reinitOnResize() {
        if (gc == null || currentGame == null) {
            return;
        }
        String state = AppStateManager.getInstance().getCurrentState();
        if (!AppStateManager.STATE_GAME.equals(state)) {
            return;
        }
        int w = (int) Math.max(1, gameCanvas.getWidth());
        int h = (int) Math.max(1, gameCanvas.getHeight());
        if (w == lastInitWidth && h == lastInitHeight) {
            return;
        }
        currentGame.init(w, h);
        lastInitWidth = w;
        lastInitHeight = h;
        LOGGER.info(() -> "[GameRenderer] 画布缩放，重开当前局: " + w + "x" + h);
    }

    /** 难度选择界面：手移选难度，握拳确认 */
    public void tickDifficultySelect(GestureData gesture) {
        tickDifficultySelect(gesture, new DualHandState(false, false, 0.0, false, 0.0, 0.0));
    }

    public void tickDifficultySelect(GestureData gesture, DualHandState dualHands) {
        if (gameCanvas == null) return;
        GraphicsContext g = gameCanvas.getGraphicsContext2D();
        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();
        g.setFill(Color.web("#120718"));
        g.fillRect(0, 0, w, h);

        GameInterface activeGame = AppStateManager.getInstance().getActiveGame();
        boolean tarotMode = activeGame != null && "塔罗牌".equals(activeGame.getName());
        if (tarotMode) {
            drawTarotEntryScreen(g, gesture, dualHands, w, h, activeGame);
            return;
        }
        Difficulty[] all = Difficulty.values();
        java.util.List<Difficulty> supported = new java.util.ArrayList<>();
        for (Difficulty d : all) {
            if (activeGame == null || activeGame.supportsDifficulty(d)) {
                supported.add(d);
            }
        }
        Difficulty[] diffs = supported.toArray(new Difficulty[0]);
        int n = diffs.length;
        double cardW = 150, cardH = 180, gap = 20;
        double totalW = cardW * n + gap * (n - 1);
        double startX = (w - totalW) / 2;
        double cardY = h / 2 - cardH / 2;
        int hoveredIndex = -1;
        updateExitHold(dualHands);

        // 手势逻辑
        if (gesture != null && gesture.isHandDetected()) {
            double hx = gesture.getHandX() * w;
            double hy = gesture.getHandY() * h;
            for (int i = 0; i < n; i++) {
                double cx = startX + i * (cardW + gap);
                if (hx >= cx && hx <= cx + cardW && hy >= cardY && hy <= cardY + cardH) {
                    hoveredIndex = i;
                    break;
                }
            }

            GestureType gestureType = gesture.getGesture();
            boolean isFist = !dualHands.captured() && gestureType == GestureType.FIST;

            if (isFist && hoveredIndex >= 0) {
                compactHoldFrames++;
            } else {
                compactHoldFrames = 0;
            }
        } else {
            compactHoldFrames = 0;
        }

        // PEACE确认(1.2s) → 进入游戏
        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            GameInterface game = AppStateManager.getInstance().getActiveGame();
            if (game != null) {
                game.setDifficulty(selectedDifficulty);
                LOGGER.info("难度选择确认: " + selectedDifficulty.getLabel() + " → " + game.getName());
            }
            currentGame = null;
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return;
        }

        // 双手稳定保持 → 返回大厅
        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            LOGGER.info("难度选择取消，返回大厅");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_LOBBY);
            return;
        }

        if (hoveredIndex >= 0) selectedDifficulty = diffs[hoveredIndex];

        // 渲染
        for (int i = 0; i < n; i++) {
            double cx = startX + i * (cardW + gap);
            boolean sel = i == hoveredIndex || (hoveredIndex < 0 && diffs[i] == selectedDifficulty);
            if (sel) {
                g.setFill(Color.web("#2a133a"));
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(3);
            } else {
                g.setFill(Color.web("#160b20"));
                g.setStroke(Color.web("#8b5bd144"));
                g.setLineWidth(1);
            }
            g.fillRoundRect(cx, cardY, cardW, cardH, 16, 16);
            g.strokeRoundRect(cx, cardY, cardW, cardH, 16, 16);

            // 文字居中
            g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            double midX = cx + cardW / 2;

            String label = activeGame != null
                    ? activeGame.getDifficultyLabel(diffs[i])
                    : diffs[i].getLabel();
            boolean hasCustomLabel = activeGame != null
                    && !activeGame.getDifficultyLabel(diffs[i]).equals(diffs[i].getLabel());

            if (hasCustomLabel) {
                // 仅显示自定义文字（如"三局两胜"），居中大字
                g.setFill(sel ? Color.WHITE : Color.web("#94a3b8"));
                g.setFont(javafx.scene.text.Font.font(20));
                g.fillText(label, midX, cardY + cardH / 2 + 6);
            } else {
                // 标准显示：标题 + 星星
                g.setFill(sel ? Color.WHITE : Color.web("#94a3b8"));
                g.setFont(javafx.scene.text.Font.font(18));
                g.fillText(label, midX, cardY + 40);
                g.setFont(javafx.scene.text.Font.font(36));
                g.fillText(diffs[i].getStars(), midX, cardY + 100);
            }
            g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        }

        // 手光标
        if (gesture != null && gesture.isHandDetected()) {
            double cx = gesture.getHandX() * w;
            double cy = gesture.getHandY() * h;

            if (compactHoldFrames > 0 && hoveredIndex >= 0) {
                // PEACE 按住中：外圈光晕 + 进度环
                g.setFill(Color.color(0.94, 0.79, 0.47, 0.12));
                g.fillOval(cx - 22, cy - 22, 44, 44);
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(2.0);
                g.strokeOval(cx - 16, cy - 16, 32, 32);
                // 进度弧
                double progress = (double) compactHoldFrames / HOLD_FRAMES;
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(3);
                g.strokeArc(cx - 20, cy - 20, 40, 40,
                        90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
            }

            // 中心小点（始终显示）
            g.setFill(Color.web("#f0ca79"));
            g.fillOval(cx - 3, cy - 3, 6, 6);
        }

        g.setFill(Color.web("#f0ca79"));
        g.setFont(javafx.scene.text.Font.font(16));
        drawExitTarget(g, w, h);
        drawExitProgress(gesture, dualHands);
        g.fillText("手移选难度 | ✊握拳确认 | 双手入镜保持返回", w / 2 - 150, cardY + cardH + 50);
    }

    private void drawTarotEntryScreen(GraphicsContext g, GestureData gesture, DualHandState dualHands,
                                      double w, double h, GameInterface activeGame) {
        updateExitHold(dualHands);
        boolean isFist = gesture != null && gesture.getGesture() == GestureType.FIST && !dualHands.captured();

        if (isFist) {
            compactHoldFrames++;
        } else {
            compactHoldFrames = 0;
        }

        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            currentGame = null;
            LOGGER.info("塔罗牌跳过难度选择，直接进入自定义占读模式");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return;
        }

        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            LOGGER.info("塔罗牌入口取消，返回大厅");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_LOBBY);
            return;
        }

        double panelW = 420;
        double panelH = 260;
        double x = (w - panelW) / 2.0;
        double y = (h - panelH) / 2.0;
        g.setFill(Color.web("#1a0c24"));
        g.fillRoundRect(x, y, panelW, panelH, 26, 26);
        g.setStroke(Color.web("#8b5bd1"));
        g.setLineWidth(1.5);
        g.strokeRoundRect(x, y, panelW, panelH, 26, 26);

        g.setFill(Color.web("#f0ca79"));
        g.setFont(javafx.scene.text.Font.font(16));
        g.fillText("✦ 紫金秘仪模式", x + 28, y + 34);

        g.setFill(Color.web("#fff3df"));
        g.setFont(javafx.scene.text.Font.font(30));
        g.fillText(activeGame.getIcon() + "  " + activeGame.getName(), x + 28, y + 84);

        g.setFill(Color.web("#d8c2e6"));
        g.setFont(javafx.scene.text.Font.font(16));
        g.fillText("塔罗牌不使用普通难度系统，将直接按你的自定义占读逻辑进入。", x + 28, y + 126);
        g.fillText("当前保留：紫金界面、78张牌、两种牌阵、完整解读。", x + 28, y + 154);

        g.setFill(Color.web("#2e143f"));
        g.fillRoundRect(x + 28, y + 188, 168, 42, 22, 22);
        g.setStroke(Color.web("#f0ca79"));
        g.strokeRoundRect(x + 28, y + 188, 168, 42, 22, 22);
        g.setFill(Color.web("#f6dfae"));
        g.fillText("✊ 握拳开始占读", x + 50, y + 215);

        g.setFill(Color.web("#24112f"));
        g.fillRoundRect(x + 220, y + 188, 148, 42, 22, 22);
        g.setStroke(Color.web("#8b5bd1"));
        g.strokeRoundRect(x + 220, y + 188, 148, 42, 22, 22);
        g.setFill(Color.web("#d7c1e4"));
        g.fillText("👥 双手保持退出", x + 248, y + 215);

        drawExitTarget(g, w, h);
        drawExitProgress(gesture, dualHands);
    }

    private void clearCanvas() {
        if (gc != null && gameCanvas != null) {
            gc.setFill(Color.web("#120718"));
            gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        }
    }

    private void updateExitHold(DualHandState dualHands) {
        if (dualHands.active()) {
            exitHoldFrames = Math.min(HOLD_FRAMES, exitHoldFrames + 1);
            exitDropoutFrames = 0;
        } else if (dualHands.active() && exitHoldFrames > 0 && exitDropoutFrames < 6) {
            exitDropoutFrames++;
        } else {
            exitHoldFrames = 0;
            exitDropoutFrames = 0;
        }
    }

    private void drawExitTarget(GraphicsContext graphics, double width, double height) {
        graphics.setFill(Color.color(0.87, 1.0, 0.6, 0.10));
        graphics.fillRoundRect(16, 14, 190, 34, 16, 16);
        graphics.setFill(Color.web("#deff9a"));
        graphics.setFont(javafx.scene.text.Font.font(14));
        graphics.fillText("双手入镜保持返回", 30, 36);
    }

    private void drawExitProgress(GestureData gesture, DualHandState dualHands) {
        if (exitHoldFrames <= 0 || gesture == null || !gesture.isHandDetected()) {
            return;
        }
        double cx = (gesture.getHandX() + dualHands.secondHandX()) * 0.5 * gameCanvas.getWidth();
        double cy = (gesture.getHandY() + dualHands.secondHandY()) * 0.5 * gameCanvas.getHeight();
        double progress = (double) exitHoldFrames / HOLD_FRAMES;

        gc.setFill(Color.color(0.87, 1.0, 0.6, 0.12));
        gc.fillOval(cx - 24, cy - 24, 48, 48);
        gc.setStroke(Color.web("#deff9a"));
        gc.setLineWidth(3.0);
        gc.strokeArc(cx - 21, cy - 21, 42, 42,
                90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
        gc.setFill(Color.web("#deff9a"));
        gc.fillOval(cx - 3, cy - 3, 6, 6);
    }
}
