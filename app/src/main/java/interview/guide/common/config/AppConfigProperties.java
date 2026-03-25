package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.resume")
public class AppConfigProperties {

    private String uploadDir;
    private List<String> allowedTypes;

}
