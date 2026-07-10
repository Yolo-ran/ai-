package com.gesturegame.engine;

import com.gesturegame.common.GameInterface;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 应用状态机代理，统一负责场景注册与界面切换。
 *
 * <p>状态流：{@code LOGIN} → {@code LOBBY} → {@code GAME} → {@code LOBBY}。
 * 进入 GAME 状态前需通过 {@link #setActiveGame(GameInterface)} 注入当前游戏实例。
 */
public class AppStateManager {

    public static final String STATE_LOGIN = "LOGIN";
    public static final String STATE_LOBBY = "LOBBY";
    public static final String STATE_GAME = "GAME";

    private static final Logger LOGGER = Logger.getLogger(AppStateManager.class.getName());
    private static AppStateManager instance;

    private final Map<String, Scene> scenes = new HashMap<>();
    private Stage primaryStage;
    private String currentState = STATE_LOGIN;
    private volatile GameInterface activeGame;

    private AppStateManager() {
    }

    public static AppStateManager getInstance() {
        if (instance == null) {
            instance = new AppStateManager();
        }
        return instance;
    }

    public void init(Stage stage) {
        this.primaryStage = stage;
    }

    public void registerScene(String name, Scene scene) {
        scenes.put(name, scene);
    }

    public void switchState(String newState) {
        currentState = newState;
        Platform.runLater(() -> {
            Scene targetScene = scenes.get(newState);
            if (primaryStage != null && targetScene != null) {
                primaryStage.setScene(targetScene);
                LOGGER.info(() -> "[Agent 状态机] 切换至状态: " + newState);
            }
        });
    }

    public String getCurrentState() {
        return currentState;
    }

    /**
     * 注入当前要运行的游戏实例，供 GAME 场景的游戏循环读取。
     */
    public void setActiveGame(GameInterface game) {
        this.activeGame = game;
        LOGGER.info(() -> "[Agent 状态机] 设置当前游戏: " + (game == null ? "null" : game.getName()));
    }

    public GameInterface getActiveGame() {
        return activeGame;
    }
}
