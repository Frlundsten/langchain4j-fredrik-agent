<script setup lang="ts">
import { computed, nextTick, useTemplateRef, watch } from 'vue'
import type { ChatMessage } from '../api/types'
import { toolActivity } from '../chat/reducer'
import { toolLook, truncate } from '../lib/tools'
import ToolCard from './ToolCard.vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ messages: ChatMessage[] }>()
const emit = defineEmits<{ close: [] }>()

const activity = computed(() => toolActivity(props.messages))
const running = computed(() => activity.value.filter((a) => a.call.status === 'running').length)

/** Group consecutive steps by the question they answered. */
const groups = computed(() => {
  const out: { question: string; items: typeof activity.value }[] = []
  for (const item of activity.value) {
    const group = out.at(-1)
    if (group && group.question === item.question) group.items.push(item)
    else out.push({ question: item.question, items: [item] })
  }
  return out
})

const list = useTemplateRef<HTMLElement>('list')
watch(
  () => activity.value.length,
  async (now, before) => {
    if (now <= (before ?? 0)) return
    await nextTick()
    list.value?.scrollTo({ top: list.value.scrollHeight, behavior: 'smooth' })
  },
)
</script>

<template>
  <aside class="flex h-full flex-col bg-base-200" aria-label="Agent activity">
    <header class="flex items-center gap-2 border-b border-base-300 px-4 py-3">
      <AppIcon name="activity" class="text-primary" />
      <h2 class="font-semibold">Agent activity</h2>
      <span class="badge badge-sm badge-ghost">{{ activity.length }}</span>
      <span v-if="running" class="status status-warning animate-pulse" aria-label="tool running"></span>
      <button type="button" class="btn btn-ghost btn-sm btn-square ml-auto xl:hidden" aria-label="Close panel" @click="emit('close')">
        ✕
      </button>
    </header>

    <div v-if="activity.length === 0" class="flex flex-1 flex-col items-center justify-center gap-3 px-8 text-center">
      <div class="grid size-12 place-items-center rounded-full bg-base-300 text-base-content/70">
        <AppIcon name="wrench" :size="22" />
      </div>
      <p class="font-medium">No tool calls yet</p>
      <p class="text-sm text-base-content/70">
        When the agent uses a tool (the calculator or the clock), each call shows up here as a step:
        what went in, what came out, and how long it took.
      </p>
    </div>

    <div v-else ref="list" class="flex-1 overflow-y-auto px-3 py-4">
      <section v-for="(group, g) in groups" :key="g" class="mb-4">
        <p class="mb-2 flex items-center gap-1.5 px-1 text-xs text-base-content/70">
          <AppIcon name="chat" :size="12" />
          <span class="truncate" :title="group.question">{{ truncate(group.question, 70) || 'Earlier message' }}</span>
        </p>
        <!-- A trace rail: one dot per step, in the order the agent took them. -->
        <ol class="ml-2.5 flex flex-col gap-3 border-l-2 border-base-300 pl-4">
          <li v-for="item in group.items" :key="item.call.id" class="relative min-w-0">
            <span
              class="tool-dot absolute top-4 -left-[1.4rem] block size-3 rounded-full ring-4 ring-base-200"
              :class="{ 'animate-pulse': item.call.status === 'running' }"
              :style="{ '--tool': toolLook(item.call.name).color }"
              aria-hidden="true"
            ></span>
            <ToolCard :call="item.call" :step="item.step" />
          </li>
        </ol>
      </section>
    </div>
  </aside>
</template>
