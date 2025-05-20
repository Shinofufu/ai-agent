package com.renye.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

/**
 * @author 忍
 */
@Configuration
@ConfigurationProperties(prefix = "interview.knowledge-base")
@Data
public class KnowledgeBaseConfig {

    // 基础路径，指向 classpath 下的PDFs文件夹
    // 对应 src/main/resources/knowledgebase/pdfs/
    private String pdfResourcePath = "knowledgebase/pdfs/";

    // 标签到文件名的映射。key是前端可能传来的tag (小写)，value是包含该tag的PDF文件名关键词(用于匹配)
    // 例如: java -> ["Java 基础篇", "Java 集合框架篇", "JVM篇", "Java并发编程"]
    //      spring -> ["Spring 篇"]
    // 这个映射可以做得更智能，或者直接根据文件名包含tag来动态查找。
    // 为了更灵活，我们可以直接在代码中根据tag动态查找文件名。
    // 这里可以先定义一些明确的映射，或者定义一些通用规则。
    private Map<String, List<String>> tagToFileKeywords;

    // 默认加载的PDF文件名（无论有无tag都可能需要的基础知识）
    private List<String> defaultPdfFiles;



}