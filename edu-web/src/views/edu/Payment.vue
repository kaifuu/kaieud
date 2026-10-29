<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">缴费管理</span>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新增缴费</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="query.studentId" placeholder="学生" clearable filterable style="width: 150px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="payment" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.paymentNo" prop="paymentNo" label="缴费单号" width="185" sortable="custom" />
        <el-table-column v-if="cols.studentId" label="学生" width="95">
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseId" label="课程" min-width="140">
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.amount" prop="amount" label="实缴金额" width="105" sortable="custom" />
        <el-table-column v-if="cols.discountAmount" prop="discountAmount" label="优惠金额" width="105" sortable="custom" />
        <el-table-column v-if="cols.payType" label="支付方式" width="100" sortable="custom">
          <template #default="{ row }">{{ dictStore.label('pay_channel', row.payType) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.operator" prop="operator" label="经办人" width="95" />
        <el-table-column v-if="cols.payTime" label="缴费时间" width="165" sortable="custom">
          <template #default="{ row }">{{ fmt(row.payTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <el-drawer v-model="visible" title="新增缴费" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="学生" required>
          <el-select v-model="form.studentId" filterable style="width: 100%" @change="loadEnrolls">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联合同" required>
          <el-select v-model="form.contractId" filterable style="width: 100%"
                     placeholder="选择合同（带出应收/已缴/待缴）">
            <el-option v-for="c in contracts" :key="c.id"
                       :label="`${c.contractNo} 应收${c.totalAmount}/已缴${c.paidAmount}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="收款凭证">
          <input type="file" accept=".pdf,.jpg,.jpeg,.png" @change="onVoucher" />
          <span v-if="form.voucherUrl" style="color:#0094FF;font-size:12px">已上传</span>
        </el-form-item>
        <el-form-item label="实缴金额" required><el-input-number v-model="form.amount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="优惠金额"><el-input-number v-model="form.discountAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="form.payType" style="width: 100%">
            <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
    <DetailDrawer v-model="detailVisible" title="缴费详情" :fields="detailFields" :data="detailRow" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const query = reactive({ studentId: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.paymentPage(q), query)

const students = ref([])
const courses = ref([])
const enrolls = ref([])
const contracts = ref([])
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

const allColumns = [
  { prop: 'paymentNo', label: '缴费单号' }, { prop: 'studentId', label: '学生' },
  { prop: 'courseId', label: '课程' }, { prop: 'amount', label: '实缴金额' },
  { prop: 'discountAmount', label: '优惠金额' }, { prop: 'payType', label: '支付方式' },
  { prop: 'operator', label: '经办人' }, { prop: 'payTime', label: '缴费时间' }
]
const cols = reactive({ paymentNo: true, studentId: true, courseId: true, amount: true,
  discountAmount: true, payType: true, operator: true, payTime: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const saving = ref(false)
const form = ref({})
async function loadEnrolls() {
  if (!form.value.studentId) return
  const d = await api.enrollPage({ page: 1, size: 50, studentId: form.value.studentId, status: 1 })
  enrolls.value = d.records || []
}
function openCreate() { form.value = { payType: 2, discountAmount: 0 }; enrolls.value = []; visible.value = true }
async function save() {
  const f = form.value
  if (!f.studentId || !f.enrollId || !f.amount) return ElMessage.warning('请完善缴费信息')
  saving.value = true
  try {
    const c = contracts.value.find((x) => x.id === f.contractId)
    f.courseId = c?.courseId
    f.enrollId = null
    await api.paymentSave(f)
    ElMessage.success('缴费成功')
    visible.value = false
    load()
  } finally { saving.value = false }
}

function doExport() {
  exportXlsx('缴费流水', [
      { label: '缴费单号', prop: 'paymentNo' },
      { label: '学生', prop: 'studentId', fmt: (v) => studentName(v) },
      { label: '实缴金额', prop: 'amount' }, { label: '优惠金额', prop: 'discountAmount' },
      { label: '支付方式', prop: 'payType', fmt: (v) => ['', '现金', '微信', '支付宝', '银行转账'][v] },
      { label: '经办人', prop: 'operator' }, { label: '缴费时间', prop: 'payTime' }
    ], rows.value)
}
onMounted(async () => {
  load()
  dictStore.load(true)
  students.value = (await api.studentList()) || []
  courses.value = (await api.courseList()) || []
})
</script>
