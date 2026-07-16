package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import com.gesturegame.persistence.UserAccountStore;
import com.gesturegame.persistence.UserAccountStore.RegistrationResult;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 视频英雄页账户入口。账号验证成功后，继续进入原有 ENTER 手势验证页。 */
public final class AuthController {

    private static final Logger LOGGER = Logger.getLogger(AuthController.class.getName());
    private static final String VIDEO_RESOURCE = "/assets/auth/web3-hero.mp4";

    @FXML private StackPane rootPane;
    @FXML private MediaView mediaView;
    @FXML private HBox navigationLinks;
    @FXML private Text heroHeading;
    @FXML private StackPane authModal;
    @FXML private VBox authCard;
    @FXML private Label authEyebrow;
    @FXML private Label authTitle;
    @FXML private Label authSubtitle;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private VBox confirmPasswordWrap;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button submitButton;
    @FXML private Button switchButton;
    @FXML private Label statusLabel;
    @FXML private Label databaseLabel;

    private AppStateManager stateManager;
    private UserAccountStore accountStore;
    private MediaPlayer mediaPlayer;
    private boolean registrationMode;
    private boolean submitting;

    @FXML
    public void initialize() {
        loadFonts();
        heroHeading.setFill(new LinearGradient(
                0.08, 0.0, 0.92, 1.0, true, null,
                new Stop(0.0, Color.WHITE),
                new Stop(0.46, Color.web("#f4f4f4", 0.94)),
                new Stop(0.82, Color.web("#b7b7b7", 0.58)),
                new Stop(1.0, Color.TRANSPARENT)));

        authModal.setVisible(false);
        authModal.setManaged(false);
        rootPane.widthProperty().addListener((ignored, oldValue, newValue) -> {
            navigationLinks.setVisible(newValue.doubleValue() >= 940.0);
            navigationLinks.setManaged(newValue.doubleValue() >= 940.0);
            layoutVideo();
        });
        rootPane.heightProperty().addListener((ignored, oldValue, newValue) -> layoutVideo());

        try {
            accountStore = new UserAccountStore();
            registrationMode = !accountStore.hasUsers();
            databaseLabel.setText("Encrypted local profile  •  " + accountStore.getDatabasePath().getFileName());
        } catch (RuntimeException error) {
            LOGGER.log(Level.SEVERE, "账户数据库初始化失败", error);
            statusLabel.setText("本地账户数据库不可用，请检查启动日志");
            statusLabel.getStyleClass().add("auth-status-error");
            submitButton.setDisable(true);
        }

        Platform.runLater(this::startVideo);
    }

    public void bindStateManager(AppStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @FXML
    private void openApiSettings() {
        Window owner = rootPane.getScene() == null ? null : rootPane.getScene().getWindow();
        ApiSettingsController.show(owner);
    }

    @FXML
    private void showAuthModal() {
        updateFormMode();
        authModal.setManaged(true);
        authModal.setVisible(true);
        authModal.setOpacity(0.0);
        authCard.setScaleX(0.96);
        authCard.setScaleY(0.96);
        FadeTransition fade = new FadeTransition(Duration.millis(260), authModal);
        fade.setToValue(1.0);
        fade.play();
        ScaleTransition scale = new ScaleTransition(Duration.millis(320), authCard);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.play();
        Platform.runLater(usernameField::requestFocus);
    }

    @FXML
    private void hideAuthModal() {
        if (submitting) return;
        FadeTransition fade = new FadeTransition(Duration.millis(180), authModal);
        fade.setToValue(0.0);
        fade.setOnFinished(event -> {
            authModal.setVisible(false);
            authModal.setManaged(false);
        });
        fade.play();
    }

    @FXML
    private void toggleFormMode() {
        if (submitting) return;
        registrationMode = !registrationMode;
        updateFormMode();
    }

    @FXML
    private void submit() {
        if (submitting || accountStore == null) return;

        String username = usernameField.getText() == null ? "" : usernameField.getText().strip();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (registrationMode && !password.equals(confirmPasswordField.getText())) {
            showStatus("两次输入的密码不一致", true);
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            showStatus("请输入用户名和密码", true);
            return;
        }

        setSubmitting(true);
        CompletableFuture.supplyAsync(() -> {
            if (registrationMode) {
                RegistrationResult result = accountStore.register(username, password.toCharArray());
                return new AuthResult(result.success(), result.message());
            }
            boolean accepted = accountStore.authenticate(username, password.toCharArray());
            return new AuthResult(accepted, accepted ? "登录成功" : "用户名或密码不正确");
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            setSubmitting(false);
            passwordField.clear();
            confirmPasswordField.clear();
            if (error != null) {
                LOGGER.log(Level.WARNING, "账户验证失败", error);
                showStatus("账户服务暂时不可用，请查看启动日志", true);
            } else if (!result.success()) {
                showStatus(result.message(), true);
            } else {
                showStatus(registrationMode ? "账户已创建，正在进入手势验证" : "验证通过，正在进入手势验证", false);
                completeAuthentication(username);
            }
        }));
    }

    private void completeAuthentication(String username) {
        PauseTransition pause = new PauseTransition(Duration.millis(520));
        pause.setOnFinished(event -> {
            if (mediaPlayer != null) mediaPlayer.stop();
            if (stateManager != null) {
                stateManager.markAccountAuthenticated(username);
                stateManager.switchState(AppStateManager.STATE_LOGIN);
            }
        });
        pause.play();
    }

    private void updateFormMode() {
        authEyebrow.setText(registrationMode ? "CREATE LOCAL PROFILE" : "WELCOME BACK");
        authTitle.setText(registrationMode ? "Create your account" : "Sign in to continue");
        authSubtitle.setText(registrationMode
                ? "Your profile stays on this device. Nothing is uploaded."
                : "Continue to the ENTER gesture verification experience.");
        confirmPasswordWrap.setVisible(registrationMode);
        confirmPasswordWrap.setManaged(registrationMode);
        submitButton.setText(registrationMode ? "Create account  →" : "Sign in  →");
        switchButton.setText(registrationMode ? "Already have an account?  Sign in" : "New here?  Create an account");
        showStatus("", false);
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        submitButton.setDisable(value);
        submitButton.setText(value ? "Please wait…" : registrationMode ? "Create account  →" : "Sign in  →");
        switchButton.setDisable(value);
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("auth-status-error", "auth-status-success");
        if (!message.isBlank()) {
            statusLabel.getStyleClass().add(error ? "auth-status-error" : "auth-status-success");
        }
    }

    private void startVideo() {
        try {
            Media media = new Media(resolveMediaUri());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setMute(true);
            mediaPlayer.setVolume(0.0);
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            mediaPlayer.setAutoPlay(true);
            mediaPlayer.setOnReady(() -> {
                layoutVideo();
                mediaPlayer.play();
                FadeTransition reveal = new FadeTransition(Duration.millis(1100), mediaView);
                reveal.setToValue(1.0);
                reveal.play();
            });
            mediaPlayer.setOnError(() -> LOGGER.log(Level.WARNING,
                    "视频背景播放失败，使用纯黑背景", mediaPlayer.getError()));
            mediaView.setMediaPlayer(mediaPlayer);
        } catch (RuntimeException | IOException error) {
            LOGGER.log(Level.WARNING, "无法加载登录页视频背景，使用纯黑背景", error);
        }
    }

    private void layoutVideo() {
        if (mediaPlayer == null || mediaPlayer.getMedia() == null) return;
        double videoWidth = mediaPlayer.getMedia().getWidth();
        double videoHeight = mediaPlayer.getMedia().getHeight();
        double paneWidth = rootPane.getWidth();
        double paneHeight = rootPane.getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || paneWidth <= 0 || paneHeight <= 0) return;

        double scale = Math.max(paneWidth / videoWidth, paneHeight / videoHeight);
        mediaView.setFitWidth(videoWidth * scale);
        mediaView.setFitHeight(videoHeight * scale);
        mediaView.setPreserveRatio(true);
    }

    private static String resolveMediaUri() throws IOException {
        URL resource = AuthController.class.getResource(VIDEO_RESOURCE);
        if (resource == null) throw new IOException("找不到资源 " + VIDEO_RESOURCE);
        if ("file".equalsIgnoreCase(resource.getProtocol())) return resource.toExternalForm();

        Path extracted = Path.of(System.getProperty("java.io.tmpdir"), "ai-gesture-game", "web3-hero.mp4");
        Files.createDirectories(extracted.getParent());
        if (!Files.exists(extracted) || Files.size(extracted) < 1024) {
            try (InputStream input = AuthController.class.getResourceAsStream(VIDEO_RESOURCE)) {
                if (input == null) throw new IOException("无法读取资源 " + VIDEO_RESOURCE);
                Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return extracted.toUri().toString();
    }

    private static void loadFonts() {
        loadFont("/fonts/GeneralSans-Regular.ttf", 14);
        loadFont("/fonts/GeneralSans-Medium.ttf", 14);
        loadFont("/fonts/GeneralSans-Semibold.ttf", 14);
    }

    private static void loadFont(String resource, double size) {
        try (InputStream input = AuthController.class.getResourceAsStream(resource)) {
            if (input != null) Font.loadFont(input, size);
        } catch (IOException error) {
            LOGGER.log(Level.FINE, "字体加载失败: " + resource, error);
        }
    }

    private record AuthResult(boolean success, String message) {}
}
