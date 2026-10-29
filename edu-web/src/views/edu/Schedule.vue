<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">排课管理</span>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新增排课</el-button>
      </div>
    </div>

    <!-- 一级视图TAB：日视图 / 周视图 / 月视图 / 列表 / 排课历史 -->
    <el-tabs v-model="viewTab" class="view-tabs" @tab-change="onViewTab">
      <el-tab-pane label="日视图" name="day" />
      <el-tab-pane label="周视图" name="week" />
      <el-tab-pane label="月视图" name="month" />
      <el-tab-pane label="列表" name="list" />
      <el-tab-pane label="排课历史" name="history" />
    </el-tabs>

    <!-- ========== 日/周/月日历视图：双击日期看当日详情，拖拽调时，框选新增 ========== -->
    <div v-show="isCalView" class="panel cal-panel"
         style="height: calc(100% - 90px); padding: 12px"
         title="双击日期查看当日课次详情"
         @dblclick="onCalDblClick">
      <FullCalendar ref="calRef" :options="calOptions" />
    </div>

    <!-- ========== 日期详情抽屉：当日课次的教师/课程/班型/学员基础信息 ========== -->
    <el-drawer v-model="dayDetailVisible" :title="dayDetailTitle" size="520px">
      <div v-loading="dayLoading">
        <div v-for="item in dayDetail" :key="item.id" class="panel" style="padding: 12px 16px; margin-bottom: 12px">
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
            <el-tag effect="dark" size="small">{{ item.time }}</el-tag>
            <el-tag effect="plain" size="small" type="warning">{{ item.classType }}</el-tag>
            <span style="font-weight: 600">{{ item.courseName }}</span>
            <span style="color: #909399; font-size: 12px">{{ item.classroom }}</span>
          </div>
          <div style="font-size: 13px; margin-bottom: 4px">
            授课教师：<b>{{ item.teacherName }}</b>（{{ item.teacherTitle || '教师' }}）
          </div>
          <div style="font-size: 12.5px; color: #667085; margin-bottom: 4px">
            教师介绍：{{ item.teacherIntro || '暂无' }}
          </div>
          <div style="font-size: 12.5px; color: #667085; margin-bottom: 6px">
            课程介绍：{{ item.courseIntro || item.courseSubject || '暂无' }}
          </div>
          <div style="font-size: 13px">
            <b>学员（{{ (item.students || []).length }}）</b>
            <div v-for="st in item.students" :key="st.name" style="padding: 3px 0; color: #475467">
              {{ st.name }}（{{ st.gender }} / {{ st.grade || '-' }} / {{ st.school || '-' }}）
              <span style="color: #98a2b3">家长 {{ st.parentPhone }}</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!dayDetail.length && !dayLoading" description="当日无排课" :image-size="60" />
      </div>
    </el-drawer>

    <!-- ========== 列表 TAB：4个阶段子TAB（徽标数与拆分一致） ========== -->
    <div v-show="viewTab === 'list'" class="panel" style="padding: 0 16px; margin-bottom: 0">
      <el-tabs v-model="phase" class="phase-tabs" @tab-change="onPhaseChange">
        <el-tab-pane name="tobe">
          <template #label>待排课<el-badge v-if="counts.tobe" :value="counts.tobe" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="today">
          <template #label>待上课<el-badge v-if="counts.today" :value="counts.today" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="ongoing">
          <template #label>上课中<el-badge v-if="counts.ongoing" :value="counts.ongoing" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="done">
          <template #label>已下课<el-badge v-if="counts.done" :value="counts.done" :max="99" class="phase-badge" /></template>
        </el-tab-pane>
      </el-tabs>
    </div>
    <div v-show="viewTab === 'list'" class="panel table-panel" style="height: calc(100% - 136px)">
      <div class="toolbar">
        <el-select v-model="filters.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="onFilterChange">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.clazzId" placeholder="班级" clearable filterable style="width: 160px">
          <el-option v-for="c in clazzes" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="filters.orgId" placeholder="校区" clearable filterable style="width: 140px" @change="onFilterChange">
          <el-option v-for="o in orgList" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="schedule" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.startTime" label="上课时间" width="290" sortable="custom">
          <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teacherId" label="教师" width="100">
          <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.clazzId" label="班级/学生" min-width="140">
          <template #default="{ row }">{{ row.clazzId ? clazzName(row.clazzId) : studentName(row.studentId) + '(一对一)' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classroom" prop="classroom" label="教室" width="95" />
        <el-table-column v-if="cols.status" label="状态" width="105" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'danger'][row.status]" effect="plain">
              {{ ['待上课', '已完成', '已取消'][row.status] }}
            </el-tag>
            <el-tag v-if="row.compensation === 1" size="small" type="info" effect="plain">补</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="openAdjust(row)">调时</el-button>
              <el-button link type="success" @click="doComplete(row)">完成</el-button>
              <el-button link type="danger" @click="doCancel(row)">取消</el-button>
            </template>
            <el-button v-if="row.status === 2" link type="warning" @click="openCompensate(row)">补课</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- ========== 排课历史 TAB：只读操作记录 ========== -->
    <div v-show="viewTab === 'history'" class="panel" style="padding: 12px 16px; height: calc(100% - 90px); display: flex; flex-direction: column">
      <div class="toolbar">
        <el-select v-model="filters.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="loadHistory">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.orgId" placeholder="校区" clearable filterable style="width: 140px" @change="loadHistory">
          <el-option v-for="o in orgList" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-date-picker v-model="historyRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" @change="loadHistory" />
        <el-button @click="loadHistory">刷新</el-button>
        <div style="flex: 1"></div>
        <el-button @click="doExportHistory">导出历史</el-button>
      </div>
      <el-table :data="historyRows" v-loading="historyLoading" stripe size="small" style="flex: 1; overflow-y: auto">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="操作类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="opTypeTag(row.opType)" effect="light">{{ row.opType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="教师" width="90" />
        <el-table-column prop="courseName" label="课程" min-width="150" />
        <el-table-column prop="studentName" label="学员" width="90">
          <template #default="{ row }">{{ row.studentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="课时" width="80">
          <template #default="{ row }">{{ row.hours ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="classroom" label="教室" width="90">
          <template #default="{ row }">{{ row.classroom || '-' }}</template>
        </el-table-column>
        <el-table-column label="记录状态" width="100">
          <template #default="{ row }">{{ row.recordStatus }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90">
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column prop="opTime" label="操作时间" width="170" />
      </el-table>
    </div>

    <!-- 新增排课 -->
    <el-drawer v-model="createVisible" title="新增排课" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.mode">
            <el-radio value="clazz">班课</el-radio>
            <el-radio value="one2one">一对一</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'clazz'" label="班级" required>
          <el-select v-model="form.clazzId" style="width: 100%" @change="onClazzChange">
            <el-option v-for="c in clazzes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="学生" required>
          <el-select v-model="form.studentId" filterable style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师" required>
          <el-select v-model="form.teacherId" filterable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="form.classroomId" filterable clearable style="width: 100%"
                     placeholder="选择教室（显示可容纳人数）" @change="onRoomChange">
            <el-option v-for="r in classrooms" :key="r.id"
                       :label="`${r.name}（容纳${r.capacity}人）`" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">保存</el-button>
      </template>
    </el-drawer>

    <!-- 调时/补课 -->
    <el-dialog v-model="timeVisible" :title="timeMode === 'adjust' ? '调整上课时间' : '安排补课'" width="430px">
      <el-form label-width="90px">
        <el-form-item label="开始时间">
          <el-date-picker v-model="timeForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="timeForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item v-if="timeMode === 'adjust'" label="调时原因"><el-input v-model="timeForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTime">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import { exportXlsx } from '../../utils/export'

/* ===== 一级视图与筛选 ===== */
const viewTab = ref('week') // day / week / month / list / history

/** 日/周/月日历视图可见性（非列表、非历史即为日历视图） */
const isCalView = computed(() => ['day', 'week', 'month'].includes(viewTab.value))
const dateRange = ref(null)
const historyRange = ref(null)
const filters = reactive({ teacherId: null, clazzId: null, status: null, orgId: null })
const phase = ref('tobe')
const counts = reactive({ tobe: 0, today: 0, ongoing: 0, done: 0 })

const teachers = ref([])
const clazzes = ref([])
const students = ref([])
const orgList = ref([])
const classrooms = ref([])
const teacherName = (id) => teachers.value.find((t) => t.id === id)?.name || '-'
const clazzName = (id) => clazzes.value.find((c) => c.id === id)?.name || '-'
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'

/* ===== 列表 TAB（4个阶段子TAB，后端 phase 过滤保持一致） ===== */
const listQuery = reactive({ keyword: '', page: 1 })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => {
    const p = { ...q, phase: phase.value, teacherId: filters.teacherId,
      clazzId: filters.clazzId, orgId: filters.orgId,
      begin: dateRange.value?.[0], end: dateRange.value?.[1] }
    delete p.status
    return api.schedulePage(p)
  }, listQuery)

function onPhaseChange() { listQuery.page = 1; load() }

async function loadPhaseCounts() {
  try {
    const d = (await api.schedulePhaseCounts({
      teacherId: filters.teacherId, clazzId: filters.clazzId, orgId: filters.orgId
    })) || {}
    Object.assign(counts, d)
  } catch (e) { /* ignore */ }
}
watch([phase, filters.teacherId, filters.clazzId, filters.orgId], () => loadPhaseCounts())

/* ===== 排课历史 TAB ===== */
const historyRows = ref([])
const historyLoading = ref(false)
async function loadHistory() {
  historyLoading.value = true
  try {
    historyRows.value = (await api.scheduleHistory({
      teacherId: filters.teacherId, orgId: filters.orgId,
      begin: historyRange.value?.[0], end: historyRange.value?.[1]
    })) || []
  } finally { historyLoading.value = false }
}
watch(historyRange, loadHistory)
function opTypeTag(t) {
  return { '排课': 'primary', '消课': 'success', '撤销消课': 'info', '补课': 'warning' }[t] || 'info'
}
function doExportHistory() {
  exportXlsx('排课历史', [
    { label: '操作类型', prop: 'opType' }, { label: '教师', prop: 'teacherName' },
    { label: '课程', prop: 'courseName' }, { label: '学员', prop: 'studentName' },
    { label: '课时', prop: 'hours' }, { label: '教室', prop: 'classroom' },
    { label: '记录状态', prop: 'recordStatus' }, { label: '操作人', prop: 'operator' },
    { label: '操作时间', prop: 'opTime' }
  ], historyRows.value)
}

/* ===== 日历视图（日/周/月共用一个 FullCalendar） ===== */
const calRef = ref()
const rawSchedules = ref([])

async function loadCalEvents(info, successCallback, failureCallback) {
  try {
    const d = await api.schedulePage({
      page: 1, size: 500,
      begin: (info.startStr || '').slice(0, 10),
      end: (info.endStr || '').slice(0, 10),
      teacherId: filters.teacherId, clazzId: filters.clazzId, orgId: filters.orgId
    })
    const records = d.records || []
    rawSchedules.value = records
    successCallback(records.map((r) => ({
      id: String(r.id),
      title: (r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId)) + ' · ' + teacherName(r.teacherId)
             + (r.classroom ? ' @' + r.classroom : ''),
      start: r.startTime, end: r.endTime,
      backgroundColor: ['#0094FF', '#12b76a', '#c0c4cc'][r.status],
      borderColor: 'transparent', editable: r.status === 0
    })))
  } catch (e) { failureCallback(e) }
}

function onEventDrop(info) {
  api.scheduleAdjust(Number(info.event.id), {
    startTime: info.event.startStr, endTime: info.event.endStr, reason: '日历拖拽调整'
  }).then(() => { ElMessage.success('上课时间已调整'); load(); loadPhaseCounts() })
    .catch(() => info.revert())
}

function onEventClick(info) {
  const raw = rawSchedules.value.find((r) => String(r.id) === info.event.id)
  if (raw) openAdjust(raw)
}

function onSelect(info) {
  openCreate()
  form.value.startTime = info.startStr
  form.value.endTime = info.endStr
}

/* ===== 视图切换：日历 resize / 列表与历史加载 ===== */
watch(viewTab, async (v) => {
  await nextTick()
  if (v === 'day' || v === 'week' || v === 'month') {
    const api = calRef.value?.getApi?.()
    if (api) {
      const viewMap = { day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' }
      api.changeView(viewMap[v])
      setTimeout(() => { if (typeof api.resize === 'function') api.resize() }, 50)
    }
  } else if (v === 'history') {
    loadHistory()
  } else if (v === 'list') {
    load(); loadPhaseCounts()
  }
})

/* ===== 列配置 / 导出 ===== */
const allColumns = [
  { prop: 'startTime', label: '上课时间' }, { prop: 'teacherId', label: '教师' },
  { prop: 'clazzId', label: '班级/学生' }, { prop: 'classroom', label: '教室' },
  { prop: 'status', label: '状态' }
]
const cols = reactive({ startTime: true, teacherId: true, clazzId: true, classroom: true, status: true })
const onCols = (c) => Object.assign(cols, c)

function doExport() {
  exportXlsx('排课列表', [
    { label: '上课时间', prop: 'startTime' }, { label: '结束时间', prop: 'endTime' },
    { label: '教室', prop: 'classroom' },
    { label: '状态', prop: 'status', fmt: (v) => ['待上课', '已完成', '已取消'][v] }
  ], rows.value)
}

watch(dateRange, search)

/* ===== 新增排课 ===== */
const createVisible = ref(false)
const saving = ref(false)
const form = ref({})
const onClazzChange = (id) => {
  const c = clazzes.value.find((x) => x.id === id)
  if (c) form.value.teacherId = c.classTeacherId
}
function openCreate() { form.value = { mode: 'clazz' }; createVisible.value = true }
function onRoomChange(id) {
  const r = classrooms.value.find((x) => x.id === id)
  if (r) form.value.classroom = r.name
}
async function saveCreate() {
  const f = form.value
  if (f.mode === 'clazz') {
    const c = clazzes.value.find((x) => x.id === f.clazzId)
    f.courseId = c?.courseId
    f.studentId = null
  } else {
    f.clazzId = null
    const enrolls = await api.enrollPage({ page: 1, size: 1, studentId: f.studentId, status: 1 })
    f.courseId = enrolls.records?.[0]?.courseId
  }
  if (!f.courseId) return ElMessage.warning('未找到对应课程，请先为学生报名')
  if (!f.startTime || !f.endTime) return ElMessage.warning('请选择上课时间')
  saving.value = true
  try {
    f.classroom = classrooms.value.find((x) => x.id === Number(f.classroomId))?.name || f.classroom
    await api.scheduleCreate(f)
    ElMessage.success('排课成功')
    createVisible.value = false
    load(); loadPhaseCounts()
  } finally { saving.value = false }
}

/* ===== 完成/取消/调时/补课 ===== */
async function doComplete(row) {
  await ElMessageBox.confirm('确认该课次已完成？将扣减学生课时', '提示', { type: 'warning' })
  await api.scheduleComplete(row.id)
  ElMessage.success('已完成')
  load(); loadPhaseCounts()
}
async function doCancel(row) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消课次')
  await api.scheduleCancel(row.id, { reason: value || '' })
  ElMessage.success('已取消')
  load(); loadPhaseCounts()
}

const timeVisible = ref(false)
const timeMode = ref('adjust')
const timeForm = ref({})
const currentRow = ref(null)
function openAdjust(row) {
  timeMode.value = 'adjust'; currentRow.value = row
  timeForm.value = { startTime: row.startTime, endTime: row.endTime, reason: '' }
  timeVisible.value = true
}
function openCompensate(row) {
  timeMode.value = 'compensate'; currentRow.value = row
  timeForm.value = { startTime: '', endTime: '' }
  timeVisible.value = true
}
async function saveTime() {
  if (timeMode.value === 'adjust') {
    await api.scheduleAdjust(currentRow.value.id, timeForm.value)
    ElMessage.success('已调整')
  } else {
    await api.scheduleCompensate(currentRow.value.id, timeForm.value)
    ElMessage.success('补课已安排')
  }
  timeVisible.value = false
  load(); loadPhaseCounts()
}

/* ===== 日历事件：颜色=状态，仅待上课可拖拽；点事件=调时 ===== */
const calOptions = {
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  initialView: 'timeGridWeek',
  locale: zhCnLocale,
  headerToolbar: { left: 'prev,next today', center: 'title', right: '' },
  allDaySlot: false,
  slotMinTime: '07:00:00', slotMaxTime: '22:00:00',
  selectable: true, editable: true, eventStartEditable: true,
  height: '100%',
  events: loadCalEvents,
  eventDrop: onEventDrop,
  eventClick: onEventClick,
  select: onSelect,
  eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
  slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false }
}

onMounted(async () => {
  load(); loadPhaseCounts()
  teachers.value = (await api.teacherList()) || []
  clazzes.value = (await api.clazzList()) || []
  students.value = (await api.studentList()) || []
  classrooms.value = (await api.classroomList()) || []
  const tree = (await api.orgTree()) || []
  const orgs = []
  const walk = (nodes, p) => nodes.forEach((n) => {
    orgs.push({ id: n.id, name: p ? p + '/' + n.name : n.name })
    walk(n.children || [], p || n.name)
  })
  walk(tree, '')
  orgList.value = orgs
})
</script>

<style scoped>
/* 阶段子TAB徽标位置 */
.phase-tabs :deep(.el-tabs__item) { height: 40px; }
.phase-badge { margin-left: 4px; }
/* 日历文字与全站字号统一 */
.cal-panel :deep(.fc) { font-size: 12.5px; }
.cal-panel :deep(.fc .fc-toolbar-title) { font-size: 16px; color: #142a44; }
.cal-panel :deep(.fc .fc-button) { background: #0094FF; border-color: #0094FF; text-transform: capitalize; }
.cal-panel :deep(.fc .fc-button:hover) { background: #33aaff; }
.cal-panel :deep(.fc .fc-button-active),
.cal-panel :deep(.fc .fc-button:disabled) {
  background: linear-gradient(135deg, #0094FF, #7B5BE6) !important;
  border-color: transparent;
}
.cal-panel :deep(.fc .fc-event) { border-radius: 5px; border: none; cursor: pointer; }
.cal-panel :deep(.fc .fc-daygrid-day-number),
.cal-panel :deep(.fc .fc-col-header-cell-cushion) { color: #475467; }
.cal-panel :deep(.fc .fc-day-today) { background: rgba(0, 148, 255, 0.06) !important; }
</style>
