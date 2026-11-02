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
          <el-button size="small" type="primary" @click="openConsult(item.teacher, item.course, null)">咨询</el-button>
          <el-button size="small" type="warning" @click="openEval(item)">评价</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 14px">
      <template #header>我的咨询记录（选择签约班级，向该班老师咨询问题）</template>

      <!-- 签约班级切换条：班课按班级、一对一按课程；点击筛选该老师的咨询记录 -->
      <div class="clazz-bar">
        <div class="clazz-chip" :class="{ active: activeClass === null }" @click="activeClass = null">
          <span class="chip-name">全部</span>
          <span class="chip-count">{{ consultPager.total }}条</span>
        </div>
        <div v-for="(c, i) in classes" :key="i" class="clazz-chip"
             :class="{ active: activeClass === c }" @click="activeClass = c">
          <span class="chip-name">{{ classLabel(c) }}</span>
          <span class="chip-count">{{ c.teacher ? c.teacher.name : '' }}</span>
        </div>
        <el-empty v-if="!classes.length" description="暂无签约班级（可在选课中心报名）" :image-size="50" />
      </div>

      <!-- 选中班级后：向该班老师发起咨询 -->
      <div v-if="activeClass" class="consult-actions">
        <el-button size="small" type="primary"
                   @click="openConsult(activeClass.teacher, activeClass.course, activeClass)">
          向 {{ activeClass.teacher ? activeClass.teacher.name : '' }} 老师咨询本班问题
        </el-button>
        <span class="remain-tip" v-if="activeClass.remainHours != null">剩余课时 {{ activeClass.remainHours }} 节</span>
      </div>

      <!-- 咨询记录：服务端分页 + 按老师筛选（切换条联动） -->
      <el-table :data="consultRows" v-loading="consultLoading" stripe>
        <el-table-column type="index" label="序号" width="60" :index="consultSeq" />
        <el-table-column label="咨询班级/课程" min-width="150">
          <template #default="{ row }">{{ row.className || '-' }}</template>
        </el-table-column>
        <el-table-column label="老师" width="90">
          <template #default="{ row }">{{ row.teacherName }}</template>
        </el-table-column>
        <el-table-column label="咨询内容" min-width="200">
          <template #default="{ row }">
            <div class="consult-cell">
              <span class="consult-txt">{{ row.lastMsg || row.consult.content }}</span>
              <el-tag v-if="row.msgCount > 2" size="small" type="primary" effect="plain">{{ row.msgCount }}条对话</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="老师回复" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.consult.reply">{{ row.consult.reply }}</span>
            <el-tag v-else size="small" type="warning">待回复</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="咨询时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.consult.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openThread(row)">对话/追问</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="consultPager.total"
                       v-model:current-page="consultPager.page" v-model:page-size="consultPager.size"
                       :page-sizes="[10, 20, 50]" @current-change="consultLoad" @size-change="consultSizeChange" />
      </div>
    </el-card>

    <el-dialog v-model="consultVisible" title="发起咨询" width="460px">
      <el-descriptions :column="1" size="small" border style="margin-bottom: 12px">
        <el-descriptions-item label="咨询对象">
          {{ consultTarget.teacher ? consultTarget.teacher.name : '' }}
          <span v-if="consultTarget.label">（{{ consultTarget.label }}）</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-input v-model="consultContent" type="textarea" :rows="3" maxlength="500" show-word-limit
                placeholder="请输入您的问题，老师收到后会尽快回复" />
      <template #footer>
        <el-button @click="consultVisible = false">取消</el-button>
        <el-button type="primary" @click="sendConsult">发送</el-button>
      </template>
    </el-dialog>

    <!-- 咨询对话：完整追问/回复消息流，可继续发问 -->
    <el-dialog v-model="threadVisible" :title="`咨询对话 · ${threadRow ? threadRow.teacherName : ''}`" width="520px">
      <div class="thread-box" v-loading="threadLoading">
        <div v-for="(m, i) in threadRows" :key="i" class="msg" :class="m.sender === 0 ? 'mine' : 'theirs'">
          <div class="bubble">
            <div class="who">{{ m.sender === 0 ? '我' : '老师' }}</div>
            <div class="txt">{{ m.content }}</div>
            <div class="time">{{ m.time || '-' }}</div>
          </div>
        </div>
        <el-empty v-if="!threadRows.length && !threadLoading" description="暂无消息" :image-size="50" />
      </div>
      <el-input v-model="askContent" type="textarea" :rows="3" maxlength="500" show-word-limit
                placeholder="针对该问题继续向老师提问…" style="margin-top: 12px" />
      <template #footer>
        <el-button @click="threadVisible = false">关闭</el-button>
        <el-button type="primary" :loading="askSending" @click="sendAsk">发送追问</el-button>
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
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { usePage } from '../../composables/usePage'

const list = ref([])
const classes = ref([])
const activeClass = ref(null)
const consultVisible = ref(false)
const consultContent = ref('')
const consultTarget = ref({ teacher: null, label: '' })
const evalVisible = ref(false)
const evalScore = ref(5)
const evalContent = ref('')
const current = ref(null)

/** 签约班级标签：班课取班级名，一对一取课程名 */
const classLabel = (c) => (c.clazz ? c.clazz.name : `一对一 · ${c.course ? c.course.name : ''}`)

/* ===== 咨询记录：服务端分页，签约班级切换条按老师筛选 ===== */
const consultFilters = reactive({ teacherId: undefined })
const { loading: consultLoading, rows: consultRows, pager: consultPager, seq: consultSeq,
        onSizeChange: consultSizeChange, search: consultSearch, load: consultLoad } =
  usePage((q) => api.portalMyConsults(q), consultFilters)

// 切换班级（或复位全部）时按该班老师过滤并回到第一页
watch(activeClass, (c) => {
  consultFilters.teacherId = c?.teacher?.id || undefined
  consultSearch()
})

/* ===== 咨询对话：完整追问/回复消息流，可多次发问 ===== */
const threadVisible = ref(false)
const threadLoading = ref(false)
const threadRows = ref([])
const threadRow = ref(null)
const askContent = ref('')
const askSending = ref(false)

async function openThread(row) {
  threadRow.value = row
  askContent.value = ''
  threadVisible.value = true
  await loadThread(row.consult.id)
}

async function loadThread(id) {
  threadLoading.value = true
  try {
    threadRows.value = (await api.portalConsultMessages(id)) || []
  } finally {
    threadLoading.value = false
  }
}

const sendAsk = async () => {
  if (!askContent.value) return ElMessage.warning('请输入问题')
  askSending.value = true
  try {
    await api.portalConsultAsk(threadRow.value.consult.id, askContent.value)
    ElMessage.success('追问已发送，等待老师回复')
    askContent.value = ''
    await loadThread(threadRow.value.consult.id)
    consultLoad()
  } finally {
    askSending.value = false
  }
}

/** 咨询入口：老师卡片（带课程）或签约班级切换条（带班级） */
function openConsult(teacher, course, clazz) {
  consultTarget.value = { teacher, label: clazz ? classLabel(clazz) : (course ? course.name : '') }
  consultContent.value = ''
  consultVisible.value = true
}

const sendConsult = async () => {
  if (!consultContent.value) return ElMessage.warning('请输入问题')
  await api.portalConsult({ teacherId: consultTarget.value.teacher.id, content: consultContent.value })
  ElMessage.success('已发送，等待老师回复')
  consultVisible.value = false
  consultLoad()
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

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  list.value = (await api.portalMyTeachers()) || []
  classes.value = (await api.portalMyClasses()) || []
  // 已选中的班级不在最新签约列表中时复位为全部（watch 会触发咨询列表刷新）
  if (activeClass.value && !classes.value.includes(activeClass.value)) activeClass.value = null
  else consultLoad()
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
/* 签约班级切换条：胶囊式分组标签，选中态主色点亮（与教师端"我的学生"同款） */
.clazz-bar {
  display: flex; flex-wrap: wrap; gap: 8px;
  padding: 2px 0 14px;
}
.clazz-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 14px; border-radius: 14px;
  border: 1px solid #e4edf7; background: linear-gradient(180deg, #ffffff, #f7fafd);
  font-size: 13px; color: #475467; cursor: pointer; white-space: nowrap;
  transition: all 0.2s ease;
}
.clazz-chip:hover { color: #155EEF; border-color: rgba(21, 94, 239, 0.45); }
.clazz-chip.active {
  color: #fff; border-color: transparent;
  background: linear-gradient(135deg, #155EEF, #5B8DEF);
  box-shadow: 0 3px 10px rgba(21, 94, 239, 0.22);
}
.chip-name { font-weight: 600; }
.chip-count { font-size: 12px; opacity: 0.85; }
.consult-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.remain-tip { font-size: 12px; color: #98a2b3; }
/* 咨询内容单元格：最新消息省略号 + 对话条数标签 */
.consult-cell { display: flex; align-items: center; gap: 6px; min-width: 0; }
.consult-txt { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pager-row { display: flex; justify-content: flex-end; margin-top: 12px; }
/* 咨询对话气泡：学生右侧主色、老师左侧灰色（聊天流） */
.thread-box {
  height: 320px; overflow-y: auto; padding: 4px 8px;
  background: #f7f9fc; border-radius: 6px;
}
.thread-box .msg { display: flex; margin: 8px 0; }
.thread-box .msg.mine { justify-content: flex-end; }
.thread-box .bubble {
  max-width: 78%; padding: 8px 12px; border-radius: 8px;
  background: #fff; box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.thread-box .msg.mine .bubble { background: linear-gradient(135deg, #155EEF, #5B8DEF); }
.thread-box .who { font-size: 11px; color: #98a2b3; margin-bottom: 2px; }
.thread-box .msg.mine .who { color: rgba(255, 255, 255, 0.75); }
.thread-box .txt { font-size: 13px; color: #1d2939; white-space: pre-wrap; word-break: break-word; }
.thread-box .msg.mine .txt { color: #fff; }
.thread-box .time { font-size: 11px; color: #b0b8c4; margin-top: 4px; }
.thread-box .msg.mine .time { color: rgba(255, 255, 255, 0.68); }
</style>
