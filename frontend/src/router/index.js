import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ForcePasswordChangeView from '../views/ForcePasswordChangeView.vue'
import SchoolAdminView from '../views/SchoolAdminView.vue'
import SystemConfigView from '../views/SystemConfigView.vue'
import StaffManagementView from '../views/StaffManagementView.vue'
import SiteSettingsView from '../views/SiteSettingsView.vue'
import KnowledgeTemplateView from '../views/KnowledgeTemplateView.vue'
import StudentRegistrationView from '../views/StudentRegistrationView.vue'
import StudentExamView from '../views/StudentExamView.vue'
import ExamTakeView from '../views/ExamTakeView.vue'
import { readSessionUser, writeSessionUser } from '../utils/session'
import { authApi } from '../services/api'
import { sessionRedirect } from '../utils/sessionRedirect'

const ADMIN_ROLES = ['IT_ADMIN', 'HR_ADMIN', 'HR_USER', 'SYSTEM_ADMIN', 'DEPARTMENT_HEAD', 'LECTURER']
const KNOWN_ROLES = new Set([...ADMIN_ROLES, 'STUDENT'])
const routes = [
  { path: '/', redirect: '/student/register' },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/student/register', name: 'student-register', component: StudentRegistrationView },
  { path: '/change-password', alias: '/changepasswd', name: 'change-password', component: ForcePasswordChangeView, meta: { requiresAuth: true, allowOptionalPasswordChange: true } },
  { path: '/student', name: 'student-exams', component: StudentExamView, meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/exam/take/:processId', name: 'exam-take', component: ExamTakeView, meta: { requiresAuth: true, roles: ['STUDENT'] } },
  { path: '/admin', redirect: '/admin/exams' },
  { path: '/admin/exams', name: 'admin-exams', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'exams' } },
  { path: '/admin/classes', name: 'admin-classes', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'classes' } },
  { path: '/admin/students', name: 'admin-students', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'students' } },
  { path: '/admin/analytics', name: 'admin-analytics', component: SchoolAdminView, meta: { requiresAuth: true, roles: ADMIN_ROLES, schoolMode: 'analytics' } },
  { path: '/admin/knowledge', name: 'admin-knowledge', component: KnowledgeTemplateView, meta: { requiresAuth: true, roles: ADMIN_ROLES } },
  { path: '/admin/settings', name: 'admin-settings', component: SystemConfigView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'SYSTEM_ADMIN'] } },
  { path: '/admin/site-settings', name: 'admin-site-settings', component: SiteSettingsView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'SYSTEM_ADMIN'] } },
  { path: '/admin/staff', name: 'admin-staff', component: StaffManagementView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'SYSTEM_ADMIN', 'DEPARTMENT_HEAD'] } },
]

const router = createRouter({ history: createWebHistory(), routes })
let sessionRestored = false
router.beforeEach(async (to) => {
  // Restore the HttpOnly cookie session even when localStorage was cleared.
  if (!sessionRestored) {
    try {
      const response = await authApi.getSession()
      writeSessionUser(response.data)
      sessionRestored = true
    } catch (error) {
      if (error.status !== 401) throw error
      sessionRestored = true
    }
  }
  const session = readSessionUser()
  const validSession = session && KNOWN_ROLES.has(session.roleCode)
  const redirect = validSession ? sessionRedirect(to, session) : null
  if (redirect) return redirect
  if (!to.meta.requiresAuth) return true
  if (!validSession) return '/login'
  if (Number(session.mustChangePassword) !== 1 && to.name === 'change-password' && !to.meta.allowOptionalPasswordChange) return session.roleCode === 'STUDENT' ? '/student' : '/admin/exams'
  if (to.meta.roles && !to.meta.roles.includes(session.roleCode)) return session.roleCode === 'STUDENT' ? '/student' : '/admin/exams'
  return true
})

export default router
