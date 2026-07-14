package com.gesturegame.ui;

import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Login screen: a JavaFX port of the supplied SpiralAnimation.
 * The only login action is a recognized fist (CONFIRM).
 */
public class LoginController {

    private static final double CHANGE_EVENT_TIME = 0.32;
    private static final double CAMERA_Z = -400.0;
    private static final double CAMERA_TRAVEL_DISTANCE = 3400.0;
    private static final double START_DOT_Y_OFFSET = 28.0;
    private static final double VIEW_ZOOM = 100.0;
    private static final int NUMBER_OF_STARS = 5000;
    private static final int TRAIL_LENGTH = 80;
    private static final double LOOP_SECONDS = 15.0;

    @FXML
    private StackPane rootPane;

    @FXML
    private Canvas starCanvas;

    private final double[] dx = new double[NUMBER_OF_STARS];
    private final double[] dy = new double[NUMBER_OF_STARS];
    private final double[] spiralLocation = new double[NUMBER_OF_STARS];
    private final double[] strokeWeightFactor = new double[NUMBER_OF_STARS];
    private final double[] z = new double[NUMBER_OF_STARS];
    private final double[] angle = new double[NUMBER_OF_STARS];
    private final double[] distance = new double[NUMBER_OF_STARS];
    private final double[] rotationDirection = new double[NUMBER_OF_STARS];
    private final double[] expansionRate = new double[NUMBER_OF_STARS];
    private final double[] finalScale = new double[NUMBER_OF_STARS];

    private AppStateManager appStateManager;
    private AnimationTimer animationTimer;
    private long animationStartNanos;
    private boolean entering;

    @FXML
    public void initialize() {
        starCanvas.widthProperty().bind(rootPane.widthProperty());
        starCanvas.heightProperty().bind(rootPane.heightProperty());
        createStars();
    }

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
        Platform.runLater(this::startAnimation);
    }

    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        if (command != GestureCommand.CONFIRM || entering) {
            return;
        }

        entering = true;
        Platform.runLater(() -> {
            if (!AppStateManager.STATE_LOGIN.equals(AppStateManager.getInstance().getCurrentState())) {
                return;
            }
            if (animationTimer != null) {
                animationTimer.stop();
            }
            if (appStateManager != null) {
                appStateManager.markAuthenticated();
                appStateManager.switchState(AppStateManager.STATE_LOBBY);
            }
        });
    }

    /** Login no longer displays or decodes camera frames. */
    public void updateCameraStream(String base64Image) {
        // Kept for compatibility with the existing stream API.
    }

    /** Login no longer displays or decodes camera frames. */
    public void updateCameraImage(Image image) {
        // Kept for compatibility with the existing local debug API.
    }

    private void createStars() {
        SeededRandom random = new SeededRandom(1234L);
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            angle[i] = random.next() * Math.PI * 2.0;
            distance[i] = 30.0 * random.next() + 15.0;
            rotationDirection[i] = random.next() > 0.5 ? 1.0 : -1.0;
            expansionRate[i] = 1.2 + random.next() * 0.8;
            finalScale[i] = 0.7 + random.next() * 0.6;

            dx[i] = distance[i] * Math.cos(angle[i]);
            dy[i] = distance[i] * Math.sin(angle[i]);
            spiralLocation[i] = (1.0 - Math.pow(1.0 - random.next(), 3.0)) / 1.3;
            z[i] = random(-200.0, CAMERA_TRAVEL_DISTANCE + CAMERA_Z, random);
            z[i] = lerp(z[i], CAMERA_TRAVEL_DISTANCE / 2.0, 0.3 * spiralLocation[i]);
            strokeWeightFactor[i] = Math.pow(random.next(), 2.0);
        }
    }

    private void startAnimation() {
        if (animationTimer != null) {
            return;
        }
        animationStartNanos = System.nanoTime();
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double elapsedSeconds = (now - animationStartNanos) / 1_000_000_000.0;
                render((elapsedSeconds % LOOP_SECONDS) / LOOP_SECONDS);
            }
        };
        animationTimer.start();
    }

    private void render(double time) {
        double width = starCanvas.getWidth();
        double height = starCanvas.getHeight();
        if (width < 2.0 || height < 2.0) {
            return;
        }

        GraphicsContext ctx = starCanvas.getGraphicsContext2D();
        ctx.setGlobalAlpha(1.0);
        ctx.setFill(Color.BLACK);
        ctx.fillRect(0.0, 0.0, width, height);

        double size = Math.max(width, height);
        double t1 = constrain(map(time, 0.0, CHANGE_EVENT_TIME + 0.25, 0.0, 1.0), 0.0, 1.0);
        double t2 = constrain(map(time, CHANGE_EVENT_TIME, 1.0, 0.0, 1.0), 0.0, 1.0);

        ctx.save();
        ctx.translate(width / 2.0, height / 2.0);
        // The source renders to a square canvas and stretches it to the viewport.
        ctx.scale(width / size, height / size);
        ctx.rotate(Math.toDegrees(-Math.PI * ease(t2, 2.7)));

        drawTrail(ctx, t1, time);

        ctx.setFill(Color.WHITE);
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            drawStar(ctx, i, t1, t2);
        }

        drawStartDot(ctx, time, t2);
        ctx.restore();
    }

    private void drawTrail(GraphicsContext ctx, double t1, double time) {
        ctx.setFill(Color.WHITE);
        for (int i = 0; i < TRAIL_LENGTH; i++) {
            double factor = map(i, 0.0, TRAIL_LENGTH, 1.1, 0.1);
            double strokeWidth = (1.3 * (1.0 - t1) + 3.0 * Math.sin(Math.PI * t1)) * factor;
            double pathTime = t1 - 0.00015 * i;
            double x = spiralX(pathTime);
            double y = spiralY(pathTime);

            double rotateProgress = Math.sin(time * Math.PI * 2.0) * 0.5 + 0.5;
            double[] rotated = rotate(x, y, x + 5.0, y + 5.0, rotateProgress, i % 2 == 0);
            ctx.fillOval(rotated[0] - strokeWidth / 2.0,
                    rotated[1] - strokeWidth / 2.0, strokeWidth, strokeWidth);
        }
    }

    private void drawStar(GraphicsContext ctx, int i, double progress, double t2) {
        double q = progress - spiralLocation[i];
        if (q <= 0.0) {
            return;
        }

        double displacementProgress = constrain(4.0 * q, 0.0, 1.0);
        double linearEasing = displacementProgress;
        double elasticEasing = easeOutElastic(displacementProgress);
        double powerEasing = Math.pow(displacementProgress, 2.0);
        double easing;
        if (displacementProgress < 0.3) {
            easing = lerp(linearEasing, powerEasing, displacementProgress / 0.3);
        } else if (displacementProgress < 0.7) {
            easing = lerp(powerEasing, elasticEasing, (displacementProgress - 0.3) / 0.4);
        } else {
            easing = elasticEasing;
        }

        double pathX = spiralX(spiralLocation[i]);
        double pathY = spiralY(spiralLocation[i]);
        double screenX;
        double screenY;
        if (displacementProgress < 0.3) {
            screenX = lerp(pathX, pathX + dx[i] * 0.3, easing / 0.3);
            screenY = lerp(pathY, pathY + dy[i] * 0.3, easing / 0.3);
        } else if (displacementProgress < 0.7) {
            double midProgress = (displacementProgress - 0.3) / 0.4;
            double curveStrength = Math.sin(midProgress * Math.PI) * rotationDirection[i] * 1.5;
            double baseX = pathX + dx[i] * 0.3;
            double baseY = pathY + dy[i] * 0.3;
            double targetX = pathX + dx[i] * 0.7;
            double targetY = pathY + dy[i] * 0.7;
            double perpendicularX = -dy[i] * 0.4 * curveStrength;
            double perpendicularY = dx[i] * 0.4 * curveStrength;
            screenX = lerp(baseX, targetX, midProgress) + perpendicularX * midProgress;
            screenY = lerp(baseY, targetY, midProgress) + perpendicularY * midProgress;
        } else {
            double endProgress = (displacementProgress - 0.7) / 0.3;
            double baseX = pathX + dx[i] * 0.7;
            double baseY = pathY + dy[i] * 0.7;
            double targetDistance = distance[i] * expansionRate[i] * 1.5;
            double spiralAngle = angle[i] + 1.2 * rotationDirection[i] * endProgress * Math.PI;
            double targetX = pathX + targetDistance * Math.cos(spiralAngle);
            double targetY = pathY + targetDistance * Math.sin(spiralAngle);
            screenX = lerp(baseX, targetX, endProgress);
            screenY = lerp(baseY, targetY, endProgress);
        }

        double vx = (z[i] - CAMERA_Z) * screenX / VIEW_ZOOM;
        double vy = (z[i] - CAMERA_Z) * screenY / VIEW_ZOOM;

        double sizeMultiplier;
        if (displacementProgress < 0.6) {
            sizeMultiplier = 1.0 + displacementProgress * 0.2;
        } else {
            double scaleProgress = (displacementProgress - 0.6) / 0.4;
            sizeMultiplier = 1.2 * (1.0 - scaleProgress) + finalScale[i] * scaleProgress;
        }
        double dotSize = 8.5 * strokeWeightFactor[i] * sizeMultiplier;
        showProjectedDot(ctx, vx, vy, z[i], dotSize, t2);
    }

    private void drawStartDot(GraphicsContext ctx, double time, double t2) {
        if (time <= CHANGE_EVENT_TIME) {
            return;
        }
        double y = CAMERA_Z * START_DOT_Y_OFFSET / VIEW_ZOOM;
        showProjectedDot(ctx, 0.0, y, CAMERA_TRAVEL_DISTANCE, 2.5, t2);
    }

    private void showProjectedDot(GraphicsContext ctx, double x, double y, double pointZ,
                                  double sizeFactor, double t2) {
        double newCameraZ = CAMERA_Z
                + ease(Math.pow(t2, 1.2), 1.8) * CAMERA_TRAVEL_DISTANCE;
        if (pointZ <= newCameraZ) {
            return;
        }

        double depth = pointZ - newCameraZ;
        double projectedX = VIEW_ZOOM * x / depth;
        double projectedY = VIEW_ZOOM * y / depth;
        // Kept for formula parity with the supplied Canvas implementation.
        ctx.setLineWidth(400.0 * sizeFactor / depth);
        ctx.fillOval(projectedX - 0.5, projectedY - 0.5, 1.0, 1.0);
    }

    private static double[] rotate(double x1, double y1, double x2, double y2,
                                   double progress, boolean orientation) {
        double middleX = (x1 + x2) / 2.0;
        double middleY = (y1 + y2) / 2.0;
        double deltaX = x1 - middleX;
        double deltaY = y1 - middleY;
        double angle = Math.atan2(deltaY, deltaX);
        double direction = orientation ? -1.0 : 1.0;
        double radius = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        double bounce = Math.sin(progress * Math.PI) * 0.05 * (1.0 - progress);
        double rotation = direction * Math.PI * easeOutElastic(progress);
        return new double[] {
                middleX + radius * (1.0 + bounce) * Math.cos(angle + rotation),
                middleY + radius * (1.0 + bounce) * Math.sin(angle + rotation)
        };
    }

    private static double spiralX(double value) {
        double p = ease(constrain(1.2 * value, 0.0, 1.0), 1.8);
        double theta = 2.0 * Math.PI * 6.0 * Math.sqrt(p);
        return 170.0 * Math.sqrt(p) * Math.cos(theta);
    }

    private static double spiralY(double value) {
        double p = ease(constrain(1.2 * value, 0.0, 1.0), 1.8);
        double theta = 2.0 * Math.PI * 6.0 * Math.sqrt(p);
        return 170.0 * Math.sqrt(p) * Math.sin(theta) + START_DOT_Y_OFFSET;
    }

    private static double ease(double p, double power) {
        if (p < 0.5) {
            return 0.5 * Math.pow(2.0 * p, power);
        }
        return 1.0 - 0.5 * Math.pow(2.0 * (1.0 - p), power);
    }

    private static double easeOutElastic(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        double c4 = 2.0 * Math.PI / 4.5;
        return Math.pow(2.0, -8.0 * value)
                * Math.sin((value * 8.0 - 0.75) * c4) + 1.0;
    }

    private static double map(double value, double start1, double stop1,
                              double start2, double stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    private static double constrain(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private static double lerp(double start, double end, double progress) {
        return start * (1.0 - progress) + end * progress;
    }

    private static double random(double min, double max, SeededRandom random) {
        return min + random.next() * (max - min);
    }

    /** Matches the seeded Math.random replacement in the supplied code. */
    private static final class SeededRandom {
        private long seed;

        private SeededRandom(long seed) {
            this.seed = seed;
        }

        private double next() {
            seed = (seed * 9301L + 49297L) % 233280L;
            return (double) seed / 233280.0;
        }
    }
}
