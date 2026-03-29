package interview.guide.modules.voiceinterview.service;

import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;

import java.util.List;
import java.util.function.Consumer;

public interface LlmService {
    /**
     * Chat with LLM (synchronous for MVP)
     * @param userInput Current user input
     * @param session Interview session
     * @param conversationHistory List of previous messages in format ["User: xxx", "AI: xxx"]
     * @return AI response
     */
    String chat(String userInput, VoiceInterviewSessionEntity session, List<String> conversationHistory);

    /**
     * Chat with LLM (streaming, for future optimization)
     */
    String chatStream(String userInput, Consumer<String> onToken, VoiceInterviewSessionEntity session, List<String> conversationHistory);
}
