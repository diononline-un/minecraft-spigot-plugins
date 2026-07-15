package me.diono.workernpcs.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import java.lang.reflect.Type;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class DeepSeekClient {

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public DeepSeekClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public CompletableFuture<AiResponse> chat(String personality, String context,
                                               String playerName, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doChat(personality, context, playerName, message);
            } catch (Exception e) {
                return new AiResponse("[Error talking to AI: " + e.getMessage() + "]", null, null);
            }
        });
    }

    private AiResponse doChat(String personality, String context,
                               String playerName, String message) throws IOException, InterruptedException {
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", personality + "\n\nCurrent situation:\n" + context
            + "\n\nRespond in JSON format: {\"message\": \"your response to the player\", "
            + "\"task\": \"follow|farm|breed|gohome|build|idle|null\", "
            + "\"task_params\": {\"structure\": \"house|wall|tower\", \"size\": \"WxHxD\", \"material\": \"block_type\"}}"
            + "\n\nAvailable tasks: follow (follow the player), farm (tend nearby crops), "
            + "breed (breed nearby animals), gohome (return to home), "
            + "build (build a structure - specify structure, size, material), idle (do nothing)."
            + "\nKeep your message short and friendly. Be a helpful Minecraft worker.");

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Player " + playerName + " says: " + message);

        JsonArray messages = new JsonArray();
        messages.add(systemMsg);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", 0.7);
        body.addProperty("max_tokens", 300);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .timeout(Duration.ofSeconds(45))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return new AiResponse("[AI service unavailable]", null, null);
        }

        JsonObject respObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray choices = respObj.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return new AiResponse("[No response from AI]", null, null);
        }

        String content = choices.get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content").getAsString();

        return parseResponse(content);
    }

    private AiResponse parseResponse(String content) {
        try {
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String message = json.has("message") ? json.get("message").getAsString() : content;
            String task = json.has("task") ? json.get("task").getAsString() : null;
            Map<String, Object> taskParams = null;
            if (json.has("task_params") && json.get("task_params").isJsonObject()) {
                taskParams = gson.fromJson(json.get("task_params"),
                    new TypeToken<Map<String, Object>>(){}.getType());
            }
            return new AiResponse(message, task, taskParams);
        } catch (JsonSyntaxException e) {
            return new AiResponse(content, null, null);
        }
    }
}
