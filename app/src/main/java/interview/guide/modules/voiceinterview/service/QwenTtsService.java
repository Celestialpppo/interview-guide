package interview.guide.modules.voiceinterview.service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeAudioFormat;
import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Qwen3 Realtime TTS Service
 *
 * Provides real-time text-to-speech synthesis using Alibaba Cloud DashScope's
 * qwen3-tts-flash-realtime model. This service uses WebSocket-based real-time API
 * for low-latency speech synthesis.
 *
 * Key Features:
 * - Synchronous synthesis API with 30-second timeout protection
 * - Automatic audio chunk collection via response.audio.delta events
 * - Server-commit mode for reliable delivery
 * - Support for Chinese language with configurable voice, speech rate, and volume
 *
 * Configuration:
 * - Model: qwen3-tts-flash-realtime
 * - Voice: Cherry (Chinese female voice)
 * - Audio format: PCM, 16kHz sample rate
 * - Mode: server_commit
 * - Language: Chinese
 *
 * @see OmniRealtimeConversation
 * @see OmniRealtimeCallback
 */
@Slf4j
@Service
public class QwenTtsService {

    // Configuration fields (injected via @Value from application.yml or reflection in tests)
    @Value("${app.voice-interview.qwen.tts.url}")
    private String url;

    @Value("${app.voice-interview.qwen.tts.model}")
    private String model;

    @Value("${app.voice-interview.qwen.tts.api-key}")
    private String apiKey;

    @Value("${app.voice-interview.qwen.tts.voice}")
    private String voice;

    @Value("${app.voice-interview.qwen.tts.format}")
    private String format;

    @Value("${app.voice-interview.qwen.tts.sample-rate}")
    private Integer sampleRate;

    @Value("${app.voice-interview.qwen.tts.mode}")
    private String mode;

    @Value("${app.voice-interview.qwen.tts.language-type}")
    private String languageType;

    @Value("${app.voice-interview.qwen.tts.speech-rate}")
    private Float speechRate;

    @Value("${app.voice-interview.qwen.tts.volume}")
    private Integer volume;

    /**
     * Initialize the TTS service.
     * This method is automatically called by Spring after the service is constructed
     * and all configuration values have been injected via @Value annotations.
     *
     * @throws IllegalStateException if apiKey is not configured
     */
    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API key must be configured before initializing QwenTtsService");
        }
        log.info("QwenTtsService initialized with model: {}, voice: {}, url: {}", model, voice, url);
    }

    /**
     * Synthesize text to speech audio.
     *
     * This method synchronously converts text to PCM audio data using the DashScope
     * real-time TTS API. It establishes a WebSocket connection, sends the text for
     * synthesis, collects audio chunks, and returns the complete audio data.
     *
     * The method uses CountDownLatch to wait for synthesis completion with a 30-second
     * timeout to prevent indefinite blocking.
     *
     * @param text Text to synthesize (null, empty, or whitespace-only text returns empty array)
     * @return PCM audio data at 16kHz sample rate, or empty array if synthesis fails
     */
    public byte[] synthesize(String text) {
        // Handle null, empty, or whitespace-only text
        if (text == null || text.trim().isEmpty()) {
            log.debug("Empty or null text provided, returning empty audio array");
            return new byte[0];
        }

        log.debug("Starting TTS synthesis for text: {} characters", text.length());

        // Latch for synchronous waiting
        CountDownLatch synthesisLatch = new CountDownLatch(1);

        // Container for collected audio data
        ByteArrayContainer audioContainer = new ByteArrayContainer();

        // Error container
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

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
                    log.debug("TTS WebSocket connection established");
                }

                @Override
                public void onEvent(JsonObject message) {
                    handleServerEvent(message, audioContainer, synthesisLatch, errorRef);
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("TTS WebSocket closed - code: {}, reason: {}", code, reason);
                    synthesisLatch.countDown();
                }
            };

            // Create OmniRealtimeConversation instance
            OmniRealtimeConversation conversation = new OmniRealtimeConversation(param, callback);

            try {
                // Connect to server (blocking)
                conversation.connect();

                // Configure session with TTS parameters
                OmniRealtimeConfig config = OmniRealtimeConfig.builder()
                        .modalities(Collections.singletonList(OmniRealtimeModality.AUDIO))
                        .voice(voice)
                        .outputAudioFormat(OmniRealtimeAudioFormat.PCM_16000HZ_MONO_16BIT)
                        .enableTurnDetection(false) // TTS doesn't need VAD
                        .build();

                // Set additional TTS parameters via the parameters map
                java.util.Map<String, Object> parameters = new java.util.HashMap<>();
                parameters.put("language_type", languageType);
                parameters.put("speech_rate", speechRate);
                parameters.put("volume", volume);
                parameters.put("mode", mode);
                config.setParameters(parameters);

                // Update session with configuration
                conversation.updateSession(config);

                // Create response with text for synthesis
                // The createResponse method takes event_id and modalities
                conversation.createResponse(null, Collections.singletonList(OmniRealtimeModality.AUDIO));

                // Send text via sendRaw - construct proper JSON message
                JsonObject textMessage = new JsonObject();
                textMessage.addProperty("type", "conversation.item.create");
                JsonObject item = new JsonObject();
                item.addProperty("type", "message");
                item.addProperty("role", "user");
                JsonObject content = new JsonObject();
                content.addProperty("type", "input_text");
                content.addProperty("text", text);
                item.add("content", content);
                textMessage.add("item", item);
                conversation.sendRaw(textMessage.toString());

                log.debug("Text sent for synthesis");

                // Wait for synthesis completion with timeout
                boolean completed = synthesisLatch.await(30, TimeUnit.SECONDS);

                if (!completed) {
                    log.error("TTS synthesis timeout after 30 seconds");
                    return new byte[0];
                }

                // Check if error occurred
                Throwable error = errorRef.get();
                if (error != null) {
                    log.error("TTS synthesis failed", error);
                    return new byte[0];
                }

                // Return collected audio data
                byte[] audioData = audioContainer.toByteArray();
                log.debug("TTS synthesis completed - {} bytes of audio data", audioData.length);

                return audioData;

            } finally {
                // Ensure connection is closed
                try {
                    conversation.close();
                } catch (Exception e) {
                    log.error("Error closing TTS connection", e);
                }
            }

        } catch (InterruptedException e) {
            log.error("TTS synthesis interrupted", e);
            Thread.currentThread().interrupt();
            return new byte[0];
        } catch (Exception e) {
            log.error("Failed to synthesize text", e);
            return new byte[0];
        }
    }

    /**
     * Destroy the service and cleanup resources.
     *
     * This method is called automatically when the Spring container shuts down.
     * Currently, no persistent resources need cleanup as each synthesis creates
     * its own temporary connection.
     */
    @PreDestroy
    public void destroy() {
        log.info("QwenTtsService destroyed successfully");
    }

    /**
     * Handle server events from the DashScope TTS service.
     *
     * This method processes various event types:
     * - session.created: Session successfully created
     * - session.updated: Session configuration updated
     * - response.audio.delta: Audio chunk received
     * - response.audio.done: Audio synthesis completed
     * - error: Error occurred
     *
     * @param message JSON event message from server
     * @param audioContainer Container for collecting audio chunks
     * @param synthesisLatch Latch to signal completion
     * @param errorRef Container for error tracking
     */
    private void handleServerEvent(JsonObject message, ByteArrayContainer audioContainer,
                                    CountDownLatch synthesisLatch, AtomicReference<Throwable> errorRef) {
        try {
            String eventType = message.get("type").getAsString();

            log.trace("Received TTS event: {}", eventType);

            switch (eventType) {
                case "session.created":
                    log.debug("TTS session created on server");
                    break;

                case "session.updated":
                    log.debug("TTS session configuration updated");
                    break;

                case "response.audio.delta":
                    // Audio chunk received
                    JsonObject deltaObj = message.getAsJsonObject("delta");
                    if (deltaObj != null && deltaObj.has("audio")) {
                        String audioBase64 = deltaObj.get("audio").getAsString();
                        byte[] audioChunk = Base64.getDecoder().decode(audioBase64);
                        audioContainer.append(audioChunk);
                        log.trace("Received audio chunk - {} bytes", audioChunk.length);
                    }
                    break;

                case "response.audio.done":
                    // Audio synthesis completed
                    log.debug("TTS audio synthesis completed");
                    synthesisLatch.countDown();
                    break;

                case "error":
                    // Error event
                    JsonObject errorObj = message.getAsJsonObject("error");
                    String errorType = errorObj.has("type") ? errorObj.get("type").getAsString() : "unknown";
                    String errorCode = errorObj.has("code") ? errorObj.get("code").getAsString() : "unknown";
                    String errorMessage = errorObj.has("message") ? errorObj.get("message").getAsString() : "Unknown error";

                    String fullErrorMessage = String.format("TTS Error [%s/%s]: %s", errorType, errorCode, errorMessage);
                    log.error("{}", fullErrorMessage);

                    errorRef.set(new RuntimeException(fullErrorMessage));
                    synthesisLatch.countDown();
                    break;

                case "response.done":
                    log.debug("TTS response completed");
                    break;

                default:
                    log.trace("Unhandled TTS event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing TTS server event", e);
            errorRef.set(e);
            synthesisLatch.countDown();
        }
    }

    /**
     * Internal class for dynamically growing byte array.
     * Used to collect audio chunks from multiple response.audio.delta events.
     */
    private static class ByteArrayContainer {
        private byte[] data = new byte[0];

        public synchronized void append(byte[] chunk) {
            byte[] newData = new byte[data.length + chunk.length];
            System.arraycopy(data, 0, newData, 0, data.length);
            System.arraycopy(chunk, 0, newData, data.length, chunk.length);
            data = newData;
        }

        public synchronized byte[] toByteArray() {
            return data;
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

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setLanguageType(String languageType) {
        this.languageType = languageType;
    }

    public void setSpeechRate(Float speechRate) {
        this.speechRate = speechRate;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }
}
