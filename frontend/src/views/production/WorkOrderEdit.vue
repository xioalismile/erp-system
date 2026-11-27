<template>
  <div class="work-order-edit">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production/workorders')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-area">
          <div class="title-row">
            <el-icon class="title-icon"><Document /></el-icon>
            <span class="page-title">{{ isEdit ? '生产工单' : '新增生产工单' }}</span>
            <span v-if="workOrder.orderNo" class="order-no">
              <el-icon><Collection /></el-icon>
              {{ workOrder.orderNo }}
            </span>
            <el-tag v-if="workOrder.isUrgent === 1" size="small" type="danger" effect="dark">{{ $tt("紧急") }}</el-tag>
            <el-tag v-if="workOrder.status" size="small" :type="statusType(workOrder.status, workOrder.closeStatus)" effect="plain" class="status-tag">{{ $dt(statusText()) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button v-permission="perm('view')" v-if="!(!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id)" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>{{ $tt('操作日志') }}
        </el-button>
        <!-- 下推：与销售订单同款统一组件，固定放在操作日志之后、保存/提交之前 -->
        <PushDownButton v-if="isEdit && pushDownEnabled" source-type="WorkOrders" :source-doc="workOrder" />
        <BillActions bill-type="PRODUCTION_ORDER"
          :status="form.status"
          :close-status="form.closeStatus"
          :is-new="!form.orderId && !form.deliveryId && !form.inboundId && !form.invoiceId && !form.voucherId && !form.contractId && !form.requisitionId && !form.stockCheckId && !form.transferId && !form.bomId && !form.sampleId && !form.freeSampleId && !form.workOrderId && !form.billId && !form.id"
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
        <el-form :model="workOrder" :rules="rules" ref="headerFormRef" label-width="80px" class="base-form">
          <!-- 单据信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>单据信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('工单编号')" v-if="isFieldVisible('orderNo')" :required="isFieldRequired('orderNo')">
                <el-input v-model="workOrder.orderNo" :placeholder="docCodePlaceholder" :disabled="!isDraft" class="field-input" />
              </el-form-item>
              <el-form-item :label="$tf('工单日期')" prop="orderDate" required v-if="isFieldVisible('orderDate')">
                <el-date-picker v-model="workOrder.orderDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('工单状态')">
                <el-tag :type="statusType(workOrder.status, workOrder.closeStatus)" size="small">{{ $dt(statusText()) }}</el-tag>
              </el-form-item>
              <el-form-item :label="$tf('优先级')" v-if="isFieldVisible('priority')" :required="isFieldRequired('priority')">
                <el-select v-model="workOrder.priority" class="field-input" :disabled="!isDraft">
                  <el-option :label="$tf('普通')" value="普通" />
                  <el-option :label="$tf('加急')" value="加急" />
                  <el-option :label="$tf('紧急')" value="紧急" />
                </el-select>
              </el-form-item>
              <!-- 计税方式（含税价 / 不含税价 / 不计税）：驱动明细区「含税价 / 不含税价」两列换算 -->
              <el-form-item v-if="taxEnabled && isFieldVisible('taxCalcMethod')" :label="$tf('计税方式')" :required="isFieldRequired('taxCalcMethod')">
                <el-select v-model="workOrder.taxCalcMethod" class="field-input" :disabled="!isDraft" @change="onTaxCalcMethodChange">
                  <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
                </el-select>
              </el-form-item>
            </div>
          </div>

          <!-- 计划信息 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>计划信息
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('计划开始')" v-if="isFieldVisible('planStartDate')" :required="isFieldRequired('planStartDate')">
                <el-date-picker v-model="workOrder.planStartDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('计划完工')" v-if="isFieldVisible('planEndDate')" :required="isFieldRequired('planEndDate')">
                <el-date-picker v-model="workOrder.planEndDate" type="date" value-format="YYYY-MM-DD" class="field-input" :disabled="!isDraft" />
              </el-form-item>
              <el-form-item :label="$tf('提前期')" v-if="isFieldVisible('leadTimeDays')" :required="isFieldRequired('leadTimeDays')">
                <el-input v-model="workOrder.leadTimeDays" class="field-input" readonly placeholder="—">
                  <template #append>{{ $tt("天") }}</template>
                </el-input>
              </el-form-item>
              <el-form-item :label="$tf('实际开始')" v-if="isFieldVisible('actualStartDate')" :required="isFieldRequired('actualStartDate')">
                <el-date-picker v-model="workOrder.actualStartDate" type="date" value-format="YYYY-MM-DD" class="field-input" disabled />
              </el-form-item>
              <el-form-item :label="$tf('实际完工')" v-if="isFieldVisible('actualEndDate')" :required="isFieldRequired('actualEndDate')">
                <el-date-picker v-model="workOrder.actualEndDate" type="date" value-format="YYYY-MM-DD" class="field-input" disabled />
              </el-form-item>
            </div>
          </div>

          <!-- 单据自定义字段（设置中心→自定义字段配置 中为「生产工单」定义的字段） -->
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
                  v-model="workOrder[field.prop]"
                  :placeholder="field.placeholder || '请选择'"
                  :filterable="field.filterable !== false"
                  class="field-input"
                  :disabled="!isDraft"
                >
                  <el-option v-for="opt in (field.options || [])" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                </el-select>
                <el-input-number
                  v-else-if="field.type === 'number'"
                  v-model="workOrder[field.prop]"
                  :precision="field.precision"
                  :min="field.min !== undefined ? field.min : 0"
                  :step="0.1"
                  controls-position="right"
                  class="field-input"
                  :disabled="!isDraft"
                />
                <el-switch
                  v-else-if="field.type === 'switch'"
                  v-model="workOrder[field.prop]"
                  :active-value="field.activeValue !== undefined ? field.activeValue : 1"
                  :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
                  :disabled="!isDraft"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="workOrder[field.prop]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :placeholder="field.placeholder || '选择日期'"
                  class="field-input"
                  :disabled="!isDraft"
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="workOrder[field.prop]"
                  type="textarea"
                  :rows="field.rows || 3"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!isDraft"
                />
                <el-input
                  v-else
                  v-model="workOrder[field.prop]"
                  :placeholder="field.placeholder || '请输入'"
                  class="field-input"
                  :disabled="!isDraft"
                />
              </el-form-item>
            </div>
          </div>
          </template>

          <!-- 其他 -->
          <div class="info-section">
            <div class="section-title">
              <span class="section-bar"></span>其他
            </div>
            <div class="section-fields">
              <el-form-item :label="$tf('备注')" class="span-all" v-if="isFieldVisible('remark')" :required="isFieldRequired('remark')">
                <el-input v-model="workOrder.remark" type="textarea" :rows="2" class="field-input" :disabled="!isDraft" :placeholder="$tt('请输入备注信息')" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </div>
    </div>

    <!-- 明细卡片（可折叠） -->
    <div class="collapsible-card" :class="{ collapsed: !collapsed.items }">
      <div class="card-header" @click="toggleCard('items')">
        <div class="header-left">
          <el-icon class="collapse-icon" :class="{ active: collapsed.items }"><ArrowRight /></el-icon>
          <span class="card-title">{{ $tt("生产明细") }}</span>
          <span class="item-count">工序 {{ progressData.operations?.length || 0 }} / 成品 {{ productList.length }} / 原料 {{ workOrder.materials?.length || 0 }}</span>
        </div>
        <div class="header-right" @click.stop>
          <SourcePicker ref="sourcePickerRef" target-type="WorkOrders" @pick="onSourcePicked" :disabled="!isDraft" btn-type="success" size="small" />
          <el-button v-permission="perm('refresh')" size="small" @click="loadProgress">
            <el-icon><RefreshLeft /></el-icon>刷新
          </el-button>
        </div>
      </div>
      <div v-show="collapsed.items" class="card-body no-padding">
        <div class="table-wrap" style="padding:0;">
          <el-tabs v-model="activeTab" type="border-card" class="detail-tabs">
            <!-- 工序进度 -->
            <el-tab-pane v-if="isEdit" :label="$tf('进度流程')" name="progress">
              <div v-if="progressData.operations && progressData.operations.length" class="progress-section">
                <!-- 报工/入库进度拆分 -->
                <div class="progress-overview">
                  <div class="progress-overview-card">
                    <div class="overview-header">
                      <span class="overview-title">{{ $tt("报工进度") }}</span>
                      <el-tag size="small" type="primary" effect="light">{{ $tt("合格量/计划量") }}</el-tag>
                    </div>
                    <div class="overview-body">
                      <el-progress
                        :percentage="reportedProgress"
                        :color="reportedProgressColor"
                        :stroke-width="14"
                        :text-inside="true"
                      />
                      <div class="overview-meta">
                        <span>{{ $tt("已报工：") }}<b class="num primary">{{ reportedQuantity }}</b></span>
                        <span>{{ $tt("计划量：") }}<b class="num">{{ totalPlanQty }}</b></span>
                        <span>{{ $tt("报废：") }}<b class="num danger">{{ totalRejectedQty }}</b></span>
                      </div>
                    </div>
                  </div>
                  <div class="progress-overview-card">
                    <div class="overview-header">
                      <span class="overview-title">{{ $tt("入库进度") }}</span>
                      <el-tag size="small" type="success" effect="light">{{ $tt("入库量/计划量") }}</el-tag>
                    </div>
                    <div class="overview-body">
                      <el-progress
                        :percentage="receiptProgress"
                        :color="receiptProgressColor"
                        :stroke-width="14"
                        :text-inside="true"
                        :status="receiptProgress >= 100 ? 'success' : undefined"
                      />
                      <div class="overview-meta">
                        <span>{{ $tt("已入库：") }}<b class="num success">{{ totalCompletedQty }}</b></span>
                        <span>{{ $tt("计划量：") }}<b class="num">{{ totalPlanQty }}</b></span>
                        <span>{{ $tt("未入库：") }}<b class="num warning">{{ receiptRemaining }}</b></span>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="section-title-bar">
                  <span>{{ $tt("工序进度") }}</span>
                  <el-tag size="small" type="info">{{ $tt("点击工序卡片打开工序操作面板") }}</el-tag>
                  <el-button v-permission="perm('view')" type="primary" plain size="small" @click="printBatchOpCards">
                    <el-icon><Printer /></el-icon>{{ $tt("批量打印工序卡") }}
                  </el-button>
                </div>

                <!-- 第10期：按成品分组，每个成品一块（标题=成品名+数量），块内是该成品的工序线路 -->
                <div v-for="g in progressGroups" :key="g.key" class="product-progress-block">
                  <div class="pp-head">
                    <el-icon class="pp-icon"><Grid /></el-icon>
                    <span class="pp-name">{{ g.productName || $tt('未指定成品') }}</span>
                    <el-tag v-if="g.quantity != null" size="small" type="primary" effect="plain">{{ $tt("数量") }} {{ g.quantity }}</el-tag>
                    <span class="pp-count">{{ g.ops.length }} {{ $tt("道工序") }}</span>
                  </div>
                  <div class="timeline-flow">
                  <div
                    v-for="(op, idx) in mainOps(g)"
                    :key="opKey(op)"
                    class="timeline-step"
                    :class="stepClass(op)"
                  >
                    <!-- 点击工序卡片：父工序（有子工序）→ 弹子工序弹窗；其余 → 工序操作面板 -->
                    <div class="step-node" @click="onProgressNodeClick(op)">
                      <div class="op-dot">
                        <el-icon v-if="op.status === '已完工'" class="check-icon"><CircleCheck /></el-icon>
                        <el-icon v-else-if="['已开工','进行中'].includes(op.status)" class="loading-icon"><Loading /></el-icon>
                        <span v-else>{{ idx + 1 }}</span>
                      </div>
                      <div class="op-info">
                        <div class="step-name">{{ op.operationName }}</div>
                        <div class="step-qty">
                          {{ op.reportedQty || 0 }} / {{ op.planQty }}
                        </div>
                        <div class="step-tags">
                          <el-tag v-if="childCountOf(op) > 0" size="small" type="primary" effect="plain">{{ $tt("总") }}·{{ childCountOf(op) }}</el-tag>
                          <el-tag v-if="op.isCritical === 1" size="small" type="danger" effect="dark">{{ $tt("关键") }}</el-tag>
                          <el-tag v-else-if="op.isQcRequired === 1" size="small" type="warning">{{ $tt("质检") }}</el-tag>
                          <el-tag size="small" :type="opTagType(op.status)">{{ $dt(op.status) }}</el-tag>
                        </div>
                      </div>
                    </div>
                    <div v-if="idx < mainOps(g).length - 1" class="step-connector" :class="{ done: op.status === '已完工' }">
                      <div class="connector-bar" />
                    </div>
                  </div>
                  </div>
                </div>

              </div>

              <el-empty v-else :description="$tt('该工单还没有工序：请到「成品明细」里为成品选择「工艺路线」，系统会自动带出该成品的工序；也可在成品的「工序」弹窗里手工添加')">
                <el-button v-permission="perm('view')" type="primary" @click="goConfigRouting">{{ $tt("去配置工艺路线") }}</el-button>
              </el-empty>

              <!-- 第10期：成品工序编辑弹窗（每个成品一个；沿用第7期已具备的能力：勾选/拖动排序/增删/改负责人与计划完工） -->
              <el-dialog
                v-model="opDialogVisible"
                :title="`${$tt('工序配置')} · ${dialogProduct ? (dialogProduct.materialName || dialogProduct.materialCode || '') : ''}`"
                width="1120px"
                top="6vh"
                append-to-body
                class="op-dialog"
              >
                <div v-if="dialogProduct" class="op-dialog-body">
                  <div class="op-dialog-sub">
                    <span>{{ $tt("工艺路线：") }}<b>{{ routingName(dialogProduct.routingId) || $tt('未选择') }}</b></span>
                    <el-tag v-if="dialogProduct.routingVersion" size="small" effect="plain">{{ dialogProduct.routingVersion }}</el-tag>
                    <span class="op-dialog-qty">{{ $tt("成品数量：") }}{{ dialogProduct.quantity ?? 0 }}</span>
                  </div>

                  <div v-if="opsConfigurable" class="op-toolbar">
                    <el-button v-permission="perm('addRow')" type="primary" size="small" @click="bringInForDialog">
                      <el-icon><MagicStick /></el-icon>{{ $tt("按工艺路线带入全部工序") }}
                    </el-button>
                    <el-select
                      v-model="dialogAddOperationId"
                      v-permission="perm('addRow')"
                      filterable
                      clearable
                      :placeholder="$tt('添加工序（从工序/工位主档选择）')"
                      class="op-add-select"
                      @change="onDialogAddOperationChange"
                    >
                      <el-option
                        v-for="w in workstationOptions"
                        :key="w.workstationId"
                        :label="`${w.workstationCode || ''} - ${w.workstationName || ''}`"
                        :value="w.workstationId"
                      />
                    </el-select>
                    <span class="op-toolbar-tip">{{ opsDraggable ? $tt('拖动行可调整工序顺序') : $tt('已生效工单的工序顺序已锁定') }}</span>
                  </div>

                  <div v-if="dialogOps.length" class="op-table-wrap">
                    <table class="op-config-table">
                      <colgroup>
                        <col v-for="c in opConfigCols" :key="c.key" :style="opConfigWidths[c.key] ? { width: opConfigWidths[c.key] + 'px' } : null" />
                      </colgroup>
                      <thead>
                        <tr>
                          <th v-for="c in opConfigCols" :key="c.key" :class="c.cls">
                            <el-checkbox v-if="c.kind === 'check'" v-model="dialogAllSelected" :indeterminate="dialogIndeterminate" />
                            <template v-else>{{ $tf(c.label) }}</template>
                            <!-- 列宽拖拽手柄：除非显式标 resizable:false（勾选列/拖拽列），其余列都可拖 -->
                            <span v-if="c.resizable !== false" class="op-col-resizer" @mousedown.stop.prevent="startOpColResize($event, c.key)" @click.stop></span>
                          </th>
                        </tr>
                      </thead>
                      <draggable
                        v-model="dialogOps"
                        tag="tbody"
                        item-key="_k"
                        handle=".op-drag-handle"
                        :disabled="!opsDraggable"
                        @end="onOpsDragEnd"
                      >
                        <template #item="{ element: row, index }">
                          <tr>
                            <td v-if="opsSelectable" class="op-col-check">
                              <el-checkbox v-model="row._selected" />
                            </td>
                            <td v-if="opsDraggable" class="op-col-drag">
                              <span class="op-drag-handle"><el-icon><Rank /></el-icon></span>
                            </td>
                            <td class="op-col-no">{{ (index + 1) * 10 }}</td>
                            <td>{{ row.operationCode || '' }}</td>
                            <td>{{ row.operationName || '' }}</td>
                            <td>{{ operationWorkCenterName(row) }}</td>
                            <td>{{ row.operationType || '' }}</td>
                            <!-- 计件单价（工序单价）+ 含税价 / 不含税价（按表头「计税方式」换算，两列只读） -->
                            <td class="op-col-edit">
                              <el-input-number v-if="opsConfigurable" v-model="row.pieceRate" :min="0" :precision="4" controls-position="right" size="small" class="op-mini-num" @change="() => calcOpRow(row)" />
                              <span v-else>{{ row.pieceRate ?? 0 }}</span>
                            </td>
                            <td class="op-col-right">{{ Number(row.priceWithTax || 0).toFixed(4) }}</td>
                            <td class="op-col-right">{{ Number(row.priceWithoutTax || 0).toFixed(4) }}</td>
                            <td class="op-col-edit">
                              <el-input-number v-if="opsConfigurable" v-model="row.planQuantity" :min="0" :precision="scaleFor('qty')" controls-position="right" size="small" class="op-mini-num" />
                              <span v-else>{{ row.planQuantity ?? 0 }}</span>
                            </td>
                            <td class="op-col-edit">
                              <el-date-picker v-if="opsConfigurable" v-model="row.planEndDate" type="date" value-format="YYYY-MM-DD" size="small" class="op-mini-date" />
                              <span v-else>{{ row.planEndDate || '' }}</span>
                            </td>
                            <td class="op-col-edit">
                              <el-select v-if="opsConfigurable" v-model="row.assigneeId" filterable clearable size="small" class="op-mini-select" @change="(v) => onAssigneeChange(row, v)">
                                <el-option v-for="e in employeeOptions" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
                              </el-select>
                              <span v-else>{{ row.assigneeName || '' }}</span>
                            </td>
                            <td class="op-col-flag">
                              <el-switch v-if="opsConfigurable" v-model="row.isQcRequired" :active-value="1" :inactive-value="0" size="small" />
                              <el-tag v-else size="small" :type="row.isQcRequired === 1 ? 'warning' : 'info'">{{ row.isQcRequired === 1 ? $tt('是') : $tt('否') }}</el-tag>
                            </td>
                            <!-- 第 12 期：质检模板 —— 默认不勾「需质检」时本列为空（不显示模板选择） -->
                            <td class="op-col-edit">
                              <el-select v-if="opsConfigurable && row.isQcRequired === 1" v-model="row.qcTemplateId" filterable clearable size="small" class="op-mini-select" :placeholder="$tt('选择质检模板')">
                                <el-option v-for="t in qcTemplateOptions" :key="t.templateId" :label="t.templateName" :value="t.templateId" />
                              </el-select>
                              <span v-else-if="row.isQcRequired === 1">{{ qcTemplateName(row.qcTemplateId) }}</span>
                            </td>
                            <td class="op-col-flag">
                              <el-switch v-if="opsConfigurable" v-model="row.isCritical" :active-value="1" :inactive-value="0" size="small" />
                              <el-tag v-else size="small" :type="row.isCritical === 1 ? 'danger' : 'info'">{{ row.isCritical === 1 ? $tt('是') : $tt('否') }}</el-tag>
                            </td>
                            <td class="op-col-right">{{ row.reportedQuantity || 0 }}</td>
                            <td class="op-col-right">{{ row.rejectedQuantity || 0 }}</td>
                            <td class="op-col-status">
                              <span class="op-status-cell">
                                <i class="op-status-dot" :style="{ background: opStatusColor(row.operationStatus) }"></i>
                                <el-tag size="small" :type="opStatusStyle(row.operationStatus).tag" effect="light">{{ $dt(row.operationStatus || '待派工') }}</el-tag>
                              </span>
                            </td>
                            <!-- 【2026-11-25】生产工序自定义字段：勾选、文本、数字、下拉、日期按类型渲染 -->
                            <td v-for="cf in opCustomDefs" :key="'cf_' + cf.fieldKey" class="op-col-edit op-col-custom">
                              <!-- 【2026-11-26】每道工序只显示自己的字段：不属于本工序的字段整格留空 -->
                              <template v-if="rowHasCf(row, cf)">
                              <el-select
                                v-if="cf.fieldType === 'select'"
                                :model-value="opCustomVal(row, cf)"
                                filterable
                                clearable
                                size="small"
                                class="op-mini-select"
                                :disabled="!opsConfigurable"
                                @update:model-value="v => setOpCustomVal(row, cf, v)"
                              >
                                <el-option v-for="opt in opCustomOptions(cf)" :key="opt.value" :label="opt.label" :value="opt.value" />
                              </el-select>
                              <el-input-number
                                v-else-if="cf.fieldType === 'number'"
                                :model-value="opCustomVal(row, cf)"
                                :precision="cf.precisionVal || 2"
                                :min="cf.minValue != null ? Number(cf.minValue) : 0"
                                :max="cf.maxValue != null ? Number(cf.maxValue) : undefined"
                                :controls="false"
                                size="small"
                                class="op-mini-num"
                                :disabled="!opsConfigurable"
                                @update:model-value="v => setOpCustomVal(row, cf, v)"
                              />
                              <el-switch
                                v-else-if="cf.fieldType === 'switch'"
                                :model-value="opCustomVal(row, cf)"
                                :active-value="1"
                                :inactive-value="0"
                                size="small"
                                :disabled="!opsConfigurable"
                                @update:model-value="v => setOpCustomVal(row, cf, v)"
                              />
                              <el-date-picker
                                v-else-if="cf.fieldType === 'date' || cf.fieldType === 'datetime'"
                                :model-value="opCustomVal(row, cf)"
                                :type="cf.fieldType === 'datetime' ? 'datetime' : 'date'"
                                :value-format="cf.fieldType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
                                size="small"
                                class="op-mini-date"
                                :disabled="!opsConfigurable"
                                @update:model-value="v => setOpCustomVal(row, cf, v)"
                              />
                              <span v-else-if="cf.fieldType === 'formula'" class="op-cf-readonly">{{ opCustomVal(row, cf) ?? '' }}</span>
                              <el-input
                                v-else
                                :model-value="opCustomVal(row, cf)"
                                :type="cf.fieldType === 'textarea' ? 'textarea' : 'text'"
                                :rows="1"
                                size="small"
                                :placeholder="cf.placeholder || ''"
                                :disabled="!opsConfigurable"
                                @update:model-value="v => setOpCustomVal(row, cf, v)"
                              />
                              </template>
                              <span v-else></span>
                            </td>
                            <td class="op-col-op">
                              <!-- 第2期能力保留：已生效的工单可点「操作」打开工序操作面板（派工/开工/报工/回退/判废/下推补料） -->
                              <el-button v-if="row.id && form.status === '已生效'" v-permission="perm('view')" type="primary" link @click="openOpPanel(row)">
                                {{ $tt("操作") }}
                              </el-button>
                              <!-- 第11期：已生效工单里「已取消」的工序允许删除（回程②：删掉重新添加） -->
                              <el-button v-if="opsConfigurable || row.operationStatus === '已取消'" v-permission="perm('deleteRow')" type="danger" link @click="removeDialogOperation(index)">
                                <el-icon><Delete /></el-icon>{{ $tt("删除") }}
                              </el-button>
                            </td>
                            <td class="op-col-op op-col-card">
                              <div v-if="row.id" class="op-qr-cell">
                                <!-- 工序二维码（2026-09-27）：常驻显示，手机对屏即可扫，无需先点打印；权限复用「打印」。
                                     表格行内空间窄，故用竖向码牌 + 单行小标题，与「打印」并排，避免把行撑高。 -->
                                <QrCodeCard
                                  v-permission="perm('print')"
                                  type="OP"
                                  layout="vertical"
                                  :biz-id="row.id"
                                  :size="50"
                                  :title="$tt('工序码')"
                                  hint=""
                                />
                                <!-- 工序卡（含二维码）：扫工序码可直达该工序操作（2026-09-27） -->
                                <el-button v-permission="perm('view')" type="primary" link @click="printOpCard(row)">
                                  {{ $tt("打印") }}
                                </el-button>
                              </div>
                              <span v-else>—</span>
                            </td>
                          </tr>
                        </template>
                      </draggable>
                    </table>
                  </div>
                  <div v-else class="op-empty-tip">
                    {{ $tt("该成品还没有工序：可「按工艺路线带入全部工序」，或「添加工序」从「工序/工位」主档挑选") }}
                  </div>
                </div>

                <template #footer>
                  <el-button @click="opDialogVisible = false">{{ $tt("关闭") }}</el-button>
                  <!-- 第11期：保存入口收敛 —— 仅「已生效且原本无工序」的补选场景出现「保存工序」 -->
                  <el-button v-if="opsSupplementMode" v-permission="perm('addRow')" type="primary" :loading="opsSaving" @click="saveFromDialog">
                    <el-icon><Check /></el-icon>{{ $tt("保存工序") }}
                  </el-button>
                </template>
              </el-dialog>
            </el-tab-pane>

            <!-- 工序操作面板（弹窗挂在页签内即可，el-dialog 会 teleport 到 body） -->
            <OperationPanel v-model="opPanelVisible" :operation="currentOperation" @changed="onOperationChanged" />

            <!-- 【第 19 期】子工序圆环链弹窗（点父工序「总」卡片打开；多层下钻、右上角 × 关闭） -->
            <OperationChildDialog
              v-model="childDialogVisible"
              :parent-op="childDialogParent"
              :all-ops="childDialogOps"
              @open-panel="onChildLeafOpen"
            />

            <!-- 成品明细 -->
            <el-tab-pane :label="$tf('成品明细')" name="products">
              <!-- 成品明细：统一到通用明细组件（保留按状态切换的"删除 / 进度流程"操作列） -->
              <BillItemsTable
                v-model:collapsed="productsOpen"
                :items="productList"
                page-key="WorkOrderEdit_products"
                title="成品明细"
                :show-summary="false"
                :operation-width="130"
                :columns="productColumns"
                row-clickable
                :row-class-name="productRowClassName"
                @row-click="onProductRowClick"
              >
                <template #actions>
                  <el-button v-permission="perm('addRow')" v-if="isDraft" type="primary" size="small" @click="addProductRow">
                    <el-icon><Plus /></el-icon>添加成品
                  </el-button>
                  <!-- 2026-11-07：与销售订单同款物料库弹窗，替代“只能行内下拉逐个选”的方式 -->
                  <el-button v-permission="perm('addRow')" v-if="isDraft" size="small" @click="productPickerVisible = true">
                    <el-icon><Grid /></el-icon>从物料库选择
                  </el-button>
                </template>
                <template #cell-materialMainImage="{ row }">
                  <el-image
                    v-if="row.materialMainImage"
                    :src="row.materialMainImage"
                    :preview-src-list="[row.materialMainImage]"
                    fit="cover"
                    preview-teleported
                    hide-on-click-modal
                    class="material-thumb"
                  />
                  <span v-else class="thumb-placeholder">{{ $tt("无图") }}</span>
                </template>
                <template #cell-materialId="{ row }">
                  <el-select
                    v-if="isDraft"
                    v-model="row.materialId"
                    filterable
                    :placeholder="materialSelectPlaceholder"
                    class="table-select"
                    size="small"
                    :teleported="true"
                    :popper-options="{ strategy: 'fixed' }"
                    popper-class="material-select-popper"
                    :filter-method="(val) => productMaterialFilter(val, row)"
                    @change="onProductMaterialChange(row)"
                  >
                    <el-option v-for="m in (row._filteredMaterials || productCandidates(row))" :key="m.materialId" :label="`${m.materialCode} - ${m.materialName}`" :value="m.materialId">
                      <span class="opt-code">{{ m.materialCode }}</span>
                      <span class="opt-name">{{ m.materialName }}</span>
                      <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
                    </el-option>
                  </el-select>
                  <span v-else>{{ row.materialCode || '—' }}</span>
                </template>
                <template #cell-quantity="{ row }">
                  <el-input-number v-if="isDraft" v-model="row.quantity" :min="0" :precision="scaleFor('qty')" controls-position="right" style="width:100%" @change="onProductQuantityChange(row)" />
                  <span v-else>{{ row.quantity || 0 }}</span>
                </template>
                <template #cell-unitName="{ row }">
                  <UnitPickerCell
                    v-model="row.unitId"
                    :units="units"
                    :material="materialOf(row)"
                    :disabled="!isDraft"
                    @change="onProductUnitChange(row)"
                  />
                </template>
                <!-- 2026-11-07：成品行的行级 BOM（草稿可逐行选；非草稿只读回显） -->
                <template #cell-bomCode="{ row }">
                  <el-select
                    v-if="isDraft"
                    v-model="row.bomId"
                    filterable
                    clearable
                    size="small"
                    class="table-select"
                    :disabled="row.materialId == null"
                    :placeholder="bomPlaceholderOf(row)"
                    :no-data-text="bomNoDataTextOf(row)"
                    @change="onRowBomChange(row)"
                  >
                    <el-option v-for="b in bomOptionsForRow(row)" :key="b.bomId" :label="`${b.bomCode} / ${b.bomVersion || ''}`" :value="b.bomId" />
                  </el-select>
                  <span v-else>{{ row.bomCode || '' }}</span>
                </template>
                <template #cell-bomVersion="{ row }">
                  <span>{{ row.bomVersion || '' }}</span>
                </template>
                <!-- 第10期：成品行自己的「工艺路线」下拉；选完自动按该路线带出该成品的全部工序 -->
                <template #cell-routingId="{ row }">
                  <el-select
                    v-if="isDraft"
                    v-model="row.routingId"
                    filterable
                    clearable
                    :placeholder="$tt('选择工艺路线')"
                    class="field-input"
                    @change="(v) => onProductRoutingChange(row, v)"
                  >
                    <el-option v-for="r in routingOptionsFor(row)" :key="r.routingId" :label="`${r.routingCode} - ${r.routingName}`" :value="r.routingId" />
                  </el-select>
                  <span v-else>{{ routingName(row.routingId) || '—' }}</span>
                </template>
                <!-- 第10期：「工序」列：显示该成品行的工序数，点开该成品的工序编辑弹窗 -->
                <template #cell-operationCount="{ row }">
                  <el-button type="primary" link @click="openOpDialog(row)">
                    {{ $tt("共") }} {{ opsOfProduct(row).length }} {{ $tt("道") }}
                  </el-button>
                </template>
                <template #operation="{ index }">
                  <el-button v-permission="perm('deleteRow')" v-if="isDraft" type="danger" link @click="removeProductRow(index)">
                    <el-icon><Delete /></el-icon>删除
                  </el-button>
                  <el-button v-permission="perm('view')" v-else-if="isEdit" type="danger" link @click="activeTab = 'progress'">{{ $tt("进度流程") }}</el-button>
                </template>
              </BillItemsTable>

              <!-- 原料明细：统一到通用明细组件（操作列按状态整列显隐 = 原 v-if="isDraft"） -->
              <BillItemsTable
                v-model:collapsed="materialsOpen"
                :items="displayedMaterials"
                page-key="WorkOrderEdit_materials"
                title="原料明细"
                :show-summary="false"
                :operation-column="isDraft"
                operation-width="90"
                :columns="materialColumns"
              >
                <template #actions>
                  <span v-if="activeProductPid" style="font-size:12px;color:#606266;margin-right:12px">
                    当前查看：{{ activeProductLabel }}
                    <el-button link type="primary" size="small" @click="clearProductFilter">显示全部</el-button>
                  </span>
                  <el-button v-permission="perm('addRow')" v-if="isDraft" type="primary" size="small" @click="addMaterialRow">
                    <el-icon><Plus /></el-icon>添加原料
                  </el-button>
                </template>
                <template #cell-materialMainImage="{ row }">
                  <el-image
                    v-if="row.materialMainImage"
                    :src="row.materialMainImage"
                    :preview-src-list="[row.materialMainImage]"
                    fit="cover"
                    preview-teleported
                    hide-on-click-modal
                    class="material-thumb"
                  />
                  <span v-else class="thumb-placeholder">{{ $tt("无图") }}</span>
                </template>
                <template #cell-materialId="{ row }">
                  <el-select
                    v-if="isDraft"
                    v-model="row.materialId"
                    filterable
                    :placeholder="materialSelectPlaceholder"
                    class="table-select"
                    size="small"
                    :teleported="true"
                    :popper-options="{ strategy: 'fixed' }"
                    popper-class="material-select-popper"
                    :filter-method="(val) => materialMaterialFilter(val, row)"
                    @change="onMaterialRowChange(row)"
                  >
                    <el-option v-for="m in (row._filteredMaterials || materialCandidates(row))" :key="m.materialId" :label="`${m.materialCode} - ${m.materialName}`" :value="m.materialId">
                      <span class="opt-code">{{ m.materialCode }}</span>
                      <span class="opt-name">{{ m.materialName }}</span>
                      <span v-for="cf in mcf.dropText(m)" :key="cf.key" class="opt-cf">{{ cf.label }}：{{ cf.value }}</span>
                    </el-option>
                  </el-select>
                  <span v-else>{{ row.materialCode || '—' }}</span>
                </template>
                <!-- 仓库同样作为明细列（整单一个仓库，改任意一行即整单生效） -->
                <template #cell-warehouseName>
                  <el-select
                    v-model="workOrder.warehouseId"
                    filterable
                    size="small"
                    class="table-select"
                    :disabled="!isDraft"
                    :placeholder="$tt('选择仓库')"
                  >
                    <el-option v-for="w in withFallbackOption(warehouseOptions, 'warehouseId', 'warehouseName', workOrder)" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
                  </el-select>
                </template>
                <template #cell-requiredQuantity="{ row }">
                  <el-input-number v-if="isDraft" v-model="row.requiredQuantity" :min="0" :precision="scaleFor('qty')" controls-position="right" style="width:100%" />
                  <span v-else>{{ row.requiredQuantity || row.quantity || 0 }}</span>
                </template>
                <template #cell-issuedQty="{ row }">
                  <span :style="{ color: (row.issuedQty || row.issuedQuantity) > 0 ? '#67C23A' : '#909399', fontWeight: 600 }">
                    {{ row.issuedQty || row.issuedQuantity || 0 }}
                  </span>
                </template>
                <template #operation="{ index }">
                  <el-button v-permission="perm('deleteRow')" type="danger" link @click="removeMaterialRow(index)">
                    <el-icon><Delete /></el-icon>删除
                  </el-button>
                </template>
              </BillItemsTable>

              <!-- 金额汇总（生产统计） -->
              <div class="summary-area">
                <div class="summary-block">
                  <div class="block-title">{{ $tt("生产统计") }}</div>
                  <div class="sum-line">
                    <span class="label">{{ $tt("原料需求总量：") }}</span>
                    <span class="value">{{ totalQty }}</span>
                  </div>
                  <div class="sum-line">
                    <span class="label">{{ $tt("计划数量：") }}</span>
                    <span class="value">{{ totalPlanQty }}</span>
                  </div>
                  <div class="sum-line">
                    <span class="label">{{ $tt("完工数量：") }}</span>
                    <span class="value" style="color:#67c23a;">{{ totalCompletedQty }}</span>
                  </div>
                  <div class="sum-line">
                    <span class="label">{{ $tt("报废数量：") }}</span>
                    <span class="value" style="color:#f56c6c;">{{ totalRejectedQty }}</span>
                  </div>
                  <div class="sum-line total">
                    <span class="label">{{ $tt("完成率：") }}</span>
                    <span class="value">{{ totalProgress }}%</span>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- 汇报记录 -->
            <el-tab-pane v-if="isEdit" :label="$tf('汇报记录')" name="reports">
              <el-table :data="progressData.recentReports || []" border size="small" class="compact-table">
                <el-table-column prop="reportNo" :label="$tf('汇报单号')" min-width="160">
                  <template #default="{ row }">
                    <el-link type="primary" :underline="false" @click="goToReport(row)">{{ row.reportNo }}</el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="reportDate" :label="$tf('汇报日期')" width="120" />
                <el-table-column prop="operationName" :label="$tf('工序')" width="120" />
                <el-table-column prop="processNo" :label="$tf('工序号')" width="90" />
                <el-table-column prop="employeeName" :label="$tf('操作工')" width="100" />
                <el-table-column :label="$tf('数量')" width="200" align="center">
                  <template #default="{ row }">
                    总: {{ row.totalQuantity || 0 }}
                    合格: <span class="text-success">{{ row.qualifiedQuantity || 0 }}</span>
                    报废: <span class="text-danger">{{ row.rejectedQuantity || 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="workHours" :label="$tf('工时(h)')" width="90" align="right" />
                <el-table-column prop="shiftType" :label="$tf('班次')" width="80" />
                <el-table-column prop="remark" :label="$tf('备注')" min-width="150" show-overflow-tooltip />
                <el-table-column prop="status" :label="$tf('状态')" width="90">
                  <template #default="{ row }">
                    <el-tag size="small">{{ $dt(row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
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
          v-model="workOrder.attachmentUrls"
          type="work-order"
          :disabled="!isDraft"
          button-text="上传附件"
          tip-text="支持图片、PDF、Word、Excel 等文件"
        />
      </div>
    </div>

    <!-- 审核流转（真实审批进度，通用组件） -->
    <ApprovalFlowTimeline bill-type="PRODUCTION_ORDER" :bill-id="workOrder.orderId" :status="form.status" />

    <!-- 关闭工单弹窗 -->
    <el-dialog v-model="closeDialog" :title="$tf('关闭工单')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$tf('关闭原因')">
          <el-input v-model="closeReason" type="textarea" :rows="3" :placeholder="$tt('请输入关闭原因')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="closeDialog = false">{{ $tt("返回") }}</el-button>
        <el-button v-permission="perm('dlgConfirm')" type="warning" :loading="acting" @click="doClose">{{ $tt("确认关闭") }}</el-button>
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

    <!-- 操作日志弹窗 -->
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="生产工单" :bill-no="workOrder.orderNo || ''" />

    <!-- 打印预览弹窗 -->
    <PrintPreviewDialog v-model="printVisible" bill-type="WORK_ORDER" :bill-id="workOrder.orderId" />
    <PrintPreviewDialog v-model="opCardVisible" bill-type="WORK_OPERATION" :bill-id="opCardId" />
    <BatchLabelPrintDialog v-model="opBatchVisible" type="WORK_OPERATION" :ids="opCardBatchIds" />
      </el-tab-pane>

      <el-tab-pane v-if="form.orderId" :label="$tf('批生产记录')" name="batchrecord">
        <BatchRecordPanel v-if="topTab === 'batchrecord'" :work-order-id="form.orderId" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('来源单据')" name="source" :disabled="!form.orderId">
        <BillLinkPanel v-if="topTab === 'source'" bill-type="PRODUCTION_ORDER" :bill-id="form.orderId" mode="source" />
      </el-tab-pane>

      <el-tab-pane v-if="billLinkRegistered" :label="$tf('去向单据')" name="target" :disabled="!form.orderId">
        <BillLinkPanel v-if="topTab === 'target'" bill-type="PRODUCTION_ORDER" :bill-id="form.orderId" mode="target" />
      </el-tab-pane>
    </el-tabs>

    <!-- 从物料库批量添加成品行（与销售订单同款物料库弹窗） -->
    <MaterialPickerDialog v-model="productPickerVisible" :title="$tt('从物料库选择成品')" :material-types="PRODUCT_MATERIAL_TYPES" @confirm="onProductsPicked" />
  </div>

  </template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Document, Collection, Plus, Grid, Search, Delete, Printer, Link,
  Check, TopRight, ArrowRight, CircleCheck, CircleClose, RefreshLeft,
  Share, Close, Upload, Edit, Refresh, Loading, Clock, Bottom, MagicStick, Memo, Rank
} from '@element-plus/icons-vue'
import {
  getWorkOrderProgress, getWorkOrder, createWorkOrder, updateWorkOrder,
  submitWorkOrder, approveWorkOrder, closeWorkOrder,
  pushRequisition, pushReceipt,
  rejectWorkOrder, reverseApproveWorkOrder, voidWorkOrder, unvoidWorkOrder,
  reopenWorkOrder, deleteWorkOrder, saveWorkOrderOperations
} from '@/api/production/workOrder'
import { useBillActions } from '@/composables/useBillActions'
import { useCopyBill } from '@/composables/useCopyBill'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { listAllUnit } from '@/api/basedata/unit'
// 第4期联动：选 BOM 带出子件用料（需要 BOM 详情），下拉选项统一走 api 封装（不在 .vue 内硬写 URL）
import { listAllBom, getBom } from '@/api/basedata/bom'
import { createAutoFillToken } from '@/utils/bomAutoFill'
import { listAllRouting, listApplicableRouting, getRouting } from '@/api/production/routing'
// 工序清单（可配置，2026-09-27）：工位/工序主档 + 工作中心主档 + 员工
import { listAllWorkstation } from '@/api/production/workstation'
import { listAllWorkCenter } from '@/api/production/workCenter'
import { listAllEmployee } from '@/api/basedata/employee'
// 第 12 期：质检模板（工序「需质检」开启时选择绑定模板）
import { listAllQcTemplates } from '@/api/qc/qcTemplate'
import { opStatusStyle, opStatusColor } from '@/utils/operationStatus'
import draggable from 'vuedraggable'
import { confirmSubmit, submitSuccessMessage } from '@/utils/submit-confirm'
import FileUploader from '@/components/FileUploader.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import PrintPreviewDialog from '@/components/PrintPreviewDialog.vue'
import BatchLabelPrintDialog from '@/components/BatchLabelPrintDialog.vue'
import QrCodeCard from '@/components/QrCodeCard.vue'
// 工序操作面板（2026-09-23 第 2 期）：按工序类型操作状态、派工、报工、查看操作日志
import OperationPanel from '@/components/production/OperationPanel.vue'
import OperationChildDialog from '@/components/production/OperationChildDialog.vue'
import { listWorkOrderOperation, deleteWorkOrderOperation } from '@/api/production/workOrderOperation'
import ApprovalFlowTimeline from '@/components/bill/ApprovalFlowTimeline.vue'
import BillLinkPanel from '@/components/bill/BillLinkPanel.vue'
// 化妆品批记录完整闭环（2026-11-06）：工单详情「批生产记录」页签
import BatchRecordPanel from '@/components/production/BatchRecordPanel.vue'
import BillItemsTable from '@/components/bill/BillItemsTable.vue'
import UnitPickerCell from '@/components/bill/UnitPickerCell.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { useColWidth } from '@/composables/useColWidth'
import { isBillLinkRegistered } from '@/api/billLink'
import SourcePicker from '@/components/SourcePicker.vue'
// 下推进入自动带入（2026-10-19）：与销售出库单/采购订单等编辑页同款机制，
// 修复「下推生成生产工单落地空白单」——此前本页不识别 srcType/srcId，下推进来什么都没带
import { applyPushSource } from '@/utils/pushSourceApplier'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

import * as BillStatus from '@/utils/bill-status'
import BillActions from '@/components/bill/BillActions.vue'
// 2026-11-07：下推统一组件（与销售订单同款，替代页面手写 el-dropdown）
import PushDownButton from '@/components/PushDownButton.vue'
import { triggerListRefresh } from '@/composables/useAutoRefresh'
import { tt } from '@/i18n'
import { useBillLineStock } from '@/composables/useBillLineStock'
import { usePagePerm } from '@/utils/pagePerm'
// 物料自定义字段（行内下拉搜索范围）+ 物料下拉的输入过滤/背景提示文字（通用件）
import { useMaterialCustomFields } from '@/composables/useMaterialCustomFields'
import { filterMaterials, buildMaterialPlaceholder } from '@/utils/materialFilter'
// 单据自定义字段（生产工单）：与销售订单同一套通用机制，实体标识 production_order
import { loadCustomFieldDefinitions, loadCustomFieldValues, saveCustomFieldValues, batchLoadCustomFieldValues } from '@/composables/useCustomField'
// 全站商业口径总开关（含税 / 外币）+ 设置中心参数（默认计税方式）：表头「计税方式」驱动明细含税价/不含税价换算
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useSysConfig } from '@/composables/useSysConfig'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { usePrecision } from '@/composables/usePrecision'

// 数量输入/显示的位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor } = usePrecision()

const perm = usePagePerm('production:workorders')
const { isFieldVisible, isFieldRequired } = useFieldPermission('production_order')
useBillLineStock(() => productList.value || [])
useBillLineStock(() => workOrder.materials || [])

// ===== 计税方式（含税价 / 不含税价 / 不计税）：表头驱动明细区「含税价 / 不含税价」换算（照销售订单试点） =====
const { taxEnabled, foreignCurrencyEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const { loadSysConfig, getConfig } = useSysConfig()
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
}

/** 按表头「计税方式」把「单价」换算成 含税价 / 不含税价 */
function convertPrice(price, taxRate) {
  const p = Number(price || 0)
  const method = form.taxCalcMethod || '不含税价'
  const factor = 1 + (Number(taxRate) || 0) / 100
  if (method === '含税价') return { withTax: p, withoutTax: factor ? Number((p / factor).toFixed(6)) : p }
  if (method === '不计税') return { withTax: p, withoutTax: p }
  return { withoutTax: p, withTax: Number((p * factor).toFixed(6)) }
}

/** 原料明细行：按表头「计税方式」换算「含税价 / 不含税价」并计算金额 */
function calcMaterialRow(row) {
  if (!row) return
  const qty = Number(row.requiredQuantity ?? row.quantity ?? 0)
  const price = Number(row.unitCost || 0)
  const conv = convertPrice(price, 0)
  row.priceWithTax = conv.withTax
  row.priceWithoutTax = conv.withoutTax
  row.totalCost = Number((qty * price).toFixed(2))
}

/** 工序行：按表头「计税方式」由「计件单价」换算「含税价 / 不含税价」 */
function calcOpRow(row) {
  if (!row) return
  const conv = convertPrice(Number(row.pieceRate || 0), 0)
  row.priceWithTax = conv.withTax
  row.priceWithoutTax = conv.withoutTax
}

/** 表头「计税方式」切换 → 原料明细 + 工序清单一起按新口径重算 */
function onTaxCalcMethodChange() {
  (workOrder.materials || []).forEach(r => calcMaterialRow(r))
  ;(operations.value || []).forEach(r => calcOpRow(r))
}

// 明细折叠态（通用明细组件 BillItemsTable 需 v-model:collapsed）
const productsOpen = ref(true)
const materialsOpen = ref(true)

// 明细列定义（列设置 pageKey：WorkOrderEdit_products / WorkOrderEdit_materials）
const productColumns = [
  { prop: 'materialMainImage', label: '图片', width: 70, align: 'center' },
  { prop: 'materialId', label: '成品编码', minWidth: 180 },
  { prop: 'stockQty', label: '库存量', width: 100, align: 'right', type: 'billStock' },
  { prop: 'stockAvailQty', label: '可用量', width: 100, align: 'right', type: 'billStock' },
  { prop: 'stockExpectedQty', label: '预计可用量', width: 110, align: 'right', type: 'billStock' },
  { prop: 'materialName', label: '成品名称', minWidth: 160 },
  { prop: 'specification', label: '规格型号', minWidth: 130 },
  // 2026-11-07：BOM 信息归成品行（行级），草稿可逐行选，非草稿只读回显
  { prop: 'bomCode', label: 'BOM清单', width: 210 },
  { prop: 'bomVersion', label: 'BOM版本', width: 100 },
  // 第10期：每个成品行各自的工艺路线 + 工序入口（点「共 N 道」打开该成品的工序弹窗）
  { prop: 'routingId', label: '工艺路线', width: 220 },
  { prop: 'operationCount', label: '工序', width: 110, align: 'center' },
  { prop: 'quantity', label: '计划数量', width: 140, align: 'right' },
  { prop: 'unitName', label: '单位', width: 80, align: 'center' },
  { prop: 'sourceNo', label: '来源单号', minWidth: 140 }
]
const materialColumns = [
  { prop: 'materialMainImage', label: '图片', width: 70, align: 'center' },
  { prop: 'materialId', label: '原料编码', minWidth: 180 },
  { prop: 'stockQty', label: '库存量', width: 100, align: 'right', type: 'billStock' },
  { prop: 'stockAvailQty', label: '可用量', width: 100, align: 'right', type: 'billStock' },
  { prop: 'stockExpectedQty', label: '预计可用量', width: 110, align: 'right', type: 'billStock' },
  { prop: 'materialName', label: '原料名称', minWidth: 160 },
  { prop: 'specification', label: '原料规格', minWidth: 120 },
  // 原料单位（2026-11-11 统一）：可选该物料的辅单位，库存/领料按折算后的基本单位记账；副单位/浮动列自动注入
  { prop: 'unitId', label: '单位', width: 110, align: 'center' },
  { prop: 'requiredQuantity', label: '需求数量', width: 140, align: 'right' },
  // 计税口径（2026-10-09）：原料明细补「单位成本 / 含税价 / 不含税价 / 金额」列（原页不展示金额，无法看到换算结果）
  { prop: 'unitCost', label: '单位成本', width: 120, editor: 'number', precision: 4, change: (row) => calcMaterialRow(row) },
  { prop: 'priceWithTax', label: '含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'priceWithoutTax', label: '不含税价', width: 95, align: 'right', type: 'amount4' },
  { prop: 'totalCost', label: '金额', width: 100, align: 'right', type: 'amount2' },
  { prop: 'issuedQty', label: '已领料', width: 100, align: 'right' },
  { prop: 'warehouseName', label: '仓库', width: 110 }
]

const route = useRoute()
const router = useRouter()
const orderId = route.params.id
const headerFormRef = ref(null)

// 单据自定义字段（生产工单）：设置中心「自定义字段配置」中为「生产工单」定义的字段
const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

// 是否编辑模式
const isEdit = computed(() => !!route.params.id)
const { docCodeDisabled, docCodePlaceholder, autoFillDocCodeOnCreate } = useAutoDocCode('WORK_ORDER')

const workOrder = reactive({
  orderId: null,
  orderNo: '',
  orderDate: new Date().toISOString().slice(0, 10),
  status: '草稿',
  priority: '普通',
  // 计税方式（含税价 / 不含税价 / 不计税）：驱动明细区「含税价 / 不含税价」换算；由 initTaxSettings 统一归一
  taxCalcMethod: '不含税价',
  materialId: null,
  materialCode: '',
  materialName: '',
  model: '',
  materialMainImage: '',
  bomId: null,
  bomVersion: '',
  routingId: null,
  routingVersion: '',
  quantity: 1,
  completedQuantity: 0,
  rejectedQuantity: 0,
  warehouseId: null,
  planStartDate: '',
  planEndDate: '',
  actualStartDate: '',
  actualEndDate: '',
  leadTimeDays: '',
  sourceType: 'MANUAL',
  sourceNo: '',
  salesOrderNo: '',
  remark: '',
  attachmentUrls: '',
  products: [],   // 成品明细列表
  materials: []    // 原料明细列表
})
const form = workOrder // 别名：兼容通用 BaseEdit 模板逻辑（billId / onSubmit / handleCopy 等）

const isDraft = computed(() => BillStatus.canEdit(form.status, form.closeStatus))
// 下推守卫（与后端 assertPushable 同口径）：只有「已生效」且未结案（close_status≠1）的工单可下推
const pushDownEnabled = computed(() => BillStatus.canPushDown(form.status, form.closeStatus))
const saving = ref(false)
const sourcePickerRef = ref()
const acting = ref(false)

// 折叠状态记忆
const STORAGE_KEY = 'work-order-edit-collapsed'
const collapsed = reactive({
  base: true,
  items: true,
  attach: false,
  audit: false,
})

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

const loading = ref(false)
// 2026-11-07：无论新建/草稿/已生效，进入工单详情默认落在「成品明细」
const activeTab = ref('products')
const changeRecordVisible = ref(false)
const closeDialog = ref(false)
const closeReason = ref('')

const materialOptions = ref([])
// 计量单位主档（多单位换算用：按物料带出可选单位与换算率）
const units = ref([])
// 物料自定义字段：行内下拉搜索范围（逻辑集中在 composable）
const mcf = useMaterialCustomFields()

// ===== 物料属性过滤（2026-11-10 用户口径）=====
// 成品明细只允许「成品 / 半成品 / 自制件」；原材料明细排除「成品」，其余属性全部可见。
// 属性取物料档案「物料属性」的中文值，不做额外映射。
const PRODUCT_MATERIAL_TYPES = ['成品', '半成品', '自制件']
const MATERIAL_EXCLUDED_TYPES = ['成品']
/** 成品行候选物料：只含自己生产/可自制的三类 */
const productMaterialBase = computed(() => filterByMaterialType(materialOptions.value, PRODUCT_MATERIAL_TYPES, false))
/** 原料行候选物料：排除成品 */
const materialMaterialBase = computed(() => filterByMaterialType(materialOptions.value, MATERIAL_EXCLUDED_TYPES, true))
/** 按属性白名单/黑名单过滤；属性为空或非标准值的物料：白名单不命中、黑名单不排除 */
function filterByMaterialType(list, types, isExclude) {
  const arr = Array.isArray(list) ? list : []
  return arr.filter(m => {
    if (!m) return false
    const t = (m.materialType || '').trim()
    return isExclude ? !types.includes(t) : types.includes(t)
  })
}
/** 行候选物料：过滤后的候选 + 该行已存物料（存量单据里属性不在范围内的老物料仍要正常回显） */
function candidateMaterials(base, row) {
  const list = base || []
  if (!row || row.materialId == null) return list
  if (list.some(m => m && m.materialId === row.materialId)) return list
  const cur = materialOptions.value.find(m => m && m.materialId === row.materialId)
  return cur ? [cur, ...list] : list
}
/** 成品行物料候选 */
function productCandidates(row) { return candidateMaterials(productMaterialBase.value, row) }
/** 原料行物料候选 */
function materialCandidates(row) { return candidateMaterials(materialMaterialBase.value, row) }

// 参与搜索的物料自定义字段：勾了「可搜索」的 ∪（下拉显示开关开启且勾了「下拉显示」的）
const materialSearchableFields = computed(() => (mcf.allFields.value || []).filter(f =>
  f && (f.isSearch === 1 || (mcf.enabled.value && f.isDropShow === 1))))
// 物料下拉的背景提示文字：声明可搜索范围（没有可搜索的自定义字段时原样返回「选择物料」）
const materialSelectPlaceholder = computed(() => buildMaterialPlaceholder(tt('选择物料'), materialSearchableFields.value))

/** 明细行物料下拉：输入即过滤（编码/名称/规格 + 自定义字段值/字段名），搜索范围限定在该行的候选物料内 */
function materialFilterMethod(val, row, base) {
  if (!val) { row._filteredMaterials = null; return }
  // 用「已预取全量值」的 mcf.valueMap，不能只用已选中物料的值表，否则搜不到
  row._filteredMaterials = filterMaterials(base || [], val, mcf.valueMap.value, materialSearchableFields.value)
}
/** 成品行搜索：只在这三类物料里搜，搜不出原材料 */
function productMaterialFilter(val, row) { materialFilterMethod(val, row, productCandidates(row)) }
/** 原料行搜索：排除成品 */
function materialMaterialFilter(val, row) { materialFilterMethod(val, row, materialCandidates(row)) }

const warehouseOptions = ref([])
const bomOptions = ref([])
// BOM 自动带出的并发令牌：只认最后一次选择，避免两套明细叠加（2026-10-26）
const bomAutoFill = createAutoFillToken()
const routingOptions = ref([])
/** 【2026-11-26】按成品物料缓存的适用工艺路线（后端按「物料 + 分类」判定） */
const routingOptionsByMaterial = ref({})

/** 该成品行可选的工艺路线：按该行物料过滤；没取到/没选物料时保留全部（不阻断录入） */
function routingOptionsFor (row) {
  if (!row || row.materialId == null) return routingOptions.value
  const list = routingOptionsByMaterial.value[String(row.materialId)]
  if (!list) return routingOptions.value
  // 当前已选路线不在适用清单里时保留它，避免回显空白
  if (row.routingId != null && !list.some(r => r.routingId === row.routingId)) {
    const cur = routingOptions.value.find(r => r.routingId === row.routingId)
    return cur ? [cur, ...list] : list
  }
  return list
}

/** 按物料取适用工艺路线（同一物料只请求一次） */
async function loadApplicableRoutings (row) {
  if (!row || row.materialId == null) return
  const key = String(row.materialId)
  if (routingOptionsByMaterial.value[key]) return
  try {
    const list = await listApplicableRouting(row.materialId)
    routingOptionsByMaterial.value = { ...routingOptionsByMaterial.value, [key]: Array.isArray(list) ? list : [] }
  } catch (e) {
    // 取不到就继续用全部路线，不阻断工单录入
  }
}

// 【2026-11-09】多成品行 BOM：点成品行「查看用料」切换原料区，只看该行的 BOM 明细
const activeProductPid = ref('')
const activeProduct = computed(() => (productList.value || []).find(p => p._pid === activeProductPid.value) || null)
const activeProductLabel = computed(() => activeProduct.value
  ? `${activeProduct.value.materialName || ''} ${activeProduct.value.bomCode || ''}`.trim()
  : '')
/** 原料区当前展示的行：未选成品行=全部；选中=该成品行自己的 BOM 用料 */
const displayedMaterials = computed(() => {
  const all = workOrder.materials || []
  if (!activeProductPid.value) return all
  // 归属统一由 _productPid 表示（详情加载时按原料行 order_product_id → 成品行 _pid 重建）
  return all.filter(m => m._productPid === activeProductPid.value)
})
function focusProductMaterials(row) {
  if (!row) return
  activeProductPid.value = row._pid || ''
  materialsOpen.value = true
}
function clearProductFilter() {
  activeProductPid.value = ''
}
/** 点成品行直接切换原料区视图（不再点"查看用料"按钮）；点行内控件不切换，避免选料/改数时视图乱跳 */
function onProductRowClick(row, column, event) {
  const el = event && event.target
  if (el && el.closest && el.closest('input, textarea, button, a, .el-select, .el-input, .el-input-number, .el-date-editor, .el-switch, .el-checkbox, .el-radio, .el-image')) return
  focusProductMaterials(row)
}
/** 当前正在查看用料的成品行高亮（与原料区显示的行一致） */
function productRowClassName({ row }) {
  return row && row._pid && row._pid === activeProductPid.value ? 'active-product-row' : ''
}

// ===== 工序清单（第10期：工序按「成品明细行」绑定） =====
const operations = ref([])            // 工单工序行（一维数组；每行用 _pid 记录属于哪个成品行）
const workstationOptions = ref([])    // 工序/工位主档（「添加工序」下拉）
const workCenterOptions = ref([])     // 工作中心主档（名称回显）
const employeeOptions = ref([])       // 员工（负责人下拉）
const qcTemplateOptions = ref([])     // 质检模板主档（第12期：工序「需质检」开启时选择绑定）
const opsSaving = ref(false)          // 「保存工序」进行中（只保存工序清单，不整单保存）
// 【2026-11-25】生产工序自定义字段：定义来自设置中心「自定义字段配置 → 生产工序」，
// 值按工序行 ID 存取（草稿保存会重建工序行，后端按旧行ID自动迁移，值不丢）
const opCustomDefs = ref([])
const opCustomFormulaFields = ref([])
// 服务端已落库的工序行数（2026-09-27 修复）：补选是否可用要按"已保存的"行数判断，
// 不能按当前编辑中的数组长度 —— 否则一添加工序行，工具栏（含「保存工序」）就整块消失，永远存不下去。
const savedOpCount = ref(0)

// 是否可增删改：草稿态；或「已生效且服务端一道工序都没有」（后端允许补选）
const opsConfigurable = computed(() =>
  isDraft.value || (!isDraft.value && workOrder.status === '已生效' && savedOpCount.value === 0))
// 第11期：仅"已生效且服务端原本一道工序都没有（补选场景）"才显示弹窗里的「保存工序」；
// 草稿态工序随顶部整单「保存」一起提交，不再单独出现「保存工序」入口（保存入口收敛）
const opsSupplementMode = computed(() =>
  !isDraft.value && workOrder.status === '已生效' && savedOpCount.value === 0)
// 是否允许拖动排序：仅草稿态
const opsDraggable = computed(() => isDraft.value)
// 「选用」勾选列：草稿/补选可见
const opsSelectable = computed(() => opsConfigurable.value)

// ===== 成品行 ↔ 工序行 绑定（第10期） =====
// 成品行用 _pid 做"前端稳定标识"：整单保存会把成品明细整表重建（主键会变），
// 因此开发期用 _pid 绑定，保存时再按成品行号把工序绑到成品行最新主键（见 persistOperations）。
let _pidSeq = 0
function newPid() { return 'p-' + Date.now().toString(36) + '-' + (++_pidSeq) }
function ensureProductPid(row) { if (row && !row._pid) row._pid = newPid(); return row }
function ensureProductsPid() { (workOrder.products || []).forEach(ensureProductPid) }
/** 某成品行下的工序 */
function opsOfProduct(row) {
  if (!row || !row._pid) return []
  return operations.value.filter(r => r._pid === row._pid)
}
/** 用新列表替换某成品行的工序（保持该成品在整单数组里的相对位置） */
function replaceProductOps(pid, list) {
  if (!pid) return
  const next = []
  let inserted = false
  for (const r of operations.value) {
    if (r._pid === pid) {
      if (!inserted) { next.push(...list); inserted = true }
    } else {
      next.push(r)
    }
  }
  if (!inserted) next.push(...list)
  operations.value = next
}
/** 工艺路线名称回显 */
function routingName(id) {
  if (id == null || id === '') return ''
  const r = routingOptions.value.find(x => x.routingId === id)
  return r ? `${r.routingCode || ''} - ${r.routingName || ''}`.replace(/^ - /, '') : ''
}
/** 工序行稳定键（同一路线工序可能出现在不同成品下，展开/折叠必须按"成品 + 工序"区分） */
function opKey(op) {
  return `${op?.orderProductId ?? 0}-${op?.operationId ?? ''}-${op?.lineNo ?? ''}`
}

// ===== 成品工序编辑弹窗（每个成品一个） =====
const opDialogVisible = ref(false)
const dialogProduct = ref(null)          // 当前弹窗对应的成品行
const dialogAddOperationId = ref(null)   // 弹窗内「添加工序」下拉当前值（选完即入列并清空）
// 弹窗里该成品的工序（get 取值 / set 回写整单数组，拖动排序走 setter）
const dialogOps = computed({
  get: () => opsOfProduct(dialogProduct.value),
  set: (list) => { if (dialogProduct.value) replaceProductOps(dialogProduct.value._pid, list) }
})
// 全选（默认全选）：按"当前弹窗里该成品的工序"计算
const dialogAllSelected = computed({
  get: () => dialogOps.value.length > 0 && dialogOps.value.every(r => r._selected !== false),
  set: (v) => { dialogOps.value.forEach(r => { r._selected = v }) }
})
const dialogIndeterminate = computed(() => {
  const sel = dialogOps.value.filter(r => r._selected !== false).length
  return sel > 0 && sel < dialogOps.value.length
})

// ===== 工序配置弹窗表：列宽拖拽 + 持久化（2026-10-08）=====
// 该表是可编辑表（行内输入 + 拖动排序），保留手写 <table> 以免破坏行内编辑与拖拽；
// 这里只补「列宽可拖 + 记住」：宽度写入 columnConfig store（与列显隐/顺序同一条记录，互不覆盖）。
const OP_CONFIG_PAGE_KEY = 'WorkOrderEditOpConfig'
const { getColWidth: getOpConfigColWidth, onHeaderDragend: onOpConfigHeaderDragend } = useColWidth(OP_CONFIG_PAGE_KEY)
/** 用户拖过的列宽 { key: px }（打开弹窗时从 store 恢复） */
const opConfigWidths = reactive({})
/** 弹窗表列清单（含条件列），用于表头渲染 + 列宽拖拽；key 同时作为列宽记忆的列标识 */
const opConfigCols = computed(() => {
  const cols = []
  if (opsSelectable.value) cols.push({ key: '__check', cls: 'op-col-check', kind: 'check', resizable: false })
  if (opsDraggable.value) cols.push({ key: '__drag', cls: 'op-col-drag', kind: 'drag', resizable: false })
  cols.push(
    { key: 'opNo', cls: 'op-col-no', label: '工序号' },
    { key: 'operationCode', label: '工序编码' },
    { key: 'operationName', label: '工序名称' },
    { key: 'workCenter', label: '工作中心' },
    { key: 'operationType', label: '工序类型' },
    { key: 'pieceRate', cls: 'op-col-edit', label: '计件单价' },
    { key: 'priceWithTax', cls: 'op-col-right', label: '含税价' },
    { key: 'priceWithoutTax', cls: 'op-col-right', label: '不含税价' },
    { key: 'planQuantity', cls: 'op-col-edit', label: '计划数量' },
    { key: 'planEndDate', cls: 'op-col-edit', label: '计划完工' },
    { key: 'assignee', cls: 'op-col-edit', label: '负责人' },
    { key: 'isQcRequired', cls: 'op-col-flag', label: '需质检' },
    { key: 'qcTemplate', cls: 'op-col-edit', label: '质检模板' },
    { key: 'isCritical', cls: 'op-col-flag', label: '关键工序' },
    { key: 'reportedQuantity', cls: 'op-col-right', label: '已汇报' },
    { key: 'rejectedQuantity', cls: 'op-col-right', label: '报废' },
    { key: 'status', cls: 'op-col-status', label: '状态' }
  )
  // 【2026-11-25】生产工序自定义字段列：插在「操作 / 工序卡」之前，列多时表格横向滚动
  opCustomDefs.value.forEach(f => {
    cols.push({ key: 'cf_' + f.fieldKey, cls: 'op-col-edit op-col-custom', label: f.fieldLabel })
  })
  cols.push(
    { key: 'action', cls: 'op-col-op', label: '操作' },
    { key: 'card', cls: 'op-col-op op-col-card', label: '工序卡' }
  )
  return cols
})

/** 工序自定义字段：解析下拉选项（与明细自定义字段同一口径） */
function opCustomOptions(f) {
  try {
    const arr = JSON.parse(f.fieldOptions || '[]')
    return Array.isArray(arr) ? arr : []
  } catch (e) { return [] }
}
/** 【2026-11-26】该行是否属于本字段：有工序标识时只看自己工序的字段；老工单保持原全局口径 */
function rowHasCf(row, f) {
  if (!row) return false
  if (!row.routingOperationKey) return true
  const keys = (row.operationFieldDefs || []).map(x => x.fieldKey)
  return keys.includes(f.fieldKey)
}

/** 工序自定义字段值（只读取值；渲染期不改行数据，写入统一走 setOpCustomVal） */
function opCustomVal(row, f) {
  return row.customFields ? row.customFields[f.fieldKey] : undefined
}
function setOpCustomVal(row, f, v) {
  if (!row.customFields) row.customFields = {}
  row.customFields[f.fieldKey] = v
}
/** 必填校验（只拦「保存工单」，不拦工序动作） */
function validateOpCustomRequired(rows) {
  const required = opCustomDefs.value.filter(f => f.isRequired === 1)
  if (!required.length) return null
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    const cf = row.customFields || {}
    for (const f of required) {
      // 【2026-11-26】字段属于哪道工序就只校验哪道：别把 A 工序的必填字段压到 B 工序上
      if (!rowHasCf(row, f)) continue
      const v = cf[f.fieldKey]
      if (v === undefined || v === null || v === '') {
        return `第${i + 1}道工序：请填写「${f.fieldLabel}」`
      }
    }
  }
  return null
}
/** 打开弹窗时把已保存的列宽套进来 */
function hydrateOpConfigWidths () {
  opConfigCols.value.forEach(c => {
    const w = getOpConfigColWidth(c.key)
    if (w && opConfigWidths[c.key] !== w) opConfigWidths[c.key] = w
  })
}
watch(opDialogVisible, (v) => { if (v) nextTick(hydrateOpConfigWidths) })

/** 拖动表头边界改列宽：mousemove 实时预览，mouseup 落库（复用 useColWidth 的保存口径，不另写存储） */
let opColResize = null
function startOpColResize (e, key) {
  const th = e.currentTarget && e.currentTarget.parentElement
  const startWidth = th ? th.offsetWidth : (opConfigWidths[key] || 120)
  opColResize = { key, startX: e.clientX, startWidth }
  document.addEventListener('mousemove', onOpColResizeMove)
  document.addEventListener('mouseup', stopOpColResize)
}
function onOpColResizeMove (e) {
  if (!opColResize) return
  opConfigWidths[opColResize.key] = Math.max(40, Math.round(opColResize.startWidth + (e.clientX - opColResize.startX)))
}
function stopOpColResize () {
  if (opColResize) {
    const { key, startWidth } = opColResize
    const w = opConfigWidths[key]
    if (w && w > 0) onOpConfigHeaderDragend(w, startWidth, { property: key })
  }
  opColResize = null
  document.removeEventListener('mousemove', onOpColResizeMove)
  document.removeEventListener('mouseup', stopOpColResize)
}
onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onOpColResizeMove)
  document.removeEventListener('mouseup', stopOpColResize)
})

const rules = {
  orderDate: [{ required: true, message: tt('请选择工单日期'), trigger: 'change' }],
  warehouseId: [{ required: true, message: tt('请选择仓库'), trigger: 'change' }]
}

const progressData = reactive({
  operations: [],
  recentReports: [],
  recentTransfers: [],
  totalProgress: 0,
  currentOperation: '',
  currentOperationIndex: -1
})

// 第10期：「进度流程」按成品分组 —— 每个成品一块（成品名 + 数量 + 该成品的工序线路）；
// 没有成品归属的工序归到「未指定成品」一块。
const progressGroups = computed(() => {
  const ops = progressData.operations || []
  if (!ops.length) return []
  const groups = []
  const map = new Map()
  const products = workOrder.products || []
  for (const op of ops) {
    const key = op.orderProductId != null ? `p-${op.orderProductId}` : 'none'
    let g = map.get(key)
    if (!g) {
      const prod = op.orderProductId != null ? products.find(p => p.id === op.orderProductId) : null
      g = {
        key,
        productName: op.orderProductId == null ? '未指定成品' : (op.productName || prod?.materialName || ''),
        quantity: prod && prod.quantity != null ? prod.quantity : null,
        ops: []
      }
      map.set(key, g)
      groups.push(g)
    }
    g.ops.push(op)
  }
  return groups
})

// 成品明细列表（直接返回 workOrder.products）
const productList = computed(() => workOrder.products || [])

// 成品计划数量汇总
const totalPlanQty = computed(() => {
  return (workOrder.products || []).reduce((s, p) => s + (Number(p.quantity) || 0), 0)
})

// 成品完工数量汇总
const totalCompletedQty = computed(() => {
  const sum = (workOrder.products || []).reduce((s, p) => s + (Number(p.completedQuantity) || 0), 0)
  // 若成品行未带完工数（旧数据），回退用工单汇总字段
  return sum || Number(workOrder.completedQuantity || 0)
})

// 成品报废数量汇总
const totalRejectedQty = computed(() => {
  const sum = (workOrder.products || []).reduce((s, p) => s + (Number(p.rejectedQuantity) || 0), 0)
  return sum || Number(workOrder.rejectedQuantity || 0)
})

const totalProgress = computed(() => {
  if (progressData.totalProgress !== undefined && progressData.totalProgress !== null) {
    return Math.round(progressData.totalProgress)
  }
  const plan = Number(totalPlanQty.value) || 0
  if (plan === 0) return 0
  return Math.round((totalCompletedQty.value || 0) / plan * 100)
})

// 报工合格量 = 工序合格量合计（若无工序则用工单 rejectedQuantity 反推或为0）
const reportedQuantity = computed(() => {
  if (Array.isArray(progressData.operations) && progressData.operations.length > 0) {
    return progressData.operations.reduce((s, op) => s + (Number(op.qualifiedQty) || 0), 0)
  }
  // 回退：用 workOrder 上的 reportedQuantity 字段（若存在）
  return Number(workOrder.reportedQuantity || 0)
})

// 报工进度 = 报工合格量 / 计划数量 × 100%
const reportedProgress = computed(() => {
  const plan = Number(totalPlanQty.value) || 0
  if (plan === 0) return 0
  return Math.min(100, Math.round(reportedQuantity.value / plan * 100))
})

// 入库进度 = 入库量 / 计划数量 × 100%
const receiptProgress = computed(() => {
  const plan = Number(totalPlanQty.value) || 0
  if (plan === 0) return 0
  return Math.min(100, Math.round((Number(totalCompletedQty.value) || 0) / plan * 100))
})

// 入库剩余量
const receiptRemaining = computed(() => {
  const plan = Number(totalPlanQty.value) || 0
  const done = Number(totalCompletedQty.value) || 0
  return Math.max(0, plan - done).toFixed(4).replace(/\.?0+$/, '')
})

// 报工进度颜色：<50% 灰, 50-80% 蓝, 80-99% 绿, 100% 深绿
const reportedProgressColor = computed(() => {
  const p = reportedProgress.value
  if (p >= 100) return '#15803d'
  if (p >= 80) return '#67c23a'
  if (p >= 50) return '#409eff'
  return '#909399'
})

// 入库进度颜色
const receiptProgressColor = computed(() => {
  const p = receiptProgress.value
  if (p >= 100) return '#15803d'
  if (p >= 80) return '#67c23a'
  if (p >= 50) return '#409eff'
  return '#909399'
})

// 来源单号是否可跳转（销售订单 / MRP计划）
const isSourceLinkable = computed(() => {
  const t = workOrder.sourceType || ''
  return t.includes('销售') || t.includes('MRP')
})

// 跳转到来源单据：销售订单→/sale/orders/:id，MRP计划→/production/mrp-plans/edit/:id
function goToSourceDoc() {
  const t = workOrder.sourceType || ''
  const id = workOrder.sourceId
  if (t.includes('销售')) {
    if (id) router.push(`/sale/orders/${id}`)
    else router.push({ path: '/sale/orders', query: { keyword: workOrder.sourceNo || '' } })
  } else if (t.includes('MRP')) {
    if (id) router.push(`/production/mrp-plans/edit/${id}`)
    else router.push({ path: '/production/mrp-plan', query: { keyword: workOrder.sourceNo || '' } })
  }
}

// 跳转到销售订单详情
function goToSaleOrder() {
  const id = workOrder.salesOrderId
  if (id) {
    router.push(`/sale/orders/${id}`)
  } else {
    router.push({ path: '/sale/orders', query: { keyword: workOrder.salesOrderNo || '' } })
  }
}

// 跳转到报工单详情（汇报记录）
function goToReport(row) {
  const id = row.reportId || row.id
  if (id) {
    router.push(`/production/operation-reports/edit/${id}`)
  } else {
    router.push({ path: '/production/operation-report', query: { keyword: row.reportNo || '' } })
  }
}

// 原料需求总数量
const totalQty = computed(() => {
  return (workOrder.materials || []).reduce((s, r) => s + (Number(r.requiredQuantity || r.quantity) || 0), 0).toFixed(2)
})

// 状态标签类型：统一复用 @/utils/bill-status 的 statusTagType（本页不再维护重复的状态映射表；
// status 只有 草稿/待审核/已生效/已作废，「已结案」由 close_status 判定）
function statusType(s, closeStatus) {
  return BillStatus.statusTagType(s, closeStatus)
}

// 状态展示文案：已生效 + close_status=1 → 显示「已结案」
function statusText() {
  return BillStatus.isClosed(workOrder.closeStatus)
    ? BillStatus.CLOSE_STATUS.CLOSED_LABEL
    : (workOrder.status || '草稿')
}

function stepClass(op) {
  // 工序状态改 8 值口径（2026-09-23 第 2 期）：待派工/已派工/已开工/进行中/暂停/完工待检/已完工/已取消
  return {
    'step-done': op.status === '已完工',
    'step-active': ['已开工', '进行中', '完工待检'].includes(op.status),
    'step-pending': ['待派工', '已派工', '暂停'].includes(op.status)
  }
}

function opTagType(status) {
  return {
    '已完工': 'success',
    '进行中': 'success',
    '已开工': 'primary',
    '完工待检': 'warning',
    '暂停': 'warning',
    '已派工': 'primary',
    '已取消': 'danger',
    '待派工': 'info'
  }[status] || 'info'
}

function opStatusType(status) {
  return opTagType(status)
}

/** 当前行物料（多单位换算用：取基本单位、辅计量单位与换算率） */
function materialOf(row) {
  if (!row || row.materialId == null) return null
  return materialOptions.value.find(x => x.materialId === row.materialId) || null
}

/** 切换单位后同步单位名称，保证落库的单位与所选单位一致 */
function onProductUnitChange(row) {
  const u = units.value.find(x => x.unitId === row.unitId)
  row.unitName = u ? u.unitName : ''
}

// 成品行物料变更：选择物料后自动带出编码/名称/规格/单位等
function onProductMaterialChange(row) {
  const m = materialOptions.value.find(x => x.materialId === row.materialId)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.specification = m.model || ''
    row.model = m.model || ''
    row.unitId = m.baseUnitId
    row.unitName = m.baseUnitName || ''
    row.materialMainImage = m.mainImage || ''
  }
  // 【2026-11-26】工艺路线按该行物料过滤：只列绑定了这个物料的路线
  loadApplicableRoutings(row)
  // BOM 必须绑定当前行物料：换物料后原 BOM 若不属于新物料则清空，避免带着别的商品的 BOM 保存
  if (row.bomId != null) {
    const stillOwn = row.materialId != null && (bomOptions.value || []).some(
      b => b && String(b.bomId) === String(row.bomId) && String(b.materialId) === String(row.materialId))
    if (!stillOwn) {
      row.bomId = null
      row.bomCode = ''
      row.bomVersion = ''
    }
  }
  // 跟着物料自动带出默认BOM，并自动展开下面的原料明细
  applyDefaultBomForProduct(row)
}

/** 按成品物料自动带出默认BOM：默认BOM优先 → 已生效 → 最新；随后按BOM带出原料明细并展开 */
async function applyDefaultBomForProduct(row) {
  if (!isDraft.value || !row || row.materialId == null) return
  const boms = (bomOptions.value || []).filter(b => b && String(b.materialId) === String(row.materialId))
  if (!boms.length) return
  const pick = boms.find(b => b.isDefault === 1 && b.status === '已生效')
    || boms.find(b => b.isDefault === 1)
    || boms.find(b => b.status === '已生效')
    || boms[0]
  const productId = row.materialId
  workOrder.bomId = pick.bomId
  workOrder.bomVersion = pick.bomVersion || ''
  // 行级 BOM（2026-11-06）：默认 BOM 同时写到该成品行，保证一行一份、可逐行改
  row.bomId = pick.bomId
  row.bomCode = pick.bomCode || ''
  row.bomVersion = pick.bomVersion || ''
  const token = bomAutoFill.begin()
  try {
    const detail = await getBom(pick.bomId)
    // 并发防护（2026-10-26）：等BOM期间又改选了成品 / 成品已被清空 → 丢弃本次结果，
    // 否则两套BOM的原料会先后追加，领料出库时会重复扣料。
    if (!bomAutoFill.isLatest(token)) return
    if (row.materialId == null || String(row.materialId) !== String(productId)) return
    // 每行成品各带各的 BOM 用料：只判断「本成品行」是否已有原料，避免多成品互相覆盖
    const owns = (workOrder.materials || []).filter(m => m._productPid === row._pid)
    if (!owns.length) {
      const items = (detail && detail.items) || []
      if (items.length) {
        if (!workOrder.materials) workOrder.materials = []
        items.filter(it => it.materialId != null).forEach(it => {
          const newRow = newMaterialRow()
          applyBomItemToRow(newRow, it)
          newRow._productPid = row._pid
          workOrder.materials.push(newRow)
        })
      }
    }
    // 带完自动切到该成品的用料视图
    activeProductPid.value = row._pid || ''
    materialsOpen.value = true
    productsOpen.value = true
    collapsed.items = true
  } catch (e) { /* 带出失败不影响已选物料 */ }
}

// 原料行物料变更：选择物料后自动带出编码/名称/规格
function onMaterialRowChange(row) {
  const m = materialOptions.value.find(x => x.materialId === row.materialId)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    row.specification = m.model || ''
    row.materialMainImage = m.mainImage || ''
    // 换料后单位回到新物料的基本单位（旧物料的辅单位不再适用），可再按需改辅单位
    if (m.baseUnitId) {
      row.unitId = m.baseUnitId
      row.unitName = m.baseUnitName || ''
    } else {
      row.unitId = null
      row.unitName = ''
    }
  }
}

// 从物料库批量添加成品行（2026-11-07：与销售订单同款物料库弹窗）
const productPickerVisible = ref(false)
function onProductsPicked(payload) {
  const rows = (payload && payload.rows) || []
  if (!rows.length) return
  if (payload.valueMap) {
    Object.assign(mcf.valueMap.value, payload.valueMap)
    Object.keys(payload.valueMap).forEach(id => mcf.loadedIds.value.add(Number(id)))
  }
  rows.forEach(m => {
    if (m.materialId == null) return
    const row = ensureProductPid({
      lineNo: ((workOrder.products || []).length + 1) * 10,
      materialId: m.materialId,
      materialCode: m.materialCode || '',
      materialName: m.materialName || '',
      specification: m.model || '',
      model: m.model || '',
      unitId: m.baseUnitId || null,
      unitName: m.baseUnitName || '',
      materialMainImage: m.mainImage || '',
      quantity: 1,
      completedQuantity: 0,
      reportedQuantity: 0,
      rejectedQuantity: 0,
      routingId: null,
      routingVersion: '',
      sourceType: workOrder.sourceType || '',
      sourceNo: workOrder.sourceNo || '',
      sourceItemId: null,
      remark: ''
    })
    if (!workOrder.products) workOrder.products = []
    workOrder.products.push(row)
    applyDefaultBomForProduct(row)
  })
  productPickerVisible.value = false
}

// 添加成品行
function addProductRow() {
  if (!workOrder.products) workOrder.products = []
  const lineNo = (workOrder.products.length + 1) * 10
  workOrder.products.push(ensureProductPid({
    lineNo,
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    model: '',
    unitId: null,
    unitName: '',
    quantity: 1,
    completedQuantity: 0,
    reportedQuantity: 0,
    rejectedQuantity: 0,
    routingId: null,        // 第10期：该成品行自己的工艺路线
    routingVersion: '',
    sourceType: '',
    sourceNo: '',
    sourceItemId: null,
    remark: ''
  }))
}

// 删除成品行（同时删掉它的工序，避免留下"未指定成品"的孤儿工序）
function removeProductRow(index) {
  const row = (workOrder.products || [])[index]
  if (row && row._pid) {
    operations.value = operations.value.filter(r => r._pid !== row._pid)
  }
  workOrder.products.splice(index, 1)
}

// 添加原料行
function addMaterialRow() {
  if (!workOrder.materials) workOrder.materials = []
  const lineNo = (workOrder.materials.length + 1) * 10
  const row = {
    lineNo,
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    requiredQuantity: 1,
    issuedQuantity: 0,
    returnedQuantity: 0,
    unitCost: 0,
    priceWithTax: 0,
    priceWithoutTax: 0,
    totalCost: 0,
    supplyType: '领料',
    remark: ''
  }
  calcMaterialRow(row)
  workOrder.materials.push(row)
}

// 删除原料行
function removeMaterialRow(index) {
  workOrder.materials.splice(index, 1)
}

/** 行级 BOM 下拉选项：只列「当前行物料自己的」BOM（BOM 必须绑定该行商品，不再退回全部可选） */
function bomOptionsForRow(row) {
  if (!row || row.materialId == null) return []
  return (bomOptions.value || []).filter(b => b && String(b.materialId) === String(row.materialId))
}
/** BOM 下拉提示：未选物料 / 该物料没维护 BOM / 正常选择 三种态 */
function bomPlaceholderOf(row) {
  if (!row || row.materialId == null) return tt('请先选择物料')
  return bomOptionsForRow(row).length ? tt('选择该行的BOM清单') : tt('该物料未维护BOM')
}
function bomNoDataTextOf(row) {
  if (!row || row.materialId == null) return tt('请先选择物料')
  return tt('该物料未维护BOM')
}

/** 行级 BOM 变更：回写该行清单编码与版本，并按新 BOM 重带该成品行的原料 */
async function onRowBomChange(row) {
  const b = (bomOptions.value || []).find(x => String(x.bomId) === String(row.bomId))
  row.bomCode = b ? (b.bomCode || '') : ''
  row.bomVersion = b ? (b.bomVersion || '') : ''
  if (!row.bomId || !isDraft.value) return
  const token = bomAutoFill.begin()
  try {
    const detail = await getBom(row.bomId)
    if (!bomAutoFill.isLatest(token)) return
    // 只替换该成品行的原料，其他成品行的用料不动
    workOrder.materials = (workOrder.materials || []).filter(m => m._productPid !== row._pid)
    ;((detail && detail.items) || []).filter(it => it.materialId != null).forEach(it => {
      const newRow = newMaterialRow()
      applyBomItemToRow(newRow, it)
      newRow._productPid = row._pid
      workOrder.materials.push(newRow)
    })
    activeProductPid.value = row._pid || ''
    materialsOpen.value = true
  } catch (e) { /* 带出失败不影响已选 BOM 与版本号回显 */ }
}

// BOM变更
async function onBomChange(bomId) {
  const b = bomOptions.value.find(x => x.bomId === bomId)
  workOrder.bomVersion = b ? (b.bomVersion || '') : ''
  if (bomId == null || bomId === '') return
  // 第4期联动：选 BOM → 自动带出子件用料到原料区。
  // 规则（用户 2026-09-27 口径）：**只填空行、绝不覆盖已填行**；已有该物料的行不重复带出；空行不够才追加。
  try {
    const detail = await getBom(bomId)
    // 并发防护（2026-10-26）：等BOM期间又改了BOM/成品（自动带出与手动选择都会走到这里）→
    // 丢弃本次结果，避免两套BOM的原料混在一起（重复或多余用料会一路带到领料/成本）。
    if (String(workOrder.bomId) !== String(bomId)) return
    const items = (detail && detail.items) || []
    if (!items.length) return
    if (!workOrder.materials) workOrder.materials = []
    const existingIds = new Set(workOrder.materials.filter(m => m.materialId != null).map(m => m.materialId))
    const pending = items.filter(it => it.materialId != null && !existingIds.has(it.materialId))
    if (!pending.length) return
    let idx = 0
    for (const row of workOrder.materials) {
      if (idx >= pending.length) break
      if (row.materialId == null) { applyBomItemToRow(row, pending[idx]); idx++ }
    }
    while (idx < pending.length) {
      const row = newMaterialRow()
      applyBomItemToRow(row, pending[idx])
      workOrder.materials.push(row)
      idx++
    }
  } catch (e) { /* 带出失败不影响已选 BOM 与版本号回显 */ }
}

/** 造一个空原料行（结构同「添加原料」，此处不直接入列，由调用方决定填空行还是追加） */
function newMaterialRow() {
  return {
    lineNo: ((workOrder.materials || []).length + 1) * 10,
    materialId: null,
    materialCode: '',
    materialName: '',
    specification: '',
    requiredQuantity: 1,
    issuedQuantity: 0,
    returnedQuantity: 0,
    unitCost: 0,
    priceWithTax: 0,
    priceWithoutTax: 0,
    totalCost: 0,
    supplyType: '领料',
    remark: ''
  }
}

/** 把 BOM 子件写进原料行（需求数量 = BOM 子件用量） */
function applyBomItemToRow(row, item) {
  row.materialId = item.materialId
  row.materialCode = item.materialCode || ''
  row.materialName = item.materialName || ''
  row.specification = item.specification || ''
  row.requiredQuantity = item.quantity != null ? Number(item.quantity) : 1
  if (item.unitPrice != null) row.unitCost = Number(item.unitPrice) || 0
  if (!row.supplyType) row.supplyType = '领料'
  calcMaterialRow(row)
}

// 销售订单行的「剩余可下推生产数量」= 订单数量 − 已下推生产数量（与后端守卫同口径，2026-10-19）
function saleLineRemainingQty(it) {
  const q = Number(it?.quantity) || 0
  const t = Number(it?.turnedProduceQuantity) || 0
  return Math.round((q - t) * 100) / 100
}

/**
 * 把销售订单明细行映射为工单成品明细行（下推进入 / 选单两条路共用，2026-10-19）。
 * 数量取「剩余可下推生产数量」；已推完（剩余 ≤ 0）的行自动跳过，防止重复下推。
 * 返回 { rows, skipped }：rows=成品行数组，skipped=因已推完被跳过的行数。
 */
function buildProductsFromSaleItems(items) {
  const rows = []
  let skipped = 0
  for (const it of (items || [])) {
    const remaining = saleLineRemainingQty(it)
    if (!(remaining > 0)) { skipped++; continue }
    const mat = materialOptions.value.find(m => m.materialId === it.materialId)
    rows.push(ensureProductPid({
      lineNo: (rows.length + 1) * 10,
      materialId: it.materialId,
      materialCode: it.materialCode || '',
      materialName: it.materialName || '',
      specification: mat?.model || '',
      model: mat?.model || '',
      unitId: it.unitId || mat?.baseUnitId || null,
      unitName: mat?.baseUnitName || '',
      materialMainImage: mat?.mainImage || '',
      quantity: remaining,
      completedQuantity: 0,
      reportedQuantity: 0,
      rejectedQuantity: 0,
      routingId: null,        // 第10期：该成品行自己的工艺路线（选单后由用户逐行选择）
      routingVersion: '',
      sourceType: workOrder.sourceType || '销售订单',
      sourceNo: workOrder.sourceNo || '',
      sourceItemId: it.orderItemId || it.id || null
    }))
  }
  return { rows, skipped }
}

// 选单回调: 从销售订单/MRP计划下推填充工单表单
function onSourcePicked(sourceData, items) {
  if (sourceData) {
    if (sourceData.sourceType) workOrder.sourceType = sourceData.sourceType
    if (sourceData.sourceNo) workOrder.sourceNo = sourceData.sourceNo
    if (sourceData.salesOrderNo) workOrder.salesOrderNo = sourceData.salesOrderNo
    if (sourceData.remark) workOrder.remark = sourceData.remark
  }
  if (items && items.length > 0) {
    // 2026-11-23：选单带入追加到现有成品明细末尾，不再整单替换；已有成品行的工序保持不动
    const existingProducts = workOrder.products || []
    const maxLineNo = existingProducts.reduce((max, r) => Math.max(max, Number(r.lineNo) || 0), 0)
    const isSaleOrderSource = (sourceData?.sourceType || workOrder.sourceType) === '销售订单'
    if (isSaleOrderSource) {
      // 销售订单来源：数量取剩余可下推量、已推完的行跳过（2026-10-19 防重复下推）
      const { rows, skipped } = buildProductsFromSaleItems(items)
      if (rows.length === 0) {
        ElMessage.warning(tt('所选明细行已全部下推生产，没有剩余可下推数量'))
        return
      }
      rows.forEach((r, idx) => { r.lineNo = maxLineNo + (idx + 1) * 10 })
      workOrder.products = [...existingProducts, ...rows]
      if (skipped > 0) ElMessage.info(tt(`${skipped} 行已全部下推生产，已自动跳过`))
    } else {
      const newRows = items.map((it, idx) => {
        const mat = materialOptions.value.find(m => m.materialId === it.materialId)
        return ensureProductPid({
          lineNo: maxLineNo + (idx + 1) * 10,
          materialId: it.materialId,
          materialCode: it.materialCode || '',
          materialName: it.materialName || '',
          specification: mat?.model || '',
          model: mat?.model || '',
          unitId: it.unitId || mat?.baseUnitId || null,
          unitName: mat?.baseUnitName || '',
          materialMainImage: mat?.mainImage || '',
          quantity: it.quantity || 1,
          completedQuantity: 0,
          reportedQuantity: 0,
          rejectedQuantity: 0,
          routingId: null,        // 第10期：该成品行自己的工艺路线（选单后由用户逐行选择）
          routingVersion: '',
          sourceType: sourceData?.sourceType || '',
          sourceNo: sourceData?.sourceNo || '',
          sourceItemId: it.orderItemId || it.id || null
        })
      })
      workOrder.products = [...existingProducts, ...newRows]
    }
  }
  ElMessage.success(tt('选单成功，请确认信息后保存'))
}

// ---------------- 第10期：成品行的「工艺路线」+ 成品工序弹窗 ----------------
/** 成品行选择工艺路线：写入该成品行，并自动按该路线为该成品带出全部工序（已有工序时先确认覆盖） */
function onProductRoutingChange(row, routingId) {
  const r = routingOptions.value.find(x => x.routingId === routingId)
  row.routingVersion = r ? (r.routingVersion || '') : ''
  if (routingId == null || routingId === '') return
  if (!opsConfigurable.value) return
  const existing = opsOfProduct(row)
  if (existing.length === 0) {
    bringInRoutingOperationsForProduct(row, true)
  } else {
    ElMessageBox.confirm(tt('该成品已有工序，是否用新工艺路线的工序覆盖该成品的现有工序？'), tt('确认覆盖'), { type: 'warning' })
      .then(() => bringInRoutingOperationsForProduct(row, true))
      .catch(() => {})
  }
}

/** 打开某成品行的工序编辑弹窗 */
function openOpDialog(row) {
  if (!row) return
  ensureProductPid(row)
  dialogProduct.value = row
  dialogAddOperationId.value = null
  opDialogVisible.value = true
  // 【2026-11-25】打开即拉取该成品下各工序已填的自定义字段值
  loadOpCustomValues()
}

// ---------------- 工序清单（可配置，2026-09-27） ----------------
/** 规整一条工单工序行：补默认值、统一 0/1、标记选用与唯一键 */
function normalizeOpRow(row, idx) {
  const out = {
    ...row,
    lineNo: row.lineNo != null ? row.lineNo : (idx + 1) * 10,
    pieceRate: row.pieceRate ?? 0,
    planQuantity: row.planQuantity != null ? row.planQuantity : null,
    planEndDate: row.planEndDate || '',
    assigneeName: row.assigneeName || '',
    isQcRequired: (row.isQcRequired === 1 || row.isQcRequired === true) ? 1 : 0,
    qcTemplateId: row.qcTemplateId ?? null,
    isCritical: (row.isCritical === 1 || row.isCritical === true) ? 1 : 0,
    reportedQuantity: row.reportedQuantity ?? 0,
    rejectedQuantity: row.rejectedQuantity ?? 0,
    _selected: true,
    customFields: row.customFields || {},
    _pid: null,
    _k: row.id != null ? `db-${row.id}` : `db-${row.orderProductId ?? 0}-${row.lineNo ?? idx}-${idx}`
  }
  // 计税口径：按表头「计税方式」由「计件单价」换算两列（老数据未带时补算）
  calcOpRow(out)
  return out
}

/** 造一条新工序行（带入/添加共用；planQuantity 默认 = 该成品行的数量；planEndDate 空） */
function newOperationRow(src, idx, product) {
  const qty = product && product.quantity != null ? Number(product.quantity) : (Number(workOrder.quantity) || 1)
  const out = {
    id: null,
    orderProductId: (product && product.id != null) ? product.id : (src.orderProductId ?? null),
    lineNo: (idx + 1) * 10,
    operationId: src.operationId ?? null,
    // 【2026-11-26】工序稳定标识 + 该工序自己的字段定义（从工艺路线带入时随行携带）
    routingOperationKey: src.routingOperationKey ?? src.operationKey ?? null,
    operationFieldDefs: src.operationFieldDefs || [],
    operationCode: src.operationCode || '',
    operationName: src.operationName || '',
    workCenterId: src.workCenterId ?? null,
    workstationId: src.workstationId ?? null,
    operationType: src.operationType || '',
    planQuantity: src.planQuantity != null ? Number(src.planQuantity) : qty,
    planEndDate: src.planEndDate || '',
    assigneeId: src.assigneeId ?? null,
    assigneeName: src.assigneeName || '',
    isQcRequired: (src.isQcRequired === 1 || src.isQcRequired === true) ? 1 : 0,
    qcTemplateId: src.qcTemplateId ?? null,
    isCritical: (src.isCritical === 1 || src.isCritical === true) ? 1 : 0,
    pieceRate: src.pieceRate != null ? Number(src.pieceRate) : 0,
    priceWithTax: 0,
    priceWithoutTax: 0,
    setupHours: src.setupHours ?? 0,
    processHours: src.processHours ?? 0,
    operationStatus: '',
    reportedQuantity: 0,
    rejectedQuantity: 0,
    _selected: true,
    customFields: {},
    _pid: product ? product._pid : (src._pid ?? null),
    _k: `new-${Date.now()}-${idx}-${Math.random().toString(36).slice(2, 7)}`
  }
  // 计税口径：按表头「计税方式」由「计件单价」换算两列
  calcOpRow(out)
  return out
}

/** 成品行计划数量变更：同步该成品下尚未报工的工序计划数量 + 进度界面（2026-10-26） */
function onProductQuantityChange(row) {
  if (!row) return
  const qty = Number(row.quantity) || 0
  if (qty <= 0) return
  opsOfProduct(row).forEach(op => {
    if ((Number(op.reportedQuantity) || 0) > 0 || (Number(op.rejectedQuantity) || 0) > 0) return
    op.planQuantity = qty
  })
  ;(progressData.operations || []).forEach(op => {
    if (op.orderProductId == null || row.id == null || Number(op.orderProductId) !== Number(row.id)) return
    if ((Number(op.reportedQty) || 0) > 0 || (Number(op.rejectedQty) || 0) > 0) return
    op.planQty = qty
  })
}

/** 打开已有工单时纠正历史「工序计划数量=1」：未报工的工序按成品行计划数量对齐 */
function alignPlanQuantitiesWithProducts() {
  const qtyByPid = {}
  const qtyByProductId = {}
  ;(workOrder.products || []).forEach(p => {
    const qty = Number(p.quantity)
    if (!(qty > 0)) return
    if (p._pid) qtyByPid[p._pid] = qty
    if (p.id != null) qtyByProductId[p.id] = qty
  })
  operations.value.forEach(op => {
    if ((Number(op.reportedQuantity) || 0) > 0 || (Number(op.rejectedQuantity) || 0) > 0) return
    const qty = qtyByPid[op._pid]
    if (qty != null && Number(op.planQuantity) !== qty) op.planQuantity = qty
  })
  ;(progressData.operations || []).forEach(op => {
    if ((Number(op.reportedQty) || 0) > 0 || (Number(op.rejectedQty) || 0) > 0) return
    const qty = qtyByProductId[op.orderProductId]
    if (qty != null && Number(op.planQty) !== qty) op.planQty = qty
  })
}

/** 从服务端明细重建「工序清单」（按 orderProductId → 成品行 _pid 建立归属） */
function syncOpsFromServer() {
  ensureProductsPid()
  const pidByProductId = {}
  ;(workOrder.products || []).forEach(p => { if (p.id != null) pidByProductId[p.id] = p._pid })
  const list = Array.isArray(workOrder.operations) ? workOrder.operations : []
  operations.value = list.map((r, i) => {
    const row = normalizeOpRow(r, i)
    row._pid = r.orderProductId != null ? (pidByProductId[r.orderProductId] ?? null) : null
    return row
  })
  savedOpCount.value = list.length
}

/** 从服务端原料明细重建「归属成品行」（原料行 orderProductId 是成品行主键 → 换成前端 _pid） */
function syncMaterialProductPid() {
  ensureProductsPid()
  const pidByProductId = {}
  ;(workOrder.products || []).forEach(p => { if (p.id != null) pidByProductId[p.id] = p._pid })
  ;(workOrder.materials || []).forEach(r => {
    r._productPid = r.orderProductId != null ? (pidByProductId[r.orderProductId] ?? '') : ''
  })
}

/**
 * 只保存工序清单：
 * 已生效工单没有常规「保存」按钮（常规保存要求草稿态），但口径要求"生效时一道工序都没配 → 允许补选"，
 * 所以必须给一个独立入口；草稿期也可用它只落工序、不动整单其它字段。
 */
async function saveOperations() {
  const id = workOrder.orderId || orderId
  if (!id) { ElMessage.warning(tt('请先保存工单，再保存工序')); return false }
  if (!opsConfigurable.value) { ElMessage.warning(tt('当前单据状态不允许修改工序清单')); return false }
  const rows = operations.value
    .filter(r => r._selected !== false)
    .map(r => { const { _selected, _k, _pid, ...rest } = r; return rest })
  if (!rows.length) {
    ElMessage.warning(tt('请先配置工序（可「按工艺路线带入全部工序」或从「工序/工位」主档挑选）'))
    return false
  }
  opsSaving.value = true
  try {
    await saveWorkOrderOperations(id, rows)
    ElMessage.success(tt('工序已保存'))
    await loadProgress()
    return true
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
    return false
  } finally {
    opsSaving.value = false
  }
}

/** 按某成品行的工艺路线带入全部工序；skipConfirm=true 表示调用方已确认 */
async function bringInRoutingOperationsForProduct(product, skipConfirm = false) {
  if (!opsConfigurable.value) { ElMessage.warning(tt('当前单据状态不允许修改工序清单')); return }
  if (!product) return
  ensureProductPid(product)
  const routingId = product.routingId
  if (!routingId) { ElMessage.warning(tt('请先为该成品选择工艺路线')); return }
  if (!skipConfirm && opsOfProduct(product).length > 0) {
    try {
      await ElMessageBox.confirm(tt('该成品已有工序，是否用新工艺路线的工序覆盖该成品的现有工序？'), tt('确认覆盖'), { type: 'warning' })
    } catch (e) { return }
  }
  try {
    const detail = await getRouting(routingId)
    const list = (detail && detail.operations) || []
    if (!list.length) { ElMessage.warning(tt('该工艺路线下没有工序')); return }
    const rows = list.map((op, idx) => newOperationRow({
      operationId: op.operationId,
      operationCode: op.operationCode,
      operationName: op.operationName,
      workCenterId: op.workCenterId,
      workstationId: op.workstationId,
      operationType: op.operationType,
      pieceRate: op.pieceRate,
      setupHours: op.setupHours,
      processHours: op.processHours,
      isQcRequired: op.isQcRequired,
      isCritical: op.isCritical,
      // 【2026-11-26】工序稳定标识 + 自己的字段定义随路线带入（保存工序后按此显示/填写）
      operationKey: op.operationKey,
      operationFieldDefs: (op.fieldDefs || []).map(f => ({
        fieldKey: f.customFieldKey,
        fieldLabel: f.fieldLabel,
        fieldType: f.fieldType,
        fieldOptions: f.fieldOptions,
        isRequired: f.isRequired,
        sortOrder: f.sortNo,
        precisionVal: 2
      }))
    }, idx, product))
    replaceProductOps(product._pid, rows)
    ElMessage.success(`${tt('已带入工序：')}${rows.length}`)
  } catch (e) {
    ElMessage.error(e?.message || tt('工艺路线工序加载失败'))
  }
}

/** 弹窗内「按工艺路线带入全部工序」 */
function bringInForDialog() {
  if (!dialogProduct.value) return
  bringInRoutingOperationsForProduct(dialogProduct.value, false)
}

/** 弹窗内「添加工序」：从工序/工位主档选择 → 自动带出编码/名称/工作中心/类型 */
function onDialogAddOperationChange(workstationId) {
  if (workstationId == null || workstationId === '') return
  if (!opsConfigurable.value || !dialogProduct.value) { ElMessage.warning(tt('当前单据状态不允许修改工序清单')); dialogAddOperationId.value = null; return }
  const ws = workstationOptions.value.find(w => w.workstationId === workstationId)
  if (!ws) { dialogAddOperationId.value = null; return }
  const prod = dialogProduct.value
  const row = newOperationRow({
    operationCode: ws.workstationCode,
    operationName: ws.workstationName,
    workstationId: ws.workstationId,
    workCenterId: ws.workCenterId,
    operationType: ws.workstationType
  }, dialogOps.value.length, prod)
  replaceProductOps(prod._pid, [...dialogOps.value, row])
  dialogAddOperationId.value = null
}

/** 负责人下拉变更 → 回填姓名（提交时后端也读 assigneeName） */
function onAssigneeChange(row, v) {
  const emp = employeeOptions.value.find(e => e.employeeId === v)
  row.assigneeName = emp ? emp.employeeName : ''
}

/** 删除弹窗里某成品的工序行（第11期：已生效工单的「已取消」行可直接删除并立即落库） */
async function removeDialogOperation(index) {
  if (!dialogProduct.value) return
  const row = dialogOps.value[index]
  if (!row) return
  // 草稿 / 补选场景：只从本地清单移除，随顶部「保存」或弹窗「保存工序」一起提交
  if (opsConfigurable.value) {
    replaceProductOps(dialogProduct.value._pid, dialogOps.value.filter((_, i) => i !== index))
    return
  }
  // 已生效且已有工序（锁定）：只允许删除「已取消」的工序，删完立即落库
  if (row.operationStatus !== '已取消' || row.id == null) {
    ElMessage.warning(tt('已生效工单只允许删除「已取消」的工序'))
    return
  }
  try {
    await ElMessageBox.confirm(tt('确认删除该已取消的工序？删除后需重新添加。'), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteWorkOrderOperation(row.id)
    ElMessage.success(tt('已删除'))
    const pid = dialogProduct.value.id
    await loadProgress()
    // 刷新后成品行对象会重建，重新定位当前弹窗对应的成品行，避免弹窗内容变空
    if (pid != null) {
      const np = (workOrder.products || []).find(p => p.id === pid)
      if (np) dialogProduct.value = np
    }
  } catch (e) {
    if (e?.message && !/^Request/.test(e.message)) ElMessage.error(e.message)
  }
}

/** 拖动结束：按"成品分组"各自重算工序号（组内 10/20/30…） */
function onOpsDragEnd() {
  const counters = {}
  operations.value.forEach(r => {
    const k = r._pid ?? 'none'
    counters[k] = (counters[k] || 0) + 1
    r.lineNo = counters[k] * 10
  })
}

/** 弹窗底部「保存工序」：第11期仅在补选场景（已生效且原本无工序）显示，走工序专用接口 */
async function saveFromDialog() {
  if (!opsConfigurable.value) { opDialogVisible.value = false; return }
  const ok = isDraft.value ? await onSave() : await saveOperations()
  if (ok !== false) opDialogVisible.value = false
}

/** 工作中心名称回显（后端 operations 只给 workCenterId，用主档映射） */
function operationWorkCenterName(row) {
  if (row.workCenterName) return row.workCenterName
  const wc = workCenterOptions.value.find(w => w.workCenterId === row.workCenterId)
  return wc ? (wc.workCenterName || '') : ''
}

/** 质检模板名称回显（第 12 期：只读态显示已绑定的模板名；未绑定返回空） */
function qcTemplateName (templateId) {
  if (templateId == null) return ''
  const hit = qcTemplateOptions.value.find(t => t.templateId === templateId)
  return hit ? (hit.templateName || '') : ''
}

// 加载下拉选项
async function loadOptions() {
  try {
    const [mats, whs, unitList] = await Promise.all([
      listAllMaterial(),
      listAllWarehouse(),
      listAllUnit()
    ])
    materialOptions.value = mats || []
    units.value = unitList || []
    // 行内下拉附加字段/搜索：物料到位后按批预取（不阻塞渲染）
    mcf.preloadAll(materialOptions.value)
    warehouseOptions.value = whs || []
    const [boms, routings, stations, centers, emps, qcTpls] = await Promise.all([
      listAllBom(),
      listAllRouting(),
      listAllWorkstation(),
      listAllWorkCenter(),
      listAllEmployee(),
      listAllQcTemplates()
    ])
    bomOptions.value = boms || []
    routingOptions.value = routings || []
    // 【2026-11-26】已加载的成品行按各自物料预取适用路线（下拉打开即已过滤）
    ;(productList.value || []).forEach(p => { if (p.materialId != null) loadApplicableRoutings(p) })
    workstationOptions.value = stations || []
    workCenterOptions.value = centers || []
    employeeOptions.value = emps || []
    qcTemplateOptions.value = qcTpls || []
  } catch (e) { /* 忽略选项加载失败 */ }
}

// ---------------- 工序操作面板（2026-09-23 第 2 期） ----------------
const opPanelVisible = ref(false)
const currentOperation = ref(null)

// ===== 【第 19 期】子工序弹窗：父工序（有直接子工序）点击后打开，展示子工序圆环链（可多层下钻） =====
const childDialogVisible = ref(false)
const childDialogParent = ref(null)
const childDialogOps = ref([])
/** 直接子工序数（后端下发 childCount） */
function childCountOf (op) { return Math.max(0, Number(op && op.childCount) || 0) }
/** 主链工序 = 顶层工序（无父，或父不在本成品组内 → 视为顶层） */
function mainOps (g) {
  const ops = (g && g.ops) || []
  const ids = new Set(ops.map(o => o && o.operationRowId).filter(v => v != null).map(Number))
  return ops.filter(o => {
    const pid = o && o.parentRowId
    if (pid == null) return true
    return !ids.has(Number(pid))
  })
}
/** 进度行 → 子工序弹窗识别口径（字段名对齐：planQty/reportedQty/status → 弹窗所需名称） */
function toDialogOp (op) {
  return {
    operationRowId: op.operationRowId,
    parentRowId: op.parentRowId,
    operationName: op.operationName,
    operationStatus: op.status,
    operationType: op.operationType,
    planQuantity: op.planQty,
    reportedQuantity: op.reportedQty,
    childCount: op.childCount,
    isCritical: op.isCritical
  }
}
/** 点工序卡片：父工序 → 弹子工序弹窗；其余 → 工序操作面板 */
function onProgressNodeClick (op) {
  if (childCountOf(op) > 0) {
    const g = progressGroups.value.find(x => (x.ops || []).some(o => o.operationRowId != null && Number(o.operationRowId) === Number(op.operationRowId)))
    childDialogOps.value = ((g && g.ops) || []).map(toDialogOp)
    childDialogParent.value = toDialogOp(op)
    childDialogVisible.value = true
    return
  }
  openOpPanel(op)
}
/** 弹窗里点叶子子工序：按工单工序行ID 找回原始进度行 → 打开工序操作面板 */
function onChildLeafOpen (dlgOp) {
  const row = (progressData.operations || []).find(o => o.operationRowId != null && Number(o.operationRowId) === Number(dlgOp && dlgOp.operationRowId))
  if (row) openOpPanel(row)
}
/** 工单工序实例（含行ID，操作面板用它调后端动作；进度数据里只有工艺路线工序ID） */
const woOperations = ref([])

async function loadWoOperations() {
  if (!orderId) return
  try {
    woOperations.value = (await listWorkOrderOperation(orderId)) || []
  } catch (e) {
    woOperations.value = []
  }
}

/**
 * 打开某道工序的操作面板。
 * 2026-09-27 第7期：工序明细表现在直接就是「工单工序」行（带行ID id），优先直接用它；
 * 兼容旧的进度行（只有工艺路线工序ID/行号）→ 按 operationId / lineNo 去工单工序实例里匹配。
 */
function openOpPanel(row) {
  if (!row) return
  if (row.id) {
    // 确保面板拿得到工单ID（工序通知要按工单查下道工序）
    currentOperation.value = { ...row, orderId: row.orderId ?? workOrder.orderId ?? orderId }
    opPanelVisible.value = true
    return
  }
  const list = woOperations.value || []
  // 进度流程的工序行没有工单工序行ID：按「成品行 + 工序号」精确匹配，且必须唯一命中；
  // 再按「工序主档ID + 成品行」唯一匹配。定不下来就明确提示、**绝不猜** ——
  // 与"批量打印打错码"同源：原来取"第一个命中"，多成品/行号重排时会打开另一道工序。
  const sameProduct = (o) => (row.orderProductId == null ? true : o.orderProductId === row.orderProductId)
  let hit = null
  if (row.lineNo != null) {
    const byLine = list.filter(o => o.lineNo === row.lineNo && sameProduct(o))
    if (byLine.length === 1) hit = byLine[0]
  }
  if (!hit && row.operationId != null) {
    const byOp = list.filter(o => o.operationId === row.operationId && sameProduct(o))
    if (byOp.length === 1) hit = byOp[0]
  }
  if (!hit) {
    ElMessage.warning(tt('该工序还没有工单工序实例，请先提交/生效工单（生效时按工艺路线生成）'))
    return
  }
  currentOperation.value = { ...hit, orderId: hit.orderId ?? workOrder.orderId ?? orderId }
  opPanelVisible.value = true
}

// ---------------- 工序卡打印（含二维码，2026-09-27）：单个工序 / 整单批量 ----------------
const opCardVisible = ref(false)
const opCardId = ref(null)
const opBatchVisible = ref(false)
const opCardBatchIds = ref([])

/**
 * 取工序行的"工单工序实例"主键（打印二维码 / 打开工序面板都靠它定位）。
 *
 * 第 7 期起明细行本身就是工单工序行（自带主键）→ 直接用；
 * 旧数据 / 尚未保存的行没有主键 → 按「同一成品行 + 同一行号」精确回捞，且**必须唯一命中**才认；
 * 再退一步按「工序主档 ID + 同一成品行」回捞，同样要求唯一。
 * 仍然定不下来就返回 null（**宁可不打印，也不猜出别的工序**）——
 * 历史 bug：原来用 find 取"第一个命中"，多成品/行号重排时会捞到另一道工序，
 * 表现就是"标签上写着子项、扫出来却是雕刻"。
 */
function opInstanceId(row) {
  if (!row) return null
  if (row.id != null) return row.id
  const list = woOperations.value || []
  const sameProduct = (o) => (row.orderProductId == null ? true : o.orderProductId === row.orderProductId)
  const pickId = (o) => (o.id != null ? o.id : (o.operationRowId != null ? o.operationRowId : null))
  if (row.lineNo != null) {
    const byLine = list.filter(o => o.lineNo === row.lineNo && sameProduct(o))
    if (byLine.length === 1) return pickId(byLine[0])
  }
  if (row.operationId != null) {
    const byOp = list.filter(o => o.operationId === row.operationId && sameProduct(o))
    if (byOp.length === 1) return pickId(byOp[0])
  }
  return null
}

function printOpCard(row) {
  const id = opInstanceId(row)
  if (id == null) {
    ElMessage.warning(tt('该工序还没有工单工序实例，请先提交/生效工单（生效时按工艺路线生成）'))
    return
  }
  opCardId.value = id
  opCardVisible.value = true
}

/**
 * 批量打印工序卡：
 * ① 只打印「勾选参与本工单」的工序（与保存口径一致，避免把已取消勾选的行也打出来、拿错纸）；
 * ② 定不到工单工序实例的行**直接跳过并明确提示**，绝不猜（否则会打出别的工序的码）。
 */
function printBatchOpCards() {
  const rows = (operations.value || []).filter(r => r._selected !== false)
  const ids = []
  let skipped = 0
  rows.forEach(r => { const id = opInstanceId(r); if (id != null) ids.push(id); else skipped++ })
  if (ids.length === 0) {
    ElMessage.warning(tt('当前没有可打印的工序（工序卡在工单提交/生效后才生成）'))
    return
  }
  if (skipped > 0) {
    ElMessage.warning(`${tt('有')} ${skipped} ${tt('道工序还没有工单工序实例，已跳过；请先保存或生效工单后再打印')}`)
  }
  opCardBatchIds.value = ids
  opBatchVisible.value = true
}

/** 面板里操作成功 → 刷新进度与工序实例（状态/数量都变了） */
async function onOperationChanged() {
  await loadProgress()
  await loadWoOperations()
}

// 加载工单进度数据
async function loadProgress() {
  if (!orderId) return
  // 工序操作面板需要工单工序实例（含行ID），与进度一起刷新
  loadWoOperations()
  loading.value = true
  try {
    // 确保物料列表已加载（用于回填物料图片）
    if (materialOptions.value.length === 0) {
      try {
        const matRes = await listAllMaterial()
        materialOptions.value = Array.isArray(matRes) ? matRes : (matRes?.records || [])
        // 行内下拉附加字段/搜索：物料到位后按批预取（不阻塞渲染）
        mcf.preloadAll(materialOptions.value)
      } catch (e) {}
    }
    const res = await getWorkOrderProgress(orderId)
    if (res) {
      if (res.workOrder) Object.assign(workOrder, res.workOrder)
      progressData.operations = res.operations || []
      progressData.recentReports = res.recentReports || []
      progressData.recentTransfers = res.recentTransfers || []
      progressData.totalProgress = res.totalProgress || 0
      progressData.currentOperation = res.currentOperation || ''
      progressData.currentOperationIndex = res.currentOperationIndex || -1
      // 先纠正进度界面（成品与工序都已就位，立即对齐，避免先闪一下历史 1）
      alignPlanQuantitiesWithProducts()
    }
    if (!Object.keys(workOrder).length || !workOrder.orderNo) {
      const wo = await getWorkOrder(orderId)
      if (wo) Object.assign(workOrder, wo)
    }
    // 回填原料行物料图片及单位等
    if (workOrder.materials && materialOptions.value && materialOptions.value.length > 0) {
      workOrder.materials.forEach(row => {
        if (!row.materialMainImage && row.materialId) {
          const m = materialOptions.value.find(x => x.materialId === row.materialId)
          row.materialMainImage = m?.mainImage || ''
          if (!row.materialCode && m) row.materialCode = m.materialCode
          if (!row.materialName && m) row.materialName = m.materialName
          if (!row.specification && m) row.specification = m.model || ''
        }
      })
    }
    // 回填成品行物料图片及单位等
    if (workOrder.products && workOrder.products.length > 0 && materialOptions.value && materialOptions.value.length > 0) {
      workOrder.products.forEach(row => {
        if (!row.materialMainImage && row.materialId) {
          const m = materialOptions.value.find(x => x.materialId === row.materialId)
          row.materialMainImage = m?.mainImage || ''
          if (!row.materialCode && m) row.materialCode = m.materialCode
          if (!row.materialName && m) row.materialName = m.materialName
          if (!row.specification && m) row.specification = m.model || ''
          if (!row.unitName && m) row.unitName = m.baseUnitName || ''
        }
      })
    }
    if (!workOrder.materialMainImage && workOrder.materialId && materialOptions.value && materialOptions.value.length > 0) {
      const m = materialOptions.value.find(x => x.materialId === workOrder.materialId)
      workOrder.materialMainImage = m?.mainImage || ''
    }
    // 单据自定义字段：编辑/打开工单时读取已保存的值并回填到表单（只覆盖自定义字段键）
    if (headerCustomFieldMap.size > 0 && workOrder.orderId) {
      const headerValues = await loadCustomFieldValues('production_order', workOrder.orderId)
      Object.assign(workOrder, headerValues)
    }
    // 计税方式：老单据按设置中心默认/总开关归一，再按口径回算原料明细两列
    await initTaxSettings()
    ;(workOrder.materials || []).forEach(r => calcMaterialRow(r))
    // 工序清单（第10期）：进度接口返回的 workOrder 里已带 operations（含 orderProductId），
    // 这里按"成品行 → 工序"重建绑定（不再额外请求详情）
    syncOpsFromServer()
    // 原料行归属（2026-11-10）：按 order_product_id 还原到前端成品行，供"按成品行查看用料 / 换 BOM 只替换本行"
    syncMaterialProductPid()
    // 历史数据纠正：已有工单的工序计划数量若仍是默认 1，按成品行计划数量对齐（未报工的工序）
    alignPlanQuantitiesWithProducts()
    // 【2026-10-09】打开已有工单时默认页签：没有工序就落在「成品明细」——
    // 此时「进度流程」是空页没有意义（MRP 下推生成的工单就属于这种）；有工序仍按老习惯落在「进度流程」。
    if (!(progressData.operations || []).length && !(operations.value || []).length) {
      activeTab.value = 'products'
    }
    // 【2026-10-09】明细卡片「记住上次折叠」会让人以为没数据：只要单据里有内容就自动展开，
    // 避免"下推出来的工单打开后原材料明细看不见"。
    if ((workOrder.materials || []).length) collapsed.items = true
    if ((workOrder.products || []).length) collapsed.items = true
  } catch (e) {
  } finally {
    loading.value = false
  }
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
  getId: () => form.orderId,
  api: {
    // 通用动作集（可见性由 BillActions + bill-status 的 can* 判定；此处只登记本页真实具备的能力）
    approve: approveWorkOrder,
    reject: rejectWorkOrder,
    reverseApprove: reverseApproveWorkOrder,
    close: closeWorkOrder,
    reopen: reopenWorkOrder,
    void: voidWorkOrder,
    unvoid: unvoidWorkOrder,
    delete: deleteWorkOrder
  },
  reload: loadProgress,
  router,
  newPath: '/production/workorders/new',
  listPath: '/production/workorders',
  billLabel: '生产工单'
})

// ===== 复制功能 =====
const { applyCopy } = useCopyBill({
  route,
  loadFn: getWorkOrder,
  form: workOrder,
  idField: 'orderId',
  noField: 'orderNo',
  itemsField: 'products',
  // 原料明细的整表重建由后端负责，这里只把行主键清掉，避免携带源单行标识；并按当前计税方式回算两列
  afterCopy: () => { (workOrder.materials || []).forEach(r => { r.orderProductId = null; r._productPid = ''; calcMaterialRow(r) }) },
  // 表头自定义字段随复制带出（生产工单自定义字段为表头级 ext_data）
  customField: { entity: 'production_order' }
})

// 加载单据自定义字段定义（生产工单）。失败静默降级：只打印告警，绝不阻塞页面。
async function loadCustomFields() {
  try {
    const result = await loadCustomFieldDefinitions('production_order')
    headerCustomSections.value = result.sections
    headerCustomFieldMap.clear()
    result.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
    headerFormulaFields.value = result.formulaFields
  } catch (e) {
    console.warn('[WorkOrderEdit] 加载单据自定义字段定义失败:', e)
  }
}

// 保存单据自定义字段值（生产工单）。保存失败向上抛出，由 onSave 统一提示，避免出现"假成功"。
async function saveHeaderCustomFields() {
  if (headerCustomFieldMap.size === 0) return
  const id = workOrder.orderId
  if (!id) return
  const headerValues = {}
  for (const key of headerCustomFieldMap.keys()) {
    if (key in workOrder) headerValues[key] = workOrder[key]
  }
  await saveCustomFieldValues('production_order', id, headerValues, headerFormulaFields.value)
}

// 【2026-11-25】生产工序自定义字段定义（设置中心 → 自定义字段配置 → 生产工序）。
// 失败静默降级：只告警，不阻塞工单页面。
async function loadOpCustomFields() {
  try {
    const result = await loadCustomFieldDefinitions('work_order_operation')
    opCustomDefs.value = Array.from(result.fieldMap.values())
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    opCustomFormulaFields.value = result.formulaFields
  } catch (e) {
    console.warn('[WorkOrderEdit] 加载工序自定义字段定义失败:', e)
  }
}

/**
 * 【2026-11-26】自定义字段值读取归一化：
 * 值表统一存文本，勾选字段回读是 "1"/"0"，直接给 el-switch 的 active-value=1 会永远不勾；
 * 数字字段回读是字符串，e-input-number 需要 Number。
 */
function normalizeOpCustomValue(f, v) {
  if (v === undefined || v === null || v === '') return v
  if (f.fieldType === 'switch') {
    return (v === 1 || v === '1' || v === true || v === 'true') ? 1 : 0
  }
  if (f.fieldType === 'number' || f.fieldType === 'formula') {
    const n = Number(v)
    return Number.isNaN(n) ? v : n
  }
  return v
}

/** 打开工序配置弹窗时，按工序行ID批量取出已填值并挂到行上（没有值的行补空对象） */
async function loadOpCustomValues() {
  const rows = dialogOps.value || []
  const ids = rows.map(r => r.id).filter(id => id != null)
  let map = {}
  if (ids.length) {
    try {
      map = await batchLoadCustomFieldValues('work_order_operation', ids)
    } catch (e) {
      console.warn('[WorkOrderEdit] 加载工序自定义字段值失败:', e)
    }
  }
  rows.forEach(r => {
    const raw = r.id != null && map[r.id] ? map[r.id] : (r.customFields || {})
    if (!opCustomDefs.value.length) {
      r.customFields = { ...raw }
      return
    }
    const norm = {}
    opCustomDefs.value.forEach(f => {
      if (raw[f.fieldKey] !== undefined && raw[f.fieldKey] !== null) {
        norm[f.fieldKey] = normalizeOpCustomValue(f, raw[f.fieldKey])
      }
    })
    r.customFields = norm
  })
}

// 保存工单（第10期两段式：整单先存成品明细 → 取回成品行最新主键 → 按成品行号把工序绑上去）
async function onSave(silent, opts = {}) {
  if (workOrder.status && !isDraft.value && isEdit.value) {
    ElMessage.warning(`${tt("当前状态为「")}${workOrder.status}${tt("」, 不可修改")}`)
    return false
  }
  opDialogVisible.value = false
  // 按当前表头「计税方式」把原料明细的含税价/不含税价/金额、工序的换算两列刷成最新
  ;(workOrder.materials || []).forEach(r => calcMaterialRow(r))
  ;(operations.value || []).forEach(r => calcOpRow(r))
  // 宽松草稿保存：丢弃未选物料的空行（后端这些字段 NOT NULL，无法落库）
  const products = (workOrder.products || []).filter(r => r && r.materialId)
  // 成品行号按顺序重排（10/20/30…）：成品行号是"工序归属"的稳定参照，保存后要能按行号回溯到最新主键
  products.forEach((p, i) => { p.lineNo = (i + 1) * 10; ensureProductPid(p) })
  workOrder.products = products
  // 工序：只提交勾选行；用"成品行号"记录归属（成品主键保存后会重排，不能直接存主键）
  const lineByPid = {}
  products.forEach(p => { lineByPid[p._pid] = p.lineNo })
  // 原料行归属（2026-11-10）：上传「成品行号」，后端再换成成品行新主键写进 order_product_id。
  // 不能传成品行主键 —— 保存会重建成品行，且主键塞进原料主键会撞主键。
  ;(workOrder.materials || []).forEach(m => {
    m.orderProductId = (m._productPid && lineByPid[m._productPid] != null) ? lineByPid[m._productPid] : null
  })
  const opsPayload = opsConfigurable.value
    ? operations.value.filter(r => r._selected !== false).map(r => {
        const { _selected, _k, _pid, ...rest } = r
        return {
          ...rest,
          customFields: { ...(r.customFields || {}) },
          _pidLine: (_pid != null && lineByPid[_pid] != null) ? lineByPid[_pid] : null
        }
      })
    : null
  // 【2026-11-25】工序自定义字段必填校验：只拦「保存工单」，不拦开工/完工等工序动作
  if (opsPayload) {
    const missing = validateOpCustomRequired(opsPayload)
    if (missing) { ElMessage.warning(missing); return false }
  }
  // 整单请求体不夹带工序：成品明细每次保存都会整表重建，夹带会把工序绑到已失效的成品主键
  workOrder.operations = null
  saving.value = true
  try {
    if (isEdit.value && workOrder.orderId) {
      await updateWorkOrder(workOrder.orderId, workOrder)
      if (opsPayload !== null) await persistOperations(workOrder.orderId, opsPayload)
      // 单据自定义字段：主表已落库、订单ID已知，写入通用自定义字段表
      await saveHeaderCustomFields()
    } else {
      const res = await createWorkOrder(workOrder)
      if (res && res.orderId) {
        workOrder.orderId = res.orderId
        if (opsPayload !== null) await persistOperations(res.orderId, opsPayload)
        // 新建首次保存：拿到新订单ID后立即写入自定义字段值
        await saveHeaderCustomFields()
        // 新建保存后跳转到编辑页
        if (!opts.skipRouteSync) router.replace(`/production/workorders/${res.orderId}`)
        return true
      }
      ElMessage.error(tt('保存失败'))
      return false
    }
    triggerListRefresh('/production/work-orders')
    if (!silent) ElMessage.success(tt('保存成功'))
    await loadProgress()
    return true
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || tt('保存失败'))
    return false
  } finally {
    saving.value = false
  }
}

/**
 * 工序落库（第10期）：整单保存会把成品明细整表重建（主键会变），
 * 因此先取回成品行最新主键，再按"成品行号"把工序绑到对应成品行，最后走只保存工序清单的接口。
 */
async function persistOperations(orderId2, opsPayload) {
  const wo = await getWorkOrder(orderId2)
  const idByLine = {}
  ;((wo && wo.products) || []).forEach(p => { if (p.lineNo != null) idByLine[p.lineNo] = p.id })
  const rows = opsPayload.map(r => {
    const { _pidLine, ...rest } = r
    return { ...rest, orderProductId: (_pidLine != null ? (idByLine[_pidLine] ?? null) : null) }
  })
  await saveWorkOrderOperations(orderId2, rows)
}

async function onSubmit() {
  // 提交校验：纯手工建单（无来源单、无BOM）必须自行选择原料明细，为空则拦截并提示；
  // 有关联的场景（选源单/选了BOM）由后端保存时按 BOM 自动带出原料明细。
  const isLinked = !!(workOrder.sourceNo || workOrder.bomId)
  if (!isLinked && (!workOrder.materials || workOrder.materials.length === 0)) {
    ElMessage.warning(tt('未选择原料明细，请先添加原料明细，或选择来源单/BOM后由系统自动带出'))
    return
  }
  // 无论新建还是已有草稿，提交前一律先静默保存（把当前页面所见落库，避免提交旧数据）
  const saved = await onSave(true, { skipRouteSync: true })
  if (!saved || !workOrder.orderId) return
  try {
    const needApproval = await confirmSubmit({ billType: 'PRODUCTION_ORDER', billLabel: '生产工单' })
    // 统一状态机：提交走 POST /workorders/{id}/submit（草稿 → 待审核；单据审批开关关闭时提交即生效）
    await submitWorkOrder(workOrder.orderId || orderId)
    ElMessage.success(submitSuccessMessage(needApproval))
    await loadProgress()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || tt('提交失败'))
  } finally {
    // 提交（或用户取消）后再把新单 id 写回地址栏：此时重建实例无副作用，新实例挂载时会重新拉取最新状态
    if (!route.params.id && workOrder.orderId) {
      try { await router.replace(`/production/workorders/${workOrder.orderId}`) } catch (_) {}
    }
  }
}



// 关闭工单
async function doClose() {
  if (!closeReason.value || !closeReason.value.trim()) {
    ElMessage.warning(tt('请输入关闭原因'))
    return
  }
  acting.value = true
  try {
    await closeWorkOrder(workOrder.orderId || orderId, closeReason.value)
    ElMessage.success(tt('工单已关闭'))
    closeDialog.value = false
    closeReason.value = ''
    loadProgress()
  } catch (e) {
    ElMessage.error(e?.message || tt('关闭失败'))
  } finally {
    acting.value = false
  }
}

function goConfigRouting() {
  router.push('/production/routings')
}

// 处理更多操作下拉
// 下推操作状态
const pushing = ref(false)

// 通用下推处理：调用下推接口，成功后提示并跳转
async function doPush(apiFn, label, buildPath) {
  const id = workOrder.orderId || orderId
  if (!id) {
    ElMessage.warning(tt('请先保存工单再下推'))
    return
  }
  try {
    await ElMessageBox.confirm(`${tt("确认下推生成「")}${label}」？`, tt('确认下推'), { type: 'warning' })
  } catch (e) {
    return // 用户取消
  }
  pushing.value = true
  try {
    const fn = apiFn
    const res = await fn(id)
    ElMessage.success(`${label}${tt("下推成功")}`)
    // 优先使用返回的新单据ID跳转，否则跳转到列表页
    const newId = res && (res.id || res.orderId || res.requisitionId || res.reportId || res.transferId || res.receiptId || res.subcontractId)
    if (newId) {
      router.push(buildPath(newId))
    } else {
      // 后端未返回ID时跳转到对应列表页
      router.push(buildPath())
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || `${label}${tt("下推失败")}`)
  } finally {
    pushing.value = false
  }
}

// 下推领料
function handlePushRequisition() {
  doPush(pushRequisition, '领料单', (newId) =>
    newId ? `/inventory/material-requisitions/edit/${newId}` : '/inventory/material-requisitions'
  )
}

// 下推入库
function handlePushReceipt() {
  doPush(pushReceipt, '生产入库单', (newId) =>
    newId ? `/production/receipts/edit/${newId}` : '/production/receipts'
  )
}

const printVisible = ref(false)
function handlePrint() {
  if (!workOrder.orderId && !orderId) { ElMessage.warning(tt('请先保存工单再打印')); return }
  printVisible.value = true
}

// ===== 顶级页签：单据 / 来源单据 / 去向单据 =====
const topTab = ref('doc')
const billLinkRegistered = ref(false)

onMounted(async () => {
  // 物料自定义字段：定制开关 + 字段定义（失败静默降级）
  await mcf.init()
  // 该单据类型是否登记了「来源单据/去向单据」配置（未登记则不显示这两个页签）
  isBillLinkRegistered('PRODUCTION_ORDER')
    .then(v => { billLinkRegistered.value = !!v })
    .catch(() => { billLinkRegistered.value = false })

  loadCollapsed()
  await loadOptions()
  // 单据自定义字段定义（失败静默降级，不阻塞页面）
  await loadCustomFields()
  // 生产工序自定义字段定义（同上，失败静默降级）
  await loadOpCustomFields()
  if (!isEdit.value) {
    autoFillDocCodeOnCreate(workOrder, 'orderNo')
  }
  // 新建/复制：表头「计税方式」取设置中心「默认计税方式」（编辑态由 loadProgress 内部归一）
  await initTaxSettings()
  // 复制进入（地址带 ?copy=源单主键）：装入源单数据成新草稿；否则按编辑态加载进度
  const copied = await applyCopy()
  if (!copied && isEdit.value) {
    loadProgress()
  }
  // 下推进入自动带入（2026-10-19）：销售订单点「下推生成生产工单」跳转进来时（地址带 ?srcType=SaleOrder&srcId=..），
  // 自动拉源单详情并按「剩余可下推生产数量」带入成品明细；已推完的行自动跳过。
  // 与销售出库单/采购订单等编辑页同款机制（applyPushSource），此前本页漏接导致下推落地空白单。
  if (!copied && !isEdit.value) {
    const pushed = await applyPushSource(route, 'WorkOrders', workOrder, (items) => {
      const { rows, skipped } = buildProductsFromSaleItems(items)
      workOrder.products = rows
      if (rows.length === 0) {
        ElMessage.warning(tt('该销售订单明细已全部下推生产，没有剩余可下推数量'))
      } else if (skipped > 0) {
        ElMessage.info(tt(`${skipped} 行已全部下推生产，已自动跳过`))
      }
    }, 'items')
    if (pushed) ElMessage.info(tt('已从源单带入数据, 请确认后保存'))
  }
})

// 切回标签页时自动刷新下拉基础数据（只刷下拉，不动表单草稿），避免档案改过后这里还是旧数据
useBaseDataRefresh(loadOptions)


  </script>

<style scoped lang="scss">
.work-order-edit {
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

// 可点击字段链接样式
.field-link {
  font-size: 12px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  height: 28px;
  &:hover { text-decoration: underline; }
}

// ===== 明细表格 =====
.table-wrap {
  overflow-x: auto;
  padding: 10px 12px 0;
}

.detail-tabs {
  :deep(.el-tabs--border-card) {
    box-shadow: none;
  }
  :deep(.el-tabs__header) {
    background: #fafbfc;
  }
  /* 2026-11-07 用户口径：页签顺序永远是 成品明细 → 原料明细 → 进度流程 → 汇报记录 → 转移记录。
     这里只重排导航顺序（内容页顺序不变，点击/激活逻辑仍由 el-tabs 负责）。 */
  :deep(.el-tabs__nav) {
    display: flex !important;
  }
  :deep(.el-tabs__item) {
    flex: 0 0 auto;
  }
  :deep(#tab-products) { order: 1 !important; }
  :deep(#tab-materials) { order: 2 !important; }
  :deep(#tab-progress) { order: 3 !important; }
  :deep(#tab-reports) { order: 4 !important; }
  :deep(#tab-transfers) { order: 5 !important; }
}

// BOM 信息（2026-11-02 移入原料明细卡片内，表头正下方）
.bom-inline-form {
  padding: 10px 12px 4px;
  border-bottom: 1px dashed #ebeef5;
}

.section-title-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding: 8px 12px;
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
  /* 当前正在查看用料的成品行（点行切换后高亮，2026-11-10） */
  :deep(.el-table__row.active-product-row > td) {
    background: #ecf5ff !important;
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

// ===== 工序进度流程 =====
.progress-section {
  padding: 8px 4px;
}

// ===== 第10期：进度按成品分组的块 =====
.product-progress-block {
  margin-bottom: 14px;

  .pp-head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    margin-bottom: 6px;
    background: #f0f6ff;
    border-left: 3px solid #409eff;
    border-radius: 4px;

    .pp-icon { color: #409eff; }
    .pp-name { font-size: 13px; font-weight: 600; color: #303133; }
    .pp-count { font-size: 12px; color: #909399; margin-left: auto; }
  }
}

// 成品工序弹窗
.op-dialog-sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #606266;

  b { color: #303133; font-weight: 600; }
  .op-dialog-qty { margin-left: auto; color: #909399; }
}

// ===== 报工/入库进度拆分 =====
.progress-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 12px 12px 16px;
  background: linear-gradient(180deg, #f8f9fb 0%, #ffffff 100%);
  border-radius: 10px;
  border: 1px solid #ebeef5;
  margin-bottom: 12px;
}

.progress-overview-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  transition: all .25s;
  &:hover {
    box-shadow: 0 4px 14px rgba(64, 158, 255, 0.1);
    border-color: #c6e2ff;
  }

  .overview-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    .overview-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      display: flex;
      align-items: center;
      gap: 6px;
      &::before {
        content: '';
        width: 3px;
        height: 14px;
        background: linear-gradient(180deg, #409eff, #66b1ff);
        border-radius: 2px;
      }
    }
  }

  .overview-body {
    .overview-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 10px;
      font-size: 12px;
      color: #606266;
      flex-wrap: wrap;
      gap: 6px;
      .num {
        color: #303133;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        &.primary { color: #409eff; }
        &.success { color: #67c23a; }
        &.danger { color: #f56c6c; }
        &.warning { color: #e6a23c; }
      }
    }
  }
}

.timeline-flow {
  display: flex;
  align-items: flex-start;
  gap: 0;
  overflow-x: auto;
  padding: 24px 12px 16px;
  background: linear-gradient(180deg, #f8f9fb 0%, #ffffff 100%);
  border-radius: 10px;
  border: 1px solid #ebeef5;
}

.timeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  flex-shrink: 0;
}

.step-node {
  width: 140px;
  min-height: 110px;
  background: white;
  border: 2px solid #dcdfe6;
  border-radius: 12px;
  padding: 14px 10px;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  position: relative;
  z-index: 2;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 16px rgba(0,0,0,0.1);
  }

  &.step-done .op-dot {
    background: #67c23a;
    border-color: #67c23a;
    color: white;
    .check-icon { font-size: 20px; }
  }
  &.step-done {
    border-color: #67c23a;
    background: #f0f9eb;
  }

  &.step-active .op-dot {
    background: #409eff;
    border-color: #409eff;
    color: white;
    .loading-icon {
      font-size: 20px;
      animation: spin 1.2s linear infinite;
    }
  }
  &.step-active {
    border-color: #409eff;
    background: #ecf5ff;
    box-shadow: 0 0 0 5px rgba(64, 158, 255, 0.12);
  }

  &.step-pending {
    opacity: 0.75;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

// 工序进度圆点
.op-dot {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #f4f4f5;
  border: 2px solid #dcdfe6;
  color: #909399;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 2px;
}

// 工序进度信息
.op-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: center;
}

.step-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  line-height: 1.3;
}

.step-qty {
  font-size: 12px;
  color: #606266;
  font-weight: 500;
}

.step-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 2px;
}

.step-connector {
  width: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  margin-top: 50px;
  height: 4px;
  z-index: 1;
  .connector-bar {
    width: 100%;
    height: 4px;
    background: #e4e7ed;
    border-radius: 2px;
    transition: background 0.3s;
  }
  &.done .connector-bar {
    background: linear-gradient(90deg, #67c23a, #85ce61);
  }
}

.op-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

// ===== 工序清单（可配置表，2026-09-27） =====
.op-config-section {
  padding: 0 12px 12px;
}

.op-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .op-add-select { width: 280px; }
}

.op-toolbar-tip {
  font-size: 12px;
  color: #9ca3af;
}

.op-table-wrap {
  overflow-x: auto;
}

.op-config-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;

  th, td {
    border: 1px solid #ebeef5;
    padding: 4px 6px;
    text-align: left;
    vertical-align: middle;
    white-space: nowrap;
  }

  thead th {
    background: #f5f7fa;
    color: #606266;
    font-weight: 600;
    font-size: 12px;
    padding: 6px 4px;
  }

  /* 列宽拖拽手柄（2026-10-08）：贴在表头单元格右边界内侧
     注意：不能写成 right:-3px —— 超出单元格的部分会被表头的 overflow 裁掉，鼠标点不到（实测拖不动） */
  th { position: relative; overflow: visible; }
  .op-col-resizer {
    position: absolute;
    top: 0;
    right: 0;
    width: 6px;
    height: 100%;
    cursor: col-resize;
    z-index: 3;
    &:hover { background: rgba(64, 158, 255, .25); }
  }

  .op-col-check { width: 44px; text-align: center; }
  .op-col-drag { width: 34px; text-align: center; }
  .op-col-no { width: 64px; text-align: center; }
  .op-col-flag { width: 84px; text-align: center; }
  .op-col-op { width: 80px; text-align: center; }
  /* 工序卡列：需容纳「工序码 + 打印」，比普通操作列宽 */
  .op-col-card { width: 124px; }
  /* 工序码与打印入口并排：竖向码牌本身只有 64px 宽，并排后行高不被撑高 */
  .op-qr-cell {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .op-col-status { text-align: center; }
  .op-col-right { text-align: right; }
  .op-col-edit { min-width: 130px; }

  .op-drag-handle {
    cursor: move;
    color: #909399;
    display: inline-flex;
    align-items: center;
  }

  .op-mini-num { width: 118px; }
  .op-mini-date { width: 138px; }
  .op-mini-select { width: 120px; }
  /* 【2026-11-25】生产工序自定义字段列：列多时横向滚动，输入控件不撑破表格 */
  .op-col-custom { min-width: 140px; }
  .op-col-custom .el-input,
  .op-col-custom .el-select { width: 100%; min-width: 120px; }
  .op-col-custom .el-textarea__inner { min-height: 28px !important; height: 28px !important; }
}

.op-cf-readonly {
  display: inline-block;
  min-width: 60px;
  color: #606266;
  font-size: 13px;
}

.op-status-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.op-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.op-empty-tip {
  margin: 4px 0 8px;
  padding: 14px 16px;
  background: #f8f9fb;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  color: #909399;
  font-size: 12px;
}

.text-success { color: #67c23a; font-weight: 500; }
.text-danger { color: #f56c6c; font-weight: 500; }
.text-warning { color: #e6a23c; font-weight: 500; }

// ===== 金额汇总区 =====
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
  .base-form .section-fields { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .base-form .section-fields { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .work-order-edit { padding: 8px; }
  .page-header { flex-direction: column; gap: 8px; align-items: stretch; }
  .base-form .section-fields { grid-template-columns: 1fr; }
  .summary-area { flex-direction: column; }
  .progress-overview { grid-template-columns: 1fr; }
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
/* 行内下拉附加字段（自定义字段「字段名：值」）：灰色小字，仅在有值时渲染 */
.opt-cf {
  color: #909399;
  font-size: 11px;
  margin-left: 8px;
  white-space: normal;
  word-break: break-word;
}
</style>

<!-- 非 scoped：物料下拉的 popper 被 teleport 到 body，scoped 选择器无法命中，只能配全局类名（popper-class="material-select-popper"） -->
<style>
/* 面板宽度：Element Plus 给 .el-select-dropdown 设的是【行内】min-width = 触发框宽度-2px，
   行内样式无法用普通 CSS 覆盖，因此在 popper 根节点 .material-select-popper 上设 min-width */
.material-select-popper {
  min-width: 640px;
  max-width: 90vw;
}
/* 选项内容：默认 white-space:nowrap + overflow:hidden 会把「自定义字段」内容截断成省略号，
   改为允许换行 + 高度自适应，保证内容不被隐藏 */
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
