<template>
  <div class="sale-order-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/sale/orders')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row" data-onboarding="page-title">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '销售订单' : '新增销售订单' }}</span>
            <span v-if="form.orderNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.orderNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
            <el-tag v-if="form.approvalStatus && form.approvalStatus !== form.status" size="small" :type="approvalStatusType(form.approvalStatus)" effect="plain" class="status-tag">{{ $dt(form.approvalStatus) }}</el-tag>
            <el-tag v-if="form.closeStatus === 1" size="small" type="success" effect="plain">{{ $tt("已结案") }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right" data-onboarding="bill-actions">
        <el-button v-permission="perm('view')" v-if="form.orderId" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <!-- 售后报修入口：订单已生效时可发起售后维修单；客户包隐藏单据时整钮不渲染 -->
        <el-button v-permission="perm('add')" v-if="form.orderId && form.status === '已生效' && !exchangeBillHidden" @click="goRepair">售后报修</el-button>
        <!-- 销售替代出库（2026-11-18 用户口径）：不占页头独立按钮，并入「下推」下拉，
             避免破坏页头按钮结构；订单已无替换额度时该菜单项不出现。 -->
        <PushDownButton
          v-if="canPushDown(form.status, form.closeStatus) || (!exchangeBillHidden && canExchange)"
          source-type="SaleOrder"
          :source-doc="form"
          :extra-items="pushExtraItems"
          @extra="onExtraPush"
        />
        <BillActions bill-type="SALE_ORDER"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.orderId"
          @save="onSave"
          @submit="onSubmit"
          @approve="onApprove"
          @reject="handleReject"
          @reverse-approve="handleReverseApprove"
          @close="onCloseOrder"
          @reopen="onReopenOrder"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="70px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section" data-onboarding="section-doc">
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
              <el-form-item :label="$tf('交货日期')" :required="isFieldRequired('deliveryDate')">
                <el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('超量发货比例(%)')">
                <el-input-number v-model="form.overshipRatio" :min="0" :max="9999" :precision="2" :disabled="!isDraft" class="field-input" controls-position="right" />
              </el-form-item>
            </div>
          </div>

          <!-- 客户信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>客户信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('客户')" prop="customerId" required>
                <div class="customer-field-row">
                  <el-select v-model="form.customerId" filterable :placeholder="$tt('选择客户')" class="field-input" :disabled="!isDraft" @change="onCustomerChange">
                    <el-option v-for="c in withFallbackOption(customers, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
                  </el-select>
                  <el-button v-permission="perm('add')" v-if="isDraft" class="customer-add-btn" :icon="Plus" :title="$tt('快捷新建客户')" @click="openQuickCustomer" />
                </div>
              </el-form-item>
              <el-form-item :label="$tf('商机')">
                <el-select v-model="form.opportunityId" :placeholder="$tt('可选择关联商机')" filterable clearable class="field-input" :disabled="!isDraft" @change="onOpportunityChange">
                  <el-option v-for="opt in opportunityOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('客户编号')">
                <el-input v-model="form.customerNo" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('付款分类')">
                <el-tag v-if="form.paymentCategory" :type="paymentCategoryTagType(form.paymentCategory)" size="default" effect="light">
                  {{ form.paymentCategory }}
                </el-tag>
                <span v-else class="payment-category-empty">—</span>
              </el-form-item>
              <el-form-item :label="$tf('客户简称')">
                <el-input v-model="form.customerShortName" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('联系人')" v-if="isFieldVisible('contactPerson')">
                <el-input v-model="form.contactPerson" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('联系电话')" v-if="isFieldVisible('contactPhone')">
                <el-input v-model="form.contactPhone" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('收货地址')" class="span-all" v-if="isFieldVisible('receiveAddress')">
                <el-input v-model="form.receiveAddress" class="field-input" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

          <!-- 交易信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>交易信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('所属部门')">
                <el-select v-model="form.departmentId" filterable clearable :placeholder="$tt('选择部门')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('销售员')" prop="salerId" required>
                <el-select v-model="form.salerId" filterable :placeholder="$tt('选择销售员')" class="field-input" :disabled="!isDraft" @change="onSalerChange">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('币种')" prop="currencyId" required>
                <el-select v-model="form.currencyId" :placeholder="$tt('选择币种')" class="field-input" :disabled="!isDraft || !foreignCurrencyEnabled" @change="onCurrencyChange">
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
              <el-form-item :label="$tf('运输方式')">
                <el-select v-model="form.transportMethod" :placeholder="$tt('选择运输方式')" class="field-input" :disabled="!isDraft">
                  <el-option :label="$tf('快递')" value="快递" />
                  <el-option :label="$tf('物流')" value="物流" />
                  <el-option :label="$tf('自提')" value="自提" />
                  <el-option :label="$tf('空运')" value="空运" />
                  <el-option :label="$tf('海运')" value="海运" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('仓库')" prop="warehouseId">
                <el-select v-model="form.warehouseId" filterable :placeholder="$tt('选择仓库')" class="field-input" :disabled="!isDraft">
                  <el-option v-for="w in withFallbackOption(warehouses, 'warehouseId', 'warehouseName', form)" :key="w.stockId || w.warehouseId" :label="w.stockName || w.warehouseName" :value="w.stockId || w.warehouseId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('价格等级')">
                <el-select v-model="form.priceLevelId" filterable :placeholder="$tt('选择价格等级')" class="field-input" clearable :disabled="!isDraft" @change="onPriceLevelChange">
                  <el-option v-for="p in priceLevels" :key="p.priceLevelId" :label="p.priceLevelName" :value="p.priceLevelId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('整单折扣')">
                <el-input-number v-model="form.discountRate" :precision="2" :step="1" :min="0" :max="100" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('发货收款管控')">
                <el-select v-model="form.paymentControl" class="field-input" :disabled="!isDraft">
                  <el-option :label="$tf('不管控')" value="none" />
                  <el-option :label="$tf('收多少款发多少货')" value="quota" />
                  <el-option :label="$tf('全额收款才能发货')" value="full" />
                  <el-option :label="$tf('定金模式')" value="advance" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('超期账款管控')">
                <el-switch v-model="overdueBlockBool" :disabled="!isDraft" />
              </el-form-item>
            </div>
          </div>

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

    <!-- 明细卡片（通用组件 BillItemsTable：与库存单据共用同一套明细实现） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="form.items"
      page-key="SaleOrderEdit_items"
      title="商品明细"
      subtitle="物料明细"
      col-setting-title="物料明细列设置"
      onboarding-header="detail-header-actions"
      onboarding-subheader="detail-subtable-header"
      onboarding-table="detail-table"
      :columns="baseDetailColumns"
      :custom-columns="itemCustomColumns"
      :readonly="!isDraft"
      :total-qty="totalQty"
      :summary-items="summaryItems"
      @row-change="onRowPriceChange"
      @remove="calcTotal"
    >
      <template #actions>
        <SourcePicker ref="sourcePickerRef" target-type="SaleOrder" @pick="onSourcePicked" :disabled="!isDraft" btn-type="success" size="small" />
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
          popper-class="material-select-popper"
        >
          <el-option
            v-for="m in row._filteredMaterials || materials"
            :key="m.materialId"
            :label="`${m.materialCode} ${m.materialName}`"
            :value="m.materialId"
          >
            <span class="opt-code">{{ m.materialCode }}</span>
            <span class="opt-name">{{ m.materialName }}</span>
            <!-- 下拉附加字段：设置中心→自定义字段配置（物料）里勾选了「下拉显示」的字段，值为空则不渲染 -->
            <span v-for="cf in materialDropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
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
          @change="(e) => onUnitCellChange(row, e)"
        />
      </template>
      <!-- 多单位换算 5 列（副单位/副单位数量/辅助数量/浮动单位/浮动数量）已由明细通用组件自动注入，本页不再手写 -->
      <template #cell-sourceBillNo="{ row }">
        <el-link v-if="row.sourceBillNo" type="primary" :underline="false" @click="goToSourceBill(row)">{{ row.sourceBillNo }}</el-link>
        <span v-else class="thumb-placeholder">—</span>
      </template>
      <template #summary-extra>
        <!-- 收款进度（已生效订单才显示） -->
        <div v-if="form.orderId && form.status === '已生效'" class="summary-block">
          <div class="block-title">{{ $tt("收款进度") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("订单金额：") }}</span>
            <span class="value">¥{{ Number(form.netAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("已收款：") }}</span>
            <span class="value" :style="{ color: Number(form.receivedAmount || 0) > 0 ? '#67c23a' : '#909399' }">¥{{ Number(form.receivedAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("未收款：") }}</span>
            <span class="value" :style="{ color: Number(form.netAmount || 0) - Number(form.receivedAmount || 0) > 0 ? '#e6a23c' : '#67c23a' }">¥{{ (Number(form.netAmount || 0) - Number(form.receivedAmount || 0)).toFixed(2) }}</span>
          </div>
          <div class="sum-line total">
            <span class="label">{{ $tt("收款进度：") }}</span>
            <span class="value">
              <el-progress
                :percentage="Math.min(100, Math.round(Number(form.receivedAmount || 0) / (Number(form.netAmount || 1)) * 100))"
                :status="Number(form.receivedAmount || 0) >= Number(form.netAmount || 0) ? 'success' : ''"
                :stroke-width="14"
                style="width:160px"
              />
            </span>
          </div>
        </div>
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
        <div class="summary-block">
          <div class="block-title">{{ $tt("定金信息") }}</div>
          <div class="sum-line">
            <span class="label">{{ $tt("定金金额：") }}</span>
            <span class="value">
              <el-input-number v-model="form.depositAmount" :precision="2" :step="1" :min="0" size="small" :disabled="!isDraft" />
            </span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("付款方式：") }}</span>
            <span class="value">
              <el-select v-model="form.paymentMethod" :placeholder="$tt('选择付款方式')" size="small" style="width:140px;" :disabled="!isDraft">
                <el-option :label="$tf('现金')" value="现金" />
                <el-option :label="$tf('银行转账')" value="银行转账" />
                <el-option :label="$tf('支付宝')" value="支付宝" />
                <el-option :label="$tf('微信支付')" value="微信支付" />
              </el-select>
            </span>
          </div>
          <div class="sum-line">
            <span class="label">{{ $tt("收款账户：") }}</span>
            <span class="value">
              <el-select v-model="form.accountId" :placeholder="$tt('选择收款账户')" clearable filterable size="small" style="width:150px;" :disabled="!isDraft" @change="onPickAccount">
                <el-option v-for="a in accountOptions" :key="a.accountId" :label="a.accountName" :value="a.accountId" />
              </el-select>
            </span>
          </div>
        </div>
      </template>
      <template #operation="{ row, index }">
        <el-button
          v-if="canPlanReplace(row)"
          v-permission="'sale:order:push'"
          text
          type="primary"
          size="small"
          @click="openReplacePlan(row)"
        >{{ $tt('替换发货') }}</el-button>
        <el-button
          v-permission="perm('deleteRow')"
          text
          type="danger"
          size="small"
          :disabled="!isDraft"
          @click="removeItem(index)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
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
          type="sale-order"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="SALE_ORDER" :bill-id="form.orderId" :status="form.status" />

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

    <!-- 物料库选择弹窗（通用组件：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker） -->
    <MaterialPickerDialog v-model="pickerVisible" @confirm="onMaterialPicked" />

    <!-- 快捷新建客户弹窗 -->
    <el-dialog v-model="quickCustomerVisible" :title="$tf('快捷新建客户')" width="560px" :close-on-click-modal="false" append-to-body>
      <el-form :model="quickCustomerForm" label-width="90px">
        <el-form-item :label="$tf('客户名称')" required>
          <el-input v-model="quickCustomerForm.customerName" :placeholder="$tt('如 XX科技有限公司')" maxlength="200" />
        </el-form-item>
        <el-form-item :label="$tf('客户简称')">
          <el-input v-model="quickCustomerForm.shortName" :placeholder="$tt('客户简称（选填）')" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$tf('联系人')">
          <el-input v-model="quickCustomerForm.contactPerson" :placeholder="$tt('联系人（选填）')" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$tf('联系电话')">
          <el-input v-model="quickCustomerForm.contactPhone" :placeholder="$tt('联系电话（选填）')" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$tf('地址')">
          <el-input v-model="quickCustomerForm.address" :placeholder="$tt('地址（选填）')" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="quickCustomerVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" :loading="quickCustomerSaving" :disabled="!quickCustomerForm.customerName || !quickCustomerForm.customerName.trim()" @click="submitQuickCustomer">{{ $tt("保存并选择") }}</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="SALE_ORDER" :bill-id="form.orderId" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="销售订单" :bill-no="form.orderNo || ''" />

    <!-- 销售替代出库（生成销售换货单） -->
    <SaleExchangeDialog v-model="exchangeDialogVisible" :order="form" @created="onExchangeCreated" />

    <!-- 替换发货指示（销售在订单行指定替代品，仓库下推/选单自动展开） -->
    <SaleOrderReplacePlanDialog
      v-model="replacePlanVisible"
      :order="form"
      :item="replacePlanItem"
      :materials="materials"
      @changed="onReplacePlanChanged"
    />

      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.orderId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="SALE_ORDER" :bill-id="form.orderId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.orderId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="SALE_ORDER" :bill-id="form.orderId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { calcTaxAmount } from '@/utils/billTax'
import { isBillHidden } from '@/config/hiddenBills'
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, h} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification} from 'element-plus'
import { ArrowLeft, Document, Collection, Plus, Grid, Delete, Printer, Link, ArrowRight, Check, TopRight, ArrowDown, CircleCheck, CircleClose, RefreshLeft, Close, Share, Setting, Memo } from '@element-plus/icons-vue'
import { getSaleOrder, saveSaleOrder, updateSaleOrder, submitSaleOrder, approveSaleOrder, rejectSaleOrder, reverseApproveSaleOrder, cancelSaleOrder, closeOrder, reopenOrder, voidSaleOrder, unvoidSaleOrder, deleteSaleOrder, pushSaleOrderToPurchase } from '@/api/sale/saleOrder'
import { listAllCustomer, saveCustomer } from '@/api/basedata/customer'
import { listAllBankAccount } from '@/api/basedata/bankAccount'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllEmployee } from '@/api/basedata/employee'
import { getCurrentEmployee } from '@/composables/useCurrentEmployee'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { useSysConfig } from '@/composables/useSysConfig'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllPaymentTerm } from '@/api/basedata/payment-term'
import { listAllPriceLevel } from '@/api/basedata/price-level'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllLocation } from '@/api/basedata/location'
import { listOpportunity } from '@/api/crm/opportunity'
import FileUploader from '@/components/FileUploader.vue'
import SourcePicker from '@/components/SourcePicker.vue'
import PushDownButton from '@/components/PushDownButton.vue'
import PaymentTermSelect from '@/components/PaymentTermSelect.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { getDropDisplayEnabled } from '@/api/system/customField'
import { canPushDown } from '@/utils/bill-status'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import SaleExchangeDialog from '@/components/sale/SaleExchangeDialog.vue'
import SaleOrderReplacePlanDialog from '@/components/sale/SaleOrderReplacePlanDialog.vue'
import BillActions from '@/components/bill/BillActions.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
// 多单位换算（2026-11-10）：浮动开关 + 换算行批量加载 + 换算率快照（5 列取值由明细通用组件负责）
import { loadConversions, loadFloatUnitEnabled, rateInfoOf } from '@/composables/useUnitConvert'
import { LINE_BOM_COLUMNS, useLineBom } from '@/composables/useLineBom'
const { fillRowBom } = useLineBom()
import { useCopyBill } from '@/composables/useCopyBill'
import { validateBillRequiredFields } from '@/utils/billValidation'
import { applyPushSource } from '@/utils/pushSourceApplier'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import * as BillStatus from '@/utils/bill-status'
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
// 物料库选择弹窗（通用件）：内置自定义字段搜索、全字段/自定义字段列、列设置全局共享 MaterialPicker
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
// 物料下拉的输入过滤 + 背景提示文字（通用件）
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
import { useBillLineStock, BILL_STOCK_COLUMNS } from '@/composables/useBillLineStock'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
// 单据取价（2026-09-20 价格策略修复）：按设置中心「取价优先级」取价，选物料/改数量时回填单价
import { resolveUnitPrice, applyFetchedUnitPrice } from '@/composables/usePriceFetch'
import { statusType, approvalStatusType } from '@/composables/useBillStatus'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('sale:orders')

// 单据编码自动生成
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('SALE_ORDER')

// 物料明细基础列定义（通用组件 BillItemsTable 消费）：editor = 单元格控件，type = 纯展示格式；
// 物料列与来源单号列由页面提供插槽（#cell-materialId / #cell-sourceBillNo）
const baseDetailColumns = [
  { prop: 'materialMainImage', label: '图片', width: 60, align: 'center', type: 'image' },
  { prop: 'materialId', label: '商品名称', minWidth: 280 },
  { prop: 'materialCode', label: '商品编号', width: 130 },
  ...BILL_STOCK_COLUMNS,
  { prop: 'specification', label: '规格', width: 110, type: 'text' },
  // 行级 BOM（2026-11-06）：按物料主 BOM 自动带出，只读
  ...LINE_BOM_COLUMNS,
  { prop: 'unit', label: '单位', width: 60, align: 'center', type: 'text' },
  { prop: 'warehouseId', label: '仓库', width: 160, editor: 'select', clearable: false,
    options: () => warehouses.value.map(w => ({ value: w.stockId || w.warehouseId, label: w.stockName || w.warehouseName })),
    change: (row) => onRowWarehouseChange(row, row.warehouseId) },
  { prop: 'binId', label: '储位', width: 140, editor: 'select',
    optionsFn: (row) => rowLocations(row).map(b => ({ value: b.binId, label: `${b.binCode || ''} ${b.binName || ''}`.trim() })) },
  { prop: 'quantity', label: '数量', width: 90, editor: 'number', precision: 4 },
  { prop: 'unitPrice', label: '单价', width: 90, editor: 'number', precision: 4 },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'taxRate', label: '税率%', width: 70, editor: 'number', precision: 0, min: 0, max: 100 },
  { prop: 'discountRate', label: '折扣率%', width: 80, editor: 'number', precision: 2, min: 0, max: 100 },
  { prop: 'discountAmount', label: '折扣额', width: 85, align: 'right', type: 'amount2', cellClass: 'discount-text' },
  { prop: 'amount', label: '金额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'taxAmount', label: '税额', width: 85, align: 'right', type: 'amount2' },
  { prop: 'netAmount', label: '价税合计', width: 95, align: 'right', type: 'netAmount' },
  { prop: 'avgCost', label: '成本', width: 85, align: 'right', type: 'amount4' },
  { prop: 'batchNo', label: '批次号', width: 120, editor: 'text' },
  { prop: 'sourceBillNo', label: '来源单号', minWidth: 160 },
  { prop: 'sourceLineNo', label: '来源行号', width: 80, align: 'center', type: 'text' },
  { prop: 'replaceDeliveryInfo', label: '替换发货', minWidth: 240, type: 'text' }
]
// 多单位换算 5 列由明细通用组件（BillItemsTable）按「单位列」自动注入，本页不再手写列定义与取值
// 汇总区（通用组件按此渲染，与库存单据同一套）；含税业务关闭时不显示「合计税额」
const summaryItems = computed(() => {
  const items = [
    { label: '折前金额：', value: `¥${Number(form.totalAmount || 0).toFixed(2)}` },
    { label: '折扣金额：', value: `- ¥${Number(form.discountAmount || 0).toFixed(2)}`, class: 'discount' }
  ]
  if (taxEnabled.value) {
    items.push({ label: '合计税额：', value: `¥${Number(form.taxAmount || 0).toFixed(2)}` })
  }
  items.push({ label: '价税合计：', value: `¥${Number(form.netAmount || 0).toFixed(2)}`, total: true })
  return items
})
useBillLineStock(() => form.items)

// ===== 物料自定义字段：①行内下拉附加展示 ②物料库弹窗全字段展示 =====
// dropDisplayEnabled：「自定义字段下拉显示」定制功能总开关（设置中心→系统参数→界面显示 可配）
//   关闭时 materialDropFields 保持为空 → 行内下拉不追加任何自定义字段内容（弹窗全字段不受该开关控制）
const dropDisplayEnabled = ref(true)
// materialCfAllFields：物料实体全部启用的自定义字段（物料库弹窗展示用，不受「下拉显示」控制）
// materialDropFields：其中勾选了「下拉显示」的字段（单据明细行内下拉附加展示用）
const materialCfAllFields = ref([])
const materialDropFields = ref([])
// 已请求过自定义字段值的物料ID集合：避免"该物料确实没有自定义值"时被反复重复请求
const materialCfLoadedIds = ref(new Set())
// 批量预取上限：/custom-field/batch-value 是 GET + ids 逗号拼接，一次性全量提交会导致 URL 超长(414)
const MATERIAL_CF_BATCH = 500

// 行内下拉要追加展示的「字段名：值」列表；值为空的字段不渲染（不留空、不显示占位符）
function materialDropText(m) {
  if (!m || !materialDropFields.value.length) return []
  const vals = materialCustomValueMap.value[m.materialId]
  if (!vals) return []
  return materialDropFields.value
    .map(f => ({ key: f.fieldKey, label: f.fieldLabel, value: vals[f.fieldKey] }))
    .filter(x => x.value !== undefined && x.value !== null && x.value !== '')
}

// 按批预取全量物料的自定义字段值（≤500/批、顺序执行；失败静默降级，不影响选料）
async function preloadMaterialCustomValues() {
  if (!materialDropFields.value.length) return
  const ids = (materials.value || [])
    .map(m => m.materialId)
    .filter(id => id != null && !materialCfLoadedIds.value.has(id))
  if (!ids.length) return
  for (let i = 0; i < ids.length; i += MATERIAL_CF_BATCH) {
    const batch = ids.slice(i, i + MATERIAL_CF_BATCH)
    batch.forEach(id => materialCfLoadedIds.value.add(id))
    try {
      const map = await batchLoadCustomFieldValues('material', batch)
      Object.assign(materialCustomValueMap.value, map)
    } catch (e) {
      console.warn('[material-cf] 下拉预取失败:', e)
    }
  }
}

// ===== 物料库弹窗的列定义 / 列设置 / 图片渲染 已收进通用组件 MaterialPickerDialog =====
// （列定义用共享的 materialPickerColumns()，列设置 page-key = 'MaterialPicker' 全局共享：
//   配一次，所有单据页的物料库弹窗都生效；本页不再重复维护这份列清单）

const route = useRoute()
const router = useRouter()
const formRef = ref()
const headerFormRef = ref()

const isEdit = computed(() => !!route.params.id)

// ========== form 必须放在最前面，避免 computed TDZ 错误 ==========
const form = reactive({
  orderId: null,
  orderNo: '',
  orderDate: new Date().toISOString().slice(0, 10),
  customerId: null,
  customerNo: '',
  customerShortName: '',
  customerName: '',
  paymentCategory: '',
  opportunityId: null,
  opportunityNo: '',
  contactPerson: '',
  contactPhone: '',
  departmentId: null,
  salerId: null,
  salerName: '',
  priceLevelId: null,
  currencyId: null,
  currencyCode: '',
  exchangeRate: 1.0,
  paymentTermId: null,
  paymentTermName: '',
  paymentMethod: '',
  depositAmount: 0,
  paymentControl: '',   // 发货收款管控（源头订单选定，出库单随单带出）
  overdueBlock: '',     // 超期账款管控开关：'true'/'false'，空=按设置中心默认
  warehouseId: null,
  receiveAddress: '',
  transportMethod: '',
  isTax: '是',
  taxCalcMethod: '不含税价',
  isInvoiced: '否',
  isConsignment: '否',
  logisticsCompany: '',
  logisticsNo: '',
  projectNo: '',
  customerOrderNo: '',
  deliveryDate: null,
  overshipRatio: 0,
  totalAmount: 0,
  discountRate: 0,
  discountAmount: 0,
  taxAmount: 0,
  netAmount: 0,
  receivedAmount: 0,
  remark: '',
  attachmentUrls: '',
  status: '草稿',
  approvalStatus: '',
  sourceBillType: '',
  sourceBillNo: '',
  closeStatus: 0,
  createTime: '',
  auditTime: '',
  customField1: '',
  customField2: '',
  customField3: '',
  items: []
})

// 仅草稿/驳回等可编辑态允许修改表头/明细/按钮；已生效/待审核/已结案/已作废等全只读
// 保留 isDraft 变量名（模板里 ~30+ 处用 `:disabled="!isDraft"`），语义升级为"是否可编辑"
const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const canSaveShow = computed(() => !form.orderId || BillStatus.canSave(form.status, form.closeStatus))
const submitShow = computed(() => BillStatus.canSubmit(form.status, form.closeStatus))
const approveShow = computed(() => BillStatus.canApprove(form.status, form.closeStatus))

// ===== 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细「含税价 / 不含税价」换算 =====
const { loadSysConfig, getConfig } = useSysConfig()
// 全站商业口径总开关（设置中心：启用含税业务 / 启用外币业务）
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
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
    // 读取失败保持默认（含税=开、外币=关）
  }
  if (!taxEnabled.value) {
    // 含税业务关闭：表头「计税方式」整项隐藏，一律按不计税计算
    form.taxCalcMethod = '不计税'
  } else if (!form.orderId) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
  // 外币业务关闭：新建单据只用本位币、汇率锁 1（老单据保持原币，仅供查看）
  if (!foreignCurrencyEnabled.value && !form.orderId) {
    const list = currencies.value || []
    const base = list.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || list[0]
    if (base) {
      form.currencyId = base.currencyId
      form.exchangeRate = Number(base.exchangeRate || 1) || 1
    }
  }
  // 发货管控下放单据（2026-10-19）：新建订单默认取设置中心；老单据为空时按设置中心补显示
  //（与提交校验兜底口径一致：单据上没选 → 后端按设置中心默认判断）
  form.paymentControl = form.paymentControl || (getConfig('sale.delivery.payment.control') || 'none')
  form.overdueBlock = form.overdueBlock || ((getConfig('sale.credit.overdue.block') === 'true') ? 'true' : 'false')
}

/** 表头「计税方式」切换 → 全表明细按新口径重算 */
function onTaxCalcMethodChange() {
  form.items.forEach(row => calcRow(row))
  calcTotal()
}

// 「超期账款管控」开关代理：落库口径为字符串 'true'/'false'（与设置中心布尔参数同值域）
const overdueBlockBool = computed({
  get: () => form.overdueBlock === 'true',
  set: (v) => { form.overdueBlock = v ? 'true' : 'false' }
})

const STORAGE_KEY = 'sale-order-edit-collapsed'

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
const itemCustomColumns = ref([])
const materialCustomValueMap = ref({})

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

// ========== 新手教程交互：演示注入 ==========
// 第12步模拟添加物料：从基础物料库挑两个真实数据，push进form.items，自动带出单位价格
function injectDemoItems() {
  try {
    if (!isDraft.value) return
    if (!form.items) form.items = []
    if (form.items.length > 0) return
    const defaultWhId = form.warehouseId || null
    const defaultWhName = (() => {
      if (!defaultWhId) return ''
      const wh = warehouses.value.find(w => (w.stockId || w.warehouseId) === defaultWhId)
      return wh ? (wh.stockName || wh.warehouseName) : ''
    })()
    const picks = (materials.value || []).slice(0, 2)
    if (picks.length === 0) {
      // 物料库还没加载完成，先空push一行
      addItem()
      return
    }
    picks.forEach((m, idx) => {
      const row = {
        materialId: m.materialId,
        materialCode: m.materialCode || '',
        materialName: m.materialName || '',
        specification: m.model || '',
        unitId: m.saleUnitId || m.baseUnitId || null,
        unit: m.baseUnitName || '',
        warehouseId: defaultWhId,
        warehouseName: defaultWhName,
        binId: null,
        binName: '',
        quantity: idx === 0 ? 10 : 5,
        unitPrice: Number(m.salePrice || 0),
        avgCost: Number(m.avgCost || 0),
        taxRate: 13,
        discountRate: 0,
        discountAmount: 0,
        amount: 0,
        taxAmount: 0,
        netAmount: 0,
        batchNo: '',
        _isLotControl: !!m.lotControl,
        _isExpiryControl: !!m.expiryControl
      }
      form.items.push(row)
      calcRow(row)
    })
    calcTotal()
  } catch (e) {
    console.warn('[SaleOrderEdit] injectDemoItems failed:', e)
  }
}
function onOnboardingInject(e) {
  const d = e && e.detail
  if (!d) return
  if (d.inject === 'demo-items') {
    if (materials.value && materials.value.length > 0) injectDemoItems()
    else setTimeout(injectDemoItems, 300)
  }
}

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('sale_order')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields
  
  const itemResult = await loadCustomFieldDefinitions('sale_order_item')
  itemCustomFields.value = Array.from(itemResult.fieldMap.values())
  itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })

  // 物料自定义字段：全部（物料库弹窗展示用）+ 勾选了「下拉显示」的（行内下拉附加展示用）
  const materialResult = await loadCustomFieldDefinitions('material')
  materialCfAllFields.value = Array.from(materialResult.fieldMap.values())
  // 定制开关关闭时不下拉展示任何自定义字段（materialCfAllFields 仍保留，供物料库弹窗使用）
  materialDropFields.value = dropDisplayEnabled.value
    ? materialCfAllFields.value.filter(f => f.isDropShow === 1)
    : []
}

// 草稿态变化时，重新生成明细自定义字段列（editable控制）
watch(isDraft, () => {
  if (itemCustomFields.value && itemCustomFields.value.length > 0) {
    itemCustomColumns.value = customFieldsToTableColumns(itemCustomFields.value, { editable: isDraft.value, listShowOnly: false })
  }
}, { immediate: false })

const customers = ref([])
const materials = ref([])
/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materials.value.find(x => x.materialId === row.materialId) || null
}

// ===== 多单位换算（2026-11-10）：5 列取值与渲染已收进明细通用组件（components/bill/multiUnitColumns.js），本页只保留单位快照 =====

/** 行单位变化：写「所选单位 → 基本」换算率快照（浮动行后端信任 >0 快照）并重算行 */
function onUnitCellChange(row, e) {
  if (e && e.rate != null) row.conversionRate = e.rate
  calcRow(row)
}

// 明细行物料变化（打开回显/选料/物料库添加/复制/下推 全部路径）→ 批量预取该批物料的换算行
// （内部判重不重复请求；缓存回填后只读单元格经响应式缓存自动刷新）
watch(() => (form.items || []).map(r => (r && r.materialId) || '').join('|'), (val) => {
  if (!val) return
  loadConversions(val.split('|').filter(Boolean).map(Number))
})
const employees = ref([])
const currencies = ref([])
const paymentTerms = ref([])
const priceLevels = ref([])
const units = ref([])
const warehouses = ref([])
const locations = ref([])
// 所属部门下拉：后端 sale_order.department_id 列本就存在，本页此前缺选择入口 → 部门恒为空
const departments = ref([])
const opportunityOptions = ref([])
const accountOptions = ref([])

const cancelDialog = ref(false)
const cancelReason = ref('')
const rejectDialog = ref(false)
const rejectReason = ref('')
const voidDialog = ref(false)
const voidReason = ref('')
const printVisible = ref(false)
const changeRecordVisible = ref(false)
const sourcePickerVisible = ref(false)
const sourcePickerRef = ref()

// ===== 替换发货指示（2026-11-27）=====
const replacePlanVisible = ref(false)
const replacePlanItem = ref(null)
function canPlanReplace(row) {
  if (!row || !row.orderItemId) return false
  if (form.status !== '已生效' || Number(form.closeStatus) === 1) return false
  const remain = Number(row.quantity || 0) - Number(row.turnedOutputQuantity || 0) + Number(row.turnedReturnQuantity || 0)
  return remain > 0.0001
}
function openReplacePlan(row) {
  replacePlanItem.value = row
  replacePlanVisible.value = true
}
async function onReplacePlanChanged() {
  try { await loadDetail() } catch (e) { /* 详情刷新失败时保持当前页面 */ }
}
function removeItem(index) {
  form.items.splice(index, 1)
  calcTotal()
}

// ===== 销售替代出库（生成销售换货单）=====
// 口径：已生效 + 已有已生效出库 + 未手动结案 + 仍有可替换额度。
// 禁止用 closeStatus !== 1 判断：发货满额会自动结案，会导致入口消失。
// 2026-11-18：可替换额度=0 时（替换发货已完成）入口必须消失，避免点开弹窗无可操作行造成"点击静默"。
// 客户包隐藏单据（VITE_HIDDEN_BILLS）：true = 换货/维修相关入口一律不渲染（121 不上）
const exchangeBillHidden = isBillHidden('SALE_EXCHANGE')
const exchangeDialogVisible = ref(false)
const canExchange = computed(() => !!(
  form.orderId
  && form.status === '已生效'
  && form.hasEffectiveDelivery === true
  && form.manualClosed !== true
  && Number(form.replaceableTotalQuantity || 0) > 0
))

/** 「下推」下拉里的额外项（2026-11-18）：销售替代出库并入下推，不再单占页头按钮 */
const pushExtraItems = computed(() => {
  const items = []
  if (canPushDown(form.status, form.closeStatus)) {
    items.push({ key: 'purchaseOrder', label: tt('下推生成采购订单') })
  }
  if (!exchangeBillHidden && canExchange.value) {
    items.push({ key: 'saleExchange', label: tt('销售替代出库') })
  }
  return items
})

function onExchangeCreated(exchangeId) {
  if (exchangeId) router.push('/sale/exchanges/' + exchangeId)
}

/** 售后报修：带来源与客户跳到售后维修单新增页 */
function goRepair() {
  router.push({
    path: '/sale/repairs/new',
    query: {
      sourceType: '销售订单',
      sourceId: form.orderId,
      sourceBillNo: form.orderNo,
      customerId: form.customerId,
      customerName: form.customerName
    }
  })
}

/** 物料库选择弹窗显隐（弹窗本体 = 通用组件 MaterialPickerDialog，搜索/分页/列设置都在组件内） */
const pickerVisible = ref(false)

const totalQty = computed(() => {
  return form.items.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)
})

const rules = {
  orderDate: [{ required: true, message: tt('请选择订单日期'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  warehouseId: [],
  salerId: [{ required: true, message: tt('请选择销售员'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}

/** 付款管控分类的标签颜色：不同分类用不同色调，便于操作员识别 */
function paymentCategoryTagType(c) {
  return {
    '现结': 'success',
    '月结': 'warning'
  }[c] || 'info'
}

function onCustomerChange(id) {
  const c = customers.value.find(x => x.customerId === id)
  if (c) {
    form.customerName = c.customerName
    form.customerNo = c.customerCode || ''
    form.customerShortName = c.shortName || ''
    form.paymentCategory = c.paymentCategory || ''
    if (c.contactPerson) form.contactPerson = c.contactPerson
    if (c.contactPhone) form.contactPhone = c.contactPhone
    if (c.deliveryAddress || c.address) form.receiveAddress = c.deliveryAddress || c.address || ''
    if (c.paymentTermId) {
      form.paymentTermId = c.paymentTermId
      onPaymentTermChange(c.paymentTermId)
    }
    if (c.currencyId) {
      form.currencyId = c.currencyId
      onCurrencyChange(c.currencyId)
    }
    if (c.defaultSalerId) form.salerId = c.defaultSalerId
    if (c.defaultPriceLevelId) {
      form.priceLevelId = c.defaultPriceLevelId
      onPriceLevelChange(c.defaultPriceLevelId)
    }
    // 客户默认税率带入明细行（2026-11-08：数值化比较，兼容输入框回传的字符串税率）
    const custTaxRate = Number(c.taxRate)
    if (custTaxRate > 0) {
      form.items.forEach(row => {
        const rowRate = Number(row.taxRate)
        if (!rowRate || rowRate === 13) {
          row.taxRate = custTaxRate
          calcRow(row)
        }
      })
    }
    calcTotal()
  }
}

// 商机选择：加载未关闭商机列表
async function loadOpportunities() {
  try {
    const res = await listOpportunity({ page: 1, size: 1000, isClosed: 0 })
    const data = res || {}
    opportunityOptions.value = (data.records || []).map(o => ({
      value: o.opportunityId,
      label: `${o.opportunityNo} - ${o.opportunityName}`,
      customerId: o.customerId,
      customerName: o.customerName
    }))
  } catch (e) { console.warn('加载商机失败', e) }
}

// 商机变更联动：带出商机编号，若未选客户则自动带出商机的客户
function onOpportunityChange(oppId) {
  if (!oppId) {
    form.opportunityNo = ''
    return
  }
  const selected = opportunityOptions.value.find(o => o.value === oppId)
  if (selected) {
    form.opportunityNo = selected.label.split(' - ')[0]
    if (!form.customerId && selected.customerId) {
      form.customerId = selected.customerId
      form.customerName = selected.customerName
      onCustomerChange(selected.customerId)
    }
  }
}

function onSalerChange(id) {
  const e = employees.value.find(x => x.employeeId === id)
  if (e) form.salerName = e.employeeName
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c) {
    form.currencyCode = c.currencyCode
    if (c.exchangeRate) form.exchangeRate = c.exchangeRate
  }
}

// 定金收款账户联动：选择账户后回写账户名称
function onPickAccount(id) {
  const a = accountOptions.value.find(x => x.accountId === id)
  form.accountId = id || null
  form.accountName = a ? a.accountName : ''
}

function onPaymentTermChange(id) {
  const p = (id && typeof id === 'object') ? id : paymentTerms.value.find(x => x.paymentTermId === id)
  if (!p) {
    form.paymentTermName = ''
    return
  }
  form.paymentTermName = p.paymentTermName
  const baseAmount = Number(form.netAmount || form.totalAmount || 0)
  if (p.discountRate) {
    form.discountRate = p.discountRate
  }
  if (p.prepaidRate) {
    form.depositAmount = Number((baseAmount * p.prepaidRate / 100).toFixed(2))
  }
}

function onPriceLevelChange(id) {
  const p = priceLevels.value.find(x => x.priceLevelId === id)
  const rate = p && p.priceAdjustRate ? Number(p.priceAdjustRate) : 0
  form.items.forEach(row => {
    if (row.baseUnitPrice != null) {
      row.unitPrice = Number((row.baseUnitPrice * (1 + rate / 100)).toFixed(4))
      calcRow(row)
    }
  })
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
  const key = row.itemId || row.orderItemId || row.materialId
  if (_repriceTimers.has(key)) clearTimeout(_repriceTimers.get(key))
  _repriceTimers.set(key, setTimeout(async () => {
    _repriceTimers.delete(key)
    if (!row.materialId) return
    // 用户已手改单价 → 不覆盖
    if (row._autoUnitPrice !== undefined && Number(row.unitPrice) !== Number(row._autoUnitPrice)) return
    const priced = await resolveUnitPrice({
      side: 'sale',
      partnerId: form.customerId,
      materialId: row.materialId,
      quantity: row.quantity,
      taxType: row.taxCalcMethod
    })
    if (!priced) return
    row.unitPrice = priced.price
    row._autoUnitPrice = priced.price
    calcRow(row)
  }, 400))
}

async function onMaterialChange(row, id) {
  // 行级 BOM（2026-11-06）：选物料后按主 BOM 自动带出清单与版本
  fillRowBom(row)
  const m = materials.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.materialMainImage = m.mainImage || ''
    row.specification = m.model
    const unitId = m.saleUnitId || m.baseUnitId || null
    row.unitId = unitId
    if (unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === unitId)
      row.unit = u ? u.unitName : ''
    } else {
      row.unit = ''
    }
    // 多单位换算（2026-11-10）：按新物料/新单位写换算率快照；换料后旧浮动单位/浮动数量不再适用 → 清空
    row.conversionRate = rateInfoOf(m, unitId).rate
    row.floatUnitId = null
    row.floatQuantity = null
    // 2026-09-20 价格策略修复：单价按设置中心「取价优先级」取价（价格策略 → 价格等级 → 物料默认售价）。
    // 只做"带价"、不做校验：保存仍不校验（既定口径），低于策略价的拦截在【提交】时由后端执行。
    // baseUnitPrice 仍取物料基础售价，保证「价格等级」切换时的重算口径不变。
    const priced = await resolveUnitPrice({
      side: 'sale',
      partnerId: form.customerId,
      materialId: m.materialId,
      quantity: row.quantity,
      taxType: row.taxCalcMethod
    })
    row.baseUnitPrice = m.salePrice || 0
    row.unitPrice = priced ? priced.price : (m.salePrice || 0)
    // 记录"本次自动带出的价"：数量变化时仅当单价仍等于它（用户未手改）才自动重算
    row._autoUnitPrice = row.unitPrice
    row.avgCost = m.avgCost || 0
    row.taxRate = m.taxRate || 13
    row.discountRate = 0
    row.discountAmount = 0
    row._isLotControl = m.isLotControl || false
    row._isExpiryControl = m.isExpiryControl || false
    row._expiryDays = m.expiryDays || 0
    // 填充行默认仓库：优先物料默认仓库，否则表头仓库
    const preferredWhId = m.defaultWarehouseId || form.warehouseId
    if (preferredWhId) {
      row.warehouseId = preferredWhId
      const wh = warehouses.value.find(w => (w.stockId || w.warehouseId) === preferredWhId)
      if (wh) row.warehouseName = wh.stockName || wh.warehouseName
    }
    // 行级储位：如果之前选的储位不属于本仓库，清空
    if (row.binId && locations.value.length > 0) {
      const bindExists = locations.value.some(b => b.binId === row.binId && b.stockId === row.warehouseId)
      if (!bindExists) row.binId = null
    }
    if (m && m.materialId && itemCustomFields.value.length > 0) {
      // 用"已请求过的ID集合"判重：该物料本身没有自定义值时接口返回空对象，
      // 若还按 materialCustomValueMap 是否命中来判断，会每次选料都重复请求一次
      if (!materialCfLoadedIds.value.has(m.materialId)) {
        materialCfLoadedIds.value.add(m.materialId)
        const map = await batchLoadCustomFieldValues('material', [m.materialId])
        Object.assign(materialCustomValueMap.value, map)
      }
      // 第5参必须传物料实体字段定义：物料档案的字段与单据明细的字段是各自独立创建的，
      // fieldKey 必然不同（自动生成），只有带上物料侧定义才能按 fieldLabel 同名关联，
      // 把物料档案的值带进明细同名列（与 BomEdit.vue 的既有正确写法一致）
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value, materialCfAllFields.value)
    }
    calcRow(row)
  }
}

function calcRow(row, { displayOnly = false } = {}) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  const taxRate = Number(row.taxRate || 0)
  const discountRate = Number(row.discountRate || 0)
  // 表头「计税方式」决定「单价」是含税价还是不含税价 → 换算另一列（含税价 / 不含税价）
  const method = form.taxCalcMethod || '不含税价'
  const factor = 1 + taxRate / 100
  const noTax = method === '不计税'
  const inclusive = method === '含税价'
  // displayOnly：打开已有单据/复制装入源单时，只按表头口径补算「含税价/不含税价」两个显示列，
  // 金额/折扣/税额/价税合计一律保持数据库原值 —— 老单据单价是反算舍入值，
  // 用"单价×数量"重算会让价税合计漂移 1 分（2026-10-13 用户反馈 9000 → 9000.01）
  if (displayOnly) {
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
    return
  }
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
  calcTotal()
}

function rowLocations(row) {
  const whId = row?.warehouseId
  if (!whId) return []
  return locations.value.filter(b => b.stockId === whId)
}

function onRowWarehouseChange(row, whId) {
  const wh = warehouses.value.find(w => (w.stockId || w.warehouseId) === whId)
  row.warehouseName = wh ? (wh.stockName || wh.warehouseName) : ''
  // 切换仓库后清空储位（新仓库可能无此储位）
  row.binId = null
  row.binName = ''
}

function calcTotal() {
  let totalAmount = 0
  let discountAmount = 0
  let taxAmount = 0
  form.items.forEach(i => {
    const amt = Number(i.amount || 0)
    const disc = Number(i.discountAmount || 0)
    totalAmount += amt + disc
    discountAmount += disc
    taxAmount += Number(i.taxAmount || 0)
  })
  form.totalAmount = totalAmount.toFixed(2)
  form.discountAmount = discountAmount.toFixed(2)
  form.taxAmount = taxAmount.toFixed(2)
  form.netAmount = (totalAmount - discountAmount + taxAmount).toFixed(2)
  // 本位币折算（按表头汇率，实时显示）
  const rate = Number(form.exchangeRate || 1)
  form.baseTotalAmount = (totalAmount * rate).toFixed(2)
  form.baseTaxAmount = (taxAmount * rate).toFixed(2)
  form.baseNetAmount = (Number(form.netAmount) * rate).toFixed(2)
}

function addItem() {
  const defaultWhId = form.warehouseId || null
  const defaultWhName = (() => {
    if (!defaultWhId) return ''
    const wh = warehouses.value.find(w => (w.stockId || w.warehouseId) === defaultWhId)
    return wh ? (wh.stockName || wh.warehouseName) : ''
  })()
  form.items.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    unitId: null,
    unit: '',
    // 多单位换算（2026-11-10）：浮动单位/浮动数量随行保存，新行初始化为空
    floatUnitId: null,
    floatQuantity: null,
    warehouseId: defaultWhId,
    warehouseName: defaultWhName,
    binId: null,
    binName: '',
    quantity: 1,
    unitPrice: 0,
    priceWithTax: 0,
    priceWithoutTax: 0,
    avgCost: 0,
    taxRate: 13,
    discountRate: 0,
    discountAmount: 0,
    amount: 0,
    taxAmount: 0,
    netAmount: 0,
    batchNo: '',
    _isLotControl: false,
    _isExpiryControl: false
  })
}

/** 参与「物料」下拉搜索的自定义字段：勾了「下拉显示」（受定制总开关控制）或勾了「可搜索」的字段 */
const materialSearchableFields = computed(() => (materialCfAllFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (dropDisplayEnabled.value && f.isDropShow === 1))
))

/** 物料下拉的背景提示文字：声明可搜索范围；没有可搜索的自定义字段时原样返回「选择物料」（零回归） */
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/**
 * 明细行「物料」下拉的输入过滤（用户口径：不加额外搜索框，统一用下拉本身）
 * 过滤范围 = 物料编码/名称/规格 ＋ 自定义字段值 ＋ 自定义字段名，详见 utils/materialFilter.js
 */
function materialFilterMethod(val, row) {
  if (!val) { row._filteredMaterials = null; return }
  row._filteredMaterials = filterMaterials(materials.value, val, materialCustomValueMap.value, materialSearchableFields.value)
}

/**
 * 物料库弹窗「确认添加」回调（弹窗本体 = 通用组件 MaterialPickerDialog）
 * - 组件内已批量取好选中物料的「全部自定义字段值」，并随 confirm 回传 valueMap
 * - 这里把值并入本页值表（与行内下拉预取共用同一份，不再二次请求），再逐行组装明细行
 */
async function onMaterialPicked({ rows: picked, valueMap }) {
  if (!picked || picked.length === 0) return
  if (valueMap) {
    Object.assign(materialCustomValueMap.value, valueMap)
    Object.keys(valueMap).forEach(id => materialCfLoadedIds.value.add(Number(id)))
  }
  picked.forEach(m => {
    const row = {
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      materialMainImage: m.mainImage || '',
      specification: m.model || '',
      unitId: m.saleUnitId || m.baseUnitId || null,
      unit: '',
      quantity: 1,
      unitPrice: m.salePrice || 0,
      baseUnitPrice: m.salePrice || 0,
      avgCost: m.avgCost || 0,
      taxRate: m.taxRate || 13,
      discountRate: 0,
      discountAmount: 0,
      amount: 0,
      taxAmount: 0,
      netAmount: 0,
      batchNo: ''
    }
    if (row.unitId && units.value.length > 0) {
      const u = units.value.find(x => x.unitId === row.unitId)
      row.unit = u ? u.unitName : ''
    }
    // 多单位换算（2026-11-10）：换算率快照带出 + 浮动两字段初始化为空（与行内选料同口径）
    row.conversionRate = rateInfoOf(m, row.unitId).rate
    row.floatUnitId = null
    row.floatQuantity = null
    if (itemCustomFields.value.length > 0) {
      // 同 picker 外选中路径：带物料实体定义做「同名 label」关联（详见 handleMaterialChange）
      injectMaterialCustomFields(row, m.materialId, materialCustomValueMap.value, itemCustomFields.value, materialCfAllFields.value)
    }
    calcRow(row)
    form.items.push(row)
    // 2026-09-20 价格策略修复：物料库弹窗添加明细这条路径也要按「取价优先级」取价
    applyFetchedUnitPrice(row, {
      side: 'sale',
      partnerId: form.customerId,
      materialId: m.materialId,
      quantity: row.quantity,
      taxType: row.taxCalcMethod
    }, m.salePrice || 0, (r) => calcRow(r))
  })
  pickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${picked.length}${tt(" 条明细")}`)
}

// 强制同步 el-input-number 组件的输入值到 Vue 响应式数据
// 解决用户修改了单价/数量但未失焦就点击保存/提交导致数据不同步的问题
function flushInputNumbers() {
  if (!form.items || form.items.length === 0) return
  // 遍历并强制转换数值，确保响应式对象里的值是最新的 Number 类型
  form.items.forEach(row => {
    // 只处理有值的行，避免干扰空行
    if (row.quantity !== undefined) row.quantity = Number(row.quantity || 0)
    // 浮动数量：有值才强制同步（空保持空，避免未使用浮动的行被写成 0 落库）
    if (row.floatQuantity != null) row.floatQuantity = Number(row.floatQuantity || 0)
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
  // 强制触发 Vue 响应式更新
  form.items = [...form.items]
}

async function onSave(silent, opts = {}) {
  if (form.status && !isDraft.value) {
    ElMessage.warning(`${tt("当前状态为「")}${form.status}${tt("」, 不可修改")}`)
    return false
  }
  // 强制同步输入框数据，防止用户未失焦就点击保存导致单价等数据为旧值
  flushInputNumbers()
  // 宽松草稿保存：不校验必填，允许空客户、空物料、无明细（用户可以后续补全再提交）
  // 保存宽松：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  form.items = (form.items || []).filter(r => r && r.materialId)
  // 按当前表头「计税方式」把明细含税价/不含税价/金额/税额/价税合计刷成最新
  form.items.forEach(row => calcRow(row))
  calcTotal()
  try {
    if (form.orderId) {
      await updateSaleOrder(form.orderId, form)
    } else {
      const res = await saveSaleOrder(form)
      if (res && res.orderId) form.orderId = res.orderId
      if (res && res.orderNo) form.orderNo = res.orderNo
      if (res && res.status) form.status = res.status
      if (res && res.approvalStatus) form.approvalStatus = res.approvalStatus
    
      if (!opts.skipRouteSync && !route.params.id && form.orderId) {
        try { await router.replace({ name: 'SaleOrderEdit', params: { id: String(form.orderId) } }) } catch (_) {}
      }
    }
    if (headerCustomFieldMap.size > 0) {
      const headerValues = {}
      for (const key of headerCustomFieldMap.keys()) {
        if (key in form) headerValues[key] = form[key]
      }
      await saveCustomFieldValues('sale_order', form.orderId, headerValues, headerFormulaFields.value)
    }
    if (itemCustomFields.value.length > 0 && form.items && form.items.length > 0) {
      // 关键修复（2026-09-22）：保存主表时后端走"明细先删后插"，明细主键已变（实测 506→507）。
      // 必须先按保存后的**新主键**重新对齐，否则明细级自定义字段会写到已删除的旧行上 ——
      // 表现为编辑页再打开读不到、报表里那一列永远空白（历史孤儿值 447/453 即由此产生）。
      try {
        const fresh = await getSaleOrder(form.orderId)
        remapItemIdsAfterSave(form.items, (fresh && fresh.items) || [], 'orderItemId')
      } catch (e) {
        console.warn('[saleOrder] 保存后刷新明细主键失败，明细级自定义字段可能挂到旧行', e)
      }
      const itemKeys = new Set(itemCustomFields.value.map(f => f.fieldKey))
      for (let i = 0; i < form.items.length; i++) {
        const row = form.items[i]
        if (!row.itemId && !row.orderItemId) continue
        const itemId = row.itemId || row.orderItemId
        const values = {}
        for (const key of itemKeys) {
          if (key in row) values[key] = row[key]
        }
        await saveCustomFieldValues('sale_order_item', itemId, values)
      }
    }
    triggerListRefresh('/sale/orders')
    if (!silent) ElMessage.success(tt('保存成功'))

    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  return false
  }
}

async function onSubmit() {
  // 强制同步输入框数据，防止用户未失焦就点击提交导致单价等数据为旧值
  flushInputNumbers()
    // 前端必填校验（B功能）
    // 统一校验：头表单 + 明细内表单 + 任何 formRef；失败时精确列出缺失字段名
  const missing = await validateBillRequiredFields({ headerFormRef, formRef, rules, $el: document, pageKey: 'sale_order', model: form })
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

  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.orderId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'SALE_ORDER', billLabel: '订单' })
    await submitSaleOrder(form.orderId)
    ElMessage.success(submitSuccessMessage(needApproval))
    triggerListRefresh('/sale/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.orderId) {
      try { await router.replace({ name: 'SaleOrderEdit', params: { id: String(form.orderId) } }) } catch (_) {}
    }
  }
}

async function onApprove() {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  try {
    await ElMessageBox.confirm(tt('确认审核该订单？审核后将不可修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await approveSaleOrder(form.orderId)
    ElMessage.success(tt('审核成功'))
    triggerListRefresh('/sale/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('审核失败'))
  }
}

async function doCancel() {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  if (!cancelReason.value || !cancelReason.value.trim()) {
    ElMessage.warning(tt('请输入取消原因'))
    return
  }
  try {
    await cancelSaleOrder(form.orderId, cancelReason.value)
    ElMessage.success(tt('取消成功'))
    cancelDialog.value = false
    cancelReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('取消失败'))
  }
}

async function onCloseOrder() {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  try {
    await ElMessageBox.confirm(tt('确认结案？结案后可反结案'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await closeOrder(form.orderId)
    ElMessage.success(tt('结案成功'))
    triggerListRefresh('/sale/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('结案失败'))
  }
}

async function onReopenOrder() {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  try {
    await ElMessageBox.confirm(tt('确认反结案？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await reopenOrder(form.orderId)
    ElMessage.success(tt('反结案成功'))
    triggerListRefresh('/sale/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('反结案失败'))
  }
}

function handleReject () {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  if (!BillStatus.canReject(form.status, form.closeStatus)) { ElMessage.warning(tt('当前状态不可驳回')); return }
  rejectReason.value = ''
  rejectDialog.value = true
}

async function doReject () {
  if (!rejectReason.value || !rejectReason.value.trim()) {
    ElMessage.warning(tt('请输入驳回原因'))
    return
  }
  try {
    await rejectSaleOrder(form.orderId, rejectReason.value.trim())
    ElMessage.success(tt('已驳回'))
    triggerListRefresh('/sale/orders')
    rejectDialog.value = false
    rejectReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('驳回失败'))
  }
}

async function handleReverseApprove () {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  if (!BillStatus.canReverseApprove(form.status, form.closeStatus)) { ElMessage.warning(tt('不是已生效，不可重置草稿')); return }
  try {
    await ElMessageBox.confirm(tt('确认重置草稿？重置草稿后单据回到草稿态，可重新编辑'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await reverseApproveSaleOrder(form.orderId)
    ElMessage.success(tt('重置草稿成功'))
    triggerListRefresh('/sale/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('重置草稿失败'))
  }
}

function handleVoid () {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  if (!BillStatus.canVoid(form.status, form.closeStatus)) { ElMessage.warning(tt('当前状态不可作废')); return }
  voidReason.value = ''
  voidDialog.value = true
}

async function doVoid () {
  if (!voidReason.value || !voidReason.value.trim()) {
    ElMessage.warning(tt('请输入作废原因'))
    return
  }
  try {
    await voidSaleOrder(form.orderId, voidReason.value.trim())
    ElMessage.success(tt('已作废'))
    triggerListRefresh('/sale/orders')
    voidDialog.value = false
    voidReason.value = ''
    await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || tt('作废失败'))
  }
}

async function handleUnvoid () {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  if (!BillStatus.canUnvoid(form.status)) { ElMessage.warning(tt('当前状态不可反作废')); return }
  try {
    await ElMessageBox.confirm(tt('确认反作废？反作废后单据回到作废前的状态'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await unvoidSaleOrder(form.orderId)
    ElMessage.success(tt('反作废成功'))
    triggerListRefresh('/sale/orders')
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('反作废失败'))
  }
}

async function handleDelete () {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单')); return }
  if (!BillStatus.canDelete(form.status, form.closeStatus)) { ElMessage.warning(tt('当前状态不可删除')); return }
  try {
    await ElMessageBox.confirm(tt('确认删除该订单？删除后不可恢复'), tt('提示'), {
      confirmButtonText: tt('确定删除'),
      cancelButtonText: tt('取消'),
      type: 'error'
    })
    await deleteSaleOrder(form.orderId)
    ElMessage.success(tt('已删除'))
    triggerListRefresh('/sale/orders')
    router.push('/sale/orders')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('删除失败'))
  }
}

// ===== 复制功能 =====
// 2026-11-27 口径：复制只保留"当前单据本身"的信息；回写（已发/已退/已开票/已收/成本/结案）
// 和下推来源链（来源单号/商机/报价/明细来源行）一律不复制。销售员按设定保留同一个。
function clearCopiedOrderWriteBack () {
  const headerNull = [
    'sourceBillType', 'sourceBillNo', 'sourceType', 'sourceId', 'sourceDocId',
    'opportunityId', 'opportunityNo', 'quoteNo',
    'approvedBy', 'approvedAt', 'closedBy', 'closedAt', 'closedReason',
    'billBeforeStatus', 'voidedBy', 'voidedAt', 'voidReason',
    'createdAt', 'updatedAt', 'createdBy', 'updatedBy'
  ]
  headerNull.forEach(k => { if (k in form) form[k] = null })
  form.receivedAmount = 0
  form.hasEffectiveDelivery = false
  form.manualClosed = false
  form.hasPendingReturn = false
  form.replaceableTotalQuantity = 0

  form.items.forEach(row => {
    ;[
      'turnedOutputQuantity', 'turnedReturnQuantity', 'turnedInvoiceQuantity',
      'turnedInvoiceAmount', 'turnedProduceQuantity', 'turnedSubcontractQuantity',
      'deliveredQuantity', 'receivedAmount', 'avgCost', 'detailCloseStatus',
      'pendingReturnQuantity'
    ].forEach(k => { if (k in row) row[k] = 0 })
    ;[
      'deliveredLineNo', 'sourceLineNo', 'sourceId', 'sourceItemId', 'sourceDocId',
      'sourceType', 'sourceBillType', 'sourceBillNo',
      'replacePlanId', 'replacedFromMaterialId', 'replacedFromMaterialCode',
      'replacedFromMaterialName', 'orderCostBefore',
      'sourceMaterialId', 'sourceMaterialCode', 'sourceMaterialName',
      'replaceDeliveryInfo', 'createdAt', 'updatedAt', 'createdBy', 'updatedBy'
    ].forEach(k => { if (k in row) row[k] = null })
    if ('substitutePlans' in row) row.substitutePlans = []
    if ('orderHasActivePlan' in row) row.orderHasActivePlan = false
  })
}

const { applyCopy } = useCopyBill({
  route,
  loadFn: getSaleOrder,
  form,
  afterCopy: () => {
    // 先剥离回写/下推字段，再按源单库值补显示并汇总
    clearCopiedOrderWriteBack()
    // 复制装入源单：明细保持源单库值原样（含两列缺失时的补显示），不按"单价×数量"重算，
    // 避免复制反算失真的老单时价税合计漂移（与 loadDetail 同口径，2026-10-13）
    form.items.forEach(row => {
      if (row.priceWithTax == null || row.priceWithoutTax == null) {
        calcRow(row, { displayOnly: true })
      }
    })
    calcTotal()
  },
  noField: 'orderNo',
  customField: { entity: 'sale_order', itemEntity: 'sale_order_item', itemIdField: 'orderItemId' }
})

function handleCopy () {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单再复制')); return }
  router.push({ path: '/sale/orders/new', query: { copy: form.orderId } })
}
function handlePrint() {
  if (!form.orderId) { ElMessage.warning(tt('请先保存订单再打印')); return }
  printVisible.value = true
}

// 下推采购订单：服务端生成采购订单（模式与 WorkOrderEdit.doPush 一致：确认 → 调接口 → 提示 → 跳转详情）
const pushingToPurchase = ref(false)
/** 【2026-10-09】「下推」菜单里并进来的额外项分发（采购订单 / 销售替代出库） */
function onExtraPush (key) {
  if (key === 'purchaseOrder') onPushToPurchase()
  else if (key === 'saleExchange') exchangeDialogVisible.value = true
}
async function onPushToPurchase() {
  const id = form.orderId
  if (!id) { ElMessage.warning(tt('请先保存订单再下推')); return }
  try {
    await ElMessageBox.confirm(tt('确认下推生成「采购订单」？'), tt('确认下推'), { type: 'warning' })
  } catch (e) {
    return // 用户取消
  }
  pushingToPurchase.value = true
  try {
    const res = await pushSaleOrderToPurchase(id)
    ElMessage.success(tt('采购订单下推成功'))
    // 优先使用返回的新单据ID跳转，否则跳转到采购订单列表
    const newId = res && (res.orderId || res.id)
    router.push(newId ? `/purchase/orders/${newId}` : '/purchase/orders')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || tt('下推采购订单失败'))
  } finally {
    pushingToPurchase.value = false
  }
}

async function loadDetail() {
  const __detailId = route.params.id || form.orderId || ''
  if (__detailId) {
    try {
      const res = await getSaleOrder(__detailId)
      Object.assign(form, res)
      if (headerCustomFieldMap.size > 0 && res.orderId) {
        const headerValues = await loadCustomFieldValues('sale_order', res.orderId)
        Object.assign(form, headerValues)
      }
      if (!form.items) form.items = []
      if (form.items && materials.value && materials.value.length > 0) {
        form.items.forEach(row => {
          if (!row.materialMainImage && row.materialId) {
            const m = materials.value.find(x => x.materialId === row.materialId)
            row.materialMainImage = m?.mainImage || ''
          }
        })
      }
      if (itemCustomFields.value.length > 0 && res.items && res.items.length > 0) {
        const itemIds = res.items.map(it => it.itemId || it.orderItemId).filter(Boolean)
        if (itemIds.length > 0) {
          const itemValuesMap = await batchLoadCustomFieldValues('sale_order_item', itemIds)
          form.items.forEach(row => {
            const itemId = row.itemId || row.orderItemId
            const values = itemValuesMap[itemId]
            if (values) Object.assign(row, values)
          })
        }
      }
      // 计税方式：老单据按设置中心默认/总开关归一
      // 明细一律按数据库原值回显（金额/税额/价税合计/含税价/不含税价都不重算），
      // 仅两列缺失的老老单据补显示列；用户改动行或切表头计税方式时才真正重算
      // —— 防止老单据单价反算舍入值被"单价×数量"重算放大成价税合计漂移（2026-10-13）
      await initTaxSettings()
      form.items.forEach(row => {
        if (row.priceWithTax == null || row.priceWithoutTax == null) {
          calcRow(row, { displayOnly: true })
        }
      })
      calcTotal()
    } catch (e) {
      ElMessage.error(e.message || tt('加载失败'))
    }
  } else {
    // 新建时默认当前用户为销售员（可修改）
    const me = await getCurrentEmployee()
    if (me && !form.salerId) {
      form.salerId = me.employeeId
      form.salerName = me.employeeName
    }
    // 新建：表头「计税方式」取设置中心「默认计税方式」
    await initTaxSettings()
  }
}

const SOURCE_BILL_ROUTE_MAP = {
  '销售报价单': '/sale/quotes',
  '销售合同': '/sale/contracts',
  '销售订单': '/sale/orders',
  '销售出库单': '/sale/deliveries',
  '销售退货单': '/sale/returns',
  '打样单': '/sample/orders'
}

function goToSourceBill(row) {
  if (!row.sourceBillNo) return
  const routeBase = SOURCE_BILL_ROUTE_MAP[row.sourceBillType]
  if (routeBase) {
    router.push(`${routeBase}?keyword=${encodeURIComponent(row.sourceBillNo)}`)
  } else {
    ElMessage.warning(`${tt("暂不支持跳转到 ")}${row.sourceBillType || tt('该来源单据')}`)
  }
}

function onSourcePicked(sourceData, items) {
  if (sourceData) {
    Object.keys(sourceData).forEach(key => {
      if (key in form && key !== 'items' && key !== 'orderId' && key !== 'orderNo') {
        form[key] = sourceData[key]
      }
    })
    if (sourceData && headerCustomFieldMap.size > 0) {
      copyCustomFieldValues(sourceData, form, Array.from(headerCustomFieldMap.values()))
    }
  }
  if (items && items.length > 0) {
    // 2026-11-23：选单带入追加到现有明细末尾，不再覆盖原有明细
    const newRows = items.map(item => {
      const row = { ...item }
      calcRow(row)
      return row
    })
    if (itemCustomFields.value.length > 0) {
      newRows.forEach((row, idx) => {
        if (items[idx]) {
          copyCustomFieldValues(items[idx], row, itemCustomFields.value)
        }
      })
    }
    form.items = [...(form.items || []), ...newRows]
  }
  calcTotal()
  ElMessage.success(tt('已从源单带入数据'))
}

// 加载下拉基础数据（物料/客户/币种等）——独立函数，供 onMounted 和切回标签页时复用
async function loadBaseData() {
  // 使用 allSettled 防止单点失败导致全部下拉框为空
  try {
    const results = await Promise.allSettled([
      listAllCustomer(),
      listAllMaterial(),
      listAllEmployee(),
      listAllCurrency(),
      listAllPaymentTerm(),
      listAllPriceLevel(),
      listAllUnit(),
      listAllWarehouse(),
      listAllLocation(),
      listAllBankAccount(),
      listAllDepartment()
    ])
    const [custRes, matRes, empRes, curRes, ptRes, plRes, unitRes, whRes, locRes, accRes, deptRes] = results.map(r => r.status === 'fulfilled' ? r.value : [])
    customers.value = custRes || []
    materials.value = matRes || []
    employees.value = empRes || []
    currencies.value = curRes || []
    paymentTerms.value = ptRes || []
    priceLevels.value = plRes || []
    units.value = unitRes || []
    warehouses.value = whRes || []
    locations.value = Array.isArray(locRes) ? locRes : (locRes?.records || [])
    accountOptions.value = accRes || []
    departments.value = deptRes || []
    // 行内下拉附加字段：物料到位后按批预取自定义字段值（不阻塞渲染；失败静默降级不影响选料）
    preloadMaterialCustomValues()
    if (!form.currencyId && currencies.value.length > 0) {
      form.currencyId = currencies.value[0].currencyId
      onCurrencyChange(form.currencyId)
    }
  } catch (e) {
    console.error('下拉数据加载失败:', e)
  }
}

// ===== 快捷新建客户（客户字段 + 号入口）=====
const quickCustomerVisible = ref(false)
const quickCustomerSaving = ref(false)
const quickCustomerForm = reactive({
  customerName: '',
  shortName: '',
  contactPerson: '',
  contactPhone: '',
  address: ''
})

function openQuickCustomer() {
  quickCustomerForm.customerName = ''
  quickCustomerForm.shortName = ''
  quickCustomerForm.contactPerson = ''
  quickCustomerForm.contactPhone = ''
  quickCustomerForm.address = ''
  quickCustomerVisible.value = true
}

async function submitQuickCustomer() {
  const name = quickCustomerForm.customerName.trim()
  if (!name) return
  quickCustomerSaving.value = true
  try {
    // 编码由后端按 customer_code 自动生成，无需填写
    const saved = await saveCustomer({
      customerName: name,
      shortName: quickCustomerForm.shortName.trim() || null,
      contactPerson: quickCustomerForm.contactPerson.trim() || null,
      contactPhone: quickCustomerForm.contactPhone.trim() || null,
      address: quickCustomerForm.address.trim() || null
    })
    const newId = saved && (saved.customerId || saved.customer_id || saved.data && (saved.data.customerId || saved.data.customer_id)) || null
    quickCustomerVisible.value = false
    ElMessage.success(tt('客户保存成功，已自动选择'))
    // 刷新客户下拉数据
    await loadBaseData()
    // 自动选择刚录入的客户（优先用返回ID，兜底按名称匹配）
    let target = null
    if (newId) {
      target = customers.value.find(c => c.customerId === newId)
    }
    if (!target) {
      target = customers.value.find(c => c.customerName === name)
    }
    if (target) {
      form.customerId = target.customerId
      onCustomerChange(target.customerId)
    }
  } catch (e) {
    console.error('快捷新建客户失败:', e)
    ElMessage.error(tt('客户保存失败：') + ((e && (e.message || e.msg)) || tt('未知错误')))
  } finally {
    quickCustomerSaving.value = false
  }
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  loadCollapsed()

  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('SALE_ORDER')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  // 【性能】以下三件事互不依赖，必须并行，不能串行 await。
  // 原先是三个连续 await（下拉显示开关 → 自定义字段 → 10 个下拉），打开单据要连等三段网络往返，
  // 而真正的单据内容（loadDetail）排在它们之后 → 表现为"打开单据后内容加载很慢"。
  // 并行后墙钟时间从 T1+T2+T3 降到 max(T1,T2,T3)；所有下游逻辑仍在这三者完成之后才执行，依赖顺序不变。
  await Promise.all([
    // 「自定义字段下拉显示」定制功能总开关（取不到按开启处理，失败不阻断主流程）
    getDropDisplayEnabled()
      .then(v => { dropDisplayEnabled.value = !!v })
      .catch(() => { dropDisplayEnabled.value = true }),
    // 全局开关「启用浮动单位」：决定明细「浮动单位/浮动数量」两列整列显隐（取不到按关闭 = 旧行为）
    loadFloatUnitEnabled().catch(() => {}),
    // 加载自定义字段定义（失败不阻断主流程）
    loadCustomFields().catch(e => { console.warn('加载自定义字段失败:', e) }),
    // 下拉数据（loadBaseData 内部已用 Promise.allSettled 并行）
    loadBaseData()
  ])
  // 行内下拉的预取同时依赖「物料自定义字段定义」与「物料列表」两份结果，而上面三者是并行执行的：
  // loadBaseData 内部那次预取会因定义尚未加载完（materialDropFields 还是空）被整体跳过 →
  // 表现为下拉里自定义字段一直空白。此处等两者都就绪后补一次（内部有判重，不会重复请求）。
  preloadMaterialCustomValues()
  loadOpportunities()
  if (route.query.opportunityId) {
    form.opportunityId = Number(route.query.opportunityId) || route.query.opportunityId
    form.opportunityNo = route.query.opportunityNo || ''
    if (route.query.customerId && !form.customerId) {
      form.customerId = Number(route.query.customerId) || route.query.customerId
      form.customerName = route.query.customerName || ''
      onCustomerChange(form.customerId)
    }
    // 从商机带入物料明细
    const oppItemsStr = sessionStorage.getItem('oppToSaleOrderItems')
    if (oppItemsStr) {
      try {
        const oppItems = JSON.parse(oppItemsStr)
        if (Array.isArray(oppItems) && oppItems.length > 0) {
          // 2026-11-23：商机转入的物料明细追加到现有明细末尾，不覆盖已有明细
          const oppRows = oppItems.map(it => ({
            materialId: it.materialId,
            materialCode: it.materialCode || '',
            materialName: it.materialName || '',
            specification: it.specification || '',
            unit: it.unit || '',
            quantity: Number(it.quantity || 1),
            unitPrice: Number(it.unitPrice || 0),
            taxRate: 13,
            taxType: 2,
            discountRate: 0,
            amount: Number(it.amount || 0).toFixed(2),
            taxAmount: (Number(it.amount || 0) * 0.13).toFixed(2),
            netAmount: (Number(it.amount || 0) * 1.13).toFixed(2),
            remark: it.remark || '',
            sourceBillType: '商机',
            sourceBillNo: route.query.opportunityNo || '',
            sourceId: Number(route.query.opportunityId) || null,
            sourceItemId: null,
            sourceType: 'OPPORTUNITY'
          }))
          oppRows.forEach(row => calcRow(row))
          form.items = [...(form.items || []), ...oppRows]
          ElMessage.success(`${tt("已从商机 ")}${route.query.opportunityNo}${tt(" 带入 ")}${oppItems.length}${tt(" 条物料明细")}`)
        }
      } catch (e) { /* 忽略 JSON 解析错误 */ }
      // 用完即清，避免重复带入
      sessionStorage.removeItem('oppToSaleOrderItems')
    }
  }
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
    const applied = await applyPushSource(route, 'SaleOrder', form, (items) => {
      form.items = items
      form.items.forEach(row => calcRow(row))
    })
    if (applied) ElMessage.info(tt('已从源单带入数据，请确认后保存'))
  }
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(form, 'orderNo')
  }
  if (form.status === '草稿' && !form.warehouseId && form.items && form.items.length > 0) {
    const firstItem = form.items[0]
    if (firstItem && firstItem.materialId && materials.value && materials.value.length > 0) {
      const mat = materials.value.find(m => m.materialId === firstItem.materialId)
      if (mat && mat.defaultWarehouseId) {
        form.warehouseId = mat.defaultWarehouseId
      }
    }
    if (!form.warehouseId && form.customerId && customers.value && customers.value.length > 0) {
      const cust = customers.value.find(c => c.customerId === form.customerId)
      if (cust && cust.defaultDeliveryWarehouseId) {
        form.warehouseId = cust.defaultDeliveryWarehouseId
      }
    }
    if (!form.warehouseId && warehouses.value && warehouses.value.length > 0) {
      form.warehouseId = warehouses.value[0].stockId || warehouses.value[0].warehouseId
    }
  }
  // 监听新手教程的全局交互事件（如第12步模拟添加明细）
  window.addEventListener('onboarding:interaction-inject', onOnboardingInject)
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过物料后这里还是旧数据
useBaseDataRefresh(loadBaseData)

// 字段权限（设置中心-字段权限设置，page_key=sale_order）：被角色关闭的字段在表单中隐藏
const { isFieldVisible, isFieldRequired } = useFieldPermission('sale_order')
// 角色字段权限「必填」→ 界面打星号。
// 本页是手写 el-form-item，星号原先只来自手写的 required / 静态 rules，未接角色字段权限，
// 会出现「提交校验拦得住、界面却不提示」→ 用本工具补齐（口径与 utils/billValidation.js 完全同源）
useFieldRequiredMark('sale_order', '.sale-order-edit')

onBeforeUnmount(() => {
  window.removeEventListener('onboarding:interaction-inject', onOnboardingInject)
})
</script>

<style scoped lang="scss">
// ===== 客户字段行：下拉 + 快捷新建按钮（横排 / 垂直居中 / 间距12px）=====
.customer-field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;

  .field-input {
    flex: 1;
    min-width: 0;
  }

  .customer-add-btn {
    flex-shrink: 0;
    padding: 0 10px;
    height: 28px;
  }
}

.sale-order-edit {
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

  /* 按钮必须和「保存/提交」在同一条水平线上：显式 align-items:center。
     不写的话默认 stretch，而 BillActions 是 <div class="bill-actions"><el-space wrap> 两层包裹、
     PushDownButton 在部分场景渲染的是 el-dropdown，包裹层会把高度基准带偏 → 按钮上下错位。 */
  .header-right { display: flex; align-items: center; gap: 6px; }
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
  // 区块间距可调（设置中心 → 界面尺寸设置 → 区块间距）；未配置时 = 10px 原值
  margin-bottom: var(--erp-form-section-gap, 10px);

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
    // 区块间距可调（设置中心 → 界面尺寸设置 → 区块间距）；未配置时 = 10px 原值
    margin-bottom: var(--erp-form-section-gap, 10px);
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
    // 编辑页字段文字大小可调（设置中心 → 界面尺寸设置）；未配置时 = 12px 原值
    font-size: var(--erp-form-font-size, 12px);
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
    // 编辑页字段文字大小可调（设置中心 → 界面尺寸设置）；未配置时 = 12px 原值
    font-size: var(--erp-form-font-size, 12px);
    padding: 0 8px;
    .el-input__inner { font-size: var(--erp-form-font-size, 12px); line-height: 28px; }
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
// .table-wrap / .detail-subtable-header 由全局 styles/index.scss 统一定义
// el-table 自带横向滚动，不需要外层 overflow-x 容器
.detail-subtable-header {
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
  color: #909399;
  font-size: 11px;
  margin-left: 8px;
  /* 允许在面板宽度内换行折词，避免长值被 ellipsis 截断 */
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

@media (max-width: 1400px) {
  .form-groups { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .form-groups { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .sale-order-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .form-groups { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}
</style>

<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
/* 面板宽度：Element Plus 给 .el-select-dropdown 设的是【行内】min-width = 触发框宽度-2px，
   行内样式无法用普通 CSS 覆盖，因此在 popper 根节点 .material-select-popper 上设 min-width，
   面板会随容器一起变宽；max-width 防止窄屏溢出。 */
.material-select-popper {
  min-width: 640px;
  max-width: 90vw;
}
/* 选项内容：默认 white-space:nowrap + overflow:hidden 会把「自定义字段」内容直接截断成省略号，
   改为允许换行 + 高度自适应，保证内容不被隐藏（字段多时仍由 el-scrollbar 纵向滚动，max-height 274px） */
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
