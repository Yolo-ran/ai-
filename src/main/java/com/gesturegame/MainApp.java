package com.gesturegame;

import com.gesturegame.camera.CameraManager;
import com.gesturegame.camera.HandDetector;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureStreamServer;
import com.gesturegame.ui.LobbyController;
import com.gesturegame.ui.LoginController;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 应用启动入口，负责初始化场景、状态机和手势通信服务。
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static final int SERVER_PORT = 8765;
    private static final boolean ENABLE_LOCAL_CAMERA_FALLBACK = false;
    private static final int CAMERA_REFRESH_MS = 33;
    private static final double SWIPE_VELOCITY_THRESHOLD = 24.0;
    private static final double SWIPE_DISTANCE_THRESHOLD = 0.10;
    private static final int DETECTION_INTERVAL = 1;
    private static final int COMPACT_HAND_HOLD_FRAMES = 5;
    private static final int OPEN_HAND_HOLD_FRAMES = 3;
    private static final double STABLE_HAND_VELOCITY_THRESHOLD = 10.0;
    private static final long COMMAND_COOLDOWN_MS = 380L;

    private GestureStreamServer gestureStreamServer;
    private final CameraManager cameraManager = new CameraManager();
    private final HandDetector handDetector = new HandDetector();
    private ScheduledExecutorService cameraExecutor;
    private GestureData latestGestureData = new GestureData();
    private WritableImage reusableFxImage;
    private int frameCounter;
    private int compactHandHoldFrames;
    private int openHandHoldFrames;
    private long lastCommandTime;

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

        if (ENABLE_LOCAL_CAMERA_FALLBACK) {
            startLocalCameraPreview(loginController, lobbyController);
        } else {
            LOGGER.info("当前以混合模式启动：停用 Java 本地摄像头链路，等待 Python 端通过 WebSocket 推送手势数据");
        }
        gestureStreamServer = new GestureStreamServer(SERVER_PORT, loginController, lobbyController);
        gestureStreamServer.start();
        LOGGER.info(() -> "手势图像串流服务已启动，端口: " + SERVER_PORT);
    }

    private void startLocalCameraPreview(LoginController loginController, LobbyController lobbyController) {
        cameraManager.start();
        cameraExecutor = Executors.newSingleThreadScheduledExecutor();
        cameraExecutor.scheduleAtFixedRate(() -> pushCameraFrame(loginController, lobbyController),
                0,
                CAMERA_REFRESH_MS,
                TimeUnit.MILLISECONDS);
    }

    private void pushCameraFrame(LoginController loginController, LobbyController lobbyController) {
        BufferedImage frame = cameraManager.getFrame();
        if (frame == null) {
            return;
        }

        frameCounter++;
        if (frameCounter % DETECTION_INTERVAL == 0 || !latestGestureData.isHandDetected()) {
            latestGestureData = handDetector.detect(frame);
        }
        GestureData gestureData = latestGestureData;

        Image image = convertToFxImage(frame);
        if (image == null) {
            return;
        }

        String currentState = AppStateManager.getInstance().getCurrentState();
        if ("LOGIN".equals(currentState)) {
            loginController.updateCameraImage(image);
        } else if ("LOBBY".equals(currentState)) {
            lobbyController.updateCameraImage(image);
        }

        dispatchLocalGesture(currentState, gestureData, loginController, lobbyController);
    }

    private void dispatchLocalGesture(String state,
                                      GestureData gestureData,
                                      LoginController loginController,
                                      LobbyController lobbyController) {
        GestureCommand command = mapGestureToCommand(gestureData);
        if (command == GestureCommand.NONE) {
            return;
        }

        double confidence = estimateConfidence(command, gestureData);
        if ("LOGIN".equals(state)) {
            loginController.handleAgentCommand(command, confidence, "LOCAL_CAMERA");
        } else if ("LOBBY".equals(state)) {
            lobbyController.handleAgentCommand(command, confidence, "LOCAL_CAMERA");
        }
    }

    private GestureCommand mapGestureToCommand(GestureData gestureData) {
        if (!gestureData.isHandDetected()) {
            compactHandHoldFrames = 0;
            openHandHoldFrames = 0;
            return GestureCommand.NONE;
        }

        if (Math.abs(gestureData.getVelocityX()) > SWIPE_VELOCITY_THRESHOLD
                && Math.abs(gestureData.getHandX() - gestureData.getPrevHandX()) > SWIPE_DISTANCE_THRESHOLD
                && Math.abs(gestureData.getVelocityX()) > Math.abs(gestureData.getVelocityY())) {
            compactHandHoldFrames = 0;
            openHandHoldFrames = 0;
            return allowCommand(gestureData.getVelocityX() > 0
                    ? GestureCommand.SWIPE_RIGHT
                    : GestureCommand.SWIPE_LEFT);
        }

        GestureType currentGesture = gestureData.getGesture();
        boolean stableHand = isStableHand(gestureData);

        if (currentGesture == GestureType.OPEN && stableHand) {
            openHandHoldFrames++;
        } else {
            openHandHoldFrames = 0;
        }

        if (isCompactHandGesture(currentGesture) && stableHand) {
            compactHandHoldFrames++;
        } else {
            compactHandHoldFrames = 0;
        }

        if (openHandHoldFrames >= OPEN_HAND_HOLD_FRAMES) {
            compactHandHoldFrames = 0;
            return allowCommand(GestureCommand.BACK);
        }
        if (compactHandHoldFrames >= COMPACT_HAND_HOLD_FRAMES) {
            openHandHoldFrames = 0;
            return allowCommand(GestureCommand.CONFIRM);
        }
        return GestureCommand.NONE;
    }

    private GestureCommand allowCommand(GestureCommand command) {
        long now = System.currentTimeMillis();
        if (now - lastCommandTime < COMMAND_COOLDOWN_MS) {
            return GestureCommand.NONE;
        }
        lastCommandTime = now;
        return command;
    }

    private boolean isStableHand(GestureData gestureData) {
        return Math.abs(gestureData.getVelocityX()) < STABLE_HAND_VELOCITY_THRESHOLD
                && Math.abs(gestureData.getVelocityY()) < STABLE_HAND_VELOCITY_THRESHOLD;
    }

    private boolean isCompactHandGesture(GestureType gestureType) {
        return gestureType == GestureType.FIST || gestureType == GestureType.PEACE;
    }

    private double estimateConfidence(GestureCommand command, GestureData gestureData) {
        switch (command) {
            case SWIPE_LEFT:
            case SWIPE_RIGHT:
                return Math.min(0.99, Math.abs(gestureData.getVelocityX()) / 45.0);
            case CONFIRM:
            case BACK:
                return 0.92;
            default:
                return 0.50;
        }
    }

    private Image convertToFxImage(BufferedImage frame) {
        try {
            reusableFxImage = SwingFXUtils.toFXImage(frame, reusableFxImage);
            return reusableFxImage;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "本地摄像头画面转换失败", e);
            return null;
        }
    }

    @Override
    public void stop() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
        }
        cameraManager.stop();

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
