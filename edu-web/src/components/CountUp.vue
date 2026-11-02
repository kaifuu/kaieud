<template>
  <span>{{ text }}</span>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'

/**
 * 数字滚动（count-up）：数值变化时从当前显示值缓动滚到目标值；
 * 非数值（'-'、文本）直接原样展示。首页指标卡、看板数字共用。
 */
const props = defineProps({
  value: { type: [Number, String], default: 0 },
  duration: { type: Number, default: 900 }
})

const text = ref('')
let raf = null

function fmtNum(n, decimals) {
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: decimals, maximumFractionDigits: decimals
  })
}

function animate(from, to, decimals) {
  cancelAnimationFrame(raf)
  const t0 = performance.now()
  const step = (t) => {
    const p = Math.min(1, (t - t0) / props.duration)
    const e = 1 - Math.pow(1 - p, 3) // easeOutCubic
    text.value = fmtNum(from + (to - from) * e, decimals)
    if (p < 1) raf = requestAnimationFrame(step)
  }
  raf = requestAnimationFrame(step)
}

watch(() => props.value, (v, old) => {
  const s = v === null || v === undefined ? '' : String(v)
  const num = Number(s)
  if (s === '' || s === '-' || Number.isNaN(num)) { text.value = s || '-'; return }
  const decimals = s.includes('.') ? s.split('.')[1].length : 0
  const prev = Number(String(old ?? 0))
  animate(Number.isNaN(prev) ? 0 : prev, num, decimals)
}, { immediate: true })

onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>
