<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">基础设置</span>
    </div>
    <div class="panel" style="padding: 24px 28px; max-width: 640px">
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'

const loading = ref(false)
const saving = ref(false)
const form = ref({ logo_url: '', login_title: '', company_name: '', icp_no: '' })

async function load() {
  loading.value = true
  try {
    form.value = { logo_url: '', login_title: '', company_name: '', icp_no: '', ...(await api.configAll()) }
  } finally { loading.value = false }
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
</style>
