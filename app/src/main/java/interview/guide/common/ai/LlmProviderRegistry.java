package interview.guide.common.ai;

import interview.guide.common.config.LlmProviderProperties;
import interview.guide.common.config.LlmProviderProperties.AdvisorConfig;
import interview.guide.common.config.LlmProviderProperties.ProviderConfig;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing and caching LLM providers.
 * Supports dynamic creation of ChatClient based on provider configurations.
 */
@Component
@Slf4j
public class LlmProviderRegistry {

    private final LlmProviderProperties properties;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private final ToolCallback interviewSkillsToolCallback;

    public LlmProviderRegistry(
            LlmProviderProperties properties,
            @Autowired(required = false) ToolCallingManager toolCallingManager,
            @Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) @Qualifier("interviewSkillsToolCallback") ToolCallback interviewSkillsToolCallback) {
        this.properties = properties;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
        this.interviewSkillsToolCallback = interviewSkillsToolCallback;
    }

    /**
     * Get a ChatClient for the specified provider ID.
     * If the client is not in the cache, it will be created based on the provider's configuration.
     *
     * @param providerId The ID of the provider (e.g., "dashscope", "lmstudio")
     * @return A ChatClient instance
     * @throws IllegalArgumentException if the providerId is unknown
     */
    public ChatClient getChatClient(String providerId) {
        log.info("[LlmProviderRegistry] Requesting client for provider: {}", providerId);
        return clientCache.computeIfAbsent(providerId, id -> {
            log.info("[LlmProviderRegistry] Cache miss. Creating new client for: {}", id);
            return createChatClient(id);
        });
    }

    /**
     * Get the default ChatClient based on app.ai.default-provider.
     *
     * @return The default ChatClient instance
     */
    public ChatClient getDefaultChatClient() {
        return getChatClient(properties.getDefaultProvider());
    }

    /**
     * Get a ChatClient for the specified provider, falling back to the default if null or blank.
     */
    public ChatClient getChatClientOrDefault(String providerId) {
        return (providerId != null && !providerId.isBlank())
            ? getChatClient(providerId)
            : getDefaultChatClient();
    }

    private ChatClient createChatClient(String providerId) {
        ProviderConfig config = properties.getProviders().get(providerId);
        if (config == null) {
            log.error("[LlmProviderRegistry] Provider config not found: {}", providerId);
            throw new IllegalArgumentException("Unknown LLM provider: " + providerId);
        }

        log.info("[LlmProviderRegistry] Building client - Provider: {}, BaseUrl: {}, Model: {}", 
                 providerId, config.getBaseUrl(), config.getModel());

        // Setup SimpleClientHttpRequestFactory with long timeouts (5 minutes for local models)
        // This provides better compatibility with local servers like LM Studio
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000); // 10 seconds
        requestFactory.setReadTimeout(300000);   // 5 minutes

        // Create RestClient.Builder with timeout
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        // Create OpenAiApi using builder to ensure compatibility
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .restClientBuilder(restClientBuilder)
                .build();

        // Create OpenAiChatOptions with model name and default temperature
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModel())
                .temperature(0.2)
                .build();
        
        // Instantiate OpenAiChatModel with all required parameters for Spring AI 2.0.0-M4
        OpenAiChatModel chatModel = new OpenAiChatModel(
                openAiApi,
                options,
                toolCallingManager,
                RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP
        );

        log.info("[LlmProviderRegistry] Successfully created ChatClient for {}", providerId);

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (interviewSkillsToolCallback != null) {
            builder.defaultToolCallbacks(interviewSkillsToolCallback);
        }
        List<Advisor> advisors = buildDefaultAdvisors(providerId);
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
            log.info("[LlmProviderRegistry] Applied {} advisors for provider {}", advisors.size(), providerId);
        }

        // Build and return the ChatClient
        return builder.build();
    }

    private List<Advisor> buildDefaultAdvisors(String providerId) {
        AdvisorConfig config = properties.getAdvisors();
        if (config == null || !config.isEnabled()) {
            return List.of();
        }

        List<Advisor> advisors = new ArrayList<>();

        if (config.isToolCallEnabled()) {
            if (toolCallingManager != null) {
                ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .conversationHistoryEnabled(config.isToolCallConversationHistoryEnabled())
                    .streamToolCallResponses(config.isStreamToolCallResponses())
                    .build();
                advisors.add(toolCallAdvisor);
            } else {
                log.warn("[LlmProviderRegistry] ToolCallAdvisor skipped: ToolCallingManager unavailable, provider={}", providerId);
            }
        }

        if (config.isMessageChatMemoryEnabled()) {
            int maxMessages = Math.max(20, config.getMessageChatMemoryMaxMessages());
            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder()
                    .maxMessages(maxMessages)
                    .build()
            ).build();
            advisors.add(memoryAdvisor);
        }

        if (config.isSimpleLoggerEnabled()) {
            advisors.add(new SimpleLoggerAdvisor());
        }

        return advisors;
    }
}
