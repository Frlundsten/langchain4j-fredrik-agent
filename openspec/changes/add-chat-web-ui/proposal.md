## Why

The agent can only be used from a terminal, and it gives no view of what it does between your question and its answer. We want a browser chat UI that also teaches: every tool call the agent makes is shown where it happens in the reply and collected in a side panel, so you can watch how an agent works. The UI must not depend on LangChain4j, so the backend can later be replaced with something else and the UI kept as it is.

## What Changes

- Add a **standalone frontend module** in `chat-ui/` (its own build, no Java dependency). It is a three-column chat app with its own visual style:
  - **Left:** a sidebar of conversations (new, switch, delete), most recent first.
  - **Center:** the chat, with assistant replies **streamed** token by token and rendered as Markdown.
  - **Right:** a **tool activity panel** listing every tool call in the current conversation (tool name, arguments, result, status, duration).
- **Tool calls are highlighted in the reply text.** Each call appears as an inline marker at the point in the reply where it happened. Parts of the answer text that literally repeat a tool result are highlighted too. Hovering or clicking an inline marker, a highlighted span or a panel card highlights the matching items in the other places.
- Define a **backend-agnostic chat API**: plain HTTP + JSON, with replies streamed as Server-Sent Events that carry typed events (`token`, `tool_call`, `tool_result`, `done`, `error`). The frontend only knows this contract. The contract is documented in the repo so another backend can implement it.
- Add an **HTTP server mode** to the existing Java agent that implements the contract on top of LangChain4j streaming (`TokenStream`) and the existing Redis memory. It also keeps a small Redis index of conversations (title, last update) for the sidebar. The CLI keeps working unchanged.

## Capabilities

### New Capabilities
- `chat-api`: the HTTP/SSE contract between the UI and any agent backend (conversations, history, streamed replies with tool events, errors), and the Java backend's implementation of it.
- `chat-web-ui`: the browser UI: conversation sidebar, streamed chat, tool call highlighting in reply text, the tool activity panel, and how they link to each other.

### Modified Capabilities
<!-- none: no main specs exist yet; the CLI agent's behavior is unchanged -->

## Impact

- **New module:** `chat-ui/` (Vue 3, Vite, TypeScript, Tailwind CSS + daisyUI, Vitest). Needs Node.js on the developer machine.
- **Java code:** new HTTP server entry point and SSE endpoint classes in `src/main/java`, a streaming chat model and a streaming `Assistant` method, and a conversation index in Redis. Adds an `exec-maven-plugin` execution to start the server.
- **Dependencies:** none new for Java beyond what LangChain4j already brings (JDK HTTP server; Jackson is already present through LangChain4j). npm dependencies for the UI.
- **Docs:** README section on running the server and the UI. A `chat-ui/API.md` contract document.
- **Out of scope:** authentication, deployment/packaging the UI into the jar, a stop-generation button, showing the model's thinking text, transcripts longer than the backend's memory window.
