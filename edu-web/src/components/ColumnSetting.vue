<template>
  <!-- 列设置：下拉多选控制表格字段展示，按 storageKey 本地记忆 -->
  <el-popover placement="bottom-end" trigger="click" width="216" popper-class="col-setting-popper">
    <template #reference>
      <button class="col-setting-btn" type="button">
        <el-icon :size="14"><Setting /></el-icon>
        <span>列设置</span>
        <el-icon :size="12" class="arrow"><ArrowDown /></el-icon>
      </button>
    </template>
    <div class="col-head">
      <span>显示字段</span>
      <span class="col-ops">
        <a @click.stop.prevent="setAll(true)">全选</a><i>/</i><a @click.stop.prevent="resetDefault">恢复默认</a>
      </span>
    </div>
    <div class="col-list">
      <label v-for="c in columns" :key="c.prop" class="col-item">
        <el-checkbox v-model="checked[c.prop]" @change="persist" />
        <span class="col-label">{{ c.label }}</span>
      </label>
    </div>
  </el-popover>
</template>

<script setup>
import { reactive, watch, onMounted } from 'vue'

const props = defineProps({
  storageKey: { type: String, required: true },
  columns: { type: Array, required: true } // [{prop,label}]
})
const emit = defineEmits(['change'])

const checked = reactive({})

function init() {
  if (!props.columns || !props.columns.length) return
  const saved = JSON.parse(localStorage.getItem('cols_' + props.storageKey) || '{}')
  props.columns.forEach((c) => { checked[c.prop] = saved[c.prop] !== false })
  emit('change', { ...checked })
}
onMounted(init)
watch(() => [props.columns, props.storageKey], init)

function persist() {
  localStorage.setItem('cols_' + props.storageKey, JSON.stringify(checked))
  emit('change', { ...checked })
}

/** 全选：全部字段显示 */
function setAll(v) {
  props.columns.forEach((c) => { checked[c.prop] = v })
  persist()
}

/** 恢复默认：清除本地记忆，回到默认全显示 */
function resetDefault() {
  localStorage.removeItem('cols_' + props.storageKey)
  props.columns.forEach((c) => { checked[c.prop] = true })
  persist()
}
</script>
