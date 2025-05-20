package com.renye.aiagent.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
// import com.fasterxml.jackson.annotation.JsonInclude;

// @JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAIStreamChoice {
    public int index;
    public OpenAIDelta delta;
    @JsonProperty("finish_reason")
    public String finishReason;
    // public Integer logprobs; // 如果需要logprobs

    public OpenAIStreamChoice() {}

    public OpenAIStreamChoice(int index, OpenAIDelta delta, String finishReason) {
        this.index = index;
        this.delta = delta;
        this.finishReason = finishReason;
    }
}