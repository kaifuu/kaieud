// 头像读取：带JWT拉取图像二进制并转为本地URL（缓存避免重复请求）
import request from '../api/request'

const cache = {}

export async function fetchAvatarUrl(id) {
  if (cache[id] !== undefined) return cache[id]
  try {
    const resp = await request.get('/sys/user/' + id + '/avatar', { responseType: 'blob', silent: true })
    const blob = resp.data
    if (!blob || !String(blob.type).startsWith('image')) {
      cache[id] = null
      return null
    }
    const url = URL.createObjectURL(blob)
    cache[id] = url
    return url
  } catch (e) {
    cache[id] = null
    return null
  }
}

/** 上传后清缓存，强制重新拉取 */
export function clearAvatarCache(id) { delete cache[id] }
