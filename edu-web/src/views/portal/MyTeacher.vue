<template>
  <div>
    <el-row :gutter="14">
      <el-col :span="8" v-for="item in list" :key="item.teacher.id">
        <el-card class="t-card">
          <div class="head">
            <el-avatar :size="46">{{ item.teacher.name.charAt(0) }}</el-avatar>
            <div>
              <div class="name">{{ item.teacher.name }}</div>
              <div class="sub">{{ item.teacher.title }} · {{ item.teacher.subject }}</div>
            </div>
          </div>
          <div class="line">联系方式：{{ item.teacher.phone }}</div>
          <div class="line intro">{{ item.teacher.intro }}</div>
          <div class="line">课程：{{ item.course ? item.course.name : '' }}（剩余 {{ item.remainHours }} 课时）</div>
          <el-button size="small" type="primary" @click="openConsult(item)">咨询</el-button>
          <el-button size="small" type="warning" @click="openEval(item)">评价</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 14px">
      <template #header>我的咨询记录</template>
      <el-table :data="consults" stripe>
      <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="content" label="咨询内容" min-width="200" />
        <el-table-column label="老师回复" min-width="200">
          <template #default="{ row }">{{ row.reply || '待回复' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
    </el-card>

    <el-dialog v-model="consultVisible" title="发起咨询" width="420px">
      <el-input v-model="consultContent" type="textarea" :rows="3" placeholder="请输入您的问题" />
      <template #footer>
        <el-button @click="consultVisible = false">取消</el-button>
        <el-button type="primary" @click="sendConsult">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evalVisible" title="评价老师" width="420px">
      <el-rate v-model="evalScore" style="margin-bottom: 12px" />
      <el-input v-model="evalContent" type="textarea" :rows="3" placeholder="说说你的上课感受" />
      <template #footer>
        <el-button @click="evalVisible = false">取消</el-button>
        <el-button type="primary" @click="sendEval">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const list = ref([])
const consults = ref([])
const consultVisible = ref(false)
const consultContent = ref('')
const evalVisible = ref(false)
const evalScore = ref(5)
const evalContent = ref('')
const current = ref(null)

const openConsult = (item) => {
  current.value = item
  consultContent.value = ''
  consultVisible.value = true
}

const sendConsult = async () => {
  if (!consultContent.value) return ElMessage.warning('请输入问题')
  await api.portalConsult({ teacherId: current.value.teacher.id, content: consultContent.value })
  ElMessage.success('已发送，等待老师回复')
  consultVisible.value = false
  load()
}

const openEval = (item) => {
  current.value = item
  evalScore.value = 5
  evalContent.value = ''
  evalVisible.value = true
}

const sendEval = async () => {
  await api.portalEvaluation({
    teacherId: current.value.teacher.id,
    courseId: current.value.course ? current.value.course.id : null,
    score: evalScore.value,
    content: evalContent.value
  })
  ElMessage.success('感谢您的评价')
  evalVisible.value = false
}

const load = async () => {
  list.value = (await api.portalMyTeachers()) || []
  consults.value = (await api.portalMyConsults()) || []
}

onMounted(load)
</script>

<style scoped>
.t-card { margin-bottom: 14px; }
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.name { font-weight: bold; font-size: 16px; }
.sub { color: #909399; font-size: 13px; }
.line { margin: 6px 0; font-size: 13px; }
.intro { color: #666; }
</style>
