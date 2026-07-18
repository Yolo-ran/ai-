package com.gesturegame.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Random;

/**
 * Draws the lobby's time-travel rays outside WebView.
 *
 * <p>Animating a full-window HTML canvas makes JavaFX WebView continuously
 * replace its backing RTTexture. On the Windows D3D pipeline that can produce
 * intermittent black frames. A single native Canvas keeps the same look while
 * allowing the complete lobby to stay on Prism's stable native render path.</p>
 */
final class LobbyRayRenderer {

    static final double DESIGN_WIDTH = 1280.0;
    static final double DESIGN_HEIGHT = 720.0;

    private static final int PARTICLE_COUNT = 340;
    private static final long FRAME_INTERVAL_NS = 1_000_000_000L / 24L;
    private static final Color[] COLORS = {
            Color.web("#fff200"),
            Color.web("#a855f7"),
            Color.web("#f43f5e"),
            Color.web("#22c55e")
    };
    private static final double[] ALPHA_LEVELS = {0.22, 0.42, 0.66, 0.92};
    private static final double[] THICKNESS_LEVELS = {0.82, 1.28};
    private static final int GROUP_COUNT = COLORS.length
            * ALPHA_LEVELS.length * THICKNESS_LEVELS.length;

    private final Canvas canvas;
    private final GraphicsContext graphics;
    private final Particle[] particles = new Particle[PARTICLE_COUNT];
    private final SegmentGroup[] groups = new SegmentGroup[GROUP_COUNT];
    private final AnimationTimer timer;

    private long lastFrameNs;
    private boolean running;

    LobbyRayRenderer(Canvas canvas) {
        this.canvas = canvas;
        this.graphics = canvas.getGraphicsContext2D();
        canvas.setWidth(DESIGN_WIDTH);
        canvas.setHeight(DESIGN_HEIGHT);
        canvas.setMouseTransparent(true);

        createGroups();
        createParticles();
        paintBlack();

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNs != 0L && now - lastFrameNs < FRAME_INTERVAL_NS) {
                    return;
                }
                lastFrameNs = now;
                draw(now * 1.0e-9);
            }
        };
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        lastFrameNs = 0L;
        timer.start();
    }

    void stop() {
        if (!running) {
            return;
        }
        running = false;
        timer.stop();
        lastFrameNs = 0L;
    }

    private void createGroups() {
        int index = 0;
        for (Color color : COLORS) {
            for (double alpha : ALPHA_LEVELS) {
                for (double thickness : THICKNESS_LEVELS) {
                    groups[index++] = new SegmentGroup(color, alpha, thickness);
                }
            }
        }
    }

    private void createParticles() {
        Random random = new Random(0x51A7F13DL);
        for (int index = 0; index < particles.length; index++) {
            double pitch = random(random, -Math.PI, Math.PI);
            double angle = random(random, 0.0, Math.PI * 2.0);
            particles[index] = new Particle(
                    Math.cos(angle),
                    Math.sin(angle),
                    random(random, 1.0, 5.0),
                    random(random, 0.0, 5.0),
                    random.nextInt(COLORS.length),
                    0.2 + Math.abs(Math.cos(pitch)) * 0.8,
                    random(random, 0.44, 0.98),
                    random.nextDouble() < 0.68 ? 0 : 1);
        }
    }

    private void draw(double seconds) {
        paintBlack();
        for (SegmentGroup group : groups) {
            group.clear();
        }

        double centerX = DESIGN_WIDTH * 0.5;
        double centerY = DESIGN_HEIGHT * 0.5;
        double baseLength = Math.min(DESIGN_WIDTH, DESIGN_HEIGHT) * 0.4;

        for (Particle particle : particles) {
            double progress = ((seconds + particle.phase) % particle.duration)
                    / particle.duration;
            double scale = 2.0 * (1.0 - progress);
            double length = baseLength * scale * (0.72 + particle.depth * 0.4);
            double alpha = (progress < 0.2 ? progress / 0.2 : 1.0)
                    * particle.brightness;
            if (length < 0.6 || alpha < 0.04) {
                continue;
            }

            int alphaIndex = Math.min(ALPHA_LEVELS.length - 1,
                    Math.max(0, (int) Math.floor(alpha * ALPHA_LEVELS.length)));
            int groupIndex = particle.colorIndex
                    * ALPHA_LEVELS.length * THICKNESS_LEVELS.length
                    + alphaIndex * THICKNESS_LEVELS.length
                    + particle.thicknessIndex;
            double innerLength = length * 0.46;
            groups[groupIndex].add(
                    centerX + particle.dx * innerLength,
                    centerY + particle.dy * innerLength,
                    centerX + particle.dx * length,
                    centerY + particle.dy * length);
        }

        for (SegmentGroup group : groups) {
            group.stroke(graphics);
        }
        graphics.setGlobalAlpha(1.0);
    }

    private void paintBlack() {
        graphics.setGlobalAlpha(1.0);
        graphics.setFill(Color.BLACK);
        graphics.fillRect(0.0, 0.0, DESIGN_WIDTH, DESIGN_HEIGHT);
    }

    private static double random(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private record Particle(double dx, double dy, double duration, double phase,
                            int colorIndex, double depth, double brightness,
                            int thicknessIndex) {
    }

    private static final class SegmentGroup {
        private final Color color;
        private final double alpha;
        private final double thickness;
        private final double[] coordinates = new double[PARTICLE_COUNT * 4];
        private int size;

        private SegmentGroup(Color color, double alpha, double thickness) {
            this.color = color;
            this.alpha = alpha;
            this.thickness = thickness;
        }

        private void clear() {
            size = 0;
        }

        private void add(double x1, double y1, double x2, double y2) {
            coordinates[size++] = x1;
            coordinates[size++] = y1;
            coordinates[size++] = x2;
            coordinates[size++] = y2;
        }

        private void stroke(GraphicsContext graphics) {
            if (size == 0) {
                return;
            }
            graphics.setGlobalAlpha(alpha);
            graphics.setStroke(color);
            graphics.setLineWidth(thickness);
            graphics.beginPath();
            for (int index = 0; index < size; index += 4) {
                graphics.moveTo(coordinates[index], coordinates[index + 1]);
                graphics.lineTo(coordinates[index + 2], coordinates[index + 3]);
            }
            graphics.stroke();
        }
    }
}
