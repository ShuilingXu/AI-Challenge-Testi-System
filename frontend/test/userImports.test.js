import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const staffView = readFileSync(new URL('../src/views/StaffManagementView.vue', import.meta.url), 'utf8')
const schoolView = readFileSync(new URL('../src/views/SchoolAdminView.vue', import.meta.url), 'utf8')
const knowledgeView = readFileSync(new URL('../src/views/KnowledgeTemplateView.vue', import.meta.url), 'utf8')
const router = readFileSync(new URL('../src/router/index.js', import.meta.url), 'utf8')

test('all active bulk import screens expose an XLS template download', () => {
  for (const view of [staffView, schoolView, knowledgeView]) {
    assert.match(view, /下载 XLS 模板/)
    assert.match(view, /accept="\.xls,\.xlsx(?:,\.csv)?"/)
  }
  assert.match(staffView, /批量导入教职工/)
  assert.match(schoolView, /批量导入班级/)
  assert.match(schoolView, /批量导入学生/)
  assert.match(knowledgeView, /批量导入知识点/)
})

test('student routes use STUDENT and no longer expose INTERVIEWEE as a user role', () => {
  assert.match(router, /'STUDENT'/)
  assert.doesNotMatch(router, /'INTERVIEWEE'/)
})
