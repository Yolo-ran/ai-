package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

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

    private List<Card> cards;
    private int selectedIndex = -1;
    private Card flippingCard = null;
    private double flipProgress = 0.0;
    private double handCanvasX, handCanvasY;
    private boolean handDetected;

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
    private Difficulty difficulty = Difficulty.NORMAL;
    private int cardCount;
    private double hoverTimeRequired;

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
        applyDifficulty();
    }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY:
                cardCount = 3; hoverTimeRequired = 0.3; break;
            case NORMAL:
                cardCount = 5; hoverTimeRequired = 0.2; break;
            case HARD:
                cardCount = 7; hoverTimeRequired = 0.2; break;
        }
        List<String> shuffled = new ArrayList<>(java.util.Arrays.asList(FORTUNES));
        Collections.shuffle(shuffled, RANDOM);
        double cardWidth = cardCount > 5 ? 100 : 120;
        double cardHeight = 180;
        double gap = 20;
        double totalWidth = cardWidth * cardCount + gap * (cardCount - 1);
        double startX = (canvasWidth - totalWidth) / 2.0;
        double cardY = 130;
        Color[] positionColors = {
            Color.web("#4a6fa5"), Color.web("#7b4fbf"), Color.web("#4a9e8e"),
            Color.web("#c0392b"), Color.web("#d4a017"), Color.web("#2e86c1"),
            Color.web("#884ea0")
        };
        this.cards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            double cx = startX + i * (cardWidth + gap);
            cards.add(new Card(cx, cardY, cardWidth, cardHeight,
                    shuffled.get(i), positionColors[i]));
        }
        this.selectedIndex = -1;
        this.flippingCard = null;
        this.flipProgress = 0.0;
        this.handDetected = false;
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // 1. 手移动选牌（归一化坐标 → 画布像素）
        if (gesture.isHandDetected()) {
            handDetected = true;
            handCanvasX = gesture.getHandX() * canvasWidth;
            handCanvasY = gesture.getHandY() * canvasHeight;

            selectedIndex = -1;
            for (int i = 0; i < cards.size(); i++) {
                Card c = cards.get(i);
                if (handCanvasX >= c.x && handCanvasX <= c.x + c.width
                        && handCanvasY >= c.y && handCanvasY <= c.y + c.height) {
                    selectedIndex = i;
                    break;
                }
            }
        } else {
            handDetected = false;
            selectedIndex = -1;
        }

        // 翻牌动画进行中 → 更新进度
        if (flippingCard != null) {
            flipProgress += 1.0 / 30.0;
            if (flipProgress >= 0.5 && !flippingCard.revealed) {
                flippingCard.revealed = true;
            }
            if (flipProgress >= 1.0) {
                flipProgress = 1.0;
                flippingCard = null;
                score += 50;
            }
        }

        // 2. 翻牌：FIST + 有选中 + 该牌未翻开
        if (flippingCard == null
                && gesture.getGesture() == GestureType.FIST
                && selectedIndex >= 0
                && !cards.get(selectedIndex).revealed) {
            flippingCard = cards.get(selectedIndex);
            flipProgress = 0.0;
        }

        // 3. 洗牌：OPEN + 所有牌已翻开 → 重新抽3张
        if (gesture.getGesture() == GestureType.OPEN && flippingCard == null) {
            boolean allRevealed = true;
            for (Card c : cards) {
                if (!c.revealed) { allRevealed = false; break; }
            }
            if (allRevealed) {
                List<String> shuffled = new ArrayList<>(java.util.Arrays.asList(FORTUNES));
                Collections.shuffle(shuffled, RANDOM);
                for (int i = 0; i < 3; i++) {
                    cards.get(i).fortune = shuffled.get(i);
                    cards.get(i).revealed = false;
                }
                selectedIndex = -1;
            }
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#1a0033"));  // 深紫色背景，塔罗牌主题
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 1. 标题
        gc.setFill(Color.GOLD);
        gc.setFont(Font.font(24));
        gc.fillText("🔮 塔罗牌占卜", canvasWidth / 2.0 - 90, 50);

        // 2-3. 画3张牌 + 翻牌动画
        String[] labels = {"过去", "现在", "未来"};
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            double displayWidth = c.width;
            double displayX = c.x;

            // 翻牌动画：宽度随进度变化
            if (flippingCard == c) {
                displayWidth = c.width * Math.abs(0.5 - flipProgress) * 2;
                displayX = c.x + (c.width - displayWidth) / 2.0;
            }

            // 翻牌进度 < 0.5 时显示背面
            boolean showFront = c.revealed && !(flippingCard == c && flipProgress < 0.5);

            if (!showFront) {
                // 牌面朝下：深紫色 + 金色星星 + 虚线边框
                gc.setFill(Color.web("#2d004d"));
                gc.fillRoundRect(displayX, c.y, displayWidth, c.height, 15, 15);
                gc.setStroke(Color.GOLD);
                gc.setLineWidth(2);
                gc.setLineDashes(6);
                gc.strokeRoundRect(displayX, c.y, displayWidth, c.height, 15, 15);
                gc.setLineDashes(null);
                gc.setFill(Color.GOLD);
                gc.setFont(Font.font(30));
                gc.fillText("✦", displayX + displayWidth / 2.0 - 15, c.y + c.height / 2.0 + 10);
            } else {
                // 牌面朝上：淡紫背景 + 含义文字
                gc.setFill(Color.web("#e8d5f5"));
                gc.fillRoundRect(displayX, c.y, displayWidth, c.height, 15, 15);
                gc.setStroke(Color.web("#7b4fbf"));
                gc.setLineWidth(2);
                gc.setLineDashes(null);
                gc.strokeRoundRect(displayX, c.y, displayWidth, c.height, 15, 15);
                gc.setFill(Color.web("#1a0033"));
                gc.setFont(Font.font(12));
                String text = c.fortune;
                double textY = c.y + 30;
                int charsPerLine = Math.max(1, (int) (displayWidth / 14));
                int start = 0;
                while (start < text.length() && textY < c.y + c.height - 10) {
                    int end = Math.min(start + charsPerLine, text.length());
                    gc.fillText(text.substring(start, end), displayX + 10, textY);
                    textY += 20;
                    start = end;
                }
            }

            // 选中高亮（非翻牌中的牌）
            if (i == selectedIndex && flippingCard != c) {
                gc.setStroke(Color.GOLD);
                gc.setLineWidth(3);
                gc.setLineDashes(null);
                gc.strokeRoundRect(c.x - 3, c.y - 3, c.width + 6, c.height + 6, 18, 18);
            }
        }

        // 4. 位置标签
        gc.setFill(Color.GRAY);
        gc.setFont(Font.font(14));
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            gc.fillText(labels[i], c.x + c.width / 2.0 - 14, c.y + c.height + 25);
        }

        // 5. 底部提示
        gc.setFill(Color.web("#deff9a"));
        gc.setFont(Font.font(14));
        gc.fillText("✊握拳翻牌 | ✋张开洗牌 | 手移动选择",
                canvasWidth / 2.0 - 130, canvasHeight - 20);

        if (over) {
            gc.setFill(Color.GOLD);
            gc.fillText("命运已揭示", canvasWidth / 2.0 - 60, canvasHeight / 2.0);
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
    public void setDifficulty(Difficulty d) { this.difficulty = d; applyDifficulty(); }

    @Override
    public Difficulty getDifficulty() { return difficulty; }

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }

    /**
     * 塔罗牌内部类。
     */
    private static class Card {
        double x, y, width, height;
        String fortune;
        boolean revealed = false;
        Color cardColor;

        Card(double x, double y, double width, double height, String fortune, Color cardColor) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fortune = fortune;
            this.cardColor = cardColor;
        }
    }
}
