package interview.guide.modules.voiceinterview.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 语音面试评估任务生产者
 * Voice interview evaluation task producer
 * <p>
 * Sends evaluation tasks to Redis Stream for async processing.
 * Reuses the same AbstractStreamProducer pattern as text-based interviews.
 * </p>
 */
@Slf4j
@Component
public class VoiceEvaluateStreamProducer extends AbstractStreamProducer<String> {

    private final VoiceInterviewSessionRepository sessionRepository;

    public VoiceEvaluateStreamProducer(RedisService redisService,
                                       VoiceInterviewSessionRepository sessionRepository) {
        super(redisService);
        this.sessionRepository = sessionRepository;
    }

    /**
     * 发送语音面试评估任务到 Redis Stream
     */
    public void sendEvaluateTask(String sessionId) {
        sendTask(sessionId);
    }

    @Override
    protected String taskDisplayName() {
        return "语音面试评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.VOICE_EVALUATE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(String sessionId) {
        return Map.of(
            AsyncTaskStreamConstants.FIELD_VOICE_SESSION_ID, sessionId,
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(String sessionId) {
        return "voiceSessionId=" + sessionId;
    }

    @Override
    protected void onSendFailed(String sessionId, String error) {
        updateEvaluateStatus(sessionId, AsyncTaskStatus.FAILED, truncateError(error));
    }

    private void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        try {
            sessionRepository.findById(Long.parseLong(sessionId)).ifPresent(session -> {
                session.setEvaluateStatus(status);
                if (error != null) {
                    session.setEvaluateError(error.length() > 500 ? error.substring(0, 500) : error);
                }
                sessionRepository.save(session);
            });
        } catch (Exception e) {
            log.error("更新语音面试评估状态失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }
}
