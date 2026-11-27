<template>
  <div class="settings-panel">
    <el-alert :title="$tt('API Key 将加密存储在数据库中，不会明文显示或硬编码在代码中')" type="info" :closable="false" show-icon style="margin-bottom:16px" />

    <!-- ① 内置服务商 -->
    <div class="provider-card">
      <div class="provider-card-title">{{ $tt("内置服务商") }}</div>
      <div v-for="c in builtinChannels" :key="c.channel" class="provider-row">
        <el-tag :type="c.hasApiKey ? 'success' : 'info'" size="small" effect="plain">
          {{ c.hasApiKey ? '已配置' : '未配置' }}
        </el-tag>
        <span class="provider-name">{{ c.name }}</span>
        <span class="provider-model">{{ c.model }}</span>
        <el-button size="small" text type="primary" v-permission="perm('edit')" @click="openBuiltinConfig(c)">{{ $tt("配置") }}</el-button>
      </div>

      <div v-if="editingBuiltin && currentChannelInfo" class="provider-form">
        <div class="apply-link" v-if="currentChannelInfo.applyUrl">
          申请地址：<a :href="currentChannelInfo.applyUrl" target="_blank" rel="noopener">{{ currentChannelInfo.applyUrl }}</a>
        </div>
        <el-form label-width="90px" size="small">
          <el-form-item label="API Key">
            <el-input
              v-model="apiKeyInput"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="currentChannelInfo.hasApiKey ? $tt('已配置（留空则不修改）') : $tt('请输入API Key')"
              clearable />
          </el-form-item>
          <el-form-item :label="$tf('调用地址')">
            <el-input v-model="baseUrlInput" autocomplete="off" :placeholder="$tt('API基础地址')" clearable />
          </el-form-item>
          <el-form-item :label="$tf('模型名称')">
            <el-input v-model="modelInput" autocomplete="off" :placeholder="$tt('如 qwen-plus / deepseek-flash')" clearable />
          </el-form-item>
        </el-form>
        <div class="config-actions">
          <el-button type="primary" size="small" v-permission="perm('save')" @click="saveApiKey" :loading="savingConfig">{{ $tt("保存") }}</el-button>
          <el-button size="small" v-permission="perm('testConnection')" @click="testConnection" :loading="testingConfig">{{ $tt("测试连接") }}</el-button>
          <el-button size="small" text v-permission="perm('collapse')" @click="editingBuiltin = false">{{ $tt("收起") }}</el-button>
        </div>

        <el-divider content-position="left">{{ $tt("租户自有Key（费用自理·免配额）") }}</el-divider>
        <div class="tenant-key-status">
          <el-tag :type="currentChannelInfo.hasTenantApiKey ? 'success' : 'info'" size="small">
            {{ currentChannelInfo.hasTenantApiKey ? '已配置自有Key' : '未配置（使用平台Key）' }}
          </el-tag>
          <span v-if="currentChannelInfo.hasTenantApiKey" class="masked-key">
              {{ $tt("已配置（界面不回显任何字符）") }}
            </span>
          </div>
          <el-form label-width="90px" size="small" style="margin-top:8px">
            <el-form-item :label="$tf('自有Key')">
              <el-input
                v-model="tenantApiKeyInput"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="$tt('输入租户自有API Key（留空则不修改）')"
                clearable />
          </el-form-item>
        </el-form>
        <div class="config-actions">
          <el-button type="warning" size="small" v-permission="perm('save')" @click="saveTenantApiKey" :loading="savingTenantKey">{{ $tt("保存自有Key") }}</el-button>
          <el-button
            type="danger"
            plain
            size="small"
            v-permission="perm('delete')"
            @click="clearTenantApiKey"
            v-if="currentChannelInfo.hasTenantApiKey">{{ $tt("清除自有Key") }}</el-button>
        </div>
      </div>
    </div>

    <!-- ② 自定义服务商 -->
    <div class="provider-card">
      <div class="provider-card-title">
        <span>{{ $tt("自定义服务商") }}</span>
        <el-button size="small" type="primary" plain v-permission="perm('add')" @click="openProviderForm(null)">{{ $tt("+ 新增服务商") }}</el-button>
      </div>
      <div v-if="customProviders.length === 0" class="provider-empty">
        还没有自定义服务商。点右上角"新增服务商"可接入任意 OpenAI 兼容接口（如火山方舟）。
      </div>
      <div v-for="p in customProviders" :key="p.id" class="provider-row">
        <el-tag :type="p.hasApiKey ? 'success' : 'info'" size="small" effect="plain">
          {{ p.hasApiKey ? '已配置' : '未配置' }}
        </el-tag>
        <span class="provider-name">{{ p.name }}</span>
        <span class="provider-model">{{ (p.models || []).length }} 个模型</span>
        <a v-if="p.applyUrl" :href="p.applyUrl" target="_blank" rel="noopener" class="provider-link">{{ $tt("控制台") }}</a>
        <el-button size="small" text type="primary" v-permission="perm('edit')" @click="openProviderForm(p)">{{ $tt("编辑") }}</el-button>
        <el-button size="small" text type="danger" v-permission="perm('delete')" @click="deleteProvider(p)">{{ $tt("删除") }}</el-button>
      </div>

      <div v-if="editingProvider" class="provider-form">
        <el-form label-width="90px" size="small">
          <el-form-item :label="$tf('厂商名称')" required>
            <el-input v-model="providerForm.name" autocomplete="off" :placeholder="$tt('如 火山方舟')" clearable />
          </el-form-item>
          <el-form-item :label="$tf('调用地址')" required>
            <el-input v-model="providerForm.baseUrl" autocomplete="off" :placeholder="$tt('如 https://ark.cn-beijing.volces.com/api/v3')" clearable />
          </el-form-item>
          <el-form-item label="API Key" :required="!providerForm.id">
            <el-input
              v-model="providerForm.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="providerForm.id ? '留空不修改' : '请输入API Key'"
              clearable />
          </el-form-item>
          <el-form-item :label="$tf('模型列表')" required>
            <el-input
              v-model="providerForm.modelsText"
              type="textarea"
              autocomplete="off"
              :rows="3"
              :placeholder="$tt('每行一个，或用英文逗号分隔')" />
          </el-form-item>
          <el-form-item :label="$tf('视觉模型')">
            <el-input v-model="providerForm.visionModel" autocomplete="off" :placeholder="$tt('选填，图片识别用；不填则复用所选模型')" clearable />
          </el-form-item>
          <el-form-item :label="$tf('联网搜索')">
            <el-switch v-model="providerForm.webSearch" />
            <div class="pref-hint">{{ $tt("该服务商接口支持联网时才勾选；与上方「对话能力-联网搜索」开关同时开启才生效") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('厂商链接')">
            <el-input v-model="providerForm.applyUrl" autocomplete="off" :placeholder="$tt('选填，厂商控制台/申请地址')" clearable />
          </el-form-item>
        </el-form>
        <div class="config-actions">
          <el-button type="primary" size="small" v-permission="perm('save')" @click="saveProvider" :loading="savingProvider">{{ $tt("保存") }}</el-button>
          <el-button size="small" v-permission="perm('testConnection')" @click="testProvider" :loading="testingProvider">{{ $tt("测试连接") }}</el-button>
          <el-button size="small" text v-permission="perm('dlgCancel')" @click="editingProvider = false">{{ $tt("取消") }}</el-button>
        </div>
      </div>
    </div>

    <!-- ③ 对话能力（模型级能力：联网搜索 / 回答严谨度 / 回答详细度 / 上下文上限） -->
    <div class="provider-card">
      <div class="provider-card-title">{{ $tt("对话能力") }}</div>
      <el-form label-width="92px" size="small">
        <el-form-item :label="$tf('联网搜索')">
          <el-switch v-model="chatCaps.webSearch" @change="saveChatCaps" />
          <div class="pref-hint">{{ $tt("开启后小楷可联网检索公开资料；仅对支持联网的服务商生效（DeepSeek 官方接口暂不支持）") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('回答严谨度')">
          <el-select v-model="chatCaps.temperature" class="pref-select" @change="saveChatCaps">
            <el-option :value="0.1" :label="$tt('最严谨（0.1）')" />
            <el-option :value="0.3" :label="$tt('严谨（0.3，默认）')" />
            <el-option :value="0.6" :label="$tt('均衡（0.6）')" />
            <el-option :value="1" :label="$tt('发散（1）')" />
          </el-select>
          <div class="pref-hint">{{ $tt("数值越低回答越稳重准确，越高越发散有创意") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('回答详细度')">
          <el-select v-model="chatCaps.maxTokens" class="pref-select" @change="saveChatCaps">
            <el-option :value="1000" :label="$tt('简短（1000）')" />
            <el-option :value="2000" :label="$tt('适中（2000，默认）')" />
            <el-option :value="4000" :label="$tt('详细（4000）')" />
            <el-option :value="8192" :label="$tt('最详细（8192）')" />
          </el-select>
          <div class="pref-hint">{{ $tt("小楷单次回答的篇幅上限") }}</div>
        </el-form-item>
        <!-- 上下文窗口（2026-11-22）：改为小楷对话框内的三档个人选择（照参考设计），此处不再要求用户填数字；
             后端配置值保留为全局默认回退，本面板只透传不修改 -->
      </el-form>
    </div>

    <!-- ④ 展示与播报（用户自选：单据标识方式 / 播报字数 —— 只影响文案口径，不涉及权限） -->
    <div class="provider-card">
      <div class="provider-card-title">{{ $tt("展示与播报") }}</div>
      <el-form label-width="92px" size="small">
        <el-form-item :label="$tf('单据标识')">
          <el-select v-model="billLabelStyle" class="pref-select" @change="onBillLabelStyleChange">
            <el-option value="code" :label="$tt('单号（如 SO-20260914-0005）')" />
            <el-option value="name" :label="$tt('客户名（如 深圳远大智能科技有限公司）')" />
            <el-option value="both" :label="$tt('单号+客户名')" />
          </el-select>
          <div class="pref-hint">{{ $tt("确认卡片、执行结果、语音播报里怎么称呼一张单据（不再念内部编号 ID）") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('播报字数')">
          <el-select v-model="speakLimit" class="pref-select" @change="onSpeakLimitChange">
            <el-option :value="20" :label="$tt('20 字（最短）')" />
            <el-option :value="30" :label="$tt('30 字')" />
            <el-option :value="40" :label="$tt('40 字（推荐）')" />
            <el-option :value="60" :label="$tt('60 字（最详细）')" />
          </el-select>
          <div class="pref-hint">{{ $tt("语音播报一句话最多念多少字，超出自动截断") }}</div>
        </el-form-item>
        <!-- 播报语速：纯语音能力设置，客户包（VITE_VOICE_FEATURES=false）整项不显示（与 A 口径一致：121 保持改造前界面） -->
        <el-form-item v-if="VOICE_FEATURES_ENABLED" :label="$tf('播报语速')">
          <el-select v-model="speakRate" class="pref-select" @change="onSpeakRateChange">
            <el-option :value="0.8" :label="$tt('0.8 慢（听得清）')" />
            <el-option :value="1" :label="$tt('1.0 标准（默认）')" />
            <el-option :value="1.2" :label="$tt('1.2 稍快')" />
            <el-option :value="1.5" :label="$tt('1.5 快')" />
          </el-select>
          <div class="pref-hint">{{ $tt("语速只影响本机发声快慢，只在你当前电脑生效") }}</div>
        </el-form-item>
        <!-- 播报语音：选一个说话的声音，"自然女声" 走服务器离线引擎；后续想加更多音色只需新增模型文件 + voiceList 条目 -->
        <el-form-item v-if="VOICE_FEATURES_ENABLED" :label="$tf('播报语音')">
          <div style="display:flex; align-items:center; gap:8px; width:100%;">
            <el-select v-model="voiceName" class="pref-select" style="flex:1;" @change="onVoiceNameChange">
              <el-option v-for="v in voiceList" :key="v.value" :value="v.value" :label="$tf(v.label)" />
            </el-select>
            <el-button size="small" @click="previewVoice">{{ $tf('试听') }}</el-button>
          </div>
          <div class="pref-hint">{{ $tt("选了就立刻生效；服务器离线引擎没启动时自动退回到浏览器内置音色") }}</div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
/**
 * 小楷助手「模型服务商 / 密钥 / 展示与播报」设置面板（可独立成组件的面板内容）。
 *
 * 用途：小助手 ⚙️ 设置弹窗与 设置中心 → AI → AI 助手设置 两处共用同一份面板，
 *       保证两处布局、文案、交互完全一致（弹窗外壳由各自调用方提供）。
 * 数据：自己 onMounted 拉取内置渠道与自定义服务商；API 全部来自 @/api/ai/modelChannel。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import {
  listChannels as apiListChannels,
  saveChannelApiKey as apiSaveChannelApiKey,
  saveChannelConfig as apiSaveChannelConfig,
  testChannel as apiTestChannel,
  saveTenantApiKey as apiSaveTenantApiKey,
  clearTenantApiKey as apiClearTenantApiKey,
  listProviders as apiListProviders,
  saveProvider as apiSaveProvider,
  deleteProvider as apiDeleteProvider,
  testProvider as apiTestProvider,
  getChatCapabilities as apiGetChatCapabilities,
  saveChatCapabilities as apiSaveChatCapabilities
} from '@/api/ai/modelChannel'
import { VOICE_FEATURES_ENABLED } from '@/config/featureFlags'
import { useAiDisplayPrefs } from '@/composables/useAiDisplayPrefs'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:ai-assistant-settings')


const {
  billLabelStyle,
  speakLimit,
  speakRate,
  voiceName,
  voiceList,
  onBillLabelStyleChange,
  onSpeakLimitChange,
  onSpeakRateChange,
  onVoiceNameChange,
  previewVoice
} = useAiDisplayPrefs()

// ===== 内置服务商 =====
const builtinChannels = ref([])
const editingBuiltin = ref(false)
const configChannel = ref('QWEN')
const apiKeyInput = ref('')
const baseUrlInput = ref('')
const modelInput = ref('')
const savingConfig = ref(false)
const testingConfig = ref(false)
const currentChannelInfo = computed(() => {
  return builtinChannels.value.find(c => c.channel === configChannel.value)
})

// ===== 自定义服务商 =====
const customProviders = ref([])
const editingProvider = ref(false)
const savingProvider = ref(false)
const testingProvider = ref(false)
const providerForm = ref({ id: '', name: '', baseUrl: '', apiKey: '', modelsText: '', visionModel: '', webSearch: false, applyUrl: '' })

// ===== 对话能力（联网搜索 / 回答严谨度 / 回答详细度 / 上下文上限） =====
const chatCaps = ref({ webSearch: false, temperature: 0.3, maxTokens: 2000, contextMaxTokens: 128000 })

// 加载对话能力设置（接口缺省字段时保留本地默认值）
const loadChatCaps = async () => {
  try {
    const data = await apiGetChatCapabilities()
    if (data) {
      chatCaps.value = { ...chatCaps.value, ...data }
    }
  } catch (e) {
    // 拦截器已统一提示；卡片仍按默认值展示
  }
}

// 保存对话能力设置（下拉/开关/输入框 @change 即时保存，与「展示与播报」卡片同模式）
const saveChatCaps = async () => {
  try {
    // 上下文上限：允许 8000~1000000，非法输入回退 128000（后端同样会做范围校验）
    let ctx = parseInt(String(chatCaps.value.contextMaxTokens).replace(/[^\d]/g, ''), 10)
    if (!ctx || ctx < 8000 || ctx > 1000000) ctx = 128000
    chatCaps.value.contextMaxTokens = ctx
    await apiSaveChatCapabilities({
      webSearch: !!chatCaps.value.webSearch,
      temperature: Number(chatCaps.value.temperature) || 0.3,
      maxTokens: Number(chatCaps.value.maxTokens) || 2000,
      contextMaxTokens: ctx
    })
    ElMessage.success(tt('对话能力已保存'))
  } catch (e) {
    // 拦截器已统一提示
  }
}

// ===== 租户自有 Key =====
const tenantApiKeyInput = ref('')
const savingTenantKey = ref(false)

// 展开某个内置服务商的配置表单
const openBuiltinConfig = (c) => {
  configChannel.value = c.channel
  onConfigChannelChange(c.channel)
  editingBuiltin.value = true
}

// 加载内置渠道配置信息
const loadChannelsInfo = async () => {
  try {
    const data = await apiListChannels()
    builtinChannels.value = data || []
    onConfigChannelChange(configChannel.value)
  } catch (e) {
    console.error('加载渠道配置失败', e)
  }
}

// 加载自定义服务商列表
const loadProviders = async () => {
  try {
    const data = await apiListProviders()
    customProviders.value = data || []
  } catch (e) {
    console.error('加载服务商失败', e)
  }
}

// 切换配置渠道时，填充当前渠道的配置信息
const onConfigChannelChange = (ch) => {
  const info = builtinChannels.value.find(c => c.channel === ch)
  if (info) {
    apiKeyInput.value = ''
    baseUrlInput.value = info.baseUrl || ''
    modelInput.value = info.model || ''
  }
}

// 保存：API Key 可留空（只改模型名称时不需重填）；模型名称会一并持久化，
// 否则"界面上改了模型但没生效"（用户 2026-09-28：我选哪个模型就用哪个模型）
const saveApiKey = async () => {
  const keyInput = apiKeyInput.value.trim()
  const modelInputVal = modelInput.value.trim()
  const modelChanged = !!modelInputVal && modelInputVal !== (currentChannelInfo.value?.model || '')
  if (!keyInput && !modelChanged) {
    ElMessage.warning(tt('请输入API Key，或修改模型名称后再保存'))
    return false
  }
  savingConfig.value = true
  try {
    if (keyInput) {
      await apiSaveChannelApiKey(configChannel.value, keyInput)
    }
    if (modelChanged) {
      // 调用地址传空 = 后端跳过（仅管理员可改调用地址，这里不涉及）
      await apiSaveChannelConfig(configChannel.value, '', modelInputVal)
    }
    ElMessage.success(tt('保存成功（API Key 已加密存储）'))
    apiKeyInput.value = ''
    await loadChannelsInfo()
    return true
  } catch (e) {
    return false
  } finally {
    savingConfig.value = false
  }
}

// 测试连接
const testConnection = async () => {
  // 如果有输入新的API Key，先保存
  if (apiKeyInput.value.trim()) {
    const saved = await saveApiKey()
    if (!saved) return
  }

  // 同时保存baseUrl和model配置
  if (baseUrlInput.value.trim() || modelInput.value.trim()) {
    try {
      await apiSaveChannelConfig(configChannel.value, baseUrlInput.value.trim(), modelInput.value.trim())
    } catch (e) { return }
  }

  testingConfig.value = true
  try {
    const data = await apiTestChannel(configChannel.value)
    if (data && data.success) {
      ElMessage.success(tt('连接测试通过: ') + data.message)
    } else {
      ElMessage.warning(tt('测试结果: ') + (data?.message || tt('未知')))
    }
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    testingConfig.value = false
  }
}

// 打开自定义服务商表单（p=null 为新增）
const openProviderForm = (p) => {
  if (p) {
    providerForm.value = {
      id: p.id || '',
      name: p.name || '',
      baseUrl: p.baseUrl || '',
      apiKey: '',
      modelsText: Array.isArray(p.models) ? p.models.join('\n') : '',
      visionModel: p.visionModel || '',
      webSearch: !!p.webSearch,
      applyUrl: p.applyUrl || ''
    }
  } else {
    providerForm.value = { id: '', name: '', baseUrl: '', apiKey: '', modelsText: '', visionModel: '', webSearch: false, applyUrl: '' }
  }
  editingProvider.value = true
}

// 保存自定义服务商
const saveProvider = async () => {
  const f = providerForm.value
  if (!f.name.trim()) { ElMessage.warning(tt('请填写厂商名称')); return }
  if (!f.baseUrl.trim()) { ElMessage.warning(tt('请填写调用地址')); return }
  if (!f.id && !f.apiKey.trim()) { ElMessage.warning(tt('请填写 API Key')); return }
  const models = f.modelsText.split(/[,，\n]/).map(s => s.trim()).filter(Boolean)
  if (models.length === 0) { ElMessage.warning(tt('至少填写一个模型名称')); return }
  savingProvider.value = true
  try {
    await apiSaveProvider({
      id: f.id || null,
      name: f.name.trim(),
      baseUrl: f.baseUrl.trim(),
      apiKey: f.apiKey.trim(),
      models,
      visionModel: f.visionModel.trim(),
      webSearch: !!f.webSearch,
      applyUrl: f.applyUrl.trim()
    })
    ElMessage.success(tt('服务商已保存'))
    editingProvider.value = false
    await loadProviders()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    savingProvider.value = false
  }
}

// 测试自定义服务商连通性
const testProvider = async () => {
  const f = providerForm.value
  testingProvider.value = true
  try {
    const data = await apiTestProvider({
      id: f.id || null,
      baseUrl: f.baseUrl.trim(),
      apiKey: f.apiKey.trim(),
      model: f.modelsText.split(/[,，\n]/).map(s => s.trim()).filter(Boolean)[0] || ''
    })
    if (data && data.success) ElMessage.success(tt('连接测试通过: ') + data.message)
    else ElMessage.warning(tt('测试结果: ') + (data?.message || tt('未知')))
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    testingProvider.value = false
  }
}

// 删除自定义服务商
const deleteProvider = async (p) => {
  try {
    await ElMessageBox.confirm(`${tt("确定删除服务商「")}${p.name}${tt("」吗？")}`, tt('提示'), { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await apiDeleteProvider(p.id)
    ElMessage.success(tt('已删除'))
    if (providerForm.value.id === p.id) editingProvider.value = false
    await loadProviders()
  } catch (e) {
    // 拦截器已统一提示
  }
}

// 租户自有Key
const saveTenantApiKey = async () => {
  if (!tenantApiKeyInput.value.trim()) {
    ElMessage.warning(tt('请输入租户自有API Key'))
    return
  }
  savingTenantKey.value = true
  try {
    await apiSaveTenantApiKey(configChannel.value, tenantApiKeyInput.value.trim())
    ElMessage.success(tt('租户自有Key已保存（优先使用，免配额限制）'))
    tenantApiKeyInput.value = ''
    await loadChannelsInfo()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    savingTenantKey.value = false
  }
}

const clearTenantApiKey = async () => {
  try {
    await apiClearTenantApiKey(configChannel.value)
    ElMessage.success(tt('租户自有Key已清除（恢复使用平台Key）'))
    await loadChannelsInfo()
  } catch (e) {
    // 拦截器已统一提示
  }
}

onMounted(() => {
  loadChannelsInfo()
  loadProviders()
  loadChatCaps()
})
</script>

<style scoped lang="scss">
/* 设置面板样式（原小助手 ⚙️ 弹窗样式原样搬入，两处入口保持一致） */
.settings-panel {
  padding: 0 4px;
}

.masked-key {
  font-size: 12px;
  color: #6b7280;
  font-family: monospace;
}

.apply-link {
  font-size: 12px;
  color: #6b7280;
}

.apply-link a {
  color: #2563eb;
  text-decoration: none;
}

.apply-link a:hover {
  text-decoration: underline;
}

.config-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 20px;
}

.provider-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #fafafa;
}
/* 展示与播报偏好：下拉定宽 + 灰色说明文案 */
.pref-select {
  width: 260px;
}
.pref-hint {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
  margin-top: 4px;
}
.provider-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.provider-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}
.provider-row:last-child {
  border-bottom: none;
}
.provider-name {
  font-weight: 500;
  color: #303133;
}
.provider-model {
  color: #909399;
  font-size: 12px;
}
.provider-link {
  color: #409eff;
  font-size: 12px;
  text-decoration: none;
}
.provider-empty {
  font-size: 12px;
  color: #909399;
  padding: 8px 0;
}
.provider-form {
  margin-top: 10px;
  padding: 12px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
</style>
