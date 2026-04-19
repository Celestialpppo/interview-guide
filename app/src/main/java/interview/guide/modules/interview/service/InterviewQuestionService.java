package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.constant.CommonConstants.InterviewDefaults;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.HistoricalQuestion;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.interview.skill.InterviewSkillService.CategoryDTO;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillDTO;
import interview.guide.modules.interview.skill.InterviewSkillService.SkillCategoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 模拟面试出题编排服务。
 *
 * <p>职责边界：
 * 1. 根据 skill、简历、JD、历史题目组装 Prompt 上下文。
 * 2. 调用 LLM 生成结构化题目结果。
 * 3. 将结果转换为系统内部的 InterviewQuestionDTO。
 * 4. 在并发、降级和兜底策略之间做统一编排。
 *
 * <p>核心模式：
 * 1. 无简历：只生成方向题。
 * 2. 有简历：并行生成简历题和方向题，再合并结果。
 */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    /** 当模型未明确返回题型时使用的默认题型。 */
    private static final String DEFAULT_QUESTION_TYPE = "GENERAL";

    /** 单个主问题允许保留的最大追问数量。 */
    private static final int MAX_FOLLOW_UP_COUNT = 2;

    /** 有简历时，主问题默认按 60% 简历题 + 40% 方向题拆分。 */
    private static final double RESUME_QUESTION_RATIO = 0.6;

    /**
     * 无简历模式下附加给 system prompt 的约束。
     * 作用是显式告诉模型不要虚构候选人经历，而应输出标准方向题。
     */
    private static final String GENERIC_MODE_SYSTEM_APPEND = """
        \n\n# 通用面试模式
        本次面试无候选人简历，请出该方向的标准面试题。
        - 禁止出现"你在简历中提到..."、"你在项目中..."等暗示存在简历的表述
        - 问题表述应与简历无关，直接考察该方向的技术能力
        """;

    /**
     * 难度枚举到自然语言说明的映射，直接注入 Prompt。
        */
    private static final Map<String, String> DIFFICULTY_DESCRIPTIONS = Map.of(
        "junior", "校招/0-1年经验。考察基础概念和简单应用。",
        "mid", "1-3年经验。考察原理理解和实战经验。",
        "senior", "3年+经验。考察架构设计和深度调优。"
    );

    /**
     * 最后的通用兜底题库。
     * 每条记录依次表示：问题文本、题型、分类展示名。
     */
    private static final String[][] GENERIC_FALLBACK_QUESTIONS = {
        {"请描述一个你主导解决的技术难题，你的分析思路是什么？", "GENERAL", "综合能力"},
        {"你在做技术方案选型时，通常考虑哪些因素？请举例说明。", "GENERAL", "综合能力"},
        {"请分享一次你处理线上故障的经历，从发现到修复的完整过程。", "GENERAL", "综合能力"},
        {"你如何保证代码质量？介绍你实践过的有效手段。", "GENERAL", "综合能力"},
        {"描述一个你做过的技术优化案例，优化的动机、方案和效果。", "GENERAL", "综合能力"},
        {"你在团队协作中遇到过最大的分歧是什么？如何解决的？", "GENERAL", "综合能力"},
    };

    /** 方向题模式使用的 system prompt 模板。 */
    private final PromptTemplate skillSystemPromptTemplate;
    /** 方向题模式使用的 user prompt 模板。 */
    private final PromptTemplate skillUserPromptTemplate;
    /** 简历题模式使用的 system prompt 模板。 */
    private final PromptTemplate resumeSystemPromptTemplate;
    /** 简历题模式使用的 user prompt 模板。 */
    private final PromptTemplate resumeUserPromptTemplate;
    /** 将模型结构化输出转换为 QuestionListDTO。 */
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    /** 封装结构化输出调用、重试和错误码映射。 */
    private final StructuredOutputInvoker structuredOutputInvoker;
    /** 提供 Skill、分类分配、reference 和 persona 等能力。 */
    private final InterviewSkillService skillService;
    /** 有简历场景下并发生成两路题单的执行器。 */
    private final ExecutorService questionExecutor;
    /** 最终生效的追问数量上限。 */
    private final int followUpCount;

    /** 模型返回的最外层题单结构。 */
    private record QuestionListDTO(List<QuestionDTO> questions) {}

    /** 模型返回的单道题结构，主问题和追问会在后续被展开。 */
    private record QuestionDTO(String question, String type, String category,
                               String topicSummary, List<String> followUps) {}

    /**
     * 初始化出题服务。
     *
     * <p>主要完成模板加载、依赖注入、执行器初始化和追问数量约束。
     */
    public InterviewQuestionService(
            StructuredOutputInvoker structuredOutputInvoker,
            InterviewSkillService skillService,
            InterviewQuestionProperties properties,
            ResourceLoader resourceLoader) throws IOException {
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.skillService = skillService;
        // 出题属于典型的 I/O 密集型场景，使用虚拟线程可以低成本并发等待 LLM 返回。
        this.questionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.skillSystemPromptTemplate = loadTemplate(resourceLoader, properties.getQuestionSystemPromptPath());
        this.skillUserPromptTemplate = loadTemplate(resourceLoader, properties.getQuestionUserPromptPath());
        this.resumeSystemPromptTemplate = loadTemplate(resourceLoader, properties.getResumeQuestionSystemPromptPath());
        this.resumeUserPromptTemplate = loadTemplate(resourceLoader, properties.getResumeQuestionUserPromptPath());
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(properties.getFollowUpCount(), MAX_FOLLOW_UP_COUNT));
    }

    /** 从资源路径加载 Prompt 模板。 */
    private static PromptTemplate loadTemplate(ResourceLoader loader, String location) throws IOException {
        return new PromptTemplate(loader.getResource(location).getContentAsString(StandardCharsets.UTF_8));
    }

    /** Bean 销毁时关闭执行器，避免后台虚拟线程残留。 */
    @PreDestroy
    void destroy() {
        questionExecutor.shutdownNow();
    }

    /**
     * 统一出题入口。
     *
     * <p>无简历时同步生成方向题；有简历时并行生成简历题和方向题，再按结果降级或合并。
     */
    public List<InterviewQuestionDTO> generateQuestionsBySkill(
            ChatClient chatClient,
            String skillId,
            String difficulty,
            String resumeText,
            int questionCount,
            List<HistoricalQuestion> historicalQuestions,
            List<CategoryDTO> customCategories,
            String jdText) {

        SkillDTO skill = resolveSkill(skillId, customCategories, jdText);
        String difficultyDesc = resolveDifficulty(difficulty);

        // 历史题会被压缩成提示词片段，帮助模型避开已经考过的知识点。
        boolean hasResume = resumeText != null && !resumeText.isBlank();
        String historicalSection = buildHistoricalSection(historicalQuestions);
        // 没有简历时无需拆双路，直接生成方向题即可。
        if (!hasResume) {
            return generateDirectionOnly(chatClient, skill, difficultyDesc, questionCount, historicalSection);
        }

        // 有简历时将题量拆成两路：简历题关注经历，方向题关注岗位能力。
        int resumeCount = Math.max(1, (int) Math.round(questionCount * RESUME_QUESTION_RATIO));
        int directionCount = questionCount - resumeCount;

        log.info("并行出题: skill={}, total={}, resumeCount={}, directionCount={}",
            skillId, questionCount, resumeCount, directionCount);

        // 两路任务并发启动，以减少等待 LLM 返回带来的总耗时。
        CompletableFuture<List<InterviewQuestionDTO>> resumeFuture = CompletableFuture.supplyAsync(
            () -> generateResumeQuestions(chatClient, resumeText, resumeCount, skill, difficultyDesc, historicalSection),
            questionExecutor);

        CompletableFuture<List<InterviewQuestionDTO>> directionFuture = CompletableFuture.supplyAsync(
            () -> generateDirectionOnly(chatClient, skill, difficultyDesc, directionCount, historicalSection),
            questionExecutor);

        List<InterviewQuestionDTO> resumeQuestions;
        List<InterviewQuestionDTO> directionQuestions;
        try {
            // 先等待简历题；若失败，则整次流程降级为“全量方向题”。
            resumeQuestions = resumeFuture.join();
        } catch (CompletionException e) {
            log.error("简历题生成失败，降级为全方向题", e.getCause());
            // 尝试取消仍在后台运行的方向题任务，避免继续浪费资源。
            directionFuture.cancel(true);
            return generateDirectionOnly(chatClient, skill, difficultyDesc, questionCount, historicalSection);
        }

        try {
            // 方向题失败时优先保留已经成功的简历题结果。
            directionQuestions = directionFuture.join();
        } catch (CompletionException e) {
            log.error("方向题生成失败，降级为全简历题", e.getCause());
            if (resumeQuestions.isEmpty()) {
                return generateFallbackQuestions(skill, questionCount);
            }
            return resumeQuestions;
        }

        // 两路都返回空时，说明模型结果不可用，回落到本地兜底题库。
        if (resumeQuestions.isEmpty() && directionQuestions.isEmpty()) {
            log.warn("简历题和方向题均为空，回退到默认问题");
            return generateFallbackQuestions(skill, questionCount);
        }

        // 两路都成功时合并题单，并修正第二批题目的索引和追问父索引。
        List<InterviewQuestionDTO> merged = mergeQuestionBatches(resumeQuestions, directionQuestions);
        log.info("并行出题成功: 简历题={}, 方向题={}, 合计={}",
            resumeQuestions.size(), directionQuestions.size(), merged.size());
        return merged;
    }

    /**
     * 生成简历题。
     *
     * <p>该方法聚焦候选人的经历、项目和简历内容，
     * 不负责做 Skill 分类分配，也不会拼接 reference section。
     */
    private List<InterviewQuestionDTO> generateResumeQuestions(
            ChatClient chatClient, String resumeText, int questionCount,
            SkillDTO skill, String difficultyDesc, String historicalSection) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", followUpCount);
            variables.put("skillName", skill.name());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("resumeText", resumeText);
            variables.put("historicalSection", historicalSection);

            String systemPrompt = resumeSystemPromptTemplate.render() + "\n\n" + outputConverter.getFormat();
            String userPrompt = resumeUserPromptTemplate.render(variables);

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                chatClient, systemPrompt, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "简历题生成失败：", "简历题", log);

            // 模型结果先转换为题目 DTO，再裁剪主问题数量，避免超发。
            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            questions = capToMainCount(questions, questionCount);
            log.info("简历题生成完成: 请求={}, 实际主问题={}",
                questionCount, questions.stream().filter(q -> !q.isFollowUp()).count());
            return questions;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历题生成异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 生成纯 Skill 方向题。
     *
     * <p>该方法最依赖 Skill 编排能力，会综合分类分配、reference、persona 和 JD 上下文。
     */
    private List<InterviewQuestionDTO> generateDirectionOnly(
            ChatClient chatClient, SkillDTO skill, String difficultyDesc,
            int questionCount, String historicalSection) {
        // 先按分类优先级为主问题分配题量，再把分配结果转成文本描述注入 Prompt。
        Map<String, Integer> allocation = skillService.calculateAllocation(skill.categories(), questionCount);
        String allocationTable = skillService.buildAllocationDescription(allocation, skill.categories());

        log.info("方向题生成: skill={}, total={}, allocation={}",
            skill.id(), questionCount, allocation);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", followUpCount);
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("skillName", skill.name());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("skillToolCommand", skill.id());
            variables.put("allocationTable", allocationTable);
            variables.put("historicalSection", historicalSection);
            variables.put("referenceSection", skillService.buildReferenceSection(skill, allocation));
            variables.put("personaSection", buildPersonaSection(skill.persona()));
            variables.put("jdSection", buildJdSection(skill.sourceJd()));

            // 无简历时附加一段约束，避免模型编造“你在简历里提到”之类的表述。
            String systemPrompt = skillSystemPromptTemplate.render()
                + GENERIC_MODE_SYSTEM_APPEND + outputConverter.getFormat();
            String userPrompt = skillUserPromptTemplate.render(variables);

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                chatClient, systemPrompt, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "方向题生成失败：", "方向题", log);

            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            // 如果连一个主问题都没有生成出来，说明这次方向题结果不可用，直接回落到兜底题库。
            if (questions.stream().filter(q -> !q.isFollowUp()).count() == 0) {
                log.warn("方向题返回空题单，回退到默认问题");
                return generateFallbackQuestions(skill, questionCount);
            }
            questions = capToMainCount(questions, questionCount);
            log.info("方向题生成完成: 请求={}, 实际主问题={}",
                questionCount, questions.stream().filter(q -> !q.isFollowUp()).count());
            return questions;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("方向题生成失败，回退到默认问题: {}", e.getMessage(), e);
            return generateFallbackQuestions(skill, questionCount);
        }
    }

    /**
     * 合并两批题目，并重写第二批题目的索引。
     *
     * <p>不能直接 addAll，因为追问的 parentQuestionIndex 依赖题目在最终列表中的位置。
     */
    private List<InterviewQuestionDTO> mergeQuestionBatches(
            List<InterviewQuestionDTO> first, List<InterviewQuestionDTO> second) {
        if (second.isEmpty()) {
            return first;
        }
        if (first.isEmpty()) {
            return second;
        }
        int offset = first.size();
        List<InterviewQuestionDTO> merged = new ArrayList<>(first);
        for (InterviewQuestionDTO q : second) {
            // 第二批题目接在第一批后面，因此主问题索引和追问父索引都要整体偏移。
            int newIndex = q.questionIndex() + offset;
            Integer newParent = q.parentQuestionIndex() != null
                ? q.parentQuestionIndex() + offset : null;
            merged.add(InterviewQuestionDTO.create(
                newIndex, q.question(), q.type(), q.category(),
                q.topicSummary(), q.isFollowUp(), newParent));
        }
        return merged;
    }

    /**
     * 解析当前请求最终使用的 Skill。
     *
     * <p>如果用户选择 custom 且携带 JD 解析出的分类，则现场构造自定义 Skill；
     * 否则直接读取预设 Skill。
     */
    private SkillDTO resolveSkill(String skillId, List<CategoryDTO> customCategories, String jdText) {
        if (InterviewSkillService.CUSTOM_SKILL_ID.equals(skillId)
                && customCategories != null && !customCategories.isEmpty()) {
            return skillService.buildCustomSkill(customCategories, jdText != null ? jdText : "");
        }
        return skillService.getSkill(skillId);
    }

    /** 将难度枚举转换为可直接注入 Prompt 的自然语言说明。 */
    private String resolveDifficulty(String difficulty) {
        return DIFFICULTY_DESCRIPTIONS.getOrDefault(
            difficulty != null ? difficulty : InterviewDefaults.DIFFICULTY,
            DIFFICULTY_DESCRIPTIONS.get(InterviewDefaults.DIFFICULTY));
    }

    /**
     * 将模型输出的 QuestionListDTO 展开为系统内部的题目 DTO 列表。
     *
     * <p>转换时会过滤空题目、标准化题型、展开追问，并建立追问到主问题的父子关系。
     */
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
            // 主问题先入列表，记录索引，便于后续所有追问引用该主问题。
            String type = (q.type() != null && !q.type().isBlank()) ? q.type().toUpperCase() : DEFAULT_QUESTION_TYPE;
            int mainQuestionIndex = index;
            questions.add(InterviewQuestionDTO.create(index++, q.question(), type, q.category(), q.topicSummary(), false, null));

            // 追问会再次清洗并限制数量，避免模型返回过长或空白的追问列表。
            List<String> followUps = sanitizeFollowUps(q.followUps());
            for (int i = 0; i < followUps.size(); i++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, followUps.get(i), type,
                    buildFollowUpCategory(q.category(), i + 1), null, true, mainQuestionIndex
                ));
            }
        }

        return questions;
    }

    /**
     * 将问题列表截断到指定的主问题数量（AI 多生时截断，少生时保留原样并记录警告）。
     */
    private List<InterviewQuestionDTO> capToMainCount(
            List<InterviewQuestionDTO> questions, int maxMainCount) {
        long currentMainCount = questions.stream().filter(q -> !q.isFollowUp()).count();

        if (currentMainCount <= maxMainCount) {
            if (currentMainCount < maxMainCount) {
                log.warn("AI 生成主问题不足: 请求={}, 实际={}", maxMainCount, currentMainCount);
            }
            return questions;
        }

        List<InterviewQuestionDTO> capped = new ArrayList<>();
        int mainSeen = 0;
        for (InterviewQuestionDTO q : questions) {
            if (!q.isFollowUp()) {
                mainSeen++;
            }
            if (mainSeen > maxMainCount) {
                break;
            }
            capped.add(q);
        }
        log.info("题目截断: 主问题 {} → {}", currentMainCount, maxMainCount);
        return capped;
    }

    /**
     * 生成兜底题目。
     *
     * <p>优先复用当前 Skill 的分类，尽量保持岗位相关性；若没有分类可用，再退回通用题库。
     */
    private List<InterviewQuestionDTO> generateFallbackQuestions(SkillDTO skill, int count) {
        List<SkillCategoryDTO> categories = skill != null ? skill.categories() : List.of();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (!categories.isEmpty()) {
            int generated = 0;
            while (generated < count) {
                // 按分类轮询生成兜底主问题，避免题目全部集中在同一个方向。
                SkillCategoryDTO cat = categories.get(generated % categories.size());
                String question = "请谈谈你在\"" + cat.label() + "\"方向的技术理解和实践经验。";
                questions.add(InterviewQuestionDTO.create(index++, question, cat.key(), cat.label(), null, false, null));
                int mainIndex = index - 1;
                for (int j = 0; j < followUpCount; j++) {
                    questions.add(InterviewQuestionDTO.create(
                        index++, buildDefaultFollowUp(question, j + 1),
                        cat.key(), buildFollowUpCategory(cat.label(), j + 1), null, true, mainIndex
                    ));
                }
                generated++;
            }
            return questions;
        }

        for (int i = 0; i < Math.min(count, GENERIC_FALLBACK_QUESTIONS.length); i++) {
            String[] q = GENERIC_FALLBACK_QUESTIONS[i];
            questions.add(InterviewQuestionDTO.create(index++, q[0], q[1], q[2], null, false, null));
            int mainIndex = index - 1;
            for (int j = 0; j < followUpCount; j++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, buildDefaultFollowUp(q[0], j + 1),
                    q[1], buildFollowUpCategory(q[2], j + 1), null, true, mainIndex
                ));
            }
        }
        return questions;
    }

    /**
     * 将历史题目压缩成 Prompt 片段。
     *
     * <p>这里只保留“已经考过哪些知识点”的摘要信息，不保留完整题干，
     * 目的是提醒模型避开重复考察，而不是重放历史题单。
     */
    private String buildHistoricalSection(List<HistoricalQuestion> historicalQuestions) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return "暂无历史提问";
        }

        Map<String, List<String>> grouped = new HashMap<>();
        for (HistoricalQuestion hq : historicalQuestions) {
            String type = hq.type() != null && !hq.type().isBlank() ? hq.type() : DEFAULT_QUESTION_TYPE;
            String summary = hq.topicSummary();
            if (summary == null || summary.isBlank()) {
                String q = hq.question();
                summary = q.length() > 30 ? q.substring(0, 30) + "…" : q;
            }
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(summary);
        }

        StringBuilder sb = new StringBuilder("已考过的知识点（避免重复出题）：\n");
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            sb.append(String.join(", ", entry.getValue()));
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 生成 persona 片段；若 Skill 未提供 persona，则使用默认面试官风格。 */
    private String buildPersonaSection(String persona) {
        if (persona == null || persona.isBlank()) {
            return "使用专业、直接、可执行的技术面试官风格。";
        }
        return persona;
    }

    /** 生成 JD 片段；只有传入 JD 时才会真正注入 Prompt。 */
    private String buildJdSection(String sourceJd) {
        if (sourceJd == null || sourceJd.isBlank()) {
            return "";
        }
        return "## 职位描述（JD）\n根据以下 JD 关键要求出题，确保题目与岗位实际需求相关：\n" + sourceJd;
    }

    /** 清洗模型返回的追问列表：去空、去首尾空白并限制数量。 */
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

    /** 为追问构造带序号的分类名，便于展示和区分层级。 */
    private String buildFollowUpCategory(String category, int order) {
        String base = (category == null || category.isBlank()) ? "追问" : category;
        return base + "（追问" + order + "）";
    }

    /** 生成兜底追问模板，第二问起默认转向故障定位和修复思路。 */
    private String buildDefaultFollowUp(String mainQuestion, int order) {
        if (order == 1) {
            return "基于\"" + mainQuestion + "\"，请结合你亲自做过的一个真实场景展开说明。";
        }
        return "基于\"" + mainQuestion + "\"，如果线上出现异常，你会如何定位并给出修复方案？";
    }
}
