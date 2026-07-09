package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * 登录界面控制器，负责处理签入提示和确认动画。
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private static final double CONFIRM_THRESHOLD = 0.80;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator confirmProgress;

    private AppStateManager appStateManager;
    private boolean signingIn;

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    @FXML
    public void initialize() {
        confirmProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        confirmProgress.setVisible(true);
        statusLabel.setText("正在等待手势签入...");
    }

    public void handleAgentCommand(String gesture, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!"LOGIN".equals(AppStateManager.getInstance().getCurrentState())) {
                return;
            }

            if ("CONFIRM".equals(gesture) && confidence >= CONFIRM_THRESHOLD && !signingIn) {
                startConfirmSequence(hand, confidence);
            } else if (!"CONFIRM".equals(gesture) && !signingIn) {
                statusLabel.setText("正在等待手势签入...");
                confirmProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            }
        });
    }

    private void startConfirmSequence(String hand, double confidence) {
        signingIn = true;
        statusLabel.setText(String.format("检测到 %s 手确认手势，正在完成签入...", hand));
        confirmProgress.setProgress(0.0);
        LOGGER.info(() -> "登录界面开始处理 CONFIRM 指令, confidence=" + confidence);

        PauseTransition stepOne = new PauseTransition(Duration.millis(250));
        stepOne.setOnFinished(event -> confirmProgress.setProgress(0.35));

        PauseTransition stepTwo = new PauseTransition(Duration.millis(550));
        stepTwo.setOnFinished(event -> confirmProgress.setProgress(0.72));

        PauseTransition stepThree = new PauseTransition(Duration.millis(850));
        stepThree.setOnFinished(event -> {
            confirmProgress.setProgress(1.0);
            statusLabel.setText("签入成功，正在进入游戏大厅...");
        });

        PauseTransition switchDelay = new PauseTransition(Duration.millis(1150));
        switchDelay.setOnFinished(event -> {
            signingIn = false;
            if (appStateManager != null) {
                appStateManager.switchState("LOBBY");
            }
        });

        stepOne.play();
        stepTwo.play();
        stepThree.play();
        switchDelay.play();
    }
}
