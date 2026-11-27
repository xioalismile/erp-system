<template>
  <div
    class="qr-namecard"
    :class="[`is-${layout}`, { 'is-compact': compact, 'is-error': failed, 'is-empty': !bizId }]"
    :title="tip"
  >
    <div class="qr-namecard__frame" :style="frameStyle">
      <img
        v-if="dataUri"
        :src="dataUri"
        class="qr-namecard__img"
        :alt="altText"
        @click="reload"
      />
      <div v-else class="qr-namecard__ph" @click="reload">
        <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
        <span v-else>{{ hintText }}</span>
      </div>
    </div>
    <div v-if="!compact" class="qr-namecard__meta">
      <span class="qr-namecard__title">{{ title }}</span>
      <span v-if="hint" class="qr-namecard__hint">{{ hint }}</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 二维码码牌（2026-09-27）
 *
 * 用途：在电脑端的「物料 / 单据 / 工序」页面上常驻显示二维码，手机对屏即可扫，无需按钮、无需打印。
 *
 * 视觉设计（为什么这样做）：
 *   二维码是"这条记录的身份标识"，属于信息；而页头那一排是"动作按钮"。
 *   直接把一张裸二维码图片塞进按钮堆里既丑又抢戏，所以这里做成一块独立的「码牌」：
 *   白底 + 细边框 + 圆角 + 轻投影 + 下方一行小字标题，与按钮在视觉上明确分层；
 *   悬停时轻微上浮、边框转为主题蓝，提示"这是一块可交互的码牌"。
 *
 * 尺寸取舍：宽度只占 70 余像素（竖向码牌），比横向卡片窄一半，塞进拥挤的页头也不易挤爆；
 *   码面 60px 左右在屏幕上约 1.6cm，手机近距离可正常扫出。
 *
 * 技术关键：不能用 <img src="/api/qrcode?..."> —— 该接口需登录令牌，而 <img> 不带 Authorization 头，必然 401；
 *   故统一走 request 封装取 data-uri（自动带令牌）。
 */
import { ref, computed, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { tt } from '@/i18n'
import scanCodeApi from '@/api/system/scanCode'

const props = defineProps({
  /** 码类型：MAT 物料 / BILL 单据 / OP 工序 */
  type: { type: String, default: '' },
  /** 业务主键（新建未保存时为空 → 显示占位提示） */
  bizId: { type: [Number, String], default: null },
  /** 单据类型（仅 BILL 需要，如 SALE_ORDER） */
  docType: { type: String, default: '' },
  /** 码面尺寸（px） */
  size: { type: Number, default: 56 },
  /** 码牌标题（如「单据码」） */
  title: { type: String, default: '' },
  /** 标题下的辅助小字；传空则不显示 */
  hint: { type: String, default: '手机扫码打开' },
  /** 排布：horizontal 横向名片（默认，矮而宽，不撑高页头）/ vertical 竖向（窄，用于表格行内） */
  layout: { type: String, default: 'horizontal' },
  /** 紧凑模式：去掉外框与文字，只留码面（用于表格行内） */
  compact: { type: Boolean, default: false }
})

const dataUri = ref('')
const loading = ref(false)
const failed = ref(false)

const px = computed(() => `${props.size}px`)
const frameStyle = computed(() => ({ width: px.value, height: px.value }))
const altText = computed(() => props.title || tt('二维码'))
const tip = computed(() => {
  if (!props.bizId) return tt('保存后自动生成二维码')
  if (failed.value) return tt('二维码加载失败，点击重试')
  return props.hint ? `${props.title || ''} ${props.hint}`.trim() : (props.title || tt('二维码'))
})
const hintText = computed(() => {
  if (!props.bizId) return tt('保存后生成')
  return failed.value ? tt('点击重试') : tt('加载失败')
})

async function load() {
  if (!props.type || !props.bizId) {
    dataUri.value = ''
    failed.value = false
    return
  }
  loading.value = true
  failed.value = false
  try {
    // 取 2 倍图、按 1 倍显示：高倍屏下边缘更锐利，扫码更稳
    dataUri.value = await scanCodeApi.dataUri(props.type, props.docType || undefined, props.bizId, props.size * 2)
  } catch (e) {
    dataUri.value = ''
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 点击码面重试（失败后或需要刷新时） */
function reload() {
  if (props.bizId) load()
}

watch(() => [props.type, props.bizId, props.docType, props.size], load, { immediate: true })
</script>

<style scoped>
/* ===== 码牌外壳：白底细边框圆角 + 轻投影，与旁边的实心按钮明确分层 ===== */
.qr-namecard {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 5px 6px 6px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(31, 45, 61, 0.06);
  transition: border-color .2s, box-shadow .2s, transform .2s;
  user-select: none;
}
.qr-namecard:hover {
  border-color: #a0cfff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, .16);
  transform: translateY(-1px);
}

/* 横向名片（默认，用于页头）：码面在左、文字在右 —— 总高约 70px，
   只把页头抬高约 20px，不会像竖向码牌那样把标题栏撑成 120px 的高塔。 */
.qr-namecard.is-horizontal {
  flex-direction: row;
  gap: 9px;
  padding: 6px 12px 6px 6px;
}
.qr-namecard.is-horizontal .qr-namecard__meta {
  align-items: flex-start;
  text-align: left;
  gap: 2px;
}
.qr-namecard.is-horizontal .qr-namecard__title {
  font-size: 12px;
}
.qr-namecard.is-horizontal .qr-namecard__hint {
  font-size: 10px;
}

/* 紧凑模式：表格行内使用，去掉一切装饰只留码面 */
.qr-namecard.is-compact {
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  box-shadow: none;
}
.qr-namecard.is-compact:hover {
  transform: none;
  box-shadow: none;
}

/* ===== 码面：自带浅底与内边距，充当二维码的静区，保证识别率 ===== */
.qr-namecard__frame {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 6px;
  overflow: hidden;
  flex: 0 0 auto;
}
.qr-namecard.is-compact .qr-namecard__frame {
  border-color: #ebeef5;
}
.qr-namecard__img {
  display: block;
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}

/* ===== 占位态：未保存 / 加载中 / 加载失败 ===== */
.qr-namecard__ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  font-size: 10px;
  line-height: 1.25;
  color: #a8abb2;
  text-align: center;
  background: #fafbfc;
  cursor: pointer;
}
.qr-namecard.is-empty .qr-namecard__frame {
  border-style: dashed;
  border-color: #dcdfe6;
}
.qr-namecard.is-error .qr-namecard__frame {
  border-color: #fbc4c4;
  background: #fef6f6;
}

/* ===== 文字区 ===== */
.qr-namecard__meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  line-height: 1.2;
}
.qr-namecard__title {
  font-size: 11px;
  font-weight: 600;
  color: #303133;
  letter-spacing: .2px;
}
.qr-namecard__hint {
  font-size: 10px;
  color: #a8abb2;
  white-space: nowrap;
}
</style>