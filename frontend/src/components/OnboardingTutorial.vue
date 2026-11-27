<template>
  <!-- 遮罩层：4块拼接，中间镂空高亮区域 -->
  <template v-if="visible">
    <div class="onboarding-overlay-top" :style="{ height: rect.top + 'px' }" @click="onMaskClick"></div>
    <div class="onboarding-overlay-bottom" :style="{ top: (rect.bottom) + 'px' }" @click="onMaskClick"></div>
    <div class="onboarding-overlay-left" :style="{ top: rect.top + 'px', height: (rect.bottom - rect.top) + 'px', width: rect.left + 'px' }" @click="onMaskClick"></div>
    <div class="onboarding-overlay-right" :style="{ top: rect.top + 'px', height: (rect.bottom - rect.top) + 'px', left: rect.right + 'px' }" @click="onMaskClick"></div>

    <!-- 高亮边框 -->
    <div class="onboarding-highlight-box" :style="highlightBoxStyle">
      <span v-if="stepBadge" class="onboarding-step-badge">{{ stepBadge }}</span>
    </div>

    <!-- 提示气泡 -->
    <div ref="tooltipRef" class="onboarding-tooltip" :style="tooltipStyle" :class="'tip-' + effectivePosition">
      <div class="tip-arrow" v-if="effectivePosition !== 'center'"></div>
      <div class="tip-header">
        <span class="tip-step">{{ currentStep + 1 }} / {{ steps.length }}</span>
        <span class="tip-title">{{ currentStepData.title }}</span>
        <button class="tip-skip" @click="skip">{{ $tt("跳过教程 &times;") }}</button>
      </div>
      <div class="tip-body" v-html="currentStepData.description"></div>

      <!-- 模拟交互区域 -->
      <div v-if="currentStepData.interaction" class="tip-interaction">
        <component
          v-if="currentStepData.interaction.type === 'select'"
          :is="'el-select'"
          v-model="interactionValue"
          :placeholder="currentStepData.interaction.placeholder || '请选择'"
          :popper-class="'tutorial-select-popper'"
          style="width: 100%"
          @change="onInteractionComplete"
        >
          <el-option
            v-for="opt in currentStepData.interaction.options"
            :key="opt.value"
            :label="$tf(opt.label)"
            :value="opt.value"
          />
        </component>
        <component
          v-else-if="currentStepData.interaction.type === 'input'"
          :is="'el-input'"
          v-model="interactionValue"
          :placeholder="currentStepData.interaction.placeholder || '请输入'"
          @input="onInteractionInput"
        />
        <div v-else-if="currentStepData.interaction.type === 'button'" class="interaction-btn-wrap">
          <el-button
            type="primary"
            v-permission="perm('dlgConfirm')"
            @click="onInteractionComplete"
          >{{ currentStepData.interaction.label || '点击演示' }}</el-button>
        </div>
        <p v-if="interactionDone" class="interaction-hint">
          <el-icon color="#67c23a"><CircleCheckFilled /></el-icon>
          {{ currentStepData.interaction.successText || '操作成功！' }}
        </p>
      </div>

      <div class="tip-actions">
        <el-button v-if="currentStep > 0" size="small" v-permission="perm('dlgCancel')" @click="prev">{{ $tt("上一步") }}</el-button>
        <el-button v-if="currentStep < steps.length - 1" type="primary" size="small" v-permission="perm('dlgConfirm')" @click="next" :disabled="needInteraction && !interactionDone">
          下一步 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
        <el-button v-if="currentStep === steps.length - 1" type="success" size="small" v-permission="perm('dlgConfirm')" @click="finish">
          完成教程 <el-icon class="el-icon--right"><CircleCheckFilled /></el-icon>
        </el-button>
      </div>

      <!-- 进度条 -->
      <div class="tip-progress">
        <div class="tip-progress-bar" :style="{ width: ((currentStep + 1) / steps.length * 100) + '%' }"></div>
      </div>
    </div>
  </template>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { CircleCheckFilled, ArrowRight } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const props = defineProps({
  steps: { type: Array, default: () => [] },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'finish', 'skip'])

const currentStep = ref(0)
const interactionValue = ref('')
const interactionDone = ref(false)
const targetEl = ref(null)
const tooltipRef = ref(null)
const tooltipHeight = ref(0)
const finalPosition = ref('bottom')
const tooltipPosition = computed(() => currentStepData.value.position || 'bottom')

// 目标元素的位置信息
const rect = reactive({ top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 })

// 当前步骤数据
const currentStepData = computed(() => props.steps[currentStep.value] || {})

// 是否需要交互才能继续
const needInteraction = computed(() => !!currentStepData.value.interaction)

// 步骤序号徽章
const stepBadge = computed(() => needInteraction.value ? '' : (currentStep.value + 1))

// 气泡位置（考虑回退后的实际位置）
const effectivePosition = computed(() => finalPosition.value)

// 高亮框样式
const highlightBoxStyle = computed(() => ({
  top: (rect.top - 4) + 'px',
  left: (rect.left - 4) + 'px',
  width: (rect.width + 8) + 'px',
  height: (rect.height + 8) + 'px'
}))

// ========== 气泡尺寸常量 ==========
const TOOLTIP_WIDTH = 380
const MARGIN = 12
// 头部(约56) + 交互区(约100) + 操作区(约60) + 进度条(约4) ≈ 固定区块 220px
const TOOLTIP_FIXED_H = 220
// 最小气泡高度 = 固定区 + body 最小 80px
const TOOLTIP_MIN_H = TOOLTIP_FIXED_H + 80

// ========== 气泡定位：翻转 + 边界夹取 + 超长卡片贴边 ==========
// 硬性约束：气泡的 top∈[MARGIN, vh-tipH-MARGIN]，left∈[MARGIN, vw-WIDTH-MARGIN]
// 优先级：期望位置 → 翻转 → 直接粘到视口边界（允许和高亮元素重叠，也不能超出）
const tooltipStyle = computed(() => {
  const vw = window.innerWidth
  const vh = window.innerHeight
  const MAX_H = Math.max(TOOLTIP_MIN_H, vh - MARGIN * 2)
  let tipH = tooltipHeight.value > 0 ? Math.min(tooltipHeight.value, MAX_H) : Math.min(360, MAX_H)

  if (tooltipPosition.value === 'center') {
    finalPosition.value = 'center'
    return {
      top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
      width: TOOLTIP_WIDTH + 'px', maxHeight: MAX_H + 'px'
    }
  }

  const desiredPos = currentStepData.value.position || 'bottom'

  // Step 1：按期望位置计算初始坐标
  let top, left, pos = desiredPos
  if (pos === 'top')          { top = rect.top - tipH - MARGIN;  left = rect.left }
  else if (pos === 'bottom')  { top = rect.bottom + MARGIN;      left = rect.left }
  else if (pos === 'left')    { top = rect.top;                  left = rect.left - TOOLTIP_WIDTH - MARGIN }
  else /* right */            { top = rect.top;                  left = rect.right + MARGIN }

  // Step 2：翻转（如果指定方向空间不够）
  if ((pos === 'top')    && (top < MARGIN))                       { top = rect.bottom + MARGIN;                 pos = 'bottom' }
  if ((pos === 'bottom') && (top + tipH > vh - MARGIN))           { top = rect.top - tipH - MARGIN;             pos = 'top' }
  if ((pos === 'left')   && (left < MARGIN))                      { left = rect.right + MARGIN;                 pos = 'right' }
  if ((pos === 'right')  && (left + TOOLTIP_WIDTH > vw - MARGIN)) { left = rect.left - TOOLTIP_WIDTH - MARGIN;  pos = 'left' }

  // Step 3：超长卡片 (height > 视口 70%) → 气泡直接贴视口顶部，不跟随高亮位置
  if ((pos === 'top' || pos === 'bottom') && rect.height > vh * 0.7) {
    top = MARGIN
    pos = 'bottom'
  }

  // Step 4：硬性夹取 — 任何情况气泡都必须完全在视口内
  const minTop = MARGIN
  const maxTop = vh - tipH - MARGIN
  top = Math.max(minTop, Math.min(top, maxTop < minTop ? minTop : maxTop))
  left = Math.max(MARGIN, Math.min(left, vw - TOOLTIP_WIDTH - MARGIN))

  finalPosition.value = pos
  return {
    top: top + 'px',
    left: left + 'px',
    width: TOOLTIP_WIDTH + 'px',
    maxHeight: MAX_H + 'px'
  }
})

// 查找目标元素
function findTargetEl(selector) {
  if (!selector) return null
  let el = document.querySelector(selector)
  if (el) return el
  if (selector.startsWith('text:')) {
    const text = selector.slice(5)
    const allEls = document.querySelectorAll('button, .el-button, a, span, div')
    for (const e of allEls) {
      if (e.textContent.trim() === text || e.textContent.includes(text)) return e
    }
  }
  return null
}

// ========== 折叠展开工具 ==========
// 第 10/15 步常见问题：目标元素的祖先有 .collapsible-card.collapsed，card-body 是 v-show=display:none
// → getBoundingClientRect 全是 0 → 红框就出不来
// 解法：从目标元素向上遍历，每遇到一个 .collapsible-card.collapsed，就点它的 .card-header 展开，然后等待动画结束（350ms）
function expandCollapsedAncestors(el) {
  if (!el) return Promise.resolve(false)
  const toOpen = []
  let cur = el
  while (cur && cur !== document.body) {
    if (cur.classList && cur.classList.contains('collapsible-card') && cur.classList.contains('collapsed')) {
      toOpen.push(cur)
    }
    cur = cur.parentElement
  }
  if (toOpen.length === 0) return Promise.resolve(false)
  toOpen.forEach(card => {
    const header = card.querySelector(':scope > .card-header')
    if (header) header.click()
  })
  // 350ms 是折叠卡片 v-show + transition 的常见时长
  return new Promise(resolve => setTimeout(() => resolve(true), 360))
}

// 把目标元素滚动到可视区（如果需要），返回 Promise
function scrollIfNeeded(el, desiredPos, r) {
  const vw = window.innerWidth
  const vh = window.innerHeight
  const reserveTop = TOOLTIP_MIN_H + MARGIN * 3
  const reserveBottom = TOOLTIP_MIN_H + MARGIN * 3
  const reserveSide = TOOLTIP_WIDTH + MARGIN * 2

  let need = false
  const opts = { behavior: 'smooth', block: 'center', inline: 'nearest' }
  if ((desiredPos === 'bottom') && (vh - r.bottom) < reserveBottom) need = true
  if ((desiredPos === 'top') && r.top < reserveTop) need = true
  if ((desiredPos === 'right') && (vw - r.right) < reserveSide) need = true
  if ((desiredPos === 'left') && r.left < reserveSide) need = true
  if (r.top < -4 || r.bottom > vh + 4) need = true

  if (!need) return Promise.resolve()
  if (r.height > vh * 0.7) opts.block = 'start'
  else if (desiredPos === 'top') opts.block = 'end'
  else if (desiredPos === 'bottom') opts.block = 'start'
  el.scrollIntoView(opts)
  return new Promise(resolve => setTimeout(resolve, 450))
}

// 更新目标元素位置（先展开折叠祖先 → 再滚 → 最后写 rect）
function updateRect() {
  const step = currentStepData.value
  if (!step.selector && tooltipPosition.value === 'center') {
    rect.top = 0; rect.left = 0; rect.right = 0; rect.bottom = 0; rect.width = 0; rect.height = 0
    nextTick(() => measureAndUpdate())
    return
  }
  const el = findTargetEl(step.selector)
  targetEl.value = el
  if (!el) {
    rect.top = 0; rect.left = 0; rect.right = 0; rect.bottom = 0; rect.width = 0; rect.height = 0
    return
  }

  const desiredPos = step.position || 'bottom'

  // Phase 1：展开所有折叠祖先（第 15 步 .audit-flow 一般在这里被展开）
  expandCollapsedAncestors(el).then(() => {
    // Phase 2：展开后再拿一次 rect（因为父级 display 从 none → block）
    let r = el.getBoundingClientRect()
    // Phase 3：滚到可视区内合适位置，给气泡留空间
    return scrollIfNeeded(el, desiredPos, r)
  }).then(() => {
    // Phase 4：最终写入 rect，随后测量气泡
    const r2 = el.getBoundingClientRect()
    rect.top = r2.top
    rect.left = r2.left
    rect.right = r2.right
    rect.bottom = r2.bottom
    rect.width = r2.width
    rect.height = r2.height
    nextTick(() => {
      measureTooltipHeight()
      setTimeout(() => measureTooltipHeight(), 60)
    })
  })
}

// 测量气泡实际高度
function measureTooltipHeight() {
  if (tooltipRef.value) {
    tooltipHeight.value = tooltipRef.value.offsetHeight
  }
}

// 测量并更新位置
function measureAndUpdate() {
  measureTooltipHeight()
  // tooltipStyle 是 computed，依赖 tooltipHeight 会自动重算
}

// 下一步
function next() {
  if (needInteraction.value && !interactionDone.value) return
  if (currentStep.value < props.steps.length - 1) {
    currentStep.value++
    interactionValue.value = ''
    interactionDone.value = false
    nextTick(() => {
      measureTooltipHeight()
      updateRect()
    })
  }
}

// 上一步
function prev() {
  if (currentStep.value > 0) {
    currentStep.value--
    interactionValue.value = ''
    interactionDone.value = false
    nextTick(() => {
      measureTooltipHeight()
      updateRect()
    })
  }
}

// 跳过
function skip() {
  emit('update:visible', false)
  emit('skip')
  resetTutorial()
}

// 完成
function finish() {
  emit('update:visible', false)
  emit('finish')
  resetTutorial()
}

// 重置
function resetTutorial() {
  currentStep.value = 0
  interactionValue.value = ''
  interactionDone.value = false
  tooltipHeight.value = 0
  finalPosition.value = 'bottom'
}

// 交互完成
function onInteractionComplete() {
  interactionDone.value = true
  const step = currentStepData.value
  // 若步骤里声明了 interaction.inject，则 dispatch 全局事件，让 Edit 页填充演示数据
  // 例：第12步点"模拟点击添加明细"按钮 → inject: 'demo-items'
  if (step && step.interaction && step.interaction.inject) {
    try {
      const evt = new CustomEvent('onboarding:interaction-inject', {
        detail: { inject: step.interaction.inject, stepIndex: currentStep.value, stepKey: step.key || null }
      })
      window.dispatchEvent(evt)
    } catch (e) {
      console.warn('[onboarding] dispatch inject event failed:', e)
    }
  }
}

function onInteractionInput(val) {
  if (val && val.length >= 1) {
    interactionDone.value = true
  }
}

// 监听 interactionValue 变化：选择任意选项后自动标记交互完成
watch(interactionValue, (newVal) => {
  if (newVal !== '' && newVal !== null && newVal !== undefined) {
    interactionDone.value = true
  }
})

// 点击遮罩（不关闭，防止误操作）
function onMaskClick() {
  // 不做任何操作，用户必须点击下一步或跳过
}

// 窗口resize时重新定位
function onResize() {
  nextTick(() => {
    measureTooltipHeight()
    updateRect()
  })
}

// 监听visible变化
watch(() => props.visible, (val) => {
  if (val) {
    currentStep.value = 0
    interactionValue.value = ''
    interactionDone.value = false
    tooltipHeight.value = 0
    finalPosition.value = 'bottom'
    nextTick(() => {
      measureTooltipHeight()
      updateRect()
    })
  }
})

// 监听步骤变化
watch(currentStep, () => {
  nextTick(() => {
    measureTooltipHeight()
    updateRect()
  })
})

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})

// 暴露方法给父组件
defineExpose({ next, prev, skip, finish })
</script>

<style scoped>
/* 遮罩层 - 4块拼接 */
.onboarding-overlay-top,
.onboarding-overlay-bottom,
.onboarding-overlay-left,
.onboarding-overlay-right {
  position: fixed;
  z-index: 9998;
  background: rgba(0, 0, 0, 0.55);
  transition: all 0.3s ease;
}

.onboarding-overlay-top {
  top: 0; left: 0; width: 100%;
}
.onboarding-overlay-bottom {
  left: 0; width: 100%; bottom: 0;
}
.onboarding-overlay-left {
  left: 0;
}
.onboarding-overlay-right {
}

/* 高亮边框框 */
.onboarding-highlight-box {
  position: fixed;
  z-index: 9999;
  border: 3px solid var(--el-color-primary, #409eff);
  border-radius: 8px;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0);
  pointer-events: none;
  animation: pulse-highlight 2s ease-in-out infinite;
  transition: all 0.3s ease;
}

@keyframes pulse-highlight {
  0%, 100% { box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.3); }
  50% { box-shadow: 0 0 20px 4px rgba(64, 158, 255, 0.5); }
}

/* 步骤序号徽章 */
.onboarding-step-badge {
  position: absolute;
  top: -12px;
  right: -12px;
  width: 28px;
  height: 28px;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

/* 提示气泡 */
.onboarding-tooltip {
  position: fixed;
  z-index: 10000;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  padding: 0;
  overflow: hidden;
  transition: all 0.3s ease;
  animation: tip-fade-in 0.3s ease;
  display: flex;
  flex-direction: column;
  /* 最终高度 + 头部/操作区固定不滚 + body内部滚动，由外层 maxHeight 限制到视口内 */
  max-height: calc(100vh - 24px);
  min-height: 160px;
}

@keyframes tip-fade-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.tip-center {
  transform: translate(-50%, -50%) !important;
}

/* 气泡箭头 */
.tip-arrow {
  position: absolute;
  width: 0;
  height: 0;
  border: 8px solid transparent;
}

.tip-bottom .tip-arrow {
  top: -16px;
  left: 24px;
  border-bottom-color: var(--el-color-primary, #409eff);
}

.tip-top .tip-arrow {
  bottom: -16px;
  left: 24px;
  border-top-color: var(--el-color-primary, #409eff);
}

.tip-left .tip-arrow {
  right: -16px;
  top: 24px;
  border-left-color: var(--el-color-primary, #409eff);
}

.tip-right .tip-arrow {
  left: -16px;
  top: 24px;
  border-right-color: var(--el-color-primary, #409eff);
}

/* 气泡头部 */
.tip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, var(--el-color-primary, #409eff), var(--el-color-primary-light-3, #79bbff));
  color: #fff;
  flex-shrink: 0;
}

.tip-step {
  background: rgba(255, 255, 255, 0.25);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.tip-title {
  font-size: 15px;
  font-weight: 600;
  flex: 1;
}

.tip-skip {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: #fff;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s;
  white-space: nowrap;
}

.tip-skip:hover {
  background: rgba(255, 255, 255, 0.35);
}

/* 气泡内容 - 允许滚动（核心：内容高度溢出时只滚动 tip-body）*/
.tip-body {
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: #333;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1 1 auto;
  min-height: 0;
  word-break: break-word;
  white-space: normal;
}

.tip-body :deep(strong) {
  color: var(--el-color-primary, #409eff);
}
.tip-body ul { margin: 6px 0; }
.tip-body li { margin: 2px 0; line-height: 1.7; }

/* 交互区域：紧跟在 tip-body 下方，不参与滚动 */
.tip-interaction {
  padding: 0 16px 12px;
  border-top: 1px dashed #f0f0f0;
  padding-top: 10px;
  margin-top: 2px;
  flex-shrink: 0;
}

.interaction-btn-wrap {
  display: flex;
  justify-content: center;
}

.interaction-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  color: #67c23a;
  font-size: 13px;
}

/* 操作按钮 - 固定在底部，绝对不滚出视口 */
.tip-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
  background: #fafafa;
}

/* 进度条 */
.tip-progress {
  height: 4px;
  background: #f0f0f0;
  overflow: hidden;
  flex-shrink: 0;
}

.tip-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--el-color-primary, #409eff), var(--el-color-primary-light-3, #79bbff));
  transition: width 0.3s ease;
  border-radius: 2px;
}
</style>

<!-- 全局样式：确保教程内 el-select 弹出层 z-index 高于遮罩层(9998)和气泡(10000) -->
<style>
.tutorial-select-popper {
  z-index: 10001 !important;
}
</style>
