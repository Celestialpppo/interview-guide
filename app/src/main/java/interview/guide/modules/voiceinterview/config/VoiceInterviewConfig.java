package interview.guide.modules.voiceinterview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Voice Interview Configuration
 * 语音面试配置
 */
@Configuration
@EnableScheduling
public class VoiceInterviewConfig {
    // Enables @Scheduled annotations for timeout detection
}
