<template>
  <!-- 页面容器：亮蓝色渐变科技底，左右分区（左品牌场景 / 右登录卡片） -->
  <div class="login-page fade-in">
    <div class="grid-overlay"></div>
    <div class="blob blob-a"></div>
    <div class="blob blob-b"></div>

    <div class="login-panel">
      <!-- ============ 左侧：教育科技品牌区 ============ -->
      <div class="brand">
        <svg class="scene" viewBox="0 0 640 520" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <defs>
            <radialGradient id="coreGlow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="#BFE9FF" stop-opacity="0.9"/>
              <stop offset="55%" stop-color="#4FC3FF" stop-opacity="0.35"/>
              <stop offset="100%" stop-color="#0094FF" stop-opacity="0"/>
            </radialGradient>
            <linearGradient id="capGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#FFFFFF"/>
              <stop offset="100%" stop-color="#BEE7FF"/>
            </linearGradient>
            <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#D4C8FF"/>
              <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0.1"/>
            </linearGradient>
          </defs>

          <!-- 三层知识轨道环：不同速度/方向旋转 -->
          <g class="spin-slow">
            <circle cx="320" cy="248" r="216" stroke="#FFFFFF" stroke-opacity="0.35"
                    stroke-width="1.5" stroke-dasharray="5 16"/>
            <circle cx="320" cy="32" r="4" fill="#AEE9FF"/>
          </g>
          <g class="spin-rev">
            <circle cx="320" cy="248" r="158" stroke="url(#ringGrad)" stroke-width="2.5"
                    stroke-dasharray="80 340" stroke-linecap="round"/>
            <circle cx="320" cy="90" r="6" fill="#FFFFFF">
              <animate attributeName="r" values="5;7;5" dur="4s" repeatCount="indefinite"/>
            </circle>
          </g>
          <g class="spin-fast">
            <circle cx="320" cy="248" r="112" stroke="#FFFFFF" stroke-opacity="0.3" stroke-width="1"/>
            <circle cx="432" cy="248" r="7" fill="#FFFFFF"/>
            <circle cx="432" cy="248" r="13" fill="#FFFFFF" opacity="0.25"/>
          </g>
          <g class="spin-fast2">
            <circle cx="320" cy="248" r="72" stroke="#FFFFFF" stroke-opacity="0.25" stroke-width="1"/>
            <circle cx="320" cy="176" r="5" fill="#D9F3FF"/>
          </g>

          <!-- 中心光晕 + 学士帽（教育核心意象） -->
          <circle cx="320" cy="248" r="128" fill="url(#coreGlow)" class="core-pulse"/>
          <g transform="translate(320 248)">
            <polygon points="0,-48 80,-15 0,18 -80,-15" fill="url(#capGrad)"/>
            <path d="M-36 4 v28 q36 24 72 0 v-28 l-36 15 z" fill="#6E5BE8"/>
            <line x1="46" y1="-8" x2="46" y2="36" stroke="#FFD166" stroke-width="3.5"/>
            <circle cx="46" cy="41" r="5.5" fill="#FFD166" class="tassel"/>
            <path d="M-22 56 h44" stroke="#D6F0FF" stroke-width="3.5" stroke-linecap="round" opacity="0.9"/>
          </g>

          <!-- 轨道上的教育元素小徽标：书 / 日历 / 学员 -->
          <g class="spin-rev2">
            <g transform="translate(320 76)">
              <rect x="-17" y="-17" width="34" height="34" rx="9" fill="#FFFFFF" opacity="0.95"/>
              <path d="M-9 -3 C -5 -6, -2 -6, 0 -4 C 2 -6, 5 -6, 9 -3 L 9 6 C 5 4, 2 4, 0 6 C -2 4, -5 4, -9 6 Z"
                    fill="none" stroke="#0094FF" stroke-width="2"/>
            </g>
          </g>
          <g class="spin-slow2">
            <g transform="translate(320 420)">
              <rect x="-17" y="-17" width="34" height="34" rx="9" fill="#FFFFFF" opacity="0.95"/>
              <rect x="-9" y="-8" width="18" height="16" rx="3" fill="none" stroke="#7B5BE6" stroke-width="2"/>
              <line x1="-9" y1="-3" x2="9" y2="-3" stroke="#7B5BE6" stroke-width="2"/>
            </g>
          </g>
          <g class="spin-fast">
            <g transform="translate(208 248)">
              <rect x="-17" y="-17" width="34" height="34" rx="17" fill="#FFFFFF" opacity="0.95"/>
              <circle cx="0" cy="-4" r="5" fill="#0094FF"/>
              <path d="M-7 9 C -7 3, -3 1, 0 1 C 3 1, 7 3, 7 9" fill="#0094FF"/>
            </g>
          </g>

          <!-- 流光链路：知识传输（4s循环） -->
          <path d="M392 190 C 470 140, 530 110, 596 96" stroke="#FFFFFF" stroke-opacity="0.35" stroke-width="1.5"/>
          <path d="M392 190 C 470 140, 530 110, 596 96" stroke="#DCD2FF" stroke-width="3"
                stroke-linecap="round" stroke-dasharray="26 300">
            <animate attributeName="stroke-dashoffset" from="326" to="0" dur="4s" repeatCount="indefinite"/>
          </path>
          <path d="M250 310 C 190 360, 150 400, 108 440" stroke="#FFFFFF" stroke-opacity="0.35" stroke-width="1.5"/>
          <path d="M250 310 C 190 360, 150 400, 108 440" stroke="#DCD2FF" stroke-width="3"
                stroke-linecap="round" stroke-dasharray="26 300">
            <animate attributeName="stroke-dashoffset" from="326" to="0" dur="4s" begin="-2s" repeatCount="indefinite"/>
          </path>

          <!-- 悬浮数据徽标：课时趋势 / 周课时 -->
          <g class="float-a">
            <rect x="66" y="128" width="118" height="64" rx="12" fill="#FFFFFF" opacity="0.92"/>
            <polyline points="82,172 98,152 112,160 132,142 152,150 168,140"
                      stroke="#0094FF" stroke-width="3" fill="none" stroke-linecap="round"/>
            <circle cx="168" cy="140" r="4" fill="#FFB648"/>
            <text x="82" y="150" font-size="10" fill="#5B9BD0" font-family="sans-serif">+12.6%</text>
          </g>
          <g class="float-b">
            <rect x="470" y="368" width="126" height="58" rx="12" fill="#FFFFFF" opacity="0.92"/>
            <rect x="484" y="392" width="12" height="22" rx="3" fill="#8FCFF7"/>
            <rect x="502" y="384" width="12" height="30" rx="3" fill="#0094FF"/>
            <rect x="520" y="378" width="12" height="36" rx="3" fill="#8B6FF0"/>
            <rect x="538" y="390" width="12" height="24" rx="3" fill="#0094FF" opacity="0.55"/>
            <text x="558" y="404" font-size="10" fill="#5B9BD0" font-family="sans-serif">周课时</text>
          </g>
        </svg>

        <h1 class="slogan up-1">{{ cfg.login_title || '智教云课 · 课时管理平台' }}</h1>
        <p class="sub up-2">连接老师、学生与教务，让每一课时都有价值</p>
        <div class="tags up-3">
          <span>智能排课</span><span>课时追踪</span><span>数据洞察</span><span>家校协同</span>
        </div>
        <p v-if="cfg.company_name || cfg.icp_no" class="icp up-3">
          © {{ new Date().getFullYear() }} {{ cfg.company_name }}
          <a v-if="cfg.icp_no" href="https://beian.miit.gov.cn" target="_blank" rel="noreferrer">{{ cfg.icp_no }}</a>
        </p>
      </div>

      <!-- ============ 右侧：登录卡片 ============ -->
      <div class="card-zone">
        <div class="login-card card-in">
          <div class="card-head">
            <img v-if="cfg.logo_url" :src="cfg.logo_url" class="brand-mark brand-img" alt="logo" />
            <div v-else class="brand-mark">E</div>
            <div>
              <h2>欢迎登录</h2>
              <p>EDU COURSE MANAGEMENT SYSTEM</p>
            </div>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="onLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" size="large" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input v-model="form.captchaCode" placeholder="验证码" size="large" maxlength="4">
                  <template #prefix><el-icon><Key /></el-icon></template>
                </el-input>
                <canvas ref="captchaRef" width="112" height="40" class="captcha-canvas"
                        title="看不清？点击刷新" @click="drawCaptcha"></canvas>
              </div>
            </el-form-item>
            <el-button class="login-btn" size="large" :loading="loading" @click="onLogin">
              {{ loading ? '登录中…' : '登 录' }}
            </el-button>
          </el-form>

          <div class="tips">推荐 Chrome / Edge 浏览器访问 · 忘记密码请联系教务管理员</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { buildDynamicRoutes } from '../router'

const router = useRouter()
const store = useUserStore()

const formRef = ref(null)
const captchaRef = ref(null)
const loading = ref(false)
const captchaCode = ref('') // Canvas当前验证码答案（仅前端校验）
const cfg = ref({ logo_url: '', login_title: '', company_name: '', icp_no: '' })

const form = ref({ username: 'admin', password: '123456', captchaCode: '' })

/** 表单校验：非空 + 验证码比对，提示柔和 */
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码长度至少4位', trigger: 'blur' }
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!value || value.toUpperCase() !== captchaCode.value) {
          callback(new Error('验证码不正确，可点击图片刷新'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** Canvas绘制本地验证码：随机4位字符 + 柔和干扰 */
const drawCaptcha = () => {
  const canvas = captchaRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 4; i++) code += chars[Math.floor(Math.random() * chars.length)]
  captchaCode.value = code

  ctx.clearRect(0, 0, 112, 40)
  ctx.fillStyle = '#EAF5FF'
  ctx.fillRect(0, 0, 112, 40)
  for (let i = 0; i < 2; i++) {
    ctx.strokeStyle = `rgba(0,148,255,${0.15 + Math.random() * 0.1})`
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(0, 8 + Math.random() * 24)
    ctx.bezierCurveTo(30, Math.random() * 40, 70, Math.random() * 40, 112, 8 + Math.random() * 24)
    ctx.stroke()
  }
  for (let i = 0; i < 12; i++) {
    ctx.fillStyle = `rgba(0,148,255,${0.15 + Math.random() * 0.2})`
    ctx.beginPath()
    ctx.arc(Math.random() * 112, Math.random() * 40, 1.2, 0, Math.PI * 2)
    ctx.fill()
  }
  for (let i = 0; i < 4; i++) {
    ctx.save()
    ctx.translate(18 + i * 25, 27 + (Math.random() * 4 - 2))
    ctx.rotate((Math.random() * 24 - 12) * Math.PI / 180)
    ctx.font = 'bold 23px Consolas, monospace'
    ctx.fillStyle = ['#0094FF', '#2FA8FF', '#0072CC', '#57B5FF'][Math.floor(Math.random() * 4)]
    ctx.textAlign = 'center'
    ctx.fillText(code[i], 0, 0)
    ctx.restore()
  }
}

/** 登录：本地校验后调用接口（本地验证码，无需服务端captchaKey） */
const onLogin = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await store.login({ username: form.value.username, password: form.value.password })
      buildDynamicRoutes(store.menus)
      router.push('/home')
    } catch (e) {
      drawCaptcha()
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  drawCaptcha()
  // 加载基础设置（LOGO/标题/公司名/备案号），失败用默认值
  api.configPublic().then((d) => { Object.assign(cfg.value, d || {}) }).catch(() => {})
})
</script>

<style scoped>
/* ===== 页面容器：亮蓝色科技渐变底 ===== */
.login-page {
  position: relative;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  /* 兜底纯色：即使渐变未加载也不会出现白底白字 */
  background-color: #2a6ae8;
  background:
    radial-gradient(1100px 700px at 12% 15%, rgba(200, 185, 255, 0.4) 0%, transparent 55%),
    radial-gradient(900px 700px at 88% 85%, rgba(70, 50, 190, 0.45) 0%, transparent 60%),
    linear-gradient(125deg, #0080F0 0%, #3D7BFA 42%, #6E5BE8 78%, #8B6FF0 100%);
}
.fade-in { animation: page-fade 0.7s ease-out both; }
@keyframes page-fade { from { opacity: 0; } to { opacity: 1; } }

/* 科技网格（白色细线，低调） */
.grid-overlay {
  position: absolute; inset: 0; pointer-events: none; opacity: 0.07;
  background-image:
    linear-gradient(rgba(255,255,255,0.9) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.9) 1px, transparent 1px);
  background-size: 48px 48px;
  animation: grid-pan 26s linear infinite;
}
@keyframes grid-pan { to { background-position: 48px 48px; } }

/* 漂移光斑 */
.blob { position: absolute; border-radius: 50%; filter: blur(90px); pointer-events: none; }
.blob-a { width: 480px; height: 480px; left: -140px; top: -160px;
  background: rgba(196, 180, 252, 0.45); animation: drift 18s ease-in-out infinite alternate; }
.blob-b { width: 560px; height: 560px; right: -180px; bottom: -200px;
  background: rgba(40, 50, 170, 0.4); animation: drift 22s ease-in-out infinite alternate-reverse; }
@keyframes drift { to { transform: translate(70px, 46px) scale(1.15); } }

/* ===== 左右分区布局 ===== */
.login-panel {
  position: relative; z-index: 1; height: 100%;
  display: flex; align-items: center; justify-content: space-evenly; gap: 40px;
  padding: 0 5vw;
}

/* ---------- 左侧品牌区 ---------- */
.brand { max-width: 580px; }
.scene { width: min(560px, 40vw); display: block; margin: 0 auto; animation: scene-float 9s ease-in-out infinite; }
@keyframes scene-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-10px); } }

/* 轨道旋转（多层不同速度/方向） */
.spin-slow  { transform-origin: 320px 248px; animation: spin 40s linear infinite; }
.spin-slow2 { transform-origin: 320px 248px; animation: spin 30s linear infinite; }
.spin-rev   { transform-origin: 320px 248px; animation: spin-rev 26s linear infinite; }
.spin-rev2  { transform-origin: 320px 248px; animation: spin-rev 20s linear infinite; }
.spin-fast  { transform-origin: 320px 248px; animation: spin 14s linear infinite; }
.spin-fast2 { transform-origin: 320px 248px; animation: spin-rev 11s linear infinite; }
@keyframes spin     { to { transform: rotate(360deg); } }
@keyframes spin-rev { to { transform: rotate(-360deg); } }

/* 中心光晕呼吸 + 帽穗摆动 */
.core-pulse { transform-origin: 320px 248px; animation: pulse 4.5s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 0.75; } 50% { opacity: 1; } }
.tassel { transform-origin: 46px -8px; animation: tassel-swing 3.2s ease-in-out infinite; }
@keyframes tassel-swing { 0%,100% { transform: rotate(-9deg); } 50% { transform: rotate(9deg); } }

/* 数据徽标漂浮 */
.float-a, .float-b { animation: floaty 5.5s ease-in-out infinite; }
.float-b { animation-delay: -2.6s; }
@keyframes floaty { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-14px); } }

/* 标语区：分层上浮入场 */
.slogan { color: #fff; font-size: 34px; font-weight: 700; letter-spacing: 4px;
  text-align: center; margin: 14px 0 10px;
  text-shadow: 0 4px 24px rgba(0, 60, 140, 0.35); }
.sub { color: #E6E1FF; text-align: center; font-size: 15px; letter-spacing: 1.5px; margin: 0; }
.tags { display: flex; gap: 12px; justify-content: center; margin-top: 24px; flex-wrap: wrap; }
.tags span { color: #EAF7FF; font-size: 13px; padding: 7px 18px; border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.45); background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(6px); transition: background 0.25s, transform 0.25s; }
.tags span:hover { background: rgba(255, 255, 255, 0.26); transform: translateY(-2px); }
.up-1 { animation: rise 0.8s cubic-bezier(0.22, 0.8, 0.32, 1) 0.15s both; }
.up-2 { animation: rise 0.8s cubic-bezier(0.22, 0.8, 0.32, 1) 0.3s both; }
.up-3 { animation: rise 0.8s cubic-bezier(0.22, 0.8, 0.32, 1) 0.45s both; }
@keyframes rise { from { opacity: 0; transform: translateY(18px); } to { opacity: 1; transform: translateY(0); } }

/* ---------- 右侧登录卡片（白色玻璃，渐变描边，柔和发光） ---------- */
.card-zone { flex-shrink: 0; }
.login-card {
  width: min(410px, 92vw);
  padding: 40px 38px 24px;
  border-radius: 20px;
  background:
    linear-gradient(rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.88)) padding-box,
    linear-gradient(160deg, rgba(255, 255, 255, 0.95), rgba(139, 111, 240, 0.4) 50%, rgba(255, 255, 255, 0.6)) border-box;
  border: 1.2px solid transparent;
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow:
    0 26px 70px rgba(30, 30, 110, 0.35),
    0 0 60px rgba(190, 175, 255, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}
/* 卡片自右向左滑入 */
.card-in { animation: card-in 0.85s cubic-bezier(0.22, 0.8, 0.32, 1) 0.1s both; }
@keyframes card-in {
  from { opacity: 0; transform: translateX(46px); }
  to { opacity: 1; transform: translateX(0); }
}

.card-head { display: flex; align-items: center; gap: 14px; margin-bottom: 26px; }
.brand-mark {
  width: 46px; height: 46px; border-radius: 13px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; font-weight: 800; color: #fff;
  background: linear-gradient(135deg, #0094FF, #8B6FF0);
  box-shadow: 0 6px 18px rgba(110, 91, 232, 0.45);
}
.card-head h2 { margin: 0; font-size: 21px; color: #142a44; letter-spacing: 1px; }
.card-head p { margin: 3px 0 0; font-size: 11px; color: #8fa8c4; letter-spacing: 1.5px; }

/* 输入框：聚焦亮起蓝色渐变光 */
.login-card :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 0 0 1px #d6e7f7 inset;
  transition: box-shadow 0.25s ease;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1.5px #6E5BE8 inset,
    0 0 0 4px rgba(110, 91, 232, 0.1),
    0 0 14px rgba(0, 148, 255, 0.2);
}
.login-card :deep(.el-input__prefix .el-icon) { color: #6ea6d8; }
.login-card :deep(.el-input__inner) { color: #142a44; }

.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-canvas {
  flex-shrink: 0; width: 112px; height: 40px; border-radius: 10px;
  cursor: pointer; transition: box-shadow 0.2s ease, transform 0.15s ease;
}
.captcha-canvas:hover { box-shadow: 0 0 10px rgba(0, 148, 255, 0.3); transform: scale(1.02); }

/* 登录按钮：hover轻微放大 + 柔光扩散 + 流光平移 */
.login-btn {
  width: 100%; margin-top: 4px; border: none; border-radius: 10px;
  font-size: 16px; font-weight: 600; letter-spacing: 6px; color: #fff;
  background: linear-gradient(110deg, #0080E8 0%, #0094FF 28%, #7B5BE6 55%, #0094FF 78%, #0080E8 100%);
  background-size: 220% 100%;
  box-shadow: 0 8px 22px rgba(0, 120, 220, 0.4);
  transition: transform 0.2s ease, box-shadow 0.25s ease, background-position 0.6s ease;
}
.login-btn:hover {
  transform: scale(1.015);
  background-position: 100% 0;
  box-shadow: 0 10px 30px rgba(100, 90, 235, 0.5), 0 0 0 7px rgba(255, 255, 255, 0.14);
}
.login-btn:active { transform: scale(0.99); }

/* 校验提示：柔和低饱和暖色 */
.login-card :deep(.el-form-item__error) { color: #d9907f; font-size: 12px; padding-top: 3px; }
.login-card :deep(.el-form-item.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px #e8c4b8 inset; }

.tips { margin-top: 16px; font-size: 12px; color: #9db4cc; text-align: center; }

/* ===== 响应式：窄屏隐藏品牌区，卡片居中并缩小 ===== */
@media (max-width: 1024px) {
  .brand { display: none; }
  .panel { justify-content: center; }
}
@media (max-width: 768px) {
  .login-card { padding: 30px 22px 18px; border-radius: 16px; }
}
</style>
