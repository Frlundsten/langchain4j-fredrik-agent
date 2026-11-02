package org.fl.agent.web;

import org.fl.agent.web.ApiModels.ConversationSummary;
import redis.clients.jedis.UnifiedJedis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which conversations exist, for the sidebar. The chat memory store can't list its ids, so the
 * server keeps its own index next to it:
 * <ul>
 *   <li>{@code chat:conversations}: sorted set, member = id, score = last update (epoch millis)</li>
 *   <li>{@code chat:conversation:{id}}: hash with {@code title} and {@code updatedAt}</li>
 * </ul>
 */
public final class ConversationIndex {

    static final String SET_KEY = "chat:conversations";
    static final String HASH_PREFIX = "chat:conversation:";
    static final int MAX_TITLE_LENGTH = 60;

    private final UnifiedJedis redis;

    public ConversationIndex(UnifiedJedis redis) {
        this.redis = redis;
    }

    /** Records activity in a conversation. The title is set from the first message only. */
    public void touch(String id, String userMessage, Instant now) {
        String key = HASH_PREFIX + id;
        redis.hsetnx(key, "title", title(userMessage));
        redis.hset(key, "updatedAt", now.toString());
        redis.zadd(SET_KEY, now.toEpochMilli(), id);
    }

    /** Most recently updated first. */
    public List<ConversationSummary> list() {
        List<ConversationSummary> result = new ArrayList<>();
        for (String id : redis.zrevrange(SET_KEY, 0, -1)) {
            Map<String, String> fields = redis.hgetAll(HASH_PREFIX + id);
            result.add(new ConversationSummary(id,
                    fields.getOrDefault("title", "Untitled"),
                    fields.getOrDefault("updatedAt", "")));
        }
        return result;
    }

    public void delete(String id) {
        redis.zrem(SET_KEY, id);
        redis.del(HASH_PREFIX + id);
    }

    /** First message, whitespace collapsed, cut to {@value #MAX_TITLE_LENGTH} characters. */
    static String title(String userMessage) {
        String oneLine = userMessage == null ? "" : userMessage.strip().replaceAll("\\s+", " ");
        if (oneLine.isEmpty()) {
            return "Untitled";
        }
        if (oneLine.codePointCount(0, oneLine.length()) <= MAX_TITLE_LENGTH) {
            return oneLine;
        }
        int end = oneLine.offsetByCodePoints(0, MAX_TITLE_LENGTH - 1);
        return oneLine.substring(0, end).stripTrailing() + "…";
    }
}
