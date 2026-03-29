package interview.guide.modules.voiceinterview.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.voiceinterview.dto.CreateSessionRequest;
import interview.guide.modules.voiceinterview.dto.SessionMetaDTO;
import interview.guide.modules.voiceinterview.dto.SessionResponseDTO;
import interview.guide.modules.voiceinterview.model.VoiceInterviewMessageEntity;
import interview.guide.modules.voiceinterview.service.VoiceInterviewEvaluationService;
import interview.guide.modules.voiceinterview.service.VoiceInterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Voice Interview Controller
 * 语音面试控制器
 * <p>
 * REST API endpoints for voice interview session management:
 * - Session lifecycle (create, retrieve, end)
 * - Message history retrieval
 * - Evaluation generation and retrieval
 * </p>
 */
@RestController
@RequestMapping("/api/voice-interview")
@RequiredArgsConstructor
@Slf4j
public class VoiceInterviewController {

    private final VoiceInterviewService voiceInterviewService;
    private final VoiceInterviewEvaluationService evaluationService;

    /**
     * Create a new voice interview session
     * 创建新的语音面试会话
     *
     * @param request Session creation request with role type and phase configuration
     * @return SessionResponseDTO with session details and WebSocket URL
     */
    @PostMapping("/sessions")
    public Result<SessionResponseDTO> createSession(@RequestBody CreateSessionRequest request) {
        log.info("Creating voice interview session for role: {}", request.getRoleType());
        SessionResponseDTO session = voiceInterviewService.createSession(request);
        return Result.success(session);
    }

    /**
     * Get session details by ID
     * 获取会话详情
     *
     * @param sessionId Session ID
     * @return SessionResponseDTO with session details
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<SessionResponseDTO> getSession(@PathVariable Long sessionId) {
        log.info("Getting session details for: {}", sessionId);
        SessionResponseDTO session = voiceInterviewService.getSessionDTO(sessionId);
        if (session == null) {
            return Result.error("Session not found: " + sessionId);
        }
        return Result.success(session);
    }

    /**
     * End interview session
     * 结束面试会话
     *
     * @param sessionId Session ID
     * @return Success result
     */
    @PostMapping("/sessions/{sessionId}/end")
    public Result<Void> endSession(@PathVariable Long sessionId) {
        log.info("Ending session: {}", sessionId);
        voiceInterviewService.endSession(sessionId.toString());
        return Result.success();
    }

    /**
     * Pause interview session
     * 暂停面试会话
     *
     * @param sessionId Session ID
     * @param request Pause request with reason
     * @return Success result
     */
    @PutMapping("/sessions/{sessionId}/pause")
    public Result<Void> pauseSession(
        @PathVariable Long sessionId,
        @RequestBody Map<String, String> request
    ) {
        log.info("Pausing session: {}", sessionId);
        String reason = request.getOrDefault("reason", "user_initiated");
        voiceInterviewService.pauseSession(sessionId.toString(), reason);
        return Result.success();
    }

    /**
     * Resume interview session
     * 恢复面试会话
     *
     * @param sessionId Session ID
     * @return SessionResponseDTO with WebSocket URL
     */
    @PutMapping("/sessions/{sessionId}/resume")
    public Result<SessionResponseDTO> resumeSession(@PathVariable Long sessionId) {
        log.info("Resuming session: {}", sessionId);
        SessionResponseDTO session = voiceInterviewService.resumeSession(sessionId.toString());
        return Result.success(session);
    }

    /**
     * Get all sessions for user
     * 获取用户所有会话
     *
     * @param userId User ID (optional)
     * @param status Filter by status (optional)
     * @return List of session metadata
     */
    @GetMapping("/sessions")
    public Result<List<SessionMetaDTO>> getAllSessions(
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String status
    ) {
        log.info("Getting sessions for user: {}, status: {}", userId, status);
        try {
            List<SessionMetaDTO> sessions = voiceInterviewService.getAllSessions(userId, status);
            return Result.success(sessions);
        } catch (Exception e) {
            log.error("Failed to get sessions", e);
            return Result.error(500, "获取会话列表失败: " + e.getMessage());
        }
    }

    /**
     * Get conversation history for a session
     * 获取会话的对话历史记录
     *
     * @param sessionId Session ID
     * @return List of messages ordered by sequence number
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<VoiceInterviewMessageEntity>> getMessages(@PathVariable Long sessionId) {
        log.info("Getting messages for session: {}", sessionId);
        List<VoiceInterviewMessageEntity> messages =
                voiceInterviewService.getConversationHistory(sessionId.toString());
        return Result.success(messages);
    }

    /**
     * Get evaluation for a session
     * 获取会话评估结果
     *
     * @param sessionId Session ID
     * @return Evaluation result
     */
    @GetMapping("/sessions/{sessionId}/evaluation")
    public Result<Object> getEvaluation(@PathVariable Long sessionId) {
        log.info("Getting evaluation for session: {}", sessionId);
        Object evaluation = evaluationService.getEvaluation(sessionId);
        return Result.success(evaluation);
    }

    /**
     * Generate evaluation for a session
     * 生成会话评估结果
     *
     * @param sessionId Session ID
     * @return Generated evaluation result
     */
    @PostMapping("/sessions/{sessionId}/evaluation")
    public Result<Object> generateEvaluation(@PathVariable Long sessionId) {
        log.info("Generating evaluation for session: {}", sessionId);
        Object evaluation = evaluationService.generateEvaluation(sessionId);
        return Result.success(evaluation);
    }
}
