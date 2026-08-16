<template>
  <main class="login-page"><section class="login-shell"><div class="login-copy"><RouterLink class="brand" to="/student/register"><BrandMark /><strong>{{ siteSettings.siteTitle }}</strong></RouterLink><div><p class="page-eyebrow">Teacher workspace</p><h1>考试管理后台</h1><p>发布考试、维护班级与学生，并查看知识点得分分析。</p></div><RouterLink class="student-link" to="/student/register">学生进入考试</RouterLink></div><el-form class="login-form" label-position="top" :model="form"><h2>教师登录</h2><el-form-item label="账号"><el-input v-model="form.username" autocomplete="username" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" /></el-form-item><el-form-item label="验证码"><div class="captcha-row"><el-input v-model="form.captchaCode" placeholder="输入图中字符" /><button type="button" class="captcha" @click="loadCaptcha"><img :src="captcha.imageBase64" alt="登录验证码" /></button></div></el-form-item><el-button type="primary" :loading="submitting" @click="login">登录管理后台</el-button></el-form></section></main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { useSiteSettings } from '../composables/useSiteSettings'
import { authApi } from '../services/api'
import { writeSession } from '../utils/session'

const router = useRouter(); const { siteSettings } = useSiteSettings(); const submitting = ref(false); const form = reactive({ username: '', password: '', captchaId: '', captchaCode: '' }); const captcha = reactive({ imageBase64: '' })
async function loadCaptcha() { try { const response = await authApi.getCaptcha(); form.captchaId = response.data.captchaId; form.captchaCode = ''; captcha.imageBase64 = response.data.imageBase64 } catch (error) { ElMessage.error(error.message || '验证码加载失败') } }
async function login() { submitting.value = true; try { const response = await authApi.login({ ...form }); writeSession(response.data.token, response.data.user); router.push(Number(response.data.user.mustChangePassword) === 1 ? '/change-password' : response.data.user.roleCode === 'INTERVIEWEE' ? '/student' : '/admin/exams') } catch (error) { ElMessage.error(error.message || '登录失败'); await loadCaptcha() } finally { submitting.value = false } }
onMounted(loadCaptcha)
</script>

<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:#eef2ef}.login-shell{width:min(900px,100%);display:grid;grid-template-columns:minmax(270px,.9fr) minmax(0,1.1fr);overflow:hidden;border:1px solid #d2ddd6;border-radius:8px;background:#fff;box-shadow:0 24px 65px rgba(28,55,45,.12)}.login-copy{display:flex;min-height:520px;flex-direction:column;padding:38px;color:#f5f8f5;background:#164f46}.brand{display:flex;gap:10px;align-items:center;color:inherit;text-decoration:none}.brand strong{font-size:15px}.login-copy>div:nth-child(2){margin:auto 0}.login-copy .page-eyebrow{color:#afd8c6}.login-copy h1{margin:12px 0;font-size:40px}.login-copy p:not(.page-eyebrow){max-width:290px;margin:0;color:rgba(255,255,255,.72);line-height:1.75}.student-link{color:#c5e9d6;text-decoration:none}.login-form{align-self:center;padding:48px}.login-form h2{margin:0 0 28px;font-size:24px}.login-form>.el-button{width:100%}.captcha-row{display:grid;grid-template-columns:minmax(0,1fr) 116px;gap:10px;width:100%}.captcha{height:40px;padding:2px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface);cursor:pointer}.captcha img{display:block;width:100%;height:100%;object-fit:contain}@media(max-width:700px){.login-page{padding:14px}.login-shell{grid-template-columns:1fr}.login-copy{min-height:250px;padding:28px}.login-copy>div:nth-child(2){margin:42px 0}.login-form{padding:32px 26px}}
</style>
