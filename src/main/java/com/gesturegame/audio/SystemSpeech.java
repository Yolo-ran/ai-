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
        String payload = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String command = "$text=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($args[0]));"
                + "$voice=New-Object -ComObject SAPI.SpVoice;"
                + "[void]$voice.Speak($text)";
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command,
                    payload)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.warning("语音播报超时，已终止本次播报");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "语音播报不可用", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "语音播报被中断", ex);
        }
    }
}
