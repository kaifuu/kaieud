<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">充值记录</span>
    </div>

    <el-row :gutter="14" style="margin-bottom: 14px">
      <el-col :span="8">
        <div class="panel sum-card balance">
          <div class="num">¥{{ summary.balance ?? '0.00' }}</div>
          <div class="label">{{ isStudent ? '账户余额（合同退费 + 补课充值）' : '本企业账户余额合计' }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel sum-card">
          <div class="num" style="color: #12b76a">¥{{ summary.rechargeTotal ?? '0.00' }}</div>
          <div class="label">{{ isStudent ? '累计补课充值' : '累计补课充值（全员）' }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel sum-card">
          <div class="num" style="color: #f79009">¥{{ summary.refundInTotal ?? '0.00' }}</div>
          <div class="label">{{ isStudent ? '累计合同退费' : '累计合同退费（全员）' }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="panel table-panel">
      <div class="toolbar">
        <!-- 管理员/财务模式：本企业全部学生的充值记录筛选 -->
        <template v-if="!isStudent">
          <el-select v-model="q.studentId" placeholder="学生" clearable filterable style="width: 150px" @change="doSearch">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <el-select v-model="q.type" placeholder="类型" clearable style="width: 110px" @change="doSearch">
            <el-option label="补课充值" :value="1" /><el-option label="合同退费" :value="2" />
          </el-select>
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" @change="doSearch" />
          <el-button type="primary" @click="doSearch">查询</el-button>
          <span style="color: #8fa8c4; font-size: 13px">本企业全部学生的充值记录（仅查询，充值请在财务管理-充值管理办理）</span>
        </template>
        <el-alert v-else type="info" :closable="false"
                  title="账户余额来源两部分：① 合同退费所得（教务在合同退费时选择「退费至账户」入账）② 补课充值（如1对4转1对1补差价，由教务在财务管理-充值管理办理）。本页面仅查询，不能发起充值；如需充值请联系教务在财务管理-充值管理办理。" />
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column v-if="!isStudent" prop="studentName" label="学生" width="100" />
        <el-table-column label="类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'success' : 'warning'" effect="light">
              {{ row.type === 1 ? '补课充值' : '合同退费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="130" align="right" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: row.type === 1 ? '#12b76a' : '#f79009', fontWeight: 600, fontSize: '15px' }">
              {{ row.type === 1 ? '+' : '+' }}{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="退费课时" width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.hours != null ? row.hours + ' 课时' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="contractNo" label="关联合同" width="160"  show-overflow-tooltip/>
        <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100"  show-overflow-tooltip/>
        <el-table-column label="操作时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length && !loading" description="暂无充值记录" :image-size="70" />
      <div v-if="!isStudent" class="pager-row">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="q.page" :page-size="q.size" @current-change="loadStaff" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '../../api'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()
/** 学生=本人记录；企业管理员/财务=本企业全部学生记录（仅查询） */
const isStudent = computed(() => userStore.user?.userType === 5)

const loading = ref(false)
const data = ref({})
const rows = ref([])
const total = ref(0)
const students = ref([])
const range = ref(null)
const q = reactive({ page: 1, size: 10, studentId: null, type: null })

const summary = computed(() => isStudent.value
  ? { balance: data.value.balance, rechargeTotal: data.value.rechargeTotal, refundInTotal: data.value.refundInTotal }
  : data.value)

function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }
function doSearch() { q.page = 1; loadStaff() }

async function loadStaff() {
  loading.value = true
  try {
    const d = (await api.portalAccountRecords({
      page: q.page, size: q.size, studentId: q.studentId, type: q.type,
      begin: range.value?.[0], end: range.value?.[1]
    })) || {}
    rows.value = d.records || []
    total.value = d.total || 0
    data.value = d
  } finally { loading.value = false }
}

onMounted(async () => {
  loading.value = true
  try {
    if (isStudent.value) {
      data.value = (await api.portalMyAccount()) || {}
      rows.value = data.value.logs || []
    } else {
      await loadStaff()
      students.value = (await api.studentList()) || []
    }
  } finally { loading.value = false }
})
</script>

<style scoped>
.sum-card { text-align: center; padding: 20px 0; }
.sum-card .num { font-size: 26px; font-weight: 700; color: #344054; }
.sum-card .label { font-size: 13px; color: #8fa8c4; margin-top: 5px; }
.sum-card.balance .num { color: #155EEF; }
</style>
