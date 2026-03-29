package interview.guide.modules.voiceinterview.service;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Aliyun Speech-to-Text Service (Real-time Streaming)
 * 阿里云实时语音识别服务封装
 * <p>
 * Provides continuous real-time speech recognition using Aliyun's NLS Transcriber API.
 * Uses SpeechTranscriber for long-running sessions instead of SpeechRecognizer.
 * </p>
 *
 * <p>Key difference from SpeechRecognizer:
 * <ul>
 *   <li>SpeechRecognizer: One-shot recognition for short audio (≤60s)</li>
 *   <li>SpeechTranscriber: Continuous streaming for long sessions (voice interview)</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class AliyunSttService {

    @Value("${app.voice-interview.aliyun.stt.url}")
    private String url;

    @Value("${app.voice-interview.aliyun.stt.app-key}")
    private String appKey;

    @Value("${app.voice-interview.aliyun.stt.access-key-id}")
    private String accessKeyId;

    @Value("${app.voice-interview.aliyun.stt.access-key-secret}")
    private String accessKeySecret;

    @Value("${app.voice-interview.aliyun.stt.format}")
    private String format;

    @Value("${app.voice-interview.aliyun.stt.sample-rate}")
    private int sampleRate;

    private NlsClient client;
    private ExecutorService executorService;

    // Session management: sessionId -> TranscriberSession
    private final Map<String, TranscriberSession> sessions = new ConcurrentHashMap<>();

    /**
     * Initialize the Aliyun STT client and thread pool
     */
    @PostConstruct
    public void init() {
        // Create NlsClient with access token
        if (accessKeyId != null && !accessKeyId.isEmpty() &&
            accessKeySecret != null && !accessKeySecret.isEmpty()) {
            try {
                // Get access token using AccessKey ID and Secret
                AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
                accessToken.apply();
                String token = accessToken.getToken();

                // Create NlsClient with token
                client = new NlsClient(token);
                log.info("Aliyun STT service initialized successfully, token expire time: {}", accessToken.getExpireTime());
            } catch (Exception e) {
                log.error("Failed to get Aliyun access token for STT service", e);
                client = new NlsClient("");
            }
        } else {
            log.warn("Aliyun STT credentials not configured. Please set ALIYUN_ACCESS_KEY_ID and ALIYUN_ACCESS_KEY_SECRET");
            client = new NlsClient("");
        }
        executorService = Executors.newCachedThreadPool();
        log.info("Aliyun STT service initialized with format: {}, sample rate: {}", format, sampleRate);
    }

    /**
     * Cleanup resources on shutdown
     */
    @PreDestroy
    public void destroy() {
        // Close all active transcribers
        sessions.forEach((sessionId, session) -> {
            try {
                session.transcriber.close();
                log.info("Closed transcriber for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Error closing transcriber for session: {}", sessionId, e);
            }
        });
        sessions.clear();

        if (client != null) {
            client.shutdown();
            log.info("Aliyun STT client shutdown completed");
        }
        if (executorService != null) {
            executorService.shutdown();
            log.info("Aliyun STT executor service shutdown completed");
        }
    }

    /**
     * Start a new transcription session
     * Creates and starts a SpeechTranscriber for continuous streaming
     *
     * @param sessionId Unique session identifier
     * @param onResult Callback for recognition results (receives intermediate and final text)
     * @param onError Callback for errors
     * @throws Exception if transcriber creation fails
     */
    public void startTranscription(String sessionId, Consumer<String> onResult, Consumer<Throwable> onError) throws Exception {
        if (sessions.containsKey(sessionId)) {
            log.warn("Session {} already has an active transcriber, stopping old one", sessionId);
            stopTranscription(sessionId);
        }

        log.info("Starting transcription for session: {}", sessionId);

        // Create listener with callbacks
        SpeechTranscriberListener listener = createListener(onResult, onError);

        // Create transcriber
        SpeechTranscriber transcriber = new SpeechTranscriber(client, listener);
        transcriber.setAppKey(appKey);

        // Configure audio format
        configureTranscriber(transcriber);

        // Start transcriber
        transcriber.start();

        // Store session
        sessions.put(sessionId, new TranscriberSession(transcriber, onResult, onError));
        log.info("Transcriber started successfully for session: {}", sessionId);
    }

    /**
     * Send audio data to an active transcription session
     *
     * @param sessionId Session identifier
     * @param audioData PCM audio bytes
     * @throws Exception if session not found or send fails
     */
    public void sendAudio(String sessionId, byte[] audioData) throws Exception {
        TranscriberSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("No active transcriber for session: " + sessionId);
        }

        if (audioData == null || audioData.length == 0) {
            log.warn("Empty audio data received for session: {}", sessionId);
            return;
        }

        session.transcriber.send(audioData);
        log.debug("Sent {} bytes of audio to transcriber for session: {}", audioData.length, sessionId);
    }

    /**
     * Stop transcription session
     *
     * @param sessionId Session identifier
     */
    public void stopTranscription(String sessionId) {
        TranscriberSession session = sessions.remove(sessionId);
        if (session == null) {
            log.warn("No active transcriber to stop for session: {}", sessionId);
            return;
        }

        try {
            session.transcriber.stop();
            log.info("Transcriber stopped for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error stopping transcriber for session: {}", sessionId, e);
        } finally {
            try {
                session.transcriber.close();
            } catch (Exception e) {
                log.error("Error closing transcriber for session: {}", sessionId, e);
            }
        }
    }

    /**
     * Check if session has active transcriber
     *
     * @param sessionId Session identifier
     * @return true if session has active transcriber
     */
    public boolean hasActiveSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * Create and configure a SpeechTranscriberListener
     *
     * @param onResult Callback for recognition results
     * @param onError Callback for errors
     * @return Configured listener
     */
    private SpeechTranscriberListener createListener(Consumer<String> onResult, Consumer<Throwable> onError) {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                log.info("STT transcriber started, task_id: {}", response.getTaskId());
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                // Intermediate result during recognition
                String text = response.getTransSentenceText();
                if (text != null && !text.isEmpty()) {
                    log.debug("STT intermediate result: {}", text);
                    if (onResult != null) {
                        onResult.accept(text);
                    }
                }
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                // New sentence detected
                log.debug("Sentence begin: {}", response.getTransSentenceText());
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                // Sentence completed
                String text = response.getTransSentenceText();
                if (text != null && !text.isEmpty()) {
                    log.info("STT sentence completed: {}", text);
                    if (onResult != null) {
                        onResult.accept(text);
                    }
                }
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                // All transcription completed
                log.info("STT transcription completed for task: {}", response.getTaskId());
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                // Recognition failed
                String errorMsg = String.format("STT failed: status=%d, message=%s, task_id=%s",
                        response.getStatus(), response.getStatusText(), response.getTaskId());
                log.error(errorMsg);

                if (onError != null) {
                    onError.accept(new RuntimeException(errorMsg));
                }
            }
        };
    }

    /**
     * Configure SpeechTranscriber with audio format and settings
     *
     * @param transcriber SpeechTranscriber to configure
     */
    private void configureTranscriber(SpeechTranscriber transcriber) {
        // Set audio format (PCM, OPUS, etc.)
        if ("opus".equalsIgnoreCase(format)) {
            transcriber.setFormat(InputFormatEnum.OPUS);
        } else if ("wav".equalsIgnoreCase(format)) {
            transcriber.setFormat(InputFormatEnum.WAV);
        } else {
            transcriber.setFormat(InputFormatEnum.PCM);
        }

        // Set sample rate (8k or 16k)
        if (sampleRate == 16000) {
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        } else if (sampleRate == 8000) {
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_8K);
        } else {
            // Default to 16k
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        }

        // Enable intermediate results during recognition
        transcriber.setEnableIntermediateResult(true);

        // Enable punctuation
        transcriber.addCustomedParam("enable_punctuation_prediction", true);

        // Enable inverse text normalization (convert spoken numbers to digits)
        transcriber.addCustomedParam("enable_inverse_text_normalization", true);

        // Enable voice detection (VAD)
        transcriber.addCustomedParam("enable_voice_detection", true);

        log.debug("Transcriber configured: format={}, sampleRate={}", format, sampleRate);
    }

    /**
     * Internal class to hold session state
     */
    private static class TranscriberSession {
        final SpeechTranscriber transcriber;
        final Consumer<String> onResult;
        final Consumer<Throwable> onError;

        TranscriberSession(SpeechTranscriber transcriber, Consumer<String> onResult, Consumer<Throwable> onError) {
            this.transcriber = transcriber;
            this.onResult = onResult;
            this.onError = onError;
        }
    }
}
