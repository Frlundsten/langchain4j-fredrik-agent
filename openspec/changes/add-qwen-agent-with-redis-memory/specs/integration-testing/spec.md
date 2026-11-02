## Purpose

Automated tests that run the agent against a real qwen3.5 2B model and a real Redis in throwaway containers, so behavior is checked end to end without depending on the Compose environment.

## ADDED Requirements

### Requirement: Testcontainers Ollama
Tests that need a model SHALL start Ollama through the Testcontainers Ollama module, using the same pinned image tag as the Compose environment. Tests SHALL NOT depend on the Compose environment running.

#### Scenario: Test run with Compose stopped
- **WHEN** the test suite runs while no Compose services are running
- **THEN** the tests start their own Ollama and Redis containers and pass

### Requirement: Testcontainers Redis
Tests that need conversation memory SHALL use a Redis instance started by Testcontainers.

#### Scenario: Isolated Redis
- **WHEN** a memory test runs
- **THEN** it reads and writes a Redis container created for the test run, not the developer's Compose Redis

### Requirement: Model reuse across test runs
The test Ollama container SHALL mount the shared `ollama` model volume. It SHALL pull qwen3.5 2B only if the model is not already present.

#### Scenario: Second test run
- **WHEN** the tests have run once already (or the Compose environment downloaded the model)
- **THEN** a later test run does not download the model again

### Requirement: Deterministic-leaning assertions
Model-backed tests SHALL use temperature 0 and a fixed seed. They SHALL assert on observable behavior (stored messages, isolation, tool invocation, answers containing a known fact) rather than exact answer text.

#### Scenario: Memory recall test
- **WHEN** a test tells the agent a name and then asks for it in the same conversation
- **THEN** the test passes if the answer contains that name, regardless of the rest of the wording

#### Scenario: Tool invocation test
- **WHEN** a test asks an arithmetic question that requires the calculator
- **THEN** the test asserts that the calculator tool was invoked and that the answer contains the correct result
