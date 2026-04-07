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

    private final interview.guide.common.ai.LlmProviderRegistry llmProviderRegistry;
    private final VoiceInterviewPromptService promptService;
    private final ResumeRepository resumeRepository;

    public DashscopeLlmService(interview.guide.common.ai.LlmProviderRegistry llmProviderRegistry, VoiceInterviewPromptService promptService, ResumeRepository resumeRepository) {
        this.llmProviderRegistry = llmProviderRegistry;
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

            // Get LLM client from registry
            String provider = session.getLlmProvider();
            log.info("[VoiceInterview] Session {} using LLM provider: {}", session.getId(), provider);
            
            org.springframework.ai.chat.client.ChatClient chatClient = (provider != null && !provider.isBlank())
                ? llmProviderRegistry.getChatClient(provider)
                : llmProviderRegistry.getDefaultChatClient();

            // Build prompt with ChatClient
            org.springframework.ai.chat.client.ChatClient.CallResponseSpec response = chatClient.prompt()
                .system(systemPrompt)
                .user(promptBuilder.toString())
                .call();

            String content = response.chatResponse().getResult().getOutput().getText();

            log.info("LLM response generated for session {}: {}", session.getId(),
                     content.substring(0, Math.min(100, content.length())));

            return content;

        } catch (Exception e) {
            log.error("LLM chat error for session {}: {}", session.getId(), e.getMessage(), e);

            // Return specific error message based on exception type
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("403") || errorMessage.contains("ACCESS_DENIED") ||
                    errorMessage.contains("Authentication")) {
                    return "AI 服务认证失败，请检查 API Key 配置";
                } else if (errorMessage.contains("timeout") || errorMessage.contains("Timeout")) {
                    return "AI 服务响应超时，请稍后重试";
                } else if (errorMessage.contains("429") || errorMessage.contains("rate limit") ||
                           errorMessage.contains("quota")) {
                    return "AI 服务调用频率超限，请稍后重试";
                } else if (errorMessage.contains("connection") || errorMessage.contains("network")) {
                    return "AI 服务网络连接失败，请检查网络";
                }
            }

            return "抱歉，AI 服务暂时不可用，请稍后重试";
        }
    }

    @Override
    public String chatStream(String userInput, Consumer<String> onToken, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        // MVP: Use synchronous version
        // TODO: Implement streaming in Phase 2 optimization
        return chat(userInput, session, conversationHistory);
    }
}
