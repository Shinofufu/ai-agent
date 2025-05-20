package com.renye.aiagent.dto; // 或者 com.renye.aiagent.context

import com.renye.aiagent.dto.openai.OpenAIMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
/**
 * @author 忍
 */
@Getter
@AllArgsConstructor
public class CurrentInterviewContextPojo {
    private final String dynamicSystemPrompt;
    private final List<String> relevantTagsForRag;
    private final ResumeInfo resumeInfo;
    private final List<OpenAIMessage> conversationTranscript;

    // 添加消息到对话记录中
    public synchronized void addMessageToTranscript(String role, String content) {
        //避免记录空的AI回复块
        if (content != null && !content.isBlank()) {
            this.conversationTranscript.add(new OpenAIMessage(role, content));
        }
    }

}