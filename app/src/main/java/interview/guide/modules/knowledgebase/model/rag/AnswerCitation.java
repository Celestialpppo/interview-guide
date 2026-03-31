package interview.guide.modules.knowledgebase.model.rag;

/**
 * 答案中的单条引用。
 */
public record AnswerCitation(
    String claimText,
    String chunkId,
    String supportExcerpt
) {
}
