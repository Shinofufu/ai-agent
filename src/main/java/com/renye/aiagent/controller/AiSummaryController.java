package com.renye.aiagent.controller;

import com.renye.aiagent.dto.ResumeInfo; // Assuming ResumeInfo DTO is here or imported
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; // Ensure @RequestBody is imported

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/ai")
public class AiSummaryController {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryController.class);

    @Resource
    private ChatModel dashScopeChatModel; // Use the same configured model (e.g., qwen-turbo)

    @PostMapping("/generate-summary")
    // Method accepts ResumeInfo JSON object from the request body
    public ResponseEntity<String> generateSummary(@RequestBody ResumeInfo resumeInfo) {

        if (resumeInfo == null) {
            return ResponseEntity.badRequest().body("接收到的简历信息为空");
        }

        log.info("收到生成面试总结的请求，候选人: {}", resumeInfo.name());

        // 1. Format Resume Data for the Prompt
        String formattedProjects = formatProjectsForPrompt(resumeInfo.projects());
        Prompt prompt = getPrompt(resumeInfo, formattedProjects);

        // 5. Call AI Model
        try {
            log.info("向 AI 模型发送生成总结的请求...");
            ChatResponse response = dashScopeChatModel.call(prompt);
            String summaryText = response.getResult().getOutput().getText();
            log.info("AI 生成总结成功: {}", summaryText.substring(0, Math.min(summaryText.length(), 100)) + "..."); // Log part of the summary

            // 6. Return the generated summary text
            return ResponseEntity.ok(summaryText);

        } catch (Exception e) {
            log.error("调用 AI 模型生成总结时出错，候选人: {}", resumeInfo.name(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("AI 生成总结时出错: " + e.getMessage());
        }
    }

    private static Prompt getPrompt(ResumeInfo resumeInfo, String formattedProjects) {
        String promptInputText = String.format(
                "候选人姓名: %s\n教育背景: %s\n项目经历:\n%s",
                resumeInfo.name(),
                resumeInfo.educationSummary(),
                formattedProjects
        );

        // 2. Create Prompt Template for Summary Generation
        //    Note: No BeanOutputConverter needed here, we want a text response.
        String templateString = """
                你是一位资深的面试官或职业顾问。请根据以下候选人的简历信息，生成一段简短（约 100-150 字）、中肯的面试总结或建议。
                请侧重于根据其教育背景和项目经历，指出其可能的优势、潜在的待提升方面（如果有明显体现），或者提出一个可能的面试切入点。
                语气应专业、客观且具有建设性，请使用 第二人称 “你” 来评价候选人。

                候选人简历信息如下:
                ```
                {resume_details}
                ```
                
                请直接生成总结建议文本，不需要额外的解释或开场白。
                """;
        PromptTemplate promptTemplate = new PromptTemplate(templateString);

        // 3. Prepare Variables
        Map<String, Object> variables = Map.of("resume_details", promptInputText);

        // 4. Create Prompt
        Prompt prompt = promptTemplate.create(variables);
        return prompt;
    }

    /**
     * Helper method to format the list of projects into a readable string for the prompt.
     */
    private String formatProjectsForPrompt(List<ResumeInfo.ProjectExperience> projects) {
        if (projects == null || projects.isEmpty()) {
            return "无项目经历。";
        }
        return projects.stream()
                .map(p -> String.format(
                        "- 项目名称: %s (%s)\n  描述: %s",
                        p.projectName(),
                        p.dateRange(),
                        " "// Replace newlines in description for cleaner prompt input
                ))
                .collect(Collectors.joining("\n"));
    }

    // The extractTextFromPdf method is NOT needed in this controller
    // private String extractTextFromPdf(...) { ... }
}