package com.gesturegame.game;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 缓存塔罗静态背景与复杂牌面，避免相同图案在每一帧重复绘制。 */
final class TarotRenderCache {

    private static final int MAX_CARD_IMAGES = 48;

    private final Map<String, WritableImage> cardImages = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WritableImage> eldest) {
            return size() > MAX_CARD_IMAGES;
        }
    };

    private WritableImage backgroundImage;
    private int backgroundWidth;
    private int backgroundHeight;
    private long cardCacheMisses;

    void clear() {
        cardImages.clear();
        backgroundImage = null;
        backgroundWidth = 0;
        backgroundHeight = 0;
        cardCacheMisses = 0;
    }

    void drawBackground(GraphicsContext target, int width, int height,
                        Consumer<GraphicsContext> painter) {
        if (backgroundImage == null || backgroundWidth != width || backgroundHeight != height) {
            backgroundWidth = width;
            backgroundHeight = height;
            backgroundImage = render(width, height, painter);
        }
        target.drawImage(backgroundImage, 0, 0, width, height);
    }

    void drawCard(GraphicsContext target, String key, double x, double y, double width, double height,
                  Consumer<GraphicsContext> painter) {
        int pixelWidth = Math.max(1, (int) Math.ceil(width));
        int pixelHeight = Math.max(1, (int) Math.ceil(height));
        String sizedKey = key + '@' + pixelWidth + 'x' + pixelHeight;
        WritableImage image = cardImages.get(sizedKey);
        if (image == null) {
            image = render(pixelWidth, pixelHeight, painter);
            cardImages.put(sizedKey, image);
            cardCacheMisses++;
        }
        target.drawImage(image, x, y, width, height);
    }

    private WritableImage render(int width, int height, Consumer<GraphicsContext> painter) {
        Canvas canvas = new Canvas(width, height);
        painter.accept(canvas.getGraphicsContext2D());
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return canvas.snapshot(parameters, new WritableImage(width, height));
    }

    int cardImageCount() {
        return cardImages.size();
    }

    long cardCacheMisses() {
        return cardCacheMisses;
    }
}
