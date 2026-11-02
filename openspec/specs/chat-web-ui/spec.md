# chat-web-ui Specification

## Purpose
A browser chat interface for the agent that streams replies and makes the agent's tool use visible, both inline in the reply and in a dedicated panel, so it can be used to show how an agent works.

## Requirements

### Requirement: Standalone frontend module
The UI SHALL live in its own module with its own build, and SHALL depend on the agent only through the chat API contract. The backend address SHALL be configurable without code changes.

#### Scenario: Point at another backend
- **WHEN** the UI is started with the backend address set to a different server that implements the chat API
- **THEN** the UI works against that server with no code changes

### Requirement: Three-area layout
On wide screens the UI SHALL show a conversation sidebar on the left, the chat in the center, and the tool activity panel on the right. On narrow screens the sidebar and the tool panel SHALL collapse into drawers that can be opened from the chat view.

#### Scenario: Wide screen
- **WHEN** the UI is opened in a window at least 1280 px wide
- **THEN** the sidebar, chat and tool panel are visible side by side

#### Scenario: Narrow screen
- **WHEN** the UI is opened in a window narrower than 1024 px
- **THEN** the chat fills the width, and buttons open the sidebar and the tool panel as drawers

### Requirement: Conversation sidebar
The sidebar SHALL list conversations by title, most recent first, and SHALL let the user start a new conversation, switch to an existing one, and delete one after confirming. The active conversation SHALL be visibly marked. Switching SHALL load that conversation's history, including its tool calls, into the chat and the tool panel.

#### Scenario: New conversation
- **WHEN** the user clicks "New chat" and sends a first message
- **THEN** a new entry titled from that message appears at the top of the sidebar and is marked active

#### Scenario: Switch conversation
- **WHEN** the user selects another conversation in the sidebar
- **THEN** the chat shows that conversation's messages and the tool panel shows its tool calls

#### Scenario: Delete conversation
- **WHEN** the user deletes a conversation and confirms
- **THEN** it disappears from the sidebar, and if it was active the UI switches to an empty new chat

### Requirement: Streamed replies
The UI SHALL show the assistant's reply as it streams, appending text as it arrives and rendering it as Markdown. While waiting for the first event the UI SHALL show a working indicator. The message input SHALL be disabled for the active conversation while a reply is streaming. Enter SHALL send and Shift+Enter SHALL insert a new line.

#### Scenario: Streaming text
- **WHEN** the user sends a message
- **THEN** the reply text grows on screen as tokens arrive, and the input is enabled again after the reply completes

#### Scenario: Stream error
- **WHEN** the reply stream ends with an error event or the connection fails
- **THEN** the reply shows an error state with the message, and the user can send again

### Requirement: Inline tool call markers
Each tool call SHALL appear inside the assistant's reply as a highlighted inline marker at the position in the stream where the call happened, showing the tool name and a short form of its arguments and result. While the tool is running the marker SHALL show a running state.

#### Scenario: Marker in reply
- **WHEN** a reply contains a calculator call followed by answer text
- **THEN** the reply shows a highlighted calculator marker before the answer text, showing the arguments and result `7006652`

#### Scenario: Running state
- **WHEN** a `tool_call` event has arrived but its `tool_result` has not
- **THEN** the marker and its panel card both show a running state

### Requirement: Highlight tool results in answer text
When the answer text contains a tool call's result literally (for example the number `7006652`), the UI SHALL highlight those spans of text and link them to that tool call.

#### Scenario: Result repeated in answer
- **WHEN** the calculator returned `7006652` and the answer says "1234 × 5678 = 7006652"
- **THEN** `7006652` in the answer text is highlighted in the same color as the calculator call

### Requirement: Tool activity panel
The right panel SHALL list every tool call in the active conversation in order. Each card SHALL show the tool name, the arguments, the result, the status (running, done, error), and the duration. New calls SHALL appear in the panel as they happen, and the panel SHALL scroll to the newest call. When the conversation has no tool calls the panel SHALL show an empty state that explains what will appear there.

#### Scenario: Live card
- **WHEN** the agent calls a tool during a streaming reply
- **THEN** a new card appears in the panel in the running state, then switches to done with the result and duration

#### Scenario: Empty panel
- **WHEN** the active conversation has no tool calls
- **THEN** the panel shows an empty state message

### Requirement: Linked highlighting
An inline marker, its highlighted result spans, and its panel card SHALL be linked. Hovering any one of them SHALL highlight the others. Clicking a marker or span SHALL scroll its panel card into view, and clicking a card SHALL scroll its marker into view. Each tool SHALL have a consistent color across all three places.

#### Scenario: Click marker
- **WHEN** the user clicks an inline tool marker in an older message
- **THEN** the matching card scrolls into view in the panel and is highlighted

#### Scenario: Click card
- **WHEN** the user clicks a card in the tool panel
- **THEN** the chat scrolls to the matching inline marker and highlights it

### Requirement: Own visual style
The UI SHALL use a custom visual theme, not a stock component-library theme, with both light and dark variants that follow the operating system setting and can be toggled by the user.

#### Scenario: Theme toggle
- **WHEN** the user toggles the theme
- **THEN** the whole UI switches between light and dark and the choice is kept on reload

### Requirement: Backend unavailable
When the backend cannot be reached the UI SHALL show a clear, non-blocking message and SHALL recover without a page reload once the backend is back.

#### Scenario: Backend down at load
- **WHEN** the UI loads while the backend is not running
- **THEN** it shows a message that the agent backend is unreachable, and loading the conversation list works after the backend starts and the user retries
