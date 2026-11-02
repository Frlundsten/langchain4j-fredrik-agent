package org.fl.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fl.agent.web.ChatServer;
import org.fl.agent.web.ConversationIndex;
import org.fl.agent.web.ReplyStreamer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chat API over real HTTP, backed by the test containers. */
class ChatServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static ChatServer server;
    private static RedisClient redis;

    @BeforeAll
    static void start() throws Exception {
        AgentTestSupport agent = new AgentTestSupport();
        redis = RedisClient.create(TestEnvironment.REDIS.getHost(), TestEnvironment.REDIS.getMappedPort(6379));
        server = new ChatServer(0, new ReplyStreamer(agent.streamingAssistant), agent.store,
                new ConversationIndex(redis), Clock.systemUTC());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.close();
        redis.close();
    }

    private static URI uri(String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> delete(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    record SseEvent(String name, JsonNode data) {
    }

    private static List<SseEvent> parse(String stream) throws Exception {
        List<SseEvent> events = new ArrayList<>();
        for (String block : stream.split("\n\n")) {
            String name = null;
            String data = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("event: ")) name = line.substring(7);
                if (line.startsWith("data: ")) data = line.substring(6);
            }
            if (name != null) events.add(new SseEvent(name, JSON.readTree(data)));
        }
        return events;
    }

    @Test
    void healthAndUnknownRoutes() throws Exception {
        assertEquals(200, get("/api/health").statusCode());
        assertEquals("ok", JSON.readTree(get("/api/health").body()).get("status").asText());
        assertEquals(404, get("/api/nope").statusCode());
        assertEquals(405, post("/api/conversations", "{}").statusCode());
    }

    @Test
    void unknownConversationHasEmptyHistory() throws Exception {
        HttpResponse<String> response = get("/api/conversations/" + AgentTestSupport.newConversationId() + "/messages");
        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    @Test
    void rejectsBlankMessageAndInvalidId() throws Exception {
        String id = AgentTestSupport.newConversationId();
        assertEquals(400, post("/api/conversations/" + id + "/messages", "{\"text\":\"   \"}").statusCode());
        assertEquals(400, post("/api/conversations/" + id + "/messages", "not json").statusCode());
        assertEquals(400, get("/api/conversations/bad.id!/messages").statusCode());
    }

    @Test
    void toolReplyIsStreamedListedStoredAndDeletable() throws Exception {
        String id = AgentTestSupport.newConversationId();

        HttpResponse<String> response = post("/api/conversations/" + id + "/messages", "{\"text\":\"What is 1234 * 5678?\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));

        List<SseEvent> events = parse(response.body());
        List<String> names = events.stream().map(SseEvent::name).toList();
        assertEquals("done", names.getLast(), names.toString());
        SseEvent call = events.get(names.indexOf("tool_call"));
        SseEvent result = events.get(names.indexOf("tool_result"));
        assertTrue(names.indexOf("tool_result") > names.indexOf("tool_call"));
        assertEquals("calculate", call.data().get("name").asText());
        assertEquals(1234, call.data().get("arguments").get("a").asInt());
        assertEquals(call.data().get("id"), result.data().get("id"));
        assertEquals("7006652", result.data().get("result").asText());

        JsonNode list = JSON.readTree(get("/api/conversations").body());
        assertEquals(id, list.get(0).get("id").asText(), "newest conversation first");
        assertEquals("What is 1234 * 5678?", list.get(0).get("title").asText());

        JsonNode history = JSON.readTree(get("/api/conversations/" + id + "/messages").body());
        assertEquals("user", history.get(0).get("role").asText());
        JsonNode parts = history.get(1).get("parts");
        assertEquals("tool", parts.get(0).get("type").asText());
        assertEquals("7006652", parts.get(0).get("result").asText());
        assertEquals("text", parts.get(parts.size() - 1).get("type").asText());

        assertEquals(204, delete("/api/conversations/" + id).statusCode());
        assertEquals("[]", get("/api/conversations/" + id + "/messages").body());
        JsonNode after = JSON.readTree(get("/api/conversations").body());
        after.forEach(c -> assertTrue(!c.get("id").asText().equals(id)));
    }

    @Test
    void secondMessageWhileStreamingIsRejected() throws Exception {
        String id = AgentTestSupport.newConversationId();
        CompletableFuture<HttpResponse<String>> first = HTTP.sendAsync(HttpRequest.newBuilder(uri("/api/conversations/" + id + "/messages"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Write two sentences about the sea.\"}")).build(),
                HttpResponse.BodyHandlers.ofString());

        // The index entry is written once the first request holds the conversation.
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!get("/api/conversations").body().contains(id)) {
            assertTrue(System.nanoTime() < deadline, "first request never started");
            Thread.sleep(20);
        }
        assertEquals(409, post("/api/conversations/" + id + "/messages", "{\"text\":\"hi\"}").statusCode());

        HttpResponse<String> firstResponse = first.get();
        assertEquals(200, firstResponse.statusCode());
        assertTrue(firstResponse.body().contains("event: done"), firstResponse.body());
    }
}
