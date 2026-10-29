<template>
  <el-card>
    <template #header>我的课程排课时间</template>
    <el-table :data="list" stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="endTime" label="结束时间" width="180" />
      <el-table-column prop="classroom" label="教室" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="['warning', 'success'][row.status]">{{ ['待上课', '已完成'][row.status] }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const loading = ref(false)
onMounted(async () => {
  loading.value = true
  try { list.value = (await api.portalMySchedule()) || [] } finally { loading.value = false }
})
</script>
