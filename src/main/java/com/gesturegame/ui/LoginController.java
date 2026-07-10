package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 登录界面控制器，负责处理签入提示和确认动画。
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private static final double CONFIRM_THRESHOLD = 0.80;
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final String DATA_URI_SEPARATOR = ",";

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator confirmProgress;

    @FXML
    private ImageView cameraView;

    private AppStateManager appStateManager;
    private boolean signingIn;
    private long lastIdleUpdateTime;

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    @FXML
    public void initialize() {
        confirmProgress.setProgress(0.0);
        confirmProgress.setVisible(false);
        statusLabel.setText("正在等待握拳手势签入...");
    }

    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!"LOGIN".equals(AppStateManager.getInstance().getCurrentState())) {
                return;
            }

            if (command == GestureCommand.CONFIRM && confidence >= CONFIRM_THRESHOLD && !signingIn) {
                startConfirmSequence(hand, confidence);
            } else if (!signingIn) {
                refreshIdleStatus(command);
            }
        });
    }

    /**
     * 线程安全地刷新登录页摄像头画面。
     */
    public void updateCameraStream(String base64Image) {
        try {
            if (base64Image == null || base64Image.isBlank()) {
                return;
            }

            String payload = base64Image;
            int separatorIndex = payload.indexOf(DATA_URI_SEPARATOR);
            if (separatorIndex >= 0) {
                payload = payload.substring(separatorIndex + 1);
            }

            byte[] imageBytes = BASE64_DECODER.decode(payload);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            Platform.runLater(() -> {
                if (cameraView != null) {
                    cameraView.setImage(image);
                }
            });
        } catch (Exception e) {
            LOGGER.warning(() -> "登录页摄像头帧解码失败: " + e.getMessage());
        }
    }

    /**
     * 使用本地摄像头图像直接刷新预览区域。
     */
    public void updateCameraImage(Image image) {
        if (image == null) {
            return;
        }

        Platform.runLater(() -> {
            if (cameraView != null) {
                cameraView.setImage(image);
            }
        });
    }

    private void refreshIdleStatus(GestureCommand command) {
        long now = System.currentTimeMillis();
        if (now - lastIdleUpdateTime < 200) {
            return;
        }

        switch (command) {
            case SWIPE_LEFT:
                statusLabel.setText("检测到左划手势，登录页当前不响应该动作");
                confirmProgress.setVisible(false);
                confirmProgress.setProgress(0.0);
                break;
            case SWIPE_RIGHT:
                statusLabel.setText("检测到右划手势，登录页当前不响应该动作");
                confirmProgress.setVisible(false);
                confirmProgress.setProgress(0.0);
                break;
            case BACK:
                statusLabel.setText("检测到返回手势，请使用握拳手势进入大厅");
                confirmProgress.setVisible(false);
                confirmProgress.setProgress(0.0);
                break;
            default:
                statusLabel.setText("正在等待握拳手势签入...");
                confirmProgress.setVisible(false);
                confirmProgress.setProgress(0.0);
                break;
        }
        lastIdleUpdateTime = now;
    }

    private void startConfirmSequence(String hand, double confidence) {
        signingIn = true;
        statusLabel.setText(String.format("检测到 %s 手确认手势，正在完成签入...", hand));
        confirmProgress.setVisible(true);
        confirmProgress.setProgress(0.0);
        LOGGER.info(() -> "登录界面开始处理 CONFIRM 指令, confidence=" + confidence);

        PauseTransition stepOne = new PauseTransition(Duration.millis(120));
        stepOne.setOnFinished(event -> confirmProgress.setProgress(0.35));

        PauseTransition stepTwo = new PauseTransition(Duration.millis(140));
        stepTwo.setOnFinished(event -> confirmProgress.setProgress(0.72));

        PauseTransition stepThree = new PauseTransition(Duration.millis(160));
        stepThree.setOnFinished(event -> {
            confirmProgress.setProgress(1.0);
            statusLabel.setText("签入成功，正在进入游戏大厅...");
        });

        PauseTransition switchDelay = new PauseTransition(Duration.millis(120));
        switchDelay.setOnFinished(event -> {
            signingIn = false;
            confirmProgress.setVisible(false);
            confirmProgress.setProgress(0.0);
            if (appStateManager != null) {
                appStateManager.switchState("LOBBY");
            }
        });

        SequentialTransition sequence = new SequentialTransition(stepOne, stepTwo, stepThree, switchDelay);
        sequence.play();
    }
}
