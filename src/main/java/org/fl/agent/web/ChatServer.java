package org.fl.agent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.fl.agent.web.ApiModels.ErrorBody;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The chat API ({@code chat-ui/API.md}) on the JDK's built-in HTTP server. */
public final class ChatServer implements AutoCloseable {

    private static final Pattern CONVERSATION_PATH = Pattern.compile("^/api/conversations/([^/]+)(/messages)?/?$");
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    private final HttpServer server;
    private final ReplyStreamer streamer;
    private final ChatMemoryStore memory;
    private final ConversationIndex index;
    private final Clock clock;
    /** Conversations currently streaming a reply. */
    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    public ChatServer(int port, ReplyStreamer streamer, ChatMemoryStore memory, ConversationIndex index, Clock clock)
            throws IOException {
        this.streamer = streamer;
        this.memory = memory;
        this.index = index;
        this.clock = clock;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            route(exchange);
        } catch (Exception e) {
            System.err.println("Request failed: " + e);
            try {
                json(exchange, 500, new ErrorBody("Internal server error"));
            } catch (IOException | RuntimeException ignored) {
                // headers already sent, nothing more we can do
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/api/health") || path.equals("/api/health/")) {
            if (requireMethod(exchange, method, "GET")) {
                json(exchange, 200, Map.of("status", "ok"));
            }
            return;
        }
        if (path.equals("/api/conversations") || path.equals("/api/conversations/")) {
            if (requireMethod(exchange, method, "GET")) {
                json(exchange, 200, index.list());
            }
            return;
        }
        Matcher m = CONVERSATION_PATH.matcher(path);
        if (!m.matches()) {
            json(exchange, 404, new ErrorBody("Not found: " + path));
            return;
        }
        String id = m.group(1);
        if (!VALID_ID.matcher(id).matches()) {
            json(exchange, 400, new ErrorBody("Invalid conversation id"));
            return;
        }
        boolean messages = m.group(2) != null;
        if (!messages) {
            if (requireMethod(exchange, method, "DELETE")) {
                index.delete(id);
                memory.deleteMessages(id);
                exchange.sendResponseHeaders(204, -1);
            }
        } else if (method.equals("GET")) {
            json(exchange, 200, HistoryMapper.toHistory(memory.getMessages(id)));
        } else if (method.equals("POST")) {
            postMessage(exchange, id);
        } else {
            methodNotAllowed(exchange, "GET, POST");
        }
    }

    private void postMessage(HttpExchange exchange, String id) throws IOException {
        String text;
        try {
            JsonNode body = Json.MAPPER.readTree(exchange.getRequestBody());
            JsonNode textNode = body == null ? null : body.get("text");
            text = textNode != null && textNode.isTextual() ? textNode.asText() : null;
        } catch (IOException e) {
            json(exchange, 400, new ErrorBody("Body must be JSON like {\"text\": \"...\"}"));
            return;
        }
        if (text == null || text.isBlank()) {
            json(exchange, 400, new ErrorBody("Message text must not be blank"));
            return;
        }
        if (!busy.add(id)) {
            json(exchange, 409, new ErrorBody("This conversation is still answering the previous message"));
            return;
        }
        try {
            index.touch(id, text, clock.instant().truncatedTo(ChronoUnit.MILLIS));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream body = exchange.getResponseBody();
            streamer.stream(id, text, new SseWriter(body));
        } finally {
            busy.remove(id);
        }
    }

    private static boolean requireMethod(HttpExchange exchange, String method, String allowed) throws IOException {
        if (method.equals(allowed)) {
            return true;
        }
        methodNotAllowed(exchange, allowed);
        return false;
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        json(exchange, 405, new ErrorBody("Method not allowed"));
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
