# chat-api Specification

## Purpose
A backend-agnostic HTTP and Server-Sent Events contract between the chat UI and an agent backend, so any backend that implements it can serve the UI, plus the agent's own implementation of that contract.

## Requirements

### Requirement: Documented, backend-neutral contract
The chat API SHALL be plain HTTP with JSON bodies under the path prefix `/api`, and SHALL be documented in the repository in enough detail (endpoints, payloads, event types, error codes) for a different backend to implement it. The contract SHALL NOT expose framework-specific concepts or types from the agent's implementation.

#### Scenario: Contract document exists
- **WHEN** a developer opens the contract document in the frontend module
- **THEN** it lists every endpoint, request and response body, stream event type, and error status used by the UI

### Requirement: List conversations
The API SHALL return the list of conversations, each with an id, a title, and a last-updated timestamp (ISO-8601), ordered most recently updated first. A conversation's title SHALL be derived from its first user message, shortened to at most 60 characters.

#### Scenario: Conversations ordered by recency
- **WHEN** the client requests the conversation list after messages were sent in conversations `A` and then `B`
- **THEN** the response lists `B` before `A`, each with id, title and last-updated time

#### Scenario: No conversations
- **WHEN** the client requests the conversation list and none exist
- **THEN** the response is an empty list with status 200

### Requirement: Conversation history
The API SHALL return a conversation's history as an ordered list of messages. Each user message SHALL contain its text. Each assistant message SHALL be an ordered list of parts, where a part is either text or a tool call with id, tool name, arguments (as a JSON object), and result. The history SHALL contain only what the backend still remembers, and SHALL NOT contain thinking content.

#### Scenario: History with a tool call
- **WHEN** the client loads a conversation in which the assistant used the calculator before answering
- **THEN** the assistant message contains a tool call part (name, arguments, result) followed by a text part with the answer

#### Scenario: Unknown conversation
- **WHEN** the client requests the history of a conversation id that does not exist
- **THEN** the response is an empty message list with status 200

### Requirement: Streamed reply with tool events
Sending a user message to a conversation SHALL respond with a Server-Sent Events stream. The stream SHALL deliver these typed events in the order they happen:
- `token`: a piece of answer text
- `tool_call`: a tool call is about to run, with a call id, tool name and arguments
- `tool_result`: that call finished, with the same call id, the result text, and its duration in milliseconds
- `done`: the reply is complete
- `error`: the reply failed, with a human-readable message

Every `tool_call` SHALL be followed by a `tool_result` with the same id before `done`. The stream SHALL end with exactly one `done` or `error` event. The conversation SHALL be created on its first message if it does not exist, with a client-chosen id.

#### Scenario: Plain answer
- **WHEN** the client sends "Hi" to a conversation
- **THEN** the stream delivers one or more `token` events and then `done`

#### Scenario: Answer that uses a tool
- **WHEN** the client sends "What is 1234 * 5678?"
- **THEN** the stream delivers a `tool_call` for the calculator, then a `tool_result` with the same id containing `7006652`, then `token` events whose text contains `7006652`, then `done`

#### Scenario: Model failure
- **WHEN** the model cannot be reached while a reply is streaming
- **THEN** the stream ends with an `error` event that carries a message, and no `done` event

### Requirement: Input validation and concurrency
The API SHALL reject an empty or blank message with status 400. It SHALL reject a new message to a conversation that is still streaming a reply with status 409. Messages to different conversations SHALL be handled independently.

#### Scenario: Blank message
- **WHEN** the client sends a message consisting only of whitespace
- **THEN** the response has status 400 and no stream is started

#### Scenario: Concurrent message to same conversation
- **WHEN** the client sends a second message to conversation `A` while `A` is still streaming
- **THEN** the second request gets status 409 and the first stream continues

### Requirement: Delete conversation
The API SHALL delete a conversation, removing both its history and its entry in the conversation list.

#### Scenario: Delete
- **WHEN** the client deletes conversation `A`
- **THEN** `A` no longer appears in the conversation list and its history is empty

### Requirement: Agent server mode
The agent SHALL provide a server mode, separate from the CLI, that implements this contract using the same model, tools and Redis-backed memory as the CLI. The listening port SHALL be configurable with a default. Startup SHALL fail with a clear message and non-zero exit if Ollama or Redis is unreachable, as the CLI does.

#### Scenario: Shared memory with CLI
- **WHEN** a conversation is held through the server and the CLI is then started with the same conversation id
- **THEN** the CLI has the earlier exchanges of that conversation as context
