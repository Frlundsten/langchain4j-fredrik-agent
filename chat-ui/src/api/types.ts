// Wire types of the chat API contract (see ../../API.md) and the UI's message model.

export interface ConversationSummary {
  id: string
  title: string
  updatedAt: string
}

// ---- history as sent by the backend ----

export interface WireTextPart {
  type: 'text'
  text: string
}

export interface WireToolPart {
  type: 'tool'
  id: string
  name: string
  arguments: Record<string, unknown>
  result: string | null
}

export type WireMessage =
  | { role: 'user'; text: string }
  | { role: 'assistant'; parts: (WireTextPart | WireToolPart)[] }

// ---- stream events ----

export type StreamEvent =
  | { type: 'token'; text: string }
  | { type: 'tool_call'; id: string; name: string; arguments: Record<string, unknown> }
  | { type: 'tool_result'; id: string; result: string; durationMs: number }
  | { type: 'done' }
  | { type: 'error'; message: string }

// ---- UI model: history and live streams produce the same shape ----

export interface TextPart {
  type: 'text'
  text: string
}

export type ToolStatus = 'running' | 'done' | 'error'

export interface ToolPart {
  type: 'tool'
  id: string
  name: string
  arguments: Record<string, unknown>
  result?: string
  status: ToolStatus
  durationMs?: number
}

export type Part = TextPart | ToolPart

export interface UserMessage {
  role: 'user'
  text: string
}

export interface AssistantMessage {
  role: 'assistant'
  parts: Part[]
  status: 'streaming' | 'done' | 'error'
  error?: string
}

export type ChatMessage = UserMessage | AssistantMessage
