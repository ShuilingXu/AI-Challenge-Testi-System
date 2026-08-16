<template>
  <main class="password-change-page">
    <section class="password-change-panel">
      <header>
        <p class="page-eyebrow">Account security</p>
        <h1>修改初始密码</h1>
        <p>为保护账号安全，请先设置新的登录密码。</p>
      </header>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="当前密码">
          <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="submit" />
        </el-form-item>
        <div class="actions">
          <el-button type="primary" size="large" :loading="submitting" native-type="submit">保存新密码</el-button>
          <el-button :disabled="submitting" @click="logout">退出登录</el-button>
        </div>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'
import { isStrongPassword, strongPasswordMessage } from '../utils/password'
import { writeSession } from '../utils/session'

const router = useRouter()
const submitting = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

function targetByRole(roleCode) {
  return roleCode === 'INTERVIEWEE' ? '/user' : '/admin'
}

async function submit() {
  if (!isStrongPassword(form.newPassword)) {
    ElMessage.warning(strongPasswordMessage)
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  submitting.value = true
  try {
    const response = await authApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    writeSession(response.data.token, response.data.user)
    ElMessage.success('密码已修改')
    router.replace(targetByRole(response.data.user.roleCode))
  } catch (error) {
    ElMessage.error(error.message || '密码修改失败')
  } finally {
    submitting.value = false
  }
}

async function logout() {
  try {
    await authApi.logout()
  } finally {
    router.replace('/login')
  }
}
</script>

<style scoped>
.password-change-page { display: grid; min-height: 100vh; padding: 24px; place-items: center; background: #eef2ef; }
.password-change-panel { width: min(460px, 100%); padding: 34px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); box-shadow: var(--shadow-card); }
.password-change-panel header { margin-bottom: 28px; }
.password-change-panel h1 { margin: 7px 0 9px; font-size: 28px; line-height: 1.2; letter-spacing: 0; }
.password-change-panel header > p:last-child { margin: 0; color: var(--text-muted); line-height: 1.65; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 24px; }
@media (max-width: 560px) { .password-change-page { padding: 14px; } .password-change-panel { padding: 26px 20px; } .actions > * { flex: 1 1 auto; } }
</style>
