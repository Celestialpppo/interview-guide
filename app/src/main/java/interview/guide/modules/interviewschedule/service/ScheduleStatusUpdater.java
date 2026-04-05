package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.InterviewScheduleEntity;
import interview.guide.modules.interviewschedule.model.InterviewStatus;
import interview.guide.modules.interviewschedule.repository.InterviewScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleStatusUpdater {

    private final InterviewScheduleRepository repository;

    // Run every hour
    @Scheduled(cron = "0 0 * * * ?")
    public void updateExpiredInterviews() {
        log.info("开始更新过期面试状态");

        List<InterviewScheduleEntity> expired = repository
            .findByStatusAndInterviewTimeBefore(InterviewStatus.PENDING, LocalDateTime.now());

        if (expired.isEmpty()) {
            log.info("没有需要更新的面试记录");
            return;
        }

        for (InterviewScheduleEntity interview : expired) {
            interview.setStatus(InterviewStatus.COMPLETED);
            repository.save(interview);
            log.info("面试状态已更新为已完成: ID={}, 公司={}", interview.getId(), interview.getCompanyName());
        }

        log.info("共更新 {} 条面试记录", expired.size());
    }
}
