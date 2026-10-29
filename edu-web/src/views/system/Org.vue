<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">组织管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit(null, currentNode)">新增组织</el-button>
      </div>
    </div>

    <div style="display: flex; gap: 14px; height: calc(100% - 46px)">
      <!-- 左侧：组织树 -->
      <div class="panel" style="width: 300px; flex-shrink: 0; padding: 6px; overflow: auto">
        <div class="panel-title">组织树</div>
        <el-tree ref="treeRef" :data="tree" node-key="id" :props="{ label: 'name' }"
                 highlight-current default-expand-all @node-click="onNodeClick">
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon :size="14" style="margin-right: 5px"><OfficeBuilding /></el-icon>
              {{ data.name }}
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右侧：下级组织列表（联动 + 筛选 + 分页） -->
      <div class="panel table-panel" style="flex: 1">
        <div class="toolbar">
          <el-tag effect="light">{{ currentNode?.name || '全部组织' }}</el-tag>
          <el-input v-model="filter.keyword" placeholder="名称 / 负责人 / 电话" clearable
                    style="width: 200px" @keyup.enter="filter.page = 1">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <span style="color: #8fa8c4; font-size: 13px">
            {{ currentNode ? '当前节点的下级组织' : '点击左侧节点查看下级组织' }}
          </span>
          <div style="flex: 1"></div>
          <el-button v-if="currentNode" @click="currentNode = null">查看全部</el-button>
        </div>
        <el-table :data="pagedChildren" v-loading="loading" stripe height="100%">
          <el-table-column type="index" label="序号" width="60" :index="seq" />
          <el-table-column prop="name" label="组织名称" min-width="160" />
          <el-table-column prop="leader" label="负责人" width="130" />
          <el-table-column prop="phone" label="电话" width="150" />
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(null, row)">新增下级</el-button>
              <el-button link type="primary" @click="openEdit(row, null)">编辑</el-button>
              <el-popconfirm title="确认删除该组织?" @confirm="remove(row.id)">
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
    <el-drawer v-model="visible" :title="form.id ? '编辑组织' : '新增组织'" size="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="上级组织">
          <el-tree-select v-model="form.parentId" :data="treeOptions" check-strictly style="width: 100%" />
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" /></el-form-item>
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

const tree = ref([])
const loading = ref(false)
const visible = ref(false)
const form = ref({})
const treeOptions = ref([])
const currentNode = ref(null)

/** 右侧列表：当前节点的下级组织 + 筛选 + 客户端分页 */
const filter = reactive({ keyword: '', page: 1, size: 10 })
const children = computed(() => {
  if (!currentNode.value) return tree.value
  return currentNode.value.children || []
})
const filteredChildren = computed(() =>
  children.value.filter((o) => {
    if (!filter.keyword) return true
    const k = filter.keyword.toLowerCase()
    return [o.name, o.leader, o.phone].some((v) => v && String(v).toLowerCase().includes(k))
  }))
const pagedChildren = computed(() =>
  filteredChildren.value.slice((filter.page - 1) * filter.size, filter.page * filter.size))
function seq(i) { return (filter.page - 1) * filter.size + i + 1 }

function onNodeClick(data) {
  currentNode.value = currentNode.value?.id === data.id ? null : data
  filter.page = 1
}

const toOptions = (nodes) =>
  nodes.map((n) => ({ value: n.id, label: n.name, children: n.children?.length ? toOptions(n.children) : undefined }))

const load = async () => {
  loading.value = true
  try {
    tree.value = (await api.orgTree()) || []
    treeOptions.value = [{ value: 0, label: '根节点', children: toOptions(tree.value) }]
    if (currentNode.value) {
      const find = (nodes) => nodes.find((n) => n.id === currentNode.value.id)
      let found = null
      const walk = (nodes) => nodes.forEach((n) => { if (n.id === currentNode.value?.id) found = n; walk(n.children || []) })
      walk(tree.value)
      currentNode.value = found
    }
  } finally { loading.value = false }
}

const openEdit = (row, parent) => {
  form.value = row ? { ...row } : { parentId: parent ? parent.id : 0, sort: 0, status: 1 }
  visible.value = true
}

const save = async () => {
  if (!form.value.name) return ElMessage.warning('名称不能为空')
  if (form.value.id) await api.orgUpdate(form.value)
  else await api.orgSave(form.value)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

const remove = async (id) => {
  await api.orgDel(id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.tree-node { display: inline-flex; align-items: center; }
</style>
