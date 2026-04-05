# Interview Schedule System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent interview scheduling system with text parsing, calendar views, and drag-and-drop support.

**Architecture:** React Big Calendar for views, Spring Boot REST API, PostgreSQL storage. Rule-based text parsing with AI fallback using Spring AI structured output.

**Tech Stack:** React Big Calendar, dayjs, Spring Boot, Spring AI, PostgreSQL, Tailwind CSS

---

## File Structure

### Backend Files (New)
```
app/src/main/java/interview/guide/modules/interviewschedule/
├── InterviewScheduleController.java
├── model/
│   ├── InterviewScheduleEntity.java
│   ├── InterviewScheduleDTO.java
│   ├── CreateInterviewRequest.java
│   ├── ParseRequest.java
│   ├── ParseResponse.java
│   └── InterviewStatus.java
├── repository/
│   └── InterviewScheduleRepository.java
└── service/
    ├── InterviewScheduleService.java
    ├── InterviewParseService.java
    ├── RuleParseService.java
    ├── AIParseService.java
    └── ScheduleStatusUpdater.java
```

### Frontend Files (New/Modified)
```
frontend/src/
├── types/interviewSchedule.ts (new)
├── api/interviewSchedule.ts (new)
├── hooks/useInterviewSchedule.ts (new)
├── pages/InterviewSchedulePage.tsx (new)
├── components/interviewschedule/ (new directory)
│   ├── ScheduleHeader.tsx
│   ├── ScheduleCalendar.tsx
│   ├── ScheduleList.tsx
│   ├── InterviewListItem.tsx
│   ├── InterviewEvent.tsx
│   └── InterviewFormModal.tsx
├── components/Layout.tsx (modify - add nav item)
└── App.tsx (modify - add route)
```

---

## Task 1: Backend - Database Entity and Repository

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/model/InterviewStatus.java`
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/model/InterviewScheduleEntity.java`
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/repository/InterviewScheduleRepository.java`

- [ ] **Step 1: Create InterviewStatus enum**

```java
package interview.guide.modules.interviewschedule.model;

public enum InterviewStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    RESCHEDULED
}
```

- [ ] **Step 2: Create InterviewScheduleEntity**

```java
package interview.guide.modules.interviewschedule.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_schedule")
@Data
public class InterviewScheduleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String position;

    @Column(name = "interview_time", nullable = false)
    private LocalDateTime interviewTime;

    @Column(name = "interview_type")
    private String interviewType; // ONSITE, VIDEO, PHONE

    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    @Column(name = "round_number")
    private Integer roundNumber = 1;

    private String interviewer;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status = InterviewStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create InterviewScheduleRepository**

```java
package interview.guide.modules.interviewschedule.repository;

import interview.guide.modules.interviewschedule.model.InterviewScheduleEntity;
import interview.guide.modules.interviewschedule.model.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewScheduleEntity, Long> {
    List<InterviewScheduleEntity> findByStatusAndInterviewTimeBefore(InterviewStatus status, LocalDateTime time);

    List<InterviewScheduleEntity> findByStatus(InterviewStatus status);

    List<InterviewScheduleEntity> findByInterviewTimeBetween(LocalDateTime start, LocalDateTime end);
}
```

- [ ] **Step 4: Verify database table creation**

Start the application and check logs for table creation:
```bash
./gradlew bootRun
```

Expected: Hibernate creates `interview_schedule` table with all columns and indexes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/
git commit -m "feat(interview-schedule): add entity and repository"
```

---

## Task 2: Backend - DTOs and Request Models

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/model/InterviewScheduleDTO.java`
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/model/CreateInterviewRequest.java`
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/model/ParseRequest.java`
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/model/ParseResponse.java`

- [ ] **Step 1: Create InterviewScheduleDTO**

```java
package interview.guide.modules.interviewschedule.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InterviewScheduleDTO {
    private Long id;
    private String companyName;
    private String position;
    private LocalDateTime interviewTime;
    private String interviewType;
    private String meetingLink;
    private Integer roundNumber;
    private String interviewer;
    private String notes;
    private InterviewStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create CreateInterviewRequest**

```java
package interview.guide.modules.interviewschedule.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateInterviewRequest {
    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    @NotBlank(message = "岗位不能为空")
    private String position;

    @NotNull(message = "面试时间不能为空")
    private LocalDateTime interviewTime;

    private String interviewType; // ONSITE, VIDEO, PHONE

    private String meetingLink;

    private Integer roundNumber = 1;

    private String interviewer;

    private String notes;
}
```

- [ ] **Step 3: Create ParseRequest**

```java
package interview.guide.modules.interviewschedule.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParseRequest {
    @NotBlank(message = "文本不能为空")
    private String rawText;

    private String source; // feishu, tencent, zoom, other
}
```

- [ ] **Step 4: Create ParseResponse**

```java
package interview.guide.modules.interviewschedule.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParseResponse {
    private Boolean success;
    private CreateInterviewRequest data;
    private Double confidence;
    private String parseMethod; // rule, ai
    private String log;
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/model/
git commit -m "feat(interview-schedule): add DTOs and request models"
```

---

## Task 3: Backend - Rule-Based Text Parsing Service

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/service/RuleParseService.java`

- [ ] **Step 1: Create RuleParseService with Feishu parser**

```java
package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.CreateInterviewRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RuleParseService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER_2 = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    public CreateInterviewRequest parseFeishu(String rawText) {
        log.info("尝试解析飞书格式文本");
        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            // 提取时间
            Pattern timePattern = Pattern.compile("时间[：:](\\d{4}[-/]\\d{2}[-/]\\d{2}\\s+\\d{2}:\\d{2})");
            Matcher timeMatcher = timePattern.matcher(rawText);
            if (timeMatcher.find()) {
                String timeStr = timeMatcher.group(1).replace("/", "-");
                request.setInterviewTime(LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER));
            }

            // 提取会议链接
            Pattern linkPattern = Pattern.compile("https://meeting\\.feishu\\.cn/[^\\s\\n]+");
            Matcher linkMatcher = linkPattern.matcher(rawText);
            if (linkMatcher.find()) {
                request.setMeetingLink(linkMatcher.group(0));
            }

            // 提取公司名称（常见关键词）
            Pattern companyPattern = Pattern.compile("公司[：:]([^\\s\\n]{2,20})");
            Matcher companyMatcher = companyPattern.matcher(rawText);
            if (companyMatcher.find()) {
                request.setCompanyName(companyMatcher.group(1).trim());
            }

            // 提取岗位
            Pattern positionPattern = Pattern.compile("岗位[：:]([^\\s\\n]{2,30})");
            Matcher positionMatcher = positionPattern.matcher(rawText);
            if (positionMatcher.find()) {
                request.setPosition(positionMatcher.group(1).trim());
            }

            // 提取轮次
            Pattern roundPattern = Pattern.compile("第[一二三四五六七八九十\\d]+[轮场]");
            Matcher roundMatcher = roundPattern.matcher(rawText);
            if (roundMatcher.find()) {
                String roundText = roundMatcher.group(0);
                request.setRoundNumber(parseRoundNumber(roundText));
            }

            // 设置面试形式为视频
            request.setInterviewType("VIDEO");

            log.info("飞书格式解析成功: {}", request);
            return request;

        } catch (Exception e) {
            log.error("飞书格式解析失败", e);
            return null;
        }
    }

    public CreateInterviewRequest parseTencent(String rawText) {
        log.info("尝试解析腾讯会议格式文本");
        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            // 提取时间
            Pattern timePattern = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2})\\s+(\\d{2}:\\d{2})");
            Matcher timeMatcher = timePattern.matcher(rawText);
            if (timeMatcher.find()) {
                String timeStr = (timeMatcher.group(1) + " " + timeMatcher.group(2)).replace("/", "-");
                request.setInterviewTime(LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER));
            }

            // 提取会议号
            Pattern meetingIdPattern = Pattern.compile("会议号[：:]?(\\d{9,})");
            Matcher meetingIdMatcher = meetingIdPattern.matcher(rawText);
            if (meetingIdMatcher.find()) {
                request.setMeetingLink("腾讯会议号: " + meetingIdMatcher.group(1));
            }

            // 提取密码
            Pattern passwordPattern = Pattern.compile("密码[：:]?(\\d{4,})");
            Matcher passwordMatcher = passwordPattern.matcher(rawText);
            if (passwordMatcher.find() && request.getMeetingLink() != null) {
                request.setMeetingLink(request.getMeetingLink() + " 密码: " + passwordMatcher.group(1));
            }

            // 提取公司和岗位（同飞书）
            Pattern companyPattern = Pattern.compile("公司[：:]([^\\s\\n]{2,20})");
            Matcher companyMatcher = companyPattern.matcher(rawText);
            if (companyMatcher.find()) {
                request.setCompanyName(companyMatcher.group(1).trim());
            }

            Pattern positionPattern = Pattern.compile("岗位[：:]([^\\s\\n]{2,30})");
            Matcher positionMatcher = positionPattern.matcher(rawText);
            if (positionMatcher.find()) {
                request.setPosition(positionMatcher.group(1).trim());
            }

            request.setInterviewType("VIDEO");

            log.info("腾讯会议格式解析成功: {}", request);
            return request;

        } catch (Exception e) {
            log.error("腾讯会议格式解析失败", e);
            return null;
        }
    }

    public CreateInterviewRequest parseZoom(String rawText) {
        log.info("尝试解析 Zoom 格式文本");
        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            // 提取 Join URL
            Pattern linkPattern = Pattern.compile("https://zoom\\.us/j/[^\\s\\n]+");
            Matcher linkMatcher = linkPattern.matcher(rawText);
            if (linkMatcher.find()) {
                request.setMeetingLink(linkMatcher.group(0));
            }

            // 提取 Meeting ID
            Pattern meetingIdPattern = Pattern.compile("Meeting ID[：:]?\\s*(\\d+[\\s-]\\d+[\\s-]\\d+)");
            Matcher meetingIdMatcher = meetingIdPattern.matcher(rawText);
            if (meetingIdMatcher.find()) {
                if (request.getMeetingLink() == null) {
                    request.setMeetingLink("Zoom Meeting ID: " + meetingIdMatcher.group(1));
                }
            }

            // 提取时间（Zoom 通常使用英文格式）
            Pattern timePattern = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2})");
            Matcher timeMatcher = timePattern.matcher(rawText);
            if (timeMatcher.find()) {
                String dateStr = timeMatcher.group(1).replace("/", "-");
                // 尝试提取时间
                Pattern hourPattern = Pattern.compile("(\\d{1,2}:\\d{2})");
                Matcher hourMatcher = hourPattern.matcher(rawText);
                if (hourMatcher.find()) {
                    request.setInterviewTime(LocalDateTime.parse(dateStr + " " + hourMatcher.group(1), DATE_TIME_FORMATTER));
                }
            }

            // 提取公司和岗位
            Pattern companyPattern = Pattern.compile("公司[：:]([^\\s\\n]{2,20})");
            Matcher companyMatcher = companyPattern.matcher(rawText);
            if (companyMatcher.find()) {
                request.setCompanyName(companyMatcher.group(1).trim());
            }

            Pattern positionPattern = Pattern.compile("岗位[：:]([^\\s\\n]{2,30})");
            Matcher positionMatcher = positionPattern.matcher(rawText);
            if (positionMatcher.find()) {
                request.setPosition(positionMatcher.group(1).trim());
            }

            request.setInterviewType("VIDEO");

            log.info("Zoom 格式解析成功: {}", request);
            return request;

        } catch (Exception e) {
            log.error("Zoom 格式解析失败", e);
            return null;
        }
    }

    private Integer parseRoundNumber(String roundText) {
        if (roundText.contains("一") || roundText.contains("1")) return 1;
        if (roundText.contains("二") || roundText.contains("2")) return 2;
        if (roundText.contains("三") || roundText.contains("3")) return 3;
        if (roundText.contains("四") || roundText.contains("4")) return 4;
        if (roundText.contains("五") || roundText.contains("5")) return 5;
        return 1;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/service/RuleParseService.java
git commit -m "feat(interview-schedule): add rule-based text parsing service"
```

---

## Task 4: Backend - AI-Based Text Parsing Service

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/service/AIParseService.java`

- [ ] **Step 1: Create AIParseService**

```java
package interview.guide.modules.interviewschedule.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interviewschedule.model.CreateInterviewRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIParseService {

    private final ChatClient.Builder chatClientBuilder;
    private final StructuredOutputInvoker structuredOutputInvoker;

    private static final String PARSE_PROMPT = """
        你是一个面试邀约信息提取助手。从以下文本中提取面试信息：
        - 公司名称（companyName）
        - 岗位名称（position）
        - 面试时间（interviewTime，转换为 ISO 8601 格式，例如 2026-04-10T14:00:00）
        - 面试形式（interviewType: ONSITE/VIDEO/PHONE）
        - 会议链接（meetingLink）
        - 第几轮面试（roundNumber，数字）
        - 面试官（interviewer，如有）
        - 其他备注（notes）

        文本：
        {{rawText}}

        请以 JSON 格式返回结果，字段名使用英文。如果某字段无法提取，返回 null。
        """;

    public CreateInterviewRequest parseWithAI(String rawText) {
        log.info("使用 AI 解析文本");

        try {
            String prompt = PARSE_PROMPT.replace("{{rawText}}", rawText);

            ChatClient chatClient = chatClientBuilder
                .defaultSystem("你是面试邀约信息提取助手，返回 JSON 格式数据")
                .build();

            Map<String, Object> result = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(Map.class);

            if (result == null || result.isEmpty()) {
                log.error("AI 解析返回空结果");
                return null;
            }

            CreateInterviewRequest request = new CreateInterviewRequest();

            if (result.get("companyName") != null) {
                request.setCompanyName(result.get("companyName").toString());
            }

            if (result.get("position") != null) {
                request.setPosition(result.get("position").toString());
            }

            if (result.get("interviewTime") != null) {
                String timeStr = result.get("interviewTime").toString();
                request.setInterviewTime(LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }

            if (result.get("interviewType") != null) {
                request.setInterviewType(result.get("interviewType").toString());
            }

            if (result.get("meetingLink") != null) {
                request.setMeetingLink(result.get("meetingLink").toString());
            }

            if (result.get("roundNumber") != null) {
                request.setRoundNumber(Integer.parseInt(result.get("roundNumber").toString()));
            }

            if (result.get("interviewer") != null) {
                request.setInterviewer(result.get("interviewer").toString());
            }

            if (result.get("notes") != null) {
                request.setNotes(result.get("notes").toString());
            }

            log.info("AI 解析成功: {}", request);
            return request;

        } catch (Exception e) {
            log.error("AI 解析失败", e);
            return null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/service/AIParseService.java
git commit -m "feat(interview-schedule): add AI-based text parsing service"
```

---

## Task 5: Backend - Unified Parse Service

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/service/InterviewParseService.java`

- [ ] **Step 1: Create InterviewParseService**

```java
package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.CreateInterviewRequest;
import interview.guide.modules.interviewschedule.model.ParseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewParseService {

    private final RuleParseService ruleParseService;
    private final AIParseService aiParseService;

    public ParseResponse parse(String rawText, String source) {
        log.info("开始解析文本，来源: {}", source);
        StringBuilder logBuilder = new StringBuilder();

        // Step 1: 尝试规则解析
        CreateInterviewRequest result = tryRuleParsing(rawText, source, logBuilder);

        if (result != null && isValidResult(result)) {
            logBuilder.append("规则解析成功\n");
            return new ParseResponse(true, result, 0.95, "rule", logBuilder.toString());
        }

        // Step 2: 规则解析失败，尝试 AI 解析
        logBuilder.append("规则解析失败，尝试 AI 解析\n");
        result = aiParseService.parseWithAI(rawText);

        if (result != null && isValidResult(result)) {
            logBuilder.append("AI 解析成功\n");
            return new ParseResponse(true, result, 0.8, "ai", logBuilder.toString());
        }

        // Step 3: 都失败了
        logBuilder.append("AI 解析也失败\n");
        return new ParseResponse(false, null, 0.0, "ai", logBuilder.toString());
    }

    private CreateInterviewRequest tryRuleParsing(String rawText, String source, StringBuilder logBuilder) {
        // 如果指定了来源，优先尝试对应格式
        if ("feishu".equalsIgnoreCase(source)) {
            logBuilder.append("尝试飞书格式解析\n");
            return ruleParseService.parseFeishu(rawText);
        } else if ("tencent".equalsIgnoreCase(source)) {
            logBuilder.append("尝试腾讯会议格式解析\n");
            return ruleParseService.parseTencent(rawText);
        } else if ("zoom".equalsIgnoreCase(source)) {
            logBuilder.append("尝试 Zoom 格式解析\n");
            return ruleParseService.parseZoom(rawText);
        }

        // 未指定来源，依次尝试所有格式
        logBuilder.append("未指定来源，依次尝试所有规则\n");

        // 检查是否包含飞书关键词
        if (rawText.contains("飞书") || rawText.contains("Feishu") || rawText.contains("meeting.feishu.cn")) {
            logBuilder.append("检测到飞书关键词\n");
            CreateInterviewRequest result = ruleParseService.parseFeishu(rawText);
            if (result != null) return result;
        }

        // 检查是否包含腾讯会议关键词
        if (rawText.contains("腾讯会议") || rawText.contains("Tencent Meeting") || rawText.contains("会议号")) {
            logBuilder.append("检测到腾讯会议关键词\n");
            CreateInterviewRequest result = ruleParseService.parseTencent(rawText);
            if (result != null) return result;
        }

        // 检查是否包含 Zoom 关键词
        if (rawText.contains("Zoom") || rawText.contains("zoom.us")) {
            logBuilder.append("检测到 Zoom 关键词\n");
            CreateInterviewRequest result = ruleParseService.parseZoom(rawText);
            if (result != null) return result;
        }

        // 尝试所有格式
        logBuilder.append("尝试所有格式\n");
        CreateInterviewRequest result = ruleParseService.parseFeishu(rawText);
        if (result != null) return result;

        result = ruleParseService.parseTencent(rawText);
        if (result != null) return result;

        result = ruleParseService.parseZoom(rawText);
        return result;
    }

    private boolean isValidResult(CreateInterviewRequest result) {
        // 至少要有公司名称、岗位和时间
        return result != null
            && result.getCompanyName() != null
            && result.getPosition() != null
            && result.getInterviewTime() != null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/service/InterviewParseService.java
git commit -m "feat(interview-schedule): add unified parse service with rule + AI fallback"
```

---

## Task 6: Backend - Main Service Layer

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/service/InterviewScheduleService.java`

- [ ] **Step 1: Create InterviewScheduleService**

```java
package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.*;
import interview.guide.modules.interviewschedule.repository.InterviewScheduleRepository;
import lombok.RequiredArgsConstructor;
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
        dto.setId(entity.getId());
        dto.setCompanyName(entity.getCompanyName());
        dto.setPosition(entity.getPosition());
        dto.setInterviewTime(entity.getInterviewTime());
        dto.setInterviewType(entity.getInterviewType());
        dto.setMeetingLink(entity.getMeetingLink());
        dto.setRoundNumber(entity.getRoundNumber());
        dto.setInterviewer(entity.getInterviewer());
        dto.setNotes(entity.getNotes());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/service/InterviewScheduleService.java
git commit -m "feat(interview-schedule): add main service layer with CRUD operations"
```

---

## Task 7: Backend - Scheduled Status Updater

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/service/ScheduleStatusUpdater.java`
- Modify: `app/src/main/java/interview/guide/App.java`

- [ ] **Step 1: Create ScheduleStatusUpdater**

```java
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

    // 每小时执行一次
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
```

- [ ] **Step 2: Enable scheduling in App.java**

Add `@EnableScheduling` annotation to `App.java`:

```java
package interview.guide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/service/ScheduleStatusUpdater.java
git add app/src/main/java/interview/guide/App.java
git commit -m "feat(interview-schedule): add scheduled status updater"
```

---

## Task 8: Backend - REST Controller

**Files:**
- Create: `app/src/main/java/interview/guide/modules/interviewschedule/InterviewScheduleController.java`

- [ ] **Step 1: Create InterviewScheduleController**

```java
package interview.guide.modules.interviewschedule;

import interview.guide.common.result.Result;
import interview.guide.modules.interviewschedule.model.*;
import interview.guide.modules.interviewschedule.service.InterviewParseService;
import interview.guide.modules.interviewschedule.service.InterviewScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/interview-schedule")
@RequiredArgsConstructor
public class InterviewScheduleController {

    private final InterviewScheduleService scheduleService;
    private final InterviewParseService parseService;

    @PostMapping("/parse")
    public Result<ParseResponse> parse(@Valid @RequestBody ParseRequest request) {
        ParseResponse response = parseService.parse(request.getRawText(), request.getSource());
        return Result.success(response);
    }

    @PostMapping
    public Result<InterviewScheduleDTO> create(@Valid @RequestBody CreateInterviewRequest request) {
        InterviewScheduleDTO dto = scheduleService.create(request);
        return Result.success(dto);
    }

    @GetMapping("/{id}")
    public Result<InterviewScheduleDTO> getById(@PathVariable Long id) {
        InterviewScheduleDTO dto = scheduleService.getById(id);
        return Result.success(dto);
    }

    @GetMapping
    public Result<List<InterviewScheduleDTO>> getAll(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        List<InterviewScheduleDTO> list = scheduleService.getAll(status, start, end);
        return Result.success(list);
    }

    @PutMapping("/{id}")
    public Result<InterviewScheduleDTO> update(
        @PathVariable Long id,
        @Valid @RequestBody CreateInterviewRequest request
    ) {
        InterviewScheduleDTO dto = scheduleService.update(id, request);
        return Result.success(dto);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<InterviewScheduleDTO> updateStatus(
        @PathVariable Long id,
        @RequestParam InterviewStatus status
    ) {
        InterviewScheduleDTO dto = scheduleService.updateStatus(id, status);
        return Result.success(dto);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/interview/guide/modules/interviewschedule/InterviewScheduleController.java
git commit -m "feat(interview-schedule): add REST controller with all endpoints"
```

---

## Task 9: Frontend - Type Definitions

**Files:**
- Create: `frontend/src/types/interviewSchedule.ts`

- [ ] **Step 1: Create type definitions**

```typescript
// frontend/src/types/interviewSchedule.ts

export type InterviewStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED';

export type InterviewType = 'ONSITE' | 'VIDEO' | 'PHONE';

export interface InterviewSchedule {
  id: number;
  companyName: string;
  position: string;
  interviewTime: string; // ISO 8601
  interviewType: InterviewType;
  meetingLink?: string;
  roundNumber: number;
  interviewer?: string;
  notes?: string;
  status: InterviewStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInterviewRequest {
  companyName: string;
  position: string;
  interviewTime: string;
  interviewType?: InterviewType;
  meetingLink?: string;
  roundNumber?: number;
  interviewer?: string;
  notes?: string;
}

export interface ParseRequest {
  rawText: string;
  source?: 'feishu' | 'tencent' | 'zoom' | 'other';
}

export interface ParseResponse {
  success: boolean;
  data: CreateInterviewRequest | null;
  confidence: number;
  parseMethod: 'rule' | 'ai';
  log: string;
}

export interface InterviewFormData extends CreateInterviewRequest {
  id?: number;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/types/interviewSchedule.ts
git commit -m "feat(interview-schedule): add TypeScript type definitions"
```

---

## Task 10: Frontend - API Client

**Files:**
- Create: `frontend/src/api/interviewSchedule.ts`

- [ ] **Step 1: Create API client**

```typescript
// frontend/src/api/interviewSchedule.ts

import { request } from './request';
import type {
  InterviewSchedule,
  CreateInterviewRequest,
  ParseRequest,
  ParseResponse,
  InterviewStatus
} from '../types/interviewSchedule';

export const interviewScheduleApi = {
  parse: async (rawText: string, source?: string): Promise<ParseResponse> => {
    const payload: ParseRequest = { rawText, source };
    const response = await request.post('/interview-schedule/parse', payload);
    return response.data.data;
  },

  create: async (data: CreateInterviewRequest): Promise<InterviewSchedule> => {
    const response = await request.post('/interview-schedule', data);
    return response.data.data;
  },

  getById: async (id: number): Promise<InterviewSchedule> => {
    const response = await request.get(`/interview-schedule/${id}`);
    return response.data.data;
  },

  getAll: async (params?: {
    status?: string;
    start?: string;
    end?: string;
  }): Promise<InterviewSchedule[]> => {
    const response = await request.get('/interview-schedule', { params });
    return response.data.data;
  },

  update: async (id: number, data: CreateInterviewRequest): Promise<InterviewSchedule> => {
    const response = await request.put(`/interview-schedule/${id}`, data);
    return response.data.data;
  },

  delete: async (id: number): Promise<void> => {
    await request.delete(`/interview-schedule/${id}`);
  },

  updateStatus: async (id: number, status: InterviewStatus): Promise<InterviewSchedule> => {
    const response = await request.patch(`/interview-schedule/${id}/status`, null, {
      params: { status }
    });
    return response.data.data;
  },
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/interviewSchedule.ts
git commit -m "feat(interview-schedule): add API client"
```

---

## Task 11: Frontend - Custom Hook

**Files:**
- Create: `frontend/src/hooks/useInterviewSchedule.ts`

- [ ] **Step 1: Create custom hook**

```typescript
// frontend/src/hooks/useInterviewSchedule.ts

import { useState, useEffect, useCallback } from 'react';
import { interviewScheduleApi } from '../api/interviewSchedule';
import type {
  InterviewSchedule,
  CreateInterviewRequest,
  InterviewStatus
} from '../types/interviewSchedule';

export function useInterviewSchedule() {
  const [interviews, setInterviews] = useState<InterviewSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchInterviews = useCallback(async (params?: {
    status?: string;
    start?: string;
    end?: string;
  }) => {
    setLoading(true);
    setError(null);
    try {
      const data = await interviewScheduleApi.getAll(params);
      setInterviews(data);
    } catch (err: any) {
      setError(err.message || '获取面试列表失败');
      console.error('Failed to fetch interviews:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  const createInterview = async (data: CreateInterviewRequest): Promise<InterviewSchedule> => {
    const newInterview = await interviewScheduleApi.create(data);
    await fetchInterviews();
    return newInterview;
  };

  const updateInterview = async (id: number, data: CreateInterviewRequest): Promise<InterviewSchedule> => {
    const updated = await interviewScheduleApi.update(id, data);
    await fetchInterviews();
    return updated;
  };

  const deleteInterview = async (id: number): Promise<void> => {
    await interviewScheduleApi.delete(id);
    setInterviews(interviews.filter(i => i.id !== id));
  };

  const updateStatus = async (id: number, status: InterviewStatus): Promise<InterviewSchedule> => {
    const updated = await interviewScheduleApi.updateStatus(id, status);
    await fetchInterviews();
    return updated;
  };

  useEffect(() => {
    fetchInterviews();
  }, [fetchInterviews]);

  return {
    interviews,
    loading,
    error,
    fetchInterviews,
    createInterview,
    updateInterview,
    deleteInterview,
    updateStatus,
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/hooks/useInterviewSchedule.ts
git commit -m "feat(interview-schedule): add custom hook for state management"
```

---

## Task 12: Frontend - Install Dependencies

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install required packages**

```bash
cd frontend
pnpm add react-big-calendar dayjs
pnpm add -D @types/react-big-calendar
```

- [ ] **Step 2: Verify installation**

Check `frontend/package.json` contains:
```json
{
  "dependencies": {
    "react-big-calendar": "^1.13.0",
    "dayjs": "^1.11.13"
  },
  "devDependencies": {
    "@types/react-big-calendar": "^1.8.6"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/pnpm-lock.yaml
git commit -m "feat(interview-schedule): add react-big-calendar dependencies"
```

---

## Task 13: Frontend - InterviewEvent Component

**Files:**
- Create: `frontend/src/components/interviewschedule/InterviewEvent.tsx`

- [ ] **Step 1: Create InterviewEvent component**

```typescript
// frontend/src/components/interviewschedule/InterviewEvent.tsx

import React from 'react';
import type { InterviewSchedule } from '../../types/interviewSchedule';

interface InterviewEventProps {
  event: InterviewSchedule;
}

export const InterviewEvent: React.FC<InterviewEventProps> = ({ event }) => {
  const statusColors = {
    PENDING: 'bg-blue-500',
    COMPLETED: 'bg-green-500',
    CANCELLED: 'bg-gray-400',
    RESCHEDULED: 'bg-orange-500',
  };

  return (
    <div className={`p-1 rounded ${statusColors[event.status]} text-white text-xs`}>
      <div className="font-semibold truncate">{event.companyName}</div>
      <div className="truncate">{event.position}</div>
      {event.roundNumber > 1 && (
        <div className="text-xs opacity-90">第{event.roundNumber}轮</div>
      )}
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/interviewschedule/InterviewEvent.tsx
git commit -m "feat(interview-schedule): add InterviewEvent component"
```

---

## Task 14: Frontend - InterviewFormModal Component

**Files:**
- Create: `frontend/src/components/interviewschedule/InterviewFormModal.tsx`

- [ ] **Step 1: Create InterviewFormModal component (Part 1 - Imports and State)**

```typescript
// frontend/src/components/interviewschedule/InterviewFormModal.tsx

import React, { useState } from 'react';
import { X, ChevronRight, ChevronLeft, AlertCircle, CheckCircle } from 'lucide-react';
import type { InterviewFormData, ParseResponse, InterviewType } from '../../types/interviewSchedule';
import { interviewScheduleApi } from '../../api/interviewSchedule';
import dayjs from 'dayjs';

interface InterviewFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: InterviewFormData) => Promise<void>;
  initialData?: InterviewFormData;
  mode: 'create' | 'edit';
}

type Step = 'text' | 'parse-result' | 'form';

export const InterviewFormModal: React.FC<InterviewFormModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  mode,
}) => {
  const [step, setStep] = useState<Step>(mode === 'edit' ? 'form' : 'text');
  const [rawText, setRawText] = useState('');
  const [parseResult, setParseResult] = useState<ParseResponse | null>(null);
  const [parsing, setParsing] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [formData, setFormData] = useState<InterviewFormData>(initialData || {
    companyName: '',
    position: '',
    interviewTime: '',
    interviewType: 'VIDEO' as InterviewType,
    meetingLink: '',
    roundNumber: 1,
    interviewer: '',
    notes: '',
  });

  if (!isOpen) return null;
```

- [ ] **Step 2: Add parse handler**

```typescript
  const handleParse = async () => {
    if (!rawText.trim()) return;

    setParsing(true);
    try {
      const result = await interviewScheduleApi.parse(rawText);
      setParseResult(result);

      if (result.success && result.data) {
        setFormData({
          ...result.data,
          interviewTime: result.data.interviewTime || '',
        });
      }

      setStep('parse-result');
    } catch (error) {
      console.error('Parse failed:', error);
      setParseResult({
        success: false,
        data: null,
        confidence: 0,
        parseMethod: 'ai',
        log: '解析失败，请手动输入',
      });
      setStep('parse-result');
    } finally {
      setParsing(false);
    }
  };
```

- [ ] **Step 3: Add form handlers**

```typescript
  const handleFormChange = (field: keyof InterviewFormData, value: any) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await onSubmit(formData);
      onClose();
    } catch (error) {
      console.error('Submit failed:', error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleReset = () => {
    setStep('text');
    setRawText('');
    setParseResult(null);
    setFormData({
      companyName: '',
      position: '',
      interviewTime: '',
      interviewType: 'VIDEO',
      meetingLink: '',
      roundNumber: 1,
      interviewer: '',
      notes: '',
    });
  };
```

- [ ] **Step 4: Add render methods (Text Input Step)**

```typescript
  const renderTextInput = () => (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">
          粘贴面试邀约文本
        </label>
        <textarea
          value={rawText}
          onChange={(e) => setRawText(e.target.value)}
          placeholder="支持飞书、腾讯会议、Zoom 等格式..."
          className="w-full h-64 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
        />
      </div>

      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
        >
          取消
        </button>
        <button
          type="button"
          onClick={handleParse}
          disabled={!rawText.trim() || parsing}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {parsing ? '解析中...' : '解析'}
        </button>
      </div>
    </div>
  );
```

- [ ] **Step 5: Add render methods (Parse Result Step)**

```typescript
  const renderParseResult = () => (
    <div className="space-y-4">
      {parseResult && (
        <>
          <div className={`p-4 rounded-lg ${parseResult.success ? 'bg-green-50' : 'bg-red-50'}`}>
            <div className="flex items-center gap-2 mb-2">
              {parseResult.success ? (
                <CheckCircle className="text-green-600" />
              ) : (
                <AlertCircle className="text-red-600" />
              )}
              <span className="font-medium">
                {parseResult.success ? '解析成功' : '解析失败'}
              </span>
              {parseResult.success && (
                <span className="text-sm text-gray-500">
                  置信度: {(parseResult.confidence * 100).toFixed(0)}%
                </span>
              )}
            </div>

            {parseResult.success && parseResult.data && (
              <div className="bg-white p-3 rounded space-y-2 text-sm">
                <div><strong>公司：</strong>{parseResult.data.companyName}</div>
                <div><strong>岗位：</strong>{parseResult.data.position}</div>
                <div><strong>时间：</strong>{dayjs(parseResult.data.interviewTime).format('YYYY-MM-DD HH:mm')}</div>
                {parseResult.data.meetingLink && (
                  <div><strong>会议链接：</strong>{parseResult.data.meetingLink}</div>
                )}
              </div>
            )}
          </div>

          <details className="bg-gray-50 p-3 rounded">
            <summary className="cursor-pointer font-medium text-sm">详细日志</summary>
            <pre className="mt-2 text-xs overflow-auto whitespace-pre-wrap">{parseResult.log}</pre>
          </details>
        </>
      )}

      <div className="flex justify-between">
        <button
          type="button"
          onClick={() => setStep('text')}
          className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg flex items-center gap-1"
        >
          <ChevronLeft className="w-4 h-4" />
          重新输入
        </button>
        <button
          type="button"
          onClick={() => setStep('form')}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 flex items-center gap-1"
        >
          {parseResult?.success ? '确认并编辑' : '手动输入'}
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
```

- [ ] **Step 6: Add render methods (Form Step)**

```typescript
  const renderForm = () => (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          公司名称 <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={formData.companyName}
          onChange={(e) => handleFormChange('companyName', e.target.value)}
          required
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          岗位 <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={formData.position}
          onChange={(e) => handleFormChange('position', e.target.value)}
          required
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          面试时间 <span className="text-red-500">*</span>
        </label>
        <input
          type="datetime-local"
          value={formData.interviewTime ? dayjs(formData.interviewTime).format('YYYY-MM-DDTHH:mm') : ''}
          onChange={(e) => handleFormChange('interviewTime', e.target.value)}
          required
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">面试形式</label>
        <select
          value={formData.interviewType}
          onChange={(e) => handleFormChange('interviewType', e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        >
          <option value="VIDEO">视频面试</option>
          <option value="ONSITE">现场面试</option>
          <option value="PHONE">电话面试</option>
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">会议链接</label>
        <input
          type="url"
          value={formData.meetingLink}
          onChange={(e) => handleFormChange('meetingLink', e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">第几轮面试</label>
        <input
          type="number"
          min="1"
          value={formData.roundNumber}
          onChange={(e) => handleFormChange('roundNumber', parseInt(e.target.value))}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">面试官</label>
        <input
          type="text"
          value={formData.interviewer}
          onChange={(e) => handleFormChange('interviewer', e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">备注</label>
        <textarea
          value={formData.notes}
          onChange={(e) => handleFormChange('notes', e.target.value)}
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 resize-none"
        />
      </div>

      <div className="flex justify-between pt-4">
        {mode === 'create' && step !== 'text' && (
          <button
            type="button"
            onClick={handleReset}
            className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
          >
            重置
          </button>
        )}
        <div className="flex gap-2 ml-auto">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
          >
            取消
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </form>
  );
```

- [ ] **Step 7: Add main render**

```typescript
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-xl font-semibold">
            {mode === 'edit' ? '编辑面试' : '添加面试'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4">
          {step === 'text' && renderTextInput()}
          {step === 'parse-result' && renderParseResult()}
          {step === 'form' && renderForm()}
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/interviewschedule/InterviewFormModal.tsx
git commit -m "feat(interview-schedule): add InterviewFormModal with parse and form steps"
```

---

## Task 15: Frontend - ScheduleCalendar Component

**Files:**
- Create: `frontend/src/components/interviewschedule/ScheduleCalendar.tsx`

- [ ] **Step 1: Create ScheduleCalendar component**

```typescript
// frontend/src/components/interviewschedule/ScheduleCalendar.tsx

import React from 'react';
import { Calendar, dayjsLocalizer, View } from 'react-big-calendar';
import dayjs from 'dayjs';
import 'react-big-calendar/lib/css/react-big-calendar.css';
import type { InterviewSchedule } from '../../types/interviewSchedule';
import { InterviewEvent } from './InterviewEvent';

const localizer = dayjsLocalizer(dayjs);

interface ScheduleCalendarProps {
  interviews: InterviewSchedule[];
  onSelectEvent: (interview: InterviewSchedule) => void;
  onEventDrop?: (interview: InterviewSchedule, newStart: Date, newEnd: Date) => void;
  view: View;
  onViewChange: (view: View) => void;
  date: Date;
  onDateChange: (date: Date) => void;
}

export const ScheduleCalendar: React.FC<ScheduleCalendarProps> = ({
  interviews,
  onSelectEvent,
  onEventDrop,
  view,
  onViewChange,
  date,
  onDateChange,
}) => {
  const events = interviews.map(interview => ({
    ...interview,
    title: interview.companyName,
    start: new Date(interview.interviewTime),
    end: new Date(new Date(interview.interviewTime).getTime() + 60 * 60 * 1000), // 默认1小时
  }));

  const eventStyleGetter = (event: any) => {
    const colors = {
      PENDING: { backgroundColor: '#3b82f6', borderColor: '#2563eb' },
      COMPLETED: { backgroundColor: '#10b981', borderColor: '#059669' },
      CANCELLED: { backgroundColor: '#6b7280', borderColor: '#4b5563' },
      RESCHEDULED: { backgroundColor: '#f59e0b', borderColor: '#d97706' },
    };
    return { style: colors[event.status as keyof typeof colors] };
  };

  const handleEventDrop = ({ event, start, end }: any) => {
    if (onEventDrop) {
      onEventDrop(event, start, end);
    }
  };

  const formats = {
    timeGutterFormat: 'HH:mm',
    eventTimeRangeFormat: ({ start, end }: any) =>
      `${dayjs(start).format('HH:mm')} - ${dayjs(end).format('HH:mm')}`,
  };

  return (
    <div className="bg-white rounded-lg shadow">
      <Calendar
        localizer={localizer}
        events={events}
        view={view}
        onView={onViewChange}
        date={date}
        onNavigate={onDateChange}
        startAccessor="start"
        endAccessor="end"
        style={{ height: 600 }}
        eventPropGetter={eventStyleGetter}
        components={{
          event: InterviewEvent as any,
        }}
        formats={formats}
        selectable
        onSelectEvent={onSelectEvent}
        onEventDrop={handleEventDrop}
        resizable={false}
        messages={{
          today: '今天',
          previous: '上一页',
          next: '下一页',
          month: '月',
          week: '周',
          day: '日',
          agenda: '列表',
          date: '日期',
          time: '时间',
          event: '事件',
          noEventsInRange: '在此范围内没有面试',
        }}
      />
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/interviewschedule/ScheduleCalendar.tsx
git commit -m "feat(interview-schedule): add ScheduleCalendar component with drag support"
```

---

## Task 16: Frontend - InterviewListItem Component

**Files:**
- Create: `frontend/src/components/interviewschedule/InterviewListItem.tsx`

- [ ] **Step 1: Create InterviewListItem component**

```typescript
// frontend/src/components/interviewschedule/InterviewListItem.tsx

import React from 'react';
import { Edit2, Trash2, ExternalLink } from 'lucide-react';
import dayjs from 'dayjs';
import type { InterviewSchedule, InterviewStatus } from '../../types/interviewSchedule';

interface InterviewListItemProps {
  interview: InterviewSchedule;
  onEdit: () => void;
  onDelete: () => void;
  onStatusChange: (status: InterviewStatus) => void;
}

const statusLabels: Record<InterviewStatus, string> = {
  PENDING: '待面试',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  RESCHEDULED: '已改期',
};

const statusColors: Record<InterviewStatus, string> = {
  PENDING: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  RESCHEDULED: 'bg-orange-100 text-orange-800',
};

const typeLabels: Record<string, string> = {
  ONSITE: '现场面试',
  VIDEO: '视频面试',
  PHONE: '电话面试',
};

export const InterviewListItem: React.FC<InterviewListItemProps> = ({
  interview,
  onEdit,
  onDelete,
  onStatusChange,
}) => {
  return (
    <div className="bg-white border rounded-lg p-4 hover:shadow-md transition-shadow">
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[interview.status]}`}>
              {statusLabels[interview.status]}
            </span>
            <span className="text-sm text-gray-500">
              {dayjs(interview.interviewTime).format('YYYY-MM-DD HH:mm')}
            </span>
          </div>

          <h3 className="font-semibold text-lg mb-1">{interview.companyName}</h3>
          <p className="text-gray-600 mb-2">{interview.position}</p>

          <div className="flex items-center gap-4 text-sm text-gray-500">
            <span>第 {interview.roundNumber} 轮</span>
            <span>·</span>
            <span>{typeLabels[interview.interviewType] || interview.interviewType}</span>
            {interview.interviewer && (
              <>
                <span>·</span>
                <span>{interview.interviewer}</span>
              </>
            )}
          </div>

          {interview.meetingLink && (
            <a
              href={interview.meetingLink}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-sm text-primary-600 hover:underline mt-2"
            >
              <ExternalLink className="w-3 h-3" />
              进入会议
            </a>
          )}

          {interview.notes && (
            <p className="text-sm text-gray-500 mt-2">{interview.notes}</p>
          )}
        </div>

        <div className="flex gap-2">
          <button
            onClick={onEdit}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded"
            title="编辑"
          >
            <Edit2 className="w-4 h-4" />
          </button>
          <button
            onClick={onDelete}
            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded"
            title="删除"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {interview.status === 'PENDING' && (
        <div className="mt-3 pt-3 border-t flex gap-2">
          <button
            onClick={() => onStatusChange('COMPLETED')}
            className="px-3 py-1 text-sm bg-green-50 text-green-700 hover:bg-green-100 rounded"
          >
            标记为已完成
          </button>
          <button
            onClick={() => onStatusChange('CANCELLED')}
            className="px-3 py-1 text-sm bg-gray-50 text-gray-700 hover:bg-gray-100 rounded"
          >
            取消面试
          </button>
        </div>
      )}
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/interviewschedule/InterviewListItem.tsx
git commit -m "feat(interview-schedule): add InterviewListItem component"
```

---

## Task 17: Frontend - ScheduleList Component

**Files:**
- Create: `frontend/src/components/interviewschedule/ScheduleList.tsx`

- [ ] **Step 1: Create ScheduleList component**

```typescript
// frontend/src/components/interviewschedule/ScheduleList.tsx

import React from 'react';
import type { InterviewSchedule, InterviewStatus } from '../../types/interviewSchedule';
import { InterviewListItem } from './InterviewListItem';

interface ScheduleListProps {
  interviews: InterviewSchedule[];
  onEdit: (interview: InterviewSchedule) => void;
  onDelete: (id: number) => void;
  onStatusChange: (id: number, status: InterviewStatus) => void;
}

export const ScheduleList: React.FC<ScheduleListProps> = ({
  interviews,
  onEdit,
  onDelete,
  onStatusChange,
}) => {
  const sortedInterviews = [...interviews].sort(
    (a, b) => new Date(a.interviewTime).getTime() - new Date(b.interviewTime).getTime()
  );

  if (sortedInterviews.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <p>暂无面试记录</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {sortedInterviews.map(interview => (
        <InterviewListItem
          key={interview.id}
          interview={interview}
          onEdit={() => onEdit(interview)}
          onDelete={() => onDelete(interview.id)}
          onStatusChange={(status) => onStatusChange(interview.id, status)}
        />
      ))}
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/interviewschedule/ScheduleList.tsx
git commit -m "feat(interview-schedule): add ScheduleList component"
```

---

## Task 18: Frontend - ScheduleHeader Component

**Files:**
- Create: `frontend/src/components/interviewschedule/ScheduleHeader.tsx`

- [ ] **Step 1: Create ScheduleHeader component**

```typescript
// frontend/src/components/interviewschedule/ScheduleHeader.tsx

import React from 'react';
import { Plus, ChevronLeft, ChevronRight, Calendar, List, LayoutGrid } from 'lucide-react';
import dayjs from 'dayjs';
import { View } from 'react-big-calendar';

interface ScheduleHeaderProps {
  view: 'week' | 'month' | 'list';
  onViewChange: (view: 'week' | 'month' | 'list') => void;
  date: Date;
  onDateChange: (date: Date) => void;
  onAddClick: () => void;
}

export const ScheduleHeader: React.FC<ScheduleHeaderProps> = ({
  view,
  onViewChange,
  date,
  onDateChange,
  onAddClick,
}) => {
  const handlePrevious = () => {
    const newDate = new Date(date);
    if (view === 'week') {
      newDate.setDate(newDate.getDate() - 7);
    } else if (view === 'month') {
      newDate.setMonth(newDate.getMonth() - 1);
    }
    onDateChange(newDate);
  };

  const handleNext = () => {
    const newDate = new Date(date);
    if (view === 'week') {
      newDate.setDate(newDate.getDate() + 7);
    } else if (view === 'month') {
      newDate.setMonth(newDate.getMonth() + 1);
    }
    onDateChange(newDate);
  };

  const handleToday = () => {
    onDateChange(new Date());
  };

  const getTitle = () => {
    if (view === 'list') {
      return '面试列表';
    }
    return dayjs(date).format(view === 'month' ? 'YYYY年MM月' : 'YYYY年MM月DD日');
  };

  return (
    <div className="bg-white rounded-lg shadow p-4 mb-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h2 className="text-xl font-semibold">{getTitle()}</h2>

          {view !== 'list' && (
            <div className="flex items-center gap-2">
              <button
                onClick={handlePrevious}
                className="p-2 hover:bg-gray-100 rounded"
                title="上一页"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={handleToday}
                className="px-3 py-1 text-sm bg-primary-50 text-primary-700 hover:bg-primary-100 rounded"
              >
                今天
              </button>
              <button
                onClick={handleNext}
                className="p-2 hover:bg-gray-100 rounded"
                title="下一页"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <div className="flex bg-gray-100 rounded-lg p-1">
            <button
              onClick={() => onViewChange('week')}
              className={`px-3 py-1 rounded flex items-center gap-1 ${
                view === 'week' ? 'bg-white shadow text-primary-700' : 'text-gray-600'
              }`}
            >
              <Calendar className="w-4 h-4" />
              周视图
            </button>
            <button
              onClick={() => onViewChange('month')}
              className={`px-3 py-1 rounded flex items-center gap-1 ${
                view === 'month' ? 'bg-white shadow text-primary-700' : 'text-gray-600'
              }`}
            >
              <LayoutGrid className="w-4 h-4" />
              月视图
            </button>
            <button
              onClick={() => onViewChange('list')}
              className={`px-3 py-1 rounded flex items-center gap-1 ${
                view === 'list' ? 'bg-white shadow text-primary-700' : 'text-gray-600'
              }`}
            >
              <List className="w-4 h-4" />
              列表
            </button>
          </div>

          <button
            onClick={onAddClick}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 flex items-center gap-1"
          >
            <Plus className="w-4 h-4" />
            添加面试
          </button>
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/interviewschedule/ScheduleHeader.tsx
git commit -m "feat(interview-schedule): add ScheduleHeader component"
```

---

## Task 19: Frontend - Main Page Component

**Files:**
- Create: `frontend/src/pages/InterviewSchedulePage.tsx`

- [ ] **Step 1: Create InterviewSchedulePage component (Part 1 - Imports and State)**

```typescript
// frontend/src/pages/InterviewSchedulePage.tsx

import React, { useState, useCallback } from 'react';
import { View } from 'react-big-calendar';
import dayjs from 'dayjs';
import { useInterviewSchedule } from '../hooks/useInterviewSchedule';
import { ScheduleHeader } from '../components/interviewschedule/ScheduleHeader';
import { ScheduleCalendar } from '../components/interviewschedule/ScheduleCalendar';
import { ScheduleList } from '../components/interviewschedule/ScheduleList';
import { InterviewFormModal } from '../components/interviewschedule/InterviewFormModal';
import { ConfirmDialog } from '../components/ConfirmDialog';
import type { InterviewSchedule, InterviewFormData, InterviewStatus } from '../types/interviewSchedule';

export const InterviewSchedulePage: React.FC = () => {
  const {
    interviews,
    loading,
    error,
    createInterview,
    updateInterview,
    deleteInterview,
    updateStatus,
  } = useInterviewSchedule();

  const [view, setView] = useState<'week' | 'month' | 'list'>('week');
  const [date, setDate] = useState(new Date());
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'edit'>('create');
  const [selectedInterview, setSelectedInterview] = useState<InterviewSchedule | null>(null);
  const [pendingChanges, setPendingChanges] = useState<Map<number, Date>>(new Map());
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [interviewToDelete, setInterviewToDelete] = useState<number | null>(null);
```

- [ ] **Step 2: Add handlers**

```typescript
  const handleAddClick = useCallback(() => {
    setModalMode('create');
    setSelectedInterview(null);
    setIsModalOpen(true);
  }, []);

  const handleEditClick = useCallback((interview: InterviewSchedule) => {
    setModalMode('edit');
    setSelectedInterview(interview);
    setIsModalOpen(true);
  }, []);

  const handleDeleteClick = useCallback((id: number) => {
    setInterviewToDelete(id);
    setIsDeleteConfirmOpen(true);
  }, []);

  const handleConfirmDelete = useCallback(async () => {
    if (interviewToDelete) {
      await deleteInterview(interviewToDelete);
      setInterviewToDelete(null);
    }
    setIsDeleteConfirmOpen(false);
  }, [interviewToDelete, deleteInterview]);

  const handleStatusChange = useCallback(async (id: number, status: InterviewStatus) => {
    await updateStatus(id, status);
  }, [updateStatus]);

  const handleFormSubmit = useCallback(async (data: InterviewFormData) => {
    if (modalMode === 'create') {
      await createInterview(data);
    } else if (selectedInterview) {
      await updateInterview(selectedInterview.id, data);
    }
    setIsModalOpen(false);
    setSelectedInterview(null);
  }, [modalMode, selectedInterview, createInterview, updateInterview]);

  const handleEventDrop = useCallback((interview: InterviewSchedule, newStart: Date) => {
    setPendingChanges(new Map(pendingChanges).set(interview.id, newStart));
    setIsConfirmOpen(true);
  }, [pendingChanges]);

  const handleConfirmChanges = useCallback(async () => {
    for (const [id, newTime] of pendingChanges) {
      const interview = interviews.find(i => i.id === id);
      if (interview) {
        await updateInterview(id, {
          companyName: interview.companyName,
          position: interview.position,
          interviewTime: dayjs(newTime).format('YYYY-MM-DDTHH:mm:ss'),
          interviewType: interview.interviewType,
          meetingLink: interview.meetingLink,
          roundNumber: interview.roundNumber,
          interviewer: interview.interviewer,
          notes: interview.notes,
        });
      }
    }
    setPendingChanges(new Map());
    setIsConfirmOpen(false);
  }, [pendingChanges, interviews, updateInterview]);

  const handleCancelChanges = useCallback(() => {
    setPendingChanges(new Map());
    setIsConfirmOpen(false);
  }, []);
```

- [ ] **Step 3: Add main render**

```typescript
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 text-red-500">
        <p>{error}</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto p-6">
      <ScheduleHeader
        view={view}
        onViewChange={setView}
        date={date}
        onDateChange={setDate}
        onAddClick={handleAddClick}
      />

      {view === 'list' ? (
        <ScheduleList
          interviews={interviews}
          onEdit={handleEditClick}
          onDelete={handleDeleteClick}
          onStatusChange={handleStatusChange}
        />
      ) : (
        <ScheduleCalendar
          interviews={interviews}
          onSelectEvent={handleEditClick}
          onEventDrop={handleEventDrop}
          view={view as View}
          onViewChange={(v) => setView(v as 'week' | 'month')}
          date={date}
          onDateChange={setDate}
        />
      )}

      <InterviewFormModal
        isOpen={isModalOpen}
        onClose={() => {
          setIsModalOpen(false);
          setSelectedInterview(null);
        }}
        onSubmit={handleFormSubmit}
        initialData={selectedInterview}
        mode={modalMode}
      />

      <ConfirmDialog
        isOpen={isConfirmOpen}
        title="确认调整面试时间"
        message={`您调整了 ${pendingChanges.size} 个面试的时间，确认保存吗？`}
        onConfirm={handleConfirmChanges}
        onCancel={handleCancelChanges}
      />

      <ConfirmDialog
        isOpen={isDeleteConfirmOpen}
        title="确认删除"
        message="确定要删除这个面试吗？此操作无法撤销。"
        onConfirm={handleConfirmDelete}
        onCancel={() => {
          setIsDeleteConfirmOpen(false);
          setInterviewToDelete(null);
        }}
      />
    </div>
  );
};
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/InterviewSchedulePage.tsx
git commit -m "feat(interview-schedule): add main page with calendar and list views"
```

---

## Task 20: Frontend - Update Layout Component

**Files:**
- Modify: `frontend/src/components/Layout.tsx`

- [ ] **Step 1: Add navigation item for interview schedule**

Find the navigation items array in `Layout.tsx` and add the interview schedule entry after the knowledge base item:

```typescript
// In the navigation items array, add:
{
  icon: Calendar,
  label: '面试日程',
  path: '/interview-schedule',
}
```

Make sure to import `Calendar` from `lucide-react` if not already imported.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/Layout.tsx
git commit -m "feat(interview-schedule): add navigation item to sidebar"
```

---

## Task 21: Frontend - Update App Router

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Add route for interview schedule page**

Add lazy import at the top of `App.tsx`:

```typescript
const InterviewSchedulePage = lazy(() => import('./pages/InterviewSchedulePage'));
```

Add route in the Routes section:

```typescript
{/* 面试日程管理 */}
<Route path="interview-schedule" element={<InterviewSchedulePage />} />
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "feat(interview-schedule): add route to app router"
```

---

## Task 22: Testing - Backend API Testing

**Files:**
- Test: Backend endpoints using curl or Postman

- [ ] **Step 1: Test parse endpoint**

```bash
curl -X POST http://localhost:8080/api/interview-schedule/parse \
  -H "Content-Type: application/json" \
  -d '{
    "rawText": "飞书会议邀约\n时间：2026-04-10 14:00\nhttps://meeting.feishu.cn/abc123\n公司：字节跳动\n岗位：前端工程师\n第二轮面试"
  }'
```

Expected: JSON response with success=true and extracted fields.

- [ ] **Step 2: Test create endpoint**

```bash
curl -X POST http://localhost:8080/api/interview-schedule \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "字节跳动",
    "position": "前端工程师",
    "interviewTime": "2026-04-10T14:00:00",
    "interviewType": "VIDEO",
    "meetingLink": "https://meeting.feishu.cn/abc123",
    "roundNumber": 2
  }'
```

Expected: JSON response with id and status="PENDING".

- [ ] **Step 3: Test get all endpoint**

```bash
curl http://localhost:8080/api/interview-schedule
```

Expected: Array of interview records.

- [ ] **Step 4: Test update status endpoint**

```bash
curl -X PATCH "http://localhost:8080/api/interview-schedule/1/status?status=COMPLETED"
```

Expected: Updated interview record with status="COMPLETED".

---

## Task 23: Testing - Frontend Integration Testing

**Files:**
- Test: Frontend application in browser

- [ ] **Step 1: Start frontend development server**

```bash
cd frontend
pnpm dev
```

- [ ] **Step 2: Test navigation**

Navigate to `http://localhost:5173/interview-schedule`

Expected: Interview schedule page loads with calendar view.

- [ ] **Step 3: Test add interview flow**

1. Click "添加面试" button
2. Paste test text in the modal
3. Click "解析"
4. Verify parse result
5. Click "确认并编辑"
6. Edit form fields
7. Click "保存"

Expected: New interview appears in calendar/list.

- [ ] **Step 4: Test calendar view**

1. Verify week view shows by default
2. Switch to month view
3. Navigate dates using previous/next buttons
4. Click on an interview event

Expected: Modal opens with edit form.

- [ ] **Step 5: Test list view**

1. Switch to list view
2. Verify interviews are sorted by time
3. Click "标记为已完成"
4. Click "删除" and confirm

Expected: Status updates and deletes work correctly.

- [ ] **Step 6: Test drag-and-drop (calendar view)**

1. Drag an interview to a different time slot
2. Verify confirmation dialog appears
3. Click "确认"

Expected: Interview time updates successfully.

---

## Task 24: Final Integration and Polish

**Files:**
- All created files

- [ ] **Step 1: Run backend tests (if available)**

```bash
./gradlew test
```

Expected: All tests pass.

- [ ] **Step 2: Build frontend**

```bash
cd frontend
pnpm build
```

Expected: Build completes without errors.

- [ ] **Step 3: Test full flow end-to-end**

Start both backend and frontend, test complete user flow:
1. Navigate to interview schedule
2. Add interview via text parsing
3. View in calendar
4. Edit interview
5. Drag to reschedule
6. Update status
7. Delete interview

Expected: All features work smoothly without errors.

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "feat(interview-schedule): complete interview scheduling system

- Add database entity and repository
- Implement rule-based and AI-based text parsing
- Create REST API endpoints
- Build calendar and list views with React Big Calendar
- Add drag-and-drop support with confirmation
- Implement status management and scheduled updates"
```

---

## Summary

This plan implements a complete interview scheduling system with:

**Backend:**
- PostgreSQL database table
- Rule-based text parsing (Feishu, Tencent Meeting, Zoom)
- AI-based parsing fallback
- REST API with full CRUD operations
- Scheduled status updates

**Frontend:**
- Calendar view (week/month) using React Big Calendar
- List view for detailed overview
- Text parsing modal with 3-step flow
- Drag-and-drop with confirmation
- Status management UI
- Seamless integration with existing UI

**Total: 24 tasks with step-by-step implementation details**
