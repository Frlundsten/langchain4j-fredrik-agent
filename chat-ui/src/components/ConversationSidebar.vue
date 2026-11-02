<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'
import type { ConversationSummary } from '../api/types'
import AppIcon from './AppIcon.vue'

const props = defineProps<{
  conversations: ConversationSummary[]
  activeId: string
  loaded: boolean
  isStreaming: (id: string) => boolean
}>()
const emit = defineEmits<{ select: [id: string]; create: []; remove: [id: string] }>()

const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const pending = ref<ConversationSummary | null>(null)

function askRemove(conversation: ConversationSummary) {
  pending.value = conversation
  dialog.value?.showModal()
}

function confirmRemove() {
  if (pending.value) emit('remove', pending.value.id)
  pending.value = null
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
function ago(iso: string): string {
  const time = Date.parse(iso)
  if (Number.isNaN(time)) return ''
  const seconds = Math.round((time - Date.now()) / 1000)
  const steps: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ]
  for (const [unit, size] of steps) {
    if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit)
  }
  return 'just now'
}

const isActive = (id: string) => id === props.activeId
</script>

<template>
  <nav class="flex h-full w-72 flex-col bg-base-200" aria-label="Conversations">
    <div class="flex items-center gap-2.5 px-4 pt-4 pb-3">
      <div class="grid size-8 place-items-center rounded-lg bg-primary font-mono text-sm font-bold text-primary-content">›_</div>
      <div class="leading-tight">
        <div class="font-semibold tracking-tight">Agent Console</div>
        <div class="text-xs text-base-content/70">see how your agent works</div>
      </div>
    </div>

    <div class="px-3 pb-2">
      <button type="button" class="btn btn-primary w-full justify-start gap-2" @click="emit('create')">
        <AppIcon name="plus" /> New chat
      </button>
    </div>

    <div class="flex-1 overflow-y-auto px-1 pb-4">
      <p class="px-4 pt-3 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/70">Recent</p>
      <div v-if="!loaded" class="flex flex-col gap-2 px-3 pt-1">
        <div v-for="n in 4" :key="n" class="skeleton h-9 w-full"></div>
      </div>
      <p v-else-if="conversations.length === 0" class="px-4 py-2 text-sm text-base-content/70">
        No conversations yet.
      </p>
      <ul v-else class="menu w-full gap-0.5">
        <li v-for="c in conversations" :key="c.id" class="group">
          <div class="flex items-center gap-1 p-0 pr-1" :class="isActive(c.id) ? 'bg-base-300 font-medium' : ''">
            <button
              type="button"
              class="min-w-0 flex-1 cursor-pointer px-3 py-1.5 text-left"
              :aria-current="isActive(c.id) ? 'page' : undefined"
              @click="emit('select', c.id)"
            >
              <span class="block truncate text-sm">{{ c.title }}</span>
              <span class="block text-xs opacity-75">
                <span v-if="isStreaming(c.id)" class="inline-flex items-center gap-1">
                  <span class="status status-warning animate-pulse"></span> answering…
                </span>
                <span v-else>{{ ago(c.updatedAt) }}</span>
              </span>
            </button>
            <button
              type="button"
              class="btn btn-ghost btn-xs btn-square opacity-100 lg:opacity-0 lg:group-hover:opacity-100 focus:opacity-100"
              :aria-label="`Delete conversation ${c.title}`"
              @click.stop="askRemove(c)"
            >
              <AppIcon name="trash" :size="14" />
            </button>
          </div>
        </li>
      </ul>
    </div>

    <dialog ref="dialog" class="modal" @close="pending = null">
      <div class="modal-box max-w-sm">
        <h3 class="text-lg font-semibold">Delete conversation?</h3>
        <p class="py-3 text-sm text-base-content/70">
          “{{ pending?.title }}” and its history will be removed for good.
        </p>
        <form method="dialog" class="modal-action">
          <button class="btn btn-ghost">Cancel</button>
          <button class="btn btn-error" @click="confirmRemove">Delete</button>
        </form>
      </div>
      <form method="dialog" class="modal-backdrop"><button>close</button></form>
    </dialog>
  </nav>
</template>
