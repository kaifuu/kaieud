<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">角色管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增角色</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="角色名称" clearable style="width: 200px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="role" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="角色名称" min-width="140" sortable="custom" />
        <el-table-column v-if="cols.code" prop="code" label="角色编码" min-width="140" sortable="custom" />
        <el-table-column v-if="cols.remark" prop="remark" label="备注" min-width="180" />
        <el-table-column v-if="cols.createTime" prop="createTime" label="创建时间" width="170" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="assign(row)">分配菜单</el-button>
            <el-popconfirm v-if="row.id !== 1" title="确认删除该角色?" @confirm="remove(row.id)">
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

    <el-drawer v-model="visible" :title="form.id ? '编辑角色' : '新增角色'" size="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="角色编码" required><el-input v-model="form.code" placeholder="如 EDU_ADMIN" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="assignVisible" title="分配菜单权限" width="420px">
      <el-tree ref="menuTreeRef" :data="menuTree" show-checkbox node-key="id"
               :props="{ label: 'name' }" :default-checked-keys="checkedKeys" />
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'

const query = reactive({ keyword: '' })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.rolePage(q), query)

const allColumns = [
  { prop: 'name', label: '角色名称' }, { prop: 'code', label: '角色编码' },
  { prop: 'remark', label: '备注' }, { prop: 'createTime', label: '创建时间' }
]
const cols = reactive({ name: true, code: true, remark: true, createTime: true })
const onCols = (c) => Object.assign(cols, c)

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

const visible = ref(false)
const saving = ref(false)
const form = ref({})
function openEdit(row) {
  form.value = row ? { ...row } : {}
  visible.value = true
}
async function save() {
  if (!form.value.name || !form.value.code) return ElMessage.warning('名称与编码不能为空')
  saving.value = true
  try {
    if (form.value.id) await api.roleUpdate(form.value)
    else await api.roleSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}

/* 分配菜单 */
const assignVisible = ref(false)
const menuTree = ref([])
const checkedKeys = ref([])
const currentRole = ref(null)
const menuTreeRef = ref()
const collectIds = (nodes) => nodes.flatMap((n) => [n.id, ...collectIds(n.children || [])])

async function assign(row) {
  currentRole.value = row
  menuTree.value = (await api.menuTree()) || []
  const all = await api.roleMenus(row.id)
  const leafIds = collectIds(menuTree.value).filter((id) => !menuTree.value.some(
    (m) => (m.children || []).some((c) => c.id === id)))
  checkedKeys.value = all.filter((id) => leafIds.includes(id))
  assignVisible.value = true
  await nextTick()
}
async function saveAssign() {
  const ids = [...menuTreeRef.value.getCheckedKeys(), ...menuTreeRef.value.getHalfCheckedKeys()]
  await api.roleAssignMenus(currentRole.value.id, ids)
  ElMessage.success('分配成功')
  assignVisible.value = false
}

async function remove(id) {
  await api.roleDel(id)
  ElMessage.success('已删除')
  load()
}

function doExport() {
  exportXlsx('角色列表', [
      { label: '角色名称', prop: 'name' }, { label: '角色编码', prop: 'code' },
      { label: '备注', prop: 'remark' }
    ], rows.value)
}
onMounted(load)
</script>
