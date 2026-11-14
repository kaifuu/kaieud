<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { initHeaderTip } from './utils/headerTip'

onMounted(() => {
  // 全局表头悬浮提示（表头不折行，展示不全时悬停浮窗、离开驻留1秒）
  initHeaderTip()
})
</script>
