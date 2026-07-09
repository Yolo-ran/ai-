package com.gesturegame.engine;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 应用状态机代理，统一负责场景注册与界面切换。
 */
public class AppStateManager {

    private static final Logger LOGGER = Logger.getLogger(AppStateManager.class.getName());
    private static AppStateManager instance;

    private final Map<String, Scene> scenes = new HashMap<>();
    private Stage primaryStage;
    private String currentState = "LOGIN";

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
}
