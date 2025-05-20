package com.renye.aiagent.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OpenAIChoice {
    public int index;
    public OpenAIMessage message; // 使用上面定义的 OpenAIMessage
    @JsonProperty("finish_reason")
    public String finishReason;

    public OpenAIChoice() {}

    public OpenAIChoice(int index, OpenAIMessage message, String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }
}