package com.gesturegame.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 猜拳 AI 对手台词生成器。
 * 每局判定后异步调用 LLM 生成一句嘲讽或鼓励的话；
 * API 不可用时从本地台词库随机选取兜底。
 */
public final class OpponentAI {

    private static final Logger LOGGER = Logger.getLogger(OpponentAI.class.getName());
    private static final Random RANDOM = new Random();

    private static final String DEFAULT_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final String API_KEY = firstNonBlank(
            System.getenv("DEEPSEEK_API_KEY"),
            System.getenv("LLM_API_KEY"));

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final String[] LOCAL_TAUNT = {
        "嘿，我闭着眼睛都能赢你！",
        "你这手气，要不要去庙里拜拜？",
        "我已经看穿你的套路了。",
        "再练练吧，小朋友。",
        "就这？",
        "我奶奶都比你出得好。"
    };

    private static final String[] LOCAL_ENCOURAGE = {
        "漂亮！这局你确实厉害。",
        "不错嘛，继续加油！",
        "你越来越强了，我有点慌了。",
        "看来你找到感觉了！",
        "厉害厉害，这局我心服口服。",
        "好拳！值得鼓掌。"
    };

    private static final String[] LOCAL_DRAW = {
        "平局…再来一局决胜负？",
        "心有灵犀啊，都出一样的。",
        "不分上下，有意思。",
        "居然平了，下一把可不会了！"
    };

    private OpponentAI() {}

    /**
     * 异步获取对手台词。
     * @param outcome 结果：赢/输/平
     * @param playerGesture 玩家手势
     * @param cpuGesture    电脑手势
     * @param playerScore   玩家当前得分
     * @param cpuScore      电脑当前得分
     * @param callback      回调，在主线程更新 UI
     */
    public static void getLine(String outcome, String playerGesture, String cpuGesture,
                                int playerScore, int cpuScore, Consumer<String> callback) {
        // 先给本地兜底，秒回
        String local = localLine(outcome);
        if (callback != null) callback.accept(local);

        // 异步调 LLM，结果到了覆盖
        if (API_KEY == null) return;

        String prompt = String.format(
            "玩家出了%s，电脑出了%s，结果：玩家%s。当前比分 %d:%d。",
            playerGesture, cpuGesture, outcome, playerScore, cpuScore);
        String tone = outcome.contains("赢") ? "夸赞、敬佩" : outcome.equals("平局") ? "不服气、挑衅" : "嘲讽、戏谑";

        CompletableFuture.supplyAsync(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", DEFAULT_MODEL);
                body.put("temperature", 0.9);
                body.put("max_tokens", 80);

                JSONArray msgs = new JSONArray();
                msgs.put(new JSONObject().put("role", "system")
                        .put("content", "你是剪刀石头布游戏的AI对手。用" + tone + "的口吻说一句话，不超过20个字，要像真人在说话。"));
                msgs.put(new JSONObject().put("role", "user").put("content", prompt));
                body.put("messages", msgs);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(DEFAULT_URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + API_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .timeout(Duration.ofSeconds(6))
                        .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String text = new JSONObject(resp.body())
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content").trim();
                    if (!text.isBlank()) return text;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "LLM对手台词失败，用本地兜底", e);
            }
            return local;
        }).thenAccept(llmLine -> {
            if (callback != null && !llmLine.equals(local)) callback.accept(llmLine);
        });
    }

    private static String localLine(String outcome) {
        if (outcome.contains("赢")) return LOCAL_ENCOURAGE[RANDOM.nextInt(LOCAL_ENCOURAGE.length)];
        if (outcome.equals("平局")) return LOCAL_DRAW[RANDOM.nextInt(LOCAL_DRAW.length)];
        return LOCAL_TAUNT[RANDOM.nextInt(LOCAL_TAUNT.length)];
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
