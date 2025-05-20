package com.renye.aiagent.dto.openai;

// import com.fasterxml.jackson.annotation.JsonInclude; // 如果需要更精细控制null字段

// @JsonInclude(JsonInclude.Include.NON_NULL) // 只序列化非null字段 (可选)
public class OpenAIDelta {
    public String role;
    public String content;
    // 如果支持tool_calls, 在这里添加: public List<ToolCall> tool_calls;

    public OpenAIDelta() {}

    public OpenAIDelta(String role, String content) {
        this.role = role;
        this.content = content;
    }
}