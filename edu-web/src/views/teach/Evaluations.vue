<template>
  <el-card>
    <template #header>学生给我的评价</template>
    <el-table :data="list" stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="评分" width="150">
        <template #default="{ row }"><el-rate :model-value="row.score" disabled /></template>
      </el-table-column>
      <el-table-column prop="content" label="评价内容" min-width="280" />
      <el-table-column prop="createTime" label="时间" width="180" />
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
  try { list.value = (await api.teachEvaluations()) || [] } finally { loading.value = false }
})
</script>
