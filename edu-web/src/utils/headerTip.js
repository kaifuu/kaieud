/**
 * 全局表头悬浮提示：表头不折行（配套 base.css 的 nowrap），文字展示不全时悬停出现浮窗，
 * 浮窗内容超高可滚动；鼠标离开表头或浮窗后驻留 1 秒再隐藏。
 * 事件委托实现，覆盖系统内全部 el-table，无需逐表配置。
 */
let tipEl = null
let hideTimer = null
let currentCell = null

function ensureTip() {
  if (tipEl) return tipEl
  tipEl = document.createElement('div')
  tipEl.className = 'header-auto-tip'
  document.body.appendChild(tipEl)
  // 悬停浮窗本身时不隐藏（滚动阅读长文本）
  tipEl.addEventListener('mouseenter', () => clearTimeout(hideTimer))
  tipEl.addEventListener('mouseleave', scheduleHide)
  return tipEl
}

function showTip(cell) {
  const tip = ensureTip()
  clearTimeout(hideTimer)
  const text = (cell.innerText || '').trim()
  tip.textContent = text
  tip.style.display = 'block'
  const r = cell.getBoundingClientRect()
  const tw = tip.offsetWidth
  const thh = tip.offsetHeight
  let left = r.left + r.width / 2 - tw / 2
  left = Math.max(8, Math.min(left, window.innerWidth - tw - 8))
  let top = r.bottom + 6
  if (top + thh > window.innerHeight - 8) top = Math.max(8, r.top - thh - 6)
  tip.style.left = left + 'px'
  tip.style.top = top + 'px'
}

function scheduleHide() {
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    if (tipEl) tipEl.style.display = 'none'
    currentCell = null
  }, 1000)   // 离开驻留 1 秒
}

export function initHeaderTip() {
  document.addEventListener('mouseover', (e) => {
    const cell = e.target && e.target.closest ? e.target.closest('.el-table th .cell') : null
    if (!cell) return
    // 仅当文字被截断（展示不全）时浮窗
    if (cell.scrollWidth <= cell.clientWidth + 1) return
    if (cell === currentCell && tipEl && tipEl.style.display === 'block') return
    currentCell = cell
    showTip(cell)
  })
  document.addEventListener('mouseout', (e) => {
    const cell = e.target && e.target.closest ? e.target.closest('.el-table th .cell') : null
    if (cell) scheduleHide()
  })
}
