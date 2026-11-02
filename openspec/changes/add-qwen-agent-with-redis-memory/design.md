## Context

The repository is an empty Maven project targeting Java 25 (`pom.xml` has no dependencies). The host is Windows with Docker Desktop. The developer's earlier project `langchain4j-agoncal` already has a working pattern for this setup:
- An Ollama container with model storage on a named `ollama` volume.
- A Redis container used as chat memory through `RedisChatMemoryStore` from `langchain4j-community-redis`.
- A Testcontainers helper (`OllamaUtil`) that mounts the same model storage and pulls only when needed.

This design reuses that pattern and fixes the weak spots noted below. See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**
- One `docker compose up -d` gives a working runtime. A model downloaded once is reused by both Compose and tests.
- Tests are independent of Compose but share its model cache.
- The setup is simple enough to read in one sitting: plain Java and a few classes.

**Non-Goals:**
- Dependency injection frameworks, REST endpoints, streaming output.
- GPU configuration (CPU is enough for a 2B model). It can be added to Compose later.
- A multi-model evaluation harness.

## Decisions

### D1: Plain Java + LangChain4j `AiServices`
An `Assistant` interface with `String chat(@MemoryId String conversationId, @UserMessage String message)`, built with `AiServices.builder(...)` using `.chatModel(...)`, `.chatMemoryProvider(...)` and `.tools(...)`. A `main` class runs the Scanner loop.
*Alternatives:* Spring Boot starter (more setup, REST not needed); the low-level `ChatModel` API plus manual memory, as in the agoncal project (more code, and tools have to be handled by hand).

### D2: Model client configuration
`OllamaChatModel` with:
- `modelName("qwen3.5:2b")`
- `think(true)` and `returnThinking(false)`. The model reasons, but the thinking text never reaches the `AiMessage`, so it is neither printed nor stored in Redis. This satisfies both "thinking hidden" and "thinking not persisted" with no custom filtering.
- `numCtx(8192)`
- `timeout(5 min)`, because the first call after a cold start loads the model
- `temperature`/`seed` left at model defaults in the app and set to `0.0` / fixed in tests

Requires a LangChain4j version whose Ollama module supports `think`/`returnThinking`. Use the latest BOM release at implementation time.
*Alternative:* `returnThinking(true)` and strip thinking before storing. This is more code and easy to get wrong.

### D3: Memory = `RedisChatMemoryStore` + `MessageWindowChatMemory`
`ChatMemoryProvider` → `MessageWindowChatMemory.builder().id(id).maxMessages(20).chatMemoryStore(redisStore)`. The store comes from `langchain4j-community-redis` (proven in the agoncal project) instead of a hand-written one. 20 messages, including tool request/result messages, fit comfortably in 8k context for short turns.
*Alternative:* a custom `ChatMemoryStore` on Jedis. That would only be worth it if we needed TTLs or custom keys, and we don't yet.

### D4: Tools
One `AgentTools` class with `@Tool` methods: `currentDateTime()` (ISO-8601 local date-time and zone) and `calculate(double a, String operator, double b)`. The `operator` is limited to `+ - * /`, and division by zero and unknown operators return an error string instead of throwing. Flat, primitive parameters are deliberate because small models are much more reliable with them. The class keeps a simple invocation counter so tests can assert that a tool was called.

### D5: Compose layout and model volume
```
services:
  ollama       image ollama/ollama:<PINNED>, port 11434, volume ollama:/root/.ollama,
               healthcheck: `ollama list`
  ollama-init  same image, depends_on ollama (healthy), OLLAMA_HOST=ollama:11434,
               entrypoint: ollama pull qwen3.5:2b   (one-shot, restart: "no")
  redis        image redis:<PINNED>, port 6379, command: redis-server --save "" --appendonly no
volumes:
  ollama:
    name: ollama          # fixed name, no compose project prefix → shareable with tests
```
`ollama pull` compares against the local manifest and downloads nothing when the model is already present, so running it on every `up` is fine.
*Changed from agoncal:* that project used `external: true`, which requires a manual `docker volume create`. A fixed `name:` keeps the volume shareable without the manual step.

### D6: Testcontainers share the volume by name, not by host path
The agoncal helper binds the host path `/var/lib/docker/volumes/ollama/_data`. That path is internal to Docker and fragile on Docker Desktop for Windows. Instead, mount the **named volume** through the container create command (docker-java `Bind("ollama", new Volume("/root/.ollama"))` in `withCreateContainerCmdModifier`).
After start, check `ollama show qwen3.5:2b` via `execInContainer` and pull only if it fails. (The agoncal check `test -d .../library/<name:tag>` never matches a `name:tag` path, so it always pulled.)
The Ollama container is a JVM-wide singleton shared by all test classes. This avoids loading the model several times.
*Alternative:* `commitToImage` per model. That duplicates gigabytes into images, and Compose couldn't share them.

### D7: Single Testcontainers line
Use a single Testcontainers 2.x version for core, JUnit integration, and the Ollama module, managed through the Testcontainers BOM. The agoncal project mixed `2.0.3` core with `1.21.4` modules, which risks classpath conflicts. Redis in tests uses `GenericContainer` with the same Redis image tag and flags as Compose.

### D8: Keeping image tags in sync
Integration tests take the Ollama and Redis image tags from one constants class. A small, non-container unit test parses `docker-compose.yaml` and asserts that the tags match those constants. This enforces the "same tag" requirement at build time instead of relying on discipline.

### D9: Configuration
Environment variables with defaults: `OLLAMA_BASE_URL` (`http://localhost:11434`), `REDIS_HOST` (`localhost`), `REDIS_PORT` (`6379`). The conversation id comes from the first CLI argument, defaulting to `default`. At startup the app pings Redis and Ollama and exits non-zero with a clear message if either is unreachable.

## Risks / Trade-offs

- [The `qwen3.5:2b` tag or its size differs from expectation] → Verify on ollama.com before pinning. The model name lives in one constant.
- [The pinned Ollama version is too old for qwen3.5] → Pin a release known to run it. The tag-sync test keeps tests and Compose on the same version.
- [Thinking mode makes CPU responses slow] → Generous timeouts. Tests use short prompts. `think` could become configurable later without changing the specs.
- [A 2B model sometimes skips a tool or misremembers] → Tests assert on contained facts and tool invocation counts with temperature 0 and a fixed seed. Few tools with flat signatures.
- [Testcontainers and Compose share the `ollama` volume concurrently] → Pulls are idempotent. Concurrent reads are safe. Avoid running a first-ever pull from both at the same moment.
- [The first test run on a clean machine downloads the model (multiple GB)] → One-time cost, documented in the README.
- [Testcontainers on Windows needs Docker Desktop running] → Document it. Tests fail fast with a Testcontainers "no Docker environment" error.

## Open Questions

- Exact pinned versions (Ollama image, Redis image, LangChain4j BOM, Testcontainers BOM). Pick the latest compatible ones at implementation time.
