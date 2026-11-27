/**
 * 唤醒词匹配 + 语音偏好（**全项目唯一来源**）
 *
 * 2026-09-18 瘦身说明（用户要求："已经有本地语音了，那个浏览器的语音唤醒就去掉吧，代码全删掉"）：
 *   本模块原来包含整套「浏览器语音唤醒」—— Web Speech API（SpeechRecognition）+ 设备端语音包下载
 *   + 云端降级。实测结论是这条路走不通：① 云端识别把音频送浏览器厂商云，国内常连不通；
 *   ② 设备端语音包是浏览器自己的组件，网页无权安装、每台终端各一份，实测本机 Edge 153 对
 *   zh-CN/en-US **全部 unavailable**、install() 0ms 空操作。
 *   现已**整套删除**，识别统一走**服务器自建中文语音识别**（utils/serverWakeup.js + asr-service/），
 *   不依赖浏览器厂商的识别服务，也不需要给每台终端下载语音包。
 *
 * 本文件只保留两件事：
 *   1) 唤醒词匹配规则（服务器唤醒链路使用；改这里一处即全项目生效）
 *   2) 语音相关的本机偏好（⚡语音直发/🗣️语音需确认、🚀纯语音不打开界面）
 */

/** 唤醒事件：detail = { text, wakeOnly, cancel } */
export const EVENT_WAKEUP = 'erp:voice-wakeup'

/**
 * 唤醒词 = 「你好，小楷」（用户 2026-10-21 指定）。
 *
 * 口径（三条都写死）：
 *   1) **只认"整句就是唤醒词"**：唤醒词后面再跟指令（如「你好小楷，查一下库存」）
 *      不算唤醒，也不把后半句当指令发出去 —— 必须分两步：先喊唤醒词，听到应答后再讲指令。
 *   2) 同音容错保留：ASR 常把"楷"识别成"凯"、"你"识别成"您"，句首句尾允许标点。
 *   3) 只喊名字（"小楷""小凯"）不算唤醒；旧唤醒词「小楷助手」「小楷小楷」也不再算。
 */
const WAKE_EXACT_RE = /^[\s，,、。.!！?？]*(?:你好|您好)[\s，,、。.!！?？]*小[楷凯](?:呀|啊|哈)?[\s，,、。.!！?？]*$/
/** 文本里是否出现"小楷/小凯"（用于失败提示：用户确实在喊我，但没喊完整；刻意不含单独的"开/凯"避免误判） */
const WAKE_NAME_RE = /(?:小[楷凯]|[楷凯]助手)/
/** 文本里是否出现唤醒短语（用于识别"唤醒词+指令"的同句） */
const WAKE_PHRASE_RE = /(?:你好|您好)[\s，,、。.!！?？]*小[楷凯]/

/** 是否为一次合格的唤醒（整句必须只有唤醒词） */
export function matchesWake (text) {
  if (!text) return false
  return WAKE_EXACT_RE.test(String(text))
}

/** 文本里是否出现"小楷/小凯"（用户喊了名字但没喊完整时，要给出明确提示，不能静默） */
export function mentionsWakeName (text) {
  if (!text) return false
  return WAKE_NAME_RE.test(String(text))
}

/** 文本里是否含唤醒短语（"你好小楷，查库存"这类同句要拒绝并提示分开说） */
export function containsWakePhrase (text) {
  if (!text) return false
  return WAKE_PHRASE_RE.test(String(text))
}

// ===== 语音偏好（本机 localStorage，各存各的） =====
const LS_CONFIRM = 'erp.voiceWakeup.confirm'
const LS_SILENT = 'erp.voiceWakeup.silent'

function readFlag (key) {
  try { return localStorage.getItem(key) === '1' } catch (e) { return false }
}

function writeFlag (key, on) {
  try {
    if (on) localStorage.setItem(key, '1')
    else localStorage.removeItem(key)
  } catch (e) { /* 隐私模式：忽略 */ }
}

/**
 * 语音指令是否需要"二次确认"（默认关闭 = 说完直发）。
 * true  = 识别到的文本只填入输入框，用户确认后才发送
 * false = 识别到就自动发送（写操作仍由既有确认卡片兜底）
 */
export function isVoiceConfirmEnabled () {
  return readFlag(LS_CONFIRM)
}

/** 切换"语音需确认"偏好，返回切换后的值 */
export function toggleVoiceConfirm () {
  const next = !isVoiceConfirmEnabled()
  writeFlag(LS_CONFIRM, next)
  return next
}

/**
 * 「纯语音（不打开界面）」偏好：开启后语音指令**不打开抽屉**，执行完直接语音播报结果；
 * 遇到写操作确认时也不弹卡片，改为**语音问一句、用户口头答"确认/取消"**后直接执行（用户 2026-09-18 要求）。
 */
export function isVoiceSilentEnabled () {
  return readFlag(LS_SILENT)
}

/** 切换「纯语音」偏好，返回切换后的值 */
export function toggleVoiceSilent () {
  const next = !isVoiceSilentEnabled()
  writeFlag(LS_SILENT, next)
  return next
}

/* ===== 语音会话控制：结束语 / 取消语（本地判定，不进模型） =====
 * 依据《小楷语音交互设计》v1.0：
 *   ① 只有**整句**就是结束语才算（「没事了就帮我查下库存」照常执行）；
 *   ② 句中出现任何业务要素 → 一律不当结束语；
 *   ③ 有待确认时说「不用了」按取消处理（由调用方按状态判断，这里只给词表）。
 */
const END_PHRASES = new Set([
  '没事', '没事了', '没事儿', '算了', '不问了', '没什么', '就这个', '先这样',
  '等会再说', '待会儿再说', '等会儿再说', '知道了', '知道了谢谢', '谢谢', '谢谢了',
  '好的', '好', '行', '可以了', '就这样'
])
const CANCEL_PHRASES = new Set([
  '不用了', '不要了', '取消', '取消吧', '算了', '不做了', '不去', '不要', '撤销', '不执行'
])
/** 业务要素：出现任一项就不当结束语/取消语（避免把「不用查了，帮我建单」误判） */
const BUSINESS_RE = /(查|看|建|开单|单据|改|删|加|提交|审核|通过|驳回|作废|导出|导入|打印|库存|订单|客户|供应商|物料|产品|报表|设置|配置|权限|员工|仓库|金额|数量|单价|多少|什么|怎么|为什么|帮我|给我|把|统计)/
const MODAL_TAIL_RE = /[吧啊哦呀哈呢啦嘛]+$/

/** 归一化：去标点空白、去句尾语气词（保留「了」，因为「没事了」本身就是一种说法） */
function normalizePhrase (text) {
  return String(text || '')
    .replace(/[\s，,。.！!？?、；;：:~～…]/g, '')
    .replace(MODAL_TAIL_RE, '')
}

/** 是否为「结束本次语音对话」的整句（如「没事了」「算了」「知道了」） */
export function isVoiceEndPhrase (text) {
  const raw = String(text || '')
  if (!raw.trim()) return false
  if (BUSINESS_RE.test(raw)) return false
  return END_PHRASES.has(normalizePhrase(raw))
}

/** 是否为「取消当前待执行操作」的整句（如「不用了」「取消」「撤销」） */
export function isVoiceCancelPhrase (text) {
  const raw = String(text || '')
  if (!raw.trim()) return false
  if (BUSINESS_RE.test(raw)) return false
  return CANCEL_PHRASES.has(normalizePhrase(raw))
}
