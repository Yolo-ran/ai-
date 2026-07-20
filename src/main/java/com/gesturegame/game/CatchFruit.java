package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
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
 * 体感接水果：移动手掌控制篮子，完成目标分并避开炸弹。
 *
 * <p>背景复刻 AnimatedDots 的核心观感：固定横向点阵、随机速度、循环下落，
 * 同时根据纵向位置改变选定颜色通道。游戏逻辑完全留在 Java 端，不改变手势协议。</p>
 */
public class CatchFruit implements GameInterface {

    private static final Random RANDOM = new Random();
    private static final int INK_LANE_COUNT = 60;
    private static final int MAX_PARTICLES = 220;
    private static final double READY_SECONDS = 2.35;
    private static final double COMBO_WINDOW = 1.25;
    private static final double FEVER_SECONDS = 5.5;
    private static final double BASKET_HEIGHT = 30.0;

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
        APPLE(Color.web("#ff453a"), Color.web("#a91d2d"), 10),
        ORANGE(Color.web("#ff9f0a"), Color.web("#c55a11"), 10),
        LEMON(Color.web("#ffd60a"), Color.web("#d19a00"), 10),
        LIME(Color.web("#a4ff2e"), Color.web("#3f9d4a"), 10),
        BERRY(Color.web("#af52de"), Color.web("#6731a8"), 15),
        GOLD(Color.web("#ffd700"), Color.web("#fff0a3"), 30),
        BOMB(Color.web("#20232d"), Color.web("#050609"), 0);

        final Color main;
        final Color shade;
        final int points;

        FruitKind(Color main, Color shade, int points) {
            this.main = main;
            this.shade = shade;
            this.points = points;
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
    private double handLostTime;
    private long lastUpdateNanos;

    private final List<FallingItem> items = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<InkLane> inkLanes = new ArrayList<>();

    @Override
    public String getName() {
        return "暗星采矿";
    }

    @Override
    public String getDescription() {
        return "移动手掌控制篮子，连击接取水果并避开炸弹";
    }

    @Override
    public String getIcon() {
        return "🍎";
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
        handLostTime = 0.0;
        lastUpdateNanos = 0L;
        items.clear();
        particles.clear();
        floatingTexts.clear();
        applyDifficulty();
        basketX = canvasWidth * 0.5 - basketBaseWidth * 0.5;
        basketTargetX = basketX;
        basketY = canvasHeight - Math.max(76.0, canvasHeight * 0.105);
        createInkLanes();
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
        updateInkLanes(dt);
        updateParticles(dt);
        updateFloatingTexts(dt);
        updateBasket(gesture, dt);

        if (phase == Phase.READY) {
            if (phaseTime >= READY_SECONDS) {
                phase = Phase.PLAYING;
                phaseTime = 0.0;
                spawnTimer = 0.15;
                floatingTexts.add(new FloatingText(canvasWidth * 0.5, canvasHeight * 0.39,
                        "开始接取!", Color.web("#ffffff"), 1.0, 30));
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
                    "彩虹狂热!  得分 ×2", Color.web("#fff5b8"), 1.15, 28));
        }
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
        spawnBurst(item.x, basketY, Color.web("#ff453a"), 26);
        floatingTexts.add(new FloatingText(item.x, basketY - 28,
                "炸弹!  -1 生命", Color.web("#ff6b61"), 0.95, 21));
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
                        "漏接  -1 生命", Color.web("#ff9f8f"), 0.85, 17));
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

        // 赛道把高饱和背景压成可读的深色玻璃，而非覆盖整张油墨背景。
        gc.setFill(Color.color(0.012, 0.018, 0.040, 0.50));
        gc.fillRoundRect(left, top, panelWidth, bottom - top, 30, 30);
        gc.setFill(Color.color(0.10, 0.14, 0.24, 0.14));
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
            if (item.kind == FruitKind.BOMB) {
                drawBomb(gc, item.radius);
            } else {
                drawFruit(gc, item);
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

        gc.setFill(Color.color(0, 0, 0, 0.34));
        gc.fillRoundRect(basketX + 5, basketY + 8, width, BASKET_HEIGHT, 12, 12);
        gc.setFill(BASKET_FILL);
        gc.fillRoundRect(basketX, basketY, width, BASKET_HEIGHT, 12, 12);
        gc.setStroke(handDetected ? trackingColor : Color.web("#777b86"));
        gc.setLineWidth(2.4);
        gc.strokeRoundRect(basketX, basketY, width, BASKET_HEIGHT, 12, 12);

        gc.setStroke(Color.color(0.30, 0.16, 0.06, 0.45));
        gc.setLineWidth(1.1);
        for (double x = basketX + 13; x < basketX + width - 7; x += 16) {
            gc.strokeLine(x, basketY + 3, x + 6, basketY + BASKET_HEIGHT - 3);
        }
        gc.strokeLine(basketX + 6, basketY + BASKET_HEIGHT * 0.52,
                basketX + width - 6, basketY + BASKET_HEIGHT * 0.52);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(HUD_LABEL_FONT);
        gc.setFill(handDetected ? Color.web("#d5fff7") : Color.web("#9497a0"));
        String controlText = handDetected ? "手掌追踪中" : (handLostTime > 0.8 ? "请将单手放入画面" : "等待手势");
        gc.fillText(controlText, basketX + width * 0.5, basketY + BASKET_HEIGHT + 23);
        gc.setTextAlign(TextAlignment.LEFT);
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
        gc.fillText("移动手掌，让篮子接住水果", canvasWidth * 0.5, canvasHeight * 0.45 + 60);
        gc.setFont(HUD_LABEL_FONT);
        gc.setFill(Color.web("#ff9892"));
        gc.fillText("避开炸弹  ·  漏接水果会损失生命", canvasWidth * 0.5, canvasHeight * 0.45 + 88);
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
        gc.fillText("接取 " + caughtCount + "   ·   漏接 " + missedCount
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
