package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🫧 戳泡泡游戏（简单 ⭐）
 *
 * 玩法：屏幕上随机冒出彩色泡泡，手移到泡泡位置即可戳破。
 *
 * 你需要实现：
 * 1. Bubble 内部类（x, y, radius, color, alpha透明度, growing）
 * 2. 每1~2秒生成新泡泡（随机位置、随机颜色、随机大小）
 * 3. 手坐标和泡泡圆心做距离检测：距离 < radius → 戳破！
 * 4. 戳破动画：泡泡变大 + 透明度降低 → 消失
 * 5. 泡泡在5秒后自动消失 → 扣分
 * 6. 同时最多15个泡泡
 *
 * 可用的手势数据：
 * - gesture.getHandX()      : 手X坐标 0.0~1.0
 * - gesture.getHandY()      : 手Y坐标 0.0~1.0
 * - gesture.isHandDetected(): 是否检测到手
 */
public class PopBubbles implements GameInterface {

    private static final Random RANDOM = new Random();

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private boolean over;

    private List<Bubble> bubbles;
    private int frameCount;
    private int nextSpawnFrame = 0;
    private double handCanvasX, handCanvasY;
    private boolean handDetected;

    @Override
    public String getName() {
        return "戳泡泡";
    }

    @Override
    public String getDescription() {
        return "移动手势瞄准泡泡，连击破裂拿高分";
    }

    @Override
    public String getIcon() {
        return "🫧";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.over = false;
        this.bubbles = new ArrayList<>();
        this.frameCount = 0;
        this.nextSpawnFrame = 0;
        this.handDetected = false;
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // 1. 每隔60~90帧生成新泡泡
        if (nextSpawnFrame <= 0) {
            if (bubbles.size() < 15) {
                double bx = 50 + RANDOM.nextDouble() * (canvasWidth - 100);
                double by = 50 + RANDOM.nextDouble() * (canvasHeight - 100);
                double radius = 30 + RANDOM.nextDouble() * 30;
                Color[] colors = {Color.RED, Color.BLUE, Color.GREEN,
                        Color.PURPLE, Color.ORANGE, Color.DEEPPINK};
                Color color = colors[RANDOM.nextInt(colors.length)];
                bubbles.add(new Bubble(bx, by, radius, color));
            }
            nextSpawnFrame = 60 + RANDOM.nextInt(31);
        }
        nextSpawnFrame--;

        // 更新手坐标（归一化 → 画布像素）
        if (gesture.isHandDetected()) {
            handDetected = true;
            handCanvasX = gesture.getHandX() * canvasWidth;
            handCanvasY = gesture.getHandY() * canvasHeight;
        } else {
            handDetected = false;
        }

        List<Bubble> toRemove = new ArrayList<>();
        for (Bubble b : bubbles) {
            if (b.popping) {
                // 4. 戳破动画：膨胀 + 透明度降低，持续15帧后移除
                b.popFrame++;
                b.radius += 2.0;
                b.alpha = Math.max(0.0, b.alpha - 0.05);
                if (b.popFrame >= 15) {
                    toRemove.add(b);
                }
                continue;
            }

            // 2. 泡泡慢慢变大
            b.radius += 0.15;
            b.age++;

            // 5. 300帧后自动消失 → 扣分
            if (b.age >= 300) {
                toRemove.add(b);
                score = Math.max(0, score - 5);
                continue;
            }

            // 3. 碰撞检测：手到泡泡圆心的距离 < 半径 → 戳破
            if (handDetected) {
                double dx = handCanvasX - b.x;
                double dy = handCanvasY - b.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < b.radius) {
                    b.popping = true;
                    b.popFrame = 0;
                    // 6. 按大小加分
                    if (b.radius < 40) {
                        score += 20;
                    } else if (b.radius <= 55) {
                        score += 10;
                    } else {
                        score += 5;
                    }
                }
            }
        }
        bubbles.removeAll(toRemove);

        frameCount++;
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 1. 画所有泡泡
        for (Bubble b : bubbles) {
            double r = b.radius;
            double alpha = Math.max(0.0, b.alpha);
            gc.setFill(b.color.deriveColor(0, 1, 1, alpha));
            gc.fillOval(b.x - r, b.y - r, r * 2, r * 2);

            // 高光效果：白色小圆在泡泡左上方
            if (!b.popping || b.popFrame < 8) {
                gc.setFill(Color.WHITE.deriveColor(0, 1, 1, Math.min(1.0, alpha + 0.2)));
                double hr = r * 0.25;
                gc.fillOval(b.x - r * 0.35 - hr, b.y - r * 0.35 - hr, hr * 2, hr * 2);
            }
        }

        // 2. 十字准星
        if (handDetected && !over) {
            gc.setStroke(Color.LIME);
            gc.setLineWidth(2);
            gc.strokeLine(handCanvasX - 15, handCanvasY, handCanvasX + 15, handCanvasY);
            gc.strokeLine(handCanvasX, handCanvasY - 15, handCanvasX, handCanvasY + 15);
        }

        // 3. HUD
        gc.setFill(Color.WHITE);
        gc.fillText("分数: " + score, 20, 30);

        if (over) {
            gc.setFill(Color.WHITE);
            gc.fillText("游戏结束！得分: " + score, canvasWidth / 2.0 - 100, canvasHeight / 2.0);
        }
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
        init(canvasWidth, canvasHeight);
    }

    /**
     * 泡泡内部类。
     */
    private static class Bubble {
        double x, y, radius;
        Color color;
        double alpha = 0.7;
        boolean popping = false;
        int popFrame = 0;
        int age = 0;

        Bubble(double x, double y, double radius, Color color) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.color = color;
        }
    }
}
