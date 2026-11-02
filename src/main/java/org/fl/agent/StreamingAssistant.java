package org.fl.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/** Streaming variant of {@link Assistant}: same prompt, memory and tools, reply as a {@link TokenStream}. */
public interface StreamingAssistant {

    @SystemMessage(Assistant.SYSTEM_PROMPT)
    TokenStream chat(@MemoryId String conversationId, @UserMessage String message);
}
