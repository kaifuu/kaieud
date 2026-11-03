<template>
  <div>
    <el-row :gutter="12">
      <el-col :span="4" v-for="c in pagedCourses" :key="c.id">
        <el-card class="course-card" shadow="hover">
          <div class="name">{{ c.name }}</div>
          <div class="meta">
            <el-tag :type="c.courseType === 1 ? 'primary' : 'warning'" size="small">{{ c.courseType === 1 ? '班课' : '一对一' }}</el-tag>
            <span class="item">{{ c.subject }}</span>
            <span class="item">{{ c.totalHours }}课时</span>
          </div>
          <div class="price">¥{{ c.price }}/课时 <span v-if="c.discount < 100" class="discount">{{ c.discount }}折</span></div>
          <div class="flex-space"></div>
          <template v-if="c.courseType === 1">
            <el-select v-model="pick[c.id]" placeholder="选择班级" size="small" style="width: 100%">
              <el-option v-for="cl in clazzesOf(c.id)" :key="cl.id" :label="cl.name" :value="cl.id" />
            </el-select>
            <el-button type="primary" size="small" class="card-btn" @click="subscribe(c)">立即订阅</el-button>
          </template>
          <el-button v-else type="warning" size="small" class="card-btn" @click="subscribe(c)">预约一对一</el-button>
        </el-card>
      </el-col>
    </el-row>

    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="page"
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :pager-count="7"
        :total="courses.length"
        hide-on-single-page
      />
    </div>

    <el-card style="margin-top: 16px">
      <template #header>我的课程</template>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="myEnrolls" stripe>
        <el-table-column label="课程" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ courseName(row.courseId) }}</template></el-table-column>
        <el-table-column label="剩余课时" width="100" show-overflow-tooltip><template #default="{ row }">{{ row.totalHours - row.usedHours }}</template></el-table-column>
        <el-table-column label="状态" width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ ['', '在读', '已结课', '已退费'][row.status] }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const courses = ref([])
const clazzes = ref([])
const myEnrolls = ref([])
const pick = ref({})
const page = ref(1)
const pageSize = 12

const pagedCourses = computed(() => courses.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const clazzesOf = (courseId) => clazzes.value.filter((c) => c.courseId === courseId)
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'

const load = async () => {
  const d = (await api.portalCourses()) || {}
  courses.value = d.courses || []
  clazzes.value = d.clazzes || []
  myEnrolls.value = d.myEnrolls || []
  const maxPage = Math.max(1, Math.ceil(courses.value.length / pageSize))
  if (page.value > maxPage) page.value = maxPage
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
.course-card {
  margin-bottom: 12px;
  aspect-ratio: 1 / 1;
  box-sizing: border-box;
}
.course-card :deep(.el-card__body) {
  height: 100%;
  box-sizing: border-box;
  padding: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.name {
  font-weight: bold;
  font-size: 13px;
  line-height: 1.3;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; min-width: 0; }
.meta .item { flex-shrink: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item { color: #909399; font-size: 12px; }
.price { color: #f56c6c; font-weight: bold; font-size: 13px; white-space: nowrap; }
.discount { font-size: 11px; background: #f56c6c; color: #fff; border-radius: 3px; padding: 0 4px; margin-left: 4px; }
.flex-space { flex: 1; min-height: 4px; }
.card-btn { width: 100%; margin-top: 6px; }
.pager-wrap { display: flex; justify-content: center; margin: 4px 0 16px; }
.pager-wrap :deep(.el-pagination .el-pager li) { border-radius: 6px; }
</style>
