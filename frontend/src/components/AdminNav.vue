<template>
  <nav class="admin-nav" aria-label="考试管理导航">
    <div class="admin-nav-inner">
      <RouterLink class="admin-brand" to="/admin/exams" :aria-label="`${siteSettings.siteTitle} 考试管理后台`">
        <BrandMark />
        <span class="brand-name">{{ siteSettings.siteTitle }}</span>
      </RouterLink>
      <div class="admin-nav-links">
        <RouterLink v-for="item in visibleItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
      </div>
      <div class="admin-nav-actions">
        <span v-if="sessionUser" class="admin-user">{{ sessionUser.displayName || sessionUser.username }}</span>
        <button type="button" class="logout-control" @click="logout">退出</button>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BrandMark from './BrandMark.vue'
import { useSiteSettings } from '../composables/useSiteSettings'
import { authApi } from '../services/api'
import { readSessionUser } from '../utils/session'

const router = useRouter()
const { siteSettings } = useSiteSettings()
const sessionUser = ref(readSessionUser())
const visibleItems = computed(() => {
  return [
    { label: '考试发布', to: '/admin/exams' },
    { label: '班级管理', to: '/admin/classes' },
    { label: '学生管理', to: '/admin/students' },
    { label: '知识库与模板', to: '/admin/knowledge' },
    { label: '得分分析', to: '/admin/analytics' },
    ...(sessionUser.value?.roleCode === 'IT_ADMIN' ? [{ label: '系统配置', to: '/admin/settings' }] : []),
  ]
})

async function logout() {
  try { await authApi.logout() } finally { router.push('/login') }
}
</script>

<style scoped>
.admin-nav { position: sticky; top: 0; z-index: 20; border-bottom: 1px solid var(--border); background: rgba(255,255,255,.96); box-shadow: 0 1px 8px rgba(23,33,31,.04); }
.admin-nav-inner { display: flex; align-items: center; gap: 18px; max-width: 1440px; min-width: 0; margin: 0 auto; padding: 0 28px; }
.admin-brand { --site-brand-size: 30px; display: inline-flex; flex: 0 0 auto; min-width: 0; max-width: min(30vw,260px); align-items: center; gap: 9px; color: var(--ink); text-decoration: none; font-weight: 800; letter-spacing: 0; }
.brand-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-nav-links { display: flex; flex: 1 1 auto; min-width: 0; gap: 2px; overflow-x: auto; scrollbar-width: thin; }
.admin-nav-links a { flex: 0 0 auto; padding: 16px 11px 13px; border-bottom: 3px solid transparent; color: var(--text-muted); text-decoration: none; font-size: 14px; font-weight: 600; white-space: nowrap; }
.admin-nav-links a:hover { color: var(--primary); background: var(--primary-soft); }.admin-nav-links a.router-link-active { color: var(--primary); border-bottom-color: var(--primary); }
.admin-nav-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; }.admin-user { max-width: 120px; overflow: hidden; color: var(--text-muted); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.logout-control { min-height: 34px; padding: 6px 10px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface); color: var(--ink-soft); cursor: pointer; font: inherit; font-size: 13px; font-weight: 600; white-space: nowrap; }
.logout-control:hover { border-color: var(--primary); background: var(--primary-soft); color: var(--primary); }
@media (max-width:900px) { .admin-nav-inner { gap:10px;padding:0 14px }.brand-name,.admin-user { display:none }.admin-nav-links a { padding-inline:9px } }
</style>
