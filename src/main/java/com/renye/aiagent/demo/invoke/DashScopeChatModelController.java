//package com.renye.aiagent.demo.invoke;
//
//
//import jakarta.annotation.Resource;
//import org.springframework.ai.chat.memory.ChatMemory;
//import org.springframework.ai.chat.memory.InMemoryChatMemory;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.messages.Message;
//import org.springframework.ai.chat.messages.UserMessage;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.chat.model.Generation;
//import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.ai.chat.prompt.PromptTemplate;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
///**
// * @Description 前を歩く君じゃなきゃダメだから
// * @Author Sh1NoBu_
// * @Data 2025/4/26 14:12
// * 焦らないでいい
// * <p>
// * SpringAI 框架调用
// */
//@Component
//public class DashScopeChatModelController implements CommandLineRunner {
//
//    @Resource
//    private ChatModel dashScopeChatModel;
//
//
//
//    @Override
//    public void run(String... args) throws Exception {
//        System.out.println("\n--- Demonstrating ChatMemory ---");
//        // 1. 创建 ChatMemory 实例
//        // 在真实应用中，这通常与用户会话关联，这里我们创建一个临时的
//        ChatMemory chatMemory = new InMemoryChatMemory();
//        // --- 对话第一轮 ---
//        System.out.println("[User - Turn 1]");
//        String userQuery1 = "你好，AI恋爱大师！我感觉和伴侣的沟通越来越少了，怎么办？；不超过50字";
//        System.out.println("用户提问: " + userQuery1);
//
//        // 创建用户消息对象
//        Message userMessage1 = new UserMessage(userQuery1);
//
//        // 第一次调用时，历史记录是空的。
//        // 我们创建一个只包含当前用户消息的列表来构建 Prompt
//        // 注意：更复杂的场景可能还会包含一个 SystemMessage 来设定角色
//        List<Message> messagesForTurn1 = List.of(userMessage1);
//        Prompt prompt1 = new Prompt(messagesForTurn1);
//
//        // 调用模型
//        System.out.println("AI 正在思考...");
//        ChatResponse response1 = dashScopeChatModel.call(prompt1);
//        Message assistantMessage1 = response1.getResult().getOutput(); // 获取 AI 的回复消息对象
//        System.out.println("AI 回复: " + assistantMessage1.getText());
//        String id = response1.getMetadata().getId();
//        // 4. 将用户消息和 AI 回复添加到 ChatMemory 中 (按顺序！)
//        chatMemory.add(id,userMessage1);
//        chatMemory.add(id,assistantMessage1); // 存储 AI 的回复
//
//        // --- 对话第二轮 ---
//        System.out.println("\n[User - Turn 2]");
//        String userQuery2 = "你提到主动创造沟通机会，有什么具体的建议吗？比如说什么话题比较好？不超过50字";
//        System.out.println("用户提问: " + userQuery2);
//
//        // 创建第二轮的用户消息对象
//        UserMessage userMessage2 = new UserMessage(userQuery2);
//
//        // 2. 从 ChatMemory 获取历史消息
//        List<Message> conversationHistory = chatMemory.get(id,1); // 获取所有已存消息 [User1, Assistant1]
//
//        // 3. 构建包含历史和新消息的列表用于 Prompt
//        List<Message> messagesForTurn2 = new ArrayList<>(conversationHistory); // 复制历史
//        messagesForTurn2.add(userMessage2); // 添加当前用户消息
//
//        Prompt prompt2 = new Prompt(messagesForTurn2); // 使用包含上下文的列表创建 Prompt
//
//        // 调用模型
//        System.out.println("AI 正在思考 (带着记忆)...");
//        ChatResponse response2 = dashScopeChatModel.call(prompt2);
//        AssistantMessage assistantMessage2 = response2.getResult().getOutput();
//        System.out.println("AI 回复: " + assistantMessage2.getText());
//
//        // 4. 再次将用户消息和 AI 回复添加到 ChatMemory
//        chatMemory.add(id,userMessage2);
//        chatMemory.add(id,assistantMessage2);
//
//
//
//        // --- 验证内存内容 ---
//        System.out.println("\n--- 当前对话内存中的消息 ---");
//        chatMemory.get(id,5).forEach(message -> {
//            System.out.println("  [" + message.getMessageType() + "] " + message.getText());
//        });
//        System.out.println("-----------------------------");
//
//    }
//}
