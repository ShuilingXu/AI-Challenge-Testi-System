import test from 'node:test'
import assert from 'node:assert/strict'
import { requestCollection } from '../src/utils/pagination.js'

test('collections fetch every page beyond 200 and preserve filters', async () => {
  const rows = Array.from({ length: 451 }, (_, id) => ({ id }))
  const calls = []
  const result = await requestCollection(async query => {
    calls.push({ ...query })
    return { data: { items: rows.slice((query.page - 1) * query.pageSize, query.page * query.pageSize), total: rows.length } }
  }, { status: 1 })
  assert.deepEqual(result.data, rows)
  assert.deepEqual(calls.map(query => query.page), [1, 2, 3])
  assert.ok(calls.every(query => query.status === 1))
})

test('explicit page requests do not load other pages', async () => {
  let calls = 0
  const result = await requestCollection(async query => {
    calls++
    assert.equal(query.page, 2)
    return { data: { items: [{ id: 201 }], total: 401 } }
  }, { page: 2 })
  assert.equal(calls, 1)
  assert.equal(result.data.length, 1)
})
