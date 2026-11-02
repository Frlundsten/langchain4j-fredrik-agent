package org.fl.agent.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** JSON shapes of the chat API contract ({@code chat-ui/API.md}). */
public final class ApiModels {

    private ApiModels() {
    }

    public record ConversationSummary(String id, String title, String updatedAt) {
    }

    // ---- history ----

    public sealed interface Message permits UserMessage, AssistantMessage {
    }

    public record UserMessage(String role, String text) implements Message {
        public UserMessage(String text) {
            this("user", text);
        }
    }

    public record AssistantMessage(String role, List<Part> parts) implements Message {
        public AssistantMessage(List<Part> parts) {
            this("assistant", parts);
        }
    }

    public sealed interface Part permits TextPart, ToolPart {
    }

    public record TextPart(String type, String text) implements Part {
        public TextPart(String text) {
            this("text", text);
        }
    }

    public record ToolPart(String type, String id, String name, JsonNode arguments, String result) implements Part {
        public ToolPart(String id, String name, JsonNode arguments, String result) {
            this("tool", id, name, arguments, result);
        }
    }

    // ---- stream events ----

    public record TokenEvent(String text) {
    }

    public record ToolCallEvent(String id, String name, JsonNode arguments) {
    }

    public record ToolResultEvent(String id, String result, long durationMs) {
    }

    public record ErrorEvent(String message) {
    }

    public record DoneEvent() {
    }

    public record ErrorBody(String error) {
    }
}
