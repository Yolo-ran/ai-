package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * 游戏大厅传送带控制器，负责卡片切换与选中动画。
 */
public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());
    private static final int MAX_GAMES = 3;
    private static final double CARD_WIDTH = 320.0;

    @FXML
    private HBox gameCardContainer;

    @FXML
    private VBox card0;

    @FXML
    private VBox card1;

    @FXML
    private VBox card2;

    private AppStateManager appStateManager;
    private int currentIndex;

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    @FXML
    public void initialize() {
        applySelectionStyle();
    }

    public void handleAgentCommand(String gesture, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!"LOBBY".equals(AppStateManager.getInstance().getCurrentState())) {
                return;
            }

            switch (gesture) {
                case "SWIPE_LEFT":
                    navigate(1);
                    break;
                case "SWIPE_RIGHT":
                    navigate(-1);
                    break;
                case "CONFIRM":
                    launchGame();
                    break;
                case "BACK":
                    if (appStateManager != null) {
                        appStateManager.switchState("LOGIN");
                    }
                    break;
                default:
                    break;
            }
            LOGGER.info(() -> "大厅接收指令: " + gesture + ", confidence=" + confidence + ", hand=" + hand);
        });
    }

    private void navigate(int direction) {
        int nextIndex = currentIndex + direction;
        if (nextIndex < 0 || nextIndex >= MAX_GAMES) {
            return;
        }

        currentIndex = nextIndex;
        TranslateTransition transition = new TranslateTransition(Duration.millis(350), gameCardContainer);
        transition.setToX(-currentIndex * CARD_WIDTH);
        transition.play();
        applySelectionStyle();
    }

    private void launchGame() {
        LOGGER.info(() -> "[大厅] 准备启动游戏，ID 为: " + currentIndex);
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
