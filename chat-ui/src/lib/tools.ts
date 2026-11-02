// How each tool looks: label, color, icon and a one-line summary of its arguments.

export interface ToolLook {
  label: string
  /** CSS color expression, set as --tool on the element. */
  color: string
  icon: 'calculator' | 'clock' | 'wrench'
}

const KNOWN: Record<string, ToolLook> = {
  calculate: { label: 'Calculator', color: 'var(--tool-amber)', icon: 'calculator' },
  currentDateTime: { label: 'Date & time', color: 'var(--tool-violet)', icon: 'clock' },
}

const FALLBACK = ['var(--tool-sky)', 'var(--tool-rose)', 'var(--tool-lime)', 'var(--tool-teal)']

/** Same tool name → same look, also for tools this UI has never heard of. */
export function toolLook(name: string): ToolLook {
  const known = KNOWN[name]
  if (known) return known
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return { label: humanize(name), color: FALLBACK[Math.abs(hash) % FALLBACK.length], icon: 'wrench' }
}

function humanize(name: string): string {
  const words = name.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

const OPERATORS: Record<string, string> = { '*': '×', x: '×', '/': '÷', '-': '−', '+': '+' }

/** Short human form of the arguments, e.g. "1234 × 5678". */
export function summarizeArguments(name: string, args: Record<string, unknown>): string {
  if (name === 'calculate' && 'a' in args && 'b' in args) {
    const op = String(args.operator ?? '?').trim()
    return `${formatValue(args.a)} ${OPERATORS[op] ?? op} ${formatValue(args.b)}`
  }
  const entries = Object.entries(args)
  if (entries.length === 0) return ''
  return truncate(entries.map(([k, v]) => `${k}: ${formatValue(v)}`).join(', '), 48)
}

export function formatValue(value: unknown): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' && Number.isInteger(value)) return String(value)
  return JSON.stringify(value)
}

export function truncate(text: string, max: number): string {
  return text.length <= max ? text : text.slice(0, max - 1).trimEnd() + '…'
}

export function formatDuration(ms: number | undefined): string {
  if (ms == null) return ''
  if (ms < 1) return '<1 ms'
  if (ms < 1000) return `${Math.round(ms)} ms`
  return `${(ms / 1000).toFixed(1)} s`
}
