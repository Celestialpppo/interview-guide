package interview.guide.modules.voiceinterview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.voiceinterview.dto.VoiceEvaluationDetailDTO;
import interview.guide.modules.voiceinterview.dto.VoiceEvaluationDetailDTO.AnswerDetail;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Voice Interview Evaluation Service
 * 语音面试评估服务
 * <p>
 * Generates per-question evaluation results aligned with text-based interview format,
 * enabling reuse of the InterviewDetailPanel frontend component.
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
     * Generate evaluation for a session (called by async consumer)
     */
    @Transactional
    public void generateEvaluation(Long sessionId) {
        try {
            log.info("Generating evaluation for session: {}", sessionId);

            VoiceInterviewSessionEntity session = getSession(sessionId);
            List<VoiceInterviewMessageEntity> messages = messageRepository
                    .findBySessionIdOrderBySequenceNumAsc(sessionId);

            if (messages.isEmpty()) {
                throw new BusinessException(ErrorCode.VOICE_EVALUATION_FAILED,
                        "面试会话无对话记录: " + sessionId);
            }

            // Build Q&A pairs from messages
            String qaRecords = buildQaRecords(messages);

            // Build and send evaluation prompt
            String prompt = buildEvaluationPrompt(session, qaRecords);
            log.debug("Evaluation prompt: {}", prompt);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("LLM response: {}", response);

            // Parse and save
            Map<String, Object> evaluationData = parseEvaluationResponse(response);
            saveEvaluation(sessionId, session, evaluationData);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating evaluation for session {}", sessionId, e);
            throw new BusinessException(ErrorCode.VOICE_EVALUATION_FAILED,
                    "生成评估失败: " + e.getMessage());
        }
    }

    /**
     * Get evaluation for a session
     */
    public VoiceEvaluationDetailDTO getEvaluation(Long sessionId) {
        log.info("Getting evaluation for session: {}", sessionId);

        VoiceInterviewEvaluationEntity evaluation = evaluationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOICE_EVALUATION_NOT_FOUND,
                        "评估结果不存在: " + sessionId));

        return buildDetailDTO(evaluation);
    }

    // ==================== Private Methods ====================

    /**
     * Build Q&A records from messages for the evaluation prompt.
     * Each message may contain both AI question and user answer.
     */
    private String buildQaRecords(List<VoiceInterviewMessageEntity> messages) {
        StringBuilder sb = new StringBuilder();
        int index = 0;

        for (VoiceInterviewMessageEntity msg : messages) {
            String aiText = msg.getAiGeneratedText();
            String userText = msg.getUserRecognizedText();

            // Only include exchanges where AI asked something or user answered
            if ((aiText != null && !aiText.isBlank()) || (userText != null && !userText.isBlank())) {
                sb.append("Q").append(index).append(":\n");
                if (aiText != null && !aiText.isBlank()) {
                    sb.append("  面试官：").append(aiText.trim()).append("\n");
                }
                if (userText != null && !userText.isBlank()) {
                    sb.append("  候选人：").append(userText.trim()).append("\n");
                }
                sb.append("\n");
                index++;
            }
        }

        return sb.toString();
    }

    /**
     * Build evaluation prompt — per-question evaluation aligned with text interview format.
     */
    private String buildEvaluationPrompt(VoiceInterviewSessionEntity session, String qaRecords) {
        int durationMinutes = session.getActualDuration() != null
                ? session.getActualDuration() / 60
                : 30;

        return String.format("""
                你是一位资深的技术面试官，现在需要严格评估一场语音面试的表现。

                【面试信息】
                - 面试官角色：%s
                - 面试时长：%d分钟
                - 对话记录（Q&A对）：
                %s

                【严格评分规则】
                1. 逐题评估：对每个 Q&A 对独立评分。
                2. 未作答/无效回答（如"嗯"、"不知道"、空白）必须给 0 分。
                3. 回答过于简短或浅尝辄止，不得超过 40 分。
                4. 评分必须基于对话记录中的实际证据，禁止凭空给分。
                5. overallScore 取所有题目得分的算术平均值。
                6. 如果整场面试几乎没有实质回答，overallScore 不得超过 20。

                【评分等级】（严格遵守）
                - 90-100：优秀（深入底层原理、丰富实战经验、表达清晰有逻辑）
                - 75-89：良好（技术基础扎实、能结合实际经验、表达有条理）
                - 60-74：及格（掌握基础知识、有一定项目经验、表达基本清晰）
                - 40-59：不及格（知识有明显漏洞、项目经验浅薄、表达混乱）
                - 0-39：差（基本未作答、或回答完全错误、无法沟通）

                【评分维度】（用于综合评判每题得分）
                - 准确性（40%%）：技术概念的准确程度
                - 完整性（20%%）：回答是否覆盖要点
                - 深度（25%%）：是否深入底层原理
                - 表达（15%%）：清晰度、条理性

                【输出格式】（必须严格遵守 JSON 格式，不要包含其他文字或 markdown 标记）
                {
                  "totalQuestions": 5,
                  "overallScore": 65,
                  "overallFeedback": "基于所有问答表现的综合评价...",
                  "questionDetails": [
                    {
                      "questionIndex": 0,
                      "question": "面试官的提问内容",
                      "category": "技术/项目/HR/自我介绍",
                      "userAnswer": "候选人的回答内容",
                      "score": 60,
                      "feedback": "对该回答的具体评价..."
                    }
                  ],
                  "strengths": ["基于实际回答的优势1", "优势2"],
                  "improvements": ["基于实际表现的改进建议1", "建议2"],
                  "referenceAnswers": [
                    {
                      "questionIndex": 0,
                      "question": "面试官的提问内容",
                      "referenceAnswer": "参考答案...",
                      "keyPoints": ["要点1", "要点2"]
                    }
                  ]
                }
                """,
                session.getRoleType(),
                durationMinutes,
                qaRecords
        );
    }

    /**
     * Parse AI evaluation response into a structured map.
     */
    private Map<String, Object> parseEvaluationResponse(String response) {
        try {
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            }

            throw new BusinessException(ErrorCode.VOICE_EVALUATION_FAILED, "评估响应中未找到有效 JSON");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse evaluation response", e);
            throw new BusinessException(ErrorCode.VOICE_EVALUATION_FAILED,
                    "解析评估响应失败: " + e.getMessage());
        }
    }

    /**
     * Save evaluation data to database as JSON fields.
     */
    private void saveEvaluation(Long sessionId, VoiceInterviewSessionEntity session,
                                Map<String, Object> data) {
        try {
            Number overallScore = (Number) data.get("overallScore");

            VoiceInterviewEvaluationEntity entity = VoiceInterviewEvaluationEntity.builder()
                    .sessionId(sessionId)
                    .overallScore(overallScore != null ? overallScore.intValue() : 0)
                    .overallFeedback((String) data.get("overallFeedback"))
                    .questionEvaluationsJson(objectMapper.writeValueAsString(data.get("questionDetails")))
                    .strengthsJson(objectMapper.writeValueAsString(data.get("strengths")))
                    .improvementsJson(objectMapper.writeValueAsString(data.get("improvements")))
                    .referenceAnswersJson(objectMapper.writeValueAsString(data.get("referenceAnswers")))
                    .interviewerRole(session.getRoleType())
                    .interviewDate(session.getStartTime())
                    .build();

            evaluationRepository.save(entity);
            log.info("Saved evaluation for session: {}, score: {}", sessionId, entity.getOverallScore());
        } catch (Exception e) {
            log.error("Error saving evaluation for session: {}", sessionId, e);
            throw new BusinessException(ErrorCode.VOICE_EVALUATION_FAILED,
                    "保存评估失败: " + e.getMessage());
        }
    }

    /**
     * Build VoiceEvaluationDetailDTO from entity (aligned with text interview format).
     */
    private VoiceEvaluationDetailDTO buildDetailDTO(VoiceInterviewEvaluationEntity entity) {
        try {
            List<Map<String, Object>> questionDetails = objectMapper.readValue(
                    entity.getQuestionEvaluationsJson(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            List<String> strengths = objectMapper.readValue(
                    entity.getStrengthsJson(),
                    new TypeReference<List<String>>() {}
            );

            List<String> improvements = objectMapper.readValue(
                    entity.getImprovementsJson(),
                    new TypeReference<List<String>>() {}
            );

            List<Map<String, Object>> referenceAnswers = objectMapper.readValue(
                    entity.getReferenceAnswersJson(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            // Build a lookup map for reference answers by questionIndex
            Map<Integer, Map<String, Object>> refAnswerMap = referenceAnswers.stream()
                    .collect(Collectors.toMap(
                            ra -> ((Number) ra.get("questionIndex")).intValue(),
                            ra -> ra,
                            (a, b) -> a
                    ));

            // Build per-answer details
            List<AnswerDetail> answers = new ArrayList<>();
            for (Map<String, Object> qd : questionDetails) {
                int qIndex = ((Number) qd.get("questionIndex")).intValue();
                Map<String, Object> refAnswer = refAnswerMap.get(qIndex);

                AnswerDetail answer = AnswerDetail.builder()
                        .questionIndex(qIndex)
                        .question((String) qd.get("question"))
                        .category((String) qd.get("category"))
                        .userAnswer((String) qd.get("userAnswer"))
                        .score(((Number) qd.get("score")).intValue())
                        .feedback((String) qd.get("feedback"))
                        .referenceAnswer(refAnswer != null ? (String) refAnswer.get("referenceAnswer") : null)
                        .keyPoints(refAnswer != null && refAnswer.get("keyPoints") != null
                                ? objectMapper.convertValue(refAnswer.get("keyPoints"), new TypeReference<List<String>>() {})
                                : null)
                        .build();
                answers.add(answer);
            }

            return VoiceEvaluationDetailDTO.builder()
                    .sessionId(entity.getSessionId())
                    .totalQuestions(answers.size())
                    .overallScore(entity.getOverallScore())
                    .overallFeedback(entity.getOverallFeedback())
                    .strengths(strengths)
                    .improvements(improvements)
                    .answers(answers)
                    .build();

        } catch (Exception e) {
            log.error("Error building evaluation detail DTO for session: {}", entity.getSessionId(), e);
            throw new BusinessException(ErrorCode.VOICE_EVALUATION_FAILED,
                    "构建评估结果失败: " + e.getMessage());
        }
    }

    private VoiceInterviewSessionEntity getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND,
                        "语音面试会话不存在: " + sessionId));
    }
}
