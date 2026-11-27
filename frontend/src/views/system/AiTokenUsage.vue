<template>
  <div class="ai-token-usage">
    <div class="page-header">
      <div class="title-row">
        <h2 class="page-title">{{ $tt("AI 积分管理") }}</h2>
        <el-tag type="success" effect="light" class="own-key-tag">
          <el-icon><Coin /></el-icon> 1 积分 = {{ rules.tokensPerCredit || 1000 }} tokens
        </el-tag>
      </div>
      <p class="page-desc">{{ $tt("按积分统计小楷助手的消耗：公司总积分默认全员共用，管理员可单独给员工分配；分配后该员工只扣个人积分。") }}</p>
    </div>

    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- ========== 公司积分 ========== -->
      <el-tab-pane name="tenant">
        <template #label>
          <span class="tab-label"><el-icon><OfficeBuilding /></el-icon> {{ $tt("公司积分") }}</span>
        </template>

        <div class="quota-cards">
          <div class="quota-card total">
            <div class="card-label">{{ $tt("公司总积分余额") }}</div>
            <div class="card-value">{{ fmt(credit.balance) }}<span class="unit">积分</span></div>
            <div class="card-sub">{{ credit.enforced === 1 ? '对话按积分扣减' : '尚未启用积分控制' }}</div>
          </div>
          <div class="quota-card allocated">
            <div class="card-label">{{ $tt("本月消耗积分") }}</div>
            <div class="card-value">{{ fmt(credit.monthConsumed) }}<span class="unit">积分</span></div>
            <div class="card-sub">{{ $tt("按模型倍率与真实 tokens 折算") }}</div>
          </div>
          <div class="quota-card remaining">
            <div class="card-label">{{ $tt("累计消耗积分") }}</div>
            <div class="card-value">{{ fmt(credit.consumed) }}<span class="unit">积分</span></div>
            <div class="card-sub">{{ $tt("累计获得") }} {{ fmt(credit.granted) }}</div>
          </div>
          <div class="quota-card allocated">
            <div class="card-label">{{ $tt("已分配给员工") }}</div>
            <div class="card-value">{{ fmt(credit.allocated) }}<span class="unit">积分</span></div>
            <div class="card-sub">{{ credit.allocatedUsers || 0 }} 名员工使用个人积分</div>
          </div>
        </div>

        <el-card class="set-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ $tt("积分规则") }}</span>
              <el-tag size="small" type="info">{{ $tt("平台统一配置") }}</el-tag>
            </div>
          </template>
          <div class="rule-grid">
            <div class="rule-item"><span>换算比例</span><b>1 积分 = {{ rules.tokensPerCredit || 1000 }} tokens</b></div>
            <div class="rule-item"><span>深度思考倍率</span><b>{{ rules.deepThinkingMultiplier || 1.5 }}</b></div>
            <div class="rule-item"><span>自带模型</span><b>{{ rules.ownModelFree ? '不扣积分' : '照常扣积分' }}</b></div>
            <div class="rule-item"><span>低余额提醒</span><b>低于 {{ rules.lowBalance ?? 100 }} 积分</b></div>
            <div class="rule-item"><span>新客户初始积分</span><b>{{ rules.newTenantCredits ?? 1000 }}</b></div>
            <div class="rule-item"><span>新员工默认分配</span><b>{{ rules.newUserCredits > 0 ? rules.newUserCredits + ' 积分' : '不单独分配，全员共用' }}</b></div>
          </div>
          <div v-if="rules.modelRatesText" class="ratio-example">
            <div class="ratio-title">模型积分倍率</div>
            <pre class="ratio-text">{{ rules.modelRatesText }}</pre>
          </div>
          <div class="ratio-example">
            扣减口径：本轮 tokens ÷ {{ rules.tokensPerCredit || 1000 }} × 模型倍率<span v-if="rules.deepThinkingMultiplier"> × 深度思考倍率（开启时）</span>；余额不足在下一轮开始前拦截。
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ========== 员工积分 ========== -->
      <el-tab-pane name="user">
        <template #label>
          <span class="tab-label"><el-icon><User /></el-icon> {{ $tt("员工积分") }}</span>
        </template>

        <div class="user-toolbar">
          <div class="left">
            <el-date-picker
              v-model="selectedMonth"
              type="month"
              value-format="YYYY-MM"
              :placeholder="$tt('选择月份')"
              style="width: 160px"
              @change="loadStats"
            />
          </div>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle @click="openColumnSetting" />
          </el-tooltip>
        </div>

        <el-table ref="tableRef" :data="rows" stripe border class="user-table" :empty-text="$tt('暂无数据')" :key="configVersion" @header-dragend="onHeaderDragend">
          <template v-for="col in visibleSortedColumns" :key="col.prop">
            <el-table-column
              :prop="col.prop"
              :label="$tf(col.label)"
              :width="col.width"
              :min-width="col.minWidth"
              :align="col.align || 'left'"
              :show-overflow-tooltip="!!col.tooltip"
              :fixed="getColFixed(col.prop)"
            >
              <template v-if="col.type" #default="{ row }">
                <template v-if="col.type === 'username'">{{ row.username || '用户#' + row.user_id }}</template>
                <template v-else-if="col.type === 'number'">{{ fmt(row[col.prop]) }}</template>
                <template v-else-if="col.type === 'creditBalance'">
                  <span :class="['quota-val', row.creditOwner === 'USER' ? 'custom' : 'tenant']">{{ fmt(row.creditBalance) }}</span>
                  <el-tag size="small" :type="row.creditOwner === 'USER' ? 'primary' : 'warning'" class="source-tag">
                    {{ row.creditOwner === 'USER' ? '个人积分' : '公司共享' }}
                  </el-tag>
                </template>
                <template v-else-if="col.type === 'credits'">{{ fmt(row.credits) }}</template>
                <template v-else-if="col.type === 'status'">
                  <el-tag v-if="isExhausted(row)" type="danger" size="small">{{ $tt("已用完") }}</el-tag>
                  <el-tag v-else-if="isLow(row)" type="warning" size="small">{{ $tt("将用尽") }}</el-tag>
                  <el-tag v-else type="success" size="small">{{ $tt("正常") }}</el-tag>
                </template>
              </template>
            </el-table-column>
          </template>
          <el-table-column :label="$tf('操作')" width="110" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="perm('edit')" type="primary" link @click="openAllocateDialog(row)">{{ $tt("分配积分") }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 分配积分弹窗 -->
    <el-dialog v-model="dialogVisible" :title="$tf('分配积分')" width="480px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item :label="$tf('员工')">
          <span class="dialog-user">{{ dialogRow.username || ('用户#' + dialogRow.user_id) }}</span>
        </el-form-item>
        <el-form-item :label="$tf('当前归属')">
          <el-tag :type="dialogRow.creditOwner === 'USER' ? 'primary' : 'warning'" size="small">
            {{ dialogRow.creditOwner === 'USER' ? '个人积分' : '公司共享积分' }}
          </el-tag>
          <span class="dialog-balance">余额 {{ fmt(dialogRow.creditBalance) }} 积分</span>
        </el-form-item>
        <el-form-item :label="$tf('分配数量')">
          <el-input v-model="dialogAmount" inputmode="decimal" :placeholder="$tt('从公司总积分划拨的积分数量')" />
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="dialogRemark" maxlength="100" :placeholder="$tt('选填，写入积分流水')" />
        </el-form-item>
      </el-form>
      <div class="dialog-hint">
        公司总积分余额：<b>{{ fmt(credit.balance) }}</b> 积分。分配后该员工只扣个人积分，用完不会自动回退公司共享积分。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-if="dialogRow.creditOwner === 'USER'" plain type="warning" :loading="saving" @click="handleReclaim">{{ $tt("收回个人积分") }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleAllocate">{{ $tt("确认分配") }}</el-button>
      </template>
    </el-dialog>

    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'AiTokenUsage'" :columns="columns" />
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:ai-token-usage')
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin, OfficeBuilding, User, Setting } from '@element-plus/icons-vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { getTokenUsageStats, allocateUserCredit, reclaimUserCredit } from '@/api/ai/tokenUsage'
import { tt } from '@/i18n'

const now = new Date()
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

const activeTab = ref('tenant')
const selectedMonth = ref(currentMonth)
const rows = ref([])
const credit = ref({ balance: 0, granted: 0, consumed: 0, monthConsumed: 0, allocated: 0, allocatedUsers: 0, enforced: 0 })
const rules = ref({ tokensPerCredit: 1000, deepThinkingMultiplier: 1.5, ownModelFree: false, lowBalance: 100, newTenantCredits: 1000, newUserCredits: 0, modelRatesText: '' })

const columns = [
  { prop: 'username', label: '用户', minWidth: 140, type: 'username' },
  { prop: 'call_count', label: '调用次数', width: 100, align: 'right', type: 'number' },
  { prop: 'prompt_tokens', label: '输入 Tokens', width: 130, align: 'right', type: 'number' },
  { prop: 'completion_tokens', label: '输出 Tokens', width: 130, align: 'right', type: 'number' },
  { prop: 'total_tokens', label: '总 Tokens', width: 130, align: 'right', type: 'number' },
  { prop: 'credits', label: '本月消耗积分', width: 130, align: 'right', type: 'credits' },
  { prop: 'creditBalance', label: '积分余额', width: 180, align: 'right', type: 'creditBalance' },
  { prop: '_status', label: '状态', width: 90, align: 'center', type: 'status' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns, onHeaderDragend, applySavedWidths } = useColumnSetting('AiTokenUsage', columns)
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))
const tableRef = ref(null)

const dialogVisible = ref(false)
const dialogRow = reactive({ user_id: null, username: '', creditOwner: 'TENANT', creditBalance: 0 })
const dialogAmount = ref('')
const dialogRemark = ref('')
const saving = ref(false)

const fmt = (v) => (v === null || v === undefined || v === '' ? '0' : Number(v).toLocaleString(undefined, { maximumFractionDigits: 4 }))

const isExhausted = (row) => Number(row.creditBalance || 0) <= 0
const isLow = (row) => {
  const bal = Number(row.creditBalance || 0)
  return bal > 0 && bal < Number(rules.value.lowBalance || 0)
}

const loadStats = async () => {
  try {
    const data = await getTokenUsageStats(selectedMonth.value || currentMonth)
    rows.value = data.rows || []
    credit.value = data.credit || credit.value
    rules.value = data.creditRules || rules.value
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    nextTick(() => applySavedWidths(tableRef))
  }
}

const openAllocateDialog = (row) => {
  dialogRow.user_id = row.user_id
  dialogRow.username = row.username || ''
  dialogRow.creditOwner = row.creditOwner || 'TENANT'
  dialogRow.creditBalance = Number(row.creditBalance || 0)
  dialogAmount.value = ''
  dialogRemark.value = ''
  dialogVisible.value = true
}

const handleAllocate = async () => {
  const amount = Number(dialogAmount.value)
  if (!Number.isFinite(amount) || amount <= 0) {
    ElMessage.warning(tt('请填写大于 0 的分配积分'))
    return
  }
  saving.value = true
  try {
    await allocateUserCredit(dialogRow.user_id, dialogRow.username, amount, dialogRemark.value)
    ElMessage.success(tt('积分已分配'))
    dialogVisible.value = false
    loadStats()
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    saving.value = false
  }
}

const handleReclaim = async () => {
  saving.value = true
  try {
    await reclaimUserCredit(dialogRow.user_id, dialogRow.username, dialogRemark.value)
    ElMessage.success(tt('已收回个人积分，恢复共用公司总积分'))
    dialogVisible.value = false
    loadStats()
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped lang="scss">
.ai-token-usage { padding: 4px 2px; }
.page-header {
  margin-bottom: 20px;
  .title-row { display: flex; align-items: center; gap: 12px; }
  .page-title { margin: 0; font-size: 20px; font-weight: 600; color: #1f2937; }
  .own-key-tag { display: inline-flex; align-items: center; gap: 4px; }
  .page-desc { margin: 8px 0 0; font-size: 13px; color: #6b7280; line-height: 1.6; }
}
.main-tabs {
  :deep(.el-tabs__header) { margin-bottom: 20px; }
  :deep(.el-tabs__item) { font-size: 15px; font-weight: 500; }
}
.tab-label { display: inline-flex; align-items: center; gap: 6px; }

.quota-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.quota-card {
  padding: 20px 24px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #eef0f3;
  transition: all 0.2s;
  &:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
  .card-label { font-size: 13px; color: #6b7280; margin-bottom: 8px; }
  .card-value {
    font-size: 26px; font-weight: 700; color: #1f2937;
    .unit { font-size: 13px; font-weight: 400; color: #9ca3af; margin-left: 4px; }
  }
  .card-sub { font-size: 12px; color: #9ca3af; margin-top: 6px; }
  &.total { border-left: 3px solid #409eff; }
  &.allocated { border-left: 3px solid #e6a23c; }
  &.remaining { border-left: 3px solid #67c23a; }
}
.set-card {
  border-radius: 10px;
  :deep(.el-card__header) { padding: 14px 20px; }
  .card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }
}
.rule-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px 24px; }
.rule-item {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: #6b7280; padding: 6px 0; border-bottom: 1px dashed #ebeef5;
  b { color: #303133; font-weight: 600; }
}
.ratio-example {
  margin-top: 12px; padding: 10px 12px; background: #f5f7fa; border-left: 3px solid #409eff;
  border-radius: 3px; font-size: 12px; color: #6b7280; line-height: 1.7;
}
.ratio-title { font-weight: 600; color: #303133; margin-bottom: 4px; }
.ratio-text { margin: 0; white-space: pre-wrap; font-family: inherit; }

.user-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.user-table { border-radius: 8px; overflow: hidden; }
.quota-val { font-weight: 600; margin-right: 6px; &.custom { color: #409eff; } &.tenant { color: #e6a23c; } }
.source-tag { vertical-align: middle; }

.dialog-user { font-weight: 600; color: #303133; }
.dialog-balance { margin-left: 10px; font-size: 13px; color: #606266; }
.dialog-hint { background: #f5f7fa; border-radius: 6px; padding: 10px 14px; font-size: 13px; color: #606266; line-height: 1.7; }

@media (max-width: 1200px) { .quota-cards { grid-template-columns: repeat(2, 1fr); } .rule-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .quota-cards { grid-template-columns: 1fr; } }
</style>
