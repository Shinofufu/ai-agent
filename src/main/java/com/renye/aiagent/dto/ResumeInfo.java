package com.renye.aiagent.dto; // 或者你喜欢的包名

import java.util.List;

/**
 * 用于封装从简历中提取的结构化信息
 */
public record ResumeInfo(
    String name,
    String email,
    String phone,
    String educationSummary, // 教育背景暂时合并为一个字符串
    List<ProjectExperience> projects // 项目经历列表
) {
    // 如果需要默认值或构造函数，可以不使用 Record 而用普通 Class

    /**
     * 嵌套 Record 定义项目经验结构
     */
    public record ProjectExperience(
        String projectName,
        String dateRange,
        String description
    ) {}
}