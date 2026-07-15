package com.gesturegame.ui;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureCommand;
import com.gesturegame.network.GestureStreamServer.DualHandState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

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
    private final double[] difficultyLift = new double[Difficulty.values().length];
    private long lastDifficultyFrameNanos;
    private double difficultyFade;
    private long settlementStartedNanos;
    private static final int HOLD_FRAMES = 72; // 1.2秒@60fps
    private static final int DROPOUT_MAX = 3;  // 最多容忍3帧闪断

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
        setGameHudVisible(true);
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
        game.update(dualHands.captured() ? new GestureData() : gesture);
        game.render(gc);
        drawExitTarget(gc, gameCanvas.getWidth(), gameCanvas.getHeight());
        drawExitProgress(gesture, dualHands);

        boolean tarotMode = "塔罗牌".equals(game.getName());
        boolean fruitNinjaMode = "切水果".equals(game.getName());
        if (gameNameLabel != null) {
            gameNameLabel.setText(tarotMode || fruitNinjaMode ? "" : game.getIcon() + "  " + game.getName());
        }
        if (scoreLabel != null) {
            scoreLabel.setText(tarotMode || fruitNinjaMode ? "" : "分数: " + game.getScore());
        }
        if (statusLabel != null && tarotMode) {
            statusLabel.setText("");
        }

        if (game.isOver() && !gameOverHandled) {
            gameOverHandled = true;
            settling = true;
            settlementStartedNanos = System.nanoTime();
            if (statusLabel != null) {
                statusLabel.setText("");
            }
            LOGGER.info(() -> "[GameRenderer] 游戏结束: " + game.getName() + " 得分=" + game.getScore());
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
        exitHoldFrames = 0;
        compactHoldFrames = 0;
        openHoldFrames = 0;
        settlementStartedNanos = 0;
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
        exitHoldFrames = 0;
        compactHoldFrames = 0;
        openHoldFrames = 0;
        settlementStartedNanos = 0;
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

        // PEACE确认(1.2s) → 进入游戏
        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            GameInterface game = AppStateManager.getInstance().getActiveGame();
            if (game != null) {
                game.setDifficulty(selectedDifficulty);
                LOGGER.info("难度选择确认: " + selectedDifficulty.getLabel() + " → " + game.getName());
            }
            currentGame = null;
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return;
        }

        // 双手稳定保持 → 返回大厅
        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
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
                // PEACE 按住中：外圈光晕 + 进度环
                g.setFill(Color.color(0.94, 0.79, 0.47, 0.12));
                g.fillOval(cx - 22, cy - 22, 44, 44);
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(2.0);
                g.strokeOval(cx - 16, cy - 16, 32, 32);
                // 进度弧
                double progress = (double) compactHoldFrames / HOLD_FRAMES;
                g.setStroke(Color.web("#f0ca79"));
                g.setLineWidth(3);
                g.strokeArc(cx - 20, cy - 20, 40, 40,
                        90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
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
        }
        difficultyFade += (1.0 - difficultyFade) * 0.12;

        java.util.List<Difficulty> supported = new java.util.ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            if (activeGame == null || activeGame.supportsDifficulty(difficulty)) {
                supported.add(difficulty);
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
        if (gesture != null && gesture.isHandDetected()) {
            double hx = gesture.getHandX() * w;
            double hy = gesture.getHandY() * h;
            double skewPadding = cardW * 0.08;
            if (hx >= cardX[selectedIndex] && hx <= cardX[selectedIndex] + cardW
                    && hy >= cardY[selectedIndex] - skewPadding
                    && hy <= cardY[selectedIndex] + cardH + skewPadding) {
                hoveredIndex = selectedIndex;
            }
            for (int i = n - 1; hoveredIndex < 0 && i >= 0; i--) {
                if (hx >= cardX[i] && hx <= cardX[i] + cardW
                        && hy >= cardY[i] - skewPadding
                        && hy <= cardY[i] + cardH + skewPadding) {
                    hoveredIndex = i;
                    break;
                }
            }
            if (hoveredIndex >= 0) {
                selectedDifficulty = diffs[hoveredIndex];
                selectedIndex = hoveredIndex;
            }

            boolean isFist = !dualHands.captured() && gesture.getGesture() == GestureType.FIST;
            if (isFist && hoveredIndex >= 0) {
                compactHoldFrames++;
                diffDropoutFrames = 0;
            } else if (compactHoldFrames > 0 && diffDropoutFrames < DROPOUT_MAX) {
                diffDropoutFrames++;
            } else {
                compactHoldFrames = 0;
                diffDropoutFrames = 0;
            }
        } else {
            compactHoldFrames = 0;
            diffDropoutFrames = 0;
        }

        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            if (activeGame != null) {
                activeGame.setDifficulty(selectedDifficulty);
                LOGGER.info("难度选择确认: " + selectedDifficulty.getLabel() + " → " + activeGame.getName());
            }
            currentGame = null;
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
            return true;
        }

        if (exitHoldFrames >= HOLD_FRAMES) {
            exitHoldFrames = 0;
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
                    ? compactHoldFrames / (double) HOLD_FRAMES : 0;
            drawDifficultyGlassCard(g, diffs[i], activeGame, cardX[i], cardY[i],
                    cardW, cardH, i == selectedIndex, progress);
        }
        g.setGlobalAlpha(1.0);

        return true;
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
                                         double holdProgress) {
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

        String label = activeGame == null
                ? difficulty.getLabel() : activeGame.getDifficultyLabel(difficulty);
        g.setTextAlign(TextAlignment.LEFT);
        g.setFill(selected ? Color.web("#3B82F6") : Color.rgb(161, 161, 170, 0.68));
        g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        g.fillText(label, 54, 35);

        g.setFill(Color.rgb(244, 244, 245, selected ? 0.91 : 0.52));
        g.setFont(Font.font("Microsoft YaHei", 16));
        g.fillText(difficultyDescription(difficulty), 16, cardH - 48, cardW - 32);
        g.setFill(Color.rgb(161, 161, 170, selected ? 0.72 : 0.40));
        g.setFont(Font.font("Consolas", 11));
        g.fillText(difficultyTag(difficulty), 16, cardH - 19);

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

    private void drawDifficultyCursor(GraphicsContext g, double x, double y, double progress) {
        g.setFill(Color.rgb(147, 197, 253, 0.13));
        g.fillOval(x - 18, y - 18, 36, 36);
        g.setStroke(Color.web("#bfdbfe"));
        g.setLineWidth(1.5);
        g.strokeOval(x - 9, y - 9, 18, 18);
        g.setFill(Color.WHITE);
        g.fillOval(x - 2.5, y - 2.5, 5, 5);
        if (progress > 0) {
            g.setLineWidth(3);
            g.strokeArc(x - 23, y - 23, 46, 46, 90,
                    -360 * clampDifficulty(progress, 0, 1), javafx.scene.shape.ArcType.OPEN);
        }
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
        updateExitHold(dualHands);
        boolean isFist = gesture != null && gesture.getGesture() == GestureType.FIST && !dualHands.captured();

        if (isFist) {
            compactHoldFrames++;
        } else {
            compactHoldFrames = 0;
        }

        if (compactHoldFrames >= HOLD_FRAMES) {
            compactHoldFrames = 0;
            currentGame = null;
            LOGGER.info("塔罗牌跳过难度选择，直接进入自定义占读模式");
            AppStateManager.getInstance().switchState(AppStateManager.STATE_GAME);
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

    /**
     * Unified end screen based on the supplied Background Paths reference.
     * It intentionally contains no visual button or gesture instruction.
     */
    private void drawSettlementScreen(GraphicsContext g, GameInterface game, double w, double h) {
        if (settlementStartedNanos == 0) {
            settlementStartedNanos = System.nanoTime();
        }
        double elapsed = (System.nanoTime() - settlementStartedNanos) / 1_000_000_000.0;

        g.setGlobalAlpha(1.0);
        g.setFill(Color.web("#0A0A0A"));
        g.fillRect(0, 0, w, h);

        double backgroundFade = clampDifficulty(elapsed / 1.2, 0, 1);
        g.setGlobalAlpha(backgroundFade);
        drawSettlementPaths(g, w, h, elapsed, 1);
        drawSettlementPaths(g, w, h, elapsed, -1);
        g.setGlobalAlpha(1.0);

        double reveal = clampDifficulty((elapsed - 0.08) / 1.15, 0, 1);
        double eased = 1.0 - Math.pow(1.0 - reveal, 3);
        double titleY = h * 0.50 + (1.0 - eased) * 70.0;
        double titleSize = clampDifficulty(w * 0.082, 64, 118);

        g.setGlobalAlpha(reveal);
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, titleSize));
        g.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.98)),
                new Stop(1, Color.rgb(229, 229, 229, 0.82))));
        g.fillText("GAME OVER", w * 0.5, titleY);

        String gameName = game == null ? "GAME" : game.getName();
        int score = game == null ? 0 : game.getScore();
        String result = gameName + "   /   FINAL SCORE  " + String.format("%06d", Math.max(0, score));
        g.setGlobalAlpha(clampDifficulty((elapsed - 0.52) / 0.9, 0, 1));
        g.setFont(Font.font("Consolas", FontWeight.NORMAL, clampDifficulty(w * 0.014, 14, 20)));
        g.setFill(Color.rgb(161, 161, 170, 0.78));
        g.fillText(result, w * 0.5, titleY + titleSize * 0.62);

        g.setGlobalAlpha(1.0);
        g.setTextAlign(TextAlignment.LEFT);
    }

    /** Recreates both 36-path SVG layers from the supplied Framer Motion component. */
    private void drawSettlementPaths(GraphicsContext g, double w, double h,
                                     double elapsed, int position) {
        double scale = w / 696.0;
        double offsetY = (h - 316.0 * scale) * 0.5;

        g.save();
        for (int i = 0; i < 36; i++) {
            double drift = i * 5.0 * position;
            double x0 = -(380.0 - drift);
            double y0 = -(189.0 + i * 6.0);
            double x2 = -(312.0 - drift);
            double y2 = 216.0 - i * 6.0;
            double x3 = 152.0 - drift;
            double y3 = 343.0 - i * 6.0;
            double x4 = 616.0 - drift;
            double y4 = 470.0 - i * 6.0;
            double x5 = 684.0 - drift;
            double y5 = 875.0 - i * 6.0;

            double duration = 20.0 + ((i * 17 + (position > 0 ? 7 : 19)) % 101) / 10.0;
            double phase = (elapsed / duration + i * 0.037) % 1.0;
            double pulse = 0.30 + 0.30 * (0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
            double opacity = Math.min(0.48, (0.10 + i * 0.03) * pulse);

            g.beginPath();
            g.moveTo(x0 * scale, offsetY + y0 * scale);
            g.bezierCurveTo(x0 * scale, offsetY + y0 * scale,
                    x2 * scale, offsetY + y2 * scale,
                    x3 * scale, offsetY + y3 * scale);
            g.bezierCurveTo(x4 * scale, offsetY + y4 * scale,
                    x5 * scale, offsetY + y5 * scale,
                    x5 * scale, offsetY + y5 * scale);
            g.setStroke(Color.rgb(255, 255, 255, opacity));
            g.setLineWidth(Math.max(0.65, (0.5 + i * 0.03) * scale * 0.72));
            g.setLineDashes((145.0 + i * 4.2) * scale, (82.0 + i * 1.5) * scale);
            g.setLineDashOffset((position > 0 ? -1 : 1)
                    * phase * (240.0 + i * 4.0) * scale);
            g.stroke();
        }
        g.setLineDashes();
        g.setLineDashOffset(0);
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
        } else if (dualHands.active() && exitHoldFrames > 0 && exitDropoutFrames < 6) {
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
}
