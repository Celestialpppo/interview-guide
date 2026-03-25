package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统一封装结构化输出调用与重试策略。
 * 在这里发生大模型调用。
 */
@Component
public class StructuredOutputInvoker {

    //这是一个固定提示词模板。
    private static final String STRICT_JSON_INSTRUCTION = """
请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
1) 不要输出 Markdown 代码块（如 ```json）。
2) 不要输出任何解释文字、前后缀、注释。
3) 所有字符串内引号必须正确转义。
""";

    //最大重试次数，至少为1
    private final int maxAttempts;
    //控制 “重试时要不要把上一次失败原因拼进 prompt”，可以把错误信息提示给模型，帮助它修正输出
    private final boolean includeLastErrorInRetryPrompt;

    //@Value是Spring的注解，用于从配置文件中注入值
    public StructuredOutputInvoker(
        @Value("${app.ai.structured-max-attempts:2}") int maxAttempts,
        @Value("${app.ai.structured-include-last-error:true}") boolean includeLastErrorInRetryPrompt
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.includeLastErrorInRetryPrompt = includeLastErrorInRetryPrompt;
    }

    //调用大模型，要求返回结构化结果，并把结果解析成 T 类型对象；如果失败则重试，最后失败则抛业务异常。
    public <T> T invoke(
        ChatClient chatClient,
        String systemPromptWithFormat,
        String userPrompt,
        BeanOutputConverter<T> outputConverter,
        ErrorCode errorCode,
        String errorPrefix,
        String logContext,
        Logger log
    ) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            //第一次尝试：直接用原始 systemPromptWithFormat
            //第二次及以后：在原 prompt 基础上，调用 buildRetrySystemPrompt(...) 生成一个更严格的重试 prompt
            String attemptSystemPrompt = attempt == 1
                ? systemPromptWithFormat
                : buildRetrySystemPrompt(systemPromptWithFormat, lastError);
            try {
                return chatClient.prompt() //开始构造一次 prompt 请求。
                    .system(attemptSystemPrompt)//设置系统提示词。
                    .user(userPrompt)//设置用户提示词。
                    .call()//真正发起一次同步调用。
                    .entity(outputConverter);//把模型返回的文本，转换成想要的 Java 类型。
            } catch (Exception e) {
                lastError = e;
                log.warn("{}结构化解析失败，准备重试: attempt={}, error={}", logContext, attempt, e.getMessage());
            }
        }

        throw new BusinessException(
            errorCode,
            errorPrefix + (lastError != null ? lastError.getMessage() : "unknown")
        );
    }

    private String buildRetrySystemPrompt(String systemPromptWithFormat, Exception lastError) {
        StringBuilder prompt = new StringBuilder(systemPromptWithFormat)
            .append("\n\n")
            .append(STRICT_JSON_INSTRUCTION)
            .append("\n上次输出解析失败，请仅返回合法 JSON。");

        if (includeLastErrorInRetryPrompt && lastError != null && lastError.getMessage() != null) {
            prompt.append("\n上次失败原因：")
                .append(sanitizeErrorMessage(lastError.getMessage()));
        }
        return prompt.toString();
    }

    private String sanitizeErrorMessage(String message) {
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 200) {
            return oneLine.substring(0, 200) + "...";
        }
        return oneLine;
    }
}
