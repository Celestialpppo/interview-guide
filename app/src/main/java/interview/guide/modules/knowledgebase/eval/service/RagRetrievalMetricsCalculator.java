package interview.guide.modules.knowledgebase.eval.service;

import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseQrelEntity;
import interview.guide.modules.knowledgebase.eval.model.RagRetrievalMetrics;
import interview.guide.modules.knowledgebase.model.rag.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检索指标计算器。
 */
@Component
public class RagRetrievalMetricsCalculator {

    public RagRetrievalMetrics calculate(List<RetrievedChunk> hits, List<RagEvalCaseQrelEntity> qrels, int k) {
        int effectiveK = Math.max(k, 1);
        List<RetrievedChunk> safeHits = hits != null ? hits : List.of();
        List<RagEvalCaseQrelEntity> safeQrels = qrels != null ? qrels : List.of();
        Map<String, Integer> relevanceMap = safeQrels.stream()
            .collect(Collectors.toMap(
                RagEvalCaseQrelEntity::getChunkId,
                RagEvalCaseQrelEntity::getRelevanceGrade,
                Math::max
            ));

        long relevantCount = relevanceMap.values().stream()
            .filter(grade -> grade != null && grade > 0)
            .count();
        if (relevantCount == 0) {
            return new RagRetrievalMetrics(effectiveK, 0.0, 0.0, 0.0, 0, safeHits.size());
        }

        long retrievedRelevantCount = safeHits.stream()
            .limit(effectiveK)
            .map(RetrievedChunk::chunkId)
            .filter(chunkId -> relevanceMap.getOrDefault(chunkId, 0) > 0)
            .count();

        double recallAtK = (double) retrievedRelevantCount / relevantCount;

        double mrr = 0.0;
        for (int i = 0; i < safeHits.size(); i++) {
            if (relevanceMap.getOrDefault(safeHits.get(i).chunkId(), 0) > 0) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }

        double dcg = 0.0;
        List<RetrievedChunk> topHits = safeHits.stream().limit(effectiveK).toList();
        for (int i = 0; i < topHits.size(); i++) {
            int relevance = relevanceMap.getOrDefault(topHits.get(i).chunkId(), 0);
            if (relevance > 0) {
                dcg += (Math.pow(2, relevance) - 1) / log2(i + 2);
            }
        }

        List<Integer> idealRelevances = relevanceMap.values().stream()
            .filter(grade -> grade != null && grade > 0)
            .sorted(Comparator.reverseOrder())
            .limit(effectiveK)
            .toList();

        double idcg = 0.0;
        for (int i = 0; i < idealRelevances.size(); i++) {
            idcg += (Math.pow(2, idealRelevances.get(i)) - 1) / log2(i + 2);
        }

        double ndcgAtK = idcg == 0.0 ? 0.0 : dcg / idcg;
        return new RagRetrievalMetrics(effectiveK, recallAtK, mrr, ndcgAtK, (int) relevantCount, safeHits.size());
    }

    private double log2(int value) {
        return Math.log(value) / Math.log(2);
    }
}
