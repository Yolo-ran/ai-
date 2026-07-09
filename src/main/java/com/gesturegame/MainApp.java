package com.gesturegame;

import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureSocketServer;
import com.gesturegame.ui.LobbyController;
import com.gesturegame.ui.LoginController;
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
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static final int SERVER_PORT = 8765;

    private GestureSocketServer gestureSocketServer;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loginLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Login.fxml")));
        Parent loginRoot = loginLoader.load();
        LoginController loginController = loginLoader.getController();

        FXMLLoader lobbyLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Lobby.fxml")));
        Parent lobbyRoot = lobbyLoader.load();
        LobbyController lobbyController = lobbyLoader.getController();

        Scene loginScene = new Scene(loginRoot, 1280, 720);
        Scene lobbyScene = new Scene(lobbyRoot, 1280, 720);

        AppStateManager appStateManager = AppStateManager.getInstance();
        appStateManager.init(primaryStage);
        appStateManager.registerScene("LOGIN", loginScene);
        appStateManager.registerScene("LOBBY", lobbyScene);

        loginController.bindStateManager(appStateManager);
        lobbyController.bindStateManager(appStateManager);

        primaryStage.setTitle("AI 手势交互游戏大厅");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        appStateManager.switchState("LOGIN");
        primaryStage.show();

        gestureSocketServer = new GestureSocketServer(SERVER_PORT, loginController, lobbyController);
        gestureSocketServer.start();
        LOGGER.info(() -> "手势 Socket 服务已启动，端口: " + SERVER_PORT);
    }

    @Override
    public void stop() {
        if (gestureSocketServer != null) {
            try {
                gestureSocketServer.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "关闭手势 Socket 服务失败", e);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
