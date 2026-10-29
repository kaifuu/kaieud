<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">通知管理</span>
      <div class="actions">
        <el-button v-if="tab === 'notice'" type="primary" @click="openNotice()">新增通知</el-button>
        <el-button v-else type="primary" @click="openConfig()">新增配置</el-button>
      </div>
    </div>

    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane label="通知列表" name="notice" />
        <el-tab-pane label="通知配置" name="config" />
      </el-tabs>
    </div>

    <!-- TAB1 通知列表 -->
    <div v-show="tab === 'notice'" class="panel table-panel" style="height: calc(100% - 110px)">
      <div class="toolbar">
        <el-input v-model="nQuery.keyword" placeholder="通知标题" clearable style="width: 200px" @keyup.enter="nPage.search()">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="nQuery.noticeType" placeholder="类型" clearable style="width: 110px">
          <el-option label="系统" :value="0" /><el-option label="课程" :value="1" /><el-option label="缴费" :value="2" />
        </el-select>
        <el-select v-model="nQuery.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="草稿" :value="0" /><el-option label="已发布" :value="1" />
        </el-select>
        <el-button type="primary" @click="nPage.search()">查询</el-button>
        <el-button @click="doExport">导出</el-button>
      </div>
      <el-table :data="nPage.rows.value" v-loading="nPage.loading.value" stripe height="100%" @sort-change="nPage.onSort">
        <el-table-column type="index" label="序号" width="60" :index="nPage.seq" />
        <el-table-column prop="title" label="标题" min-width="200" sortable="custom" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ ['', '课程', '缴费'][row.noticeType] || '系统' }}</template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'warning', 'danger'][row.level]" effect="light">
              {{ ['普通', '重要', '紧急'][row.level] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接收对象" width="110">
          <template #default="{ row }">{{ targetName(row.target) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="165" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openNotice(row)">编辑</el-button>
            <el-button v-if="row.status === 0" link type="success" @click="publish(row)">发布</el-button>
            <el-popconfirm title="确认删除该通知?" @confirm="removeNotice(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="nPage.pager.total"
                       v-model:current-page="nPage.pager.page" v-model:page-size="nPage.pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="nPage.load" @size-change="nPage.onSizeChange" />
      </div>
    </div>

    <!-- TAB2 通知配置 -->
    <div v-show="tab === 'config'" class="panel table-panel" style="height: calc(100% - 110px)">
      <div class="toolbar">
        <span style="color: #8fa8c4; font-size: 13px">配置各业务事件的站内/短信/邮件通知规则</span>
      </div>
      <el-table :data="configs" v-loading="cLoading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="name" label="配置名称" min-width="140" />
        <el-table-column label="事件类型" width="150">
          <template #default="{ row }">{{ eventTypeText(row.eventType) }}</template>
        </el-table-column>
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">{{ ['站内', '短信', '邮件'][row.channel] }}</template>
        </el-table-column>
        <el-table-column label="接收对象" min-width="180">
          <template #default="{ row }">{{ targetName(row.receivers) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(v) => toggleConfig(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConfig(row)">编辑</el-button>
            <el-popconfirm title="确认删除该配置?" @confirm="removeConfig(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 通知编辑抽屉 -->
    <el-drawer v-model="noticeVisible" :title="noticeForm.id ? '编辑通知' : '新增通知'" size="460px">
      <el-form :model="noticeForm" label-width="90px">
        <el-form-item label="标题" required><el-input v-model="noticeForm.title" /></el-form-item>
        <el-form-item label="内容" required><el-input v-model="noticeForm.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="noticeForm.noticeType" style="width: 100%">
            <el-option label="系统" :value="0" /><el-option label="课程" :value="1" /><el-option label="缴费" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="noticeForm.level" style="width: 100%">
            <el-option label="普通" :value="0" /><el-option label="重要" :value="1" /><el-option label="紧急" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收对象">
          <el-select v-model="noticeForm.target" style="width: 100%">
            <el-option label="全部" value="ALL" /><el-option label="教管人员" value="EDU_ADMIN" />
            <el-option label="班主任" value="CLASS_TEACHER" /><el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveNotice">保存</el-button>
      </template>
    </el-drawer>

    <!-- 配置编辑抽屉 -->
    <el-drawer v-model="configVisible" :title="configForm.id ? '编辑配置' : '新增配置'" size="440px">
      <el-form :model="configForm" label-width="90px">
        <el-form-item label="配置名称" required><el-input v-model="configForm.name" /></el-form-item>
        <el-form-item label="事件类型">
          <el-select v-model="configForm.eventType" style="width: 100%">
            <el-option label="开课提醒" value="CLASS_START" /><el-option label="调课/取消通知" value="COURSE_CANCEL" />
            <el-option label="缴费到期提醒" value="PAY_DUE" /><el-option label="请假审批结果" value="LEAVE_AUDIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="configForm.channel" style="width: 100%">
            <el-option label="站内" :value="0" /><el-option label="短信" :value="1" /><el-option label="邮件" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收对象">
          <el-select v-model="configForm.receivers" multiple style="width: 100%">
            <el-option label="教管人员" value="EDU_ADMIN" /><el-option label="班主任" value="CLASS_TEACHER" />
            <el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="configForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import { exportXlsx } from '../../utils/export'

const tab = ref('notice')
const targetName = (t) => (t || 'ALL').split(',').map((x) => ({
  ALL: '全部', EDU_ADMIN: '教管人员', CLASS_TEACHER: '班主任', TEACHER: '教师', STUDENT: '学生'
}[x] || x)).join('、')
const eventTypeText = (e) => ({
  CLASS_START: '开课提醒', COURSE_CANCEL: '调课/取消通知',
  PAY_DUE: '缴费到期提醒', LEAVE_AUDIT: '请假审批结果'
}[e] || e)

/* TAB1 通知列表 */
const nQuery = reactive({ keyword: '', noticeType: null, status: null })
const nPage = usePage((q) => api.noticePage(q), nQuery)

const noticeVisible = ref(false)
const saving = ref(false)
const noticeForm = ref({})
function openNotice(row) {
  noticeForm.value = row ? { ...row } : { noticeType: 0, level: 0, target: 'ALL', status: 0 }
  noticeVisible.value = true
}
async function saveNotice() {
  const f = noticeForm.value
  if (!f.title || !f.content) return ElMessage.warning('标题和内容不能为空')
  saving.value = true
  try {
    await api.noticeSave(f)
    ElMessage.success('已保存')
    noticeVisible.value = false
    nPage.load()
  } finally { saving.value = false }
}
async function publish(row) {
  await api.noticePublish(row.id)
  ElMessage.success('已发布')
  nPage.load()
}
async function removeNotice(id) {
  await api.noticeDel(id)
  ElMessage.success('已删除')
  nPage.load()
}

/* TAB2 通知配置 */
const configs = ref([])
const cLoading = ref(false)
const configVisible = ref(false)
const configForm = ref({})
async function loadConfigs() {
  cLoading.value = true
  try { configs.value = (await api.configList()) || [] } finally { cLoading.value = false }
}
function openConfig(row) {
  configForm.value = row ? { ...row, receivers: (row.receivers || '').split(',').filter(Boolean) }
                         : { channel: 0, receivers: [] }
  configVisible.value = true
}
async function saveConfig() {
  const f = configForm.value
  if (!f.name || !f.eventType) return ElMessage.warning('名称与事件类型不能为空')
  saving.value = true
  try {
    await api.configSave({ ...f, receivers: (f.receivers || []).join(',') })
    ElMessage.success('已保存')
    configVisible.value = false
    loadConfigs()
  } finally { saving.value = false }
}
async function toggleConfig(row, v) {
  await api.configToggle(row.id, v ? 1 : 0)
  ElMessage.success(v ? '已启用' : '已停用')
  loadConfigs()
}
async function removeConfig(id) {
  await api.configDel(id)
  ElMessage.success('已删除')
  loadConfigs()
}

function doExport() {
  exportXlsx('通知列表', [
    { label: '标题', prop: 'title' },
    { label: '类型', prop: 'noticeType', fmt: (v) => ['', '课程', '缴费'][v] || '系统' },
    { label: '级别', prop: 'level', fmt: (v) => ['普通', '重要', '紧急'][v] },
    { label: '接收对象', prop: 'target' },
    { label: '状态', prop: 'status', fmt: (v) => (v === 1 ? '已发布' : '草稿') },
    { label: '发布时间', prop: 'publishTime' }
  ], nPage.rows.value)
}

function load() {
  if (tab.value === 'notice') nPage.load()
  else loadConfigs()
}
onMounted(load)
</script>
