<template>
  <div class="monthly-form">
    <div class="form-grid">
      <el-form-item label="员工">
        <el-select v-model="form.employeeId" filterable>
          <el-option v-for="employee in employees" :key="employee.id" :label="`${employee.employeeCode} · ${employee.fullName}`" :value="employee.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="薪资月"><el-date-picker v-model="form.salaryMonth" type="month" value-format="YYYY-MM" /></el-form-item>
      <el-form-item v-for="field in inputFields" :key="field.key" :label="field.label">
        <el-input-number v-model="form[field.key]" :min="0" :precision="2" />
      </el-form-item>
      <el-form-item v-if="noteEnabled" label="备注"><el-input v-model="form.note" maxlength="500" /></el-form-item>
    </div>
    <div class="actions">
      <el-button type="primary" @click="submit">保存</el-button>
      <el-button @click="$emit('template')">下载导入模板</el-button>
      <el-upload :show-file-list="false" accept=".xlsx" :http-request="({ file }) => $emit('import', file)">
        <el-button>批量导入 xlsx</el-button>
      </el-upload>
      <el-button @click="$emit('refresh')">刷新列表</el-button>
    </div>
    <el-table :data="entries" stripe class="entries-table" empty-text="本月暂无录入数据">
      <el-table-column prop="employeeCode" label="工号" min-width="110" />
      <el-table-column prop="employeeName" label="姓名" min-width="100" />
      <el-table-column prop="salaryMonth" label="月份" min-width="100" />
      <el-table-column v-for="field in fields" :key="field.key" :prop="field.key" :label="field.label" min-width="130" />
      <el-table-column v-if="noteEnabled" prop="note" label="备注" min-width="150" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }"><el-button text type="danger" @click="$emit('delete', row)">删除</el-button></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({ employees: Array, month: String, fields: Array, entries: Array, noteEnabled: Boolean })
const emit = defineEmits(['submit', 'import', 'template', 'refresh', 'delete'])
const form = reactive({ employeeId: null, salaryMonth: props.month, note: '' })
const inputFields = computed(() => props.fields.filter((field) => !field.readonly))

watch(() => props.month, (value) => { form.salaryMonth = value })
watch(() => inputFields.value, (fields) => fields.forEach((field) => {
  if (form[field.key] === undefined) form[field.key] = 0
}), { immediate: true })

function submit() { emit('submit', { ...form }) }
</script>

<style scoped>
.monthly-form { display: grid; gap: 18px; }
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 10px 16px; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; }
.entries-table { width: 100%; }
@media (max-width: 700px) { .form-grid { grid-template-columns: 1fr; } .actions > * { flex: 1 1 160px; } }
</style>
