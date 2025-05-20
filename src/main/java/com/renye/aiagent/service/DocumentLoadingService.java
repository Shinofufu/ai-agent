package com.renye.aiagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter; // 用于分块
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct; // 用于应用启动时加载

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class DocumentLoadingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoadingService.class);
    // 注入您的VectorStore
    private final VectorStore vectorStore;
    // private final EmbeddingModel embeddingModel; // TokenTextSplitter 可能需要 EmbeddingModel 来估算 token

    @Value("classpath:knowledgebase/pdfs/**/*.pdf") // Spring 资源表达式，匹配所有子目录下的PDF
    private Resource[] pdfResources;

    // 存储每个“知识库标识符”（可能基于文件名或标签）对应的文档列表
    // 或者，更好的方式是直接将文档与元数据一起加载到VectorStore中，查询时通过元数据过滤
    // 这里我们先简单加载，并在VectorStore中用metadata区分来源
    private final Map<String, List<Document>> knowledgeBases = new HashMap<>();

    public DocumentLoadingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }


    @PostConstruct // 应用启动后执行
    public void loadDocuments() {
        log.info("开始加载PDF知识库文档...");
        if (pdfResources == null || pdfResources.length == 0) {
            log.warn("在 classpath:knowledgebase/pdfs/ 下没有找到PDF文件。");
            return;
        }

        TokenTextSplitter textSplitter = new TokenTextSplitter(
            // 默认的分块大小和重叠，您可以根据需要调整
            // 1000, // maxTokensPerChunk
            // 200,  // minTokensPerChunk
            // 20,   // overlapTokens
            // true, // keepSeparator
            // embeddingModel // 传入embeddingModel用于精确的token计数 (如果需要)
        );

        for (Resource pdfResource : pdfResources) {
            String filename = pdfResource.getFilename();

            log.info("正在处理文件: {}", filename);
            try {
                // 使用 PagePdfDocumentReader，它会为PDF的每一页创建一个Document对象
                // 这样可以保留页码等元数据，并且初始的Document粒度较小
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
                // 获取原始的、按页分的文档
                List<Document> rawDocuments = pdfReader.get();

                List<Document> processedDocuments = new ArrayList<>();
                for (Document rawDoc : rawDocuments) {
                    // 为每个文档添加来源文件名作为元数据
                    rawDoc.getMetadata().put("sourceFile", filename);
                    // 可以添加从文件名提取的tag作为元数据
                    extractTagsFromFilename(filename).forEach(tag -> {
                        // 将标签添加到元数据中，例如 "tags" : ["java", "基础"]
                        // 注意：VectorStore对元数据中List<String>的支持取决于具体实现
                        // 有些可能只支持简单的String或数字。如果不支持List，可以考虑将tags合并为逗号分隔的字符串。
                        // 或者为每个tag创建一个单独的元数据字段，但这可能不灵活。
                        // 最好的方式是，如果VectorStore支持，就用List<String>。
                        // 假设我们用一个逗号分隔的字符串
                        String existingTags = (String) rawDoc.getMetadata().getOrDefault("doc_tags", "");
                        if (!existingTags.isEmpty()) {
                            existingTags += ",";
                        }
                        existingTags += tag;
                        rawDoc.getMetadata().put("doc_tags", existingTags);
                    });
                    processedDocuments.add(rawDoc);
                }


                // 对从PDF读取的（可能很大的）文档内容进行分块
                // List<Document> splitDocuments = textSplitter.apply(processedDocuments);
                // log.info("文件 {} 被分割为 {} 个文档块", filename, splitDocuments.size());

                // 注意：如果PagePdfDocumentReader已经将每页作为一个Document，
                // 并且每页的内容不是特别长，可以考虑是否还需要进一步用TokenTextSplitter分块。
                // 如果每页内容可能超过LLM上下文或embedding模型的限制，则需要分块。
                // 假设我们直接使用按页分割的文档，或者您已经配置好了分块逻辑：
                // vectorStore.add(splitDocuments);

                // 简化：直接添加按页分割的文档到VectorStore。
                // 如果文档内容过长，请务必使用TokenTextSplitter。
                if (!processedDocuments.isEmpty()) {
                    vectorStore.add(processedDocuments);
                    log.info("已将来自 {} 的 {} 页文档（或文档块）添加到VectorStore。", filename, processedDocuments.size());
                }

                // (可选) 如果您仍想按文件名/知识库标识符在内存中组织一份引用，可以这样做：
                // String kbIdentifier = extractKbIdentifierFromFile(filename);
                // knowledgeBases.computeIfAbsent(kbIdentifier, k -> new ArrayList<>()).addAll(splitDocuments);

            } catch (Exception e) {
                log.error("处理PDF文件 {} 时出错: {}", filename, e.getMessage(), e);
            }
        }
        log.info("PDF知识库文档加载完成。");
    }

    // 从文件名中提取标签的简单示例逻辑
    // 例如："面试逆袭 Java 基础篇.pdf" -> ["java", "基础"]
    public List<String> extractTagsFromFilename(String filename) {
        List<String> tags = new ArrayList<>();
        String nameWithoutExtension = filename.replace(".pdf", "");
        // 简单的基于空格或特定关键词的分割 (您需要根据您的文件名模式定制)
        if (nameWithoutExtension.contains("Java 基础")) {
            tags.add("java基础");
        }
        if (nameWithoutExtension.contains("Java 集合")) {
            tags.add("java集合");
        }
        if (nameWithoutExtension.contains("JVM")) {
            tags.add("jvm");
        }
        if (nameWithoutExtension.contains("Mybatis")) {
            tags.add("mybatis");
        }
        if (nameWithoutExtension.contains("MySQL")) {
            tags.add("mysql");
        }
        if (nameWithoutExtension.contains("Redis")) {
            tags.add("redis");
        }
        if (nameWithoutExtension.contains("Spring")) {
            tags.add("spring");
        }
        if (nameWithoutExtension.contains("并发编程")) {
            tags.add("java并发");
        }
        if (nameWithoutExtension.contains("RocketMQ")) {
            tags.add("rocketmq");
        }
        if (nameWithoutExtension.contains("操作系统")) {
            tags.add("操作系统");
        }
        if (nameWithoutExtension.contains("分布式")) {
            tags.add("分布式");
        }
        if (nameWithoutExtension.contains("计算机网络")) {
            tags.add("计算机网络");
        }

        // 通用标签提取 (更灵活，但可能不准确)
        // String[] parts = nameWithoutExtension.replace("面试逆袭", "").replace("篇", "").trim().split("\\s+|-");
        // for (String part : parts) {
        //     if (!part.isEmpty() && part.length() > 1) { // 避免太短或无意义的词
        //         tags.add(part.toLowerCase());
        //     }
        // }
        return tags.stream().distinct().collect(Collectors.toList());
    }

    // (可选) 从文件名提取知识库标识符
    // public String extractKbIdentifierFromFile(String filename) {
    //     // 例如，简单地使用文件名（去除扩展名）作为标识符
    //     // 或者根据您的 KnowledgeBaseConfig 中的 tagMappings 来反向查找或匹配
    //     return filename.replace(".pdf", "").toLowerCase().replaceAll("\\s+", "_");
    // }
}