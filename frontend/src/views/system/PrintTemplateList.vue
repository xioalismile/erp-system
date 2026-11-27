﻿<template>
  <div class="erp-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">{{ $tt("打印模板管理") }}</span>
          <el-button v-permission="perm('add')" type="primary" :icon="Plus" @click="handleAdd">{{ $tt("新增模板") }}</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="erp-search-bar">
        <div class="erp-search-item">
          <span class="label">{{ $tt("关键词:") }}</span>
          <el-input v-model="searchForm.keyword" :placeholder="$tt('模板编码/名称')" clearable style="width: 200px" @keyup.enter="loadData" />
        </div>
        <div class="erp-search-item">
          <span class="label">{{ $tt("单据类型:") }}</span>
          <el-select v-model="searchForm.docType" :placeholder="$tt('全部')" clearable style="width: 160px">
            <el-option v-for="t in docTypeOptions" :key="t.value" :label="$tf(t.label)" :value="t.value" />
          </el-select>
        </div>
        <el-button v-permission="perm('query')" type="primary" :icon="Search" @click="loadData">{{ $tt("查询") }}</el-button>
        <el-button v-permission="perm('reset')" :icon="RefreshRight" @click="handleReset">{{ $tt("重置") }}</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%" @sort-change="onSortChange">
        <el-table-column prop="templateCode" :label="$tf('模板编码')" width="180" sortable="custom"/>
        <el-table-column prop="templateName" :label="$tf('模板名称')" min-width="180" sortable="custom"/>
        <el-table-column prop="docType" :label="$tf('单据类型')" width="150" sortable="custom">
          <template #default="{ row }">{{ getDocTypeLabel(row.docType) }}</template>
        </el-table-column>
        <el-table-column prop="pageSize" :label="$tf('纸张')" width="80" sortable="custom"/>
        <el-table-column prop="pageOrientation" :label="$tf('方向')" width="80" sortable="custom">
          <template #default="{ row }">{{ row.pageOrientation === 'landscape' ? '横向' : '纵向' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('默认')" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">{{ $tt("默认") }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ $dt(row.status === 1 ? '启用' : '停用') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" :label="$tf('排序')" width="70" sortable="custom"/>
        <el-table-column :label="$tf('操作')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="perm('edit')" size="small" type="primary" link :icon="EditPen" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button v-permission="perm('copy')" size="small" type="primary" link :icon="CopyDocument" @click="handleCopy(row)">{{ $tt("复制") }}</el-button>
            <el-button v-permission="perm('print')" size="small" type="primary" link :icon="View" @click="handlePreview(row)">{{ $tt("预览") }}</el-button>
            <el-button v-permission="perm('delete')" size="small" type="danger" link :icon="Delete" @click="handleDelete(row)">{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="erp-pagination">
        <el-pagination v-model:current-page="page.current" v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]" :total="page.total" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 编辑弹窗：自由画布设计器 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模板' : '新增模板'" width="98%" top="1vh"
      :close-on-click-modal="false" :close-on-press-escape="false" destroy-on-close class="tpl-edit-dialog">
      <!-- 基础信息条：两行布局，统一标签宽度与间距 -->
      <div class="pt-meta-bar">
        <div class="pt-meta-row">
          <div class="pt-meta-item">
            <span class="pt-meta-cap">模板名称</span>
            <el-input v-model="formData.templateName" class="pt-w200" />
          </div>
          <div class="pt-meta-item">
            <span class="pt-meta-cap">单据类型</span>
            <el-select v-model="formData.docType" class="pt-w150" placeholder="选择单据类型" @change="onDocTypeChange">
              <el-option v-for="t in docTypeOptions" :key="t.value" :label="$tf(t.label)" :value="t.value" />
            </el-select>
          </div>
          <div class="pt-meta-item">
            <span class="pt-meta-cap">模板编码</span>
            <el-input v-model="formData.templateCode" class="pt-w190" @input="templateCodeAuto = false" />
          </div>
          <div class="pt-meta-item">
            <span class="pt-meta-cap">纸张</span>
            <el-select v-model="formData.pageSize" class="pt-w80"><el-option label="A4" value="A4" /><el-option label="A5" value="A5" /></el-select>
            <el-select v-model="formData.pageOrientation" class="pt-w90"><el-option label="纵向" value="portrait" /><el-option label="横向" value="landscape" /></el-select>
          </div>
          <div class="pt-meta-item">
            <span class="pt-meta-cap">设为默认</span>
            <el-switch v-model="formData.isDefault" :active-value="1" :inactive-value="0" />
          </div>
          <div class="pt-meta-item">
            <span class="pt-meta-cap">状态</span>
            <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
          </div>
        </div>
        <div class="pt-meta-row pt-meta-row-sub">
          <div class="pt-meta-item">
            <span class="pt-meta-cap">页边距</span>
            <span class="pt-margin-cell"><em>上</em><el-input-number v-model="formData.marginTop" :min="0" :max="50" size="small" controls-position="right" class="pt-w64" /></span>
            <span class="pt-margin-cell"><em>下</em><el-input-number v-model="formData.marginBottom" :min="0" :max="50" size="small" controls-position="right" class="pt-w64" /></span>
            <span class="pt-margin-cell"><em>左</em><el-input-number v-model="formData.marginLeft" :min="0" :max="50" size="small" controls-position="right" class="pt-w64" /></span>
            <span class="pt-margin-cell"><em>右</em><el-input-number v-model="formData.marginRight" :min="0" :max="50" size="small" controls-position="right" class="pt-w64" /></span>
            <span class="pt-meta-unit">毫米</span>
          </div>
        </div>
      </div>
      <!-- 设计器 -->
      <PrintTemplateDesigner
        ref="designerRef"
        v-model:modelContent="formData.templateContent"
        v-model:modelStyle="formData.templateStyle"
        :doc-type="formData.docType"
        :page-size="formData.pageSize"
        :page-orientation="formData.pageOrientation"
        :margin-top="formData.marginTop"
        :margin-bottom="formData.marginBottom"
        :margin-left="formData.marginLeft"
        :margin-right="formData.marginRight"
        :field-map="fieldMap"
        :is-edit="isEdit"
        :doc-label="dialogDocLabel"
        @save="handleSave"
        @reload-fields="loadFieldMap" />
      <template #footer>
        <div class="dialog-footer">
          <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewDialogVisible" :title="$tf('打印预览')" width="900px" top="5vh" destroy-on-close>
      <iframe ref="previewIframeRef" class="preview-iframe"></iframe>
    </el-dialog>

    <!-- 单据预览弹窗 -->
    <PrintPreviewDialog v-model="previewVisible" :bill-type="previewBillType" :bill-id="previewBillId" />
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:print-setting')
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshRight, View, CopyDocument, Delete, EditPen } from '@element-plus/icons-vue'
import { printTemplateApi } from '@/api/system/print'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import PrintTemplateDesigner from '@/components/PrintTemplateDesigner.vue'
import { useTableSort } from '@/composables/useTableSort'
import { isBillHidden } from '@/config/hiddenBills'
import { tt } from '@/i18n'
const { handleSortChange, getSortParams } = useTableSort()


const onSortChange = (e) => { handleSortChange(e); loadData() }
const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const page = reactive({ current: 1, size: 20, total: 0, ...getSortParams() })
const searchForm = reactive({ keyword: '', docType: '' })

// 预览
const previewVisible = ref(false)
const previewBillType = ref('')
const previewBillId = ref(null)
const previewDialogVisible = ref(false)
const previewIframeRef = ref()


// 单据类型下拉：唯一来源在后端（field-map 的 docTypes，2026-11-22 起）。
// 下面这份仅作接口失败时的兜底，正常情况下不会用到；新增单据由后端自动加入，无需再改这里。
const BACKUP_DOC_TYPE_OPTIONS = [
  // 销售
  { label: '销售报价单', value: 'SALE_QUOTE' },
  { label: '销售订单', value: 'SALE_ORDER' },
  { label: '销售合同', value: 'SALE_CONTRACT' },
  { label: '销售出库单', value: 'SALE_DELIVERY' },
  { label: '销售退货单', value: 'SALE_RETURN' },
  { label: '销售换货单', value: 'SALE_EXCHANGE' },
  { label: '售后维修单', value: 'SALE_REPAIR' },
  { label: '销售发票', value: 'SALE_INVOICE' },
  { label: '销售费用单', value: 'SALE_EXPENSE' },
  // 采购
  { label: '采购申请单', value: 'PURCHASE_REQUEST' },
  { label: '采购订单', value: 'PURCHASE_ORDER' },
  { label: '采购合同', value: 'PURCHASE_CONTRACT' },
  { label: '采购入库单', value: 'PURCHASE_INBOUND' },
  { label: '采购退货单', value: 'PURCHASE_RETURN' },
  { label: '采购发票', value: 'PURCHASE_INVOICE' },
  { label: '采购费用单', value: 'PURCHASE_EXPENSE' },
  // 生产
  { label: '生产订单', value: 'WORK_ORDER' },
  { label: '生产入库单', value: 'PRODUCTION_RECEIPT' },
  { label: '生产领料单', value: 'MATERIAL_REQUISITION' },
  { label: '组装单', value: 'PRODUCE_ASSEMBLE' },
  { label: '拆卸单', value: 'PRODUCE_DISASSEMBLE' },
  { label: '报工单', value: 'OPERATION_REPORT' },

  { label: 'MRP计划', value: 'MRP_PLAN' },
  // 委外
  { label: '委外加工单', value: 'SUBCONTRACT_ORDER' },
  { label: '委外发料单', value: 'SUBCONTRACT_PICK' },
  { label: '委外入库单', value: 'SUBCONTRACT_INBOUND' },
  { label: '委外退料单', value: 'SUBCONTRACT_RETURN' },
  { label: '委外补料单', value: 'SUBCONTRACT_SUPPLEMENT' },
  // 样品
  { label: '打样单', value: 'SAMPLE' },
  { label: '免费样品单', value: 'FREE_SAMPLE' },
  // 库存
  { label: '库存调拨单', value: 'STOCK_TRANSFER' },
  { label: '库存盘点单', value: 'STOCK_CHECK' },
  { label: '其他入库单', value: 'OTHER_RECEIPT' },
  { label: '其他出库单', value: 'OTHER_ISSUE' },
  // 财务
  { label: '收款单', value: 'PAYMENT_RECEIPT' },
  { label: '付款单', value: 'PAYMENT_VOUCHER' },
  { label: '销售预收单', value: 'SALE_PRE_RECEIPT' },
  { label: '采购预付单', value: 'PURCHASE_PRE_PAYMENT' },
  { label: '费用分摊', value: 'OPERATION_EXPENSE' },
  { label: '成本核算单', value: 'COST_CALCULATION' },
  // 价格策略
  { label: '销售价格策略', value: 'SALE_PRICE_STRATEGY' },
  { label: '采购价格策略', value: 'PURCHASE_PRICE_STRATEGY' },
  // 基础
  { label: 'BOM物料清单', value: 'BOM' },
  { label: '商品标签（二维码）', value: 'MATERIAL' },
  { label: '工序卡（二维码）', value: 'WORK_OPERATION' },
  { label: '通用模板', value: 'GENERIC' }
]

// 客户包隐藏单据（VITE_HIDDEN_BILLS）：隐藏的单据类型在下拉里不出现（121 不上换货/维修）
const docTypeOptions = ref([...BACKUP_DOC_TYPE_OPTIONS].filter(o => !isBillHidden(o.value)))

function getDocTypeLabel(val) {
  const t = docTypeOptions.value.find(o => o.value === val)
  return t ? t.label : val
}

// ============ 字段映射表 ============
// ============ 可用字段（唯一来源在后端：GET /print-template/field-map） ============
// 2026-10-10：字段词汇原写在本页（前端），导致 ① AI 建模板时不知道该用哪些字段（建出来取不到值）；
// ② 前后端各存一份必然漂移。现统一由后端提供，本页启动时读取一次。
const docTypeFieldsMap = ref({})
const commonItemFields = ref([])
const employeeFields = ref([])
const customerFields = ref([])
const designerRef = ref(null)

const loadFieldMap = async () => {
  try {
    const data = await printTemplateApi.getFieldMap()
    docTypeFieldsMap.value = (data && data.docTypeFieldsMap) || {}
    commonItemFields.value = (data && data.commonItemFields) || []
    employeeFields.value = (data && data.employeeFields) || []
    customerFields.value = (data && data.customerFields) || []
    // 单据类型以服务端清单为准（含台账自动加入的新单据）；返回空时保留本页兜底清单
    if (data && Array.isArray(data.docTypes) && data.docTypes.length) {
      docTypeOptions.value = data.docTypes.filter(o => !isBillHidden(o.value))
    }
  } catch (e) {
    console.error('加载打印模板可用字段失败', e)
  }
}

// 给设计器用的完整字段映射
const fieldMap = computed(() => ({
  commonItemFields: commonItemFields.value,
  employeeFields: employeeFields.value,
  customerFields: customerFields.value,
  docTypeFieldsMap: docTypeFieldsMap.value
}))

const currentFields = computed(() => {
  if (!formData.docType) return { header: [], items: [], footer: [] }
  return (docTypeFieldsMap.value || {})[formData.docType] || { header: [], items: [], footer: [] }
})

// 一键生成时的单据标题（取单据类型中文名，避免各类单据都叫"打印单据"）
const dialogDocLabel = computed(() => {
  const o = docTypeOptions.value.find(x => x.value === formData.docType)
  return o ? o.label : ''
})

const formData = reactive({})

// ============ 编辑器生命周期 ============
function handleEditorCreated(editor) {
  editorRef.value = editor
}

function handleEditorChange() {
  // 编辑器内容变化时可联动处理
}

// ============ 数据加载 ============
async function loadData() {
  loading.value = true
  try {
    const res = await printTemplateApi.list({
      current: page.current, size: page.size,
      keyword: searchForm.keyword || undefined, docType: searchForm.docType || undefined,
      ...getSortParams()
    })
    tableData.value = res?.records || []
    page.total = res?.total || 0
  } catch {} finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.docType = ''
  page.current = 1
  loadData()
}

// ============ 新增/编辑 ============
function handleAdd() {
  isEdit.value = false
  resetForm()
  formData.pageSize = 'A4'
  formData.pageOrientation = 'portrait'
  formData.marginTop = 15
  formData.marginBottom = 15
  formData.marginLeft = 15
  formData.marginRight = 15
  formData.status = 1
  formData.isDefault = 0
  formData.sortOrder = 0
  formData.templateStyle = ''
  formData.templateContent = ''
  formData.remark = ''
  formData.templateCode = genTemplateCode('')
  templateCodeAuto = true
  dialogVisible.value = true
}

// 模板编码默认自动生成（编码类项目默认不手输）；用户手动改过就不再覆盖
let templateCodeAuto = true
function genTemplateCode(docType) {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
  const prefix = (docType || 'print').toLowerCase()
  return `${prefix}_${ts}`
}

function resetForm() {
  Object.keys(formData).forEach(k => delete formData[k])
  templateCodeAuto = false
}

function onDocTypeChange() {
  // 单据类型变更由设计器内部 watch 处理（重新加载字段）；编码仍是自动值时跟随单据类型重新生成
  if (templateCodeAuto) formData.templateCode = genTemplateCode(formData.docType)
}

// ============ 预览模板（与真实打印同一套后端渲染，样例数据） ============
async function handlePreview(row) {
  try {
    const detail = await printTemplateApi.getDetail(row.id)
    if (!detail) return
    previewDialogVisible.value = true
    await nextTick()
    const iframe = previewIframeRef.value
    if (!iframe) return
    const html = await printTemplateApi.previewHtml({
      templateName: detail.templateName,
      docType: detail.docType,
      templateContent: detail.templateContent,
      templateStyle: detail.templateStyle,
      pageSize: detail.pageSize,
      pageOrientation: detail.pageOrientation,
      marginTop: detail.marginTop,
      marginBottom: detail.marginBottom,
      marginLeft: detail.marginLeft,
      marginRight: detail.marginRight
    })
    const doc = iframe.contentDocument || iframe.contentWindow.document
    doc.open()
    doc.write(html || '')
    doc.close()
  } catch (e) {
    ElMessage.error(tt('预览生成失败，请重试'))
  }
}

// ============ 保存 ============
async function handleSave() {
  if (!formData.templateCode) { ElMessage.warning(tt('请输入模板编码')); return }
  if (!formData.templateName) { ElMessage.warning(tt('请输入模板名称')); return }
  if (!formData.docType) { ElMessage.warning(tt('请选择单据类型')); return }
  if (!formData.templateContent || !formData.templateContent.trim()) {
    ElMessage.warning(tt('请在画布中添加内容'))
    return
  }

  formData.headerContent = ''
  formData.footerContent = ''

  saving.value = true
  try {
    if (isEdit.value) {
      await printTemplateApi.update(formData)
    } else {
      await printTemplateApi.save(formData)
    }
    ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    loadData()
  } catch {} finally {
    saving.value = false
  }
}

// ============ 编辑 / 复制 / 删除 ============
async function handleEdit(row) {
  isEdit.value = true
  resetForm()
  Object.assign(formData, row)
  // 拉取详情（含完整内容）
  try {
    const detail = await printTemplateApi.getDetail(row.id)
    if (detail) Object.assign(formData, detail)
  } catch {}
  dialogVisible.value = true
}

async function handleCopy(row) {
  try {
    const newName = row.templateName + '-副本'
    await printTemplateApi.copy(row.id, newName)
    ElMessage.success(tt('复制成功'))
    loadData()
  } catch {}
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.templateName}」吗？`, tt('提示'), { type: 'warning' })
    await printTemplateApi.delete(row.id)
    ElMessage.success(tt('删除成功'))
    loadData()
  } catch {}
}

onMounted(() => { loadData(); loadFieldMap() })
useAutoRefresh(loadData)
</script>

<style scoped lang="scss">
.erp-page { padding: 16px; }
.card-header {
  display: flex; align-items: center; justify-content: space-between;
  .title { font-size: 16px; font-weight: 600; }
}
.erp-search-bar {
  display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap;
  .erp-search-item { display: flex; align-items: center;
    .label { font-size: 13px; color: #606266; margin-right: 6px; }
  }
}
.erp-pagination { margin-top: 16px; display: flex; justify-content: flex-end; }

/* 编辑弹窗整体布局 */
.tpl-edit-dialog :deep(.el-dialog__body) {
  padding: 0 20px 12px 20px;
  height: calc(97vh - 140px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
/* 设计器占满弹窗剩余高度：纸面完整可见，不再只露半截 */
.tpl-edit-dialog .pt-meta-bar {
  flex: 0 0 auto;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.pt-meta-row { display: flex; align-items: center; flex-wrap: wrap; gap: 18px; }
.pt-meta-row-sub { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #ebeef5; }
.pt-meta-item { display: inline-flex; align-items: center; gap: 8px; }
.pt-meta-cap { font-size: 12px; color: #606266; white-space: nowrap; }
.pt-meta-unit { font-size: 11px; color: #a8abb2; }
.pt-margin-cell { display: inline-flex; align-items: center; gap: 4px; }
.pt-margin-cell em { font-style: normal; font-size: 11px; color: #909399; }
.pt-w64 { width: 66px; }
.pt-w80 { width: 80px; }
.pt-w90 { width: 90px; }
.pt-w150 { width: 150px; }
.pt-w190 { width: 190px; }
.pt-w200 { width: 200px; }
.tpl-edit-dialog :deep(.pt-designer) { flex: 1 1 auto; min-height: 0; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; }

.tpl-edit-wrap {
  display: flex; gap: 16px; height: 78vh; min-height: 600px;
}

/* 左侧字段面板 */
.tpl-field-sidebar {
  width: 300px; flex-shrink: 0; background: #fafbfc; border: 1px solid #ebeef5;
  border-radius: 6px; padding: 12px; overflow-y: auto;
  .sidebar-title {
    display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 15px;
    color: #303133; margin-bottom: 6px; padding-bottom: 8px;
    border-bottom: 2px solid #409eff;
  }
  .sidebar-tip {
    font-size: 12px; color: #909399; margin-bottom: 12px; line-height: 1.5;
    background: #ecf5ff; padding: 6px 10px; border-radius: 4px;
  }
}

.field-section {
  margin-bottom: 16px;
  .section-title {
    display: flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 600;
    color: #303133; margin-bottom: 8px; padding: 6px 10px; background: #ecf5ff;
    border-radius: 4px; border-left: 3px solid #409eff;
  }
  .field-grid {
    display: flex; flex-direction: column; gap: 4px;
  }
  .field-card {
    display: flex; align-items: center; gap: 6px; padding: 5px 8px;
    background: #fff; border: 1px solid #e4e7ed; border-radius: 4px;
    transition: all 0.2s;
    &:hover {
      border-color: #409eff; background: #ecf5ff; cursor: pointer;
    }
    .field-num {
      width: 20px; height: 20px; line-height: 20px; text-align: center;
      background: #f0f2f5; color: #909399; border-radius: 50%; font-size: 11px;
      flex-shrink: 0;
    }
    .field-name {
      flex: 1; font-size: 13px; color: #303133; overflow: hidden;
      text-overflow: ellipsis; white-space: nowrap;
    }
  }
  .tool-list {
    display: flex; flex-direction: column; gap: 8px;
  }
}

/* 右侧编辑区 */
.tpl-editor-area {
  flex: 1; display: flex; flex-direction: column; min-width: 0; gap: 10px;
}

.tpl-info-bar {
  background: #fafbfc; padding: 8px 12px; border: 1px solid #ebeef5;
  border-radius: 6px;
  :deep(.el-form-item) { margin-bottom: 4px; }
}

.editor-container {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden;
  .editor-label {
    display: flex; align-items: center; gap: 6px; padding: 8px 12px;
    background: #f5f7fa; border-bottom: 1px solid #ebeef5; font-size: 13px;
    font-weight: 600; color: #303133;
    .editor-tip { font-weight: normal; color: #909399; font-size: 12px; }
  }
}

.w-e-wrapper {
  :deep(.w-e-text-container) { background-color: #fff; }
  :deep(.w-e-toolbar) { background-color: #f5f7fa; border-bottom: 1px solid #e4e7ed; }
  :deep(.w-e-bar-item) { padding: 0 6px; }
}

/* 明细列配置 */
.detail-config {
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px;
  .detail-config-header {
    display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;
    .config-title {
      display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600;
      color: #303133;
      .config-tip { font-weight: normal; color: #909399; font-size: 12px; margin-left: 6px; }
    }
    .config-actions { display: flex; gap: 6px; }
  }
  .detail-col-list {
    display: flex; flex-wrap: wrap; gap: 8px;
    .col-item {
      background: #fff; padding: 4px 10px; border: 1px solid #e4e7ed;
      border-radius: 4px; font-size: 12px;
      .col-name { color: #606266; }
    }
  }
}

.preview-iframe {
  width: 100%; height: 70vh; border: 1px solid #e4e7ed; border-radius: 4px;
  background: #fff;
}

/* ============ 编辑模式切换 tabs ============ */
.editor-mode-tabs {
  display: flex; gap: 4px; padding: 4px; background: #f0f2f5;
  border-radius: 6px 6px 0 0; border: 1px solid #ebeef5; border-bottom: none;
  .mode-tab {
    display: flex; align-items: center; gap: 4px; padding: 6px 14px;
    font-size: 13px; color: #606266; cursor: pointer; border-radius: 4px;
    transition: all 0.2s; user-select: none;
    &:hover { background: #fff; color: #303133; }
    &.active {
      background: #fff; color: #409eff; font-weight: 600;
      box-shadow: 0 1px 3px rgba(64,158,255,0.2);
    }
  }
}

/* ============ 可视化表格设计器 ============ */
.table-designer {
  background: #fff; border: 1px solid #ebeef5; border-radius: 0 0 6px 6px;
  padding: 10px; display: flex; flex-direction: column; gap: 8px;
}
.designer-toolbar {
  display: flex; align-items: center; flex-wrap: wrap; gap: 8px;
  padding: 6px 8px; background: #fafbfc; border: 1px solid #ebeef5; border-radius: 4px;
  .designer-tip {
    margin-left: auto; font-size: 12px; color: #909399;
    background: #ecf5ff; padding: 4px 8px; border-radius: 4px;
    max-width: 380px;
  }
}
.designer-table-wrap {
  position: relative; overflow: auto; max-height: 480px;
  background: repeating-conic-gradient(#f5f7fa 0% 25%, #fff 0% 50%) 50% / 16px 16px;
  border: 1px solid #dcdfe6; border-radius: 4px; padding: 8px;
}
.designer-table {
  border-collapse: collapse; table-layout: fixed; background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  .designer-cell {
    border: 1px solid #c0c4cc; padding: 4px 6px; font-size: 12px;
    color: #303133; vertical-align: middle; position: relative; cursor: cell;
    min-width: 30px; word-break: break-all;
    &:hover { border-color: #409eff; background: #f0f7ff; }
    &.selected {
      border: 2px solid #409eff !important; background: #ecf5ff;
      box-shadow: inset 0 0 0 1px #fff;
    }
    .cell-content { display: block; min-height: 16px; outline: none; }
    .cell-input {
      width: 100%; border: none; outline: none; padding: 0;
      font-size: 12px; font-family: inherit; background: transparent;
    }
  }
  tr.header-row .designer-cell {
    background: #f0f2f5; font-weight: 600;
  }
}
.col-resize-handles {
  position: absolute; top: 8px; left: 8px; height: calc(100% - 16px); pointer-events: none;
}
.col-resize-handle {
  position: absolute; top: 0; width: 8px; height: 100%; cursor: col-resize;
  pointer-events: auto; z-index: 10;
  &:hover, &:active {
    &::after {
      content: ''; position: absolute; left: 3px; top: 0; width: 2px; height: 100%;
      background: #409eff;
    }
  }
}
.designer-info {
  display: flex; align-items: center; gap: 14px; padding: 6px 12px;
  font-size: 12px; color: #606266;
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 4px;
  .el-button { margin-left: auto; }
}

/* 右键菜单 */
.designer-context-menu {
  position: fixed; z-index: 9999; min-width: 160px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 4px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12); padding: 4px 0;
  .ctx-item {
    display: flex; align-items: center; gap: 6px; padding: 7px 14px;
    font-size: 13px; color: #303133; cursor: pointer;
    &:hover { background: #ecf5ff; color: #409eff; }
    &.disabled { color: #c0c4cc; cursor: not-allowed; &:hover { background: transparent; } }
  }
  .ctx-divider { height: 1px; background: #ebeef5; margin: 4px 0; }
}
</style>
