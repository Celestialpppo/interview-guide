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

        // Step 1: Try rule-based parsing
        CreateInterviewRequest result = tryRuleParsing(rawText, source, logBuilder);

        if (result != null && isValidResult(result)) {
            logBuilder.append("规则解析成功\n");
            return new ParseResponse(true, result, 0.95, "rule", logBuilder.toString());
        }

        // Step 2: Rule parsing failed, try AI parsing
        logBuilder.append("规则解析失败，尝试 AI 解析\n");
        result = aiParseService.parseWithAI(rawText);

        if (result != null && isValidResult(result)) {
            logBuilder.append("AI 解析成功\n");
            return new ParseResponse(true, result, 0.8, "ai", logBuilder.toString());
        }

        // Step 3: Both failed
        logBuilder.append("AI 解析也失败\n");
        return new ParseResponse(false, null, 0.0, "ai", logBuilder.toString());
    }

    private CreateInterviewRequest tryRuleParsing(String rawText, String source, StringBuilder logBuilder) {
        // If source specified, try corresponding format first
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

        // No source specified, try all formats
        logBuilder.append("未指定来源，依次尝试所有规则\n");

        // Check for Feishu keywords
        if (rawText.contains("飞书") || rawText.contains("Feishu") || rawText.contains("meeting.feishu.cn")) {
            logBuilder.append("检测到飞书关键词\n");
            CreateInterviewRequest result = ruleParseService.parseFeishu(rawText);
            if (result != null && isValidResult(result)) return result;
        }

        // Check for Tencent Meeting keywords
        if (rawText.contains("腾讯会议") || rawText.contains("Tencent Meeting") || rawText.contains("会议号")) {
            logBuilder.append("检测到腾讯会议关键词\n");
            CreateInterviewRequest result = ruleParseService.parseTencent(rawText);
            if (result != null && isValidResult(result)) return result;
        }

        // Check for Zoom keywords
        if (rawText.contains("Zoom") || rawText.contains("zoom.us")) {
            logBuilder.append("检测到 Zoom 关键词\n");
            CreateInterviewRequest result = ruleParseService.parseZoom(rawText);
            if (result != null && isValidResult(result)) return result;
        }

        // Try all formats
        logBuilder.append("尝试所有格式\n");
        CreateInterviewRequest result = ruleParseService.parseFeishu(rawText);
        if (result != null && isValidResult(result)) return result;

        result = ruleParseService.parseTencent(rawText);
        if (result != null && isValidResult(result)) return result;

        result = ruleParseService.parseZoom(rawText);
        return result;
    }

    private boolean isValidResult(CreateInterviewRequest result) {
        // Must have at least company name, position, and time
        return result != null
            && result.getCompanyName() != null
            && result.getPosition() != null
            && result.getInterviewTime() != null;
    }
}
