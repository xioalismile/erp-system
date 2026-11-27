<template>
  <div class="m-page">
    <!-- 页头：标题 + 右侧主操作（不做底部操作条） -->
    <div class="m-header">
      <span class="m-back" @click="goBack">‹</span>
      <span class="m-title">扫码</span>
      <span class="m-header-btn" @click="openScan">扫一扫</span>
    </div>

    <div class="m-body">
      <div class="m-card">
        <div class="m-card-title">二维码内容</div>
        <input
          v-model="code"
          class="m-input"
          type="text"
          enterkeyhint="go"
          placeholder="粘贴或输入二维码内容"
          @keyup.enter="resolveCode"
        />
        <div class="m-actions">
          <button class="m-btn m-btn-primary" :disabled="loading" @click="resolveCode">
            {{ loading ? '解析中…' : '解析' }}
          </button>
        </div>
        <div class="m-hint">用微信「扫一扫」扫工序卡或单据上的二维码，会自动打开对应页面；扫不了时可把码内容粘到上面。</div>
      </div>

      <div v-if="resolved" class="m-card">
        <div class="m-card-title">{{ resolved.title || '识别结果' }}</div>
        <div class="m-row"><span class="m-label">类型</span><span>{{ typeText }}</span></div>
        <div v-if="resolved.subtitle" class="m-row"><span class="m-label">说明</span><span>{{ resolved.subtitle }}</span></div>
        <div v-if="!resolved.valid" class="m-error">{{ resolved.invalidReason || '该二维码不可用' }}</div>
        <div v-if="resolved.valid" class="m-actions">
          <button class="m-btn m-btn-primary" @click="openTarget">打开</button>
        </div>
      </div>

      <div v-if="errorMsg" class="m-card m-error">{{ errorMsg }}</div>
    </div>
  </div>
</template>

<script setup>
/**
 * 移动端扫码页（/m/scan）：供微信小程序 web-view 或手机浏览器使用。
 * 能力：解析二维码内容（完整 URL 或 t/id/dt/s 参数串）→ 识别工序/单据 → 跳到对应页面。
 * 只复用现有 /api/qrcode/resolve，不在前端自行判定权限与状态。
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import scanCode from '@/api/system/scanCode'

const route = useRoute()
const router = useRouter()

const code = ref('')
const loading = ref(false)
const errorMsg = ref('')
const resolved = ref(null)

const typeText = computed(() => {
  const t = resolved.value?.type
  if (t === 'OP') return '工序'
  if (t === 'BILL') return '单据'
  if (t === 'MAT') return '物料'
  return t || ''
})

/** 从任意文本里取 t/dt/id/s：支持完整 URL、?query、纯参数串 */
function parseCode (text) {
  const raw = String(text || '').trim()
  if (!raw) return null
  let query = ''
  if (/^https?:\/\//i.test(raw)) {
    const i = raw.indexOf('?')
    query = i >= 0 ? raw.slice(i + 1) : ''
  } else if (raw.includes('?')) {
    query = raw.slice(raw.indexOf('?') + 1)
  } else {
    query = raw
  }
  const params = new URLSearchParams(query)
  const t = params.get('t')
  const id = params.get('id')
  if (!t || !id) return null
  return { t: t.toUpperCase(), dt: params.get('dt') || '', id, s: params.get('s') || '' }
}

async function resolveCode () {
  errorMsg.value = ''
  resolved.value = null
  const parsed = parseCode(code.value)
  if (!parsed) {
    errorMsg.value = '没识别到有效内容，请确认扫的是系统二维码'
    return
  }
  loading.value = true
  try {
    const r = await scanCode.resolve(parsed.t, parsed.dt || undefined, parsed.id, parsed.s || undefined)
    resolved.value = r || null
    // 工序码：直接进工序操作页，少点一次
    if (r?.valid && r.type === 'OP' && r.id) {
      router.push(`/m/operation/${r.id}`)
    }
  } catch (e) {
    errorMsg.value = e?.message || '解析失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openTarget () {
  const r = resolved.value
  if (!r) return
  if (r.type === 'OP' && r.id) {
    router.push(`/m/operation/${r.id}`)
    return
  }
  if (r.navPath) {
    router.push({ path: r.navPath, query: r.navQuery || {} })
    return
  }
  errorMsg.value = '该二维码没有可打开的页面'
}

function openScan () {
  const wx = window.wx
  if (wx && typeof wx.scanQRCode === 'function') {
    wx.scanQRCode({
      needResult: 1,
      scanType: ['qrCode', 'barCode'],
      success: (res) => {
        const text = (res.resultStr || '').split(',').pop()
        code.value = text
        resolveCode()
      }
    })
    return
  }
  errorMsg.value = '当前环境不支持直接调起摄像头，请在微信里用「扫一扫」扫二维码'
}

function goBack () {
  if (window.history.length > 1) router.back()
  else router.push('/dashboard')
}

onMounted(() => {
  // 二维码直接指向本页时（/m/scan?t=OP&id=12&s=...），进来就自动解析
  const q = route.query || {}
  if (q.t && q.id) {
    code.value = `t=${q.t}&id=${q.id}${q.dt ? `&dt=${q.dt}` : ''}${q.s ? `&s=${q.s}` : ''}`
    resolveCode()
  }
})
</script>

<style scoped>
.m-page {
  min-height: 100vh;
  background: #f5f6f8;
  font-size: 14px;
  color: #1f2937;
}
.m-header {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #eef0f3;
}
.m-back {
  width: 26px;
  font-size: 22px;
  line-height: 1;
  color: #4b5563;
}
.m-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
}
.m-header-btn {
  font-size: 14px;
  color: #2563eb;
  padding: 4px 2px;
}
.m-body {
  padding: 10px 12px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-card {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
}
.m-card-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}
.m-input {
  width: 100%;
  box-sizing: border-box;
  height: 40px;
  padding: 0 10px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  outline: none;
  background: #fafbfc;
}
.m-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.m-btn {
  flex: 1;
  height: 38px;
  border-radius: 6px;
  border: 1px solid #dbe2ea;
  background: #fff;
  font-size: 14px;
  color: #1f2937;
}
.m-btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.m-btn[disabled] {
  opacity: 0.6;
}
.m-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #9ca3af;
}
.m-row {
  display: flex;
  gap: 10px;
  padding: 4px 0;
  font-size: 13px;
}
.m-label {
  flex: none;
  width: 44px;
  color: #9ca3af;
}
.m-error {
  margin-top: 8px;
  font-size: 13px;
  color: #dc2626;
}
</style>
