<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">租户管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增租户</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="租户名称 / 编码" clearable style="width: 200px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="tenant" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="租户名称" min-width="150" sortable="custom" />
        <el-table-column v-if="cols.code" prop="code" label="编码" min-width="110" sortable="custom" />
        <el-table-column v-if="cols.contactPerson" prop="contactPerson" label="联系人" width="110" />
        <el-table-column v-if="cols.contactPhone" prop="contactPhone" label="联系电话" width="140" />
        <el-table-column v-if="cols.createTime" prop="createTime" label="创建时间" width="170" sortable="custom">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该租户?" @confirm="remove(row.id)">
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

    <el-drawer v-model="visible" :title="form.id ? '编辑租户' : '新增租户'" size="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="租户名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'

const query = reactive({ keyword: '' })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.tenantPage(q), query)

const allColumns = [
  { prop: 'name', label: '租户名称' }, { prop: 'code', label: '编码' },
  { prop: 'contactPerson', label: '联系人' }, { prop: 'contactPhone', label: '联系电话' },
  { prop: 'createTime', label: '创建时间' }
]
const cols = reactive({ name: true, code: true, contactPerson: true,
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
  if (!form.value.name || !form.value.code) return ElMessage.warning('名称与编码不能为空')
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

function doExport() {
  exportXlsx('租户列表', [
      { label: '租户名称', prop: 'name' }, { label: '编码', prop: 'code' },
      { label: '联系人', prop: 'contactPerson' }, { label: '联系电话', prop: 'contactPhone' }
    ], rows.value)
}
onMounted(load)
</script>
