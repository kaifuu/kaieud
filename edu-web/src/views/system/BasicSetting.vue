<template>
  <div class="page tabs-page">
    <div class="page-header">
      <span class="page-title">基础设置</span>
    </div>

    <div class="panel" style="padding: 0 16px; margin-bottom: 14px">
      <el-tabs v-model="tab">
        <el-tab-pane label="系统设置" name="base" />
        <el-tab-pane label="首页广告" name="banner" />
      </el-tabs>
    </div>

    <!-- TAB1 系统设置（原有配置） -->
    <div v-show="tab === 'base'" class="panel" style="padding: 24px 28px; max-width: 640px">
      <el-form label-width="120px" v-loading="loading">
        <el-form-item label="系统LOGO">
          <div class="logo-upload">
            <img v-if="form.logo_url" :src="form.logo_url" class="logo-preview" />
            <div v-else class="logo-preview logo-empty">E</div>
            <div class="logo-ops">
              <input type="file" accept=".png,.jpg,.jpeg" id="logo-file" style="display:none" @change="onLogo" />
              <el-button size="small" @click="document.getElementById('logo-file').click()">上传LOGO（左上角显示）</el-button>
              <el-button v-if="form.logo_url" size="small" text type="danger" @click="form.logo_url = ''">移除</el-button>
              <div class="u-tip">支持 png/jpg，建议正方形，用于登录页与侧栏展示</div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="登录页主标题">
          <el-input v-model="form.login_title" maxlength="30" placeholder="如：智教云课 · 课时管理平台" />
        </el-form-item>
        <el-form-item label="公司名称">
          <el-input v-model="form.company_name" maxlength="60" placeholder="展示在登录页的公司/机构名称" />
        </el-form-item>
        <el-form-item label="ICP备案号">
          <el-input v-model="form.icp_no" maxlength="50" placeholder="如：京ICP备2026000000号-1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
          <el-button @click="load">还原</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false"
                title="保存后刷新浏览器即可在登录页与侧栏看到新的LOGO、公司名称与备案号" />
    </div>

    <!-- TAB2 首页广告：上传轮播图（名师辅导/双十一酬宾等），展示在登录后首页顶部横幅 -->
    <div v-show="tab === 'banner'" class="panel" style="padding: 20px 24px; max-width: 820px" v-loading="loading">
      <div class="b-switch-row">
        <el-switch v-model="bannerEnabled" />
        <span class="b-switch-label">启用首页广告</span>
        <span class="u-tip">关闭后首页顶部不显示广告轮播，图片配置保留</span>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px"
                title="上传首页顶部横幅广告图（如「名师一对一辅导」「双十一报课酬宾」）；建议 1600×360 或同比例宽图，支持多张轮播，鼠标悬停暂停并高亮标题" />
      <div v-for="(b, i) in banners" :key="i" class="banner-item">
        <div class="b-idx">{{ i + 1 }}</div>
        <img :src="b.img" class="b-img" preview />
        <div class="b-fields">
          <el-input v-model="b.title" maxlength="20" placeholder="广告标题（悬停展示/角标，如：名师一对一辅导 8折起">
            <template #prepend>标题</template>
          </el-input>
          <el-input v-model="b.link" maxlength="200" placeholder="跳转链接（选填，点击广告新窗口打开）">
            <template #prepend>链接</template>
          </el-input>
        </div>
        <div class="b-ops">
          <el-button link :disabled="i === 0" @click="moveBanner(i, -1)">上移</el-button>
          <el-button link :disabled="i === banners.length - 1" @click="moveBanner(i, 1)">下移</el-button>
          <el-button link type="danger" @click="banners.splice(i, 1)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!banners.length" description="暂无广告，上传图片并保存后即可在首页顶部展示" :image-size="70" />
      <div class="b-actions">
        <input type="file" accept=".png,.jpg,.jpeg" id="banner-file" style="display:none" multiple @change="onBannerFiles" />
        <el-button @click="document.getElementById('banner-file').click()">上传广告图</el-button>
        <el-button type="primary" :loading="saving" @click="saveBanners">保存广告</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const tab = ref('base')
const loading = ref(false)
const saving = ref(false)
const form = ref({ logo_url: '', login_title: '', company_name: '', icp_no: '' })

/* TAB2 首页广告：[{img, title, link}]，整体以 JSON 存在 sys_config.home_banners；
   启用开关存 home_banner_enabled（'1'/'0'，缺省视为开启） */
const banners = ref([])
const bannerEnabled = ref(true)

async function load() {
  loading.value = true
  try {
    const cfg = await api.configAll()
    form.value = { logo_url: '', login_title: '', company_name: '', icp_no: '', ...cfg }
    banners.value = parseBanners(cfg.home_banners)
    bannerEnabled.value = cfg.home_banner_enabled !== '0'
  } finally { loading.value = false }
}

function parseBanners(raw) {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.filter((b) => b && b.img) : []
  } catch (e) { return [] }
}

async function onLogo(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  const r = await api.upload(fd)
  form.value.logo_url = r.url
  ElMessage.success('LOGO已上传，保存设置后生效')
  e.target.value = ''
}

/* 上传广告图（支持多选）：逐张上传后追加到列表末尾 */
async function onBannerFiles(e) {
  const files = [...e.target.files]
  if (!files.length) return
  loading.value = true
  try {
    for (const file of files) {
      const fd = new FormData()
      fd.append('file', file)
      const r = await api.upload(fd)
      banners.value.push({ img: r.url, title: '', link: '' })
    }
    ElMessage.success('已上传，填写标题后点击「保存广告」生效')
  } finally {
    loading.value = false
    e.target.value = ''
  }
}

function moveBanner(i, step) {
  const j = i + step
  if (j < 0 || j >= banners.value.length) return
  const arr = banners.value
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
}

async function saveBanners() {
  saving.value = true
  try {
    await api.configSave({
      home_banners: JSON.stringify(banners.value),
      home_banner_enabled: bannerEnabled.value ? '1' : '0'
    })
    ElMessage.success('首页广告已保存，刷新首页即可看到')
  } finally { saving.value = false }
}

async function save() {
  saving.value = true
  try {
    await api.configSave(form.value)
    ElMessage.success('已保存')
  } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.logo-upload { display: flex; align-items: center; gap: 16px; }
.logo-preview {
  width: 64px; height: 64px; border-radius: 14px; object-fit: cover;
  border: 1px solid #e4eaf2;
  display: flex; align-items: center; justify-content: center;
  font-size: 30px; font-weight: 800; color: #fff;
  background: linear-gradient(135deg, #155EEF, #5B8DEF);
}
.logo-empty { content: 'E'; }
.logo-ops { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.u-tip { color: #98a2b3; font-size: 12px; }

/* 广告条目：序号 + 缩略图 + 标题/链接 + 排序删除 */
.banner-item {
  display: flex; align-items: center; gap: 14px;
  padding: 12px 14px; margin-bottom: 10px;
  border: 1px solid #e8eef7; border-radius: 12px;
  background: #fbfdff;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}
.banner-item:hover { border-color: rgba(21, 94, 239, 0.4); box-shadow: 0 4px 14px rgba(21, 94, 239, 0.1); }
.b-idx {
  width: 22px; height: 22px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #155EEF, #5B8DEF);
  color: #fff; font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.b-img {
  width: 150px; height: 64px; border-radius: 8px; object-fit: cover;
  border: 1px solid #e4eaf2; flex-shrink: 0; background: #f2f6fb;
}
.b-fields { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.b-ops { display: flex; flex-direction: column; align-items: stretch; flex-shrink: 0; }
.b-actions { display: flex; gap: 10px; margin-top: 6px; }
.b-switch-row { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.b-switch-label { font-size: 14px; font-weight: 600; color: #344054; }
</style>
