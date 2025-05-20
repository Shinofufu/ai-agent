//package com.renye.aiagent.demo.invoke;
//
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.chat.model.Generation;
//import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
//import org.springframework.ai.chat.metadata.Usage; // Spring AI Usage
//import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.ai.chat.prompt.PromptTemplate;
//// 假设 dashScopeChatModel 是 org.springframework.ai.chat.model.ChatModel 的一个实例
//// import org.springframework.ai.dashscope.DashScopeChatModel; // 如果您直接使用
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.ai.converter.BeanOutputConverter;
//
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//
//public class DemoLoveAdviceWithOpenAiConversion {
//    // --- OpenAI 响应格式的 DTOs ---
//    // 为了简洁，我们将它们作为静态内部类放在这里
//    static class OpenAiMessage {
//        public String role;
//        public String content;
//        public OpenAiMessage(String role, String content) { this.role = role; this.content = content; }
//    }
//    static class OpenAiDelta {
//        public String role; // 通常只在第一个数据块的delta中出现
//        public String content; // 文本内容的增量
//        // 如果支持 tool_calls 或 function_call，这里还需要相应的字段
//
//        public OpenAiDelta() {}
//        public OpenAiDelta(String role, String content) {
//            this.role = role;
//            this.content = content;
//        }
//    }
//
//    // 用于流式响应中 "choices" 数组的元素
//    static class OpenAiStreamChoice {
//        public int index;
//        public OpenAiDelta delta;
//        @JsonProperty("finish_reason")
//        public String finishReason; // 在最后一个数据块的choice中为 "stop", "length" 等
//
//        public OpenAiStreamChoice(int index, OpenAiDelta delta, String finishReason) {
//            this.index = index;
//            this.delta = delta;
//            this.finishReason = finishReason;
//        }
//    }
//
//    // 流式响应的每个数据块 (chunk)
//    static class OpenAiChatCompletionChunk {
//        public String id; // 所有相关数据块共享相同的ID
//        public String object = "chat.completion.chunk"; // 固定值
//        public long created; // 所有相关数据块共享相同的创建时间戳
//        public String model;
//        public List<OpenAiStreamChoice> choices;
//        public OpenAiUsage usage; // 只有在最后一个数据块且 stream_options.include_usage=true 时出现
//        @JsonProperty("system_fingerprint")
//        public String systemFingerprint; // 可选
//
//        public OpenAiChatCompletionChunk(String id, long created, String model, List<OpenAiStreamChoice> choices, OpenAiUsage usage, String systemFingerprint) {
//            this.id = id;
//            this.created = created;
//            this.model = model;
//            this.choices = choices;
//            this.usage = usage;
//            this.systemFingerprint = systemFingerprint;
//        }
//    }
//    static class OpenAiChoice {
//        public int index;
//        public OpenAiMessage message;
//        @JsonProperty("finish_reason")
//        public String finishReason;
//        public OpenAiChoice(int index, OpenAiMessage message, String finishReason) {
//            this.index = index; this.message = message; this.finishReason = finishReason;
//        }
//    }
//
//    static class OpenAiUsage {
//        @JsonProperty("prompt_tokens")
//        public long promptTokens;
//        @JsonProperty("completion_tokens")
//        public long completionTokens;
//        @JsonProperty("total_tokens")
//        public long totalTokens;
//        public OpenAiUsage(long pt, long ct, long tt) {
//            this.promptTokens = pt; this.completionTokens = ct; this.totalTokens = tt;
//        }
//    }
//
//    static class OpenAiChatCompletionResponse {
//        public String id;
//        public String object;
//        public long created;
//        public String model;
//        public List<OpenAiChoice> choices;
//        public OpenAiUsage usage;
//        @JsonProperty("system_fingerprint")
//        public String systemFingerprint;
//
//        public OpenAiChatCompletionResponse(String id, String object, long created, String model,
//                                            List<OpenAiChoice> choices, OpenAiUsage usage, String sf) {
//            this.id = id; this.object = object; this.created = created; this.model = model;
//            this.choices = choices; this.usage = usage; this.systemFingerprint = sf;
//        }
//    }
//
//    // --- 将 Spring AI ChatResponse 转换为 OpenAI JSON 的工具类 ---
//    public static class OpenAiResponseConverter {
//        private static final ObjectMapper objectMapper = new ObjectMapper();
//
//        public static String convert(ChatResponse springAiChatResponse, String requestedModelName) {
//            if (springAiChatResponse == null) {
//                return "{\"error\": \"ChatResponse is null\"}";
//            }
//
//            List<OpenAiChoice> openAiChoices = new ArrayList<>();
//            OpenAiUsage openAiUsage = null;
//            String systemFingerprint = null; // 您可以从 springAiChatResponse.getMetadata() 尝试获取
//
//            List<Generation> generations = springAiChatResponse.getResults();
//            if (generations != null && !generations.isEmpty()) {
//                for (int i = 0; i < generations.size(); i++) {
//                    Generation generation = generations.get(i);
//                    String content = generation.getOutput().getContent(); // 使用 .getContent()
//                    String role = "assistant";
//                    String finishReason = "stop"; // 默认
//                    Usage springAiUsage = null;
//
//                    ChatGenerationMetadata genMetadata = generation.getMetadata();
//                    if (genMetadata != null) {
//                        if (genMetadata.getFinishReason() != null) {
//                            finishReason = genMetadata.getFinishReason().toLowerCase();
//                        }
//                        springAiUsage = springAiChatResponse.getMetadata().getUsage();
//                    }
//
//                    openAiChoices.add(new OpenAiChoice(i, new OpenAiMessage(role, content), finishReason));
//
//                    if (i == 0 && springAiUsage != null) { // 取第一个 generation 的 usage
//                        openAiUsage = new OpenAiUsage(
//                                springAiUsage.getPromptTokens(),
//                                springAiUsage.getGenerationTokens(), // Spring AI 使用 generationTokens
//                                springAiUsage.getTotalTokens()
//                        );
//                    }
//                }
//            } else {
//                openAiChoices.add(new OpenAiChoice(0, new OpenAiMessage("assistant", ""), "error_no_generation"));
//            }
//
//            // 如果顶层元数据有Usage且前面没获取到，可以尝试使用
//            if (openAiUsage == null && springAiChatResponse.getMetadata() != null && springAiChatResponse.getMetadata().getUsage() != null) {
//                Usage overallUsage = springAiChatResponse.getMetadata().getUsage();
//                openAiUsage = new OpenAiUsage(
//                        overallUsage.getPromptTokens(),
//                        overallUsage.getGenerationTokens(),
//                        overallUsage.getTotalTokens()
//                );
//            }
//            // 尝试从ChatResponse元数据获取system_fingerprint (示例, key可能不同)
//            // if (springAiChatResponse.getMetadata() != null && springAiChatResponse.getMetadata().get("system_fingerprint") != null) {
//            //     systemFingerprint = springAiChatResponse.getMetadata().get("system_fingerprint").toString();
//            // }
//
//
//            OpenAiChatCompletionResponse openAiResponse = new OpenAiChatCompletionResponse(
//                    "chatcmpl-" + UUID.randomUUID().toString(),
//                    "chat.completion",
//                    Instant.now().getEpochSecond(),
//                    requestedModelName, // 使用传入的模型名
//                    openAiChoices,
//                    openAiUsage,
//                    systemFingerprint
//            );
//
//            try {
//                // objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(openAiResponse) // 漂亮打印
//                return objectMapper.writeValueAsString(openAiResponse);
//            } catch (JsonProcessingException e) {
//                e.printStackTrace();
//                return "{\"error\": \"Failed to serialize to OpenAI JSON: " + e.getMessage() + "\"}";
//            }
//        }
//    }
//
//}