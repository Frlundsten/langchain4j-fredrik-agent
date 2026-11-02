<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantMessage, ToolPart } from '../api/types'
import type { HighlightTarget } from '../lib/highlight'
import { toolLook } from '../lib/tools'
import AnswerText from './AnswerText.vue'
import ToolMarker from './ToolMarker.vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ message: AssistantMessage }>()

const targets = computed<HighlightTarget[]>(() =>
  props.message.parts
    .filter((p): p is ToolPart => p.type === 'tool' && p.status === 'done' && p.result != null)
    .map((p) => ({ id: p.id, result: p.result!, color: toolLook(p.name).color })),
)

const streaming = computed(() => props.message.status === 'streaming')
const hasText = computed(() => props.message.parts.some((p) => p.type === 'text' && p.text.trim()))
const last = computed(() => props.message.parts.at(-1))
/** While streaming, the agent is "thinking" whenever it isn't writing text right now. */
const working = computed(() => streaming.value && last.value?.type !== 'text')
const workingLabel = computed(() => {
  if (!last.value) return 'Thinking…'
  if (last.value.type === 'tool' && last.value.status === 'running') return `Using ${toolLook(last.value.name).label}…`
  return 'Reading the result…'
})
</script>

<template>
  <article class="flex gap-3">
    <div
      class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full bg-primary text-primary-content"
      aria-hidden="true"
    >
      <AppIcon name="spark" :size="16" />
    </div>
    <div class="min-w-0 flex-1 pt-1">
      <template v-for="(part, i) in message.parts" :key="part.type === 'tool' ? part.id : `text-${i}`">
        <div v-if="part.type === 'tool'"><ToolMarker :call="part" /></div>
        <AnswerText
          v-else
          :text="part.text"
          :targets="targets"
          :streaming="streaming && i === message.parts.length - 1"
          class="my-1"
        />
      </template>

      <div v-if="working" class="flex items-center gap-2 py-1 text-sm text-base-content/70">
        <span class="loading loading-dots loading-sm"></span>
        <span>{{ workingLabel }}</span>
      </div>

      <p v-if="message.status === 'done' && !hasText" class="py-1 text-sm italic text-base-content/70">
        The agent finished without writing an answer.
      </p>

      <div v-if="message.status === 'error'" role="alert" class="alert alert-error alert-soft mt-2 py-2 text-sm">
        <AppIcon name="alert" />
        <span>{{ message.error || 'Something went wrong.' }} You can send your message again.</span>
      </div>
    </div>
  </article>
</template>
