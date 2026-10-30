<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">数据导入</span>
    </div>
    <div class="panel" style="padding: 20px; max-width: 760px">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px"
                title="导入校宝存量学员数据：下载标准模板 → 填写数据 → 上传xlsx → 系统逐行校验导入" />
      <el-steps :active="step" align-center style="margin-bottom: 20px">
        <el-step title="下载模板" />
        <el-step title="上传文件" />
        <el-step title="导入结果" />
      </el-steps>

      <div v-if="step === 0">
        <el-button type="primary" @click="downloadTemplate">下载标准模板</el-button>
        <el-button @click="step = 1">下一步</el-button>
      </div>

      <div v-else-if="step === 1">
        <input type="file" accept=".xlsx,.xls" @change="onFile" style="margin-bottom: 16px" />
        <div v-if="parsedRows.length" style="margin-bottom: 16px">
          解析出 <b style="color:#155EEF">{{ parsedRows.length }}</b> 条学员数据，预览前5条：
          <el-table :data="parsedRows.slice(0, 5)" size="small" stripe style="margin-top: 8px">
            <el-table-column prop="name" label="姓名" />
            <el-table-column prop="gender" label="性别">
              <template #default="{ row }">{{ row.gender === 2 ? '女' : '男' }}</template>
            </el-table-column>
            <el-table-column prop="grade" label="年级" />
            <el-table-column prop="school" label="学校" />
            <el-table-column prop="parentPhone" label="家长电话" />
            <el-table-column prop="advisor" label="归属顾问" />
          </el-table>
        </div>
        <el-button :disabled="!parsedRows.length" type="primary" :loading="importing" @click="doImport">开始导入</el-button>
        <el-button @click="step = 0">上一步</el-button>
      </div>

      <div v-else>
        <el-result icon="success" :title="`导入完成：成功 ${result?.success || 0} 条，失败 ${result?.failed?.length || 0} 条`">
          <template #extra>
            <el-button @click="step = 0">继续导入</el-button>
          </template>
        </el-result>
        <div v-if="result?.failed?.length" class="panel" style="padding: 12px; max-height: 300px; overflow: auto">
          <div class="panel-title">失败清单（含原因）</div>
          <div v-for="(f, i) in result.failed" :key="i" style="font-size: 13px; color: #d9907f; padding: 3px 0">
            {{ f }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as XLSX from 'xlsx'
import api from '../../api'

const step = ref(0)
const parsedRows = ref([])
const importing = ref(false)
const result = ref(null)

/** 下载标准模板（生成示例xlsx） */
function downloadTemplate() {
  const ws = XLSX.utils.json_to_sheet([
    { 姓名: '张三', 性别: '男', 年级: '五年级', 学校: '实验小学', 家长电话: '13800000001', 归属顾问: '李顾问' }
  ])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '学员模板')
  XLSX.writeFile(wb, '学员导入模板.xlsx')
}

/** 解析上传文件 */
function onFile(e) {
  const file = e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    const wb = XLSX.read(ev.target.result, { type: 'array' })
    const ws = wb.Sheets[wb.SheetNames[0]]
    const raw = XLSX.utils.sheet_to_json(ws)
    parsedRows.value = raw.map((r) => ({
      name: r['姓名'],
      gender: r['性别'] === '女' ? 2 : 1,
      grade: r['年级'],
      school: r['学校'],
      parentPhone: String(r['家长电话'] ?? ''),
      advisor: r['归属顾问']
    })).filter((r) => r.name)
    if (!parsedRows.value.length) ElMessage.warning('未解析到有效数据，请检查模板格式')
  }
  reader.readAsArrayBuffer(file)
}

/** 提交导入 */
async function doImport() {
  importing.value = true
  try {
    result.value = (await api.importStudents(parsedRows.value)) || {}
    step.value = 2
  } finally { importing.value = false }
}
</script>
