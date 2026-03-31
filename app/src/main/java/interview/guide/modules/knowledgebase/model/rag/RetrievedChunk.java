package interview.guide.modules.knowledgebase.model.rag;

import java.util.Map;

/**
 * 单次检索命中的 chunk 结果。
 */
public record RetrievedChunk(
    String chunkId,
    int rank,
    Double score,
    String text,
    Map<String, Object> metadata
) {
}
