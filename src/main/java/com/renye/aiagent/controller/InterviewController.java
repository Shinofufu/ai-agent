// InterviewController.java (功能大幅简化)
package com.renye.aiagent.controller;

import com.renye.aiagent.dto.CurrentInterviewContextPojo;
import com.renye.aiagent.dto.InterviewSetupRequest;
// import com.renye.aiagent.dto.InterviewSetupResponse; // 可能只需要返回提示词
import com.renye.aiagent.service.AiInterviewerService;
import com.renye.aiagent.service.CurrentInterviewContextService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap; // 用于简单返回
import java.util.Map;     // 用于简单返回
import java.util.stream.Collectors;

/**
 * @author 忍
 */
@CrossOrigin
@RestController
@RequestMapping("/interview") // 路径可以保留或修改
public class InterviewController {
    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    @Resource
    private AiInterviewerService aiInterviewerService;

    @Resource
    private CurrentInterviewContextService currentInterviewContextService;

    @PostMapping("/setup-current")
    public ResponseEntity<Map<String, Object>> setupCurrentInterview(@RequestBody InterviewSetupRequest setupRequest) {
        if (setupRequest == null || setupRequest.getResumeInfo() == null || CollectionUtils.isEmpty(setupRequest.getSelectedInterviewTags())) {
            log.warn("面试设置请求无效，缺少简历信息或选择的标签。");
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少简历信息或选择的标签"));
        }
        log.info("收到设置当前面试上下文的请求，选择的标签: {}", setupRequest.getSelectedInterviewTags());

        String dynamicSystemPrompt = aiInterviewerService.generateDynamicSystemPrompt(
                setupRequest.getSelectedInterviewTags(),
                setupRequest.getResumeInfo(),
                "你是一位专业的AI技术面试官。"
        );

        CurrentInterviewContextPojo contextToStore = new CurrentInterviewContextPojo(
                dynamicSystemPrompt,
                setupRequest.getSelectedInterviewTags(),
                setupRequest.getResumeInfo()
        );

        currentInterviewContextService.set(contextToStore);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "当前面试上下文已成功设置。");
        response.put("generatedSystemPrompt", dynamicSystemPrompt);

        log.info("当前面试上下文已更新。");
        return ResponseEntity.ok(response);
    }
}