package com.renye.aiagent.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renye.aiagent.dto.openai.*; // 导入所有OpenAI DTOs
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage; // Spring AI Usage
import org.springframework.stereotype.Component; // 或者只是静态方法，取决于您是否需要注入

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component // 如果您想通过@Autowired注入它，否则可以移除并使用静态方法
public class OpenAIResponseFormatter {

    private static final Logger log = LoggerFactory.getLogger(OpenAIResponseFormatter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Set<String> VALID_OPENAI_FINISH_REASONS = Set.of("stop", "length", "tool_calls", "content_filter", "function_call");

    /**
     * 将Spring AI的ChatResponse对象转换为OpenAI API兼容的【非流式】JSON字符串。
     */
    public String formatToNonStreamingResponse(ChatResponse springAiChatResponse, String requestedModelName) {
        if (springAiChatResponse == null) {
            log.error("Input ChatResponse is null for non-streaming conversion.");
            return "{\"error\": \"Input ChatResponse is null\"}";
        }

        List<OpenAIChoice> openAiChoices = new ArrayList<>();
        OpenAIUsage openAiUsage = null;
        String systemFingerprint = null; // 尝试从元数据获取

        List<Generation> generations = springAiChatResponse.getResults();
        if (generations != null && !generations.isEmpty()) {
            for (int i = 0; i < generations.size(); i++) {
                Generation generation = generations.get(i);
                String content = generation.getOutput().getContent();
                String role = "assistant";
                String finishReason = "stop"; // 默认
                Usage springAiGenerationUsage = null;

                ChatGenerationMetadata genMetadata = generation.getMetadata();
                if (genMetadata != null) {
                    if (genMetadata.getFinishReason() != null) {
                        finishReason = genMetadata.getFinishReason().toLowerCase(); // **确保小写**
                        if (!VALID_OPENAI_FINISH_REASONS.contains(finishReason)) {
                            log.warn("从LLM收到未知的finish_reason: {}. 将默认设置为 'stop'.", genMetadata.getFinishReason());
                            finishReason = "stop";
                        }
                    }
                    springAiGenerationUsage = springAiChatResponse.getMetadata().getUsage();
                }
                openAiChoices.add(new OpenAIChoice(i, new OpenAIMessage(role, content), finishReason));

                if (i == 0 && springAiGenerationUsage != null) { // 通常取第一个generation的usage
                    openAiUsage = new OpenAIUsage(
                            springAiGenerationUsage.getPromptTokens(),
                            springAiGenerationUsage.getGenerationTokens(),
                            springAiGenerationUsage.getTotalTokens());
                }
            }
        } else {
            openAiChoices.add(new OpenAIChoice(0, new OpenAIMessage("assistant", ""), "error_no_generation"));
        }

        // 如果顶层元数据有Usage且前面没获取到，可以尝试使用
        if (openAiUsage == null && springAiChatResponse.getMetadata() != null && springAiChatResponse.getMetadata().getUsage() != null) {
            Usage overallUsage = springAiChatResponse.getMetadata().getUsage();
            openAiUsage = new OpenAIUsage(
                    overallUsage.getPromptTokens(),
                    overallUsage.getGenerationTokens(),
                    overallUsage.getTotalTokens()
            );
        }

        // system_fingerprint (示例，具体key可能不同)
        // if (springAiChatResponse.getMetadata() != null && springAiChatResponse.getMetadata().get("system_fingerprint") != null) {
        //    systemFingerprint = springAiChatResponse.getMetadata().get("system_fingerprint").toString();
        // }

        OpenAIChatCompletionResponse fullResponse = new OpenAIChatCompletionResponse(
                "chatcmpl-" + UUID.randomUUID().toString(),
                Instant.now().getEpochSecond(),
                requestedModelName,
                openAiChoices,
                openAiUsage,
                systemFingerprint);
        try {
            return objectMapper.writeValueAsString(fullResponse);
        } catch (JsonProcessingException e) {
            log.error("序列化非流式OpenAI响应失败: {}", e.getMessage(), e);
            return "{\"error\": \"Failed to serialize non-streaming response to JSON\"}";
        }
    }

    /**
     * 将Spring AI的ChatResponse（通常是一个数据块）转换为OpenAI API兼容的【流式数据块】JSON字符串。
     */
    public String formatToStreamingChunk(
            ChatResponse springAiChatResponseChunk, // 这是从Flux<ChatResponse>来的单个ChatResponse
            String streamId,
            long createdTimestamp,
            String requestedModelName,
            AtomicBoolean isFirstContentChunk, // 用于判断是否需要发送 role:"assistant"
            boolean includeUsageInThisChunk // 标记这是否是最后一个包含usage的块
    ) {
        String openAiApiFinishReason = null; // 用于最终JSON的finish_reason，默认为null
        OpenAIDelta delta = new OpenAIDelta();
        String finishReason = null;
        String chunkContent = "";
        Usage springAiChunkUsage = null; // 这个数据块的Usage信息
        String systemFingerprint = null; // 如果能从某个地方获取

        Generation generation = (springAiChatResponseChunk.getResults() != null && !springAiChatResponseChunk.getResults().isEmpty())
                ? springAiChatResponseChunk.getResults().getFirst() : null;

        if (generation != null) {
            chunkContent = generation.getOutput().getContent(); // 可能为null或空
            ChatGenerationMetadata genMetadata =generation.getMetadata();

            if (genMetadata != null) {
                String rawFinishReasonFromMeta = genMetadata.getFinishReason(); // 从元数据获取原始值

                // --- 核心修正逻辑 ---
                if (rawFinishReasonFromMeta != null && !rawFinishReasonFromMeta.equalsIgnoreCase("NULL")) {
                    openAiApiFinishReason = rawFinishReasonFromMeta.toLowerCase();
                    // 转小写
                    if (!VALID_OPENAI_FINISH_REASONS.contains(openAiApiFinishReason)) {
                        // 如果转换后不是一个已知的有效OpenAI finish_reason
                        log.warn("流式处理中收到非OpenAI标准的finish_reason: '{}' (原始值: '{}'). 将其映射为 'stop' (或考虑作为错误处理).",
                                openAiApiFinishReason, rawFinishReasonFromMeta);
                        openAiApiFinishReason = "stop";
                        // 或者根据您的业务逻辑决定如何处理未知原因
                    }
                }


                // 如果这是包含 finish_reason 的块，并且要包含 usage
                if (finishReason != null && includeUsageInThisChunk) {
                     springAiChunkUsage = springAiChatResponseChunk.getMetadata().getUsage();
                }
            }
        }

        // 如果finishReason已确定，说明这是最后一个有意义的内容块或结束块
        if (finishReason != null) {
            if (chunkContent == null || chunkContent.isEmpty()) { // 如果是纯结束信号的块
                delta = new OpenAIDelta(); // 空的delta
            } else { // 如果结束信号的块还带有最后的内容
                delta.content = chunkContent;
            }
        } else if (chunkContent != null && !chunkContent.isEmpty()) { // 普通内容块
             delta.content = chunkContent;
        } else { // 完全空的chunk，可能不应该发生，或者可以不发送
            return null; 
        }

        if (isFirstContentChunk.getAndSet(false) && (delta.content != null && !delta.content.isEmpty())) {
             delta.role = "assistant"; // 仅在第一个包含内容的块的delta中设置role
        }


        OpenAIStreamChoice streamChoice = new OpenAIStreamChoice(0, delta, finishReason);
        OpenAIUsage openAiChunkUsage = null;
        if (springAiChunkUsage != null) {
            openAiChunkUsage = new OpenAIUsage(
                    springAiChunkUsage.getPromptTokens(),
                    springAiChunkUsage.getGenerationTokens(),
                    springAiChunkUsage.getTotalTokens());
        }

        OpenAIChatCompletionChunk chunk = new OpenAIChatCompletionChunk(
                streamId,
                createdTimestamp,
                requestedModelName,
                List.of(streamChoice),
                systemFingerprint, // 尝试从 ChatResponse 元数据获取
                openAiChunkUsage);

        try {
            return objectMapper.writeValueAsString(chunk);
        } catch (JsonProcessingException e) {
            log.error("序列化流式OpenAI数据块失败: {}", e.getMessage(), e);
            return null; // 或者抛出异常，让上层处理
        }
    }
}