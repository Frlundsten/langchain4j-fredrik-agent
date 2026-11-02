## Context

The agent is a plain Java 25 CLI (`AgentApp`) on LangChain4j `AiServices`. It uses a blocking `OllamaChatModel` (qwen3.5:2b, thinking on but not returned), `MessageWindowChatMemory` (20 messages) on `RedisChatMemoryStore`, and two tools in `AgentTools`. There is no HTTP layer, no streaming, and no record of which conversations exist: Redis only holds each conversation's message window under its memory id. See `proposal.md` for motivation and `specs/` for required behavior.

The user installed the Vue and daisyUI agent skills, which signals the frontend stack.

## Goals / Non-Goals

**Goals:**
- The UI and backend meet only at the documented contract (`chat-ui/API.md`). Deleting `src/` and writing a new backend against the contract must be enough.
- Tool calls are first-class data in the stream and in history, never parsed back out of text.
- Keep the Java side as small as the CLI: a few classes, no web framework.

**Non-Goals:**
- Serving the built UI from the Java process. The UI runs on its own (Vite dev server or any static host plus a proxy).
- Cancelling a reply mid-stream, auth, multi-user.
- A full transcript beyond the backend's memory window (see Risks).

## Decisions

### D1: Repository layout: sibling module, not a Maven multi-module
```
pom.xml, src/...        Java agent (CLI + new server mode), unchanged layout
chat-ui/                standalone npm project (Vue + Vite), own README and API.md
```
The UI has no Maven coupling, so swapping the backend never touches the UI's build. *Alternative:* convert to a Maven parent with `agent` and `chat-ui` modules (frontend-maven-plugin). Rejected because it moves every existing file and ties the UI build to Maven for no gain.

### D2: Transport: `POST` + Server-Sent Events read with `fetch`
`POST /api/conversations/{id}/messages` with `{"text": "..."}` answers `text/event-stream`. The browser reads it with `fetch` + `ReadableStream` and a small SSE parser, because `EventSource` only supports `GET` and cannot send a body. *Alternatives:* WebSocket (stateful, more code on both sides, and harder for another backend to implement); `GET` with the message in the query (length limits, and it puts user text in logs).

Endpoints:
| Method | Path | Result |
|---|---|---|
| GET | `/api/conversations` | `[{id, title, updatedAt}]` |
| GET | `/api/conversations/{id}/messages` | `[{role:"user", text} \| {role:"assistant", parts:[{type:"text", text} \| {type:"tool", id, name, arguments, result}]}]` |
| POST | `/api/conversations/{id}/messages` | SSE stream (below); 400 blank; 409 busy |
| DELETE | `/api/conversations/{id}` | 204 |
| GET | `/api/health` | 200 `{status:"ok"}`. Used by the UI to detect an unreachable backend |

SSE events (`event:` name + JSON `data:`):
```
token        {"text":"..."}
tool_call    {"id":"t1","name":"calculate","arguments":{"a":1234,"operator":"*","b":5678}}
tool_result  {"id":"t1","result":"7006652","durationMs":3}
done         {}
error        {"message":"..."}
```
Conversation ids are client-generated (`crypto.randomUUID()`), so "New chat" needs no round trip.

### D3: Java HTTP server: JDK `com.sun.net.httpserver` on virtual threads
New `AgentServer` main (port `AGENT_HTTP_PORT`, default `8080`) with a virtual-thread executor. JSON via Jackson, which LangChain4j already puts on the classpath. Declare it explicitly in the pom so we don't rely on a transitive dependency. Started with `mvn -q compile exec:java@server` (a second exec-plugin execution). It reuses `AgentApp`'s reachability checks. *Alternative:* Javalin (nicer routing and built-in SSE, but a new dependency for about 5 routes). Spring Boot is too heavy for this project's "few plain classes" style.

### D4: Streaming agent via `TokenStream`
Add `AgentFactory.streamingChatModelBuilder` (`OllamaStreamingChatModel`, same settings as the blocking one: think on, `returnThinking(false)`, numCtx, timeout) and a `StreamingAssistant` interface whose method returns `TokenStream` (same system message, same memory provider and tools). Mapping:
- `onPartialResponse` → `token`
- `beforeToolExecution` → `tool_call` (start a timer)
- `onToolExecuted` → `tool_result` (with `durationMs`)
- `onCompleteResponse` → `done`
- `onError` → `error`

Ollama does not reliably return tool call ids, so the server assigns its own (`t1`, `t2`, … per reply) and pairs `beforeToolExecution`/`onToolExecuted` in order. Tools run one after another, so ordering is safe. The CLI keeps the blocking `Assistant`, and both share the system message through a constant.

### D5: Conversation index in Redis
The memory store has no "list ids" operation, so the server keeps its own index:
- `chat:conversations`: a sorted set, member = id, score = last-updated epoch millis
- `chat:conversation:{id}`: a hash with `title` (first user message, 60 chars max) and `updatedAt`

The index is written when a message is accepted, before streaming starts. Delete removes the index entries and calls `ChatMemoryStore.deleteMessages(id)`. The `chat:` prefix keeps it clear of the memory store's keys. Conversations created from the CLI don't appear in the sidebar until they get a message through the server. That's acceptable, and noted in the README. *Alternative:* `SCAN` the memory store's keys. Rejected because it relies on the store's internal key format and has no titles or timestamps.

### D6: History built from stored `ChatMessage`s
`GET .../messages` reads `ChatMemoryStore.getMessages(id)` and folds it into the contract shape. `UserMessage` → user message. A run of `AiMessage` with tool requests + `ToolExecutionResultMessage`s + a final `AiMessage` text → one assistant message with parts in order. Tool requests and results are paired by position within the run. `SystemMessage`s are dropped. Arguments are parsed from the stored JSON string into an object. This gives tool calls in history for free, because LangChain4j already stores them.

### D7: Frontend stack
Vue 3 (`<script setup>`, TypeScript) + Vite + Tailwind CSS v4 + daisyUI 5, Vitest for unit tests. Markdown via `markdown-it` (HTML disabled) with DOMPurify on the output. No router: the active conversation id lives in the URL hash so reloads keep it. State lives in a few composables (`useConversations`, `useChat`, `useToolHighlight`) instead of Pinia, because the app has a single screen. The backend address comes from the `CHAT_API_URL` env var, read by the Vite dev proxy (default `http://localhost:8080`). The app itself always calls relative `/api`, so swapping the backend is a config change.

### D8: Message model and highlighting
In the UI, an assistant message is `parts: (TextPart | ToolPart)[]`, built by a pure reducer over stream events (`token` appends to the last text part or starts a new one after a tool part; `tool_call` pushes a running ToolPart; `tool_result` completes it by id). The same shape comes from history, so streamed and loaded messages render the same way. This reducer and the SSE parser are the main unit-test targets.

Result highlighting: after Markdown rendering, a DOM pass over the reply's text nodes wraps literal occurrences of each completed tool result (trimmed; only results of 2 or more characters, so "0" or "1" don't light up everywhere) in `<mark data-tool-id>`. Inline markers and panel cards carry the same `data-tool-id`. `useToolHighlight` holds the hovered/selected id, and clicks call `scrollIntoView` on the counterpart. Tool colors come from a fixed palette keyed by tool name, so `calculate` always has the same color.

### D9: Visual style
A custom daisyUI theme pair (light and dark), not a stock theme. The idea: an "agent console". The center chat is calm and readable. The right panel is a timeline (vertical line, numbered steps, monospace arguments and results) that makes each step feel like a trace. The theme follows `prefers-color-scheme`, has a toggle, and the choice is stored in `localStorage` (guarded). The exact palette and type are left to the builder, as long as both themes pass WCAG AA contrast for text.

## Risks / Trade-offs

- [History is limited to the 20-message memory window, and tool messages use up that window, so older turns vanish from the UI] → Accepted for now and stated in the README. A separate transcript store can come later without changing the contract.
- [A small model sometimes writes the tool result differently in its answer (e.g. `7,006,652`), so it isn't highlighted] → Inline markers and the panel still show the call. Only the bonus span highlight is missed. Number normalization can be added later.
- [`beforeToolExecution`/`onToolExecuted` callbacks differ between LangChain4j versions] → Verify against the pinned 1.23.0 API in the first backend task. If one is missing, emit `tool_call` and `tool_result` together from `onToolExecuted` (the contract still holds, without the running state).
- [The JDK HTTP server needs manual SSE flushing] → Write each event and `flush()` the response stream. Chunked mode (`sendResponseHeaders(200, 0)`).
- [The client disconnects mid-stream] → A write failure is caught and logged. The model call finishes and memory is still saved. The busy flag is cleared in a finally block.

## Migration Plan

Additive. The CLI, its tests and `docker-compose.yaml` are untouched. To run: `docker compose up -d`, `mvn -q compile exec:java@server`, `cd chat-ui && npm install && npm run dev`.

## Implementation Notes

Decisions made while building, which differ from or add to the plan above:
- **`-parameters` compiler flag** (`maven.compiler.parameters`). Without it, LangChain4j exposed the calculator's parameters as `arg0..arg2`, both to the model and in the UI's tool cards. They are now `a`, `operator`, `b`.
- **All five `TokenStream` callbacks exist in 1.23.0**, and `ToolExecution.duration()` supplies `durationMs`. The fallback in the D4 risk wasn't needed but is kept: if `beforeToolExecution` doesn't fire, `onToolExecuted` emits both events.
- **Stream tool ids** are random (`t-xxxxxxxx`) rather than `t1, t2, …`, so ids from different replies in one conversation can't collide. History uses `h1, h2, …`.
- **Number-aware highlighting was built now, not later.** In the first live run the model wrote `7,006,652` for the result `7006652`. Numeric results match with optional thousands separators, and never inside a longer number.
- **Tool error status:** a result starting with `Error` is shown as a failed call. The contract has no failure flag, and that is how `AgentTools` reports errors.
- **Activity panel** uses a small custom "trace rail" instead of daisyUI `timeline`, because `timeline-compact` pushed the cards past the panel's right edge.
- **Empty answers:** qwen3.5 sometimes ends a turn with only (hidden) thinking after a tool call. The UI then says "The agent finished without writing an answer" so the reply doesn't look broken.
- **Muted text** uses 70% opacity of `base-content`. 50–60% failed WCAG AA (as low as 3.1:1) in the light theme. 70% gives 5.7:1 (light) and 7.6:1 (dark).

## Open Questions

- Exact palette, fonts and icons (left to the builder within D9).
