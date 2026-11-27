<template>
  <el-dialog
    :model-value="visible"
    :title="tt('手写签名')"
    width="480px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="onVisibleChange"
    @opened="initCanvas"
  >
    <div class="sig-body">
      <div class="sig-action">
        <span class="sig-label">{{ tt('签名动作') }}</span>
        <span class="sig-value">{{ actionLabel }}</span>
      </div>
      <div v-if="targetDesc" class="sig-action">
        <span class="sig-label">{{ tt('签名对象') }}</span>
        <span class="sig-value">{{ targetDesc }}</span>
      </div>
      <div class="sig-tip">{{ tt('请在下方手写签名，签名人、时间与签名图将永久留痕') }}</div>
      <div class="sig-canvas-wrap">
        <canvas
          ref="canvasRef"
          class="sig-canvas"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointerleave="onPointerUp"
        ></canvas>
        <span v-if="!hasDrawn" class="sig-placeholder">{{ tt('在这里签名') }}</span>
      </div>
      <div class="sig-tools">
        <el-button size="small" @click="clearCanvas">{{ tt('清除重签') }}</el-button>
        <span class="sig-hint">{{ tt('支持鼠标与手指书写') }}</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="onVisibleChange(false)">{{ tt('取消') }}</el-button>
      <el-button type="primary" :loading="loading" @click="confirm">{{ tt('确认签名') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 通用手写签名弹窗（化妆品批记录）
 * 用法：<SignatureDialog v-model:visible="show" action-type="检验提交" target-desc="批次 XXX" @signed="onSigned" />
 * 口径（2026-11-06 用户口径）：只手写签名，不再输入登录密码；确认后把签名图交给调用方落库。
 * 说明：本组件只负责采集签名图，签名留痕在后端（signature_record 只增不改）。
 */
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { tt } from '@/i18n'

const props = defineProps({
  visible: { type: Boolean, default: false },
  actionType: { type: String, default: '' },
  targetDesc: { type: String, default: '' }
})
const emit = defineEmits(['update:visible', 'signed', 'cancel'])

const actionLabel = ref('')
const loading = ref(false)
/** 画布与绘制状态 */
const canvasRef = ref(null)
const hasDrawn = ref(false)
let ctx = null
let drawing = false
let lastX = 0
let lastY = 0

watch(
  () => props.actionType,
  v => { actionLabel.value = v || tt('手写签名') },
  { immediate: true }
)

watch(
  () => props.visible,
  v => { if (v) { loading.value = false } }
)

/** 弹窗打开后再初始化画布（弹窗未渲染时 canvas 宽高为 0） */
function initCanvas () {
  nextTick(() => {
    const cv = canvasRef.value
    if (!cv) return
    const ratio = window.devicePixelRatio || 1
    const rect = cv.getBoundingClientRect()
    const w = Math.max(320, Math.round(rect.width))
    const h = Math.max(160, Math.round(rect.height))
    cv.width = w * ratio
    cv.height = h * ratio
    ctx = cv.getContext('2d')
    ctx.scale(ratio, ratio)
    ctx.lineWidth = 2.4
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.strokeStyle = '#1f2937'
    hasDrawn.value = false
  })
}

function pointOf (e) {
  const cv = canvasRef.value
  const rect = cv.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}

function onPointerDown (e) {
  if (!ctx) return
  e.preventDefault()
  drawing = true
  const p = pointOf(e)
  lastX = p.x
  lastY = p.y
  ctx.beginPath()
  ctx.moveTo(lastX, lastY)
  // 单点也要留下一个墨点
  ctx.lineTo(lastX + 0.1, lastY + 0.1)
  ctx.stroke()
  hasDrawn.value = true
  if (canvasRef.value?.setPointerCapture) {
    try { canvasRef.value.setPointerCapture(e.pointerId) } catch (_) { /* 忽略 */ }
  }
}

function onPointerMove (e) {
  if (!drawing || !ctx) return
  e.preventDefault()
  const p = pointOf(e)
  ctx.beginPath()
  ctx.moveTo(lastX, lastY)
  ctx.lineTo(p.x, p.y)
  ctx.stroke()
  lastX = p.x
  lastY = p.y
  hasDrawn.value = true
}

function onPointerUp () {
  drawing = false
}

function clearCanvas () {
  const cv = canvasRef.value
  if (!cv || !ctx) return
  ctx.clearRect(0, 0, cv.width, cv.height)
  hasDrawn.value = false
}

function onVisibleChange (v) {
  emit('update:visible', v)
  if (!v) {
    drawing = false
    emit('cancel')
  }
}

function confirm () {
  if (!hasDrawn.value) {
    ElMessage.warning(tt('请先手写签名'))
    return
  }
  const signImage = canvasRef.value ? canvasRef.value.toDataURL('image/png') : ''
  if (!signImage) {
    ElMessage.warning(tt('签名图生成失败，请重新签名'))
    return
  }
  loading.value = true
  // 把签名图交给调用方，由调用方调对应业务接口（提交检验 / 复核 / 记录更正）
  emit('signed', { signImage, signMethod: '手写' })
  loading.value = false
  emit('update:visible', false)
}
</script>

<style scoped>
.sig-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sig-action {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.sig-label {
  color: var(--el-text-color-secondary);
  min-width: 72px;
}
.sig-value {
  color: var(--el-text-color-primary);
}
.sig-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.sig-canvas-wrap {
  position: relative;
  height: 180px;
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  background: #fafbfc;
  touch-action: none;
}
.sig-canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: crosshair;
}
.sig-placeholder {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: #c0c4cc;
  font-size: 14px;
  pointer-events: none;
}
.sig-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sig-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
