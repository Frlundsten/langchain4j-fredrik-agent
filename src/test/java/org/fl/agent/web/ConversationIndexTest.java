package org.fl.agent.web;

import org.fl.agent.web.ApiModels.ConversationSummary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.fl.agent.AgentConfig;
import redis.clients.jedis.RedisClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a throwaway Redis container; no model needed. */
class ConversationIndexTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = startRedis();
    private static final RedisClient CLIENT = RedisClient.create(REDIS.getHost(), REDIS.getMappedPort(6379));

    private final ConversationIndex index = new ConversationIndex(CLIENT);

    private static GenericContainer<?> startRedis() {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(AgentConfig.REDIS_IMAGE))
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
        redis.start();
        return redis;
    }

    @BeforeEach
    void flush() {
        CLIENT.flushAll();
    }

    @AfterAll
    static void close() {
        CLIENT.close();
    }

    @Test
    void emptyWhenNothingStored() {
        assertTrue(index.list().isEmpty());
    }

    @Test
    void listsMostRecentFirstAndKeepsFirstTitle() {
        Instant t0 = Instant.parse("2026-11-02T10:00:00Z");
        index.touch("A", "First question in A", t0);
        index.touch("B", "Question in B", t0.plusSeconds(1));
        index.touch("A", "Second question in A", t0.plusSeconds(2));

        List<ConversationSummary> list = index.list();

        assertEquals(List.of("A", "B"), list.stream().map(ConversationSummary::id).toList());
        assertEquals("First question in A", list.getFirst().title());
        assertEquals("2026-11-02T10:00:02Z", list.getFirst().updatedAt());
    }

    @Test
    void deleteRemovesEntry() {
        index.touch("A", "hello", Instant.now());
        index.delete("A");
        assertTrue(index.list().isEmpty());
        assertTrue(CLIENT.hgetAll(ConversationIndex.HASH_PREFIX + "A").isEmpty());
    }

    @Test
    void titleIsOneLineAndAtMostSixtyCharacters() {
        assertEquals("Hello there", ConversationIndex.title("  Hello\n  there  "));
        String title = ConversationIndex.title("x".repeat(100));
        assertEquals(60, title.length());
        assertTrue(title.endsWith("…"));
        assertEquals("Untitled", ConversationIndex.title("   "));
    }
}
