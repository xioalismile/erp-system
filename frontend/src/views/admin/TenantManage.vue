<template>
  <div class="erp-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $tt("客户管理") }}</span>
          <div class="header-actions">
            <el-button type="info" plain icon="Setting" v-permission="perm('edit')" @click="openAiDefaults">{{ $tt("AI 默认规则") }}</el-button>
            <el-button type="primary" icon="Plus" v-permission="perm('add')" @click="handleAdd">{{ $tt("新增客户") }}</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="query.keyword"
          :placeholder="$tt('客户名称 / 编码 / 联系人')"
          clearable
          style="width: 240px"
          @keyup.enter="loadData"
          @clear="loadData"
        />
        <el-select v-model="query.status" :placeholder="$tt('状态')" clearable style="width: 140px" @change="loadData">
          <el-option :label="$tf('正常')" value="active" />
          <el-option :label="$tf('已禁用')" value="disabled" />
          <el-option :label="$tf('已过期')" value="expired" />
        </el-select>
        <el-button type="primary" icon="Search" v-permission="perm('query')" @click="loadData">{{ $tt("查询") }}</el-button>
        <el-button icon="Refresh" v-permission="perm('reset')" @click="handleReset">{{ $tt("重置") }}</el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="records" border stripe style="width: 100%">
        <el-table-column prop="tenant_code" :label="$tf('租户编码')" width="120" />
        <el-table-column prop="tenant_name" :label="$tf('客户名称')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$tf('版本')" width="110">
          <template #default="{ row }">
            <el-tag :type="versionTagType(row.version)" effect="plain">{{ versionText(row.version) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ $dt(statusText(row.status)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('用户数')" width="80" align="center">
          <template #default="{ row }">
            <span>{{ row.user_count ?? 0 }} / {{ row.max_users }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('到期日期')" width="130">
          <template #default="{ row }">
            <span :class="{ 'expire-warn': isExpiringSoon(row), 'expire-danger': isExpired(row) }">
              {{ formatDate(row.expire_date) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="contact_person" :label="$tf('联系人')" width="90" />
        <el-table-column prop="contact_phone" :label="$tf('联系电话')" width="130" />
        <el-table-column :label="$tf('管理员账号')" width="120">
          <template #default="{ row }">
            <span v-if="row.admin_username" class="admin-username">{{ row.admin_username }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="db_name" :label="$tf('数据库')" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-muted">{{ row.db_name }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('创建时间')" width="160">
          <template #default="{ row }">
            <span class="text-muted">{{ formatDateTime(row.created_at) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="430" fixed="right">
          <template #default="{ row }">
            <el-button size="small" v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button size="small" type="warning" v-permission="perm('reset')" @click="handleResetPwd(row)">{{ $tt("重置密码") }}</el-button>
            <el-button size="small" type="primary" v-permission="perm('edit')" @click="handleRenew(row)">{{ $tt("续费") }}</el-button>
            <el-button size="small" type="info" plain v-permission="perm('edit')" @click="handleAiCredit(row)">{{ $tt("积分") }}</el-button>
            <el-button
              v-if="row.status !== 'disabled'"
              size="small"
              type="danger"
              v-permission="perm('disable')"
              @click="handleDisable(row)"
            >{{ $tt("禁用") }}</el-button>
            <el-button
              v-else
              size="small"
              type="success"
              v-permission="perm('enable')"
              @click="handleEnable(row)"
            >{{ $tt("启用") }}</el-button>
            <el-button
              v-if="row.tenant_code !== 'DEFAULT'"
              size="small"
              type="danger"
              plain
              v-permission="perm('delete')"
              @click="handleDelete(row)"
            >{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- AI 默认规则（平台统一） -->
    <el-dialog v-model="aiDefaultsVisible" :title="$tf('AI 默认规则')" width="640px" destroy-on-close>
      <el-form label-width="130px">
        <el-divider content-position="left">积分规则</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('每积分 tokens')">
              <el-input v-model="aiRules.tokensPerCredit" inputmode="numeric" placeholder="1000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('深度思考倍率')">
              <el-input v-model="aiRules.deepThinkingMultiplier" inputmode="decimal" placeholder="1.5" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('新客户初始积分')">
              <el-input v-model="aiRules.newTenantCredits" inputmode="numeric" placeholder="1000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('新员工默认分配')">
              <el-input v-model="aiRules.newUserCredits" inputmode="numeric" placeholder="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('低余额提醒阈值')">
              <el-input v-model="aiRules.lowBalance" inputmode="numeric" placeholder="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('自带模型不扣积分')">
              <el-switch v-model="aiRules.ownModelFree" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('模型积分倍率')">
          <el-input v-model="aiRules.modelRatesText" type="textarea" :rows="3" placeholder="deepseek-flash=1&#10;deepseek-reasoner=2.5" />
        </el-form-item>
        <div class="form-tip">每行一条「模型名=倍率」，未设置的模型按 1.0；深度思考开启时再乘深度倍率。</div>

        <el-divider content-position="left">默认模型</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('模型服务商')">
              <el-select v-model="aiModel.channel" style="width: 100%">
                <el-option label="DeepSeek" value="DEEPSEEK" />
                <el-option label="通义千问" value="QWEN" />
                <el-option label="本地模型" value="OLLAMA" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('模型名称')">
              <el-input v-model="aiModel.model" placeholder="deepseek-flash" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('调用地址')">
          <el-input v-model="aiModel.baseUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item :label="$tf('模型密钥')">
          <el-input
            v-model="aiModel.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="aiModel.hasApiKey ? '已配置，留空保持原值' : 'sk-...'"
          />
        </el-form-item>
        <div class="form-tip">平台统一密钥：客户没有单独配置时自动使用，新建客户开箱即可在模型下拉里选择。</div>

        <template v-if="internalVoiceTuning">
          <el-divider content-position="left">语音参数</el-divider>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item :label="$tf('等待指令时长')">
                <el-input v-model="aiRules.voiceCmdWaitSec" inputmode="numeric" placeholder="5" />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="form-tip">单位秒，全局统一。唤醒后超过这个时间没听到指令，小楷播报待命并结束本次语音对话。</div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="aiDefaultsVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" :loading="savingAiDefaults" @click="saveAiDefaultsHandler">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 客户积分 -->
    <el-dialog v-model="creditDialogVisible" :title="$tf('客户积分')" width="520px" destroy-on-close>
      <div v-loading="creditLoading">
        <div class="credit-cards">
          <div class="credit-card">
            <div class="cc-label">公司总积分余额</div>
            <div class="cc-value">{{ fmtCredit(creditData.balance) }}</div>
          </div>
          <div class="credit-card">
            <div class="cc-label">本月消耗积分</div>
            <div class="cc-value">{{ fmtCredit(creditData.monthConsumed) }}</div>
          </div>
          <div class="credit-card">
            <div class="cc-label">累计消耗积分</div>
            <div class="cc-value">{{ fmtCredit(creditData.consumed) }}</div>
          </div>
        </div>
        <div class="credit-sub">
          {{ (creditRow && creditRow.tenant_name) || '' }} · 已分配给 {{ creditData.allocatedUsers || 0 }} 名员工
        </div>
        <el-form label-width="100px" style="margin-top: 14px">
          <el-form-item :label="$tf('调整积分')">
            <el-input v-model="creditDelta" inputmode="decimal" placeholder="正数增加，负数扣减" />
          </el-form-item>
          <el-form-item :label="$tf('备注')">
            <el-input v-model="creditRemark" maxlength="100" placeholder="选填，写入积分流水" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="creditDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" :loading="savingCredit" @click="saveCreditHandler">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <TenantForm ref="formRef" @success="loadData" />
    <!-- 续费弹窗 -->
    <TenantRenew ref="renewRef" @success="loadData" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTenants, disableTenant, enableTenant, resetTenantPassword, deleteTenant,
  getAiDefaults, saveAiDefaults, getTenantCredit, adjustTenantCredit
} from '@/api/admin/tenant'
import TenantForm from './TenantForm.vue'
import TenantRenew from './TenantRenew.vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('platform:tenant-manage')

const loading = ref(false)
const records = ref([])
const total = ref(0)
const formRef = ref(null)
const renewRef = ref(null)

// ===== AI 默认规则（平台统一，2026-10-24 积分制）=====
const aiDefaultsVisible = ref(false)
const savingAiDefaults = ref(false)
// 语音调参：只在我们自己部署的构建里出现（客户包不设该变量 → 整块不渲染）
const internalVoiceTuning = import.meta.env.VITE_INTERNAL_VOICE_TUNING === 'true'
const aiRules = reactive({
  tokensPerCredit: 1000,
  deepThinkingMultiplier: '1.5',
  newTenantCredits: 1000,
  newUserCredits: 0,
  lowBalance: 100,
  ownModelFree: false,
  modelRatesText: '',
  defaultChannel: 'DEEPSEEK',
  voiceCmdWaitSec: 5
})
const aiModel = reactive({ channel: 'DEEPSEEK', model: '', baseUrl: '', apiKey: '', hasApiKey: false })

// ===== 客户积分 =====
const creditDialogVisible = ref(false)
const creditLoading = ref(false)
const savingCredit = ref(false)
const creditRow = ref(null)
const creditData = reactive({ balance: 0, monthConsumed: 0, consumed: 0, granted: 0, allocatedUsers: 0 })
const creditDelta = ref('')
const creditRemark = ref('')

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: ''
})

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await listTenants({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined
    })
    records.value = res.records || []
    total.value = res.total || 0
  } catch {
    // request 拦截器已静默处理 GET 错误
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.page = 1
  query.keyword = ''
  query.status = ''
  loadData()
}

function handleAdd() {
  formRef.value.open(null)
}

function handleEdit(row) {
  formRef.value.open(row)
}

function handleRenew(row) {
  renewRef.value.open(row)
}

async function handleDisable(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确定禁用客户「")}${row.tenant_name}${tt("」吗？禁用后该客户所有用户将无法登录，数据保留不删除。")}`,
      tt('禁用确认'),
      { type: 'warning', confirmButtonText: tt('确定禁用'), cancelButtonText: tt('取消') }
    )
  } catch {
    return
  }
  await disableTenant(row.tenant_id)
  ElMessage.success(tt('已禁用'))
  loadData()
}

async function handleEnable(row) {
  await enableTenant(row.tenant_id)
  ElMessage.success(tt('已启用'))
  loadData()
}

async function handleDelete(row) {
  const isIndependentDb = row.db_name && row.db_name.startsWith('erp_tenant_')
  const warnText = isIndependentDb
    ? `该客户使用独立数据库「${row.db_name}」，删除后整个数据库将被物理删除、数据无法恢复！`
    : `该客户挂载在共享数据库「${row.db_name || 'erp_db'}」，删除仅移除平台登记和管理员账号，不影响其他客户数据。`
  try {
    await ElMessageBox.confirm(
      `${tt("确定删除客户「")}${row.tenant_name}（${row.tenant_code}${tt("）」吗？\\n\\n")}${warnText}${tt("\\n\\n删除操作不可恢复，请谨慎确认！")}`,
      tt('删除确认'),
      { type: 'error', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch {
    return
  }
  await deleteTenant(row.tenant_id)
  ElMessage.success(tt('已删除'))
  loadData()
}

async function handleResetPwd(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `${tt("将重置客户「")}${row.tenant_name}${tt("」的管理员密码，请输入新密码（至少6位）")}`,
      tt('重置管理员密码'),
      {
        confirmButtonText: tt('确认重置'),
        cancelButtonText: tt('取消'),
        inputPlaceholder: tt('输入新密码'),
        inputType: 'password',
        inputValidator: (v) => {
          if (!v) return '密码不能为空'
          if (v.length < 6) return '密码长度不能少于6位'
          return true
        }
      }
    )
    await resetTenantPassword(row.tenant_id, value)
    ElMessage.success(tt('密码已重置，请通知客户使用新密码登录'))
  } catch (e) {
    if (e === 'cancel' || e?.action === 'cancel') return
    ElMessage.error(e?.message || tt('重置失败'))
  }
}

/** 打开平台「AI 默认规则」：积分规则 + 平台统一模型密钥 */
async function openAiDefaults() {
  try {
    const data = await getAiDefaults()
    const r = data?.rules || {}
    Object.assign(aiRules, {
      tokensPerCredit: r.tokensPerCredit ?? 1000,
      deepThinkingMultiplier: String(r.deepThinkingMultiplier ?? '1.5'),
      newTenantCredits: r.newTenantCredits ?? 1000,
      newUserCredits: r.newUserCredits ?? 0,
      lowBalance: r.lowBalance ?? 100,
      ownModelFree: !!r.ownModelFree,
      modelRatesText: r.modelRatesText || '',
      defaultChannel: r.defaultChannel || 'DEEPSEEK',
      voiceCmdWaitSec: data?.voice?.cmdWaitSec ?? 5
    })
    const m = data?.model || {}
    Object.assign(aiModel, {
      channel: m.channel || r.defaultChannel || 'DEEPSEEK',
      model: m.model || '',
      baseUrl: m.baseUrl || '',
      apiKey: '',
      hasApiKey: !!m.hasApiKey
    })
    aiDefaultsVisible.value = true
  } catch (e) {
    ElMessage.error(e?.message || tt('读取AI默认规则失败'))
  }
}

async function saveAiDefaultsHandler() {
  savingAiDefaults.value = true
  try {
    await saveAiDefaults({
      tokensPerCredit: Number(aiRules.tokensPerCredit),
      deepThinkingMultiplier: Number(aiRules.deepThinkingMultiplier),
      newTenantCredits: Number(aiRules.newTenantCredits),
      newUserCredits: Number(aiRules.newUserCredits),
      lowBalance: Number(aiRules.lowBalance),
      ownModelFree: !!aiRules.ownModelFree,
      modelRatesText: aiRules.modelRatesText,
      defaultChannel: aiModel.channel,
      baseUrl: aiModel.baseUrl,
      model: aiModel.model,
      apiKey: aiModel.apiKey,
      // 只在自有构建里提交，客户包不传该字段（后端"不传=不改"）
      ...(internalVoiceTuning ? { voiceCmdWaitSec: Number(aiRules.voiceCmdWaitSec) } : {})
    })
    ElMessage.success(tt('AI 默认规则已保存'))
    aiDefaultsVisible.value = false
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    savingAiDefaults.value = false
  }
}

const fmtCredit = (v) => {
  const n = Number(v || 0)
  return n.toLocaleString(undefined, { maximumFractionDigits: 4 })
}

/** 平台查看/调整客户公司总积分 */
async function handleAiCredit(row) {
  creditRow.value = row
  creditDelta.value = ''
  creditRemark.value = ''
  Object.assign(creditData, { balance: 0, monthConsumed: 0, consumed: 0, granted: 0, allocatedUsers: 0 })
  creditDialogVisible.value = true
  creditLoading.value = true
  try {
    const data = await getTenantCredit(row.tenant_id)
    Object.assign(creditData, {
      balance: data?.balance ?? 0,
      monthConsumed: data?.monthConsumed ?? 0,
      consumed: data?.consumed ?? 0,
      granted: data?.granted ?? 0,
      allocatedUsers: data?.allocatedUsers ?? 0
    })
  } catch (e) {
    // 读取失败仍可调整（按 0 展示）
  } finally {
    creditLoading.value = false
  }
}

async function saveCreditHandler() {
  const delta = Number(creditDelta.value)
  if (!Number.isFinite(delta) || delta === 0) {
    ElMessage.warning(tt('请填写要调整的积分数量，正数增加、负数扣减'))
    return
  }
  savingCredit.value = true
  try {
    const data = await adjustTenantCredit(creditRow.value.tenant_id, delta, creditRemark.value)
    Object.assign(creditData, {
      balance: data?.balance ?? creditData.balance,
      monthConsumed: data?.monthConsumed ?? creditData.monthConsumed,
      consumed: data?.consumed ?? creditData.consumed,
      granted: data?.granted ?? creditData.granted,
      allocatedUsers: data?.allocatedUsers ?? creditData.allocatedUsers
    })
    creditDelta.value = ''
    creditRemark.value = ''
    ElMessage.success(tt('客户积分已更新'))
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    savingCredit.value = false
  }
}

function versionText(v) {
  if (v === 'CRM') return 'CRM获客版'
  if (v === 'ULTIMATE') return '旗舰版'
  return '标准版'
}

function versionTagType(v) {
  if (v === 'CRM') return 'warning'
  if (v === 'ULTIMATE') return 'danger'
  return 'success'
}

function statusText(s) {
  if (s === 'disabled') return '已禁用'
  if (s === 'expired') return '已过期'
  return '正常'
}

function statusTagType(s) {
  if (s === 'disabled') return 'info'
  if (s === 'expired') return 'danger'
  return 'success'
}

function formatDate(d) {
  return d ? String(d).slice(0, 10) : ''
}

function formatDateTime(d) {
  if (!d) return ''
  const s = String(d)
  return s.length >= 19 ? s.slice(0, 19).replace('T', ' ') : s
}

function isExpired(row) {
  if (!row.expire_date) return false
  return new Date(row.expire_date) < new Date(new Date().toDateString())
}

function isExpiringSoon(row) {
  if (!row.expire_date || isExpired(row)) return false
  const diff = (new Date(row.expire_date) - new Date(new Date().toDateString())) / 86400000
  return diff <= 30
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-tip {
  margin: -4px 0 10px 130px;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
}
.credit-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.credit-card {
  padding: 14px 16px;
  background: #f7f9fc;
  border: 1px solid #eef0f3;
  border-radius: 8px;
  .cc-label { font-size: 12px; color: #6b7280; }
  .cc-value { margin-top: 6px; font-size: 22px; font-weight: 700; color: #1f2937; }
}
.credit-sub {
  margin-top: 10px;
  font-size: 12.5px;
  color: #6b7280;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.admin-username {
  color: #409eff;
  font-weight: 500;
  font-size: 13px;
}

.expire-warn {
  color: #e6a23c;
  font-weight: 600;
}

.expire-danger {
  color: #f56c6c;
  font-weight: 600;
}
</style>
