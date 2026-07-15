package com.gesturegame.game;

import com.gesturegame.ai.AdaptiveShooterLevelService;
import com.gesturegame.ai.PlayerPerformance;
import com.gesturegame.ai.ShooterLevelConfig;
import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * 横向卷轴太空射击：手的纵坐标控制战机，战机自动射击。
 * 每局表现会异步交给兼容 LLM 接口生成下一关，接口不可用时自动使用本地规则。
 */
public final class SideScrollingShooter implements GameInterface {

    private static final Random RANDOM = new Random();
    private static final int PLAYER_FIRE_INTERVAL = 9;

    private final AdaptiveShooterLevelService levelService = new AdaptiveShooterLevelService();
    private final List<Bullet> playerBullets = new ArrayList<>();
    private final List<Bullet> enemyBullets = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Flash> flashes = new ArrayList<>();

    private Difficulty difficulty = Difficulty.NORMAL;
    private ShooterLevelConfig level;
    private CompletableFuture<ShooterLevelConfig> nextLevelFuture;
    private PlayerPerformance lastPerformance;
    private int width;
    private int height;
    private int frame;
    private int score;
    private int hp;
    private int damageTaken;
    private int enemiesDestroyed;
    private int shotsFired;
    private int shotsHit;
    private int nextWaveIndex;
    private double playerX;
    private double playerY;
    private double targetY;
    private boolean bossSpawned;
    private boolean over;
    private boolean cleared;

    @Override
    public String getName() { return "星际突击"; }

    @Override
    public String getDescription() { return "手势驾驶战机，AI 根据表现动态生成下一关"; }

    @Override
    public String getIcon() { return "🚀"; }

    @Override
    public void init(int width, int height) {
        this.width = Math.max(640, width);
        this.height = Math.max(420, height);
        if (level == null) level = levelService.firstLevel(difficulty);
        frame = 0;
        score = 0;
        hp = level.playerHp();
        damageTaken = 0;
        enemiesDestroyed = 0;
        shotsFired = 0;
        shotsHit = 0;
        nextWaveIndex = 0;
        playerX = this.width * 0.16;
        playerY = this.height * 0.5;
        targetY = playerY;
        bossSpawned = false;
        over = false;
        cleared = false;
        nextLevelFuture = null;
        playerBullets.clear();
        enemyBullets.clear();
        enemies.clear();
        flashes.clear();
        createStars();
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;
        frame++;

        if (gesture != null && gesture.isHandDetected()) {
            targetY = 70 + clamp(gesture.getHandY(), 0, 1) * (height - 140);
        }
        playerY += (targetY - playerY) * 0.18;

        updateStars();
        spawnScheduledWaves();
        if (frame % PLAYER_FIRE_INTERVAL == 0) {
            playerBullets.add(new Bullet(playerX + 42, playerY, 10.5, 0, true));
            shotsFired++;
        }

        if (!bossSpawned && frame >= level.durationSeconds() * 60) {
            spawnBoss();
            requestNextLevel();
        }

        updateBullets();
        updateEnemies();
        handleCollisions();
        updateFlashes();

        if (hp <= 0) finish(false);
        if (bossSpawned && enemies.stream().noneMatch(enemy -> enemy.boss)) finish(true);
        if (frame > (level.durationSeconds() + 35) * 60) finish(false);
    }

    private void spawnScheduledWaves() {
        List<ShooterLevelConfig.Wave> waves = level.waves();
        while (nextWaveIndex < waves.size()
                && frame >= waves.get(nextWaveIndex).startSecond() * 60) {
            spawnWave(waves.get(nextWaveIndex));
            nextWaveIndex++;
        }
    }

    private void spawnWave(ShooterLevelConfig.Wave wave) {
        for (int i = 0; i < wave.count(); i++) {
            double y;
            if ("RANDOM".equals(wave.formation())) {
                y = 85 + RANDOM.nextDouble() * (height - 170);
            } else if ("ZIGZAG".equals(wave.formation())) {
                y = height * 0.5 + (i % 2 == 0 ? -1 : 1) * (70 + (i % 3) * 32);
            } else {
                y = 90 + (i + 1.0) * (height - 180) / (wave.count() + 1.0);
            }
            Enemy enemy = new Enemy(width + 80 + i * 72, y, wave.speed(), wave.hp(), wave.shooter(), false);
            enemy.phase = i * 0.8;
            enemy.fireCooldown = 35 + RANDOM.nextInt(Math.max(20, level.fireIntervalFrames()));
            enemies.add(enemy);
        }
    }

    private void spawnBoss() {
        bossSpawned = true;
        Enemy boss = new Enemy(width + 120, height * 0.5, 1.35, level.bossHp(), true, true);
        boss.maxHp = level.bossHp();
        boss.fireCooldown = 25;
        enemies.add(boss);
    }

    private void updateBullets() {
        playerBullets.forEach(Bullet::move);
        enemyBullets.forEach(Bullet::move);
        playerBullets.removeIf(b -> b.x > width + 40 || b.y < -30 || b.y > height + 30);
        enemyBullets.removeIf(b -> b.x < -40 || b.y < -30 || b.y > height + 30);
    }

    private void updateEnemies() {
        for (Enemy enemy : enemies) {
            enemy.x -= enemy.speed;
            enemy.phase += 0.045;
            enemy.y += Math.sin(enemy.phase) * (enemy.boss ? 1.2 : 0.75);
            enemy.y = clamp(enemy.y, 65, height - 65);
            if (enemy.shooter && enemy.x < width - 40) {
                enemy.fireCooldown--;
                if (enemy.fireCooldown <= 0) {
                    double dx = playerX - enemy.x;
                    double dy = playerY - enemy.y;
                    double len = Math.max(1, Math.hypot(dx, dy));
                    double speed = enemy.boss ? 5.2 : 4.1;
                    enemyBullets.add(new Bullet(enemy.x - 28, enemy.y, dx / len * speed, dy / len * speed, false));
                    enemy.fireCooldown = Math.max(22, level.fireIntervalFrames() + RANDOM.nextInt(35) - 15);
                }
            }
        }
        enemies.removeIf(enemy -> !enemy.boss && enemy.x < -90);
    }

    private void handleCollisions() {
        Iterator<Bullet> playerIterator = playerBullets.iterator();
        while (playerIterator.hasNext()) {
            Bullet bullet = playerIterator.next();
            Enemy hit = null;
            for (Enemy enemy : enemies) {
                double radius = enemy.boss ? 62 : 26;
                if (distanceSquared(bullet.x, bullet.y, enemy.x, enemy.y) < radius * radius) {
                    hit = enemy;
                    break;
                }
            }
            if (hit != null) {
                playerIterator.remove();
                shotsHit++;
                hit.hp--;
                flashes.add(new Flash(hit.x, hit.y, Color.web("#fde047")));
                if (hit.hp <= 0) {
                    score += hit.boss ? 1800 : 100;
                    enemiesDestroyed++;
                }
            }
        }
        enemies.removeIf(enemy -> enemy.hp <= 0);

        Iterator<Bullet> enemyIterator = enemyBullets.iterator();
        while (enemyIterator.hasNext()) {
            Bullet bullet = enemyIterator.next();
            if (distanceSquared(bullet.x, bullet.y, playerX, playerY) < 28 * 28) {
                enemyIterator.remove();
                hp--;
                damageTaken++;
                flashes.add(new Flash(playerX, playerY, Color.web("#fb7185")));
            }
        }

        for (Enemy enemy : enemies) {
            double radius = enemy.boss ? 68 : 30;
            if (distanceSquared(enemy.x, enemy.y, playerX, playerY) < (radius + 22) * (radius + 22)) {
                hp -= enemy.boss ? 2 : 1;
                damageTaken++;
                if (enemy.boss) {
                    enemy.x = Math.max(enemy.x + 140, width * 0.72);
                    playerY = clamp(playerY + (playerY < height / 2.0 ? -70 : 70), 65, height - 65);
                } else {
                    enemy.hp = 0;
                }
            }
        }
        enemies.removeIf(enemy -> enemy.hp <= 0);
    }

    private void finish(boolean victory) {
        if (over) return;
        cleared = victory;
        if (victory) score += hp * 150;
        over = true;
        lastPerformance = performance();
        requestNextLevel();
    }

    private void requestNextLevel() {
        if (nextLevelFuture == null) {
            nextLevelFuture = levelService.generateNext(level.levelNumber() + 1, difficulty, performance());
        }
    }

    private PlayerPerformance performance() {
        double accuracy = shotsFired == 0 ? 0 : shotsHit / (double) shotsFired;
        return new PlayerPerformance(level.levelNumber(), score, accuracy, damageTaken,
                enemiesDestroyed, frame / 60, cleared);
    }

    @Override
    public void render(GraphicsContext gc) {
        drawBackground(gc);
        drawStars(gc);
        drawBullets(gc);
        drawEnemies(gc);
        drawPlayer(gc);
        drawFlashes(gc);
        drawHud(gc);
        if (over) drawResult(gc);
    }

    private void drawBackground(GraphicsContext gc) {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#020617")), new Stop(0.55, Color.web("#11144a")),
                new Stop(1, Color.web("#3b0764"))));
        gc.fillRect(0, 0, width, height);
        gc.setFill(Color.rgb(56, 189, 248, 0.07));
        gc.fillOval(width * 0.45, -height * 0.35, height, height);
    }

    private void drawStars(GraphicsContext gc) {
        for (Star star : stars) {
            gc.setFill(Color.rgb(255, 255, 255, star.alpha));
            gc.fillOval(star.x, star.y, star.size, star.size);
        }
    }

    private void drawPlayer(GraphicsContext gc) {
        gc.save();
        gc.translate(playerX, playerY);
        gc.setFill(Color.rgb(34, 211, 238, 0.25));
        gc.fillOval(-50, -25, 72, 50);
        gc.setFill(Color.web("#e0f2fe"));
        double[] xs = {-32, 22, -14, -28};
        double[] ys = {-22, 0, 25, 10};
        gc.fillPolygon(xs, ys, 4);
        gc.setFill(Color.web("#38bdf8"));
        gc.fillPolygon(new double[]{-22, 8, -18}, new double[]{-9, 0, 12}, 3);
        gc.setFill(Color.web("#f97316"));
        gc.fillPolygon(new double[]{-30, -54 - RANDOM.nextDouble() * 8, -30},
                new double[]{-8, 0, 8}, 3);
        gc.restore();
    }

    private void drawEnemies(GraphicsContext gc) {
        for (Enemy enemy : enemies) {
            if (enemy.boss) {
                gc.setFill(Color.rgb(244, 63, 94, 0.18));
                gc.fillOval(enemy.x - 78, enemy.y - 78, 156, 156);
                gc.setFill(Color.web("#be123c"));
                gc.fillRoundRect(enemy.x - 48, enemy.y - 54, 88, 108, 25, 25);
                gc.setFill(Color.web("#fda4af"));
                gc.fillOval(enemy.x - 40, enemy.y - 18, 34, 36);
                double ratio = Math.max(0, enemy.hp / (double) enemy.maxHp);
                gc.setFill(Color.rgb(0, 0, 0, 0.5));
                gc.fillRoundRect(width * 0.28, 34, width * 0.44, 12, 10, 10);
                gc.setFill(Color.web("#fb7185"));
                gc.fillRoundRect(width * 0.28, 34, width * 0.44 * ratio, 12, 10, 10);
            } else {
                gc.setFill(enemy.shooter ? Color.web("#f472b6") : Color.web("#a78bfa"));
                gc.fillPolygon(new double[]{enemy.x + 28, enemy.x - 24, enemy.x - 12},
                        new double[]{enemy.y, enemy.y - 20, enemy.y + 21}, 3);
                gc.setFill(Color.web("#fef3c7"));
                gc.fillOval(enemy.x - 4, enemy.y - 5, 10, 10);
            }
        }
    }

    private void drawBullets(GraphicsContext gc) {
        gc.setFill(Color.web("#67e8f9"));
        for (Bullet bullet : playerBullets) gc.fillRoundRect(bullet.x - 8, bullet.y - 2, 18, 5, 5, 5);
        gc.setFill(Color.web("#fb7185"));
        for (Bullet bullet : enemyBullets) gc.fillOval(bullet.x - 5, bullet.y - 5, 10, 10);
    }

    private void drawFlashes(GraphicsContext gc) {
        for (Flash flash : flashes) {
            gc.setStroke(flash.color.deriveColor(0, 1, 1, flash.life / 14.0));
            gc.setLineWidth(3);
            double radius = 22 - flash.life;
            gc.strokeOval(flash.x - radius, flash.y - radius, radius * 2, radius * 2);
        }
    }

    private void drawHud(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        gc.fillText("关卡 " + level.levelNumber() + " · " + level.title(), 28, 40);
        gc.setFont(Font.font("Microsoft YaHei", 14));
        gc.setFill(Color.web("#a5f3fc"));
        gc.fillText("关卡来源：" + level.source(), 28, 65);
        gc.setFill(Color.web("#fda4af"));
        gc.fillText("耐久 " + "●".repeat(Math.max(0, hp)), 28, 91);
        int remaining = Math.max(0, level.durationSeconds() - frame / 60);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.rgb(255, 255, 255, 0.65));
        gc.fillText(bossSpawned ? "BOSS 作战" : "距离 BOSS  " + remaining + "s", width / 2.0, 67);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawResult(GraphicsContext gc) {
        gc.setFill(Color.rgb(2, 6, 23, 0.82));
        gc.fillRect(0, 0, width, height);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(cleared ? Color.web("#67e8f9") : Color.web("#fb7185"));
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 42));
        gc.fillText(cleared ? "航线突破成功" : "战机失联", width / 2.0, height * 0.39);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", 22));
        double accuracy = shotsFired == 0 ? 0 : shotsHit * 100.0 / shotsFired;
        gc.fillText("得分 " + score + "    命中率 " + String.format("%.1f%%", accuracy),
                width / 2.0, height * 0.48);
        gc.setFill(Color.web("#c4b5fd"));
        gc.setFont(Font.font("Microsoft YaHei", 17));
        String generation = nextLevelFuture != null && nextLevelFuture.isDone()
                ? "下一关已动态生成" : "正在后台生成下一关…";
        gc.fillText(generation, width / 2.0, height * 0.55);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void createStars() {
        stars.clear();
        for (int i = 0; i < 100; i++) {
            stars.add(new Star(RANDOM.nextDouble() * width, RANDOM.nextDouble() * height,
                    0.5 + RANDOM.nextDouble() * 2.2, 0.25 + RANDOM.nextDouble() * 0.65));
        }
    }

    private void updateStars() {
        for (Star star : stars) {
            star.x -= star.size * 0.65;
            if (star.x < 0) {
                star.x = width;
                star.y = RANDOM.nextDouble() * height;
            }
        }
    }

    private void updateFlashes() {
        flashes.forEach(flash -> flash.life--);
        flashes.removeIf(flash -> flash.life <= 0);
    }

    @Override
    public boolean isOver() { return over; }

    @Override
    public int getScore() { return score; }

    @Override
    public void reset() {
        int nextNumber = level == null ? 1 : level.levelNumber() + 1;
        if (nextLevelFuture != null && nextLevelFuture.isDone()) {
            level = nextLevelFuture.getNow(levelService.localLevel(nextNumber, difficulty, lastPerformance));
        } else {
            level = levelService.localLevel(nextNumber, difficulty, lastPerformance);
        }
        init(width, height);
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
        this.level = null;
    }

    @Override
    public Difficulty getDifficulty() { return difficulty; }

    private static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Bullet {
        double x, y;
        final double vx, vy;
        final boolean player;
        Bullet(double x, double y, double vx, double vy, boolean player) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.player = player;
        }
        void move() { x += vx; y += vy; }
    }

    private static final class Enemy {
        double x, y, speed, phase;
        int hp, maxHp, fireCooldown;
        final boolean shooter, boss;
        Enemy(double x, double y, double speed, int hp, boolean shooter, boolean boss) {
            this.x = x; this.y = y; this.speed = speed; this.hp = hp; this.maxHp = hp;
            this.shooter = shooter; this.boss = boss;
        }
    }

    private static final class Star {
        double x, y;
        final double size, alpha;
        Star(double x, double y, double size, double alpha) {
            this.x = x; this.y = y; this.size = size; this.alpha = alpha;
        }
    }

    private static final class Flash {
        final double x, y;
        final Color color;
        int life = 14;
        Flash(double x, double y, Color color) { this.x = x; this.y = y; this.color = color; }
    }
}
