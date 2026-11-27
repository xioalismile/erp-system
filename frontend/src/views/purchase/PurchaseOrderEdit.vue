<template>
  <div class="purchase-order-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/purchase/orders')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '采购订单' : '新增采购订单' }}</span>
            <span v-if="form.orderNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.orderNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
            <el-tag v-if="form.closeStatus === 1" size="small" type="success" effect="plain">{{ $tt("已结案") }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <PushDownButton v-if="BillStatus.canPushDown(form.status, form.closeStatus)" source-type="PurchaseOrder" :source-doc="form" />
        <BillActions bill-type="PURCHASE_ORDER"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.orderId && !form.id"
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

    <!-- 顶级页签：单据 / 来源单据 / 去向单据（来源去向为通用组件，未登记的单据类型不显示） -->
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
        <el-form ref="headerFormRef" :model="form" :rules="rules" label-width="70px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('订单编号')">
                <el-input v-model="form.orderNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('订单日期')" prop="orderDate" required>
                <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('交货日期')">
                <el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 供应商信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>供应商信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('供应商')" prop="supplierId" required>
                <el-select v-model="form.supplierId" filterable :placeholder="$tt('选择供应商')" class="field-input" :disabled="!isDraft" @change="onSupplierChange">
                  <el-option v-for="s in withFallbackOption(suppliers, 'supplierId', 'supplierName', form)" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('供应商编号')">
                <el-input v-model="form.supplierCode" :disabled="!isDraft" readonly class="field-input"  />
              </el-form-item>
              <el-form-item :label="$tf('联系人')" v-if="isFieldVisible('contactPerson')">
                <el-input v-model="form.contactPerson" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('联系电话')" v-if="isFieldVisible('contactPhone')">
                <el-input v-model="form.contactPhone" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('收货地址')" class="span-all" v-if="isFieldVisible('deliveryPlace')">
                <el-input v-model="form.deliveryPlace" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 交易信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>交易信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('采购员')" prop="buyerName" required>
                <el-select v-model="buyerSelectedId" filterable :placeholder="$tt('选择采购员')" class="field-input" :disabled="!isDraft" @change="(id) => { const e = employeeOptions.find(o => o.employeeId === id); form.buyerName = e ? e.employeeName : ''; form.buyerId = e ? e.employeeId : null }">
                  <el-option v-for="e in employeeOptions" :key="e.employeeId" :label="e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '')" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('币种')" prop="currencyId" required>
                <el-select v-model="form.currencyId" filterable :placeholder="$tt('选择币种')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item v-if="taxEnabled" :label="$tf('计税方式')">
                <el-select v-model="form.taxCalcMethod" class="field-input" :disabled="!isDraft" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('付款条件')">
                <PaymentTermSelect v-model="form.paymentTermId" :disabled="!isDraft" @change="onPaymentTermChange" />
              </el-form-item>
              <el-form-item :label="$tf('结算方式')">
                <el-select v-model="form.settleModeId" filterable :placeholder="$tt('选择结算方式')" class="field-input" clearable :disabled="!isDraft" @change="onSettleChange">
                  <el-option v-for="s in settleTypes" :key="s.settleModeId" :label="s.settleModeName" :value="s.settleModeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('整单折扣')">
                <el-input-number v-model="form.discountRate" :precision="2" :step="1" :min="0" :max="100" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <template v-if="headerCustomSections.length > 0">
            <div class="info-section" v-for="section in headerCustomSections" :key="section.title">
            <div class="section-title">
              <span class="section-bar"></span>{{ section.title }}
            </div>
            <div class="section-fields">
              <el-form-item
                v-for="field in section.fields"
                :key="field.prop"
                :label="$tf(field.label)"
                :required="field.required"
              >
                <el-select
                  v-if="field.type === 'select'"
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请选择'"
                  :filterable="field.filterable !== false"
                  class="field-input"
                  :disabled="!isDraft"
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
                  :disabled="!isDraft"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="form[field.prop]"
                  :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                  :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                  :disabled="!isDraft"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.prop]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '选择日期'"
                  class="field-input"
                  :disabled="!isDraft"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="form[field.prop]"
                  type="textarea"
                  :rows="field.rows || 3"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!isDraft"
                />
                <el-input
                  v-else
                  v-model="form[field.prop]"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!isDraft"
                />
              </el-form-item>
            </div>
          </div>
          </template>

          <!-- 备注 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/自定义字段列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="PurchaseOrderEdit_items"
      title="采购明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      :columns="detailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isDraft"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      :source-link-click="goToSourceBill"
      @row-change="onRowPriceChange"
      @remove="calcTotal"
    >
      <template #actions>
        <SourcePicker target-type="PurchaseOrder" @pick="onSourcePicked" :disabled="!isDraft" btn-type="success" size="small" />
        <el-button v-permission="perm('pick')" size="small" @click="openSaleOrderDialog" :disabled="!isDraft">
          <el-icon><Link /></el-icon>从销售订单导入
        </el-button>
        <el-button v-permission="perm('pick')" size="small" @click="pickerVisible = true" :disabled="!isDraft">
          <el-icon><Grid /></el-icon>从物料库选择
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!isDraft">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
      </template>
      <template #cell-materialId="{ row }">
        <el-select
          v-model="row.materialId"
          filterable
          :placeholder="materialSelectPlaceholder"
          @change="(v) => onMaterialChange(row, v)"
          :disabled="!isDraft"
          :filter-method="(val) => materialFilterMethod(val, row)"
          class="table-select"
          size="small"
          :teleported="true"
          :popper-options="{ strategy: 'fixed' }"
          popper-class="material-select-popper">
          <el-option
            v-for="m in row._filteredMaterials || materials"
            :key="m.materialId"
            :label="`${m.materialCode} ${m.materialName}`"
            :value="m.materialId"
          >
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
            <span v-if="m.model" class="opt-model">{{ m.model }}</span>
          </el-option>
        </el-select>
      </template>
      <template #cell-unit="{ row }">
        <UnitPickerCell
          v-model="row.unitId"
          :units="units"
          :material="materialOf(row)"
          :disabled="!isDraft"
          @change="calcRow(row)"
        />
      </template>
      <template #cell-stockId="{ row }">
        <el-select v-model="row.stockId" filterable size="small" :placeholder="$tt('请选择仓库')" style="width:100%" :disabled="!isDraft">
          <el-option v-for="w in withFallbackOption(warehouseOptions, 'stockId', 'warehouseName', row)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
        </el-select>
      </template>
      <template #cell-taxType="{ row }">
        <el-select v-model="row.taxType" style="width:100%" size="small" @change="calcRow(row)" :disabled="!isDraft">
          <el-option :label="'价内税'" :value="1" />
          <el-option :label="'价外税'" :value="2" />
          <el-option :label="'不计税'" :value="3" />
        </el-select>
      </template>
      <template #cell-batchNo="{ row }">
        <el-input v-model="row.batchNo" size="small" :placeholder="row._isLotControl ? '必填' : ''" :disabled="!isDraft" />
        <span v-if="row._isLotControl" style="color:#f56c6c;margin-left:2px;">*</span>
      </template>
      <template #cell-productionDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" :disabled="!isDraft" @change="(v) => onProductionDateChange(row, v)" />
        <span v-else class="thumb-placeholder">-</span>
      </template>
      <template #cell-expiryDate="{ row }">
        <el-date-picker v-if="row._isExpiryControl" v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" disabled />
        <span v-else class="thumb-placeholder">-</span>
      </template>
      <template #summary-extra>
        <!-- 本位币折算（非人民币单据才显示） -->
        <div v-if="form.currencyCode && form.currencyCode !== 'CNY'" class="summary-block">
          <div class="block-title">{{ $tt("本位币折算") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("折前金额：") }}</span>
            <span class="value">¥{{ Number(form.baseTotalAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("税额：") }}</span>
            <span class="value">¥{{ Number(form.baseTaxAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line total">
            <span class="label">{{ $tt("价税合计：") }}</span>
            <span class="value">¥{{ Number(form.baseNetAmount || 0).toFixed(2) }}</span>
          </div>
        </div>
        <!-- 预付信息 -->
        <div class="summary-block">
          <div class="block-title">{{ $tt("预付信息") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("预付比例%：") }}</span>
            <span class="value">
              <el-input-number v-model="form.prepaidRate" :precision="2" :step="1" :min="0" :max="100" size="small" :disabled="!isDraft" @change="onPrepaidRateChange" />
            </span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("预付金额：") }}</span>
            <span class="value">
              <el-input-number v-model="form.prepaymentAmount" :precision="2" :step="1" :min="0" size="small" :disabled="!isDraft" />
            </span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("付款账户：") }}</span>
            <span class="value">
              <el-select v-model="form.accountId" :placeholder="$tt('选择付款账户')" clearable filterable size="small" style="width:150px;" :disabled="!isDraft" @change="onPickPayAccount">
                <el-option v-for="a in accountOptions" :key="a.accountId" :label="a.accountName" :value="a.accountId" />
              </el-select>
            </span>
          </div>
        </div>
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
          type="purchase-order"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PURCHASE_ORDER" :bill-id="form.orderId" :status="form.status" />

    <!-- 入库弹窗 -->
    <el-dialog v-model="inboundDialogVisible" :title="$tf('采购入库')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('入库仓库')">
          <el-select v-model="inboundWarehouse" filterable :placeholder="$tt('请选择仓库')" style="width:100%">
            <el-option v-for="w in warehouseOptions" :key="w.stockId" :label="w.stockName" :value="w.stockName" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert type="success" :closable="false" style="margin:10px 0">{{ $tt("入库将自动增加库存") }}</el-alert>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="inboundDialogVisible=false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('confirm')" type="primary" @click="confirmInbound">{{ $tt("确认入库") }}</el-button>
      </template>
    </el-dialog>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" :title="$tf('取消订单')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('取消原因')">
          <el-input v-model="cancelReason" type="textarea" :rows="3" :placeholder="$tt('请输入取消原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="cancelDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="danger" @click="doCancel">{{ $tt("确认取消") }}</el-button>
      </template>
    </el-dialog>

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <MaterialPickerDialog v-model="pickerVisible" @confirm="onMaterialPicked" />

    <!-- 从销售订单导入弹窗 -->
    <el-dialog v-model="saleOrderDialog" :title="$tf('从销售订单导入')" width="1000px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" style="margin-bottom:12px;">
        仅可选择已生效的销售订单。确认导入后将把销售订单明细追加到当前采购明细末尾，自动建立来源单据关联（sourceBillType/sourceId/sourceItemId）。
      </el-alert>
      <div class="picker-toolbar">
        <el-input v-model="saleOrderKeyword" :placeholder="$tt('订单号/客户简称')" clearable style="width:260px;" @keyup.enter="searchSaleOrders" />
        <el-button v-permission="perm('query')" type="primary" @click="searchSaleOrders">{{ $tt("搜索") }}</el-button>
        <el-button v-permission="perm('reset')" @click="saleOrderKeyword=''; searchSaleOrders()">{{ $tt("重置") }}</el-button>
      </div>
      <el-table
        :data="saleOrderList"
        height="380"
        border
        highlight-current-row
        @current-change="onSaleOrderCurrentChange"
        v-loading="saleOrderLoading"
      >
        <el-table-column label="" width="50" align="center">
          <template #default="{ row }">
            <el-radio :model-value="saleOrderSelected?.orderId" :value="row.orderId" @click.prevent="onSaleOrderCurrentChange(row)"><span></span></el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" :label="$tf('订单号')" width="180" />
        <el-table-column prop="orderDate" :label="$tf('订单日期')" width="110" />
        <el-table-column prop="customerShortName" :label="$tf('客户')" min-width="140" />
        <el-table-column prop="salerName" :label="$tf('销售员')" width="100" />
        <el-table-column :label="$tf('订单金额')" width="120" align="right">
          <template #default="{ row }">{{ Number(row.netAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="approvalStatus" :label="$tf('状态')" width="90" />
      </el-table>
      <el-pagination
        v-model:current-page="saleOrderPage.current"
        v-model:page-size="saleOrderPage.size"
        :total="saleOrderPage.total"
        layout="total, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end;display:flex;"
        @current-change="searchSaleOrders"
      />
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="saleOrderDialog = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('import')" type="primary" :disabled="!saleOrderSelected" @click="confirmSaleOrderImport">{{ $tt("确认导入") }}</el-button>
      </template>
    </el-dialog>

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

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="PURCHASE_ORDER" :bill-id="form.orderId" />
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="采购订单" :bill-no="form.orderNo || ''" />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.orderId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PURCHASE_ORDER" :bill-id="form.orderId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.orderId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PURCHASE_ORDER" :bill-id="form.orderId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { calcTaxAmount } from '@/utils/billTax'
import { ref, onMounted, watch, reactive, computed, h} from 'vue'
import { applyPushSource } from '@/utils/pushSourceApplier'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useSysConfig } from '@/composables/useSysConfig'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Printer, Link, ArrowRight, Check, TopRight, ArrowDown, CircleCheck, CircleClose, RefreshLeft, Close, Share, Memo } from '@element-plus/icons-vue'
import api from '../../api'
import { listAllEmployee } from '@/api/basedata/employee'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllSettle } from '@/api/basedata/settle'
import { listAllBankAccount } from '@/api/basedata/bankAccount'
import FileUploader from '@/components/FileUploader.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import SourcePicker from '@/components/SourcePicker.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PushDownButton from '@/components/PushDownButton.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues,
  batchLoadCustomFieldValues,
  customFieldsToTableColumns,
  injectMaterialCustomFields,
  copyCustomFieldValues,
  remapItemIdsAfterSave,
} from '@/composables/useCustomField'
// 明细通用组件（卡片折叠/列设置/动态列/自定义字段列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
// 单据取价（2026-09-20 价格策略修复）：按设置中心「取价优先级」取价，选物料/改数量时回填单价
import { resolveUnitPrice, applyFetchedUnitPrice } from '@/composables/usePriceFetch'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useFieldPermission } from '@/composables/useFieldPermission'
import {
  savePurchaseOrder, updatePurchaseOrder, submitPurchaseOrder, approvePurchaseOrder,
  rejectPurchaseOrder, reverseApprovePurchaseOrder, closeOrderPurchaseOrder,
  reopenPurchaseOrder, voidPurchaseOrder, unvoidPurchaseOrder, deletePurchaseOrder
} from '@/api/purchase/purchaseOrder'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useCopyBill } from '@/composables/useCopyBill'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import { tt } from '@/i18n'
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('purchase:orders')

// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const { loadSysConfig, getConfig } = useSysConfig()

// ===== 计税方式（含税价 / 不含税价 / 不计税）：采购订单为行级口径，表头值仅作新增行默认值 =====
const taxMethodOptions = ['不含税价', '含税价', '不计税']

/** 历史值归一：价内税→含税价、价外税/空→不含税价；非法值兜底为不含税价 */
function normalizeTaxMethod(v) {
  if (v === '含税价' || v === '价内税') return '含税价'
  if (v === '不计税') return '不计税'
  return '不含税价'
}

/** 从设置中心取「默认计税方式」与两个总开关（含税 / 外币） */
async function initTaxSettings() {
  try {
    await ensureCommercialSwitches()
    await loadSysConfig()
  } catch (e) {
    // 读取失败保持默认
  }
  if (!taxEnabled.value) {
    form.value.taxCalcMethod = '不计税'
  } else if (!form.value.orderId) {
    form.value.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.value.taxCalcMethod = normalizeTaxMethod(form.value.taxCalcMethod)
  }
}

/** 行级口径：表头「计税方式」仅作为「新增明细行」的默认值，不覆盖用户逐行选择 */
function onTaxCalcMethodChange() {
  // 保留每行自己的计税方式（价内税/价外税/不计税）
}

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('PURCHASE_ORDER')

// 物料明细列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料/仓库/计税方式/批次号/生产日期/有效期至列由页面提供插槽，来源单号列由组件的 sourceLinkClick 回调跳转来源单
const detailColumns = [
  { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 110, type: 'text' },
  { prop: 'unit', label: '单位', width: 60, align: 'center', type: 'text' },
  { prop: 'stockId', label: '仓库', width: 120, editor: 'select', clearable: false,
    optionsFn: () => warehouseOptions.value, change: (row) => { row.binId = null } },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 4 },
  { prop: 'unitPrice', label: '单价', width: 90, editor: 'number', precision: 4 },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'taxRate', label: '税率%', width: 70, editor: 'number', precision: 0, min: 0, max: 100 },
  { prop: 'taxType', label: '计税方式', width: 100 },
  { prop: 'discountRate', label: '折扣率%', width: 80, editor: 'number', precision: 2, min: 0, max: 100 },
  { prop: 'discountAmount', label: '折扣额', width: 85, align: 'right', type: 'amount2', cellClass: 'discount-text' },
  { prop: 'amount', label: '金额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'taxAmount', label: '税额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'totalAmount', label: '价税合计', width: 95, align: 'right', type: 'amount2', cellClass: 'net-amount' },
  { prop: 'avgCost', label: '成本', width: 85, align: 'right', type: 'amount4' },
  { prop: 'batchNo', label: '批次号', width: 120 },
  { prop: 'productionDate', label: '生产日期', width: 130 },
  { prop: 'expiryDate', label: '有效期至', width: 130 },
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 160, type: 'sourceLink' },
  { prop: 'sourceLineNo', label: '来源行号', width: 80, align: 'center', type: 'text' }
]
// 汇总区（通用组件按此渲染，与销售/库存单据同一套口径）
const summaryItems = computed(() => {
  const items = [
    { label: '折前金额：', value: `¥${Number(form.value.totalAmount || 0).toFixed(2)}` },
    { label: '折扣金额：', value: `- ¥${Number(form.value.discountAmount || 0).toFixed(2)}`, class: 'discount' }
  ]
  // 含税业务关闭：不显示「合计税额」
  if (taxEnabled.value) {
    items.push({ label: '合计税额：', value: `¥${Number(form.value.taxAmount || 0).toFixed(2)}` })
  }
  items.push({ label: '价税合计：', value: `¥${Number(form.value.netAmount || 0).toFixed(2)}`, total: true })
  return items
})
useBillLineStock(() => form.value.items)
// ===== 物料库弹窗的列定义 / 列设置 / 图片渲染 已收进通用组件 MaterialPickerDialog =====

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const isDraft = computed(() => BillStatus.canEdit(form.value.status, form.value.closeStatus))

// 来源单据类型 -> 列表路由映射（用于明细行来源单号点击跳转）
const SOURCE_BILL_ROUTE_MAP = {
  '销售报价单': '/sale/quotes',
  '销售合同': '/sale/contracts',
  '销售订单': '/sale/orders',
  '销售出库单': '/sale/deliveries',
  '销售退货单': '/sale/returns',
  '销售发票': '/sale/invoices',
  '采购合同': '/purchase/contracts',
  '采购申请单': '/purchase/requests',
  '采购订单': '/purchase/orders',
  '采购入库单': '/purchase/inbounds',
  '采购退货单': '/purchase/returns',
  '采购发票': '/purchase/invoices',
  '生产工单': '/production/workorders',
  '生产入库单': '/production/receipts',
  '生产领料单': '/inventory/material-requisitions',

  '工序汇报单': '/production/operation-reports',
  '委外加工单': '/subcontract/orders',
  'MRP计划': '/mrp/plans',
  '打样单': '/sample/orders'
}

function goToSourceBill(row) {
  if (!row || !row.sourceBillNo) return
  const billType = row.sourceBillType || row.sourceType
  const routeBase = SOURCE_BILL_ROUTE_MAP[billType]
  if (routeBase) {
    router.push(`${routeBase}?keyword=${encodeURIComponent(row.sourceBillNo)}`)
  } else {
    ElMessage.warning(`${tt("暂不支持跳转到 ")}${billType || tt('该来源单据')}`)
  }
}

const STORAGE_KEY = 'purchase-order-edit-collapsed'

const collapsed = reactive({
  base: true,
  items: true,
  attach: false,
  audit: false,
})

const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])
const itemCustomFields = ref([])
// 物料自定义字段：行内下拉附加展示 + 物料库弹窗全字段（逻辑集中在 composable）
const mcf = useMaterialCustomFields()
const itemCustomColumns = ref([])
const materialCustomValueMap = ref({})

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('purchase_order')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields

  const itemResult = await loadCustomFieldDefinitions('purchase_order_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })

}

function loadCollapsed() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      Object.keys(parsed).forEach(k => {
        if (k in collapsed) collapsed[k] = parsed[k]
      })
    }
  } catch (e) {}
}

function saveCollapsed() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed))
  } catch (e) {}
}

function toggleCard(key) {
  collapsed[key] = !collapsed[key]
  saveCollapsed()
}

// 明细卡片折叠由通用组件 v-model 驱动，这里监听后持久化
watch(() => collapsed.items, () => saveCollapsed())

const suppliers = ref([])
const materials = ref([])
const inboundDialogVisible = ref(false)
const inboundWarehouse = ref('主仓库')
const cancelDialog = ref(false)
const cancelReason = ref('')
const printVisible = ref(false)
const changeRecordVisible = ref(false)

const pickerVisible = ref(false)
const units = ref([])
/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}

// 从销售订单导入相关状态
const saleOrderDialog = ref(false)
const saleOrderKeyword = ref('')
const saleOrderList = ref([])
const saleOrderSelected = ref(null)
const saleOrderLoading = ref(false)
const saleOrderPage = reactive({ current: 1, size: 20, total: 0 })

const form = ref({
  orderId: null,
  orderNo: '',
  orderDate: new Date().toISOString().slice(0,10),
  supplierId: null, supplierName:'', supplierCode:'', contactPerson:'', contactPhone:'', deliveryPlace:'',
  buyerId: null, buyerName:'', paymentMethod:'月结30天', deliveryDate:'',
  paymentTermId: null, paymentTermName: '',
  settleModeId: null, settleName: '',
  prepaidRate: 0, prepaidAmount: 0, prepaymentAmount: 0,
  accountId: null, accountName: '',
  discountRate: 0, discountAmount: 0,
  currencyId: null, currencyCode: 'CNY', exchangeRate: 1.0,
  baseTotalAmount: 0, baseTaxAmount: 0, baseNetAmount: 0,
  totalAmount:0, taxAmount:0, netAmount:0,
  taxCalcMethod:'不含税价',
  status:'草稿', inboundStatus:'未入库', sourceType:'MANUAL', sourceBillNo: '', sourceOrderNo: '',
  closeStatus:0, createTime:'', auditTime:'',
  remark:'', attachmentUrls:'', items:[]
})
const headerFormRef = ref()
const formRef = ref()

// 草稿态变化时重新生成明细自定义字段列（editable 控制），必须在 setup 顶层注册 watch
watch(isDraft, () => {
  if (itemCustomFields.value && itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false });
  }
});

const totalQty = computed(() => {
  return form.value.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)
})

const rules = {
  orderDate: [{ required: true, message: tt('请选择订单日期'), trigger: 'change' }],
  supplierId: [{ required: true, message: tt('请选择供应商'), trigger: 'change' }],
  buyerName: [{ required: true, message: tt('请选择采购员'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}

const employeeOptions = ref([])
const warehouseOptions = ref([])
const buyerSelectedId = ref(null)
const currencies = ref([])
const paymentTerms = ref([])
const settleTypes = ref([])
const accountOptions = ref([])
const onCurrencyChange = (id) => {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c) {
    form.value.currencyCode = c.currencyCode
    if (c.exchangeRate) form.value.exchangeRate = c.exchangeRate
  }
}

watch(() => form.value.buyerName, (name) => {
  if (!name) { buyerSelectedId.value = null; return }
  const e = employeeOptions.value.find(o => o.employeeName === name)
  buyerSelectedId.value = e ? e.employeeId : null
}, { immediate: true })

const statusType = (s) => ({'草稿':'info','待审核':'warning','已确认':'','已生效':'success','已入库':'warning','已完成':'success','已取消':'danger'}[s]||'info')

const onSupplierChange = (id) => {
  const s = suppliers.value.find(x => x.supplierId === id)
  if (s) {
    form.value.supplierName=s.supplierName
    form.value.supplierCode=s.supplierCode
    if (s.defaultBuyerId) {
      form.value.buyerId = s.defaultBuyerId
      const de = employeeOptions.value.find(o => o.employeeId === s.defaultBuyerId)
      if (de) form.value.buyerName = de.employeeName
      buyerSelectedId.value = s.defaultBuyerId
    }
    form.value.contactPerson=s.contactPerson||s.linkman||''
    form.value.contactPhone=s.contactPhone||s.phone||''
    form.value.deliveryPlace=s.deliveryAddress||s.address||''
    if (s.paymentMethod) form.value.paymentMethod = s.paymentMethod
    // 供应商默认税率带入明细行（2026-11-08：数值化比较，兼容输入框回传的字符串税率）
    const supTaxRate = Number(s.taxRate)
    if (supTaxRate > 0) {
      form.value.items.forEach(row => {
        const rowRate = Number(row.taxRate)
        if (!rowRate || rowRate === 13) {
          row.taxRate = supTaxRate
          calcRow(row)
        }
      })
    }
    if (s.paymentTermId) {
      form.value.paymentTermId = s.paymentTermId
      onPaymentTermChange(s.paymentTermId)
    }
    if (s.settleModeId) {
      form.value.settleModeId = s.settleModeId
      onSettleChange(s.settleModeId)
    }
    form.value.deliveryDate=new Date(Date.now()+((s.deliveryDays||s.leadTimeDays||7)*86400000)).toISOString().slice(0,10)
    calcTotal()
  }
}

// 付款条件联动：回填名称、计算现金折扣与预付金额
const onPaymentTermChange = (id) => {
  const p = (id && typeof id === 'object') ? id : paymentTerms.value.find(x => x.paymentTermId === id)
  if (!p) {
    form.value.paymentTermName = ''
    form.value.prepaidRate = 0
    form.value.prepaidAmount = 0
    return
  }
  form.value.paymentTermName = p.paymentTermName
  const baseAmount = Number(form.value.netAmount || form.value.totalAmount || 0)
  if (p.discountRate) {
    form.value.discountRate = p.discountRate
    form.value.discountAmount = (baseAmount * p.discountRate / 100).toFixed(2)
  }
  if (p.prepaidRate) {
    form.value.prepaidRate = p.prepaidRate
    form.value.prepaymentAmount = Number((baseAmount * p.prepaidRate / 100).toFixed(2))
  } else {
    form.value.prepaidRate = 0
    form.value.prepaymentAmount = 0
  }
}

// 预付比例手工调整：按价税合计自动重算预付金额（prepaymentAmount 为真正落库字段）
const onPrepaidRateChange = (val) => {
  const rate = Number(val || 0)
  const baseAmount = Number(form.value.netAmount || form.value.totalAmount || 0)
  form.value.prepaidRate = rate
  form.value.prepaymentAmount = rate > 0 ? Number((baseAmount * rate / 100).toFixed(2)) : form.value.prepaymentAmount
}

// 预付付款账户联动：选择账户后回写账户名称
function onPickPayAccount(id) {
  const a = accountOptions.value.find(x => x.accountId === id)
  form.value.accountId = id || null
  form.value.accountName = a ? a.accountName : ''
}

// 结算方式联动：回填名称并兼容原有 paymentMethod 字段
const onSettleChange = (id) => {
  const s = settleTypes.value.find(x => x.settleModeId === id)
  if (s) {
    form.value.settleName = s.settleModeName
    form.value.paymentMethod = s.settleModeName  // 兼容原有字段
  } else {
    form.value.settleName = ''
  }
}

const onMaterialChange = async (row, id) => {
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.materialMainImage = m.mainImage || ''
    row.specification = m.model || m.specification
    // Material 实体没有 unitName 字段，根据 unitId 从计量单位列表查找单位名称
    const unitId = m.purchaseUnitId || m.baseUnitId || null
    row.unitId = unitId
    if (unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    } else {
      row.unit = ''
    }
    // 2026-09-20 价格策略修复：单价按设置中心「取价优先级」取价（价格策略 → 物料采购价）。
    applyFetchedUnitPrice(row, {
      side: 'purchase',
      partnerId: form.value.supplierId,
      materialId: m.materialId,
      quantity: row.quantity
    }, m.purchasePrice || 0, (r) => calcRow(r))
    row.avgCost = m.avgCost || 0
    row.taxRate = m.taxRate || 13
    // Material 实体无默认仓库字段，明细仓库不预设（避免硬编码"主仓库"名称）
    row.stockId = null
    // 物料批次/保质期管理联动
    row._isLotControl = m.isLotControl || false
    row._isExpiryControl = m.isExpiryControl || false
    row._expiryDays = m.expiryDays || 0
    if (m.isExpiryControl && m.expiryDays) {
      if (!row.productionDate) {
        row.productionDate = new Date().toISOString().slice(0, 10)
      }
      const d = new Date(row.productionDate)
      d.setDate(d.getDate() + Number(m.expiryDays))
      row.expiryDate = d.toISOString().slice(0, 10)
    }
    if (m && m.materialId && itemCustomFields.value.length > 0) {
      if (!materialCustomValueMap.value[m.materialId]) {
        const map = await batchLoadCustomFieldValues('material', [m.materialId])
        Object.assign(materialCustomValueMap.value, map)
      }
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
    calcRow(row)
  }
}

const onProductionDateChange = (row, date) => {
  if (row._expiryDays && date) {
    const d = new Date(date)
    d.setDate(d.getDate() + Number(row._expiryDays))
    row.expiryDate = d.toISOString().slice(0, 10)
  }
}

const calcRow = (row) => {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  const taxRate = Number(row.taxRate || 0)
  const discountRate = Number(row.discountRate || 0)
  // 行级「计税方式」（taxType：1=价内税/含税价、3=不计税、其它=价外税/不含税价）决定「单价」口径；
  // 含税业务关闭时一律按不计税
  const raw = row.taxType
  const method = !taxEnabled.value ? '不计税'
    : (raw === 1 ? '含税价' : (raw === 3 ? '不计税' : '不含税价'))
  const factor = 1 + taxRate / 100
  const noTax = method === '不计税'
  const inclusive = method === '含税价'
  if (inclusive) {
    row.priceWithTax = price
    row.priceWithoutTax = factor ? Number((price / factor).toFixed(6)) : price
  } else if (noTax) {
    row.priceWithTax = price
    row.priceWithoutTax = price
  } else {
    row.priceWithoutTax = price
    row.priceWithTax = Number((price * factor).toFixed(6))
  }
  // 折前金额一律按「不含税」口径参与折扣与税额计算
  const originalAmount = inclusive ? (price * qty) / factor : price * qty
  row.discountAmount = (originalAmount * discountRate / 100).toFixed(2)
  row.amount = (originalAmount - Number(row.discountAmount)).toFixed(2)
  // 税额：不计税恒 0；含税价按「折后含税合计 − 折后不含税金额」倒算（统一口径，见 utils/billTax）
  row.taxAmount = calcTaxAmount({ amount: row.amount, unitPrice: price, quantity: qty, taxRate, discountRate, inclusive, noTax }).toFixed(2)
  row.netAmount = (Number(row.amount) + Number(row.taxAmount)).toFixed(2)
  // 兼容本页历史列名：行「价税合计」列绑定的是 totalAmount
  row.totalAmount = row.netAmount
  calcTotal()
}

/**
 * 行内单元格变更（BillItemsTable 的 @row-change）
 * 2026-09-20 价格策略修复：数量变了 → 若该行单价仍是"上次自动带出的价"（即用户没手改过），
 * 按新数量重新取价（价格策略支持数量区间档位）。用户手改过的行不覆盖其输入。
 */
function onRowPriceChange(row, prop) {
  calcRow(row)
  if (prop === 'quantity') scheduleRowReprice(row)
}

const _repriceTimers = new Map()
function scheduleRowReprice(row) {
  if (!row || !row.materialId) return
  const key = row.itemId || row.invoiceItemId || row.returnItemId || row.orderItemId || row.materialId
  if (_repriceTimers.has(key)) clearTimeout(_repriceTimers.get(key))
  _repriceTimers.set(key, setTimeout(async () => {
    _repriceTimers.delete(key)
    if (!row.materialId) return
    // 用户已手改单价 → 不覆盖
    if (row._autoUnitPrice !== undefined && Number(row.unitPrice) !== Number(row._autoUnitPrice)) return
    const priced = await resolveUnitPrice({
      side: 'purchase',
      partnerId: form.value.supplierId,
      materialId: row.materialId,
      quantity: row.quantity
    })
    if (!priced) return
    row.unitPrice = priced.price
    row._autoUnitPrice = priced.price
    calcRow(row)
  }, 400))
}

const calcTotal = () => {
  let totalAmount = 0
  let discountAmount = 0
  let taxAmount = 0
  form.value.items.forEach(i => {
    const amt = Number(i.amount || 0)
    const disc = Number(i.discountAmount || 0)
    totalAmount += amt + disc
    discountAmount += disc
    taxAmount += Number(i.taxAmount || 0)
  })
  form.value.totalAmount = totalAmount.toFixed(2)
  form.value.discountAmount = discountAmount.toFixed(2)
  form.value.taxAmount = taxAmount.toFixed(2)
  form.value.netAmount = (totalAmount - discountAmount + taxAmount).toFixed(2)
  // 本位币金额换算：原币金额 × 汇率
  const rate = Number(form.value.exchangeRate || 1)
  form.value.baseTotalAmount = (totalAmount * rate).toFixed(2)
  form.value.baseTaxAmount = (taxAmount * rate).toFixed(2)
  form.value.baseNetAmount = (Number(form.value.netAmount) * rate).toFixed(2)
}

const addItem = () => { form.value.items.push({ quantity:1, unitPrice:0, priceWithTax:0, priceWithoutTax:0, taxRate:13, taxType: (form.value.taxCalcMethod === '含税价' ? 1 : (form.value.taxCalcMethod === '不计税' ? 3 : 2)), stockId:null, discountRate:0, discountAmount:0, _isLotControl:false, _isExpiryControl:false, _expiryDays:0, productionDate:'', expiryDate:'', batchNo:'' }) }

// 选单回调 - 从源单据带入表头和明细
function onSourcePicked(sourceData, items) {
  if (sourceData) {
    Object.keys(sourceData).forEach(key => {
      if (key in form.value && key !== 'items' && key !== 'orderId' && key !== 'orderNo') {
        form.value[key] = sourceData[key]
      }
    })
  }
  if (items && items.length > 0) {
    // 2026-11-23：选单带入追加到现有明细末尾，不再覆盖原有明细
    const newRows = items.map(item => {
      const row = { ...item, taxType: 2, stockId: null, discountRate: item.discountRate || 0, discountAmount: 0, _isLotControl: false, _isExpiryControl: false, _expiryDays: 0, productionDate: '', expiryDate: '', batchNo: '' }
      calcRow(row)
      return row
    })
    form.value.items = [...(form.value.items || []), ...newRows]
  }
  calcTotal()
}

/** 参与「物料」下拉搜索的物料自定义字段：勾了「下拉显示」（受定制总开关控制）或勾了「可搜索」的字段 */
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))
))

/** 物料下拉的背景提示文字：声明可搜索范围；没有可搜索的自定义字段时原样返回「选择物料」（零回归） */
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/**
 * 明细行「物料」下拉的输入过滤（用户口径：不加额外搜索框，统一用下拉本身）
 * 过滤范围 = 物料编码/名称/规格 ＋ 自定义字段值 ＋ 自定义字段名，详见 utils/materialFilter.js
 */
const materialFilterMethod = (val, row) => {
  if (!val) {
    row._filteredMaterials = null
    return
  }
  // 过滤用「已预取全量值」的 mcf.valueMap（本页 materialCustomValueMap 只含已选中的物料，会导致搜不到）
  row._filteredMaterials = filterMaterials(materials.value, val, mcf.valueMap.value, materialSearchableFields.value)
}

/**
 * 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * - 组件内已批量取好选中物料的「全部自定义字段值」，并随 confirm 回传 valueMap
 * - 这里把值并入本页值表（与行内下拉取值共用同一份，不再二次请求），再逐行组装明细行
 */
async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(materialCustomValueMap.value, valueMap)
  }
  if (itemCustomFields.value.length > 0) {
    const matIds = picked.map(m => m.materialId).filter(id => !materialCustomValueMap.value[id])
    if (matIds.length > 0) {
      const map = await batchLoadCustomFieldValues('material', matIds)
      Object.assign(materialCustomValueMap.value, map)
    }
  }
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      specification: m.model || '',
      unit: '',
      stockId: null,
      quantity: 1,
      unitPrice: Number(m.purchasePrice || 0),
      priceWithTax: 0,
      priceWithoutTax: 0,
      avgCost: m.avgCost || 0,
      taxRate: Number(m.taxRate || 13),
      taxType: (form.value.taxCalcMethod === '含税价' ? 1 : (form.value.taxCalcMethod === '不计税' ? 3 : 2)),
      discountRate: 0,
      discountAmount: 0,
      amount: Number(m.purchasePrice || 0).toFixed(2)
    }
    const unitId = m.purchaseUnitId || m.baseUnitId || null
    if (unitId) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    }
    if (itemCustomFields.value.length > 0) {
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value)
    }
    form.value.items.push(row)
    applyFetchedUnitPrice(row, { side: 'purchase', partnerId: form.value.supplierId, materialId: m.materialId, quantity: row.quantity }, m.purchasePrice || 0, (r) => calcRow(r))
  })
  calcTotal()
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
}

// ===== 从销售订单导入 ====
const openSaleOrderDialog = () => {
  saleOrderSelected.value = null
  saleOrderKeyword.value = ''
  saleOrderPage.current = 1
  saleOrderDialog.value = true
  searchSaleOrders()
}

const searchSaleOrders = async () => {
  saleOrderLoading.value = true
  try {
    const res = await api.get('/sale/orders', {
      params: {
        current: saleOrderPage.current,
        size: saleOrderPage.size,
        status: '已生效',
        keyword: saleOrderKeyword.value || undefined
      }
    })
    saleOrderList.value = res?.records || []
    saleOrderPage.total = res?.total || 0
  } catch (e) {
    ElMessage.error(tt('销售订单列表加载失败'))
  } finally {
    saleOrderLoading.value = false
  }
}

const onSaleOrderCurrentChange = (row) => {
  if (row) saleOrderSelected.value = row
}

const confirmSaleOrderImport = async () => {
  if (!saleOrderSelected.value) {
    ElMessage.warning(tt('请先选择一个销售订单'))
    return
  }
  const so = saleOrderSelected.value
  try {
    // 拉取销售订单详情（含 items 明细）
    const detail = await api.get(`/sale/orders/${so.orderId}`)
    if (!detail || !detail.items || detail.items.length === 0) {
      ElMessage.warning(tt('该销售订单没有明细，无法导入'))
      return
    }
    // 2026-11-23：导入的销售订单明细追加到当前采购明细末尾，不再清空原有明细
    const newItems = detail.items.map(it => ({
      materialId: it.materialId,
      materialCode: it.materialCode || '',
      materialName: it.materialName || '',
      specification: it.specification || '',
      unit: it.unit || '',
      stockId: null,
      quantity: Number(it.quantity || 0),
      unitPrice: Number(it.unitPrice || 0),
      taxRate: Number(it.taxRate || 13),
      taxType: it.taxType || 2,
      amount: Number(it.amount || 0).toFixed(2),
      taxAmount: Number(it.taxAmount || 0).toFixed(2),
      totalAmount: (Number(it.amount || 0) + Number(it.taxAmount || 0)).toFixed(2),
      // 来源单据关联字段
      sourceBillType: '销售订单',
      sourceBillNo: detail.orderNo,
      sourceLineNo: it.lineNo,
      sourceId: detail.orderId,
      sourceItemId: it.orderItemId,
      sourceType: 'SALE_ORDER'
    }))
    form.value.items = [...(form.value.items || []), ...newItems]
    // 设置采购订单顶层来源标识
    form.value.sourceType = 'SALE_ORDER'
    form.value.sourceOrderNo = detail.orderNo
    form.value.sourceBillNo = detail.orderNo
    // 回填物料图片
    if (materials.value && materials.value.length > 0) {
      newItems.forEach(row => {
        if (!row.materialMainImage && row.materialId) {
          const m = materials.value.find(x => x.materialId === row.materialId)
          row.materialMainImage = m?.mainImage || ''
        }
      })
    }
    // 带入销售订单自定义字段值（按 fieldKey 匹配，仅复制采购订单定义中存在的字段）
    if (headerCustomFieldMap.size > 0 && detail.orderId) {
      const sourceHeaderValues = await loadCustomFieldValues('sale_order', detail.orderId)
      copyCustomFieldValues(sourceHeaderValues, form.value, Array.from(headerCustomFieldMap.values()))
    }
    if (itemCustomFields.value.length > 0 && detail.items && detail.items.length > 0) {
      const sourceItemIds = detail.items.map(it => it.itemId || it.orderItemId).filter(Boolean)
      if (sourceItemIds.length > 0) {
        const sourceItemValuesMap = await batchLoadCustomFieldValues('sale_order_item', sourceItemIds)
        newItems.forEach((row, idx) => {
          const sourceItemId = detail.items[idx].itemId || detail.items[idx].orderItemId
          const sourceValues = sourceItemValuesMap[sourceItemId]
          if (sourceValues) copyCustomFieldValues(sourceValues, row, itemCustomFields.value)
        })
      }
    }
    calcTotal()
    saleOrderDialog.value = false
    ElMessage.success(`${tt("已从销售订单 ")}${detail.orderNo}${tt(" 导入 ")}${newItems.length}${tt(" 条明细")}`)
  } catch (e) {
    ElMessage.error(tt('销售订单详情加载失败: ') + (e.message || ''))
  }
}

const doApprove = async () => { try { await api.post(`/purchase/orders/${route.params.id}/approve`); ElMessage.success(tt('审核成功')); load() } catch(e) {} }
const doInbound = () => { inboundDialogVisible.value = true }
const confirmInbound = async () => { try { await api.post(`/purchase/orders/${route.params.id}/inbound`, { warehouse: inboundWarehouse.value }); ElMessage.success(tt('入库成功')); inboundDialogVisible.value=false; load() } catch(e) {} }
const doCancel = async () => { try { await api.post(`/purchase/orders/${route.params.id}/cancel`, { reason: cancelReason.value }); ElMessage.success(tt('已取消')); cancelDialog.value=false; load() } catch(e) {} }
const doCloseOrder = async () => { try { await ElMessageBox.confirm(tt('确认结案? 结案后可反结案'), tt('提示'), { confirmButtonText:tt('确定'), cancelButtonText:tt('取消'), type:'warning' }); await api.post(`/purchase/orders/${route.params.id}/close-order`); ElMessage.success(tt('结案成功')); load() } catch(e) { if(e !== 'cancel') ElMessage.error(e.message || tt('结案失败')) } }
const doReopenOrder = async () => { try { await ElMessageBox.confirm(tt('确认反结案?'), tt('提示'), { confirmButtonText:tt('确定'), cancelButtonText:tt('取消'), type:'warning' }); await api.post(`/purchase/orders/${route.params.id}/reopen`); ElMessage.success(tt('反结案成功')); load() } catch(e) { if(e !== 'cancel') ElMessage.error(e.message || tt('反结案失败')) } }
function handlePrint() {
  if (!form.value.orderId) { ElMessage.warning(tt('请先保存订单再打印')); return }
  printVisible.value = true
}

const load = async () => {
  if (route.params.id) {
    try {
      const data = await api.get(`/purchase/orders/${route.params.id}`)
      if (data) form.value = data
      if (headerCustomFieldMap.size > 0 && form.value.orderId) {
        const headerValues = await loadCustomFieldValues('purchase_order', form.value.orderId)
        Object.assign(form.value, headerValues)
      }
      // 结算方式名称回填：后端只落库 settle_mode_id，名称从结算方式下拉列表反查，保证只读/详情区能显示名称
      if (form.value.settleModeId != null) {
        const st = settleTypes.value.find(x => x.settleModeId === form.value.settleModeId)
        if (st) form.value.settleName = st.settleModeName
      }
      // 回填物料图片（加载已有单据时后端不返回图片字段，从物料列表回填）
      if (form.value.items && materials.value && materials.value.length > 0) {
        form.value.items.forEach(row => {
          if (!row.materialMainImage && row.materialId) {
            const m = materials.value.find(x => x.materialId === row.materialId)
            row.materialMainImage = m?.mainImage || ''
          }
        })
      }
      if (itemCustomFields.value.length > 0 && form.value.items && form.value.items.length > 0) {
        const itemIds = form.value.items.map(it => it.itemId || it.orderItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('purchase_order_item', itemIds)
          form.value.items.forEach(row => {
            const itemId = row.itemId || row.orderItemId
            const values = itemValuesMap[itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
      // 计税方式：老单据按设置中心默认/总开关归一，再按口径回算明细两列
      await initTaxSettings()
      ;(form.value.items || []).forEach(row => calcRow(row))
      calcTotal()
    } catch(e) {}
  } else {
    // 新建时默认当前用户为采购员（可修改；buyerName存姓名，buyerSelectedId为对应员工ID）
    const me = await getCurrentEmployee()
    if (me && !form.value.buyerName) {
      form.value.buyerName = me.employeeName
      form.value.buyerId = me.employeeId
      buyerSelectedId.value = me.employeeId
    }
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
  }
}

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const results = await Promise.allSettled([
        api.get('/basedata/supplier/list'),
        api.get('/basedata/material/list'),
        listAllEmployee(),
        listAllWarehouse(),
        listAllUnit(),
        listAllCurrency(),
        listAllPaymentTerm(),
        listAllSettle(),
        listAllBankAccount()
      ])
      const [supRes, matRes, empRes, whRes, unitRes, curRes, ptRes, stRes, accRes] = results.map(r => r.status === 'fulfilled' ? r.value : [])
      results.forEach((r, i) => {
        if (r.status === 'rejected') console.warn('[PurchaseOrderEdit] 接口失败 index=' + i, r.reason)
      })
      suppliers.value = supRes || []
      materials.value = matRes || []
      // 行内下拉附加字段：物料到位后按批预取（不阻塞渲染）
      mcf.preloadAll(materials.value)
      employeeOptions.value = empRes || []
      warehouseOptions.value = whRes || []
      units.value = unitRes || []
      currencies.value = curRes || []
      paymentTerms.value = ptRes || []
      settleTypes.value = stRes || []
      accountOptions.value = accRes || []
      // 设置默认币种（currency_id是NOT NULL字段）
      if (!form.value.currencyId && currencies.value.length > 0) {
        // 外币业务关闭且为新增单据：币种默认本位币、汇率锁定为 1（编辑单据保留原币，仅供查看）
        if (!foreignCurrencyEnabled.value && !route.params.id && !form.value.orderId && !form.value.id) {
          const list = currencies.value || []
          const base = list.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || list[0]
          if (base) {
            form.value.currencyId = base.currencyId
            form.value.currencyCode = base.currencyCode
            form.value.exchangeRate = 1
          }
        } else {
          form.value.currencyId = currencies.value[0].currencyId
          onCurrencyChange(form.value.currencyId)
        }
      }
    } catch(e) {
      console.error('[PurchaseOrderEdit] 加载基础数据失败:', e)
    }
}

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: (id) => api.get(`/purchase/orders/${id}`),
  form,
  afterCopy: () => { form.value.items.forEach(row => calcRow(row)) },
  noField: 'orderNo',
  customField: { entity: 'purchase_order', itemEntity: 'purchase_order_item', itemIdField: 'orderItemId' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  // 全站商业口径总开关：取设置中心「启用含税业务 / 启用外币业务」最新值
  await ensureCommercialSwitches()
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PURCHASE_ORDER')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  loadCollapsed()

  try {
    await loadCustomFields()
  } catch(e) {
    console.warn('[PurchaseOrderEdit] 加载自定义字段失败:', e)
  }

  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await load()
    // 检测是否为下推进入 (路由 query.srcType/srcId), 自动填充源单数据
    const applied = await applyPushSource(route, 'PurchaseOrder', form.value, (items, sourceData) => {
      form.value.items = items
      if (sourceData && headerCustomFieldMap.size > 0) {
        copyCustomFieldValues(sourceData, form.value, Array.from(headerCustomFieldMap.values()))
      }
      if (itemCustomFields.value.length > 0 && items && items.length > 0) {
        form.value.items.forEach((row, idx) => {
          if (items[idx]) {
            copyCustomFieldValues(items[idx], row, itemCustomFields.value)
          }
        })
      }
      form.value.items.forEach(row => calcRow(row))
    })
    if (applied) ElMessage.info(tt('已从源单带入数据, 请确认后保存'))
  }
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'orderNo')
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)

// 字段权限（设置中心-字段权限设置，page_key=purchase_order）：被角色关闭的字段在表单中隐藏
const { isFieldVisible } = useFieldPermission('purchase_order')


  // ======== 状态流转：通用占位 handler（样板：与 SaleOrderEdit 对齐） ========
  // 采购订单 API 映射（已替换 TODO_* 占位）
  const saveBill = (form) => savePurchaseOrder(form)
  const updateBill = (id, form) => updatePurchaseOrder(id, form)
  const submitBill = (id) => submitPurchaseOrder(id)
  const approveBill = (id) => approvePurchaseOrder(id)
  const rejectBill = (id, reason) => rejectPurchaseOrder(id, reason)
  const reverseApproveBill = (id) => reverseApprovePurchaseOrder(id)
  const closeBill = (id) => closeOrderPurchaseOrder(id)
  const reopenBill = (id) => reopenPurchaseOrder(id)
  const voidBill = (id, reason) => voidPurchaseOrder(id, reason)
  const unvoidBill = (id) => unvoidPurchaseOrder(id)
  const deleteBill = (id) => deletePurchaseOrder(id)

  // 通用：取单据ID（覆盖各模块常见字段，兜底 form.id）
  const billId = computed(() => {
    return form.value.orderId || form.value.deliveryId || form.value.inboundId || form.value.invoiceId || form.value.contractId
        || form.value.voucherId || form.value.requisitionId || form.value.stockCheckId || form.value.transferId
        || form.value.bomId || form.value.sampleId || form.value.freeSampleId || form.value.workOrderId || form.value.billId || form.value.id
  })

  // 弹窗与 reason
  const rejectDialog = ref(false)
  const rejectReason = ref('')
  const voidDialog = ref(false)
  const voidReason = ref('')

  function flushInputNumbers() {
  if (!form.value.items || form.value.items.length === 0) return
  form.value.items.forEach(row => {
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    if (row.unitPrice !== undefined) row.unitPrice = Number(row.unitPrice || 0)
    if (row.priceWithTax !== undefined) row.priceWithTax = Number(row.priceWithTax || 0)
    if (row.priceWithoutTax !== undefined) row.priceWithoutTax = Number(row.priceWithoutTax || 0)
    if (row.taxRate !== undefined) row.taxRate = Number(row.taxRate || 0)
    if (row.discountRate !== undefined) row.discountRate = Number(row.discountRate || 0)
    if (row.discountAmount !== undefined) row.discountAmount = Number(row.discountAmount || 0)
    if (row.amount !== undefined) row.amount = Number(row.amount || 0)
    if (row.taxAmount !== undefined) row.taxAmount = Number(row.taxAmount || 0)
    if (row.netAmount !== undefined) row.netAmount = Number(row.netAmount || 0)
  })
  form.value.items = [...form.value.items]
}

  async function onSave(silent, opts = {}) {
    flushInputNumbers()
    if (form.value.status && !isDraft.value) { ElMessage.warning(`${tt("当前状态为「")}${form.value.status}${tt("」, 不可修改")}`); return false }
    // 按当前口径把明细含税价/不含税价/金额/税额/价税合计刷成最新
    form.value.items.forEach(row => calcRow(row))
    if (typeof calcTotal === 'function') calcTotal()
    try {
      if (billId.value) {
        await updateBill(billId.value, form.value)
      } else {
        const res = await saveBill(form.value)
        if (res) {
          const idKeys = ['orderId','deliveryId','inboundId','invoiceId','contractId','voucherId','requisitionId','stockCheckId','transferId','bomId','sampleId','freeSampleId','workOrderId','billId','id']
          for (const k of idKeys) if (res[k] != null && form.value[k] == null) form.value[k] = res[k]
          const noKeys = ['orderNo','deliveryNo','inboundNo','invoiceNo','contractNo','voucherNo','requisitionNo','stockCheckNo','transferNo','sampleNo','billNo']
          for (const k of noKeys) if (res[k] != null) form.value[k] = res[k]
          if (res.status) form.value.status = res.status
          if (res.approvalStatus) form.value.approvalStatus = res.approvalStatus
          // 新增态保存后必须把新单 id 写回地址栏：否则地址栏仍停在 /new，用户按 F5 会丢掉刚保存的草稿。
          // skipRouteSync：提交链路里先跳过（写回会改变布局 keep-alive key(fullPath) → 组件实例重建），由 onSubmit 的 finally 统一写回。
          if (!opts.skipRouteSync && !route.params.id && billId.value) {
            try { await router.replace({ name: 'PurchaseOrderEdit', params: { id: String(billId.value) } }) } catch (_) {}
          }
        }
      }
      // 自定义字段保存（表头 + 明细）
      const savedId = form.value.orderId
      if (headerCustomFieldMap.size > 0 && savedId) {
        const headerValues = {}
        for (const key of headerCustomFieldMap.keys()) {
          if (key in form.value) headerValues[key] = form.value[key]
        }
        await saveCustomFieldValues('purchase_order', savedId, headerValues, headerFormulaFields.value)
      }
      if (itemCustomFields.value.length > 0 && form.value.items && form.value.items.length > 0) {
        // 关键修复（2026-09-22，同销售订单 bug）：保存主表时后端"明细先删后插"，明细主键会变 →
        // 必须先按保存后的新主键重新对齐，否则明细级自定义字段会写到已删除的旧行上（报表永远空白）。
        try {
          // 注意：本页**没有** import getPurchaseOrder（它走通用 api 客户端），
          // 必须用与本页 load() 完全一致的取详情方式，否则会抛 ReferenceError 被 catch 吞掉、重映射静默失效。
          const fresh = await api.get(`/purchase/orders/${savedId}`)
          remapItemIdsAfterSave(form.value.items, (fresh && fresh.items) || [], 'orderItemId')
        } catch (e) {
          console.warn('[purchaseOrder] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
        }
        const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
        for (let i = 0; i < form.value.items.length; i++) {
          const row = form.value.items[i]
          if (!row.itemId && !row.orderItemId) continue
          const itemId = row.itemId || row.orderItemId
          const values = {}
          for (const key of itemKeys) {
            if (key in row) values[key] = row[key]
          }
          await saveCustomFieldValues('purchase_order_item', itemId, values)
        }
      }
      triggerListRefresh('/purchase/orders')
    if (!silent) ElMessage.success(tt('保存成功'))
      await load()
      return true
    } catch (e) { ElMessage.error(e.message || tt('保存失败')); return false }
  }

  async function onSubmit() {
    flushInputNumbers()
    // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
    // skipRouteSync：提交链路里先跳过写回地址栏，避免 keep-alive key 变化重建组件实例（见本函数 finally）
    const saved = await onSave(true, { skipRouteSync: true })
    if (!saved || !billId.value) return
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, formRef, rules, $el: document, pageKey: 'purchase_order', model: form })
  if (missing.length) {
    ElNotification.error({
      title: tt('请完善必填字段后再提交'),
      message: h('div', { style: { fontSize: '13px', lineHeight: '1.6' } }, [
        h('div', { style: { marginBottom: '6px', fontWeight: 600, color: '#f56c6c' } }, tt('共 ') + missing.length + tt(' 个字段未填写：')),
        h('ul', { style: { margin: 0, paddingLeft: '18px', color: '#606266' } },
          missing.map(m => h('li', { style: { marginBottom: '2px' } }, [
            h('span', { style: { color: '#303133', fontWeight: 600 } }, m.label || m.prop || tt('未命名字段')),
            m.message && m.message !== tt('该字段不能为空') ? h('span', { style: { color: '#909399', marginLeft: '4px' } }, '（' + m.message + '）') : null
          ].filter(Boolean)))
        )
      ]),
      duration: 5000,
      offset: 60
    })
    // 自动滚动到第一个错误字段（headerFormRef 优先）
    try {
      const target = (headerFormRef && headerFormRef.value) ? headerFormRef.value : (formRef && formRef.value ? formRef.value : null)
      if (target && typeof target.scrollToField === 'function') target.scrollToField(missing[0].prop)
    } catch(_) {}
    return
  }

    try {
      const needApproval = await confirmSubmit({ billType: 'PURCHASE_ORDER', billLabel: '采购订单' })
      await submitBill(billId.value)
      ElMessage.success(submitSuccessMessage(needApproval))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
    } finally {
      // 新建单据（URL=/new）提交（或用户取消）后再跳到编辑URL：此时实例重建无副作用，新实例挂载时会按 id 重新拉取最新状态
      if (!route.params.id && billId.value) {
        try { await router.replace({ name: 'PurchaseOrderEdit', params: { id: String(billId.value) } }) } catch (_) {}
      }
    }
  }

  async function onApprove() {
    if (!billId.value) { ElMessage.warning(tt('请先保存单据')); return }
    try {
      await ElMessageBox.confirm(tt('确认审核？审核后将不可直接修改'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await approveBill(billId.value)
      ElMessage.success(tt('审核成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败')) }
  }

  function handleReject() { rejectReason.value = ''; rejectDialog.value = true }
  async function doReject() {
    try {
      await rejectBill(billId.value, rejectReason.value)
      ElMessage.success(tt('已驳回')); rejectDialog.value = false
      await load()
    } catch (e) { ElMessage.error(e.message || tt('驳回失败')) }
  }

  async function handleReverseApprove() {
    try {
      await ElMessageBox.confirm(tt('确认重置草稿？重置草稿后单据回到草稿态可修改，需下游未生效'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await reverseApproveBill(billId.value)
      ElMessage.success(tt('重置草稿成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败')) }
  }

  async function handleClose() {
    try {
      await ElMessageBox.confirm(tt('确认结案？结案后进入完结态不可修改'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await closeBill(billId.value)
      ElMessage.success(tt('已结案'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('结案失败')) }
  }

  async function handleReopen() {
    try {
      await ElMessageBox.confirm(tt('确认反结案？反结案后返回已生效态'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await reopenBill(billId.value)
      ElMessage.success(tt('反结案成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('反结案失败')) }
  }

  function handleVoid() { voidReason.value = ''; voidDialog.value = true }
  async function doVoid() {
    try {
      await voidBill(billId.value, voidReason.value)
      ElMessage.success(tt('已作废')); voidDialog.value = false
      await load()
    } catch (e) { ElMessage.error(e.message || tt('作废失败')) }
  }

  async function handleUnvoid() {
    try {
      await ElMessageBox.confirm(tt('确认反作废？将恢复到作废前状态'), tt('提示'), { confirmButtonText: tt('确定'), cancelButtonText: tt('取消'), type: 'warning' })
      await unvoidBill(billId.value)
      ElMessage.success(tt('反作废成功'))
      await load()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('反作废失败')) }
  }

  async function handleDelete() {
    try {
      await ElMessageBox.confirm(tt('确认删除？删除后不可恢复'), tt('警告'), { confirmButtonText: tt('删除'), cancelButtonText: tt('取消'), type: 'error' })
      await deleteBill(billId.value)
      ElMessage.success(tt('删除成功'))
      if (typeof router !== 'undefined' && router.back) router.back()
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败')) }
  }

  function handleCopy() {
    // 全站统一口径：跳到本单据「新增」页并带上源单，由新增页自动装入源单数据成新草稿
    const id = billId.value
    if (!id) { ElMessage.warning(tt('请先保存单据再复制')); return }
    router.push({ path: '/purchase/orders/new', query: { copy: id } })
  }
  // ======== 状态流转 END ========

</script>

<style scoped lang="scss">
.purchase-order-edit {
  padding: 12px 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
  font-size: 12px;
}

// ===== 顶部标题栏 =====
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

  .title-area {
    .title-row {
      display: flex;
      align-items: center;
      gap: 6px;
    }
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

  .header-right { display: flex; gap: 6px; align-items: center; }
}

.more-btn {
  color: #606266;
  &:hover { color: #409eff; border-color: #b3d8ff; }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
  &:hover {
    box-shadow: 0 3px 12px rgba(64, 158, 255, 0.4);
  }
}

// ===== 可折叠卡片 =====
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

    .header-right {
      display: flex;
      gap: 6px;
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

    .item-count {
      font-size: 11px;
      color: #909399;
      font-weight: 400;
    }
  }

  .card-body {
    padding: 12px 14px;
    &.no-padding { padding: 0; }
  }
}

// ===== 基本信息 - 上下分组 =====
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

    .custom-hint {
      font-size: 11px;
      font-weight: 400;
      color: #c0c4cc;
    }
  }

  .section-fields {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0 12px;
  }

  :deep(.el-form-item) {
    margin-bottom: 6px;
  }

  :deep(.el-form-item__label) {
    font-size: 12px;
    color: #606266;
    line-height: 28px;
  }

  :deep(.el-form-item__content) {
    line-height: 28px;
  }

  .span-all {
    grid-column: span 4;
  }

  .empty-custom {
    grid-column: span 4;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 12px 0;
    color: #c0c4cc;
    font-size: 12px;
    background: #fafbfc;
    border-radius: 4px;
    border: 1px dashed #ebeef5;
  }
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
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px #409eff inset;
  }
  :deep(.el-input-number) {
    width: 100%;
    .el-input__wrapper {
      min-height: 28px;
      padding: 0 6px;
    }
  }
  :deep(.el-date-editor) {
    width: 100%;
  }
}

.muted-text { color: #c0c4cc; font-size: 12px; }

// ===== 源单信息栏 =====
.source-info-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
  font-size: 12px;
  color: #409eff;

  &.muted {
    background: #f5f7fa;
    border-bottom-color: #ebeef5;
    color: #909399;
  }

  .source-label {
    color: #606266;
    font-weight: 500;
  }

  .source-hint {
    margin-left: auto;
    color: #c0c4cc;
    font-size: 11px;
  }
}

// ===== 明细表格 =====
.table-wrap {
  overflow-x: auto;
  padding: 10px 12px 0;
}
.detail-subtable-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 0 4px;
  .subtable-title {
    font-size: 14px;
    font-weight: bold;
    color: #303133;
    &::before {
      content: '';
      display: inline-block;
      width: 3px;
      height: 14px;
      background: #409eff;
      margin-right: 6px;
      vertical-align: middle;
    }
  }
}

.compact-table {
  min-width: 1800px;
  font-size: 12px;

  :deep(.el-table__header th) {
    background: #f5f7fa !important;
    color: #606266;
    font-weight: 600;
    font-size: 12px;
    padding: 6px 4px;
  }
  :deep(.el-table__body td) {
    padding: 6px 4px;
  }
  :deep(.el-table__row:hover > td) {
    background: #f5f7fa !important;
  }
}

.material-thumb {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  cursor: pointer;
}

.thumb-placeholder {
  color: #c0c4cc;
  font-size: 12px;
}

.table-select {
  :deep(.el-select__wrapper) {
    box-shadow: none;
  }
  :deep(.el-input__inner) { font-size: 12px; }
}

.opt-code {
  color: #409eff;
  font-weight: 600;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  font-size: 11px;
  margin-right: 6px;
}
.opt-name { color: #303133; font-size: 12px; }
.opt-model {
  float: right;
  color: #909399;
  font-size: 11px;
}

/* 行内下拉附加字段（自定义字段「字段名：值」）：灰色小字，仅在有值时渲染 */
.opt-cf {
  margin-left: 8px;
  color: #909399;
  font-size: 11px;
  white-space: normal;
  word-break: break-word;
}
.discount-text { color: #e6a23c; }
.net-amount { color: #409eff; font-weight: 600; }

// ===== 金额汇总区（放在明细下面） =====
.summary-area {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 14px;
  background: #fafbfc;
  border-top: 1px solid #ebeef5;
}

.summary-block {
  flex: 1;
  min-width: 200px;

  .block-title {
    font-size: 11px;
    font-weight: 600;
    color: #409eff;
    margin-bottom: 6px;
    padding-bottom: 4px;
    border-bottom: 1px solid #ebeef5;
  }

  .sum-line {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 3px 0;
    font-size: 12px;

    .label { color: #606266; }
    .value {
      color: #303133;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
      &.discount { color: #e6a23c; }
    }

    &.total {
      padding-top: 5px;
      margin-top: 3px;
      border-top: 1px dashed #dcdfe6;
      font-size: 13px;

      .label { font-weight: 600; color: #303133; }
      .value {
        color: #409eff;
        font-weight: 700;
        font-size: 14px;
      }
    }
  }
}

.picker-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

@media (max-width: 1400px) {
  .form-groups { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .form-groups { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .purchase-order-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .form-groups { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>


<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
/* 面板加宽：Element Plus 给 .el-select-dropdown 设的是行内 min-width = 触发框宽度-2px，覆盖不了，
   因此在 popper 根节点设 min-width；选项改为换行，避免内容被 ellipsis 截断 */
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
