package com.gesturegame.network;

import com.gesturegame.engine.AppStateManager;
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
 */
public class GestureStreamServer extends WebSocketServer {

    private static final Logger LOGGER = Logger.getLogger(GestureStreamServer.class.getName());

    private final LoginController loginController;
    private final LobbyController lobbyController;

    public GestureStreamServer(int port, LoginController loginController, LobbyController lobbyController) {
        super(new InetSocketAddress(port));
        this.loginController = loginController;
        this.lobbyController = lobbyController;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOGGER.info("[Socket] Python 视觉串流端已成功建立连接");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject json = new JSONObject(message);
            String rawGesture = json.optString("gesture", "IDLE");
            double confidence = json.optDouble("confidence", 0.0);
            String base64Image = json.optString("image_data", "");

            GestureCommand command = GestureCommandResolver.resolve(rawGesture);
            String state = AppStateManager.getInstance().getCurrentState();

            dispatchCameraFrame(state, base64Image);
            dispatchGesture(state, rawGesture, command, confidence);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "串流消息解析失败: " + message, ex);
        }
    }

    private void dispatchCameraFrame(String state, String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return;
        }

        if ("LOGIN".equals(state) && loginController != null) {
            loginController.updateCameraStream(base64Image);
        } else if ("LOBBY".equals(state) && lobbyController != null) {
            lobbyController.updateCameraStream(base64Image);
        }
    }

    private void dispatchGesture(String state, String rawGesture, GestureCommand command, double confidence) {
        LOGGER.info(() -> "收到串流指令: raw=" + rawGesture
                + ", mapped=" + command
                + ", confidence=" + confidence
                + ", state=" + state);

        if ("LOGIN".equals(state) && loginController != null) {
            loginController.handleAgentCommand(command, confidence, "UNKNOWN");
        } else if ("LOBBY".equals(state) && lobbyController != null) {
            lobbyController.handleAgentCommand(command, confidence, "UNKNOWN");
        }
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
