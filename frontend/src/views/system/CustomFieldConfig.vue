<template>
  <div class="erp-page custom-field-config">
    <el-card shadow="never" class="config-card">
      <template #header>
        <div class="card-header">
          <span class="title">{{ $tt("自定义字段配置") }}</span>
          <div>
            <el-button v-permission="perm('refreshCache')" type="success" @click="onRefresh" :loading="refreshing">{{ $tt("刷新缓存") }}</el-button>
          </div>
        </div>
      </template>

      <div class="config-body">
        <!-- 左侧：实体列表 -->
        <div class="group-nav">
          <el-input
            v-model="entityKeyword"
            class="entity-search"
            :placeholder="$tt('搜索业务对象（如：销售订单/物料）')"
            clearable
          />
          <el-scrollbar class="group-scroll">
            <el-collapse v-model="activeGroups" class="group-collapse">
              <el-collapse-item v-for="g in groupedEntities" :key="g.group" :name="g.group">
                <template #title>
                  <span class="group-title">{{ g.group }}</span>
                  <span class="group-count">{{ g.items.length }}</span>
                </template>
                <div
                  v-for="e in g.items"
                  :key="e.key"
                  class="entity-item"
                  :class="{ active: activeEntity === e.key }"
                  @click="onSelectEntity(e.key)"
                >
                  <el-icon class="entity-icon"><component :is="iconMap[e.icon]" /></el-icon>
                  <span class="entity-label">{{ $tf(e.label) }}</span>
                </div>
              </el-collapse-item>
            </el-collapse>
            <div v-if="groupedEntities.length === 0" class="entity-empty">{{ $tt("没有匹配的业务对象") }}</div>
          </el-scrollbar>
        </div>

        <!-- 右侧：字段定义表格 -->
        <div class="config-form">
          <div class="form-header">
            <span class="form-title">{{ currentEntityLabel }} - 自定义字段</span>
            <span class="form-tip">共 {{ fieldList.length }} 个字段</span>
            <el-button v-permission="perm('addRow')" type="primary" style="margin-left:auto;" @click="handleAdd">{{ $tt("+ 新增字段") }}</el-button>
          </div>

          <el-alert type="info" :closable="false" style="margin:12px 0;">
            自定义字段会自动出现在「{{ currentEntityLabel }}」的新增/编辑表单中。
            「可搜索」字段会同步到业务表 ext_data 列，支持 WHERE 过滤。
            「公式」字段在表单输入时前端实时计算，保存时后端也会重新计算确保正确。
          </el-alert>

          <el-table :data="fieldList" v-loading="loading" border stripe>
            <el-table-column :label="$tf('字段名称')" prop="fieldLabel" min-width="150" sortable>
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="handleEdit(row)">{{ row.fieldLabel }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="fieldKey" :label="$tf('字段标识')" width="140" sortable>
              <template #default="{ row }">
                <code style="color:#409EFF; font-size:12px;">{{ row.fieldKey }}</code>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('类型')" width="110">
              <template #default="{ row }">
                <el-tag :type="typeTagType(row.fieldType)" size="small">{{ typeLabel(row.fieldType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('分区')" width="120" prop="sectionName" sortable />
            <el-table-column :label="$tf('排序')" width="80" prop="sortOrder" sortable />
            <el-table-column :label="$tf('属性')" width="240">
              <template #default="{ row }">
                <el-tag v-if="row.isRequired===1" type="danger" size="small" style="margin-right:4px;">{{ $tt("必填") }}</el-tag>
                <el-tag v-if="row.isListShow===1 && !isItemEntity" type="success" size="small" style="margin-right:4px;">{{ $tt("列表显示") }}</el-tag>
                <el-tag v-if="row.isSearch===1" type="warning" size="small" style="margin-right:4px;">{{ $tt("可搜索") }}</el-tag>
                <el-tag v-if="dropDisplayEnabled && row.isDropShow===1" type="primary" size="small" style="margin-right:4px;">{{ $tt("下拉显示") }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('启用')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isEnabled===1?'success':'info'" size="small">{{ row.isEnabled===1?'启用':'禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('操作')" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="perm('edit')" link type="primary" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
                <el-button v-permission="perm('delete')" link type="danger" @click="handleDelete(row)">{{ $tt("删除") }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-card>

    <!-- 新增/编辑字段弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editForm.fieldId ? '编辑自定义字段' : '新增自定义字段'" width="760px" :close-on-click-modal="false" class="custom-field-dialog">
      <el-form :model="editForm" :rules="rules" ref="formRef" label-width="110px" label-position="right">

        <!-- 基本信息 -->
        <div class="section-block">
          <div class="section-title">{{ $tt("基本信息") }}</div>
          <div class="section-content">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$tf('字段名称')" prop="fieldLabel" class="mb-16">
                  <el-input v-model="editForm.fieldLabel" :placeholder="$tt('如：企业规模')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$tf('字段类型')" prop="fieldType" class="mb-16">
                  <el-select v-model="editForm.fieldType" style="width:100%" @change="onTypeChange">
                    <el-option v-for="t in fieldTypeOptions" :key="t.value" :label="$tf(t.label)" :value="t.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$tf('所属分区')" class="mb-16">
                  <el-input v-model="editForm.sectionName" :placeholder="$tt('如：自定义信息')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$tf('排序')" class="mb-16">
                  <el-input-number v-model="editForm.sortOrder" :min="0" :max="999" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$tf('默认值')" class="mb-16">
                  <el-input v-model="editForm.defaultValue" :placeholder="$tt('新增时的默认值（可选）')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$tf('输入提示')" class="mb-16">
                  <el-input v-model="editForm.placeholder" :placeholder="$tt('表单中的占位提示文字（可选）')" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </div>

        <!-- 下拉选项配置 -->
        <div v-if="editForm.fieldType === 'select'" class="section-block">
          <div class="section-title">{{ $tt("下拉选项配置") }}</div>
          <div class="section-content">
            <div v-for="(opt, idx) in selectOptions" :key="idx" class="option-row">
              <span class="option-idx">{{ idx + 1 }}</span>
              <el-input v-model="opt.label" :placeholder="$tt('显示文本')" size="default" style="flex:1;" />
              <span class="option-arrow">=</span>
              <el-input v-model="opt.value" :placeholder="$tt('存储值')" size="default" style="flex:1;" />
              <el-button v-permission="perm('deleteRow')" link type="danger" @click="selectOptions.splice(idx, 1)" style="margin-left:8px;">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button v-permission="perm('addRow')" type="primary" link @click="selectOptions.push({ label: '', value: '' })" style="margin-top:8px;">
              + 添加选项
            </el-button>
          </div>
        </div>

        <!-- 数字字段配置 -->
        <div v-if="editForm.fieldType === 'number'" class="section-block">
          <div class="section-title">{{ $tt("数字字段配置") }}</div>
          <div class="section-content">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item :label="$tf('小数位')" class="mb-16">
                  <el-input-number v-model="editForm.precisionVal" :min="0" :max="6" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="$tf('最小值')" class="mb-16">
                  <el-input-number v-model="editForm.minValue" :precision="4" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="$tf('最大值')" class="mb-16">
                  <el-input-number v-model="editForm.maxValue" :precision="4" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </div>

        <!-- 公式配置（可视化构建器） -->
        <div v-if="editForm.fieldType === 'formula'" class="section-block">
          <div class="section-title">{{ $tt("公式计算配置") }}</div>
          <div class="section-content">
            <el-form-item :label="$tf('小数位')" class="mb-16">
              <el-input-number v-model="editForm.precisionVal" :min="0" :max="6" />
            </el-form-item>

            <!-- 可视化公式构建区 -->
            <div class="formula-builder">
              <div class="builder-label">{{ $tt("可视化构建公式：") }}</div>
              <div class="builder-area">
                <!-- 字段选择 -->
                <div class="builder-group">
                  <div class="group-label">{{ $tt("选择字段") }}</div>
                  <div class="group-tags">
                    <el-tag
                      v-for="f in availableNumberFields"
                      :key="f.fieldKey"
                      size="default"
                      effect="plain"
                      class="field-tag"
                      @click="insertField(f.fieldKey)"
                    >
                      {{ f.fieldLabel }}
                      <span class="tag-key">({{ f.fieldKey }})</span>
                    </el-tag>
                    <span v-if="availableNumberFields.length === 0" style="color:#909399; font-size:13px;">
                      暂无可用的数字字段（请先保存其他数字类型的自定义字段）
                    </span>
                  </div>
                </div>

                <!-- 运算符按钮 -->
                <div class="builder-group">
                  <div class="group-label">{{ $tt("运算符") }}</div>
                  <div class="group-buttons">
                    <el-button v-permission="perm('apply')" v-for="op in operators" :key="op.value" size="default" @click="insertText(op.value)" :title="op.label">
                      {{ $tf(op.label) }}
                    </el-button>
                    <el-button v-permission="perm('apply')" size="default" @click="insertText('(')" :title="$tt('左括号')">( )</el-button>
                    <el-button v-permission="perm('apply')" size="default" @click="insertText('100')" :title="$tt('插入数字100')">100</el-button>
                    <el-button v-permission="perm('clear')" size="default" type="danger" plain @click="clearFormula" :title="$tt('清空公式')">
                      <el-icon><Delete /></el-icon> 清空
                    </el-button>
                  </div>
                </div>

                <!-- 公式预览/编辑 -->
                <div class="builder-group">
                  <div class="group-label">
                    公式表达式
                    <span style="font-weight:normal; color:#909399; font-size:12px; margin-left:8px;">
                      可直接编辑，也可点击上方字段和按钮插入
                    </span>
                  </div>
                  <el-input
                    v-model="editForm.formulaExpr"
                    type="textarea"
                    :rows="3"
                    placeholder="例如：{{custom_1}} * {{custom_2}} 或 {{length}} * {{width}} * {{height}}"
                    class="formula-input"
                  />
                </div>

                <!-- 公式校验结果 -->
                <div class="formula-result">
                  <el-alert
                    v-if="formulaValidation.message"
                    :type="formulaValidation.valid ? 'success' : 'error'"
                    :closable="false"
                    show-icon
                  >
                    {{ formulaValidation.message }}
                  </el-alert>
                </div>

                <!-- 实时测试 -->
                <div class="builder-group" v-if="formulaValidation.valid">
                  <div class="group-label">{{ $tt("实时测试（输入测试值）") }}</div>
                  <div class="test-area">
                    <div v-for="dep in formulaDeps" :key="dep" class="test-item">
                      <span class="test-label">{{ getFieldLabel(dep) }}:</span>
                      <el-input-number v-model="testValues[dep]" :precision="2" :min="0" size="default" />
                    </div>
                    <div v-if="formulaDeps.length > 0" class="test-result">
                      <span class="result-label">{{ $tt("计算结果：") }}</span>
                      <span class="result-value">{{ testResult !== null ? Number(testResult).toFixed(editForm.precisionVal || 2) : '--' }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 显示与行为 -->
        <div class="section-block">
          <div class="section-title">{{ $tt("显示与行为") }}</div>
          <div class="section-content">
            <el-row :gutter="20">
              <el-col :span="6">
                <div class="switch-item">
                  <div class="switch-label">{{ $tt("是否必填") }}</div>
                  <el-switch v-model="editForm.isRequired" :active-value="1" :inactive-value="0" />
                </div>
              </el-col>
              <!-- 「列表显示」仅对"一条记录一个值"的对象开放；单据明细（xxx明细）一张单有 N 行、
                   字段就是 N 个值，无法作为列表的一列展示 → 明细对象隐藏该开关 -->
              <el-col :span="6" v-if="!isItemEntity">
                <div class="switch-item">
                  <div class="switch-label">{{ $tt("列表显示") }}</div>
                  <el-switch v-model="editForm.isListShow" :active-value="1" :inactive-value="0" />
                </div>
              </el-col>
              <el-col :span="6">
                <div class="switch-item">
                  <div class="switch-label">{{ $tt("可搜索") }}</div>
                  <el-switch v-model="editForm.isSearch" :active-value="1" :inactive-value="0" />
                </div>
              </el-col>
              <el-col :span="6">
                <div class="switch-item">
                  <div class="switch-label">{{ $tt("启用") }}</div>
                  <el-switch v-model="editForm.isEnabled" :active-value="1" :inactive-value="0" />
                </div>
              </el-col>
              <!-- 「下拉显示」仅对物料实体开放，且受定制开关控制：控制该字段是否出现在单据明细的"选择物料"下拉列表中 -->
              <el-col :span="6" v-if="dropDisplayEnabled && editForm.entityType === 'material'">
                <div class="switch-item">
                  <div class="switch-label">{{ $tt("下拉显示") }}</div>
                  <el-switch v-model="editForm.isDropShow" :active-value="1" :inactive-value="0" />
                </div>
              </el-col>
            </el-row>
            <div style="font-size:12px; color:#909399; margin-top:8px;">
              「可搜索」字段会同步到业务表的 ext_data JSONB 列，支持 WHERE 条件过滤查询
            </div>
            <div v-if="dropDisplayEnabled && editForm.entityType === 'material'" style="font-size:12px; color:#909399; margin-top:4px;">
              「下拉显示」字段会出现在单据明细"选择物料"的下拉列表中（物料库弹窗始终显示全部自定义字段）
            </div>
          </div>
        </div>

        <!-- 备注 -->
        <div class="section-block">
          <div class="section-title">{{ $tt("备注") }}</div>
          <div class="section-content">
            <el-input v-model="editForm.remark" type="textarea" :rows="2" :placeholder="$tt('字段说明（可选）')" />
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" :loading="saving" @click="handleSave">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:custom-field')
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, User, Trophy, Box, Document, Grid, Van, Tickets, OfficeBuilding, ChatDotSquare, RefreshLeft, Money, Sort, Download, Upload, Finished, Setting, List, House, Odometer, UserFilled, Coin, Files, Coordinate, Wallet, PriceTag, Collection, CreditCard } from '@element-plus/icons-vue'
import { tt } from '@/i18n'

const iconMap = { User, Trophy, Box, Document, Grid, Van, Tickets, OfficeBuilding, ChatDotSquare, RefreshLeft, Money, Sort, Download, Upload, Finished, Setting, List, House, Odometer, UserFilled, Coin, Files, Coordinate, Wallet, PriceTag, Collection, CreditCard }
import {
  listDefinition, saveDefinition, deleteDefinition, refreshCache, getDropDisplayEnabled
} from '@/api/system/customField'
import { validateFormula, extractFormulaDeps, evaluateFormula } from '@/utils/formulaEvaluator'

const entityList = [
  // 基础资料
  { key: 'customer', label: '客户', icon: 'User', group: '基础资料' },
  { key: 'supplier', label: '供应商', icon: 'OfficeBuilding', group: '基础资料' },
  { key: 'material', label: '物料', icon: 'Box', group: '基础资料' },
  // 基础资料·档案（2026-09-15 开放：这些页面早就接了字段能力，此前配置页没开放 → 白挂）
  { key: 'material_category', label: '物料分类', icon: 'Collection', group: '基础资料' },
  { key: 'unit', label: '计量单位', icon: 'Odometer', group: '基础资料' },
  { key: 'warehouse', label: '仓库档案', icon: 'House', group: '基础资料' },
  { key: 'location', label: '货位档案', icon: 'Coordinate', group: '基础资料' },
  { key: 'department', label: '部门档案', icon: 'OfficeBuilding', group: '基础资料' },
  { key: 'employee', label: '员工档案', icon: 'UserFilled', group: '基础资料' },
  { key: 'currency', label: '币种', icon: 'Coin', group: '基础资料' },
  { key: 'settle', label: '结算方式', icon: 'Wallet', group: '基础资料' },
  { key: 'price_level', label: '价格等级', icon: 'PriceTag', group: '基础资料' },
  { key: 'bank_account', label: '银行账户', icon: 'CreditCard', group: '基础资料' },
  // 会计科目不接入自定义字段（与"不接入字段权限/不进 EntityMetadataRegistry"同口径）——按用户要求全面注释，勿恢复
  // { key: 'account', label: '会计科目', icon: 'Files', group: '基础资料' },
  { key: 'opportunity', label: '商机', icon: 'Trophy', group: '客户管理' },
  // 销售模块
  { key: 'sale_quote', label: '销售报价单', icon: 'ChatDotSquare', group: '销售管理' },
  { key: 'sale_quote_item', label: '销售报价明细', icon: 'Tickets', group: '销售管理' },
  { key: 'sale_contract', label: '销售合同', icon: 'Document', group: '销售管理' },
  { key: 'sale_contract_item', label: '销售合同明细', icon: 'Tickets', group: '销售管理' },
  { key: 'sale_order', label: '销售订单', icon: 'Document', group: '销售管理' },
  { key: 'sale_order_item', label: '销售订单明细', icon: 'Grid', group: '销售管理' },
  { key: 'sale_delivery', label: '销售出库单', icon: 'Van', group: '销售管理' },
  { key: 'sale_delivery_item', label: '销售出库明细', icon: 'Tickets', group: '销售管理' },
  { key: 'sale_return', label: '销售退货单', icon: 'RefreshLeft', group: '销售管理' },
  { key: 'sale_return_item', label: '销售退货明细', icon: 'Tickets', group: '销售管理' },
  { key: 'sale_invoice', label: '销售发票', icon: 'Money', group: '销售管理' },
  { key: 'sale_invoice_item', label: '销售发票明细', icon: 'Tickets', group: '销售管理' },
  // 采购模块
  { key: 'purchase_request', label: '请购单', icon: 'Document', group: '采购管理' },
  { key: 'purchase_request_item', label: '请购单明细', icon: 'Tickets', group: '采购管理' },
  { key: 'purchase_order', label: '采购订单', icon: 'Document', group: '采购管理' },
  { key: 'purchase_order_item', label: '采购订单明细', icon: 'Grid', group: '采购管理' },
  { key: 'purchase_inbound', label: '采购入库单', icon: 'Van', group: '采购管理' },
  { key: 'purchase_inbound_item', label: '采购入库明细', icon: 'Tickets', group: '采购管理' },
  { key: 'purchase_return', label: '采购退货单', icon: 'RefreshLeft', group: '采购管理' },
  { key: 'purchase_return_item', label: '采购退货明细', icon: 'Tickets', group: '采购管理' },
  { key: 'purchase_contract', label: '采购合同', icon: 'Document', group: '采购管理' },
  { key: 'purchase_contract_item', label: '采购合同明细', icon: 'Tickets', group: '采购管理' },
  { key: 'purchase_invoice', label: '采购发票', icon: 'Money', group: '采购管理' },
  { key: 'purchase_invoice_item', label: '采购发票明细', icon: 'Tickets', group: '采购管理' },
  // 库存模块
  { key: 'stock_transfer', label: '调拨单', icon: 'Sort', group: '库存管理' },
  { key: 'stock_transfer_item', label: '调拨单明细', icon: 'Tickets', group: '库存管理' },
  { key: 'other_receipt', label: '其他入库单', icon: 'Download', group: '库存管理' },
  { key: 'other_receipt_item', label: '其他入库明细', icon: 'Tickets', group: '库存管理' },
  { key: 'other_issue', label: '其他出库单', icon: 'Upload', group: '库存管理' },
  { key: 'other_issue_item', label: '其他出库明细', icon: 'Tickets', group: '库存管理' },
  { key: 'material_requisition', label: '领料单', icon: 'Box', group: '库存管理' },
  { key: 'material_requisition_item', label: '领料单明细', icon: 'Tickets', group: '库存管理' },
  { key: 'stock_check', label: '盘点单', icon: 'Finished', group: '库存管理' },
  { key: 'stock_check_item', label: '盘点单明细', icon: 'Tickets', group: '库存管理' },
  // 生产模块
  { key: 'produce_assemble', label: '组装单', icon: 'Setting', group: '生产管理' },
  { key: 'produce_disassemble', label: '拆卸单', icon: 'Setting', group: '生产管理' },
  // 【第 13 期】生产工单接入自定义字段（此前漏接线，生产模块只挂了组装/拆卸单）；
  // 用途：车间排程看板「站点设置」可把工单自定义字段放到工序站点上
  { key: 'production_order', label: '生产工单', icon: 'Tickets', group: '生产管理' },
  // 【2026-11-25】生产工序（工单内工序进度节点）：工序配置弹窗 / 工序操作面板填写，打印模板带出
  { key: 'work_order_operation', label: '生产工序', icon: 'Setting', group: '生产管理' },
  // 2026-11-24 用户口径「生产入库单、报工单、委外 5 类加入自定义功能」
  { key: 'production_receipt', label: '生产入库单', icon: 'Document', group: '生产管理' },
  { key: 'production_receipt_item', label: '生产入库明细', icon: 'Tickets', group: '生产管理' },
  { key: 'operation_report', label: '报工单', icon: 'Document', group: '生产管理' },
  // 委外管理（2026-11-24 新增）
  { key: 'subcontract_order', label: '委外加工单', icon: 'Document', group: '委外管理' },
  { key: 'subcontract_order_item', label: '委外加工明细', icon: 'Tickets', group: '委外管理' },
  { key: 'subcontract_pick', label: '委外发料单', icon: 'Upload', group: '委外管理' },
  { key: 'subcontract_pick_item', label: '委外发料明细', icon: 'Tickets', group: '委外管理' },
  { key: 'subcontract_inbound', label: '委外入库单', icon: 'Download', group: '委外管理' },
  { key: 'subcontract_inbound_item', label: '委外入库明细', icon: 'Tickets', group: '委外管理' },
  { key: 'subcontract_return', label: '委外退料单', icon: 'RefreshLeft', group: '委外管理' },
  { key: 'subcontract_return_item', label: '委外退料明细', icon: 'Tickets', group: '委外管理' },
  { key: 'subcontract_supplement', label: '委外补料单', icon: 'Box', group: '委外管理' },
  { key: 'subcontract_supplement_item', label: '委外补料明细', icon: 'Tickets', group: '委外管理' },
  // BOM 在前端菜单里挂在「基础资料」组下（/masterdata/bom），此处对齐菜单分组，不再归「其他」
  { key: 'bom', label: 'BOM', icon: 'List', group: '基础资料' },
  { key: 'bom_item', label: 'BOM明细', icon: 'Tickets', group: '基础资料' },
  // 打样管理：打样单（sample_order）与免费样品订单（free_sample_order）是两张不同单据。
  // 2026-10-09 更正：此前 free_sample_order 的标签被误写成"打样单"，会让用户在"打样单"名下
  // 配的字段实际作用到免费样品订单上；本轮更正标签并补上真正的打样单对象。
  { key: 'sample_order', label: '打样单', icon: 'Document', group: '打样管理' },
  { key: 'sample_order_item', label: '打样单明细', icon: 'Tickets', group: '打样管理' },
  { key: 'free_sample_order', label: '免费样品订单', icon: 'Document', group: '打样管理' },
  { key: 'free_sample_order_item', label: '免费样品订单明细', icon: 'Tickets', group: '打样管理' }
]
const activeEntity = ref('customer')
// 「自定义字段下拉显示」定制功能总开关（设置中心→系统参数→界面显示 可配）；关闭后本页不显示相关项
const dropDisplayEnabled = ref(true)
const currentEntityLabel = computed(() => entityList.find(e => e.key === activeEntity.value)?.label || '')
// 是否为「单据明细」对象（key 以 _item 结尾，如 销售订单明细）：一张单有 N 行 → 该字段就有 N 个值，
// 无法作为列表的一格展示 → 明细对象不提供「列表显示」（隐藏开关、不显示标记、保存时强制 0）
// 生产工序（work_order_operation）与明细对象一样是"一张单 N 行"：不提供列表显示
const isItemEntity = computed(() => (activeEntity.value || '').endsWith('_item') || activeEntity.value === 'work_order_operation')

// ===== 左侧导航：按业务域分组 + 关键字搜索 =====
// 分组顺序与名称对齐前端左侧菜单（menuConfig.js）：基础资料 / 客户管理 / 销售管理 / 采购管理 / 库存管理 / 生产管理 / 打样管理
// 说明：本表是白名单式过滤 —— entityList 里出现的分组名必须在这里，否则该分组（含条目）会被整组隐藏
const GROUP_ORDER = ['基础资料', '客户管理', '销售管理', '采购管理', '库存管理', '生产管理', '委外管理', '打样管理']
const entityKeyword = ref('')
const activeGroups = ref([...GROUP_ORDER])
const groupedEntities = computed(() => {
  const kw = entityKeyword.value.trim().toLowerCase()
  return GROUP_ORDER
    .map(g => ({
      group: g,
      items: entityList.filter(e =>
        e.group === g &&
        (!kw || e.label.toLowerCase().includes(kw) || e.key.toLowerCase().includes(kw))
      )
    }))
    .filter(g => g.items.length > 0)
})
function onSelectEntity(key) {
  activeEntity.value = key
  load()
}

const fieldTypeOptions = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '下拉选择' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'switch', label: '开关(布尔)' },
  { value: 'formula', label: '公式计算' }
]
function typeLabel(t) {
  return fieldTypeOptions.find(o => o.value === t)?.label || t
}
function typeTagType(t) {
  const m = { text: '', textarea: 'info', number: 'success', select: 'warning', date: 'info', datetime: 'info', switch: 'info', formula: 'danger' }
  return m[t] || ''
}

const operators = [
  { value: ' + ', label: '+ 加' },
  { value: ' - ', label: '- 减' },
  { value: ' * ', label: '× 乘' },
  { value: ' / ', label: '÷ 除' },
]

const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const fieldList = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const selectOptions = ref([])
const testValues = reactive({})

const editForm = ref({
  fieldId: null, entityType: 'customer',
  fieldKey: '', fieldLabel: '', fieldType: 'text',
  fieldOptions: '', defaultValue: '', placeholder: '',
  precisionVal: 2, minValue: null, maxValue: null,
  formulaExpr: '', formulaDeps: '',
  sectionName: '自定义信息', sortOrder: 0,
  isRequired: 0, isListShow: 0, isSearch: 0, isEnabled: 1, remark: ''
})

const rules = {
  fieldLabel: [{ required: true, message: tt('请输入字段名称'), trigger: 'blur' }],
  fieldType: [{ required: true, message: tt('请选择字段类型'), trigger: 'change' }],
}

const formulaDeps = computed(() => extractFormulaDeps(editForm.value.formulaExpr))

const formulaValidation = computed(() => {
  if (editForm.value.fieldType !== 'formula') return { valid: true, message: '' }
  if (!editForm.value.formulaExpr) return { valid: false, message: tt('请输入公式表达式（可点击上方字段和运算符快速构建）') }
  return validateFormula(editForm.value.formulaExpr, formulaDeps.value)
})

const availableNumberFields = computed(() => {
  return fieldList.value.filter(f =>
    (f.fieldType === 'number' || f.fieldType === 'formula') &&
    f.isEnabled === 1 &&
    f.fieldKey !== editForm.value.fieldKey
  )
})

const testResult = computed(() => {
  if (!formulaValidation.value.valid || formulaDeps.value.length === 0) return null
  return evaluateFormula(editForm.value.formulaExpr, testValues)
})

function getFieldLabel(key) {
  const f = fieldList.value.find(x => x.fieldKey === key)
  return f?.fieldLabel || key
}

function insertField(fieldKey) {
  const text = '{{' + fieldKey + '}}'
  insertText(text)
}

function insertText(text) {
  if (!editForm.value.formulaExpr) editForm.value.formulaExpr = ''
  editForm.value.formulaExpr += text
}

function clearFormula() {
  editForm.value.formulaExpr = ''
}

async function load() {
  loading.value = true
  try {
    fieldList.value = await listDefinition(activeEntity.value) || []
  } catch (e) {
    ElMessage.error(tt('加载字段列表失败'))
  } finally {
    loading.value = false
  }
}

function onTypeChange() {
  if (editForm.value.fieldType === 'select' && selectOptions.value.length === 0) {
    selectOptions.value = [{ label: '选项1', value: 'option1' }, { label: '选项2', value: 'option2' }]
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `${tt("确定删除字段「")}${row.fieldLabel}（${row.fieldKey}${tt("）」吗？删除后该字段已录入的值也会一并清除。")}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确定删除'), cancelButtonText: tt('取消') }
    )
  } catch {
    return
  }
  try {
    await deleteDefinition(row.fieldId)
    ElMessage.success(tt('已删除'))
    load()
  } catch (e) {
    ElMessage.error(e.message || tt('删除失败'))
  }
}

function handleAdd() {
  editForm.value = {
    fieldId: null, entityType: activeEntity.value,
    fieldKey: '', fieldLabel: '', fieldType: 'text',
    fieldOptions: '', defaultValue: '', placeholder: '',
    precisionVal: 2, minValue: null, maxValue: null,
    formulaExpr: '', formulaDeps: '',
    sectionName: '自定义信息', sortOrder: fieldList.value.length + 1,
    isRequired: 0, isListShow: 0, isDropShow: 0, isSearch: 0, isEnabled: 1, remark: ''
  }
  selectOptions.value = []
  Object.keys(testValues).forEach(k => delete testValues[k])
  dialogVisible.value = true
}

// 点击字段名称进入二次编辑（与 handleAdd 共用同一弹窗，回填 row 数据后按 fieldId 区分新增/编辑）
function handleEdit(row) {
  editForm.value = {
    fieldId: row.fieldId ?? null,
    entityType: row.entityType || activeEntity.value,
    fieldKey: row.fieldKey || '',
    fieldLabel: row.fieldLabel || '',
    fieldType: row.fieldType || 'text',
    fieldOptions: row.fieldOptions || '',
    defaultValue: row.defaultValue ?? '',
    placeholder: row.placeholder ?? '',
    precisionVal: row.precisionVal ?? 2,
    minValue: row.minValue ?? null,
    maxValue: row.maxValue ?? null,
    formulaExpr: row.formulaExpr || '',
    formulaDeps: row.formulaDeps || '',
    sectionName: row.sectionName || '自定义信息',
    sortOrder: row.sortOrder ?? 0,
    isRequired: row.isRequired ?? 0,
    isListShow: row.isListShow ?? 0,
    isDropShow: row.isDropShow ?? 0,
    isSearch: row.isSearch ?? 0,
    isEnabled: row.isEnabled ?? 1,
    remark: row.remark ?? ''
  }
  if (editForm.value.fieldType === 'select') {
    try {
      selectOptions.value = row.fieldOptions ? JSON.parse(row.fieldOptions) : []
    } catch (_) { selectOptions.value = [] }
  } else {
    selectOptions.value = []
  }
  Object.keys(testValues).forEach(k => delete testValues[k])
  dialogVisible.value = true
}

watch(formulaDeps, (deps) => {
  deps.forEach(dep => {
    if (!(dep in testValues)) testValues[dep] = 0
  })
})

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch { return }
  if (editForm.value.fieldType === 'formula' && !formulaValidation.value.valid) {
    ElMessage.error(tt('公式表达式有误：') + formulaValidation.value.message)
    return
  }
  saving.value = true
  try {
    const data = { ...editForm.value }
    // 单据明细不提供「列表显示」：弹窗已隐藏该开关，保存时再兜底置 0（避免历史残留值继续生效）
    if ((data.entityType || '').endsWith('_item')) data.isListShow = 0
    if (data.fieldType === 'select') {
      const validOpts = selectOptions.value.filter(o => o.label && o.value)
      if (validOpts.length === 0) {
        ElMessage.error(tt('请至少配置一个有效的下拉选项'))
        saving.value = false
        return
      }
      data.fieldOptions = JSON.stringify(validOpts)
    } else {
      data.fieldOptions = null
    }
    if (data.fieldType === 'formula') {
      data.formulaDeps = JSON.stringify(extractFormulaDeps(data.formulaExpr))
    } else {
      data.formulaExpr = null
      data.formulaDeps = null
    }
    await saveDefinition(data)
    ElMessage.success(editForm.value.fieldId ? tt('修改成功') : tt('新增成功'))
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    await refreshCache()
    ElMessage.success(tt('缓存已刷新'))
  } catch (e) {
    ElMessage.error(tt('刷新失败'))
  } finally {
    refreshing.value = false
  }
}

onMounted(async () => {
  // 定制功能总开关：取不到按开启处理，失败不阻断页面
  try {
    dropDisplayEnabled.value = !!(await getDropDisplayEnabled())
  } catch (e) {
    dropDisplayEnabled.value = true
  }
  load()
})
</script>

<style scoped>
.custom-field-config { padding: 12px; }
.config-card { min-height: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header .title { font-size: 16px; font-weight: 600; }
/* 左栏菜单独立滚动、内容区独立滚动：整页不再跟着一起滑 —— 滑动就发生在菜单里 */
.config-body { display: flex; gap: 16px; height: calc(100vh - 240px); min-height: 380px; }
.group-nav { width: 250px; border-right: 1px solid #ebeef5; padding-right: 12px; display: flex; flex-direction: column; gap: 10px; height: 100%; }
.entity-search { width: 100%; flex: none; }
.group-scroll { flex: 1; height: 0; min-height: 0; }
.group-collapse { border-top: none; }
.group-collapse :deep(.el-collapse-item__header) { height: 36px; line-height: 36px; font-size: 13px; border-bottom: 1px dashed #ebeef5; }
.group-collapse :deep(.el-collapse-item__content) { padding-bottom: 4px; }
.group-title { flex: 1; font-size: 13px; color: #303133; font-weight: 600; }
.group-count { background: #f0f2f5; color: #909399; border-radius: 10px; font-size: 11px; padding: 0 7px; }
.entity-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; margin: 2px 0; border-radius: 6px; cursor: pointer; color: #606266; font-size: 13px; transition: all .15s; }
.entity-item:hover { background: #f0f4fa; }
.entity-item.active { background: #ecf5ff; color: #409EFF; font-weight: 600; }
.entity-icon { font-size: 14px; }
.entity-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.entity-empty { color: #909399; font-size: 13px; text-align: center; padding: 20px 0; }
.config-form { flex: 1; padding: 0 8px; height: 100%; overflow-y: auto; }
.form-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.form-title { font-size: 16px; font-weight: 600; }
.form-tip { color: #909399; font-size: 13px; }

.custom-field-dialog :deep(.el-dialog__body) {
  padding: 0 24px 16px;
}
.custom-field-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}
.section-block {
  margin-bottom: 20px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}
.section-title {
  background: #f8f9fb;
  padding: 10px 16px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
}
.section-content {
  padding: 16px;
}
.mb-16 {
  margin-bottom: 16px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 6px 8px;
  background: #f8f9fb;
  border-radius: 4px;
}
.option-idx {
  width: 24px;
  height: 24px;
  background: #409EFF;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.option-arrow {
  color: #909399;
  font-weight: 600;
}

.switch-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: #f8f9fb;
  border-radius: 6px;
}
.switch-label {
  font-size: 13px;
  color: #606266;
}

.formula-builder {
  background: #f8f9fb;
  border-radius: 6px;
  padding: 12px;
}
.builder-label {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.builder-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.builder-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.group-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.group-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.field-tag {
  cursor: pointer;
  transition: all .2s;
}
.field-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64,158,255,0.3);
}
.tag-key {
  opacity: 0.7;
  font-size: 11px;
  margin-left: 2px;
}
.group-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.formula-input :deep(textarea) {
  font-family: Consolas, Monaco, monospace;
  font-size: 14px;
  line-height: 1.6;
}
.formula-result {
  margin-top: 4px;
}
.test-area {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}
.test-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.test-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}
.test-result {
  margin-left: auto;
  padding: 8px 16px;
  background: #ecf5ff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-label {
  font-size: 13px;
  color: #409EFF;
}
.result-value {
  font-size: 18px;
  font-weight: 600;
  color: #409EFF;
  font-family: Consolas, Monaco, monospace;
}
</style>
