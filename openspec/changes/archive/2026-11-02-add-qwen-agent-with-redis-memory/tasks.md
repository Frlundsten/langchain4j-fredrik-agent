## 1. Versions and build setup

- [x] 1.1 Confirm the `qwen3.5:2b` Ollama tag and pick a pinned `ollama/ollama` version that runs it, plus a pinned `redis` image tag. Verify by running `ollama run qwen3.5:2b` once in that image version
- [x] 1.2 Add the LangChain4j BOM (version supporting Ollama `think`/`returnThinking`), `langchain4j`, `langchain4j-ollama`, `langchain4j-community-redis`, and an SLF4J binding to `pom.xml`. Verify with `mvn -q compile`
- [x] 1.3 Add the Testcontainers 2.x BOM with core, JUnit Jupiter and Ollama modules, plus JUnit 5 and Surefire, all on one Testcontainers version. Verify with `mvn -q test-compile` and `mvn dependency:tree` showing a single Testcontainers version

## 2. Local runtime (Docker Compose)

- [x] 2.1 Create `docker-compose.yaml` with `ollama` (pinned image, port 11434, `ollama` volume, healthcheck), `ollama-init` (one-shot `ollama pull qwen3.5:2b`), and `redis` (pinned, `--save "" --appendonly no`), plus volume `ollama` with `name: ollama`. Verify that `docker compose up -d` succeeds and `ollama-init` exits 0
- [x] 2.2 Verify model persistence: run `docker compose down` then `up -d`, and confirm `ollama-init` finishes in seconds with no download and `docker exec ollama ollama list` shows `qwen3.5:2b`
- [x] 2.3 Verify Redis has persistence off: `docker exec redis redis-cli CONFIG GET save` returns an empty value and `CONFIG GET appendonly` returns `no`

## 3. Agent core

- [x] 3.1 Create a constants/config class holding the model name, image tags, and env-var defaults (`OLLAMA_BASE_URL`, `REDIS_HOST`, `REDIS_PORT`). Verify it compiles
- [x] 3.2 Implement `AgentTools` with `currentDateTime()` and `calculate(a, operator, b)` and an invocation counter. Verify with plain unit tests for the four operators, division by zero and an unknown operator (error strings, no exceptions)
- [x] 3.3 Implement the model factory (`OllamaChatModel`: qwen3.5:2b, `think(true)`, `returnThinking(false)`, `numCtx(8192)`, timeout) and the memory provider (`MessageWindowChatMemory` with max 20 messages on `RedisChatMemoryStore`). Verify with `mvn -q compile`
- [x] 3.4 Define the `Assistant` interface (`@MemoryId` conversation id, `@SystemMessage` briefly describing the tools) and wire it with `AiServices`. Verify with `mvn -q compile`
- [x] 3.5 Implement `main`: conversation id from args (default `default`), startup ping of Redis and Ollama with a clear error and non-zero exit, a Scanner loop that prints only the answer, and the quit command. Verify manually against Compose: chat, quit, and stop Redis to see the error exit

## 4. Integration tests

- [x] 4.1 Create a test support singleton: an `OllamaContainer` (pinned tag) mounting the named `ollama` volume through the create-command modifier, pulling `qwen3.5:2b` only if `ollama show` fails, plus a Redis `GenericContainer` (pinned tag, same flags). Verify that a second test run logs "model present, skipping pull"
- [x] 4.2 Add the compose/tag sync unit test that parses `docker-compose.yaml` and asserts the image tags equal the constants. Verify it passes, and that it fails when one tag is changed temporarily
- [x] 4.3 Memory tests (temperature 0, fixed seed): name recall within a conversation; isolation between ids `A` and `B` (check Redis contents); window eviction keeps ≤ 20 messages; the stored AI message contains no thinking content. Verify with `mvn test` while Compose is stopped
- [x] 4.4 Tool test: an arithmetic question (e.g. "What is 1234 * 5678?") asserts that the calculator counter is > 0 and the answer contains `7006652`. Verify with `mvn test`

## 5. Documentation and wrap-up

- [x] 5.1 Add a README section covering prerequisites (Docker Desktop, JDK 25), `docker compose up -d`, the first-run model download, running the agent with a conversation id, and `mvn test`. Verify by following it from a clean `docker compose down`
- [x] 5.2 Run `openspec validate add-qwen-agent-with-redis-memory --strict` and the full `mvn verify`. Both pass
