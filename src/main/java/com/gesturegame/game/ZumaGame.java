package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
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
    private static final int FIST_CONFIRM_FRAMES = 10;
    private static final int FIST_RELEASE_FRAMES = 8;
    private static final int FIST_DROPOUT_MAX = 3;
    private static final int MAX_PROJECTILES = 4;
    private static final double AIM_CENTER_X = 0.50;
    private static final double AIM_CENTER_Y = 0.52;
    private static final double AIM_DEAD_ZONE = 0.035;
    private static final double AIM_SMOOTHING = 0.24;
    private static final double AIM_ASSIST_ANGLE = Math.toRadians(9.0);
    private static final Color[] PALETTE = {
            Color.web("#ef4444"),
            Color.web("#f5cf45"),
            Color.web("#22c55e"),
            Color.web("#38bdf8"),
            Color.web("#a855f7"),
            Color.web("#f97316")
    };

    private final Random random = new Random();
    private final List<PathPoint> path = new ArrayList<>(PATH_SAMPLES);
    private final List<ChainBall> chain = new ArrayList<>();
    private final List<BurstParticle> particles = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();

    private int width;
    private int height;
    private double pathLength;
    private double frogX;
    private double frogY;
    private double aimX;
    private double aimY;
    private double aimAngle = -Math.PI / 2.0;
    private double lockedShotAngle = -Math.PI / 2.0;
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
        return "祖玛";
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
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        frogX = this.width * 0.5;
        frogY = this.height * 0.5;
        aimX = frogX;
        aimY = frogY - 180.0;
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
        double centerX = width * 0.5;
        double centerY = height * 0.5;
        double outerRadiusX = width * 0.43;
        double outerRadiusY = height * 0.37;
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
            double angle = Math.PI + t * Math.PI * 3.72;
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
            // 只有持续检测到非握拳才重新解锁；短暂丢手不会造成二次发射。
            if (fistLatched && handDetected) {
                fistReleaseFrames = Math.min(FIST_RELEASE_FRAMES, fistReleaseFrames + 1);
                if (fistReleaseFrames >= FIST_RELEASE_FRAMES) {
                    fistLatched = false;
                }
            } else if (!handDetected) {
                fistReleaseFrames = 0;
            }
        }
    }

    private void updateComfortAim(GestureData gesture) {
        double dx = clamp(gesture.getHandX(), 0.0, 1.0) - AIM_CENTER_X;
        double dy = clamp(gesture.getHandY(), 0.0, 1.0) - AIM_CENTER_Y;
        if (Math.hypot(dx, dy) < AIM_DEAD_ZONE) {
            updateAimMarker();
            return;
        }

        double targetAngle = applyAimAssist(Math.atan2(dy, dx));
        aimAngle = normalizeAngle(aimAngle
                + shortestAngleDelta(aimAngle, targetAngle) * AIM_SMOOTHING);
        updateAimMarker();
    }

    private double applyAimAssist(double rawAngle) {
        double bestDelta = AIM_ASSIST_ANGLE;
        double bestAngle = rawAngle;
        for (ChainBall ball : chain) {
            if (ball.distance < 0.0 || ball.distance > pathLength) {
                continue;
            }
            PathPoint point = pointAtDistance(ball.distance);
            double ballAngle = Math.atan2(point.y - frogY, point.x - frogX);
            double delta = Math.abs(shortestAngleDelta(rawAngle, ballAngle));
            if (delta < bestDelta) {
                bestDelta = delta;
                bestAngle = ballAngle;
            }
        }
        if (bestDelta >= AIM_ASSIST_ANGLE) {
            return rawAngle;
        }
        double strength = 0.72 * (1.0 - bestDelta / AIM_ASSIST_ANGLE);
        return normalizeAngle(rawAngle + shortestAngleDelta(rawAngle, bestAngle) * strength);
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

            int hitIndex = -1;
            double hitDistance = Double.MAX_VALUE;
            for (int index = 0; index < chain.size(); index++) {
                ChainBall ball = chain.get(index);
                if (ball.distance < -BALL_RADIUS || ball.distance > pathLength) {
                    continue;
                }
                PathPoint point = pointAtDistance(ball.distance);
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
        Vec tangent = tangentAtDistance(hit.distance);
        double alongPath = projectile.vx * tangent.x + projectile.vy * tangent.y;
        int insertIndex = alongPath >= 0.0 ? hitIndex + 1 : hitIndex;

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
                PathPoint point = pointAtDistance(ball.distance);
                createBurst(point.x, point.y, ball.color);
            }
            chain.subList(left, right + 1).clear();
            for (int index = left; index < chain.size(); index++) {
                chain.get(index).distance -= removed * BALL_SPACING;
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
        Color color = PALETTE[colorIndex];
        for (int index = 0; index < 9; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 1.8 + random.nextDouble() * 4.2;
            particles.add(new BurstParticle(
                    x, y,
                    Math.cos(angle) * speed,
                    Math.sin(angle) * speed,
                    1.0,
                    2.0 + random.nextDouble() * 3.5,
                    color));
        }
        if (particles.size() > 180) {
            particles.subList(0, particles.size() - 180).clear();
        }
    }

    private void updateParticles() {
        for (int index = particles.size() - 1; index >= 0; index--) {
            BurstParticle particle = particles.get(index);
            particle.x += particle.vx;
            particle.y += particle.vy;
            particle.vx *= 0.965;
            particle.vy = particle.vy * 0.965 + 0.055;
            particle.life -= 0.035;
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
        drawParticles(graphics);
        drawAim(graphics);
        drawFrog(graphics);
        drawProjectiles(graphics);
        drawHud(graphics);
    }

    private void drawBackground(GraphicsContext graphics) {
        graphics.setGlobalAlpha(1.0);
        graphics.setFill(new LinearGradient(0.0, 0.0, 0.0, 1.0, true,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#071912")),
                new Stop(0.55, Color.web("#10261b")),
                new Stop(1.0, Color.web("#06100c"))));
        graphics.fillRect(0.0, 0.0, width, height);

        graphics.setStroke(Color.color(0.55, 0.76, 0.39, 0.075));
        graphics.setLineWidth(1.0);
        double tile = Math.max(48.0, Math.min(width, height) / 12.0);
        for (double x = -height; x < width + height; x += tile) {
            graphics.strokeLine(x, 0.0, x - height, height);
            graphics.strokeLine(x, 0.0, x + height, height);
        }

        graphics.setFill(Color.color(0.02, 0.05, 0.03, 0.38));
        graphics.fillOval(frogX - width * 0.24, frogY - height * 0.30,
                width * 0.48, height * 0.60);
    }

    private void drawTrack(GraphicsContext graphics) {
        strokePath(graphics, 48.0, Color.color(0.0, 0.0, 0.0, 0.58));
        strokePath(graphics, 40.0, Color.web("#75633c"));
        strokePath(graphics, 34.0, Color.web("#2f3929"));
        strokePath(graphics, 27.0, Color.web("#131c16"));
        graphics.setLineDashes(7.0, 13.0);
        strokePath(graphics, 1.4, Color.color(0.83, 0.75, 0.40, 0.28));
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
        graphics.setFill(Color.color(0.0, 0.0, 0.0, 0.55));
        graphics.fillOval(end.x - 37.0, end.y - 32.0, 74.0, 64.0);
        graphics.setFill(Color.web("#8b7a48"));
        graphics.fillOval(end.x - 30.0, end.y - 27.0, 60.0, 54.0);
        graphics.setFill(Color.web("#1a2118"));
        graphics.fillOval(end.x - 22.0, end.y - 18.0, 44.0, 38.0);
        graphics.setFill(Color.web("#d8c46b"));
        graphics.fillOval(end.x - 16.0, end.y - 10.0, 10.0, 9.0);
        graphics.fillOval(end.x + 6.0, end.y - 10.0, 10.0, 9.0);
        graphics.setStroke(Color.web("#d8c46b"));
        graphics.setLineWidth(3.0);
        graphics.strokeArc(end.x - 13.0, end.y + 2.0, 26.0, 16.0,
                200.0, 140.0, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawChain(GraphicsContext graphics) {
        for (ChainBall ball : chain) {
            if (ball.distance < -BALL_RADIUS * 2.0 || ball.distance > pathLength) {
                continue;
            }
            PathPoint point = pointAtDistance(ball.distance);
            drawBall(graphics, point.x, point.y, BALL_RADIUS, ball.color, 1.0);
        }
    }

    private void drawBall(GraphicsContext graphics, double x, double y,
                          double radius, int colorIndex, double alpha) {
        Color color = PALETTE[Math.floorMod(colorIndex, PALETTE.length)];
        graphics.setGlobalAlpha(alpha);
        graphics.setFill(Color.color(0.0, 0.0, 0.0, 0.42));
        graphics.fillOval(x - radius + 3.0, y - radius + 5.0,
                radius * 2.0, radius * 2.0);
        graphics.setFill(color.deriveColor(0.0, 0.90, 0.84, 1.0));
        graphics.fillOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        graphics.setStroke(color.deriveColor(0.0, 1.0, 0.48, 1.0));
        graphics.setLineWidth(2.0);
        graphics.strokeOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        graphics.setFill(Color.color(1.0, 1.0, 1.0, 0.58));
        graphics.fillOval(x - radius * 0.48, y - radius * 0.50,
                radius * 0.55, radius * 0.42);
        graphics.setGlobalAlpha(1.0);
    }

    private void drawAim(GraphicsContext graphics) {
        if (!handDetected || over) {
            return;
        }
        double cos = Math.cos(aimAngle);
        double sin = Math.sin(aimAngle);
        graphics.setStroke(Color.color(0.78, 1.0, 0.50, 0.42));
        graphics.setLineWidth(1.5);
        graphics.setLineDashes(7.0, 8.0);
        graphics.strokeLine(frogX + cos * 42.0, frogY + sin * 42.0,
                aimX, aimY);
        graphics.setLineDashes();
        graphics.setStroke(Color.web("#d9ff88"));
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
    }

    private void drawFrog(GraphicsContext graphics) {
        graphics.save();
        graphics.translate(frogX, frogY);
        graphics.rotate(Math.toDegrees(aimAngle) + 90.0);

        graphics.setFill(Color.color(0.0, 0.0, 0.0, 0.50));
        graphics.fillOval(-39.0, -34.0, 78.0, 82.0);
        graphics.setFill(Color.web("#3f7f46"));
        graphics.fillOval(-34.0, -40.0, 68.0, 78.0);
        graphics.setStroke(Color.web("#b7d66d"));
        graphics.setLineWidth(3.0);
        graphics.strokeOval(-34.0, -40.0, 68.0, 78.0);

        graphics.setFill(Color.web("#7faf57"));
        graphics.fillOval(-31.0, -48.0, 24.0, 27.0);
        graphics.fillOval(7.0, -48.0, 24.0, 27.0);
        graphics.setFill(Color.web("#f5f5d7"));
        graphics.fillOval(-26.0, -43.0, 14.0, 16.0);
        graphics.fillOval(12.0, -43.0, 14.0, 16.0);
        graphics.setFill(Color.web("#172217"));
        graphics.fillOval(-21.5, -38.5, 6.0, 8.0);
        graphics.fillOval(15.5, -38.5, 6.0, 8.0);

        drawBall(graphics, 0.0, -29.0, 12.5, currentColor, 1.0);
        graphics.restore();
    }

    private void drawProjectiles(GraphicsContext graphics) {
        for (Projectile projectile : projectiles) {
            drawBall(graphics, projectile.x, projectile.y,
                    PROJECTILE_RADIUS, projectile.color, 1.0);
        }
    }

    private void drawParticles(GraphicsContext graphics) {
        for (BurstParticle particle : particles) {
            graphics.setGlobalAlpha(Math.max(0.0, particle.life));
            graphics.setFill(particle.color);
            graphics.fillOval(particle.x - particle.radius,
                    particle.y - particle.radius,
                    particle.radius * 2.0, particle.radius * 2.0);
        }
        graphics.setGlobalAlpha(1.0);
    }

    private void drawHud(GraphicsContext graphics) {
        graphics.setTextAlign(TextAlignment.LEFT);
        graphics.setFill(Color.color(0.015, 0.055, 0.035, 0.88));
        graphics.fillRoundRect(22.0, 18.0, 245.0, 78.0, 20.0, 20.0);
        graphics.setStroke(Color.color(0.74, 0.90, 0.37, 0.48));
        graphics.setLineWidth(1.2);
        graphics.strokeRoundRect(22.0, 18.0, 245.0, 78.0, 20.0, 20.0);
        graphics.setFill(Color.web("#e9ffc0"));
        graphics.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 22.0));
        graphics.fillText("🐸  祖玛", 41.0, 49.0);
        graphics.setFill(Color.web("#9fb790"));
        graphics.setFont(Font.font("Microsoft YaHei UI", 13.0));
        graphics.fillText("稳定握拳发射一颗  ·  发射后自动换色", 41.0, 76.0);

        graphics.setTextAlign(TextAlignment.RIGHT);
        graphics.setFill(Color.web("#f7ffe8"));
        graphics.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 22.0));
        graphics.fillText("得分  " + score, width - 28.0, 45.0);
        graphics.setFill(Color.web("#a7bb9a"));
        graphics.setFont(Font.font("Microsoft YaHei UI", 13.0));
        graphics.fillText(targetScore > 0
                ? "目标  " + targetScore : "无尽模式", width - 28.0, 70.0);

        if (comboDisplayFrames > 0 && combo > 1) {
            double alpha = Math.min(1.0, comboDisplayFrames / 18.0);
            graphics.setGlobalAlpha(alpha);
            graphics.setTextAlign(TextAlignment.CENTER);
            graphics.setFill(Color.web("#f5d547"));
            graphics.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 25.0));
            graphics.fillText("连锁 × " + combo, width * 0.5, 62.0);
            graphics.setGlobalAlpha(1.0);
        }

        graphics.setTextAlign(TextAlignment.RIGHT);
        graphics.setFill(Color.color(0.88, 1.0, 0.70, 0.72));
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

    private record Vec(double x, double y) {
    }

    private static final class ChainBall {
        private double distance;
        private final int color;

        private ChainBall(double distance, int color) {
            this.distance = distance;
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

    private static final class BurstParticle {
        private double x;
        private double y;
        private double vx;
        private double vy;
        private double life;
        private final double radius;
        private final Color color;

        private BurstParticle(double x, double y, double vx, double vy,
                              double life, double radius, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.radius = radius;
            this.color = color;
        }
    }
}
