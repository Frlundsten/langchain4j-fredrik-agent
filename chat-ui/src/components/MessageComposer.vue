<script setup lang="ts">
import { nextTick, useTemplateRef, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ disabled: boolean }>()
const emit = defineEmits<{ send: [text: string] }>()
const text = defineModel<string>({ default: '' })

const input = useTemplateRef<HTMLTextAreaElement>('input')

function resize() {
  const el = input.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 200)}px`
}

function submit() {
  if (props.disabled || !text.value.trim()) return
  emit('send', text.value)
  text.value = ''
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}

watch(text, () => nextTick(resize))
// Give focus back once the reply is finished.
watch(
  () => props.disabled,
  (disabled) => {
    if (!disabled) void nextTick(() => input.value?.focus())
  },
)

defineExpose({ focus: () => input.value?.focus() })
</script>

<template>
  <form class="mx-auto w-full max-w-3xl px-4 pb-4 sm:px-6" @submit.prevent="submit">
    <div
      class="flex items-end gap-2 rounded-box border border-base-300 bg-base-100 p-2 shadow-sm transition focus-within:border-primary"
    >
      <textarea
        ref="input"
        v-model="text"
        rows="1"
        class="textarea textarea-ghost min-h-0 flex-1 resize-none border-0 bg-transparent px-2 py-2 text-base leading-6 focus:outline-none"
        :placeholder="disabled ? 'The agent is answering…' : 'Ask the agent something…'"
        :disabled="disabled"
        aria-label="Message"
        @keydown="onKeydown"
      ></textarea>
      <button
        type="submit"
        class="btn btn-primary btn-square"
        :disabled="disabled || !text.trim()"
        aria-label="Send message"
      >
        <span v-if="disabled" class="loading loading-spinner loading-sm"></span>
        <AppIcon v-else name="send" />
      </button>
    </div>
    <p class="mt-2 hidden text-center text-xs text-base-content/70 sm:block">
      <kbd class="kbd kbd-xs">Enter</kbd> to send · <kbd class="kbd kbd-xs">Shift</kbd> +
      <kbd class="kbd kbd-xs">Enter</kbd> for a new line
    </p>
  </form>
</template>
