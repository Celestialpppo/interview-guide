package interview.guide.modules.knowledgebase.eval.model;

/**
 * 评测任务详情。
 */
public record RagEvalRunDetailDTO(
    RagEvalRunSummaryDTO run,
    int caseCount,
    int passCount,
    int hallucinationCaseCount
) {
}
