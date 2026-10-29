// 列表导出xlsx（导出字段与页面展示一致）
import * as XLSX from 'xlsx'

/**
 * @param {string} name 文件名（不含扩展名）
 * @param {Array<{label:string,prop:string,fmt?:Function}>} columns 导出列
 * @param {Array} rows 数据行
 */
export function exportXlsx(name, columns, rows) {
  const data = rows.map((r) => {
    const o = {}
    columns.forEach((c) => {
      const v = r[c.prop]
      o[c.label] = c.fmt ? c.fmt(v, r) : (v ?? '')
    })
    return o
  })
  const ws = XLSX.utils.json_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, name.slice(0, 30))
  XLSX.writeFile(wb, name + '_' + new Date().toISOString().slice(0, 10) + '.xlsx')
}
