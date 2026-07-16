package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.io.File;

/**
 * web3-hero 风格登录：帧动画视频背景 + 玻璃态登录卡 → 星空 ENTER 界面
 */
public class LoginController {

    private static final double CHANGE_EVENT_TIME = 0.32;
    private static final double CAMERA_Z = -400.0;
    private static final double CAMERA_TRAVEL_DISTANCE = 3400.0;
    private static final double LOOP_SECONDS = 15.0;
    private static final int NUMBER_OF_STARS = 5000;
    private static final int TRAIL_LENGTH = 80;
    private static final String VALID_USER = "admin";
    private static final String VALID_PASS = "123456";

    @FXML private StackPane rootPane;
    @FXML private ImageView bgImage;
    @FXML private Canvas starCanvas;
    @FXML private Label enterLabel;
    @FXML private VBox loginCard;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label loginStatus;
    @FXML private StackPane loginBtn;
    @FXML private Label loginBtnText;

    private AppStateManager appStateManager;
    private AnimationTimer starTimer;
    private long starStartNanos;
    private AnimationTimer videoTimer;
    private Image[] videoFrames;
    private int videoFrameIdx;
    private boolean entering;
    private boolean loggedIn;
    private boolean active;

    // Star arrays
    private final double[] dx = new double[NUMBER_OF_STARS];
    private final double[] dy = new double[NUMBER_OF_STARS];
    private final double[] spiralLocation = new double[NUMBER_OF_STARS];
    private final double[] strokeWeightFactor = new double[NUMBER_OF_STARS];
    private final double[] z = new double[NUMBER_OF_STARS];
    private final double[] angle = new double[NUMBER_OF_STARS];
    private final double[] distance = new double[NUMBER_OF_STARS];
    private final double[] rotationDirection = new double[NUMBER_OF_STARS];
    private final double[] expansionRate = new double[NUMBER_OF_STARS];
    private final double[] finalScale = new double[NUMBER_OF_STARS];

    @FXML
    public void initialize() {
        starCanvas.widthProperty().bind(rootPane.widthProperty());
        starCanvas.heightProperty().bind(rootPane.heightProperty());
        initVideo();
        passwordField.setOnAction(e -> tryLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());
    }

    private void initVideo() {
        try {
            File framesDir = new File("src/main/resources/assets/frames");
            if (!framesDir.exists()) framesDir = new File("target/classes/assets/frames");
            if (framesDir.exists()) {
                java.util.List<Image> list = new java.util.ArrayList<>();
                for (int i = 0; ; i += 2) {
                    File f = new File(framesDir, String.format("f%04d.jpg", i));
                    if (!f.exists()) break;
                    list.add(new Image(f.toURI().toString()));
                }
                if (!list.isEmpty()) {
                    videoFrames = list.toArray(new Image[0]);
                    bgImage.setImage(videoFrames[0]);
                    bgImage.fitWidthProperty().bind(rootPane.widthProperty());
                    bgImage.fitHeightProperty().bind(rootPane.heightProperty());
                    final long interval = 42_000_000L;
                    final long[] last = {0};
                    videoTimer = new AnimationTimer() {
                        @Override
                        public void handle(long now) {
                            if (now - last[0] < interval) return;
                            last[0] = now;
                            videoFrameIdx = (videoFrameIdx + 1) % videoFrames.length;
                            bgImage.setImage(videoFrames[videoFrameIdx]);
                        }
                    };
                    videoTimer.start();
                    return;
                }
            }
        } catch (Exception ignored) {}
        rootPane.setStyle("-fx-background-color: #050510;");
    }

    @FXML
    private void onLoginClick() { tryLogin(); }

    public void bindStateManager(AppStateManager asm) { this.appStateManager = asm; }

    public void activate() {
        active = true;
        if (videoTimer != null) videoTimer.start();
    }

    public void handleAgentCommand(GestureCommand command, double c, String h) {
        if (command != GestureCommand.CONFIRM || entering) return;
        if (!loggedIn) { tryLogin(); return; }
        entering = true;
        Platform.runLater(() -> {
            if (starTimer != null) starTimer.stop();
            if (videoTimer != null) videoTimer.stop();
            if (appStateManager != null) {
                appStateManager.markAuthenticated();
                appStateManager.switchState(AppStateManager.STATE_LOBBY);
            }
        });
    }

    private void tryLogin() {
        if (loggedIn) return;
        String u = usernameField.getText().trim();
        String p = passwordField.getText();
        if (u.isEmpty()) { loginStatus.setText("Please enter username"); return; }
        if (VALID_USER.equals(u) && VALID_PASS.equals(p)) {
            loggedIn = true;
            loginStatus.setText("");
            loginBtnText.setText("✓");
            FadeTransition ft = new FadeTransition(Duration.millis(600), loginCard);
            ft.setFromValue(1); ft.setToValue(0);
            ft.setOnFinished(e -> { loginCard.setVisible(false); showStarField(); });
            ft.play();
        } else {
            loginStatus.setText("Invalid username or password");
            passwordField.clear();
        }
    }

    private void showStarField() {
        starCanvas.setVisible(true);
        enterLabel.setVisible(true);
        createStars();
        Platform.runLater(this::startStarAnimation);
    }

    public void updateCameraStream(String s) {}
    public void updateCameraImage(Image i) {}

    // ===== Star animation =====
    private void createStars() {
        SeededRandom r = new SeededRandom(1234L);
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            angle[i] = r.next() * Math.PI * 2.0;
            distance[i] = 30.0 * r.next() + 15.0;
            rotationDirection[i] = r.next() > 0.5 ? 1.0 : -1.0;
            expansionRate[i] = 1.2 + r.next() * 0.8;
            finalScale[i] = 0.7 + r.next() * 0.6;
            dx[i] = distance[i] * Math.cos(angle[i]);
            dy[i] = distance[i] * Math.sin(angle[i]);
            spiralLocation[i] = (1.0 - Math.pow(1.0 - r.next(), 3.0)) / 1.3;
            z[i] = r(-200.0, CAMERA_TRAVEL_DISTANCE + CAMERA_Z, r);
            z[i] = l(z[i], CAMERA_TRAVEL_DISTANCE / 2.0, 0.3 * spiralLocation[i]);
            strokeWeightFactor[i] = Math.pow(r.next(), 2.0);
        }
    }
    private void startStarAnimation() {
        if (starTimer != null) return;
        starStartNanos = System.nanoTime();
        starTimer = new AnimationTimer() {
            public void handle(long now) {
                double e = (now - starStartNanos) / 1_000_000_000.0;
                render((e % LOOP_SECONDS) / LOOP_SECONDS);
            }
        };
        starTimer.start();
    }
    private void render(double t) {
        double w = starCanvas.getWidth(), h = starCanvas.getHeight();
        if (w < 2 || h < 2) return;
        GraphicsContext c = starCanvas.getGraphicsContext2D();
        c.setGlobalAlpha(1.0);
        c.setFill(Color.BLACK); c.fillRect(0, 0, w, h);
        double s = Math.max(w, h);
        double t1 = con(map(t, 0, CHANGE_EVENT_TIME + 0.25, 0, 1), 0, 1);
        double t2 = con(map(t, CHANGE_EVENT_TIME, 1, 0, 1), 0, 1);
        c.save(); c.translate(w / 2, h / 2); c.scale(w / s, h / s);
        c.rotate(Math.toDegrees(-Math.PI * e(t2, 2.7)));
        drawTrail(c, t1, t);
        c.setFill(Color.WHITE);
        for (int i = 0; i < NUMBER_OF_STARS; i++) drawStar(c, i, t1, t2);
        c.restore();
    }
    private void drawTrail(GraphicsContext c, double t1, double t) {
        c.setFill(Color.WHITE);
        for (int i = 0; i < TRAIL_LENGTH; i++) {
            double f = map(i, 0, TRAIL_LENGTH, 1.1, 0.1);
            double sw = (1.3 * (1 - t1) + 3 * Math.sin(Math.PI * t1)) * f;
            double pt = t1 - 0.00015 * i;
            double x = spiralX(pt), y = spiralY(pt);
            double rp = Math.sin(t * Math.PI * 2) * 0.5 + 0.5;
            double[] ro = rot(x, y, x + 5, y + 5, rp, i % 2 == 0);
            c.fillOval(ro[0] - sw / 2, ro[1] - sw / 2, sw, sw);
        }
    }
    private void drawStar(GraphicsContext c, int i, double p, double t2) {
        double q = p - spiralLocation[i]; if (q <= 0) return;
        double dp = con(4 * q, 0, 1);
        double easing = dp < 0.3 ? l(dp, dp * dp, dp / 0.3) : dp < 0.7 ? l(dp * dp, eo(dp), (dp - 0.3) / 0.4) : eo(dp);
        double px = spiralX(spiralLocation[i]), py = spiralY(spiralLocation[i]);
        double x0 = px + dx[i] * easing * finalScale[i], y0 = py + dy[i] * easing * finalScale[i];
        c.setFill(Color.WHITE);
        double hw = (0.5 + dp * 1.2) * strokeWeightFactor[i];
        c.fillOval(x0 - hw, y0 - hw, hw * 2, hw * 2);
    }
    private double spiralX(double t) { return (1 - t) * Math.cos(-3 * Math.PI * t - 0.8) * (20 + t * 45); }
    private double spiralY(double t) { return (1 - t) * Math.sin(-3 * Math.PI * t - 0.8) * (20 + t * 45); }

    private static double l(double a, double b, double t) { return a + (b - a) * t; }
    private static double map(double v, double a, double b, double c, double d) { return c + (d - c) * (v - a) / (b - a); }
    private static double con(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double e(double t, double p) { return Math.pow(t, p); }
    private static double eo(double t) {
        if (t <= 0 || t >= 1) return t;
        return Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3) + 1;
    }
    private static double[] rot(double x, double y, double cx, double cy, double a, boolean cw) {
        double ar = (cw ? a * 2 * Math.PI : -a * 2 * Math.PI);
        double cos = Math.cos(ar), sin = Math.sin(ar);
        return new double[]{cx + (x - cx) * cos - (y - cy) * sin, cy + (x - cx) * sin + (y - cy) * cos};
    }
    private static double r(double lo, double hi, SeededRandom sr) { return lo + sr.next() * (hi - lo); }

    private static class SeededRandom {
        private long seed;
        SeededRandom(long s) { seed = s; }
        double next() { seed = (seed * 1103515245L + 12345L) & 0x7fffffffL; return seed / (double) 0x7fffffffL; }
    }
}
