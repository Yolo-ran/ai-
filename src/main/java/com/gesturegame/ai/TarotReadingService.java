package com.gesturegame.ai;

import com.gesturegame.persistence.LlmSettingsStore;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** DeepSeek/兼容接口增强三牌占读；接口不可用时始终返回本地解读。 */
public final class TarotReadingService {

    private static final Logger LOGGER = Logger.getLogger(TarotReadingService.class.getName());
    private static final HttpClient HTTP = LlmHttpClientFactory.create(Duration.ofSeconds(4));

    private TarotReadingService() { }

    public static CompletableFuture<ReadingResult> generate(
            String question,
            String cardContext,
            String fallbackInterpretation,
            String fallbackAdvice) {
        ReadingResult fallback = new ReadingResult(fallbackInterpretation, fallbackAdvice, "本地牌义");
        LlmConfiguration configuration = LlmSettingsStore.getInstance().getEffective();
        if (!configuration.isConfigured()) {
            return CompletableFuture.completedFuture(fallback);
        }

        try {
            JSONObject body = new JSONObject()
                    .put("model", configuration.model())
                    .put("temperature", 0.72)
                    .put("max_tokens", 600)
                    .put("stream", false)
                    .put("response_format", new JSONObject().put("type", "json_object"))
                    .put("messages", new JSONArray()
                            .put(new JSONObject().put("role", "system").put("content",
                                    "你是一名克制、清晰、负责任的塔罗解读者。"
                                            + "根据过去、现在、未来三张牌回答问题，不做绝对预言，不渲染恐惧。"
                                            + "只输出合法JSON，字段必须为interpretation和advice。"
                                            + "interpretation为160至220字的综合解读；"
                                            + "advice必须正好三条、每条不超过45字，使用换行分隔。"))
                            .put(new JSONObject().put("role", "user").put("content",
                                    "问题：" + question + "\n牌阵：\n" + cardContext)));

            HttpRequest request = HttpRequest.newBuilder(URI.create(configuration.apiUrl()))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + configuration.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parse(response, fallback, configuration.model()))
                    .exceptionally(error -> {
                        LOGGER.log(Level.FINE, "塔罗解读接口不可用，保留本地牌义", error);
                        return fallback;
                    });
        } catch (RuntimeException error) {
            LOGGER.log(Level.FINE, "塔罗解读配置无效，保留本地牌义", error);
            return CompletableFuture.completedFuture(fallback);
        }
    }

    private static ReadingResult parse(
            HttpResponse<String> response, ReadingResult fallback, String model) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return fallback;
        }
        try {
            String content = new JSONObject(response.body())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").strip();
            JSONObject result = new JSONObject(content);
            String interpretation = result.optString("interpretation", "").strip();
            String advice = result.optString("advice", "").strip();
            if (interpretation.isEmpty() || advice.isEmpty()) {
                return fallback;
            }
            return new ReadingResult(interpretation, advice, "AI · " + model);
        } catch (RuntimeException error) {
            LOGGER.log(Level.FINE, "塔罗解读响应格式无效，保留本地牌义", error);
            return fallback;
        }
    }

    public record ReadingResult(String interpretation, String advice, String source) { }
}
