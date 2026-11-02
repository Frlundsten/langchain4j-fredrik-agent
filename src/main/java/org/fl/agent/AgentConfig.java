package org.fl.agent;

/**
 * Runtime configuration for the agent. Connection settings come from environment variables
 * with defaults matching the local {@code docker-compose.yaml}.
 */
public record AgentConfig(String ollamaBaseUrl, String redisHost, int redisPort, int httpPort) {

    /** The only model this agent uses. */
    public static final String MODEL_NAME = "qwen3.5:2b";

    /** Image tags; must match docker-compose.yaml (enforced by ComposeImageTagsTest). */
    public static final String OLLAMA_IMAGE = "ollama/ollama:0.40.13";
    public static final String REDIS_IMAGE = "redis:8.8.4";

    /** Context window requested from Ollama, in tokens. */
    public static final int NUM_CTX = 8192;

    /** Maximum number of messages kept per conversation. */
    public static final int MAX_MESSAGES = 20;

    public static final String DEFAULT_CONVERSATION_ID = "default";

    public static AgentConfig fromEnvironment() {
        return new AgentConfig(
                env("OLLAMA_BASE_URL", "http://localhost:11434"),
                env("REDIS_HOST", "localhost"),
                Integer.parseInt(env("REDIS_PORT", "6379")),
                Integer.parseInt(env("AGENT_HTTP_PORT", "8080")));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
