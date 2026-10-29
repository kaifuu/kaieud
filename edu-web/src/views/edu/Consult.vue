<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">咨询与评价</span>
    </div>
    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab" @tab-change="reload">
        <el-tab-pane label="问题咨询" name="consult" />
        <el-tab-pane label="课程评价" name="eval" />
      </el-tabs>
    </div>
    <div class="panel table-panel" style="height: calc(100% - 110px)">
      <div class="toolbar">
        <el-select v-if="tab === 'consult'" v-model="query.status" placeholder="回复状态" clearable style="width: 130px">
          <el-option label="待回复" :value="0" /><el-option label="已回复" :value="1" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
      </div>
      <template v-if="tab === 'consult'">
        <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
          <el-table-column type="index" label="序号" width="60" :index="seq" />
          <el-table-column label="学生" width="100">
            <template #default="{ row }">{{ studentName(row.studentId) }}</template>
          </el-table-column>
          <el-table-column label="咨询老师" width="110">
            <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="咨询内容" min-width="220" />
          <el-table-column label="回复" min-width="220">
            <template #default="{ row }">{{ row.reply || '待回复' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-popconfirm title="确认删除该咨询?" @confirm="removeConsult(row.id)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template v-else>
        <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
          <el-table-column type="index" label="序号" width="60" :index="seq" />
          <el-table-column label="学生" width="100">
            <template #default="{ row }">{{ studentName(row.studentId) }}</template>
          </el-table-column>
          <el-table-column label="教师" width="110">
            <template #default="{ row }">{{ teacherName(row.teacherId) }}</template>
          </el-table-column>
          <el-table-column label="评分" width="150" sortable="custom" prop="score">
            <template #default="{ row }"><el-rate :model-value="row.score" disabled /></template>
          </el-table-column>
          <el-table-column prop="content" label="评价内容" min-width="280" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import { exportXlsx } from '../../utils/export'

const tab = ref('consult')
const query = reactive({ status: null })
const fetchers = {
  consult: (q) => api.consultPage(q),
  eval: (q) => api.evaluationPage(q)
}
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => fetchers[tab.value](q), query)

const students = ref([])
const teachers = ref([])
const studentName = (id) => students.value.find((s) => s.id === id)?.name || '-'
const teacherName = (id) => teachers.value.find((t) => t.id === id)?.name || '-'
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

function doExport() {
  if (tab.value === 'consult') {
    exportXlsx('咨询列表', [
      { label: '咨询内容', prop: 'content' }, { label: '回复', prop: 'reply' },
      { label: '时间', prop: 'createTime' }
    ], rows.value)
  } else {
    exportXlsx('评价列表', [
      { label: '评分', prop: 'score' }, { label: '评价内容', prop: 'content' },
      { label: '时间', prop: 'createTime' }
    ], rows.value)
  }
}

function reload() { query.status = null; search() }
async function removeConsult(id) {
  await api.consultDel(id)
  ElMessage.success('已删除')
  load()
}
onMounted(async () => {
  load()
  students.value = (await api.studentList()) || []
  teachers.value = (await api.teacherList()) || []
})
</script>
