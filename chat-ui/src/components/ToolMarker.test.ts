import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ToolMarker from './ToolMarker.vue'
import { useToolHighlight } from '../composables/useToolHighlight'
import type { ToolPart } from '../api/types'

const call: ToolPart = {
  type: 'tool',
  id: 't1',
  name: 'calculate',
  arguments: { a: 1234, operator: '*', b: 5678 },
  result: '7006652',
  durationMs: 3,
  status: 'done',
}

describe('ToolMarker', () => {
  it('shows the tool, short arguments and result, linked by id', () => {
    const wrapper = mount(ToolMarker, { props: { call } })
    const text = wrapper.text()
    expect(text).toContain('Calculator')
    expect(text).toContain('1234 × 5678')
    expect(text).toContain('7006652')
    expect(wrapper.attributes('data-tool-id')).toBe('t1')
    expect(wrapper.attributes('data-tool-anchor')).toBe('marker')
    expect(wrapper.attributes('style')).toContain('--tool: var(--tool-amber)')
  })

  it('shows a running state without a result', () => {
    const wrapper = mount(ToolMarker, { props: { call: { ...call, status: 'running', result: undefined } } })
    expect(wrapper.find('[aria-label="running"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('→')
  })

  it('highlights when the same call is hovered elsewhere', async () => {
    const wrapper = mount(ToolMarker, { props: { call } })
    const { hover } = useToolHighlight()
    hover('t1')
    await wrapper.vm.$nextTick()
    expect(wrapper.classes()).toContain('tool-linked')
    hover(null)
    await wrapper.vm.$nextTick()
    expect(wrapper.classes()).not.toContain('tool-linked')
  })
})
