package com.renye.aiagent.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map; // 如果 fullResumeData 是 Map

public class OpenAIChatRequest {
    // 用于关联已初始化的面试上下文
    public String interviewSessionId;
    public List<OpenAIMessage> messages;
    public String model;
    public Boolean stream;
    @JsonProperty("stream_options")
    public StreamOptions streamOptions;
    public List<String> tags;
    public String resumeSummary;
    // 可选的完整简历数据对象
    @JsonProperty("full_resume_data")
    public Map<String, Object> fullResumeData;


    public static class StreamOptions {
        @JsonProperty("include_usage")
        public Boolean includeUsage;
    }
}