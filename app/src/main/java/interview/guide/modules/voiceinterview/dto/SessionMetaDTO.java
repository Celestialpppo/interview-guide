package interview.guide.modules.voiceinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Session metadata for list display
 * 会话元数据（用于列表展示)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMetaDTO {
    /**
     * Session ID
     */
    private Long sessionId;

    /**
     * Interview role type
     */
    private String roleType;

    /**
     * Session status
     */
    private String status;

    /**
     * Current interview phase
     */
    private String currentPhase;

    /**
     * Creation time
     */
    private LocalDateTime createdAt;

    /**
     * Last update time
     */
    private LocalDateTime updatedAt;

    /**
     * Actual duration in seconds
     */
    private Integer actualDuration;

    /**
     * Number of messages
     */
    private Long messageCount;
}
