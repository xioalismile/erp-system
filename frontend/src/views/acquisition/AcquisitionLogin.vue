<template>
  <div class="erp-page">
    <el-card shadow="never" class="login-card">
      <!-- 顶部：页面标题 + 三个平台 Tab（Tab 上的小圆点＝该平台是否已保存登录态） -->
      <div class="page-head">
        <span class="page-title">{{ $tt("采集平台登录") }}</span>
        <el-tabs v-model="platform" class="plat-tabs">
          <el-tab-pane
            v-for="p in PLATFORM_TABS"
            :key="p.value"
            :name="p.value"
          >
            <template #label>
              <span class="tab-label">
                <i class="tab-dot" :class="{ on: platformStates[p.value] === true }"></i>
                <span>{{ $tt(p.label) }}</span>
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 所有操作按钮集中在这里（列表/状态在下方，画面独占整宽） -->
      <div class="action-bar">
        <!-- 采集任务撞风控时：先给一个"继续采集"（用户在下方画面把验证做完后点） -->
        <el-button v-if="captureSession && captureSession.status === 'blocked'" type="success" v-permission="perm('start')" @click="handleResumeCapture" :loading="resuming">
          <el-icon><VideoPlay /></el-icon>
          <span>{{ $tt("继续采集") }}</span>
        </el-button>
        <el-button type="primary" v-permission="perm('start')" @click="handleStartLogin" :loading="qrLoading">
          <el-icon><Refresh /></el-icon>
          <span>{{ (streamConnected || liveReady) ? $tt('重新打开采集浏览器') : $tt('打开采集浏览器') }}</span>
        </el-button>
        <el-button type="danger" plain v-permission="perm('clear')" @click="handleResetLogin" :loading="resetting">
          <el-icon><Delete /></el-icon>
          <span>{{ $tt("清除登录态并重新扫码") }}</span>
        </el-button>
        <el-button v-permission="perm('refresh')" @click="handleRefreshView" :loading="liveLoading" :disabled="!liveReady">{{ $tt("刷新画面") }}</el-button>
        <el-button v-permission="perm('dlgClose')" @click="handleCloseBrowser" :loading="closeBusy" :disabled="!liveReady">{{ $tt("关闭浏览器") }}</el-button>
        <el-button v-permission="perm('dlgCancel')" @click="handleBack">{{ $tt("返回") }}</el-button>
      </div>

      <!-- 状态：当前平台登录态 + 本次登录流程状态 -->
      <div class="status-bar">
        <el-tag :type="savedState && savedState.loggedIn ? 'success' : 'info'" effect="plain" size="large">
          {{ platformName }}：{{ savedState && savedState.loggedIn ? $tt('登录态已保存') : $tt('未保存登录态') }}
        </el-tag>
        <span class="status-detail" v-if="savedState && savedState.loggedIn">{{ savedStateText }}</span>
        <el-tag :type="statusTagType" size="large" effect="dark">{{ statusText }}</el-tag>
      </div>

      <!-- 采集任务撞风控/验证页：采集浏览器已接到下方画面，等用户自己过验证 -->
      <el-alert
        v-if="captureSession"
        :title="captureSessionTitle"
        :type="captureSession.status === 'blocked' ? 'warning' : 'success'"
        :closable="false"
        show-icon
        class="err-tip"
      />

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="true"
        show-icon
        class="err-tip"
      />

      <!-- 实时画面：上下结构，画面独占整宽（服务器浏览器实时投屏，可直接操作） -->
      <div class="remote-area">
        <div class="remote-head">
          <span class="remote-title">{{ $tt("服务器浏览器实时画面") }}</span>
          <el-tag size="small" :type="liveReady ? 'success' : ((streamConnected || qrLoading) ? 'warning' : 'info')">
            {{ liveReady ? $tt("可直接操作") : (streamConnected ? $tt("连接中…") : (qrLoading ? $tt("正在打开…") : $tt("未打开"))) }}
          </el-tag>
        </div>
        <!-- 实时投屏：画面就是服务器上的浏览器本身（可直接点击 / 拖滑块 / 键盘输入）。
             用 v-show 常驻不销毁：断线重连后画布不会被重建，键盘焦点与画面都不会"跳" -->
        <div class="remote-screen" v-show="viewMode === 'live'">
          <canvas
            ref="canvasRef"
            class="live-canvas"
            tabindex="0"
            @mousedown="handleCanvasMouseDown"
            @mousemove="handleCanvasMouseMove"
            @mouseup="handleCanvasMouseUp"
            @mouseleave="handleCanvasLeave"
            @keydown="handleCanvasKeyDown"
            @contextmenu.prevent
          ></canvas>
        </div>

        <!-- 正在启动浏览器 -->
        <div class="remote-empty" v-if="viewMode === 'loading'">
          <el-icon class="is-loading" :size="42"><Loading /></el-icon>
          <div class="empty-text">{{ loadingText }}</div>
        </div>

        <!-- 兜底①：投屏不可用时退回截图画面（每 4 秒刷新，仍可点击） -->
        <div class="remote-screen" v-if="viewMode === 'shot'">
          <img :src="liveUrl" class="live-img" @click="handleLiveClick" :alt="$tt('服务器浏览器画面（截图）')" />
        </div>

        <!-- 兜底②：画面还没取到时，先显示二维码/登录页截图，保证扫得到码 -->
        <div class="remote-screen" v-if="viewMode === 'qr'">
          <img :src="qrcodeUrl" class="live-img" :alt="$tt('登录二维码')" />
        </div>

        <!-- 空态 -->
        <div class="remote-empty" v-if="viewMode === 'empty'">
          {{ liveMessage || $tt("点击上方「打开采集浏览器」，这里会显示服务器浏览器的实时画面：可直接在上面扫码、点按钮、用键盘输入") }}
        </div>

        <div class="live-foot">
          <span class="live-hint">{{ liveHint }}</span>
          <span class="live-tip">{{ $tt("点击画面即可操作；先点一下页面里的输入框，再直接用键盘输入手机号/验证码（退格、回车、Tab、方向键都支持）") }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Refresh, Delete, VideoPlay } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import {
  startCaptureLogin,
  getCaptureLoginStatus,
  getCaptureLoginState,
  getCaptureLive,
  captureLiveShotUrl,
  captureScreencastUrl,
  interactCapture,
  closeCaptureBrowser,
  resetCaptureLogin,
  captureQrcodeUrl,
  getCaptureSession,
  resumeCapture
} from '@/api/acquisition'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('acquisition:login')

const router = useRouter()
const route = useRoute()

// 平台：三个平台各一个 Tab（qcc=企查查，fnb=风鸟，tyc=天眼查）
// （爱企查 aqc 已下线：百度对自动化浏览器做硬拦截，打开即被判定"调试窗口"，无法稳定登录）
const PLATFORM_TABS = [
  { value: 'qcc', label: '企查查' },
  { value: 'fnb', label: '风鸟' },
  { value: 'tyc', label: '天眼查' }
]
const platform = ref('qcc')
const platformName = computed(() => PLATFORM_TABS.find((p) => p.value === platform.value)?.label || '企查查')
// Tab 上的小圆点：各平台是否已保存登录态（true=已保存，false=未保存，null=还没查）
const platformStates = ref({ qcc: null, fnb: null, tyc: null })

// 登录状态：idle/running/scanned/success/failed/expired
const loginStatus = ref('idle')
const qrLoading = ref(false)
// 浏览器正在启动（点击「打开采集浏览器」后、第一帧画面到达前为 true，用于显示转圈）
const startingBrowser = ref(false)
const loadingText = ref('正在启动浏览器...')
const qrcodeUrl = ref('')
// 二维码版本（服务端每次重截会变），用于判断是否需要刷新图片
const lastQrKey = ref('')
const resetting = ref(false)
const errorMessage = ref('')
let pollTimer = null

// 平台「已保存的登录态」：来自服务端落盘的Cookie（不是内存里的登录任务），
// 因此重新进入页面、刷新、采集服务重启后都能显示真实状态
const savedState = ref(null)

// 采集任务撞风控/验证页时的会话（source='capture'）：
// status='blocked' = 采集任务正停在验证页面等人工处理 → 画面自动接出来 + 显示「继续采集」
// 为什么要有：远程浏览器本来就是为了让用户自己过风控（2026-09-22 用户要求），
// 以前采集浏览器是 headless 且不进投屏，撞风控时前端什么都看不到，只能等它失败
const captureSession = ref(null)
const resuming = ref(false)

const formatTime = (v) => {
  const d = new Date(v)
  if (isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const savedStateText = computed(() => {
  const s = savedState.value
  if (!s || !s.loggedIn) return ''
  const parts = []
  if (s.savedAt) {
    parts.push(`${tt('登录时间')} ${formatTime(s.savedAt)}`)
    // 登录态有效期 = 保存后 7 天（与页面说明、采集服务校验口径一致）
    parts.push(`${tt('有效期至')} ${formatTime(new Date(new Date(s.savedAt).getTime() + 7 * 24 * 60 * 60 * 1000))}`)
  }
  parts.push(`${s.cookieCount}${tt('条Cookie')}`)
  return parts.join('，')
})

// 采集会话提示语：blocked（等你过验证）是重点，必须一眼看懂"要做什么"
const captureSessionTitle = computed(() => {
  const s = captureSession.value
  if (!s) return ''
  if (s.status === 'blocked') {
    return `${platformName.value}${tt('采集任务正停在验证/风控页面（等待人工处理）：请在下方画面里完成验证，然后点【继续采集】')}`
  }
  return `${platformName.value}${tt('采集任务正在使用服务器浏览器，画面已接过来（采集进行中请勿操作）')}`
})

// 查询单个平台"是否已保存登录态"（供 Tab 上的小圆点用）
const fetchPlatformLoggedIn = async (p) => {
  try {
    const data = await getCaptureLoginState(p)
    return !!(data && data.success && data.loggedIn)
  } catch (e) {
    return false
  }
}

// 拉取当前平台真实登录态；失败（采集服务未启动/未登录）不影响主流程，仅不展示
// 同时把结果回写到 Tab 小圆点，保证"状态标签"与"Tab 圆点"永远一致
const refreshSavedState = async () => {
  try {
    const data = await getCaptureLoginState(platform.value)
    savedState.value = data && data.success ? data : null
    platformStates.value = { ...platformStates.value, [platform.value]: !!(data && data.success && data.loggedIn) }
  } catch (e) {
    savedState.value = null
    platformStates.value = { ...platformStates.value, [platform.value]: false }
  }
}

// 进页面时把另外两个平台的登录态也查出来，三个 Tab 的小圆点一次点亮
const refreshOtherPlatformStates = async () => {
  const others = PLATFORM_TABS.map((p) => p.value).filter((v) => v !== platform.value)
  const results = await Promise.all(others.map(fetchPlatformLoggedIn))
  const next = { ...platformStates.value }
  others.forEach((v, i) => { next[v] = results[i] })
  platformStates.value = next
}

// ===== 远程操作服务器浏览器（实时投屏 + 原生键鼠）=====
// 用途：服务器部署后没人能进服务器桌面操作浏览器，扫码/验证码/弹窗/人工登录都在本页完成。
// 画面：服务端用 CDP Page.startScreencast 推帧（SSE），前端 canvas 连续渲染（不再是"4 秒一张图"）；
// 操作：鼠标按下/移动/抬起 + 键盘按键实时转发给真实浏览器（可拖滑块、可连续输入）。
const liveUrl = ref('')            // 兜底画面：投屏不可用时退回截图轮询
const liveViewport = ref(null)     // 浏览器视口（CSS 像素）：把画布坐标换算成浏览器坐标
const livePageTitle = ref('')
const liveReady = ref(false)       // 有可用画面（投屏已连上或截图兜底已取到）
const streamConnected = ref(false) // 投屏通道已连上（等待首帧时也算）
const liveLoading = ref(false)
const liveMessage = ref('')
const canvasRef = ref(null)
let liveTimer = null               // 截图兜底轮询
let stream = null                  // EventSource（实时投屏）
let streamRetryTimer = null
let streamRetryCount = 0
let wantStream = false             // 打开浏览器后为 true：允许投屏失败自动重试
let frameImg = null                // 复用的图片对象（避免每帧 new Image）
// 鼠标：轻点=一次 click 请求（最快）；拖拽=down+move…+up（滑块用）
const TAP_MS = 120                 // 按下多久之内松手算"轻点"
const TAP_PX = 6                   // 位移超过多少像素算"拖拽"
let pressTimer = null
let pressStart = null              // { x, y, button } 按下时的浏览器坐标
let downSent = false               // down 是否已经发出去
let lastMouseMoveAt = 0
let engaged = false                // 用户是否点过画面（点过之后键盘就一直转给远程浏览器）
// 键盘：单字符先攒 80ms 再一次性发（连打手机号只发 1 个请求）
let typeBuf = ''
let typeTimer = null

const liveHint = computed(() => (livePageTitle.value
  ? `${tt('当前页面：')}${livePageTitle.value}`
  : tt('点击画面 = 在服务器浏览器里点击同一位置')))

// 画面区当前该显示什么：实时投屏 > 截图兜底 > 二维码兜底 > 启动中 > 空态
// 用 computed 统一判定，模板里画布走 v-show 常驻（不被销毁），断线重连后键盘焦点不会丢
const viewMode = computed(() => {
  if (streamConnected.value && liveReady.value) return 'live'
  if (liveUrl.value) return 'shot'
  if (qrcodeUrl.value) return 'qr'
  if (qrLoading.value || startingBrowser.value) return 'loading'
  return 'empty'
})

const stopLiveTimer = () => {
  if (liveTimer) {
    clearInterval(liveTimer)
    liveTimer = null
  }
}

// 截图兜底：投屏连不上时每 4 秒取一张（老版本 nginx 缓冲 SSE 时仍能看能操作）
const startLiveTimer = () => {
  if (liveTimer) return
  liveTimer = setInterval(() => refreshLive({ silent: true, force: true }), 4000)
}

/**
 * 拉取截图画面（兜底路径）
 * @param {Object} opts silent=失败不弹提示；force=忽略"自动刷新已在跑"的跳过
 */
const refreshLive = async (opts = {}) => {
  const { silent = false, force = false } = opts
  if (liveLoading.value) return
  if (liveTimer && !force) return
  liveLoading.value = true
  try {
    const data = await getCaptureLive(platform.value)
    if (!data || !data.success) throw new Error((data && data.message) || '取实时画面失败')
    liveViewport.value = data.viewport || null
    livePageTitle.value = data.pageTitle || ''
    liveUrl.value = `${captureLiveShotUrl(platform.value)}?t=${data.version}`
    liveReady.value = true
    liveMessage.value = ''
    startLiveTimer()
  } catch (e) {
    // 只有投屏也没连上时才清空画面/给提示：投屏正常时兜底失败不该影响用户
    if (!stream) {
      liveReady.value = false
      liveUrl.value = ''
      liveMessage.value = e.message || '请先点击「打开采集浏览器」，右侧会显示服务器浏览器画面'
    }
    stopLiveTimer()
    if (!silent) ElMessage.warning(liveMessage.value || '取实时画面失败')
  } finally {
    liveLoading.value = false
  }
}

// ----- 实时投屏（SSE + canvas 渲染）-----
const drawFrame = (data) => {
  const canvas = canvasRef.value
  if (!canvas) return
  if (!frameImg) {
    frameImg = new Image()
    frameImg.onload = () => {
      const c = canvasRef.value
      if (!c) return
      // 画布分辨率跟随帧本身，CSS 只负责缩放显示（坐标换算用 CSS 宽度）
      if (c.width !== frameImg.naturalWidth || c.height !== frameImg.naturalHeight) {
        c.width = frameImg.naturalWidth
        c.height = frameImg.naturalHeight
      }
      const ctx = c.getContext('2d')
      if (ctx) ctx.drawImage(frameImg, 0, 0)
    }
  }
  frameImg.src = `data:image/jpeg;base64,${data}`
}

const closeStream = () => {
  if (streamRetryTimer) {
    clearTimeout(streamRetryTimer)
    streamRetryTimer = null
  }
  if (stream) {
    try { stream.close() } catch (e) { /* ignore */ }
    stream = null
  }
  streamConnected.value = false
}

// 用户级停止（关闭浏览器/离页）：彻底断开且不再重试
const stopStream = () => {
  wantStream = false
  streamRetryCount = 0
  startingBrowser.value = false
  closeStream()
  liveReady.value = false
}

const openStream = () => {
  closeStream()
  const es = new EventSource(captureScreencastUrl(platform.value))
  stream = es
  es.onopen = () => { streamConnected.value = true }
  es.onmessage = (ev) => {
    let msg
    try { msg = JSON.parse(ev.data) } catch (e) { return }
    if (msg.type === 'frame') {
      streamRetryCount = 0
      streamConnected.value = true
      startingBrowser.value = false   // 第一帧到了，转圈停掉
      if (!liveReady.value) {
        liveReady.value = true
        liveMessage.value = ''
        liveUrl.value = ''
        stopLiveTimer()          // 投屏已可用 → 停掉截图兜底
      }
      drawFrame(msg.data)
    } else if (msg.type === 'wait') {
      // 服务端：登录任务在跑、浏览器还在启动（保持连接等它就绪，就绪后自动推帧）
      if (!liveReady.value) {
        loadingText.value = msg.message || '正在启动浏览器...'
        if (!liveMessage.value) liveMessage.value = msg.message || ''
      }
    } else if (msg.type === 'meta') {
      livePageTitle.value = msg.pageTitle || ''
      if (msg.viewport && msg.viewport.width) liveViewport.value = msg.viewport
    } else if (msg.type === 'error') {
      // 服务端明确告知"没有可用会话 / 浏览器已关闭"：停止重连，提示用户重新打开
      wantStream = false
      streamRetryCount = 0
      startingBrowser.value = false
      closeStream()
      liveReady.value = false
      liveMessage.value = msg.message || '实时画面不可用'
      refreshLive({ silent: true, force: true })
    }
  }
  es.onerror = () => {
    // 传输层断开（网络抖动 / 服务重启）：不等用户操作，按 1s→5s 退避一直重连，连上后画面自动恢复。
    // （原来只重试 5 次就放弃 → 用户会遇到"画面一直卡在截图上、点了也没反应"）
    closeStream()
    if (wantStream) {
      const delay = Math.min(1000 + streamRetryCount * 500, 5000)
      streamRetryCount += 1
      streamRetryTimer = setTimeout(openStream, delay)
    }
    if (!liveReady.value) refreshLive({ silent: true, force: true })
  }
}

// 打开投屏（打开浏览器 / 恢复进行中的登录任务 / 进入页面时调用）
const startStream = () => {
  wantStream = true
  streamRetryCount = 0
  liveMessage.value = ''
  stopLiveTimer()   // 投屏期间不走截图轮询；真连不上时 onerror 会自动启用兜底
  liveUrl.value = ''
  openStream()
}

// ===== 采集任务撞风控：把采集浏览器接到本页画面上，用户自己过验证 =====
// 采集服务在撞到风控/验证页时会把任务挂成 blocked 并保持浏览器存活（不再直接失败关浏览器），
// 这里每 5 秒问一次"该平台有没有可操作的采集浏览器"，有就把画面接出来。
const refreshCaptureSession = async () => {
  try {
    const data = await getCaptureSession(platform.value)
    captureSession.value = data && data.success && data.source === 'capture' ? data : null
    if (captureSession.value && captureSession.value.status === 'blocked' && !stream) {
      startStream()   // 撞风控 → 画面自动出现，不需要用户再点「打开采集浏览器」
    }
  } catch (e) {
    captureSession.value = null
  }
}

let captureTimer = null
const startCaptureWatch = () => {
  refreshCaptureSession()
  if (captureTimer) return
  captureTimer = setInterval(refreshCaptureSession, 5000)
}
const stopCaptureWatch = () => {
  if (captureTimer) {
    clearInterval(captureTimer)
    captureTimer = null
  }
}

// 人工验证做完 → 通知采集任务从当前位置继续
const handleResumeCapture = async () => {
  const s = captureSession.value
  if (!s || !s.captureId) return
  resuming.value = true
  try {
    const data = await resumeCapture(s.captureId)
    if (!data || !data.success) throw new Error((data && data.message) || tt('通知继续失败'))
    ElMessage.success(tt('已通知采集任务继续，请观察采集结果是否继续增加'))
    await refreshCaptureSession()
  } catch (e) {
    ElMessage.warning(e.message || tt('通知继续失败'))
  } finally {
    resuming.value = false
  }
}

// 发送一步远程操作；投屏在线时画面由服务端自动推帧，无需再截图
const applyInteract = async (payload) => {
  const data = await interactCapture(platform.value, payload)
  if (!data || !data.success) throw new Error((data && data.message) || '操作失败')
  if (!stream) await refreshLive({ silent: true, force: true })
}

// 兜底截图画面上的点击：出错给提示（投屏画布走下面的 sendSilent，避免拖拽时刷屏）
const runRemote = async (payload) => {
  try {
    await applyInteract(payload)
  } catch (e) {
    ElMessage.warning(e.message || '操作失败')
  }
}

// 画面上的鼠标/键盘：静默发送（鼠标移动失败不弹提示，否则拖拽时会刷屏）
const sendSilent = (payload) => {
  applyInteract(payload).catch((e) => {
    if (payload.type !== 'move') ElMessage.warning(e.message || '操作失败')
  })
}

// 坐标换算：画面是按 CSS 缩放显示的，必须用"显示宽度 : 视口宽度"换算，否则点不准
const toRemoteCoords = (e, el) => {
  const vp = liveViewport.value
  const target = el || (e && e.currentTarget)
  if (!vp || !vp.width || !target || !target.clientWidth) return null
  const rect = target.getBoundingClientRect()
  const scale = vp.width / target.clientWidth
  return { x: Math.round((e.clientX - rect.left) * scale), y: Math.round((e.clientY - rect.top) * scale) }
}

// 截图兜底画面上的点击（投屏路径用下面的 mouse down/move/up）
const handleLiveClick = (e) => {
  const p = toRemoteCoords(e)
  if (!p) return
  runRemote({ action: 'click', x: p.x, y: p.y })
}

// 投屏画布：轻点 = 一次 click 请求（最快、最跟手）；拖拽 = down + move… + up（滑块验证用）
// 判定：按下到松手不超过 TAP_MS 且位移不超过 TAP_PX → 轻点；否则按拖拽处理
// 注意：mousedown 里主动 focus 画布，这样"点一下输入框 → 直接敲键盘"才收得到按键
const handleCanvasMouseDown = (e) => {
  if (e.button !== 0 && e.button !== 2) return
  engaged = true   // 点过画面之后，键盘就一直转给远程浏览器（见 handleDocumentKeyDown）
  if (canvasRef.value && canvasRef.value.focus) canvasRef.value.focus()
  flushTyping()   // 先把手打了一半的字符发出去，保证输入顺序
  const p = toRemoteCoords(e)
  if (!p) return
  pressStart = { x: p.x, y: p.y, button: e.button === 2 ? 'right' : 'left' }
  downSent = false
  if (pressTimer) clearTimeout(pressTimer)
  // 按住不动超过 TAP_MS（长按）→ 把 down 补发出去，松手时再发 up
  pressTimer = setTimeout(() => {
    pressTimer = null
    if (!downSent && pressStart) {
      downSent = true
      sendSilent({ action: 'mouse', type: 'down', x: pressStart.x, y: pressStart.y, button: pressStart.button })
    }
  }, TAP_MS)
}

const handleCanvasMouseMove = (e) => {
  if (!pressStart) return
  const p = toRemoteCoords(e)
  if (!p) return
  // 位移超阈值＝拖拽：立刻补发 down（用最初按下的坐标当起点，拖拽才不偏）
  if (!downSent && (Math.abs(p.x - pressStart.x) + Math.abs(p.y - pressStart.y) > TAP_PX)) {
    if (pressTimer) { clearTimeout(pressTimer); pressTimer = null }
    downSent = true
    sendSilent({ action: 'mouse', type: 'down', x: pressStart.x, y: pressStart.y, button: pressStart.button })
  }
  if (!downSent) return
  const now = Date.now()
  if (now - lastMouseMoveAt < 80) return   // 拖拽期间限流，避免把请求打爆
  lastMouseMoveAt = now
  sendSilent({ action: 'mouse', type: 'move', x: p.x, y: p.y })
}

const handleCanvasMouseUp = (e) => {
  if (pressTimer) { clearTimeout(pressTimer); pressTimer = null }
  const start = pressStart
  pressStart = null
  if (!start) return
  const p = toRemoteCoords(e)
  if (!downSent) {
    // 轻点：一次请求完成 move+down+up
    const target = p || { x: start.x, y: start.y }
    sendSilent({ action: 'mouse', type: 'click', x: target.x, y: target.y, button: start.button })
    return
  }
  downSent = false
  if (p) sendSilent({ action: 'mouse', type: 'up', x: p.x, y: p.y, button: start.button })
}

const handleCanvasLeave = () => {
  if (pressTimer) { clearTimeout(pressTimer); pressTimer = null }
  pressStart = null
  downSent = false
  flushTyping()
}

// 投屏画布键盘：控制键立刻发；单字符攒 TYPING_MS 合并成一次请求（连打手机号只发 1 个请求）
const SPECIAL_KEYS = ['Enter', 'Escape', 'Tab', 'Backspace', 'Delete', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown']
const TYPING_MS = 80
const flushTyping = () => {
  if (typeTimer) { clearTimeout(typeTimer); typeTimer = null }
  if (!typeBuf) return
  const text = typeBuf
  typeBuf = ''
  sendSilent({ action: 'type', text })
}
const handleCanvasKeyDown = (e) => {
  if (e.ctrlKey || e.metaKey || e.altKey) return
  if (SPECIAL_KEYS.includes(e.key)) {
    e.preventDefault()
    flushTyping()          // 先发完攒下的字符，保证顺序（如先输数字再按回车）
    sendSilent({ action: 'key', key: e.key })
  } else if (typeof e.key === 'string' && e.key.length === 1) {
    e.preventDefault()
    typeBuf += e.key
    if (typeTimer) clearTimeout(typeTimer)
    typeTimer = setTimeout(flushTyping, TYPING_MS)
  }
}

// 键盘兜底：画布焦点会被页面其它组件/弹层抢走（实测连打手机号中途 activeElement 会变回 body，
// 结果后半段手机号打不进去）。只要用户点过画面、且当前没在真正的输入框里打字，就继续把按键转给远程浏览器。
const handleDocumentKeyDown = (e) => {
  if (!engaged) return
  if (!streamConnected.value || !liveReady.value) return
  if (e.target === canvasRef.value) return   // 画布自身的监听已处理，避免同一次按键被转发两遍
  const ae = document.activeElement
  if (ae && ae !== document.body && ae !== canvasRef.value) return
  handleCanvasKeyDown(e)
}

// 「刷新画面」：投屏断开则重连，兜底路径顺手取一张新图
const handleRefreshView = () => {
  if (!streamConnected.value) startStream()
  refreshLive({ silent: true, force: true })
}

// 手动关闭服务器浏览器（登录流程结束后浏览器会保活一段时间，这里可提前关闭）
const closeBusy = ref(false)
const handleCloseBrowser = async () => {
  closeBusy.value = true
  try {
    const data = await closeCaptureBrowser(platform.value)
    if (!data || !data.success) throw new Error((data && data.message) || '关闭浏览器失败')
    stopLiveTimer()
    stopStream()
    liveUrl.value = ''
    liveViewport.value = null
    liveMessage.value = tt('浏览器已关闭，如需继续操作请点「打开采集浏览器」')
    ElMessage.success(tt('浏览器已关闭'))
  } catch (e) {
    ElMessage.warning(e.message || tt('关闭浏览器失败'))
  } finally {
    closeBusy.value = false
  }
}


// 切换平台时重置登录状态，避免读到上一个平台的登录结果/二维码（平台隔离）
watch(platform, () => {
  // 记住用户选的平台：下次进来自动回到同一个平台（"持久化"体验）
  try { localStorage.setItem('acq_last_platform', platform.value) } catch (e) { /* ignore */ }
  stopPolling()
  loginStatus.value = 'idle'
  qrcodeUrl.value = ''
  lastQrKey.value = ''
  errorMessage.value = ''
  loadingText.value = '正在启动浏览器...'
  savedState.value = null
  stopLiveTimer()
  liveUrl.value = ''
  liveReady.value = false
  liveMessage.value = ''
  liveViewport.value = null
  // 切换平台后重新拉取该平台的真实登录态 + 恢复仍在进行中的登录任务 + 重连实时投屏
  stopStream()
  refreshSavedState()
  resumeRunningLoginTask()
  startCaptureWatch()
  startStream()
})

const statusText = computed(() => {
  const map = {
    idle: '等待登录',
    running: '正在启动浏览器...',
    scanned: platform.value === 'fnb' ? '登录页已就绪，请按右侧画面提示完成登录' : '已生成二维码，请扫码登录',
    success: '已成功登录',
    failed: '登录失败',
    expired: '登录超时'
  }
  return map[loginStatus.value] || '等待登录'
})

const statusTagType = computed(() => {
  const map = {
    idle: 'info',
    running: 'warning',
    scanned: 'primary',
    success: 'success',
    failed: 'danger',
    expired: 'danger'
  }
  return map[loginStatus.value] || 'info'
})

// 启动登录
const handleStartLogin = async () => {
  qrLoading.value = true
  startingBrowser.value = true
  loadingText.value = `正在启动浏览器加载${platformName.value}登录页...`
  qrcodeUrl.value = ''
  errorMessage.value = ''
  loginStatus.value = 'running'
  stopPolling()

  try {
    // 调用采集服务的启动登录接口（平台隔离：登录任务 key 使用 login_${platform}）
    const data = await startCaptureLogin(platform.value)
    if (!data.success) {
      throw new Error(data.message || '启动登录失败')
    }
    ElMessage.info(`${tt("浏览器已启动，正在加载")}${platformName.value}${tt("登录页...")}`)

    // 立刻连投屏（不再白等 3 秒）：服务端在浏览器还没起来时会推 wait 事件、一就绪马上推帧，
    // 所以画面会在浏览器起来的瞬间出现；状态轮询晚 1.5 秒开始即可（那时服务端才有二维码信息）。
    startStream()
    setTimeout(() => pollLoginStatus(), 1500)
  } catch (e) {
    loginStatus.value = 'failed'
    startingBrowser.value = false
    errorMessage.value = '启动登录失败: ' + e.message
    ElMessage.error(tt('启动登录失败: ') + e.message)
  } finally {
    qrLoading.value = false
  }
}

// 轮询登录状态（带超时兜底：最长6分钟自动停止）
const POLL_TIMEOUT_MS = 6 * 60 * 1000
const pollStartTime = ref(0)
const pollFailCount = ref(0)
const MAX_POLL_FAILS = 10

const pollLoginStatus = () => {
  stopPolling()
  pollStartTime.value = Date.now()
  pollFailCount.value = 0
  pollTimer = setInterval(async () => {
    // 超时兜底
    const elapsed = Date.now() - pollStartTime.value
    if (elapsed > POLL_TIMEOUT_MS) {
      console.warn('轮询超时')
      stopPolling()
      loginStatus.value = 'expired'
      errorMessage.value = '登录超时，请重试（后端登录任务可能已完成但网络中断）'
      ElMessage.warning(tt('登录超时，轮询已停止'))
      return
    }
    try {
      const data = await getCaptureLoginStatus(platform.value)
      pollFailCount.value = 0
      // status='none'：服务端已没有该平台的登录任务（服务重启 / 任务超时清理）
      // 必须立即停止轮询，否则会一直空转到 6 分钟超时，用户只看到"正在启动浏览器..."
      if (data.status === 'none') {
        stopPolling()
        stopLiveTimer()
        loginStatus.value = 'idle'
        errorMessage.value = '登录任务已结束，请重新点击「打开采集浏览器」'
        ElMessage.warning(tt('登录任务已结束，请重新点击「打开采集浏览器」'))
        return
      }
      if (!data.success) return
      // 二维码版本变化就刷新图片：服务端每 8 秒重截一次，
      // 避免用户一直看到那张已过期的二维码（表现为"扫了没反应、状态不变"）
      const qrKey = data.qrImageUrl ? String(data.qrVersion || 'v1') : ''
      if (qrKey && qrKey !== lastQrKey.value) {
        const isFirst = !qrcodeUrl.value
        lastQrKey.value = qrKey
        qrcodeUrl.value = `${captureQrcodeUrl(platform.value)}?t=${qrKey}`
        if (isFirst) {
          const isDesktopLogin = platform.value === 'fnb'
          loadingText.value = isDesktopLogin ? '登录页已加载' : '二维码已加载，请扫码'
          const tipMap = {
            qcc: '二维码已加载，请使用企查查APP扫码',
            tyc: '二维码已加载，请使用天眼查APP扫码',
            fnb: '风鸟登录页已加载，请在右侧画面里输入手机号与验证码'
          }
          ElMessage.success(tipMap[platform.value] || tipMap.qcc)
        }
      }
      if (data.status === 'success') {
        loginStatus.value = 'success'
        stopPolling()
        // 不停实时画面：登录成功后浏览器会保活一段时间，这段时间里实时画面/点击/输入仍可用
        // （用于处理平台风控页、验证码）；到点自动关闭，也可点「关闭浏览器」提前关。
        // 也不再自动跳转获客任务页——跳走就看不了画面了，需要时用左上角「返回」。
        // 先把"已登录"状态落定（读回服务端已保存的登录态），这样下次进本页仍显示该平台已登录
        await refreshSavedState()
        ElMessage.success(tt('登录成功，Cookie已自动保存；浏览器将保留一段时间，可在右侧画面继续操作'))
      } else if (data.status === 'failed') {
        loginStatus.value = 'failed'
        errorMessage.value = data.message || '登录失败'
        stopPolling()
        // 同理不停实时画面：失败时通常需要在右侧画面里人工处理风控/验证码后再重新登录
      } else if (data.status === 'running') {
        if (qrcodeUrl.value) {
          loginStatus.value = 'scanned'
        }
      }
    } catch (e) {
      pollFailCount.value++
      if (pollFailCount.value >= MAX_POLL_FAILS) {
        console.warn(`轮询连续失败${MAX_POLL_FAILS}次，停止轮询`)
        stopPolling()
        loginStatus.value = 'expired'
        errorMessage.value = '网络异常，无法获取登录状态'
      }
    }
  }, 1000)   // 轮询间隔 2000ms → 1000ms：扫码/登录完成后页面反应更快
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 页面刷新/切回本站平台时，如果服务端还有该平台"进行中"的登录任务，
// 自动恢复展示二维码并继续轮询——避免刷新一下扫码状态就没了
const resumeRunningLoginTask = async () => {
  try {
    const data = await getCaptureLoginStatus(platform.value)
    if (!data || !data.success || data.status !== 'running') return
    if (data.qrImageUrl) {
      lastQrKey.value = String(data.qrVersion || 'v1')
      qrcodeUrl.value = `${captureQrcodeUrl(platform.value)}?t=${lastQrKey.value}`
      loginStatus.value = 'scanned'
    } else {
      loginStatus.value = 'running'
      loadingText.value = `${tt('正在等待')}${platformName.value}${tt('登录完成...')}`
    }
    pollLoginStatus()
    // 登录任务还在跑：把实时投屏恢复起来，用户可直接在页面上操作浏览器
    startStream()
  } catch (e) {
    // 登录任务不存在或采集服务未启动：保持"未登录"初始态即可
  }
}

// 清除登录态并重新扫码
// 解决：浏览器持久化目录残留登录态 → 系统"检测到已登录"跳过扫码 → 实际Cookie已过期 → 采集失败且无按钮可强制重登
const handleResetLogin = async () => {
  try {
    await ElMessageBox.confirm(
      tt('将清除该平台在服务器上的登录态（Cookie + 浏览器缓存），清除后需重新扫码登录。确认继续？'),
      tt('清除登录态确认'),
      { type: 'warning', confirmButtonText: tt('确认清除'), cancelButtonText: tt('取消') }
    )
  } catch {
    return
  }
  resetting.value = true
  errorMessage.value = ''
  stopPolling()
  try {
    const data = await resetCaptureLogin(platform.value)
    if (!data.success) {
      throw new Error(data.message || '清除登录态失败')
    }
    ElMessage.success(data.message || tt('登录态已清除，请重新扫码'))
    // 清除成功后：① 断开旧投屏 ② 立刻刷新左侧登录态标签（否则要刷新页面才更新）③ 重新拉起浏览器
    stopPolling()
    stopStream()
    qrcodeUrl.value = ''
    loginStatus.value = 'idle'
    await refreshSavedState()
    resetting.value = false
    await handleStartLogin()
  } catch (e) {
    errorMessage.value = '清除登录态失败: ' + e.message
    ElMessage.error(tt('清除登录态失败: ') + e.message)
    resetting.value = false
  }
}

const handleBack = () => {
  router.push('/acquisition/task')
}

// 从获客任务列表"重新登录"跳转过来时，URL 带 ?platform=qcc 预选平台
onMounted(() => {
  const validPlatforms = PLATFORM_TABS.map((p) => p.value)
  const qp = route.query.platform
  // 优先用 URL 指定；否则用上次选过的平台（记住用户选择，避免每次都被拉回企查查）
  let target
  if (qp && validPlatforms.includes(String(qp))) {
    target = String(qp)
  } else {
    try { target = localStorage.getItem('acq_last_platform') || '' } catch (e) { target = '' }
  }
  // 无论走哪个分支都要做四件事：① 拉当前平台登录态 ② 查另外两个平台的登录态点亮 Tab 圆点
  // ③ 恢复仍在进行中的登录任务 ④ 连实时投屏
  const init = () => {
    refreshSavedState()
    refreshOtherPlatformStates()
    resumeRunningLoginTask()
    startCaptureWatch()
    startStream()
  }
  // 键盘兜底监听（画布焦点被抢走时仍能把按键转给远程浏览器）
  document.addEventListener('keydown', handleDocumentKeyDown)
  if (target && validPlatforms.includes(target)) {
    const changed = platform.value !== target
    platform.value = target
    // 目标平台与默认值相同 → watch 不会触发，这里手动跑一遍
    if (!changed) init()
  } else {
    init()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleDocumentKeyDown)
  stopPolling()
  stopLiveTimer()
  stopCaptureWatch()
  stopStream()
})
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.login-card {
  max-width: 1360px;
  margin: 16px auto;
}

/* 顶部：标题 + 三个平台 Tab（同一行，Tab 靠右） */
.page-head {
  display: flex;
  align-items: center;
  gap: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}
.plat-tabs {
  flex: 1;
  min-width: 0;
}
.plat-tabs :deep(.el-tabs__header) { margin: 0; }
.plat-tabs :deep(.el-tabs__nav-scroll) { display: flex; justify-content: flex-end; }
.plat-tabs :deep(.el-tabs__nav) { float: none; }
.plat-tabs :deep(.el-tabs__content) { display: none; }
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
/* Tab 上的小圆点：绿=该平台已保存登录态，灰=未保存（颜色与下方状态标签同源） */
.tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d0d3d9;
}
.tab-dot.on { background: #67c23a; }

/* 所有操作按钮集中在这一排 */
.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.status-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.status-detail {
  font-size: 13px;
  color: #606266;
}
.err-tip { margin-top: 12px; }

/* 实时画面：上下结构的画面区（独占整宽、给足高度） */
.remote-area {
  margin-top: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fff;
}
.remote-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.remote-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.remote-screen {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
}
/* 实时投屏画布：CSS 只做缩放显示（坐标换算按显示宽度），需要可聚焦才能接键盘事件 */
.live-canvas {
  width: 100%;
  display: block;
  cursor: crosshair;
  outline: none;
  background: #fff;
}
.live-img {
  width: 100%;
  display: block;
  cursor: crosshair;
}
.remote-empty {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 20px 40px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  background: #fafafa;
  color: #909399;
  font-size: 13px;
  line-height: 1.9;
}
.empty-text { margin-top: 12px; }
/* 画面下方只留两行提示：当前页面 + 怎么操作（键盘输入） */
.live-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.live-hint {
  font-size: 12px;
  color: #909399;
}
.live-tip {
  font-size: 12px;
  color: #606266;
}
</style>
