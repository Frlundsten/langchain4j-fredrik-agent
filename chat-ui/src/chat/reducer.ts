import type { AssistantMessage, ChatMessage, Part, StreamEvent, ToolPart, ToolStatus, WireMessage } from '../api/types'

/** Tools report failures as text ("Error: division by zero"); show those as failed calls. */
export function statusOfResult(result: string | null | undefined): ToolStatus {
  return result != null && /^\s*error\b/i.test(result) ? 'error' : 'done'
}

export function emptyAssistantMessage(): AssistantMessage {
  return { role: 'assistant', parts: [], status: 'streaming' }
}

/**
 * Applies one stream event to an assistant message, returning a new message. Text and tool calls
 * keep the order they happened in: tokens after a tool call start a new text part.
 */
export function applyEvent(message: AssistantMessage, event: StreamEvent): AssistantMessage {
  if (message.status !== 'streaming') return message
  const parts = message.parts.slice()
  switch (event.type) {
    case 'token': {
      const last = parts.at(-1)
      if (last?.type === 'text') parts[parts.length - 1] = { type: 'text', text: last.text + event.text }
      else parts.push({ type: 'text', text: event.text })
      return { ...message, parts }
    }
    case 'tool_call':
      parts.push({ type: 'tool', id: event.id, name: event.name, arguments: event.arguments ?? {}, status: 'running' })
      return { ...message, parts }
    case 'tool_result': {
      const index = parts.findIndex((p) => p.type === 'tool' && p.id === event.id)
      if (index === -1) return message
      const call = parts[index] as ToolPart
      parts[index] = { ...call, result: event.result, durationMs: event.durationMs, status: statusOfResult(event.result) }
      return { ...message, parts }
    }
    case 'done':
      return { ...message, parts: finishRunning(parts, 'done'), status: 'done' }
    case 'error':
      return { ...message, parts: finishRunning(parts, 'error'), status: 'error', error: event.message }
  }
}

function finishRunning(parts: Part[], status: ToolStatus): Part[] {
  return parts.map((p) => (p.type === 'tool' && p.status === 'running' ? { ...p, status } : p))
}

/** Converts loaded history into the same shape the stream produces. */
export function fromHistory(history: WireMessage[]): ChatMessage[] {
  return history.map((message): ChatMessage => {
    if (message.role === 'user') return { role: 'user', text: message.text }
    return {
      role: 'assistant',
      status: 'done',
      parts: message.parts.map((part): Part =>
        part.type === 'text'
          ? { type: 'text', text: part.text }
          : {
              type: 'tool',
              id: part.id,
              name: part.name,
              arguments: part.arguments ?? {},
              ...(part.result != null ? { result: part.result } : {}),
              status: part.result == null ? 'error' : statusOfResult(part.result),
            },
      ),
    }
  })
}

/** All tool calls of a conversation, with the user question each one answered. */
export interface ToolActivity {
  call: ToolPart
  step: number
  question: string
}

export function toolActivity(messages: ChatMessage[]): ToolActivity[] {
  const activity: ToolActivity[] = []
  let question = ''
  for (const message of messages) {
    if (message.role === 'user') {
      question = message.text
      continue
    }
    for (const part of message.parts) {
      if (part.type === 'tool') activity.push({ call: part, step: activity.length + 1, question })
    }
  }
  return activity
}
