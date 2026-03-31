package interview.guide.modules.knowledgebase.eval.model;

import java.util.List;
import java.util.Map;

/**
 * 单个 run case 结果详情。
 */
public record RagEvalRunCaseDTO(
    Long runCaseId,
    Long caseId,
    String question,
    String expectedAnswer,
    String originalQuery,
    String rewrittenQuery,
    String resolvedQuery,
    String answerText,
    Boolean overallPass,
    Map<String, Object> retrievalMetrics,
    Map<String, Object> generationMetrics,
    Map<String, Object> rawJudge,
    List<RagEvalRunCaseHitDTO> hits
) {
}
