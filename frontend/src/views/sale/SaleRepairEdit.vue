<template>
  <div class="sale-repair-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/sale/repairs')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '售后维修单' : '新增售后维修单' }}</span>
            <span v-if="form.repairNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.repairNo }}
            </span>
            <el-tag v-if="form.approvalStatus" size="small" :type="approvalStatusType(form.approvalStatus)" effect="plain" class="status-tag">{{ $dt(form.approvalStatus) }}</el-tag>
            <el-tag v-if="form.status && !canEditNow" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="form.repairId" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="SALE_REPAIR"
          :supported-actions="supportedActions"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.repairId"
          :show-copy="false"
          :extra-actions="extraActions"
          @save="onSave"
          @submit="onSubmit"
          @approve="onApprove"
          @reject="handleReject"
          @reverse-approve="handleReverseApprove"
          @void="handleVoid"
          @delete="handleDelete"
          @print="handlePrint"
        />
      </div>
    </div>

    <!-- 顶级页签：单据 / 来源单据 / 去向单据 -->
    <el-tabs v-model="topTab" class="bill-top-tabs">
      <el-tab-pane :label="$tf('单据')" name="doc">

    <!-- 基本信息卡片（可折叠） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.base }">
      <div class="card-header" @click="toggleCard('base')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.base }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("基本信息") }}</span>
        </div>
      </div>
      <div v-show="collapsed.base" class="card-body">
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="96px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('维修单号')" v-if="isFieldVisible('repairNo')">
                <el-input v-model="form.repairNo" :placeholder="$tt('保存后自动生成')" :disabled="!canEditNow" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('维修日期')" prop="repairDate" v-if="isFieldVisible('repairDate')" :required="isFieldRequired('repairDate')">
                <el-date-picker v-model="form.repairDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!canEditNow || !isFieldEditable('repairDate')" />
              </el-form-item>
              <el-form-item :label="$tf('来源单据类型')" v-if="isFieldVisible('sourceBillType')">
                <el-input v-model="form.sourceBillType" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('来源单据号')" v-if="isFieldVisible('sourceBillNo')">
                <el-input v-model="form.sourceBillNo" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('服务方式')" v-if="isFieldVisible('serviceType')" :required="isFieldRequired('serviceType')">
                <el-select v-model="form.serviceType" class="field-input" :disabled="!canEditNow || !isFieldEditable('serviceType')">
                  <el-option v-for="o in serviceTypeOptions" :key="o" :label="o" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('保修判定')" v-if="isFieldVisible('warrantyStatus')" :required="isFieldRequired('warrantyStatus')">
                <el-select v-model="form.warrantyStatus" class="field-input" :disabled="!canEditNow || !isFieldEditable('warrantyStatus')" @change="onWarrantyChange">
                  <el-option v-for="o in warrantyOptions" :key="o" :label="o" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('保修月数')" v-if="isFieldVisible('warrantyMonths')">
                <el-input :model-value="form.warrantyMonths == null ? '' : String(form.warrantyMonths)" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('保修判定依据')" class="span-two" v-if="isFieldVisible('warrantyBasis')">
                <el-input :model-value="form.warrantyBasis" readonly class="field-input" />
              </el-form-item>
            </div>
          </div>

          <!-- 客户与收件 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>客户与收件
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('客户')" prop="customerId" v-if="isFieldVisible('customerId')" :required="isFieldRequired('customerId')">
                <el-select v-model="form.customerId" filterable :placeholder="$tt('选择客户')" class="field-input" :disabled="!canEditNow || !isFieldEditable('customerId')" @change="onCustomerChange">
                  <el-option v-for="c in withFallbackOption(customers, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('联系人')" v-if="isFieldVisible('contactPerson')" :required="isFieldRequired('contactPerson')">
                <el-input v-model="form.contactPerson" class="field-input" :disabled="!canEditNow || !isFieldEditable('contactPerson')" />
              </el-form-item>
              <el-form-item :label="$tf('联系电话')" v-if="isFieldVisible('contactPhone')" :required="isFieldRequired('contactPhone')">
                <el-input v-model="form.contactPhone" class="field-input" :disabled="!canEditNow || !isFieldEditable('contactPhone')" />
              </el-form-item>
              <el-form-item :label="$tf('维修仓')" v-if="isFieldVisible('warehouseId')" :required="isFieldRequired('warehouseId')">
                <el-select v-model="form.warehouseId" filterable :placeholder="$tt('选择维修仓')" class="field-input" :disabled="!canEditNow || !isFieldEditable('warehouseId')">
                  <el-option v-for="w in warehouses" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('备件仓')" v-if="isFieldVisible('partsWarehouseId')" :required="isFieldRequired('partsWarehouseId')">
                <el-select v-model="form.partsWarehouseId" filterable :placeholder="$tt('选择备件仓')" class="field-input" :disabled="!canEditNow || !isFieldEditable('partsWarehouseId')">
                  <el-option v-for="w in warehouses" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('故障描述')" class="span-all" v-if="isFieldVisible('faultDescription')" :required="isFieldRequired('faultDescription')">
                <el-input v-model="form.faultDescription" type="textarea" :rows="2" class="field-input" :disabled="!canEditNow || !isFieldEditable('faultDescription')" :placeholder="$tt('记录客户报修的故障现象')" />
              </el-form-item>
            </div>
          </div>

          <!-- 费用与其他 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>费用与其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('维修费')" v-if="isFieldVisible('repairFee')" :required="isFieldRequired('repairFee')">
                <el-input-number v-model="form.repairFee" :precision="2" :min="0" :step="10" :disabled="!canEditNow || isInWarranty || !isFieldEditable('repairFee')" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('配件费')" v-if="isFieldVisible('partsFee')" :required="isFieldRequired('partsFee')">
                <el-input-number v-model="form.partsFee" :precision="2" :min="0" :step="10" :disabled="!canEditNow || isInWarranty || !isFieldEditable('partsFee')" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('合计费用')" v-if="isFieldVisible('totalFee')">
                <el-input :model-value="totalFeeText" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('维修结果')" v-if="isFieldVisible('repairResult')">
                <el-input :model-value="form.repairResult" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('备注')" class="span-all" v-if="isFieldVisible('remark')" :required="isFieldRequired('remark')">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!canEditNow || !isFieldEditable('remark')" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>

          <!-- 自定义字段（来自设置中心自定义字段定义，按所属分区成组渲染） -->
          <div class="info-section" v-for="section in headerCustomSections" :key="section.title" v-if="headerCustomSections.length > 0">
            <div class="section-title">
              <span class="section-bar"></span>{{ section.title }}
            </div>
            <div class="section-fields">
              <el-form-item
                v-for="field in section.fields"
                :key="field.prop"
                :label="$tf(field.label)"
                :required="field.required || isFieldRequired(field.prop)"
              >
                <el-select
                  v-if="field.type === 'select'"
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请选择'"
                  :filterable="field.filterable !== false"
                  class="field-input"
                  :disabled="!canEditNow"
                >
                  <el-option v-for="opt in (field.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                </el-select>
                <el-input-number
                  v-else-if="field.type === 'number'"
                  v-model="form[field.prop]"
                  :precision="field.precision"
                  :min="field.min !== undefined ? field.min : 0"
                  :step="0.1"
                  controls-position="right"
                  class="field-input"
                  :disabled="!canEditNow"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="form[field.prop]"
                  :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                  :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                  :disabled="!canEditNow"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.prop]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '选择日期'"
                  class="field-input"
                  :disabled="!canEditNow"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="form[field.prop]"
                  type="textarea"
                  :rows="field.rows || 3"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!canEditNow"
                />
                <el-input
                  v-else
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!canEditNow"
                />
              </el-form-item>
            </div>
          </div>

          <!-- 处理轨迹（有值才显示） -->
          <div class="info-section" v-if="hasTrace">
            <div class="section-title">
              <span class="section-bar"></span>处理轨迹
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('收件日期')" v-if="form.receiveDate">
                <el-input :model-value="form.receiveDate" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('收件数量')" v-if="Number(form.receivedQuantity || 0) > 0">
                <el-input :model-value="Number(form.receivedQuantity || 0).toFixed(2)" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('返还日期')" v-if="form.returnDate">
                <el-input :model-value="form.returnDate" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('返还数量')" v-if="Number(form.returnedQuantity || 0) > 0">
                <el-input :model-value="Number(form.returnedQuantity || 0).toFixed(2)" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('收件入库单')" v-if="form.receivedInboundNo">
                <el-link type="primary" :underline="false" @click="goToReceivedInbound">{{ form.receivedInboundNo }}</el-link>
              </el-form-item>
              <el-form-item :label="$tf('返还出库单')" v-if="form.returnedIssueNo">
                <el-link type="primary" :underline="false" @click="goToReturnedIssue">{{ form.returnedIssueNo }}</el-link>
              </el-form-item>
              <el-form-item :label="$tf('转出单据')" v-if="form.transferredBillNo">
                <el-link v-if="form.transferredBillId" type="primary" :underline="false" @click="goToTransferredBill">
                  {{ form.transferredBillType }} {{ form.transferredBillNo }}
                </el-link>
                <span v-else class="trace-text">{{ form.transferredBillType }} {{ form.transferredBillNo }}</span>
              </el-form-item>
              <el-form-item :label="$tf('收款单')" v-if="form.receiptNo">
                <el-link type="primary" :underline="false" @click="goToReceipt">{{ form.receiptNo }}</el-link>
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 维修明细卡片（通用组件 BillItemsTable） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SaleRepairEdit_items"
      title="维修明细"
      col-setting-title="维修明细列设置"
      :columns="itemColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!canEditNow"
      :total-qty="totalQtyText"
      :summary-items="itemSummaryItems"
      @row-change="calcTotalQty"
      @remove="calcTotalQty"
      @scan-material="onItemScanned"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" :disabled="!canEditNow" @click="itemPickerVisible = true">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" :disabled="!canEditNow" @click="addItem">
          <el-icon><Plus /></el-icon>添加维修产品
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="$tt('选择产品')"
          :disabled="!canEditNow"
          class="table-select"
          size="small"
          :teleported="true"
          :popper-options="{ strategy: 'fixed' }"
          popper-class="material-select-popper"
          @change="(v) => onItemMaterialChange(row, v)">
          <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode} ${m.materialName}`" :value="m.materialId">
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
          </el-option>
        </el-select>
      </template>
    </BillItemsTable>

    <!-- 配件领用卡片（通用组件 BillItemsTable 的第二个实例） -->
    <BillItemsTable
      v-model:collapsed="collapsed.parts"
      :items="form.parts"
      page-key="SaleRepairEdit_parts"
      title="配件领用"
      col-setting-title="配件领用列设置"
      :columns="partColumns"
      :custom-columns="partCustomColumns"
      :readonly="!canEditNow"
      :summary-items="partSummaryItems"
      @row-change="calcPartsAmount"
      @remove="calcPartsAmount"
      @scan-material="onPartScanned"
    >
      <template #actions>
        <el-button v-permission="perm('pick')" size="small" :disabled="!canEditNow" @click="partPickerVisible = true">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" :disabled="!canEditNow" @click="addPart">
          <el-icon><Plus /></el-icon>添加配件
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="$tt('选择配件')"
          :disabled="!canEditNow"
          class="table-select"
          size="small"
          :teleported="true"
          :popper-options="{ strategy: 'fixed' }"
          popper-class="material-select-popper"
          @change="(v) => onPartMaterialChange(row, v)">
          <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode} ${m.materialName}`" :value="m.materialId">
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
          </el-option>
        </el-select>
      </template>
    </BillItemsTable>

    <!-- 附件卡片（可折叠） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.attach }">
      <div class="card-header" @click="toggleCard('attach')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.attach }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("附件资料") }}</span>
        </div>
      </div>
      <div v-show="collapsed.attach" class="card-body">
        <FileUploader
          v-model="form.attachmentUrls"
          type="sale-repair"
          :disabled="!canEditNow"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SALE_REPAIR" :bill-id="form.repairId" :status="form.status" />

    <!-- 完成维修弹窗 -->
    <el-dialog v-model="completeDialog" :title="$tf('完成维修')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="$tf('维修结果')">
          <el-select v-model="completeResult" class="field-input" :placeholder="$tt('请选择维修结果')">
            <el-option v-for="o in repairResultOptions" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="completeDialog = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" :loading="actionLoading" @click="doComplete">{{ $tt("确定") }}</el-button>
      </template>
    </el-dialog>

    <!-- 转换新弹窗 -->
    <el-dialog v-model="transferDialog" :title="$tf('转换新')" width="720px">
      <div class="transfer-tip">为每行维修产品选择替换货品，确认后按来源销售订单生成销售换货单。</div>
      <el-table :data="transferRows" size="small" border class="transfer-table">
        <el-table-column label="维修产品" min-width="180">
          <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ Number(row.quantity || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="替换货品" min-width="260">
          <template #default="{ row }">
            <el-input :model-value="row.substituteMaterialName" readonly :placeholder="$tt('选择替换货品')" size="small">
              <template #append>
                <el-button @click="openTransferMaterialPick(row)">{{ $tt('选择') }}</el-button>
              </template>
            </el-input>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="transferDialog = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" :loading="actionLoading" @click="doTransferExchange">{{ $tt("确定") }}</el-button>
      </template>
    </el-dialog>

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <MaterialPickerDialog v-model="itemPickerVisible" :title="$tf('从物料库选择维修产品')" @confirm="onItemsPicked" />
    <MaterialPickerDialog v-model="partPickerVisible" :title="$tf('从物料库选择配件')" @confirm="onPartsPicked" />
    <MaterialPickerDialog v-model="transferPickerVisible" :title="$tf('从物料库选择替换货品')" @confirm="onTransferMaterialPicked" />

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
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="售后维修单" :bill-no="form.repairNo || ''" />

    <!-- 打印预览 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="SALE_REPAIR" :bill-id="form.repairId" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.repairId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SALE_REPAIR" :bill-id="form.repairId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.repairId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SALE_REPAIR" :bill-id="form.repairId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, h } from 'vue'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft, Document, Collection, ArrowRight, Memo, Plus, Grid } from '@element-plus/icons-vue'
import {
  getRepairDetail, getRepairNextNo, saveRepair, updateRepair, deleteRepair,
  submitRepair, approveRepair, rejectRepair, reverseApproveRepair, voidRepair,
  startRepair, completeRepair, receiveInRepair, issuePartsRepair, returnToCustomerRepair,
  transferRepairToExchange, transferRepairToReturn
} from '@/api/sale/saleRepair'
import { getSaleOrder } from '@/api/sale/saleOrder'
import { getSaleDelivery } from '@/api/sale/saleDelivery'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { withFallbackOption } from '@/utils/labelFallback'
import { isBillLinkRegistered } from '@/api/billLink'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  remapItemIdsAfterSave
} from '@/composables/useCustomField'
import FileUploader from '@/components/FileUploader.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import BillActions from '@/components/bill/BillActions.vue'
import { useBillActions } from '@/composables/useBillActions'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import { statusType, approvalStatusType } from '@/composables/useBillStatus'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'

const perm = usePagePerm('sale:repairs')

// 字段权限：按「设置中心 → 角色权限 → 字段权限」隐藏 / 只读 / 必填星号（与销售订单、销售退货同口径）
const { isFieldVisible, isFieldRequired, isFieldEditable } = useFieldPermission('sale_repair')
useFieldRequiredMark('sale_repair', '.sale-repair-edit')

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id && route.params.id !== 'new')

const STORAGE_KEY = 'sale-repair-edit-collapsed'
const collapsed = reactive({ base: true, items: true, parts: true, attach: false })
function loadCollapsed() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      Object.keys(parsed).forEach(k => { if (k in collapsed) collapsed[k] = parsed[k] })
    }
  } catch (e) {}
}
function saveCollapsed() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed)) } catch (e) {}
}
function toggleCard(key) {
  collapsed[key] = !collapsed[key]
  saveCollapsed()
}
watch(() => [collapsed.items, collapsed.parts, collapsed.attach], () => saveCollapsed())

const customers = ref([])
const materials = ref([])
const warehouses = ref([])

/** 物料库选择弹窗显隐（弹窗本体 = 通用组件 MaterialPickerDialog） */
const itemPickerVisible = ref(false)
const partPickerVisible = ref(false)
const transferPickerVisible = ref(false)

const changeRecordVisible = ref(false)
const printVisible = ref(false)
const actionLoading = ref(false)

const serviceTypeOptions = ['寄修', '上门', '返厂']
const warrantyOptions = ['保内', '保外', '待判定']
const repairResultOptions = ['已修复', '无法修复', '客户放弃']

const form = reactive({
  repairId: null,
  repairNo: '',
  repairDate: new Date().toISOString().slice(0, 10),
  customerId: null,
  customerName: '',
  contactPerson: '',
  contactPhone: '',
  sourceBillType: '',
  sourceBillNo: '',
  sourceId: null,
  serviceType: '寄修',
  warrantyStatus: '待判定',
  warrantyBasis: '',
  warrantyMonths: null,
  warehouseId: null,
  partsWarehouseId: null,
  faultDescription: '',
  receiveDate: null,
  returnDate: null,
  receivedQuantity: 0,
  returnedQuantity: 0,
  repairFee: 0,
  partsFee: 0,
  totalFee: 0,
  repairResult: '',
  transferredBillType: '',
  transferredBillNo: '',
  transferredBillId: null,
  receiptId: null,
  receiptNo: '',
  receivedInboundId: null,
  receivedInboundNo: '',
  returnedIssueId: null,
  returnedIssueNo: '',
  totalQuantity: 0,
  remark: '',
  attachmentUrls: '',
  status: '草稿',
  approvalStatus: '',
  closeStatus: 0,
  items: [],
  parts: []
})

/** 可编辑口径：草稿 / 已驳回（与后端保存校验一致） */
const canEditNow = computed(() => form.status === '草稿' || form.status === '已驳回')

// ===== 自定义字段（表头 sale_repair / 维修明细 sale_repair_item / 配件领用 sale_repair_part）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
const itemCustomColumns = ref([])
const partCustomFields = ref([])
const partCustomColumns = ref([])

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('sale_repair')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('sale_repair_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: canEditNow.value, listShowOnly: false })

  const partResult = await loadCustomFieldDefinitions('sale_repair_part')
  partCustomFields.value = Array.from(partResult.fieldMap.values())
  partCustomColumns.value = customFieldsToTableColumns(partCustomFields.value, { editable: canEditNow.value, listShowOnly: false })
}

// 可编辑状态变化时重新生成明细 / 配件自定义字段列（editable 控制）
watch(canEditNow, () => {
  if (itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: canEditNow.value, listShowOnly: false })
  }
  if (partCustomFields.value.length > 0) {
    partCustomColumns.value = customFieldsToTableColumns(partCustomFields.value, { editable: canEditNow.value, listShowOnly: false })
  }
})
const isInWarranty = computed(() => form.warrantyStatus === '保内')
const totalFeeText = computed(() => Number((Number(form.repairFee || 0) + Number(form.partsFee || 0)).toFixed(2)).toFixed(2))
const totalQtyText = computed(() => Number(form.items.reduce((s, r) => s + Number(r.quantity || 0), 0)).toFixed(2))

const rules = {
  repairDate: [{ required: true, message: tt('请选择维修日期'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }]
}

const itemColumns = [
  { prop: 'materialId', label: '产品', minWidth: 260 },
  { prop: 'serialNo', label: '序列号', width: 140, editor: 'text' },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 2 },
  { prop: 'faultDescription', label: '故障现象', minWidth: 180, editor: 'text' },
  { prop: 'repairPlan', label: '维修方案', minWidth: 180, editor: 'text' },
  { prop: 'laborHours', label: '工时', width: 90, editor: 'number', precision: 2 },
  { prop: 'warrantyStatus', label: '保修状态', width: 110, editor: 'select', clearable: false,
    options: warrantyOptions.map(o => ({ value: o, label: o })) },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' }
]

const partColumns = [
  { prop: 'materialId', label: '配件', minWidth: 260 },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 2 },
  { prop: 'warehouseId', label: '仓库', width: 160, editor: 'select', nameProp: 'stockName',
    optionsFn: () => warehouses.value },
  { prop: 'batchNo', label: '批次', width: 140, editor: 'text' },
  { prop: 'issuedQuantity', label: '已领用', width: 100, align: 'right', type: 'text' },
  { prop: 'remark', label: '备注', minWidth: 140, editor: 'text' }
]

const itemSummaryItems = computed(() => [
  { label: '维修费：', value: `¥${Number(form.repairFee || 0).toFixed(2)}` },
  { label: '配件费：', value: `¥${Number(form.partsFee || 0).toFixed(2)}` },
  { label: '合计费用：', value: `¥${totalFeeText.value}`, total: true }
])

const partSummaryItems = computed(() => [
  { label: '配件种类：', value: String((form.parts || []).length) },
  { label: '已领用合计：', value: Number((form.parts || []).reduce((s, p) => s + Number(p.issuedQuantity || 0), 0)).toFixed(2) }
])

const hasTrace = computed(() => !!(
  form.receiveDate || form.returnDate
  || Number(form.receivedQuantity || 0) > 0 || Number(form.returnedQuantity || 0) > 0
  || form.receivedInboundNo || form.returnedIssueNo || form.transferredBillNo || form.receiptNo
))

// ===== 业务动作显隐条件 =====
const canReceiveIn = computed(() => !!form.repairId
  && (form.status === '已受理' || form.status === '维修中')
  && !form.receivedInboundNo)
const canStart = computed(() => !!form.repairId && form.status === '已受理')
const canIssueParts = computed(() => !!form.repairId
  && form.status === '维修中'
  && (form.parts || []).some(p => Number(p.issuedQuantity || 0) < Number(p.quantity || 0)))
const canComplete = computed(() => !!form.repairId && form.status === '维修中')
const canReturn = computed(() => !!form.repairId
  && form.status === '维修完成'
  && Number(form.receivedQuantity || 0) > 0
  && !form.returnedIssueNo)
const canTransfer = computed(() => !!form.repairId
  && form.status === '维修完成'
  && form.repairResult !== '已修复'
  && !form.transferredBillNo)

const extraActions = computed(() => [
  { key: 'receiveIn', label: tt('收件入库'), type: 'primary', plain: true, perm: perm('receive'), show: canReceiveIn.value, handler: handleReceiveIn },
  { key: 'start', label: tt('开始维修'), type: 'primary', perm: perm('start'), show: canStart.value, handler: handleStart },
  { key: 'issueParts', label: tt('配件领用'), type: 'warning', plain: true, perm: perm('issueParts'), show: canIssueParts.value, handler: handleIssueParts },
  { key: 'complete', label: tt('完成维修'), type: 'success', perm: perm('complete'), show: canComplete.value, handler: openComplete },
  { key: 'returnCustomer', label: tt('返还客户'), type: 'success', plain: true, perm: perm('return'), show: canReturn.value, handler: handleReturn },
  { key: 'transferExchange', label: tt('转换新'), type: 'info', plain: true, perm: perm('transferExchange'), show: canTransfer.value, handler: openTransferExchange },
  { key: 'transferReturn', label: tt('转退货'), type: 'info', plain: true, perm: perm('transferReturn'), show: canTransfer.value, handler: handleTransferReturn }
])

function onCustomerChange(id) {
  const c = customers.value.find(x => x.customerId === id)
  if (c) {
    form.customerName = c.customerName
    if (c.contactPerson) form.contactPerson = c.contactPerson
    if (c.contactPhone) form.contactPhone = c.contactPhone
  }
}

/** 保内维修不收维修费/配件费：切到保内时金额归零并禁用 */
function onWarrantyChange(v) {
  if (v === '保内') {
    form.repairFee = 0
    form.partsFee = 0
  }
}

function onItemMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (!m) return
  row.materialCode = m.materialCode
  row.materialName = m.materialName
  row.specification = m.model
  row.unitId = m.baseUnitId || null
  row.unit = m.baseUnitName || ''
}

function onPartMaterialChange(row, id) {
  const m = materials.value.find(x => x.materialId === id)
  if (!m) return
  row.materialCode = m.materialCode
  row.materialName = m.materialName
  row.specification = m.model
  row.unitId = m.baseUnitId || null
  row.unit = m.baseUnitName || ''
  if (!row.warehouseId) row.warehouseId = form.partsWarehouseId
}

/** 物料库弹窗「确认添加」→ 维修产品行（弹窗本体 = 通用组件 MaterialPickerDialog） */
function onItemsPicked({ rows: picked }) {
  if (!picked || picked.length === 0) return
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      specification: m.model || '',
      unitId: m.baseUnitId || null,
      unit: m.baseUnitName || '',
      serialNo: '',
      quantity: 1,
      faultDescription: '',
      repairPlan: '',
      warrantyStatus: form.warrantyStatus,
      laborHours: 0,
      receivedQuantity: 0,
      returnedQuantity: 0,
      remark: ''
    }
    form.items.push(row)
  })
  calcTotalQty()
  itemPickerVisible.value = false
  ElMessage.success(`${tt('已添加 ')}${picked.length}${tt(' 条明细')}`)
}

/** 物料库弹窗「确认添加」→ 配件领用行（弹窗本体 = 通用组件 MaterialPickerDialog） */
function onPartsPicked({ rows: picked }) {
  if (!picked || picked.length === 0) return
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      specification: m.model || '',
      unitId: m.baseUnitId || null,
      unit: m.baseUnitName || '',
      quantity: 1,
      warehouseId: form.partsWarehouseId,
      batchNo: '',
      avgCost: 0,
      amount: 0,
      issuedQuantity: 0,
      remark: ''
    }
    form.parts.push(row)
  })
  calcPartsAmount()
  partPickerVisible.value = false
  ElMessage.success(`${tt('已添加 ')}${picked.length}${tt(' 条配件')}`)
}

/** 通用扫码加行（BillItemsTable 内置）→ 维修产品行补齐单位与保修状态 */
function onItemScanned(m, row) {
  if (!m || !row) return
  row.unitId = m.baseUnitId || null
  row.unit = m.baseUnitName || ''
  if (!row.warrantyStatus) row.warrantyStatus = form.warrantyStatus
}

/** 通用扫码加行（BillItemsTable 内置）→ 配件行补齐单位与备件仓 */
function onPartScanned(m, row) {
  if (!m || !row) return
  row.unitId = m.baseUnitId || null
  row.unit = m.baseUnitName || ''
  if (!row.warehouseId) row.warehouseId = form.partsWarehouseId
}

function addItem() {
  form.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unitId: null,
    unit: '',
    serialNo: '',
    quantity: 1,
    faultDescription: '',
    repairPlan: '',
    warrantyStatus: form.warrantyStatus,
    laborHours: 0,
    receivedQuantity: 0,
    returnedQuantity: 0,
    remark: ''
  })
}

function addPart() {
  form.parts.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unitId: null,
    unit: '',
    quantity: 1,
    warehouseId: form.partsWarehouseId,
    batchNo: '',
    avgCost: 0,
    amount: 0,
    issuedQuantity: 0,
    remark: ''
  })
}

function calcTotalQty() {
  form.totalQuantity = Number(form.items.reduce((s, r) => s + Number(r.quantity || 0), 0)).toFixed(2)
}

function calcPartsAmount() {
  (form.parts || []).forEach(p => {
    p.amount = Number((Number(p.quantity || 0) * Number(p.avgCost || 0)).toFixed(2))
  })
}

function goToReceivedInbound() {
  if (form.receivedInboundId) router.push(`/inventory/other-receipts/edit/${form.receivedInboundId}`)
}

function goToReturnedIssue() {
  if (form.returnedIssueId) router.push(`/inventory/other-issues/edit/${form.returnedIssueId}`)
}

function goToTransferredBill() {
  if (!form.transferredBillId) return
  if (form.transferredBillType === '销售换货单') router.push(`/sale/exchanges/${form.transferredBillId}`)
  else if (form.transferredBillType === '销售退货单') router.push(`/sale/returns/${form.transferredBillId}`)
}

function goToReceipt() {
  if (form.receiptId) router.push(`/finance/receipt/edit/${form.receiptId}`)
}

function flushNumbers() {
  ;(form.items || []).forEach(row => {
    row.quantity = Number(row.quantity || 0)
    row.laborHours = Number(row.laborHours || 0)
  })
  ;(form.parts || []).forEach(row => {
    row.quantity = Number(row.quantity || 0)
  })
  form.repairFee = Number(form.repairFee || 0)
  form.partsFee = Number(form.partsFee || 0)
}

async function onSave(silent, opts = {}) {
  flushNumbers()
  if (!canEditNow.value) {
    ElMessage.warning(`${tt('当前状态为「')}${form.status}${tt('」，只有草稿或已驳回状态可保存')}`)
    return false
  }
  if (!form.customerId) {
    if (!silent) ElMessage.warning(tt('请选择客户'))
    return false
  }
  const payload = {
    ...form,
    items: (form.items || []).filter(r => r && r.materialId),
    parts: (form.parts || []).filter(r => r && r.materialId)
  }
  if (!payload.items.length) {
    if (!silent) ElMessage.warning(tt('请至少添加一行维修产品'))
    return false
  }
  payload.totalQuantity = payload.items.reduce((s, r) => s + Number(r.quantity || 0), 0)
  payload.totalFee = Number(form.repairFee || 0) + Number(form.partsFee || 0)
  try {
    if (form.repairId) {
      await updateRepair(form.repairId, payload)
    } else {
      const res = await saveRepair(payload)
      if (res && res.repairId) form.repairId = res.repairId
      if (res && res.repairNo) form.repairNo = res.repairNo
      if (res && res.status) form.status = res.status
      if (res && res.approvalStatus) form.approvalStatus = res.approvalStatus
      if (!opts.skipRouteSync && !isEdit.value && form.repairId) {
        try { await router.replace({ name: 'SaleRepairEdit', params: { id: String(form.repairId) } }) } catch (_) {}
      }
    }
    // ===== 自定义字段随单据落库（表头 / 维修明细 / 配件领用）=====
    if (headerCustomFieldMap.size > 0 && form.repairId) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('sale_repair', form.repairId, headerValues, headerFormulaFields.value)
    }
    if ((itemCustomFields.value.length > 0 || partCustomFields.value.length > 0) && form.repairId) {
      // 保存主表时后端走「明细先删后插」，明细主键已变，必须先按新主键对齐再写明细自定义字段
      let fresh = null
      try {
        fresh = await getRepairDetail(form.repairId)
      } catch (e) {
        console.warn('[saleRepair] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      if (itemCustomFields.value.length > 0 && payload.items.length > 0) {
        remapItemIdsAfterSave(payload.items, (fresh && fresh.items) || [], 'repairItemId')
        const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
        for (const row of payload.items) {
          if (!row.repairItemId) continue
          const values = {}
          for (const key of itemKeys) {
            if (key in row) values[key] = row[key]
          }
          await saveCustomFieldValues('sale_repair_item', row.repairItemId, values)
        }
      }
      if (partCustomFields.value.length > 0 && payload.parts.length > 0) {
        remapItemIdsAfterSave(payload.parts, (fresh && fresh.parts) || [], 'partId')
        const partKeys = new Set(partCustomFields.value.map(f => f.fieldKey))
        for (const row of payload.parts) {
          if (!row.partId) continue
          const values = {}
          for (const key of partKeys) {
            if (key in row) values[key] = row[key]
          }
          await saveCustomFieldValues('sale_repair_part', row.partId, values)
        }
      }
    }
    triggerListRefresh('/sale/repairs')
    if (!silent) ElMessage.success(tt('保存成功'))
    await load()
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  }
}

async function onSubmit() {
  flushNumbers()
  const missing = await validateBillRequiredFields({ headerFormRef, rules, $el: document, pageKey: 'sale_repair', model: form })
  if (missing.length) {
    ElNotification.error({
      title: tt('请完善必填字段后再提交'),
      message: h('div', { style: { fontSize: '13px', lineHeight: '1.6' } }, [
        h('div', { style: { marginBottom: '6px', fontWeight: 600, color: '#f56c6c' } }, tt('共 ') + missing.length + tt(' 个字段未填写：')),
        h('ul', { style: { margin: 0, paddingLeft: '18px', color: '#606266' } },
          missing.map(m => h('li', { style: { marginBottom: '2px' } }, m.label || m.prop || tt('未命名字段')))
        )
      ]),
      duration: 5000,
      offset: 60
    })
    return
  }
  if (!form.items.length) {
    ElMessage.warning(tt('请至少保留一行维修产品'))
    return
  }
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.repairId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'SALE_REPAIR', billLabel: '售后维修单' })
    await submitRepair(form.repairId)
    ElMessage.success(submitSuccessMessage(needApproval))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    if (!isEdit.value && form.repairId) {
      try { await router.replace({ name: 'SaleRepairEdit', params: { id: String(form.repairId) } }) } catch (_) {}
    }
  }
}

function handlePrint() {
  if (!form.repairId) {
    ElMessage.warning(tt('请先保存维修单再打印'))
    return
  }
  printVisible.value = true
}

// ===== 维修过程动作 =====
async function confirmThen(message, fn, successText) {
  if (!form.repairId) { ElMessage.warning(tt('请先保存维修单')); return }
  actionLoading.value = true
  try {
    await ElMessageBox.confirm(message, tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
    await fn()
    ElMessage.success(successText)
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('操作失败'))
  } finally {
    actionLoading.value = false
  }
}

function handleReceiveIn() {
  confirmThen(tt('确认收件入库？将生成其他入库单草稿'), () => receiveInRepair(form.repairId), tt('已生成收件入库单草稿'))
}

function handleStart() {
  confirmThen(tt('确认开始维修？'), () => startRepair(form.repairId), tt('已开始维修'))
}

function handleIssueParts() {
  confirmThen(tt('确认领用配件？将生成其他出库单草稿'), () => issuePartsRepair(form.repairId), tt('已生成配件领用出库单草稿'))
}

const completeDialog = ref(false)
const completeResult = ref('已修复')
function openComplete() {
  completeResult.value = '已修复'
  completeDialog.value = true
}
async function doComplete() {
  if (!completeResult.value) { ElMessage.warning(tt('请选择维修结果')); return }
  actionLoading.value = true
  try {
    await completeRepair(form.repairId, completeResult.value)
    ElMessage.success(tt('已完成维修'))
    completeDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('完成维修失败'))
  } finally {
    actionLoading.value = false
  }
}

function handleReturn() {
  confirmThen(tt('确认返还客户？将生成其他出库单草稿'), () => returnToCustomerRepair(form.repairId), tt('已生成返还出库单草稿'))
}

// ===== 转换新 / 转退货 =====
const transferDialog = ref(false)
const transferRows = ref([])
/** 正在为哪一行选替换货品（物料库弹窗为通用多选，本场景取第一行 = 单选口径） */
const transferPickRow = ref(null)

function openTransferMaterialPick(row) {
  transferPickRow.value = row
  transferPickerVisible.value = true
}

function onTransferMaterialPicked({ rows: picked }) {
  if (!picked || picked.length === 0 || !transferPickRow.value) return
  const m = picked[0]
  transferPickRow.value.substituteMaterialId = m.materialId
  transferPickRow.value.substituteMaterialCode = m.materialCode || ''
  transferPickRow.value.substituteMaterialName = m.materialName || ''
  transferPickerVisible.value = false
}

function openTransferExchange() {
  if (!String(form.sourceBillType || '').includes('销售订单')) {
    ElMessage.warning(tt('转换新只支持来源为销售订单的维修单'))
    return
  }
  transferRows.value = (form.items || []).map(it => ({
    repairItemId: it.repairItemId,
    materialCode: it.materialCode,
    materialName: it.materialName,
    quantity: it.quantity,
    substituteMaterialId: null,
    substituteMaterialCode: '',
    substituteMaterialName: ''
  }))
  transferDialog.value = true
}
async function doTransferExchange() {
  if (!transferRows.value.length) { ElMessage.warning(tt('没有可转换的维修产品')); return }
  const lacks = transferRows.value.filter(r => !r.substituteMaterialId)
  if (lacks.length) {
    ElMessage.warning(tt('请为每行维修产品选择替换货品'))
    return
  }
  actionLoading.value = true
  try {
    await transferRepairToExchange(form.repairId, transferRows.value.map(r => ({
      repairItemId: r.repairItemId,
      substituteMaterialId: r.substituteMaterialId
    })))
    ElMessage.success(tt('已生成销售换货单草稿'))
    transferDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || tt('转换新失败'))
  } finally {
    actionLoading.value = false
  }
}

function handleTransferReturn() {
  confirmThen(tt('确认转退货？将生成销售退货单草稿'), () => transferRepairToReturn(form.repairId), tt('已生成销售退货单草稿'))
}

// ===== 加载 =====
async function load() {
  const detailId = route.params.id && route.params.id !== 'new' ? route.params.id : (form.repairId || '')
  if (!detailId) return
  try {
    const res = await getRepairDetail(detailId)
    Object.assign(form, res)
    if (!form.items) form.items = []
    if (!form.parts) form.parts = []
    if (headerCustomFieldMap.size > 0 && res.repairId) {
      const headerValues = await loadCustomFieldValues('sale_repair', res.repairId)
      Object.assign(form, headerValues)
    }
    if (itemCustomFields.value.length > 0 && form.items.length > 0) {
      const itemIds = form.items.map(it => it.repairItemId).filter(Boolean)
      if (itemIds.length > 0) {
        const itemValuesMap = await batchLoadCustomFieldValues('sale_repair_item', itemIds)
        form.items.forEach(row => {
          const values = itemValuesMap[row.repairItemId]
          if (values) Object.assign(row, values)
        })
      }
    }
    if (partCustomFields.value.length > 0 && form.parts.length > 0) {
      const partIds = form.parts.map(it => it.partId).filter(Boolean)
      if (partIds.length > 0) {
        const partValuesMap = await batchLoadCustomFieldValues('sale_repair_part', partIds)
        form.parts.forEach(row => {
          const values = partValuesMap[row.partId]
          if (values) Object.assign(row, values)
        })
      }
    }
    form.repairFee = Number(form.repairFee || 0)
    form.partsFee = Number(form.partsFee || 0)
    calcTotalQty()
    calcPartsAmount()
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  }
}

// ===== 统一单据操作（审核/驳回/重置草稿/作废/删除）=====
const {
  supportedActions, rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove, handleVoid, handleDelete
} = useBillActions({
  form,
  getId: () => form.repairId,
  api: {
    approve: approveRepair,
    reject: rejectRepair,
    reverseApprove: reverseApproveRepair,
    void: voidRepair,
    delete: deleteRepair
  },
  reload: load,
  router,
  listPath: '/sale/repairs',
  billLabel: '售后维修单'
})

async function loadBaseData() {
  try {
    const [custRes, matRes, whRes] = await Promise.all([
      listAllCustomer(),
      listAllMaterial(),
      listAllWarehouse()
    ])
    customers.value = custRes || []
    materials.value = matRes || []
    warehouses.value = whRes || []
  } catch (e) {
    console.warn('下拉数据加载失败:', e)
  }
}

/** 从销售订单/销售出库单「售后报修」带入来源与客户；来源单取不到时留空由用户填写 */
async function applyQuerySource() {
  const q = route.query || {}
  if (q.sourceType) form.sourceBillType = String(q.sourceType)
  if (q.sourceBillNo) form.sourceBillNo = String(q.sourceBillNo)
  if (q.sourceId) form.sourceId = Number(q.sourceId)
  if (q.customerId) form.customerId = Number(q.customerId)
  if (q.customerName) form.customerName = String(q.customerName)
  if (!form.sourceId) return
  try {
    let src = null
    if (String(form.sourceBillType).includes('销售出库')) src = await getSaleDelivery(form.sourceId)
    else if (String(form.sourceBillType).includes('销售订单')) src = await getSaleOrder(form.sourceId)
    if (!src) return
    if (!form.customerId && src.customerId) form.customerId = src.customerId
    if (src.customerName) form.customerName = src.customerName
    if (src.contactPerson) form.contactPerson = src.contactPerson
    if (src.contactPhone) form.contactPhone = src.contactPhone
    const srcItems = src.items || []
    if (!form.items.length && srcItems.length) {
      form.items = srcItems.map(it => ({
        materialId: it.materialId,
        materialCode: it.materialCode,
        materialName: it.materialName,
        specification: it.specification,
        unitId: it.unitId,
        unit: it.unit,
        serialNo: it.serialNo || '',
        quantity: Number(it.quantity || 0),
        faultDescription: '',
        repairPlan: '',
        warrantyStatus: form.warrantyStatus,
        laborHours: 0,
        receivedQuantity: 0,
        returnedQuantity: 0,
        remark: ''
      }))
      calcTotalQty()
    }
  } catch (e) {
    // 来源单取不到时不阻断开单
  }
}

async function initNewBill() {
  await applyQuerySource()
  try {
    const no = await getRepairNextNo()
    if (no && !form.repairNo) form.repairNo = no
  } catch (e) {
    // 单号取不到时保存由后端自动生成
  }
}

// ===== 顶级页签（单据 / 来源单据 / 去向单据）=====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  loadCollapsed()
  await loadCustomFields()
  isBillLinkRegistered('SALE_REPAIR')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  await loadBaseData()
  if (isEdit.value) {
    await load()
  } else {
    await initNewBill()
  }
})

useBaseDataRefresh(loadBaseData)
</script>

<style scoped lang="scss">
.sale-repair-edit {
  padding: 12px 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
  font-size: 12px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

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
    &:hover {
      color: #409eff;
      border-color: #b3d8ff;
      background: #ecf5ff;
    }
  }

  .title-area .title-row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .title-icon { color: #409eff; font-size: 16px; }

  .page-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

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

  .status-tag {
    transform: scale(0.88);
    transform-origin: left center;
  }

  .header-right { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
}

.collapsible-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;

  &.collapsed {
    .card-header { border-bottom: none; }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 14px;
    background: #fafbfc;
    border-bottom: 1px solid #e4e7ed;
    cursor: pointer;
    user-select: none;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .collapse-icon {
      color: #909399;
      font-size: 12px;
      transition: transform .25s ease;
      &.active {
        transform: rotate(90deg);
        color: #409eff;
      }
    }

    .card-title {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
    }
  }

  .card-body { padding: 12px 14px; }
}

.base-form {
  .info-section {
    margin-bottom: 10px;
    padding-bottom: 10px;
    border-bottom: 1px dashed #ebeef5;

    &:last-child {
      margin-bottom: 0;
      padding-bottom: 0;
      border-bottom: none;
    }
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

  .section-fields {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0 12px;
  }

  :deep(.el-form-item) { margin-bottom: 6px; }
  :deep(.el-form-item__label) { font-size: 12px; color: #606266; line-height: 28px; }
  :deep(.el-form-item__content) { line-height: 28px; }

  .span-all { grid-column: span 4; }
  .span-two { grid-column: span 2; }
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
  :deep(.el-input-number) {
    width: 100%;
    .el-input__wrapper { min-height: 28px; padding: 0 6px; }
  }
  :deep(.el-date-editor) { width: 100%; }
}

.trace-text { color: #303133; font-size: 12px; }

.table-select { width: 100%; }
.opt-code { color: #909399; margin-right: 6px; }
.opt-name { color: #303133; }

.transfer-tip {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.transfer-table { width: 100%; }
</style>
