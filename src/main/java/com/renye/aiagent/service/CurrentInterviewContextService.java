package com.renye.aiagent.service;

import com.renye.aiagent.dto.CurrentInterviewContextPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service // 确保是单例
public class CurrentInterviewContextService {
    private static final Logger log = LoggerFactory.getLogger(CurrentInterviewContextService.class);

    // 使用AtomicReference来存储当前的面试上下文，提供一些基本的线程安全（尽管您假设单线程）
    private final AtomicReference<CurrentInterviewContextPojo> currentContext = new AtomicReference<>(null);

    public void set(CurrentInterviewContextPojo context) {
        log.info("设置新的当前面试上下文。Tags: {}", context.getRelevantTagsForRag());
        currentContext.set(context);
    }

    public Optional<CurrentInterviewContextPojo> get() {
        CurrentInterviewContextPojo ctx = currentContext.get();
        if (ctx == null) {
            log.warn("当前没有激活的面试上下文。");
        }
        return Optional.ofNullable(ctx);
    }

    public void clear() {
        log.info("清除当前面试上下文。");
        currentContext.set(null);
    }
}