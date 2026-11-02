import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const viewModules = import.meta.glob('../views/**/*.vue')

// 组件路径大小写无关解析
const lowerMap = {}
for (const key of Object.keys(viewModules)) lowerMap[key.toLowerCase()] = key
function resolveView(comp) {
  return viewModules[lowerMap[('../views/' + comp + '.vue').toLowerCase()]]
}

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    name: 'Layout',
    component: () => import('../layout/index.vue'),
    redirect: '/home',
    children: [
      // 通知管理静态路由：所有登录用户可从右上角铃铛进入（菜单可见性仍由角色菜单控制）
      { path: 'system/notify', name: 'sys-notify', component: () => import('../views/system/Notify.vue'), meta: { title: '通知管理' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })
let dynamicAdded = false // 动态路由是否已注册（防止守卫内循环重试）

// 根据后端菜单构建动态路由
export function buildDynamicRoutes(menus) {
  const add = []
  const walk = (nodes) => {
    nodes.forEach((n) => {
      if (n.type === 'C' && n.component) {
        add.push({
          path: n.path,
          name: 'menu-' + n.id,
          component: resolveView(n.component),
          meta: { title: n.name, icon: n.icon }
        })
      }
      if (n.children && n.children.length) walk(n.children)
    })
  }
  walk(menus)
  // 路径归一化：菜单 path 可能带前导斜杠（如 '/home'），拼接前去掉，防止出现 '//home' 死循环
  const norm = (p) => (p || '').replace(/^\//, '')
  // 目录节点重定向到第一个子菜单
  menus
    .filter((m) => m.type === 'M' && m.path)
    .forEach((t) => {
      const first = flatten(t.children || [])[0]
      if (first) add.push({ path: t.path, redirect: '/' + norm(first.path) })
    })
  // 菜单里没有首页时（如平台运营只有平台运营菜单），/home 重定向到第一个可用菜单，防路由守卫死循环
  if (!flatten(menus).some((m) => norm(m.path) === 'home')) {
    const firstMenu = flatten(menus)[0]
    if (firstMenu) add.push({ path: 'home', redirect: '/' + norm(firstMenu.path) })
  }
  add.forEach((r) => router.addRoute('Layout', r))
}

function flatten(nodes, acc = []) {
  nodes.forEach((n) => {
    if (n.type === 'C') acc.push(n)
    if (n.children) flatten(n.children, acc)
  })
  return acc
}

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.path === '/login') return store.token ? '/home' : true
  if (!store.token) return '/login'
  // 刷新页面时动态路由尚未注册：先按本地缓存菜单重建，再重试本次导航
  if (!to.matched.length && store.menus.length) {
    if (!dynamicAdded) {
      dynamicAdded = true
      buildDynamicRoutes(store.menus)
      return { path: to.fullPath, replace: true }
    }
    return '/home' // 兜底：重建后仍无匹配，回首页
  }
  return true
})

export default router
