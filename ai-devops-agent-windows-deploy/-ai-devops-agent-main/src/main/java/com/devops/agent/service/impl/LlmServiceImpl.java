package com.devops.agent.service.impl;

import com.devops.agent.service.LlmMessage;
import com.devops.agent.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final ObjectMapper objectMapper;

    public LlmServiceImpl(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.model}") String model,
            @Value("${llm.max-tokens}") int maxTokens,
            @Value("${llm.temperature}") double temperature) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return chatWithHistory(systemPrompt, List.of(
                LlmMessage.user(userMessage)
        ));
    }

    @Override
    public String chatWithHistory(String systemPrompt, List<LlmMessage> messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);

            ArrayNode messagesNode = objectMapper.createArrayNode();

            // system prompt
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messagesNode.add(createMessageNode("system", systemPrompt));
            }

            // conversation history
            for (LlmMessage msg : messages) {
                messagesNode.add(createMessageNode(msg.role(), msg.content()));
            }

            requestBody.set("messages", messagesNode);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "no body";
                    throw new RuntimeException("LLM API call failed: " + response.code() + " - " + body);
                }

                String responseBody = response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                JsonNode choices = jsonNode.get("choices");

                if (choices != null && choices.isArray() && choices.size() > 0) {
                    return choices.get(0).get("message").get("content").asText();
                }

                throw new RuntimeException("LLM response has no choices");
            }
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            throw new RuntimeException("LLM API error: " + e.getMessage(), e);
        }
    }

    private ObjectNode createMessageNode(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }
}
