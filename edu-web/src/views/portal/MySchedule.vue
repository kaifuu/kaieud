<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">我的课表</span>
      <span class="ro-tip">点击课次查看详情；调课、请假、扣课等请咨询授课老师或教务处理</span>
    </div>

    <!-- 一级视图TAB：日视图 / 周视图 / 月视图 / 我的记录（学生只读，不含登记操作） -->
    <el-tabs v-model="viewTab" class="view-tabs">
      <el-tab-pane label="日视图" name="day" />
      <el-tab-pane label="周视图" name="week" />
      <el-tab-pane label="月视图" name="month" />
      <el-tab-pane label="我的记录" name="record" />
    </el-tabs>

    <div v-show="viewTab !== 'record'" class="panel cal-panel tabs-fill" style="padding: 12px">
      <FullCalendar ref="calRef" :options="calOptions" />
    </div>

    <!-- 我的记录TAB：本人全部课次记录（含班课按班级归属），支持状态/时间筛选、排序、分页 -->
    <div v-show="viewTab === 'record'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-select v-model="recFilters.status" placeholder="全部状态" clearable style="width: 140px" @change="recSearch">
          <el-option v-for="(t, i) in STATUS_TEXT" :key="i" :label="t" :value="i" />
        </el-select>
        <el-date-picker v-model="recDateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" @change="recSearch" />
        <el-button type="primary" @click="recSearch">查询</el-button>
        <div style="flex: 1"></div>
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="recRows" v-loading="recLoading" stripe height="100%" @sort-change="onRecSort"
                @row-click="onRecRowClick">
        <el-table-column type="index" label="序号" width="60" :index="recSeq" />
        <el-table-column prop="startTime" label="上课时间" width="290" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.start }} ~ {{ (row.end || '').slice(11) }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="140"  show-overflow-tooltip/>
        <el-table-column label="形式" width="85" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.classType === '班课' ? 'primary' : 'warning'">{{ row.classType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column prop="teacherName" label="授课老师" width="100"  show-overflow-tooltip/>
        <el-table-column prop="classroom" label="教室" width="95"  show-overflow-tooltip/>
        <el-table-column prop="status" label="状态" width="120" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="recPager.total"
                       v-model:current-page="recPager.page" v-model:page-size="recPager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="recLoad" @size-change="onRecSizeChange" />
      </div>
    </div>

    <!-- 课次详情：点击色块/记录行查看（学生只读，无登记操作） -->
    <el-dialog v-model="detailVisible" title="课次详情" width="420px">
      <el-descriptions :column="1" border size="small" v-if="detail">
        <el-descriptions-item label="课程">{{ detail.courseName }}</el-descriptions-item>
        <el-descriptions-item label="形式">
          <el-tag size="small" :type="detail.classType === '班课' ? 'primary' : 'warning'">{{ detail.classType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="班级">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="授课老师">{{ detail.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.start }} ~ {{ (detail.end || '').slice(11) }}</el-descriptions-item>
        <el-descriptions-item label="教室">{{ detail.classroom || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="statusTagType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'

/* ===== 学生课表：与教师端/排课管理同款日历视觉，完全只读 ===== */
const calRef = ref()
const viewTab = ref('week')
const detailVisible = ref(false)
const detail = ref(null)
const rawRows = ref([])
// 0待上课 1已到课 2已取消 3教师请假 4学生请假 5旷课待审批 6旷课免扣 7旷课已扣（与排课管理同色板）
const CAL_COLORS = { 0: '#155EEF', 1: '#12b76a', 2: '#c0c4cc', 3: '#f79009', 4: '#fdb022', 5: '#f04438', 6: '#66c555', 7: '#d92d20' }
const STATUS_TEXT = ['待上课', '已到课', '已取消', '教师请假', '学生请假', '旷课·待审批', '旷课·已免扣', '旷课·已扣课']
/* 色块状态标记：仅已归档课次加标记，待上课保持干净（口径与教师端一致） */
const STATUS_MARK = { 1: '到课', 2: '已取消', 3: '教师请假', 4: '学生请假', 5: '旷课', 6: '旷课', 7: '旷课' }
const statusText = (s) => STATUS_TEXT[s] || '-'
const statusTagType = (s) => ({ 0: 'primary', 1: 'success', 2: 'info', 3: 'warning', 4: 'warning', 5: 'danger', 6: 'success', 7: 'danger' }[s] || 'info')

async function loadEvents(info, successCallback, failureCallback) {
  try {
    const rows = (await api.portalMySchedule({
      begin: (info.startStr || '').slice(0, 10),
      end: (info.endStr || '').slice(0, 10)
    })) || []
    rawRows.value = rows
    successCallback(rows.map((r) => ({
      id: String(r.id),
      title: r.name + (r.classroom ? ' @' + r.classroom : ''),
      start: r.start, end: r.end,
      backgroundColor: CAL_COLORS[r.status] || '#155EEF',
      borderColor: 'transparent',
      extendedProps: {
        status: r.status,
        // 两行式色块：行1 班级，行2 📍教室 · 课程 · 老师
        name: r.name,
        meta: [r.classroom ? '📍' + r.classroom : '', r.courseName, r.teacherName].filter(Boolean).join(' · ')
      }
    })))
  } catch (e) { failureCallback(e) }
}

/** 徽章配色按色块底色亮度自适应：深底白字（透明白底），浅底深字（实白底）——保证醒目对比 */
function badgeThemeOn(bg) {
  const m = String(bg || '').replace('#', '')
  if (m.length < 6) return { color: '#fff', background: 'rgba(255,255,255,0.24)' }
  const r = parseInt(m.slice(0, 2), 16), g = parseInt(m.slice(2, 4), 16), b = parseInt(m.slice(4, 6), 16)
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return lum >= 0.5
    ? { color: '#142a44', background: 'rgba(255, 255, 255, 0.8)' }
    : { color: '#fff', background: 'rgba(255, 255, 255, 0.24)' }
}

/** 色块两行式内容（与教师端一致；DOM 为 FullCalendar 动态创建，CSS 须经 :deep 挂靠） */
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
  // 已归档课次在第二行行首加状态标记（到课/教师请假/学生请假/旷课）
  const mark = STATUS_MARK[p.status]
  if (mark) {
    const tag = document.createElement('span')
    tag.className = 'ev2-st'
    tag.textContent = mark
    const theme = badgeThemeOn(arg.event.backgroundColor)
    tag.style.color = theme.color
    tag.style.background = theme.background
    line2.appendChild(tag)
    line2.appendChild(document.createTextNode(' '))
  }
  line2.appendChild(document.createTextNode(p.meta || ''))
  box.appendChild(line1); box.appendChild(line2)
  return { domNodes: [box] }
}

function onEventClick(info) {
  const raw = rawRows.value.find((r) => String(r.id) === info.event.id)
  if (raw) { detail.value = raw; detailVisible.value = true }
}

const calOptions = {
  plugins: [dayGridPlugin, timeGridPlugin],
  initialView: 'timeGridWeek',
  locale: zhCnLocale,
  headerToolbar: { left: 'prev,next today', center: 'title', right: '' },
  allDaySlot: false,
  slotMinTime: '06:00:00', slotMaxTime: '23:00:00',
  eventDisplay: 'block',
  height: '100%',
  // 学生只读：不可拖拽调时、不可框选新增
  editable: false, selectable: false,
  events: loadEvents,
  eventContent: renderEventContent,
  eventClick: onEventClick,
  datesSet: () => nextTick(() => setTimeout(shadePastArea, 50)),
  dayMaxEvents: 3,
  moreLinkText: (n) => `+${n} 节`,
  eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
  slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false }
}

/* ===== 视图切换：日/周/月切换日历视图，我的记录TAB加载列表 ===== */
watch(viewTab, async (v) => {
  if (v === 'record') { recLoad(); return }
  await nextTick()
  const fcApi = calRef.value?.getApi?.()
  if (!fcApi) return
  fcApi.changeView({ day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' }[v])
  setTimeout(() => { if (typeof fcApi.resize === 'function') fcApi.resize(); shadePastArea() }, 50)
})

/* ===== 我的记录TAB：本人全部课次记录（分页 + 状态/时间筛选 + 服务端排序） ===== */
const recQuery = reactive({ page: 1 })
const recFilters = reactive({ status: null })
const recDateRange = ref(null)
const { loading: recLoading, rows: recRows, pager: recPager, seq: recSeq,
        onSort: onRecSort, onSizeChange: onRecSizeChange, search: recSearch, load: recLoad } =
  usePage((q) => api.portalScheduleRecords({
    ...q, status: recFilters.status, begin: recDateRange.value?.[0], end: recDateRange.value?.[1]
  }), recQuery)

/** 行点击：复用课次详情弹窗（字段口径与日历行一致） */
function onRecRowClick(row) {
  detail.value = row
  detailVisible.value = true
}

/** 日/周视图：给今天已过时段（时间轴 6:00~当前时间，按比例）加灰遮罩（与教师端一致） */
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
onBeforeUnmount(() => window.removeEventListener('resize', shadePastArea))
</script>

<style scoped>
.cal-panel :deep(.fc .fc-toolbar-title) { font-size: 16px; color: #142a44; }
.cal-panel :deep(.fc .fc-button) {
  background: #fff; border: 1px solid #d0d5dd; color: #475467;
  font-size: 13px; padding: 4px 10px; box-shadow: none;
}
.cal-panel :deep(.fc .fc-button:hover) { color: #155eef; border-color: #155eef; }
.cal-panel :deep(.fc .fc-button-primary:not(:disabled).fc-button-active),
.cal-panel :deep(.fc .fc-button-primary:not(:disabled):active) { background: #155eef; color: #fff; }
.cal-panel :deep(.fc) { --fc-border-color: #e4e7ec; --fc-today-bg-color: transparent; height: 100%; }
.cal-panel :deep(.fc .fc-col-header-cell-cushion) { color: #475467; padding: 6px 4px; }
.cal-panel :deep(.fc-timegrid-slot) { height: 2.2em; }
.cal-panel :deep(.fc-timegrid-slot-label-cushion) { color: #667085; font-size: 11px; }
.cal-panel :deep(.fc .fc-event) { border-radius: 5px; border: none; cursor: pointer; }
/* 两行式色块内容 */
.cal-panel :deep(.ev2) {
  display: flex; flex-direction: column; gap: 1px;
  padding: 1px 4px 2px; line-height: 1.25; overflow: hidden;
}
.cal-panel :deep(.ev2-n) {
  font-size: 11.5px; font-weight: 600; color: #fff;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.cal-panel :deep(.ev2-t) { font-size: 10px; font-weight: 400; opacity: 0.85; }
.cal-panel :deep(.ev2-m) {
  font-size: 10.5px; color: rgba(255, 255, 255, 0.92);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
/* 状态标记小徽章（动态创建的 DOM，须经 :deep 挂靠） */
.cal-panel :deep(.ev2-st) {
  display: inline-block; padding: 0 4px; margin-right: 1px;
  border-radius: 3px;
  font-size: 10px; font-weight: 600; line-height: 1.5;
}
.cal-panel :deep(.fc .fc-daygrid-day-number) { color: #475467; }
/* 今天列底色（当日可用时段）：淡紫（与教师端一致） */
.cal-panel :deep(.fc .fc-day-today) { background: rgba(124, 58, 237, 0.28) !important; }
/* 过去的整天置灰 */
.cal-panel :deep(.fc .fc-day-past) { background: #cbd5e1 !important; }
.cal-panel :deep(.fc .fc-day-past .fc-daygrid-day-number) { color: #8b95a7; }
/* 今天已过时段遮罩 */
.cal-panel :deep(.fc-timegrid-past-overlay) {
  position: absolute; top: 0; left: 0; width: 100%;
  background: rgba(148, 163, 184, 0.45); pointer-events: none; z-index: 2;
}
.cal-panel :deep(.fc-daygrid-day-frame) { min-height: 90px; }
.ro-tip { font-size: 12px; color: #98a2b3; }
</style>
