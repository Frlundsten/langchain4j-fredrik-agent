## Purpose

Per-conversation chat memory stored in Redis so the agent keeps context across turns and across agent restarts, bounded so it fits the model's configured context window.

## ADDED Requirements

### Requirement: Redis-backed memory per conversation
The system SHALL store the messages of each conversation in Redis, keyed by conversation id. Each turn SHALL be read from and written back to Redis.

#### Scenario: Memory written after a turn
- **WHEN** the user completes one exchange in conversation `A`
- **THEN** Redis contains the user message and the final AI answer for conversation `A`

#### Scenario: Context used on a later turn
- **WHEN** the user states their name in one turn and asks for it in a later turn of the same conversation
- **THEN** the earlier turn is included in the context sent to the model

### Requirement: Conversation isolation
Messages from one conversation id SHALL NOT be visible in the context of a different conversation id.

#### Scenario: Two conversations
- **WHEN** conversation `A` has messages and a first message is sent in conversation `B`
- **THEN** the context sent for `B` contains no messages from `A`

### Requirement: Bounded memory window
The stored memory for a conversation SHALL be limited to a configurable maximum number of messages. When the limit is exceeded, the oldest messages SHALL be evicted first.

#### Scenario: Window exceeded
- **WHEN** a conversation grows beyond the configured maximum number of messages
- **THEN** the oldest messages are evicted and the stored count does not exceed the maximum

### Requirement: Thinking content not persisted
Model thinking content SHALL NOT be stored in conversation memory.

#### Scenario: Turn with thinking
- **WHEN** the model returns thinking content and a final answer
- **THEN** the AI message stored in Redis contains the final answer and no thinking content

### Requirement: Volatile storage
Redis SHALL run without on-disk persistence. Conversation memory SHALL survive agent restarts while Redis keeps running, and SHALL be lost when the Redis container is recreated or restarted.

#### Scenario: Redis restarted
- **WHEN** the Redis container is restarted
- **THEN** all previously stored conversations are gone
