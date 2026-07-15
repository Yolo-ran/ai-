package com.gesturegame.persistence;

import com.gesturegame.ai.LlmConfiguration;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 在本地 SQLite 中保存 LLM 配置；API Key 使用 AES-GCM 加密后落盘。 */
public final class LlmSettingsStore {

    private static final Logger LOGGER = Logger.getLogger(LlmSettingsStore.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final Path databasePath;
    private final Path secretPath;
    private volatile LlmConfiguration savedConfiguration;

    private LlmSettingsStore() {
        databasePath = UserAccountStore.resolveDatabasePath();
        secretPath = databasePath.resolveSibling("llm-secret.key");
        try {
            initialize();
            savedConfiguration = readStored();
        } catch (RuntimeException error) {
            LOGGER.log(Level.WARNING, "LLM 设置存储初始化失败，将继续使用环境变量或离线模式", error);
        }
    }

    public static LlmSettingsStore getInstance() {
        return Holder.INSTANCE;
    }

    public LlmConfiguration getForEditing() {
        LlmConfiguration saved = savedConfiguration;
        return saved == null ? LlmConfiguration.deepSeekDefaults() : saved;
    }

    /** 程序内配置优先；没有保存 Key 时兼容原有环境变量配置。 */
    public LlmConfiguration getEffective() {
        LlmConfiguration saved = savedConfiguration;
        if (saved != null && saved.isConfigured()) return saved;

        String key = firstNonBlank(System.getenv("LLM_API_KEY"), System.getenv("DEEPSEEK_API_KEY"));
        String url = firstNonBlank(System.getenv("LLM_API_URL"), System.getenv("DEEPSEEK_API_URL"),
                LlmConfiguration.DEEPSEEK_URL);
        String model = firstNonBlank(System.getenv("LLM_MODEL"), System.getenv("DEEPSEEK_MODEL"),
                LlmConfiguration.DEEPSEEK_MODEL);
        return new LlmConfiguration("环境变量", url, model, key);
    }

    public synchronized void save(LlmConfiguration configuration) {
        String validation = configuration.validationError();
        if (validation != null) throw new IllegalArgumentException(validation);

        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        byte[] cipherText = encrypt(configuration.apiKey(), iv);
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO llm_settings(id, provider, api_url, model, api_key_cipher, api_key_iv, updated_at) "
                             + "VALUES(1, ?, ?, ?, ?, ?, ?) "
                             + "ON CONFLICT(id) DO UPDATE SET provider=excluded.provider, api_url=excluded.api_url, "
                             + "model=excluded.model, api_key_cipher=excluded.api_key_cipher, "
                             + "api_key_iv=excluded.api_key_iv, updated_at=excluded.updated_at")) {
            statement.setString(1, configuration.provider());
            statement.setString(2, configuration.apiUrl());
            statement.setString(3, configuration.model());
            statement.setBytes(4, cipherText);
            statement.setBytes(5, iv);
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
            savedConfiguration = configuration;
        } catch (SQLException error) {
            throw new IllegalStateException("无法保存 API 设置", error);
        } finally {
            Arrays.fill(cipherText, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    public synchronized void clear() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM llm_settings WHERE id = 1");
            savedConfiguration = null;
        } catch (SQLException error) {
            throw new IllegalStateException("无法清除 API 设置", error);
        }
    }

    private void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
            Class.forName("org.sqlite.JDBC");
            ensureSecretKey();
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS llm_settings ("
                        + "id INTEGER PRIMARY KEY CHECK(id = 1),"
                        + "provider TEXT NOT NULL,"
                        + "api_url TEXT NOT NULL,"
                        + "model TEXT NOT NULL,"
                        + "api_key_cipher BLOB NOT NULL,"
                        + "api_key_iv BLOB NOT NULL,"
                        + "updated_at TEXT NOT NULL)");
            }
        } catch (IOException | SQLException | ClassNotFoundException error) {
            throw new IllegalStateException("无法初始化 API 设置存储", error);
        }
    }

    private LlmConfiguration readStored() {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT provider, api_url, model, api_key_cipher, api_key_iv FROM llm_settings WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) return null;
            String apiKey = decrypt(result.getBytes("api_key_cipher"), result.getBytes("api_key_iv"));
            return new LlmConfiguration(result.getString("provider"), result.getString("api_url"),
                    result.getString("model"), apiKey);
        } catch (SQLException error) {
            throw new IllegalStateException("无法读取 API 设置", error);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=3000");
        }
        return connection;
    }

    private byte[] encrypt(String value, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Files.readAllBytes(secretPath), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("无法加密 API Key", error);
        }
    }

    private String decrypt(byte[] cipherText, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Files.readAllBytes(secretPath), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("无法解密已保存的 API Key", error);
        }
    }

    private void ensureSecretKey() throws IOException {
        if (Files.exists(secretPath) && Files.size(secretPath) == 32) return;
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        Files.write(secretPath, key, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Arrays.fill(key, (byte) 0);
        try {
            Files.setAttribute(secretPath, "dos:hidden", true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // 非 Windows 文件系统无需隐藏属性。
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return "";
    }

    private static final class Holder {
        private static final LlmSettingsStore INSTANCE = new LlmSettingsStore();
    }
}
