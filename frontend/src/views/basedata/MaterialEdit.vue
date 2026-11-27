<template>
  <div class="material-edit-page">
    <!-- 顶部：返回 + 标题 + 操作按钮 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="goBack">
          <span class="back-arrow">←</span>
          <span class="back-text">{{ $tt("返回列表") }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">{{ isEdit ? '编辑物料' : '新增物料' }}</div>
          <div class="page-sub">{{ $tt("填写物料档案信息，带") }} <span class="req">*</span> {{ $tt("为必填项") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button size="small" v-permission="perm('view')" @click="changeRecordVisible = true">
          <el-icon><Memo /></el-icon>操作日志
        </el-button>
        <el-button v-permission="perm('dlgCancel')" @click="goBack">{{ $tt("取消") }}</el-button>
        <el-button :loading="saving" v-permission="perm('saveDraft')" @click="saveDraft">{{ $tt("保存为草稿") }}</el-button>
        <el-button type="primary" :loading="saving" v-permission="perm('saveAndBack')" @click="saveAndBack">{{ $tt("保存并返回") }}</el-button>
        <!-- 商品二维码（2026-09-27）：已保存的物料在本页常驻显示，手机对屏即可扫。
             视觉：与按钮用细分隔线切开，做成竖向码牌（白底圆角小卡 + 标题小字），避免把裸码塞进按钮堆；权限复用「打印」。 -->
        <template v-if="isEdit && form.materialId">
          <span class="header-split" aria-hidden="true"></span>
          <QrCodeCard
            v-permission="perm('print')"
            type="MAT"
            :biz-id="form.materialId"
            :size="56"
            :title="$tt('商品码')"
          />
        </template>
      </div>
    </div>

    <!-- 表单内容区：4 个分区纵向排列 -->
    <div class="form-content">
      <!-- 区域 1: 基本信息 -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("基本信息") }}</span>
          <span class="title-sub">{{ $tt("物料的标识和分类") }}</span>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="right" label-width="116px" class="form-grid">
          <el-form-item :label="$tf('物料编码')" prop="materialCode" :required="isRequiredField('materialCode')">
            <el-input
              v-model="form.materialCode"
              :disabled="autoCodeOn"
              :placeholder="autoCodeOn ? $tt('保存后自动生成') : $tt('请输入物料编码')"
            />
            <div style="font-size:12px;color:#999;margin-top:4px">
              <template v-if="autoCodeOn">选择物料分类后自动生成</template>
              <template v-else>请手动输入物料编码</template>
            </div>
          </el-form-item>
          <el-form-item :label="$tf('物料名称')" prop="materialName" :required="isRequiredField('materialName')">
            <el-input v-model="form.materialName" :placeholder="$tt('如 精密轴承')">
              <template #append v-if="nameChecking">
                <el-icon class="is-loading"><Loading /></el-icon>
              </template>
            </el-input>
            <!-- 预检结果提示 -->
            <div v-if="nameCheckResult?.enabled && nameCheckResult?.duplicate" style="color:#f56c6c;font-size:12px;margin-top:4px;display:flex;align-items:center;gap:4px;">
              <el-icon><Warning /></el-icon>
              <span>{{ $tt("该物料名称已存在，保存时将被阻止") }}</span>
            </div>
            <div v-else-if="nameCheckResult?.enabled && !nameCheckResult?.duplicate && form.materialName && form.materialName.trim().length >= 2" style="color:#67c23a;font-size:12px;margin-top:4px;display:flex;align-items:center;gap:4px;">
              <el-icon><CircleCheck /></el-icon>
              <span>{{ $tt("名称可用") }}</span>
            </div>
          </el-form-item>
          <el-form-item :label="$tf('全称')" :required="isRequiredField('fullName')">
            <el-input v-model="form.fullName" :placeholder="$tt('物料全称')" />
          </el-form-item>
          <el-form-item :label="$tf('规格型号')" :required="isRequiredField('model')">
            <el-input v-model="form.model" :placeholder="$tt('如 M8*20')" />
          </el-form-item>
          <el-form-item :label="$tf('物料类型')" prop="materialType" :required="isRequiredField('materialType')">
            <el-select v-model="form.materialType" :placeholder="$tt('选择类型')" style="width:100%">
              <el-option v-for="opt in typeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('物料分类')" prop="categoryId" :required="isRequiredField('categoryId')">
            <CategorySelect v-model="form.categoryId" @change="onCategoryChange" />
            <div style="font-size:12px;color:#999;margin-top:4px">
              选择分类后自动生成物料编码
            </div>
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 2: 计量信息（多单位换算的设置都放这里） -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("计量信息") }}</span>
          <span class="title-sub">{{ $tt("各环节使用的单位与换算关系") }}</span>
        </div>
        <el-form :model="form" label-position="right" label-width="116px" class="form-grid">
          <el-form-item :label="$tf('基本单位')" prop="baseUnitId" :required="isRequiredField('baseUnitId')">
            <el-select v-model="form.baseUnitId" :placeholder="$tt('请选择计量单位')" filterable style="width:100%">
              <el-option v-for="opt in unitOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('辅计量管理')">
            <el-switch v-model="form.multiUnitEnabled" />
            <div style="font-size:12px;color:#999;margin-top:4px">开启后，单据可切换辅计量单位并按下方换算率折算</div>
          </el-form-item>
        </el-form>
        <el-tabs v-if="form.multiUnitEnabled" v-model="conversionTab" class="conversion-tabs">
          <el-tab-pane :label="$tt('辅计量换算')" name="conversion">
            <div class="conversion-toolbar">
              <el-button size="small" type="primary" plain @click="addConversionRow">{{ $tt('添加一行') }}</el-button>
            </div>
            <el-table :data="conversionRows" size="small" border class="conversion-table">
              <el-table-column :label="$tt('辅计量单位')" min-width="170">
                <template #default="{ row }">
                  <el-select v-model="row.unitId" :placeholder="$tt('请选择单位')" filterable style="width:100%">
                    <el-option v-for="opt in unitOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="$tt('换算方式')" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.rateMode" style="width:100%">
                    <el-option :label="$tt('固定')" value="固定" />
                    <el-option :label="$tt('浮动')" value="浮动" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="$tt('换算率')" min-width="170">
                <template #default="{ row }">
                  <el-input-number v-model="row.conversionRate" :precision="4" :step="0.1" :min="0" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column :label="$tt('操作')" width="80" align="center">
                <template #default="{ $index }">
                  <el-button size="small" type="danger" link @click="removeConversionRow($index)">{{ $tt('删除') }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="conversion-tip">{{ $tt('1 个基本单位折合多少辅计量单位，如 1盒=10件 换算率填 10') }}</div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 区域：替代货品（销售替代出库时换货弹窗的默认候选）；客户包隐藏换货单时整块不渲染 -->
      <div class="form-card" v-if="!exchangeBillHidden">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("替代货品") }}</span>
          <span class="title-sub">{{ $tt("销售替代出库时换货弹窗的默认候选货品") }}</span>
        </div>
        <div class="substitute-toolbar">
          <el-button size="small" type="primary" plain @click="substitutePickerVisible = true">{{ $tt('添加替代货品') }}</el-button>
        </div>
        <el-table :data="substitutes" size="small" border class="substitute-table">
          <el-table-column prop="materialCode" :label="$tf('货品编码')" min-width="120" />
          <el-table-column prop="materialName" :label="$tf('货品名称')" min-width="160" />
          <el-table-column prop="specification" :label="$tf('规格型号')" min-width="120" />
          <el-table-column :label="$tf('基本单位')" width="100">
            <template #default="{ row }">{{ unitNameOf(row.baseUnitId) }}</template>
          </el-table-column>
          <el-table-column :label="$tf('操作')" width="80" align="center">
            <template #default="{ $index }">
              <el-button size="small" type="danger" link @click="removeSubstitute($index)">{{ $tt('删除') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!substitutes.length" class="substitute-tip">{{ $tt('还没有替代货品，点「添加替代货品」从物料库选择') }}</div>
      </div>

      <!-- 替代货品选择（通用物料库弹窗）；客户包隐藏换货单时不挂载 -->
      <MaterialPickerDialog v-if="!exchangeBillHidden" v-model="substitutePickerVisible" :exclude-fn="excludeSelfMaterial" @confirm="onSubstitutePicked" />

      <!-- 自定义字段分区（放在「基本信息」下方） -->
      <div class="form-card" v-for="section in headerCustomSections" :key="section.title" v-if="headerCustomSections.length > 0">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ section.title }}</span>
          <span class="title-sub" v-if="section.subtitle">{{ section.subtitle }}</span>
        </div>
        <el-form :model="form" label-position="right" label-width="116px" class="form-grid">
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
              style="width:100%"
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
              style="width:100%"
              :disabled="field.readonly"
            />
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="form[field.prop]"
              :active-value="field.activeValue !== undefined ? field.activeValue : 1"
              :inactive-value="field.inactiveValue !== undefined ? field.inactiveValue : 0"
            />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="form[field.prop]"
              type="date"
              value-format="YYYY-MM-DD"
              :placeholder="field.placeholder || '选择日期'"
              style="width:100%"
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="form[field.prop]"
              type="textarea"
              :rows="field.rows || 3"
              :placeholder="field.placeholder || '请输入'"
            />
            <el-input
              v-else
              v-model="form[field.prop]"
              :placeholder="field.placeholder || '请输入'"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 2: 价格与库存 -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("价格与库存") }}</span>
          <span class="title-sub">{{ $tt("物料的定价和库存预警") }}</span>
        </div>
        <el-form :model="form" label-position="right" label-width="116px" class="form-grid">
          <el-form-item :label="$tf('销售单价')" :required="isRequiredField('salePrice')">
            <el-input-number v-model="form.salePrice" :precision="4" :step="0.1" :min="0" controls-position="right" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$tf('采购单价')" :required="isRequiredField('purchasePrice')">
            <el-input-number v-model="form.purchasePrice" :precision="4" :step="0.1" :min="0" controls-position="right" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$tf('税率')" :required="isRequiredField('taxRate')">
            <el-input-number v-model="form.taxRate" :precision="4" :step="1" :min="0" :max="100" controls-position="right" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$tf('最低库存')" :required="isRequiredField('minStock')">
            <el-input-number v-model="form.minStock" :precision="4" :step="1" :min="0" controls-position="right" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$tf('最高库存')" :required="isRequiredField('maxStock')">
            <el-input-number v-model="form.maxStock" :precision="4" :step="1" :min="0" controls-position="right" style="width:100%" />
          </el-form-item>
          <el-form-item :label="$tf('采购批量')" :required="isRequiredField('purchaseBatchQty')">
            <el-input v-model="form.purchaseBatchQty" placeholder="0" style="width:100%" />
            <div style="font-size:12px;color:#909399;margin-top:4px;">{{ $tt("最小订单递增量，按基本单位填写；MRP 采购建议与单据数量按它的整数倍取整，0 表示不启用") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('最小订货量')" :required="isRequiredField('minOrderQty')">
            <el-input v-model="form.minOrderQty" placeholder="0" style="width:100%" />
            <div style="font-size:12px;color:#909399;margin-top:4px;">{{ $tt("每次采购的数量下限，按基本单位填写；0 表示不启用") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('单件标准工时(h)')" :required="isRequiredField('standardHours')">
            <el-input-number v-model="form.standardHours" :precision="4" :step="0.1" :min="0" controls-position="right" style="width:100%" />
            <div style="font-size:12px;color:#909399;margin-top:4px;">{{ $tt("生产一件的标准加工工时；工艺路线的『单件加工工时』会默认带出它，并用于工时误差对比") }}</div>
          </el-form-item>
          <!-- 2026-11-07：物料日产能，参与 APS 排程占用天数计算；0 或空 = 不限制 -->
          <el-form-item :label="$tf('日产能')" :required="isRequiredField('dailyCapacity')">
            <el-input v-model="form.dailyCapacity" :placeholder="$tt('0 表示不限制')" style="width:100%">
              <template #append>{{ $tt("件/天") }}</template>
            </el-input>
            <div style="font-size:12px;color:#909399;margin-top:4px;">{{ $tt("排程时按「工序计划数量 ÷ 日产能」估算占用天数；填 0 表示不限制") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('默认仓库')" prop="defaultWarehouseId" :required="isRequiredField('defaultWarehouseId')">
            <el-select v-model="form.defaultWarehouseId" filterable :placeholder="$tt('选择默认仓库')" class="field-input" clearable style="width:100%">
              <el-option v-for="w in withFallbackOption(warehouses, 'defaultWarehouseId', 'warehouseName', form)" :key="w.stockId || w.warehouseId" :label="w.stockName || w.warehouseName" :value="w.stockId || w.warehouseId" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 3: 控制选项 -->
      <div class="form-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("控制选项") }}</span>
          <span class="title-sub">{{ $tt("物料的批次/序列号/质检等管理方式") }}</span>
        </div>
        <el-form :model="form" label-position="right" label-width="116px" class="form-grid">
          <el-form-item :label="$tf('批次管理')" :required="isRequiredField('isLotControl')">
            <el-switch v-model="form.isLotControl" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="$tf('序列号管理')" :required="isRequiredField('isSnControl')">
            <el-switch v-model="form.isSnControl" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="$tf('货位管理')" :required="isRequiredField('isBinControl')">
            <el-switch v-model="form.isBinControl" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="$tf('质检要求')" :required="isRequiredField('isQcRequired')">
            <el-switch v-model="form.isQcRequired" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="$tf('保质期管理')" :required="isRequiredField('isExpiryControl')">
            <el-switch v-model="form.isExpiryControl" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item :label="$tf('有效期天数')" v-if="form.isExpiryControl === 1" :required="isRequiredField('expiryDays')">
            <el-input-number v-model="form.expiryDays" :min="0" :precision="0" style="width:120px" />
          </el-form-item>
          <el-form-item :label="$tf('保密物料')" :required="isRequiredField('isConfidential')">
            <el-switch v-model="form.isConfidential" :active-value="1" :inactive-value="0" />
            <div style="font-size:12px;color:#909399;margin-top:4px;">{{ $tt("开启后AI小助手将无法读取该物料信息") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('状态')" prop="status" :required="isRequiredField('status')">
            <el-select v-model="form.status" :placeholder="$tt('选择状态')" style="width:100%">
              <el-option :label="$tf('正常')" value="正常" />
              <el-option :label="$tf('停用')" value="停用" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('备注')" :required="isRequiredField('remark')">
            <el-input v-model="form.remark" type="textarea" :rows="2" :placeholder="$tt('请输入备注信息')" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 区域 4: 产品图片 -->
      <el-card style="margin-bottom:16px;">
        <template #header>{{ $tt("产品图片") }}</template>
        <el-form-item :label="$tf('产品图片')" :disabled="form.status !== '正常'" :required="isRequiredField('imageUrls')">
          <ImageUploader
            v-model="form.imageUrls"
            :main-image.sync="form.mainImage"
            :type="'material'"
            :disabled="form.status !== '正常'"
          />
        </el-form-item>
      </el-card>

      <!-- 区域 5: 信息确认 -->
      <div class="form-card confirm-card">
        <div class="card-title">
          <span class="title-bar"></span>
          <span class="title-text">{{ $tt("信息确认") }}</span>
          <span class="title-sub">{{ $tt("请核对填写内容无误后保存") }}</span>
        </div>
        <div class="confirm-grid">
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("物料编码") }}</span>
            <span class="confirm-value">{{ form.materialCode || '—' }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("物料名称") }}</span>
            <span class="confirm-value">{{ form.materialName || '—' }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("规格型号") }}</span>
            <span class="confirm-value">{{ form.model || '—' }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("物料类型") }}</span>
            <span class="confirm-value">{{ form.materialType || '—' }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("销售单价") }}</span>
            <span class="confirm-value">¥ {{ form.salePrice ?? 0 }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("采购单价") }}</span>
            <span class="confirm-value">¥ {{ form.purchasePrice ?? 0 }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("最低库存") }}</span>
            <span class="confirm-value">{{ form.minStock ?? 0 }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("最高库存") }}</span>
            <span class="confirm-value">{{ form.maxStock ?? 0 }}</span>
          </div>
          <div class="confirm-row">
            <span class="confirm-label">{{ $tt("状态") }}</span>
            <span class="confirm-value">
              <el-tag :type="form.status === '正常' ? 'success' : 'info'" size="small">{{ $dt(form.status || '正常') }}</el-tag>
            </span>
          </div>
        </div>
      </div>
    </div>
    <ChangeRecordDialog v-model="changeRecordVisible" bill-type="物料" :bill-no="form.materialCode || ''" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { withFallbackOption } from '@/utils/labelFallback'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMaterial, saveMaterial, updateMaterial, checkMaterialName } from '@/api/basedata/material'
// 换算关系保存后作废单据侧缓存（2026-11-11）：不刷新浏览器再进单据也按最新档案显示
import { invalidateConversions } from '@/composables/useUnitConvert'
import { listAllUnit } from '@/api/basedata/unit'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import ImageUploader from '@/components/ImageUploader.vue'
// 替代货品（2026-11-17）：销售替代出库换货弹窗的默认候选清单
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { listSubstitutes, batchSaveSubstitutes } from '@/api/basedata/materialSubstitute'
import { isBillHidden } from '@/config/hiddenBills'
import CategorySelect from '@/components/CategorySelect.vue'
import ChangeRecordDialog from '@/components/ChangeRecordDialog.vue'
import QrCodeCard from '@/components/QrCodeCard.vue'
import { Memo, Loading } from '@element-plus/icons-vue'
import {
  loadCustomFieldDefinitions,
  loadCustomFieldValues,
  saveCustomFieldValues
} from '@/composables/useCustomField'
import { generateMaterialCode, listCodeRule } from '@/api/basedata/material-code-rule'
import { isAutoGenerate } from '@/api/system/serialNoConfig'
import { useFieldPermission } from '@/composables/useFieldPermission'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('basedata:material')
const router = useRouter()
const route = useRoute()
// 客户包隐藏单据（VITE_HIDDEN_BILLS）：true = 「替代货品」整块不渲染（121 不上换货单）
const exchangeBillHidden = isBillHidden('SALE_EXCHANGE')
const formRef = ref()
const changeRecordVisible = ref(false)
// 保存防重复提交锁（双击保存会撞上第一次刚创建的记录，被名称查重误判"已存在"）
const saving = ref(false)

const isEdit = computed(() => !!route.params.id)

// 名称预检相关状态
const nameCheckResult = ref(null) // { enabled: boolean, duplicate: boolean }
const nameChecking = ref(false)
let nameCheckTimer = null

const typeOptions = [
  { value: '原材料', label: '原材料' },
  { value: '半成品', label: '半成品' },
  { value: '成品', label: '成品' },
  { value: '包材', label: '包材' },
  { value: '辅助材料', label: '辅助材料' },
  { value: '自制件', label: '自制件' }
]

// 计量单位下拉数据
const unitOptions = ref([])
const warehouses = ref([])

// ===== 替代货品（销售替代出库换货弹窗的默认候选）=====
const substitutes = ref([])
const substitutePickerVisible = ref(false)

function unitNameOf(unitId) {
  const u = unitOptions.value.find(x => x.value === unitId)
  return u ? u.label : ''
}

/** 选料弹窗排除货品自身 */
function excludeSelfMaterial(m) {
  return !!form.materialId && m && m.materialId === form.materialId
}

/** 追加替代货品（去重、排除自身） */
function onSubstitutePicked({ rows: picked }) {
  if (!picked || picked.length === 0) return
  picked.forEach(m => {
    if (form.materialId && m.materialId === form.materialId) return
    if (substitutes.value.some(s => s.substituteMaterialId === m.materialId)) return
    substitutes.value.push({
      substituteMaterialId: m.materialId,
      materialCode: m.materialCode,
      materialName: m.materialName,
      specification: m.model,
      baseUnitId: m.baseUnitId
    })
  })
  substitutePickerVisible.value = false
}

function removeSubstitute(index) {
  substitutes.value.splice(index, 1)
}

async function loadSubstitutes(materialId) {
  if (!materialId) {
    substitutes.value = []
    return
  }
  try {
    const list = await listSubstitutes(materialId)
    substitutes.value = (Array.isArray(list) ? list : []).map(r => ({
      substituteMaterialId: r.substituteMaterialId,
      materialCode: r.materialCode,
      materialName: r.materialName,
      specification: r.specification,
      baseUnitId: r.baseUnitId
    }))
  } catch (e) {
    substitutes.value = []
  }
}

async function loadOptions() {
  try {
    const unitList = await listAllUnit()
    unitOptions.value = (Array.isArray(unitList) ? unitList : (unitList?.records || [])).map(u => ({ value: u.unitId, label: u.unitName }))
  } catch (e) { console.warn('加载计量单位失败', e) }
  try {
    const whList = await listAllWarehouse()
    warehouses.value = Array.isArray(whList) ? whList : (whList?.records || [])
  } catch (e) { console.warn('加载仓库列表失败', e) }
}

const form = reactive({
  materialId: null,
  materialCode: '',
  materialName: '',
  fullName: '',
  model: '',
  materialType: '自制件',
  categoryId: null,
  baseUnitId: null,
  multiUnitEnabled: false,
  isLotControl: 0,
  isSnControl: 0,
  isBinControl: 0,
  isQcRequired: 0,
  isExpiryControl: 0,
  isConfidential: 0,
  expiryDays: 0,
  salePrice: 0,
  purchasePrice: 0,
  taxRate: 13,
  minStock: 0,
  maxStock: 0,
  purchaseBatchQty: 0,
  minOrderQty: 0,
  standardHours: 0,
  dailyCapacity: 0,
  defaultWarehouseId: null,
  status: '正常',
  imageUrls: '',
  mainImage: '',
  remark: ''
})

// 监听物料名称变化，进行防抖预检
// 注意：必须在 form 声明之后注册——原先注册在 form 之前，触发
// "Cannot access 'form' before initialization"（TDZ）报错，导致名称预检一直不生效
watch(() => form.materialName, async (newName) => {
  if (nameCheckTimer) clearTimeout(nameCheckTimer)
  if (!newName || newName.trim().length < 2) {
    nameCheckResult.value = null
    return
  }
  nameCheckTimer = setTimeout(async () => {
    nameChecking.value = true
    try {
      const excludeId = isEdit.value ? form.materialId : undefined
      const res = await checkMaterialName(newName.trim(), excludeId)
      nameCheckResult.value = res
    } catch (e) {
      console.warn('名称预检失败', e)
    } finally {
      nameChecking.value = false
    }
  }, 500) // 500ms 防抖
})

const rules = {
  materialName: [{ required: true, message: tt('请输入物料名称'), trigger: 'blur' }],
  materialType: [{ required: true, message: tt('请选择物料类型'), trigger: 'change' }],
  baseUnitId: [{ required: true, message: tt('请选择基本单位'), trigger: 'change' }],
  status: [{ required: true, message: tt('请选择状态'), trigger: 'change' }]
}

// ==================== 辅计量换算（2026-11-10 多单位换算重构第一阶段）====================
// 一个物料可配多个换算单位；行字段与后端实体一致（无 snake_case 转换层，字段名必须逐字一致）
const conversionTab = ref('conversion')
const conversionRows = ref([])

function addConversionRow() {
  conversionRows.value.push({ unitId: null, rateMode: '固定', conversionRate: null })
}

function removeConversionRow(index) {
  conversionRows.value.splice(index, 1)
}


const headerCustomSections = ref([])
const headerCustomFieldMap = new Map()
const headerFormulaFields = ref([])

function goBack() {
  router.push('/basedata/material')
}

async function loadCustomFields() {
  const headerResult = await loadCustomFieldDefinitions('material')
  headerCustomSections.value = headerResult.sections
  headerCustomFieldMap.clear()
  headerResult.fieldMap.forEach((v, k) => headerCustomFieldMap.set(k, v))
  headerFormulaFields.value = headerResult.formulaFields
}

async function loadData() {
  const __detailId = route.params.id || form.materialId || ''
  if (__detailId) {
    try {
      const data = await getMaterial(__detailId)
      // 辅计量换算明细单独取出（详情随物料返回），不灌进 form
      const { unitConversions, ...materialData } = data || {}
      Object.assign(form, materialData)
      conversionRows.value = Array.isArray(unitConversions)
        ? unitConversions.map(r => ({ unitId: r.unitId ?? null, rateMode: r.rateMode || '固定', conversionRate: r.conversionRate ?? null }))
        : []
      // 单件标准工时 / 日产能：后端老数据可能未返回该字段 → 兜底 0，避免输入框留空/出现 undefined
      if (form.standardHours == null) form.standardHours = 0
      if (form.dailyCapacity == null) form.dailyCapacity = 0
      if (headerCustomFieldMap.size > 0 && form.materialId) {
        const headerValues = await loadCustomFieldValues('material', form.materialId)
        Object.assign(form, headerValues)
      }
      await loadSubstitutes(form.materialId)
    } catch (e) {
      ElMessage.error(tt('加载数据失败'))
    }
  }
}

/**
 * 物料分类变化时按分类编码规则自动生成物料编码
 * - 仅新建模式或编码为空时预填，编辑模式已有编码不覆盖
 * - 未配置编码规则时静默降级：清空编码让用户手动输入
 */
async function onCategoryChange(categoryId) {
  if (!categoryId) return
  if (isEdit.value && form.materialCode) return
  try {
    const res = await generateMaterialCode(categoryId)
    const code = typeof res === 'string' ? res : (res?.code || res?.data || '')
    if (code) form.materialCode = code
  } catch (e) {
    form.materialCode = ''
    console.warn('按分类生成物料编码失败（可能未配置物料编码规则），请手动输入编码', e)
  }
}

// ==================== 必填判定（星号 + 保存拦截 唯一真相源）====================
// pageKey = material，与字段权限配置页 / EntityMetadataRegistry（实体 @TableName）同口径
const { loadFieldPermission, isFieldRequired } = useFieldPermission('material')

/**
 * 物料编码是否由系统自动生成：决定编号框是否置灰只读、以及是否要求用户填。
 * 两个来源任一成立即视为"自动生成"（与后端 MaterialController / MaterialServiceImpl
 * 及导入模板的 isAutoCodeEnabled 同一口径）：
 *   ①「自动编号设置 → 物料档案(MD_MATERIAL)」的"自动生成"开启；
 *   ② 存在启用中的「物料编码规则」（按物料分类配置，未命中分类时用通用规则兜底）。
 */
const serialAutoOn = ref(true)
const materialRuleExists = ref(false)
const autoCodeOn = computed(() => serialAutoOn.value || materialRuleExists.value)

/** 页面写死的静态必填（与后端 FieldMetaService.PAGE_LOCKED_REQUIRED['material'] 保持一致，改一处必须同步两处） */
const STATIC_REQUIRED = new Set(['materialName', 'materialType', 'baseUnitId', 'status'])

/**
 * 页面字段清单：fieldKey → 页面中文标签。
 * 用途：① 保存校验的字段范围与提示文案（标签与页面 $tf('...') 完全一致）
 *      ② 与后端 MaterialController.FILLABLE_LABELS（保存兜底白名单）保持同一口径，改一处必须同步两处。
 * 说明：元数据里的审计字段（createdAt/updatedAt/createdBy/updatedBy）与仅导入用字段不在本清单内——
 *      页面上没有输入口，即使被配成必填也无处可填（后端兜底同样不看它们）。
 */
const PAGE_FIELD_LABELS = {
  materialCode: '物料编码',
  materialName: '物料名称',
  fullName: '全称',
  model: '规格型号',
  materialType: '物料类型',
  baseUnitId: '基本单位',
  categoryId: '物料分类',
  salePrice: '销售单价',
  purchasePrice: '采购单价',
  taxRate: '税率',
  minStock: '最低库存',
  maxStock: '最高库存',
  purchaseBatchQty: '采购批量',
  minOrderQty: '最小订货量',
  defaultWarehouseId: '默认仓库',
  isLotControl: '批次管理',
  isSnControl: '序列号管理',
  isBinControl: '货位管理',
  isQcRequired: '质检要求',
  isExpiryControl: '保质期管理',
  expiryDays: '有效期天数',
  isConfidential: '保密物料',
  status: '状态',
  remark: '备注',
  imageUrls: '产品图片'
}

/**
 * 必填唯一真相源：模板 :required（星号）与保存校验都走这里。
 * 口径（用户 2026-09-15 定稿）：
 *  - 页面写死必填 OR 角色字段权限必填；
 *  - 物料编码特殊：自动编号开启时不要求用户填（后端保存时会生成），关闭时必须填 —— 避免与"留空自动生成"既有流程冲突；
 *  - 开关/数值类字段的 0 是合法值，只有 null/undefined/空白串才算"没填"。
 */
function isRequiredField(key) {
  if (key === 'materialCode') return autoCodeOn.value === false
  return STATIC_REQUIRED.has(key) || isFieldRequired(key)
}

/** 空值判定：null/undefined/纯空白串算空；数字 0 与布尔 false 都算已填 */
function isBlankFieldValue(v) {
  if (v === null || v === undefined) return true
  if (typeof v === 'string') return v.trim() === ''
  return false
}

/** 缺项清单（「保存并返回」时用；「保存为草稿」不校验，仅对数据库 NOT NULL 列兜底） */
function getMissingRequiredFields() {
  const missing = []
  Object.keys(PAGE_FIELD_LABELS).forEach(key => {
    if (!isRequiredField(key)) return
    if (isBlankFieldValue(form[key])) missing.push(tt(PAGE_FIELD_LABELS[key]))
  })
  // 自定义字段：定义里勾了必填 OR 角色字段权限必填（与单据页同口径）
  if (headerCustomFieldMap.size > 0) {
    headerCustomFieldMap.forEach((def, key) => {
      const need = (def && def.isRequired === 1) || isFieldRequired(key)
      if (!need) return
      if (isBlankFieldValue(form[key])) missing.push(tt((def && def.fieldLabel) || key))
    })
  }
  return missing
}

/** 保存主体（不含校验），供「保存并返回」与「保存为草稿」共用 */
async function doSave(opts = {}) {
  const headerValues = {}
  for (const key of headerCustomFieldMap.keys()) {
    if (key in form) headerValues[key] = form[key]
  }
  // 日产能用普通文本框输入，提交前归一为数值（空/非法按 0 = 不限制）
  form.dailyCapacity = Number(form.dailyCapacity || 0) || 0
  // 采购批量/最小订货量同样用普通文本框输入，提交前归一为数值（空/非法/负数按 0 = 不启用）
  form.purchaseBatchQty = Math.max(0, Number(form.purchaseBatchQty || 0) || 0)
  form.minOrderQty = Math.max(0, Number(form.minOrderQty || 0) || 0)
  // 辅计量换算明细随物料一起保存：开关开带当前行，开关关传空数组（后端整表删插 = 清空）
  const payload = { ...form, customFieldValues: headerValues, unitConversions: form.multiUnitEnabled ? conversionRows.value : [] }
  if (isEdit.value) {
    await updateMaterial(payload)
  } else {
    const res = await saveMaterial(payload)
    if (res && res.materialId) form.materialId = res.materialId

    // 新增态保存后把新单 id 写回地址栏：否则地址栏仍停在 /new，用户按 F5 会丢掉刚保存的草稿
    // skipRouteSync：提交链路里先跳过（写回会改变布局 keep-alive key(fullPath) → 组件实例重建）
    if (!opts.skipRouteSync && !route.params.id && form.materialId) {
      try { await router.replace({ name: 'MaterialEdit', params: { id: String(form.materialId) } }) } catch (_) {}
    }
  }
  // 换算关系已随物料保存：立即作废单据侧的换算行缓存，避免"改了档案、单据还是旧单位"
  invalidateConversions(form.materialId)
  // 替代货品清单随物料一起保存（整单全删全插；后端会校验与原货品基本单位一致）
  if (form.materialId) {
    await batchSaveSubstitutes({
      materialId: form.materialId,
      substituteMaterialIds: substitutes.value.map(s => s.substituteMaterialId)
    })
  }
  if (headerCustomFieldMap.size > 0 && form.materialId) {
    await saveCustomFieldValues('material', form.materialId, headerValues, headerFormulaFields.value)
  }
}

async function saveAndBack() {
  if (saving.value) return
  const missing = getMissingRequiredFields()
  if (missing.length) {
    ElMessage.warning(`${tt('请填写必填项：')}${missing.join('、')}`)
    return
  }
  saving.value = true
  try {
    await doSave()
    ElMessage.success(tt('保存成功'))
    goBack()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

/** 保存为草稿：不校验必填，仅对数据库 NOT NULL 列兜底，避免保存直接失败 */
async function saveDraft() {
  if (saving.value) return
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  if (!form.materialName) form.materialName = `（草稿）${ts}`
  if (!form.materialType) form.materialType = '自制件'
  if (!form.baseUnitId) {
    if (unitOptions.value.length === 0) await loadOptions()
    if (unitOptions.value.length > 0) form.baseUnitId = unitOptions.value[0].value
    else {
      ElMessage.warning(tt('请先在「计量单位」中维护单位，或填写基本单位'))
      return
    }
  }
  form.status = '停用' // 草稿标记（沿用原口径）
  saving.value = true
  try {
    await doSave()
    ElMessage.success(tt('保存成功'))
    goBack()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loadOptions()
  // ① 自动编号设置里"物料档案"的自动生成开关（开启=后端保存时会生成，页面不强制填）
  try {
    const res = await isAutoGenerate('MD_MATERIAL')
    serialAutoOn.value = (res === true || res?.data === true || Number(res?.data ?? res) === 1)
  } catch (_) {
    serialAutoOn.value = true // 取不到配置时按"自动编号开启"处理，避免误拦导致物料建不出来
  }
  // ② 物料编码规则（按分类配置）：任一启用中的规则存在 → 编号同样由系统生成，页面置灰只读
  try {
    const rules = await listCodeRule()
    const ruleList = Array.isArray(rules) ? rules : (rules?.records || rules?.data || [])
    materialRuleExists.value = ruleList.some(r => Number(r?.isEnabled) === 1)
  } catch (_) {
    materialRuleExists.value = false
  }
  // 角色字段权限（pageKey=material）：useFieldPermission 在 setup 阶段已发起加载，这里等待完成再渲染校验
  await loadFieldPermission('material').catch(() => {})
  await loadCustomFields()
  await loadData()
})
</script>

<style scoped lang="scss">
.material-edit-page {
  padding: 20px 24px;
  background: #fafafa;
  min-height: calc(100vh - 60px);
}

/* 顶部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  /* 顶部留白：加入二维码码牌后，避免码牌紧贴页头最上沿 */
  padding-top: 10px;
  padding-bottom: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
/* 页头右侧：按钮与二维码码牌同一水平线垂直居中（原为无样式 div，加入码牌后会基线错位） */
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 动作区与身份码之间的细分隔线 */
.header-split {
  width: 1px;
  height: 26px;
  background: #e4e7ed;
  flex: 0 0 auto;
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #6b7280;
  font-size: 12px;
  padding: 4px 0;
  &:hover { color: #6366f1; }
}
.back-arrow {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #4b5563;
  transition: all .2s;
}
.back-btn:hover .back-arrow {
  background: #eef2ff;
  border-color: #6366f1;
  color: #6366f1;
}
.title-group { display: flex; flex-direction: column; gap: 2px; }
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: -.3px;
}
.page-sub {
  font-size: 12px;
  color: #9ca3af;
}
.req { color: #ef4444; }

/* 表单卡片 */
.form-content {
  max-width: 1100px;
}
.form-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 24px 28px;
  margin-bottom: 16px;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f5f5f5;
}
.title-bar {
  width: 3px;
  height: 14px;
  background: #6366f1;
  border-radius: 2px;
}
.title-text { color: #1f2937; }
.title-sub {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
  margin-left: 8px;
}
.form-grid {
  display: grid;
  /* 标签在左、一行 3 个字段；!important 覆盖全局「按容器宽度自动排」规则 */
  grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
}
/* 辅计量换算页签（2026-11-10）：蓝色主题、小尺寸，随计量信息卡片内嵌 */
.conversion-tabs {
  margin-top: 12px;
  :deep(.el-tabs__header) { margin-bottom: 10px; }
  :deep(.el-tabs__item) {
    font-size: 13px;
    color: #6b7280;
    &.is-active { color: #4f6ef2; }
    &.is-active::after { background-color: #4f6ef2; }
  }
  :deep(.el-tabs__active-bar) { background-color: #4f6ef2; }
  :deep(.el-tabs__item:hover) { color: #4f6ef2; }
}
.conversion-toolbar {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 8px;
}
.conversion-table {
  width: 100%;
}
.conversion-tip {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}
.substitute-toolbar {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 8px;
}
.substitute-table {
  width: 100%;
}
.substitute-tip {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}
.form-grid :deep(.el-form-item) { margin-bottom: 0; }
.form-grid :deep(.el-form-item__label) {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
  height: auto;
}
.form-grid :deep(.el-form-item__content) {
  line-height: 1;
}
.form-grid :deep(.el-input__wrapper),
.form-grid :deep(.el-select__wrapper),
.form-grid :deep(.el-input-number) {
  box-shadow: 0 0 0 1px #e5e7eb inset;
  border-radius: 6px;
}
.form-grid :deep(.el-input__wrapper):hover,
.form-grid :deep(.el-select__wrapper):hover {
  box-shadow: 0 0 0 1px #c7d2fe inset;
}
.form-grid :deep(.el-input__wrapper.is-focus),
.form-grid :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6366f1 inset;
}

/* 确认区域 - 用网格布局展示 */
.confirm-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 0 24px;
}
.confirm-row {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
  align-items: center;
}
.confirm-label {
  width: 90px;
  color: #6b7280;
  flex-shrink: 0;
}
.confirm-value {
  color: #1f2937;
  font-weight: 500;
  flex: 1;
}

/* 底部 */
.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  margin-top: 8px;
}
.footer-info {
  font-size: 12px;
  color: #9ca3af;
}
.footer-actions { display: flex; gap: 8px; }

/* 按钮样式 */
:deep(.el-button) {
  border-radius: 6px;
  font-weight: 500;
}
:deep(.el-button--primary) {
  background: #6366f1;
  border-color: #6366f1;
  &:hover {
    background: #4f46e5;
    border-color: #4f46e5;
  }
}

/* 响应式 */
@media (max-width: 1100px) {
  .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; }
  .confirm-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 700px) {
  .form-grid { grid-template-columns: 1fr !important; }
  .confirm-grid { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>
