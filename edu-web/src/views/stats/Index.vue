<template>
  <div v-loading="loading">
    <el-card style="margin-bottom: 14px">
      <template #header>统计时间范围</template>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
        start-placeholder="开始日期" end-placeholder="结束日期" @change="load" />
    </el-card>

    <el-row :gutter="14">
      <el-col :span="12">
        <el-card><template #header>按学校维度收入</template>
          <el-table :data="data.revenueBySchool" size="small" stripe max-height="320">
            <el-table-column prop="name" label="学校" />
            <el-table-column prop="payCount" label="缴费笔数" width="100" />
            <el-table-column prop="income" label="收入(元)" width="120" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card><template #header>按学生维度收入</template>
          <el-table :data="data.revenueByStudent" size="small" stripe max-height="320">
            <el-table-column prop="name" label="学生" />
            <el-table-column prop="school" label="学校" />
            <el-table-column prop="income" label="收入(元)" width="120" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <el-card><template #header>按教师维度收入</template>
          <el-table :data="data.revenueByTeacher" size="small" stripe max-height="320">
            <el-table-column prop="name" label="教师" />
            <el-table-column prop="subject" label="科目" width="90" />
            <el-table-column prop="income" label="收入(元)" width="110" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card><template #header>课时占比（按课程）</template>
          <div ref="shareChartRef" style="height: 320px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 14px">
      <template #header>教师投入量 / 闲置量</template>
      <el-table :data="data.teacherLoad" stripe>
        <el-table-column prop="name" label="教师" width="110" />
        <el-table-column prop="subject" label="科目" width="90" />
        <el-table-column prop="scheduleCount" label="排课次数" width="100" />
        <el-table-column prop="scheduledHours" label="排课课时" width="100" />
        <el-table-column prop="completedHours" label="已完成课时" width="110" />
        <el-table-column label="投入率" min-width="200">
          <template #default="{ row }">
            <el-progress :percentage="usageRate(row)" :color="usageRate(row) > 60 ? '#67c23a' : '#e6a23c'" />
          </template>
        </el-table-column>
        <el-table-column label="闲置量(课时)" width="130">
          <template #default="{ row }">{{ idleHours(row) }}</template>
        </el-table-column>
      </el-table>
      <div class="tip">注：按所选时间范围月数 × 160 标准课时/月 计算教师容量。</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../../api'

const loading = ref(false)
const data = ref({})
const shareChartRef = ref()
const dateRange = ref(null)

const monthCount = () => {
  if (!dateRange.value) return 12
  const [b, e] = dateRange.value.map((s) => new Date(s))
  return Math.max(1, Math.round((e - b) / (30 * 86400000)))
}

const usageRate = (row) => {
  const capacity = monthCount() * 160
  return Math.min(100, Math.round(((row.scheduledHours || 0) / capacity) * 100))
}

const idleHours = (row) => {
  const capacity = monthCount() * 160
  return Math.max(0, (capacity - (row.scheduledHours || 0)).toFixed(1))
}

const load = async () => {
  loading.value = true
  try {
    const p = {}
    if (dateRange.value) { p.begin = dateRange.value[0]; p.end = dateRange.value[1] }
    data.value = (await api.statSummary(p)) || {}
    await nextTick()
    renderShare()
  } finally { loading.value = false }
}

const renderShare = () => {
  if (!shareChartRef.value) return
  const chart = echarts.init(shareChartRef.value)
  const list = data.value.hoursShare || []
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 课时 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['38%', '66%'],
      label: { formatter: '{b}\n{d}%' },
      data: list.map((x) => ({ name: x.name, value: Number(x.hours) || 0 }))
    }]
  })
}

onMounted(load)
</script>

<style scoped>
.tip { color: #999; font-size: 12px; margin-top: 8px; }
</style>
