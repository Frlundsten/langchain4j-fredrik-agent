package org.fl.agent.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared JSON helpers for the chat API. */
final class Json {

    /** Empty records (e.g. the {@code done} event) serialize as {@code {}}. */
    static final ObjectMapper MAPPER = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private Json() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

    /**
     * Tool arguments as a JSON object. Models occasionally produce invalid JSON; that is kept
     * visible as {@code {"raw": "..."}} instead of failing the request.
     */
    static JsonNode toolArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode node = MAPPER.readTree(arguments);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (JsonProcessingException ignored) {
            // fall through
        }
        ObjectNode raw = MAPPER.createObjectNode();
        raw.put("raw", arguments);
        return raw;
    }
}
