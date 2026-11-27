<template>
  <div class="kgw-logistics-config">
    <!-- 首次配置向导（未配置授权码时显示） -->
    <el-card shadow="never" class="guide-card" v-if="!credentials.configured">
      <template #header>
        <div class="guide-card-header">
          <el-icon style="color:#e6a23c;"><WarningFilled /></el-icon>
          <span style="font-weight:600;">{{ $tt("首次配置向导（共3步，约5分钟完成）") }}</span>
        </div>
      </template>
      <el-steps :active="0" align-center style="margin-bottom:16px;">
        <el-step :title="$tt('注册百递云API')" :description="$tt('访问 api.kuaidi100.com 注册开发者账号（免费100次/日）')" />
        <el-step :title="$tt('获取授权信息')" :description="$tt('控制台 → 我的授权 → 复制 customer + key')" />
        <el-step :title="$tt('填入并测试')" :description="$tt('在下方填入授权码+密钥 → 保存 → 测试连接')" />
      </el-steps>
      <div style="text-align:center;">
        <el-button v-permission="perm('view')" type="primary" tag="a" href="https://api.kuaidi100.com" target="_blank">{{ $tt("前往百递云API平台注册 →") }}</el-button>
      </div>
    </el-card>

    <!-- 安全提示 -->
    <el-alert
      type="warning"
      :closable="false"
      style="margin-bottom:16px;"
    >
      <div style="line-height:1.6;">
        <strong>{{ $tt("授权码属于敏感信息，仅保存在数据库，不会以明文形式返回。") }}</strong><br />
        未注册百递云API账号？<el-link type="primary" href="https://api.kuaidi100.com" target="_blank" :underline="false" style="vertical-align:baseline;">{{ $tt("前往百递云API开放平台注册 →") }}</el-link>
      </div>
    </el-alert>

    <!-- 当前状态卡片 -->
    <div class="status-card">
      <div class="status-item">
        <div class="status-label">{{ $tt("查询功能") }}</div>
        <div class="status-value">
          <el-tag :type="credentials.enabled ? 'success' : 'info'" size="small">
            {{ credentials.enabled ? '已启用' : '已关闭' }}
          </el-tag>
        </div>
      </div>
      <div class="status-item">
        <div class="status-label">{{ $tt("授权码状态") }}</div>
        <div class="status-value">
          <el-tag :type="credentials.configured ? 'success' : 'danger'" size="small">
            {{ credentials.configured ? '已配置' : '未配置' }}
          </el-tag>
          <span v-if="credentials.configured" class="status-masked">{{ credentials.customerMasked }}</span>
        </div>
      </div>
      <div v-if="credentials.updatedAt" class="status-item">
        <div class="status-label">{{ $tt("最后更新") }}</div>
        <div class="status-value">{{ credentials.updatedAt }}</div>
      </div>
    </div>

    <!-- 配置表单 -->
    <el-form label-width="160px" class="config-form" style="margin-top:16px;">
      <el-form-item :label="$tf('物流查询功能')">
        <el-switch v-model="credentialsForm.enabled" @change="onKd100EnabledChange" />
        <span class="item-remark">{{ $tt("改动即保存；关闭后所有物流查询接口将返回未启用提示") }}</span>
      </el-form-item>

      <el-form-item :label="$tf('快递100授权码')">
        <el-input
          v-model="credentialsForm.customer"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="$tt('请输入快递100授权码 customer')"
          style="width:360px"
        />
        <span class="item-remark">{{ $tt("留空不更新（避免误清空已配置的授权码）") }}</span>
      </el-form-item>

      <el-form-item :label="$tf('签名密钥')">
        <el-input
          v-model="credentialsForm.key"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="$tt('请输入签名密钥 key（免费查询接口可不填）')"
          style="width:360px"
        />
        <span class="item-remark">{{ $tt("免费实时查询接口可不填，订阅类高级接口需要") }}</span>
      </el-form-item>

      <el-form-item>
        <el-button v-permission="perm('save')" type="primary" :loading="savingCredentials" @click="onSaveCredentials">{{ $tt("保存授权配置") }}</el-button>
        <el-button v-permission="perm('testConnection')" :loading="testing" :disabled="!credentials.configured" @click="onTestCredentials">{{ $tt("测试连接") }}</el-button>
        <el-button v-permission="perm('refresh')" @click="onLoadCredentials">{{ $tt("重新加载") }}</el-button>
      </el-form-item>
    </el-form>

    <!-- 测试结果 -->
    <el-alert
      v-if="testResult"
      :type="testResult.success ? 'success' : 'error'"
      :closable="false"
      style="margin-top:12px;"
      :title="testResult.success ? '连接成功' : '连接失败'"
    >
      {{ testResult.message }}
    </el-alert>

    <el-divider />

    <!-- ===== 17TRACK 国际物流 ===== -->
    <div class="form-header" style="margin-top:8px;">
      <span class="form-title">{{ $tt("17TRACK 国际物流") }}</span>
      <span class="form-tip">{{ $tt("国际承运商查询（可选，按次消耗17TRACK额度）") }}</span>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:16px;">
      <div style="line-height:1.6;">
        <strong>{{ $tt("17TRACK 用于国际物流查询（DHL / UPS / FedEx / USPS / 国际EMS 等）。") }}</strong><br />
        未注册？<el-link type="primary" href="https://api.17track.net" target="_blank" :underline="false" style="vertical-align:baseline;">{{ $tt("前往 17TRACK 开放平台注册 →") }}</el-link>
        注册后登录后台「设置页」复制 API 密钥(17token)，填入下方保存即可。
      </div>
    </el-alert>

    <div class="status-card">
      <div class="status-item">
        <div class="status-label">{{ $tt("国际查询功能") }}</div>
        <div class="status-value">
          <el-tag :type="t17.enabled ? 'success' : 'info'" size="small">
            {{ t17.enabled ? '已启用' : '已关闭' }}
          </el-tag>
        </div>
      </div>
      <div class="status-item">
        <div class="status-label">{{ $tt("密钥状态") }}</div>
        <div class="status-value">
          <el-tag :type="t17.configured ? 'success' : 'danger'" size="small">
            {{ t17.configured ? '已配置' : '未配置' }}
          </el-tag>
          <span v-if="t17.configured" class="status-masked">{{ t17.tokenMasked }}</span>
        </div>
      </div>
      <div v-if="t17.updatedAt" class="status-item">
        <div class="status-label">{{ $tt("最后更新") }}</div>
        <div class="status-value">{{ t17.updatedAt }}</div>
      </div>
    </div>

    <el-form label-width="160px" class="config-form" style="margin-top:16px;">
      <el-form-item :label="$tf('国际物流查询功能')">
        <el-switch v-model="t17Form.enabled" @change="onTrack17EnabledChange" />
        <span class="item-remark">{{ $tt("改动即保存；开启后国际件走17TRACK，国内件仍走快递100（单号注册一次扣1个额度，之后查询不再扣费）") }}</span>
      </el-form-item>

      <el-form-item :label="$tf('17TRACK密钥')">
        <el-input
          v-model="t17Form.token"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="$tt('请输入17TRACK密钥 17token')"
          style="width:360px"
        />
        <span class="item-remark">{{ $tt("留空不更新（避免误清空已配置的密钥）") }}</span>
      </el-form-item>

      <el-form-item :label="$tf('国际承运商关键字')">
        <el-input
          v-model="t17Form.carriers"
          type="textarea"
          :rows="3"
          :placeholder="$tt('逗号分隔，如 DHL,UPS,FedEx,USPS')"
          style="width:520px"
        />
        <div class="item-remark">{{ $tt("承运商名称包含其中任一关键字即判定为国际件（走17TRACK）；不填则只按国际单号格式判断") }}</div>
      </el-form-item>

      <el-form-item>
        <el-button v-permission="perm('save')" type="primary" :loading="savingT17" @click="onSaveT17">{{ $tt("保存17TRACK配置") }}</el-button>
        <el-button v-permission="perm('testConnection')" :loading="testingT17" :disabled="!t17.configured" @click="onTestT17">{{ $tt("测试连接") }}</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="t17TestResult"
      :type="t17TestResult.success ? 'success' : 'error'"
      :closable="false"
      style="margin-top:12px;"
      :title="t17TestResult.success ? '连接成功' : '连接失败'"
    >
      {{ t17TestResult.message }}
    </el-alert>

    <el-divider />

    <!-- 看板参数 -->
    <div class="form-header" style="margin-top:8px;">
      <span class="form-title">{{ $tt("看板参数") }}</span>
      <span class="form-tip">{{ $tt("自动刷新间隔 / 最大显示数") }}</span>
    </div>
    <el-form :model="formData" label-width="180px" class="config-form" style="margin-top:12px;">
      <template v-for="item in logisticsConfigs" :key="item.configKey">
        <el-form-item :label="item.configName">
          <div class="item-row">
            <component
              :is="resolveControl(item)"
              v-model="formData[item.configKey]"
              v-bind="resolveProps(item)"
              style="width:100%"
            />
            <span v-if="item.remark" class="item-remark">{{ item.remark }}</span>
          </div>
        </el-form-item>
      </template>
      <el-form-item>
        <el-button v-permission="perm('save')" type="primary" @click="onSaveParams" :loading="saving">{{ $tt("保存看板参数") }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:logistics-config')
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElInput, ElInputNumber, ElSwitch, ElSelect } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import {
  getSysConfigByGroup,
  batchUpdateSysConfig
} from '@/api/system/sysConfig'
import {
  getCredentials as getLogisticsCredentials,
  saveCredentials as saveLogisticsCredentials,
  testCredentials as testLogisticsCredentials
} from '@/api/logistics/logistics'
import { tt } from '@/i18n'

// 接收 embedded 属性（兼容设置中心嵌入模式）
defineProps({
  embedded: { type: Boolean, default: false }
})

// ===== 物流授权配置 =====
const credentials = reactive({
  enabled: true,
  configured: false,
  customerMasked: '',
  updatedAt: ''
})
const credentialsForm = reactive({
  customer: '',
  key: '',
  enabled: true
})
const savingCredentials = ref(false)
const testing = ref(false)
const testResult = ref(null)

// ===== 17TRACK 国际物流配置 =====
const t17 = reactive({
  enabled: false,
  configured: false,
  tokenMasked: '',
  updatedAt: ''
})
const t17Form = reactive({
  token: '',
  enabled: false,
  carriers: ''
})
const savingT17 = ref(false)
const testingT17 = ref(false)
const t17TestResult = ref(null)

// ===== 看板参数配置 =====
const allConfigs = ref([])
const formData = reactive({})
const saving = ref(false)

// 看板参数只展示 dashboard 相关项
// （快递100授权码/密钥属于上方授权区，绝不能在这里重复出现，更不能明文展示密码字段）
const logisticsConfigs = computed(() => {
  return allConfigs.value.filter(c =>
    c.configGroup === 'logistics' && String(c.configKey || '').startsWith('logistics.dashboard.')
  )
})

function resolveControl(item) {
  const t = (item.configType || '').toLowerCase()
  if (t === 'boolean') return ElSwitch
  if (t === 'number') return ElInputNumber
  if (t === 'select') return ElSelect
  return ElInput
}

function resolveProps(item) {
  const t = (item.configType || '').toLowerCase()
  const props = {}
  if (t === 'number') {
    props.controls = false
    props.style = 'width:100%'
  }
  if (t === 'boolean') {
    props.activeText = '开启'
    props.inactiveText = '关闭'
  }
  return props
}

async function loadConfigs() {
  try {
    // 注意：request 响应拦截器已把 Result.data 解包，这里拿到的就是数组本身。
    // 原写法 res.data?.data || res.data 会永远得到 []，导致「看板参数」一条都不显示、
    // 点保存还报"批量更新失败"（本次修复）。
    const res = await getSysConfigByGroup('logistics')
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    allConfigs.value = list
    allConfigs.value.forEach(c => {
      if (c.configType === 'boolean') {
        formData[c.configKey] = c.configValue === 'true' || c.configValue === true
      } else if (c.configType === 'number') {
        formData[c.configKey] = Number(c.configValue) || 0
      } else {
        formData[c.configKey] = c.configValue || ''
      }
    })
  } catch (e) {
    console.warn('加载物流看板参数失败:', e.message)
  }
}

async function onSaveParams() {
  if (logisticsConfigs.value.length === 0) {
    ElMessage.warning(tt('没有可保存的看板参数'))
    return
  }
  saving.value = true
  try {
    const updates = logisticsConfigs.value.map(c => ({
      configKey: c.configKey,
      configValue: String(formData[c.configKey])
    }))
    await batchUpdateSysConfig(updates)
    ElMessage.success(tt('看板参数已保存'))
  } catch (e) {
    ElMessage.error(tt('保存失败: ') + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function onLoadCredentials() {
  try {
    const res = await getLogisticsCredentials()
    const data = res.data || res
    credentials.enabled = !!data.enabled
    credentials.configured = !!data.configured
    credentials.customerMasked = data.customerMasked || ''
    credentials.updatedAt = data.updatedAt || ''
    credentialsForm.enabled = credentials.enabled
    credentialsForm.customer = ''
    credentialsForm.key = ''
    testResult.value = null

    // 17TRACK 国际物流（旧接口不返回这些字段时用默认值兜底，保证不报错）
    t17.enabled = data.track17Enabled ?? false
    t17.configured = data.track17Configured ?? false
    t17.tokenMasked = data.track17TokenMasked ?? ''
    t17.updatedAt = data.track17UpdatedAt ?? ''
    t17Form.enabled = t17.enabled
    t17Form.token = ''
    t17Form.carriers = data.track17Carriers ?? ''
    t17TestResult.value = null
  } catch (e) {
    ElMessage.error(tt('加载物流配置失败: ') + (e.message || e))
  }
}

async function onSaveT17() {
  savingT17.value = true
  try {
    const payload = {
      track17Enabled: t17Form.enabled,
      track17Carriers: t17Form.carriers || ''
    }
    if (t17Form.token && t17Form.token.trim()) {
      payload.track17Token = t17Form.token.trim()
    }
    await saveLogisticsCredentials(payload)
    ElMessage.success(tt('17TRACK 配置已保存'))
    t17Form.token = ''
    await onLoadCredentials()
  } catch (e) {
    ElMessage.error(tt('保存失败: ') + (e.message || e))
  } finally {
    savingT17.value = false
  }
}

async function onTestT17() {
  testingT17.value = true
  t17TestResult.value = null
  try {
    const res = await testLogisticsCredentials('17TRACK')
    const data = res.data || res
    t17TestResult.value = {
      success: !!data.success,
      message: data.message || (data.success ? '连接成功' : '连接失败')
    }
  } catch (e) {
    t17TestResult.value = {
      success: false,
      message: tt('测试失败: ') + (e.message || e)
    }
  } finally {
    testingT17.value = false
  }
}

async function onSaveCredentials() {
  savingCredentials.value = true
  try {
    const payload = { enabled: credentialsForm.enabled }
    if (credentialsForm.customer && credentialsForm.customer.trim()) {
      payload.customer = credentialsForm.customer.trim()
    }
    if (credentialsForm.key && credentialsForm.key.trim()) {
      payload.key = credentialsForm.key.trim()
    }
    await saveLogisticsCredentials(payload)
    ElMessage.success(tt('物流授权配置已保存'))
    credentialsForm.customer = ''
    credentialsForm.key = ''
    await onLoadCredentials()
  } catch (e) {
    ElMessage.error(tt('保存失败: ') + (e.message || e))
  } finally {
    savingCredentials.value = false
  }
}

async function onTestCredentials() {
  testing.value = true
  testResult.value = null
  try {
    const res = await testLogisticsCredentials()
    const data = res.data || res
    testResult.value = {
      success: !!data.success,
      message: data.message || (data.success ? '连接成功' : '连接失败')
    }
  } catch (e) {
    testResult.value = {
      success: false,
      message: tt('测试失败: ') + (e.message || e)
    }
  } finally {
    testing.value = false
  }
}

// 快递100 查询总开关：改动即保存（避免"开了没点对保存按钮，刷新就没了"）
async function onKd100EnabledChange(val) {
  try {
    await saveLogisticsCredentials({ enabled: !!val })
    credentials.enabled = !!val
    ElMessage.success(val ? tt('物流查询功能已开启') : tt('物流查询功能已关闭'))
  } catch (e) {
    credentialsForm.enabled = !val   // 保存失败：回滚开关，避免显示与后端不一致
    ElMessage.error(tt('保存失败: ') + (e.message || e))
  }
}

// 17TRACK 国际物流开关：改动即保存（同上）
async function onTrack17EnabledChange(val) {
  try {
    await saveLogisticsCredentials({ track17Enabled: !!val })
    t17.enabled = !!val
    ElMessage.success(val ? tt('国际物流查询已开启') : tt('国际物流查询已关闭'))
  } catch (e) {
    t17Form.enabled = !val           // 保存失败：回滚开关
    ElMessage.error(tt('保存失败: ') + (e.message || e))
  }
}

onMounted(() => {
  loadConfigs()
  onLoadCredentials()
})
</script>

<style scoped lang="scss">
.kgw-logistics-config {
  padding: 16px;
}
.guide-card {
  margin-bottom: 16px;
  border: 1px solid #f5dab1;
  background: #fdf6ec;
}
.guide-card .guide-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.status-card {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: rgba(245, 247, 250, 0.6);
  border: 1px solid #ebeef5;
  border-radius: 6px;
  flex-wrap: wrap;
}
.status-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 160px;
}
.status-label {
  font-size: 12px;
  color: #909399;
}
.status-value {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #303133;
}
.status-masked {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #67c23a;
  letter-spacing: 1px;
}
.form-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.form-title {
  font-size: 15px;
  font-weight: 600;
}
.form-tip {
  font-size: 12px;
  color: #909399;
}
.item-remark {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
.item-row {
  display: flex;
  align-items: center;
  width: 100%;
}
</style>
