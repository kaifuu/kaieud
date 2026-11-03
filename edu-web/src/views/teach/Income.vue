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

    <!-- 班级收入明细：各班对应合同收入 + 课时/请假/旷课 -->
    <el-card style="margin-top: 14px">
      <template #header>班级收入明细（所带班级对应合同的收入与课时情况）</template>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="data.groups || []" stripe>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="班级" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="课程" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.courseName || '-' }}</template>
        </el-table-column>
        <el-table-column label="学生数" width="80" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.studentCount }}</template>
        </el-table-column>
        <el-table-column label="合同金额(元)" width="120" align="right" show-overflow-tooltip>
          <template #default="{ row }">¥{{ row.totalAmount }}</template>
        </el-table-column>
        <el-table-column label="已缴(元)" width="100" align="right" show-overflow-tooltip>
          <template #default="{ row }">¥{{ row.paidAmount }}</template>
        </el-table-column>
        <el-table-column label="总课时" width="80" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.totalHours }}</template>
        </el-table-column>
        <el-table-column label="已上课时" width="90" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.usedHours }}</template>
        </el-table-column>
        <el-table-column label="未完课时" width="90" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'remain-warn': row.remainHours > 0 }">{{ row.remainHours }}</span>
          </template>
        </el-table-column>
        <el-table-column label="请假(次)" width="90" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.leaveCount }}</template>
        </el-table-column>
        <el-table-column label="旷课(次)" width="90" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'absent-warn': row.absentCount > 0 }">{{ row.absentCount }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!(data.groups || []).length" description="暂无班级收入数据" :image-size="60" />
    </el-card>

    <el-card style="margin-top: 14px">
      <template #header>说明</template>
      <div class="desc">
        课程收入按您所带学生的报名合同统计：应收总额 = 课时单价 × 总课时 × 折扣；
        已缴金额为学生实际缴费累计。平均折扣为各合同折扣的算术平均。<br>
        班级收入明细按所带班级聚合：总课时/已上课时/未完课时取该班学生报名课时；
        请假含教师请假与学生请假课次，旷课含待审批/免扣/已扣课的旷课课次；一对一报名单独归入「一对一」组。
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
  { label: '总课时/已上/未完', value: `${data.value.totalHours ?? '-'} / ${data.value.usedHours ?? '-'} / ${data.value.remainHours ?? '-'}` },
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
.remain-warn { color: #155EEF; font-weight: 600; }
.absent-warn { color: #f04438; font-weight: 600; }
</style>
