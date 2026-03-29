package interview.guide.modules.voiceinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {
    private String roleType; // "ali-p8", "byteance-algo", "tencent-backend"
    private String customJdText;
    private Long resumeId; // Add resumeId

    @Builder.Default
    private Boolean introEnabled = false; // Default to false
    @Builder.Default
    private Boolean techEnabled = true;
    @Builder.Default
    private Boolean projectEnabled = true;
    @Builder.Default
    private Boolean hrEnabled = true;
    @Builder.Default
    private Integer plannedDuration = 30;
}
