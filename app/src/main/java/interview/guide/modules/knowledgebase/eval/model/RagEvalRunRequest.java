package interview.guide.modules.knowledgebase.eval.model;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 创建评测任务请求。
 * retrieverConfig:
 *   - rewriteEnabled
 *   - topK
 *   - minScore
 */
public record RagEvalRunRequest(
    @NotNull(message = "datasetId 不能为空")
    Long datasetId,
    String name,
    Map<String, Object> retrieverConfig,
    Map<String, Object> generatorConfig,
    Map<String, Object> judgeConfig
) {
}
