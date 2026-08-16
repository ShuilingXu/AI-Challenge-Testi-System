<template>
  <AdminNav />
  <main class="statistics">
    <header>
      <div><p class="page-eyebrow">Analytics</p><h1>员工统计</h1></div>
      <el-date-picker v-model="month" type="month" value-format="YYYY-MM" @change="load" />
    </header>
    <el-alert v-if="loadError" class="load-error" type="error" :closable="false" show-icon>
      <template #title>统计数据加载失败</template>
      <template #default>
        <span>{{ loadError }}</span>
        <el-button text type="primary" @click="load">重试</el-button>
      </template>
    </el-alert>
    <el-tabs v-show="!loadError" v-model="tab" @tab-change="draw">
      <el-tab-pane v-for="section in sections" :key="section.key" :label="section.label" :name="section.key">
        <div class="summary-grid">
          <article v-for="metric in summaries(section.key)" :key="metric.label">
            <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong>
          </article>
        </div>
        <div class="chart-toolbar">
          <span>图表类型</span>
          <el-radio-group v-model="chartTypes[section.key]" @change="draw">
            <el-radio-button value="bar">柱状图</el-radio-button>
            <el-radio-button value="pie">饼图</el-radio-button>
            <el-radio-button value="table">表格</el-radio-button>
          </el-radio-group>
        </div>
        <div v-show="chartTypes[section.key] !== 'table'" :ref="(element) => setChart(section.key, element)" class="chart" />
        <el-table v-if="chartTypes[section.key] === 'table'" :data="rows(section.key)" stripe empty-text="当前月份暂无数据">
          <el-table-column v-for="column in tableColumns[section.key]" :key="column.key" :prop="column.key" :label="column.label" :min-width="column.width || 130">
            <template #default="{ row }">{{ formatValue(row[column.key], column) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup>
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import AdminNav from '../components/AdminNav.vue'
import { hrApi } from '../services/api'
import { currentMonth } from '../utils/date'

const month = ref(currentMonth())
const tab = ref('salary')
const data = ref(null)
const loadError = ref('')
const charts = {}
const nodes = {}
const sections = [
  { key: 'salary', label: '员工薪资' }, { key: 'recruitment', label: '招聘情况' },
  { key: 'dismissal', label: '辞退情况' }, { key: 'department', label: '部门情况' },
]
const chartTypes = reactive({ salary: 'bar', recruitment: 'bar', dismissal: 'pie', department: 'bar' })
let loadTimer = null
let activeController = null
let disposed = false
const tableColumns = {
  salary: [
    { key: 'employeeCode', label: '工号' }, { key: 'employeeName', label: '员工' },
    { key: 'grossIncome', label: '税前薪资', money: true }, { key: 'monthOverMonth', label: '环比涨幅', percent: true },
    { key: 'newEmployeeGrowth', label: '新员工涨幅', percent: true },
  ],
  recruitment: [
    { key: 'jobCode', label: '岗位编码' }, { key: 'jobTitle', label: '岗位' },
    { key: 'averageBaseSalary', label: '录取平均薪资', money: true },
  ],
  dismissal: [{ key: 'name', label: '辞退原因' }, { key: 'value', label: '人数' }],
  department: [{ key: 'departmentName', label: '部门' }, { key: 'averageGross', label: '部门平均税前薪资', money: true }],
}

function summaries(key) {
  if (!data.value) return []
  if (key === 'salary') return [
    { label: '全员税前薪资总额', value: money(data.value.salary.grossTotal) },
    { label: '全员平均税前薪资', value: money(data.value.salary.averageGross) },
    { label: '总额环比上月', value: percent(data.value.salary.monthOverMonth) },
  ]
  if (key === 'recruitment') return [
    { label: '开放岗位数', value: data.value.recruitment.openJobCount },
    { label: '投递人员数', value: data.value.recruitment.candidateCount },
    { label: '面试中人员', value: data.value.recruitment.interviewingCount },
    { label: '通过人员', value: data.value.recruitment.passedCount },
  ]
  if (key === 'dismissal') return [
    { label: '当月辞退人数', value: data.value.dismissal.count },
    { label: '辞退人员平均薪资', value: money(data.value.dismissal.averageGross) },
  ]
  return [
    { label: '平均部门人数', value: data.value.department.averageEmployeeCount },
    { label: '平均招聘人数', value: data.value.department.averageHireCount },
    { label: '平均辞退人数', value: data.value.department.averageDismissalCount },
  ]
}

function rows(key) {
  if (!data.value) return []
  if (key === 'salary') return data.value.salary.employees
  if (key === 'recruitment') return data.value.recruitment.jobAverageSalaries
  if (key === 'dismissal') return data.value.dismissal.reasons
  return data.value.department.averageSalaries
}
function setChart(key, node) { if (node) nodes[key] = node }
function chartData(key) {
  const values = rows(key)
  if (key === 'salary') return values.map((item) => [item.employeeName, item.grossIncome])
  if (key === 'recruitment') return values.map((item) => [item.jobTitle, item.averageBaseSalary])
  if (key === 'dismissal') return values.map((item) => [item.name, item.value])
  return values.map((item) => [item.departmentName, item.averageGross])
}
function draw() {
  nextTick(() => {
    const key = tab.value
    const node = nodes[key]
    if (!node || chartTypes[key] === 'table') return
    const items = chartData(key)
    const chart = charts[key] || (charts[key] = echarts.init(node))
    chart.resize()
    const pie = chartTypes[key] === 'pie'
    chart.setOption(pie ? {
      tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['35%', '68%'], data: items.map(([name, value]) => ({ name, value })) }],
    } : {
      tooltip: { trigger: 'axis' }, grid: { left: 54, right: 24, top: 24, bottom: 58 },
      xAxis: { type: 'category', data: items.map((item) => item[0]), axisLabel: { rotate: items.length > 8 ? 35 : 0 } },
      yAxis: { type: 'value' }, series: [{ type: 'bar', data: items.map((item) => item[1]), itemStyle: { color: '#27806f' } }],
    }, { notMerge: true })
  })
}
function load() {
  if (disposed) return
  if (loadTimer) window.clearTimeout(loadTimer)
  loadTimer = window.setTimeout(async () => {
    activeController?.abort()
    activeController = new AbortController()
    const controller = activeController
    try {
      const response = await hrApi.statistics(month.value, controller.signal)
      if (!disposed && controller === activeController) {
        loadError.value = ''
        data.value = response.data
        draw()
      }
    } catch (error) {
      if (!disposed && controller === activeController && error.code !== 'ERR_CANCELED' && error.name !== 'CanceledError') {
        loadError.value = error.message || '请稍后重试'
      }
    }
  }, 250)
}
function money(value) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function percent(value) { return value === null || value === undefined ? '—' : `${Number(value).toFixed(2)}%` }
function formatValue(value, column) { if (column.money) return money(value); if (column.percent) return percent(value); return value ?? '—' }
function resizeCharts() {
  Object.values(charts).forEach((chart) => {
    const node = chart.getDom()
    if (node?.offsetParent !== null && node.clientWidth > 0 && node.clientHeight > 0) chart.resize()
  })
}

window.addEventListener('resize', resizeCharts)
load()
onBeforeUnmount(() => {
  disposed = true
  if (loadTimer) window.clearTimeout(loadTimer)
  activeController?.abort()
  window.removeEventListener('resize', resizeCharts)
  Object.values(charts).forEach((chart) => chart.dispose())
})
</script>

<style scoped>
.statistics { max-width: 1440px; margin: auto; padding: 30px; }
.statistics header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 16px; }
.statistics h1 { margin: 4px 0; }
.load-error { margin-bottom: 18px; }
.load-error :deep(.el-alert__content) { min-width: 0; }
.load-error :deep(.el-alert__description) { display: flex; align-items: center; justify-content: space-between; gap: 14px; width: 100%; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(160px, 1fr)); gap: 12px; margin: 8px 0 18px; }
.summary-grid article { padding: 14px 0; border-bottom: 2px solid #d8e4e1; }
.summary-grid span { display: block; color: var(--text-muted); }
.summary-grid strong { display: block; margin-top: 7px; font-size: 22px; }
.chart-toolbar { display: flex; justify-content: flex-end; align-items: center; gap: 12px; margin: 0 0 12px; }
.chart { height: 320px; margin-bottom: 18px; border: 1px solid var(--border); }
@media (max-width: 760px) { .statistics { padding: 16px; } .statistics header { align-items: flex-start; flex-direction: column; } .summary-grid { grid-template-columns: repeat(2, 1fr); } .chart-toolbar { justify-content: flex-start; flex-wrap: wrap; } }
</style>
