package interview.guide.modules.knowledgebase.model.rag;

/**
 * 检索阶段运行时覆盖配置。
 */
public record RagRetrieverOptions(
    Boolean rewriteEnabled,
    Integer topK,
    Double minScore
) {
}
