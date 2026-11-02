<script setup lang="ts">
import { computed } from 'vue'
import type { ToolPart } from '../api/types'
import { formatDuration, formatValue, toolLook } from '../lib/tools'
import { useToolHighlight } from '../composables/useToolHighlight'

const props = defineProps<{ call: ToolPart; step: number }>()

const { isLinked, hover, reveal } = useToolHighlight()
const look = computed(() => toolLook(props.call.name))
const args = computed(() => Object.entries(props.call.arguments))
</script>

<template>
  <div
    role="button"
    tabindex="0"
    class="tool-stripe card cursor-pointer border border-base-300 bg-base-100 text-sm transition-shadow"
    :class="{ 'tool-linked': isLinked(call.id) }"
    :style="{ '--tool': look.color }"
    :data-tool-id="call.id"
    data-tool-anchor="card"
    :aria-label="`Step ${step}: ${look.label}. Show in the chat.`"
    @mouseenter="hover(call.id)"
    @mouseleave="hover(null)"
    @focus="hover(call.id)"
    @blur="hover(null)"
    @click="reveal(call.id, 'marker')"
    @keydown.enter.prevent="reveal(call.id, 'marker')"
    @keydown.space.prevent="reveal(call.id, 'marker')"
  >
    <div class="flex flex-col gap-2 p-3">
      <header class="flex items-center gap-2">
        <span class="font-mono text-xs text-base-content/70">#{{ step }}</span>
        <span class="whitespace-nowrap font-semibold">{{ look.label }}</span>
        <code class="min-w-0 truncate font-mono text-xs text-base-content/70">{{ call.name }}</code>
        <span class="ml-auto shrink-0">
          <span v-if="call.status === 'running'" class="badge badge-sm badge-ghost gap-1">
            <span class="loading loading-spinner loading-xs"></span> running
          </span>
          <span v-else-if="call.status === 'error'" class="badge badge-sm badge-error badge-soft">error</span>
          <span v-else class="badge badge-sm badge-success badge-soft">
            done<template v-if="call.durationMs != null"> · {{ formatDuration(call.durationMs) }}</template>
          </span>
        </span>
      </header>

      <div class="rounded-field bg-base-200 px-2.5 py-2 font-mono text-xs leading-5">
        <div class="mb-1 font-sans text-[0.68rem] font-semibold uppercase tracking-wider text-base-content/70">Input</div>
        <div v-if="args.length === 0" class="text-base-content/70">no arguments</div>
        <div v-for="[key, value] in args" :key="key" class="flex gap-2">
          <span class="text-base-content/70">{{ key }}</span>
          <span class="break-all">{{ formatValue(value) }}</span>
        </div>
      </div>

      <div class="rounded-field bg-base-200 px-2.5 py-2 font-mono text-xs leading-5">
        <div class="mb-1 font-sans text-[0.68rem] font-semibold uppercase tracking-wider text-base-content/70">Output</div>
        <div v-if="call.status === 'running'" class="text-base-content/70">waiting for the tool…</div>
        <div v-else-if="call.result == null" class="text-base-content/70">no result</div>
        <div v-else class="break-all font-semibold" :class="{ 'text-error': call.status === 'error' }">{{ call.result }}</div>
      </div>
    </div>
  </div>
</template>
