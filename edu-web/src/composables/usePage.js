// 列表页通用逻辑：分页 + 服务端排序 + 跨页序号
import { reactive, ref } from 'vue'

/**
 * @param {Function} fetchFn (query) => Promise<{records, total}> 请求函数
 * @param {Object}   filters  筛选条件响应式对象
 */
export function usePage(fetchFn, filters = {}) {
  const loading = ref(false)
  const rows = ref([])
  const pager = reactive({ page: 1, size: 10, total: 0 })
  const sort = reactive({ sortBy: '', direction: '' })

  async function load() {
    loading.value = true
    try {
      const query = {
        page: pager.page,
        size: pager.size,
        sortBy: sort.sortBy || undefined,
        direction: sort.sortBy ? sort.direction : undefined,
        ...filters
      }
      const d = await fetchFn(query)
      rows.value = d.records || []
      pager.total = d.total || 0
    } finally {
      loading.value = false
    }
  }

  /** 跨页连续序号 */
  function seq(i) { return (pager.page - 1) * pager.size + i + 1 }

  /** 表头排序（sortable=custom） */
  function onSort({ prop, order }) {
    sort.sortBy = order ? prop : ''
    sort.direction = order === 'ascending' ? 'asc' : 'desc'
    pager.page = 1
    load()
  }

  function search() { pager.page = 1; load() }
  function onSizeChange() { pager.page = 1; load() }

  return { loading, rows, pager, sort, seq, onSort, onSizeChange, search, load }
}
