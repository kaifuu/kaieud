<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">签到消课</span>
      <div class="actions">
        <el-button type="primary" @click="openManual">手动消课</el-button>
        <el-button @click="openSign">排课一键消课</el-button>
        <el-button @click="doExport">导出明细</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="filters.studentId" placeholder="学员" clearable filterable style="width: 140px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="filters.teacherId" placeholder="老师" clearable filterable style="width: 140px">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="query.auditStatus" placeholder="审核状态" clearable style="width: 120px">
          <el-option label="待审核" :value="0" /><el-option label="已审核" :value="1" /><el-option label="已驳回" :value="2" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column prop="consumeNo" label="消课编号" width="180" sortable="custom" />
        <el-table-column label="学员" width="90">
          <template #default="{ row }">{{ name(students, row.studentId) }}</template>
        </el-table-column>
        <el-table-column label="课程" min-width="120">
          <template #default="{ row }">{{ name(courses, row.courseId) }}</template>
        </el-table-column>
        <el-table-column label="班型" width="80">
          <template #default="{ row }">{{ dictStore.label('class_type', row.classType) }}</template>
        </el-table-column>
        <el-table-column label="授课老师" width="95">
          <template #default="{ row }">{{ name(teachers, row.teacherId) }}</template>
        </el-table-column>
        <el-table-column prop="hours" label="消耗课时" width="95" sortable="custom" />
        <el-table-column label="消课前/后" width="110">
          <template #default="{ row }">{{ row.beforeHours }} / {{ row.afterHours }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ ['正常上课', '补课'][row.consumeType] }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'danger'][(row.auditStatus ?? 1)]"
                    :effect="row.auditStatus === 0 ? 'light' : 'plain'">
              {{ ['待审核', '已审核', '已驳回'][(row.auditStatus ?? 1)] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.canceled === 1 ? 'info' : 'success'" effect="plain">
              {{ row.canceled === 1 ? '已撤销' : '有效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column label="消课时间" width="165" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 0 && row.canceled === 0 && canAudit">
              <el-button link type="success" @click="audit(row, true)">通过</el-button>
              <el-button link type="danger" @click="audit(row, false)">驳回</el-button>
            </template>
            <el-popconfirm v-else-if="row.canceled === 0 && canManage" title="确认撤销消课？课时将归还" @confirm="cancel(row.id)">
              <template #reference><el-button link type="warning">撤销</el-button></template>
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

    <!-- 手动消课抽屉 -->
    <el-drawer v-model="manualVisible" title="手动消课（无排课补课场景）" size="440px">
      <el-form label-width="100px">
        <el-form-item label="学员合同" required>
          <el-select v-model="mForm.contractId" filterable style="width: 100%"
                     placeholder="搜索学员后选择合同">
            <el-option v-for="c in contracts" :key="c.id"
                       :label="`${studentName(c.studentId)} - ${courseName(c.courseId)}（剩余${remainOf(c)}课时）`"
                       :value="c.id" :disabled="c.status === 3 || c.status === 4" />
          </el-select>
        </el-form-item>
        <el-form-item label="消耗课时" required>
          <el-input-number v-model="mForm.hours" :min="0.5" :step="0.5" :precision="2" />
          <span style="color:#909399;font-size:12px;margin-left:8px">支持0.5课时</span>
        </el-form-item>
        <el-form-item label="消课类型">
          <el-radio-group v-model="mForm.consumeType">
            <el-radio :value="0">正常上课</el-radio><el-radio :value="1">补课</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert title="消课将按 购买课时→赠送课时 顺序扣减，并自动计提教师成本" type="info" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveManual">确认消课</el-button>
      </template>
    </el-drawer>

    <!-- 排课一键消课抽屉 -->
    <el-drawer v-model="signVisible" title="排课一键签到消课" size="420px">
      <el-form label-width="100px">
        <el-form-item label="选择课次" required>
          <el-select v-model="signForm.scheduleId" filterable style="width: 100%"
                     placeholder="选择待上课次（班课自动对全班消课）">
            <el-option v-for="sc in pendingSchedules" :key="sc.id"
                       :label="`${sc.startTime} ${sc.classroom || ''}（${sc.clazzId ? '班课' : '一对一'} ID:${sc.id}）`"
                       :value="sc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="消耗课时">
          <el-input-number v-model="signForm.hours" :min="0.5" :step="0.5" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSign">确认签到消课</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import { exportXlsx } from '../../utils/export'
import { useDictStore } from '../../store/dict'

import { useUserStore } from '../../store/user'
const store = useUserStore()
// 消课审核权限（总部教务/运营）；撤销为教务管理类权限
const canAudit = store.hasPerm('edu:consume:audit')
const canManage = store.hasPerm('edu:consume') || canAudit

const dateRange = ref(null)
const filters = reactive({ studentId: null, teacherId: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.consumePage({ ...q, begin: dateRange.value?.[0], end: dateRange.value?.[1] }), filters)

const dictStore = useDictStore()
const students = ref([])
const teachers = ref([])
const courses = ref([])
const contracts = ref([])
const pendingSchedules = ref([])
const name = (list, id) => list.value.find((x) => x.id === id)?.name || '-'
const studentName = (id) => name(students, id)
const courseName = (id) => name(courses, id)
const remainOf = (c) => (Number(c.purchasedHours) + Number(c.giftHours) - Number(c.usedPurchased) - Number(c.usedGift)).toFixed(2)
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

watch(dateRange, search)

function doExport() {
  exportXlsx('消课明细', [
    { label: '消课编号', prop: 'consumeNo' }, { label: '消耗课时', prop: 'hours' },
    { label: '消课前', prop: 'beforeHours' }, { label: '消课后', prop: 'afterHours' },
    { label: '操作人', prop: 'operator' }, { label: '时间', prop: 'createTime' }
  ], rows.value)
}

/* 手动消课 */
const manualVisible = ref(false)
const saving = ref(false)
const mForm = ref({ hours: 1, consumeType: 0 })
function openManual() {
  mForm.value = { hours: 1, consumeType: 0 }
  api.contractPage({ page: 1, size: 200, status: 2 }).then((d) => { contracts.value = d.records || [] })
  manualVisible.value = true
}
async function saveManual() {
  if (!mForm.value.contractId) return ElMessage.warning('请选择学员合同')
  saving.value = true
  try {
    await api.consumeManual(mForm.value)
    ElMessage.success('消课成功，已自动计提教师成本')
    manualVisible.value = false
    load()
  } finally { saving.value = false }
}

/* 一键消课 */
const signVisible = ref(false)
const signForm = ref({ hours: 1 })
function openSign() {
  signForm.value = { hours: 1 }
  api.schedulePage({ page: 1, size: 100, status: 0 }).then((d) => { pendingSchedules.value = d.records || [] })
  signVisible.value = true
}
async function saveSign() {
  if (!signForm.value.scheduleId) return ElMessage.warning('请选择课次')
  saving.value = true
  try {
    const n = await api.consumeSign(signForm.value.scheduleId, signForm.value.hours)
    ElMessage.success(`签到消课完成，共消课 ${n} 份合同`)
    signVisible.value = false
    load()
  } finally { saving.value = false }
}

async function audit(row, pass) {
  const { value } = await ElMessageBox.prompt(pass ? '确认通过该课消？通过后计入成本报表' : '请输入驳回原因（课时将归还）',
    pass ? '通过课消' : '驳回课消', { type: pass ? 'success' : 'warning' })
  await api.consumeAudit(row.id, { pass, remark: pass ? '' : (value || '') })
  ElMessage.success(pass ? '已通过，计入成本报表' : '已驳回，课时已归还')
  load()
}

async function cancel(id) {
  await api.consumeCancel(id)
  ElMessage.success('已撤销，课时已归还并冲减成本')
  load()
}

onMounted(async () => {
  load()
  dictStore.load(true)
  students.value = (await api.studentList()) || []
  teachers.value = (await api.teacherList()) || []
  courses.value = (await api.courseList()) || []
})
</script>
