package com.renye.aiagent.dto.openai;

public class OpenAIMessage {
    public String role;
    public Object content; // content可以是String (文本), 或 List<Map<String, Object>> (多模态)

    public OpenAIMessage() {}

    public OpenAIMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }
}