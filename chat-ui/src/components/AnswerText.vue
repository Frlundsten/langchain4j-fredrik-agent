<script setup lang="ts">
import { computed, onMounted, useTemplateRef, watch } from 'vue'
import { highlightResults, type HighlightTarget } from '../lib/highlight'
import { renderMarkdown } from '../lib/markdown'
import { useToolHighlight } from '../composables/useToolHighlight'

// Markdown answer text with tool results marked. The HTML is set by hand (not v-html) so the
// highlighting pass can run on the fresh DOM every time the text or the tool results change.
const props = defineProps<{ text: string; targets: HighlightTarget[]; streaming?: boolean }>()

const root = useTemplateRef<HTMLDivElement>('root')
const { hoveredId, selectedId, isLinked, hover, reveal } = useToolHighlight()

const html = computed(() => renderMarkdown(props.text))
const targetsKey = computed(() => props.targets.map((t) => `${t.id}=${t.result}`).join('|'))

function syncLinked() {
  root.value?.querySelectorAll<HTMLElement>('mark[data-tool-id]').forEach((mark) => {
    mark.classList.toggle('is-linked', isLinked(mark.dataset.toolId!))
  })
}

function render() {
  if (!root.value) return
  root.value.innerHTML = html.value
  highlightResults(root.value, props.targets)
  syncLinked()
}

onMounted(render)
watch([html, targetsKey], render, { flush: 'post' })
watch([hoveredId, selectedId], syncLinked)

const markOf = (event: Event) => (event.target as HTMLElement).closest<HTMLElement>('mark[data-tool-id]')

function onOver(event: Event) {
  const mark = markOf(event)
  if (mark) hover(mark.dataset.toolId!)
}
function onOut(event: Event) {
  if (markOf(event)) hover(null)
}
function onClick(event: Event) {
  const mark = markOf(event)
  if (mark) void reveal(mark.dataset.toolId!, 'card')
}
</script>

<template>
  <div
    ref="root"
    class="md"
    :class="{ 'streaming-caret': streaming }"
    @mouseover="onOver"
    @mouseout="onOut"
    @click="onClick"
  ></div>
</template>
