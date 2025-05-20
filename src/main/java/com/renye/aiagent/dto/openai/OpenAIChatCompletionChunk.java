package com.renye.aiagent.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
// import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// @JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIChatCompletionChunk {
    public String id;
    public String object = "chat.completion.chunk";
    public long created;
    public String model;
    public List<OpenAIStreamChoice> choices;
    @JsonProperty("system_fingerprint")
    public String systemFingerprint;
    public OpenAIUsage usage; // 仅在最后一个数据块且stream_options.include_usage=true时出现

    public OpenAIChatCompletionChunk() {}

    public OpenAIChatCompletionChunk(String id, long created, String model, List<OpenAIStreamChoice> choices, String systemFingerprint, OpenAIUsage usage) {
        this.id = id;
        this.object = "chat.completion.chunk";
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.systemFingerprint = systemFingerprint;
        this.usage = usage;
    }
}