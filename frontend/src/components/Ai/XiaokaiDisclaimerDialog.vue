<!-- @feature FEAT-AI-DISCLAIMER -->
<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="760px"
    top="6vh"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="true"
    class="xiaokai-disclaimer-dialog"
    @close="onDialogClose"
  >
    <!-- ① 首屏：显著提示 + 核心条款摘要（不替代全文） -->
    <div v-if="!showFull" class="dk-body">
      <div class="dk-alert">
        <div class="dk-alert-tag">重要提示</div>
        <div class="dk-alert-txt">
          本协议含<b>免除或限制提供方责任</b>的条款（第七条 数据与第三方模型服务、第八条 数据安全与泄露免责、
          第十一条 责任限制、第十二条 使用方赔偿义务）及<b>争议管辖约定</b>（第十六条），已加粗提示，请务必阅读。
        </div>
      </div>

      <div v-for="s in DISCLAIMER_SUMMARY" :key="s.title" class="dk-item">
        <div class="dk-item-t">{{ s.title }}</div>
        <div class="dk-item-b">{{ s.text }}</div>
      </div>

      <el-button class="dk-open-full" type="primary" plain v-permission="perm('view')" @click="openFull">
        📄 查看完整协议全文（共 {{ DISCLAIMER_CLAUSES.length }} 条 · 滚动到正文底部才能勾选）
      </el-button>
      <div class="dk-sub">
        版本 {{ DISCLAIMER_VERSION }} · 更新日期 {{ DISCLAIMER_UPDATED_AT }}
      </div>
    </div>

    <!-- ② 全文阅读器：滚到底解锁勾选 -->
    <div v-else class="dk-body">
      <div class="dk-reader-head">
        <span class="dk-reader-title">完整协议全文</span>
        <el-button link type="primary" size="small" v-permission="perm('dlgCancel')" @click="backToSummary">← 返回摘要</el-button>
      </div>
      <div ref="scrollBox" class="dk-scroll" @scroll="onScroll">
        <div v-for="c in DISCLAIMER_CLAUSES" :key="c.no" class="dk-clause">
          <div class="dk-clause-t" :class="{ key: c.key }">
            第{{ c.no }}条 {{ c.title }}
            <span v-if="c.key" class="dk-key-tag">核心条款</span>
          </div>
          <div class="dk-clause-b" :class="{ key: c.key }">{{ c.text }}</div>
        </div>
        <div class="dk-end">— 正文结束 —</div>
      </div>
      <div class="dk-read-tip" :class="{ done: readToEnd }">
        <template v-if="readToEnd">✓ 已滚动到正文底部，勾选框已解锁</template>
        <template v-else>请继续向下滚动阅读剩余条款，阅读到正文底部后勾选框才会解锁</template>
      </div>
    </div>

    <template #footer>
      <div class="dk-footer">
        <el-checkbox v-model="checked" :disabled="!readToEnd" class="dk-chk">
          {{ DISCLAIMER_CHECKBOX_TEXT }}
        </el-checkbox>
        <div class="dk-btns">
          <el-button v-permission="perm('dlgCancel')" @click="onDecline">不同意</el-button>
          <el-button type="primary" :disabled="!checked" v-permission="perm('dlgConfirm')" :loading="saving" @click="onAccept">
            同意并开始使用
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 小楷助手《AI 服务使用协议与免责声明》首次使用弹窗（闸门）。
 *
 * 行为口径（用户 2026-09-19 确认）：
 *  1. 双层：首屏摘要（显著提示）→ 点"查看完整协议全文"进入 18 条全文阅读器；
 *  2. 滚动到正文底部 → 勾选框解锁 → 勾选后"同意并开始使用"才可点（scrollwrap，证据力最强）；
 *  3. 点 ✕ 或"不同意" → 关闭弹窗（父组件同时关闭小楷抽屉），浮球保留，下次打开仍弹；
 *  4. 勾选成功 → 写入后端账号级记录（一次永久生效，换设备/重登不再弹）。
 */
import { ref, computed, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { acceptDisclaimer } from '@/api/ai/disclaimer'
import {
  DISCLAIMER_VERSION,
  DISCLAIMER_TITLE,
  DISCLAIMER_UPDATED_AT,
  DISCLAIMER_SUMMARY,
  DISCLAIMER_CLAUSES,
  DISCLAIMER_CHECKBOX_TEXT
} from '@/constants/xiaokaiDisclaimer'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:ai-disclaimer')


const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'accepted'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const dialogTitle = computed(() => `🤖 ${DISCLAIMER_TITLE} v${DISCLAIMER_VERSION}`)

const showFull = ref(false)
const scrollBox = ref(null)
const readToEnd = ref(false)
const checked = ref(false)
const saving = ref(false)

/** 每次打开都重置到首屏（未同意 → 下次打开还是这个界面） */
watch(() => props.modelValue, (v) => {
  if (v) {
    showFull.value = false
    readToEnd.value = false
    checked.value = false
    saving.value = false
  }
})

function openFull() {
  showFull.value = true
  nextTick(() => checkScrollState())
}

function backToSummary() {
  showFull.value = false
}

/** 内容不足一屏时（大屏/短文本）直接视为已读完，避免用户"滚不动"被卡住 */
function checkScrollState() {
  const el = scrollBox.value
  if (!el) return
  if (el.scrollHeight - el.clientHeight <= 16) {
    readToEnd.value = true
  }
}

function onScroll() {
  const el = scrollBox.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 16) {
    readToEnd.value = true
  }
}

function onDialogClose() {
  // ✕ 关闭 = 未同意：父组件会据此关闭小楷抽屉
  emit('update:modelValue', false)
}

function onDecline() {
  visible.value = false
}

async function onAccept() {
  if (!checked.value) return
  saving.value = true
  try {
    const res = await acceptDisclaimer(DISCLAIMER_VERSION)
    const acceptedAt = res && res.acceptedAt ? res.acceptedAt : ''
    ElMessage.success('已记录你的同意' + (acceptedAt ? '（' + acceptedAt + '）' : ''))
    emit('accepted', { version: DISCLAIMER_VERSION, acceptedAt })
    visible.value = false
  } catch (e) {
    // 写入失败不放行：不关闭弹窗，用户可重试（宁可多弹，不可漏放）
    ElMessage.error('同意记录保存失败，请重试；未保存成功前无法使用小楷')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dk-body { display: flex; flex-direction: column; gap: 12px; }

.dk-alert { border: 1px solid #f56c6c; border-radius: 8px; padding: 10px 12px; }
.dk-alert-tag { color: #f56c6c; font-weight: 600; font-size: 12px; margin-bottom: 4px; }
.dk-alert-txt { font-size: 13px; line-height: 20px; color: #303133; }

.dk-item-t { font-weight: 600; font-size: 14px; margin-bottom: 2px; }
.dk-item-b { font-size: 12px; line-height: 18px; color: #909399; }

.dk-open-full { align-self: flex-start; }
.dk-sub { font-size: 12px; color: #a8abb2; }

.dk-reader-head { display: flex; align-items: center; justify-content: space-between; }
.dk-reader-title { font-weight: 600; font-size: 14px; }

.dk-scroll { max-height: 46vh; overflow-y: auto; border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px 16px; }
.dk-clause { margin-bottom: 12px; }
.dk-clause-t { font-size: 14px; font-weight: 500; margin-bottom: 2px; }
.dk-clause-t.key { font-weight: 700; color: #c45656; }
.dk-clause-b { font-size: 13px; line-height: 20px; color: #606266; }
.dk-clause-b.key { font-weight: 600; color: #303133; }
.dk-key-tag { margin-left: 6px; font-size: 12px; font-weight: 500; color: #f56c6c; border: 1px solid #f56c6c; border-radius: 999px; padding: 0 8px; }
.dk-end { text-align: center; font-size: 12px; color: #a8abb2; padding: 4px 0 8px; }

.dk-read-tip { font-size: 12px; color: #e6a23c; }
.dk-read-tip.done { color: #67c23a; }

.dk-footer { display: flex; flex-direction: column; gap: 10px; }
.dk-chk { white-space: normal; height: auto; align-items: flex-start; }
.dk-chk :deep(.el-checkbox__label) { white-space: normal; line-height: 20px; font-size: 13px; }
.dk-btns { display: flex; justify-content: flex-end; gap: 8px; }
</style>
