## Purpose

A local command-line chat agent that holds multi-turn conversations with a locally served qwen3.5 2B model and can call simple tools to answer questions it cannot answer reliably on its own.

## ADDED Requirements

### Requirement: Interactive command-line conversation
The agent SHALL provide an interactive command-line loop that reads a user message, sends it to the model, and prints the model's final answer. The loop SHALL end when the user enters the quit command.

#### Scenario: User asks a question
- **WHEN** the user types a message and presses enter
- **THEN** the agent prints the model's answer and prompts for the next message

#### Scenario: User quits
- **WHEN** the user enters the quit command
- **THEN** the agent exits without sending anything to the model

### Requirement: Conversation identity
The agent SHALL associate every exchange with a conversation id. The id SHALL be configurable at startup and SHALL default to a fixed value when none is given, so that restarting the agent with the same id continues the same conversation.

#### Scenario: Resume conversation after restart
- **WHEN** the agent is restarted with the same conversation id as a previous session and Redis has not been restarted
- **THEN** the agent has the earlier exchanges of that conversation available as context

### Requirement: Fixed local model
The agent SHALL use the qwen3.5 2B model served by the local Ollama instance. The Ollama base URL SHALL be configurable, with a default that matches the local Docker Compose setup.

#### Scenario: Default configuration
- **WHEN** the agent starts without overrides while the Compose environment is running
- **THEN** it sends chat requests to the qwen3.5 2B model on the local Ollama instance

### Requirement: Thinking mode enabled
The agent SHALL send requests with the model's thinking mode enabled. Only the final answer SHALL be shown to the user. Thinking content SHALL NOT be printed by default.

#### Scenario: Answer shown without reasoning
- **WHEN** the model returns thinking content together with a final answer
- **THEN** the agent prints only the final answer

### Requirement: Tool calling
The agent SHALL offer the model exactly two tools: one that returns the current date and time, and one that evaluates basic arithmetic (addition, subtraction, multiplication, division) on numeric operands. Tool results SHALL be returned to the model so it can write the final answer.

#### Scenario: Current time question
- **WHEN** the user asks what the current date or time is
- **THEN** the model may call the date/time tool, and if it does, the final answer reflects the tool's result

#### Scenario: Invalid arithmetic
- **WHEN** the calculator tool is asked to divide by zero
- **THEN** the tool returns an error message to the model instead of throwing, and the conversation continues

### Requirement: Clear failure when dependencies are unavailable
The agent SHALL print a clear error message and exit with a non-zero status if Ollama or Redis cannot be reached at startup.

#### Scenario: Redis is down
- **WHEN** the agent starts and Redis is not reachable
- **THEN** it prints an error that names Redis and exits with a non-zero status
