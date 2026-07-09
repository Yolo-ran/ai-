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
public class FruitNinja implements GameInterface {

    private static final Random RANDOM = new Random();

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int lives;
    private int combo;
    private boolean over;

    // TODO: 定义水果列表 List<Fruit>
    // TODO: 定义果汁粒子列表 List<Particle>
    // TODO: 定义刀光轨迹 LinkedList<Point2D>（最多保留15个点）
    // TODO: 定义帧计数器（控制水果生成）

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
        // TODO: 初始化水果列表、粒子列表、轨迹列表
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // TODO:
        //
        // 1. 更新手势轨迹：
        //    if gesture.isHandDetected():
        //      把当前手坐标加入轨迹列表（canvas坐标）
        //      轨迹最多保留15个点，超出的移除旧的
        //    else:
        //      清空轨迹（手消失了）
        //
        // 2. 生成水果（每60~100帧生成一波）：
        //    每波2~4个水果 + 15%概率1个炸弹
        //    水果从底部弹出：
        //      x = 画布内随机
        //      y = canvasHeight + 30（从底部外面弹起来）
        //      vx = 随机 -8~8（左右散射）
        //      vy = 随机 -22~-30（向上弹）
        //      gravity = 0.8
        //      rotation = 随机0~360
        //      rotationSpeed = 随机 -5~5
        //
        // 3. 更新所有水果：
        //    fruit.vy += fruit.gravity;
        //    fruit.y += fruit.vy;
        //    fruit.x += fruit.vx;
        //    fruit.rotation += fruit.rotationSpeed;
        //
        // 4. 碰撞检测（刀光切水果）：
        //    if 轨迹有至少2个点:
        //      遍历所有未切开的水果：
        //        遍历轨迹中每对相邻点(prev, curr)，形成线段
        //        计算线段到水果圆心的最短距离（点到线段距离公式）
        //        if 距离 < 水果半径 → 切中！
        //          fruit.sliced = true
        //          combo++
        //          生成果汁粒子（8~12个小圆点，从水果位置四散）
        //          score += 10 * combo
        //          炸弹 → 游戏立即结束
        //
        // 5. 更新果汁粒子：
        //    每个粒子有vx, vy, life
        //    life递减，粒子移动
        //
        // 6. 移除飞出屏幕的水果和消亡的粒子
        //
        // 7. 水果落到屏幕底部以下还没被切 → miss
        //    lives--, combo归零
        //    如果 lives<=0 → over
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO:
        //
        // 1. 画刀光轨迹：
        //    从旧到新画线，颜色从透明到白色渐变
        //    for i in 1..trail.size-1:
        //      alpha = (double)i / trail.size()
        //      gc.setStroke(Color.rgb(255, 255, 255, alpha))
        //      gc.setLineWidth(3 + alpha * 5)
        //      gc.strokeLine(trail[i-1].x, trail[i-1].y, trail[i].x, trail[i].y)
        //
        // 2. 画所有水果：
        //    未切开的水果：
        //      - 彩色圆形（西瓜=绿，橙子=橙，苹果=红，柠檬=黄）
        //      - 带旋转角度（画之前gc.save() → gc.rotate() → 画 → gc.restore()）
        //    已切开的水果：
        //      - 两半分开（左半向左偏移，右半向右偏移）
        //      - 中间露出白色果肉
        //    炸弹：
        //      - 黑色圆形 + 红色引线
        //      - 💀标志
        //
        // 3. 画果汁粒子：
        //    - 彩色小圆点，透明度随life降低
        //
        // 4. HUD：
        //    - 左上：生命❤❤❤
        //    - 右上：分数
        //    - 如果 combo>=3：中间显示combo数（大字，金色）
        //
        // 5. 如果没检测到手：屏幕中央显示"请伸出手"

        if (over) {
            gc.setFill(Color.RED);
            gc.fillText("💥 切到炸弹！得分: " + score + "  握拳重新开始",
                    canvasWidth/2 - 150, canvasHeight/2);
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

    // TODO: 定义内部类
    // private static class Fruit {
    //     double x, y, vx, vy, gravity, rotation, rotationSpeed, radius;
    //     Color color, fleshColor;
    //     boolean sliced, isBomb;
    // }
    //
    // private static class Particle {
    //     double x, y, vx, vy;
    //     int life;
    //     Color color;
    // }
}
