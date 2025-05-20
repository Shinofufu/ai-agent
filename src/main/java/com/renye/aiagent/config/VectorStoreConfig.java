package com.renye.aiagent.config; // 请替换为您的实际配置包路径

import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            log.error("EmbeddingModel bean is null! Cannot create SimpleVectorStore.");
            throw new IllegalStateException("EmbeddingModel bean is required to create SimpleVectorStore but was not found. " +
                    "Please ensure an EmbeddingModel (e.g., DashScopeEmbeddingModel) is correctly configured.");
        }
        log.info("正在创建 SimpleVectorStore bean (通过 Builder)，使用的 EmbeddingModel 类型: {}", embeddingModel.getClass().getName());

        // --- 修改点在这里 ---
        // 使用 SimpleVectorStore.Builder 来创建实例
        // 这通常是创建一个纯内存、非持久化的 VectorStore
        return SimpleVectorStore.builder(embeddingModel)
                .build();

    }
}


