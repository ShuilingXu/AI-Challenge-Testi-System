import assert from 'node:assert/strict'
import test from 'node:test'
import { buildInterviewDecision } from '../src/utils/interviewDecision.js'

test('详情 A 的入职草稿不会污染列表中流程 B 的审批', () => {
  const result = buildInterviewDecision({
    process: { id: 202, jobId: 22, jobDepartmentId: 12 },
    approved: 1,
    finalApproval: true,
    detailProcessId: 101,
    draft: { departmentId: 11, jobId: 21, baseSalary: 18000 },
  })

  assert.deepEqual(result, { payload: null, missing: 'jobAndSalary' })
})

test('仅当前详情流程可以使用自己的入职草稿', () => {
  const result = buildInterviewDecision({
    process: { id: 202, jobDepartmentId: null },
    approved: 1,
    finalApproval: true,
    detailProcessId: 202,
    draft: { departmentId: 12, jobId: 22, baseSalary: 16000 },
  })

  assert.deepEqual(result, {
    payload: { approved: 1, departmentId: 12, jobId: 22, baseSalary: 16000 },
    missing: null,
  })
})
