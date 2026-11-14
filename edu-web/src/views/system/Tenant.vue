<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">平台运营</span>
      <div class="actions" v-if="tab === 'corp'">
        <el-button type="primary" @click="openEdit()">新增企业</el-button>
      </div>
    </div>

    <!-- 平台运营视图：企业管理 / 品牌与广告（系统字典由各企业自维护） -->
    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab">
        <el-tab-pane label="企业管理" name="corp" />
        <el-tab-pane label="品牌与广告" name="brand" />

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
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="企业名称" min-width="150" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.code" prop="code" label="编码" min-width="110" sortable="custom"  show-overflow-tooltip/>
        <el-table-column v-if="cols.adminUsername" label="管理员账号" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.adminUsername" size="small" effect="light">{{ row.adminUsername }}</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">未开通</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.contactPerson" prop="contactPerson" label="联系人" width="110"  show-overflow-tooltip/>
        <el-table-column v-if="cols.contactPhone" prop="contactPhone" label="联系电话" width="140"  show-overflow-tooltip/>
        <el-table-column v-if="cols.createTime" prop="createTime" label="创建时间" width="170" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <el-dropdown trigger="click" popper-class="more-dropdown"
                         @visible-change="(v) => { row.__moreOpen = v }"
                         @command="(cmd) => onRowCommand(cmd, row)">
              <span class="more-trigger" :class="{ open: row.__moreOpen }">
                <el-icon :size="12"><MoreFilled /></el-icon>
                <span>更多</span>
                <el-icon :size="10" class="more-arrow"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit" class="more-item">
                    <span class="mi mi-purple"><el-icon :size="13"><EditPen /></el-icon></span>
                    <span class="mi-txt"><b>编辑</b><i>修改企业信息</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="toggle" class="more-item">
                    <span class="mi" :class="row.status === 1 ? 'mi-orange' : 'mi-green'">
                      <el-icon :size="13"><component :is="row.status === 1 ? 'CircleClose' : 'CircleCheck'" /></el-icon>
                    </span>
                    <span class="mi-txt">
                      <b>{{ row.status === 1 ? '禁用' : '启用' }}</b>
                      <i>{{ row.status === 1 ? '全员立即禁登' : '恢复全员登录' }}</i>
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="del" divided class="more-item">
                    <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                    <span class="mi-txt"><b>删除</b><i>移入回收站可恢复</i></span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
          <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="recycleRows" v-loading="recycleLoading" size="small" stripe>
            <el-table-column prop="id" label="ID" width="60"  show-overflow-tooltip/>
            <el-table-column prop="name" label="企业名称" min-width="120"  show-overflow-tooltip/>
            <el-table-column prop="code" label="编码" width="90"  show-overflow-tooltip/>
            <el-table-column label="删除时间" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="96" class-name="no-wrap-col">
              <template #default="{ row }">
                <el-dropdown trigger="click" popper-class="more-dropdown"
                             @visible-change="(v) => { row.__moreOpen = v }"
                             @command="(cmd) => onRecycleCommand(cmd, row)">
                  <span class="more-trigger" :class="{ open: row.__moreOpen }">
                    <el-icon :size="12"><MoreFilled /></el-icon>
                    <span>更多</span>
                    <el-icon :size="10" class="more-arrow"><ArrowDown /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="restore" class="more-item">
                        <span class="mi mi-green"><el-icon :size="13"><RefreshLeft /></el-icon></span>
                        <span class="mi-txt"><b>恢复</b><i>补齐账号与配置</i></span>
                      </el-dropdown-item>
                      <el-dropdown-item command="purge" divided class="more-item">
                        <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                        <span class="mi-txt"><b>物理删除</b><i>需超级秘钥确认</i></span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!recycleRows.length && !recycleLoading" description="回收站为空" :image-size="60" />
        </el-tab-pane>
        <el-tab-pane label="清除历史" name="logs">
          <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="purgeLogs" v-loading="purgeLogLoading" size="small" stripe>
            <el-table-column prop="createTime" label="时间" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ fmt(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="140" show-overflow-tooltip />
            <el-table-column label="企业" width="130" show-overflow-tooltip>
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

/** 平台运营视图：企业管理 / 品牌与广告 */
const tab = ref('corp')

/* ===== 通知公告（平台公告，tenant_id=0，全员可见） ===== */
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

/** 企业列表操作列「更多」命令分发 */
function onRowCommand(cmd, row) {
  if (cmd === 'edit') openEdit(row)
  else if (cmd === 'toggle') toggle(row)
  else if (cmd === 'del') ElMessageBox.confirm('确认删除该企业租户？删除后该企业全部数据不可恢复', '删除租户', { type: 'warning' })
    .then(() => remove(row.id)).catch(() => {})
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

/** 回收站操作列「更多」命令分发 */
function onRecycleCommand(cmd, row) {
  if (cmd === 'restore') restore(row)
  else if (cmd === 'purge') purge(row)
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
