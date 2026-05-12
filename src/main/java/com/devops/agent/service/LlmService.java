package com.devops.agent.service;

public interface LlmService {
    /**
     * Send a prompt to the LLM and return the raw text response.
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Send a prompt with conversation history for multi-turn reasoning.
     */
    String chatWithHistory(String systemPrompt, java.util.List<LlmMessage> messages);
}
