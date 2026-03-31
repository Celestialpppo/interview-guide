package interview.guide.modules.knowledgebase.eval.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 评测任务摘要。
 */
public record RagEvalRunSummaryDTO(
    Long runId,
    Long datasetId,
    String datasetName,
    String name,
    String status,
    Map<String, Object> summaryMetrics,
    Map<String, Object> retrieverConfig,
    Map<String, Object> generatorConfig,
    Map<String, Object> judgeConfig,
    String failureMessage,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
}
