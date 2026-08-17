<template>
  <div>
    <AdminNav />
    <main class="page">
      <header><p class="page-eyebrow">题目设计</p><h1>知识库与人工智能模板</h1><p>设置评分依据，并把考试拆分为按顺序执行的人工智能答题阶段。</p></header>
      <el-tabs v-model="tab">
        <el-tab-pane label="知识库" name="knowledge">
          <section class="grid">
            <el-form class="panel" label-position="top" :model="baseForm">
              <h2>{{ baseForm.id ? '编辑知识库' : '新建知识库' }}</h2>
              <el-form-item label="名称"><el-input v-model="baseForm.knowledgeBaseName" /></el-form-item>
              <el-form-item label="学科"><el-input v-model="baseForm.techCategory" /></el-form-item>
              <el-form-item label="课程"><el-input v-model="baseForm.jobCategory" /></el-form-item>
              <el-form-item label="启用"><el-switch v-model="baseForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
              <div class="actions"><el-button type="primary" @click="saveBase">保存</el-button><el-button @click="resetBase">新建</el-button></div>
            </el-form>
            <section class="panel"><div class="panel-head"><h2>知识库</h2><el-button @click="loadBases">刷新</el-button></div>
              <el-table :data="bases" height="360" @row-click="selectBase"><el-table-column prop="knowledgeBaseName" label="名称" /><el-table-column prop="techCategory" label="学科" /><el-table-column prop="jobCategory" label="课程" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column></el-table>
            </section>
          </section>
          <section v-if="selectedBase" class="panel items"><div class="panel-head"><div><h2>{{ selectedBase.knowledgeBaseName }} 条目</h2><p>CSV 列：知识点、内容、状态。</p></div><el-upload :show-file-list="false" accept=".csv" :http-request="importItems"><el-button>导入 CSV</el-button></el-upload></div>
            <div class="item-form"><el-input v-model="itemForm.knowledgePoint" placeholder="知识点" /><el-input v-model="itemForm.knowledgeContent" type="textarea" :rows="2" placeholder="教学材料或预期证据" /><el-button type="primary" @click="saveItem">添加条目</el-button></div>
            <el-table :data="items" max-height="340"><el-table-column prop="knowledgePoint" label="知识点" width="240" /><el-table-column prop="knowledgeContent" label="内容" /></el-table>
          </section>
        </el-tab-pane>
        <el-tab-pane label="人工智能考试模板" name="templates">
          <section class="grid template-grid">
            <el-form class="panel" label-position="top" :model="templateForm">
              <h2>{{ templateForm.id ? '编辑模板' : '新建模板' }}</h2>
              <el-form-item label="模板名称"><el-input v-model="templateForm.templateName" maxlength="128" /></el-form-item>
              <el-form-item label="描述"><el-input v-model="templateForm.description" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
              <el-form-item label="启用"><el-switch v-model="templateForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
              <div class="stage-list-head"><div><h3>答题流程</h3><p>各阶段按顺序自动执行。每个阶段可使用独立知识库与知识点计划。</p></div><el-button type="primary" :icon="Plus" @click="addTemplateStage">添加阶段</el-button></div>
              <div v-if="templateForm.stages.length" class="stage-list">
                <section v-for="(stage, index) in templateForm.stages" :key="stage.key" class="stage-editor">
                  <div class="stage-title"><span>阶段 {{ index + 1 }}</span><div class="stage-tools"><el-tooltip content="上移" placement="top"><el-button circle text :icon="ArrowUp" :disabled="index === 0" @click="moveTemplateStage(index, -1)" /></el-tooltip><el-tooltip content="下移" placement="top"><el-button circle text :icon="ArrowDown" :disabled="index === templateForm.stages.length - 1" @click="moveTemplateStage(index, 1)" /></el-tooltip><el-tooltip content="删除阶段" placement="top"><el-button circle text type="danger" :icon="Delete" :disabled="templateForm.stages.length === 1" @click="removeTemplateStage(index)" /></el-tooltip></div></div>
                  <el-form-item label="阶段名称"><el-input v-model="stage.stageName" maxlength="128" placeholder="例如：基础知识" /></el-form-item>
                  <el-form-item label="知识库"><el-select v-model="stage.knowledgeBaseId" filterable placeholder="选择此阶段的知识库"><el-option v-for="item in activeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
                  <el-form-item label="每轮知识点计划"><el-input v-model="stage.roundKnowledgePoints" type="textarea" :rows="3" placeholder="用逗号或换行分隔：基础、分支、循环" /><p>第 1 项对应第 1 轮；超出计划的轮次会复用最后一项。</p></el-form-item>
                </section>
              </div>
              <div v-else class="empty-stages">请先添加一个人工智能答题阶段。</div>
              <div class="actions"><el-button type="primary" :loading="savingTemplate" @click="saveTemplate">保存模板</el-button><el-button @click="resetTemplate">新建</el-button></div>
            </el-form>
            <section class="panel"><div class="panel-head"><h2>模板</h2><el-button @click="loadTemplates">刷新</el-button></div>
              <el-table :data="templates" height="580"><el-table-column prop="templateName" label="模板" min-width="150" /><el-table-column prop="description" label="描述" min-width="170" /><el-table-column label="答题流程" min-width="220"><template #default="{ row }">{{ templateStageSummary(row) }}</template></el-table-column><el-table-column label="状态" width="84"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="76"><template #default="{ row }"><el-button text type="primary" @click="editTemplate(row)">编辑</el-button></template></el-table-column></el-table>
            </section>
          </section>
        </el-tab-pane>
      </el-tabs>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowDown, ArrowUp, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { interviewApi } from '../services/api'

const tab = ref('knowledge')
const bases = ref([]); const items = ref([]); const templates = ref([]); const selectedBase = ref(null); const savingTemplate = ref(false)
const baseForm = reactive({ id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 })
const itemForm = reactive({ knowledgePoint: '', knowledgeContent: '' })
function createTemplateStage(index = 1) { return { key: `${Date.now()}-${Math.random()}`, stageName: `AI 答题阶段 ${index}`, knowledgeBaseId: null, roundKnowledgePoints: '' } }
function createTemplateForm() { return { id: null, version: null, templateName: '', description: '', status: 1, stages: [createTemplateStage()] } }
const templateForm = reactive(createTemplateForm())
const activeBases = computed(() => bases.value.filter(item => item.status === 1))
const fail = (error) => ElMessage.error(error.message || '请求失败')
function resetBase() { Object.assign(baseForm, { id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 }) }
function resetTemplate() { Object.assign(templateForm, createTemplateForm()) }
function addTemplateStage() { templateForm.stages.push(createTemplateStage(templateForm.stages.length + 1)) }
function removeTemplateStage(index) { if (templateForm.stages.length > 1) templateForm.stages.splice(index, 1) }
function moveTemplateStage(index, offset) { const target = index + offset; if (target < 0 || target >= templateForm.stages.length) return; const [stage] = templateForm.stages.splice(index, 1); templateForm.stages.splice(target, 0, stage) }
function templateStageSummary(template) { return (template.stages || []).map((stage, index) => `${index + 1}. ${stage.stageName || '未命名阶段'}`).join(' -> ') || '未设置' }
async function loadBases() { try { bases.value = (await interviewApi.listKnowledgeBases()).data || [] } catch (error) { fail(error) } }
async function loadTemplates() { try { templates.value = (await interviewApi.listProcessTemplates()).data || [] } catch (error) { fail(error) } }
async function selectBase(base) { selectedBase.value = base; Object.assign(baseForm, base); try { items.value = (await interviewApi.listKnowledgeItems({ knowledgeBaseId: base.id })).data || [] } catch (error) { fail(error) } }
async function saveBase() { try { const saved = (await interviewApi.saveKnowledgeBase({ ...baseForm })).data; await loadBases(); await selectBase(saved); ElMessage.success('知识库已保存') } catch (error) { fail(error) } }
async function saveItem() { if (!selectedBase.value) return; try { await interviewApi.saveKnowledgeItem({ knowledgeBaseId: selectedBase.value.id, knowledgePoint: itemForm.knowledgePoint, knowledgeContent: itemForm.knowledgeContent, status: 1 }); itemForm.knowledgePoint = ''; itemForm.knowledgeContent = ''; await selectBase(selectedBase.value); ElMessage.success('知识点已添加') } catch (error) { fail(error) } }
async function importItems({ file }) { try { const count = (await interviewApi.importKnowledgeItems(selectedBase.value.id, file)).data; await selectBase(selectedBase.value); ElMessage.success(`已导入 ${count} 条知识点`) } catch (error) { fail(error) } }
async function editTemplate(row) { try { const template = (await interviewApi.getProcessTemplate(row.id)).data; if ((template.stages || []).some(stage => stage.stageType !== 'AI')) { ElMessage.warning('该模板包含视频阶段，请在智能面试管理中编辑'); return } Object.assign(templateForm, { id: template.id, version: template.version, templateName: template.templateName, description: template.description || '', status: template.status ?? 1, stages: (template.stages || []).map(stage => ({ key: `${stage.id}-${Date.now()}`, stageName: stage.stageName, knowledgeBaseId: stage.knowledgeBaseId || null, roundKnowledgePoints: stage.roundKnowledgePoints || '' })) }); if (!templateForm.stages.length) templateForm.stages.push(createTemplateStage()); } catch (error) { fail(error) } }
async function saveTemplate() { if (!templateForm.templateName.trim()) { ElMessage.warning('请填写模板名称'); return } if (!templateForm.stages.length) { ElMessage.warning('请至少添加一个答题阶段'); return } const invalid = templateForm.stages.find(stage => !stage.stageName.trim() || !stage.knowledgeBaseId); if (invalid) { ElMessage.warning('请为每个阶段填写名称并选择知识库'); return } savingTemplate.value = true; try { await interviewApi.saveProcessTemplate({ id: templateForm.id, version: templateForm.version, templateName: templateForm.templateName.trim(), description: templateForm.description.trim(), status: templateForm.status, stages: templateForm.stages.map((stage, index) => ({ stageName: stage.stageName.trim(), stageType: 'AI', knowledgeBaseId: stage.knowledgeBaseId, roundKnowledgePoints: stage.roundKnowledgePoints.trim(), sequenceNo: index + 1 })) }); resetTemplate(); await loadTemplates(); ElMessage.success('模板已保存') } catch (error) { fail(error) } finally { savingTemplate.value = false } }
onMounted(async () => { await Promise.all([loadBases(), loadTemplates()]) })
</script>

<style scoped>
.page{max-width:1440px;margin:0 auto;padding:30px}.page header{margin-bottom:22px}.page h1{margin:5px 0 8px;font-size:28px}.page header p:not(.page-eyebrow),.panel p{margin:0;color:var(--text-muted);line-height:1.6}.grid{display:grid;grid-template-columns:minmax(310px,.78fr) minmax(0,1.22fr);gap:24px}.template-grid{grid-template-columns:minmax(390px,.9fr) minmax(0,1.1fr)}.panel{padding:20px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface)}.panel h2{margin:0;font-size:18px}.panel-head,.stage-list-head,.stage-title{display:flex;justify-content:space-between;gap:14px;align-items:flex-start}.panel-head{align-items:center;margin-bottom:16px}.actions{display:flex;gap:8px;margin-top:20px}.items{margin-top:24px}.item-form{display:grid;grid-template-columns:minmax(160px,.45fr) minmax(0,1fr) auto;gap:10px;margin-bottom:14px}.stage-list-head{padding:12px 0;margin-top:6px;border-top:1px solid var(--border)}.stage-list-head h3{margin:0 0 4px;font-size:16px}.stage-list{border-top:1px solid var(--border)}.stage-editor{padding:16px 0;border-bottom:1px solid var(--border)}.stage-title{align-items:center;margin-bottom:8px}.stage-title>span{font-size:13px;font-weight:700}.stage-tools{display:flex;gap:2px}.stage-editor :deep(.el-form-item){margin-bottom:12px}.stage-editor :deep(.el-form-item:last-child){margin-bottom:0}.empty-stages{padding:20px 0;color:var(--text-muted);border-top:1px solid var(--border)}@media(max-width:900px){.page{padding:18px}.grid,.template-grid{grid-template-columns:1fr}.item-form{grid-template-columns:1fr}.item-form .el-button{justify-self:start}}@media(max-width:560px){.stage-list-head{align-items:stretch;flex-direction:column}.stage-list-head .el-button{align-self:flex-start}}
</style>
