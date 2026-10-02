<template>
  <el-button type="primary" @click="open">AI 添加</el-button>
  <el-dialog v-model="visible" title="从教学材料生成知识库" width="min(900px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close">
    <p>添加到：{{ target?.knowledgeBaseName }}。上传教学参考材料、教学大纲或课件，由 AI 提炼知识点和教学内容。</p>
    <el-upload v-model:file-list="files" multiple :auto-upload="false" :limit="5" :disabled="busy" accept=".pdf,.docx,.pptx,.xls,.xlsx,.txt,.md,.csv" :on-exceed="exceed" :on-change="clearPreview" :on-remove="clearPreview">
      <el-button :disabled="busy">选择教学材料</el-button>
      <template #tip><div class="el-upload__tip">支持 PDF、DOCX、PPTX、Excel、UTF-8 文本；最多5份，每份10MB，总文字最多60000字。扫描件和图片需先转为文字。</div></template>
    </el-upload>
    <div class="generate-action"><el-button type="primary" :loading="generating" :disabled="saving || !files.length" @click="generate">{{ generating ? '正在整理材料，请稍候…' : 'AI 生成知识点' }}</el-button></div>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <template v-if="draft.length">
      <p>已生成 {{ draft.length }} 条，可编辑或移除后入库。入库会追加条目。</p>
      <div class="draft-list">
        <div v-for="(item, index) in draft" :key="index" class="draft-item">
          <el-input v-model="item.knowledgePoint" placeholder="知识点" maxlength="255" show-word-limit :disabled="busy" />
          <el-input v-model="item.knowledgeContent" type="textarea" :rows="3" placeholder="教学内容" maxlength="5000" show-word-limit :disabled="busy" />
          <el-button text type="danger" :disabled="busy" @click="draft.splice(index, 1)">移除</el-button>
        </div>
      </div>
    </template>
    <template #footer><el-button :disabled="busy" @click="visible = false">关闭</el-button><el-button type="primary" :loading="saving" :disabled="generating || !draft.length" @click="save">一键入库</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { interviewApi } from '../services/api'

const props = defineProps({ base: { type: Object, required: true } })
const emit = defineEmits(['saved'])
const visible = ref(false), files = ref([]), draft = ref([]), target = ref(null)
const generating = ref(false), saving = ref(false), errorMessage = ref('')
const busy = computed(() => generating.value || saving.value)
function open() { target.value = { ...props.base }; files.value = []; draft.value = []; errorMessage.value = ''; visible.value = true }
function close(done) { if (!busy.value) done() }
function exceed() { ElMessage.warning('最多上传5份材料') }
function clearPreview() { draft.value = []; errorMessage.value = '' }
async function generate() {
  if (files.value.some(file => !file.raw || file.size > 10 * 1024 * 1024 || file.size === 0)) { ElMessage.warning('请选择非空且不超过10MB的文件'); return }
  generating.value = true; errorMessage.value = ''; draft.value = []
  try {
    const result = await interviewApi.generateKnowledgeFromMaterials(target.value.id, files.value.map(file => file.raw))
    draft.value = result.data || []
    ElMessage.success(`已生成 ${draft.value.length} 条知识点，请检查后入库`)
  } catch (error) { errorMessage.value = error.message || '生成失败，请重试' }
  finally { generating.value = false }
}
async function save() {
  if (draft.value.some(item => !item.knowledgePoint.trim() || !item.knowledgeContent.trim())) { ElMessage.warning('知识点和教学内容不能为空'); return }
  saving.value = true; errorMessage.value = ''
  try {
    const result = await interviewApi.saveAiKnowledgeItems({ knowledgeBaseId: target.value.id, items: draft.value })
    ElMessage.success(`已添加 ${result.data.imported} 条知识点`)
    visible.value = false; draft.value = []; emit('saved', target.value.id)
  } catch (error) { errorMessage.value = error.message || '入库失败，请重试' }
  finally { saving.value = false }
}
</script>

<style scoped>
p{color:var(--text-muted);line-height:1.7}.generate-action{margin:18px 0}.draft-list{max-height:420px;overflow:auto}.draft-item{display:grid;grid-template-columns:1fr;gap:10px;padding:16px 0;border-bottom:1px solid var(--border)}.draft-item .el-button{justify-self:end}
</style>
