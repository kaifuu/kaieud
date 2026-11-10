<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">试听管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增试听</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="query.type" placeholder="试听类型" clearable style="width: 120px">
          <el-option label="插班试听" :value="1" /><el-option label="开班试听" :value="2" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="名称 / 课程 / 教师" clearable style="width: 190px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="audition" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="名称" min-width="150" sortable="custom" show-overflow-tooltip />
        <el-table-column v-if="cols.type" label="试听类型" width="92" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'primary' : 'warning'" effect="plain">
              {{ row.type === 1 ? '插班试听' : '开班试听' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.auditionDate" prop="auditionDate" label="日期" width="105" sortable="custom" />
        <el-table-column v-if="cols.campus" prop="campus" label="校区" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.campus || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseName" prop="courseName" label="课程" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.courseName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teacherName" prop="teacherName" label="教师" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.teacherName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classroomName" prop="classroomName" label="教室" width="95" show-overflow-tooltip>
          <template #default="{ row }">{{ row.classroomName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.timePeriod" prop="timePeriod" label="时段" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.timePeriod || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.assistantName" prop="assistantName" label="助教" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assistantName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.plannedCount" prop="plannedCount" label="预招人数" width="86" align="center" sortable="custom">
          <template #default="{ row }">{{ row.plannedCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.operator" prop="operator" label="经办人" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该试听记录?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 新增/编辑试听 -->
    <el-drawer v-model="visible" :title="form.id ? '编辑试听' : '新增试听'" size="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="试听类型">
          <el-radio-group v-model="form.type" :disabled="!!form.id">
            <el-radio :value="1">插班试听</el-radio>
            <el-radio :value="2">开班试听</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="关联日程">
          <div class="relate-box">
            <el-input :model-value="relateText" readonly placeholder="点击「选择班课」关联已成班级"
                      class="relate-input" />
            <el-button type="primary" plain @click="openClazzDlg">选择班课</el-button>
          </div>
          <div class="relate-tip">选择后自动回填名称/日期/校区/教师/教室/时段/课程，可再调整</div>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" placeholder="试听名称" /></el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.auditionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="校区">
          <el-select v-model="form.campus" clearable filterable style="width: 100%">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-input v-model="form.courseName" :disabled="form.type === 1 && !!form.relateClazzId"
                    placeholder="课程名称" />
        </el-form-item>
        <el-form-item label="教师">
          <el-select v-model="form.teacherName" clearable filterable style="width: 100%" placeholder="选择教师">
            <el-option v-for="t in teacherOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="form.classroomName" clearable filterable style="width: 100%">
            <el-option v-for="r in classroomList" :key="r.id"
                       :label="`${r.name}（${r.campus || '未设校区'}·容${r.capacity ?? 0}人）`" :value="r.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="时段">
          <el-time-picker v-model="timeRange" is-range range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          format="HH:mm" value-format="HH:mm" style="width: 230px" />
        </el-form-item>
        <el-form-item label="助教">
          <el-select v-model="form.assistantName" clearable filterable style="width: 100%">
            <el-option v-for="t in teacherOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="预招人数">
          <el-input-number v-model="form.plannedCount" :min="0" :max="999" style="width: 140px" />
          <span class="unit">人</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 选择班课弹窗：已成班级（开课中/招生中，课程为班课），点击行回填 -->
    <el-dialog v-model="clazzDlgVisible" title="选择班课（关联日程）" width="760px" append-to-body>
      <div class="dlg-toolbar">
        <el-input v-model="clazzKw" placeholder="班级名称 / 课程名称搜索" clearable
                  style="width: 220px" :prefix-icon="Search" />
        <span class="dlg-tip">点击行选择并回填（仅班课）</span>
      </div>
      <el-table :data="clazzFiltered" v-loading="clazzLoading" height="340" size="small" stripe
                highlight-current-row @current-change="pickClazz">
        <el-table-column prop="name" label="班级名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="课程" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column label="班主任" width="86" show-overflow-tooltip>
          <template #default="{ row }">{{ teacherName(row.classTeacherId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="教室" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ roomName(row.classroomId) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开班日期" width="100" />
        <el-table-column label="班容(占用/满班)" width="120" align="center">
          <template #default="{ row }">{{ stuCount[row.id] ?? 0 }}/{{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'warning' : 'success'" effect="plain">
              {{ ['', '招生中', '开课中'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!clazzFiltered.length && !clazzLoading" description="暂无可选班课（需先在班级管理创建班课班级）" :image-size="70" />
      <template #footer>
        <el-button @click="clazzDlgVisible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const query = reactive({ type: null, keyword: '' })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.auditionPage(q), query)

const allColumns = [
  { prop: 'name', label: '名称' }, { prop: 'type', label: '试听类型' },
  { prop: 'auditionDate', label: '日期' }, { prop: 'campus', label: '校区' },
  { prop: 'courseName', label: '课程' }, { prop: 'teacherName', label: '教师' },
  { prop: 'classroomName', label: '教室' }, { prop: 'timePeriod', label: '时段' },
  { prop: 'assistantName', label: '助教' }, { prop: 'plannedCount', label: '预招人数' },
  { prop: 'operator', label: '经办人' }
]
const cols = reactive({ name: true, type: true, auditionDate: true, campus: true, courseName: true,
  teacherName: true, classroomName: true, timePeriod: true, assistantName: false,
  plannedCount: true, operator: false })
const onCols = (c) => Object.assign(cols, c)

/* ===== 基础数据 ===== */
const courses = ref([])
const teachers = ref([])
const classroomList = ref([])
const staffCandidates = ref([])
const clazzes = ref([])
const stuCount = ref({})
const campusOptions = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))
/** 教师/助教候选（员工账号名） */
const teacherOptions = computed(() =>
  staffCandidates.value.map((c) => c.name).filter(Boolean))
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const teacherName = (id) => {
  const cand = staffCandidates.value.find((c) => c.teacherId === id)
  return cand?.name || teachers.value.find((t) => t.id === id)?.name || ''
}
const roomName = (id) => classroomList.value.find((r) => r.id === id)?.name || '-'

async function loadBase() {
  courses.value = (await api.courseList()) || []
  classroomList.value = (await api.classroomList()) || []
  staffCandidates.value = (await api.clazzTeacherCandidates()) || []
  teachers.value = (await api.teacherList()) || []
  // 已成班级（含招生中/开课中）+ 班课过滤 + 占用统计
  clazzes.value = (await api.clazzList()) || []
  const counts = {}
  const all = await api.enrollPage({ page: 1, size: 1000, status: 1 })
  for (const e of (all.records || [])) if (e.clazzId) counts[e.clazzId] = (counts[e.clazzId] || 0) + 1
  stuCount.value = counts
}

/* ===== 新增/编辑 ===== */
const visible = ref(false)
const saving = ref(false)
const form = ref({})
/** 时段时间范围（time-picker is-range，HH:mm 二元组，存库拼为 HH:mm-HH:mm） */
const timeRange = ref(null)
const parsePeriod = (s) => {
  const m = /^(\d{2}:\d{2})-(\d{2}:\d{2})$/.exec(s || '')
  return m ? [m[1], m[2]] : null
}
const relateText = computed(() => {
  if (form.value.type !== 1 || !form.value.relateClazzId) return ''
  const c = clazzes.value.find((x) => x.id === form.value.relateClazzId)
  return c ? `${c.name}（${courseName(c.courseId)}）` : ''
})
function openEdit(row) {
  form.value = row
    ? { ...row }
    : { type: 2, name: '', auditionDate: null, campus: null, courseName: '', teacherName: null,
        classroomName: null, timePeriod: '', assistantName: null, plannedCount: null,
        relateClazzId: null, remark: '' }
  timeRange.value = parsePeriod(form.value.timePeriod)
  visible.value = true
}
async function save() {
  if (!form.value.name?.trim()) return ElMessage.warning('名称不能为空')
  form.value.timePeriod = timeRange.value ? timeRange.value.join('-') : ''
  saving.value = true
  try {
    if (form.value.id) await api.auditionUpdate(form.value)
    else await api.auditionSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.auditionDel(id)
  ElMessage.success('已删除')
  load()
}

/* ===== 插班试听：选择班课弹窗 ===== */
const clazzDlgVisible = ref(false)
const clazzKw = ref('')
const clazzLoading = ref(false)
/** 班课 = 课程授课模式为「班课」（字典） */
const bankeVal = computed(() => {
  const hit = dictStore.options('class_type').find((d) => d.label === '班课')
  return hit ? Number(hit.value) : null
})
/** 可关联班级：非已结课 且 课程为班课 */
const clazzFiltered = computed(() => {
  const k = (clazzKw.value || '').trim().toLowerCase()
  return clazzes.value.filter((c) => {
    const course = courses.value.find((x) => x.id === c.courseId)
    if (!course || course.status !== 1) return false
    if (bankeVal.value != null && course.teachMode !== bankeVal.value) return false
    if (k && !(c.name || '').toLowerCase().includes(k)
      && !(course.name || '').toLowerCase().includes(k)) return false
    return true
  })
})
function openClazzDlg() {
  if (!clazzes.value.length) loadBase()
  clazzKw.value = ''
  clazzDlgVisible.value = true
}
/** 选中班级：回填全部字段（课程按关联班级锁定，其余可调） */
async function pickClazz(row) {
  if (!row) return
  const course = courses.value.find((x) => x.id === row.courseId)
  form.value.relateClazzId = row.id
  form.value.name = `${row.name}（插班试听）`
  form.value.auditionDate = row.startDate || form.value.auditionDate
  const room = classroomList.value.find((r) => r.id === row.classroomId)
  form.value.campus = room?.campus || (course?.campuses || [])[0] || form.value.campus
  form.value.courseName = course?.name || form.value.courseName
  form.value.teacherName = teacherName(row.classTeacherId) || form.value.teacherName
  form.value.classroomName = room?.name || form.value.classroomName
  // 时段：取该班最近一次未取消课次的开始-结束时间（时间范围选择器回显）
  try {
    const rs = (await api.schedulePage({ page: 1, size: 50, clazzId: row.id })).records || []
    const s = rs.find((x) => x.status !== 2)
    if (s) timeRange.value = [String(s.startTime).slice(11, 16), String(s.endTime || '').slice(11, 16)]
  } catch (e) { /* 无排课时保持原值 */ }
  form.value.plannedCount = form.value.plannedCount ?? Math.max((row.capacity || 0) - (stuCount.value[row.id] ?? 0), 0)
  clazzDlgVisible.value = false
  ElMessage.success(`已关联班级「${row.name}」，字段已回填`)
}

onMounted(async () => {
  load()
  dictStore.load(true)
  await loadBase()
})
</script>

<style scoped>
/* 关联日程：只读展示 + 选择按钮 */
.relate-box { display: flex; gap: 8px; width: 100%; }
.relate-input { flex: 1; }
.relate-tip { font-size: 12px; color: #98a2b3; line-height: 1.4; margin-top: 2px; }
.unit { font-size: 12.5px; color: #8fa8c4; margin-left: 6px; }
/* 选择班课弹窗 */
.dlg-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.dlg-tip { font-size: 12.5px; color: #8fa8c4; }
</style>
