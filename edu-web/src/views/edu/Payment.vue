<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">缴费管理</span>
      <div class="actions">
        <el-button v-if="tab === 'pay'" type="primary" @click="openCreate">新增缴费</el-button>
      </div>
    </div>
    <!-- 缴费 / 操作记录 双TAB -->
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="缴费" name="pay" />
      <el-tab-pane label="操作记录" name="log" />
    </el-tabs>
    <div v-if="tab === 'pay'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-select v-model="query.studentId" placeholder="学生" clearable filterable style="width: 150px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-input v-model="query.contractNo" placeholder="合同号" clearable style="width: 170px"
                  @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="payment" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.paymentNo" prop="paymentNo" label="缴费单号" width="185" sortable="custom" show-overflow-tooltip />
        <el-table-column v-if="cols.studentId" label="学生" width="95" show-overflow-tooltip>
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseId" label="课程" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.amount" prop="amount" label="实缴金额" width="105" sortable="custom" />
        <el-table-column v-if="cols.contractAmount" label="合同金额" width="105" show-overflow-tooltip>
          <template #default="{ row }">{{ contractAmount(row.contractId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.payType" label="支付方式" width="100" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ dictStore.label('pay_channel', row.payType) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.operator" prop="operator" label="经办人" width="95" show-overflow-tooltip />
        <el-table-column v-if="cols.updateBy" prop="updateBy" label="操作人" width="95" show-overflow-tooltip />
        <el-table-column v-if="cols.payTime" label="缴费时间" width="165" sortable="custom">
          <template #default="{ row }">{{ fmt(row.payTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">修改</el-button>
            <el-popconfirm title="确认删除该缴费单？合同已缴金额将自动重算，操作将留痕"
                           @confirm="remove(row)">
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

    <!-- 操作记录TAB：只读留痕，仅查询/筛选/分页 -->
    <div v-else class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-select v-model="logQuery.action" placeholder="操作类型" clearable style="width: 110px">
          <el-option label="新增" :value="1" /><el-option label="修改" :value="2" /><el-option label="删除" :value="3" />
        </el-select>
        <el-select v-model="logQuery.studentId" placeholder="学生" clearable filterable style="width: 130px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-input v-model="logQuery.paymentNo" placeholder="缴费单号" clearable style="width: 185px"
                  @keyup.enter="logSearch" @clear="logSearch" />
        <el-input v-model="logQuery.operator" placeholder="操作人" clearable style="width: 110px"
                  @keyup.enter="logSearch" @clear="logSearch" />
        <el-date-picker v-model="logDateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px"
                        @change="onLogDate" />
        <el-button type="primary" @click="logSearch">查询</el-button>
      </div>
      <el-table :data="logRows" v-loading="logLoading" stripe height="100%" @sort-change="onLogSort">
        <el-table-column type="index" label="序号" width="60" :index="logSeq" />
        <el-table-column label="操作类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="light"
                    :type="row.action === 1 ? 'success' : row.action === 2 ? 'warning' : 'danger'">
              {{ { 1: '新增', 2: '修改', 3: '删除' }[row.action] || row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paymentNo" label="缴费单号" width="185" sortable="custom" show-overflow-tooltip />
        <el-table-column label="学生" width="95" show-overflow-tooltip>
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column label="变更前金额" width="105">
          <template #default="{ row }">{{ row.amountBefore == null ? '-' : row.amountBefore }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="实缴金额" width="95" sortable="custom">
          <template #default="{ row }">{{ row.amount == null ? '-' : row.amount }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="变更明细" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="95" show-overflow-tooltip />
        <el-table-column prop="createTime" label="操作时间" width="165" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="logPager.total"
                       v-model:current-page="logPager.page" v-model:page-size="logPager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="logLoad" @size-change="onLogSizeChange" />
      </div>
    </div>

    <el-drawer v-model="visible" :title="form.id ? '修改缴费' : '新增缴费'" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="学生" required>
          <el-select v-model="form.studentId" filterable style="width: 100%" :disabled="!!form.id"
                     placeholder="选择学生（也可先选合同自动带出）" @change="onStudentChange">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联合同" required>
          <el-select v-model="form.contractId" filterable style="width: 100%" :disabled="!!form.id"
                     placeholder="搜索合同编号（未缴清合同：待缴费/部分缴费）" @change="onContractChange">
            <el-option v-for="c in contractOptions" :key="c.id" :label="contractLabel(c)" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同金额">
          <el-input :model-value="selectedContract ? selectedContract.totalAmount : ''"
                    disabled placeholder="选择合同后带出合同总价" />
        </el-form-item>
        <el-form-item label="已缴金额">
          <el-input :model-value="summary ? summary.paidAmount : ''" disabled
                    placeholder="所选合同缴费记录汇总（不含本条）" />
        </el-form-item>
        <el-form-item label="待缴纳金额">
          <el-input :model-value="summary ? summary.remainAmount : ''" disabled
                    placeholder="合同金额 - 已缴金额汇总" />
        </el-form-item>
        <el-form-item label="收款凭证">
          <input type="file" accept=".pdf,.jpg,.jpeg,.png" @change="onVoucher" />
          <span v-if="form.voucherUrl" style="color:#155EEF;font-size:12px">已上传</span>
        </el-form-item>
        <el-form-item label="实缴金额" required>
          <el-input-number v-model="form.amount" :min="0" :precision="2"
                           :max="summary ? Number(summary.remainAmount) : Infinity" />
          <div v-if="summary" style="color:#909399;font-size:12px;line-height:1.6;width:100%">
            不能超过待缴纳金额，本次最多可缴 {{ summary.remainAmount }} 元
          </div>
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="form.payType" style="width: 100%">
            <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="经办人">
          <el-select v-model="form.operator" filterable clearable style="width: 100%"
                     placeholder="默认当前登录人">
            <el-option v-for="u in staffUsers" :key="u.id" :label="u.realName" :value="u.realName" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="4" maxlength="200" show-word-limit
                    placeholder="可输入200字以内的备注信息" />
        </el-form-item>
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
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const tab = ref('pay')
const query = reactive({ studentId: null, contractNo: '' })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.paymentPage(q), query)

/* ===== 操作记录TAB：只读留痕，独立分页/筛选 ===== */
const logQuery = reactive({ action: null, studentId: null, paymentNo: '', operator: '' })
const logDateRange = ref(null)
const { loading: logLoading, rows: logRows, pager: logPager, seq: logSeq,
  onSort: onLogSort, onSizeChange: onLogSizeChange, search: logSearch, load: logLoad } =
  usePage((q) => api.paymentLogPage(q), logQuery)
function onLogDate(v) {
  logQuery.begin = v ? v[0] : undefined
  logQuery.end = v ? v[1] : undefined
  logSearch()
}
/** 切到操作记录TAB时刷新（进入即取最新留痕） */
function onTab(t) { if (t === 'log') logSearch() }

const students = ref([])
const courses = ref([])
// 全量合同缓存（合同管理取数）：下拉选项、详情/导出解析编号与金额共用
const allContracts = ref([])
// 编辑回显：原合同已非待缴费时钉进选项，避免锁定下拉显示裸ID
const pinnedContract = ref(null)
// 经办人候选：用户管理中的员工类型用户（教务/班主任/校长/咨询师/学管师）
const staffUsers = ref([])
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const findContract = (id) => allContracts.value.find((x) => x.id === id) || (pinnedContract.value?.id === id ? pinnedContract.value : null)
const contractNo = (id) => findContract(id)?.contractNo || '-'
/** 合同金额：从合同记录取合同总价（total_amount） */
const contractAmount = (id) => {
  const c = findContract(id)
  return c && c.totalAmount != null ? c.totalAmount : '-'
}
/** 当前表单选中的合同（带出合同金额展示） */
const selectedContract = computed(() => findContract(form.value.contractId))
/**
 * 关联合同可选项：合同管理中尚未缴清的合同（待缴费/部分缴费）；
 * 已缴清/退费/到期的不可再缴。已选学生则收窄到该学生，
 * 未选学生展示全部（选合同自动带出学生）。
 */
const contractOptions = computed(() => {
  let list = allContracts.value.filter((c) => (c.status === 0 || c.status === 1) && c.deleted === 0)
  if (form.value.studentId) list = list.filter((c) => c.studentId === form.value.studentId)
  if (pinnedContract.value && !list.some((c) => c.id === pinnedContract.value.id))
    list = [pinnedContract.value, ...list]
  return list
})
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

/** 合同下拉展示：编号 + 学生 + 应收/已缴/待缴（支持按编号/姓名搜索） */
function contractLabel(c) {
  const total = Number(c.totalAmount || 0)
  const paid = Number(c.paidAmount || 0)
  return `${c.contractNo} · ${studentName(c.studentId)} · 应收${total}/已缴${paid}/待缴${Math.max(0, total - paid)}`
}

const allColumns = [
  { prop: 'paymentNo', label: '缴费单号' }, { prop: 'studentId', label: '学生' },
  { prop: 'courseId', label: '课程' }, { prop: 'amount', label: '实缴金额' },
  { prop: 'contractAmount', label: '合同金额' }, { prop: 'payType', label: '支付方式' },
  { prop: 'operator', label: '经办人' }, { prop: 'updateBy', label: '操作人' },
  { prop: 'payTime', label: '缴费时间' }
]
const cols = reactive({ paymentNo: true, studentId: true, courseId: true, amount: true,
  contractAmount: true, payType: true, operator: true, updateBy: true, payTime: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const saving = ref(false)
const form = ref({})
/** 所选合同的缴费汇总：已缴=缴费记录求和（修改时排除本条），待缴=合同金额-已缴 */
const summary = ref(null)
async function loadSummary() {
  summary.value = null
  const id = form.value?.contractId
  if (!id) return
  summary.value = await api.paymentContractSummary(
    { contractId: id, excludePaymentId: form.value.id || undefined })
}
watch(() => form.value?.contractId, loadSummary)

/** 刷新合同缓存（合同管理全量）：打开表单时取最新状态，避免刚缴完的合同仍出现在待缴费里 */
async function refreshContracts() {
  const d = await api.contractPage({ page: 1, size: 500 })
  allContracts.value = (d.records || []).filter((c) => c.deleted === 0)
}

/** 学生切换：已选合同不属于该学生时清空（选项列表由 contractOptions 自动收窄） */
function onStudentChange() {
  const keep = contractOptions.value.some((c) => c.id === form.value.contractId)
  if (!keep) form.value.contractId = null
}

/** 选定合同：学生未选时自动带出合同所属学生 */
function onContractChange(id) {
  const c = findContract(id)
  if (c && !form.value.studentId) form.value.studentId = c.studentId
}

function openCreate() {
  form.value = { payType: 2, discountAmount: 0 }
  pinnedContract.value = null
  refreshContracts()
  visible.value = true
}

/** 修改：学员/合同锁定，金额/支付方式/经办人/备注可改 */
async function openEdit(row) {
  form.value = { ...row }
  pinnedContract.value = null
  await refreshContracts()
  // 原合同已非待缴费时钉进选项回显
  if (row.contractId && !contractOptions.value.some((c) => c.id === row.contractId))
    pinnedContract.value = findContract(row.contractId) || null
  // 同合同不同记录连续编辑时 watch 不触发，显式刷新汇总口径
  loadSummary()
  visible.value = true
}

async function save() {
  const f = form.value
  if (!f.studentId || !f.contractId || !f.amount) return ElMessage.warning('请完善缴费信息')
  // 汇总未就绪（打开表单就快速保存）先补拉一次，后端仍会强校验
  if (!summary.value) await loadSummary()
  // 实缴金额不能超过待缴纳金额（合同金额-已缴汇总，修改时不含本条）
  if (summary.value && Number(f.amount) > Number(summary.value.remainAmount)) {
    const { totalAmount, paidAmount, remainAmount } = summary.value
    return ElMessage.warning(
      `实缴金额不能超过待缴纳金额 ${remainAmount} 元（合同总额 ${totalAmount}，已缴 ${paidAmount}）`)
  }
  saving.value = true
  try {
    const c = findContract(f.contractId)
    f.courseId = c?.courseId
    if (f.id) await api.paymentUpdate(f)
    else await api.paymentSave(f)
    ElMessage.success(f.id ? '修改成功' : '缴费成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/** 删除缴费：流水删除并留痕，合同已缴/状态按剩余流水自动重算 */
async function remove(row) {
  await api.paymentDel(row.id)
  ElMessage.success('删除成功')
  load()
}

function onVoucher(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  // 凭证上传：通用上传接口，回填访问地址
  api.upload(fd).then((r) => {
    form.value.voucherUrl = r.url
    ElMessage.success('凭证已上传')
  })
}

/* ===== 缴费详情 ===== */
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
  { prop: 'paymentNo', label: '缴费单号' },
  { prop: 'studentId', label: '学生', formatter: (v) => studentName(v) },
  { prop: 'courseId', label: '课程', formatter: (v) => courseName(v) },
  { prop: 'contractId', label: '关联合同', formatter: (v) => contractNo(v) },
  { prop: 'amount', label: '实缴金额' },
  { prop: 'contractId', label: '合同金额', formatter: (v) => contractAmount(v) },
  { prop: 'payType', label: '支付方式', formatter: (v) => dictStore.label('pay_channel', v) },
  { prop: 'operator', label: '经办人' },
  { prop: 'updateBy', label: '操作人' },
  { prop: 'payTime', label: '缴费时间', formatter: (v) => fmt(v) },
  { prop: 'remark', label: '备注' }
]
function openDetail(row) {
  detailRow.value = { ...row }
  detailVisible.value = true
}

function doExport() {
  exportXlsx('缴费流水', [
      { label: '缴费单号', prop: 'paymentNo' },
      { label: '学生', prop: 'studentId', fmt: (v) => studentName(v) },
      { label: '实缴金额', prop: 'amount' },
      { label: '合同金额', prop: 'contractId', fmt: (v) => contractAmount(v) },
      { label: '支付方式', prop: 'payType', fmt: (v) => dictStore.label('pay_channel', v) },
      { label: '经办人', prop: 'operator' }, { label: '操作人', prop: 'updateBy' },
      { label: '缴费时间', prop: 'payTime' }
    ], rows.value)
}
onMounted(async () => {
  load()
  dictStore.load(true)
  students.value = (await api.studentList()) || []
  courses.value = (await api.courseList()) || []
  // 员工类型用户（教务/班主任/校长/咨询师/学管师），启用状态
  const d = await api.userPage({ page: 1, size: 500, userTypes: '2,3,6,7,8', status: 1 })
  staffUsers.value = d.records || []
  // 合同缓存：列表合同金额列/详情解析用
  refreshContracts()
})
</script>
