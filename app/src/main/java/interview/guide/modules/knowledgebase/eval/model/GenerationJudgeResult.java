package interview.guide.modules.knowledgebase.eval.model;

/**
 * 生成质量评估结果。
 */
public record GenerationJudgeResult(
    int accuracyScore,
    int citationScore,
    int consistencyScore,
    boolean hallucinationFlag,
    String reason
) {
}
