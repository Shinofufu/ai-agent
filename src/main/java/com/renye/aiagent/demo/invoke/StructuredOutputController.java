package com.renye.aiagent.demo.invoke;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 定义我们希望 AI 返回的结构化数据
 * 使用 Java Record 非常方便
 */


@Component
public class StructuredOutputController implements CommandLineRunner  { // 换个名字更清晰

    @Resource
    private ChatModel dashScopeChatModel;

    public record LoveAdvice(String situationSummary, String suggestion, int confidenceRating) {
        // situationSummary: 对用户情况的简短总结
        // suggestion: 具体的建议
        // confidenceRating: AI 对这个建议把握程度的评分 (1-5)
    }
    @Override
    public void run(String... args) throws Exception {
        DemoLoveAdviceWithOpenAiConversion converter = new DemoLoveAdviceWithOpenAiConversion();
        ChatResponse response = test();
        String convert = DemoLoveAdviceWithOpenAiConversion.OpenAiResponseConverter.convert(response,"qwen-turbo");
        System.out.println(convert);


    }

    private ChatResponse test(){
        System.out.println("\n--- Demonstrating Structured Output (BeanOutputParser) ---");
        // 1. 为目标 Record 创建 BeanOutputConverter
        BeanOutputConverter<LoveAdvice> outputParser = new BeanOutputConverter<>(LoveAdvice.class);

        // 2. 获取格式指令 (告诉 AI 如何格式化 JSON)
        String formatInstructions = outputParser.getFormat();
        // 你可以取消下面这行注释，看看这些指令具体长什么样
        System.out.println("--- Format Instructions --- \n" + formatInstructions + "\n-------------------------\n");

        // 3. 创建 Prompt 模板，包含任务描述、用户输入和格式指令
        String templateString = """
                你是一位经验丰富的恋爱大师。请仔细分析用户描述的恋爱困境，并根据你的分析提供具体的建议。
                用户困境: "{user_situation}"

                请严格按照以下格式指令返回你的分析和建议:
                {format_instructions}
                """;

        PromptTemplate promptTemplate = new PromptTemplate(templateString);

        // 4. 准备模板变量
        Map<String, Object> variables = Map.of(
                "user_situation", "我暗恋我的同事很久了，一起工作时感觉挺合拍的，但我完全不知道对方的想法，也不敢贸然表白，怕影响工作关系，怎么办？",
                "format_instructions", formatInstructions // 将格式指令作为变量传入
        );

        // 5. 创建最终的 Prompt
        Prompt prompt = promptTemplate.create(variables);

        // 6. 调用模型
        System.out.println("AI 正在生成结构化建议...");
        ChatResponse response = dashScopeChatModel.call(prompt);
        String rawResponseContent = response.getResult().getOutput().getText();
        System.out.println("--- Raw AI Response --- \n" + rawResponseContent + "\n-------------------------\n");

        // 7. 使用 OutputParser 解析模型的文本响应
        try {
            LoveAdvice loveAdvice = outputParser.convert(rawResponseContent);

            System.out.println("--- Parsed Love Advice (Java Object) ---");
            System.out.println("Situation Summary: " + loveAdvice.situationSummary());
            System.out.println("Suggestion: " + loveAdvice.suggestion());
            System.out.println("Confidence Rating (1-5): " + loveAdvice.confidenceRating());
            System.out.println("----------------------------------------");

            // 现在你可以直接使用这个 loveAdvice 对象了，比如存数据库等
            // if (loveAdvice.confidenceRating() > 3) { ... }

        } catch (Exception e) {
            // LLM 可能不会总是完美遵循格式指令，导致解析失败
            System.err.println("解析 AI 响应失败: " + e.getMessage());
            System.err.println("请检查 Raw AI Response 是否严格符合 JSON 格式以及定义的字段。");
        }
        return response;
    }
}