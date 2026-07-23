package com.gesturegame.persistence;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;

/** 本地 SQLite 账户仓库，密码仅以 PBKDF2 加盐摘要形式保存。 */
public final class UserAccountStore {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Path databasePath;

    public UserAccountStore() {
        this(resolveDatabasePath());
    }

    UserAccountStore(Path databasePath) {
        this.databasePath = databasePath;
        initialize();
    }

    public boolean hasUsers() {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM users LIMIT 1)")) {
            return result.next() && result.getInt(1) == 1;
        } catch (SQLException error) {
            throw new IllegalStateException("无法读取本地账户数据库", error);
        }
    }

    public RegistrationResult register(String rawUsername, char[] password) {
        String username = normalizeUsername(rawUsername);
        String validation = validate(username, password);
        if (validation != null) {
            Arrays.fill(password, '\0');
            return new RegistrationResult(false, validation);
        }

        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = hash(password, salt);
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users(username, password_salt, password_hash, created_at) VALUES(?, ?, ?, ?)")) {
            statement.setString(1, username);
            statement.setBytes(2, salt);
            statement.setBytes(3, hash);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
            return new RegistrationResult(true, "账户已创建");
        } catch (SQLException error) {
            if (error.getMessage() != null && error.getMessage().toLowerCase().contains("unique")) {
                return new RegistrationResult(false, "这个用户名已经存在");
            }
            throw new IllegalStateException("无法写入本地账户数据库", error);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(hash, (byte) 0);
            Arrays.fill(password, '\0');
        }
    }

    public boolean authenticate(String rawUsername, char[] password) {
        String username = normalizeUsername(rawUsername);
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_salt, password_hash FROM users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return false;
                byte[] salt = result.getBytes("password_salt");
                byte[] expected = result.getBytes("password_hash");
                byte[] actual = hash(password, salt);
                boolean matches = MessageDigest.isEqual(expected, actual);
                Arrays.fill(actual, (byte) 0);
                if (matches) updateLastLogin(connection, username);
                return matches;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("无法验证本地账户", error);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    private void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("CREATE TABLE IF NOT EXISTS users ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "username TEXT NOT NULL UNIQUE,"
                        + "password_salt BLOB NOT NULL,"
                        + "password_hash BLOB NOT NULL,"
                        + "created_at TEXT NOT NULL,"
                        + "last_login_at TEXT)");
            }
        } catch (IOException | SQLException | ClassNotFoundException error) {
            throw new IllegalStateException("无法初始化本地账户数据库: " + databasePath, error);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=3000");
        }
        return connection;
    }

    private static void updateLastLogin(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET last_login_at = ? WHERE username = ?")) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }

    private static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("当前 Java 环境不支持安全密码摘要", error);
        } finally {
            spec.clearPassword();
        }
    }

    private static String validate(String username, char[] password) {
        if (!username.matches("[A-Za-z0-9_\u4e00-\u9fa5]{3,24}")) {
            return "用户名需为 3–24 位中文、字母、数字或下划线";
        }
        if (password.length < 8 || password.length > 72) {
            return "密码长度需为 8–72 位";
        }
        return null;
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.strip();
    }

    static Path resolveDatabasePath() {
        String override = System.getProperty("gesturegame.data.dir");
        Path dataDirectory;
        if (override != null && !override.isBlank()) {
            dataDirectory = Path.of(override);
        } else {
            String localAppData = System.getenv("LOCALAPPDATA");
            dataDirectory = localAppData != null && !localAppData.isBlank()
                    ? Path.of(System.getProperty("user.dir"), "data")
                    : Path.of(System.getProperty("user.dir"), "data");
        }
        return dataDirectory.resolve("users.db");
    }

    public record RegistrationResult(boolean success, String message) {}
}
