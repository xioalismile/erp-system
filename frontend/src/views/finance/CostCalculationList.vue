<template>
  <div class="cost-calc-page">
    <!-- 顶部标题 + 操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-group">
          <div class="page-title">
            成本核算单
            <span class="title-badge">COST CALCULATION</span>
          </div>
          <div class="page-sub">{{ $tt("归集当期成本 · 自动计算单位成本") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="success" :icon="Histogram" v-permission="perm('apply')" @click="handlePeriodCalc">{{ $tt("当期核算") }}</el-button>
        <el-button type="warning" :icon="Download" v-permission="perm('export')" @click="handleExportReport">{{ $tt("导出报表") }}</el-button>
      </div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="stat-cards">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-icon" :class="stat.type">
          <el-icon><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ $tf(stat.label) }}</div>
        </div>
        <div class="stat-glow" :class="stat.type"></div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="content-card">
      <!-- 搜索 + 状态 tab -->
      <div class="search-bar">
        <div class="status-tabs">
          <div
            v-for="tab in statusTabs"
            :key="tab.value"
            :class="['status-tab', { active: activeTab === tab.value }]"
            @click="switchTab(tab.value)"
          >
            <span class="tab-text">{{ $tf(tab.label) }}</span>
            <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
          </div>
        </div>
        <div class="search-right">
          <el-date-picker
            v-model="reportForm.period"
            type="month"
            :placeholder="$tt('核算期间')"
            format="YYYY-MM"
            value-format="YYYY-MM"
            style="width: 150px"
            @change="loadReport"
          />
          <el-input v-model="reportForm.keyword" :placeholder="$tt('物料编码/名称')" clearable class="search-input" @keyup.enter="loadReport">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" v-permission="perm('query')" @click="loadReport">{{ $tt("查询") }}</el-button>
          <el-button v-permission="perm('reset')" @click="handleReportReset">{{ $tt("重置") }}</el-button>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
          </el-tooltip>
        </div>
      </div>

      <!-- 成本报表表格 -->
      <el-table
        :data="filteredReportData"
        border
        class="tech-table"
        :header-cell-style="headerCellStyle"
        show-summary
        :summary-method="getReportSummary"
        row-key="materialId"
        max-height="600"
        v-loading="reportLoading"
        :key="configVersion"
      >
        <el-table-column type="index" label="#" width="50" />
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <!-- 原多级表头（期初/本期入库/本期出库/期末/成本构成分组）拍平为单层平铺列，与列设置抽屉的平铺列定义一一对应 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable ? 'custom' : false"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop) || col.fixed"
          >
            <template v-if="col.type" #default="{ row }">
              <span v-if="col.type === 'code'" class="order-no-text">{{ row[col.prop] }}</span>
              <div v-else-if="col.type === 'customer'" class="customer-cell">
                <div class="customer-avatar">{{ (row[col.prop] || '?').charAt(0) }}</div>
                <div class="customer-info">
                  <div class="customer-name">{{ row[col.prop] }}</div>
                </div>
              </div>
              <span v-else-if="col.type === 'num4'">{{ formatNum(row[col.prop], 4) }}</span>
              <span v-else-if="col.type === 'num2'">{{ formatNum(row[col.prop], 2) }}</span>
              <span v-else-if="col.type === 'num4Neg'" :class="{ 'negative-num': row[col.prop] < 0 }">{{ formatNum(row[col.prop], 4) }}</span>
              <span v-else-if="col.type === 'num2Neg'" :class="{ 'negative-num': row[col.prop] < 0 }">{{ formatNum(row[col.prop], 2) }}</span>
              <span v-else-if="col.type === 'costMaterial'" class="cost-value material-cost">{{ formatNum(row[col.prop], 2) }}</span>
              <span v-else-if="col.type === 'costLabor'" class="cost-value labor-cost">{{ formatNum(row[col.prop], 2) }}</span>
              <span v-else-if="col.type === 'costOverhead'" class="cost-value overhead-cost">{{ formatNum(row[col.prop], 2) }}</span>
              <span v-else-if="col.type === 'unitCost'" class="unit-cost-value">{{ formatNum(row[col.prop], 4) }}</span>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
      </el-table>

      <!-- 空状态提示 -->
      <el-empty v-if="!reportLoading && filteredReportData.length === 0" :description="$tt('请选择核算期间并点击查询')" />

      <!-- 分页 -->
      <div class="pagination-wrap">
        <div class="pagination-info">共 {{ filteredReportData.length }} 条记录</div>
      </div>
    </div>

    <!-- 当期核算确认弹窗 -->
    <el-dialog v-model="periodCalcVisible" :title="$tf('当期核算')" width="480px" :close-on-click-modal="false">
      <el-alert :title="$tt('当期核算将自动归集指定期间所有已生效的费用分摊、领料单、报工单数据，为每个有成本数据的物料自动计算成本。')" type="info" :closable="false" show-icon style="margin-bottom: 20px;" />
      <el-form label-width="100px">
        <el-form-item :label="$tf('核算期间')" required>
          <el-date-picker
            v-model="periodCalcPeriod"
            type="month"
            :placeholder="$tt('请选择核算期间')"
            format="YYYY年MM月"
            value-format="YYYY-MM"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="periodCalcVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="doPeriodCalc" :loading="periodCalcLoading" :disabled="!periodCalcPeriod">{{ $tt("确认核算") }}</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog
      v-model="printVisible"
      bill-type="COST_CALCULATION"
      :bill-id="printBillId"
    />

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'CostCalculationList'" :columns="columns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, Setting, Histogram } from '@element-plus/icons-vue'
import {
  periodCalculate,
  getCostReport
} from '@/api/finance/costCalculation'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:cost-calculation')

const router = useRouter()

// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列
// type 标记单元格渲染分支：code 编码文本、customer 物料头像、num4/num2 数量金额、num4Neg/num2Neg 负数红显、
// costMaterial/costLabor/costOverhead 成本构成（分色）、unitCost 单位成本
const columns = [
  { prop: 'materialCode', label: '物料编码', width: 120, fixed: true, type: 'code' },
  { prop: 'materialName', label: '物料名称', minWidth: 140, fixed: true, type: 'customer' },
  { prop: 'specification', label: '规格型号', width: 120 },
  { prop: 'unit', label: '单位', width: 70, align: 'center' },
  { prop: 'beginQuantity', label: '期初数量', width: 100, align: 'right', type: 'num4' },
  { prop: 'beginAmount', label: '期初金额', width: 120, align: 'right', type: 'num2' },
  { prop: 'inQuantity', label: '本期入库数量', width: 100, align: 'right', type: 'num4' },
  { prop: 'inAmount', label: '本期入库金额', width: 120, align: 'right', type: 'num2' },
  { prop: 'outQuantity', label: '本期出库数量', width: 100, align: 'right', type: 'num4' },
  { prop: 'outAmount', label: '本期出库金额', width: 120, align: 'right', type: 'num2' },
  { prop: 'endQuantity', label: '期末数量', width: 100, align: 'right', type: 'num4Neg' },
  { prop: 'endAmount', label: '期末金额', width: 120, align: 'right', type: 'num2Neg' },
  { prop: 'directMaterialCost', label: '直接材料', width: 110, align: 'right', type: 'costMaterial' },
  { prop: 'directLaborCost', label: '直接人工', width: 110, align: 'right', type: 'costLabor' },
  { prop: 'manufacturingOverhead', label: '制造费用', width: 110, align: 'right', type: 'costOverhead' },
  { prop: 'unitCost', label: '单位成本', width: 120, align: 'right', type: 'unitCost' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('CostCalculationList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

// 报表查询
const reportForm = reactive({
  period: '',
  keyword: ''
})

const reportData = ref([])
const reportLoading = ref(false)
const activeTab = ref('all')

const statusTabs = computed(() => [
  { label: '全部', value: 'all', count: reportData.value.length },
  { label: '有结存', value: 'positive', count: reportData.value.filter(r => Number(r.endQuantity || 0) > 0).length },
  { label: '负结存', value: 'negative', count: reportData.value.filter(r => Number(r.endQuantity || 0) < 0).length },
  { label: '零结存', value: 'zero', count: reportData.value.filter(r => Number(r.endQuantity || 0) === 0).length }
])

const filteredReportData = computed(() => {
  if (activeTab.value === 'all') return reportData.value
  if (activeTab.value === 'positive') return reportData.value.filter(r => Number(r.endQuantity || 0) > 0)
  if (activeTab.value === 'negative') return reportData.value.filter(r => Number(r.endQuantity || 0) < 0)
  if (activeTab.value === 'zero') return reportData.value.filter(r => Number(r.endQuantity || 0) === 0)
  return reportData.value
})

const stats = computed(() => [
  { label: '物料种类', value: reportData.value.length, type: 'primary', icon: 'Document' },
  { label: '期末金额合计', value: `¥${formatNum(reportData.value.reduce((s, r) => s + Number(r.endAmount || 0), 0), 2)}`, type: 'success', icon: 'Coin' },
  { label: '直接材料', value: `¥${formatNum(reportData.value.reduce((s, r) => s + Number(r.directMaterialCost || 0), 0), 2)}`, type: 'info', icon: 'Box' },
  { label: '制造费用', value: `¥${formatNum(reportData.value.reduce((s, r) => s + Number(r.manufacturingOverhead || 0), 0), 2)}`, type: 'warning', icon: 'Tools' }
])

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

const printVisible = ref(false)
const printBillId = ref(null)

function switchTab(value) {
  activeTab.value = value
}

async function loadReport() {
  if (!reportForm.period) {
    ElMessage.warning(tt('请选择核算期间'))
    return
  }
  reportLoading.value = true
  try {
    const res = await getCostReport(reportForm.period, reportForm.keyword)
    reportData.value = res || []
  } catch (e) {
    console.error('加载成本报表失败', e)
    ElMessage.error(e.message || tt('加载成本报表失败'))
  } finally {
    reportLoading.value = false
  }
}

function handleReportReset() {
  reportForm.period = ''
  reportForm.keyword = ''
  reportData.value = []
  activeTab.value = 'all'
}

function formatNum(val, decimals = 2) {
  const num = Number(val || 0)
  return num.toFixed(decimals)
}

// 合计行
function getReportSummary({ columns, data }) {
  const sums = []
  columns.forEach((column, index) => {
    if (index === 0) {
      sums[index] = '合计'
      return
    }
    if (index === 1) {
      sums[index] = `${data.length} 种物料`
      return
    }
    const prop = column.property
    const sumFields = ['beginQuantity', 'beginAmount', 'inQuantity', 'inAmount', 'outQuantity', 'outAmount', 'endQuantity', 'endAmount', 'directMaterialCost', 'directLaborCost', 'manufacturingOverhead']
    if (sumFields.includes(prop)) {
      const total = data.reduce((sum, row) => sum + Number(row[prop] || 0), 0)
      sums[index] = total.toFixed(prop.includes('Quantity') ? 4 : 2)
    } else if (prop === 'unitCost') {
      const totalEndAmount = data.reduce((sum, row) => sum + Number(row.endAmount || 0), 0)
      const totalEndQty = data.reduce((sum, row) => sum + Number(row.endQuantity || 0), 0)
      sums[index] = totalEndQty > 0 ? (totalEndAmount / totalEndQty).toFixed(4) : '0.0000'
    } else {
      sums[index] = ''
    }
  })
  return sums
}

// 导出报表
function handleExportReport() {
  if (!reportForm.period || reportData.value.length === 0) {
    ElMessage.warning(tt('请先查询报表数据后再导出'))
    return
  }
  const headers = ['物料编码', '物料名称', '规格型号', '单位', '期初数量', '期初金额', '本期入库数量', '本期入库金额', '本期出库数量', '本期出库金额', '期末数量', '期末金额', '单位成本', '直接材料', '直接人工', '制造费用']
  const fields = ['materialCode', 'materialName', 'specification', 'unit', 'beginQuantity', 'beginAmount', 'inQuantity', 'inAmount', 'outQuantity', 'outAmount', 'endQuantity', 'endAmount', 'unitCost', 'directMaterialCost', 'directLaborCost', 'manufacturingOverhead']

  const rows = reportData.value.map(row => {
    return fields.map(f => {
      const val = row[f]
      if (f === 'materialCode' || f === 'materialName' || f === 'specification' || f === 'unit') {
        return val || ''
      }
      return Number(val || 0).toFixed(f.includes('Quantity') || f === 'unitCost' ? 4 : 2)
    }).join(',')
  })

  const totalEndAmount = reportData.value.reduce((s, r) => s + Number(r.endAmount || 0), 0)
  const totalEndQty = reportData.value.reduce((s, r) => s + Number(r.endQuantity || 0), 0)
  const totalRow = [
    '合计', `${reportData.value.length} 种物料`, '', '',
    formatNum(reportData.value.reduce((s, r) => s + Number(r.beginQuantity || 0), 0), 4),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.beginAmount || 0), 0), 2),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.inQuantity || 0), 0), 4),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.inAmount || 0), 0), 2),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.outQuantity || 0), 0), 4),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.outAmount || 0), 0), 2),
    formatNum(totalEndQty, 4),
    formatNum(totalEndAmount, 2),
    totalEndQty > 0 ? (totalEndAmount / totalEndQty).toFixed(4) : '0.0000',
    formatNum(reportData.value.reduce((s, r) => s + Number(r.directMaterialCost || 0), 0), 2),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.directLaborCost || 0), 0), 2),
    formatNum(reportData.value.reduce((s, r) => s + Number(r.manufacturingOverhead || 0), 0), 2)
  ].join(',')

  const bom = '\uFEFF'
  const csvContent = bom + [headers.join(','), ...rows, totalRow].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `成本报表_${reportForm.period}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
  ElMessage.success(tt('报表导出成功'))
}

// 当期核算
const periodCalcVisible = ref(false)
const periodCalcPeriod = ref('')
const periodCalcLoading = ref(false)

function handlePeriodCalc() {
  periodCalcPeriod.value = reportForm.period || ''
  periodCalcVisible.value = true
}

async function doPeriodCalc() {
  if (!periodCalcPeriod.value) {
    ElMessage.warning(tt('请选择核算期间'))
    return
  }
  try {
    await ElMessageBox.confirm(
      `${tt("确认对 ")}${periodCalcPeriod.value}${tt(" 期间进行当期核算？系统将自动归集该期间所有已生效的费用分摊、领料单、报工单数据。")}`,
      tt('当期核算确认'),
      { type: 'warning', confirmButtonText: tt('确认核算'), cancelButtonText: tt('取消') }
    )
    periodCalcLoading.value = true
    const res = await periodCalculate(periodCalcPeriod.value)
    periodCalcLoading.value = false
    periodCalcVisible.value = false
    ElMessage.success(`${tt("当期核算完成！共处理 ")}${res.totalMaterials || 0}${tt(" 个物料")}`)
    // 核算完成后自动刷新报表
    reportForm.period = periodCalcPeriod.value
    loadReport()
  } catch (e) {
    periodCalcLoading.value = false
    if (e !== 'cancel') ElMessage.error(e.message || tt('当期核算失败'))
  }
}

onMounted(() => {
  // 默认查询当前月份
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  reportForm.period = `${y}-${m}`
  loadReport()
})
useAutoRefresh(() => {
  // 默认查询当前月份
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  reportForm.period = `${y}-${m}`
  loadReport()
})
</script>

<style scoped lang="scss">
.cost-calc-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #6b7280;
    font-size: 13px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 6px;
    background: #fff;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
    }
  }

  .title-group { display: flex; flex-direction: column; gap: 2px; }

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title-badge {
    font-size: 10px;
    font-weight: 600;
    color: #6366f1;
    background: linear-gradient(135deg, #e0e7ff, #f0f4ff);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 1px;
  }

  .page-sub { font-size: 12px; color: #9ca3af; }

  .header-actions { display: flex; gap: 8px; }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .search-input {
    width: 240px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }
}

.status-tabs {
  display: flex;
  gap: 4px;

  .status-tab {
    padding: 8px 16px;
    font-size: 13px;
    color: #6b7280;
    cursor: pointer;
    border-radius: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    transition: all .2s;

    &:hover { color: #6366f1; background: #f5f3ff; }

    &.active {
      color: #6366f1;
      background: linear-gradient(135deg, #eef2ff, #f5f3ff);
      font-weight: 600;
    }

    .tab-count {
      font-size: 11px;
      background: #e5e7eb;
      color: #6b7280;
      padding: 1px 6px;
      border-radius: 10px;
    }

    &.active .tab-count {
      background: #6366f1;
      color: #fff;
    }
  }
}

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }

  :deep(.el-table__row:hover > td) {
    background: rgba(99, 102, 241, 0.03) !important;
  }

  :deep(.el-table td) {
    border-bottom: 1px solid #f3f4f6;
  }

  :deep(.el-table__footer-wrapper .el-table__footer td) {
    background: #f5f7fa !important;
    color: #111827;
    font-weight: 600;
  }
}

.order-no-text {
  color: #6366f1;
  font-weight: 500;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
}

.customer-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .customer-avatar {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .customer-name {
    font-size: 13px;
    color: #374151;
    font-weight: 500;
  }
}

.cost-value {
  font-weight: 600;
  font-size: 13px;
}

.material-cost { color: #6366f1; }
.labor-cost { color: #10b981; }
.overhead-cost { color: #f59e0b; }

.unit-cost-value {
  color: #6366f1;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.negative-num {
  color: #ef4444;
  font-weight: 600;
}

.action-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #6b7280;
  }
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .cost-calc-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .status-tabs { flex-wrap: wrap; }
}
</style>
