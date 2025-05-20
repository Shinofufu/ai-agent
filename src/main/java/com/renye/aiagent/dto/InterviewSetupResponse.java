package com.renye.aiagent.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
    public class InterviewSetupResponse {
        public String interviewSessionId;
        public String initialSystemPrompt; // 可选，让前端知道生成的系统提示词
        // public String firstQuestion;    // 可选，如果AI生成第一个问题

        public InterviewSetupResponse(String interviewSessionId) {
            this.interviewSessionId = interviewSessionId;
        }
         // Getters and Setters...
    }