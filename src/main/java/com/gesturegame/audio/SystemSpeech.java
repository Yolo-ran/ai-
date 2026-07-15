package com.gesturegame.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin wrapper around the Windows system speech engine.
 */
public final class SystemSpeech {

    private static final Logger LOGGER = Logger.getLogger(SystemSpeech.class.getName());
    private static final boolean ENABLED = !Boolean.getBoolean("gesturegame.speech.disabled");
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gesture-game-speech");
        thread.setDaemon(true);
        return thread;
    });

    private SystemSpeech() {
    }

    public static void speak(String text) {
        if (!ENABLED || !WINDOWS || text == null || text.isBlank()) {
            return;
        }
        String message = text.trim();
        EXECUTOR.submit(() -> speakOnWindows(message));
    }

    private static void speakOnWindows(String text) {
        try {
            java.nio.file.Path vbs = java.nio.file.Files.createTempFile("speech_", ".vbs");
            String safe = text.replace("\"", "'");
            String script = "Dim voice: Set voice = CreateObject(\"SAPI.SpVoice\")\r\n"
                    // 优先选中文字音色，找不到就用默认
                    + "Dim token: For Each token In voice.GetVoices\r\n"
                    + "  If InStr(token.GetDescription, \"Chinese\") > 0 Then\r\n"
                    + "    Set voice.Voice = token: Exit For\r\n"
                    + "  End If\r\n"
                    + "Next\r\n"
                    + "voice.Rate = 1\r\n"       // 语速稍慢（默认0，范围-10~10）
                    + "voice.Volume = 100\r\n"
                    + "voice.Speak \"" + safe + "\"\r\n";
            java.nio.file.Files.write(vbs, script.getBytes("GBK"));
            new ProcessBuilder("cscript", "//Nologo", vbs.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(8, TimeUnit.SECONDS);
            try { java.nio.file.Files.deleteIfExists(vbs); } catch (IOException ignored) {}
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.FINE, "语音播报不可用", ex);
        }
    }
}
