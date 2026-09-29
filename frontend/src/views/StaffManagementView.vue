<template>
  <main class="staff-page">
    <AdminNav />
    <section class="staff-layout">
      <el-form class="tool-panel" label-position="top" :model="form">
        <h1>{{ form.id ? '编辑教职工' : '新增教职工' }}</h1>
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="后台角色"><el-select v-model="form.roleCode"><el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.mobilePhone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item :label="form.id ? '重置密码（留空不修改）' : '初始密码'"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item v-if="form.id" label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <div class="actions"><el-button type="primary" :loading="saving" @click="save">{{ form.id ? '保存信息' : '创建教职工' }}</el-button><el-button @click="reset">清空</el-button></div>
      </el-form>
      <section class="list-panel">
        <div class="panel-head"><div><h2>教职工列表</h2><p>上级角色可重置下级密码和信息。</p></div><div class="panel-actions"><el-button @click="downloadTemplate">下载 XLS 模板</el-button><el-upload :show-file-list="false" accept=".xls,.xlsx" :http-request="importStaff"><el-button type="primary">批量导入教职工</el-button></el-upload><el-button :loading="loading" @click="load">刷新</el-button></div></div>
        <el-table :data="users" v-loading="loading" height="620" @row-click="edit">
          <el-table-column prop="username" label="用户名" min-width="150" />
          <el-table-column prop="displayName" label="姓名" min-width="130" />
          <el-table-column label="角色" width="130"><template #default="{ row }">{{ roleLabel(row.roleCode) }}</template></el-table-column>
          <el-table-column prop="mobilePhone" label="手机号" min-width="130" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="primary" @click.stop="edit(row)">编辑</el-button></template></el-table-column>
        </el-table>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { authApi } from '../services/api'
import { readSessionUser } from '../utils/session'

const session = readSessionUser() || {}
const users = ref([]); const loading = ref(false); const saving = ref(false)
const form = reactive({ id: null, username: '', displayName: '', roleCode: 'LECTURER', mobilePhone: '', email: '', password: '', status: 1 })
const allRoles = [{ value: 'SYSTEM_ADMIN', label: '系统管理员' }, { value: 'DEPARTMENT_HEAD', label: '系主任' }, { value: 'LECTURER', label: '讲师' }]
const roleOptions = computed(() => {
  if (session.roleCode === 'DEPARTMENT_HEAD') return allRoles.filter(item => item.value === 'LECTURER')
  if (session.roleCode === 'SYSTEM_ADMIN') return allRoles.filter(item => ['DEPARTMENT_HEAD', 'LECTURER'].includes(item.value))
  return allRoles
})
function roleLabel(value) { return allRoles.find(item => item.value === value)?.label || value }
function reset() { Object.assign(form, { id: null, username: '', displayName: '', roleCode: roleOptions.value[0]?.value || 'LECTURER', mobilePhone: '', email: '', password: '', status: 1 }) }
function edit(row) { Object.assign(form, { ...row, password: '' }) }
async function load() { loading.value = true; try { users.value = (await authApi.listUsers({ roleCode: undefined, pageSize: 200 })).data || [] } catch (error) { ElMessage.error(error.message || '教职工加载失败') } finally { loading.value = false } }
function downloadBlob(blob, filename) { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url) }
async function downloadTemplate() { try { downloadBlob(await authApi.downloadStaffTemplate(), 'staff-import-template.xls') } catch (error) { ElMessage.error(error.message || '模板下载失败') } }
async function importStaff({ file }) { try { const result = (await authApi.importStaff(file)).data; ElMessage.success(`教职工导入完成：成功 ${result.successCount}，失败 ${result.failureCount}`); await load() } catch (error) { ElMessage.error(error.message || '教职工导入失败') } }
async function save() {
  saving.value = true
  try {
    if (form.id) {
      await authApi.updateUser(form.id, { roleCode: form.roleCode, displayName: form.displayName, mobilePhone: form.mobilePhone, email: form.email, status: form.status, newPassword: form.password || undefined })
    } else {
      if (!form.password) throw new Error('请设置初始密码')
      await authApi.createUser({ username: form.username, password: form.password, roleCode: form.roleCode, displayName: form.displayName, mobilePhone: form.mobilePhone, email: form.email })
    }
    ElMessage.success(form.id ? '教职工信息已保存' : '教职工已创建'); reset(); await load()
  } catch (error) { ElMessage.error(error.message || '保存失败') } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.staff-page{min-height:100vh;background:var(--background)}.staff-layout{max-width:1280px;margin:0 auto;padding:30px;display:grid;grid-template-columns:320px minmax(0,1fr);gap:24px}.tool-panel,.list-panel{border:1px solid var(--border);background:var(--surface);padding:22px;border-radius:var(--radius-sm)}.tool-panel h1,.list-panel h2{margin:0 0 18px;font-size:20px}.panel-head{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.panel-head p{margin:5px 0 0;color:var(--text-muted);font-size:13px}.panel-actions{display:flex;gap:8px;align-items:center}.actions{display:flex;gap:8px}@media(max-width:900px){.staff-layout{grid-template-columns:1fr;padding:18px}.panel-actions{flex-wrap:wrap}}
.tool-panel,.list-panel,.panel-head>*,.staff-layout>*{min-width:0}
.panel-head,.panel-actions,.actions{flex-wrap:wrap}
@media(max-width:900px){.staff-layout{grid-template-columns:minmax(0,1fr)}}
</style>
