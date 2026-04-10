package interview.guide.common.ai;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * spring-ai-agent-utils 工具配置。
 * 当前先接入 SkillsTool，复用 resources/skills/{skillId}/SKILL.md。
 */
@Configuration
@Slf4j
public class AgentUtilsConfiguration {

    private final ResourceLoader resourceLoader;
    private final String configuredSkillsRoot;

    public AgentUtilsConfiguration(
        ResourceLoader resourceLoader,
        @Value("${app.ai.agent-utils.skills-root:classpath:skills}") String configuredSkillsRoot
    ) {
        this.resourceLoader = resourceLoader;
        this.configuredSkillsRoot = configuredSkillsRoot;
    }

    @Bean("interviewSkillsToolCallback")
    public ToolCallback interviewSkillsToolCallback() {
        String normalizedSkillsRoot = normalizeSkillsRoot(configuredSkillsRoot);
        Resource skillsRootResource = resourceLoader.getResource(normalizedSkillsRoot);

        if (!skillsRootResource.exists()) {
            throw new IllegalStateException("未找到 skills 根目录，请检查配置: " + normalizedSkillsRoot);
        }

        log.info("AgentUtils SkillsTool 已启用，skillsRoot={}, configured={}", normalizedSkillsRoot, configuredSkillsRoot);

        return SkillsTool.builder()
            .addSkillsResource(skillsRootResource)
            .build();
    }

    private String normalizeSkillsRoot(String raw) {
        if (raw == null || raw.isBlank()) {
            return "classpath:skills";
        }

        String normalized = raw.trim();
        normalized = normalized.replace('\\', '/');

        if (normalized.endsWith("/SKILL.md")) {
            normalized = normalized.substring(0, normalized.length() - "/SKILL.md".length());
        }

        int wildcardIndex = normalized.indexOf('*');
        if (wildcardIndex >= 0) {
            normalized = normalized.substring(0, wildcardIndex);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.isBlank() ? "classpath:skills" : normalized;
    }
}
