package interview.guide.modules.voiceinterview.service;

import interview.guide.modules.interview.skill.InterviewSkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VoiceInterviewPromptService {

    private static final String DEFAULT_PROMPT = "你是一位专业的面试官，请根据候选人的回答进行深入提问。";

    private final InterviewSkillService skillService;

    public VoiceInterviewPromptService(InterviewSkillService skillService) {
        this.skillService = skillService;
    }

    public String generateSystemPromptWithContext(String skillId, String resumeText) {
        String basePrompt = loadPersona(skillId);

        if (resumeText != null && !resumeText.isEmpty()) {
            return basePrompt + "\n\n【实时语音面试 - 候选人简历内容】\n" +
                   "你已查阅过候选人简历。第一句话请明确告知你已查阅简历，并直接针对简历中的技术栈或项目抛出首个问题。\n\n" +
                   "【简历解析文本】\n" + resumeText;
        }
        return basePrompt;
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
