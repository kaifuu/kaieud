<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">报名管理</span>
      <div class="actions">
        <el-button type="primary" @click="openSubscribe">为学生订阅班课</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="query.studentId" placeholder="学生" clearable filterable style="width: 150px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="在读" :value="1" /><el-option label="已结课" :value="2" /><el-option label="已退费" :value="3" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="enroll" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.studentId" label="学生" width="100">
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseId" label="课程" min-width="150">
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.clazzId" label="班级" width="150">
          <template #default="{ row }">{{ row.clazzId ? clazzName(row.clazzId) : '一对一' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teacherId" label="授课老师" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.teacherId" size="small" effect="light">{{ teacherName(row.teacherId) }}</el-tag>
            <el-tag v-else size="small" type="danger" effect="light">未指派</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.usedHours" label="课时(已用/总)" width="125" sortable="custom" prop="usedHours">
          <template #default="{ row }">{{ row.usedHours }} / {{ row.totalHours }}</template>
        </el-table-column>
        <el-table-column v-if="cols.totalAmount" label="缴费(已缴/应收)" width="155" sortable="custom" prop="totalAmount">
          <template #default="{ row }">{{ row.paidAmount }} / {{ row.totalAmount }}（{{ row.discount }}折）</template>
        </el-table-column>
        <el-table-column v-if="cols.status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="['', 'success', 'info', 'danger'][row.status]" effect="plain">
              {{ ['', '在读', '已结课', '已退费'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="assign(row)">指派老师</el-button>
            <el-button link type="warning" @click="compensate(row)">补课时</el-button>
            <el-button v-if="row.status === 1" link type="danger" @click="unsubscribe(row)">退订</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 订阅班课 -->
    <el-drawer v-model="subVisible" title="为学生订阅班课" size="420px">
      <el-form label-width="90px">
        <el-form-item label="学生" required>
          <el-select v-model="subForm.studentId" filterable style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" required>
          <el-select v-model="subForm.clazzId" style="width: 100%">
            <el-option v-for="c in clazzes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="授课老师">
          <el-select v-model="subForm.teacherId" filterable clearable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSubscribe">订阅</el-button>
      </template>
    </el-drawer>

    <!-- 指派老师 -->
    <el-dialog v-model="assignVisible" title="指派/更换授课老师" width="400px">
      <el-select v-model="assignTeacherId" filterable style="width: 100%">
        <el-option v-for="t in teachers" :key="t.id" :label="`${t.name}（${t.subject || ''}）`" :value="t.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssign">确定</el-button>
      </template>
    </el-dialog>
    <DetailDrawer v-model="detailVisible" title="报名详情" :fields="detailFields" :data="detailRow" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import DetailDrawer from '../../components/DetailDrawer.vue'

const query = reactive({ studentId: null, status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.enrollPage(q), query)

const students = ref([])
const teachers = ref([])
const clazzes = ref([])
const courses = ref([])
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const clazzName = (id) => clazzes.value.find((c) => c.id === id)?.name || '-'
const teacherName = (id) => teachers.value.find((t) => t.id === id)?.name || '-'

const allColumns = [
  { prop: 'studentId', label: '学生' }, { prop: 'courseId', label: '课程' },
  { prop: 'clazzId', label: '班级' }, { prop: 'teacherId', label: '授课老师' },
  { prop: 'usedHours', label: '课时' }, { prop: 'totalAmount', label: '缴费' },
  { prop: 'status', label: '状态' }
]
const cols = reactive({ studentId: true, courseId: true, clazzId: true, teacherId: true,
  usedHours: true, totalAmount: true, status: true })
const onCols = (c) => Object.assign(cols, c)

/* 订阅 */
const subVisible = ref(false)
const subForm = ref({})
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
      { prop: 'studentId', label: '学生', formatter: studentName },
      { prop: 'courseId', label: '课程', formatter: courseName },
      { prop: 'clazzId', label: '班级', formatter: (v) => (v ? clazzName(v) : '一对一') },
      { prop: 'teacherId', label: '授课老师', formatter: teacherName },
      { prop: 'totalHours', label: '总课时' }, { prop: 'usedHours', label: '已用课时' },
      { prop: 'price', label: '单价(元)' }, { prop: 'discount', label: '折扣(%)' },
      { prop: 'totalAmount', label: '应收总额(元)' }, { prop: 'paidAmount', label: '已缴金额(元)' },
      { prop: 'status', label: '状态', map: { 1: '在读', 2: '已结课', 3: '已退费' } }
    ]
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}
function openSubscribe() { subForm.value = {}; subVisible.value = true }
async function saveSubscribe() {
  const f = subForm.value
  if (!f.studentId || !f.clazzId) return ElMessage.warning('请选择学生和班级')
  const c = clazzes.value.find((x) => x.id === f.clazzId)
  await api.enrollSubscribe({ studentId: f.studentId, courseId: c?.courseId, clazzId: f.clazzId, teacherId: f.teacherId })
  ElMessage.success('订阅成功，请前往缴费管理收款')
  subVisible.value = false
  load()
}
async function unsubscribe(row) {
  await ElMessageBox.confirm('确定取消该订阅吗？未来待上课次将自动取消', '提示', { type: 'warning' })
  await api.enrollUnsubscribe(row.id)
  ElMessage.success('已退订')
  load()
}

/* 指派老师 / 补课时 */
const assignVisible = ref(false)
const assignTeacherId = ref(null)
const currentRow = ref(null)
function assign(row) {
  currentRow.value = row
  assignTeacherId.value = row.teacherId
  assignVisible.value = true
}
async function saveAssign() {
  if (!assignTeacherId.value) return ElMessage.warning('请选择老师')
  await api.enrollAssignTeacher(currentRow.value.id, assignTeacherId.value)
  ElMessage.success('已指派')
  assignVisible.value = false
  load()
}
async function compensate(row) {
  const { value } = await ElMessageBox.prompt('补充课时次数（因请假取消的课时）', '补课时', { inputValue: '1' })
  await api.enrollCompensateHours(row.id, Number(value) || 1)
  ElMessage.success('课时已补充')
  load()
}


function doExport() {
  exportXlsx('报名列表', [
      { label: '学员', prop: 'studentId', fmt: (v) => studentName(v) },
      { label: '课程', prop: 'courseId', fmt: (v) => courseName(v) },
      { label: '总课时', prop: 'totalHours' }, { label: '已消课时', prop: 'usedHours' },
      { label: '应收', prop: 'totalAmount' }, { label: '已缴', prop: 'paidAmount' },
      { label: '状态', prop: 'status', fmt: (v) => ['', '在读', '已结课', '已退费'][v] || '' }
    ], rows.value)
}
onMounted(async () => {
  load()
  students.value = (await api.studentList()) || []
  teachers.value = (await api.teacherList()) || []
  clazzes.value = (await api.clazzList()) || []
  courses.value = (await api.courseList()) || []
})
</script>
