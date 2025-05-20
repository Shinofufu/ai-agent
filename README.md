# ai-agent

## 描述

这是一个 AI 代理项目，旨在提供智能面试、简历分析和内容评估等功能。 它利用 Spring Boot框架和各种 AI 及 Java 技术栈来实现其核心服务。

## 特性

  * **简历分析**: 支持上传 PDF 简历并提取结构化信息，如姓名、教育背景、项目经验和技能标签。
  * **AI 面试官**:
      * 根据候选人简历和选定的面试标签动态生成面试系统提示词。
      * 支持流式聊天补全，模拟面试对话。
      * 利用 RAG (Retrieval Augmented Generation) 服务，结合知识库中的 PDF 文档来增强面试问题的相关性和深度。
  * **面试评估**: 根据候选人简历和面试对话记录，生成结构化的面试表现评估，包括回答完整度、沟通表达能力、代码能力（如果适用）、综合评分和建议。
  * **知识库管理**:
      * 应用启动时从指定路径 (classpath:knowledgebase/pdfs/) 加载 PDF 文档作为知识库。
      * 支持根据文件名自动提取标签，用于 RAG 检索。
      * 使用向量存储 (SimpleVectorStore) 来存储和查询文档嵌入。
  * **API 接口**: 提供 RESTful API 用于简历分析、面试设置、聊天交互和面试评估生成。
  * **OpenAI 兼容接口**:聊天完成端点 (`/chat/completions`) 在设计上旨在兼容 OpenAI API 格式，包括流式响应和数据结构。

## 技术栈

  * **核心框架**: Spring Boot 3.4.5
  * **Java 版本**: 21
  * **AI 集成**:
      * Spring AI (包括 alibaba-starter, pdf-document-reader)
      * DashScope SDK (alibaba/dashscope-sdk-java)
  * **主要依赖**:
      * Spring Boot Starter Web
      * Hutool (多功能工具包)
      * Lombok (简化代码)
      * Knife4j (API 文档)
      * Victools JSON Schema Generator
      * Apache PDFBox (PDF 内容提取)
  * **构建工具**: Maven
  * **API 文档**: Knife4j (Swagger UI 路径: `/api/swagger-ui.html`, API Docs 路径: `/api/v3/api-docs`)

## 安装与启动

1.  **环境准备**:
      * 安装 Java 21 JDK。
      * 配置 Maven 环境。
2.  **获取代码**: 克隆本仓库。
3.  **配置**:
      * 修改 `src/main/resources/application.yaml` 文件：
          * 配置 `spring.ai.dashscope.api-key` 为您的有效 DashScope API 密钥。
          * 如果需要，调整 `interview.knowledge-base.pdf-resource-path` 指向您的知识库 PDF 文件存放路径（默认为 `classpath:knowledgebase/pdfs/`）。
4.  **构建项目**:
    ```bash
    mvn clean install
    ```
5.  **运行项目**:
    ```bash
    java -jar target/ai-agent-0.0.1-SNAPSHOT.jar
    ```
    应用启动后，API 文档通常可以在 `http://localhost:8123/api/doc.html` (根据 `AiAgentApplication.java` 中的打印信息) 或 `http://localhost:8123/api/swagger-ui.html` (根据 `application.yaml`) 访问。

## API 端点

项目主要通过以下 Controller 提供 API 服务：

  * **`ResumeAnalysisController`** (`/resume`)
      * `POST /analyze`: 上传 PDF 简历文件进行分析，返回结构化的简历信息。
      * `POST /analyze-text`: 接收纯文本简历内容进行分析。
  * **`InterviewController`** (`/interview`)
      * `POST /setup-current`: 设置当前面试的上下文，包括简历信息和选择的面试标签，生成并返回动态系统提示词。
  * **`ChatController`** (`/chat`)
      * `POST /completions`: 处理聊天完成请求，支持流式响应，兼容 OpenAI API 格式。此接口会利用 RAG 服务和当前面试上下文。
  * **`AiSummaryController`** (`/ai`)
      * `POST /generate-evaluation`: 根据当前面试上下文（简历和对话记录）生成面试评估报告。
  * **`BodyController`** (`/body`)
      * `GET /`: 一个简单的检查端点。

## 配置

主要的应用程序配置在 `src/main/resources/application.yaml` 文件中。 关键配置项包括：

  * **DashScope API 密钥**: `spring.ai.dashscope.api-key` (必须填写)
  * **DashScope 模型**:
      * 文本嵌入模型: `spring.ai.dashscope.embedding.options.model` (默认为 `text-embedding-v2`)
      * 聊天模型: `spring.ai.dashscope.chat.options.model` (默认为 `qwen-turbo`)
  * **知识库配置** (`interview.knowledge-base`):
      * `pdf-resource-path`: PDF 知识库文件在 classpath 中的路径。
      * `default-pdf-files`: 默认加载的 PDF 文件列表。
  * **服务器配置**:
      * 端口: `server.port` (默认为 `8123`)
      * 上下文路径: `server.servlet.context-path` (默认为 `/api`)
  * **API 文档 (Knife4j)**: 相关路径和分组配置。

## 如何贡献

欢迎提交 Pull Request 或 Issue 来改进本项目。

## 许可证

请在项目根目录添加 `LICENSE` 文件来明确项目的开源许可证。目前 `pom.xml` 中 `licenses` 标签为空。
