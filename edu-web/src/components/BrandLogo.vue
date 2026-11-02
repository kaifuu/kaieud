<template>
  <div class="brand-logo">
    <!-- 学士帽徽标：白色圆角磁贴 + 蓝紫渐变帽身 + 金色流苏 -->
    <div class="bl-mark" :class="{ 'bl-dark': dark }" :style="{ width: size + 'px', height: size + 'px' }">
      <svg :width="size * 0.62" :height="size * 0.62" viewBox="0 0 24 24" fill="none"
           xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <defs>
          <linearGradient id="bl-grad" x1="2" y1="3" x2="22" y2="20" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#155EEF" />
            <stop offset="1" stop-color="#7C5CFF" />
          </linearGradient>
        </defs>
        <!-- 帽顶（学士帽板） -->
        <path d="M12 2.6 1.2 8.4 12 14.2l9.2-4.85V8.4L12 2.6z"
              :fill="dark ? '#ffffff' : 'url(#bl-grad)'" />
        <!-- 帽身（底座） -->
        <path d="M5 13.4v3.9L12 21l7-3.7v-3.9L12 17.1 5 13.4z"
              :fill="dark ? 'rgba(255,255,255,0.82)' : '#5B8DEF'" fill-opacity="0.92" />
        <!-- 流苏绳 + 流苏穗 -->
        <path d="M20.6 9.6v5" stroke="#f79009" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="20.6" cy="15.9" r="1.5" fill="#f79009" />
      </svg>
    </div>
    <div v-if="showText" class="bl-texts">
      <span class="bl-name">{{ name }}</span>
      <span v-if="sub" class="bl-sub">{{ sub }}</span>
    </div>
  </div>
</template>

<script setup>
/** 品牌 Logo：学士帽教育徽标 + 字标（侧栏/登录页共用） */
defineProps({
  size: { type: Number, default: 34 },
  dark: { type: Boolean, default: false },     // 深色形态：白帽图标（配蓝色磁贴）
  showText: { type: Boolean, default: true },
  name: { type: String, default: '智教云课' },
  sub: { type: String, default: '' }
})
</script>

<style scoped>
.brand-logo { display: inline-flex; align-items: center; gap: 10px; }
.bl-mark {
  border-radius: 30%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(9, 30, 66, 0.25);
}
/* 深色形态：蓝紫渐变磁贴 + 白色帽身（登录卡片用） */
.bl-mark.bl-dark {
  background: linear-gradient(135deg, #155EEF, #7C5CFF);
  box-shadow: 0 6px 18px rgba(110, 91, 232, 0.45);
}
.bl-texts { display: flex; flex-direction: column; line-height: 1.15; white-space: nowrap; }
.bl-name {
  color: #fff; font-size: 17px; font-weight: 700; letter-spacing: 2.5px;
}
.bl-sub {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 10px; letter-spacing: 1.5px; text-transform: uppercase;
}
</style>
