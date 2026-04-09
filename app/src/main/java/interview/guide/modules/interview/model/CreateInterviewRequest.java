package interview.guide.modules.interview.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建面试会话请求
 */
public record CreateInterviewRequest(
    String resumeText,      // 简历文本内容（可选，无简历时为通用面试）

    @Min(value = 3, message = "题目数量最少3题")
    @Max(value = 20, message = "题目数量最多20题")
    int questionCount,      // 面试题目数量 (3-20)

    Long resumeId,          // 简历ID（可选，无简历时不传）

    Boolean forceCreate,    // 是否强制创建新会话（忽略未完成的会话），默认为 false

    String llmProvider,     // LLM提供商

    @NotBlank(message = "面试主题不能为空")
    String skillId,         // 面试主题 ID（如 java-backend, frontend 等）

    String difficulty       // 难度级别: junior / mid / senior
) {}
