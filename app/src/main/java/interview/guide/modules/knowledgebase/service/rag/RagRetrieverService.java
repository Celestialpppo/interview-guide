package interview.guide.modules.knowledgebase.service.rag;

import interview.guide.modules.knowledgebase.model.rag.RagRetrievalTrace;
import interview.guide.modules.knowledgebase.model.rag.RagRetrieverOptions;
import interview.guide.modules.knowledgebase.model.rag.RetrievedChunk;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RAG 检索阶段服务。
 */
@Slf4j
@Service
public class RagRetrieverService {

    private static final Pattern SHORT_TOKEN_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{2,20}$");

    private final ChatClient chatClient;
    private final KnowledgeBaseVectorService vectorService;
    private final PromptTemplate rewritePromptTemplate;
    private final boolean rewriteEnabled;
    private final int shortQueryLength;
    private final int topkShort;
    private final int topkMedium;
    private final int topkLong;
    private final double minScoreShort;
    private final double minScoreDefault;

    private record SearchParams(int topK, double minScore) {
    }

    public RagRetrieverService(
        ChatClient.Builder chatClientBuilder,
        KnowledgeBaseVectorService vectorService,
        @Value("classpath:prompts/knowledgebase-query-rewrite.st") Resource rewritePromptResource,
        @Value("${app.ai.rag.rewrite.enabled:true}") boolean rewriteEnabled,
        @Value("${app.ai.rag.search.short-query-length:4}") int shortQueryLength,
        @Value("${app.ai.rag.search.topk-short:20}") int topkShort,
        @Value("${app.ai.rag.search.topk-medium:12}") int topkMedium,
        @Value("${app.ai.rag.search.topk-long:8}") int topkLong,
        @Value("${app.ai.rag.search.min-score-short:0.18}") double minScoreShort,
        @Value("${app.ai.rag.search.min-score-default:0.28}") double minScoreDefault
    ) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.vectorService = vectorService;
        this.rewritePromptTemplate = new PromptTemplate(rewritePromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.rewriteEnabled = rewriteEnabled;
        this.shortQueryLength = shortQueryLength;
        this.topkShort = topkShort;
        this.topkMedium = topkMedium;
        this.topkLong = topkLong;
        this.minScoreShort = minScoreShort;
        this.minScoreDefault = minScoreDefault;
    }

    public RagRetrievalTrace retrieve(List<Long> knowledgeBaseIds, String question) {
        return retrieve(knowledgeBaseIds, question, null);
    }

    public RagRetrievalTrace retrieve(List<Long> knowledgeBaseIds, String question, RagRetrieverOptions options) {
        String normalizedQuestion = normalizeQuestion(question);
        SearchParams params = resolveSearchParams(normalizedQuestion, options);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizedQuestion.isBlank()) {
            return new RagRetrievalTrace(
                normalizedQuestion,
                normalizedQuestion,
                normalizedQuestion,
                params.topK(),
                params.minScore(),
                false,
                List.of()
            );
        }

        String rewrittenQuestion = rewriteQuestion(normalizedQuestion, options);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(rewrittenQuestion);
        candidates.add(normalizedQuestion);

        RagRetrievalTrace fallbackTrace = null;
        for (String candidateQuery : candidates) {
            if (candidateQuery == null || candidateQuery.isBlank()) {
                continue;
            }
            List<Document> docs = vectorService.similaritySearch(
                candidateQuery,
                knowledgeBaseIds,
                params.topK(),
                params.minScore()
            );
            boolean effectiveHit = hasEffectiveHit(candidateQuery, docs);
            RagRetrievalTrace currentTrace = buildTrace(
                normalizedQuestion,
                rewrittenQuestion,
                candidateQuery,
                params,
                docs,
                effectiveHit
            );
            log.info("检索候选 query='{}'，命中 {} 条，effectiveHit={}", candidateQuery, docs.size(), effectiveHit);
            if (fallbackTrace == null || (!currentTrace.hits().isEmpty() && fallbackTrace.hits().isEmpty())) {
                fallbackTrace = currentTrace;
            }
            if (effectiveHit) {
                return currentTrace;
            }
        }

        if (fallbackTrace != null) {
            return fallbackTrace;
        }

        return new RagRetrievalTrace(
            normalizedQuestion,
            rewrittenQuestion,
            normalizedQuestion,
            params.topK(),
            params.minScore(),
            false,
            List.of()
        );
    }

    private RagRetrievalTrace buildTrace(
        String originalQuestion,
        String rewrittenQuestion,
        String resolvedQuery,
        SearchParams params,
        List<Document> docs,
        boolean effectiveHit
    ) {
        List<RetrievedChunk> hits = new ArrayList<>();
        int rank = 1;
        if (docs != null) {
            for (Document doc : docs) {
                Map<String, Object> metadata = doc.getMetadata() == null
                    ? Map.of()
                    : new HashMap<>(doc.getMetadata());
                String chunkId = metadata.get("chunk_id") != null
                    ? metadata.get("chunk_id").toString()
                    : (doc.getId() != null ? doc.getId() : "rank-" + rank);
                hits.add(new RetrievedChunk(
                    chunkId,
                    rank,
                    doc.getScore(),
                    doc.getText(),
                    metadata
                ));
                rank++;
            }
        }

        return new RagRetrievalTrace(
            originalQuestion,
            rewrittenQuestion,
            resolvedQuery,
            params.topK(),
            params.minScore(),
            effectiveHit,
            hits
        );
    }

    private SearchParams resolveSearchParams(String question, RagRetrieverOptions options) {
        SearchParams defaults;
        int compactLength = question.replaceAll("\\s+", "").length();
        if (compactLength <= shortQueryLength) {
            defaults = new SearchParams(topkShort, minScoreShort);
        } else if (compactLength <= 12) {
            defaults = new SearchParams(topkMedium, minScoreDefault);
        } else {
            defaults = new SearchParams(topkLong, minScoreDefault);
        }

        if (options == null) {
            return defaults;
        }

        return new SearchParams(
            options.topK() != null ? options.topK() : defaults.topK(),
            options.minScore() != null ? options.minScore() : defaults.minScore()
        );
    }

    private String rewriteQuestion(String question, RagRetrieverOptions options) {
        boolean rewriteEnabledForRequest = options != null && options.rewriteEnabled() != null
            ? options.rewriteEnabled()
            : rewriteEnabled;
        if (!rewriteEnabledForRequest || question.isBlank()) {
            return question;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            String rewritePrompt = rewritePromptTemplate.render(variables);
            String rewritten = chatClient.prompt()
                .user(rewritePrompt)
                .call()
                .content();
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String normalized = rewritten.trim();
            log.info("Query rewrite: origin='{}', rewritten='{}'", question, normalized);
            return normalized;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，使用原问题继续检索: {}", e.getMessage());
            return question;
        }
    }

    private boolean hasEffectiveHit(String question, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return false;
        }

        String normalized = normalizeQuestion(question);
        if (!isShortTokenQuery(normalized)) {
            return true;
        }

        String loweredToken = normalized.toLowerCase();
        for (Document doc : docs) {
            String text = doc.getText();
            if (text != null && text.toLowerCase().contains(loweredToken)) {
                return true;
            }
        }

        log.info("短 query 命中确认失败，视为无有效结果: question='{}', docs={}", normalized, docs.size());
        return false;
    }

    private boolean isShortTokenQuery(String question) {
        if (question == null) {
            return false;
        }
        return SHORT_TOKEN_PATTERN.matcher(question.trim()).matches();
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim();
    }
}
