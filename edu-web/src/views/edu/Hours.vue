<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">课时管理</span>
      <span class="header-tip">扣课记录台账：到课 / 旷课 / 签到消课 / 手动消课 统一在此留痕，与学员合同已消课时一致</span>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="学员姓名" clearable style="width: 150px" @keyup.enter="doSearch" />
        <el-select v-model="query.teacherId" placeholder="教师" clearable filterable style="width: 140px" @change="doSearch">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="query.source" placeholder="扣课来源" clearable style="width: 130px" @change="doSearch">
          <el-option v-for="s in sources" :key="s" :label="s" :value="s" />
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="扣课开始" end-placeholder="扣课结束" style="width: 250px" @change="doSearch" />
        <el-button type="primary" @click="doSearch">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <el-tag type="primary" effect="light" size="large" class="sum-tag">
          当前筛选合计扣课：<b>{{ sumHours }}</b> 节
        </el-tag>
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column prop="consumeNo" label="扣课单号" width="170" />
        <el-table-column prop="studentName" label="学员" width="100" />
        <el-table-column prop="courseName" label="课程" min-width="150" />
        <el-table-column prop="teacherName" label="教师" width="90" />
        <el-table-column prop="classTime" label="上课时间" width="170">
          <template #default="{ row }">{{ row.classTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="扣课课时" width="90" align="center">
          <template #default="{ row }">
            <b :class="row.canceled === 1 ? 'hours-canceled' : 'hours-ok'">{{ row.hours }}</b>
          </template>
        </el-table-column>
        <el-table-column label="扣前→扣后剩余" width="130" align="center">
          <template #default="{ row }">{{ row.beforeHours }} → {{ row.afterHours }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceMeta[row.source]?.type || 'info'" effect="light">
              {{ row.source }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扣课说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="审核" width="85" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'danger'][row.auditStatus] || 'info'" effect="plain">
              {{ ['待审核', '已审核', '已驳回'][row.auditStatus] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="撤销" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.canceled === 1" size="small" type="info" effect="plain">已撤销</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="createTime" label="扣课时间" width="170" />
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
import api from '../../api'
import { exportXlsx } from '../../utils/export'

const sources = ['到课', '旷课', '签到消课', '消课']
const sourceMeta = {
  '到课': { type: 'success' },
  '旷课': { type: 'danger' },
  '签到消课': { type: 'primary' },
  '消课': { type: 'info' }
}

const teachers = ref([])
const range = ref(null)
const query = reactive({ keyword: '', teacherId: null, source: null })
const loading = ref(false)
const rows = ref([])
const sumHours = ref(0)
const pager = reactive({ page: 1, size: 10, total: 0 })
const seq = (i) => (pager.page - 1) * pager.size + i + 1

async function load() {
  loading.value = true
  try {
    const d = (await api.hoursRecords({
      page: pager.page, size: pager.size, ...query,
      begin: range.value?.[0], end: range.value?.[1]
    })) || {}
    rows.value = d.records || []
    pager.total = d.total || 0
    sumHours.value = d.sumHours ?? 0
  } finally { loading.value = false }
}
function doSearch() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

function doExport() {
  exportXlsx('课时扣课记录', [
    { label: '扣课单号', prop: 'consumeNo' }, { label: '学员', prop: 'studentName' },
    { label: '课程', prop: 'courseName' }, { label: '教师', prop: 'teacherName' },
    { label: '上课时间', prop: 'classTime' }, { label: '扣课课时', prop: 'hours' },
    { label: '扣前剩余', prop: 'beforeHours' }, { label: '扣后剩余', prop: 'afterHours' },
    { label: '来源', prop: 'source' }, { label: '扣课说明', prop: 'remark' }, { label: '操作人', prop: 'operator' },
    { label: '扣课时间', prop: 'createTime' }
  ], rows.value)
}

onMounted(async () => {
  load()
  teachers.value = (await api.teacherList()) || []
})
</script>

<style scoped>
.header-tip { font-size: 12.5px; color: #98a2b3; margin-left: 12px; }
.sum-tag { font-size: 13px; }
.hours-ok { color: #155EEF; }
.hours-canceled { color: #98a2b3; text-decoration: line-through; }
</style>
