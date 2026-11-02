<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">通知管理</span>
      <div class="actions">
        <el-button v-if="tab === 'notice' && canManage" type="primary" @click="openNotice()">新增通知</el-button>
        <el-button v-else-if="tab === 'config' && canManage" type="primary" @click="openConfig()">新增配置</el-button>
      </div>
    </div>

    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane label="我收到的通知" name="notice" />
        <el-tab-pane v-if="canManage" label="通知配置" name="config" />
        <el-tab-pane label="消息历史" name="history" />
      </el-tabs>
    </div>

    <!-- TAB1 我收到的通知：后端按 角色接收对象 + 指定用户 过滤，他人定向消息不可见 -->
    <div v-show="tab === 'notice'" class="panel table-panel tabs-fill">
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
        <el-button @click="markAllRead">全部已读</el-button>
        <el-button v-if="canManage" @click="doExport">导出</el-button>
        <span style="color: #8fa8c4; font-size: 13px">仅展示发送给您（按角色或指定用户）的通知</span>
      </div>
      <el-table :data="nPage.rows.value" v-loading="nPage.loading.value" stripe height="100%" @sort-change="nPage.onSort">
        <el-table-column type="index" label="序号" width="60" :index="nPage.seq" />
        <el-table-column prop="title" label="标题" min-width="200" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'unread-title': !readSet.has(row.id) }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="readSet.has(row.id) ? 'info' : 'danger'" effect="light">
              {{ readSet.has(row.id) ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column label="接收对象" width="130">
          <template #default="{ row }">{{ targetName(row.target) }}</template>
        </el-table-column>
        <el-table-column label="指定用户" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetUsers ? `${row.targetUsers.split(',').filter(Boolean).length} 人` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="165" />
        <el-table-column label="操作" :width="canManage ? 240 : 90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewNotice(row)">查看</el-button>
            <template v-if="canManage">
            <el-button link type="primary" @click="openNotice(row)">编辑</el-button>
            <el-button v-if="row.status === 0" link type="success" @click="publish(row)">发布</el-button>
            <el-popconfirm title="确认删除该通知?" @confirm="removeNotice(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="nPage.pager.total"
                       v-model:current-page="nPage.pager.page" v-model:page-size="nPage.pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="nPage.load" @size-change="nPage.onSizeChange" />
      </div>
    </div>

    <!-- TAB2 通知配置（仅 sys:notify 权限可见） -->
    <div v-show="tab === 'config'" class="panel table-panel tabs-fill">
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

    <!-- TAB3 消息历史：全部已发布消息全量记录，只读（仅查询/导出，不能修改删除） -->
    <div v-show="tab === 'history'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-input v-model="hQuery.keyword" placeholder="通知标题" clearable style="width: 200px" @keyup.enter="hPage.search()">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="hQuery.noticeType" placeholder="类型" clearable style="width: 110px">
          <el-option label="系统" :value="0" /><el-option label="课程" :value="1" /><el-option label="缴费" :value="2" />
        </el-select>
        <el-select v-model="hQuery.level" placeholder="级别" clearable style="width: 110px">
          <el-option label="普通" :value="0" /><el-option label="重要" :value="1" /><el-option label="紧急" :value="2" />
        </el-select>
        <el-button type="primary" @click="hPage.search()">查询</el-button>
        <el-button @click="doHistoryExport">导出</el-button>
        <span style="color: #8fa8c4; font-size: 13px">系统发出过的所有消息（只读，仅可查询与导出）</span>
      </div>
      <el-table :data="hPage.rows.value" v-loading="hPage.loading.value" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="hPage.seq" />
        <el-table-column prop="title" label="标题" min-width="190" show-overflow-tooltip />
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
        <el-table-column label="接收对象" width="130">
          <template #default="{ row }">{{ row.targetName }}</template>
        </el-table-column>
        <el-table-column prop="targetUserNames" label="指定用户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createBy" label="发布人" width="100" />
        <el-table-column prop="publishTime" label="发布时间" width="165" />
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="hPage.pager.total"
                       v-model:current-page="hPage.pager.page" v-model:page-size="hPage.pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="hPage.load" @size-change="hPage.onSizeChange" />
      </div>
    </div>

    <!-- 通知详情：点击查看内容，未读自动转已读 -->
    <el-dialog v-model="viewVisible" title="通知详情" width="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ viewing.title }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag size="small" :type="['info', 'warning', 'danger'][viewing.level]" effect="light">
            {{ ['普通', '重要', '紧急'][viewing.level] }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ (viewing.publishTime || '').replace('T', ' ').slice(0, 16) }}</el-descriptions-item>
        <el-descriptions-item label="内容">
          <div style="white-space: pre-wrap; line-height: 1.8">{{ viewing.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="viewVisible = false">关闭</el-button>
      </template>
    </el-dialog>

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
            <el-option label="全部" value="ALL" />
            <el-option v-for="o in userTypeOptions" :key="o.value" :label="o.label" :value="String(o.value)" />
          </el-select>
          <div class="form-tip">接收对象与用户管理的用户类型一致（系统字典 user_type，可在字典管理维护）</div>
        </el-form-item>
        <el-form-item label="指定用户">
          <el-select v-model="noticeForm.targetUserIds" multiple filterable clearable
                     placeholder="按用户定向发送（如仅A教务可见），可搜索" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :value="u.id"
                       :label="`${u.realName}（${u.username}）`" />
          </el-select>
          <div class="form-tip">指定后仅这些用户可见；与接收对象为"或"关系，两者都不满足的人看不到</div>
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
            <el-option v-for="o in userTypeOptions" :key="o.value" :label="o.label" :value="String(o.value)" />
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { useUserStore } from '../../store/user'
import { useDictStore } from '../../store/dict'
import { usePage } from '../../composables/usePage'
import { exportXlsx } from '../../utils/export'

const store = useUserStore()
const dictStore = useDictStore()
/** 通知维护（新增/编辑/发布/删除/配置）仅 sys:notify 权限；普通用户只看自己收到的与消息历史 */
const canManage = computed(() => store.hasPerm('sys:notify'))

/** 接收对象候选：与用户管理的用户类型一致（系统字典 user_type） */
const userTypeOptions = computed(() => dictStore.options('user_type') || [])

const tab = ref('notice')
/** 接收对象显示名：全部/角色编码(历史)/用户类型字典值 */
const ROLE_NAMES = {
  ALL: '全部', EDU_ADMIN: '教管人员', CLASS_TEACHER: '班主任', TEACHER: '教师', STUDENT: '学生',
  SUPER_ADMIN: '系统管理员', CAMPUS_PRINCIPAL: '校区校长', CONSULTANT: '校区咨询师',
  ACADEMIC_MANAGER: '校区学管师', FINANCE: '财务'
}
const targetName = (t) => (t || 'ALL').split(',').map((x) => {
  const s = x.trim()
  if (!s || s === 'ALL') return '全部'
  if (ROLE_NAMES[s]) return ROLE_NAMES[s]
  const lb = dictStore.label('user_type', s)
  return lb && lb !== s ? lb : s
}).join('、')
const eventTypeText = (e) => ({
  CLASS_START: '开课提醒', COURSE_CANCEL: '调课/取消通知',
  PAY_DUE: '缴费到期提醒', LEAVE_AUDIT: '请假审批结果'
}[e] || e)

/* TAB1 我收到的通知 */
const nQuery = reactive({ keyword: '', noticeType: null, status: null })
const nPage = usePage((q) => api.noticePage(q), nQuery)

/* 已读/未读：readSet 为当前用户已读通知ID集合 */
const readSet = ref(new Set())
async function loadReadIds() {
  try {
    const ids = (await api.noticeReadIds()) || []
    readSet.value = new Set(ids)
  } catch (e) { /* 忽略 */ }
}
const viewVisible = ref(false)
const viewing = ref({})
async function viewNotice(row) {
  viewing.value = row
  viewVisible.value = true
  // 查看内容即已读（未读 -> 已读）
  if (!readSet.value.has(row.id)) {
    readSet.value = new Set(readSet.value).add(row.id)
    try {
      await api.noticeRead(row.id)
      window.dispatchEvent(new CustomEvent('notice-read'))   // 通知布局刷新铃铛角标
    } catch (e) { /* 静默 */ }
  }
}
async function markAllRead() {
  const n = await api.noticeReadAll()
  ElMessage.success(`已将 ${n ?? 0} 条通知标记为已读`)
  await loadReadIds()
  window.dispatchEvent(new CustomEvent('notice-read'))
}

const noticeVisible = ref(false)
const saving = ref(false)
const noticeForm = ref({})
const userOptions = ref([]) // 指定接收用户候选

/** 打开通知编辑：加载用户候选（指定用户定向发送用） */
async function openNotice(row) {
  noticeForm.value = row
    ? { ...row, targetUserIds: (row.targetUsers || '').split(',').filter(Boolean).map(Number) }
    : { noticeType: 0, level: 0, target: 'ALL', targetUserIds: [], status: 0 }
  noticeVisible.value = true
  if (!userOptions.value.length) {
    try {
      const d = await api.userPage({ page: 1, size: 500 })
      userOptions.value = d.records || []
    } catch (e) { /* 忽略，候选为空 */ }
  }
}
async function saveNotice() {
  const f = noticeForm.value
  if (!f.title || !f.content) return ElMessage.warning('标题和内容不能为空')
  saving.value = true
  try {
    const { targetUserIds, ...rest } = f
    await api.noticeSave({ ...rest, targetUsers: (targetUserIds || []).join(',') })
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
    await api.notifyConfigSave({ ...f, receivers: (f.receivers || []).join(',') })
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

/* TAB3 消息历史（只读） */
const hQuery = reactive({ keyword: '', noticeType: null, level: null })
const hPage = usePage((q) => api.noticeHistory(q), hQuery)

function doExport() {
  exportXlsx('我收到的通知', [
    { label: '标题', prop: 'title' },
    { label: '类型', prop: 'noticeType', fmt: (v) => ['', '课程', '缴费'][v] || '系统' },
    { label: '级别', prop: 'level', fmt: (v) => ['普通', '重要', '紧急'][v] },
    { label: '接收对象', prop: 'target' },
    { label: '状态', prop: 'status', fmt: (v) => (v === 1 ? '已发布' : '草稿') },
    { label: '发布时间', prop: 'publishTime' }
  ], nPage.rows.value)
}
function doHistoryExport() {
  exportXlsx('消息历史', [
    { label: '标题', prop: 'title' },
    { label: '类型', prop: 'noticeType', fmt: (v) => ['', '课程', '缴费'][v] || '系统' },
    { label: '级别', prop: 'level', fmt: (v) => ['普通', '重要', '紧急'][v] },
    { label: '接收对象', prop: 'targetName' },
    { label: '指定用户', prop: 'targetUserNames' },
    { label: '发布人', prop: 'createBy' },
    { label: '发布时间', prop: 'publishTime' }
  ], hPage.rows.value)
}

function load() {
  if (tab.value === 'notice') { nPage.load(); loadReadIds() }
  else if (tab.value === 'config') loadConfigs()
  else hPage.load()
}
onMounted(() => {
  dictStore.load()
  load()
})
</script>

<style scoped>
.form-tip { font-size: 12px; color: #8fa8c4; line-height: 1.5; margin-top: 4px; }
</style>
