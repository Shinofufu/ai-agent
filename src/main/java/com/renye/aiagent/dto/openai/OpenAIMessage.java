package com.renye.aiagent.dto.openai;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author 忍
 */
@Data
@AllArgsConstructor
public class OpenAIMessage {
    public String role;
    public Object content;
}