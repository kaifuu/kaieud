<template>
  <div class="page" style="overflow: auto">
    <!-- 月度看板 -->
    <el-row :gutter="14">
      <el-col :span="5" v-for="c in dashCards" :key="c.label">
        <div class="panel" style="padding: 16px 18px; display: flex; align-items: center; gap: 12px">
          <div class="d-num">{{ c.value ?? '-' }}<span v-if="c.suffix" class="d-suffix">{{ c.suffix }}</span></div>
          <div class="d-label">{{ c.label }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="panel" style="padding: 16px 18px">
          <el-date-picker v-model="month" type="month" value-format="YYYY-MM" placeholder="统计月份" style="width: 100%" />
        </div>
      </el-col>
    </el-row>

    <!-- 明细报表 -->
    <div class="panel table-panel" style="height: auto; margin-top: 14px">
      <div class="toolbar">
        <el-select v-model="query.teacherId" placeholder="教师" clearable filterable style="width: 140px">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="query.courseId" placeholder="课程" clearable filterable style="width: 160px">
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.classType" placeholder="班型" clearable style="width: 120px">
          <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>
        <el-button type="primary" @click="loadAll">查询</el-button>
        <div style="flex: 1"></div>
        <el-button @click="doExport">导出明细</el-button>
      </div>
      <el-table :data="detail" v-loading="loading" stripe max-height="360" size="small">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="consumeDate" label="消课日期" width="100" />
        <el-table-column prop="studentName" label="学员" width="90" />
        <el-table-column prop="courseName" label="课程" min-width="130" />
        <el-table-column label="班型" width="80">
          <template #default="{ row }">{{ dictStore.label('class_type', row.classType) }}</template>
        </el-table-column>
        <el-table-column prop="teacherName" label="教师" width="90" />
        <el-table-column prop="hours" label="课时" width="70" />
        <el-table-column prop="revenue" label="营收(元)" width="95" />
        <el-table-column prop="teacherCost" label="教师成本(元)" width="110" />
        <el-table-column prop="profit" label="毛利(元)" width="95" />
        <el-table-column label="成本占比" width="100">
          <template #default="{ row }">
            <span :style="row.costRatio > 50 ? 'color:#f56c6c' : ''">{{ row.costRatio }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="contractNo" label="关联合同" width="160" />
      </el-table>
    </div>

    <!-- 汇总报表 -->
    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="12">
        <div class="panel" style="padding: 8px">
          <div class="toolbar">
            <span class="panel-title" style="padding: 0">按教师汇总</span>
            <div style="flex: 1"></div>
            <el-button size="small" type="primary" @click="exportPayroll">导出薪酬核算单</el-button>
          </div>
          <el-table :data="summaryTeacher" size="small" stripe max-height="300">
            <el-table-column prop="name" label="教师" width="90" />
            <el-table-column prop="totalHours" label="消课课时" width="90" />
            <el-table-column prop="totalRevenue" label="营收" width="90" />
            <el-table-column prop="totalCost" label="教师成本" width="90" />
            <el-table-column prop="totalProfit" label="毛利" width="90" />
            <el-table-column prop="avgCostRatio" label="成本占比%" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel" style="padding: 8px">
          <div class="toolbar">
            <span class="panel-title" style="padding: 0">按课程汇总</span>
          </div>
          <el-table :data="summaryCourse" size="small" stripe max-height="300">
            <el-table-column prop="name" label="课程" width="130" />
            <el-table-column prop="totalHours" label="消课课时" width="90" />
            <el-table-column prop="totalRevenue" label="营收" width="90" />
            <el-table-column prop="totalCost" label="教师成本" width="90" />
            <el-table-column prop="totalProfit" label="毛利" width="90" />
            <el-table-column prop="avgCostRatio" label="成本占比%" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import api from '../../api'
import { exportXlsx } from '../../utils/export'
import { useDictStore } from '../../store/dict'

const month = ref(new Date().toISOString().slice(0, 7))
const query = reactive({ teacherId: null, courseId: null, classType: null })
const dash = ref({})
const detail = ref([])
const summaryTeacher = ref([])
const summaryCourse = ref([])
const loading = ref(false)
const dictStore = useDictStore()
const teachers = ref([])
const courses = ref([])

const period = computed(() => {
  if (month.value) {
    const [y, m] = month.value.split('-')
    const lastDay = new Date(Number(y), Number(m), 0).getDate()
    return { begin: `${month.value}-01`, end: `${month.value}-${lastDay}` }
  }
  return { begin: undefined, end: undefined }
})

const dashCards = computed(() => [
  { label: '总消课课时', value: dash.value.totalHours },
  { label: '总营收(元)', value: dash.value.totalRevenue },
  { label: '总教师成本(元)', value: dash.value.totalCost },
  { label: '总毛利(元)', value: dash.value.totalProfit },
  { label: '教师成本占比', value: dash.value.costRatio, suffix: '%' }
])

async function loadAll() {
  loading.value = true
  try {
    const p = { ...period.value, ...query }
    const [d, st, sc] = await Promise.all([
      api.costDetail(p),
      api.costSummary({ ...period.value, groupBy: 'teacher' }),
      api.costSummary({ ...period.value, groupBy: 'course' })
    ])
    detail.value = d || []
    summaryTeacher.value = st || []
    summaryCourse.value = sc || []
    dash.value = (await api.costDashboard(period.value)) || {}
  } finally { loading.value = false }
}

watch(month, loadAll)

/** 兼职老师薪酬核算单导出（按教师汇总） */
function exportPayroll() {
  exportXlsx('兼职老师薪酬核算单', [
    { label: '教师', prop: 'name' },
    { label: '消课课时', prop: 'totalHours' },
    { label: '营收(元)', prop: 'totalRevenue' },
    { label: '教师成本(元)', prop: 'totalCost' },
    { label: '毛利(元)', prop: 'totalProfit' },
    { label: '成本占比(%)', prop: 'avgCostRatio' }
  ], summaryTeacher.value)
}

function doExport() {
  exportXlsx('消课成本明细', [
    { label: '消课日期', prop: 'consumeDate' }, { label: '学员', prop: 'studentName' },
    { label: '课程', prop: 'courseName' }, { label: '教师', prop: 'teacherName' },
    { label: '课时', prop: 'hours' }, { label: '营收', prop: 'revenue' },
    { label: '教师成本', prop: 'teacherCost' }, { label: '毛利', prop: 'profit' },
    { label: '成本占比%', prop: 'costRatio' }, { label: '合同', prop: 'contractNo' }
  ], detail.value)
}

onMounted(async () => {
  dictStore.load(true)
  loadAll()
  teachers.value = (await api.teacherList()) || []
  courses.value = (await api.courseList()) || []
})
</script>

<style scoped>
.d-num { font-size: 26px; font-weight: 700;
  background: linear-gradient(135deg, #1d2939 20%, #155EEF 90%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.d-suffix { font-size: 14px; }
.d-label { font-size: 13px; color: #8fa8c4; }
</style>
