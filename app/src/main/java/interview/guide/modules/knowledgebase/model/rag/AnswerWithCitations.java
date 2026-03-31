package interview.guide.modules.knowledgebase.model.rag;

import java.util.List;

/**
 * 生成答案及其归因引用。
 */
public record AnswerWithCitations(
    String answerText,
    List<AnswerCitation> citations
) {
}
