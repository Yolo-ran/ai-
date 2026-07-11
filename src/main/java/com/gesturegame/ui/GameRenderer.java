package com.gesturegame.ui;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
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
 *   <li>游戏结束（{@code isOver}）后切换至 GAME_OVER 态，等待握拳重玩或张手返回大厅</li>
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
    private static final int HOLD_FRAMES = 30; // 0.5秒

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
        if (gc == null) {
            return;
        }

        String state = AppStateManager.getInstance().getCurrentState();
        if (AppStateManager.STATE_GAME_OVER.equals(state)) {
            // 冻结最后一帧，结算提示已在 isOver 触发时写入 statusLabel
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
                statusLabel.setText("✋ 张手按住返回大厅");
            }
        }

        game.update(gesture);
        game.render(gc);

        if (gameNameLabel != null) {
            gameNameLabel.setText(game.getIcon() + "  " + game.getName());
        }
        if (scoreLabel != null) {
            scoreLabel.setText("分数: " + game.getScore());
        }

        if (game.isOver() && !gameOverHandled) {
            gameOverHandled = true;
            settling = true;
            if (statusLabel != null) {
                statusLabel.setText("游戏结束！得分: " + game.getScore() + "  ✊握拳重玩 | ✋张手回大厅");
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
                    LOGGER.info(() -> "[GameRenderer] 收到 BACK 指令，返回大厅");
                    exitToLobby();
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
                    LOGGER.info(() -> "[GameRenderer] 结算态收到 BACK，返回大厅");
                    settling = false;
                    exitToLobby();
                }
            }
        });
    }

    private void exitToLobby() {
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
        if (gameCanvas == null) return;
        GraphicsContext g = gameCanvas.getGraphicsContext2D();
        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();
        g.setFill(Color.web("#0f172a"));
        g.fillRect(0, 0, w, h);

        Difficulty[] diffs = Difficulty.values();
        double cardW = 180, cardH = 200, gap = 30;
        double totalW = cardW * 3 + gap * 2;
        double startX = (w - totalW) / 2;
        double cardY = h / 2 - cardH / 2;
        int hoveredIndex = -1;

        // 手势逻辑
        if (gesture != null && gesture.isHandDetected()) {
            double hx = gesture.getHandX() * w;
            double hy = gesture.getHandY() * h;
            for (int i = 0; i < 3; i++) {
                double cx = startX + i * (cardW + gap);
                if (hx >= cx && hx <= cx + cardW && hy >= cardY && hy <= cardY + cardH) {
                    hoveredIndex = i;
                    break;
                }
            }

            boolean isCompact = gesture.getGesture() != null
                    && (gesture.getGesture().name().equals("FIST")
                        || gesture.getGesture().name().equals("PEACE"));
            boolean isOpen = gesture.getGesture() != null
                    && gesture.getGesture().name().equals("OPEN");

            if (isCompact) {
                compactHoldFrames++;
                openHoldFrames = 0;
            } else if (isOpen) {
                openHoldFrames++;
                compactHoldFrames = 0;
            } else {
                compactHoldFrames = 0;
                openHoldFrames = 0;
            }
        } else {
            compactHoldFrames = 0;
            openHoldFrames = 0;
        }

        // 握拳确认 → 进入游戏
        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            GameInterface game = AppStateManager.getInstance().getActiveGame();
            if (game != null) {
                game.setDifficulty(selectedDifficulty);
                game.init((int) w, (int) h);
                LOGGER.info("难度选择确认: " + selectedDifficulty.getLabel() + " → " + game.getName());
            }
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return;
        }

        // 张开手 → 返回大厅
        if (openHoldFrames >= HOLD_FRAMES) {
            openHoldFrames = 0;
            LOGGER.info("难度选择取消，返回大厅");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_LOBBY);
            return;
        }

        if (hoveredIndex >= 0) selectedDifficulty = diffs[hoveredIndex];

        // 渲染
        for (int i = 0; i < 3; i++) {
            double cx = startX + i * (cardW + gap);
            boolean sel = i == hoveredIndex || (hoveredIndex < 0 && diffs[i] == selectedDifficulty);
            if (sel) {
                g.setFill(Color.web("#1e3a5f"));
                g.setStroke(Color.web("#deff9a"));
                g.setLineWidth(3);
            } else {
                g.setFill(Color.web("#111827"));
                g.setStroke(Color.web("#ffffff20"));
                g.setLineWidth(1);
            }
            g.fillRoundRect(cx, cardY, cardW, cardH, 16, 16);
            g.strokeRoundRect(cx, cardY, cardW, cardH, 16, 16);

            g.setFill(sel ? Color.WHITE : Color.web("#94a3b8"));
            g.setFont(javafx.scene.text.Font.font(18));
            javafx.scene.text.Text t = new javafx.scene.text.Text(diffs[i].getLabel());
            double tw = t.getLayoutBounds().getWidth();
            g.fillText(diffs[i].getLabel(), cx + (cardW - tw) / 2, cardY + 40);

            g.setFont(javafx.scene.text.Font.font(36));
            t = new javafx.scene.text.Text(diffs[i].getStars());
            tw = t.getLayoutBounds().getWidth();
            g.fillText(diffs[i].getStars(), cx + (cardW - tw) / 2, cardY + 100);

            String hint = i == 0 ? "5❤" : i == 1 ? "3❤" : "1❤";
            g.setFont(javafx.scene.text.Font.font(14));
            t = new javafx.scene.text.Text(hint);
            tw = t.getLayoutBounds().getWidth();
            g.fillText(hint, cx + (cardW - tw) / 2, cardY + cardH - 20);
        }

        // 手光标
        if (gesture != null && gesture.isHandDetected()) {
            double cx = gesture.getHandX() * w;
            double cy = gesture.getHandY() * h;
            g.setFill(Color.color(0.87, 1.0, 0.6, 0.1));
            g.fillOval(cx - 22, cy - 22, 44, 44);
            g.setStroke(Color.web("#deff9a"));
            g.setLineWidth(2.0);
            g.strokeOval(cx - 16, cy - 16, 32, 32);
            g.setFill(Color.color(0.87, 1.0, 0.6, 0.95));
            g.fillOval(cx - 3, cy - 3, 6, 6);
        }

        g.setFill(Color.web("#deff9a"));
        g.setFont(javafx.scene.text.Font.font(16));
        g.fillText("手移选难度 | 握拳确认 | 张开返回", w / 2 - 120, cardY + cardH + 50);
    }

    private void clearCanvas() {
        if (gc != null && gameCanvas != null) {
            gc.setFill(Color.web("#0f172a"));
            gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        }
    }
}
