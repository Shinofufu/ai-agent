package com.renye.aiagent.service;

import com.renye.aiagent.config.KnowledgeBaseConfig;
import com.renye.aiagent.dto.ResumeInfo;
import com.renye.aiagent.session.InterviewContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;




import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource; // 或 javax.annotation.Resource，取决于您的Jakarta EE版本

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 忍
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    // DashScopeChatModel
    @Resource
    private ChatModel chatModel;

    @Resource
    private VectorStore vectorStore;
    // 用于获取tag到知识库
    @Resource
    private KnowledgeBaseConfig knowledgeBaseConfig;

    /**
     * RAG 流式处理方法
     * @param allMessagesIncludingDynamicSystem 包含客户端发来的历史和当前用户输入 (但不包括最终的系统提示)
     * @param tagsForRag RAG所需标签
     * @return LLM生成的ChatResponse流
     */
    public Flux<ChatResponse> streamWithRag(
            // 包含了动态系统提示和用户对话历史+当前输入
            List<Message> allMessagesIncludingDynamicSystem,
            // 从 OpenAIChatRequest 直接传入的tags，用于知识库检索
            List<String> tagsForRag
    ) {
        String currentUserQuery = "";
        // 从 messagesFromClient 提取当前用户查询（通常是最后一条USER类型的消息）
        for (int i = allMessagesIncludingDynamicSystem.size() - 1; i >= 0; i--) {
            Message msg = allMessagesIncludingDynamicSystem.get(i);
            if (msg.getMessageType() == MessageType.USER) {
                currentUserQuery = msg.getText();
                break;
            }
        }


        if (!StringUtils.hasText(currentUserQuery)) {
            log.warn("RAG: 用户当前查询为空，但系统提示词已包含上下文，直接调用LLM。");
            return chatModel.stream(new Prompt(allMessagesIncludingDynamicSystem));
        }

        log.info("RAG处理：用户查询='{}', 使用会话中的焦点标签={}", currentUserQuery,tagsForRag);


        Filter.Expression finalFilterExpression = null;
        if (!CollectionUtils.isEmpty(tagsForRag)) {
            List<Expression> tagExpressions = new ArrayList<>();
            for (String tag : tagsForRag) {
                // 假设您的 DocumentLoadingService 在存储文档时，
                // 将标签存储在元数据的 "doc_tags_list" 字段中 (例如，作为列表或可以被EQ匹配的单个文本)。
                // Filter.ExpressionType.EQ 通常用于精确匹配。
                // 如果 "doc_tags_list" 是一个列表，并且 VectorStore 支持对列表元素的EQ查询，这是可行的。
                // 如果 "doc_tags_list" 是一个包含多个标签的文本（如 "java,spring"），则EQ "java" 不会匹配。
                // 您需要确保元数据存储方式与这里的过滤方式匹配。
                // 假设我们希望匹配元数据字段 "doc_tag" (单个标签) 等于前端传来的任一标签：
                tagExpressions.add(new Expression(ExpressionType.EQ, new Key("doc_tag_exact_match"), new Value(tag.toLowerCase())));
                // 或者，如果您在 DocumentLoadingService 中将每个标签作为单独的布尔元数据字段存储，
                // 例如 metadata.put("tag_java", true)，那么可以这样过滤：
                // tagExpressions.add(new Expression(ExpressionType.EQ, new Key("tag_" + tag.toLowerCase()), new Filter.Value(true)));
            }

            if (tagExpressions.size() == 1) {
                finalFilterExpression = tagExpressions.get(0);
            }
        }

        // ---- 执行相似度搜索 (带元数据过滤) ----
        // 主要还是基于用户问题进行语义搜索
        SearchRequest searchRequest = SearchRequest.builder()
                .query(currentUserQuery)
                .topK(3)
                .build(); // 每个标签组合条件下，检索最相关的3个文档块

        if (finalFilterExpression != null) {

            searchRequest = searchRequest.withFilterExpression(finalFilterExpression);
            log.info("VectorStore查询将使用过滤器表达式: {}", finalFilterExpression.toString());
        } else {
            log.info("没有有效的标签过滤器，仅基于用户查询进行语义搜索。");
        }

        List<Document> relevantDocuments = new ArrayList<>();
        try {
            relevantDocuments = vectorStore.similaritySearch(searchRequest);
            log.info("从VectorStore检索到 {} 条与查询和/或标签相关的文档。", relevantDocuments.size());
        } catch (Exception e) {
            log.error("从VectorStore检索文档时出错: {}", e.getMessage(), e);
            // 即使检索失败，也应继续尝试无RAG的回复，或返回错误信息
        }

        String contextInformation = relevantDocuments.stream()
                .distinct() // 去重，以防万一
                .map(doc -> {
                    // 尝试从元数据获取更友好的来源名称
                    String sourceName = "知识库参考";
                    if (doc.getMetadata().containsKey("sourceFile")) {
                        sourceName = doc.getMetadata().get("sourceFile").toString();
                    } else if (doc.getMetadata().containsKey("doc_tag_exact_match")) {
                        sourceName = "关于 " + doc.getMetadata().get("doc_tag_exact_match").toString() + " 的资料";
                    }
                    return "相关资料 (" + sourceName + "):\n\"" + doc.getContent() + "\"";
                })
                .collect(Collectors.joining("\n---\n"));

        // ---- 构建最终发送给LLM的消息列表 ----
        // 复制一份，准备修改
        List<Message> finalLlmMessages = new ArrayList<>(allMessagesIncludingDynamicSystem);

        // 2. （可选）将RAG上下文作为一条新的系统消息或用户消息的补充
        if (StringUtils.hasText(contextInformation)) {
            // 将RAG上下文增强到系统提示词中
            // 假设SystemMessage是列表中的第一条
            if (!finalLlmMessages.isEmpty() && finalLlmMessages.get(0).getMessageType() == MessageType.SYSTEM) {
                SystemMessage originalSystemMessage = (SystemMessage) finalLlmMessages.get(0);
                String augmentedSystemPrompt = originalSystemMessage.getContent() +
                        "\n\n请额外参考以下背景知识来回答和提问：\n---背景知识开始---\n" +
                        contextInformation +
                        "\n---背景知识结束---";
                finalLlmMessages.set(0, new SystemMessage(augmentedSystemPrompt));
                log.info("RAG上下文已整合到系统提示词中。");
            } else {
                // 如果没有系统消息，或者不方便修改，可以作为一条新的系统消息插入到最前面
                finalLlmMessages.add(0, new SystemMessage("参考背景知识：\n" + contextInformation));
                log.info("RAG上下文已作为新的系统消息添加。");
            }
        } else {
            log.info("本次提问没有可用的RAG上下文信息。");
        }




        Prompt augmentedPrompt = new Prompt(finalLlmMessages);
        // 避免在生产中打印过长的Prompt
        if (log.isDebugEnabled()) {
            // log.debug("最终发送给LLM的Prompt内容: {}", augmentedPrompt.getContents());
            log.debug("最终发送给LLM的消息条数: {}, 首条消息类型: {}, 最后一条消息类型: {}",
                    augmentedPrompt.getInstructions().size(),
                    augmentedPrompt.getInstructions().isEmpty() ? "N/A" : augmentedPrompt.getInstructions().get(0).getMessageType(),
                    augmentedPrompt.getInstructions().isEmpty() ? "N/A" : augmentedPrompt.getInstructions().get(augmentedPrompt.getInstructions().size()-1).getMessageType()
            );
        }


        return chatModel.stream(augmentedPrompt);
    }
}