package com.renye.aiagent.dto;

import lombok.Data;

/**
 * @author 忍
 */
@Data
    public class InterviewSetupResponse {
        public String interviewSessionId;
        public String initialSystemPrompt;


        public InterviewSetupResponse(String interviewSessionId) {
            this.interviewSessionId = interviewSessionId;
        }

    }