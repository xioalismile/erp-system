<template>
  <div class="kgw-popup-notice">
    <!-- 顶部工具栏 -->
    <div class="kgw-toolbar">
      <el-button v-permission="perm('save')" type="primary" size="small" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
      <el-button v-permission="perm('refresh')" size="small" @click="loadConfig">{{ $tt("刷新") }}</el-button>
      <span class="kgw-hint">{{ $tt("提示：修改后请点击保存，立即生效") }}</span>
    </div>

    <!-- 总开关卡片 -->
    <div class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><ChatDotRound /></el-icon>
        <span>{{ $tt("弹窗提醒总开关") }}</span>
      </div>
      <div class="kgw-card-body">
        <div class="kgw-row">
          <div class="kgw-label">{{ $tt("启用弹窗提醒：") }}</div>
          <div class="kgw-control">
            <el-switch v-model="cfg.popupEnabled" />
            <span class="kgw-desc">{{ cfg.popupEnabled ? '已启用' : '已关闭' }}，{{ cfg.popupEnabled ? '有新提醒时屏幕右下角弹出提醒卡片' : '只在顶栏铃铛里显示红点，不弹窗' }}</span>
          </div>
        </div>
        <div class="kgw-tip">
          {{ $tt("弹窗由顶栏铃铛的未读检测驱动；同一条提醒只弹一次；点击弹窗的去处理会跳到对应单据并转为已读。") }}
        </div>
      </div>
    </div>

    <!-- 提醒类型卡片 -->
    <div class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><Setting /></el-icon>
        <span>{{ $tt("提醒类型与弹窗内容") }}</span>
      </div>
      <div class="kgw-card-body">
        <div v-for="t in typeItems" :key="t.key" class="kgw-row">
          <div class="kgw-label">
            <el-icon :color="t.color"><component :is="t.icon" /></el-icon>
            <span>{{ $tf(t.label) }}：</span>
          </div>
          <div class="kgw-control kgw-control-col">
            <div class="kgw-control-line">
              <el-switch v-model="cfg[t.enabledField]" />
              <span class="kgw-desc">{{ t.desc }}</span>
            </div>
            <div class="kgw-control-line kgw-template-line">
              <span class="kgw-template-label">{{ $tt("弹窗内容") }}</span>
              <el-input
                v-model="cfg[t.templateField]"
                type="textarea"
                :rows="2"
                :placeholder="t.placeholder"
                :disabled="!cfg[t.enabledField]"
                class="kgw-template-input"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 占位符说明卡片 -->
    <div class="kgw-card">
      <div class="kgw-card-title">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ $tt("弹窗内容写法说明") }}</span>
      </div>
      <div class="kgw-card-body">
        <ul class="kgw-help-list">
          <li><strong>{数量}</strong> —— 该类提醒当前的未读条数</li>
          <li><strong>{单据类型}</strong> —— 最新一条提醒的单据中文名，如 销售订单</li>
          <li><strong>{单号}</strong> —— 最新一条提醒的单据编号</li>
          <li><strong>{内容}</strong> —— 该条提醒的正文摘要</li>
        </ul>
      </div>
    </div>

    <!-- 使用说明卡片 -->
    <div class="kgw-card kgw-help-card">
      <div class="kgw-card-title">
        <el-icon><QuestionFilled /></el-icon>
        <span>{{ $tt("使用说明") }}</span>
      </div>
      <div class="kgw-card-body">
        <ul class="kgw-help-list">
          <li>{{ $tt("弹窗数据来自系统提醒模块，与顶栏铃铛、消息中心完全同源。") }}</li>
          <li>{{ $tt("提醒弹窗在屏幕中央显示，一次一张排队展示，处理完或关闭后自动显示下一条。") }}</li>
          <li>{{ $tt("待审批弹窗上可直接点通过或驳回：通过即生效，驳回需填写原因，单据退回草稿。") }}</li>
          <li>{{ $tt("审批结果、工序通知弹窗提供去处理按钮，跳转到对应单据办理。") }}</li>
          <li>{{ $tt("提交单据、审批操作后提醒即时弹出，无需等待。") }}</li>
          <li>{{ $tt("总开关关闭时任何类型都不弹窗；各类型开关只在总开关开启时生效。") }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:popup-setting')
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Setting, InfoFilled, Document, CircleCheck, Promotion, QuestionFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { tt } from '@/i18n'

const saving = ref(false)
const cfg = ref({
  popupEnabled: false,
  approvalEnabled: false,
  approvalTemplate: '您有 {数量} 张待审批单据，请及时处理。',
  approvalFinishEnabled: false,
  approvalFinishTemplate: '您的单据已完成审批：{单据类型} {单号}',
  operationMentionEnabled: false,
  operationMentionTemplate: '工序通知：{单号} {内容}'
})

// 提醒类型配置项（与后台提醒模块 type 对应：approval=待审批 / approval_finish=审批结果 / operation_mention=工序通知含@）
const typeItems = [
  { key: 'approval',         label: '待审批提醒',   icon: Document,   color: '#409EFF', desc: '有新的待审批提醒时弹窗',   enabledField: 'approvalEnabled',         templateField: 'approvalTemplate',         placeholder: '您有 {数量} 张待审批单据，请及时处理。' },
  { key: 'approval_finish',  label: '审批结果提醒', icon: CircleCheck, color: '#67C23A', desc: '审批通过或驳回时弹窗',     enabledField: 'approvalFinishEnabled',   templateField: 'approvalFinishTemplate',   placeholder: '您的单据已完成审批：{单据类型} {单号}' },
  { key: 'operation_mention',label: '工序通知',     icon: Promotion,  color: '#E6A23C', desc: '收到工序通知或被@时弹窗', enabledField: 'operationMentionEnabled', templateField: 'operationMentionTemplate', placeholder: '工序通知：{单号} {内容}' }
]

// 后端 config_key ↔ 前端字段映射
const KEY_MAP = {
  'notification.popup.enabled':                            'popupEnabled',
  'notification.popup.type.approval.enabled':              'approvalEnabled',
  'notification.popup.type.approval.template':             'approvalTemplate',
  'notification.popup.type.approval_finish.enabled':       'approvalFinishEnabled',
  'notification.popup.type.approval_finish.template':      'approvalFinishTemplate',
  'notification.popup.type.operation_mention.enabled':     'operationMentionEnabled',
  'notification.popup.type.operation_mention.template':    'operationMentionTemplate'
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
      } else {
        map[field] = item.configValue
      }
    }
    cfg.value = { ...cfg.value, ...map }
  } catch (e) {
    ElMessage.error(tt('加载弹窗设置失败'))
  }
}

async function onSave() {
  saving.value = true
  try {
    const payload = Object.entries(KEY_MAP).map(([key, field]) => ({
      configKey: key,
      configValue: String(cfg.value[field]),
      configGroup: 'notification'
    }))
    await request.put('/sys-config/batch', payload)
    ElMessage.success(tt('弹窗设置已保存'))
    // 广播给全局弹窗组件（components/PopupNotify.vue 监听同名事件）：保存立即生效，无需刷新页面
    try { window.dispatchEvent(new CustomEvent('erp:popup-config-changed')) } catch (e) { /* 忽略 */ }
  } catch (e) {
    ElMessage.error(tt('保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped lang="scss">
.kgw-popup-notice {
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
  align-items: flex-start;
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
  padding-top: 2px;
}
.kgw-control {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.kgw-control-col {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.kgw-control-line {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kgw-template-line {
  width: 100%;
}
.kgw-template-label {
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}
.kgw-template-input {
  max-width: 560px;
}
.kgw-desc {
  color: #909399;
  font-size: 12px;
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
