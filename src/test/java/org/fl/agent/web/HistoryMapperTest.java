package org.fl.agent.web;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryMapperTest {

    private static ToolExecutionRequest request(String name, String args) {
        return ToolExecutionRequest.builder().name(name).arguments(args).build();
    }

    private static String json(List<ChatMessage> stored) {
        return Json.write(HistoryMapper.toHistory(stored));
    }

    @Test
    void emptyConversation() {
        assertTrue(HistoryMapper.toHistory(List.of()).isEmpty());
    }

    @Test
    void textOnlyAndSystemDropped() {
        assertEquals("""
                [{"role":"user","text":"Hi"},{"role":"assistant","parts":[{"type":"text","text":"Hello!"}]}]""",
                json(List.of(SystemMessage.from("sys"), UserMessage.from("Hi"), AiMessage.from("Hello!"))));
    }

    @Test
    void singleToolCallThenAnswer() {
        ToolExecutionRequest calc = request("calculate", "{\"a\":1234,\"operator\":\"*\",\"b\":5678}");
        String result = json(List.of(
                UserMessage.from("What is 1234 * 5678?"),
                AiMessage.from(List.of(calc)),
                ToolExecutionResultMessage.from(calc, "7006652"),
                AiMessage.from("It is 7006652.")));

        assertEquals("""
                [{"role":"user","text":"What is 1234 * 5678?"},\
                {"role":"assistant","parts":[\
                {"type":"tool","id":"h1","name":"calculate","arguments":{"a":1234,"operator":"*","b":5678},"result":"7006652"},\
                {"type":"text","text":"It is 7006652."}]}]""", result);
    }

    @Test
    void twoToolCallsKeepOrderAndIdsAreUniqueAcrossTurns() {
        ToolExecutionRequest time = request("currentDateTime", "{}");
        ToolExecutionRequest calc = request("calculate", "{\"a\":2,\"operator\":\"+\",\"b\":2}");
        String result = json(List.of(
                UserMessage.from("Time and 2+2?"),
                AiMessage.from(List.of(time, calc)),
                ToolExecutionResultMessage.from(time, "2026-11-02T21:00:00+01:00"),
                ToolExecutionResultMessage.from(calc, "4"),
                AiMessage.from("Done."),
                UserMessage.from("Again 2+2?"),
                AiMessage.from(List.of(calc)),
                ToolExecutionResultMessage.from(calc, "4"),
                AiMessage.from("4")));

        assertEquals("""
                [{"role":"user","text":"Time and 2+2?"},\
                {"role":"assistant","parts":[\
                {"type":"tool","id":"h1","name":"currentDateTime","arguments":{},"result":"2026-11-02T21:00:00+01:00"},\
                {"type":"tool","id":"h2","name":"calculate","arguments":{"a":2,"operator":"+","b":2},"result":"4"},\
                {"type":"text","text":"Done."}]},\
                {"role":"user","text":"Again 2+2?"},\
                {"role":"assistant","parts":[\
                {"type":"tool","id":"h3","name":"calculate","arguments":{"a":2,"operator":"+","b":2},"result":"4"},\
                {"type":"text","text":"4"}]}]""", result);
    }
}
