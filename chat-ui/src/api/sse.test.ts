import { describe, expect, it } from 'vitest'
import { createSseParser, readSse, type SseMessage } from './sse'

function parseChunks(chunks: string[]): SseMessage[] {
  const out: SseMessage[] = []
  const parser = createSseParser((m) => out.push(m))
  chunks.forEach((c) => parser.push(c))
  parser.end()
  return out
}

describe('createSseParser', () => {
  it('parses named events with JSON data', () => {
    expect(parseChunks(['event: token\ndata: {"text":"hi"}\n\nevent: done\ndata: {}\n\n'])).toEqual([
      { event: 'token', data: '{"text":"hi"}' },
      { event: 'done', data: '{}' },
    ])
  })

  it('handles events split across arbitrary chunk boundaries', () => {
    const wire = 'event: tool_call\ndata: {"id":"t1"}\n\nevent: tool_result\ndata: {"id":"t1","result":"7"}\n\n'
    const expected = parseChunks([wire])
    for (let size = 1; size < wire.length; size++) {
      const chunks = wire.match(new RegExp(`[\\s\\S]{1,${size}}`, 'g'))!
      expect(parseChunks(chunks)).toEqual(expected)
    }
  })

  it('joins multi-line data and ignores comments', () => {
    expect(parseChunks([': keep-alive\nevent: token\ndata: line one\ndata: line two\n\n'])).toEqual([
      { event: 'token', data: 'line one\nline two' },
    ])
  })

  it('accepts CRLF line endings, even split between chunks', () => {
    expect(parseChunks(['event: done\r', '\ndata: {}\r\n\r\n'])).toEqual([{ event: 'done', data: '{}' }])
  })

  it('flushes a final event without a trailing blank line', () => {
    expect(parseChunks(['event: done\ndata: {}'])).toEqual([{ event: 'done', data: '{}' }])
  })

  it('defaults the event name to "message"', () => {
    expect(parseChunks(['data: x\n\n'])).toEqual([{ event: 'message', data: 'x' }])
  })
})

describe('readSse', () => {
  it('decodes UTF-8 split inside a multi-byte character', async () => {
    const bytes = new TextEncoder().encode('event: token\ndata: {"text":"×"}\n\n')
    const cut = bytes.indexOf(0xc3) + 1 // between the two bytes of "×"
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(bytes.slice(0, cut))
        controller.enqueue(bytes.slice(cut))
        controller.close()
      },
    })
    const out: SseMessage[] = []
    await readSse(body, (m) => out.push(m))
    expect(out).toEqual([{ event: 'token', data: '{"text":"×"}' }])
  })
})
