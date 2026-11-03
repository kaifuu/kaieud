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
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.contractNo" prop="contractNo" label="合同编号" width="160" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.studentId" label="学员" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseId" label="课程" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classType" label="班型" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ dictText('class_type', row.classType) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.purchasedHours" prop="purchasedHours" label="购买课时" width="95" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.giftHours" prop="giftHours" label="赠送课时" width="95"  show-overflow-tooltip/>
        <el-table-column label="总/已消/剩余" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ remain(row).total }} / {{ Number(row.usedPurchased) + Number(row.usedGift) }} /
            <span :style="remain(row).left <= warnHours ? 'color:#f56c6c;font-weight:600' : ''">{{ remain(row).left }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.unitPrice" prop="unitPrice" label="单价(元)" width="90" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.totalAmount" label="应收/已缴" width="130" sortable="custom" prop="totalAmount" show-overflow-tooltip>
          <template #default="{ row }">{{ row.totalAmount }} / {{ row.paidAmount }}</template>
        </el-table-column>
        <el-table-column v-if="cols.endDate" prop="endDate" label="到期日期" width="105" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.status" label="状态" width="90" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'warning', 'success', 'danger', 'danger'][row.status]" effect="plain">
              {{ statusText[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 3" link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <!-- 新增/修改合同抽屉：TAB 分组一屏展示（选套餐回填，可一客一价） -->
    <el-drawer v-model="createVisible" :title="editing ? '修改合同' : '新增合同'" size="680px">
      <el-form class="contract-form" :model="form" label-width="110px">
        <el-tabs v-model="createTab">
          <el-tab-pane label="基本信息" name="base">
            <el-form-item label="选择学员" required>
              <el-select v-model="form.studentId" filterable :disabled="editing" style="width: 100%">
                <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="套餐模板">
              <el-select v-model="form.packageId" clearable filterable :disabled="editing" style="width: 100%"
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
            <el-form-item label="咨询顾问">
              <el-select v-model="form.advisor" filterable allow-create clearable
                         default-first-option style="width: 100%" placeholder="选择用户管理中的账号（可输入搜索）">
                <el-option v-for="a in advisorCandidates" :key="a.username"
                           :label="`${a.name}（${typeName(a.userType)}）`" :value="a.name" />
              </el-select>
              <div class="desc-tip">候选来自本企业用户管理中非学生、非系统用户的在职账号</div>
            </el-form-item>
            <el-form-item label="合同状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="d in statusOptions" :key="d.value" :label="d.label" :value="Number(d.value)" />
              </el-select>
            </el-form-item>
            <el-form-item label="创建人">
              <el-input :model-value="form.createBy || currentUserName" disabled />
            </el-form-item>
          </el-tab-pane>
          <el-tab-pane label="课时信息" name="hours">
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
          </el-tab-pane>
          <el-tab-pane label="价格信息" name="price">
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
          </el-tab-pane>
          <el-tab-pane label="条款与附件" name="terms">
            <el-form-item label="合同生效日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
            <el-form-item label="有效期(天)"><el-input-number v-model="form.validDays" :min="1" /></el-form-item>
            <el-form-item label="合同到期日期">
              <el-input :model-value="contractEndDate" disabled placeholder="由生效日期+有效期自动计算" />
            </el-form-item>
            <el-form-item label="备注" class="full"><el-input v-model="form.remark" type="textarea" /></el-form-item>
            <el-form-item label="合同附件" class="full">
              <el-upload :show-file-list="false" :http-request="uploadAttachment"
                         accept=".pdf,.jpg,.jpeg,.png" class="att-uploader">
                <el-button size="small"><el-icon style="margin-right:4px"><Upload /></el-icon>选择文件</el-button>
              </el-upload>
              <span v-if="form.attachmentUrl" class="att-ok">已上传</span>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 合同详情抽屉：TAB 分组一屏展示 -->
    <el-drawer v-model="detailVisible" title="合同详情" size="620px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="合同信息" name="info">
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
        </el-tab-pane>
        <el-tab-pane label="缴费记录" name="pay">
          <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="detail.payments || []" size="small" stripe>
            <el-table-column prop="paymentNo" label="缴费单号" min-width="150"  show-overflow-tooltip/>
            <el-table-column prop="amount" label="金额" width="90"  show-overflow-tooltip/>
            <el-table-column prop="payTime" label="时间" width="150"  show-overflow-tooltip/>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 退费抽屉：单页一屏展示（描述 3 列压缩高度，课时/金额并排） -->
    <el-drawer v-model="refundVisible" title="合同退费" size="780px">
      <el-descriptions :column="3" border v-if="refundInfo.contract" class="refund-desc">
        <el-descriptions-item label="合同编号">{{ refundInfo.contract.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{ refundInfo.packageName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学员">{{ studentName(refundInfo.contract.studentId) }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ courseName(refundInfo.contract.courseId) }}</el-descriptions-item>
        <el-descriptions-item label="班型">{{ dictText('class_type', refundInfo.contract.classType) }}</el-descriptions-item>
        <el-descriptions-item label="购买/赠送课时">{{ refundInfo.contract.purchasedHours }} / {{ refundInfo.contract.giftHours }}</el-descriptions-item>
        <el-descriptions-item label="已消课时">{{ Number(refundInfo.contract.usedPurchased) + Number(refundInfo.contract.usedGift) }}</el-descriptions-item>
        <el-descriptions-item label="剩余课时">{{ refundInfo.remainHours }}</el-descriptions-item>
        <el-descriptions-item label="套餐单价">{{ refundInfo.packageUnitPrice ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="套餐总价">{{ refundInfo.packageTotalPrice ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同单价">{{ refundInfo.contract.unitPrice }}</el-descriptions-item>
        <el-descriptions-item label="合同总价">{{ refundInfo.contract.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="咨询顾问">{{ refundInfo.contract.advisor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="已上课时">{{ refundInfo.attendedHours ?? 0 }}<span class="desc-tip">（课时记录表）</span></el-descriptions-item>
        <el-descriptions-item label="已退课时">{{ refundInfo.refundedHours ?? 0 }}<span class="desc-tip">（退费表）</span></el-descriptions-item>
      </el-descriptions>
      <div class="panel-title">退费信息</div>
      <el-form label-width="90px" class="refund-form">
        <div class="refund-row">
          <el-form-item label="退费课时" required>
            <el-input-number v-model="refundForm.refundHours" :min="0" :max="Number(refundInfo.remainHours)" :precision="2" style="width: 100%" />
            <div class="desc-tip">不能大于剩余课时 {{ refundInfo.remainHours }}</div>
          </el-form-item>
          <el-form-item label="退费金额" required>
            <el-input-number v-model="refundForm.refundAmount" :min="0" :max="Number(refundInfo.refundCapAmount)" :precision="2" style="width: 100%" />
            <div class="desc-tip">不能大于{{ refundCapLabel }} ¥{{ refundInfo.refundCapAmount }}</div>
          </el-form-item>
        </div>
        <el-form-item label="退费方式">
          <el-radio-group v-model="refundForm.refundTo">
            <el-radio :value="1">退费至个人</el-radio>
            <el-radio :value="2">退费至账户</el-radio>
          </el-radio-group>
          <div class="desc-tip">退费至账户：退费金额将进入该学生的账户余额（学生中心-账户管理可查 金额/课时/合同/操作人/时间）；退费至个人：现金或原路退回学员</div>
        </el-form-item>
        <el-form-item label="退费原因"><el-input v-model="refundForm.reason" type="textarea" :rows="2" placeholder="选填，退费留痕" /></el-form-item>
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
import { reactive, ref, computed, watch, onMounted } from 'vue'
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

/** 学员历史已消课时（新增模式=学员汇总；修改模式=本合同已消） */
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

/* 新增/修改合同 */
const createVisible = ref(false)
const saving = ref(false)
const form = ref({})
/** 新增合同 TAB：基本信息/课时信息/价格信息/条款与附件 */
const createTab = ref('base')
/** 修改模式：学员/套餐/课时锁定，价格/有效期/状态/备注/附件可改 */
const editing = ref(false)
const editContract = ref(null)
/** 合同状态选项：修改时不可选"已退费"（须走退费流程） */
const statusOptions = computed(() => {
  const opts = dictStore.options('contract_status')
  return editing.value ? opts.filter((d) => Number(d.value) !== 3) : opts
})
/** 剩余课时：修改=本合同剩余；新增=新合同总课时（未消课） */
const remainHoursDisplay = computed(() => {
  if (editing.value) {
    const c = editContract.value || {}
    return (Number(c.purchasedHours || 0) + Number(c.giftHours || 0))
      - (Number(c.usedPurchased || 0) + Number(c.usedGift || 0))
  }
  return totalCourseHours.value || ''
})
/** 新增模式选学员后联动学员历史已消课时 */
watch(() => form.value.studentId, async (id) => {
  if (editing.value || !id) return
  const s = await api.contractStudentSummary(id)
  usedHoursDisplay.value = s?.usedHours ?? 0
})
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
  editing.value = false
  editContract.value = null
  form.value = {
    giftHours: 0, validDays: 365, status: 0, classType: 1,
    startDate: new Date().toISOString().slice(0, 10),
    createBy: userStore.user?.realName || ''
  }
  usedHoursDisplay.value = '-'
  createTab.value = 'base'
  createVisible.value = true
}

/** 修改合同：回填合同数据（有效期由到期日-生效日反算），学员/套餐/课时锁定 */
async function openEdit(row) {
  const d = (await api.contractDetail(row.id)) || {}
  const c = d.contract || row
  const validDays = c.startDate && c.endDate
    ? Math.round((new Date(c.endDate) - new Date(c.startDate)) / 86400000) : 365
  editing.value = true
  editContract.value = c
  form.value = {
    id: c.id, studentId: c.studentId, packageId: c.packageId, courseId: c.courseId,
    classType: c.classType, purchasedHours: Number(c.purchasedHours), giftHours: Number(c.giftHours),
    packageUnitPrice: d.packageUnitPrice != null ? Number(d.packageUnitPrice) : '',
    unitPrice: Number(c.unitPrice), startDate: c.startDate, validDays,
    status: c.status, advisor: c.advisor, remark: c.remark,
    attachmentUrl: c.attachmentUrl, createBy: c.createBy
  }
  usedHoursDisplay.value = Number(c.usedPurchased || 0) + Number(c.usedGift || 0)
  createTab.value = 'base'
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
  // 必填校验：缺失时自动切到对应 TAB，避免用户来回找字段
  if (!f.studentId || !f.courseId) {
    createTab.value = 'base'
    return ElMessage.warning('学员、课程为必填项')
  }
  if (!f.purchasedHours) {
    createTab.value = 'hours'
    return ElMessage.warning('请选择套餐带出购买课时')
  }
  if (!f.unitPrice) {
    createTab.value = 'price'
    return ElMessage.warning('请填写合同单价')
  }
  saving.value = true
  try {
    if (editing.value) {
      await api.contractUpdate(f)
      ElMessage.success('合同已修改')
    } else {
      await api.contractSave(f)
      ElMessage.success('合同已创建（待缴费）')
    }
    createVisible.value = false
    load()
  } finally { saving.value = false }
}

/* 详情 */
const detailVisible = ref(false)
const detail = ref({})
const detailTab = ref('info')
async function openDetail(row) {
  detail.value = (await api.contractDetail(row.id)) || {}
  detailTab.value = 'info'
  detailVisible.value = true
}

/* 退费 */
const refundVisible = ref(false)
const refundInfo = ref({})
const refundForm = ref({})
/** 退费金额上限说明：有套餐为套餐总价，无套餐兜底合同总价 */
const refundCapLabel = computed(() => (refundInfo.value.packageTotalPrice != null ? '套餐总价' : '合同总价'))
async function openRefund(row) {
  refundInfo.value = (await api.contractRefundInfo(row.id)) || {}
  const c = refundInfo.value.contract || {}
  const remainHours = Number(refundInfo.value.remainHours || 0)
  const cap = Number(refundInfo.value.refundCapAmount || 0)
  // 默认：退剩余课时，金额按剩余课时×合同单价（不超过上限）
  refundForm.value = {
    id: row.id,
    refundHours: remainHours,
    refundAmount: Math.min(remainHours * Number(c.unitPrice || 0), cap).toFixed(2),
    refundTo: 1,
    reason: ''
  }
  refundVisible.value = true
}
async function saveRefund() {
  const f = refundForm.value
  const remainHours = Number(refundInfo.value.remainHours || 0)
  const cap = Number(refundInfo.value.refundCapAmount || 0)
  if (f.refundHours == null || f.refundHours < 0) return ElMessage.warning('请填写退费课时')
  if (Number(f.refundHours) > remainHours + 0.001)
    return ElMessage.warning(`退费课时不能大于剩余课时 ${remainHours}`)
  if (f.refundAmount == null || f.refundAmount < 0) return ElMessage.warning('请填写退费金额')
  if (Number(f.refundAmount) > cap + 0.001)
    return ElMessage.warning(`退费金额不能大于${refundCapLabel.value} ¥${cap}`)
  saving.value = true
  try {
    await api.contractRefund(f.id, {
      refundHours: f.refundHours, refundAmount: f.refundAmount,
      refundTo: f.refundTo || 1, reason: f.reason
    })
    ElMessage.success(f.refundTo === 2 ? '退费完成，金额已入学生账户' : '退费完成，剩余课时已冻结')
    refundVisible.value = false
    load()
  } finally { saving.value = false }
}

async function remove(id) {
  await ElMessageBox.confirm('确认删除该合同？（逻辑删除，历史保留）', '提示', { type: 'warning' })
  await api.contractDel(id)
  ElMessage.success('已删除')
  load()
}

/* 咨询顾问候选：本企业非学生/非系统用户的在职账号 */
const advisorCandidates = ref([])
const USER_TYPE_NAMES = { 2: '教管人员', 3: '班主任', 4: '教师', 6: '校区校长', 7: '校区咨询师', 8: '校区学管师' }
const typeName = (t) => USER_TYPE_NAMES[t] || '员工'

onMounted(async () => {
  load()
  dictStore.load(true)
  students.value = (await api.studentList()) || []
  courses.value = (await api.courseList()) || []
  packages.value = (await api.packageList()) || []
  advisorCandidates.value = (await api.contractAdvisorCandidates()) || []
})
</script>

<style scoped>
/* 退费抽屉：说明小字（口径来源/上限提示） */
.desc-tip { color: #909399; font-size: 12px; }
/* 退费抽屉一屏布局：描述紧凑 + 课时/金额并排 */
.refund-desc { margin-bottom: 4px; }
.refund-desc :deep(.el-descriptions__label) { width: 96px; }
.refund-desc .desc-tip { margin-left: 4px; }
.refund-row { display: flex; gap: 24px; }
.refund-row .el-form-item { flex: 1; }
.refund-form :deep(.el-form-item) { margin-bottom: 12px; }
</style>
