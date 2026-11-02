package org.fl.agent.web;

import dev.langchain4j.service.TokenStream;
import org.fl.agent.StreamingAssistant;
import org.fl.agent.web.ApiModels.DoneEvent;
import org.fl.agent.web.ApiModels.ErrorEvent;
import org.fl.agent.web.ApiModels.TokenEvent;
import org.fl.agent.web.ApiModels.ToolCallEvent;
import org.fl.agent.web.ApiModels.ToolResultEvent;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns one LangChain4j {@link TokenStream} reply into the contract's stream events
 * ({@code token}, {@code tool_call}, {@code tool_result}, {@code done} / {@code error}).
 */
public final class ReplyStreamer {

    /** Receives events in order. Implementations must not throw. */
    @FunctionalInterface
    public interface EventSink {
        void send(String event, Object data);
    }

    /** Upper bound for one reply; the model client itself times out after 5 minutes. */
    private static final long REPLY_TIMEOUT_MINUTES = 10;

    private final StreamingAssistant assistant;

    public ReplyStreamer(StreamingAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Streams the reply to {@code sink} and blocks until it has ended. Failures never escape:
     * they end the stream with an {@code error} event.
     */
    public void stream(String conversationId, String text, EventSink sink) {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        AtomicBoolean ended = new AtomicBoolean();
        // Ollama doesn't reliably return tool call ids, so we assign our own. Tools run one after
        // another, so "before" and "executed" callbacks pair up in order.
        Queue<PendingCall> pending = new ConcurrentLinkedQueue<>();

        Runnable endOnce = () -> finished.complete(null);
        try {
            TokenStream tokens = assistant.chat(conversationId, text);
            tokens.onPartialResponse(token -> {
                        if (token != null && !token.isEmpty()) {
                            sink.send("token", new TokenEvent(token));
                        }
                    })
                    .beforeToolExecution(before -> {
                        PendingCall call = new PendingCall(newToolId(), System.nanoTime());
                        pending.add(call);
                        sink.send("tool_call", new ToolCallEvent(call.id(), before.request().name(),
                                Json.toolArguments(before.request().arguments())));
                    })
                    .onToolExecuted(execution -> {
                        PendingCall call = pending.poll();
                        if (call == null) { // no "before" callback: announce the call now
                            call = new PendingCall(newToolId(), System.nanoTime());
                            sink.send("tool_call", new ToolCallEvent(call.id(), execution.request().name(),
                                    Json.toolArguments(execution.request().arguments())));
                        }
                        long durationMs = execution.duration() != null
                                ? execution.duration().toMillis()
                                : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - call.startNanos());
                        sink.send("tool_result", new ToolResultEvent(call.id(), execution.result(), durationMs));
                    })
                    .onCompleteResponse(response -> {
                        if (ended.compareAndSet(false, true)) {
                            sink.send("done", new DoneEvent());
                        }
                        endOnce.run();
                    })
                    .onError(error -> {
                        if (ended.compareAndSet(false, true)) {
                            sink.send("error", new ErrorEvent(describe(error)));
                        }
                        endOnce.run();
                    })
                    .start();
            finished.get(REPLY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failOnce(ended, sink, "Interrupted while waiting for the model");
        } catch (TimeoutException e) {
            failOnce(ended, sink, "The model did not finish within " + REPLY_TIMEOUT_MINUTES + " minutes");
        } catch (Exception e) {
            failOnce(ended, sink, describe(e));
        }
    }

    private static void failOnce(AtomicBoolean ended, EventSink sink, String message) {
        if (ended.compareAndSet(false, true)) {
            sink.send("error", new ErrorEvent(message));
        }
    }

    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private static String newToolId() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record PendingCall(String id, long startNanos) {
    }
}
