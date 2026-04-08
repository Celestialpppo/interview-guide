package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.*;
import interview.guide.modules.interviewschedule.repository.InterviewScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewScheduleService {

    private final InterviewScheduleRepository repository;

    @Transactional
    public InterviewScheduleDTO create(CreateInterviewRequest request) {
        InterviewScheduleEntity entity = new InterviewScheduleEntity();
        entity.setCompanyName(request.getCompanyName());
        entity.setPosition(request.getPosition());
        entity.setInterviewTime(request.getInterviewTime());
        entity.setInterviewType(request.getInterviewType());
        entity.setMeetingLink(request.getMeetingLink());
        entity.setRoundNumber(request.getRoundNumber());
        entity.setInterviewer(request.getInterviewer());
        entity.setNotes(request.getNotes());
        entity.setStatus(InterviewStatus.PENDING);

        InterviewScheduleEntity saved = repository.save(entity);
        return toDTO(saved);
    }

    @Transactional
    public InterviewScheduleDTO update(Long id, CreateInterviewRequest request) {
        InterviewScheduleEntity entity = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("面试记录不存在: " + id));

        entity.setCompanyName(request.getCompanyName());
        entity.setPosition(request.getPosition());
        entity.setInterviewTime(request.getInterviewTime());
        entity.setInterviewType(request.getInterviewType());
        entity.setMeetingLink(request.getMeetingLink());
        entity.setRoundNumber(request.getRoundNumber());
        entity.setInterviewer(request.getInterviewer());
        entity.setNotes(request.getNotes());

        InterviewScheduleEntity saved = repository.save(entity);
        return toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public InterviewScheduleDTO updateStatus(Long id, InterviewStatus status) {
        InterviewScheduleEntity entity = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("面试记录不存在: " + id));

        entity.setStatus(status);
        InterviewScheduleEntity saved = repository.save(entity);
        return toDTO(saved);
    }

    public List<InterviewScheduleDTO> getAll(String status, LocalDateTime start, LocalDateTime end) {
        List<InterviewScheduleEntity> entities;

        if (start != null && end != null) {
            entities = repository.findByInterviewTimeBetween(start, end);
        } else if (status != null) {
            entities = repository.findByStatus(InterviewStatus.valueOf(status));
        } else {
            entities = repository.findAll();
        }

        return entities.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public InterviewScheduleDTO getById(Long id) {
        InterviewScheduleEntity entity = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("面试记录不存在: " + id));
        return toDTO(entity);
    }

    private InterviewScheduleDTO toDTO(InterviewScheduleEntity entity) {
        InterviewScheduleDTO dto = new InterviewScheduleDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
