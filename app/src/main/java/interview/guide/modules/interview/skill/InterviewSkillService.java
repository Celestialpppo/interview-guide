package interview.guide.modules.interview.skill;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class InterviewSkillService {

    private static final Logger log = LoggerFactory.getLogger(InterviewSkillService.class);
    private static final int MIN_JD_LENGTH = 50;

    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final BeanOutputConverter<CategoryListDTO> jdOutputConverter;
    private final PromptTemplate jdSystemPromptTemplate;

    /** 预设 Skill 注册表，启动时从 classpath:skills/*.yml 加载 */
    private final Map<String, InterviewSkillProperties.SkillDefinition> presetRegistry = new TreeMap<>();

    public InterviewSkillService(LlmProviderRegistry llmProviderRegistry,
                                  StructuredOutputInvoker structuredOutputInvoker,
                                  @Value("classpath:prompts/jd-parse-system.st") Resource jdPromptResource) throws IOException {
        this.llmProviderRegistry = llmProviderRegistry;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.jdOutputConverter = new BeanOutputConverter<>(CategoryListDTO.class) {};
        this.jdSystemPromptTemplate = new PromptTemplate(jdPromptResource.getContentAsString(StandardCharsets.UTF_8));
    }

    @PostConstruct
    void loadPresetSkills() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:skills/*.yml");
        Yaml yaml = new Yaml();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;
            String skillId = filename.replace(".yml", "");

            try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                InterviewSkillProperties.SkillDefinition def = yaml.loadAs(reader, InterviewSkillProperties.SkillDefinition.class);
                if (def.getName() != null) {
                    presetRegistry.put(skillId, def);
                    log.info("加载预设 Skill: {} ({})", skillId, def.getName());
                }
            }
        }

        log.info("共加载 {} 个预设 Skill", presetRegistry.size());
    }

    public List<SkillDTO> getAllSkills() {
        return presetRegistry.entrySet().stream()
            .map(e -> toSkillDTO(e.getKey(), e.getValue()))
            .toList();
    }

    public SkillDTO getSkill(String skillId) {
        InterviewSkillProperties.SkillDefinition preset = presetRegistry.get(skillId);
        if (preset != null) {
            return toSkillDTO(skillId, preset);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到面试主题: " + skillId);
    }

    public List<CategoryDTO> parseJd(String jdText) {
        if (jdText == null || jdText.length() < MIN_JD_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JD 内容太少（至少 " + MIN_JD_LENGTH + " 字），请补充后重试");
        }

        log.info("开始解析 JD，长度: {}", jdText.length());

        ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();
        String systemPrompt = jdSystemPromptTemplate.render() + "\n\n" + jdOutputConverter.getFormat();
        String userPrompt = "职位描述：\n" + jdText;

        try {
            CategoryListDTO result = structuredOutputInvoker.invoke(
                chatClient, systemPrompt, userPrompt, jdOutputConverter,
                ErrorCode.AI_SERVICE_ERROR, "JD 解析失败：", "JD 解析", log
            );

            if (result == null || result.categories() == null || result.categories().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "JD 解析结果为空，请重试");
            }
            return result.categories();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("JD 解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "JD 解析失败，请重试或选择预设主题");
        }
    }

    public Map<String, Integer> calculateAllocation(String skillId, int totalQuestions) {
        return calculateAllocation(getSkill(skillId).categories(), totalQuestions);
    }

    public Map<String, Integer> calculateAllocation(List<CategoryDTO> categories, int totalQuestions) {
        List<CategoryDTO> alwaysOneCats = new ArrayList<>();
        List<CategoryDTO> coreCats = new ArrayList<>();
        List<CategoryDTO> normalCats = new ArrayList<>();

        for (CategoryDTO cat : categories) {
            switch (cat.priority()) {
                case "ALWAYS_ONE" -> alwaysOneCats.add(cat);
                case "CORE" -> coreCats.add(cat);
                default -> normalCats.add(cat);
            }
        }

        Map<String, Integer> allocation = new LinkedHashMap<>();
        int remaining = totalQuestions;

        for (CategoryDTO cat : alwaysOneCats) {
            if (remaining > 0) { allocation.put(cat.key(), 1); remaining--; }
        }
        for (CategoryDTO cat : coreCats) {
            if (remaining > 0) { allocation.put(cat.key(), 1); remaining--; }
        }

        while (remaining > 0) {
            for (CategoryDTO cat : coreCats) {
                if (remaining <= 0) break;
                allocation.merge(cat.key(), 1, Integer::sum);
                remaining--;
            }
            for (CategoryDTO cat : normalCats) {
                if (remaining <= 0) break;
                allocation.merge(cat.key(), 1, Integer::sum);
                remaining--;
            }
            if (coreCats.isEmpty() && normalCats.isEmpty()) break;
        }

        for (CategoryDTO cat : normalCats) {
            allocation.putIfAbsent(cat.key(), 0);
        }

        log.debug("题目分配: total={}, allocation={}", totalQuestions, allocation);
        return allocation;
    }

    public String buildAllocationDescription(Map<String, Integer> allocation, List<CategoryDTO> categories) {
        StringBuilder sb = new StringBuilder();
        for (CategoryDTO cat : categories) {
            int count = allocation.getOrDefault(cat.key(), 0);
            if (count > 0) {
                sb.append("| ").append(cat.label()).append(" | ").append(count).append(" 题 | ").append(cat.priority()).append(" |\n");
            }
        }
        return sb.toString();
    }

    private SkillDTO toSkillDTO(String id, InterviewSkillProperties.SkillDefinition def) {
        InterviewSkillProperties.DisplayDef disp = def.getDisplay();
        DisplayDTO displayDTO = disp != null
            ? new DisplayDTO(disp.getIcon(), disp.getGradient(), disp.getIconBg(), disp.getIconColor())
            : null;
        return new SkillDTO(
            id, def.getName(), def.getDescription(),
            def.getCategories().stream()
                .map(c -> new CategoryDTO(c.getKey(), c.getLabel(), c.getPriority()))
                .toList(),
            true, null,
            def.getPersona(), displayDTO
        );
    }

    public record SkillDTO(String id, String name, String description, List<CategoryDTO> categories, boolean isPreset, String sourceJd, String persona, DisplayDTO display) {}
    public record DisplayDTO(String icon, String gradient, String iconBg, String iconColor) {}
    public record CategoryDTO(String key, String label, String priority) {}
    private record CategoryListDTO(List<CategoryDTO> categories) {}
}
