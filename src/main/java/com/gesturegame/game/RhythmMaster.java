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

    // ===== VFX =====
    private List<Particle> particles;
    private List<Shockwave> shockwaves;
    private List<MissGlitch> misses;

    // 宇宙深潭同心涟漪 (Cosmic Pond Ripples) -> 升级为液态玻璃波纹
    private List<BgRipple> bgRipples;

    // 量子玻璃碎块 (Quantum Glass Shards)
    private List<GlassShard> glassShards;

    // ===== 游戏数据 =====
    private int canvasWidth, canvasHeight;
    private int score, combo, maxCombo;
    private int perfectCount, greatCount, missCount;
    private int frameCount;
    private GestureType currentGesture = GestureType.NONE;
    private boolean handDetected;
    private String performanceComment;

    // 背景环境粒子
    private List<Particle> ambientParticles;
    
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
    @Override
    public String getName() {
        return "节奏大师";
    }
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
        this.particles = new ArrayList<>();
        this.ambientParticles = new ArrayList<>();
        this.shockwaves = new ArrayList<>();
        this.misses = new ArrayList<>();
        this.bgRipples = new ArrayList<>();
        this.glassShards = new ArrayList<>();
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
                laneCount = 1; noteSpeed = 3.0;
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

    /** 获取音频时长：用MediaPlayer尝试读取 */
    private double estimateDuration(File f) {
        try {
            Media probe = new Media(f.toURI().toString());
            // Media 的 duration 在播放前可能是 UNKNOWN，但可以尝试
            javafx.scene.media.MediaPlayer probePlayer = new javafx.scene.media.MediaPlayer(probe);
            // 等一小段时间让 Media 解析元数据
            final double[] dur = {90.0};
            probePlayer.setOnReady(() -> {
                double d = probe.getDuration().toSeconds();
                if (d > 0 && d < 3600) dur[0] = d;
            });
            // 简单估算：文件大小 / 典型比特率
            long size = f.length();
            double estBySize = size / 16000.0; // ~128kbps mp3
            if (estBySize > 0 && estBySize < 3600) dur[0] = estBySize;
            probePlayer.dispose();
            return dur[0];
        } catch (Exception e) {
            return 90.0;
        }
    }

    /** 自动生成谱面 */
    private void generateBeatmapFromSong() {
        if (selectedSong == null) return;
        beatmapNotes = new ArrayList<>();

        double duration = selectedSong.duration;
        double beatInterval = 60.0 / defaultBpm;  // 每拍秒数
        GestureType[] gestures = {GestureType.FIST, GestureType.OPEN, GestureType.PEACE};

        // 在节拍上生成音符，单手操作保证不重叠
        double minGap = Math.max(0.6, beatInterval * 0.8); // 最小间隔0.45秒
        double t = beatInterval;
        while (t < duration - travelSeconds - 1) {
            int lane = RAND.nextInt(laneCount);
            GestureType gt = gestures[RAND.nextInt(3)];
            // ~25%概率生成长条
            double hold = 0;
            if (RAND.nextDouble() < 0.25) {
                hold = 0.8 + RAND.nextDouble() * 1.2; // 0.8~2.0秒
            }
            beatmapNotes.add(new BeatmapNote(t, lane, gt, hold));

            double r = RAND.nextDouble();
            double gap;
            if (r < 0.3) gap = beatInterval;
            else if (r < 0.7) gap = beatInterval * 1.5;
            else gap = beatInterval * 2.0;
            t += Math.max(minGap, gap);
            // 长条占满其时长的间隔，不会同时出现两条
            t += hold;
        }
        System.out.println("[RhythmMaster] Generated " + beatmapNotes.size() + " notes for " + (int)duration + "s song");

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

        // 跳过校准期间已过的音符，避免开始瞬间堆积多条
        double now = mediaPlayer != null ? mediaPlayer.getCurrentTime().toSeconds() : 0;
        while (nextBeatmapIdx < beatmapNotes.size()
                && beatmapNotes.get(nextBeatmapIdx).time < now + travelSeconds) {
            nextBeatmapIdx++;
        }

        frameCount = 0;
        state = State.PLAYING;
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
            // 握拳1.2秒确认选歌（握拳后锁定，松拳才可换歌）
            boolean isFist = (gesture.getGesture() == GestureType.FIST && handDetected);
            if (isFist) {
                songHoldFrames++;
            } else {
                songHoldFrames = 0;
            }

            if (songHoldFrames > 0) {
                // 握拳中：锁定选中项，手移动不变
            } else if (handDetected && songs != null) {
                double relY = gesture.getHandY();
                int maxIdx = songs.size();
                selectedSongIdx = Math.max(0, Math.min(maxIdx, (int)(relY * (maxIdx + 1))));
            }

            if (songHoldFrames >= SONG_HOLD_REQUIRED) {
                songHoldFrames = 0;
                if (selectedSongIdx == songs.size()) {
                    openFileChooser();
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

        // 谱面生成：同时只允许一个音符，等判定完再出下一个
        if (!noMusicFallback && beatmapNotes != null) {
            boolean hasActive = notes.stream().anyMatch(n -> !n.judged);
            if (!hasActive) {
                while (nextBeatmapIdx < beatmapNotes.size()) {
                    BeatmapNote bn = beatmapNotes.get(nextBeatmapIdx);
                    if (musicTime >= bn.time - travelSeconds) {
                        double holdPx = bn.holdDuration > 0 ? bn.holdDuration * noteSpeed * 60 : 0;
                        double startY = holdPx > 0 ? -60 - holdPx : -60;
                        notes.add(new Note(bn.gestureType, bn.lane, startY, holdPx));
                        nextBeatmapIdx++;
                        break;
                    } else break;
                }
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
            boolean match = gesture.getGesture() == note.gestureType && gesture.getGesture() != GestureType.NONE;

            if (note.holdLength > 0) {
                // 长条：头部到判定线时开始，必须持续保持手势直到尾部过线
                double tailDist = (note.y + note.holdLength) - judgeLineY;
                double headDist = dist;

                // 头部进判定区：开始追踪
                if (!note.holdStarted && Math.abs(headDist) <= greatWindow && match) {
                    note.holdStarted = true;
                    note.holdBroken = false;
                }
                // 头部错过
                if (!note.holdStarted && headDist > greatWindow) {
                    note.judged = true; combo = 0; missCount++;
                    createMissGlitch(laneX[note.lane], judgeLineY);
                    floatTexts.add(new FloatText("MISS", laneX[note.lane], judgeLineY - 30, 30, Color.RED));
                    laneHits.add(new LaneHit(note.lane, Color.RED));
                }
                // 保持中但手势松开了
                if (note.holdStarted && !note.judged && headDist > 0 && !match) {
                    note.holdBroken = true;
                }
                // 尾部过线：判定结果
                if (note.holdStarted && tailDist > greatWindow) {
                    note.judged = true;
                    if (!note.holdBroken) {
                        score += (int)(200 * getComboMultiplier()); combo++; perfectCount++;
                        createPerfectBlast(laneX[note.lane], judgeLineY);
                        floatTexts.add(new FloatText("PERFECT +200", laneX[note.lane], judgeLineY - 50, 40, Color.CYAN));
                        if (combo > 0) floatTexts.add(new FloatText("COMBO +" + combo, laneX[note.lane], judgeLineY - 80, 40, Color.GOLD));
                        laneHits.add(new LaneHit(note.lane, Color.CYAN));
                    } else {
                        combo = 0; missCount++;
                        createMissGlitch(laneX[note.lane], judgeLineY);
                        floatTexts.add(new FloatText("MISS", laneX[note.lane], judgeLineY - 30, 30, Color.RED));
                        laneHits.add(new LaneHit(note.lane, Color.RED));
                    }
                }
            } else {
                if (dist > greatWindow) {
                    note.judged = true; combo = 0; missCount++;
                    createMissGlitch(laneX[note.lane], judgeLineY);
                    floatTexts.add(new FloatText("MISS", laneX[note.lane], judgeLineY - 30, 30, Color.RED));
                    laneHits.add(new LaneHit(note.lane, Color.RED));
                } else if (Math.abs(dist) <= perfectWindow && match) {
                    note.judged = true;
                    score += (int)(100 * getComboMultiplier()); combo++; perfectCount++;
                    createPerfectBlast(laneX[note.lane], judgeLineY);
                    floatTexts.add(new FloatText("PERFECT +1000", laneX[note.lane], judgeLineY - 50, 40, Color.CYAN));
                    if (combo > 0) floatTexts.add(new FloatText("COMBO +" + combo, laneX[note.lane], judgeLineY - 80, 40, Color.GOLD));
                    laneHits.add(new LaneHit(note.lane, Color.CYAN));
                } else if (Math.abs(dist) <= greatWindow && match) {
                    note.judged = true;
                    score += (int)(50 * getComboMultiplier()); combo++; greatCount++;
                    createGreatBlast(laneX[note.lane], judgeLineY);
                    floatTexts.add(new FloatText("GREAT +500", laneX[note.lane], judgeLineY - 50, 30, Color.LIME));
                    laneHits.add(new LaneHit(note.lane, Color.LIME));
                }
            }
        }

        if (combo > maxCombo) maxCombo = combo;
        notes.removeIf(n -> n.y > canvasHeight + 80);
        for (FloatText ft : floatTexts) { ft.y -= 1.5; ft.life--; }
        floatTexts.removeIf(ft -> ft.life <= 0);
        for (LaneHit lh : laneHits) { lh.life--; }
        laneHits.removeIf(lh -> lh.life <= 0);

        // 更新特效
        for (Particle p : particles) {
            p.x += p.vx; p.y += p.vy;
            p.life--;
        }
        particles.removeIf(p -> p.life <= 0);

        for (Particle p : ambientParticles) {
            p.x += p.vx; p.y += p.vy;
            p.life--;
        }
        ambientParticles.removeIf(p -> p.life <= 0);

        // 随机生成背景深空粒子 (Gravitational Particle Vortex & Cosmic light streaks)
        if (RAND.nextDouble() < 0.4) {
            boolean isStreak = RAND.nextDouble() < 0.15;
            // Arcaea-inspired Vortex: Particles flow inward and downward towards the center-bottom
            double ax = RAND.nextDouble() * canvasWidth;
            double ay = RAND.nextDouble() * canvasHeight * 0.8; 
            
            // Calculate velocity vector towards a gravitational center (bottom center of the screen)
            double targetX = canvasWidth / 2.0;
            double targetY = canvasHeight + 200; // Far below the screen
            double dx = targetX - ax;
            double dy = targetY - ay;
            double dist = Math.sqrt(dx * dx + dy * dy);
            
            double baseSpeed = isStreak ? 20 + RAND.nextDouble() * 15 : 2 + RAND.nextDouble() * 4;
            double avx = (dx / dist) * baseSpeed + (RAND.nextDouble() - 0.5) * (isStreak ? 2 : 4);
            double avy = (dy / dist) * baseSpeed + (RAND.nextDouble() - 0.5) * (isStreak ? 2 : 4);
            
            double alife = isStreak ? 25 : 80 + RAND.nextInt(60);
            double asize = isStreak ? 2.5 : 1.5 + RAND.nextDouble() * 4;
            Color ac = isStreak ? Color.CYAN : (RAND.nextBoolean() ? Color.web("#8b5cf6") : Color.web("#3b82f6"));
            ambientParticles.add(new Particle(ax, ay, avx, avy, alife, asize, ac));
        }

        // 随机生成判定区反重力粒子 (Rising Energy Particles)
        if (RAND.nextDouble() < 0.6) {
            double rx = laneX[0] - laneWidth / 2.0 + RAND.nextDouble() * (laneWidth * laneCount);
            double ry = judgeLineY + targetZoneH + RAND.nextDouble() * 30;
            ambientParticles.add(new Particle(rx, ry, (RAND.nextDouble() - 0.5) * 2.5, -3 - RAND.nextDouble() * 6, 40 + RAND.nextInt(20), 2.5 + RAND.nextDouble() * 4, Color.MAGENTA));
        }

        for (Shockwave s : shockwaves) {
            s.radius += (s.maxRadius - s.radius) * 0.2;
            s.life--;
        }
        shockwaves.removeIf(s -> s.life <= 0);

        for (MissGlitch m : misses) {
            m.life--;
        }
        misses.removeIf(m -> m.life <= 0);

        // 更新玻璃碎片
        for (GlassShard gs : glassShards) {
            gs.x += gs.vx; gs.y += gs.vy;
            gs.vy += 0.5; // 重力
            gs.angle += gs.rotSpeed;
            gs.life--;
        }
        glassShards.removeIf(gs -> gs.life <= 0);

        // 宇宙深潭：生成同心波纹涟漪 (Cosmic Pond Ripples)
        if (frameCount % 60 == 0) {
            bgRipples.add(new BgRipple(canvasWidth / 2.0, canvasHeight / 2.0, canvasWidth * 0.8, 300));
        }
        for (BgRipple r : bgRipples) {
            r.radius += (r.maxRadius - r.radius) * 0.006;
            r.life--;
        }
        bgRipples.removeIf(r -> r.life <= 0);
    }

    private int renderCallCount = 0;

    @Override
    public void render(GraphicsContext gc) {
        if (renderCallCount < 3) {
            System.out.println("[RhythmMaster] render() called, state=" + state + " songs=" + (songs != null ? songs.size() : 0));
            renderCallCount++;
        }
        
        // Deep space nebula background (DEEP BLUE AND CYBER PURPLE PARTICLE FIELD)
        gc.setFill(Color.web("#030310"));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        
        // Nebula gradients
        gc.setFill(new RadialGradient(0, 0, canvasWidth * 0.3, canvasHeight * 0.2, canvasWidth * 0.8, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(40, 10, 80, 0.6)), new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        
        gc.setFill(new RadialGradient(0, 0, canvasWidth * 0.7, canvasHeight * 0.8, canvasWidth * 0.7, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(10, 30, 100, 0.5)), new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // Draw Cosmic Pond Ripples (宇宙深潭涟漪)
        for (BgRipple r : bgRipples) {
            double prog = 1.0 - (r.life / r.maxLife); // 0.0 to 1.0
            Color rc;
            if (prog < 0.2) rc = Color.rgb(128, 0, 128); // Deep Purple (内环)
            else if (prog < 0.5) rc = Color.rgb(0, 0, 139); // Dark Blue (过渡)
            else if (prog < 0.8) rc = Color.CYAN; // Cyan (外圈)
            else rc = Color.rgb(173, 255, 47); // Green-Yellow/Gold-green (外缘)
            
            gc.setStroke(rc.deriveColor(0, 1, 1, Math.sin(prog * Math.PI) * 0.4));
            gc.setLineWidth(3 + prog * 6);
            gc.strokeOval(r.x - r.radius, r.y - r.radius, r.radius * 2, r.radius * 2);
        }

        // Stars & Dynamic Particles
        if (stars != null) {
            double twinkle = Math.sin(frameCount * 0.05) * 0.3 + 0.7;
            for (double[] s : stars) {
                gc.setFill(Color.rgb(150, 200, 255, s[2] * 0.6 * twinkle));
                gc.fillOval(s[0], s[1], 1.5, 1.5);
            }
        }
        
        // Draw ambient particles (stardust & light streaks)
        for (Particle p : ambientParticles) {
            double alpha = p.life / p.maxLife;
            gc.setFill(p.color.deriveColor(0, 1, 1, alpha));
            if (p.vy > 10) {
                // Light streak
                gc.fillRoundRect(p.x - 1, p.y - p.size * 5, 2, p.size * 10, 1, 1);
            } else {
                // Glow dust
                gc.fillOval(p.x - p.size, p.y - p.size, p.size * 2, p.size * 2);
            }
        }
        
        // Circuit pattern overlay (subtle lines)
        gc.setStroke(Color.rgb(0, 255, 150, 0.05));
        gc.setLineWidth(1);
        for (int i = 0; i < canvasWidth; i += 80) {
            gc.strokeLine(i, 0, i, canvasHeight);
        }
        for (int i = 0; i < canvasHeight; i += 80) {
            gc.strokeLine(0, i, canvasWidth, i);
        }
        
        // Dynamic scan lines
        double scanY = (frameCount * 3) % canvasHeight;
        gc.setFill(new LinearGradient(0, scanY - 50, 0, scanY + 50, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT), new Stop(0.5, Color.rgb(0, 255, 255, 0.1)), new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(0, scanY - 50, canvasWidth, 100);

        // 动态音频声波背景 (Equalizer audio waves)
        double waveTime = frameCount * 0.1;
        double centerX = canvasWidth / 2.0;
        double waveH = 150;
        gc.setLineWidth(2);
        for (int i = 0; i < 40; i++) {
            double wx = centerX - 400 + i * 20;
            // 模拟音乐频谱跳动，如果音乐播放中则跳动更剧烈
            double amp = musicStarted && mediaPlayer != null ? 
                    (Math.sin(waveTime * (1.2 + i * 0.1)) * Math.cos(waveTime * 0.8 + i) * 0.5 + 0.5) :
                    (Math.sin(waveTime + i * 0.2) * 0.3 + 0.3);
            
            double h = 20 + amp * waveH;
            
            gc.setStroke(new LinearGradient(0, canvasHeight - h, 0, canvasHeight, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(0, 255, 255, 0.6)), new Stop(1, Color.TRANSPARENT)));
            gc.strokeLine(wx, canvasHeight - h, wx, canvasHeight);
            
            // 对称的另一侧
            double wx2 = centerX + 400 - i * 20;
            gc.strokeLine(wx2, canvasHeight - h, wx2, canvasHeight);
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
                renderCard(gc, cardX, cy, cardW, cardH, sel,
                        String.format("%02d   %s", i + 1, s.title), "", "");
            } else {
                // === "+" 添加歌曲卡片 ===
                boolean sel = (i == selectedSongIdx);
                renderCard(gc, cardX, cy, cardW, cardH, sel,
                        "＋  添加本地歌曲", "从电脑选取 mp3 / wav / flac 等音频文件", "");
            }
        }

        // 底部操作提示
        double hintY = canvasHeight - 30;
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(0, hintY - 15, canvasWidth, 55);
        gc.setFill(Color.rgb(255, 255, 255, 0.55));
        gc.setFont(Font.font("Microsoft YaHei UI", 15));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("✋ 移动手位置选歌    ✊ 握拳1.2s确认    🖱 点击确认    🤲 双手入镜返回",
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

        double textX = cx + 40;
        double maxTextW = cw - 80; // 留左右边距

        gc.setFill(sel ? Color.WHITE : Color.rgb(210, 210, 230));
        gc.setFont(Font.font("Microsoft YaHei UI", sel ? 19 : 16));
        gc.fillText(clipText(title, maxTextW, gc), textX, cy + 32);

        if (subtitle != null && !subtitle.isEmpty()) {
            gc.setFill(Color.rgb(160, 160, 190, sel ? 0.75 : 0.5));
            gc.setFont(Font.font("Microsoft YaHei UI", 12));
            gc.fillText(clipText(subtitle, maxTextW, gc), textX, cy + 52);
        }

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
        // Window frame with a sci-fi border titled 'AI 手势交互游戏大厅 2026_7_22 18_57_44.png'
        gc.setStroke(Color.rgb(0, 255, 255, 0.6));
        gc.setLineWidth(2);
        gc.strokeRect(10, 10, canvasWidth - 20, canvasHeight - 20);
        gc.setFill(Color.rgb(0, 255, 255, 0.2));
        gc.fillRect(10, 10, canvasWidth - 20, 30);
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Courier New", 16));
        gc.fillText("AI 手势交互游戏大厅 2026_7_22 18_57_44.png", 20, 30);

        // 轨道：pulsing cyan laser track
        double trackW = laneWidth * laneCount;
        double trackX = laneX[0] - laneWidth / 2.0;
        
        // 垂直激光扫描线 / 透光柱 (Vertical holographic laser line)
        if (handDetected) {
            double activeX = laneX[activeLane];
            gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.TRANSPARENT),
                    new Stop(0.5, Color.rgb(0, 255, 255, 0.2)),
                    new Stop(1, Color.rgb(0, 255, 255, 0.5))));
            gc.fillRect(activeX - laneWidth / 2.0, 40, laneWidth, judgeLineY - 40);
            
            // 激光中轴线 (Thin vertical laser beam)
            gc.setStroke(Color.CYAN);
            gc.setLineWidth(2.0);
            gc.setLineDashes(15, 10);
            gc.strokeLine(activeX, 40, activeX, judgeLineY);
            gc.setLineDashes(null);
            
            // 全息目标光标虚影 & 动态缩放光环 (Shrinking Target Ring & Ghost Cursor & Fist Halo)
            double targetY = judgeLineY - targetZoneH - 30;
            double shrinkPhase = (frameCount % 60) / 60.0; // 0.0 to 1.0 every second
            double ringSize = 80 - shrinkPhase * 40; // Shrinks from 80 to 40
            
            // Miniature smooth gradient ripple halos (cyan-purple) around the fist/gesture
            gc.setStroke(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.CYAN), new Stop(1, Color.PURPLE)));
            gc.setLineWidth(4);
            gc.strokeOval(activeX - ringSize/2, targetY - ringSize/2, ringSize, ringSize);
            
            gc.setStroke(Color.rgb(0, 255, 255, 0.5));
            gc.setLineWidth(2);
            gc.strokeOval(activeX - 30, targetY - 30, 60, 60);
            
            gc.setFill(Color.rgb(0, 255, 255, 0.3));
            gc.fillOval(activeX - 30, targetY - 30, 60, 60);
            
            gc.setFill(Color.CYAN);
            gc.setFont(Font.font(28));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(gEmoji(currentGesture), activeX, targetY + 10);
            gc.setTextAlign(TextAlignment.LEFT);
        }
        
        // Track Background
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.8, Color.rgb(0, 255, 255, 0.1)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(trackX, 40, trackW, canvasHeight - 40);
        
        // Lane dividers
        gc.setStroke(Color.rgb(0, 255, 255, 0.3));
        gc.setLineWidth(1);
        for (int i = 0; i <= laneCount; i++) {
            double lx = trackX + i * laneWidth;
            gc.strokeLine(lx, 40, lx, canvasHeight - 20);
            
            // "左中右" 文本与周围涟漪回声 (Left Center Right text with tiny ripple echoes)
            if (i < laneCount) {
                double textX = lx + laneWidth / 2.0;
                double textY = canvasHeight - 35;
                String[] labels = laneCount == 1 ? new String[]{"中"} : (laneCount == 2 ? new String[]{"左", "右"} : new String[]{"左", "中", "右"});
                String label = i < labels.length ? labels[i] : "";
                
                gc.setStroke(Color.rgb(0, 255, 255, Math.sin(frameCount * 0.1) * 0.4 + 0.2));
                gc.setLineWidth(1);
                gc.strokeOval(textX - 15, textY - 15, 30, 30); // Ripple echo
                
                gc.setFill(Color.CYAN);
                gc.setFont(Font.font(16));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(label, textX, textY + 5);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }

        // 底部核对区域（Hit / Check Zone）-> 升级为量子棱镜镜面 (Quantum Prism Mirror)
        // 根据要求：固定渲染，不再使用 pulse 和随机粒子避免一闪一闪
        double checkZoneY = judgeLineY - targetZoneH;
        double checkZoneH = targetZoneH * 2;
        
        // Vortex background & Plasma Glowing Halo (始终渲染，不依赖手势状态)
        // 棱镜折射渐变底色，固定透明度不闪烁
        gc.setFill(new RadialGradient(0, 0, trackX + trackW/2, judgeLineY, trackW * 0.8, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 255, 255, 0.5)),              // 固定 Cyan center
                new Stop(0.3, Color.rgb(180, 0, 255, 0.5)),            // Magenta/Purple
                new Stop(0.8, Color.rgb(0, 0, 100, 0.3)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillRect(trackX - 50, checkZoneY - 50, trackW + 100, checkZoneH + 100);
        
        // 玻璃折射网格 (Refractive Glass Lattice) - 固定渲染
        gc.setLineWidth(2);
        for (double y = checkZoneY; y < checkZoneY + checkZoneH; y += 20) {
            for (double x = trackX; x < trackX + trackW; x += 30) {
                // 绘制带色散的多边形
                gc.setStroke(Color.rgb(255, 0, 150, 0.5));
                gc.strokeLine(x, y, x + 15, y + 15);
                gc.setStroke(Color.rgb(0, 255, 255, 0.8));
                gc.strokeLine(x + 15, y + 15, x + 30, y);
            }
        }
        
        // 移除了随机生成的 Quantum Sparkles，避免雪花屏般的闪烁感
        
        // Reactive neon boundary - 固定线宽
        gc.setStroke(Color.MAGENTA);
        gc.setLineWidth(4);
        gc.strokeRect(trackX, checkZoneY, trackW, checkZoneH);
        
        // 绘制特效在音符下方
        for (Shockwave s : shockwaves) {
            gc.setStroke(s.color.deriveColor(0, 1, 1, s.life / s.maxLife));
            gc.setLineWidth(4);
            gc.strokeOval(s.x - s.radius, s.y - s.radius, s.radius * 2, s.radius * 2);
        }
        for (Particle p : particles) {
            gc.setFill(p.color.deriveColor(0, 1, 1, p.life / p.maxLife));
            gc.fillOval(p.x - p.size, p.y - p.size, p.size * 2, p.size * 2);
        }
        for (MissGlitch m : misses) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            double a = m.life / m.maxLife;
            for (int i = 0; i < 5; i++) {
                double gx = m.x + (RAND.nextDouble() - 0.5) * 60;
                double gy = m.y + (RAND.nextDouble() - 0.5) * 60;
                gc.strokeLine(m.x, m.y, gx, gy);
            }
            gc.setFill(Color.rgb(0, 0, 0, 0.5 * a));
            gc.fillRect(m.x - 40, m.y - 40, 80, 80);
        }

        // 渲染玻璃碎块 (Quantum Glass Shards)
        for (GlassShard gs : glassShards) {
            double a = gs.life / gs.maxLife;
            gc.save();
            gc.translate(gs.x, gs.y);
            gc.rotate(gs.angle);
            // 色散边缘 (Chromatic Aberration)
            gc.setFill(Color.rgb(255, 0, 100, 0.6 * a));
            double[] xPoints = {-gs.size, gs.size, 0};
            double[] yPoints = {-gs.size, -gs.size, gs.size};
            gc.fillPolygon(xPoints, yPoints, 3);
            
            gc.setFill(Color.rgb(0, 255, 255, 0.8 * a));
            double[] xPoints2 = {-gs.size + 1, gs.size - 1, 0};
            double[] yPoints2 = {-gs.size + 1, -gs.size + 1, gs.size - 1};
            gc.fillPolygon(xPoints2, yPoints2, 3);
            gc.restore();
        }

        // 光标与悬停指示 (不再覆盖或清除之前的渲染内容)
        if (cursorAlpha > 0.01) {
            double cx = cursorX, cy = judgeLineY + targetZoneH + 35, cr = 14;
            gc.setFill(Color.rgb(255, 255, 255, 0.18 * cursorAlpha));
            gc.fillOval(cx - cr - 12, cy - cr - 12, (cr + 12) * 2, (cr + 12) * 2);
            gc.setFill(Color.rgb(255, 255, 255, 0.7 * cursorAlpha));
            gc.fillOval(cx - cr, cy - cr, cr * 2, cr * 2);
            gc.setFill(Color.rgb(0, 255, 255, 0.8 * cursorAlpha));
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

            if (note.holdLength > 0) {
                // 长条 (Molten Neon Glass Trail / 熔融液态玻璃拖尾)
                double hl = note.holdLength;
                double vw = r * 0.55;
                if (note.holdStarted && !note.holdBroken) {
                    double p = 1.0 + 0.22 * Math.sin(frameCount * 0.22);
                    // 色散边缘光晕
                    gc.setFill(Color.rgb(255, 0, 150, 0.2 * p));
                    gc.fillRoundRect(x - vw - 25, y - 6, vw * 2 + 50, hl + 12, vw + 22, vw + 22);
                    gc.setFill(nc.deriveColor(0, 1, 1, 0.3 * p));
                    gc.fillRoundRect(x - vw - 20, y - 6, vw * 2 + 40, hl + 12, vw + 22, vw + 22);
                    floatTexts.add(new FloatText("✦", x + vw + 2 + RAND.nextDouble() * 14,
                            note.y + RAND.nextDouble() * hl, 10, nc.deriveColor(0, 1, 1, 0.95)));
                    floatTexts.add(new FloatText("✦", x - vw - 2 - RAND.nextDouble() * 14,
                            note.y + RAND.nextDouble() * hl, 10, nc.deriveColor(0, 1, 1, 0.95)));
                }
                
                // 晶体化高光层 (Crystalline Highlight Layer)
                gc.setFill(new LinearGradient(x - vw, 0, x + vw, 0, false, CycleMethod.NO_CYCLE,
                        new Stop(0, nc.deriveColor(0, 1, 1, 0.8)),
                        new Stop(0.5, Color.WHITE.deriveColor(0, 1, 1, 0.9)),
                        new Stop(1, nc.deriveColor(0, 1, 1, 0.8))));
                gc.fillRoundRect(x - vw, y, vw * 2, hl, vw, vw);
                
                // 内部折射阴影
                gc.setFill(Color.rgb(0, 0, 0, 0.3));
                gc.fillRoundRect(x - vw + 4, y + 4, vw * 2 - 8, hl - 8, vw - 4, vw - 4);
                
                gc.setStroke(nc.deriveColor(0, 1, 1, note.holdStarted ? 1.0 : 0.8));
                gc.setLineWidth(3.0);
                gc.strokeRoundRect(x - vw, y, vw * 2, hl, vw, vw);
                
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(20));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(gEmoji(note.gestureType), x, y + hl + 16);
                gc.setTextAlign(TextAlignment.LEFT);
            } else {
                // 普通音符: Stylized crystalline hand-sign notes
                double haloPhase = frameCount * 0.05;
                gc.setStroke(Color.rgb(255, 0, 150, 0.4)); // Chromatic red shift
                gc.setLineWidth(2);
                gc.strokeOval(x - r * 1.5 - 2, y - r * 1.5, r * 3, r * 3);
                
                gc.setStroke(Color.rgb(0, 255, 255, 0.4)); // Chromatic blue shift
                gc.strokeOval(x - r * 1.5 + 2, y - r * 1.5, r * 3, r * 3);
                
                gc.setStroke(nc.deriveColor(0, 1, 1, 0.8));
                gc.setLineWidth(1.5);
                gc.strokeOval(x - r * 1.2 - Math.sin(haloPhase)*5, y - r * 1.2 - Math.cos(haloPhase)*5, r * 2.4, r * 2.4);
                
                // 玻璃高光面
                gc.setFill(new LinearGradient(x - r, y - r, x + r, y + r, false, CycleMethod.NO_CYCLE,
                        new Stop(0, nc.deriveColor(0, 1, 1, 0.9)),
                        new Stop(0.3, Color.WHITE.deriveColor(0, 1, 1, 0.8)),
                        new Stop(1, nc.deriveColor(0, 1, 1, 0.5))));
                gc.fillOval(x - r, y - r, r * 2, r * 2);
                
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2);
                gc.strokeOval(x - r, y - r, r * 2, r * 2);
                
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(24 * scale));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(gEmoji(note.gestureType), x, y + 8 * scale);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }

        // Float Texts
        for (FloatText ft : floatTexts) {
            gc.setFill(ft.color.deriveColor(0, 1, 1, Math.max(0, ft.life / 30.0)));
            gc.setFont(Font.font("Courier New", 22));
            gc.setTextAlign(TextAlignment.CENTER); 
            gc.fillText(ft.text, ft.x, ft.y);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // Modular high-tech HUD boxes
        // Top-left with '节奏大师' and track info
        gc.setFill(Color.rgb(0, 20, 40, 0.8));
        gc.setStroke(Color.CYAN);
        gc.setLineWidth(1.5);
        gc.fillRect(20, 50, 200, 60);
        gc.strokeRect(20, 50, 200, 60);
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Courier New", 18));
        gc.fillText("节奏大师", 30, 75);
        gc.setFill(Color.rgb(0, 255, 255, 0.7));
        gc.setFont(Font.font("Courier New", 12));
        gc.fillText((selectedSong != null ? selectedSong.title : "") + " | L:" + laneCount, 30, 95);

        // Top-center with timer '305s' and large score '0'
        gc.fillRect(canvasWidth / 2.0 - 100, 50, 200, 60);
        gc.strokeRect(canvasWidth / 2.0 - 100, 50, 200, 60);
        int remSecs = 0;
        if (musicStarted && mediaPlayer != null) {
            remSecs = Math.max(0, (int)(selectedSong.duration - mediaPlayer.getCurrentTime().toSeconds()));
        }
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Courier New", 14));
        gc.fillText("TIMER: " + remSecs + "s", canvasWidth / 2.0 - 80, 70);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Courier New", 28));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.valueOf(score), canvasWidth / 2.0, 100);
        gc.setTextAlign(TextAlignment.LEFT);

        // Top-right with score breakdown
        gc.fillRect(canvasWidth - 220, 50, 200, 60);
        gc.strokeRect(canvasWidth - 220, 50, 200, 60);
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Courier New", 16));
        gc.fillText("P:" + perfectCount, canvasWidth - 200, 75);
        gc.setFill(Color.LIME);
        gc.fillText("G:" + greatCount, canvasWidth - 140, 75);
        gc.setFill(Color.RED);
        gc.fillText("M:" + missCount, canvasWidth - 80, 75);
        if (combo >= 10) {
            gc.setFill(Color.GOLD);
            gc.fillText("COMBO: " + combo, canvasWidth - 200, 95);
        }

        // Progress bar at very bottom
        double prog = 0;
        if (musicStarted && mediaPlayer != null) {
            prog = Math.min(1.0, mediaPlayer.getCurrentTime().toSeconds() / selectedSong.duration);
        } else if (noMusicFallback) {
            prog = (double) frameCount / gameDurationFrames;
        }
        gc.setFill(Color.rgb(0, 255, 255, 0.3));
        gc.fillRect(20, canvasHeight - 20, canvasWidth - 40, 4);
        gc.setFill(Color.CYAN);
        gc.fillRect(20, canvasHeight - 20, (canvasWidth - 40) * prog, 4);
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
    @Override public int getMaxCombo() { return maxCombo; }

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
    private void createPerfectBlast(double x, double y) {
        shockwaves.add(new Shockwave(x, y, 150, 30, Color.CYAN));
        shockwaves.add(new Shockwave(x, y, 100, 20, Color.GOLD));
        for (int i = 0; i < 40; i++) {
            double angle = RAND.nextDouble() * Math.PI * 2;
            double speed = 2 + RAND.nextDouble() * 8;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed, 20 + RAND.nextInt(20), 3 + RAND.nextDouble() * 5, RAND.nextBoolean() ? Color.CYAN : Color.GOLD));
        }
        // 量子玻璃碎裂 (Quantum Glass Shards)
        for (int i = 0; i < 15; i++) {
            double angle = RAND.nextDouble() * Math.PI * 2;
            double speed = 4 + RAND.nextDouble() * 12;
            glassShards.add(new GlassShard(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed, 20 + RAND.nextInt(15), 6 + RAND.nextDouble() * 10, Color.CYAN));
        }
    }

    private void createGreatBlast(double x, double y) {
        shockwaves.add(new Shockwave(x, y, 100, 25, Color.LIME));
        for (int i = 0; i < 20; i++) {
            double angle = RAND.nextDouble() * Math.PI * 2;
            double speed = 2 + RAND.nextDouble() * 5;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed, 15 + RAND.nextInt(15), 2 + RAND.nextDouble() * 4, Color.LIME));
        }
    }

    private void createMissGlitch(double x, double y) {
        misses.add(new MissGlitch(x, y, 30));
    }

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

    /** 裁剪过长文字以适配卡片宽度 */
    private String clipText(String text, double maxWidth, GraphicsContext gc) {
        if (text == null || text.isEmpty()) return "";
        double textW = gc.getFont().getSize() * text.length() * 0.6; // 粗略估算
        if (textW <= maxWidth) return text;
        // 逐步截短
        for (int i = text.length() - 1; i > 0; i--) {
            if (gc.getFont().getSize() * i * 0.6 <= maxWidth - 20) {
                return text.substring(0, i) + "…";
            }
        }
        return text;
    }

    // ===== 内部类 =====
    private static class SongInfo {
        String title; File file; double duration;
        SongInfo(String t, File f, double d) { title = t; file = f; duration = d; }
    }

    private static class BeatmapNote {
        double time; int lane; GestureType gestureType;
        double holdDuration; // >0 表示长条，0表示普通音符
        BeatmapNote(double t, int l, GestureType g) { this(t, l, g, 0); }
        BeatmapNote(double t, int l, GestureType g, double hd) { time = t; lane = l; gestureType = g; holdDuration = hd; }
    }

    private static class Note {
        GestureType gestureType; int lane; double y; boolean judged;
        double holdLength;
        boolean holdStarted;
        boolean holdBroken;  // 中途松手
        Note(GestureType g, int l, double y) { this(g, l, y, 0); }
        Note(GestureType g, int l, double y, double hl) { gestureType = g; lane = l; this.y = y; holdLength = hl; }
    }

    private static class FloatText {
        String text; double x, y; int life; Color color;
        FloatText(String t, double x, double y, int l, Color c) { text=t; x=x; y=y; life=l; color=c; }
    }

    private static class LaneHit {
        int lane, life = 12; Color color;
        LaneHit(int l, Color c) { lane = l; color = c; }
    }

    private static class Particle {
        double x, y, vx, vy, life, maxLife, size;
        Color color;
        Particle(double x, double y, double vx, double vy, double life, double size, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.maxLife = life; this.size = size; this.color = c;
        }
    }

    private static class Shockwave {
        double x, y, radius, maxRadius, life, maxLife;
        Color color;
        Shockwave(double x, double y, double maxRadius, double life, Color c) {
            this.x = x; this.y = y; this.radius = 0; this.maxRadius = maxRadius; this.life = life; this.maxLife = life; this.color = c;
        }
    }

    private static class MissGlitch {
        double x, y, life, maxLife;
        MissGlitch(double x, double y, double life) {
            this.x = x; this.y = y; this.life = life; this.maxLife = life;
        }
    }

    private static class BgRipple {
        double x, y, radius, maxRadius, life, maxLife;
        BgRipple(double x, double y, double maxRadius, double life) {
            this.x = x; this.y = y; this.radius = 0; this.maxRadius = maxRadius; this.life = life; this.maxLife = life;
        }
    }

    private static class GlassShard {
        double x, y, vx, vy, angle, rotSpeed, life, maxLife, size;
        Color color;
        GlassShard(double x, double y, double vx, double vy, double life, double size, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.maxLife = life; this.size = size; this.color = c;
            this.angle = RAND.nextDouble() * 360;
            this.rotSpeed = (RAND.nextDouble() - 0.5) * 20;
        }
    }
}
