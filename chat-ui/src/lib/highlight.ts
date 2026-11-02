// Finds tool results repeated in the answer text and wraps them in <mark data-tool-id>.

export interface HighlightTarget {
  id: string
  result: string
  color: string
}

const MIN_LENGTH = 2

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Pattern for a result as it may appear in prose. Numbers may be written with thousands separators
 * ("7006652" as "7,006,652" or "7 006 652") and are never matched inside a longer number.
 */
export function resultPattern(result: string): RegExp | null {
  const value = result.trim()
  if (value.length < MIN_LENGTH || /^error\b/i.test(value)) return null
  const number = /^(-?)(\d+)(\.\d+)?$/.exec(value)
  if (number) {
    const [, sign, int, frac = ''] = number
    let intPattern = ''
    for (let i = 0; i < int.length; i++) {
      const fromEnd = int.length - i
      if (i > 0 && fromEnd % 3 === 0) intPattern += "[,\\u00a0\\u202f' ]?"
      intPattern += int[i]
    }
    const fracPattern = frac ? `[.,]${frac.slice(1)}` : ''
    return new RegExp(`(?<![\\d.,])${escapeRegExp(sign)}${intPattern}${fracPattern}(?![\\d]|[.,]\\d)`, 'g')
  }
  return new RegExp(escapeRegExp(value), 'g')
}

/** Wraps matches inside text nodes of `root` (no HTML re-parsing, so it can't inject markup). */
export function highlightResults(root: ParentNode, targets: HighlightTarget[]): void {
  const patterns = targets
    .map((t) => ({ target: t, pattern: resultPattern(t.result) }))
    .filter((p): p is { target: HighlightTarget; pattern: RegExp } => p.pattern !== null)
    // longer results first, so "7006652" wins over "70"
    .sort((a, b) => b.target.result.length - a.target.result.length)
  if (patterns.length === 0) return

  const doc = (root as Node).ownerDocument ?? document
  const walker = doc.createTreeWalker(root as Node, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) =>
      (node.parentElement?.closest('mark, code, pre') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT),
  })
  const textNodes: Text[] = []
  for (let n = walker.nextNode(); n; n = walker.nextNode()) textNodes.push(n as Text)

  for (const node of textNodes) {
    const text = node.data
    const hits: { start: number; end: number; target: HighlightTarget }[] = []
    for (const { target, pattern } of patterns) {
      pattern.lastIndex = 0
      for (let m = pattern.exec(text); m; m = pattern.exec(text)) {
        const start = m.index
        const end = start + m[0].length
        if (!hits.some((h) => start < h.end && end > h.start)) hits.push({ start, end, target })
      }
    }
    if (hits.length === 0) continue
    hits.sort((a, b) => a.start - b.start)

    const fragment = doc.createDocumentFragment()
    let cursor = 0
    for (const hit of hits) {
      if (hit.start > cursor) fragment.append(text.slice(cursor, hit.start))
      const mark = doc.createElement('mark')
      mark.className = 'tool-hit'
      mark.dataset.toolId = hit.target.id
      mark.style.setProperty('--tool', hit.target.color)
      mark.title = 'Result of a tool call. Click to show it.'
      mark.textContent = text.slice(hit.start, hit.end)
      fragment.append(mark)
      cursor = hit.end
    }
    if (cursor < text.length) fragment.append(text.slice(cursor))
    node.replaceWith(fragment)
  }
}
