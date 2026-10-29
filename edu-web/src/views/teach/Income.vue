<template>
  <div v-loading="loading">
    <el-row :gutter="14">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <el-card class="card">
          <div class="num">{{ c.value }}</div>
          <div class="label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 14px">
      <template #header>说明</template>
      <div class="desc">
        课程收入按您所带学生的报名合同统计：应收总额 = 课时单价 × 总课时 × 折扣；
        已缴金额为学生实际缴费累计。平均折扣为各合同折扣的算术平均。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../../api'

const data = ref({})
const loading = ref(false)

const cards = computed(() => [
  { label: '所带学生数', value: data.value.studentCount ?? '-' },
  { label: '课程总收入(元)', value: data.value.totalAmount ?? '-' },
  { label: '已缴金额(元)', value: data.value.paidAmount ?? '-' },
  { label: '平均折扣(%)', value: data.value.avgDiscount ?? '-' }
])

onMounted(async () => {
  loading.value = true
  try { data.value = (await api.teachIncome()) || {} } finally { loading.value = false }
})
</script>

<style scoped>
.card { margin-bottom: 8px; text-align: center; }
.num { font-size: 26px; font-weight: bold; color: #1f6feb; }
.label { color: #909399; margin-top: 6px; font-size: 13px; }
.desc { color: #666; font-size: 13px; line-height: 1.8; }
</style>
