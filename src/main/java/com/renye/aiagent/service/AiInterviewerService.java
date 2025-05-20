package com.renye.aiagent.service;

import com.renye.aiagent.dto.ResumeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiInterviewerService {
    private static final Logger log = LoggerFactory.getLogger(AiInterviewerService.class);



    // 接收从OpenAIChatRequest中提取的tags和resume信息
    public String generateDynamicSystemPrompt(
            List<String> tags,
            ResumeInfo resumeInfo,
            String baseSystemPrompt
    ) {
        // 从一个基础模板开始
        StringBuilder systemPromptBuilder = new StringBuilder(baseSystemPrompt);

        if (!CollectionUtils.isEmpty(tags)) {
            systemPromptBuilder.append(" 本次面试将侧重于以下领域：【").append(String.join("、", tags)).append("】。");
        } else {
            systemPromptBuilder.append(" 本次面试将进行通用的技术能力评估。");
        }

        if (resumeInfo != null) {
            String resumeSummary = buildResumeSummary(resumeInfo);
            if (StringUtils.hasText(resumeSummary)) {
                systemPromptBuilder.append("\n\n关于候选人的背景信息（供你参考）：\n").append(resumeSummary);
            }
        }
        // 可以从 fullResumeData 中提取更详细信息加入

        // 添加通用的面试官行为指令 (这部分可以和baseSystemPrompt合并或细化)
        systemPromptBuilder.append("\n\n你的面试任务是：\n");
        systemPromptBuilder.append("1. 根据候选人的背景和期望交流的领域，提出相关、有深度、开放式的技术问题和情景问题。\n");
        systemPromptBuilder.append("2. 仔细聆听并分析候选人的回答,因为语言转译问题，可能会出现错别字的现象很正常，你需要努力去理解并且进行有针对性的追问。\n");
        systemPromptBuilder.append("3. 评估候选人在相关领域的技术掌握程度、实际项目经验、问题分析与解决能力以及沟通表达能力。\n");
        systemPromptBuilder.append("4. 保持面试的专业性和互动性，营造一个积极的面试氛围。\n");
        systemPromptBuilder.append("5. 你的提问和回复都应该简洁明了，一次对话通常包含2-3句话，并正确使用标点符号。\n");
        systemPromptBuilder.append("6. 用户会主动开始对话，请你自然回应并开始面试流程。");


        String dynamicPrompt = systemPromptBuilder.toString();
        log.info("生成的动态系统提示词: {}", dynamicPrompt);
        return dynamicPrompt;
    }

    // 辅助方法，从 InterviewSetupRequest (或等效的 Map 来自 fullResumeData) 构建摘要
    public String buildResumeSummary(String name, String education, List<ResumeInfo.ProjectExperience> experience) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(name)) {
            sb.append("姓名: ").append(name).append("。");
        }
        if (StringUtils.hasText(education)) {
            sb.append("\n教育背景: ").append(education).append("。");
        }

        if (!CollectionUtils.isEmpty(experience)) {
            sb.append("\n主要项目/工作经历：");
            experience.stream().limit(2).forEach(exp -> {
                sb.append("\n  - 项目名称: ").append(exp.projectName());
                List<String> expTags = exp.tags();
                if (!CollectionUtils.isEmpty(expTags)) {
                    sb.append(" (相关技术: ").append(String.join(", ", expTags)).append(")");
                }
            });
        }
        return sb.toString().trim();
    }
    // 辅助方法从ResumeInfo构建简历摘要
    private String buildResumeSummary(ResumeInfo resumeInfo) {
        if (resumeInfo == null) return "";
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(resumeInfo.name())) sb.append("姓名: ").append(resumeInfo.name()).append("。");
        if (StringUtils.hasText(resumeInfo.educationSummary())) sb.append("\n教育背景: ").append(resumeInfo.educationSummary()).append("。");

        if (!CollectionUtils.isEmpty(resumeInfo.projects())) {
            sb.append("\n主要项目/工作经历：");
            resumeInfo.projects().stream().limit(2).forEach(proj -> {
                sb.append("\n  - 项目名称: ").append(proj.projectName());
                if (!CollectionUtils.isEmpty(proj.tags())) {
                    sb.append(" (相关技术: ").append(String.join(", ", proj.tags())).append(")");
                }
            });
        }
        return sb.toString().trim();
    }

    public String getDefaultAiInterviewerSystemPrompt() {

        return generateDynamicSystemPrompt(null,null,"你是AI面试官");
    }
}