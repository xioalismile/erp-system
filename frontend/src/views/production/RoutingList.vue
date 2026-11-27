<template>
  <!-- 通用列表壳（页头/统计卡/搜索条/表格外壳/分页/打印/列设置）+ 声明式筛选维度
       原「状态页签」整块删除 —— 状态改用通用搜索栏的「单据状态」下拉（filters 声明 'status' 时壳自动隐藏页签）
       该页无「单据日期」，只有生效日期 effective_date → 日期维度标签传「生效日期」 -->
  <BillListPage
    page-class="routing-list-page"
    back-path="/production"
    :title="$tt('工艺路线')"
    badge="ROUTING"
    sub-title="管理生产工艺路线 · 追踪工序工时"
    :stats="stats"
    :active-stat-key="activeStatKey"
    search-placeholder="输入工艺路线编码/名称搜索"
    keyword-label="路线编码"
    date-label="生效日期"
    :filters="['status', 'date']"
    :status-options="statusOptions"
    :filter-model="search"
    :page-sizes="[20, 50, 100]"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="RoutingList"
    :columns="columns"
    :table-key="configVersion"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    v-model:column-setting-visible="columnSettingVisible"
    v-model:print-visible="printVisible"
    v-model:print-bill-id="printBillId"
    @search="handleSearch"
    @reset="handleReset"
    @filter-change="onFilterChange"
    @stat-click="onStatClick"
    @sort-change="onSortChange"
    @page-change="fetchData"
  >
    <template #header-actions>
      <el-button type="primary" class="btn-primary-glow" v-permission="perm('add')" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增工艺路线
      </el-button>
      <el-button type="success" :icon="Download" v-permission="perm('export')" @click="handleExport">{{ $tt("导出") }}</el-button>
    </template>

    <el-table-column type="index" label="#" width="60" />
    <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
    <template v-for="col in visibleSortedColumns" :key="col.prop">
      <el-table-column
        :prop="col.prop"
        :label="$tf(col.label)"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
        :sortable="col.sortable ? 'custom' : false"
        :show-overflow-tooltip="!!col.tooltip"
        :fixed="getColFixed(col.prop, col.fixed)"
      >
        <template v-if="col.type" #default="{ row }">
          <div v-if="col.type === 'routingCode'" class="order-no-cell">
            <span class="order-no-text" @click="handleEdit(row)">{{ row.routingCode }}</span>
            <el-icon class="link-icon"><Link /></el-icon>
          </div>
          <div v-else-if="col.type === 'status'" class="status-cell">
            <span :class="['status-dot', statusDotClass(row.status)]"></span>
            <el-tag size="small" :type="statusTagType(row.status)" effect="light" class="status-tag">{{ $dt(row.status) }}</el-tag>
          </div>
          <span v-else-if="col.type === 'material'">{{ materialNameOf(row) }}</span>
        </template>
      </el-table-column>
    </template>
    <AuditColumns />
  </BillListPage>

  <!-- 新增/编辑弹窗：方向 A 主从工作台（左工序清单 + 右工序详情）；原有全部字段与功能保留 -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1400px" top="4vh" @close="resetForm">
    <el-form :model="form" label-width="96px" :rules="rules" ref="formRef">
      <!-- 表头卡片 -->
      <section class="rd-card">
        <div class="rd-card-head">
          <div>
            <div class="rd-card-title">工艺路线信息</div>
            <div class="rd-card-hint">原有表头字段与工序控制全部保留；一个工艺路线可绑定多个物料，也可按物料分类绑定</div>
          </div>
          <span class="rd-status" :class="form.status === '启用' ? 'on' : ''">{{ form.status }}</span>
        </div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item :label="$tf('工艺路线编码')" prop="routingCode">
              <el-input v-model="form.routingCode" :placeholder="$tt('留空自动生成')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('工艺路线名称')" prop="routingName">
              <el-input v-model="form.routingName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('版本')">
              <el-input v-model="form.routingVersion" :placeholder="$tt('如 V1.0')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('是否主工艺')">
              <el-select v-model="form.isPrimary" style="width:100%">
                <el-option :label="$tf('主工艺路线')" :value="1" />
                <el-option :label="$tf('替代工艺路线')" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="rd-scope">
          <div class="rd-field">
            <span class="rd-label">适用物料</span>
            <div class="rd-tags">
              <el-tag v-for="m in form.materials" :key="m.id" size="small" closable @close="removeMaterial(m.id)">
                {{ m.name }}<span class="rd-code">{{ m.code }}</span>
              </el-tag>
              <el-button size="small" plain type="primary" @click="materialPickerVisible = true">+ 从物料库选择</el-button>
              <span v-if="!form.materials.length" class="rd-tags-empty">未绑定物料时，所有物料都可以选这条路线</span>
            </div>
          </div>
          <div class="rd-field">
            <span class="rd-label">适用分类</span>
            <div class="rd-tags">
              <el-tag v-for="c in form.categories" :key="c.id" size="small" type="success" closable @close="removeCategory(c.id)">
                {{ c.fullName || c.name }}
              </el-tag>
              <el-select v-model="form.categoryIds" multiple filterable clearable collapse-tags collapse-tags-tooltip
                         placeholder="选择分类" size="small" style="width:280px" @change="onCategoryChange">
                <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </div>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="BOM">
              <el-select v-model="form.bomId" :placeholder="$tt('请选择BOM')" filterable clearable style="width:100%"
                         @change="onRoutingBomChange">
                <el-option v-for="b in filteredBomOptions" :key="b.bomId" :label="`${b.bomCode} - ${b.bomName || b.materialName}`" :value="b.bomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('生效日期')">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('失效日期')">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$tf('总工时')">
              <el-input-number v-model="form.totalWorkHours" :controls="false" :precision="4" disabled style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item :label="$tf('状态')" prop="status">
              <el-select v-model="form.status" style="width:100%">
                <el-option :label="$tf('启用')" value="启用" />
                <el-option :label="$tf('停用')" value="停用" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="taxEnabled">
            <el-form-item :label="$tf('计税方式')">
              <el-select v-model="form.taxCalcMethod" style="width:100%" @change="onTaxCalcMethodChange">
                <el-option v-for="o in taxMethodOptions" :key="o" :label="$tf(o)" :value="o" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('备注')">
              <el-input v-model="form.remark" type="textarea" :rows="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="rd-card-title" style="margin-top:10px">工序控制</div>
        <div class="switch-row">
          <div class="switch-item">
            <el-switch v-model="form.autoNotifyNext" :active-value="1" :inactive-value="0" size="small" />
            <span class="switch-label">{{ $tt("自动通知下道负责人") }}</span>
            <el-tooltip placement="top" :content="$tt('开启后，本路线工序完工（含质检通过）时，自动给下一道工序的负责人发一条消息（消息中心与工作台）')">
              <span class="switch-help">?</span>
            </el-tooltip>
          </div>
          <div class="switch-item">
            <el-switch v-model="form.enforceSequence" :active-value="1" :inactive-value="0" size="small" />
            <span class="switch-label">{{ $tt("按顺序执行") }}</span>
            <el-tooltip placement="top" :content="$tt('开启后，前道工序未完工时，后道工序不能「开工」')">
              <span class="switch-help">?</span>
            </el-tooltip>
          </div>
          <div class="switch-item">
            <el-switch v-model="form.autoInputFromPrev" :active-value="1" :inactive-value="0" size="small" />
            <span class="switch-label">{{ $tt("投入带出上道产出") }}</span>
            <el-tooltip placement="top" :content="$tt('开启后，工序操作面板的「本次投入」自动填上道工序的产出数量；关闭则默认填计划数量')">
              <span class="switch-help">?</span>
            </el-tooltip>
          </div>
        </div>
        <div class="assign-tip">{{ $tt("「谁可以改负责人」已改为在下方「工序清单」里逐道工序各自设置") }}</div>
      </section>

      <!-- 主从区：左工序清单 + 右工序详情 -->
      <div class="rd-master">
        <aside class="rd-list">
          <div class="rd-list-head"><b>工序清单</b><span>{{ form.operations.length }} 道</span></div>
          <el-button type="primary" plain size="small" v-permission="perm('addRow')" @click="addOperation" style="width:100%;margin-bottom:8px">{{ $tt("添加工序") }}</el-button>
          <div v-for="(op, idx) in form.operations" :key="op.operationKey || idx" class="rd-op-item" :class="{ on: idx === activeOpIndex }" @click="activeOpIndex = idx">
            <span class="rd-seq">{{ String(idx + 1).padStart(2, '0') }}</span>
            <span class="rd-op-main">
              <span class="rd-op-name">{{ op.operationName || '未命名工序' }}</span>
              <span class="rd-op-sub">{{ op.operationCode || '保存后自动生成' }} · {{ op.workCenterName || '未设工作中心' }}</span>
            </span>
            <span class="rd-op-side">
              <el-tag size="small" effect="plain">{{ op.operationType || '状态类型' }}</el-tag>
              <span class="rd-op-cf">{{ (op.fieldDefs || []).length }} 项字段</span>
            </span>
          </div>
          <div v-if="!form.operations.length" class="rd-empty">还没有工序，点上面「添加工序」</div>
        </aside>

        <section class="rd-detail" v-if="activeOp">
          <div class="rd-detail-head">
            <div>
              <div class="rd-eyebrow">工序 {{ String(activeOpIndex + 1).padStart(2, '0') }}</div>
              <h3>{{ activeOp.operationName || '未命名工序' }}</h3>
              <div class="rd-meta">{{ activeOp.operationCode || '保存后自动生成' }} · {{ activeOp.operationType || '状态类型' }} · 自定义字段 {{ (activeOp.fieldDefs || []).length }} 项</div>
            </div>
          </div>

          <section class="rd-card inner">
            <div class="rd-card-title">基础信息</div>
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="工序编码">
                  <el-input v-model="activeOp.operationCode" size="small" disabled :placeholder="$tt('保存后自动生成')" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工序名称">
                  <el-input v-model="activeOp.operationName" size="small" @change="() => fillRowUnitHours(activeOp)" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工序类型">
                  <el-select v-model="activeOp.operationType" size="small" style="width:100%" @change="() => onOperationTypeChange(activeOp)">
                    <el-option v-for="t in operationTypes" :key="t" :label="$tf(t)" :value="t" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="父工序">
                  <el-select v-model="activeOp.parentIndex" :placeholder="$tt('顶层（不选）')" clearable filterable size="small" style="width:100%">
                    <el-option v-for="(o, oi) in form.operations" :key="oi" :label="o.operationName || (o.operationCode || ($tt('工序') + (oi + 1)))" :value="oi" :disabled="oi === activeOpIndex" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工位">
                  <el-select v-model="activeOp.workstationId" :placeholder="$tt('选择或输入新工位')" filterable clearable allow-create
                             default-first-option size="small" style="width:100%" @change="(v) => onWorkstationChange(activeOp, v)">
                    <el-option v-for="opt in workstationOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="工作中心">
                  <el-select v-model="activeOp.workCenterId" :placeholder="$tt('选择或输入新工作中心')" filterable clearable allow-create
                             default-first-option size="small" style="width:100%" @change="(v) => onWorkCenterChange(activeOp, v)">
                    <el-option v-for="opt in workCenterOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="rd-card inner">
            <div class="rd-card-title">工时</div>
            <el-row :gutter="16">
              <el-col :span="4">
                <el-form-item label="准备工时"><el-input-number v-model="activeOp.setupHours" :controls="false" :precision="4" :min="0" size="small" style="width:100%" @change="calcRow(activeOp)" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="加工工时"><el-input-number v-model="activeOp.processHours" :controls="false" :precision="4" :min="0" size="small" style="width:100%" @change="calcRow(activeOp)" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="单件工时"><el-input-number v-model="activeOp.unitHours" :controls="false" :precision="4" :min="0" size="small" style="width:100%" @change="calcRow(activeOp)" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="等待工时"><el-input-number v-model="activeOp.waitHours" :controls="false" :precision="4" :min="0" size="small" style="width:100%" @change="calcRow(activeOp)" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="移动工时"><el-input-number v-model="activeOp.moveHours" :controls="false" :precision="4" :min="0" size="small" style="width:100%" @change="calcRow(activeOp)" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="合计工时"><span class="rd-value">{{ activeOp.totalHours }}</span></el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="rd-card inner">
            <div class="rd-card-title">计价</div>
            <el-row :gutter="16">
              <el-col :span="4">
                <el-form-item label="批量"><el-input-number v-model="activeOp.batchSize" :controls="false" :min="1" size="small" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="人数"><el-input-number v-model="activeOp.workersRequired" :controls="false" :min="1" size="small" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="计件单价"><el-input-number v-model="activeOp.pieceRate" :controls="false" :precision="4" :min="0" size="small" style="width:100%" @change="() => calcOpPrice(activeOp)" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="含税价"><span class="rd-value">{{ Number(activeOp.priceWithTax || 0).toFixed(4) }}</span></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="不含税价"><span class="rd-value">{{ Number(activeOp.priceWithoutTax || 0).toFixed(4) }}</span></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="计划金额">
                  <el-input-number v-model="activeOp.planAmount" :controls="false" :precision="2" :min="0" size="small" style="width:100%" :disabled="!isAmountType(activeOp)" />
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="rd-card inner">
            <div class="rd-card-title">质检与权限</div>
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="需质检"><el-switch v-model="activeOp.isQcRequired" :active-value="1" :inactive-value="0" size="small" /></el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="关键工序"><el-switch v-model="activeOp.isCritical" :active-value="1" :inactive-value="0" size="small" /></el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="扫码认领"><el-switch v-model="activeOp.allowScanClaim" active-value="是" inactive-value="否" size="small" /></el-form-item>
              </el-col>
            </el-row>
            <el-form-item :label="$tf('谁可以改负责人')">
              <el-select v-model="activeOp.assignScope" size="small" style="width:220px" @change="() => onAssignScopeChange(activeOp)">
                <el-option :label="$tt('全部人')" value="全部人" />
                <el-option :label="$tt('当前负责人')" value="当前负责人" />
                <el-option :label="$tt('指定角色')" value="指定角色" />
                <el-option :label="$tt('工单开单人')" value="工单开单人" />
              </el-select>
              <el-popover v-if="activeOp.assignScope === '指定角色'" placement="top" :width="280" trigger="click">
                <template #reference>
                  <el-button link type="primary" size="small">
                    {{ $tt("选角色") }}<span v-if="roleCountOf(activeOp)">（{{ roleCountOf(activeOp) }}）</span>
                  </el-button>
                </template>
                <el-select v-model="activeOp.assignRoleList" multiple filterable clearable size="small" style="width:100%" :placeholder="$tt('选择可改负责人的角色')">
                  <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
                </el-select>
              </el-popover>
            </el-form-item>
          </section>

          <section class="rd-card inner">
            <el-form-item label="备注"><el-input v-model="activeOp.remark" type="textarea" :rows="2" /></el-form-item>
          </section>

          <section class="rd-card inner">
            <div class="rd-card-head">
              <div>
                <div class="rd-card-title">本工序自定义字段</div>
                <div class="rd-card-hint">只属于「{{ activeOp.operationName || '当前工序' }}」；工单操作面板与打印只显示这道工序自己的字段</div>
              </div>
              <el-button size="small" type="primary" plain @click="openFieldDialog()">+ 新增字段</el-button>
            </div>
            <el-table v-if="(activeOp.fieldDefs || []).length" :data="activeOp.fieldDefs" size="small" border>
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="fieldLabel" label="字段名称" min-width="140" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">{{ fieldTypeLabel(row.fieldType) }}</template>
              </el-table-column>
              <el-table-column label="必填" width="70" align="center">
                <template #default="{ row }">{{ row.isRequired ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="配置" min-width="160">
                <template #default="{ row }">{{ fieldConfigSummary(row) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="right">
                <template #default="{ row, $index }">
                  <el-button link type="primary" size="small" @click="openFieldDialog(row, $index)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="deleteFieldDef($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="rd-empty">本工序还没有自定义字段，点右上角「新增字段」只给这道工序加</div>
          </section>
        </section>
        <section v-else class="rd-detail rd-empty-detail">请先添加工序</section>
      </div>

      <div class="rd-save-bar">
        <div>总工时合计 <b>{{ form.totalWorkHours }}</b> · {{ form.operations.length }} 道工序</div>
        <div>
          <el-button v-permission="perm('dlgCancel')" @click="dialogVisible=false">{{ $tt("取消") }}</el-button>
          <el-button type="primary" v-permission="perm('save')" @click="handleSave">{{ $tt("保存工艺路线") }}</el-button>
        </div>
      </div>
    </el-form>
  </el-dialog>

  <!-- 物料库弹窗（多选）：选中即累加进「适用物料」 -->
  <MaterialPickerDialog v-model="materialPickerVisible" title="选择适用物料" @confirm="onMaterialPicked" />

  <!-- 工序自定义字段：新增/编辑 -->
  <el-dialog v-model="fieldDialogVisible" :title="fieldForm.index == null ? '新增字段' : '编辑字段'" width="480px" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="字段名称" required>
        <el-input v-model="fieldForm.fieldLabel" maxlength="20" placeholder="例如 客户要求" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="fieldForm.fieldType" style="width:100%">
          <el-option v-for="t in fieldTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="必填">
        <el-switch v-model="fieldForm.isRequired" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item v-if="fieldForm.fieldType === 'select'" label="下拉选项">
        <el-input v-model="fieldForm.optionsText" type="textarea" :rows="3" placeholder="每行一个选项" />
      </el-form-item>
      <el-form-item v-if="fieldForm.fieldType === 'number'" label="小数位数">
        <el-select v-model="fieldForm.precision" style="width:100%">
          <el-option :value="0" label="0 位" />
          <el-option :value="1" label="1 位" />
          <el-option :value="2" label="2 位" />
          <el-option :value="4" label="4 位" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="fieldDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveFieldDef">保存字段</el-button>
    </template>
  </el-dialog>
  <!-- 打印预览/列设置抽屉已由 BillListPage 壳统一渲染（bill-type / storage-key 传入） -->
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRouting, getRouting, saveRouting, updateRouting,
  getRoutingDashboard
} from '@/api/production/routing'
import { listAllRole } from '@/api/system/role'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import { listAllBom } from '@/api/basedata/bom'
import { listAllWorkstation, quickSaveWorkstation } from '@/api/production/workstation'
import { listAllWorkCenter, quickSaveWorkCenter } from '@/api/production/workCenter'
import { exportListAsSeen } from '@/utils/exportUtil'
import BillListPage from '@/components/bill/BillListPage.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { Plus, Download, Link } from '@element-plus/icons-vue'

import { useTableSort } from '@/composables/useTableSort'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'
// 全站商业口径总开关（含税 / 外币）+ 设置中心参数（默认计税方式）：表头「计税方式」驱动工序「含税价 / 不含税价」换算
import { useCommercialSwitches } from '@/composables/useCommercialSwitches'
import { useSysConfig } from '@/composables/useSysConfig'
const { handleSortChange, getSortParams } = useTableSort()
const perm = usePagePerm()

// ===== 计税方式（含税价 / 不含税价 / 不计税）：表头驱动工序「含税价 / 不含税价」换算（照销售订单试点） =====
const { taxEnabled, ensureLoaded: ensureCommercialSwitches } = useCommercialSwitches()
const { loadSysConfig, getConfig } = useSysConfig()
const taxMethodOptions = ['不含税价', '含税价', '不计税']

/** 历史值归一：价内税→含税价、价外税/空→不含税价；非法值兜底为不含税价 */
function normalizeTaxMethod(v) {
  if (v === '含税价' || v === '价内税') return '含税价'
  if (v === '不计税') return '不计税'
  return '不含税价'
}

/** 当前生效的计税方式（含税业务关闭 → 一律不计税） */
function currentTaxMethod() {
  if (!taxEnabled.value) return '不计税'
  const m = form.taxCalcMethod
  if (m === '含税价' || m === '不计税') return m
  return '不含税价'
}

/** 从设置中心取「默认计税方式」（新建时用；编辑时按老值归一） */
async function initTaxSettings(isNew) {
  try {
    await ensureCommercialSwitches()
    await loadSysConfig()
  } catch (e) { /* 读取失败保持默认 */ }
  if (!taxEnabled.value) {
    form.taxCalcMethod = '不计税'
  } else if (isNew) {
    form.taxCalcMethod = normalizeTaxMethod(getConfig('sys.tax.method'))
  } else {
    form.taxCalcMethod = normalizeTaxMethod(form.taxCalcMethod)
  }
}

/** 按表头「计税方式」把「计件单价」换算成 含税价 / 不含税价 */
function convertPrice(price, taxRate) {
  const p = Number(price || 0)
  const method = currentTaxMethod()
  const factor = 1 + (Number(taxRate) || 0) / 100
  if (method === '含税价') return { withTax: p, withoutTax: factor ? Number((p / factor).toFixed(6)) : p }
  if (method === '不计税') return { withTax: p, withoutTax: p }
  return { withoutTax: p, withTax: Number((p * factor).toFixed(6)) }
}

/** 单条工序行：按表头「计税方式」由「计件单价」换算两列 */
function calcOpPrice(row) {
  if (!row) return
  const conv = convertPrice(row.pieceRate, 0)
  row.priceWithTax = conv.withTax
  row.priceWithoutTax = conv.withoutTax
}

/** 表头「计税方式」切换 → 全部工序行按新口径重算 */
function onTaxCalcMethodChange() {
  (form.operations || []).forEach(r => calcOpPrice(r))
}


const onSortChange = (e) => { handleSortChange(e); fetchData() }
// 列定义（用于列设置抽屉+动态渲染）= 表格全部数据列；type 标记单元格渲染分支，纯文本列不加 type
const columns = [
  { prop: 'routingCode', label: '工艺路线编码', minWidth: 180, sortable: true, type: 'routingCode' },
  { prop: 'routingName', label: '工艺路线名称', minWidth: 180, sortable: true },
  { prop: 'routingVersion', label: '版本', width: 100, sortable: true },
  { prop: 'materialId', exportProp: 'materialNames', label: '物料', width: 240, sortable: true, type: 'material' },
  { prop: 'totalWorkHours', label: '总工时', width: 120, align: 'right', sortable: true },
  { prop: 'status', label: '状态', width: 100, sortable: true, type: 'status' },
  { prop: 'createdBy', label: '制单人', width: 100, sortable: true },
  { prop: 'createdAt', label: '制单时间', width: 160, sortable: true }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('RoutingList', columns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const materialOptions = ref([])
const workstationOptions = ref([])
const workCenterOptions = ref([])
const bomOptions = ref([])
/** 物料分类选项（带全路径）：适用分类多选 */
const categoryOptions = ref([])
/** 物料库弹窗（多选）：选中即累加进「适用物料」 */
const materialPickerVisible = ref(false)
/** 方向 A：当前选中的工序下标（左侧清单 → 右侧详情） */
const activeOpIndex = ref(0)
const activeOp = computed(() => form.operations[activeOpIndex.value])
/** 工序自定义字段：新增/编辑弹窗 */
const fieldDialogVisible = ref(false)
const fieldForm = reactive({
  index: null, fieldLabel: '', fieldType: 'text', isRequired: 0, optionsText: '', precision: 2
})
const fieldTypeOptions = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '下拉' },
  { value: 'date', label: '日期' },
  { value: 'switch', label: '勾选' }
]

/** 物料分类：扁平列表 → 带全路径的选项（重名分类也能分清） */
function buildCategoryOptions (list) {
  const map = {}
  ;(list || []).forEach(c => { if (c && c.categoryId != null) map[c.categoryId] = c })
  const fullName = (id) => {
    const parts = []
    let cur = map[id]
    let guard = 0
    while (cur && guard++ < 32) {
      parts.unshift(cur.categoryName || '')
      cur = cur.parentId != null ? map[cur.parentId] : null
    }
    return parts.filter(Boolean).join(' / ')
  }
  return Object.keys(map).map(k => ({ value: Number(k), label: fullName(Number(k)) }))
}

async function loadDropdownOptions() {
  try {
    const matList = await listAllMaterial()
    materialOptions.value = (Array.isArray(matList) ? matList : (matList?.records || [])).map(m => ({ value: m.materialId, label: `${m.materialName} (${m.materialCode})`, standardHours: Number(m.standardHours ?? 0) }))
  } catch (e) { console.warn('加载物料失败', e) }
  try {
    const cats = await listAllMaterialCategory()
    categoryOptions.value = buildCategoryOptions(Array.isArray(cats) ? cats : (cats?.records || []))
  } catch (e) { console.warn('加载物料分类失败', e) }
  try {
    const wsList = await listAllWorkstation()
    workstationOptions.value = (Array.isArray(wsList) ? wsList : (wsList?.records || [])).map(w => ({ value: w.workstationId, label: w.workstationName }))
  } catch (e) { console.warn('加载工位失败', e) }
  try {
    const wcList = await listAllWorkCenter()
    workCenterOptions.value = (Array.isArray(wcList) ? wcList : (wcList?.records || [])).map(w => ({ value: w.workCenterId, label: w.workCenterName }))
  } catch (e) { console.warn('加载工作中心失败', e) }
  try {
    // BOM 选项：接口调用已下沉到 api 封装（api/basedata/bom.js），页面内不再直接拼 URL
    const boms = await listAllBom()
    bomOptions.value = (Array.isArray(boms) ? boms : (boms?.records || []))
  } catch (e) { console.warn('加载BOM失败', e) }
}

// 物料名称映射：多物料优先按绑定清单展示（超出 2 个显示「等 N 个」）；老数据回落到单个物料
const materialNameOf = (row) => {
  if (!row) return ''
  if (Array.isArray(row.materials) && row.materials.length) {
    const names = row.materials
      .map(m => m && (m.name || m.code) ? `${m.name || ''}${m.code ? ' (' + m.code + ')' : ''}` : '')
      .filter(Boolean)
    if (names.length > 2) return names.slice(0, 2).join('、') + ` 等 ${names.length} 个`
    return names.join('、')
  }
  if (row.materialName) return row.materialName
  if (row.materialId === null || row.materialId === undefined || row.materialId === '') return ''
  const hit = materialOptions.value.find(o => o.value === row.materialId)
  return hit ? hit.label : ''
}

/**
 * 【工时体系 2026-10-07】工序「单件加工工时」默认带出。
 * 来源：当前工艺路线所选物料的「单件标准工时」（standardHours）。
 * 规则：工序行「单件加工工时」为空或 0 时才带出，已填过的不覆盖；取不到物料标准工时就保持 0，不报错、不阻断保存。
 */
function currentMaterialStandardHours() {
  const mid = form.materialId
  if (mid == null || mid === '') return 0
  const hit = materialOptions.value.find(o => o.value === mid)
  const h = hit ? Number(hit.standardHours) : 0
  return Number.isFinite(h) && h > 0 ? h : 0
}

/** 单条工序行：为空或 0 时用物料标准工时填充（不覆盖已填值） */
function fillRowUnitHours(row) {
  if (!row) return
  const std = currentMaterialStandardHours()
  if (std <= 0) return
  if (row.unitHours == null || Number(row.unitHours) === 0) row.unitHours = std
}

/** 全部工序行：为空或 0 的都用物料标准工时填充（选物料 / BOM 反填物料时触发） */
function fillAllUnitHours() {
  if (!Array.isArray(form.operations)) return
  form.operations.forEach(fillRowUnitHours)
}

// ===== 第 4 期联动：物料 ↔ BOM =====
// 判定依据：BOM 头上有 materialId（成品物料）与 isPrimary（主 BOM），据此做"选中即带出 + 下拉过滤"。
/** BOM 下拉选项：选了物料就只列该物料的 BOM；没选物料则列全部 */
const filteredBomOptions = computed(() => {
  const mid = form.materialId
  if (mid == null || mid === '') return bomOptions.value
  return bomOptions.value.filter(b => b.materialId === mid)
})

/** 选物料 → 自动带出该物料的主 BOM（无主 BOM 取第一条）；已选 BOM 若本就属于该物料则不覆盖 */
function onRoutingMaterialChange(mid) {
  if (mid == null || mid === '') return
  // 【工时体系】选物料 → 把该物料的「单件标准工时」带出到各工序行的「单件加工工时」（仅填未填/为 0 的行）
  fillAllUnitHours()
  const list = bomOptions.value.filter(b => b.materialId === mid)
  if (!list.length) return
  const cur = bomOptions.value.find(b => b.bomId === form.bomId)
  if (cur && cur.materialId === mid) return   // 防止"选BOM反填物料"时把用户选的 BOM 改回主 BOM
  const primary = list.find(b => b.isPrimary === 1 || b.isPrimary === true) || list[0]
  form.bomId = primary.bomId
}

/** 选 BOM → 反填该 BOM 的成品物料（联动只是默认带出，不锁死，用户可手工改） */
function onRoutingBomChange(bomId) {
  if (bomId == null || bomId === '') return
  const hit = bomOptions.value.find(b => b.bomId === bomId)
  if (hit && hit.materialId != null) {
    form.materialId = hit.materialId
    // BOM 反填的成品物料若不在适用物料里，补进去（保证「适用物料」与 BOM 不脱节）
    if (!form.materials.some(m => m.id === hit.materialId)) {
      const opt = materialOptions.value.find(o => o.value === hit.materialId)
      form.materials.push({
        id: hit.materialId,
        name: opt ? opt.label.replace(/\s*\([^)]*\)\s*$/, '') : (hit.materialName || ''),
        code: hit.materialCode || ''
      })
    }
  }
  // 【工时体系】BOM 反填物料后，同样按新物料带出各工序行的「单件加工工时」
  fillAllUnitHours()
}

/** 物料库弹窗确认：合并选中物料进「适用物料」，已选的自动去重 */
function onMaterialPicked ({ rows }) {
  const picked = (rows || []).filter(m => m && m.materialId != null)
  picked.forEach(m => {
    if (!form.materials.some(x => x.id === m.materialId)) {
      form.materials.push({ id: m.materialId, name: m.materialName || '', code: m.materialCode || '' })
    }
  })
  form.materialIds = form.materials.map(m => m.id)
  // 第一个物料作为主物料：继续驱动 BOM 带出与单件标准工时
  if (form.materials.length) {
    form.materialId = form.materials[0].id
    onRoutingMaterialChange(form.materialId)
  }
}

/** 移除适用物料：主物料被移除时，自动改用剩下的第一个；BOM 不属于剩余物料时一并清空 */
function removeMaterial (id) {
  form.materials = form.materials.filter(m => m.id !== id)
  form.materialIds = form.materials.map(m => m.id)
  const next = form.materials.length ? form.materials[0].id : null
  if (form.materialId === id) {
    form.materialId = next
    if (next != null) onRoutingMaterialChange(next)
  }
  if (form.bomId != null) {
    const bom = bomOptions.value.find(b => b.bomId === form.bomId)
    if (bom && bom.materialId === id && !form.materials.some(m => m.id === bom.materialId)) {
      form.bomId = null
    }
  }
}

/** 适用分类变更：同步展示用分类明细（带全路径） */
function onCategoryChange (ids) {
  const picked = []
  ;(ids || []).forEach(id => {
    const hit = categoryOptions.value.find(c => c.value === id)
    if (hit) picked.push({ id, name: hit.label, fullName: hit.label })
  })
  form.categories = picked
}

function removeCategory (id) {
  form.categoryIds = (form.categoryIds || []).filter(x => x !== id)
  onCategoryChange(form.categoryIds)
}

const loading = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const printVisible = ref(false)
const printBillId = ref(null)
// 统一入参名（docs/knowledge-base.md 6.1）：keyword / status / startDate / endDate
// 注：startDate/endDate 对应「生效日期」effective_date（本页无「单据日期」）
const search = reactive({ keyword: '', status: '', startDate: '', endDate: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })
// 通用搜索栏「启停状态」下拉：工艺路线已从"单据"降为"主档"（2026-09-23），只有 启用/停用
const statusOptions = [
  { label: '启用', value: '启用' },
  { label: '停用', value: '停用' }
]
// 工序类型取值：由后端 dashboard 下发（唯一事实来源，避免前后端各写一份）
const operationTypes = ref(['状态类型', '数量类型', '入库类型', '金额类型'])
// 统计卡片高亮（点卡片筛选列表时由 onStatClick 控制）
const activeStatKey = ref('')

// 顶部统计卡片：数据来自后端 /api/production/routings/dashboard（基础资料类，与列表一致不做数据权限过滤）
// 带 filter 的卡片可点击 → 直接筛选下方列表（再点同一张卡 = 取消）
const stats = ref([
  { key: 'total', label: '工艺路线总数', value: 0, type: 'primary', icon: 'Document', filter: { status: '' } },
  { key: 'enabled', label: '启用中', value: 0, type: 'success', icon: 'CircleCheck', filter: { status: '启用' } },
  { key: 'disabled', label: '已停用', value: 0, type: 'danger', icon: 'EditPen', filter: { status: '停用' } }
])

async function loadStats() {
  try {
    const d = (await getRoutingDashboard()) || {}
    if (Array.isArray(d.operationTypes) && d.operationTypes.length) operationTypes.value = d.operationTypes
    stats.value = [
      { key: 'total', label: '工艺路线总数', value: d.totalCount ?? 0, type: 'primary', icon: 'Document', filter: { status: '' } },
      { key: 'enabled', label: '启用中', value: d.enabledCount ?? 0, type: 'success', icon: 'CircleCheck', filter: { status: '启用' } },
      { key: 'disabled', label: '已停用', value: d.disabledCount ?? 0, type: 'danger', icon: 'EditPen', filter: { status: '停用' } }
    ]
  } catch (e) {
    // 静默降级：取数失败时卡片保持原值，不弹诊断卡、不阻断列表
  }
}

// 卡片点击 → 应用该卡筛选条件（状态/日期先清空再套用，避免上一张卡残留）；再点同一张卡 = 取消
function onStatClick({ key, filter }) {
  if (activeStatKey.value === key) {
    activeStatKey.value = ''
    Object.assign(search, { status: '', startDate: '', endDate: '' })
  } else {
    activeStatKey.value = key
    Object.assign(search, { status: '', startDate: '', endDate: '' }, filter || {})
  }
  pagination.current = 1
  fetchData()
}

const headerCellStyle = {
  background: 'rgba(99, 102, 241, 0.04)',
  color: '#1f2937',
  fontWeight: 600,
  borderBottom: '2px solid #e0e7ff'
}

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const form = reactive({
  routingId: null, routingCode: '', routingName: '', routingVersion: '',
  materialId: null, bomId: null, isPrimary: 1, status: '启用',
  // 【2026-11-26】一个工艺路线可绑定多个物料 / 多个分类；materialId 始终等于第一个物料（兼容老逻辑）
  materialIds: [], materials: [], categoryIds: [], categories: [],
  effectiveDate: null, expiryDate: null,
  autoNotifyNext: 0, enforceSequence: 0, autoInputFromPrev: 1,
  assignScope: '全部人', assignRoleIds: '',
  taxCalcMethod: '不含税价',
  totalWorkHours: 0, remark: '', operations: []
})
const rules = {
  routingName: [{ required: true, message: tt('请输入工艺路线名称'), trigger: 'blur' }]
}
/** 【第 4 版】可改负责人的角色候选（sys_role：roleCode + roleName） */
const roleOptions = ref([])
/** 【第 4 版】加载角色候选（取不到不影响其它功能，仅下拉为空） */
async function loadRoles () {
  try {
    const list = await listAllRole()
    roleOptions.value = (Array.isArray(list) ? list : (list?.records || []))
      .map(r => ({ value: r.roleCode, label: r.roleName || r.roleCode }))
      .filter(o => o.value)
  } catch (e) { roleOptions.value = [] }
}

function statusTagType(s) {
  return { '启用': 'success', '停用': 'danger' }[s] || 'info'
}

function statusDotClass(s) {
  return {
    '启用': 'dot-success',
    '停用': 'dot-danger'
  }[s] || 'dot-info'
}

// 通用搜索栏（状态/生效日期）变更：先把值写回本页 search
// 注：BillSearchBar 变更后会紧接着抛 search 事件（壳已转发 → handleSearch），这里不重复发起查询
function onFilterChange(patch) {
  Object.assign(search, patch || {})
}

/**
 * 列表与导出**共用**的查询条件（2026-09-24 全站口径）：
 * 导出必须与列表用同一套条件，抽成一个函数避免两处各写一份导致口径漂移
 * （用户要求"页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部"）。
 */
function buildQueryParams() {
  return {
    keyword: search.keyword, status: search.status,
    startDate: search.startDate, endDate: search.endDate,
    ...getSortParams()
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listRouting({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function handleSearch() { pagination.current = 1; fetchData() }

function handleReset() {
  // 重置 = 回到未筛选状态：关键词 + 通用搜索栏全部维度一并清空
  Object.assign(search, { keyword: '', status: '', startDate: '', endDate: '' })
  activeStatKey.value = ''   // 同时取消统计卡片的高亮
  pagination.current = 1
  fetchData()
}

function handleAdd() {
  Object.assign(form, {
    routingId: null, routingCode: '', routingName: '', routingVersion: '',
    materialId: null, bomId: null, isPrimary: 1, status: '启用',
    materialIds: [], materials: [], categoryIds: [], categories: [],
    effectiveDate: null, expiryDate: null,
    autoNotifyNext: 0, enforceSequence: 0, autoInputFromPrev: 1,
    assignScope: '全部人', assignRoleIds: '',
    taxCalcMethod: '不含税价',
    totalWorkHours: 0, remark: '', operations: []
  })
  activeOpIndex.value = 0
  // 新建：表头「计税方式」取设置中心「默认计税方式」
  initTaxSettings(true)
  dialogTitle.value = '新增工艺路线'
  dialogVisible.value = true
}

async function handleEdit(row) {
  const data = await getRouting(row.routingId)
  Object.assign(form, data)
  if (!form.operations) form.operations = []
  // 【2026-11-26】多物料/多分类回显；老数据只有单物料时补一条展示
  if (!Array.isArray(form.materials)) form.materials = []
  if (!Array.isArray(form.materialIds)) form.materialIds = []
  if (!form.materials.length && form.materialId != null) {
    const opt = materialOptions.value.find(o => o.value === form.materialId)
    form.materials = [{
      id: form.materialId,
      name: opt ? opt.label.replace(/\s*\([^)]*\)\s*$/, '') : (form.materialName || ''),
      code: form.materialCode || ''
    }]
    form.materialIds = [form.materialId]
  }
  if (!Array.isArray(form.categoryIds)) form.categoryIds = []
  if (!Array.isArray(form.categories)) form.categories = []
  // 分类下拉还没加载完时保留后端回传的分类标签，不能被空选项表覆盖
  if (form.categoryIds.length && categoryOptions.value.length) onCategoryChange(form.categoryIds)
  // 工序稳定标识 + 每道工序自己的字段定义：老数据补默认值
  form.operations.forEach(op => {
    if (!op.operationKey) op.operationKey = genOperationKey()
    if (!Array.isArray(op.fieldDefs)) op.fieldDefs = []
  })
  activeOpIndex.value = 0
  // 工序通知与顺序控制两个开关：老数据未返回时按「关」处理（与升级前行为一致）
  if (form.autoNotifyNext == null) form.autoNotifyNext = 0
  if (form.enforceSequence == null) form.enforceSequence = 0
  // 投入自动带出上道产出：老数据未返回时按「开」兜底（后端数据库默认即 1）
  if (form.autoInputFromPrev == null) form.autoInputFromPrev = 1
  // 【第 4 版】谁可以改负责人：老数据未返回时按「全部人」兜底（= 升级前行为）；角色清单归一为空串
  if (!form.assignScope) form.assignScope = '全部人'
  if (form.assignRoleIds == null) form.assignRoleIds = ''
  form.operations.forEach(op => {
    if (op.isQcRequired == null) op.isQcRequired = 0
    if (op.isCritical == null) op.isCritical = 0
    // 【2026-10-09】工序级「谁可以改负责人」：老数据未返回时按「全部人」兜底；角色字符串 → 数组（表格多选绑定）
    if (!op.assignScope) op.assignScope = '全部人'
    op.assignRoleList = op.assignRoleIds ? String(op.assignRoleIds).split(',').map(s => s.trim()).filter(Boolean) : []
    if (!op.operationType) op.operationType = '状态类型'
    if (op.pieceRate == null) op.pieceRate = 0
    if (op.planAmount == null) op.planAmount = null
    // 【工时体系】老路线工序未返回「单件加工工时」时兜底 0（保持老行为：以后端 0 为准）
    if (op.unitHours == null) op.unitHours = 0
    // 【2026-10-25】老路线未配置「允许扫码认领」→ 按「是」显示（与后端空值默认同口径）
    if (op.allowScanClaim == null || op.allowScanClaim === '') op.allowScanClaim = '是'
  })
  // 计税口径（2026-10-09）：老路线按设置中心默认/总开关归一，再按口径回算工序含税价/不含税价
  await initTaxSettings(false)
  form.operations.forEach(op => calcOpPrice(op))
  syncParentIndexFromLineNo()
  // 工艺路线是「主档」：启用/停用都随时可编辑，不再有"非草稿只能查看"
  dialogTitle.value = '编辑工艺路线'
  dialogVisible.value = true
}

/** 工序稳定标识：工艺路线保存是全删全插，字段定义必须绑它才能跨保存保留 */
function genOperationKey () {
  return 'ro_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
}

function addOperation() {
  const row = {
    operationKey: genOperationKey(),
    operationCode: '', operationName: '', operationType: '状态类型',
    workstationType: '',
    workstationId: null, workCenterId: null,
    setupHours: 0, processHours: 0, unitHours: 0, waitHours: 0, moveHours: 0, totalHours: 0,
    batchSize: 1, workersRequired: 1, pieceRate: 0, planAmount: null,
    priceWithTax: 0, priceWithoutTax: 0,
    parentIndex: null, isQcRequired: 0, isCritical: 0, remark: '',
    assignScope: '全部人', assignRoleIds: '', assignRoleList: [],
    allowScanClaim: '是',
    fieldDefs: []
  }
  calcOpPrice(row)
  form.operations.push(row)
  activeOpIndex.value = form.operations.length - 1
  calcTotal()
}

/** 是否「金额类型」工序（唯一可填「计划金额」的类型；与后端 WorkOperationStatus.TYPE_AMOUNT 同口径） */
function isAmountType (row) {
  return row && row.operationType === '金额类型'
}

/** 切走「金额类型」时把计划金额清空（避免留下与类型不符的脏值，后端保存时也会再清一次） */
function onOperationTypeChange (row) {
  if (!isAmountType(row)) row.planAmount = null
}

/**
 * 【第 13 期】父子工序的行号 ↔ 行下标互转。
 *
 * 后端保存时按提交顺序重排行号（第 i 行 = (i+1)*10），所以父引用必须落在"保存后的行号空间"里。
 * 页面上用「行下标」表达父引用（用户增删/调序后不会指错），保存前再换算成行号。
 */
/** 加载详情后：把后端回传的 parent_line_no 换算成行下标 */
function syncParentIndexFromLineNo () {
  const lineToIndex = {}
  form.operations.forEach((op, i) => {
    if (op.lineNo != null) lineToIndex[op.lineNo] = i
  })
  form.operations.forEach(op => {
    const idx = op.parentLineNo == null ? undefined : lineToIndex[op.parentLineNo]
    op.parentIndex = idx === undefined ? null : idx
  })
}

/** 保存前：把行下标换算成"保存后行号"（第 i 行 = (i+1)*10） */
function applyParentLineNoForSave () {
  form.operations.forEach((op, i) => {
    op.parentLineNo = (op.parentIndex == null || op.parentIndex === '') ? null : (Number(op.parentIndex) + 1) * 10
  })
}

/** 【2026-10-09】工序级「谁可以改负责人」：切走「指定角色」时清空已选角色 */
function onAssignScopeChange (row) {
  if (row.assignScope !== '指定角色') row.assignRoleList = []
}
/** 该工序已选的可改负责人角色数（「选角色」按钮上的角标） */
function roleCountOf (row) { return (row.assignRoleList || []).length }
/** 保存前：把每行的角色数组写回逗号分隔字符串（后端字段 assignRoleIds）；非「指定角色」一律清空 */
function applyAssignRoleForSave () {
  form.operations.forEach(op => {
    op.assignRoleIds = (op.assignScope === '指定角色') ? (op.assignRoleList || []).join(',') : ''
  })
}

// ==================== 【2026-11-26】每道工序自己的自定义字段 ====================

function fieldTypeLabel (type) {
  const hit = fieldTypeOptions.find(t => t.value === type)
  return hit ? hit.label : (type || '')
}

/** 配置摘要：下拉=几个选项；数字=几位小数；其余无额外设置 */
function fieldConfigSummary (f) {
  let opt = null
  try { opt = f.fieldOptions ? JSON.parse(f.fieldOptions) : null } catch (e) { opt = null }
  if (f.fieldType === 'select') return Array.isArray(opt) ? `${opt.length} 个选项` : '未配置选项'
  if (f.fieldType === 'number') return `${(opt && opt.precision != null) ? opt.precision : 2} 位小数`
  return '无额外设置'
}

/** 打开字段弹窗：index 为空 = 新增；有值 = 编辑 */
function openFieldDialog (row, index) {
  const editing = row && typeof index === 'number'
  fieldForm.index = editing ? index : null
  fieldForm.fieldLabel = editing ? (row.fieldLabel || '') : ''
  fieldForm.fieldType = editing ? (row.fieldType || 'text') : 'text'
  fieldForm.isRequired = editing ? (row.isRequired ? 1 : 0) : 0
  fieldForm.optionsText = ''
  fieldForm.precision = 2
  if (editing && row.fieldOptions) {
    try {
      const opt = JSON.parse(row.fieldOptions)
      if (Array.isArray(opt)) {
        fieldForm.optionsText = opt.map(o => o.label ?? o.value ?? o).join('\n')
      } else if (opt && opt.precision != null) {
        fieldForm.precision = Number(opt.precision)
      }
    } catch (e) { /* 历史脏值按空处理 */ }
  }
  fieldDialogVisible.value = true
}

/** 保存字段到当前工序（只改前端，随工艺路线保存一起落库） */
function saveFieldDef () {
  if (!activeOp.value) return
  const label = (fieldForm.fieldLabel || '').trim()
  if (!label) { ElMessage.warning('请先填写字段名称'); return }
  let fieldOptions = null
  if (fieldForm.fieldType === 'select') {
    const options = (fieldForm.optionsText || '').split('\n').map(s => s.trim()).filter(Boolean)
    if (!options.length) { ElMessage.warning('下拉字段至少需要一个选项'); return }
    fieldOptions = JSON.stringify(options.map(o => ({ label: o, value: o })))
  } else if (fieldForm.fieldType === 'number') {
    fieldOptions = JSON.stringify({ precision: Number(fieldForm.precision) || 0 })
  }
  const def = {
    fieldLabel: label,
    fieldType: fieldForm.fieldType,
    fieldOptions,
    isRequired: fieldForm.isRequired ? 1 : 0,
    sortNo: fieldForm.index == null ? (activeOp.value.fieldDefs || []).length : fieldForm.index
  }
  if (!Array.isArray(activeOp.value.fieldDefs)) activeOp.value.fieldDefs = []
  if (fieldForm.index == null) {
    activeOp.value.fieldDefs.push(def)
  } else {
    activeOp.value.fieldDefs.splice(fieldForm.index, 1, def)
  }
  fieldDialogVisible.value = false
  ElMessage.success('字段已保存，随工艺路线保存一起生效')
}

/** 删除字段（只改前端，随工艺路线保存一起落库） */
async function deleteFieldDef (index) {
  if (!activeOp.value) return
  try {
    await ElMessageBox.confirm('确认删除这个字段？保存工艺路线后生效。', '提示', { type: 'warning' })
  } catch (e) { return }
  activeOp.value.fieldDefs.splice(index, 1)
}

/**
 * 【即输即存】工位下拉里直接输入新名称回车 → 立即写主档并选中该条（下次可直接选到）。
 * 下拉已配 allow-create，用户输入的是"名称字符串"而非 id，这里负责落库并回填 id。
 */
async function onWorkstationChange(row, val) {
  if (val == null || val === '') return
  // 【工时体系】选工位 → 该工序行「单件加工工时」为空或 0 时，用路线物料的标准工时带出
  fillRowUnitHours(row)
  if (typeof val === 'number' || workstationOptions.value.some(o => o.value === val)) return
  const text = String(val).trim()
  if (!text) return
  try {
    const ws = await quickSaveWorkstation({ name: text })
    const id = ws?.workstationId
    if (!id) { row.workstationId = null; return }
    if (!workstationOptions.value.some(o => o.value === id)) {
      workstationOptions.value.push({ value: id, label: text })
    }
    row.workstationId = id
    ElMessage.success(`${tt("已新增工位：")}${text}`)
  } catch (e) {
    row.workstationId = null
    ElMessage.error(e?.message || tt('工位保存失败'))
  }
}

/** 【即输即存】工作中心下拉里直接输入新名称回车 → 立即写主档并选中该条 */
async function onWorkCenterChange(row, val) {
  if (val == null || val === '') { row.workCenterName = ''; return }
  // 选的是已有档案：直接把名称带到冗余列
  const hit = workCenterOptions.value.find(o => o.value === val)
  if (hit) { row.workCenterName = hit.label; return }
  if (typeof val === 'number') return
  const text = String(val).trim()
  if (!text) return
  try {
    const wc = await quickSaveWorkCenter({ name: text })
    const id = wc?.workCenterId
    if (!id) { row.workCenterId = null; return }
    if (!workCenterOptions.value.some(o => o.value === id)) {
      workCenterOptions.value.push({ value: id, label: text })
    }
    row.workCenterId = id
    // 工作中心名称冗余列（列表/看板展示用），随选择一并带上
    row.workCenterName = text
    ElMessage.success(`${tt("已新增工作中心：")}${text}`)
  } catch (e) {
    row.workCenterId = null
    ElMessage.error(e?.message || tt('工作中心保存失败'))
  }
}

function calcRow(row) {
  row.totalHours = (
    Number(row.setupHours || 0) +
    Number(row.processHours || 0) +
    Number(row.waitHours || 0) +
    Number(row.moveHours || 0)
  ).toFixed(4)
  calcTotal()
}

function calcTotal() {
  form.totalWorkHours = form.operations.reduce(
    (sum, r) => sum + Number(r.totalHours || 0), 0
  ).toFixed(4)
}

async function handleSave() {
  await formRef.value.validate()
  // 父子工序：把页面上的「行下标」换算成后端要求的分工行号（(i+1)*10）后再提交
  applyParentLineNoForSave()
  // 工序级「谁可以改负责人」：角色多选数组 → 逗号分隔字符串
  applyAssignRoleForSave()
  // 计税口径：按表头「计税方式」把工序「含税价 / 不含税价」两列刷成最新
  form.operations.forEach(op => calcOpPrice(op))
  // 【2026-11-26】多物料/多分类 + 工序稳定标识 + 每道工序自己的字段
  form.materialIds = (form.materials || []).map(m => m.id)
  form.operations.forEach(op => {
    if (!op.operationKey) op.operationKey = genOperationKey()
    if (!Array.isArray(op.fieldDefs)) op.fieldDefs = []
  })
  const payload = { ...form }
  if (form.routingId) {
    await updateRouting(form.routingId, payload)
  } else {
    await saveRouting(payload)
  }
  ElMessage.success(tt('保存成功'))
  dialogVisible.value = false
  fetchData()
}

function resetForm() {
  formRef.value && formRef.value.resetFields()
}

async function handleExport() {
  try {
    // 页面上看到什么列，导出就带什么列；导出行 = 当前筛选条件下的全部
    await exportListAsSeen({
      entityName: 'routing',
      columns: visibleSortedColumns.value,
      fetchPage: listRouting,
      params: buildQueryParams(),
      idField: 'routingId'
    })
    ElMessage.success(tt('导出成功'))
  } catch (e) {
    // 失败原因由 exportUtil 统一提示
  }
}

onMounted(() => {
  loadDropdownOptions()
  loadRoles()
  loadStats()
  fetchData()
})
useAutoRefresh(() => { fetchData(); loadStats() })
</script>

<style scoped>
.routing-list-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #6b7280;
    font-size: 13px;
    cursor: pointer;
    padding: 6px 12px;
    border-radius: 6px;
    background: #fff;
    border: 1px solid #e5e7eb;
    transition: all .2s;
    &:hover {
      color: #6366f1;
      border-color: #c7d2fe;
    }
  }

  .title-group { display: flex; flex-direction: column; gap: 2px; }

  .page-title {
    font-size: 20px;
    font-weight: 700;
    color: #111827;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .title-badge {
    font-size: 10px;
    font-weight: 600;
    color: #6366f1;
    background: linear-gradient(135deg, #e0e7ff, #f0f4ff);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 1px;
  }

  .page-sub { font-size: 12px; color: #9ca3af; }

  .header-actions { display: flex; gap: 8px; }
}

.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  &:hover {
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
    transform: translateY(-1px);
  }
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .search-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .search-input {
    width: 280px;
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 0 0 1px #e5e7eb inset;
      &:hover { box-shadow: 0 0 0 1px #c7d2fe inset; }
      &.is-focus { box-shadow: 0 0 0 1px #6366f1 inset; }
    }
  }
}

/* 状态页签样式已删除：状态改为通用搜索栏的「单据状态」下拉（filters 声明 'status'，壳自动隐藏页签） */

.tech-table {
  :deep(.el-table__header th) {
    background: rgba(99, 102, 241, 0.04) !important;
  }

  :deep(.el-table__row:hover > td) {
    background: rgba(99, 102, 241, 0.03) !important;
  }

  :deep(.el-table td) {
    border-bottom: 1px solid #f3f4f6;
  }
}

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .order-no-text {
    color: #6366f1;
    font-weight: 500;
    cursor: pointer;
    &:hover { color: #8b5cf6; }
  }

  .link-icon {
    color: #c7d2fe;
    font-size: 12px;
    opacity: 0;
    transition: opacity .2s;
  }
  &:hover .link-icon { opacity: 1; }
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.dot-success { background: #10b981; box-shadow: 0 0 6px rgba(16, 185, 129, 0.5); }
    &.dot-warning { background: #f59e0b; box-shadow: 0 0 6px rgba(245, 158, 11, 0.5); }
    &.dot-info { background: #3b82f6; box-shadow: 0 0 6px rgba(59, 130, 246, 0.5); }
    &.dot-danger { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.5); }
  }

  .status-tag {
    border-radius: 4px;
    font-weight: 500;
  }
}

.action-cell {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;

  .pagination-info {
    font-size: 13px;
    color: #6b7280;
  }
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

/* 工序通知与顺序控制：开关旁的灰字说明 */
.switch-hint { font-size: 12px; color: #9ca3af; margin-left: 10px; }

/* 【2026-10-09】工序控制：三个开关压成一行（长说明收进「?」悬停提示） */
.switch-row { display: flex; flex-wrap: wrap; align-items: center; gap: 24px; padding: 2px 0 6px; }
.switch-item { display: inline-flex; align-items: center; gap: 6px; }
.switch-label { font-size: 13px; color: #4b5563; }
.switch-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid #c7cbd4;
  color: #909399;
  font-size: 10px;
  line-height: 1;
  cursor: help;
}
.assign-tip { font-size: 12px; color: #9ca3af; padding: 0 0 4px; }

/* ===== 方向 A：主从工作台（2026-11-26） ===== */
:deep(.el-dialog__body) { max-height: calc(100vh - 190px); overflow-y: auto; }
.rd-card {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.rd-card.inner { padding: 10px 12px; margin-bottom: 10px; }
.rd-card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 10px; }
.rd-card-title { font-size: 13px; font-weight: 600; color: #101828; display: flex; align-items: center; gap: 8px; }
.rd-card-title::before { content: ""; width: 3px; height: 13px; border-radius: 2px; background: #1677ff; }
.rd-card-hint { font-size: 11px; color: #667085; margin-top: 2px; line-height: 16px; }
.rd-status { font-size: 11px; padding: 2px 10px; border-radius: 999px; background: #f2f4f7; color: #475467; white-space: nowrap; }
.rd-status.on { background: #e7f8f1; color: #0f9f6e; }
.rd-scope { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px; margin: 4px 0 10px; }
.rd-field { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.rd-label { font-size: 12px; color: #667085; }
.rd-tags { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; min-height: 30px; }
.rd-code { font-size: 10px; color: #6b8fd6; margin-left: 4px; }
.rd-tags-empty { font-size: 11px; color: #98a2b3; }
.rd-master { display: grid; grid-template-columns: 270px minmax(0, 1fr); gap: 12px; align-items: start; }
.rd-list { background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; padding: 10px; position: sticky; top: 0; }
.rd-list-head { display: flex; justify-content: space-between; font-size: 12px; color: #667085; padding: 0 4px 8px; }
.rd-list-head b { color: #101828; }
.rd-op-item { display: grid; grid-template-columns: 26px minmax(0, 1fr) auto; gap: 8px; align-items: center; padding: 8px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; }
.rd-op-item:hover { background: #f8fafc; }
.rd-op-item.on { background: #e8f1ff; border-color: #c7dcff; }
.rd-seq { font-size: 11px; color: #98a2b3; text-align: center; }
.rd-op-main { min-width: 0; }
.rd-op-name { display: block; font-size: 12px; font-weight: 600; color: #101828; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rd-op-sub { display: block; font-size: 11px; color: #98a2b3; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rd-op-side { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.rd-op-cf { font-size: 10px; color: #98a2b3; }
.rd-detail { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 10px; padding: 12px; min-width: 0; }
.rd-detail-head { display: flex; justify-content: space-between; align-items: flex-end; padding: 0 2px 10px; border-bottom: 1px solid #e4e7ec; margin-bottom: 10px; }
.rd-eyebrow { font-size: 11px; letter-spacing: .08em; color: #98a2b3; }
.rd-detail-head h3 { font-size: 16px; margin: 2px 0; color: #101828; }
.rd-meta { font-size: 11px; color: #667085; }
.rd-value { display: inline-flex; align-items: center; height: 24px; font-size: 12px; color: #344054; font-family: var(--el-font-family); }
.rd-empty { padding: 18px 10px; text-align: center; font-size: 12px; color: #98a2b3; border: 1px dashed #d0d5dd; border-radius: 8px; background: #fff; }
.rd-empty-detail { display: flex; align-items: center; justify-content: center; min-height: 240px; }
.rd-save-bar {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  padding: 10px 14px; background: #fff; border: 1px solid #e4e7ec; border-radius: 10px;
  margin-top: 12px; position: sticky; bottom: 0; z-index: 5;
  box-shadow: 0 -6px 20px rgba(16, 24, 40, .06);
}
.rd-save-bar b { font-size: 13px; }
.rd-detail :deep(.el-form-item) { margin-bottom: 10px; }

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .rd-master { grid-template-columns: 1fr; }
  .rd-list { position: static; }
  .rd-scope { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .routing-list-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .search-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>
