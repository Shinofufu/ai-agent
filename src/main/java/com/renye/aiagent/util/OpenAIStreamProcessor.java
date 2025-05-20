package com.renye.aiagent.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renye.aiagent.dto.openai.*; // 导入所有相关的OpenAI DTO
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage; // Spring AI 的 Usage
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OpenAIStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(OpenAIStreamProcessor.class);
    private final ObjectMapper objectMapper;

    private final String modelName;
    private final boolean includeUsage;

    private final String streamId;
    private final long createdTimestamp;
    private final AtomicBoolean isFirstContentChunk = new AtomicBoolean(true);
    private final AtomicReference<Usage> accumulatedUsage = new AtomicReference<>(null); // Spring AI Usage

    private static final Set<String> VALID_OPENAI_FINISH_REASONS =
            Set.of("stop", "length", "tool_calls", "content_filter", "function_call");

    public OpenAIStreamProcessor(String modelName, boolean includeUsage, ObjectMapper objectMapper) {
        this.modelName = modelName;
        this.includeUsage = includeUsage;
        // 为每个流处理器实例配置独立的ObjectMapper，或使用注入的并注意线程安全
        // NON_NULL 对于 delta 对象很重要，避免发送如 delta: {"role":null, "content":null}
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.streamId = "chatcmpl-" + UUID.randomUUID().toString();
        this.createdTimestamp = Instant.now().getEpochSecond();
    }

    /**
     * 将输入的 Spring AI ChatResponse 流转换为 OpenAI SSE 负载字符串流。
     * 每个字符串要么是数据块的JSON，要么是最后的 "[DONE]" 标记。
     */
    public Flux<String> process(Flux<ChatResponse> springAiChatResponseFlux) {
        return springAiChatResponseFlux
                .mapNotNull(this::convertToOpenAICunkPayloadString) // mapNotNull 会自动过滤掉返回null的元素
                .concatWith(Mono.just("[DONE]")); // 在所有数据块之后追加 "[DONE]"
    }

    /**
     * 将单个 Spring AI ChatResponse (通常是流中的一个块) 转换为 OpenAI 数据块的JSON负载字符串。
     * 返回 null 如果此块不应作为数据发送 (例如，完全空的中间块)。
     */
    private String convertToOpenAICunkPayloadString(ChatResponse springAiChatResponseChunk) {
        OpenAIDelta delta = new OpenAIDelta();
        String openAiApiFinishReason = null;
        String chunkContent = "";
        OpenAIUsage usageForThisChunk = null;
        String systemFingerprint = null; // 可从 springAiChatResponseChunk.getMetadata() 提取

        Generation generation = (springAiChatResponseChunk.getResults() != null && !springAiChatResponseChunk.getResults().isEmpty())
                ? springAiChatResponseChunk.getResults().get(0)
                : null;

        if (generation != null) {
            chunkContent = generation.getOutput().getContent();
            ChatGenerationMetadata genMetadata = generation.getMetadata();
            if (genMetadata != null) {
                String rawFinishReasonFromMeta = genMetadata.getFinishReason();
                if (rawFinishReasonFromMeta != null && !rawFinishReasonFromMeta.equalsIgnoreCase("NULL")) {
                    openAiApiFinishReason = rawFinishReasonFromMeta.toLowerCase();
                    if (!VALID_OPENAI_FINISH_REASONS.contains(openAiApiFinishReason)) {
                        log.warn("流式处理中收到非OpenAI标准的finish_reason: '{}' (原始值: '{}'). 将映射为 'stop'.",
                                openAiApiFinishReason, rawFinishReasonFromMeta);
                        openAiApiFinishReason = "stop";
                    }
                }
            }
        }
        // 尝试从 ChatResponse 顶层元数据获取 Usage（如果 generation 中没有）
        if (this.accumulatedUsage.get() == null && springAiChatResponseChunk.getMetadata() != null && springAiChatResponseChunk.getMetadata().getUsage() != null) {
            this.accumulatedUsage.set(springAiChatResponseChunk.getMetadata().getUsage());
        }


        boolean hasContent = (chunkContent != null && !chunkContent.isEmpty());

        if (this.isFirstContentChunk.getAndSet(false)) { // 仅对第一个被此方法处理的块为true
            delta.role = "assistant";
            if (!hasContent && openAiApiFinishReason == null) { // 第一个块，无内容，也未结束
                delta.content = ""; // OpenAI期望即使内容为空，如果role出现，content也应出现（通常为空字符串）
            }
        }

        if (hasContent) {
            delta.content = chunkContent;
        } else if (openAiApiFinishReason != null && delta.role == null && delta.content == null) {
            // 如果是结束块，但没有内容，且role和content都未设置（说明不是第一个块），则delta为空对象
            // Jackson NON_NULL配置会确保delta序列化为 {}
        }

        // 只有当这是真正的结束块 (openAiApiFinishReason非null) 并且客户端请求了usage时，才添加usage
        if (openAiApiFinishReason != null && this.includeUsage && this.accumulatedUsage.get() != null) {
            Usage finalSpringUsage = this.accumulatedUsage.get();
            usageForThisChunk = new OpenAIUsage(
                    finalSpringUsage.getPromptTokens(),
                    finalSpringUsage.getGenerationTokens(),
                    finalSpringUsage.getTotalTokens());
        }

        // 如果delta为空 (role和content都为null) 并且不是结束块，则此块无意义，可以不发送
        if (delta.role == null && delta.content == null && openAiApiFinishReason == null && usageForThisChunk == null) {
            log.trace("跳过发送空的中间数据块。Stream ID: {}", this.streamId);
            return null; // mapNotNull 会过滤掉这个
        }

        OpenAIStreamChoice streamChoice = new OpenAIStreamChoice(0, delta, openAiApiFinishReason);
        OpenAIChatCompletionChunk chunk = new OpenAIChatCompletionChunk(
                this.streamId,
                this.createdTimestamp,
                this.modelName,
                Collections.singletonList(streamChoice), // OpenAI通常只有一个choice，除非n>1
                systemFingerprint, // 可从元数据获取
                usageForThisChunk);

        try {
            return objectMapper.writeValueAsString(chunk);
        } catch (JsonProcessingException e) {
            log.error("序列化流式OpenAI数据块失败 for Stream ID {}: {}", this.streamId, e.getMessage(), e);
            throw new RuntimeException("Failed to serialize stream chunk", e); // 抛出让Flux的onError处理
        }
    }
}