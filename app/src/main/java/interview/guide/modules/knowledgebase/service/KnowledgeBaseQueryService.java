package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.QueryRequest;
import interview.guide.modules.knowledgebase.model.QueryResponse;
import interview.guide.modules.knowledgebase.model.rag.RagRetrievalTrace;
import interview.guide.modules.knowledgebase.service.rag.RagAnswerGeneratorService;
import interview.guide.modules.knowledgebase.service.rag.RagResponseConstants;
import interview.guide.modules.knowledgebase.service.rag.RagRetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 知识库查询服务
 * 基于向量搜索的 RAG 问答编排层。
 */
@Slf4j
@Service
public class KnowledgeBaseQueryService {

    private final RagRetrieverService ragRetrieverService;
    private final RagAnswerGeneratorService ragAnswerGeneratorService;
    private final KnowledgeBaseListService listService;
    private final KnowledgeBaseCountService countService;

    public KnowledgeBaseQueryService(
        RagRetrieverService ragRetrieverService,
        RagAnswerGeneratorService ragAnswerGeneratorService,
        KnowledgeBaseListService listService,
        KnowledgeBaseCountService countService
    ) {
        this.ragRetrieverService = ragRetrieverService;
        this.ragAnswerGeneratorService = ragAnswerGeneratorService;
        this.listService = listService;
        this.countService = countService;
    }

    /**
     * 基于单个知识库回答用户问题。
     */
    public String answerQuestion(Long knowledgeBaseId, String question) {
        return answerQuestion(List.of(knowledgeBaseId), question);
    }

    /**
     * 基于多个知识库回答用户问题（RAG）。
     */
    public String answerQuestion(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || question == null || question.trim().isBlank()) {
            return RagResponseConstants.NO_RESULT_RESPONSE;
        }

        countService.updateQuestionCounts(knowledgeBaseIds);
        RagRetrievalTrace trace = ragRetrieverService.retrieve(knowledgeBaseIds, question);
        if (!trace.effectiveHit()) {
            return RagResponseConstants.NO_RESULT_RESPONSE;
        }

        String answer = ragAnswerGeneratorService.generateAnswer(question, trace);
        log.info("知识库问答完成: kbIds={}, hits={}", knowledgeBaseIds, trace.hits().size());
        return answer;
    }

    /**
     * 查询知识库并返回完整响应。
     */
    public QueryResponse queryKnowledgeBase(QueryRequest request) {
        String answer = answerQuestion(request.knowledgeBaseIds(), request.question());
        List<String> kbNames = listService.getKnowledgeBaseNames(request.knowledgeBaseIds());
        String kbNamesStr = String.join("、", kbNames);
        Long primaryKbId = request.knowledgeBaseIds().getFirst();
        return new QueryResponse(answer, primaryKbId, kbNamesStr);
    }

    /**
     * 流式查询知识库（SSE）。
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库流式提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || question == null || question.trim().isBlank()) {
            return Flux.just(RagResponseConstants.NO_RESULT_RESPONSE);
        }

        try {
            countService.updateQuestionCounts(knowledgeBaseIds);
            RagRetrievalTrace trace = ragRetrieverService.retrieve(knowledgeBaseIds, question);
            if (!trace.effectiveHit()) {
                return Flux.just(RagResponseConstants.NO_RESULT_RESPONSE);
            }
            return ragAnswerGeneratorService.generateAnswerStream(question, trace)
                .doOnComplete(() -> log.info("流式输出完成: kbIds={}", knowledgeBaseIds));
        } catch (Exception e) {
            log.error("知识库流式问答失败: {}", e.getMessage(), e);
            return Flux.just("【错误】知识库查询失败：" + e.getMessage());
        }
    }
}
