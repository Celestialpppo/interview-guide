package interview.guide.modules.knowledgebase.eval.model;

/**
 * 数据集导入结果。
 */
public record RagEvalDatasetImportResponse(
    Long datasetId,
    String name,
    String version,
    int importedCaseCount,
    int importedQrelCount
) {
}
