package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 体感版水果忍者。
 *
 * <p>单手负责瞄准和挥砍：慢速移动只显示刀尖，超过速度门槛才形成有效刀刃。
 * 双手输入会在 GameRenderer 中被系统退出逻辑截获，本游戏不会消费双手数据。</p>
 */
public class FruitNinja implements GameInterface {

    private static final Random RANDOM = new Random();
    private static final double COUNTDOWN_SECONDS = 2.4;
    private static final double ENTRY_GUARD_SECONDS = 0.65;
    private static final double COMBO_WINDOW_SECONDS = 1.15;
    private static final int MAX_PARTICLES = 180;
    private static final LinearGradient BACKGROUND = new LinearGradient(0, 0, 0, 1, true,
            CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#081923")),
            new Stop(0.58, Color.web("#102a2b")),
            new Stop(1, Color.web("#07110e")));
    private static final Font HUD_SCORE_FONT = Font.font("System", FontWeight.BOLD, 20);
    private static final Font HUD_DETAIL_FONT = Font.font("System", FontWeight.NORMAL, 15);
    private static final Font COMBO_FONT = Font.font("System", FontWeight.BOLD, 30);
    private static final Font BIG_COMBO_FONT = Font.font("System", FontWeight.BOLD, 38);
    private static final Font COUNTDOWN_FONT = Font.font("System", FontWeight.BOLD, 92);
    private static final Font EFFECT_SMALL_FONT = Font.font("System", FontWeight.BOLD, 23);
    private static final Font EFFECT_MEDIUM_FONT = Font.font("System", FontWeight.BOLD, 28);
    private static final Font EFFECT_LARGE_FONT = Font.font("System", FontWeight.BOLD, 34);
    private static final Color FRUIT_SHADOW = Color.color(0, 0, 0, 0.25);
    private static final Color FRUIT_HIGHLIGHT = Color.color(1, 1, 1, 0.28);
    private static final Color BOMB_SHADOW = Color.color(0, 0, 0, 0.30);
    private static final Color BOMB_HIGHLIGHT = Color.color(1, 1, 1, 0.18);

    private enum Phase { COUNTDOWN, PLAYING, FINISHED }

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int targetScore;
    private int missedFruitCount;
    private int maxCombo;
    private int slicedCount;
    private int swingCount;
    private int successfulSwings;
    private int combo;
    private double comboTimer;
    private double phaseTime;
    private double waveTimer;
    private double screenFlash;
    private double shakeTime;
    private long lastUpdateNanos;
    private Phase phase = Phase.COUNTDOWN;
    private Difficulty difficulty = Difficulty.NORMAL;

    private final List<Fruit> fruits = new ArrayList<>();
    private final List<FruitHalf> halves = new ArrayList<>();
    private final List<JuiceParticle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final BladeTracker blade = new BladeTracker();

    private int minWaveCount;
    private int maxWaveCount;
    private double minWaveDelay;
    private double maxWaveDelay;
    private double bombChance;
    private double sliceSpeedThreshold;

    @Override
    public String getName() {
        return "切水果";
    }

    @Override
    public String getDescription() {
        return "快速挥手切开水果，慢速移动可以安全瞄准";
    }

    @Override
    public String getIcon() {
        return "🔪";
    }

    @Override
    public void init(int width, int height) {
        canvasWidth = Math.max(1, width);
        canvasHeight = Math.max(1, height);
        score = 0;
        targetScore = 500;
        missedFruitCount = 0;
        maxCombo = 0;
        slicedCount = 0;
        swingCount = 0;
        successfulSwings = 0;
        combo = 0;
        comboTimer = 0.0;
        phaseTime = 0.0;
        waveTimer = 0.0;
        screenFlash = 0.0;
        shakeTime = 0.0;
        lastUpdateNanos = 0L;
        phase = Phase.COUNTDOWN;
        fruits.clear();
        halves.clear();
        particles.clear();
        floatingTexts.clear();
        shockwaves.clear();
        blade.reset();
        applyDifficulty();
    }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY -> {
                targetScore = 300;
                minWaveCount = 2;
                maxWaveCount = 4;
                minWaveDelay = 1.35;
                maxWaveDelay = 1.75;
                bombChance = 0.04;
                sliceSpeedThreshold = 0.32;
            }
            case NORMAL -> {
                targetScore = 500;
                minWaveCount = 3;
                maxWaveCount = 5;
                minWaveDelay = 1.0;
                maxWaveDelay = 1.4;
                bombChance = 0.12;
                sliceSpeedThreshold = 0.42;
            }
            case HARD -> {
                targetScore = 800;
                minWaveCount = 4;
                maxWaveCount = 7;
                minWaveDelay = 0.72;
                maxWaveDelay = 1.08;
                bombChance = 0.20;
                sliceSpeedThreshold = 0.50;
            }
            case ENDLESS -> {
                targetScore = 1200;
                minWaveCount = 3;
                maxWaveCount = 6;
                minWaveDelay = 0.9;
                maxWaveDelay = 1.25;
                bombChance = 0.14;
                sliceSpeedThreshold = 0.40;
            }
        }
    }

    @Override
    public void update(GestureData gesture) {
        double dt = frameDeltaSeconds();
        phaseTime += dt;
        screenFlash = Math.max(0.0, screenFlash - dt * 2.8);
        shakeTime = Math.max(0.0, shakeTime - dt);

        boolean cuttingEnabled = phase == Phase.PLAYING && phaseTime >= ENTRY_GUARD_SECONDS;
        blade.update(gesture, dt, canvasWidth, canvasHeight, sliceSpeedThreshold, cuttingEnabled);
        if (blade.consumeSwingStarted()) {
            swingCount++;
        }

        updateEffects(dt);

        if (phase == Phase.COUNTDOWN) {
            if (phaseTime >= COUNTDOWN_SECONDS) {
                phase = Phase.PLAYING;
                phaseTime = 0.0;
                waveTimer = 0.15;
                floatingTexts.add(new FloatingText(canvasWidth / 2.0, canvasHeight * 0.42,
                        "开始挥砍!", Color.web("#fef08a"), 0.9, 34));
            }
            return;
        }

        if (phase == Phase.FINISHED) {
            return;
        }

        comboTimer -= dt;
        if (comboTimer <= 0.0) {
            combo = 0;
        }

        waveTimer -= dt;
        if (waveTimer <= 0.0) {
            spawnWave();
            waveTimer = random(minWaveDelay, maxWaveDelay);
        }

        updateWorld(dt);
        resolveBladeHits();
        removeMissedObjects();

        if (score >= targetScore) {
            score = targetScore;
            finishGame();
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

    private void updateWorld(double dt) {
        for (Fruit fruit : fruits) {
            fruit.update(dt);
        }
        for (FruitHalf half : halves) {
            half.update(dt);
        }
        halves.removeIf(half -> half.y - half.radius > canvasHeight + 100
                || half.x < -140 || half.x > canvasWidth + 140);
    }

    private void resolveBladeHits() {
        BladeSegment segment = blade.currentCutSegment();
        if (segment == null) {
            return;
        }

        int fruitHits = 0;
        boolean hitAnything = false;
        for (Fruit fruit : fruits) {
            if (fruit.hit || !segment.intersects(fruit.x, fruit.y,
                    fruit.bomb ? fruit.radius * 0.72 : fruit.radius * 0.90)) {
                continue;
            }
            hitAnything = true;
            fruit.hit = true;
            if (fruit.bomb) {
                hitBomb(fruit);
            } else {
                fruitHits++;
                sliceFruit(fruit, segment);
            }
        }

        if (hitAnything && blade.markCurrentSwingSuccessful()) {
            successfulSwings++;
        }

        if (fruitHits >= 2) {
            int multiBonus = fruitHits * 15;
            score += multiBonus;
            String label = fruitHits >= 4 ? "疯狂连切 ×" + fruitHits : fruitHits + " 连切";
            floatingTexts.add(new FloatingText(segment.endX, segment.endY - 28,
                    label + "  +" + multiBonus, Color.web("#fde047"), 0.9, 28));
            shockwaves.add(new Shockwave(segment.endX, segment.endY, Color.web("#fde047")));
        }
    }

    private void sliceFruit(Fruit fruit, BladeSegment segment) {
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        comboTimer = COMBO_WINDOW_SECONDS;
        slicedCount++;

        int multiplier = 1 + Math.min(5, combo / 3);
        int gained = 10 * multiplier;
        score += gained;

        double bladeDx = segment.endX - segment.startX;
        double bladeDy = segment.endY - segment.startY;
        double bladeLength = Math.max(1.0, Math.hypot(bladeDx, bladeDy));
        double normalX = -bladeDy / bladeLength;
        double normalY = bladeDx / bladeLength;
        double splitForce = 115.0 + Math.min(160.0, bladeLength * 4.0);

        halves.add(new FruitHalf(fruit, -1,
                fruit.vx - normalX * splitForce,
                fruit.vy - normalY * splitForce - 30));
        halves.add(new FruitHalf(fruit, 1,
                fruit.vx + normalX * splitForce,
                fruit.vy + normalY * splitForce - 30));

        addJuice(fruit, segment, 10 + RANDOM.nextInt(5));
        floatingTexts.add(new FloatingText(fruit.x, fruit.y - fruit.radius,
                "+" + gained, fruit.kind.flesh, 0.65, combo >= 6 ? 30 : 23));
        shockwaves.add(new Shockwave(fruit.x, fruit.y, fruit.kind.flesh));
    }

    private void hitBomb(Fruit bomb) {
        combo = 0;
        comboTimer = 0.0;
        screenFlash = 0.72;
        shakeTime = 0.38;
        shockwaves.add(new Shockwave(bomb.x, bomb.y, Color.web("#fb7185")));
        addExplosion(bomb.x, bomb.y);

        int penalty = difficulty == Difficulty.EASY ? 30
                : (difficulty == Difficulty.HARD ? 75 : 50);
        score = Math.max(0, score - penalty);
        floatingTexts.add(new FloatingText(bomb.x, bomb.y - 45,
                "炸弹 -" + penalty, Color.web("#fb7185"), 1.0, 31));
    }

    private void updateEffects(double dt) {
        for (JuiceParticle particle : particles) {
            particle.update(dt);
        }
        particles.removeIf(particle -> particle.life <= 0.0);

        for (FloatingText text : floatingTexts) {
            text.update(dt);
        }
        floatingTexts.removeIf(text -> text.life <= 0.0);

        for (Shockwave wave : shockwaves) {
            wave.update(dt);
        }
        shockwaves.removeIf(wave -> wave.life <= 0.0);
    }

    private void removeMissedObjects() {
        Iterator<Fruit> iterator = fruits.iterator();
        while (iterator.hasNext()) {
            Fruit fruit = iterator.next();
            if (fruit.hit) {
                iterator.remove();
                continue;
            }
            if (fruit.y - fruit.radius > canvasHeight + 45) {
                iterator.remove();
                if (!fruit.bomb) {
                    missedFruitCount++;
                    combo = 0;
                    comboTimer = 0.0;
                    floatingTexts.add(new FloatingText(
                            clamp(fruit.x, 80, canvasWidth - 80), canvasHeight - 70,
                            "漏掉了!", Color.web("#fda4af"), 0.75, 23));
                }
            } else if (fruit.x < -150 || fruit.x > canvasWidth + 150) {
                iterator.remove();
            }
        }
    }

    private void spawnWave() {
        int count = minWaveCount + RANDOM.nextInt(maxWaveCount - minWaveCount + 1);
        double center = random(canvasWidth * 0.18, canvasWidth * 0.82);
        for (int i = 0; i < count; i++) {
            double spread = (i - (count - 1) / 2.0) * random(48.0, 76.0);
            fruits.add(createFruit(clamp(center + spread, 55, canvasWidth - 55), false));
        }
        if (RANDOM.nextDouble() < bombChance) {
            double bombX = clamp(center + random(-180, 180), 55, canvasWidth - 55);
            fruits.add(createFruit(bombX, true));
        }
    }

    private Fruit createFruit(double x, boolean bomb) {
        double scale = Math.max(0.75, Math.min(canvasWidth / 1280.0, canvasHeight / 720.0));
        double radius = random(29, 42) * scale;
        double horizontalBias = (canvasWidth / 2.0 - x) * 0.14;
        double vx = horizontalBias + random(-175, 175) * scale;
        double vy = -random(820, 1050) * scale;
        double gravity = 760 * scale;
        FruitKind kind = bomb ? FruitKind.BOMB
                : FruitKind.values()[RANDOM.nextInt(FruitKind.BOMB.ordinal())];
        return new Fruit(x, canvasHeight + radius + 8, vx, vy, gravity, radius,
                random(0, 360), random(-210, 210), kind, bomb);
    }

    private void addJuice(Fruit fruit, BladeSegment segment, int count) {
        double dx = segment.endX - segment.startX;
        double dy = segment.endY - segment.startY;
        double length = Math.max(1.0, Math.hypot(dx, dy));
        for (int i = 0; i < count && particles.size() < MAX_PARTICLES; i++) {
            double along = random(-120, 120);
            double across = random(-170, 170);
            double vx = dx / length * along - dy / length * across;
            double vy = dy / length * along + dx / length * across - random(30, 130);
            particles.add(new JuiceParticle(fruit.x, fruit.y, vx, vy,
                    random(0.35, 0.75), random(2.5, 6.5), fruit.kind.flesh));
        }
    }

    private void addExplosion(double x, double y) {
        for (int i = 0; i < 20 && particles.size() < MAX_PARTICLES; i++) {
            double angle = random(0, Math.PI * 2);
            double speed = random(90, 360);
            Color color = i % 3 == 0 ? Color.web("#facc15")
                    : (i % 2 == 0 ? Color.web("#fb7185") : Color.web("#64748b"));
            particles.add(new JuiceParticle(x, y, Math.cos(angle) * speed,
                    Math.sin(angle) * speed, random(0.45, 0.9), random(3, 8), color));
        }
    }

    private void finishGame() {
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.FINISHED;
        phaseTime = 0.0;
        blade.disableCutting();
    }

    @Override
    public void render(GraphicsContext gc) {
        drawBackground(gc);

        gc.save();
        if (shakeTime > 0.0) {
            double amount = 8.0 * Math.min(1.0, shakeTime / 0.38);
            gc.translate(random(-amount, amount), random(-amount, amount));
        }
        drawWorld(gc);
        gc.restore();

        blade.render(gc);
        drawHud(gc);

        if (phase == Phase.COUNTDOWN) {
            drawCountdown(gc);
        } else if (phase == Phase.FINISHED) {
            drawSummary(gc);
        } else if (phaseTime < ENTRY_GUARD_SECONDS) {
            drawCenteredText(gc, "准备挥砍", canvasHeight * 0.43,
                    32, Color.web("#fef3c7"));
        }

        if (screenFlash > 0.0) {
            gc.setFill(Color.color(1.0, 0.15, 0.12, screenFlash * 0.32));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);
        }
    }

    private void drawBackground(GraphicsContext gc) {
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        gc.setStroke(Color.color(0.35, 0.8, 0.55, 0.08));
        gc.setLineWidth(2);
        double gap = Math.max(70, canvasWidth / 15.0);
        for (double x = -canvasHeight; x < canvasWidth + canvasHeight; x += gap) {
            gc.strokeLine(x, canvasHeight, x + canvasHeight * 0.45, 0);
        }
        gc.setFill(Color.color(0.02, 0.07, 0.05, 0.55));
        gc.fillRect(0, canvasHeight * 0.88, canvasWidth, canvasHeight * 0.12);
    }

    private void drawWorld(GraphicsContext gc) {
        for (Shockwave wave : shockwaves) {
            wave.render(gc);
        }
        for (Fruit fruit : fruits) {
            fruit.render(gc);
        }
        for (FruitHalf half : halves) {
            half.render(gc);
        }
        for (JuiceParticle particle : particles) {
            particle.render(gc);
        }
        for (FloatingText text : floatingTexts) {
            text.render(gc);
        }
    }

    private void drawHud(GraphicsContext gc) {
        gc.setFill(Color.color(0.02, 0.06, 0.08, 0.72));
        gc.fillRoundRect(22, 20, 235, 76, 20, 20);
        gc.setStroke(Color.color(0.55, 0.95, 0.75, 0.28));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(22, 20, 235, 76, 20, 20);

        gc.setFill(Color.web("#d1fae5"));
        gc.setFont(HUD_SCORE_FONT);
        gc.fillText("得分  " + score + " / " + targetScore, 42, 49);
        gc.setFont(HUD_DETAIL_FONT);
        gc.setFill(Color.web("#94a3b8"));
        gc.fillText("已切 " + slicedCount + "   漏掉 " + missedFruitCount, 42, 78);

        double barX = canvasWidth - 310;
        double barY = 30;
        double barWidth = 270;
        double progress = clamp(score / (double) targetScore, 0, 1);
        gc.setFill(Color.color(0.02, 0.06, 0.08, 0.72));
        gc.fillRoundRect(barX, barY, barWidth, 28, 14, 14);
        gc.setFill(Color.web("#22d3ee"));
        gc.fillRoundRect(barX + 3, barY + 3, (barWidth - 6) * progress, 22, 11, 11);
        gc.setStroke(Color.color(0.65, 0.95, 1.0, 0.42));
        gc.strokeRoundRect(barX, barY, barWidth, 28, 14, 14);

        if (combo >= 2 && phase == Phase.PLAYING) {
            double pulse = 1.0 + 0.05 * Math.sin(System.nanoTime() / 90_000_000.0);
            gc.save();
            gc.translate(canvasWidth / 2.0, 66);
            gc.scale(pulse, pulse);
            gc.setFill(Color.web("#fde047"));
            gc.setFont(combo >= 8 ? BIG_COMBO_FONT : COMBO_FONT);
            String text = "COMBO ×" + combo;
            gc.fillText(text, -estimateTextWidth(text, combo >= 8 ? 38 : 30) / 2.0, 0);
            gc.restore();
        }

        if (!blade.isHandVisible() && phase == Phase.PLAYING) {
            drawCenteredText(gc, "请将一只手伸入画面", canvasHeight * 0.54,
                    24, Color.color(1, 1, 1, 0.68));
        }
    }

    private void drawCountdown(GraphicsContext gc) {
        double remaining = Math.max(0.0, COUNTDOWN_SECONDS - phaseTime);
        int number = Math.max(1, (int) Math.ceil(remaining / 0.8));
        double local = (phaseTime % 0.8) / 0.8;
        double scale = 1.25 - local * 0.25;
        gc.save();
        gc.translate(canvasWidth / 2.0, canvasHeight * 0.44);
        gc.scale(scale, scale);
        gc.setFill(Color.color(0, 0, 0, 0.35));
        gc.fillOval(-76, -96, 152, 152);
        gc.setFill(Color.web("#fef08a"));
        gc.setFont(COUNTDOWN_FONT);
        gc.fillText(String.valueOf(number), -27, 18);
        gc.restore();
        drawCenteredText(gc, "快速挥手才能切开 · 慢速移动只瞄准",
                canvasHeight * 0.67, 22, Color.color(1, 1, 1, 0.72));
    }

    private void drawSummary(GraphicsContext gc) {
        gc.setFill(Color.color(0.015, 0.04, 0.05, 0.84));
        gc.fillRoundRect(canvasWidth / 2.0 - 275, canvasHeight / 2.0 - 190,
                550, 340, 30, 30);
        gc.setStroke(Color.color(0.65, 1.0, 0.78, 0.38));
        gc.setLineWidth(2);
        gc.strokeRoundRect(canvasWidth / 2.0 - 275, canvasHeight / 2.0 - 190,
                550, 340, 30, 30);

        drawCenteredText(gc, "挑战完成", canvasHeight / 2.0 - 125,
                38, Color.web("#fef08a"));
        drawCenteredText(gc, score + " / " + targetScore, canvasHeight / 2.0 - 55,
                58, Color.WHITE);
        drawCenteredText(gc, "切开水果  " + slicedCount + "     最高连击  " + maxCombo,
                canvasHeight / 2.0 + 10, 21, Color.web("#d1fae5"));
        int accuracy = swingCount == 0 ? 0 : (int) Math.round(successfulSwings * 100.0 / swingCount);
        drawCenteredText(gc, "有效挥砍命中率  " + accuracy + "%",
                canvasHeight / 2.0 + 48, 19, Color.web("#94a3b8"));
        drawCenteredText(gc, "握拳重新开始 · 双手保持返回",
                canvasHeight / 2.0 + 108, 18, Color.color(1, 1, 1, 0.66));
    }

    private void drawCenteredText(GraphicsContext gc, String text, double y, double size, Color color) {
        gc.setFont(Font.font("System", FontWeight.BOLD, size));
        gc.setFill(color);
        gc.fillText(text, canvasWidth / 2.0 - estimateTextWidth(text, size) / 2.0, y);
    }

    private static double estimateTextWidth(String text, double size) {
        double units = 0.0;
        for (int i = 0; i < text.length(); i++) {
            units += text.charAt(i) > 255 ? 1.0 : 0.58;
        }
        return units * size;
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

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
    }

    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public String getDifficultyLabel(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "目标 300 分 · 炸弹扣 30";
            case NORMAL -> "目标 500 分 · 炸弹扣 50";
            case HARD -> "目标 800 分 · 炸弹扣 75";
            case ENDLESS -> "目标 1200 分 · 持久挑战";
        };
    }

    @Override
    public boolean supportsDifficulty(Difficulty difficulty) {
        return difficulty != Difficulty.ENDLESS;
    }

    private static double pointToSegmentDistance(double px, double py,
                                                  double x1, double y1,
                                                  double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 0.0001) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = clamp(t, 0.0, 1.0);
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private static double random(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum FruitKind {
        APPLE(Color.web("#ef4444"), Color.web("#fee2e2"), Color.web("#7f1d1d")),
        ORANGE(Color.web("#f97316"), Color.web("#ffedd5"), Color.web("#9a3412")),
        WATERMELON(Color.web("#22c55e"), Color.web("#fb7185"), Color.web("#14532d")),
        LEMON(Color.web("#eab308"), Color.web("#fef9c3"), Color.web("#854d0e")),
        PLUM(Color.web("#a855f7"), Color.web("#f3e8ff"), Color.web("#581c87")),
        BOMB(Color.web("#111827"), Color.web("#475569"), Color.web("#020617"));

        final Color skin;
        final Color flesh;
        final Color edge;

        FruitKind(Color skin, Color flesh, Color edge) {
            this.skin = skin;
            this.flesh = flesh;
            this.edge = edge;
        }
    }

    private static final class Fruit {
        double x;
        double y;
        double vx;
        double vy;
        final double gravity;
        final double radius;
        double rotation;
        final double rotationSpeed;
        final FruitKind kind;
        final boolean bomb;
        boolean hit;

        Fruit(double x, double y, double vx, double vy, double gravity, double radius,
              double rotation, double rotationSpeed, FruitKind kind, boolean bomb) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.radius = radius;
            this.rotation = rotation;
            this.rotationSpeed = rotationSpeed;
            this.kind = kind;
            this.bomb = bomb;
        }

        void update(double dt) {
            vy += gravity * dt;
            x += vx * dt;
            y += vy * dt;
            rotation += rotationSpeed * dt;
        }

        void render(GraphicsContext gc) {
            gc.save();
            gc.translate(x, y);
            gc.rotate(rotation);
            if (bomb) {
                drawBomb(gc, radius);
            } else {
                drawWholeFruit(gc, kind, radius);
            }
            gc.restore();
        }
    }

    private static final class FruitHalf {
        double x;
        double y;
        double vx;
        double vy;
        final double gravity;
        final double radius;
        double rotation;
        final double rotationSpeed;
        final int side;
        final FruitKind kind;

        FruitHalf(Fruit fruit, int side, double vx, double vy) {
            this.x = fruit.x + side * fruit.radius * 0.18;
            this.y = fruit.y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = fruit.gravity * 1.08;
            this.radius = fruit.radius;
            this.rotation = fruit.rotation;
            this.rotationSpeed = fruit.rotationSpeed + side * 150;
            this.side = side;
            this.kind = fruit.kind;
        }

        void update(double dt) {
            vy += gravity * dt;
            x += vx * dt;
            y += vy * dt;
            rotation += rotationSpeed * dt;
        }

        void render(GraphicsContext gc) {
            gc.save();
            gc.translate(x, y);
            gc.rotate(rotation);
            double startX = side < 0 ? -radius : 0;
            gc.setFill(kind.skin);
            gc.fillOval(startX, -radius, radius, radius * 2);
            gc.setFill(kind.flesh);
            double innerX = side < 0 ? -radius * 0.82 : 0;
            gc.fillOval(innerX, -radius * 0.82, radius * 0.82, radius * 1.64);
            gc.setStroke(kind.edge);
            gc.setLineWidth(2.2);
            gc.strokeLine(0, -radius * 0.82, 0, radius * 0.82);
            if (kind == FruitKind.WATERMELON) {
                gc.setFill(Color.web("#3f1d2e"));
                for (int i = -1; i <= 1; i++) {
                    gc.fillOval(side * radius * 0.28, i * radius * 0.28 - 2, 4, 7);
                }
            }
            gc.restore();
        }
    }

    private static void drawWholeFruit(GraphicsContext gc, FruitKind kind, double radius) {
        gc.setFill(FRUIT_SHADOW);
        gc.fillOval(-radius * 0.90, -radius * 0.82, radius * 1.95, radius * 1.95);
        gc.setFill(kind.skin);
        gc.fillOval(-radius, -radius, radius * 2, radius * 2);
        gc.setStroke(kind.edge);
        gc.setLineWidth(2.2);
        gc.strokeOval(-radius, -radius, radius * 2, radius * 2);

        if (kind == FruitKind.WATERMELON) {
            gc.setStroke(Color.color(0.05, 0.28, 0.12, 0.7));
            gc.setLineWidth(Math.max(2, radius * 0.10));
            gc.strokeArc(-radius * 0.72, -radius * 0.95, radius * 1.44, radius * 1.9, 78, 24,
                    javafx.scene.shape.ArcType.OPEN);
            gc.strokeArc(-radius * 0.72, -radius * 0.95, radius * 1.44, radius * 1.9, 258, 24,
                    javafx.scene.shape.ArcType.OPEN);
        }

        gc.setFill(FRUIT_HIGHLIGHT);
        gc.fillOval(-radius * 0.52, -radius * 0.62, radius * 0.48, radius * 0.36);
        gc.setStroke(Color.web("#713f12"));
        gc.setLineWidth(Math.max(2.5, radius * 0.10));
        gc.strokeLine(0, -radius * 0.90, radius * 0.08, -radius * 1.22);
        gc.setFill(Color.web("#4d7c0f"));
        gc.fillOval(radius * 0.02, -radius * 1.26, radius * 0.58, radius * 0.22);
    }

    private static void drawBomb(GraphicsContext gc, double radius) {
        gc.setFill(BOMB_SHADOW);
        gc.fillOval(-radius * 0.86, -radius * 0.78, radius * 1.92, radius * 1.92);
        gc.setFill(Color.web("#111827"));
        gc.fillOval(-radius, -radius, radius * 2, radius * 2);
        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(3);
        gc.strokeOval(-radius, -radius, radius * 2, radius * 2);
        gc.setFill(BOMB_HIGHLIGHT);
        gc.fillOval(-radius * 0.52, -radius * 0.58, radius * 0.5, radius * 0.35);
        gc.setStroke(Color.web("#a16207"));
        gc.setLineWidth(4);
        gc.strokeLine(0, -radius * 0.92, radius * 0.25, -radius * 1.30);
        gc.setFill(Color.web("#fde047"));
        gc.fillOval(radius * 0.12, -radius * 1.48, radius * 0.35, radius * 0.35);
        gc.setFill(Color.web("#ef4444"));
        gc.fillOval(radius * 0.20, -radius * 1.40, radius * 0.18, radius * 0.18);
    }

    private static final class JuiceParticle {
        double x;
        double y;
        double vx;
        double vy;
        double life;
        final double maxLife;
        final double size;
        final Color color;

        JuiceParticle(double x, double y, double vx, double vy,
                      double life, double size, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
        }

        void update(double dt) {
            vy += 460 * dt;
            vx *= Math.pow(0.35, dt);
            x += vx * dt;
            y += vy * dt;
            life -= dt;
        }

        void render(GraphicsContext gc) {
            double alpha = clamp(life / maxLife, 0, 1);
            gc.setGlobalAlpha(alpha * 0.88);
            gc.setFill(color);
            gc.fillOval(x - size / 2, y - size / 2, size, size * 0.72);
            gc.setGlobalAlpha(1.0);
        }
    }

    private static final class FloatingText {
        double x;
        double y;
        double life;
        final double maxLife;
        final String text;
        final Color color;
        final double size;
        final Font font;

        FloatingText(double x, double y, String text, Color color, double life, double size) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.font = size >= 32 ? EFFECT_LARGE_FONT
                    : (size >= 26 ? EFFECT_MEDIUM_FONT : EFFECT_SMALL_FONT);
        }

        void update(double dt) {
            y -= 48 * dt;
            life -= dt;
        }

        void render(GraphicsContext gc) {
            double alpha = clamp(life / maxLife, 0, 1);
            gc.setFont(font);
            gc.setGlobalAlpha(alpha);
            gc.setFill(color);
            gc.fillText(text, x - estimateTextWidth(text, size) / 2.0, y);
            gc.setGlobalAlpha(1.0);
        }
    }

    private static final class Shockwave {
        final double x;
        final double y;
        final Color color;
        double radius = 12;
        double life = 0.28;

        Shockwave(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
        }

        void update(double dt) {
            radius += 190 * dt;
            life -= dt;
        }

        void render(GraphicsContext gc) {
            double alpha = clamp(life / 0.28, 0, 1);
            gc.setGlobalAlpha(alpha * 0.72);
            gc.setStroke(color);
            gc.setLineWidth(2.5);
            gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
            gc.setGlobalAlpha(1.0);
        }
    }

    private static final class BladeSegment {
        double startX;
        double startY;
        double endX;
        double endY;

        void set(double startX, double startY, double endX, double endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        boolean intersects(double x, double y, double radius) {
            return pointToSegmentDistance(x, y, startX, startY, endX, endY) <= radius;
        }
    }

    private static final class BladeTracker {
        private static final int TRAIL_CAPACITY = 18;
        private static final Color HOT_TRAIL = Color.web("#c7f9ff");
        private static final Color IDLE_TRAIL = Color.web("#94bfc6");
        private static final Color HOT_CURSOR = Color.web("#ecfeff");
        private static final Color IDLE_CURSOR = Color.web("#b7dfe5");
        private static final Color HOT_RING = Color.web("#67e8f9");
        private static final Color IDLE_RING = Color.web("#94bfc6");

        private final double[] trailX = new double[TRAIL_CAPACITY];
        private final double[] trailY = new double[TRAIL_CAPACITY];
        private final double[] trailLife = new double[TRAIL_CAPACITY];
        private final boolean[] trailCutting = new boolean[TRAIL_CAPACITY];
        private final BladeSegment segment = new BladeSegment();
        private int trailCount;
        private double currentX;
        private double currentY;
        private boolean hasCurrent;
        private boolean segmentValid;
        private boolean handVisible;
        private boolean cutting;
        private boolean swingStarted;
        private boolean currentSwingSuccessful;

        void reset() {
            trailCount = 0;
            hasCurrent = false;
            segmentValid = false;
            handVisible = false;
            cutting = false;
            swingStarted = false;
            currentSwingSuccessful = false;
        }

        void update(GestureData gesture, double dt, int width, int height,
                    double speedThreshold, boolean enabled) {
            fadeTrail(dt);
            segmentValid = false;
            swingStarted = false;

            if (gesture == null || !gesture.isHandDetected()) {
                handVisible = false;
                cutting = false;
                hasCurrent = false;
                currentSwingSuccessful = false;
                return;
            }

            double rawX = clamp(gesture.getHandX(), 0, 1) * width;
            double rawY = clamp(gesture.getHandY(), 0, 1) * height;
            handVisible = true;
            if (!hasCurrent) {
                currentX = rawX;
                currentY = rawY;
                hasCurrent = true;
                addTrailPoint(rawX, rawY, 0.18, false);
                return;
            }

            double rawDistance = Math.hypot(rawX - currentX, rawY - currentY);
            double diagonal = Math.max(1.0, Math.hypot(width, height));
            double normalizedSpeed = rawDistance / Math.max(dt, 0.001) / diagonal;
            double smoothing = clamp(0.24 + normalizedSpeed * 0.48, 0.24, 0.84);
            double previousX = currentX;
            double previousY = currentY;
            currentX += (rawX - currentX) * smoothing;
            currentY += (rawY - currentY) * smoothing;

            boolean wasCutting = cutting;
            cutting = enabled && normalizedSpeed >= speedThreshold
                    && Math.hypot(currentX - previousX, currentY - previousY) >= 4.0;
            if (cutting) {
                segment.set(previousX, previousY, currentX, currentY);
                segmentValid = true;
            }
            if (cutting && !wasCutting) {
                swingStarted = true;
                currentSwingSuccessful = false;
            }
            if (!cutting && wasCutting) {
                currentSwingSuccessful = false;
            }

            addTrailPoint(currentX, currentY, cutting ? 0.22 : 0.10, cutting);
        }

        private void fadeTrail(double dt) {
            int write = 0;
            for (int read = 0; read < trailCount; read++) {
                double nextLife = trailLife[read] - dt;
                if (nextLife > 0) {
                    trailX[write] = trailX[read];
                    trailY[write] = trailY[read];
                    trailLife[write] = nextLife;
                    trailCutting[write] = trailCutting[read];
                    write++;
                }
            }
            trailCount = write;
        }

        private void addTrailPoint(double x, double y, double life, boolean activeCut) {
            if (trailCount == TRAIL_CAPACITY) {
                System.arraycopy(trailX, 1, trailX, 0, TRAIL_CAPACITY - 1);
                System.arraycopy(trailY, 1, trailY, 0, TRAIL_CAPACITY - 1);
                System.arraycopy(trailLife, 1, trailLife, 0, TRAIL_CAPACITY - 1);
                System.arraycopy(trailCutting, 1, trailCutting, 0, TRAIL_CAPACITY - 1);
                trailCount--;
            }
            trailX[trailCount] = x;
            trailY[trailCount] = y;
            trailLife[trailCount] = life;
            trailCutting[trailCount] = activeCut;
            trailCount++;
        }

        void render(GraphicsContext gc) {
            if (trailCount >= 2) {
                for (int i = 1; i < trailCount; i++) {
                    double alpha = (i + 1.0) / trailCount;
                    boolean hot = trailCutting[i];
                    gc.setGlobalAlpha(hot ? alpha * 0.86 : alpha * 0.24);
                    gc.setStroke(hot ? HOT_TRAIL : IDLE_TRAIL);
                    gc.setLineWidth(hot ? 3.5 + alpha * 7.5 : 2.0);
                    gc.strokeLine(trailX[i - 1], trailY[i - 1], trailX[i], trailY[i]);
                }
                gc.setGlobalAlpha(1.0);
            }

            if (handVisible && hasCurrent) {
                gc.setFill(cutting ? HOT_CURSOR : IDLE_CURSOR);
                double radius = cutting ? 8 : 6;
                gc.fillOval(currentX - radius, currentY - radius, radius * 2, radius * 2);
                gc.setStroke(cutting ? HOT_RING : IDLE_RING);
                gc.setLineWidth(2);
                gc.strokeOval(currentX - 13, currentY - 13, 26, 26);
            }
        }

        BladeSegment currentCutSegment() {
            return segmentValid ? segment : null;
        }

        boolean consumeSwingStarted() {
            boolean result = swingStarted;
            swingStarted = false;
            return result;
        }

        boolean markCurrentSwingSuccessful() {
            if (currentSwingSuccessful) {
                return false;
            }
            currentSwingSuccessful = true;
            return true;
        }

        boolean isHandVisible() {
            return handVisible;
        }

        void disableCutting() {
            cutting = false;
            segmentValid = false;
        }
    }
}
