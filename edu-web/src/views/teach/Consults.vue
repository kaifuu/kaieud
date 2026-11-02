<template>
  <el-card>
    <template #header>
      <div class="card-head">
        <span>学生问题咨询</span>
        <el-radio-group v-model="view">
          <el-radio-button value="student">按学生</el-radio-button>
          <el-radio-button value="clazz">按班级</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <!-- 学生/班级卡片视图：每张卡片聚合该学生/该班级的全部咨询 -->
    <div class="persp-cards" v-loading="loading">
      <div v-for="c in cards" :key="c.key" class="persp-card" @click="openDetail(c)">
        <div class="pc-top">
          <span class="pc-name">{{ c.name }}</span>
          <span class="pc-count">{{ c.items.length }} 条</span>
        </div>
        <div class="pc-meta">
          <el-tag v-if="c.pending" size="small" type="danger" effect="light">{{ c.pending }} 条待回复</el-tag>
          <el-tag v-else size="small" type="success" effect="light">已全部回复</el-tag>
        </div>
        <div class="pc-snip">{{ c.items[0].consult.content }}</div>
        <div class="pc-foot">
          <template v-if="view === 'student'">最新咨询 · 点击查看全部</template>
          <template v-else>{{ c.students }} 位学生 · 点击查看全部</template>
        </div>
      </div>
      <el-empty v-if="!loading && !cards.length" description="暂无咨询" :image-size="70" />
    </div>

    <!-- 卡片详情抽屉：该学生/班级的全部咨询列表，支持直接回复 -->
    <el-drawer v-model="visible" :title="`${view === 'student' ? '学生' : '班级'}咨询 — ${detail?.name || ''}`" size="560px">
      <el-table :data="detail?.items || []" stripe>
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column v-if="view === 'clazz'" label="学生" width="90">
          <template #default="{ row }">{{ row.student ? row.student.name : '-' }}</template>
        </el-table-column>
        <el-table-column label="咨询内容" min-width="150">
          <template #default="{ row }">{{ row.consult.content }}</template>
        </el-table-column>
        <el-table-column label="回复" min-width="150">
          <template #default="{ row }">{{ row.consult.reply || '待回复' }}</template>
        </el-table-column>
        <el-table-column prop="consult.createTime" label="时间" width="150" />
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.consult.status === 0" link type="primary" @click="openReply(row)">回复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="replyVisible" title="回复咨询" width="440px" append-to-body>
      <div class="q">学生：{{ current && current.consult ? current.consult.content : '' }}</div>
      <el-input v-model="reply" type="textarea" :rows="3" placeholder="请输入回复" />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" @click="send">发送</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([])
const loading = ref(false)
const view = ref('student')
const visible = ref(false)
const detail = ref(null)
const replyVisible = ref(false)
const reply = ref('')
const current = ref(null)

const load = async () => {
  loading.value = true
  try { list.value = (await api.teachConsults()) || [] } finally { loading.value = false }
}

/**
 * 双视角分组：按学生聚合（studentId）或按班级聚合（clazzId，无班级归入「一对一/未分班」）；
 * 卡片按咨询条数倒序，每条记录内保持最新在前。
 */
const cards = computed(() => {
  const map = new Map()
  for (const it of list.value) {
    let key, name
    if (view.value === 'student') {
      key = 's' + it.consult.studentId
      name = it.student ? it.student.name : `学生#${it.consult.studentId}`
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
      pending: c.items.filter((it) => it.consult.status === 0).length,
      students: new Set(c.items.map((it) => it.consult.studentId)).size
    }))
    .sort((a, b) => b.items.length - a.items.length)
})

function openDetail(c) {
  detail.value = c
  visible.value = true
}
function openReply(row) {
  current.value = row
  reply.value = ''
  replyVisible.value = true
}
async function send() {
  if (!reply.value) return ElMessage.warning('请输入回复内容')
  await api.teachReply(current.value.consult.id, reply.value)
  ElMessage.success('已回复')
  replyVisible.value = false
  await load()
  // 刷新后重新定位当前卡片，保持抽屉内数据同步
  detail.value = cards.value.find((c) => c.key === detail.value?.key) || null
}

onMounted(load)
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
.pc-meta { display: flex; align-items: center; margin: 8px 0 6px; }
.pc-snip {
  font-size: 13px; color: #475467; line-height: 1.5; height: 40px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.pc-foot { margin-top: 8px; font-size: 12px; color: #98a2b3; }
.q { background: #f5f7fa; border-radius: 6px; padding: 10px; margin-bottom: 12px; font-size: 13px; }
</style>
