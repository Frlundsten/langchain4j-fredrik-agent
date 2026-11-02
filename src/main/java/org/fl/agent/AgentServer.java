package org.fl.agent;

import org.fl.agent.web.ChatServer;
import org.fl.agent.web.ConversationIndex;
import org.fl.agent.web.ReplyStreamer;
import redis.clients.jedis.RedisClient;

import java.io.IOException;
import java.time.Clock;

/**
 * HTTP entry point for the chat UI: {@code mvn -q compile exec:java@server}. Same model, tools and
 * Redis memory as the CLI, so conversations can be continued from either side.
 */
public class AgentServer {

    static void main(String[] args) throws IOException {
        AgentConfig config = AgentConfig.fromEnvironment();
        if (!AgentApp.redisReachable(config) || !AgentApp.ollamaReachable(config)) {
            System.exit(1);
        }

        var memory = AgentFactory.redisMemoryStore(config.redisHost(), config.redisPort());
        StreamingAssistant assistant = AgentFactory.streamingAssistant(
                AgentFactory.streamingChatModelBuilder(config.ollamaBaseUrl()).build(), memory, new AgentTools());
        var index = new ConversationIndex(RedisClient.create(config.redisHost(), config.redisPort()));

        ChatServer server = new ChatServer(config.httpPort(), new ReplyStreamer(assistant), memory, index, Clock.systemUTC());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.printf("Chat API on http://localhost:%d/api with %s. Ctrl+C to stop.%n",
                server.port(), AgentConfig.MODEL_NAME);
    }
}
