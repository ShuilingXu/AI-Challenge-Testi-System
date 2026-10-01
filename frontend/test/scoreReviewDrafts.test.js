import test from 'node:test'
import assert from 'node:assert/strict'
import { updateReviewDrafts } from '../src/utils/scoreReviewDrafts.js'

test('saving one review preserves other scores and notes in progress', () => {
  const drafts = { 1: { score: 85, note: '保存此题' }, 2: { score: 90, note: '尚未保存的评语' } }
  updateReviewDrafts(drafts, [{ id: 1, averageScore: 85, teacherNote: '保存此题' }, { id: 2, averageScore: 50 }], 1)
  assert.deepEqual(drafts[2], { score: 90, note: '尚未保存的评语' })
  updateReviewDrafts(drafts, [{ id: 1, averageScore: 30 }])
  assert.deepEqual(drafts[1], { score: 30, note: '' })
})
