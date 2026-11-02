<script setup lang="ts">
import { computed } from 'vue'
import type { ToolPart } from '../api/types'
import { formatDuration, summarizeArguments, toolLook, truncate } from '../lib/tools'
import { useToolHighlight } from '../composables/useToolHighlight'
import AppIcon from './AppIcon.vue'

// Inline marker in the answer, at the point in the reply where the agent called the tool.
const props = defineProps<{ call: ToolPart }>()

const { isLinked, hover, reveal } = useToolHighlight()
const look = computed(() => toolLook(props.call.name))
const args = computed(() => summarizeArguments(props.call.name, props.call.arguments))
const result = computed(() => truncate(props.call.result ?? '', 40))
</script>

<template>
  <button
    type="button"
    class="tool-chip my-1 inline-flex max-w-full items-center gap-2 rounded-field px-2.5 py-1 text-left font-mono text-[0.8rem] leading-5 cursor-pointer"
    :class="{ 'tool-linked': isLinked(call.id) }"
    :style="{ '--tool': look.color }"
    :data-tool-id="call.id"
    data-tool-anchor="marker"
    :title="`${look.label} call. Click to show it in the activity panel.`"
    @mouseenter="hover(call.id)"
    @mouseleave="hover(null)"
    @focus="hover(call.id)"
    @blur="hover(null)"
    @click="reveal(call.id, 'card')"
  >
    <span class="grid size-5 place-items-center rounded-full tool-dot text-base-100">
      <AppIcon :name="look.icon" :size="12" />
    </span>
    <span class="whitespace-nowrap font-sans font-semibold">{{ look.label }}</span>
    <span v-if="args" class="truncate opacity-80">{{ args }}</span>
    <template v-if="call.status === 'running'">
      <span class="loading loading-dots loading-xs" aria-label="running"></span>
    </template>
    <template v-else-if="call.result != null">
      <span class="opacity-50">→</span>
      <span class="truncate font-semibold" :class="{ 'text-error': call.status === 'error' }">{{ result }}</span>
      <span v-if="call.durationMs != null" class="hidden opacity-70 sm:inline">{{ formatDuration(call.durationMs) }}</span>
    </template>
  </button>
</template>
