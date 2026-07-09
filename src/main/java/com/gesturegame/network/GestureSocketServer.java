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
 * 手势通信桥梁，负责接收 Python 视觉识别端发送的 JSON 指令。
 */
public class GestureSocketServer extends WebSocketServer {

    private static final Logger LOGGER = Logger.getLogger(GestureSocketServer.class.getName());

    private final LoginController loginController;
    private final LobbyController lobbyController;

    public GestureSocketServer(int port, LoginController loginController, LobbyController lobbyController) {
        super(new InetSocketAddress(port));
        this.loginController = loginController;
        this.lobbyController = lobbyController;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOGGER.info("[Socket 连通] Python 视觉识别端已成功连接");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject json = new JSONObject(message);
            String rawGesture = json.optString("gesture", "");
            double confidence = json.optDouble("confidence", 0.0);
            String hand = json.optString("hand", "UNKNOWN");
            GestureCommand command = GestureCommandResolver.resolve(rawGesture);

            String state = AppStateManager.getInstance().getCurrentState();
            LOGGER.info(() -> "收到手势指令: raw=" + rawGesture
                    + ", mapped=" + command
                    + ", confidence=" + confidence
                    + ", hand=" + hand);

            if ("LOGIN".equals(state)) {
                loginController.handleAgentCommand(command, confidence, hand);
            } else if ("LOBBY".equals(state)) {
                lobbyController.handleAgentCommand(command, confidence, hand);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "解析手势报文失败: " + message, ex);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        LOGGER.info(() -> "[Socket 断开] code=" + code + ", reason=" + reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOGGER.log(Level.WARNING, "Socket 服务异常", ex);
    }

    @Override
    public void onStart() {
        LOGGER.info(() -> "[Socket 服务器] 启动成功，监听端口: " + getPort());
    }
}
