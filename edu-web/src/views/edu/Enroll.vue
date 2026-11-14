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
          <el-option label="在读" :value="1" /><el-option label="已结课" :value="2" /><el-option label="已退订" :value="3" />
          <el-option label="已转课" :value="4" /><el-option label="已作废" :value="5" /><el-option label="停课" :value="6" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="enroll" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.studentId" label="学员姓名" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ studentName(row.studentId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.phone" label="联系电话" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ studentPhone(row.studentId) || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.courseId" label="报读课程" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teachMode" label="授课模式" width="86" align="center">
          <template #default="{ row }">
            {{ (() => { const c = courses.value.find((x) => x.id === row.courseId); return c?.teachMode ? dictStore.label('class_type', c.teachMode) : '-' })() }}
          </template>
        </el-table-column>
        <el-table-column v-if="cols.clazzId" label="班级" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.clazzId ? clazzName(row.clazzId) : '一对一' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.teacherId" label="授课老师" width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.teacherId" size="small" effect="light">{{ teacherName(row.teacherId) }}</el-tag>
            <el-tag v-else size="small" type="danger" effect="light">未指派</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.totalHours" label="购买数量" width="92" align="center">
          <template #default="{ row }">{{ row.totalHours ?? '-' }}{{ row.hoursUnit || '课时' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.usedHours" label="已用数量" width="92" align="center" sortable="custom" prop="usedHours">
          <template #default="{ row }">{{ row.usedHours ?? 0 }}{{ row.hoursUnit || '课时' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.remainHours" label="剩余数量" width="92" align="center"
                         sortable :sort-method="(a, b) => remainHours(a) - remainHours(b)">
          <template #default="{ row }">
            <b :style="{ color: remainHours(row) > 0 ? '#12b76a' : '#98a2b3' }">{{ remainHours(row) }}</b>{{ row.hoursUnit || '课时' }}
          </template>
        </el-table-column>
        <el-table-column v-if="cols.totalFee" label="总学费" width="96" align="right" sortable="custom" prop="totalAmount">
          <template #default="{ row }">¥{{ totalFee(row) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.paidAmount" label="实缴费用" width="96" align="right">
          <template #default="{ row }">¥{{ Number(row.paidAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.usedFee" label="已用学费" width="96" align="right"
                         sortable :sort-method="(a, b) => usedFee(a) - usedFee(b)">
          <template #default="{ row }">¥{{ usedFee(row) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.remainFee" label="剩余学费" width="96" align="right"
                         sortable :sort-method="(a, b) => remainFee(a) - remainFee(b)">
          <template #default="{ row }">
            <b :style="{ color: remainFee(row) > 0 ? '#155EEF' : '#98a2b3' }">¥{{ remainFee(row) }}</b>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.due" label="欠费" width="86" align="right"
                         sortable :sort-method="(a, b) => rowDueN(a) - rowDueN(b)">
          <template #default="{ row }">
            <span :style="{ color: rowDueN(row) > 0 ? '#f04438' : '#98a2b3', fontWeight: rowDueN(row) > 0 ? 600 : 400 }">
              ¥{{ rowDueN(row).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.campus" label="报读校区" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.campus || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="cols.status" label="报读状态" width="88" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="{ 1: 'success', 2: 'info', 3: 'danger', 4: 'warning', 5: 'info', 6: 'warning' }[row.status]" effect="plain">
              {{ { 1: '在读', 2: '已结课', 3: '已退订', 4: '已转课', 5: '已作废', 6: '停课' }[row.status] || '-' }}
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
                    <span class="mi-txt"><b>查看详情</b><i>报名课时与缴费</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="assign" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><User /></el-icon></span>
                    <span class="mi-txt"><b>指派老师</b><i>指派或更换老师</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="compensate" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Clock /></el-icon></span>
                    <span class="mi-txt"><b>补课时</b><i>补充请假课时</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1 || row.status === 6" command="renew" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Wallet /></el-icon></span>
                    <span class="mi-txt"><b>续费</b><i>追加课时与费用</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" command="suspend" class="more-item">
                    <span class="mi mi-orange"><el-icon :size="13"><VideoPause /></el-icon></span>
                    <span class="mi-txt"><b>停课</b><i>暂停课消可复课</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-else-if="row.status === 6" command="resume" class="more-item">
                    <span class="mi mi-green"><el-icon :size="13"><VideoPlay /></el-icon></span>
                    <span class="mi-txt"><b>复课</b><i>恢复在读</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1 || row.status === 6" command="finish" class="more-item">
                    <span class="mi mi-purple"><el-icon :size="13"><CircleCheck /></el-icon></span>
                    <span class="mi-txt"><b>结课</b><i>结束本门课程</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" command="transfer" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Switch /></el-icon></span>
                    <span class="mi-txt"><b>转课</b><i>跳转办理中心转出</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" command="unsubscribe" divided class="more-item">
                    <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                    <span class="mi-txt"><b>退订</b><i>取消订阅自动停课</i></span>
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
    <!-- 续费：追加课时与费用（课时/应收/实收/渠道/备注） -->
    <el-dialog v-model="renewVisible" title="续费" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="学员/课程">
          <el-input :model-value="`${studentName(renewRow?.studentId)} · ${courseName(renewRow?.courseId)}`" disabled />
        </el-form-item>
        <el-form-item label="续费课时" required>
          <el-input-number v-model="renewForm.hours" :min="1" :max="999" style="width: 140px" />
        </el-form-item>
        <el-form-item label="续费金额" required>
          <el-input-number v-model="renewForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 140px" />
          <span style="margin-left: 6px; color: #667c99; font-size: 13px">元</span>
        </el-form-item>
        <el-form-item label="实收金额" required>
          <el-input-number v-model="renewForm.paidAmount" :min="0" :precision="2" :controls="false" style="width: 140px" />
          <span style="margin-left: 6px; color: #667c99; font-size: 13px">元（允许欠费）</span>
        </el-form-item>
        <el-form-item label="支付渠道">
          <el-select v-model="renewForm.channel" clearable style="width: 100%">
            <el-option v-for="d in dictStore.options('pay_channel')" :key="d.value" :label="d.label" :value="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="renewForm.remark" maxlength="100" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取 消</el-button>
        <el-button type="primary" :loading="renewSaving" @click="saveRenew">保 存</el-button>
      </template>
    </el-dialog>
    <DetailDrawer v-model="detailVisible" title="报名详情" :fields="detailFields" :data="detailRow" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const query = reactive({ studentId: null, status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.enrollPage(q), query)

const students = ref([])
const teachers = ref([])
const clazzes = ref([])
const courses = ref([])
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
const studentPhone = (id) => students.value.find((s) => s.id === id)?.phone || ''
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const clazzName = (id) => clazzes.value.find((c) => c.id === id)?.name || '-'
const teacherName = (id) => teachers.value.find((t) => t.id === id)?.name || '-'
/* 金额口径：总学费=应收+优惠（原价合计）；已用学费=已用数量×单价；剩余学费=总学费−已用学费；欠费=应收−实缴 */
const totalFee = (r) => (Number(r.totalAmount || 0) + Number(r.discountAmount || 0)).toFixed(2)
const usedFee = (r) => (Number(r.usedHours || 0) * Number(r.price || 0)).toFixed(2)
const remainFee = (r) => (Number(totalFee(r)) - Number(usedFee(r))).toFixed(2)
const remainHours = (r) => Math.max(Number(r.totalHours || 0) - Number(r.usedHours || 0), 0)
const rowDueN = (r) => Math.max(Number(r.totalAmount || 0) - Number(r.paidAmount || 0), 0)

const allColumns = [
  { prop: 'studentId', label: '学员姓名' }, { prop: 'phone', label: '联系电话' },
  { prop: 'courseId', label: '报读课程' }, { prop: 'teachMode', label: '授课模式' },
  { prop: 'clazzId', label: '班级' }, { prop: 'teacherId', label: '授课老师' },
  { prop: 'totalHours', label: '购买数量' }, { prop: 'usedHours', label: '已用数量' },
  { prop: 'remainHours', label: '剩余数量' }, { prop: 'totalFee', label: '总学费' },
  { prop: 'paidAmount', label: '实缴费用' }, { prop: 'usedFee', label: '已用学费' },
  { prop: 'remainFee', label: '剩余学费' }, { prop: 'due', label: '欠费' },
  { prop: 'campus', label: '报读校区' }, { prop: 'status', label: '报读状态' }
]
const cols = reactive({ studentId: true, phone: true, courseId: true, teachMode: true, clazzId: true,
  teacherId: true, totalHours: true, usedHours: true, remainHours: true, totalFee: true,
  paidAmount: true, usedFee: false, remainFee: true, due: true, campus: true, status: true })
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
      { prop: 'status', label: '状态', map: { 1: '在读', 2: '已结课', 3: '已退费', 4: '已转课' } }
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

/* ===== 停课/复课、续费、结课、转课 ===== */
const router = useRouter()
async function suspendRow(row) {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt('停课期间课消暂停（课次保留），可随时复课。选填停课原因', '停课',
      { inputPlaceholder: '如：学员外出', confirmButtonText: '确认停课', cancelButtonText: '取 消', type: 'warning' })
    remark = (r.value || '').trim()
  } catch { return }
  await api.enrollSuspend(row.id, { remark })
  ElMessage.success('已停课')
  load()
}
async function resumeRow(row) {
  await ElMessageBox.confirm(`确认恢复「${studentName(row.studentId)}」的「${courseName(row.courseId)}」为在读？`, '复课',
    { type: 'info', confirmButtonText: '确认复课' })
  await api.enrollResume(row.id)
  ElMessage.success('已复课')
  load()
}
const renewVisible = ref(false)
const renewSaving = ref(false)
const renewRow = ref(null)
const renewForm = ref({})
function openRenew(row) {
  renewRow.value = row
  renewForm.value = { hours: 10, amount: null, paidAmount: null, channel: null, remark: '' }
  renewVisible.value = true
}
async function saveRenew() {
  const f = renewForm.value
  if (!f.hours || f.hours < 1) return ElMessage.warning('请填写续费课时')
  if (!f.amount || f.amount <= 0) return ElMessage.warning('请填写续费金额')
  if (f.paidAmount == null || f.paidAmount < 0) return ElMessage.warning('请填写实收金额')
  if (f.paidAmount > f.amount) return ElMessage.warning('实收金额不能超过续费金额')
  renewSaving.value = true
  try {
    await api.enrollRenew(renewRow.value.id, f)
    ElMessage.success('续费成功')
    renewVisible.value = false
    load()
  } finally { renewSaving.value = false }
}
async function finishRow(row) {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt(
      `结课后「${courseName(row.courseId)}」将不可再排课/续费（剩余课时 ${remainHours(row)}${row.hoursUnit || '课时'}）`,
      '结课', { inputPlaceholder: '选填结课说明', confirmButtonText: '确认结课', cancelButtonText: '取 消', type: 'warning' })
    remark = (r.value || '').trim()
  } catch { return }
  await api.enrollFinish(row.id, { remark })
  ElMessage.success('已结课')
  load()
}
/** 转课：跳转办理中心转课页并预选学员与转出报名单 */
function transferRow(row) {
  router.push({ path: '/reenroll', query: { mode: 'transfer', studentId: row.studentId, enrollId: row.id } })
}
async function compensate(row) {
  const { value } = await ElMessageBox.prompt('补充课时次数（因请假取消的课时）', '补课时', { inputValue: '1' })
  await api.enrollCompensateHours(row.id, Number(value) || 1)
  ElMessage.success('课时已补充')
  load()
}

/** 操作列下拉菜单分发 */
function onRowCommand(cmd, row) {
  switch (cmd) {
    case 'detail': openDetail(row); break
    case 'assign': assign(row); break
    case 'compensate': compensate(row); break
    case 'unsubscribe': unsubscribe(row); break
    case 'renew': openRenew(row); break
    case 'suspend': suspendRow(row); break
    case 'resume': resumeRow(row); break
    case 'finish': finishRow(row); break
    case 'transfer': transferRow(row); break
  }
}


function doExport() {
  exportXlsx('报名列表', [
      { label: '学员', prop: 'studentId', fmt: (v) => studentName(v) },
      { label: '课程', prop: 'courseId', fmt: (v) => courseName(v) },
      { label: '总课时', prop: 'totalHours' }, { label: '已消课时', prop: 'usedHours' },
      { label: '应收', prop: 'totalAmount' }, { label: '已缴', prop: 'paidAmount' },
      { label: '状态', prop: 'status', fmt: (v) => ['', '在读', '已结课', '已退费', '已转课'][v] || '' }
    ], rows.value)
}
onMounted(async () => {
  load()
  dictStore.load()
  students.value = (await api.studentList()) || []
  teachers.value = (await api.teacherList()) || []
  clazzes.value = (await api.clazzList()) || []
  courses.value = (await api.courseList()) || []
})
</script>
