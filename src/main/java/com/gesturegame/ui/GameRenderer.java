package com.gesturegame.ui;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.game.TarotGame;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureStreamServer.DualHandState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * 游戏场景渲染器，同时作为 {@code Game.fxml} 的控制器。
 *
 * <p>职责：
 * <ul>
 *   <li>管理全屏 {@link Canvas} 与 {@link GraphicsContext}</li>
 *   <li>{@link #tick(GestureData, GameInterface)} 每帧由 {@code AnimationTimer} 驱动：
 *       GAME 态调用 {@code game.update/render}；GAME_OVER 态冻结最后一帧并显示结算</li>
 *   <li>游戏结束后等待握拳重玩，或双手入镜保持返回</li>
 *   <li>窗口缩放时重开当前局，保证画面与画布尺寸一致</li>
 * </ul>
 */
public class GameRenderer {

    private static final Logger LOGGER = Logger.getLogger(GameRenderer.class.getName());

    @FXML
    private Canvas gameCanvas;

    @FXML
    private Label gameNameLabel;

    @FXML
    private Label scoreLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private StackPane tarotQuestionOverlay;

    @FXML
    private TextField tarotQuestionField;

    private AppStateManager appStateManager;
    private GraphicsContext gc;
    private GameInterface currentGame;
    private boolean gameOverHandled;
    private boolean settling;
    private double lastInitWidth;
    private double lastInitHeight;
    private Difficulty selectedDifficulty = Difficulty.NORMAL;
    private int compactHoldFrames;
    private int openHoldFrames;
    private int exitHoldFrames;
    private int exitDropoutFrames;
    private int diffDropoutFrames; // 难度选择手势闪断容错
    private int difficultyHoldIndex = -1;
    private double difficultyCursorX = Double.NaN;
    private double difficultyCursorY = Double.NaN;
    private double difficultyCursorOpacity;
    private final double[] difficultyLift = new double[Difficulty.values().length];
    private long lastDifficultyFrameNanos;
    private double difficultyFade;
    private long settlementStartedNanos;
    private final List<SettlementParticle> settlementParticles = new ArrayList<>();

    // 选歌阶段
    private boolean songSelectPhase;
    private int songSelectIdx;
    private int songSelectFrames;
    private static final int SONG_SELECT_DEBOUNCE = 45;

    // 无尽模式子菜单（复用难度选择界面）
    private boolean endlessSubMenu;
    private int endlessSubLeaderIdx;  // -1=菜单, 0=或1=选项, 2=看榜
    private GameInterface endlessSubGame;
    private java.util.List<com.gesturegame.persistence.LeaderboardStore.LeaderboardEntry> endlessLeaderboard;
    private final com.gesturegame.persistence.LeaderboardStore leaderboard =
            new com.gesturegame.persistence.LeaderboardStore();
    private java.util.List<com.gesturegame.persistence.LeaderboardStore.LeaderboardEntry> settlementLeaderboard;

    private double mouseY = -1;
    private boolean mouseClicked;
    private double settlementParticleWidth = -1;
    private double settlementParticleHeight = -1;
    private WritableImage cssRainImage;
    private int[] cssRainPixels;
    private int cssRainImageWidth;
    private int cssRainImageHeight;
    private long cssRainLastFrameNanos;
    private static final int HOLD_FRAMES = 72; // 1.2秒@60fps
    private static final int DROPOUT_MAX = 3;  // 最多容忍3帧闪断
    private static final int DIFFICULTY_HOLD_FRAMES = 48; // 难度确认0.8秒，反馈更直接
    private static final int DIFFICULTY_DROPOUT_MAX = 12; // 允许短暂识别抖动，不让进度频繁归零

    @FXML
    public void initialize() {
        gc = gameCanvas.getGraphicsContext2D();
        if (gameCanvas.getParent() instanceof Pane) {
            Pane parent = (Pane) gameCanvas.getParent();
            gameCanvas.widthProperty().bind(parent.widthProperty());
            gameCanvas.heightProperty().bind(parent.heightProperty());
        }
        gameCanvas.widthProperty().addListener((obs, oldW, newW) -> reinitOnResize());
        gameCanvas.heightProperty().addListener((obs, oldH, newH) -> reinitOnResize());

        // 鼠标支持（选歌界面用）
        gameCanvas.setOnMouseMoved(e -> mouseY = e.getY());
        gameCanvas.setOnMouseClicked(e -> { mouseY = e.getY(); mouseClicked = true; });
        gameCanvas.setOnMouseExited(e -> mouseY = -1);
        clearCanvas();
    }

    public void bindStateManager(AppStateManager appStateManager) {
        this.appStateManager = appStateManager;
    }

    public Canvas getCanvas() {
        return gameCanvas;
    }

    private void setGameHudVisible(boolean visible) {
        if (gameNameLabel != null) gameNameLabel.setVisible(visible);
        if (scoreLabel != null) scoreLabel.setVisible(visible);
        if (statusLabel != null) statusLabel.setVisible(visible);
    }

    /**
     * 游戏循环每帧调用：GAME 态更新+渲染并检测结束；GAME_OVER 态冻结画面。
     * 仅在 GAME / GAME_OVER 状态下由 MainApp 的 AnimationTimer 调用。
     */
    public void tick(GestureData gesture, GameInterface game) {
        tick(gesture, game, new DualHandState(false, false, 0.0, false, 0.0, 0.0));
    }

    public void tick(GestureData gesture, GameInterface game, DualHandState dualHands) {
        if (gc == null) {
            return;
        }

        String state = AppStateManager.getInstance().getCurrentState();
        // 游戏内的标题、分数和操作提示由各游戏自行绘制，避免统一 HUD
        // 把塔罗的紫金样式叠到所有游戏上。
        setGameHudVisible(false);
        if (AppStateManager.STATE_GAME_OVER.equals(state)) {
            setGameHudVisible(false);
            updateExitHold(dualHands);
            if (exitHoldFrames >= HOLD_FRAMES) {
                exitHoldFrames = 0;
                exitToDifficulty();
                return;
            }
            drawSettlementScreen(gc, game, gameCanvas.getWidth(), gameCanvas.getHeight());
            return;
        }
        if (!AppStateManager.STATE_GAME.equals(state)) {
            return;
        }

        if (game == null) {
            clearCanvas();
            if (statusLabel != null) {
                statusLabel.setText("等待游戏加载...");
            }
            return;
        }

        if (game != currentGame) {
            currentGame = game;
            gameOverHandled = false;
            settling = false;
            settlementStartedNanos = 0;
            initGame(game);
            if (statusLabel != null) {
                statusLabel.setText("塔罗牌".equals(game.getName()) ? "" : "双手入镜保持返回");
            }
        }

        updateExitHold(dualHands);
        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            exitToDifficulty();
            return;
        }

        // 单手基础手势完整交给游戏；双手系统模式只屏蔽输入，不暂停游戏动画。
        game.update(dualHands.active() ? new GestureData() : gesture);
        game.render(gc);
        // 保留全局退出判定及确认进度，但不再绘制统一文字提示。
        drawExitProgress(gesture, dualHands);

        boolean tarotMode = "塔罗牌".equals(game.getName());
        boolean fruitNinjaMode = "切水果".equals(game.getName());
        boolean catchFruitMode = "接水果".equals(game.getName());
        if (gameNameLabel != null) {
            gameNameLabel.setText(tarotMode || fruitNinjaMode || catchFruitMode
                    ? "" : game.getIcon() + "  " + game.getName());
        }
        if (scoreLabel != null) {
            scoreLabel.setText(tarotMode || fruitNinjaMode || catchFruitMode ? "" : "分数: " + game.getScore());
        }
        if (statusLabel != null && tarotMode) {
            statusLabel.setText("");
        }

        if (game.isOver() && !gameOverHandled) {
            gameOverHandled = true;
            settling = true;
            settlementStartedNanos = System.nanoTime();
            if (statusLabel != null) statusLabel.setText("");
            LOGGER.info(() -> "[GameRenderer] 游戏结束: " + game.getName() + " 得分=" + game.getScore());
            // 无尽模式保存排行榜
            if (game.getDifficulty() == Difficulty.ENDLESS && appStateManager != null) {
                String user = appStateManager.getSignedInUser();
                if (user != null && !user.isBlank()) {
                    leaderboard.saveScore(game.getName(), "ENDLESS", user,
                            game.getScore(), game.getMaxCombo());
                }
                settlementLeaderboard = leaderboard.getTop(game.getName(), "ENDLESS", 5);
            } else {
                settlementLeaderboard = null;
            }
            if (appStateManager != null) {
                appStateManager.switchState(AppStateManager.STATE_GAME_OVER);
            }
        }
    }

    /**
     * 接收串流服务器派发的手势命令。
     * GAME 态仅响应 BACK（返回大厅）；GAME_OVER 态响应 CONFIRM（重玩）/BACK（回大厅）。
     * {@code settling} 标志保证结算态下仅处理一次命令，避免重复切换场景。
     */
    public void handleAgentCommand(GestureCommand command, double confidence, String hand) {
        Platform.runLater(() -> {
            String state = AppStateManager.getInstance().getCurrentState();

            if (AppStateManager.STATE_GAME.equals(state)) {
                if (command == GestureCommand.BACK) {
                    LOGGER.info(() -> "[GameRenderer] 收到 BACK 指令，返回难度选择");
                    exitToDifficulty();
                }
                return;
            }

            if (AppStateManager.STATE_GAME_OVER.equals(state) && settling) {
                if (command == GestureCommand.CONFIRM) {
                    LOGGER.info(() -> "[GameRenderer] 结算态收到 CONFIRM，重玩当前游戏");
                    settling = false;
                    GameInterface game = AppStateManager.getInstance().getActiveGame();
                    if (game != null) {
                        game.reset();
                    }
                    currentGame = null;
                    gameOverHandled = false;
                    if (appStateManager != null) {
                        appStateManager.switchState(AppStateManager.STATE_GAME);
                    }
                } else if (command == GestureCommand.BACK) {
                    LOGGER.info(() -> "[GameRenderer] 结算态收到 BACK，返回难度选择");
                    settling = false;
                    exitToDifficulty();
                }
            }
        });
    }

    private void exitToDifficulty() {
        hideTarotQuestionPrompt();
        exitHoldFrames = 0;
        compactHoldFrames = 0;
        openHoldFrames = 0;
        settlementStartedNanos = 0;
        songSelectPhase = false;
        endlessSubMenu = false;
        mouseY = -1;
        // 重置当前游戏（清理WebView等资源）
        if (currentGame != null) {
            currentGame.reset();
        }
        currentGame = null;
        gameOverHandled = false;
        settling = false;
        if (gameNameLabel != null) gameNameLabel.setText("");
        if (scoreLabel != null) scoreLabel.setText("");
        clearCanvas();
        if (appStateManager != null) {
            appStateManager.switchState(AppStateManager.STATE_DIFFICULTY);
        }
    }

    private void exitToLobby() {
        hideTarotQuestionPrompt();
        exitHoldFrames = 0;
        compactHoldFrames = 0;
        openHoldFrames = 0;
        settlementStartedNanos = 0;
        songSelectPhase = false;
        endlessSubMenu = false;
        GameInterface game = AppStateManager.getInstance().getActiveGame();
        if (game != null) {
            game.reset();
        }
        currentGame = null;
        gameOverHandled = false;
        settling = false;
        if (appStateManager != null) {
            appStateManager.switchState(AppStateManager.STATE_LOBBY);
        }
    }

    private void initGame(GameInterface game) {
        int w = (int) Math.max(1, gameCanvas.getWidth());
        int h = (int) Math.max(1, gameCanvas.getHeight());
        game.init(w, h);
        lastInitWidth = w;
        lastInitHeight = h;
        LOGGER.info(() -> "[GameRenderer] 初始化游戏: " + game.getName() + " (" + w + "x" + h + ")");
    }

    /**
     * 窗口缩放时重开当前局，使游戏画面与新画布尺寸匹配。
     * 仅在 GAME 态且尺寸确实变化时触发，避免重复初始化。
     */
    private void reinitOnResize() {
        if (gc == null || currentGame == null) {
            return;
        }
        String state = AppStateManager.getInstance().getCurrentState();
        if (!AppStateManager.STATE_GAME.equals(state)) {
            return;
        }
        int w = (int) Math.max(1, gameCanvas.getWidth());
        int h = (int) Math.max(1, gameCanvas.getHeight());
        if (w == lastInitWidth && h == lastInitHeight) {
            return;
        }
        currentGame.init(w, h);
        lastInitWidth = w;
        lastInitHeight = h;
        LOGGER.info(() -> "[GameRenderer] 画布缩放，重开当前局: " + w + "x" + h);
    }

    /** 难度选择界面：手移选难度，握拳确认 */
    public void tickDifficultySelect(GestureData gesture) {
        tickDifficultySelect(gesture, new DualHandState(false, false, 0.0, false, 0.0, 0.0));
    }

    public void tickDifficultySelect(GestureData gesture, DualHandState dualHands) {
        if (gameCanvas == null) return;
        setGameHudVisible(false);
        GraphicsContext g = gameCanvas.getGraphicsContext2D();
        double w = gameCanvas.getWidth();
        double h = gameCanvas.getHeight();

        // === 选歌阶段 ===
        if (songSelectPhase) {
            songSelectFrames++;
            GameInterface game = AppStateManager.getInstance().getActiveGame();
            if (game == null) return;

            // 双手入镜返回难度选择
            if (dualHands.captured()) {
                songSelectPhase = false;
                mouseY = -1;
                exitToDifficulty();
                return;
            }

            // 鼠标 → 悬停选歌，点击确认
            GestureType effectiveGesture = GestureType.NONE;
            boolean effectiveHand = false;
            double effectiveHandY = 0.5;
            double effectiveHandX = 0.5;

            if (mouseY >= 0) {
                effectiveHandY = mouseY / gameCanvas.getHeight();
                effectiveHandX = 0.5;
                effectiveHand = true;
            } else if (gesture != null && gesture.isHandDetected()) {
                // 手势选歌
                effectiveHandY = gesture.getHandY();
                effectiveHandX = gesture.getHandX();
                effectiveHand = true;
                effectiveGesture = gesture.getGesture();
            }

            // 防抖
            if (songSelectFrames < SONG_SELECT_DEBOUNCE && effectiveGesture == GestureType.FIST) {
                effectiveGesture = GestureType.NONE;
            }

            GestureData safeGesture = new GestureData(
                    effectiveHandX, effectiveHandY,
                    effectiveHandX, effectiveHandY,
                    0, 0,
                    effectiveGesture, 1.0, effectiveHand);

            game.update(safeGesture);

            // 鼠标点击 → 选歌确认 或 BPM校准打拍
            if (mouseClicked && songSelectFrames >= SONG_SELECT_DEBOUNCE) {
                mouseClicked = false;
                if (game instanceof com.gesturegame.game.RhythmMaster) {
                    com.gesturegame.game.RhythmMaster rm = (com.gesturegame.game.RhythmMaster) game;
                    if (rm.isSongConfirmed()) {
                        // 已确认，忽略
                    } else if (rm.isCalibrating()) {
                        // 校准阶段 → 点击算打拍
                        rm.tapFromMouse();
                    } else {
                        // 选歌阶段 → 点击确认
                        rm.clickConfirmSong();
                    }
                }
            }

            // 背景 + 游戏自己画选歌 UI
            g.setFill(Color.web("#05051a"));
            g.fillRect(0, 0, w, h);
            game.render(g);

            // 确认后切换到 GAME
            if (game instanceof com.gesturegame.game.RhythmMaster) {
                com.gesturegame.game.RhythmMaster rm = (com.gesturegame.game.RhythmMaster) game;
                if (rm.isSongConfirmed()) {
                    songSelectPhase = false;
                    mouseY = -1;
                    currentGame = game;
                    AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
                }
            }
            return;
        }

        g.setFill(Color.web("#120718"));
        g.fillRect(0, 0, w, h);

        GameInterface activeGame = AppStateManager.getInstance().getActiveGame();
        boolean tarotMode = activeGame != null && "塔罗牌".equals(activeGame.getName());
        if (tarotMode) {
            drawTarotEntryScreen(g, gesture, dualHands, w, h, activeGame);
            return;
        }
        if (drawStackedDifficultySelect(g, gesture, dualHands, w, h, activeGame)) {
            return;
        }
        Difficulty[] all = Difficulty.values();
        java.util.List<Difficulty> supported = new java.util.ArrayList<>();
        for (Difficulty d : all) {
            if (activeGame == null || activeGame.supportsDifficulty(d)) {
                supported.add(d);
            }
        }
        Difficulty[] diffs = supported.toArray(new Difficulty[0]);
        int n = diffs.length;
        double cardW = 150, cardH = 180, gap = 20;
        double totalW = cardW * n + gap * (n - 1);
        double startX = (w - totalW) / 2;
        double cardY = h / 2 - cardH / 2;
        int hoveredIndex = -1;
        updateExitHold(dualHands);

        // 手势逻辑
        if (gesture != null && gesture.isHandDetected()) {
            double hx = gesture.getHandX() * w;
            double hy = gesture.getHandY() * h;
            for (int i = 0; i < n; i++) {
                double cx = startX + i * (cardW + gap);
                if (hx >= cx && hx <= cx + cardW && hy >= cardY && hy <= cardY + cardH) {
                    hoveredIndex = i;
                    break;
                }
            }

            GestureType gestureType = gesture.getGesture();
            boolean isFist = !dualHands.captured() && gestureType == GestureType.FIST;

            if (isFist && hoveredIndex >= 0) {
                compactHoldFrames++;
                diffDropoutFrames = 0;
            } else if (compactHoldFrames > 0 && diffDropoutFrames < DROPOUT_MAX) {
                diffDropoutFrames++; // 容错：允许几帧闪断
            } else {
                compactHoldFrames = 0;
                diffDropoutFrames = 0;
            }
        } else {
            compactHoldFrames = 0;
            diffDropoutFrames = 0;
        }

        // 握拳确认(1.2s) → 进入选歌/无尽菜单/游戏
        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            GameInterface game = AppStateManager.getInstance().getActiveGame();
            if (game != null) {
                // 无尽子菜单：确认开始游戏或查看排行榜
                if (endlessSubMenu) {
                    if (selectedDifficulty == Difficulty.EASY) {
                        endlessSubMenu = false;
                        game.setDifficulty(Difficulty.ENDLESS);
                        initGame(game);
                        currentGame = game;
                        AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
                    } else {
                        endlessSubLeaderIdx = 2;
                    }
                    return;
                }
                game.setDifficulty(selectedDifficulty);
                LOGGER.info("难度选择确认: " + selectedDifficulty.getLabel() + " → " + game.getName());
                // 节奏大师：先进选歌界面
                if (game instanceof com.gesturegame.game.RhythmMaster) {
                    initGame(game);
                    songSelectPhase = true;
                    songSelectFrames = 0;
                    return;
                }
                // 选无尽模式 → 进子菜单（不进游戏）
                if (selectedDifficulty == Difficulty.ENDLESS) {
                    endlessSubMenu = true;
                    endlessSubLeaderIdx = -1;
                    endlessSubGame = game;
                    endlessLeaderboard = leaderboard.getTop(game.getName(), "ENDLESS", 5);
                    selectedDifficulty = Difficulty.EASY;
                    return;
                }
            }
            currentGame = null;
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return;
        }

        // 双手稳定保持 → 返回大厅
        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            endlessSubMenu = false;
            LOGGER.info("难度选择取消，返回大厅");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_LOBBY);
            return;
        }

        if (hoveredIndex >= 0) selectedDifficulty = diffs[hoveredIndex];

        // 渲染
        for (int i = 0; i < n; i++) {
            double cx = startX + i * (cardW + gap);
            boolean sel = i == hoveredIndex || (hoveredIndex < 0 && diffs[i] == selectedDifficulty);
            if (sel) {
                g.setFill(Color.web("#2a133a"));
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(3);
            } else {
                g.setFill(Color.web("#160b20"));
                g.setStroke(Color.web("#8b5bd144"));
                g.setLineWidth(1);
            }
            g.fillRoundRect(cx, cardY, cardW, cardH, 16, 16);
            g.strokeRoundRect(cx, cardY, cardW, cardH, 16, 16);

            // 文字居中
            g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            double midX = cx + cardW / 2;

            String label = activeGame != null
                    ? activeGame.getDifficultyLabel(diffs[i])
                    : diffs[i].getLabel();
            boolean hasCustomLabel = activeGame != null
                    && !activeGame.getDifficultyLabel(diffs[i]).equals(diffs[i].getLabel());

            if (hasCustomLabel) {
                // 仅显示自定义文字（如"三局两胜"），居中大字
                g.setFill(sel ? Color.WHITE : Color.web("#94a3b8"));
                g.setFont(javafx.scene.text.Font.font(20));
                g.fillText(label, midX, cardY + cardH / 2 + 6);
            } else {
                // 标准显示：标题 + 星星
                g.setFill(sel ? Color.WHITE : Color.web("#94a3b8"));
                g.setFont(javafx.scene.text.Font.font(18));
                g.fillText(label, midX, cardY + 40);
                g.setFont(javafx.scene.text.Font.font(36));
                g.fillText(diffs[i].getStars(), midX, cardY + 100);
            }
            g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        }

        // 手光标
        if (gesture != null && gesture.isHandDetected()) {
            double cx = gesture.getHandX() * w;
            double cy = gesture.getHandY() * h;

            if (compactHoldFrames > 0 && hoveredIndex >= 0) {
                // FIST确认中：金色光晕 + 进度环
                g.setFill(Color.color(0.94, 0.79, 0.47, 0.12));
                g.fillOval(cx - 22, cy - 22, 44, 44);
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(2.0);
                g.strokeOval(cx - 16, cy - 16, 32, 32);
                double progress = (double) compactHoldFrames / HOLD_FRAMES;
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(3);
                g.strokeArc(cx - 20, cy - 20, 40, 40,
                        90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
            }

            if (exitHoldFrames > 0) {
                // 返回中：紫色光晕 + 进度环
                double ep = (double) exitHoldFrames / HOLD_FRAMES;
                g.setFill(Color.color(0.55, 0.35, 0.85, 0.1));
                g.fillOval(cx - 22, cy - 22, 44, 44);
                g.setStroke(Color.web("#a78bfa"));
                g.setLineWidth(2.0);
                g.strokeOval(cx - 16, cy - 16, 32, 32);
                g.setStroke(Color.web("#a78bfa"));
                g.setLineWidth(3);
                g.strokeArc(cx - 20, cy - 20, 40, 40,
                        90, -360 * ep, javafx.scene.shape.ArcType.OPEN);
            }

            // 中心小点（始终显示）
            g.setFill(Color.web("#f0ca79"));
            g.fillOval(cx - 3, cy - 3, 6, 6);
        }

        g.setFill(Color.web("#f0ca79"));
        g.setFont(javafx.scene.text.Font.font(16));
        drawExitTarget(g, w, h);
        drawExitProgress(gesture, dualHands);
        g.fillText("手移选难度 | ✊握拳确认 | 双手入镜保持返回", w / 2 - 150, cardY + cardH + 50);
    }

    /** 参考 DisplayCards 的倾斜玻璃叠卡难度界面。 */
    private boolean drawStackedDifficultySelect(GraphicsContext g, GestureData gesture,
                                                DualHandState dualHands, double w, double h,
                                                GameInterface activeGame) {
        long now = System.nanoTime();
        boolean entering = lastDifficultyFrameNanos == 0
                || now - lastDifficultyFrameNanos > 500_000_000L;
        lastDifficultyFrameNanos = now;
        if (entering) {
            difficultyFade = 0;
            java.util.Arrays.fill(difficultyLift, 0);
            compactHoldFrames = 0;
            diffDropoutFrames = 0;
            difficultyHoldIndex = -1;
            exitHoldFrames = 0;
            exitDropoutFrames = 0;
        }
        difficultyFade += (1.0 - difficultyFade) * 0.12;

        java.util.List<Difficulty> supported = new java.util.ArrayList<>();
        // 无尽子菜单：只显示"开始游戏"和"查看排行榜"
        if (endlessSubMenu) {
            supported.add(Difficulty.EASY);   // → 开始游戏
            supported.add(Difficulty.HARD);   // → 查看排行榜
        } else {
            for (Difficulty difficulty : Difficulty.values()) {
                if (activeGame == null || activeGame.supportsDifficulty(difficulty)) {
                    supported.add(difficulty);
                }
            }
        }
        Difficulty[] diffs = supported.toArray(new Difficulty[0]);
        if (diffs.length == 0) return true;

        int selectedIndex = -1;
        for (int i = 0; i < diffs.length; i++) {
            if (diffs[i] == selectedDifficulty) selectedIndex = i;
        }
        if (selectedIndex < 0) {
            selectedIndex = Math.min(1, diffs.length - 1);
            selectedDifficulty = diffs[selectedIndex];
        }

        int n = diffs.length;
        double cardW = clampDifficulty(w * 0.275, 300, 352);
        double cardH = cardW * 144.0 / 352.0;
        double offsetX = n > 3 ? 54 : 64;
        double offsetY = n > 3 ? 32 : 40;
        double stackW = cardW + offsetX * (n - 1);
        double stackH = cardH + offsetY * (n - 1);
        double baseX = (w - stackW) / 2.0;
        double baseY = (h - stackH) / 2.0 + 18;
        double[] cardX = new double[n];
        double[] cardY = new double[n];
        for (int i = 0; i < n; i++) {
            cardX[i] = baseX + i * offsetX;
            cardY[i] = baseY + i * offsetY - difficultyLift[i];
        }

        int hoveredIndex = -1;
        updateExitHold(dualHands);
        updateDifficultyCursor(gesture, dualHands, w, h);

        // 排行榜查看模式：只响应双手入镜返回，不处理选卡手势
        if (endlessSubLeaderIdx == 2) {
            if (dualHands.captured()) {
                exitHoldFrames++;
                if (exitHoldFrames >= HOLD_FRAMES) {
                    endlessSubLeaderIdx = -1;
                    exitHoldFrames = 0;
                }
            } else {
                exitHoldFrames = 0;
            }
            // 直接渲染排行榜，跳过卡片手势
            drawDifficultyGlassBackground(g, w, h, activeGame);
            renderLeaderboardOverlay(g, w, h);
            if (difficultyCursorOpacity > 0.015 && !Double.isNaN(difficultyCursorX)) {
                g.save(); g.setGlobalAlpha(difficultyCursorOpacity);
                drawDifficultyCursor(g, difficultyCursorX, difficultyCursorY, 0, 0);
                g.restore();
            }
            return true;
        }

        if (gesture != null && gesture.isHandDetected() && !dualHands.captured()) {
            double hx = gesture.getHandX() * w;
            double hy = gesture.getHandY() * h;
            hoveredIndex = resolveDifficultyHover(
                    hx, hy, baseX, baseY, stackW, stackH,
                    cardW, cardH, offsetX, offsetY, n);

            boolean isFist = gesture.getGesture() == GestureType.FIST;
            if (hoveredIndex >= 0 && !isFist) {
                selectedDifficulty = diffs[hoveredIndex];
                selectedIndex = hoveredIndex;
            }

            if (isFist && hoveredIndex >= 0) {
                // 握拳时锁定开始确认的卡片，避免手型变化导致坐标轻微偏移后跳到相邻难度。
                if (difficultyHoldIndex < 0) {
                    difficultyHoldIndex = hoveredIndex;
                    compactHoldFrames = 0;
                }
                selectedIndex = difficultyHoldIndex;
                selectedDifficulty = diffs[difficultyHoldIndex];
                compactHoldFrames++;
                diffDropoutFrames = 0;
            } else if (compactHoldFrames > 0 && diffDropoutFrames < DIFFICULTY_DROPOUT_MAX) {
                diffDropoutFrames++;
            } else {
                compactHoldFrames = 0;
                diffDropoutFrames = 0;
                difficultyHoldIndex = -1;
            }
        } else {
            if (compactHoldFrames > 0 && diffDropoutFrames < DIFFICULTY_DROPOUT_MAX) {
                diffDropoutFrames++;
            } else {
                compactHoldFrames = 0;
                diffDropoutFrames = 0;
                difficultyHoldIndex = -1;
            }
        }

        if (compactHoldFrames >= DIFFICULTY_HOLD_FRAMES) {
            compactHoldFrames = 0;
            difficultyHoldIndex = -1;
            if (activeGame != null) {
                // 无尽子菜单确认
                if (endlessSubMenu) {
                    if (selectedDifficulty == Difficulty.EASY) {
                        endlessSubMenu = false;
                        activeGame.setDifficulty(Difficulty.ENDLESS);
                        initGame(activeGame);
                        currentGame = activeGame;
                        AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
                    } else {
                        endlessSubLeaderIdx = 2;
                    }
                    return true;
                }
                activeGame.setDifficulty(selectedDifficulty);
                LOGGER.info("难度选择确认: " + selectedDifficulty.getLabel() + " → " + activeGame.getName());
                if (selectedDifficulty == Difficulty.ENDLESS) {
                    endlessSubMenu = true;
                    endlessSubLeaderIdx = -1;
                    endlessSubGame = activeGame;
                    endlessLeaderboard = leaderboard.getTop(activeGame.getName(), "ENDLESS", 5);
                    selectedDifficulty = Difficulty.EASY;
                    return true;
                }
            }
            currentGame = null;
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return true;
        }

        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            endlessSubMenu = false;
            LOGGER.info("难度选择取消，返回大厅");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_LOBBY);
            return true;
        }

        for (int i = 0; i < n; i++) {
            double targetLift = i == hoveredIndex ? 40.0 : 0.0;
            difficultyLift[i] += (targetLift - difficultyLift[i]) * 0.14;
            cardY[i] = baseY + i * offsetY - difficultyLift[i];
        }

        drawDifficultyGlassBackground(g, w, h, activeGame);
        g.setGlobalAlpha(difficultyFade);
        for (int i = 0; i < n; i++) {
            double progress = i == selectedIndex
                    ? compactHoldFrames / (double) DIFFICULTY_HOLD_FRAMES : 0;
            String labelOverride = null;
            if (endlessSubMenu) {
                labelOverride = (i == 0) ? "🎮  开始游戏" : "🏆  查看排行榜";
            }
            drawDifficultyGlassCard(g, diffs[i], activeGame, cardX[i], cardY[i],
                    cardW, cardH, i == selectedIndex, progress, labelOverride);
        }
        g.setGlobalAlpha(1.0);

        // 无尽子菜单：替换卡片上的文字
        if (endlessSubMenu) {
            String[] labels = {"🎮  开始游戏", "🏆  查看排行榜"};
            for (int i = 0; i < n; i++) {
                boolean sel = i == selectedIndex;
                double shear = Math.tan(Math.toRadians(-8));
                g.save();
                g.translate(cardX[i], cardY[i]);
                g.transform(1, shear, 0, 1, 0, -shear * cardW / 2.0);
                g.setFill(sel ? Color.web("#3B82F6") : Color.rgb(161, 161, 170, 0.68));
                g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
                g.fillText(labels[i], 54, 35);
                g.restore();
            }
        }

        if (difficultyCursorOpacity > 0.015 && !Double.isNaN(difficultyCursorX)) {
            g.save();
            g.setGlobalAlpha(difficultyCursorOpacity);
            double fistPct = compactHoldFrames / (double) DIFFICULTY_HOLD_FRAMES;
            double exitPct = exitHoldFrames / (double) HOLD_FRAMES;
            drawDifficultyCursor(g, difficultyCursorX, difficultyCursorY, fistPct, exitPct);
            g.restore();
        }

        drawDifficultyExitCountdown(g, gesture, dualHands, w, h);

        return true;
    }

    /** 排行榜全屏覆盖 */
    private void renderLeaderboardOverlay(GraphicsContext g, double w, double h) {
        g.setFill(Color.rgb(0, 0, 0, 0.78));
        g.fillRect(0, 0, w, h);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Microsoft YaHei UI", 24));
        g.setTextAlign(TextAlignment.CENTER);
        String gn = endlessSubGame != null ? endlessSubGame.getName() : "";
        g.fillText("🏆 " + gn + " 排行榜 TOP 5", w * 0.5, h * 0.15);

        int en = endlessLeaderboard != null ? Math.min(endlessLeaderboard.size(), 5) : 0;
        if (en == 0) {
            g.setFill(Color.rgb(255, 255, 255, 0.4));
            g.setFont(Font.font("Microsoft YaHei UI", 16));
            g.fillText("暂无记录", w * 0.5, h * 0.50);
        } else {
            g.setFont(Font.font("Consolas", 15));
            for (int i = 0; i < en; i++) {
                var e = endlessLeaderboard.get(i);
                g.setFill(i == 0 ? Color.GOLD : i == 1 ? Color.SILVER : i == 2
                        ? Color.web("#cd7f32") : Color.rgb(200, 200, 200));
                g.fillText(String.format("%d.  %-10s  %6d  %d combo",
                        i + 1, e.username(), e.score(), e.maxCombo()),
                        w * 0.5, h * 0.30 + i * 28);
            }
        }
        g.setFill(Color.rgb(255, 255, 255, 0.4));
        g.setFont(Font.font("Microsoft YaHei UI", 14));
        g.fillText("🤲 双手入镜返回菜单", w * 0.5, h * 0.85);
        g.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * 叠卡大量重合，不能再用"当前卡优先"的矩形命中，否则选中后会把其他卡全部挡住。
     * 在整个卡堆的扩展范围内选择距离各卡左上可见标签最近的一张，卡片抬升动画不影响命中区。
     */
    private int resolveDifficultyHover(double handX, double handY,
                                       double baseX, double baseY, double stackW, double stackH,
                                       double cardW, double cardH, double offsetX, double offsetY,
                                       int count) {
        double paddingX = Math.max(48.0, cardW * 0.16);
        double paddingY = Math.max(54.0, cardH * 0.45);
        if (handX < baseX - paddingX || handX > baseX + stackW + paddingX
                || handY < baseY - paddingY || handY > baseY + stackH + paddingY) {
            return -1;
        }

        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            // 叠卡真正可点击的是每张卡露出的图标/标题区域；后方卡片的中央会被前方卡覆盖。
            double anchorX = baseX + i * offsetX + cardW * 0.16;
            double anchorY = baseY + i * offsetY + cardH * 0.24;
            double dx = handX - anchorX;
            double dy = handY - anchorY;
            double distance = dx * dx + dy * dy * 1.15;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    /** 实时手势光标：大位移快速追随，小抖动平滑过滤。 */
    private void updateDifficultyCursor(GestureData gesture, DualHandState dualHands,
                                        double width, double height) {
        boolean visible = gesture != null && gesture.isHandDetected() && !dualHands.captured();
        // 双手退出时也要保持光标显示进度
        if (!visible && exitHoldFrames > 0 && dualHands.captured()) {
            visible = true;
            difficultyCursorOpacity = Math.min(1.0, difficultyCursorOpacity + 0.1);
        }
        if (!visible) {
            difficultyCursorOpacity *= 0.74;
            return;
        }

        double targetX, targetY;
        if (dualHands.captured() && exitHoldFrames > 0) {
            // 双手退出：用两手中心点
            targetX = ((gesture != null ? gesture.getHandX() : 0.5) + dualHands.secondHandX()) * 0.5 * width;
            targetY = ((gesture != null ? gesture.getHandY() : 0.5) + dualHands.secondHandY()) * 0.5 * height;
        } else {
            targetX = clampDifficulty(gesture.getHandX(), 0.0, 1.0) * width;
            targetY = clampDifficulty(gesture.getHandY(), 0.0, 1.0) * height;
        }
        if (Double.isNaN(difficultyCursorX) || difficultyCursorOpacity < 0.02) {
            difficultyCursorX = targetX;
            difficultyCursorY = targetY;
        } else {
            double distance = Math.hypot(targetX - difficultyCursorX, targetY - difficultyCursorY);
            double follow = distance > 110.0 ? 0.78 : 0.46;
            difficultyCursorX += (targetX - difficultyCursorX) * follow;
            difficultyCursorY += (targetY - difficultyCursorY) * follow;
        }
        difficultyCursorOpacity += (1.0 - difficultyCursorOpacity) * 0.42;
    }

    private void drawDifficultyGlassBackground(GraphicsContext g, double w, double h,
                                               GameInterface activeGame) {
        // Exact background sampled from the reference (Tailwind zinc-950).
        g.setFill(Color.web("#09090B"));
        g.fillRect(0, 0, w, h);
    }

    private void drawDifficultyGlassCard(GraphicsContext g, Difficulty difficulty,
                                         GameInterface activeGame, double x, double y,
                                         double cardW, double cardH, boolean selected,
                                         double holdProgress, String labelOverride) {
        Color accent = difficultyAccent(difficulty);
        double shear = Math.tan(Math.toRadians(-8));
        g.save();
        g.translate(x, y);
        g.transform(1, shear, 0, 1, 0, -shear * cardW / 2.0);

        // The reference uses bg-muted/70 over #09090b, not a blue-tinted pane.
        g.setFill(Color.rgb(0, 0, 0, 0.42));
        g.fillRoundRect(3, 7, cardW, cardH, 18, 18);
        LinearGradient glass = new LinearGradient(0, 0, 1, 1, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(39, 39, 42, 0.72)),
                new Stop(0.52, Color.rgb(31, 31, 35, 0.70)),
                new Stop(1, Color.rgb(24, 24, 27, 0.66)));
        g.setFill(glass);
        g.fillRoundRect(0, 0, cardW, cardH, 18, 18);

        LinearGradient edgeSheen = new LinearGradient(0, 0, 0, 1, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, selected ? 0.035 : 0.018)),
                new Stop(0.16, Color.TRANSPARENT),
                new Stop(1, Color.rgb(0, 0, 0, 0.08)));
        g.setFill(edgeSheen);
        g.fillRoundRect(1, 1, cardW - 2, cardH - 2, 17, 17);

        g.setStroke(selected ? Color.rgb(255, 255, 255, 0.18) : Color.rgb(82, 82, 91, 0.48));
        g.setLineWidth(2);
        g.strokeRoundRect(1, 1, cardW - 2, cardH - 2, 17, 17);

        g.setFill(selected ? Color.web("#1D4ED8") : Color.rgb(82, 82, 91, 0.82));
        g.fillOval(16, 14, 28, 28);
        g.setFill(selected ? Color.web("#93C5FD") : Color.rgb(212, 212, 216, 0.72));
        g.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, 15));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("✦", 30, 34);

        String label = labelOverride != null
                ? labelOverride
                : (activeGame == null ? difficulty.getLabel() : activeGame.getDifficultyLabel(difficulty));
        g.setTextAlign(TextAlignment.LEFT);
        g.setFill(selected ? Color.web("#3B82F6") : Color.rgb(161, 161, 170, 0.68));
        g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        g.fillText(label, 54, 35);

        if (labelOverride == null) {
            g.setFill(Color.rgb(244, 244, 245, selected ? 0.91 : 0.52));
            g.setFont(Font.font("Microsoft YaHei", 16));
            g.fillText(difficultyDescription(difficulty), 16, cardH - 48, cardW - 32);
            g.setFill(Color.rgb(161, 161, 170, selected ? 0.72 : 0.40));
            g.setFont(Font.font("Consolas", 11));
            g.fillText(difficultyTag(difficulty), 16, cardH - 19);
        }

        // Mirrors the reference card's oversized right-side ::after mask.
        LinearGradient fade = new LinearGradient(0, 0, 1, 0, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.60, Color.rgb(9, 9, 11, selected ? 0.22 : 0.36)),
                new Stop(1, Color.rgb(9, 9, 11, 1.0)));
        g.setFill(fade);
        g.fillRoundRect(cardW * 0.09, 1, cardW * 0.91 - 1, cardH - 2, 17, 17);

        if (!selected) {
            // before:bg-background/50 + grayscale from the original component.
            g.setFill(Color.rgb(9, 9, 11, 0.50));
            g.fillRoundRect(0, 0, cardW, cardH, 18, 18);
        } else if (holdProgress > 0) {
            double progressW = cardW * clampDifficulty(holdProgress, 0, 1);
            g.setFill(Color.rgb(96, 165, 250, 0.18));
            g.fillRoundRect(0, cardH - 6, progressW, 6, 6, 6);
            g.setFill(accent);
            g.fillRoundRect(0, cardH - 2.2, progressW, 2.2, 2.2, 2.2);
        }
        g.restore();
    }

    private void drawDifficultyCursor(GraphicsContext g, double x, double y, double fistProgress, double exitProgress) {
        g.setFill(Color.rgb(147, 197, 253, 0.13));
        g.fillOval(x - 18, y - 18, 36, 36);
        g.setStroke(Color.web("#bfdbfe"));
        g.setLineWidth(1.5);
        g.strokeOval(x - 9, y - 9, 18, 18);
        g.setFill(Color.WHITE);
        g.fillOval(x - 2.5, y - 2.5, 5, 5);
        // 握拳确认进度（金色）
        if (fistProgress > 0) {
            g.setStroke(Color.web("#f0ca79"));
            g.setLineWidth(3);
            g.strokeArc(x - 23, y - 23, 46, 46, 90,
                    -360 * clampDifficulty(fistProgress, 0, 1), javafx.scene.shape.ArcType.OPEN);
        }
        // 双手返回进度（紫色）
        if (exitProgress > 0) {
            g.setStroke(Color.web("#a78bfa"));
            g.setLineWidth(3);
            g.strokeArc(x - 28, y - 28, 56, 56, 90,
                    -360 * clampDifficulty(exitProgress, 0, 1), javafx.scene.shape.ArcType.OPEN);
        }
    }

    /** 难度选择页的双手返回倒计时，显示在两只手之间。 */
    private void drawDifficultyExitCountdown(GraphicsContext g, GestureData gesture,
                                             DualHandState dualHands, double width, double height) {
        if (exitHoldFrames <= 0 || gesture == null || !gesture.isHandDetected()
                || !dualHands.captured()) {
            return;
        }

        double firstX = gesture.getHandX() * width;
        double firstY = gesture.getHandY() * height;
        double secondX = dualHands.secondHandX() * width;
        double secondY = dualHands.secondHandY() * height;
        double centerX = (firstX + secondX) * 0.5;
        double centerY = (firstY + secondY) * 0.5;
        double progress = clampDifficulty(exitHoldFrames / (double) HOLD_FRAMES, 0.0, 1.0);
        double secondsLeft = Math.max(0.0, (HOLD_FRAMES - exitHoldFrames) / 60.0);

        g.save();
        g.setStroke(Color.rgb(167, 139, 250, 0.34));
        g.setLineWidth(1.2);
        g.strokeLine(firstX, firstY, secondX, secondY);

        g.setFill(Color.rgb(9, 9, 11, 0.88));
        g.fillOval(centerX - 38, centerY - 38, 76, 76);
        g.setStroke(Color.rgb(167, 139, 250, 0.28));
        g.setLineWidth(2.0);
        g.strokeOval(centerX - 32, centerY - 32, 64, 64);
        g.setStroke(Color.web("#a78bfa"));
        g.setLineWidth(4.0);
        g.strokeArc(centerX - 34, centerY - 34, 68, 68,
                90, -360 * progress, javafx.scene.shape.ArcType.OPEN);

        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 17));
        g.fillText(String.format("%.1f", secondsLeft), centerX, centerY + 5);
        g.setFill(Color.rgb(196, 181, 253, 0.86));
        g.setFont(Font.font("Microsoft YaHei UI", 11));
        g.fillText("返回大厅", centerX, centerY + 54);
        g.restore();
    }

    private Color difficultyAccent(Difficulty difficulty) {
        return Color.web("#60a5fa");
    }

    private String difficultyDescription(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "节奏舒缓，适合熟悉手势";
            case NORMAL -> "均衡挑战，推荐首次体验";
            case HARD -> "更快节奏与更高强度";
            case ENDLESS -> "无限挑战，刷新最高纪录";
        };
    }

    private String difficultyTag(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "RELAXED";
            case NORMAL -> "RECOMMENDED";
            case HARD -> "EXPERT";
            case ENDLESS -> "NO LIMIT";
        };
    }

    private static double clampDifficulty(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawTarotEntryScreen(GraphicsContext g, GestureData gesture, DualHandState dualHands,
                                      double w, double h, GameInterface activeGame) {
        boolean questionOpen = tarotQuestionOverlay != null && tarotQuestionOverlay.isVisible();
        if (!questionOpen) {
            updateExitHold(dualHands);
        }
        boolean isFist = !questionOpen && gesture != null
                && gesture.getGesture() == GestureType.FIST && !dualHands.captured();

        if (isFist) {
            compactHoldFrames++;
        } else {
            compactHoldFrames = 0;
        }

        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            showTarotQuestionPrompt(activeGame);
            return;
        }

        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
            LOGGER.info("塔罗牌入口取消，返回大厅");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_LOBBY);
            return;
        }

        double panelW = 420;
        double panelH = 260;
        double x = (w - panelW) / 2.0;
        double y = (h - panelH) / 2.0;
        g.setFill(Color.web("#1a0c24"));
        g.fillRoundRect(x, y, panelW, panelH, 26, 26);
        g.setStroke(Color.web("#8b5bd1"));
        g.setLineWidth(1.5);
        g.strokeRoundRect(x, y, panelW, panelH, 26, 26);

        g.setFill(Color.web("#f0ca79"));
        g.setFont(javafx.scene.text.Font.font(16));
        g.fillText("✦ 紫金秘仪模式", x + 28, y + 34);

        g.setFill(Color.web("#fff3df"));
        g.setFont(javafx.scene.text.Font.font(30));
        g.fillText(activeGame.getIcon() + "  " + activeGame.getName(), x + 28, y + 84);

        g.setFill(Color.web("#d8c2e6"));
        g.setFont(javafx.scene.text.Font.font(16));
        g.fillText("塔罗牌不使用普通难度系统，将直接按你的自定义占读逻辑进入。", x + 28, y + 126);
        g.fillText("当前保留：紫金界面、78张牌、两种牌阵、完整解读。", x + 28, y + 154);

        g.setFill(Color.web("#2e143f"));
        g.fillRoundRect(x + 28, y + 188, 168, 42, 22, 22);
        g.setStroke(Color.web("#f0ca79"));
        g.strokeRoundRect(x + 28, y + 188, 168, 42, 22, 22);
        g.setFill(Color.web("#f6dfae"));
        g.fillText("✊ 握拳开始占读", x + 50, y + 215);

        g.setFill(Color.web("#24112f"));
        g.fillRoundRect(x + 220, y + 188, 148, 42, 22, 22);
        g.setStroke(Color.web("#8b5bd1"));
        g.strokeRoundRect(x + 220, y + 188, 148, 42, 22, 22);
        g.setFill(Color.web("#d7c1e4"));
        g.fillText("👥 双手保持退出", x + 248, y + 215);

        drawExitTarget(g, w, h);
        drawExitProgress(gesture, dualHands);
    }

    private void showTarotQuestionPrompt(GameInterface activeGame) {
        if (!(activeGame instanceof TarotGame) || tarotQuestionOverlay == null || tarotQuestionField == null) {
            return;
        }
        tarotQuestionField.clear();
        tarotQuestionOverlay.setManaged(true);
        tarotQuestionOverlay.setVisible(true);
        Platform.runLater(tarotQuestionField::requestFocus);
    }

    private void hideTarotQuestionPrompt() {
        if (tarotQuestionOverlay != null) {
            tarotQuestionOverlay.setVisible(false);
            tarotQuestionOverlay.setManaged(false);
        }
    }

    @FXML
    private void submitTarotQuestion() {
        GameInterface activeGame = AppStateManager.getInstance().getActiveGame();
        if (!(activeGame instanceof TarotGame tarotGame)) {
            hideTarotQuestionPrompt();
            return;
        }
        String question = tarotQuestionField == null ? "" : tarotQuestionField.getText().trim();
        if (question.isEmpty()) {
            question = "我此刻最需要看清的是什么？";
        }
        tarotGame.setQuestion(question);
        hideTarotQuestionPrompt();
        currentGame = null;
        LOGGER.info("塔罗问题已确认，进入三牌占读");
        AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
    }

    /** Circuit-board settlement screen with a particle-built GAME OVER title. */
    private void drawSettlementScreen(GraphicsContext g, GameInterface game, double w, double h) {
        if (settlementStartedNanos == 0) {
            settlementStartedNanos = System.nanoTime();
        }
        double elapsed = (System.nanoTime() - settlementStartedNanos) / 1_000_000_000.0;

        g.setGlobalAlpha(1.0);
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, w, h);
        drawCssRainBackground(g, w, h, elapsed);

        double titleSize = clampDifficulty(w * 0.095, 72, 130);
        double titleY = h * 0.52;
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, titleSize));
        double hue = (204 + elapsed * 36.0) % 360.0;
        g.setStroke(Color.hsb(hue, 0.88, 1.0, 0.065));
        g.setLineWidth(1.0);
        g.strokeText("GAME OVER", w * 0.5, titleY);

        ensureSettlementParticles(w, h, titleSize, titleY);
        drawSettlementTextParticles(g, elapsed, h, hue);

        String gameName = game == null ? "GAME" : game.getName();
        int score = game == null ? 0 : game.getScore();
        String result = gameName + "   /   FINAL SCORE  " + String.format("%06d", Math.max(0, score));
        g.setGlobalAlpha(clampDifficulty((elapsed - 1.3) / 1.0, 0, 1));
        g.setFont(Font.font("Consolas", FontWeight.NORMAL, clampDifficulty(w * 0.014, 14, 20)));
        g.setFill(Color.hsb(hue, 0.62, 0.92, 0.62));
        g.fillText(result, w * 0.5, titleY + titleSize * 0.60);

        // 无尽模式排行榜
        if (settlementLeaderboard != null && !settlementLeaderboard.isEmpty()) {
            double lbAlpha = clampDifficulty((elapsed - 1.8) / 1.0, 0, 1);
            g.setGlobalAlpha(lbAlpha);
            double lbY = titleY + titleSize * 0.85;
            g.setFill(Color.GOLD.deriveColor(0, 1, 1, 0.85));
            g.setFont(Font.font("Microsoft YaHei UI", 14));
            g.fillText("🏆 排行榜 TOP 5", w * 0.5, lbY);
            g.setFont(Font.font("Consolas", 12));
            for (int i = 0; i < settlementLeaderboard.size(); i++) {
                var e = settlementLeaderboard.get(i);
                g.setFill(i == 0 ? Color.GOLD : Color.rgb(200, 200, 200));
                g.fillText(String.format("%d. %-8s  %6d",
                        i + 1, e.username(), e.score()), w * 0.5, lbY + 18 + i * 16);
            }
        }

        g.setGlobalAlpha(1.0);
        g.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Recreates the browser's final CSS composition: a moving blurred field sampled
     * through the 8px circular mask. Rendering the mask directly is both closer to
     * the original and cheaper than drawing thousands of translucent shapes.
     */
    private void drawCssRainBackground(GraphicsContext g, double w, double h, double elapsed) {
        int imageW = Math.max(1, (int) Math.ceil(w * 0.5));
        int imageH = Math.max(1, (int) Math.ceil(h * 0.5));
        if (cssRainImage == null || imageW != cssRainImageWidth || imageH != cssRainImageHeight) {
            cssRainImageWidth = imageW;
            cssRainImageHeight = imageH;
            cssRainImage = new WritableImage(imageW, imageH);
            cssRainPixels = new int[imageW * imageH];
            cssRainLastFrameNanos = 0;
        }

        long now = System.nanoTime();
        if (cssRainLastFrameNanos == 0 || now - cssRainLastFrameNanos >= 33_000_000L) {
            updateCssRainPixels(elapsed);
            PixelWriter writer = cssRainImage.getPixelWriter();
            writer.setPixels(0, 0, imageW, imageH,
                    PixelFormat.getIntArgbPreInstance(), cssRainPixels, 0, imageW);
            cssRainLastFrameNanos = now;
        }

        g.save();
        g.setGlobalAlpha(clampDifficulty(elapsed / 0.8, 0, 1));
        g.setImageSmoothing(true);
        g.drawImage(cssRainImage, 0, 0, w, h);
        g.restore();
    }

    private void updateCssRainPixels(double elapsed) {
        int[] periods = {235, 252, 150, 253, 204, 134, 179, 299, 215, 281, 158, 210};
        double[] startY = {220, 24, 16, 224, 19, 120, 31, 235, 121, 224, 26, 75};
        double[] speeds = {
                43.8667, 90.72, 36.0, 113.0067, 34.0, 55.3867,
                65.6333, 87.7067, 97.4667, 123.64, 33.7067, 42.0
        };
        Arrays.fill(cssRainPixels, 0xff090909);
        double hue = (204 + elapsed * 36.0) % 360.0;
        Color signal = Color.hsb(hue, 0.98, 1.0);
        double signalR = signal.getRed() * 255.0;
        double signalG = signal.getGreen() * 255.0;
        double signalB = signal.getBlue() * 255.0;

        // One cached pixel becomes a softly sampled 2-3px dot on an 8px grid.
        for (int py = 1; py < cssRainImageHeight - 1; py += 4) {
            double screenY = py * 2.0;
            for (int px = 1; px < cssRainImageWidth - 1; px += 4) {
                double screenX = px * 2.0;
                double energy = 0.0;
                for (int lane = 0; lane < periods.length; lane++) {
                    double period = periods[lane];
                    double shiftY = startY[lane] + elapsed * speeds[lane];

                    // The two 4x100 gradients are 3px apart. blur(1em) expands
                    // their effective horizontal footprint to roughly 18px.
                    double dx = periodicDistance(screenX - (lane * 25.0 + 1.5), 300.0);
                    double dy = periodicDistance(screenY - shiftY, period);
                    double radial = Math.sqrt((dx * dx) / (18.0 * 18.0)
                            + (dy * dy) / (116.0 * 116.0));
                    if (radial < 1.0) {
                        energy += Math.pow(1.0 - radial, 1.30) * 0.44;
                    }

                    // The third 1.5px radial layer sits halfway across/down each tile.
                    double nodeDx = periodicDistance(screenX - (lane * 25.0 + 151.5), 300.0);
                    double nodeDy = periodicDistance(screenY - (shiftY + period * 0.5), period);
                    double nodeDistance = Math.sqrt(nodeDx * nodeDx + nodeDy * nodeDy);
                    if (nodeDistance < 18.0) {
                        energy += (1.0 - nodeDistance / 18.0) * 0.34;
                    }
                }

                // Match the browser mask: fewer visible dots, but stronger bright regions.
                double level = Math.min(0.90, (1.0 - Math.exp(-energy * 2.50)) * 0.98);
                if (level < 0.10) continue;
                int red = (int) clampDifficulty(9 + signalR * level, 0, 255);
                int green = (int) clampDifficulty(9 + signalG * level, 0, 255);
                int blue = (int) clampDifficulty(9 + signalB * level, 0, 255);
                int color = 0xff000000 | (red << 16) | (green << 8) | blue;
                int row = py * cssRainImageWidth;
                cssRainPixels[row + px] = color;
            }
        }
    }

    private static double periodicDistance(double value, double period) {
        double wrapped = value % period;
        if (wrapped < 0) wrapped += period;
        return Math.min(wrapped, period - wrapped);
    }

    /** Generates a text mask once per resolution, then turns its sampled pixels into particles. */
    private void ensureSettlementParticles(double w, double h, double fontSize, double baselineY) {
        if (!settlementParticles.isEmpty()
                && Math.abs(settlementParticleWidth - w) < 1
                && Math.abs(settlementParticleHeight - h) < 1) {
            return;
        }

        settlementParticles.clear();
        settlementParticleWidth = w;
        settlementParticleHeight = h;
        int imageW = Math.max(1, (int) Math.ceil(w));
        int imageH = Math.max(1, (int) Math.ceil(h));
        Canvas maskCanvas = new Canvas(imageW, imageH);
        GraphicsContext mask = maskCanvas.getGraphicsContext2D();
        mask.setTextAlign(TextAlignment.CENTER);
        mask.setFont(Font.font("Segoe UI", FontWeight.BOLD, fontSize));
        mask.setFill(Color.WHITE);
        mask.fillText("GAME OVER", w * 0.5, baselineY);

        WritableImage image = new WritableImage(imageW, imageH);
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        maskCanvas.snapshot(snapshotParameters, image);
        PixelReader pixels = image.getPixelReader();
        Random random = new Random(0x47414D454F564552L ^ imageW * 31L ^ imageH);
        int step = (int) clampDifficulty(w / 230.0, 5, 8);
        int minY = Math.max(0, (int) (baselineY - fontSize * 1.05));
        int maxY = Math.min(imageH - 1, (int) (baselineY + fontSize * 0.10));
        for (int y = minY; y <= maxY; y += step) {
            double row = (y - minY) / Math.max(1.0, maxY - minY);
            for (int x = step; x < imageW - step; x += step) {
                if (((pixels.getArgb(x, y) >>> 24) & 0xff) < 72) continue;
                SettlementParticle particle = new SettlementParticle();
                particle.targetX = x + (random.nextDouble() - 0.5) * 1.8;
                particle.targetY = y + (random.nextDouble() - 0.5) * 1.8;
                boolean fallsFromTop = random.nextDouble() < 0.32;
                if (fallsFromTop) {
                    particle.startX = particle.targetX + (random.nextDouble() - 0.5) * w * 0.18;
                    particle.startY = -20.0 - random.nextDouble() * h * 0.55;
                    particle.delay = row * 0.72 + random.nextDouble() * 0.16;
                    particle.duration = 1.05 + row * 1.10 + random.nextDouble() * 0.62;
                } else {
                    particle.startX = particle.targetX + (random.nextDouble() - 0.5) * 22.0;
                    particle.startY = particle.targetY - 18.0 - random.nextDouble() * 34.0;
                    particle.delay = row * 0.30 + random.nextDouble() * 0.24;
                    particle.duration = 0.48 + random.nextDouble() * 0.42;
                }
                particle.size = 1.15 + random.nextDouble() * 1.65;
                particle.phase = random.nextDouble() * Math.PI * 2.0;
                particle.hueOffset = (random.nextDouble() - 0.5) * 22.0;
                settlementParticles.add(particle);
            }
        }
    }

    private void drawSettlementTextParticles(GraphicsContext g, double elapsed, double h, double hue) {
        double scanTop = h * 0.34;
        double scanRange = h * 0.30;
        double scanY = scanTop + ((elapsed * 0.20) % 1.0) * scanRange;
        g.save();
        for (SettlementParticle particle : settlementParticles) {
            double progress = (elapsed - particle.delay) / particle.duration;
            if (progress <= 0) continue;

            double x;
            double y;
            double alpha;
            double glow;
            if (progress < 1.0) {
                double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
                x = particle.startX + (particle.targetX - particle.startX) * eased
                        + Math.sin(elapsed * 6.0 + particle.phase) * (1.0 - eased) * 10.0;
                y = particle.startY + (particle.targetY - particle.startY) * eased;
                alpha = 0.22 + eased * 0.72;
                glow = 1.0 - eased;
            } else {
                double scan = Math.exp(-Math.abs(particle.targetY - scanY) / 18.0);
                x = particle.targetX + Math.sin(elapsed * 1.7 + particle.phase) * 0.42;
                y = particle.targetY + Math.cos(elapsed * 1.3 + particle.phase) * 0.34;
                alpha = 0.46 + scan * 0.54;
                glow = scan;
            }

            double particleHue = (hue + particle.hueOffset + 360.0) % 360.0;
            if (glow > 0.12) {
                double halo = particle.size * (2.8 + glow * 2.2);
                g.setFill(Color.hsb(particleHue, 0.92, 1.0, 0.055 + glow * 0.10));
                g.fillOval(x - halo * 0.5, y - halo * 0.5, halo, halo);
            }
            g.setFill(Color.hsb(particleHue, 0.76, 1.0, alpha));
            g.fillOval(x - particle.size * 0.5, y - particle.size * 0.5,
                    particle.size, particle.size);
        }
        g.restore();
    }

    private void clearCanvas() {
        if (gc != null && gameCanvas != null) {
            gc.setFill(Color.web("#120718"));
            gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        }
    }

    private void updateExitHold(DualHandState dualHands) {
        if (dualHands.active()) {
            exitHoldFrames = Math.min(HOLD_FRAMES, exitHoldFrames + 1);
            exitDropoutFrames = 0;
        } else if (dualHands.captured() && exitHoldFrames > 0 && exitDropoutFrames < 6) {
            exitDropoutFrames++;
        } else {
            exitHoldFrames = 0;
            exitDropoutFrames = 0;
        }
    }

    private void drawExitTarget(GraphicsContext graphics, double width, double height) {
        graphics.setFill(Color.color(0.87, 1.0, 0.6, 0.10));
        graphics.fillRoundRect(16, 14, 190, 34, 16, 16);
        graphics.setFill(Color.web("#deff9a"));
        graphics.setFont(javafx.scene.text.Font.font(14));
        graphics.fillText("双手入镜保持返回", 30, 36);
    }

    private void drawExitProgress(GestureData gesture, DualHandState dualHands) {
        if (exitHoldFrames <= 0 || gesture == null || !gesture.isHandDetected()) {
            return;
        }
        double cx = (gesture.getHandX() + dualHands.secondHandX()) * 0.5 * gameCanvas.getWidth();
        double cy = (gesture.getHandY() + dualHands.secondHandY()) * 0.5 * gameCanvas.getHeight();
        double progress = (double) exitHoldFrames / HOLD_FRAMES;

        gc.setFill(Color.color(0.87, 1.0, 0.6, 0.12));
        gc.fillOval(cx - 24, cy - 24, 48, 48);
        gc.setStroke(Color.web("#deff9a"));
        gc.setLineWidth(3.0);
        gc.strokeArc(cx - 21, cy - 21, 42, 42,
                90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
        gc.setFill(Color.web("#deff9a"));
        gc.fillOval(cx - 3, cy - 3, 6, 6);
    }

    private static final class SettlementParticle {
        double targetX;
        double targetY;
        double startX;
        double startY;
        double delay;
        double duration;
        double size;
        double phase;
        double hueOffset;
    }
}
