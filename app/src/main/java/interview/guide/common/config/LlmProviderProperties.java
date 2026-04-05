package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class LlmProviderProperties {
    private String defaultProvider = "dashscope";
    private Map<String, ProviderConfig> providers;

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}
