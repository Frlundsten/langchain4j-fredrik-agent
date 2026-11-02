import { computed, reactive, ref, shallowRef } from 'vue'
import * as client from '../api/client'
import { BackendUnreachableError } from '../api/client'
import type { AssistantMessage, ChatMessage, ConversationSummary } from '../api/types'
import { applyEvent, emptyAssistantMessage, fromHistory } from '../chat/reducer'

export type ChatApi = Pick<
  typeof client,
  'checkHealth' | 'listConversations' | 'getHistory' | 'deleteConversation' | 'sendMessage'
>

export interface ConversationState {
  messages: ChatMessage[]
  loaded: boolean
  loading: boolean
  streaming: boolean
}

export interface HashLocation {
  hash: string
}

const HASH_PREFIX = '#/c/'

/**
 * All app state: the conversation list, the active conversation (kept in the URL hash), and each
 * conversation's messages. A stream keeps running when the user switches away; its messages
 * update in the background.
 */
export function createChatStore(api: ChatApi = client, location: HashLocation = window.location) {
  const conversations = shallowRef<ConversationSummary[]>([])
  const listLoaded = ref(false)
  const backendDown = ref(false)
  const lastError = ref<string | null>(null)
  const activeId = ref('')
  const states = reactive(new Map<string, ConversationState>())

  function stateOf(id: string): ConversationState {
    if (!states.has(id)) states.set(id, { messages: [], loaded: false, loading: false, streaming: false })
    return states.get(id)!
  }

  function handleError(error: unknown) {
    if (error instanceof BackendUnreachableError) {
      backendDown.value = true
      return
    }
    lastError.value = error instanceof Error ? error.message : String(error)
  }

  async function refreshList() {
    try {
      conversations.value = await api.listConversations()
      backendDown.value = false
      listLoaded.value = true
    } catch (e) {
      handleError(e)
    }
  }

  async function loadHistory(id: string) {
    const state = stateOf(id)
    if (state.loaded || state.loading || state.streaming) return
    state.loading = true
    try {
      const history = fromHistory(await api.getHistory(id))
      // A message sent while history was loading wins; the server has it all by now anyway.
      if (!state.streaming && !state.loaded) state.messages = history
      state.loaded = true
    } catch (e) {
      handleError(e)
    } finally {
      state.loading = false
    }
  }

  function writeHash(id: string) {
    if (location.hash !== HASH_PREFIX + id) location.hash = HASH_PREFIX + id
  }

  function select(id: string) {
    activeId.value = id
    writeHash(id)
    void loadHistory(id)
  }

  function newChat() {
    const id = crypto.randomUUID()
    stateOf(id).loaded = true // nothing to load for a brand-new id
    select(id)
  }

  async function remove(id: string) {
    try {
      await api.deleteConversation(id)
    } catch (e) {
      handleError(e)
      return
    }
    states.delete(id)
    conversations.value = conversations.value.filter((c) => c.id !== id)
    if (activeId.value === id) newChat()
  }

  /** Sends to the active conversation. Returns false if it was not sent (blank or busy). */
  async function send(text: string): Promise<boolean> {
    const id = activeId.value
    const state = stateOf(id)
    if (!text.trim() || state.streaming) return false

    state.messages.push({ role: 'user', text })
    state.messages.push(emptyAssistantMessage())
    const index = state.messages.length - 1
    const update = (event: Parameters<typeof applyEvent>[1]) => {
      state.messages[index] = applyEvent(state.messages[index] as AssistantMessage, event)
    }

    state.streaming = true
    try {
      await api.sendMessage(id, text, update, {
        onOpen: () => {
          backendDown.value = false
          void refreshList() // the new title shows up in the sidebar right away
        },
      })
    } catch (e) {
      if (e instanceof BackendUnreachableError) backendDown.value = true
      update({ type: 'error', message: e instanceof BackendUnreachableError ? 'The agent backend is unreachable.' : (e as Error).message })
    } finally {
      state.streaming = false
      state.loaded = true
      void refreshList()
    }
    return true
  }

  /** Re-checks the backend after it was unreachable. */
  async function retry() {
    try {
      await api.checkHealth()
      backendDown.value = false
    } catch (e) {
      handleError(e)
      return
    }
    await refreshList()
    void loadHistory(activeId.value)
  }

  function idFromHash(): string | null {
    const hash = location.hash
    if (!hash.startsWith(HASH_PREFIX)) return null
    const id = decodeURIComponent(hash.slice(HASH_PREFIX.length))
    return /^[A-Za-z0-9_-]{1,100}$/.test(id) ? id : null
  }

  function init() {
    const fromHash = idFromHash()
    if (fromHash) select(fromHash)
    else newChat()
    void refreshList()
  }

  function onHashChange() {
    const id = idFromHash()
    if (id && id !== activeId.value) select(id)
  }

  const active = computed(() => states.get(activeId.value) ?? stateOf(activeId.value))
  const activeTitle = computed(() => conversations.value.find((c) => c.id === activeId.value)?.title ?? 'New chat')

  return {
    // state
    conversations,
    listLoaded,
    backendDown,
    lastError,
    activeId,
    active,
    activeTitle,
    states,
    // actions
    init,
    onHashChange,
    refreshList,
    select,
    newChat,
    remove,
    send,
    retry,
  }
}

export type ChatStore = ReturnType<typeof createChatStore>

let store: ChatStore | null = null

function appStore(): ChatStore {
  store ??= createChatStore()
  return store
}

export function useConversations() {
  const s = appStore()
  return {
    conversations: s.conversations,
    listLoaded: s.listLoaded,
    activeId: s.activeId,
    activeTitle: s.activeTitle,
    backendDown: s.backendDown,
    init: s.init,
    onHashChange: s.onHashChange,
    select: s.select,
    newChat: s.newChat,
    remove: s.remove,
    retry: s.retry,
    isStreaming: (id: string) => s.states.get(id)?.streaming ?? false,
  }
}

export function useChat() {
  const s = appStore()
  return {
    active: s.active,
    activeId: s.activeId,
    send: s.send,
    backendDown: s.backendDown,
  }
}
