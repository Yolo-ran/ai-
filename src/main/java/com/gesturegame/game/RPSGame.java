package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Random;

/**
 * ✂️ 剪刀石头布游戏（中等 ⭐⭐）
 *
 * 玩法：摄像头前出手势，和电脑AI对战，五局三胜制。
 *
 * 你需要实现：
 * 1. 倒计时状态机：WAITING → COUNTDOWN(3→2→1) → JUDGE → RESULT
 * 2. 电脑随机出拳
 * 3. 根据 gesture.getGesture() 判定玩家出拳
 * 4. 判定规则：石头>剪刀>布>石头
 * 5. 五局三胜，每局之间2秒间隔
 * 6. 手势映射：FIST=石头, OPEN=布, PEACE=剪刀
 *
 * 可用的手势数据：
 * - gesture.getGesture()      : 手势类型(FIST/OPEN/PEACE/NONE)
 * - gesture.isHandDetected()  : 是否检测到手
 */
public class RPSGame implements GameInterface {

    private static final Random RANDOM = new Random();

    // 游戏状态枚举
    private enum RPSState {
        WAITING,      // 等待出拳
        COUNTDOWN,    // 倒计时中（3-2-1）
        JUDGE,        // 判定阶段
        RESULT        // 显示结果
    }

    private int canvasWidth;
    private int canvasHeight;
    private int playerScore;
    private int computerScore;
    private int roundCount;
    private boolean over;

    // TODO: 定义当前状态 RPSState
    // TODO: 定义倒计时变量（倒计时帧数、目标帧数）
    // TODO: 定义玩家当前出拳 GestureType
    // TODO: 定义电脑当前出拳（0=石头, 1=剪刀, 2=布）
    // TODO: 定义本局结果（"你赢了" / "你输了" / "平局"）
    // TODO: 定义游戏日志（最近3局的结果记录）

    @Override
    public String getName() {
        return "猜拳";
    }

    @Override
    public String getDescription() {
        return "剪刀石头布对决，体验三秒倒计时出拳";
    }

    @Override
    public String getIcon() {
        return "✂️";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.playerScore = 0;
        this.computerScore = 0;
        this.roundCount = 0;
        this.over = false;
        // TODO: 初始化状态为 WAITING
        // TODO: 初始化倒计时
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // TODO: 实现状态机
        //
        // WAITING:
        //   - 如果检测到手（gesture.isHandDetected()）→ 进入 COUNTDOWN
        //   - 倒计时设置90帧（约1.5秒）
        //
        // COUNTDOWN:
        //   - 每秒显示倒计时数字（3→2→1）
        //   - 数字变化时播放"嘀"的效果（改变背景色一帧）
        //   - 倒计时到45帧时电脑随机出拳（0石头/1剪刀/2布）
        //   - 倒计时到0 → 进入 JUDGE
        //
        // JUDGE:
        //   - 读取玩家手势：gesture.getGesture()
        //   - FIST→石头, OPEN→布, PEACE→剪刀
        //   - NONE→本轮无效，回到 WAITING
        //   - 判定胜负：
        //       if (玩家 == 电脑) → 平局
        //       else if ((玩家==0 && 电脑==1) || (玩家==1 && 电脑==2) || (玩家==2 && 电脑==0)) → 玩家赢
        //       else → 电脑赢
        //   - 更新比分，roundCount++
        //   - 进入 RESULT，停留60帧
        //
        // RESULT:
        //   - 60帧后 → 如果 roundCount>=5 或有人3胜 → over=true
        //   - 否则 → 回到 WAITING
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO: 根据当前状态渲染
        //
        // 通用元素（所有状态都画）：
        //   - 顶部标题："石头剪刀布 ✂️"
        //   - 底部比分：玩家 vs 电脑（五局三胜进度条）
        //
        // WAITING 状态：
        //   - 屏幕中央："请出手势"
        //   - 提示："握拳=石头 | 张开=布 | 剪刀手=剪刀"
        //
        // COUNTDOWN 状态：
        //   - 屏幕中央大字：3 → 2 → 1
        //   - 根据帧数变化
        //
        // JUDGE 状态：
        //   - 左半屏：玩家的手势（大emoji）
        //       石头✊ / 布✋ / 剪刀✌️
        //   - 右半屏：电脑的手势（大emoji）
        //   - VS 文字中间
        //
        // RESULT 状态：
        //   - 大字结果："你赢了！" / "你输了" / "平局"
        //   - 颜色：绿/红/黄

        if (over) {
            String finalResult = playerScore > computerScore ? "你赢了！🏆" : "电脑赢了 🤖";
            gc.setFill(playerScore > computerScore ? Color.GOLD : Color.RED);
            gc.fillText(finalResult, canvasWidth/2 - 80, canvasHeight/2);
            gc.setFill(Color.WHITE);
            gc.fillText("最终比分 " + playerScore + ":" + computerScore + "  握拳重新开始",
                    canvasWidth/2 - 140, canvasHeight/2 + 40);
        }
    }

    @Override
    public boolean isOver() {
        return over;
    }

    @Override
    public int getScore() {
        return playerScore * 100;  // 每赢一局100分
    }

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }
}
