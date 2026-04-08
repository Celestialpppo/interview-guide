package interview.guide.modules.voiceinterview.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import interview.guide.modules.voiceinterview.service.VoiceInterviewEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 语音面试评估 Stream 消费者
 * Voice interview evaluation stream consumer
 * <p>
 * Consumes evaluation tasks from Redis Stream and processes them asynchronously.
 * Reuses the same AbstractStreamConsumer pattern as text-based interviews.
 * </p>
 */
@Slf4j
@Component
public class VoiceEvaluateStreamConsumer extends AbstractStreamConsumer<VoiceEvaluateStreamConsumer.VoiceEvaluatePayload> {

    private final VoiceInterviewSessionRepository sessionRepository;
    private final VoiceInterviewEvaluationService evaluationService;

    public VoiceEvaluateStreamConsumer(RedisService redisService,
                                       VoiceInterviewSessionRepository sessionRepository,
                                       VoiceInterviewEvaluationService evaluationService) {
        super(redisService);
        this.sessionRepository = sessionRepository;
        this.evaluationService = evaluationService;
    }

    record VoiceEvaluatePayload(String sessionId) {}

    @Override
    protected String taskDisplayName() {
        return "语音面试评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "voice-evaluate-consumer";
    }

    @Override
    protected VoiceEvaluatePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(AsyncTaskStreamConstants.FIELD_VOICE_SESSION_ID);
        if (sessionId == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        return new VoiceEvaluatePayload(sessionId);
    }

    @Override
    protected String payloadIdentifier(VoiceEvaluatePayload payload) {
        return "voiceSessionId=" + payload.sessionId();
    }

    @Override
    protected void markProcessing(VoiceEvaluatePayload payload) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.PROCESSING, null);
    }

    @Override
    protected void processBusiness(VoiceEvaluatePayload payload) {
        String sessionId = payload.sessionId();

        // Verify session still exists before processing
        if (sessionRepository.findById(Long.parseLong(sessionId)).isEmpty()) {
            log.warn("会话已被删除，跳过评估任务: sessionId={}", sessionId);
            return;
        }

        // Delegate to existing evaluation service (LLM call + save)
        evaluationService.generateEvaluation(Long.parseLong(sessionId));
        log.info("语音面试评估完成: sessionId={}", sessionId);
    }

    @Override
    protected void markCompleted(VoiceEvaluatePayload payload) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(VoiceEvaluatePayload payload, String error) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(VoiceEvaluatePayload payload, int retryCount) {
        String sessionId = payload.sessionId();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_VOICE_SESSION_ID, sessionId,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.VOICE_EVALUATE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("语音面试评估任务已重新入队: sessionId={}, retryCount={}", sessionId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            updateEvaluateStatus(sessionId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    private void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        try {
            sessionRepository.findById(Long.parseLong(sessionId)).ifPresent(session -> {
                session.setEvaluateStatus(status);
                session.setEvaluateError(error);
                sessionRepository.save(session);
                log.debug("语音面试评估状态已更新: sessionId={}, status={}", sessionId, status);
            });
        } catch (Exception e) {
            log.error("更新语音面试评估状态失败: sessionId={}, status={}, error={}",
                    sessionId, status, e.getMessage(), e);
        }
    }
}
