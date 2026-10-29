<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">合同管理</span>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新增合同</el-button>
        <el-button @click="doExport">导出台账</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="filters.studentId" placeholder="学员" clearable filterable style="width: 150px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="合同编号" clearable style="width: 170px" @keyup.enter="search" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px">
          <el-option v-for="(label, v) in statusText" :key="v" :label="label" :value="Number(v)" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="生效开始" end-placeholder="生效结束" style="width: 250px" />
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="contract" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.contractNo" prop="contractNo" label="合同编号" width="160" sortable="custom" />
        <el-table-column v-if="cols.studentId" label="学员" width="90">
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseId" label="课程" min-width="130">
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classType" label="班型" width="80">
          <template #default="{ row }">{{ dictText('class_type', row.classType) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.purchasedHours" prop="purchasedHours" label="购买课时" width="95" sortable="custom" />
        <el-table-column v-if="cols.giftHours" prop="giftHours" label="赠送课时" width="95" />
        <el-table-column label="总/已消/剩余" width="130">
          <template #default="{ row }">
            {{ remain(row).total }} / {{ Number(row.usedPurchased) + Number(row.usedGift) }} /
            <span :style="remain(row).left <= warnHours ? 'color:#f56c6c;font-weight:600' : ''">{{ remain(row).left }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.unitPrice" prop="unitPrice" label="单价(元)" width="90" sortable="custom" />
        <el-table-column v-if="cols.totalAmount" label="应收/已缴" width="130" sortable="custom" prop="totalAmount">
          <template #default="{ row }">{{ row.totalAmount }} / {{ row.paidAmount }}</template>
        </el-table-column>
        <el-table-column v-if="cols.endDate" prop="endDate" label="到期日期" width="105" sortable="custom" />
        <el-table-column v-if="cols.status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'warning', 'success', 'danger', 'danger'][row.status]" effect="plain">
              {{ statusText[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 3" link type="warning" @click="openRefund(row)">退费</el-button>
            <el-popconfirm title="确认删除该合同?" @confirm="remove(row.id)">
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

    <!-- 新增合同抽屉：选套餐回填，可一客一价 -->
    <el-drawer v-model="createVisible" title="新增合同" size="680px">
      <el-form class="contract-form" :model="form" label-width="110px">
        <el-form-item label="选择学员" required>
          <el-select v-model="form.studentId" filterable style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐模板">
          <el-select v-model="form.packageId" clearable filterable style="width: 100%"
                     placeholder="选择套餐模板（已过期套餐不可选）" @change="onPackage">
            <el-option v-for="p in selectablePackages" :key="p.id"
                       :label="`${p.name}（${p.hours}+${p.giftHours}课时）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联课程" required>
          <el-select v-model="form.courseId" disabled style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班型">
          <el-select v-model="form.classType" disabled style="width: 100%">
            <el-option v-for="d in classTypes" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="购买课时" required>
          <el-input-number v-model="form.purchasedHours" :min="0" :precision="0" disabled style="width: 100%" />
        </el-form-item>
        <el-form-item label="赠送课时">
          <el-input-number v-model="form.giftHours" :min="0" :precision="0" disabled style="width: 100%" />
        </el-form-item>
        <el-form-item label="总课时">
          <el-input :model-value="totalCourseHours" disabled />
        </el-form-item>
        <el-form-item label="已消课时">
          <el-input :model-value="usedHoursDisplay" disabled />
        </el-form-item>
        <el-form-item label="剩余课时">
          <el-input :model-value="remainHoursDisplay" disabled />
        </el-form-item>
        <el-form-item label="套餐单价">
          <el-input :model-value="form.packageUnitPrice ?? ''" disabled />
        </el-form-item>
        <el-form-item label="套餐总价">
          <el-input :model-value="packageTotalPrice" disabled />
        </el-form-item>
        <el-form-item label="合同单价" required>
          <el-input-number v-model="form.unitPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="合同总价">
          <el-input :model-value="totalAmount" disabled />
        </el-form-item>
        <el-form-item v-if="Number(form.packageId)" label="利润占比">
          <span :style="{ color: profitInfo.color, fontWeight: 600 }">{{ profitInfo.text }}</span>
        </el-form-item>
        <el-form-item label="合同生效日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="有效期(天)"><el-input-number v-model="form.validDays" :min="1" /></el-form-item>
        <el-form-item label="合同到期日期">
          <el-input :model-value="contractEndDate" disabled placeholder="由生效日期+有效期自动计算" />
        </el-form-item>
        <el-form-item label="合同状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="d in dictStore.options('contract_status')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="咨询顾问"><el-input v-model="form.advisor" placeholder="业绩归属顾问" /></el-form-item>
        <el-form-item label="创建人">
          <el-input :model-value="form.createBy || currentUserName" disabled />
        </el-form-item>
        <el-form-item label="备注" class="full"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-form-item label="合同附件" class="full">
          <el-upload :show-file-list="false" :http-request="uploadAttachment"
                     accept=".pdf,.jpg,.jpeg,.png" class="att-uploader">
            <el-button size="small"><el-icon style="margin-right:4px"><Upload /></el-icon>选择文件</el-button>
          </el-upload>
          <span v-if="form.attachmentUrl" class="att-ok">已上传</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 合同详情抽屉 -->
    <el-drawer v-model="detailVisible" title="合同详情" size="560px">
      <el-descriptions :column="2" border v-if="detail.contract">
        <el-descriptions-item label="合同编号">{{ detail.contract.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="学员">{{ studentName(detail.contract.studentId) }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ courseName(detail.contract.courseId) }}</el-descriptions-item>
        <el-descriptions-item label="班型">{{ dictText('class_type', detail.contract.classType) }}</el-descriptions-item>
        <el-descriptions-item label="购买/赠送课时">{{ detail.contract.purchasedHours }} / {{ detail.contract.giftHours }}</el-descriptions-item>
        <el-descriptions-item label="已消课时">{{ Number(detail.contract.usedPurchased) + Number(detail.contract.usedGift) }}</el-descriptions-item>
        <el-descriptions-item label="剩余课时">{{ remainHoursOf(detail.contract) }}</el-descriptions-item>
        <el-descriptions-item label="套餐单价">{{ detail.packageUnitPrice ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="套餐总价">{{ detail.packageTotalPrice ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同单价">{{ detail.contract.unitPrice }}</el-descriptions-item>
        <el-descriptions-item label="合同总价">{{ detail.contract.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="咨询顾问">{{ detail.contract.advisor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.contract.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ detail.contract.startDate }}</el-descriptions-item>
        <el-descriptions-item label="到期日期">{{ detail.contract.endDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText[detail.contract.status] }}</el-descriptions-item>
        <el-descriptions-item label="附件">
          <a v-if="detail.contract.attachmentUrl" :href="detail.contract.attachmentUrl" target="_blank">查看附件</a>
          <span v-else>无</span>
        </el-descriptions-item>
      </el-descriptions>
      <div class="panel-title">缴费记录</div>
      <el-table :data="detail.payments || []" size="small" stripe>
        <el-table-column prop="paymentNo" label="缴费单号" min-width="150" />
        <el-table-column prop="amount" label="金额" width="90" />
        <el-table-column prop="payTime" label="时间" width="150" />
      </el-table>
    </el-drawer>

    <!-- 退费抽屉 -->
    <el-drawer v-model="refundVisible" title="合同退费" size="400px">
      <el-form label-width="100px">
        <el-form-item label="退费课时" required><el-input-number v-model="refundForm.refundHours" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="退费金额" required><el-input-number v-model="refundForm.refundAmount" :min="0" :precision="2" /></el-form-item>
        <el-alert title="退费后合同剩余课时冻结，不允许再消课" type="warning" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRefund">确认退费</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import { useDictStore } from '../../store/dict'
import { useUserStore } from '../../store/user'

const statusText = { 0: '待缴费', 1: '部分缴费', 2: '已结清', 3: '已退费', 4: '已到期' }
const dictStore = useDictStore()
const userStore = useUserStore()

/** 合同到期日期 = 生效日期 + 有效期(天)，自动计算不可修改 */
const contractEndDate = computed(() => {
  if (!form.value.startDate || !form.value.validDays) return ''
  const d = new Date(form.value.startDate)
  d.setDate(d.getDate() + Number(form.value.validDays))
  return d.toISOString().slice(0, 10)
})

/** 学员历史已消课时（跨全部合同汇总） */
const usedHoursDisplay = ref('-')
const currentUserName = computed(() => userStore.user?.realName || '')
const classTypes = computed(() => dictStore.options('class_type'))
const warnHours = 3 // 课时预警阈值（剩余<=3标红）

const dateRange = ref(null)
const filters = reactive({ studentId: null, keyword: '', status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.contractPage({
    ...q, begin: dateRange.value?.[0], end: dateRange.value?.[1]
  }), filters)

const students = ref([])
const courses = ref([])
const packages = ref([])
/** 可选套餐：启用中且截止日期未过（已过期套餐选择不到） */
const selectablePackages = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return packages.value.filter((p) => p.enabled === 1 && (!p.endDate || p.endDate >= today))
})
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
/** 剩余课时 = 购买+赠送 − 已消 */
function remainHoursOf(c) {
  const total = Number(c.purchasedHours || 0) + Number(c.giftHours || 0)
  const used = Number(c.usedPurchased || 0) + Number(c.usedGift || 0)
  return total - used
}
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const dictText = (t, v) => dictStore.label(t, v)
const remain = (row) => {
  const total = Number(row.purchasedHours) + Number(row.giftHours)
  const used = Number(row.usedPurchased) + Number(row.usedGift)
  return { total, left: total - used }
}

const allColumns = [
  { prop: 'contractNo', label: '合同编号' }, { prop: 'studentId', label: '学员' },
  { prop: 'courseId', label: '课程' }, { prop: 'classType', label: '班型' },
  { prop: 'purchasedHours', label: '购买课时' }, { prop: 'giftHours', label: '赠送课时' },
  { prop: 'unitPrice', label: '单价' }, { prop: 'totalAmount', label: '应收/已缴' },
  { prop: 'endDate', label: '到期日期' }, { prop: 'status', label: '状态' }
]
const cols = reactive({ contractNo: true, studentId: true, courseId: true, classType: true,
  purchasedHours: true, giftHours: true, unitPrice: true, totalAmount: true, endDate: true, status: true })
const onCols = (c) => Object.assign(cols, c)

function doExport() {
  exportXlsx('合同台账', [
    { label: '合同编号', prop: 'contractNo' }, { label: '学员', prop: 'studentId', fmt: (v) => studentName(v) },
    { label: '购买课时', prop: 'purchasedHours' }, { label: '赠送课时', prop: 'giftHours' },
    { label: '单价', prop: 'unitPrice' }, { label: '总金额', prop: 'totalAmount' },
    { label: '已缴', prop: 'paidAmount' }, { label: '状态', prop: 'status', fmt: (v) => statusText[v] }
  ], rows.value)
}

/* 新增合同 */
const createVisible = ref(false)
const saving = ref(false)
const form = ref({})
const totalAmount = computed(() =>
  (Number(form.value.purchasedHours || 0) * Number(form.value.unitPrice || 0)).toFixed(2))
/** 选中套餐（套餐总价从关联套餐获取） */
const selectedPackage = computed(() => packages.value.find((x) => x.id === Number(form.value.packageId)))

/** 总课时 = 所选套餐的 购买课时 + 赠送课时（随套餐带过来，只读） */
const totalCourseHours = computed(() => {
  const p = selectedPackage.value
  if (p) return Number(p.hours || 0) + Number(p.giftHours || 0)
  const ph = Number(form.value.purchasedHours || 0)
  const gh = Number(form.value.giftHours || 0)
  return (ph + gh) || ''
})
const packageTotalPrice = computed(() => {
  const p = selectedPackage.value
  if (!p) return ''
  return (Number(p.hours || 0) * Number(p.unitPrice || 0)).toFixed(2)
})

/** 利润占比：差额 = 套餐总价 - 合同总价（合同总价>套餐总价红色，<绿色，=黑色），只读不可修改 */
const profitInfo = computed(() => {
  const pt = Number(packageTotalPrice.value || 0)   // 套餐总价
  const ct = Number(totalAmount.value || 0)         // 合同总价
  if (!pt) return { text: '-', color: '#142a44' }
  const diff = pt - ct
  const ratio = pt > 0 ? ((diff / pt) * 100).toFixed(1) : '0.0'
  const text = `差额 ¥${diff.toFixed(2)}（利润占比 ${ratio}%）`
  const color = diff > 0 ? '#12b76a' : diff < 0 ? '#f04438' : '#142a44'
  return { text, color }
})

function onPackage(pid) {
  const p = packages.value.find((x) => x.id === pid)
  if (p) {
    form.value.courseId = p.courseId
    form.value.classType = p.classType
    form.value.purchasedHours = Number(p.hours)
    form.value.giftHours = Number(p.giftHours)
    form.value.packageUnitPrice = Number(p.unitPrice)
    form.value.unitPrice = Number(p.unitPrice)
    form.value.validDays = p.validDays
  }
}
function openCreate() {
  form.value = {
    giftHours: 0, validDays: 365, status: 0, classType: 1,
    startDate: new Date().toISOString().slice(0, 10),
    createBy: userStore.user?.realName || ''
  }
  usedHoursDisplay.value = '-'
  createVisible.value = true
}
/** 附件上传：格式/大小校验后走统一上传接口 */
async function uploadAttachment(file) {
  const okType = ['.pdf', '.jpg', '.jpeg', '.png'].some((x) => file.name.toLowerCase().endsWith(x))
  if (!okType) { ElMessage.error('仅支持 PDF、图片文件'); return false }
  if (file.size / 1024 / 1024 > 10) { ElMessage.error('文件不能超过 10M'); return false }
  const fd = new FormData()
  fd.append('file', file)
  const r = await api.upload(fd)
  form.value.attachmentUrl = r.url
  attachmentName.value = r.name
  ElMessage.success('附件已上传')
  return true
}
const attachmentName = ref('')
async function save() {
  const f = form.value
  if (!f.studentId || !f.courseId || !f.purchasedHours || !f.unitPrice)
    return ElMessage.warning('学员、课程、课时、售价为必填项')
  saving.value = true
  try {
    await api.contractSave(f)
    ElMessage.success('合同已创建（待缴费）')
    createVisible.value = false
    load()
  } finally { saving.value = false }
}

/* 详情 */
const detailVisible = ref(false)
const detail = ref({})
async function openDetail(row) {
  detail.value = (await api.contractDetail(row.id)) || {}
  detailVisible.value = true
}

/* 退费 */
const refundVisible = ref(false)
const refundForm = ref({})
function openRefund(row) {
  refundForm.value = { id: row.id, refundHours: remain(row).left, refundAmount: Number(row.totalAmount) - Number(row.paidAmount) }
  refundVisible.value = true
}
async function saveRefund() {
  await api.contractRefund(refundForm.value.id, {
    refundHours: refundForm.value.refundHours, refundAmount: refundForm.value.refundAmount
  })
  ElMessage.success('退费完成，剩余课时已冻结')
  refundVisible.value = false
  load()
}

async function remove(id) {
  await ElMessageBox.confirm('确认删除该合同？（逻辑删除，历史保留）', '提示', { type: 'warning' })
  await api.contractDel(id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  dictStore.load(true)
  students.value = (await api.studentList()) || []
  courses.value = (await api.courseList()) || []
  packages.value = (await api.packageList()) || []
})
</script>
