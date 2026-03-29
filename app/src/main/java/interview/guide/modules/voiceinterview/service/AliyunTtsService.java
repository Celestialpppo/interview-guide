package interview.guide.modules.voiceinterview.service;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * Aliyun Text-to-Speech Service
 * 阿里云语音合成服务封装
 * <p>
 * Provides text-to-speech synthesis using Aliyun's NLS (Natural Language Service) API.
 * Supports synchronous synthesis for MVP, can be extended to streaming for production.
 * </p>
 */
@Service
@Slf4j
public class AliyunTtsService {

    @Value("${app.voice-interview.aliyun.tts.url}")
    private String url;

    @Value("${app.voice-interview.aliyun.tts.app-key}")
    private String appKey;

    @Value("${app.voice-interview.aliyun.tts.access-key-id}")
    private String accessKeyId;

    @Value("${app.voice-interview.aliyun.tts.access-key-secret}")
    private String accessKeySecret;

    @Value("${app.voice-interview.tts.default-voice:${app.voice-interview.aliyun.tts.voice:zhichu}}")
    private String voice;

    @Value("${app.voice-interview.aliyun.tts.speech-rate:80}")
    private int speechRate;

    @Value("${app.voice-interview.aliyun.tts.volume:60}")
    private int volume;

    @Value("${app.voice-interview.aliyun.tts.format}")
    private String format;

    @Value("${app.voice-interview.aliyun.tts.sample-rate}")
    private int sampleRate;

    private NlsClient client;

    /**
     * Initialize the Aliyun TTS client
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
                log.info("Aliyun TTS service initialized successfully, token expire time: {}", accessToken.getExpireTime());
            } catch (Exception e) {
                log.error("Failed to get Aliyun access token for TTS service", e);
                client = new NlsClient("");
            }
        } else {
            log.warn("Aliyun TTS credentials not configured. Please set ALIYUN_ACCESS_KEY_ID and ALIYUN_ACCESS_KEY_SECRET");
            client = new NlsClient("");
        }
        log.info("Aliyun TTS service initialized with voice: {}, format: {}, sample rate: {}",
                voice, format, sampleRate);
    }

    /**
     * Cleanup resources on shutdown
     */
    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.shutdown();
            log.info("Aliyun TTS client shutdown completed");
        }
    }

    /**
     * Synthesize text to audio (synchronous for MVP)
     * <p>
     * This method blocks until synthesis is complete. For production use with WebSocket,
     * consider implementing streaming synthesis with callbacks.
     * </p>
     *
     * @param text Text to synthesize
     * @return Audio data bytes, or empty array if synthesis fails
     */
    public byte[] synthesize(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("TTS synthesis called with empty text");
            return new byte[0];
        }

        SpeechSynthesizer synthesizer = null;
        try {
            // Create listener for synthesis results
            CountDownLatch latch = new CountDownLatch(1);
            ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
            StringBuilder errorMessage = new StringBuilder();

            SpeechSynthesizerListener listener = createListener(latch, audioStream, errorMessage);

            // Create synthesizer instance
            synthesizer = new SpeechSynthesizer(client, listener);
            synthesizer.setAppKey(appKey);

            // Configure audio format
            configureSynthesizer(synthesizer);

            // Set text to synthesize
            synthesizer.setText(text);

            // Start synthesis (this sends the request and waits for acknowledgment)
            synthesizer.start();
            log.debug("TTS synthesizer started, text length: {}", text.length());

            // Wait for synthesis to complete with timeout
            if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.error("TTS synthesis timeout after 30 seconds for text length: {}", text.length());
                return new byte[0];
            }

            // Check for errors
            if (errorMessage.length() > 0) {
                log.error("TTS synthesis failed: {}", errorMessage.toString());
                return new byte[0];
            }

            byte[] audioData = audioStream.toByteArray();
            log.info("TTS synthesis completed, audio size: {} bytes", audioData.length);
            return audioData;

        } catch (Exception e) {
            log.error("TTS synthesis error", e);
            return new byte[0];
        } finally {
            // Close synthesizer (each synthesis task requires a new synthesizer instance)
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
    }

    /**
     * Create and configure a SpeechSynthesizerListener
     *
     * @param latch CountDownLatch for synchronization
     * @param audioStream ByteArrayOutputStream to collect audio data
     * @param errorMessage StringBuilder to collect error messages
     * @return Configured listener
     */
    private SpeechSynthesizerListener createListener(
            CountDownLatch latch,
            ByteArrayOutputStream audioStream,
            StringBuilder errorMessage) {

        return new SpeechSynthesizerListener() {
            private boolean firstRecvBinary = true;

            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                // Synthesis completed successfully
                log.info("TTS synthesis completed: name={}, status={}, task_id={}",
                        response.getName(), response.getStatus(), response.getTaskId());
                latch.countDown();
            }

            @Override
            public void onMessage(ByteBuffer message) {
                try {
                    if (firstRecvBinary) {
                        firstRecvBinary = false;
                        log.debug("TTS first binary data received");
                    }
                    byte[] bytesArray = new byte[message.remaining()];
                    message.get(bytesArray, 0, bytesArray.length);
                    audioStream.write(bytesArray);
                    log.debug("TTS audio data received, chunk size: {} bytes", bytesArray.length);
                } catch (IOException e) {
                    log.error("Failed to write audio data to stream", e);
                }
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                // Synthesis failed
                String errorMsg = "TTS synthesis failed: status=" + response.getStatus() +
                        ", status_text=" + response.getStatusText() +
                        ", task_id=" + response.getTaskId();
                errorMessage.append(errorMsg);
                log.error(errorMsg);
                latch.countDown();
            }
        };
    }

    /**
     * Configure SpeechSynthesizer with audio format and settings
     *
     * @param synthesizer SpeechSynthesizer to configure
     */
    private void configureSynthesizer(SpeechSynthesizer synthesizer) {
        // Set audio format using enum
        if ("mp3".equalsIgnoreCase(format)) {
            synthesizer.setFormat(OutputFormatEnum.MP3);
        } else if ("wav".equalsIgnoreCase(format)) {
            synthesizer.setFormat(OutputFormatEnum.WAV);
        } else {
            synthesizer.setFormat(OutputFormatEnum.PCM);
        }

        // Set sample rate using enum
        if (sampleRate == 16000) {
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        } else if (sampleRate == 8000) {
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_8K);
        } else {
            // Default to 16k
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
        }

        // Set voice (xiaoyun for female, zhichu for professional female, etc.)
        synthesizer.setVoice(voice);

        // Set volume (0-100, default 50)
        synthesizer.setVolume(volume);

        // Set speech rate (-500 to 500, default 0, 80 for干练)
        synthesizer.setSpeechRate(speechRate);

        // Set pitch rate (-500 to 500, default 0)
        synthesizer.setPitchRate(0);

        // Enable subtitle (optional, for real-time subtitle)
        synthesizer.addCustomedParam("enable_subtitle", false);

        log.debug("Synthesizer configured: format={}, sampleRate={}, voice={}, speechRate={}, volume={}",
                format, sampleRate, voice, speechRate, volume);
    }
}
