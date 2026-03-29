package interview.guide.modules.voiceinterview.service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.audio.omni.OmniRealtimeTranscriptionParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Qwen3 Realtime ASR Service
 *
 * Provides real-time speech recognition using Alibaba Cloud DashScope's qwen3-asr-flash-realtime model.
 * This service manages WebSocket connections for multiple concurrent sessions and handles
 * audio transcription with server-side Voice Activity Detection (VAD).
 *
 * Key Features:
 * - Multi-session management with thread-safe concurrent map
 * - Server-side VAD with 400ms silence duration for automatic sentence detection
 * - Callback-based result handling for real-time transcription updates
 * - Automatic resource cleanup on session termination
 *
 * Configuration:
 * - Model: qwen3-asr-flash-realtime
 * - Audio format: PCM, 16kHz sample rate
 * - Language: Chinese (zh)
 * - VAD: Enabled with server_vad type
 *
 * @see OmniRealtimeConversation
 * @see OmniRealtimeCallback
 */
@Slf4j
@Service
public class QwenAsrService {

    // Configuration fields (injected via @Value from application.yml or reflection in tests)
    @Value("${app.voice-interview.qwen.asr.url}")
    private String url;

    @Value("${app.voice-interview.qwen.asr.model}")
    private String model;

    @Value("${app.voice-interview.qwen.asr.api-key}")
    private String apiKey;

    @Value("${app.voice-interview.qwen.asr.language}")
    private String language;

    @Value("${app.voice-interview.qwen.asr.format}")
    private String format;

    @Value("${app.voice-interview.qwen.asr.sample-rate}")
    private Integer sampleRate;

    @Value("${app.voice-interview.qwen.asr.enable-turn-detection}")
    private Boolean enableTurnDetection;

    @Value("${app.voice-interview.qwen.asr.turn-detection-type}")
    private String turnDetectionType;

    @Value("${app.voice-interview.qwen.asr.turn-detection-threshold}")
    private Float turnDetectionThreshold;

    @Value("${app.voice-interview.qwen.asr.turn-detection-silence-duration-ms}")
    private Integer turnDetectionSilenceDurationMs;

    /**
     * Active ASR sessions map.
     * Key: session ID (user-provided identifier)
     * Value: AsrSession containing the OmniRealtimeConversation instance and callbacks
     */
    private final Map<String, AsrSession> sessions = new ConcurrentHashMap<>();

    /**
     * Initialize the ASR service.
     * This method is automatically called by Spring after the service is constructed
     * and all configuration values have been injected via @Value annotations.
     *
     * @throws IllegalStateException if apiKey is not configured
     */
    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API key must be configured before initializing QwenAsrService");
        }
        log.info("QwenAsrService initialized with model: {}, url: {}", model, url);
    }

    /**
     * Start a new transcription session.
     *
     * This method creates a new WebSocket connection to the DashScope ASR service
     * and sets up callbacks for handling transcription results and errors.
     *
     * The session uses server-side VAD (Voice Activity Detection) to automatically
     * detect sentence boundaries. When speech is detected and transcribed, the
     * onResult callback will be invoked with the transcribed text.
     *
     * @param sessionId Unique identifier for this session
     * @param onResult Callback invoked when transcription results are received
     * @param onError Callback invoked when errors occur
     * @throws IllegalStateException if session already exists or service not initialized
     */
    public void startTranscription(String sessionId, Consumer<String> onResult, Consumer<Throwable> onError) {
        if (sessions.containsKey(sessionId)) {
            throw new IllegalStateException("Session already exists: " + sessionId);
        }

        try {
            // Build OmniRealtimeParam with connection settings
            OmniRealtimeParam param = OmniRealtimeParam.builder()
                    .model(model)
                    .url(url)
                    .apikey(apiKey)
                    .build();

            // Create callback handler for WebSocket events
            OmniRealtimeCallback callback = new OmniRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("[Session: {}] WebSocket connection established", sessionId);
                }

                @Override
                public void onEvent(JsonObject message) {
                    handleServerEvent(sessionId, message, onResult, onError);
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("[Session: {}] WebSocket closed - code: {}, reason: {}", sessionId, code, reason);
                    sessions.remove(sessionId);
                }
            };

            // Create OmniRealtimeConversation instance
            OmniRealtimeConversation conversation = new OmniRealtimeConversation(param, callback);

            // Store session in map BEFORE connecting to ensure hasActiveSession() returns true
            sessions.put(sessionId, new AsrSession(conversation, onResult, onError));

            // Connect to server asynchronously (non-blocking)
            Thread connectionThread = new Thread(() -> {
                try {
                    conversation.connect();

                    // Configure session with transcription parameters
                    OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
                    transcriptionParam.setLanguage(language);
                    transcriptionParam.setInputSampleRate(sampleRate);
                    transcriptionParam.setInputAudioFormat(format);

                    OmniRealtimeConfig config = OmniRealtimeConfig.builder()
                            .modalities(Collections.singletonList(OmniRealtimeModality.TEXT))
                            .enableTurnDetection(enableTurnDetection)
                            .turnDetectionType(turnDetectionType)
                            .turnDetectionThreshold(turnDetectionThreshold)
                            .turnDetectionSilenceDurationMs(turnDetectionSilenceDurationMs)
                            .transcriptionConfig(transcriptionParam)
                            .build();

                    // Update session with configuration
                    conversation.updateSession(config);

                    log.info("[Session: {}] Transcription session started successfully", sessionId);

                } catch (Exception e) {
                    log.error("[Session: {}] Failed to establish connection", sessionId, e);
                    sessions.remove(sessionId);
                    onError.accept(e);
                }
            }, "ASR-Connection-" + sessionId);
            connectionThread.setDaemon(true);
            connectionThread.start();

        } catch (Exception e) {
            String errorMsg = "Failed to create transcription session: " + sessionId;
            log.error(errorMsg, e);
            sessions.remove(sessionId);
            onError.accept(new IllegalStateException(errorMsg, e));
            throw new IllegalStateException(errorMsg, e);
        }
    }

    /**
     * Send audio data to the ASR service for transcription.
     *
     * The audio data should be in PCM format at 16kHz sample rate.
     * The data is Base64-encoded before being sent to the DashScope service.
     *
     * With server-side VAD enabled, the service will automatically detect
     * speech segments and trigger transcription when silence is detected.
     *
     * @param sessionId Session identifier
     * @param audioData Raw PCM audio bytes
     * @throws IllegalStateException if session does not exist
     */
    public void sendAudio(String sessionId, byte[] audioData) {
        AsrSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("No active session found: " + sessionId);
        }

        try {
            // Convert audio data to Base64
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);

            // Send to ASR service
            session.getConversation().appendAudio(audioBase64);

            log.trace("[Session: {}] Sent {} bytes of audio data", sessionId, audioData.length);

        } catch (Exception e) {
            log.error("[Session: {}] Failed to send audio data", sessionId, e);
            session.getOnError().accept(e);
        }
    }

    /**
     * Stop transcription and close the session.
     *
     * This method notifies the ASR service to complete any pending transcription,
     * waits for the final results, and then closes the WebSocket connection.
     *
     * @param sessionId Session identifier
     */
    public void stopTranscription(String sessionId) {
        AsrSession session = sessions.remove(sessionId);
        if (session == null) {
            log.warn("[Session: {}] Attempted to stop non-existent session", sessionId);
            return;
        }

        try {
            // Notify server to finish transcription
            session.getConversation().endSession();

            log.info("[Session: {}] Transcription session stopped", sessionId);

        } catch (InterruptedException e) {
            log.error("[Session: {}] Thread interrupted while ending session", sessionId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[Session: {}] Error while stopping session", sessionId, e);
        } finally {
            // Ensure connection is closed
            try {
                session.getConversation().close();
            } catch (Exception e) {
                log.error("[Session: {}] Error closing connection", sessionId, e);
            }
        }
    }

    /**
     * Check if a session with the given ID is currently active.
     *
     * @param sessionId Session identifier
     * @return true if session exists and is active, false otherwise
     */
    public boolean hasActiveSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * Destroy the service and cleanup all active sessions.
     *
     * This method is called automatically when the Spring container shuts down.
     * It stops all active sessions and releases resources.
     */
    @PreDestroy
    public void destroy() {
        log.info("Destroying QwenAsrService with {} active sessions", sessions.size());

        // Stop all active sessions
        sessions.keySet().forEach(sessionId -> {
            try {
                stopTranscription(sessionId);
            } catch (Exception e) {
                log.error("[Session: {}] Error during cleanup", sessionId, e);
            }
        });

        sessions.clear();
        log.info("QwenAsrService destroyed successfully");
    }

    /**
     * Handle server events from the DashScope ASR service.
     *
     * This method processes various event types:
     * - session.created: Session successfully created
     * - session.updated: Session configuration updated
     * - conversation.item.input_audio_transcription.completed: Final transcription result
     * - conversation.item.input_audio_transcription.text: Partial transcription result (ignored)
     * - error: Error occurred
     *
     * @param sessionId Session identifier
     * @param message JSON event message from server
     * @param onResult Callback for transcription results
     * @param onError Callback for errors
     */
    private void handleServerEvent(String sessionId, JsonObject message,
                                    Consumer<String> onResult, Consumer<Throwable> onError) {
        try {
            String eventType = message.get("type").getAsString();

            log.trace("[Session: {}] Received event: {}", sessionId, eventType);

            switch (eventType) {
                case "session.created":
                    log.debug("[Session: {}] Session created on server", sessionId);
                    break;

                case "session.updated":
                    log.debug("[Session: {}] Session configuration updated", sessionId);
                    break;

                case "conversation.item.input_audio_transcription.completed":
                    // Final transcription result
                    JsonObject transcriptObj = message.getAsJsonObject();
                    String transcript = transcriptObj.get("transcript").getAsString();
                    String language = transcriptObj.has("language") ?
                            transcriptObj.get("language").getAsString() : "unknown";
                    String emotion = transcriptObj.has("emotion") ?
                            transcriptObj.get("emotion").getAsString() : "neutral";

                    log.debug("[Session: {}] Transcription completed - language: {}, emotion: {}, text: {}",
                            sessionId, language, emotion, transcript);

                    // Invoke callback with final result
                    onResult.accept(transcript);
                    break;

                case "conversation.item.input_audio_transcription.text":
                    // Partial transcription result (ignored - we only use final results)
                    log.trace("[Session: {}] Partial transcription received", sessionId);
                    break;

                case "error":
                    // Error event
                    JsonObject errorObj = message.getAsJsonObject("error");
                    String errorType = errorObj.has("type") ? errorObj.get("type").getAsString() : "unknown";
                    String errorCode = errorObj.has("code") ? errorObj.get("code").getAsString() : "unknown";
                    String errorMessage = errorObj.has("message") ? errorObj.get("message").getAsString() : "Unknown error";

                    String fullErrorMessage = String.format("ASR Error [%s/%s]: %s", errorType, errorCode, errorMessage);
                    log.error("[Session: {}] {}", sessionId, fullErrorMessage);

                    onError.accept(new RuntimeException(fullErrorMessage));
                    break;

                case "session.finished":
                    log.debug("[Session: {}] Session finished on server", sessionId);
                    break;

                default:
                    log.trace("[Session: {}] Unhandled event type: {}", sessionId, eventType);
            }

        } catch (Exception e) {
            log.error("[Session: {}] Error processing server event", sessionId, e);
            onError.accept(e);
        }
    }

    /**
     * Internal class to hold session data.
     */
    private static class AsrSession {
        private final OmniRealtimeConversation conversation;
        private final Consumer<String> onResult;
        private final Consumer<Throwable> onError;

        public AsrSession(OmniRealtimeConversation conversation,
                          Consumer<String> onResult,
                          Consumer<Throwable> onError) {
            this.conversation = conversation;
            this.onResult = onResult;
            this.onError = onError;
        }

        public OmniRealtimeConversation getConversation() {
            return conversation;
        }

        public Consumer<String> getOnResult() {
            return onResult;
        }

        public Consumer<Throwable> getOnError() {
            return onError;
        }
    }

    // Setter methods for configuration (used by Spring @Value injection or tests)

    public void setUrl(String url) {
        this.url = url;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setEnableTurnDetection(Boolean enableTurnDetection) {
        this.enableTurnDetection = enableTurnDetection;
    }

    public void setTurnDetectionType(String turnDetectionType) {
        this.turnDetectionType = turnDetectionType;
    }

    public void setTurnDetectionThreshold(Float turnDetectionThreshold) {
        this.turnDetectionThreshold = turnDetectionThreshold;
    }

    public void setTurnDetectionSilenceDurationMs(Integer turnDetectionSilenceDurationMs) {
        this.turnDetectionSilenceDurationMs = turnDetectionSilenceDurationMs;
    }
}
