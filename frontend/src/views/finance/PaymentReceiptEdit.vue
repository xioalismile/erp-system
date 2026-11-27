<template>
  <div class="payment-receipt-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/finance/receipt')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Wallet /></el-icon>
            <span class="page-title">{{ isEdit ? '收款单' : '新增收款单' }}</span>
            <span v-if="form.receiptNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ form.receiptNo }}
            </span>
            <el-tag v-if="form.status" size="small" :type="statusType(form.status)" effect="plain" class="status-tag">{{ $dt(form.status) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.receiptId)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <BillActions bill-type="PAYMENT_RECEIPT"
        :supported-actions="supportedActions"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.receiptId"
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
        <el-form :model="form" :rules="rules" ref="headerFormRef" label-width="80px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('单号')">
                <el-input v-model="form.receiptNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('收款日期')" prop="receiptDate" required>
                <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!editable" />
              </el-form-item>
              <el-form-item :label="$tf('单据流水')">
                <el-input v-model="form.documentNo" :placeholder="$tt('交易流水号')" class="field-input" :disabled="!editable" />
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
                <el-select v-model="form.customerId" filterable :placeholder="$tt('选择客户')" class="field-input" :disabled="!editable" @change="onCustomerChange">
                  <el-option v-for="c in withFallbackOption(customers, 'customerId', 'customerName', form)" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tf('客户名称')">
                <el-input v-model="form.customerName" readonly class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('收款人')">
                <el-select v-model="form.cashierId" filterable :placeholder="$tt('选择收款人')" class="field-input" :disabled="!editable" @change="onCashierChange">
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
              <el-form-item :label="$tf('收款账户')">
                <el-input v-model="form.bankAccount" :placeholder="$tt('银行账号')" class="field-input" :disabled="!editable" />
              </el-form-item>
            </div>
          </div>

          <!-- 销售提成：全部字段在核销明细行上（比例/特殊服务费逐行可改），表头不放任何提成字段（2026-10-16 用户拍板） -->

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

    <!-- 收款明细卡片（通用组件 BillItemsTable：卡片/列设置/动态列/汇总 全站共用一套） -->
    <BillItemsTable
      v-model:collapsed="collapsed.items"
      :items="combinedDetailRows"
      page-key="PaymentReceiptEdit_items"
      title="① 收款明细"
      col-setting-title="收款明细列设置"
      :columns="detailColumns"
      :readonly="!editable"
      :show-total-qty="false"
      :summary-items="summaryItems"
      @row-change="calcTotal"
      @remove="onDetailRowRemove"
    >
      <template #actions>
        <el-button v-permission="perm('writeOff')" size="small" type="warning" plain @click="openPreSourcePicker" :disabled="!editable">
          <el-icon><Wallet /></el-icon>预收冲抵
        </el-button>
        <el-button v-permission="perm('addRow')" size="small" type="primary" @click="addItem" :disabled="!editable">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
        <el-button v-permission="perm('deleteRow')" size="small" type="danger" plain @click="clearItems" :disabled="!editable || !form.items.length">
          <el-icon><Delete /></el-icon>删除明细
        </el-button>
      </template>
      <template #cell-sourceNo="{ row }">
        <el-link v-if="isPreRow(row)" type="primary" :underline="false">{{ row.sourceNo || '-' }}</el-link>
        <span v-else></span>
      </template>
      <template #cell-accountId="{ row }">
        <el-select v-if="!isPreRow(row)" v-model="row.accountId" filterable :placeholder="$tt('选择账户')" size="small" style="width:100%" :disabled="!editable" @change="onAccountChange(row)">
          <el-option v-for="a in accounts" :key="a.accountId" :label="accountLabel(a)" :value="a.accountId" />
        </el-select>
        <span v-else style="color:#999;">{{ $tt("预收冲抵无需账户") }}</span>
      </template>
      <template #cell-bankAccount="{ row }">
        <el-input v-if="!isPreRow(row)" v-model="row.bankAccount" size="small" :disabled="!editable" :placeholder="$tt('银行账号')" />
        <span v-else>-</span>
      </template>
      <template #cell-amount="{ row }">
        <el-input-number
          v-if="!isPreRow(row)"
          v-model="row.amount"
          :controls="false" :min="0" :precision="2" style="width:100%" size="small"
          :disabled="!editable" @change="recalcAndAllocate"
        />
        <el-input-number
          v-else
          v-model="row.writeoffAmount"
          :controls="false"
          :min="0"
          :max="Number((Number(row.sourceAmount || 0) - Number(row.usedAmount || 0)).toFixed(2))"
          :precision="2" style="width:100%" size="small"
          :disabled="!editable" @change="recalcAndAllocate"
        />
      </template>
      <template #cell-remark="{ row }">
        <el-input v-model="row.remark" size="small" :disabled="!editable" :placeholder="$tt('备注')" />
      </template>
      <template #cell-baseAmount="{ row }">
        <span class="net-amount">¥{{ Number(row.baseAmount || 0).toFixed(2) }}</span>
      </template>
    </BillItemsTable>

    <!-- 核销明细卡片：唯一核销入口（设计图 v3 界面 2：选单按钮在标题栏右侧） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.writeoff }">
      <div class="card-header" @click="toggleCard('writeoff')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.writeoff }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("② 核销明细") }}</span>
          <span class="item-count">共 {{ allWriteOffRows.length }} 行</span>
        </div>
        <div class="header-actions" @click.stop>
          <el-button v-permission="perm('pick')" size="small" type="primary" @click="openSourcePicker" :disabled="!editable">
            <el-icon><Search /></el-icon>选择来源单
          </el-button>
        </div>
      </div>
      <div v-show="collapsed.writeoff" class="card-body no-padding">
        <!-- 核销明细列表（唯一列表：来源可为销售订单 / 销售出库单 / 销售发票；预收款单冲抵在「收款明细」表中） -->
        <div class="detail-list">
          <div v-if="allWriteOffRows.length === 0" class="empty-state">
            <el-empty :description="$tt('请选择来源单以添加核销明细')" :image-size="60" />
          </div>
          <el-table v-else :data="allWriteOffRows" class="compact-table" size="small" border>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column :label="$tf('单据类型')" width="110" prop="sourceType">
              <template #default="{ row }">
                <el-tag size="small" :type="row.sourceType === '预收款单' ? 'warning' : 'primary'" effect="plain">{{ row.sourceType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('来源单号')" min-width="160">
              <template #default="{ row }">
                <el-link type="primary" :underline="false">{{ row.sourceNo || '-' }}</el-link>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('货品')" min-width="110">
              <template #default="{ row }">{{ row.sourceItemName || '' }}</template>
            </el-table-column>
            <el-table-column :label="$tf('金额')" width="120" align="right">
              <template #default="{ row }">
                <span class="amount-text">¥{{ Number(row.sourceAmount || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('已核/已收')" width="120" align="right">
              <template #default="{ row }">
                <span class="used-text">¥{{ Number(row.usedAmount || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('剩余')" width="120" align="right">
              <template #default="{ row }">
                <span class="net-amount">¥{{ Number((row.sourceAmount || 0) - (row.usedAmount || 0)).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('本次核销金额')" width="150">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.writeoffAmount"
                  :controls="false"
                  :min="0"
                  :max="Number((row.sourceAmount || 0) - (row.usedAmount || 0))"
                  :precision="2"
                  style="width:100%"
                  size="small"
                  :disabled="!editable || row.settledQty === '已冲销'"
                  @change="calcTotal"
                />
              </template>
            </el-table-column>
            <!-- 销售提成（2026-10-16）：销售员可改；行不含税/行提成自动算 -->
            <el-table-column v-if="commissionEnabled" :label="$tf('销售员')" width="120">
              <template #default="{ row }">
                <el-select v-model="row.salerId" filterable clearable size="small" style="width:100%"
                  :disabled="!editable || row.settledQty === '已冲销'"
                  @change="(v) => { const e = employees.find(x => x.employeeId === v); row.salerName = e ? e.employeeName : ''; }"
                >
                  <el-option v-for="emp in employees" :key="emp.employeeId" :label="emp.employeeName" :value="emp.employeeId" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column v-if="commissionEnabled" :label="$tf('提成比例%')" width="110" align="right">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.commissionRate"
                  :controls="false" :min="0" :max="100" :precision="2"
                  style="width:100%" size="small"
                  :disabled="!editable || row.settledQty === '已冲销'"
                  @change="recalcCommissionRows"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="commissionEnabled" :label="$tf('特殊服务费')" width="120" align="right">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.specialServiceFee"
                  :controls="false" :min="0" :precision="2"
                  style="width:100%" size="small"
                  :disabled="!editable || row.settledQty === '已冲销'"
                  @change="recalcCommissionRows"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="commissionEnabled" :label="$tf('不含税金额')" width="130" align="right">
              <template #default="{ row }">
                <span class="used-text">¥{{ Number(row.taxExcludedAmount || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="commissionEnabled" :label="$tf('销售提成')" width="120" align="right">
              <template #default="{ row }">
                <span class="amount-text">¥{{ Number(row.commission || 0).toFixed(2) }}</span>
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
              <template #default="{ row }">
                <el-button v-permission="perm('deleteRow')"
                  text
                  type="danger"
                  size="small"
                  @click="removeWriteOffRow(row)"
                  :disabled="!editable"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="allWriteOffRows.length > 0" class="list-summary">
            <span>{{ $tt("核销合计：") }}<strong>¥{{ totalWriteOffAmount.toFixed(2) }}</strong></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 金额汇总 -->
    <div class="summary-area">
      <div class="summary-block">
        <div class="block-title">{{ $tt("金额合计") }}</div>
        <div class="sum-line">
          <span class="label">{{ $tt("收款金额合计（现金/转账）：") }}</span>
          <span class="value">¥{{ totalReceiptAmount.toFixed(2) }}</span>
        </div>
        <div class="sum-line">
          <span class="label">{{ $tt("预收款冲抵：") }}</span>
          <span class="value discount">¥{{ preWriteOffTotal.toFixed(2) }}</span>
        </div>
        <div class="sum-line">
          <span class="label">{{ $tt("核销合计：") }}</span>
          <span class="value discount">¥{{ totalWriteOffDemand.toFixed(2) }}</span>
        </div>
        <div class="sum-line total">
          <span class="label">{{ $tt("多收自动转预收：") }}</span>
          <span class="value">¥{{ overageAmount.toFixed(2) }}</span>
        </div>
      </div>
    </div>

    <!-- 自定义字段卡片（设置中心-自定义字段 cash_receipt 实体） -->
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
          type="payment-receipt"
          :disabled="!editable"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PAYMENT_RECEIPT" :bill-id="form.receiptId" :status="form.status" />

    <!-- 选择来源单弹窗：操作按钮全在标题栏右侧；类型页签独立一行；弹窗内只勾选不填金额（金额回写到核销明细再填） -->
    <el-dialog v-model="sourcePickerVisible" width="min(1180px, 96vw)" :close-on-click-modal="false" class="source-picker-dialog">
      <template #header>
        <div class="picker-header">
          <span class="picker-title">{{ sourcePickerMode === 'pre' ? $tt('选择预收款单') : $tt('选择来源单') }}</span>
          <div class="picker-header-actions">
            <span v-if="form.customerId" class="picker-customer">{{ $tt("已选客户：") }}{{ form.customerName }}</span>
            <el-button v-if="isDetailPick" v-permission="perm('columnSetting')" size="small" plain @click="pickerColumnSettingVisible = true">
              <el-icon><Setting /></el-icon>{{ $tt("列设置") }}
            </el-button>
            <el-button v-permission="perm('dlgCancel')" size="small" @click="sourcePickerVisible = false">{{ $tt("取消") }}</el-button>
            <el-button
              v-permission="perm('dlgConfirm')"
              size="small"
              type="primary"
              :disabled="!sourcePickReady"
              @click="confirmSourcePick"
            >
              {{ isDetailPick ? `${$tt('确认明细收款')}（${sourceSelected.length}）` : `${$tt('确认选择')}（${sourceSelected.length}）` }}
            </el-button>
          </div>
        </div>
      </template>
      <div class="picker-toolbar">
        <el-input v-model="sourceKeyword" :placeholder="$tt('单号 / 物料 / 规格 / 客户搜索')" clearable style="width:260px;" @keyup.enter="doSourceSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button v-permission="perm('query')" type="primary" @click="doSourceSearch">{{ $tt("搜索") }}</el-button>
      </div>
      <el-tabs v-if="sourcePickerMode === 'bill'" v-model="sourceTypeFilter" class="picker-type-tabs">
        <el-tab-pane v-for="t in sourceTypeTabs" :key="t" :label="$tt(t)" :name="t" />
      </el-tabs>
      <!-- 明细收款：弹窗就是来源单明细行列表（销售订单 / 销售出库单 / 销售发票），打勾勾选 → 确认后回写核销明细，金额按收款明细自动向下核销 -->
      <el-table v-if="isDetailPick" v-loading="detailLinesLoading" :data="filteredDetailLines" height="clamp(240px, calc(100vh - 330px), 440px)" border size="small" row-key="pickerRowKey" @selection-change="onSourceSelectionChange">
        <el-table-column type="selection" width="50" :selectable="isDetailLineSelectable" reserve-selection />
        <el-table-column type="index" :label="$tt('#')" width="50" />
        <el-table-column v-if="isPickerColVisible('imageUrls')" :label="$tf('图')" width="56" align="center">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" :preview-src-list="[row.imageUrl]" preview-teleported fit="cover" style="width:28px;height:28px;border-radius:3px" />
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" :label="$tf('类型')" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="primary">{{ row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceNo" :label="$tf('单号')" width="165" show-overflow-tooltip />
        <el-table-column v-if="isPickerColVisible('customerName')" prop="customerName" :label="$tf('客户')" width="120" show-overflow-tooltip />
        <el-table-column prop="materialCode" :label="$tf('物料编码')" width="110" show-overflow-tooltip />
        <el-table-column prop="materialName" :label="$tt('物料名称')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="spec" :label="$tf('规格型号')" width="120" show-overflow-tooltip />
        <el-table-column prop="categoryName" :label="$tf('物料分类')" width="90" show-overflow-tooltip />
        <el-table-column prop="unitName" :label="$tf('单位')" width="60" show-overflow-tooltip />
        <el-table-column v-for="cf in pickerCfFields" :key="cf.fieldKey" :label="cf.fieldLabel" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="mcfFieldValue(row, cf.fieldKey)" class="cf">{{ mcfFieldValue(row, cf.fieldKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="commissionEnabled && isPickerColVisible('salerName')" prop="salerName" :label="$tf('销售员')" width="100" show-overflow-tooltip />
        <el-table-column :label="$tt('行金额')" width="110" align="right">
          <template #default="{ row }">¥{{ Number(row.netAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('已收')" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.receivedAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('未收')" width="100" align="right">
          <template #default="{ row }">
            <span style="color:#6366f1;font-weight:600;">¥{{ Number(row.remainAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-table v-else :data="sourceBillList" height="clamp(240px, calc(100vh - 330px), 440px)" border @selection-change="onSourceSelectionChange">
        <el-table-column type="selection" width="50" :selectable="isSourceSelectable" />
        <el-table-column prop="sourceType" :label="$tf('单据类型')" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceType === '预收款单' ? 'warning' : 'primary'" effect="plain">{{ row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceNo" :label="$tf('单号')" width="170" />
        <el-table-column prop="sourceDate" :label="$tf('日期')" width="110" />
        <el-table-column prop="customerName" :label="$tf('客户')" min-width="140" />
        <el-table-column v-if="commissionEnabled" prop="salerName" :label="$tf('销售员')" width="100" show-overflow-tooltip />
        <el-table-column :label="$tf('金额')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.sourceAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('已核/已收')" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.usedAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('剩余')" width="120" align="right">
          <template #default="{ row }">
            <span style="color:#6366f1;font-weight:600;">¥{{ Number(row.remainAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <ColumnSettingDrawer v-model="pickerColumnSettingVisible" page-key="PaymentReceiptPicker" :columns="pickerColumns" :title="$tt('选单弹窗列设置')" />

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
    <PrintPreviewDialog v-model="printVisible" bill-type="PAYMENT_RECEIPT" :bill-id="form.receiptId" />

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="收款单" :bill-no="form.receiptNo || ''" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.receiptId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PAYMENT_RECEIPT" :bill-id="form.receiptId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.receiptId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PAYMENT_RECEIPT" :bill-id="form.receiptId" mode="target" />
      </el-tab-pane>
    </el-tabs>
  </div>

  </template>

<script setup>
import { withFallbackOption } from '@/utils/labelFallback'
import { isBillHidden } from '@/config/hiddenBills'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
import { isBillLinkRegistered } from '@/api/billLink'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Wallet, Collection, Plus, Delete, Search, Printer,
  ArrowRight, Check, TopRight, Close, CircleCheck, Clock,
  MagicStick, Refresh, Memo, Setting
} from '@element-plus/icons-vue'
import { listReceipt, getReceipt, saveReceipt, confirmReceipt, submitReceipt, rejectReceipt, reverseApproveReceipt, voidReceipt, unvoidReceipt, closeOrderReceipt, reopenReceipt, deleteReceipt, getWriteoffReceived, listActiveReceiptSourceNos } from '@/api/finance/paymentReceipt'
import { listAllCustomer } from '@/api/basedata/customer'
import { listAllEmployee } from '@/api/basedata/employee'
import { listAllCurrency } from '@/api/basedata/currency'
import { listAllSettle } from '@/api/basedata/settle'
import { listAllDepartment } from '@/api/basedata/department'
import { listAllBankAccount } from '@/api/basedata/bankAccount'
import { listSalePreReceipt } from '@/api/finance/salePreReceipt'
import { listSaleOrder, getSaleOrder } from '@/api/sale/saleOrder'
import { listSaleDelivery, getSaleDelivery } from '@/api/sale/saleDelivery'
import { listSaleInvoice, getSaleInvoice } from '@/api/sale/saleInvoice'
// 售后维修单来源（只读列表接口，维修单本身归销售模块维护）
import { listRepairs } from '@/api/sale/saleRepair'
import { listAllMaterial } from '@/api/basedata/material'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import FileUploader from '@/components/FileUploader.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { useAutoDocCode } from '@/composables/useAutoDocCode'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { useSysConfig } from '@/composables/useSysConfig'
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useMaterialCustomFields, pickerFirstImage } from '@/composables/useMaterialCustomFields'

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

const perm = usePagePerm('finance:receipt')

// ===== 物料自定义字段（选单弹窗明细行：客户按「芯片」等自定义字段认料）=====
const mcf = useMaterialCustomFields()
// 下拉显示字段（设置中心「自定义字段」里勾了「下拉显示」的物料字段）
const mcfDropFields = computed(() => mcf.dropFields.value)
// 取某行物料的自定义字段值（取不到返回空串，不渲染占位）
function mcfFieldValue(row, fieldKey) {
  const id = row && row.materialId
  if (id == null) return ''
  const vals = mcf.valueMap.value[id]
  if (!vals) return ''
  const v = vals[fieldKey]
  return v == null ? '' : String(v)
}

// ===== 表头级自定义字段（设置中心-自定义字段 cash_receipt 实体）=====
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

async function loadHeaderCustomFields() {
  try {
    const result = await loadCustomFieldDefinitions('cash_receipt')
    headerCustomSections.value = result.sections
    headerCustomFieldMap.clear()
    result.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
    headerFormulaFields.value = result.formulaFields
    result.fieldMap.forEach((def, key) => {
      if (!(key in form)) form[key] = def.defaultValue || ''
    })
  } catch (e) {
    console.warn('[PaymentReceiptEdit] 加载自定义字段失败:', e)
  }
}

async function saveHeaderCustomFields() {
  if (headerCustomFieldMap.size > 0 && form.receiptId) {
    const values = {}
    for (const key of headerCustomFieldMap.keys()) {
      if (key in form) values[key] = form[key]
    }
    await saveCustomFieldValues('cash_receipt', form.receiptId, values, headerFormulaFields.value)
  }
}

const route = useRoute()
const router = useRouter()
const headerFormRef = ref()
const changeRecordVisible = ref(false)

// 收款明细列定义（通用组件 BillItemsTable 消费；page-key 沿用 PaymentReceiptEdit_items 不变）
// ① 收款明细列（预收冲抵行回写进本表：预收单号列默认放第一，普通行该列空白）
const detailColumns = [
  { prop: 'sourceNo', label: '预收单号', minWidth: 170 },
  { prop: 'accountId', label: '收款账户', minWidth: 160 },
  { prop: 'bankAccount', label: '银行账号', minWidth: 140 },
  { prop: 'amount', label: '收款金额', width: 140 },
  { prop: 'remark', label: '备注', minWidth: 150 },
  { prop: 'baseAmount', label: '收款本位币', width: 130, align: 'right' }
]

const isEdit = computed(() => !!route.params.id)

// 仅草稿态允许编辑（单号/业务字段/自定义字段）；已生效/待审批等只读
// 折叠状态
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('RECEIPT')

const STORAGE_KEY = 'payment-receipt-edit-collapsed'
const collapsed = reactive({
  base: true,
  sourceOrder: true,
  items: true,
  writeoff: true,
  attach: true,
  audit: false
})

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

// 收款明细卡片折叠由通用组件 v-model 驱动，这里监听后持久化（与页头卡片共用同一份 localStorage）
watch(() => collapsed.items, () => saveCollapsed())

// ===== 系统配置（收款方式 / 销售提成总开关）=====
const { config: sysConfigMap, loadSysConfig: loadSysConfigFn, getConfig } = useSysConfig()

// ===== 下拉数据 =====
const customers = ref([])
const employees = ref([])
const currencies = ref([])
const settleModes = ref([])
const departments = ref([])
const accounts = ref([])

// 表单数据
const form = reactive({
  receiptId: null,
  receiptNo: '',
  receiptDate: new Date().toISOString().slice(0, 10),
  customerId: null,
  customerName: '',
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
  receivedAmount: 0,
  documentNo: '',
  remark: '',
  attachmentUrls: '',
  status: '草稿',
  approvalStatus: '',
  approvedBy: '',
  approvedAt: '',
  createdAt: '',
  createdBy: '',
  // 来源标识（系统自动生成 / 手工新增）与来源单据
  sourceType: '手工新增',
  sourceBillType: '',
  sourceBillNo: '',
  // 选中的销售订单信息（展示用，不传后端）
  sourceOrderAmount: 0,
  sourceOrderReceived: 0,
  sourceOrderUnreceived: 0,
  items: [],
  writeOffItems: [],
  // ===== 销售提成（2026-10-16）：比例/特殊服务费可改，不含税与提成自动算 =====
  commissionRate: null,
  specialServiceFee: 0,
  taxExcludedReceivedAmount: 0,
  salesCommission: 0
})


const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
const rules = {
  receiptDate: [{ required: true, message: tt('请选择收款日期'), trigger: 'change' }],
  customerId: [{ required: true, message: tt('请选择客户'), trigger: 'change' }],
  currencyId: [{ required: true, message: tt('请选择币种'), trigger: 'change' }]
}

// 是否可编辑（仅草稿状态可编辑）
const editable = computed(() => form.status === '草稿' || !form.status)

// 全站商业口径总开关（设置中心：启用外币业务）→ 关闭时单据只用本位币、汇率锁 1
const { foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()

function statusType(s) {
  return { '草稿': 'info', '待确认': 'warning', '已确认': 'success', '已取消': 'danger' }[s] || 'info'
}

// ===== 下拉联动 =====
function onCustomerChange(id) {
  const c = customers.value.find(x => x.customerId === id)
  if (c) {
    form.customerName = c.customerName
    if (c.currencyId) {
      form.currencyId = c.currencyId
      onCurrencyChange(c.currencyId)
    }
  }
  // 切换客户时清空已选核销明细（含预收款冲抵）
  if (form.writeOffItems.length > 0) {
    ElMessageBox.confirm(tt('切换客户将清空已选预收款冲抵与核销明细，是否继续？'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    }).then(() => {
      form.writeOffItems = []
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
  // 重算明细本位币
  form.items.forEach(row => {
    row.baseAmount = Number((Number(row.amount || 0) * Number(form.exchangeRate || 1)).toFixed(2))
  })
}

// ===== 收款明细 =====
function addItem() {
  form.items.push({
    accountId: null,
    accountName: '',
    bankAccount: '',
    amount: 0,
    baseAmount: 0,
    remark: ''
  })
  recalcAndAllocate()
}

function clearItems() {
  form.items = []
  recalcAndAllocate()
}

// ===== 金额计算 =====
const totalReceiptAmount = computed(() => {
  return form.items.reduce((s, r) => s + Number(r.amount || 0), 0)
})

// 核销明细行（来源=销售订单 / 销售出库单 / 销售发票；预收款单冲抵单列在「收款明细」表，不计入核销明细）
const allWriteOffRows = computed(() => form.writeOffItems.filter(w => w.sourceType !== '预收款单'))
// 核销合计 = 所有核销行「本次核销金额」之和
const totalWriteOffAmount = computed(() => allWriteOffRows.value.reduce((s, r) => s + Number(r.writeoffAmount || 0), 0))
// 收款明细核销合计（含预收款冲抵，供「实际收款」「剩余可分配」口径使用；与核销明细口径区分）
const totalWriteOffAllAmount = computed(() => {
  return form.writeOffItems.reduce((s, r) => s + Number(r.writeoffAmount || 0), 0)
})

const actualReceiptAmount = computed(() => {
  return Math.max(0, totalReceiptAmount.value - totalWriteOffAllAmount.value)
})

// 核销合计 = 全部业务来源核销行「本次核销金额」之和（预收款冲抵不构成新增需求，仅提供可用资金）
const totalWriteOffDemand = computed(() => totalWriteOffAmount.value)

// 多收自动转预收 = 现金结余 - 未被预收冲抵覆盖的核销需求（与后端 createOveragePreReceipt 口径一致）
const overageAmount = computed(() => {
  const covered = Math.max(0, totalWriteOffDemand.value - preWriteOffTotal.value)
  return Math.max(0, totalReceiptAmount.value - covered)
})

/**
 * 自动向下核销（2026-10-29 用户口径）：收款明细金额变动后，按核销明细行顺序
 * 把可用收款（现金/转账 + 预收冲抵）依次分配到每行「本次核销金额」，
 * 每行不超过该行未收余额；分配完仍有结余的，按多收转预收逻辑处理。
 */
function autoAllocateWriteoff() {
  let budget = Number((totalReceiptAmount.value + preWriteOffTotal.value).toFixed(2))
  form.writeOffItems.forEach(row => {
    if (row.sourceType === PRE_RECEIPT_SOURCE) return
    const cap = Math.max(0, Number((Number(row.sourceAmount || 0) - Number(row.usedAmount || 0)).toFixed(2)))
    const alloc = Math.min(cap, Math.max(0, budget))
    row.writeoffAmount = Number(alloc.toFixed(2))
    budget = Number((budget - alloc).toFixed(2))
  })
}

/** 收款明细变动入口：先自动向下核销，再重算合计/提成 */
function recalcAndAllocate() {
  autoAllocateWriteoff()
  calcTotal()
}

function calcTotal() {
  // 重算明细本位币
  form.items.forEach(row => {
    row.baseAmount = Number((Number(row.amount || 0) * Number(form.exchangeRate || 1)).toFixed(2))
  })
  form.totalAmount = totalReceiptAmount.value.toFixed(2)
  form.amount = totalReceiptAmount.value.toFixed(2)
  form.receivedAmount = actualReceiptAmount.value.toFixed(2)
  // 校验核销金额不能超过剩余金额
  form.writeOffItems.forEach(row => {
    const max = Number((row.sourceAmount || 0) - (row.usedAmount || 0))
    if (Number(row.writeoffAmount || 0) > max) {
      row.writeoffAmount = max
    }
    // 结案判断
    row.isClosed = Number(row.writeoffAmount || 0) >= max && max > 0 ? 1 : 0
  })
  // 销售提成联动（2026-10-16）：核销金额/比例/特殊服务费变化 → 行与表头统一重算
  recalcCommissionRows()
}

/**
 * 销售提成联动重算（2026-10-16 全行级口径，用户拍板：一切字段在明细行，表头不展示）。
 * 每行：销售员 / 提成比例%（行级可改，默认带设置中心值）/ 特殊服务费（行级手填）/ 不含税金额 / 销售提成。
 *   行销售提成 = (行不含税金额 − 行特殊服务费) × 行提成比例%
 *   表头合计（不展示，仅随主表落库供列表/导出）：不含税=Σ行、特殊服务费=Σ行、销售提成=Σ行
 */
function recalcCommissionRows() {
  const defaultRate = Number(form.commissionRate || 0)
  let taxExcludedSum = 0
  let specialSum = 0
  let commissionSum = 0
  form.writeOffItems.forEach(row => {
    if (row.sourceType === PRE_RECEIPT_SOURCE) return // 预收冲抵不参与提成
    const hasBase = Number(row.orderTotalAmount || 0) > 0 && Number(row.orderNetAmount || 0) > 0
    if (hasBase) {
      // 选单落行（带订单基数）：行不含税 = 核销金额 ×(不含税÷价税合计)
      row.taxExcludedAmount = Number((Number(row.writeoffAmount || 0) * (row.orderNetAmount / row.orderTotalAmount)).toFixed(2))
    } else if (row.taxExcludedAmount === undefined || row.taxExcludedAmount === null) {
      // 新落行但无基数：按不换算口径
      row.taxExcludedAmount = Number(Number(row.writeoffAmount || 0).toFixed(2))
    }
    // 无基数的回显行：保留落库的行不含税，不覆盖（否则刷新后换算结果丢失）
    let rate = Number(row.commissionRate)
    if (!rate || rate <= 0) {
      rate = defaultRate > 0 ? defaultRate : 3
      row.commissionRate = rate
    }
    const lineSpecial = Number(row.specialServiceFee || 0)
    row.commission = Number(((Number(row.taxExcludedAmount || 0) - lineSpecial) * rate / 100).toFixed(2))
    taxExcludedSum += Number(row.taxExcludedAmount || 0)
    specialSum += lineSpecial
    commissionSum += Number(row.commission || 0)
  })
  form.taxExcludedReceivedAmount = Number(taxExcludedSum.toFixed(2))
  form.specialServiceFee = Number(specialSum.toFixed(2))
  form.salesCommission = Number(commissionSum.toFixed(2))
}

// 表头已无提成字段（用户拍板全行级）；保留空入口避免模板残留引用报错
function onCommissionParamChange() {
  recalcCommissionRows()
}

// ===== 来源单选择：核销明细=业务单据(销售订单/销售出库单/销售发票) / 收款明细=预收款单(冲抵) =====
const PRE_RECEIPT_SOURCE = '预收款单'
const sourcePickerMode = ref('bill')
// 来源单选择弹窗（业务单据 / 预收款单共用）
const sourcePickerVisible = ref(false)
const sourceKeyword = ref('')
const sourceBillList = ref([])
const sourceSelected = ref([])
// 来源类型筛选（设计图 v3 界面 3：全部 / 销售订单 / 销售出库单 / 销售发票 / 售后维修单）
// 客户包隐藏单据（VITE_HIDDEN_BILLS）：售后维修单整项不出现（121 不上），也不查维修单候选
const repairBillHidden = isBillHidden('SALE_REPAIR')
const sourceTypeTabs = computed(() => {
  const tabs = ['全部', '销售订单', '销售出库单', '销售发票']
  if (!repairBillHidden) tabs.push('售后维修单')
  return tabs
})
const sourceTypeFilter = ref('全部')

// ===== 收款方式（2026-10-12 用户指正改版）：全局唯一，设置中心「财务参数 → 收款单收款方式」 =====
// 二选一固定生效、不随单切换：summary=汇总收款（整单一行核销）/ detail=明细收款（按来源单明细行逐行收）。
const receiveMode = computed(() => (getConfig('sale.receipt.receive.mode', 'summary') === 'detail' ? 'detail' : 'summary'))
// 销售提成总开关（2026-10-16）：设置中心「财务参数 → 收款发货管控 → 启用销售提成」，关=完全旧行为（提成字段与列全隐藏）
const commissionEnabled = computed(() => getConfig('finance.commission.enabled', 'true') !== 'false')
// 明细收款：弹窗直接铺开三类来源的明细行，勾选后回写核销明细（金额在核销明细里填）
const isDetailPick = computed(() => sourcePickerMode.value === 'bill' && receiveMode.value === 'detail')
const detailLineRows = ref([])
const detailLinesLoading = ref(false)
// 物料档案（图片 / 分类补全）：首次打开选单时懒加载，失败静默降级
const materialInfoMap = ref({})
let materialInfoLoaded = false
async function ensureMaterialInfo() {
  if (materialInfoLoaded) return
  materialInfoLoaded = true
  try {
    const list = await listAllMaterial()
    const map = {}
    ;(list || []).forEach(m => {
      if (m && m.materialId != null) map[m.materialId] = m
    })
    materialInfoMap.value = map
  } catch (e) {
    materialInfoMap.value = {}
  }
}
function materialInfo(materialId) {
  return materialId == null ? null : (materialInfoMap.value[materialId] || null)
}
const filteredDetailLines = computed(() => {
  const kw = (sourceKeyword.value || '').trim().toLowerCase()
  return detailLineRows.value.filter(l => {
    if (sourceTypeFilter.value !== '全部' && l.sourceType !== sourceTypeFilter.value) return false
    if (!kw) return true
    return [l.sourceNo, l.customerName, l.materialName, l.materialCode, l.spec].some(v => String(v || '').toLowerCase().includes(kw))
  })
})

// 弹窗只做勾选、不填金额：勾选 ≥1 行即可确认；金额回写到核销明细后再填
const sourcePickReady = computed(() => sourceSelected.value.length > 0)

// 明细行勾选限制：一次只允许同一客户（与汇总收款口径一致）
function isDetailLineSelectable(row) {
  if (Number(row.remainAmount || 0) <= 0) return false
  if (sourceSelected.value.length === 0) return true
  return row.customerId === sourceSelected.value[0].customerId
}

// ===== 选单弹窗列设置（按登录人 + 所属公司各自保存，全站列设置同一套存储口径）=====
const pickerColumns = [
  { prop: 'imageUrls', label: '图', width: 56 },
  { prop: 'customerName', label: '客户', width: 120 },
  { prop: 'salerName', label: '销售员', width: 100 }
]
const { columnSettingVisible: pickerColumnSettingVisible, isColVisible: isPickerColVisible } = useColumnSetting('PaymentReceiptPicker', pickerColumns)
const pickerCfFields = computed(() => mcfDropFields.value.filter(cf => isPickerColVisible('cf_' + cf.fieldKey)))

// 已核销金额汇总（销售出库单 / 销售发票没有已收列，按已生效收款单的核销明细反算）
// 返回 { bySource: {'类型_单ID': 金额}, byItem: {'类型_行ID': 金额} }
async function loadWriteoffReceivedMap() {
  const map = { bySource: {}, byItem: {} }
  try {
    const rows = await getWriteoffReceived({ sourceTypes: '销售订单,销售出库单,销售发票,售后维修单' })
    ;(rows || []).forEach(r => {
      const amount = Number(r.amount || 0)
      const key = `${r.sourceType}_${r.sourceId}`
      map.bySource[key] = Number((Number(map.bySource[key] || 0) + amount).toFixed(2))
      if (r.sourceItemId != null) {
        const itemKey = `${r.sourceType}_${r.sourceItemId}`
        map.byItem[itemKey] = Number((Number(map.byItem[itemKey] || 0) + amount).toFixed(2))
      }
    })
  } catch (e) {
    // 静默降级：取不到时按 0 已收展示，不阻断选单
  }
  return map
}

// 销售订单来源行（汇总/明细两用）：已生效 + 表头客户过滤 + 未收完
async function fetchOrderSourceRows(useKeyword = true) {
  const orderRes = await listSaleOrder({ current: 1, size: 9999, status: '已生效', keyword: useKeyword ? (sourceKeyword.value || undefined) : undefined })
  let records = (orderRes.records || orderRes.data?.records || [])
  if (form.customerId) records = records.filter(r => r.customerId === form.customerId)
  return records.map(r => {
    const netAmount = Number(r.netAmount || r.totalAmount || 0)
    const received = Number(r.receivedAmount || 0)
    return {
      sourceType: '销售订单',
      sourceId: r.orderId,
      sourceNo: r.orderNo,
      sourceDate: r.orderDate,
      customerId: r.customerId,
      customerName: r.customerName,
      sourceAmount: netAmount,
      usedAmount: received,
      orderNetAmount: netAmount,
      orderTotalAmount: Number(r.totalAmount || 0),
      salerId: r.salerId || null,
      salerName: r.salerName || '',
      remainAmount: Math.max(0, netAmount - received)
    }
  }).filter(r => r.remainAmount > 0)
}

// 销售出库单来源行
async function fetchDeliverySourceRows(receivedMap) {
  const res = await listSaleDelivery({ current: 1, size: 9999, status: '已生效', keyword: sourceKeyword.value || undefined })
  let records = (res.records || res.data?.records || [])
  if (form.customerId) records = records.filter(r => r.customerId === form.customerId)
  return records.map(r => {
    const netAmount = Number(r.netAmount || 0)
    const received = Number(receivedMap.bySource[`销售出库单_${r.deliveryId}`] || 0)
    return {
      sourceType: '销售出库单',
      sourceId: r.deliveryId,
      sourceNo: r.deliveryNo,
      sourceDate: r.deliveryDate,
      customerId: r.customerId,
      customerName: r.customerName,
      sourceAmount: netAmount,
      usedAmount: received,
      salerId: r.salerId || null,
      salerName: r.salerName || '',
      remainAmount: Number((netAmount - received).toFixed(2))
    }
  }).filter(r => r.remainAmount > 0)
}

// 销售发票来源行（红字发票金额为负，自动不进候选）
async function fetchInvoiceSourceRows(receivedMap) {
  const res = await listSaleInvoice({ current: 1, size: 9999, status: '已生效', keyword: sourceKeyword.value || undefined })
  let records = (res.records || res.data?.records || [])
  if (form.customerId) records = records.filter(r => r.customerId === form.customerId)
  return records.map(r => {
    const netAmount = Number(r.netAmount || 0)
    const received = Number(receivedMap.bySource[`销售发票_${r.invoiceId}`] || 0)
    return {
      sourceType: '销售发票',
      sourceId: r.invoiceId,
      sourceNo: r.invoiceNo,
      sourceDate: r.invoiceDate,
      customerId: r.customerId,
      customerName: r.customerName,
      sourceAmount: netAmount,
      usedAmount: received,
      salerId: r.salerId || null,
      salerName: r.salerName || '',
      remainAmount: Number((netAmount - received).toFixed(2))
    }
  }).filter(r => r.remainAmount > 0)
}

// 售后维修单来源行：保外维修完成或已返还、且「不存在未作废收款单」的维修单；
// 维修单没有物料明细行，整单一条「维修服务费」核销行（sourceItemId 为空）。
// 已收 = 已生效收款单核销明细反算（getWriteoffReceived），未收 = 合计费用 − 已收。
// 候选排除口径按收款单自身状态判断：已有未作废收款单的维修单不进入候选；收款单已作废的维修单重新进入候选。
async function fetchRepairSourceRows(receivedMap) {
  if (repairBillHidden) return []
  const params = { current: 1, size: 100, keyword: sourceKeyword.value || undefined }
  if (form.customerId) params.customerId = form.customerId
  const [returnedRes, doneRes, activeNos] = await Promise.all([
    listRepairs({ ...params, status: '已返还' }).catch(() => null),
    listRepairs({ ...params, status: '维修完成' }).catch(() => null),
    listActiveReceiptSourceNos('售后维修单').catch(() => [])
  ])
  const merged = new Map()
  const pickRecords = (res) => (res && (res.records || res.data?.records)) || []
  ;[...pickRecords(returnedRes), ...pickRecords(doneRes)].forEach(r => {
    if (r && r.repairId != null) merged.set(r.repairId, r)
  })
  // 仅排除存在未作废收款单的维修单号，避免重复收款；已作废收款单的维修单重新可选
  const blockedNos = new Set((Array.isArray(activeNos) ? activeNos : []).map(no => String(no || '')))
  return Array.from(merged.values())
    .filter(r => !blockedNos.has(String(r.repairNo || '')))
    .map(r => {
      const totalFee = Number(r.totalFee || 0)
      const received = Number(receivedMap.bySource[`售后维修单_${r.repairId}`] || 0)
      return {
        sourceType: '售后维修单',
        sourceId: r.repairId,
        sourceNo: r.repairNo,
        sourceDate: r.repairDate,
        customerId: r.customerId,
        customerName: r.customerName,
        sourceAmount: totalFee,
        usedAmount: received,
        salerId: null,
        salerName: '',
        orderNetAmount: totalFee,
        orderTotalAmount: totalFee,
        remainAmount: Number((totalFee - received).toFixed(2))
      }
    })
    .filter(r => r.remainAmount > 0)
}

/**
 * 明细收款：拉三类来源的明细行，拍平成一张列表（弹窗打开即明细列表）
 * 已收口径：销售订单取行级 received_amount；销售出库单/销售发票取已生效收款单的核销明细合计。
 */
async function loadDetailLineRows() {
  detailLinesLoading.value = true
  detailLineRows.value = []
  try {
    await ensureMaterialInfo()
    const [orderRows, receivedMap] = await Promise.all([fetchOrderSourceRows(false), loadWriteoffReceivedMap()])
    const [deliveryRows, invoiceRows, repairRows] = await Promise.all([
      fetchDeliverySourceRows(receivedMap),
      fetchInvoiceSourceRows(receivedMap),
      fetchRepairSourceRows(receivedMap)
    ])
    if (orderRows.length + deliveryRows.length + invoiceRows.length + repairRows.length > 400) {
      ElMessage.warning(tt('未收完的来源单据过多，请先在表头选择客户再选择明细'))
      return
    }
    const rows = []
    // 1) 销售订单明细行
    const orderDetails = await Promise.all(orderRows.map(r => getSaleOrder(r.sourceId).catch(() => null)))
    orderDetails.forEach((res, i) => {
      if (!res) return
      const detail = (res && (res.data || res)) || {}
      const items = detail.items || detail.orderItems || []
      const head = orderRows[i]
      items.forEach(it => {
        const netAmount = Number(it.netAmount || 0)
        const received = Number(it.receivedAmount || 0)
        const remain = Number((netAmount - received).toFixed(2))
        if (remain <= 0) return
        const mi = materialInfo(it.materialId)
        rows.push({
          sourceType: '销售订单',
          sourceId: head.sourceId,
          sourceNo: head.sourceNo,
          customerId: head.customerId,
          customerName: head.customerName,
          itemId: it.orderItemId,
          pickerRowKey: `销售订单_${it.orderItemId}`,
          materialId: it.materialId ?? null,
          materialCode: it.materialCode || (mi && mi.materialCode) || '',
          materialName: it.materialName || (mi && mi.materialName) || '',
          spec: it.specification || (mi && mi.specification) || '',
          categoryName: it.categoryName || (mi && mi.categoryName) || '',
          unitName: it.unit || (mi && mi.unit) || '',
          imageUrl: mi ? pickerFirstImage(mi.imageUrls) : '',
          netAmount,
          receivedAmount: received,
          remainAmount: remain,
          salerId: head.salerId || null,
          salerName: head.salerName || '',
          orderNetAmount: Number(head.orderNetAmount || 0),
          orderTotalAmount: Number(head.orderTotalAmount || 0)
        })
      })
    })
    // 2) 销售出库单明细行
    const deliveryDetails = await Promise.all(deliveryRows.map(r => getSaleDelivery(r.sourceId).catch(() => null)))
    deliveryDetails.forEach((res, i) => {
      if (!res) return
      const detail = (res && (res.data || res)) || {}
      const items = detail.items || []
      const head = deliveryRows[i]
      items.forEach(it => {
        const netAmount = Number(it.netAmount || 0)
        const received = Number(receivedMap.byItem[`销售出库单_${it.deliveryItemId}`] || 0)
        const remain = Number((netAmount - received).toFixed(2))
        if (remain <= 0) return
        const mi = materialInfo(it.materialId)
        rows.push({
          sourceType: '销售出库单',
          sourceId: head.sourceId,
          sourceNo: head.sourceNo,
          customerId: head.customerId,
          customerName: head.customerName,
          itemId: it.deliveryItemId,
          pickerRowKey: `销售出库单_${it.deliveryItemId}`,
          materialId: it.materialId ?? null,
          materialCode: it.materialCode || (mi && mi.materialCode) || '',
          materialName: it.materialName || (mi && mi.materialName) || '',
          spec: it.specification || (mi && mi.specification) || '',
          categoryName: (mi && mi.categoryName) || '',
          unitName: it.unit || (mi && mi.unit) || '',
          imageUrl: mi ? pickerFirstImage(mi.imageUrls) : '',
          netAmount,
          receivedAmount: received,
          remainAmount: remain,
          salerId: head.salerId || null,
          salerName: head.salerName || '',
          orderNetAmount: netAmount,
          orderTotalAmount: netAmount
        })
      })
    })
    // 3) 销售发票明细行
    const invoiceDetails = await Promise.all(invoiceRows.map(r => getSaleInvoice(r.sourceId).catch(() => null)))
    invoiceDetails.forEach((res, i) => {
      if (!res) return
      const detail = (res && (res.data || res)) || {}
      const items = detail.items || []
      const head = invoiceRows[i]
      items.forEach(it => {
        const netAmount = Number(it.netAmount || 0)
        const received = Number(receivedMap.byItem[`销售发票_${it.invoiceItemId}`] || 0)
        const remain = Number((netAmount - received).toFixed(2))
        if (remain <= 0) return
        const mi = materialInfo(it.materialId)
        rows.push({
          sourceType: '销售发票',
          sourceId: head.sourceId,
          sourceNo: head.sourceNo,
          customerId: head.customerId,
          customerName: head.customerName,
          itemId: it.invoiceItemId,
          pickerRowKey: `销售发票_${it.invoiceItemId}`,
          materialId: it.materialId ?? null,
          materialCode: it.materialCode || (mi && mi.materialCode) || '',
          materialName: it.materialName || (mi && mi.materialName) || '',
          spec: it.specification || (mi && mi.specification) || '',
          categoryName: (mi && mi.categoryName) || '',
          unitName: it.unit || (mi && mi.unit) || '',
          imageUrl: mi ? pickerFirstImage(mi.imageUrls) : '',
          netAmount,
          receivedAmount: received,
          remainAmount: remain,
          salerId: head.salerId || null,
          salerName: head.salerName || '',
          orderNetAmount: netAmount,
          orderTotalAmount: netAmount
        })
      })
    })
    // 4) 售后维修单：维修单无物料明细，整单一行「维修服务费」
    repairRows.forEach(r => {
      rows.push({
        sourceType: '售后维修单',
        sourceId: r.sourceId,
        sourceNo: r.sourceNo,
        customerId: r.customerId,
        customerName: r.customerName,
        itemId: null,
        pickerRowKey: `售后维修单_${r.sourceId}`,
        materialId: null,
        materialCode: '',
        materialName: '维修服务费',
        spec: '',
        categoryName: '',
        unitName: '',
        imageUrl: '',
        netAmount: r.sourceAmount,
        receivedAmount: r.usedAmount,
        remainAmount: r.remainAmount,
        salerId: null,
        salerName: '',
        orderNetAmount: Number(r.orderNetAmount || 0),
        orderTotalAmount: Number(r.orderTotalAmount || 0)
      })
    })
    detailLineRows.value = rows
    // 物料自定义字段值预取（列展示用；失败静默降级，不阻断选单）
    mcf.preloadAll(rows)
  } catch (e) {
    ElMessage.error(e.message || tt('查询来源单明细失败'))
  } finally {
    detailLinesLoading.value = false
  }
}

// 预收冲抵行（来源=预收款单）：随收款明细展示，确认收款时后端自动扣减预收单
const preWriteOffRows = computed(() => form.writeOffItems.filter(w => w.sourceType === PRE_RECEIPT_SOURCE))
// 订单核销行（来源=销售订单）
const orderWriteOffRows = computed(() => form.writeOffItems.filter(w => w.sourceType === '销售订单'))
const preWriteOffTotal = computed(() => preWriteOffRows.value.reduce((s, r) => s + Number(r.writeoffAmount || 0), 0))
const orderWriteOffTotal = computed(() => orderWriteOffRows.value.reduce((s, r) => s + Number(r.writeoffAmount || 0), 0))

// ① 收款明细表数据源 = 普通收款行 + 预收冲抵行（预收冲抵回写进本表，靠「预收单号」列区分）
const combinedDetailRows = computed(() => [...form.items, ...preWriteOffRows.value])

// 收款明细卡片汇总区（通用组件 BillItemsTable 按此渲染）：数值/标签全部沿用本页既有 computed 口径；
// 原页表格下方没有汇总区，这里仅把「本卡片两行数据」的金额口径按统一版式呈现（页头下方「金额合计」块保持原样不动）
const summaryItems = computed(() => [
  { label: '收款金额合计（现金/转账）：', value: `¥${totalReceiptAmount.value.toFixed(2)}` },
  { label: '预收款冲抵：', value: `¥${preWriteOffTotal.value.toFixed(2)}`, class: 'discount' },
  { label: '实际收款：', value: `¥${actualReceiptAmount.value.toFixed(2)}`, total: true }
])

// 判断某行是否「预收冲抵」行
function isPreRow(row) {
  return row && row.sourceType === PRE_RECEIPT_SOURCE
}

// 删除收款明细表里的一行：预收冲抵行从 writeOffItems 删，普通行从 items 删
function removeDetailRow(row) {
  if (isPreRow(row)) {
    const idx = form.writeOffItems.findIndex(w => w === row || (w.sourceType === row.sourceType && w.sourceId === row.sourceId))
    if (idx > -1) form.writeOffItems.splice(idx, 1)
  } else {
    const idx = form.items.findIndex(w => w === row)
    if (idx > -1) form.items.splice(idx, 1)
  }
  recalcAndAllocate()
}

// ① 收款明细删除：合并行 = [...form.items, ...预收冲抵行]，按同一索引回推源行（普通行删 items、冲抵行删 writeOffItems）
function onDetailRowRemove(index) {
  const row = index < form.items.length
    ? form.items[index]
    : preWriteOffRows.value[index - form.items.length]
  if (row) removeDetailRow(row)
}

// 按行删除（预收冲抵与业务来源核销共用 form.writeOffItems 载体）：明细行按「类型+明细行ID」精确定位，避免同单多行误删
function removeWriteOffRow(row) {
  const idx = form.writeOffItems.findIndex(w => w === row
    || (w.sourceType === row.sourceType
      && w.sourceId === row.sourceId
      && (w.sourceItemId || null) === (row.sourceItemId || null)))
  if (idx > -1) form.writeOffItems.splice(idx, 1)
  recalcAndAllocate()
}

// ===== 余数不收 =====
// 余数不收勾选：该来源单剩余金额不再收款（结案）
function onWaiveChange(row, val) {
  row.waiveRemainder = val ? 1 : 0
}

// ===== 来源单弹窗：不强制先选客户，未选客户时加载全部，勾选时限制同客户 =====
function resetDetailPickState() {
  detailLineRows.value = []
}

function openSourcePicker() {
  sourcePickerMode.value = 'bill'
  sourceTypeFilter.value = '全部'
  sourceSelected.value = []
  resetDetailPickState()
  sourcePickerVisible.value = true
  if (isDetailPick.value) loadDetailLineRows()
  else loadSourceBills()
}

// 搜索按钮/回车：明细收款列表随输入即时过滤（单号/客户/货品），无需重查；其余模式走服务端搜索
function doSourceSearch() {
  if (!isDetailPick.value) loadSourceBills()
}

function openPreSourcePicker() {
  sourcePickerMode.value = 'pre'
  sourceSelected.value = []
  resetDetailPickState()
  sourcePickerVisible.value = true
  loadSourceBills()
}

async function loadSourceBills() {
  try {
    if (sourcePickerMode.value === 'pre') {
      // 预收款单：已生效且未核销完；表头已选客户则只查该客户，未选则查全部
      const params = { current: 1, size: 9999, status: 2, keyword: sourceKeyword.value || undefined }
      if (form.customerId) params.customerId = form.customerId
      const preRes = await listSalePreReceipt(params)
      sourceBillList.value = (preRes.records || []).filter(r => Number(r.remainAmount || 0) > 0).map(r => ({
        sourceType: PRE_RECEIPT_SOURCE,
        sourceId: r.id,
        sourceNo: r.receiptNo,
        sourceDate: r.receiptDate,
        customerId: r.customerId,
        customerName: r.customerName,
        sourceAmount: Number(r.amount || 0),
        usedAmount: Number(r.usedAmount || 0),
        remainAmount: Number(r.remainAmount || 0)
      }))
    } else {
      // 业务单据：销售订单 / 销售出库单 / 销售发票 / 售后维修单（未收完；表头已选客户则只查该客户）
      const receivedMap = await loadWriteoffReceivedMap()
      const [orders, deliveries, invoices, repairs] = await Promise.all([
        fetchOrderSourceRows(),
        fetchDeliverySourceRows(receivedMap),
        fetchInvoiceSourceRows(receivedMap),
        fetchRepairSourceRows(receivedMap)
      ])
      sourceBillList.value = [...orders, ...deliveries, ...invoices, ...repairs]
    }
  } catch (e) {
    sourceBillList.value = []
    ElMessage.error(e.message || tt('查询来源单失败'))
  }
}

function isSourceSelectable(row) {
  if (Number(row.remainAmount || 0) <= 0) return false
  if (sourceSelected.value.length === 0) return true
  return row.customerId === sourceSelected.value[0].customerId
}

function onSourceSelectionChange(rows) {
  sourceSelected.value = rows
}

function confirmSourcePick() {
  // ===== 明细收款分支：弹窗只做勾选，勾选行回写核销明细，金额回写后在核销明细里填 =====
  if (isDetailPick.value) {
    const pickedLines = sourceSelected.value
    if (pickedLines.length === 0) {
      ElMessage.warning(tt('请先勾选要收款的明细行'))
      return
    }
    // 同一次只能选同一客户（与汇总收款口径一致）
    const firstCid = pickedLines[0].customerId
    if (pickedLines.some(l => l.customerId !== firstCid)) {
      ElMessage.warning(tt('一次只能选择同一客户的明细行，请重新填写'))
      return
    }
    // 自动回填客户到表头（含币种联动），与汇总收款一致
    if (firstCid && form.customerId !== firstCid) {
      form.customerId = firstCid
      form.customerName = pickedLines[0].customerName || ''
      const c = customers.value.find(x => x.customerId === firstCid)
      if (c && c.currencyId) {
        form.currencyId = c.currencyId
        onCurrencyChange(c.currencyId)
      }
    }
    let added = 0
    pickedLines.forEach(l => {
      // 同一明细行不重复加入（按「类型 + 明细行ID」判重；
      // 售后维修单没有明细行，改按「类型 + 单据ID」判重，避免同类型维修单互相挡掉）
      const dup = form.writeOffItems.some(w => w.sourceType === l.sourceType && (
        l.itemId != null
          ? w.sourceItemId === l.itemId
          : (w.sourceItemId == null) && w.sourceId === l.sourceId
      ))
      if (dup) return
      form.writeOffItems.push({
        sourceType: l.sourceType,
        sourceId: l.sourceId,
        sourceNo: l.sourceNo,
        sourceAmount: l.netAmount,
        usedAmount: l.receivedAmount,
        // 弹窗只勾选不填金额：落行金额默认 0，回写后在核销明细「本次核销金额」里填
        writeoffAmount: 0,
        sourceItemId: l.itemId,
        sourceItemName: l.materialName,
        // 提成（2026-10-16 全行级）：销售员/比例/费用随行 + 行不含税基数（落行后统一重算）
        salerId: l.salerId || null,
        salerName: l.salerName || '',
        orderNetAmount: Number(l.orderNetAmount || 0),
        orderTotalAmount: Number(l.orderTotalAmount || 0),
        commissionRate: Number(form.commissionRate || 3),
        taxExcludedAmount: 0,
        specialServiceFee: 0,
        commission: 0,
        isClosed: 0
      })
      added++
    })
    recalcAndAllocate()
    sourcePickerVisible.value = false
    const billCount = new Set(pickedLines.map(l => `${l.sourceType}_${l.sourceId}`)).size
    if (added > 0) {
      ElMessage.success(`${tt('已添加 ')}${added}${tt(' 行明细收款（')}${billCount}${tt(' 张来源单）')}，${tt('已按收款金额自动向下核销')}`)
    } else {
      ElMessage.warning(tt('所选明细行已在核销明细中，未重复添加'))
    }
    sourceSelected.value = []
    return
  }
  if (sourceSelected.value.length === 0) {
    ElMessage.warning(tt('请至少选择一张来源单'))
    return
  }
  // 同一次只能选同一客户
  const firstCid = sourceSelected.value[0].customerId
  if (sourceSelected.value.some(s => s.customerId !== firstCid)) {
    ElMessage.warning(tt('一次只能选择同一客户的来源单，请重新选择'))
    return
  }
  // 自动回填客户到表头（含币种联动）
  if (firstCid && form.customerId !== firstCid) {
    form.customerId = firstCid
    form.customerName = sourceSelected.value[0].customerName || ''
    const c = customers.value.find(x => x.customerId === firstCid)
    if (c && c.currencyId) {
      form.currencyId = c.currencyId
      onCurrencyChange(c.currencyId)
    }
  }
  sourceSelected.value.forEach(s => {
    if (form.writeOffItems.some(w => w.sourceType === s.sourceType && w.sourceId === s.sourceId)) return
    form.writeOffItems.push({
      sourceType: s.sourceType,
      sourceId: s.sourceId,
      sourceNo: s.sourceNo,
      sourceAmount: s.sourceAmount,
      usedAmount: s.usedAmount,
      writeoffAmount: s.remainAmount,
      // 售后维修单整单核销：来源明细行为空，名称固定「维修服务费」
      sourceItemId: s.sourceItemId ?? null,
      sourceItemName: s.sourceType === '售后维修单' ? '维修服务费' : (s.sourceItemName || null),
      // 提成（2026-10-16 全行级）：销售员/比例/费用/提成全部随行；比例默认带表单默认值（源自设置中心）
      salerId: s.salerId || null,
      salerName: s.salerName || '',
      orderNetAmount: Number(s.orderNetAmount || 0),
      orderTotalAmount: Number(s.orderTotalAmount || 0),
      commissionRate: Number(form.commissionRate || 3),
      taxExcludedAmount: 0,
      specialServiceFee: 0,
      commission: 0,
      isClosed: 0
    })
  })
  recalcAndAllocate()
  sourcePickerVisible.value = false
  ElMessage.success(`${tt("已添加 ")}${sourceSelected.value.length}${tt(" 张来源单")}`)
  sourceSelected.value = []
}

// ===== 来源类型标签 =====

// 来源类型标签
function sourceTypeTag(type) {
  if (type === 'SALES_ORDER') return 'warning'
  if (type === 'SALES_DELIVERY') return 'primary'
  return 'info'
}

function sourceTypeLabel(type) {
  if (type === 'SALES_ORDER') return '销售订单'
  if (type === 'SALES_DELIVERY') return '销售发货'
  return type || '-'
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
  // 宽松草稿保存：不校验必填，允许空客户/无明细（可后续补全再提交）

  calcTotal()
  // 合并核销明细：预收款单 / 销售订单 / 销售出库单 / 销售发票 统一存于 writeOffItems，生效时由后端统一核销
  // 全量保存（含金额 0 的行）：弹窗勾选回写后金额默认 0，是"待财务在核销明细里填金额"的合法中间态，不能丢行
  const writeoffDetails = form.writeOffItems
    .map(w => ({
      sourceType: w.sourceType,
      sourceId: w.sourceId,
      sourceNo: w.sourceNo,
      sourceAmount: Number(w.sourceAmount || 0),
      usedAmount: Number(w.usedAmount || 0),
      writeoffAmount: Number(w.writeoffAmount || 0),
      // 明细收款行（2026-10-12）：来源明细行ID+货品名必须随保存落库，否则行级已收回写失效
      sourceItemId: w.sourceItemId || null,
      sourceItemName: w.sourceItemName || null,
      // 销售提成（2026-10-16 全行级）：销售员/比例/行不含税/行特殊服务费/行提成随行落库
      salerId: w.salerId || null,
      salerName: w.salerName || null,
      commissionRate: Number(w.commissionRate || 0) || null,
      taxExcludedAmount: Number(w.taxExcludedAmount || 0),
      specialServiceFee: Number(w.specialServiceFee || 0),
      commission: Number(w.commission || 0),
      settledQty: w.settledQty || '未冲销',
      waiveRemainder: Number(w.waiveRemainder || 0)
    }))
  const payload = { ...form, status: '草稿', writeOffItems: writeoffDetails }
  try {
    const res = await saveReceipt(payload)
    if (res && res.receiptId) form.receiptId = res.receiptId
    if (res && res.receiptNo) form.receiptNo = res.receiptNo
    form.status = '草稿'
    // 新增态保存后必须把新单 id 写回地址栏：否则地址栏仍停在 /new，用户按 F5 会丢掉刚保存的草稿。
    // skipRouteSync：提交链路里先跳过（写回会改变布局 keep-alive key(fullPath) → 组件实例重建），由 onSubmit 的 finally 统一写回。
    if (!opts.skipRouteSync && !route.params.id && form.receiptId) {
      try { await router.replace({ name: 'FinanceReceiptEdit', params: { id: String(form.receiptId) } }) } catch (_) {}
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
  if (!saved || !form.receiptId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PAYMENT_RECEIPT', billLabel: '收款单', billNo: form.receiptNo })
    await submitReceipt(form.receiptId)
    form.status = needApproval ? '待审核' : '已确认'
    ElMessage.success(submitSuccessMessage(needApproval))
    if (route.params.id) await loadDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时实例重建无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && form.receiptId) {
      try { await router.replace({ name: 'FinanceReceiptEdit', params: { id: String(form.receiptId) } }) } catch (_) {}
    }
  }
}



// ===== 取消 =====
async function onCancel() {
  try {
    await ElMessageBox.confirm(tt('确认取消该收款单？取消后不可恢复'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('返回'),
      type: 'warning'
    })
    await saveReceipt({ ...form, status: '已取消' })
    form.status = '已取消'
    ElMessage.success(tt('已取消'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('取消失败'))
  }
}

// ===== 确认收款 =====
async function onConfirm() {
  if (!form.receiptId) { ElMessage.warning(tt('请先保存单据')); return }
  try {
    await ElMessageBox.confirm(tt('确认收款？确认后单据不可修改'), tt('提示'), {
      confirmButtonText: tt('确定'),
      cancelButtonText: tt('取消'),
      type: 'warning'
    })
    await confirmReceipt(form.receiptId)
    form.status = '已确认'
    ElMessage.success(tt('确认成功'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('确认失败'))
  }
}

// ===== 打印 =====
function handlePrint() {
  if (!form.receiptId) { ElMessage.warning(tt('请先保存单据再打印')); return }
  printVisible.value = true
}
const printVisible = ref(false)

// ===== 加载详情 =====
async function loadDetail() {
  if (!route.params.id) {
    autoFillDocCodeOnCreate(form, 'receiptNo')
    // 新建模式也加载自定义字段定义（分区渲染）
    await loadHeaderCustomFields()
    return
  }
  try {
    const res = await getReceipt(route.params.id)
    Object.assign(form, res)
    if (!form.items) form.items = []
    if (!form.writeOffItems) form.writeOffItems = []
    // 回填表头级自定义字段值
    await loadHeaderCustomFields()
    if (form.receiptId) {
      const cfValues = await loadCustomFieldValues('cash_receipt', form.receiptId).catch(() => ({}))
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
  getId: () => form.receiptId,
  api: {
    approve: confirmReceipt,
    reject: rejectReceipt,
    reverseApprove: reverseApproveReceipt,
    close: closeOrderReceipt,
    reopen: reopenReceipt,
    void: voidReceipt,
    unvoid: unvoidReceipt,
    delete: deleteReceipt
  },
  reload: loadDetail,
  router,
  listPath: '/finance/receipt',
  newPath: '/finance/receipt/new',
  billLabel: '收款单'
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
      const [custRes, empRes, curRes, stlRes, depRes, accRes] = await Promise.all([
        listAllCustomer(),
        listAllEmployee(),
        listAllCurrency(),
        listAllSettle(),
        listAllDepartment(),
        listAllBankAccount()
      ])
      customers.value = custRes || []
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
  loadFn: getReceipt,
  form,
  idField: 'receiptId',
  noField: 'receiptNo',
  itemsField: 'items',
  afterCopy: () => calcTotal(),
  customField: { entity: 'cash_receipt' }
})

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

const { isFieldRequired } = useFieldPermission('cash_receipt')
useFieldRequiredMark('cash_receipt', '.payment-receipt-edit')

onMounted(async () => {
  // 全站商业口径总开关（设置中心：启用外币业务）——最先装载，失败不阻断页面
  await ensureCommercialSwitches()
  // 物料自定义字段定义（选单弹窗明细行列用；内部已容错，失败不阻断页面）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PAYMENT_RECEIPT')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })
  loadCollapsed()
  await loadSysConfigFn()
  await loadBaseData()
  // 销售提成默认比例（2026-10-16）：新建单据从设置中心带出（单内可改）；已加载的单据用单上保存的值
  if (!route.params.id && !form.receiptId && (form.commissionRate === null || form.commissionRate === undefined)) {
    const defRate = Number(getConfig('finance.commission.default.rate', '3'))
    form.commissionRate = Number.isFinite(defRate) && defRate > 0 ? defRate : 3
  }
  const copied = await applyCopy()
  if (!copied) {
    await loadDetail()
  }
  if (form.commissionRate === null || form.commissionRate === undefined || Number(form.commissionRate) <= 0) {
    const defRate = Number(getConfig('finance.commission.default.rate', '3'))
    form.commissionRate = Number.isFinite(defRate) && defRate > 0 ? defRate : 3
  }
  recalcCommissionRows()
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadBaseData)


  </script>

<style scoped lang="scss">
.payment-receipt-edit {
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

// ===== 选单弹窗标题栏（设计图 v3：操作按钮全在标题栏右侧）=====
.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 28px; // 给弹窗右上角关闭按钮留位
}
.picker-title { font-size: 15px; font-weight: 600; color: #303133; }
.picker-header-actions { display: flex; align-items: center; gap: 8px; }
.picker-customer { font-size: 12px; color: #909399; margin-right: 4px; }
.header-actions { display: flex; align-items: center; gap: 8px; }

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

// ===== 明细子表标题与列设置按钮 =====
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

  // 当前收款方式提示（全局配置，不可在此切换）
  .picker-mode-tip {
    margin-left: 12px;
    font-size: 12px;
    color: #1890ff;
    background: #e8f4ff;
    border: 1px solid #b3d8ff;
    border-radius: 4px;
    padding: 2px 8px;
    white-space: nowrap;
  }
}

// ===== 来源销售订单卡片 =====
.empty-source-tip {
  padding: 16px 0;
}
.source-order-info {
  padding: 4px 0;
  .info-row {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px 0;
    font-size: 13px;
  }
  .info-label {
    color: #909399;
    font-weight: 500;
  }
}

@media (max-width: 1400px) {
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .payment-receipt-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
}

// ===== 核销明细卡片样式 =====
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 10px;
}

.selected-order-info {
  padding: 10px 12px;
  background: #f0f9ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  margin-bottom: 10px;
  
  .info-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 8px 16px;
    margin-bottom: 10px;
  }
  
  .info-item {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    
    &.highlight {
      background: #ecf5ff;
      padding: 4px 8px;
      border-radius: 3px;
    }
  }
  
  .info-label {
    color: #909399;
    font-weight: 500;
  }
  
  .info-value {
    color: #303133;
    font-weight: 600;
    
    &.amount { color: #e6a23c; }
    &.paid { color: #67c23a; }
    &.unpaid { color: #f56c6c; }
  }
  
  .progress-bar-wrap {
    .progress-label {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: #606266;
      margin-bottom: 4px;
    }
  }
}

.balance-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 10px;
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

.overdue {
  color: #f56c6c;
  font-weight: 600;
}

.due-soon {
  color: #e6a23c;
  
  &.near {
    color: #f56c6c;
  }
}

.discount-text {
  color: #e6a23c;
}

// 物料自定义字段值（选单弹窗明细行，客户按「芯片」等认料）
.cf {
  display: inline-block;
  padding: 0 4px;
  font-size: 11px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe0a3;
  border-radius: 2px;
}
</style>
