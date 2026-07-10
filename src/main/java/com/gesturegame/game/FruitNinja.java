package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * 🔪 切水果游戏（困难 ⭐⭐⭐）
 *
 * 玩法：水果从底部弹起走抛物线，手滑动产生刀光，碰到水果就切开。
 *
 * 你需要实现：
 * 1. Fruit 内部类（x, y, vx, vy, gravity, rotation, sliced, type）
 * 2. 抛物线物理：vy += gravity, y += vy, x += vx
 * 3. 手势轨迹记录：用 LinkedList<Point2D> 记录最近15帧手坐标
 * 4. 碰撞检测：轨迹线段与水果圆心的距离 < 水果半径 → 切中
 * 5. 切中特效：水果分两半 + 果汁粒子
 * 6. 炸弹：切到 → 游戏结束
 * 7. 每波3~5个水果同时弹出，炸弹概率15%
 *
 * 可用的手势数据：
 * - gesture.getHandX/Y()       : 当前手坐标
 * - gesture.getPrevHandX/Y()   : 上一帧手坐标
 * - gesture.getVelocityX/Y()   : 手移动速度
 * - gesture.isHandDetected()   : 是否检测到手
 */
public class
FruitNinja implements GameInterface {

    private static final Random RANDOM = new Random();

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int lives;
    private int combo;
    private boolean over;

    private List<Fruit> fruits;
    private List<Particle> particles;
    private LinkedList<Point2D> trail;
    private int frameCount;
    private int nextWaveFrame;
    private boolean handDetected;

    @Override
    public String getName() {
        return "切水果";
    }

    @Override
    public String getDescription() {
        return "用手滑动切水果，小心炸弹！";
    }

    @Override
    public String getIcon() {
        return "🔪";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.lives = 3;
        this.combo = 0;
        this.over = false;
        this.fruits = new ArrayList<>();
        this.particles = new ArrayList<>();
        this.trail = new LinkedList<>();
        this.frameCount = 0;
        this.nextWaveFrame = 0;
        this.handDetected = false;
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // 1. 更新手势轨迹
        if (gesture.isHandDetected()) {
            handDetected = true;
            double hx = gesture.getHandX() * canvasWidth;
            double hy = gesture.getHandY() * canvasHeight;
            trail.add(new Point2D.Double(hx, hy));
            while (trail.size() > 15) {
                trail.removeFirst();
            }
        } else {
            handDetected = false;
            trail.clear();
        }

        // 2. 生成水果波（每60~100帧）
        if (nextWaveFrame <= 0) {
            int count = 2 + RANDOM.nextInt(3); // 2~4个水果
            for (int i = 0; i < count; i++) {
                fruits.add(createRandomFruit(false));
            }
            // 15%概率生成1个炸弹
            if (RANDOM.nextDouble() < 0.15) {
                fruits.add(createRandomFruit(true));
            }
            nextWaveFrame = 60 + RANDOM.nextInt(41);
        }
        nextWaveFrame--;

        // 3. 更新所有水果的抛物线物理
        for (Fruit f : fruits) {
            f.vy += f.gravity;
            f.y += f.vy;
            f.x += f.vx;
            f.rotation += f.rotationSpeed;
        }

        // 4. 碰撞检测（刀光切水果）
        if (trail.size() >= 2 && handDetected) {
            for (Fruit f : fruits) {
                if (f.sliced) continue;
                boolean hit = false;
                for (int i = 1; i < trail.size() && !hit; i++) {
                    Point2D prev = trail.get(i - 1);
                    Point2D curr = trail.get(i);
                    double dist = pointToSegmentDist(f.x, f.y,
                            prev.getX(), prev.getY(),
                            curr.getX(), curr.getY());
                    if (dist < f.radius) {
                        hit = true;
                        if (f.isBomb) {
                            over = true;
                        } else {
                            f.sliced = true;
                            combo++;
                            // 生成果汁粒子（8~12个）
                            int pCount = 8 + RANDOM.nextInt(5);
                            for (int p = 0; p < pCount; p++) {
                                double pvx = -5 + RANDOM.nextDouble() * 10;
                                double pvy = -5 + RANDOM.nextDouble() * 10;
                                int life = 20 + RANDOM.nextInt(20);
                                particles.add(new Particle(f.x, f.y, pvx, pvy, life, f.fleshColor));
                            }
                            score += 10 * combo;
                        }
                    }
                }
            }
        }

        // 5. 更新果汁粒子
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.2; // 轻微重力
            p.life--;
        }
        particles.removeIf(p -> p.life <= 0);

        // 6-7. 移除飞出屏幕的水果 + 漏掉检测
        List<Fruit> toRemove = new ArrayList<>();
        for (Fruit f : fruits) {
            if (f.y - f.radius > canvasHeight + 50) {
                toRemove.add(f);
                if (!f.sliced && !f.isBomb) {
                    lives--;
                    combo = 0;
                }
            }
            // 横向飞出太远也移除
            if (f.x < -100 || f.x > canvasWidth + 100) {
                toRemove.add(f);
            }
        }
        fruits.removeAll(toRemove);

        // 生命归零 → 游戏结束
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

        // 1. 画刀光轨迹：从旧到新，透明→白色渐变
        for (int i = 1; i < trail.size(); i++) {
            double alpha = 0.3 + 0.7 * i / trail.size();
            gc.setStroke(Color.rgb(255, 255, 255, alpha));
            gc.setLineWidth(3 + alpha * 5);
            Point2D prev = trail.get(i - 1);
            Point2D curr = trail.get(i);
            gc.strokeLine(prev.getX(), prev.getY(), curr.getX(), curr.getY());
        }

        // 2. 画所有水果
        for (Fruit f : fruits) {
            if (f.sliced) {
                // 已切开：两半分开，露出果肉
                double offset = f.radius * 0.5;
                // 左半
                gc.save();
                gc.translate(f.x - offset, f.y);
                gc.rotate(f.rotation);
                gc.setFill(f.color);
                gc.fillOval(-f.radius, -f.radius, f.radius * 2, f.radius * 2);
                gc.setFill(f.fleshColor);
                gc.fillOval((int)(-f.radius * 0.7), (int)(-f.radius * 0.7),
                        (int)(f.radius * 1.4), (int)(f.radius * 1.4));
                gc.restore();
                // 右半
                gc.save();
                gc.translate(f.x + offset, f.y);
                gc.rotate(f.rotation);
                gc.setFill(f.color);
                gc.fillOval(-f.radius, -f.radius, f.radius * 2, f.radius * 2);
                gc.setFill(f.fleshColor);
                gc.fillOval((int)(-f.radius * 0.7), (int)(-f.radius * 0.7),
                        (int)(f.radius * 1.4), (int)(f.radius * 1.4));
                gc.restore();
            } else if (f.isBomb) {
                // 炸弹：黑色圆 + 红色引线 + 💀
                gc.save();
                gc.translate(f.x, f.y);
                gc.rotate(f.rotation);
                gc.setFill(Color.BLACK);
                gc.fillOval(-f.radius, -f.radius, f.radius * 2, f.radius * 2);
                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                gc.strokeLine(0, -f.radius, -4, -f.radius - 10);
                gc.setStroke(Color.GRAY);
                gc.setLineWidth(1);
                gc.strokeOval(-f.radius, -f.radius, f.radius * 2, f.radius * 2);
                gc.restore();
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(f.radius * 0.8));
                gc.fillText("💀", f.x - f.radius * 0.6, f.y + f.radius * 0.35);
            } else {
                // 未切开：带旋转的彩色圆
                gc.save();
                gc.translate(f.x, f.y);
                gc.rotate(f.rotation);
                gc.setFill(f.color);
                gc.fillOval(-f.radius, -f.radius, f.radius * 2, f.radius * 2);
                // 高光
                gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.3));
                gc.fillOval((int)(-f.radius * 0.4), (int)(-f.radius * 0.6),
                        (int)(f.radius * 0.6), (int)(f.radius * 0.6));
                gc.restore();
            }
        }

        // 3. 画果汁粒子
        for (Particle p : particles) {
            double alpha = Math.max(0.0, p.life / 35.0);
            gc.setFill(p.color.deriveColor(0, 1, 1, alpha));
            gc.fillOval(p.x - 3, p.y - 3, 6, 6);
        }

        // 4. HUD
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(18));
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < lives; i++) hearts.append("❤");
        gc.fillText(hearts.toString(), 20, 30);
        gc.fillText("分数: " + score, canvasWidth - 120, 30);
        if (combo >= 3) {
            gc.setFill(Color.GOLD);
            gc.setFont(javafx.scene.text.Font.font(36));
            gc.fillText("COMBO x" + combo, canvasWidth / 2.0 - 80, canvasHeight / 2.0 - 40);
        }

        // 5. 未检测到手 → 提示
        if (!handDetected && !over) {
            gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.6));
            gc.setFont(javafx.scene.text.Font.font(24));
            gc.fillText("请伸出手", canvasWidth / 2.0 - 60, canvasHeight / 2.0);
        }

        if (over) {
            gc.setFill(Color.RED);
            gc.fillText("💥 切到炸弹！得分: " + score + "  握拳重新开始",
                    canvasWidth / 2.0 - 150, canvasHeight / 2.0);
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

    // ========== 辅助方法 ==========

    /** 生成一个随机水果或炸弹。 */
    private Fruit createRandomFruit(boolean isBomb) {
        double x = 30 + RANDOM.nextDouble() * (canvasWidth - 60);
        double y = canvasHeight + 30;
        double vx = -8 + RANDOM.nextDouble() * 16;
        double vy = -22 - RANDOM.nextDouble() * 8;
        double gravity = 0.8;
        double rotation = RANDOM.nextDouble() * 360;
        double rotationSpeed = -5 + RANDOM.nextDouble() * 10;
        double radius = 25 + RANDOM.nextDouble() * 15;

        if (isBomb) {
            return new Fruit(x, y, vx, vy, gravity, rotation, rotationSpeed,
                    radius, Color.BLACK, Color.DARKGRAY, false, true);
        }

        // 水果类型 → 颜色
        String[] types = {"西瓜", "橙子", "苹果", "柠檬"};
        int idx = RANDOM.nextInt(types.length);
        Color[] colors = {Color.GREEN, Color.ORANGE, Color.RED, Color.YELLOW};
        Color[] fleshColors = {Color.RED, Color.web("#FFD700"), Color.WHITE, Color.web("#FFFFE0")};
        return new Fruit(x, y, vx, vy, gravity, rotation, rotationSpeed,
                radius, colors[idx], fleshColors[idx], false, false);
    }

    /** 点到线段的最短距离。 */
    private double pointToSegmentDist(double px, double py,
                                       double x1, double y1,
                                       double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) {
            return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double cx = x1 + t * dx;
        double cy = y1 + t * dy;
        return Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
    }

    // ========== 内部类 ==========

    /** 水果/炸弹。 */
    private static class Fruit {
        double x, y, vx, vy, gravity, rotation, rotationSpeed, radius;
        Color color, fleshColor;
        boolean sliced, isBomb;

        Fruit(double x, double y, double vx, double vy, double gravity,
              double rotation, double rotationSpeed, double radius,
              Color color, Color fleshColor, boolean sliced, boolean isBomb) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.rotation = rotation;
            this.rotationSpeed = rotationSpeed;
            this.radius = radius;
            this.color = color;
            this.fleshColor = fleshColor;
            this.sliced = sliced;
            this.isBomb = isBomb;
        }
    }

    /** 果汁粒子。 */
    private static class Particle {
        double x, y, vx, vy;
        int life;
        Color color;

        Particle(double x, double y, double vx, double vy, int life, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.color = color;
        }
    }
}
