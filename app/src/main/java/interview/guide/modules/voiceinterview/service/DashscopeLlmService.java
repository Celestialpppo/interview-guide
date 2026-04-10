package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class DashscopeLlmService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final VoiceInterviewPromptService promptService;
    private final ResumeRepository resumeRepository;
    private final VoiceInterviewProperties voiceInterviewProperties;

    public DashscopeLlmService(LlmProviderRegistry llmProviderRegistry,
                               VoiceInterviewPromptService promptService,
                               ResumeRepository resumeRepository,
                               VoiceInterviewProperties voiceInterviewProperties) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.promptService = promptService;
        this.resumeRepository = resumeRepository;
        this.voiceInterviewProperties = voiceInterviewProperties;
    }

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
            String systemPrompt = promptService.generateSystemPromptWithContext(session.getSkillId(), resumeText);

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
            
            ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(provider);

            // Build prompt with ChatClient
            ChatClient.CallResponseSpec response = chatClient.prompt()
                .system(systemPrompt)
                .user(promptBuilder.toString())
                .call();

            String content = response.chatResponse().getResult().getOutput().getText();
            String optimized = optimizeForVoice(content);

            log.info("LLM response generated for session {}: {}", session.getId(),
                     optimized.substring(0, Math.min(100, optimized.length())));

            return optimized;

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

    public String chatStream(String userInput, Consumer<String> onToken, VoiceInterviewSessionEntity session, List<String> conversationHistory) {
        // MVP: Use synchronous version
        // TODO: Implement streaming in Phase 2 optimization
        return chat(userInput, session, conversationHistory);
    }

    private String optimizeForVoice(String content) {
        if (content == null || content.isBlank()) {
            return "请继续。";
        }

        String normalized = content
            .replace("**", "")
            .replace("```", "")
            .replace("`", "")
            .replaceAll("(?m)^\\s*[-*+]\\s*", "")
            .replaceAll("\\s+", " ")
            .trim();

        int maxChars = Math.max(80, voiceInterviewProperties.getAiQuestionMaxChars());
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        String truncated = normalized.substring(0, maxChars);
        int lastTerminal = Math.max(
            Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
            Math.max(truncated.lastIndexOf('？'), truncated.lastIndexOf('；'))
        );
        if (lastTerminal >= maxChars / 2) {
            return truncated.substring(0, lastTerminal + 1);
        }

        return truncated + "…";
    }
}
