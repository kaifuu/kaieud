<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">旷课审批</span>
      <span class="header-tip">审批通过不扣课；驳回立即扣课；超过72小时未审批系统自动扣课</span>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px" @change="search">
          <el-option label="待审批" :value="0" /><el-option label="已批准(免扣课)" :value="1" />
          <el-option label="已驳回(已扣课)" :value="2" /><el-option label="超72小时自动扣课" :value="3" />
        </el-select>
        <el-select v-model="query.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="search">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="学员姓名" clearable style="width: 150px" @keyup.enter="search" />
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="申请开始" end-placeholder="申请结束" style="width: 250px" @change="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column label="学员/班级" min-width="130">
          <template #default="{ row }">{{ row.studentName || row.clazzName + '（整班）' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="150" />
        <el-table-column prop="teacherName" label="教师" width="90" />
        <el-table-column prop="classTime" label="上课时间" width="170" />
        <el-table-column prop="reason" label="旷课说明" min-width="140">
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="170" />
        <el-table-column label="审批截止" width="170">
          <template #default="{ row }">
            {{ row.deadline }}
            <el-tag v-if="row.status === 0 && isOverdue(row)" size="small" type="danger" effect="plain">即将自动扣课</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="absenceMeta[row.status]?.type" effect="plain">
              {{ absenceMeta[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批人/时间" width="170">
          <template #default="{ row }">{{ row.approver ? row.approver + ' · ' + (row.approveTime || '') : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="success" @click="approve(row, true)">批准</el-button>
              <el-button link type="danger" @click="approve(row, false)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'

/* 旷课申请状态：0待审批 1已批准(免扣课) 2已驳回(已扣课) 3超72小时自动扣课 */
const absenceMeta = {
  0: { label: '待审批', type: 'warning' },
  1: { label: '已批准·免扣课', type: 'success' },
  2: { label: '已驳回·已扣课', type: 'danger' },
  3: { label: '超时·自动扣课', type: 'danger' }
}

const teachers = ref([])
const range = ref(null)
const query = reactive({ status: null, teacherId: null, keyword: '' })

const { loading, rows, pager, seq, onSizeChange, search, load } =
  usePage((q) => api.absencePage({
    ...q, ...query,
    begin: range.value?.[0], end: range.value?.[1]
  }), query)

const isOverdue = (row) => new Date(row.deadline.replace(/-/g, '/')) <= new Date()

async function approve(row, pass) {
  if (pass) {
    await ElMessageBox.confirm('批准后该课次不扣课时，确认批准？', '旷课审批', { type: 'warning' })
    await api.absenceApprove(row.id)
    ElMessage.success('已批准，本课次不扣课')
  } else {
    const { value } = await ElMessageBox.prompt('驳回将立即扣课（1节，记入课时管理），可填写驳回原因', '旷课审批',
      { inputPlaceholder: '驳回原因（选填）', type: 'warning' })
    await api.absenceReject(row.id, value || '')
    ElMessage.success('已驳回并完成扣课')
  }
  load()
}

onMounted(async () => {
  load()
  teachers.value = (await api.teacherList()) || []
})
</script>

<style scoped>
.header-tip { font-size: 12.5px; color: #98a2b3; margin-left: 12px; }
</style>
