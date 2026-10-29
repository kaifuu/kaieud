<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">课程管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增课程</el-button>
        <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
      </div>
    </div>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="课程名称" clearable style="width: 190px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.category" placeholder="课程分类" clearable style="width: 130px">
          <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting storage-key="course" :columns="allColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort" @selection-change="sel = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.name" prop="name" label="课程名称" min-width="170" sortable="custom" />
        <el-table-column v-if="cols.category" label="课程分类" width="100">
          <template #default="{ row }">{{ dictStore.label('course_category', row.category) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.classType" label="班型" width="90">
          <template #default="{ row }">{{ dictStore.label('class_type', row.classType) }}</template>
        </el-table-column>
        <el-table-column v-if="cols.durationMinutes" prop="durationMinutes" label="标准时长(分)" width="110" sortable="custom" />
        <el-table-column v-if="cols.status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该课程?" @confirm="remove(row.id)">
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

    <el-drawer v-model="visible" :title="form.id ? '编辑课程' : '新增课程'" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="课程名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="课程分类" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="d in dictStore.options('course_category')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="班型">
          <el-select v-model="form.classType" style="width: 100%">
            <el-option v-for="d in dictStore.options('class_type')" :key="d.value" :label="d.label" :value="Number(d.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="标准时长(分)"><el-input-number v-model="form.durationMinutes" :min="30" :step="15" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
    <DetailDrawer v-model="detailVisible" title="课程详情" :fields="detailFields" :data="detailRow" />
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
import { useDictStore } from '../../store/dict'
import DetailDrawer from '../../components/DetailDrawer.vue'

const dictStore = useDictStore()
const query = reactive({ keyword: '', category: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.coursePage(q), query)

const allColumns = [
  { prop: 'name', label: '课程名称' }, { prop: 'category', label: '课程分类' },
  { prop: 'classType', label: '班型' }, { prop: 'durationMinutes', label: '标准时长' },
  { prop: 'status', label: '状态' }
]
const cols = reactive({ name: true, category: true, classType: true, durationMinutes: true, status: true })
const onCols = (c) => Object.assign(cols, c)

const visible = ref(false)
const saving = ref(false)
const form = ref({})
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
      { prop: 'name', label: '课程名称' },
      { prop: 'category', label: '课程分类', fmt: (v) => dictStore.label('course_category', v) },
      { prop: 'classType', label: '班型', fmt: (v) => dictStore.label('class_type', v) },

      { prop: 'status', label: '状态', map: { 1: '上架', 0: '下架' } }, { prop: 'remark', label: '备注' }
    ]
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}
function openEdit(row) {
  form.value = row ? { ...row } : { classType: 1, category: 1, durationMinutes: 60, status: 1 }
  visible.value = true
}
async function save() {
  if (!form.value.name) return ElMessage.warning('课程名称不能为空')
  saving.value = true
  try {
    if (form.value.id) await api.courseUpdate(form.value)
    else await api.courseSave(form.value)
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function remove(id) {
  await api.courseDel(id)
  ElMessage.success('已删除')
  load()
}

function doExport() {
  exportXlsx('课程列表', [
      { label: '课程名称', prop: 'name' },
      { label: '课程分类', prop: 'category', fmt: (v) => dictStore.label('course_category', v) },
      { label: '班型', prop: 'classType', fmt: (v) => dictStore.label('class_type', v) },
    ], rows.value)
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条课程数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.courseDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

onMounted(() => {
  load()
  dictStore.load(true)
})
</script>
