package com.gesturegame.ui;

import javafx.animation.AnimationTimer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/** GPU-friendly native card carousel used by the lobby. */
final class LobbyCardDeck {

    static final double DESIGN_WIDTH = 1280.0;
    static final double DESIGN_HEIGHT = 720.0;

    private static final double CARD_WIDTH = 558.0;
    private static final double CARD_HEIGHT = 334.8;
    private static final double CARD_SPACING = CARD_WIDTH * 0.47;
    private static final long TRANSITION_NS = 690_000_000L;
    private static final List<CardData> CARDS = List.of(
            new CardData("接水果", "Catch the rhythm of falling color", "01", "#06b6d4", "#0f766e"),
            new CardData("猜拳对决", "Read the moment. Make your move.", "02", "#f43f5e", "#7c3aed"),
            new CardData("戳泡泡", "Aim softly. Chain every touch.", "03", "#84cc16", "#0891b2"),
            new CardData("塔罗牌", "Three cards reveal one direction", "04", "#d8b4fe", "#4338ca"),
            new CardData("水果忍者", "Draw the blade through color", "05", "#f97316", "#e11d48"),
            new CardData("节奏大师", "Move precisely inside the beat", "06", "#a78bfa", "#0ea5e9"),
            new CardData("星际突击", "A new path generated every run", "07", "#38bdf8", "#1d4ed8")
    );

    private final Canvas canvas;
    private final GraphicsContext graphics;
    private final Image[] cardImages = new Image[CARDS.size()];
    private final CardPose[] poses = new CardPose[CARDS.size()];
    private final CardPose[] starts = new CardPose[CARDS.size()];
    private final CardPose[] targets = new CardPose[CARDS.size()];
    private final IntConsumer selectHandler;
    private final IntConsumer navigationHandler;
    private final Runnable launchHandler;
    private final AnimationTimer timer;

    private int activeIndex;
    private long transitionStarted;
    private long lastWheelTime;
    private double pressX;
    private double dragX;
    private boolean dragging;
    private boolean active;
    private boolean handVisible;
    private double handX = DESIGN_WIDTH * 0.5;
    private double handY = DESIGN_HEIGHT * 0.5;
    private double confirmProgress;

    LobbyCardDeck(Canvas canvas, IntConsumer selectHandler,
                  IntConsumer navigationHandler, Runnable launchHandler) {
        this.canvas = canvas;
        this.graphics = canvas.getGraphicsContext2D();
        this.selectHandler = selectHandler;
        this.navigationHandler = navigationHandler;
        this.launchHandler = launchHandler;
        canvas.setWidth(DESIGN_WIDTH);
        canvas.setHeight(DESIGN_HEIGHT);
        canvas.setFocusTraversable(true);

        for (int index = 0; index < CARDS.size(); index++) {
            cardImages[index] = createCardImage(CARDS.get(index));
            poses[index] = poseFor(index, activeIndex);
            starts[index] = poses[index].copy();
            targets[index] = poses[index].copy();
        }
        installInputHandlers();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double progress = Math.min(1.0,
                        (double) (now - transitionStarted) / TRANSITION_NS);
                double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
                for (int index = 0; index < poses.length; index++) {
                    poses[index].setInterpolated(starts[index], targets[index], eased);
                }
                draw();
                if (progress >= 1.0) {
                    stopAnimation();
                }
            }
        };
        draw();
    }

    void activate(int index) {
        active = true;
        setActive(index, false);
        canvas.requestFocus();
        draw();
    }

    void pause() {
        active = false;
        stopAnimation();
    }

    void setActive(int index) {
        setActive(index, true);
    }

    void setGesture(boolean visible, double normalizedX, double normalizedY, double progress) {
        handVisible = visible;
        handX = clamp(normalizedX, 0.0, 1.0) * DESIGN_WIDTH;
        handY = clamp(normalizedY, 0.0, 1.0) * DESIGN_HEIGHT;
        confirmProgress = clamp(progress, 0.0, 1.0);
        if (active) {
            draw();
        }
    }

    private void setActive(int index, boolean animate) {
        int wrapped = Math.floorMod(index, CARDS.size());
        if (wrapped == activeIndex && animate) {
            return;
        }
        activeIndex = wrapped;
        for (int card = 0; card < poses.length; card++) {
            starts[card] = poses[card].copy();
            targets[card] = poseFor(card, activeIndex);
            if (!animate) {
                poses[card] = targets[card].copy();
            }
        }
        if (animate && active) {
            transitionStarted = System.nanoTime();
            timer.start();
        } else {
            stopAnimation();
            draw();
        }
    }

    private void stopAnimation() {
        timer.stop();
        for (int index = 0; index < poses.length; index++) {
            if (targets[index] != null) {
                poses[index] = targets[index].copy();
            }
        }
        if (active) {
            draw();
        }
    }

    private void installInputHandlers() {
        canvas.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.LEFT) {
                navigationHandler.accept(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                navigationHandler.accept(1);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                launchHandler.run();
                event.consume();
            }
        });
        canvas.setOnScroll(event -> {
            long now = System.currentTimeMillis();
            if (now - lastWheelTime < 230L || Math.abs(event.getDeltaY()) < 5.0) {
                return;
            }
            lastWheelTime = now;
            navigationHandler.accept(event.getDeltaY() > 0.0 ? -1 : 1);
            event.consume();
        });
        canvas.setOnMousePressed(event -> {
            canvas.requestFocus();
            pressX = event.getX();
            dragX = 0.0;
            dragging = true;
            draw();
        });
        canvas.setOnMouseDragged(event -> {
            if (!dragging) {
                return;
            }
            dragX = event.getX() - pressX;
            draw();
        });
        canvas.setOnMouseReleased(event -> {
            if (!dragging) {
                return;
            }
            dragging = false;
            double releasedDrag = dragX;
            dragX = 0.0;
            if (releasedDrag > 95.0) {
                navigationHandler.accept(-1);
            } else if (releasedDrag < -95.0) {
                navigationHandler.accept(1);
            } else if (event.getX() < DESIGN_WIDTH * 0.34) {
                selectHandler.accept(Math.floorMod(activeIndex - 1, CARDS.size()));
            } else if (event.getX() > DESIGN_WIDTH * 0.66) {
                selectHandler.accept(Math.floorMod(activeIndex + 1, CARDS.size()));
            }
            draw();
        });
    }

    private void draw() {
        graphics.clearRect(0.0, 0.0, DESIGN_WIDTH, DESIGN_HEIGHT);
        drawVignette();

        Integer[] order = new Integer[CARDS.size()];
        for (int index = 0; index < order.length; index++) {
            order[index] = index;
        }
        Arrays.sort(order, (left, right) -> Double.compare(
                poses[right].distance, poses[left].distance));
        for (int index : order) {
            drawCard(index, poses[index]);
        }
        drawDots();
        drawCursor();
    }

    private void drawVignette() {
        graphics.save();
        graphics.translate(DESIGN_WIDTH * 0.5, DESIGN_HEIGHT * 0.51);
        graphics.scale(1.75, 1.0);
        graphics.setFill(new RadialGradient(0.0, 0.0, 0.0, 0.0, 300.0,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.0, 0.0, 0.0, 0.73)),
                new Stop(0.55, Color.color(0.0, 0.0, 0.0, 0.42)),
                new Stop(1.0, Color.TRANSPARENT)));
        graphics.fillOval(-300.0, -300.0, 600.0, 600.0);
        graphics.restore();
    }

    private void drawCard(int index, CardPose pose) {
        double opacity = pose.opacity;
        if (opacity <= 0.005) {
            return;
        }
        graphics.save();
        graphics.setGlobalAlpha(opacity);
        graphics.translate(pose.x + (index == activeIndex ? dragX : 0.0), pose.y);
        graphics.rotate(pose.angle);
        graphics.scale(pose.scale, pose.scale);

        graphics.setFill(Color.color(0.0, 0.0, 0.0, 0.48 * opacity));
        graphics.fillRoundRect(-CARD_WIDTH * 0.5 - 12.0, -CARD_HEIGHT * 0.5 + 24.0,
                CARD_WIDTH + 24.0, CARD_HEIGHT + 26.0, 34.0, 34.0);
        graphics.drawImage(cardImages[index], -CARD_WIDTH * 0.5, -CARD_HEIGHT * 0.5,
                CARD_WIDTH, CARD_HEIGHT);
        if (index == activeIndex) {
            graphics.setGlobalAlpha(Math.min(1.0, opacity * 0.78));
            graphics.setStroke(Color.web(CARDS.get(index).accent));
            graphics.setLineWidth(1.25);
            graphics.strokeRoundRect(-CARD_WIDTH * 0.5, -CARD_HEIGHT * 0.5,
                    CARD_WIDTH, CARD_HEIGHT, 22.0, 22.0);
        }
        graphics.restore();
        graphics.setGlobalAlpha(1.0);
    }

    private void drawDots() {
        double gap = 16.0;
        double total = gap * (CARDS.size() - 1);
        double startX = DESIGN_WIDTH * 0.5 - total * 0.5;
        double y = DESIGN_HEIGHT - 25.0;
        for (int index = 0; index < CARDS.size(); index++) {
            boolean selected = index == activeIndex;
            graphics.setFill(selected ? Color.color(1.0, 1.0, 1.0, 0.94)
                    : Color.color(1.0, 1.0, 1.0, 0.31));
            if (selected) {
                graphics.fillRoundRect(startX + index * gap - 8.0, y - 3.5,
                        24.0, 7.0, 7.0, 7.0);
            } else {
                graphics.fillOval(startX + index * gap, y - 3.5, 7.0, 7.0);
            }
        }
    }

    private void drawCursor() {
        if (!handVisible) {
            return;
        }
        graphics.setFill(Color.WHITE);
        graphics.fillOval(handX - 3.5, handY - 3.5, 7.0, 7.0);
        graphics.setStroke(Color.color(1.0, 1.0, 1.0, 0.9));
        graphics.setLineWidth(2.0);
        graphics.strokeOval(handX - 9.0, handY - 9.0, 18.0, 18.0);
        if (confirmProgress > 0.001) {
            graphics.setLineWidth(3.0);
            graphics.strokeArc(handX - 21.0, handY - 21.0, 42.0, 42.0,
                    90.0, -360.0 * confirmProgress, javafx.scene.shape.ArcType.OPEN);
        }
    }

    private static CardPose poseFor(int cardIndex, int activeIndex) {
        int offset = signedOffset(cardIndex, activeIndex);
        double distance = Math.abs(offset);
        if (distance > 2.0) {
            double sign = offset < 0 ? -1.0 : 1.0;
            return new CardPose(DESIGN_WIDTH * 0.5 + sign * DESIGN_WIDTH * 0.64,
                    DESIGN_HEIGHT * 0.5 + 52.0, sign * 50.0, 0.82, 0.0, distance);
        }
        return new CardPose(
                DESIGN_WIDTH * 0.5 + offset * CARD_SPACING,
                DESIGN_HEIGHT * 0.5 + distance * 18.0 + (distance == 0.0 ? -22.0 : 0.0),
                offset * 23.0,
                distance == 0.0 ? 1.035 : 0.94,
                distance == 0.0 ? 1.0 : distance == 1.0 ? 0.9 : 0.66,
                distance);
    }

    private static int signedOffset(int index, int active) {
        int raw = index - active;
        int alternate = raw > 0 ? raw - CARDS.size() : raw + CARDS.size();
        return Math.abs(alternate) < Math.abs(raw) ? alternate : raw;
    }

    private static Image createCardImage(CardData card) {
        int width = 1116;
        int height = 670;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            RoundRectangle2D shape = new RoundRectangle2D.Double(2.0, 2.0,
                    width - 4.0, height - 4.0, 44.0, 44.0);
            graphics.setClip(shape);

            java.awt.Color accent = java.awt.Color.decode(card.accent);
            java.awt.Color accent2 = java.awt.Color.decode(card.accent2);
            graphics.setPaint(new LinearGradientPaint(0.0f, 0.0f, width, height,
                    new float[]{0.0f, 0.48f, 1.0f},
                    new java.awt.Color[]{accent2, new java.awt.Color(5, 7, 11), accent}));
            graphics.fillRect(0, 0, width, height);

            graphics.setComposite(AlphaComposite.SrcOver.derive(0.48f));
            graphics.setPaint(new GradientPaint(width * 0.52f, height * 0.08f,
                    withAlpha(accent2, 220), width * 0.82f, height * 0.62f,
                    new java.awt.Color(0, 0, 0, 0)));
            graphics.fillOval((int) (width * 0.34), (int) (-height * 0.16),
                    (int) (width * 0.72), (int) (height * 0.9));
            graphics.setPaint(new GradientPaint(width * 0.18f, height * 0.28f,
                    withAlpha(accent, 205), width * 0.62f, height * 0.68f,
                    new java.awt.Color(0, 0, 0, 0)));
            graphics.fillOval((int) (-width * 0.05), (int) (height * 0.04),
                    (int) (width * 0.72), (int) (height * 0.84));

            graphics.setComposite(AlphaComposite.SrcOver.derive(0.12f));
            graphics.setStroke(new BasicStroke(1.0f));
            graphics.setColor(java.awt.Color.WHITE);
            for (int x = -height; x < width + height; x += 8) {
                graphics.drawLine(x, 0, x - height, height);
            }

            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setPaint(new GradientPaint(0.0f, height * 0.30f,
                    new java.awt.Color(0, 0, 0, 0), 0.0f, height,
                    new java.awt.Color(0, 0, 0, 238)));
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(new java.awt.Color(255, 255, 255, 160));
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.drawRoundRect(3, 3, width - 7, height - 7, 42, 42);

            int left = 55;
            int baseline = height - 92;
            graphics.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 23));
            FontMetrics pillMetrics = graphics.getFontMetrics();
            int pillWidth = pillMetrics.stringWidth(card.number) + 30;
            graphics.setColor(new java.awt.Color(255, 255, 255, 32));
            graphics.fillRoundRect(left, baseline - 98, pillWidth, 38, 38, 38);
            graphics.setColor(new java.awt.Color(255, 255, 255, 185));
            graphics.drawString(card.number, left + 15, baseline - 70);

            graphics.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 57));
            graphics.setColor(java.awt.Color.WHITE);
            graphics.drawString(card.title, left, baseline);
            graphics.setFont(new Font("Segoe UI", Font.PLAIN, 25));
            graphics.setColor(new java.awt.Color(255, 255, 255, 180));
            graphics.drawString(card.subtitle, left, baseline + 48);
        } finally {
            graphics.dispose();
        }
        return SwingFXUtils.toFXImage(image, null);
    }

    private static java.awt.Color withAlpha(java.awt.Color color, int alpha) {
        return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CardData(String title, String subtitle, String number,
                            String accent, String accent2) {
    }

    private static final class CardPose {
        private double x;
        private double y;
        private double angle;
        private double scale;
        private double opacity;
        private double distance;

        private CardPose(double x, double y, double angle, double scale,
                         double opacity, double distance) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.scale = scale;
            this.opacity = opacity;
            this.distance = distance;
        }

        private CardPose copy() {
            return new CardPose(x, y, angle, scale, opacity, distance);
        }

        private void setInterpolated(CardPose from, CardPose to, double amount) {
            x = interpolate(from.x, to.x, amount);
            y = interpolate(from.y, to.y, amount);
            angle = interpolate(from.angle, to.angle, amount);
            scale = interpolate(from.scale, to.scale, amount);
            opacity = interpolate(from.opacity, to.opacity, amount);
            distance = interpolate(from.distance, to.distance, amount);
        }

        private static double interpolate(double from, double to, double amount) {
            return from + (to - from) * amount;
        }
    }
}
