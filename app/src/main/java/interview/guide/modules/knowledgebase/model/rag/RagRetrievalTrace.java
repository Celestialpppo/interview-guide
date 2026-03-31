package interview.guide.modules.knowledgebase.model.rag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索阶段的可观测 trace。
 */
public record RagRetrievalTrace(
    String originalQuestion,
    String rewrittenQuestion,
    String resolvedQuery,
    int topK,
    double minScore,
    boolean effectiveHit,
    List<RetrievedChunk> hits
) {

    public String contextText() {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        return hits.stream()
            .map(RetrievedChunk::text)
            .collect(Collectors.joining("\n\n---\n\n"));
    }
}
