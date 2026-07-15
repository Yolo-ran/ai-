package com.gesturegame.game;

import com.gesturegame.ai.AdaptiveShooterLevelService;
import com.gesturegame.ai.PlayerPerformance;
import com.gesturegame.ai.ShooterLevelConfig;
import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
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
 * 包含全新的赛博科幻 UI、粒子尾焰、激光武器与全息 HUD。
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
    private final List<Particle> particles = new ArrayList<>();

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
    private double lastPlayerY;
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
        lastPlayerY = playerY;
        targetY = playerY;
        bossSpawned = false;
        over = false;
        cleared = false;
        nextLevelFuture = null;
        playerBullets.clear();
        enemyBullets.clear();
        enemies.clear();
        flashes.clear();
        particles.clear();
        createStars();
    }

    @Override
    public void update(GestureData gesture) {
        if (over) return;
        frame++;

        if (gesture != null && gesture.isHandDetected()) {
            targetY = 70 + clamp(gesture.getHandY(), 0, 1) * (height - 140);
        }
        lastPlayerY = playerY;
        playerY += (targetY - playerY) * 0.18;

        updateStars();
        spawnScheduledWaves();
        if (frame % PLAYER_FIRE_INTERVAL == 0) {
            // 双翼发射激光束
            playerBullets.add(new Bullet(playerX + 20, playerY - 15, 14.5, 0, true));
            playerBullets.add(new Bullet(playerX + 20, playerY + 15, 14.5, 0, true));
            shotsFired += 2;
        }

        if (!bossSpawned && frame >= level.durationSeconds() * 60) {
            spawnBoss();
            requestNextLevel();
        }

        updateBullets();
        updateEnemies();
        handleCollisions();
        updateFlashes();
        updateParticles();

        if (hp <= 0) finish(false);
        if (bossSpawned && enemies.stream().noneMatch(enemy -> enemy.boss)) finish(true);
        if (frame > (level.durationSeconds() + 35) * 60) finish(false);
    }

    private void updateParticles() {
        // 生成玩家战机尾焰粒子
        double playerVy = playerY - lastPlayerY;
        for (int i = 0; i < 4; i++) {
            double py = playerY + RANDOM.nextDouble() * 12 - 6;
            double px = playerX - 35 + RANDOM.nextDouble() * 5;
            double vx = -6 - RANDOM.nextDouble() * 6;
            double vy = playerVy * 0.3 + RANDOM.nextDouble() * 2 - 1;
            Color c = RANDOM.nextDouble() > 0.4 ? Color.web("#00F0FF") : Color.web("#D946EF"); // 电光蓝/荧光紫
            if (RANDOM.nextDouble() > 0.8) c = Color.web("#F97316"); // 亮橙核心
            particles.add(new Particle(px, py, vx, vy, 15 + RANDOM.nextDouble() * 15, 5 + RANDOM.nextDouble() * 7, c));
        }

        particles.forEach(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.life--;
            p.size *= 0.92;
        });
        particles.removeIf(p -> p.life <= 0);
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
            
            // 敌机尾迹
            if (frame % 2 == 0 && !enemy.boss) {
                particles.add(new Particle(enemy.x + 20, enemy.y, 2, 0, 10, 4, Color.web("#FF0055")));
            }

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
                flashes.add(new Flash(hit.x, hit.y, Color.web("#00F0FF"))); // 击中特效改蓝
                if (hit.hp <= 0) {
                    score += hit.boss ? 1800 : 100;
                    enemiesDestroyed++;
                    // 爆炸特效
                    for(int i=0; i<15; i++) {
                        particles.add(new Particle(hit.x, hit.y, RANDOM.nextDouble()*8-4, RANDOM.nextDouble()*8-4, 20, 8, Color.web("#00F0FF")));
                    }
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
                flashes.add(new Flash(playerX, playerY, Color.web("#FF0055")));
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
                    for(int i=0; i<15; i++) {
                        particles.add(new Particle(enemy.x, enemy.y, RANDOM.nextDouble()*8-4, RANDOM.nextDouble()*8-4, 20, 8, Color.web("#FF0055")));
                    }
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
        drawParticles(gc);
        drawBullets(gc);
        drawEnemies(gc);
        drawPlayer(gc);
        drawFlashes(gc);
        drawHud(gc);
        if (over) drawResult(gc);
    }

    private void drawBackground(GraphicsContext gc) {
        // 方案一：星际暗流 (Deep Nebula)
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0A0F1D")), new Stop(0.5, Color.web("#111827")),
                new Stop(1, Color.web("#1A1129"))));
        gc.fillRect(0, 0, width, height);
        
        // 动态星云层 (Nebula Dust)
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 240, 255, 0.05)), new Stop(0.5, Color.TRANSPARENT),
                new Stop(1, Color.rgb(217, 70, 239, 0.08))));
        gc.fillOval(-width*0.2, -height*0.2, width*1.4, height*1.4);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawStars(GraphicsContext gc) {
        // 星空与流星粒子层
        for (Star star : stars) {
            gc.setFill(Color.rgb(255, 255, 255, star.alpha));
            if (star.size > 2) {
                // 流星拉长
                gc.fillRoundRect(star.x, star.y, star.size * 3, star.size * 0.5, 2, 2);
            } else {
                gc.fillOval(star.x, star.y, star.size, star.size);
            }
        }
    }

    private void drawParticles(GraphicsContext gc) {
        gc.setGlobalBlendMode(BlendMode.ADD);
        for (Particle p : particles) {
            double alpha = Math.max(0, p.life / p.maxLife);
            gc.setFill(Color.color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha * 0.7));
            gc.fillOval(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
        }
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawPlayer(GraphicsContext gc) {
        gc.save();
        gc.translate(playerX, playerY);
        
        // 随手势俯仰的动态倾斜 (Pitch/Roll)
        double pitch = clamp((playerY - lastPlayerY) * 0.08, -0.4, 0.4);
        gc.rotate(Math.toDegrees(pitch));

        // 蜂窝半透明能量护盾
        gc.setGlobalBlendMode(BlendMode.ADD);
        gc.setStroke(Color.rgb(0, 240, 255, 0.4));
        gc.setLineWidth(2);
        gc.strokeOval(-45, -35, 90, 70);
        gc.setStroke(Color.rgb(0, 240, 255, 0.8));
        gc.strokeArc(-40, -30, 80, 60, -45, 90, javafx.scene.shape.ArcType.OPEN);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);

        // 战机 3D 金属机身
        // 顶翼
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, 
            new Stop(0, Color.web("#94A3B8")), new Stop(1, Color.web("#475569"))));
        gc.fillPolygon(new double[]{-25, 30, -10}, new double[]{-15, 0, 0}, 3);
        // 底翼
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, 
            new Stop(0, Color.web("#475569")), new Stop(1, Color.web("#1E293B"))));
        gc.fillPolygon(new double[]{-25, 30, -10}, new double[]{15, 0, 0}, 3);
        // 主机体（拉丝蓝碳纤维）
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, 
            new Stop(0, Color.web("#0F172A")), new Stop(1, Color.web("#1E293B"))));
        gc.fillPolygon(new double[]{-35, 20, -15}, new double[]{-5, 0, 5}, 3);
        // 座舱发光
        gc.setFill(Color.web("#00F0FF"));
        gc.fillOval(5, -4, 12, 8);
        
        gc.restore();
    }

    private void drawEnemies(GraphicsContext gc) {
        for (Enemy enemy : enemies) {
            gc.save();
            gc.translate(enemy.x, enemy.y);
            // 敌机轻微悬浮
            gc.rotate(Math.sin(enemy.phase) * 5);

            if (enemy.boss) {
                // 科幻机械 BOSS
                gc.setFill(Color.web("#1F1229")); // 暗机械底色
                gc.fillOval(-70, -70, 140, 140);
                gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, 
                    new Stop(0, Color.web("#6B21A8")), new Stop(1, Color.web("#3B0764"))));
                gc.fillPolygon(new double[]{-40, -60, -40, 20}, new double[]{-50, 0, 50, 0}, 4);
                
                // 红色核心
                gc.setFill(Color.web("#FF0055"));
                gc.fillOval(-15, -15, 30, 30);
                
                // BOSS血条
                double ratio = Math.max(0, enemy.hp / (double) enemy.maxHp);
                gc.setFill(Color.rgb(0, 0, 0, 0.6));
                gc.fillRoundRect(-60, -90, 120, 8, 4, 4);
                gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, 
                    new Stop(0, Color.web("#FF0055")), new Stop(1, Color.web("#F97316"))));
                gc.fillRoundRect(-60, -90, 120 * ratio, 8, 4, 4);
            } else {
                // 小型机械侦察机
                gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, 
                    new Stop(0, Color.web("#4C1D95")), new Stop(1, Color.web("#2E1065"))));
                gc.fillPolygon(new double[]{15, -15, -15}, new double[]{0, -15, 15}, 3);
                // 引擎发光
                gc.setFill(Color.web("#FF0055"));
                gc.fillOval(-10, -4, 8, 8);
            }
            gc.restore();
        }
    }

    private void drawBullets(GraphicsContext gc) {
        gc.setGlobalBlendMode(BlendMode.ADD);
        for (Bullet bullet : playerBullets) {
            // 电光蓝激光束特效 (Laser Beams)
            gc.setStroke(Color.rgb(0, 240, 255, 0.2));
            gc.setLineWidth(12);
            gc.strokeLine(bullet.x - 30, bullet.y, bullet.x + 10, bullet.y);
            gc.setStroke(Color.rgb(0, 240, 255, 0.6));
            gc.setLineWidth(5);
            gc.strokeLine(bullet.x - 20, bullet.y, bullet.x + 10, bullet.y);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeLine(bullet.x - 10, bullet.y, bullet.x + 10, bullet.y);
        }
        for (Bullet bullet : enemyBullets) {
            // 敌方能量弹
            gc.setFill(Color.rgb(255, 0, 85, 0.3));
            gc.fillOval(bullet.x - 12, bullet.y - 12, 24, 24);
            gc.setFill(Color.rgb(255, 0, 85, 0.8));
            gc.fillOval(bullet.x - 6, bullet.y - 6, 12, 12);
            gc.setFill(Color.WHITE);
            gc.fillOval(bullet.x - 3, bullet.y - 3, 6, 6);
        }
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawFlashes(GraphicsContext gc) {
        gc.setGlobalBlendMode(BlendMode.ADD);
        for (Flash flash : flashes) {
            gc.setStroke(flash.color.deriveColor(0, 1, 1, flash.life / 14.0));
            gc.setLineWidth(4);
            double radius = 30 - flash.life;
            gc.strokeOval(flash.x - radius, flash.y - radius, radius * 2, radius * 2);
        }
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawHud(GraphicsContext gc) {
        // 全息发光面板风格 (Holographic HUD)
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16)); // 科技感无衬线
        
        // 顶部网格半透明面板
        gc.setFill(Color.rgb(15, 23, 42, 0.6));
        gc.fillRoundRect(20, 20, 240, 65, 8, 8);
        gc.setStroke(Color.rgb(0, 240, 255, 0.3));
        gc.setLineWidth(1);
        gc.strokeRoundRect(20, 20, 240, 65, 8, 8);

        gc.setFill(Color.web("#00F0FF"));
        gc.fillText("🚀 " + level.title() + " [Lv." + level.levelNumber() + "]", 35, 45);
        
        // 能量槽形式血条 (Energy Bar)
        gc.setFill(Color.rgb(0, 0, 0, 0.5));
        gc.fillRect(35, 60, 150, 8);
        int maxHp = level.playerHp();
        double hpRatio = Math.max(0, hp / (double) maxHp);
        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, 
            new Stop(0, Color.web("#00F0FF")), new Stop(1, Color.web("#38BDF8"))));
        gc.fillRect(35, 60, 150 * hpRatio, 8);

        // 倒计时进度条
        int remaining = Math.max(0, level.durationSeconds() - frame / 60);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#94A3B8"));
        gc.setFont(Font.font("Arial", 14));
        gc.fillText(bossSpawned ? ">> BOSS 接触 <<" : "距离 BOSS 接触: " + remaining + "s", width / 2.0, 35);
        
        // 得分面板 (右上角悬浮面板)
        gc.setFill(Color.rgb(15, 23, 42, 0.6));
        gc.fillPolygon(new double[]{width - 140, width - 20, width - 20, width - 155}, 
                       new double[]{20, 20, 50, 50}, 4);
        gc.setStroke(Color.rgb(0, 240, 255, 0.5));
        gc.strokePolygon(new double[]{width - 140, width - 20, width - 20, width - 155}, 
                         new double[]{20, 20, 50, 50}, 4);
        gc.setFill(Color.web("#00F0FF"));
        gc.fillText("SCORE: " + score, width - 85, 40);

        // 底部“双手入镜保持返回”提示 (隐式悬浮 + 响应式边框)
        gc.setFill(Color.rgb(0, 0, 0, 0.4));
        gc.fillRoundRect(width / 2.0 - 100, height - 40, 200, 24, 12, 12);
        gc.setStroke(Color.rgb(217, 70, 239, 0.6)); // 荧光紫提示边框流光
        gc.setLineWidth(1);
        gc.strokeRoundRect(width / 2.0 - 100, height - 40, 200, 24, 12, 12);
        gc.setFill(Color.web("#00F0FF"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("👐 双手入镜保持返回", width / 2.0, height - 23);
        
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawResult(GraphicsContext gc) {
        gc.setFill(Color.rgb(2, 6, 23, 0.85));
        gc.fillRect(0, 0, width, height);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(cleared ? Color.web("#00F0FF") : Color.web("#FF0055"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        gc.fillText(cleared ? "MISSION ACCOMPLISHED" : "SIGNAL LOST", width / 2.0, height * 0.39);
        
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 20));
        double accuracy = shotsFired == 0 ? 0 : shotsHit * 100.0 / shotsFired;
        gc.fillText(String.format("SCORE: %d   ACCURACY: %.1f%%", score, accuracy),
                width / 2.0, height * 0.48);
        
        gc.setFill(Color.web("#A78BFA"));
        gc.setFont(Font.font("Arial", 16));
        String generation = nextLevelFuture != null && nextLevelFuture.isDone()
                ? ">> NEXT LEVEL READY <<" : ">> GENERATING NEXT LEVEL <<";
        gc.fillText(generation, width / 2.0, height * 0.55);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void createStars() {
        stars.clear();
        for (int i = 0; i < 120; i++) {
            // 背景远景星系和前景流星混杂
            stars.add(new Star(RANDOM.nextDouble() * width, RANDOM.nextDouble() * height,
                    0.5 + RANDOM.nextDouble() * 3.5, 0.15 + RANDOM.nextDouble() * 0.5));
        }
    }

    private void updateStars() {
        for (Star star : stars) {
            star.x -= star.size * 0.8; // 大星星(流星)移动更快产生纵深感
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

    private static final class Particle {
        double x, y, vx, vy, life, maxLife, size;
        Color color;
        Particle(double x, double y, double vx, double vy, double life, double size, Color color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; 
            this.life = life; this.maxLife = life; this.size = size; this.color = color;
        }
    }
}
