package com.renye.aiagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty; // 用于字段名映射
import java.util.List;
import java.util.Map;

// 如果你的 DTO 类使用了 Lombok，可以省略下面的构造函数、getter 和 setter
// import lombok.Data;
// @Data
public class OpenAIChatRequest {

    private String model;
    private List<Message> messages;
    private Double temperature; // 对于可能带小数的数字，使用 Double

    @JsonProperty("top_p") // 将 JSON 中的 "top_p" 映射到 Java 字段 "topP"
    private Double topP;
    static class StreamOptions { // 新增
        public Boolean include_usage;
    }

    static class OpenAIChatRequestMessage {
        public String role;
        public Object content; // content 可以是 String 或 List<Map<String, String>> (用于多模态)
    }
    private Integer n;
    private Boolean stream;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("logit_bias")
    private Map<String, Integer> logitBias;

    private String user;

    // Jackson 需要一个无参构造函数来进行反序列化
    public OpenAIChatRequest() {}

    // 所有字段的 Getters 和 Setters
    // (如果使用 Lombok 的 @Data 或 @Getter/@Setter，可以省略这些)
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public Integer getN() { return n; }
    public void setN(Integer n) { this.n = n; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }
    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
    public Map<String, Integer> getLogitBias() { return logitBias; }
    public void setLogitBias(Map<String, Integer> logitBias) { this.logitBias = logitBias; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    // 内部类，用于表示 messages 数组中的每个消息对象
    // @Data // Lombok
    public static class Message {
        private String role;
        private Object content; // 对于支持图像输入的模型，content 可以是字符串或对象数组
        private String name;    // 可选字段

        public Message() {}

        // Getters and Setters
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public Object getContent() { return content; }
        public void setContent(Object content) { this.content = content; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @Override
        public String toString() {
            return "Message{" + "role='" + role + '\'' + ", content=" + content + ", name='" + name + '\'' + '}';
        }
    }

    @Override
    public String toString() {
        return "OpenAIChatRequest{" +
                "model='" + model + '\'' +
                ", messages=" + messages +
                // ... 其他字段 ...
                '}';
    }
}