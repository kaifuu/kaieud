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
    children: []
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
  // 目录节点重定向到第一个子菜单
  menus
    .filter((m) => m.type === 'M' && m.path)
    .forEach((t) => {
      const first = flatten(t.children || [])[0]
      if (first) add.push({ path: t.path, redirect: '/' + first.path })
    })
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
