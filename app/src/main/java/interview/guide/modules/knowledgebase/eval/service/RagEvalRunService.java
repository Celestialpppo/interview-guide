package interview.guide.modules.knowledgebase.eval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.eval.model.*;
import interview.guide.modules.knowledgebase.eval.repository.*;
import interview.guide.modules.knowledgebase.model.rag.AnswerWithCitations;
import interview.guide.modules.knowledgebase.model.rag.RagRetrievalTrace;
import interview.guide.modules.knowledgebase.model.rag.RagRetrieverOptions;
import interview.guide.modules.knowledgebase.model.rag.RetrievedChunk;
import interview.guide.modules.knowledgebase.service.rag.RagAnswerGeneratorService;
import interview.guide.modules.knowledgebase.service.rag.RagRetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 离线评测执行服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalRunService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final RagRetrieverService ragRetrieverService;
    private final RagAnswerGeneratorService ragAnswerGeneratorService;
    private final RagGenerationJudgeService ragGenerationJudgeService;
    private final RagRetrievalMetricsCalculator retrievalMetricsCalculator;
    private final RagEvalDatasetRepository datasetRepository;
    private final RagEvalCaseRepository caseRepository;
    private final RagEvalCaseQrelRepository qrelRepository;
    private final RagEvalRunRepository runRepository;
    private final RagEvalRunCaseRepository runCaseRepository;
    private final RagEvalRunHitRepository runHitRepository;

    private record CaseExecutionResult(
        RagEvalRunCaseEntity runCase,
        RagRetrievalMetrics retrievalMetrics,
        GenerationJudgeResult judgeResult
    ) {
    }

    public RagEvalRunDetailDTO createAndExecuteRun(RagEvalRunRequest request) {
        RagEvalDatasetEntity dataset = datasetRepository.findById(request.datasetId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RAG_EVAL_DATASET_NOT_FOUND, "评测数据集不存在"));

        RagEvalRunEntity run = new RagEvalRunEntity();
        run.setDataset(dataset);
        run.setName(request.name() != null && !request.name().isBlank()
            ? request.name().trim()
            : "run-" + System.currentTimeMillis());
        run.setStatus(RagEvalRunStatus.PROCESSING);
        run.setRetrieverConfigJson(writeJson(normalizeConfigMap(request.retrieverConfig())));
        run.setGeneratorConfigJson(writeJson(normalizeConfigMap(request.generatorConfig())));
        run.setJudgeConfigJson(writeJson(normalizeConfigMap(request.judgeConfig())));
        run = runRepository.save(run);

        try {
            List<RagEvalCaseEntity> cases = caseRepository.findByDatasetIdAndEnabledTrueOrderByIdAsc(dataset.getId());
            List<CaseExecutionResult> results = new ArrayList<>();
            for (RagEvalCaseEntity evalCase : cases) {
                results.add(executeCase(run, evalCase, request));
            }

            Map<String, Object> summaryMetrics = buildSummaryMetrics(results);
            run.setSummaryMetricsJson(writeJson(summaryMetrics));
            run.setStatus(RagEvalRunStatus.COMPLETED);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            return buildRunDetail(run, results);
        } catch (Exception e) {
            log.error("RAG 评测执行失败: runId={}, error={}", run.getId(), e.getMessage(), e);
            run.setStatus(RagEvalRunStatus.FAILED);
            run.setFailureMessage(truncate(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.RAG_EVAL_RUN_FAILED, "RAG 评测执行失败：" + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RagEvalRunDetailDTO getRunDetail(Long runId) {
        RagEvalRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RAG_EVAL_RUN_NOT_FOUND, "评测任务不存在"));
        List<RagEvalRunCaseEntity> runCases = runCaseRepository.findByRunIdOrderByIdAsc(runId);
        int passCount = (int) runCases.stream().filter(RagEvalRunCaseEntity::getOverallPass).count();
        int hallucinationCaseCount = (int) runCases.stream()
            .map(RagEvalRunCaseEntity::getGenerationMetricsJson)
            .map(this::readMap)
            .filter(metrics -> Boolean.TRUE.equals(metrics.get("hallucinationFlag")))
            .count();
        return new RagEvalRunDetailDTO(
            toRunSummary(run),
            runCases.size(),
            passCount,
            hallucinationCaseCount
        );
    }

    @Transactional(readOnly = true)
    public List<RagEvalRunCaseDTO> listRunCases(Long runId) {
        if (!runRepository.existsById(runId)) {
            throw new BusinessException(ErrorCode.RAG_EVAL_RUN_NOT_FOUND, "评测任务不存在");
        }
        List<RagEvalRunCaseEntity> runCases = runCaseRepository.findByRunIdOrderByIdAsc(runId);
        List<RagEvalRunCaseDTO> result = new ArrayList<>();
        for (RagEvalRunCaseEntity runCase : runCases) {
            List<RagEvalRunCaseHitDTO> hits = runHitRepository.findByRunCaseIdOrderByRankAsc(runCase.getId()).stream()
                .map(hit -> new RagEvalRunCaseHitDTO(
                    hit.getRank(),
                    hit.getChunkId(),
                    hit.getSimilarityScore(),
                    hit.getRelevanceGrade(),
                    hit.getMatchedGold(),
                    hit.getSourceName(),
                    hit.getChunkPreview(),
                    readMap(hit.getMetadataJson())
                ))
                .toList();
            RagEvalCaseEntity evalCase = runCase.getEvalCase();
            result.add(new RagEvalRunCaseDTO(
                runCase.getId(),
                evalCase.getId(),
                evalCase.getQuestion(),
                evalCase.getExpectedAnswer(),
                runCase.getOriginalQuery(),
                runCase.getRewrittenQuery(),
                runCase.getResolvedQuery(),
                runCase.getAnswerText(),
                runCase.getOverallPass(),
                readMap(runCase.getRetrievalMetricsJson()),
                readMap(runCase.getGenerationMetricsJson()),
                readMap(runCase.getRawJudgeJson()),
                hits
            ));
        }
        return result;
    }

    private CaseExecutionResult executeCase(
        RagEvalRunEntity run,
        RagEvalCaseEntity evalCase,
        RagEvalRunRequest request
    ) {
        List<RagEvalCaseQrelEntity> qrels = qrelRepository.findByEvalCaseIdOrderByIdAsc(evalCase.getId());
        List<Long> knowledgeBaseIds = readLongList(evalCase.getKnowledgeBaseIdsJson());
        RagRetrieverOptions retrieverOptions = resolveRetrieverOptions(request.retrieverConfig());
        RagRetrievalTrace trace = ragRetrieverService.retrieve(knowledgeBaseIds, evalCase.getQuestion(), retrieverOptions);
        String answer = ragAnswerGeneratorService.generateAnswer(evalCase.getQuestion(), trace);
        AnswerWithCitations answerWithCitations = ragAnswerGeneratorService.extractAnswerWithCitations(
            evalCase.getQuestion(),
            answer,
            trace
        );
        RagRetrievalMetrics retrievalMetrics = retrievalMetricsCalculator.calculate(trace.hits(), qrels, trace.topK());
        GenerationJudgeResult judgeResult = ragGenerationJudgeService.evaluate(
            evalCase.getQuestion(),
            evalCase.getExpectedAnswer(),
            trace,
            answerWithCitations
        );

        RagEvalRunCaseEntity runCase = new RagEvalRunCaseEntity();
        runCase.setRun(run);
        runCase.setEvalCase(evalCase);
        runCase.setOriginalQuery(trace.originalQuestion());
        runCase.setRewrittenQuery(trace.rewrittenQuestion());
        runCase.setResolvedQuery(trace.resolvedQuery());
        runCase.setAnswerText(answerWithCitations.answerText());
        runCase.setRetrievalMetricsJson(writeJson(toRetrievalMetricsMap(retrievalMetrics)));
        runCase.setGenerationMetricsJson(writeJson(toGenerationMetricsMap(judgeResult)));
        runCase.setOverallPass(evaluateOverallPass(retrievalMetrics, judgeResult, request.judgeConfig()));
        runCase.setRawJudgeJson(writeJson(Map.of(
            "judgeResult", toGenerationMetricsMap(judgeResult),
            "citations", answerWithCitations.citations() != null ? answerWithCitations.citations() : List.of()
        )));
        runCase = runCaseRepository.save(runCase);

        persistHits(runCase, trace.hits(), qrels);
        return new CaseExecutionResult(runCase, retrievalMetrics, judgeResult);
    }

    private void persistHits(RagEvalRunCaseEntity runCase, List<RetrievedChunk> hits, List<RagEvalCaseQrelEntity> qrels) {
        Map<String, Integer> relevanceMap = new HashMap<>();
        for (RagEvalCaseQrelEntity qrel : qrels) {
            relevanceMap.put(qrel.getChunkId(), qrel.getRelevanceGrade());
        }
        for (RetrievedChunk hit : hits) {
            RagEvalRunHitEntity entity = new RagEvalRunHitEntity();
            entity.setRunCase(runCase);
            entity.setRank(hit.rank());
            entity.setChunkId(hit.chunkId());
            entity.setSimilarityScore(hit.score());
            entity.setRelevanceGrade(relevanceMap.get(hit.chunkId()));
            entity.setMatchedGold(relevanceMap.getOrDefault(hit.chunkId(), 0) > 0);
            entity.setSourceName(asString(hit.metadata().get("source_name")));
            entity.setChunkPreview(truncate(hit.text(), 500));
            entity.setMetadataJson(writeJson(hit.metadata()));
            runHitRepository.save(entity);
        }
    }

    private Map<String, Object> buildSummaryMetrics(List<CaseExecutionResult> results) {
        int caseCount = results.size();
        if (caseCount == 0) {
            return Map.of(
                "caseCount", 0,
                "passRate", 0.0,
                "hallucinationRate", 0.0
            );
        }

        double meanRecall = results.stream().mapToDouble(result -> result.retrievalMetrics().recallAtK()).average().orElse(0.0);
        double meanMrr = results.stream().mapToDouble(result -> result.retrievalMetrics().mrr()).average().orElse(0.0);
        double meanNdcg = results.stream().mapToDouble(result -> result.retrievalMetrics().ndcgAtK()).average().orElse(0.0);
        double meanAccuracy = results.stream().mapToDouble(result -> result.judgeResult().accuracyScore()).average().orElse(0.0);
        double meanCitation = results.stream().mapToDouble(result -> result.judgeResult().citationScore()).average().orElse(0.0);
        double meanConsistency = results.stream().mapToDouble(result -> result.judgeResult().consistencyScore()).average().orElse(0.0);
        long hallucinationCount = results.stream().filter(result -> result.judgeResult().hallucinationFlag()).count();
        long passCount = results.stream().filter(result -> result.runCase().getOverallPass()).count();
        int k = results.getFirst().retrievalMetrics().k();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", caseCount);
        summary.put("k", k);
        summary.put("meanRecallAtK", round4(meanRecall));
        summary.put("meanMrr", round4(meanMrr));
        summary.put("meanNdcgAtK", round4(meanNdcg));
        summary.put("meanAccuracyScore", round4(meanAccuracy));
        summary.put("meanCitationScore", round4(meanCitation));
        summary.put("meanConsistencyScore", round4(meanConsistency));
        summary.put("hallucinationRate", round4((double) hallucinationCount / caseCount));
        summary.put("passRate", round4((double) passCount / caseCount));
        return summary;
    }

    private boolean evaluateOverallPass(
        RagRetrievalMetrics retrievalMetrics,
        GenerationJudgeResult judgeResult,
        Map<String, Object> judgeConfig
    ) {
        double minRecall = asDouble(judgeConfig, "minRecallAtK", 0.5);
        int minAccuracy = asInt(judgeConfig, "minAccuracyScore", 4);
        int minCitation = asInt(judgeConfig, "minCitationScore", 3);
        int minConsistency = asInt(judgeConfig, "minConsistencyScore", 3);
        return retrievalMetrics.recallAtK() >= minRecall
            && judgeResult.accuracyScore() >= minAccuracy
            && judgeResult.citationScore() >= minCitation
            && judgeResult.consistencyScore() >= minConsistency
            && !judgeResult.hallucinationFlag();
    }

    private RagRetrieverOptions resolveRetrieverOptions(Map<String, Object> retrieverConfig) {
        if (retrieverConfig == null || retrieverConfig.isEmpty()) {
            return null;
        }
        return new RagRetrieverOptions(
            asBoolean(retrieverConfig, "rewriteEnabled"),
            asInteger(retrieverConfig, "topK"),
            asDoubleObject(retrieverConfig, "minScore")
        );
    }

    private RagEvalRunDetailDTO buildRunDetail(RagEvalRunEntity run, List<CaseExecutionResult> results) {
        int passCount = (int) results.stream().filter(result -> result.runCase().getOverallPass()).count();
        int hallucinationCaseCount = (int) results.stream().filter(result -> result.judgeResult().hallucinationFlag()).count();
        return new RagEvalRunDetailDTO(
            toRunSummary(run),
            results.size(),
            passCount,
            hallucinationCaseCount
        );
    }

    private RagEvalRunSummaryDTO toRunSummary(RagEvalRunEntity run) {
        return new RagEvalRunSummaryDTO(
            run.getId(),
            run.getDataset().getId(),
            run.getDataset().getName(),
            run.getName(),
            run.getStatus().name(),
            readMap(run.getSummaryMetricsJson()),
            readMap(run.getRetrieverConfigJson()),
            readMap(run.getGeneratorConfigJson()),
            readMap(run.getJudgeConfigJson()),
            run.getFailureMessage(),
            run.getCreatedAt(),
            run.getFinishedAt()
        );
    }

    private Map<String, Object> normalizeConfigMap(Map<String, Object> config) {
        return config != null ? config : Map.of();
    }

    private Map<String, Object> toRetrievalMetricsMap(RagRetrievalMetrics metrics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("k", metrics.k());
        map.put("recallAtK", round4(metrics.recallAtK()));
        map.put("mrr", round4(metrics.mrr()));
        map.put("ndcgAtK", round4(metrics.ndcgAtK()));
        map.put("relevantCount", metrics.relevantCount());
        map.put("retrievedCount", metrics.retrievedCount());
        return map;
    }

    private Map<String, Object> toGenerationMetricsMap(GenerationJudgeResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("accuracyScore", result.accuracyScore());
        map.put("citationScore", result.citationScore());
        map.put("consistencyScore", result.consistencyScore());
        map.put("hallucinationFlag", result.hallucinationFlag());
        map.put("reason", result.reason());
        return map;
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LONG_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.RAG_EVAL_RUN_FAILED, "解析评测 case 的 knowledgeBaseIds 失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.RAG_EVAL_RUN_FAILED, "序列化评测结果失败");
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private Boolean asBoolean(Map<String, Object> config, String key) {
        Object value = config != null ? config.get(key) : null;
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Integer asInteger(Map<String, Object> config, String key) {
        Object value = config != null ? config.get(key) : null;
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Double asDoubleObject(Map<String, Object> config, String key) {
        Object value = config != null ? config.get(key) : null;
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private int asInt(Map<String, Object> config, String key, int defaultValue) {
        Integer value = asInteger(config, key);
        return value != null ? value : defaultValue;
    }

    private double asDouble(Map<String, Object> config, String key, double defaultValue) {
        Double value = asDoubleObject(config, key);
        return value != null ? value : defaultValue;
    }
}
