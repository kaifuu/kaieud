<template>
  <!-- 图标选择器：默认展示教育/后台场景精选图标；输入关键字检索图标库全量 292 个 -->
  <el-popover placement="bottom-start" :width="320" trigger="click" popper-class="icon-picker-popper">
    <template #reference>
      <el-input :model-value="modelValue ? modelValue : ''" readonly placeholder="点击选择图标" class="icon-input">
        <template #prefix>
          <el-icon v-if="modelValue && Icons[modelValue]"><component :is="modelValue" /></el-icon>
          <el-icon v-else><Grid /></el-icon>
        </template>
      </el-input>
    </template>
    <div class="picker-head">
      <el-input v-model="keyword" placeholder="搜索图标（可搜全量图标库）" size="small" clearable :prefix-icon="Search" />
      <el-button v-if="modelValue" link type="danger" size="small" @click="$emit('update:modelValue', '')">清除</el-button>
    </div>
    <div class="icon-grid">
      <div v-for="name in filteredIcons" :key="name" class="icon-cell"
           :class="{ active: name === modelValue }" :title="name"
           @click="$emit('update:modelValue', name)">
        <el-icon :size="17"><component :is="name" /></el-icon>
      </div>
      <div v-if="!filteredIcons.length" class="icon-empty">无匹配图标</div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import * as Icons from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const keyword = ref('')

// 图标库全量名称（存在性校验后的真实集合）
const allNames = Object.keys(Icons).filter((n) => typeof Icons[n] === 'object')

// 常用图标清单（教育/后台场景精选；含存在性过滤，避免清单里出现包中不存在的名字渲染成空白）
const iconNames = [
  'HomeFilled', 'School', 'User', 'UserFilled', 'Avatar',
  'Reading', 'Notebook', 'EditPen', 'Collection', 'CollectionTag',
  'Calendar', 'AlarmClock', 'Clock', 'Bell', 'BellFilled',
  'Setting', 'Tools', 'Operation', 'DataAnalysis', 'TrendCharts',
  'PieChart', 'Money', 'Wallet', 'CreditCard', 'Tickets',
  'Files', 'Document', 'Memo', 'List', 'Grid',
  'Menu', 'Fold', 'Expand', 'OfficeBuilding', 'Shop',
  'ShoppingCart', 'Box', 'Present', 'Medal', 'Trophy',
  'Star', 'StarFilled', 'ChatDotRound', 'ChatLineRound', 'Message',
  'Picture', 'Camera', 'Upload', 'Download', 'Link',
  'Search', 'Edit', 'Delete', 'Plus', 'Finished',
  'Checked', 'Stamp', 'Key', 'Lock', 'View',
  'Promotion', 'Suitcase', 'Guide', 'Histogram', 'Coin'
].filter((n) => Icons[n])

const filteredIcons = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  // 有关键字时检索全量图标库，无关键字时展示精选网格
  const base = k ? allNames : iconNames
  return base.filter((n) => n.toLowerCase().includes(k))
})
</script>

<style scoped>
.icon-input { width: 100%; cursor: pointer; }
.picker-head { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.icon-grid {
  display: grid; grid-template-columns: repeat(8, 1fr);
  gap: 4px; max-height: 220px; overflow-y: auto;
}
.icon-cell {
  height: 34px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  color: #475467; cursor: pointer;
  transition: all 0.15s ease;
}
.icon-cell:hover { background: #f0f6ff; color: #155EEF; }
.icon-cell.active { background: #155EEF; color: #fff; }
.icon-empty { grid-column: 1 / -1; text-align: center; color: #98a2b3; font-size: 12px; padding: 10px 0; }
</style>
