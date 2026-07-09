package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🥁 手势节奏大师（困难 ⭐⭐⭐）
 *
 * 玩法：4条轨道，音符下落，在判定线做正确手势来命中。
 *
 * 你需要实现：
 * 1. Note 内部类（lane轨道0~3, y坐标, speed, judged是否已判定）
 * 2. 预设节拍序列（约90秒，200+个音符）
 * 3. 音符到达判定线时，检查玩家手势是否匹配该轨道
 * 4. 判定时机：距判定线±3帧=Perfect, ±6帧=Great, >6帧=Miss
 * 5. 连击系统：Combo加成，连续10/30/50有特效
 *
 * 轨道-手势映射：
 *   轨道0（最左）→ FIST     ✊（紫色 #9333ea）
 *   轨道1         → OPEN     ✋（蓝色 #2563eb）
 *   轨道2         → PEACE    ✌️（绿色 #16a34a）
 *   轨道3（最右）→ POINTING  👆（橙色 #ea580c）
 *
 * 可用的手势数据：
 * - gesture.getGesture()   : 手势类型 FIST/OPEN/PEACE/POINTING
 */
public class RhythmMaster implements GameInterface {

    private static final Random RANDOM = new Random();

    // TODO: 定义常量
    // LANE_COUNT = 4（轨道数）
    // LANE_WIDTH = 每个轨道的宽度
    // JUDGE_LINE_Y = 判定线的Y坐标（画布底部往上约150px处）
    // NOTE_SPEED = 音符下落速度（px/帧）
    // PERFECT_WINDOW = 3帧, GREAT_WINDOW = 6帧

    // TODO: 定义音符列表 List<Note>
    // TODO: 定义节拍序列（用数组存储：每个音符的生成时间和轨道）
    // TODO: 定义当前帧计数
    // TODO: 定义 combo 连击数
    // TODO: 定义判定统计（perfectCount, greatCount, missCount）
    // TODO: 定义命中特效列表（显示在判定线上）

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int combo;
    private boolean over;
    private int gameDuration;  // 总帧数，约5400帧（90秒@60fps）

    @Override
    public String getName() {
        return "节奏大师";
    }

    @Override
    public String getDescription() {
        return "在正确时机做手势，打出完美节拍！";
    }

    @Override
    public String getIcon() {
        return "🥁";
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.combo = 0;
        this.over = false;
        this.gameDuration = 5400;  // 90秒
        // TODO:
        // 1. 初始化音符列表
        // 2. 初始化判定统计
        // 3. 生成节拍序列（从JSON文件加载或硬编码）
        //    格式：int[][] beatMap = new int[][] {
        //        {生成帧数, 轨道},
        //        {60, 0},   ← 第60帧，轨道0
        //        {120, 2},  ← 第120帧，轨道2
        //        ...
        //    };
        //    总共约200个音符，分布在90秒内
        //    可以写一个简单的节拍生成算法：
        //      - 随机选择轨道
        //      - 间隔在15~45帧之间
        //      - 偶尔出现双音符（同一帧两个轨道）
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        // 帧计数递增
        // if 帧计数 > gameDuration → over = true
        // TODO:
        //
        // 1. 根据帧计数生成音符：
        //    从节拍序列中取出"生成帧==当前帧"的音符
        //    Note(lane, y=0, speed=NOTE_SPEED)
        //    加入音符列表
        //
        // 2. 更新所有音符：
        //    note.y += note.speed
        //
        // 3. 判定逻辑（对每个未判定的音符）：
        //    distance = Math.abs(note.y - JUDGE_LINE_Y)
        //
        //    if distance <= PERFECT_WINDOW:
        //      if gesture.getGesture() == 该轨道对应的手势：
        //        → Perfect！score += 100, combo++
        //        note.judged = true
        //        生成命中特效（金色闪光）
        //      else:
        //        手势不对，暂不判定（等它错过再判Miss）
        //
        //    if distance <= GREAT_WINDOW && 未判定:
        //      if 手势匹配:
        //        → Great！score += 50, combo++
        //        note.judged = true
        //
        //    if note.y > JUDGE_LINE_Y + GREAT_WINDOW && 未判定:
        //      → Miss！combo = 0, missCount++
        //      note.judged = true
        //
        // 4. Combo加成：
        //    10 combo: score加成 1.5x
        //    30 combo: score加成 2.0x
        //    50 combo: score加成 3.0x
        //
        // 5. 移除已判定且飞出屏幕的音符
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO:
        //
        // 1. 画4条轨道：
        //    每条轨道用竖直线表示
        //    轨道间距均匀分布
        //    gc.setStroke(Color.rgb(255, 255, 255, 0.15));
        //    gc.setLineWidth(2);
        //    gc.strokeLine(laneX, 0, laneX, canvasHeight);
        //
        //    轨道顶部标手势emoji和名称：
        //    轨道0: ✊ FIST   轨道1: ✋ OPEN
        //    轨道2: ✌️ PEACE  轨道3: 👆 POINT
        //
        // 2. 画判定线：
        //    JUDGE_LINE_Y 处画一条横跨4轨道的亮色线
        //    gc.setStroke(Color.web("#deff9a"));
        //    gc.setLineWidth(3);
        //    gc.strokeLine(最左轨道-20, JUDGE_LINE_Y, 最右轨道+20, JUDGE_LINE_Y);
        //
        // 3. 画所有音符：
        //    根据note.lane确定颜色
        //    0=紫色方块, 1=蓝色方块, 2=绿色方块, 3=橙色方块
        //    圆角矩形 + 左右边框发光线
        //    double laneX = 轨道中心X坐标
        //    gc.setFill(颜色);
        //    gc.fillRoundRect(laneX - noteWidth/2, note.y - noteHeight/2,
        //                     noteWidth, noteHeight, 8, 8);
        //
        // 4. 画命中特效：
        //    判定线处的闪烁效果
        //    Perfect→金色光晕（大圆，渐变透明）
        //    Great→绿色光晕
        //    Miss→红色短横线
        //
        // 5. HUD：
        //    左上：分数
        //    中间大字：Combo数（如果combo>=10）
        //    右侧：进度条（当前帧/总帧数）
        //    右上：判定统计 P:xx G:xx M:xx
        //
        // 6. 当前手势指示器（底部）：
        //    显示当前检测到的手势是什么
        //    "当前手势：✊ FIST"

        if (over) {
            gc.setFill(Color.GOLD);
            gc.fillText("🎵 演奏结束！总分: " + score + "  握拳重新开始",
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
    // private static class Note {
    //     int lane;        // 轨道 0~3
    //     double y;        // Y坐标
    //     double speed;    // 下落速度
    //     boolean judged;  // 是否已被判定
    // }
    //
    // private static class HitEffect {
    //     double y;        // 特效Y位置（=判定线Y）
    //     int frames;      // 剩余显示帧数
    //     String type;     // "PERFECT"/"GREAT"/"MISS"
    // }
    //
    // 手势-轨道对应关系（可以用数组存）：
    // GestureType[] LANE_GESTURES = {FIST, OPEN, PEACE, POINTING};
    // Color[] LANE_COLORS = {Color.web("#9333ea"), Color.web("#2563eb"),
    //                        Color.web("#16a34a"), Color.web("#ea580c")};
    // String[] LANE_EMOJIS = {"✊", "✋", "✌️", "👆"};
}
