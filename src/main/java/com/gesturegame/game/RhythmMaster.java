package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.*;

/**
 * 🥁 手势节奏大师 — 本地音乐 + 自动谱面
 *
 * 流程：选歌（浏览音频文件）→ 自动生成谱面 → 播放音乐 → 音符按谱面下落 → 判定 → 结束
 *
 * 支持的音频格式：mp3, wav, flac, ogg, m4a, aac
 */
public class RhythmMaster implements GameInterface {

    private static final Random RAND = new Random();
    private static final String[] AUDIO_EXTS = {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac",
                                                 ".MP3", ".WAV", ".FLAC", ".OGG", ".M4A", ".AAC"};

    private enum State { SONG_SELECT, BPM_READY, BPM_CALIBRATE, PLAYING, GAME_OVER }
    private int bpmReadyFrames; // 说明页倒计时
    private State state = State.SONG_SELECT;

    // ===== 轨道 =====
    private int laneCount;
    private double[] laneX;
    private double judgeLineY, laneWidth, targetZoneH;

    // ===== 音符 =====
    private List<Note> notes;
    private List<FloatText> floatTexts;
    private List<LaneHit> laneHits;

    // ===== 游戏数据 =====
    private int canvasWidth, canvasHeight;
    private int score, combo, maxCombo;
    private int perfectCount, greatCount, missCount;
    private int frameCount;
    private GestureType currentGesture = GestureType.NONE;
    private boolean handDetected;
    private String performanceComment;

    // ===== 难度 =====
    private Difficulty difficulty = Difficulty.NORMAL;
    private double noteSpeed;
    private int perfectWindow, greatWindow;

    // ===== 光标 =====
    private double handX = 0.5;
    private int activeLane;
    private double cursorX, cursorAlpha;
    private double[][] stars;

    // 选歌确认握拳计时
    private int songHoldFrames;
    private static final int SONG_HOLD_REQUIRED = 72; // 1.2秒

    // BPM 校准
    private List<Double> tapTimes;      // 打节拍的时间点（秒）
    private static final int TAP_REQUIRED = 8;  // 需要8次打拍
    private int tapCountdown;           // 倒计时显示
    private boolean prevTapGesture;     // 上一帧是否在做确认手势（防连击）

    // ===== 音乐系统 =====
    private List<SongInfo> songs;
    private int selectedSongIdx;
    private SongInfo selectedSong;
    private MediaPlayer mediaPlayer;
    private double musicStartNano;
    private double travelSeconds;
    private List<BeatmapNote> beatmapNotes;
    private int nextBeatmapIdx;
    private boolean musicStarted;
    private int defaultBpm = 120;  // 默认BPM，后续可让用户设

    // ===== 无音乐兜底 =====
    private boolean noMusicFallback;
    private java.util.List<Integer> scheduledFrames;
    private int scheduleIdx;
    private int gameDurationFrames;

    // ===== 接口 =====
    @Override public String getName() { return "节奏大师"; }
    @Override public String getDescription() { return "手势跳舞机，跟随音乐节拍！"; }
    @Override public String getIcon() { return "🥁"; }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0; this.combo = 0; this.maxCombo = 0;
        this.frameCount = 0;
        this.perfectCount = 0; this.greatCount = 0; this.missCount = 0;
        this.notes = new ArrayList<>();
        this.floatTexts = new ArrayList<>();
        this.laneHits = new ArrayList<>();
        this.currentGesture = GestureType.NONE;
        this.handDetected = false;
        this.state = State.SONG_SELECT;
        this.musicStarted = false;

        applyDifficulty();
        initStars();
        scanForMusic();
    }

    private void initStars() {
        stars = new double[100][3];
        for (int i = 0; i < 100; i++) {
            stars[i][0] = RAND.nextDouble() * canvasWidth;
            stars[i][1] = RAND.nextDouble() * canvasHeight;
            stars[i][2] = 0.3 + RAND.nextDouble() * 0.7;
        }
    }

    @Override public void setDifficulty(Difficulty d) { this.difficulty = d; }

    private void applyDifficulty() {
        switch (difficulty) {
            case EASY:
                laneCount = 2; noteSpeed = 3.0;
                perfectWindow = 30; greatWindow = 60;
                defaultBpm = 100; break;
            case NORMAL:
                laneCount = 2; noteSpeed = 4.5;
                perfectWindow = 22; greatWindow = 48;
                defaultBpm = 120; break;
            case HARD:
                laneCount = 3; noteSpeed = 6.5;
                perfectWindow = 15; greatWindow = 35;
                defaultBpm = 140; break;
        }
        travelSeconds = (canvasHeight * 0.85 + 60) / (noteSpeed * 60.0);
    }

    @Override public Difficulty getDifficulty() { return difficulty; }
    @Override public boolean supportsDifficulty(Difficulty d) { return d != Difficulty.ENDLESS; }

    // ===== 扫描音频文件 =====
    private void scanForMusic() {
        songs = new ArrayList<>();
        System.out.println("[RhythmMaster] Scanning for music... cwd=" + new File(".").getAbsolutePath());

        // 多路径搜索
        List<File> searchDirs = new ArrayList<>();
        String[] tryPaths = {
            "music",
            "src/main/resources/music",
            "target/classes/music",
            System.getProperty("user.dir") + "/music",
        };
        for (String p : tryPaths) {
            File f = new File(p);
            System.out.println("[RhythmMaster]   try: " + f.getAbsolutePath() + " -> " + (f.exists() && f.isDirectory()));
            if (f.exists() && f.isDirectory()) searchDirs.add(f);
        }

        if (searchDirs.isEmpty()) {
            new File("music").mkdirs();
            searchDirs.add(new File("music"));
            System.out.println("[RhythmMaster] No music dir found, created music/");
        }

        // 收集音频文件
        Set<String> seen = new HashSet<>();
        for (File dir : searchDirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isDirectory()) continue;
                String name = f.getName();
                boolean isAudio = false;
                for (String ext : AUDIO_EXTS) {
                    if (name.endsWith(ext)) { isAudio = true; break; }
                }
                if (!isAudio || seen.contains(name)) continue;
                seen.add(name);

                String title = name.substring(0, name.lastIndexOf('.'));
                double estDuration = estimateDuration(f);
                songs.add(new SongInfo(title, f, estDuration));
                System.out.println("[RhythmMaster]   found: " + name + " (" + (int)estDuration + "s)");
            }
        }

        songs.sort(Comparator.comparing(s -> s.title));
        System.out.println("[RhythmMaster] Total: " + songs.size() + " songs");

        // 无论有没有歌，都先显示选歌界面
        initGameLayout();
    }

    /** 估算音频时长：wav文件读header，其他格式给默认值 */
    private double estimateDuration(File f) {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".wav")) {
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
                raf.seek(28);
                int byteRate = Integer.reverseBytes(raf.readInt());
                raf.seek(4);
                int chunkSize = Integer.reverseBytes(raf.readInt());
                if (byteRate > 0) return (chunkSize - 36) / (double) byteRate;
            } catch (Exception ignored) {}
        }
        return 90.0; // 默认90秒
    }

    /** 自动生成谱面 */
    private void generateBeatmapFromSong() {
        if (selectedSong == null) return;
        beatmapNotes = new ArrayList<>();

        double duration = selectedSong.duration;
        double beatInterval = 60.0 / defaultBpm;  // 每拍秒数
        GestureType[] gestures = {GestureType.FIST, GestureType.OPEN, GestureType.PEACE};

        // 在节拍上生成音符，单手操作保证不重叠
        double minGap = Math.max(0.45, beatInterval * 0.6); // 最小间隔0.45秒
        double t = beatInterval;
        while (t < duration - 2) {
            int lane = RAND.nextInt(laneCount);
            GestureType gt = gestures[RAND.nextInt(3)];
            beatmapNotes.add(new BeatmapNote(t, lane, gt));

            // 节奏变化，但保证不小于最小间隔
            double r = RAND.nextDouble();
            double gap;
            if (r < 0.3) gap = beatInterval;             // 一拍
            else if (r < 0.7) gap = beatInterval * 1.5;   // 一拍半
            else gap = beatInterval * 2.0;                 // 两拍
            t += Math.max(minGap, gap);
        }

        nextBeatmapIdx = 0;
        System.out.println("[RhythmMaster] Generated " + beatmapNotes.size() + " notes for BPM " + defaultBpm);
    }

    private void initGameLayout() {
        this.laneWidth = canvasWidth / (laneCount + 1.0);
        this.laneX = new double[laneCount];
        for (int i = 0; i < laneCount; i++) laneX[i] = laneWidth * (i + 1);
        this.judgeLineY = canvasHeight * 0.85;
        this.targetZoneH = canvasHeight * 0.08;
        this.activeLane = 0;
        this.cursorX = laneX[0];
    }

    private void generateFallbackBeat() {
        int intervalMin = 20, intervalMax = 35;
        java.util.Set<Integer> bf = new java.util.HashSet<>();
        int t = 60;
        while (t < 5400) { bf.add(t); t += intervalMin + RAND.nextInt(intervalMax - intervalMin + 1); }
        this.scheduledFrames = new java.util.ArrayList<>(bf);
        java.util.Collections.sort(this.scheduledFrames);
        this.scheduleIdx = 0;
        this.gameDurationFrames = 5400;
    }

    /** 用户确认选歌 → 进入BPM校准或直接开始 */
    private void confirmSong() {
        initGameLayout();
        notes.clear(); floatTexts.clear(); laneHits.clear();
        score = 0; combo = 0; maxCombo = 0;
        perfectCount = 0; greatCount = 0; missCount = 0;
        frameCount = 0;

        if (songs != null && !songs.isEmpty() && selectedSongIdx < songs.size()) {
            selectedSong = songs.get(selectedSongIdx);
            // 启动音乐 + 进入BPM校准
            try {
                Media media = new Media(selectedSong.file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setOnEndOfMedia(() -> { if (state != State.GAME_OVER) gameOver(); });
                mediaPlayer.play();
                musicStartNano = System.nanoTime();
                musicStarted = true;
            } catch (Exception e) {
                System.err.println("[RhythmMaster] Cannot play: " + e.getMessage());
                musicStarted = false;
            }

            // 先显示说明页，再进入BPM校准
            tapTimes = new ArrayList<>();
            prevTapGesture = false;
            tapCountdown = TAP_REQUIRED;
            bpmReadyFrames = 300; // 5秒说明
            state = State.BPM_READY;
        } else {
            // 没歌：直接随机节拍
            noMusicFallback = true;
            musicStarted = false;
            generateFallbackBeat();
            state = State.PLAYING;
        }
    }

    /** 校准完成 → 根据打拍结果生成谱面 */
    private void finishCalibration() {
        if (tapTimes.size() < 3) {
            // 打拍太少，用默认BPM
            defaultBpm = difficulty == Difficulty.EASY ? 100
                       : difficulty == Difficulty.NORMAL ? 120 : 140;
        } else {
            // 计算平均间隔
            double totalGap = 0;
            for (int i = 1; i < tapTimes.size(); i++) {
                totalGap += tapTimes.get(i) - tapTimes.get(i - 1);
            }
            double avgGap = totalGap / (tapTimes.size() - 1);
            defaultBpm = (int) Math.round(60.0 / avgGap);
            defaultBpm = Math.max(60, Math.min(200, defaultBpm)); // 限制范围
        }
        System.out.println("[RhythmMaster] Calibrated BPM: " + defaultBpm + " from " + tapTimes.size() + " taps");

        generateBeatmapFromSong();
        frameCount = 0;
        state = State.PLAYING;
        // 音乐已经播了，直接接上
    }

    private void gameOver() {
        state = State.GAME_OVER;
        performanceComment = generatePerformanceComment();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    public void update(GestureData gesture) {
        if (state == State.GAME_OVER) return;

        currentGesture = gesture.getGesture();
        handDetected = gesture.isHandDetected();

        // === 选歌阶段 ===
        if (state == State.SONG_SELECT) {
            if (handDetected && songs != null) {
                double relY = gesture.getHandY();
                int maxIdx = songs.size(); // 多一个位置给 "+"
                selectedSongIdx = Math.max(0, Math.min(maxIdx, (int)(relY * (maxIdx + 1))));
            }
            // 握拳1.2秒确认选歌（或添加歌曲）
            if (gesture.getGesture() == GestureType.FIST && handDetected) {
                songHoldFrames++;
            } else {
                songHoldFrames = 0;
            }
            if (songHoldFrames >= SONG_HOLD_REQUIRED) {
                songHoldFrames = 0;
                if (selectedSongIdx == songs.size()) {
                    openFileChooser();  // "+" 按钮：添加本地歌曲
                } else {
                    confirmSong();
                }
            }
            return;
        }

        // === BPM 说明页 ===
        if (state == State.BPM_READY) {
            bpmReadyFrames--;
            if (bpmReadyFrames <= 0) {
                state = State.BPM_CALIBRATE;
            }
            return;
        }

        // === BPM 校准阶段 ===
        if (state == State.BPM_CALIBRATE) {
            double musicTime = mediaPlayer != null ? mediaPlayer.getCurrentTime().toSeconds() : frameCount / 60.0;
            boolean tapNow = (handDetected && gesture.getGesture() == GestureType.FIST);

            if (tapNow && !prevTapGesture) {
                tapTimes.add(musicTime);
                tapCountdown = TAP_REQUIRED - tapTimes.size();
                System.out.println("[RhythmMaster] Tap " + tapTimes.size() + " at " + String.format("%.2f", musicTime) + "s");
            }
            prevTapGesture = tapNow;

            if (tapTimes.size() >= TAP_REQUIRED) {
                finishCalibration();
            }
            return;
        }

        // === 游戏阶段 ===
        frameCount++;

        double musicTime;
        if (musicStarted && mediaPlayer != null) {
            musicTime = mediaPlayer.getCurrentTime().toSeconds();
        } else {
            musicTime = frameCount / 60.0;
        }

        // 光标
        if (handDetected) {
            handX = gesture.getHandX();
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < laneCount; i++) {
                double dist = Math.abs(handX * canvasWidth - laneX[i]);
                if (dist < minDist) { minDist = dist; activeLane = i; }
            }
            cursorAlpha = Math.min(1.0, cursorAlpha + 0.12);
        } else {
            cursorAlpha = Math.max(0.0, cursorAlpha - 0.06);
        }
        cursorX += (laneX[activeLane] - cursorX) * 0.2;

        // 谱面生成
        if (!noMusicFallback && beatmapNotes != null) {
            while (nextBeatmapIdx < beatmapNotes.size()) {
                BeatmapNote bn = beatmapNotes.get(nextBeatmapIdx);
                if (musicTime >= bn.time - travelSeconds) {
                    notes.add(new Note(bn.gestureType, bn.lane, -60));
                    nextBeatmapIdx++;
                } else break;
            }
            if (nextBeatmapIdx >= beatmapNotes.size() && notes.isEmpty()) { gameOver(); return; }
        } else {
            while (scheduleIdx < scheduledFrames.size() && scheduledFrames.get(scheduleIdx) == frameCount) {
                scheduleIdx++;
                int lane = RAND.nextInt(laneCount);
                GestureType[] types = {GestureType.FIST, GestureType.OPEN, GestureType.PEACE};
                notes.add(new Note(types[RAND.nextInt(3)], lane, -60));
            }
            if (frameCount >= gameDurationFrames) { gameOver(); return; }
        }

        // 音符移动
        for (Note note : notes) note.y += noteSpeed;

        // 判定
        for (Note note : notes) {
            if (note.judged) continue;
            double dist = note.y - judgeLineY;
            if (dist > greatWindow) {
                note.judged = true; combo = 0; missCount++;
                floatTexts.add(new FloatText("MISS", laneX[note.lane], judgeLineY - 30, 30, Color.RED));
                laneHits.add(new LaneHit(note.lane, Color.RED));
            } else if (Math.abs(dist) <= perfectWindow &&
                       gesture.getGesture() == note.gestureType && gesture.getGesture() != GestureType.NONE) {
                note.judged = true;
                score += (int)(100 * getComboMultiplier()); combo++; perfectCount++;
                floatTexts.add(new FloatText("PERFECT", laneX[note.lane], judgeLineY - 50, 30, Color.GOLD));
                laneHits.add(new LaneHit(note.lane, Color.GOLD));
            } else if (Math.abs(dist) <= greatWindow &&
                       gesture.getGesture() == note.gestureType && gesture.getGesture() != GestureType.NONE) {
                note.judged = true;
                score += (int)(50 * getComboMultiplier()); combo++; greatCount++;
                floatTexts.add(new FloatText("GREAT", laneX[note.lane], judgeLineY - 50, 30, Color.LIME));
                laneHits.add(new LaneHit(note.lane, Color.LIME));
            }
        }

        if (combo > maxCombo) maxCombo = combo;
        notes.removeIf(n -> n.y > canvasHeight + 80);
        for (FloatText ft : floatTexts) { ft.y -= 1.5; ft.life--; }
        floatTexts.removeIf(ft -> ft.life <= 0);
        for (LaneHit lh : laneHits) { lh.life--; }
        laneHits.removeIf(lh -> lh.life <= 0);
    }

    private int renderCallCount = 0;

    @Override
    public void render(GraphicsContext gc) {
        if (renderCallCount < 3) {
            System.out.println("[RhythmMaster] render() called, state=" + state + " songs=" + (songs != null ? songs.size() : 0));
            renderCallCount++;
        }
        gc.setFill(Color.web("#05051a"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        if (stars != null) {
            for (double[] s : stars) {
                gc.setFill(Color.rgb(180, 200, 255, s[2] * 0.5));
                gc.fillOval(s[0], s[1], 1.2, 1.2);
            }
        }

        if (state == State.SONG_SELECT) { renderSongSelect(gc); return; }
        if (state == State.BPM_READY) { renderBpmReady(gc); return; }
        if (state == State.BPM_CALIBRATE) { renderBpmCalibrate(gc); return; }
        if (state == State.GAME_OVER) { renderGame(gc); renderGameOver(gc); return; }
        renderGame(gc);
    }

    // ========== 选歌界面 ==========
    private void renderSongSelect(GraphicsContext gc) {
        // 背景星星闪烁
        frameCount++;
        double twinkle = Math.sin(frameCount * 0.02) * 0.3 + 0.7;
        for (double[] s : stars) {
            gc.setFill(Color.rgb(180, 200, 255, s[2] * 0.4 * twinkle));
            gc.fillOval(s[0], s[1], 1.2, 1.2);
        }

        // 标题
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7c3aed")), new Stop(1, Color.web("#06b6d4"))));
        gc.setFont(Font.font("Microsoft YaHei UI", 30));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🎵  选择歌曲", canvasWidth / 2.0, 70);

        String diffName = difficulty == Difficulty.EASY ? "简单" : difficulty == Difficulty.NORMAL ? "普通" : "困难";
        gc.setFill(Color.rgb(180, 180, 200, 0.5));
        gc.setFont(Font.font("Microsoft YaHei UI", 13));
        gc.fillText("难度: " + diffName + "  |  轨道: " + laneCount + "列  |  BPM: " + defaultBpm,
                canvasWidth / 2.0, 94);

        // 歌曲列表（即使为空也显示 "+" 按钮）
        double cardW = canvasWidth * 0.6, cardX = (canvasWidth - cardW) / 2;
        double cardH = 72, gap = 8;
        int maxVis = 5;
        int totalItems = songs.size() + 1; // 多一个 "+"
        int offset = Math.max(0, Math.min(selectedSongIdx - maxVis / 2, totalItems - maxVis));

        for (int i = offset; i < Math.min(totalItems, offset + maxVis); i++) {
            int ri = i - offset;
            double cy = 118 + ri * (cardH + gap);

            if (i < songs.size()) {
                // === 正常歌曲卡片 ===
                SongInfo s = songs.get(i);
                boolean sel = (i == selectedSongIdx);
                renderCard(gc, cardX, cy, cardW, cardH, sel, s.title,
                        s.file.getName().substring(s.file.getName().lastIndexOf('.') + 1).toUpperCase()
                        + "    " + formatSize(s.file.length() / 1024) + "    约 " + (int)s.duration + " 秒",
                        String.format("%02d", i + 1));
            } else {
                // === "+" 添加歌曲卡片 ===
                boolean sel = (i == selectedSongIdx);
                renderCard(gc, cardX, cy, cardW, cardH, sel, "➕  添加本地歌曲",
                        "从电脑中选取音乐文件（mp3/wav/flac...）", "＋");
            }
        }

        // 底部操作提示
        double hintY = canvasHeight - 30;
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(0, hintY - 15, canvasWidth, 55);
        gc.setFill(Color.rgb(255, 255, 255, 0.55));
        gc.setFont(Font.font("Microsoft YaHei UI", 15));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("👆 上下移动手选择歌曲     ✊ 握拳1.2s确认     ✌ 返回难度选择",
                canvasWidth / 2.0, hintY + 10);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** 画一张选歌卡片 */
    private void renderCard(GraphicsContext gc, double cx, double cy, double cw, double ch,
                            boolean sel, String title, String subtitle, String badge) {
        if (sel) {
            gc.setFill(Color.rgb(100, 40, 200, 0.12));
            gc.fillRoundRect(cx - 8, cy - 8, cw + 16, ch + 16, 18, 18);
            gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(80, 40, 180, 0.7)), new Stop(1, Color.rgb(60, 20, 160, 0.7))));
            gc.setStroke(Color.web("#a78bfa").deriveColor(0, 1, 1, 0.8));
            gc.setLineWidth(2.5);
        } else {
            gc.setFill(Color.rgb(20, 20, 40, 0.55));
            gc.setStroke(Color.rgb(255, 255, 255, 0.08));
            gc.setLineWidth(1);
        }
        gc.fillRoundRect(cx, cy, cw, ch, 14, 14);
        gc.strokeRoundRect(cx, cy, cw, ch, 14, 14);

        gc.setFill(sel ? Color.web("#c4b5fd") : Color.rgb(100, 100, 130));
        gc.setFont(Font.font("Microsoft YaHei UI", sel ? 15 : 13));
        gc.fillText(badge, cx + 20, cy + 32);

        gc.setFill(sel ? Color.WHITE : Color.rgb(210, 210, 230));
        gc.setFont(Font.font("Microsoft YaHei UI", sel ? 19 : 16));
        gc.fillText(title, cx + 52, cy + 32);

        gc.setFill(Color.rgb(160, 160, 190, sel ? 0.75 : 0.5));
        gc.setFont(Font.font("Microsoft YaHei UI", 12));
        gc.fillText(subtitle, cx + 52, cy + 52);

        if (sel) {
            gc.setFill(Color.web("#a78bfa"));
            gc.setFont(Font.font(20));
            gc.fillText("▶", cx + cw - 36, cy + 34);

            if (songHoldFrames > 0) {
                double progress = Math.min(1.0, songHoldFrames / (double) SONG_HOLD_REQUIRED);
                double pr = 20, px = cx + cw - 55, py = cy + ch / 2;
                gc.setStroke(Color.rgb(255, 255, 255, 0.2));
                gc.setLineWidth(4);
                gc.strokeOval(px - pr, py - pr, pr * 2, pr * 2);
                gc.setStroke(Color.web("#a78bfa"));
                gc.setLineWidth(4);
                gc.strokeArc(px - pr, py - pr, pr * 2, pr * 2, -90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(10));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText((int) (progress * 100) + "%", px, py + 4);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }
    }

    // ========== BPM 说明页 ==========
    private void renderBpmReady(GraphicsContext gc) {
        gc.setFill(Color.web("#0a0a1e"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        int remainSecs = bpmReadyFrames / 60 + 1;

        // 标题
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7c3aed")), new Stop(1, Color.web("#f59e0b"))));
        gc.setFont(Font.font("Microsoft YaHei UI", 32));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🎯 跟着音乐打节拍", canvasWidth / 2.0, 140);

        // 核心说明
        gc.setFill(Color.rgb(220, 220, 240));
        gc.setFont(Font.font("Microsoft YaHei UI", 20));
        gc.fillText("音乐已开始播放", canvasWidth / 2.0, 220);
        gc.fillText("听到节奏就 ✊ 握拳 一下", canvasWidth / 2.0, 260);

        // 大图标
        gc.setFont(Font.font(64));
        gc.fillText("✊     ✊     ✊", canvasWidth / 2.0, 350);

        // 打几次
        gc.setFill(Color.rgb(255, 200, 120));
        gc.setFont(Font.font("Microsoft YaHei UI", 22));
        gc.fillText("打 " + TAP_REQUIRED + " 次就够了", canvasWidth / 2.0, 420);

        // 倒计时
        gc.setFill(Color.rgb(255, 255, 255, 0.4));
        gc.setFont(Font.font("Microsoft YaHei UI", 36));
        gc.fillText(String.valueOf(remainSecs), canvasWidth / 2.0, canvasHeight - 80);
        gc.setFont(Font.font("Microsoft YaHei UI", 14));
        gc.fillText("秒后开始", canvasWidth / 2.0, canvasHeight - 50);

        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ========== BPM 校准画面 ==========
    private void renderBpmCalibrate(GraphicsContext gc) {
        gc.setFill(Color.web("#0a0a1e"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 星星闪烁
        double twinkle = Math.sin(frameCount * 0.03) * 0.3 + 0.7;
        if (stars != null) {
            for (double[] s : stars) {
                gc.setFill(Color.rgb(180, 200, 255, s[2] * 0.4 * twinkle));
                gc.fillOval(s[0], s[1], 1.2, 1.2);
            }
        }

        double musicTime = mediaPlayer != null ? mediaPlayer.getCurrentTime().toSeconds() : 0;

        // 标题
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f59e0b")), new Stop(1, Color.web("#ef4444"))));
        gc.setFont(Font.font("Microsoft YaHei UI", 28));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🎯 BPM 校准", canvasWidth / 2.0, 100);

        gc.setFill(Color.rgb(200, 200, 220, 0.6));
        gc.setFont(Font.font("Microsoft YaHei UI", 15));
        gc.fillText("歌曲: " + (selectedSong != null ? selectedSong.title : ""), canvasWidth / 2.0, 130);

        // 脉冲圈
        double beatPhase = (musicTime * 2) % 1.0; // 2Hz 闪烁
        double pulseR = 80 + 20 * Math.sin(musicTime * Math.PI * 4);
        double cx = canvasWidth / 2.0, cy = canvasHeight / 2.0 - 30;

        // 外圈脉冲
        gc.setStroke(Color.rgb(255, 180, 50, 0.3 + 0.3 * beatPhase));
        gc.setLineWidth(3);
        gc.strokeOval(cx - pulseR, cy - pulseR, pulseR * 2, pulseR * 2);

        // 内圈
        double innerR = 50;
        gc.setFill(Color.rgb(255, 150, 30, 0.1));
        gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
        gc.setStroke(Color.web("#f59e0b"));
        gc.setLineWidth(3);
        gc.strokeOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // 打拍计数
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei UI", 48));
        gc.fillText(String.valueOf(tapTimes.size()), cx, cy + 16);

        // 进度条
        double progress = Math.min(1.0, tapTimes.size() / (double) TAP_REQUIRED);
        double barW = 300, barH = 8, barX = cx - barW / 2, barY = cy + 80;
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRoundRect(barX, barY, barW, barH, 4, 4);
        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f59e0b")), new Stop(1, Color.web("#ef4444"))));
        gc.fillRoundRect(barX, barY, barW * progress, barH, 4, 4);

        // 提示文字
        gc.setFill(Color.rgb(255, 255, 255, 0.7));
        gc.setFont(Font.font("Microsoft YaHei UI", 16));
        gc.fillText("跟着音乐节奏  ✊ 握拳打节拍", cx, cy + 120);

        // 打拍时间点可视化
        double dotY = cy + 160;
        gc.setStroke(Color.rgb(255, 255, 255, 0.2));
        gc.setLineWidth(1);
        gc.strokeLine(cx - 150, dotY, cx + 150, dotY);
        for (double tt : tapTimes) {
            double dx = cx - 150 + (tt / Math.max(4, musicTime)) * 300;
            if (dx > cx + 150) dx = cx + 150;
            gc.setFill(Color.web("#f59e0b"));
            gc.fillOval(dx - 5, dotY - 5, 10, 10);
        }

        // 底部提示
        gc.setFill(Color.rgb(255, 255, 255, 0.35));
        gc.setFont(Font.font("Microsoft YaHei UI", 13));
        gc.fillText("需要 " + TAP_REQUIRED + " 次打拍 | 已打 " + tapTimes.size() + " 次", cx, canvasHeight - 50);
        gc.fillText("歌曲已开始播放，听准节奏再打", cx, canvasHeight - 30);

        gc.setTextAlign(TextAlignment.LEFT);
        frameCount++;
    }

    // ========== 游戏画面 ==========
    private void renderGame(GraphicsContext gc) {
        Color[] laneColors = {Color.web("#ff4477"), Color.web("#7c3aed"), Color.web("#06b6d4")};
        String[] labels = laneCount == 2 ? new String[]{"左轨", "右轨"} : new String[]{"左轨", "中轨", "右轨"};

        for (int i = 0; i < laneCount; i++) {
            double x = laneX[i]; Color lc = laneColors[i];
            gc.setStroke(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.TRANSPARENT),
                    new Stop(0.3, lc.deriveColor(0, 1, 1, 0.55)),
                    new Stop(0.85, lc.deriveColor(0, 1, 1, 0.55)),
                    new Stop(1, Color.TRANSPARENT)));
            gc.setLineWidth(2); gc.setLineDashes(12, 8);
            gc.strokeLine(x, 0, x, canvasHeight); gc.setLineDashes(null);

            double za = 0.2;
            for (Note n : notes) {
                if (!n.judged && n.lane == i && Math.abs(n.y - judgeLineY) < greatWindow * 2)
                { za = 0.55; break; }
            }
            if (handDetected && activeLane == i) za = Math.max(za, 0.45);
            gc.setFill(lc.deriveColor(0, 1, 1, za));
            gc.fillRoundRect(x - laneWidth * 0.38, judgeLineY - targetZoneH,
                    laneWidth * 0.76, targetZoneH * 2, 12, 12);

            for (LaneHit lh : laneHits) {
                if (lh.lane == i) {
                    gc.setFill(lh.color.deriveColor(0, 1, 1, lh.life / 12.0 * 0.6));
                    gc.fillRoundRect(x - laneWidth * 0.42, judgeLineY - targetZoneH * 1.2,
                            laneWidth * 0.84, targetZoneH * 2.4, 14, 14);
                }
            }

            gc.setFill(lc.deriveColor(0, 1, 1, 0.55));
            gc.setFont(Font.font(11));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(labels[i], x, judgeLineY + targetZoneH + 16);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        gc.setStroke(Color.rgb(255, 255, 255, 0.25));
        gc.setLineWidth(1.5);
        gc.strokeLine(laneWidth * 0.3, judgeLineY, canvasWidth - laneWidth * 0.3, judgeLineY);

        // 光标
        if (cursorAlpha > 0.01) {
            double cx = cursorX, cy = judgeLineY + targetZoneH + 35, cr = 14;
            gc.setFill(Color.rgb(255, 255, 255, 0.18 * cursorAlpha));
            gc.fillOval(cx - cr - 12, cy - cr - 12, (cr + 12) * 2, (cr + 12) * 2);
            gc.setFill(Color.rgb(255, 255, 255, 0.7 * cursorAlpha));
            gc.fillOval(cx - cr, cy - cr, cr * 2, cr * 2);
            gc.setFill(Color.rgb(100, 200, 255, 0.8 * cursorAlpha));
            gc.fillOval(cx - cr * 0.5, cy - cr * 0.5, cr, cr);
            if (handDetected) {
                gc.setFill(Color.WHITE.deriveColor(0, 1, 1, cursorAlpha));
                gc.setFont(Font.font(16));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(gEmoji(currentGesture), cx, cy - 26);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }

        // 音符
        for (Note note : notes) {
            if (note.judged) continue;
            double x = laneX[note.lane], y = note.y;
            Color nc = gColor(note.gestureType);
            double distToJudge = Math.abs(y - judgeLineY);
            double scale = 1.0;
            double nearby = greatWindow * 2;
            if (distToJudge < nearby) scale = 1.0 + 0.25 * (1.0 - distToJudge / nearby);
            double r = 26 * scale;
            gc.setFill(nc.deriveColor(0, 1, 1, 0.15));
            gc.fillOval(x - r - 8, y - r - 8, (r + 8) * 2, (r + 8) * 2);
            gc.setFill(nc.deriveColor(0, 1, 1, 0.85));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
            gc.setStroke(nc.deriveColor(0, 1, 1, 0.5));
            gc.setLineWidth(2); gc.strokeOval(x - r, y - r, r * 2, r * 2);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(24 * scale));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(gEmoji(note.gestureType), x, y + 8 * scale);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        for (FloatText ft : floatTexts) {
            gc.setFill(ft.color.deriveColor(0, 1, 1, Math.max(0, ft.life / 30.0)));
            gc.setFont(Font.font(22));
            gc.setTextAlign(TextAlignment.CENTER); gc.fillText(ft.text, ft.x, ft.y);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // HUD
        gc.setFill(Color.color(0.04, 0.05, 0.16, 0.80));
        gc.fillRoundRect(22, 18, 265, 70, 18, 18);
        gc.setStroke(Color.color(0.58, 0.44, 1.0, 0.40));
        gc.setLineWidth(1.2); gc.strokeRoundRect(22, 18, 265, 70, 18, 18);
        gc.setFill(Color.web("#ddd6fe"));
        gc.setFont(Font.font("Microsoft YaHei UI", 19));
        gc.fillText("🥁  节奏大师", 40, 46);
        gc.setFill(Color.web("#a5b4fc"));
        gc.setFont(Font.font("Microsoft YaHei UI", 13));
        gc.fillText((selectedSong != null ? selectedSong.title : "") + "  得分 " + score + "  连击 " + combo, 40, 72);

        gc.setFill(Color.GOLD); gc.setFont(Font.font(14));
        gc.fillText("P:" + perfectCount, canvasWidth - 150, 36);
        gc.setFill(Color.LIME); gc.fillText("G:" + greatCount, canvasWidth - 105, 36);
        gc.setFill(Color.RED); gc.fillText("M:" + missCount, canvasWidth - 60, 36);

        if (combo >= 10) {
            gc.setFill(Color.GOLD.deriveColor(0, 1, 1, 0.7));
            gc.setFont(Font.font(18));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("🔥 " + combo + " combo  x" + String.format("%.1f", getComboMultiplier()),
                    canvasWidth / 2.0, judgeLineY - targetZoneH - 50);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.4));
        gc.setFont(Font.font(14));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(handDetected ? "手势: " + gEmoji(currentGesture) + " | 轨道: " + (activeLane + 1) : "未检测到手",
                canvasWidth - 28, canvasHeight - 20);
        gc.setTextAlign(TextAlignment.LEFT);

        // 进度条
        double progress = 0;
        if (musicStarted && mediaPlayer != null) {
            progress = Math.min(1.0, mediaPlayer.getCurrentTime().toSeconds() / selectedSong.duration);
        } else if (noMusicFallback) {
            progress = (double) frameCount / gameDurationFrames;
        }
        gc.setFill(progress < 0.5 ? Color.web("#7c3aed") : progress < 0.8 ? Color.web("#f59e0b") : Color.web("#ef4444"));
        gc.fillRect(0, 0, canvasWidth * progress, 3);

        int remSecs = 0;
        if (musicStarted && mediaPlayer != null) {
            remSecs = Math.max(0, (int)(selectedSong.duration - mediaPlayer.getCurrentTime().toSeconds()));
        }
        gc.setFill(Color.rgb(200, 180, 255));
        gc.setFont(Font.font(14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("⏱ " + remSecs + "s", canvasWidth / 2.0, 24);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderGameOver(GraphicsContext gc) {
        gc.setFill(Color.rgb(0, 0, 0, 0.7));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        gc.setFill(Color.GOLD);
        gc.setFont(Font.font("Microsoft YaHei", 36));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🎵 演奏结束！", canvasWidth / 2.0, canvasHeight / 2.0 - 80);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", 24));
        gc.fillText("总分: " + score, canvasWidth / 2.0, canvasHeight / 2.0 - 20);
        gc.fillText("最大连击: " + maxCombo, canvasWidth / 2.0, canvasHeight / 2.0 + 15);
        gc.fillText("P:" + perfectCount + "  G:" + greatCount + "  M:" + missCount,
                canvasWidth / 2.0, canvasHeight / 2.0 + 50);
        if (performanceComment != null) {
            gc.setFill(Color.web("#fbbf24"));
            gc.setFont(Font.font("Microsoft YaHei", 18));
            gc.fillText(performanceComment, canvasWidth / 2.0, canvasHeight / 2.0 + 90);
        }
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", 16));
        gc.fillText("✊握拳 重新开始 | ✋张开 返回大厅", canvasWidth / 2.0, canvasHeight / 2.0 + 130);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    @Override public boolean isOver() { return state == State.GAME_OVER; }
    @Override public int getScore() { return score; }

    /** GameRenderer 用来判断选歌阶段是否完成 */
    public boolean isSongConfirmed() { return state == State.PLAYING || state == State.GAME_OVER; }

    /** 鼠标点击直接确认选歌（不需1.2s握拳） */
    public void clickConfirmSong() {
        if (state != State.SONG_SELECT) return;
        if (selectedSongIdx == songs.size()) {
            openFileChooser();
        } else {
            confirmSong();
        }
    }

    /** GameRenderer 用来判断是否在BPM校准中 */
    public boolean isCalibrating() { return state == State.BPM_CALIBRATE; }

    /** 鼠标点击打拍 */
    public void tapFromMouse() {
        if (state != State.BPM_CALIBRATE || mediaPlayer == null) return;
        double t = mediaPlayer.getCurrentTime().toSeconds();
        tapTimes.add(t);
        tapCountdown = TAP_REQUIRED - tapTimes.size();
        if (tapTimes.size() >= TAP_REQUIRED) {
            finishCalibration();
        }
    }

    /** 打开系统文件选择器，添加本地音乐 */
    private void openFileChooser() {
        Platform.runLater(() -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择音乐文件");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("音频文件", "*.mp3", "*.wav", "*.flac", "*.ogg", "*.m4a", "*.aac",
                    "*.MP3", "*.WAV", "*.FLAC", "*.OGG", "*.M4A", "*.AAC"));
            // 默认打开 music 目录
            File musicDir = new File("music");
            if (musicDir.exists() && musicDir.isDirectory()) {
                chooser.setInitialDirectory(musicDir);
            }
            Window window = javafx.stage.Window.getWindows().stream()
                    .filter(Window::isShowing).findFirst().orElse(null);
            File selected = chooser.showOpenDialog(window);
            if (selected != null) {
                String name = selected.getName();
                String title = name.substring(0, name.lastIndexOf('.'));
                double dur = estimateDuration(selected);
                songs.add(new SongInfo(title, selected, dur));
                songs.sort(Comparator.comparing(s -> s.title));
                System.out.println("[RhythmMaster] Added: " + name);
            }
        });
    }

    @Override
    public void reset() {
        if (mediaPlayer != null) { try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {} }
        init(canvasWidth, canvasHeight);
    }

    // ===== 工具方法 =====
    private String generatePerformanceComment() {
        int total = perfectCount + greatCount + missCount;
        double acc = total > 0 ? (perfectCount * 1.0 + greatCount * 0.5) / total : 0;
        if (acc >= 0.9 && maxCombo >= 50) return "🌟 银河级演奏！你是星际最强的节奏大师！";
        if (acc >= 0.7 && maxCombo >= 20) return "🚀 不错的表演，宇宙已经听到了你的节拍！";
        if (acc >= 0.4) return "🌍 还行，再多练练就能飞出银河系了...";
        return "💫 再接再厉，星辰大海等你征服！";
    }

    private double getComboMultiplier() {
        if (combo >= 100) return 3.0;
        if (combo >= 50) return 2.0;
        if (combo >= 30) return 1.5;
        if (combo >= 10) return 1.2;
        return 1.0;
    }

    private Color gColor(GestureType g) {
        if (g == GestureType.FIST) return Color.web("#ff4477");
        if (g == GestureType.OPEN) return Color.web("#7c3aed");
        return Color.web("#06b6d4");
    }

    private String gEmoji(GestureType g) {
        if (g == GestureType.FIST) return "✊";
        if (g == GestureType.OPEN) return "✋";
        if (g == GestureType.PEACE) return "✌";
        return "👆";
    }

    private String formatSize(long kb) {
        if (kb >= 1024) return String.format("%.1f MB", kb / 1024.0);
        return kb + " KB";
    }

    // ===== 内部类 =====
    private static class SongInfo {
        String title; File file; double duration;
        SongInfo(String t, File f, double d) { title = t; file = f; duration = d; }
    }

    private static class BeatmapNote {
        double time; int lane; GestureType gestureType;
        BeatmapNote(double t, int l, GestureType g) { time = t; lane = l; gestureType = g; }
    }

    private static class Note {
        GestureType gestureType; int lane; double y; boolean judged;
        Note(GestureType g, int l, double y) { gestureType = g; lane = l; y = y; }
    }

    private static class FloatText {
        String text; double x, y; int life; Color color;
        FloatText(String t, double x, double y, int l, Color c) { text=t; x=x; y=y; life=l; color=c; }
    }

    private static class LaneHit {
        int lane, life = 12; Color color;
        LaneHit(int l, Color c) { lane = l; color = c; }
    }
}
