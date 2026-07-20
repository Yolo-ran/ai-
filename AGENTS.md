# AGENTS.md — AI手势交互游戏大厅（混合方案）

> **用途**：三人团队共用的AI项目说明书
> **怎么用**：每次让AI写代码前，把这份文件发给AI，或放到项目根目录

---

## 1. 项目概述

一个**混合架构**的桌面软件：
- **Python** 负责手势识别（MediaPipe，准确率99%）
- **Java** 负责游戏大厅 + 6款游戏 + UI渲染
- 两者通过 **WebSocket** 通信（本机，不走网络）

```
┌─────────────────────┐      WebSocket       ┌──────────────────────┐
│  Python 程序         │ ───── JSON ──────→   │  Java 程序            │
│  MediaPipe 手势识别   │     localhost:8765   │  大厅 + 6个游戏 + 画面 │
│  输出：手坐标+手势类型  │                      │  输入：JSON手势数据     │
└─────────────────────┘                      └──────────────────────┘
```

- 类型：单机桌面软件（两个本地进程通信）
- 不需要联网、不需要服务器、不需要数据库

---

## 2. 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 手势识别 | **Python + MediaPipe** | 21个手部关键点，准确率99%，不受光线影响 |
| 通信桥梁 | **WebSocket** | Python → Java，JSON格式，localhost:8765 |
| Java界面 | **JavaFX + FXML** | FXML布局 + CSS样式，已有暗黑科技风主题 |
| 游戏渲染 | **JavaFX Canvas** | 在Canvas上画游戏画面，Graphics2D风格API |
| Java构建 | **Maven** | 管理依赖，打包jar |
| Python依赖 | **pip** | mediapipe, opencv-python, websockets |

---

## 3. 项目结构

```
gesture-game-hall/
│
├── pom.xml                          ← Maven配置（已有，需补充依赖）
├── AGENTS.md                        ← 本文件
├── README.md
├── .gitignore
│
├── python/                          ← 【人A负责】Python手势识别
│   ├── requirements.txt             ← pip依赖清单
│   └── gesture_server.py            ← 主程序：摄像头→MediaPipe→WebSocket发送
│
├── src/main/java/com/gesturegame/
│   │
│   ├── MainApp.java                 ← 启动入口（已有，需微调）
│   │
│   ├── common/                      ← 【公共层，三个人都不可修改】
│   │   ├── GestureType.java         ← 枚举：手势类型
│   │   ├── GestureData.java         ← 数据类：手坐标 + 手势类型
│   │   └── GameInterface.java       ← 游戏统一接口
│   │
│   ├── engine/                      ← 状态机（已有）
│   │   └── AppStateManager.java     ← 状态：LOGIN → LOBBY → GAME
│   │
│   ├── network/                     ← WebSocket桥梁（已有，需扩展）
│   │   └── GestureSocketServer.java ← 收JSON → 转GestureData → 路由
│   │
│   ├── game/                        ← 【人B负责】6个游戏
│   │   ├── CatchFruit.java          ← 🍎 接水果      ⭐
│   │   ├── ZumaGame.java            ← 🐸 祖玛        ⭐⭐
│   │   ├── RPSGame.java             ← ✂️ 猜拳        ⭐⭐
│   │   ├── TarotGame.java           ← 🔮 塔罗牌      ⭐⭐
│   │   ├── FruitNinja.java          ← 🔪 切水果      ⭐⭐⭐
│   │   └── RhythmMaster.java        ← 🥁 节奏大师    ⭐⭐⭐
│   │
│   └── ui/                          ← 【人C负责】界面
│       ├── LoginController.java     ← 登录界面控制器（已有）
│       ├── LobbyController.java     ← 大厅控制器（已有，需改）
│       └── GameRenderer.java        ← 游戏渲染器（NEW）
│
└── src/main/resources/
    ├── css/app.css                  ← 暗黑主题样式（已有）
    └── fxml/
        ├── Login.fxml               ← 登录界面布局（已有）
        ├── Lobby.fxml               ← 大厅布局（已有，需改6卡片）
        └── Game.fxml                ← 游戏界面布局（NEW）
```

---

## 4. 核心约定（合同）

> ⚠️ **三人代码能合在一起的保证，AI绝对不能改这部分**

### 4.1 WebSocket 通信协议

Python 发给 Java 的 JSON 格式（**唯一标准**）：

```json
{
    "handX": 0.52,
    "handY": 0.61,
    "prevHandX": 0.50,
    "prevHandY": 0.63,
    "velocityX": 0.02,
    "velocityY": -0.02,
    "gesture": "FIST",
    "confidence": 0.95,
    "handDetected": true
}
```

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| handX | double | 0.0~1.0 | 手中心X坐标，归一化，左上角原点 |
| handY | double | 0.0~1.0 | 手中心Y坐标，归一化 |
| prevHandX | double | 0.0~1.0 | 上一帧的手X坐标 |
| prevHandY | double | 0.0~1.0 | 上一帧的手Y坐标 |
| velocityX | double | -1.0~1.0 | X方向移动速度 |
| velocityY | double | -1.0~1.0 | Y方向移动速度 |
| gesture | string | 见下方 | 手势类型 |
| confidence | double | 0.0~1.0 | 识别置信度 |
| handDetected | bool | true/false | 这一帧是否检测到手 |

### 4.2 手势类型

```java
// Java 枚举
public enum GestureType {
    NONE,       // 未检测到手
    FIST,       // 握拳（石头）
    OPEN,       // 张开手掌（布）
    PEACE,      // 两根手指（剪刀）
    POINTING    // 一根手指（指向）
}
```

Python 发送时用小写字符串：`"fist"`, `"open"`, `"peace"`, `"pointing"`, `"none"`

> **大厅导航手势**：由 Java 端根据 handX/gesture 自己判断，不需要 Python 单独发送：
> - 手连续向左移动 → SWIPE_LEFT（切上一个游戏）
> - 手连续向右移动 → SWIPE_RIGHT（切下一个游戏）
> - FIST 保持1秒 → CONFIRM（进入游戏）
> - OPEN 保持1秒 → BACK（返回大厅）

### 4.3 GestureData Java类

```java
public class GestureData {
    private double handX;         // 0.0~1.0 归一化
    private double handY;         // 0.0~1.0 归一化
    private double prevHandX;
    private double prevHandY;
    private double velocityX;
    private double velocityY;
    private GestureType gesture;
    private double confidence;    // 0.0~1.0
    private boolean handDetected;

    // 必须有：无参构造 + 全参构造 + 所有字段getter/setter
    // 必须有：fromJson(String json) 静态工厂方法
}
```

### 4.4 GameInterface 游戏接口

```java
public interface GameInterface {
    String getName();                    // 游戏名称（大厅显示）
    String getDescription();             // 一句话介绍
    String getIcon();                    // 图标emoji（🍎✂️🐸🔮🔪🥁）
    void init(int width, int height);    // 初始化，传入画布宽高
    void update(GestureData gesture);    // 每帧调用
    void render(GraphicsContext gc);     // 每帧渲染（JavaFX Canvas）
    boolean isOver();                    // 游戏是否结束
    int getScore();                      // 当前分数
    void reset();                        // 重新开始
}
```

> ⚠️ 所有游戏必须实现全部9个方法，编译缺一个就报错

---

## 5. 已有机代码说明（不要重写）

### 5.1 保留的文件

| 文件 | 说明 | 需要改吗 |
|------|------|------|
| `MainApp.java` | 启动入口，加载FXML，启动WebSocket | 🔧 需要加 GAME 场景 |
| `AppStateManager.java` | 单例状态机，LOGIN↔LOBBY切换 | 🔧 需要加 GAME 状态 |
| `GestureSocketServer.java` | WebSocket服务器，收JSON路由到Controller | 🔧 需要解析坐标字段，转GestureData |
| `LoginController.java` | 登录界面，手势签入 | ✅ 基本不用动 |
| `LobbyController.java` | 大厅，3张卡片轮播 | 🔧 需要改成6张，接入真实游戏 |
| `Login.fxml` | 登录界面布局 | ✅ 不动 |
| `Lobby.fxml` | 大厅布局，3张卡片 | 🔧 改成6张 |
| `app.css` | 暗黑主题样式 | ✅ 不动，新增游戏相关样式 |

### 5.2 需要新增的文件

| 文件 | 说明 |
|------|------|
| `common/GestureType.java` | 手势枚举 |
| `common/GestureData.java` | 手势数据类 |
| `common/GameInterface.java` | 游戏接口 |
| `game/CatchFruit.java` 等6个 | 游戏实现 |
| `ui/GameRenderer.java` | 游戏渲染管理 |
| `resources/fxml/Game.fxml` | 游戏界面布局（Canvas） |
| `python/gesture_server.py` | Python手势识别程序 |
| `python/requirements.txt` | Python依赖清单 |

---

## 6. Python手势识别程序规格（人A）

### 6.1 gesture_server.py

```
功能：
  1. 用 OpenCV 打开默认摄像头（640×480）
  2. 用 MediaPipe Hands 检测手部21个关键点
  3. 计算手的中心坐标（归一化到0~1）
  4. 根据手指弯曲角度判断手势类型
  5. 计算手移动速度（与上一帧对比）
  6. 封装成 JSON，通过 WebSocket 发送到 localhost:8765
  7. 每秒发送约30帧（不用每帧都发，隔帧发即可）

MediaPipe 手势判断逻辑：
  - 五根手指指尖都在指根上方且手指展开 → open（张开）
  - 所有指尖都在指根下方 → fist（握拳）
  - 只有食指+中指在指根上方 → peace（剪刀）
  - 只有食指在指根上方 → pointing（指向）
  - 没有检测到手 → none

依赖：
  - mediapipe
  - opencv-python
  - websockets

启动方式：
  python gesture_server.py

注意事项：
  - 先启动 Python，再启动 Java
  - Python 会打印 "WebSocket 服务已启动，等待 Java 连接..."
  - Java 连上后会打印 "Python 视觉识别端已成功连接"
```

### 6.2 requirements.txt

```
mediapipe>=0.10.0
opencv-python>=4.8.0
websockets>=12.0
```

---

## 7. Java端修改规格

### 7.1 GestureSocketServer 改造

```
现有：接收 gesture(字符串) + confidence + hand
改造后：
  1. 接收完整 JSON（包含所有坐标字段）
  2. 解析为 GestureData 对象
  3. 将 GestureData 路由给当前活跃的 Controller/Game

路由逻辑：
  - LOGIN 状态 → LoginController.handleGesture(GestureData)
  - LOBBY 状态 → LobbyController.handleGesture(GestureData)
  - GAME  状态 → 当前游戏的 update(GestureData)
```

### 7.2 AppStateManager 改造

```
现有状态：LOGIN, LOBBY
新增状态：GAME

新增方法：
  - setActiveGame(GameInterface game)  // 设置当前游戏
  - getActiveGame()                     // 获取当前游戏
```

### 7.3 LobbyController 改造

```
现有：3张卡片，MAX_GAMES=3
改造：6张卡片，MAX_GAMES=6
  - 动态从游戏注册表读取游戏列表
  - SWIPE_LEFT/RIGHT 切换
  - CONFIRM → launchGame() 真正启动游戏
  - BACK → 返回 LOGIN（或直接留在LOBBY）

launchGame() 实现：
  1. 根据 currentIndex 获取对应的 GameInterface 实例
  2. appStateManager.setActiveGame(game)
  3. game.init(canvasWidth, canvasHeight)
  4. appStateManager.switchState("GAME")
```

### 7.4 Game.fxml + GameRenderer

```
Game.fxml 布局：
  - 一个全屏 Canvas（游戏渲染区）
  - 左上角：游戏名称 Label
  - 右上角：分数 Label
  - 左下角：摄像头预览小窗（可选，ImageView）
  - 底部提示：手势操作说明

GameRenderer 职责：
  - 持有 Canvas 的 GraphicsContext
  - 在游戏循环中调用 game.update() + game.render()
  - 管理帧率（AnimationTimer，60fps）
  - 检测游戏结束 → 显示结算 → 返回大厅
```

---

## 8. 六个游戏详细说明

### 🍎 CatchFruit — 接水果（简单 ⭐）

```
核心数据：handX（手左右移动）

玩法：
  - 水果从画布顶部随机位置掉落
  - 手的X坐标控制底部篮子位置
  - 接住水果 +10分，碰到炸弹 -20分+扣命
  - 漏掉3个水果 = 游戏结束

实现要点：
  - Fruit内部类：x, y, vy(下落速度), type(水果/炸弹), color
  - 篮子X = handX * canvasWidth
  - 碰撞检测：水果矩形 vs 篮子矩形
  - 每15秒加速一次
  - 炸弹出现概率20%

渲染：
  - 水果：彩色圆形+叶子
  - 炸弹：黑色圆形+引线
  - 篮子：底部弧形
  - HUD：分数(右上) + 生命(左上)
```

### 🐸 ZumaGame — 祖玛（中等 ⭐⭐）

```
核心数据：handX, handY（瞄准）+ gesture（FIST=发射，PEACE=换球）

玩法：
  - 彩球链沿螺旋轨道不断向神庙入口推进
  - 移动手掌控制中央青蛙炮台的瞄准方向
  - 握拳发射彩球，剪刀手交换当前球与下一颗球
  - 插入后形成3颗及以上同色球即可消除，并可触发连锁得分
  - 彩球进入轨道终点则本局结束；目标模式达到指定分数获胜

实现要点：
  - PathPoint采样螺旋轨道，并按轨道距离定位每颗彩球
  - Projectile按瞄准向量运动，与球链碰撞后按切线方向确定插入位置
  - 从插入点向两侧扫描同色连续区间，完成消除和连锁判定
  - 按难度调整颜色数量、推进速度、目标分数和每波球数
  - 进入游戏后设置短暂输入保护，避免难度确认的握拳被当成发射

渲染：
  - 深色遗迹背景与螺旋石质轨道
  - 中央青蛙炮台、当前球、下一颗球和瞄准线
  - 彩球高光、消除粒子、连锁提示与目标分数HUD
```

### ✂️ RPSGame — 剪刀石头布（中等 ⭐⭐）

```
核心数据：gesture（FIST/OPEN/PEACE）

玩法：
  - 3-2-1倒计时
  - 摄像头前出手势：FIST=石头, OPEN=布, PEACE=剪刀
  - 电脑随机出拳
  - 五局三胜制

实现要点：
  - 倒计时状态机：WAITING → COUNTDOWN(3,2,1) → JUDGE → RESULT
  - 电脑出拳：Math.random() 随机
  - 判定：石头>剪刀>布>石头
  - 每局之间2秒间隔显示结果
  - 比分记录

渲染：
  - 摄像头预览小窗
  - 倒计时大字（占画面中央）
  - 双方出拳对比（emoji大字）
  - 底部比分
```

### 🔮 TarotGame — 塔罗牌（中等 ⭐⭐）

```
核心数据：handX, handY（选牌）+ gesture（FIST=翻牌, OPEN=洗牌）

玩法：
  - 3张牌面朝下排列
  - 手移到某张牌上 → 高亮选中
  - FIST握拳 → 翻牌动画 → 显示牌面和含义
  - OPEN张开 → 洗牌，换3张新牌

实现要点：
  - Card内部类：x, y, w, h, reversed(是否翻开), meaning(含义文字), color
  - 碰撞检测：手坐标 vs 卡牌矩形
  - 翻转动画：宽度从w缩到0，换文字，再从0扩到w
  - 至少准备15条不同的牌面含义
  - 3张牌对应"过去/现在/未来"三个位置

牌面内容示例：
  - "🌟 太阳 — 成功与喜悦即将到来，保持积极的心态"
  - "🌙 月亮 — 注意隐藏的信息，相信你的直觉"
  - "⭐ 星星 — 希望与灵感在指引你的方向"
  - ... 等等，共15条

渲染：
  - 牌背：深紫色+星星花纹
  - 选中：金色边框发光
  - 翻开：渐变背景+文字
  - 底部："握拳翻牌 | 张开洗牌 | 另一手势回大厅"
```

### 🔪 FruitNinja — 切水果（困难 ⭐⭐⭐）

```
核心数据：handX, handY 连续轨迹（velocityX/Y辅助判断刀光）

玩法：
  - 水果从画布底部弹起，走抛物线
  - 手滑动 = 刀光轨迹
  - 轨迹碰到水果 → 切开（两半+果汁粒子）
  - 切到炸弹 → 游戏结束

实现要点：
  - Fruit内部类：x, y, vx, vy, gravity, rotation, sliced
  - 抛物线物理：每帧 vy += gravity, y += vy, x += vx
  - 轨迹记录：List<Point2D> gestureTrail，最多保留15帧
  - 碰撞检测：轨迹线段 vs 水果圆形（点到线段距离）
  - 切中特效：水果分两半，各飞出去，果汁粒子散开
  - 每波3~5个水果同时弹出
  - 炸弹概率15%

渲染：
  - 水果：彩色圆形+纹理
  - 刀光轨迹：白色渐变线条（最新=亮白，最旧=透明）
  - 果汁粒子：彩色小圆点四散
  - 炸弹：黑色+💀标志
  - HUD：分数 + 连击数
```

### 🥁 RhythmMaster — 手势节奏大师（困难 ⭐⭐⭐）

```
核心数据：gesture + 时机判定

玩法：
  - 4条轨道，每条对应一种手势
  - 音符沿轨道下落
  - 音符到达判定线时，做出对应手势
  - 判定：Perfect / Great / Miss
  - 连击加成

轨道-手势映射：
  轨道1（最左） → FIST     ✊（紫色）
  轨道2         → OPEN     ✋（蓝色）
  轨道3         → PEACE    ✌️（绿色）
  轨道4（最右） → POINTING  👆（橙色）

实现要点：
  - Note内部类：lane(0~3), y, speed, judged
  - 预设节拍序列（约90秒，200+个音符）
  - 判定时机（用帧数，不用真实音频）：
    - 音符Y与判定线Y差 < 20px → Perfect（+100 + combo）
    - 差 < 40px → Great（+50 + combo）
    - 差 < 60px → Miss（combo断）
  - 音符速度：从生成到判定线约2秒
  - Combo系统：连续10/30/50 combo有特效提示

节拍序列格式（JSON文件或硬编码）：
  [
    {"time": 0.5, "lane": 0},     // 0.5秒，轨道0
    {"time": 1.0, "lane": 2},     // 1.0秒，轨道2
    {"time": 1.5, "lane": 1},
    ...
  ]

渲染：
  - 4条竖直线（轨道），判定线（横线）
  - 音符：不同颜色方块沿轨道下落
  - 命中特效：判定线闪烁（金/绿/红）
  - Combo数字：画面中央大字
  - 分数：右上角
  - 进度条：顶部
```

---

## 9. 大厅交互设计

```
状态机：
  LOGIN → 手势签入 → LOBBY → 选游戏 → GAME → 游戏结束 → LOBBY
    ↑                                      │
    └──────────── BACK手势 ←────────────────┘

每个状态的手势操作：

  LOGIN（登录）：
    - CONFIRM（FIST保持1秒）→ 签入 → 进入LOBBY
    - 其他手势 → 提示"请做确认手势"

  LOBBY（大厅）：
    - SWIPE_LEFT  → 切换到左边的游戏卡片
    - SWIPE_RIGHT → 切换到右边的游戏卡片
    - CONFIRM（FIST保持1秒）→ 进入选中的游戏
    - BACK（OPEN保持1秒）→ 返回LOGIN

  GAME（游戏中）：
    - 游戏自身的手势操作（由各游戏定义）
    - 游戏isOver() → 显示结算 → 自动回LOBBY或重玩

  GAME_OVER（结算）：
    - CONFIRM → 重玩
    - BACK → 回LOBBY
```

---

## 10. MainApp 游戏循环

```java
// 在 MainApp 中启动 AnimationTimer（60fps 游戏循环）

AnimationTimer gameLoop = new AnimationTimer() {
    @Override
    public void handle(long now) {
        String state = appStateManager.getCurrentState();

        if ("GAME".equals(state)) {
            GameInterface game = appStateManager.getActiveGame();
            GestureData latestGesture = gestureSocketServer.getLatestData();
            if (latestGesture != null) {
                game.update(latestGesture);
            }
            game.render(gameCanvas.getGraphicsContext2D());

            if (game.isOver()) {
                appStateManager.switchState("GAME_OVER");
                showGameOverScreen(game.getScore());
            }
        }
    }
};
gameLoop.start();
```

---

## 11. 编码规范

```
1. 所有代码 UTF-8 编码
2. 类名：大驼峰；方法名：小驼峰；常量：全大写+下划线
3. 注释用中文
4. 每个类必须有 Javadoc
5. 坐标统一归一化（0.0~1.0），左上角原点
6. Java日志用 java.util.logging.Logger，Python用 print+logging
7. 游戏内部类写成 private static class
8. 渲染每帧清空重画，不跨帧缓存
9. Java 和 Python 之间只通过 WebSocket JSON 通信，不要有其他通信方式
```

---

## 12. AI开发规则

> 🤖 **每个AI助手必须遵守的铁律**

1. **绝不修改 common/ 目录**（GestureType, GestureData, GameInterface）
2. **JSON协议是唯一的通信标准**，不要自己发明新的数据格式
3. **Python只做手势识别**，不要在里面写游戏逻辑
4. **Java只做游戏+UI**，不要在里面重复实现手势识别
5. **坐标必须是归一化坐标（0.0~1.0）**
6. **所有游戏必须实现 GameInterface 全部9个方法**
7. **不要引入新依赖**，需要新库先团队商量
8. **每帧清空Canvas重画**
9. **不要自己写 main 方法**，统一由 MainApp 启动
10. **Python退出时不要报错**，优雅关闭WebSocket连接

---

## 13. 依赖清单

### pom.xml（Java）

```xml
<java.version>17</java.version>

<!-- JavaFX -->
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>17.0.6</version>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-fxml</artifactId>
    <version>17.0.6</version>
</dependency>

<!-- WebSocket Server（已有） -->
<dependency>
    <groupId>org.java-websocket</groupId>
    <artifactId>Java-WebSocket</artifactId>
    <version>1.5.7</version>
</dependency>

<!-- JSON 解析（已有） -->
<dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20240303</version>
</dependency>
```

### requirements.txt（Python）

```
mediapipe>=0.10.0
opencv-python>=4.8.0
websockets>=12.0
```

---

## 14. 建议分工

| 人员 | 负责 | 文件 |
|------|------|------|
| **人A** | Python手势识别 + Java公共层 | gesture_server.py, requirements.txt, GestureType, GestureData, GameInterface |
| **人B** | 6个Java游戏 | CatchFruit, ZumaGame, RPSGame, TarotGame, FruitNinja, RhythmMaster |
| **人C** | Java UI + 整合 | 改造 LobbyController/LoginController, 新建 GameRenderer/Game.fxml, 改造 MainApp/AppStateManager/GestureSocketServer |

---

## 15. 开发顺序

```
第1步（三人一起）：搭环境
  - 人A：装Python + pip install mediapipe opencv-python websockets
  - 人B、人C：装JDK 17 + Maven，clone项目，mvn compile 跑通

第2步：打通数据链路（最关键！）
  - 人A完成 gesture_server.py
  - 人C改造 GestureSocketServer 解析 JSON → GestureData
  - 验证：Python发数据 → Java收到 → 打印日志 ✅

第3步：大厅+登录跑通
  - 人C改造 LobbyController（6游戏） + GameRenderer
  - 验证：登录 → 大厅 → 选游戏 → 进入空白游戏画布 ✅

第4步：各自写游戏
  - 人B写6个游戏（可以先写完2个简单的，验证框架通了再写复杂的）
  - 人A协助调试手势识别

第5步：整合测试+打包
```

---

## 16. 常见问题

**Q: Python 和 Java 启动顺序？**
- 先启动 Python（gesture_server.py），再启动 Java
- Python 会等待 Java 连接，顺序反了也没事，Python 会一直等

**Q: 端口被占用？**
- 默认端口 8765，如果被占用，同时改 Python 和 Java 的端口号

**Q: WebSocket 连接不上？**
- 检查 Python 是否已启动
- 检查防火墙是否拦截了本地 8765 端口
- 在 Python 终端看有没有打印 "等待连接..."

**Q: 手势识别不准？**
- MediaPipe 基本不会不准
- 确保摄像头画面里只有手，不要有其他人的手
- 光线太暗时 MediaPipe 也能工作，但最好保证基本照明

**Q: 怎么测试自己的代码？**
- 人A：单独跑 Python，看终端打印的手势数据对不对
- 人B：用 MockGestureData 模拟手势输入，不用等 Python
- 人C：用假 GestureData 测试大厅切换和游戏启动
