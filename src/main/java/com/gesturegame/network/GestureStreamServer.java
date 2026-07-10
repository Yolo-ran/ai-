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
    // 滑动判定：位移提交 + 方向感知锁定（防回弹误触）
    private static final double SWIPE_ARM_VELOCITY = 0.028;
    private static final double SWIPE_COMMIT_DISTANCE = 0.12;
    private static final double SWIPE_ABORT_REVERSE = -0.05;
    private static final long SWIPE_ARM_TIMEOUT_MS = 500L;
    private static final double SWIPE_REST_VELOCITY = 0.012;
    private static final long SWIPE_REST_MS = 180L;
    private static final double SWIPE_NEUTRAL_RADIUS = 0.05;
    private static final double SWIPE_VXY_RATIO = 1.3;
    private static final long LOGIN_CONFIRM_HOLD_MS = 1000L;
    private static final long LOBBY_CONFIRM_HOLD_MS = 1200L;
    private static final long LOBBY_BACK_HOLD_MS = 1500L;
    private static final long GAME_BACK_HOLD_MS = 1800L;
    private static final long GAME_OVER_ENTRY_GUARD_MS = 400L;
    private static final long GAME_OVER_CONFIRM_HOLD_MS = 1000L;
    private static final long GAME_OVER_BACK_HOLD_MS = 1200L;
    private static final long COMMAND_COOLDOWN_MS = 450L;
    private static final long CAMERA_FRAME_INTERVAL_MS = 80L;
    private static final long IDLE_DISPATCH_INTERVAL_MS = 200L;
    private static final long LOGIN_ENTRY_GUARD_MS = 800L;
    private static final long LOBBY_ENTRY_GUARD_MS = 600L;
    private static final long GAME_ENTRY_GUARD_MS = 2500L;

    private final LoginController loginController;
    private final LobbyController lobbyController;
    private final GameRenderer gameRenderer;
    private volatile GestureData latestGestureData = new GestureData();
    private GestureType lastHoldGesture = GestureType.NONE;
    private long holdStartTime;

    private enum SwipePhase { IDLE, ARMED }
    private SwipePhase swipePhase = SwipePhase.IDLE;
    private double swipeArmX;
    private double swipeArmDir;
    private long swipeArmTime;
    private int swipeBlockSign;
    private long swipeBlockRestSince;
    private double swipeLastArmX;

    private long lastStaticCommandTime;
    private long lastCameraFrameTime;
    private long lastIdleDispatchTime;
    private String lastObservedState = AppStateManager.STATE_LOGIN;
    private long stateEnterTime = System.currentTimeMillis();

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
            onStateObserved(state);

            dispatchCameraFrame(state, base64Image);

            if (containsGestureData(json)) {
                GestureData gestureData = buildGestureData(json);
                this.latestGestureData = gestureData;
                GestureCommand command = mapGestureDataToCommand(state, gestureData);
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

    /**
     * 从已解析的 JSONObject 直接构建 GestureData，避免对报文做二次 JSON 解析。
     *
     * <p>字段默认值与 {@link GestureData#fromJson(String)} 保持一致，遵循 AGENTS §4.1 协议；
     * 该方法仅消费 common 层的公有构造与 {@link GestureType#fromString}，不修改 common。
     */
    private GestureData buildGestureData(JSONObject json) {
        double handX = json.optDouble("handX", 0.0);
        double handY = json.optDouble("handY", 0.0);
        return new GestureData(
                handX,
                handY,
                json.optDouble("prevHandX", handX),
                json.optDouble("prevHandY", handY),
                json.optDouble("velocityX", 0.0),
                json.optDouble("velocityY", 0.0),
                GestureType.fromString(json.optString("gesture", "none")),
                json.optDouble("confidence", 0.0),
                json.optBoolean("handDetected", false));
    }

    private void dispatchCameraFrame(String state, String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCameraFrameTime < CAMERA_FRAME_INTERVAL_MS) {
            return;
        }
        lastCameraFrameTime = now;

        if (AppStateManager.STATE_LOGIN.equals(state) && loginController != null) {
            loginController.updateCameraStream(base64Image);
        } else if (AppStateManager.STATE_LOBBY.equals(state) && lobbyController != null) {
            lobbyController.updateCameraStream(base64Image);
        }
        // GAME 状态：游戏全屏渲染，不分发摄像头画面
    }

    private void dispatchGesture(String state, String rawGesture, GestureCommand command, double confidence) {
        long now = System.currentTimeMillis();
        if (command == GestureCommand.NONE && now - lastIdleDispatchTime < IDLE_DISPATCH_INTERVAL_MS) {
            return;
        }
        if (command == GestureCommand.NONE) {
            lastIdleDispatchTime = now;
        }

        LOGGER.info(() -> "收到串流指令: raw=" + rawGesture
                + ", mapped=" + command
                + ", confidence=" + confidence
                + ", state=" + state);

        if (AppStateManager.STATE_LOGIN.equals(state) && loginController != null) {
            loginController.handleAgentCommand(command, confidence, "STREAM");
        } else if (AppStateManager.STATE_LOBBY.equals(state) && lobbyController != null) {
            lobbyController.handleAgentCommand(command, confidence, "STREAM");
        } else if (AppStateManager.STATE_GAME.equals(state) && gameRenderer != null) {
            gameRenderer.handleAgentCommand(command, confidence, "STREAM");
        } else if (AppStateManager.STATE_GAME_OVER.equals(state) && gameRenderer != null) {
            gameRenderer.handleAgentCommand(command, confidence, "STREAM");
        }
    }

    /**
     * 返回最近一帧手势数据，供 GAME 场景的游戏循环读取。
     * 线程安全：字段为 volatile，返回的 GestureData 在 fromJson 后不再被修改。
     */
    public GestureData getLatestGesture() {
        return latestGestureData;
    }

    private GestureCommand mapGestureDataToCommand(String state, GestureData gestureData) {
        if (!gestureData.isHandDetected()) {
            resetHoldState();
            resetSwipeState();
            return GestureCommand.NONE;
        }

        long now = System.currentTimeMillis();
        if (isInStateEntryGuard(state, now)) {
            resetHoldState();
            resetSwipeState();
            return GestureCommand.NONE;
        }

        GestureCommand swipeCommand = updateSwipe(gestureData, now);
        if (swipeCommand != GestureCommand.NONE) {
            resetHoldState();
            return swipeCommand;
        }
        if (swipePhase != SwipePhase.IDLE) {
            // 正在起划/锁定恢复中，抑制 hold，避免划动误触确认或返回
            resetHoldState();
            return GestureCommand.NONE;
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

        long requiredHoldMs = resolveRequiredHoldMs(state, gestureType);
        if (now - holdStartTime < requiredHoldMs) {
            return GestureCommand.NONE;
        }
        if (now - lastStaticCommandTime < COMMAND_COOLDOWN_MS) {
            return GestureCommand.NONE;
        }

        lastStaticCommandTime = now;
        holdStartTime = now;
        return gestureType == GestureType.FIST ? GestureCommand.CONFIRM : GestureCommand.BACK;
    }

    /**
     * 位移提交 + 方向感知锁定的滑动状态机。
     *
     * <p>起划（速度过阈）进入 ARMED，仅当手在某一方向累计净位移达到阈值才提交该方向滑动；
     * 回弹净位移小、达不到阈值，不会误触。提交后锁反方向，直到手停顿或回到起划点附近才解锁，
     * 从而彻底吸收回弹；同方向可立即续划。
     */
    private GestureCommand updateSwipe(GestureData gestureData, long now) {
        double vx = gestureData.getVelocityX();
        double vy = gestureData.getVelocityY();
        double handX = gestureData.getHandX();

        if (swipeBlockSign != 0) {
            boolean rested = Math.abs(vx) < SWIPE_REST_VELOCITY;
            if (rested) {
                if (swipeBlockRestSince == 0L) {
                    swipeBlockRestSince = now;
                }
            } else {
                swipeBlockRestSince = 0L;
            }
            boolean clearByRest = rested && now - swipeBlockRestSince >= SWIPE_REST_MS;
            boolean clearByNeutral = Math.abs(handX - swipeLastArmX) < SWIPE_NEUTRAL_RADIUS;
            if (clearByRest || clearByNeutral) {
                swipeBlockSign = 0;
                swipeBlockRestSince = 0L;
            }
        }

        if (swipePhase == SwipePhase.ARMED) {
            double progress = (handX - swipeArmX) * swipeArmDir;
            if (progress >= SWIPE_COMMIT_DISTANCE) {
                GestureCommand dir = swipeArmDir < 0
                        ? GestureCommand.SWIPE_LEFT : GestureCommand.SWIPE_RIGHT;
                swipeBlockSign = (int) -swipeArmDir;
                swipeLastArmX = swipeArmX;
                swipeBlockRestSince = 0L;
                swipePhase = SwipePhase.IDLE;
                return dir;
            }
            if (progress < SWIPE_ABORT_REVERSE || now - swipeArmTime > SWIPE_ARM_TIMEOUT_MS) {
                swipePhase = SwipePhase.IDLE;
            }
            return GestureCommand.NONE;
        }

        if (Math.abs(vx) >= SWIPE_ARM_VELOCITY
                && Math.abs(vx) > Math.abs(vy) * SWIPE_VXY_RATIO) {
            int dirSign = vx < 0 ? -1 : 1;
            if (swipeBlockSign != 0 && dirSign == swipeBlockSign) {
                return GestureCommand.NONE;
            }
            swipePhase = SwipePhase.ARMED;
            swipeArmX = handX;
            swipeArmDir = dirSign;
            swipeArmTime = now;
        }
        return GestureCommand.NONE;
    }

    private void resetSwipeState() {
        swipePhase = SwipePhase.IDLE;
        swipeBlockSign = 0;
        swipeBlockRestSince = 0L;
    }

    private void onStateObserved(String state) {
        if (state.equals(lastObservedState)) {
            return;
        }

        lastObservedState = state;
        stateEnterTime = System.currentTimeMillis();
        resetHoldState();
        resetSwipeState();
        lastStaticCommandTime = stateEnterTime;
        LOGGER.info(() -> "[GestureStream] 场景切换，启用手势保护: " + state);
    }

    private boolean isInStateEntryGuard(String state, long now) {
        return now - stateEnterTime < resolveEntryGuardMs(state);
    }

    private long resolveEntryGuardMs(String state) {
        if (AppStateManager.STATE_GAME.equals(state)) {
            return GAME_ENTRY_GUARD_MS;
        }
        if (AppStateManager.STATE_GAME_OVER.equals(state)) {
            return GAME_OVER_ENTRY_GUARD_MS;
        }
        if (AppStateManager.STATE_LOBBY.equals(state)) {
            return LOBBY_ENTRY_GUARD_MS;
        }
        return LOGIN_ENTRY_GUARD_MS;
    }

    private long resolveRequiredHoldMs(String state, GestureType gestureType) {
        if (AppStateManager.STATE_GAME.equals(state)) {
            return gestureType == GestureType.OPEN ? GAME_BACK_HOLD_MS : Long.MAX_VALUE;
        }
        if (AppStateManager.STATE_GAME_OVER.equals(state)) {
            if (gestureType == GestureType.FIST) {
                return GAME_OVER_CONFIRM_HOLD_MS;
            }
            if (gestureType == GestureType.OPEN) {
                return GAME_OVER_BACK_HOLD_MS;
            }
            return Long.MAX_VALUE;
        }
        if (AppStateManager.STATE_LOBBY.equals(state)) {
            return gestureType == GestureType.FIST ? LOBBY_CONFIRM_HOLD_MS : LOBBY_BACK_HOLD_MS;
        }
        if (AppStateManager.STATE_LOGIN.equals(state)) {
            return gestureType == GestureType.FIST ? LOGIN_CONFIRM_HOLD_MS : Long.MAX_VALUE;
        }
        return Long.MAX_VALUE;
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
