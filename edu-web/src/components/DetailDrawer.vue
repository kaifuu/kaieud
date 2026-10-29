<template>
  <!-- 通用查看详情抽屉：fields=[{prop,label,map?,formatter?}] -->
  <el-drawer :model-value="modelValue" :title="title" size="420px"
             @update:model-value="$emit('update:modelValue', $event)">
    <el-descriptions :column="1" border size="large">
      <el-descriptions-item v-for="f in fields" :key="f.prop" :label="f.label"
                            :label-width="labelWidth">
        <slot :name="f.prop" :value="pick(f)">
          {{ pick(f) }}
        </slot>
      </el-descriptions-item>
    </el-descriptions>
  </el-drawer>
</template>

<script setup>
const props = defineProps({
  modelValue: Boolean,
  title: String,
  fields: { type: Array, default: () => [] },
  data: { type: Object, default: () => ({}) },
  labelWidth: { type: String, default: '110px' }
})
defineEmits(['update:modelValue'])

function pick(f) {
  const v = props.data?.[f.prop]
  if (v == null || v === '') return '-'
  if (f.map) return f.map[v] ?? v
  if (f.formatter) return f.formatter(v)
  return v
}
</script>
