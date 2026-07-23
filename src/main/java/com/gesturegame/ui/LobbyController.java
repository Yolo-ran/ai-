package com.gesturegame.ui;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.game.CatchFruit;
import com.gesturegame.game.FruitNinja;
import com.gesturegame.game.RPSGame;
import com.gesturegame.game.RhythmMaster;
import com.gesturegame.game.SideScrollingShooter;
import com.gesturegame.game.TarotGame;
import com.gesturegame.game.ZumaGame;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureCommandResolver;
import com.gesturegame.network.GestureStreamServer.DualHandState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Controls the GPU-native lobby, game selection and gesture routing. */
public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());
    private static final List<Supplier<GameInterface>> GAME_REGISTRY = List.of(
            CatchFruit::new,
            RPSGame::new,
            ZumaGame::new,
            TarotGame::new,
            FruitNinja::new,
            RhythmMaster::new,
            SideScrollingShooter::new);
    private static final int MAX_GAMES = GAME_REGISTRY.size();
    private static final long NAVIGATION_COOLDOWN_MS = 180L;
    private static final long ACTION_COOLDOWN_MS = 500L;
    private static final long CAMERA_INTERVAL_MS = 125L;
    private static final int CONFIRM_HOLD_FRAMES = 72;

    @FXML
    private StackPane planetLayer;
    @FXML
    private StackPane uiLayer;
    @FXML
    private Canvas cardCanvas;
    @FXML
    private Button apiButton;
    @FXML
    private ImageView cameraView;

    private AppStateManager appStateManager;
    private LobbyPlanetBackground planetBackground;
    private LobbyCardDeck cardDeck;
    private int currentIndex;
    private int confirmHoldFrames;
    private long lastNavigationTime;
    private long lastActionTime;
    private long lastCameraTime;

    @FXML
    public void initialize() {
        planetBackground = new LobbyPlanetBackground(planetLayer);
        cardDeck = new LobbyCardDeck(cardCanvas, this::selectGame,
                this::navigate, this::launchGame);

        cardCanvas.scaleXProperty().bind(uiLayer.widthProperty()
                .divide(LobbyCardDeck.DESIGN_WIDTH));
        cardCanvas.scaleYProperty().bind(uiLayer.heightProperty()
                .divide(LobbyCardDeck.DESIGN_HEIGHT));
        apiButton.setOnAction(event -> showApiSettings());
    }

    public void bindStateManager(AppStateManager manager) {
        appStateManager = manager;
        if (manager != null) {
            manager.registerStateActivationHandler(AppStateManager.STATE_LOBBY, this::activate);
        }
    }

    private void activate() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::activate);
            return;
        }
        confirmHoldFrames = 0;
        planetBackground.start();
        cardDeck.activate(currentIndex);
    }

    public void tick(GestureData gesture) {
        tick(gesture, new DualHandState(false, false, 0.0, false, false, 0.0, 0.0));
    }

    public void tick(GestureData gesture, DualHandState dualHands) {
        boolean handVisible = gesture != null && gesture.isHandDetected();
        GestureType type = handVisible ? gesture.getGesture() : GestureType.NONE;
        // A second hand is allowed to coexist with normal lobby navigation.
        // Input is reserved only while both hands explicitly form fists.
        if (dualHands != null && dualHands.bothFists()) {
            type = GestureType.NONE;
        }

        updateConfirmHold(type);
        double x = handVisible ? clamp01(gesture.getHandX()) : 0.5;
        double y = handVisible ? clamp01(gesture.getHandY()) : 0.5;
        cardDeck.setGesture(handVisible, x, y,
                (double) confirmHoldFrames / CONFIRM_HOLD_FRAMES);
        if (confirmHoldFrames >= CONFIRM_HOLD_FRAMES) {
            confirmHoldFrames = 0;
            launchGame();
        }
    }

    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!AppStateManager.STATE_LOBBY.equals(
                    AppStateManager.getInstance().getCurrentState())) {
                return;
            }
            switch (command) {
                case SWIPE_LEFT -> navigate(1);
                case SWIPE_RIGHT -> navigate(-1);
                case BACK -> {
                    pauseLobby();
                    if (appStateManager != null) {
                        appStateManager.switchState(AppStateManager.STATE_LOGIN);
                    }
                }
                case CONFIRM, NONE -> {
                    // Fist confirmation is accumulated by tick() to avoid duplicates.
                }
            }
            if (command != GestureCommand.NONE) {
                LOGGER.info(() -> "大厅接收指令: " + command
                        + ", confidence=" + confidence + ", hand=" + hand);
            }
        });
    }

    public void handleAgentCommand(String gesture) {
        handleAgentCommand(GestureCommandResolver.resolve(gesture), 1.0, "STREAM");
    }

    public void updateCameraStream(String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCameraTime < CAMERA_INTERVAL_MS) {
            return;
        }
        lastCameraTime = now;
        if (AppStateManager.STATE_LOBBY.equals(
                AppStateManager.getInstance().getCurrentState())) {
            CameraStreamHelper.push(cameraView, base64Image);
        }
    }

    public void updateCameraImage(Image image) {
        if (image != null) {
            Platform.runLater(() -> cameraView.setImage(image));
        }
    }

    private void navigate(int direction) {
        long now = System.currentTimeMillis();
        if (now - lastNavigationTime < NAVIGATION_COOLDOWN_MS) {
            return;
        }
        currentIndex = Math.floorMod(currentIndex + direction, MAX_GAMES);
        lastNavigationTime = now;
        cardDeck.setActive(currentIndex);
    }

    private void selectGame(int index) {
        if (index < 0 || index >= MAX_GAMES) {
            return;
        }
        currentIndex = index;
        lastNavigationTime = System.currentTimeMillis();
        cardDeck.setActive(currentIndex);
    }

    private void launchGame() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) {
            return;
        }
        GameInterface game = createGame(currentIndex);
        if (game == null || appStateManager == null) {
            LOGGER.warning(() -> "[大厅] 无法启动游戏，索引越界: " + currentIndex);
            return;
        }
        lastActionTime = now;
        LOGGER.info(() -> "[大厅] 启动游戏: " + game.getName() + " (索引 " + currentIndex + ")");
        pauseLobby();
        appStateManager.setActiveGame(game);
        appStateManager.switchState(AppStateManager.STATE_DIFFICULTY);
    }

    private GameInterface createGame(int index) {
        return index >= 0 && index < GAME_REGISTRY.size()
                ? GAME_REGISTRY.get(index).get() : null;
    }

    private void updateConfirmHold(GestureType gesture) {
        if (gesture == GestureType.FIST) {
            confirmHoldFrames = Math.min(confirmHoldFrames + 1, CONFIRM_HOLD_FRAMES);
        } else {
            confirmHoldFrames = 0;
        }
    }

    private void pauseLobby() {
        planetBackground.stop();
        cardDeck.pause();
    }

    private void showApiSettings() {
        Window owner = apiButton == null || apiButton.getScene() == null
                ? null : apiButton.getScene().getWindow();
        ApiSettingsController.show(owner);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
