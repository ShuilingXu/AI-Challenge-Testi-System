<template>
  <main class="register-page"><section class="register-shell"><div class="register-copy"><BrandMark /><p class="page-eyebrow">Student assessment</p><h1>进入考试</h1><p>选择所属班级，填写姓名和学号后即可查看并参加已发布的考试。</p><RouterLink to="/login">教师登录</RouterLink></div><el-form class="register-form" label-position="top" :model="form"><h2>学生登记</h2><el-form-item label="所属班级"><el-select v-model="form.classId" filterable placeholder="请选择班级"><el-option v-for="item in classes" :key="item.id" :label="`${item.majorName} / ${item.className}`" :value="item.id" /></el-select></el-form-item><el-form-item label="姓名"><el-input v-model="form.fullName" autocomplete="name" /></el-form-item><el-form-item label="学号"><el-input v-model="form.studentNo" autocomplete="off" /></el-form-item><el-button type="primary" :loading="submitting" @click="register">进入我的考试</el-button></el-form></section></main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { schoolApi } from '../services/api'
import { writeSession } from '../utils/session'

const router = useRouter(); const classes = ref([]); const submitting = ref(false); const form = reactive({ classId: null, fullName: '', studentNo: '' })
async function loadClasses() { try { classes.value = (await schoolApi.listPublicClasses()).data || [] } catch (error) { ElMessage.error(error.message || '班级列表加载失败') } }
async function register() { if (!form.classId || !form.fullName.trim() || !form.studentNo.trim()) { ElMessage.warning('请完整填写班级、姓名和学号'); return } submitting.value = true; try { const response = await schoolApi.registerStudent({ ...form }); writeSession(response.data.token, response.data.user); router.push('/student') } catch (error) { ElMessage.error(error.message || '学生登记失败') } finally { submitting.value = false } }
onMounted(loadClasses)
</script>

<style scoped>
.register-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:#eef2ef}.register-shell{width:min(900px,100%);display:grid;grid-template-columns:minmax(260px,.9fr) minmax(0,1.1fr);overflow:hidden;border:1px solid #d2ddd6;border-radius:8px;background:#fff;box-shadow:0 24px 65px rgba(28,55,45,.12)}.register-copy{display:flex;min-height:520px;flex-direction:column;padding:38px;color:#f5f8f5;background:#164f46}.register-copy .brand-mark{margin-bottom:auto}.register-copy .page-eyebrow{margin-top:auto;color:#afd8c6}.register-copy h1{margin:12px 0;font-size:40px}.register-copy p:not(.page-eyebrow){max-width:290px;margin:0;color:rgba(255,255,255,.72);line-height:1.75}.register-copy a{margin-top:34px;color:#c5e9d6;text-decoration:none}.register-form{align-self:center;padding:48px}.register-form h2{margin:0 0 28px;font-size:24px}.register-form .el-button{width:100%}@media(max-width:700px){.register-page{padding:14px}.register-shell{grid-template-columns:1fr}.register-copy{min-height:260px;padding:28px}.register-copy .page-eyebrow{margin-top:42px}.register-form{padding:32px 26px}}
</style>
