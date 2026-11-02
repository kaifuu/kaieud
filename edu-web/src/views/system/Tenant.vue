<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">平台运营</span>
      <div class="actions" v-if="tab === 'corp'">
        <el-button type="primary" @click="openEdit()">新增企业</el-button>
      </div>
      <div class="actions" v-if="tab === 'notice'">
        <el-button type="primary" @click="openNotice()">发布公告</el-button>
      </div>
    </div>

    <!-- 平台运营视图：企业管理 / 品牌与广告 / 通知公告（系统字典由各企业自维护） -->
    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="企业管理" name="corp" />
        <el-tab-pane label="品牌与广告" name="brand" />
        <el-tab-pane label="通知公告" name="notice" />
      </el-tabs>
    </div>

    <!-- TAB1 企业管理 -->
    <div v-show="tab === 'corp'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="企业名称 / 编码" clearable style="width: 200px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <el-button @click="openRecycle">回收站</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="tenant" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="企业名称" min-width="150" sortable="custom" />
        <el-table-column v-if="cols.code" prop="code" label="编码" min-width="110" sortable="custom" />
        <el-table-column v-if="cols.adminUsername" label="管理员账号" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.adminUsername" size="small" effect="light">{{ row.adminUsername }}</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">未开通</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.contactPerson" prop="contactPerson" label="联系人" width="110" />
        <el-table-column v-if="cols.contactPhone" prop="contactPhone" label="联系电话" width="140" />
        <el-table-column v-if="cols.createTime" prop="createTime" label="创建时间" width="170" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'"
                       @click="toggle(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
            <el-popconfirm title="确认删除该租户? 仅停用其全部用户并移入回收站（数据保留，可恢复）" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- TAB2 品牌与广告（嵌入基础设置页，隐藏其页头） -->
    <div v-show="tab === 'brand'" class="embed-view tabs-fill">
      <BasicSetting class="embed-page" />
    </div>

    <!-- TAB4 通知公告：平台向全部企业用户发布系统升级等公告（tenant_id=0，全员可见） -->
    <div v-show="tab === 'notice'" class="panel table-panel tabs-fill">
      <div class="toolbar">
        <el-input v-model="noticeQuery.keyword" placeholder="公告标题" clearable style="width: 200px"
                  @keyup.enter="noticePage.search()">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="noticeQuery.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="草稿" :value="0" /><el-option label="已发布" :value="1" />
        </el-select>
        <el-button type="primary" @click="noticePage.search()">查询</el-button>
        <span style="color: #8fa8c4; font-size: 13px">平台公告对全部企业的用户可见（铃铛/通知管理自动展示）</span>
      </div>
      <el-table :data="noticePage.rows.value" v-loading="noticePage.loading.value" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" :index="noticePage.seq" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
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
          <template #default="{ row }">
            {{ (row.target || 'ALL').split(',').map((x) => ({ ALL: '全部', EDU_ADMIN: '教管人员', CLASS_TEACHER: '班主任',
              TEACHER: '教师', STUDENT: '学生' }[x.trim()] || x.trim())).join('、') }}
          </template>
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
            <el-button v-if="row.status === 0" link type="success" @click="publishNotice(row)">发布</el-button>
            <el-popconfirm title="确认删除该公告?" @confirm="delNotice(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, prev, pager, next" :total="noticePage.pager.total"
                       v-model:current-page="noticePage.pager.page" :page-size="noticePage.pager.size"
                       @current-change="noticePage.load" />
      </div>
    </div>

    <!-- 公告编辑抽屉 -->
    <el-drawer v-model="noticeVisible" :title="noticeForm.id ? '编辑公告' : '发布公告'" size="460px">
      <el-form :model="noticeForm" label-width="90px">
        <el-form-item label="标题" required><el-input v-model="noticeForm.title" maxlength="60" /></el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="noticeForm.content" type="textarea" :rows="5" maxlength="500"
                    placeholder="如：系统将于今晚 23:00-24:00 升级维护，期间暂停访问" />
        </el-form-item>
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
            <el-option label="全部（所有企业用户）" value="ALL" />
            <el-option label="教管人员" value="EDU_ADMIN" />
            <el-option label="班主任" value="CLASS_TEACHER" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="立即发布">
          <el-switch v-model="noticeForm.publishNow" active-text="保存后立即对全员可见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" :loading="noticeSaving" @click="saveNotice">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="visible" :title="form.id ? '编辑企业' : '新增企业'" size="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="企业名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="企业编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 HQ，该企业用户登录时填写" />
          <div class="form-tip">创建企业后自动开通整套功能，并生成该企业的管理员账号 admin（初始密码 123456）；
            企业用户登录时需填写本企业编码</div>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="form.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="留空 = 永久有效" style="width: 100%" />
          <div class="form-tip">过期后该租户所有用户将无法登录与访问</div>
        </el-form-item>
        <el-form-item label="套餐编码"><el-input v-model="form.packageCode" maxlength="32" placeholder="如：标准版 / 专业版" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 新增租户提示：创建即自动开通整套功能与管理员账号 -->

    <!-- 回收站：软删除的租户可恢复（用户重新启用，默认数据幂等补齐）；物理删除需超级秘钥 -->
    <el-dialog v-model="recycleVisible" title="企业回收站" width="720px">
      <el-tabs v-model="recycleTab">
        <el-tab-pane label="已删除企业" name="list">
          <el-table :data="recycleRows" v-loading="recycleLoading" size="small" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="企业名称" min-width="120" />
            <el-table-column prop="code" label="编码" width="90" />
            <el-table-column label="删除时间" width="150">
              <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button link type="primary" @click="restore(row)">恢复</el-button>
                <el-button link type="danger" @click="purge(row)">物理删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!recycleRows.length && !recycleLoading" description="回收站为空" :image-size="60" />
        </el-tab-pane>
        <el-tab-pane label="清除历史" name="logs">
          <el-table :data="purgeLogs" v-loading="purgeLogLoading" size="small" stripe>
            <el-table-column prop="createTime" label="时间" width="160">
              <template #default="{ row }">{{ fmt(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="140" show-overflow-tooltip />
            <el-table-column label="企业" width="130">
              <template #default="{ row }">{{ row.tenantName }}(#{{ row.tenantId }})</template>
            </el-table-column>
            <el-table-column prop="operation" label="操作" width="120" />
            <el-table-column prop="detail" label="清除明细" min-width="220" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!purgeLogs.length && !purgeLogLoading" description="暂无物理删除记录" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import BasicSetting from './BasicSetting.vue'

/** 平台运营视图：企业管理 / 品牌与广告 / 通知公告 */
const tab = ref('corp')

/* ===== 通知公告（平台公告，tenant_id=0，全员可见） ===== */
const noticeQuery = reactive({ keyword: '', status: null })
const noticePage = usePage((q) => api.noticePlatformPage(q), noticeQuery)
const noticeVisible = ref(false)
const noticeSaving = ref(false)
const noticeForm = ref({})
function openNotice(row) {
  noticeForm.value = row
    ? { ...row, publishNow: row.status === 1 }
    : { noticeType: 0, level: 1, target: 'ALL', publishNow: true }
  noticeVisible.value = true
}
async function saveNotice() {
  const f = noticeForm.value
  if (!f.title || !f.content) return ElMessage.warning('标题和内容不能为空')
  noticeSaving.value = true
  try {
    await api.noticeSave({ ...f, status: f.publishNow ? 1 : 0 })
    ElMessage.success(f.publishNow ? '公告已发布，全员可见' : '公告已存为草稿')
    noticeVisible.value = false
    noticePage.load()
  } finally { noticeSaving.value = false }
}
async function publishNotice(row) {
  await api.noticePublish(row.id)
  ElMessage.success('已发布')
  noticePage.load()
}
async function delNotice(id) {
  await api.noticeDel(id)
  ElMessage.success('已删除')
  noticePage.load()
}

/** 切到通知公告TAB时懒加载公告列表 */
let noticeLoaded = false
function onTabChange(t) {
  if (t === 'notice' && !noticeLoaded) {
    noticeLoaded = true
    noticePage.load()
  }
}

const query = reactive({ keyword: '' })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.tenantPage(q), query)

const allColumns = [
  { prop: 'name', label: '企业名称' }, { prop: 'code', label: '编码' },
  { prop: 'adminUsername', label: '管理员账号' },
  { prop: 'contactPerson', label: '联系人' }, { prop: 'contactPhone', label: '联系电话' },
  { prop: 'createTime', label: '创建时间' }
]
const cols = reactive({ name: true, code: true, adminUsername: true, contactPerson: true,
  contactPhone: true, createTime: true })
const onCols = (c) => Object.assign(cols, c)

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

const visible = ref(false)
const saving = ref(false)
const form = ref({})

function openEdit(row) {
  form.value = row ? { ...row } : { status: 1 }
  visible.value = true
}
async function save() {
  if (!form.value.name || !form.value.code) return ElMessage.warning('企业名称与编码不能为空')
  saving.value = true
  try {
    if (form.value.id) await api.tenantUpdate(form.value)
    else await api.tenantSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.tenantDel(id)
  ElMessage.success('已删除')
  load()
}

/** 启用/禁用：禁用后该租户所有用户立即无法登录与访问 */
async function toggle(row) {
  const enable = row.status !== 1
  await api.tenantToggle(row.id, enable)
  ElMessage.success(enable ? '已启用' : '已禁用，该租户用户立即无法访问')
  load()
}

/* ===== 回收站：软删除租户恢复 / 物理删除（超级秘钥）/ 清除历史 ===== */
const recycleVisible = ref(false)
const recycleLoading = ref(false)
const recycleRows = ref([])
const recycleTab = ref('list')
const purgeLogs = ref([])
const purgeLogLoading = ref(false)

async function openRecycle() {
  recycleVisible.value = true
  recycleLoading.value = true
  purgeLogLoading.value = true
  try {
    [recycleRows.value, purgeLogs.value] = await Promise.all([
      api.tenantRecycle().then((d) => d || []),
      api.tenantPurgeLogs().then((d) => d || [])
    ])
  } finally {
    recycleLoading.value = false
    purgeLogLoading.value = false
  }
}
async function restore(row) {
  recycleLoading.value = true
  try {
    await api.tenantRestore(row.id)
    ElMessage.success(`企业「${row.name}」已恢复：用户已启用，默认菜单/角色/管理员已补齐`)
    await openRecycle()
    load()
  } finally { recycleLoading.value = false }
}

/** 物理删除：双重确认 + 超级秘钥，操作全程审计留痕 */
async function purge(row) {
  try {
    await ElMessageBox.confirm(
      `将永久删除企业「${row.name}」的全部数据（用户/角色/菜单/组织/业务数据），不可恢复！确定继续？`,
      '物理删除确认', { type: 'error', confirmButtonText: '继续', cancelButtonText: '取消' })
    const { value } = await ElMessageBox.prompt(
      '此操作不可恢复，请输入超级秘钥以确认', '超级秘钥校验',
      { inputType: 'password', confirmButtonText: '确认清除', cancelButtonText: '取消' })
    recycleLoading.value = true
    try {
      const detail = await api.tenantPurge(row.id, value)
      ElMessage.success(`已物理删除「${row.name}」，明细：${(detail || '').slice(0, 60)}…`)
      await openRecycle()
      load()
    } finally { recycleLoading.value = false }
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

function doExport() {
  exportXlsx('企业列表', [
      { label: '企业名称', prop: 'name' }, { label: '编码', prop: 'code' },
      { label: '联系人', prop: 'contactPerson' }, { label: '联系电话', prop: 'contactPhone' }
    ], rows.value)
}
onMounted(load)
</script>

<style scoped>
.form-tip { font-size: 12px; color: #8fa8c4; line-height: 1.5; margin-top: 4px; }

/* 嵌入子页面（品牌/字典）：隐藏其页头并自适应高度 */
.embed-view { overflow: auto; }
.embed-view :deep(.page) { height: 100%; overflow: visible; padding: 0; }
.embed-view :deep(.page-header) { display: none; }
/* 嵌入时面板拉伸至整行（原页面有 max-width 限宽，内嵌场景下拉平避免广告图行折行） */
.embed-view :deep(.panel) { max-width: none !important; width: 100%; }
.embed-view :deep(.banner-item .b-fields) { min-width: 320px; }
</style>
