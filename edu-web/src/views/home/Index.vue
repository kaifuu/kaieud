<template>
  <div class="page home-page">
    <!-- 广告轮播位：基础设置-首页广告 上传（名师辅导/酬宾活动等），悬停暂停并高亮 -->
    <el-carousel v-if="banners.length" :interval="4500" :autoplay="adPlay" height="176px"
                 arrow="hover" class="ad-carousel"
                 @mouseenter="adPlay = false" @mouseleave="adPlay = true">
      <el-carousel-item v-for="(b, i) in banners" :key="i">
        <component
          :is="b.link ? 'a' : 'div'"
          :href="b.link || undefined" target="_blank" rel="noreferrer"
          class="ad-slide">
          <img :src="b.img" :alt="b.title || '广告图'" />
          <div v-if="b.title" class="ad-title">
            <el-icon class="ad-ico"><Promotion /></el-icon>{{ b.title }}
          </div>
        </component>
      </el-carousel-item>
    </el-carousel>

    <!-- 视角横幅：当前角色视角 + 问候 -->
    <div class="panel view-banner" :style="banners.length ? 'margin-top: 14px' : ''">
      <div class="vb-info">
        <div class="vb-title">{{ data.viewName || '首页概览' }}</div>
        <div class="vb-sub">
          {{ greeting }}，{{ userStore.user?.realName || userStore.user?.username || '' }}<template v-if="data.orgName"> · {{ data.orgName }}</template>
          <span class="vb-tip">数据按您的角色视角呈现，每 {{ REFRESH_MS / 1000 }} 秒自动刷新</span>
        </div>
      </div>
      <div class="vb-date">
        <div class="vb-day">{{ todayText }}</div>
        <div class="vb-week">{{ weekText }}</div>
      </div>
    </div>

    <!-- 指标卡：数字滚动动效 -->
    <el-row :gutter="14" style="margin-top: 14px">
      <el-col v-for="(c, i) in data.cards || []" :key="c.label" :span="cardSpan">
        <div class="stat-card panel">
          <div class="s-icon" :style="{ background: GRADS[i % GRADS.length] }">
            <el-icon :size="22"><component :is="ICONS[i % ICONS.length]" /></el-icon>
          </div>
          <div class="s-body">
            <div class="s-num">
              <CountUp :value="c.value" />
              <span v-if="c.unit" class="s-unit">{{ c.unit }}</span>
            </div>
            <div class="s-label">{{ c.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区：后端指定类型与跨度，按类型渲染（入场动效 + tooltip 自动轮播） -->
    <el-row :gutter="14" style="margin-top: 14px">
      <el-col v-for="c in data.charts || []" :key="c.key" :span="c.span || 12">
        <div class="panel chart-panel">
          <div class="panel-title">{{ c.title }}</div>
          <div :ref="(el) => setRef(c.key, el)" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 表格区 -->
    <el-row v-for="t in data.tables || []" :key="t.title" :gutter="14" style="margin-top: 14px">
      <el-col :span="24">
        <div class="panel" style="padding-bottom: 10px">
          <div class="panel-title">{{ t.title }}</div>
          <el-table :data="t.rows" size="small" stripe max-height="320">
            <el-table-column
              v-for="col in t.columns" :key="col.prop"
              :prop="col.prop" :label="col.label" :min-width="col.width || 100" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Promotion } from '@element-plus/icons-vue'
import api from '../../api'
import { useUserStore } from '../../store/user'
import CountUp from '../../components/CountUp.vue'

const userStore = useUserStore()
const data = ref({})
const chartEls = {}
const charts = []
const carouselTimers = []
let refreshTimer = null
/** 自动刷新间隔（页面可见时静默重拉，数字随之重新滚动） */
const REFRESH_MS = 90 * 1000

const ICONS = ['DataAnalysis', 'TrendCharts', 'Money', 'User', 'Calendar', 'Clock', 'Warning', 'School']
const GRADS = [
  'linear-gradient(135deg,#155EEF,#33B0FF)',
  'linear-gradient(135deg,#155EEF,#5B8DEF)',
  'linear-gradient(135deg,#5B8DEF,#B18CFF)',
  'linear-gradient(135deg,#5B8DEF,#C0A8FF)',
  'linear-gradient(135deg,#33B0FF,#7BE0FF)',
  'linear-gradient(135deg,#B18CFF,#E4B8FF)',
  'linear-gradient(135deg,#f79009,#fdb022)',
  'linear-gradient(135deg,#e04438,#f97066)'
]
const PALETTE = ['#155EEF', '#4FA8FF', '#5B8DEF', '#B18CFF', '#C9B8FF', '#7BE0FF', '#f79009', '#f04438']
const BLUE = '#155EEF'
const PURPLE = '#5B8DEF'
const axisStyle = {
  axisLine: { lineStyle: { color: '#d4dde9' } },
  axisLabel: { color: '#667085' },
  splitLine: { lineStyle: { color: '#eef2f7' } }
}
/** tooltip 轮播间隔 */
const CAROUSEL_MS = 2600

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const todayText = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
const weekText = new Date().toLocaleDateString('zh-CN', { weekday: 'long' })

// 指标卡每行个数：≤4 张一行铺满，6/8 张按 3/4 张一行
const cardSpan = computed(() => {
  const n = (data.value.cards || []).length
  const perRow = n <= 4 ? Math.max(n, 1) : (n % 3 === 0 ? 3 : 4)
  return 24 / perRow
})

/** 首页广告轮播：基础设置-首页广告 上传的 [{img, title, link}]，悬停暂停；
 *  开关 home_banner_enabled 关闭（'0'）时不显示，缺省视为开启 */
const banners = ref([])
const adPlay = ref(true)
async function loadBanners() {
  try {
    const cfg = (await api.configPublic()) || {}
    if (cfg.home_banner_enabled === '0') { banners.value = []; return }
    if (cfg.home_banners) {
      try {
        const arr = JSON.parse(cfg.home_banners)
        banners.value = Array.isArray(arr) ? arr.filter((b) => b && b.img) : []
      } catch (e) { banners.value = [] }
    }
  } catch (e) { /* 静默：无广告时保持原布局 */ }
}

function setRef(key, el) { if (el) chartEls[key] = el }

/** 销毁全部图表与轮播定时器（刷新数据前调用，避免实例与定时器泄漏） */
function destroyCharts() {
  carouselTimers.forEach(clearInterval)
  carouselTimers.length = 0
  charts.forEach((c) => c.dispose())
  charts.length = 0
}

/** tooltip 自动轮播：依次高亮各数据点，数字自动"动"起来 */
function startCarousel(inst, total) {
  if (!total || total < 2) return
  let idx = 0
  const timer = setInterval(() => {
    if (inst.isDisposed()) { clearInterval(timer); return }
    inst.dispatchAction({ type: 'downplay' })
    inst.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: idx })
    idx = (idx + 1) % total
  }, CAROUSEL_MS)
  carouselTimers.push(timer)
}

/* ===== 图表渲染：pie / rank(横向条形) / bar / line（含入场动效） ===== */
function renderChart(c) {
  const el = chartEls[c.key]
  if (!el) return
  const inst = echarts.init(el)
  const unit = c.unit ? `（${c.unit}）` : ''

  if (c.type === 'pie') {
    const items = (c.data || []).filter((d) => Number(d.value) > 0)
    inst.setOption({
      tooltip: { trigger: 'item', formatter: `{b}: {c}${unit} ({d}%)` },
      legend: { bottom: 0, type: 'scroll', textStyle: { color: '#667085' } },
      color: PALETTE,
      series: [{
        type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{d}%', color: '#475467' },
        data: items.map((d) => ({ name: d.name, value: Number(d.value) })),
        // 饼图逐块弹入
        animationType: 'scale', animationEasing: 'elasticOut',
        animationDuration: 900, animationDelay: (idx) => idx * 90
      }]
    })
    charts.push(inst)
    startCarousel(inst, items.length)
    return
  }

  if (c.type === 'rank') {
    // 横向渐变条（值倒序，第一在最上），逐条生长
    const items = (c.data || []).slice(0, 10).reverse()
    inst.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (p) => `${p[0].name}: ${p[0].value}${unit}` },
      grid: { left: 90, right: 40, top: 10, bottom: 30 },
      xAxis: { type: 'value', ...axisStyle },
      yAxis: { type: 'category', data: items.map((d) => d.name), ...axisStyle, splitLine: { show: false } },
      series: [{
        type: 'bar', barWidth: 14, borderRadius: 7,
        data: items.map((d) => Number(d.value) || 0),
        animationDuration: 900, animationEasing: 'cubicOut',
        animationDelay: (idx) => idx * 80,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: BLUE }, { offset: 1, color: PURPLE }
          ])
        }
      }]
    })
    charts.push(inst)
    startCarousel(inst, items.length)
    return
  }

  // bar / line：categories + 多序列，逐点入场
  const categories = c.categories || []
  const series = (c.series || []).map((s, i) => {
    if (c.type === 'line') {
      return {
        name: s.name, type: 'line', smooth: true, symbolSize: 7,
        data: (s.data || []).map((v) => Number(v) || 0),
        lineStyle: { width: 3, color: PALETTE[i % PALETTE.length] },
        itemStyle: { color: PALETTE[i % PALETTE.length] },
        animationDuration: 1200, animationEasing: 'cubicOut',
        animationDelay: (idx) => idx * 25,
        areaStyle: (c.series || []).length === 1 ? {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(21,94,239,0.35)' },
            { offset: 1, color: 'rgba(21,94,239,0.02)' }
          ])
        } : undefined
      }
    }
    return {
      name: s.name, type: 'bar', barWidth: 18, borderRadius: [6, 6, 0, 0],
      data: (s.data || []).map((v) => Number(v) || 0),
      animationDuration: 900, animationEasing: 'cubicOut',
      animationDelay: (idx) => idx * 60,
      itemStyle: { color: PALETTE[i % PALETTE.length] }
    }
  })
  inst.setOption({
    tooltip: { trigger: 'axis', axisPointer: c.type === 'line' ? undefined : { type: 'shadow' } },
    legend: (c.series || []).length > 1 ? { top: 0, right: 0, textStyle: { color: '#667085' } } : undefined,
    grid: { left: 60, right: 24, top: (c.series || []).length > 1 ? 36 : 20, bottom: 30 },
    xAxis: { type: 'category', data: categories, ...axisStyle, axisLabel: { color: '#667085', interval: 0, rotate: categories.length > 8 ? 30 : 0 } },
    yAxis: { type: 'value', ...axisStyle },
    series
  })
  charts.push(inst)
  startCarousel(inst, categories.length)
}

function onResize() { charts.forEach((c) => c.resize()) }

async function load() {
  data.value = (await api.overview()) || {}
  destroyCharts()
  await nextTick()
  ;(data.value.charts || []).forEach(renderChart)
}

onMounted(async () => {
  loadBanners()
  await load()
  window.addEventListener('resize', onResize)
  // 定时自动刷新：页面不可见时跳过，回到前台即刻补一次
  refreshTimer = setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, REFRESH_MS)
  document.addEventListener('visibilitychange', onVisible)
})
function onVisible() {
  if (document.visibilityState === 'visible') load()
}
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  document.removeEventListener('visibilitychange', onVisible)
  clearInterval(refreshTimer)
  destroyCharts()
})
</script>

<style scoped>
.home-page { overflow: auto; }
.view-banner {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 24px;
  background: linear-gradient(120deg, #f0f6ff 0%, #ffffff 55%, #f5efff 100%);
}
.vb-title {
  font-size: 19px; font-weight: 700; color: #142a44;
  background: linear-gradient(90deg, #155EEF, #7C5CFF);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.vb-sub { font-size: 13px; color: #667085; margin-top: 5px; }
.vb-tip { margin-left: 10px; color: #98a2b3; }
.vb-date { text-align: right; }

/* 广告轮播位：圆角大图，悬停暂停/微放大，标题角标高亮 */
.ad-carousel {
  border-radius: 14px; overflow: hidden;
  box-shadow: 0 8px 24px rgba(9, 30, 66, 0.10);
}
.ad-slide {
  position: relative; display: block; width: 100%; height: 100%;
  cursor: pointer; background: #f2f6fb;
}
.ad-slide img {
  width: 100%; height: 176px; object-fit: cover; display: block;
  transition: transform 0.4s ease;
}
.ad-carousel:hover .ad-slide img { transform: scale(1.03); }
.ad-title {
  position: absolute; left: 18px; bottom: 14px;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 16px; border-radius: 20px;
  color: #fff; font-size: 14.5px; font-weight: 600; letter-spacing: 1px;
  background: linear-gradient(90deg, rgba(21, 94, 239, 0.92), rgba(124, 92, 255, 0.85));
  box-shadow: 0 4px 14px rgba(21, 94, 239, 0.35);
  backdrop-filter: blur(4px);
  transition: transform 0.2s ease;
}
/* 悬停时标题突出放大 */
.ad-carousel:hover .ad-title { transform: translateY(-2px) scale(1.04); }
.ad-ico { font-size: 15px; }
.vb-day { font-size: 16px; font-weight: 600; color: #344054; }
.vb-week { font-size: 12px; color: #8fa8c4; margin-top: 2px; }

.stat-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px; margin-bottom: 8px;
  transition: transform 0.2s ease, box-shadow 0.25s ease;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 26px rgba(21, 94, 239, 0.16);
}
.s-icon {
  width: 46px; height: 46px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; box-shadow: 0 6px 14px rgba(21, 94, 239, 0.28);
}
.s-body { overflow: hidden; }
.s-num {
  font-size: 24px; font-weight: 700; color: #142a44; line-height: 1.1;
  white-space: nowrap; font-variant-numeric: tabular-nums;
}
.s-unit { font-size: 12px; font-weight: 500; color: #8fa8c4; margin-left: 4px; }
.s-label { font-size: 13px; color: #8fa8c4; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.chart-panel { height: 320px; }
.chart { height: calc(100% - 44px); }
</style>
