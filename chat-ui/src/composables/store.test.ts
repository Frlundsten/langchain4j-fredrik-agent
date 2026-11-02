import { describe, expect, it, vi } from 'vitest'
import { ApiError, BackendUnreachableError } from '../api/client'
import type { StreamEvent } from '../api/types'
import { createChatStore, type ChatApi } from './store'

function fakeApi(overrides: Partial<ChatApi> = {}): ChatApi {
  return {
    checkHealth: vi.fn(async () => {}),
    listConversations: vi.fn(async () => []),
    getHistory: vi.fn(async () => []),
    deleteConversation: vi.fn(async () => {}),
    sendMessage: vi.fn(async (_id: string, _text: string, onEvent: (e: StreamEvent) => void, options = {}) => {
      options.onOpen?.()
      onEvent({ type: 'token', text: 'Hi!' })
      onEvent({ type: 'done' })
    }),
    ...overrides,
  }
}

const flush = () => new Promise((r) => setTimeout(r, 0))

describe('chat store', () => {
  it('starts a new chat when the hash has no conversation, and keeps the id in the hash', () => {
    const location = { hash: '' }
    const store = createChatStore(fakeApi(), location)
    store.init()
    expect(store.activeId.value).toMatch(/^[0-9a-f-]{36}$/)
    expect(location.hash).toBe(`#/c/${store.activeId.value}`)
  })

  it('opens the conversation named in the hash and loads its history', async () => {
    const api = fakeApi({ getHistory: vi.fn(async () => [{ role: 'user' as const, text: 'old' }]) })
    const store = createChatStore(api, { hash: '#/c/abc' })
    store.init()
    await flush()
    expect(api.getHistory).toHaveBeenCalledWith('abc')
    expect(store.active.value.messages).toEqual([{ role: 'user', text: 'old' }])
  })

  it('streams a reply into the active conversation and refreshes the list', async () => {
    const api = fakeApi()
    const store = createChatStore(api, { hash: '' })
    store.init()
    expect(await store.send('Hello')).toBe(true)
    expect(store.active.value.messages).toEqual([
      { role: 'user', text: 'Hello' },
      { role: 'assistant', status: 'done', parts: [{ type: 'text', text: 'Hi!' }] },
    ])
    expect(store.active.value.streaming).toBe(false)
    expect(vi.mocked(api.listConversations).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('locks the conversation while streaming but not other conversations', async () => {
    let finish!: () => void
    const api = fakeApi({
      sendMessage: vi.fn((_id, _t, onEvent) => new Promise<void>((resolve) => {
        finish = () => {
          onEvent({ type: 'done' })
          resolve()
        }
      })),
    })
    const store = createChatStore(api, { hash: '#/c/a' })
    store.init()
    const first = store.send('one')
    expect(store.active.value.streaming).toBe(true)
    expect(await store.send('two')).toBe(false)

    store.select('b')
    expect(store.active.value.streaming).toBe(false)
    finish()
    await first
    expect(store.states.get('a')!.messages).toHaveLength(2)
  })

  it('shows send failures in the reply and marks the backend down when unreachable', async () => {
    const down = async (): Promise<never> => {
      throw new BackendUnreachableError()
    }
    const api = fakeApi({ sendMessage: vi.fn(down), listConversations: vi.fn(down) })
    const store = createChatStore(api, { hash: '' })
    store.init()
    await store.send('hi')
    expect(store.backendDown.value).toBe(true)
    expect(store.active.value.messages[1]).toMatchObject({ status: 'error', error: 'The agent backend is unreachable.' })

    const busy = fakeApi({ sendMessage: vi.fn(async () => { throw new ApiError(409, 'still answering') }) })
    const store2 = createChatStore(busy, { hash: '' })
    store2.init()
    await store2.send('hi')
    expect(store2.active.value.messages[1]).toMatchObject({ status: 'error', error: 'still answering' })
  })

  it('recovers from an unreachable backend on retry', async () => {
    let up = false
    const api = fakeApi({
      listConversations: vi.fn(async () => {
        if (!up) throw new BackendUnreachableError()
        return [{ id: 'a', title: 'Hi', updatedAt: '' }]
      }),
    })
    const store = createChatStore(api, { hash: '' })
    store.init()
    await flush()
    expect(store.backendDown.value).toBe(true)
    up = true
    await store.retry()
    expect(store.backendDown.value).toBe(false)
    expect(store.conversations.value.map((c) => c.id)).toEqual(['a'])
  })

  it('keeps a message sent while history was still loading', async () => {
    let resolveHistory!: (h: []) => void
    const api = fakeApi({ getHistory: vi.fn(() => new Promise<[]>((r) => (resolveHistory = r))) })
    const store = createChatStore(api, { hash: '#/c/a' })
    store.init()
    await store.send('quick')
    resolveHistory([])
    await flush()
    expect(store.active.value.messages[0]).toEqual({ role: 'user', text: 'quick' })
  })

  it('deleting the active conversation switches to a new chat', async () => {
    const api = fakeApi({ listConversations: vi.fn(async () => [{ id: 'a', title: 'A', updatedAt: '' }]) })
    const store = createChatStore(api, { hash: '#/c/a' })
    store.init()
    await flush()
    await store.remove('a')
    expect(api.deleteConversation).toHaveBeenCalledWith('a')
    expect(store.conversations.value).toEqual([])
    expect(store.activeId.value).not.toBe('a')
    expect(store.active.value.messages).toEqual([])
  })
})
