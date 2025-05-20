package com.renye.aiagent.session;

    import com.renye.aiagent.dto.InterviewSetupRequest;
    import lombok.Getter;

    import java.util.List;
    // import com.renye.aiagent.dto.InterviewSetupRequest; // 可以包含部分简历信息
    @Getter
    public class InterviewContext {
        public final String interviewSessionId;
        public final String dynamicSystemPrompt;
        public final List<String> relevantTags; // 用于RAG的标签
        public final InterviewSetupRequest resumeData; // 存储完整的简历设置请求，方便RAG按需取用

        public InterviewContext(String interviewSessionId, String dynamicSystemPrompt, List<String> relevantTags, InterviewSetupRequest resumeData) {
            this.interviewSessionId = interviewSessionId;
            this.dynamicSystemPrompt = dynamicSystemPrompt;
            this.relevantTags = relevantTags;
            this.resumeData = resumeData;
        }
        // Getters
    }