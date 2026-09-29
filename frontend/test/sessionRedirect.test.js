import test from 'node:test'
import assert from 'node:assert/strict'
import { sessionRedirect } from '../src/utils/sessionRedirect.js'

test('未改密用户删除URL、重新登录或访问注册页都回到改密页', () => {
  for (const name of [undefined, 'login', 'student-register', 'student-exams', 'exam-take']) {
    assert.equal(sessionRedirect({ name }, { roleCode: 'STUDENT', mustChangePassword: '1' }), '/change-password')
  }
})
test('改密页不循环跳转，已改密或未登录用户不被强制跳转', () => {
  assert.equal(sessionRedirect({ name: 'change-password' }, { mustChangePassword: 1 }), null)
  assert.equal(sessionRedirect({ name: 'student-exams' }, { mustChangePassword: 0 }), null)
  assert.equal(sessionRedirect({ name: 'login' }, null), null)
})
