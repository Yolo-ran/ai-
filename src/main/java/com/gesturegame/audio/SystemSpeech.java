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
        ExecutorService executor = EXECUTOR; // 复用类级 executor
        // 方案1: Python edge-tts（免费、音色统一、跨电脑一致）
        // 方案2: PowerShell .NET Speech（Win10/11 自然语音）
        // 方案3: SAPI VBS（所有 Windows 兜底）
        String safe = text.replace("\"", "'");
        if (tryEdgeTts(safe)) return;
        if (tryDotNetSpeech(safe)) return;
        trySapiFallback(safe);
    }

    /** Python edge-tts — 免费 Edge TTS，所有电脑音色统一 */
    private static boolean tryEdgeTts(String safe) {
        try {
            java.nio.file.Path mp3 = java.nio.file.Files.createTempFile("speech_", ".mp3");
            String cmd = "import edge_tts,asyncio;"
                    + "async def f():"
                    + " tts=edge_tts.Communicate('" + safe + "','zh-CN-XiaoxiaoNeural');"
                    + " await tts.save('" + mp3.toString().replace("\\", "\\\\") + "');"
                    + "asyncio.run(f())";
            Process p = new ProcessBuilder("python", "-c", cmd)
                    .redirectErrorStream(true).start();
            if (p.waitFor(12, TimeUnit.SECONDS) && p.exitValue() == 0) {
                // 播放 mp3（用 PowerShell 系统播放器）
                new ProcessBuilder("powershell", "-c",
                        "(New-Object Media.SoundPlayer '" + mp3 + "').PlaySync()")
                        .redirectErrorStream(true).start().waitFor(8, TimeUnit.SECONDS);
                try { java.nio.file.Files.deleteIfExists(mp3); } catch (IOException ignored) {}
                return true;
            }
            try { java.nio.file.Files.deleteIfExists(mp3); } catch (IOException ignored) {}
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean tryDotNetSpeech(String safe) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command",
                    "Add-Type -AssemblyName System.Speech;"
                    + "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                    + "try{$s.SelectVoice('Microsoft Xiaoxiao')}catch{};"
                    + "$s.Rate=-1;$s.Volume=100;$s.Speak('" + safe + "')")
                    .redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception ignored) { return false; }
    }

    private static void trySapiFallback(String text) {
        try {
            java.nio.file.Path vbs = java.nio.file.Files.createTempFile("speech_", ".vbs");
            String script = "Dim voice: Set voice = CreateObject(\"SAPI.SpVoice\")\r\n"
                    + "Dim token: For Each token In voice.GetVoices\r\n"
                    + "  If InStr(token.GetDescription,\"Chinese\")>0 Then Set voice.Voice=token:Exit For\r\n"
                    + "  End If\r\nNext\r\n"
                    + "voice.Rate=-2:voice.Volume=100\r\n"
                    + "voice.Speak \"" + text.replace("\"", "'") + "\"\r\n";
            java.nio.file.Files.write(vbs, script.getBytes("GBK"));
            new ProcessBuilder("cscript", "//Nologo", vbs.toString())
                    .redirectErrorStream(true).start().waitFor(8, TimeUnit.SECONDS);
            try { java.nio.file.Files.deleteIfExists(vbs); } catch (IOException ignored) {}
        } catch (Exception ignored) {}
    }
}
