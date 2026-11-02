package org.fl.agent;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.memory.chat.redis.StoreType;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.time.Duration;

/** Wires the model, the Redis-backed memory and the tools into an {@link Assistant}. */
public final class AgentFactory {

    private AgentFactory() {
    }

    /**
     * Model builder with the agent's fixed settings. Thinking is on, but thinking text is not
     * returned, so it is never shown to the user nor stored in memory.
     */
    public static OllamaChatModel.OllamaChatModelBuilder chatModelBuilder(String baseUrl) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(AgentConfig.MODEL_NAME)
                .think(true)
                .returnThinking(false)
                .numCtx(AgentConfig.NUM_CTX)
                .timeout(Duration.ofMinutes(5));
    }

    /** Streaming counterpart of {@link #chatModelBuilder(String)}, with identical settings. */
    public static OllamaStreamingChatModel.OllamaStreamingChatModelBuilder streamingChatModelBuilder(String baseUrl) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(AgentConfig.MODEL_NAME)
                .think(true)
                .returnThinking(false)
                .numCtx(AgentConfig.NUM_CTX)
                .timeout(Duration.ofMinutes(5));
    }

    public static ChatMemoryStore redisMemoryStore(String host, int port) {
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .storeType(StoreType.STRING)
                .build();
    }

    public static ChatMemoryProvider memoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(AgentConfig.MAX_MESSAGES)
                .chatMemoryStore(store)
                .build();
    }

    public static Assistant assistant(ChatModel model, ChatMemoryStore store, AgentTools tools) {
        return AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(memoryProvider(store))
                .tools(tools)
                .build();
    }

    public static StreamingAssistant streamingAssistant(StreamingChatModel model, ChatMemoryStore store, AgentTools tools) {
        return AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(memoryProvider(store))
                .tools(tools)
                .build();
    }
}
