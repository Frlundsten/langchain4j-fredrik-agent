package org.fl.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemoryTest {

    private final AgentTestSupport agent = new AgentTestSupport();

    @Test
    void remembersEarlierTurnInSameConversation() {
        String id = AgentTestSupport.newConversationId();

        agent.assistant.chat(id, "Hi! My name is Fredrik.");
        String answer = agent.assistant.chat(id, "What is my name?");

        assertTrue(answer.contains("Fredrik"), "answer should recall the name: " + answer);
        List<ChatMessage> lastRequest = agent.sentRequests.getLast();
        assertTrue(lastRequest.stream().anyMatch(m -> text(m).contains("My name is Fredrik")),
                "earlier turn must be part of the context sent to the model");
    }

    @Test
    void storesUserMessageAndAnswerInRedisWithoutThinking() {
        String id = AgentTestSupport.newConversationId();

        String answer = agent.assistant.chat(id, "Say hello in one short sentence.");

        List<ChatMessage> stored = agent.store.getMessages(id);
        assertTrue(stored.stream().anyMatch(m -> m instanceof UserMessage u && u.singleText().contains("Say hello")));
        List<AiMessage> aiMessages = stored.stream().filter(AiMessage.class::isInstance).map(AiMessage.class::cast).toList();
        assertFalse(aiMessages.isEmpty(), "AI answer must be stored");
        assertEquals(answer, aiMessages.getLast().text());
        aiMessages.forEach(ai -> assertTrue(ai.thinking() == null || ai.thinking().isBlank(),
                "thinking must not be stored: " + ai.thinking()));
    }

    @Test
    void conversationsAreIsolated() {
        String a = AgentTestSupport.newConversationId();
        String b = AgentTestSupport.newConversationId();

        agent.assistant.chat(a, "Remember the secret word: pineapple.");
        agent.sentRequests.clear();
        agent.assistant.chat(b, "Hello, who are you? Answer in one sentence.");

        assertFalse(agent.sentRequests.isEmpty());
        agent.sentRequests.forEach(request -> assertTrue(
                request.stream().noneMatch(m -> text(m).contains("pineapple")),
                "context for conversation B must not contain messages from A"));
        assertTrue(agent.store.getMessages(b).stream().noneMatch(m -> text(m).contains("pineapple")));
    }

    @Test
    void windowEvictsOldestMessages() {
        String id = AgentTestSupport.newConversationId();
        ChatMemory memory = AgentFactory.memoryProvider(agent.store).get(id);

        for (int i = 1; i <= AgentConfig.MAX_MESSAGES + 5; i++) {
            memory.add(UserMessage.from("message " + i));
        }

        List<ChatMessage> stored = agent.store.getMessages(id);
        assertEquals(AgentConfig.MAX_MESSAGES, stored.size());
        assertEquals("message 6", text(stored.getFirst()), "oldest messages must be evicted first");
        assertEquals("message " + (AgentConfig.MAX_MESSAGES + 5), text(stored.getLast()));
    }

    private static String text(ChatMessage message) {
        return switch (message) {
            case UserMessage u -> u.hasSingleText() ? u.singleText() : u.contents().toString();
            case AiMessage ai -> ai.text() == null ? "" : ai.text();
            case dev.langchain4j.data.message.SystemMessage s -> s.text();
            default -> message.toString();
        };
    }
}
