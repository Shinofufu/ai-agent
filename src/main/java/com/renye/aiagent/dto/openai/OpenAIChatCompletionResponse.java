package com.renye.aiagent.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpenAIChatCompletionResponse {
    public String id;
    public String object = "chat.completion"; // 通常固定
    public long created;
    public String model;
    public List<OpenAIChoice> choices;
    public OpenAIUsage usage;
    @JsonProperty("system_fingerprint")
    public String systemFingerprint;

    public OpenAIChatCompletionResponse() {}

    public OpenAIChatCompletionResponse(String id, long created, String model, List<OpenAIChoice> choices, OpenAIUsage usage, String systemFingerprint) {
        this.id = id;
        this.object = "chat.completion";
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
        this.systemFingerprint = systemFingerprint;
    }
}