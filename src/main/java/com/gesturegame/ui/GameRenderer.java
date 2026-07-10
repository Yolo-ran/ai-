package com.gesturegame.ui;

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

    private void clearCanvas() {
        if (gc != null && gameCanvas != null) {
            gc.setFill(Color.web("#0f172a"));
            gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        }
    }
}
