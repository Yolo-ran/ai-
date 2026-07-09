package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 🔮 塔罗牌游戏（中等 ⭐⭐）
 *
 * 玩法：3张牌面朝下排列，手移到牌上选中，握拳翻牌查看命运。
 *
 * 你需要实现：
 * 1. Card 内部类（x, y, width, height, 牌面含义文字, 是否翻开, 颜色）
 * 2. 手移动选牌：手坐标与卡牌矩形碰撞检测 → 选中高亮
 * 3. FIST握拳 → 翻牌动画（宽度缩小→换内容→宽度恢复）
 * 4. OPEN张开 → 洗牌（随机换3张新牌面内容）
 * 5. 至少准备15条牌面含义
 * 6. 3张牌对应"过去/现在/未来"三个位置
 *
 * 可用的手势数据：
 * - gesture.getHandX()      : 手X坐标
 * - gesture.getHandY()      : 手Y坐标
 * - gesture.getGesture()    : 手势类型
 * - gesture.isHandDetected(): 是否检测到手
 */
public class TarotGame implements GameInterface {

    private static final Random RANDOM = new Random();

    // TODO: 定义 Card 内部类
    // TODO: 定义牌面列表 List<Card>（3张牌）
    // TODO: 定义当前选中的牌索引（-1=未选中）
    // TODO: 定义翻牌动画变量（正在翻的牌、翻牌进度0.0~1.0）
    // TODO: 定义牌面内容池（至少15条字符串）

    // 牌面内容示例（你可以多加点）：
    private static final String[] FORTUNES = {
        "🌟 太阳 — 成功与喜悦即将到来，保持积极的心态",
        "🌙 月亮 — 注意隐藏的信息，相信你的直觉",
        "⭐ 星星 — 希望与灵感在指引你的方向",
        "⚡ 塔 — 突如其来的变化，但也可能是新的机会",
        "💀 死神 — 一个阶段即将结束，新的开始在前方",
        "❤️ 恋人 — 重要的选择摆在面前，跟随内心",
        "⚖️ 正义 — 真相将被揭示，公平会到来",
        "🎡 命运之轮 — 运势正在转变，抓住机会",
        "💪 力量 — 你有足够的能力克服困难",
        "🕯️ 隐士 — 需要独处思考，寻找内心的答案",
        "👑 皇帝 — 权威和秩序，稳扎稳打才能成功",
        "👸 皇后 — 创造力与丰饶，享受生活的美好",
        "🎭 愚者 — 新的旅程即将开始，勇敢迈出第一步",
        "🌍 世界 — 一个循环圆满完成，收获你的成果",
        "🔥 审判 — 是时候做出最终决定了"
    };

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private boolean over;

    @Override
    public String getName() {
        return "塔罗牌";
    }

    @Override
    public String getDescription() {
        return "手势选牌翻牌，探索神秘的塔罗命运";
    }

    @Override
    public String getIcon() {
        return "🔮";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.over = false;
        // TODO: 初始化3张牌
        // 牌的位置：均匀分布在画布水平方向
        // 每张牌随机从 FORTUNES 中抽取（不重复）
        // 所有牌初始为背面朝上（reversed = false）
        // 当前选中 = -1
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // TODO:
        //
        // 1. 手移动选牌：
        //    if gesture.isHandDetected():
        //      handCanvasX = gesture.getHandX() * canvasWidth
        //      handCanvasY = gesture.getHandY() * canvasHeight
        //      遍历3张牌：
        //        if handX在牌的X范围内 && handY在牌的Y范围内 → 选中这张牌
        //      如果手不在任何牌上 → 取消选中
        //
        // 2. 翻牌逻辑：
        //    if gesture.getGesture() == GestureType.FIST && 有选中的牌 && 该牌未翻开:
        //      开始翻牌动画
        //      翻牌进度从0.0→1.0（约30帧完成）
        //      进度0.5时：切换牌面内容（从背面→正面）
        //      翻开后 score += 50
        //
        // 3. 洗牌逻辑：
        //    if gesture.getGesture() == GestureType.OPEN:
        //      如果所有牌都已翻开 → 重新随机抽取3张
        //      所有牌翻回背面
        //      取消选中
        //
        // 4. 全部翻开 → 可以洗牌（OPEN手势提示）
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#1a0033"));  // 深紫色背景，塔罗牌主题
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO:
        //
        // 1. 画标题："🔮 塔罗牌占卜"
        //    gc.setFill(Color.GOLD);
        //
        // 2. 画3张牌：
        //    每张牌是一张矩形卡片
        //
        //    牌面朝下时（未翻开）：
        //      - 深紫色底色
        //      - 金色星星图案（可以用文字"✦"代替）
        //      - 边框：金色虚线
        //      gc.setFill(Color.web("#2d004d"));
        //      gc.fillRoundRect(x, y, w, h, 15, 15);
        //
        //    牌面朝上时（已翻开）：
        //      - 渐变背景（淡紫→白）
        //      - 显示牌面含义文字（多行，自动换行）
        //      gc.fillRoundRect(x, y, w, h, 15, 15);
        //      gc.setFill(Color.web("#1a0033"));
        //      gc.fillText(含义, x+10, y+30);
        //
        //    选中状态（手悬停）：
        //      - 金色边框发光
        //      gc.setStroke(Color.GOLD);
        //      gc.setLineWidth(3);
        //      gc.strokeRoundRect(x-3, y-3, w+6, h+6, 18, 18);
        //
        // 3. 翻牌动画（正在翻的牌）：
        //    - 宽度随进度变化：cardWidth * abs(0.5 - progress) * 2
        //    - 进度=0.5瞬间切换内容
        //
        // 4. 牌面位置标签（牌下方）：
        //    "过去"  "现在"  "未来"
        //    gc.setFill(Color.GRAY);
        //
        // 5. 底部提示：
        //    "✊握拳翻牌 | ✋张开洗牌 | 手移动选择"
        //    gc.setFill(Color.web("#deff9a"));

        if (over) {
            gc.setFill(Color.GOLD);
            gc.fillText("命运已揭示", canvasWidth/2 - 60, canvasHeight/2);
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

    // TODO: 定义 Card 内部类
    // private static class Card {
    //     double x, y, width, height;
    //     String fortune;        // 牌面含义文字
    //     boolean revealed;      // 是否已翻开
    //     boolean flipping;      // 正在翻牌动画中
    //     double flipProgress;   // 翻牌进度 0.0~1.0
    //     Color cardColor;       // 卡片主题色
    // }
}
