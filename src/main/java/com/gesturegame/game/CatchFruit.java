package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🍎 接水果游戏（简单 ⭐）
 *
 * 玩法：水果从画布顶部掉落，手左右移动控制底部篮子接住水果，躲开炸弹。
 *
 * 你需要实现：
 * 1. Fruit 内部类（x, y, vy速度, type水果/炸弹, color）
 * 2. 每帧随机生成水果（概率20%），从顶部随机位置掉落
 * 3. 篮子位置 = gesture.getHandX() * 画布宽度
 * 4. 碰撞检测：水果矩形 vs 篮子矩形
 * 5. 漏掉3个水果 → 游戏结束
 * 6. 炸弹碰到篮子 → 扣一条命
 * 7. 每15秒加速一次
 *
 * 可用的手势数据：
 * - gesture.getHandX()      : 手X坐标 0.0~1.0
 * - gesture.isHandDetected(): 是否检测到手
 */
public class CatchFruit implements GameInterface {

    private static final Random RANDOM = new Random();

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int lives;
    private boolean over;

    private List<Fruit> fruits;
    private double basketX, basketY;
    private static final double BASKET_HEIGHT = 20;
    private int frameCount;
    private int spawnInterval;
    private double speedMultiplier;
    private double basketWidth = 80;
    private boolean handDetected = false;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int baseLives;
    private double bombProbability;
    private int accelerateFrames;
    private int targetScore; // 目标分数，-1 表示无尽模式
    private boolean won;     // 是否通关

    @Override
    public String getName() {
        return "接水果";
    }

    @Override
    public String getDescription() {
        return "手势控制篮子，接住水果并躲开炸弹";
    }

    @Override
    public String getIcon() {
        return "🍎";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.over = false;
        this.won = false;
        this.fruits = new ArrayList<>();
        this.basketX = canvasWidth / 2.0 - basketWidth / 2;
        this.basketY = canvasHeight - 60;
        this.frameCount = 0;
        this.handDetected = false;
        applyDifficulty();
    }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY:
                baseLives = 5; targetScore = 100;
                spawnInterval = 45; speedMultiplier = 0.7;
                basketWidth = 120; bombProbability = 0.05;
                accelerateFrames = Integer.MAX_VALUE;
                break;
            case NORMAL:
                baseLives = 3; targetScore = 200;
                spawnInterval = 30; speedMultiplier = 1.0;
                basketWidth = 80; bombProbability = 0.20;
                accelerateFrames = 900;
                break;
            case HARD:
                baseLives = 1; targetScore = 300;
                spawnInterval = 18; speedMultiplier = 1.5;
                basketWidth = 50; bombProbability = 0.35;
                accelerateFrames = 480;
                break;
            case ENDLESS:
                baseLives = 3; targetScore = -1;
                spawnInterval = 30; speedMultiplier = 1.0;
                basketWidth = 80; bombProbability = 0.10;
                accelerateFrames = -1;
                break;
        }
        lives = baseLives;
    }

    @Override
    public void setDifficulty(Difficulty d) { this.difficulty = d; }

    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // 加速
        if (accelerateFrames == -1) {
            // 无尽模式：分数每+50，速度加快
            int level = score / 50;
            speedMultiplier = 1.0 + level * 0.2;
            spawnInterval = Math.max(10, 30 - level * 2);
            bombProbability = Math.min(0.40, 0.10 + level * 0.02);
        } else if (frameCount > 0 && accelerateFrames < Integer.MAX_VALUE
                && frameCount % accelerateFrames == 0) {
            speedMultiplier += 0.3;
            spawnInterval = Math.max(10, spawnInterval - 3);
        }

        // 生成水果
        if (frameCount % spawnInterval == 0) {
            double fx = 20 + RANDOM.nextDouble() * (canvasWidth - 60);
            boolean isBomb = RANDOM.nextDouble() < bombProbability;
            double vy = (2 + RANDOM.nextDouble() * 2) * speedMultiplier;
            Color color;
            if (isBomb) {
                color = Color.BLACK;
            } else {
                Color[] fruitColors = {
                    Color.RED, Color.ORANGE, Color.YELLOW,
                    Color.LIMEGREEN, Color.PURPLE, Color.DEEPPINK
                };
                color = fruitColors[RANDOM.nextInt(fruitColors.length)];
            }
            fruits.add(new Fruit(fx, -40, vy, color, isBomb));
        }

        // 2. 更新所有水果的Y坐标
        for (Fruit f : fruits) {
            f.y += f.vy;
        }

        // 3. 更新篮子位置（归一化坐标 → 像素）
        if (gesture.isHandDetected()) {
            handDetected = true;
            basketX = gesture.getHandX() * canvasWidth - basketWidth / 2;
        } else {
            handDetected = false;
        }

        // 4-6. 碰撞检测 + 加分扣命 + 移除
        List<Fruit> toRemove = new ArrayList<>();
        for (Fruit f : fruits) {
            // 水果底部碰到篮子 → 接到
            boolean caught = f.y + 40 >= basketY
                    && f.y + 40 <= basketY + BASKET_HEIGHT + 10
                    && f.x + 40 >= basketX
                    && f.x <= basketX + basketWidth;
            if (caught) {
                toRemove.add(f);
                if (f.isBomb) {
                    score = Math.max(0, score - 20);
                    // 简单/普通：炸弹只扣分；困难/无尽：炸弹还扣命
                    if (difficulty == Difficulty.HARD || difficulty == Difficulty.ENDLESS) {
                        lives--;
                    }
                } else {
                    score += 10;
                }
            } else if (f.y > canvasHeight) {
                // 飞出屏幕底部
                toRemove.add(f);
                if (!f.isBomb) {
                    lives--;
                }
            }
        }
        fruits.removeAll(toRemove);

        // 7. 通关检测（非无尽模式）
        if (targetScore > 0 && score >= targetScore) {
            won = true;
            over = true;
        }

        // 8. 生命耗尽
        if (lives <= 0) {
            lives = 0;
            over = true;
        }

        frameCount++;
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 1-2. 画所有水果和炸弹
        for (Fruit f : fruits) {
            if (f.isBomb) {
                // 炸弹：黑色圆形 + 红色引线
                gc.setFill(Color.BLACK);
                gc.fillOval(f.x, f.y, 40, 40);
                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                gc.strokeLine(f.x + 20, f.y, f.x + 16, f.y - 10);
            } else {
                // 水果：彩色圆形 + 绿色小叶子
                gc.setFill(f.color);
                gc.fillOval(f.x, f.y, 40, 40);
                gc.setFill(Color.GREEN);
                double[] xs = {f.x + 20, f.x + 16, f.x + 24};
                double[] ys = {f.y, f.y - 8, f.y - 8};
                gc.fillPolygon(xs, ys, 3);
            }
        }

        // 3. 画篮子
        if (handDetected) {
            gc.setFill(Color.web("#4ade80"));
            gc.setStroke(Color.web("#22c55e"));
        } else {
            gc.setFill(Color.GRAY);
            gc.setStroke(Color.DARKGRAY);
        }
        gc.setLineWidth(3);
        gc.fillRoundRect(basketX, basketY, basketWidth, BASKET_HEIGHT, 10, 10);
        gc.strokeRoundRect(basketX, basketY, basketWidth, BASKET_HEIGHT, 10, 10);

        drawGameHud(gc);

        // 游戏结束遮罩
        if (over) {
            gc.setFill(Color.rgb(0, 0, 0, 0.75));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);

            if (won) {
                gc.setFill(Color.GOLD);
                gc.setFont(javafx.scene.text.Font.font(36));
                gc.fillText("🎉 通关！", canvasWidth / 2.0 - 80, canvasHeight / 2.0 - 20);
            } else {
                gc.setFill(Color.RED);
                gc.setFont(javafx.scene.text.Font.font(36));
                gc.fillText("游戏结束", canvasWidth / 2.0 - 80, canvasHeight / 2.0 - 20);
            }
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(20));
            gc.fillText("得分: " + score, canvasWidth / 2.0 - 40, canvasHeight / 2.0 + 25);
            if (targetScore > 0 && !won) {
                gc.setFill(Color.GRAY);
                gc.setFont(javafx.scene.text.Font.font(14));
                gc.fillText("目标: " + targetScore, canvasWidth / 2.0 - 25, canvasHeight / 2.0 + 50);
            }
            gc.setFill(Color.web("#deff9a"));
            gc.setFont(javafx.scene.text.Font.font(14));
            gc.fillText("✊重玩 | 👆返回", canvasWidth / 2.0 - 55, canvasHeight / 2.0 + 75);
        }
    }

    private void drawGameHud(GraphicsContext gc) {
        gc.setFill(Color.color(0.02, 0.09, 0.14, 0.82));
        gc.fillRoundRect(22, 18, 220, 70, 18, 18);
        gc.setStroke(Color.color(0.29, 0.90, 0.72, 0.34));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(22, 18, 220, 70, 18, 18);
        gc.setFill(Color.web("#bbf7d0"));
        gc.setFont(javafx.scene.text.Font.font("Microsoft YaHei UI", 20));
        gc.fillText("🍎  接水果", 40, 46);
        gc.setFill(Color.web("#94a3b8"));
        gc.setFont(javafx.scene.text.Font.font("Microsoft YaHei UI", 13));
        gc.fillText("生命  ❤ × " + lives, 40, 72);

        gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
        gc.setFill(Color.web("#f8fafc"));
        gc.setFont(javafx.scene.text.Font.font("Microsoft YaHei UI", 20));
        gc.fillText("得分  " + score, canvasWidth - 28, 43);
        gc.setFill(Color.web("#94a3b8"));
        gc.setFont(javafx.scene.text.Font.font("Microsoft YaHei UI", 13));
        gc.fillText(targetScore > 0 ? "目标  " + targetScore : "无尽模式", canvasWidth - 28, 69);
        gc.setFill(Color.color(0.73, 0.97, 0.84, 0.68));
        gc.fillText("左右移动手掌控制篮子", canvasWidth - 28, canvasHeight - 24);
        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
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
     * 水果/炸弹内部类。
     */
    private static class Fruit {
        double x, y;
        double vy;
        Color color;
        boolean isBomb;

        Fruit(double x, double y, double vy, Color color, boolean isBomb) {
            this.x = x;
            this.y = y;
            this.vy = vy;
            this.color = color;
            this.isBomb = isBomb;
        }
    }
}
