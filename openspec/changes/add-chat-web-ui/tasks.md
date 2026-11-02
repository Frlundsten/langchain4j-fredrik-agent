## 1. Contract

- [x] 1.1 Write `chat-ui/API.md` documenting every endpoint, JSON body, SSE event, and error status from design D2. Verify that each requirement in `specs/chat-api/spec.md` maps to a section of it

## 2. Backend: streaming agent

- [x] 2.1 Check the LangChain4j 1.23.0 `TokenStream` API (`onPartialResponse`, `beforeToolExecution`, `onToolExecuted`, `onCompleteResponse`, `onError`) and record which callbacks exist. Verify with a compile of a small usage
- [x] 2.2 Add `streamingChatModelBuilder` (`OllamaStreamingChatModel`, same settings) and a `StreamingAssistant` returning `TokenStream`, sharing the system message constant with `Assistant`. Verify with `mvn -q compile` and that the existing tests still pass
- [x] 2.3 Add an integration test (existing Testcontainers support) that streams "What is 1234 * 5678?" and asserts the event order: tool call before tool result, tokens containing `7006652`, completion last. Verify with `mvn test`

## 3. Backend: HTTP server

- [x] 3.1 Add an explicit Jackson dependency and a JSON event writer for SSE (event name + JSON data + flush). Verify with a unit test of the exact wire format
- [x] 3.2 Implement the conversation index (`chat:conversations` sorted set, `chat:conversation:{id}` hash; title of 60 chars max; touch, list, delete). Verify with an integration test against the Redis test container
- [x] 3.3 Implement the history mapper from stored `ChatMessage`s to contract messages (user, assistant parts with paired tool calls, system dropped). Verify with unit tests for text-only, single tool, two tools, and an empty conversation
- [x] 3.4 Implement `AgentServer` (JDK HttpServer, virtual threads, `AGENT_HTTP_PORT` default 8080, startup reachability checks reused from `AgentApp`) with `/api/health`, list, history, and delete routes. Verify with HTTP tests for 200/204 responses and an empty list
- [x] 3.5 Implement the streaming POST route: 400 for blank, 409 when the conversation is busy, index touch, `TokenStream` → SSE mapping with server-assigned tool ids and `durationMs`, error event on failure, busy flag cleared in finally. Verify with integration tests for the tool scenario, 400 and 409
- [x] 3.6 Add an `exec-maven-plugin` execution `server` and run `mvn -q compile exec:java@server` against Compose. Verify by `curl -N` POSTing a message and seeing `tool_call`, `tool_result`, `token` and `done` events

## 4. Frontend: scaffold

- [x] 4.1 Create `chat-ui/` with Vite + Vue 3 + TypeScript, Tailwind CSS v4, daisyUI 5, Vitest, and a Vite dev proxy for `/api` → `CHAT_API_URL` (default `http://localhost:8080`). Verify that `npm run build` and `npm test` succeed
- [x] 4.2 Define a custom light/dark daisyUI theme pair (design D9) with a toggle that is remembered across reloads and follows the OS by default. Verify visually in both themes and check text contrast for AA

## 5. Frontend: data layer

- [x] 5.1 Add TypeScript types for the contract and an API client (list, history, delete, health). Verify with unit tests against a mocked `fetch`
- [x] 5.2 Implement the `fetch`-based SSE parser (chunk boundaries, multi-line data, final event without a trailing newline). Verify with unit tests that feed split chunks
- [x] 5.3 Implement the pure stream-event reducer into `parts` (design D8). Verify with unit tests: tokens only, tool between texts, two tools, error mid-stream, and that history and stream produce the same shape
- [x] 5.4 Implement `useConversations` (list, active id in URL hash, new, delete) and `useChat` (load history, send, streaming state, input lock, errors, backend-unreachable state with retry). Verify with unit tests on a mocked client

## 6. Frontend: UI

- [x] 6.1 Build the three-area layout with drawers below 1024 px. Verify at 1280 px and 390 px widths
- [x] 6.2 Build the conversation sidebar (new, switch, delete with confirm, active marker, recency order). Verify by manually running the sidebar scenarios in the spec
- [x] 6.3 Build the chat view: Markdown rendering (HTML disabled + DOMPurify), streaming text, working indicator, Enter/Shift+Enter, error state. Verify by manually running the streaming scenarios against the real backend
- [x] 6.4 Build inline tool markers (name, short arguments → result, running state, per-tool color). Verify with a component test and by asking a calculator question
- [x] 6.5 Build result-span highlighting (text-node pass, results of 2 or more characters, `<mark data-tool-id>`). Verify with a unit test that `7006652` is wrapped and `1` is not
- [x] 6.6 Build the tool activity panel as a timeline (cards with name, arguments, result, status, duration; auto-scroll; empty state). Verify by watching a live call go from running to done
- [x] 6.7 Implement linked highlighting across marker, spans and card (hover highlights, click scrolls the counterpart). Verify manually in both directions on an older message

## 7. Wrap-up

- [x] 7.1 Verify end to end with the backend down: the UI shows the unreachable message, and after starting the backend and clicking retry, it recovers without a reload
- [x] 7.2 Add a README section (root) and `chat-ui/README.md` covering running the server, running the UI, `CHAT_API_URL`, the memory-window limit, and CLI conversations not appearing in the sidebar. Verify by following it from a clean start
