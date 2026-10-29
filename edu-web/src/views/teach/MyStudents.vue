<template>
  <el-card>
    <template #header>我所带的学生</template>
    <el-table :data="list" stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="学生" width="110">
        <template #default="{ row }">{{ row.student ? row.student.name : '-' }}</template>
      </el-table-column>
      <el-table-column label="就读学校" width="140">
        <template #default="{ row }">{{ row.student ? row.student.school : '-' }}</template>
      </el-table-column>
      <el-table-column label="课程" min-width="150">
        <template #default="{ row }">{{ row.course ? row.course.name : '-' }}</template>
      </el-table-column>
      <el-table-column label="课时(已用/总)" width="120">
        <template #default="{ row }">{{ row.enroll.usedHours }} / {{ row.enroll.totalHours }}</template>
      </el-table-column>
      <el-table-column label="折扣" width="90">
        <template #default="{ row }">{{ row.enroll.discount }}%</template>
      </el-table-column>
      <el-table-column label="合同金额" width="110">
        <template #default="{ row }">¥{{ row.enroll.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ ['', '在读', '已结课', '已退费'][row.enroll.status] }}</template>
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
  try { list.value = (await api.teachStudents()) || [] } finally { loading.value = false }
})
</script>
