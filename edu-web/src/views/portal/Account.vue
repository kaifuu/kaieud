<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">账户管理</span>
    </div>

    <el-row :gutter="14" style="margin-bottom: 14px">
      <el-col :span="8">
        <div class="panel sum-card balance">
          <div class="num">¥{{ data.balance ?? '0.00' }}</div>
          <div class="label">账户余额（合同退费 + 补课充值）</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel sum-card">
          <div class="num" style="color: #12b76a">¥{{ data.rechargeTotal ?? '0.00' }}</div>
          <div class="label">累计补课充值</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel sum-card">
          <div class="num" style="color: #f79009">¥{{ data.refundInTotal ?? '0.00' }}</div>
          <div class="label">累计合同退费</div>
        </div>
      </el-col>
    </el-row>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-alert type="info" :closable="false"
                  title="账户余额来源两部分：① 合同退费所得（教务在合同退费时选择「退费至账户」入账）② 补课充值（如1对4转1对1补差价，由教务在财务管理-充值管理办理）。本页面仅查看，如需充值请联系教务。" />
      </div>
      <el-table :data="data.logs || []" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'success' : 'warning'" effect="light">
              {{ row.type === 1 ? '补课充值' : '合同退费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.type === 1 ? '#12b76a' : '#f79009', fontWeight: 600, fontSize: '15px' }">
              {{ row.type === 1 ? '+' : '+' }}{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="退费课时" width="100" align="center">
          <template #default="{ row }">{{ row.hours != null ? row.hours + ' 课时' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="contractNo" label="关联合同" width="160" />
        <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!(data.logs || []).length && !loading" description="暂无账户流水" :image-size="70" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'

const data = ref({})
const loading = ref(false)

function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

onMounted(async () => {
  loading.value = true
  try { data.value = (await api.portalMyAccount()) || {} }
  finally { loading.value = false }
})
</script>

<style scoped>
.sum-card { text-align: center; padding: 20px 0; }
.sum-card .num { font-size: 26px; font-weight: 700; color: #344054; }
.sum-card .label { font-size: 13px; color: #8fa8c4; margin-top: 5px; }
.sum-card.balance .num { color: #155EEF; }
</style>
