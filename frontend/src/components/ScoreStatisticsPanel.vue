<template>
  <section class="score-statistics">
    <el-alert type="info" :closable="false" show-icon>
      <template #title>统计样本 {{ stats.sampleCount || 0 }} 份 · 排除无有效评分或评分不完整的已结束记录 {{ stats.excludedCount || 0 }} 份</template>
      <p>按已结束考试记录（含未通过）统计，每题满分 100，以有效题目平均分表示成绩。多场考试汇总仅描述当前样本；Z、PR、T 分按各考试内的筛选样本计算，不代表试卷已等值。</p>
    </el-alert>
    <h2>描述统计</h2>
    <div class="stat-grid"><article v-for="[key, label] in descriptive" :key="key"><span>{{ label }}</span><strong>{{ fmt(stats.description?.[key]) }}</strong></article><article><span>众数</span><strong>{{ stats.description?.modes?.length ? stats.description.modes.map(v => fmt(v)).join('、') : '—' }}</strong></article></div>
    <div ref="boxElement" class="box-chart" role="img" aria-label="成绩箱线图，显示四分位数、须线和离群值"></div>
    <p class="note">总体方差除以 N；四分位数采用线性插值；偏度为三阶标准化中心矩，峰度为超额峰度（正态参考值 0）。零方差、样本不足时不计算偏度或峰度。须线位于 1.5 × IQR 内，离群值仅提示复核。</p>
    <h2>达标与等级分布</h2>
    <div class="stat-grid"><article v-for="[key, label, rule] in rateMetrics" :key="key"><span>{{ label }}</span><strong>{{ fmt(stats.rates?.[key], '%') }}</strong><small>{{ rule }}</small></article></div>
    <el-table :data="stats.rates?.grades || []"><el-table-column prop="grade" label="等级（固定分档）" /><el-table-column prop="count" label="记录数" /><el-table-column label="占比"><template #default="{ row }"><el-progress v-if="row.rate != null" :percentage="Number(row.rate.toFixed(2))" /><span v-else>—</span></template></el-table-column></el-table>
    <h2>学生分层与标准分</h2>
    <p class="note">高分 ≥80，中分 60–&lt;80，低分 &lt;60。Z=(成绩−考试内均值)/标准差；T=50+10Z；PR=100×(低于该成绩人数+同分人数÷2)/样本数。同分采用中秩；零方差时 Z/T 留空。</p>
    <el-table :data="standardPage" max-height="420"><el-table-column prop="studentNo" label="学号" /><el-table-column prop="fullName" label="姓名" /><el-table-column prop="examName" label="考试" /><el-table-column prop="group" label="分层" /><el-table-column v-for="[key, label] in [['score','成绩'],['z','Z 分'],['pr','PR'],['t','T 分']]" :key="key" :label="label"><template #default="{ row }">{{ fmt(row[key]) }}</template></el-table-column><el-table-column prop="cohortSize" label="参照样本数" /></el-table>
    <el-pagination v-model:current-page="standardPageNo" :page-size="20" layout="total, prev, pager, next" :total="stats.standardScores?.length || 0" />
    <h2>知识点教学诊断</h2>
    <p class="note">知识点得分率先按每份记录内该知识点的题目取平均，再对记录等权平均。掌握率为知识点得分率 ≥80 的记录比例，未考查的知识点留空。</p>
    <el-table :data="stats.knowledge?.points || []"><el-table-column prop="knowledgePoint" label="知识点（薄弱优先）" /><el-table-column label="得分率"><template #default="{ row }">{{ fmt(row.scoreRate, '%') }}</template></el-table-column><el-table-column label="掌握率"><template #default="{ row }">{{ fmt(row.masteryRate, '%') }}</template></el-table-column><el-table-column prop="sampleCount" label="有效记录数" /></el-table>
    <div class="heatmap-scroll"><table class="heatmap"><caption>学生 × 知识点掌握热力图：红 &lt;60，黄 60–&lt;80，绿 ≥80</caption><thead><tr><th>学生 / 考试</th><th v-for="point in points" :key="point">{{ point }}</th></tr></thead><tbody><tr v-for="student in heatmapPage" :key="student.processId"><th>{{ student.fullName }}（{{ student.studentNo }}）<small>{{ student.examName }}</small></th><td v-for="point in points" :key="point" :class="heatClass(cellScore(student.processId, point))">{{ fmt(cellScore(student.processId, point)) }}</td></tr></tbody></table></div>
    <el-pagination v-model:current-page="heatmapPageNo" :page-size="20" layout="total, prev, pager, next" :total="stats.standardScores?.length || 0" />
    <h2>CTT 试卷与题目质量</h2>
    <p class="note">按考试单独计算，要求样本题目内容和知识点集合完全相同、无重复题。难度 P=题目均分/100（参考 0.3–0.7）；D 为总分最高与最低各 27% 样本的题目得分率之差（参考 ≥0.3，≥0.4 较好）。少于 4 人或分组边界同分时 D 留空。相关系数使用题目与其余题目总分的 Pearson 相关。</p>
    <el-empty v-if="!stats.ctt?.length" description="暂无有效同卷样本" />
    <article v-for="(exam, index) in stats.ctt || []" :key="index" class="ctt-paper">
      <h3>{{ exam.examName }} · {{ exam.sampleCount }} 份 · {{ exam.itemCount }} 道共同题</h3><p class="note">{{ exam.reason }}</p>
      <div class="stat-grid"><article><span>Cronbach's α</span><strong>{{ fmt(exam.alpha) }}</strong><small>参考 ≥0.7，≥0.8 更好</small></article><article><span>SEM（平均分尺度）</span><strong>{{ fmt(exam.sem) }}</strong><small>SD × √(1−α)，α∈[0,1]</small></article><article><span>分半信度</span><strong>{{ fmt(exam.splitHalf) }}</strong><small>奇偶题分半，Spearman–Brown 校正</small></article></div>
      <el-table :data="exam.items" max-height="360"><el-table-column prop="question" label="题目" min-width="240" show-overflow-tooltip /><el-table-column label="难度 P"><template #default="{ row }">{{ fmt(row.p) }}</template></el-table-column><el-table-column label="区分度 D"><template #default="{ row }">{{ fmt(row.d) }}</template></el-table-column><el-table-column label="校正题目—总分相关"><template #default="{ row }">{{ fmt(row.correlation) }}</template></el-table-column></el-table>
    </article>
    <el-collapse><el-collapse-item title="高级评价与所需数据" name="requirements"><el-table :data="requirements"><el-table-column prop="metric" label="指标" min-width="180" /><el-table-column prop="required" label="当前未计算：所需数据 / 条件" min-width="300" /></el-table><p class="note">参考区间用于提示，不是对试卷、学生或教师的自动结论。数据不足以支持因果归因；题目生成、评分方式和样本构成会影响统计解释。</p></el-collapse-item></el-collapse>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BoxplotChart, ScatterChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([BoxplotChart, ScatterChart, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps({ statistics: { type: Object, default: () => ({}) } })
const stats = computed(() => props.statistics || {})
const fmt = (value, suffix = '') => value == null || !Number.isFinite(Number(value)) ? '—' : `${Number(value).toFixed(2)}${suffix}`
const descriptive = [['mean','平均分'],['median','中位数'],['standardDeviation','标准差'],['variance','方差'],['minimum','最低分'],['maximum','最高分'],['range','极差'],['q1','第一四分位数'],['q3','第三四分位数'],['iqr','四分位距'],['skewness','偏度'],['excessKurtosis','超额峰度']]
const rateMetrics = [['pass','及格率','按每场考试设置的及格线'],['excellent','优秀率','成绩 ≥90'],['low','低分率','成绩 <40'],['mastery','成绩达标率','成绩 ≥80（不等于掌握 80% 知识点）']]
const requirements = [
  { metric: '选项 / 干扰项分析、点二列相关', required: '固定选择题、选项选择记录和二分计分；目前为开放式回答与连续评分。' },
  { metric: '重测信度、Kappa、ICC', required: '同卷重测数据、独立评分者配对评分及评分者标识。' },
  { metric: '内容 / 结构 / 效标效度', required: '命题蓝图和专家审查、能力维度标注、外部效标数据及验证模型。' },
  { metric: 'IRT：θ、a、b、c、信息函数、ICC / CAT', required: '稳定题目标识、足够的共同题作答矩阵、模型拟合与校准；CTT 得分率不能替代 IRT 参数。' },
  { metric: '增益分、进步率、达标进步率、VAM / SGP', required: '明确配对的前后测、可比较或等值成绩尺度；VAM 另需背景协变量，SGP 需纵向参照样本和条件百分位模型。' },
  { metric: '聚类、能力雷达、错题归因、学习行为相关', required: '可比较的多维成绩、能力标签、结构化错误分类以及作业 / 出勤 / 学习时长配对记录。当前支持按分数分层和知识点诊断。' }
]
const standardPageNo = ref(1), heatmapPageNo = ref(1)
const standardPage = computed(() => (stats.value.standardScores || []).slice((standardPageNo.value - 1) * 20, standardPageNo.value * 20))
const heatmapPage = computed(() => (stats.value.standardScores || []).slice((heatmapPageNo.value - 1) * 20, heatmapPageNo.value * 20))
const points = computed(() => (stats.value.knowledge?.points || []).map(p => p.knowledgePoint))
const cellIndex = computed(() => new Map((stats.value.knowledge?.cells || []).map(c => [JSON.stringify([c.processId, c.knowledgePoint]), c.scoreRate])))
const cellScore = (id, point) => cellIndex.value.get(JSON.stringify([id, point]))
const heatClass = v => v == null ? 'missing' : v >= 80 ? 'high' : v >= 60 ? 'middle' : 'low'
const boxElement = ref(null)
let chart, observer
function draw() {
  if (!boxElement.value) return
  chart ||= echarts.init(boxElement.value)
  const d = stats.value.description || {}
  chart.setOption({ tooltip: { trigger: 'item' }, grid: { left: 50, right: 24, top: 25, bottom: 40 }, xAxis: { type: 'category', data: ['成绩分布'] }, yAxis: { type: 'value', min: 0, max: 100 }, series: [
    { name: '下须 / Q1 / 中位数 / Q3 / 上须', type: 'boxplot', data: d.mean == null ? [] : [[d.lowerWhisker, d.q1, d.median, d.q3, d.upperWhisker]], itemStyle: { color: '#dbeafe', borderColor: '#2563eb' } },
    { name: '离群值', type: 'scatter', data: (d.outliers || []).map(v => [0, v]) }
  ] }, true)
}
watch(stats, async () => { standardPageNo.value = 1; heatmapPageNo.value = 1; await nextTick(); draw() })
onMounted(() => { draw(); observer = new ResizeObserver(() => chart?.resize()); observer.observe(boxElement.value) })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<style scoped>
.score-statistics { margin: 24px 0; padding: 24px; background: white; border: 1px solid #e2e8f0; border-radius: 16px; min-width: 0; }
h2 { margin: 28px 0 16px; font-size: 19px; } h3 { font-size: 16px; }
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin: 16px 0; }
.stat-grid article { padding: 16px; background: #f8fafc; border-radius: 10px; overflow-wrap: anywhere; }
.stat-grid span, .stat-grid small { display: block; color: #64748b; font-size: 13px; }
.stat-grid strong { display: block; margin: 8px 0; font-size: 23px; color: #0f172a; }
.note { font-size: 13px; color: #64748b; line-height: 1.8; } .box-chart { height: 260px; width: 100%; }
.heatmap-scroll { overflow: auto; max-height: 480px; margin-top: 20px; }.heatmap { border-collapse: separate; border-spacing: 4px; width: 100%; font-size: 13px; }
.heatmap caption { text-align: left; padding: 12px; color: #64748b; }.heatmap th, .heatmap td { padding: 10px; min-width: 90px; text-align: center; border-radius: 4px; }.heatmap th { background: #f1f5f9; }.heatmap small { display: block; color: #64748b; margin-top: 5px; }
.high { background: #dcfce7; color: #166534; }.middle { background: #fef3c7; color: #92400e; }.low { background: #fee2e2; color: #991b1b; }.missing { background: #f8fafc; color: #64748b; }
.ctt-paper { border-top: 1px solid #e2e8f0; padding: 12px 0; }.el-pagination { margin: 16px 0; }.el-collapse { margin-top: 24px; }
@media (max-width: 640px) { .score-statistics { padding: 14px; }.stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
