<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'
import { useChat, useConversations } from './composables/store'
import { useLayout } from './composables/useLayout'
import { useTheme } from './composables/useTheme'
import { useToolHighlight } from './composables/useToolHighlight'
import { toolActivity } from './chat/reducer'
import ConversationSidebar from './components/ConversationSidebar.vue'
import ChatView from './components/ChatView.vue'
import MessageComposer from './components/MessageComposer.vue'
import ToolPanel from './components/ToolPanel.vue'
import AppIcon from './components/AppIcon.vue'

const conversations = useConversations()
const chat = useChat()
const { navOpen, toolsOpen } = useLayout()
const { isDark, toggle: toggleTheme } = useTheme()
const { clearSelection } = useToolHighlight()

const draft = ref('')
const composer = useTemplateRef<InstanceType<typeof MessageComposer>>('composer')
const retrying = ref(false)

const messages = computed(() => chat.active.value.messages)
const toolCount = computed(() => toolActivity(messages.value).length)

function select(id: string) {
  clearSelection()
  conversations.select(id)
  navOpen.value = false
}

function create() {
  clearSelection()
  conversations.newChat()
  navOpen.value = false
  composer.value?.focus()
}

function send(text: string) {
  void chat.send(text)
}

async function retry() {
  retrying.value = true
  try {
    await conversations.retry()
  } finally {
    retrying.value = false
  }
}

onMounted(() => {
  conversations.init()
  window.addEventListener('hashchange', conversations.onHashChange)
})
onBeforeUnmount(() => window.removeEventListener('hashchange', conversations.onHashChange))
</script>

<template>
  <!-- Outer drawer: conversations (left). Always open from lg (1024 px). -->
  <div class="drawer h-dvh lg:drawer-open">
    <input id="nav-drawer" v-model="navOpen" type="checkbox" class="drawer-toggle" />
    <div class="drawer-content flex h-dvh min-w-0 flex-col">
      <!-- Inner drawer: agent activity (right). Always open from xl (1280 px). -->
      <div class="drawer drawer-end h-full xl:drawer-open">
        <input id="tools-drawer" v-model="toolsOpen" type="checkbox" class="drawer-toggle" />
        <div class="drawer-content flex h-dvh min-w-0 flex-col bg-base-100">
          <header class="flex items-center gap-1 border-b border-base-300 px-2 py-2 sm:px-4">
            <label for="nav-drawer" class="btn btn-ghost btn-square btn-sm lg:hidden" aria-label="Open conversations">
              <AppIcon name="menu" />
            </label>
            <h1 class="min-w-0 flex-1 truncate px-2 font-semibold">{{ conversations.activeTitle.value }}</h1>
            <span
              v-if="chat.active.value.streaming"
              class="hidden items-center gap-1.5 text-xs text-base-content/70 sm:inline-flex"
            >
              <span class="status status-warning animate-pulse"></span> answering
            </span>
            <button
              type="button"
              class="btn btn-ghost btn-square btn-sm"
              :aria-label="isDark ? 'Switch to light theme' : 'Switch to dark theme'"
              :title="isDark ? 'Light theme' : 'Dark theme'"
              @click="toggleTheme"
            >
              <AppIcon :name="isDark ? 'sun' : 'moon'" />
            </button>
            <label for="tools-drawer" class="btn btn-ghost btn-sm gap-1.5 xl:hidden" aria-label="Open agent activity">
              <AppIcon name="activity" />
              <span class="badge badge-sm" :class="toolCount ? 'badge-primary' : 'badge-ghost'">{{ toolCount }}</span>
            </label>
          </header>

          <div
            v-if="conversations.backendDown.value"
            role="alert"
            class="alert alert-warning alert-soft mx-auto mt-3 w-[calc(100%-2rem)] max-w-3xl py-2 text-sm"
          >
            <AppIcon name="alert" />
            <span>
              The agent backend is unreachable. Is the server running?
              <code class="font-mono text-xs">mvn -q compile exec:java@server</code>
            </span>
            <button type="button" class="btn btn-sm" :disabled="retrying" @click="retry">
              <span v-if="retrying" class="loading loading-spinner loading-xs"></span>
              <AppIcon v-else name="refresh" :size="14" />
              Retry
            </button>
          </div>

          <ChatView
            :messages="messages"
            :loading="chat.active.value.loading"
            :conversation-id="chat.activeId.value"
            @suggest="send"
          />
          <MessageComposer ref="composer" v-model="draft" :disabled="chat.active.value.streaming" @send="send" />
        </div>
        <div class="drawer-side z-20 xl:h-dvh">
          <label for="tools-drawer" aria-label="Close agent activity" class="drawer-overlay"></label>
          <div class="h-full w-[min(24rem,90vw)] border-l border-base-300">
            <ToolPanel :messages="messages" @close="toolsOpen = false" />
          </div>
        </div>
      </div>
    </div>
    <div class="drawer-side z-30">
      <label for="nav-drawer" aria-label="Close conversations" class="drawer-overlay"></label>
      <div class="h-full border-r border-base-300">
        <ConversationSidebar
          :conversations="conversations.conversations.value"
          :active-id="conversations.activeId.value"
          :loaded="conversations.listLoaded.value"
          :is-streaming="conversations.isStreaming"
          @select="select"
          @create="create"
          @remove="conversations.remove"
        />
      </div>
    </div>
  </div>
</template>
