package org.fl.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallingTest {

    private final AgentTestSupport agent = new AgentTestSupport();

    @Test
    void usesCalculatorForArithmetic() {
        String answer = agent.assistant.chat(AgentTestSupport.newConversationId(), "What is 1234 * 5678?");

        assertTrue(agent.tools.calculatorCalls() > 0, "calculator tool should have been called");
        String digitsOnly = answer.replaceAll("[,\\s 1'.]", "");
        assertTrue(digitsOnly.contains("7006652"), "answer should contain 7006652: " + answer);
    }
}
