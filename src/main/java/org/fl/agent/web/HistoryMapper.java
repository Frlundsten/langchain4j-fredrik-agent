package org.fl.agent.web;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.fl.agent.web.ApiModels.AssistantMessage;
import org.fl.agent.web.ApiModels.Message;
import org.fl.agent.web.ApiModels.Part;
import org.fl.agent.web.ApiModels.TextPart;
import org.fl.agent.web.ApiModels.ToolPart;
import org.fl.agent.web.ApiModels.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds the LangChain4j messages kept in chat memory into the contract's history shape. One
 * assistant turn (AI messages with tool requests, their results, and the final AI text) becomes
 * a single assistant message whose parts keep the original order. System messages are dropped.
 */
public final class HistoryMapper {

    private HistoryMapper() {
    }

    public static List<Message> toHistory(List<ChatMessage> stored) {
        List<Message> history = new ArrayList<>();
        List<Part> turn = null;
        List<ToolPart> awaitingResult = new ArrayList<>();
        int toolCounter = 0;

        for (ChatMessage message : stored) {
            switch (message) {
                case dev.langchain4j.data.message.UserMessage user -> {
                    flush(history, turn);
                    turn = null;
                    awaitingResult.clear();
                    history.add(new UserMessage(userText(user)));
                }
                case AiMessage ai -> {
                    if (turn == null) {
                        turn = new ArrayList<>();
                    }
                    if (ai.text() != null && !ai.text().isBlank()) {
                        turn.add(new TextPart(ai.text()));
                    }
                    for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                        ToolPart part = new ToolPart("h" + (++toolCounter), request.name(),
                                Json.toolArguments(request.arguments()), null);
                        turn.add(part);
                        awaitingResult.add(part);
                    }
                }
                case ToolExecutionResultMessage result -> {
                    if (turn == null) {
                        turn = new ArrayList<>();
                    }
                    int index = matchingCall(awaitingResult, result);
                    if (index < 0) { // result without a request (window cut it off): show it on its own
                        turn.add(new ToolPart("h" + (++toolCounter), result.toolName(),
                                Json.MAPPER.createObjectNode(), result.text()));
                        continue;
                    }
                    ToolPart call = awaitingResult.remove(index);
                    turn.set(turn.indexOf(call), new ToolPart(call.id(), call.name(), call.arguments(), result.text()));
                }
                default -> {
                    // SystemMessage and anything else is not part of the visible conversation
                }
            }
        }
        flush(history, turn);
        return history;
    }

    /** Pairs by tool name in order; the model may not return ids, so ids aren't relied on. */
    private static int matchingCall(List<ToolPart> awaiting, ToolExecutionResultMessage result) {
        for (int i = 0; i < awaiting.size(); i++) {
            if (result.toolName() == null || result.toolName().equals(awaiting.get(i).name())) {
                return i;
            }
        }
        return awaiting.isEmpty() ? -1 : 0;
    }

    private static void flush(List<Message> history, List<Part> turn) {
        if (turn != null && !turn.isEmpty()) {
            history.add(new AssistantMessage(List.copyOf(turn)));
        }
    }

    private static String userText(dev.langchain4j.data.message.UserMessage user) {
        return user.hasSingleText() ? user.singleText() : user.toString();
    }
}
