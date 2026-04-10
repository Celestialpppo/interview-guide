package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.constant.CommonConstants.InterviewDefaults;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillCategoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面试问题生成服务
 * 基于 Skill 动态出题，type 由 Skill category key 驱动
 */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    private static final String DEFAULT_QUESTION_TYPE = "GENERAL";

    private final PromptTemplate skillSystemPromptTemplate;
    private final PromptTemplate skillUserPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final InterviewSkillService skillService;
    private final int followUpCount;

    private static final int MAX_FOLLOW_UP_COUNT = 2;

    private static final Map<String, String> DIFFICULTY_DESCRIPTIONS = Map.of(
        "junior", "校招/0-1年经验。考察基础概念和简单应用。",
        "mid", "1-3年经验。考察原理理解和实战经验。",
        "senior", "3年+经验。考察架构设计和深度调优。"
    );

    // 通用行为/软技能兜底题（与方向无关）
    private static final String[][] GENERIC_FALLBACK_QUESTIONS = {
        {"请描述一个你主导解决的技术难题，你的分析思路是什么？", "GENERAL", "综合能力"},
        {"你在做技术方案选型时，通常考虑哪些因素？请举例说明。", "GENERAL", "综合能力"},
        {"请分享一次你处理线上故障的经历，从发现到修复的完整过程。", "GENERAL", "综合能力"},
        {"你如何保证代码质量？介绍你实践过的有效手段。", "GENERAL", "综合能力"},
        {"描述一个你做过的技术优化案例，优化的动机、方案和效果。", "GENERAL", "综合能力"},
        {"你在团队协作中遇到过最大的分歧是什么？如何解决的？", "GENERAL", "综合能力"},
    };

    private record QuestionListDTO(List<QuestionDTO> questions) {}

    private record QuestionDTO(String question, String type, String category, List<String> followUps) {}

    public InterviewQuestionService(
            StructuredOutputInvoker structuredOutputInvoker,
            InterviewSkillService skillService,
            InterviewQuestionProperties properties,
            ResourceLoader resourceLoader) throws IOException {
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.skillService = skillService;
        this.skillSystemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(properties.getQuestionSystemPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.skillUserPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(properties.getQuestionUserPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(properties.getFollowUpCount(), MAX_FOLLOW_UP_COUNT));
    }

    public List<InterviewQuestionDTO> generateQuestionsBySkill(
            ChatClient chatClient,
            String skillId,
            String difficulty,
            String resumeText,
            int questionCount,
            List<String> historicalQuestions) {

        SkillDTO skill = skillService.getSkill(skillId);
        Map<String, Integer> allocation = skillService.calculateAllocation(skill.categories(), questionCount);
        String allocationTable = skillService.buildAllocationDescription(allocation, skill.categories());

        String difficultyDesc = DIFFICULTY_DESCRIPTIONS.getOrDefault(
            difficulty != null ? difficulty : InterviewDefaults.DIFFICULTY,
            DIFFICULTY_DESCRIPTIONS.get(InterviewDefaults.DIFFICULTY));

        log.info("Skill 驱动出题: skill={}, difficulty={}, total={}, allocation={}",
            skillId, difficulty, questionCount, allocation);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", followUpCount);
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("skillName", skill.name());
            variables.put("skillToolCommand", skill.id());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("allocationTable", allocationTable);
            variables.put("resumeSection", buildResumeSection(resumeText));
            variables.put("historicalSection", buildHistoricalSection(historicalQuestions));
            variables.put("referenceSection", skillService.buildReferenceSection(skill, allocation));
            variables.put("personaSection", buildPersonaSection(skill.persona()));

            String systemPrompt = skillSystemPromptTemplate.render();
            String userPrompt = skillUserPromptTemplate.render(variables);
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                chatClient, systemPromptWithFormat, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "面试问题生成失败：", "Skill 驱动出题", log
            );

            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            log.info("Skill 驱动出题成功: {} 个问题", questions.size());
            return questions;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Skill 驱动出题失败，回退到默认问题: {}", e.getMessage(), e);
            return generateFallbackQuestions(skill, questionCount);
        }
    }

    public List<InterviewQuestionDTO> generateQuestions(ChatClient chatClient, String resumeText, int questionCount, List<String> historicalQuestions) {
        return generateQuestionsBySkill(chatClient, InterviewDefaults.SKILL_ID, InterviewDefaults.DIFFICULTY, resumeText, questionCount, historicalQuestions);
    }

    public List<InterviewQuestionDTO> generateQuestions(ChatClient chatClient, String resumeText, int questionCount) {
        return generateQuestions(chatClient, resumeText, questionCount, null);
    }

    private String buildResumeSection(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return "通用面试模式（无简历），请根据面试方向出标准面试题。";
        }
        return "---简历内容开始---\n" + resumeText + "\n---简历内容结束---";
    }

    private String buildHistoricalSection(List<String> historicalQuestions) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return "暂无历史提问";
        }
        return "---历史提问开始---\n" + String.join("\n", historicalQuestions) + "\n---历史提问结束---";
    }

    private String buildPersonaSection(String persona) {
        if (persona == null || persona.isBlank()) {
            return "使用专业、直接、可执行的技术面试官风格。";
        }
        return persona;
    }

    private List<InterviewQuestionDTO> convertToQuestions(QuestionListDTO dto) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (dto == null || dto.questions() == null) {
            return questions;
        }

        for (QuestionDTO q : dto.questions()) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            String type = (q.type() != null && !q.type().isBlank()) ? q.type().toUpperCase() : DEFAULT_QUESTION_TYPE;
            int mainQuestionIndex = index;
            questions.add(InterviewQuestionDTO.create(index++, q.question(), type, q.category(), false, null));

            List<String> followUps = sanitizeFollowUps(q.followUps());
            for (int i = 0; i < followUps.size(); i++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, followUps.get(i), type,
                    buildFollowUpCategory(q.category(), i + 1), true, mainQuestionIndex
                ));
            }
        }

        return questions;
    }

    /**
     * 两层兜底策略：
     * 1. 根据 Skill categories 生成通用占位题
     * 2. 如果 Skill 也拿不到，用通用行为/软技能题
     */
    private List<InterviewQuestionDTO> generateFallbackQuestions(SkillDTO skill, int count) {
        List<SkillCategoryDTO> categories = skill != null ? skill.categories() : List.of();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (!categories.isEmpty()) {
            // 第一层：按 Skill categories 生成通用占位题
            int generated = 0;
            for (SkillCategoryDTO cat : categories) {
                if (generated >= count) break;
                String question = "请谈谈你在\"" + cat.label() + "\"方向的技术理解和实践经验。";
                questions.add(InterviewQuestionDTO.create(index++, question, cat.key(), cat.label(), false, null));
                int mainIndex = index - 1;
                for (int j = 0; j < followUpCount && generated + j + 1 < count; j++) {
                    questions.add(InterviewQuestionDTO.create(
                        index++, buildDefaultFollowUp(question, j + 1),
                        cat.key(), buildFollowUpCategory(cat.label(), j + 1), true, mainIndex
                    ));
                }
                generated++;
            }
            return questions;
        }

        // 第二层：通用行为/软技能题
        for (int i = 0; i < Math.min(count, GENERIC_FALLBACK_QUESTIONS.length); i++) {
            String[] q = GENERIC_FALLBACK_QUESTIONS[i];
            questions.add(InterviewQuestionDTO.create(index++, q[0], q[1], q[2], false, null));
            int mainIndex = index - 1;
            for (int j = 0; j < followUpCount; j++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, buildDefaultFollowUp(q[0], j + 1),
                    q[1], buildFollowUpCategory(q[2], j + 1), true, mainIndex
                ));
            }
        }
        return questions;
    }

    private List<String> sanitizeFollowUps(List<String> followUps) {
        if (followUpCount == 0 || followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        return followUps.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(followUpCount)
            .collect(Collectors.toList());
    }

    private String buildFollowUpCategory(String category, int order) {
        String base = (category == null || category.isBlank()) ? "追问" : category;
        return base + "（追问" + order + "）";
    }

    private String buildDefaultFollowUp(String mainQuestion, int order) {
        if (order == 1) {
            return "基于\"" + mainQuestion + "\"，请结合你亲自做过的一个真实场景展开说明。";
        }
        return "基于\"" + mainQuestion + "\"，如果线上出现异常，你会如何定位并给出修复方案？";
    }
}
