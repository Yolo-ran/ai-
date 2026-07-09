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

    // TODO: 定义泡泡列表 List<Bubble>
    // TODO: 定义帧计数器

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
        // TODO: 初始化泡泡列表
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // TODO:
        // 1. 每隔60~90帧生成新泡泡
        //    - 随机位置：x在画布内，y在画布内
        //    - 随机半径：30~60
        //    - 随机颜色：红/蓝/绿/紫/橙/粉
        //
        // 2. 泡泡慢慢变大：bubble.radius += 0.15
        //
        // 3. 如果检测到手，进行碰撞检测：
        //    double handCanvasX = gesture.getHandX() * canvasWidth;
        //    double handCanvasY = gesture.getHandY() * canvasHeight;
        //    for each bubble:
        //      distance = sqrt((handX-bubble.x)² + (handY-bubble.y)²)
        //      if distance < bubble.radius → 戳破！
        //
        // 4. 戳破效果：不是立即删除，而是标记为"正在爆"
        //    泡泡快速膨胀 + 透明度降低，持续15帧后移除
        //
        // 5. 未被戳破的泡泡在300帧后自动消失 → score -= 5
        //
        // 6. 加分：
        //    - 小泡泡（radius < 40）：+20分
        //    - 中泡泡（40~55）：+10分
        //    - 大泡泡（> 55）：+5分
        //
        // 7. 最多15个泡泡同时存在，超出不新增
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO:
        // 1. 画所有泡泡：
        //    - 正在爆的泡泡：半径较大、透明度低
        //    - 正常泡泡：半透明彩色圆形
        //    - 画高光效果：白色小圆在泡泡左上方
        //    gc.setFill(bubble.color.deriveColor(0, 1, 1, bubble.alpha));
        //    gc.fillOval(bubble.x - r, bubble.y - r, r*2, r*2);
        //
        // 2. 如果检测到手，画十字准星：
        //    double handX = gesture数据中的坐标
        //    gc.setStroke(Color.LIME);
        //    gc.strokeLine(handX-15, handY, handX+15, handY);
        //    gc.strokeLine(handX, handY-15, handX, handY+15);
        //
        // 3. 画HUD：左上角分数
        //    gc.setFill(Color.WHITE);
        //    gc.fillText("分数: " + score, 20, 30);

        if (over) {
            gc.setFill(Color.WHITE);
            gc.fillText("游戏结束！得分: " + score, canvasWidth/2 - 100, canvasHeight/2);
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

    // TODO: 定义 Bubble 内部类
    // private static class Bubble {
    //     double x, y, radius;
    //     Color color;
    //     double alpha;     // 0.0~1.0 透明度
    //     boolean popping;   // 正在爆
    //     int popFrame;      // 爆的动画帧计数
    //     int age;           // 存活帧数
    // }
}
