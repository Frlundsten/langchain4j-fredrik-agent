package org.fl.agent.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes Server-Sent Events ({@code event:} + one-line JSON {@code data:}) and flushes each one
 * so it reaches the client immediately. A client that went away is not an error for the reply:
 * once a write fails, later events are dropped silently.
 */
final class SseWriter implements ReplyStreamer.EventSink {

    private final OutputStream out;
    private boolean broken;

    SseWriter(OutputStream out) {
        this.out = out;
    }

    static String format(String event, Object data) {
        // Jackson writes JSON on one line, so a single data: line is enough.
        return "event: " + event + "\ndata: " + Json.write(data) + "\n\n";
    }

    @Override
    public synchronized void send(String event, Object data) {
        if (broken) {
            return;
        }
        try {
            out.write(format(event, data).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            broken = true;
            System.err.println("Client disconnected while streaming: " + e.getMessage());
        }
    }

    synchronized boolean broken() {
        return broken;
    }
}
