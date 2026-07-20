package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Gesture-controlled, JavaFX-native marble shooter inspired by classic Zuma. */
public final class ZumaGame implements GameInterface {

    private static final double BALL_RADIUS = 17.0;
    private static final double BALL_SPACING = BALL_RADIUS * 1.92;
    private static final double PROJECTILE_RADIUS = 15.5;
    private static final double PROJECTILE_SPEED = 17.0;
    private static final int PATH_SAMPLES = 960;
    private static final int INPUT_GUARD_FRAMES = 30;
    // 30fps 下约 0.17 秒确认；必须明确张开手掌约 0.17 秒才可再次发射。
    private static final int FIST_CONFIRM_FRAMES = 5;
    private static final int FIST_RELEASE_FRAMES = 5;
    private static final int FIST_DROPOUT_MAX = 2;
    private static final int MAX_PROJECTILES = 4;
    private static final double AIM_CENTER_X = 0.50;
    private static final double AIM_CENTER_Y = 0.52;
    // Hysteresis keeps tiny camera-coordinate jitter around the neutral hand
    // position from making the direction flip.  Once aiming has begun, it
    // remains active until the hand comes back well inside the smaller zone.
    private static final double AIM_DEAD_ZONE_ENTER = 0.027;
    private static final double AIM_DEAD_ZONE_EXIT = 0.018;
    private static final double AIM_TURN_RESPONSE = 0.46;
    private static final double AIM_MAX_TURN_PER_FRAME = Math.toRadians(14.0);
    private static final Color[] PALETTE = {
            Color.web("#ef4444"),
            Color.web("#f5cf45"),
            Color.web("#22c55e"),
            Color.web("#38bdf8"),
            Color.web("#a855f7"),
            Color.web("#f97316")
    };

    private static final class Star {
        double x, y, radius, alpha, blinkSpeed;
        public Star(double x, double y) {
            this.x = x; this.y = y;
            this.radius = Math.random() * 1.5 + 0.5;
            this.alpha = Math.random();
            this.blinkSpeed = Math.random() * 0.05 + 0.01;
        }
        public void updateAndDraw(GraphicsContext gc) {
            alpha += blinkSpeed;
            if (alpha >= 1.0 || alpha <= 0.0) blinkSpeed = -blinkSpeed;
            gc.setFill(Color.color(1, 1, 1, Math.max(0, Math.min(1, alpha))));
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        }
    }

    private static final class Shockwave {
        double x, y, radius, maxRadius, lineWidth, alpha;
        Color color;
        Shockwave(double x, double y, Color color) {
            this.x = x; this.y = y; this.color = color;
            this.radius = 10; this.maxRadius = 55; this.lineWidth = 5; this.alpha = 1.0;
        }
    }

    private static final class MagneticArc {
        ChainBall b1, b2;
        Color color;
        MagneticArc(ChainBall b1, ChainBall b2, Color color) {
            this.b1 = b1; this.b2 = b2; this.color = color;
        }
    }

    // --- Image Assets ---
    private javafx.scene.image.Image bgImage;
    private javafx.scene.image.Image turretImage;
    private javafx.scene.image.Image[] ballImages;
    private boolean assetsLoaded = false;

    private void loadAssets() {
        try {
            bgImage = new javafx.scene.image.Image("file:///C:/Users/Justin/Desktop/实训项目素材/background.png", 0, 0, false, true);
            turretImage = new javafx.scene.image.Image("file:///C:/Users/Justin/Desktop/实训项目素材/炮台.png", 0, 0, false, true);

            ballImages = new javafx.scene.image.Image[6];
            ballImages[0] = new javafx.scene.image.Image("file:///C:/Users/Justin/Desktop/实训项目素材/红球.png", 0, 0, false, true); // Red
            ballImages[1] = new javafx.scene.image.Image("file:///C:/Users/Justin/Desktop/实训项目素材/黄球.png", 0, 0, false, true); // Yellow
            ballImages[2] = new javafx.scene.image.Image("file:///C:/Users/Justin/Desktop/实训项目素材/绿球.png", 0, 0, false, true); // Green
            ballImages[3] = new javafx.scene.image.Image("file:///C:/Users/Justin/Desktop/实训项目素材/蓝球.png", 0, 0, false, true); // Blue
            // Reuse for other indices or leave null to use fallback rendering
            ballImages[4] = null;
            ballImages[5] = null;

            assetsLoaded = isUsableImage(bgImage) || isUsableImage(turretImage);
            for (javafx.scene.image.Image ballImage : ballImages) {
                assetsLoaded |= isUsableImage(ballImage);
            }
        } catch (Exception e) {
            System.err.println("Failed to load Zuma assets: " + e.getMessage());
        }
    }

    private boolean isUsableImage(javafx.scene.image.Image image) {
        return image != null && !image.isError();
    }
    // --------------------

    private static final class ChargeParticle {
        double angle, dist, speed, size;
        Color color;
        ChargeParticle(Color color) {
            this.angle = Math.random() * Math.PI * 2;
            this.dist = 40 + Math.random() * 20;
            this.speed = 2 + Math.random() * 3;
            this.size = 2 + Math.random() * 3;
            this.color = color;
        }
        boolean update() {
            dist -= speed;
            angle += 0.1;
            return dist > 0;
        }
    }

    private static final class LightningArc {
        double x, y, angle, length, life;
        Color color;
        LightningArc(double x, double y, Color color) {
            this.x = x; this.y = y; this.color = color;
            this.angle = Math.random() * Math.PI * 2;
            this.length = 30 + Math.random() * 30;
            this.life = 1.0;
        }
    }

    private final Random random = new Random();
    private final List<PathPoint> path = new ArrayList<>(PATH_SAMPLES);
    private final List<ChainBall> chain = new ArrayList<>();
    private final List<BurstParticle> particles = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<TrailParticle> trailParticles = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<MagneticArc> magneticArcs = new ArrayList<>();
    private final List<ChargeParticle> chargeParticles = new ArrayList<>();
    private final List<LightningArc> lightnings = new ArrayList<>();

    private int width;
    private int height;
    private double pathLength;
    private double frogX;
    private double frogY;
    private double aimX;
    private double aimY;
    private double aimAngle = -Math.PI / 2.0;
    private double lockedShotAngle = -Math.PI / 2.0;
    private boolean aimOutsideDeadZone;
    private boolean handDetected;

    private Difficulty difficulty = Difficulty.NORMAL;
    private int activeColors;
    private double chainSpeed;
    private int targetScore;
    private int waveSize;
    private int waveRemaining;
    private int refillDelay;
    private int inputGuardFrames;

    private int score;
    private boolean over;
    private boolean won;
    private int frame;
    private int currentColor;
    private boolean fistLatched;
    private int fistHoldFrames;
    private int fistDropoutFrames;
    private int fistReleaseFrames;
    private int combo;
    private int comboDisplayFrames;

    @Override
    public String getName() {
        return "星际祖玛";
    }

    @Override
    public String getDescription() {
        return "瞄准彩球链，三颗同色即可消除";
    }

    @Override
    public String getIcon() {
        return "🐸";
    }

    @Override
    public void init(int width, int height) {
        if (!assetsLoaded) {
            loadAssets();
        }
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        frogX = this.width * 0.5;
        frogY = this.height * 0.5;
        aimX = frogX;
        aimY = frogY - 180.0;
        aimOutsideDeadZone = false;
        handDetected = false;
        score = 0;
        over = false;
        won = false;
        frame = 0;
        combo = 1;
        comboDisplayFrames = 0;
        projectiles.clear();
        fistLatched = true; // Require a release after difficulty confirmation.
        fistHoldFrames = 0;
        fistDropoutFrames = 0;
        fistReleaseFrames = 0;
        inputGuardFrames = INPUT_GUARD_FRAMES;
        chain.clear();
        particles.clear();
        trailParticles.clear();
        stars.clear();
        shockwaves.clear();
        magneticArcs.clear();
        chargeParticles.clear();
        lightnings.clear();

        applyDifficulty();
        createSpiralPath();
        updateAimMarker();
        startWave();
        currentColor = choosePlayableColor();
    }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY -> {
                activeColors = 4;
                chainSpeed = 0.36;
                targetScore = 450;
                waveSize = 58;
            }
            case NORMAL -> {
                activeColors = 5;
                chainSpeed = 0.52;
                targetScore = 700;
                waveSize = 78;
            }
            case HARD -> {
                activeColors = 6;
                chainSpeed = 0.72;
                targetScore = 1000;
                waveSize = 100;
            }
            case ENDLESS -> {
                activeColors = 5;
                chainSpeed = 0.58;
                targetScore = -1;
                waveSize = 88;
            }
        }
    }

    private void createSpiralPath() {
        path.clear();
        double centerX = width * 0.48; // Align with the background nebula core
        double centerY = height * 0.45;
        double outerRadiusX = width * 0.45;
        double outerRadiusY = height * 0.40;
        double innerRadiusX = Math.max(112.0, width * 0.115);
        double innerRadiusY = Math.max(72.0, height * 0.105);
        double previousX = 0.0;
        double previousY = 0.0;
        double distance = 0.0;

        for (int index = 0; index < PATH_SAMPLES; index++) {
            double t = index / (double) (PATH_SAMPLES - 1);
            double eased = t * t * (3.0 - 2.0 * t);
            double radiusX = outerRadiusX + (innerRadiusX - outerRadiusX) * eased;
            double radiusY = outerRadiusY + (innerRadiusY - outerRadiusY) * eased;

            // Adjust the angle to match the nebula swirl (Starts from top-right, spirals inwards to center)
            double angle = -Math.PI / 4.0 + t * Math.PI * 3.72;

            double x = centerX + Math.cos(angle) * radiusX;
            double y = centerY + Math.sin(angle) * radiusY;
            if (index > 0) {
                distance += Math.hypot(x - previousX, y - previousY);
            }
            path.add(new PathPoint(x, y, distance));
            previousX = x;
            previousY = y;
        }
        pathLength = distance;
    }

    private void startWave() {
        chain.clear();
        int visibleSeed = Math.min(22, waveSize);
        for (int index = visibleSeed - 1; index >= 0; index--) {
            double distance = 165.0 - index * BALL_SPACING;
            chain.add(new ChainBall(distance, randomChainColor()));
        }
        chain.sort(Comparator.comparingDouble(ball -> ball.distance));
        waveRemaining = Math.max(0, waveSize - visibleSeed);
        refillDelay = 0;
    }

    private int randomChainColor() {
        int color = random.nextInt(activeColors);
        if (chain.size() >= 2) {
            int last = chain.get(chain.size() - 1).color;
            int before = chain.get(chain.size() - 2).color;
            if (last == color && before == color) {
                color = (color + 1 + random.nextInt(activeColors - 1)) % activeColors;
            }
        }
        return color;
    }

    @Override
    public void update(GestureData gesture) {
        if (over) {
            return;
        }
        frame++;
        if (inputGuardFrames > 0) {
            inputGuardFrames--;
        }
        if (comboDisplayFrames > 0) {
            comboDisplayFrames--;
        }

        updateInput(gesture == null ? new GestureData() : gesture);
        advanceChain();
        feedWave();
        updateProjectiles();
        updateParticles();

        if (!chain.isEmpty()
                && chain.get(chain.size() - 1).distance >= pathLength - BALL_RADIUS * 1.35) {
            won = false;
            over = true;
        } else if (targetScore > 0 && score >= targetScore) {
            score = targetScore;
            won = true;
            over = true;
        }
    }

    private void updateInput(GestureData gesture) {
        handDetected = gesture.isHandDetected();
        GestureType type = handDetected ? gesture.getGesture() : GestureType.NONE;
        boolean fistNow = type == GestureType.FIST;
        boolean confirmingFist = !fistLatched && fistHoldFrames > 0;
        // 握拳会改变手掌中心；确认阶段冻结枪口，避免发射瞬间方向跳动。
        if (handDetected && !fistNow && !confirmingFist) {
            updateComfortAim(gesture);
        }

        if (fistNow) {
            fistReleaseFrames = 0;
            fistDropoutFrames = 0;
            if (!fistLatched) {
                if (fistHoldFrames == 0) {
                    // Closing a hand moves its centre.  Preserve the visible
                    // guide-line direction from the first fist frame and use
                    // that exact angle for this shot.
                    lockedShotAngle = aimAngle;
                }
                fistHoldFrames = Math.min(FIST_CONFIRM_FRAMES, fistHoldFrames + 1);
                if (fistHoldFrames >= FIST_CONFIRM_FRAMES) {
                    if (inputGuardFrames == 0 && projectiles.size() < MAX_PROJECTILES) {
                        shoot(lockedShotAngle);
                    }
                    fistLatched = true;
                    fistHoldFrames = 0;
                }
            }
        } else if (!fistLatched && fistHoldFrames > 0) {
            // 确认中允许极短闪断，进度不会因一帧抖动突然归零。
            if (fistDropoutFrames < FIST_DROPOUT_MAX) {
                fistDropoutFrames++;
            } else {
                fistHoldFrames = 0;
                fistDropoutFrames = 0;
            }
            fistReleaseFrames = 0;
        } else {
            fistHoldFrames = 0;
            fistDropoutFrames = 0;
            // 只接受明确张开手掌作为“松手”；NONE/误识别不能让持续握拳变成连发。
            if (fistLatched && type == GestureType.OPEN) {
                fistReleaseFrames = Math.min(FIST_RELEASE_FRAMES, fistReleaseFrames + 1);
                if (fistReleaseFrames >= FIST_RELEASE_FRAMES) {
                    fistLatched = false;
                }
            } else {
                fistReleaseFrames = 0;
            }
        }
    }

    private void updateComfortAim(GestureData gesture) {
        double dx = clamp(gesture.getHandX(), 0.0, 1.0) - AIM_CENTER_X;
        double dy = clamp(gesture.getHandY(), 0.0, 1.0) - AIM_CENTER_Y;
        double handRadius = Math.hypot(dx, dy);
        if (aimOutsideDeadZone) {
            if (handRadius < AIM_DEAD_ZONE_EXIT) {
                aimOutsideDeadZone = false;
            }
        } else if (handRadius > AIM_DEAD_ZONE_ENTER) {
            aimOutsideDeadZone = true;
        }
        if (!aimOutsideDeadZone) {
            updateAimMarker();
            return;
        }

        double rawAngle = Math.atan2(dy, dx);
        double delta = shortestAngleDelta(aimAngle, rawAngle);
        // Never consume a large coordinate jump in a single render frame.
        // It turns at a visibly continuous speed while still covering normal
        // hand movements in only a few frames.
        double turn = clamp(delta * AIM_TURN_RESPONSE,
                -AIM_MAX_TURN_PER_FRAME, AIM_MAX_TURN_PER_FRAME);
        aimAngle = normalizeAngle(aimAngle + turn);
        updateAimMarker();
    }

    private void updateAimMarker() {
        double cos = Math.cos(aimAngle);
        double sin = Math.sin(aimAngle);
        double radiusX = Math.max(1.0, width * 0.43);
        double radiusY = Math.max(1.0, height * 0.37);
        double rayLength = 1.0 / Math.sqrt(
                cos * cos / (radiusX * radiusX)
                        + sin * sin / (radiusY * radiusY));
        aimX = frogX + cos * rayLength;
        aimY = frogY + sin * rayLength;
    }

    private void shoot(double shotAngle) {
        double cos = Math.cos(shotAngle);
        double sin = Math.sin(shotAngle);
        int firedColor = currentColor;
        projectiles.add(new Projectile(
                frogX + cos * 42.0,
                frogY + sin * 42.0,
                cos * PROJECTILE_SPEED,
                sin * PROJECTILE_SPEED,
                firedColor));
        // 每次发射后立即装填另一种颜色；持续握拳不会重复触发。
        currentColor = chooseDifferentPlayableColor(firedColor);
    }

    private void advanceChain() {
        for (ChainBall ball : chain) {
            ball.distance += chainSpeed;
            if (ball.visualOffset > 0) {
                ball.visualOffset = Math.max(0, ball.visualOffset - 12.0); // Magnetic snap speed
            }
        }
    }

    private void feedWave() {
        if (waveRemaining > 0) {
            if (chain.isEmpty() || chain.get(0).distance >= BALL_SPACING * 0.90) {
                double distance = chain.isEmpty()
                        ? -BALL_SPACING
                        : chain.get(0).distance - BALL_SPACING;
                chain.add(0, new ChainBall(distance, randomChainColor()));
                waveRemaining--;
            }
            return;
        }

        if (chain.isEmpty()) {
            refillDelay++;
            if (refillDelay >= 72 && (targetScore < 0 || score < targetScore)) {
                startWave();
                currentColor = choosePlayableColor();
            }
        }
    }

    private void updateProjectiles() {
        for (int projectileIndex = projectiles.size() - 1;
             projectileIndex >= 0; projectileIndex--) {
            Projectile projectile = projectiles.get(projectileIndex);
            projectile.x += projectile.vx;
            projectile.y += projectile.vy;

            // Keep the teammate's neon projectile trail for every active shot.
            for (int i = 0; i < 3; i++) {
                trailParticles.add(new TrailParticle(
                        projectile.x + (random.nextDouble() - 0.5) * 4,
                        projectile.y + (random.nextDouble() - 0.5) * 4,
                        -projectile.vx * 0.15 + (random.nextDouble() - 0.5) * 2,
                        -projectile.vy * 0.15 + (random.nextDouble() - 0.5) * 2,
                        random.nextDouble() * 6 + 4,
                        PALETTE[Math.floorMod(projectile.color, PALETTE.length)]
                ));
            }

            int hitIndex = -1;
            double hitDistance = Double.MAX_VALUE;
            for (int index = 0; index < chain.size(); index++) {
                ChainBall ball = chain.get(index);
                double renderDistance = ball.distance + ball.visualOffset;
                if (renderDistance < -BALL_RADIUS || renderDistance > pathLength) {
                    continue;
                }
                PathPoint point = pointAtDistance(renderDistance);
                double distance = Math.hypot(projectile.x - point.x, projectile.y - point.y);
                if (distance < BALL_RADIUS + PROJECTILE_RADIUS - 2.0
                        && distance < hitDistance) {
                    hitDistance = distance;
                    hitIndex = index;
                }
            }

            if (hitIndex >= 0) {
                insertProjectile(hitIndex, projectile);
                projectiles.remove(projectileIndex);
                continue;
            }

            double margin = 80.0;
            if (projectile.x < -margin || projectile.x > width + margin
                    || projectile.y < -margin || projectile.y > height + margin) {
                projectiles.remove(projectileIndex);
            }
        }
    }

    private void insertProjectile(int hitIndex, Projectile projectile) {
        ChainBall hit = chain.get(hitIndex);
        Vec tangent = tangentAtDistance(hit.distance + hit.visualOffset);
        double alongPath = projectile.vx * tangent.x + projectile.vy * tangent.y;
        // A shot travelling along the chain reaches the hit marble from its rear,
        // so it must be inserted before it. The former direction was reversed.
        int insertIndex = alongPath >= 0.0 ? hitIndex : hitIndex + 1;

        double distance;
        if (insertIndex <= 0) {
            distance = chain.get(0).distance - BALL_SPACING;
        } else if (insertIndex >= chain.size()) {
            distance = chain.get(chain.size() - 1).distance + BALL_SPACING;
        } else {
            distance = (chain.get(insertIndex - 1).distance
                    + chain.get(insertIndex).distance) * 0.5;
        }
        chain.add(insertIndex, new ChainBall(distance, projectile.color));
        closeSpacingForward();
        resolveMatches(insertIndex);
    }

    private void closeSpacingForward() {
        for (int index = 1; index < chain.size(); index++) {
            double minimum = chain.get(index - 1).distance + BALL_SPACING;
            if (chain.get(index).distance < minimum) {
                chain.get(index).distance = minimum;
            }
        }
    }

    private void resolveMatches(int insertedIndex) {
        int probe = Math.max(0, Math.min(insertedIndex, chain.size() - 1));
        int chainCombo = 1;
        while (!chain.isEmpty() && probe >= 0 && probe < chain.size()) {
            int color = chain.get(probe).color;
            int left = probe;
            int right = probe;
            while (left > 0 && chain.get(left - 1).color == color) {
                left--;
            }
            while (right + 1 < chain.size() && chain.get(right + 1).color == color) {
                right++;
            }
            int removed = right - left + 1;
            if (removed < 3) {
                break;
            }

            for (int index = left; index <= right; index++) {
                ChainBall ball = chain.get(index);
                PathPoint point = pointAtDistance(ball.distance + ball.visualOffset);
                createBurst(point.x, point.y, ball.color);
                shockwaves.add(new Shockwave(point.x, point.y, PALETTE[Math.floorMod(ball.color, PALETTE.length)]));
            }
            chain.subList(left, right + 1).clear();
            for (int index = left; index < chain.size(); index++) {
                chain.get(index).distance -= removed * BALL_SPACING;
                chain.get(index).visualOffset += removed * BALL_SPACING;
            }

            if (left > 0 && left < chain.size()) {
                magneticArcs.add(new MagneticArc(chain.get(left - 1), chain.get(left), PALETTE[Math.floorMod(color, PALETTE.length)]));
            }

            score += removed * 10 * chainCombo;
            combo = chainCombo;
            comboDisplayFrames = 70;
            chainCombo++;

            if (left <= 0 || left >= chain.size()
                    || chain.get(left - 1).color != chain.get(left).color) {
                break;
            }
            probe = left;
        }
    }

    private void createBurst(double x, double y, int colorIndex) {
        Color color = PALETTE[Math.floorMod(colorIndex, PALETTE.length)];
        int type = Math.floorMod(colorIndex, PALETTE.length);

        int numParticles = 15;
        if (type == 0) numParticles = 20;
        else if (type == 1) numParticles = 10;

        for (int index = 0; index < numParticles; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 2.0 + random.nextDouble() * 5.0;
            particles.add(new BurstParticle(
                    x, y,
                    Math.cos(angle) * speed,
                    Math.sin(angle) * speed,
                    1.0,
                    2.0 + random.nextDouble() * 4.0,
                    color, type));
        }

        if (type == 1) { // Yellow: Lightning arcs
            for (int i = 0; i < 4; i++) {
                lightnings.add(new LightningArc(x, y, color));
            }
        }

        if (particles.size() > 300) {
            particles.subList(0, particles.size() - 300).clear();
        }
    }

    private void updateParticles() {
        if (projectiles.isEmpty() && frame % 2 == 0) {
            chargeParticles.add(new ChargeParticle(PALETTE[Math.floorMod(currentColor, PALETTE.length)]));
        }
        for (int i = chargeParticles.size() - 1; i >= 0; i--) {
            if (!chargeParticles.get(i).update()) {
                chargeParticles.remove(i);
            }
        }

        for (int i = lightnings.size() - 1; i >= 0; i--) {
            LightningArc arc = lightnings.get(i);
            arc.life -= 0.05;
            if (arc.life <= 0) {
                lightnings.remove(i);
            }
        }

        for (int index = shockwaves.size() - 1; index >= 0; index--) {
            Shockwave sw = shockwaves.get(index);
            sw.radius += 3.0;
            sw.alpha -= 0.05;
            sw.lineWidth = Math.max(0.5, sw.lineWidth - 0.15);
            if (sw.alpha <= 0 || sw.radius >= sw.maxRadius) {
                shockwaves.remove(index);
            }
        }

        for (int index = trailParticles.size() - 1; index >= 0; index--) {
            TrailParticle tp = trailParticles.get(index);
            tp.x += tp.vx;
            tp.y += tp.vy;
            tp.life -= 0.08;
            tp.radius *= 0.92;
            if (tp.life <= 0) {
                trailParticles.remove(index);
            }
        }

        for (int index = particles.size() - 1; index >= 0; index--) {
            BurstParticle particle = particles.get(index);
            particle.x += particle.vx;
            particle.y += particle.vy;
            particle.vx *= 0.94; // space drag
            particle.vy *= 0.94;
            particle.life -= 0.03;
            particle.radius *= 0.95;
            particle.angle += particle.angularVelocity;
            if (particle.life <= 0.0) {
                particles.remove(index);
            }
        }
    }

    private int choosePlayableColor() {
        return choosePlayableColor(-1);
    }

    private int choosePlayableColor(int fallback) {
        boolean[] present = new boolean[activeColors];
        int count = 0;
        for (ChainBall ball : chain) {
            if (!present[ball.color]) {
                present[ball.color] = true;
                count++;
            }
        }
        if (count == 0) {
            return fallback >= 0 && fallback < activeColors
                    ? fallback : random.nextInt(activeColors);
        }
        int pick = random.nextInt(count);
        for (int color = 0; color < activeColors; color++) {
            if (present[color] && pick-- == 0) {
                return color;
            }
        }
        return 0;
    }

    private int chooseDifferentPlayableColor(int previousColor) {
        boolean[] present = new boolean[activeColors];
        int alternatives = 0;
        for (ChainBall ball : chain) {
            if (ball.color != previousColor && !present[ball.color]) {
                present[ball.color] = true;
                alternatives++;
            }
        }
        if (alternatives > 0) {
            int pick = random.nextInt(alternatives);
            for (int color = 0; color < activeColors; color++) {
                if (present[color] && pick-- == 0) {
                    return color;
                }
            }
        }
        if (activeColors <= 1) {
            return previousColor;
        }
        return (previousColor + 1 + random.nextInt(activeColors - 1)) % activeColors;
    }

    @Override
    public void render(GraphicsContext graphics) {
        drawBackground(graphics);
        drawTrack(graphics);
        drawTempleMouth(graphics);
        drawChain(graphics);
        drawMagneticArcs(graphics);
        drawParticles(graphics);
        drawAim(graphics);
        drawFrog(graphics);
        drawProjectiles(graphics);
        drawHud(graphics);
    }

    private void drawBackground(GraphicsContext graphics) {
        if (assetsLoaded && isUsableImage(bgImage)) {
            graphics.clearRect(0, 0, width, height);
            graphics.drawImage(bgImage, 0, 0, width, height);

            // Core Energy Pulse (Nebula core breathing)
            double coreX = width * 0.48;
            double coreY = height * 0.45;
            double alpha = 0.1 + Math.sin(frame * 0.05) * 0.05;

            graphics.setGlobalBlendMode(BlendMode.ADD);
            RadialGradient coreGrad = new RadialGradient(0, 0, coreX, coreY, 120, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(0.9, 0.39, 0.86, Math.min(1.0, alpha * 1.5))),
                    new Stop(0.5, Color.color(0.51, 0.15, 0.7, alpha)),
                    new Stop(1, Color.TRANSPARENT));
            graphics.setFill(coreGrad);
            graphics.fillOval(coreX - 120, coreY - 120, 240, 240);
            graphics.setGlobalBlendMode(BlendMode.SRC_OVER);

            // Floating Space Dust
            if (stars.isEmpty()) {
                for (int i = 0; i < 40; i++) {
                    stars.add(new Star(random.nextDouble() * width, random.nextDouble() * height));
                }
            }
            for (Star star : stars) {
                star.updateAndDraw(graphics);
            }
            return;
        }

        if (stars.isEmpty()) {
            for (int i = 0; i < 100; i++) {
                stars.add(new Star(random.nextDouble() * width, random.nextDouble() * height));
            }
        }
        graphics.setGlobalAlpha(1.0);
        RadialGradient bgGrad = new RadialGradient(0, 0, width / 2.0, height / 2.0, Math.max(width, height), false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0d1127")),
                new Stop(0.6, Color.web("#050714")),
                new Stop(1, Color.web("#020208")));
        graphics.setFill(bgGrad);
        graphics.fillRect(0.0, 0.0, width, height);

        for (Star star : stars) {
            star.updateAndDraw(graphics);
        }
    }

    private void drawMagneticArcs(GraphicsContext graphics) {
        graphics.setGlobalBlendMode(BlendMode.ADD);
        for (int i = magneticArcs.size() - 1; i >= 0; i--) {
            MagneticArc arc = magneticArcs.get(i);
            if (arc.b2.visualOffset <= 0 || !chain.contains(arc.b1) || !chain.contains(arc.b2)) {
                magneticArcs.remove(i);
                continue;
            }
            PathPoint p1 = pointAtDistance(arc.b1.distance + arc.b1.visualOffset);
            PathPoint p2 = pointAtDistance(arc.b2.distance + arc.b2.visualOffset);

            graphics.save();
            graphics.setStroke(arc.color);
            graphics.setLineWidth(3.0);
            graphics.setEffect(new DropShadow(15, arc.color));
            graphics.beginPath();
            graphics.moveTo(p1.x, p1.y);
            double midX = (p1.x + p2.x) / 2.0 + (random.nextDouble() - 0.5) * 20.0;
            double midY = (p1.y + p2.y) / 2.0 + (random.nextDouble() - 0.5) * 20.0;
            graphics.lineTo(midX, midY);
            graphics.lineTo(p2.x, p2.y);
            graphics.stroke();
            graphics.restore();
        }
        graphics.setGlobalBlendMode(BlendMode.SRC_OVER);
    }
    private void drawTrack(GraphicsContext graphics) {
        if (assetsLoaded && isUsableImage(bgImage)) {
            // Layer 1: 3D Groove Groove
            strokePath(graphics, 22.0, Color.color(0.04, 0.05, 0.09, 0.4));

            // Layer 2: Glowing Fiber Edge
            graphics.setEffect(new DropShadow(8, Color.color(0.84, 0.55, 1.0, 0.6))); // Pink/Purple glow
            graphics.setLineDashes();

            // Offset the stroke by drawing two thin paths
            // Note: A true offset path is complex in basic GraphicsContext, so we draw a single slightly wider track
            // and hollow it out to create the "two edge lines" look.
            strokePath(graphics, 18.0, Color.color(0.84, 0.55, 1.0, 0.35));
            graphics.setEffect(null);

            // To hollow it out, we'll draw over it using SRC_OUT or just redraw the background in that area if possible.
            // Since JavaFX doesn't have BlendMode.CLEAR, and SRC_OUT requires a specific group structure,
            // we will simply draw the hollow part with a dark semi-transparent color to simulate the hollow center.
            strokePath(graphics, 16.0, Color.color(0.04, 0.05, 0.09, 0.8));

            // Re-fill the groove
            strokePath(graphics, 16.0, Color.color(0.04, 0.05, 0.09, 0.4));

            // Layer 3: Particle Flow (Gravity energy dots)
            graphics.setGlobalBlendMode(BlendMode.ADD);
            graphics.setFill(Color.color(1.0, 0.8, 0.9, 0.8));
            double flowSpeed = frame * 2.0;
            for (int i = 0; i < 18; i++) {
                double d = (flowSpeed + i * (pathLength / 18.0)) % pathLength;
                PathPoint p = pointAtDistance(d);
                graphics.fillOval(p.x - 2, p.y - 2, 4, 4);
            }
            graphics.setGlobalBlendMode(BlendMode.SRC_OVER);
            return;
        }

        // Outer glowing edge (20px width to create 2px border around 16px inner)
        graphics.setEffect(new DropShadow(12, Color.web("#8a2be2")));
        graphics.setLineDashes();
        strokePath(graphics, 20.0, Color.web("#00e5ff"));

        graphics.setEffect(null);

        // Hollow out the center using background color
        strokePath(graphics, 16.0, Color.web("#080c1e"));

        // Semi-transparent blue fill for the glass pipe
        strokePath(graphics, 16.0, Color.color(0.0, 0.706, 1.0, 0.25));

        // Center dashed energy flow
        graphics.setLineDashes(4.0, 8.0);
        strokePath(graphics, 2.0, Color.color(1.0, 1.0, 1.0, 0.6));
        graphics.setLineDashes();
    }

    private void strokePath(GraphicsContext graphics, double lineWidth, Color color) {
        if (path.isEmpty()) {
            return;
        }
        graphics.setStroke(color);
        graphics.setLineWidth(lineWidth);
        graphics.beginPath();
        PathPoint first = path.get(0);
        graphics.moveTo(first.x, first.y);
        for (int index = 2; index < path.size(); index += 2) {
            PathPoint point = path.get(index);
            graphics.lineTo(point.x, point.y);
        }
        PathPoint last = path.get(path.size() - 1);
        graphics.lineTo(last.x, last.y);
        graphics.stroke();
    }

    private void drawTempleMouth(GraphicsContext graphics) {
        PathPoint end = path.get(path.size() - 1);

        // Pulsating event horizon rings
        double pulse1 = (Math.sin(frame * 0.05) + 1.0) / 2.0;
        double pulse2 = (Math.cos(frame * 0.04) + 1.0) / 2.0;

        graphics.setStroke(Color.color(0.8, 0.0, 0.8, 0.3 + 0.3 * pulse1));
        graphics.setLineWidth(2.0 + 2.0 * pulse1);
        graphics.strokeOval(end.x - 45.0 - 10.0 * pulse1, end.y - 45.0 - 10.0 * pulse1, 90.0 + 20.0 * pulse1, 90.0 + 20.0 * pulse1);

        graphics.setStroke(Color.color(1.0, 0.0, 0.5, 0.2 + 0.2 * pulse2));
        graphics.setLineWidth(1.0 + 3.0 * pulse2);
        graphics.strokeOval(end.x - 55.0 - 15.0 * pulse2, end.y - 55.0 - 15.0 * pulse2, 110.0 + 30.0 * pulse2, 110.0 + 30.0 * pulse2);

        graphics.setEffect(new DropShadow(25, Color.web("#ff00ff")));
        graphics.setFill(Color.BLACK);
        graphics.fillOval(end.x - 40.0, end.y - 40.0, 80.0, 80.0);

        RadialGradient holeGrad = new RadialGradient(0, 0, end.x, end.y, 40, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.BLACK), new Stop(0.8, Color.web("#4b0082")), new Stop(1, Color.TRANSPARENT));
        graphics.setFill(holeGrad);
        graphics.fillOval(end.x - 55.0, end.y - 55.0, 110.0, 110.0);
        graphics.setEffect(null);
    }

    private void drawChain(GraphicsContext graphics) {
        for (ChainBall ball : chain) {
            double renderDistance = ball.distance + ball.visualOffset;
            if (renderDistance < -BALL_RADIUS * 2.0 || renderDistance > pathLength) {
                continue;
            }
            PathPoint point = pointAtDistance(renderDistance);
            drawBall(graphics, point.x, point.y, BALL_RADIUS, ball.color, 1.0);
        }
    }

    private void drawBall(GraphicsContext graphics, double x, double y,
                          double radius, int colorIndex, double alpha) {
        Color color = PALETTE[Math.floorMod(colorIndex, PALETTE.length)];
        graphics.setGlobalAlpha(alpha);

        double auraBlur = 12 + Math.sin(frame * 0.05) * 6;
        graphics.setEffect(new DropShadow(auraBlur, color));

        int type = Math.floorMod(colorIndex, PALETTE.length);
        if (assetsLoaded && ballImages != null && type < ballImages.length
                && isUsableImage(ballImages[type])) {
            graphics.drawImage(ballImages[type], x - radius, y - radius, radius * 2.0, radius * 2.0);
        } else {
            RadialGradient ballGrad = new RadialGradient(0, 0, x - radius * 0.3, y - radius * 0.3, radius * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.WHITE), new Stop(0.3, color), new Stop(1, color.darker().darker()));

            graphics.setFill(ballGrad);
            graphics.fillOval(x - radius, y - radius, radius * 2.0, radius * 2.0);

            graphics.setStroke(color.brighter());
            graphics.setLineWidth(1.5);
            graphics.strokeOval(x - radius, y - radius, radius * 2.0, radius * 2.0);

            // Draw patterns
            graphics.setEffect(null);
            graphics.setGlobalAlpha(alpha * 0.7);
            graphics.setStroke(Color.color(1.0, 1.0, 1.0, 0.8));
            graphics.setFill(Color.color(1.0, 1.0, 1.0, 0.4));
            graphics.setLineWidth(2.0);

            double pr = radius * 0.45;

            switch (type) {
                case 0: // Red -> 4-point star / cross
                    graphics.strokeLine(x - pr, y, x + pr, y);
                    graphics.strokeLine(x, y - pr, x, y + pr);
                    break;
                case 1: // Yellow -> Triangle
                    graphics.fillPolygon(new double[]{x, x - pr, x + pr}, new double[]{y - pr, y + pr*0.8, y + pr*0.8}, 3);
                    break;
                case 2: // Green -> Diamond
                    graphics.strokePolygon(new double[]{x, x + pr, x, x - pr}, new double[]{y - pr, y, y + pr, y}, 4);
                    break;
                case 3: // Blue -> Concentric circles
                    graphics.strokeOval(x - pr, y - pr, pr * 2, pr * 2);
                    graphics.strokeOval(x - pr * 0.4, y - pr * 0.4, pr * 0.8, pr * 0.8);
                    break;
                case 4: // Purple -> Atom / 3-leaf swirl
                    graphics.strokeOval(x - pr, y - pr*0.4, pr*2, pr*0.8);
                    graphics.save();
                    graphics.translate(x, y);
                    graphics.rotate(60);
                    graphics.strokeOval(-pr, -pr*0.4, pr*2, pr*0.8);
                    graphics.rotate(60);
                    graphics.strokeOval(-pr, -pr*0.4, pr*2, pr*0.8);
                    graphics.restore();
                    break;
                case 5: // Orange -> Square
                    graphics.strokeRect(x - pr*0.7, y - pr*0.7, pr*1.4, pr*1.4);
                    break;
            }
        }
        graphics.setEffect(null);
        graphics.setGlobalAlpha(1.0);
    }

    private void drawAim(GraphicsContext graphics) {
        if (!handDetected || over) {
            return;
        }
        double cos = Math.cos(aimAngle);
        double sin = Math.sin(aimAngle);

        // Thin red laser aiming line
        graphics.setStroke(Color.color(1.0, 0.2, 0.4, 0.4)); // rgba(255, 51, 102, 0.4)
        graphics.setLineWidth(1.0);
        graphics.setLineDashes();
        graphics.strokeLine(frogX + cos * 42.0, frogY + sin * 42.0,
                frogX + cos * Math.max(width, height), frogY + sin * Math.max(width, height));

        drawInsertionGuide(graphics, cos, sin);

        // Cyan crosshair at cursor position
        graphics.setEffect(new DropShadow(10, Color.web("#00e5ff")));
        graphics.setStroke(Color.web("#00e5ff"));
        graphics.setLineWidth(2.0);
        graphics.strokeOval(aimX - 13.0, aimY - 13.0, 26.0, 26.0);
        graphics.strokeLine(aimX - 18.0, aimY, aimX - 6.0, aimY);
        graphics.strokeLine(aimX + 6.0, aimY, aimX + 18.0, aimY);
        graphics.strokeLine(aimX, aimY - 18.0, aimX, aimY - 6.0);
        graphics.strokeLine(aimX, aimY + 6.0, aimX, aimY + 18.0);
        if (fistHoldFrames > 0) {
            double progress = fistHoldFrames / (double) FIST_CONFIRM_FRAMES;
            graphics.setStroke(Color.web("#f5d547"));
            graphics.setLineWidth(3.0);
            graphics.strokeArc(aimX - 22.0, aimY - 22.0, 44.0, 44.0,
                    90.0, -360.0 * progress, javafx.scene.shape.ArcType.OPEN);
        }
        graphics.setEffect(null);
    }

    /**
     * Shows the exact gap where the current guide line would insert a marble.
     * This is visual-only: it neither bends the aim nor changes collision or
     * insertion logic.
     */
    private void drawInsertionGuide(GraphicsContext graphics, double directionX, double directionY) {
        GuideGap gap = findGuideGap(directionX, directionY);
        if (gap == null) {
            return;
        }

        Color shotColor = PALETTE[Math.floorMod(currentColor, PALETTE.length)];
        double pulse = 0.82 + Math.sin(frame * 0.18) * 0.18;
        double halfLength = 17.0;
        double x1 = gap.x - gap.normalX * halfLength;
        double y1 = gap.y - gap.normalY * halfLength;
        double x2 = gap.x + gap.normalX * halfLength;
        double y2 = gap.y + gap.normalY * halfLength;

        graphics.save();
        graphics.setGlobalBlendMode(BlendMode.ADD);
        graphics.setEffect(new DropShadow(18.0, shotColor));
        graphics.setStroke(Color.color(shotColor.getRed(), shotColor.getGreen(), shotColor.getBlue(), 0.35 * pulse));
        graphics.setLineWidth(8.0);
        graphics.strokeLine(x1, y1, x2, y2);
        graphics.setEffect(new DropShadow(8.0, Color.WHITE));
        graphics.setStroke(Color.color(1.0, 1.0, 1.0, 0.96));
        graphics.setLineWidth(2.4);
        graphics.strokeLine(x1, y1, x2, y2);
        graphics.setFill(Color.WHITE);
        graphics.fillOval(gap.x - 3.2, gap.y - 3.2, 6.4, 6.4);
        graphics.restore();
    }

    private GuideGap findGuideGap(double directionX, double directionY) {
        double originX = frogX + directionX * 42.0;
        double originY = frogY + directionY * 42.0;
        double hitRadius = BALL_RADIUS + PROJECTILE_RADIUS - 2.0;
        int hitIndex = -1;
        double nearestEntry = Double.MAX_VALUE;

        for (int index = 0; index < chain.size(); index++) {
            ChainBall ball = chain.get(index);
            double renderDistance = ball.distance + ball.visualOffset;
            if (renderDistance < -BALL_RADIUS || renderDistance > pathLength) {
                continue;
            }
            PathPoint point = pointAtDistance(renderDistance);
            double offsetX = point.x - originX;
            double offsetY = point.y - originY;
            double forward = offsetX * directionX + offsetY * directionY;
            if (forward <= 0.0) {
                continue;
            }
            double lateral = Math.abs(offsetX * directionY - offsetY * directionX);
            if (lateral > hitRadius) {
                continue;
            }
            double entry = forward - Math.sqrt(hitRadius * hitRadius - lateral * lateral);
            if (entry < nearestEntry) {
                nearestEntry = entry;
                hitIndex = index;
            }
        }

        if (hitIndex < 0) {
            return null;
        }

        ChainBall hit = chain.get(hitIndex);
        double hitDistance = hit.distance + hit.visualOffset;
        Vec tangent = tangentAtDistance(hitDistance);
        double alongPath = directionX * tangent.x + directionY * tangent.y;
        int insertIndex = alongPath >= 0.0 ? hitIndex : hitIndex + 1;

        PathPoint boundary;
        if (insertIndex > 0 && insertIndex < chain.size()) {
            ChainBall before = chain.get(insertIndex - 1);
            ChainBall after = chain.get(insertIndex);
            PathPoint beforePoint = pointAtDistance(before.distance + before.visualOffset);
            PathPoint afterPoint = pointAtDistance(after.distance + after.visualOffset);
            boundary = new PathPoint((beforePoint.x + afterPoint.x) * 0.5,
                    (beforePoint.y + afterPoint.y) * 0.5, 0.0);
            double joinX = afterPoint.x - beforePoint.x;
            double joinY = afterPoint.y - beforePoint.y;
            double joinLength = Math.hypot(joinX, joinY);
            if (joinLength > 1e-6) {
                tangent = new Vec(joinX / joinLength, joinY / joinLength);
            }
        } else {
            double side = insertIndex <= 0 ? -1.0 : 1.0;
            PathPoint hitPoint = pointAtDistance(hitDistance);
            boundary = new PathPoint(hitPoint.x + tangent.x * BALL_RADIUS * side,
                    hitPoint.y + tangent.y * BALL_RADIUS * side, 0.0);
        }

        return new GuideGap(boundary.x, boundary.y, -tangent.y, tangent.x);
    }

    private void drawFrog(GraphicsContext graphics) {
        graphics.save();
        graphics.translate(frogX, frogY);
        graphics.rotate(Math.toDegrees(aimAngle) + 90.0);

        if (assetsLoaded && isUsableImage(turretImage)) {
            graphics.drawImage(turretImage, -70, -70, 140, 140);

            Color cColor = PALETTE[Math.floorMod(currentColor, PALETTE.length)];
            graphics.setGlobalBlendMode(BlendMode.ADD);

            // Energy core / slot glow
            double corePulse = 15 + Math.sin(frame * 0.1) * 5;
            graphics.setEffect(new DropShadow(corePulse, cColor));
            graphics.setFill(Color.color(cColor.getRed(), cColor.getGreen(), cColor.getBlue(), 0.6));
            graphics.fillOval(-15, -15, 30, 30);

            // Inward vortex
            for (ChargeParticle cp : chargeParticles) {
                double px = Math.cos(cp.angle) * cp.dist;
                double py = Math.sin(cp.angle) * cp.dist - 35; // move center to cannon mouth
                graphics.setFill(cp.color);
                graphics.fillOval(px - cp.size, py - cp.size, cp.size * 2, cp.size * 2);
            }

            graphics.setGlobalBlendMode(BlendMode.SRC_OVER);
            graphics.setEffect(null);
            drawBall(graphics, 0.0, -29.0, 12.5, currentColor, 1.0);
        } else {
            graphics.setEffect(new DropShadow(10, Color.web("#00e5ff")));
            graphics.setFill(Color.web("#0d1b2a"));
            graphics.fillOval(-39.0, -34.0, 78.0, 82.0);
            graphics.setFill(Color.web("#1b263b"));
            graphics.fillOval(-34.0, -40.0, 68.0, 78.0);
            graphics.setStroke(Color.web("#00e5ff"));
            graphics.setLineWidth(2.0);
            graphics.strokeOval(-34.0, -40.0, 68.0, 78.0);

            graphics.setFill(Color.web("#415a77"));
            graphics.fillOval(-31.0, -48.0, 24.0, 27.0);
            graphics.fillOval(7.0, -48.0, 24.0, 27.0);
            graphics.setFill(Color.web("#00e5ff"));
            graphics.fillOval(-26.0, -43.0, 14.0, 16.0);
            graphics.fillOval(12.0, -43.0, 14.0, 16.0);
            graphics.setFill(Color.web("#172217"));
            graphics.fillOval(-21.5, -38.5, 6.0, 8.0);
            graphics.fillOval(15.5, -38.5, 6.0, 8.0);

            graphics.setEffect(null);
            drawBall(graphics, 0.0, -29.0, 12.5, currentColor, 1.0);
        }
        graphics.restore();
    }

    private void drawProjectiles(GraphicsContext graphics) {
        for (Projectile projectile : projectiles) {
            drawBall(graphics, projectile.x, projectile.y,
                    PROJECTILE_RADIUS, projectile.color, 1.0);
        }
    }

    private void drawParticles(GraphicsContext graphics) {
        graphics.setGlobalBlendMode(BlendMode.ADD);

        for (LightningArc arc : lightnings) {
            graphics.setGlobalAlpha(Math.max(0.0, arc.life));
            graphics.setStroke(arc.color);
            graphics.setLineWidth(2.0);
            graphics.setEffect(new DropShadow(10, arc.color));
            graphics.beginPath();
            graphics.moveTo(arc.x, arc.y);
            double lx = arc.x;
            double ly = arc.y;
            double segLen = arc.length / 4.0;
            for (int j = 1; j <= 4; j++) {
                lx += Math.cos(arc.angle) * segLen + (random.nextDouble() - 0.5) * 15;
                ly += Math.sin(arc.angle) * segLen + (random.nextDouble() - 0.5) * 15;
                graphics.lineTo(lx, ly);
            }
            graphics.stroke();
        }

        for (Shockwave sw : shockwaves) {
            graphics.setGlobalAlpha(Math.max(0.0, sw.alpha));
            graphics.setStroke(sw.color);
            graphics.setLineWidth(sw.lineWidth);
            graphics.setEffect(new DropShadow(15, sw.color));
            graphics.strokeOval(sw.x - sw.radius, sw.y - sw.radius, sw.radius * 2, sw.radius * 2);
        }

        for (TrailParticle tp : trailParticles) {
            graphics.setGlobalAlpha(Math.max(0.0, tp.life));
            graphics.setEffect(new DropShadow(10, tp.color));
            graphics.setFill(tp.color);
            graphics.fillOval(tp.x - tp.radius, tp.y - tp.radius, tp.radius * 2, tp.radius * 2);
        }
        graphics.setEffect(null);

        for (BurstParticle particle : particles) {
            graphics.setGlobalAlpha(Math.max(0.0, particle.life));
            graphics.setFill(particle.color);
            if (particle.type == 0) { // Red Sparks
                graphics.fillOval(particle.x - particle.radius, particle.y - particle.radius, particle.radius * 2.0, particle.radius * 2.0);
            } else if (particle.type == 1) { // Yellow Triangle fragments
                graphics.save();
                graphics.translate(particle.x, particle.y);
                graphics.rotate(particle.angle);
                double r = particle.radius * 1.5;
                graphics.fillPolygon(new double[]{0, -r, r}, new double[]{-r, r, r}, 3);
                graphics.restore();
            } else if (particle.type == 2) { // Green Diamond
                graphics.save();
                graphics.translate(particle.x, particle.y);
                graphics.rotate(particle.angle);
                double r = particle.radius * 1.5;
                graphics.fillPolygon(new double[]{0, r, 0, -r}, new double[]{-r, 0, r, 0}, 4);
                graphics.restore();
            } else if (particle.type == 3) { // Blue Ice crystals
                graphics.save();
                graphics.translate(particle.x, particle.y);
                graphics.rotate(particle.angle);
                double r = particle.radius * 1.2;
                graphics.fillRect(-r/2, -r, r, r*2);
                graphics.fillRect(-r, -r/2, r*2, r);
                graphics.restore();
            } else {
                graphics.fillOval(particle.x - particle.radius, particle.y - particle.radius, particle.radius * 2.0, particle.radius * 2.0);
            }
        }

        graphics.setGlobalBlendMode(BlendMode.SRC_OVER);
        graphics.setGlobalAlpha(1.0);
    }

    private void drawHud(GraphicsContext graphics) {
        graphics.setTextAlign(TextAlignment.LEFT);
        // Glassmorphism background for left panel
        graphics.setFill(Color.color(0.05, 0.06, 0.15, 0.65));
        graphics.fillRoundRect(22.0, 18.0, 245.0, 78.0, 20.0, 20.0);
        graphics.setStroke(Color.color(0.0, 0.9, 1.0, 0.3));
        graphics.setLineWidth(1.2);
        graphics.strokeRoundRect(22.0, 18.0, 245.0, 78.0, 20.0, 20.0);

        graphics.setFill(Color.web("#00e5ff"));
        graphics.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 22.0));
        graphics.fillText("🌌  星际祖玛", 41.0, 49.0);
        graphics.setFill(Color.LIGHTGRAY);
        graphics.setFont(Font.font("Microsoft YaHei UI", 13.0));
        graphics.fillText("稳定握拳发射一颗  ·  发射后自动换色", 41.0, 76.0);

        graphics.setTextAlign(TextAlignment.RIGHT);

        // Background panel for Score (Right side)
        graphics.setFill(Color.color(0.05, 0.06, 0.15, 0.65));
        graphics.fillRoundRect(width - 200.0, 18.0, 180.0, 78.0, 20.0, 20.0);
        graphics.setStroke(Color.color(0.0, 0.9, 1.0, 0.3));
        graphics.strokeRoundRect(width - 200.0, 18.0, 180.0, 78.0, 20.0, 20.0);

        graphics.setFill(Color.web("#00e5ff"));
        graphics.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 22.0));
        graphics.fillText("得分  " + score, width - 38.0, 49.0);
        graphics.setFill(Color.LIGHTGRAY);
        graphics.setFont(Font.font("Microsoft YaHei UI", 13.0));
        graphics.fillText(targetScore > 0
                ? "目标  " + targetScore : "无尽模式", width - 38.0, 76.0);

        if (comboDisplayFrames > 0 && combo > 1) {
            double alpha = Math.min(1.0, comboDisplayFrames / 18.0);
            graphics.setGlobalAlpha(alpha);
            graphics.setTextAlign(TextAlignment.CENTER);
            graphics.setFill(Color.web("#00e5ff"));
            graphics.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 25.0));
            graphics.setEffect(new DropShadow(10, Color.web("#00e5ff")));
            graphics.fillText("连锁 × " + combo, width * 0.5, 62.0);
            graphics.setEffect(null);
            graphics.setGlobalAlpha(1.0);
        }

        graphics.setTextAlign(TextAlignment.RIGHT);
        graphics.setFill(Color.web("#00e5ff"));
        graphics.setFont(Font.font("Microsoft YaHei UI", 13.0));
        graphics.fillText("手在中央小幅移动即可全场瞄准 · 握拳锁定并发射",
                width - 28.0, height - 24.0);
        graphics.setTextAlign(TextAlignment.LEFT);
    }

    private PathPoint pointAtDistance(double distance) {
        if (path.isEmpty()) {
            return new PathPoint(frogX, frogY, 0.0);
        }
        if (distance <= 0.0) {
            PathPoint first = path.get(0);
            PathPoint second = path.get(1);
            double segment = Math.max(0.001, second.distance - first.distance);
            double ratio = distance / segment;
            return new PathPoint(
                    first.x + (second.x - first.x) * ratio,
                    first.y + (second.y - first.y) * ratio,
                    distance);
        }
        if (distance >= pathLength) {
            return path.get(path.size() - 1);
        }

        int low = 0;
        int high = path.size() - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (path.get(middle).distance < distance) {
                low = middle;
            } else {
                high = middle;
            }
        }
        PathPoint before = path.get(low);
        PathPoint after = path.get(high);
        double span = Math.max(0.001, after.distance - before.distance);
        double ratio = (distance - before.distance) / span;
        return new PathPoint(
                before.x + (after.x - before.x) * ratio,
                before.y + (after.y - before.y) * ratio,
                distance);
    }

    private Vec tangentAtDistance(double distance) {
        PathPoint before = pointAtDistance(distance - 4.0);
        PathPoint after = pointAtDistance(distance + 4.0);
        double dx = after.x - before.x;
        double dy = after.y - before.y;
        double length = Math.max(0.001, Math.hypot(dx, dy));
        return new Vec(dx / length, dy / length);
    }

    @Override
    public boolean isOver() {
        return over;
    }

    @Override
    public int getScore() {
        return score;
    }

    @Override
    public void reset() {
        init(width, height);
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
    }

    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double shortestAngleDelta(double from, double to) {
        return Math.atan2(Math.sin(to - from), Math.cos(to - from));
    }

    private static double normalizeAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private record PathPoint(double x, double y, double distance) {
    }

    private record GuideGap(double x, double y, double normalX, double normalY) {
    }

    private record Vec(double x, double y) {
    }

    private static final class ChainBall {
        private double distance;
        private double visualOffset;
        private final int color;

        private ChainBall(double distance, int color) {
            this.distance = distance;
            this.visualOffset = 0.0;
            this.color = color;
        }
    }

    private static final class Projectile {
        private double x;
        private double y;
        private final double vx;
        private final double vy;
        private final int color;

        private Projectile(double x, double y, double vx, double vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }
    }

    private static final class TrailParticle {
        double x, y, vx, vy, life, radius;
        Color color;
        TrailParticle(double x, double y, double vx, double vy, double radius, Color color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.radius = radius; this.color = color;
            this.life = 1.0;
        }
    }

    private static final class BurstParticle {
        double x, y, vx, vy, life, radius, angle, angularVelocity;
        final Color color;
        final int type;

        private BurstParticle(double x, double y, double vx, double vy,
                              double life, double radius, Color color, int type) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.radius = radius;
            this.color = color;
            this.type = type;
            this.angle = Math.random() * 360.0;
            this.angularVelocity = (Math.random() - 0.5) * 20.0;
        }
    }
}
