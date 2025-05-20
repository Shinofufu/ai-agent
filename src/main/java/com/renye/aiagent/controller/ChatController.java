package com.renye.aiagent.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renye.aiagent.dto.CurrentInterviewContextPojo;
import com.renye.aiagent.dto.openai.*;
import com.renye.aiagent.service.AiInterviewerService;
import com.renye.aiagent.service.CurrentInterviewContextService;
import com.renye.aiagent.service.RagService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@CrossOrigin
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ObjectMapper objectMapper;

    @Resource
    private RagService ragService;

    @Resource
    private AiInterviewerService aiInterviewerService;


    @Resource
    private CurrentInterviewContextService currentInterviewContextService;


    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // 确保ObjectMapper配置为不序列化null值，特别是对于delta对象
    public ChatController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy(); // 创建副本以进行本地配置，或确保注入的已配置
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // OpenAI官方定义的有效finish_reason值
    private static final Set<String> VALID_OPENAI_FINISH_REASONS =
            Set.of("stop", "length", "tool_calls", "content_filter", "function_call");

    // 辅助方法：从多种可能的content结构中提取文本
    private String parseContentFromRequest(Object contentObject) {
        switch (contentObject) {
            case null -> {
                return "";
            }
            case String s -> {
                return s;
            }
            case List list -> {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> mapItem) {
                        if ("text".equals(mapItem.get("type")) && mapItem.get("text") instanceof String) {
                            return (String) mapItem.get("text");
                        }
                    }
                }
                return ""; // 如果列表中没有找到text类型的项
            }
            default -> {
            }
        }
        return contentObject.toString(); // 其他情况尝试toString
    }


    @PostMapping("/completions")
    public SseEmitter handleChatCompletions(@RequestBody OpenAIChatRequest openAiRequest) {
        try {
            log.info("Chat Completions 初始请求: {}", objectMapper.writeValueAsString(openAiRequest));
        } catch (JsonProcessingException e) {
            log.warn("记录请求JSON时出错: {}", e.getMessage());
        }
        // 1. 从 CurrentInterviewContextService 获取当前面试上下文
        Optional<CurrentInterviewContextPojo> contextOptional = currentInterviewContextService.get();

        String dynamicSystemPrompt;
        List<String> tagsForRag = null; // RAG 使用的标签
        // InterviewSetupRequest originalRequestData = null; // 如果RagService需要原始简历数据

        if (contextOptional.isPresent()) {
            CurrentInterviewContextPojo currentContext = contextOptional.get();
            dynamicSystemPrompt = currentContext.getDynamicSystemPrompt();
            tagsForRag = currentContext.getRelevantTagsForRag();
            // originalRequestData = currentContext.getOriginalSetupRequest();
            log.info("ChatController: 使用了JVM中存储的当前面试上下文。Tags: {}", tagsForRag);
        } else {
            // 如果JVM中没有设置上下文（例如，Vue前端从未调用过/setup-current，或者已被清除）
            // 则使用一个非常通用的默认系统提示词
            log.warn("ChatController: JVM中未找到当前面试上下文，将使用通用AI面试官提示。");
            dynamicSystemPrompt = aiInterviewerService.getDefaultAiInterviewerSystemPrompt();
            // tagsForRag 保持为 null，RAG将基于通用知识或对话内容进行
        }

        // 2. 构建发送给RagService的消息列表
        List<Message> messagesForRag = new ArrayList<>();
        messagesForRag.add(new SystemMessage(dynamicSystemPrompt)); // 使用动态或默认的系统提示

        boolean userMessageFound = false;

        if (openAiRequest.messages != null) {
            for (var msg : openAiRequest.messages) {
                if ("system".equalsIgnoreCase(msg.role)) {
                    continue; // 跳过客户端的system消息，我们用自己生成的
                }
                String role = msg.role != null ? msg.role.toLowerCase() : "user";
                String contentText = parseContentFromRequest(msg.content);

                if ("user".equalsIgnoreCase(role)) {
                    messagesForRag.add(new UserMessage(contentText));
                    userMessageFound = true;
                } else if ("assistant".equalsIgnoreCase(role)) {
                    messagesForRag.add(new AssistantMessage(contentText));
                }
            }
        }
        if (!userMessageFound) {
            log.warn("处理的请求中未找到有效的用户消息。AI将仅基于系统提示行动。");
        }

        String requestedModelName = openAiRequest.model != null ? openAiRequest.model : "qwen-turbo";
        boolean includeUsage = openAiRequest.streamOptions != null && Boolean.TRUE.equals(openAiRequest.streamOptions.includeUsage);

        // --- 流式处理，难以理解无法解决！

        if (!Boolean.TRUE.equals(openAiRequest.stream)) {
            log.warn("收到非流式请求，但此端点目前主要演示流式。认为非法请求");
            SseEmitter errorEmitter = new SseEmitter();
            try {
                String errorPayload = "{\"error\": {\"message\": \"Non-streaming is not fully supported in this demo endpoint. Please use stream=true.\", \"type\": \"invalid_request_error\"}}";
                errorEmitter.send(SseEmitter.event().name("error").data(errorPayload));
                errorEmitter.complete();
            } catch (IOException e) { errorEmitter.completeWithError(e); }
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(-1L);
        log.info("处理流式请求: model={}, tags={}", requestedModelName, openAiRequest.tags);

        final String streamId = "chatcmpl-" + UUID.randomUUID();
        final long createdTimestamp = Instant.now().getEpochSecond();
        final AtomicBoolean isFirstChunkProcessed = new AtomicBoolean(false); // 用于确保role只发送一次
        final AtomicReference<Usage> finalUsageFromStream = new AtomicReference<>(null);

        emitter.onCompletion(() -> log.info("SseEmitter is completed for streamId: {}", streamId));
        emitter.onTimeout(() -> { log.warn("SseEmitter timed out for streamId: {}", streamId); emitter.complete();});


        Flux<ChatResponse> chatResponseFlux = ragService.streamWithRag(
                messagesForRag,
                tagsForRag
        );

        sseExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().comment("stream_opened")); // 初始"心跳"
                log.info("SSE Task Started. Stream ID: {}", streamId);

                chatResponseFlux.map(springChatResponseChunk -> {
                            // --- 开始核心转换逻辑 (原OpenAIStreamProcessor.formatToStreamingChunk的逻辑) ---
                            OpenAIDelta delta = new OpenAIDelta();
                            String finalFinishReasonForThisChunk = null; // 最终放入OpenAI JSON的finish_reason
                            String currentChunkContent = "";
                            OpenAIUsage usageForThisChunk = null;
                            String systemFingerprint = null; // 可选

                            Generation generation = (springChatResponseChunk.getResults() != null && !springChatResponseChunk.getResults().isEmpty())
                                    ? springChatResponseChunk.getResults().getFirst() : null;

                            if (generation != null) {
                                currentChunkContent = generation.getOutput().getContent();
                                ChatGenerationMetadata genMetadata = generation.getMetadata();
                                if (genMetadata != null) {
                                    String rawFinishReason = genMetadata.getFinishReason();
                                    if (rawFinishReason != null && !rawFinishReason.equalsIgnoreCase("NULL")) {
                                        finalFinishReasonForThisChunk = rawFinishReason.toLowerCase();
                                        if (!VALID_OPENAI_FINISH_REASONS.contains(finalFinishReasonForThisChunk)) {
                                            log.warn("Stream ID {}: 无效的 finish_reason '{}' (原始: '{}'), 修正为 'stop'.", streamId, finalFinishReasonForThisChunk, rawFinishReason);
                                            finalFinishReasonForThisChunk = "stop";
                                        }
                                    }

                                }
                            }
                            // 尝试从ChatResponse顶层元数据获取Usage (如果Generation中没有)
                            if (finalUsageFromStream.get() == null && springChatResponseChunk.getMetadata() != null && springChatResponseChunk.getMetadata().getUsage() != null) {
                                finalUsageFromStream.set(springChatResponseChunk.getMetadata().getUsage());
                            }

                            boolean hasContent = (currentChunkContent != null && !currentChunkContent.isEmpty());

                            if (!isFirstChunkProcessed.get()) { // 这是流中的第一个被处理的ChatResponse块
                                delta.role = "assistant";
                                if (hasContent) {
                                    delta.content = currentChunkContent;
                                } else if (finalFinishReasonForThisChunk == null) { // 第一个块，没内容，也没结束
                                    delta.content = ""; // 发送空内容确保delta不只有role
                                }
                                isFirstChunkProcessed.set(true); // 标记第一个块已处理（role已发送）
                            } else if (hasContent) { // 非第一个块，但有内容
                                delta.content = currentChunkContent;
                            } else if (finalFinishReasonForThisChunk != null) { // 结束块，但没内容
                                // delta保持为空对象 (Jackson NON_NULL会处理成 {})
                            } else {
                                // 中间的空内容块，可以发送一个带有空content的delta，或根据需要跳过
                                // 为确保客户端持续收到响应，发送空content通常更好
                                delta.content = "";
                            }


                            if (finalFinishReasonForThisChunk != null && includeUsage && finalUsageFromStream.get() != null) {
                                Usage lastUsage = finalUsageFromStream.get();
                                usageForThisChunk = new OpenAIUsage(
                                        lastUsage.getPromptTokens(),
                                        lastUsage.getGenerationTokens(),
                                        lastUsage.getTotalTokens());
                            }

                            OpenAIStreamChoice streamChoice = new OpenAIStreamChoice(0, delta, finalFinishReasonForThisChunk);
                            OpenAIChatCompletionChunk chunkForClient = new OpenAIChatCompletionChunk(
                                    streamId, createdTimestamp, requestedModelName,
                                    List.of(streamChoice), systemFingerprint, usageForThisChunk);

                            try {
                                return objectMapper.writeValueAsString(chunkForClient);
                            } catch (JsonProcessingException e) {
                                log.error("Stream ID {}: 序列化OpenAI数据块DTO失败: {}", streamId, e.getMessage(), e);
                                throw new RuntimeException("Chunk DTO serialization error", e);
                            }
                            // --- 核心转换逻辑结束 ---
                        })
                        .filter(Objects::nonNull) // 过滤掉转换逻辑中可能返回的null (例如，如果我们决定跳过某些空块)
                        .subscribe(
                                jsonChunkString -> {
                                    try {
                                        emitter.send(SseEmitter.event().data(jsonChunkString));
                                    } catch (IOException e) {
                                        throw new RuntimeException("SSE send error", e); // 会被doOnError捕获
                                    }
                                },
                                error -> { // Flux流处理过程中的错误
                                    log.error("Stream ID {}: SSE Flux处理错误: {}", streamId, error.getMessage(), error);
                                    try {
                                        String errorJson = "{\"error\":{\"message\":\"Stream processing error: " + error.getMessage().replace("\"", "'") + "\",\"type\":\"internal_server_error\"}}";
                                        emitter.send(SseEmitter.event().name("error").data(errorJson));
                                    } catch (Exception ex) {
                                        log.error("Stream ID {}: 发送错误SSE事件时再次出错: {}", streamId, ex.getMessage(), ex);
                                    }
                                    emitter.completeWithError(error);
                                },
                                () -> { // Flux流成功完成
                                    try {
                                        log.info("Stream ID {}: SSE Flux已完成。发送 [DONE] 信号。", streamId);
                                        emitter.send(SseEmitter.event().data("[DONE]"));
                                        emitter.complete();
                                        log.info("Stream ID {}: [DONE] 已发送，SseEmitter已完成。", streamId);
                                    } catch (IOException e) {
                                        log.error("Stream ID {}: 发送[DONE]或完成Emitter时IO异常: {}", streamId, e.getMessage(), e);
                                        emitter.completeWithError(e);
                                    }
                                }
                        );
            } catch (Exception e) { // sseExecutor.execute() lambda 中同步代码的异常
                log.error("Stream ID {}: SseEmitter异步任务启动时发生意外错误: {}", streamId, e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        log.info("Controller 已返回 SseEmitter. Stream ID: {}", streamId);
        return emitter;
    }
}