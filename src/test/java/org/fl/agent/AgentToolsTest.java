package org.fl.agent;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolsTest {

    private final AgentTools tools = new AgentTools();

    @Test
    void basicOperators() {
        assertEquals("5", tools.calculate(2, "+", 3));
        assertEquals("-1", tools.calculate(2, "-", 3));
        assertEquals("7006652", tools.calculate(1234, "*", 5678));
        assertEquals("2.5", tools.calculate(5, "/", 2));
        assertEquals(4, tools.calculatorCalls());
    }

    @Test
    void divisionByZeroReturnsError() {
        assertEquals("Error: division by zero", tools.calculate(1, "/", 0));
    }

    @Test
    void unknownOperatorReturnsError() {
        assertTrue(tools.calculate(2, "^", 3).startsWith("Error: unsupported operator"));
    }

    @Test
    void currentDateTimeIsParseable() {
        assertDoesNotThrow(() -> ZonedDateTime.parse(tools.currentDateTime()));
        assertEquals(1, tools.dateTimeCalls());
    }
}
