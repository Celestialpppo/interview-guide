package interview.guide.modules.interviewschedule.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.interviewschedule.model.CreateInterviewRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AIParseService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PARSE_PROMPT = """
        你是一个专业的面试邀约信息提取助手。请仔细分析以下文本，提取面试相关信息。

        **提取规则**：
        1. companyName（公司名称）：提取面试公司的全称或简称，**必需字段**
        2. position（岗位名称）：提取面试岗位的名称，**必需字段**
        3. interviewTime（面试时间）：提取面试开始时间并转换为 ISO 8601 格式，**必需字段**
           - 格式：YYYY-MM-DDTHH:MM:SS（例如：2026-04-10T14:00:00）
           - 若只有相对时间（如"明天下午2点"），根据当前日期 %s 推算
        4. interviewType（面试形式）：ONSITE（现场）/ VIDEO（视频）/ PHONE（电话）
        5. meetingLink（会议链接）：提取完整的会议链接或会议号+密码
        6. roundNumber（第几轮面试）：提取数字（1-10），如"二面"提取为2
        7. notes（其他备注）：包含面试官姓名（如果不重要可忽略）、时长（**默认30分钟**）等。

        **重要提示**：
        - 面试官是谁不重要，只需在 notes 中提及。
        - 优先保证 companyName、position、interviewTime 的准确性。
        - 如果文本中没说时长，默认设置为 30 分钟。

        **待解析文本**：
        %s

        **返回格式**：
        纯 JSON 格式，不要包含```json标记，示例：
        {"companyName":"阿里巴巴","position":"Java工程师","interviewTime":"2026-04-10T14:00:00","interviewType":"VIDEO","meetingLink":"https://meeting.feishu.cn/xxx","roundNumber":2,"interviewer":"张三","notes":"技术面"}
        """;

    public AIParseService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
            .defaultSystem("你是面试邀约信息提取助手，返回纯 JSON 格式数据，不要包含```json标记")
            .build();
    }

    public CreateInterviewRequest parseWithAI(String rawText) {
        log.info("使用 AI 解析文本，文本长度: {}", rawText.length());

        if (rawText == null || rawText.trim().isEmpty()) {
            log.warn("Input text is null or empty");
            return null;
        }

        CreateInterviewRequest request = new CreateInterviewRequest();

        try {
            String currentDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String prompt = String.format(PARSE_PROMPT, currentDate, rawText);

            log.debug("Sending prompt to AI model...");
            String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            if (content == null || content.trim().isEmpty()) {
                log.error("AI 解析返回内容为空");
                return request;
            }

            // 尝试从 Markdown 代码块中提取 JSON
            String jsonContent = content.trim();
            if (jsonContent.contains("```")) {
                Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
                Matcher matcher = pattern.matcher(jsonContent);
                if (matcher.find()) {
                    jsonContent = matcher.group(1).trim();
                }
            }

            log.info("提取到的 JSON 内容: {}", jsonContent);
            Map<String, Object> result = objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});

            if (result == null || result.isEmpty()) {
                log.error("JSON 解析返回空结果");
                return request;
            }

            // 提取并校验字段
            if (result.get("companyName") != null) {
                request.setCompanyName(result.get("companyName").toString().trim());
            }

            if (result.get("position") != null) {
                request.setPosition(result.get("position").toString().trim());
            }

            if (result.get("interviewTime") != null) {
                try {
                    String timeStr = result.get("interviewTime").toString().trim();
                    // 处理 ISO 格式 (YYYY-MM-DDTHH:mm 或 YYYY-MM-DDTHH:mm:ss)
                    if (timeStr.length() == 16) { // YYYY-MM-DDTHH:MM
                        request.setInterviewTime(LocalDateTime.parse(timeStr + ":00"));
                    } else {
                        request.setInterviewTime(LocalDateTime.parse(timeStr));
                    }
                } catch (Exception e) {
                    log.error("AI 返回的时间格式不正确: {}", result.get("interviewTime"));
                }
            }

            if (result.get("interviewType") != null) {
                request.setInterviewType(result.get("interviewType").toString().trim());
            }

            if (result.get("meetingLink") != null) {
                request.setMeetingLink(result.get("meetingLink").toString().trim());
            }

            if (result.get("roundNumber") != null) {
                try {
                    String roundStr = result.get("roundNumber").toString().trim();
                    request.setRoundNumber(Integer.parseInt(roundStr));
                } catch (Exception e) {
                    request.setRoundNumber(1);
                }
            }

            if (result.get("interviewer") != null) {
                request.setInterviewer(result.get("interviewer").toString().trim());
            }

            if (result.get("notes") != null) {
                request.setNotes(result.get("notes").toString().trim());
            }

            log.info("AI 解析成功: {}", request.getCompanyName());
            return request;

        } catch (Exception e) {
            log.error("AI 解析异常: {}", e.getMessage(), e);
            return request;
        }
    }
}
