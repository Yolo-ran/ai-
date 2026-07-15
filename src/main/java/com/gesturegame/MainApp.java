package com.gesturegame;

import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.network.GestureStreamServer;
import com.gesturegame.network.GestureStreamServer.DualHandState;
import com.gesturegame.ui.GameRenderer;
import com.gesturegame.ui.AuthController;
import com.gesturegame.ui.LobbyController;
import com.gesturegame.ui.LoginController;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 应用启动入口，负责初始化场景、状态机和手势通信服务。
 *
 * <p>混合架构：Java 仅负责游戏大厅 + UI 渲染，手势数据由 Python 视觉端通过
 * WebSocket（{@link GestureStreamServer}）推送，本进程不再持有本地摄像头链路。
 */
public class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static final int SERVER_PORT = 8765;
    private static final long FRAME_NS = 16_666_667L; // 1/60 秒，锁 60fps

    private GestureStreamServer gestureStreamServer;
    private GameRenderer gameRenderer;
    private LobbyController lobbyController;
    private Process pythonProcess;
    private long lastFrameNs;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader authLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Auth.fxml")));
        Parent authRoot = authLoader.load();
        AuthController authController = authLoader.getController();

        FXMLLoader loginLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Login.fxml")));
        Parent loginRoot = loginLoader.load();
        LoginController loginController = loginLoader.getController();

        FXMLLoader lobbyLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Lobby.fxml")));
        Parent lobbyRoot = lobbyLoader.load();
        lobbyController = lobbyLoader.getController();

        FXMLLoader gameLoader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/Game.fxml")));
        Parent gameRoot = gameLoader.load();
        gameRenderer = gameLoader.getController();

        Scene authScene = new Scene(authRoot, 1280, 720);
        Scene loginScene = new Scene(loginRoot, 1280, 720);
        Scene lobbyScene = new Scene(lobbyRoot, 1280, 720);
        Scene gameScene = new Scene(gameRoot, 1280, 720);

        AppStateManager appStateManager = AppStateManager.getInstance();
        appStateManager.init(primaryStage);
        appStateManager.registerScene(AppStateManager.STATE_AUTH, authScene);
        appStateManager.registerScene(AppStateManager.STATE_LOGIN, loginScene);
        appStateManager.registerScene(AppStateManager.STATE_LOBBY, lobbyScene);
        appStateManager.registerScene(AppStateManager.STATE_GAME, gameScene);
        appStateManager.registerScene(AppStateManager.STATE_DIFFICULTY, gameScene);

        authController.bindStateManager(appStateManager);
        loginController.bindStateManager(appStateManager);
        lobbyController.bindStateManager(appStateManager);
        gameRenderer.bindStateManager(appStateManager);

        primaryStage.setTitle("AI 手势交互游戏大厅");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.setFullScreenExitHint("");
        primaryStage.setFullScreen(true);
        appStateManager.switchState(AppStateManager.STATE_AUTH);
        primaryStage.show();

        startGameLoop();

        LOGGER.info("当前以混合模式启动：停用 Java 本地摄像头链路，等待 Python 端通过 WebSocket 推送手势数据");
        gestureStreamServer = new GestureStreamServer(SERVER_PORT, loginController, lobbyController, gameRenderer);
        gestureStreamServer.start();
        LOGGER.info(() -> "手势图像串流服务已启动，端口: " + SERVER_PORT);

        // 一键启动器会提前预热 Python、模型和摄像头；此时 Java 只负责接收数据。
        // 从 IDE 单独运行 MainApp 时仍立即自动启动视觉引擎，不再固定等待 1.5 秒。
        boolean externalGestureEngine = "1".equals(System.getenv("GESTURE_ENGINE_EXTERNAL"));
        if (externalGestureEngine) {
            LOGGER.info("视觉引擎已由一键启动器提前启动");
        } else {
            Thread pythonLauncher = new Thread(this::launchPythonEngine, "python-launcher");
            pythonLauncher.setDaemon(true);
            pythonLauncher.start();
        }
    }

    /**
     * 启动 60fps 渲染循环：LOBBY 态驱动大厅粒子宇宙，GAME/GAME_OVER 态驱动游戏渲染。
     * GAME_OVER 复用 GAME 场景（不单独注册），仅由 {@link GameRenderer} 冻结画面与显示结算。
     */
    private void startGameLoop() {
        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNs > 0 && now - lastFrameNs < FRAME_NS) {
                    return; // 跳过本帧 → 无论 60/144/165Hz 屏都锁在 60fps
                }
                lastFrameNs = now;

                String state = AppStateManager.getInstance().getCurrentState();
                GestureData gesture = gestureStreamServer.getLatestGesture();
                DualHandState dualHands = gestureStreamServer.getLatestDualHandState();
                if (AppStateManager.STATE_LOBBY.equals(state)) {
                    lobbyController.tick(gesture, dualHands);
                } else if (AppStateManager.STATE_DIFFICULTY.equals(state)) {
                    gameRenderer.tickDifficultySelect(gesture, dualHands);
                } else if (AppStateManager.STATE_GAME.equals(state)
                        || AppStateManager.STATE_GAME_OVER.equals(state)) {
                    GameInterface game = AppStateManager.getInstance().getActiveGame();
                    gameRenderer.tick(gesture, game, dualHands);
                }
            }
        };
        gameLoop.start();
        LOGGER.info(() -> "渲染循环已启动 (AnimationTimer)");
    }

    @Override
    public void stop() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroyForcibly();
            LOGGER.info("Python 手势引擎已关闭");
        }
        if (gestureStreamServer != null) {
            try {
                gestureStreamServer.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "关闭手势图像串流服务失败", e);
            }
        }
    }

    /**
     * 自动拉起 Python 手势引擎子进程。
     *
     * <p>按优先级查找可执行入口：
     * <ol>
     *   <li>{@code python/dist/gesture_server/gesture_server.exe} — PyInstaller 打包</li>
     *   <li>{@code .venv/Scripts/pythonw.exe} — 项目虚拟环境</li>
     *   <li>{@code %LOCALAPPDATA%\Programs\Python\Python31x\pythonw.exe} — 3.12/3.11/3.13 标准安装</li>
     *   <li>系统 {@code pythonw} / {@code python} — PATH 兜底</li>
     * </ol>
     */
    private void launchPythonEngine() {
        try {
            String projectDir = System.getProperty("user.dir");
            File pythonDir = new File(projectDir, "python");
            File packagedExe = new File(pythonDir, "dist/gesture_server/gesture_server.exe");
            File venvPythonw = new File(projectDir, ".venv/Scripts/pythonw.exe");

            String[] cmd;
            if (packagedExe.exists()) {
                cmd = new String[]{packagedExe.getAbsolutePath()};
                LOGGER.info("使用打包 exe 启动手势引擎");
            } else if (venvPythonw.exists()) {
                cmd = new String[]{venvPythonw.getAbsolutePath(), "gesture_server.py"};
                LOGGER.info("使用 venv pythonw 启动手势引擎");
            } else {
                cmd = findSystemPythonw(pythonDir);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(pythonDir);
            pb.inheritIO();
            pythonProcess = pb.start();
            LOGGER.info("Python 手势引擎子进程已启动");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "无法自动启动 Python 手势引擎，请手动运行 启动.bat", e);
        }
    }

    /** 按优先级查找系统 Python（覆盖队友的不同安装版本）。 */
    private static String[] findSystemPythonw(File pythonDir) {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            for (String version : new String[]{"312", "311", "313", "310"}) {
                File cand = new File(localAppData,
                        "Programs/Python/Python" + version + "/pythonw.exe");
                if (cand.exists()) {
                    LOGGER.info("使用 AppData pythonw " + version + " 启动手势引擎");
                    return new String[]{cand.getAbsolutePath(), "gesture_server.py"};
                }
            }
        }
        // 最后兜底：pythonw 不在 PATH 时用 python
        try {
            ProcessBuilder test = new ProcessBuilder("pythonw", "--version");
            test.start().destroy();
            LOGGER.info("使用系统 pythonw 启动手势引擎");
            return new String[]{"pythonw", "gesture_server.py"};
        } catch (IOException e) {
            LOGGER.info("使用系统 python 启动手势引擎");
            return new String[]{"python", "gesture_server.py"};
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
