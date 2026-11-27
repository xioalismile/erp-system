<template>
  <div class="erp-page">
    <el-card shadow="never" class="disclaimer-card">
      <div class="page-header">
        <h2 class="page-title">{{ DISCLAIMER_TITLE }}</h2>
        <div class="meta-line">
          <span>版本 v{{ DISCLAIMER_VERSION }}</span>
          <span class="meta-sep">·</span>
          <span>更新日期 {{ DISCLAIMER_UPDATED_AT }}</span>
          <el-button v-permission="perm('print')" type="primary" size="small" class="print-btn" @click="onPrint">🖨 打印留存</el-button>
        </div>
        <div class="status-line" :class="statusClass">
          <template v-if="loading">正在读取同意状态…</template>
          <template v-else-if="loadError">状态读取失败，请刷新重试</template>
          <template v-else-if="status.accepted">✅ 已于 {{ status.acceptedAt }} 同意 v{{ status.acceptedVersion }}</template>
          <template v-else>⏳ 尚未同意：下次打开小楷助手时会弹出本协议，勾选同意后即可使用</template>
        </div>
      </div>

      <!-- 重要提示：核心免责/管辖条款的显著提示（格式条款硬要求） -->
      <div class="key-block">
        <div class="key-block-title">重要提示</div>
        <div class="key-block-txt">
          本协议含<b>免除或限制提供方责任</b>的条款及<b>争议管辖约定</b>，以下条款已显著提示，请务必阅读：
        </div>
        <div v-for="c in keyClauses" :key="c.no" class="key-item">
          <div class="key-item-t">第{{ c.no }}条 {{ c.title }}<span class="key-tag">核心条款</span></div>
          <div class="key-item-b">{{ c.text }}</div>
        </div>
      </div>

      <!-- 完整协议正文 -->
      <el-divider content-position="left">
        <span class="divider-text">完整协议全文（共 {{ DISCLAIMER_CLAUSES.length }} 条）</span>
      </el-divider>
      <div class="clause-list">
        <div v-for="c in DISCLAIMER_CLAUSES" :key="c.no" class="clause">
          <div class="clause-t" :class="{ key: c.key }">
            第{{ c.no }}条 {{ c.title }}
            <span v-if="c.key" class="key-tag">核心条款</span>
          </div>
          <div class="clause-b" :class="{ key: c.key }">{{ c.text }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:ai-disclaimer')
/**
 * 设置中心 → AI → 协议与免责声明：只读查阅页。
 * 正文唯一来源 = constants/xiaokaiDisclaimer.js；同意状态来自后端账号级记录。
 * 只提供浏览器打印留档，不做导出 PDF、不做重新签署。
 */
import { ref, computed, onMounted } from 'vue'
import { getDisclaimerStatus } from '@/api/ai/disclaimer'
import {
  DISCLAIMER_VERSION,
  DISCLAIMER_TITLE,
  DISCLAIMER_UPDATED_AT,
  DISCLAIMER_CLAUSES
} from '@/constants/xiaokaiDisclaimer'

// 设置中心以 <component :is="..." :embedded /> 内嵌渲染，声明该 prop 避免落到 DOM 属性上
defineProps({ embedded: { type: Boolean, default: false } })

// 需要显著提示的核心条款（第七条/第八条/第十一条/第十二条/第十六条）
const keyClauses = DISCLAIMER_CLAUSES.filter(c => c.key)

const loading = ref(true)
const loadError = ref(false)
const status = ref({ accepted: false, acceptedVersion: '', acceptedAt: '' })

const statusClass = computed(() => {
  if (loadError.value) return 'is-error'
  return status.value.accepted ? 'is-accepted' : 'is-pending'
})

async function loadStatus() {
  loading.value = true
  loadError.value = false
  try {
    const res = await getDisclaimerStatus(DISCLAIMER_VERSION)
    status.value = {
      accepted: !!(res && res.accepted),
      acceptedVersion: (res && res.acceptedVersion) || '',
      acceptedAt: (res && res.acceptedAt) || ''
    }
  } catch (e) {
    // 接口失败不白屏：给出可重试提示（刷新页面即重试）
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onPrint() {
  window.print()
}

onMounted(() => {
  loadStatus()
})
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.disclaimer-card {
  max-width: 1000px;
  margin: 20px auto;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 22px;
  color: #303133;
  margin: 0 0 8px 0;
  font-weight: 600;
}
.meta-line {
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
}
.meta-sep { color: #c0c4cc; }
.print-btn { margin-left: 12px; }
.status-line {
  margin-top: 10px;
  font-size: 13px;
  line-height: 20px;
  &.is-accepted { color: #67c23a; }
  &.is-pending { color: #e6a23c; }
  &.is-error { color: #f56c6c; }
}
.key-block {
  border: 1px solid #f56c6c;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 8px;
}
.key-block-title {
  color: #f56c6c;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 6px;
}
.key-block-txt {
  font-size: 13px;
  line-height: 20px;
  color: #303133;
  margin-bottom: 8px;
}
.key-item {
  margin-bottom: 8px;
}
.key-item:last-child { margin-bottom: 0; }
.key-item-t {
  font-size: 13px;
  font-weight: 700;
  color: #c45656;
}
.key-item-b {
  font-size: 12px;
  line-height: 19px;
  color: #606266;
}
.divider-text {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}
.clause-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.clause-t {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 2px;
  &.key {
    font-weight: 700;
    color: #c45656;
  }
}
.clause-b {
  font-size: 13px;
  line-height: 20px;
  color: #606266;
  &.key {
    font-weight: 600;
    color: #303133;
  }
}
.key-tag {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #f56c6c;
  border: 1px solid #f56c6c;
  border-radius: 999px;
  padding: 0 8px;
}
</style>
