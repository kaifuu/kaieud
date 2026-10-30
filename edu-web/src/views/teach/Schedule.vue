<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">我的课表</span>
      <span class="ro-tip">只读视图：如需调课、请假，请联系教务处理</span>
    </div>

    <!-- 一级视图TAB：日视图 / 周视图 / 月视图（教师仅查看，不能调整/删除） -->
    <el-tabs v-model="viewTab" class="view-tabs">
      <el-tab-pane label="日视图" name="day" />
      <el-tab-pane label="周视图" name="week" />
      <el-tab-pane label="月视图" name="month" />
    </el-tabs>

    <div class="panel cal-panel" style="height: calc(100% - 90px); padding: 12px">
      <FullCalendar ref="calRef" :options="calOptions" />
    </div>

    <!-- 课次详情（只读）：点击色块查看 -->
    <el-dialog v-model="detailVisible" title="课次详情" width="420px">
      <el-descriptions :column="1" border size="small" v-if="detail">
        <el-descriptions-item label="课程">{{ detail.courseName }}</el-descriptions-item>
        <el-descriptions-item label="形式">
          <el-tag size="small" :type="detail.classType === '班课' ? 'primary' : 'warning'">{{ detail.classType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="班级/学员">{{ detail.name }}</el-descriptions-item>
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
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import api from '../../api'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'

/* ===== 只读课表：与排课管理同款日历视觉，但不可拖拽/框选/删除/调时 ===== */
const calRef = ref()
const viewTab = ref('week')
const detailVisible = ref(false)
const detail = ref(null)
const rawRows = ref([])
// 0待上课 1已到课 2已取消 3教师请假 4学生请假 5旷课待审批 6旷课免扣 7旷课已扣（与排课管理同色板）
const CAL_COLORS = { 0: '#155EEF', 1: '#12b76a', 2: '#c0c4cc', 3: '#f79009', 4: '#fdb022', 5: '#f04438', 6: '#66c555', 7: '#d92d20' }
const STATUS_TEXT = ['待上课', '已到课', '已取消', '教师请假', '学生请假', '旷课·待审批', '旷课·已免扣', '旷课·已扣课']
const statusText = (s) => STATUS_TEXT[s] || '-'
const statusTagType = (s) => ({ 0: 'primary', 1: 'success', 2: 'info', 3: 'warning', 4: 'warning', 5: 'danger', 6: 'success', 7: 'danger' }[s] || 'info')

async function loadEvents(info, successCallback, failureCallback) {
  try {
    const rows = (await api.teachSchedule({
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
        // 两行式色块：行1 名称，行2 📍教室 · 课程
        name: r.name,
        meta: [r.classroom ? '📍' + r.classroom : '', r.courseName].filter(Boolean).join(' · ')
      }
    })))
  } catch (e) { failureCallback(e) }
}

/** 色块两行式内容（与排课管理一致；DOM 为 FullCalendar 动态创建，CSS 须经 :deep 挂靠） */
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
  // 只读：不可拖拽调时、不可框选新增（排课管理才有的操作）
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

/* ===== 视图切换 ===== */
watch(viewTab, async (v) => {
  await nextTick()
  const fcApi = calRef.value?.getApi?.()
  if (!fcApi) return
  fcApi.changeView({ day: 'timeGridDay', week: 'timeGridWeek', month: 'dayGridMonth' }[v])
  setTimeout(() => { if (typeof fcApi.resize === 'function') fcApi.resize(); shadePastArea() }, 50)
})

/** 日/周视图：给今天已过时段（时间轴 6:00~当前时间，按比例）加灰遮罩（与排课管理一致） */
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
.cal-panel :deep(.fc .fc-daygrid-day-number) { color: #475467; }
/* 今天列底色（当日可用时段）：淡紫（与排课管理一致） */
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
