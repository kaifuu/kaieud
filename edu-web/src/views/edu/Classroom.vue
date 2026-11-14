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
        <el-select v-model="query.campus" placeholder="校区" clearable filterable style="width: 140px">
          <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <div style="flex: 1"></div>
      </div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="校区" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.campus || '-' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="教室名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="容纳人数(最低~最多)" width="140" align="center">
          <template #default="{ row }">{{ row.minCapacity ?? '—' }} ~ {{ row.capacity ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contact || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="135" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contactPhone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
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
                    <span class="mi-txt"><b>编辑教室</b><i>修改教室信息</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="occupancy" class="more-item">
                    <span class="mi mi-blue"><el-icon :size="13"><Clock /></el-icon></span>
                    <span class="mi-txt"><b>占用/空余时段</b><i>查看排课占用情况</i></span>
                  </el-dropdown-item>
                  <el-dropdown-item command="del" divided class="more-item">
                    <span class="mi mi-red"><el-icon :size="13"><Delete /></el-icon></span>
                    <span class="mi-txt"><b>删除</b><i>已排课教室不可删</i></span>
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

    <!-- 新增/编辑教室：所属校区必填，置于教室名称之前 -->
    <el-drawer v-model="visible" :title="form.id ? '编辑教室' : '新增教室'" size="440px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属校区" required>
          <el-select v-model="form.campus" placeholder="请选择所属校区" style="width: 100%">
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="容纳人数">
          <div class="cap-box">
            <span class="cap-label">最低</span>
            <el-input-number v-model="form.minCapacity" :min="0" :max="999" size="small"
                             controls-position="right" style="width: 100px" placeholder="最低" />
            <span class="cap-label">最多</span>
            <el-input-number v-model="form.capacity" :min="1" :max="999" size="small"
                             controls-position="right" style="width: 100px" />
            <span class="cap-unit">人</span>
          </div>
        </el-form-item>
        <el-form-item label="联系人">
          <el-select v-model="form.contact" filterable clearable allow-create default-first-option
                     style="width: 100%" placeholder="选择员工（支持搜索，也可手输）"
                     @change="onContactChange">
            <el-option v-for="c in contactCandidates" :key="c.name"
                       :label="c.name + (c.phone ? '（' + c.phone + '）' : '')" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" maxlength="20" placeholder="选择联系人后自动带出，可修改" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="教室位置、设备、使用注意事项等" />
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
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="occ.occupied || []" size="small" stripe max-height="220">
        <el-table-column prop="date" label="日期" width="110"  show-overflow-tooltip/>
        <el-table-column prop="start" label="开始" width="80"  show-overflow-tooltip/>
        <el-table-column prop="end" label="结束" width="80"  show-overflow-tooltip/>
        <el-table-column label="状态" width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success'][row.status]" effect="plain">
              {{ ['待上课', '已完成'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-title" style="margin-top: 14px">空余时段（08:00-22:00）</div>
      <el-table :tooltip-options="{ showAfter: 300, hideAfter: 3000, enterable: true, effect: 'light', popperClass: 'soft-tip' }"  :data="occ.free || []" size="small" stripe max-height="220">
        <el-table-column prop="date" label="日期" width="110"  show-overflow-tooltip/>
        <el-table-column prop="start" label="开始" width="80"  show-overflow-tooltip/>
        <el-table-column prop="end" label="结束" width="80"  show-overflow-tooltip/>
      </el-table>
      <el-empty v-if="!(occ.free || []).length && !(occ.occupied || []).length" description="暂无数据" :image-size="60" />
    </el-drawer>
  </div>
</template>

<style scoped>
/* 容纳人数：最低 ~ 最多 */
.cap-box { display: flex; align-items: center; gap: 6px; }
.cap-label { font-size: 12.5px; color: #667c99; white-space: nowrap; }
.cap-unit { font-size: 12.5px; color: #8fa8c4; margin-left: 2px; }
</style>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()

const query = reactive({ keyword: '', campus: null })

/* 联系人候选：本企业在职员工；选中后手机号联动带出（可改） */
const contactCandidates = ref([])
function onContactChange(name) {
  const hit = contactCandidates.value.find((c) => c.name === name)
  if (hit) form.value.contactPhone = hit.phone || ''
}
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.classroomPage({ ...q, campus: query.campus }), query)


const visible = ref(false)
const saving = ref(false)
const form = ref({})
const occVisible = ref(false)
const occRoom = ref(null)
const occRange = ref([new Date().toISOString().slice(0, 10), new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10)])
const occ = ref({})

async function openEdit(row) {
  form.value = row ? { ...row } : { capacity: 30, minCapacity: null, campus: query.campus || null }
  visible.value = true
  // 每次打开抽屉刷新联系人候选：用户管理改/补手机号后立即带出最新号码
  try { contactCandidates.value = (await api.classroomContactCandidates()) || [] } catch (e) { /* 保留旧候选 */ }
}
async function save() {
  if (!form.value.campus) return ElMessage.warning('所属校区为必填项')
  if (!form.value.name) return ElMessage.warning('教室名称不能为空')
  if (form.value.minCapacity != null && form.value.capacity != null
      && form.value.minCapacity > form.value.capacity)
    return ElMessage.warning(`最低容纳（${form.value.minCapacity} 人）不能大于最多容纳（${form.value.capacity} 人）`)
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

/** 操作列下拉菜单分发 */
function onRowCommand(cmd, row) {
  switch (cmd) {
    case 'edit': openEdit(row); break
    case 'occupancy': openOccupancy(row); break
    case 'del':
      ElMessageBox.confirm('已排课的教室无法删除，确认删除？', '删除提示', { type: 'warning' })
        .then(() => remove(row.id)).catch(() => {})
      break
  }
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

/* 校区来源：系统字典-校区（不含"全部校区"占位项） */
const campusOptions = computed(() =>
  dictStore.options('campus').map((d) => d.label).filter((l) => l !== '全部校区'))

onMounted(async () => {
  dictStore.load(true)
  contactCandidates.value = (await api.classroomContactCandidates()) || []
  load()
})
</script>
