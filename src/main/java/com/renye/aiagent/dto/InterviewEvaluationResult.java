package com.renye.aiagent.dto; // 假设的包名

import com.fasterxml.jackson.annotation.JsonProperty; // 如果字段名和 JSON 不一致需要

/**
 * 封装 AI 对面试表现的结构化评估结果
 */
public record InterviewEvaluationResult(
        @JsonProperty("completionRate") // 对应前端 results.completionRate
        int completionRate,             // 回答完整度评分 (0-100)

        @JsonProperty("communicationSkill") // 对应前端 results.communicationSkill
        int communicationSkill,         // 沟通表达能力评分 (0-100)

        // 我们仍然可以保留代码能力评分，如果AI能评估的话，前端可以决定是否展示
        // 如果不需要，可以移除此字段和对应的Prompt指令
        @JsonProperty("codingAbility")
        int codingAbilityScore,         // 代码能力评分 (0-100)

        @JsonProperty("overallScore")   // 对应前端 results.overallScore
        int overallScore,               // 综合评分 (0-100)

        @JsonProperty("suggestion")     // 对应前端 results.suggestion
        String suggestionText           // 综合建议文本
) {}