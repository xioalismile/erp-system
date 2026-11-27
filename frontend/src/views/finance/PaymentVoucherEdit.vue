<template>
  <div class="payment-voucher-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/finance/payment')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Wallet /></el-icon>
            <span class="page-title">{{ isEdit ? '付款单' : '新增付款单' }}</span>
            <span v-if="form.paymentNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.paymentNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.paymentId)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="PAYMENT_VOUCHER"
        :supported-actions="supportedActions"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.paymentId"
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

    <!-- 基本信息卡片 -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.base }">
      <div class="card-header" @click="toggleCard('base')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.base }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("基本信息") }}</span>
        </div>
      </div>
      <div v-show="collapsed.base" class="card-body">
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="80px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('单号')">
                <el-input v-model="form.paymentNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('付款日期')" prop="paymentDate" required>
                <el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!editable" />
              </el-form-item>
              <el-form-item :label="$tf('单据流水')">
                <el-input v-model="form.documentNo" :placeholder="$tt('交易流水号')" class="field-input" :disabled="!editable" />
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
                <el-select v-model="form.supplierId" filterable :placeholder="$tt('选择供应商')" class="field-input" :disabled="!editable" @change="onSupplierChange">
                  <el-option v-for="s in withFallbackOption(suppliers, 'supplierId', 'supplierName', form)" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('供应商名称')">
                <el-input v-model="form.supplierName" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('付款人')">
                <el-select v-model="form.cashierId" filterable :placeholder="$tt('选择付款人')" class="field-input" :disabled="!editable" @change="onCashierChange">
                  <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('部门')">
                <el-select v-model="form.departmentId" filterable :placeholder="$tt('选择部门')" class="field-input" :disabled="!editable">
                  <el-option v-for="d in departments" :key="d.deptId" :label="d.deptName" :value="d.deptId" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 交易信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>交易信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('币种')" prop="currencyId" required>
                <el-select v-model="form.currencyId" :placeholder="$tt('选择币种')" class="field-input" :disabled="!editable || !foreignCurrencyEnabled" @change="onCurrencyChange">
                  <el-option v-for="c in currencies" :key="c.currencyId" :label="c.currencyCode" :value="c.currencyId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('汇率')">
                <el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" :min="0" class="field-input" :disabled="!editable || !foreignCurrencyEnabled" />
              </el-form-item>
              <el-form-item :label="$tf('结算方式')">
                <el-select v-model="form.settleModeId" filterable :placeholder="$tt('选择结算方式')" class="field-input" :disabled="!editable">
                  <el-option v-for="s in settleModes" :key="s.settleModeId" :label="s.settleModeName" :value="s.settleModeId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('付款账户')">
                <el-input v-model="form.bankAccount" :placeholder="$tt('银行账号')" class="field-input" :disabled="!editable" />
              </el-form-item>
            </div>
          </div>

          <!-- 其他 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all">
                <el-input v-model="form.remark" type="textarea" :rows="2" class="field-input" :disabled="!editable" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 付款明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="combinedPayRows"
      page-key="PaymentVoucherEdit_items"
      title="付款明细"
      col-setting-title="付款明细列设置"
      :columns="detailColumns"
      :readonly="!editable"
      :show-total-qty="false"
      :summary-items="summaryItems"
      @row-change="calcTotal"
      @remove="onPayRowRemove"
    >
      <template #actions>
        <el-button v-permission="perm('writeOff')" size="small" type="warning" plain @click="openPrePaymentPicker" :disabled="!editable">
          <el-icon><Wallet /></el-icon>预付冲抵
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!editable">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
        <el-button v-permission="perm('deleteRow')" size="small" type="danger" plain @click="clearItems" :disabled="!editable || !form.items.length">
          <el-icon><Delete /></el-icon>删除明细
        </el-button>
      </template>
      <template #cell-rowType="{ row }">
        <el-tag v-if="isPayPreRow(row)" size="small" type="warning" effect="plain">{{ $tt("预付冲抵") }}</el-tag>
        <el-tag v-else size="small" type="info" effect="plain">{{ $tt("现金/转账") }}</el-tag>
      </template>
      <template #cell-accountId="{ row }">
        <el-select v-if="!isPayPreRow(row)" v-model="row.accountId" filterable :placeholder="$tt('选择账户')" size="small" style="width:100%" :disabled="!editable" @change="onAccountChange(row)">
          <el-option v-for="a in accounts" :key="a.accountId" :label="accountLabel(a)" :value="a.accountId" />
        </el-select>
        <span v-else style="color:#999;">{{ $tt("预付冲抵无需账户") }}</span>
      </template>
      <template #cell-sourceBillNo="{ row }">
        <el-link v-if="isPayPreRow(row)" type="primary" :underline="false">{{ row.sourceBillNo || '-' }}</el-link>
        <span v-else>-</span>
      </template>
      <template #cell-bankAccount="{ row }">
        <el-input v-if="!isPayPreRow(row)" v-model="row.bankAccount" size="small" :disabled="!editable" :placeholder="$tt('银行账号')" />
        <span v-else>-</span>
      </template>
      <template #cell-amount="{ row }">
        <el-input-number
          v-if="!isPayPreRow(row)"
          v-model="row.amount"
          :controls="false" :min="0" :precision="2" style="width:100%" size="small"
          :disabled="!editable" @change="calcTotal"
        />
        <el-input-number
          v-else
          v-model="row.thisWriteOffAmount"
          :controls="false"
          :min="0"
          :max="Number(row.remainAmount || 0)"
          :precision="2" style="width:100%" size="small"
          :disabled="!editable" @change="calcTotal"
        />
      </template>
      <template #cell-remark="{ row }">
        <el-input v-model="row.remark" size="small" :disabled="!editable" :placeholder="$tt('备注')" />
      </template>
      <template #cell-baseAmount="{ row }">
        <span class="net-amount">¥{{ Number(row.baseAmount || 0).toFixed(2) }}</span>
      </template>
    </BillItemsTable>

    <!-- 核销明细卡片：应付单核销（预付款冲抵已移至付款明细-预付冲抵区） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.writeoff }">
      <div class="card-header" @click="toggleCard('writeoff')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.writeoff }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("核销明细") }}</span>
          <el-tag size="small" type="primary" effect="plain" class="mode-tag">{{ $tt("应付单核销") }}</el-tag>
          <span class="item-count">共 {{ form.apWriteOffItems.length }} 行</span>
        </div>
        <div class="header-right" @click.stop>
          <span class="hint-text">{{ $tt("预付款（预付冲抵）请在「付款明细-预付冲抵」操作；本卡仅核销应付单") }}</span>
        </div>
      </div>
      <div v-show="collapsed.writeoff" class="card-body no-padding">
        <!-- 实时余额条：付款金额 - 核销合计 = 待分配 -->
        <div class="balance-bar">
          <div class="balance-item">
            <span class="bl-label">{{ $tt("付款金额合计：") }}</span>
            <span class="bl-value">¥{{ totalPaymentAmount.toFixed(2) }}</span>
          </div>
          <div class="balance-item">
            <span class="bl-label">{{ $tt("预付冲抵：") }}</span>
            <span class="bl-value discount">¥{{ totalWriteOffAmount.toFixed(2) }}</span>
          </div>
          <div class="balance-item">
            <span class="bl-label">{{ $tt("应付单核销：") }}</span>
            <span class="bl-value discount">¥{{ totalApWriteOffAmount.toFixed(2) }}</span>
          </div>
          <div class="balance-item" :class="{ warn: remainingAmount < 0 }">
            <span class="bl-label">{{ $tt("待分配（转预付）：") }}</span>
            <span class="bl-value" :class="{ over: remainingAmount < 0 }">¥{{ remainingAmount.toFixed(2) }}</span>
          </div>
        </div>

        <el-tabs v-model="activeWriteoffTab" class="writeoff-tabs">
          <!-- Tab 1 说明：预付款核销已迁至「付款明细-预付冲抵」区——预付款单用于向下冲销应付，不再作为核销来源 -->

          <!-- Tab 2: 应付单核销 -->
          <el-tab-pane :label="$tf('📋 应付单核销')" name="ap">
            <div class="tab-toolbar">
              <el-button v-permission="perm('pick')" size="small" type="primary" @click="openApPicker" :disabled="!editable">
                <el-icon><Search /></el-icon>选择应付单
              </el-button>
              <span v-if="!form.supplierId" class="hint-text">{{ $tt("选择应付单后自动带出供应商") }}</span>
            </div>
            <div class="detail-list">
              <div v-if="form.apWriteOffItems.length === 0" class="empty-state">
                <el-empty :description="$tt('请选择应付单以添加核销明细')" :image-size="60" />
              </div>
              <el-table v-else :data="form.apWriteOffItems" class="compact-table" size="small" border show-summary :summary-method="apSummaryMethod">
                <el-table-column type="index" label="#" width="50" />
                <el-table-column :label="$tf('应付单号')" prop="apNo" min-width="150" />
                <el-table-column :label="$tf('单据日期')" prop="apDate" width="110" />
                <el-table-column :label="$tf('来源单据')" prop="sourceBillNo" min-width="150">
                  <template #default="{ row }">
                    <span>{{ row.sourceBillNo || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('应付金额')" width="120" align="right">
                  <template #default="{ row }">
                    <span class="amount-text">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('已付金额')" width="120" align="right">
                  <template #default="{ row }">
                    <span class="used-text">¥{{ Number(row.paidAmount || 0).toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('未付金额')" width="120" align="right">
                  <template #default="{ row }">
                    <span class="net-amount">¥{{ Number(row.balanceAmount || 0).toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('本次付款金额')" width="150">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.thisPayAmount"
                      :controls="false"
                      :min="0"
                      :max="Number(row.balanceAmount || 0)"
                      :precision="2"
                      style="width:100%"
                      size="small"
                      :disabled="!editable || row.settledQty === '已冲销'"
                      @change="calcTotal"
                    />
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('是否结案')" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isClosed" size="small" type="success">{{ $tt("已结案") }}</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('冲销状态')" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.settledQty === '已冲销'" size="small" type="success">{{ $tt("已冲销") }}</el-tag>
                    <el-tag v-else size="small" type="info" effect="plain">{{ $tt("未冲销") }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('余数不收')" width="90" align="center">
                  <template #default="{ row }">
                    <el-checkbox
                      :model-value="row.waiveRemainder === 1"
                      :disabled="!editable || row.settledQty === '已冲销'"
                      @change="onWaiveChange(row, $event)"
                    />
                  </template>
                </el-table-column>
                <el-table-column :label="$tf('操作')" width="60" align="center" fixed="right">
                  <template #default="{ $index }">
                    <el-button v-permission="perm('deleteRow')" text type="danger" size="small" @click="form.apWriteOffItems.splice($index, 1); calcTotal()" :disabled="!editable">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 金额汇总 -->
    <div class="summary-area">
      <div class="summary-block">
        <div class="block-title">{{ $tt("金额合计") }}</div>
        <div class="sum-line">
          <span class="label">{{ $tt("付款金额合计：") }}</span>
          <span class="value">¥{{ totalPaymentAmount.toFixed(2) }}</span>
        </div>
        <div class="sum-line">
          <span class="label">{{ $tt("预付款核销合计：") }}</span>
          <span class="value discount">¥{{ totalWriteOffAmount.toFixed(2) }}</span>
        </div>
        <div class="sum-line">
          <span class="label">{{ $tt("应付单核销合计：") }}</span>
          <span class="value discount">¥{{ totalApWriteOffAmount.toFixed(2) }}</span>
        </div>
        <div class="sum-line total">
          <span class="label">{{ $tt("实际付款（转入预付）：") }}</span>
          <span class="value">¥{{ actualPaymentAmount.toFixed(2) }}</span>
        </div>
      </div>
    </div>

    <!-- 自定义字段卡片（设置中心-自定义字段 cash_payment 实体） -->
    <div class="collapsible-card" v-for="section in headerCustomSections" :key="section.title" v-show="headerCustomSections.length > 0">
      <div class="card-header">
        <div class="header-left">
          <el-icon class="collapse-icon active"><ArrowRight /></el-icon>
          <span class="card-title">{{ section.title }}</span>
        </div>
      </div>
      <div class="card-body">
        <el-form label-width="110px" class="custom-field-form">
          <el-row :gutter="16">
            <el-col :span="8" v-for="field in section.fields" :key="field.prop">
              <el-form-item :label="$tf(field.label)" :required="field.required || isFieldRequired(field.prop)">
                <el-input v-if="field.type === 'input'" v-model="form[field.prop]" :placeholder="field.placeholder || '请输入'" :disabled="!editable" />
                <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" :precision="field.precision" :min="0" :step="0.1" controls-position="right" style="width:100%" :disabled="!editable" />
                <el-input v-else-if="field.type === 'textarea'" v-model="form[field.prop]" type="textarea" :rows="2" :placeholder="field.placeholder || '请输入'" :disabled="!editable" />
                <el-select v-else-if="field.type === 'select'" v-model="form[field.prop]" :placeholder="$tt('请选择')" style="width:100%" :disabled="!editable">
                  <el-option v-for="opt in (field.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                </el-select>
                <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.prop]" type="date" value-format="YYYY-MM-DD" :placeholder="$tt('选择日期')" style="width:100%" :disabled="!editable" />
                <el-input v-else v-model="form[field.prop]" :placeholder="field.placeholder || '请输入'" :disabled="!editable" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </div>

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
          type="payment-voucher"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PAYMENT_VOUCHER" :bill-id="form.paymentId" :status="form.status" />

    <!-- 预付款选择弹窗 -->
    <el-dialog v-model="prePaymentPickerVisible" :title="$tf('选择预付款单')" width="900px" :close-on-click-modal="false">
      <div class="picker-toolbar">
        <el-input v-model="prePaymentKeyword" :placeholder="$tt('输入单号搜索')" clearable style="width:280px;" @keyup.enter="loadPrePayments">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button v-permission="perm('query')" type="primary" @click="loadPrePayments">{{ $tt("搜索") }}</el-button>
        <span class="picker-tip">{{ $tt("仅显示当前供应商已生效且未核销完的预付款单") }}</span>
      </div>
      <el-table :data="prePaymentList" height="380" border @selection-change="onPrePaymentSelectionChange">
        <el-table-column type="selection" width="50" :selectable="isPrePaymentSelectable" />
        <el-table-column prop="paymentNo" :label="$tf('单号')" width="160" />
        <el-table-column prop="paymentDate" :label="$tf('单据日期')" width="120" />
        <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="140" />
        <el-table-column :label="$tf('金额')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('已核销')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.usedAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('未核销')" width="120" align="right">
          <template #default="{ row }">
            <span style="color:#6366f1;font-weight:600;">¥{{ Number(row.remainAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="prePaymentPage.current"
        v-model:page-size="prePaymentPage.size"
        :total="prePaymentPage.total"
        layout="total, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end;display:flex;"
        @current-change="loadPrePayments"
      />
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="prePaymentPickerVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" :disabled="prePaymentSelected.length === 0" @click="confirmPrePaymentPick">
          确认选择（{{ prePaymentSelected.length }} 张）
        </el-button>
      </template>
    </el-dialog>

    <!-- 应付单选择弹窗 -->
    <el-dialog v-model="apPickerVisible" :title="`选择应付单 - ${form.supplierName || ''}`" width="1000px" :close-on-click-modal="false">
      <div class="picker-toolbar">
        <el-input v-model="apKeyword" :placeholder="$tt('单号/来源单据')" clearable style="width:280px;" @keyup.enter="loadApList">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button v-permission="perm('query')" type="primary" @click="loadApList">{{ $tt("搜索") }}</el-button>
        <span class="picker-tip">仅显示当前供应商未结清（余额>{{ $tt("0）的应付单") }}</span>
      </div>
      <el-table :data="apList" height="420" border row-key="apId" @selection-change="onApSelectionChange">
        <el-table-column type="selection" width="50" :selectable="isApSelectable" />
        <el-table-column prop="apNo" :label="$tf('应付单号')" width="160" />
        <el-table-column prop="apDate" :label="$tf('单据日期')" width="110" />
        <el-table-column prop="sourceBillNo" :label="$tf('来源单据')" min-width="150">
          <template #default="{ row }">{{ row.sourceBillNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" :label="$tf('供应商')" min-width="140" />
        <el-table-column :label="$tf('应付金额')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('已付金额')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.paidAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('未付金额')" width="120" align="right">
          <template #default="{ row }">
            <span style="color:#6366f1;font-weight:600;">¥{{ Number(row.balanceAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="apPage.current"
        v-model:page-size="apPage.size"
        :total="apPage.total"
        layout="total, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end;display:flex;"
        @current-change="loadApList"
      />
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="apPickerVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="primary" :disabled="apSelected.length === 0" @click="confirmApPick">
          确认选择（{{ apSelected.length }} 张）
        </el-button>
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
    <PrintPreviewDialog v-model="printVisible" bill-type="PAYMENT_VOUCHER" :bill-id="form.paymentId" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="付款单" :bill-no="form.paymentNo || ''" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.paymentId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PAYMENT_VOUCHER" :bill-id="form.paymentId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.paymentId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PAYMENT_VOUCHER" :bill-id="form.paymentId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>

  </template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Wallet, Collection, Plus, Delete, Search, Printer,
  ArrowRight, Check, TopRight, Close, CircleCheck, Clock, Memo
} from '@element-plus/icons-vue'
import { listPayment, getVoucher, savePayment, confirmVoucher, submitVoucher, rejectPayment, reverseApprovePayment, voidPayment, unvoidPayment, closeOrderPayment, reopenPayment, deletePayment } from '@/api/finance/paymentVoucher'
import { listAllSupplier } from '@/api/basedata/supplier'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllSettle } from '@/api/basedata/settle'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllBankAccount } from '@/api/basedata/bankAccount'
import { listPurchasePrePayment } from '@/api/finance/purchasePrePayment'
import { pageAp } from '@/api/finance/accountsPayable'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import FileUploader from '@/components/FileUploader.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'

import * as BillStatus from '@/utils/bill-status'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { useFieldRequiredMark } from '@/utils/fieldRequiredMark'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import BillActions from '@/components/bill/BillActions.vue'
// 明细通用组件（卡片折叠/列设置/动态列/汇总 全站共用一套）
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues
} from '@/composables/useCustomField'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('finance:payment')

// ===== 表头级自定义字段（设置中心-自定义字段 cash_payment 实体）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

async function loadHeaderCustomFields() {
  try {
    const result = await loadCustomFieldDefinitions('cash_payment')
    headerCustomSections.value = result.sections
    headerCustomFieldMap.clear()
    result.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
    headerFormulaFields.value = result.formulaFields
    result.fieldMap.forEach((def, key) => {
      if (!(key in form)) form[key] = def.defaultValue || ''
    })
  } catch (e) {
    console.warn('[PaymentVoucherEdit] 加载自定义字段失败:', e)
  }
}

async function saveHeaderCustomFields() {
  if (headerCustomFieldMap.size > 0 && form.paymentId) {
    const values = {}
    for (const key of headerCustomFieldMap.keys()) {
      if (key in form) values[key] = form[key]
    }
    await saveCustomFieldValues('cash_payment', form.paymentId, values, headerFormulaFields.value)
  }
}

// 付款明细列定义（通用组件 BillItemsTable 消费；page-key 沿用 PaymentVoucherEdit_items 不变）：
// 列声明顺序 = 迁移前模板的渲染顺序（类型/账户/预付款单号/银行账号/金额/备注/付款本位币），
// 7 列全部由页面提供 #cell-<prop> 插槽渲染（双态：普通行 vs 预付冲抵行）
const detailColumns = [
  { prop: 'rowType', label: '类型', width: 110 },
  { prop: 'accountId', label: '账户', minWidth: 150 },
  { prop: 'sourceBillNo', label: '预付款单号', minWidth: 170 },
  { prop: 'bankAccount', label: '银行账号', minWidth: 140 },
  { prop: 'amount', label: '金额', width: 140 },
  { prop: 'remark', label: '备注', minWidth: 150 },
  { prop: 'baseAmount', label: '付款本位币', width: 130, align: 'right' }
]

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const changeRecordVisible = ref(false)

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('PAYMENT')

const STORAGE_KEY = 'payment-voucher-edit-collapsed'
const collapsed = reactive({
  base: true,
  items: true,
  writeoff: true,
  attach: true,
  audit: false
})

// 核销明细 Tab（仅 UI 展示状态：ap=应付单核销；预付款冲抵已移至「付款明细-预付冲抵」）
const activeWriteoffTab = ref('ap')

// 付款明细合并展示：现金/转账行(items) + 预付冲抵行(writeOffItems，仅预付来源)
const combinedPayRows = computed(() => [...form.items, ...form.writeOffItems])
function isPayPreRow(row) {
  return !!row && !!row.sourceBillId
}
function removePayRow(row) {
  if (isPayPreRow(row)) {
    const idx = form.writeOffItems.findIndex(r => r === row || (r.sourceBillId != null && r.sourceBillId === row.sourceBillId))
    if (idx > -1) form.writeOffItems.splice(idx, 1)
  } else {
    const idx = form.items.findIndex(r => r === row)
    if (idx > -1) form.items.splice(idx, 1)
  }
  calcTotal()
}

// 通用组件内置删除列按「合并行索引」触发删除。合并行 = [...form.items, ...form.writeOffItems]，
// 组件删除的是合并出来的临时副本（两张源数组 rows 长度不变）→ 用同一索引回推源行，再走迁移前的
// removePayRow（普通行删 form.items、预付冲抵行删 form.writeOffItems），删除行为与迁移前完全一致
function onPayRowRemove(index) {
  const row = index < form.items.length
    ? form.items[index]
    : form.writeOffItems[index - form.items.length]
  if (row) removePayRow(row)
}

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

// 付款明细卡片折叠由通用组件 v-model 驱动，这里监听后持久化（与页头卡片共用同一份 localStorage）
watch(() => collapsed.items, () => saveCollapsed())

// 下拉数据
const suppliers = ref([])
const employees = ref([])
const currencies = ref([])
const settleModes = ref([])
const departments = ref([])
const accounts = ref([])

// 表单数据
const form = reactive({
  paymentId: null,
  paymentNo: '',
  paymentDate: new Date().toISOString().slice(0, 10),
  supplierId: null,
  supplierName: '',
  cashierId: null,
  cashierName: '',
  departmentId: null,
  settleModeId: null,
  bankAccount: '',
  currencyId: null,
  currencyCode: '',
  exchangeRate: 1.0,
  totalAmount: 0,
  amount: 0,
  discountAmount: 0,
  paidAmount: 0,
  documentNo: '',
  remark: '',
  attachmentUrls: '',
  status: '草稿',
  approvalStatus: '',
  approvedBy: '',
  approvedAt: '',
  createdAt: '',
  createdBy: '',
  items: [],
  writeOffItems: [],
  // 应付单核销明细（转入来源单：应付单）
  apWriteOffItems: []
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const rules = {
  paymentDate: [{ required: true, message: tt('请选择付款日期'), trigger: 'change' }],
  supplierId: [{ required: true, message: tt('请选择供应商'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}

const editable = computed(() => form.status === '草稿' || !form.status)

// 全站商业口径总开关（设置中心：启用外币业务）→ 关闭时单据只用本位币、汇率锁 1
const { foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()

function statusType(s) {
  return { '草稿': 'info', '待确认': 'warning', '已确认': 'success', '已取消': 'danger' }[s] || 'info'
}

// ===== 下拉联动 =====
function onSupplierChange(id) {
  const s = suppliers.value.find(x => x.supplierId === id)
  if (s) {
    form.supplierName = s.supplierName
    if (s.currencyId) {
      form.currencyId = s.currencyId
      onCurrencyChange(s.currencyId)
    }
  }
  if (form.writeOffItems.length > 0 || form.apWriteOffItems.length > 0) {
    ElMessageBox.confirm(tt('切换供应商将清空已选预付款核销与应付单核销明细，是否继续？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    }).then(() => {
      form.writeOffItems = []
      form.apWriteOffItems = []
      calcTotal()
    }).catch(() => {})
  }
}

function onCashierChange(id) {
  const e = employees.value.find(x => x.employeeId === id)
  if (e) form.cashierName = e.employeeName
}

function onCurrencyChange(id) {
  const c = currencies.value.find(x => x.currencyId === id)
  if (c) {
    form.currencyCode = c.currencyCode
    if (c.exchangeRate) form.exchangeRate = c.exchangeRate
  }
  form.items.forEach(row => {
    row.baseAmount = Number((Number(row.amount || 0) * Number(form.exchangeRate || 1)).toFixed(2))
  })
}

// ===== 付款明细 =====
function addItem() {
  form.items.push({
    accountId: null,
    accountName: '',
    bankAccount: '',
    amount: 0,
    baseAmount: 0,
    remark: ''
  })
}

function clearItems() {
  form.items = []
  calcTotal()
}

// ===== 金额计算 =====
const totalPaymentAmount = computed(() => {
  return form.items.reduce((s, r) => s + Number(r.amount || 0), 0)
})

const totalWriteOffAmount = computed(() => {
  return form.writeOffItems.reduce((s, r) => s + Number(r.thisWriteOffAmount || 0), 0)
})

// 应付单核销合计：本次付款金额合计
const totalApWriteOffAmount = computed(() => {
  return form.apWriteOffItems.reduce((s, r) => s + Number(r.thisPayAmount || 0), 0)
})

// 应付单应付合计
const totalApAmount = computed(() => {
  return form.apWriteOffItems.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
})

// 应付单已付合计
const totalApPaidAmount = computed(() => {
  return form.apWriteOffItems.reduce((s, r) => s + Number(r.paidAmount || 0), 0)
})

const actualPaymentAmount = computed(() => {
  return Math.max(0, totalPaymentAmount.value - totalWriteOffAmount.value)
})

// 待分配金额 = 付款金额合计 - 预付款核销 - 应付单核销（纯展示计算，余额转预付）
const remainingAmount = computed(() => {
  return totalPaymentAmount.value - totalWriteOffAmount.value - totalApWriteOffAmount.value
})

// 付款明细卡片汇总区（通用组件 BillItemsTable 按此渲染）：数值/标签全部沿用本页既有 computed 口径；
// 原页表格下方没有汇总区，这里仅把「本卡片两行数据」的金额口径按统一版式呈现（页头下方「金额合计」块保持原样不动）
const summaryItems = computed(() => [
  { label: '付款金额合计：', value: `¥${totalPaymentAmount.value.toFixed(2)}` },
  { label: '预付冲抵合计：', value: `¥${totalWriteOffAmount.value.toFixed(2)}`, class: 'discount' },
  { label: '实际付款（转入预付）：', value: `¥${actualPaymentAmount.value.toFixed(2)}`, total: true }
])

// ===== 余数不收 =====
// 余数不收勾选：该来源单剩余金额不再付款（结案）
function onWaiveChange(row, val) {
  row.waiveRemainder = val ? 1 : 0
}

function calcTotal() {
  form.items.forEach(row => {
    row.baseAmount = Number((Number(row.amount || 0) * Number(form.exchangeRate || 1)).toFixed(2))
  })
  form.totalAmount = totalPaymentAmount.value.toFixed(2)
  form.amount = totalPaymentAmount.value.toFixed(2)
  form.paidAmount = actualPaymentAmount.value.toFixed(2)
  form.writeOffItems.forEach(row => {
    const max = Number(row.remainAmount || 0)
    if (Number(row.thisWriteOffAmount || 0) > max) {
      row.thisWriteOffAmount = max
    }
    row.isClosed = Number(row.thisWriteOffAmount || 0) >= max && max > 0 ? 1 : 0
  })
  // 校验应付单本次付款金额不能超过未付金额，并重算结案标识
  form.apWriteOffItems.forEach(row => {
    const max = Number(row.balanceAmount || 0)
    if (Number(row.thisPayAmount || 0) > max) {
      row.thisPayAmount = max
    }
    row.isClosed = Number(row.thisPayAmount || 0) >= max && max > 0 ? 1 : 0
  })
}

// ===== 预付款核销：弹窗选择 =====
const prePaymentPickerVisible = ref(false)
const prePaymentKeyword = ref('')
const prePaymentList = ref([])
const prePaymentSelected = ref([])
const prePaymentPage = reactive({ current: 1, size: 20, total: 0 })

async function openPrePaymentPicker() {
  prePaymentPickerVisible.value = true
  await loadPrePayments()
}

async function loadPrePayments() {
  try {
    const params = {
      current: prePaymentPage.current,
      size: prePaymentPage.size,
      keyword: prePaymentKeyword.value || undefined,
      status: 2 // 2 = 已生效
    }
    if (form.supplierId) params.supplierId = form.supplierId
    const res = await listPurchasePrePayment(params)
    const all = res.records || []
    prePaymentList.value = all.filter(r => Number(r.remainAmount || 0) > 0)
    prePaymentPage.total = prePaymentList.value.length
  } catch (e) {
    ElMessage.error(e.message || tt('查询预付款单失败'))
  }
}

function isPrePaymentSelectable(row) {
  if (Number(row.remainAmount || 0) <= 0) return false
  if (prePaymentSelected.value.length === 0) return true
  return row.supplierId === prePaymentSelected.value[0].supplierId
}

function onPrePaymentSelectionChange(rows) {
  prePaymentSelected.value = rows
}

function confirmPrePaymentPick() {
  if (prePaymentSelected.value.length === 0) {
    ElMessage.warning(tt('请至少选择一张预付款单'))
    return
  }
  // 同一次只能选同一供应商
  const firstSid = prePaymentSelected.value[0].supplierId
  if (prePaymentSelected.value.some(p => p.supplierId !== firstSid)) {
    ElMessage.warning(tt('一次只能选择同一供应商的预付款单，请重新选择'))
    return
  }
  // 自动回填供应商到表头（含币种联动）
  if (firstSid && form.supplierId !== firstSid) {
    form.supplierId = firstSid
    form.supplierName = prePaymentSelected.value[0].supplierName || ''
    const s = suppliers.value.find(x => x.supplierId === firstSid)
    if (s && s.currencyId) {
      form.currencyId = s.currencyId
      onCurrencyChange(s.currencyId)
    }
  }
  prePaymentSelected.value.forEach(p => {
    if (form.writeOffItems.some(w => w.sourceBillId === p.id)) return
    form.writeOffItems.push({
      sourceBillId: p.id,
      billType: '采购预付款单',
      paymentDate: p.paymentDate,
      sourceBillNo: p.paymentNo,
      totalAmount: Number(p.amount || 0),
      usedAmount: Number(p.usedAmount || 0),
      remainAmount: Number(p.remainAmount || 0),
      thisWriteOffAmount: Number(p.remainAmount || 0),
      isClosed: 0
    })
  })
  calcTotal()
  prePaymentPickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${prePaymentSelected.value.length}${tt(" 张预付款单")}`)
}

// ===== 应付单核销：弹窗选择（转入来源单：应付单） =====
const apPickerVisible = ref(false)
const apKeyword = ref('')
const apList = ref([])
const apSelected = ref([])
const apPage = reactive({ current: 1, size: 100, total: 0 })

async function openApPicker() {
  apPickerVisible.value = true
  await loadApList()
}

async function loadApList() {
  try {
    // 调用应付账款列表 API，过滤当前供应商未结清的应付单（表头已选供应商则只查该供应商，未选则查全部）
    const params = {
      current: apPage.current,
      size: apPage.size,
      keyword: apKeyword.value || undefined
    }
    if (form.supplierId) params.supplierId = form.supplierId
    const res = await pageAp(params)
    const all = res.records || []
    // 仅展示未结清（balanceAmount > 0）的应付单
    apList.value = all.filter(r => Number(r.balanceAmount || 0) > 0)
    apPage.total = apList.value.length
  } catch (e) {
    apList.value = []
    apPage.total = 0
    ElMessage.error(e.message || tt('查询应付单失败'))
  }
}

function onApSelectionChange(rows) {
  apSelected.value = rows
}

function confirmApPick() {
  if (apSelected.value.length === 0) {
    ElMessage.warning(tt('请至少选择一张应付单'))
    return
  }
  // 同一次只能选同一供应商
  const firstSid = apSelected.value[0].supplierId
  if (apSelected.value.some(a => a.supplierId !== firstSid)) {
    ElMessage.warning(tt('一次只能选择同一供应商的应付单，请重新选择'))
    return
  }
  // 自动回填供应商到表头（含币种联动）
  if (firstSid && form.supplierId !== firstSid) {
    form.supplierId = firstSid
    form.supplierName = apSelected.value[0].supplierName || ''
    const s = suppliers.value.find(x => x.supplierId === firstSid)
    if (s && s.currencyId) {
      form.currencyId = s.currencyId
      onCurrencyChange(s.currencyId)
    }
  }
  apSelected.value.forEach(a => {
    // 避免重复添加
    if (form.apWriteOffItems.some(w => w.apId === a.apId)) return
    form.apWriteOffItems.push({
      apId: a.apId,
      apNo: a.apNo,
      apDate: a.apDate,
      sourceBillNo: a.sourceBillNo || '',
      sourceBillType: a.sourceBillType || '',
      totalAmount: Number(a.totalAmount || 0),
      paidAmount: Number(a.paidAmount || 0),
      balanceAmount: Number(a.balanceAmount || 0),
      // 默认本次付款金额 = 未付金额
      thisPayAmount: Number(a.balanceAmount || 0),
      isClosed: 0
    })
  })
  calcTotal()
  apPickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${apSelected.value.length}${tt(" 张应付单")}`)
}

// 应付单核销合计行：应付合计、已付合计、本次付款合计
function apSummaryMethod({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    const prop = col.property
    if (prop === 'totalAmount') {
      const sum = data.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
      sums[idx] = `¥${sum.toFixed(2)}`
    } else if (prop === 'paidAmount') {
      const sum = data.reduce((s, r) => s + Number(r.paidAmount || 0), 0)
      sums[idx] = `¥${sum.toFixed(2)}`
    } else if (prop === 'thisPayAmount') {
      const sum = data.reduce((s, r) => s + Number(r.thisPayAmount || 0), 0)
      sums[idx] = `¥${sum.toFixed(2)}`
    } else {
      sums[idx] = ''
    }
  })
  return sums
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

// ===== 保存（保存即草稿；是否走审批由「提交」时校验）=====
async function onSave(silent, opts = {}) {
  flushInputNumbers()
  // 宽松草稿保存：不校验必填，允许空供应商/无明细（可后续补全再提交）

  calcTotal()
  // 合并核销明细：预付款核销（预付款单）+ 应付单核销（应付单），生效时由后端统一核销
  const writeoffDetails = [
    ...form.writeOffItems
      .filter(w => Number(w.thisWriteOffAmount || 0) > 0 || Number(w.waiveRemainder || 0) === 1)
      .map(w => ({
        sourceType: '预付款单',
        sourceId: w.sourceBillId,
        sourceNo: w.sourceBillNo,
        sourceAmount: Number(w.totalAmount || 0),
        usedAmount: Number(w.usedAmount || 0),
        writeoffAmount: Number(w.thisWriteOffAmount || 0),
        settledQty: w.settledQty || '未冲销',
        waiveRemainder: Number(w.waiveRemainder || 0)
      })),
    ...form.apWriteOffItems
      .filter(a => Number(a.thisPayAmount || 0) > 0 || Number(a.waiveRemainder || 0) === 1)
      .map(a => ({
        sourceType: '应付单',
        sourceId: a.apId,
        sourceNo: a.apNo,
        sourceAmount: Number(a.totalAmount || 0),
        usedAmount: Number(a.paidAmount || 0),
        writeoffAmount: Number(a.thisPayAmount || 0),
        settledQty: a.settledQty || '未冲销',
        waiveRemainder: Number(a.waiveRemainder || 0)
      }))
  ]
  const payload = { ...form, status: '草稿', writeOffItems: writeoffDetails }
  try {
    const res = await savePayment(payload)
    if (res && res.paymentId) form.paymentId = res.paymentId
    if (res && res.paymentNo) form.paymentNo = res.paymentNo
    form.status = '草稿'
    // 新增态保存后必须把新单 id 写回地址栏：否则地址栏仍停在 /new，用户按 F5 会丢掉刚保存的草稿。
    // skipRouteSync：提交链路里先跳过（写回会改变布局 keep-alive key(fullPath) → 组件实例重建），由 onSubmit 的 finally 统一写回。
    if (!opts.skipRouteSync && !route.params.id && form.paymentId) {
      try { await router.replace({ name: 'FinancePaymentEdit', params: { id: String(form.paymentId) } }) } catch (_) {}
    }
    // 保存表头级自定义字段值
    await saveHeaderCustomFields().catch(() => {})
    if (!silent) ElMessage.success(tt('保存成功（草稿）'))
    return true
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
    return false
  }
}

// ===== 提交（提交时校验审批开关：开启→待审核；未开启→直接生效）=====
async function onSubmit() {
  flushInputNumbers()
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  // skipRouteSync：提交链路里先跳过写回地址栏，避免 keep-alive key 变化重建组件实例（见本函数 finally）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !form.paymentId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PAYMENT_VOUCHER', billLabel: '付款单', billNo: form.paymentNo })
    await submitVoucher(form.paymentId)
    form.status = needApproval ? '待审核' : '已确认'
    ElMessage.success(submitSuccessMessage(needApproval))
    if (route.params.id) await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时实例重建无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.paymentId) {
      try { await router.replace({ name: 'FinancePaymentEdit', params: { id: String(form.paymentId) } }) } catch (_) {}
    }
  }
}



// ===== 取消 =====
async function onCancel() {
  try {
    await ElMessageBox.confirm(tt('确认取消该付款单？取消后不可恢复'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('返回'),
      type: 'warning'
    })
    await savePayment({ ...form, status: '已取消' })
    form.status = '已取消'
    ElMessage.success(tt('已取消'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('取消失败'))
  }
}

// ===== 确认付款 =====
async function onConfirm() {
  if (!form.paymentId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认付款？确认后单据不可修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await confirmVoucher(form.paymentId)
    form.status = '已确认'
    ElMessage.success(tt('确认成功'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('确认失败'))
  }
}

// ===== 打印 =====
function handlePrint() {
  if (!form.paymentId) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}
const printVisible = ref(false)

// ===== 加载详情 =====
async function loadDetail() {
  if (!route.params.id) {
    autoFillDocCodeOnCreate(form, 'paymentNo')
    // 新建模式也加载自定义字段定义（分区渲染）
    await loadHeaderCustomFields()
    return
  }
  try {
    const res = await getVoucher(route.params.id)
    const rawWriteOffs = Array.isArray(res.writeOffItems) ? res.writeOffItems : []
    Object.assign(form, res)
    if (!form.items) form.items = []
    // 后端核销明细（bill_writeoff_detail）按来源类型还原为两个 UI 载体：
    // 预付款单 → 付款明细-预付冲抵行；应付单 → 核销明细-应付单核销行
    form.writeOffItems = rawWriteOffs
      .filter(w => w.sourceType === '预付款单')
      .map(w => {
        const total = Number(w.sourceAmount || 0)
        const used = Number(w.usedAmount || 0)
        return {
          sourceBillId: w.sourceId,
          sourceBillNo: w.sourceNo,
          billType: '采购预付款单',
          totalAmount: total,
          usedAmount: used,
          remainAmount: Math.max(0, total - used),
          thisWriteOffAmount: Number(w.writeoffAmount || 0),
          settledQty: w.settledQty || '未冲销',
          waiveRemainder: Number(w.waiveRemainder || 0)
        }
      })
    form.apWriteOffItems = rawWriteOffs
      .filter(w => w.sourceType === '应付单')
      .map(w => {
        const total = Number(w.sourceAmount || 0)
        const paid = Number(w.usedAmount || 0)
        return {
          apId: w.sourceId,
          apNo: w.sourceNo,
          totalAmount: total,
          paidAmount: paid,
          balanceAmount: Math.max(0, total - paid),
          thisPayAmount: Number(w.writeoffAmount || 0),
          settledQty: w.settledQty || '未冲销',
          waiveRemainder: Number(w.waiveRemainder || 0)
        }
      })
    // 回填表头级自定义字段值
    await loadHeaderCustomFields()
    if (form.paymentId) {
      const cfValues = await loadCustomFieldValues('cash_payment', form.paymentId).catch(() => ({}))
      Object.assign(form, cfValues)
    }
    calcTotal()
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  }
}

// ===== 统一单据操作（审核/驳回/作废/删除等）=====
const {
  supportedActions,  rejectDialog, rejectReason, doReject,
  voidDialog, voidReason, doVoid,
  onApprove, handleReject, handleReverseApprove,
  handleClose, handleReopen, handleVoid, handleUnvoid,
  handleDelete, handleCopy
} = useBillActions({
  form,
  getId: () => form.paymentId,
  api: {
    approve: confirmVoucher,
    reject: rejectPayment,
    reverseApprove: reverseApprovePayment,
    close: closeOrderPayment,
    reopen: reopenPayment,
    void: voidPayment,
    unvoid: unvoidPayment,
    delete: deletePayment
  },
  reload: loadDetail,
  router,
  listPath: '/finance/payment',
  newPath: '/finance/payment/new',
  billLabel: '付款单'
})

// 账户下拉显示：账户名称（开户银行），无银行时仅显示名称；数据源=账户维护(bank_account)
function accountLabel(a) {
  if (!a) return ''
  return a.bankName ? `${a.accountName}（${a.bankName}）` : (a.accountName || '')
}

// 明细选中账户后自动带出账户编码到"银行账号"列（可手动修改覆盖）
function onAccountChange(row) {
  if (!row) return
  const acc = accounts.value.find(a => a.accountId === row.accountId)
  row.bankAccount = acc ? (acc.accountNo || '') : ''
}

// 加载下拉基础数据——独立函数，供 onMounted 和切回标签页时复用
  async function loadBaseData() {
  try {
      const [supRes, empRes, curRes, stlRes, depRes, accRes] = await Promise.all([
        listAllSupplier(),
        listAllEmployee(),
        listAllCurrency(),
        listAllSettle(),
        listAllDepartment(),
        listAllBankAccount()
      ])
      suppliers.value = supRes || []
      employees.value = empRes || []
      currencies.value = curRes || []
      settleModes.value = stlRes || []
      departments.value = depRes || []
      accounts.value = accRes || []
      // 默认币种：外币业务关闭时新建单据只用本位币
      if (!form.currencyId && currencies.value.length > 0) {
        const base = currencies.value.find(c => c.isBaseCurrency === 1 || c.isBaseCurrency === true) || currencies.value[0]
        form.currencyId = (foreignCurrencyEnabled.value ? currencies.value[0] : base).currencyId
        onCurrencyChange(form.currencyId)
      }
    } catch (e) {
      console.warn('下拉数据加载失败:', e)
    }
}

// ===== 复制功能（URL 带 ?copy=id 时自动带入源单数据）=====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getVoucher,
  form,
  idField: 'paymentId',
  noField: 'paymentNo',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'cash_payment' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('cash_payment')
useFieldRequiredMark('cash_payment', '.payment-voucher-edit')

onMounted(async () => {
  // 全站商业口径总开关（设置中心：启用外币业务）——最先装载，失败不阻断页面
  await ensureCommercialSwitches()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PAYMENT_VOUCHER')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  loadCollapsed()
  await loadBaseData()
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.payment-voucher-edit {
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
      align-items: center;
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

    .hint-text {
      font-size: 11px;
      color: #c0c4cc;
      margin-left: 4px;
    }
  }

  .card-body {
    padding: 12px 14px;
    &.no-padding { padding: 0; }
  }
}

// ===== 基本信息 =====
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

.amount-text { color: #1f2937; font-weight: 600; font-variant-numeric: tabular-nums; }
.used-text { color: #6b7280; font-variant-numeric: tabular-nums; }
.net-amount { color: #2563eb; font-weight: 700; font-variant-numeric: tabular-nums; }
.discount-text { color: #e6a23c; }

// ===== 金额汇总区 =====
.summary-area {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 10px;
}

.summary-block {
  flex: 1;
  min-width: 280px;

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
  align-items: center;

  .picker-tip {
    font-size: 12px;
    color: #909399;
    margin-left: auto;
  }
}

@media (max-width: 1400px) {
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .payment-voucher-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}

// ===== 核销明细卡片（Tab 合并版） =====
.mode-tag {
  margin-left: 4px;
}

.writeoff-tabs {
  padding: 0 14px;

  :deep(.el-tabs__item) {
    font-size: 13px;
    font-weight: 500;
    height: 36px;
    line-height: 36px;
  }

  :deep(.el-tabs__nav-wrap::after) {
    background-color: #e4e7ed;
  }
}

.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 10px;
}

.balance-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;

  .balance-item {
    display: flex;
    align-items: baseline;
    gap: 6px;

    .bl-label {
      color: #4b5563;
      font-size: 13px;
    }

    .bl-value {
      color: #1f2937;
      font-size: 16px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      letter-spacing: 0.2px;

      &.discount { color: #b45309; }
      &.over { color: #dc2626; }
    }

    &.warn {
      .bl-label { color: #dc2626; }
      .bl-value { color: #dc2626; }
    }
  }
}

.detail-list {
  .empty-state {
    padding: 12px 0;
  }

  .list-summary {
    display: flex;
    justify-content: flex-end;
    padding: 8px 12px;
    border-top: 1px solid #ebeef5;
    font-size: 12px;
    color: #606266;

    strong {
      color: #409eff;
      font-size: 14px;
    }
  }
}
</style>
