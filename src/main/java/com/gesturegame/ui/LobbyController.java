package com.gesturegame.ui;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.game.CatchFruit;
import com.gesturegame.game.FruitNinja;
import com.gesturegame.game.PopBubbles;
import com.gesturegame.game.RPSGame;
import com.gesturegame.game.RhythmMaster;
import com.gesturegame.game.TarotGame;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureCommandResolver;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 星河大厅控制器：以 Canvas 渲染粒子宇宙背景 + 6 颗游戏星球。
 *
 * <p>混合方案 1.5：
 * <ul>
 *   <li>星尘背景受手光标弹簧吸引（聚散），无手时回归各自的“家”位置</li>
 *   <li>5 颗非焦点星球绕中心核心在椭圆轨道上缓慢公转；1 颗焦点星球
 *       缓动脱离到轨道正前方（前台舞台）放大，并在底部状态栏显示其信息</li>
 *   <li>中心为发光脉冲核心（大厅身份焦点，非游戏星球）</li>
 *   <li>选择按索引（SWIPE 切换、FIST 按住确认），不按位置瞄准</li>
 * </ul>
 *
 * <p>渲染由 {@link com.gesturegame.MainApp} 的 AnimationTimer 在 LOBBY 态每帧调用
 * {@link #tick(GestureData)} 驱动，与 {@link GameRenderer#tick} 架构对称。
 */
public class LobbyController {

    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());

    private static final List<Supplier<GameInterface>> GAME_REGISTRY = List.of(
            CatchFruit::new,
            RPSGame::new,
            PopBubbles::new,
            TarotGame::new,
            FruitNinja::new,
            RhythmMaster::new);
    private static final int MAX_GAMES = GAME_REGISTRY.size();

    /** 游戏展示元数据（名/简介/图标/双色），与 GAME_REGISTRY 顺序一致；硬编码以避免为取元数据而实例化游戏。 */
    private record GameInfo(String name, String desc, String icon, String color1, String color2) {}
    private static final List<GameInfo> GAME_INFO = List.of(
            new GameInfo("接水果", "手势控篮子接水果躲炸弹", "🍎", "#1d4ed8", "#22d3ee"),
            new GameInfo("猜拳", "三秒倒计时出拳对决", "✂️", "#7c3aed", "#ec4899"),
            new GameInfo("戳泡泡", "手势瞄准戳破泡泡连击", "🫧", "#0f766e", "#84cc16"),
            new GameInfo("塔罗牌", "选牌翻牌探索命运", "🔮", "#4c1d95", "#f59e0b"),
            new GameInfo("切水果", "手滑动切水果躲炸弹", "🔪", "#dc2626", "#f97316"),
            new GameInfo("节奏大师", "按时摆出正确手势", "🥁", "#1e40af", "#7c3aed"));

    private static final int STAR_COUNT = 420;
    private static final int PLANET_PARTICLES = 80;
    private static final long NAVIGATION_COOLDOWN_MS = 180L;
    private static final long ACTION_COOLDOWN_MS = 500L;
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final String DATA_URI_SEPARATOR = ",";

    @FXML
    private Canvas lobbyCanvas;

    @FXML
    private ImageView cameraView;

    @FXML
    private Label statusLabel;

    private AppStateManager appStateManager;
    private GraphicsContext gc;
    private Star[] stars;
    private Planet[] planets;
    private int currentIndex;
    private long lastNavigationTime;
    private long lastActionTime;
    private double drift;
    private double pulse;

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    @FXML
    public void initialize() {
        gc = lobbyCanvas.getGraphicsContext2D();
        if (lobbyCanvas.getParent() instanceof Pane) {
            Pane parent = (Pane) lobbyCanvas.getParent();
            lobbyCanvas.widthProperty().bind(parent.widthProperty());
            lobbyCanvas.heightProperty().bind(parent.heightProperty());
        }
        stars = createStars();
        planets = createPlanets();
        updateStatusText();
    }

    /**
     * 大厅渲染循环，每帧由 MainApp 的 AnimationTimer 在 LOBBY 态调用。
     */
    public void tick(GestureData gesture) {
        if (gc == null || lobbyCanvas == null) {
            return;
        }
        double w = lobbyCanvas.getWidth();
        double h = lobbyCanvas.getHeight();
        if (w < 1 || h < 1) {
            return;
        }

        boolean hand = gesture != null && gesture.isHandDetected();
        double handX = hand ? gesture.getHandX() : 0.5;
        double handY = hand ? gesture.getHandY() : 0.5;

        drawBackground(w, h);
        drawStars(w, h, hand, handX, handY);

        double cx = w / 2.0;
        double cy = h / 2.0;
        double rx = w * 0.30;
        double ry = h * 0.17;

        drift += 0.0015;
        pulse += 0.05;

        int n = planets.length;
        double[] px = new double[n];
        double[] py = new double[n];
        double[] pr = new double[n];
        double[] pdepth = new double[n];
        boolean[] pfoc = new boolean[n];
        Integer[] order = new Integer[n];
        double baseR = Math.min(w, h) * 0.052;
        for (int i = 0; i < n; i++) {
            Planet p = planets[i];
            boolean focused = (i == currentIndex);
            double targetAngle = focused ? Math.PI / 2 : p.slotAngle + drift;
            p.displayAngle = angleLerp(p.displayAngle, targetAngle, 0.12);
            double depth = Math.sin(p.displayAngle);
            double targetScale = focused ? 1.4 : (0.55 + 0.40 * (depth + 1) / 2);
            p.scale += (targetScale - p.scale) * 0.12;
            p.spin += p.spinSpeed;
            px[i] = cx + rx * Math.cos(p.displayAngle);
            py[i] = cy + ry * Math.sin(p.displayAngle);
            pr[i] = baseR * p.scale;
            pdepth[i] = depth;
            pfoc[i] = focused;
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(pdepth[a], pdepth[b]));

        for (int idx : order) {
            if (pdepth[idx] < 0) {
                drawPlanet(px[idx], py[idx], pr[idx], pfoc[idx], planets[idx]);
            }
        }
        drawCore(cx, cy, Math.min(w, h));
        for (int idx : order) {
            if (pdepth[idx] >= 0) {
                drawPlanet(px[idx], py[idx], pr[idx], pfoc[idx], planets[idx]);
            }
        }

        if (hand) {
            drawHandCursor(handX * w, handY * h);
        }
    }

    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            if (!AppStateManager.STATE_LOBBY.equals(AppStateManager.getInstance().getCurrentState())) {
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
                    // 已禁用：验证成功后不再退回登录页
                    break;
                default:
                    break;
            }
            LOGGER.info(() -> "大厅接收指令: " + command + ", confidence=" + confidence + ", hand=" + hand);
        });
    }

    /** 兼容字符串手势入口，便于直接对接视觉端原始指令。 */
    public void handleAgentCommand(String gesture) {
        handleAgentCommand(GestureCommandResolver.resolve(gesture), 1.0, "STREAM");
    }

    /** 线程安全地刷新大厅右上角摄像头预览画面。 */
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

    /** 使用本地摄像头图像直接刷新预览区域。 */
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
        lastNavigationTime = now;
        updateStatusText();
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
        LOGGER.info(() -> "[大厅] 启动游戏: " + game.getName() + " (星球索引 " + currentIndex + ")");
        appStateManager.setActiveGame(game);
        appStateManager.switchState(AppStateManager.STATE_GAME);
    }

    private GameInterface createGame(int index) {
        if (index < 0 || index >= GAME_REGISTRY.size()) {
            return null;
        }
        return GAME_REGISTRY.get(index).get();
    }

    private void updateStatusText() {
        if (statusLabel == null || currentIndex < 0 || currentIndex >= GAME_INFO.size()) {
            return;
        }
        GameInfo gi = GAME_INFO.get(currentIndex);
        statusLabel.setText(gi.icon() + "  " + gi.name() + " — " + gi.desc()
                + "    ·    左右挥手切换 · 握拳确认进入");
    }

    // ===== 渲染细节 =====

    private void drawBackground(double w, double h) {
        gc.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0f172a")),
                new Stop(0.5, Color.web("#0b1220")),
                new Stop(1, Color.web("#06111e"))));
        gc.fillRect(0, 0, w, h);
    }

    private void drawStars(double w, double h, boolean hand, double handX, double handY) {
        for (Star s : stars) {
            double tx = hand ? handX + s.gx : s.hx;
            double ty = hand ? handY + s.gy : s.hy;
            double k = 0.03 + 0.05 * s.z;
            s.x += (tx - s.x) * k;
            s.y += (ty - s.y) * k;
            s.twinkle += s.twinkleSpeed;
            double px = s.x * w;
            double py = s.y * h;
            double a = (0.5 + 0.5 * Math.sin(s.twinkle)) * (0.35 + 0.65 * s.z);
            double sz = s.size * (0.5 + 0.5 * s.z);
            gc.setFill(Color.color(0.82, 0.86, 1.0, a));
            gc.fillOval(px - sz / 2, py - sz / 2, sz, sz);
        }
    }

    private void drawPlanet(double px, double py, double r, boolean focused, Planet p) {
        double alpha = focused ? 1.0 : 0.85;
        Color halo = p.c2;
        for (int k = 0; k < 3; k++) {
            double hr = r * (1.6 + k * 0.7);
            double ha = (0.18 - k * 0.06) * alpha;
            if (ha > 0) {
                gc.setFill(Color.color(halo.getRed(), halo.getGreen(), halo.getBlue(), ha));
                gc.fillOval(px - hr, py - hr, hr * 2, hr * 2);
            }
        }
        // 背面粒子
        drawPlanetParticles(px, py, r, p, false, alpha);
        // 正面粒子
        drawPlanetParticles(px, py, r, p, true, alpha);
    }

    private void drawPlanetParticles(double px, double py, double r, Planet p, boolean front, double alpha) {
        double cosS = Math.cos(p.spin);
        double sinS = Math.sin(p.spin);
        for (int i = 0; i < p.offsets.length; i++) {
            double ox = p.offsets[i][0];
            double oy = p.offsets[i][1];
            double oz = p.offsets[i][2];
            double rxp = ox * cosS + oz * sinS;
            double rzp = -ox * sinS + oz * cosS;
            if ((front && rzp < 0) || (!front && rzp >= 0)) {
                continue;
            }
            double ryp = oy;
            double sx = px + rxp * r;
            double sy = py + ryp * r * 0.92;
            double bright = 0.35 + 0.65 * (rzp + 1) / 2;
            double psize = r * 0.16 * (0.6 + 0.4 * (rzp + 1) / 2);
            Color c = lerpColor(p.c1, p.c2, p.ptc[i]);
            gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha * bright));
            gc.fillOval(sx - psize / 2, sy - psize / 2, psize, psize);
        }
    }

    private void drawCore(double cx, double cy, double minWH) {
        double coreR = minWH * 0.055 * (1 + 0.06 * Math.sin(pulse));
        Color c = Color.web("#fde68a");
        for (int k = 0; k < 4; k++) {
            double hr = coreR * (2.0 + k * 1.2);
            double ha = 0.12 - k * 0.025;
            if (ha > 0) {
                gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), ha));
                gc.fillOval(cx - hr, cy - hr, hr * 2, hr * 2);
            }
        }
        gc.setFill(Color.color(1.0, 0.96, 0.80, 0.95));
        gc.fillOval(cx - coreR, cy - coreR, coreR * 2, coreR * 2);
    }

    private void drawHandCursor(double x, double y) {
        gc.setStroke(Color.web("#deff9a"));
        gc.setLineWidth(2.0);
        gc.strokeOval(x - 16, y - 16, 32, 32);
        gc.setFill(Color.color(0.87, 1.0, 0.60, 0.9));
        gc.fillOval(x - 3, y - 3, 6, 6);
    }

    private static Color lerpColor(Color a, Color b, double t) {
        return Color.color(
                a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    }

    private static double angleLerp(double current, double target, double t) {
        double diff = normalizeAngle(target - current);
        return current + diff * t;
    }

    private static double normalizeAngle(double a) {
        while (a > Math.PI) {
            a -= 2 * Math.PI;
        }
        while (a < -Math.PI) {
            a += 2 * Math.PI;
        }
        return a;
    }

    private Star[] createStars() {
        Star[] ss = new Star[STAR_COUNT];
        for (int i = 0; i < ss.length; i++) {
            Star s = new Star();
            s.hx = Math.random();
            s.hy = Math.random();
            s.x = s.hx;
            s.y = s.hy;
            s.gx = (Math.random() - 0.5) * 0.16;
            s.gy = (Math.random() - 0.5) * 0.16;
            s.z = Math.random();
            s.size = 0.8 + Math.random() * 1.8;
            s.twinkle = Math.random() * Math.PI * 2;
            s.twinkleSpeed = 0.02 + Math.random() * 0.05;
            ss[i] = s;
        }
        return ss;
    }

    private Planet[] createPlanets() {
        double[][] sphere = fibSphere(PLANET_PARTICLES);
        Planet[] ps = new Planet[GAME_INFO.size()];
        for (int i = 0; i < ps.length; i++) {
            Planet p = new Planet();
            p.index = i;
            p.slotAngle = i * (2 * Math.PI / ps.length);
            p.displayAngle = p.slotAngle;
            p.scale = 0.8;
            p.spin = Math.random() * Math.PI * 2;
            p.spinSpeed = 0.008 + Math.random() * 0.02;
            p.offsets = sphere;
            p.ptc = new double[sphere.length];
            for (int j = 0; j < p.ptc.length; j++) {
                p.ptc[j] = Math.random();
            }
            GameInfo gi = GAME_INFO.get(i);
            p.c1 = Color.web(gi.color1());
            p.c2 = Color.web(gi.color2());
            ps[i] = p;
        }
        return ps;
    }

    private static double[][] fibSphere(int n) {
        double[][] pts = new double[n][3];
        double phi = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i / (double) (n - 1)) * 2;
            double radius = Math.sqrt(Math.max(0, 1 - y * y));
            double theta = phi * i;
            pts[i][0] = Math.cos(theta) * radius;
            pts[i][1] = y;
            pts[i][2] = Math.sin(theta) * radius;
        }
        return pts;
    }

    /** 星尘粒子：归一化坐标 + 弹簧聚散。 */
    private static final class Star {
        double hx, hy;
        double x, y;
        double gx, gy;
        double z;
        double size;
        double twinkle;
        double twinkleSpeed;
    }

    /** 游戏星球：纯粒子团，绕中心轨道公转 + 本地自旋。 */
    private static final class Planet {
        int index;
        double slotAngle;
        double displayAngle;
        double scale;
        double spin;
        double spinSpeed;
        double[][] offsets;
        double[] ptc;
        Color c1;
        Color c2;
    }
}
