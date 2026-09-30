import test from 'node:test'
import assert from 'node:assert/strict'
import { answerForQuestion } from '../src/utils/examAnswer.js'

test('reload restores a failed answer exactly for an idempotent retry', () => {
  assert.equal(answerForQuestion(null, { id: 1, answerStatus: 'FAILED', answerContent: 'original answer' }, ''), 'original answer')
})

test('polling preserves the current draft and new questions clear it', () => {
  assert.equal(answerForQuestion({ id: 1 }, { id: 1 }, 'draft'), 'draft')
  assert.equal(answerForQuestion({ id: 1 }, null, 'draft'), 'draft')
  assert.equal(answerForQuestion({ id: 1 }, { id: 2 }, 'draft'), '')
})
