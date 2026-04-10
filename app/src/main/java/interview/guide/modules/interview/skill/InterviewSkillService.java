package interview.guide.modules.interview.skill;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InterviewSkillService {

    private static final Logger log = LoggerFactory.getLogger(InterviewSkillService.class);
    private static final int MIN_JD_LENGTH = 50;

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$");
    private static final Pattern SKILL_ID_PATTERN = Pattern.compile(".*/skills/([^/]+)/SKILL\\.md$");
    private static final String SKILL_META_FILE = "skill.meta.yml";

    private static final int MAX_REFERENCE_SECTION_CHARS = 12000;
    private static final int MAX_SINGLE_REFERENCE_CHARS = 3000;

    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final BeanOutputConverter<CategoryListDTO> jdOutputConverter;
    private final PromptTemplate jdSystemPromptTemplate;
    private final ResourceLoader resourceLoader;

    /** 预设 Skill 注册表，启动时从 classpath:skills/{skillId}/SKILL.md 加载 */
    private final Map<String, InterviewSkillProperties.SkillDefinition> presetRegistry = new TreeMap<>();

    public InterviewSkillService(LlmProviderRegistry llmProviderRegistry,
                                 StructuredOutputInvoker structuredOutputInvoker,
                                 ResourceLoader resourceLoader,
                                 @Value("classpath:prompts/jd-parse-system.st") Resource jdPromptResource) throws IOException {
        this.llmProviderRegistry = llmProviderRegistry;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.resourceLoader = resourceLoader;
        this.jdOutputConverter = new BeanOutputConverter<>(CategoryListDTO.class) {};
        this.jdSystemPromptTemplate = new PromptTemplate(jdPromptResource.getContentAsString(StandardCharsets.UTF_8));
    }

    @PostConstruct
    void loadPresetSkills() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:skills/*/SKILL.md");
        Yaml yaml = new Yaml();

        for (Resource resource : resources) {
            String skillId = extractSkillId(resource);
            if (skillId == null || "_shared".equals(skillId)) {
                continue;
            }

            InterviewSkillProperties.SkillDefinition def = parseSkillDefinition(skillId, resource, yaml);
            if (def.getName() == null || def.getName().isBlank()) {
                log.warn("跳过无效 Skill（缺少 name）: {}", skillId);
                continue;
            }

            presetRegistry.put(skillId, def);
            log.info("加载预设 Skill: {} ({})", skillId, def.getName());
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

    public Map<String, Integer> calculateAllocation(List<SkillCategoryDTO> categories, int totalQuestions) {
        List<SkillCategoryDTO> alwaysOneCats = new ArrayList<>();
        List<SkillCategoryDTO> coreCats = new ArrayList<>();
        List<SkillCategoryDTO> normalCats = new ArrayList<>();

        for (SkillCategoryDTO cat : categories) {
            switch (cat.priority()) {
                case "ALWAYS_ONE" -> alwaysOneCats.add(cat);
                case "CORE" -> coreCats.add(cat);
                default -> normalCats.add(cat);
            }
        }

        Map<String, Integer> allocation = new LinkedHashMap<>();
        int remaining = totalQuestions;

        for (SkillCategoryDTO cat : alwaysOneCats) {
            if (remaining > 0) {
                allocation.put(cat.key(), 1);
                remaining--;
            }
        }
        for (SkillCategoryDTO cat : coreCats) {
            if (remaining > 0) {
                allocation.put(cat.key(), 1);
                remaining--;
            }
        }

        while (remaining > 0) {
            for (SkillCategoryDTO cat : coreCats) {
                if (remaining <= 0) break;
                allocation.merge(cat.key(), 1, Integer::sum);
                remaining--;
            }
            for (SkillCategoryDTO cat : normalCats) {
                if (remaining <= 0) break;
                allocation.merge(cat.key(), 1, Integer::sum);
                remaining--;
            }
            if (coreCats.isEmpty() && normalCats.isEmpty()) break;
        }

        for (SkillCategoryDTO cat : normalCats) {
            allocation.putIfAbsent(cat.key(), 0);
        }

        log.debug("题目分配: total={}, allocation={}", totalQuestions, allocation);
        return allocation;
    }

    public String buildAllocationDescription(Map<String, Integer> allocation, List<SkillCategoryDTO> categories) {
        StringBuilder sb = new StringBuilder();
        for (SkillCategoryDTO cat : categories) {
            int count = allocation.getOrDefault(cat.key(), 0);
            if (count > 0) {
                sb.append("| ").append(cat.label()).append(" | ").append(count).append(" 题 | ").append(cat.priority()).append(" |\n");
            }
        }
        return sb.toString();
    }

    public String buildReferenceSection(SkillDTO skill, Map<String, Integer> allocation) {
        StringBuilder sb = new StringBuilder();

        for (SkillCategoryDTO category : skill.categories()) {
            if (allocation.getOrDefault(category.key(), 0) <= 0) {
                continue;
            }
            if (category.ref() == null || category.ref().isBlank()) {
                continue;
            }

            String referenceContent = loadReferenceContent(skill.id(), category.ref(), category.shared());
            if (referenceContent.isBlank()) {
                continue;
            }

            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("### ").append(category.label()).append(" (").append(category.key()).append(")\n");
            sb.append(referenceContent);

            if (sb.length() >= MAX_REFERENCE_SECTION_CHARS) {
                sb.setLength(MAX_REFERENCE_SECTION_CHARS);
                sb.append("\n...（references 已截断）");
                break;
            }
        }

        return sb.isEmpty() ? "未配置 references。" : sb.toString();
    }

    private InterviewSkillProperties.SkillDefinition parseSkillDefinition(String skillId, Resource resource, Yaml yaml) {
        try {
            String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
            Matcher matcher = FRONT_MATTER_PATTERN.matcher(markdown);
            if (!matcher.matches()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Skill 文件格式错误（缺少 front matter）: " + resource.getDescription());
            }

            String frontMatter = matcher.group(1);
            String body = matcher.group(2) != null ? matcher.group(2).trim() : "";

            InterviewSkillProperties.SkillFrontMatterDefinition frontMatterDef =
                yaml.loadAs(frontMatter, InterviewSkillProperties.SkillFrontMatterDefinition.class);

            InterviewSkillProperties.SkillDefinition definition = new InterviewSkillProperties.SkillDefinition();
            if (frontMatterDef != null) {
                definition.setName(frontMatterDef.getName());
                definition.setDescription(frontMatterDef.getDescription());
            }
            if (!body.isBlank()) {
                definition.setPersona(body);
            }

            InterviewSkillProperties.SkillMetaDefinition metaDef = loadSkillMetaDefinition(skillId, yaml);
            if (metaDef != null) {
                definition.setDisplayName(metaDef.getDisplayName());
                definition.setDisplay(metaDef.getDisplay());
                definition.setCategories(metaDef.getCategories());
            }

            if (definition.getCategories() == null) {
                definition.setCategories(List.of());
            }
            return definition;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "读取 Skill 文件失败: " + resource.getDescription());
        }
    }

    private InterviewSkillProperties.SkillMetaDefinition loadSkillMetaDefinition(String skillId, Yaml yaml) {
        String location = "classpath:skills/" + skillId + "/" + SKILL_META_FILE;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("skill meta 文件不存在，使用默认配置: skillId={}, location={}", skillId, location);
            return null;
        }

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            InterviewSkillProperties.SkillMetaDefinition meta = yaml.loadAs(content, InterviewSkillProperties.SkillMetaDefinition.class);
            return meta != null ? meta : new InterviewSkillProperties.SkillMetaDefinition();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取 skill meta 文件失败: " + location);
        }
    }

    private String extractSkillId(Resource resource) {
        try {
            String normalized = resource.getURL().toString().replace('\\', '/');
            Matcher matcher = SKILL_ID_PATTERN.matcher(normalized);
            if (matcher.matches()) {
                return matcher.group(1);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String loadReferenceContent(String skillId, String referenceFile, boolean shared) {
        if (!isSafeReferencePath(referenceFile)) {
            log.warn("忽略不安全的 reference 路径: skillId={}, ref={}", skillId, referenceFile);
            return "";
        }

        String location = shared
            ? "classpath:skills/_shared/references/" + referenceFile
            : "classpath:skills/" + skillId + "/references/" + referenceFile;

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("reference 文件不存在: skillId={}, location={}", skillId, location);
            return "";
        }

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            if (content.length() > MAX_SINGLE_REFERENCE_CHARS) {
                return content.substring(0, MAX_SINGLE_REFERENCE_CHARS) + "\n...（单文件内容已截断）";
            }
            return content;
        } catch (IOException e) {
            log.warn("读取 reference 失败: skillId={}, location={}", skillId, location, e);
            return "";
        }
    }

    private boolean isSafeReferencePath(String referenceFile) {
        return !referenceFile.contains("..")
            && !referenceFile.startsWith("/")
            && !referenceFile.startsWith("\\")
            && referenceFile.matches("[a-zA-Z0-9._/-]+");
    }

    private SkillDTO toSkillDTO(String id, InterviewSkillProperties.SkillDefinition def) {
        String skillDisplayName = (def.getDisplayName() != null && !def.getDisplayName().isBlank())
            ? def.getDisplayName()
            : def.getName();

        InterviewSkillProperties.DisplayDef disp = def.getDisplay();
        DisplayDTO displayDTO = disp != null
            ? new DisplayDTO(disp.getIcon(), disp.getGradient(), disp.getIconBg(), disp.getIconColor())
            : null;

        List<SkillCategoryDTO> categories = def.getCategories() == null
            ? List.of()
            : def.getCategories().stream()
                .map(c -> new SkillCategoryDTO(
                    c.getKey(),
                    c.getLabel(),
                    c.getPriority(),
                    c.getRef(),
                    Boolean.TRUE.equals(c.getShared())
                ))
                .toList();

        return new SkillDTO(
            id,
            skillDisplayName,
            def.getDescription(),
            categories,
            true,
            null,
            def.getPersona(),
            displayDTO
        );
    }

    public record SkillDTO(String id, String name, String description,
                           List<SkillCategoryDTO> categories,
                           boolean isPreset, String sourceJd, String persona, DisplayDTO display) {}

    public record DisplayDTO(String icon, String gradient, String iconBg, String iconColor) {}

    /**
     * 预设 Skill 分类（可携带 references 绑定信息）
     */
    public record SkillCategoryDTO(String key, String label, String priority, String ref, boolean shared) {}

    /**
     * JD 解析返回分类（轻量结构，避免耦合 references 细节）
     */
    public record CategoryDTO(String key, String label, String priority) {}

    private record CategoryListDTO(List<CategoryDTO> categories) {}
}
