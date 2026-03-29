package interview.guide.modules.voiceinterview.handler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import interview.guide.modules.voiceinterview.dto.WebSocketControlMessage;
import interview.guide.modules.voiceinterview.dto.WebSocketSubtitleMessage;
import interview.guide.modules.voiceinterview.model.VoiceInterviewMessageEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.service.QwenAsrService;
import interview.guide.modules.voiceinterview.service.QwenTtsService;
import interview.guide.modules.voiceinterview.service.LlmService;
import interview.guide.modules.voiceinterview.service.VoiceInterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket Handler for Voice Interview
 * 语音面试 WebSocket 处理器
 * <p>
 * Handles real-time bidirectional audio streaming for voice interviews.
 * Processing pipeline: User Audio → STT → LLM → TTS → AI Audio
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceInterviewWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final QwenAsrService sttService;
    private final QwenTtsService ttsService;
    private final LlmService llmService;
    private final VoiceInterviewService interviewService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    // Activity tracking for pause timeout
    // 活动跟踪（用于暂停超时）
    private final Map<String, Long> lastActivityTime = new ConcurrentHashMap<>();
    private static final long WARNING_TIME_MS = (long) (4.5 * 60 * 1000);  // 4:30
    private static final long PAUSE_TIMEOUT_MS = 5 * 60 * 1000;            // 5:00

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);

        // Increase message size limits for audio streaming
        // 1 second of PCM audio @ 16kHz, 16-bit = ~32KB raw, ~42KB base64
        // Set limit to 256KB to allow some buffer and multiple messages
        session.setTextMessageSizeLimit(256 * 1024); // 256KB
        session.setBinaryMessageSizeLimit(256 * 1024); // 256KB

        sessions.put(sessionId, session);
        sessionStates.put(sessionId, new SessionState());
        lastActivityTime.put(sessionId, System.currentTimeMillis());
        log.info("WebSocket connection established for session: {}", sessionId);

        try {
            // Start STT transcription session
            sttService.startTranscription(
                sessionId,
                // On result callback
                recognizedText -> handleSttResult(sessionId, recognizedText),
                // On error callback
                error -> {
                    log.error("STT error for session {}", sessionId, error);
                    sendError(session, "语音识别失败: " + error.getMessage());
                }
            );

            // 发送欢迎消息
            sendMessage(session, createWelcomeMessage());
        } catch (Exception e) {
            log.error("Error establishing WebSocket connection for session {}", sessionId, e);
            sendError(session, "初始化语音识别失败: " + e.getMessage());
        }
    }

    /**
     * Create welcome message
     */
    private String createWelcomeMessage() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "type", "control",
                "action", "welcome",
                "message", "连接成功，准备开始语音面试",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error creating welcome message", e);
            return "{\"type\":\"control\",\"action\":\"welcome\"}";
        }
    }

    /**
     * Send message to WebSocket session
     */
    private void sendMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
                log.debug("Message sent to session: {}", message.substring(0, Math.min(100, message.length())));
            } else {
                log.warn("Session is closed, cannot send message");
            }
        } catch (Exception e) {
            log.error("Error sending message to session", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = extractSessionId(session);

        // ⚠️ CRITICAL: Log message size for debugging
        int messageSize = message.getPayload().length();
        int messageSizeKB = messageSize / 1024;
        log.info("[WebSocket] Received message: sessionId={}, size={}KB ({} bytes), limit={}KB",
                sessionId, messageSizeKB, messageSize, session.getTextMessageSizeLimit() / 1024);

        if (messageSizeKB > 200) {
            log.warn("[WebSocket] Large message detected: {}KB", messageSizeKB);
        }

        try {
            JsonNode msg = objectMapper.readTree(message.getPayload());
            String type = msg.get("type").asText();

            // Update last activity time for pause timeout detection
            // 更新最后活动时间（用于暂停超时检测）
            lastActivityTime.put(sessionId, System.currentTimeMillis());

            switch (type) {
                case "audio":
                    String audioData = msg.has("data") ? msg.get("data").asText() : null;
                    if (audioData != null && !audioData.isEmpty()) {
                        handleUserAudio(sessionId, audioData);
                    } else {
                        log.warn("Received audio message without data");
                    }
                    break;
                case "control":
                    try {
                        handleControl(sessionId, objectMapper.treeToValue(msg, WebSocketControlMessage.class));
                    } catch (Exception e) {
                        log.error("Error handling control message for session {}", sessionId, e);
                        sendError(session, "控制消息处理失败: " + e.getMessage());
                    }
                    break;
                default:
                    log.warn("Unknown message type: {} for session {}", type, sessionId);
            }

        } catch (Exception e) {
            log.error("Error handling message for session {}", sessionId, e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        sessions.remove(sessionId);
        sessionStates.remove(sessionId);
        lastActivityTime.remove(sessionId);

        // Stop STT transcription
        try {
            sttService.stopTranscription(sessionId);
            log.info("WebSocket connection closed for session: {}, status: {}", sessionId, status);
        } catch (Exception e) {
            log.error("Error stopping transcription for session {}", sessionId, e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}", extractSessionId(session), exception);
    }

    /**
     * Handle user audio message
     * Just send audio to STT transcriber (results come via callback)
     */
    private void handleUserAudio(String sessionId, String base64Audio) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }

        try {
            // Decode base64 audio
            byte[] audioData = Base64.getDecoder().decode(base64Audio);
            log.debug("Received audio data for session {}, size: {} bytes", sessionId, audioData.length);

            // Send audio to STT transcriber (results come via callback)
            sttService.sendAudio(sessionId, audioData);

        } catch (Exception e) {
            log.error("Error handling user audio for session {}", sessionId, e);

            // Send user-friendly error message
            String errorMessage = getErrorMessage(e);
            sendErrorMessage(session, errorMessage);
        }
    }

    /**
     * Handle STT result from callback
     * Accumulates text and triggers LLM when sentence is complete
     */
    private void handleSttResult(String sessionId, String recognizedText) {
        WebSocketSession session = sessions.get(sessionId);
        SessionState state = sessionStates.get(sessionId);

        if (session == null || state == null) {
            log.warn("Session or state not found: {}", sessionId);
            return;
        }

        log.debug("STT result for session {}: {}", sessionId, recognizedText);

        // Send subtitle to frontend (intermediate result)
        sendSubtitle(session, recognizedText, false);

        // Update accumulated text
        state.setAccumulatedText(recognizedText);

        // Check if user finished speaking
        if (isUserFinishedSpeaking(recognizedText)) {
            log.info("User finished speaking for session {}, triggering LLM", sessionId);

            // Prevent duplicate LLM calls
            if (state.isProcessing().compareAndSet(false, true)) {
                try {
                    triggerLlmResponse(sessionId, session, state);
                } finally {
                    state.isProcessing().set(false);
                }
            }
        }
    }

    /**
     * Trigger LLM response for completed sentence
     */
    private void triggerLlmResponse(String sessionId, WebSocketSession session, SessionState state) {
        try {
            // Check if WebSocket is still open
            if (!session.isOpen()) {
                log.warn("WebSocket session is closed, skipping LLM response for session {}", sessionId);
                return;
            }

            String userText = state.getAccumulatedText();
            if (userText == null || userText.trim().isEmpty()) {
                log.warn("Empty user text, skipping LLM response");
                return;
            }

            log.info("Getting LLM response for session {}, text: {}", sessionId, userText);

            // Get LLM response
            VoiceInterviewSessionEntity sessionEntity = getSessionEntity(sessionId);
            if (sessionEntity == null) {
                log.error("Session entity not found for session {}, cannot generate LLM response", sessionId);
                sendError(session, "会话不存在，请重新开始面试");
                return;
            }

            // Load conversation history for context
            List<String> conversationHistory = getHistory(sessionId);

            String aiReply = llmService.chat(userText, sessionEntity, conversationHistory);
            log.info("LLM response for session {}: '{}'", sessionId, aiReply);

            // Check again if session is still open before sending
            if (!session.isOpen()) {
                log.warn("WebSocket closed during LLM processing, discarding response for session {}", sessionId);
                return;
            }

            // Send final subtitle
            sendSubtitle(session, userText, true);

            // TTS synthesis
            byte[] aiAudio = ttsService.synthesize(aiReply);

            // Final check before sending audio
            if (!session.isOpen()) {
                log.warn("WebSocket closed during TTS processing, discarding audio for session {}", sessionId);
                return;
            }

            if (aiAudio == null || aiAudio.length == 0) {
                log.warn("TTS returned empty audio, using text-only response");
                sendTextMessage(session, aiReply);
            } else {
                sendAudio(session, aiAudio, aiReply);
            }

            // Save message
            saveMessage(sessionId, userText, aiReply);

            // Clear accumulated text for next utterance
            state.setAccumulatedText("");

        } catch (Exception e) {
            log.error("Error triggering LLM response for session {}", sessionId, e);
            if (session.isOpen()) {
                sendError(session, "AI响应失败: " + e.getMessage());
            }
        }
    }

    /**
     * Convert exception to user-friendly error message
     */
    private String getErrorMessage(Exception e) {
        Throwable cause = e.getCause();

        // Check for specific Aliyun errors
        if (cause != null) {
            String message = cause.getMessage();
            if (message != null) {
                if (message.contains("403") || message.contains("ACCESS_DENIED")) {
                    return "阿里云语音服务认证失败：AccessKey 无效或已过期。请在 .env 文件中配置正确的 ALIYUN_ACCESS_KEY";
                }
                if (message.contains("timeout") || message.contains("channel inactive")) {
                    return "阿里云语音服务连接超时。请检查网络连接或稍后重试";
                }
            }
        }

        // Default error message
        return "语音处理失败：" + e.getMessage();
    }

    /**
     * Handle control message (end_interview, start_phase)
     */
    private void handleControl(String sessionId, WebSocketControlMessage control) {
        log.info("Control message for session {}: action={}, phase={}",
                sessionId, control.getAction(), control.getPhase());

        switch (control.getAction()) {
            case "end_interview":
                interviewService.endSession(sessionId);
                break;
            case "start_phase":
                interviewService.startPhase(sessionId, control.getPhase());
                break;
        }
    }

    /**
     * Send subtitle message to frontend
     */
    private void sendSubtitle(WebSocketSession session, String text, boolean isFinal) {
        if (!session.isOpen()) {
            log.warn("Attempted to send subtitle to closed session");
            return;
        }
        try {
            WebSocketSubtitleMessage subtitle = WebSocketSubtitleMessage.builder()
                    .type("subtitle")
                    .text(text)
                    .isFinal(isFinal)
                    .build();

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(subtitle)));
        } catch (Exception e) {
            log.error("Error sending subtitle", e);
        }
    }

    /**
     * Send audio message to frontend
     */
    private void sendAudio(WebSocketSession session, byte[] audio, String text) {
        if (!session.isOpen()) {
            log.warn("Attempted to send audio to closed session");
            return;
        }
        try {
            String base64Audio = Base64.getEncoder().encodeToString(audio);

            Map<String, Object> message = Map.of(
                    "type", "audio",
                    "data", base64Audio,
                    "text", text
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.error("Error sending audio", e);
        }
    }

    /**
     * Send text-only message (when TTS fails)
     */
    private void sendTextMessage(WebSocketSession session, String text) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "text",
                    "content", text
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.error("Error sending text message", e);
        }
    }

    /**
     * Send error message to frontend
     */
    private void sendErrorMessage(WebSocketSession session, String error) {
        sendError(session, error);
    }

    /**
     * Send error message to frontend (existing method)
     */
    private void sendError(WebSocketSession session, String error) {
        if (!session.isOpen()) {
            log.warn("Attempted to send error to closed session");
            return;
        }
        try {
            Map<String, Object> message = Map.of(
                    "type", "error",
                    "message", error
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.error("Error sending error message", e);
        }
    }

    /**
     * Scheduled task to check for pause warnings and timeouts
     * Runs every 30 seconds
     * 定时任务：检查暂停警告和超时
     */
    @Scheduled(fixedRate = 30000)
    public void checkPauseTimeout() {
        long now = System.currentTimeMillis();

        lastActivityTime.forEach((sessionId, lastTime) -> {
            long elapsed = now - lastTime;

            // Send warning at 4:30
            if (elapsed > WARNING_TIME_MS && elapsed < PAUSE_TIMEOUT_MS) {
                sendPauseWarning(sessionId);
            }
            // Timeout at 5:00
            else if (elapsed >= PAUSE_TIMEOUT_MS) {
                log.warn("Session {} inactive for {} minutes, pausing",
                    sessionId, PAUSE_TIMEOUT_MS / 60000);
                handlePauseTimeout(sessionId);
            }
        });
    }

    /**
     * Send pause warning notification
     * 发送暂停警告通知
     */
    private void sendPauseWarning(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> message = Map.of(
                    "type", "control",
                    "action", "pause_timeout_warning",
                    "message", "会话将在30秒后暂停，请继续说话或点击继续",
                    "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                log.debug("Sent pause warning to session {}", sessionId);
            } catch (Exception e) {
                log.error("Error sending pause warning to session {}", sessionId, e);
            }
        }
    }

    /**
     * Handle pause timeout - save state and disconnect
     * 处理暂停超时 - 保存状态并断开连接
     */
    private void handlePauseTimeout(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);

        try {
            // 1. Send timeout notification
            if (session != null && session.isOpen()) {
                Map<String, Object> message = Map.of(
                    "type", "control",
                    "action", "pause_timeout",
                    "message", "会话因超时已暂停,可在历史记录中恢复",
                    "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }

            // 2. Save session state to database
            interviewService.pauseSession(sessionId, "timeout");

            // 3. Close WebSocket connection
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.GOING_AWAY);
            }

            // 4. Cleanup
            sessions.remove(sessionId);
            sessionStates.remove(sessionId);
            lastActivityTime.remove(sessionId);

            log.info("Session {} paused due to timeout", sessionId);

        } catch (Exception e) {
            log.error("Error handling pause timeout for session {}", sessionId, e);
        }
    }

    /**
     * Extract session ID from WebSocket URI path
     * Path format: /ws/voice-interview/{sessionId}
     */
    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Check if user finished speaking using simple heuristic
     * MVP: End with sentence-ending punctuation (。？！)
     */
    private boolean isUserFinishedSpeaking(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.endsWith("。") || trimmed.endsWith("?") || trimmed.endsWith("!")
                || trimmed.endsWith("？") || trimmed.endsWith("！");
    }

    /**
     * Get chat history for session
     * Load conversation history from database
     */
    private List<String> getHistory(String sessionId) {
        try {
            List<VoiceInterviewMessageEntity> messages = interviewService.getConversationHistory(sessionId);
            List<String> history = new ArrayList<>();

            for (VoiceInterviewMessageEntity msg : messages) {
                if (msg.getUserRecognizedText() != null && !msg.getUserRecognizedText().isEmpty()) {
                    history.add("用户：" + msg.getUserRecognizedText());
                }
                if (msg.getAiGeneratedText() != null && !msg.getAiGeneratedText().isEmpty()) {
                    history.add("AI：" + msg.getAiGeneratedText());
                }
            }

            log.debug("Loaded {} messages from history for session {}", history.size(), sessionId);
            return history;
        } catch (Exception e) {
            log.error("Error loading conversation history for session {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Get session entity from database
     */
    private VoiceInterviewSessionEntity getSessionEntity(String sessionId) {
        try {
            Long sessionIdLong = Long.parseLong(sessionId);
            return interviewService.getSession(sessionIdLong);
        } catch (NumberFormatException e) {
            log.error("Invalid session ID format: {}", sessionId);
            return null;
        }
    }

    /**
     * Save message to database
     */
    private void saveMessage(String sessionId, String userText, String aiText) {
        try {
            interviewService.saveMessage(sessionId, userText, aiText);
            log.debug("Message saved to database for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error saving message for session {}", sessionId, e);
        }
    }

    /**
     * Internal class to hold session state
     */
    private static class SessionState {
        private final AtomicReference<String> accumulatedText = new AtomicReference<>("");
        private final AtomicBoolean processing = new AtomicBoolean(false);

        public String getAccumulatedText() {
            return accumulatedText.get();
        }

        public void setAccumulatedText(String text) {
            accumulatedText.set(text);
        }

        public AtomicBoolean isProcessing() {
            return processing;
        }
    }
}
