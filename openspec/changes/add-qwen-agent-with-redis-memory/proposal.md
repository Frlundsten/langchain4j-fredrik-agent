## Why

The repository is an empty Maven project. We want a simple, fully local LangChain4j agent that holds multi-turn conversations, can call a couple of simple tools, and keeps its conversation context in Redis. It should run entirely in Docker on a developer machine, and its tests should run against a real model in containers rather than mocks.

## What Changes

- Add a plain Java 25 command-line chat agent built on LangChain4j `AiServices`, with per-conversation memory selected by a memory id.
- Use **qwen3.5 2B** served by Ollama as the only chat model, with **thinking mode enabled**.
- Store conversation memory in Redis via `RedisChatMemoryStore` (`langchain4j-community-redis`) behind a bounded message window. Redis runs with persistence disabled, so memory survives app restarts but not Redis restarts.
- Give the agent two simple tools: current date/time and a calculator.
- Add `docker-compose.yaml` with a version-pinned Ollama container, a Redis container, and a named Docker volume (`ollama`) that holds downloaded models so they are pulled only once.
- Add integration tests that use the Testcontainers **Ollama** module (plus a Redis container). They reuse the same `ollama` model volume so tests do not download the model again.

## Capabilities

### New Capabilities
- `chat-agent`: CLI conversation loop, model configuration (qwen3.5 2B, thinking on), and tool calling.
- `conversation-memory`: Redis-backed, per-conversation chat memory with a bounded window, excluding thinking content.
- `local-runtime`: Docker Compose environment for Ollama and Redis, including the persistent model volume and model provisioning.
- `integration-testing`: Testcontainers-based tests (Ollama module required) that check agent and memory behavior against the real model.

### Modified Capabilities
<!-- none: no existing specs -->

## Impact

- **Code**: new sources under `src/main/java` and `src/test/java`, plus `pom.xml` dependencies (LangChain4j BOM, `langchain4j-ollama`, `langchain4j-community-redis`, Testcontainers Ollama module, JUnit 5).
- **Files**: new `docker-compose.yaml`. An optional README section on how to run it.
- **Systems**: requires Docker Desktop (Windows host). The first run downloads the qwen3.5 2B model into the `ollama` volume.
- **Out of scope**: REST API, Spring/Quarkus, multi-model evaluation suite, GPU configuration (may be added later).
