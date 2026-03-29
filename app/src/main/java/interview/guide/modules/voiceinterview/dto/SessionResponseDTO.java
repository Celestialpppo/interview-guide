package interview.guide.modules.voiceinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Session response with WebSocket URL
 * 会话响应（包含WebSocket URL）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponseDTO {
    /**
     * Session ID
     */
    private Long sessionId;

    /**
     * Interview role type
     */
    private String roleType;

    /**
     * Current interview phase
     */
    private String currentPhase;

    /**
     * Session status
     */
    private String status;

    /**
     * Session start time
     */
    private LocalDateTime startTime;

    /**
     * Planned duration in minutes
     */
    private Integer plannedDuration;

    /**
     * WebSocket URL for connection
     */
    private String webSocketUrl;
}
