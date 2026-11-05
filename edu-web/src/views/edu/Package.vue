<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">课时套餐库</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增套餐</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="套餐名称" clearable style="width: 190px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.category" placeholder="科目" clearable style="width: 110px">
          <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-select v-model="query.teachMode" placeholder="授课模式" clearable style="width: 120px">
          <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
        <el-button @click="doExport">导出</el-button>
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }" :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column prop="name" label="套餐名称" min-width="160" sortable="custom" show-overflow-tooltip />
        <el-table-column label="科目" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ row.category ? dictText('course_category', row.category) : '-' }}</template>
        </el-table-column>
        <el-table-column label="授课模式" width="88" show-overflow-tooltip>
          <template #default="{ row }">{{ row.teachMode ? dictText('class_type', row.teachMode) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="grade" label="适用年级" width="86" show-overflow-tooltip>
          <template #default="{ row }">{{ row.grade || '不限' }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价(元/课时)" width="125" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="hours" label="购买课时" width="95" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="giftHours" label="赠送课时" width="95" show-overflow-tooltip />
        <el-table-column label="套餐总课时" width="105" show-overflow-tooltip>
          <template #default="{ row }">{{ Number(row.hours) + Number(row.giftHours || 0) }}</template>
        </el-table-column>
        <el-table-column prop="validDays" label="有效期(天)" width="105" show-overflow-tooltip />
        <el-table-column prop="startDate" label="生效日期" width="110" show-overflow-tooltip />
        <el-table-column prop="endDate" label="截止日期" width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="80" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="isExpired(row)" size="small" type="danger" effect="plain">已过期</el-tag>
            <el-tag v-else size="small" :type="row.enabled === 1 ? 'success' : 'info'" effect="plain">
              {{ row.enabled === 1 ? '启用' : '停用' }}
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
                    <span class="mi-txt">
                      <b>查看详情</b>
                      <i>套餐完整信息</i>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="edit" class="more-item">
                    <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                    <span class="mi-txt">
                      <b>编辑</b>
                      <i>修改套餐信息</i>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="ref" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Tickets /></el-icon></span>
                    <span class="mi-txt">
                      <b>合同引用</b>
                      <i>查看引用的合同</i>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="del" divided class="more-item">
                    <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                    <span class="mi-txt">
                      <b>删除</b>
                      <i>被引用时不可删</i>
                    </span>
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

    <el-drawer v-model="visible" :title="form.id ? '编辑套餐' : '新增套餐'" size="460px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="套餐名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="科目" required>
          <el-select v-model="form.category" style="width: 100%" placeholder="选择科目">
            <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="授课模式">
          <el-select v-model="form.teachMode" clearable style="width: 100%" placeholder="选择授课模式">
            <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用年级">
          <el-select v-model="form.grade" clearable filterable style="width: 100%" placeholder="不限">
            <el-option v-for="d in dictStore.options('grade')" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="单课时售价" required><el-input-number v-model="form.unitPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="套餐总价"><el-input :model-value="packageTotal" disabled /></el-form-item>
        <el-form-item label="购买课时" required><el-input-number v-model="form.hours" :min="1" :precision="0" :step="1" /></el-form-item>
        <el-form-item label="赠送课时"><el-input-number v-model="form.giftHours" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item label="套餐总课时"><el-input :model-value="totalHours" disabled /></el-form-item>
        <el-form-item label="有效期(天)" required><el-input-number v-model="form.validDays" :min="1" /></el-form-item>
        <el-form-item label="生效日期" required>
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-input :model-value="endDate" disabled placeholder="由生效日期+有效期自动计算" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用"
                     :disabled="formExpired" @change="onEnabledChange" />
          <span v-if="formExpired" class="u-tip">截止日期已过，不能启用</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 合同引用抽屉：查看引用该套餐的全部合同 -->
    <el-drawer v-model="refVisible" :title="`合同引用 — ${refRow.name || ''}`" size="720px">
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }" :data="refContracts" v-loading="refLoading" stripe size="small">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="contractNo" label="合同编号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="studentName" label="学员" width="90" show-overflow-tooltip />
        <el-table-column prop="purchasedHours" label="购买课时" width="90" show-overflow-tooltip />
        <el-table-column prop="giftHours" label="赠送课时" width="90" show-overflow-tooltip />
        <el-table-column prop="unitPrice" label="单价(元)" width="90" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="合同金额" width="100" show-overflow-tooltip />
        <el-table-column prop="startDate" label="生效日期" width="105" show-overflow-tooltip />
        <el-table-column prop="endDate" label="截止日期" width="105" show-overflow-tooltip />
        <el-table-column label="状态" width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'warning', 'success', 'danger', 'danger'][row.status]" effect="plain">
              {{ ['待缴费', '部分缴费', '已结清', '已退费', '已到期'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 套餐详情抽屉：完整套餐信息（更多-查看详情） -->
    <DetailDrawer v-model="detailVisible" title="套餐详情" :fields="detailFields" :data="detailRow" />
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import { exportXlsx } from '../../utils/export'
import { useDictStore } from '../../store/dict'
import DetailDrawer from '../../components/DetailDrawer.vue'

const dictStore = useDictStore()
const dictText = (type, v) => dictStore.label(type, v)
const query = reactive({ keyword: '', category: null, teachMode: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.packagePage(q), query)

const totalHours = computed(() =>
  Number(form.value.hours || 0) + Number(form.value.giftHours || 0))

/** 套餐总价 = 购买课时 × 单课时售价 */
const packageTotal = computed(() =>
  (Number(form.value.hours || 0) * Number(form.value.unitPrice || 0)).toFixed(2))

const visible = ref(false)
const saving = ref(false)
const form = ref({})

const todayStr = new Date().toISOString().slice(0, 10)

/** 截止日期 = 生效日期 + 有效期(天)，自动计算不可修改 */
const endDate = computed(() => {
  if (!form.value.startDate || !form.value.validDays) return ''
  const d = new Date(form.value.startDate)
  d.setDate(d.getDate() + Number(form.value.validDays))
  return d.toISOString().slice(0, 10)
})

// 以展示中的截止日期（生效日期+有效期自动计算）判断是否过期
const formExpired = computed(() => !!endDate.value && endDate.value < todayStr)

// 截止日期已过 -> 状态直接改为已下线
watch(formExpired, (v) => {
  if (v && form.value.id) form.value.enabled = 0
})

/** 开启已过期的套餐：弹窗提示并回弹为下线 */
function onEnabledChange(v) {
  if (v === 1 && formExpired.value) {
    form.value.enabled = 0
    ElMessageBox.alert('截止日期已过，不能启用', '提示', {
      type: 'warning', confirmButtonText: '知道了'
    }).catch(() => {})
  }
}

function isExpired(row) {
  return !!row.endDate && row.endDate < todayStr
}

function doExport() {
  exportXlsx('课时套餐', [
    { label: '套餐名称', prop: 'name' },
    { label: '科目', prop: 'category', fmt: (v) => (v ? dictText('course_category', v) : '-') },
    { label: '授课模式', prop: 'teachMode', fmt: (v) => (v ? dictText('class_type', v) : '-') },
    { label: '适用年级', prop: 'grade', fmt: (v) => v || '不限' },
    { label: '单价', prop: 'unitPrice' },
    { label: '购买课时', prop: 'hours' }, { label: '赠送课时', prop: 'giftHours' },
    { label: '有效期(天)', prop: 'validDays' }
  ], rows.value)
}

const refVisible = ref(false)
const refContracts = ref([])
const refLoading = ref(false)
const refRow = ref({})
/* ===== 查看详情抽屉 ===== */
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
  { prop: 'name', label: '套餐名称' },
  { prop: 'category', label: '科目', formatter: (v) => (v ? dictText('course_category', v) : '-') },
  { prop: 'teachMode', label: '授课模式', formatter: (v) => (v ? dictText('class_type', v) : '-') },
  { prop: 'grade', label: '适用年级', formatter: (v) => v || '不限' },
  { prop: 'unitPrice', label: '单价(元/课时)', formatter: (v) => Number(v || 0).toFixed(2) },
  { prop: 'hours', label: '购买课时' },
  { prop: 'giftHours', label: '赠送课时' },
  { prop: '__total', label: '套餐总课时' },
  { prop: 'validDays', label: '有效期(天)' },
  { prop: 'startDate', label: '生效日期' },
  { prop: 'endDate', label: '截止日期' },
  { prop: '__state', label: '状态' }
]
function onRowCommand(cmd, row) {
  if (cmd === 'detail') openDetail(row)
  else if (cmd === 'edit') openEdit(row)
  else if (cmd === 'ref') openContractRef(row)
  else if (cmd === 'del') remove(row)
}
/** 详情展示字段（套餐总课时/状态在打开时预计算，DetailDrawer 只做单值渲染） */
function openDetail(row) {
  detailRow.value = {
    ...row,
    __total: Number(row.hours || 0) + Number(row.giftHours || 0),
    __state: isExpired(row) ? '已过期' : (row.enabled === 1 ? '启用' : '停用')
  }
  detailVisible.value = true
}

async function openContractRef(row) {
  refRow.value = row
  refVisible.value = true
  refLoading.value = true
  try {
    refContracts.value = (await api.packageContracts(row.id)) || []
  } finally { refLoading.value = false }
}
function openEdit(row) {
  if (row) {
    form.value = { ...row }
    // 截止日期已过的套餐强制下线，不允许直接开启
    if (isExpired(row)) form.value.enabled = 0
  } else {
    form.value = {
      category: null, teachMode: null, grade: null,
      hours: 1, giftHours: 0, validDays: 365, enabled: 1, unitPrice: 0,
      startDate: new Date().toISOString().slice(0, 10)
    }
  }
  visible.value = true
}
async function save() {
  if (!form.value.name || !form.value.category) return ElMessage.warning('名称与科目为必填项')
  if (!form.value.startDate) return ElMessage.warning('请选择生效日期')
  if (Number(form.value.enabled) === 1 && formExpired.value) {
    ElMessage.error('截止日期已过，不能启用')
    return
  }
  form.value.endDate = endDate.value
  saving.value = true
  try {
    await api.packageSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.packageDel(id)
  ElMessage.success('已删除')
  load()
}
onMounted(async () => {
  load()
  dictStore.load(true)
})
</script>

<style scoped>
/* "更多"触发器：胶囊式，展开时主色点亮 + 箭头旋转 */
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
.more-trigger.open { border-color: rgba(91, 141, 239, 0.5); }
.more-arrow { transition: transform 0.2s ease; }
.more-trigger.open .more-arrow { transform: rotate(180deg); }
</style>
