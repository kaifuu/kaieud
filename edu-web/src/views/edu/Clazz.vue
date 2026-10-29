<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">班级管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增班级</el-button>
        <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="班级名称" clearable style="width: 190px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="招生中" :value="1" /><el-option label="开课中" :value="2" /><el-option label="已结课" :value="3" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="clazz" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort" @selection-change="sel = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="班级名称" min-width="160" sortable="custom" />
        <el-table-column v-if="cols.courseId" label="课程" min-width="140">
          <template #default="{ row }">{{ courseName(row.courseId) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classTeacherId" label="班主任" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.classTeacherId" size="small" effect="light">{{ teacherName(row.classTeacherId) }}</el-tag>
            <span v-else>未指定</span>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.capacity" prop="capacity" label="容量" width="80" sortable="custom" />
        <el-table-column v-if="cols.startDate" prop="startDate" label="开课日期" width="115" sortable="custom" />
        <el-table-column v-if="cols.status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="['', 'primary', 'success', 'info'][row.status]" effect="light">
              {{ ['', '招生中', '开课中', '已结课'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该班级?" @confirm="remove(row.id)">
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

    <el-drawer v-model="visible" :title="form.id ? '编辑班级' : '新增班级'" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="班级名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班主任">
          <el-select v-model="form.classTeacherId" clearable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
        <el-form-item label="开课日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="招生中" :value="1" /><el-option label="开课中" :value="2" /><el-option label="已结课" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
    <DetailDrawer v-model="detailVisible" title="班级详情" :fields="detailFields" :data="detailRow" />
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
import DetailDrawer from '../../components/DetailDrawer.vue'

const query = reactive({ keyword: '', status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.clazzPage(q), query)

const courses = ref([])
const teachers = ref([])
const courseName = (id) => courses.value.find((c) => c.id === id)?.name || '-'
const teacherName = (id) => teachers.value.find((t) => t.id === id)?.name || '-'

const allColumns = [
  { prop: 'name', label: '班级名称' }, { prop: 'courseId', label: '课程' },
  { prop: 'classTeacherId', label: '班主任' }, { prop: 'capacity', label: '容量' },
  { prop: 'startDate', label: '开课日期' }, { prop: 'status', label: '状态' }
]
const cols = reactive({ name: true, courseId: true, classTeacherId: true,
  capacity: true, startDate: true, status: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const saving = ref(false)
const form = ref({})
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
      { prop: 'name', label: '班级名称' },
      { prop: 'courseId', label: '课程', formatter: courseName },
      { prop: 'classTeacherId', label: '班主任', formatter: teacherName },
      { prop: 'capacity', label: '容量' }, { prop: 'startDate', label: '开课日期' },
      { prop: 'status', label: '状态', map: { 1: '招生中', 2: '开课中', 3: '已结课' } }
    ]
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}
function openEdit(row) {
  form.value = row ? { ...row } : { capacity: 20, status: 1 }
  visible.value = true
}
async function save() {
  if (!form.value.name) return ElMessage.warning('班级名称不能为空')
  saving.value = true
  try {
    if (form.value.id) await api.clazzUpdate(form.value)
    else await api.clazzSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.clazzDel(id)
  ElMessage.success('已删除')
  load()
}

function doExport() {
  exportXlsx('班级列表', [
      { label: '班级名称', prop: 'name' }, { label: '容量', prop: 'capacity' },
      { label: '开课日期', prop: 'startDate' }
    ], rows.value)
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条班级数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.clazzDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

onMounted(async () => {
  load()
  courses.value = (await api.courseList()) || []
  teachers.value = (await api.teacherList()) || []
})
</script>
