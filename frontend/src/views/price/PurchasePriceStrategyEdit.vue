<template>
  <div class="price-strategy-edit">
    <!-- 顶部标题栏（单据风格） -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="onBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ form.strategyId ? '采购价格策略' : '新增采购价格策略' }}</span>
            <span v-if="form.strategyCode" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.strategyCode }}
            </span>
            <el-tag size="small" effect="plain" class="status-tag" :type="form.status === '启用' ? 'success' : 'info'">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.strategyId)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions
          :status="'草稿'"
          :close-status="0"
          :is-new="!form.strategyId"
          :supported-actions="supportedActions"
          @save="onSave"
          @submit="onSubmit"
          @approve="onApprove"
          @reject="handleReject"
          @reverse-approve="handleReverseApprove"
          @close="handleClose"
          @reopen="handleReopen"
          @void="handleVoid"
          @unvoid="handleUnvoid"
          @delete="handleDelete"
          @print="handlePrint"
          @copy="handleCopy"
        />
      </div>
    </div>

    <el-form :model="form" :rules="rules" ref="baseFormRef" label-width="90px" class="base-form">
      <!-- 基本信息 -->
      <div class="collapsible-card" :class="{ collapsed: !collapsed.base }">
        <div class="card-header" @click="collapsed.base = !collapsed.base">
          <div class="header-left">
            <el-icon class="collapse-icon" :class="{ active: collapsed.base }"><ArrowRight /></el-icon>
            <span class="card-title">{{ $tt("基本信息") }}</span>
          </div>
        </div>
        <div v-show="collapsed.base" class="card-body">
          <div class="info-section">
            <div class="section-title"><span class="section-bar"></span>{{ $tt("策略信息") }}</div>
            <div class="section-fields">
              <el-form-item :label="$tf('策略编码')">
                <el-input v-model="form.strategyCode" class="field-input" disabled :placeholder="$tt('保存后系统自动生成')" />
              </el-form-item>
              <el-form-item :label="$tf('优先级')" prop="priority" required>
                <el-input-number v-model="form.priority" :min="1" :max="9999" controls-position="right" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('状态')" prop="status" required>
                <el-select v-model="form.status" class="field-input">
                  <el-option :label="$tf('启用')" value="启用" />
                  <el-option :label="$tf('禁用')" value="禁用" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('生效日期')">
                <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" class="field-input" :placeholder="$tt('不填=立即生效')" />
              </el-form-item>
              <el-form-item :label="$tf('失效日期')">
                <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" class="field-input" :placeholder="$tt('不填=长期有效')" />
              </el-form-item>
            </div>
          </div>

          <div class="info-section">
            <div class="section-title"><span class="section-bar"></span>{{ $tt("备注说明") }}</div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :placeholder="$tt('请输入备注说明')" />
              </el-form-item>
            </div>
          </div>
        </div>
      </div>

      <!-- 适用范围：只保留三种互斥选择 -->
      <div class="collapsible-card" :class="{ collapsed: !collapsed.scope }">
        <div class="card-header" @click="collapsed.scope = !collapsed.scope">
          <div class="header-left">
            <el-icon class="collapse-icon" :class="{ active: collapsed.scope }"><ArrowRight /></el-icon>
            <span class="card-title">{{ $tt("适用范围") }}</span>
            <span class="card-hint">{{ $tt("三种只能选一种，选了就锁定") }}</span>
          </div>
        </div>
        <div v-show="collapsed.scope" class="card-body">
          <el-radio-group v-model="form.targetType" class="target-radios" @change="onTargetTypeChange">
            <el-radio-button :label="'material'">{{ $tt("按物料") }}</el-radio-button>
            <el-radio-button :label="'category'">{{ $tt("按商品分类") }}</el-radio-button>
            <el-radio-button :label="'supplier'">{{ $tt("按供应商") }}</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </el-form>

    <!-- 按物料：物料明细通用组件（自带 图片 / 规格型号 / 分类 / 物料自定义字段 / 列设置 / 从物料库选择） -->
    <MaterialItemsTable
      v-if="form.targetType === 'material'"
      v-model="form.items"
      page-key="PurchasePriceStrategyEdit_material"
      title="价格明细"
      hint="每个物料一行，各自设数量区间与价格；点「从物料库选择」可批量加行。"
      :extra-columns="materialExtraColumns"
      :row-defaults="materialRowDefaults"
      @change="onMaterialItemsChange"
    />

    <!-- 按商品分类 / 按供应商：对象不是物料，仍用单据明细组件 -->
    <BillItemsTable
      v-else
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="PurchasePriceStrategyEdit_items"
      title="价格明细"
      subtitle="每个对象一行，各自设数量区间与价格"
      col-setting-title="价格明细列设置"
      :columns="detailColumns"
      :custom-columns="materialCustomColumns"
      :summary-items="[]"
      :show-summary="false"
      :show-total-qty="false"
      :scan-add="false"
    >
      <template #actions>
        <el-button v-if="form.targetType === 'material'" v-permission="perm('pick')" size="small" @click="pickerVisible = true">{{ $tt("从物料库选择") }}</el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem">{{ $tt("添加明细") }}</el-button>
      </template>
      <template #cell-targetId="{ row }">
        <!-- 按物料：与其它单据明细同一套（自定义字段附加展示 + 按自定义字段搜索） -->
        <el-select
          v-if="form.targetType === 'material'"
          v-model="row.materialId"
          filterable
          :placeholder="materialSelectPlaceholder"
          @change="(v) => onMaterialChange(row, v)"
          style="width:100%"
          size="small"
          :filter-method="(val) => materialFilterMethod(val, row)"
          :no-data-text="$tt('无匹配物料')"
          popper-class="material-select-popper">
          <el-option
            v-for="m in row._filteredMaterials || materials"
            :key="m.materialId"
            :label="`${m.materialCode} ${m.materialName}`"
            :value="m.materialId"
          >
            <span style="float:left;">
              <span style="color:#606266;font-weight:500;">{{ m.materialCode }}</span>
              <span style="margin-left:8px;">{{ m.materialName }}</span>
              <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
            </span>
            <span v-if="m.model" style="float:right;color:#909399;font-size:12px;">{{ m.model }}</span>
          </el-option>
        </el-select>
        <!-- 按商品分类 -->
        <el-select
          v-else-if="form.targetType === 'category'"
          v-model="row.targetId"
          filterable
          :placeholder="$tt('选择商品分类')"
          size="small"
          style="width:100%">
          <el-option v-for="c in materialCategories" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId" />
        </el-select>
        <!-- 按供应商 -->
        <el-select
          v-else
          v-model="row.targetId"
          filterable
          :placeholder="$tt('选择供应商')"
          size="small"
          style="width:100%">
          <el-option
            v-for="s in suppliers"
            :key="s.supplierId"
            :label="`${s.supplierCode || ''} ${s.supplierName || ''}`"
            :value="s.supplierId"
          />
        </el-select>
      </template>
      <template #cell-priceValue="{ row }">
        <div class="pv-cell">
          <el-input-number v-model="row.priceValue" :controls="false" :min="0" :precision="4" size="small" style="width:100%" />
          <span v-if="isRate(row)" class="unit-suffix">%</span>
        </div>
      </template>
    </BillItemsTable>

    <!-- 物料库选择弹窗 -->
    <MaterialPickerDialog v-model="pickerVisible" @confirm="onMaterialPicked" />

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialog" :title="$tf('驳回单据')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('驳回原因')">
          <el-input v-model="rejectReason" type="textarea" :rows="3" :placeholder="$tt('请输入驳回原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="rejectDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="warning" @click="doReject">{{ $tt("确认驳回") }}</el-button>
      </template>
    </el-dialog>

    <!-- 作废弹窗 -->
    <el-dialog v-model="voidDialog" :title="$tf('作废单据')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('作废原因')">
          <el-input v-model="voidReason" type="textarea" :rows="3" :placeholder="$tt('请输入作废原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="voidDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="danger" @click="doVoid">{{ $tt("确认作废") }}</el-button>
      </template>
    </el-dialog>

    <ChangeRecordDialog
      v-model="changeRecordVisible"
      bill-type="采购价格策略"
      :bill-no="form.strategyCode"
    />

    <PrintPreviewDialog v-model="printVisible" bill-type="PURCHASE_PRICE_STRATEGY" :bill-id="form.strategyId" />
  </div>
  </template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Document, Collection, Memo } from '@element-plus/icons-vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import MaterialItemsTable from '@/components/MaterialItemsTable.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { tt } from '@/i18n'

import {
  getPurchasePriceStrategy,
  savePurchasePriceStrategy,
  updatePurchasePriceStrategy,
  deletePurchasePriceStrategy,
  rejectPurchasePriceStrategy,
  reverseApprovePurchasePriceStrategy,
  voidPurchasePriceStrategy,
  unvoidPurchasePriceStrategy,
  nextPurchasePriceStrategyPriority
} from '@/api/priceStrategy'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import { listAllSupplier } from '@/api/basedata/supplier'

import BillActions from '@/components/bill/BillActions.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('system:purchase-price')

const route = useRoute()
const router = useRouter()
const baseFormRef = ref()

// 返回列表：若来自设置中心，则回到设置中心价格策略菜单
function onBack() {
  if (route.query.from === 'settings') {
    router.push('/system/settings?group=business&item=purchase-price')
  } else {
    router.push('/purchase/price-strategy')
  }
}

const materials = ref([])
const materialCategories = ref([])
const suppliers = ref([])
// 物料自定义字段：明细「物料」列附加展示 + 按自定义字段搜索 + 可勾选成明细列
const mcf = useMaterialCustomFields()

const changeRecordVisible = ref(false)
const pickerVisible = ref(false)
const collapsed = reactive({ base: true, scope: true, items: true })

const form = reactive({
  strategyId: null,
  strategyCode: '',
  targetType: 'material',
  priority: 1,
  status: '启用',
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  items: []
})

const rules = {
  priority: [{ required: true, message: tt('请输入优先级'), trigger: 'blur' }],
  status: [{ required: true, message: tt('请选择状态'), trigger: 'change' }]
}

/** 明细第一列列名：随适用范围变化 */
const objectColumnLabel = computed(() => {
  if (form.targetType === 'category') return tt('商品分类')
  if (form.targetType === 'supplier') return tt('供应商')
  return tt('物料')
})

/** 价格类型：采购以采购价为基准，固定价/折扣率/加价率三种 */
const priceTypeOptions = computed(() => ['固定价', '折扣率', '加价率'])

const isRate = (row) => ['折扣率', '加价率'].includes(row.priceType)

/** 明细列：对象 + 该对象的数量区间 + 价格类型/价格值 + 备注 */
const detailColumns = computed(() => [
  { prop: 'targetId', label: objectColumnLabel.value, minWidth: 260 },
  { prop: 'minQuantity', label: '数量从', width: 110, editor: 'number', precision: 4 },
  { prop: 'maxQuantity', label: '数量到', width: 110, editor: 'number', precision: 4 },
  { prop: 'priceType', label: '价格类型', width: 130, editor: 'select', optionsFn: () => priceTypeOptions.value.map(v => ({ value: v, label: v })) },
  { prop: 'priceValue', label: '价格值', width: 140 },
  { prop: 'remark', label: '备注', minWidth: 160, editor: 'text' }
])

/** 明细可选显示「物料自定义字段」列（列设置里勾选） */
const materialCustomColumns = computed(() => form.targetType === 'material'
  ? (mcf.allFields.value || []).map(f => ({
      prop: f.fieldKey,
      label: f.fieldLabel,
      fieldType: f.fieldType,
      options: f.options,
      isListShow: f.isListShow,
      precision: f.precision
    }))
  : [])

// ===== 按物料模式：物料明细通用组件的业务附加列 / 新增行默认值 =====
const materialExtraColumns = computed(() => [
  { prop: 'minQuantity', label: '数量从', width: 110, editor: 'number', precision: 4 },
  { prop: 'maxQuantity', label: '数量到', width: 110, editor: 'number', precision: 4 },
  { prop: 'priceType', label: '价格类型', width: 130, editor: 'select', options: priceTypeOptions.value.map(v => ({ value: v, label: v })) },
  { prop: 'priceValue', label: '价格值', width: 140, editor: 'number', precision: 4 },
  { prop: 'remark', label: '备注', minWidth: 160 }
])
const materialRowDefaults = { priceType: '固定价', priceValue: 0, costPrice: 0, minQuantity: null, maxQuantity: null, remark: '' }
/** 通用组件加行/改行后：补齐 targetId、成本价，并按物料默认采购价带出价格值 */
function onMaterialItemsChange(rows) {
  (rows || []).forEach(r => {
    if (r.materialId != null) r.targetId = r.materialId
    if (r.costPrice == null || r.costPrice === 0) r.costPrice = r.purchasePrice ?? r.avgCost ?? 0
    if (r.priceValue == null || r.priceValue === 0) r.priceValue = r.purchasePrice || 0
  })
}

/** 参与「物料」下拉搜索的自定义字段 */
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))
))
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

function materialFilterMethod(val, row) {
  if (!val) {
    row._filteredMaterials = null
    return
  }
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

/** 选物料：带出采购价，并把该物料的「自定义字段值」并入行（明细列可直接显示） */
async function onMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.targetId = id
    if (row.priceType === '固定价' && !row.priceValue) {
      row.priceValue = m.purchasePrice || 0
    }
    await mcf.ensureValues([id])
    Object.assign(row, mcf.valueMap.value[id] || {})
  }
}

/** 切换适用范围：清空明细（对象类型变了，旧的明细行不再适用） */
function onTargetTypeChange() {
  form.items = []
}

function addItem() {
  form.items.push({
    targetId: null,
    materialId: null,
    priceType: '固定价',
    priceValue: 0,
    minQuantity: null,
    maxQuantity: null,
    remark: ''
  })
}

async function onMaterialPicked({ rows: picked }) {
  if (!picked || picked.length === 0) return
  picked.forEach(m => {
    form.items.push({
      targetId: m.materialId,
      materialId: m.materialId,
      priceType: '固定价',
      priceValue: m.purchasePrice || 0,
      minQuantity: null,
      maxQuantity: null,
      remark: ''
    })
  })
  pickerVisible.value = false
  await mcf.preloadAll(materials.value)
  await mcf.mergeIntoRows(form.items)
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
}

const saving = ref(false)
async function onSave(silent, opts = {}) {
  try {
    await baseFormRef.value?.validate()
  } catch (e) {
    ElMessage.warning(tt('请填写必填项后再保存'))
    return false
  }
  if (!form.items || form.items.length === 0) {
    ElMessage.warning(tt('价格明细不能为空，请点「添加明细」或「从物料库选择」'))
    return false
  }
  const missing = form.items.findIndex(it => {
    const id = form.targetType === 'material' ? it.materialId : it.targetId
    return id === null || id === undefined || id === ''
  })
  if (missing >= 0) {
    ElMessage.warning(`${tt('价格明细第')}${missing + 1}${tt('行还没选适用对象')}`)
    return false
  }
  saving.value = true
  try {
    const payload = {
      id: form.strategyId || null,
      strategyCode: form.strategyCode || null,
      targetType: form.targetType,
      priority: form.priority,
      status: form.status === '启用' ? 1 : 0,
      startDate: form.effectiveDate || null,
      endDate: form.expiryDate || null,
      remark: form.remark,
      items: form.items.map(it => {
        const id = form.targetType === 'material' ? it.materialId : it.targetId
        return {
          targetType: form.targetType,
          targetId: id,
          materialId: form.targetType === 'material' ? id : null,
          minQuantity: it.minQuantity == null || it.minQuantity === '' ? null : Number(it.minQuantity),
          maxQuantity: it.maxQuantity == null || it.maxQuantity === '' ? null : Number(it.maxQuantity),
          priceType: it.priceType,
          priceValue: Number(it.priceValue || 0),
          remark: it.remark
        }
      })
    }

    if (form.strategyId) {
      await updatePurchasePriceStrategy(payload)
    } else {
      const res = await savePurchasePriceStrategy(payload)
      // 新增：后端返回落库实体（含系统自动生成的策略编码），回写页面，避免再次保存时重复建档
      if (res && res.id) {
        form.strategyId = res.id
        form.strategyCode = res.strategyCode || form.strategyCode
      }
      if (!opts.skipRouteSync && !route.params.id && form.strategyId) {
        try { await router.replace({ name: 'PurchasePriceStrategyEdit', params: { id: String(form.strategyId) } }) } catch (_) {}
      }
    }
    triggerListRefresh('/purchase/price-strategy')
    if (!silent) ElMessage.success(tt('保存成功'))
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  } finally {
    saving.value = false
  }
}

async function onSubmit() {
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.strategyId) return
  try {
    ElMessage.success(tt('已保存，价格策略为基础资料类单据，无需审批流程'))
    await loadDetail()
  } finally {
    if (!route.params.id && form.strategyId) {
      try { await router.replace({ name: 'PurchasePriceStrategyEdit', params: { id: String(form.strategyId) } }) } catch (_) {}
    }
  }
}

async function loadDetail() {
  const __detailId = route.params.id || form.strategyId || ''
  if (__detailId) {
    try {
      const res = await getPurchasePriceStrategy(__detailId)
      form.strategyId = res.id
      form.strategyCode = res.strategyCode || ''
      form.targetType = res.targetType || 'material'
      form.priority = res.priority ?? 1
      form.status = res.status === 1 ? '启用' : '禁用'
      form.effectiveDate = res.startDate || ''
      form.expiryDate = res.endDate || ''
      form.remark = res.remark || ''
      form.items = (res.items || []).map(item => {
        const id = item.targetId != null ? item.targetId : item.materialId
        return {
          targetId: id,
          materialId: item.materialId != null ? item.materialId : (res.targetType === 'material' ? id : null),
          priceType: item.priceType || '固定价',
          priceValue: item.priceValue ?? 0,
          minQuantity: item.minQuantity,
          maxQuantity: item.maxQuantity,
          remark: item.remark || ''
        }
      })
      if (!form.items) form.items = []
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  }
}

const printVisible = ref(false)
function handlePrint() {
  if (!form.strategyId) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

// ===== 统一单据操作（审核/驳回/作废/删除等）=====
const {
  supportedActions,
  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.strategyId,
  api: {
    reject: rejectPurchasePriceStrategy,
    reverseApprove: reverseApprovePurchasePriceStrategy,
    void: voidPurchasePriceStrategy,
    unvoid: unvoidPurchasePriceStrategy,
    delete: deletePurchasePriceStrategy
  },
  reload: loadDetail,
  router,
  listPath: '/purchase/price-strategy',
  newPath: '/purchase/price-strategy/new',
  billLabel: '采购价格策略'
})

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
async function loadBaseData() {
  try {
    const [matRes, catRes, supRes] = await Promise.all([
      listAllMaterial(),
      listAllMaterialCategory(),
      listAllSupplier()
    ])
    materials.value = matRes || []
    materialCategories.value = catRes || []
    suppliers.value = supRes || []
    mcf.preloadAll(materials.value)
  } catch (e) {
    console.warn('下拉数据加载失败:', e)
  }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getPurchasePriceStrategy,
  form,
  noField: 'strategyCode'
})

/** 新增 / 复制时取「下一个可用优先级」，避免与现有策略重复 */
async function applyNextPriority() {
  try {
    const p = await nextPurchasePriceStrategyPriority()
    if (p) form.priority = p
  } catch (_) { /* 取不到就保留默认值，后端保存时仍会校验重复 */ }
}

onMounted(async () => {
  await mcf.init()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
  if (!form.strategyId) {
    await applyNextPriority()
  }
  // 已有明细行：把物料自定义字段值并入行（明细列可直接显示）
  if (form.targetType === 'material' && form.items.length) {
    await mcf.mergeIntoRows(form.items)
  }
})

useBaseDataRefresh(loadBaseData)
</script>

<style scoped lang="scss">
.price-strategy-edit {
  padding: 12px 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
  font-size: 12px;
}

// ===== 顶部标题栏（与全站单据编辑页同一套）=====
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;

  .header-left { display: flex; align-items: center; gap: 12px; }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #606266;
    font-size: 12px;
    cursor: pointer;
    padding: 4px 10px;
    border-radius: 4px;
    background: #f5f7fa;
    border: 1px solid #dcdfe6;
    transition: all .2s;
    &:hover { color: #409eff; border-color: #b3d8ff; background: #ecf5ff; }
  }

  .title-area .title-row { display: flex; align-items: center; gap: 6px; }
  .title-icon { color: #409eff; font-size: 16px; }
  .page-title { font-size: 15px; font-weight: 600; color: #303133; }

  .order-no {
    display: flex;
    align-items: center;
    gap: 3px;
    font-size: 11px;
    color: #409eff;
    background: #ecf5ff;
    padding: 2px 8px;
    border-radius: 3px;
    font-family: 'SF Mono', Monaco, monospace;
    font-weight: 500;
  }

  .status-tag { transform: scale(0.88); transform-origin: left center; }
  .header-right { display: flex; gap: 6px; align-items: center; }
}

// ===== 可折叠卡片（与全站单据编辑页同一套）=====
.collapsible-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;

  &.collapsed .card-header { border-bottom: none; }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 14px;
    background: #fafbfc;
    border-bottom: 1px solid #e4e7ed;
    cursor: pointer;
    user-select: none;

    .header-left { display: flex; align-items: center; gap: 8px; }
    .collapse-icon {
      color: #909399;
      font-size: 12px;
      transition: transform .25s ease;
      &.active { transform: rotate(90deg); color: #409eff; }
    }
    .card-title { font-size: 13px; font-weight: 600; color: #303133; }
    .card-hint { font-size: 11px; color: #909399; font-weight: 400; }
  }

  .card-body { padding: 12px 14px; }
}

// ===== 基本信息分组 =====
.base-form {
  // 单据编辑页标准：分区内字段多列自动排（缺了这条就会退化成一字段一行）
  .section-fields {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0 12px;
  }

  .info-section {
    margin-bottom: 10px;
    padding-bottom: 10px;
    border-bottom: 1px dashed #ebeef5;

    &:last-child { margin-bottom: 0; padding-bottom: 0; border-bottom: none; }
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #409eff;
    margin-bottom: 8px;

    .section-bar {
      width: 3px;
      height: 12px;
      background: linear-gradient(180deg, #409eff, #66b1ff);
      border-radius: 2px;
    }
  }

  :deep(.el-form-item) { margin-bottom: 6px; }
  :deep(.el-form-item__label) { font-size: 12px; color: #606266; line-height: 28px; }
  :deep(.el-form-item__content) { line-height: 28px; }
}

.field-input {
  width: 100%;
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    box-shadow: 0 0 0 1px #dcdfe6 inset;
    border-radius: 4px;
    min-height: 28px;
    font-size: 12px;
    padding: 0 8px;
    .el-input__inner { font-size: 12px; line-height: 28px; }
    &:hover { box-shadow: 0 0 0 1px #c6e2ff inset; }
  }
  :deep(.el-input__wrapper.is-focus),
  :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #409eff inset; }
  :deep(.el-input-number) {
    width: 100%;
    .el-input__wrapper { min-height: 28px; padding: 0 6px; }
  }
  :deep(.el-date-editor) { width: 100%; }
}

.target-radios { margin-bottom: 0; }

.pv-cell { display: flex; align-items: center; gap: 4px; width: 100%; }
.unit-suffix { color: #909399; }
</style>

<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中 -->
<style>
.material-select-popper { min-width: 640px; max-width: 90vw; }
.material-select-popper .opt-cf { margin-left: 8px; color: #909399; font-size: 12px; white-space: normal; word-break: break-word; }
.material-select-popper .el-select-dropdown__item {
  height: auto;
  min-height: 34px;
  line-height: 1.6;
  padding-top: 6px;
  padding-bottom: 6px;
  white-space: normal;
  overflow: visible;
}
</style>
