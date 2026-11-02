package org.fl.agent;

import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Scanner;

/** Command-line entry point: {@code mvn -q exec:java [-Dexec.args=<conversation-id>]}. */
public class AgentApp {

    static final String QUIT_COMMAND = "/quit";

    static void main(String[] args) {
        AgentConfig config = AgentConfig.fromEnvironment();
        String conversationId = args.length > 0 && !args[0].isBlank()
                ? args[0]
                : AgentConfig.DEFAULT_CONVERSATION_ID;

        if (!redisReachable(config) || !ollamaReachable(config)) {
            System.exit(1);
        }

        Assistant assistant = AgentFactory.assistant(
                AgentFactory.chatModelBuilder(config.ollamaBaseUrl()).build(),
                AgentFactory.redisMemoryStore(config.redisHost(), config.redisPort()),
                new AgentTools());

        System.out.printf("Conversation '%s' with %s. Type %s to exit.%n",
                conversationId, AgentConfig.MODEL_NAME, QUIT_COMMAND);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            if (QUIT_COMMAND.equalsIgnoreCase(input)) {
                return;
            }
            try {
                System.out.println(assistant.chat(conversationId, input));
            } catch (RuntimeException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    static boolean redisReachable(AgentConfig config) {
        try (Jedis jedis = new Jedis(config.redisHost(), config.redisPort())) {
            jedis.ping();
            return true;
        } catch (RuntimeException e) {
            System.err.printf("Cannot reach Redis at %s:%d (%s). Is 'docker compose up -d' running?%n",
                    config.redisHost(), config.redisPort(), e.getMessage());
            return false;
        }
    }

    static boolean ollamaReachable(AgentConfig config) {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(config.ollamaBaseUrl() + "/api/tags")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.printf("Ollama at %s answered HTTP %d.%n", config.ollamaBaseUrl(), response.statusCode());
                return false;
            }
            if (!response.body().contains("\"" + AgentConfig.MODEL_NAME + "\"")) {
                System.err.printf("Ollama at %s does not have model %s. Run 'docker compose up -d' and wait for ollama-init.%n",
                        config.ollamaBaseUrl(), AgentConfig.MODEL_NAME);
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.printf("Cannot reach Ollama at %s (%s). Is 'docker compose up -d' running?%n",
                    config.ollamaBaseUrl(), e.getMessage());
            return false;
        }
    }
}
