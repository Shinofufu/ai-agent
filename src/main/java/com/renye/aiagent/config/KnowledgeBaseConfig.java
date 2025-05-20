package com.renye.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "interview.knowledge-base")
public class KnowledgeBaseConfig {

    // 基础路径，指向 classpath 下的PDFs文件夹
    private String pdfResourcePath = "knowledgebase/pdfs/"; // 对应 src/main/resources/knowledgebase/pdfs/

    // 标签到文件名的映射。key是前端可能传来的tag (小写)，value是包含该tag的PDF文件名关键词(用于匹配)
    // 例如: java -> ["Java 基础篇", "Java 集合框架篇", "JVM篇", "Java并发编程"]
    //      spring -> ["Spring 篇"]
    // 这个映射可以做得更智能，或者直接根据文件名包含tag来动态查找。
    // 为了更灵活，我们可以直接在代码中根据tag动态查找文件名。
    // 这里可以先定义一些明确的映射，或者定义一些通用规则。
    private Map<String, List<String>> tagToFileKeywords;

    // 默认加载的PDF文件名（无论有无tag都可能需要的基础知识）
    private List<String> defaultPdfFiles;


    public String getPdfResourcePath() {
        return pdfResourcePath;
    }

    public void setPdfResourcePath(String pdfResourcePath) {
        this.pdfResourcePath = pdfResourcePath;
    }

    public Map<String, List<String>> getTagToFileKeywords() {
        return tagToFileKeywords;
    }

    public void setTagToFileKeywords(Map<String, List<String>> tagToFileKeywords) {
        this.tagToFileKeywords = tagToFileKeywords;
    }

    public List<String> getDefaultPdfFiles() {
        return defaultPdfFiles;
    }

    public void setDefaultPdfFiles(List<String> defaultPdfFiles) {
        this.defaultPdfFiles = defaultPdfFiles;
    }
}