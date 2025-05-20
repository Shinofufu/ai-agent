package com.renye.aiagent.dto; // 或者你喜欢的包名

import java.util.List;

/**
 * 用于封装从简历中提取的结构化信息
 */
public record ResumeInfo(
        String name,
        String email,
        String phone,
        String educationSummary,
        List<ProjectExperience> projects // 项目经历列表
) {


    /**
     * 嵌套 Record 定义项目经验结构
     */
    public record ProjectExperience(
            String projectName,
            String dateRange,
            String description,
            List<String> tags // <--- 新增字段：用于存储项目相关的技术标签列表
    ) {}
}