package com.gesturegame.ai;

import com.gesturegame.common.Difficulty;
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

/** DeepSeek/国内兼容接口优先、离线规则兜底的关卡生成服务。 */
public final class AdaptiveShooterLevelService {

    private static final Logger LOGGER = Logger.getLogger(AdaptiveShooterLevelService.class.getName());
    private static final String DEFAULT_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v4-pro";

    private final LocalShooterLevelGenerator fallback = new LocalShooterLevelGenerator();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public ShooterLevelConfig firstLevel(Difficulty difficulty) {
        return fallback.generate(1, difficulty, null);
    }

    public ShooterLevelConfig localLevel(int level, Difficulty difficulty, PlayerPerformance performance) {
        return fallback.generate(level, difficulty, performance);
    }

    public CompletableFuture<ShooterLevelConfig> generateNext(
            int level, Difficulty difficulty, PlayerPerformance performance) {
        ShooterLevelConfig local = fallback.generate(level, difficulty, performance);
        String apiKey = firstNonBlank(System.getenv("LLM_API_KEY"), System.getenv("DEEPSEEK_API_KEY"));
        if (apiKey == null) {
            return CompletableFuture.completedFuture(local);
        }

        String url = firstNonBlank(System.getenv("LLM_API_URL"), System.getenv("DEEPSEEK_API_URL"), DEFAULT_URL);
        String model = firstNonBlank(System.getenv("LLM_MODEL"), System.getenv("DEEPSEEK_MODEL"), DEFAULT_MODEL);
        try {
            JSONObject body = buildRequest(model, level, difficulty, performance);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parseResponse(response, level, model))
                    .exceptionally(error -> {
                        LOGGER.log(Level.WARNING, "[AI关卡] 接口不可用，已切换本地生成: " + error.getMessage());
                        return local;
                    });
        } catch (RuntimeException error) {
            LOGGER.log(Level.WARNING, "[AI关卡] 配置无效，已切换本地生成", error);
            return CompletableFuture.completedFuture(local);
        }
    }

    private JSONObject buildRequest(String model, int level, Difficulty difficulty,
                                    PlayerPerformance performance) {
        String schemaExample = "{\"title\":\"星云封锁线\",\"durationSeconds\":42,"
                + "\"playerHp\":5,\"enemyFireIntervalFrames\":80,\"bossHp\":55,\"waves\":["
                + "{\"startSecond\":2,\"count\":5,\"hp\":2,\"speed\":2.5,"
                + "\"formation\":\"LINE\",\"shooter\":false}]}";
        String system = "你是横版太空射击游戏的关卡设计器。只输出合法 JSON，不要 Markdown。"
                + "必须包含 title、durationSeconds、playerHp、enemyFireIntervalFrames、bossHp、waves。"
                + "waves 必须有 3 到 8 项；formation 只能是 LINE、ZIGZAG、RANDOM。"
                + "根据玩家表现温和调节，不能让相邻关卡难度突变。示例 JSON：" + schemaExample;
        JSONObject userData = new JSONObject()
                .put("nextLevel", level)
                .put("difficulty", difficulty.name())
                .put("performance", performance == null ? JSONObject.NULL : performance.toJson());
        return new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", system))
                        .put(new JSONObject().put("role", "user")
                                .put("content", "请根据这些数据生成下一关 JSON：" + userData)))
                .put("response_format", new JSONObject().put("type", "json_object"))
                .put("max_tokens", 900)
                .put("stream", false);
    }

    private ShooterLevelConfig parseResponse(HttpResponse<String> response, int level, String model) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        JSONObject envelope = new JSONObject(response.body());
        JSONArray choices = envelope.optJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("接口未返回 choices");
        }
        String content = choices.getJSONObject(0).getJSONObject("message").optString("content", "").strip();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("接口返回了空 JSON");
        }
        return ShooterLevelConfig.fromJson(new JSONObject(content), level, "AI · " + model);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return null;
    }
}
