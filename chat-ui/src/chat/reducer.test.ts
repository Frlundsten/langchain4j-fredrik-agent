import { describe, expect, it } from 'vitest'
import type { AssistantMessage, StreamEvent } from '../api/types'
import { applyEvent, emptyAssistantMessage, fromHistory, toolActivity } from './reducer'

const run = (events: StreamEvent[]): AssistantMessage => events.reduce(applyEvent, emptyAssistantMessage())

const calcCall: StreamEvent = { type: 'tool_call', id: 't1', name: 'calculate', arguments: { a: 1234, operator: '*', b: 5678 } }
const calcResult: StreamEvent = { type: 'tool_result', id: 't1', result: '7006652', durationMs: 3 }

describe('applyEvent', () => {
  it('concatenates tokens into one text part', () => {
    const m = run([
      { type: 'token', text: 'Hel' },
      { type: 'token', text: 'lo' },
      { type: 'done' },
    ])
    expect(m).toEqual({ role: 'assistant', status: 'done', parts: [{ type: 'text', text: 'Hello' }] })
  })

  it('keeps a tool call between two text parts, in order', () => {
    const m = run([{ type: 'token', text: 'Let me check. ' }, calcCall, calcResult, { type: 'token', text: 'It is 7006652.' }, { type: 'done' }])
    expect(m.parts.map((p) => p.type)).toEqual(['text', 'tool', 'text'])
    expect(m.parts[1]).toEqual({
      type: 'tool',
      id: 't1',
      name: 'calculate',
      arguments: { a: 1234, operator: '*', b: 5678 },
      result: '7006652',
      durationMs: 3,
      status: 'done',
    })
  })

  it('shows a tool as running until its result arrives', () => {
    const m = run([calcCall])
    expect(m.parts[0]).toMatchObject({ status: 'running' })
    expect(m.status).toBe('streaming')
  })

  it('pairs two tool results by id', () => {
    const m = run([
      { type: 'tool_call', id: 'a', name: 'currentDateTime', arguments: {} },
      { type: 'tool_result', id: 'a', result: '2026-11-02T21:00:00+01:00', durationMs: 1 },
      { type: 'tool_call', id: 'b', name: 'calculate', arguments: { a: 2, operator: '+', b: 2 } },
      { type: 'tool_result', id: 'b', result: '4', durationMs: 1 },
      { type: 'done' },
    ])
    expect(m.parts.map((p) => (p.type === 'tool' ? `${p.name}=${p.result}` : p.type))).toEqual([
      'currentDateTime=2026-11-02T21:00:00+01:00',
      'calculate=4',
    ])
  })

  it('marks tool error results as failed', () => {
    const m = run([
      { type: 'tool_call', id: 't1', name: 'calculate', arguments: { a: 1, operator: '/', b: 0 } },
      { type: 'tool_result', id: 't1', result: 'Error: division by zero', durationMs: 0 },
    ])
    expect(m.parts[0]).toMatchObject({ status: 'error' })
  })

  it('ends with an error mid-stream and fails running tools', () => {
    const m = run([{ type: 'token', text: 'Part' }, calcCall, { type: 'error', message: 'model went away' }])
    expect(m.status).toBe('error')
    expect(m.error).toBe('model went away')
    expect(m.parts[1]).toMatchObject({ status: 'error' })
  })

  it('ignores events after the reply ended', () => {
    const m = run([{ type: 'done' }, { type: 'token', text: 'late' }])
    expect(m.parts).toEqual([])
  })
})

describe('fromHistory', () => {
  it('produces the same shape as a streamed reply', () => {
    const streamed = run([calcCall, calcResult, { type: 'token', text: 'It is 7006652.' }, { type: 'done' }])
    const [user, loaded] = fromHistory([
      { role: 'user', text: 'What is 1234 * 5678?' },
      {
        role: 'assistant',
        parts: [
          { type: 'tool', id: 't1', name: 'calculate', arguments: { a: 1234, operator: '*', b: 5678 }, result: '7006652' },
          { type: 'text', text: 'It is 7006652.' },
        ],
      },
    ])
    expect(user).toEqual({ role: 'user', text: 'What is 1234 * 5678?' })
    // History has no durations; everything else matches.
    const withoutDuration = {
      ...streamed,
      parts: streamed.parts.map((p) => (p.type === 'tool' ? { ...p, durationMs: undefined } : p)),
    }
    expect(JSON.parse(JSON.stringify(loaded))).toEqual(JSON.parse(JSON.stringify(withoutDuration)))
  })
})

describe('toolActivity', () => {
  it('numbers calls across the conversation and remembers the question', () => {
    const reply = run([calcCall, calcResult, { type: 'done' }])
    const activity = toolActivity([{ role: 'user', text: 'Q1' }, reply, { role: 'user', text: 'Q2' }, reply])
    expect(activity.map((a) => [a.step, a.question])).toEqual([
      [1, 'Q1'],
      [2, 'Q2'],
    ])
  })
})
