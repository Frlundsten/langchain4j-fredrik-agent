import { nextTick, ref } from 'vue'
import { isWide, useLayout } from './useLayout'

export type ToolAnchor = 'marker' | 'card'

/** Links one tool call across its inline marker, highlighted result spans and panel card. */
const hoveredId = ref<string | null>(null)
const selectedId = ref<string | null>(null)

function find(anchor: ToolAnchor, id: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-tool-anchor="${anchor}"][data-tool-id="${CSS.escape(id)}"]`)
}

function flash(el: HTMLElement) {
  el.classList.remove('tool-flash')
  void el.offsetWidth // restart the animation
  el.classList.add('tool-flash')
}

export function useToolHighlight() {
  const { toolsOpen } = useLayout()

  const isLinked = (id: string) => hoveredId.value === id || selectedId.value === id

  function hover(id: string | null) {
    hoveredId.value = id
  }

  /** Selects a call and scrolls its counterpart (the other side of the screen) into view. */
  async function reveal(id: string, target: ToolAnchor) {
    selectedId.value = id
    if (target === 'card' && !isWide('(min-width: 1280px)')) toolsOpen.value = true
    await nextTick()
    const el = find(target, id)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    flash(el)
  }

  function clearSelection() {
    selectedId.value = null
  }

  return { hoveredId, selectedId, isLinked, hover, reveal, clearSelection }
}
