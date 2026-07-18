package com.gesturegame.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class CameraStreamHelper {

    private static final Logger LOGGER = Logger.getLogger(CameraStreamHelper.class.getName());
    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final String DATA_URI_SEPARATOR = ",";
    private static final Executor DECODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "camera-decode");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean DECODING = new AtomicBoolean(false);

    private CameraStreamHelper() {
    }

    public static void push(ImageView targetView, String base64Image) {
        if (base64Image == null || base64Image.isBlank() || targetView == null) {
            return;
        }
        if (!DECODING.compareAndSet(false, true)) {
            return;
        }

        String payload = base64Image;
        int separatorIndex = payload.indexOf(DATA_URI_SEPARATOR);
        if (separatorIndex >= 0) {
            payload = payload.substring(separatorIndex + 1);
        }

        final String finalPayload = payload;
        DECODE_EXECUTOR.execute(() -> {
            try {
                byte[] bytes = DECODER.decode(finalPayload);
                Image image = new Image(new ByteArrayInputStream(bytes));
                Platform.runLater(() -> targetView.setImage(image));
            } catch (Exception e) {
                LOGGER.fine(() -> "帧解码失败: " + e.getMessage());
            } finally {
                DECODING.set(false);
            }
        });
    }
}
