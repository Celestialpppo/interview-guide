package interview.guide.modules.voiceinterview.service;

import interview.guide.modules.interview.skill.InterviewSkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VoiceInterviewPromptService {

    private static final String DEFAULT_PROMPT = "你是一位专业的面试官，请根据候选人的回答进行深入提问。";
    private static final String VOICE_RESPONSE_CONSTRAINTS = """
            【语音面试输出约束】
            1. 每轮只问 1 个主问题，必要时最多补 1 个短追问。
            2. 总长度控制在 2-4 句，避免长段落、列表、Markdown、代码块。
            3. 不要重复开场白，不要复述上一轮已问过的完整问题。
            4. 若候选人回答过短，先简短确认（1句）再给出一个具体追问。
            5. 语气简洁直接，适配口语对话。
            """;

    private final InterviewSkillService skillService;

    public VoiceInterviewPromptService(InterviewSkillService skillService) {
        this.skillService = skillService;
    }

    public String generateSystemPromptWithContext(String skillId, String resumeText) {
        String basePrompt = loadPersona(skillId);

        StringBuilder prompt = new StringBuilder(basePrompt)
            .append("\n\n")
            .append(VOICE_RESPONSE_CONSTRAINTS);

        if (resumeText != null && !resumeText.isEmpty()) {
            prompt.append("\n\n【实时语音面试 - 候选人简历内容】\n")
                .append("你已查阅过候选人简历。首轮仅用一句话说明已查阅，并立即进入首个问题。\n\n")
                .append("【简历解析文本】\n")
                .append(resumeText);
        }
        return prompt.toString();
    }

    private String loadPersona(String skillId) {
        try {
            InterviewSkillService.SkillDTO skill = skillService.getSkill(skillId);
            String persona = skill.persona();
            if (persona != null && !persona.isBlank()) {
                log.debug("Loaded persona from template: {}", skillId);
                return persona;
            }
        } catch (Exception e) {
            log.warn("Failed to load persona for skillId: {}, using default prompt", skillId, e);
        }
        return DEFAULT_PROMPT;
    }
}
