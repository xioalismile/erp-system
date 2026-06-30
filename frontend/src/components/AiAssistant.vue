<template>
  <!-- 浮动AI按钮 -->
  <div class="ai-fab" @click="visible = true" v-show="!visible">
    🤖 AI助手
  </div>

  <!-- AI助手面板 -->
  <el-drawer v-model="visible" title="🤖 ERP智能助手" direction="rtl" size="420px" :with-header="true">
    <!-- 渠道切换 -->
    <div class="channel-tabs">
      <el-tag :type="channel === 'QWEN' ? 'primary' : 'info'" effect="dark" size="small" @click="channel = 'QWEN'">通义千问</el-tag>
      <el-tag :type="channel === 'DEEPSEEK' ? 'primary' : 'info'" effect="plain" size="small" @click="channel = 'DEEPSEEK'">DeepSeek</el-tag>
    </div>

    <!-- 对话区 -->
    <div class="chat-area" ref="chatArea">
      <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
        <div class="msg-role">{{ msg.role === 'user' ? '👤 我' : '🤖 助手' }}</div>
        <div class="msg-text">{{ msg.text }}</div>
        <div v-if="msg.action" class="msg-action">
          <el-alert :title="msg.action" type="success" :closable="false" show-icon />
        </div>
      </div>
      <div v-if="loading" class="msg bot">
        <div class="msg-role">🤖 助手</div>
        <div class="msg-text">思考中...</div>
      </div>
    </div>

    <!-- 确认区 -->
    <div v-if="pendingConfirm" class="confirm-area">
      <el-input v-model="confirmToken" placeholder="输入6位验证码确认" style="width:180px" />
      <el-button type="primary" @click="doConfirm">确认操作</el-button>
    </div>

    <!-- 快捷指令 -->
    <div class="quick-cmds">
      <el-button text size="small" @click="sendQuick('查一下本月所有订单')">查本月订单</el-button>
      <el-button text size="small" @click="sendQuick('查一下MAT-001的库存')">查库存</el-button>
      <el-button text size="small" @click="sendQuick('帮我在华南精密制造下100个精密轴承A，单价25.5元，7月15号交货')">AI下单</el-button>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <el-input v-model="input" placeholder="输入指令或问题..." @keyup.enter="send" />
      <el-button type="primary" @click="send" :loading="loading">发送</el-button>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const visible = ref(false)
const channel = ref('QWEN')
const input = ref('')
const loading = ref(false)
const messages = ref([
  { role: 'bot', text: '您好！我是ERP智能助手。可以帮您创建订单、查询库存、生成报表。试试说"帮我在华南精密制造下100个精密轴承A"' }
])
const pendingConfirm = ref(false)
const confirmToken = ref('')
const chatArea = ref(null)

const send = async () => {
  if (!input.value.trim()) return
  const userMsg = input.value
  messages.value.push({ role: 'user', text: userMsg })
  input.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const res = await api.post('/ai/chat', { message: userMsg, channel: channel.value })
    if (res.code === 200) {
      const data = res.data
      let actionInfo = ''
      if (data.actionResult) {
        if (data.actionResult.orderNo) actionInfo = `订单已创建: ${data.actionResult.orderNo}`
        else if (data.actionResult.orders) actionInfo = `查询到 ${data.actionResult.total} 条记录`
        else if (data.actionResult.inventory) actionInfo = `查询到 ${data.actionResult.total} 条库存`
      }
      messages.value.push({ role: 'bot', text: data.replyText, action: actionInfo })
      if (data.needConfirm) {
        pendingConfirm.value = true
      }
    } else {
      messages.value.push({ role: 'bot', text: res.message || '处理失败' })
    }
  } catch (e) {
    messages.value.push({ role: 'bot', text: '服务异常，请稍后重试' })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const sendQuick = (text) => {
  input.value = text
  send()
}

const doConfirm = async () => {
  if (!confirmToken.value) return
  try {
    const res = await api.post('/ai/confirm', { confirmToken: confirmToken.value })
    if (res.code === 200) {
      ElMessage.success('操作成功!')
      messages.value.push({ role: 'bot', text: '✅ 操作已完成: ' + (res.data.replyText || '成功') })
      pendingConfirm.value = false
      confirmToken.value = ''
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) { /* handled */ }
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
}
</script>

<style scoped>
.ai-fab {
  position: fixed; right: 24px; bottom: 24px;
  background: linear-gradient(135deg, #7c3aed, #6366f1);
  color: #fff; padding: 12px 24px; border-radius: 30px;
  cursor: pointer; box-shadow: 0 4px 20px rgba(124,58,237,0.4);
  z-index: 999; font-size: 14px; font-weight: 600; transition: transform .2s;
}
.ai-fab:hover { transform: scale(1.05); }
.channel-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.chat-area { height: calc(100vh - 280px); overflow-y: auto; padding: 10px; }
.msg { margin-bottom: 16px; }
.msg-role { font-size: 12px; font-weight: 600; margin-bottom: 4px; }
.msg.user .msg-role { color: #2563eb; }
.msg.bot .msg-role { color: #7c3aed; }
.msg-text { background: #f3f4f6; padding: 10px 14px; border-radius: 8px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; color: #374151; }
.msg-action { margin-top: 6px; }
.confirm-area { display: flex; gap: 8px; padding: 10px; background: #fef3c7; border-radius: 8px; margin: 10px 0; }
.quick-cmds { display: flex; flex-wrap: wrap; gap: 4px; padding: 8px 0; border-top: 1px solid #e5e7eb; }
.input-area { display: flex; gap: 8px; padding-top: 10px; border-top: 1px solid #e5e7eb; }
</style>
