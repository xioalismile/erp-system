<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="560px"
    :before-close="handleClose"
    destroy-on-close
  >
    <div class="logistics-tracker">

      <!-- ============ 模式一：【单输入框一键查询】（默认展示，只要没有deliveryId） ============ -->
      <div v-if="!deliveryId" class="one-click-query">
        <div class="section-title">
          <el-icon><Search /></el-icon>
          <span>{{ $tt("输入快递单号，自动识别快递公司查询物流") }}</span>
        </div>

        <!-- 单号输入框 -->
        <div class="input-row">
          <el-input
            v-model="queryTrackingNo"
            :placeholder="$tt('请输入快递单号（粘贴即可，自动去空格）')"
            size="large"
            clearable
            :disabled="loading"
            @keyup.enter="onInputSubmit"
            @blur="onInputBlurMaybeDetect"
          >
            <template #prefix>
              <el-icon><Van /></el-icon>
            </template>
            <template #append>
              <el-button type="primary" v-permission="perm('query')" :loading="loading || detecting" @click="onInputSubmit">
                {{ loading ? '查询中' : '查询' }}
              </el-button>
            </template>
          </el-input>
        </div>

        <!-- 识别中 -->
        <div v-if="detecting" class="detecting-row">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ $tt("正在根据单号自动识别快递公司...") }}</span>
        </div>

        <!-- 识别成功（单一命中）：提示是啥公司 + 回填提示 -->
        <el-alert
          v-if="detectResult && detectResult.matched && !needUserSelectCarrier"
          class="detect-alert"
          :closable="false"
          type="success"
          show-icon
        >
          <template #title>
            自动识别承运商：<b>{{ detectResult.matchedName }}</b>
            <el-tag size="small" effect="plain" style="margin-left:8px;">
              {{ detectResult.source }}
            </el-tag>
            <el-button
              link size="small" type="primary" style="margin-left:10px;"
              v-permission="perm('pick')"
              @click="backfillCarrierToParent(detectResult.matchedName)"
            >{{ $tt("回填到出库单") }}</el-button>
          </template>
        </el-alert>

        <!-- 多候选：让用户手动选，绝不瞎猜第一个避免串单 -->
        <el-alert
          v-if="needUserSelectCarrier && detectResult && detectResult.candidates && detectResult.candidates.length"
          class="detect-alert"
          :closable="false"
          type="warning"
          show-icon
          :title="$tt('单号可能属于以下多个快递公司，请手动选择：')"
        >
          <div class="candidate-list">
            <el-radio-group v-model="userSelectedCarrierCode" size="default">
              <el-radio-button
                v-for="c in detectResult.candidates"
                :key="c.code"
                :value="c.code"
              >
                {{ c.name }}
                <el-tag v-if="c.needPhone" size="small" type="danger" effect="plain" style="margin-left:4px;">
                  需手机号
                </el-tag>
              </el-radio-button>
            </el-radio-group>
          </div>
        </el-alert>

        <!-- 需要手机号后四位（顺丰/京东） -->
        <div v-if="resolvedNeedPhone" class="phone-input-row">
          <div class="phone-input-label">
            <el-icon><Phone /></el-icon>
            <span>
              该快递公司（<b>{{ currentCarrierName }}</b>）查询需要
              <el-tooltip :content="$tt('快递100接口要求，不存储、仅用于本次查询')">
                <el-tag size="small" type="danger" effect="light">{{ $tt("手机号后4位") }}</el-tag>
              </el-tooltip>
              才能查询到物流：
            </span>
          </div>
          <div class="phone-input-body">
            <el-input
              v-model="phoneLast4Input"
              :placeholder="$tt('请输入收件人手机号后 4 位数字')"
              size="default"
              maxlength="4"
              :disabled="loading"
              style="max-width:260px;"
              @keyup.enter="submitQueryWithPhone"
            >
              <template #prefix><el-icon><Key /></el-icon></template>
            </el-input>
            <el-button type="primary" v-permission="perm('query')" :loading="loading" @click="submitQueryWithPhone">
              补全后查询
            </el-button>
          </div>
          <div class="phone-hint">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ $tt("例如：收件人手机号 138****1234，只需输入最后 4 位") }} <code>1234</code> {{ $tt("即可") }}</span>
          </div>
        </div>
      </div>

      <!-- ============ 单号 / 承运商 信息头 ============ -->
      <div class="tracking-header" v-if="displayTrackingNo">
        <div class="tracking-row">
          <span class="label">{{ $tt("物流单号：") }}</span>
          <span class="value tracking-no">{{ displayTrackingNo }}</span>
          <el-button link type="primary" v-permission="perm('copy')" @click="copyTrackingNo">
            <el-icon><DocumentCopy /></el-icon>
          </el-button>
        </div>
        <div class="tracking-row" v-if="currentCarrierName">
          <span class="label">{{ $tt("承运商：") }}</span>
          <span class="value">{{ currentCarrierName }}</span>
        </div>
        <div class="tracking-row" v-if="result">
          <span class="label">{{ $tt("当前状态：") }}</span>
          <el-tag :type="statusTagType(result.statusCode)" effect="dark" size="small" round>
            <el-icon class="status-icon">
              <Box v-if="result.statusCode === '1' || result.statusCode === '10'" />
              <Van v-else-if="result.statusCode === '0' || result.statusCode === '8'" />
              <MapLocation v-else-if="result.statusCode === '5'" />
              <CircleCheck v-else-if="result.statusCode === '3'" />
              <Warning v-else-if="['2','4','6'].includes(result.statusCode)" />
              <Refresh v-else-if="result.statusCode === '7'" />
              <Search v-else />
            </el-icon>
            {{ $dt(result.statusText || '未知') }}
          </el-tag>
        </div>
      </div>

      <!-- ============ 加载 / 错误 / 轨迹 ============ -->
      <div v-if="loading" class="loading-container">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ $tt("正在查询物流信息...") }}</span>
      </div>

      <el-alert
        v-else-if="errorMessage"
        type="error"
        :title="errorMessage"
        :closable="false"
        show-icon
      />

      <div v-else-if="result && result.tracks && result.tracks.length > 0" class="track-list">
        <el-timeline>
          <el-timeline-item
            v-for="(track, index) in result.tracks"
            :key="index"
            :timestamp="track.time"
            placement="top"
            :type="index === 0 ? 'primary' : 'default'"
            :hollow="index !== 0"
            size="large"
          >
            <div class="track-item">
              <div class="track-context">{{ track.context }}</div>
              <div class="track-time">{{ track.time }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>

      <el-empty v-else-if="result" :description="$tt('暂无物流轨迹数据，可能刚发货请稍后再试')" />
    </div>

    <!-- 底部刷新 -->
    <template #footer>
      <div class="drawer-footer">
        <span class="query-time" v-if="result && result.queryTime">
          查询时间：{{ formatTime(result.queryTime) }}
        </span>
        <el-button v-permission="perm('dlgClose')" @click="handleClose">{{ $tt("关闭") }}</el-button>
        <el-button type="primary" v-permission="perm('refresh')" :loading="loading" @click="onRefresh">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DocumentCopy, Loading, Refresh, Search, Van, MapLocation,
  CircleCheck, Warning, Box, Clock, Phone, Key, InfoFilled
} from '@element-plus/icons-vue'
import { detectCarrier, queryLogistics, queryLogisticsByDelivery } from '@/api/logistics/logistics'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


// ===================== Props =====================
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 以下3个用于"出库单里已经填好值，直接查"的模式（抽屉用）
  trackingNo: { type: String, default: '' },
  carrier:    { type: String, default: '' },
  phone:      { type: String, default: '' },
  // 出库单ID（传了则走按ID查询，后端读carrier/phone自动）
  deliveryId: { type: [Number, String], default: null },
})

const emit = defineEmits([
  'update:modelValue',
  // 当检测或选择到承运商时通知父组件回填出库单
  // payload: { carrierName, carrierCode, trackingNo }
  'carrier-detected',
])

// ===================== State =====================
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const loading = ref(false)     // 查询轨迹loading
const detecting = ref(false)   // 识别承运商loading
const result = ref(null)
const errorMessage = ref('')

// 单输入框模式
const queryTrackingNo = ref('')
const phoneLast4Input = ref('')
const userSelectedCarrierCode = ref('')
const detectResult = ref(null)           // detectCarrier 原始返回
const overrideCarrierCode = ref('')      // 用户从候选里选了之后，或者父组件传了carrier翻译后
const overrideCarrierName = ref('')
const overrideNeedPhone = ref(false)
const needUserSelectCarrier = ref(false) // 后端返回了多候选

// ===================== Computed =====================
const drawerTitle = computed(() => {
  if (displayTrackingNo.value) return `物流查询 · ${displayTrackingNo.value}`
  return '物流查询 · 输入单号一键查询'
})

/** 最终显示用的单号 */
const displayTrackingNo = computed(() => {
  if (result.value && result.value.trackingNo) return result.value.trackingNo
  if (props.trackingNo) return props.trackingNo
  if (queryTrackingNo.value) return queryTrackingNo.value
  return ''
})

/** 最终显示用的承运商名 */
const currentCarrierName = computed(() => {
  if (result.value && (result.value.detectedCarrierName || result.value.carrierName)) {
    return result.value.detectedCarrierName || result.value.carrierName
  }
  if (overrideCarrierName.value) return overrideCarrierName.value
  if (detectResult.value && detectResult.value.matched) return detectResult.value.matchedName
  if (props.carrier) return props.carrier
  return ''
})

/** 当前选中的承运商编码（查询/手机号判断用） */
const currentCarrierCode = computed(() => {
  if (overrideCarrierCode.value) return overrideCarrierCode.value
  if (userSelectedCarrierCode.value) return userSelectedCarrierCode.value
  if (detectResult.value && detectResult.value.matched) return detectResult.value.matchedCode
  return ''
})

/** 当前是否缺手机号后四位？（注意：顺丰/京东 needPhone + 还没填后四位 时才true） */
const resolvedNeedPhone = computed(() => {
  // 1) 用返回结果定
  if (result.value && result.value.needPhone && !phoneLast4Filled.value) {
    return true
  }
  // 2) 用 detectResult / override 定
  let need = false
  if (detectResult.value && detectResult.value.matched) need = detectResult.value.needPhone
  if (overrideNeedPhone.value) need = true
  // 候选中的选中情况
  if (needUserSelectCarrier.value && userSelectedCarrierCode.value && detectResult.value) {
    const hit = (detectResult.value.candidates || []).find(c => c.code === userSelectedCarrierCode.value)
    if (hit) need = hit.needPhone
  }
  return need && !phoneLast4Filled.value
})

/** 已提供手机号后四位？要么完整phone有，要么用户刚输入了 */
const phoneLast4Filled = computed(() => {
  if (phoneLast4Input.value && phoneLast4Input.value.length === 4) return true
  if (props.phone && props.phone.replace(/\D/g, '').length >= 4) return true
  return false
})

// ===================== Watchers =====================
watch(visible, (val) => {
  if (val) {
    // 传了 deliveryId：直接查
    if (props.deliveryId) {
      nextTick(() => doQuery())
      return
    }
    // 父组件已经预置了 trackingNo（出库单点查询的情况）：放进输入框，自动识别+查
    if (props.trackingNo) {
      queryTrackingNo.value = props.trackingNo
      nextTick(() => onInputSubmit())
    }
  }
})

// ===================== Actions =====================
/** 输入框回车 / 失焦300ms无输入 → 自动走识别+查询流程 */
let _blurTimer = null
function onInputBlurMaybeDetect() {
  if (!queryTrackingNo.value.trim()) return
  if (detecting.value || loading.value) return
  clearTimeout(_blurTimer)
  _blurTimer = setTimeout(() => {
    onInputSubmit()
  }, 300)
}

async function onInputSubmit() {
  const no = (queryTrackingNo.value || '').trim()
  if (!no) {
    ElMessage.warning(tt('请输入快递单号'))
    return
  }
  errorMessage.value = ''
  result.value = null
  detectResult.value = null
  overrideCarrierCode.value = ''
  overrideCarrierName.value = ''
  overrideNeedPhone.value = false
  needUserSelectCarrier.value = false
  userSelectedCarrierCode.value = ''

  // 如果父组件已经给了carrier，就不用识别了，直接查
  if (props.carrier) {
    // 尝试从phone取后四位
    const last4 = extractLast4(props.phone)
    if (last4) phoneLast4Input.value = last4
    return doQuery({ carrier: props.carrier, phone: props.phone, phoneLast4: last4 })
  }

  // Step 1: 调后端 detectCarrier（严格前缀→autonumber候选）
  detecting.value = true
  try {
    const raw = await detectCarrier(no)
    const data = unwrapData(raw)
    detectResult.value = data

    if (data && data.matched) {
      // 单命中 → 自动回填给父组件
      emit('carrier-detected', {
        carrierName: data.matchedName,
        carrierCode: data.matchedCode,
        trackingNo: no,
      })
      overrideNeedPhone.value = !!data.needPhone
      // 继承父组件的phone（如果有）
      if (props.phone) {
        const last4 = extractLast4(props.phone)
        if (last4) phoneLast4Input.value = last4
      }
      // needPhone且没后四位 → 展示输入框，等待用户输入后再查（不直接查）
      if (data.needPhone && !phoneLast4Filled.value) {
        return
      }
      return doQuery({ carrierCode: data.matchedCode, phoneLast4: phoneLast4Input.value || undefined })
    }

    // 多候选 → 让用户选，选完再查
    if (data && data.candidates && data.candidates.length) {
      needUserSelectCarrier.value = true
      // 候选只有1个的时候后端已经返回matched=true了，这里一定≥2
      // 监听 radio 变化 → 选中即查
      // 用 effect 形式 watch
      watchOnceSelected(() => {
        const c = (data.candidates || []).find(x => x.code === userSelectedCarrierCode.value)
        if (c) {
          emit('carrier-detected', {
            carrierName: c.name, carrierCode: c.code, trackingNo: no,
          })
          overrideNeedPhone.value = !!c.needPhone
          overrideCarrierCode.value = c.code
          overrideCarrierName.value = c.name
          needUserSelectCarrier.value = false
          if (props.phone) {
            const last4 = extractLast4(props.phone)
            if (last4) phoneLast4Input.value = last4
          }
          if (c.needPhone && !phoneLast4Filled.value) return
          doQuery({ carrierCode: c.code, phoneLast4: phoneLast4Input.value || undefined })
        }
      })
      return
    }

    // 识别不到 → 报错提示用户手动填承运商
    errorMessage.value = '未能根据单号识别出承运商，请先到出库单【承运商】字段选择快递公司，或直接联系仓库确认'
  } catch (e) {
    console.error('detectCarrier failed:', e)
    errorMessage.value = e.message || '识别承运商失败，请直接在出库单选择承运商后查询'
  } finally {
    detecting.value = false
  }
}

function watchOnceSelected(cb) {
  const stop = watch(userSelectedCarrierCode, (v) => {
    if (v) {
      stop && stop()
      cb()
    }
  })
}

/** 用户刚输入了后四位 → 提交查询 */
function submitQueryWithPhone() {
  if (!/^\d{4}$/.test(phoneLast4Input.value || '')) {
    ElMessage.warning(tt('请输入4位数字的手机号后四位'))
    return
  }
  const code = currentCarrierCode.value
  if (!code && props.carrier) {
    doQuery({ carrier: props.carrier, phoneLast4: phoneLast4Input.value })
    return
  }
  if (!code) {
    ElMessage.warning(tt('请先选择或识别承运商'))
    return
  }
  doQuery({ carrierCode: code, phoneLast4: phoneLast4Input.value })
}

/** 真正发起查询轨迹请求 */
async function doQuery(extra = {}) {
  errorMessage.value = ''
  // 出库单ID：优先级最高
  if (props.deliveryId) {
    loading.value = true
    try {
      const res = await queryLogisticsByDelivery(props.deliveryId)
      applyResult(unwrapData(res))
    } catch (e) {
      errorMessage.value = e.message || '查询失败，请稍后重试'
    } finally {
      loading.value = false
    }
    return
  }

  const trackingNo = (queryTrackingNo.value || props.trackingNo || '').trim()
  if (!trackingNo) {
    errorMessage.value = '缺少物流单号'
    return
  }
  loading.value = true
  try {
    const payload = Object.assign({ trackingNo }, extra)
    // 如果 props 有phone但没传后四位，顺带带上让后端自己取
    if (!payload.phone && !payload.phoneLast4 && props.phone) {
      payload.phone = props.phone
    }
    const res = await queryLogistics(payload)
    const data = unwrapData(res)

    // 如果后端返回了多候选 → 展示候选列表让用户选
    if (data && data.needUserSelectCarrier && data.carrierCandidates && data.carrierCandidates.length) {
      detectResult.value = {
        matched: false,
        matchedCode: '',
        matchedName: '',
        needPhone: false,
        candidates: data.carrierCandidates,
        source: 'query_multi_candidate',
      }
      needUserSelectCarrier.value = true
      watchOnceSelected(() => {
        const c = data.carrierCandidates.find(x => x.code === userSelectedCarrierCode.value)
        if (c) {
          emit('carrier-detected', { carrierName: c.name, carrierCode: c.code, trackingNo })
          overrideCarrierCode.value = c.code
          overrideCarrierName.value = c.name
          overrideNeedPhone.value = !!c.needPhone
          needUserSelectCarrier.value = false
          if (c.needPhone && !phoneLast4Filled.value) {
            loading.value = false
            return
          }
          doQuery({ carrierCode: c.code, phoneLast4: phoneLast4Input.value || undefined })
        }
      })
      loading.value = false
      return
    }

    // 把识别到的回填给父组件（出库单页面）
    if (data && data.detectedCarrierName && !props.carrier) {
      emit('carrier-detected', {
        carrierName: data.detectedCarrierName,
        carrierCode: data.detectedCarrierCode,
        trackingNo,
      })
    }
    applyResult(data)
  } catch (e) {
    console.error('query logistics failed:', e)
    errorMessage.value = e.message || '查询失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function applyResult(data) {
  if (!data) {
    errorMessage.value = '查询失败：空响应'
    return
  }
  if (data.success === false) {
    errorMessage.value = data.errorMessage || '查询失败'
    // 仍把部分元信息挂到result让needPhone UI能显示
    if (data.needPhone || data.detectedCarrierName) {
      result.value = Object.assign({ tracks: [], statusCode: '', statusText: '' }, data, { success: false })
    }
    return
  }
  result.value = data
}

/** 底部刷新按钮 */
function onRefresh() {
  // deliveryId模式：直接重查
  if (props.deliveryId) return doQuery()
  // 有单号就再查一次（保留已选carrierCode和已填phoneLast4）
  const no = (queryTrackingNo.value || props.trackingNo || '').trim()
  if (!no) {
    ElMessage.warning(tt('请先输入快递单号'))
    return
  }
  const code = currentCarrierCode.value
  const params = {}
  if (code) params.carrierCode = code
  else if (props.carrier) params.carrier = props.carrier
  if (phoneLast4Input.value) params.phoneLast4 = phoneLast4Input.value
  else if (props.phone) params.phone = props.phone
  doQuery(params)
}

/** 识别结果 / 用户选了承运商 → 弹"回填到出库单" */
function backfillCarrierToParent(name) {
  const no = (queryTrackingNo.value || props.trackingNo || '').trim()
  const code = currentCarrierCode.value
  emit('carrier-detected', { carrierName: name, carrierCode: code, trackingNo: no })
  ElMessage.success(tt('已回填承运商到出库单'))
}

function handleClose() {
  visible.value = false
  result.value = null
  errorMessage.value = ''
  detectResult.value = null
  needUserSelectCarrier.value = false
  userSelectedCarrierCode.value = ''
  phoneLast4Input.value = ''
  overrideCarrierCode.value = ''
  overrideCarrierName.value = ''
  overrideNeedPhone.value = false
  clearTimeout(_blurTimer)
}

function copyTrackingNo() {
  const no = displayTrackingNo.value
  if (!no) return
  navigator.clipboard.writeText(no).then(() => {
    ElMessage.success(tt('已复制物流单号'))
  }).catch(() => ElMessage.warning(tt('复制失败，请手动复制')))
}

// ===================== Helpers =====================
function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 19)
}

function statusTagType(state) {
  if (!state) return 'info'
  const map = {
    '1': 'primary', '10': 'primary',
    '0': 'warning', '8': 'warning',
    '5': 'info',
    '3': 'success',
    '2': 'danger', '4': 'danger', '6': 'danger',
    '7': 'info',
  }
  return map[state] || 'info'
}

/** 兼容Result包装 { code, data, msg } 或直接对象 */
function unwrapData(res) {
  if (res == null) return null
  if (res.data !== undefined && res.data !== null) {
    // 还有一层 code+msg 的 Result 包装：取data
    return res.data
  }
  return res
}

function extractLast4(phone) {
  if (!phone) return ''
  const d = String(phone).replace(/\D/g, '')
  return d.length >= 4 ? d.slice(-4) : ''
}
</script>

<style scoped>
.logistics-tracker { padding: 8px 6px 4px; }

/* ============ 一键查询顶部 ============ */
.one-click-query {
  background: linear-gradient(135deg, #f5f9ff 0%, #eef5ff 100%);
  border: 1px solid #e1ebff;
  border-radius: 10px;
  padding: 14px 16px 16px;
  margin-bottom: 14px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f4e8a;
  margin-bottom: 10px;
}
.section-title .el-icon { font-size: 17px; }
.input-row { margin-bottom: 10px; }
.detecting-row {
  display: flex; align-items: center; gap: 8px;
  color: #909399; font-size: 13px; margin: 4px 0 8px;
}
.detecting-row .el-icon { color: #409eff; font-size: 18px; }
.detect-alert { margin-top: 6px; margin-bottom: 4px; }
.candidate-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 手机号后四位 */
.phone-input-row {
  margin-top: 12px;
  padding: 12px 14px;
  background: #fff7ec;
  border: 1px dashed #ffc98a;
  border-radius: 8px;
}
.phone-input-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8a4b00;
  font-size: 13px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.phone-input-body {
  display: flex;
  gap: 10px;
  align-items: center;
}
.phone-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a46400;
  font-size: 12px;
  margin-top: 8px;
}
.phone-hint code {
  background: #fff;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #ffd9a8;
}

/* ============ 信息头 ============ */
.tracking-header {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.tracking-row {
  display: flex; align-items: center;
  padding: 4px 0;
  font-size: 14px;
}
.tracking-row .label { color: #909399; width: 84px; flex-shrink: 0; }
.tracking-row .value { color: #303133; flex: 1; }
.tracking-no {
  font-family: 'Consolas', 'Monaco', monospace;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.status-icon { margin-right: 4px; vertical-align: middle; }

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  color: #909399;
  gap: 12px;
}
.loading-container .is-loading { font-size: 28px; color: #409eff; }
.track-list { padding: 8px 4px; }
.track-item { padding: 4px 0; }
.track-context {
  color: #303133; font-size: 14px; line-height: 1.6; margin-bottom: 4px;
}
.track-time { color: #909399; font-size: 12px; margin-top: 2px; }

.drawer-footer {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
}
.query-time {
  color: #909399; font-size: 12px; margin-right: auto;
}
</style>
