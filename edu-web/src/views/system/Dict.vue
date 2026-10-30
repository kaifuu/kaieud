<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">系统字典</span>
      <div class="actions">
        <el-button type="primary" @click="openAddType">新增类型</el-button>
        <el-button @click="openEdit()">新增字典项</el-button>
      </div>
    </div>

    <div style="display: flex; gap: 14px; height: calc(100% - 46px)">
      <!-- 左侧：字典类型（可新增/重命名/删除） -->
      <div class="panel" style="width: 260px; flex-shrink: 0; padding: 6px">
        <div class="panel-title">字典类型</div>
        <div class="type-list">
          <div v-for="(items, type) in dicts" :key="type" class="type-item"
               :class="{ active: currentType === type }" @click="selectType(type)">
            <span class="ti-name">{{ typeName(type) }}</span>
            <span class="ti-right">
              <el-tag size="small" effect="plain">{{ items.length }}</el-tag>
              <el-icon class="ti-edit" title="重命名" @click.stop="openRenameType(type)"><EditPen /></el-icon>
              <el-icon class="ti-del" title="删除类型" @click.stop="removeType(type)"><Delete /></el-icon>
            </span>
          </div>
        </div>
      </div>

      <!-- 右侧：字典项列表 -->
      <div class="panel table-panel" style="flex: 1">
        <div class="toolbar">
          <el-tag effect="light">{{ typeName(currentType) }}</el-tag>
          <span style="color: #8fa8c4; font-size: 13px">字典项配置后全站下拉框与标签自动生效</span>
          <div style="flex: 1"></div>
          <el-button @click="reload">刷新缓存</el-button>
        </div>
        <el-table :data="currentItems" stripe height="100%">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="label" label="标签" min-width="140" />
          <el-table-column prop="value" label="值" width="110" />
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm title="确认删除该字典项?" @confirm="remove(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 字典项编辑 -->
    <el-drawer v-model="visible" :title="form.id ? '编辑字典项' : '新增字典项'" size="400px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="字典类型" required>
          <el-select v-model="form.dictType" style="width: 100%">
            <el-option v-for="(items, type) in dicts" :key="type" :label="typeName(type)" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" required><el-input v-model="form.label" /></el-form-item>
        <el-form-item label="值" required>
          <el-input-number v-model="form.value" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 新增字典类型 -->
    <el-dialog v-model="typeVisible" title="新增字典类型" width="420px">
      <el-form label-width="90px">
        <el-form-item label="中文名"><el-input v-model="typeForm.name" placeholder="如：校区、学历" /></el-form-item>
        <el-form-item label="类型标识" required>
          <el-input v-model="typeForm.type" placeholder="英文标识，如 campus" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSaving" @click="saveType">创建</el-button>
      </template>
    </el-dialog>

    <!-- 重命名字典类型 -->
    <el-dialog v-model="renameVisible" title="重命名字典类型" width="420px">
      <el-form label-width="90px">
        <el-form-item label="中文名"><el-input v-model="renameForm.name" /></el-form-item>
        <el-form-item label="类型标识">
          <el-input v-model="renameForm.type" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSaving" @click="saveRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Delete } from '@element-plus/icons-vue'
import api from '../../api'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
const dicts = computed(() => dictStore.dicts)
const currentType = ref('class_type')
const visible = ref(false)
const saving = ref(false)
const form = ref({})

// 字典类型中文名：优先本地映射，其次 dict_type_labels（后端可扩展），最后回退英文标识
const typeNameMap = reactive({
  class_type: '班型', course_category: '课程分类',
  pay_channel: '缴费方式', student_status: '学员状态',
  contract_status: '合同状态', campus: '所属校区',
  log_type: '日志类型'
})
const typeName = (t) => typeNameMap[t] || t

const currentItems = computed(() => dicts.value[currentType.value] || [])

function selectType(t) { currentType.value = t }
async function reload() {
  await dictStore.load(true)
  ElMessage.success('字典缓存已刷新，全站生效')
}

/* ===== 字典类型新增/重命名/删除 ===== */
const typeVisible = ref(false)
const renameVisible = ref(false)
const typeSaving = ref(false)
const typeForm = reactive({ name: '', type: '' })
const renameForm = reactive({ oldType: '', name: '', type: '' })

function openAddType() {
  typeForm.name = ''; typeForm.type = ''
  typeVisible.value = true
}
function openRenameType(type) {
  renameForm.oldType = type
  renameForm.name = typeNameMap[type] && typeNameMap[type] !== type ? typeNameMap[type] : ''
  renameForm.type = type
  renameVisible.value = true
}

async function saveType() {
  if (!typeForm.type.trim()) return ElMessage.warning('请输入类型标识')
  typeSaving.value = true
  try {
    await api.dictTypeSave({ type: typeForm.type.trim(), label: typeForm.name.trim() || typeForm.type.trim() })
    if (typeForm.name.trim()) typeNameMap[typeForm.type.trim()] = typeForm.name.trim()
    ElMessage.success('字典类型已创建')
    typeVisible.value = false
    currentType.value = typeForm.type.trim()
    await dictStore.load(true)
  } finally { typeSaving.value = false }
}

async function saveRename() {
  if (!renameForm.type.trim()) return ElMessage.warning('请输入类型标识')
  typeSaving.value = true
  try {
    await api.dictTypeRename({ oldType: renameForm.oldType, newType: renameForm.type.trim() })
    delete typeNameMap[renameForm.oldType]
    if (renameForm.name.trim()) typeNameMap[renameForm.type.trim()] = renameForm.name.trim()
    ElMessage.success('字典类型已重命名')
    renameVisible.value = false
    currentType.value = renameForm.type.trim()
    await dictStore.load(true)
  } finally { typeSaving.value = false }
}

async function removeType(type) {
  await ElMessageBox.confirm(
    `确认删除字典类型「${typeName(type)}」？该类型下所有字典项将一并删除，全站引用将回退为原始值。`,
    '删除字典类型', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  await api.dictTypeDel(type)
  delete typeNameMap[type]
  ElMessage.success('字典类型已删除')
  currentType.value = Object.keys(dictStore.dicts || {})[0] || 'class_type'
  await dictStore.load(true)
}

/* ===== 字典项 CRUD ===== */
function openEdit(row) {
  form.value = row ? { ...row } : { dictType: currentType.value, sort: 0 }
  visible.value = true
}
async function save() {
  saving.value = true
  try {
    await api.dictSave(form.value)
    ElMessage.success('已保存，全站生效')
    visible.value = false
    await dictStore.load(true)
  } finally { saving.value = false }
}
async function remove(id) {
  await api.dictDel(id)
  ElMessage.success('已删除')
  await dictStore.load(true)
}
onMounted(() => dictStore.load(true))
</script>

<style scoped>
.type-list { display: flex; flex-direction: column; gap: 2px; }
.type-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 9px 12px; border-radius: 8px; cursor: pointer;
  color: #344054; font-size: 13.5px;
  transition: background 0.15s ease;
}
.ti-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ti-right { display: flex; align-items: center; gap: 5px; }
.ti-edit, .ti-del {
  width: 0; opacity: 0; overflow: hidden; font-size: 13px;
  transition: all 0.2s ease; cursor: pointer;
}
.ti-edit { color: #155EEF; }
.ti-del { color: #f04438; }
.type-item:hover .ti-edit,
.type-item:hover .ti-del { width: 15px; opacity: 1; }
.type-item:hover { background: #f0f6ff; }
.type-item.active {
  background: linear-gradient(90deg, #155EEF, #5B8DEF);
  color: #fff; font-weight: 600;
}
.type-item.active .el-tag { --el-tag-bg-color: rgba(255,255,255,0.2); --el-tag-text-color: #fff; --el-tag-border-color: transparent; }
</style>
