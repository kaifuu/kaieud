<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">教室管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增教室</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="教室名称" clearable style="width: 190px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.orgId" placeholder="校区" clearable filterable style="width: 140px">
          <el-option v-for="d in dictStore.options('campus')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="name" label="教室名称" min-width="150" />
        <el-table-column prop="capacity" label="可容纳人数" width="110" />
        <el-table-column label="校区" min-width="130">
          <template #default="{ row }">{{ dictStore.label('campus', row.orgId) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" class-name="no-wrap-col">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" @click="openOccupancy(row)">占用/空余时段</el-button>
            <el-popconfirm title="已排课的教室无法删除，确认删除？" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
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

    <!-- 新增/编辑教室 -->
    <el-drawer v-model="visible" :title="form.id ? '编辑教室' : '新增教室'" size="440px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="教室名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="可容纳人数"><el-input-number v-model="form.capacity" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="所属校区">
          <el-select v-model="form.orgId" clearable style="width: 100%">
            <el-option v-for="d in dictStore.options('campus')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 占用/空余时段抽屉 -->
    <el-drawer v-model="occVisible" :title="`占用/空余时段 — ${occRoom?.name || ''}`" size="560px">
      <div style="margin-bottom: 12px">
        <el-date-picker v-model="occRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" @change="loadOcc" />
      </div>
      <div class="panel-title">已占用时段</div>
      <el-table :data="occ.occupied || []" size="small" stripe max-height="220">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column prop="start" label="开始" width="80" />
        <el-table-column prop="end" label="结束" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success'][row.status]" effect="plain">
              {{ ['待上课', '已完成'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-title" style="margin-top: 14px">空余时段（08:00-22:00）</div>
      <el-table :data="occ.free || []" size="small" stripe max-height="220">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column prop="start" label="开始" width="80" />
        <el-table-column prop="end" label="结束" width="80" />
      </el-table>
      <el-empty v-if="!(occ.free || []).length && !(occ.occupied || []).length" description="暂无数据" :image-size="60" />
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
const dictStore = useDictStore()
import { usePage } from '../../composables/usePage'
import { useDictStore } from '../../store/dict'

const query = reactive({ keyword: '', orgId: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.classroomPage({ ...q, orgId: query.orgId }), query)


const visible = ref(false)
const saving = ref(false)
const form = ref({})
const occVisible = ref(false)
const occRoom = ref(null)
const occRange = ref([new Date().toISOString().slice(0, 10), new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10)])
const occ = ref({})

function openEdit(row) {
  form.value = row ? { ...row } : { capacity: 30, orgId: query.orgId }
  visible.value = true
}
async function save() {
  if (!form.value.name) return ElMessage.warning('教室名称不能为空')
  saving.value = true
  try {
    if (form.value.id) await api.classroomUpdate(form.value)
    else await api.classroomSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.classroomDel(id)
  ElMessage.success('已删除')
  load()
}

function openOccupancy(row) {
  occRoom.value = row
  occVisible.value = true
  loadOcc()
}
async function loadOcc() {
  if (!occRoom.value) return
  occ.value = (await api.classroomOccupancy({
    id: occRoom.value.id,
    begin: occRange.value?.[0] || new Date().toISOString().slice(0, 10),
    end: occRange.value?.[1] || new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10)
  })) || {}
}

onMounted(() => {
  load()
  dictStore.load(true)
})
</script>
