<template>
  <AdminNav />
  <main class="dashboard">
    <header><div><p class="page-eyebrow">Overview</p><h1>仪表盘</h1></div><el-button @click="configVisible = true">配置卡片</el-button></header>
    <el-alert v-if="loadError" class="load-error" type="error" :closable="false" show-icon>
      <template #title>仪表盘加载失败</template>
      <template #default>
        <span>{{ loadError }}</span>
        <el-button text type="primary" :loading="dashboardLoading" @click="load">重试</el-button>
      </template>
    </el-alert>
    <div class="cards">
      <article v-for="card in selectedCards" :key="card.id"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></article>
    </div>
    <div class="chart-grid">
      <section v-for="section in chartSections" :key="section.key">
        <div class="section-head"><h2>{{ section.label }}</h2><span>{{ chartTypeLabel(config.charts[section.key]) }}</span></div>
        <div v-show="config.charts[section.key] !== 'table'" :ref="(node) => setChartNode(section.key, node)" class="chart" />
        <el-table v-if="config.charts[section.key] === 'table'" :data="chartTableRows(section.key)" stripe>
          <el-table-column prop="name" label="项目" min-width="160" />
          <el-table-column prop="value" label="数值" min-width="120" />
        </el-table>
      </section>
    </div>
    <el-dialog v-model="configVisible" title="仪表盘配置" width="min(620px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="统计卡片"><el-checkbox-group v-model="config.cards"><el-checkbox v-for="card in cards" :key="card.id" :label="card.id">{{ card.label }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item v-for="section in chartSections" :key="section.key" :label="`${section.label}图表`">
          <el-radio-group v-model="config.charts[section.key]"><el-radio-button value="bar">柱状图</el-radio-button><el-radio-button value="pie">饼图</el-radio-button><el-radio-button value="table">表格</el-radio-button></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="configVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { hrApi } from '../services/api'

const dashboard = ref({})
const stats = ref(null)
const dashboardLoading = ref(false)
const loadError = ref('')
const configVisible = ref(false)
const chartNodes = {}
const chartInstances = {}
const chartSections = [
  { key: 'salary', label: '本月薪资' }, { key: 'recruitment', label: '岗位录取薪资' },
  { key: 'dismissal', label: '本月辞退原因' }, { key: 'department', label: '部门平均薪资' },
]
const defaultConfig = {
  cards: ['employeeCount', 'departmentCount', 'openJobCount', 'newHireCount', 'dismissalCount', 'averageGross'],
  charts: { salary: 'bar', recruitment: 'pie', dismissal: 'pie', department: 'bar' },
}
const config = reactive(JSON.parse(JSON.stringify(defaultConfig)))
let configSnapshot = null
let savingConfig = false
const cards = computed(() => [
  { id: 'employeeCount', label: '员工总数', value: dashboard.value.employeeCount || 0 },
  { id: 'activeEmployeeCount', label: '在册员工', value: dashboard.value.activeEmployeeCount || 0 },
  { id: 'departmentCount', label: '部门数', value: dashboard.value.departmentCount || 0 },
  { id: 'openJobCount', label: '开放岗位数', value: dashboard.value.openJobCount || 0 },
  { id: 'newHireCount', label: '本月新入职', value: dashboard.value.currentMonthHireCount || 0 },
  { id: 'dismissalCount', label: '本月辞退', value: dashboard.value.currentMonthDismissalCount || 0 },
  { id: 'averageGross', label: '全员平均税前薪资', value: money(stats.value?.salary?.averageGross ?? dashboard.value.averageGrossSalary) },
  { id: 'grossTotal', label: '全员税前薪资总额', value: money(stats.value?.salary?.grossTotal) },
  { id: 'salaryGrowth', label: '薪资总额环比', value: percent(stats.value?.salary?.monthOverMonth) },
  { id: 'candidateCount', label: '投递人员数', value: stats.value?.recruitment?.candidateCount || 0 },
  { id: 'interviewingCount', label: '面试中人员', value: stats.value?.recruitment?.interviewingCount || 0 },
  { id: 'passedCount', label: '面试通过人员', value: stats.value?.recruitment?.passedCount || 0 },
  { id: 'dismissalAverage', label: '辞退人员平均薪资', value: money(stats.value?.dismissal?.averageGross) },
  { id: 'departmentAverageCount', label: '平均部门人数', value: stats.value?.department?.averageEmployeeCount || 0 },
])
const selectedCards = computed(() => cards.value.filter((card) => config.cards.includes(card.id)))

function setChartNode(key, node) { if (node) chartNodes[key] = node }
function chartRows(key) {
  if (!stats.value) return []
  if (key === 'salary') return stats.value.salary.employees.map((item) => [item.employeeName, item.grossIncome])
  if (key === 'recruitment') return stats.value.recruitment.jobAverageSalaries.map((item) => [item.jobTitle, item.averageBaseSalary])
  if (key === 'dismissal') return stats.value.dismissal.reasons.map((item) => [item.name, item.value])
  return stats.value.department.averageSalaries.map((item) => [item.departmentName, item.averageGross])
}
function chartTableRows(key) { return chartRows(key).map(([name, value]) => ({ name, value })) }
function draw() {
  nextTick(() => chartSections.forEach(({ key }) => {
    const node = chartNodes[key]
    if (!node) return
    if (config.charts[key] === 'table') {
      chartInstances[key]?.clear()
      return
    }
    const chart = chartInstances[key] || (chartInstances[key] = echarts.init(node))
    chart.resize()
    const items = chartRows(key)
    const pie = config.charts[key] === 'pie'
    chart.setOption(pie ? {
      tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['34%', '68%'], data: items.map(([name, value]) => ({ name, value })) }],
    } : {
      tooltip: { trigger: 'axis' }, grid: { left: 52, right: 18, top: 20, bottom: 48 },
      xAxis: { type: 'category', data: items.map((item) => item[0]), axisLabel: { rotate: items.length > 7 ? 35 : 0 } },
      yAxis: { type: 'value' }, series: [{ type: 'bar', data: items.map((item) => item[1]), itemStyle: { color: '#27806f' } }],
    }, { notMerge: true })
  }))
}
function applyStoredConfig(stored) {
  const allowedCards = new Set(cards.value.map((card) => card.id))
  config.cards = Array.isArray(stored?.cards)
    ? stored.cards.filter((id) => typeof id === 'string' && allowedCards.has(id))
    : [...defaultConfig.cards]
  config.charts = Object.fromEntries(Object.entries(defaultConfig.charts).map(([key, fallback]) => [
    key,
    ['bar', 'pie', 'table'].includes(stored?.charts?.[key]) ? stored.charts[key] : fallback,
  ]))
}
watch(configVisible, (visible) => {
  if (visible) {
    configSnapshot = JSON.parse(JSON.stringify(config))
  } else if (configSnapshot && !savingConfig) {
    applyStoredConfig(configSnapshot)
  }
  if (!visible) {
    savingConfig = false
    configSnapshot = null
  }
})
async function load() {
  if (dashboardLoading.value) return
  dashboardLoading.value = true
  try {
    const [dashboardResponse, configResponse] = await Promise.all([
      hrApi.getDashboard(), hrApi.getDashboardConfig(),
    ])
    dashboard.value = dashboardResponse.data
    stats.value = dashboardResponse.data.statistics
    loadError.value = ''
    try { applyStoredConfig(JSON.parse(configResponse.data.configJson)) } catch { applyStoredConfig(defaultConfig) }
    draw()
  } catch (error) {
    loadError.value = error.message || '请稍后重试'
  } finally {
    dashboardLoading.value = false
  }
}
async function save() {
  try {
    savingConfig = true
    await hrApi.saveDashboardConfig(JSON.stringify(config))
    configVisible.value = false
    draw()
    ElMessage.success('仪表盘配置已保存')
  } catch (error) {
    savingConfig = false
    ElMessage.error(error.message)
  }
}
function money(value) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function percent(value) { return value === null || value === undefined ? '—' : `${Number(value).toFixed(2)}%` }
function chartTypeLabel(type) { return type === 'pie' ? '饼图' : type === 'table' ? '表格' : '柱状图' }
function resizeCharts() {
  Object.values(chartInstances).forEach((chart) => {
    const node = chart.getDom()
    if (node?.offsetParent !== null && node.clientWidth > 0 && node.clientHeight > 0) chart.resize()
  })
}

window.addEventListener('resize', resizeCharts)
onMounted(load)
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); Object.values(chartInstances).forEach((chart) => chart.dispose()) })
</script>

<style scoped>
.dashboard { max-width: 1440px; margin: auto; padding: 30px; }
.dashboard header, .section-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.dashboard h1 { margin: 4px 0; }
.load-error { margin-top: 24px; }
.load-error :deep(.el-alert__content) { min-width: 0; }
.load-error :deep(.el-alert__description) { display: flex; align-items: center; justify-content: space-between; gap: 14px; width: 100%; }
.cards { display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 14px; margin: 24px 0 30px; }
.cards article { padding: 16px 0; border-bottom: 2px solid #d8e4e1; }
.cards span { display: block; color: var(--text-muted); }
.cards strong { display: block; margin-top: 8px; font-size: 26px; }
.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 28px 22px; }
.chart-grid section { min-width: 0; }
.section-head h2 { margin: 0 0 12px; font-size: 18px; }
.section-head span { color: var(--text-muted); }
.chart { height: 300px; border: 1px solid var(--border); }
:deep(.el-checkbox-group) { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
@media (max-width: 760px) { .dashboard { padding: 16px; } .cards, .chart-grid { grid-template-columns: 1fr; } :deep(.el-checkbox-group) { grid-template-columns: 1fr; } }
</style>
