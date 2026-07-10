package com.gesturegame.ui;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * 游戏场景渲染器，同时作为 {@code Game.fxml} 的控制器。
 *
 * <p>职责：
 * <ul>
 *   <li>管理全屏 {@link Canvas} 与 {@link GraphicsContext}</li>
 *   <li>{@link #tick(GestureData, GameInterface)} 每帧由 {@code AnimationTimer} 驱动，
 *       调用 {@code game.update} / {@code game.render}，并刷新 HUD</li>
 *   <li>游戏结束（{@code isOver}）后延时返回大厅</li>
 *   <li>接收 {@code BACK} 命令（张手按住）即时返回大厅</li>
 * </ul>
 */
public class GameRenderer {

    private static final Logger LOGGER = Logger.getLogger(GameRenderer.class.getName());
    private static final long GAME_OVER_RETURN_DELAY_MS = 3000L;

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

    @FXML
    public void initialize() {
        gc = gameCanvas.getGraphicsContext2D();
        if (gameCanvas.getParent() instanceof Pane) {
            Pane parent = (Pane) gameCanvas.getParent();
            gameCanvas.widthProperty().bind(parent.widthProperty());
            gameCanvas.heightProperty().bind(parent.heightProperty());
        }
        clearCanvas();
    }

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    public Canvas getCanvas() {
        return gameCanvas;
    }

    /**
     * 游戏循环每帧调用：更新 + 渲染 + 刷新 HUD + 检测游戏结束。
     * 仅在 GAME 状态下由 MainApp 的 AnimationTimer 调用。
     */
    public void tick(GestureData gesture, GameInterface game) {
        if (gc == null) {
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
            int w = (int) Math.max(1, gameCanvas.getWidth());
            int h = (int) Math.max(1, gameCanvas.getHeight());
            game.init(w, h);
            if (statusLabel != null) {
                statusLabel.setText("✋ 张手按住返回大厅");
            }
            LOGGER.info(() -> "[GameRenderer] 初始化游戏: " + game.getName() + " (" + w + "x" + h + ")");
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
            if (statusLabel != null) {
                statusLabel.setText("游戏结束！得分: " + game.getScore() + "  即将返回大厅...");
            }
            LOGGER.info(() -> "[GameRenderer] 游戏结束: " + game.getName() + " 得分=" + game.getScore());
            PauseTransition delay = new PauseTransition(Duration.millis(GAME_OVER_RETURN_DELAY_MS));
            delay.setOnFinished(e -> {
                game.reset();
                gameOverHandled = false;
                currentGame = null;
                if (appStateManager != null) {
                    appStateManager.switchState(AppStateManager.STATE_LOBBY);
                }
            });
            delay.play();
        }
    }

    /**
     * 接收串流服务器派发的手势命令；GAME 状态下仅响应 BACK（返回大厅）。
     */
    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!AppStateManager.STATE_GAME.equals(AppStateManager.getInstance().getCurrentState())) {
                return;
            }
            if (command == GestureCommand.BACK) {
                LOGGER.info(() -> "[GameRenderer] 收到 BACK 指令，返回大厅");
                GameInterface game = AppStateManager.getInstance().getActiveGame();
                if (game != null) {
                    game.reset();
                }
                currentGame = null;
                gameOverHandled = false;
                if (appStateManager != null) {
                    appStateManager.switchState(AppStateManager.STATE_LOBBY);
                }
            }
        });
    }

    private void clearCanvas() {
        if (gc != null && gameCanvas != null) {
            gc.setFill(Color.web("#0f172a"));
            gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        }
    }
}
