package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.util.logging.Logger;

/**
 * 现代架构版水果忍者（混合渲染）
 * 
 * <p>基于 JavaFX 外壳 + 本地嵌入 WebView 的黄金技术栈重构。
 * Java 端负责生命周期、手势坐标下发以及成绩记录，
 * WebView + PixiJS 负责抛物线物理、丝滑刀光与果汁飞溅等 GPU 加速特效。</p>
 */
public class FruitNinja implements GameInterface {

    private static final Logger LOGGER = Logger.getLogger(FruitNinja.class.getName());

    private WebView webView;
    private WebEngine webEngine;
    private JSObject jsWindow;
    private boolean isWebViewAdded = false;
    private Pane parentPane;

    private int score = 0;
    private boolean over = false;
    private Difficulty difficulty = Difficulty.NORMAL;
    
    private double lastX = -1;
    private double lastY = -1;
    private boolean lastDetected = false;

    @Override
    public String getName() {
        return "切水果";
    }

    @Override
    public String getDescription() {
        return "丝滑特效版：本地 WebView + PixiJS 硬件加速";
    }

    @Override
    public String getIcon() {
        return "🔪";
    }

    private String initErrorMsg = null;

    @Override
    public void init(int width, int height) {
        score = 0;
        over = false;
        initErrorMsg = null;
        jsWindow = null;
        lastX = -1;
        lastY = -1;
        lastDetected = false;
        
        // JavaFX 的 UI 操作本身就在 JavaFX Application Thread 中进行
        if (webView == null) {
            try {
                webView = new WebView();
                webEngine = webView.getEngine();
                
                // 加载本地打包的前端渲染逻辑
                String url = getClass().getResource("/web/fruitninja/index.html").toExternalForm();
                webEngine.load(url);

                // 注册 Java 与 JS 的双向通信桥梁
                webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        jsWindow = (JSObject) webEngine.executeScript("window");
                        jsWindow.setMember("javaBackend", new JavaBridge());
                        LOGGER.info("Fruit Ninja WebView 加载成功，JSBridge 注入完毕");

                        // 传递难度设置给前端
                        String mode;
                        int livesCount = 3;
                        switch (difficulty) {
                            case EASY: mode = "easy"; break;
                            case HARD: mode = "hard"; break;
                            case ENDLESS: mode = "endless"; break;
                            default: mode = "normal"; break;
                        }
                        try {
                            jsWindow.call("resetGame", mode, livesCount);
                        } catch (Exception e) {
                            LOGGER.warning("重置前端游戏状态失败: " + e.getMessage());
                        }
                    }
                });
            } catch (Throwable t) {
                initErrorMsg = t.getMessage();
                if (initErrorMsg == null || initErrorMsg.isEmpty()) {
                    initErrorMsg = t.getClass().getName();
                }
                LOGGER.severe("WebView 初始化失败: " + t);
            }
        } else {
            // 如果 WebView 已经被复用（即没有被彻底销毁），我们需要在重新进入时调用前端的复位逻辑
            if (jsWindow != null) {
                try {
                    jsWindow.call("resetGame");
                } catch (Exception e) {
                    LOGGER.warning("复用 WebView 时重置前端状态失败: " + e.getMessage());
                }
            }
        }
        if (webView != null) {
            webView.setPrefSize(width, height);
        }
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;
        
        // 当页面加载完成时，将本地识别的手势坐标高频下发给前端 PixiJS/Canvas
        if (jsWindow != null) {
            boolean detected = gesture != null && gesture.isHandDetected();
            double x = detected ? gesture.getIndexTipX() : 0.5;
            double y = detected ? gesture.getIndexTipY() : 0.5;
            
            try {
                jsWindow.call("updateHandPosition", x, y, detected);
            } catch (Exception e) {
                // Ignore transient JS errors during page unload
            }
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        if (initErrorMsg != null) {
            gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
            gc.setFill(Color.RED);
            gc.setFont(Font.font(24));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("WebView 引擎初始化失败！\n\n原因: " + initErrorMsg + 
                        "\n\n在 Trae IDE 的终端中运行可能会触发 Sandbox 安全拦截 (jfxwebkit.dll)\n" +
                        "请在你的本地系统命令行（如 CMD/PowerShell）中执行 mvn javafx:run\n" +
                        "或者在 Trae 的设置中配置 Sandbox 规则放行 .openjfx 目录。\n\n" +
                        "（挥手或握拳返回大厅）", 
                        gc.getCanvas().getWidth() / 2, gc.getCanvas().getHeight() / 2 - 50);
            return;
        }

        // 由于 GameRenderer 使用 Canvas，我们需要将 WebView 作为兄弟节点（覆盖在 Canvas 之上）添加到父容器 Pane 中
        if (!isWebViewAdded && webView != null) {
            parentPane = (Pane) gc.getCanvas().getParent();
            if (parentPane != null) {
                parentPane.getChildren().add(webView);
                if (parentPane instanceof AnchorPane) {
                    AnchorPane.setTopAnchor(webView, 0.0);
                    AnchorPane.setBottomAnchor(webView, 0.0);
                    AnchorPane.setLeftAnchor(webView, 0.0);
                    AnchorPane.setRightAnchor(webView, 0.0);
                }
                isWebViewAdded = true;
            }
        }
        
        // 游戏界面的实际渲染由 WebView 内部的 WebGL/Canvas 完成
        // 这里可以清空底层的 JavaFX Canvas 避免透出杂乱背景
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
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
        removeWebView();
        webView = null;
        webEngine = null;
        score = 0;
        over = false;
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    private void removeWebView() {
        if (parentPane != null && webView != null && isWebViewAdded) {
            parentPane.getChildren().remove(webView);
            isWebViewAdded = false;
        }
    }

    /**
     * JS 通信桥梁，允许 HTML/JS 直接调用此类的公有方法。
     */
    public class JavaBridge {
        public void log(String msg) {
            LOGGER.info("[JS Log] " + msg);
        }

        public void updateScore(int currentScore) {
            score = currentScore;
        }

        public void saveScore(int finalScore) {
            LOGGER.info("收到前端游戏结束信号，得分: " + finalScore);
            score = finalScore;
            over = true;
            
            // 游戏结束后，在 JavaFX 主线程中安全地移除 WebView
            Platform.runLater(FruitNinja.this::removeWebView);
        }
    }
}
