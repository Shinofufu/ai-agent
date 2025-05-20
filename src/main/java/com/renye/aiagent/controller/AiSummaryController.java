package com.renye.aiagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renye.aiagent.dto.CurrentInterviewContextPojo;
import com.renye.aiagent.dto.InterviewEvaluationResult;
import com.renye.aiagent.dto.ResumeInfo;
import com.renye.aiagent.dto.openai.OpenAIMessage; // 用于对话记录
import com.renye.aiagent.service.CurrentInterviewContextService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author 忍
 * &#064;Des: 生成总结评价
 */
@CrossOrigin
@RestController
@RequestMapping("/ai")
public class AiSummaryController {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryController.class);

    @Resource
    private ChatModel dashScopeChatModel;

    @Resource
    private CurrentInterviewContextService currentInterviewContextService;

    private final ObjectMapper objectMapper;

    public AiSummaryController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    }

    @PostMapping("/generate-evaluation") // 新的或修改后的端点名
    public ResponseEntity<?> generateInterviewEvaluation() { // 请求体可能不需要，因为我们用全局上下文

        Optional<CurrentInterviewContextPojo> contextOptional = currentInterviewContextService.get();
        if (contextOptional.isEmpty()) {
            log.warn("无法生成面试总结：当前没有激活的面试上下文。");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"No active interview session found to summarize.\"}");
        }

        CurrentInterviewContextPojo interviewContext = contextOptional.get();
        // 从 interviewContext 中获取 ResumeInfo (通过 getOriginalSetupRequest())
        ResumeInfo resumeInfo =interviewContext.getResumeInfo();

        List<OpenAIMessage> transcript = interviewContext.getConversationTranscript();

        if (resumeInfo == null && CollectionUtils.isEmpty(transcript)) {
            log.warn("无法生成面试总结：简历信息和对话记录都为空。");
            return ResponseEntity.badRequest().body("{\"error\": \"Resume information and conversation transcript are missing.\"}");
        }

        log.info("收到生成面试评估的请求。候选人: {}", resumeInfo != null ? resumeInfo.name() : "未知");

        // 1. 格式化简历信息和对话记录作为Prompt的输入
        String formattedResume = (resumeInfo != null) ? formatResumeForEvaluationPrompt(resumeInfo) : "无简历信息。";
        String formattedTranscript = formatTranscriptForPrompt(transcript);

        // 2. 准备 BeanOutputConverter 以解析为 InterviewEvaluationResult DTO
        BeanOutputConverter<InterviewEvaluationResult> outputParser =
                new BeanOutputConverter<>(InterviewEvaluationResult.class);
        String formatInstructions = outputParser.getFormat();

        // 3. 创建用于生成评估的Prompt模板
        String templateString = """
                你是一位经验丰富的HR专家和资深技术招聘官。
                你的任务是根据以下提供的候选人简历信息（如果有）和完整的面试对话记录，对候选人的整体表现给出一个结构化的评估。

                候选人简历信息:
                ```
                {resume_details}
                ```

                面试对话记录 (user是候选人, assistant是AI面试官):
                ```
                {interview_transcript}
                ```

                请严格按照以下JSON格式输出你的评估结果，只输出JSON对象，不要有任何其他多余的文字或解释:
                {format_instructions}

                评估要点及评分标准 (均为0-100分):
                - completionRate (回答完整度): 评估候选人对每个问题的回答是否全面、是否直接回应了问题核心、信息是否充分且没有遗漏关键点。
                - communicationSkill (沟通表达能力): 评估候选人表达的逻辑性、清晰度、语言组织能力，以及在技术交流中的顺畅程度。
                - codingAbility (代码能力评分): （如果面试内容能体现）评估候选人在对话中展现的技术知识的深度和广度、代码理解、设计思路和解决实际编码问题的能力。如果面试内容不侧重代码细节，请基于其技术论述和逻辑给出评估。
                - overallScore (综合评分): 这是对候选人本次面试表现的总体打分，综合考量其技术水平、问题解决、沟通表达、以及与岗位潜在匹配度。
                - suggestion (综合建议文本，100-200字): 提供具体的评价，指出候选人的主要亮点、潜在的不足或待提升方面。评价应客观、中肯、专业，并具有建设性。例如，是否推荐进入下一轮，或建议候选人加强哪些方面的学习和实践。
                """;
        PromptTemplate promptTemplate = new PromptTemplate(templateString);

        Map<String, Object> variables = Map.of(
                "resume_details", formattedResume,
                "interview_transcript", formattedTranscript,
                "format_instructions", formatInstructions
        );


        Prompt prompt = promptTemplate.create(variables);
        if (log.isDebugEnabled()){
            log.debug("发送给AI的评估Prompt内容: {}", prompt.getContents());
        } else {
            log.info("发送给AI的评估Prompt instruction 长度: {}", prompt.getInstructions().stream().map(m->m.getText().length()).reduce(0, Integer::sum));
        }


        // 5. 调用AI模型 (非流式)
        try {
            log.info("向AI模型发送生成面试评估的请求...");
            ChatResponse response = dashScopeChatModel.call(prompt);
            String rawJsonResponse = response.getResult().getOutput().getContent();
            log.info("AI生成评估响应 (原始JSON): {}", rawJsonResponse);

            // 6. 使用OutputParser解析模型的JSON文本响应
            InterviewEvaluationResult evaluationResult = outputParser.convert(rawJsonResponse);
            log.info("AI评估解析成功!");

            return ResponseEntity.ok(evaluationResult);

        } catch (Exception e) {
            log.error("调用AI模型生成面试评估时出错，候选人: {}", (resumeInfo != null ? resumeInfo.name() : "未知"), e);
            // 返回更详细的错误信息给客户端，便于调试
            String errorMessage = "AI生成面试评估时出错: " + e.getMessage().replace("\"", "'");
            if (e.getCause() != null) {
                errorMessage += " | Caused by: " + e.getCause().getMessage().replace("\"", "'");
            }
            log.error(errorMessage);
            return generateInterviewEvaluation();
        }
    }

    private String formatResumeForEvaluationPrompt(ResumeInfo resumeInfo) {
        // 将 ResumeInfo 转换为易于LLM阅读的文本格式
        // 可以简单地使用JSON，或者自定义格式
        try {
            // 为了Prompt的清晰度，避免过于冗长的JSON，可以只选择关键字段
            Map<String, Object> simplifiedResume = Map.of(
                    "name", resumeInfo.name(),
                    "educationSummary", resumeInfo.educationSummary(),
                    "projects", resumeInfo.projects().stream()
                            .map(p -> Map.of(
                                    "projectName", p.projectName(),
                                    "description", p.description() != null ? p.description().substring(0, Math.min(p.description().length(), 100))+"..." : "", // 截断描述
                                    "tags", p.tags()
                            ))
                            .collect(Collectors.toList())
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplifiedResume);
        } catch (JsonProcessingException e) {
            log.error("序列化ResumeInfo到JSON失败 (for prompt)", e);
            return "简历信息格式化失败。";
        }
    }

    private String formatTranscriptForPrompt(List<OpenAIMessage> transcript) {
        if (CollectionUtils.isEmpty(transcript)) {
            return "无对话记录。";
        }
        return transcript.stream()
                .map(msg -> msg.getRole() + ": " + msg.getContent())
                .collect(Collectors.joining("\n"));
    }
}