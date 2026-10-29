<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">用户管理</span>
      <div class="actions">
        <el-button type="primary" @click="openEdit()">新增用户</el-button>
        <el-button v-if="sel.length" type="danger" @click="batchRemove">批量删除({{ sel.length }})</el-button>
      </div>
    </div>
    <!-- 四类用户TAB -->
    <el-tabs v-model="tab" class="user-tabs" @tab-change="onTab">
      <el-tab-pane label="学生" name="student" />
      <el-tab-pane label="教师" name="teacher" />
      <el-tab-pane label="员工" name="staff" />
      <el-tab-pane label="系统" name="system" />
    </el-tabs>
    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="用户名 / 姓名 / 手机号" clearable style="width: 210px"
                  @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-if="tab === 'staff'" v-model="query.userType" placeholder="员工类型" clearable style="width: 140px">
          <el-option label="教务人员" :value="2" /><el-option label="班主任" :value="3" />
          <el-option label="校区校长" :value="6" /><el-option label="校区咨询师" :value="7" />
          <el-option label="校区学管师" :value="8" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" /><el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="doExport">导出</el-button>
        <div style="flex: 1"></div>
        <ColumnSetting :storage-key="'user_' + tab" :columns="tabColumns" @change="onCols" />
      </div>
      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort" @selection-change="sel = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column label="照片" width="70">
          <template #default="{ row }">
            <img v-if="row.avatarUrl" :src="row.avatarUrl" class="row-avatar" />
            <div v-else class="row-avatar row-avatar-empty">{{ row.realName?.charAt(0) }}</div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="序号" width="60" :index="seq" />
        <el-table-column v-if="cols.username" prop="username" label="用户名" min-width="110" sortable="custom" />
        <el-table-column v-if="cols.realName" prop="realName" label="姓名" width="100" sortable="custom" />
        <!-- 学生TAB专属列：学籍信息 -->
        <template v-if="tab === 'student'">
          <el-table-column v-if="cols.grade" prop="grade" label="年级" width="90" />
          <el-table-column v-if="cols.school" prop="school" label="学校" min-width="130" />
          <el-table-column v-if="cols.parentPhone" prop="parentPhone" label="家长电话" width="135" />
          <el-table-column v-if="cols.advisor" prop="advisor" label="归属顾问" width="100" />
        </template>
        <el-table-column v-if="cols.userType && (tab === 'staff' || tab === 'system')" label="类型" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.userType === 1 ? 'danger' : 'primary'" effect="light">
              {{ typeNames[row.userType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.phone" prop="phone" label="手机号" width="135" />
        <el-table-column v-if="cols.email" prop="email" label="邮箱" min-width="140" />
        <el-table-column v-if="cols.status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="cols.lastLoginTime" prop="lastLoginTime" label="最近登录" width="165" sortable="custom">
          <template #default="{ row }">{{ fmt(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm :title="'重置密码为 123456 ?'" @confirm="resetPwd(row.id)">
              <template #reference>
                <el-button link type="warning">重置密码</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm v-if="row.id !== 1" title="确认删除该用户?" @confirm="remove(row.id)">
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

    <el-drawer v-model="visible" :title="form.id ? '编辑用户' : '新增用户'" size="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username" required><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="姓名" prop="realName" required><el-input v-model="form.realName" /></el-form-item>
      <el-form-item label="归属校区">
        <el-select v-model="form.orgId" clearable style="width: 100%"
                   placeholder="校区级角色必选，总部账号可不选">
          <el-option v-for="d in dictStore.options('campus')" :key="d.value" :label="d.label" :value="Number(d.value)" />
        </el-select>
      </el-form-item>
      <el-form-item label="照片">
        <PhotoUpload :url="avatarPreview" :user-id="form.id" :auto-upload="!!form.id"
                     :fallback="form.realName"
                     @success="onPhotoUploaded" @select="onPhotoSelected" />
      </el-form-item>
        <el-form-item label="用户类型" prop="userType">
          <el-select v-model="form.userType" placeholder="请选择用户类型" style="width: 100%">
            <el-option v-for="(n, k) in typeNames" :key="k" :label="n" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>

        <!-- 学生信息（用户类型=学生） -->
        <template v-if="Number(form.userType) === 5">
          <el-divider content-position="left">学生信息</el-divider>
          <el-form-item label="出生日期"><el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="年级"><el-input v-model="form.grade" /></el-form-item>
          <el-form-item label="就读学校"><el-input v-model="form.school" /></el-form-item>
          <el-form-item label="家长姓名"><el-input v-model="form.parentName" /></el-form-item>
          <el-form-item label="家长电话"><el-input v-model="form.parentPhone" /></el-form-item>
          <el-form-item label="备用电话"><el-input v-model="form.backupPhone" /></el-form-item>
          <el-form-item label="归属顾问"><el-input v-model="form.advisor" /></el-form-item>
          <el-form-item label="学员标签"><el-input v-model="form.tags" placeholder="多标签用逗号分隔" /></el-form-item>
          <el-form-item label="家庭住址"><el-input v-model="form.address" /></el-form-item>
        </template>

        <!-- 教师信息（用户类型=兼职老师） -->
        <template v-else-if="Number(form.userType) === 4">
          <el-divider content-position="left">教师信息</el-divider>
          <el-form-item label="科目"><el-input v-model="form.subject" /></el-form-item>
          <el-form-item label="职称"><el-input v-model="form.title" /></el-form-item>
          <el-form-item label="一对一提成"><el-input-number v-model="form.rateOne2one" :min="0" :precision="2" />
            <span class="u-tip">元/课时</span></el-form-item>
          <el-form-item label="班课提成"><el-input-number v-model="form.rateClass" :min="0" :precision="2" />
            <span class="u-tip">元/课时</span></el-form-item>
          <el-form-item label="可带班型">
            <el-checkbox-group v-model="classTypeArr">
              <el-checkbox value="1">一对一</el-checkbox>
              <el-checkbox value="2">小班课</el-checkbox>
              <el-checkbox value="3">大班课</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="简介"><el-input v-model="form.intro" type="textarea" /></el-form-item>
        </template>

        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" /></el-form-item>
        <el-alert v-if="!form.id" title="初始密码为 123456，请登录后尽快修改" type="info" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
    <DetailDrawer v-model="detailVisible" title="用户详情" :fields="detailFields" :data="detailRow">
      <template #avatarUrl="{ value }">
        <img v-if="value" :src="value" class="detail-photo" title="双击放大查看"
             @dblclick="openPhotoViewer(value)" />
        <span v-else style="color: #98a2b3">未上传</span>
      </template>
    </DetailDrawer>

    <!-- 照片放大查看器（Element Plus 标准组件） -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="[viewerUrl]"
      hide-on-click-modal
      teleported
      @close="viewerVisible = false"
    />
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../../api'
import { usePage } from '../../composables/usePage'
import ColumnSetting from '../../components/ColumnSetting.vue'
import { exportXlsx } from '../../utils/export'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { fetchAvatarUrl, clearAvatarCache } from '../../utils/avatar'
import { useDictStore } from '../../store/dict'

const dictStore = useDictStore()
import PhotoUpload from '../../components/PhotoUpload.vue' 

const typeNames = {
  1: '运营总负责人', 2: '教务人员', 3: '班主任', 4: '教师', 5: '学生',
  6: '校区校长', 7: '校区咨询师', 8: '校区学管师', 9: '系统用户'
}
const tab = ref('student')
// TAB -> 用户类型集合（学生/教师/员工/系统）
const tabTypes = { student: [5], teacher: [4], staff: [2, 3, 6, 7, 8], system: [1, 9] }

// 各TAB的列定义（列设置按TAB独立记忆）
const tabColumnDefs = {
  student: [
    { prop: 'username', label: '用户名' }, { prop: 'realName', label: '姓名' },
    { prop: 'grade', label: '年级' }, { prop: 'school', label: '学校' },
    { prop: 'parentPhone', label: '家长电话' }, { prop: 'advisor', label: '归属顾问' },
    { prop: 'status', label: '状态' }, { prop: 'lastLoginTime', label: '最近登录' }
  ],
  teacher: [
    { prop: 'username', label: '用户名' }, { prop: 'realName', label: '姓名' },
    { prop: 'phone', label: '手机号' }, { prop: 'email', label: '邮箱' },
    { prop: 'status', label: '状态' }, { prop: 'lastLoginTime', label: '最近登录' }
  ],
  staff: [
    { prop: 'username', label: '用户名' }, { prop: 'realName', label: '姓名' },
    { prop: 'userType', label: '类型' }, { prop: 'orgId', label: '所属校区' },
    { prop: 'phone', label: '手机号' }, { prop: 'status', label: '状态' },
    { prop: 'lastLoginTime', label: '最近登录' }
  ],
  system: [
    { prop: 'username', label: '用户名' }, { prop: 'realName', label: '姓名' },
    { prop: 'status', label: '状态' }, { prop: 'lastLoginTime', label: '最近登录' }
  ]
}
const tabColumns = computed(() => tabColumnDefs[tab.value] || [])
const query = reactive({ keyword: '', userType: null, status: null })
const { loading, rows, pager, seq, onSort, onSizeChange, search, load } =
  usePage((q) => api.userPage({ ...q, userTypes: tabTypes[tab.value].join(','), userType: q.userType }), query)

// 列表数据就绪后拉取头像（存于MySQL，带JWT读取）
watch(rows, (rs) => {
  rs.forEach((row) => {
    fetchAvatarUrl(row.id).then((url) => { row.avatarUrl = url || '' })
  })
})

const roles = ref([])
const orgTree = ref([])
const orgMap = ref({})
const formRef = ref(null)
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }]
}
const allColumns = [
  { prop: 'username', label: '用户名' }, { prop: 'realName', label: '姓名' },
  { prop: 'userType', label: '类型' }, { prop: 'orgId', label: '所属校区', fmt: (v) => dictStore.label('campus', v) || '总部' },
  { prop: 'grade', label: '年级' }, { prop: 'school', label: '学校' },
  { prop: 'parentPhone', label: '家长电话' }, { prop: 'advisor', label: '归属顾问' },
  { prop: 'phone', label: '手机号' },
  { prop: 'email', label: '邮箱' }, { prop: 'status', label: '状态' },
  { prop: 'lastLoginTime', label: '最近登录' }
]
const cols = reactive({ username: true, realName: true, userType: true, orgId: true, grade: true,
  school: true, parentPhone: true, advisor: true, phone: true,
  email: true, status: true, lastLoginTime: true })
const onCols = (c) => Object.assign(cols, c)

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }

const visible = ref(false)
const saving = ref(false)
const form = ref({})
const detailVisible = ref(false)
const detailRow = ref({})
const detailFields = [
  { prop: 'avatarUrl', label: '照片' },
  { prop: 'username', label: '用户名' }, { prop: 'realName', label: '姓名' },
  { prop: 'userType', label: '类型', map: { 1: '超级管理员', 2: '教管人员', 3: '班主任', 4: '教师', 5: '学生' } },
  { prop: 'phone', label: '手机号' }, { prop: 'email', label: '邮箱' },
  { prop: 'status', label: '状态', map: { 1: '启用', 0: '停用' } },
  { prop: 'lastLoginTime', label: '最近登录' }
]
const viewerVisible = ref(false)
const viewerUrl = ref('')
function openPhotoViewer(url) {
  viewerUrl.value = url
  viewerVisible.value = true
}
function openDetail(row) {
  detailRow.value = { ...row }
  detailVisible.value = true
  // 照片回显：无缓存时单独拉取
  if (!detailRow.value.avatarUrl) {
    fetchAvatarUrl(row.id).then((url) => { detailRow.value.avatarUrl = url || '' })
  }
}
function onTab() {
  query.userType = null
  query.page = 1
  load()
}

async function openEdit(row) {
  pendingPhoto.value = null
  if (row) {
    const roleIds = await api.userRoles(row.id)
    form.value = { ...row, roleIds, password: undefined }
    avatarPreview.value = row.avatarUrl || (await fetchAvatarUrl(row.id)) || ''
    // 编辑时回填学生/教师档案字段
    if ([4, 5].includes(Number(row.userType))) {
      const p = (await api.userProfile(row.id)) || {}
      form.value.birthDate = p.birthDate
      form.value.grade = p.grade
      form.value.school = p.school
      form.value.parentName = p.parentName
      form.value.parentPhone = p.parentPhone
      form.value.backupPhone = p.backupPhone
      form.value.advisor = p.advisor
      form.value.tags = p.tags
      form.value.address = p.address
      form.value.subject = p.subject
      form.value.title = p.title
      form.value.rateOne2one = p.rateOne2one
      form.value.rateClass = p.rateClass
      form.value.intro = p.intro
      form.value.classTypes = p.classTypes
    }
  } else {
    form.value = { status: 1, roleIds: [], userType: null }
    avatarPreview.value = ''
  }
  classTypeArr.value = (form.value.classTypes || '2').split(',').filter(Boolean)
  visible.value = true
}
const avatarPreview = ref('')
const pendingPhoto = ref(null) // 新增用户时暂存的照片文件，随保存统一上传
const classTypeArr = ref(['2'])
function onPhotoUploaded(url) {
  avatarPreview.value = url
  load()
}
function onPhotoSelected({ file, previewUrl }) {
  pendingPhoto.value = file
  avatarPreview.value = previewUrl
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const f = form.value
  saving.value = true
  try {
    if (Number(f.userType) === 4) f.classTypes = (f.classTypeArr || []).join(',')
    else delete f.classTypes
    if (f.id) {
      await api.userUpdate(f)
      // 编辑已有用户：换了照片则立即补传
      if (pendingPhoto.value) {
        await api.userAvatarUpload(f.id, toFormData(pendingPhoto.value))
        pendingPhoto.value = null
      }
    } else {
      const created = await api.userSave(f)
      // 新建用户：拿到新ID后上传暂存照片
      if (pendingPhoto.value && created?.id) {
        await api.userAvatarUpload(created.id, toFormData(pendingPhoto.value))
        clearAvatarCache(created.id)
        pendingPhoto.value = null
      }
    }
    ElMessage.success('已保存')
    visible.value = false
    // 按用户类型跳转到对应TAB展示（学生/教师/系统用户/其他员工）
    const typeToTab = { 5: 'student', 4: 'teacher', 9: 'system' }
    tab.value = typeToTab[Number(f.userType)] || 'staff'
    load()
  } finally { saving.value = false }
}
async function resetPwd(id) {
  await api.userResetPwd(id)
  ElMessage.success('密码已重置为 123456')
}
async function remove(id) {
  await api.userDel(id)
  ElMessage.success('已删除')
  load()
}

function toFormData(file) {
  const fd = new FormData()
  fd.append('file', file)
  return fd
}

function doExport() {
  exportXlsx('用户列表', [
      { label: '用户名', prop: 'username' }, { label: '姓名', prop: 'realName' },
      { label: '手机号', prop: 'phone' }, { label: '邮箱', prop: 'email' },
      { label: '最近登录', prop: 'lastLoginTime' }
    ], rows.value)
}

const sel = ref([])
async function batchRemove() {
  await ElMessageBox.confirm(`确认删除选中的 ${sel.value.length} 条用户数据？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(sel.value.map((r) => api.userDel(r.id)))
  const ok = results.filter((x) => x.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage.success(`批量删除完成：成功 ${ok} 条` + (fail ? `，失败 ${fail} 条` : ''))
  sel.value = []
  load()
}

onMounted(async () => {
  load()
  roles.value = (await api.roleList()) || []
  const tree = (await api.orgTree()) || []
  orgTree.value = tree
  const flat = {}
  const walk = (nodes, parentName) => nodes.forEach((n) => {
    flat[n.id] = parentName ? `${parentName}/${n.name}` : n.name
    walk(n.children || [], parentName || n.name)
  })
  walk(tree, '')
  orgMap.value = flat
})
</script>

<style scoped>
/* 详情照片：64px 圆形，可双击放大 */
.detail-photo {
  width: 64px; height: 64px; border-radius: 50%;
  object-fit: cover; display: block;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px rgba(0, 148, 255, 0.35), 0 4px 12px rgba(20, 60, 140, 0.15);
  cursor: zoom-in;
}
/* 列表照片：28px 紧凑圆形，不撑行高 */
.row-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  border: 1px solid #e8eef7;
}
.row-avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e3eeff, #ede8ff);
  color: #4a7ad1;
  font-weight: 600;
  font-size: 12px;
}
/* 抽屉照片预览：64px */
.drawer-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.drawer-avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e3eeff, #ede8ff);
  color: #4a7ad1;
  font-weight: 600;
  font-size: 22px;
}
</style>
