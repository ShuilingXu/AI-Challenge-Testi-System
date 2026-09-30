<template>
  <div><AdminNav /><main class="review-page">
    <header><h1>捞(挂)人</h1><p>按班级、考试、姓名或学号查找成绩，并复核单题评分。</p></header>
    <section v-if="!detail.processId" class="panel">
      <div class="filters">
        <el-select v-model="filter.classId" clearable placeholder="全部班级"><el-option v-for="item in classes" :key="item.id" :label="item.className" :value="item.id" /></el-select>
        <el-select v-model="filter.examId" clearable placeholder="全部考试"><el-option v-for="item in exams" :key="item.id" :label="item.examName" :value="item.id" /></el-select>
        <el-input v-model="filter.name" clearable placeholder="姓名" @keyup.enter="search" />
        <el-input v-model="filter.studentNo" clearable placeholder="学号" @keyup.enter="search" />
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="results" empty-text="暂无符合条件的成绩记录">
        <el-table-column prop="className" label="班级" /><el-table-column prop="examName" label="考试" />
        <el-table-column prop="fullName" label="姓名" /><el-table-column prop="studentNo" label="学号" />
        <el-table-column label="AI 给分"><template #default="{ row }">{{ row.aiScore == null ? '待评分' : `${row.aiScore} 分` }}</template></el-table-column>
        <el-table-column label="人工复核后给分"><template #default="{ row }">{{ row.reviewedScore == null ? '未复核' : `${row.reviewedScore} 分` }}</template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{ row }"><el-button text type="primary" @click="open(row)">人工改分</el-button></template></el-table-column>
      </el-table>
    </section>
    <section v-if="detail.processId" class="panel">
      <div class="detail-head"><div><h2>{{ detail.fullName }} · {{ detail.examName }}</h2><p>{{ detail.className }} · {{ detail.studentNo }}</p><p>AI 给分：{{ detail.aiScore == null ? '待评分' : `${detail.aiScore} 分` }}　人工复核后给分：{{ detail.reviewedScore == null ? '未复核' : `${detail.reviewedScore} 分` }}</p></div><el-button @click="closeDetail">返回结果</el-button></div>
      <article v-for="record in detail.records || []" :key="record.id" class="answer">
        <h3>第 {{ record.sequenceNo }} 题 · {{ record.knowledgePoint }}</h3>
        <dl><dt>题目</dt><dd>{{ record.questionContent }}</dd><dt>回答</dt><dd>{{ record.answerContent || '尚未作答' }}</dd><dt>AI 判分评语</dt><dd>{{ record.interviewerComment || '暂无评语' }}</dd></dl>
        <div class="score-line"><strong>AI 给分：{{ record.aiScore == null ? '待评分' : `${record.aiScore} 分` }}</strong><strong>人工复核后给分：{{ record.reviewedScore == null ? '未复核' : `${record.reviewedScore} 分` }}</strong></div>
        <div v-if="record.reviewedScore != null" class="review-note"><strong>复核注释</strong><p>{{ record.teacherNote || '本次复核未填写注释' }}</p></div>
        <div v-if="record.answerStatus === 'COMPLETED'" class="review-controls">
          <el-input-number v-model="drafts[record.id].score" :min="0" :max="100" aria-label="复核得分" />
          <el-input v-model="drafts[record.id].note" maxlength="2000" show-word-limit placeholder="教师注释（可选，仅教职工可见）" />
          <el-button type="primary" :loading="savingId === record.id" @click="save(record)">保存复核</el-button>
        </div>
      </article>
      <p v-if="!detail.records?.length">该学生暂无答题记录。</p>
    </section>
  </main></div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { schoolApi } from '../services/api'

const classes = ref([]); const exams = ref([]); const results = ref([])
const route = useRoute(); const router = useRouter()
const filter = reactive({ classId: null, examId: null, name: '', studentNo: '' })
const detail = reactive({ records: [] }); const drafts = reactive({})
const loading = ref(false); const savingId = ref(null)
function closeDetail() { router.push('/admin/score-review') }
function setDetail(value) {
  Object.assign(detail, value)
  for (const record of value.records || []) drafts[record.id] = { score: record.averageScore ?? 0, note: record.teacherNote || '' }
}
async function search() {
  loading.value = true
  try { results.value = (await schoolApi.searchScores({ classId: filter.classId || undefined, examId: filter.examId || undefined, name: filter.name || undefined, studentNo: filter.studentNo || undefined })).data || [] }
  catch (error) { ElMessage.error(error.message || '成绩查询失败') } finally { loading.value = false }
}
async function open(row) { await router.push(`/admin/score-review/${row.processId}`) }
async function loadDetail(processId) { if (!processId) { Object.assign(detail, { processId: null, records: [] }); return }; try { setDetail((await schoolApi.getAdminAttempt(processId)).data) } catch (error) { ElMessage.error(error.message || '答题记录加载失败'); closeDetail() } }
async function save(record) {
  savingId.value = record.id
  try { setDetail((await schoolApi.reviewScore(record.id, drafts[record.id])).data); ElMessage.success('复核结果已保存'); await search() }
  catch (error) { ElMessage.error(error.message || '复核保存失败') } finally { savingId.value = null }
}
onMounted(async () => {
  try { const [classResponse, examResponse] = await Promise.all([schoolApi.listClasses(), schoolApi.listAdminExams()]); classes.value = classResponse.data || []; exams.value = examResponse.data || []; if (!route.params.processId) await search(); await loadDetail(route.params.processId) }
  catch (error) { ElMessage.error(error.message || '筛选条件加载失败') }
})
watch(() => route.params.processId, loadDetail)
</script>

<style scoped>
.review-page{max-width:1280px;margin:auto;padding:28px}.review-page header{margin-bottom:24px}.review-page h1{margin:0 0 6px}.review-page p{color:var(--text-muted)}.panel{padding:22px;margin-bottom:20px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface)}.filters,.detail-head,.score-line,.review-controls{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.filters{margin-bottom:18px}.filters .el-input,.filters .el-select{width:180px}.detail-head{justify-content:space-between}.detail-head h2{margin:0}.answer{padding:18px 0;border-top:1px solid var(--border)}.answer h3{margin:0 0 12px}.answer dl{display:grid;grid-template-columns:100px minmax(0,1fr);gap:10px;margin:0 0 16px}.answer dt{color:var(--text-muted)}.answer dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.score-line{margin-bottom:12px}.review-note{margin:0 0 16px;padding:12px 14px;background:var(--primary-soft);border-radius:var(--radius-sm)}.review-note strong{font-size:13px}.review-note p{margin:6px 0 0;white-space:pre-wrap;overflow-wrap:anywhere}.review-controls .el-input{flex:1;min-width:220px}@media(max-width:700px){.review-page{padding:16px}.filters .el-input,.filters .el-select{width:100%}.answer dl{grid-template-columns:1fr}}
</style>
