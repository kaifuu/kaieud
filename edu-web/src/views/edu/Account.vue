<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">充值管理</span>
      <div class="actions">
        <el-button type="primary" @click="openRecharge">账户充值</el-button>
      </div>
    </div>

    <el-row :gutter="14" style="margin-bottom: 14px">
      <el-col :span="8" v-for="c in cards" :key="c.label">
        <div class="panel sum-card">
          <div class="num">¥{{ c.value }}</div>
          <div class="label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="query.studentId" placeholder="学生" clearable filterable style="width: 160px">
          <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px">
          <el-option label="补课充值" :value="1" /><el-option label="合同退费" :value="2" />
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" @change="doSearch" />
        <el-button type="primary" @click="doSearch">查询</el-button>
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column prop="studentName" label="学生" width="100"  show-overflow-tooltip/>
        <el-table-column label="类型" width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'success' : 'warning'" effect="light">
              {{ row.type === 1 ? '补课充值' : '合同退费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="110" align="right" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: row.type === 1 ? '#12b76a' : '#f79009', fontWeight: 600 }">
              {{ row.type === 1 ? '+' : '' }}{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="退费课时" width="90" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.hours != null ? row.hours : '-' }}</template>
        </el-table-column>
        <el-table-column prop="contractNo" label="关联合同" width="150"  show-overflow-tooltip/>
        <el-table-column prop="remark" label="原因备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100"  show-overflow-tooltip/>
        <el-table-column prop="createTime" label="操作时间" width="165"  show-overflow-tooltip/>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 充值弹窗：必须关联合同 + 注明原因 -->
    <el-dialog v-model="visible" title="学生账户充值" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="学生" required>
          <el-select v-model="form.studentId" filterable style="width: 100%" @change="loadContracts">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联合同" required>
          <el-select v-model="form.contractId" style="width: 100%" placeholder="选择该学生的合同" :disabled="!form.studentId">
            <el-option v-for="c in contracts" :key="c.id"
                       :label="`${c.contractNo}（${fmtAmount(c.totalAmount)}元）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="充值金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="充值原因" required>
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200"
                    placeholder="请填写充值原因，例如：学员报名为1对4小组课（单价较低），请假后转1对1补课，需按课时单价差补交费用" />
          <div class="form-tip">典型场景：1对4课程学员请假后补1对1课程，两种班型课时单价不同，需补差价计入账户</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'

const query = reactive({ studentId: null, type: null })
const range = ref(null)
const { loading, rows, pager, seq, onSizeChange, search, load } =
  usePage((q) => api.accountPage({ ...q, begin: range.value?.[0], end: range.value?.[1] }), query)

const summary = ref({})
const cards = computed(() => [
  { label: '累计补课充值(元)', value: summary.value.rechargeTotal ?? '-' },
  { label: '累计合同退费(元)', value: summary.value.refundInTotal ?? '-' },
  { label: '账户余额合计(元)', value: summary.value.balanceTotal ?? '-' }
])

const students = ref([])
const visible = ref(false)
const saving = ref(false)
const form = ref({})
const contracts = ref([])

function openRecharge() {
  form.value = { studentId: null, contractId: null, amount: 100, remark: '' }
  contracts.value = []
  visible.value = true
}

async function loadContracts(studentId) {
  form.value.contractId = null
  if (!studentId) { contracts.value = []; return }
  const d = await api.contractPage({ page: 1, size: 50, studentId })
  contracts.value = d.records || []
}

async function save() {
  const f = form.value
  if (!f.studentId || !f.contractId) return ElMessage.warning('请选择学生与关联合同')
  if (!f.amount || f.amount <= 0) return ElMessage.warning('充值金额必须大于0')
  if (!f.remark?.trim()) return ElMessage.warning('请填写充值原因')
  saving.value = true
  try {
    await api.accountRecharge(f)
    ElMessage.success('充值成功，学生账户已入账')
    visible.value = false
    doSearch()
  } finally { saving.value = false }
}

function doSearch() { search(); loadSummary() }
async function loadSummary() {
  summary.value = (await api.accountSummary({ studentId: query.studentId })) || {}
}
const fmtAmount = (v) => Number(v ?? 0).toFixed(2)

onMounted(async () => {
  load()
  loadSummary()
  students.value = (await api.studentList()) || []
})
</script>

<style scoped>
.sum-card { text-align: center; padding: 18px 0; }
.sum-card .num { font-size: 24px; font-weight: 700; color: #155EEF; }
.sum-card .label { font-size: 13px; color: #8fa8c4; margin-top: 4px; }
.form-tip { font-size: 12px; color: #8fa8c4; line-height: 1.5; margin-top: 4px; }
</style>
