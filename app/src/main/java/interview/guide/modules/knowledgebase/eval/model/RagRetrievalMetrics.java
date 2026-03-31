package interview.guide.modules.knowledgebase.eval.model;

/**
 * 单个 case 的检索指标。
 */
public record RagRetrievalMetrics(
    int k,
    double recallAtK,
    double mrr,
    double ndcgAtK,
    int relevantCount,
    int retrievedCount
) {
}
