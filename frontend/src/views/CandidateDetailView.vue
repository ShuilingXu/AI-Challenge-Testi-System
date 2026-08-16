<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Candidate Detail</p>
          <h1 class="page-title">候选人详情</h1>
        </div>
        <RouterLink class="link-chip" to="/admin/recruitment/candidates">返回候选人列表</RouterLink>
      </div>

      <div v-if="loading" class="empty-box">正在加载候选人信息...</div>
      <div v-else-if="!candidate" class="empty-box">候选人不存在或无权访问</div>
      <template v-else>
        <section class="surface detail-surface">
          <div class="detail-head">
            <div>
              <p class="page-eyebrow">{{ candidate.jobTitle || '未绑定岗位' }}</p>
              <h2>{{ candidate.fullName }}</h2>
            </div>
            <div class="score-card">
              <span>LLM简历评分</span>
              <strong>{{ candidate.resumeLlmScore ?? resumeLlmStatusLabel(candidate.resumeLlmStatus) }}</strong>
            </div>
          </div>

          <div class="detail-grid">
            <div><span>候选人编号</span><strong>{{ candidate.id }}</strong></div>
            <div><span>应聘岗位</span><strong>{{ candidate.jobTitle || '-' }}</strong></div>
            <div><span>联系电话</span><strong>{{ candidate.mobilePhone || '-' }}</strong></div>
            <div><span>邮箱</span><strong>{{ candidate.email || '-' }}</strong></div>
            <div><span>身份证号</span><strong>{{ candidate.idCardNo || '-' }}</strong></div>
            <div><span>专业</span><strong>{{ candidate.major || '-' }}</strong></div>
            <div><span>学历</span><strong>{{ candidate.educationLevel || '-' }}</strong></div>
            <div><span>毕业院校</span><strong>{{ candidate.graduationSchool || '-' }}</strong></div>
            <div><span>工作年限</span><strong>{{ candidate.yearsOfExperience ?? '-' }}</strong></div>
            <div><span>期望薪资</span><strong>{{ candidate.expectedSalary || '-' }}</strong></div>
            <div><span>投递状态</span><strong>{{ candidate.applicationStatus || '-' }}</strong></div>
            <div><span>面试阶段</span><strong>{{ candidate.interviewStageStatus || '-' }}</strong></div>
            <div><span>面试者用户ID</span><strong>{{ candidate.intervieweeUserId || '-' }}</strong></div>
            <div><span>流程流水号</span><strong>{{ candidate.interviewProcessId || '-' }}</strong></div>
            <div><span>评分状态</span><strong>{{ resumeLlmStatusLabel(candidate.resumeLlmStatus) }}</strong></div>
            <div><span>评分时间</span><strong>{{ candidate.resumeLlmEvaluatedAt || '-' }}</strong></div>
          </div>

          <div class="intro-box">
            <span>LLM简历评价</span>
            <p>{{ candidate.resumeLlmComment || '暂无评价' }}</p>
          </div>
          <div class="intro-box">
            <span>个人简介</span>
            <p>{{ candidate.selfIntroduction || '未填写' }}</p>
          </div>
          <div class="action-row">
            <el-button v-if="candidate.resumeFileId" @click="openResume(candidate.resumeFileId)">打开简历文件</el-button>
            <el-button :disabled="!canReevaluateResumeLlm" :loading="reevaluating" @click="reevaluateResumeLlm">{{ resumeLlmReevaluateLabel }}</el-button>
            <el-button v-if="!candidate.interviewProcessId" :loading="startingInterview" @click="startCandidateInterview">发起面试</el-button>
            <el-button v-if="!candidate.interviewProcessId" type="danger" :loading="rejectingResume" @click="rejectCandidateResume">面试拒绝</el-button>
            <RouterLink v-if="candidate.interviewProcessId" class="link-chip" :to="`/interview/hr/processes/${candidate.interviewProcessId}`">查看面试流程</RouterLink>
          </div>
        </section>
      </template>
    </section>
    <el-dialog v-model="templateDialogVisible" title="选择面试流程" width="min(560px, calc(100vw - 32px))" destroy-on-close>
      <p class="dialog-intro">选择流程模板后，系统会为该候选人创建独立的面试阶段快照；留空则沿用旧流程。</p>
      <el-form label-position="top"><el-form-item label="流程模板（可选）"><el-select v-model="selectedTemplateId" clearable placeholder="不选择则沿用旧流程"><el-option v-for="item in enabledTemplates" :key="item.id" :label="item.templateName" :value="item.id"><span>{{ item.templateName }}</span><small class="template-option-detail">{{ templateStageSummary(item) }}</small></el-option></el-select></el-form-item></el-form>
      <div v-if="selectedTemplate" class="template-preview"><strong>{{ selectedTemplate.templateName }}</strong><span>{{ templateStageSummary(selectedTemplate) }}</span></div>
      <template #footer><el-button @click="templateDialogVisible = false">取消</el-button><el-button type="primary" :loading="startingInterview" @click="confirmStartCandidateInterview">发起面试</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, interviewApi, recruitmentApi } from '../services/api'

const route = useRoute()
const loading = ref(false)
const candidate = ref(null)
const reevaluating = ref(false)
const startingInterview = ref(false)
const rejectingResume = ref(false)
const templates = ref([])
const templateDialogVisible = ref(false)
const selectedTemplateId = ref(null)
const canReevaluateResumeLlm = computed(() => candidate.value?.resumeLlmStatus !== 'PENDING')
const resumeLlmReevaluateLabel = computed(() => canReevaluateResumeLlm.value ? 'AI简历重评' : '评分中不可重评')
const enabledTemplates = computed(() => templates.value.filter((item) => item.status === 1))
const selectedTemplate = computed(() => enabledTemplates.value.find((item) => item.id === selectedTemplateId.value) || null)

async function openResume(id) { try { await recruitmentApi.openResume(id) } catch (error) { ElMessage.error(error.message || '简历文件打开失败') } }
function resumeLlmStatusLabel(status) { return ({ PENDING: '评分中', COMPLETED: '已完成', FAILED: '评分失败' })[status] || '-' }

async function loadCandidate() {
  loading.value = true
  try {
    candidate.value = (await recruitmentApi.getCandidate(route.params.id)).data
    templates.value = (await interviewApi.listProcessTemplates({ status: 1 })).data
  } catch (error) {
    ElMessage.error(error.message || '候选人加载失败')
    candidate.value = null
  } finally {
    loading.value = false
  }
}

async function reevaluateResumeLlm() {
  reevaluating.value = true
  try {
    candidate.value = (await recruitmentApi.reevaluateResumeLlm(candidate.value.id)).data
    ElMessage.success('已提交AI简历重评')
  } catch (error) {
    ElMessage.error(error.message || 'AI简历重评失败')
  } finally {
    reevaluating.value = false
  }
}

async function startCandidateInterview() {
  selectedTemplateId.value = null
  templateDialogVisible.value = true
}

function templateStageSummary(template) { return (template?.stages || []).map((stage) => stage.stageName || (stage.stageType === 'AI' ? 'AI 面试' : '视频面试')).join(' -> ') || '暂无阶段' }

async function confirmStartCandidateInterview() {
  startingInterview.value = true
  try {
    const userList = (await authApi.listUsers({ roleCode: 'INTERVIEWEE', keyword: candidate.value.mobilePhone })).data
    const interviewee = userList.find((item) => item.mobilePhone === candidate.value.mobilePhone)
    if (!interviewee) {
      ElMessage.warning('未找到对应面试者账号，请先注册并完善资料')
      return
    }
    const process = (await interviewApi.startProcess({ recruitmentCandidateId: candidate.value.id, intervieweeUserId: interviewee.id, jobId: candidate.value.jobId, templateId: selectedTemplateId.value, aiThresholdScore: 70, aiFollowUpThreshold: 70, aiMinQuestionRounds: 5, aiMaxQuestionRounds: 10, antiCheatSwitchLimit: 5 })).data
    ElMessage.success('面试流程已发起')
    templateDialogVisible.value = false
    candidate.value = (await recruitmentApi.getCandidate(candidate.value.id)).data
    if (process?.id) candidate.value.interviewProcessId = process.id
  } catch (error) {
    ElMessage.error(error.message || '发起面试失败')
  } finally {
    startingInterview.value = false
  }
}

async function rejectCandidateResume() {
  rejectingResume.value = true
  try {
    candidate.value = (await recruitmentApi.rejectCandidateResume(candidate.value.id)).data
    ElMessage.success('已拒绝该报名者简历面试')
  } catch (error) {
    ElMessage.error(error.message || '面试拒绝失败')
  } finally {
    rejectingResume.value = false
  }
}

onMounted(loadCandidate)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 20px; }
.detail-surface { padding: 18px; }
.detail-head { display: flex; justify-content: space-between; gap: 18px; align-items: flex-start; margin-bottom: 18px; }
.detail-head h2 { margin: 6px 0 0; font-size: 30px; }
.score-card { min-width: 150px; border-radius: var(--radius-md); padding: 16px; background: var(--primary); color: #ffffff; }
.score-card span { display: block; opacity: 0.82; margin-bottom: 6px; font-size: 13px; }
.score-card strong { font-size: 32px; }
.detail-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.detail-grid div, .intro-box { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 12px; }
.detail-grid span, .intro-box span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 5px; }
.intro-box { margin: 12px 0; }
.intro-box p { margin: 0; line-height: 1.7; }
.action-row { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 12px; }
.dialog-intro { margin: 0 0 18px; color: var(--text-muted); line-height: 1.7; }
.template-preview { display: grid; gap: 6px; padding: 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface-soft); }
.template-preview span, .template-option-detail { color: var(--text-muted); font-size: 12px; }
.template-option-detail { display: block; margin-top: 3px; }
@media (max-width: 980px) { .detail-head { display: block; } .score-card { margin-top: 12px; } .detail-grid { grid-template-columns: 1fr; } }
</style>
