package interview.guide.modules.voiceinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Evaluation Response DTO
 * 语音面试评估响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponseDTO {
    private Long sessionId;
    private Integer overallScore;
    private String overallRating;
    private Map<String, Object> techKnowledge; // {score: 80, comment: "..."}
    private Map<String, Object> projectExp; // {score: 90, comment: "..."}
    private Map<String, Object> communication; // {score: 85, comment: "..."}
    private Map<String, Object> logicalThinking; // {score: 75, comment: "..."}
    private List<String> improvementSuggestions;
    private String strengthsSummary;
}
