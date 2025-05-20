package com.renye.aiagent.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OpenAIUsage {
    @JsonProperty("prompt_tokens")
    public long promptTokens;
    @JsonProperty("completion_tokens")
    public long completionTokens;
    @JsonProperty("total_tokens")
    public long totalTokens;

    public OpenAIUsage() {}

    public OpenAIUsage(long promptTokens, long completionTokens, long totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }
}