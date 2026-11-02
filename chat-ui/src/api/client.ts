import { readSse } from './sse'
import type { ConversationSummary, StreamEvent, WireMessage } from './types'

/** The backend answered with an error status. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** The backend could not be reached at all (down, or the dev proxy has nothing to forward to). */
export class BackendUnreachableError extends Error {
  constructor(cause?: unknown) {
    super('The agent backend is unreachable', { cause })
    this.name = 'BackendUnreachableError'
  }
}

const BASE = '/api'

// 502/503/504 are what the dev proxy (or a gateway) answers when the backend is down.
const GATEWAY_DOWN = new Set([502, 503, 504])

async function request(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(BASE + path, init)
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new BackendUnreachableError(e)
  }
  if (GATEWAY_DOWN.has(response.status)) throw new BackendUnreachableError()
  if (!response.ok) {
    let message = `Request failed with status ${response.status}`
    try {
      const body = (await response.json()) as { error?: string }
      if (body?.error) message = body.error
    } catch {
      // not JSON: keep the generic message
    }
    throw new ApiError(response.status, message)
  }
  return response
}

const path = (id: string) => `/conversations/${encodeURIComponent(id)}`

export async function checkHealth(): Promise<void> {
  await request('/health')
}

export async function listConversations(): Promise<ConversationSummary[]> {
  return (await request('/conversations')).json()
}

export async function getHistory(id: string): Promise<WireMessage[]> {
  return (await request(`${path(id)}/messages`)).json()
}

export async function deleteConversation(id: string): Promise<void> {
  await request(path(id), { method: 'DELETE' })
}

const EVENT_TYPES = new Set(['token', 'tool_call', 'tool_result', 'done', 'error'])

/**
 * Sends a message and reports each stream event. Resolves when the stream has ended. A stream that
 * closes without `done`/`error` is reported as an `error` event, so callers always get exactly one
 * final event. Errors before the stream starts (400, 409, unreachable) are thrown.
 */
export async function sendMessage(
  id: string,
  text: string,
  onEvent: (event: StreamEvent) => void,
  options: { onOpen?: () => void; signal?: AbortSignal } = {},
): Promise<void> {
  const response = await request(`${path(id)}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ text }),
    signal: options.signal,
  })
  options.onOpen?.()
  if (!response.body) {
    onEvent({ type: 'error', message: 'The backend sent no reply stream' })
    return
  }

  let ended = false
  try {
    await readSse(response.body, ({ event, data }) => {
      if (ended || !EVENT_TYPES.has(event)) return
      let payload: Record<string, unknown> = {}
      try {
        payload = data ? JSON.parse(data) : {}
      } catch {
        return // skip malformed event
      }
      if (event === 'done' || event === 'error') ended = true
      onEvent({ ...payload, type: event } as StreamEvent)
    })
  } catch {
    if (!ended) onEvent({ type: 'error', message: 'The connection to the agent was lost' })
    return
  }
  if (!ended) onEvent({ type: 'error', message: 'The connection closed before the reply finished' })
}
