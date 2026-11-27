<template>
  <div class="bom-edit-page">
    <!-- 顶部：返回 + 标题 + 操作按钮 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="goBack">
          <span class="back-arrow">←</span>
          <span class="back-text">{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">{{ isEdit ? '编辑BOM' : '新增BOM' }}</div>
          <div class="page-sub">
            <span>{{ $tt("填写BOM物料清单，带") }} <span class="req">*</span> {{ $tt("为必填项") }}</span>
            <el-tag v-if="isEdit" :type="statusTagType(form.status)" size="small" style="margin-left:8px;">{{ $dt(form.status) }}</el-tag>
            <el-tag v-if="isEdit && form.approvalStatus" type="warning" size="small" style="margin-left:4px;">审核: {{ $dt(form.approvalStatus) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-actions">
        <!-- 统一单据按钮条（2026-10-09）：BOM 专属动作（操作日志 / BOM展开 / 停用 / 启用）随 extra-actions 一并进入同一条按钮栏，
             与全站单据同尺寸、同间距、同顺序；collapse 打开「放不下自动折叠」——超出宽度时末尾次要按钮收进「更多」下拉，
             保存 / 提交 / 打印 / 复制 四个主操作永不折叠。 -->
        <BillActions bill-type="BOM"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.bomId"
          :supported-actions="supportedActions"
          collapse
          :extra-actions="extraActions"
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

    <!-- 审计信息（仅编辑模式显示） -->
    <div v-if="isEdit && hasAuditInfo" class="audit-info-bar">
      <div class="audit-item">
        <span class="audit-label">{{ $tt("创建人：") }}</span>
        <span class="audit-value">{{ form.createdBy || '-' }}</span>
      </div>
      <div class="audit-item">
        <span class="audit-label">{{ $tt("创建时间：") }}</span>
        <span class="audit-value">{{ formatDateTime(form.createdAt) }}</span>
      </div>
      <div class="audit-item">
        <span class="audit-label">{{ $tt("修改人：") }}</span>
        <span class="audit-value">{{ form.updatedBy || '-' }}</span>
      </div>
      <div class="audit-item">
        <span class="audit-label">{{ $tt("修改时间：") }}</span>
        <span class="audit-value">{{ formatDateTime(form.updatedAt) }}</span>
      </div>
      <div class="audit-item" v-if="form.approvedBy">
        <span class="audit-label">{{ $tt("审核人：") }}</span>
        <span class="audit-value">{{ form.approvedBy }}</span>
      </div>
      <div class="audit-item" v-if="form.approvedAt">
        <span class="audit-label">{{ $tt("审核时间：") }}</span>
        <span class="audit-value">{{ formatDateTime(form.approvedAt) }}</span>
      </div>
    </div>

    <!-- 表单内容区 -->
    <div class="form-content">
      <!-- 区域 1: 基本信息 -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("基本信息") }}</span>
          <span class="title-sub">{{ $tt("BOM单据标识与分类") }}</span>
        </div>
        <el-form :model="form" :rules="rules" label-position="right" label-width="88px" class="form-grid">
          <el-form-item :label="$tf('单据编号')">
            <el-input v-model="form.bomCode" :placeholder="docCodePlaceholder" :disabled="docCodeDisabled && !isEdit || !isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('单据日期')" prop="bomDate" required>
            <el-date-picker
              v-model="form.bomDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width:100%"
              :disabled="!isDraft"
            />
          </el-form-item>
          <el-form-item :label="$tf('BOM分类')">
            <el-select v-model="form.bomCategory" :placeholder="$tt('请选择')" style="width:100%" :disabled="!isDraft">
              <el-option :label="$tf('标准BOM')" value="标准BOM" />
              <el-option :label="$tf('工程BOM')" value="工程BOM" />
              <el-option :label="$tf('生产BOM')" value="生产BOM" />
              <el-option :label="$tf('成本BOM')" value="成本BOM" />
              <el-option :label="$tf('委外BOM')" value="委外BOM" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('BOM版本')">
            <el-input v-model="form.bomVersion" :placeholder="$tt('如V1.0')" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('BOM类型')">
            <el-select v-model="form.bomType" :placeholder="$tt('请选择')" style="width:100%" :disabled="!isDraft">
              <el-option :label="$tf('标准BOM')" value="标准BOM" />
              <el-option :label="$tf('工程BOM')" value="工程BOM" />
              <el-option :label="$tf('生产BOM')" value="生产BOM" />
              <el-option :label="$tf('成本BOM')" value="成本BOM" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('默认BOM')">
            <el-switch v-model="isDefaultChecked" :disabled="!isDraft" @change="onDefaultChange" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 2: 母件信息 -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("母件信息") }}</span>
          <span class="title-sub">{{ $tt("父项物料基本信息") }}</span>
        </div>
        <el-form :model="form" :rules="rules" label-position="right" label-width="88px" class="form-grid">
          <el-form-item :label="$tf('母件物料')" prop="materialId" required>
            <el-select
              v-model="form.materialId"
              filterable
              :placeholder="materialSelectPlaceholder"
              style="width:100%"
              :disabled="!isDraft"
              @change="onMaterialChange"
              :filter-method="(val) => materialFilterMethod(val, parentMaterialFilterRow, parentMaterials)"
             popper-class="material-select-popper">
              <el-option v-for="m in (parentMaterialFilterRow._filteredMaterials || parentMaterials)" :key="m.materialId" :label="`${m.materialName} (${m.materialCode}) [${m.materialType}]`" :value="m.materialId">
                  <span class="opt-code">{{ m.materialCode }}</span>
                  <span class="opt-name">{{ m.materialName }}</span>
                  <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
                  <span v-if="m.model" class="opt-model">{{ m.model }}</span>
                </el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('母件编号')">
            <el-input v-model="form.materialCode" readonly />
          </el-form-item>
          <el-form-item :label="$tf('母件名称')">
            <el-input v-model="form.materialName" readonly />
          </el-form-item>
          <el-form-item :label="$tf('规格')">
            <el-input v-model="form.specification" readonly />
          </el-form-item>
          <el-form-item :label="$tf('规格属性')">
            <el-input v-model="form.specProperty" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('色号')">
            <el-input v-model="form.colorNo" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('产地')">
            <el-input v-model="form.origin" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('材质')">
            <el-input v-model="form.materialQuality" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('基本单位')" prop="unitId" required>
            <el-select v-model="form.unitId" :placeholder="$tt('选择单位')" style="width:100%" :disabled="!isDraft">
              <el-option v-for="u in units" :key="u.unitId" :label="u.unitName" :value="u.unitId" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 3: 其他信息 -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("其他信息") }}</span>
          <span class="title-sub">{{ $tt("负责人、仓库与生效日期") }}</span>
        </div>
        <el-form :model="form" label-position="right" label-width="88px" class="form-grid">
          <el-form-item :label="$tf('负责人')">
            <el-select v-model="form.managerId" filterable :placeholder="$tt('选择负责人')" style="width:100%" :disabled="!isDraft">
              <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('默认仓库')">
            <el-select v-model="form.defaultWarehouseId" filterable :placeholder="$tt('选择仓库')" style="width:100%" :disabled="!isDraft">
              <el-option v-for="w in withFallbackOption(warehouses, 'defaultWarehouseId', 'warehouseName', form)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('生效日期')">
            <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('失效日期')">
            <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!isDraft" />
          </el-form-item>
          <el-form-item :label="$tf('备注')" class="full-row">
            <el-input v-model="form.remark" type="textarea" :rows="2" :disabled="!isDraft" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 4: BOM明细（通用明细组件 BillItemsTable：卡片/列设置/动态列/展开行 全站共用一套） -->
      <BillItemsTable
        v-model:collapsed="detailCollapsed"
        :items="form.items"
        page-key="BomEdit_items"
        title="BOM明细（子件）"
        :columns="detailColumns"
        :custom-columns="itemCustomColumns"
        :readonly="!isDraft"
        :expandable="hasAnyChildBom"
        :scan-add="false"
        :show-summary="false"
        :operation-width="80"
      >
        <!-- 标题行右侧按钮：从物料库选择 / 添加子件（列设置齿轮由通用组件自带） -->
        <template #actions>
          <el-button v-permission="perm('pick')" type="success" size="small" @click="openMaterialSelector">
            <el-icon><Collection /></el-icon> 从物料库选择
          </el-button>
          <el-button v-permission="perm('addRow')" type="primary" size="small" @click="addItem">
            <el-icon><Plus /></el-icon> 添加子件
          </el-button>
        </template>

        <!-- 展开行：该子件是半成品时，展开显示它的下级 BOM 构成 -->
        <template #expand="{ row }">
          <div v-if="row.materialCode && childBomData[row.materialCode]" class="child-bom-expand">
            <div class="child-bom-title">
              <el-icon><Connection /></el-icon>
              <span>下级 BOM 明细（{{ row.materialName }}）</span>
            </div>
            <el-table :data="childBomData[row.materialCode].items || []" size="small" border style="width:100%;margin-top:8px;">
              <el-table-column type="index" :label="$tf('行号')" width="60" />
              <el-table-column prop="materialCode" :label="$tf('子件编码')" width="120" />
              <el-table-column prop="materialName" :label="$tf('子件名称')" min-width="120" />
              <el-table-column prop="specification" :label="$tf('规格')" width="100" />
              <el-table-column prop="unitName" :label="$tf('单位')" width="70" />
              <el-table-column prop="quantity" :label="$tf('用量')" width="100" />
              <el-table-column prop="scrapRate" :label="$tf('损耗率(%)')" width="90" />
            </el-table>
            <div v-if="!childBomData[row.materialCode].items || childBomData[row.materialCode].items.length === 0" style="padding:20px;text-align:center;color:#909399;">
              暂无下级BOM明细
            </div>
          </div>
          <div v-else-if="row.materialCode && childBomLoading[row.materialCode]" style="padding:20px;text-align:center;">
            <el-icon class="is-loading" style="font-size:20px;"><Loading /></el-icon>
            <span style="margin-left:8px;color:#909399;">{{ $tt("加载中...") }}</span>
          </div>
        </template>

        <!-- 子件物料：带自定义字段提示的可搜索下拉（与迁移前一致） -->
        <template #cell-materialId="{ row }">
          <el-select
            v-model="row.materialId"
            filterable
            :placeholder="materialSelectPlaceholder"
            style="width:100%"
            :disabled="!isDraft"
            @change="(v) => onItemMaterialChange(row, v)"
            :filter-method="(val) => materialFilterMethod(val, row, childMaterials)"
           popper-class="material-select-popper">
            <el-option v-for="m in (row._filteredMaterials || childMaterials)" :key="m.materialId" :label="`${m.materialName} (${m.materialCode})`" :value="m.materialId">
              <span class="opt-code">{{ m.materialCode }}</span>
              <span class="opt-name">{{ m.materialName }}</span>
              <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
              <span v-if="m.model" class="opt-model">{{ m.model }}</span>
            </el-option>
          </el-select>
        </template>

        <!-- 图片：无图时显示「无图」（与迁移前一致） -->
        <template #cell-materialMainImage="{ row }">
          <el-image
            v-if="row.materialMainImage"
            :src="row.materialMainImage"
            :preview-src-list="[row.materialMainImage]"
            fit="cover"
            preview-teleported
            hide-on-click-modal
            style="width:40px;height:40px;border-radius:4px;"
          />
          <span v-else style="color:#c0c4cc;font-size:12px;">{{ $tt("无图") }}</span>
        </template>

        <!-- 子件BOM编号：半成品才有值，点击行首箭头可查看其下级BOM构成 -->
        <template #cell-childBomCode="{ row }">
          <el-tooltip v-if="childBomCodeMap[row.materialCode]" :content="$tt('该子件为半成品，点击行首箭头可查看其下级BOM构成')" placement="top">
            <span class="child-bom-code">
              <el-icon><Connection /></el-icon>{{ childBomCodeMap[row.materialCode] }}
            </span>
          </el-tooltip>
          <span v-else-if="childBomLoading[row.materialCode]" style="color:#c0c4cc;font-size:12px;">{{ $tt("检查中...") }}</span>
          <span v-else style="color:#c0c4cc;"></span>
        </template>

        <!-- 成本金额 = 该行 用量 ×（1＋损耗率%÷100）× 成本，逐行独立计算 -->
        <template #cell-costAmount="{ row }">
          <span class="cost-amount-cell">{{ fmtAmount(rowCostAmount(row)) }}</span>
        </template>

        <!-- 操作列：删除子件并按顺序重排行号（与原页面同一处理） -->
        <template #operation="{ row, index }">
          <el-button v-permission="perm('deleteRow')" text type="danger" size="small" @click="removeItem(index)" :disabled="!isDraft">{{ $tt("删除") }}</el-button>
        </template>
      </BillItemsTable>

      <!-- 成本合计（2026-11-03）：各行「成本金额」相加 -->
      <div class="detail-cost-summary">
        <span class="label">{{ $tt("成本合计：") }}</span>
        <span class="value">{{ fmtAmount(totalCostAmount) }}</span>
      </div>
      <!-- 区域 5: 多级BOM展开 -->
      <div class="form-card bom-expand-section" v-if="form.materialCode">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("BOM展开") }}</span>
          <span class="title-sub">{{ $tt("点击展开当前BOM的所有层级子件构成（半成品显示其BOM编号）") }}</span>
        </div>
        <div class="card-actions">
          <el-button v-permission="perm('view')" type="primary" size="small" @click="loadBomTree" :loading="treeLoading">
            <el-icon><Refresh /></el-icon> BOM展开
          </el-button>
          <el-button v-permission="perm('view')" size="small" @click="loadBomFlatten" :loading="flattenLoading">
            <el-icon><List /></el-icon> 扁平化视图
          </el-button>
        </div>
        <div v-if="bomTree" class="bom-tree-view">
          <div class="tree-node root-node">
            <span class="node-tag">L0</span>
            <span class="node-name">{{ bomTree.materialName }} ({{ bomTree.materialCode }})</span>
            <span v-if="bomTree.bomCode" class="node-bomno">BOM: {{ bomTree.bomCode }}</span>
            <span class="node-qty">× {{ bomTree.quantity }}</span>
          </div>
          <div v-if="bomTree.children && bomTree.children.length" class="tree-children">
            <BomTreeNode
              v-for="(child, idx) in bomTree.children"
              :key="idx"
              :node="child"
              :level="1"
            />
          </div>
          <el-empty v-else :description="$tt('该BOM暂无子件明细')" :image-size="60" />
        </div>
        <div v-if="bomFlatten && bomFlatten.length" class="bom-flatten-view">
          <el-table :data="bomFlatten" border size="small">
            <el-table-column type="index" :label="$tf('序号')" width="60" />
            <el-table-column prop="materialCode" :label="$tf('物料编码')" width="140" />
            <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="160" />
            <el-table-column prop="specification" :label="$tf('规格')" width="120" />
            <el-table-column prop="unit" :label="$tf('单位')" width="80" />
            <el-table-column :label="$tf('汇总数量')" width="120">
              <template #default="{ row }">
                <span style="color:#409EFF;font-weight:600;">{{ row.totalQuantity }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="purchasePrice" :label="$tf('采购单价')" width="110" />
            <el-table-column prop="salePrice" :label="$tf('销售单价')" width="110" />
          </el-table>
        </div>
        <el-empty v-if="!bomTree && !bomFlatten" :description="$tt('点击上方按钮展开多级BOM')" :image-size="60" />
      </div>
    </div>

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <!-- 子件选择：沿用原弹窗标题；排除「成品」（成品不能做子件），与明细行下拉 childMaterials 同口径 -->
    <MaterialPickerDialog
      v-model="materialSelectorVisible"
      :title="$tf('从物料库选择子件')"
      :exclude-fn="(m) => m.materialType === '成品'"
      @confirm="onMaterialPicked"
    />

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

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="BOM清单" :bill-no="form.bomCode || ''" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="BOM" :bill-id="form.bomId || route.params.id" />

    <!-- 子件列设置由通用明细组件 BillItemsTable 自带（page-key = BomEdit_items，沿用原有列设置存储） -->
  </div>
  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h, defineComponent, nextTick } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Plus, Refresh, List, Clock, CaretBottom, Loading, Connection } from '@element-plus/icons-vue'
import { api } from '@/api'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  injectMaterialCustomFields,
  customFieldsToTableColumns,
  validateItemCustomRequired
} from '@/composables/useCustomField'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import MaterialFieldCell from '@/components/MaterialFieldCell.vue'
// 列设置由通用明细组件 BillItemsTable 内部承担（page-key 仍为 BomEdit_items，沿用原有列配置）
import { usePrecision } from '@/composables/usePrecision'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { canEdit } from '@/utils/bill-status'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import BillActions from '@/components/bill/BillActions.vue'
import { tt } from '@/i18n'
import { useMaterialCustomFields, materialArchiveRowFields } from '@/composables/useMaterialCustomFields'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('masterdata:bom')

// BOM子件列（通用明细组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 可编辑列的控件口径与原页面一致（文本/下拉/数值/开关/日期），列设置与拖拽由通用组件承担
const detailColumns = [
  { prop: 'materialId', label: '子件物料', width: 240 },
  { prop: 'materialMainImage', label: '图片', width: 70, align: 'center', type: 'image' },
  { prop: 'materialCode', label: '子件编码', width: 120 },
  { prop: 'materialName', label: '子件名称', minWidth: 120 },
  { prop: 'childBomCode', label: '子件BOM编号', width: 170, type: 'childBom' },
  { prop: 'modelNo', label: '型号', width: 110, editor: 'text' },
  { prop: 'specProperty', label: '规格属性', width: 100, editor: 'text' },
  { prop: 'unitId', label: '单位', width: 80, editor: 'select', nameProp: 'unitName', options: () => units.value.map(u => ({ value: u.unitId, label: u.unitName })) },
  { prop: 'quantity', label: '用量', width: 110, editor: 'number', precision: 4, min: 0 },
  { prop: 'qtyType', label: '用量类型', width: 100, editor: 'select', options: [{ value: '变动', label: '变动' }, { value: '固定', label: '固定' }] },
  { prop: 'scrapRate', label: '损耗率(%)', width: 100, editor: 'number', precision: 2, min: 0, max: 100 },
  { prop: 'supplyType', label: '供应类型', width: 100, editor: 'select', options: [{ value: '领料', label: '领料' }, { value: '倒冲', label: '倒冲' }, { value: '虚拟件', label: '虚拟件' }, { value: '委外', label: '委外' }] },
  { prop: 'processNo', label: '工序号', width: 90, editor: 'text' },
  { prop: 'isKeyPart', label: '关键件', width: 70, align: 'center', editor: 'switch' },
  { prop: 'positionNo', label: '位号', width: 90, editor: 'text' },
  { prop: 'effectiveDate', label: '生效日期', width: 110, editor: 'date' },
  { prop: 'expiryDate', label: '失效日期', width: 110, editor: 'date' },
  // 成本（2026-11-03）＝ 子件物料档案「最新成本」自动带出、只读；materialArchive 标记 = 本列就是档案列（并入档案清单时自动去重）
  { prop: 'avgCost', label: '成本', width: 110, align: 'right', type: 'amount4', materialArchive: true },
  // 成本金额（2026-11-03）＝ 该行 用量 ×（1＋损耗率%÷100）× 成本，逐行独立计算（由 #cell-costAmount 插槽渲染）
  { prop: 'costAmount', label: '成本金额', width: 120, align: 'right', type: 'costAmount' }
]
// 明细卡片折叠状态（通用明细组件 BillItemsTable 的 v-model:collapsed，true = 展开）
const detailCollapsed = ref(true)

// ===== 成本口径（2026-11-03）=====
// 成本 = 子件物料档案「最新成本」（随物料自动带出、只读）；成本金额 = 该行 用量 ×（1＋损耗率%÷100）× 成本，逐行独立计算
const { fmtAmount } = usePrecision()
function rowCostAmount(row) {
  if (!row) return 0
  const qty = Number(row.quantity) || 0
  const scrap = Number(row.scrapRate) || 0
  const cost = Number(row.avgCost) || 0
  return qty * (1 + scrap / 100) * cost
}
const totalCostAmount = computed(() => (form.items || []).reduce((sum, r) => sum + rowCostAmount(r), 0))

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('MD_BOM')

const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const materialCustomValueMap = ref({})
// 物料实体的自定义字段定义（用于同名 label 关联：物料档案"芯片"→BOM明细"芯片"自动带出）
const materialCustomFieldDefs = ref([])
// BOM明细自定义字段列（明细表格动态渲染）
const itemCustomColumns = ref([])

function rebuildItemCustomColumns() {
  // 编辑态全部字段可填；isListShow=0 的列 defaultVisible=false（列设置默认不勾选）
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: true, listShowOnly: false })
}

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('bom')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields
  const itemResult = await loadCustomFieldDefinitions('bom_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  rebuildItemCustomColumns()
  // 加载物料实体字段定义（同名字段关联取值用）
  try {
    const materialResult = await loadCustomFieldDefinitions('material')
    materialCustomFieldDefs.value = Array.from(materialResult.fieldMap.values())
  } catch (e) {
    materialCustomFieldDefs.value = []
  }
}

const materials = ref([])
// 物料自定义字段：行内下拉附加展示 + 物料库弹窗全字段（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
const units = ref([])
const employees = ref([])
const warehouses = ref([])

// 母件物料黑名单：不显示「原材料」「包材」「辅助材料」属性的物料（这三类不能作为母件），其余属性全部放行
const parentMaterials = computed(() =>
  materials.value.filter(m => !['原材料', '包材', '辅助材料'].includes(m.materialType))
)
// 子件物料黑名单：不显示「成品」属性的物料，其它属性全部放行（原材料/半成品/包材/辅料/未来新增类型等）
const childMaterials = computed(() =>
  materials.value.filter(m => m.materialType !== '成品')
)

// 母件下拉在表头、没有明细行 row 作用域：给过滤结果一个载体
const parentMaterialFilterRow = reactive({ _filteredMaterials: null })
// 参与搜索的物料自定义字段：勾了「可搜索」的 ∪（下拉显示开关开启且勾了「下拉显示」的）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
// 物料下拉的背景提示文字：声明可搜索范围（没有可搜索的自定义字段时原样返回「选择物料」）
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/**
 * 物料下拉：输入即过滤（编码/名称/规格 + 自定义字段值/字段名）
 * @param {Array} list 候选物料列表（母件/子件各有类型黑名单，传各自候选列表；不传则用全量物料列表）
 */
function materialFilterMethod(val, row, list) {
  if (!val) { row._filteredMaterials = null; return }
  // 用「已预取全量值」的 mcf.valueMap，不能只用已选中物料的值表，否则搜不到
  row._filteredMaterials = filterMaterials(list || materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

// ===== 物料档案字段补齐（2026-11-03 全站统一口径）=====
// 勾出的档案列（规格型号/物料分类/单价/最新成本…）要有值：从本页已加载的物料档案补到行上
// （/list 接口本身回填 分类名/基本单位名/最新成本，无需按行再发请求）。
// 保护口径：只接管「本轮自己补过的字段」和「行上尚未声明的字段」——页面行字段（备注/税率等）一律不碰；换料自动重取。
function applyArchiveToRow(row) {
  if (!row || row.materialId == null) return
  if (row._archFor === String(row.materialId)) return
  const m = materials.value.find(x => x.materialId === row.materialId)
  if (!m) return
  const fields = materialArchiveRowFields(detailColumns)
  const taken = Array.isArray(row._archKeys) ? row._archKeys.slice() : []
  fields.forEach(k => {
    if (taken.includes(k)) {
      if (m[k] !== undefined) row[k] = m[k]
      return
    }
    if (!(k in row) && m[k] !== undefined) {
      row[k] = m[k]
      taken.push(k)
    }
  })
  row._archKeys = taken
  row._archFor = String(row.materialId)
}
function enrichItemsArchive() {
  ;(form.items || []).forEach(applyArchiveToRow)
}
// 监听注册在下方 form 声明之后（避免 TDZ）

const isDefaultChecked = ref(false)
const changeRecordVisible = ref(false)

const form = reactive({
  bomId: null,
  bomCode: '',
  bomName: '',
  bomDate: new Date().toISOString().slice(0, 10),
  bomCategory: '标准BOM',
  materialId: null,
  materialCode: '',
  materialName: '',
  specification: '',
  specProperty: '',
  colorNo: '',
  origin: '',
  materialQuality: '',
  bomVersion: 'V1.0',
  unitId: null,
  quantity: 1,
  bomType: '标准BOM',
  isDefault: 0,
  isPrimary: 0,
  isEnabled: 1,
  status: '草稿',
  approvalStatus: '',
  managerId: null,
  managerName: '',
  defaultWarehouseId: null,
  defaultWarehouseName: '',
  effectiveDate: '',
  expiryDate: '',
  substituteBomCode: '',
  scrapRate: 0,
  remark: '',
  items: [],
  // 审计字段
  createdAt: null,
  updatedAt: null,
  createdBy: '',
  updatedBy: '',
  approvedBy: '',
  approvedAt: null
})


const isDraft = computed(() => canEdit(form.status, form.closeStatus))
const hasAuditInfo = computed(() =>
  isEdit.value && !!(form.createdAt || form.updatedAt || form.createdBy || form.updatedBy)
)

// 子件行物料变化（选料/换料/装单/复制）→ 自动补齐物料档案字段（2026-11-03 全站统一口径）
watch(() => (form.items || []).map(r => (r && r.materialId) || '').join('|'), () => { enrichItemsArchive() })

// 页头「统一单据按钮条」里的 BOM 专属动作（数组顺序 = 展示顺序；放不下时从末尾往前折叠）。
// 主操作（保存/提交/打印/复制）在按钮条内部固定不折，以下动作均可收进「更多」下拉。
const extraActions = computed(() => [
  { key: 'changeRecord', label: tt('操作日志'), icon: Clock, perm: perm('view'), handler: () => { changeRecordVisible.value = true } },
  { key: 'bomExpand', label: tt('BOM展开'), icon: Connection, perm: perm('view'), show: !!form.materialCode, loading: treeLoading.value, handler: loadBomTree },
  { key: 'disable', label: tt('停用'), perm: perm('disable'), show: form.status === '已生效', handler: confirmDisable },
  { key: 'enable', label: tt('启用'), type: 'primary', perm: perm('enable'), show: form.status === '已停用', handler: confirmEnable }
])

function formatDateTime(val) {
  if (!val) return '-'
  if (Array.isArray(val)) {
    const [y, mo, d, h = 0, mi = 0, se = 0] = val
    return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}:${String(se).padStart(2, '0')}`
  }
  const s = String(val)
  return s.length > 19 ? s.slice(0, 19).replace('T', ' ') : s.replace('T', ' ')
}

const rules = {
  bomDate: [{ required: true, message: tt('请选择单据日期'), trigger: 'change' }],
  materialId: [{ required: true, message: tt('请选择母件物料'), trigger: 'change' }],
  unitId: [{ required: true, message: tt('请选择基本单位'), trigger: 'change' }]
}

function statusTagType(s) {
  return { '草稿': 'info', '待审批': 'warning', '已生效': 'success', '已停用': 'danger' }[s] || 'info'
}

function onDefaultChange(val) {
  form.isDefault = val ? 1 : 0
}

function onMaterialChange(materialId) {
  const m = materials.value.find(x => x.materialId === materialId)
  if (m) {
    form.materialCode = m.materialCode
    form.materialName = m.materialName
    form.specification = m.model || ''
    form.unitId = m.baseUnitId
    form.bomName = m.materialName + ' ' + form.bomVersion
  }
}

function onItemMaterialChange(row, materialId) {
  const m = childMaterials.value.find(x => x.materialId === materialId)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.specification = m.model || ''
    row.unitId = m.baseUnitId
    row.materialMainImage = m.mainImage || ''
    if (m && m.materialId && itemCustomFields.value.length > 0) {
      if (!materialCustomValueMap.value[m.materialId]) {
        batchLoadCustomFieldValues('material', [m.materialId]).then(map => {
          Object.assign(materialCustomValueMap.value, map)
          injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value, materialCustomFieldDefs.value)
        }).catch(() => {})
      } else {
        injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value, materialCustomFieldDefs.value)
      }
    }
    // 检查是否有下级BOM
    checkChildBom(row)
  }
}

function addItem() {
  form.items.push({
    bomItemId: null,
    bomId: null,
    lineNo: form.items.length + 1,
    materialId: null,
    materialCode: '',
    materialName: '',
    modelNo: '',
    specProperty: '',
    specification: '',
    imageUrl: '',
    materialMainImage: '',
    unitId: null,
    quantity: 1,
    qtyType: '变动',
    scrapRate: 0,
    effectiveDate: '',
    expiryDate: '',
    processNo: '',
    isKeyPart: 0,
    supplyType: '领料',
    positionNo: '',
    workstationId: null,
    isOptional: 0,
    parentLineNo: null,
    substituteLineNo: null,
    remark: ''
  })
}

function removeItem(index) {
  form.items.splice(index, 1)
  form.items.forEach((item, i) => {
    item.lineNo = i + 1
  })
}

async function persistBom(opts = {}) {
  // 保存=草稿宽松：母件/单位/明细等必填校验不在保存时执行，统一在提交(saveAndSubmit)时校验
  // 保存宽松：丢弃未选子件物料的空行（后端 material_code/material_name 等列 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)

  try {
    let res
    if (isEdit.value) {
      res = await api.put(`/masterdata/bom/${route.params.id}`, form)
      ElMessage.success(tt('保存成功'))
    } else {
      res = await api.post('/masterdata/bom', form)
      if (res && res.bomId) form.bomId = res.bomId
      ElMessage.success(tt('创建成功'))
    
      if (!opts.skipRouteSync && !route.params.id && form.bomId) {
        try { await router.replace({ name: 'BomEdit', params: { id: String(form.bomId) } }) } catch (_) {}
      }
    }
    if (headerCustomFieldMap.size > 0 && form.bomId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('bom', form.bomId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.bomItemId) continue
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('bom_item', row.bomItemId, values)
      }
    }
    return res
  } catch (e) {
    console.warn('保存失败', e)
    return null
  }
}

async function saveDraft() {
  const res = await persistBom()
  if (res) {
    if (!isEdit.value && res.bomId) {
      router.push(`/masterdata/bom/${res.bomId}`)
    } else {
      loadDetail()
    }
  }
}

async function saveAndBack() {
  const res = await persistBom()
  if (res) {
    ElMessage.success(tt('保存成功'))
    router.push('/masterdata/bom')
  }
}

async function doConfirmSubmit() {
  try {
    const needApproval = await confirmSubmit({ billType: 'BOM', billLabel: 'BOM清单' })
    await api.post(`/masterdata/bom/${route.params.id}/submit`)
    ElMessage.success(submitSuccessMessage(needApproval))
    loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  }
}

async function confirmDisable() {
  try {
    await ElMessageBox.confirm(tt('确认停用此BOM?'), tt('停用'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await api.post(`/masterdata/bom/${route.params.id}/disable`)
    ElMessage.success(tt('停用成功'))
    loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('停用失败'))
  }
}

async function confirmEnable() {
  try {
    await ElMessageBox.confirm(tt('确认启用此BOM?'), tt('启用'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await api.post(`/masterdata/bom/${route.params.id}/enable`)
    ElMessage.success(tt('启用成功'))
    loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('启用失败'))
  }
}

function goBack() {
  router.push('/masterdata/bom')
}

async function loadDetail() {
  if (!isEdit.value) {
    // 新建时默认当前用户为负责人（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.managerId) form.managerId = me.employeeId
    return
  }
  try {
    const res = await api.get(`/masterdata/bom/${route.params.id}`)
    Object.assign(form, res)
    isDefaultChecked.value = res.isDefault === 1
    if (!form.items) form.items = []
    if (form.items && materials.value && materials.value.length > 0) {
      form.items.forEach(row => {
        if (!row.materialMainImage && row.materialId) {
          const m = materials.value.find(x => x.materialId === row.materialId)
          row.materialMainImage = m?.mainImage || ''
        }
      })
    }
    if (headerCustomFieldMap.size > 0 && form.bomId) {
      const headerValues = await loadCustomFieldValues('bom', form.bomId)
      Object.assign(form, headerValues)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      const itemIds = form.items.map(it => it.bomItemId).filter(Boolean)
      if (itemIds.length > 0) {
        const itemValuesMap = await batchLoadCustomFieldValues('bom_item', itemIds)
        form.items.forEach(row => {
          const values = itemValuesMap[row.bomItemId]
          if (values) Object.assign(row, values)
        })
      }
    }
    // 检查所有子件是否有下级BOM
    if (form.items && form.items.length > 0) {
      form.items.forEach(row => {
        if (row.materialCode) {
          checkChildBom(row)
        }
      })
    }
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  }
}

function flushInputNumbers() {
  const list = form.items || form.value?.items || form.details || form.value?.details
  if (!list || list.length === 0) return
  list.forEach(row => {
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    if (row.unitPrice !== undefined) row.unitPrice = Number(row.unitPrice || 0)
    if (row.taxRate !== undefined) row.taxRate = Number(row.taxRate || 0)
    if (row.discountRate !== undefined) row.discountRate = Number(row.discountRate || 0)
    if (row.discountAmount !== undefined) row.discountAmount = Number(row.discountAmount || 0)
    if (row.amount !== undefined) row.amount = Number(row.amount || 0)
    if (row.taxAmount !== undefined) row.taxAmount = Number(row.taxAmount || 0)
    if (row.netAmount !== undefined) row.netAmount = Number(row.netAmount || 0)
  })
  if (form.items) form.items = [...form.items]
  else if (form.value?.items) form.value.items = [...form.value.items]
  else if (form.details) form.details = [...form.details]
  else if (form.value?.details) form.value.details = [...form.value.details]
}

// ===== 统一单据操作按钮（保存/提交/审核/驳回/作废/删除等）=====
function onSave() {
  flushInputNumbers()
  return saveDraft()
}

async function saveAndSubmit() {
  // 提交=严格校验：母件/单位/明细/自定义字段必填校验在此执行（保存已放宽）
  if (!form.materialId) { ElMessage.warning(tt('请选择母件物料')); return }
  if (!form.unitId) { ElMessage.warning(tt('请选择基本单位')); return }
  if (!form.items || form.items.length === 0) { ElMessage.warning(tt('请至少添加一条BOM明细')); return }
  for (let i = 0; i < form.items.length; i++) {
    if (!form.items[i].materialId) { ElMessage.warning(`${tt("第")}${i + 1}${tt("行子件物料不能为空")}`); return }
  }
  const cfRequiredError = validateItemCustomRequired(form.items, itemCustomFields.value)
  if (cfRequiredError) { ElMessage.warning(cfRequiredError); return }

  const res = await persistBom({ skipRouteSync: true })
  if (!res) return
  const id = form.bomId || route.params.id
  if (!id) return
  try {
    const needApproval = await confirmSubmit({ billType: 'BOM', billLabel: 'BOM清单' })
    await api.post(`/masterdata/bom/${id}/submit`)
    ElMessage.success(submitSuccessMessage(needApproval))
    if (!isEdit.value && form.bomId) {
      router.push(`/masterdata/bom/${form.bomId}`)
    } else {
      loadDetail()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  }
}

function onSubmit() {
  flushInputNumbers()
  if (!route.params.id) return saveAndSubmit()
  return doConfirmSubmit()
}

const printVisible = ref(false)
function handlePrint() {
  const id = form.bomId || route.params.id
  if (!id) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}

const {
  supportedActions,
  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.bomId || route.params.id,
  // 后端 BomController 实际提供的动作路由：
  //   /approve(132) /reject(140,重置草稿) /reverse-approve(233) /void(240) /unvoid(247) DELETE /{id}(87)
  //   —— 无 close / reopen 路由，故不接线（按钮自动隐藏）
  api: {
    approve: (id) => api.post(`/masterdata/bom/${id}/approve`),
    reject: (id, reason) => api.post(`/masterdata/bom/${id}/reject`, { reason }),
    reverseApprove: (id) => api.post(`/masterdata/bom/${id}/reverse-approve`),
    void: (id, reason) => api.post(`/masterdata/bom/${id}/void`, { reason }),
    unvoid: (id) => api.post(`/masterdata/bom/${id}/unvoid`),
    delete: (id) => api.delete(`/masterdata/bom/${id}`)
  },
  reload: loadDetail,
  router,
  listPath: '/masterdata/bom',
  newPath: '/masterdata/bom/new',
  billLabel: 'BOM'
})

// 复制：URL 带 ?copy=id 打开时自动把源单填入当前表单（复制按钮 handleCopy 由 useBillActions 负责跳到该地址）
const { applyCopy } = useCopyBill({
  route,
  loadFn: (id) => api.get(`/masterdata/bom/${id}`),
  form,
  idField: 'bomId',
  noField: 'bomCode',
  itemIdField: 'bomItemId',
  itemsField: 'items',
  customField: { entity: 'bom', itemEntity: 'bom_item', itemIdField: 'bomItemId' }
})

async function loadBaseData() {
  try {
    const [matRes, unitRes, empRes, whRes] = await Promise.all([
      listAllMaterial(),
      listAllUnit(),
      listAllEmployee(),
      listAllWarehouse()
    ])
    materials.value = Array.isArray(matRes) ? matRes : (matRes?.records || [])
    // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
    mcf.preloadAll(materials.value)
    units.value = Array.isArray(unitRes) ? unitRes : (unitRes?.records || [])
    employees.value = Array.isArray(empRes) ? empRes : (empRes?.records || [])
    warehouses.value = Array.isArray(whRes) ? whRes : (whRes?.records || [])
  } catch (e) {
    console.warn('加载基础资料失败', e)
  }
}

// ========== 多级BOM展开 ==========
const bomTree = ref(null)
const bomFlatten = ref(null)
const treeLoading = ref(false)
const flattenLoading = ref(false)

async function loadBomTree() {
  if (!form.materialCode) {
    ElMessage.warning(tt('请先选择母件物料'))
    return
  }
  treeLoading.value = true
  try {
    // 编辑态优先按当前BOM展开（保证展开的就是本单BOM），新单按母件编码自动查找生效BOM
    const params = { materialCode: form.materialCode, quantity: form.quantity || 1 }
    if (isEdit && form.bomId) {
      params.bomId = form.bomId
    }
    const res = await api.get('/masterdata/bom/expand', { params })
    bomTree.value = res
    bomFlatten.value = null
    nextTick(() => {
      document.querySelector('.bom-expand-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  } catch (e) {
    ElMessage.error(e.message || tt('展开失败'))
  } finally {
    treeLoading.value = false
  }
}

async function loadBomFlatten() {
  if (!form.materialCode) {
    ElMessage.warning(tt('请先选择母件物料'))
    return
  }
  flattenLoading.value = true
  try {
    const res = await api.get('/masterdata/bom/flatten', {
      params: { materialCode: form.materialCode, quantity: form.quantity || 1 }
    })
    bomFlatten.value = res
    bomTree.value = null
  } catch (e) {
    ElMessage.error(e.message || tt('展开失败'))
  } finally {
    flattenLoading.value = false
  }
}

// ========== 子件BOM（下级BOM关联） ==========
// 记录每个子件物料是否有下级BOM
const hasChildBomMap = reactive({})
// 记录每个子件物料的下级BOM数据
const childBomData = reactive({})
// 记录每个子件物料的加载状态
const childBomLoading = reactive({})
// 是否已检查过子件BOM
const childBomChecked = reactive({})
// 子件自身的BOM编号（半成品下级结构入口，用于明细列展示）
const childBomCodeMap = reactive({})

// 是否有任何子件有BOM（控制展开列是否显示）
const hasAnyChildBom = computed(() => {
  return Object.values(hasChildBomMap).some(v => v)
})

// 检查并加载子件的下级BOM
async function checkChildBom(row) {
  if (!row.materialCode) return
  if (childBomChecked[row.materialCode]) return

  childBomChecked[row.materialCode] = true
  childBomLoading[row.materialCode] = true

  try {
    // 使用分页查询，按物料编码精确搜索
    const res = await api.get('/masterdata/bom/page', {
      params: {
        current: 1,
        size: 10,
        keyword: row.materialCode
      }
    })
    const records = res?.records || []
    // 过滤出物料编码完全匹配且未作废的BOM，已生效优先
    const bomList = records
      .filter(b => b.materialCode === row.materialCode && b.status !== '已作废')
      .sort((a, b) => (a.status === '已生效' ? -1 : 0) - (b.status === '已生效' ? -1 : 0))
    hasChildBomMap[row.materialCode] = bomList.length > 0

    // 如果有BOM，默认加载第一个BOM的明细
    if (bomList.length > 0) {
      const firstBom = bomList[0]
      childBomCodeMap[row.materialCode] = firstBom.bomCode
      if (firstBom.bomId) {
        const detailRes = await api.get(`/masterdata/bom/${firstBom.bomId}`)
        childBomData[row.materialCode] = detailRes
      }
    } else {
      childBomCodeMap[row.materialCode] = ''
    }
  } catch (e) {
    console.warn('检查子件BOM失败', e)
    hasChildBomMap[row.materialCode] = false
  } finally {
    childBomLoading[row.materialCode] = false
  }
}

// 切换子件BOM展开/收起
function toggleChildBom(row) {
  // 这个函数主要是为了提供点击交互，实际展开由 el-table 的 expand 列控制
  // 可以在这里做一些额外的事情
  if (!childBomChecked[row.materialCode]) {
    checkChildBom(row)
  }
}

// 当子件物料变化时，检查是否有下级BOM
function checkChildBomForRow(row) {
  if (row.materialCode) {
    checkChildBom(row)
  }
}

// ========== 物料选择弹窗（弹窗本体 = 通用组件 MaterialPickerDialog） ==========
const materialSelectorVisible = ref(false)

function openMaterialSelector() {
  materialSelectorVisible.value = true
}

/**
 * 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * - 组件内已批量取好选中物料的「全部自定义字段值」，并随 confirm 回传 valueMap
 * - 这里把值并入本页值表（与明细「同名字段带值」共用同一份，不再二次请求），再逐行组装子件行
 */
async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(materialCustomValueMap.value, valueMap)
  }
  picked.forEach(m => {
    const row = {
      bomItemId: null,
      bomId: null,
      lineNo: form.items.length + 1,
      materialId: m.materialId,
      materialCode: m.materialCode,
      materialName: m.materialName,
      modelNo: m.model || '',
      specProperty: '',
      specification: m.model || '',
      imageUrl: '',
      materialMainImage: m.mainImage || '',
      unitId: m.baseUnitId,
      quantity: 1,
      qtyType: '变动',
      scrapRate: 0,
      effectiveDate: '',
      expiryDate: '',
      processNo: '',
      isKeyPart: 0,
      supplyType: '领料',
      positionNo: '',
      workstationId: null,
      isOptional: 0,
      parentLineNo: null,
      substituteLineNo: null,
      remark: ''
    }
    if (itemCustomFields.value.length > 0) {
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value, materialCustomFieldDefs.value)
    }
    form.items.push(row)
  })
  materialSelectorVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 个子件")}`)
}

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  await loadCustomFields()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    if (isEdit.value) {
      loadDetail()
    } else {
      autoFillDocCodeOnCreate(form, 'bomCode')
    }
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)

// ========== 多级BOM树节点组件（递归） ==========
const BomTreeNode = defineComponent({
  name: 'BomTreeNode',
  props: {
    node: { type: Object, required: true },
    level: { type: Number, default: 1 }
  },
  setup(props) {
    return () => h('div', { class: 'tree-node' }, [
      h('span', { class: 'node-tag', style: { background: levelColor(props.level) } }, 'L' + props.level),
      h('span', { class: 'node-name' }, `${props.node.materialName} (${props.node.materialCode})`),
      h('span', { class: 'node-spec' }, props.node.specification || ''),
      h('span', { class: 'node-qty' }, '× ' + props.node.totalQuantity),
      // 半成品的BOM编号（该子件自身生效BOM入口）
      ...(props.node.childBomCode
        ? [h('span', { class: 'node-bomno' }, 'BOM: ' + props.node.childBomCode)]
        : []),
      ...(props.node.children && props.node.children.length
        ? [h('div', { class: 'tree-children' },
            props.node.children.map((c, i) => h(BomTreeNode, { node: c, level: props.level + 1, key: i }))
          )]
        : [])
    ])
  }
})

function levelColor(level) {
  const colors = ['#67C23A', '#409EFF', '#E6A23C', '#F56C6C', '#909399']
  return colors[level % colors.length]
}
</script>

<style scoped>
.bom-edit-page {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  margin-bottom: 14px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.header-left { display: flex; align-items: center; gap: 16px; flex: 0 0 auto; }
.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #606266;
  font-size: 13px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}
.back-btn:hover { color: #409EFF; background: #f0f7ff; }
.back-arrow { font-size: 16px; font-weight: 600; }
.title-group { display: flex; flex-direction: column; gap: 2px; }
.page-title { font-size: 18px; font-weight: 600; color: #303133; }
.page-sub { font-size: 12px; color: #909399; }
.page-sub .req { color: #f56c6c; }
/* 页头右栏「动作区」占满剩余宽度并右对齐、允许收缩：
   这样它的可用宽度由页面布局决定（与按钮多少无关），「放不下自动折叠」才能稳定判定溢出；
   否则动作区会随按钮增多把页头撑宽，永远测不出溢出。 */
.header-actions { display: flex; gap: 8px; align-items: center; flex: 1 1 auto; min-width: 0; justify-content: flex-end; }

/* 审计信息条 */
.audit-info-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 8px 16px;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: linear-gradient(135deg, #f6f9ff 0%, #f0f5ff 100%);
  border: 1px solid #e6eefa;
  border-radius: 6px;
  font-size: 12px;
}
.audit-item { display: flex; align-items: center; gap: 4px; }
.audit-label { color: #909399; flex-shrink: 0; }
.audit-value { color: #303133; font-weight: 500; }

.form-content { display: flex; flex-direction: column; gap: 14px; }
.form-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}
.title-bar {
  width: 4px;
  height: 14px;
  background: #409EFF;
  border-radius: 2px;
}
.title-text { font-size: 15px; font-weight: 600; color: #303133; }
.title-sub { font-size: 12px; color: #909399; margin-left: 8px; }
.card-actions { margin-bottom: 10px; display: flex; gap: 8px; }

.form-grid {
  display: grid;
  /* 标签在左、一行 3 个字段；!important 覆盖全局「按容器宽度自动排」规则 */
  grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
}
.form-grid :deep(.el-form-item) { margin-bottom: 12px; }
.form-grid :deep(.el-form-item__label) { font-size: 13px; color: #606266; height: auto; }
.form-grid .full-row { grid-column: 1 / -1; }
@media (max-width: 1100px) {
  .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; }
}
@media (max-width: 700px) {
  .form-grid { grid-template-columns: 1fr !important; }
}

/* 多级BOM树视图 */
.bom-tree-view {
  padding: 12px;
  background: #fafbfc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  font-size: 13px;
  border-bottom: 1px dashed #ebeef5;
}
.tree-node:last-child { border-bottom: none; }
.root-node { font-weight: 600; background: #f0f7ff; border-radius: 4px; }
.node-tag {
  display: inline-block;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: #409EFF;
  border-radius: 3px;
  min-width: 32px;
  text-align: center;
}
.node-name { color: #303133; font-weight: 500; }
.node-spec { color: #909399; font-size: 12px; }
.node-qty { color: #409EFF; font-weight: 600; margin-left: auto; }
.node-bomno {
  color: #E6A23C;
  font-size: 12px;
  font-family: monospace;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 4px;
  padding: 1px 6px;
  margin-left: 8px;
}
.child-bom-code {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #E6A23C;
  font-size: 12px;
  font-family: monospace;
}
.tree-children { margin-left: 24px; border-left: 2px solid #ebeef5; padding-left: 8px; margin-top: 4px; }

.bom-flatten-view { margin-top: 12px; }

/* 子件BOM展开样式 */
.child-bom-expand {
  padding: 12px 20px;
  background: #fafbfc;
  border-top: 1px solid #ebeef5;
}
.child-bom-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  .el-icon { color: #409EFF; }
}

/* 明细标题行右侧按钮区（2026-11-03：单标签 + 按钮右置，与全站单据明细同款） */
.title-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-actions { display: flex; gap: 8px; }

/* 成本合计行（2026-11-03）：各行成本金额相加 */
.detail-cost-summary {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid #ebeef5;
  background: #fafbfc;
  .label { color: #606266; font-size: 13px; }
  .value { color: #409eff; font-weight: 700; font-size: 14px; font-variant-numeric: tabular-nums; }
}
/* 成本金额单元格（逐行独立计算） */
.cost-amount-cell { color: #409eff; font-weight: 600; font-variant-numeric: tabular-nums; }
</style>


<style scoped>
/* 物料下拉选项内容（与其它单据页保持一致） */
.opt-code { color: #409EFF; font-weight: 500; }
.opt-name { margin-left: 8px; }
.opt-cf { margin-left: 8px; color: #909399; font-size: 11px; white-space: normal; word-break: break-word; }
.opt-model { float: right; color: #909399; font-size: 12px; }
</style>

<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
.material-select-popper {
  min-width: 640px;
  max-width: 90vw;
}
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
