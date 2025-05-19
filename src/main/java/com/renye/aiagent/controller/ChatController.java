package com.renye.aiagent.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.renye.aiagent.dto.OpenAIChatRequest;
import com.renye.aiagent.dto.ResumeInfo;
import jakarta.annotation.Resource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/chat") // API 的基础路径
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Resource
    private ChatModel dashScopeChatModel; // 注入你在配置中定义的 ChatModel Bean

    @PostMapping("/completions")
    public ResponseEntity<?> handleChatCompletions(@RequestBody String rawJsonBody) {
        System.out.println("接收到的原始 JSON: " + rawJsonBody);


        return ResponseEntity.ok("已接收原始 JSON，请检查控制台输出。");
    }

    private Prompt getPrompt(BeanOutputConverter<ResumeInfo> outputParser,String resumeText) {


        String formatInstructions = outputParser.getFormat();

        // 3. 创建 Prompt 模板
        String templateString = """
        你是一位专业的 HR 助手，擅长从简历中精确提取关键信息。
        请仔细阅读并分析以下简历文本，严格按照要求提取信息。

        简历文本:
        ```
        {resume_text}
        ```

        请提取以下信息，并严格按照 JSON 格式返回:
        - name: 候选人姓名 (如果找不到明确的姓名，返回 "未知")
        - email: 候选人邮箱 (如果找不到，返回 "")
        - phone: 候选人电话号码 (最好是手机号，如果找不到，返回 "")
        - educationSummary: 教育背景的简洁摘要 (将所有教育经历合并为一个字符串)
        - projects: 一个包含所有项目经历的列表，每个项目包含（此外如果有 工作经历 ，也加入到这个信息当中）:
          - projectName: 项目名称
          - dateRange: 项目起止日期范围 (如 "2023年09月 - 2024年09月" ，如果没有，就返回空字符串 "")
          - tags: 一个字符串列表，包含从该项目描述中识别出的主要技术、关键技能或知识领域标签 (例如：["Java", "Spring Boot", "微服务架构", "数据分析", "Python"])。请尽量提取3-5个核心标签。如果无法从项目描述中明确分析出相关标签，请返回一个空列表 []。

        {format_instructions}

        确保 JSON 格式正确，所有字段都包含在内。如果某项信息确实找不到，请按上述说明填入默认值或空字符串/列表。
        """;
        PromptTemplate promptTemplate = new PromptTemplate(templateString);

        // 4. 准备模板变量
        Map<String, Object> variables = Map.of(
                "resume_text", resumeText,
                "format_instructions", formatInstructions
        );

        // 5. 创建 Prompt
        return promptTemplate.create(variables);
    }

    /**
     * 使用 Apache PDFBox 从 PDF 输入流中提取文本
     * @param inputStream PDF文件的输入流
     * @return 提取的文本内容
     * @throws IOException IO 异常
     */
    private String extractTextFromPdf(InputStream inputStream) throws IOException {

        // 1. 读取 InputStream 到 byte[] 数组
        //    注意: 对于非常大的文件，这可能会消耗较多内存
        byte[] pdfBytes = inputStream.readAllBytes(); // Java 9+
        // 2. 使用 Loader.loadPDF(byte[]) 加载文档
        try (PDDocument document = Loader.loadPDF(pdfBytes)) { // <--- 使用 byte[] 重载方法
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        }

    }


}