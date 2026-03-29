package interview.guide.modules.voiceinterview.service;

import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.service.VoiceInterviewPromptService.RolePrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class DashscopeLlmService implements LlmService {

    private final ChatClient chatClient;
    private final VoiceInterviewPromptService promptService;
    private final ResumeRepository resumeRepository;

    public DashscopeLlmService(ChatClient.Builder chatClientBuilder, VoiceInterviewPromptService promptService, ResumeRepository resumeRepository) {
        this.chatClient = chatClientBuilder.build();
        this.promptService = promptService;
        this.resumeRepository = resumeRepository;
    }

    @Override
    public String chat(String userInput, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        try {
            // Fetch resume text if resumeId is provided
            String resumeText = null;
            if (session.getResumeId() != null) {
                ResumeEntity resume = resumeRepository.findById(session.getResumeId()).orElse(null);
                if (resume != null) {
                    resumeText = resume.getResumeText();
                }
            }

            // Generate system prompt dynamically with resume context
            String systemPrompt = promptService.generateSystemPromptWithContext(session.getRoleType(), resumeText);

            // Build conversation context
            StringBuilder promptBuilder = new StringBuilder();

            // Add conversation history if exists
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                promptBuilder.append("【之前的对话】\n");
                for (String message : conversationHistory) {
                    promptBuilder.append(message).append("\n");
                }
                promptBuilder.append("\n【当前对话】\n");
            }

            // Add current user input
            promptBuilder.append("用户：").append(userInput);

            // Build prompt with ChatClient
            ChatClient.CallResponseSpec response = chatClient.prompt()
                .system(systemPrompt)
                .user(promptBuilder.toString())
                .call();

            String content = response.chatResponse().getResult().getOutput().getText();

            log.info("LLM response generated for session {}: {}", session.getId(),
                     content.substring(0, Math.min(100, content.length())));

            return content;

        } catch (Exception e) {
            log.error("LLM chat error for session {}", session.getId(), e);
            return "抱歉，我刚才发生了一点错误。能再说一遍吗？";
        }
    }

    @Override
    public String chatStream(String userInput, Consumer<String> onToken, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        // MVP: Use synchronous version
        // TODO: Implement streaming in Phase 2 optimization
        return chat(userInput, session, conversationHistory);
    }
}
