# local-runtime Specification

## Purpose
A Docker Compose environment that provides the Ollama model server and Redis for the agent, with downloaded models kept in a persistent volume so they are fetched only once.

## Requirements

### Requirement: Compose services
The project SHALL provide a Docker Compose file that starts an Ollama service and a Redis service, each reachable from the host on its standard port (11434 for Ollama, 6379 for Redis).

#### Scenario: Environment start
- **WHEN** the developer runs `docker compose up -d`
- **THEN** Ollama and Redis are running and reachable from the host

### Requirement: Pinned Ollama version
The Ollama image SHALL be pinned to an explicit version tag that supports qwen3.5, not `latest`. The same tag SHALL be used by the integration tests.

#### Scenario: Version consistency
- **WHEN** the Compose file and the test configuration are inspected
- **THEN** both reference the same Ollama image tag

### Requirement: Persistent model volume
Ollama's model storage SHALL be mounted on a named Docker volume called `ollama`. Downloaded models SHALL survive `docker compose down` and container recreation.

#### Scenario: Recreate without re-download
- **WHEN** the model has been downloaded once and the environment is taken down and started again
- **THEN** the model is available without being downloaded again

### Requirement: Model provisioning
The environment SHALL ensure the qwen3.5 2B model is present in Ollama without manual `ollama pull` steps. Provisioning SHALL skip the download when the model is already in the volume.

#### Scenario: First start on a clean machine
- **WHEN** the environment is started and the `ollama` volume does not contain the model
- **THEN** the model is downloaded into the volume and becomes available for chat

### Requirement: Redis without persistence
The Redis service SHALL be configured with snapshotting and append-only persistence disabled.

#### Scenario: Redis config
- **WHEN** the running Redis configuration is queried
- **THEN** RDB saving is disabled and append-only mode is off
