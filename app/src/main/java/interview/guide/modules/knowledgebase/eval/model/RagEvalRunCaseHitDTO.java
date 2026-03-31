package interview.guide.modules.knowledgebase.eval.model;

import java.util.Map;

/**
 * 单个 run case 的检索命中详情。
 */
public record RagEvalRunCaseHitDTO(
    int rank,
    String chunkId,
    Double similarityScore,
    Integer relevanceGrade,
    Boolean matchedGold,
    String sourceName,
    String chunkPreview,
    Map<String, Object> metadata
) {
}
