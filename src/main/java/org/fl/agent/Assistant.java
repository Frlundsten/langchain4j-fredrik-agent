package org.fl.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface Assistant {

    /** Shared by {@link Assistant} and {@link StreamingAssistant}. */
    String SYSTEM_PROMPT = """
            You are a helpful, concise assistant.
            You have two tools: one returns the current date and time, the other does basic arithmetic.
            Use the date/time tool whenever the user asks about the current date or time.
            Use the calculator for any arithmetic instead of computing it yourself.
            """;

    @SystemMessage(SYSTEM_PROMPT)
    String chat(@MemoryId String conversationId, @UserMessage String message);
}
