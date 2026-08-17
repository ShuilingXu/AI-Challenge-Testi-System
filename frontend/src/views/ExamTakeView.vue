<template>
  <main class="exam-take"><header><RouterLink to="/student">返回我的考试</RouterLink><span>人工智能在线考试</span></header>
    <section v-if="process" class="exam-shell"><div class="exam-state"><p class="page-eyebrow">答题中</p><h1>{{ process.stageName || '人工智能答题' }}</h1><div class="round"><strong>{{ answeredCount }}</strong><span>/ {{ process.aiMaxQuestionRounds || '-' }} 轮已完成</span></div><el-progress :percentage="progress" :show-text="false" :stroke-width="8" /></div>
      <template v-if="question"><article class="question-panel"><span class="topic">{{ question.knowledgePoint || '综合知识' }}</span><h2>{{ question.questionContent }}</h2><el-input v-model="answerContent" type="textarea" :rows="10" maxlength="5000" show-word-limit placeholder="请独立作答，尽量说明你的推理过程和关键结论。" :disabled="submitting" /><div class="answer-actions"><span v-if="lastFeedback">上一题反馈：{{ lastFeedback }}</span><el-button type="primary" :loading="submitting" :disabled="!answerContent.trim()" @click="submit">提交本题答案</el-button></div></article><aside class="records-panel"><h2>答题记录</h2><ol><li v-for="item in records" :key="item.id"><strong>第 {{ item.sequenceNo }} 轮</strong><span>{{ item.knowledgePoint }}</span><em v-if="item.averageScore !== null && item.averageScore !== undefined">{{ item.averageScore }} 分</em><small>{{ item.interviewerComment }}</small></li></ol></aside></template>
      <section v-else-if="inProgress" class="waiting"><el-icon class="is-loading"><Loading /></el-icon><h2>正在准备下一题</h2><p>人工智能正在根据本轮答题情况生成题目，请保持当前页面。</p></section>
      <section v-else class="complete"><p class="page-eyebrow">已完成</p><h2>本次答题已完成</h2><p>{{ process.processStatusView || '系统已保存答题结果。' }}</p><el-button type="primary" @click="goAnalysis">查看人工智能综合评价</el-button></section>
    </section>
    <section v-else class="waiting"><el-icon class="is-loading"><Loading /></el-icon><p>正在加载考试…</p></section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { interviewApi } from '../services/api'

const route = useRoute(); const router = useRouter(); const processId = Number(route.params.processId); const process = ref(null); const question = ref(null); const records = ref([]); const answerContent = ref(''); const submitting = ref(false); let timer = null
const answeredCount = computed(() => records.value.filter(item => item.answerStatus === 'COMPLETED').length)
const progress = computed(() => process.value?.aiMaxQuestionRounds ? Math.min(100, Math.round(answeredCount.value / process.value.aiMaxQuestionRounds * 100)) : 0)
const inProgress = computed(() => process.value?.overallStatus === 'IN_PROGRESS' && process.value?.stageStatus === 'IN_PROGRESS')
const lastFeedback = computed(() => records.value.filter(item => item.interviewerComment).at(-1)?.interviewerComment || '')
async function load(silent = false) { try { const [processResult, questionResult, recordResult] = await Promise.all([interviewApi.getIntervieweeProcess(processId), interviewApi.getNextAiQuestion(processId), interviewApi.listIntervieweeAiRecords({ processId })]); process.value = processResult.data; question.value = questionResult.data; records.value = recordResult.data || []; if (!question.value && inProgress.value) schedule() } catch (error) { if (!silent) ElMessage.error(error.message || '考试加载失败') } }
function schedule() { clearTimeout(timer); timer = window.setTimeout(() => load(true), 2500) }
async function submit() { submitting.value = true; try { await interviewApi.submitAiAnswer({ processId, questionId: question.value.id, answerContent: answerContent.value.trim() }); answerContent.value = ''; question.value = null; await load(true) } catch (error) { ElMessage.error(error.message || '提交失败，请重试') } finally { submitting.value = false } }
function goAnalysis() { router.push('/student') }
onMounted(() => load()); onBeforeUnmount(() => clearTimeout(timer))
</script>

<style scoped>
.exam-take{min-height:100vh;background:#f6f7f4}.exam-take>header{display:flex;justify-content:space-between;max-width:1180px;margin:0 auto;padding:20px 28px;border-bottom:1px solid var(--border);color:var(--text-muted);font-size:13px}.exam-take>header a{color:var(--primary);text-decoration:none}.exam-shell{display:grid;grid-template-columns:250px minmax(0,1fr) 260px;gap:24px;max-width:1180px;margin:0 auto;padding:34px 28px}.exam-state{padding:12px 0}.exam-state h1{margin:7px 0 30px;font-size:25px}.round{margin-bottom:10px}.round strong{font-size:38px}.round span{margin-left:6px;color:var(--text-muted);font-size:13px}.question-panel{padding:28px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface)}.topic{display:inline-block;padding:4px 8px;background:var(--primary-soft);color:var(--primary);font-size:12px;font-weight:700}.question-panel h2{margin:16px 0 24px;font-size:21px;line-height:1.6}.answer-actions{display:flex;justify-content:space-between;gap:16px;align-items:center;margin-top:16px}.answer-actions span{color:var(--text-muted);font-size:13px;line-height:1.55}.records-panel{padding:18px;border-left:1px solid var(--border)}.records-panel h2{margin:0 0 16px;font-size:16px}.records-panel ol{display:grid;gap:14px;margin:0;padding:0;list-style:none}.records-panel li{display:grid;gap:3px;padding-bottom:12px;border-bottom:1px solid var(--border)}.records-panel strong{font-size:13px}.records-panel span,.records-panel small{color:var(--text-muted);font-size:12px;line-height:1.5}.records-panel em{color:var(--primary);font-size:13px;font-style:normal}.waiting,.complete{max-width:720px;margin:90px auto;padding:44px;text-align:center;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface)}.waiting .el-icon{font-size:28px;color:var(--primary)}.complete h2{margin:8px 0}.complete p:not(.page-eyebrow),.waiting p{color:var(--text-muted);line-height:1.7}@media(max-width:980px){.exam-shell{grid-template-columns:1fr}.exam-state{padding:0}.records-panel{border-left:0;border-top:1px solid var(--border)}.exam-take>header,.exam-shell{padding-left:16px;padding-right:16px}}@media(max-width:560px){.answer-actions{flex-direction:column;align-items:stretch}.question-panel{padding:20px}.waiting,.complete{margin:40px 16px;padding:28px}}
</style>
