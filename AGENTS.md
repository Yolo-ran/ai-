# AGENTS.md — AI手势交互游戏大厅

> **用途**：三人团队共享给各自AI助手的"项目说明书"
> **怎么用**：每次让AI写代码前，把这份文件发给AI，或放到项目根目录

---

## 1. 项目概述

一个**Java桌面软件**，通过摄像头识别用户手势，用不同手势玩6款小游戏。
包含一个**游戏大厅**，在大厅中用手势选择并进入不同游戏。

- 类型：单机桌面软件
- 语言：Java 17
- 不需要联网、不需要服务器、不需要数据库

---

## 2. 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 界面 | **JavaFX** | 画窗口、游戏画面、菜单 |
| 摄像头 | **Webcam Capture** | 获取摄像头画面，提供 BufferedImage |
| 图像处理 | **OpenCV** | 肤色检测、轮廓提取、凸缺陷手势判断 |
| 构建 | **Maven** | 管理依赖、打包jar |
| 协作 | **Git + GitHub** | 版本控制 |

---

## 3. 项目结构

```
src/main/java/com/gesturegame/
│
├── MainApp.java              ← 启动入口（JavaFX Application）
│
├── common/                   ← 【公共层，三人共用，不可修改】
│   ├── GestureType.java      ← 枚举：手势类型
│   ├── GestureData.java      ← 数据类：手坐标 + 手势类型 + 历史帧
│   └── GameInterface.java    ← 游戏统一接口（7个方法）
│
├── camera/                   ← 【人A负责】摄像头 + 手势识别
│   ├── CameraManager.java    ← 打开/关闭摄像头，读取画面帧
│   └── HandDetector.java     ← 肤色检测 → 找轮廓 → 凸缺陷判手势
│
├── game/                     ← 【6个游戏，三人分配】
│   ├── CatchFruit.java       ← 🍎 接水果     ⭐ 简单
│   ├── PopBubbles.java       ← 🫧 戳泡泡     ⭐ 简单
│   ├── RPSGame.java          ← ✂️ 猜拳       ⭐⭐ 中等
│   ├── TarotGame.java        ← 🔮 塔罗牌     ⭐⭐ 中等
│   ├── FruitNinja.java       ← 🔪 切水果     ⭐⭐⭐ 困难
│   └── RhythmMaster.java     ← 🥁 节奏大师   ⭐⭐⭐ 困难
│
└── ui/                       ← 【人C负责】大厅 + 整合
    ├── GameLobby.java        ← 游戏大厅界面
    └── GameRenderer.java     ← 画面渲染工具类
```

---

## 4. 核心约定（合同）

> ⚠️ **三个人代码能合在一起的保证，AI绝对不能改这部分**

### 4.1 GestureType 枚举

```java
public enum GestureType {
    NONE,       // 未检测到手
    FIST,       // 握拳（石头）
    OPEN,       // 张开手掌（布）
    PEACE,      // 两根手指（剪刀）
    POINTING    // 一根手指（指向）
}
```

### 4.2 GestureData 数据类

```java
public class GestureData {
    private double handX;         // 手当前X坐标，范围 0.0~1.0（归一化）
    private double handY;         // 手当前Y坐标，范围 0.0~1.0（归一化）
    private double prevHandX;     // 上一帧手的X坐标（用于计算移动轨迹）
    private double prevHandY;     // 上一帧手的Y坐标
    private double velocityX;     // 手在X方向的移动速度（像素/帧）
    private double velocityY;     // 手在Y方向的移动速度（像素/帧）
    private GestureType gesture;  // 当前手势类型
    private boolean handDetected; // 这一帧是否检测到了手

    // 必须有：构造方法 + 所有字段的 getter/setter
}
```

> **坐标系**：左上角(0,0)，右下角(1,1)，全部归一化
> **velocityX/Y**：正=向右/下，负=向左/上。切水果和节奏大师会用到

### 4.3 GameInterface 游戏接口

```java
public interface GameInterface {
    String getName();                    // 游戏名称（大厅显示用）
    String getDescription();             // 一句话介绍（大厅显示用）
    void init(int width, int height);    // 初始化游戏，传入画布宽高
    void update(GestureData gesture);    // 每帧调用，传入手势数据
    void render(Graphics2D g);           // 每帧调用，画游戏画面
    boolean isOver();                    // 游戏是否结束
    int getScore();                      // 返回当前分数（大厅显示用）
    void reset();                        // 重新开始游戏
}
```

> 每个游戏必须实现这8个方法，少一个编译就报错

---

## 5. 公共模块说明

### 5.1 摄像头模块 — CameraManager

```
职责：
  1. 打开默认摄像头
  2. getFrame() 返回当前帧 BufferedImage（建议640×480）
  3. start() / stop() 控制开关
  4. 可以设置分辨率

依赖：com.github.sarxos:webcam-capture
```

### 5.2 手势识别 — HandDetector

```
处理流程：
  摄像头帧(BufferedImage)
    → 转OpenCV Mat
    → BGR转HSV色彩空间
    → 肤色范围过滤（H:0~20, S:20~150, V:70~255）
    → 腐蚀+膨胀去噪
    → 找最大轮廓 = 手
    → 计算凸包 + 凸缺陷
    → 数凸缺陷数量 → 判定手势类型
    → 计算轮廓中心 → 归一化坐标
    → 计算速度（与上一帧对比）
    → 封装 GestureData 返回

手势判定规则：
  - 0~1个凸缺陷 → FIST
  - 2~3个凸缺陷 → PEACE
  - 4~5个凸缺陷 → OPEN
  - 未检测到手  → NONE

依赖：org.bytedeco:opencv-platform
```

---

## 6. 六个游戏详细说明

### 🍎 CatchFruit — 接水果（简单）

```
难度：⭐
核心手势：手左右移动（handX）

玩法：
  - 水果从屏幕上方随机掉落
  - 手左右移动控制底部篮子
  - 接住水果加分，碰到炸弹扣分

要实现：
  1. Fruit内部类（x, y, 速度, 类型:水果/炸弹）
  2. 篮子X坐标 = handX * 画布宽度
  3. 碰撞检测（水果矩形 vs 篮子矩形）
  4. 每10秒加速
  5. 漏掉3个水果 = 游戏结束
  6. 炸弹 = 直接扣一条命

渲染：
  - 水果：彩色圆形带表情
  - 炸弹：黑色圆形
  - 篮子：底部矩形
  - 分数/生命：左上角
```

---

### 🫧 PopBubbles — 戳泡泡（简单）

```
难度：⭐
核心手势：手移动到目标位置（handX, handY）

玩法：
  - 屏幕上随机冒出彩色泡泡
  - 手移到泡泡位置 → 泡泡自动破裂
  - 泡泡越冒越快，越来越大

要实现：
  1. Bubble内部类（x, y, 半径, 颜色, 透明度）
  2. 每隔0.5~1秒生成新泡泡
  3. 手坐标和泡泡圆心做距离检测（距离 < 半径 = 戳破）
  4. 戳破动画：泡泡变大 + 透明度降低 → 消失
  5. 泡泡会在3~5秒后自动消失（扣分）
  6. 同时存在的泡泡不超过10个

渲染：
  - 泡泡：半透明彩色圆形（带高光效果更佳）
  - 手的位置：十字准星指示器
  - 分数：左上角
```

---

### ✂️ RPSGame — 剪刀石头布（中等）

```
难度：⭐⭐
核心手势：FIST(石头) / OPEN(布) / PEACE(剪刀)

玩法：
  - 摄像头前出手势
  - 电脑随机出拳
  - 倒计时后判定输赢
  - 五局三胜制

要实现：
  1. 倒计时：3 → 2 → 1 → 出拳！
  2. 电脑AI随机出（可以加点难度：记住玩家习惯）
  3. 根据 GestureData.gesture 判定：
     - FIST=石头, OPEN=布, PEACE=剪刀
  4. 判定规则：石头>剪刀>布>石头
  5. 五局三胜，显示总局比分
  6. 每局之间间隔2秒

手势映射：
  FIST  → 石头 ✊
  OPEN  → 布   ✋
  PEACE → 剪刀 ✌️

渲染：
  - 摄像头预览（小窗）
  - 倒计时大字
  - 双方出拳对比
  - 比分显示
```

---

### 🔮 TarotGame — 塔罗牌（中等）

```
难度：⭐⭐
核心手势：手移动选择 + FIST确认翻牌

玩法：
  - 3张牌面朝下排列在屏幕上
  - 手移动到某张牌上方 = 选中（高亮）
  - 握拳 = 翻牌
  - 翻开后显示牌面内容和解说文字

要实现：
  1. Card内部类（位置x,y, 宽, 高, 正面图/背面图, 是否翻开, 牌面含义）
  2. 手坐标与卡牌碰撞检测
  3. 选中状态：卡牌边框发光/放大
  4. FIST手势 = 翻牌动画（牌旋转180度）
  5. 翻牌后显示文字解说
  6. 张开手掌 = 重新洗牌，换3张新牌
  7. 可以设计3种牌阵：过去/现在/未来

预设至少10张不同的牌面内容（文字即可，不需要图片）：
  - "太阳 — 成功与喜悦即将到来"
  - "月亮 — 注意隐藏的真相"
  - "星星 — 希望与灵感在指引你"
  - ... 等等

渲染：
  - 3张卡牌并排（牌背：紫色花纹图案）
  - 选中的牌：金色边框发光
  - 翻开的牌：显示牌面文字
  - 底部显示手势提示
```

---

### 🔪 FruitNinja — 切水果（困难）

```
难度：⭐⭐⭐
核心手势：手滑动轨迹（需要连续多帧的手坐标）

玩法：
  - 水果从屏幕底部弹起，走抛物线
  - 手在画面中滑动 = 刀光轨迹
  - 轨迹碰到水果 = 切开（分成两半 + 果汁特效）
  - 切到炸弹 = 游戏结束

要实现：
  1. Fruit内部类（x, y, vx, vy, 重力加速度, 旋转角度, 类型）
  2. 抛物线运动：每帧 y += vy, vy += gravity
  3. 手势轨迹：
     - 用 List<Point2D> 记录最近N帧的手坐标
     - handDetected=false 时清空轨迹
     - 轨迹画线（带渐变透明度 = 刀光效果）
  4. 碰撞检测：
     - 轨迹线段与水果圆心的距离 < 水果半径 = 切中
     - 使用点到线段的最短距离公式
  5. 切中水果 → 水果分成两半，各自飞出去 + 果汁粒子
  6. 切到炸弹 → 立即结束
  7. 漏掉3个水果 → 结束

渲染：
  - 水果：彩色圆形 + 简单纹理
  - 刀光轨迹：白色/浅蓝渐变线条（最近的帧最亮，旧的帧淡出）
  - 果汁粒子：切中瞬间向四周散开的小圆点
  - 炸弹：黑色圆形带骷髅标志
```

---

### 🥁 RhythmMaster — 手势节奏大师（困难）

```
难度：⭐⭐⭐
核心手势：在正确时机做正确手势（GestureType + 时机判定）

玩法：
  - 4条轨道，每条对应一种手势
  - 音符沿轨道从上方落下
  - 音符到达判定线时，玩家做出对应手势
  - 根据时机判定：Perfect / Great / Miss

轨道-手势映射：
  轨道1（最左）→ FIST     ✊
  轨道2         → OPEN     ✋
  轨道3         → PEACE    ✌️
  轨道4（最右）→ POINTING  👆

要实现：
  1. Note内部类（所属轨道, y坐标, 速度, 是否已被判定）
  2. 按节拍生成音符序列（可以预设一首歌的节拍表）
  3. 音符到达判定线时检查：
     - 玩家当前手势 == 该轨道对应的手势 → 命中！
     - 判定时机：距判定线 ±50ms=Perfect, ±100ms=Great, >100ms=Miss
  4. 连击系统：连续命中=Combo加成
  5. 游戏时长约90秒，歌曲结束后统计总分

判定时机简化方案（不用真正音频同步）：
  - 用帧数代替时间：音符到达判定线±3帧=Perfect, ±6帧=Great
  - 速度可调：音符从生成到判定线约2秒（120帧@60fps）

渲染：
  - 4条竖直线（轨道）
  - 判定线：一条横线
  - 音符：不同颜色的方块/圆形（根据轨道颜色）
  - Combo数：中间大字
  - 命中特效：判定线闪烁（Perfect=金色, Great=绿色, Miss=红色）
  - 分数：右上角
```

---

## 7. MainApp 启动流程

```java
public class MainApp extends Application {

    // 1. 创建 CameraManager，打开摄像头
    // 2. 创建 HandDetector
    // 3. 创建 GameLobby（大厅）
    // 4. 注册所有6个游戏：
    //    new CatchFruit(), new PopBubbles(), new RPSGame(),
    //    new TarotGame(), new FruitNinja(), new RhythmMaster()
    // 5. 启动游戏循环（JavaFX AnimationTimer，60fps）
    //    每帧：
    //      - 从 CameraManager 拿一帧 BufferedImage
    //      - 交给 HandDetector.detect() → 得到 GestureData
    //      - 传给当前游戏/大厅的 update(gesture)
    //      - 调用当前游戏/大厅的 render(g)
    //      - 如果 isOver() → 显示结算 → 返回大厅
}
```

---

## 8. 游戏大厅交互设计

```
大厅状态机：

LOBBY（大厅）
  ├── 显示6个游戏卡片（2行×3列）
  ├── 手移动 → 光标跟随手移动
  ├── 光标悬停某张卡片2秒 → 选中该游戏（卡片放大、发光）
  └── 握拳(FIST) → 进入游戏 → PLAYING

PLAYING（游戏中）
  ├── 游戏运行中
  ├── 游戏结束 → GAME_OVER
  └── 张开手掌(OPEN)持续1秒 → 返回大厅 → LOBBY

GAME_OVER（结算）
  ├── 显示得分、评星
  ├── 握拳(FIST) → 重新开始游戏 → PLAYING
  └── 张开手掌(OPEN) → 返回大厅 → LOBBY
```

---

## 9. 编码规范

```
1. 所有代码 UTF-8 编码
2. 类名：大驼峰（CatchFruit）；方法：小驼峰（getScore）
3. 常量：全大写+下划线（MAX_FRUIT_COUNT）
4. 注释用中文
5. 每个类必须有 Javadoc 说明
6. 坐标统一：归一化坐标（0.0~1.0），左上角原点
7. 不要用 System.out.println，用 java.util.logging.Logger
8. 游戏内的内部类（Fruit, Bubble, Card, Note等）写成 private static class
9. 渲染用 Graphics2D，每帧清空重画
```

---

## 10. AI开发规则

> 🤖 **每个AI助手必须遵守的铁律**

1. **绝不修改 common/ 目录**（GestureType, GestureData, GameInterface）
2. **只在自己负责的模块写代码**
3. **GestureData 是手势数据的唯一来源**
4. **坐标必须是归一化坐标（0.0~1.0）**
5. **所有游戏必须实现 GameInterface 全部8个方法**
6. **不要引入 pom.xml 中没有的新依赖**
7. **渲染不要跨帧缓存状态，每帧清空重画**
8. **写完确保能通过 `mvn compile`**
9. **不要自己写 main 方法，统一由 MainApp 启动**
10. **游戏只通过 GestureData 获取手势，不直接调 CameraManager/HandDetector**

---

## 11. 依赖版本（pom.xml）

```xml
<java.version>17</java.version>

<!-- JavaFX -->
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>17.0.6</version>
</dependency>

<!-- 摄像头 -->
<dependency>
    <groupId>com.github.sarxos</groupId>
    <artifactId>webcam-capture</artifactId>
    <version>0.3.12</version>
</dependency>

<!-- OpenCV 图像处理 -->
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>opencv-platform</artifactId>
    <version>4.9.0-1.5.10</version>
</dependency>
```

---

## 12. 建议分工（6游戏 + 摄像头 + 大厅）

| 人员 | 负责内容 | 文件数 |
|------|------|------|
| **人A** | 摄像头模块 + 接水果 + 戳泡泡 | CameraManager, HandDetector, CatchFruit, PopBubbles |
| **人B** | 猜拳 + 塔罗牌 + 切水果 + 节奏大师 | RPSGame, TarotGame, FruitNinja, RhythmMaster |
| **人C** | 游戏大厅 + 整合 + MainApp | GameLobby, GameRenderer, MainApp |

> 这是一个建议，你们可以根据兴趣自行调整

---

## 13. 常见问题

**Q: 肤色检测不准？**
- 保证光线充足、背景无肤色物体
- 微调 HSV 阈值
- 人脸也算肤色区域，如果脸也在画面里，只取画面下半部分做检测

**Q: 帧率低/卡顿？**
- 摄像头降到 320×240
- 隔帧做手势识别（每2~3帧识别一次）
- OpenCV 处理的图缩小到 320×240

**Q: 代码合并冲突？**
- 找到 `<<<<<<<` 标记
- 和队友商量保留谁的
- 删掉标记，保存，commit

**Q: 怎么测试自己的代码？**
- 人A：写个简单测试类，传入静态图片，看 HandDetector 能不能识别
- 人B：用键盘模拟 GestureData 输入（比如方向键=手移动，1/2/3=手势类型）
- 人C：用假 GestureData 测试大厅切换逻辑
