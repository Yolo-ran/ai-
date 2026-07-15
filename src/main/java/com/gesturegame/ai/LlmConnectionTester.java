package com.gesturegame.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** 使用最小请求测试用户填写的 Chat Completions 兼容接口。 */
public final class LlmConnectionTester {

    private static final HttpClient HTTP = LlmHttpClientFactory.create(Duration.ofSeconds(5));

    private LlmConnectionTester() {}

    public static CompletableFuture<TestResult> test(LlmConfiguration configuration) {
        String validation = configuration.validationError();
        if (validation != null) return CompletableFuture.completedFuture(new TestResult(false, validation));

        JSONObject body = new JSONObject()
                .put("model", configuration.model())
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user").put("content", "只回复 OK")))
                .put("max_tokens", 8)
                .put("stream", false);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(configuration.apiUrl()))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + configuration.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(new TestResult(false, "接口地址格式不正确"));
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return new TestResult(false, "连接失败：HTTP " + response.statusCode());
                    }
                    try {
                        JSONArray choices = new JSONObject(response.body()).optJSONArray("choices");
                        if (choices == null || choices.isEmpty()) {
                            return new TestResult(false, "接口已响应，但不是兼容的 Chat Completions 格式");
                        }
                        return new TestResult(true, "连接成功，模型可用");
                    } catch (RuntimeException error) {
                        return new TestResult(false, "接口返回内容无法解析");
                    }
                })
                .exceptionally(error -> new TestResult(false,
                        "连接失败：" + readableMessage(error)));
    }

    private static String readableMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    public record TestResult(boolean success, String message) {}
}
