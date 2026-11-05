<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">课程管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增课程</el-button>
        <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="课程名称" clearable style="width: 180px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.category" placeholder="科目" clearable style="width: 110px">
          <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-select v-model="query.teachMode" placeholder="授课模式" clearable style="width: 120px">
          <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 100px">
          <el-option label="上架" :value="1" /><el-option label="下架" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="course" :columns="allColumns" @change="onCols" />
      </div>
      <el-table ref="courseTableRef" :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"
                :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort"
                @selection-change="sel = $event" @row-click="onRowClick" @expand-change="onExpandChange"
                @row-dblclick="openDetail">
        <el-table-column type="expand" width="30">
          <template #default="{ row }">
            <div class="ex-box">
              <div v-for="g in priceGroups(row)" :key="g.campus" class="ex-line">
                <span class="ex-campus">{{ g.campus }}</span>
                <span v-for="(p, i) in g.items" :key="i" class="ex-item">
                  <el-tag size="small" :type="p.chargeMode === 1 ? 'primary' : 'warning'" effect="plain">
                    {{ p.chargeMode === 1 ? '按课时' : '按时间' }}
                  </el-tag>
                  {{ p.chargeMode === 1 ? `${fmtNum(p.hours)}课时` : `1${p.timePeriod}` }}={{ fmtNum(p.totalAmount) }}元
                </span>
              </div>
              <div v-if="!priceGroups(row).length" class="ex-empty">暂无定价（未开启收费模式）</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="selection" width="42" />
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="课程名称" min-width="160" sortable="custom" show-overflow-tooltip />
        <el-table-column v-if="cols.category" prop="category" label="科目" width="78" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ dictStore.label('course_category', row.category) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teachMode" prop="teachMode" label="授课模式" width="88" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.teachMode ? dictStore.label('class_type', row.teachMode) : '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.campus" label="开课校区" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.campusMode === 2 ? (row.campus || '-') : '全部校区' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.grade" prop="grade" label="适用年级" width="86" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.grade || '不限' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.createTime" prop="createTime" label="创建时间" width="98" sortable="custom" align="center">
          <template #default="{ row }">{{ (row.createTime || '').slice(5, 10) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.updateTime" prop="updateTime" label="更新时间" width="98" sortable="custom" align="center">
          <template #default="{ row }">{{ (row.updateTime || '').slice(5, 10) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.status" label="状态" width="72">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-popconfirm title="确认删除该课程?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
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

    <!-- 新建/编辑/详情（同一表单，详情只读） -->
    <el-drawer v-model="visible" :title="drawerTitle" size="520px">
      <el-form :model="form" label-width="92px" :class="{ 'form-view': isView }" :disabled="isView">
        <div class="form-sec">基本信息</div>
        <el-form-item label="课程名称" required><el-input v-model="form.name" placeholder="如：数学五年级培优班" /></el-form-item>
        <el-form-item label="科目" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="授课模式">
          <el-select v-model="form.teachMode" clearable style="width: 100%" placeholder="选择授课模式（一对一/小班/大班等）">
            <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用年级">
          <el-select v-model="form.grade" clearable filterable style="width: 100%" placeholder="不限">
            <el-option v-for="d in dictStore.options('grade')" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>

        <div class="form-sec">收费模式</div>
        <!-- 校区模式：全部 / 指定（勾选） -->
        <el-form-item label="开课校区">
          <el-radio-group v-model="form.campusMode">
            <el-radio :value="1">全部校区</el-radio>
            <el-radio :value="2">指定校区</el-radio>
          </el-radio-group>
          <template v-if="form.campusMode === 2">
            <el-checkbox-group v-model="form.campusList" class="campus-checks">
              <el-checkbox v-for="c in campusChecks" :key="c" :value="c">{{ c }}</el-checkbox>
            </el-checkbox-group>
            <div v-if="!isView && !form.campusList?.length" class="desc-tip">勾选校区后自动生成定价行</div>
          </template>
        </el-form-item>
        <!-- 按课时收费 -->
        <el-form-item label="按课时收费">
          <el-switch v-model="form.chargeHour" :active-value="1" :inactive-value="0"
                     :disabled="isView" @change="(v) => onChargeSwitch(1, v)" />
        </el-form-item>
        <template v-if="form.chargeHour === 1">
          <table class="pt-table">
            <thead>
              <tr>
                <th class="pt-campus-th">开课校区</th>
                <th><span class="necessary">*</span> 数量（课时）</th>
                <th><span class="necessary">*</span> 总价（元）</th>
                <th class="pt-op-th">操作</th>
              </tr>
            </thead>
            <tbody v-for="cs in effectiveCampuses" :key="cs">
              <tr v-for="(r, idx) in rowsOf(cs, 1)" :key="cs + '-' + idx">
                <td v-if="idx === 0" :rowspan="rowsOf(cs, 1).length" class="pt-campus">{{ cs }}</td>
                <td>
                  <span v-if="isView" class="pv-text">{{ fmtNum(r.hours) }}</span>
                  <el-input-number v-else v-model="r.hours" :min="1" :max="9999.99" :precision="2" size="small"
                                   controls-position="right" style="width: 122px" placeholder="必填" />
                </td>
                <td>
                  <span v-if="isView" class="pv-text">{{ fmtNum(r.totalAmount) }}</span>
                  <el-input-number v-else v-model="r.totalAmount" :min="0" :max="999999.99" :precision="2" size="small"
                                   controls-position="right" style="width: 122px" placeholder="必填" />
                </td>
                <td class="pt-ops">
                  <template v-if="!isView">
                    <el-icon class="pt-icon pt-del" title="删除本行" @click="delRow(r, 1)"><CircleClose /></el-icon>
                    <el-icon v-if="idx === rowsOf(cs, 1).length - 1 && rowsOf(cs, 1).length < 5"
                             class="pt-icon pt-add" title="本校区加一行" @click="addRow(cs, 1)"><CirclePlus /></el-icon>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="desc-tip price-tip">校区不可修改，随上方选择联动；每校区 1~5 行</div>
        </template>
        <!-- 按时间收费 -->
        <el-form-item label="按时间收费">
          <el-switch v-model="form.chargeTime" :active-value="1" :inactive-value="0"
                     @change="(v) => onChargeSwitch(2, v)" />
        </el-form-item>
        <template v-if="form.chargeTime === 1">
          <table class="pt-table">
            <thead>
              <tr>
                <th class="pt-campus-th">开课校区</th>
                <th class="pt-num-th">数量</th>
                <th><span class="necessary">*</span> 时间段</th>
                <th><span class="necessary">*</span> 总价（元）</th>
                <th class="pt-op-th">操作</th>
              </tr>
            </thead>
            <tbody v-for="cs in effectiveCampuses" :key="cs">
              <tr v-for="(r, idx) in rowsOf(cs, 2)" :key="cs + '-t-' + idx">
                <td v-if="idx === 0" :rowspan="rowsOf(cs, 2).length" class="pt-campus">{{ cs }}</td>
                <td class="pt-num">1</td>
                <td>
                  <span v-if="isView" class="pv-text">{{ r.timePeriod || '-' }}</span>
                  <el-select v-else v-model="r.timePeriod" size="small" style="width: 84px" placeholder="选择">
                    <el-option v-for="p in periodOptions(r)" :key="p" :label="p" :value="p" />
                  </el-select>
                </td>
                <td>
                  <span v-if="isView" class="pv-text">{{ fmtNum(r.totalAmount) }}</span>
                  <el-input-number v-else v-model="r.totalAmount" :min="0" :max="999999.99" :precision="2" size="small"
                                   controls-position="right" style="width: 122px" placeholder="必填" />
                </td>
                <td class="pt-ops">
                  <template v-if="!isView">
                    <el-icon class="pt-icon pt-del" title="删除本行" @click="delRow(r, 2)"><CircleClose /></el-icon>
                    <el-icon v-if="idx === rowsOf(cs, 2).length - 1 && firstUnusedPeriod(cs)"
                             class="pt-icon pt-add" title="本校区加一行" @click="addRow(cs, 2)"><CirclePlus /></el-icon>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="desc-tip price-tip">每种时间段仅一条，已选自动排除；数量固定为 1</div>
        </template>

        <div class="form-sec">其他</div>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
        <el-form-item label="课程介绍">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="课程特色、适用对象、教学目标等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ isView ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!isView" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, CirclePlus, CircleClose } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const query = reactive({ keyword: '', category: null, teachMode: null, status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.coursePage(q), query)

const allColumns = [
  { prop: 'name', label: '课程名称' }, { prop: 'category', label: '科目' },
  { prop: 'teachMode', label: '授课模式' },
  { prop: 'campus', label: '开课校区' }, { prop: 'grade', label: '适用年级' },
  { prop: 'createTime', label: '创建时间' },
  { prop: 'updateTime', label: '更新时间' }, { prop: 'status', label: '状态' }
]
const cols = reactive({ name: true, category: true, teachMode: true, campus: true,
  grade: true, createTime: true, updateTime: false, status: true })
const onCols = (c) => Object.assign(cols, c)

/* 教师（预留：详情/导出已去授课老师，字段不再使用） */

/* ===== 行展开：校区 × 收费模式定价 ===== */
const courseTableRef = ref(null)
const priceCache = reactive({})
/** 展开行时懒加载该课程定价 */
async function onExpandChange(row, expandedRows) {
  if (expandedRows.includes(row) && !priceCache[row.id]) {
    priceCache[row.id] = (await api.coursePrices(row.id)) || []
    // 选中行有展开列时刷新分组
  }
}
/** 行点击切换展开（勾选列/操作列点击不触发） */
function onRowClick(row, column) {
  if (column && (column.type === 'selection' || column.type === 'expand' || column.label === '操作')) return
  courseTableRef.value?.toggleRowExpansion(row)
}
/** 按校区分组定价；未配置的校区（如全部校区无行）不展示 */
function priceGroups(row) {
  const list = priceCache[row.id] || []
  const groups = []
  list.forEach((p) => {
    let g = groups.find((x) => x.campus === p.campus)
    if (!g) { g = { campus: p.campus, items: [] }; groups.push(g) }
    g.items.push(p)
  })
  return groups
}
/** 数字去尾零：48.00→48、48.50→48.5 */
const fmtNum = (n) => String(Number(n ?? 0))

/* 新建/编辑/详情（同一表单，详情只读） */
const visible = ref(false)
const saving = ref(false)
const form = ref({})
const viewMode = ref(false)
const isView = computed(() => viewMode.value)
const drawerTitle = computed(() => (isView.value ? '课程详情' : form.value.id ? '编辑课程' : '新建课程'))

/* ===== 收费模式：校区 / 定价行 ===== */
const campusChecks = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))
const PERIODS = ['天', '周', '月', '季', '年']
const hourPrices = ref([])   // [{campus, hours, totalAmount}]
const timePrices = ref([])   // [{campus, timePeriod, totalAmount}]
/** 生效校区：指定模式=勾选校区；全部模式=单条"全部校区" */
const effectiveCampuses = computed(() =>
  form.value.campusMode === 2 ? (form.value.campusList || []) : ['全部校区'])
const rowsOf = (campus, mode) =>
  (mode === 1 ? hourPrices.value : timePrices.value).filter((r) => r.campus === campus)
/** 校区/开关联动：删掉未选校区的行，为每个校区保底 1 行 */
function syncCampusRows() {
  const camps = effectiveCampuses.value
  hourPrices.value = hourPrices.value.filter((r) => camps.includes(r.campus))
  timePrices.value = timePrices.value.filter((r) => camps.includes(r.campus))
  camps.forEach((cs) => {
    if (!hourPrices.value.some((r) => r.campus === cs))
      hourPrices.value.push({ campus: cs, hours: null, totalAmount: null })
    if (!timePrices.value.some((r) => r.campus === cs))
      timePrices.value.push({ campus: cs, timePeriod: null, totalAmount: null })
  })
}
/** 收费开关：开启前必须先选好校区（指定模式未勾选校区时拦截） */
function onChargeSwitch(mode, v) {
  if (v === 1 && form.value.campusMode === 2 && !(form.value.campusList || []).length) {
    ElMessage.warning('请先选择校区')
    if (mode === 1) form.value.chargeHour = 0
    else form.value.chargeTime = 0
    return
  }
  syncCampusRows()
}
watch(() => [form.value.campusMode, (form.value.campusList || []).join(',')], syncCampusRows)
/** 模式2：校区内未占用的时间段 */
function firstUnusedPeriod(campus) {
  const used = new Set(rowsOf(campus, 2).map((r) => r.timePeriod).filter(Boolean))
  return PERIODS.find((p) => !used.has(p)) || null
}
function periodOptions(row) {
  const used = new Set(rowsOf(row.campus, 2).map((r) => r.timePeriod).filter((p) => p && p !== row.timePeriod))
  return PERIODS.filter((p) => !used.has(p))
}
function addRow(campus, mode) {
  if (mode === 1) {
    if (rowsOf(campus, 1).length >= 5) return ElMessage.warning('一个校区最多 5 行定价')
    hourPrices.value.push({ campus, hours: 20, totalAmount: null })
  } else {
    const p = firstUnusedPeriod(campus)
    if (!p) return ElMessage.warning('该校区所有时间段都已配置')
    timePrices.value.push({ campus, timePeriod: p, totalAmount: null })
  }
}
function delRow(row, mode) {
  const list = mode === 1 ? hourPrices.value : timePrices.value
  if (rowsOf(row.campus, mode).length <= 1) return ElMessage.warning('每个校区至少保留 1 行')
  list.splice(list.indexOf(row), 1)
}

async function openEdit(row) {
  viewMode.value = false
  if (row) {
    await loadIntoForm(row)
  } else {
    form.value = {
      teachMode: null, category: null, grade: null,
      campusMode: 1, campusList: [], chargeHour: 0, chargeTime: 0,
      status: 1, remark: ''
    }
    hourPrices.value = []
    timePrices.value = []
  }
  visible.value = true
}
/** 详情（双击行或点查看）：同一表单只读展示 */
async function openDetail(row) {
  viewMode.value = true
  await loadIntoForm(row)
  priceCache[row.id] = hourPrices.value.map((r, i) => ({ chargeMode: 1, ...r, sort: i }))
    .concat(timePrices.value.map((r, i) => ({ chargeMode: 2, ...r, sort: i })))
  visible.value = true
}
/** 行数据 + 定价载入表单 */
async function loadIntoForm(row) {
  form.value = { ...row, campusList: (row.campus || '').split(',').filter(Boolean) }
  const prices = (await api.coursePrices(row.id)) || []
  hourPrices.value = prices.filter((p) => p.chargeMode === 1)
    .map((p) => ({ campus: p.campus, hours: p.hours, totalAmount: Number(p.totalAmount) }))
  timePrices.value = prices.filter((p) => p.chargeMode === 2)
    .map((p) => ({ campus: p.campus, timePeriod: p.timePeriod, totalAmount: Number(p.totalAmount) }))
}
async function save() {
  const f = form.value
  if (!f.name) return ElMessage.warning('课程名称不能为空')
  if (!f.category) return ElMessage.warning('请选择科目')
  // 收费模式校验：开启即需选校区并填完整定价
  const charged = f.chargeHour === 1 || f.chargeTime === 1
  if (charged && f.campusMode === 2 && !(f.campusList || []).length)
    return ElMessage.warning('开启收费模式后请勾选开课校区')
  if (f.chargeHour === 1) {
    if (hourPrices.value.some((r) => !r.hours || !r.totalAmount))
      return ElMessage.warning('按课时定价存在未填写的数量或总价')
  }
  if (f.chargeTime === 1) {
    if (timePrices.value.some((r) => !r.timePeriod || !r.totalAmount))
      return ElMessage.warning('按时间定价存在未选择的时间段或未填写的总价')
  }
  // 组装定价标准
  const prices = []
  if (f.chargeHour === 1)
    hourPrices.value.forEach((r, i) => prices.push({ chargeMode: 1, campus: r.campus, hours: r.hours, totalAmount: r.totalAmount, sort: i }))
  if (f.chargeTime === 1)
    timePrices.value.forEach((r, i) => prices.push({ chargeMode: 2, campus: r.campus, hours: 1, timePeriod: r.timePeriod, totalAmount: r.totalAmount, sort: i }))
  const payload = { ...f, campus: f.campusMode === 2 ? (f.campusList || []).join(',') : '', prices }
  saving.value = true
  try {
    if (f.id) await api.courseUpdate(payload)
    else await api.courseSave(payload)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.courseDel(id)
  ElMessage.success('已删除')
  load()
}
async function toggleStatus(row) {
  await api.courseUpdate({ id: row.id, status: row.status === 1 ? 0 : 1 })
  ElMessage.success(row.status === 1 ? '已下架' : '已上架')
  load()
}

function doExport() {
  exportXlsx('课程列表', [
    { label: '课程名称', prop: 'name' },
    { label: '科目', prop: 'category', fmt: (v) => dictStore.label('course_category', v) },
    { label: '授课模式', prop: 'teachMode', fmt: (v) => (v ? dictStore.label('class_type', v) : '-') },
    { label: '开课校区', prop: 'campus', fmt: (v, row) => (row.campusMode === 2 ? v : '全部校区') },
    { label: '适用年级', prop: 'grade' },
    { label: '状态', prop: 'status', fmt: (v) => (v === 1 ? '上架' : '下架') }
  ], rows.value)
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条课程数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.courseDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

onMounted(async () => {
  load()
  dictStore.load(true)
})
</script>

<style scoped>
.form-sec {
  font-size: 13px; font-weight: 600; color: #344054;
  padding-left: 8px; border-left: 3px solid #155EEF;
  margin: 6px 0 14px;
}
.total-price { font-size: 18px; font-weight: 700; color: #155EEF; }
.desc-tip { font-size: 12px; color: #98a2b3; line-height: 1.4; margin-top: 2px; }
.campus-checks { width: 100%; margin-top: 6px; }
.campus-checks .el-checkbox { margin-right: 14px; }
/* 定价标准表格：校区列纵向合并，加减圆形图标操作 */
.pt-table {
  width: 100%; border-collapse: collapse; margin-bottom: 4px;
  font-size: 12.5px;
}
.pt-table th {
  background: #f5f8fc; color: #475467; font-weight: 600;
  border: 1px solid #e4edf7; padding: 7px 8px; text-align: left; white-space: nowrap;
}
.pt-table td { border: 1px solid #e4edf7; padding: 6px 8px; vertical-align: middle; }
.pt-campus { width: 84px; color: #344054; font-weight: 600; background: #fafcff; }
.pt-num { width: 46px; text-align: center; color: #667c99; }
.pt-ops { width: 64px; text-align: center; white-space: nowrap; }
.pt-icon { font-size: 17px; cursor: pointer; margin: 0 3px; vertical-align: middle; }
.pt-del { color: #f04438; }
.pt-del:hover { color: #d92d20; }
.pt-add { color: #12b76a; }
.pt-add:hover { color: #039855; }
.necessary { color: #f04438; margin-right: 2px; }
.price-tip { margin: 0 0 12px; }
/* 行展开：校区 × 定价 */
.ex-box { padding: 4px 12px 8px; }
.ex-line { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 14px; padding: 3px 0; }
.ex-campus { font-weight: 600; color: #155EEF; margin-right: 4px; }
.ex-item { font-size: 12.5px; color: #475467; white-space: nowrap; }
.ex-empty { color: #98a2b3; font-size: 12.5px; padding: 4px 0; }
/* 详情只读模式：表格内文本展示 */
.pv-text { font-size: 13px; color: #344054; }
.form-view :deep(.el-radio.is-disabled .el-radio__inner),
.form-view :deep(.el-checkbox.is-disabled .el-checkbox__inner) { cursor: default; }
</style>
