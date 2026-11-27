<template>
  <div class="kgw-commission-rate">
    <div class="kgw-toolbar">
      <div class="kgw-tabs">
        <div class="kgw-tab active">{{ $tt("提成率配置") }}</div>
      </div>
      <div class="kgw-actions">
        <el-button v-permission="perm('addRow')" type="primary" size="small" @click="openCreateDialog">{{ $tt("新增版本") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadAll">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <div class="kgw-body">
      <!-- 当前生效版本 -->
      <div class="kgw-section">
        <div class="kgw-section-title">{{ $tt("当前生效版本") }}</div>
        <div class="current-card" v-loading="loading.current">
          <template v-if="currentVersion">
            <div class="cv-row">
              <div class="cv-item">
                <span class="cv-label">{{ $tt("版本号") }}</span>
                <el-tag type="success" size="large">v{{ currentVersion.versionNo }}</el-tag>
              </div>
              <div class="cv-item">
                <span class="cv-label">{{ $tt("基础提成率") }}</span>
                <span class="cv-value highlight">{{ pct(currentVersion.baseRate) }}</span>
              </div>
              <div class="cv-item">
                <span class="cv-label">{{ $tt("超额倍数") }}</span>
                <span class="cv-value">{{ currentVersion.overAchievementMultiplier }}×</span>
              </div>
              <div class="cv-item">
                <span class="cv-label">{{ $tt("超额奖励率") }}</span>
                <span class="cv-value">{{ pct(currentVersion.overAchievementBonus) }}</span>
              </div>
              <div class="cv-item">
                <span class="cv-label">{{ $tt("计算基础") }}</span>
                <el-tag size="small">{{ basisLabel(currentVersion.basis) }}</el-tag>
              </div>
              <div class="cv-item">
                <span class="cv-label">{{ $tt("生效日期") }}</span>
                <span class="cv-value">{{ currentVersion.effectiveDate }}</span>
              </div>
              <div class="cv-item" v-if="currentVersion.remark">
                <span class="cv-label">{{ $tt("备注") }}</span>
                <span class="cv-value remark">{{ currentVersion.remark }}</span>
              </div>
              <div class="cv-item">
                <span class="cv-label">{{ $tt("创建人 / 时间") }}</span>
                <span class="cv-value">{{ currentVersion.createdBy }} · {{ currentVersion.createdAt }}</span>
              </div>
            </div>
          </template>
          <el-empty v-else :description="$tt('暂无生效版本，请新增一版')" :image-size="60" />
        </div>
      </div>

      <!-- 历史版本列表 -->
      <div class="kgw-section">
        <div class="kgw-section-title">{{ $tt("历史版本（按版本号倒序）") }}</div>
        <el-table :data="historyRecords" border size="small" v-loading="loading.history" @sort-change="onSortChange">
          <el-table-column prop="versionNo" :label="$tf('版本')" width="80" align="center" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.isCurrent === 1 ? 'success' : 'info'" size="small">v{{ row.versionNo }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="basis" :label="$tf('计算基础')" width="110" align="center" sortable="custom">
            <template #default="{ row }">{{ basisLabel(row.basis) }}</template>
          </el-table-column>
          <el-table-column prop="baseRate" :label="$tf('基础提成率')" width="120" align="right" sortable="custom">
            <template #default="{ row }">{{ pct(row.baseRate) }}</template>
          </el-table-column>
          <el-table-column prop="overAchievementMultiplier" :label="$tf('超额倍数')" width="100" align="center" sortable="custom">
            <template #default="{ row }">{{ row.overAchievementMultiplier }}×</template>
          </el-table-column>
          <el-table-column prop="overAchievementBonus" :label="$tf('超额奖励率')" width="120" align="right" sortable="custom">
            <template #default="{ row }">{{ pct(row.overAchievementBonus) }}</template>
          </el-table-column>
          <el-table-column prop="effectiveDate" :label="$tf('生效日期')" width="120" sortable="custom" />
          <el-table-column prop="expiryDate" :label="$tf('失效日期')" width="120" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.isCurrent === 1" type="success" size="small">{{ $tt("当前生效") }}</el-tag>
              <span v-else>{{ row.expiryDate || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" :label="$tf('备注')" min-width="160" show-overflow-tooltip sortable="custom" />
          <el-table-column prop="createdBy" :label="$tf('创建人')" width="100" sortable="custom" />
          <el-table-column prop="createdAt" :label="$tf('创建时间')" width="170" sortable="custom" />
        </el-table>
        <el-pagination
          style="margin-top: 12px; text-align: right;"
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>

      <!-- 说明 -->
      <div class="kgw-section">
        <div class="kgw-section-title">{{ $tt("说明") }}</div>
        <ul class="doc-list">
          <li>每次新增版本都会自动留痕：旧版本的"失效日期"被设置为"新版本生效日期 - 1天"，新版本成为当前生效版本。</li>
          <li>销售提成报表按订单的"下单日期"自动匹配当时生效的版本计算提成，跨月分段计算。</li>
          <li>{{ $tt("历史版本不可修改、不可删除，仅用于回溯和审计。") }}</li>
          <li>{{ $tt("新版本生效日期必须晚于当前版本的生效日期，且不能与当前版本相同。") }}</li>
        </ul>
      </div>
    </div>

    <!-- 新增版本对话框 -->
    <el-dialog v-model="dialogVisible" :title="$tf('新增提成率版本')" width="520px" :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" size="default">
        <el-form-item :label="$tf('计算基础')" prop="basis">
          <el-radio-group v-model="form.basis">
            <el-radio value="order">{{ $tt("按订单额") }}</el-radio>
            <el-radio value="payment">{{ $tt("按回款额") }}</el-radio>
            <el-radio value="profit">{{ $tt("按毛利额") }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$tf('基础提成率')" prop="baseRate">
          <el-input-number v-model="form.baseRate" :min="0" :max="100" :step="0.1" :precision="2" controls-position="right" style="width:180px" />
          <span class="form-hint">{{ $tt("% （如 5.00 表示 5%）") }}</span>
        </el-form-item>
        <el-form-item :label="$tf('超额倍数')" prop="overAchievementMultiplier">
          <el-input-number v-model="form.overAchievementMultiplier" :min="1" :max="10" :step="0.05" :precision="2" controls-position="right" style="width:180px" />
          <span class="form-hint">×</span>
        </el-form-item>
        <el-form-item :label="$tf('超额奖励率')" prop="overAchievementBonus">
          <el-input-number v-model="form.overAchievementBonus" :min="0" :max="100" :step="0.1" :precision="2" controls-position="right" style="width:180px" />
          <span class="form-hint">{{ $tt("% （完成率超过超额倍数后额外加成的提成率）") }}</span>
        </el-form-item>
        <el-form-item :label="$tf('生效日期')" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" type="date" :placeholder="$tt('选择生效日期')" value-format="YYYY-MM-DD" style="width:180px" />
          <span class="form-hint">必须晚于当前版本生效日期（{{ currentVersion?.effectiveDate || '-' }}）</span>
        </el-form-item>
        <el-form-item :label="$tf('备注')" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit :placeholder="$tt('如：2026Q3调整方案')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" :loading="submitting" @click="submitCreate">{{ $tt("保存并生效") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:commission-rate')
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCommissionRateCurrent,
  getCommissionRateHistory,
  createCommissionRateVersion
} from '@/api/system/commissionRate'
import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadHistory() }
const loading = reactive({ current: false, history: false, ...getSortParams()})
const currentVersion = ref(null)
const historyRecords = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  basis: 'order',
  baseRate: 5,
  overAchievementMultiplier: 1.2,
  overAchievementBonus: 3,
  effectiveDate: '',
  remark: ''
})
const rules = {
  basis: [{ required: true, message: tt('请选择计算基础'), trigger: 'change' }],
  baseRate: [{ required: true, message: tt('请输入基础提成率'), trigger: 'blur' }],
  overAchievementMultiplier: [{ required: true, message: tt('请输入超额倍数'), trigger: 'blur' }],
  effectiveDate: [{ required: true, message: tt('请选择生效日期'), trigger: 'change' }]
}

function basisLabel(basis) {
  return { order: '按订单额', payment: '按回款额', profit: '按毛利额' }[basis] || basis || '-'
}

function pct(num) {
  if (num === null || num === undefined || num === '') return '0%'
  const v = Number(num)
  if (Math.abs(v) > 1) return v.toFixed(2) + '%'
  return (v * 100).toFixed(2) + '%'
}

async function loadCurrent() {
  loading.current = true
  try {
    currentVersion.value = await getCommissionRateCurrent()
  } catch (e) {
    currentVersion.value = null
  } finally {
    loading.current = false
  }
}

async function loadHistory() {
  loading.history = true
  try {
    const res = await getCommissionRateHistory(page.current, page.size, getSortParams())
    historyRecords.value = res.records || []
    page.total = res.total || 0
  } catch (e) {
    historyRecords.value = []
    page.total = 0
  } finally {
    loading.history = false
  }
}

function loadAll() {
  loadCurrent()
  loadHistory()
}

function openCreateDialog() {
  form.basis = currentVersion.value?.basis || 'order'
  form.baseRate = currentVersion.value?.baseRate != null ? Number(currentVersion.value.baseRate) * 100 : 5
  form.overAchievementMultiplier = currentVersion.value?.overAchievementMultiplier != null ? Number(currentVersion.value.overAchievementMultiplier) : 1.2
  form.overAchievementBonus = currentVersion.value?.overAchievementBonus != null ? Number(currentVersion.value.overAchievementBonus) * 100 : 3
  // 默认生效日期 = 明天
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  form.effectiveDate = tomorrow.toISOString().slice(0, 10)
  form.remark = ''
  dialogVisible.value = true
}

async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      // 后端 baseRate/overAchievementBonus 存储为小数（0.05 = 5%），前端输入为百分比，需 /100
      const payload = {
        basis: form.basis,
        baseRate: Number(form.baseRate) / 100,
        overAchievementMultiplier: Number(form.overAchievementMultiplier),
        overAchievementBonus: Number(form.overAchievementBonus) / 100,
        effectiveDate: form.effectiveDate,
        remark: form.remark || null
      }
      await createCommissionRateVersion(payload)
      ElMessage.success(tt('新增提成率版本成功，已生效'))
      dialogVisible.value = false
      loadAll()
    } catch (e) {
      ElMessage.error(e.message || tt('新增失败'))
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => loadAll())
</script>

<style scoped lang="scss">
@import './_kgw-common.scss';

.current-card {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  border: 1px solid #91d5ff;
  border-radius: 4px;
  padding: 16px 20px;
  min-height: 80px;
}
.cv-row {
  display: flex;
  flex-wrap: wrap;
  gap: 20px 36px;
}
.cv-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
}
.cv-label {
  font-size: 12px;
  color: #606266;
}
.cv-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  &.highlight {
    color: #f56c6c;
    font-size: 18px;
    font-weight: 700;
  }
  &.remark {
    font-weight: 400;
    color: #606266;
  }
}

.doc-list {
  margin: 0;
  padding-left: 20px;
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}
.form-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
