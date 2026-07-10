package com.gesturegame.network;

import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import com.gesturegame.engine.AppStateManager;
import com.gesturegame.ui.GameRenderer;
import com.gesturegame.ui.LobbyController;
import com.gesturegame.ui.LoginController;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 高帧率串流服务器，负责同时分发手势命令和摄像头图像。
 *
 * <p>对 GAME 状态：仅缓存最新 {@link GestureData} 供游戏循环通过
 * {@link #getLatestGesture()} 读取，摄像头画面不再分发（游戏全屏渲染）。
 * 仍会派发 {@code BACK} 命令到 {@link GameRenderer} 以便玩家张手返回大厅。
 */
public class GestureStreamServer extends WebSocketServer {

    private static final Logger LOGGER = Logger.getLogger(GestureStreamServer.class.getName());
    private static final double SWIPE_VELOCITY_THRESHOLD = 0.035;
    private static final long SWIPE_COOLDOWN_MS = 260L;
    private static final long HOLD_CONFIRM_MS = 1000L;
    private static final long COMMAND_COOLDOWN_MS = 450L;

    private final LoginController loginController;
    private final LobbyController lobbyController;
    private final GameRenderer gameRenderer;
    private volatile GestureData latestGestureData = new GestureData();
    private GestureType lastHoldGesture = GestureType.NONE;
    private long holdStartTime;
    private long lastSwipeCommandTime;
    private long lastStaticCommandTime;

    public GestureStreamServer(int port, LoginController loginController,
                               LobbyController lobbyController, GameRenderer gameRenderer) {
        super(new InetSocketAddress(port));
        this.loginController = loginController;
        this.lobbyController = lobbyController;
        this.gameRenderer = gameRenderer;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOGGER.info("[Socket] Python 视觉串流端已成功建立连接");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject json = new JSONObject(message);
            String base64Image = json.optString("image_data", "");
            String state = AppStateManager.getInstance().getCurrentState();

            dispatchCameraFrame(state, base64Image);

            if (containsGestureData(json)) {
                GestureData gestureData = GestureData.fromJson(message);
                this.latestGestureData = gestureData;
                GestureCommand command = mapGestureDataToCommand(gestureData);
                dispatchGesture(state, gestureData.getGesture().name(), command, gestureData.getConfidence());
                return;
            }

            String rawGesture = json.optString("gesture", "IDLE");
            double confidence = json.optDouble("confidence", 0.0);
            GestureCommand command = GestureCommandResolver.resolve(rawGesture);
            dispatchGesture(state, rawGesture, command, confidence);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "串流消息解析失败: " + message, ex);
        }
    }

    private boolean containsGestureData(JSONObject json) {
        return json.has("handX")
                || json.has("handY")
                || json.has("prevHandX")
                || json.has("prevHandY")
                || json.has("velocityX")
                || json.has("velocityY")
                || json.has("handDetected");
    }

    private void dispatchCameraFrame(String state, String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return;
        }

        if (AppStateManager.STATE_LOGIN.equals(state) && loginController != null) {
            loginController.updateCameraStream(base64Image);
        } else if (AppStateManager.STATE_LOBBY.equals(state) && lobbyController != null) {
            lobbyController.updateCameraStream(base64Image);
        }
        // GAME 状态：游戏全屏渲染，不分发摄像头画面
    }

    private void dispatchGesture(String state, String rawGesture, GestureCommand command, double confidence) {
        LOGGER.info(() -> "收到串流指令: raw=" + rawGesture
                + ", mapped=" + command
                + ", confidence=" + confidence
                + ", state=" + state);

        if (AppStateManager.STATE_LOGIN.equals(state) && loginController != null) {
            loginController.handleAgentCommand(command, confidence, "UNKNOWN");
        } else if (AppStateManager.STATE_LOBBY.equals(state) && lobbyController != null) {
            lobbyController.handleAgentCommand(command, confidence, "UNKNOWN");
        } else if (AppStateManager.STATE_GAME.equals(state) && gameRenderer != null) {
            gameRenderer.handleAgentCommand(command, confidence, "UNKNOWN");
        }
    }

    /**
     * 返回最近一帧手势数据，供 GAME 场景的游戏循环读取。
     * 线程安全：字段为 volatile，返回的 GestureData 在 fromJson 后不再被修改。
     */
    public GestureData getLatestGesture() {
        return latestGestureData;
    }

    private GestureCommand mapGestureDataToCommand(GestureData gestureData) {
        if (!gestureData.isHandDetected()) {
            resetHoldState();
            return GestureCommand.NONE;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(gestureData.getVelocityX()) >= SWIPE_VELOCITY_THRESHOLD
                && Math.abs(gestureData.getVelocityX()) > Math.abs(gestureData.getVelocityY())) {
            resetHoldState();
            if (now - lastSwipeCommandTime < SWIPE_COOLDOWN_MS) {
                return GestureCommand.NONE;
            }
            lastSwipeCommandTime = now;
            return gestureData.getVelocityX() < 0 ? GestureCommand.SWIPE_LEFT : GestureCommand.SWIPE_RIGHT;
        }

        GestureType gestureType = gestureData.getGesture();
        if (gestureType != GestureType.FIST && gestureType != GestureType.OPEN) {
            resetHoldState();
            return GestureCommand.NONE;
        }

        if (gestureType != lastHoldGesture) {
            lastHoldGesture = gestureType;
            holdStartTime = now;
            return GestureCommand.NONE;
        }

        if (now - holdStartTime < HOLD_CONFIRM_MS) {
            return GestureCommand.NONE;
        }
        if (now - lastStaticCommandTime < COMMAND_COOLDOWN_MS) {
            return GestureCommand.NONE;
        }

        lastStaticCommandTime = now;
        holdStartTime = now;
        return gestureType == GestureType.FIST ? GestureCommand.CONFIRM : GestureCommand.BACK;
    }

    private void resetHoldState() {
        lastHoldGesture = GestureType.NONE;
        holdStartTime = 0L;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        LOGGER.info(() -> "[Socket] 串流连接已关闭: code=" + code + ", reason=" + reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOGGER.log(Level.WARNING, "串流服务器异常", ex);
    }

    @Override
    public void onStart() {
        LOGGER.info(() -> "[Server] 串流服务器启动成功，端口: " + getPort());
    }
}
