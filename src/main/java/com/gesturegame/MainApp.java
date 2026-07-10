package com.gesturegame;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureStreamServer;
import com.gesturegame.ui.GameRenderer;
import com.gesturegame.ui.LobbyController;
import com.gesturegame.ui.LoginController;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 应用启动入口，负责初始化场景、状态机和手势通信服务。
 *
 * <p>混合架构：Java 仅负责游戏大厅 + UI 渲染，手势数据由 Python 视觉端通过
 * WebSocket（{@link GestureStreamServer}）推送，本进程不再持有本地摄像头链路。
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static final int SERVER_PORT = 8765;

    private GestureStreamServer gestureStreamServer;
    private GameRenderer gameRenderer;
    private LobbyController lobbyController;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loginLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Login.fxml")));
        Parent loginRoot = loginLoader.load();
        LoginController loginController = loginLoader.getController();

        FXMLLoader lobbyLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Lobby.fxml")));
        Parent lobbyRoot = lobbyLoader.load();
        lobbyController = lobbyLoader.getController();

        FXMLLoader gameLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Game.fxml")));
        Parent gameRoot = gameLoader.load();
        gameRenderer = gameLoader.getController();

        Scene loginScene = new Scene(loginRoot, 1280, 720);
        Scene lobbyScene = new Scene(lobbyRoot, 1280, 720);
        Scene gameScene = new Scene(gameRoot, 1280, 720);

        AppStateManager appStateManager = AppStateManager.getInstance();
        appStateManager.init(primaryStage);
        appStateManager.registerScene(AppStateManager.STATE_LOGIN, loginScene);
        appStateManager.registerScene(AppStateManager.STATE_LOBBY, lobbyScene);
        appStateManager.registerScene(AppStateManager.STATE_GAME, gameScene);

        loginController.bindStateManager(appStateManager);
        lobbyController.bindStateManager(appStateManager);
        gameRenderer.bindStateManager(appStateManager);

        primaryStage.setTitle("AI 手势交互游戏大厅");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        appStateManager.switchState(AppStateManager.STATE_LOGIN);
        primaryStage.show();

        startGameLoop();

        LOGGER.info("当前以混合模式启动：停用 Java 本地摄像头链路，等待 Python 端通过 WebSocket 推送手势数据");
        gestureStreamServer = new GestureStreamServer(SERVER_PORT, loginController, lobbyController, gameRenderer);
        gestureStreamServer.start();
        LOGGER.info(() -> "手势图像串流服务已启动，端口: " + SERVER_PORT);
    }

    /**
     * 启动 60fps 渲染循环：LOBBY 态驱动大厅粒子宇宙，GAME/GAME_OVER 态驱动游戏渲染。
     * GAME_OVER 复用 GAME 场景（不单独注册），仅由 {@link GameRenderer} 冻结画面与显示结算。
     */
    private void startGameLoop() {
        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                String state = AppStateManager.getInstance().getCurrentState();
                GestureData gesture = gestureStreamServer.getLatestGesture();
                if (AppStateManager.STATE_LOBBY.equals(state)) {
                    lobbyController.tick(gesture);
                } else if (AppStateManager.STATE_GAME.equals(state)
                        || AppStateManager.STATE_GAME_OVER.equals(state)) {
                    GameInterface game = AppStateManager.getInstance().getActiveGame();
                    gameRenderer.tick(gesture, game);
                }
            }
        };
        gameLoop.start();
        LOGGER.info(() -> "渲染循环已启动 (AnimationTimer)");
    }

    @Override
    public void stop() {
        if (gestureStreamServer != null) {
            try {
                gestureStreamServer.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "关闭手势图像串流服务失败", e);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
