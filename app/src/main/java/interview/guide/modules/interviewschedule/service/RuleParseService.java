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

    // Feishu patterns
    private static final Pattern TIME_PATTERN_FEISHU = Pattern.compile("(?:时间|时段)[：:]\\s*(\\d{4}[-/]\\d{2}[-/]\\d{2}\\s+\\d{2}:\\d{2})");
    private static final Pattern LINK_PATTERN_FEISHU = Pattern.compile("https://meeting\\.feishu\\.cn/[^\\s\\n]+");
    private static final Pattern COMPANY_PATTERN_FEISHU = Pattern.compile("(?:公司|单位|组织)[：:]\\s*([^\\s\\n]{1,50})");
    private static final Pattern POSITION_PATTERN_FEISHU = Pattern.compile("(?:岗位|职位|职务)[：:]\\s*([^\\s\\n]{1,50})");
    private static final Pattern ROUND_PATTERN_FEISHU = Pattern.compile("第\\s*[一二三四五六七八九十\\d]+\\s*[轮场]");

    // Tencent meeting patterns
    private static final Pattern TIME_PATTERN_TENCENT = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2})\\s+(\\d{2}:\\d{2})");
    private static final Pattern MEETING_ID_PATTERN_TENCENT = Pattern.compile("(?:会议号|ID)[：:]?\\s*(\\d{9,})");
    private static final Pattern PASSWORD_PATTERN_TENCENT = Pattern.compile("密码[：:]?\\s*(\\d{4,})");
    private static final Pattern COMPANY_PATTERN_TENCENT = Pattern.compile("(?:公司|单位)[：:]\\s*([^\\s\\n]{1,50})");
    private static final Pattern POSITION_PATTERN_TENCENT = Pattern.compile("(?:岗位|职位)[：:]\\s*([^\\s\\n]{1,50})");

    // Zoom patterns
    private static final Pattern LINK_PATTERN_ZOOM = Pattern.compile("https://zoom\\.us/j/[^\\s\\n]+");
    private static final Pattern DATE_PATTERN_ZOOM = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2})");
    private static final Pattern HOUR_PATTERN_ZOOM = Pattern.compile("(\\d{1,2}:\\d{2})");
    private static final Pattern COMPANY_PATTERN_ZOOM = Pattern.compile("公司[：:]([^\\s\\n]{2,20})");
    private static final Pattern POSITION_PATTERN_ZOOM = Pattern.compile("岗位[：:]([^\\s\\n]{2,30})");

    // Round number pattern
    private static final Pattern ROUND_NUMBER_PATTERN = Pattern.compile("[一二三四五六七八九十]|\\d");

    public CreateInterviewRequest parseFeishu(String rawText) {
        log.info("尝试解析飞书格式文本");

        // Input validation
        if (rawText == null || rawText.trim().isEmpty()) {
            log.warn("Input text is null or empty");
            return null;
        }

        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            // 提取时间
            Matcher timeMatcher = TIME_PATTERN_FEISHU.matcher(rawText);
            if (timeMatcher.find()) {
                String timeStr = timeMatcher.group(1);
                if (timeStr.contains("/")) {
                    request.setInterviewTime(LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER_2));
                } else {
                    request.setInterviewTime(LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER));
                }
            }

            // 提取会议链接
            Matcher linkMatcher = LINK_PATTERN_FEISHU.matcher(rawText);
            if (linkMatcher.find()) {
                request.setMeetingLink(linkMatcher.group(0));
            }

            // 提取公司名称（常见关键词）
            Matcher companyMatcher = COMPANY_PATTERN_FEISHU.matcher(rawText);
            if (companyMatcher.find()) {
                request.setCompanyName(companyMatcher.group(1).trim());
            }

            // 提取岗位
            Matcher positionMatcher = POSITION_PATTERN_FEISHU.matcher(rawText);
            if (positionMatcher.find()) {
                request.setPosition(positionMatcher.group(1).trim());
            }

            // 提取轮次
            Matcher roundMatcher = ROUND_PATTERN_FEISHU.matcher(rawText);
            if (roundMatcher.find()) {
                String roundText = roundMatcher.group(0);
                request.setRoundNumber(parseRoundNumber(roundText));
            }

            // 设置面试形式为视频
            request.setInterviewType("VIDEO");

        } catch (Exception e) {
            log.error("飞书格式解析失败", e);
        }

        // Validate required fields before returning
        if (request.getInterviewTime() == null) {
            log.error("Missing required field: interviewTime");
            return null;
        }
        if (request.getCompanyName() == null) {
            log.error("Missing required field: companyName");
            return null;
        }
        if (request.getPosition() == null) {
            log.error("Missing required field: position");
            return null;
        }

        log.info("飞书格式解析成功: {}", request);
        return request;
    }

    public CreateInterviewRequest parseTencent(String rawText) {
        log.info("尝试解析腾讯会议格式文本");

        // Input validation
        if (rawText == null || rawText.trim().isEmpty()) {
            log.warn("Input text is null or empty");
            return null;
        }

        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            // 提取时间
            Matcher timeMatcher = TIME_PATTERN_TENCENT.matcher(rawText);
            if (timeMatcher.find()) {
                String timeStr = timeMatcher.group(1) + " " + timeMatcher.group(2);
                if (timeStr.contains("/")) {
                    request.setInterviewTime(LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER_2));
                } else {
                    request.setInterviewTime(LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER));
                }
            }

            // 提取会议号
            Matcher meetingIdMatcher = MEETING_ID_PATTERN_TENCENT.matcher(rawText);
            if (meetingIdMatcher.find()) {
                request.setMeetingLink("腾讯会议号: " + meetingIdMatcher.group(1));
            }

            // 提取密码
            Matcher passwordMatcher = PASSWORD_PATTERN_TENCENT.matcher(rawText);
            if (passwordMatcher.find() && request.getMeetingLink() != null) {
                request.setMeetingLink(request.getMeetingLink() + " 密码: " + passwordMatcher.group(1));
            }

            // 提取公司和岗位（同飞书）
            Matcher companyMatcher = COMPANY_PATTERN_TENCENT.matcher(rawText);
            if (companyMatcher.find()) {
                request.setCompanyName(companyMatcher.group(1).trim());
            }

            Matcher positionMatcher = POSITION_PATTERN_TENCENT.matcher(rawText);
            if (positionMatcher.find()) {
                request.setPosition(positionMatcher.group(1).trim());
            }

            request.setInterviewType("VIDEO");

        } catch (Exception e) {
            log.error("腾讯会议格式解析失败", e);
        }

        // Validate required fields before returning
        if (request.getInterviewTime() == null) {
            log.error("Missing required field: interviewTime");
            return null;
        }
        if (request.getCompanyName() == null) {
            log.error("Missing required field: companyName");
            return null;
        }
        if (request.getPosition() == null) {
            log.error("Missing required field: position");
            return null;
        }

        log.info("腾讯会议格式解析成功: {}", request);
        return request;
    }

    public CreateInterviewRequest parseZoom(String rawText) {
        log.info("尝试解析 Zoom 格式文本");

        // Input validation
        if (rawText == null || rawText.trim().isEmpty()) {
            log.warn("Input text is null or empty");
            return null;
        }

        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            // 提取 Join URL
            Matcher linkMatcher = LINK_PATTERN_ZOOM.matcher(rawText);
            if (linkMatcher.find()) {
                request.setMeetingLink(linkMatcher.group(0));
            }

            // 提取时间（Zoom 通常使用英文格式）
            Matcher timeMatcher = DATE_PATTERN_ZOOM.matcher(rawText);
            if (timeMatcher.find()) {
                String dateStr = timeMatcher.group(1).replace("/", "-");
                // 尝试提取时间
                Matcher hourMatcher = HOUR_PATTERN_ZOOM.matcher(rawText);
                if (hourMatcher.find()) {
                    request.setInterviewTime(LocalDateTime.parse(dateStr + " " + hourMatcher.group(1), DATE_TIME_FORMATTER));
                }
            }

            // 提取公司和岗位
            Matcher companyMatcher = COMPANY_PATTERN_ZOOM.matcher(rawText);
            if (companyMatcher.find()) {
                request.setCompanyName(companyMatcher.group(1).trim());
            }

            Matcher positionMatcher = POSITION_PATTERN_ZOOM.matcher(rawText);
            if (positionMatcher.find()) {
                request.setPosition(positionMatcher.group(1).trim());
            }

            request.setInterviewType("VIDEO");

        } catch (Exception e) {
            log.error("Zoom 格式解析失败", e);
        }

        // Validate required fields before returning
        if (request.getInterviewTime() == null) {
            log.error("Missing required field: interviewTime");
            return null;
        }
        if (request.getCompanyName() == null) {
            log.error("Missing required field: companyName");
            return null;
        }
        if (request.getPosition() == null) {
            log.error("Missing required field: position");
            return null;
        }

        log.info("Zoom 格式解析成功: {}", request);
        return request;
    }

    Integer parseRoundNumber(String roundText) {
        // Extract Chinese numeral or digit
        Matcher matcher = ROUND_NUMBER_PATTERN.matcher(roundText);

        if (matcher.find()) {
            String numeral = matcher.group(0);
            switch (numeral) {
                case "一": case "1": return 1;
                case "二": case "2": return 2;
                case "三": case "3": return 3;
                case "四": case "4": return 4;
                case "五": case "5": return 5;
                case "六": case "6": return 6;
                case "七": case "7": return 7;
                case "八": case "8": return 8;
                case "九": case "9": return 9;
                case "十": case "10": return 10;
                default:
                    log.warn("Unknown numeral format: {}, defaulting to 1", numeral);
                    return 1;
            }
        }
        return 1;
    }
}
