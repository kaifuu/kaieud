<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">排课管理</span>
      <div class="actions">
        <!-- 教室筛选：选中后日/周/月视图只显示该教室课次，空白时段=该教室空闲（单间教室排班表） -->
        <el-select v-show="isCalView" v-model="roomFilter" clearable filterable
                   placeholder="全部教室" style="width: 150px"
                   title="按教室筛选日历：选教室后空白时段即空闲">
          <el-option v-for="r in classrooms" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
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

    <!-- ========== 课次清单抽屉：日/周视图折叠汇总块、月视图「+N 节」、双击日期 三处共用；
         操作与列表TAB一致（调时/取消/到课/教师请假/学生请假/旷课/补课/删除） ========== -->
    <el-drawer v-model="slotVisible" :title="slotTitle" size="520px">
      <div v-for="r in slotRows" :key="r.id" class="panel slot-item">
        <div class="slot-head">
          <el-tag effect="dark" size="small" type="info">{{ fmtHM(r.startTime) }}~{{ fmtHM(r.endTime) }}</el-tag>
          <el-tag size="small" :type="r.clazzId ? 'primary' : 'warning'" effect="plain">
            {{ r.clazzId ? '班课' : '一对一' }}</el-tag>
          <span class="slot-name">{{ r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId) }}</span>
          <el-tag size="small" :type="SCH_STATUS[r.status]?.type || 'info'" effect="light">
            {{ SCH_STATUS[r.status]?.label || '-' }}</el-tag>
        </div>
        <div class="slot-meta">
          {{ teacherName(r.teacherId) }}<template v-if="r.classroom"> · 📍{{ r.classroom }}</template>
        </div>
        <div class="slot-acts">
          <!-- 未开始：调时/取消（与列表TAB口径一致） -->
          <template v-if="r.status === 0 && !isDoneRow(r)">
            <el-button v-if="canAdjustRow(r)" link type="primary" size="small" @click="slotAction(r, openAdjust)">调时</el-button>
            <el-button link type="danger" size="small" @click="slotAction(r, doCancel)">取消</el-button>
          </template>
          <!-- 已下课未登记：到课/请假/旷课 -->
          <template v-if="r.status === 0 && isDoneRow(r)">
            <el-button link type="success" size="small" @click="slotAction(r, openAttend)">到课</el-button>
            <el-button link type="warning" size="small" @click="slotAction(r, doLeave, 3)">教师请假</el-button>
            <el-button link type="warning" size="small" @click="slotAction(r, doLeave, 4)">学生请假</el-button>
            <el-button link type="danger" size="small" @click="slotAction(r, doAbsent)">旷课</el-button>
          </template>
          <el-button v-if="r.status === 2" link type="warning" size="small" @click="slotAction(r, openCompensate)">补课</el-button>
          <el-button v-if="r.status === 0 || r.status === 2" link type="danger" size="small" @click="slotDelete(r)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!slotRows.length" description="无课次" :image-size="60" />
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
        <el-button type="primary" @click="doSearch">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="schedule" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.startTime" label="上课时间" width="290" sortable="custom">
          <template #default="{ row }">
            <template v-if="row.phase === 'tobe'">{{ row.timeText || '待安排' }}<span class="tobe-hint">（待排）</span></template>
            <template v-else>{{ row.startTime }} ~ {{ row.endTime }}</template>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.teacherId" label="教师" width="100">
          <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.clazzId" label="班级/学生" min-width="140">
          <template #default="{ row }">
            {{ row.clazzId ? clazzName(row.clazzId) : studentName(row.studentId) + '(一对一)' }}
            <el-tag v-if="row.phase === 'tobe'" size="small" type="danger" effect="plain" class="remain-tag">
              剩{{ row.remainingHours }}课时未排
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.classroom" prop="classroom" label="教室" width="95" />
        <el-table-column v-if="cols.status" label="状态" width="135" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.phase === 'done'" size="small" :type="outcomeMeta[row.status]?.type || 'info'" effect="plain">
              {{ outcomeMeta[row.status]?.label || '未登记' }}
            </el-tag>
            <el-tag v-else size="small" :type="phaseMeta[row.phase]?.type || 'info'" effect="plain">
              {{ phaseMeta[row.phase]?.label || '-' }}
            </el-tag>
            <el-tag v-if="row.compensation === 1" size="small" type="info" effect="plain">补</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <!-- 未开始/进行中：调时、取消 -->
            <template v-if="row.id && row.status === 0 && row.phase !== 'done'">
              <el-button link type="primary" @click="openAdjust(row)">调时</el-button>
              <el-button link type="danger" @click="doCancel(row)">取消</el-button>
            </template>
            <!-- 已下课未登记：到课 / 教师请假 / 学生请假 / 旷课 -->
            <template v-if="row.id && row.status === 0 && row.phase === 'done'">
              <el-button link type="success" @click="openAttend(row)">到课</el-button>
              <el-button link type="warning" @click="doLeave(row, 3)">教师请假</el-button>
              <el-button link type="warning" @click="doLeave(row, 4)">学生请假</el-button>
              <el-button link type="danger" @click="doAbsent(row)">旷课</el-button>
            </template>
            <el-button v-if="row.id && row.status === 2" link type="warning" @click="openCompensate(row)">补课</el-button>
            <el-button v-if="row.phase === 'tobe'" link type="primary" @click="goSchedule(row)">去排课</el-button>
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
        <el-select v-model="filters.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="onHistoryFilter">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.orgId" placeholder="校区" clearable filterable style="width: 140px" @change="onHistoryFilter">
          <el-option v-for="o in orgList" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-select v-model="historyOpType" placeholder="操作类型" clearable filterable style="width: 130px" @change="onHistoryFilter">
          <el-option v-for="t in HISTORY_OP_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="historyStatus" placeholder="记录状态" clearable filterable style="width: 140px" @change="onHistoryFilter">
          <el-option v-for="s in HISTORY_STATUSES" :key="s" :label="s" :value="s" />
        </el-select>
        <el-date-picker v-model="historyRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" @change="onHistoryFilter" />
        <el-button @click="onHistoryFilter">刷新</el-button>
        <div style="flex: 1"></div>
        <el-button @click="doExportHistory">导出历史</el-button>
      </div>
      <el-table :data="historyRows" v-loading="historyLoading" stripe size="small" style="flex: 1; overflow-y: auto" @sort-change="onHistorySort">
        <el-table-column type="index" label="序号" width="60" :index="historySeq" />
        <el-table-column label="操作类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="opTypeTag(row.opType)" effect="light">{{ row.opType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="教师" width="90" />
        <el-table-column prop="courseName" label="课程" min-width="150" />
        <el-table-column prop="time" label="上课时间" width="170" sortable="custom">
          <template #default="{ row }">{{ row.time || '-' }}</template>
        </el-table-column>
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
          <template #default="{ row }">{{ row.recordStatus || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90">
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column prop="opTime" label="操作时间" width="170" sortable="custom" />
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="historyPager.total"
                       v-model:current-page="historyPager.page" v-model:page-size="historyPager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="loadHistory" @size-change="onHistorySizeChange" />
      </div>
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
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          :disabled-date="disablePastDate" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          :disabled-date="disablePastDate" />
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
          <el-date-picker v-model="timeForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          :disabled-date="disablePastDate" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="timeForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          :disabled-date="disablePastDate" />
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="timeForm.classroomId" filterable placeholder="选择教室（可选）" style="width: 100%">
            <el-option v-for="r in classrooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="timeMode === 'adjust'" label="调时原因"><el-input v-model="timeForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTime">确定</el-button>
      </template>
    </el-dialog>

    <!-- 到课登记：扣课节数=排课时长÷每节时长折算，扣减少于应扣须填说明 -->
    <el-dialog v-model="attendVisible" title="到课登记" width="460px">
      <el-alert type="warning" :closable="false" show-icon
                title="确认为到课并扣减课时，请选择本次扣课节数"
                :description="`本课次时长 ${attendForm.minutes ?? '-'} 分钟，课程每节 ${attendForm.durationMinutes ?? '-'} 分钟，最多可扣 ${attendForm.maxHours ?? '-'} 节。扣课将同步记入课时管理，并累计到学员合同已消课时。`" />
      <el-form label-width="90px" style="margin-top: 16px">
        <el-form-item label="扣课节数">
          <el-radio-group v-model="attendForm.hours">
            <el-radio-button v-for="n in attendForm.maxHours" :key="n" :value="n">{{ n }} 节</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="attendForm.hours < attendForm.maxHours" label="扣课说明" required>
          <el-input v-model="attendForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="本次扣课少于本课次应扣节数，请说明原因（如：提前下课 / 部分内容未上完等）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="attendVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAttend">确认扣课</el-button>
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

/* 阶段标签（后端按系统时间推导：tobe待排课/today待上课/ongoing上课中/done已下课） */
const phaseMeta = {
  tobe: { label: '待排课', type: 'info' },
  today: { label: '待上课', type: 'warning' },
  ongoing: { label: '上课中', type: 'primary' },
  done: { label: '已下课', type: 'success' },
  canceled: { label: '已取消', type: 'danger' }
}
/* 已下课登记结果：0未登记 1已到课 3教师请假 4学生请假 5旷课待审批 6旷课已免扣 7旷课已扣课 */
const outcomeMeta = {
  0: { label: '未登记', type: 'info' },
  1: { label: '已到课·已扣课', type: 'success' },
  3: { label: '教师请假', type: 'warning' },
  4: { label: '学生请假', type: 'warning' },
  5: { label: '旷课·待审批', type: 'danger' },
  6: { label: '旷课·已免扣', type: 'success' },
  7: { label: '旷课·已扣课', type: 'danger' }
}

/** 待排课任务 → 跳到周视图日历排课，并带入教师筛选 */
function goSchedule(row) {
  if (row.teacherId) filters.teacherId = row.teacherId
  viewTab.value = 'week'
  ElMessage.info('已在周视图中，点击/拖选时间段即可排课')
}

async function loadPhaseCounts() {
  try {
    const d = (await api.schedulePhaseCounts({
      teacherId: filters.teacherId, clazzId: filters.clazzId, orgId: filters.orgId,
      begin: dateRange.value?.[0], end: dateRange.value?.[1]
    })) || {}
    Object.assign(counts, d)
  } catch (e) { /* ignore */ }
}
watch([phase, filters.teacherId, filters.clazzId, filters.orgId], () => loadPhaseCounts())

/** 查询：列表与阶段徽标一起刷新（口径一致） */
function doSearch() { search(); loadPhaseCounts() }

/* ===== 排课历史 TAB（分页 + 排序） ===== */
const historyRows = ref([])
const historyLoading = ref(false)
const historyPager = reactive({ page: 1, size: 10, total: 0 })
const historySort = reactive({ sortBy: '', direction: '' })
const historySeq = (i) => (historyPager.page - 1) * historyPager.size + i + 1
/** 操作类型筛选：排课日志（排课/调课/取消/删除/补课/请假/旷课）+ 消课台账（消课/撤销消课） */
const historyOpType = ref('')
const historyStatus = ref('')
const HISTORY_OP_TYPES = ['排课', '调课', '取消排课', '删除排课', '补课', '教师请假', '学生请假', '旷课登记', '消课', '撤销消课']
/** 记录状态筛选：课次当前状态 + 已删除 + 消课审核状态 */
const HISTORY_STATUSES = ['待上课', '已到课', '已取消', '教师请假', '学生请假',
  '旷课·待审批', '旷课·已免扣', '旷课·已扣课', '已删除', '已审核', '待审核', '已驳回']

async function loadHistory() {
  historyLoading.value = true
  try {
    const d = (await api.scheduleHistory({
      page: historyPager.page, size: historyPager.size,
      sortBy: historySort.sortBy || undefined,
      direction: historySort.sortBy ? historySort.direction : undefined,
      teacherId: filters.teacherId, orgId: filters.orgId,
      opType: historyOpType.value || undefined,
      recordStatus: historyStatus.value || undefined,
      begin: historyRange.value?.[0], end: historyRange.value?.[1]
    })) || {}
    historyRows.value = d.records || []
    historyPager.total = d.total || 0
  } finally { historyLoading.value = false }
}
/** 筛选变化：回到第一页再查 */
function onHistoryFilter() { historyPager.page = 1; loadHistory() }
/** 表头排序（服务端：opTime 操作时间 / time 上课时间） */
function onHistorySort({ prop, order }) {
  historySort.sortBy = order ? prop : ''
  historySort.direction = order === 'ascending' ? 'asc' : 'desc'
  historyPager.page = 1
  loadHistory()
}
function onHistorySizeChange() { historyPager.page = 1; loadHistory() }
watch(historyRange, onHistoryFilter)
function opTypeTag(t) {
  return { '排课': 'primary', '调课': 'warning', '取消排课': 'danger', '删除排课': 'info', '补课': 'info',
           '教师请假': 'warning', '学生请假': 'warning', '旷课登记': 'danger',
           '消课': 'success', '撤销消课': 'info' }[t] || 'info'
}
function doExportHistory() {
  exportXlsx('排课历史', [
    { label: '操作类型', prop: 'opType' }, { label: '教师', prop: 'teacherName' },
    { label: '课程', prop: 'courseName' }, { label: '上课时间', prop: 'time' },
    { label: '学员', prop: 'studentName' },
    { label: '课时', prop: 'hours' }, { label: '教室', prop: 'classroom' },
    { label: '记录状态', prop: 'recordStatus' }, { label: '操作人', prop: 'operator' },
    { label: '操作时间', prop: 'opTime' }
  ], historyRows.value)
}

/* ===== 日历视图（日/周/月共用一个 FullCalendar） ===== */
const calRef = ref()
const rawSchedules = ref([])
/** 教室筛选后的当前展示集（月视图「+N 节」/双击日期的清单口径与日历一致） */
const shownSchedules = ref([])
/** 教室筛选：选中后日历只显示该教室课次（历史课次仅存教室名，按 ID 或名称任一命中） */
const roomFilter = ref('')
watch(roomFilter, () => calRef.value?.getApi?.()?.refetchEvents?.())
const CAL_COLORS = { 0: '#155EEF', 1: '#12b76a', 2: '#c0c4cc', 3: '#f79009', 4: '#fdb022', 5: '#f04438', 6: '#66c555', 7: '#d92d20' }
/* 课次状态（清单抽屉标签；0待上课 1已到课 2已取消 3/4请假 5旷课待审批 6旷课免扣 7旷课已扣） */
const SCH_STATUS = {
  0: { label: '待上课', type: 'primary' }, 1: { label: '已到课', type: 'success' },
  2: { label: '已取消', type: 'info' }, 3: { label: '教师请假', type: 'warning' },
  4: { label: '学生请假', type: 'warning' }, 5: { label: '旷课·待审批', type: 'danger' },
  6: { label: '旷课·已免扣', type: 'success' }, 7: { label: '旷课·已扣课', type: 'danger' }
}

/** 单条课次 → 日历事件（两行式色块：行1 名称，行2 📍教室 · 教师） */
function toCalEvent(r) {
  return {
    id: String(r.id),
    title: (r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId)) + ' · ' + teacherName(r.teacherId)
           + (r.classroom ? ' @' + r.classroom : ''),
    start: r.startTime, end: r.endTime,
    backgroundColor: CAL_COLORS[r.status] || '#155EEF',
    borderColor: 'transparent', editable: r.status === 0,
    extendedProps: {
      status: r.status,
      name: r.clazzId ? clazzName(r.clazzId) : studentName(r.studentId),
      meta: [r.classroom ? '📍' + r.classroom : '', teacherName(r.teacherId)].filter(Boolean).join(' · ')
    }
  }
}

/**
 * 时段折叠：同一天内时间上连通重叠（含部分重叠的阶梯链）的课次为一组，
 * 组内数量超过分道上限（周视图列窄3路/日视图单列宽5路）则折叠为汇总块，
 * 避免 10+ 教室并排把每道挤成不可读窄条。
 */
function groupOverlapChains(records, limit) {
  const byDay = new Map()
  for (const r of records) {
    const d = String(r.startTime || '').slice(0, 10)
    if (!byDay.has(d)) byDay.set(d, [])
    byDay.get(d).push(r)
  }
  const groups = []
  for (const arr of byDay.values()) {
    arr.sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)))
    let chain = [], chainEnd = ''
    const flush = () => {
      if (chain.length) groups.push({ chain, folded: chain.length > limit })
      chain = []; chainEnd = ''
    }
    for (const r of arr) {
      // 与当前链无时间交集（开始晚于链最晚结束）则成组结算
      if (chain.length && String(r.startTime) >= chainEnd) flush()
      chain.push(r)
      if (String(r.endTime || '') > chainEnd) chainEnd = String(r.endTime || '')
    }
    flush()
  }
  return groups
}

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
    const room = classrooms.value.find((x) => x.id === roomFilter.value)
    const shown = room ? records.filter((r) => r.classroomId === room.id || r.classroom === room.name) : records
    shownSchedules.value = shown
    // 折叠阈值：日视图单列约1400px可容5道；周视图每列约200px容3道；月视图由 dayMaxEvents 处理
    // （本版 FullCalendar 的 fetchInfo 不带 view，从日历 API 取当前视图，viewTab 兜底）
    const viewType = calRef.value?.getApi?.()?.view?.type
      || ({ day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' })[viewTab.value]
    const laneLimit = viewType === 'timeGridDay' ? 5
                    : viewType === 'timeGridWeek' ? 3 : 0
    const groups = laneLimit ? groupOverlapChains(shown, laneLimit)
                             : shown.map((r) => ({ chain: [r], folded: false }))
    const evts = []
    for (const g of groups) {
      if (!g.folded) { for (const r of g.chain) evts.push(toCalEvent(r)); continue }
      // 折叠汇总块：深灰蓝中性色区分于状态色，不可拖拽，点击开课次清单抽屉
      const chain = g.chain
      const maxEnd = chain.reduce((m, r) => (String(r.endTime || '') > m ? String(r.endTime || '') : m), '')
      const rooms = [...new Set(chain.map((r) => r.classroom).filter(Boolean))]
      const roomsText = !rooms.length ? '多间教室'
        : rooms.length > 3 ? rooms.slice(0, 3).join('、') + ` 等${rooms.length}间` : rooms.join('、')
      evts.push({
        id: 'b' + evts.length,
        title: `${chain.length} 节课`,
        start: chain[0].startTime, end: maxEnd,
        backgroundColor: '#475469', borderColor: 'transparent', editable: false,
        extendedProps: {
          status: null, // 不挂删除按钮、不进调时
          bundle: chain,
          range: [chain[0].startTime, maxEnd],
          name: `${chain.length} 节课`,
          meta: roomsText
        }
      })
    }
    successCallback(evts)
    console.log('[caldebug] vt=%s limit=%s rec=%s folded=%s singles=%s', viewType, laneLimit, records.length,
      evts.filter((e) => e.extendedProps.bundle).length, evts.filter((e) => !e.extendedProps.bundle).length)
  } catch (e) { console.error('日历课次加载失败:', e); failureCallback(e) }
}

/** 色块两行式内容：行1 时间+班级/学生（时间弱化），行2 📍教室 · 教师；窄块省略号截断不换行 */
function renderEventContent(arg) {
  const p = arg.event.extendedProps
  const box = document.createElement('div')
  box.className = 'ev2'
  const line1 = document.createElement('div')
  line1.className = 'ev2-n'
  const t = document.createElement('span')
  t.className = 'ev2-t'
  t.textContent = (arg.timeText || '') + ' '
  line1.appendChild(t)
  line1.appendChild(document.createTextNode(p.name || arg.event.title))
  const line2 = document.createElement('div')
  line2.className = 'ev2-m'
  line2.textContent = p.meta || ''
  box.appendChild(line1); box.appendChild(line2)
  return { domNodes: [box] }
}

/** 日历 ISO 串（带时区偏移，如 2026-10-29T10:00:00+08:00）→ 后端要求的 yyyy-MM-dd HH:mm:ss */
function fmtLocal(dt) {
  if (!dt) return dt
  const d = new Date(dt)
  if (isNaN(d.getTime())) return dt
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/* ===== 排课时间校验 ===== */
/** 日期面板禁选今天之前的日期（今天内晚于当前的时刻仍可选） */
function disablePastDate(d) {
  const t0 = new Date(); t0.setHours(0, 0, 0, 0)
  return d.getTime() < t0.getTime()
}
/** 开始时间必须晚于当前系统时间；返回错误文案，空串=通过 */
function checkStartFuture(startTime) {
  if (!startTime) return '请选择上课时间'
  if (new Date(startTime.replace(' ', 'T')).getTime() <= Date.now())
    return '开始时间必须晚于当前系统时间，不能选择已过去的时间'
  return ''
}
/** 开始时间距当前不足3小时即开课：弹提示确认（确定=true，取消=false） */
async function confirmUrgentStart(startTime) {
  const ms = new Date(startTime.replace(' ', 'T')).getTime() - Date.now()
  if (ms > 0 && ms < 3 * 60 * 60 * 1000) {
    try {
      await ElMessageBox.confirm(
        '所选开始时间距当前不足 3 小时即开课，请确认教师与学员已知晓。是否继续？',
        '开课时间临近提醒', { type: 'warning', confirmButtonText: '继续排课', cancelButtonText: '返回修改' })
    } catch { return false }
  }
  return true
}

async function onEventDrop(info) {
  const start = fmtLocal(info.event.startStr)
  if (checkStartFuture(start)) { ElMessage.warning(checkStartFuture(start)); return info.revert() }
  if (!(await confirmUrgentStart(start))) return info.revert()
  api.scheduleAdjust(Number(info.event.id), {
    startTime: start, endTime: fmtLocal(info.event.endStr), reason: '日历拖拽调整'
  }).then(() => { ElMessage.success('上课时间已调整'); refreshAll() })
    .catch(() => info.revert())
}

/** 排课数据变更后统一刷新：列表 + 阶段徽标 + 日历课次块（否则新排的课不显示色块） */
function refreshAll() {
  load(); loadPhaseCounts()
  calRef.value?.getApi?.()?.refetchEvents?.()
}

function onEventClick(info) {
  const p = info.event.extendedProps
  // 折叠汇总块：打开该时段的课次清单抽屉
  if (p.bundle) return openSlotDrawer(p.bundle, `${fmtHM(p.range[0])}~${fmtHM(p.range[1])} 时段课次（${p.bundle.length} 节）`)
  const raw = rawSchedules.value.find((r) => String(r.id) === info.event.id)
  if (!raw) return
  // 已开始的课次：时间已过，不弹调时窗，仅提示（与日历灰色已过区域一致）
  if (new Date(String(raw.startTime).replace(' ', 'T')).getTime() <= Date.now())
    return ElMessage.warning('该课次时间已过，不能调整上课时间')
  openAdjust(raw)
}

/* ===== 课次清单抽屉：汇总块 / 月视图「+N 节」 / 双击日期 共用 ===== */
const slotVisible = ref(false)
const slotTitle = ref('')
const slotRows = ref([])
const fmtHM = (s) => String(s || '').slice(11, 16)
const toTs = (s) => new Date(String(s || '').replace(' ', 'T')).getTime()
/** 已下课（结束时间已过）：清单里走 到课/请假/旷课 登记分支 */
const isDoneRow = (r) => toTs(r.endTime) <= Date.now()
/** 未开始（开始时间在未来）：可调时 */
const canAdjustRow = (r) => toTs(r.startTime) > Date.now()

function openSlotDrawer(rows, title) {
  slotRows.value = [...rows].sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)))
  slotTitle.value = title
  slotVisible.value = true
}

/** 清单内操作：先关抽屉再执行（弹窗/对话框不与抽屉叠层） */
function slotAction(r, fn, ...args) {
  slotVisible.value = false
  fn(r, ...args)
}

/** 清单内删除：成功后就地移除该行（不关抽屉，便于连续处理） */
async function slotDelete(r) {
  const ok = await doDeleteSchedule(r.id)
  if (ok) slotRows.value = slotRows.value.filter((x) => x.id !== r.id)
}

/** 当日课次清单（月视图「+N 节」与双击日期入口），口径与日历当前展示一致 */
function openDayList(dateStr) {
  const rows = shownSchedules.value.filter((r) => String(r.startTime).slice(0, 10) === dateStr)
  openSlotDrawer(rows, `${dateStr} 当日课次（${rows.length} 节）`)
}

/** 月视图「+N 节」：改为打开当日清单抽屉，不用 FullCalendar 自带小弹层 */
function onMoreLinkClick(arg) {
  const d = arg.date
  if (d) {
    const p2 = (n) => String(n).padStart(2, '0')
    openDayList(`${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`)
  }
  return 'cancel'
}

/** 双击日期（月视图日期格）/ 双击时间列（日/周视图）：当日课次清单 */
function onCalDblClick(e) {
  const date = e.target.closest('[data-date]')?.getAttribute('data-date')
  if (date) openDayList(date)
}

function onSelect(info) {
  const start = fmtLocal(info.startStr)
  // 框选已过去的时间段：不弹新增排课，仅提示
  if (new Date(start.replace(' ', 'T')).getTime() <= Date.now()) {
    calRef.value?.getApi?.()?.unselect()
    return ElMessage.warning('所选时间段已过，不能在过去的时间排课')
  }
  openCreate()
  form.value.startTime = start
  form.value.endTime = fmtLocal(info.endStr)
}

/* ===== 已过时段标识：过去整天由样式 .fc-day-past 置灰，今天 6:00~当前时间加遮罩 ===== */
function onDatesSet() { nextTick(() => setTimeout(shadePastArea, 50)) }
/** 日/周视图：给今天已过时段（时间轴 6:00~当前时间，按比例）加淡灰遮罩 */
function shadePastArea() {
  const root = document.querySelector('.cal-panel')
  if (!root) return
  root.querySelectorAll('.fc-timegrid-past-overlay').forEach((n) => n.remove())
  const todayCol = root.querySelector('.fc-timegrid-col.fc-day-today')
  const frame = todayCol?.querySelector('.fc-timegrid-col-frame') || todayCol
  if (!frame) return
  const now = new Date()
  const axisStart = new Date(now); axisStart.setHours(6, 0, 0, 0)
  const axisEnd = new Date(now); axisEnd.setHours(23, 0, 0, 0)
  if (now <= axisStart) return
  const pct = Math.min(1, (now - axisStart) / (axisEnd - axisStart))
  const ov = document.createElement('div')
  ov.className = 'fc-timegrid-past-overlay'
  ov.style.height = Math.round(frame.getBoundingClientRect().height * pct) + 'px'
  frame.appendChild(ov)
}
window.addEventListener('resize', shadePastArea)

/* ===== 视图切换：日历 resize / 列表与历史加载 ===== */
watch(viewTab, async (v) => {
  await nextTick()
  if (v === 'day' || v === 'week' || v === 'month') {
    const api = calRef.value?.getApi?.()
    if (api) {
      const viewMap = { day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' }
      api.changeView(viewMap[v])
      setTimeout(() => { if (typeof api.resize === 'function') api.resize(); shadePastArea() }, 50)
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
    { label: '状态', prop: 'phase', fmt: (v) => phaseMeta[v]?.label || v },
    { label: '剩余未排课时', prop: 'remainingHours' }
  ], rows.value)
}

watch(dateRange, doSearch)

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
  const err = checkStartFuture(f.startTime)
  if (err) return ElMessage.warning(err)
  if (!(await confirmUrgentStart(f.startTime))) return
  saving.value = true
  try {
    f.classroom = classrooms.value.find((x) => x.id === Number(f.classroomId))?.name || f.classroom
    await api.scheduleCreate(f)
    ElMessage.success('排课成功')
    createVisible.value = false
    refreshAll()
  } finally { saving.value = false }
}

/* ===== 已下课登记：到课/教师请假/学生请假/旷课 ===== */
const attendVisible = ref(false)
const attendForm = ref({ id: null, hours: 1, maxHours: 1, minutes: 0, durationMinutes: 60, remark: '' })
async function openAttend(row) {
  attendForm.value = { id: row.id, hours: 1, maxHours: 1, minutes: 0, durationMinutes: 60, remark: '' }
  attendVisible.value = true
  const info = await api.scheduleAttendInfo(row.id)
  const max = info?.maxHours || 1
  attendForm.value = { id: row.id, hours: max, maxHours: max,
    minutes: info?.minutes, durationMinutes: info?.durationMinutes, remark: '' }
}
async function saveAttend() {
  const f = attendForm.value
  if (f.hours < f.maxHours && !f.remark?.trim())
    return ElMessage.warning('本次扣课少于本课次应扣节数，请填写扣课说明')
  saving.value = true
  try {
    const n = await api.scheduleAttend(f.id, f.hours, f.remark?.trim() || undefined)
    ElMessage.success(`到课登记成功：已扣 ${f.hours} 节课（${n ?? 0} 份合同）`)
    attendVisible.value = false
    refreshAll()
  } finally { saving.value = false }
}
async function doLeave(row, type) {
  const label = type === 3 ? '教师请假' : '学生请假'
  await ElMessageBox.confirm(`确认将该课次登记为「${label}」？该状态不扣课时。`, `${label}登记`, { type: 'warning' })
  await api.scheduleLeave(row.id, type)
  ElMessage.success(`已登记为${label}（不扣课）`)
  refreshAll()
}
async function doAbsent(row) {
  const { value } = await ElMessageBox.prompt(
    '登记旷课后将生成旷课申请：审批通过不扣课；72小时内未批准系统将自动扣课（扣课记录同步至课时管理）。',
    '旷课登记', { inputPlaceholder: '旷课说明（选填）', type: 'warning' })
  await api.scheduleAbsent(row.id, value || '')
  ElMessage.success('已生成旷课申请，等待审批（72小时内未批准将自动扣课）')
  refreshAll()
}

/* ===== 取消/调时/补课 ===== */
async function doCancel(row) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消课次')
  await api.scheduleCancel(row.id, { reason: value || '' })
  ElMessage.success('已取消')
  refreshAll()
}

const timeVisible = ref(false)
const timeMode = ref('adjust')
const timeForm = ref({})
const currentRow = ref(null)
function openAdjust(row) {
  timeMode.value = 'adjust'; currentRow.value = row
  timeForm.value = { startTime: row.startTime, endTime: row.endTime, reason: '', classroomId: row.classroomId ?? undefined }
  timeVisible.value = true
}
function openCompensate(row) {
  timeMode.value = 'compensate'; currentRow.value = row
  timeForm.value = { startTime: '', endTime: '', classroomId: row.classroomId ?? undefined }
  timeVisible.value = true
}
async function saveTime() {
  const err = checkStartFuture(timeForm.value.startTime)
  if (err) return ElMessage.warning(err)
  if (!(await confirmUrgentStart(timeForm.value.startTime))) return
  if (timeMode.value === 'adjust') {
    await api.scheduleAdjust(currentRow.value.id, timeForm.value)
    ElMessage.success('已调整')
  } else {
    await api.scheduleCompensate(currentRow.value.id, timeForm.value)
    ElMessage.success('补课已安排')
  }
  timeVisible.value = false
  refreshAll()
}

/* ===== 日历事件：颜色=状态，仅待上课可拖拽；点事件=调时；悬停右上角=删除；
   同时段并发超阈值折叠为汇总块，点汇总块/「+N 节」/双击日期=课次清单抽屉 ===== */
const calOptions = {
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  initialView: 'timeGridWeek',
  locale: zhCnLocale,
  headerToolbar: { left: 'prev,next today', center: 'title', right: '' },
  allDaySlot: false,
  slotMinTime: '06:00:00', slotMaxTime: '23:00:00',
  // 月视图（dayGrid）定时事件默认渲染为圆点，改为色块与周视图一致
  eventDisplay: 'block',
  selectable: true, editable: true, eventStartEditable: true,
  height: '100%',
  events: loadCalEvents,
  // 月视图每天最多直显 3 条，其余收进「+N 节」，点击打开当日清单抽屉
  dayMaxEvents: 3,
  moreLinkText: (n) => `+${n} 节`,
  moreLinkClick: onMoreLinkClick,
  eventContent: renderEventContent,
  eventDidMount: onEventDidMount,
  eventDrop: onEventDrop,
  eventClick: onEventClick,
  select: onSelect,
  datesSet: onDatesSet,
  eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
  slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false }
}

/** 事件块右上角挂删除按钮（仅待上课/已取消可删），点击不触发调时弹窗 */
const TRASH_SVG = '<svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/></svg>'
function onEventDidMount(arg) {
  const st = arg.event.extendedProps.status
  if (st !== 0 && st !== 2) return
  const btn = document.createElement('span')
  btn.className = 'ev-del'
  btn.title = '删除课次'
  btn.innerHTML = TRASH_SVG
  btn.addEventListener('click', (ev) => {
    ev.stopPropagation()
    doDeleteSchedule(Number(arg.event.id))
  })
  arg.el.appendChild(btn)
}

/** 日历/清单删除课次：确认后软删除（排课历史留痕）；返回是否真正删除 */
async function doDeleteSchedule(id) {
  try {
    await ElMessageBox.confirm(
      '确认删除该课次？删除后将从排课中清除，并在排课历史中记录「删除排课」。',
      '删除课次', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  } catch { return false }
  await api.scheduleDelete(id)
  ElMessage.success('课次已删除')
  refreshAll()
  return true
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
.tobe-hint { color: #98a2b3; font-size: 12px; margin-left: 4px; }
.remain-tag { margin-left: 6px; }
/* 日历文字与全站字号统一 */
.cal-panel :deep(.fc) { font-size: 12.5px; }
.cal-panel :deep(.fc .fc-toolbar-title) { font-size: 16px; color: #142a44; }
.cal-panel :deep(.fc .fc-button) { background: #155EEF; border-color: #155EEF; text-transform: capitalize; }
.cal-panel :deep(.fc .fc-button:hover) { background: #4a7df4; }
.cal-panel :deep(.fc .fc-button-active),
.cal-panel :deep(.fc .fc-button:disabled) {
  background: linear-gradient(135deg, #155EEF, #5B8DEF) !important;
  border-color: transparent;
}
.cal-panel :deep(.fc .fc-event) { border-radius: 5px; border: none; cursor: pointer; }
/* 两行式色块内容（eventContent 动态创建，同 .ev-del 须经 :deep 挂靠）：
   行1 时间+班级/学生，行2 📍教室 · 教师；窄块单行省略号截断，不再挤压换行 */
.cal-panel :deep(.ev2) {
  display: flex; flex-direction: column; gap: 1px;
  padding: 1px 4px 2px; line-height: 1.25; overflow: hidden;
}
.cal-panel :deep(.ev2-n) {
  font-size: 11.5px; font-weight: 600; color: #fff;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  padding-right: 14px; /* 右上角悬停删除按钮让位，避免遮字 */
}
.cal-panel :deep(.ev2-t) { font-size: 10px; font-weight: 400; opacity: 0.85; }
.cal-panel :deep(.ev2-m) {
  font-size: 10.5px; color: rgba(255, 255, 255, 0.92);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
/* 事件块右上角删除按钮（垃圾桶图标）。按钮为 eventDidMount 动态创建（无 data-v），
   必须经 :deep(.cal-panel…) 挂靠，否则 scoped 规则匹配不上、图标不定位到右上角。
   悬停色块才显现：高并发窄道下常态不占空间、不遮挡文字 */
.cal-panel :deep(.fc .fc-event) { position: relative; }
.cal-panel :deep(.ev-del) {
  position: absolute; top: 1px; right: 2px; z-index: 2;
  width: 16px; height: 16px; display: flex; align-items: center; justify-content: center;
  color: rgba(255, 255, 255, 0.92); cursor: pointer;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.35));
  opacity: 0; transition: opacity 0.15s;
}
.cal-panel :deep(.fc-event:hover .ev-del),
.cal-panel :deep(.ev-del:focus-visible) { opacity: 1; }
.cal-panel :deep(.ev-del:hover) { color: #fff; background: rgba(0, 0, 0, 0.28); border-radius: 4px; }
.cal-panel :deep(.fc .fc-daygrid-day-number),
.cal-panel :deep(.fc .fc-col-header-cell-cushion) { color: #475467; }
/* 今天列底色（日/周/月视图的当日可用时段）：淡紫（渲染 ≈ #dac8fa），与白底/灰色对比明显 */
.cal-panel :deep(.fc .fc-day-today) { background: rgba(124, 58, 237, 0.28) !important; }
/* 系统当前时间之前的区域灰色（加深）：过去的整天（月视图日期格 + 日/周视图整天列 + 顶部星期头） */
.cal-panel :deep(.fc .fc-day-past) { background: #cbd5e1 !important; }
.cal-panel :deep(.fc .fc-day-past .fc-daygrid-day-number) { color: #8b95a7; }
/* 日/周视图：今天 6:00~当前时间 的已过时段遮罩（加深，不拦截点击） */
.cal-panel :deep(.fc-timegrid-past-overlay) {
  position: absolute; top: 0; left: 0; width: 100%;
  background: rgba(148, 163, 184, 0.45); pointer-events: none; z-index: 2;
}
/* ===== 课次清单抽屉条目 ===== */
.slot-item { padding: 10px 14px; margin-bottom: 10px; }
.slot-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.slot-name { font-weight: 600; font-size: 13.5px; color: #142a44; }
.slot-meta { font-size: 12.5px; color: #667085; margin: 4px 0 2px; }
.slot-acts { display: flex; flex-wrap: wrap; margin-top: 2px; }
.slot-acts .el-button + .el-button { margin-left: 8px; }
</style>
