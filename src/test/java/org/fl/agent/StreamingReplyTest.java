package org.fl.agent;

import org.fl.agent.web.ApiModels.TokenEvent;
import org.fl.agent.web.ApiModels.ToolCallEvent;
import org.fl.agent.web.ApiModels.ToolResultEvent;
import org.fl.agent.web.ReplyStreamer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Streams a real reply and checks the event order the chat API promises. */
class StreamingReplyTest {

    private final AgentTestSupport agent = new AgentTestSupport();

    record Event(String name, Object data) {
    }

    @Test
    void toolCallThenResultThenTokensThenDone() {
        List<Event> events = new CopyOnWriteArrayList<>();
        new ReplyStreamer(agent.streamingAssistant)
                .stream(AgentTestSupport.newConversationId(), "What is 1234 * 5678?",
                        (name, data) -> events.add(new Event(name, data)));

        List<String> names = events.stream().map(Event::name).toList();
        assertEquals("done", names.getLast(), "stream must end with done: " + names);
        assertEquals(1, names.stream().filter(n -> n.equals("done") || n.equals("error")).count(), names.toString());

        int callIndex = names.indexOf("tool_call");
        int resultIndex = names.indexOf("tool_result");
        assertTrue(callIndex >= 0, "expected a tool_call: " + names);
        assertTrue(resultIndex > callIndex, "tool_result must follow tool_call: " + names);

        ToolCallEvent call = (ToolCallEvent) events.get(callIndex).data();
        ToolResultEvent result = (ToolResultEvent) events.get(resultIndex).data();
        assertEquals("calculate", call.name());
        assertEquals(call.id(), result.id());
        assertEquals("7006652", result.result());
        assertTrue(result.durationMs() >= 0);

        String answer = events.subList(resultIndex, events.size()).stream()
                .filter(e -> e.data() instanceof TokenEvent)
                .map(e -> ((TokenEvent) e.data()).text())
                .collect(Collectors.joining());
        assertFalse(answer.isBlank(), "expected answer tokens after the tool result");
        assertTrue(answer.replaceAll("[,\\s  '.]", "").contains("7006652"), "answer should contain 7006652: " + answer);
    }
}
