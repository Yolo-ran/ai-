package com.gesturegame.ai;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 为 LLM 请求创建 HTTPS 客户端；Windows 优先使用系统根证书库。 */
public final class LlmHttpClientFactory {

    private static final Logger LOGGER = Logger.getLogger(LlmHttpClientFactory.class.getName());
    private static final SSLContext SSL_CONTEXT = createSslContext();

    private LlmHttpClientFactory() {}

    public static HttpClient create(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .sslContext(SSL_CONTEXT)
                .build();
    }

    private static SSLContext createSslContext() {
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                KeyStore windowsRoots = KeyStore.getInstance("Windows-ROOT");
                windowsRoots.load(null, null);
                if (windowsRoots.size() > 0) {
                    int rootCount = windowsRoots.size();
                    TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    trustManagers.init(windowsRoots);
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(null, trustManagers.getTrustManagers(), new SecureRandom());
                    LOGGER.info(() -> "LLM HTTPS 已加载 Windows 系统根证书: " + rootCount);
                    return context;
                }
            }
            return SSLContext.getDefault();
        } catch (Exception error) {
            LOGGER.log(Level.WARNING, "无法加载系统根证书，回退 Java 默认 HTTPS 配置", error);
            try {
                return SSLContext.getDefault();
            } catch (Exception fallbackError) {
                throw new IllegalStateException("当前 Java 环境无法初始化 HTTPS", fallbackError);
            }
        }
    }
}
