<template>
  <div class="serial-no-config-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head-info">
        <h2 class="page-title">{{ $tt("自动编号设置") }}</h2>
        <p class="page-desc">{{ $tt("统一管理各类单据的编号规则：前缀 + 日期 + 流水号，支持按业务域分组配置与实时预览。") }}</p>
      </div>
      <div class="page-head-actions">
        <el-input
          v-model="keyword"
          :placeholder="$tt('搜索单据类型 / 名称 / 前缀，跨分组查找')"
          clearable
          :prefix-icon="Search"
          class="search-input"
        />
        <el-button v-permission="perm('refresh')" :icon="Refresh" :loading="loading" @click="loadData">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- 业务域页签 -->
    <div class="domain-tabs">
      <button
        v-for="d in domainTabs"
        :key="d.key"
        class="domain-tab"
        :class="{ active: activeDomain === d.key && !keyword }"
        @click="switchDomain(d.key)"
      >
        {{ $tf(d.label) }}<span class="cnt">{{ d.count }}</span>
      </button>
    </div>

    <!-- 统计条 -->
    <div class="stat-bar">
      <template v-if="keyword">
        <span class="hit">关键词「{{ keyword }}」</span>
        <span>{{ $tt("匹配") }} <b>{{ visibleRules.length }}</b> {{ $tt("条规则") }}</span>
      </template>
      <template v-else>
        <span>{{ $tt("单据类型") }} <b>{{ visibleRules.length }}</b></span>
        <span><i class="stat-dot" style="background: var(--el-color-success)" /> {{ $tt("已启用") }} <b>{{ stat.enabled }}</b></span>
        <span><i class="stat-dot" style="background: var(--el-color-primary)" /> {{ $tt("自动生成") }} <b>{{ stat.auto }}</b></span>
        <span><i class="stat-dot" style="background: #a6b0bf" /> {{ $tt("手动录入") }} <b>{{ stat.manual }}</b></span>
      </template>
    </div>

    <!-- 物料编码规则分区（仅基础资料页签） -->
    <div v-if="showMaterialSection" class="rule-section">
      <div class="section-head">
        <div>
          <h3 class="section-title">{{ $tt("物料编码规则") }}</h3>
          <p class="section-desc">{{ $tt("按物料分类配置编码规则，") }}<b>{{ $tt("新建物料时优先生效") }}</b>；编码 = 前缀 + 各级分类编码 + 日期 + 流水号</p>
        </div>
        <div style="display:flex;align-items:center;gap:8px;">
          <el-button v-permission="perm('addRow')" type="primary" size="small" @click="openMaterialDialog(null)">{{ $tt("+ 新增规则") }}</el-button>
          <el-tooltip :content="$tt('列设置')" placement="top">
            <el-button :icon="Setting" circle size="small" @click="openMaterialCol" />
          </el-tooltip>
        </div>
      </div>
      <el-table ref="materialTableRef" :data="materialRules" v-loading="materialLoading" size="small" border class="section-table" :key="materialVer" @header-dragend="onMaterialDragend">
        <el-table-column type="index" :label="$tf('序号')" width="56" align="center" />
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
        <template v-for="col in materialVisible" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable || false"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getMaterialFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <template v-if="col.type === 'category'">
                <el-link type="primary" :underline="false" class="doc-name" @click="openMaterialDialog(row)">{{ row.categoryName || '通用规则' }}</el-link>
                <el-tag v-if="!row.categoryId" size="small" type="warning" style="margin-left: 6px">{{ $tt("兜底") }}</el-tag>
              </template>
              <template v-else-if="col.type === 'operation'">
                <el-link v-permission="perm('save')" type="primary" :underline="false" @click="openMaterialDialog(row)">{{ $tt("编辑") }}</el-link>
              </template>
              <span v-else-if="col.type === 'code'" class="code-text">{{ row.prefix || '（空）' }}</span>
              <el-tag v-else-if="col.type === 'includeCategoryCode'" :type="row.includeCategoryCode === 1 ? 'primary' : 'info'" size="small" effect="plain">
                {{ row.includeCategoryCode === 1 ? '拼接' : '不拼接' }}
              </el-tag>
              <el-tag v-else-if="col.type === 'includeDate'" :type="row.includeDate === 1 ? 'primary' : 'info'" size="small" effect="plain">
                {{ row.includeDate === 1 ? '含日期' : '无日期' }}
              </el-tag>
              <el-switch v-else-if="col.type === 'materialEnabled'" v-model="row.isEnabled" :active-value="1" :inactive-value="0" @change="onMaterialEnabledChange($event, row)" />
            </template>
          </el-table-column>
        </template>
      </el-table>
    </div>

    <!-- 常规编号规则分区 -->
    <div class="rule-section">
      <div class="section-head">
        <div>
          <h3 class="section-title">{{ keyword ? '搜索结果' : currentDomainLabel + ' · 编号规则' }}</h3>
          <p v-if="keyword" class="section-desc">跨全部分组匹配「{{ keyword }}」</p>
          <p v-else-if="activeDomain === 'basic'" class="section-desc">{{ $tt("基础资料编号规则；物料未命中分类规则时，回退使用") }} <b>{{ $tt("物料档案（MAT）") }}</b> {{ $tt("兜底规则") }}</p>
          <p v-else class="section-desc">{{ $tt("当前分组的单据编号规则，均支持实时预览与测试生成") }}</p>
        </div>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle size="small" @click="openRuleCol" />
        </el-tooltip>
      </div>
      <el-table ref="ruleTableRef" :data="visibleRules" v-loading="loading" size="small" border class="section-table" :key="ruleVer" @header-dragend="onRuleDragend">
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动，单元格按 col.type 分支渲染 -->
        <template v-for="col in ruleVisible" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable || false"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getRuleFixed(col.prop)"
          >
            <template v-if="col.type" #default="{ row }">
              <template v-if="col.type === 'docType'">
                <el-link type="primary" :underline="false" class="code-text" @click="openEdit(row)">{{ row.docType }}</el-link>
              </template>
              <template v-else-if="col.type === 'docName'">
                <span class="doc-name">{{ row.docName }}</span>
                <el-tag v-if="row.docType === 'MD_MATERIAL'" size="small" type="warning" style="margin-left: 6px">{{ $tt("兜底") }}</el-tag>
              </template>
              <span v-else-if="col.type === 'preview'" class="preview-no">
                <span class="pv-prefix">{{ row.prefix }}</span>
                <template v-for="(seg, i) in previewSegments(row)" :key="i">
                  <span class="pv-sep">{{ seg.sep }}</span>
                  <span v-if="seg.date" class="pv-date">{{ seg.date }}</span>
                  <span v-if="seg.seq" class="pv-seq">{{ seg.seq }}</span>
                </template>
              </span>
              <el-tag v-else-if="col.type === 'resetType'" size="small" :type="resetTypeTagType(row.resetType)" effect="plain">{{ resetTypeLabel(row.resetType) }}</el-tag>
              <el-switch
                v-else-if="col.type === 'autoGenerate'"
                v-model="row.autoGenerate"
                :active-value="1"
                :inactive-value="0"
                :disabled="row.enabled !== 1"
                @change="onAutoGenerateChange($event, row)"
              />
              <el-switch v-else-if="col.type === 'enabled'" v-model="row.enabled" :active-value="1" :inactive-value="0" @change="onEnabledChange($event, row)" />
            </template>
          </el-table-column>
        </template>
        <template #empty>
          <el-empty :image-size="80" :description="$tt('没有匹配的单据，换个关键词试试')" />
        </template>
      </el-table>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog :title="$tf('编辑编号规则')" v-model="dialogVisible" width="640px" @close="onDialogClose">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="96px" class="edit-form">
        <div class="dlg-grid">
          <el-form-item :label="$tf('单据名称')">
            <el-input v-model="form.docName" disabled />
          </el-form-item>
          <el-form-item :label="$tf('编号前缀')" prop="prefix">
            <el-input v-model="form.prefix" :placeholder="$tt('如：SO')" maxlength="20" />
          </el-form-item>
          <el-form-item :label="$tf('日期格式')" prop="dateFormat">
            <el-select v-model="form.dateFormat" style="width: 100%">
              <el-option :label="$tf('yyyyMMdd（如 20260829）')" value="yyyyMMdd" />
              <el-option :label="$tf('yyMMdd（如 260829）')" value="yyMMdd" />
              <el-option :label="$tf('yyyy-MM-dd（如 2026-08-29）')" value="yyyy-MM-dd" />
              <el-option :label="$tf('无日期')" value="" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$tf('分隔符')" prop="separator">
            <el-input v-model="form.separator" :placeholder="$tt('如：-')" maxlength="5" />
          </el-form-item>
          <el-form-item :label="$tf('流水号位数')" prop="serialLength">
            <el-input-number v-model="form.serialLength" :min="2" :max="10" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="$tf('重置类型')" prop="resetType">
            <el-select v-model="form.resetType" style="width: 100%">
              <el-option :label="$tf('不重置')" value="NONE" />
              <el-option :label="$tf('按日重置')" value="DAILY" />
              <el-option :label="$tf('按月重置')" value="MONTHLY" />
              <el-option :label="$tf('按年重置')" value="YEARLY" />
            </el-select>
          </el-form-item>
        </div>

        <div class="switch-panel">
          <div class="switch-row">
            <span class="switch-label">{{ $tt("自动生成") }}</span>
            <el-switch v-model="form.autoGenerate" :active-value="1" :inactive-value="0" />
            <span class="switch-hint">{{ $tt("自动：新建单据时系统自动填写编号并禁止修改；手动：编号由用户自行输入") }}</span>
          </div>
          <div class="switch-row">
            <span class="switch-label">{{ $tt("启用规则") }}</span>
            <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
            <span class="switch-hint">{{ $tt("禁用后该单据将改用系统默认编号规则") }}</span>
          </div>
        </div>

        <el-form-item :label="$tf('备注')" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" :placeholder="$tt('选填')" maxlength="200" show-word-limit />
        </el-form-item>

        <div class="preview-card">
          <div class="preview-card-label">{{ $tt("实时预览 · 下一张单据编号") }}</div>
          <div class="preview-card-value">{{ previewSerialNo }}</div>
          <div class="preview-card-tip">{{ resetTypeTip }}</div>
        </div>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="onSubmit" :loading="submitting">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 物料编码规则弹窗 -->
    <el-dialog v-model="materialDialogVisible" :title="materialDialogTitle" width="560px" @close="resetMaterialForm">
      <el-form ref="materialFormRef" :model="materialForm" :rules="materialFormRules" label-width="110px">
        <el-form-item :label="$tf('物料分类')" prop="categoryId">
          <el-select v-model="materialForm.categoryId" :placeholder="$tt('留空表示通用规则')" filterable clearable style="width: 100%" @change="onMaterialCategoryChange">
            <el-option v-for="c in categoryOptions" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId" />
          </el-select>
          <div class="form-hint">{{ $tt("不选分类则创建通用规则（所有分类兜底使用）") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('前缀')" prop="prefix">
          <el-input v-model="materialForm.prefix" :placeholder="$tt('如：RM（开启拼接分类编码时可留空）')" maxlength="20" />
          <div class="form-hint">编码开头部分；若开启"拼接分类编码"可留空</div>
        </el-form-item>
        <el-form-item :label="$tf('拼接分类编码')">
          <el-switch v-model="materialForm.includeCategoryCode" :active-value="1" :inactive-value="0" />
          <div class="form-hint">开启后编码 = 前缀 + 各级分类编码依次拼接 + [日期] + 流水号</div>
        </el-form-item>
        <el-form-item :label="$tf('包含日期')">
          <el-switch v-model="materialForm.includeDate" :active-value="1" :inactive-value="0" />
          <div class="form-hint">{{ $tt("开启后编码中插入 yyyyMMdd 日期段") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('流水位数')" prop="seqLength">
          <el-input-number v-model="materialForm.seqLength" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$tf('是否启用')">
          <el-switch v-model="materialForm.isEnabled" :active-value="1" :inactive-value="0" />
          <span class="switch-hint" style="margin-left: 8px">{{ $tt("停用后该分类新建物料回退兜底规则") }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="materialDialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-permission="perm('save')" type="primary" @click="onSaveMaterialRule" :loading="materialSaving">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>

    <!-- 测试生成弹窗 -->
    <el-dialog :title="$tf('测试生成编号')" v-model="testDialogVisible" width="480px">
      <el-form label-width="90px">
        <el-form-item :label="$tf('单据')">
          <span>{{ testRow?.docName }}（<span class="code-text">{{ testRow?.docType }}</span>）</span>
        </el-form-item>
        <el-form-item :label="$tf('生成数量')">
          <el-input-number v-model="testCount" :min="1" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item :label="$tf('生成结果')">
          <div class="test-result-box">
            <div v-for="(no, index) in testResults" :key="index" class="test-result-item">
              <span class="no">#{{ index + 1 }}</span>
              <span class="gen-no">{{ no }}</span>
            </div>
            <el-empty v-if="testResults.length === 0" :description="$tt('点击「开始生成」查看连续编号效果')" :image-size="70" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgClose')" @click="testDialogVisible = false">{{ $tt("关闭") }}</el-button>
        <el-button v-permission="perm('start')" type="primary" @click="onDoTestGenerate" :loading="testLoading">{{ $tt("开始生成") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:serial-no')
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, Setting } from '@element-plus/icons-vue'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import {
  getSerialNoConfigList,
  updateSerialNoConfig,
  generateSerialNo
} from '@/api/system/serialNoConfig'
import { listCodeRule, saveCodeRule } from '@/api/basedata/material-code-rule'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import { tt } from '@/i18n'

/* ═══ 业务域分组（纯前端展示逻辑，未识别类型落入"其他"） ═══ */
const DOMAINS = [
  { key: 'basic', label: '基础资料' },
  { key: 'crm', label: 'CRM' },
  { key: 'sale', label: '销售' },
  { key: 'purchase', label: '采购' },
  { key: 'inventory', label: '库存' },
  { key: 'production', label: '生产' },
  { key: 'subcontract', label: '委外' },
  { key: 'finance', label: '财务' },
  { key: 'other', label: '其他' }
]
const DOMAIN_EXPLICIT = {
  BANK_ACCOUNT: 'basic', BOM: 'basic', CUSTOMER: 'basic', EMPLOYEE: 'basic', MATERIAL: 'basic',
  SUPPLIER: 'basic', BD_MATERIAL_CATEGORY: 'basic', SYS_ROLE: 'basic', WF_APPROVAL_FLOW: 'basic',
  MD_BOM: 'basic', MD_CUSTOMER: 'basic', MD_MATERIAL: 'basic', MD_SUPPLIER: 'basic',
  BD_CURRENCY: 'basic', BD_DEPARTMENT: 'basic', BD_EMPLOYEE: 'basic', BD_UNIT: 'basic', BD_WAREHOUSE: 'basic',
  CONTACT: 'crm', LEAD: 'crm', OPPORTUNITY: 'crm', SAMPLE_ORDER: 'crm', FREE_SAMPLE_ORDER: 'crm',
  MATERIAL_REQUISITION: 'production', MRP_PLAN: 'production', OPERATION_REPORT: 'production',
  WORK_ORDER: 'production',
  OPERATION_EXPENSE: 'finance',
  OTHER_ISSUE: 'inventory', OTHER_RECEIPT: 'inventory',
  RECEIPT: 'finance', PAYMENT: 'finance'
}
const DOMAIN_PREFIX = [
  [/^BD_|^MD_/, 'basic'],
  [/^SALE_/, 'sale'],
  [/^PURCHASE_/, 'purchase'],
  [/^STOCK_/, 'inventory'],
  [/^PRODUCTION_|^PRODUCE_|^PROCESS_|^OPERATION_/, 'production'],
  [/^SUBCONTRACT_/, 'subcontract'],
  [/^ACCOUNT_/, 'finance']
]
function domainOf(docType) {
  if (DOMAIN_EXPLICIT[docType]) return DOMAIN_EXPLICIT[docType]
  for (const [re, key] of DOMAIN_PREFIX) {
    if (re.test(docType)) return key
  }
  return 'other'
}

/* ═══ 状态 ═══ */
const loading = ref(false)
const tableData = ref([])
const activeDomain = ref('basic')
const keyword = ref('')

// 列定义（列设置 + 动态渲染用）：两张表各用独立 pageKey
// 物料编码规则表：type=category 分类链接、operation 操作链接、code 前缀、includeCategoryCode/includeDate 标签、materialEnabled 开关
const materialColumns = [
  { prop: 'categoryName', label: '物料分类', minWidth: 150, type: 'category' },
  { prop: '_operation', label: '操作', width: 80, align: 'center', type: 'operation' },
  { prop: 'prefix', label: '前缀', width: 90, type: 'code' },
  { prop: 'includeCategoryCode', label: '拼接分类编码', width: 110, align: 'center', type: 'includeCategoryCode' },
  { prop: 'includeDate', label: '包含日期', width: 90, align: 'center', type: 'includeDate' },
  { prop: 'seqLength', label: '流水位数', width: 90, align: 'right' },
  { prop: 'lastSeq', label: '当前流水', width: 90, align: 'right' },
  { prop: 'isEnabled', label: '是否启用', width: 90, align: 'center', type: 'materialEnabled' }
]
// 编号规则表：type=docType 单据类型链接、docName 名称、preview 预览、resetType 标签、autoGenerate/enabled 开关
const ruleColumns = [
  { prop: 'docType', label: '单据类型', width: 170, sortable: true, type: 'docType' },
  { prop: 'docName', label: '单据名称', width: 160, sortable: true, type: 'docName' },
  { prop: '_preview', label: '编号规则预览', minWidth: 180, type: 'preview' },
  { prop: 'resetType', label: '重置类型', width: 100, align: 'center', sortable: true, type: 'resetType' },
  { prop: 'currentSerial', label: '当前流水', width: 96, align: 'right', sortable: true },
  { prop: 'autoGenerate', label: '自动生成', width: 104, align: 'center', type: 'autoGenerate' },
  { prop: 'enabled', label: '启用', width: 88, align: 'center', type: 'enabled' }
]
const { columnSettingVisible: materialColVisible, openColumnSetting: openMaterialCol, isColVisible: isMaterialCol, getColFixed: getMaterialFixed, configVersion: materialVer, sortedColumns: materialSorted, onHeaderDragend: onMaterialDragend, applySavedWidths: applyMaterialWidths } = useColumnSetting('SerialNoConfig-material', materialColumns)
const materialTableRef = ref(null)
const materialVisible = computed(() => materialSorted.value.filter(c => isMaterialCol(c.prop)))
const { columnSettingVisible: ruleColVisible, openColumnSetting: openRuleCol, isColVisible: isRuleCol, getColFixed: getRuleFixed, configVersion: ruleVer, sortedColumns: ruleSorted, onHeaderDragend: onRuleDragend, applySavedWidths: applyRuleWidths } = useColumnSetting('SerialNoConfig-rule', ruleColumns)
const ruleTableRef = ref(null)
const ruleVisible = computed(() => ruleSorted.value.filter(c => isRuleCol(c.prop)))

const domainTabs = computed(() =>
  DOMAINS.map(d => ({
    ...d,
    count: tableData.value.filter(r => domainOf(r.docType) === d.key).length
  }))
)

const visibleRules = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    return tableData.value.filter(r =>
      [r.docType, r.docName, r.prefix].some(v => String(v || '').toLowerCase().includes(kw))
    )
  }
  return tableData.value.filter(r => domainOf(r.docType) === activeDomain.value)
})

const stat = computed(() => {
  const list = visibleRules.value
  const enabled = list.filter(r => r.enabled === 1).length
  const auto = list.filter(r => r.enabled === 1 && r.autoGenerate === 1).length
  return { enabled, auto, manual: list.length - auto }
})

const currentDomainLabel = computed(() =>
  (DOMAINS.find(d => d.key === activeDomain.value) || {}).label || ''
)

const showMaterialSection = computed(() => activeDomain.value === 'basic' && !keyword.value.trim())

/* ═══ 编号预览 ═══ */
function fmtDate(pattern) {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  if (pattern === 'yyyyMMdd') return `${y}${m}${day}`
  if (pattern === 'yyMMdd') return String(y).slice(-2) + m + day
  if (pattern === 'yyyy-MM-dd') return `${y}-${m}-${day}`
  return ''
}

function previewSegments(row) {
  const segs = []
  const date = fmtDate(row.dateFormat)
  const sep = row.separator || ''
  const seq = String((row.currentSerial || 0) + 1).padStart(row.serialLength || 4, '0')
  if (date) segs.push({ sep, date })
  segs.push({ sep, seq })
  return segs
}

function resetTypeLabel(type) {
  const map = { NONE: '不重置', DAILY: '按日', MONTHLY: '按月', YEARLY: '按年' }
  return map[type] || type || '-'
}
function resetTypeTagType(type) {
  const map = { NONE: 'info', DAILY: 'warning', MONTHLY: 'primary', YEARLY: 'success' }
  return map[type] || 'info'
}

/* ═══ 数据加载 ═══ */
async function loadData() {
  loading.value = true
  try {
    const res = await getSerialNoConfigList()
    const list = Array.isArray(res) ? res : (res?.records || res?.data || [])
    // MD_MATERIAL 不再隐藏：作为物料编码的兜底规则展示
    tableData.value = list
  } catch (e) {
    tableData.value = []
    ElMessage.error(tt('加载单据编号配置失败'))
  } finally {
    loading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applyRuleWidths(ruleTableRef))
  }
}

function switchDomain(key) {
  activeDomain.value = key
  keyword.value = ''
}

/* ═══ 行内开关（直接保存） ═══ */
async function onEnabledChange(val, row) {
  try {
    await updateSerialNoConfig({ ...row, enabled: val })
    ElMessage.success(`${val === 1 ? tt('已启用') : tt('已停用')}「${row.docName}${tt("」编号规则")}`)
  } catch (e) {
    row.enabled = val === 1 ? 0 : 1
  }
}

async function onAutoGenerateChange(val, row) {
  try {
    await updateSerialNoConfig({ ...row, autoGenerate: val })
    ElMessage.success(`「${row.docName}${tt("」已切换为")}${val === 1 ? tt('自动生成') : tt('手动录入')}${tt("编号")}`)
  } catch (e) {
    row.autoGenerate = val === 1 ? 0 : 1
  }
}

/* ═══ 编辑弹窗 ═══ */
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = {
  id: null, docType: '', docName: '', prefix: '', dateFormat: 'yyyyMMdd',
  serialLength: 4, resetType: 'MONTHLY', separator: '-', currentSerial: 0,
  currentDate: '', enabled: 1, autoGenerate: 1, sortOrder: 0, remark: ''
}
const form = reactive({ ...defaultForm })

const formRules = {
  prefix: [{ required: true, message: tt('请输入编号前缀'), trigger: 'blur' }],
  serialLength: [{ required: true, message: tt('请输入流水号位数'), trigger: 'blur' }],
  resetType: [{ required: true, message: tt('请选择重置类型'), trigger: 'change' }]
}

const previewSerialNo = computed(() => {
  const parts = []
  if (form.prefix) parts.push(form.prefix)
  const date = fmtDate(form.dateFormat)
  if (date) parts.push(date)
  parts.push(String((form.currentSerial || 0) + 1).padStart(form.serialLength || 4, '0'))
  return parts.join(form.separator || '')
})

const RESET_TIP = {
  NONE: '流水号持续累加，不自动重置',
  DAILY: '流水号将在每日 0 点重置为 0001',
  MONTHLY: '流水号将在下月 1 日重置为 0001',
  YEARLY: '流水号将在每年 1 月 1 日重置为 0001'
}
const resetTypeTip = computed(() => RESET_TIP[form.resetType] || '')

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    docType: row.docType,
    docName: row.docName,
    prefix: row.prefix,
    dateFormat: row.dateFormat,
    serialLength: row.serialLength,
    resetType: row.resetType,
    separator: row.separator,
    currentSerial: row.currentSerial,
    currentDate: row.currentDate,
    enabled: row.enabled,
    autoGenerate: row.autoGenerate,
    sortOrder: row.sortOrder,
    remark: row.remark
  })
  dialogVisible.value = true
}

function onDialogClose() {
  formRef.value && formRef.value.resetFields()
  Object.assign(form, defaultForm)
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await updateSerialNoConfig({ ...form })
      ElMessage.success(tt('保存成功'))
      dialogVisible.value = false
      loadData()
    } catch (e) {
      // 错误已在拦截器处理
    } finally {
      submitting.value = false
    }
  })
}

/* ═══ 测试生成 ═══ */
const testDialogVisible = ref(false)
const testRow = ref(null)
const testCount = ref(5)
const testLoading = ref(false)
const testResults = ref([])

async function onDoTestGenerate() {
  if (!testRow.value) return
  testLoading.value = true
  testResults.value = []
  try {
    const promises = []
    for (let i = 0; i < testCount.value; i++) {
      promises.push(generateSerialNo(testRow.value.docType))
    }
    const results = await Promise.all(promises)
    testResults.value = results.map(r => {
      if (typeof r === 'string') return r
      return r?.serialNo || r?.data?.serialNo || JSON.stringify(r)
    })
  } catch (e) {
    ElMessage.error(tt('生成失败：') + (e.message || tt('请检查后端服务')))
  } finally {
    testLoading.value = false
  }
}

/* ═══ 物料编码规则分区（原 MaterialCodeRuleList 功能并入） ═══ */
const materialRules = ref([])
const materialLoading = ref(false)
const categoryOptions = ref([])
const materialDialogVisible = ref(false)
const materialDialogTitle = ref('')
const materialFormRef = ref()
const materialSaving = ref(false)

const materialForm = reactive({
  ruleId: null, categoryId: null, categoryName: '', prefix: '',
  includeCategoryCode: 0, includeDate: 0, seqLength: 4, isEnabled: 1, lastSeq: 0
})
const materialFormRules = {
  seqLength: [{ required: true, message: tt('请输入流水位数'), trigger: 'blur' }]
}

async function loadMaterialRules() {
  materialLoading.value = true
  try {
    const res = await listCodeRule()
    materialRules.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    materialRules.value = []
    ElMessage.error(e.message || tt('加载物料编码规则失败'))
  } finally {
    materialLoading.value = false
    // 数据到位后再套一次已保存的列宽（表格重建后列对象是新的）
    nextTick(() => applyMaterialWidths(materialTableRef))
  }
}

async function loadCategoryOptions() {
  try {
    const res = await listAllMaterialCategory()
    categoryOptions.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    console.warn('加载物料分类失败', e)
  }
}

function openMaterialDialog(row) {
  if (row) {
    Object.assign(materialForm, {
      ruleId: row.ruleId,
      categoryId: row.categoryId,
      categoryName: row.categoryName || '',
      prefix: row.prefix || '',
      includeCategoryCode: row.includeCategoryCode != null ? row.includeCategoryCode : 0,
      includeDate: row.includeDate != null ? row.includeDate : 0,
      seqLength: row.seqLength || 4,
      isEnabled: row.isEnabled != null ? row.isEnabled : 1,
      lastSeq: row.lastSeq || 0
    })
    materialDialogTitle.value = `编辑物料编码规则 · ${row.categoryName || '通用规则'}`
  } else {
    Object.assign(materialForm, {
      ruleId: null, categoryId: null, categoryName: '', prefix: '',
      includeCategoryCode: 0, includeDate: 0, seqLength: 4, isEnabled: 1, lastSeq: 0
    })
    materialDialogTitle.value = '新增物料编码规则'
  }
  materialDialogVisible.value = true
}

function onMaterialCategoryChange(v) {
  const c = categoryOptions.value.find(x => x.categoryId === v)
  materialForm.categoryName = c ? c.categoryName : ''
}

async function onSaveMaterialRule() {
  await materialFormRef.value?.validate()
  materialSaving.value = true
  try {
    await saveCodeRule({ ...materialForm })
    ElMessage.success(tt('保存成功'))
    materialDialogVisible.value = false
    loadMaterialRules()
  } catch (e) {
    ElMessage.error(e.message || tt('保存失败'))
  } finally {
    materialSaving.value = false
  }
}

async function onMaterialEnabledChange(val, row) {
  try {
    await saveCodeRule({ ...row, isEnabled: val })
    ElMessage.success(`${val === 1 ? tt('已启用') : tt('已停用')}「${row.categoryName || tt('通用')}${tt("」编码规则")}`)
  } catch (e) {
    row.isEnabled = val === 1 ? 0 : 1
    ElMessage.error(e.message || tt('操作失败'))
  }
}

function resetMaterialForm() {
  materialFormRef.value && materialFormRef.value.resetFields()
}

onMounted(() => {
  loadData()
  loadMaterialRules()
  loadCategoryOptions()
})
</script>

<style scoped lang="scss">
.serial-no-config-page {
  padding: 16px;
}

/* ═══ 页头 ═══ */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2733;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #7a8699;
  max-width: 560px;
}
.page-head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.search-input {
  width: 300px;
}

/* ═══ 业务域页签 ═══ */
.domain-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #e4e8ef;
  padding: 5px;
  border-radius: 10px;
  margin-bottom: 12px;
}
.domain-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #7a8699;
  font-weight: 500;
  border: none;
  background: none;
  transition: all 0.18s;
  font-family: inherit;

  &:hover {
    background: #f0f4fa;
    color: #1f2733;
  }
  &.active {
    background: var(--el-color-primary);
    color: #fff;
  }
  .cnt {
    font-family: Consolas, 'Courier New', monospace;
    font-size: 11px;
    background: rgba(122, 134, 153, 0.14);
    color: #7a8699;
    border-radius: 20px;
    padding: 0 7px;
    line-height: 17px;
  }
  &.active .cnt {
    background: rgba(255, 255, 255, 0.22);
    color: #fff;
  }
}

/* ═══ 统计条 ═══ */
.stat-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 12.5px;
  color: #7a8699;
  padding: 0 4px 12px;

  b {
    color: #1f2733;
    font-weight: 600;
    font-family: Consolas, 'Courier New', monospace;
  }
  .hit {
    color: var(--el-color-primary);
    font-weight: 500;
  }
  .stat-dot {
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    margin-right: 4px;
    vertical-align: middle;
  }
}

/* ═══ 分区 ═══ */
.rule-section {
  margin-bottom: 18px;
}
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 10px;
}
.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2733;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-desc {
  margin: 3px 0 0;
  font-size: 12px;
  color: #7a8699;

  b {
    color: #e6a23c;
    font-weight: 600;
  }
}
.section-table {
  width: 100%;
}

/* ═══ 编号预览 ═══ */
.code-text {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  font-weight: 500;
}
.doc-name {
  font-weight: 500;
}
.preview-no {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;

  .pv-prefix { color: var(--el-color-primary); font-weight: 600; }
  .pv-sep { color: #a6b0bf; }
  .pv-date { color: #7a8699; }
  .pv-seq { color: var(--el-color-success); font-weight: 600; }
}

/* ═══ 编辑弹窗 ═══ */
.dlg-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.edit-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.switch-panel {
  background: #fafbfd;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;

  & + .switch-row {
    margin-top: 10px;
  }
}
.switch-label {
  font-weight: 500;
  width: 60px;
  flex: none;
}
.switch-hint {
  font-size: 12px;
  color: #a6b0bf;
}
.form-hint {
  font-size: 12px;
  color: #a6b0bf;
  line-height: 1.5;
  margin-top: 4px;
  width: 100%;
}

/* 预览卡 */
.preview-card {
  margin-top: 4px;
  border: 1px dashed #d4dae4;
  background: linear-gradient(135deg, #fafcff, #f3f7fe);
  border-radius: 10px;
  padding: 14px 16px;
}
.preview-card-label {
  font-size: 12px;
  color: #7a8699;
  font-weight: 500;
  margin-bottom: 8px;
}
.preview-card-value {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--el-color-primary);
}
.preview-card-tip {
  font-size: 12px;
  color: #a6b0bf;
  margin-top: 6px;
}

/* ═══ 测试结果 ═══ */
.test-result-box {
  width: 100%;
  max-height: 240px;
  overflow-y: auto;
  padding: 8px 12px;
  background: #fafbfd;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
.test-result-item {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-family: Consolas, 'Courier New', monospace;
  color: #303133;
  border-bottom: 1px dashed #ebeef5;

  &:last-child {
    border-bottom: none;

    .gen-no {
      color: var(--el-color-primary);
      font-weight: 600;
    }
  }
  .no {
    color: #a6b0bf;
    font-size: 12px;
  }
}
</style>
