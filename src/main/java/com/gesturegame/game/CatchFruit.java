package com.gesturegame.game;

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

    // TODO: 定义水果列表 List<Fruit>
    // TODO: 定义篮子相关变量（x, y, width, height）
    // TODO: 定义帧计数器（用于控制生成频率和加速）

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
        this.lives = 3;
        this.over = false;
        // TODO: 初始化水果列表和篮子位置
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // TODO:
        // 1. 每隔一段时间（约30帧）生成新水果
        //    - 80%概率水果，20%概率炸弹
        //    - 水果从顶部随机X位置生成，vy = 2~4
        //    - 炸弹用不同颜色标记
        //
        // 2. 更新所有水果的Y坐标：fruit.y += fruit.vy
        //
        // 3. 篮子X = gesture.getHandX() * canvasWidth - 篮子宽度/2
        //    如果没检测到手，篮子停在原位
        //
        // 4. 碰撞检测：
        //    - 水果Y > 篮子Y 且 水果X在篮子范围内 → 接到！
        //    - 水果Y > canvasHeight → 漏掉了，生命-1
        //
        // 5. 加分/扣命：
        //    - 接到水果 +10分
        //    - 接到炸弹 -20分，生命-1
        //
        // 6. 移除飞出屏幕的水果
        //
        // 7. 检查游戏结束：生命 <= 0
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO:
        // 1. 画所有水果：gc.setFill(fruit.color); gc.fillOval(x, y, 40, 40);
        //    水果上可以画个小叶子（绿色小三角）
        // 2. 画炸弹：黑色圆形 + 红色引线
        // 3. 画篮子：底部矩形，用 gc.fillRoundRect()
        //    如果没检测到手，篮子用灰色；检测到用手用亮色
        // 4. 画HUD：左上角生命（❤ x lives），右上角分数
        //    gc.setFill(Color.WHITE);
        //    gc.fillText("分数: " + score, canvasWidth - 120, 30);
        //    gc.fillText("生命: " + lives, 20, 30);

        if (over) {
            gc.setFill(Color.WHITE);
            gc.fillText("游戏结束！得分: " + score + "  握拳重新开始", canvasWidth/2 - 120, canvasHeight/2);
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

    // TODO: 定义 Fruit 内部类
    // private static class Fruit {
    //     double x, y, vy;
    //     Color color;
    //     boolean isBomb;
    // }
}
