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
import javafx.scene.paint.RadialGradient;
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
 * <p>混合方案 1.5（精修版）：
 * <ul>
 *   <li>星尘（~600）动量弹簧聚散：手速感知（手快炸散、手慢聚拢成环）+ 切向涡流 + 多色相 + 近星辉光</li>
 *   <li>5 颗非焦点星球绕中心核心椭圆公转；1 颗焦点星球缓动脱离到正前方舞台放大</li>
 *   <li>星球=实体径向渐变球体 + 厚粒子云（~220，方向光照 + 表面流动）+ 轨道尘环 + 径向光晕</li>
 *   <li>中心为径向渐变发光脉冲核心 + 旋转光冕</li>
 *   <li>背景叠加柔光星云增加纵深</li>
 *   <li>选择按索引（SWIPE 切换、FIST 按住确认），不按位置瞄准</li>
 * </ul>
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

    private record GameInfo(String name, String desc, String icon, String color1, String color2) {}
    private static final List<GameInfo> GAME_INFO = List.of(
            new GameInfo("接水果", "手势控篮子接水果躲炸弹", "🍎", "#1d4ed8", "#22d3ee"),
            new GameInfo("猜拳", "三秒倒计时出拳对决", "✂️", "#7c3aed", "#ec4899"),
            new GameInfo("戳泡泡", "手势瞄准戳破泡泡连击", "🫧", "#0f766e", "#84cc16"),
            new GameInfo("塔罗牌", "选牌翻牌探索命运", "🔮", "#4c1d95", "#f59e0b"),
            new GameInfo("切水果", "手滑动切水果躲炸弹", "🔪", "#dc2626", "#f97316"),
            new GameInfo("节奏大师", "按时摆出正确手势", "🥁", "#1e40af", "#7c3aed"));

    private static final int STAR_COUNT = 600;
    private static final int PLANET_PARTICLES = 220;
    private static final int DUST_RING_PARTICLES = 36;
    private static final double STAR_STIFFNESS = 0.045;
    private static final double STAR_DAMPING = 0.86;
    private static final long NAVIGATION_COOLDOWN_MS = 180L;
    private static final long ACTION_COOLDOWN_MS = 500L;
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final String DATA_URI_SEPARATOR = ",";

    private static final Color[] STAR_TINTS = {
            Color.color(0.85, 0.88, 1.0),
            Color.color(1.0, 0.98, 0.92),
            Color.color(1.0, 0.92, 0.74),
            Color.color(1.0, 0.85, 0.86),
            Color.color(0.80, 0.95, 1.0)};

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
    private Nebula[] nebulas;
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
        nebulas = createNebulas();
        updateStatusText();
    }

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
        double handSpeed = gesture != null
                ? Math.hypot(gesture.getVelocityX(), gesture.getVelocityY()) : 0.0;

        drawBackground(w, h);
        drawStars(w, h, hand, handX, handY, handSpeed);

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
            p.flow += p.flowSpeed;
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
                drawPlanet(px[idx], py[idx], pr[idx], pfoc[idx], planets[idx], cx, cy);
            }
        }
        drawCore(cx, cy, Math.min(w, h));
        for (int idx : order) {
            if (pdepth[idx] >= 0) {
                drawPlanet(px[idx], py[idx], pr[idx], pfoc[idx], planets[idx], cx, cy);
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

    public void handleAgentCommand(String gesture) {
        handleAgentCommand(GestureCommandResolver.resolve(gesture), 1.0, "STREAM");
    }

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

    // ===== 渲染 =====

    private void drawBackground(double w, double h) {
        gc.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#070b18")),
                new Stop(0.5, Color.web("#0a0f1f")),
                new Stop(1, Color.web("#050912"))));
        gc.fillRect(0, 0, w, h);

        double minWH = Math.min(w, h);
        for (Nebula neb : nebulas) {
            neb.phase += neb.phaseSpeed;
            double ncx = (neb.nx + 0.03 * Math.sin(neb.phase)) * w;
            double ncy = (neb.ny + 0.03 * Math.cos(neb.phase * 0.8)) * h;
            double nr = neb.r * minWH;
            gc.setFill(new RadialGradient(0, 0, ncx, ncy, nr, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(neb.color.getRed(), neb.color.getGreen(), neb.color.getBlue(), 0.16)),
                    new Stop(1, Color.color(neb.color.getRed(), neb.color.getGreen(), neb.color.getBlue(), 0.0))));
            gc.fillOval(ncx - nr, ncy - nr, nr * 2, nr * 2);
        }
    }

    private void drawStars(double w, double h, boolean hand, double handX, double handY, double handSpeed) {
        double gather = hand ? clamp(1.0 - handSpeed * 7.0, 0.0, 1.0) : 0.0;
        double ringR = lerp(0.20, 0.045, gather);
        for (Star s : stars) {
            double tx;
            double ty;
            if (hand) {
                tx = handX + Math.cos(s.gatherAngle) * ringR;
                ty = handY + Math.sin(s.gatherAngle) * ringR;
            } else {
                s.driftA += 0.01;
                tx = s.hx + Math.cos(s.driftA) * s.driftR;
                ty = s.hy + Math.sin(s.driftA) * s.driftR;
            }
            double ax = (tx - s.x) * STAR_STIFFNESS;
            double ay = (ty - s.y) * STAR_STIFFNESS;
            s.vx = (s.vx + ax) * STAR_DAMPING;
            s.vy = (s.vy + ay) * STAR_DAMPING;
            if (hand && gather > 0.3) {
                // 切向涡流：绕手旋转而非塌成一点
                s.vx += -(s.y - handY) * 0.0025 * gather;
                s.vy += (s.x - handX) * 0.0025 * gather;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.twinkle += s.twinkleSpeed;

            double px = s.x * w;
            double py = s.y * h;
            double a = (0.45 + 0.55 * (0.5 + 0.5 * Math.sin(s.twinkle))) * (0.3 + 0.7 * s.z);
            double sz = s.size * (0.5 + 0.5 * s.z);
            if (s.z > 0.72) {
                gc.setFill(Color.color(s.tint.getRed(), s.tint.getGreen(), s.tint.getBlue(), a * 0.22));
                gc.fillOval(px - sz * 1.8, py - sz * 1.8, sz * 3.6, sz * 3.6);
            }
            gc.setFill(Color.color(s.tint.getRed(), s.tint.getGreen(), s.tint.getBlue(), a));
            gc.fillOval(px - sz / 2, py - sz / 2, sz, sz);
        }
    }

    private void drawPlanet(double px, double py, double r, boolean focused, Planet p, double cx, double cy) {
        double alpha = focused ? 1.0 : 0.9;
        double lx = cx - px;
        double ly = cy - py;
        double ll = Math.max(1e-6, Math.hypot(lx, ly));
        lx /= ll;
        ly /= ll;

        // 外层径向光晕（平滑，替掉同心圆）
        double glowR = r * (focused ? 3.3 : 2.4);
        gc.setFill(new RadialGradient(0, 0, px, py, glowR, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(p.c2.getRed(), p.c2.getGreen(), p.c2.getBlue(), 0.30 * alpha)),
                new Stop(0.5, Color.color(p.c1.getRed(), p.c1.getGreen(), p.c1.getBlue(), 0.10 * alpha)),
                new Stop(1, Color.color(p.c1.getRed(), p.c1.getGreen(), p.c1.getBlue(), 0.0))));
        gc.fillOval(px - glowR, py - glowR, glowR * 2, glowR * 2);

        // 尘环背面
        drawDustRing(px, py, r, p, alpha, false);

        // 实体球体（径向渐变，焦点偏向核心方向 → 立体明暗）
        double bodyR = r * 0.94;
        double bgx = px + lx * bodyR * 0.35;
        double bgy = py + ly * bodyR * 0.35;
        Color dark = p.c1.darker();
        gc.setFill(new RadialGradient(0, 0, bgx, bgy, bodyR * 1.25, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(p.cMid.getRed(), p.cMid.getGreen(), p.cMid.getBlue(), 0.96 * alpha)),
                new Stop(0.6, Color.color(p.c1.getRed(), p.c1.getGreen(), p.c1.getBlue(), 0.86 * alpha)),
                new Stop(1, Color.color(dark.getRed(), dark.getGreen(), dark.getBlue(), 0.74 * alpha))));
        gc.fillOval(px - bodyR, py - bodyR, bodyR * 2, bodyR * 2);

        // 表面粒子云（方向光照 + 纬度流动）
        drawPlanetParticles(px, py, r, p, false, alpha, lx, ly);
        drawPlanetParticles(px, py, r, p, true, alpha, lx, ly);

        // 尘环正面
        drawDustRing(px, py, r, p, alpha, true);

        // 焦点：选择环
        if (focused) {
            double ringR = r * 1.5 + 3 * Math.sin(pulse * 1.5);
            gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.55));
            gc.setLineWidth(2.0);
            gc.strokeOval(px - ringR, py - ringR, ringR * 2, ringR * 2);
        }
    }

    private void drawPlanetParticles(double px, double py, double r, Planet p,
                                     boolean front, double alpha, double lx, double ly) {
        double cosS = Math.cos(p.spin);
        double sinS = Math.sin(p.spin);
        double cosF = Math.cos(p.flow);
        double sinF = Math.sin(p.flow);
        for (int i = 0; i < p.offsets.length; i++) {
            double ox = p.offsets[i][0];
            double oy = p.offsets[i][1];
            double oz = p.offsets[i][2];
            double rx1 = ox * cosS + oz * sinS;
            double rz1 = -ox * sinS + oz * cosS;
            double ry1 = oy;
            double ry2 = ry1 * cosF - rz1 * sinF;
            double rz2 = ry1 * sinF + rz1 * cosF;
            if ((front && rz2 < 0) || (!front && rz2 >= 0)) {
                continue;
            }
            double sx = px + rx1 * r;
            double sy = py + ry2 * r * 0.94;
            double lit = clamp(0.2 + 0.8 * (rx1 * lx + ry2 * ly + rz2 * 0.5), 0.15, 1.0);
            double psize = r * 0.12 * (0.6 + 0.5 * (rz2 + 1) / 2);
            Color c = lerpColor3(p.c1, p.cMid, p.c2, p.ptc[i]);
            gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha * lit));
            gc.fillOval(sx - psize / 2, sy - psize / 2, psize, psize);
        }
    }

    private void drawDustRing(double px, double py, double r, Planet p, double alpha, boolean front) {
        double ringR = r * 1.75;
        double tilt = 0.34;
        for (int i = 0; i < p.dust.length; i++) {
            double ang = p.dust[i][0] + p.flow * 0.6;
            double s = Math.sin(ang);
            if ((front && s < 0) || (!front && s >= 0)) {
                continue;
            }
            double dx = Math.cos(ang) * ringR;
            double dy = s * ringR * tilt;
            double a = (0.18 + 0.45 * (s + 1) / 2) * alpha;
            double sz = 1.0 + 1.4 * (s + 1) / 2;
            gc.setFill(Color.color(p.c2.getRed(), p.c2.getGreen(), p.c2.getBlue(), a));
            gc.fillOval(px + dx - sz / 2, py + dy - sz / 2, sz, sz);
        }
    }

    private void drawCore(double cx, double cy, double minWH) {
        double coreR = minWH * 0.05 * (1 + 0.06 * Math.sin(pulse));

        // 旋转光冕射线
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(pulse * 12);
        gc.setStroke(Color.color(1.0, 0.9, 0.6, 0.07));
        gc.setLineWidth(2.0);
        for (int i = 0; i < 8; i++) {
            gc.rotate(Math.PI / 4);
            gc.strokeLine(0, -coreR * 0.6, 0, -coreR * 3.4);
        }
        gc.restore();

        // 外辉光
        double glowR = coreR * 4.6;
        gc.setFill(new RadialGradient(0, 0, cx, cy, glowR, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(1.0, 0.92, 0.7, 0.5)),
                new Stop(0.4, Color.color(1.0, 0.8, 0.5, 0.14)),
                new Stop(1, Color.color(1.0, 0.7, 0.4, 0.0))));
        gc.fillOval(cx - glowR, cy - glowR, glowR * 2, glowR * 2);

        // 核心球体
        gc.setFill(new RadialGradient(0, 0, cx, cy, coreR, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(1.0, 1.0, 0.96, 1.0)),
                new Stop(0.7, Color.color(1.0, 0.92, 0.7, 0.95)),
                new Stop(1, Color.color(1.0, 0.8, 0.5, 0.0))));
        gc.fillOval(cx - coreR, cy - coreR, coreR * 2, coreR * 2);
    }

    private void drawHandCursor(double x, double y) {
        gc.setFill(Color.color(0.87, 1.0, 0.6, 0.12));
        gc.fillOval(x - 22, y - 22, 44, 44);
        gc.setStroke(Color.web("#deff9a"));
        gc.setLineWidth(2.0);
        gc.strokeOval(x - 16, y - 16, 32, 32);
        gc.setFill(Color.color(0.87, 1.0, 0.6, 0.95));
        gc.fillOval(x - 3, y - 3, 6, 6);
    }

    private static Color lerpColor(Color a, Color b, double t) {
        return Color.color(
                a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    }

    private static Color lerpColor3(Color a, Color mid, Color b, double t) {
        return t < 0.5 ? lerpColor(a, mid, t * 2) : lerpColor(mid, b, (t - 0.5) * 2);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
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
            s.vx = 0;
            s.vy = 0;
            s.z = Math.random();
            s.size = 0.8 + Math.random() * 1.8;
            s.twinkle = Math.random() * Math.PI * 2;
            s.twinkleSpeed = 0.02 + Math.random() * 0.05;
            s.gatherAngle = Math.random() * Math.PI * 2;
            s.driftA = Math.random() * Math.PI * 2;
            s.driftR = 0.01 + Math.random() * 0.02;
            s.tint = STAR_TINTS[(int) (Math.random() * STAR_TINTS.length)];
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
            p.flow = Math.random() * Math.PI * 2;
            p.flowSpeed = 0.003 + Math.random() * 0.006;
            p.offsets = sphere;
            p.ptc = new double[sphere.length];
            for (int j = 0; j < p.ptc.length; j++) {
                p.ptc[j] = Math.random();
            }
            p.dust = new double[DUST_RING_PARTICLES][1];
            for (int j = 0; j < p.dust.length; j++) {
                p.dust[j][0] = j * (2 * Math.PI / DUST_RING_PARTICLES) + (Math.random() - 0.5) * 0.1;
            }
            GameInfo gi = GAME_INFO.get(i);
            p.c1 = Color.web(gi.color1());
            p.c2 = Color.web(gi.color2());
            p.cMid = lerpColor(p.c1, p.c2, 0.5);
            ps[i] = p;
        }
        return ps;
    }

    private Nebula[] createNebulas() {
        Nebula[] ns = new Nebula[3];
        ns[0] = makeNebula(0.25, 0.35, 0.55, "#4c1d95");
        ns[1] = makeNebula(0.78, 0.62, 0.5, "#0f766e");
        ns[2] = makeNebula(0.55, 0.18, 0.6, "#1e3a8a");
        return ns;
    }

    private Nebula makeNebula(double nx, double ny, double r, String hex) {
        Nebula n = new Nebula();
        n.nx = nx;
        n.ny = ny;
        n.r = r;
        n.color = Color.web(hex);
        n.phase = Math.random() * Math.PI * 2;
        n.phaseSpeed = 0.002 + Math.random() * 0.003;
        return n;
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

    private static final class Star {
        double hx, hy;
        double x, y;
        double vx, vy;
        double z;
        double size;
        double twinkle;
        double twinkleSpeed;
        double gatherAngle;
        double driftA, driftR;
        Color tint;
    }

    private static final class Planet {
        int index;
        double slotAngle;
        double displayAngle;
        double scale;
        double spin;
        double spinSpeed;
        double flow;
        double flowSpeed;
        double[][] offsets;
        double[] ptc;
        double[][] dust;
        Color c1;
        Color cMid;
        Color c2;
    }

    private static final class Nebula {
        double nx, ny;
        double r;
        Color color;
        double phase;
        double phaseSpeed;
    }
}
