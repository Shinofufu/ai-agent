package com.renye.aiagent.dto; // 假设的包名

import com.fasterxml.jackson.annotation.JsonProperty; // 如果字段名和 JSON 不一致需要

/**
 * 封装 AI 对面试表现的结构化评估结果
 */
public record InterviewEvaluationResult(
    // 注意：字段名最好与 SummaryView 中 results 对象的 key 一致
    // 或者使用 @JsonProperty 来映射

    // completionRate: 85, // 回答完整度，AI 可能较难评估，可以由其他逻辑计算或移除
    // codingAbility: 90,  // 代码能力评分
    // communicationSkill: 80, // 沟通表达评分 (如果后续加入语音分析)
    // overallScore: 85,   // 综合评分

    // 简化版：先让 AI 评估代码能力和给出总评及建议
    @JsonProperty("codingAbility") // 确保 JSON key 匹配前端
    int codingAbilityScore,   // 代码能力评分 (0-100)

    @JsonProperty("overallScore") // 确保 JSON key 匹配前端
    int overallScore,      // 综合评分 (0-100) - 让 AI 给出

    @JsonProperty("suggestion") // 确保 JSON key 匹配前端
    String suggestionText     // 综合建议文本
    // 其他评分可以后续添加或由不同模块组合
) {}