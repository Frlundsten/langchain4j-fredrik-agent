package org.fl.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Builds an agent wired to the test containers, with deterministic-leaning model settings. */
final class AgentTestSupport {

    static final double TEMPERATURE = 0.0;
    static final int SEED = 42;

    final AgentTools tools = new AgentTools();
    final ChatMemoryStore store;
    final Assistant assistant;
    final StreamingAssistant streamingAssistant;

    /** Messages of every request sent to the model, in order. */
    final List<List<ChatMessage>> sentRequests = new CopyOnWriteArrayList<>();

    AgentTestSupport() {
        ChatModelListener recorder = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                sentRequests.add(List.copyOf(context.chatRequest().messages()));
            }
        };
        ChatModel model = AgentFactory.chatModelBuilder(TestEnvironment.ollamaBaseUrl())
                .temperature(TEMPERATURE)
                .seed(SEED)
                .listeners(List.of(recorder))
                .build();
        store = AgentFactory.redisMemoryStore(TestEnvironment.REDIS.getHost(), TestEnvironment.REDIS.getMappedPort(6379));
        assistant = AgentFactory.assistant(model, store, tools);
        StreamingChatModel streamingModel = AgentFactory.streamingChatModelBuilder(TestEnvironment.ollamaBaseUrl())
                .temperature(TEMPERATURE)
                .seed(SEED)
                .build();
        streamingAssistant = AgentFactory.streamingAssistant(streamingModel, store, tools);
    }

    static String newConversationId() {
        return "test-" + UUID.randomUUID();
    }
}
