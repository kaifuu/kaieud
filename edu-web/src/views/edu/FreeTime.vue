<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">教师空闲查询</span>
    </div>
    <div class="panel" style="padding: 14px; margin-bottom: 14px">
      <div class="toolbar" style="padding: 0">
        <el-date-picker v-model="begin" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 140px" />
        <el-date-picker v-model="end" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 140px" />
        <el-select v-model="teacherIds" multiple collapse-tags filterable placeholder="选择多位教师（取共同空闲）" style="width: 300px">
          <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="period" placeholder="时段" clearable style="width: 120px">
          <el-option label="上午" :value="1" /><el-option label="下午" :value="2" /><el-option label="晚上" :value="3" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="query">查询共同空闲</el-button>
      </div>
      <el-alert style="margin-top: 10px" type="info" :closable="false"
                title="空闲 = 工作时段(08:00-21:00) - 教师固定禁排时段 - 已有排课；结果为所选教师的共同空闲交集" />
    </div>

    <div class="panel table-panel" style="height: auto">
      <el-table :data="result" v-loading="loading" stripe>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="date" label="空闲日期" width="120" />
        <el-table-column label="空闲时段" width="140">
          <template #default="{ row }">
            <el-tag effect="light">{{ row.start }} ~ {{ row.end }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="空闲教师" min-width="200">
          <template #default="{ row }">{{ row.teachers.join('、') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="goSchedule(row)">去排课</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../../api'

const router = useRouter()
const begin = ref(new Date().toISOString().slice(0, 10))
const end = ref(new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10))
const teacherIds = ref([])
const period = ref(null)
const teachers = ref([])
const result = ref([])
const loading = ref(false)

async function query() {
  if (!teacherIds.value.length) return ElMessage.warning('请至少选择一名教师')
  loading.value = true
  try {
    result.value = (await api.teacherFreeTime({
      begin: begin.value, end: end.value, period: period.value, teacherIds: teacherIds.value
    })) || []
    if (!result.value.length) ElMessage.info('所选时间段内没有共同空闲时段')
  } finally { loading.value = false }
}

/** 一键跳转排课（携带时间和老师） */
function goSchedule(row) {
  router.push({ path: '/schedule', query: {
    date: row.date, start: row.start, teacherIds: row.teacherIds.join(',')
  } })
}

onMounted(async () => {
  teachers.value = (await api.teacherList()) || []
})
</script>
