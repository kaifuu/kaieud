<template>
  <el-card>
    <template #header>
      <div class="card-head">
        <span>学生给我的评价</span>
        <el-radio-group v-model="view">
          <el-radio-button value="student">按学生</el-radio-button>
          <el-radio-button value="clazz">按班级</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <!-- 学生/班级卡片视图：每张卡片聚合该学生/该班级收到的全部评价 -->
    <div class="persp-cards" v-loading="loading">
      <div v-for="c in cards" :key="c.key" class="persp-card" @click="openDetail(c)">
        <div class="pc-top">
          <span class="pc-name">{{ c.name }}</span>
          <span class="pc-count">{{ c.items.length }} 条</span>
        </div>
        <div class="pc-meta">
          <el-rate :model-value="c.avg" disabled />
          <span class="pc-avg">{{ c.avg }} 分</span>
        </div>
        <div class="pc-snip">{{ c.items[0].evaluation.content }}</div>
        <div class="pc-foot">
          <template v-if="view === 'student'">最新评价 · 点击查看全部</template>
          <template v-else>{{ c.students }} 位学生 · 点击查看全部</template>
        </div>
      </div>
      <el-empty v-if="!loading && !cards.length" description="暂无评价" :image-size="70" />
    </div>

    <!-- 卡片详情抽屉：该学生/班级的全部评价列表 -->
    <el-drawer v-model="visible" :title="`${view === 'student' ? '学生' : '班级'}评价 — ${detail?.name || ''}`" size="500px">
      <div class="d-sum">共 {{ detail?.items.length }} 条评价 · 平均 {{ detail?.avg }} 分</div>
      <div v-for="it in detail?.items" :key="it.evaluation.id" class="eval-item">
        <div class="ei-head">
          <el-rate :model-value="it.evaluation.score" disabled size="small" />
          <span class="ei-time">{{ it.evaluation.createTime }}</span>
        </div>
        <div class="ei-content">{{ it.evaluation.content }}</div>
        <div class="ei-sub">
          <template v-if="view === 'clazz'">学生：{{ it.student ? it.student.name : '-' }}<br /></template>
          课程：{{ it.course ? it.course.name : '-' }}
        </div>
      </div>
      <el-empty v-if="detail && !detail.items.length" description="暂无评价" :image-size="60" />
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const loading = ref(false)
const view = ref('student')
const visible = ref(false)
const detail = ref(null)

onMounted(async () => {
  loading.value = true
  try { list.value = (await api.teachEvaluations()) || [] } finally { loading.value = false }
})

/**
 * 双视角分组：按学生聚合（studentId）或按班级聚合（clazzId，无班级归入「一对一/未分班」）；
 * 卡片按评价条数倒序，每条记录内保持最新在前。
 */
const cards = computed(() => {
  const map = new Map()
  const avg = (items) =>
    Math.round((items.reduce((s, it) => s + (it.evaluation.score || 0), 0) / items.length) * 10) / 10
  for (const it of list.value) {
    let key, name
    if (view.value === 'student') {
      key = 's' + it.evaluation.studentId
      name = it.student ? it.student.name : `学生#${it.evaluation.studentId}`
    } else {
      key = it.clazz ? 'c' + it.clazz.id : 'o2o'
      name = it.clazz ? it.clazz.name : '一对一/未分班'
    }
    if (!map.has(key)) map.set(key, { key, name, items: [] })
    map.get(key).items.push(it)
  }
  return [...map.values()]
    .map((c) => ({
      ...c,
      avg: avg(c.items),
      students: new Set(c.items.map((it) => it.evaluation.studentId)).size
    }))
    .sort((a, b) => b.items.length - a.items.length)
})

function openDetail(c) {
  detail.value = c
  visible.value = true
}
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.persp-cards { display: flex; flex-wrap: wrap; gap: 14px; min-height: 120px; }
.persp-card {
  width: 260px; padding: 14px 16px; border-radius: 10px; cursor: pointer;
  border: 1px solid #e4edf7; background: linear-gradient(180deg, #ffffff, #f7fafd);
  transition: all 0.2s ease;
}
.persp-card:hover {
  border-color: rgba(21, 94, 239, 0.45);
  box-shadow: 0 4px 14px rgba(21, 94, 239, 0.14);
  transform: translateY(-2px);
}
.pc-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.pc-name { font-size: 15px; font-weight: 600; color: #1d2939; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pc-count { font-size: 12px; color: #fff; padding: 2px 10px; border-radius: 10px; background: linear-gradient(135deg, #155EEF, #5B8DEF); white-space: nowrap; }
.pc-meta { display: flex; align-items: center; gap: 8px; margin: 8px 0 6px; }
.pc-avg { font-size: 13px; font-weight: 600; color: #f79009; }
.pc-snip {
  font-size: 13px; color: #475467; line-height: 1.5; height: 40px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.pc-foot { margin-top: 8px; font-size: 12px; color: #98a2b3; }
.d-sum { font-size: 13px; color: #475467; margin-bottom: 12px; }
.eval-item { padding: 12px 0; border-bottom: 1px dashed #e4edf7; }
.ei-head { display: flex; align-items: center; justify-content: space-between; }
.ei-time { font-size: 12px; color: #98a2b3; }
.ei-content { font-size: 13.5px; color: #1d2939; margin: 6px 0; line-height: 1.6; }
.ei-sub { font-size: 12px; color: #98a2b3; }
</style>
