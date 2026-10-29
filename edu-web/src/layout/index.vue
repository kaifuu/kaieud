<template>
  <el-container class="layout">
    <!-- ===== 左侧菜单栏：深蓝→紫渐变，与登录页风格统一 ===== -->
    <el-aside :width="collapsed ? '64px' : '224px'" class="aside" :class="{ collapsed }">
      <div class="logo">
        <img v-if="logoUrl" :src="logoUrl" class="logo-img" @error="logoUrl = ''" />
        <div v-else class="logo-mark">E</div>
        <span v-if="!collapsed" class="logo-text">智教云课</span>
      </div>
      <el-menu :default-active="route.path" router unique-opened class="menu"
                :collapse="collapsed" :collapse-transition="false" popper-class="dark-menu-popper">
        <template v-for="m in store.menus" :key="m.id">
          <!-- 目录 -->
          <el-sub-menu v-if="m.type === 'M'" :index="String(m.id)">
            <template #title>
              <el-icon><component :is="m.icon || 'Menu'" /></el-icon>
              <span>{{ m.name }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.id" :index="'/' + c.path">
              <el-icon><component :is="c.icon || 'Document'" /></el-icon>
              <span>{{ c.name }}</span>
              <span v-if="c.path === 'notify' && noticeCount" class="menu-badge">{{ noticeCount }}</span>
            </el-menu-item>
          </el-sub-menu>
          <!-- 顶级菜单 -->
          <el-menu-item v-else :index="m.path">
            <el-icon><component :is="m.icon || 'Document'" /></el-icon>
            <span>{{ m.name }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <!-- 侧栏底部装饰光斑 -->
      <div class="aside-glow"></div>
    </el-aside>

    <el-container class="right">
      <!-- ===== 顶栏：白色简洁，渐变头像与主色呼应 ===== -->
      <el-header class="header">
        <div class="crumb">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Expand v-if="collapsed" /><Fold v-else />
          </el-icon>
          <span v-if="crumb.parent" class="crumb-parent">{{ crumb.parent }}</span>
          <el-icon v-if="crumb.parent" class="crumb-sep"><ArrowRight /></el-icon>
          <span class="crumb-current">{{ crumb.name }}</span>
        </div>
        <div class="right-area">
          <span class="welcome">
            <b>{{ store.user?.realName }}</b>
            <i class="role-pill">{{ typeName }}</i>
          </span>
          <span class="v-divider"></span>
          <!-- 系统通知铃铛 -->
          <el-popover placement="bottom-end" width="320" trigger="click" @show="loadNotices">
            <template #reference>
              <el-badge :value="notices.length" :hidden="!notices.length" :max="9" class="bell">
                <el-icon :size="19"><Bell /></el-icon>
              </el-badge>
            </template>
            <div class="notice-pop">
              <div class="notice-head">系统通知</div>
              <el-empty v-if="!notices.length" description="暂无通知" :image-size="60" />
              <div v-for="n in notices" :key="n.id" class="notice-item">
                <div class="n-title">
                  <el-tag size="small" :type="['info', 'warning', 'danger'][n.level]" effect="light">
                    {{ ['普通', '重要', '紧急'][n.level] }}
                  </el-tag>
                  {{ n.title }}
                </div>
                <div class="n-content">{{ n.content }}</div>
                <div class="n-time">{{ (n.publishTime || '').replace('T', ' ').slice(0, 16) }}</div>
              </div>
            </div>
          </el-popover>
          <el-dropdown @command="onCommand">
            <div class="avatar">{{ store.user?.realName?.charAt(0) }}</div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
                <el-dropdown-item command="density" divided>
                  界面密度：{{ compact ? '紧凑' : '宽松' }}
                </el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- ===== 内容区：淡蓝白底 ===== -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 个人信息/修改密码抽屉 -->
  <el-drawer v-model="profileVisible" title="个人信息" size="440px">
    <el-tabs v-model="profileTab">
      <el-tab-pane label="基本信息" name="info">
        <el-form :model="profileForm" label-width="80px" style="margin-top: 10px">
          <el-form-item label="照片">
            <PhotoUpload :url="profileAvatarUrl" :user-id="store.user?.id"
                         :fallback="store.user?.realName?.charAt(0)" @success="onProfilePhoto" />
          </el-form-item>
          <el-form-item label="用户名"><el-input :model-value="store.user?.username" disabled /></el-form-item>
          <el-form-item label="姓名" required><el-input v-model="profileForm.realName" /></el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="profileForm.gender">
              <el-radio :value="1">男</el-radio><el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="手机号"><el-input v-model="profileForm.phone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="profileForm.email" /></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="修改密码" name="pwd">
        <el-form label-width="80px" style="margin-top: 10px">
          <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" show-password /></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="changePwd">修改密码</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>

  <!-- 照片放大查看器 -->
  <el-image-viewer
    v-if="photoViewerVisible"
    :url-list="[photoViewerUrl]"
    hide-on-click-modal
    teleported
    @close="photoViewerVisible = false"
  />

  <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
    <el-form label-width="90px">
      <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" @click="changePwd">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import api from '../api'
import logoAsset from '../assets/logo.png'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const typeNames = { 1: '超级管理员', 2: '教管人员', 3: '班主任', 4: '教师', 5: '学生' }
const typeName = computed(() => typeNames[store.user?.userType] || '用户')

/** 面包屑：父目录 › 当前页（顶栏不再与内容区页头重复显示同名标题） */
const crumb = computed(() => {
  const path = route.path
  for (const m of store.menus) {
    for (const c of m.children || []) {
      if ('/' + c.path === path || c.path === path) {
        return { parent: m.name, name: c.name }
      }
    }
  }
  const top = store.menus.find((m) => m.path === path)
  return { parent: null, name: top?.name || route.meta.title || '首页' }
})

onMounted(async () => {
  // 系统Logo
  try { await loadLogo() } catch (e) { /* ignore */ }
  // 顶栏头像
  try { await loadHeaderAvatar() } catch (e) { /* ignore */ }
  // 菜单徽标：最新通知数
  try { await loadNotices() } catch (e) { /* ignore */ }
  // 刷新后动态路由丢失则重建
  if (store.token && store.menus.length) {
    import('../router').then(({ buildDynamicRoutes }) => buildDynamicRoutes(store.menus))
  }
  // 拉取最新菜单（管理员新增菜单后侧栏自动更新，无需重新登录）
  try {
    const latest = await api.meMenus()
    if (Array.isArray(latest) && latest.length) {
      store.updateMenus(latest)
      const { buildDynamicRoutes } = await import('../router')
      buildDynamicRoutes(latest)
    }
  } catch (e) { /* 静默失败，沿用缓存菜单 */ }
})

// 菜单折叠状态（本地记忆）
const collapsed = ref(localStorage.getItem('menu_collapsed') === '1')
function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem('menu_collapsed', collapsed.value ? '1' : '0')
}

const saving = ref(false)
const pwd = ref({ oldPassword: '', newPassword: '' })
const pwdVisible = ref(false)

/* ===== 系统Logo：基础设置上传的优先，否则用内置资产 ===== */
const logoUrl = ref(logoAsset)
async function loadLogo() {
  try {
    const cfg = (await api.configPublic()) || {}
    if (cfg.logo_url) logoUrl.value = cfg.logo_url + '?t=' + Date.now()
  } catch (e) { /* 忽略，用内置资产 */ }
}

/* ===== 系统通知 ===== */
const notices = ref([])
const noticeCount = ref(0)
async function loadNotices() {
  notices.value = (await api.noticeRecent(8)) || []
  noticeCount.value = notices.value.length
}

/* ===== 界面密度（紧凑/宽松，全局记忆） ===== */
const compact = ref(localStorage.getItem('ui_compact') === '1')
document.body.classList.toggle('ui-compact', compact.value)

/* ===== 个人信息修改 ===== */
const profileVisible = ref(false)
const headerAvatar = ref('')
const profileAvatarUrl = ref('')
import { fetchAvatarUrl, clearAvatarCache } from '../utils/avatar'
const profileForm = ref({})
const profileTab = ref('info')
async function loadProfile() {
  const me = await api.me()
  profileForm.value = {
    realName: me.realName, gender: me.gender || 1,
    phone: me.phone || '', email: me.email || ''
  }
}
const photoViewerVisible = ref(false)
const photoViewerUrl = ref('')
function openPhotoViewer(url) {
  if (!url) return
  photoViewerUrl.value = url
  photoViewerVisible.value = true
}

async function loadHeaderAvatar() {
  if (!store.user?.id) return
  headerAvatar.value = await fetchAvatarUrl(store.user.id)
}
function onProfilePhoto(url) {
  profileAvatarUrl.value = url
  loadHeaderAvatar()
}

async function saveProfile() {
  const f = profileForm.value
  if (!f.realName) return ElMessage.warning('姓名不能为空')
  await api.updateProfile(f)
  ElMessage.success('个人信息已更新，重新登录后生效')
  profileVisible.value = false
}

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    store.logout()
    router.push('/login')
  } else if (cmd === 'pwd') {
    pwdVisible.value = true
  } else if (cmd === 'density') {
    compact.value = !compact.value
    localStorage.setItem('ui_compact', compact.value ? '1' : '0')
    document.body.classList.toggle('ui-compact', compact.value)
    ElMessage.success(compact.value ? '已切换为紧凑模式' : '已切换为宽松模式')
  } else if (cmd === 'profile') {
    loadProfile()
    profileVisible.value = true
  }
}

const changePwd = async () => {
  if (!pwd.value.oldPassword || !pwd.value.newPassword)
    return ElMessage.warning('请输入原密码和新密码')
  await api.changePassword(pwd.value)
  ElMessage.success('密码已修改，请重新登录')
  store.logout()
  router.push('/login')
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.layout { height: 100vh; height: 100dvh; }
.right { min-width: 0; }

/* ===== 侧栏：深蓝→紫渐变（登录页同款色系） ===== */
.aside {
  position: relative;
  overflow: hidden;
  transition: width 0.25s ease;
  background: #ffffff;
  border-right: 1px solid #e8eef7;
  display: flex;
  flex-direction: column;
}
/* 底部装饰光斑（淡紫，低透明度） */
.aside-glow {
  position: absolute; left: -60px; bottom: -80px;
  width: 240px; height: 240px; border-radius: 50%;
  background: rgba(0, 148, 255, 0.10);
  filter: blur(50px);
  pointer-events: none;
}

/* Logo区：渐变角标 + 白色字标 */
.logo {
  display: flex; align-items: center; justify-content: flex-start; gap: 10px;
  padding-left: 18px;
  white-space: nowrap;
  padding: 20px 0 16px;
  border-bottom: 1px solid #e8eef7;
}
.logo-img {
  width: 34px; height: 34px; border-radius: 8px;
  object-fit: contain; display: block;
  background: #fff; padding: 2px;
  box-shadow: 0 2px 8px rgba(0, 148, 255, 0.25);
}
.logo-mark {
  width: 34px; height: 34px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 19px; font-weight: 800; color: #fff;
  background: linear-gradient(135deg, #0094FF, #8B6FF0);
  box-shadow: 0 4px 12px rgba(0, 148, 255, 0.4);
}
.logo-text { color: #142a44; font-size: 17px; font-weight: 700; letter-spacing: 2px; }

/* ===== 菜单：透明底，圆角悬浮项，渐变激活态 ===== */
.aside.collapsed .logo { justify-content: center; padding-left: 0; }
.menu { flex: 1; border-right: none; background: transparent; padding: 8px 10px; overflow-y: auto; }
.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 42px; line-height: 42px;
  margin: 3px 0; border-radius: 10px;
  color: #475467;
  transition: background 0.2s ease, color 0.2s ease;
}
.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background: #f0f6ff;
  color: #0094FF;
}
/* 激活项：主色文字 + 品牌渐变淡底（浅色教育风） */
.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(0, 148, 255, 0.12), rgba(139, 111, 240, 0.10));
  color: #0094FF;
  font-weight: 600;
}
/* 子菜单 */
.menu :deep(.el-sub-menu .el-menu) { background: transparent; }
.menu :deep(.el-sub-menu .el-menu .el-menu-item) { padding-left: 48px !important; }
.menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) { color: #0094FF; }
.menu :deep(.el-menu-item .el-icon),
.menu :deep(.el-sub-menu__title .el-icon) { color: inherit; }

/* ===== 顶栏：白色，渐变标题条 + 渐变头像 ===== */
.header {
  position: relative;
  display: flex; justify-content: space-between; align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #eaf0f8;
  box-shadow: 0 2px 12px rgba(20, 60, 140, 0.05);
  padding: 0 20px;
}
/* 底部蓝紫渐变细线（呼应登录页/侧栏） */
.header::after {
  content: '';
  position: absolute; left: 0; right: 0; bottom: -1px; height: 2px;
  background: linear-gradient(90deg, rgba(0,148,255,0.30), rgba(139,111,240,0.30) 45%, transparent 80%);
  pointer-events: none;
}

/* 面包屑 */
.crumb { display: flex; align-items: center; gap: 8px; }
.collapse-btn {
  width: 32px; height: 32px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; color: #5b7396;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}
.collapse-btn:hover { background: #eef4ff; color: #0094FF; }
.crumb-parent { font-size: 13px; color: #8fa8c4; letter-spacing: 0.5px; }
.crumb-sep { font-size: 12px; color: #c0cbdb; }
.crumb-current { font-size: 14.5px; font-weight: 600; color: #142a44; letter-spacing: 0.5px; }

/* 右侧区域 */
.right-area { display: flex; align-items: center; gap: 14px; }
.welcome { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #667085; }
.welcome b { color: #142a44; font-weight: 600; font-size: 13.5px; letter-spacing: 0.3px; }
.role-pill {
  font-style: normal; font-size: 11.5px; line-height: 1;
  color: #0094FF;
  background: rgba(0, 148, 255, 0.08);
  border: 1px solid rgba(0, 148, 255, 0.28);
  padding: 3px 9px; border-radius: 10px;
  letter-spacing: 0.5px;
}
.v-divider { width: 1px; height: 18px; background: #e4eaf2; }

/* 通知铃铛：圆角方形悬停块 */
.bell {
  width: 34px; height: 34px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #5b7396; cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}
.bell:hover { background: #eef4ff; color: #0094FF; }

/* 渐变头像：白色描边圈 + 悬停光晕 */
.avatar {
  width: 34px; height: 34px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 15px; font-weight: 600; cursor: pointer;
  background: linear-gradient(135deg, #0094FF, #8B6FF0);
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px rgba(0, 148, 255, 0.35), 0 4px 10px rgba(110, 91, 232, 0.28);
  transition: transform 0.2s ease, box-shadow 0.25s ease;
}
.avatar:hover {
  transform: scale(1.06);
  box-shadow: 0 0 0 2px rgba(0, 148, 255, 0.3), 0 6px 16px rgba(110, 91, 232, 0.4);
}

/* 菜单徽标：红底白字，靠右 */
.menu-badge {
  margin-left: auto;
  font-size: 11px; line-height: 1;
  color: #fff; font-weight: 600;
  background: linear-gradient(135deg, #FF6B6B, #F04438);
  padding: 3px 6px; border-radius: 8px;
  box-shadow: 0 2px 6px rgba(240, 68, 56, 0.4);
}

.photo-box {
  position: relative; width: 64px; height: 64px;
  border-radius: 50%; cursor: pointer;
}
.photo-box:hover .p-avatar { box-shadow: 0 4px 14px rgba(0, 148, 255, 0.35); }
.p-avatar {
  width: 64px; height: 64px; border-radius: 50%; object-fit: cover;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #E3EEFF, #EDE8FF); color: #4a7ad1; font-weight: 600; font-size: 22px;
}

/* ===== 内容区：淡蓝白底 ===== */
.main { background: #ffffff; padding: 14px 18px; }
</style>
