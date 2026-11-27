/**
 * 【POC · 实验性】服务器端语音唤醒（"喊『你好，小楷』就唤醒"）
 *
 * 为什么走这条路（依据）：
 *   浏览器自带的语音识别两条路都靠不住 ——
 *   ① 云端识别：把音频送厂商云，国内常连不通（现象：喊了没反应、🎤 秒退零结果）；
 *   ② 设备端识别：语音包是浏览器自己的组件（网页无权安装、每台终端各一份），
 *      实测本机 Edge 153 对 zh-CN/en-US 等**全部 unavailable**、install() 0ms 空操作。
 *   而"服务器端识别"只依赖**标准 API**（麦克风 + 网络），**适配面最广**：不挑浏览器厂商、不挑 CPU。
 *
 * 工作方式（带宽可控版）：
 *   浏览器常驻采集 → 本地能量门限（只在"有人说话"时才动作）→ 只上传约 1.5 秒短片段
 *   → 后端复用自建 ASR 返回文本 → 命中唤醒词则派发与浏览器唤醒**同一个事件** EVENT_WAKEUP
 *   → 唤醒后的逻辑（应答「小楷在，请说你的指令。」/ 聆听态 / 结果播报）**完全复用既有实现**。
 *
 * 隐私说明：只有被能量门限判定为"有人在说话"的短片段会上传到**你自己的服务器**（不出你的服务器、不进第三方）；
 *   本模块由小助手里的实验开关控制，默认关闭。
 *
 * 记录与面板：原先的「自测面板」与配套统计（stats / 日志）已按用户要求**整套删除**（2026-09-18）——
 *   界面不显示、后台也不记录。本模块只剩"采音 → 上传识别 → 派发唤醒事件"这一条功能链路。
 */
import { EVENT_WAKEUP, matchesWake, mentionsWakeName, containsWakePhrase } from '@/utils/voiceWakeup'
import { encodeWav } from '@/utils/wavEncode'

/* 门限与切段参数（2026-09-17 按用户首轮实测结果调整）
 * 首轮问题：固定门限 0.02 + 固定切 1.5 秒 → 一次说话被切成多段（触发上传 49 次，虚高），
 *           且环境噪声也会触发。改为：**自适应门限 + 说完（静音）才上传**。 */
const FRAME = 2048            // 每帧采样数（2048/16000 ≈ 128ms）
const FRAME_MS = 128
const LEAD_FRAMES = 3         // 触发时向前回补的帧数（避免吃掉字头）
const NOISE_ALPHA = 0.99      // 噪声基线平滑系数（静默时更新）
const MIN_RMS_FLOOR = 0.008   // 门限下限：极安静环境也不要低于此值
const RMS_MULTIPLIER = 2.0    // 进入"说话"的阈值 = max(下限, 噪声基线 × 倍数)
const MAX_RMS_THRESHOLD = 0.03 // **门限上限**（2026-09-17 用户实测）：噪声大时不许高到"吃字" ——
                               // 实测门限冲到 0.055 时，1.15 秒的喊话只有 0.5 秒被算作语音 → 被误当"太短"丢掉
const RELEASE_RATIO = 0.6     // **滞回**：已判定"在说话"期间改用更低阈值（阈值×0.6）继续收字，
                              // 避免句子中间较弱的字被当成静音而提前截断（这是"有效语音被低估"的根因）
const MIN_SPEECH_MS = 400     // 一段语音最短"有效语音"时长：不足则丢弃（噪声碎片不上传，省服务器）
                              // 2026-10-21 由 500 下调到 400：喊「小楷」只有两个字、零点几秒，
                              // 500ms 会把这类短喊话直接丢掉（用户实测「唤醒不灵敏」的成因之一）。
                              // 误收一段噪声的代价只是多一次识别，且识别结果还要命中唤醒词才会唤醒。
const MAX_SPEECH_MS = 2500    // 一段语音最长时长：到点强制切断上传
const SILENCE_MS = 180        // 连续静音达到这么久 → 判定"这段说完了"
                              // 2026-10-21 400→250：用户要求唤醒响应快，判句更早、上传更早、应答更早
                              // 2026-10-25 250→180：用户反馈"命中后答复还是慢"，唤醒词只有四个字，
                              // 再收紧 70ms；只影响唤醒词段，指令段仍是 CMD_SILENCE_MS=1200（不会截断指令）
const COOLDOWN_MS = 800       // 上传后的冷却
const HIT_MUTE_MS = 2500      // 命中唤醒后静默：避免一次喊话连发多次唤醒（且覆盖应答播报时间，防自激）
/* 命中唤醒后进入「等指令」态的采集参数（与喊唤醒词时不同：指令更长、允许句内停顿） */
const CMD_MIN_VOICED_MS = 300 // 指令最短有效语音：太短的当噪声（如咳嗽）
const CMD_MAX_MS = 8000       // 指令最长 8 秒：到点强制切断上传（切断后仍会进"续话窗口"，不会立刻发送）
const CMD_SILENCE_MS = 900    // 指令尾部静音 0.9 秒即认为"这一段说完了"（《小楷语音交互设计》v1.0 定稿）
                              // 2026-09-17 曾因"指令还没说完就自动发送"从 700 提到 1200；
                              // 现在仍保留 2 秒续话窗口 + 句尾未完成词再加 600 毫秒，所以不会把话截断。
                              // 若句尾是「的/了/和/帮/给」这类未完成词，由 CMD_TAIL_EXTEND_MS 补时。
const CMD_MERGE_WAIT_MS = 2000 // **续话窗口**：识别出一段后先不发，等 2 秒；用户若接着说 → 合并成同一条指令
                               // 2026-10-21 用户指定：识别到指令后 2 秒内没有后续语音 = 这段指令完结，直接发送处理
const CMD_TAIL_EXTEND_MS = 600 // 句尾是「的/了/和/帮/给」这类未完成词时，续话窗口再补 600 毫秒
                               // 依据《小楷语音交互设计》v1.0：句尾没说完时不要急着发出去
const CMD_FORCE_SEND_MS = 9000 // **硬上限（必须有）**：从"第一次识别出文字"起最多等 9 秒，到点无论如何都发出去。
                               // 2026-09-18 实测踩坑：派发条件是"窗口到点 且 当前没在收集人声段"，如果用户说完一句
                               // 停顿不足 1.5 秒就接着说（或环境持续有声），`speaking` 会长期为 true → 派发被无限推迟、
                               // 文本不断合并，而"等指令超时取消"对"已有待发送文本"的情况不生效 → **指令永远发不出去**
                               // （表现为"喊了一次之后，后面说的话助手完全不理/收不到"）。硬上限保证一定有出库。
const DEFAULT_CMD_WAIT_MS = 5000 // 命中唤醒后最多等 5 秒（设计定稿默认值）：还没听到指令 →
                                         // 明确答复「我先待命，需要直接喊我。」并结束本次语音对话（不许静默）。
                                         // 2026-10-21 原为 10 秒；2026-10-25 按《小楷语音交互设计》v1.0 改为 5 秒。
                                         // **运行值由内部参数下发**（`ai.voice.cmdWaitSec`，全局统一、客户界面无入口），
                                         // 这里只是取不到配置时的兜底，不在代码里写死业务范围。
let cmdWaitTimeoutMs = DEFAULT_CMD_WAIT_MS

/** 设置「等待指令」时长（毫秒）。只校验必须大于 0，不限制区间——档位由平台参数决定。 */
export function setCmdWaitTimeoutMs (ms) {
  const n = Number(ms)
  if (!Number.isNaN(n) && n > 0) cmdWaitTimeoutMs = Math.round(n)
  return cmdWaitTimeoutMs
}

/** 当前「等待指令」时长（毫秒），供界面/自检读取 */
export function getCmdWaitTimeoutMs () {
  return cmdWaitTimeoutMs
}
// 唤醒词与匹配规则**不在这里定义**：统一放在 voiceWakeup.js（全项目唯一来源，避免两处规则漂移）

let running = false
let stream = null
let audioCtx = null
let source = null
let processor = null
let recent = []               // 环形缓冲：最近几帧（用于回补字头）
let segFrames = []            // 当前这一段"人声"的帧
let segMs = 0                 // 当前段已累计时长（含回补的字头）
let voicedMs = 0              // 当前段里**有效语音**累计时长（超过门限的帧）→ 用它判"够不够长"，避免边界抖动
let silenceMs = 0             // 当前段尾部已连续静音时长
let speaking = false
let noise = 0.005             // 噪声基线（自适应），初始给很小的值
let cooldownUntil = 0
let lastHitAt = 0
let awaitCmd = false          // 命中唤醒词后进入「等指令」态：此时采集的是**指令**，不再判唤醒词
let cmdDeadline = 0           // 「等指令」的截止时间（超时取消聆听态）
let pendingCmdText = ''       // 已识别但**还没发送**的指令（续话窗口内若用户接着说，就合并成一条）
let cmdFlushAt = 0            // 续话窗口的截止时间：到点才把 pendingCmdText 派发出去（= 自动发送）
let cmdFirstAt = 0            // 本轮"第一次识别出文字"的时刻（合并时不刷新）→ 用于 CMD_FORCE_SEND_MS 硬上限
let uploading = false         // 是否有上传正在飞行中（有的话不要派发，否则会把"接着说"的那段分开发送）
let uploadFn = null
let resultCb = null
let speakingFn = null         // 调用方注入的"小助手是否正在语音播报"判断（带估算兜底，见 voiceNotify.isSpeaking）

/**
 * 「小助手正在播报」判断：优先用注入实现（能兜底 speaking 状态卡死），没注入则退回浏览器原值。
 * 为什么要兜底：Chromium 上 `cancel()` 紧跟 `speak()` 偶发 speaking 不复位 → 采集被永久丢弃，
 * 表现为"喊了没反应 / 下一轮指令收不到"。
 */
function speechBusy () {
  try {
    if (speakingFn) return !!speakingFn()
    return typeof window !== 'undefined' && !!(window.speechSynthesis && window.speechSynthesis.speaking)
  } catch (e) {
    return false
  }
}

/** 唤醒词匹配：**复用 voiceWakeup.js 的同一套规则**（整句必须是「你好，小楷」；同音容错在那边统一维护） */
export function isWakeText (text) {
  return matchesWake(String(text || ''))
}

export function isServerWakeupRunning () {
  return running
}

/**
 * 手动（重新）进入「等指令」态：用于**语音确认**这类"小助手主动追问、等用户口头回答"的场景 ——
 * 下一句被识别到的内容会作为一条指令派发出去（EVENT_WAKEUP 的 detail.text）。
 * 返回 false = 当前没有在采集（麦克风没开），调用方必须降级为"显示界面让用户手动点"。
 * @param {number} [timeoutMs] 等待回答的时限（不传则用默认 15 秒）
 */
export function armVoiceCommand (timeoutMs) {
  if (!running) return false
  awaitCmd = true
  cmdDeadline = Date.now() + (timeoutMs > 0 ? timeoutMs : cmdWaitTimeoutMs)
  pendingCmdText = ''
  cmdFlushAt = 0
  cmdFirstAt = 0
  lastHitAt = 0        // 允许用户立刻开口（不等"命中后静默 2.5 秒"）
  cooldownUntil = 0
  speaking = false
  segFrames = []
  segMs = 0
  voicedMs = 0
  silenceMs = 0
  return true
}

/**
 * 启动服务端唤醒采集。
 * @param {object} opt
 * @param {(blob: Blob) => Promise<string>} opt.upload 上传音频并返回识别文本（由组件传入，复用既有上传实现）
 * @param {(r: {text: string, hit: boolean, hidden: boolean}) => void} [opt.onResult] 每次识别结果回调（供界面提示"唤醒命中"）
 * @param {() => boolean} [opt.isSpeaking] "小助手是否正在语音播报"判断（播报期丢弃采集用；不传则用浏览器原值）
 */
export async function startServerWakeup (opt = {}) {
  if (running) return { ok: true }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    return { ok: false, error: '当前浏览器不支持麦克风采集（getUserMedia）' }
  }
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) return { ok: false, error: '当前浏览器不支持音频采集（Web Audio）' }
  uploadFn = opt.upload || null
  resultCb = opt.onResult || null
  speakingFn = opt.isSpeaking || null
  if (!uploadFn) return { ok: false, error: '缺少上传实现（内部错误）' }
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    })
    let ctx
    try { ctx = new Ctx({ sampleRate: 16000 }) } catch (e) { ctx = new Ctx() }
    if (ctx.state === 'suspended') { try { await ctx.resume() } catch (e) { /* 忽略 */ } }
    audioCtx = ctx
    recent = []
    segFrames = []
    segMs = 0
    voicedMs = 0
    silenceMs = 0
    speaking = false
    noise = 0.005
    cooldownUntil = 0
    lastHitAt = 0
    awaitCmd = false
    cmdDeadline = 0
    pendingCmdText = ''
    cmdFlushAt = 0
    cmdFirstAt = 0
    source = ctx.createMediaStreamSource(stream)
    processor = ctx.createScriptProcessor(FRAME, 1, 1)
    processor.onaudioprocess = (e) => {
      try {
        onFrame(e.inputBuffer.getChannelData(0))
      } catch {
        // 单帧处理出错不能中断采集：吞掉继续（原自测面板的记录点已随面板删除）
      }
    }
    source.connect(processor)
    processor.connect(ctx.destination)   // Chrome 系需连到 destination 才会持续回调（不写输出，无回声）
    running = true
    return { ok: true }
  } catch (err) {
    await stopServerWakeup()
    return { ok: false, error: '无法访问麦克风：' + ((err && err.message) || err) }
  }
}

export function stopServerWakeup () {
  running = false
  speaking = false
  speakingFn = null
  awaitCmd = false
  cmdDeadline = 0
  pendingCmdText = ''
  cmdFlushAt = 0
  cmdFirstAt = 0
  recent = []
  segFrames = []
  segMs = 0
  voicedMs = 0
  silenceMs = 0
  try { if (processor) { processor.disconnect(); processor.onaudioprocess = null } } catch (e) { /* 忽略 */ }
  try { if (source) source.disconnect() } catch (e) { /* 忽略 */ }
  processor = null
  source = null
  if (audioCtx) { try { audioCtx.close() } catch (e) { /* 忽略 */ } audioCtx = null }
  if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null }
  return Promise.resolve()
}

/**
 * 每帧回调：自适应门限判"有人在说话" → **等这段说完（尾部静音）才上传**
 * 这样一次说话只上传一段（首轮实测的"一句话被切成多段"问题由此修掉）。
 */
function onFrame (data) {
  // 小助手正在**语音播报**时丢弃采集：否则它自己的应答（"小楷在，请说你的指令。"）会被麦克风听回来 →
  // 既浪费一次识别，又会把"听到自己说话"当成用户说的话（用户第四轮实测到过这条假数据）
  if (speechBusy()) {
    speaking = false
    segFrames = []
    segMs = 0
    voicedMs = 0
    silenceMs = 0
    recent = []
    return
  }
  const frame = new Float32Array(data)
  // 续话窗口到点：把（可能已合并多段的）指令派发出去 —— 这是"自动发送"的**唯一出口**
  // 不要在以下情况发：① 用户正在说话（新段落收集中）② 有识别还在飞行中 ③ 窗口还没到点
  // —— 否则会出现"接着说"的那段被拆成第二条消息（等于没说完就发）
  // 例外：超过 CMD_FORCE_SEND_MS 硬上限时**无条件发**（哪怕用户还在说）——
  //       否则"说完立刻接着说"会让 `speaking` 长期为 true 而永远发不出去（2026-09-18 实测踩坑）
  const hardDue = !!pendingCmdText && cmdFirstAt > 0 && Date.now() - cmdFirstAt >= CMD_FORCE_SEND_MS
  if (pendingCmdText && !uploading && ((Date.now() >= cmdFlushAt && !speaking) || hardDue)) {
    const t = pendingCmdText
    pendingCmdText = ''
    cmdFirstAt = 0
    awaitCmd = false
    window.dispatchEvent(new CustomEvent(EVENT_WAKEUP, { detail: { text: t } }))
  }
  // 「等指令」超时：唤醒后一直没人说话 → 结束本轮，但**不能静默**（用户 2026-10-21 要求：
  // 唤醒失败要明确答复"小楷没有听清楚"）。前端收到 missed=true 会语音告知并复原浮球。
  if (awaitCmd && !pendingCmdText && Date.now() > cmdDeadline) {
    awaitCmd = false
    window.dispatchEvent(new CustomEvent(EVENT_WAKEUP, {
      detail: { cancel: true, missed: true, reason: 'no-command' }
    }))
  }
  let sum = 0
  for (let i = 0; i < frame.length; i++) sum += frame[i] * frame[i]
  const rms = Math.sqrt(sum / frame.length)
  // 进入阈值（防噪声触发）+ 保持阈值（滞回，防止把句子中间较弱的字当静音而提前截断）
  const enterThreshold = Math.min(MAX_RMS_THRESHOLD, Math.max(MIN_RMS_FLOOR, noise * RMS_MULTIPLIER))
  const holdThreshold = enterThreshold * RELEASE_RATIO

  if (!speaking) {
    // 静默期：持续更新噪声基线（自适应环境），并保留最近几帧用于回补字头
    noise = noise * NOISE_ALPHA + rms * (1 - NOISE_ALPHA)
    recent.push(frame)
    if (recent.length > LEAD_FRAMES) recent.shift()
    // 「等指令」态下只受冷却约束 —— 应答播报已由 speechSynthesis 守护，不必再多等静默期（否则会吃掉用户开口的字）
    const cooled = Date.now() >= cooldownUntil && (awaitCmd || Date.now() - lastHitAt >= HIT_MUTE_MS)
    if (rms > enterThreshold && cooled) {
      speaking = true
      segFrames = recent.slice()          // 回补字头，避免"小楷"的"小"被切掉
      segMs = segFrames.length * FRAME_MS
      voicedMs = FRAME_MS                 // 触发这一帧本身就是语音
      silenceMs = 0
      recent = []
    }
    return
  }

  segFrames.push(frame)
  segMs += FRAME_MS
  if (rms > holdThreshold) {
    silenceMs = 0
    voicedMs += FRAME_MS                  // 用"保持阈值"判：句中较弱的字也算语音（不再低估有效语音）
  } else {
    silenceMs += FRAME_MS
  }

  // 「等指令」时用更宽松的切句参数：指令更长、允许句内停顿
  const silenceLimit = awaitCmd ? CMD_SILENCE_MS : SILENCE_MS
  const maxMs = awaitCmd ? CMD_MAX_MS : MAX_SPEECH_MS
  const ended = silenceMs >= silenceLimit || segMs >= maxMs
  if (!ended) return
  speaking = false
  cooldownUntil = Date.now() + COOLDOWN_MS
  // 去掉尾部那段静音，再决定这段够不够长
  const dropTail = Math.round(silenceMs / FRAME_MS)
  const seg = dropTail > 0 ? segFrames.slice(0, Math.max(0, segFrames.length - dropTail)) : segFrames.slice()
  const ms = seg.length * FRAME_MS
  const voiced = voicedMs
  const wasCmd = awaitCmd
  segFrames = []
  segMs = 0
  voicedMs = 0
  silenceMs = 0
  // 判"够不够长"用**有效语音时长**（不含回补字头）：唤醒词 500ms、指令 300ms
  // 太短 => 多半是噪声碎片，不上传（省服务器、也不浪费一次识别）
  if (voiced < (wasCmd ? CMD_MIN_VOICED_MS : MIN_SPEECH_MS)) return
  void flush(seg, ms, wasCmd)
}

/** 编码 WAV → 上传后端（复用自建 ASR）→ 匹配唤醒词 → 命中派发 EVENT_WAKEUP */
async function flush (seg, ms, wasCmd) {
  const total = seg.reduce((n, f) => n + f.length, 0)
  if (total === 0) return
  const pcm = new Float32Array(total)
  let off = 0
  for (const f of seg) { pcm.set(f, off); off += f.length }
  const blob = new Blob([encodeWav(pcm, (audioCtx && audioCtx.sampleRate) || 16000)], { type: 'audio/wav' })
  const hidden = !!document.hidden
  let text
  uploading = true                  // 上传飞行中：此时不派发待发送指令（否则"接着说"的那段会被拆成第二条消息）
  try {
    text = await uploadFn(blob)
  } catch (err) {
    uploading = false
    const msg = String((err && err.message) || err)
    if (resultCb) resultCb({ text: '', hit: false, hidden, ms, error: msg })
    return
  }
  uploading = false
  // ===== 分支一：当前处于「等指令」态 → 这段识别结果就是用户说的指令 =====
  if (wasCmd) {
    const t = String(text || '').trim()
    if (!t) {
      // 没识别出内容：**保持等指令**（不取消），继续等用户说
      if (resultCb) resultCb({ text: '', hit: false, hidden, ms, cmd: true })
      return
    }
    // 用户 2026-10-21 口径：唤醒词后面跟指令 **不算唤醒、也不发送这段指令**，必须分开说。
    if (containsWakePhrase(t) || mentionsWakeName(t)) {
      if (matchesWake(t)) {
        // 整句只是又喊了一遍唤醒词 → 重新应答一次，继续保持等指令
        awaitCmd = true
        cmdDeadline = Date.now() + cmdWaitTimeoutMs
        pendingCmdText = ''
        cmdFlushAt = 0
        cmdFirstAt = 0
        lastHitAt = Date.now()
        if (resultCb) resultCb({ text: t, hit: true, hidden, ms })
        window.dispatchEvent(new CustomEvent(EVENT_WAKEUP, { detail: { wakeOnly: true } }))
        return
      }
      // 唤醒词 + 指令混在一句 / 只喊了名字 → 不发送，明确提示"先喊唤醒词，等应答后再讲指令"，并结束本轮
      awaitCmd = false
      pendingCmdText = ''
      cmdFlushAt = 0
      cmdFirstAt = 0
      if (resultCb) resultCb({ text: t, hit: false, hidden, ms, cmd: true })
      window.dispatchEvent(new CustomEvent(EVENT_WAKEUP, {
        detail: { cancel: true, missed: true, reason: 'wake-with-command' }
      }))
      return
    }
    // 正常指令：**先不发送**，放进"续话窗口"（2 秒）—— 用户若接着说就合并成同一条，
    // 避免"话没说完就被提前发出去"；2 秒内没有后续语音就视为说完，直接发送处理
    pendingCmdText = pendingCmdText ? (pendingCmdText + ' ' + t) : t
    if (!cmdFirstAt) cmdFirstAt = Date.now()   // 记录"第一次识别出文字"的时刻（合并时不刷新，硬上限才有意义）
    // 句尾像是没说完（"的/了/和/帮/给…"）→ 续话窗口补 600 毫秒，避免把半句话发出去
    const tailChar = t.slice(-1)
    const notFinished = /[的了和帮给跟与把将就又还]/.test(tailChar)
    cmdFlushAt = Date.now() + CMD_MERGE_WAIT_MS + (notFinished ? CMD_TAIL_EXTEND_MS : 0)
    cmdDeadline = Date.now() + cmdWaitTimeoutMs              // 顺带把超时往后推，避免续话时被取消
    if (resultCb) resultCb({ text: t, hit: false, hidden, ms, cmd: true })
    return
  }

  // ===== 分支二：普通态 → 判定是不是一次合格唤醒（整句必须只有「你好，小楷」） =====
  const t = String(text || '').trim()
  const hit = isWakeText(t)
  if (hit) {
    lastHitAt = Date.now()          // 命中后静默 2.5 秒，避免一次喊话连发多次唤醒
    // 进入「等指令」态：接下来用户说的那句话就是指令（喊一句 → 应答 → 说指令 → 执行）
    awaitCmd = true
    cmdDeadline = Date.now() + cmdWaitTimeoutMs
  }
  if (resultCb) resultCb({ text: t, hit, hidden, ms })
  if (hit) {
    // 与浏览器唤醒走**同一个事件**：唤醒后的应答/聆听态/结果播报全部复用既有实现
    window.dispatchEvent(new CustomEvent(EVENT_WAKEUP, { detail: { wakeOnly: true } }))
  } else if (mentionsWakeName(t) || containsWakePhrase(t)) {
    // 用户确实在喊我，但没喊成完整唤醒词（只喊"小楷" / 唤醒词后面跟了指令）→ **不能静默**
    // 明确告知没听清楚或要求分开说，然后结束本轮（2026-10-21 用户要求）
    window.dispatchEvent(new CustomEvent(EVENT_WAKEUP, {
      detail: {
        cancel: true,
        missed: true,
        reason: containsWakePhrase(t) ? 'wake-with-command' : 'not-wake'
      }
    }))
  }
}
