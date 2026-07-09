package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureCommandResolver;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 游戏大厅传送带控制器，负责卡片切换与选中动画。
 */
public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());
    private static final int MAX_GAMES = 3;
    private static final double CARD_WIDTH = 320.0;
    private static final long NAVIGATION_COOLDOWN_MS = 180L;
    private static final long ACTION_COOLDOWN_MS = 500L;
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final String DATA_URI_SEPARATOR = ",";

    @FXML
    private HBox gameCardContainer;

    @FXML
    private ImageView cameraView;

    @FXML
    private VBox card0;

    @FXML
    private VBox card1;

    @FXML
    private VBox card2;

    private AppStateManager appStateManager;
    private int currentIndex;
    private long lastNavigationTime;
    private long lastActionTime;

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    @FXML
    public void initialize() {
        applySelectionStyle();
    }

    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!"LOBBY".equals(AppStateManager.getInstance().getCurrentState())) {
                return;
            }

            switch (command) {
                case SWIPE_LEFT:
                    navigate(1);
                    break;
                case SWIPE_RIGHT:
                    navigate(-1);
                    break;
                case CONFIRM:
                    launchGame();
                    break;
                case BACK:
                    if (appStateManager != null) {
                        returnToLogin();
                    }
                    break;
                default:
                    break;
            }
            LOGGER.info(() -> "大厅接收指令: " + command + ", confidence=" + confidence + ", hand=" + hand);
        });
    }

    /**
     * 兼容字符串手势入口，便于直接对接视觉端原始指令。
     */
    public void handleAgentCommand(String gesture) {
        handleAgentCommand(GestureCommandResolver.resolve(gesture), 1.0, "UNKNOWN");
    }

    /**
     * 线程安全地刷新大厅右上角摄像头预览画面。
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
            LOGGER.warning(() -> "大厅摄像头帧解码失败: " + e.getMessage());
        }
    }

    /**
     * 使用本地摄像头图像直接刷新右上角预览窗。
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

    private void navigate(int direction) {
        long now = System.currentTimeMillis();
        if (now - lastNavigationTime < NAVIGATION_COOLDOWN_MS) {
            return;
        }

        int nextIndex = currentIndex + direction;
        if (nextIndex < 0 || nextIndex >= MAX_GAMES) {
            return;
        }

        currentIndex = nextIndex;
        TranslateTransition transition = new TranslateTransition(Duration.millis(350), gameCardContainer);
        transition.setToX(-currentIndex * CARD_WIDTH);
        transition.play();
        applySelectionStyle();
        lastNavigationTime = now;
    }

    private void launchGame() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) {
            return;
        }

        LOGGER.info(() -> "[大厅] 准备启动游戏，ID 为: " + currentIndex);
        lastActionTime = now;
    }

    private void returnToLogin() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) {
            return;
        }

        appStateManager.switchState("LOGIN");
        lastActionTime = now;
    }

    private void applySelectionStyle() {
        VBox[] cards = {card0, card1, card2};
        for (int i = 0; i < cards.length; i++) {
            VBox card = cards[i];
            boolean selected = i == currentIndex;
            double targetScale = selected ? 1.2 : 0.92;
            card.setOpacity(selected ? 1.0 : 0.45);

            ScaleTransition st = new ScaleTransition(Duration.millis(260), card);
            st.setToX(targetScale);
            st.setToY(targetScale);
            ParallelTransition pt = new ParallelTransition(st);
            pt.play();
        }
    }
}
