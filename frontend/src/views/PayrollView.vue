<template>
  <AdminNav />
  <main class="payroll-page">
    <header>
      <div><p class="page-eyebrow">Payroll</p><h1>薪资计算</h1></div>
      <el-button @click="refreshCurrent">刷新</el-button>
    </header>
    <el-alert title="计算结果仅供参考，以税务机关扣缴端核定为准。" type="warning" :closable="false" show-icon />
    <section class="filters">
      <el-date-picker v-model="month" type="month" value-format="YYYY-MM" />
      <el-select v-model="employeeId" clearable filterable placeholder="全部员工">
        <el-option v-for="employee in employees" :key="employee.id" :label="`${employee.employeeCode} · ${employee.fullName}`" :value="employee.id" />
      </el-select>
      <el-button type="primary" @click="generate">生成工资单</el-button>
      <el-button @click="downloadPayroll">导出 xlsx</el-button>
    </section>

    <el-tabs v-model="tab">
      <el-tab-pane v-for="section in inputSections" :key="section.tab" :label="section.label" :name="section.tab">
        <MonthlyForm
          :employees="employees" :month="month" :fields="section.fields" :entries="entries"
          :note-enabled="section.noteEnabled" @submit="section.save" @import="upload(section.kind, $event)"
          @template="downloadInputTemplate(section)" @refresh="loadInputs" @delete="deleteInput(section.kind, $event)"
        />
      </el-tab-pane>
      <el-tab-pane label="工资单" name="payroll">
        <el-table :data="payrolls" stripe>
          <el-table-column prop="employeeCode" label="工号" min-width="105" />
          <el-table-column prop="employeeName" label="员工" min-width="100" />
          <el-table-column prop="baseSalary" label="基本薪资" min-width="110" />
          <el-table-column prop="performance" label="绩效" min-width="100" />
          <el-table-column prop="overtimeHours" label="加班小时" min-width="100" />
          <el-table-column prop="overtimePay" label="加班费" min-width="100" />
          <el-table-column prop="grossIncome" label="税前薪资" min-width="110" />
          <el-table-column prop="socialInsuranceTotal" label="三险一金" min-width="110" />
          <el-table-column prop="specialDeductionTotal" label="专项扣除" min-width="110" />
          <el-table-column prop="currentTaxWithheld" label="本期个税" min-width="100" />
          <el-table-column prop="netPay" label="实发" min-width="110" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.locked ? 'danger' : 'success'">{{ row.locked ? '已锁定' : '未锁定' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!row.locked" text @click="lock(row)">锁定</el-button>
              <el-button v-if="row.locked && canUnlock" text @click="unlock(row)">解锁</el-button>
              <el-button v-if="!row.locked" text type="danger" @click="deletePayroll(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="importVisible" title="导入结果" width="min(680px, 92vw)">
      <el-table :data="importRows" max-height="420">
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="message" label="明细" min-width="260" />
      </el-table>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ExcelJS from 'exceljs'
import AdminNav from '../components/AdminNav.vue'
import MonthlyForm from '../components/MonthlyForm.vue'
import { downloadBlob, hrApi, payrollApi } from '../services/api'
import { currentMonth } from '../utils/date'
import { readSessionUser } from '../utils/session'

const month = ref(currentMonth())
const employeeId = ref(null)
const tab = ref('performance')
const employees = ref([])
const payrolls = ref([])
const entries = ref([])
const importVisible = ref(false)
const importRows = ref([])
const session = readSessionUser() || {}
const canUnlock = ['HR_ADMIN', 'IT_ADMIN'].includes(session.roleCode)

const performanceFields = [{ key: 'amount', label: '绩效金额' }]
const overtimeFields = [
  { key: 'overtimeHours', label: '加班小时数' },
  { key: 'unitRate', label: '生效单价', readonly: true },
  { key: 'overtimePay', label: '加班费', readonly: true },
]
const socialFields = [
  { key: 'pension', label: '基本养老保险' }, { key: 'medical', label: '基本医疗保险' },
  { key: 'unemployment', label: '失业保险' }, { key: 'housingFund', label: '住房公积金' },
]
const specialFields = [
  { key: 'childrenEducation', label: '子女教育' }, { key: 'continuingEducation', label: '继续教育' },
  { key: 'housingLoanInterest', label: '住房贷款利息' }, { key: 'housingRent', label: '住房租金' },
  { key: 'elderlySupport', label: '赡养老人' }, { key: 'infantCare', label: '婴幼儿照护' },
  { key: 'otherDeduction', label: '其他扣除' },
]

const inputSections = computed(() => [
  { tab: 'performance', kind: 'performance', label: '绩效', fields: performanceFields, noteEnabled: true, save: savePerformance },
  { tab: 'overtime', kind: 'overtime', label: '加班', fields: overtimeFields, noteEnabled: true, save: saveOvertime },
  { tab: 'social', kind: 'social-insurance', label: '三险一金', fields: socialFields, save: saveSocial },
  { tab: 'special', kind: 'special-deductions', label: '专项附加扣除', fields: specialFields, save: saveSpecial },
])
const currentSection = computed(() => inputSections.value.find((item) => item.tab === tab.value))

async function loadEmployees() {
  employees.value = (await hrApi.listAllEmployees()).data.filter((item) => item.salaryConfirmed === 1 && [1, 3].includes(item.employmentStatus))
}
async function loadPayroll() { payrolls.value = (await payrollApi.list({ salaryMonth: month.value, employeeId: employeeId.value || undefined })).data }
async function loadInputs() {
  if (!currentSection.value) return
  const rows = (await payrollApi.listInputs(currentSection.value.kind, { salaryMonth: month.value, employeeId: employeeId.value || undefined })).data
  entries.value = rows.map(normalizeKeys)
}
async function refreshCurrent() { if (tab.value === 'payroll') await loadPayroll(); else await loadInputs() }
async function generate() { try { await payrollApi.generate({ salaryMonth: month.value, employeeId: employeeId.value || null }); await loadPayroll(); tab.value = 'payroll'; ElMessage.success('工资单已生成') } catch (error) { ElMessage.error(error.message) } }
async function savePerformance(form) { await saveInput(() => payrollApi.savePerformance(form), '绩效已保存') }
async function saveOvertime(form) { await saveInput(() => payrollApi.saveOvertime(form), '加班已保存') }
async function saveSocial(form) { await saveInput(() => payrollApi.saveSocialInsurance(form), '三险一金已保存') }
async function saveSpecial(form) { await saveInput(() => payrollApi.saveSpecialDeduction(form), '专项附加扣除已保存') }
async function saveInput(action, message) { try { await action(); ElMessage.success(message); await loadInputs() } catch (error) { ElMessage.error(error.message) } }
async function upload(kind, file) { try { const result = (await payrollApi.import(kind, file)).data; importRows.value = result.rows || []; importVisible.value = true; ElMessage.success(`导入完成：成功 ${result.successCount} 行，失败 ${result.failureCount} 行`); await loadInputs() } catch (error) { ElMessage.error(error.message) } }
async function lock(row) { try { await payrollApi.lock(row.employeeId, month.value); await loadPayroll() } catch (error) { ElMessage.error(error.message) } }
async function unlock(row) { try { await payrollApi.unlock(row.employeeId, month.value); await loadPayroll() } catch (error) { ElMessage.error(error.message) } }
async function deletePayroll(row) { try { await ElMessageBox.confirm(`删除 ${row.employeeName} 的 ${month.value} 工资单？`, '删除工资单', { type: 'warning' }); await payrollApi.deletePayroll(row.employeeId, month.value); await loadPayroll() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message) } }
async function deleteInput(kind, row) { try { await ElMessageBox.confirm(`删除 ${row.employeeName} 的 ${row.salaryMonth} 录入数据？`, '删除月度数据', { type: 'warning' }); await payrollApi.deleteInput(kind, row.employeeId, row.salaryMonth); await loadInputs() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message) } }
async function downloadPayroll() { try { downloadBlob(await payrollApi.export({ salaryMonth: month.value, employeeId: employeeId.value || undefined }), `normal-wages-${month.value}.xlsx`) } catch (error) { ElMessage.error(error.message) } }

async function downloadInputTemplate(section) {
  const workbook = new ExcelJS.Workbook()
  const sheet = workbook.addWorksheet(section.label)
  const inputFields = section.fields.filter((field) => !field.readonly)
  sheet.columns = [
    { header: '工号', key: 'employeeCode', width: 18, style: { numFmt: '@' } },
    { header: '姓名', key: 'employeeName', width: 14 },
    { header: '月份', key: 'salaryMonth', width: 12, style: { numFmt: '@' } },
    ...inputFields.map((field) => ({ header: field.label, key: field.key, width: 18 })),
  ]
  sheet.getRow(1).font = { bold: true }
  sheet.getColumn(1).numFmt = '@'
  sheet.getColumn(3).numFmt = '@'
  const buffer = await workbook.xlsx.writeBuffer()
  downloadBlob(new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), `${section.kind}-import-template.xlsx`)
}

function normalizeKeys(row) {
  return Object.fromEntries(Object.entries(row).map(([key, value]) => [key.replace(/_([a-z])/g, (_, char) => char.toUpperCase()), value]))
}

watch([month, employeeId], refreshCurrent)
watch(tab, refreshCurrent)
onMounted(async () => { await loadEmployees(); await Promise.all([loadPayroll(), loadInputs()]) })
</script>

<style scoped>
.payroll-page { display: grid; gap: 18px; max-width: 1440px; margin: 0 auto; padding: 30px; }
.payroll-page > *, :deep(.monthly-form), :deep(.form-grid), :deep(.actions) { min-width: 0; }
.payroll-page header, .filters { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.payroll-page h1 { margin: 4px 0; }
.filters { justify-content: flex-start; padding: 14px 0; border-bottom: 1px solid var(--border); }
@media (max-width: 640px) { .payroll-page { padding: 16px; } .filters > * { width: 100%; } }
</style>
