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
import com.gesturegame.game.TarotGame;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureCommandResolver;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.Bloom;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 星河大厅控制器：单团 3D 粒子云在 6 种游戏形态间形变（仿 GEM 粒子幻境）。
 *
 * <p>渲染要点：
 * <ul>
 *   <li>~3000 粒子，每帧 lerp 向当前形态目标点 → 平滑形变</li>
 *   <li>柔光径向 sprite（PixelWriter 预渲染）+ drawImage，替掉硬圆点</li>
 *   <li>Canvas 节点套 {@link Glow} 效果 → 真·泛光，逼近 GEM 的 UnrealBloom</li>
 *   <li>3D 透视投影（绕 Y 自转 + 近大远小近亮远暗），按深度排序</li>
 *   <li>OPEN 张手 → 能量爆发（整体缩放 + 外层扩得更多 + 湍流 + 增亮）</li>
 *   <li>随机闪烁（闪白衰减）、深底 #020205 + 暗角雾、手位视差</li>
 * </ul>
 *
 * <p>选择按索引：SWIPE 切形态、FIST 按住确认进游戏（复用现有滑动状态机）。
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

    private record GameInfo(String name, String desc, String icon, String color, String shape) {}
    private static final List<GameInfo> GAME_INFO = List.of(
            new GameInfo("接水果", "手势控篮子接水果躲炸弹", "🍎", "#22d3ee", "sphere"),
            new GameInfo("猜拳", "三秒倒计时出拳对决", "✂️", "#ec4899", "galaxy"),
            new GameInfo("戳泡泡", "手势瞄准戳破泡泡连击", "🫧", "#84cc16", "lotus"),
            new GameInfo("塔罗牌", "选牌翻牌探索命运", "🔮", "#f59e0b", "heart"),
            new GameInfo("切水果", "手滑动切水果躲炸弹", "🔪", "#f97316", "saturn"),
            new GameInfo("节奏大师", "按时摆出正确手势", "🥁", "#a78bfa", "star"));

    private static final int PARTICLE_COUNT = 3000;
    private static final int SPRITE_SIZE = 64;
    private static final double CAMERA_Z = 30.0;
    private static final double PX_PER_UNIT = 16.0;
    private static final double LERP_SPEED = 0.06;
    private static final double MORPH_LERP = 0.12;
    private static final double STILL_X = 0.016;
    private static final double ROT_SPEED = 0.004;
    private static final double TURBULENCE = 0.03;
    private static final double PARTICLE_DRAW_SIZE = 10.0;
    private static final long NAVIGATION_COOLDOWN_MS = 180L;
    private static final long ACTION_COOLDOWN_MS = 500L;

    @FXML
    private Canvas lobbyCanvas;

    @FXML
    private ImageView cameraView;

    @FXML
    private Label statusLabel;

    private AppStateManager appStateManager;
    private GraphicsContext gc;
    private Image[] sprites;
    private double[][][] shapeTargets;
    private Particle[] particles;
    private Integer[] order;
    private final double[] sxArr = new double[PARTICLE_COUNT];
    private final double[] syArr = new double[PARTICLE_COUNT];
    private final double[] szArr = new double[PARTICLE_COUNT];
    private final double[] saArr = new double[PARTICLE_COUNT];
    private final double[] ssArr = new double[PARTICLE_COUNT];
    private int currentIndex;
    private long lastNavigationTime;
    private long lastActionTime;
    private double morph;
    private double rotY;
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
        Bloom bloomEffect = new Bloom();
        bloomEffect.setThreshold(0.6);
        lobbyCanvas.setEffect(bloomEffect);

        sprites = createSprites();
        shapeTargets = createShapeTargets();
        particles = createParticles();
        order = new Integer[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            order[i] = i;
        }
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

        double vx = gesture != null ? gesture.getVelocityX() : 0.0;
        boolean hand = gesture != null && gesture.isHandDetected();
        double handX = hand ? gesture.getHandX() : 0.5;
        double handY = hand ? gesture.getHandY() : 0.5;

        // 聚散：手静止时 OPEN=散(+1)/FIST=聚(-1)；挥手时(|vx|大)抑制，避免与切换冲突
        double morphTarget = 0.0;
        if (hand && Math.abs(vx) < STILL_X) {
            GestureType g = gesture.getGesture();
            if (g == GestureType.OPEN) {
                morphTarget = 1.0;
            } else if (g == GestureType.FIST) {
                morphTarget = -1.0;
            }
        }
        morph += (morphTarget - morph) * MORPH_LERP;
        rotY += ROT_SPEED * (1.0 + Math.max(0.0, morph) * 2.0);
        double time = pulse;
        pulse += 0.016;
        double turb = TURBULENCE + Math.max(0.0, morph) * 0.15;

        double[][] targets = shapeTargets[currentIndex];
        for (int i = 0; i < particles.length; i++) {
            Particle p = particles[i];
            double tx = targets[i][0] + Math.sin(time * 2 + i) * turb;
            double ty = targets[i][1] + Math.cos(time * 3 + i) * turb;
            double tz = targets[i][2] + Math.sin(time * 4 + i) * turb;
            p.x += (tx - p.x) * p.lerp;
            p.y += (ty - p.y) * p.lerp;
            p.z += (tz - p.z) * p.lerp;
            if (Math.random() > 0.9995) {
                p.spark = 1.0;
            }
            p.spark *= 0.9;
        }

        drawBackground(w, h);

        double cx = w / 2.0 + (handX - 0.5) * 30.0;
        double cy = h / 2.0 + (handY - 0.5) * 30.0;
        double cosR = Math.cos(rotY);
        double sinR = Math.sin(rotY);

        for (int i = 0; i < particles.length; i++) {
            Particle p = particles[i];
            double rx = p.x * cosR + p.z * sinR;
            double rz = -p.x * sinR + p.z * cosR;
            double ry = p.y;
            double dist = Math.hypot(p.x, Math.hypot(p.y, p.z));
            double normDist = Math.min(dist / 12.0, 1.0);
            double scale = (1.0 + morph * 0.5) * (1.0 + Math.max(0.0, morph) * Math.pow(normDist, 1.5) * 1.3);
            rx *= scale;
            ry *= scale;
            rz *= scale;
            double factor = CAMERA_Z / (CAMERA_Z - rz);
            sxArr[i] = cx + rx * factor * PX_PER_UNIT;
            syArr[i] = cy + ry * factor * PX_PER_UNIT;
            szArr[i] = rz;
            double depthBright = 0.4 + 0.6 * (rz + 12.0) / 24.0;
            saArr[i] = clamp(p.brightness * depthBright * (0.7 + 0.4 * morph), 0.05, 1.0);
            ssArr[i] = PARTICLE_DRAW_SIZE * factor * (0.7 + 0.3 * p.brightness) * (1.0 + morph * 0.3);
        }
        Arrays.sort(order, (a, b) -> Double.compare(szArr[a], szArr[b]));

        Image sprite = sprites[currentIndex];
        Image sparkSprite = sprites[sprites.length - 1];
        for (int idx : order) {
            if (particles[idx].spark > 0.2) {
                gc.setGlobalAlpha(clamp(particles[idx].spark, 0.0, 1.0));
                double ss = ssArr[idx] * 1.6;
                gc.drawImage(sparkSprite, sxArr[idx] - ss / 2, syArr[idx] - ss / 2, ss, ss);
            }
            gc.setGlobalAlpha(saArr[idx]);
            double ss = ssArr[idx];
            gc.drawImage(sprite, sxArr[idx] - ss / 2, syArr[idx] - ss / 2, ss, ss);
        }
        gc.setGlobalAlpha(1.0);

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
                    if (appStateManager != null) {
                        appStateManager.switchState(AppStateManager.STATE_LOGIN);
                    }
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
        CameraStreamHelper.push(cameraView, base64Image);
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
        LOGGER.info(() -> "[大厅] 启动游戏: " + game.getName() + " (形态索引 " + currentIndex + ")");
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
                + "    ·    挥手切换 · 张手散 · 握拳聚 · ✌️开始");
    }

    // ===== 渲染 =====

    private void drawBackground(double w, double h) {
        gc.setFill(Color.web("#020205"));
        gc.fillRect(0, 0, w, h);
        double cx = w / 2.0;
        double cy = h / 2.0;
        double maxR = Math.hypot(w, h) / 2.0;
        gc.setFill(new RadialGradient(0, 0, cx, cy, maxR, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.01, 0.01, 0.02, 0.0)),
                new Stop(0.65, Color.color(0.01, 0.01, 0.02, 0.0)),
                new Stop(1, Color.color(0.01, 0.01, 0.02, 0.85))));
        gc.fillRect(0, 0, w, h);
    }

    private void drawHandCursor(double x, double y) {
        gc.setFill(Color.color(0.87, 1.0, 0.6, 0.1));
        gc.fillOval(x - 22, y - 22, 44, 44);
        gc.setStroke(Color.web("#deff9a"));
        gc.setLineWidth(2.0);
        gc.strokeOval(x - 16, y - 16, 32, 32);
        gc.setFill(Color.color(0.87, 1.0, 0.6, 0.95));
        gc.fillOval(x - 3, y - 3, 6, 6);
    }

    private Image[] createSprites() {
        Image[] sp = new Image[GAME_INFO.size() + 1];
        for (int i = 0; i < GAME_INFO.size(); i++) {
            sp[i] = makeSprite(Color.web(GAME_INFO.get(i).color()));
        }
        sp[sp.length - 1] = makeSprite(Color.color(1.0, 1.0, 1.0));
        return sp;
    }

    private Image makeSprite(Color tint) {
        int s = SPRITE_SIZE;
        WritableImage img = new WritableImage(s, s);
        PixelWriter pw = img.getPixelWriter();
        double r = tint.getRed();
        double g = tint.getGreen();
        double b = tint.getBlue();
        double half = (s - 1) / 2.0;
        for (int py = 0; py < s; py++) {
            for (int px = 0; px < s; px++) {
                double dx = (px - half) / half;
                double dy = (py - half) / half;
                double d = Math.min(1.0, Math.sqrt(dx * dx + dy * dy));
                double a = Math.max(0.0, 1.0 - d);
                a = a * a;
                pw.setColor(px, py, new Color(r, g, b, a));
            }
        }
        return img;
    }

    private double[][][] createShapeTargets() {
        double[][][] all = new double[GAME_INFO.size()][PARTICLE_COUNT][3];
        for (int i = 0; i < GAME_INFO.size(); i++) {
            buildShape(GAME_INFO.get(i).shape(), all[i]);
        }
        return all;
    }

    private void buildShape(String type, double[][] pos) {
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            double x = 0, y = 0, z = 0;
            switch (type) {
                case "sphere": {
                    double r = 10 + Math.random() * 2;
                    double theta = Math.random() * Math.PI * 2;
                    double phi = Math.acos(2 * Math.random() - 1);
                    x = r * Math.sin(phi) * Math.cos(theta);
                    y = r * Math.sin(phi) * Math.sin(theta);
                    z = r * Math.cos(phi);
                    if (i < n * 0.2) {
                        x *= 0.3;
                        y *= 0.3;
                        z *= 0.3;
                    }
                    break;
                }
                case "galaxy": {
                    int arms = 3;
                    int spin = i % arms;
                    double offset = (spin / (double) arms) * Math.PI * 2;
                    double dist = Math.pow(Math.random(), 0.5);
                    double r = dist * 20;
                    double angle = dist * 10 + offset;
                    x = r * Math.cos(angle);
                    z = r * Math.sin(angle);
                    y = (Math.random() - 0.5) * (15 - r) * 0.2;
                    if (r < 2) {
                        y *= 0.2;
                    }
                    break;
                }
                case "lotus": {
                    double u = Math.random() * Math.PI * 2;
                    double v = Math.random();
                    int petals = 7;
                    double rBase = 8 * (0.5 + 0.5 * Math.pow(Math.sin(petals * u * 0.5), 2)) * v;
                    x = rBase * Math.cos(u);
                    z = rBase * Math.sin(u);
                    y = 4 * Math.pow(v, 2) - 2;
                    if (i < n * 0.15) {
                        x = (Math.random() - 0.5);
                        z = (Math.random() - 0.5);
                        y = (Math.random() - 0.5) * 10;
                    }
                    break;
                }
                case "heart": {
                    double t = Math.PI - 2 * Math.PI * Math.random();
                    double u = 2 * Math.PI * Math.random();
                    x = 16 * Math.pow(Math.sin(t), 3);
                    y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
                    z = 6 * Math.cos(t) * Math.sin(u) * Math.sin(t);
                    double sc = 0.6;
                    x *= sc;
                    y *= sc;
                    z *= sc;
                    if (Math.random() > 0.8) {
                        x *= 1.1;
                        y *= 1.1;
                        z *= 1.1;
                    }
                    break;
                }
                case "saturn": {
                    if (i < n * 0.3) {
                        double r = 5.5;
                        double theta = Math.random() * Math.PI * 2;
                        double phi = Math.acos(2 * Math.random() - 1);
                        x = r * Math.sin(phi) * Math.cos(theta);
                        y = r * 0.9 * Math.sin(phi) * Math.sin(theta);
                        z = r * Math.cos(phi);
                    } else {
                        double angle = Math.random() * Math.PI * 2;
                        double sel = Math.random();
                        double r;
                        double thick;
                        if (sel < 0.45) {
                            r = 7 + Math.random() * 3.5;
                            thick = 0.2;
                        } else if (sel < 0.5) {
                            x = 0;
                            y = 0;
                            z = 0;
                            break;
                        } else {
                            r = 12 + Math.random() * 5;
                            thick = 0.4;
                        }
                        r += (Math.random() - 0.5) * 0.3;
                        x = r * Math.cos(angle);
                        y = (Math.random() - 0.5) * thick;
                        z = r * Math.sin(angle);
                        double tilt = 0.4;
                        double yNew = y * Math.cos(tilt) - x * Math.sin(tilt);
                        double xNew = y * Math.sin(tilt) + x * Math.cos(tilt);
                        x = xNew;
                        y = yNew;
                    }
                    break;
                }
                case "star": {
                    double a = Math.random() * Math.PI * 2;
                    int points = 5;
                    double segAngle = Math.PI * 2 / points;
                    double halfSeg = segAngle / 2;
                    double localA = a % segAngle;
                    double tt = localA / halfSeg;
                    if (tt > 1) {
                        tt = 2 - tt;
                    }
                    double r = (Math.random() < 0.15) ? Math.random() * 5 : lerp(12, 5, tt);
                    x = r * Math.cos(a);
                    y = r * Math.sin(a);
                    z = (Math.random() - 0.5) * 2;
                    break;
                }
                default:
                    break;
            }
            pos[i][0] = x;
            pos[i][1] = y;
            pos[i][2] = z;
        }
    }

    private Particle[] createParticles() {
        Particle[] ps = new Particle[PARTICLE_COUNT];
        double[][] base = shapeTargets[0];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.brightness = 0.2 + Math.random() * 0.8;
            p.spark = 0.0;
            p.lerp = LERP_SPEED * (0.7 + Math.random() * 0.6);
            p.x = base[i][0];
            p.y = base[i][1];
            p.z = base[i][2];
            ps[i] = p;
        }
        return ps;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Particle {
        double x, y, z;
        double brightness;
        double spark;
        double lerp;
    }
}
