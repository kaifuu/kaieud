<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">菜单管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit(null, currentNode)">新增菜单</el-button>
      </div>
    </div>

    <div style="display: flex; gap: 14px; height: calc(100% - 50px)">
      <!-- 左侧：菜单树 -->
      <div class="panel" style="width: 300px; flex-shrink: 0; padding: 8px; overflow: auto">
        <div class="panel-title">菜单树</div>
        <el-tree ref="treeRef" :data="tree" node-key="id" :props="{ label: 'name' }"
                 highlight-current default-expand-all @node-click="onNodeClick">
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon :size="14" style="margin-right: 5px">
                <component :is="data.type === 'M' ? 'Folder' : (data.icon || 'Document')" />
              </el-icon>
              {{ data.name }}
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右侧：子菜单列表（随左侧节点联动） -->
      <div class="panel table-panel" style="flex: 1">
        <div class="toolbar">
          <el-tag effect="light">{{ currentNode?.name || '全部菜单' }}</el-tag>
          <el-input v-model="filter.keyword" placeholder="名称 / 路由 / 权限标识" clearable
                    style="width: 200px" @keyup.enter="filter.page = 1">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filter.type" placeholder="类型" clearable style="width: 110px">
            <el-option label="目录" value="M" /><el-option label="菜单" value="C" />
          </el-select>
          <span style="color: #8fa8c4; font-size: 13px">
            {{ currentNode ? '当前节点的下级菜单' : '点击左侧节点查看下级菜单' }}
          </span>
          <div style="flex: 1"></div>
          <el-button v-if="currentNode" @click="currentNode = null">查看全部</el-button>
        </div>
        <el-table :data="pagedChildren" v-loading="loading" stripe height="100%" row-key="id" @sort-change="onSort">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="path" label="路由" min-width="110" />
          <el-table-column prop="component" label="组件" min-width="130" />
          <el-table-column prop="perm" label="权限标识" width="120" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ row.type === 'M' ? '目录' : '菜单' }}</template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(null, row)">新增下级</el-button>
              <el-button link type="primary" @click="openEdit(row, null)">编辑</el-button>
              <el-popconfirm title="确认删除该菜单及其下级?" @confirm="remove(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager-row">
          <el-pagination background layout="total, prev, pager, next" :total="filteredChildren.length"
                         :page-size="filter.size" v-model:current-page="filter.page" />
        </div>
      </div>
    </div>

    <!-- 编辑抽屉 -->
    <el-drawer v-model="visible" :title="form.id ? '编辑菜单' : '新增菜单'" size="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">菜单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="路由地址"><el-input v-model="form.path" placeholder="目录如 /system，菜单如 tenant" /></el-form-item>
        <el-form-item label="组件路径" v-if="form.type === 'C'"><el-input v-model="form.component" placeholder="如 system/Tenant" /></el-form-item>
        <el-form-item label="图标">
          <IconPicker v-model="form.icon" />
        </el-form-item>
        <el-form-item label="权限标识"><el-input v-model="form.perm" placeholder="如 sys:user" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import IconPicker from '../../components/IconPicker.vue'

const tree = ref([])
const loading = ref(false)
const visible = ref(false)
const form = ref({})
const currentNode = ref(null)
const treeRef = ref()

/** 当前节点的下级菜单（联动） */
const children = computed(() => {
  if (!currentNode.value) return tree.value
  return currentNode.value.children || []
})

/** 右侧列表：筛选 + 客户端分页 */
const filter = reactive({ keyword: '', type: null, page: 1, size: 10 })
const filteredChildren = computed(() =>
  children.value.filter((m) => {
    if (filter.type && m.type !== filter.type) return false
    if (filter.keyword) {
      const k = filter.keyword.toLowerCase()
      return [m.name, m.path, m.perm, m.component].some((v) =>
        v && String(v).toLowerCase().includes(k))
    }
    return true
  }))
const pagedChildren = computed(() =>
  filteredChildren.value.slice((filter.page - 1) * filter.size, filter.page * filter.size))
function onSort() { /* 树形数据由后端sort字段排序，前端占位 */ }

function onNodeClick(data) {
  currentNode.value = currentNode.value?.id === data.id ? null : data
  filter.page = 1
}

const load = async () => {
  loading.value = true
  try {
    tree.value = (await api.menuTree()) || []
    if (currentNode.value) {
      // 刷新后按ID找回选中节点
      const find = (nodes) => nodes.find((n) => n.id === currentNode.value.id) ||
        (nodes.flatMap((n) => find(n.children || [])).filter(Boolean)[0] || null)
      currentNode.value = find(tree.value) || null
    }
  } finally { loading.value = false }
}

const openEdit = (row, parent) => {
  form.value = row ? { ...row } : { parentId: parent ? parent.id : 0, type: 'C', sort: 0, visible: 1 }
  visible.value = true
}

const save = async () => {
  if (!form.value.name) return ElMessage.warning('名称不能为空')
  if (form.value.id) await api.menuUpdate(form.value)
  else await api.menuSave(form.value)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

const remove = async (id) => {
  await api.menuDel(id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.tree-node { display: inline-flex; align-items: center; }
</style>
