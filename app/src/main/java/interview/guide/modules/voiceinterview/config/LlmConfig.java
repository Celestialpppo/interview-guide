package interview.guide.modules.voiceinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.voice-interview")
public class LlmConfig {
    private String llmProvider = "dashscope";
}
