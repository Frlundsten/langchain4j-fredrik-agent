import { ref } from 'vue'

/** Drawer state for narrow screens. On wide screens the drawers are always open. */
const navOpen = ref(false)
const toolsOpen = ref(false)

export function useLayout() {
  return { navOpen, toolsOpen }
}

export function isWide(query: string): boolean {
  return typeof matchMedia === 'function' && matchMedia(query).matches
}
