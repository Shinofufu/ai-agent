package com.renye.aiagent.dto;
import lombok.Data;
import java.util.List;

/**
 * @author 忍
 * des:前端提交简历和标签时使用
 */
@Data
public class InterviewSetupRequest {
    private ResumeInfo resumeInfo;
    // 用户在Vue前端选择的，用于本次面试的焦点标签
    private List<String> selectedInterviewTags;
}