import { describe, expect, it } from 'vitest'
import { highlightResults, resultPattern } from './highlight'
import { renderMarkdown } from './markdown'

function highlight(html: string, results: Record<string, string>): HTMLElement {
  const root = document.createElement('div')
  root.innerHTML = html
  highlightResults(
    root,
    Object.entries(results).map(([id, result]) => ({ id, result, color: 'red' })),
  )
  return root
}

const marks = (root: HTMLElement) => [...root.querySelectorAll('mark')].map((m) => [m.dataset.toolId, m.textContent])

describe('highlightResults', () => {
  it('wraps a literal result and links it to the call', () => {
    const root = highlight('<p>1234 × 5678 = 7006652</p>', { t1: '7006652' })
    expect(marks(root)).toEqual([['t1', '7006652']])
    expect(root.querySelector('mark')!.style.getPropertyValue('--tool')).toBe('red')
  })

  it('does not light up single-character results', () => {
    const root = highlight('<p>There is 1 answer: 1.</p>', { t1: '1' })
    expect(marks(root)).toEqual([])
  })

  it('matches numbers written with thousands separators', () => {
    const root = highlight('<p>is 7,006,652. Also 7 006 652 or 7006652</p>', { t1: '7006652' })
    expect(marks(root).map((m) => m[1])).toEqual(['7,006,652', '7 006 652', '7006652'])
  })

  it('never matches inside a longer number', () => {
    const root = highlight('<p>17006652 and 70066521 and 7006652.5</p>', { t1: '7006652' })
    expect(marks(root)).toEqual([])
  })

  it('skips code and keeps surrounding markup', () => {
    const root = highlight(renderMarkdown('**Result: 42** and `42`'), { t1: '42' })
    expect(marks(root)).toEqual([['t1', '42']])
    expect(root.querySelector('strong mark')).not.toBeNull()
    expect(root.querySelector('code mark')).toBeNull()
  })

  it('ignores tool errors', () => {
    expect(resultPattern('Error: division by zero')).toBeNull()
  })

  it('treats result text as text, not HTML', () => {
    const root = highlight('<p>value &lt;b&gt;x&lt;/b&gt;</p>', { t1: '<b>x</b>' })
    expect(root.querySelector('b')).toBeNull()
    expect(marks(root)).toEqual([['t1', '<b>x</b>']])
  })
})

describe('renderMarkdown', () => {
  it('does not render raw HTML from the model', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)> **bold**')
    expect(html).not.toContain('<img')
    expect(html).toContain('<strong>bold</strong>')
  })
})
