package com.gesturegame.game;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🥁 手势节奏大师 — 跳舞机风格（困难 ⭐⭐⭐）
 *
 * 玩法：手势图标从画面底部向上滚动，到达判定圈时做出对应手势。
 *       就像跳舞机踩箭头，换成用手摆姿势。
 *
 * 画面布局：
 *   ┌──────────────────────────┐
 *   │                          │
 *   │      ✊  ← 下一个          │  手势图标从下往上飘
 *   │                          │
 *   │   ┌──────────┐           │
 *   │   │  ⭕ 判定圈  │  ← 固定   │  图标到达这里时做手势
 *   │   └──────────┘           │
 *   │                          │
 *   │   连击 x15  🔥            │
 *   │   分数: 12500             │
 *   └──────────────────────────┘
 *
 * 手势类型（3种就够了）：
 *   ✊ FIST     — 握拳
 *   ✋ OPEN     — 张开手掌
 *   👆 POINTING — 食指指向
 *
 * 你需要实现：
 * 1. Note 内部类（手势类型, y坐标, judged）
 * 2. 节拍序列：按时间顺序排队的手势列表
 * 3. 所有音符沿一条垂直轨道从下往上滚动
 * 4. 音符到达判定圈时检查玩家手势
 * 5. 判定：Perfect(±3帧) / Great(±6帧) / Miss
 * 6. Combo系统：连续命中加成
 *
 * 可用的手势数据：
 * - gesture.getGesture()   : 当前手势 FIST/OPEN/POINTING/NONE
 */
public class RhythmMaster implements GameInterface {

    private static final Random RANDOM = new Random();

    // ===== 画面配置 =====
    // TODO: 这些常量可以在 init() 里根据画布大小动态计算
    // JUDGE_CIRCLE_Y  = 判定圈Y坐标（画布中间偏上，约画布40%处）
    // JUDGE_CIRCLE_R  = 判定圈半径
    // NOTE_SPEED      = 音符上移速度（px/帧，约3~4）
    // NOTE_SPAWN_Y    = 音符生成位置（画布底部外面一点）

    // ===== 判定窗口（帧数）=====
    // PERFECT_WINDOW = 3  音符在判定圈±3帧内命中 → Perfect
    // GREAT_WINDOW   = 6  音符在判定圈±6帧内命中 → Great
    // 超出 GREAT_WINDOW → Miss

    // ===== 手势-图标映射 =====
    // FIST     → ✊ 握拳  （颜色：红色 #ef4444）
    // OPEN     → ✋ 张开  （颜色：蓝色 #3b82f6）
    // POINTING → 👆 指向  （颜色：绿色 #22c55e）

    // TODO: 定义音符列表 List<Note>
    // TODO: 定义节拍序列（音符模板队列）
    // TODO: 定义当前帧计数 frameCount
    // TODO: 定义 combo 连击数
    // TODO: 定义判定统计（perfectCount, greatCount, missCount）
    // TODO: 定义命中反馈特效列表（飘字动画）
    // TODO: 定义游戏总时长帧数（约90秒 = 5400帧）

    private int canvasWidth;
    private int canvasHeight;
    private int score;
    private int combo;
    private int maxCombo;
    private boolean over;

    private int frameCount;
    private int perfectCount;
    private int greatCount;
    private int missCount;

    // ===== 判定圈和轨道参数 =====
    private double judgeCircleX;   // 判定圈X（画布中间）
    private double judgeCircleY;   // 判定圈Y
    private double judgeCircleR;   // 判定圈半径
    private double noteSpeed;      // 音符速度

    @Override
    public String getName() {
        return "节奏大师";
    }

    @Override
    public String getDescription() {
        return "手势跳舞机，按时摆出正确姿势！";
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
        this.maxCombo = 0;
        this.over = false;
        this.frameCount = 0;
        this.perfectCount = 0;
        this.greatCount = 0;
        this.missCount = 0;

        // TODO: 计算画面参数
        // judgeCircleX = canvasWidth / 2.0;
        // judgeCircleY = canvasHeight * 0.4;
        // judgeCircleR = 50;
        // noteSpeed = 3.5;

        // TODO: 生成节拍序列
        // 方法1：让AI随机生成一个节拍表
        // 方法2：硬编码一个预设序列
        //
        // 节拍序列格式（简化版）：
        //   用一个 List<Integer> beatMap 存储
        //   每个元素是"每隔多少帧生成下一个音符"
        //   音符的手势类型随机从 FIST/OPEN/POINTING 中选
        //
        //   比如：间隔30帧 → FIST, 间隔25帧 → OPEN, 间隔20帧 → POINTING, ...
        //   总时长90秒（5400帧），大约生成150~200个音符
        //
        //   难度递进：
        //     前30秒：间隔25~35帧（简单）
        //     中间30秒：间隔18~25帧（中等）
        //     最后30秒：间隔12~20帧（困难）+ 偶尔连续两个不同手势
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;

        frameCount++;

        // TODO:
        //
        // ===== 1. 生成新音符 =====
        // 根据节拍序列的时间表，在当前帧生成对应的音符
        // Note note = new Note();
        // note.gestureType = 随机 FIST/OPEN/POINTING
        // note.y = canvasHeight + 50（从底部外面开始）
        // note.judged = false
        // 加入音符列表
        //
        // ===== 2. 更新所有音符 =====
        // for each note:
        //   note.y -= noteSpeed  （向上移动）
        //
        // ===== 3. 判定逻辑 =====
        // for each 未判定的音符:
        //   distance = Math.abs(note.y - judgeCircleY)
        //
        //   if distance <= PERFECT_WINDOW * noteSpeed:
        //     // 音符在判定圈附近，检查手势
        //     if gesture.getGesture() == note.gestureType:
        //       → Perfect！score += 100, combo++
        //       note.judged = true
        //       生成飘字特效 "PERFECT!" (金色，在判定圈上方)
        //     // 如果手势不对，先不判定，等它飘过
        //
        //   if note.y < judgeCircleY - GREAT_WINDOW * noteSpeed && 未判定:
        //     // 音符已经飘过判定圈太远
        //     if gesture.getGesture() == note.gestureType:
        //       → Great！score += 50, combo++
        //     else:
        //       → Miss！combo = 0, missCount++
        //       生成飘字特效 "MISS" (红色)
        //     note.judged = true
        //
        // ===== 4. Combo加成 =====
        // if combo > maxCombo: maxCombo = combo
        // 分数加成：
        //   10 combo: 1.2x
        //   30 combo: 1.5x
        //   50 combo: 2.0x
        //   100 combo: 3.0x
        //
        // ===== 5. 清除飞出屏幕的音符 =====
        // 音符Y < -100 → 移除
        //
        // ===== 6. 游戏结束 =====
        // if frameCount >= 总帧数(5400):
        //   over = true
        //
        // ===== 7. 更新飘字特效 =====
        // 每个飘字有生命周期（约30帧），向上飘动+淡出

        if (frameCount >= 5400) {
            over = true;
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        // 清空画布
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // TODO:
        //
        // ===== 1. 画轨道背景 =====
        // 一条竖直的"轨道"从底部通向判定圈
        // gc.setStroke(Color.rgb(255, 255, 255, 0.08));
        // gc.setLineWidth(2);
        // gc.strokeLine(judgeCircleX, canvasHeight, judgeCircleX, 0);
        // 可以有微弱的渐变线（底部亮，顶部暗）
        //
        // ===== 2. 画判定圈 =====
        // 一个大的半透明圆圈
        // gc.setStroke(Color.web("#deff9a"));
        // gc.setLineWidth(4);
        // gc.strokeOval(judgeCircleX - R, judgeCircleY - R, R*2, R*2);
        //
        // 圈内显示当前手势提示文字：
        // "做手势！"
        //
        // 当有音符接近判定圈时，判定圈发光/变亮
        //
        // ===== 3. 画所有音符 =====
        // 每个音符是一个带图标的大圆形
        // 根据 gestureType 决定颜色：
        //   FIST     → 红色 #ef4444  ✊
        //   OPEN     → 蓝色 #3b82f6  ✋
        //   POINTING → 绿色 #22c55e  👆
        //
        // 画法：
        //   gc.setFill(颜色);
        //   gc.fillOval(note.x - 30, note.y - 30, 60, 60);
        //   gc.setFill(Color.WHITE);
        //   gc.setFont(Font.font(28));
        //   gc.fillText(emoji, note.x - 14, note.y + 10);
        //
        // 音符离判定圈越近，越大越亮（pre-scale效果）
        //
        // ===== 4. 画飘字特效 =====
        // Perfect → 金色大字 "PERFECT"，向上飘 + 淡出
        // Great → 绿色 "GREAT"
        // Miss → 红色 "MISS"
        // gc.setFill(颜色.deriveColor(0, 1, 1, alpha));
        // gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        // gc.fillText(text, x, y);
        //
        // ===== 5. 画音符预览 =====
        // 在判定圈上方显示"下一个手势是什么"
        // 取队列中离判定圈最近的一个未判定音符
        // 在画布角落显示小图标预览
        // "下一个 → ✊"
        //
        // ===== 6. HUD =====
        // 左上：分数 + combo加成倍率
        //   gc.setFill(Color.WHITE);
        //   gc.fillText("分数: " + score, 20, 30);
        //   if combo >= 10:
        //     gc.setFill(Color.GOLD);
        //     gc.fillText("🔥 " + combo + " combo", 20, 60);
        //
        // 右上：判定统计
        //   gc.setFill(Color.GOLD);
        //   gc.fillText("P:" + perfectCount, canvasWidth-180, 30);
        //   gc.setFill(Color.LIME);
        //   gc.fillText("G:" + greatCount, canvasWidth-120, 30);
        //   gc.setFill(Color.RED);
        //   gc.fillText("M:" + missCount, canvasWidth-60, 30);
        //
        // 底部（判定圈下方）：
        //   显示当前检测到的手势
        //   if gesture.isHandDetected():
        //     "当前手势: ✊ FIST"
        //   else:
        //     "未检测到手"
        //
        // ===== 7. 进度条（顶部）=====
        // 细线进度条，显示游戏进度
        // double progress = (double) frameCount / 5400;
        // gc.setFill(Color.web("#deff9a"));
        // gc.fillRect(0, 0, canvasWidth * progress, 3);

        // ===== 游戏结束画面 =====
        if (over) {
            // 半透明遮罩
            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(0, 0, canvasWidth, canvasHeight);

            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Microsoft YaHei", 36));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("🎵 演奏结束！", canvasWidth / 2.0, canvasHeight / 2.0 - 60);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", 24));
            gc.fillText("总分: " + score, canvasWidth / 2.0, canvasHeight / 2.0);
            gc.fillText("最大连击: " + maxCombo, canvasWidth / 2.0, canvasHeight / 2.0 + 35);
            gc.fillText("Perfect:" + perfectCount + "  Great:" + greatCount + "  Miss:" + missCount,
                    canvasWidth / 2.0, canvasHeight / 2.0 + 70);

            gc.setFont(Font.font("Microsoft YaHei", 18));
            gc.fillText("✊握拳 重新开始 | ✋张开 返回大厅",
                    canvasWidth / 2.0, canvasHeight / 2.0 + 120);
            gc.setTextAlign(TextAlignment.LEFT);
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

    // ==========================================
    // TODO: 内部类定义
    // ==========================================

    // private static class Note {
    //     GestureType gestureType;  // 要求玩家做的手势
    //     double y;                 // 当前Y坐标
    //     boolean judged;           // 是否已被判定
    // }
    //
    // private static class FloatText {
    //     String text;      // "PERFECT" / "GREAT" / "MISS"
    //     double x, y;      // 位置
    //     int life;         // 剩余帧数（总共30帧）
    //     Color color;      // 文字颜色
    // }
    //
    // ==========================================
    // TODO: 节拍表生成参考
    // ==========================================
    // 写一个 generateBeatMap() 方法：
    //
    // List<Integer> intervals = new ArrayList<>();
    // GestureType[] gestures = {FIST, OPEN, POINTING};
    //
    // // 前30秒（0~1800帧）：间隔25~35帧，简单
    // for (int t = 0; t < 1800; t += 25 + RANDOM.nextInt(10)) {
    //     intervals.add(t);
    // }
    //
    // // 中间30秒（1800~3600帧）：间隔18~25帧，中等
    // for (int t = 1800; t < 3600; t += 18 + RANDOM.nextInt(7)) {
    //     intervals.add(t);
    // }
    //
    // // 最后30秒（3600~5400帧）：间隔12~20帧，困难
    // for (int t = 3600; t < 5400; t += 12 + RANDOM.nextInt(8)) {
    //     intervals.add(t);
    // }
    //
    // 然后在 update() 里：
    //   if intervals.contains(frameCount) → 生成新音符，随机分配手势
    //
    // ==========================================
    // TODO: 判定逻辑伪代码
    // ==========================================
    //
    // for (Note note : notes) {
    //     if (note.judged) continue;
    //
    //     note.y -= noteSpeed;
    //
    //     double dist = Math.abs(note.y - judgeCircleY);
    //     double perfectRange = PERFECT_WINDOW * noteSpeed;
    //     double greatRange = GREAT_WINDOW * noteSpeed;
    //
    //     if (dist <= perfectRange && gesture.getGesture() == note.gestureType) {
    //         // Perfect!
    //         score += 100 * getComboMultiplier();
    //         combo++;
    //         perfectCount++;
    //         note.judged = true;
    //         spawnFloatText("PERFECT", Color.GOLD);
    //     }
    //
    //     if (note.y < judgeCircleY - greatRange && !note.judged) {
    //         // 飘过了，判定为 Great 或 Miss
    //         if (gesture.getGesture() == note.gestureType) {
    //             score += 50 * getComboMultiplier();
    //             combo++;
    //             greatCount++;
    //             spawnFloatText("GREAT", Color.LIME);
    //         } else {
    //             combo = 0;
    //             missCount++;
    //             spawnFloatText("MISS", Color.RED);
    //         }
    //         note.judged = true;
    //     }
    // }
}
