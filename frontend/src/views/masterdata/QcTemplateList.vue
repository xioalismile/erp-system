<template>
  <!-- 基础资料 → 质检模板：列表（通用列表壳）+ 编辑弹窗（模板头 + 检验项明细行可增删改） -->
  <BillListPage
    page-class="qc-template-page"
    back-path="/basedata"
    :title="$tt('质检模板')"
    badge="QC"
    sub-title="可维护的检验标准：可绑多个物料 / 多个分类 / 一个工序 · 默认不绑定"
    search-placeholder="输入模板编码/名称/适用工序搜索"
    keyword-label="模板编码/名称"
    :filters="['status']"
    :status-options="statusOptions"
    :filter-model="search"
    :page-sizes="[20, 50, 100]"
    :table-data="tableData"
    :loading="loading"
    :pagination="pagination"
    storage-key="QcTemplateList"
    :columns="columns"
    :table-key="configVersion"
    v-model:keyword="search.keyword"
    v-model:selected-rows="selectedRows"
    v-model:column-setting-visible="columnSettingVisible"
    @search="handleSearch"
    @reset="handleReset"
    @filter-change="onFilterChange"
    @page-change="fetchData"
  >
    <template #header-actions>
      <el-button type="primary" class="btn-primary-glow" v-permission="perm('add')" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增质检模板
      </el-button>
    </template>

    <el-table-column type="index" label="#" width="60" />
    <el-table-column v-if="isColVisible('templateCode')" prop="templateCode" :label="$tf('模板编码')" width="150" :fixed="getColFixed('templateCode')">
      <template #default="{ row }">
        <div class="order-no-cell">
          <span class="order-no-text" @click="handleEdit(row)">{{ row.templateCode }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column v-if="isColVisible('templateName')" prop="templateName" :label="$tf('模板名称')" min-width="180" show-overflow-tooltip />
    <!-- 模板类型：物料质检模板（按物料/分类/工序绑定） / 单据质检模板（按单据类型绑定，入库单用它） -->
    <el-table-column v-if="isColVisible('templateType')" prop="templateType" :label="$tf('模板类型')" width="130" align="center">
      <template #default="{ row }">
        <el-tag size="small" :type="row.templateType === 'BILL' ? 'warning' : 'info'" effect="light">
          {{ row.templateType === 'BILL' ? $tt('单据质检模板') : $tt('物料质检模板') }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column v-if="isColVisible('billTypes')" prop="billTypes" :label="$tf('适用单据')" min-width="200">
      <template #default="{ row }">
        <div v-if="row.templateType === 'BILL' && row.billTypes" class="bind-tags">
          <el-tag v-for="b in String(row.billTypes).split(',')" :key="b" size="small" type="warning" effect="plain">
            {{ billTypeLabel(b) }}
          </el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column v-if="isColVisible('qcType')" prop="qcType" :label="$tf('质检类型')" width="100" align="center">
      <template #default="{ row }">
        <el-tag size="small" :type="qcTypeTag(row.qcType)" effect="light">{{ $dt(row.qcType || '首检') }}</el-tag>
      </template>
    </el-table-column>
    <!-- 适用物料：独立明细列 —— 逐个标签换行铺开，不用悬停也能一眼看全 -->
    <el-table-column v-if="isColVisible('materialNames')" prop="materialNames" :label="$tf('适用物料')" min-width="240">
      <template #default="{ row }">
        <div v-if="(row.materialNames || []).length" class="bind-tags">
          <el-tag v-for="(n, i) in row.materialNames" :key="i" size="small" type="info" effect="plain">{{ n }}</el-tag>
        </div>
      </template>
    </el-table-column>
    <!-- 适用分类：独立明细列（挂分类时该分类及其下级分类的物料都适用） -->
    <el-table-column v-if="isColVisible('categoryNames')" prop="categoryNames" :label="$tf('适用分类')" min-width="180">
      <template #default="{ row }">
        <div v-if="(row.categoryNames || []).length" class="bind-tags">
          <el-tag v-for="(n, i) in row.categoryNames" :key="i" size="small" type="success" effect="plain">{{ n }}</el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column v-if="isColVisible('operationName')" prop="operationName" :label="$tf('适用工序')" min-width="140" show-overflow-tooltip />
    <el-table-column v-if="isColVisible('sampleRatio')" prop="sampleRatio" :label="$tf('抽样比例(%)')" width="120" align="right">
      <template #default="{ row }">{{ row.qcType === '抽检' ? num(row.sampleRatio) : '' }}</template>
    </el-table-column>
    <el-table-column v-if="isColVisible('allowedDefectRatio')" prop="allowedDefectRatio" :label="$tf('允许不良率(%)')" width="130" align="right">
      <template #default="{ row }">{{ row.qcType === '抽检' ? num(row.allowedDefectRatio) : '' }}</template>
    </el-table-column>
    <el-table-column v-if="isColVisible('itemCount')" prop="itemCount" :label="$tf('检验项')" width="90" align="center">
      <template #default="{ row }">{{ row.itemCount ?? 0 }}</template>
    </el-table-column>
    <el-table-column v-if="isColVisible('isEnabled')" prop="isEnabled" :label="$tf('状态')" width="90" align="center">
      <template #default="{ row }">
        <div class="status-cell">
          <span :class="['status-dot', row.isEnabled === 1 ? 'dot-success' : 'dot-danger']"></span>
          <el-tag size="small" :type="row.isEnabled === 1 ? 'success' : 'danger'" effect="light">
            {{ row.isEnabled === 1 ? $tt('启用') : $tt('停用') }}
          </el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column v-if="isColVisible('remark')" prop="remark" :label="$tf('备注')" min-width="140" show-overflow-tooltip />
    <AuditColumns :visible="isColVisible('createdAt')" />
    <el-table-column :label="$tf('操作')" width="140" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link v-permission="perm('edit')" @click="handleEdit(row)">{{ $tt('编辑') }}</el-button>
        <el-button type="danger" link v-permission="perm('delete')" @click="handleDelete(row)">{{ $tt('删除') }}</el-button>
      </template>
    </el-table-column>
  </BillListPage>

  <!-- 新增/编辑弹窗：模板头 + 检验项明细 -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1080px" @close="resetForm">
    <el-form :model="form" label-width="110px" :rules="rules" ref="formRef">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-form-item :label="$tf('模板编码')">
            <el-input v-model="form.templateCode" :placeholder="$tt('留空自动生成')" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('模板名称')" prop="templateName">
            <el-input v-model="form.templateName" :placeholder="$tt('如：外观检验标准A')" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('模板类型')" prop="templateType">
            <el-select v-model="form.templateType" style="width:100%">
              <el-option :label="$tf('物料质检模板')" value="MATERIAL" />
              <el-option :label="$tf('单据质检模板')" value="BILL" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('质检类型')" prop="qcType">
            <el-select v-model="form.qcType" style="width:100%">
              <el-option :label="$tf('首检')" value="首检" />
              <el-option :label="$tf('抽检')" value="抽检" />
              <el-option :label="$tf('全检')" value="全检" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 单据质检模板：绑定单据类型（可绑定多个），入库单点「质检」时从这里选择模板 -->
      <el-row v-if="form.templateType === 'BILL'" :gutter="20">
        <el-col :span="12">
          <el-form-item :label="$tf('适用单据')" prop="billTypes">
            <el-select v-model="form.billTypes" multiple filterable clearable collapse-tags collapse-tags-tooltip
                       style="width:100%" :placeholder="$tt('选择适用的入库单类型，可多选')">
              <el-option v-for="b in BILL_TYPE_OPTIONS" :key="b.value" :label="$tf(b.label)" :value="b.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('默认模板')">
            <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('状态')">
            <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="form.templateType !== 'BILL'" :gutter="20">
        <el-col :span="12">
          <el-form-item :label="$tf('适用物料')">
            <div class="qc-chips">
              <el-tag v-for="id in form.materialIds" :key="id" closable size="small" @close="removeMaterial(id)">
                {{ materialLabelById(id) }}
              </el-tag>
              <el-button size="small" @click="materialPickerVisible = true">
                <el-icon><Plus /></el-icon>从物料库选择
              </el-button>
              <span class="qc-hint">{{ $tt("支持多选：搜索 / 按分类筛选 / 自定义字段") }}</span>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('适用工序')">
            <!-- 工序的唯一维护入口是「生产管理 → 工艺路线」：这里只列工艺路线里配置过的工序，
                 不提供手输新增（手输会造出与工艺路线不一致、永远匹配不上的工序名） -->
            <el-select v-model="form.operationName" filterable clearable default-first-option
                       style="width:100%" :placeholder="$tt('选择工艺路线里的工序')"
                       :empty-text="$tt('工艺路线里还没有配置工序')">
              <el-option v-for="w in operationOptions" :key="w" :label="$tf(w)" :value="w" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item :label="$tf('状态')">
            <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="form.templateType !== 'BILL'" :gutter="20">
        <el-col :span="12">
          <el-form-item :label="$tf('适用分类')">
            <el-select v-model="form.categoryIds" multiple filterable clearable collapse-tags collapse-tags-tooltip
                       style="width:100%" :placeholder="$tt('按分类绑定：该分类及其下级分类的物料都适用（可选）')">
              <el-option v-for="c in categoryOptions" :key="c.value" :label="$tf(c.label)" :value="c.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <div class="qc-hint qc-hint-block">
            {{ $tt("匹配顺序：物料+工序 > 仅物料 > 仅工序 > 分类；同一「物料/分类＋工序」只允许一条启用模板") }}
          </div>
        </el-col>
      </el-row>

      <el-row v-if="form.qcType === '抽检'" :gutter="20">
        <el-col :span="8">
          <el-form-item :label="$tf('抽样比例(%)')">
            <el-input-number v-model="form.sampleRatio" :min="0" :max="100" :precision="2" :controls="false" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item :label="$tf('允许不良率(%)')">
            <el-input-number v-model="form.allowedDefectRatio" :min="0" :max="100" :precision="2" :controls="false" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <div class="qc-hint">{{ $tt("抽样数 = 报工数量 × 抽样比例；不良率 ≤ 允许不良率则整批放行") }}</div>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item :label="$tf('备注')">
            <el-input v-model="form.remark" :placeholder="$tt('选填')" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 检验项明细：可增删改 -->
      <el-divider content-position="left">{{ $tt("检验项") }}</el-divider>
      <div class="qc-items-toolbar">
        <el-button type="primary" plain size="small" @click="addItem">
          <el-icon><Plus /></el-icon>添加检验项
        </el-button>
        <span class="qc-hint">{{ $tt("项目名 + 检验方法 + 标准值 + 上下限 + 单位 + 是否必填") }}</span>
      </div>
      <el-table :data="form.items" border size="small" :empty-text="$tt('还没有检验项，请点「添加检验项」')">
        <el-table-column type="index" :label="$tf('行号')" width="60" />
        <el-table-column :label="$tf('项目名')" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.itemName" size="small" :placeholder="$tt('必填')" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('检验方法')" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.inspectMethod" size="small" :placeholder="$tt('如：目视/卡尺')" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('标准值')" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.standardValue" size="small" :placeholder="$tt('如：无色差')" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('下限')" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.lowerLimit" :precision="4" :controls="false" size="small" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('上限')" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.upperLimit" :precision="4" :controls="false" size="small" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('单位')" width="90">
          <template #default="{ row }">
            <el-input v-model="row.unit" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('必填')" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isRequired" :active-value="1" :inactive-value="0" size="small" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="80" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeItem($index)">{{ $tt('删除') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $tt('取消') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ $tt('保存') }}</el-button>
    </template>
  </el-dialog>

  <!-- 物料库选择（通用件）：多选、可搜索、可按分类筛选 -->
  <MaterialPickerDialog v-model="materialPickerVisible" :title="$tt('从物料库选择（可多选）')" @confirm="onMaterialsPicked" />
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listQcTemplates, getQcTemplate, createQcTemplate, updateQcTemplate, deleteQcTemplate
} from '@/api/qc/qcTemplate'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllMaterialCategory } from '@/api/basedata/material-category'
import { listRoutingOperationNames } from '@/api/production/routing'
import BillListPage from '@/components/bill/BillListPage.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { useBaseDataRefresh } from '@/composables/useBaseDataRefresh'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()

// 列定义（列设置抽屉 + 显隐用）
const columns = [
  { prop: 'templateCode', label: '模板编码', width: 150 },
  { prop: 'templateName', label: '模板名称', minWidth: 180 },
  { prop: 'templateType', label: '模板类型', width: 130 },
  { prop: 'billTypes', label: '适用单据', minWidth: 200 },
  { prop: 'qcType', label: '质检类型', width: 100 },
  { prop: 'materialNames', label: '适用物料', minWidth: 240 },
  { prop: 'categoryNames', label: '适用分类', minWidth: 180 },
  { prop: 'operationName', label: '适用工序', minWidth: 140 },
  { prop: 'sampleRatio', label: '抽样比例(%)', width: 120 },
  { prop: 'allowedDefectRatio', label: '允许不良率(%)', width: 130 },
  { prop: 'itemCount', label: '检验项', width: 90 },
  { prop: 'isEnabled', label: '状态', width: 90 },
  { prop: 'remark', label: '备注', minWidth: 140 },
  { prop: 'createdAt', label: '创建时间', width: 160, defaultHidden: false }
]
const { columnSettingVisible, isColVisible, getColFixed, configVersion } = useColumnSetting('QcTemplateList', columns)

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const search = reactive({ keyword: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })

const materialOptions = ref([])
const categoryOptions = ref([])
const operationOptions = ref([])
const materialPickerVisible = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
/** 单据级模板可绑定的入库单类型（与后端 InboundQcService 的 BILL_* 常量一致） */
const BILL_TYPE_OPTIONS = [
  { value: 'PURCHASE_INBOUND', label: '采购入库单' },
  { value: 'PRODUCTION_RECEIPT', label: '生产入库单' },
  { value: 'SUBCONTRACT_INBOUND', label: '委外入库单' }
]
function billTypeLabel (code) {
  const hit = BILL_TYPE_OPTIONS.find(o => o.value === String(code || '').trim())
  return hit ? hit.label : code
}
const form = reactive({
  templateId: null, templateCode: '', templateName: '', templateType: 'MATERIAL',
  billTypes: [], isDefault: 0,
  materialIds: [], categoryIds: [], operationName: '',
  qcType: '首检', sampleRatio: 0, allowedDefectRatio: 0, isEnabled: 1, remark: '', items: []
})
const rules = {
  templateName: [{ required: true, message: tt('请输入模板名称'), trigger: 'blur' }],
  templateType: [{ required: true, message: tt('请选择模板类型'), trigger: 'change' }],
  qcType: [{ required: true, message: tt('请选择质检类型'), trigger: 'change' }]
}

function num (v) { return v == null ? 0 : Number(v) }
function qcTypeTag (t) {
  return { '首检': 'info', '抽检': 'warning', '全检': 'danger' }[t] || 'info'
}

/** 物料标签显示（从已加载的物料选项里映射；取不到时退化为 ID） */
function materialLabelById (id) {
  const hit = materialOptions.value.find(o => o.value === id)
  return hit ? hit.label : String(id)
}

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

async function loadDropdownOptions () {
  try {
    const list = await listAllMaterial()
    const rows = Array.isArray(list) ? list : (list?.records || [])
    materialOptions.value = rows
      .filter(m => m && m.materialId != null)
      .map(m => ({ value: m.materialId, label: `${m.materialName || ''} (${m.materialCode || ''})`.trim() }))
  } catch (e) { /* 物料下拉取不到不影响其它操作 */ }
  // 适用工序下拉：只取「生产管理 → 工艺路线」里配置过的工序（工序的唯一维护入口在那里）。
  // 不再叠加"工序/工位档案"——那个页面没有菜单入口、数据没人维护，只会带出无关工序干扰选择。
  try {
    const routingNames = await listRoutingOperationNames()
    operationOptions.value = (Array.isArray(routingNames) ? routingNames : [])
      .map(n => String(n == null ? '' : n).trim()).filter(Boolean)
  } catch (e) { operationOptions.value = [] }
  try {
    const cats = await listAllMaterialCategory()
    categoryOptions.value = buildCategoryOptions(Array.isArray(cats) ? cats : (cats?.records || []))
  } catch (e) { /* 分类下拉取不到不影响其它操作 */ }
}

/** 物料库弹窗确认：合并选中物料进多选集合（已选的自动去重） */
function onMaterialsPicked ({ rows }) {
  const picked = (rows || []).filter(m => m && m.materialId != null)
  const exist = new Set(form.materialIds)
  picked.forEach(m => {
    if (!exist.has(m.materialId)) form.materialIds.push(m.materialId)
    // 同步进选项表，保证标签文字立刻能显示（不必等页面重载）
    if (!materialOptions.value.some(o => o.value === m.materialId)) {
      materialOptions.value.push({ value: m.materialId, label: `${m.materialName || ''} (${m.materialCode || ''})`.trim() })
    }
  })
}

function removeMaterial (id) {
  form.materialIds = form.materialIds.filter(x => x !== id)
}

/** 列表查询条件（列表与将来的导出共用同一套，避免口径漂移） */
function buildQueryParams () {
  return {
    keyword: search.keyword,
    isEnabled: search.status === '' ? null : (search.status === '启用' ? 1 : 0)
  }
}

async function fetchData () {
  loading.value = true
  try {
    const res = await listQcTemplates({ current: pagination.current, size: pagination.size, ...buildQueryParams() })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    ElMessage.error(e?.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch () { pagination.current = 1; fetchData() }

function handleReset () {
  Object.assign(search, { keyword: '', status: '' })
  pagination.current = 1
  fetchData()
}

/** 通用搜索栏「启停状态」变更：写回本页 search（壳随后会抛 search 事件再查询） */
function onFilterChange (patch) {
  Object.assign(search, patch || {})
}

async function handleAdd () {
  // 打开即取最新下拉（工序/物料/分类在别处新增后，这里立刻可见）
  loadDropdownOptions()
  Object.assign(form, {
    templateId: null, templateCode: '', templateName: '', templateType: 'MATERIAL',
    billTypes: [], isDefault: 0,
    materialIds: [], categoryIds: [], operationName: '',
    qcType: '首检', sampleRatio: 0, allowedDefectRatio: 0, isEnabled: 1, remark: '', items: []
  })
  dialogTitle.value = tt('新增质检模板')
  dialogVisible.value = true
}

async function handleEdit (row) {
  loadDropdownOptions()
  const data = await getQcTemplate(row.templateId)
  Object.assign(form, {
    templateId: data.templateId,
    templateCode: data.templateCode || '',
    templateName: data.templateName || '',
    templateType: data.templateType === 'BILL' ? 'BILL' : 'MATERIAL',
    billTypes: data.billTypes ? String(data.billTypes).split(',').map(s => s.trim()).filter(Boolean) : [],
    isDefault: data.isDefault == null ? 0 : Number(data.isDefault),
    materialIds: Array.isArray(data.materialIds) ? [...data.materialIds] : (data.materialId != null ? [data.materialId] : []),
    categoryIds: Array.isArray(data.categoryIds) ? [...data.categoryIds] : [],
    operationName: data.operationName || '',
    qcType: data.qcType || '首检',
    sampleRatio: data.sampleRatio == null ? 0 : Number(data.sampleRatio),
    allowedDefectRatio: data.allowedDefectRatio == null ? 0 : Number(data.allowedDefectRatio),
    isEnabled: data.isEnabled == null ? 1 : data.isEnabled,
    remark: data.remark || '',
    items: (data.items || []).map(it => ({
      itemName: it.itemName || '',
      inspectMethod: it.inspectMethod || '',
      standardValue: it.standardValue || '',
      lowerLimit: it.lowerLimit == null ? null : Number(it.lowerLimit),
      upperLimit: it.upperLimit == null ? null : Number(it.upperLimit),
      unit: it.unit || '',
      isRequired: it.isRequired == null ? 1 : it.isRequired
    }))
  })
  dialogTitle.value = tt('编辑质检模板')
  dialogVisible.value = true
}

function addItem () {
  form.items.push({
    itemName: '', inspectMethod: '', standardValue: '',
    lowerLimit: null, upperLimit: null, unit: '', isRequired: 1
  })
}

function removeItem (index) {
  form.items.splice(index, 1)
}

async function handleSave () {
  await formRef.value.validate()
  const isBill = form.templateType === 'BILL'
  if (isBill) {
    if (!form.billTypes || !form.billTypes.length) {
      ElMessage.warning(tt('单据质检模板请至少选择一个适用单据'))
      return
    }
  } else {
    const hasMaterial = form.materialIds.length > 0
    const hasCategory = form.categoryIds.length > 0
    const hasOperation = !!(form.operationName && String(form.operationName).trim())
    if (!hasMaterial && !hasCategory && !hasOperation) {
      ElMessage.warning(tt('请至少指定适用物料、适用工序或适用分类，否则模板无法被使用'))
      return
    }
  }
  const items = form.items
    .filter(it => it && it.itemName && String(it.itemName).trim())
    .map((it, i) => ({ ...it, itemName: String(it.itemName).trim(), lineNo: (i + 1) * 10 }))
  if (!items.length) {
    ElMessage.warning(tt('请至少添加一个检验项'))
    return
  }
  const payload = {
    templateCode: form.templateCode || null,
    templateName: form.templateName,
    templateType: form.templateType,
    billTypes: isBill ? form.billTypes.join(',') : null,
    isDefault: isBill ? form.isDefault : 0,
    materialIds: isBill ? [] : form.materialIds,
    categoryIds: isBill ? [] : form.categoryIds,
    operationName: isBill ? null : (form.operationName || null),
    qcType: form.qcType,
    sampleRatio: form.sampleRatio,
    allowedDefectRatio: form.allowedDefectRatio,
    isEnabled: form.isEnabled,
    remark: form.remark,
    items
  }
  saving.value = true
  try {
    if (form.templateId) {
      await updateQcTemplate(form.templateId, payload)
    } else {
      await createQcTemplate(payload)
    }
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || tt('保存失败'))
  } finally {
    saving.value = false
  }
}

async function handleDelete (row) {
  try {
    await ElMessageBox.confirm(
      `${tt('确认删除质检模板「')}${row.templateName}${tt('」？删除后检验项一并删除')}`,
      tt('删除确认'),
      { type: 'warning', confirmButtonText: tt('确认删除'), cancelButtonText: tt('取消') }
    )
  } catch (e) {
    return
  }
  try {
    await deleteQcTemplate(row.templateId)
    ElMessage.success(tt('删除成功'))
    fetchData()
  } catch (e) {
    ElMessage.error(e?.message || tt('删除失败'))
  }
}

function resetForm () {
  formRef.value && formRef.value.resetFields()
}

const statusOptions = [
  { label: '启用', value: '启用' },
  { label: '停用', value: '停用' }
]

onMounted(() => {
  loadDropdownOptions()
  fetchData()
})
useAutoRefresh(() => { fetchData() })
// 切回页签时也刷新下拉基础数据（物料/工序/分类在别处改过后，这里立刻同步）
useBaseDataRefresh(loadDropdownOptions)
</script>

<style scoped>
.qc-template-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}
.order-no-cell { display: flex; align-items: center; gap: 6px; }
.order-no-cell .order-no-text { color: #6366f1; font-weight: 500; cursor: pointer; }
.order-no-cell .order-no-text:hover { color: #8b5cf6; }
.status-cell { display: flex; align-items: center; gap: 6px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; }
.status-dot.dot-success { background: #10b981; }
.status-dot.dot-danger { background: #ef4444; }
.qc-items-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.qc-hint { font-size: 12px; color: #9ca3af; }
.qc-hint-block { padding: 6px 0 0 4px; }
.qc-chips { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
/* 列表的「适用物料 / 适用分类」明细列：标签换行铺开，避免省略号看不全 */
.bind-tags { display: flex; flex-wrap: wrap; gap: 4px; padding: 2px 0; }
.bind-tags .el-tag { margin: 0; max-width: 100%; }
</style>
