package com.gesturegame.ai;

import java.net.URI;

/** 用户可编辑的 OpenAI Chat Completions 兼容接口配置。 */
public record LlmConfiguration(String provider, String apiUrl, String model, String apiKey) {

    public static final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";
    public static final String DEEPSEEK_MODEL = "deepseek-chat";

    public LlmConfiguration {
        provider = clean(provider, "DeepSeek");
        apiUrl = clean(apiUrl, DEEPSEEK_URL);
        model = clean(model, DEEPSEEK_MODEL);
        apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public static LlmConfiguration deepSeekDefaults() {
        return new LlmConfiguration("DeepSeek", DEEPSEEK_URL, DEEPSEEK_MODEL, "");
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String validationError() {
        if (apiKey.isBlank()) return "请填写 API Key";
        if (model.isBlank()) return "请填写模型名称";
        try {
            URI uri = URI.create(apiUrl);
            if (uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return "接口地址必须是完整的 http/https URL";
            }
        } catch (IllegalArgumentException error) {
            return "接口地址格式不正确";
        }
        return null;
    }

    public String maskedKey() {
        if (apiKey.length() <= 8) return "••••••••";
        return apiKey.substring(0, 3) + "••••••••" + apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public String toString() {
        return provider + " / " + model + " / " + maskedKey();
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
