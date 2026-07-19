package com.gesturegame.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plays the locally packaged green planet loop behind the native lobby cards. */
final class LobbyPlanetBackground {

    private static final Logger LOGGER = Logger.getLogger(
            LobbyPlanetBackground.class.getName());
    private static final String VIDEO_RESOURCE =
            "/assets/video/lobby-green-planet.mp4";
    private static final String POSTER_RESOURCE =
            "/assets/video/lobby-green-planet-poster.png";
    private static final String CACHE_FILE =
            "lobby-green-planet-16586551.mp4";
    private static final long VIDEO_SIZE = 16_586_551L;
    private static final double SOURCE_WIDTH = 1464.0;
    private static final double SOURCE_HEIGHT = 1412.0;

    private final StackPane layer;
    private final ImageView posterView;

    private MediaView mediaView;
    private MediaPlayer mediaPlayer;
    private double mediaWidth = SOURCE_WIDTH;
    private double mediaHeight = SOURCE_HEIGHT;
    private volatile boolean active;

    LobbyPlanetBackground(StackPane layer) {
        this.layer = layer;
        this.layer.setStyle("-fx-background-color: #01070b;");

        URL posterUrl = LobbyPlanetBackground.class.getResource(POSTER_RESOURCE);
        posterView = new ImageView(posterUrl == null
                ? null : new Image(posterUrl.toExternalForm(), true));
        posterView.setMouseTransparent(true);
        posterView.setSmooth(true);
        posterView.setPreserveRatio(false);
        layer.getChildren().setAll(posterView);

        layer.widthProperty().addListener((observable, oldValue, newValue) -> updateGeometry());
        layer.heightProperty().addListener((observable, oldValue, newValue) -> updateGeometry());
        updateGeometry();
        prepareVideoAsync();
    }

    void start() {
        active = true;
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    void stop() {
        active = false;
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    private void prepareVideoAsync() {
        Thread loader = new Thread(() -> {
            try {
                String mediaUri = resolveVideoUri();
                Platform.runLater(() -> initializePlayer(mediaUri));
            } catch (IOException error) {
                LOGGER.log(Level.WARNING,
                        "无法准备大厅星球视频，保留静态星球背景", error);
            }
        }, "lobby-planet-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void initializePlayer(String mediaUri) {
        try {
            Media media = new Media(mediaUri);
            media.setOnError(() -> LOGGER.warning(
                    "大厅星球视频加载失败: " + media.getError()));

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setMute(true);
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            mediaPlayer.setAutoPlay(false);
            mediaPlayer.setOnError(() -> LOGGER.warning(
                    "大厅星球视频播放失败: " + mediaPlayer.getError()));

            mediaView = new MediaView(mediaPlayer);
            mediaView.setMouseTransparent(true);
            mediaView.setSmooth(true);
            mediaView.setPreserveRatio(false);
            mediaView.setOpacity(0.0);
            layer.getChildren().add(mediaView);

            mediaPlayer.setOnReady(() -> {
                if (media.getWidth() > 0.0 && media.getHeight() > 0.0) {
                    mediaWidth = media.getWidth();
                    mediaHeight = media.getHeight();
                }
                updateGeometry();
                if (active) {
                    mediaPlayer.play();
                }
            });
            mediaPlayer.setOnPlaying(this::revealVideo);
            if (active) {
                mediaPlayer.play();
            }
        } catch (RuntimeException error) {
            LOGGER.log(Level.WARNING,
                    "无法创建大厅星球播放器，保留静态星球背景", error);
        }
    }

    private void revealVideo() {
        if (mediaView == null || mediaView.getOpacity() >= 0.99) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(420.0), mediaView);
        fade.setFromValue(mediaView.getOpacity());
        fade.setToValue(1.0);
        fade.play();
    }

    private void updateGeometry() {
        double width = Math.max(1.0, layer.getWidth());
        double height = Math.max(1.0, layer.getHeight());
        Rectangle2D viewport = coverViewport(
                width, height, mediaWidth, mediaHeight);

        posterView.setViewport(viewport);
        posterView.setFitWidth(width);
        posterView.setFitHeight(height);
        if (mediaView != null) {
            mediaView.setViewport(viewport);
            mediaView.setFitWidth(width);
            mediaView.setFitHeight(height);
        }
    }

    private static Rectangle2D coverViewport(double targetWidth, double targetHeight,
                                              double sourceWidth, double sourceHeight) {
        double targetAspect = targetWidth / targetHeight;
        double sourceAspect = sourceWidth / sourceHeight;
        if (targetAspect > sourceAspect) {
            double cropHeight = sourceWidth / targetAspect;
            return new Rectangle2D(0.0, (sourceHeight - cropHeight) * 0.5,
                    sourceWidth, cropHeight);
        }
        double cropWidth = sourceHeight * targetAspect;
        return new Rectangle2D((sourceWidth - cropWidth) * 0.5, 0.0,
                cropWidth, sourceHeight);
    }

    private static String resolveVideoUri() throws IOException {
        URL resource = LobbyPlanetBackground.class.getResource(VIDEO_RESOURCE);
        if (resource == null) {
            throw new IOException("缺少资源: " + VIDEO_RESOURCE);
        }
        if ("file".equalsIgnoreCase(resource.getProtocol())) {
            return resource.toExternalForm();
        }

        Path cacheDirectory = resolveCacheDirectory();
        Files.createDirectories(cacheDirectory);
        Path cachedVideo = cacheDirectory.resolve(CACHE_FILE);
        if (!Files.isRegularFile(cachedVideo)
                || Files.size(cachedVideo) != VIDEO_SIZE) {
            Path temporary = cacheDirectory.resolve(CACHE_FILE + ".part");
            try (InputStream input = resource.openStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, cachedVideo,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, cachedVideo,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return cachedVideo.toUri().toString();
    }

    private static Path resolveCacheDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "AIGestureGame", "cache");
        }
        return Path.of(System.getProperty("user.home"),
                ".ai-gesture-game", "cache");
    }
}
