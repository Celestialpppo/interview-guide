package interview.guide.modules.knowledgebase.eval.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.eval.model.GenerationJudgeResult;
import interview.guide.modules.knowledgebase.model.rag.AnswerCitation;
import interview.guide.modules.knowledgebase.model.rag.AnswerWithCitations;
import interview.guide.modules.knowledgebase.model.rag.RagRetrievalTrace;
import interview.guide.modules.knowledgebase.model.rag.RetrievedChunk;
import interview.guide.modules.knowledgebase.service.rag.RagResponseConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成阶段自动评测。
 */
@Slf4j
@Service
public class RagGenerationJudgeService {

    private final ChatClient chatClient;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<GenerationJudgeResult> outputConverter;

    public RagGenerationJudgeService(
        ChatClient.Builder chatClientBuilder,
        StructuredOutputInvoker structuredOutputInvoker,
        ObjectMapper objectMapper,
        @Value("classpath:prompts/knowledgebase-answer-judge-system.st") Resource systemPromptResource,
        @Value("classpath:prompts/knowledgebase-answer-judge-user.st") Resource userPromptResource
    ) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.objectMapper = objectMapper;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(GenerationJudgeResult.class);
    }

    public GenerationJudgeResult evaluate(
        String question,
        String expectedAnswer,
        RagRetrievalTrace trace,
        AnswerWithCitations answerWithCitations
    ) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", defaultString(question));
            variables.put("expectedAnswer", defaultString(expectedAnswer));
            variables.put("answer", defaultString(answerWithCitations.answerText()));
            variables.put("citationsJson", writeJson(answerWithCitations.citations()));
            variables.put("evidence", buildEvidence(trace));
            variables.put("ruleChecks", buildRuleChecks(answerWithCitations));

            String systemPrompt = systemPromptTemplate.render() + "\n\n" + outputConverter.getFormat();
            String userPrompt = userPromptTemplate.render(variables);
            GenerationJudgeResult judge = structuredOutputInvoker.invoke(
                chatClient,
                systemPrompt,
                userPrompt,
                outputConverter,
                ErrorCode.RAG_EVAL_RUN_FAILED,
                "RAG 生成评测失败：",
                "RAG 生成评测",
                log
            );
            return applyRuleGuards(judge, answerWithCitations);
        } catch (Exception e) {
            log.warn("生成评测失败，回退到保守评分: {}", e.getMessage());
            return fallbackJudge(answerWithCitations);
        }
    }

    private GenerationJudgeResult applyRuleGuards(GenerationJudgeResult judge, AnswerWithCitations answerWithCitations) {
        int accuracyScore = clampScore(judge.accuracyScore());
        int citationScore = clampScore(judge.citationScore());
        int consistencyScore = clampScore(judge.consistencyScore());
        boolean noCitationButHasAnswer = answerWithCitations.answerText() != null
            && !answerWithCitations.answerText().isBlank()
            && !RagResponseConstants.NO_RESULT_RESPONSE.equals(answerWithCitations.answerText())
            && (answerWithCitations.citations() == null || answerWithCitations.citations().isEmpty());

        if (noCitationButHasAnswer) {
            citationScore = Math.min(citationScore, 1);
        }

        return new GenerationJudgeResult(
            accuracyScore,
            citationScore,
            consistencyScore,
            judge.hallucinationFlag(),
            defaultString(judge.reason())
        );
    }

    private GenerationJudgeResult fallbackJudge(AnswerWithCitations answerWithCitations) {
        boolean noResult = RagResponseConstants.NO_RESULT_RESPONSE.equals(answerWithCitations.answerText());
        int citationScore = noResult ? 3 : (answerWithCitations.citations() == null || answerWithCitations.citations().isEmpty() ? 1 : 3);
        return new GenerationJudgeResult(
            noResult ? 3 : 2,
            citationScore,
            3,
            false,
            "评测模型调用失败，返回保守估计结果。"
        );
    }

    private String buildEvidence(RagRetrievalTrace trace) {
        if (trace == null || trace.hits() == null || trace.hits().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (RetrievedChunk hit : trace.hits()) {
            builder.append("[chunk_id=").append(hit.chunkId()).append(", rank=").append(hit.rank());
            if (hit.score() != null) {
                builder.append(", score=").append(String.format("%.6f", hit.score()));
            }
            builder.append("]\n")
                .append(defaultString(hit.text()))
                .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildRuleChecks(AnswerWithCitations answerWithCitations) {
        int citationCount = answerWithCitations.citations() != null ? answerWithCitations.citations().size() : 0;
        return "citation_count=" + citationCount
            + ", no_result_answer=" + RagResponseConstants.NO_RESULT_RESPONSE.equals(answerWithCitations.answerText());
    }

    private int clampScore(int score) {
        return Math.max(1, Math.min(5, score));
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private String writeJson(List<AnswerCitation> citations) {
        try {
            return objectMapper.writeValueAsString(citations != null ? citations : List.of());
        } catch (JacksonException e) {
            return "[]";
        }
    }
}
