package interview.guide.modules.knowledgebase.service.rag;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.model.rag.AnswerCitation;
import interview.guide.modules.knowledgebase.model.rag.AnswerWithCitations;
import interview.guide.modules.knowledgebase.model.rag.RagRetrievalTrace;
import interview.guide.modules.knowledgebase.model.rag.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG 生成阶段服务。
 */
@Slf4j
@Service
public class RagAnswerGeneratorService {

    private static final int STREAM_PROBE_CHARS = 120;

    private final ChatClient chatClient;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate citationSystemPromptTemplate;
    private final PromptTemplate citationUserPromptTemplate;
    private final BeanOutputConverter<CitationExtractionResult> citationOutputConverter;

    private record CitationExtractionResult(List<CitationItem> citations) {
    }

    private record CitationItem(String claimText, String chunkId, String supportExcerpt) {
    }

    public RagAnswerGeneratorService(
        ChatClient.Builder chatClientBuilder,
        StructuredOutputInvoker structuredOutputInvoker,
        @Value("classpath:prompts/knowledgebase-query-system.st") Resource systemPromptResource,
        @Value("classpath:prompts/knowledgebase-query-user.st") Resource userPromptResource,
        @Value("classpath:prompts/knowledgebase-citation-system.st") Resource citationSystemPromptResource,
        @Value("classpath:prompts/knowledgebase-citation-user.st") Resource citationUserPromptResource
    ) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.citationSystemPromptTemplate = new PromptTemplate(citationSystemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.citationUserPromptTemplate = new PromptTemplate(citationUserPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.citationOutputConverter = new BeanOutputConverter<>(CitationExtractionResult.class);
    }

    public String generateAnswer(String question, RagRetrievalTrace trace) {
        if (trace == null || !trace.effectiveHit()) {
            return RagResponseConstants.NO_RESULT_RESPONSE;
        }

        try {
            String answer = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(buildUserPrompt(trace.contextText(), question))
                .call()
                .content();
            return normalizeAnswer(answer);
        } catch (Exception e) {
            log.error("知识库问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库查询失败：" + e.getMessage());
        }
    }

    public Flux<String> generateAnswerStream(String question, RagRetrievalTrace trace) {
        if (trace == null || !trace.effectiveHit()) {
            return Flux.just(RagResponseConstants.NO_RESULT_RESPONSE);
        }

        try {
            Flux<String> responseFlux = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(buildUserPrompt(trace.contextText(), question))
                .stream()
                .content();
            return normalizeStreamOutput(responseFlux)
                .onErrorResume(e -> {
                    log.error("流式输出失败: error={}", e.getMessage(), e);
                    return Flux.just("【错误】知识库查询失败：AI服务暂时不可用，请稍后重试。");
                });
        } catch (Exception e) {
            log.error("知识库流式问答失败: {}", e.getMessage(), e);
            return Flux.just("【错误】知识库查询失败：" + e.getMessage());
        }
    }

    public AnswerWithCitations extractAnswerWithCitations(String question, String answer, RagRetrievalTrace trace) {
        String normalizedAnswer = normalizeAnswer(answer);
        if (trace == null || trace.hits() == null || trace.hits().isEmpty()
            || RagResponseConstants.NO_RESULT_RESPONSE.equals(normalizedAnswer)) {
            return new AnswerWithCitations(normalizedAnswer, List.of());
        }

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            variables.put("answer", normalizedAnswer);
            variables.put("retrievedChunks", buildRetrievedChunksText(trace.hits()));
            String userPrompt = citationUserPromptTemplate.render(variables);
            String systemPrompt = citationSystemPromptTemplate.render() + "\n\n" + citationOutputConverter.getFormat();
            CitationExtractionResult result = structuredOutputInvoker.invoke(
                chatClient,
                systemPrompt,
                userPrompt,
                citationOutputConverter,
                ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED,
                "知识库引用抽取失败：",
                "知识库引用抽取",
                log
            );

            Set<String> availableChunkIds = trace.hits().stream()
                .map(RetrievedChunk::chunkId)
                .collect(java.util.stream.Collectors.toSet());
            List<AnswerCitation> citations = result != null && result.citations() != null
                ? result.citations().stream()
                    .filter(item -> item != null && item.chunkId() != null && availableChunkIds.contains(item.chunkId()))
                    .map(item -> new AnswerCitation(
                        item.claimText() != null ? item.claimText().trim() : "",
                        item.chunkId().trim(),
                        item.supportExcerpt() != null ? item.supportExcerpt().trim() : ""
                    ))
                    .toList()
                : List.of();

            return new AnswerWithCitations(normalizedAnswer, citations);
        } catch (Exception e) {
            log.warn("引用抽取失败，降级为无引用结果: {}", e.getMessage());
            return new AnswerWithCitations(normalizedAnswer, List.of());
        }
    }

    private String buildSystemPrompt() {
        return systemPromptTemplate.render();
    }

    private String buildUserPrompt(String context, String question) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", question);
        return userPromptTemplate.render(variables);
    }

    private String buildRetrievedChunksText(List<RetrievedChunk> hits) {
        StringBuilder builder = new StringBuilder();
        for (RetrievedChunk hit : hits) {
            builder.append("[chunk_id=").append(hit.chunkId()).append(", rank=").append(hit.rank());
            if (hit.score() != null) {
                builder.append(", score=").append(String.format("%.6f", hit.score()));
            }
            builder.append("]\n");
            builder.append(hit.text() != null ? hit.text() : "").append("\n\n");
        }
        return builder.toString().trim();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return RagResponseConstants.NO_RESULT_RESPONSE;
        }
        String normalized = answer.trim();
        if (isNoResultLike(normalized)) {
            return RagResponseConstants.NO_RESULT_RESPONSE;
        }
        return normalized;
    }

    private boolean isNoResultLike(String text) {
        return text.contains("没有找到相关信息")
            || text.contains("未检索到相关信息")
            || text.contains("信息不足")
            || text.contains("超出知识库范围")
            || text.contains("无法根据提供内容回答");
    }

    private Flux<String> normalizeStreamOutput(Flux<String> rawFlux) {
        return Flux.create(sink -> {
            StringBuilder probeBuffer = new StringBuilder();
            AtomicBoolean passthrough = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            final Disposable[] disposableRef = new Disposable[1];

            disposableRef[0] = rawFlux.subscribe(
                chunk -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (passthrough.get()) {
                        sink.next(chunk);
                        return;
                    }

                    probeBuffer.append(chunk);
                    String probeText = probeBuffer.toString();
                    if (isNoResultLike(probeText)) {
                        completed.set(true);
                        sink.next(RagResponseConstants.NO_RESULT_RESPONSE);
                        sink.complete();
                        if (disposableRef[0] != null) {
                            disposableRef[0].dispose();
                        }
                        return;
                    }

                    if (probeBuffer.length() >= STREAM_PROBE_CHARS) {
                        passthrough.set(true);
                        sink.next(probeText);
                    }
                },
                sink::error,
                () -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (!passthrough.get() && !probeBuffer.isEmpty()) {
                        sink.next(probeBuffer.toString());
                    }
                    sink.complete();
                }
            );
        });
    }
}
