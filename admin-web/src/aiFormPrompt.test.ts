import { describe, expect, it } from 'vitest'
import promptDoc from '../../docs/AI_FORM_PROMPT.md?raw'
import { AI_FORM_PROMPT } from './aiFormPrompt'

describe('AI 폼 프롬프트', () => {
  it('docs/AI_FORM_PROMPT.md의 프롬프트 블록과 글자 하나까지 같다', () => {
    const block = /```text\n([\s\S]*?)\n```/.exec(promptDoc)
    expect(block).not.toBeNull()
    expect(AI_FORM_PROMPT).toBe(block![1])
  })
})
