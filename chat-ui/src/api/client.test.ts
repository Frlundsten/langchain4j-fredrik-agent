import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, BackendUnreachableError, deleteConversation, getHistory, listConversations, sendMessage } from './client'
import type { StreamEvent } from './types'

function sseResponse(body: string, status = 200): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'text/event-stream' } })
}

function mockFetch(impl: (url: string, init?: RequestInit) => Promise<Response>) {
  const fn = vi.fn(impl)
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => vi.unstubAllGlobals())

describe('client', () => {
  it('lists conversations', async () => {
    const list = [{ id: 'a', title: 'Hi', updatedAt: '2026-11-02T20:00:00Z' }]
    const fetch = mockFetch(async () => Response.json(list))
    expect(await listConversations()).toEqual(list)
    expect(fetch).toHaveBeenCalledWith('/api/conversations', undefined)
  })

  it('encodes conversation ids in paths', async () => {
    const fetch = mockFetch(async () => Response.json([]))
    await getHistory('a b')
    expect(fetch.mock.calls[0][0]).toBe('/api/conversations/a%20b/messages')
  })

  it('deletes with DELETE', async () => {
    const fetch = mockFetch(async () => new Response(null, { status: 204 }))
    await deleteConversation('a')
    expect(fetch.mock.calls[0][1]).toMatchObject({ method: 'DELETE' })
  })

  it('throws ApiError with the server message', async () => {
    mockFetch(async () => Response.json({ error: 'still answering' }, { status: 409 }))
    await expect(sendMessage('a', 'hi', () => {})).rejects.toEqual(new ApiError(409, 'still answering'))
  })

  it('treats network failures and gateway errors as unreachable', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch')
    })
    await expect(listConversations()).rejects.toBeInstanceOf(BackendUnreachableError)
    mockFetch(async () => new Response('proxy error', { status: 502 }))
    await expect(listConversations()).rejects.toBeInstanceOf(BackendUnreachableError)
  })

  it('streams typed events', async () => {
    const fetch = mockFetch(async () =>
      sseResponse(
        'event: tool_call\ndata: {"id":"t1","name":"calculate","arguments":{"a":1}}\n\n' +
          'event: tool_result\ndata: {"id":"t1","result":"1","durationMs":2}\n\n' +
          'event: token\ndata: {"text":"1"}\n\nevent: done\ndata: {}\n\n',
      ),
    )
    const events: StreamEvent[] = []
    const onOpen = vi.fn()
    await sendMessage('a', 'hi', (e) => events.push(e), { onOpen })
    expect(onOpen).toHaveBeenCalledOnce()
    expect(JSON.parse(fetch.mock.calls[0][1]!.body as string)).toEqual({ text: 'hi' })
    expect(events).toEqual([
      { type: 'tool_call', id: 't1', name: 'calculate', arguments: { a: 1 } },
      { type: 'tool_result', id: 't1', result: '1', durationMs: 2 },
      { type: 'token', text: '1' },
      { type: 'done' },
    ])
  })

  it('reports a stream that closes without a final event as an error', async () => {
    mockFetch(async () => sseResponse('event: token\ndata: {"text":"par"}\n\n'))
    const events: StreamEvent[] = []
    await sendMessage('a', 'hi', (e) => events.push(e))
    expect(events.at(-1)).toMatchObject({ type: 'error' })
  })
})
