<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">请假管理</span>
      <div class="actions">
        <el-button type="primary" @click="openApply">代学生请假</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="query.studentId" placeholder="学生" clearable filterable style="width: 150px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="待审批" :value="0" /><el-option label="已同意" :value="1" /><el-option label="已拒绝" :value="2" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="leave" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.studentId" label="学生" width="110">
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.scheduleId" prop="scheduleId" label="课次ID" width="90" sortable="custom" />
        <el-table-column v-if="cols.reason" prop="reason" label="请假原因" min-width="200" />
        <el-table-column v-if="cols.status" label="状态" width="95" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'danger'][row.status]" effect="plain">
              {{ ['待审批', '已同意', '已拒绝'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.createTime" label="申请时间" width="170" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="success" @click="approve(row, true)">同意</el-button>
              <el-button link type="danger" @click="approve(row, false)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <el-drawer v-model="visible" title="代学生请假" size="440px">
      <el-form label-width="90px">
        <el-form-item label="学生" required>
          <el-select v-model="form.studentId" filterable style="width: 100%" @change="loadSchedules">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课次" required>
          <el-select v-model="form.scheduleId" filterable style="width: 100%" placeholder="该学生待上课次">
            <el-option v-for="sc in schedules" :key="sc.id" :label="`${sc.startTime}（ID:${sc.id}）`" :value="sc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" required><el-input v-model="form.reason" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">提交</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'

const query = reactive({ studentId: null, status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.leavePage(q), query)

const students = ref([])
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

const allColumns = [
  { prop: 'studentId', label: '学生' }, { prop: 'scheduleId', label: '课次ID' },
  { prop: 'reason', label: '请假原因' }, { prop: 'status', label: '状态' },
  { prop: 'createTime', label: '申请时间' }
]
const cols = reactive({ studentId: true, scheduleId: true, reason: true, status: true, createTime: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const schedules = ref([])
const form = ref({})
function openApply() { form.value = {}; schedules.value = []; visible.value = true }
async function loadSchedules(studentId) {
  const d = await api.schedulePage({ page: 1, size: 50, studentId, status: 0 })
  schedules.value = d.records || []
}
async function approve(row, pass) {
  await api.leaveApprove(row.id, pass)
  ElMessage.success(pass ? '已同意，对应课次已取消（可在报名管理补课时）' : '已拒绝')
  load()
}
async function save() {
  if (!form.value.studentId || !form.value.scheduleId || !form.value.reason)
    return ElMessage.warning('请完善请假信息')
  await api.leaveApply(form.value)
  ElMessage.success('已提交')
  visible.value = false
  load()
}

function doExport() {
  exportXlsx('请假列表', [
      { label: '学生', prop: 'studentId', fmt: (v) => studentName(v) },
      { label: '课次ID', prop: 'scheduleId' }, { label: '原因', prop: 'reason' },
      { label: '状态', prop: 'status', fmt: (v) => ['待审批', '已同意', '已拒绝'][v] }
    ], rows.value)
}
onMounted(async () => {
  load()
  students.value = (await api.studentList()) || []
})
</script>
