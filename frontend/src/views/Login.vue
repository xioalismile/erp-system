<template>
  <div class="login-container">
    <!-- 右上角工具条（2026-10-18）：官网入口 + 多语言切换，同一行对齐，未登录也可用 -->
    <div class="login-lang-switch">
      <a class="site-entry" href="/promo.html" target="_blank" rel="noopener" title="打开楷煜官网">
        <span>访问官网</span>
        <span class="site-entry-arrow" aria-hidden="true">↗</span>
      </a>
      <span class="login-topbar-divider" aria-hidden="true"></span>
      <LanguageSwitch light />
    </div>

    <!-- ========== 新布局（2026-09-13 登录页改版）：system.login_bg 非空时启用 ==========
         做法：把设计图整张作为背景（画布 2279×1280，等比缩放铺满窗口），
               中间压一块真实登录表单，位置/尺寸取设计图实测像素，正好盖住图里画好的登录框。
         设计图实测（2279×1280）：登录框 x 771..1506、y 345..966 → 表单外扩 6px 保证完全覆盖。 -->
    <template v-if="useNewLayout">
      <div class="nl-stage">
        <div
          class="nl-canvas"
          :style="[{ backgroundImage: 'url(' + appConfigStore.loginBg + ')', '--nl-s': nlScale }, nlVars]"
        >
          <aside class="nl-panel" aria-label="登录表单">
            <h1 class="nl-title">{{ cardTitle }}</h1>
            <el-form :model="form" @submit.prevent="handleLogin" label-width="0" class="nl-form">
              <div class="nl-row">
                <label class="nl-label">{{ $t('login.username', '用户名') }}:</label>
                <el-input v-model="form.username" :placeholder="$t('login.username', '用户名')" size="large"
                  :autocomplete="rememberMe ? 'username' : 'off'" />
              </div>
              <div class="nl-row">
                <label class="nl-label">{{ $t('login.password', '登录密码') }}:</label>
                <!-- 记住密码（保留）：勾选时用标准 current-password，浏览器才肯保存并回填登录密码；
                     不勾选时 off，不保存。密码仍由浏览器密码管理器保管，系统只记账号、不存密码 -->
                <el-input v-model="form.password" type="password" :placeholder="$t('login.password', '密码')" size="large"
                  show-password :autocomplete="rememberMe ? 'current-password' : 'off'" />
              </div>
              <div class="nl-row">
                <label class="nl-label">{{ $t('login.phone', '手机登录') }}:</label>
                <el-input v-model="form.phone" :placeholder="$t('login.phone', '手机号')" size="large" autocomplete="off" />
              </div>
              <div class="nl-opt">
                <div class="remember-wrap" @click.stop="toggleRemember" role="checkbox" :aria-checked="rememberMe">
                  <span class="custom-checkbox" :class="{ checked: rememberMe }">
                    <span v-if="rememberMe" class="check-mark">✔</span>
                  </span>
                  <span class="check-label">{{ $t('login.rememberPwd', '记住账号密码') }}</span>
                </div>
              </div>
              <div class="nl-btn-row">
                <el-button class="nl-login-btn" native-type="submit" :loading="loading">
                  {{ $t('login.submit', '登 录') }}
                </el-button>
              </div>
            </el-form>
          </aside>
        </div>
      </div>
    </template>

    <!-- ========== 现有布局（login_bg 为空时，各环境默认） ========== -->
    <template v-else>
    <!-- 动态科技背景元素（深色 + 丰富 + 鼠标联动） -->
    <div
      class="bg-layer"
      aria-hidden="true"
      :style="{
        '--mx': mouse.x + 'px',
        '--my': mouse.y + 'px',
        '--mvx': (mouse.x - mouse.lx) * 0.015 + 'px',
        '--mvy': (mouse.y - mouse.ly) * 0.015 + 'px',
      }"
    >
      <!-- 1. 深色渐变基底（墨黑→深蓝→墨绿） -->
      <div class="bg-gradient" />
      <div class="bg-vignette" />

      <!-- 2. 色斑 3 层（更深色调 + 视差） -->
      <div class="bg-blob bb-1" :style="{ transform: `translate(${mouse.x * 0.012}px, ${mouse.y * 0.018}px)` }" />
      <div class="bg-blob bb-2" :style="{ transform: `translate(${-mouse.x * 0.01}px, ${-mouse.y * 0.014}px)` }" />
      <div class="bg-blob bb-3" :style="{ transform: `translate(${mouse.x * 0.02}px, ${-mouse.y * 0.008}px)` }" />

      <!-- 3. 数字雨矩阵（左侧一条） -->
      <div class="matrix-col mc-1">
        <span v-for="n in 18" :key="'m1-'+n" class="matrix-char" :style="matrixCharStyle(1, n)">{{ matrixChar(1, n) }}</span>
      </div>
      <div class="matrix-col mc-2">
        <span v-for="n in 20" :key="'m2-'+n" class="matrix-char" :style="matrixCharStyle(2, n)">{{ matrixChar(2, n) }}</span>
      </div>
      <div class="matrix-col mc-3">
        <span v-for="n in 16" :key="'m3-'+n" class="matrix-char" :style="matrixCharStyle(3, n)">{{ matrixChar(3, n) }}</span>
      </div>

      <!-- 4. 旋转三角/几何网格（右侧 SVG 装饰） -->
      <svg class="geo-grid" viewBox="0 0 800 800" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <defs>
          <pattern id="triGrid" width="80" height="70" patternUnits="userSpaceOnUse">
            <path d="M0 70 L40 0 L80 70 Z" fill="none" stroke="rgba(20, 217, 183, 0.10)" stroke-width="1"/>
          </pattern>
          <radialGradient id="triGlow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="rgba(20, 217, 183, 0.28)"/>
            <stop offset="100%" stop-color="rgba(20, 217, 183, 0)"/>
          </radialGradient>
        </defs>
        <g class="grid-spin-slow">
          <rect x="-400" y="-400" width="1600" height="1600" fill="url(#triGrid)"/>
        </g>
        <circle class="pulse-dot" cx="400" cy="400" r="260" fill="url(#triGlow)"/>
      </svg>

      <!-- 5. 脉冲点阵圆周（围绕中心信号环） -->
      <div class="pulse-ring pr-a">
        <span v-for="n in 32" :key="'pa-'+n" class="pulse-dot-a" :style="pulseDotStyle('a', n)"></span>
      </div>
      <div class="pulse-ring pr-b">
        <span v-for="n in 24" :key="'pb-'+n" class="pulse-dot-b" :style="pulseDotStyle('b', n)"></span>
      </div>

      <!-- 6. 浮动光点（28 个 + 视差） -->
      <span
        v-for="n in 28"
        :key="'dot-'+n"
        class="float-dot"
        :style="dotStyle(n)"
      ></span>

      <!-- 8. 连线网格 canvas（粒子向鼠标吸引/排斥） -->
      <canvas ref="networkCanvas" class="network-canvas"></canvas>

      <!-- 9. 鼠标跟随光晕 -->
      <div
        class="mouse-glow"
        :style="{
          left: mouse.x + 'px',
          top: mouse.y + 'px',
          opacity: mouse.active ? 1 : 0.15,
        }"
      ></div>

      <!-- 10. 扫描线（慢一点） -->
      <div class="scan-line" />
    </div>

    <!-- 信号波纹（呼应 logo 两侧雷达波，视差偏移） -->
    <div
      class="signal-ring sr-1"
      aria-hidden="true"
      :style="{ transform: `translate(calc(-50% + ${mouse.x * 0.006}px), calc(-50% + ${mouse.y * 0.009}px))` }"
    />
    <div
      class="signal-ring sr-2"
      aria-hidden="true"
      :style="{ transform: `translate(calc(-50% + ${-mouse.x * 0.009}px), calc(-50% + ${mouse.y * 0.005}px))` }"
    />
    <div
      class="signal-ring sr-3"
      aria-hidden="true"
      :style="{ transform: `translate(calc(-50% + ${mouse.x * 0.013}px), calc(-50% + ${-mouse.y * 0.011}px))` }"
    />

    <!-- 主容器：左侧品牌价值区 + 右侧登录卡 -->
    <div class="login-shell">
      <section class="brand-panel">
        <div class="brand-head">
          <img class="brand-logo" :src="loginLogo" alt="RHTAGS 品牌 logo" />
        </div>
      </section>

      <!-- 登录卡（简单版：只留账号/密码/记住账号+手机号验证/登录） -->
      <aside class="login-card" aria-label="登录表单">
        <el-form :model="form" @submit.prevent="handleLogin" label-width="0" class="login-form">
          <el-form-item>
            <el-input v-model="form.username" :placeholder="$t('login.username', '用户名')" prefix-icon="User" size="large"
              :autocomplete="rememberMe ? 'username' : 'off'" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" :placeholder="$t('login.password', '密码')" prefix-icon="Lock" size="large"
              show-password :autocomplete="rememberMe ? 'current-password' : 'off'" />
          </el-form-item>
          <div class="remember-row">
            <!--
              ============================================================
              终极修复"记住账号密码勾选图标不显示"：
              彻底抛弃 Element Plus 的 el-checkbox 及其 ::after 伪元素，
              100% 用原生 HTML + CSS 手写一个完全可控的复选框：
                - 方框：纯 div，样式写死，不依赖任何组件库
                - 对勾：真实的 Unicode 字符 ✔（U+2714），不是 ::after 伪元素！
                - 点击：整块 remember-wrap 接收点击，toggleRemember 切状态
              这样无论构建如何压缩、scoped hash 如何变化、浏览器如何缓存，
              ✔ 这个字符一定能渲染出来，用户一定能看到勾选状态。
              ============================================================
            -->
            <div class="remember-wrap" @click.stop="toggleRemember" role="checkbox" :aria-checked="rememberMe">
              <!-- 自定义方框 + 真实✔字符，伪元素再见！ -->
              <span class="custom-checkbox" :class="{ checked: rememberMe }">
                <span v-if="rememberMe" class="check-mark">✔</span>
              </span>
              <span class="check-label">{{ $t('login.rememberPwd', '记住账号密码') }}</span>
            </div>
            <el-button class="phone-btn phone-inline" type="text" @click.stop="handlePhoneLogin">
              {{ $t('login.phoneLogin', '手机号验证') }}
            </el-button>
          </div>
          <div class="btn-row">
            <el-button class="login-btn" native-type="submit" :loading="loading">
              {{ $t('login.submit', '登 录') }}
            </el-button>
          </div>
        </el-form>
      </aside>
    </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import api from '../api'
import { useUserStore } from '@/stores/user'
import { useAppConfigStore } from '@/stores/appConfig'
import { t } from '@/i18n'
import LanguageSwitch from '@/components/LanguageSwitch.vue'
import brandLogo from '@/assets/logo.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appConfigStore = useAppConfigStore()
// 登录页 logo：优先后台配置(system.logo)，未配置时回退内置图 —— 各环境可独立配置，互不影响
const loginLogo = computed(() => appConfigStore.resolvedLogoUrl || brandLogo)
// 登录页改版（2026-09-13）：system.login_bg 非空 → 新布局；卡片标题 login_title 优先，空回退 system.name
const useNewLayout = computed(() => !!appConfigStore.loginBg)
const cardTitle = computed(() => appConfigStore.loginTitle || appConfigStore.systemName)
// 新布局画布（设计图 2279×1280）等比缩放系数：整块画布缩放，保证表单永远精确盖住图里的登录框
const DESIGN_W = 2279
const DESIGN_H = 1280
function calcNlScale() {
  if (typeof window === 'undefined') return 1
  return Math.min(window.innerWidth / DESIGN_W, window.innerHeight / DESIGN_H)
}
const nlScale = ref(calcNlScale())
function updateNlScale() { nlScale.value = calcNlScale() }

/*
 * 新布局样式尺寸：可在不重新发布的前提下调整。
 * 取值优先级：URL 参数 > 后台配置(设置中心) > 代码兜底
 *   URL 试效果：/login?nl_label=35&nl_title=125&nl_titleY=-100  （刷新即见，无需发版）
 *   定稿固化  ：设置中心→基本信息→保存→刷新登录页
 */
const nlUrlParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null
function nlNum(urlKey, configVal, fallback) {
  const raw = nlUrlParams ? nlUrlParams.get(urlKey) : null
  if (raw !== null && raw !== '') {
    const n = Number(raw)
    if (Number.isFinite(n)) return n
  }
  const s = String(configVal == null ? '' : configVal).trim()
  if (s !== '') {
    const n2 = Number(s)
    if (Number.isFinite(n2)) return n2
  }
  return fallback
}
const nlLabelSize = computed(() => nlNum('nl_label', appConfigStore.loginLabelSize, 35))
const nlTitleSize = computed(() => nlNum('nl_title', appConfigStore.loginTitleSize, 125))
const nlTitleOffset = computed(() => nlNum('nl_titleY', appConfigStore.loginTitleOffset, -100))
const nlVars = computed(() => ({
  '--nl-label-size': nlLabelSize.value + 'px',
  '--nl-title-size': nlTitleSize.value + 'px',
  '--nl-title-offset': nlTitleOffset.value + 'px',
}))
const loading = ref(false)
const rememberMe = ref(false)
const form = ref({ username: '', password: '', phone: '' })
const networkCanvas = ref(null)
let rafId = null
let particles = []

/* ========== 鼠标联动：全局 reactive mouse 对象 ========== */
const mouse = reactive({
  x: typeof window !== 'undefined' ? window.innerWidth / 2 : 960,
  y: typeof window !== 'undefined' ? window.innerHeight / 2 : 540,
  lx: typeof window !== 'undefined' ? window.innerWidth / 2 : 960,
  ly: typeof window !== 'undefined' ? window.innerHeight / 2 : 540,
  active: false,
})
function onMouseMove(e) {
  mouse.lx = mouse.x; mouse.ly = mouse.y
  mouse.x = e.clientX; mouse.y = e.clientY
  mouse.active = true
}
let mouseLeaveTimer = null
function onMouseLeave() {
  mouse.active = false
  clearTimeout(mouseLeaveTimer)
  mouseLeaveTimer = setTimeout(() => {
    mouse.x = window.innerWidth / 2
    mouse.y = window.innerHeight / 2
  }, 1200)
}

/*
 * 记住账号密码勾选：唯一入口，确保任何一次点击都只 toggle 一次。
 * - 用时间戳去抖：同一轮事件冒泡内（< 120ms）重复触发忽略，
 *   彻底杜绝"外层 wrap + 内部 checkbox 各切一次 → 状态原地踏步"的假死。
 */
let _lastToggleAt = 0
function toggleRemember() {
  const now = Date.now()
  if (now - _lastToggleAt < 120) return
  _lastToggleAt = now
  rememberMe.value = !rememberMe.value
}

/* ========== 数字雨：字符位置 & 渲染 ========== */
const DIGITS = '0123456789ABCDEF⊕⊗⌬⎔⏚⏛⍟<>{}/\\|:;=()[]█▓▒░'
function matrixChar(col, i) {
  const seed = col * 97 + i * 131
  return DIGITS[seed % DIGITS.length]
}
function matrixCharStyle(col, i) {
  const primes = [53, 97, 131, 179, 229, 277, 331, 383, 439, 491, 547, 599, 653, 709, 761, 811, 863, 919, 967, 1009]
  const p = primes[(i + col * 3) % primes.length]
  const size = 12 + (p % 8)
  const off = (p * 7) % 100
  const dur = 3 + ((p * 11) % 9)
  const delay = -1 * ((p * 5) % 8)
  const hue = 168 + ((p * 3) % 28)
  return {
    height: `${size * 1.5}px`,
    fontSize: `${size}px`,
    transform: `translateY(${off}px)`,
    animationDuration: `${dur}s`,
    animationDelay: `${delay}s`,
    color: `hsla(${hue}, 72%, 56%, ${0.22 + ((p % 100) / 180)})`,
    textShadow: `0 0 8px hsla(${hue}, 80%, 62%, 0.35)`,
  }
}

/* ========== 脉冲点阵圆周 ========== */
function pulseDotStyle(ring, i) {
  const n = ring === 'a' ? 32 : 24
  const angle = (i / n) * Math.PI * 2
  const radius = ring === 'a' ? 210 : 320
  const size = ring === 'a' ? 3 + ((i * 7) % 5) : 2 + ((i * 5) % 3)
  const delay = -1 * ((i * 131) % 100) / 10
  const color = ring === 'a' ? 'rgba(20, 217, 183, 0.55)' : 'rgba(20, 145, 232, 0.42)'
  return {
    position: 'absolute',
    left: `calc(50% + ${Math.cos(angle) * radius}px - ${size / 2}px)`,
    top: `calc(50% + ${Math.sin(angle) * radius}px - ${size / 2}px)`,
    width: `${size}px`,
    height: `${size}px`,
    borderRadius: '50%',
    background: color,
    boxShadow: `0 0 ${size * 3}px ${color}`,
    animation: `pulseDot 2.4s ease-in-out ${delay}s infinite`,
  }
}

/* ========== 浮动光点（28 个 + 视差） ========== */
function dotStyle(i) {
  const primes = [53, 97, 131, 179, 229, 277, 331, 383, 439, 491, 547, 599, 653, 709, 761, 811, 863, 919, 967, 1009, 1061, 1091, 1151, 1193, 1259, 1291, 1373, 1409]
  const p = primes[i - 1]
  const sz = 2 + ((p % 50) / 10)
  const left = (p * 13) % 100
  const top = (p * 17) % 100
  const dur = 10 + ((p % 7) * 2)
  const delay = -1 * ((p % 9) * 2)
  const hue = 168 + ((p * 3) % 30)
  return {
    width: `${sz}px`,
    height: `${sz}px`,
    left: `${left}%`,
    top: `${top}%`,
    boxShadow: `0 0 ${sz * 2.5}px hsla(${hue}, 80%, 58%, 0.9)`,
    background: `hsl(${hue}, 88%, 66%)`,
    animationDuration: `${dur}s`,
    animationDelay: `${delay}s`,
    transform: `translate(calc(var(--mvx,0) * ${0.6 + (p % 100) / 120}), calc(var(--mvy,0) * ${0.5 + (p % 100) / 150}))`,
  }
}

/* ========== 网络粒子 canvas（升级：粒子向鼠标吸引/排斥 + 连线高亮） ========== */
function initNetwork() {
  const canvas = networkCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  function resize() {
    canvas.width = window.innerWidth * window.devicePixelRatio
    canvas.height = window.innerHeight * window.devicePixelRatio
    canvas.style.width = window.innerWidth + 'px'
    canvas.style.height = window.innerHeight + 'px'
    ctx.setTransform(window.devicePixelRatio, 0, 0, window.devicePixelRatio, 0, 0)
  }
  resize()
  const onResize = () => resize()
  window.addEventListener('resize', onResize)

  const count = Math.min(120, Math.floor((window.innerWidth * window.innerHeight) / 15000))
  particles = []
  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * window.innerWidth,
      y: Math.random() * window.innerHeight,
      vx: (Math.random() - 0.5) * 0.42,
      vy: (Math.random() - 0.5) * 0.42,
      r: 1 + Math.random() * 1.8,
    })
  }
  function draw() {
    ctx.clearRect(0, 0, window.innerWidth, window.innerHeight)
    const mx = mouse.x, my = mouse.y, active = mouse.active
    const MOUSE_RADIUS = 180
    const MOUSE_REPEL = 140

    for (const p of particles) {
      // --- 鼠标联动：近距排斥 + 远距吸引 ---
      if (active) {
        const dx = p.x - mx, dy = p.y - my
        const d = Math.sqrt(dx * dx + dy * dy)
        if (d < MOUSE_REPEL && d > 0.1) {
          // 距离越近排斥越强（推开）
          const force = (1 - d / MOUSE_REPEL) * 0.9
          p.vx += (dx / d) * force
          p.vy += (dy / d) * force
        } else if (d < MOUSE_RADIUS * 1.6 && d > MOUSE_REPEL) {
          // 外围轻微吸引（鼠标轨迹周围聚拢成晕圈）
          const f = (1 - Math.abs(d - MOUSE_RADIUS) / MOUSE_RADIUS) * 0.04
          p.vx -= (dx / d) * f
          p.vy -= (dy / d) * f
        }
      }
      // 速度衰减，防止暴走
      p.vx *= 0.985; p.vy *= 0.985

      p.x += p.vx; p.y += p.vy
      if (p.x < 0) { p.x = 0; p.vx *= -0.8 }
      if (p.x > window.innerWidth) { p.x = window.innerWidth; p.vx *= -0.8 }
      if (p.y < 0) { p.y = 0; p.vy *= -0.8 }
      if (p.y > window.innerHeight) { p.y = window.innerHeight; p.vy *= -0.8 }

      // 粒子渲染：离鼠标越近越亮越大
      let r = p.r, alpha = 0.6
      if (active) {
        const dx = p.x - mx, dy = p.y - my
        const d = Math.sqrt(dx * dx + dy * dy)
        if (d < MOUSE_RADIUS) {
          const boost = 1 - d / MOUSE_RADIUS
          r = p.r + boost * 2.2
          alpha = 0.6 + boost * 0.4
        }
      }
      ctx.beginPath()
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(38, 240, 205, ${alpha})`
      ctx.fill()
    }

    // 连线：距离越近/离鼠标越近越粗越亮
    const D = 140
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const dx = a.x - b.x, dy = a.y - b.y
        const d2 = dx * dx + dy * dy
        if (d2 > D * D) continue
        const d = Math.sqrt(d2)
        let alpha = (1 - d / D) * 0.22
        let w = 0.7
        // 鼠标附近连线高亮
        if (active) {
          const midX = (a.x + b.x) * 0.5, midY = (a.y + b.y) * 0.5
          const mdx = midX - mx, mdy = midY - my
          const md = Math.sqrt(mdx * mdx + mdy * mdy)
          if (md < MOUSE_RADIUS) {
            const near = 1 - md / MOUSE_RADIUS
            alpha = Math.min(0.75, alpha + near * 0.55)
            w = 0.7 + near * 1.3
          }
        }
        ctx.strokeStyle = `rgba(42, 218, 189, ${alpha})`
        ctx.lineWidth = w
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
      }
    }
    rafId = requestAnimationFrame(draw)
  }
  draw()
}

function handlePhoneLogin() {
  // 假功能：弹开发中，1秒自动关闭
  try {
    const close = ElNotification({
      title: t('login.phoneLogin', '手机号验证'),
      message: t('login.phoneDev', '开发中，即将上线，敬请期待'),
      type: 'info',
      duration: 1000,
      position: 'top-right',
      showClose: false,
      customClass: 'login-phone-dev-notice',
    })
    setTimeout(() => { try { close.close() } catch (_) {} }, 1000)
  } catch (_) {
    ElMessage({ message: '手机号验证开发中，敬请期待', type: 'info', duration: 1000, showClose: false })
  }
}

function syncUserLanding(info, fallbackUsername) {
  if (!info || typeof info !== 'object') info = {}
  const roles = Array.isArray(info.roles) && info.roles.length ? info.roles : (fallbackUsername ? ['user'] : [])
  const isAdmin = roles.some(r => String(r).toLowerCase() === 'admin')
  const menuIds = Array.isArray(info.menuIds) && info.menuIds.length
    ? info.menuIds
    : (isAdmin ? ['*'] : [])
  const permissions = Array.isArray(info.permissions) && info.permissions.length ? info.permissions : (isAdmin ? ['*'] : [])
  // 关键修复：同步保存 menuRoutePaths（MainLayout 的菜单渲染依赖此字段）
  const menuRoutePaths = Array.isArray(info.menuRoutePaths) && info.menuRoutePaths.length
    ? info.menuRoutePaths
    : (isAdmin ? ['*'] : [])
  localStorage.setItem('roles', JSON.stringify(roles))
  localStorage.setItem('menuIds', JSON.stringify(menuIds))
  localStorage.setItem('permissions', JSON.stringify(permissions))
  localStorage.setItem('menuRoutePaths', JSON.stringify(menuRoutePaths))
  localStorage.setItem('userInfo', JSON.stringify({
    username: info.username || fallbackUsername || '',
    realName: info.realName || localStorage.getItem('realName') || '',
    roles,
    menuIds,
    permissions,
    menuRoutePaths,
    ...info
  }))
  try {
    userStore.roles = roles
    userStore.menuIds = menuIds
    userStore.permissions = permissions
    userStore.menuRoutePaths = menuRoutePaths
    userStore.rehydrate()
  } catch (_) { }
}

function safeAfterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (redirect && (redirect.startsWith('/') && !redirect.startsWith('//'))) {
    router.replace(redirect)
  } else {
    router.replace('/dashboard')
  }
}

const REMEMBER_KEY_V1 = 'erp_login_remember_v1'
const REMEMBER_KEY = 'erp_login_remember_user_v2'

function loadRemembered() {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (!raw) return
    const obj = JSON.parse(raw)
    if (obj && obj.username) {
      form.value.username = obj.username || ''
      form.value.password = ''
      rememberMe.value = true
    }
  } catch (e) {
    console.warn('读取记住账号失败', e)
  }
}

function persistRemembered() {
  if (rememberMe.value) {
    localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username: form.value.username }))
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
}

onMounted(async () => {
  /*
   * 强缓存 / SW 兜底：
   * 用户清浏览器缓存后，Service Worker 和 HTTP Cache Storage
   * 仍然可能在关闭→重开浏览器后继续返回旧的打包 JS/CSS。
   * 这里在登录页每次挂载时都主动清理，确保能拿到最新代码。
   */
  try {
    if ('serviceWorker' in navigator) {
      const regs = await navigator.serviceWorker.getRegistrations()
      for (const r of regs) { try { await r.unregister() } catch (_) {} }
    }
    if ('caches' in window) {
      const keys = await caches.keys()
      for (const k of keys) { try { await caches.delete(k) } catch (_) {} }
    }
  } catch (_) { /* 忽略跨域/隐私模式下可能的抛错 */ }

  try { localStorage.removeItem(REMEMBER_KEY_V1) } catch (_) { }
  loadRemembered()
  initNetwork()
  // 登录页也拉取公开基础配置（系统名/logo 等，未登录可读）；失败不影响登录功能
  try { appConfigStore.loadPublicConfig().catch(_ => {}) } catch (_) {}
  window.addEventListener('mousemove', onMouseMove, { passive: true })
  window.addEventListener('mouseleave', onMouseLeave, { passive: true })
  // 新布局：窗口尺寸变化时重算画布缩放，保证表单始终盖住图里的登录框
  updateNlScale()
  window.addEventListener('resize', updateNlScale, { passive: true })
})

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseleave', onMouseLeave)
  window.removeEventListener('resize', updateNlScale)
  clearTimeout(mouseLeaveTimer)
})

const handleLogin = async () => {
  // 回车与点击最终都走表单 submit，这里加锁防止重复提交
  if (loading.value) return
  // 新布局的「手机登录」框：系统暂无手机号登录能力，只填手机号时提示开发中，不发起密码登录
  if (form.value.phone.trim() && !form.value.username.trim()) {
    handlePhoneLogin()
    return
  }
  loading.value = true
  try {
    const res = await api.post('/auth/login', {
      username: form.value.username,
      password: form.value.password,
    })
    localStorage.setItem('token', res.token)
    localStorage.setItem('username', res.username)
    localStorage.setItem('realName', res.realName || res.username)
    persistRemembered()
    const needSelect = res.requireTenantSelect === true
      || (res.tenants && res.tenants.length > 1)
    if (needSelect && res.tenants && res.tenants.length > 0) {
      localStorage.setItem('pendingTenants', JSON.stringify(res.tenants))
      let preInfo = null
      try { preInfo = await userStore.fetchUserinfo() } catch (e) { console.warn('登录后 userinfo 加载失败', e) }
      syncUserLanding(preInfo || {
        roles: Array.isArray(res.roles) ? res.roles : [],
        menuIds: Array.isArray(res.menuIds) ? res.menuIds : [],
        permissions: Array.isArray(res.permissions) ? res.permissions : [],
        username: res.username,
        realName: res.realName || res.username
      }, res.username)
      ElMessage.success(t('login.successSelectTenant', '登录成功，请选择公司'))
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
      router.push(redirect
        ? { path: '/tenant-select', query: { redirect } }
        : '/tenant-select')
    } else {
      // 单租户或后端告知无需选择：登录后直接拉取真实 userinfo 并进入主页。
      // 为了跟多租户流程等价：同样走"切换到唯一租户"的后端路径（写 tenantId / 新 token 到 LS），
      // 避免后续 /dashboard 权限拦截器因无 tenantId 行为异常。
      if (res.tenants && res.tenants.length === 1) {
        try {
          const t0 = res.tenants[0]
          const sel = await api.post('/tenant/select', { tenantId: t0.tenantId })
          if (sel && sel.token) {
            localStorage.setItem('token', sel.token)
            localStorage.setItem('tenantId', String(sel.tenantId))
            localStorage.setItem('tenantName', sel.tenantName || t0.tenantName)
            localStorage.setItem('tenantCode', sel.tenantCode || t0.tenantCode)
          }
          localStorage.removeItem('pendingTenants')
        } catch (e) {
          // 【2026-10-12 不再吞错】选公司失败（如"无权访问该租户"）继续往下走只会
          // 拿着无租户身份的 token 进系统 → 权限为空被守卫弹回登录页，
          // 用户看到"提示登录成功却卡在登录页"（121 实锤）。
          // 现改为：停在登录页让用户重新登录；错误提示由请求拦截器统一弹出（勿重复弹）。
          console.warn('单租户自动切换失败', e)
          loading.value = false
          return
        }
      }
      let info = null
      try { info = await userStore.fetchUserinfo() } catch (e) { console.warn('userinfo 加载失败', e); info = { username: res.username, roles: ['user'] } }
      syncUserLanding(info, res.username)
      // 有 token 后再刷一次系统配置（权限范围内最新值）
      // 非阻塞：不等待配置刷新完成，避免拖慢登录进入主页；失败也不影响进入系统
      try { appConfigStore.refreshConfig().catch(_ => {}) } catch (_) {}
      ElMessage.success(t('login.success', '登录成功'))
      safeAfterLogin()
    }
  } catch (e) {
    // 安全：只记录错误信息，不打印整条请求对象（否则 F12 控制台展开能看到刚输入的账号密码）
    console.error('登录失败:', (e && (e.message || e.msg)) || e)
    try { ElMessage.error((e && (e.message || e.msg)) || t('login.fail', '登录失败，请检查账号密码')) } catch (_) { }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
:root {
  --c-primary: #007362;
  --c-primary-deep: #004d42;
  --c-primary-light: #1ca58f;
  --c-accent: #f5b247;
  /* ======== 深色主题：墨黑 → 深海蓝 → 墨绿 ======== */
  --c-bg-a: #030913;   /* 接近纯黑（带一丝墨蓝） */
  --c-bg-b: #0a1e2e;   /* 深蓝黑 */
  --c-bg-c: #073a30;   /* 墨绿 */
  --c-text: #0f172a;
  --c-muted: #64748b;
  --radius-card: 22px;
  --radius-btn: 12px;
}

.login-container {
  position: relative;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--c-bg-a);
  font-family: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
}

/* 右上角工具条（2026-10-18）：官网入口 + 多语言切换同一行对齐
   2026-10-19：用户口径「改成黑色」——白底半透明胶囊 + 黑色文字，浅色登录背景图下也一眼可见；
   深色背景时靠胶囊自身的白色底保证黑字可读 */
.login-lang-switch {
  position: absolute;
  top: 24px;
  right: 32px;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, .78);
  box-shadow: 0 2px 10px rgba(0, 0, 0, .12);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

/* 官网入口：轻量文字入口，与语言切换同色系，不抢登录表单视觉 */
.site-entry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  line-height: 1;
  color: #1a1a1a;
  text-decoration: none;
  transition: color .2s;
}
.site-entry:hover { color: #007362; }
.site-entry:hover span:first-child { text-decoration: underline; text-underline-offset: 3px; }
.site-entry-arrow { font-size: 12px; opacity: .9; }
.login-topbar-divider {
  width: 1px;
  height: 12px;
  background: rgba(15, 23, 42, .22);
}
/* 语言切换组件内部是按深色背景写的浅色字，这里统一压成黑色（含 hover 底色） */
.login-lang-switch :deep(.lang-switch.is-light) { color: #1a1a1a; }
.login-lang-switch :deep(.lang-switch.is-light:hover) { background: rgba(0, 0, 0, .06); }
@media (max-width: 768px) {
  .login-lang-switch { top: 18px; right: 16px; gap: 10px; }
  .site-entry { font-size: 12px; }
}

/* 背景层：允许鼠标事件通过，不拦截 */
.bg-layer {
  position: absolute; inset: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 0;
}

/* ====== 1. 深色渐变基底 ====== */
.bg-gradient {
  position: absolute; inset: 0;
  background:
    /* 右上角青绿光晕 */
    radial-gradient(1500px 900px at 95% 5%, rgba(26, 196, 170, 0.20), transparent 58%),
    /* 左下角深蓝光晕 */
    radial-gradient(1200px 1100px at -8% 110%, rgba(20, 98, 210, 0.22), transparent 60%),
    /* 右侧下方琥珀色点缀 */
    radial-gradient(900px 900px at 115% 85%, rgba(245, 178, 71, 0.14), transparent 58%),
    /* 中心横向条带 */
    radial-gradient(900px 520px at 50% 55%, rgba(10, 130, 110, 0.26), transparent 65%),
    linear-gradient(145deg, var(--c-bg-a) 0%, #040e1a 28%, var(--c-bg-b) 58%, var(--c-bg-c) 100%);
}

/* 暗角 vignette：让四边更暗，中心更有层次 */
.bg-vignette {
  position: absolute; inset: 0;
  background: radial-gradient(120% 120% at 50% 50%, transparent 45%, rgba(0,0,0,0.55) 100%);
  pointer-events: none;
}

/* ====== 2. 色斑（更深色调）+ 视差（由模板 inline style 控制偏移） ====== */
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(110px);
  opacity: 0.85;
  will-change: transform;
  animation: blobFloat 18s ease-in-out infinite;
  z-index: 1;
}
.bb-1 {
  width: 620px; height: 620px; left: -180px; top: -140px;
  background: radial-gradient(circle, #1aa88a 0%, transparent 68%);
}
.bb-2 {
  width: 780px; height: 780px; right: -280px; bottom: -240px;
  background: radial-gradient(circle, #145ebd 0%, transparent 64%);
  animation-duration: 22s;
  animation-delay: -6s;
  opacity: 0.75;
}
.bb-3 {
  width: 520px; height: 520px; left: 42%; top: 72%;
  background: radial-gradient(circle, #f0a94a 0%, transparent 62%);
  opacity: 0.45;
  animation-duration: 26s;
  animation-delay: -12s;
}
@keyframes blobFloat {
  0%,100% { filter: blur(110px) saturate(1); opacity: 0.9; }
  50%     { filter: blur(130px) saturate(1.2); opacity: 0.7; }
}

/* ====== 3. 数字雨列 ====== */
.matrix-col {
  position: absolute;
  top: -10%;
  width: 30px;
  height: 120%;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
  z-index: 2;
  opacity: 0.9;
  -webkit-mask-image: linear-gradient(to bottom, transparent 0%, #000 12%, #000 82%, transparent 100%);
  mask-image: linear-gradient(to bottom, transparent 0%, #000 12%, #000 82%, transparent 100%);
  will-change: transform;
}
.mc-1 { left: 2%;  animation: matrixFall 9s linear infinite; }
.mc-2 { left: 22%; animation: matrixFall 12s linear infinite reverse; opacity: 0.6; }
.mc-3 { right: 28%; animation: matrixFall 10s linear infinite; opacity: 0.52; }

.matrix-char {
  display: block;
  width: 100%;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-weight: 700;
  text-align: center;
  animation: matrixTwinkle 4s ease-in-out infinite;
  will-change: transform, opacity;
}
@keyframes matrixFall {
  0%   { transform: translateY(-15%); }
  100% { transform: translateY(15%); }
}
@keyframes matrixTwinkle {
  0%, 100% { opacity: 0.2; filter: brightness(0.9); }
  50%      { opacity: 1;   filter: brightness(1.25); }
}

/* ====== 4. 旋转三角网格 SVG ====== */
.geo-grid {
  position: absolute;
  right: -20%;
  top: -15%;
  width: 1100px;
  height: 1100px;
  z-index: 2;
  opacity: 0.55;
  pointer-events: none;
  will-change: transform;
  transform: translate(calc(var(--mvx, 0) * 1.2), calc(var(--mvy, 0) * 1.2));
}
.grid-spin-slow {
  transform-origin: 400px 400px;
  animation: gridSpin 120s linear infinite;
  transform-box: fill-box;
}
.pulse-dot { animation: triPulse 6s ease-in-out infinite; transform-origin: 400px 400px; transform-box: fill-box; }
@keyframes gridSpin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
@keyframes triPulse {
  0%,100% { opacity: 0.6; transform: scale(1); }
  50%     { opacity: 1;   transform: scale(1.08); }
}

/* ====== 5. 脉冲点阵圆周 ====== */
.pulse-ring {
  position: absolute;
  left: 50%; top: 50%;
  pointer-events: none;
  z-index: 2;
  will-change: transform;
}
.pr-a {
  transform: translate(calc(var(--mvx, 0) * 2.2), calc(var(--mvy, 0) * 2.2));
}
.pr-b {
  transform: translate(calc(var(--mvx, 0) * 3.1), calc(var(--mvy, 0) * 3.1));
}
@keyframes pulseDot {
  0%, 100% { transform: scale(0.85); opacity: 0.3; }
  50%      { transform: scale(1.25); opacity: 1; }
}

/* ====== 6. 动态浮动光点（28） ====== */
.float-dot {
  position: absolute;
  border-radius: 50%;
  animation: floatY ease-in-out infinite;
  z-index: 3;
  opacity: 0.9;
  will-change: transform;
}
@keyframes floatY {
  0%, 100% { transform: translate3d(0, 0, 0); opacity: 0.35; }
  50%      { transform: translate3d(0, -34px, 0); opacity: 1; }
}

/* ====== 8. canvas 连线网络 ====== */
.network-canvas {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  z-index: 3;
  opacity: 0.95;
}

/* ====== 9. 鼠标跟随光晕 ====== */
.mouse-glow {
  position: absolute;
  width: 520px; height: 520px;
  margin-left: -260px; margin-top: -260px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(30, 230, 195, 0.26) 0%, rgba(30, 150, 210, 0.12) 35%, transparent 70%);
  pointer-events: none;
  filter: blur(18px);
  transition: opacity 0.6s ease;
  z-index: 4;
  will-change: left, top;
  mix-blend-mode: screen;
}

/* ====== 10. 扫描线（慢一点，9秒） ====== */
.scan-line {
  position: absolute;
  left: 0; right: 0; top: -2px;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(30, 230, 195, 0.85), transparent);
  box-shadow: 0 0 22px rgba(30, 230, 195, 0.65);
  animation: scanSweep 9s linear infinite;
  z-index: 5;
}
@keyframes scanSweep {
  0%   { transform: translateY(0); opacity: 0; }
  10%  { opacity: 1; }
  90%  { opacity: 1; }
  100% { transform: translateY(100vh); opacity: 0; }
}

/* ====== 11. 信号波纹（升级：深色 + 视差） ====== */
.signal-ring {
  position: absolute;
  border-radius: 50%;
  border: 2px solid rgba(30, 210, 200, 0.22);
  pointer-events: none;
  z-index: 2;
  will-change: transform, width, height, opacity;
}
.sr-1 { width: 320px; height: 320px; left: 50%; top: 50%;
  animation: pulse 6s ease-out infinite; }
.sr-2 { width: 320px; height: 320px; left: 50%; top: 50%;
  animation: pulse 6s ease-out 2s infinite; }
.sr-3 { width: 320px; height: 320px; left: 50%; top: 50%;
  animation: pulse 6s ease-out 4s infinite; }
@keyframes pulse {
  0%   { width: 220px; height: 220px; opacity: 0.7; border-color: rgba(40, 230, 200, 0.48); }
  80%  { opacity: 0; }
  100% { width: 950px; height: 950px; opacity: 0; border-color: rgba(30, 210, 200, 0); }
}

/* 主容器 */
.login-shell {
  position: relative; z-index: 3;
  height: 100%; width: 100%;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 48px;
  padding: 56px 80px;
  box-sizing: border-box;
}

/* 左侧品牌与价值主张：Logo 上移+左移，不跟下方Slogan左对齐 */
.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: flex-start;
  color: #fff;
  padding: 16px 10px 22px 0px;
  min-width: 0;
  gap: 36px;
}
.brand-head {
  margin-left: -18px;
}
.brand-head .brand-logo {
  display: block;
  width: 100%;
  max-width: 560px;
  max-height: 170px;
  height: auto;
  object-fit: contain;
  filter: drop-shadow(0 6px 26px rgba(0,0,0,0.18));
  animation: logoIn 0.9s cubic-bezier(0.2, 0.8, 0.2, 1) both;
  user-select: none;
  -webkit-user-drag: none;
}
@keyframes logoIn {
  from { opacity: 0; transform: translateY(-8px) scale(0.98); }
  to   { opacity: 1; transform: none; }
}

.value-stmt {
  animation: fadeUp 1s 0.15s cubic-bezier(0.2, 0.8, 0.2, 1) both;
  max-width: 620px;
}
.value-stmt .lead {
  margin: 0;
  font-size: 46px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.5px;
  color: #ffffff;
  opacity: 1;
  text-shadow:
    0 1px 0 rgba(0,0,0,0.35),
    0 8px 28px rgba(0,0,0,0.45);
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: none; }
}

/* 右侧登录卡：强隔离（创建独立层叠上下文），确保所有交互层都在它之下 */
.login-card {
  align-self: center;
  justify-self: end;
  width: 100%;
  max-width: 432px;
  background: rgba(255,255,255,0.98);
  border-radius: var(--radius-card);
  padding: 38px 34px 28px;
  box-sizing: border-box;
  box-shadow:
    0 30px 64px -22px rgba(0,0,0,0.45),
    0 8px 26px -10px rgba(0,0,0,0.28),
    inset 0 1px 0 rgba(255,255,255,0.9),
    0 0 0 1px rgba(255,255,255,0.08);
  backdrop-filter: blur(8px);
  animation: cardIn 0.8s 0.2s cubic-bezier(0.2, 0.8, 0.2, 1) both;
  position: relative !important;
  z-index: 50 !important;       /* 远高于背景层所有 canvas/光点(z<=4) */
  isolation: isolate !important;
  pointer-events: auto !important;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(18px) scale(0.98); }
  to   { opacity: 1; transform: none; }
}

.login-form { display: flex; flex-direction: column; gap: 2px; }
.login-form :deep(.el-input__wrapper) {
  padding: 4px 14px;
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.25s ease;
}
.login-form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #c5d0de inset !important; }
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--c-primary) inset, 0 0 0 4px rgba(0,115,98,0.12) !important;
}

.remember-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 2px 0 18px 2px;
  font-size: 13px;
  position: relative !important;
  z-index: 90 !important;
  isolation: isolate !important;
  pointer-events: auto !important;
}
/* 外层点击兜底容器：整块区域都可点击，100%保证能勾选上 */
.remember-wrap {
  display: inline-flex;
  align-items: center;
  padding: 10px 16px 10px 6px;
  margin: -10px 0 -10px -6px;
  border-radius: 8px;
  cursor: pointer !important;
  position: relative !important;
  z-index: 99 !important;
  isolation: isolate !important;
  pointer-events: auto !important;       /* 唯一接收点击的层 */
  transition: background-color 0.2s ease;
  -webkit-tap-highlight-color: transparent;
  user-select: none !important;
  gap: 8px;
}
.remember-wrap:hover {
  background-color: rgba(0, 115, 98, 0.06) !important;
}
/* ============================================================
 * 自定义复选框 —— 彻底抛弃伪元素、100% 可控
 *   custom-checkbox = 外层方框
 *   check-mark      = 真实的 ✔ 字符（用 v-if 控制显隐，不是 ::after）
 * ============================================================ */
.custom-checkbox {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 16px !important;
  height: 16px !important;
  min-width: 16px !important;
  min-height: 16px !important;
  box-sizing: border-box !important;
  border: 1.5px solid #cbd5e1 !important;
  border-radius: 4px !important;
  background-color: #ffffff !important;
  transition: all 0.18s ease !important;
  line-height: 1 !important;
  position: relative !important;
}
.custom-checkbox.checked {
  background-color: #007362 !important;
  border-color: #007362 !important;
}
/* 对勾 = 真实 Unicode ✔ 字符，v-if=true 才插入 DOM，绝对不会消失 */
.custom-checkbox .check-mark {
  display: inline-block !important;
  visibility: visible !important;
  opacity: 1 !important;
  color: #ffffff !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  font-style: normal !important;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif !important;
  line-height: 1 !important;
  margin: 0 !important;
  padding: 0 !important;
  transform: translateY(-0.5px) !important;
  text-rendering: optimizeLegibility !important;
  -webkit-font-smoothing: antialiased !important;
}
/* 文字标签样式，与自定义方框完美对齐 */
.check-label {
  font-size: 13px;
  color: #475569;
  line-height: 1.4;
  cursor: pointer !important;
}
/* 同一行的手机号验证文字按钮 */
.phone-btn.phone-inline,
:deep(.el-button.phone-inline) {
  width: auto !important;
  height: auto !important;
  min-height: 0 !important;
  padding: 2px 6px !important;
  margin: 0 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  border: none !important;
  background: transparent !important;
  background-color: transparent !important;
  background-image: none !important;
  color: var(--c-primary) !important;
  box-shadow: none !important;
  border-radius: 6px !important;
  line-height: 1.6 !important;
  letter-spacing: 0 !important;
  position: relative;
  z-index: 12;
}
.phone-btn.phone-inline:hover,
:deep(.el-button.phone-inline:hover) {
  color: var(--c-primary-deep) !important;
  background: rgba(0,115,98,0.06) !important;
  background-color: rgba(0,115,98,0.06) !important;
  border: none !important;
  transform: none !important;
  box-shadow: none !important;
}

.btn-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

/* 登录按钮 —— 直接使用具体属性，避免简写优先级错乱，scoped + :deep 双保险 */
.login-btn,
:deep(.el-button.login-btn) {
  width: 100% !important;
  height: 48px !important;
  line-height: 1 !important;
  border-radius: var(--radius-btn) !important;
  font-size: 15px !important;
  font-weight: 700 !important;
  letter-spacing: 4px !important;
  color: #ffffff !important;
  background-repeat: no-repeat !important;
  background-position: 0 0 !important;
  background-size: 100% 100% !important;
  background-image: linear-gradient(135deg, #00a88a 0%, #14d9b7 100%) !important;
  background-color: #00a88a !important;
  border: 1px solid #00a88a !important;
  box-shadow:
    0 10px 22px -8px rgba(0,115,98,0.55),
    0 4px 10px -4px rgba(0,115,98,0.25) !important;
  transition: all 0.25s ease !important;
  padding: 0 20px !important;
  outline: none !important;
}
.login-btn:hover,
:deep(.el-button.login-btn:hover) {
  transform: translateY(-1px) !important;
  color: #ffffff !important;
  background-image: linear-gradient(135deg, #007362 0%, #00a88a 100%) !important;
  background-color: #007362 !important;
  border-color: #007362 !important;
  box-shadow:
    0 14px 28px -8px rgba(0,115,98,0.62),
    0 6px 14px -4px rgba(0,115,98,0.3) !important;
}
.login-btn:active,
:deep(.el-button.login-btn:active) {
  transform: translateY(0) !important;
  background-image: linear-gradient(135deg, #005f51 0%, #007362 100%) !important;
  background-color: #005f51 !important;
}
:deep(.el-button.login-btn .el-icon) { display: none; }
:deep(.el-button.login-btn.is-loading) { pointer-events: none; }

/* 手机号验证按钮 */
/* —— 手机号验证按钮独立样式已废弃（现 inline 在记住账号行） —— */

/* 响应式 */
@media (max-width: 1080px) {
  .login-shell {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 28px 20px;
    justify-items: center;
    align-content: center;
  }
  .brand-panel { display: none; }
  .login-card { justify-self: center; }
}
@media (max-width: 520px) {
  .login-card { padding: 28px 20px; border-radius: 20px; }
  .value-stmt .lead { font-size: 30px; }
}

/* ============================================================
 * 新布局（2026-09-13 登录页改版）
 * 结构：设计图整张当背景（画布固定 2279×1280，等比缩放居中）
 *       + 中间压一块真实登录表单，坐标取设计图实测像素，正好盖住图里的登录框
 * ============================================================ */
.nl-stage {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  background: #02110d;   /* 非 16:9 屏幕的留边底色（取设计图四角近黑色） */
}
.nl-canvas {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 2279px;
  height: 1280px;
  transform: translate(-50%, -50%) scale(var(--nl-s, 1));
  transform-origin: center center;
  background-repeat: no-repeat;
  background-size: 100% 100%;
  background-position: center;
}
/* 表单面板：设计图实测登录框 x 771..1506 / y 345..966，四周各外扩 6px 保证完全盖住 */
.nl-panel {
  position: absolute;
  left: 765px;
  top: 339px;
  width: 747px;
  height: 633px;
  box-sizing: border-box;
  padding: 26px 58px 62px;
  background: #ffffff;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
/* 标题：绝对定位浮层——尺寸/上移量可配（--nl-title-size / --nl-title-offset）
   不占表单布局空间，所以改多大都不会把输入框挤出白卡片。
   offset 语义：0 = 标题底边紧贴登录卡顶边；负值 = 继续上移 */
.nl-title {
  position: absolute;
  left: 50%;
  top: 0;
  margin: 0;
  white-space: nowrap;
  font-size: var(--nl-title-size, 125px);
  font-weight: 700;
  color: #1a1a1a;
  letter-spacing: 2px;
  text-align: center;
  transform: translate(-50%, calc(-100% + var(--nl-title-offset, 0px)));
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}
.nl-form { display: flex; flex-direction: column; }
/* 字段行尺寸随 --nl-label-size 联动（标签、输入框高度、行距一起放大，保持比例协调） */
.nl-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: calc(var(--nl-label-size, 35px) * 0.6);
}
.nl-label {
  flex: 0 0 calc(var(--nl-label-size, 35px) * 4.7);
  text-align: right;
  font-size: var(--nl-label-size, 35px);
  color: #333;
  white-space: nowrap;
}
.nl-row :deep(.el-input) { flex: 1; }
/* 输入框：用真实 border 而非 box-shadow —— 保证默认/悬停/输入中 边框永远可见 */
.nl-row :deep(.el-input__wrapper) {
  height: calc(var(--nl-label-size, 35px) * 1.75);
  border-radius: 6px;
  border: 1px solid #a9b8c6 !important;
  box-shadow: none !important;
  background-color: #ffffff !important;
  transition: border-color 0.2s ease, box-shadow 0.2s ease !important;
}
.nl-row :deep(.el-input__wrapper:hover) {
  border-color: #7d8fa0 !important;
}
.nl-row :deep(.el-input__wrapper.is-focus) {
  border-color: #007362 !important;
  box-shadow: 0 0 0 3px rgba(0, 115, 98, 0.12) !important;
}
.nl-row :deep(.el-input__inner) { font-size: calc(var(--nl-label-size, 35px) * 0.6); }
/* 记住账号：与输入框左边缘对齐（标签宽 + 间距），文字随标签一起放大 */
.nl-opt {
  display: flex;
  align-items: center;
  margin: 2px 0 26px calc(var(--nl-label-size, 35px) * 4.7 + 14px);
}
.nl-opt :deep(.check-label) { font-size: calc(var(--nl-label-size, 35px) * 0.5); }
.nl-opt :deep(.custom-checkbox) {
  width: calc(var(--nl-label-size, 35px) * 0.62) !important;
  height: calc(var(--nl-label-size, 35px) * 0.62) !important;
  min-width: calc(var(--nl-label-size, 35px) * 0.62) !important;
  min-height: calc(var(--nl-label-size, 35px) * 0.62) !important;
}
.nl-opt :deep(.check-mark) { font-size: calc(var(--nl-label-size, 35px) * 0.42) !important; }
.nl-btn-row { display: flex; justify-content: center; }
.nl-login-btn,
:deep(.el-button.nl-login-btn) {
  width: 76% !important;
  height: calc(var(--nl-label-size, 35px) * 1.75) !important;
  border-radius: 8px !important;
  font-size: calc(var(--nl-label-size, 35px) * 0.6) !important;
  font-weight: 700 !important;
  letter-spacing: 8px !important;
  color: #ffffff !important;
  background-color: #0d6b52 !important;
  background-image: none !important;
  border: 1px solid #0d6b52 !important;
  box-shadow: 0 6px 16px -6px rgba(13, 107, 82, 0.5) !important;
  transition: all 0.2s ease !important;
}
.nl-login-btn:hover,
:deep(.el-button.nl-login-btn:hover) {
  background-color: #0a5a45 !important;
  border-color: #0a5a45 !important;
}
:deep(.el-button.nl-login-btn.is-loading) { pointer-events: none; }
</style>

<style>
/* 全局样式：隐藏通知弹框的手动关闭按钮，保证1秒自动关闭的效果 */
.el-notification.login-phone-dev-notice .el-notification__closeBtn { display: none !important; }

/* ================================================================
 * 登录页 记住账号密码 自定义复选框 全局兜底样式
 * 彻底抛弃 Element Plus ::after 伪元素，
 * 对勾 = 真实 Unicode ✔ 字符通过 v-if 渲染到 DOM 中，
 * 任何浏览器/构建/缓存/私有化部署环境都一定能显示。
 * ================================================================ */
.login-container .remember-wrap {
  gap: 8px;
}
.login-container .remember-wrap .custom-checkbox {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 16px !important;
  height: 16px !important;
  min-width: 16px !important;
  min-height: 16px !important;
  box-sizing: border-box !important;
  border: 1.5px solid #cbd5e1 !important;
  border-radius: 4px !important;
  background-color: #ffffff !important;
  transition: all 0.18s ease !important;
  line-height: 1 !important;
}
.login-container .remember-wrap .custom-checkbox.checked {
  background-color: #007362 !important;
  border-color: #007362 !important;
}
/* 对勾✔字符 —— 全局最优先级兜底，颜色/大小/字重全部写死，不可能看不见 */
.login-container .remember-wrap .custom-checkbox .check-mark {
  display: inline-block !important;
  visibility: visible !important;
  opacity: 1 !important;
  color: #ffffff !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  font-style: normal !important;
  line-height: 1 !important;
  margin: 0 !important;
  padding: 0 !important;
  transform: translateY(-0.5px) !important;
  /* 禁用所有字体特性继承，强制用系统通用字体族，保证✔字符一定有字形 */
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei",
               Arial, Helvetica, sans-serif, "Apple Color Emoji", "Segoe UI Emoji" !important;
  text-rendering: optimizeLegibility !important;
  -webkit-font-smoothing: antialiased !important;
}
.login-container .remember-wrap .check-label {
  font-size: 13px;
  color: #475569;
  line-height: 1.4;
}
</style>
