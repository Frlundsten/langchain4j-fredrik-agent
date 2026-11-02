package org.fl.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Keeps docker-compose.yaml and the Testcontainers image tags on the same versions. */
class ComposeImageTagsTest {

    private static final Pattern IMAGE = Pattern.compile("^\\s*image:\\s*[\"']?([^\"'\\s#]+)", Pattern.MULTILINE);

    @Test
    void composeImagesMatchTestImages() throws IOException {
        List<String> images = composeImages();

        List<String> ollama = images.stream().filter(i -> i.startsWith("ollama/ollama")).toList();
        List<String> redis = images.stream().filter(i -> i.startsWith("redis")).toList();

        assertFalse(ollama.isEmpty(), "no ollama image in docker-compose.yaml");
        assertFalse(redis.isEmpty(), "no redis image in docker-compose.yaml");
        ollama.forEach(i -> assertEquals(AgentConfig.OLLAMA_IMAGE, i, "Ollama image differs from AgentConfig"));
        redis.forEach(i -> assertEquals(AgentConfig.REDIS_IMAGE, i, "Redis image differs from AgentConfig"));
    }

    private static List<String> composeImages() throws IOException {
        Matcher matcher = IMAGE.matcher(Files.readString(Path.of("docker-compose.yaml")));
        return matcher.results().map(r -> r.group(1)).toList();
    }
}
