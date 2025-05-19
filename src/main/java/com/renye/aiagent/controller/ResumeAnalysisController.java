package com.renye.aiagent.controller;

import com.renye.aiagent.dto.ResumeInfo; // 导入我们定义的 DTO
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

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
@CrossOrigin
@RestController
@RequestMapping("/resume") // API 的基础路径
public class ResumeAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisController.class);

    @Resource
    private ChatModel dashScopeChatModel; // 注入你在配置中定义的 ChatModel Bean

    @PostMapping("/analyze") // 定义 POST 端点，用于接收文件上传
    public ResponseEntity<?> analyzeResume(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("请上传一个有效的简历文件");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.badRequest().body("目前只支持 PDF 格式的简历");
        }

        String resumeText;
        try {
            log.info("开始解析 PDF 文件: {}", file.getOriginalFilename());
            // 1. 从上传的文件中提取文本
            resumeText = extractTextFromPdf(file.getInputStream());
            log.info("PDF 文本提取成功 ({} 字符)", resumeText.length());
            // log.debug("提取到的文本:\n{}", resumeText.substring(0, Math.min(500, resumeText.length()))); // 打印部分文本用于调试
        } catch (IOException e) {
            log.error("解析 PDF 文件失败: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("解析 PDF 文件时出错");
        }
        // 2. 准备结构化输出解析器
        BeanOutputConverter<ResumeInfo> outputParser = new BeanOutputConverter<>(ResumeInfo.class);
        Prompt prompt = getPrompt(outputParser,resumeText);

        // 6. 调用 AI 模型
        try {
            log.info("向 AI 模型发送简历分析请求...");
            ChatResponse response = dashScopeChatModel.call(prompt);
            String rawResponseContent = response.getResult().getOutput().getText();
            log.info("收到 AI 响应");
            log.debug("原始 AI 响应:\n{}", rawResponseContent); // 调试时可以取消注释看原始输出

            // 7. 解析 AI 响应
            log.info("尝试解析 AI 响应为 ResumeInfo 对象...");
            ResumeInfo resumeInfo = outputParser.convert(rawResponseContent);
            log.info("AI 响应解析成功!");

            //System.out.println(resumeInfo==null?null:resumeInfo.toString());
            // 8. 返回结构化数据给前端
            return ResponseEntity.ok(resumeInfo);

        } catch (Exception e) {
            log.error("调用 AI 模型或解析响应时出错", e);
            // 这里可以根据异常类型返回更具体的错误信息
            // 比如，如果解析失败，可能是 AI 没有遵循格式指令
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("AI 模型分析简历时出错: " + e.getMessage() + ". 请检查日志获取原始响应。");
        }
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

    // --- 添加一个接收纯文本的端点作为备选 ---
    @PostMapping("/analyze-text")
    public ResponseEntity<?> analyzeResumeText(@RequestBody String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return ResponseEntity.badRequest().body("简历文本不能为空");
        }
        log.info("收到文本简历进行分析 ({} 字符)", resumeText.length());

        BeanOutputConverter<ResumeInfo> outputParser = new BeanOutputConverter<>(ResumeInfo.class);
        Prompt prompt =getPrompt(outputParser,resumeText);

        try {
            log.info("向 AI 模型发送文本简历分析请求...");
            ChatResponse response = dashScopeChatModel.call(prompt);
            String rawResponseContent = response.getResult().getOutput().getText();
            log.info("收到 AI 响应");

            log.info("尝试解析 AI 响应为 ResumeInfo 对象...");
            ResumeInfo resumeInfo = outputParser.convert(rawResponseContent);
            log.info("AI 响应解析成功!");

            return ResponseEntity.ok(resumeInfo);
        } catch (Exception e) {
            log.error("调用 AI 模型或解析响应时出错 (文本输入)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("AI 模型分析简历时出错: " + e.getMessage() + ". 请检查日志获取原始响应。");
        }
    }
}