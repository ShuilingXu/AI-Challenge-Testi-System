import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const examView = readFileSync(new URL('../src/views/ExamTakeView.vue', import.meta.url), 'utf8')

test('exam requires fullscreen and blocks common local bypass controls', () => {
  assert.match(examView, /requestFullscreen\(\)/)
  assert.match(examView, /contextmenu/)
  assert.match(examView, /event\.key === 'F12'/)
  assert.match(examView, /event\.key === 'F10'/)
  assert.match(examView, /checkDevtools/)
  assert.match(examView, /DEVTOOLS_OPEN/)
  assert.match(examView, /stopImmediatePropagation\(\)/)
  assert.match(examView, /selectstart/)
  assert.match(examView, /beforeunload/)
})

test('exam explains the configured immediate action without approval wording', () => {
  assert.match(examView, /立即交卷或进入下一阶段/)
  assert.doesNotMatch(examView, /人工复核/)
})
