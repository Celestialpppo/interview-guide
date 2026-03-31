package interview.guide.modules.knowledgebase.eval.service;

import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseQrelEntity;
import interview.guide.modules.knowledgebase.eval.model.RagRetrievalMetrics;
import interview.guide.modules.knowledgebase.model.rag.RetrievedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RAG 检索指标计算测试")
class RagRetrievalMetricsCalculatorTest {

    private final RagRetrievalMetricsCalculator calculator = new RagRetrievalMetricsCalculator();

    @Test
    @DisplayName("应正确计算 Recall@K、MRR 和 nDCG@K")
    void shouldCalculateMetrics() {
        List<RetrievedChunk> hits = List.of(
            new RetrievedChunk("chunk-a", 1, 0.91, "A", Map.of()),
            new RetrievedChunk("chunk-b", 2, 0.88, "B", Map.of()),
            new RetrievedChunk("chunk-c", 3, 0.70, "C", Map.of())
        );

        RagEvalCaseQrelEntity qrel1 = new RagEvalCaseQrelEntity();
        qrel1.setChunkId("chunk-b");
        qrel1.setRelevanceGrade(3);
        RagEvalCaseQrelEntity qrel2 = new RagEvalCaseQrelEntity();
        qrel2.setChunkId("chunk-c");
        qrel2.setRelevanceGrade(1);

        RagRetrievalMetrics metrics = calculator.calculate(hits, List.of(qrel1, qrel2), 3);

        assertEquals(1.0, metrics.recallAtK(), 1e-6);
        assertEquals(0.5, metrics.mrr(), 1e-6);
        assertEquals(0.6443, metrics.ndcgAtK(), 1e-4);
        assertEquals(2, metrics.relevantCount());
        assertEquals(3, metrics.retrievedCount());
    }

    @Test
    @DisplayName("无 gold qrel 时应返回 0 指标")
    void shouldReturnZeroMetricsWhenNoQrels() {
        RagRetrievalMetrics metrics = calculator.calculate(
            List.of(new RetrievedChunk("chunk-a", 1, 0.91, "A", Map.of())),
            List.of(),
            5
        );

        assertEquals(0.0, metrics.recallAtK(), 1e-6);
        assertEquals(0.0, metrics.mrr(), 1e-6);
        assertEquals(0.0, metrics.ndcgAtK(), 1e-6);
        assertEquals(0, metrics.relevantCount());
    }
}
