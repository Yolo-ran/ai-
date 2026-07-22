package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 暗星采矿：移动手掌控制引力回收舱，收集星核并避开反物质地雷。
 *
 * <p>宇宙主题只替换渲染资源，得分、碰撞、难度和手势逻辑仍完全留在 Java 端，
 * 不改变既有协议。</p>
 */
public class CatchFruit implements GameInterface {

    private static final Random RANDOM = new Random();
    private static final int INK_LANE_COUNT = 60;
    private static final int MAX_PARTICLES = 220;
    private static final int MAX_COLLECTION_EFFECTS = 24;
    private static final double READY_SECONDS = 2.35;
    private static final double COMBO_WINDOW = 1.25;
    private static final double FEVER_SECONDS = 5.5;
    private static final double BASKET_HEIGHT = 30.0;
    private static final String SPACE_ASSET_ROOT = "/assets/catchfruit/space/";
    private static final Image SPACE_BACKGROUND = loadImage(SPACE_ASSET_ROOT + "background.png");
    private static final Image COLLECTOR_IMAGE = loadImage(SPACE_ASSET_ROOT + "collector.png");
    private static final Image COLLECTOR_FEVER_IMAGE = loadImage(SPACE_ASSET_ROOT + "collector-fever.png");

    private static final LinearGradient BASKET_FILL = new LinearGradient(0, 0, 0, 1, true,
            CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#f7d794")),
            new Stop(0.28, Color.web("#ca8a3b")),
            new Stop(1, Color.web("#70421f")));
    private static final Font HUD_LABEL_FONT = Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 12);
    private static final Font HUD_VALUE_FONT = Font.font("Microsoft YaHei UI", FontWeight.BOLD, 25);
    private static final Font TITLE_FONT = Font.font("Microsoft YaHei UI", FontWeight.BOLD, 21);
    private static final Font COUNTDOWN_FONT = Font.font("Microsoft YaHei UI", FontWeight.BOLD, 88);
    private static final Font COMBO_FONT = Font.font("Microsoft YaHei UI", FontWeight.BOLD, 31);
    private static final Color[] INK_COLORS = {
            Color.rgb(255, 69, 58), Color.rgb(255, 149, 0), Color.rgb(255, 214, 10),
            Color.rgb(52, 199, 89), Color.rgb(0, 199, 190), Color.rgb(48, 176, 199),
            Color.rgb(0, 122, 255), Color.rgb(88, 86, 214), Color.rgb(175, 82, 222),
            Color.rgb(255, 45, 85), Color.rgb(255, 100, 130), Color.rgb(164, 255, 46),
            Color.rgb(46, 255, 220), Color.rgb(100, 200, 255), Color.rgb(205, 150, 255),
            Color.rgb(255, 215, 0)
    };

    private enum Phase { READY, PLAYING, FINISHED }

    private enum FruitKind {
        APPLE(Color.web("#ff453a"), Color.web("#a91d2d"), 10, "red-dwarf-core.png"),
        ORANGE(Color.web("#ff9f0a"), Color.web("#c55a11"), 10, "stellar-ember.png"),
        LEMON(Color.web("#ffd60a"), Color.web("#d19a00"), 10, "solar-crystal.png"),
        LIME(Color.web("#53e6c2"), Color.web("#238f78"), 10, "nebula-crystal.png"),
        BERRY(Color.web("#af52de"), Color.web("#6731a8"), 15, "dark-matter-orb.png"),
        GOLD(Color.web("#ffd700"), Color.web("#fff0a3"), 30, "golden-singularity.png"),
        BOMB(Color.web("#ff453a"), Color.web("#050609"), 0, "antimatter-mine.png");

        final Color main;
        final Color shade;
        final int points;
        final Image sprite;

        FruitKind(Color main, Color shade, int points, String assetName) {
            this.main = main;
            this.shade = shade;
            this.points = points;
            this.sprite = loadImage(SPACE_ASSET_ROOT + assetName);
        }
    }

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int targetScore;
    private int lives;
    private int level;
    private int combo;
    private int bestCombo;
    private int caughtCount;
    private int missedCount;
    private boolean won;
    private boolean handDetected;
    private Difficulty difficulty = Difficulty.NORMAL;
    private Phase phase = Phase.READY;

    private double phaseTime;
    private double elapsedTime;
    private double spawnTimer;
    private double spawnDelayMin;
    private double spawnDelayMax;
    private double fallSpeed;
    private double bombProbability;
    private double basketBaseWidth;
    private double basketX;
    private double basketTargetX;
    private double basketY;
    private double comboTimer;
    private double feverCharge;
    private double feverTime;
    private double flashTime;
    private double shakeTime;
    private double collectorPulseTime;
    private double collectorPulseDuration;
    private double handLostTime;
    private long lastUpdateNanos;
    private Color collectorPulseColor = Color.web("#5eead4");

    private final List<FallingItem> items = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<CollectionEffect> collectionEffects = new ArrayList<>();
    private final List<InkLane> inkLanes = new ArrayList<>();

    @Override
    public String getName() {
        return "暗星采矿";
    }

    @Override
    public String getDescription() {
        return "移动手掌控制引力回收舱，连击收集星核并避开反物质地雷";
    }

    @Override
    public String getIcon() {
        return "🪐";
    }

    @Override
    public void init(int width, int height) {
        canvasWidth = Math.max(1, width);
        canvasHeight = Math.max(1, height);
        score = 0;
        level = 1;
        combo = 0;
        bestCombo = 0;
        caughtCount = 0;
        missedCount = 0;
        won = false;
        handDetected = false;
        phase = Phase.READY;
        phaseTime = 0.0;
        elapsedTime = 0.0;
        spawnTimer = 0.55;
        comboTimer = 0.0;
        feverCharge = 0.0;
        feverTime = 0.0;
        flashTime = 0.0;
        shakeTime = 0.0;
        collectorPulseTime = 0.0;
        collectorPulseDuration = 0.0;
        collectorPulseColor = Color.web("#5eead4");
        handLostTime = 0.0;
        lastUpdateNanos = 0L;
        items.clear();
        particles.clear();
        floatingTexts.clear();
        collectionEffects.clear();
        applyDifficulty();
        basketX = canvasWidth * 0.5 - basketBaseWidth * 0.5;
        basketTargetX = basketX;
        basketY = canvasHeight - Math.max(76.0, canvasHeight * 0.105);
        if (SPACE_BACKGROUND == null) {
            createInkLanes();
        } else {
            inkLanes.clear();
        }
    }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY -> {
                targetScore = 100;
                lives = 5;
                basketBaseWidth = 138;
                spawnDelayMin = 0.82;
                spawnDelayMax = 1.08;
                fallSpeed = 150;
                bombProbability = 0.06;
            }
            case NORMAL -> {
                targetScore = 200;
                lives = 3;
                basketBaseWidth = 112;
                spawnDelayMin = 0.62;
                spawnDelayMax = 0.90;
                fallSpeed = 188;
                bombProbability = 0.15;
            }
            case HARD -> {
                targetScore = 300;
                lives = 2;
                basketBaseWidth = 90;
                spawnDelayMin = 0.45;
                spawnDelayMax = 0.70;
                fallSpeed = 228;
                bombProbability = 0.23;
            }
            case ENDLESS -> {
                targetScore = -1;
                lives = 3;
                basketBaseWidth = 108;
                spawnDelayMin = 0.58;
                spawnDelayMax = 0.86;
                fallSpeed = 194;
                bombProbability = 0.14;
            }
        }
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
    }

    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public void update(GestureData gesture) {
        double dt = frameDeltaSeconds();
        phaseTime += dt;
        flashTime = Math.max(0.0, flashTime - dt);
        shakeTime = Math.max(0.0, shakeTime - dt);
        collectorPulseTime = Math.max(0.0, collectorPulseTime - dt);
        if (SPACE_BACKGROUND == null) {
            updateInkLanes(dt);
        }
        updateParticles(dt);
        updateFloatingTexts(dt);
        updateCollectionEffects(dt);
        updateBasket(gesture, dt);

        if (phase == Phase.READY) {
            if (phaseTime >= READY_SECONDS) {
                phase = Phase.PLAYING;
                phaseTime = 0.0;
                spawnTimer = 0.15;
                floatingTexts.add(new FloatingText(canvasWidth * 0.5, canvasHeight * 0.39,
                        "开始回收!", Color.web("#ffffff"), 1.0, 30));
            }
            return;
        }

        if (phase == Phase.FINISHED) {
            return;
        }

        elapsedTime += dt;
        comboTimer -= dt;
        if (comboTimer <= 0.0) {
            combo = 0;
        }

        if (feverTime > 0.0) {
            feverTime = Math.max(0.0, feverTime - dt);
            if (feverTime == 0.0) {
                feverCharge = 0.0;
            }
        }

        updateLevel();
        spawnTimer -= dt;
        if (spawnTimer <= 0.0) {
            spawnWave();
            double pace = Math.max(0.70, 1.0 - (level - 1) * 0.045);
            spawnTimer = random(spawnDelayMin, spawnDelayMax) * pace;
        }

        updateItems(dt);
        resolveBasketCollisions();
        removeMissedItems();

        if (targetScore > 0 && score >= targetScore) {
            score = targetScore;
            finish(true);
        } else if (lives <= 0) {
            lives = 0;
            finish(false);
        }
    }

    private double frameDeltaSeconds() {
        long now = System.nanoTime();
        if (lastUpdateNanos == 0L) {
            lastUpdateNanos = now;
            return 1.0 / 60.0;
        }
        double dt = (now - lastUpdateNanos) / 1_000_000_000.0;
        lastUpdateNanos = now;
        return clamp(dt, 1.0 / 120.0, 1.0 / 30.0);
    }

    private void updateBasket(GestureData gesture, double dt) {
        handDetected = gesture != null && gesture.isHandDetected();
        if (handDetected) {
            handLostTime = 0.0;
            double handX = clamp(gesture.getHandX(), 0.0, 1.0) * canvasWidth;
            basketTargetX = clamp(handX - currentBasketWidth() * 0.5,
                    playfieldLeft() + 14.0, playfieldRight() - currentBasketWidth() - 14.0);
        } else {
            handLostTime += dt;
        }
        double smoothing = 1.0 - Math.exp(-dt * 15.0);
        basketX += (basketTargetX - basketX) * smoothing;
        basketX = clamp(basketX, playfieldLeft() + 14.0,
                playfieldRight() - currentBasketWidth() - 14.0);
    }

    private double currentBasketWidth() {
        return basketBaseWidth * (feverTime > 0.0 ? 1.32 : 1.0);
    }

    private double playfieldLeft() {
        return Math.max(52.0, canvasWidth * 0.15);
    }

    private double playfieldRight() {
        return canvasWidth - playfieldLeft();
    }

    private void updateLevel() {
        int progressLevel = targetScore > 0 ? score / Math.max(50, targetScore / 4) : score / 80;
        level = Math.min(9, 1 + progressLevel);
    }

    private void spawnWave() {
        int count = 1;
        double roll = RANDOM.nextDouble();
        if (level >= 2 && roll > 0.72) count = 2;
        if (level >= 5 && roll > 0.91) count = 3;

        double itemGap = Math.max(58.0, canvasWidth * 0.07);
        double minimumCenter = playfieldLeft() + 44.0 + itemGap;
        double maximumCenter = playfieldRight() - 44.0 - itemGap;
        double center = random(Math.min(minimumCenter, maximumCenter),
                Math.max(minimumCenter, maximumCenter));
        for (int i = 0; i < count; i++) {
            double x = center + (i - (count - 1) * 0.5) * itemGap;
            x = clamp(x, playfieldLeft() + 38.0, playfieldRight() - 38.0);
            double delayY = i % 2 == 0 ? 0.0 : -42.0;
            spawnItem(x, delayY, count > 1);
        }
    }

    private void spawnItem(double x, double delayY, boolean wave) {
        FruitKind kind;
        double roll = RANDOM.nextDouble();
        double adjustedBombChance = Math.min(0.34, bombProbability + (level - 1) * 0.008);
        if (roll < adjustedBombChance) {
            kind = FruitKind.BOMB;
        } else if (roll > 0.965) {
            kind = FruitKind.GOLD;
        } else {
            FruitKind[] fruits = {FruitKind.APPLE, FruitKind.ORANGE, FruitKind.LEMON,
                    FruitKind.LIME, FruitKind.BERRY};
            kind = fruits[RANDOM.nextInt(fruits.length)];
        }

        double radius = kind == FruitKind.GOLD ? 24.0 : random(20.0, 25.0);
        double speedScale = 1.0 + (level - 1) * 0.065;
        double vy = fallSpeed * speedScale * random(0.88, 1.12);
        double vx = wave ? random(-13.0, 13.0) : random(-22.0, 22.0);
        items.add(new FallingItem(x, -radius - 8 + delayY, vx, vy, radius,
                random(-95.0, 95.0), kind));
    }

    private void updateItems(double dt) {
        for (FallingItem item : items) {
            item.previousY = item.y;
            item.x += item.vx * dt;
            item.y += item.vy * dt;
            item.rotation += item.rotationSpeed * dt;
            if (item.x - item.radius < playfieldLeft() + 8.0
                    || item.x + item.radius > playfieldRight() - 8.0) {
                item.vx *= -1;
                item.x = clamp(item.x, playfieldLeft() + 8.0 + item.radius,
                        playfieldRight() - 8.0 - item.radius);
            }
        }
    }

    private void resolveBasketCollisions() {
        double basketWidth = currentBasketWidth();
        double basketTop = basketY - 7;
        Iterator<FallingItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            FallingItem item = iterator.next();
            boolean crossedTop = item.previousY + item.radius <= basketTop + 9
                    && item.y + item.radius >= basketTop;
            boolean withinBasket = item.x + item.radius * 0.70 >= basketX
                    && item.x - item.radius * 0.70 <= basketX + basketWidth;
            if (!crossedTop || !withinBasket) {
                continue;
            }
            iterator.remove();
            if (item.kind == FruitKind.BOMB) {
                hitBomb(item);
            } else {
                catchFruit(item);
            }
        }
    }

    private void catchFruit(FallingItem item) {
        caughtCount++;
        combo = comboTimer > 0.0 ? combo + 1 : 1;
        comboTimer = COMBO_WINDOW;
        bestCombo = Math.max(bestCombo, combo);
        int multiplier = 1 + Math.min(3, Math.max(0, combo - 1) / 5);
        if (feverTime > 0.0) multiplier *= 2;
        int gained = item.kind.points * multiplier;
        score += gained;
        feverCharge = Math.min(1.0, feverCharge + (item.kind == FruitKind.GOLD ? 0.28 : 0.105));
        if (feverCharge >= 1.0 && feverTime <= 0.0) {
            feverTime = FEVER_SECONDS;
            flashTime = 0.22;
            floatingTexts.add(new FloatingText(canvasWidth * 0.5, canvasHeight * 0.30,
                    "引力超载!  得分 ×2", Color.web("#fff5b8"), 1.15, 28));
        }
        spawnCollectionEffect(item, item.kind == FruitKind.GOLD);
        spawnBurst(item.x, basketY, item.kind.main, item.kind == FruitKind.GOLD ? 20 : 12);
        floatingTexts.add(new FloatingText(item.x, basketY - 18,
                "+" + gained, item.kind.main, 0.72, item.kind == FruitKind.GOLD ? 24 : 18));
        if (combo >= 3) {
            floatingTexts.add(new FloatingText(item.x, basketY - 42,
                    combo + " 连击", Color.web("#ffffff"), 0.62, 14));
        }
    }

    private void hitBomb(FallingItem item) {
        lives--;
        combo = 0;
        comboTimer = 0.0;
        feverCharge = Math.max(0.0, feverCharge - 0.35);
        feverTime = 0.0;
        score = Math.max(0, score - 20);
        flashTime = 0.30;
        shakeTime = 0.32;
        collectorPulseTime = 0.34;
        collectorPulseDuration = collectorPulseTime;
        collectorPulseColor = Color.web("#ff2338");
        spawnBurst(item.x, basketY, Color.web("#ff453a"), 26);
        floatingTexts.add(new FloatingText(item.x, basketY - 28,
                "反物质地雷!  -1 生命", Color.web("#ff6b61"), 0.95, 21));
    }

    private void removeMissedItems() {
        Iterator<FallingItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            FallingItem item = iterator.next();
            if (item.y - item.radius <= canvasHeight + 24) {
                continue;
            }
            iterator.remove();
            if (item.kind != FruitKind.BOMB) {
                missedCount++;
                lives--;
                combo = 0;
                comboTimer = 0.0;
                feverCharge = Math.max(0.0, feverCharge - 0.12);
                floatingTexts.add(new FloatingText(
                        clamp(item.x, 70, canvasWidth - 70), canvasHeight - 62,
                        "能量逸失  -1 生命", Color.web("#ff9f8f"), 0.85, 17));
            }
        }
    }

    private void finish(boolean victory) {
        won = victory;
        phase = Phase.FINISHED;
        phaseTime = 0.0;
        items.clear();
    }

    private void spawnBurst(double x, double y, Color color, int count) {
        int available = Math.max(0, MAX_PARTICLES - particles.size());
        int actual = Math.min(count, available);
        for (int i = 0; i < actual; i++) {
            double angle = random(Math.PI * 1.08, Math.PI * 1.92);
            double speed = random(55, 190);
            particles.add(new Particle(x, y, Math.cos(angle) * speed,
                    Math.sin(angle) * speed, random(2.0, 5.4), random(0.42, 0.78), color));
        }
    }

    private void updateParticles(double dt) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.life -= dt;
            if (particle.life <= 0.0) {
                iterator.remove();
                continue;
            }
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            particle.vy += 260 * dt;
            particle.vx *= Math.pow(0.90, dt * 60.0);
        }
    }

    private void updateFloatingTexts(double dt) {
        Iterator<FloatingText> iterator = floatingTexts.iterator();
        while (iterator.hasNext()) {
            FloatingText text = iterator.next();
            text.life -= dt;
            text.y -= 38 * dt;
            if (text.life <= 0.0) iterator.remove();
        }
    }

    /**
     * 每一列同时保存“旧墨色”和正在从上向下刷新的“新墨色”。
     * 新颜色刷到底部时成为下一轮的旧颜色，因此不会出现一帧清空的闪烁。
     */
    private void createInkLanes() {
        inkLanes.clear();
        double laneWidth = Math.ceil(canvasWidth / (double) INK_LANE_COUNT) + 0.6;
        for (int i = 0; i < INK_LANE_COUNT; i++) {
            Color base = randomInkColor(null);
            Color flowing = randomInkColor(base);
            double capRadius = laneWidth * 0.5;
            double headY = random(-capRadius, canvasHeight + capRadius);
            inkLanes.add(new InkLane(i * (canvasWidth / (double) INK_LANE_COUNT), laneWidth,
                    headY, random(96.0, 285.0), base, flowing));
        }
    }

    private void updateInkLanes(double dt) {
        for (InkLane lane : inkLanes) {
            lane.headY += lane.speed * dt;
            if (lane.headY - lane.width * 0.5 <= canvasHeight) {
                continue;
            }
            lane.baseColor = lane.flowingColor;
            lane.flowingColor = randomInkColor(lane.baseColor);
            lane.headY = -lane.width * 0.5;
            lane.speed = random(96.0, 285.0);
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        double shakeX = shakeTime > 0.0 ? Math.sin(shakeTime * 140.0) * 7.0 : 0.0;
        double shakeY = shakeTime > 0.0 ? Math.cos(shakeTime * 115.0) * 4.0 : 0.0;
        gc.save();
        gc.translate(shakeX, shakeY);
        drawBackground(gc);
        drawPlayfield(gc);
        drawItems(gc);
        drawCollectionEffects(gc);
        drawParticles(gc);
        drawBasket(gc);
        drawFloatingTexts(gc);
        drawHud(gc);
        if (phase == Phase.READY) drawCountdown(gc);
        if (phase == Phase.FINISHED) drawFinishCard(gc);
        if (flashTime > 0.0) {
            gc.setFill(Color.color(1.0, 0.22, 0.18, clamp(flashTime * 0.55, 0.0, 0.13)));
            gc.fillRect(-10, -10, canvasWidth + 20, canvasHeight + 20);
        }
        gc.restore();
    }

    private void drawBackground(GraphicsContext gc) {
        if (SPACE_BACKGROUND != null && SPACE_BACKGROUND.getWidth() > 0.0
                && SPACE_BACKGROUND.getHeight() > 0.0) {
            drawCoverImage(gc, SPACE_BACKGROUND, 0.0, 0.0, canvasWidth, canvasHeight);
            return;
        }
        for (InkLane lane : inkLanes) {
            gc.setFill(lane.baseColor);
            gc.fillRect(lane.x, 0, lane.width + 0.5, canvasHeight);

            gc.setFill(lane.flowingColor);
            if (lane.headY > 0) {
                gc.fillRect(lane.x, 0, lane.width + 0.5, Math.min(canvasHeight, lane.headY));
            }
            if (lane.headY + lane.width * 0.5 > 0 && lane.headY - lane.width * 0.5 < canvasHeight) {
                gc.fillOval(lane.x, lane.headY - lane.width * 0.5,
                        lane.width, lane.width);
            }
        }
    }

    private void drawPlayfield(GraphicsContext gc) {
        double left = playfieldLeft();
        double right = playfieldRight();
        double top = 108.0;
        double bottom = basketY + BASKET_HEIGHT + 24;
        double panelWidth = right - left;
        Color accent = feverTime > 0.0 ? Color.web("#ffd60a")
                : (combo >= 5 ? Color.web("#5eead4") : Color.web("#eef2ff"));

        // 中央舷窗天然构成操作区，只覆盖一层很轻的玻璃以保证 HUD 和物体可读。
        gc.setFill(Color.color(0.006, 0.014, 0.032, 0.16));
        gc.fillRoundRect(left, top, panelWidth, bottom - top, 30, 30);
        gc.setFill(Color.color(0.10, 0.18, 0.30, 0.08));
        gc.fillRoundRect(left + 2, top + 2, panelWidth - 4, 58, 28, 28);
        gc.setStroke(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.56));
        gc.setLineWidth(feverTime > 0.0 ? 2.2 : 1.4);
        gc.strokeRoundRect(left, top, panelWidth, bottom - top, 30, 30);

        gc.setStroke(Color.color(1, 1, 1, 0.075));
        for (int i = 1; i < 5; i++) {
            double y = top + (bottom - top) * i / 5.0;
            gc.strokeLine(left + 20, y, right - 20, y);
        }
        gc.setFill(Color.color(0.0, 0.0, 0.0, 0.26));
        gc.fillRoundRect(left + 12, bottom - 72, panelWidth - 24, 54, 18, 18);
    }

    private void drawItems(GraphicsContext gc) {
        for (FallingItem item : items) {
            gc.save();
            gc.translate(item.x, item.y);
            gc.rotate(item.rotation);
            drawCosmicItem(gc, item);
            gc.restore();
        }
    }

    private void spawnCollectionEffect(FallingItem item, boolean rare) {
        if (collectionEffects.size() >= MAX_COLLECTION_EFFECTS) {
            collectionEffects.remove(0);
        }
        double targetX = basketX + currentBasketWidth() * 0.5;
        collectionEffects.add(new CollectionEffect(
                item.x, item.y, targetX, basketY + 2.0,
                item.kind.main, rare ? 0.52 : 0.38, rare));
        collectorPulseTime = rare ? 0.46 : 0.30;
        collectorPulseDuration = collectorPulseTime;
        collectorPulseColor = item.kind.main;
    }

    private void updateCollectionEffects(double dt) {
        Iterator<CollectionEffect> iterator = collectionEffects.iterator();
        while (iterator.hasNext()) {
            CollectionEffect effect = iterator.next();
            effect.life -= dt;
            if (effect.life <= 0.0) {
                iterator.remove();
            }
        }
    }

    private void drawCosmicItem(GraphicsContext gc, FallingItem item) {
        double r = item.radius;
        Image sprite = item.kind.sprite;
        if (sprite == null || sprite.getWidth() <= 0.0) {
            if (item.kind == FruitKind.BOMB) {
                drawBomb(gc, r);
            } else {
                drawFruit(gc, item);
            }
            return;
        }

        double pulse = item.kind == FruitKind.GOLD
                ? 1.0 + Math.sin(elapsedTime * 8.0 + item.x) * 0.08 : 1.0;
        double sizeScale = item.kind == FruitKind.BOMB ? 3.0
                : (item.kind == FruitKind.GOLD ? 3.12 : 2.82);
        double ambientPulse = 1.0 + Math.sin(elapsedTime * 4.2 + item.x * 0.025) * 0.018;
        double size = r * sizeScale * pulse * ambientPulse;
        Color glow = item.kind == FruitKind.BOMB ? Color.web("#ff2338") : item.kind.main;
        double glowSize = size * (item.kind == FruitKind.GOLD ? 1.24 : 1.12);
        double glowAlpha = item.kind == FruitKind.BOMB ? 0.18
                : (item.kind == FruitKind.GOLD ? 0.22 : 0.10);

        // 下落能量尾迹与光环让静态贴图融入动态场景；只绘制轻量矢量层。
        gc.setFill(Color.color(glow.getRed(), glow.getGreen(), glow.getBlue(),
                item.kind == FruitKind.BOMB ? 0.06 : 0.085));
        gc.fillOval(-size * 0.28, -size * 0.88, size * 0.56, size * 1.10);
        gc.setFill(Color.color(glow.getRed(), glow.getGreen(), glow.getBlue(), glowAlpha));
        gc.fillOval(-glowSize * 0.5, -glowSize * 0.5, glowSize, glowSize);

        gc.save();
        gc.rotate(-item.rotation * 0.56 + elapsedTime * (item.kind == FruitKind.BOMB ? 18.0 : 34.0));
        gc.setStroke(Color.color(glow.getRed(), glow.getGreen(), glow.getBlue(),
                item.kind == FruitKind.BOMB ? 0.58 : 0.38));
        gc.setLineWidth(item.kind == FruitKind.GOLD ? 1.8 : 1.05);
        gc.setLineDashes(size * 0.16, size * 0.10);
        double orbitW = size * (item.kind == FruitKind.BOMB ? 1.02 : 1.14);
        double orbitH = size * (item.kind == FruitKind.BOMB ? 1.02 : 0.48);
        gc.strokeOval(-orbitW * 0.5, -orbitH * 0.5, orbitW, orbitH);
        gc.restore();

        gc.drawImage(sprite, -size * 0.5, -size * 0.5, size, size);

        if (item.kind != FruitKind.BOMB) {
            double glintAngle = elapsedTime * 3.6 + item.x * 0.01;
            double glintX = Math.cos(glintAngle) * size * 0.24;
            double glintY = Math.sin(glintAngle) * size * 0.18;
            gc.setFill(Color.color(1.0, 1.0, 1.0, item.kind == FruitKind.GOLD ? 0.72 : 0.42));
            gc.fillOval(glintX - 1.8, glintY - 1.8, 3.6, 3.6);
        }
    }

    private void drawCollectionEffects(GraphicsContext gc) {
        for (CollectionEffect effect : collectionEffects) {
            double progress = clamp(1.0 - effect.life / effect.maxLife, 0.0, 1.0);
            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
            double fade = Math.sin(progress * Math.PI);
            double controlY1 = effect.sourceY + Math.max(18.0, (effect.targetY - effect.sourceY) * 0.45);
            double controlY2 = effect.targetY - 46.0;
            double headX = cubicBezier(effect.sourceX, effect.sourceX,
                    effect.targetX, effect.targetX, eased);
            double headY = cubicBezier(effect.sourceY, controlY1,
                    controlY2, effect.targetY, eased);

            gc.save();
            gc.setGlobalAlpha(fade);
            gc.setStroke(Color.color(effect.color.getRed(), effect.color.getGreen(),
                    effect.color.getBlue(), effect.rare ? 0.54 : 0.36));
            gc.setLineWidth(effect.rare ? 8.0 : 5.0);
            gc.beginPath();
            gc.moveTo(effect.sourceX, effect.sourceY);
            gc.bezierCurveTo(effect.sourceX, controlY1,
                    effect.targetX, controlY2, effect.targetX, effect.targetY);
            gc.stroke();

            gc.setStroke(Color.color(0.88, 0.97, 1.0, effect.rare ? 0.92 : 0.66));
            gc.setLineWidth(effect.rare ? 2.2 : 1.4);
            gc.stroke();

            double coreRadius = (effect.rare ? 10.0 : 7.0) * (1.0 - eased * 0.72);
            gc.setFill(Color.color(1.0, 1.0, 1.0, 0.82));
            gc.fillOval(headX - coreRadius, headY - coreRadius,
                    coreRadius * 2.0, coreRadius * 2.0);

            for (int ring = 0; ring < 3; ring++) {
                double ringProgress = clamp(progress * 1.38 - ring * 0.16, 0.0, 1.0);
                double ringWidth = 18.0 + ringProgress * (effect.rare ? 92.0 : 66.0);
                double ringHeight = ringWidth * 0.24;
                gc.setStroke(Color.color(effect.color.getRed(), effect.color.getGreen(),
                        effect.color.getBlue(), (1.0 - ringProgress) * 0.72));
                gc.setLineWidth(effect.rare ? 2.4 : 1.5);
                gc.strokeOval(effect.targetX - ringWidth * 0.5,
                        effect.targetY - ringHeight * 0.5,
                        ringWidth, ringHeight);
            }
            gc.restore();
        }
    }

    private void drawFruit(GraphicsContext gc, FallingItem item) {
        double r = item.radius;
        if (item.kind == FruitKind.GOLD) {
            double pulse = 1.0 + Math.sin(elapsedTime * 8.0 + item.x) * 0.08;
            gc.setFill(Color.color(1.0, 0.84, 0.1, 0.16));
            gc.fillOval(-r * 1.55 * pulse, -r * 1.55 * pulse, r * 3.10 * pulse, r * 3.10 * pulse);
        }
        gc.setFill(Color.color(0, 0, 0, 0.24));
        gc.fillOval(-r - 3, -r - 1, r * 2 + 8, r * 2 + 9);
        gc.setFill(item.kind.shade);
        gc.fillOval(-r, -r, r * 2, r * 2);
        gc.setFill(item.kind.main);
        gc.fillOval(-r * 0.91, -r * 0.96, r * 1.70, r * 1.70);
        gc.setStroke(Color.color(0, 0, 0, 0.72));
        gc.setLineWidth(4.2);
        gc.strokeOval(-r, -r, r * 2, r * 2);
        gc.setStroke(Color.color(1, 1, 1, 0.72));
        gc.setLineWidth(1.25);
        gc.strokeOval(-r * 0.96, -r * 0.96, r * 1.92, r * 1.92);
        gc.setFill(Color.color(1, 1, 1, 0.34));
        gc.fillOval(-r * 0.54, -r * 0.58, r * 0.48, r * 0.35);
        gc.setStroke(Color.web("#4b2d18"));
        gc.setLineWidth(Math.max(2.0, r * 0.10));
        gc.strokeLine(0, -r * 0.88, r * 0.08, -r * 1.24);
        gc.setFill(Color.web("#52c96b"));
        gc.fillOval(r * 0.02, -r * 1.28, r * 0.58, r * 0.25);
        if (item.kind == FruitKind.GOLD) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(Font.font("System", FontWeight.BOLD, r * 0.75));
            gc.setFill(Color.web("#6f4b00"));
            gc.fillText("★", 0, r * 0.27);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    private void drawBomb(GraphicsContext gc, double r) {
        gc.setFill(Color.color(1.0, 0.27, 0.23, 0.25));
        gc.fillOval(-r * 1.55, -r * 1.55, r * 3.10, r * 3.10);
        gc.setFill(Color.color(0, 0, 0, 0.32));
        gc.fillOval(-r + 4, -r + 7, r * 2, r * 2);
        gc.setFill(FruitKind.BOMB.shade);
        gc.fillOval(-r, -r, r * 2, r * 2);
        gc.setFill(FruitKind.BOMB.main);
        gc.fillOval(-r * 0.84, -r * 0.90, r * 1.60, r * 1.60);
        gc.setStroke(Color.color(1.0, 0.35, 0.30, 0.90));
        gc.setLineWidth(2.3);
        gc.strokeOval(-r * 0.97, -r * 0.97, r * 1.94, r * 1.94);
        gc.setFill(Color.color(1, 1, 1, 0.18));
        gc.fillOval(-r * 0.48, -r * 0.52, r * 0.42, r * 0.30);
        gc.setStroke(Color.web("#a66b38"));
        gc.setLineWidth(3.0);
        gc.strokeLine(r * 0.22, -r * 0.78, r * 0.58, -r * 1.23);
        double spark = 4.0 + Math.sin(elapsedTime * 20.0) * 2.0;
        gc.setFill(Color.web("#ffb000"));
        gc.fillOval(r * 0.58 - spark, -r * 1.24 - spark, spark * 2, spark * 2);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("System", FontWeight.BOLD, r * 0.72));
        gc.setFill(Color.web("#ff786e"));
        gc.fillText("!", 0, r * 0.27);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawParticles(GraphicsContext gc) {
        for (Particle particle : particles) {
            double alpha = clamp(particle.life / particle.maxLife, 0.0, 1.0);
            gc.setFill(Color.color(particle.color.getRed(), particle.color.getGreen(),
                    particle.color.getBlue(), alpha * 0.86));
            gc.fillOval(particle.x - particle.size, particle.y - particle.size,
                    particle.size * 2, particle.size * 2);
        }
    }

    private void drawBasket(GraphicsContext gc) {
        double width = currentBasketWidth();
        double trackingPulse = handDetected ? 0.55 + Math.sin(elapsedTime * 7.0) * 0.15 : 0.18;
        Color trackingColor = feverTime > 0.0 ? Color.web("#ffd60a") : Color.web("#5eead4");
        gc.setFill(Color.color(trackingColor.getRed(), trackingColor.getGreen(),
                trackingColor.getBlue(), trackingPulse * 0.18));
        gc.fillRoundRect(basketX - 12, basketY - 16, width + 24, BASKET_HEIGHT + 34, 28, 28);

        double pulseProgress = collectorPulseTime <= 0.0 ? 0.0
                : clamp(collectorPulseTime / Math.max(0.001, collectorPulseDuration), 0.0, 1.0);
        if (pulseProgress > 0.0) {
            double pulseWidth = width + 56.0 + (1.0 - pulseProgress) * 36.0;
            double pulseHeight = 32.0 + (1.0 - pulseProgress) * 18.0;
            double centerX = basketX + width * 0.5;
            gc.setFill(Color.color(collectorPulseColor.getRed(), collectorPulseColor.getGreen(),
                    collectorPulseColor.getBlue(), pulseProgress * 0.24));
            gc.fillOval(centerX - pulseWidth * 0.5, basketY - pulseHeight * 0.62,
                    pulseWidth, pulseHeight);
            gc.setStroke(Color.color(collectorPulseColor.getRed(), collectorPulseColor.getGreen(),
                    collectorPulseColor.getBlue(), pulseProgress * 0.78));
            gc.setLineWidth(1.4 + pulseProgress * 1.8);
            gc.strokeOval(centerX - pulseWidth * 0.5, basketY - pulseHeight * 0.62,
                    pulseWidth, pulseHeight);
        }

        Image collector = feverTime > 0.0 ? COLLECTOR_FEVER_IMAGE : COLLECTOR_IMAGE;
        if (collector != null && collector.getWidth() > 0.0) {
            double visualWidth = width + 48.0;
            double visualHeight = visualWidth / 3.0;
            double visualX = basketX - (visualWidth - width) * 0.5;
            double visualY = basketY - visualHeight * 0.46;
            gc.drawImage(collector, visualX, visualY, visualWidth, visualHeight);
            drawCollectorParticles(gc, basketX + width * 0.5, basketY,
                    width, visualWidth, trackingColor, pulseProgress);
            if (pulseProgress > 0.0) {
                double apertureX = basketX + width * 0.5;
                gc.setFill(Color.color(1.0, 1.0, 1.0, pulseProgress * 0.58));
                gc.fillOval(apertureX - width * 0.18, basketY - 12.0,
                        width * 0.36, 8.0 + pulseProgress * 5.0);
            }
        } else {
            gc.setFill(Color.color(0, 0, 0, 0.34));
            gc.fillRoundRect(basketX + 5, basketY + 8, width, BASKET_HEIGHT, 12, 12);
            gc.setFill(BASKET_FILL);
            gc.fillRoundRect(basketX, basketY, width, BASKET_HEIGHT, 12, 12);
            gc.setStroke(handDetected ? trackingColor : Color.web("#777b86"));
            gc.setLineWidth(2.4);
            gc.strokeRoundRect(basketX, basketY, width, BASKET_HEIGHT, 12, 12);
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(HUD_LABEL_FONT);
        gc.setFill(handDetected ? Color.web("#d5fff7") : Color.web("#9497a0"));
        String controlText = handDetected ? "手掌追踪中" : (handLostTime > 0.8 ? "请将单手放入画面" : "等待手势");
        gc.fillText(controlText, basketX + width * 0.5, basketY + BASKET_HEIGHT + 27);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawCollectorParticles(GraphicsContext gc, double centerX, double apertureY,
                                        double collectorWidth, double visualWidth,
                                        Color trackingColor, double pulseProgress) {
        int particleCount = feverTime > 0.0 ? 22 : 14;
        double activity = handDetected ? 1.0 : 0.42;
        double speed = feverTime > 0.0 ? 1.72 : 1.08;
        Color particleColor = pulseProgress > 0.0 ? collectorPulseColor : trackingColor;

        gc.save();
        for (int i = 0; i < particleCount; i++) {
            double cycle = fractional(elapsedTime * speed + i / (double) particleCount);
            double inverse = 1.0 - cycle;
            double angle = i * 2.3999632297 + cycle * Math.PI * 4.6;
            double radiusX = 5.0 + inverse * collectorWidth * 0.48;
            double radiusY = 2.0 + inverse * 24.0;
            double px = centerX + Math.cos(angle) * radiusX;
            double py = apertureY - 8.0 - inverse * 22.0 + Math.sin(angle) * radiusY * 0.32;
            double alpha = Math.sin(cycle * Math.PI) * activity;
            double size = (feverTime > 0.0 ? 2.5 : 1.8) + cycle * 1.4;

            gc.setFill(Color.color(particleColor.getRed(), particleColor.getGreen(),
                    particleColor.getBlue(), alpha * 0.56));
            gc.fillOval(px - size * 1.8, py - size * 1.8, size * 3.6, size * 3.6);
            gc.setFill(Color.color(0.90, 0.98, 1.0, alpha * 0.82));
            gc.fillOval(px - size * 0.5, py - size * 0.5, size, size);
        }

        // 两侧推进器的短粒子尾焰与回收舱保持相同运动节奏。
        for (int side = -1; side <= 1; side += 2) {
            double thrusterX = centerX + side * visualWidth * 0.43;
            for (int i = 0; i < 4; i++) {
                double cycle = fractional(elapsedTime * (feverTime > 0.0 ? 2.8 : 1.9)
                        + i * 0.23 + (side > 0 ? 0.11 : 0.0));
                double px = thrusterX + Math.sin(i * 2.1 + elapsedTime * 5.0) * 2.4;
                double py = apertureY + 5.0 + cycle * 18.0;
                double alpha = (1.0 - cycle) * activity;
                double size = 2.8 - cycle * 1.5;
                gc.setFill(Color.color(trackingColor.getRed(), trackingColor.getGreen(),
                        trackingColor.getBlue(), alpha * 0.62));
                gc.fillOval(px - size, py - size, size * 2.0, size * 2.0);
            }
        }

        double scan = 0.5 + Math.sin(elapsedTime * (feverTime > 0.0 ? 9.0 : 5.5)) * 0.5;
        gc.setStroke(Color.color(particleColor.getRed(), particleColor.getGreen(),
                particleColor.getBlue(), (0.18 + scan * 0.34) * activity));
        gc.setLineWidth(1.0 + scan * 1.2);
        gc.strokeLine(centerX - collectorWidth * 0.20, apertureY - 7.0,
                centerX + collectorWidth * 0.20, apertureY - 7.0);
        gc.restore();
    }

    private void drawFloatingTexts(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.CENTER);
        for (FloatingText text : floatingTexts) {
            double alpha = clamp(text.life / text.maxLife, 0.0, 1.0);
            gc.setFill(Color.color(text.color.getRed(), text.color.getGreen(), text.color.getBlue(), alpha));
            gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, text.fontSize));
            gc.fillText(text.value, text.x, text.y);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawHud(GraphicsContext gc) {
        double margin = Math.max(26.0, canvasWidth * 0.035);
        drawGlassPanel(gc, margin, 22, 196, 69);
        gc.setFill(Color.web("#a8aab4"));
        gc.setFont(HUD_LABEL_FONT);
        gc.fillText("SCORE", margin + 20, 45);
        gc.setFill(Color.WHITE);
        gc.setFont(HUD_VALUE_FONT);
        gc.fillText(String.format("%04d", score), margin + 20, 75);

        double lifePanelX = canvasWidth - margin - 196;
        drawGlassPanel(gc, lifePanelX, 22, 196, 69);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFill(Color.web("#a8aab4"));
        gc.setFont(HUD_LABEL_FONT);
        gc.fillText("LIFE", lifePanelX + 176, 45);
        gc.setFont(HUD_VALUE_FONT);
        gc.setFill(Color.web("#ff6b61"));
        gc.fillText("♥ ".repeat(Math.max(0, lives)), lifePanelX + 176, 75);
        gc.setTextAlign(TextAlignment.LEFT);

        double progressWidth = clamp(canvasWidth * 0.34, 260.0, 500.0);
        double progressX = canvasWidth * 0.5 - progressWidth * 0.5;
        gc.setFill(Color.color(1, 1, 1, 0.075));
        gc.fillRoundRect(progressX, 36, progressWidth, 10, 10, 10);
        double progress = targetScore > 0 ? clamp(score / (double) targetScore, 0.0, 1.0)
                : (score % 100) / 100.0;
        gc.setFill(feverTime > 0.0 ? Color.web("#ffd60a") : Color.web("#5eead4"));
        gc.fillRoundRect(progressX, 36, progressWidth * progress, 10, 10, 10);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#d8dae2"));
        gc.setFont(HUD_LABEL_FONT);
        gc.fillText(targetScore > 0 ? "目标  " + targetScore + "   ·   LEVEL " + level
                : "无尽模式   ·   LEVEL " + level, canvasWidth * 0.5, 66);

        double feverY = 79;
        gc.setFill(Color.color(1, 1, 1, 0.055));
        gc.fillRoundRect(progressX + 50, feverY, progressWidth - 100, 5, 5, 5);
        double feverProgress = feverTime > 0.0 ? feverTime / FEVER_SECONDS : feverCharge;
        gc.setFill(feverTime > 0.0 ? Color.web("#ffcf32") : Color.web("#af52de"));
        gc.fillRoundRect(progressX + 50, feverY, (progressWidth - 100) * feverProgress, 5, 5, 5);
        gc.setFill(Color.web("#9699a5"));
        gc.setFont(Font.font("Microsoft YaHei UI", 10));
        gc.fillText(feverTime > 0.0 ? "FEVER ×2" : "连击充能", canvasWidth * 0.5, 96);

        if (combo >= 2 && phase == Phase.PLAYING) {
            gc.setFill(combo >= 10 ? Color.web("#ffd60a") : Color.WHITE);
            gc.setFont(COMBO_FONT);
            gc.fillText(combo + " COMBO", canvasWidth * 0.5, 140);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawGlassPanel(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setFill(Color.color(0.03, 0.035, 0.055, 0.76));
        gc.fillRoundRect(x, y, w, h, 20, 20);
        gc.setStroke(Color.color(1, 1, 1, 0.12));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x, y, w, h, 20, 20);
    }

    private void drawCountdown(GraphicsContext gc) {
        double remaining = READY_SECONDS - phaseTime;
        String text = remaining > 1.55 ? "3" : remaining > 0.80 ? "2" : remaining > 0.12 ? "1" : "GO";
        double progress = 1.0 - clamp((remaining % 0.78) / 0.78, 0.0, 1.0);
        double scale = 0.92 + Math.sin(progress * Math.PI) * 0.12;
        gc.setFill(Color.color(0, 0, 0, 0.42));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD,
                ("GO".equals(text) ? 70 : 88) * scale));
        gc.setFill(Color.color(0, 0, 0, 0.35));
        gc.fillText(text, canvasWidth * 0.5 + 5, canvasHeight * 0.45 + 7);
        gc.setFill(Color.WHITE);
        gc.fillText(text, canvasWidth * 0.5, canvasHeight * 0.45);
        gc.setFont(TITLE_FONT);
        gc.setFill(Color.web("#d0d2dc"));
        gc.fillText("移动手掌，让引力舱回收星核", canvasWidth * 0.5, canvasHeight * 0.45 + 60);
        gc.setFont(HUD_LABEL_FONT);
        gc.setFill(Color.web("#ff9892"));
        gc.fillText("避开反物质地雷  ·  能量逸失会损失生命", canvasWidth * 0.5, canvasHeight * 0.45 + 88);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawFinishCard(GraphicsContext gc) {
        gc.setFill(Color.color(0, 0, 0, 0.70));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        double w = Math.min(520.0, canvasWidth - 70.0);
        double h = 260.0;
        double x = canvasWidth * 0.5 - w * 0.5;
        double y = canvasHeight * 0.5 - h * 0.5;
        drawGlassPanel(gc, x, y, w, h);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 34));
        gc.setFill(won ? Color.web("#ffd60a") : Color.web("#ff6b61"));
        gc.fillText(won ? "目标达成" : "本局结束", canvasWidth * 0.5, y + 62);
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 46));
        gc.setFill(Color.WHITE);
        gc.fillText(String.valueOf(score), canvasWidth * 0.5, y + 120);
        gc.setFont(HUD_LABEL_FONT);
        gc.setFill(Color.web("#b5b7c2"));
        gc.fillText("回收 " + caughtCount + "   ·   逸失 " + missedCount
                + "   ·   最高连击 " + bestCombo, canvasWidth * 0.5, y + 158);
        gc.setFill(Color.web("#d8dae2"));
        gc.setFont(Font.font("Microsoft YaHei UI", 14));
        gc.fillText("握拳重玩   ·   返回手势退出", canvasWidth * 0.5, y + 211);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    @Override
    public boolean isOver() {
        return phase == Phase.FINISHED;
    }

    @Override
    public int getScore() {
        return score;
    }

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }

    private static Image loadImage(String resourcePath) {
        var resource = CatchFruit.class.getResource(resourcePath);
        if (resource == null) {
            return null;
        }
        try {
            Image image = new Image(resource.toExternalForm(), false);
            return image.isError() ? null : image;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void drawCoverImage(GraphicsContext gc, Image image,
                                       double x, double y, double width, double height) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double targetRatio = width / Math.max(1.0, height);
        double imageRatio = imageWidth / Math.max(1.0, imageHeight);
        double sourceX = 0.0;
        double sourceY = 0.0;
        double sourceWidth = imageWidth;
        double sourceHeight = imageHeight;
        if (imageRatio > targetRatio) {
            sourceWidth = imageHeight * targetRatio;
            sourceX = (imageWidth - sourceWidth) * 0.5;
        } else if (imageRatio < targetRatio) {
            sourceHeight = imageWidth / targetRatio;
            sourceY = (imageHeight - sourceHeight) * 0.5;
        }
        gc.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight,
                x, y, width, height);
    }

    private static double random(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    private static Color randomInkColor(Color excluded) {
        Color selected;
        do {
            selected = INK_COLORS[RANDOM.nextInt(INK_COLORS.length)];
        } while (INK_COLORS.length > 1 && selected.equals(excluded));
        return selected;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
    }

    private static double cubicBezier(double start, double controlOne,
                                      double controlTwo, double end, double progress) {
        double inverse = 1.0 - progress;
        return inverse * inverse * inverse * start
                + 3.0 * inverse * inverse * progress * controlOne
                + 3.0 * inverse * progress * progress * controlTwo
                + progress * progress * progress * end;
    }

    private static final class CollectionEffect {
        final double sourceX;
        final double sourceY;
        final double targetX;
        final double targetY;
        final Color color;
        final double maxLife;
        final boolean rare;
        double life;

        CollectionEffect(double sourceX, double sourceY, double targetX, double targetY,
                         Color color, double life, boolean rare) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.rare = rare;
        }
    }

    private static final class FallingItem {
        double x;
        double y;
        double previousY;
        double vx;
        double vy;
        double radius;
        double rotation;
        double rotationSpeed;
        final FruitKind kind;

        FallingItem(double x, double y, double vx, double vy, double radius,
                    double rotationSpeed, FruitKind kind) {
            this.x = x;
            this.y = y;
            this.previousY = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.rotationSpeed = rotationSpeed;
            this.kind = kind;
        }
    }

    private static final class Particle {
        double x;
        double y;
        double vx;
        double vy;
        double size;
        double life;
        final double maxLife;
        final Color color;

        Particle(double x, double y, double vx, double vy, double size, double life, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.life = life;
            this.maxLife = life;
            this.color = color;
        }
    }

    private static final class FloatingText {
        final String value;
        final double x;
        double y;
        double life;
        final double maxLife;
        final Color color;
        final double fontSize;

        FloatingText(double x, double y, String value, Color color, double life, double fontSize) {
            this.x = x;
            this.y = y;
            this.value = value;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.fontSize = fontSize;
        }
    }

    private static final class InkLane {
        final double x;
        final double width;
        double headY;
        double speed;
        Color baseColor;
        Color flowingColor;

        InkLane(double x, double width, double headY, double speed,
                Color baseColor, Color flowingColor) {
            this.x = x;
            this.width = width;
            this.headY = headY;
            this.speed = speed;
            this.baseColor = baseColor;
            this.flowingColor = flowingColor;
        }
    }
}
