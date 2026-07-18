package com.gesturegame.ui;

import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
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
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
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
    private static final double START_DOT_Y_OFFSET = 28.0;
    private static final double VIEW_ZOOM = 100.0;
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
    @FXML private PasswordField confirmField;
    @FXML private Label loginStatus;
    @FXML private StackPane loginBtn;
    @FXML private Label loginBtnText;
    @FXML private Label cardTitle;
    @FXML private Label cardSubtitle;
    @FXML private Label toggleLabel;

    private AppStateManager appStateManager;
    private AnimationTimer starTimer;
    private long starStartNanos;
    private AnimationTimer videoTimer;
    private Image[] videoFrames;
    private int videoFrameIdx;
    private boolean entering;
    private boolean loggedIn;
    private boolean active;
    private boolean registerMode;
    private final com.gesturegame.persistence.UserAccountStore accountStore =
            new com.gesturegame.persistence.UserAccountStore();

    // 引导页
    private boolean showGuide;
    private int guidePage;
    private int guideTotalPages = 4;
    private int guideHoldFrames;
    private static final int GUIDE_HOLD_REQUIRED = 120; // 2秒
    private double guideLastHandY = -1;
    private double guideScrollAccum;

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

    @FXML
    private void onToggleMode() {
        registerMode = !registerMode;
        if (registerMode) {
            cardTitle.setText("Create Account");
            cardSubtitle.setText("Sign up to get started");
            loginBtnText.setText("Sign Up");
            toggleLabel.setText("Already have an account? Sign in");
            confirmField.setVisible(true);
            confirmField.setManaged(true);
        } else {
            cardTitle.setText("Welcome Back");
            cardSubtitle.setText("Sign in to continue");
            loginBtnText.setText("Sign In");
            toggleLabel.setText("Don't have an account? Sign up");
            confirmField.setVisible(false);
            confirmField.setManaged(false);
        }
        loginStatus.setText("");
    }

    public void bindStateManager(AppStateManager asm) { this.appStateManager = asm; }

    public void activate() {
        active = true;
        if (videoTimer != null) videoTimer.start();
    }

    public void handleAgentCommand(GestureCommand command, double c, String h) {
        if (entering) return;
        if (!loggedIn) { tryLogin(); return; }
        // 引导页期间：SWIPE用于翻页，CONFIRM用于确认
        if (showGuide) {
            if (command == GestureCommand.SWIPE_LEFT && guidePage < guideTotalPages - 1) {
                guidePage++; guideHoldFrames = 0;
            } else if (command == GestureCommand.SWIPE_RIGHT && guidePage > 0) {
                guidePage--; guideHoldFrames = 0;
            }
            return;
        }
        // 星空 ENTER 界面
        if (command != GestureCommand.CONFIRM) return;
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

    /** 每帧手势数据，用于引导页左右滑动 */
    public void tick(GestureData gesture) {
        if (!showGuide || gesture == null || !gesture.isHandDetected()) return;

        double hx = gesture.getHandX();
        if (guideLastHandY >= 0) {
            double delta = hx - guideLastHandY; // 手右移→下一页
            guideScrollAccum += delta * 100;
            if (guideScrollAccum >= 15 && guidePage < guideTotalPages - 1) {
                guidePage++; guideScrollAccum = 0; guideHoldFrames = 0;
            } else if (guideScrollAccum <= -15 && guidePage > 0) {
                guidePage--; guideScrollAccum = 0; guideHoldFrames = 0;
            }
        }
        guideLastHandY = hx;

        // 最后一页：握拳保持2秒进入
        if (guidePage == guideTotalPages - 1 && gesture.getGesture() == GestureType.FIST) {
            guideHoldFrames++;
            if (guideHoldFrames >= GUIDE_HOLD_REQUIRED) {
                // 进入星空
                showGuide = false;
                if (starTimer != null) starTimer.stop();
                starTimer = null;
                Platform.runLater(this::showStarField);
            }
        } else {
            guideHoldFrames = 0;
        }
    }

    private void tryLogin() {
        if (loggedIn) return;
        String u = usernameField.getText().trim();
        String p = passwordField.getText();
        if (u.isEmpty()) { loginStatus.setText("Please enter username"); return; }
        
        try {
            if (registerMode) {
                // 注册模式 → SQLite 持久化
                String cp = confirmField.getText();
                if (p.isEmpty() || !p.equals(cp)) {
                    loginStatus.setText("Passwords do not match"); return;
                }
                var result = accountStore.register(u, p.toCharArray());
                if (!result.success()) {
                    loginStatus.setText(result.message());
                    return;
                }
                loginStatus.setTextFill(Color.LIME);
                loginStatus.setText("Account created! Please sign in.");
                onToggleMode(); // 切回登录
            } else {
                // 登录模式 → SQLite 验证
                if (accountStore.authenticate(u, p.toCharArray())) {
                    loggedIn = true;
                    loginStatus.setTextFill(Color.LIME);
                    loginBtnText.setText("✓");
                    FadeTransition ft = new FadeTransition(Duration.millis(600), loginCard);
                    ft.setFromValue(1); ft.setToValue(0);
                    ft.setOnFinished(e -> { loginCard.setVisible(false); showGuide(); });
                    ft.play();
                } else {
                    loginStatus.setTextFill(Color.web("#ff6b6b"));
                    loginStatus.setText("Invalid username or password");
                    // 移除 passwordField.clear(); 防止手势误触确认时清空用户输入
                }
            }
        } catch (Exception ex) {
            loginStatus.setTextFill(Color.web("#ff6b6b"));
            loginStatus.setText("Database Error: Please close other game windows.");
            ex.printStackTrace();
        }
    }

    private void showStarField() {
        starCanvas.setVisible(true);
        enterLabel.setVisible(true);
        createStars();
        Platform.runLater(this::startStarAnimation);
    }

    // ===== 引导页 =====
    private void showGuide() {
        showGuide = true;
        guidePage = 0;
        guideHoldFrames = 0;
        guideLastHandY = -1;
        guideScrollAccum = 0;

        // 鼠标滚轮翻页
        rootPane.setOnScroll(e -> {
            guideScrollAccum += e.getDeltaY();
            if (guideScrollAccum <= -40 && guidePage < guideTotalPages - 1) {
                guidePage++; guideScrollAccum = 0; guideHoldFrames = 0;
            } else if (guideScrollAccum >= 40 && guidePage > 0) {
                guidePage--; guideScrollAccum = 0; guideHoldFrames = 0;
            }
        });

        starCanvas.setVisible(true);

        // 引导页渲染循环（视频帧动画继续播放）
        if (starTimer == null) {
            starTimer = new AnimationTimer() {
                @Override public void handle(long now) { renderGuide(); }
            };
        }
        starTimer.start();
    }

    private void renderGuide() {
        double w = starCanvas.getWidth(), h = starCanvas.getHeight();
        GraphicsContext gc = starCanvas.getGraphicsContext2D();
        double cx = w / 2;

        // 透明底，让视频背景透过来；再加半透明遮罩
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.rgb(0, 0, 0, 0.45));
        gc.fillRect(0, 0, w, h);

        // 标题
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei UI", 32));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🎮 欢迎来到手势游戏大厅", cx, 100);

        // 分页内容
        gc.setFont(Font.font("Microsoft YaHei UI", 18));
        switch (guidePage) {
            case 0:
                gc.setFill(Color.rgb(200, 220, 255));
                gc.fillText("这是一个通过摄像头识别手势来玩游戏的应用", cx, 180);
                gc.fillText("你只需要动动手，就能操控一切", cx, 215);
                gc.setFont(Font.font("Microsoft YaHei UI", 22));
                gc.setFill(Color.WHITE);
                gc.fillText("📷 请确保摄像头已开启", cx, 300);
                gc.fillText("🖐 将手放入镜头范围内", cx, 340);
                gc.fillText("💡 保持良好光线效果更佳", cx, 380);
                break;
            case 1:
                gc.setFill(Color.rgb(200, 220, 255));
                gc.fillText("系统支持以下手势操作：", cx, 180);
                gc.setFont(Font.font("Microsoft YaHei UI", 24));
                gc.setFill(Color.WHITE);
                gc.fillText("✊  握拳  —  确认 / 选择", cx, 250);
                gc.fillText("✋  张开  —  返回 / 散开", cx, 310);
                gc.fillText("✌  剪刀手 —  切换 / 确认", cx, 370);
                gc.fillText("👆  食指指向 —  返回上一步", cx, 430);
                gc.fillText("🤲  双手入镜 —  返回大厅", cx, 490);
                break;
            case 2:
                gc.setFill(Color.rgb(200, 220, 255));
                gc.fillText("游戏大厅内有 7 款游戏等你挑战：", cx, 180);
                gc.setFont(Font.font("Microsoft YaHei UI", 20));
                gc.setFill(Color.WHITE);
                gc.fillText("🍎 接水果    ✊ 猜拳    🫧 戳泡泡    🔮 塔罗牌", cx, 250);
                gc.fillText("🔪 切水果    🥁 节奏大师    🚀 星际突击", cx, 295);
                gc.setFont(Font.font("Microsoft YaHei UI", 18));
                gc.setFill(Color.rgb(180, 200, 240));
                gc.fillText("左右移动手浏览游戏，握拳选择", cx, 370);
                gc.fillText("选好后选择难度即可开始", cx, 400);
                break;
            case 3:
                gc.setFill(Color.rgb(200, 220, 255));
                gc.fillText("准备好了吗？", cx, 200);
                gc.setFont(Font.font("Microsoft YaHei UI", 28));
                gc.setFill(Color.GOLD);
                gc.fillText("✊ 握拳保持 2 秒进入游戏大厅", cx, 300);

                // 进度环
                if (guideHoldFrames > 0) {
                    double progress = Math.min(1.0, guideHoldFrames / (double) GUIDE_HOLD_REQUIRED);
                    double pr = 40, py = 400;
                    gc.setStroke(Color.rgb(255, 255, 255, 0.2));
                    gc.setLineWidth(5);
                    gc.strokeOval(cx - pr, py - pr, pr * 2, pr * 2);
                    gc.setStroke(Color.GOLD);
                    gc.setLineWidth(5);
                    gc.strokeArc(cx - pr, py - pr, pr * 2, pr * 2, -90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
                    gc.setFill(Color.WHITE);
                    gc.setFont(Font.font(16));
                    gc.fillText((int)(progress * 100) + "%", cx, py + 6);
                }
                break;
        }

        // 页码指示器
        double dotY = h - 80;
        for (int i = 0; i < guideTotalPages; i++) {
            gc.setFill(i == guidePage ? Color.WHITE : Color.rgb(255, 255, 255, 0.3));
            gc.fillOval(cx - 30 + i * 20, dotY, 10, 10);
        }

        // 底部提示
        gc.setFill(Color.rgb(255, 255, 255, 0.4));
        gc.setFont(Font.font("Microsoft YaHei UI", 14));
        gc.fillText("左右移动手 或 鼠标滚轮 翻页", cx, h - 30);

        gc.setTextAlign(TextAlignment.LEFT);
    }

    public void updateCameraStream(String s) {}
    public void updateCameraImage(Image i) {}

    // ===== Star animation =====
    private void createStars() {
        SeededRandom random = new SeededRandom(1234L);
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            angle[i] = random.next() * Math.PI * 2.0;
            distance[i] = 30.0 * random.next() + 15.0;
            rotationDirection[i] = random.next() > 0.5 ? 1.0 : -1.0;
            expansionRate[i] = 1.2 + random.next() * 0.8;
            finalScale[i] = 0.7 + random.next() * 0.6;

            dx[i] = distance[i] * Math.cos(angle[i]);
            dy[i] = distance[i] * Math.sin(angle[i]);
            spiralLocation[i] = (1.0 - Math.pow(1.0 - random.next(), 3.0)) / 1.3;
            z[i] = random(-200.0, CAMERA_TRAVEL_DISTANCE + CAMERA_Z, random);
            z[i] = lerp(z[i], CAMERA_TRAVEL_DISTANCE / 2.0,
                    0.3 * spiralLocation[i]);
            strokeWeightFactor[i] = Math.pow(random.next(), 2.0);
        }
    }

    private void startStarAnimation() {
        starStartNanos = System.nanoTime();
        render(0.0);
        if (starTimer == null) {
            starTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    double elapsedSeconds =
                            (now - starStartNanos) / 1_000_000_000.0;
                    render((elapsedSeconds % LOOP_SECONDS) / LOOP_SECONDS);
                }
            };
        }
        starTimer.start();
    }

    private void render(double time) {
        double width = starCanvas.getWidth();
        double height = starCanvas.getHeight();
        if (width < 2.0 || height < 2.0) return;

        GraphicsContext ctx = starCanvas.getGraphicsContext2D();
        ctx.setGlobalAlpha(1.0);
        ctx.setFill(Color.BLACK);
        ctx.fillRect(0.0, 0.0, width, height);

        double size = Math.max(width, height);
        double t1 = constrain(map(time, 0.0, CHANGE_EVENT_TIME + 0.25,
                0.0, 1.0), 0.0, 1.0);
        double t2 = constrain(map(time, CHANGE_EVENT_TIME, 1.0,
                0.0, 1.0), 0.0, 1.0);

        ctx.save();
        ctx.translate(width / 2.0, height / 2.0);
        ctx.scale(width / size, height / size);
        ctx.rotate(Math.toDegrees(-Math.PI * ease(t2, 2.7)));
        drawTrail(ctx, t1, time);
        ctx.setFill(Color.WHITE);
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            drawStar(ctx, i, t1, t2);
        }
        drawStartDot(ctx, time, t2);
        ctx.restore();
    }

    private void drawTrail(GraphicsContext ctx, double t1, double time) {
        ctx.setFill(Color.WHITE);
        for (int i = 0; i < TRAIL_LENGTH; i++) {
            double factor = map(i, 0.0, TRAIL_LENGTH, 1.1, 0.1);
            double strokeWidth =
                    (1.3 * (1.0 - t1) + 3.0 * Math.sin(Math.PI * t1)) * factor;
            double pathTime = t1 - 0.00015 * i;
            double x = spiralX(pathTime);
            double y = spiralY(pathTime);
            double rotateProgress = Math.sin(time * Math.PI * 2.0) * 0.5 + 0.5;
            double[] rotated = rotate(x, y, x + 5.0, y + 5.0,
                    rotateProgress, i % 2 == 0);
            ctx.fillOval(rotated[0] - strokeWidth / 2.0,
                    rotated[1] - strokeWidth / 2.0, strokeWidth, strokeWidth);
        }
    }

    private void drawStar(GraphicsContext ctx, int i, double progress, double t2) {
        double q = progress - spiralLocation[i];
        if (q <= 0.0) return;

        double displacementProgress = constrain(4.0 * q, 0.0, 1.0);
        double linearEasing = displacementProgress;
        double elasticEasing = easeOutElastic(displacementProgress);
        double powerEasing = Math.pow(displacementProgress, 2.0);
        double easing;
        if (displacementProgress < 0.3) {
            easing = lerp(linearEasing, powerEasing,
                    displacementProgress / 0.3);
        } else if (displacementProgress < 0.7) {
            easing = lerp(powerEasing, elasticEasing,
                    (displacementProgress - 0.3) / 0.4);
        } else {
            easing = elasticEasing;
        }

        double pathX = spiralX(spiralLocation[i]);
        double pathY = spiralY(spiralLocation[i]);
        double screenX;
        double screenY;
        if (displacementProgress < 0.3) {
            screenX = lerp(pathX, pathX + dx[i] * 0.3, easing / 0.3);
            screenY = lerp(pathY, pathY + dy[i] * 0.3, easing / 0.3);
        } else if (displacementProgress < 0.7) {
            double midProgress = (displacementProgress - 0.3) / 0.4;
            double curveStrength =
                    Math.sin(midProgress * Math.PI) * rotationDirection[i] * 1.5;
            double baseX = pathX + dx[i] * 0.3;
            double baseY = pathY + dy[i] * 0.3;
            double targetX = pathX + dx[i] * 0.7;
            double targetY = pathY + dy[i] * 0.7;
            double perpendicularX = -dy[i] * 0.4 * curveStrength;
            double perpendicularY = dx[i] * 0.4 * curveStrength;
            screenX = lerp(baseX, targetX, midProgress)
                    + perpendicularX * midProgress;
            screenY = lerp(baseY, targetY, midProgress)
                    + perpendicularY * midProgress;
        } else {
            double endProgress = (displacementProgress - 0.7) / 0.3;
            double baseX = pathX + dx[i] * 0.7;
            double baseY = pathY + dy[i] * 0.7;
            double targetDistance = distance[i] * expansionRate[i] * 1.5;
            double spiralAngle = angle[i]
                    + 1.2 * rotationDirection[i] * endProgress * Math.PI;
            double targetX = pathX + targetDistance * Math.cos(spiralAngle);
            double targetY = pathY + targetDistance * Math.sin(spiralAngle);
            screenX = lerp(baseX, targetX, endProgress);
            screenY = lerp(baseY, targetY, endProgress);
        }

        double vx = (z[i] - CAMERA_Z) * screenX / VIEW_ZOOM;
        double vy = (z[i] - CAMERA_Z) * screenY / VIEW_ZOOM;
        double sizeMultiplier;
        if (displacementProgress < 0.6) {
            sizeMultiplier = 1.0 + displacementProgress * 0.2;
        } else {
            double scaleProgress = (displacementProgress - 0.6) / 0.4;
            sizeMultiplier = 1.2 * (1.0 - scaleProgress)
                    + finalScale[i] * scaleProgress;
        }
        double dotSize = 8.5 * strokeWeightFactor[i] * sizeMultiplier;
        showProjectedDot(ctx, vx, vy, z[i], dotSize, t2);
    }

    private void drawStartDot(GraphicsContext ctx, double time, double t2) {
        if (time <= CHANGE_EVENT_TIME) return;
        double y = CAMERA_Z * START_DOT_Y_OFFSET / VIEW_ZOOM;
        showProjectedDot(ctx, 0.0, y, CAMERA_TRAVEL_DISTANCE, 2.5, t2);
    }

    private void showProjectedDot(GraphicsContext ctx, double x, double y,
                                  double pointZ, double sizeFactor, double t2) {
        double newCameraZ = CAMERA_Z
                + ease(Math.pow(t2, 1.2), 1.8) * CAMERA_TRAVEL_DISTANCE;
        if (pointZ <= newCameraZ) return;

        double depth = pointZ - newCameraZ;
        double projectedX = VIEW_ZOOM * x / depth;
        double projectedY = VIEW_ZOOM * y / depth;
        ctx.setLineWidth(400.0 * sizeFactor / depth);
        ctx.fillOval(projectedX - 0.5, projectedY - 0.5, 1.0, 1.0);
    }

    private static double[] rotate(double x1, double y1, double x2, double y2,
                                   double progress, boolean orientation) {
        double middleX = (x1 + x2) / 2.0;
        double middleY = (y1 + y2) / 2.0;
        double deltaX = x1 - middleX;
        double deltaY = y1 - middleY;
        double angle = Math.atan2(deltaY, deltaX);
        double direction = orientation ? -1.0 : 1.0;
        double radius = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        double bounce = Math.sin(progress * Math.PI) * 0.05 * (1.0 - progress);
        double rotation = direction * Math.PI * easeOutElastic(progress);
        return new double[]{
                middleX + radius * (1.0 + bounce) * Math.cos(angle + rotation),
                middleY + radius * (1.0 + bounce) * Math.sin(angle + rotation)
        };
    }

    private static double spiralX(double value) {
        double p = ease(constrain(1.2 * value, 0.0, 1.0), 1.8);
        double theta = 2.0 * Math.PI * 6.0 * Math.sqrt(p);
        return 170.0 * Math.sqrt(p) * Math.cos(theta);
    }

    private static double spiralY(double value) {
        double p = ease(constrain(1.2 * value, 0.0, 1.0), 1.8);
        double theta = 2.0 * Math.PI * 6.0 * Math.sqrt(p);
        return 170.0 * Math.sqrt(p) * Math.sin(theta) + START_DOT_Y_OFFSET;
    }

    private static double ease(double p, double power) {
        if (p < 0.5) return 0.5 * Math.pow(2.0 * p, power);
        return 1.0 - 0.5 * Math.pow(2.0 * (1.0 - p), power);
    }

    private static double easeOutElastic(double value) {
        if (value <= 0.0 || value >= 1.0) return value;
        double c4 = 2.0 * Math.PI / 4.5;
        return Math.pow(2.0, -8.0 * value)
                * Math.sin((value * 8.0 - 0.75) * c4) + 1.0;
    }

    private static double map(double value, double start1, double stop1,
                              double start2, double stop2) {
        return start2 + (stop2 - start2)
                * ((value - start1) / (stop1 - start1));
    }

    private static double constrain(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private static double lerp(double start, double end, double progress) {
        return start * (1.0 - progress) + end * progress;
    }

    private static double random(double min, double max, SeededRandom random) {
        return min + random.next() * (max - min);
    }

    private static final class SeededRandom {
        private long seed;

        private SeededRandom(long seed) {
            this.seed = seed;
        }

        private double next() {
            seed = (seed * 9301L + 49297L) % 233280L;
            return (double) seed / 233280.0;
        }
    }
}
