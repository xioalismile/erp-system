<template>
  <div class="kgw-notification">
    <!-- 顶部工具栏 -->
    <div class="kgw-toolbar">
      <el-button v-permission="perm('save')" type="primary" size="small" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
      <el-button v-permission="perm('refresh')" size="small" @click="loadConfig">{{ $tt("刷新") }}</el-button>
      <span class="kgw-hint">{{ $tt("提示：修改后请点击保存，立即生效") }}</span>
    </div>

    <!-- 主开关卡片 -->
    <div class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><Bell /></el-icon>
        <span>{{ $tt("消息通知总开关") }}</span>
      </div>
      <div class="kgw-card-body">
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("启用消息通知：") }}</div>
          <div class="kgw-control">
            <el-switch v-model="cfg.enabled" />
            <span class="kgw-desc">{{ cfg.enabled ? '已启用' : '已关闭' }}，{{ cfg.enabled ? '铃铛会显示未读数' : '铃铛不再推送未读数' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 各类型开关卡片 -->
    <div class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><Setting /></el-icon>
        <span>{{ $tt("通知类型开关") }}</span>
      </div>
      <div class="kgw-card-body">
        <div v-for="t in typeItems" :key="t.key" class="kgw-row">
          <div class="kgw-label">
            <el-icon :color="t.color"><component :is="t.icon" /></el-icon>
            <span>{{ $tf(t.label) }}：</span>
          </div>
          <div class="kgw-control">
            <el-switch v-model="cfg[t.key]" />
            <span class="kgw-desc">{{ t.desc }}</span>
          </div>
        </div>

        <!-- 阈值配置 -->
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("即将超期阈值：") }}</div>
          <div class="kgw-control">
            <el-input-number v-model="cfg.soonOverdueDays" :min="1" :max="60" size="small" controls-position="right" style="width:120px" />
            <span class="kgw-desc">{{ $tt("剩余天数 ≤ 该值时视为即将超期（默认 7 天）") }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 扫描频率卡片 -->
    <div class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><Timer /></el-icon>
        <span>{{ $tt("扫描频率") }}</span>
      </div>
      <div class="kgw-card-body">
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("扫描频率：") }}</div>
          <div class="kgw-control">
            <el-select v-model="cfg.scanCron" size="small" style="width:240px">
              <el-option :label="$tf('每 1 分钟')" value="0 */1 * * * ?" />
              <el-option :label="$tf('每 2 分钟 (默认)')" value="0 */2 * * * ?" />
              <el-option :label="$tf('每 5 分钟')" value="0 */5 * * * ?" />
              <el-option :label="$tf('每 10 分钟')" value="0 */10 * * * ?" />
              <el-option :label="$tf('每 30 分钟')" value="0 */30 * * * ?" />
              <el-option :label="$tf('每小时')" value="0 0 * * * ?" />
            </el-select>
            <span class="kgw-desc">{{ $tt("系统定时扫描业务数据生成通知的频率（重启后端服务生效）") }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 语音提醒卡片（小助手语音播报待审批通知）：客户包设 VITE_VOICE_FEATURES=false 时整卡不显示 -->
    <div v-if="VOICE_FEATURES_ENABLED" class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><Microphone /></el-icon>
        <span>{{ $tt("语音提醒") }}</span>
      </div>
      <div class="kgw-card-body">
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("启用语音提醒：") }}</div>
          <div class="kgw-control">
            <el-switch v-model="cfg.voiceEnabled" />
            <span class="kgw-desc">{{ cfg.voiceEnabled ? '已启用' : '已关闭' }}，{{ cfg.voiceEnabled ? 'AI 小助手会用本机语音播报新的待审批单据' : '不会发出任何声音' }}</span>
          </div>
        </div>
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("最小播报间隔：") }}</div>
          <div class="kgw-control">
            <el-input-number v-model="cfg.voiceMinInterval" :min="10" :max="600" size="small" controls-position="right" style="width:120px" />
            <span class="kgw-desc">{{ $tt("秒；两条播报之间至少间隔该秒数，防止批量通知连续刷屏（默认 60 秒）") }}</span>
          </div>
        </div>
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("播报内容：") }}</div>
          <div class="kgw-control">
            <el-tag size="small" type="info" effect="plain">{{ $tt("待审批单据") }}</el-tag>
            <span class="kgw-desc">{{ $tt("本期固定只播「待审批」通知。播报示例：您有 1 条待审批单据，最新一条 销售订单 SO20260914001，请及时处理。") }}</span>
          </div>
        </div>
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("试听：") }}</div>
          <div class="kgw-control">
            <el-button v-permission="perm('preview')" size="small" :disabled="!speechSupported" @click="onPreviewVoice">🔊 {{ $tt("试听") }}</el-button>
            <span class="kgw-desc" :class="{ 'kgw-desc-danger': !speechSupported }">
              {{ speechSupported ? $tt("点击试听播报效果（需本机已安装中文语音）") : $tt("当前浏览器不支持语音合成，开启后不会发声") }}
            </span>
          </div>
        </div>
        <div class="kgw-tip">
          {{ $tt("提示：语音由本机浏览器发声，需保持系统页面处于打开状态；若听不到声音，请在 Windows「设置 → 时间和语言 → 语音」中添加中文语音包。每位用户可在 AI 小助手面板上单独静音。") }}
        </div>
      </div>
    </div>

    <!-- 说明信息 -->
    <div class="kgw-card kgw-help-card">
      <div class="kgw-card-title">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ $tt("使用说明") }}</span>
      </div>
      <div class="kgw-card-body">
        <ul class="kgw-help-list">
          <li>{{ $tt("系统消息通知功能由右上角铃铛图标集中展示，支持未读 / 已读状态切换。") }}</li>
          <li>通知类型包括：
            <strong>{{ $tt("待审批") }}</strong>（等待您审批的单据）、
            <strong>{{ $tt("已超期") }}</strong>（已超过约定日期）、
            <strong>{{ $tt("即将超期") }}</strong>（剩余天数 ≤ 阈值）、
            <strong>{{ $tt("应收应付跟进") }}</strong>（财务场景，已生效未结清且过到期日）。
          </li>
          <li>覆盖的业务模块：销售（订单/出库/退货/发票）、采购（订单/入库/退货/发票）、库存（调拨/盘点）、生产（工单/工序汇报/生产入库/工艺路线工序）、财务（应收/应付）。</li>
          <li>{{ $tt("点击铃铛中的通知项会自动标记为已读并跳转到对应单据详情页。") }}</li>
          <li>"标记全部已读"按钮会一次性将当前所有未读通知标记为已读。</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:notification-setting')
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Setting, Timer, InfoFilled, Document, WarningFilled, Coin, Microphone } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { previewSpeak, isSpeechSupported } from '@/utils/voiceNotify'
import { VOICE_FEATURES_ENABLED } from '@/config/featureFlags'
import { tt } from '@/i18n'

const saving = ref(false)
// 当前浏览器是否支持语音合成（不支持时试听按钮禁用并给出提示）
const speechSupported = ref(false)
const cfg = ref({
  enabled: true,
  approvalEnabled: true,
  overdueEnabled: true,
  soonOverdueEnabled: true,
  arApEnabled: true,
  soonOverdueDays: 7,
  scanCron: '0 */2 * * * ?',
  voiceEnabled: false,
  voiceMinInterval: 60
})

// 通知类型配置项（2026-09-22：草稿提醒已下线，此处不再出现该卡片）
const typeItems = [
  { key: 'approvalEnabled',     label: '待审批通知',   icon: Document,      color: '#409EFF', desc: '待我审批的单据触发通知' },
  { key: 'overdueEnabled',      label: '已超期通知',   icon: WarningFilled, color: '#F56C6C', desc: '已超过约定日期的单据触发通知' },
  { key: 'soonOverdueEnabled',   label: '即将超期通知', icon: Timer,         color: '#E6A23C', desc: '剩余天数小于阈值的单据触发通知' },
  { key: 'arApEnabled',          label: '应收应付跟进', icon: Coin,           color: '#67C23A', desc: '已生效未结清且过到期日的应收应付触发跟进通知' }
]

// 后端 config_key ↔ 前端字段映射
const KEY_MAP = {
  'notification.enabled':                   'enabled',
  'notification.type.approval.enabled':     'approvalEnabled',
  'notification.type.overdue.enabled':      'overdueEnabled',
  'notification.type.soon_overdue.enabled': 'soonOverdueEnabled',
  'notification.type.soon_overdue.days':    'soonOverdueDays',
  'notification.type.ar_ap.enabled':        'arApEnabled',
  'notification.scan.cron':                 'scanCron',
  'notification.voice.enabled':             'voiceEnabled',
  'notification.voice.minInterval':         'voiceMinInterval'
}

// 类型转换
function coerceValue(key, raw) {
  if (key.endsWith('.enabled')) return String(raw).toLowerCase() === 'true'
  if (key.endsWith('.days') || key.endsWith('.cron') === false && key.includes('.days')) return Number(raw)
  if (key === 'notification.type.soon_overdue.days') return Number(raw)
  return raw
}

async function loadConfig() {
  try {
    const res = await request.get('/sys-config/group/notification')
    // request.js 响应拦截器已解包 data.data，res 直接是 list 数组
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    const map = {}
    for (const item of list) {
      const field = KEY_MAP[item.configKey]
      if (!field) continue
      if (item.configKey.endsWith('.enabled')) {
        map[field] = String(item.configValue).toLowerCase() === 'true'
      } else if (item.configKey === 'notification.type.soon_overdue.days'
              || item.configKey === 'notification.voice.minInterval') {
        map[field] = Number(item.configValue)
      } else {
        map[field] = item.configValue
      }
    }
    cfg.value = { ...cfg.value, ...map }
  } catch (e) {
    ElMessage.error(tt('加载通知规则失败'))
  }
}

async function onSave() {
  saving.value = true
  try {
    // 语音功能被过滤的环境（客户包）：语音卡片不显示，同时不提交语音相关配置项，
    // 避免「卡片看不见但保存时把服务器已有配置改成默认值」的隐性破坏
    const VOICE_KEYS = ['notification.voice.enabled', 'notification.voice.minInterval']
    const payload = Object.entries(KEY_MAP)
      .filter(([key]) => VOICE_FEATURES_ENABLED || VOICE_KEYS.indexOf(key) < 0)
      .map(([key, field]) => {
        const val = cfg.value[field]
        return {
          configKey: key,
          configValue: typeof val === 'boolean' ? String(val) : String(val),
          configGroup: 'notification'
        }
      })
    await request.put('/sys-config/batch', payload)
    ElMessage.success(tt('通知规则已保存'))
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    saving.value = false
  }
}

/** 试听：忽略开关与静音，强制发声，用于确认本机语音是否可用 */
function onPreviewVoice() {
  if (!speechSupported.value) {
    ElMessage.warning(tt('当前浏览器不支持语音合成，无法试听'))
    return
  }
  if (!previewSpeak()) {
    ElMessage.warning(tt('语音播报失败，请检查本机是否已安装中文语音'))
  }
}

onMounted(() => {
  speechSupported.value = isSpeechSupported()
  loadConfig()
})
</script>

<style scoped lang="scss">
.kgw-notification {
  padding: 16px 20px;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
}
.kgw-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  .kgw-hint {
    margin-left: 12px;
    font-size: 12px;
    color: #909399;
  }
}
.kgw-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 16px;
  overflow: hidden;
}
.kgw-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.kgw-card-body {
  padding: 16px;
}
.kgw-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
  &:last-child {
    border-bottom: none;
  }
}
.kgw-label {
  width: 160px;
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
}
.kgw-control {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.kgw-desc {
  color: #909399;
  font-size: 12px;
}
.kgw-desc-danger {
  color: #f56c6c;
}
.kgw-tip {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-left: 3px solid var(--erp-primary, #409EFF);
  border-radius: 3px;
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
}
.kgw-help-card .kgw-card-body {
  background: #fffbe6;
}
.kgw-help-list {
  margin: 0;
  padding-left: 20px;
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
  li {
    margin-bottom: 4px;
  }
  strong {
    color: #303133;
  }
}
</style>
