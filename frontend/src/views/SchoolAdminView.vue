<template>
  <div>
    <AdminNav />
    <main class="school-admin">
      <header class="page-head">
        <div><p class="page-eyebrow">School assessment</p><h1>{{ title }}</h1><p>{{ description }}</p></div>
        <el-button v-if="mode === 'analytics'" :loading="loading" @click="loadAnalytics">刷新分析</el-button>
      </header>

      <section v-if="mode === 'classes'" class="two-column">
        <el-form class="tool-panel" label-position="top" :model="classForm">
          <h2>{{ classForm.id ? '编辑班级' : '新建班级' }}</h2>
          <el-form-item label="专业"><el-input v-model="classForm.majorName" placeholder="例如：计算机科学与技术" /></el-form-item>
          <el-form-item label="班级名称"><el-input v-model="classForm.className" placeholder="例如：2026 级 1 班" /></el-form-item>
          <el-form-item label="班级代码"><el-input v-model="classForm.classCode" placeholder="例如：CS2601" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="classForm.description" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="状态"><el-switch v-model="classForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
          <div class="actions"><el-button type="primary" @click="saveClass">保存班级</el-button><el-button @click="resetClass">清空</el-button></div>
          <el-divider />
          <el-upload :show-file-list="false" accept=".xlsx" :http-request="importClasses"><el-button>批量导入班级</el-button></el-upload>
          <p class="hint">Excel 列顺序：专业、班级名称、班级代码、说明。首行为表头。</p>
        </el-form>
        <section class="list-panel"><div class="panel-head"><h2>班级列表</h2><el-input v-model="classKeyword" clearable placeholder="搜索专业、班级或代码" @input="loadClasses" /></div>
          <el-table :data="classes" height="520" @row-click="editClass"><el-table-column prop="majorName" label="专业" /><el-table-column prop="className" label="班级" /><el-table-column prop="classCode" label="班级代码" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column></el-table>
        </section>
      </section>

      <section v-else-if="mode === 'students'" class="two-column">
        <el-form class="tool-panel" label-position="top" :model="studentForm">
          <h2>{{ studentForm.id ? '编辑学生' : '新建学生' }}</h2>
          <el-form-item label="学号"><el-input v-model="studentForm.studentNo" /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="studentForm.fullName" /></el-form-item>
          <el-form-item label="班级"><el-select v-model="studentForm.classId" filterable><el-option v-for="item in classes" :key="item.id" :label="`${item.majorName} / ${item.className}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="状态"><el-switch v-model="studentForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
          <div class="actions"><el-button type="primary" @click="saveStudent">保存学生</el-button><el-button @click="resetStudent">清空</el-button></div>
          <el-divider />
          <el-upload :show-file-list="false" accept=".xlsx" :http-request="importStudents"><el-button>批量导入学生</el-button></el-upload>
          <p class="hint">Excel 列顺序：学号、姓名、班级代码。请将学号列设置为文本格式。</p>
        </el-form>
        <section class="list-panel"><div class="panel-head"><h2>学生列表</h2><div class="filters"><el-select v-model="studentClassId" clearable placeholder="全部班级" @change="loadStudents"><el-option v-for="item in classes" :key="item.id" :label="item.className" :value="item.id" /></el-select><el-input v-model="studentKeyword" clearable placeholder="姓名或学号" @input="loadStudents" /></div></div>
          <el-table :data="students" height="520" @row-click="editStudent"><el-table-column prop="studentNo" label="学号" /><el-table-column prop="fullName" label="姓名" /><el-table-column prop="majorName" label="专业" /><el-table-column prop="className" label="班级" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column></el-table>
        </section>
      </section>

      <section v-else-if="mode === 'exams'" class="two-column">
        <el-form class="tool-panel" label-position="top" :model="examForm">
          <h2>{{ examForm.id ? '编辑考试' : '发布考试' }}</h2>
          <el-form-item label="考试名称"><el-input v-model="examForm.examName" /></el-form-item>
          <el-form-item label="考试代码"><el-input v-model="examForm.examCode" placeholder="例如：JAVA-2026-01" /></el-form-item>
          <el-form-item label="面向班级"><el-select v-model="examForm.classId" clearable placeholder="不选则全体学生"><el-option v-for="item in classes" :key="item.id" :label="`${item.majorName} / ${item.className}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="知识库"><el-select v-model="examForm.knowledgeBaseId" clearable><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="AI 考试模板"><el-select v-model="examForm.processTemplateId" clearable><el-option v-for="item in templates" :key="item.id" :label="item.templateName" :value="item.id" /></el-select><p class="hint">模板中可为每一轮指定知识点；未选模板时从知识库随机出题。</p></el-form-item>
          <div class="number-grid"><el-form-item label="答题轮数"><el-input-number v-model="examForm.questionRounds" :min="1" :max="20" /></el-form-item><el-form-item label="及格分"><el-input-number v-model="examForm.passingScore" :min="0" :max="100" /></el-form-item></div>
          <el-form-item label="开放时间"><el-date-picker v-model="examForm.publishStart" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" /><span class="date-separator">至</span><el-date-picker v-model="examForm.publishEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" /></el-form-item>
          <el-form-item label="考试说明"><el-input v-model="examForm.instructions" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="发布状态"><el-radio-group v-model="examForm.status"><el-radio-button value="DRAFT">草稿</el-radio-button><el-radio-button value="PUBLISHED">发布</el-radio-button><el-radio-button value="CLOSED">关闭</el-radio-button></el-radio-group></el-form-item>
          <div class="actions"><el-button type="primary" @click="saveExam">保存考试</el-button><el-button @click="resetExam">新建</el-button></div>
        </el-form>
        <section class="list-panel"><div class="panel-head"><h2>已发布考试</h2><el-button @click="loadExams">刷新</el-button></div>
          <el-table :data="exams" height="620" @row-click="editExam"><el-table-column prop="examName" label="考试" min-width="170" /><el-table-column prop="className" label="班级" /><el-table-column prop="questionRounds" label="轮数" width="80" /><el-table-column prop="passingScore" label="及格" width="80" /><el-table-column prop="templateName" label="AI 模板" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'CLOSED' ? 'info' : 'warning'">{{ statusLabel(row.status) }}</el-tag></template></el-table-column></el-table>
        </section>
      </section>

      <section v-else class="analytics">
        <div class="filter-band"><el-select v-model="analyticsFilter.examId" clearable placeholder="全部考试" @change="loadAnalytics"><el-option v-for="item in exams" :key="item.id" :label="item.examName" :value="item.id" /></el-select><el-select v-model="analyticsFilter.classId" clearable placeholder="全部班级" @change="loadAnalytics"><el-option v-for="item in classes" :key="item.id" :label="item.className" :value="item.id" /></el-select></div>
        <div class="metric-row"><article><span>参与学生</span><strong>{{ analytics.studentCount || 0 }}</strong></article><article><span>平均得分率</span><strong>{{ analytics.scoreRate || 0 }}%</strong></article><article><span>平均失分率</span><strong>{{ analytics.lossRate || 0 }}%</strong></article><article><span>覆盖考试</span><strong>{{ analytics.examCount || 0 }}</strong></article></div>
        <section class="analysis-band"><h2>AI 学情总结</h2><p>{{ analytics.aiSummary || '暂无已完成答题数据。' }}</p></section>
        <section class="knowledge-table"><div class="panel-head"><h2>知识点掌握情况</h2></div><el-table :data="analytics.knowledgePoints || []"><el-table-column prop="knowledgePoint" label="知识点" /><el-table-column label="得分率"><template #default="{ row }"><el-progress :percentage="row.scoreRate" :stroke-width="10" /></template></el-table-column><el-table-column prop="lossRate" label="失分率" width="120"><template #default="{ row }">{{ row.lossRate }}%</template></el-table-column><el-table-column prop="rounds" label="有效答题数" width="120" /></el-table></section>
        <section class="knowledge-table"><div class="panel-head"><h2>学生得分明细</h2></div><el-table :data="analytics.students || []" max-height="360"><el-table-column prop="studentNo" label="学号" /><el-table-column prop="fullName" label="姓名" /><el-table-column prop="examName" label="考试" /><el-table-column prop="className" label="班级" /><el-table-column prop="scoreRate" label="得分率"><template #default="{ row }">{{ row.scoreRate }}%</template></el-table-column><el-table-column prop="lossRate" label="失分率"><template #default="{ row }">{{ row.lossRate }}%</template></el-table-column></el-table></section>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import AdminNav from '../components/AdminNav.vue'
import { interviewApi, schoolApi } from '../services/api'

const route = useRoute()
const mode = computed(() => route.meta.schoolMode || 'exams')
const titles = { classes: ['班级管理', '维护专业与班级，支持批量导入。'], students: ['学生管理', '维护学生档案，学生可通过班级、姓名和学号进入考试。'], exams: ['考试发布', '发布给指定班级或全体学生的 AI 考试。'], analytics: ['得分分析', '从全局答题结果汇总得分率、失分率和知识点掌握情况。'] }
const title = computed(() => titles[mode.value]?.[0] || '考试管理')
const description = computed(() => titles[mode.value]?.[1] || '')
const loading = ref(false)
const classes = ref([]); const students = ref([]); const exams = ref([]); const knowledgeBases = ref([]); const templates = ref([])
const classKeyword = ref(''); const studentKeyword = ref(''); const studentClassId = ref(null)
const classForm = reactive({ id: null, majorName: '', className: '', classCode: '', description: '', status: 1 })
const studentForm = reactive({ id: null, studentNo: '', fullName: '', classId: null, status: 1 })
const examForm = reactive({ id: null, examName: '', examCode: '', classId: null, knowledgeBaseId: null, processTemplateId: null, instructions: '', questionRounds: 5, passingScore: 60, publishStart: '', publishEnd: '', status: 'DRAFT' })
const analyticsFilter = reactive({ examId: null, classId: null }); const analytics = reactive({ examCount: 0, studentCount: 0, scoreRate: 0, lossRate: 0, aiSummary: '', knowledgePoints: [], students: [] })

function fail(error) { ElMessage.error(error.message || '操作失败') }
function resetClass() { Object.assign(classForm, { id: null, majorName: '', className: '', classCode: '', description: '', status: 1 }) }
function resetStudent() { Object.assign(studentForm, { id: null, studentNo: '', fullName: '', classId: null, status: 1 }) }
function resetExam() { Object.assign(examForm, { id: null, examName: '', examCode: '', classId: null, knowledgeBaseId: null, processTemplateId: null, instructions: '', questionRounds: 5, passingScore: 60, publishStart: '', publishEnd: '', status: 'DRAFT' }) }
function editClass(row) { Object.assign(classForm, row) }; function editStudent(row) { Object.assign(studentForm, row) }; function editExam(row) { Object.assign(examForm, { ...row, publishStart: row.publishStart || '', publishEnd: row.publishEnd || '' }) }
function statusLabel(status) { return ({ DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已关闭' })[status] || status }
async function loadClasses() { try { classes.value = (await schoolApi.listClasses({ keyword: classKeyword.value || undefined })).data || [] } catch (error) { fail(error) } }
async function loadStudents() { try { students.value = (await schoolApi.listStudents({ classId: studentClassId.value || undefined, keyword: studentKeyword.value || undefined })).data || [] } catch (error) { fail(error) } }
async function loadExams() { try { exams.value = (await schoolApi.listAdminExams()).data || [] } catch (error) { fail(error) } }
async function loadDependencies() { try { const [kb, tpl] = await Promise.all([interviewApi.listKnowledgeBases({ status: 1 }), interviewApi.listProcessTemplates({ status: 1 })]); knowledgeBases.value = kb.data || []; templates.value = tpl.data || [] } catch (error) { fail(error) } }
async function saveClass() { try { await schoolApi.saveClass({ ...classForm }); ElMessage.success('班级已保存'); resetClass(); await loadClasses() } catch (error) { fail(error) } }
async function saveStudent() { try { await schoolApi.saveStudent({ ...studentForm }); ElMessage.success('学生已保存'); resetStudent(); await loadStudents() } catch (error) { fail(error) } }
async function saveExam() { try { await schoolApi.saveExam({ ...examForm, publishStart: examForm.publishStart || null, publishEnd: examForm.publishEnd || null }); ElMessage.success('考试已保存'); resetExam(); await loadExams() } catch (error) { fail(error) } }
async function importClasses({ file }) { try { const result = (await schoolApi.importClasses(file)).data; ElMessage.success(`班级导入完成：成功 ${result.successCount}，失败 ${result.failureCount}`); await loadClasses() } catch (error) { fail(error) } }
async function importStudents({ file }) { try { const result = (await schoolApi.importStudents(file)).data; ElMessage.success(`学生导入完成：成功 ${result.successCount}，失败 ${result.failureCount}`); await loadStudents() } catch (error) { fail(error) } }
async function loadAnalytics() { loading.value = true; try { Object.assign(analytics, (await schoolApi.analytics({ ...analyticsFilter })).data) } catch (error) { fail(error) } finally { loading.value = false } }
async function loadMode() { await loadClasses(); if (mode.value === 'students') await loadStudents(); if (mode.value === 'exams') { await Promise.all([loadExams(), loadDependencies()]) } if (mode.value === 'analytics') { await Promise.all([loadExams(), loadAnalytics()]) } }
onMounted(loadMode); watch(() => route.fullPath, loadMode)
</script>

<style scoped>
.school-admin { max-width: 1440px; margin: 0 auto; padding: 30px; }.page-head { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; margin-bottom:26px }.page-head h1{margin:5px 0 7px;font-size:28px}.page-head p:not(.page-eyebrow){margin:0;color:var(--text-muted)}
.two-column{display:grid;grid-template-columns:minmax(300px,.78fr) minmax(0,1.22fr);gap:24px;align-items:start}.tool-panel,.list-panel,.knowledge-table{border:1px solid var(--border);background:var(--surface);padding:20px;border-radius:var(--radius-sm)}.tool-panel h2,.list-panel h2,.analysis-band h2,.knowledge-table h2{margin:0;font-size:18px}.panel-head{display:flex;justify-content:space-between;align-items:center;gap:14px;margin-bottom:16px}.panel-head .el-input{max-width:280px}.filters{display:flex;gap:8px}.actions{display:flex;gap:8px}.hint{margin:8px 0 0;color:var(--text-muted);font-size:12px;line-height:1.6}.number-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.date-separator{padding:0 8px;color:var(--text-muted)}
.filter-band{display:flex;gap:12px;margin-bottom:18px}.metric-row{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:20px}.metric-row article{padding:17px 0;border-bottom:2px solid #d8e4e1}.metric-row span,.metric-row strong{display:block}.metric-row span{color:var(--text-muted);font-size:13px}.metric-row strong{margin-top:7px;font-size:26px}.analysis-band{padding:20px 0;border-top:1px solid var(--border);border-bottom:1px solid var(--border);margin-bottom:20px}.analysis-band p{margin:10px 0 0;line-height:1.75;color:var(--ink-soft)}.knowledge-table{margin-top:18px}
@media (max-width:960px){.school-admin{padding:18px}.two-column{grid-template-columns:1fr}.metric-row{grid-template-columns:repeat(2,1fr)}.page-head{flex-direction:column}.filter-band,.filters{flex-wrap:wrap}.date-separator{display:none}}
@media (max-width:560px){.metric-row,.number-grid{grid-template-columns:1fr}.filter-band .el-select{width:100%}}
</style>
