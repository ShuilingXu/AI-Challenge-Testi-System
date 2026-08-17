import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ForcePasswordChangeView from '../views/ForcePasswordChangeView.vue'
import SchoolAdminView from '../views/SchoolAdminView.vue'
import SystemConfigView from '../views/SystemConfigView.vue'
import KnowledgeTemplateView from '../views/KnowledgeTemplateView.vue'
import StudentRegistrationView from '../views/StudentRegistrationView.vue'
import StudentExamView from '../views/StudentExamView.vue'
import ExamTakeView from '../views/ExamTakeView.vue'
import { readSessionUser } from '../utils/session'

const ADMIN_ROLES = ['IT_ADMIN', 'HR_ADMIN', 'HR_USER']
const KNOWN_ROLES = new Set([...ADMIN_ROLES, 'INTERVIEWEE'])
const routes = [
  { path: '/', redirect: '/student/register' },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/student/register', name: 'student-register', component: StudentRegistrationView },
  { path: '/change-password', name: 'change-password', component: ForcePasswordChangeView, meta: { requiresAuth: true } },
  { path: '/student', name: 'student-exams', component: StudentExamView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
  { path: '/exam/take/:processId', name: 'exam-take', component: ExamTakeView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
  { path: '/admin', redirect: '/admin/exams' },
  { path: '/admin/exams', name: 'admin-exams', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'exams' } },
  { path: '/admin/classes', name: 'admin-classes', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'classes' } },
  { path: '/admin/students', name: 'admin-students', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'students' } },
  { path: '/admin/analytics', name: 'admin-analytics', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'analytics' } },
  { path: '/admin/knowledge', name: 'admin-knowledge', component: KnowledgeTemplateView, meta: { requiresAuth: true, roles: ADMIN_ROLES } },
  { path: '/admin/settings', name: 'admin-settings', component: SystemConfigView, meta: { requiresAuth: true, roles: ['IT_ADMIN'] } },
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true
  const session = readSessionUser()
  if (!session || !KNOWN_ROLES.has(session.roleCode)) return '/login'
  if (Number(session.mustChangePassword) === 1 && to.name !== 'change-password') return '/change-password'
  if (Number(session.mustChangePassword) !== 1 && to.name === 'change-password') return session.roleCode === 'INTERVIEWEE' ? '/student' : '/admin/exams'
  if (to.meta.roles && !to.meta.roles.includes(session.roleCode)) return session.roleCode === 'INTERVIEWEE' ? '/student' : '/admin/exams'
  return true
})

export default router
