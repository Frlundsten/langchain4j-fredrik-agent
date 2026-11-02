// Minimal Server-Sent Events parser for fetch() streams (EventSource can't POST).
// Follows the WHATWG field rules we need: event, data (multi-line), comments, CRLF.

export interface SseMessage {
  event: string
  data: string
}

export interface SseParser {
  push(chunk: string): void
  /** Flushes a final event that wasn't followed by a blank line. */
  end(): void
}

export function createSseParser(onMessage: (message: SseMessage) => void): SseParser {
  let buffer = ''
  let event = ''
  let data: string[] = []

  const dispatch = () => {
    if (data.length > 0 || event) {
      onMessage({ event: event || 'message', data: data.join('\n') })
    }
    event = ''
    data = []
  }

  const line = (raw: string) => {
    if (raw === '') {
      dispatch()
      return
    }
    if (raw.startsWith(':')) return
    const colon = raw.indexOf(':')
    const field = colon === -1 ? raw : raw.slice(0, colon)
    let value = colon === -1 ? '' : raw.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }

  return {
    push(chunk) {
      buffer += chunk
      // A lone trailing \r might be the first half of \r\n: keep it for the next chunk.
      const match = /\r\n|\n|\r(?!$)/g
      let start = 0
      let m: RegExpExecArray | null
      while ((m = match.exec(buffer)) !== null) {
        line(buffer.slice(start, m.index))
        start = m.index + m[0].length
      }
      buffer = buffer.slice(start)
    },
    end() {
      if (buffer.length > 0) {
        line(buffer.replace(/\r$/, ''))
        buffer = ''
      }
      dispatch()
    },
  }
}

/** Reads a byte stream and yields SSE messages as they complete. */
export async function readSse(
  body: ReadableStream<Uint8Array>,
  onMessage: (message: SseMessage) => void,
): Promise<void> {
  const parser = createSseParser(onMessage)
  const decoder = new TextDecoder()
  const reader = body.getReader()
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true })) // keeps split multi-byte chars intact
    }
  } finally {
    reader.releaseLock()
  }
  parser.push(decoder.decode())
  parser.end()
}
