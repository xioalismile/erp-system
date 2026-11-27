<template>
  <el-button :type="btnType" :size="size" :disabled="disabled" v-permission="perm('pick')" @click="openDialog">
    选单
  </el-button>

  <!-- 扫码带出：扫上游单据二维码 → 自动定位到该源单并勾选，确认后带入（2026-09-27 D4） -->
  <el-button :size="size" :disabled="disabled" v-permission="perm('pick')" @click="openScan">
    {{ $tt("扫码带出") }}
  </el-button>

  <el-dialog v-model="visible" :title="$tf('选单 - 选择源单据')" width="1000px" :close-on-click-modal="false">
    <!-- 步骤1: 选择源单类型 -->
    <div class="picker-step" v-if="step === 1">
      <div class="step-title">{{ $tt("请选择源单类型") }}</div>
      <div class="source-types">
        <el-card
          v-for="st in sourceTypes"
          :key="st.sourceType"
          shadow="hover"
          class="type-card"
          @click="selectSourceType(st)"
        >
          <el-icon class="type-icon"><Document /></el-icon>
          <div class="type-name">{{ getDocType(st.sourceType)?.name || st.sourceType }}</div>
          <div class="type-desc">下推至 {{ getDocType(st.targetType)?.name }}</div>
        </el-card>
        <el-empty v-if="sourceTypes.length === 0" :description="$tt('当前单据无可用源单类型')" />
      </div>
    </div>

    <!-- 步骤2: 单一表格 - 明细展开为多行 -->
    <div class="picker-step" v-else-if="step === 2">
      <div class="step-header">
        <el-button text v-permission="perm('dlgCancel')" @click="step = 1">{{ $tt("◀ 返回") }}</el-button>
        <span class="step-title">源单类型: {{ getDocType(currentSourceType)?.name }} (仅显示已生效)</span>
      </div>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="keyword"
          :placeholder="$tt('搜索: 单号/客户/物料编码/名称/规格/备注')"
          clearable
          style="width: 380px"
          @keyup.enter="loadAllData"
          prefix-icon="Search"
        />
        <el-date-picker
          v-model="searchDate"
          type="date"
          :placeholder="$tt('选择日期')"
          style="width: 160px"
          value-format="YYYY-MM-DD"
        />
        <el-button type="primary" v-permission="perm('query')" @click="loadAllData">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" @click="resetSearch">{{ $tt("重置") }}</el-button>
      </div>

      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button size="small" v-permission="perm('selectAll')" @click="selectAllRows">{{ $tt("全选") }}</el-button>
        <el-button size="small" v-permission="perm('clear')" @click="clearSelection">{{ $tt("清空") }}</el-button>
        <el-button size="small" v-permission="perm('fill')" @click="fillRemainQty">{{ $tt("剩余数量全填") }}</el-button>
        <span class="action-info">{{ $tt("已选") }} <b>{{ pickedRowCount }}</b> {{ $tt("行，共") }} <b>{{ formatWith(pickedTotalQty, scaleFor('qty'), trimZero.value) }}</b> {{ $tt("数量") }}</span>
      </div>

      <!-- 明细表格 -->
      <el-table
        ref="detailTableRef"
        :data="allDetailRows"
        v-loading="loading"
        border
        stripe
        size="small"
        style="width: 100%"
        height="420"
        row-key="rowKey"
        @selection-change="onSelectionChange"
        :empty-text="$tt('暂无可选源单明细')"
      >
        <el-table-column type="selection" width="42" :selectable="canPick" />
        <el-table-column type="index" label="#" width="40" />
        <el-table-column :prop="noField" :label="getDocType(currentSourceType)?.name + '号'" width="170" />
        <el-table-column prop="customerName" :label="$tf('客户/供应商')" width="130" />
        <el-table-column prop="orderDate" :label="$tf('日期')" width="95" />
        <el-table-column prop="materialCode" :label="$tf('物料编码')" width="120" />
        <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="130" />
        <el-table-column prop="specification" :label="$tf('规格')" width="95" />
        <el-table-column prop="unit" :label="$tf('单位')" width="55" align="center" />
        <!-- 行备注：与电脑端明细列同一字段（个别单据是 rowRemark） -->
        <el-table-column :label="$tf('行备注')" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ remarkOf(row) }}</template>
        </el-table-column>
        <!-- 明细自定义字段：定义与显示口径与明细表一致（设置中心「自定义字段」，默认按"列表显示"开关） -->
        <el-table-column
          v-for="col in itemCfColumns"
          :key="'cf-' + col.prop"
          :label="col.label"
          :width="col.width || 140"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ cfText(col, row) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('原数量')" width="85" align="right">
          <template #default="{ row }">{{ formatWith(row[qtyField], scaleFor('qty'), trimZero.value) }}</template>
        </el-table-column>
        <el-table-column v-if="turnedField" :label="turnedLabel" width="85" align="right">
          <template #default="{ row }">{{ formatWith(turnedOf(row), scaleFor('qty'), trimZero.value) }}</template>
        </el-table-column>
        <el-table-column v-if="turnedField" :label="$tf('剩余数量')" width="85" align="right">
          <template #default="{ row }">
            <span :class="{ 'zero-qty': calcRemain(row) <= 0 }">{{ formatWith(calcRemain(row), scaleFor('qty'), trimZero.value) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('本次数量')" width="110" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row._pickQty"
              :min="0"
              :max="allowOverQty ? undefined : calcRemain(row)"
              :precision="scaleFor('qty')"
              :controls="false"
              size="small"
              style="width: 100%"
              :disabled="row._mutexLocked || (!allowOverQty && calcRemain(row) <= 0)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" :label="$tf('单价')" width="85" align="right" />
        <el-table-column :label="$tf('金额')" width="95" align="right">
          <template #default="{ row }">
            {{ Number((row._pickQty || 0) * (row.unitPrice || 0)).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._mutexLocked" type="danger" size="small" effect="dark">{{ $tt("已互斥") }}</el-tag>
            <el-tag v-else-if="!allowOverQty && calcRemain(row) <= 0" type="info" size="small">{{ $tt("已完成") }}</el-tag>
            <el-tag v-else type="success" size="small">{{ $tt("可选") }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next"
        size="small"
        style="margin-top: 8px; justify-content: flex-end; display: flex"
        @current-change="loadAllData"
      />
    </div>

    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="visible = false">{{ $tt("取消") }}</el-button>
      <el-button v-if="step === 2" type="primary" v-permission="perm('dlgConfirm')" :disabled="pickedRowCount === 0" @click="confirmPick">
        确认带入 ({{ pickedRowCount }} 行)
      </el-button>
    </template>
  </el-dialog>

  <!-- 扫码带出：电脑端扫码枪（等同键盘输入）或粘贴码内容 -->
  <el-dialog v-model="scanVisible" :title="$tf('扫码带出（扫上游单据二维码）')" width="460px" append-to-body>
    <div class="scan-tip">{{ $tt("用扫码枪扫上游单据的二维码，或粘贴码内容后回车") }}</div>
    <el-input
      ref="scanInputRef"
      v-model="scanCode"
      :placeholder="$tt('扫码 / 粘贴二维码内容')"
      clearable
      @keyup.enter="doScanResolve"
    />
    <div class="scan-tip scan-tip--muted">
      {{ $tt("手机端请用相机扫码，会自动打开对应单据；本入口用于电脑端扫码枪或复制粘贴") }}
    </div>
    <template #footer>
      <el-button @click="scanVisible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" @click="doScanResolve">{{ $tt("确定") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { getSourceTypes, getRelation, getDocType } from '@/utils/pushRelations'
import { getListApi, getGetApi } from '@/utils/pushApiRegistry'
import { parseScanCode, toRelationKey } from '@/utils/billScan'
import scanCodeApi from '@/api/system/scanCode'
import { checkInvoiceMutex } from '@/api/sale/saleInvoice'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { loadCustomFieldDefinitions, batchLoadCustomFieldValues, customFieldsToTableColumns } from '@/composables/useCustomField'
import { usePrecision } from '@/composables/usePrecision'
import { expandSubstitutePlans } from '@/utils/substitutePlanExpand'

// 数量显示/输入的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor, formatWith, trimZero } = usePrecision()

const perm = usePagePerm()


const props = defineProps({
  targetType: { type: String, required: true },
  btnType: { type: String, default: 'primary' },
  size: { type: String, default: 'default' },
  disabled: { type: Boolean, default: false },
  // 源单「明细数组」所在字段名（默认 items；生产工单详情是 materials / products，不是 items）
  itemsField: { type: String, default: 'items' },
  // 明细行「原始数量」所在字段名（默认 quantity；工单用料行是 requiredQuantity）
  qtyField: { type: String, default: 'quantity' },
  // 源单状态过滤：不传/空 = 保持历史行为（固定查「已生效」）；传数组 = 逐个状态查询后合并
  // （生产工单没有「已生效」状态，真实状态为 已下达/执行中/已完工，只查「已生效」会恒 0 条）
  sourceStatus: { type: [String, Array], default: '' },
  // 是否允许本次数量超过来源单剩余数量（2026-11-09：由设置中心「生产参数-超过来源单数量」决定）
  allowOverQty: { type: Boolean, default: false },
  // 超量时是否给出预警提示（不控制=false，预警提示=true，不允许=allowOverQty 为 false）
  overQtyWarn: { type: Boolean, default: false }
})

const emit = defineEmits(['pick'])

const visible = ref(false)
const step = ref(1)
const keyword = ref('')
const searchDate = ref('')
const loading = ref(false)
const allDetailRows = ref([])
const detailTableRef = ref(null)
const selectedRows = ref([])
const currentSourceType = ref('')
const page = reactive({ current: 1, size: 20, total: 0 })

// ===== 扫码带出（2026-09-27 D4）=====
const scanVisible = ref(false)
const scanCode = ref('')
const scanInputRef = ref(null)

function openScan() {
  scanCode.value = ''
  scanVisible.value = true
  nextTick(() => scanInputRef.value?.focus())
}

async function doScanResolve() {
  const parsed = parseScanCode(scanCode.value)
  if (!parsed) {
    ElMessage.warning(tt('不是本系统的二维码'))
    return
  }
  if (parsed.type !== 'BILL') {
    ElMessage.warning(tt('请扫上游单据的二维码（当前不是单据码）'))
    return
  }
  const key = toRelationKey(parsed.docType)
  if (!key || parsed.id == null) {
    ElMessage.warning(tt('二维码内容不完整，无法识别单据'))
    return
  }

  // 服务端校验（P1）：签名防伪造 / 单据作废 / 落地权限，并把本次扫码记入审计
  try {
    const check = await scanCodeApi.resolve(parsed.type, parsed.docType, parsed.id, parsed.signature)
    if (check && check.valid === false) {
      ElMessage.error(check.invalidReason || tt('该二维码不可用'))
      return
    }
  } catch (e) {
    ElMessage.error(tt('二维码校验失败，请重试'))
    return
  }

  if (!sourceTypes.value.some(s => s.sourceType === key)) {
    const name = getDocType(key)?.name || parsed.docType
    ElMessage.warning(`${tt('这张单据不能下推到当前单据')}：${name}`)
    return
  }
  scanVisible.value = false
  await scanToPick(key, parsed.id)
}

/** 扫码定位到指定源单：只加载这一张，自动勾选可带出行，用户确认后走同一套带入逻辑 */
async function scanToPick(sourceTypeKey, docId) {
  const getApi = getGetApi(sourceTypeKey)
  if (!getApi) {
    ElMessage.warning(tt('该单据暂不支持扫码带出'))
    return
  }
  loading.value = true
  try {
    const fullDoc = await getApi(docId)
    if (!fullDoc) {
      ElMessage.warning(tt('没找到这张单据'))
      return
    }
    // 状态校验：与"选单"口径一致（默认只允许已生效；页面可传 source-status 放宽）
    const statusList = Array.isArray(props.sourceStatus)
      ? props.sourceStatus.filter(Boolean)
      : (props.sourceStatus ? [props.sourceStatus] : ['已生效'])
    const docStatus = fullDoc.status
    if (docStatus && !statusList.includes(docStatus)) {
      ElMessage.warning(`${tt('该单据状态为')}「${docStatus}」，${tt('不能下推（需')}：${statusList.join('/')}）`)
      return
    }

    const srcIdField = getDocType(sourceTypeKey)?.idField
    const docIdVal = fullDoc[srcIdField]
    if (docIdVal != null && String(docIdVal) !== String(docId)) {
      ElMessage.warning(tt('二维码与单据不匹配，可能二维码已过期'))
      return
    }

    currentSourceType.value = sourceTypeKey
    step.value = 2
    keyword.value = ''
    searchDate.value = ''
    page.current = 1
    page.total = 1

    const items = fullDoc[props.itemsField] || []
    const rows = []
    items.forEach((item, idx) => {
      const remain = calcRemain(item)
      rows.push({
        ...item,
        rowKey: `${docIdVal}-${idx}`,
        _pickQty: remain > 0 ? remain : 0,
        _mutexLocked: false,
        _sourceDoc: fullDoc,
        orderDate: fullDoc.orderDate || fullDoc.quoteDate || fullDoc.contractDate || '',
        customerName: fullDoc.customerName || fullDoc.supplierName || '',
        [noField.value]: fullDoc[noField.value]
      })
    })
    await markMutexLocked(rows)
    await attachItemCustomFields(sourceTypeKey, rows)
    allDetailRows.value = rows

    visible.value = true
    await nextTick()
    detailTableRef.value?.clearSelection()
    await nextTick()
    const selectable = rows.filter(canPick(r))
    selectable.forEach(r => detailTableRef.value?.toggleRowSelection(r, true))
    if (selectable.length === 0) {
      ElMessage.warning(tt('这张单据已无可带出的明细（可能已全部下推完成）'))
    }
  } catch (e) {
    ElMessage.error(tt('扫码带出失败'))
    console.error('scanToPick error:', e)
  } finally {
    loading.value = false
  }
}

const TURNED_QTY_MAP = {
  'SaleQuote': {
    'SaleOrder': { field: 'turnedOrderQuantity', label: '已下单' }
  },
  'SaleOrder': {
    'SaleDelivery': { field: 'turnedOutputQuantity', label: '已出库' },
    'SaleInvoice':  { field: 'turnedInvoiceQuantity', label: '已开票' },
    'SaleReturn':   { field: 'turnedReturnQuantity', label: '已退货' },
    // 2026-11-07：生产工单占用的是「已下推生产数量」，原读 turnedOutputQuantity（已出库）导致下推后仍显示未扣减
    'WorkOrders':   { field: 'turnedProduceQuantity', label: '已排产' }
  },
  'SaleDelivery': {
    'SaleReturn':  { field: 'turnedReturnQuantity', label: '已退货' },
    'SaleInvoice': { field: 'turnedInvoiceQuantity', label: '已开票' }
  },
  'SaleReturn': {
    'SaleInvoice': { field: 'turnedInvoiceQuantity', label: '已开票' }
  },
  'PurchaseOrder': {
    'PurchaseInbound': { field: 'turnedInputQuantity', label: '已入库' },
    'PurchaseInvoice': { field: 'invoicedQuantity', label: '已开票' },
    'PurchaseReturn':  { field: 'turnedReturnQuantity', label: '已退货' }
  },
  'PurchaseInbound': {
    'PurchaseReturn':  { field: 'turnedReturnQuantity', label: '已退货' },
    'PurchaseInvoice': { field: 'invoicedQuantity', label: '已开票' }
  },
  'SubcontractOrder': {
    'SubcontractPick':    { field: 'turnedPickQuantity', label: '已发料' },
    'SubcontractInbound': { field: 'turnedInboundQuantity', label: '已入库' }
  },
  'SubcontractPick': {
    'SubcontractReturn':     { field: 'turnedReturnQuantity', label: '已退料' },
    'SubcontractSupplement': { field: 'turnedSupplementQuantity', label: '已补料' }
  },
  // 生产工单作为源单（2026-11-07）：按标准「下推即占用」口径 —— 已下推含草稿/待审核，
  // 实际已领料/已入库仍只在下游生效后增加（fallback 兜底历史数据）。
  // pushedQuantity 由工单详情接口按行汇总（下游草稿+待审核+已生效 − 已作废）。
  'WorkOrders': {
    'MaterialRequisitions': { field: 'pushedQuantity', fallback: 'issuedQuantity', label: '已下推' },
    'ProductionReceipts':   { field: 'pushedQuantity', fallback: 'completedQuantity', label: '已下推' }
  }
}

/**
 * 发票选单互斥规则（方案A：单一路径互斥）：
 * 销售订单与销售出库单"二选一"——只要其中一条路径已被任何发票引用过，另一条路径就互斥。
 *
 * 互斥判断需要跨表查询（订单的出库明细已开票/出库的源订单已开票），前端无法独立完成。
 * 前端只做能做的过滤，最终互斥校验由后端 validateInvoiceSourceMutex 兜底。
 *
 * 前端可做的过滤：本明细行自身的 turnedInvoiceQuantity > 0 已通过"剩余数量=0"自动过滤。
 * 此处返回 false 表示不在前端做额外过滤，全部交给后端校验。
 */
function isMutexExcluded(row) {
  // 互斥逻辑由后端校验兜底（需跨表查询），前端不做额外过滤
  return false
}

/**
 * 销售发票选单场景下需要做互斥标记的源单类型映射。
 * 仅当 targetType === 'SaleInvoice' 且 sourceType 是以下类型时才调用互斥检查API。
 */
const MUTEX_SOURCE_TYPE_MAP = {
  'SaleOrder': '销售订单',
  'SaleDelivery': '销售出库单'
}

/**
 * 调用后端互斥检查API，标记被锁定的明细行。
 * - 销售订单源单：该订单明细的任一出库明细已被开票 → 锁定
 * - 销售出库单源单：源订单明细已被任何发票直接引用 → 锁定
 */
async function markMutexLocked(rows) {
  if (!rows || rows.length === 0) return
  if (props.targetType !== 'SaleInvoice') return
  const backendSourceType = MUTEX_SOURCE_TYPE_MAP[currentSourceType.value]
  if (!backendSourceType) return

  // 取出本明细行对应的源单明细ID（按源单类型不同取不同字段）
  const idFieldMap = {
    'SaleOrder': 'orderItemId',
    'SaleDelivery': 'deliveryItemId'
  }
  const idField = idFieldMap[currentSourceType.value]
  if (!idField) return

  const ids = rows.map(r => r[idField]).filter(id => id != null)
  if (ids.length === 0) return

  try {
    const lockedIds = await checkInvoiceMutex(backendSourceType, ids)
    const lockedSet = new Set(lockedIds.map(id => Number(id)))
    rows.forEach(r => {
      if (lockedSet.has(Number(r[idField]))) {
        r._mutexLocked = true
        r._pickQty = 0
      }
    })
  } catch (e) {
    console.warn('互斥检查API调用失败，跳过前端标记（后端保存时仍会兜底校验）:', e)
  }
}

const turnedFieldMeta = computed(() => {
  if (!currentSourceType.value || !props.targetType) return null
  return TURNED_QTY_MAP[currentSourceType.value]?.[props.targetType] || null
})

const turnedField = computed(() => turnedFieldMeta.value?.field || null)
const turnedLabel = computed(() => turnedFieldMeta.value?.label || '已转')
// 明细行「原始数量」的字段名（默认 quantity；工单用料行是 requiredQuantity）
const qtyField = computed(() => props.qtyField || 'quantity')

/** 已转/已下推数量：主字段为空时回退 fallback（历史单据兜底，不把空值当 0 覆盖真实已转量） */
function turnedOf(row) {
  const meta = turnedFieldMeta.value
  if (!meta || !row) return 0
  const raw = row[meta.field]
  if (raw !== null && raw !== undefined && raw !== '') return Number(raw) || 0
  const fb = meta.fallback ? row[meta.fallback] : null
  return Number(fb) || 0
}

function calcRemain(row) {
  const qty = Number(row[qtyField.value] || 0)
  const turned = turnedOf(row)
  // 2026-09-20 修复：存量数据里"已转数量 > 原数量"（如历史入库明细缺工单行号导致 completed_quantity 回写偏差）
  // 会让剩余量为负 → 模板里 :max 小于 :min="0" → Element Plus 抛
  // 「[InputNumber] min should not be greater than max」并污染控制台。此处下限截断为 0：
  // 剩余量为负 = 已超额完成，语义上就是"不能再选"，与 selectable(>0) / disabled(<=0) 的既有判断一致。
  return Math.max(0, qty - turned)
}

/** 该行是否可勾选：允许超量时，剩余为 0 也能继续转入 */
function canPick(row) {
  return !!row && !row._mutexLocked && (props.allowOverQty || calcRemain(row) > 0)
}

const pickedRowCount = computed(() => selectedRows.value.length)

const pickedTotalQty = computed(() => {
  return selectedRows.value.reduce((sum, r) => sum + Number(r._pickQty || 0), 0)
})

function onSelectionChange(rows) {
  selectedRows.value = rows
}

async function selectAllRows() {
  if (!detailTableRef.value) return
  const selectable = allDetailRows.value.filter(canPick(r))
  selectable.forEach(r => { r._pickQty = calcRemain(r) })
  detailTableRef.value.clearSelection()
  await nextTick()
  selectable.forEach(r => detailTableRef.value.toggleRowSelection(r, true))
}

function clearSelection() {
  if (!detailTableRef.value) return
  detailTableRef.value.clearSelection()
  allDetailRows.value.forEach(r => { r._pickQty = 0 })
}

async function fillRemainQty() {
  if (!detailTableRef.value) return
  const selectable = allDetailRows.value.filter(canPick(r))
  selectable.forEach(r => { r._pickQty = calcRemain(r) })
  // 2026-11-07：全填必须同时勾选，否则只填数量不选中，点确定会提示"请至少勾选一行明细"
  detailTableRef.value.clearSelection()
  await nextTick()
  selectable.forEach(r => detailTableRef.value.toggleRowSelection(r, true))
}

const sourceTypes = computed(() => getSourceTypes(props.targetType))
const noField = computed(() => getDocType(currentSourceType.value)?.noField || 'orderNo')

// ===== 选单：源明细的「行备注」与「自定义字段」（2026-10-07）=====
// 背景：选单表格原来只有基础字段，行备注与明细自定义字段都看不到；带入后行备注还会丢失。
// 口径：字段与取值全部照电脑端既有实现——实体名取自设置中心「自定义字段」清单，
//       取值走 batchLoadCustomFieldValues（与明细表/采购订单带入用同一套），
//       列默认口径与明细表一致（isListShow=1 才默认显示，其余在设置中心开关里控制）。
/** 源单类型 → 明细自定义字段实体（逐字取自设置中心「自定义字段」实体清单；清单里没有的说明该单据无明细实体） */
const DOC_ITEM_ENTITY = {
  SaleQuote: 'sale_quote_item',
  SaleContract: 'sale_contract_item',
  SaleOrder: 'sale_order_item',
  SaleDelivery: 'sale_delivery_item',
  SaleReturn: 'sale_return_item',
  SaleInvoice: 'sale_invoice_item',
  PurchaseRequest: 'purchase_request_item',
  PurchaseOrder: 'purchase_order_item',
  PurchaseContract: 'purchase_contract_item',
  PurchaseInbound: 'purchase_inbound_item',
  PurchaseReturn: 'purchase_return_item',
  PurchaseInvoice: 'purchase_invoice_item',
  StockTransfer: 'stock_transfer_item',
  StockCheck: 'stock_check_item',
  OtherReceipt: 'other_receipt_item',
  OtherIssue: 'other_issue_item',
  MaterialRequisitions: 'material_requisition_item'
}

const itemCfDefs = ref([])
/** 明细自定义字段列（与明细表同一函数生成，列设置元数据同源） */
const itemCfColumns = computed(() => {
  if (!itemCfDefs.value.length) return []
  return customFieldsToTableColumns(itemCfDefs.value, { editable: false, listShowOnly: true })
})

/** 明细行主键：取以 ItemId 结尾的字段（如实测的 orderItemId / quoteItemId / deliveryItemId） */
function itemIdOf(row) {
  const k = Object.keys(row || {}).find(x => /ItemId$/.test(x))
  return k ? row[k] : null
}

/** 行备注：电脑端明细列的备注字段，个别单据用 rowRemark（与 pushRelations 的写法一致） */
function remarkOf(row) {
  if (!row) return ''
  return row.remark != null && row.remark !== '' ? row.remark : (row.rowRemark || '')
}

/** 自定义字段值的展示文本（开关→是/否；下拉→选项名）；空值返回空串 */
function cfText(col, row) {
  const raw = row && row.__cf ? row.__cf[col.prop] : undefined
  if (raw === null || raw === undefined || raw === '') return ''
  if (col.fieldType === 'switch') return Number(raw) === 1 ? tt('是') : tt('否')
  if (col.fieldType === 'select') {
    const hit = (col.options || []).find(o => String(o.value) === String(raw))
    return hit ? hit.label : String(raw)
  }
  return String(raw)
}

/** 按源单类型加载明细自定义字段定义，并给每行补上值（取不到不影响选单本身） */
async function attachItemCustomFields(sourceType, rows) {
  itemCfDefs.value = []
  if (!Array.isArray(rows) || rows.length === 0) return
  const entity = DOC_ITEM_ENTITY[sourceType]
  if (!entity) return
  try {
    // 该函数返回的是 { sections, fieldMap, formulaFields }，字段定义在 fieldMap 里
    const res = await loadCustomFieldDefinitions(entity)
    const defs = res && res.fieldMap ? Array.from(res.fieldMap.values()) : []
    itemCfDefs.value = defs.filter(d => d && d.fieldKey)
  } catch (e) {
    itemCfDefs.value = []
    return
  }
  if (!itemCfDefs.value.length) return
  const ids = rows.map(itemIdOf).filter(v => v != null && v !== '')
  if (!ids.length) return
  try {
    const map = await batchLoadCustomFieldValues(entity, ids)
    if (!map) return
    rows.forEach(r => {
      const id = itemIdOf(r)
      const vals = id == null ? null : (map[String(id)] || map[id])
      if (vals && typeof vals === 'object') r.__cf = { ...vals }
    })
  } catch (e) {
    // 值取不到时，列仍展示（内容为空），不阻断选单
  }
}


function statusTag(s) {
  return { '已生效': 'success', '已关闭': 'info', '草稿': 'info', '待审核': 'warning' }[s] || 'info'
}

function openDialog() {
  visible.value = true
  step.value = 1
  keyword.value = ''
  searchDate.value = ''
  selectedRows.value = []
  allDetailRows.value = []
  if (sourceTypes.value.length === 1) {
    selectSourceType(sourceTypes.value[0])
  }
}

async function selectSourceType(st) {
  currentSourceType.value = st.sourceType
  step.value = 2
  page.current = 1
  await loadAllData()
}

function resetSearch() {
  keyword.value = ''
  searchDate.value = ''
  page.current = 1
  loadAllData()
}

async function loadAllData() {
  const listApi = getListApi(currentSourceType.value)
  const getApi = getGetApi(currentSourceType.value)
  if (!listApi || !getApi) {
    // 配置缺失时静默处理，表格展示空状态即可
    console.warn('SourcePicker: 源单 API 未注册，类型:', currentSourceType.value)
    allDetailRows.value = []
    page.total = 0
    return
  }
  loading.value = true
  selectedRows.value = []
  try {
    // 源单状态过滤：默认（未传 source-status）仍固定查「已生效」，与历史行为逐字一致；
    // 页面显式传状态（字符串/数组）时逐个状态查询后合并——生产工单没有「已生效」状态
    // （真实状态：已下达/执行中/已完工），只查「已生效」会恒返回 0 条。
    const statusList = Array.isArray(props.sourceStatus)
      ? props.sourceStatus.filter(Boolean)
      : (props.sourceStatus ? [props.sourceStatus] : ['已生效'])

    const srcIdField = getDocType(currentSourceType.value)?.idField
    const detailPromises = []
    let total = 0
    for (const st of statusList) {
      const params = {
        current: page.current,
        size: page.size,
        status: st,
        keyword: keyword.value || undefined
      }
      const listRes = await listApi(params)
      const sourceRecords = listRes.records || listRes || []
      total += listRes.total || sourceRecords.length
      sourceRecords.forEach(doc => {
        detailPromises.push(getApi(doc[srcIdField]).then(fullDoc => ({
          fullDoc,
          items: fullDoc[props.itemsField] || []
        })))
      })
    }
    page.total = total

    const results = await Promise.all(detailPromises)
    const allRows = []
    results.forEach(({ fullDoc, items }) => {
      const filteredItems = items.filter(it => !isMutexExcluded(it))
      const docAllTurned = filteredItems.every(it => {
        const qty = Number(it[qtyField.value] || 0)
        return qty - turnedOf(it) <= 0.0001
      })
      if (docAllTurned) return
      // 修复：用 srcIdField 取正确的单据ID（不同单据的id字段名不同，如orderId/quoteId）
      // 之前用 fullDoc.id 是 undefined，导致多张单据的明细行 rowKey 全部为 "undefined-0"，el-table 把它们当作同一行处理
      const docId = fullDoc[srcIdField]
      filteredItems.forEach((item, idx) => {
        const remain = calcRemain(item)
        allRows.push({
          ...item,
          rowKey: `${docId}-${idx}`,
          _pickQty: remain > 0 ? remain : 0,
          _mutexLocked: false, // 默认未锁定，后续通过 API 批量标记
          _sourceDoc: fullDoc,
          orderDate: fullDoc.orderDate || fullDoc.quoteDate || fullDoc.contractDate || '',
          customerName: fullDoc.customerName || fullDoc.supplierName || '',
          [noField.value]: fullDoc[noField.value]
        })
      })
    })

    // === 发票选单场景：调用互斥检查API，标记被锁定的行 ===
    await markMutexLocked(allRows)

    // 源明细的「行备注」与「自定义字段」补值（列与取值口径见 attachItemCustomFields）
    await attachItemCustomFields(currentSourceType.value, allRows)

    allDetailRows.value = allRows
  } catch (e) {
    console.error('加载源单数据失败:', e)
  } finally {
    loading.value = false
  }
}

function confirmPick() {
  const relation = getRelation(currentSourceType.value, props.targetType)
  if (!relation) {
    ElMessage.error(tt('未找到下推关系配置'))
    return
  }

  if (selectedRows.value.length === 0) {
    ElMessage.warning(tt('请至少勾选一行明细'))
    return
  }

  const overWarnRows = []
  for (const r of selectedRows.value) {
    const qty = Number(r._pickQty || 0)
    if (qty <= 0) {
      ElMessage.warning(`${tt("物料 ")}${r.materialName}${tt(" 的本次数量必须大于 0")}`)
      return
    }
    if (qty > calcRemain(r)) {
      if (!props.allowOverQty) {
        ElMessage.warning(`${tt("物料 ")}${r.materialName}${tt(" 的本次数量不能超过剩余数量 ")}${calcRemain(r)}`)
        return
      }
      if (props.overQtyWarn) overWarnRows.push(r.materialName)
    }
  }
  if (overWarnRows.length) {
    ElMessage.warning(`${tt("以下物料超过来源单可转数量：")}${overWarnRows.join('、')}${tt("，已按本次数量转入")}`)
  }

  const firstSourceDoc = selectedRows.value[0]._sourceDoc
  const mappedForm = relation.fieldMapper(firstSourceDoc)
  // 计税方式随单下推（2026-10-09）：销售/采购类单据的「计税方式」必须随下推带过去，
  // 否则下游表头为空会按设置中心默认口径重算 → 含税价来源被重复计税、金额虚增。
  const TAX_FLOW_DOCS = ['SaleQuote', 'SaleContract', 'SaleOrder', 'SaleDelivery', 'SaleReturn', 'SaleInvoice',
    'PurchaseRequest', 'PurchaseContract', 'PurchaseOrder', 'PurchaseInbound', 'PurchaseReturn', 'PurchaseInvoice']
  if (TAX_FLOW_DOCS.includes(relation.sourceType) && TAX_FLOW_DOCS.includes(relation.targetType)
      && firstSourceDoc && firstSourceDoc.taxCalcMethod) {
    mappedForm.taxCalcMethod = firstSourceDoc.taxCalcMethod
  }
  const sourceDocType = getDocType(currentSourceType.value)
  const sourceDocName = sourceDocType?.name || currentSourceType.value
  const noFieldVal = noField.value
  const srcIdFieldVal = sourceDocType?.idField || 'id'
  const pickedItems = selectedRows.value.map(r => {
    // itemMapper 未配置时的降级（pushRelations 有 4 条链路的 itemMapper = null）：
    // 直接把源行本身当作映射结果（仅剔除组件内部用的 _ 前缀临时字段与 rowKey），
    // 保证「勾选的行至少能被带过去」，而不是抛 TypeError 让整个确认动作无声失败。
    const mapped = typeof relation.itemMapper === 'function'
      ? relation.itemMapper(r, r._sourceDoc)
      : Object.keys(r).reduce((acc, k) => {
        if (!k.startsWith('_') && k !== 'rowKey') acc[k] = r[k]
        return acc
      }, {})
    mapped.quantity = r._pickQty
    // 行备注随带入：映射里没带的，用源明细的备注补上（原来不带 → 带入后备注直接丢失）
    if (mapped.remark == null || mapped.remark === '') {
      const srcRemark = remarkOf(r)
      if (srcRemark) mapped.remark = srcRemark
    }
    // 每行注入各自的来源单号（支持多张不同源单混选）
    mapped.sourceBillNo = r._sourceDoc?.[noFieldVal] || r[noFieldVal] || ''
    mapped.sourceBillType = sourceDocName
    mapped.sourceDocId = r._sourceDoc?.[srcIdFieldVal] || null
    // 生产工单专用字段
    mapped.sourceNo = mapped.sourceBillNo
    mapped.sourceType = sourceDocName
    return mapped
  })

  // 替换发货（2026-11-27）：销售订单→销售出库时按替换发货指示展开替代行
  const finalItems = (props.targetType === 'SaleDelivery' && currentSourceType.value === 'SaleOrder')
    ? expandSubstitutePlans(pickedItems)
    : pickedItems
  emit('pick', mappedForm, finalItems, firstSourceDoc)
  visible.value = false
  ElMessage.success(`${tt("已选择 ")}${finalItems.length}${tt(" 行明细")}`)
}

defineExpose({ openDialog, openScan })
</script>

<style scoped lang="scss">
.picker-step { min-height: 200px; }
.step-header {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 12px;
}
.step-title { font-size: 14px; font-weight: 600; color: #303133; }
.source-types {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.type-card {
  cursor: pointer; transition: all 0.2s;
  text-align: center;
  &:hover { border-color: #409eff; transform: translateY(-2px); }
}
.type-icon { font-size: 28px; color: #409eff; margin-bottom: 8px; }
.type-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
.type-desc { font-size: 12px; color: #909399; }
.search-bar {
  display: flex; gap: 8px; margin-bottom: 8px;
}
.action-bar {
  display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
}
.action-info {
  margin-left: auto;
  font-size: 12px; color: #606266;
  b { color: #409eff; font-size: 13px; }
}
.zero-qty { color: #c0c4cc; }

// 扫码带出弹窗（2026-09-27）
.scan-tip {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.scan-tip--muted {
  margin-top: 8px;
  margin-bottom: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
