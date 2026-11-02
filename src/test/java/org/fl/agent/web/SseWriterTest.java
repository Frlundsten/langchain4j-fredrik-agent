package org.fl.agent.web;

import org.fl.agent.web.ApiModels.DoneEvent;
import org.fl.agent.web.ApiModels.TokenEvent;
import org.fl.agent.web.ApiModels.ToolCallEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseWriterTest {

    @Test
    void writesEventNameAndOneLineJson() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SseWriter writer = new SseWriter(bytes);

        writer.send("token", new TokenEvent("line one\nline \"two\" ×"));
        writer.send("tool_call", new ToolCallEvent("t1", "calculate", Json.toolArguments("{\"a\":1234,\"operator\":\"*\",\"b\":5678}")));
        writer.send("done", new DoneEvent());

        assertEquals("""
                event: token
                data: {"text":"line one\\nline \\"two\\" ×"}

                event: tool_call
                data: {"id":"t1","name":"calculate","arguments":{"a":1234,"operator":"*","b":5678}}

                event: done
                data: {}

                """, bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void invalidToolArgumentsAreKeptAsRaw() {
        assertEquals("{\"raw\":\"not json\"}", Json.write(Json.toolArguments("not json")));
        assertEquals("{}", Json.write(Json.toolArguments(null)));
    }

    @Test
    void stopsWritingAfterClientDisconnects() {
        int[] writes = {0};
        SseWriter writer = new SseWriter(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                writes[0]++;
                throw new IOException("broken pipe");
            }
        });

        writer.send("token", new TokenEvent("a"));
        writer.send("token", new TokenEvent("b"));

        assertTrue(writer.broken());
        assertEquals(1, writes[0]);
    }
}
