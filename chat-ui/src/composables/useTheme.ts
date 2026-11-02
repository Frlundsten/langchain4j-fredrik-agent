import { computed, ref } from 'vue'

export type Theme = 'console-light' | 'console-dark'
const KEY = 'chat-ui.theme'

function read(): Theme | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'console-light' || value === 'console-dark' ? value : null
  } catch {
    return null
  }
}

const media = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null
const prefersDark = ref(media?.matches ?? false)
media?.addEventListener('change', (e) => (prefersDark.value = e.matches))

/** null = follow the operating system (daisyUI's prefersdark theme handles that). */
const chosen = ref<Theme | null>(read())

function apply() {
  if (chosen.value) document.documentElement.dataset.theme = chosen.value
  else delete document.documentElement.dataset.theme
}
apply()

export function useTheme() {
  const isDark = computed(() => (chosen.value ? chosen.value === 'console-dark' : prefersDark.value))
  function toggle() {
    chosen.value = isDark.value ? 'console-light' : 'console-dark'
    try {
      localStorage.setItem(KEY, chosen.value)
    } catch {
      // storage blocked: the choice lasts for this page only
    }
    apply()
  }
  return { isDark, toggle }
}
