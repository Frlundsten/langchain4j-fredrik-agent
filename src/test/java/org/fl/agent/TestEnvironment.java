package org.fl.agent;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM-wide singleton containers for the integration tests, started once and shared by all
 * test classes (Testcontainers' Ryuk removes them when the JVM exits).
 *
 * <p>Ollama mounts the named {@code ollama} volume, the same one docker-compose uses, so the
 * model is downloaded at most once per machine.
 */
final class TestEnvironment {

    static final String MODEL_VOLUME = "ollama";

    static final OllamaContainer OLLAMA = startOllama();
    static final GenericContainer<?> REDIS = startRedis();

    private TestEnvironment() {
    }

    static String ollamaBaseUrl() {
        return OLLAMA.getEndpoint();
    }

    private static OllamaContainer startOllama() {
        @SuppressWarnings("resource")
        OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(AgentConfig.OLLAMA_IMAGE))
                .withCreateContainerCmdModifier(cmd -> {
                    List<Bind> binds = new ArrayList<>();
                    Bind[] existing = cmd.getHostConfig().getBinds();
                    if (existing != null) {
                        binds.addAll(List.of(existing));
                    }
                    // Named volume, not a host path: works the same on Docker Desktop (Windows/macOS) and Linux.
                    binds.add(new Bind(MODEL_VOLUME, new Volume("/root/.ollama")));
                    cmd.getHostConfig().withBinds(binds);
                });
        ollama.start();
        ensureModel(ollama);
        return ollama;
    }

    private static void ensureModel(OllamaContainer ollama) {
        try {
            Container.ExecResult show = ollama.execInContainer("ollama", "show", AgentConfig.MODEL_NAME);
            if (show.getExitCode() == 0) {
                System.out.println("Model " + AgentConfig.MODEL_NAME + " present, skipping pull");
                return;
            }
            System.out.println("Pulling " + AgentConfig.MODEL_NAME + " into volume '" + MODEL_VOLUME + "'...");
            Container.ExecResult pull = ollama.execInContainer("ollama", "pull", AgentConfig.MODEL_NAME);
            if (pull.getExitCode() != 0) {
                throw new IllegalStateException("ollama pull failed: " + pull.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not provision model", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while provisioning model", e);
        }
    }

    private static GenericContainer<?> startRedis() {
        @SuppressWarnings("resource")
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(AgentConfig.REDIS_IMAGE))
                .withCommand("redis-server", "--save", "", "--appendonly", "no")
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
        redis.start();
        return redis;
    }
}
