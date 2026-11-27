/**
 * 小楷助手「展示与播报」偏好（模块级共享单例）。
 *
 * 背景（2026-09-19）：偏好原先定义在 AiAssistant.vue 的 ⚙️ 设置弹窗里；
 * 设置中心新增「AI 助手设置」页后，两处入口必须共用同一份状态 —— 在任一处切换，
 * 立刻对另一处与小楷实际调用（context.displayPrefs / 播报字数）生效。
 *
 * 口径：
 *  - 单据标识方式 / 播报字数：localStorage 持久化（键 ai.billLabelStyle / ai.speakLimit）
 *  - 播报语速：本机偏好，存在 utils/voiceNotify（0.8 慢 / 1.0 标准 / 1.2 稍快 / 1.5 快）
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'
import { scopedGet, scopedSet } from '@/utils/userScopedStorage'
import { getSpeakRate, setSpeakRate, getVoiceName, setVoiceName, listVoices } from '@/utils/voiceNotify'

// 单据标识方式：code=单号 / name=客户名 / both=单号+客户名
// 随 agent 请求下发后端（context.displayPrefs），确认卡片、执行结果、播报统一同口径
// 存储按「用户 + 公司」隔离：同一台电脑换用户/切公司不得继承别人的 AI 偏好
export const billLabelStyle = ref(scopedGet('ai.billLabelStyle', '') || 'code')
// 语音播报一句话最多念多少字（voiceNotify.buildSpeakSummary 的 limit）
export const speakLimit = ref(Number(scopedGet('ai.speakLimit', '')) || 40)
// 语音播报语速（本机偏好，存在 voiceNotify 模块）
export const speakRate = ref(getSpeakRate())
// 语音播报音色：huayan=服务器 Piper 自然女声；auto=浏览器内置（慧慧/康康/瑶瑶等）
// 本机个人偏好，随 voiceNotify 模块自己持久化（erp.voiceNotify.voiceName）
export const voiceName = ref(getVoiceName())

/** 身份/公司变化时按新作用域重新装载 AI 展示偏好（由 utils/sessionCacheReset.js 调用） */
export function reloadAiDisplayPrefsForScope() {
  billLabelStyle.value = scopedGet('ai.billLabelStyle', '') || 'code'
  speakLimit.value = Number(scopedGet('ai.speakLimit', '')) || 40
}

export const billLabelStyleText = (v) => (v === 'name' ? tt('客户名')
  : (v === 'both' ? tt('单号+客户名') : tt('单号')))

export function onBillLabelStyleChange(v) {
  scopedSet('ai.billLabelStyle', v || 'code')
  ElMessage.success(tt('已保存：确认卡片与播报里用「') + billLabelStyleText(v) + tt('」称呼单据'))
}

export function onSpeakLimitChange(v) {
  scopedSet('ai.speakLimit', String(v || 40))
  ElMessage.success(tt('已保存：语音播报最长 ') + (v || 40) + tt(' 字'))
}

/** 语速档位文案（下拉选项与提示共用） */
export const speakRateText = (v) => (v === 0.8 ? tt('慢（听得清）')
  : (v === 1.2 ? tt('稍快') : (v === 1.5 ? tt('快') : tt('标准（默认）'))))

/** 保存语速：落到 voiceNotify（本机 localStorage），立即生效，失败自动回落 1.0 后按真实值回显 */
export function onSpeakRateChange(v) {
  const actual = setSpeakRate(v)
  speakRate.value = actual
  ElMessage.success(tt('已保存：播报语速 ') + actual + '（' + speakRateText(actual) + tt('）'))
}

/** 保存播报音色：落到 voiceNotify，立即生效（下次播报自动走新音色） */
export function onVoiceNameChange(v) {
  const actual = setVoiceName(v)
  voiceName.value = actual
  const voices = listVoices()
  const label = (voices.find(x => x.value === actual) || {}).label || actual
  ElMessage.success(tt('已保存：播报语音 → ') + label)
}

/** 试听当前音色：只走服务器离线自然女声（单通道，不再退回系统音色） */
export async function previewVoice() {
  const text = '你好，我是小楷，很高兴为你服务。'
  try {
    const token = localStorage.getItem('token')
    const r = await fetch('/api/ai/tts/synthesize', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ text, voiceName: 'huayan' })
    })
    if (!r.ok) throw new Error('http ' + r.status)
    const blob = await r.blob()
    if (!blob || blob.size < 1000) throw new Error('empty wav')
    const url = URL.createObjectURL(blob)
    const a = new Audio(url)
    a.onended = () => { try { URL.revokeObjectURL(url) } catch (e) { /* 忽略 */ } }
    await a.play()
  } catch (e) {
    ElMessage.error(tt('试听失败：语音服务不可用，请检查服务器语音服务'))
  }
}

/** 可用语音清单（下拉直接用） */
export const voiceList = listVoices()

export function useAiDisplayPrefs() {
  return {
    billLabelStyle,
    speakLimit,
    speakRate,
    voiceName,
    voiceList,
    billLabelStyleText,
    onBillLabelStyleChange,
    onSpeakLimitChange,
    speakRateText,
    onSpeakRateChange,
    onVoiceNameChange,
    previewVoice
  }
}
