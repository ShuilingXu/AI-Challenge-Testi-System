<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Candidate</p>
      <h1 class="page-title">测试者报名</h1>
      <div class="page-grid">
        <el-form :model="form" label-position="top" class="surface">
          <h3>报名信息</h3>
          <el-form-item label="应聘岗位">
            <el-select v-model="form.jobId" placeholder="选择开放岗位">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobTitle} / ${item.departmentName}`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.fullName" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.mobilePhone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          <el-form-item label="身份证号"><el-input v-model="form.idCardNo" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="学历"><el-input v-model="form.educationLevel" /></el-form-item>
          <el-form-item label="毕业院校"><el-input v-model="form.graduationSchool" /></el-form-item>
          <el-form-item label="工作年限"><el-input-number v-model="form.yearsOfExperience" :min="0" /></el-form-item>
          <el-form-item label="期望薪资"><el-input v-model="form.expectedSalary" /></el-form-item>
          <el-form-item label="自我介绍"><el-input v-model="form.selfIntroduction" type="textarea" :rows="4" /></el-form-item>
          <el-upload :auto-upload="false" :limit="1" :on-change="pickResume" :on-remove="removeResume">
            <el-button>选择简历文件</el-button>
          </el-upload>
          <div class="link-row">
            <el-button type="primary" @click="submit">提交报名并上传简历</el-button>
            <RouterLink class="link-chip" to="/user">查看我的报名</RouterLink>
          </div>
        </el-form>
        <div class="surface">
          <h3>开放岗位</h3>
          <div class="job-list">
            <button v-for="item in jobs" :key="item.id" @click="form.jobId = item.id">
              <strong>{{ item.jobTitle }}</strong>
              <span>{{ item.departmentName }} / {{ item.workLocation || '地点待定' }}</span>
              <small>{{ item.salaryRange || '薪资面议' }}</small>
            </button>
          </div>
          <div v-if="submittedCandidate" class="result-box">
            <h3>报名成功</h3>
            <p>报名编号：{{ submittedCandidate.id }}</p>
            <p>应聘岗位：{{ submittedCandidate.jobTitle }}</p>
            <p>简历：{{ submittedCandidate.resumeFileName || '未上传' }}</p>
            <RouterLink class="link-chip" to="/user">回到首页查看流程入口</RouterLink>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, recruitmentApi } from '../services/api'
import { writeSessionUser } from '../utils/session'

const router = useRouter()
const jobs = ref([])
const resumeFile = ref(null)
const submittedCandidate = ref(null)
const form = reactive({ jobId: null, fullName: '', mobilePhone: '', email: '', idCardNo: '', major: '', educationLevel: '', graduationSchool: '', yearsOfExperience: 0, expectedSalary: '', selfIntroduction: '' })

function fail(error) { ElMessage.error(error.message || '操作失败') }
function pickResume(uploadFile) { resumeFile.value = uploadFile.raw }
function removeResume() { resumeFile.value = null }
async function loadJobs() { try { jobs.value = (await recruitmentApi.listOpenJobs()).data } catch (error) { fail(error) } }
async function loadSession() {
  try {
    const response = await authApi.getSession()
    const user = response.data
    if (user.roleCode !== 'INTERVIEWEE') {
      ElMessage.error('仅面试者可以报名')
      router.push('/login')
      return
    }
    if (user.profileCompleted !== 1) {
      ElMessage.warning('请先在用户门户完善信息')
      router.push('/user')
      return
    }
    form.fullName = user.displayName || ''
    form.mobilePhone = user.mobilePhone || ''
    form.email = user.email || ''
    writeSessionUser(user)
  } catch (error) {
    fail(error)
    router.push('/login')
  }
}
async function submit() {
  try {
    let candidate
    try {
      candidate = (await recruitmentApi.apply({ ...form })).data
    } catch (error) {
      if (!String(error.message || '').includes('重复投递') && !String(error.message || '').includes('已投递')) throw error
      const existing = (await recruitmentApi.listMyCandidates({ page: 1, pageSize: 200 })).data
        .find((item) => Number(item.jobId) === Number(form.jobId))
      if (!existing) throw error
      candidate = existing
      ElMessage.info('已找到该岗位的报名记录，将继续补传简历')
    }
    if (resumeFile.value) {
      const resumeResponse = await recruitmentApi.uploadResume(candidate.id, resumeFile.value)
      candidate = { ...candidate, resumeFileId: resumeResponse.data.id, resumeFileName: resumeResponse.data.originalFileName }
    }
    submittedCandidate.value = candidate
    ElMessage.success('报名已提交')
  } catch (error) { fail(error) }
}

onMounted(async () => {
  await loadSession()
  await loadJobs()
})
</script>

<style scoped>
.job-list { display: grid; gap: 12px; }
.job-list strong, .job-list span, .job-list small { display: block; }
.job-list span { margin: 6px 0; color: var(--text-muted); }
.upload-tip { color: var(--text-muted); margin-top: 8px; }
.result-box { margin-top: 20px; padding: 20px; border-radius: var(--radius-md); background: var(--primary); color: #ffffff; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3); }
.result-box h3 { color: #ffffff; margin: 0 0 12px; }
.result-box p { color: rgba(255,255,255,0.88); margin: 6px 0; }
</style>
