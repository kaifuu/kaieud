<template>
  <div class="rich-editor" :style="{ zIndex: zIdx }">
    <Toolbar class="rich-toolbar" :editor="editorRef" :defaultConfig="toolbarConfig" :mode="mode" />
    <Editor class="rich-editor-content" v-model="html" :defaultConfig="editorConfig" :mode="mode"
            :style="{ height: height + 'px', overflowY: 'hidden' }" @onCreated="handleCreated" />
  </div>
</template>

<script setup>
/**
 * 通用富文本编辑器（wangEditor 5，Vue3 封装）
 * 用法：<RichEditor v-model="form.content" :height="300" placeholder="请输入内容" />
 * 图片/视频插入走统一上传通道 /common/upload（jpg/png≤10M、mp4≤500M）。
 */
import { ref, shallowRef, onBeforeUnmount, watch } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import api from '../api'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请输入内容...' },
  height: { type: Number, default: 300 },
  /** 弹窗内使用时避免工具栏下拉被遮挡，可传更高层级 */
  zIdx: { type: Number, default: 100 }
})
const emit = defineEmits(['update:modelValue'])

const mode = 'default'
const editorRef = shallowRef()
const html = ref(props.modelValue || '<p><br/></p>')
watch(() => props.modelValue, (v) => { if (v !== html.value) html.value = v || '<p><br/></p>' })
watch(html, (v) => emit('update:modelValue', v))

/** 工具栏：常用排版 + 颜色/背景 + 列表/待办 + 对齐缩进行高 + 图片/视频/链接/表格 + 撤销重做/清格式 */
const toolbarConfig = {
  excludeKeys: ['fullScreen', 'group-more-style', 'codeBlock', 'code', 'clearStyle']
}
/** 通用上传（带类型/大小校验），成功后插入编辑器 */
async function uploadToEditor(file, insert, maxMB, exts) {
  const ext = String(file.name || '').split('.').pop().toLowerCase()
  if (!exts.includes(ext)) { ElMessage.warning(`仅支持 ${exts.join('/')} 格式`); return }
  if (file.size > maxMB * 1024 * 1024) { ElMessage.warning(`文件大小不能超过 ${maxMB}M`); return }
  const fd = new FormData()
  fd.append('file', file)
  try {
    const r = await api.upload(fd)
    insert(r.url, file.name)
  } catch (e) { ElMessage.error('上传失败') }
}
const editorConfig = {
  placeholder: props.placeholder,
  MENU_CONF: {
    uploadImage: {
      customUpload(file, insertFn) { uploadToEditor(file, (url, alt) => insertFn(url, alt, ''), 10, ['jpg', 'jpeg', 'png', 'gif']) }
    },
    uploadVideo: {
      customUpload(file, insertFn) { uploadToEditor(file, (url, poster) => insertFn(url, poster), 500, ['mp4']) }
    }
  }
}
const handleCreated = (editor) => { editorRef.value = editor }
onBeforeUnmount(() => { editorRef.value?.destroy() })
</script>

<style scoped>
.rich-editor { border: 1px solid #dfe7f3; border-radius: 8px; overflow: hidden; }
.rich-toolbar { border-bottom: 1px solid #edf2f9; }
.rich-editor-content :deep(.w-e-text-container) { background: #fff; overflow-y: auto; }
.rich-editor-content :deep(.w-e-text-container [data-slate-editor]) { padding: 12px 14px; font-size: 13.5px; }
</style>
