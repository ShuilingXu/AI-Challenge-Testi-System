<template>
  <div>
    <AdminNav />
    <main class="page">
      <header><p class="page-eyebrow">Question design</p><h1>Knowledge Bases and AI Templates</h1><p>Set the material for scoring, then assign a knowledge point to every AI question round.</p></header>
      <el-tabs v-model="tab">
        <el-tab-pane label="Knowledge bases" name="knowledge">
          <section class="grid">
            <el-form class="panel" label-position="top" :model="baseForm">
              <h2>{{ baseForm.id ? 'Edit knowledge base' : 'New knowledge base' }}</h2>
              <el-form-item label="Name"><el-input v-model="baseForm.knowledgeBaseName" /></el-form-item>
              <el-form-item label="Subject"><el-input v-model="baseForm.techCategory" /></el-form-item>
              <el-form-item label="Course"><el-input v-model="baseForm.jobCategory" /></el-form-item>
              <el-form-item label="Enabled"><el-switch v-model="baseForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
              <div class="actions"><el-button type="primary" @click="saveBase">Save</el-button><el-button @click="resetBase">New</el-button></div>
            </el-form>
            <section class="panel"><div class="panel-head"><h2>Knowledge bases</h2><el-button @click="loadBases">Refresh</el-button></div>
              <el-table :data="bases" height="360" @row-click="selectBase"><el-table-column prop="knowledgeBaseName" label="Name" /><el-table-column prop="techCategory" label="Subject" /><el-table-column prop="jobCategory" label="Course" /><el-table-column label="State" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? 'Enabled' : 'Disabled' }}</el-tag></template></el-table-column></el-table>
            </section>
          </section>
          <section v-if="selectedBase" class="panel items"><div class="panel-head"><div><h2>{{ selectedBase.knowledgeBaseName }} items</h2><p>CSV columns: point, content, status.</p></div><el-upload :show-file-list="false" accept=".csv" :http-request="importItems"><el-button>Import CSV</el-button></el-upload></div>
            <div class="item-form"><el-input v-model="itemForm.knowledgePoint" placeholder="Knowledge point" /><el-input v-model="itemForm.knowledgeContent" type="textarea" :rows="2" placeholder="Teaching material or expected evidence" /><el-button type="primary" @click="saveItem">Add item</el-button></div>
            <el-table :data="items" max-height="340"><el-table-column prop="knowledgePoint" label="Knowledge point" width="240" /><el-table-column prop="knowledgeContent" label="Content" /></el-table>
          </section>
        </el-tab-pane>
        <el-tab-pane label="AI exam templates" name="templates">
          <section class="grid">
            <el-form class="panel" label-position="top" :model="templateForm">
              <h2>{{ templateForm.id ? 'Edit template' : 'New template' }}</h2>
              <el-form-item label="Template name"><el-input v-model="templateForm.templateName" /></el-form-item>
              <el-form-item label="Description"><el-input v-model="templateForm.description" type="textarea" :rows="2" /></el-form-item>
              <el-form-item label="Knowledge base"><el-select v-model="templateForm.knowledgeBaseId" filterable><el-option v-for="item in activeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="Stage name"><el-input v-model="templateForm.stageName" /></el-form-item>
              <el-form-item label="Round knowledge point plan"><el-input v-model="templateForm.roundKnowledgePoints" type="textarea" :rows="4" placeholder="Use commas or new lines: basics, branches, loops" /><p>The first value is used for round one. The final value is reused when there are more rounds.</p></el-form-item>
              <el-form-item label="Enabled"><el-switch v-model="templateForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
              <div class="actions"><el-button type="primary" @click="saveTemplate">Save template</el-button><el-button @click="resetTemplate">New</el-button></div>
            </el-form>
            <section class="panel"><div class="panel-head"><h2>Templates</h2><el-button @click="loadTemplates">Refresh</el-button></div>
              <el-table :data="templates" height="480" @row-click="editTemplate"><el-table-column prop="templateName" label="Template" /><el-table-column prop="description" label="Description" /><el-table-column label="Round plan" min-width="200"><template #default="{ row }">{{ row.stages?.[0]?.roundKnowledgePoints || 'Not specified' }}</template></el-table-column></el-table>
            </section>
          </section>
        </el-tab-pane>
      </el-tabs>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { interviewApi } from '../services/api'

const tab = ref('knowledge')
const bases = ref([]); const items = ref([]); const templates = ref([]); const selectedBase = ref(null)
const baseForm = reactive({ id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 })
const itemForm = reactive({ knowledgePoint: '', knowledgeContent: '' })
const templateForm = reactive({ id: null, version: null, templateName: '', description: '', knowledgeBaseId: null, stageName: 'AI questions', roundKnowledgePoints: '', status: 1 })
const activeBases = computed(() => bases.value.filter(item => item.status === 1))
const fail = (error) => ElMessage.error(error.message || 'Request failed')
function resetBase() { Object.assign(baseForm, { id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 }) }
function resetTemplate() { Object.assign(templateForm, { id: null, version: null, templateName: '', description: '', knowledgeBaseId: null, stageName: 'AI questions', roundKnowledgePoints: '', status: 1 }) }
async function loadBases() { try { bases.value = (await interviewApi.listKnowledgeBases()).data || [] } catch (error) { fail(error) } }
async function loadTemplates() { try { templates.value = (await interviewApi.listProcessTemplates()).data || [] } catch (error) { fail(error) } }
async function selectBase(base) { selectedBase.value = base; Object.assign(baseForm, base); try { items.value = (await interviewApi.listKnowledgeItems({ knowledgeBaseId: base.id })).data || [] } catch (error) { fail(error) } }
async function saveBase() { try { const saved = (await interviewApi.saveKnowledgeBase({ ...baseForm })).data; await loadBases(); await selectBase(saved); ElMessage.success('Knowledge base saved') } catch (error) { fail(error) } }
async function saveItem() { if (!selectedBase.value) return; try { await interviewApi.saveKnowledgeItem({ knowledgeBaseId: selectedBase.value.id, knowledgePoint: itemForm.knowledgePoint, knowledgeContent: itemForm.knowledgeContent, status: 1 }); itemForm.knowledgePoint = ''; itemForm.knowledgeContent = ''; await selectBase(selectedBase.value); ElMessage.success('Knowledge item added') } catch (error) { fail(error) } }
async function importItems({ file }) { try { const count = (await interviewApi.importKnowledgeItems(selectedBase.value.id, file)).data; await selectBase(selectedBase.value); ElMessage.success(`Imported ${count} items`) } catch (error) { fail(error) } }
async function saveTemplate() { try { await interviewApi.saveProcessTemplate({ id: templateForm.id, version: templateForm.version, templateName: templateForm.templateName, description: templateForm.description, status: templateForm.status, stages: [{ stageName: templateForm.stageName, stageType: 'AI', knowledgeBaseId: templateForm.knowledgeBaseId, roundKnowledgePoints: templateForm.roundKnowledgePoints, sequenceNo: 1 }] }); resetTemplate(); await loadTemplates(); ElMessage.success('Template saved') } catch (error) { fail(error) } }
function editTemplate(row) { const stage = row.stages?.[0] || {}; Object.assign(templateForm, { id: row.id, version: row.version, templateName: row.templateName, description: row.description || '', knowledgeBaseId: stage.knowledgeBaseId || null, stageName: stage.stageName || 'AI questions', roundKnowledgePoints: stage.roundKnowledgePoints || '', status: row.status }) }
onMounted(async () => { await Promise.all([loadBases(), loadTemplates()]) })
</script>

<style scoped>
.page{max-width:1440px;margin:0 auto;padding:30px}.page header{margin-bottom:22px}.page h1{margin:5px 0 8px;font-size:28px}.page header p:not(.page-eyebrow),.panel p{margin:0;color:var(--text-muted);line-height:1.6}.grid{display:grid;grid-template-columns:minmax(310px,.78fr) minmax(0,1.22fr);gap:24px}.panel{padding:20px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface)}.panel h2{margin:0;font-size:18px}.panel-head{display:flex;justify-content:space-between;gap:14px;align-items:flex-start;margin-bottom:16px}.actions{display:flex;gap:8px}.items{margin-top:24px}.item-form{display:grid;grid-template-columns:minmax(160px,.45fr) minmax(0,1fr) auto;gap:10px;margin-bottom:14px}@media(max-width:900px){.page{padding:18px}.grid{grid-template-columns:1fr}.item-form{grid-template-columns:1fr}.item-form .el-button{justify-self:start}}
</style>
