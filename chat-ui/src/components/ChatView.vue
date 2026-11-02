<script setup lang="ts">
import { nextTick, useTemplateRef, watch } from 'vue'
import type { ChatMessage } from '../api/types'
import AssistantMessage from './AssistantMessage.vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ messages: ChatMessage[]; loading: boolean; conversationId: string }>()
const emit = defineEmits<{ suggest: [text: string] }>()

const SUGGESTIONS = [
  { text: 'What is 1234 * 5678?', hint: 'Calculator' },
  { text: 'What time is it right now?', hint: 'Date & time' },
  { text: 'What is 365 * 24, and how many hours is that per week?', hint: 'Multi-step' },
  { text: 'Use the calculator to divide 10 by 0.', hint: 'Tool error' },
]

const scroller = useTemplateRef<HTMLElement>('scroller')
let stickToBottom = true

function onScroll() {
  const el = scroller.value
  if (el) stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

async function scrollToBottom(force = false) {
  await nextTick()
  const el = scroller.value
  if (el && (force || stickToBottom)) el.scrollTop = el.scrollHeight
}

// Follow the stream while the user is at the bottom; jump down on switch or own message.
watch(
  () => props.conversationId,
  () => {
    stickToBottom = true
    void scrollToBottom(true)
  },
)
watch(
  () => props.messages.length,
  () => {
    if (props.messages.at(-1)?.role === 'assistant' && props.messages.at(-2)?.role === 'user') stickToBottom = true
    void scrollToBottom()
  },
)
watch(() => JSON.stringify(props.messages.at(-1) ?? null).length, () => void scrollToBottom())
</script>

<template>
  <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto" @scroll.passive="onScroll">
    <div class="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6">
      <div v-if="loading && messages.length === 0" class="flex flex-col gap-4" aria-label="Loading conversation">
        <div class="skeleton ml-auto h-10 w-1/2"></div>
        <div class="skeleton h-20 w-3/4"></div>
      </div>

      <section v-else-if="messages.length === 0" class="flex flex-col items-center gap-6 py-10 text-center sm:py-16">
        <div class="grid size-14 place-items-center rounded-2xl bg-primary text-primary-content">
          <AppIcon name="spark" :size="28" />
        </div>
        <div class="space-y-2">
          <h2 class="text-2xl font-semibold tracking-tight">Watch an agent think with tools</h2>
          <p class="mx-auto max-w-md text-base-content/70">
            Ask something. Each time the agent calls a tool, the call shows up in its reply and on the
            activity timeline, so you can see what it did and what it got back.
          </p>
        </div>
        <div class="grid w-full max-w-xl grid-cols-1 gap-2 sm:grid-cols-2">
          <button
            v-for="s in SUGGESTIONS"
            :key="s.text"
            type="button"
            class="card card-border cursor-pointer bg-base-100 p-3 text-left transition hover:border-primary hover:bg-base-200"
            @click="emit('suggest', s.text)"
          >
            <span class="text-xs font-medium uppercase tracking-wide text-base-content/70">{{ s.hint }}</span>
            <span class="text-sm">{{ s.text }}</span>
          </button>
        </div>
      </section>

      <template v-for="(message, i) in messages" :key="i">
        <div v-if="message.role === 'user'" class="chat chat-end">
          <div class="chat-bubble bg-base-300 text-base-content whitespace-pre-wrap">{{ message.text }}</div>
        </div>
        <AssistantMessage v-else :message="message" />
      </template>
    </div>
  </div>
</template>
