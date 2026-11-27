/**
 * 语音提醒（浏览器本机语音合成 TTS）单例模块
 *
 * 设计要点（对应《小助手语音提醒_立项登记表》）：
 * 1. 与组件生命周期解耦：由 MainLayout 调 initVoiceNotify() 只初始化一次，
 *    即使用户停在设置中心（AI 小助手组件被 v-if 卸载）也能正常播报。
 * 2. 只播报「待审批」通知（type=approval），范围本期写死；后续扩展只改 SCOPE_TYPE。
 * 3. 全程静默降级：不支持语音合成 / 无中文音色 / 接口异常，一律只 console.warn，
 *    绝不抛错、不弹窗、不影响任何业务主流程。
 * 4. 不新增轮询：复用顶栏铃铛已有的 30 秒未读数轮询结果（由 NotificationBell 派发增量事件）。
 */
import { getNotifications } from '@/api/system/notification'
import { getSysConfigByGroup } from '@/api/system/sysConfig'

/** 待审批未读增加事件（NotificationBell 派发 → 本模块与 AI 小助手共同监听） */
export const EVENT_APPROVAL_INCREASE = 'erp:approval-unread-increase'

/** 本期播报范围：只播待审批 */
const SCOPE_TYPE = 'approval'

const LS_MUTED = 'erp.voiceNotify.muted'
const SS_SPOKEN = 'erp.voiceNotify.spoken'
const SS_LAST_APPROVAL = 'erp.voiceNotify.lastApproval'
const CFG_GROUP = 'notification'
const CFG_ENABLED = 'notification.voice.enabled'
const CFG_MIN_INTERVAL = 'notification.voice.minInterval'
const DEFAULT_MIN_INTERVAL = 60

// 播报语速（本机个人偏好，与小助手 ⚙️ 的「播报字数」同卡片；0.5~2 为可用区间）
const LS_RATE = 'erp.voiceNotify.rate'
const DEFAULT_SPEAK_RATE = 1       // 默认 1.0 = 与升级前行为完全一致
const RATE_MIN = 0.5
const RATE_MAX = 2

// ===== 模块级单例状态 =====
let inited = false
let globalEnabled = false          // 全局开关（设置中心），默认关闭
let minInterval = DEFAULT_MIN_INTERVAL
let muted = false                  // 本机个人静音
// 本机播报语速：**模块加载即读本机持久值**（小助手组件 setup 早于 MainLayout 的 onMounted，
// 若只靠 initVoiceNotify 赋值，刷新后 ⚙️ 里会错误回显默认 1.0）
let speakRate = readRate()

// 本机选择的播报语音：**只保留一条通道**（服务器离线自然女声）。
// 2026-10-25 用户明确要求：「不要双语音通道了，我就要只保留一个最新的语音」——
// 不再提供"跟随浏览器内置"，历史存过 auto 的一律按自然女声处理。
let voiceName = 'huayan'

/** Piper 本地离线引擎 HTTP 地址（后端 AiTtsController 代理） */
const PIPER_TTS_URL = '/api/ai/tts/synthesize'
let lastSpeakAt = 0                // 上次播报时间戳（毫秒）
let spokenIds = new Set()          // 本次会话已播报过的通知 id（防重复）

/** 浏览器是否支持语音合成 */
export function isSpeechSupported() {
  return typeof window !== 'undefined' && !!window.speechSynthesis && typeof window.SpeechSynthesisUtterance === 'function'
}

/** 当前是否静音（本机） */
export function isMuted() {
  return muted
}

/** 全局开关是否打开 */
export function isGlobalEnabled() {
  return globalEnabled
}

/**
 * 当前本机播报语速（0.5~2，默认 1.0）。
 * 用途：小助手 ⚙️「播报语速」下拉回显；与 🔊 语音提醒/🔇 静音同为本机个人偏好，不进数据库。
 */
export function getSpeakRate() {
  return speakRate
}

/**
 * 设置本机播报语速：越界/非法值一律回落 1.0（与升级前一致），成功返回实际生效值。
 * 立即生效（不用刷新页面），因为 speak() 每次发声都读 speakRate。
 */
export function setSpeakRate(rate) {
  const n = Number(rate)
  speakRate = (!Number.isNaN(n) && n >= RATE_MIN && n <= RATE_MAX) ? n : DEFAULT_SPEAK_RATE
  try {
    if (speakRate === DEFAULT_SPEAK_RATE) localStorage.removeItem(LS_RATE)
    else localStorage.setItem(LS_RATE, String(speakRate))
  } catch (e) { /* 隐私模式：忽略 */ }
  return speakRate
}

/** 读取本机语速（无记录/非法值 = 1.0，保证老用户升级后行为不变） */
function readRate() {
  try {
    const raw = localStorage.getItem(LS_RATE)
    if (raw == null) return DEFAULT_SPEAK_RATE
    const n = Number(raw)
    return (!Number.isNaN(n) && n >= RATE_MIN && n <= RATE_MAX) ? n : DEFAULT_SPEAK_RATE
  } catch (e) { return DEFAULT_SPEAK_RATE }
}

/**
 * 本机播报语音：只剩一条通道（服务器离线自然女声）。
 * 传任何值都返回 'huayan'，保证不会再有第二条语音通道。
 */
export function getVoiceName() {
  return voiceName
}

export function setVoiceName() {
  voiceName = 'huayan'
  try { localStorage.setItem('erp.voiceNotify.voiceName', voiceName) } catch (e) { /* 忽略 */ }
  return voiceName
}

/** 可选的语音清单（只剩自然女声一项） */
export function listVoices() {
  return [
    { value: 'huayan', label: '小楷 · 自然女声' }
  ]
}

/** 切换本机静音，返回切换后的状态 */
export function toggleMute() {
  muted = !muted
  try {
    if (muted) localStorage.setItem(LS_MUTED, '1')
    else localStorage.removeItem(LS_MUTED)
  } catch (e) { /* 隐私模式：忽略 */ }
  return muted
}

/** 重新读取本机静音标记 */
export function reloadMuted() {
  muted = readMuted()
  return muted
}

/** 读取全局开关与最小间隔（失败按"关闭 + 默认间隔"处理，与旧版本行为一致） */
export async function refreshVoiceConfig() {
  try {
    const res = await getSysConfigByGroup(CFG_GROUP)
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    for (const item of list) {
      if (!item || !item.configKey) continue
      if (item.configKey === CFG_ENABLED) {
        globalEnabled = String(item.configValue).toLowerCase() === 'true'
      } else if (item.configKey === CFG_MIN_INTERVAL) {
        const n = Number(item.configValue)
        if (!Number.isNaN(n) && n > 0) minInterval = n
      }
    }
    console.info('[语音提醒] 初始化完成（全局开关=' + (globalEnabled ? '开' : '关')
      + '，本机静音=' + (muted ? '是' : '否')
      + '，最小间隔=' + minInterval + '秒'
      + '，浏览器支持=' + (isSpeechSupported() ? '是' : '否') + '）')
  } catch (e) {
    globalEnabled = false
    console.warn('[语音提醒] 读取全局开关失败，按关闭处理', e && e.message)
  }
}

/**
 * 初始化（幂等）：MainLayout 挂载时调一次
 */
export function initVoiceNotify() {
  if (inited) return
  inited = true
  reloadMuted()
  speakRate = readRate()
  loadSpoken()
  try {
    window.addEventListener(EVENT_APPROVAL_INCREASE, onApprovalIncrease)
  } catch (e) { /* 忽略 */ }
  refreshVoiceConfig()
  // 预热语音列表：getVoices 首次可能异步返回，先调一次避免首次播报挑不到中文音色
  try { window.speechSynthesis?.getVoices() } catch (e) { /* 忽略 */ }
}

/** 试听：忽略开关与静音，强制发声（用于设置中心确认本机语音可用） */
export function previewSpeak() {
  speak('您有一条待审批单据，请及时处理。', true)
  return true
}

/**
 * 直接朗读一段文本（强制发声，不受语音提醒开关/静音影响）。
 * 用途：语音唤醒后的应答、小助手执行结果播报等交互反馈。
 * 语音通道只有一条（服务器离线自然女声），失败不降级系统音色。
 *
 * @param {string} text 要念的文本
 * @param {'result'|'progress'|'notice'} [kind='result'] 播报类别：
 *        result = 用户主动问的结果（可打断 notice，但**不掐断**正在念的 progress）
 *        progress = 等待期进度播报（只在没人说话时念，绝不打断任何正在念的内容）
 *        notice = 待审批提醒等系统通知
 */
export function speakText(text, kind = 'result') {
  if (!text) return false
  enqueueSpeech(text, true, kind, true)
  return true
}

/**
 * 唤醒应答「我在，请说。」：优先播放随包预置的自然女声，命中即响，不等服务器合成。
 *
 * 为什么单独做（2026-10-25 用户实测"唤醒命中后答复等太久，像功能坏了"）：
 *   原来命中后走 speakText → 先请求服务器合成自然女声（一次约 2 秒），才开始出声；
 *   而这句应答是固定文案，完全可以提前打包成音频，命中直接播。
 *   预置音频缺失、切换了语言、或用户选了系统音色时，自动退回原有播报，不会没声音。
 *
 * @param {string} [text] 应答文案（默认「我在，请说。」）
 * @returns {boolean} true = 已走预置音频；false = 已退回普通播报
 */
export const WAKE_ACK_TEXT = '我在，请说。'
const WAKE_ACK_SRC = '/audio/xiaokai-wake-ack.mp3'
let wakeAckAudio = null

function ensureWakeAckAudio() {
  if (wakeAckAudio) return wakeAckAudio
  try {
    wakeAckAudio = new Audio(WAKE_ACK_SRC)
    wakeAckAudio.preload = 'auto'
    wakeAckAudio.volume = 1
  } catch (e) {
    wakeAckAudio = null
  }
  return wakeAckAudio
}

// 模块加载即预取（约 10KB），第一次喊唤醒词也不用等下载
try { ensureWakeAckAudio() } catch (e) { /* 忽略 */ }

export function playWakeAck(text) {
  const content = text || WAKE_ACK_TEXT
  // 非默认文案（换了语言）→ 走自然女声合成（仍只有一条通道）
  if (content !== WAKE_ACK_TEXT) {
    speakText(content, 'result')
    return false
  }
  const audio = ensureWakeAckAudio()
  if (!audio) {
    speakText(content, 'result')
    return false
  }
  try {
    try { window.speechSynthesis && window.speechSynthesis.cancel() } catch (e) { /* 忽略 */ }
    audio.playbackRate = speakRate > 0 ? speakRate : DEFAULT_SPEAK_RATE
    audio.currentTime = 0
    const p = audio.play()
    if (p && typeof p.catch === 'function') {
      p.catch(() => speakText(content, 'result'))
    }
    return true
  } catch (e) {
    speakText(content, 'result')
    return false
  }
}

/**
 * 固定话术音频表（《小楷语音交互设计》v1.0 的 16 条固定答复）。
 * 命中即播随包音频；音频缺失、用户选了系统音色、切换语言时退回服务器/本机播报，保证不会没声音。
 */
const CUE_MAP = {
  idle: { file: '/audio/xk-idle.mp3', text: '我先待命，需要直接喊我。' },
  wakeCmd: { file: '/audio/xk-wake-cmd.mp3', text: '别急，先喊你好小楷' },
  notHeard: { file: '/audio/xk-not-heard.mp3', text: '小楷没有听清楚，请再说一遍。' },
  tooShort: { file: '/audio/xk-too-short.mp3', text: '没听清楚，请再说一遍。' },
  asrFail: { file: '/audio/xk-asr-fail.mp3', text: '这次没听清，请再说一遍。' },
  working: { file: '/audio/xk-working.mp3', text: '我查一下。' },
  slow: { file: '/audio/xk-slow.mp3', text: '还在查，请稍等。' },
  confirmAsk: { file: '/audio/xk-confirm-ask.mp3', text: '确认还是取消？' },
  confirmTimeout: { file: '/audio/xk-confirm-timeout.mp3', text: '没有听到确认，这次不执行。' },
  cancelled: { file: '/audio/xk-cancelled.mp3', text: '已取消，没有做任何改动。' },
  bye: { file: '/audio/xk-bye.mp3', text: '好的。' },
  voiceOff: { file: '/audio/xk-voice-off.mp3', text: '语音已关闭。' },
  noPerm: { file: '/audio/xk-no-perm.mp3', text: '你没有这个权限，请找管理员开。' },
  noCredit: { file: '/audio/xk-no-credit.mp3', text: '积分用完了，充值后再用。' },
  stop: { file: '/audio/xk-stop.mp3', text: '小楷没听清，结束这次语音对话' }
}
const cueAudios = {}

/** 取某条固定话术的文案（音频缺失时用于退回播报） */
export function cueText (key) {
  const c = CUE_MAP[key]
  return c ? c.text : ''
}

function ensureCueAudio (key) {
  if (cueAudios[key]) return cueAudios[key]
  const c = CUE_MAP[key]
  if (!c) return null
  try {
    const a = new Audio(c.file)
    a.preload = 'auto'
    a.volume = 1
    cueAudios[key] = a
  } catch (e) {
    cueAudios[key] = null
  }
  return cueAudios[key]
}

/**
 * 播一条固定话术：优先随包音频（≤300ms 出声），失败/不可用时退回 speakText。
 * @param {string} key CUE_MAP 的键（idle / bye / cancelled / wakeCmd ...）
 * @returns {boolean} true = 走了音频；false = 已退回播报
 */
export function playCue (key) {
  const c = CUE_MAP[key]
  if (!c) return false
  const fallback = () => { speakText(c.text, 'result') }
  const audio = ensureCueAudio(key)
  if (!audio) {
    fallback()
    return false
  }
  try {
    try { window.speechSynthesis && window.speechSynthesis.cancel() } catch (e) { /* 忽略 */ }
    audio.onerror = null
    audio.onerror = () => { audio.onerror = null; fallback() }
    audio.playbackRate = speakRate > 0 ? speakRate : DEFAULT_SPEAK_RATE
    audio.currentTime = 0
    const p = audio.play()
    if (p && typeof p.catch === 'function') p.catch(() => fallback())
    return true
  } catch (e) {
    fallback()
    return false
  }
}

/**
 * 等待期「进度播报」（"好的，我正在思考 / 正在查询数据"）。
 * 与 speakText 的区别：**不打断**任何正在念的内容；若已经在念东西则直接丢弃（进度话术过期没意义）。
 * 目的（2026-09-18 用户实测"纯语音吞播报听不全"）：结果播报不再把念到一半的进度话术掐断成残句。
 */
export function speakProgress(text) {
  if (!text) return false
  if (activeRun || pendingQueue.length) return false      // 正在念/有待念 → 丢弃，不打断
  enqueueSpeech(text, true, 'progress', false)
  return true
}

/**
 * 从通知标题解析「单据类型中文名」。
 * 兼容后端两种标题格式（有据）：
 *   事件驱动：`待办审批: 销售订单 SO20260914001（经理审批）`  ← SysNotificationService
 *   定时扫描：`待审批：销售订单`                            ← NotificationScheduler
 * 解析不出返回空串（播报时降级，绝不回退去读单号）。
 */
export function parseBillTypeName(title) {
  if (!title) return ''
  const m = /^(?:待办审批|待审批)[:：]\s*(\S+)/.exec(String(title).trim())
  return m ? m[1] : ''
}

/**
 * 播报文本：读【单据类型】不读单号（用户 2026-09-14 明确要求）
 * @param {number} count    待审批未读总数
 * @param {string} typeName 最新一条的单据类型中文名（可为空）
 */
export function buildApprovalText(count, typeName) {
  const n = count > 0 ? count : 1
  if (!typeName) {
    return n === 1
      ? '您有待审批单据，请及时处理。'
      : '您有 ' + n + ' 张待审批单据，请及时处理。'
  }
  return n === 1
    ? '您有一张' + typeName + '待您审批，请及时处理。'
    : '您有 ' + n + ' 张待审批单据，最新一张是' + typeName + '，请及时处理。'
}

/**
 * 最近一次被提醒的待审批单据（供 AI 上下文：用户说"打开它/帮我审批通过"时定位到具体单据）。
 * @returns {{billType:string, billNo:string, billId:number|null, title:string, at:number}|null}
 */
export function getLastApprovalContext() {
  try {
    const raw = sessionStorage.getItem(SS_LAST_APPROVAL)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

/** 由通知记录构造并落地上下文 */
function saveApprovalContext(rec) {
  if (!rec) return null
  // sourceType 两条产生路径分别是大写枚举 / 小写 → 统一转大写，供 bill_action 的 billType 直接使用
  const ctx = {
    billType: rec.sourceType ? String(rec.sourceType).toUpperCase() : '',
    billNo: rec.sourceNo || '',
    billId: rec.sourceId != null ? Number(rec.sourceId) : null,
    title: rec.title || '',
    at: Date.now()
  }
  try {
    sessionStorage.setItem(SS_LAST_APPROVAL, JSON.stringify(ctx))
  } catch (e) { /* 隐私模式：忽略 */ }
  return ctx
}

/**
 * 把 AI 答复裁剪成"适合朗读的摘要"（语音指令结果播报用）。
 * 答复里常有 Markdown 表格 / 清单 / 链接 / emoji，全文朗读又长又听不明白 → 先去标记，再按上限取字数。
 *
 * 口径（用户 2026-09-18 修订，原"只念第一句"作废）：
 *   纯语音交流时"念到一半就没了"用户无法判断是否成功、是否正常运行（用户原话：Siri 定闹钟也会完整回一句"已定好"）
 *   → ① 优先**按句拼接**念到接近 limit 字；② 一句都没拼够时用剩余内容补齐，并在**自然停顿（。！？；，、）处收尾**，
 *     绝不硬切到半个词；③ 确实没有任何停顿点才按字数截断加省略号。
 */
export function buildSpeakSummary(text, limit) {
  const max = limit > 0 ? limit : 40
  let t = String(text || '')
  t = t.replace(/```[\s\S]*?```/g, ' ')          // 代码块
    .replace(/\|[^\n]*\|/g, ' ')                 // Markdown 表格行
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')   // 链接/图片 → 保留可见文字
    .replace(/[*_`#>~]/g, '')                    // 其余 Markdown 标记
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
  // 去掉"直接可见的告警前缀"：成功/失败的口径由调用方决定，避免念出"警告 操作失败"
  t = t.replace(/^(?:⚠️|⚠|❌|✅|\s)+/, '').trim()
  if (!t) return ''

  const sentences = (t.match(/[^。！？；;!?]+[。！？；;!?]?/g) || [t]).map(s => s.trim()).filter(Boolean)
  // ① 按句拼接：从第一句起逐句加，直到"再加一句就超上限"
  let out = ''
  let next = 0
  for (; next < sentences.length; next++) {
    if (out && out.length + sentences[next].length > max) break
    out += sentences[next]
  }
  // ② 首句本身就超上限 → 也在自然停顿处收尾（不再"按字数硬切"）
  if (out.length >= max) return cutAtNaturalPause(out, max)
  // ③ 整句拼会超、已拼内容却不足设定字数 → 用剩余内容补齐，并在自然停顿处收尾
  const rest = sentences.slice(next).join('')
  if (!rest) return out
  return cutAtNaturalPause(out + rest, max)
}

/**
 * 在自然停顿处收尾（**绝不切成半个词**，2026-09-18 用户实测"听不全"后加固）。
 *   ① 上限内最后一个**句末停顿**（。！？；）→ 在那里收；
 *   ② 没有句末停顿 → 退而在上限内最后一个**逗号/顿号/空格**处收（听起来是停顿，不是断字）；
 *   ③ 连停顿都没有（如长单号连续串）→ 才按字数截断加省略号。
 * 收尾位置不会超过用户设定字数（宁短勿断）。
 */
function cutAtNaturalPause(text, max) {
  const seg = String(text || '')
  if (seg.length <= max) return seg
  const strong = /[。！？；!?;]/
  const weak = /[，,、\s]/
  const floor = Math.max(10, Math.floor(max * 0.6))      // 收尾点太靠前就等于没念，往后找最近的停顿
  let i
  for (i = max - 1; i >= floor; i--) if (strong.test(seg[i])) return seg.slice(0, i + 1)
  for (i = max - 1; i >= floor; i--) if (weak.test(seg[i])) return seg.slice(0, i + 1)
  return seg.slice(0, max) + '…'
}

// ===== 播报队列与分段（2026-09-18 用户实测「纯语音吞播报、听不全」的根治）=====
// 三个已定位的病因 → 对应改法：
//   ① speak() 每次发声前无条件 cancel() → 正在念的句子被掐断成残句
//      → 改为队列：**结果播报遇到"正在念进度话术"时排队等它念完**，不掐断；进度话术若有人在念则直接丢弃；
//   ② 长句（60 字档 + 慢速）在部分引擎上会中途停止
//      → 按标点切成 ≤60 字分段，逐段 onend 串联；
//   ③ utterance 未被持有引用可能被 GC 中途停止 + 时长估算封顶会提前判"说完了"
//      → 模块级变量持有当前 utterance；speakUntil 按"剩余分段 + 排队内容"累计刷新，并加兜底看门狗。
const SEG_MAX_CHARS = 60          // 单段最长字数（按标点断句，绝大多数句子都短于它）
let speakUntil = 0                // 预计播报到什么时刻（isSpeaking 的估算上限）
let activeRun = null              // 当前正在念的这一轮 { kind, segs, idx, utter }
let pendingQueue = []             // 排队等待播报的 [{ text, force, kind }]
let runWatchdog = null            // 兜底：引擎不回 onend 时强制收尾，避免队列永久卡死

/** 把文本切成适合朗读的分段：优先在 。！？；，、 处断，单段不超过 SEG_MAX_CHARS 字 */
function splitForSpeech(text) {
  const t = String(text || '').trim()
  if (!t) return []
  const parts = t.match(/[^。！？；;!?，,、]+[。！？；;!?，,、]?/g) || [t]
  const out = []
  let buf = ''
  for (const p of parts) {
    if (buf && (buf + p).length > SEG_MAX_CHARS) { out.push(buf); buf = '' }
    buf += p
    while (buf.length > SEG_MAX_CHARS) { out.push(buf.slice(0, SEG_MAX_CHARS)); buf = buf.slice(SEG_MAX_CHARS) }
  }
  if (buf) out.push(buf)
  return out
}

/**
 * 按字数估算播报时长上限（中文约 4~5 字/秒，留足余量；总上限 20 秒）。
 * 慢速（rate<1）实际发声更久 → 按语速折算，避免估短了"播报还没结束就被当成说完了"。
 */
function estimateSpeakMs(text) {
  const base = String(text || '').length * 300 + 3000
  const rate = speakRate > 0 ? speakRate : DEFAULT_SPEAK_RATE
  return Math.min(20000, base / rate)
}

/** 剩余内容（当前分段剩余 + 排队等待的）→ 刷新"说到什么时候"，并重设兜底看门狗 */
function refreshSpeakUntil() {
  const restRun = activeRun ? activeRun.segs.slice(activeRun.idx).join('') : ''
  const restQueue = pendingQueue.map(q => q.text).join('')
  const rest = restRun + restQueue
  speakUntil = rest ? Date.now() + estimateSpeakMs(rest) : 0
  if (runWatchdog) { clearTimeout(runWatchdog); runWatchdog = null }
  if (rest) {
    runWatchdog = setTimeout(() => {          // 引擎没回 onend 的兜底：强制收尾，别把后续播报卡死
      if (activeRun) { try { window.speechSynthesis.cancel() } catch (e) { /* 忽略 */ } }
      activeRun = null
      playNextInQueue()
    }, estimateSpeakMs(rest) + 4000)
  }
}

/** 念当前分段；念完自动进入下一段 —— 优先后端 Piper 自然女声，失败降级本机 */
async function speakCurrentSegment() {
  const run = activeRun
  if (!run) return
  if (run.idx >= run.segs.length) { activeRun = null; playNextInQueue(); return }
  const text = run.segs[run.idx]

  // 走后端 Piper：选了自然女声（非 auto）+ 浏览器能 fetch 就用。
  // 不再看 muted —— 强制播报（用户主动用语音问、待审批提醒等）本来就会出声，
  // 静音不该把音色悄悄降级成浏览器语音（用户实测：试听是新女声，查询播报还是旧声音）。
  const usePiper = voiceName && voiceName !== 'auto' && fetchAvailable()
  const tryPiper = usePiper
    ? fetch(PIPER_TTS_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(authToken() ? { 'Authorization': `Bearer ${authToken()}` } : {})
        },
        body: JSON.stringify({ text, voiceName })
      }).then(async r => {
        if (!r.ok) throw new Error('tts http ' + r.status)
        const blob = await r.blob()
        if (!blob || blob.size < 1000) throw new Error('empty wav')
        return blob
      }).catch(e => { console.warn('[语音提醒] Piper 降级：', e.message); return null })
    : Promise.resolve(null)

  const wavBlob = await tryPiper

  // 成功用 audio 播放 WAV
  if (wavBlob) {
    try {
      const url = URL.createObjectURL(wavBlob)
      const audio = new Audio(url)
      audio.volume = 1
      run.audio = audio        // 持有引用（防止 GC 中断）
      run._audioUrl = url
      const finish = () => {
        if (activeRun !== run) return
        try { audio.pause(); URL.revokeObjectURL(run._audioUrl) } catch (e) { /* 忽略 */ }
        run.audio = null; run._audioUrl = null
        run.idx += 1; refreshSpeakUntil(); speakCurrentSegment()
      }
      audio.onended = finish
      audio.onerror = finish
      refreshSpeakUntil()
      await audio.play()
      return
    } catch (e) {
      console.warn('[语音提醒] audio 播放失败，降级本机：', e.message)
      try { URL.revokeObjectURL(run._audioUrl) } catch (e2) { /* 忽略 */ }
    }
  }

  // 只有一条语音通道（离线自然女声）：服务器合成失败时不偷偷切到浏览器音色。
  // 2026-10-25 用户明确要求去掉双通道；这里如实告警并跳过本段，避免"一会儿自然女声、一会儿系统音"。
  console.warn('[语音提醒] 自然女声合成失败，本段跳过（不降级系统音色）')
  run.idx += 1
  refreshSpeakUntil()
  speakCurrentSegment()
}

/** 浏览器 fetch 是否可用 */
function fetchAvailable() {
  try { return typeof window.fetch === 'function' } catch (e) { return false }
}

/** 取登录凭证：裸 fetch 不走 axios 拦截器，必须自己带 Authorization，否则 401 */
function authToken() {
  try { return localStorage.getItem('token') || '' } catch (e) { return '' }
}

/** 取队列里的下一条开始念；队列空 → 视为"说完了"（采集可恢复） */
function playNextInQueue() {
  const next = pendingQueue.shift()
  if (!next) {
    speakUntil = 0
    if (runWatchdog) { clearTimeout(runWatchdog); runWatchdog = null }
    return
  }
  startSpeech(next.text, next.force, next.kind)
}

/** 开始一轮播报（先打断当前，再从头念） */
function startSpeech(text, force, kind) {
  if (!force && (muted || !globalEnabled)) return
  const segs = splitForSpeech(text)
  if (!segs.length) return
  try { window.speechSynthesis && window.speechSynthesis.cancel() } catch (e) { /* 忽略 */ }
  activeRun = { kind: kind || 'result', segs, idx: 0, utter: null }
  speakCurrentSegment()
}

/**
 * 入队播报（**唯一**的播报入口）
 * @param {boolean} interrupt 是否想立刻念：true = 抢占式
 *   ① 正在念 **progress**（进度话术）→ 排队等它念完（用户实测：掐断进度话术听着像"吞了一半"）
 *   ② 正在念其它内容 → 打断并立即念（新结果比旧内容重要）
 * @param {'result'|'progress'|'notice'} kind 播报类别
 */
function enqueueSpeech(text, force, kind, interrupt) {
  if (!text) return
  if (!force && (muted || !globalEnabled)) return
  if (activeRun) {
    if (interrupt && activeRun.kind !== 'progress') {
      pendingQueue = []                    // 抢占：丢掉旧排队，只念最新这条
      startSpeech(text, force, kind)
      return
    }
    if (interrupt) pendingQueue.unshift({ text, force, kind })   // 不掐断进度话术 → 排到最前面等它念完
    else pendingQueue.push({ text, force, kind })
    refreshSpeakUntil()
    return
  }
  startSpeech(text, force, kind)
}

/**
 * 小助手此刻是否正在语音播报（含排队等待中的内容）。
 * 采集侧（serverWakeup）靠它丢弃"自己声音被听回来"的帧。
 * 注意：**不能只看 `speechSynthesis.speaking`** —— Chromium 上 cancel() 紧跟 speak() 会让它偶发不复位
 * （采集被永久丢弃，表现为"喊了没反应"）；分段播报时它还会在段与段之间瞬时 false（会把尾音录回来）。
 * 故判据 = "有在念或待念的内容" + 按字数估算的截止时间兜底（超时即视为已说完）。
 */
export function isSpeaking() {
  if (!activeRun && !pendingQueue.length) return false
  return Date.now() < speakUntil
}

/** 底层发声（兼容入口：待审批提醒与试听沿用"立即念"语义） */
function speak(text, force) {
  enqueueSpeech(text, force, 'notice', true)
}

/** 待审批未读增加 → 拉最新一条取单号 → 去重 + 节流 → 播报 */
async function onApprovalIncrease(event) {
  try {
    const detail = event && event.detail ? event.detail : {}
    const count = Number(detail.count || 0)
    const delta = Number(detail.delta || 0)
    // 只在真实新增时播报；且开关/静音两者都满足
    if (delta <= 0) return
    if (!globalEnabled || muted) return

    // 节流：两条播报之间至少间隔 minInterval 秒（防止批量生成时连续刷屏）
    const now = Date.now()
    if (lastSpeakAt && now - lastSpeakAt < minInterval * 1000) return

    // 取最新一条未读待审批通知：用于播报【单据类型】并记录 AI 上下文（失败则降级播报）
    let latest = null
    try {
      const page = await getNotifications({ current: 1, size: 1, type: SCOPE_TYPE, isRead: 0 })
      latest = (page && page.records && page.records[0]) || null
    } catch (e) {
      // 忽略：降级为不带类型播报
    }

    // 同一条通知不重复播报
    if (latest && latest.id != null) {
      if (spokenIds.has(latest.id)) return
      spokenIds.add(latest.id)
      saveSpoken()
    }

    // 记录上下文：之后用户说"打开它 / 帮我审批通过"，AI 据此定位到这张单
    saveApprovalContext(latest)

    lastSpeakAt = now
    speak(buildApprovalText(count, parseBillTypeName(latest && latest.title)), false)
  } catch (e) {
    console.warn('[语音提醒] 处理待审批提醒失败（已忽略）', e && e.message)
  }
}

// ===== 本机静音标记（localStorage） =====
function readMuted() {
  try {
    return localStorage.getItem(LS_MUTED) === '1'
  } catch (e) {
    return false
  }
}

// ===== 已播报通知 id（sessionStorage，防刷新后重复播报同一条） =====
function loadSpoken() {
  try {
    const raw = sessionStorage.getItem(SS_SPOKEN)
    const arr = raw ? JSON.parse(raw) : []
    spokenIds = new Set(Array.isArray(arr) ? arr : [])
  } catch (e) {
    spokenIds = new Set()
  }
}

function saveSpoken() {
  try {
    // 只保留最近 200 个，避免无限增长
    const arr = Array.from(spokenIds).slice(-200)
    sessionStorage.setItem(SS_SPOKEN, JSON.stringify(arr))
  } catch (e) { /* 隐私模式：忽略 */ }
}
