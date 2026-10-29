<template>
  <!-- 照片上传：el-upload 标准用法 + 虚线上传区域，http-request 走带JWT的统一请求 -->
  <el-upload
    class="photo-uploader"
    :show-file-list="false"
    :auto-upload="autoUpload"
    :http-request="upload"
    :on-change="onChange"
    :before-upload="beforeUpload"
    accept="image/png,image/jpeg"
  >
    <div class="upload-area" :class="{ filled: !!url }">
      <!-- 已有照片：回显 + 悬停更换遮罩 -->
      <template v-if="url">
        <img :src="url" class="photo" />
        <div class="photo-mask">
          <el-icon :size="16"><Camera /></el-icon>
          <span>更换照片</span>
        </div>
      </template>
      <!-- 空状态：虚线框 + 加号上传提示 -->
      <template v-else>
        <el-icon :size="22" class="plus-icon"><Plus /></el-icon>
        <span class="plus-text">上传照片</span>
      </template>
    </div>
  </el-upload>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import api from '../api'
import { fetchAvatarUrl, clearAvatarCache } from '../utils/avatar'

const props = defineProps({
  /** 当前照片URL（空则显示上传区域） */
  url: { type: String, default: '' },
  /** 用户ID（照片存MySQL sys_user.avatar） */
  userId: { type: Number, default: null },
  /** false=暂存模式：选图仅本地预览并回抛文件，随表单保存时统一上传 */
  autoUpload: { type: Boolean, default: true },
  /** 占位文字（姓名首字母） */
  fallback: { type: String, default: '?' }
})
const emit = defineEmits(['success', 'select'])

/** 暂存模式：选图校验通过后本地预览 + 回抛File */
function onChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const previewUrl = URL.createObjectURL(file)
  emit('select', { file, previewUrl })
}

/** 上传前校验：格式与大小（5M） */
function beforeUpload(file) {
  const okType = ['image/png', 'image/jpeg'].includes(file.type)
  const okSize = file.size / 1024 / 1024 <= 5
  if (!okType) { ElMessage.error('照片仅支持 PNG / JPG 格式'); return false }
  if (!okSize) { ElMessage.error('照片大小不能超过 5M'); return false }
  if (!props.userId) { ElMessage.warning('请先保存用户信息，再上传照片'); return false }
  return true
}

/** 自定义上传：FormData 走统一 axios 实例（自动携带JWT） */
async function upload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  await api.userAvatarUpload(props.userId, fd)
  clearAvatarCache(props.userId)
  const url = await fetchAvatarUrl(props.userId)
  emit('success', url || '')
  ElMessage.success('照片已更新')
}
</script>

<style scoped>
.photo-uploader :deep(.el-upload) { border: none; background: none; height: auto; width: auto; }

/* 上传区域：空状态虚线圆框，悬停点亮主色 */
.upload-area {
  position: relative;
  width: 76px; height: 76px;
  border-radius: 50%;
  border: 1px dashed #c0ccda;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s ease, background 0.2s ease;
}
.upload-area:hover {
  border-color: #0094FF;
  background: #f5f9ff;
}
.plus-icon { color: #9db4cc; transition: color 0.2s ease; }
.plus-text { font-size: 11px; color: #9db4cc; letter-spacing: 1px; }
.upload-area:hover .plus-icon,
.upload-area:hover .plus-text { color: #0094FF; }

/* 已有照片：铺满 + 悬停更换遮罩 */
.upload-area.filled { border-style: solid; border-color: #e4eaf2; }
.upload-area.filled:hover { background: transparent; }
.photo { width: 100%; height: 100%; object-fit: cover; display: block; }
.photo-mask {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
  background: rgba(13, 34, 74, 0.55);
  color: #fff; font-size: 11px; letter-spacing: 1px;
  opacity: 0; transition: opacity 0.2s ease;
}
.upload-area.filled:hover .photo-mask { opacity: 1; }
</style>
