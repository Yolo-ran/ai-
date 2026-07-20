package com.gesturegame.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 排行榜存储：无尽模式分数、连击记录。
 * 数据库文件放在 data/leaderboard.db
 */
public final class LeaderboardStore {

    private final String dbPath;

    public LeaderboardStore() {
        this.dbPath = UserAccountStore.resolveDatabasePath().getParent().resolve("leaderboard.db").toString();
        initialize();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private void initialize() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("""
                CREATE TABLE IF NOT EXISTS leaderboard (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    game        TEXT    NOT NULL,
                    difficulty  TEXT    NOT NULL DEFAULT 'ENDLESS',
                    username    TEXT    NOT NULL,
                    score       INTEGER NOT NULL,
                    max_combo   INTEGER NOT NULL DEFAULT 0,
                    played_at   TEXT    NOT NULL
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_game ON leaderboard(game, difficulty, score DESC)");
        } catch (SQLException e) {
            System.err.println("[Leaderboard] init error: " + e.getMessage());
        }
    }

    /** 保存一条无尽模式记录 */
    public void saveScore(String game, String difficulty, String username, int score, int maxCombo) {
        String sql = "INSERT INTO leaderboard (game, difficulty, username, score, max_combo, played_at) VALUES (?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, game);
            ps.setString(2, difficulty);
            ps.setString(3, username);
            ps.setInt(4, score);
            ps.setInt(5, maxCombo);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[Leaderboard] save error: " + e.getMessage());
        }
    }

    /** 获取某游戏某难度的排行榜前 N 名 */
    public List<LeaderboardEntry> getTop(String game, String difficulty, int limit) {
        List<LeaderboardEntry> list = new ArrayList<>();
        String sql = "SELECT username, score, max_combo, played_at FROM leaderboard WHERE game=? AND difficulty=? ORDER BY score DESC LIMIT ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, game);
            ps.setString(2, difficulty);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                            rs.getString("username"),
                            rs.getInt("score"),
                            rs.getInt("max_combo"),
                            rs.getString("played_at")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[Leaderboard] query error: " + e.getMessage());
        }
        return list;
    }

    public record LeaderboardEntry(String username, int score, int maxCombo, String playedAt) {}
}
