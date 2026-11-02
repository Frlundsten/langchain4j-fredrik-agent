# Chat API contract

This is the only thing the chat UI knows about its backend. Any server that implements this
contract can replace the bundled Java/LangChain4j agent.

- Base path: `/api`. The UI always calls relative `/api/...`. In development the Vite proxy forwards
  it to `CHAT_API_URL` (default `http://localhost:8080`).
- Bodies are JSON (`Content-Type: application/json; charset=utf-8`) unless stated otherwise.
- Timestamps are ISO-8601 strings in UTC, e.g. `2026-11-02T20:39:12.354Z`.
- Conversation ids are chosen by the client (the UI uses `crypto.randomUUID()`). A backend must
  accept any id matching `[A-Za-z0-9_-]{1,100}` and reply `400` for anything else.

## Errors

Non-streaming errors use an HTTP status plus a JSON body:

```json
{ "error": "human readable message" }
```

| Status | When |
|---|---|
| `400` | Invalid conversation id, missing/blank message text, malformed JSON |
| `404` | Unknown route |
| `405` | Wrong method on a known route |
| `409` | A message is sent to a conversation that is still streaming a reply |
| `500` | Unexpected server error |

## `GET /api/health`

Liveness check. The UI uses it to tell "backend unreachable" apart from other failures.

`200` → `{ "status": "ok" }`

## `GET /api/conversations`

Lists conversations, **most recently updated first**.

`200` →

```json
[
  { "id": "b3c1…", "title": "What is 1234 * 5678?", "updatedAt": "2026-11-02T20:41:00.000Z" }
]
```

- `title` comes from the conversation's first user message, cut to at most 60 characters.
- An empty list (`[]`) means there are no conversations.

## `GET /api/conversations/{id}/messages`

Returns the conversation's history, oldest first. It contains only what the backend still
remembers (it may be a sliding window), and never the model's thinking content.

`200` →

```json
[
  { "role": "user", "text": "What is 1234 * 5678?" },
  {
    "role": "assistant",
    "parts": [
      { "type": "tool", "id": "t1", "name": "calculate",
        "arguments": { "a": 1234, "operator": "*", "b": 5678 }, "result": "7006652" },
      { "type": "text", "text": "1234 × 5678 = **7006652**" }
    ]
  }
]
```

- A `user` message has `text`.
- An `assistant` message has ordered `parts`. A part is either
  - `{ "type": "text", "text": string }` or
  - `{ "type": "tool", "id": string, "name": string, "arguments": object, "result": string }`.
- Tool `id`s only need to be unique within one conversation's history.
- For an unknown id the response is `200` with `[]`.

## `POST /api/conversations/{id}/messages`

Sends a user message and streams the assistant's reply. If the conversation doesn't exist yet,
it is created.

Request:

```json
{ "text": "What is 1234 * 5678?" }
```

Before the stream starts the server can reply with `400` (blank text, invalid id) or `409`
(this conversation is already streaming). Otherwise it replies `200` with
`Content-Type: text/event-stream` and sends [Server-Sent Events](https://html.spec.whatwg.org/multipage/server-sent-events.html):

```
event: tool_call
data: {"id":"t1","name":"calculate","arguments":{"a":1234,"operator":"*","b":5678}}

event: tool_result
data: {"id":"t1","result":"7006652","durationMs":3}

event: token
data: {"text":"1234 × 5678 = "}

event: token
data: {"text":"7006652"}

event: done
data: {}

```

| Event | Data | Meaning |
|---|---|---|
| `token` | `{ "text": string }` | Next piece of answer text. Concatenate in order. |
| `tool_call` | `{ "id": string, "name": string, "arguments": object }` | A tool is about to run. |
| `tool_result` | `{ "id": string, "result": string, "durationMs": number }` | The tool call with this `id` finished. |
| `done` | `{}` | Reply complete. Last event. |
| `error` | `{ "message": string }` | Reply failed. Last event. |

Ordering rules:

- Events arrive in the order things happen, so text and tool calls can interleave.
- Every `tool_call` is followed by a `tool_result` with the same `id` before `done`.
- The stream ends with **exactly one** `done` or `error` event.
- The UI should still handle a dropped connection (no final event) as an error.

Because the request is a `POST` with a body, browsers read the stream with `fetch` and a
`ReadableStream`, not `EventSource`.

## `DELETE /api/conversations/{id}`

Removes the conversation's history and its entry in the conversation list.

`204` with no body. Deleting an unknown id also returns `204`.
