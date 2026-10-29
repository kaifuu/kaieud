<template>
  <el-card>
    <template #header>学生问题咨询</template>
    <el-table :data="list" stripe v-loading="loading">
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column label="学生" width="110">
        <template #default="{ row }">{{ row.student ? row.student.name : '-' }}</template>
      </el-table-column>
      <el-table-column prop="consult.content" label="咨询内容" min-width="220" />
      <el-table-column label="回复" min-width="220">
        <template #default="{ row }">{{ row.consult.reply || '待回复' }}</template>
      </el-table-column>
      <el-table-column prop="consult.createTime" label="时间" width="170" />
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button v-if="row.consult.status === 0" link type="primary" @click="openReply(row)">回复</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="回复咨询" width="440px">
      <div class="q">学生：{{ current && current.consult ? current.consult.content : '' }}</div>
      <el-input v-model="reply" type="textarea" :rows="3" placeholder="请输入回复" />
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="send">发送</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([])
const loading = ref(false)
const visible = ref(false)
const reply = ref('')
const current = ref(null)

const load = async () => {
  loading.value = true
  try { list.value = (await api.teachConsults()) || [] } finally { loading.value = false }
}

const openReply = (row) => {
  current.value = row
  reply.value = ''
  visible.value = true
}

const send = async () => {
  if (!reply.value) return ElMessage.warning('请输入回复内容')
  await api.teachReply(current.value.consult.id, reply.value)
  ElMessage.success('已回复')
  visible.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.q { background: #f5f7fa; border-radius: 6px; padding: 10px; margin-bottom: 12px; font-size: 13px; }
</style>
