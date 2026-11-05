<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">班级管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增班级</el-button>
        <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="班级名称" clearable style="width: 190px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="招生中" :value="1" /><el-option label="开课中" :value="2" /><el-option label="已结课" :value="3" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="clazz" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort" @selection-change="sel = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="班级名称" min-width="160" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.courseId" label="课程" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classTeacherId" label="班主任" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.classTeacherId" size="small" effect="light">{{ teacherName(row.classTeacherId) }}</el-tag>
            <span v-else>未指定</span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.capacity" label="容量(占用/空余/总量)" width="150">
          <template #default="{ row }">
            <span style="color: #12b76a; font-weight: 600">{{ stuCount[row.id] ?? 0 }}</span>
            /
            <span style="color: #f79009; font-weight: 600">{{ Math.max((row.capacity || 0) - (stuCount[row.id] ?? 0), 0) }}</span>
            /
            <span style="color: #475467">{{ row.capacity }}</span> 名
          </template>
        </el-table-column>
        <el-table-column v-if="cols.startDate" prop="startDate" label="开课日期" width="115" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.status" label="状态" width="90" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="['', 'primary', 'success', 'info'][row.status]" effect="light">
              {{ ['', '招生中', '开课中', '已结课'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <el-dropdown trigger="click" popper-class="more-dropdown"
                         @visible-change="(v) => { row.__moreOpen = v }"
                         @command="(cmd) => onRowCommand(cmd, row)">
              <span class="more-trigger" :class="{ open: row.__moreOpen }">
                <el-icon :size="12"><MoreFilled /></el-icon>
                <span>更多</span>
                <el-icon :size="10" class="more-arrow"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail" class="more-item">
                    <span class="mi mi-green"><el-icon :size="13"><View /></el-icon></span>
                    <span class="mi-txt"><b>查看详情</b><i>班级完整信息</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="students" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><User /></el-icon></span>
                    <span class="mi-txt"><b>学生管理</b><i>添加 / 调班 / 课时明细</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="edit" class="more-item">
                    <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                    <span class="mi-txt"><b>编辑</b><i>修改班级信息</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="del" divided class="more-item">
                    <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                    <span class="mi-txt"><b>删除</b><i>有在读学生不可删</i></span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>


    <!-- 班级学生管理：学生列表(合同课时) + 添加学生(合同/姓名/手机号搜索) + 调班 -->
    <el-drawer v-model="stuVisible" :title="`学生管理 · ${curClazz?.name || ''}`" size="760px">
      <div class="stu-cap-bar">
        <el-tag effect="dark" type="success">已占用 {{ stuInfo.studying }} 名</el-tag>
        <el-tag effect="dark" type="warning">空余 {{ Math.max((stuInfo.capacity || 0) - (stuInfo.studying || 0), 0) }} 名</el-tag>
        <el-tag effect="dark">总容量 {{ stuInfo.capacity }} 名</el-tag>
        <div style="flex: 1"></div>
        <el-button type="primary" size="small"
                   :disabled="(stuInfo.studying || 0) >= (stuInfo.capacity || 0)"
                   @click="openAddStu">添加学生</el-button>
      </div>

      <el-table :data="stuInfo.rows || []" v-loading="stuLoading" stripe size="small">
        <el-table-column type="index" label="序号" width="55" show-overflow-tooltip />
        <el-table-column prop="name" label="学生" width="100" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="125" show-overflow-tooltip>
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="报课总数" width="90" align="center">
          <template #default="{ row }">{{ row.totalHours }} 节</template>
        </el-table-column>
        <el-table-column label="已上课数" width="90" align="center">
          <template #default="{ row }">
            <span style="color: #155EEF; font-weight: 600">{{ row.usedHours }}</span> 节
          </template>
        </el-table-column>
        <el-table-column label="未上课数" width="90" align="center">
          <template #default="{ row }">
            <span style="color: #12b76a; font-weight: 600">{{ row.remainHours }}</span> 节
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.enrollTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" @click="openTransfer(row)">调班</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!(stuInfo.rows || []).length && !stuLoading" description="暂无学生，点「添加学生」按合同/姓名/手机号加入" :image-size="70" />
    </el-drawer>

    <!-- 添加学生：合同编号/学生姓名/手机号 搜索 + 绑定合同 -->
    <el-dialog v-model="addStuVisible" title="添加学生（绑定合同）" width="560px">
      <el-form label-width="90px">
        <el-form-item label="搜索">
          <el-input v-model="candKeyword" placeholder="输入即搜：合同编号 / 学生姓名 / 学生手机号"
                    clearable style="width: 100%">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
      </el-form>
      <el-table :data="candidates" v-loading="candLoading" size="small" stripe max-height="300"
                highlight-current-row @current-change="(r) => (pickedStu = r)">
        <el-table-column prop="name" label="学生" width="90" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="120" show-overflow-tooltip />
        <el-table-column label="可绑合同（剩余课时）" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-for="c in row.contracts" :key="c.id" class="c-contract-wrap">
              <span class="c-contract"
                    :class="{ picked: pickedStu === row && pickedContractId === c.id, expired: contractState(c) !== 'ok', unsigned: contractState(c) === 'none' }"
                    @click="contractState(c) === 'ok' && pick(row, c)">
                {{ c.contractNo }}（剩{{ c.remainHours }}节）
              </span>
              <el-tag size="small" class="c-state" effect="light"
                      :type="contractState(c) === 'none' ? 'info' : (contractState(c) === 'ok' ? 'success' : 'warning')">
                {{ contractState(c) === 'none' ? '未签合同' : (contractState(c) === 'ok' ? '合同生效中' : '合同已到期') }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!candidates.length && !candLoading" description="未搜到有生效合同的学生" :image-size="60" />
      <template #footer>
        <el-button @click="addStuVisible = false">取消</el-button>
        <el-button type="primary" :loading="addStuSaving" @click="addStudent">加入班级</el-button>
      </template>
    </el-dialog>

    <!-- 调班 -->
    <el-dialog v-model="transferVisible" title="学生调班" width="440px">
      <el-form label-width="90px">
        <el-form-item label="学生">
          <el-input :model-value="transferStu?.name" disabled />
        </el-form-item>
        <el-form-item label="当前班级">
          <el-input :model-value="curClazz?.name" disabled />
        </el-form-item>
        <el-form-item label="目标班级" required>
          <el-select v-model="transferTargetId" filterable style="width: 100%" placeholder="选择目标班级">
            <el-option v-for="c in transferTargets" :key="c.id"
                       :label="`${c.name}（占用${stuCount[c.id] ?? 0}/${c.capacity}）`" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="form-tip">调班后原班级名额自动释放，新班级不能超容量；课程不一致不允许调班</div>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSaving" @click="doTransfer">确认调班</el-button>
      </template>
    </el-dialog>
    
    <el-drawer v-model="visible" :title="form.id ? '编辑班级' : '新增班级'" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="班级名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班主任">
          <el-select v-model="form.classTeacherUserId" clearable filterable style="width: 100%"
                     placeholder="输入姓名搜索员工">
            <el-option v-for="c in staffCandidates" :key="c.userId" :label="c.name" :value="c.userId">
              <span>{{ c.name }}</span>
              <el-tag size="small" effect="plain" style="margin-left: 8px">{{ STAFF_NAMES[c.userType] || '员工' }}</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
        <el-form-item label="开课日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="招生中" :value="1" /><el-option label="开课中" :value="2" /><el-option label="已结课" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
    <DetailDrawer v-model="detailVisible" title="班级详情" :fields="detailFields" :data="detailRow" />
  </div>
</template>

<script setup>
import {reactive, ref, computed, onMounted, watch} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {Search, MoreFilled, ArrowDown, View, User, EditPen, Delete} from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import DetailDrawer from '../../components/DetailDrawer.vue'

const query = reactive({ keyword: '', status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.clazzPage(q), query)

const courses = ref([])
const teachers = ref([])
/* 班主任候选：用户管理中类型为员工的在职人员（保存时后端按 userId 解析教师档案） */
const staffCandidates = ref([])
const STAFF_NAMES = { 2: '教务人员', 3: '班主任', 6: '校区校长', 7: '校区咨询师', 8: '校区学管师' }
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const teacherName = (id) => {
  const cand = staffCandidates.value.find((c) => c.teacherId === id)
  return cand?.name || teachers.value.find((t) => t.id === id)?.name || '-'
}

const allColumns = [
  { prop: 'name', label: '班级名称' }, { prop: 'courseId', label: '课程' },
  { prop: 'classTeacherId', label: '班主任' }, { prop: 'capacity', label: '容量' },
  { prop: 'startDate', label: '开课日期' }, { prop: 'status', label: '状态' }
]
const cols = reactive({ name: true, courseId: true, classTeacherId: true,
  capacity: true, startDate: true, status: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const saving = ref(false)
const form = ref({})
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
      { prop: 'name', label: '班级名称' },
      { prop: 'courseId', label: '课程', formatter: courseName },
      { prop: 'classTeacherId', label: '班主任', formatter: teacherName },
      { prop: 'capacity', label: '容量' }, { prop: 'startDate', label: '开课日期' },
      { prop: 'status', label: '状态', map: { 1: '招生中', 2: '开课中', 3: '已结课' } }
    ]
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}
function openEdit(row) {
  form.value = row ? { ...row } : { capacity: 20, status: 1 }
  // 已有班主任（教师档案ID）回显为对应员工 userId
  if (row?.classTeacherId) {
    const cand = staffCandidates.value.find((c) => c.teacherId === row.classTeacherId)
    form.value.classTeacherUserId = cand ? cand.userId : undefined
  }
  visible.value = true
}
async function save() {
  if (!form.value.name) return ElMessage.warning('班级名称不能为空')
  // 后端仅按 classTeacherUserId 解析班主任；清空选择时须把原教师档案ID一并置空
  if (!form.value.classTeacherUserId) form.value.classTeacherId = null
  saving.value = true
  try {
    if (form.value.id) await api.clazzUpdate(form.value)
    else await api.clazzSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    // 班主任可能在保存时才补建教师档案，刷新映射表避免列表显示 '-'
    await refreshTeacherMaps()
    load()
  } finally { saving.value = false }
}
function onRowCommand(cmd, row) {
  if (cmd === 'detail') openDetail(row)
  else if (cmd === 'students') openStudents(row)
  else if (cmd === 'edit') openEdit(row, null)
  else if (cmd === 'del') remove(row.id)
}

async function remove(id) {
  await api.clazzDel(id)
  ElMessage.success('已删除')
  load()
}

function doExport() {
  exportXlsx('班级列表', [
      { label: '班级名称', prop: 'name' }, { label: '容量', prop: 'capacity' },
      { label: '开课日期', prop: 'startDate' }
    ], rows.value)
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条班级数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.clazzDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

/** 刷新班主任名称映射（候选员工 + 教师档案，列表/详情按 classTeacherId 反查姓名） */
async function refreshTeacherMaps() {
  staffCandidates.value = (await api.clazzTeacherCandidates()) || []
  teachers.value = (await api.teacherList()) || []
}

/* ===== 班级学生管理 ===== */
const stuVisible = ref(false)
const stuLoading = ref(false)
const curClazz = ref(null)
const stuInfo = reactive({ capacity: 0, studying: 0, rows: [] })
/** 列表容量占用统计：clazzId -> 在读人数 */
const stuCount = ref({})
const clazzList = ref([])

async function loadStuCounts() {
  clazzList.value = (await api.clazzList()) || []
  const map = {}
  const all = await api.enrollPage({ page: 1, size: 1000, status: 1 })
  for (const e of (all.records || [])) {
    if (e.clazzId) map[e.clazzId] = (map[e.clazzId] || 0) + 1
  }
  stuCount.value = map
}

function openStudents(row) {
  curClazz.value = row
  stuVisible.value = true
  loadStudents()
}
async function loadStudents() {
  stuLoading.value = true
  try {
    const d = await api.clazzStudents(curClazz.value.id)
    Object.assign(stuInfo, d || {})
  } finally { stuLoading.value = false }
}
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

/* 添加学生 */
const addStuVisible = ref(false)
const candKeyword = ref('')
const candidates = ref([])
const candLoading = ref(false)
const addStuSaving = ref(false)
const pickedStu = ref(null)
const pickedContractId = ref(null)

function openAddStu() {
  pickedStu.value = null
  pickedContractId.value = null
  candKeyword.value = ''
  candidates.value = []
  addStuVisible.value = true
  loadCandidates()
}

/** 合同状态：0未签(未生效) / 生效中(含到期日未到且status<3) / 已到期(已过结束日或status=4) */
function contractState(c) {
  if (c.status === 0) return 'none'
  if (c.status === 4) return 'expired'
  if (c.endDate && new Date(c.endDate) < new Date(new Date().toDateString())) return 'expired'
  return 'ok'
}

/** 联动搜索：输入即查（400ms 防抖），空关键字=全部有生效合同的学生 */
let candTimer = null
watch(candKeyword, () => {
  clearTimeout(candTimer)
  candTimer = setTimeout(loadCandidates, 400)
})
async function loadCandidates() {
  candLoading.value = true
  try {
    candidates.value = (await api.clazzStudentCandidates({
      keyword: candKeyword.value, clazzId: curClazz.value.id
    })) || []
  } finally { candLoading.value = false }
}
function pick(stu, contract) {
  pickedStu.value = stu
  pickedContractId.value = contract.id
}
async function addStudent() {
  if (!pickedStu.value || !pickedContractId.value)
    return ElMessage.warning('请先选择学生并点击其合同')
  addStuSaving.value = true
  try {
    await api.clazzAddStudent(curClazz.value.id, {
      studentId: pickedStu.value.studentId, contractId: pickedContractId.value
    })
    ElMessage.success('已加入班级')
    addStuVisible.value = false
    loadStudents()
    loadStuCounts()
  } finally { addStuSaving.value = false }
}

/* 调班 */
const transferVisible = ref(false)
const transferStu = ref(null)
const transferTargetId = ref(null)
const transferSaving = ref(false)
const transferTargets = computed(() =>
  clazzList.value.filter((c) => c.id !== curClazz.value?.id && c.status !== 3))

function openTransfer(row) {
  if (!clazzList.value.length) clazzList.value = (api.clazzList()) || []
  transferStu.value = row
  transferTargetId.value = null
  transferVisible.value = true
}
async function doTransfer() {
  if (!transferTargetId.value) return ElMessage.warning('请选择目标班级')
  transferSaving.value = true
  try {
    await api.clazzTransfer(curClazz.value.id, {
      studentId: transferStu.value.studentId, toClazzId: transferTargetId.value
    })
    ElMessage.success('调班成功')
    transferVisible.value = false
    loadStudents()
    loadStuCounts()
  } finally { transferSaving.value = false }
}

onMounted(async () => {
  load()
  courses.value = (await api.courseList()) || []
  await refreshTeacherMaps()
})
</script>

<style scoped>
.more-trigger {
  display: inline-flex; align-items: center; gap: 3px;
  font-size: 13px; color: #475467;
  padding: 3px 10px; border-radius: 12px;
  border: 1px solid #e4edf7;
  background: linear-gradient(180deg, #ffffff, #f7fafd);
  cursor: pointer; white-space: nowrap;
  transition: all 0.2s ease;
}
.more-trigger:hover, .more-trigger.open {
  color: #155EEF; border-color: rgba(21, 94, 239, 0.45);
  background: linear-gradient(180deg, #ffffff, #f0f7ff);
  box-shadow: 0 3px 10px rgba(21, 94, 239, 0.16);
}
.more-arrow { transition: transform 0.2s ease; }
.more-trigger.open .more-arrow { transform: rotate(180deg); }
</style>

<style scoped>
.c-contract-wrap {
  display: flex; align-items: center; gap: 6px;
  padding: 2px 0; white-space: nowrap;
}
.c-contract {
  display: inline-block; padding: 2px 10px; border-radius: 10px;
  border: 1px solid #e4edf7; background: #f7fafd;
  font-size: 12.5px; color: #475467; cursor: pointer;
  transition: all 0.15s ease;
}
.c-contract:hover { color: #155EEF; border-color: rgba(21, 94, 239, 0.45); }
.c-contract.picked {
  color: #fff; border-color: transparent;
  background: linear-gradient(135deg, #155EEF, #5B8DEF);
}
.c-contract.expired { color: #98a2b3; cursor: not-allowed; text-decoration: line-through; }
.c-contract.unsigned { color: #98a2b3; }
.c-state { flex-shrink: 0; }
</style>
