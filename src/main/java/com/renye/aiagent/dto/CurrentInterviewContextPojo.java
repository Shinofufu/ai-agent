package com.renye.aiagent.dto; // 或者 com.renye.aiagent.context

import lombok.Getter;

import java.util.List;
@Getter
public class CurrentInterviewContextPojo {
    private final String dynamicSystemPrompt;
    private final List<String> relevantTagsForRag;
    private final ResumeInfo resumeInfo;

    public CurrentInterviewContextPojo(String dynamicSystemPrompt, List<String> relevantTagsForRag, ResumeInfo resumeInfo) {
        this.dynamicSystemPrompt = dynamicSystemPrompt;
        this.relevantTagsForRag = relevantTagsForRag;
        this.resumeInfo = resumeInfo;
    }


}