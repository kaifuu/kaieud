<template>
  <div>
    <el-row :gutter="14">
      <el-col :span="8" v-for="c in courses" :key="c.id">
        <el-card class="course-card">
          <div class="name">{{ c.name }}</div>
          <div class="meta">
            <el-tag :type="c.courseType === 1 ? 'primary' : 'warning'" size="small">{{ c.courseType === 1 ? '班课' : '一对一' }}</el-tag>
            <span class="item">{{ c.subject }}</span>
            <span class="item">{{ c.totalHours }}课时</span>
          </div>
          <div class="price">¥{{ c.price }}/课时 <span v-if="c.discount < 100" class="discount">{{ c.discount }}折</span></div>
          <template v-if="c.courseType === 1">
            <el-select v-model="pick[c.id]" placeholder="选择班级" size="small" style="width: 100%; margin: 8px 0">
              <el-option v-for="cl in clazzesOf(c.id)" :key="cl.id" :label="cl.name" :value="cl.id" />
            </el-select>
            <el-button type="primary" style="width: 100%" @click="subscribe(c)">立即订阅</el-button>
          </template>
          <el-button v-else type="warning" style="width: 100%; margin-top: 8px" @click="subscribe(c)">预约一对一</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <template #header>我的课程</template>
      <el-table :data="myEnrolls" stripe>
        <el-table-column label="课程" min-width="160"><template #default="{ row }">{{ courseName(row.courseId) }}</template></el-table-column>
        <el-table-column label="剩余课时" width="100"><template #default="{ row }">{{ row.totalHours - row.usedHours }}</template></el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ ['', '在读', '已结课', '已退费'][row.status] }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const courses = ref([])
const clazzes = ref([])
const myEnrolls = ref([])
const pick = ref({})

const clazzesOf = (courseId) => clazzes.value.filter((c) => c.courseId === courseId)
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'

const load = async () => {
  const d = (await api.portalCourses()) || {}
  courses.value = d.courses || []
  clazzes.value = d.clazzes || []
  myEnrolls.value = d.myEnrolls || []
}

const subscribe = async (c) => {
  const clazzId = c.courseType === 1 ? pick.value[c.id] : null
  if (c.courseType === 1 && !clazzId) return ElMessage.warning('请先选择班级')
  await api.portalSubscribe({ courseId: c.id, clazzId })
  ElMessage.success('订阅成功，请联系教务完成缴费')
  load()
}

onMounted(load)
</script>

<style scoped>
.course-card { margin-bottom: 14px; }
.name { font-weight: bold; margin-bottom: 8px; }
.meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.item { color: #909399; font-size: 13px; }
.price { color: #f56c6c; font-weight: bold; }
.discount { font-size: 12px; background: #f56c6c; color: #fff; border-radius: 4px; padding: 1px 5px; margin-left: 6px; }
</style>
