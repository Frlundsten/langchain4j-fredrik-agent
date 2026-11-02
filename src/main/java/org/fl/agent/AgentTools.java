package org.fl.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tools offered to the model. Signatures are kept flat and primitive because small models
 * call such tools far more reliably. Tools never throw: errors are returned as text.
 */
public class AgentTools {

    private final AtomicInteger dateTimeCalls = new AtomicInteger();
    private final AtomicInteger calculatorCalls = new AtomicInteger();

    @Tool("Returns the current local date and time, including the time zone")
    public String currentDateTime() {
        dateTimeCalls.incrementAndGet();
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    @Tool("Calculates 'a operator b' for basic arithmetic")
    public String calculate(@P("first number") double a,
                            @P("one of: + - * /") String operator,
                            @P("second number") double b) {
        calculatorCalls.incrementAndGet();
        String op = operator == null ? "" : operator.trim();
        return switch (op) {
            case "+" -> format(a + b);
            case "-" -> format(a - b);
            case "*", "x", "×" -> format(a * b);
            case "/", "÷" -> b == 0 ? "Error: division by zero" : format(a / b);
            default -> "Error: unsupported operator '" + op + "', use one of + - * /";
        };
    }

    public int dateTimeCalls() {
        return dateTimeCalls.get();
    }

    public int calculatorCalls() {
        return calculatorCalls.get();
    }

    /** Whole numbers without a trailing ".0" so the model repeats them cleanly. */
    private static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
