// 系统字典全局store：一次加载全局共享，字典配置变更后可强制刷新
import { defineStore } from 'pinia'
import api from '../api'

export const useDictStore = defineStore('dict', {
  state: () => ({
    dicts: JSON.parse(localStorage.getItem('sys_dicts') || '{}'),
    loaded: false
  }),
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return this.dicts
      try {
        this.dicts = (await api.dictAll()) || {}
        this.loaded = true
        localStorage.setItem('sys_dicts', JSON.stringify(this.dicts))
      } catch (e) { /* 静默，保留缓存 */ }
      return this.dicts
    },
    /** 字典值转标签，如 dictLabel('class_type', 2) => '小班课' */
    label(type, value) {
      const item = (this.dicts[type] || []).find((d) => Number(d.value) === Number(value))
      return item ? item.label : (value ?? '')
    },
    options(type) {
      return (this.dicts[type] || []).map((d) => ({ label: d.label, value: Number(d.value) }))
    }
  }
})
