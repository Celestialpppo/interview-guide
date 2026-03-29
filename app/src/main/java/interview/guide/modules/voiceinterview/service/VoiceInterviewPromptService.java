package interview.guide.modules.voiceinterview.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class VoiceInterviewPromptService {

    private final Map<String, RolePrompt> rolePrompts = new HashMap<>();

    @PostConstruct
    public void init() throws IOException {
        // Load all role prompts
        rolePrompts.put("ali-p8", loadRolePrompt("prompts/voice-interview/ali-p8-interviewer.st"));
        rolePrompts.put("byteance-algo", loadRolePrompt("prompts/voice-interview/byteance-algo-interviewer.st"));
        rolePrompts.put("tencent-backend", loadRolePrompt("prompts/voice-interview/tencent-backend-interviewer.st"));

        log.info("Loaded {} role prompts for voice interview", rolePrompts.size());
    }

    public RolePrompt getRolePrompt(String roleType) {
        RolePrompt prompt = rolePrompts.get(roleType);
        if (prompt == null) {
            log.warn("Role prompt not found for type: {}, using default", roleType);
            return getDefaultPrompt();
        }
        return prompt;
    }

    public String generateSystemPromptWithContext(String roleType, String resumeText) {
        RolePrompt rolePrompt = getRolePrompt(roleType);
        String basePrompt = rolePrompt.getSystemPrompt();
        
        if (resumeText != null && !resumeText.isEmpty()) {
            return basePrompt + "\n\n【实时语音面试 - 候选人简历内容】\n" +
                   "你已查阅过候选人简历。第一句话请明确告知你已查阅简历，并直接针对简历中的技术栈或项目抛出首个问题。\n\n" +
                   "【简历解析文本】\n" + resumeText;
        }
        return basePrompt;
    }

    private RolePrompt loadRolePrompt(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String content = resource.getContentAsString(StandardCharsets.UTF_8);

        RolePrompt prompt = new RolePrompt();
        prompt.setSystemPrompt(content);
        prompt.setRoleType(extractRoleType(resourcePath));

        return prompt;
    }

    private String extractRoleType(String resourcePath) {
        // Extract role type from path like "prompts/voice-interview/ali-p8-interviewer.st"
        String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        return filename.replace("-interviewer.st", "");
    }

    private RolePrompt getDefaultPrompt() {
        RolePrompt prompt = new RolePrompt();
        prompt.setSystemPrompt("你是一位专业的面试官，请根据候选人的回答进行深入提问。");
        prompt.setRoleType("default");
        return prompt;
    }

    @Data
    public static class RolePrompt {
        private String roleType;
        private String systemPrompt;
    }
}
