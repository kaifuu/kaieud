import { defineStore } from 'pinia'
import api from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('edu_token') || '',
    user: JSON.parse(localStorage.getItem('edu_user') || 'null'),
    menus: JSON.parse(localStorage.getItem('edu_menus') || '[]'),
    perms: JSON.parse(localStorage.getItem('edu_perms') || '[]')
  }),
  actions: {
    async login(form) {
      const vo = await api.login(form)
      this.token = vo.token
      this.user = vo.user
      this.menus = vo.menus || []
      this.perms = vo.perms || []
      localStorage.setItem('edu_token', vo.token)
      localStorage.setItem('edu_user', JSON.stringify(vo.user))
      localStorage.setItem('edu_menus', JSON.stringify(vo.menus || []))
      localStorage.setItem('edu_perms', JSON.stringify(vo.perms || []))
      return vo
    },
    logout() {
      this.token = ''
      this.user = null
      this.menus = []
      this.perms = []
      localStorage.clear()
    },
    /** 用最新菜单覆盖本地缓存（管理员新增菜单后无需重新登录） */
    updateMenus(menus) {
      this.menus = menus || []
      localStorage.setItem('edu_menus', JSON.stringify(this.menus))
    },
    hasPerm(p) {
      if (!p) return true
      if (this.user && this.user.userType === 1) return true
      return this.perms.includes(p)
    }
  }
})
