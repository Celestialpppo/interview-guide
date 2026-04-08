package interview.guide.modules.voiceinterview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.voiceinterview.dto.EvaluationResponseDTO;
import interview.guide.modules.voiceinterview.model.VoiceInterviewEvaluationEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewMessageEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Voice Interview Evaluation Service
 * 语音面试评估服务
 * <p>
 * Provides AI-powered evaluation and scoring for voice interview sessions.
 * This service analyzes conversation history, generates scores per dimension,
 * and provides actionable feedback to candidates.
 * </p>
 */
@Service
@Slf4j
public class VoiceInterviewEvaluationService {

    private final ChatClient chatClient;
    private final VoiceInterviewEvaluationRepository evaluationRepository;
    private final VoiceInterviewMessageRepository messageRepository;
    private final VoiceInterviewSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public VoiceInterviewEvaluationService(
            ChatClient.Builder chatClientBuilder,
            VoiceInterviewEvaluationRepository evaluationRepository,
            VoiceInterviewMessageRepository messageRepository,
            VoiceInterviewSessionRepository sessionRepository,
            ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.evaluationRepository = evaluationRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate evaluation for a session
     * 生成会话评估结果
     *
     * @param sessionId Session ID
     * @return Generated evaluation result
     */
    @Transactional
    public EvaluationResponseDTO generateEvaluation(Long sessionId) {
        try {
            log.info("Generating evaluation for session: {}", sessionId);

            // Load session and messages
            VoiceInterviewSessionEntity session = getSession(sessionId);
            List<VoiceInterviewMessageEntity> messages = messageRepository
                    .findBySessionIdOrderBySequenceNumAsc(sessionId);

            if (messages.isEmpty()) {
                throw new RuntimeException("No messages found for session: " + sessionId);
            }

            // Build conversation history
            String conversationHistory = buildConversationHistory(messages);

            // Build evaluation prompt
            String evaluationPrompt = buildEvaluationPrompt(session, conversationHistory);

            log.debug("Evaluation prompt: {}", evaluationPrompt);

            // Call LLM
            String response = chatClient.prompt()
                    .user(evaluationPrompt)
                    .call()
                    .content();

            log.debug("LLM response: {}", response);

            // Parse response
            Map<String, Object> evaluationData = parseEvaluationResponse(response);

            // Save to database
            VoiceInterviewEvaluationEntity evaluation = saveEvaluation(sessionId, session,
                    evaluationData);

            return buildEvaluationDTO(evaluation);

        } catch (Exception e) {
            log.error("Error generating evaluation for session {}", sessionId, e);
            throw new RuntimeException("Failed to generate evaluation: " + e.getMessage(), e);
        }
    }

    /**
     * Get evaluation for a session
     * 获取会话评估结果
     *
     * @param sessionId Session ID
     * @return Evaluation result
     */
    public EvaluationResponseDTO getEvaluation(Long sessionId) {
        log.info("Getting evaluation for session: {}", sessionId);

        VoiceInterviewEvaluationEntity evaluation = evaluationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found for session: " + sessionId));

        return buildEvaluationDTO(evaluation);
    }

    /**
     * Build conversation history from messages
     * 构建对话历史
     *
     * @param messages List of interview messages
     * @return Formatted conversation history
     */
    private String buildConversationHistory(List<VoiceInterviewMessageEntity> messages) {
        return messages.stream()
                .map(msg -> {
                    StringBuilder sb = new StringBuilder();
                    if (msg.getUserRecognizedText() != null && !msg.getUserRecognizedText().isBlank()) {
                        sb.append("候选人：").append(msg.getUserRecognizedText()).append("\n");
                    }
                    if (msg.getAiGeneratedText() != null && !msg.getAiGeneratedText().isBlank()) {
                        sb.append("面试官：").append(msg.getAiGeneratedText()).append("\n");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Build evaluation prompt for AI
     * 构建评估提示词
     *
     * @param session Interview session
     * @param conversationHistory Formatted conversation history
     * @return AI prompt string
     */
    private String buildEvaluationPrompt(VoiceInterviewSessionEntity session, String conversationHistory) {
        int durationMinutes = session.getActualDuration() != null
                ? session.getActualDuration() / 60
                : 30;

        return String.format("""
                你是一位资深的技术面试官，现在需要评估一场面试的表现。

                【面试信息】
                - 面试官角色：%s
                - 面试时长：%d分钟
                - 对话记录：
                %s

                【评估要求】
                请从以下几个维度进行评估（每项0-100分）：
                1. 技术知识：深度和广度
                2. 项目经验：实际贡献和解决问题的能力
                3. 沟通表达：清晰度、逻辑性
                4. 逻辑思维：分析问题、解决问题的思路

                【输出格式】（必须严格遵守JSON格式）
                {
                  "overall_score": 85,
                  "overall_rating": "良好",
                  "tech_knowledge_score": 80,
                  "tech_knowledge_comment": "...",
                  "project_exp_score": 90,
                  "project_exp_comment": "...",
                  "communication_score": 85,
                  "communication_comment": "...",
                  "logical_thinking_score": 75,
                  "logical_thinking_comment": "...",
                  "improvement_suggestions": ["建议1", "建议2"],
                  "strengths_summary": "候选人的主要优势..."
                }
                """,
                session.getRoleType(),
                durationMinutes,
                conversationHistory
        );
    }

    /**
     * Parse AI evaluation response
     * 解析AI评估响应
     *
     * @param response AI response string
     * @return Parsed evaluation data map
     */
    private Map<String, Object> parseEvaluationResponse(String response) {
        try {
            // Extract JSON from response (in case there's extra text)
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            }

            throw new RuntimeException("No valid JSON found in response");

        } catch (Exception e) {
            log.error("Failed to parse evaluation response", e);
            // Return default evaluation
            Map<String, Object> defaultEval = new HashMap<>();
            defaultEval.put("overall_score", 70);
            defaultEval.put("overall_rating", "一般");
            defaultEval.put("tech_knowledge_score", 70);
            defaultEval.put("tech_knowledge_comment", "无法生成详细评价");
            defaultEval.put("project_exp_score", 70);
            defaultEval.put("project_exp_comment", "无法生成详细评价");
            defaultEval.put("communication_score", 70);
            defaultEval.put("communication_comment", "无法生成详细评价");
            defaultEval.put("logical_thinking_score", 70);
            defaultEval.put("logical_thinking_comment", "无法生成详细评价");
            defaultEval.put("improvement_suggestions", List.of("建议多练习面试技巧"));
            defaultEval.put("strengths_summary", "系统无法生成详细评价");
            return defaultEval;
        }
    }

    /**
     * Save evaluation to database
     * 保存评估到数据库
     *
     * @param sessionId Session ID
     * @param session Interview session entity
     * @param evaluationData Parsed evaluation data
     * @return Saved evaluation entity
     */
    private VoiceInterviewEvaluationEntity saveEvaluation(Long sessionId,
                                                          VoiceInterviewSessionEntity session,
                                                          Map<String, Object> evaluationData) {
        try {
            // Safe extraction with null checks to prevent NPE from incomplete LLM responses
            Number overallScore = (Number) evaluationData.get("overall_score");
            Number techKnowledgeScore = (Number) evaluationData.get("tech_knowledge_score");
            Number projectExpScore = (Number) evaluationData.get("project_exp_score");
            Number communicationScore = (Number) evaluationData.get("communication_score");
            Number logicalThinkingScore = (Number) evaluationData.get("logical_thinking_score");

            VoiceInterviewEvaluationEntity evaluation = VoiceInterviewEvaluationEntity.builder()
                    .sessionId(sessionId)
                    .overallScore(overallScore != null ? overallScore.intValue() : 0)
                    .overallRating((String) evaluationData.get("overall_rating"))
                    .techKnowledgeScore(techKnowledgeScore != null ? techKnowledgeScore.intValue() : 0)
                    .techKnowledgeComment((String) evaluationData.get("tech_knowledge_comment"))
                    .projectExpScore(projectExpScore != null ? projectExpScore.intValue() : 0)
                    .projectExpComment((String) evaluationData.get("project_exp_comment"))
                    .communicationScore(communicationScore != null ? communicationScore.intValue() : 0)
                    .communicationComment((String) evaluationData.get("communication_comment"))
                    .logicalThinkingScore(logicalThinkingScore != null ? logicalThinkingScore.intValue() : 0)
                    .logicalThinkingComment((String) evaluationData.get("logical_thinking_comment"))
                    .improvementSuggestions(objectMapper.writeValueAsString(evaluationData.get("improvement_suggestions")))
                    .strengthsSummary((String) evaluationData.get("strengths_summary"))
                    .interviewerRole(session.getRoleType())
                    .interviewDate(session.getStartTime())
                    .build();

            return evaluationRepository.save(evaluation);
        } catch (Exception e) {
            log.error("Error saving evaluation", e);
            throw new RuntimeException("Failed to save evaluation: " + e.getMessage(), e);
        }
    }

    /**
     * Build evaluation DTO from entity
     * 构建评估DTO
     *
     * @param evaluation Evaluation entity
     * @return Evaluation response DTO
     */
    private EvaluationResponseDTO buildEvaluationDTO(VoiceInterviewEvaluationEntity evaluation) {
        try {
            List<String> suggestions = objectMapper.readValue(
                    evaluation.getImprovementSuggestions(),
                    new TypeReference<List<String>>() {}
            );

            return EvaluationResponseDTO.builder()
                    .sessionId(evaluation.getSessionId())
                    .overallScore(evaluation.getOverallScore())
                    .overallRating(evaluation.getOverallRating())
                    .techKnowledge(Map.of(
                            "score", evaluation.getTechKnowledgeScore(),
                            "comment", evaluation.getTechKnowledgeComment()
                    ))
                    .projectExp(Map.of(
                            "score", evaluation.getProjectExpScore(),
                            "comment", evaluation.getProjectExpComment()
                    ))
                    .communication(Map.of(
                            "score", evaluation.getCommunicationScore(),
                            "comment", evaluation.getCommunicationComment()
                    ))
                    .logicalThinking(Map.of(
                            "score", evaluation.getLogicalThinkingScore(),
                            "comment", evaluation.getLogicalThinkingComment()
                    ))
                    .improvementSuggestions(suggestions)
                    .strengthsSummary(evaluation.getStrengthsSummary())
                    .build();

        } catch (Exception e) {
            log.error("Error building evaluation DTO", e);
            throw new RuntimeException("Failed to build evaluation DTO: " + e.getMessage(), e);
        }
    }

    /**
     * Get session by ID
     * 获取会话
     *
     * @param sessionId Session ID
     * @return Session entity
     */
    private VoiceInterviewSessionEntity getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
    }
}
