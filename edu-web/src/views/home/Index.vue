<template>
  <div class="page" style="overflow: hidden">
    <!-- 指标卡：渐变描边数字，呼应登录页 -->
    <el-row :gutter="14">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <div class="stat-card panel">
          <div class="s-icon" :style="{ background: c.grad }">
            <el-icon :size="22"><component :is="c.icon" /></el-icon>
          </div>
          <div class="s-body">
            <div class="s-num">{{ c.value ?? '-' }}</div>
            <div class="s-label">{{ c.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="14">
        <div class="panel chart-panel">
          <div class="panel-title">近7日缴费趋势（元）</div>
          <div ref="trendRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="panel chart-panel">
          <div class="panel-title">课时占比（按课程）</div>
          <div ref="shareRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <div class="panel chart-panel">
          <div class="panel-title">教师课时投入排名</div>
          <div ref="teacherRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel chart-panel">
          <div class="panel-title">各学校缴费收入（元）</div>
          <div ref="schoolRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import api from '../../api'

const data = ref({})
const stat = ref({})
const charts = []
const trendRef = ref()
const shareRef = ref()
const teacherRef = ref()
const schoolRef = ref()

const cards = computed(() => [
  { label: '在读学生', value: stat.value.studyingCount, icon: 'Avatar', grad: 'linear-gradient(135deg,#0094FF,#33B0FF)' },
  { label: '在职教师', value: stat.value.teacherCount, icon: 'UserFilled', grad: 'linear-gradient(135deg,#0094FF,#8B6FF0)' },
  { label: '开课班级', value: stat.value.clazzCount, icon: 'Files', grad: 'linear-gradient(135deg,#7B5BE6,#B18CFF)' },
  { label: '缴费总收入(元)', value: stat.value.totalRevenue, icon: 'Money', grad: 'linear-gradient(135deg,#8B6FF0,#C0A8FF)' }
])

// 主色渐变
const BLUE = '#0094FF'
const PURPLE = '#8B6FF0'
const axisStyle = {
  axisLine: { lineStyle: { color: '#d4dde9' } },
  axisLabel: { color: '#667085' },
  splitLine: { lineStyle: { color: '#eef2f7' } }
}

function renderCharts() {
  // 1. 缴费趋势：渐变面积折线
  const trend = stat.value.revenueTrend || []
  const c1 = echarts.init(trendRef.value)
  c1.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: trend.map((t) => t.name), ...axisStyle },
    yAxis: { type: 'value', ...axisStyle },
    series: [{
      type: 'line', smooth: true, symbolSize: 7,
      data: trend.map((t) => Number(t.income)),
      lineStyle: { width: 3, color: BLUE },
      itemStyle: { color: BLUE },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,148,255,0.35)' },
          { offset: 1, color: 'rgba(0,148,255,0.02)' }
        ])
      }
    }]
  })
  charts.push(c1)

  // 2. 课时占比：蓝紫系饼图
  const c2 = echarts.init(shareRef.value)
  c2.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 课时 ({d}%)' },
    legend: { bottom: 0, type: 'scroll', textStyle: { color: '#667085' } },
    color: ['#0094FF', '#4FA8FF', '#7B5BE6', '#8B6FF0', '#B18CFF', '#C9B8FF'],
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{d}%', color: '#475467' },
      data: (stat.value.hoursShare || []).map((x) => ({ name: x.name, value: Number(x.hours) || 0 }))
    }]
  })
  charts.push(c2)

  // 3. 教师课时排名：横向渐变条
  const load = (stat.value.teacherLoad || []).slice(0, 8).reverse()
  const c3 = echarts.init(teacherRef.value)
  c3.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 80, right: 40, top: 10, bottom: 30 },
    xAxis: { type: 'value', ...axisStyle },
    yAxis: { type: 'category', data: load.map((t) => t.name), ...axisStyle, splitLine: { show: false } },
    series: [{
      type: 'bar', barWidth: 14, borderRadius: 7,
      data: load.map((t) => Number(t.scheduledHours) || 0),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: BLUE }, { offset: 1, color: PURPLE }
        ])
      }
    }]
  })
  charts.push(c3)

  // 4. 学校收入：双色柱
  const school = stat.value.revenueBySchool || []
  const c4 = echarts.init(schoolRef.value)
  c4.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 70, right: 30, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: school.map((s) => s.name), ...axisStyle, axisLabel: { color: '#667085', interval: 0 } },
    yAxis: { type: 'value', ...axisStyle },
    series: [{
      type: 'bar', barWidth: 30, borderRadius: [7, 7, 0, 0],
      data: school.map((s) => Number(s.income) || 0),
      itemStyle: { color: (p) => (p.dataIndex % 2 ? PURPLE : BLUE) }
    }]
  })
  charts.push(c4)
}

function onResize() { charts.forEach((c) => c.resize()) }

onMounted(async () => {
  const [ov, sum] = await Promise.all([api.overview(), api.statSummary()])
  data.value = ov || {}
  stat.value = { ...data.value, ...sum }
  await nextTick()
  renderCharts()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.stat-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px; margin-bottom: 8px;
  transition: transform 0.2s ease, box-shadow 0.25s ease;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 26px rgba(0, 148, 255, 0.16);
}
.s-icon {
  width: 46px; height: 46px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; box-shadow: 0 6px 14px rgba(0, 148, 255, 0.28);
}
.s-num { font-size: 24px; font-weight: 700; color: #142a44; line-height: 1.1; }
.s-label { font-size: 13px; color: #8fa8c4; margin-top: 4px; }
.chart-panel { height: 320px; }
.chart { height: calc(100% - 44px); }
</style>
