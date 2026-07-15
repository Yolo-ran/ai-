package com.gesturegame.ui;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.game.CatchFruit;
import com.gesturegame.game.FruitNinja;
import com.gesturegame.game.PopBubbles;
import com.gesturegame.game.RPSGame;
import com.gesturegame.game.RhythmMaster;
import com.gesturegame.game.SideScrollingShooter;
import com.gesturegame.game.TarotGame;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureCommandResolver;
import com.gesturegame.network.GestureStreamServer.DualHandState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 轻量轨道大厅。固定数量的游戏节点代替原来的 3000 粒子与逐帧深度排序，
 * 保留单手切换、握拳确认和大厅摄像头预览；轨道保持固定尺寸。
 */
public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());

    private static final List<Supplier<GameInterface>> GAME_REGISTRY = List.of(
            CatchFruit::new,
            RPSGame::new,
            PopBubbles::new,
            TarotGame::new,
            FruitNinja::new,
            RhythmMaster::new,
            SideScrollingShooter::new);

    private record GameInfo(String name, String desc, String icon, String color,
                            String category, int energy) {}

    private static final List<GameInfo> GAME_INFO = List.of(
            new GameInfo("接水果", "移动手掌控制果篮，接住水果并避开炸弹。", "🍎", "#22d3ee", "ARCADE", 72),
            new GameInfo("猜拳", "三秒倒计时完成出拳，战绩会自动写入 CSV。", "✂", "#ec4899", "CLASSIC", 66),
            new GameInfo("戳泡泡", "用手势光标连续击破泡泡，挑战反应速度。", "◉", "#84cc16", "CASUAL", 58),
            new GameInfo("塔罗牌", "选择牌阵并翻开塔罗牌，获得完整牌面解读。", "◆", "#f59e0b", "MYSTIC", 80),
            new GameInfo("切水果", "挥动手掌切开水果，以目标分数完成挑战。", "◒", "#f97316", "ACTION", 88),
            new GameInfo("节奏大师", "跟随节奏摆出正确手势，积累连击分数。", "♪", "#a78bfa", "RHYTHM", 84),
            new GameInfo("星际突击", "驾驶战机突破航线，AI 动态生成下一关。", "🚀", "#38bdf8", "AI GENERATED", 94));

    private static final int MAX_GAMES = GAME_REGISTRY.size();
    private static final int CONFIRM_HOLD_FRAMES = 72;
    private static final long NAVIGATION_COOLDOWN_MS = 520L;
    private static final long ACTION_COOLDOWN_MS = 500L;
    private static final long DETAIL_VISIBLE_NS = 5_000_000_000L;
    private static final long LOBBY_ENTRY_GUARD_NS = 900_000_000L;
    private static final double IDLE_ROTATION_DEG_PER_SECOND = 5.0;

    @FXML private Canvas lobbyCanvas;
    @FXML private Label statusLabel;
    @FXML private ImageView cameraView;

    private AppStateManager appStateManager;
    private GraphicsContext gc;
    private int currentIndex;
    private int confirmHoldFrames;
    private long lastNavigationTime;
    private long lastActionTime;
    private long lastFrameNanos;
    private long detailVisibleUntilNanos;
    private long inputGuardUntilNanos;
    private boolean detailVisible;
    private boolean confirmArmed;
    private int releaseFrames;
    private double elapsedSeconds;
    private double rotationAngle = -90.0;
    private double targetRotationAngle = -90.0;
    private final double[] nodeX = new double[MAX_GAMES];
    private final double[] nodeY = new double[MAX_GAMES];

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    @FXML
    public void initialize() {
        gc = lobbyCanvas.getGraphicsContext2D();
        if (lobbyCanvas.getParent() instanceof Pane parent) {
            lobbyCanvas.widthProperty().bind(parent.widthProperty());
            lobbyCanvas.heightProperty().bind(parent.heightProperty());
        }
        lobbyCanvas.setFocusTraversable(true);
        lobbyCanvas.setOnMouseClicked(event -> selectNearestNode(event.getX(), event.getY()));
        lobbyCanvas.setOnScroll(event -> navigate(event.getDeltaY() < 0 ? 1 : -1));
        lobbyCanvas.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case LEFT -> navigate(-1);
                case RIGHT -> navigate(1);
                case ENTER, SPACE -> {
                    if (detailVisible) launchGame();
                    else showSelectedDetail(System.nanoTime());
                }
                default -> { }
            }
        });
        updateStatusText();
    }

    public void tick(GestureData gesture) {
        tick(gesture, new DualHandState(false, false, 0.0, false, 0.0, 0.0));
    }

    public void tick(GestureData gesture, DualHandState dualHands) {
        if (gc == null || lobbyCanvas == null) return;

        long now = System.nanoTime();
        boolean enteringLobby = lastFrameNanos == 0 || now - lastFrameNanos > 500_000_000L;
        double dt = enteringLobby ? 1.0 / 60.0
                : Math.min(0.05, Math.max(0.001, (now - lastFrameNanos) / 1_000_000_000.0));
        lastFrameNanos = now;
        if (enteringLobby) resetLobbyPresentation(now);
        elapsedSeconds += dt;

        boolean handDetected = gesture != null && gesture.isHandDetected();
        GestureType gestureType = handDetected ? gesture.getGesture() : GestureType.NONE;
        updateConfirmHold(dualHands.captured() ? GestureType.NONE : gestureType, now);

        if (detailVisible && now > detailVisibleUntilNanos && confirmHoldFrames == 0) {
            detailVisible = false;
        }
        if (detailVisible) {
            rotationAngle = approachAngle(rotationAngle, targetRotationAngle,
                    1.0 - Math.exp(-8.5 * dt));
        } else {
            rotationAngle = normalizeAngle(rotationAngle + IDLE_ROTATION_DEG_PER_SECOND * dt);
        }

        double w = lobbyCanvas.getWidth();
        double h = lobbyCanvas.getHeight();
        if (w <= 1 || h <= 1) return;

        double handX = handDetected ? gesture.getHandX() : 0.5;
        double handY = handDetected ? gesture.getHandY() : 0.5;
        render(w, h, handDetected, handX, handY, dualHands, detailVisible);
    }

    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!AppStateManager.STATE_LOBBY.equals(AppStateManager.getInstance().getCurrentState())) return;
            switch (command) {
                case SWIPE_LEFT -> navigate(1);
                case SWIPE_RIGHT -> navigate(-1);
                case CONFIRM -> handleConfirmCommand();
                case BACK -> {
                    if (appStateManager != null) appStateManager.switchState(AppStateManager.STATE_LOGIN);
                }
                default -> { }
            }
        });
    }

    public void handleAgentCommand(String gesture) {
        handleAgentCommand(GestureCommandResolver.resolve(gesture), 1.0, "STREAM");
    }

    public void updateCameraStream(String base64Image) {
        CameraStreamHelper.push(cameraView, base64Image);
    }

    public void updateCameraImage(Image image) {
        Platform.runLater(() -> {
            if (cameraView != null) cameraView.setImage(image);
        });
    }

    private void render(double w, double h, boolean handDetected, double handX, double handY,
                        DualHandState dualHands, boolean focused) {
        gc.setGlobalAlpha(1.0);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, w, h);

        double cx = w / 2.0;
        double cy = h / 2.0 - 10.0;
        double orbitRadius = clamp(Math.min(w, h) * 0.285, 165, 225);

        drawAtmosphere(w, h, cx, cy);
        drawOrbit(cx, cy, orbitRadius);
        drawCenterOrb(cx, cy);
        calculateNodePositions(cx, cy, orbitRadius);

        for (int i = 0; i < MAX_GAMES; i++) {
            if (!focused || i != currentIndex) drawNode(i, false);
        }
        if (focused) {
            drawSelectedCard(w, h, cx);
            drawNode(currentIndex, true);
        }

        if (handDetected) drawHandCursor(handX * w, handY * h, false);
        if (dualHands.active()) drawHandCursor(dualHands.secondHandX() * w, dualHands.secondHandY() * h, true);
    }

    private void drawAtmosphere(double w, double h, double cx, double cy) {
        RadialGradient haze = new RadialGradient(0, 0, cx, cy,
                Math.max(w, h) * 0.55, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(25, 20, 58, 0.28)),
                new Stop(0.45, Color.rgb(8, 20, 42, 0.13)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(haze);
        gc.fillRect(0, 0, w, h);

        gc.setFill(Color.rgb(255, 255, 255, 0.018));
        for (int i = 0; i < 28; i++) {
            double x = ((i * 193.0) % Math.max(1, w));
            double y = ((i * 97.0 + 31) % Math.max(1, h));
            double pulse = 0.6 + 0.4 * Math.sin(elapsedSeconds * 0.7 + i);
            gc.setGlobalAlpha(0.15 + pulse * 0.22);
            gc.fillOval(x, y, 1.2, 1.2);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawOrbit(double cx, double cy, double radius) {
        gc.setStroke(Color.rgb(255, 255, 255, 0.10));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
        gc.setStroke(Color.rgb(125, 211, 252, 0.025));
        gc.setLineWidth(20);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
        gc.setStroke(Color.rgb(255, 255, 255, 0.16));
        gc.setLineWidth(1.4);
        gc.strokeArc(cx - radius, cy - radius, radius * 2, radius * 2,
                -elapsedSeconds * 10.0, 34, ArcType.OPEN);
    }

    private void drawCenterOrb(double cx, double cy) {
        drawPulseRing(cx, cy, elapsedSeconds % 2.2 / 2.2);
        drawPulseRing(cx, cy, (elapsedSeconds + 1.1) % 2.2 / 2.2);

        RadialGradient halo = new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(96, 165, 250, 0.30)),
                new Stop(0.46, Color.rgb(139, 92, 246, 0.15)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(halo);
        gc.fillOval(cx - 58, cy - 58, 116, 116);

        LinearGradient core = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#a855f7")),
                new Stop(0.52, Color.web("#3b82f6")),
                new Stop(1, Color.web("#14b8a6")));
        gc.setFill(core);
        gc.fillOval(cx - 32, cy - 32, 64, 64);
        gc.setStroke(Color.rgb(255, 255, 255, 0.32));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 32, cy - 32, 64, 64);
        gc.setFill(Color.rgb(255, 255, 255, 0.88));
        gc.fillOval(cx - 16, cy - 16, 32, 32);
        gc.setFill(Color.rgb(255, 255, 255, 0.34));
        gc.fillOval(cx - 10, cy - 11, 13, 9);
    }

    private void drawPulseRing(double cx, double cy, double phase) {
        double radius = 34 + phase * 38;
        gc.setStroke(Color.rgb(255, 255, 255, (1.0 - phase) * 0.18));
        gc.setLineWidth(1.2);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void calculateNodePositions(double cx, double cy, double radius) {
        double step = 360.0 / MAX_GAMES;
        for (int i = 0; i < MAX_GAMES; i++) {
            double rad = Math.toRadians(i * step + rotationAngle);
            nodeX[i] = cx + radius * Math.cos(rad);
            nodeY[i] = cy + radius * Math.sin(rad);
        }
    }

    private void drawNode(int index, boolean selected) {
        GameInfo info = GAME_INFO.get(index);
        double rad = Math.toRadians(index * (360.0 / MAX_GAMES) + rotationAngle);
        double depth = (1.0 + Math.sin(rad)) / 2.0;
        double opacity = selected ? 1.0 : 0.42 + depth * 0.48;
        double scale = selected ? 1.48 : 0.84 + depth * 0.18;
        double radius = 20 * scale;
        Color accent = Color.web(info.color());

        gc.setGlobalAlpha(opacity);
        double glowRadius = radius + 15 + info.energy() * 0.08;
        RadialGradient glow = new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), selected ? 0.26 : 0.12)),
                new Stop(0.4, Color.rgb(255, 255, 255, selected ? 0.10 : 0.035)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(glow);
        gc.fillOval(nodeX[index] - glowRadius, nodeY[index] - glowRadius,
                glowRadius * 2, glowRadius * 2);

        gc.setFill(selected ? Color.WHITE : Color.rgb(3, 7, 18, 0.94));
        gc.fillOval(nodeX[index] - radius, nodeY[index] - radius, radius * 2, radius * 2);
        gc.setStroke(selected ? Color.WHITE : Color.rgb(255, 255, 255, 0.42));
        gc.setLineWidth(selected ? 2.3 : 1.5);
        gc.strokeOval(nodeX[index] - radius, nodeY[index] - radius, radius * 2, radius * 2);

        gc.setFill(selected ? Color.BLACK : Color.WHITE);
        gc.setFont(Font.font("Segoe UI Emoji", FontWeight.NORMAL, selected ? 21 : 17));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(info.icon(), nodeX[index], nodeY[index] + 1);

        gc.setTextBaseline(VPos.BASELINE);
        gc.setFill(selected ? Color.WHITE : Color.rgb(255, 255, 255, 0.72));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, selected ? 15 : 12));
        gc.fillText(info.name(), nodeX[index], nodeY[index] + radius + 24);
        gc.setGlobalAlpha(1.0);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawSelectedCard(double w, double h, double cx) {
        GameInfo info = GAME_INFO.get(currentIndex);
        double cardW = clamp(w * 0.225, 264, 296);
        double cardH = 188;
        double x = clamp(cx - cardW / 2.0, 24, w - cardW - 24);
        double y = clamp(nodeY[currentIndex] + 66, 78, h - cardH - 72);

        gc.setStroke(Color.rgb(255, 255, 255, 0.30));
        gc.setLineWidth(1.0);
        gc.strokeLine(nodeX[currentIndex], nodeY[currentIndex] + 38,
                nodeX[currentIndex], y);

        gc.setFill(Color.rgb(0, 0, 0, 0.42));
        gc.fillRoundRect(x - 7, y + 8, cardW + 14, cardH + 8, 20, 20);
        LinearGradient glass = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(8, 10, 19, 0.96)),
                new Stop(0.58, Color.rgb(2, 6, 15, 0.93)),
                new Stop(1, Color.rgb(14, 13, 28, 0.95)));
        gc.setFill(glass);
        gc.fillRoundRect(x, y, cardW, cardH, 18, 18);
        gc.setStroke(Color.rgb(255, 255, 255, 0.23));
        gc.strokeRoundRect(x, y, cardW, cardH, 18, 18);
        gc.setStroke(Color.rgb(255, 255, 255, 0.18));
        gc.strokeLine(x + 18, y + 1, x + cardW - 18, y + 1);

        drawBadge(x + 16, y + 13, info.category());
        gc.setFill(Color.rgb(255, 255, 255, 0.48));
        gc.setFont(Font.font("Consolas", 9));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(String.format("%02d / %02d", currentIndex + 1, MAX_GAMES), x + cardW - 16, y + 27);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 17));
        gc.fillText(info.name(), x + 16, y + 58);

        gc.setFill(Color.rgb(255, 255, 255, 0.68));
        gc.setFont(Font.font("Microsoft YaHei", 11));
        drawWrappedText(info.desc(), x + 16, y + 82, cardW - 32, 18);

        double energyY = y + 119;
        gc.setFill(Color.rgb(255, 255, 255, 0.65));
        gc.setFont(Font.font("Microsoft YaHei", 9));
        gc.fillText("ENERGY", x + 16, energyY);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(Font.font("Consolas", 9));
        gc.fillText(info.energy() + "%", x + cardW - 16, energyY);
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(Color.rgb(255, 255, 255, 0.10));
        gc.fillRoundRect(x + 16, energyY + 8, cardW - 32, 4, 4, 4);
        Color accent = Color.web(info.color());
        gc.setFill(accent);
        gc.fillRoundRect(x + 16, energyY + 8, (cardW - 32) * info.energy() / 100.0, 4, 4, 4);

        double holdProgress = clamp(confirmHoldFrames / (double) CONFIRM_HOLD_FRAMES, 0, 1);
        gc.setFill(Color.rgb(255, 255, 255, 0.05));
        gc.fillRoundRect(x + 16, y + cardH - 36, cardW - 32, 22, 7, 7);
        if (holdProgress > 0) {
            gc.setFill(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.22));
            gc.fillRoundRect(x + 16, y + cardH - 36, (cardW - 32) * holdProgress, 22, 7, 7);
        }
        gc.setFill(Color.rgb(255, 255, 255, 0.80));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(holdProgress > 0 ? "正在确认  " + Math.round(holdProgress * 100) + "%" : "✊  握拳保持进入游戏",
                x + cardW / 2.0, y + cardH - 21);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawBadge(double x, double y, String text) {
        double badgeW = Math.max(56, text.length() * 6.2 + 16);
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(x, y, badgeW, 19, 5, 5);
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 8));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(text, x + badgeW / 2.0, y + 12.8);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawWrappedText(String text, double x, double y, double maxWidth, double lineHeight) {
        StringBuilder line = new StringBuilder();
        double currentY = y;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String candidate = line.toString() + ch;
            if (!line.isEmpty() && gc.getFont().getSize() * candidate.length() > maxWidth * 1.55) {
                gc.fillText(line.toString(), x, currentY, maxWidth);
                line.setLength(0);
                currentY += lineHeight;
            }
            line.append(ch);
        }
        if (!line.isEmpty()) gc.fillText(line.toString(), x, currentY, maxWidth);
    }

    private void drawHandCursor(double x, double y, boolean secondary) {
        Color color = secondary ? Color.web("#a78bfa") : Color.web("#67e8f9");
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.12));
        gc.fillOval(x - 18, y - 18, 36, 36);
        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.strokeOval(x - 10, y - 10, 20, 20);
        gc.strokeLine(x - 15, y, x + 15, y);
        gc.strokeLine(x, y - 15, x, y + 15);

        if (!secondary && confirmHoldFrames > 0) {
            double progress = clamp(confirmHoldFrames / (double) CONFIRM_HOLD_FRAMES, 0, 1);
            gc.setLineWidth(3.0);
            gc.strokeArc(x - 24, y - 24, 48, 48, 90, -360 * progress, ArcType.OPEN);
        }
    }

    private void navigate(int direction) {
        long now = System.currentTimeMillis();
        if (now - lastNavigationTime < NAVIGATION_COOLDOWN_MS) return;
        currentIndex = Math.floorMod(currentIndex + direction, MAX_GAMES);
        lastNavigationTime = now;
        confirmHoldFrames = 0;
        showSelectedDetail(System.nanoTime());
        updateStatusText();
    }

    private void showSelectedDetail(long now) {
        targetRotationAngle = normalizeAngle(-90.0 - currentIndex * (360.0 / MAX_GAMES));
        detailVisible = true;
        detailVisibleUntilNanos = now + DETAIL_VISIBLE_NS;
    }

    private void resetLobbyPresentation(long now) {
        detailVisible = false;
        confirmHoldFrames = 0;
        confirmArmed = false;
        releaseFrames = 0;
        inputGuardUntilNanos = now + LOBBY_ENTRY_GUARD_NS;
    }

    private void handleConfirmCommand() {
        long now = System.nanoTime();
        if (now < inputGuardUntilNanos || !confirmArmed) return;
        if (detailVisible && now > detailVisibleUntilNanos) detailVisible = false;
        if (!detailVisible) {
            showSelectedDetail(now);
            confirmArmed = false;
            releaseFrames = 0;
            confirmHoldFrames = 0;
            return;
        }
        launchGame();
    }

    private void selectNearestNode(double x, double y) {
        int nearest = -1;
        double nearestDistance = 52 * 52;
        for (int i = 0; i < MAX_GAMES; i++) {
            double dx = x - nodeX[i];
            double dy = y - nodeY[i];
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        if (nearest >= 0) {
            currentIndex = nearest;
            confirmHoldFrames = 0;
            showSelectedDetail(System.nanoTime());
            updateStatusText();
            lobbyCanvas.requestFocus();
        }
    }

    private void updateConfirmHold(GestureType gesture, long now) {
        if (now < inputGuardUntilNanos) {
            confirmHoldFrames = 0;
            return;
        }
        if (gesture != GestureType.FIST) {
            releaseFrames = Math.min(20, releaseFrames + 1);
            if (releaseFrames >= 8) confirmArmed = true;
            confirmHoldFrames = Math.max(0, confirmHoldFrames - 4);
        } else if (confirmArmed && detailVisible) {
            releaseFrames = 0;
            confirmHoldFrames = Math.min(CONFIRM_HOLD_FRAMES, confirmHoldFrames + 1);
            detailVisibleUntilNanos = now + 1_000_000_000L;
        } else {
            releaseFrames = 0;
            confirmHoldFrames = 0;
        }
    }

    private void launchGame() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return;
        GameInterface game = createGame(currentIndex);
        if (game == null || appStateManager == null) {
            LOGGER.warning(() -> "[大厅] 无法启动游戏，索引越界: " + currentIndex);
            return;
        }
        lastActionTime = now;
        confirmHoldFrames = 0;
        detailVisible = false;
        LOGGER.info(() -> "[轨道大厅] 启动游戏: " + game.getName() + " (节点 " + currentIndex + ")");
        appStateManager.setActiveGame(game);
        appStateManager.switchState(AppStateManager.STATE_DIFFICULTY);
    }

    private GameInterface createGame(int index) {
        return index >= 0 && index < GAME_REGISTRY.size() ? GAME_REGISTRY.get(index).get() : null;
    }

    private void updateStatusText() {
        if (statusLabel == null) return;
        statusLabel.setText("张开手掌左右挥动选择   ·   轨道自动巡航   ·   ✊ 握拳查看 / 进入");
    }

    private static double approachAngle(double current, double target, double amount) {
        return normalizeAngle(current + normalizeAngle(target - current) * clamp(amount, 0, 1));
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % 360.0;
        if (normalized > 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
