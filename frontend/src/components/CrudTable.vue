<template>
  <!--
    通用CRUD表格组件 - 基础资料模块统一模板
    用法示例:
    <CrudTable
      :title="$tt('计量单位')"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
    />
  -->
  <div class="erp-card">
    <!-- 搜索栏 -->
    <div class="erp-search-bar" v-if="mergedSearchFields && mergedSearchFields.length">
      <div class="erp-search-item" v-for="f in mergedSearchFields" :key="f.prop">
        <span class="label">{{ $tf(f.label) }}:</span>
        <el-input
          v-model="searchForm[f.prop]"
          :placeholder="f.placeholder ? $tt(f.placeholder) : $tt('请输入')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </div>
      <el-button :icon="Refresh" v-permission="perm('reset')" @click="handleReset">{{ $tt('重置') }}</el-button>
    </div>

    <!-- 操作按钮区 - 使用通用 ActionToolbar；按钮显隐由「角色权限设置 → 按钮权限」控制（v-permission） -->
    <ActionToolbar
      :selected-rows="selectedRows"
      :perm-add="permAdd"
      :perm-edit="permEdit"
      :perm-delete="permDelete"
      :perm-import="effImportPerm"
      :perm-export="effExportPerm"
      :perm-print="effPrintPerm"
      :perm-query="effQueryPerm"
      :hide-buttons="hideButtons"
      @query="handleSearch"
      @add="handleAdd"
      @edit="handleToolbarEdit"
      @delete="handleBatchDelete"
      @import="handleImport"
      @export="handleExport"
      @print="handlePrint"
      @refresh="loadData"
    >
      <template #extra>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <slot name="toolbar-extra" />
      </template>
    </ActionToolbar>

    <!-- 表格 -->
    <el-table
      ref="tableRef"
      :data="tableData"
      v-loading="loading"
      border
      stripe
      height="100%"
      @selection-change="onSelectionChange"
      @sort-change="onSortChange"
      @header-dragend="onHeaderDragend"
      style="width: 100%"
      :cell-class-name="cellClassName"
      :empty-text="$tt('暂无数据')"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column
        v-for="(col, idx) in displayColumns"
        :key="col.prop"
        :prop="col.prop"
        :label="$tf(col.label)"
        :width="col.width"
        :min-width="col.minWidth || 120"
        :fixed="col.fixed || false"
        :formatter="col.formatter"
        :show-overflow-tooltip="true"
        :sortable="col.sortable === false ? false : 'custom'"
      >
        <template #default="scope" v-if="col.slot">
          <slot :name="col.slot" :row="scope.row" :index="scope.$index" />
        </template>
        <template #default="scope" v-else-if="isLinkColumn(idx)">
          <el-link type="primary" :underline="false" @click="handleEdit(scope.row)" style="font-weight:500;">
            {{ scope.row[col.prop] }}
          </el-link>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="erp-pagination">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>
  </div>

  <!-- 导入弹窗 -->
  <el-dialog
    v-model="importDialogVisible"
    :title="$tf('数据导入')"
    width="560px"
    :close-on-click-modal="false"
  >
    <div class="import-dialog-content">
      <!-- 步骤 1: 下载模板 -->
      <div class="import-step">
        <div class="step-title">
          <el-icon><Download /></el-icon>
          <span>{{ $tt('第一步：下载导入模板') }}</span>
        </div>
        <div class="step-desc">{{ $tt('下载模板后，按模板格式填写数据，支持 .xlsx 格式') }}</div>
        <div class="step-desc" style="color:#f56c6c;">{{ $tt('提示：模板中带') }} <b>*</b> {{ $tt('标记的字段为必填字段，请务必填写') }}</div>
        <el-button type="primary" v-permission="perm('downloadTemplate')" @click="downloadImportTemplate" :loading="downloadingTemplate">
          <el-icon><Download /></el-icon> {{ $tt('下载') }}{{ $tf(title) }}{{ $tt('导入模板') }}
        </el-button>
      </div>

      <!-- 物料图片导入指引 -->
      <div v-if="entityName === 'material'" class="import-tip-box">
        <div class="tip-title">
          <el-icon><Picture /></el-icon>
          <span>{{ $tt('物料图片导入说明') }}</span>
        </div>
        <div class="tip-content">
          <p><b>{{ $tt('方式1（推荐）：') }}</b>{{ $tt('在Excel中选中"物料图片"单元格 → 插入 → 图片 → 此设备，选择本地图片插入') }}</p>
          <p><b>{{ $tt('方式2：') }}</b>{{ $tt('直接填写图片URL（多张用英文逗号分隔），如：') }}<code>https://example.com/1.jpg,https://example.com/2.jpg</code></p>
          <p class="tip-warning">{{ $tt('注意：不支持本地文件路径（如 C:\\images\\1.jpg），因为服务器无法访问您的本地文件') }}</p>
        </div>
      </div>

      <el-divider />

      <!-- 步骤 2: 上传文件 -->
      <div class="import-step">
        <div class="step-title">
          <el-icon><Upload /></el-icon>
          <span>{{ $tt('第二步：上传数据文件') }}</span>
        </div>
        <div class="step-desc">{{ $tt('选择填写好的 Excel 文件上传导入') }}</div>
        <el-upload
          ref="uploadRef"
          action="#"
          :auto-upload="false"
          :on-change="handleFileChange"
          :show-file-list="true"
          accept=".xlsx,.xls,.csv"
          drag
          class="import-uploader"
        >
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">
            {{ $tt('将文件拖到此处，或') }}<em>{{ $tt('点击上传') }}</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              {{ $tt('请上传 .xlsx / .xls 格式文件，文件大小不超过 10MB') }}
            </div>
          </template>
        </el-upload>
      </div>

      <!-- 导入结果 -->
      <div v-if="importResult" class="import-result">
        <el-divider />
        <div class="result-title">{{ $tt('导入结果') }}</div>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-statistic :title="$tt('总记录数')" :value="importResult.totalCount || 0" />
          </el-col>
          <el-col :span="8">
            <el-statistic :title="$tt('成功')" :value="importResult.successCount || 0">
              <template #suffix>
                <el-icon color="#67C23A"><CircleCheck /></el-icon>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="8">
            <el-statistic :title="$tt('失败')" :value="importResult.failCount || 0">
              <template #suffix>
                <el-icon color="#F56C6C"><CircleClose /></el-icon>
              </template>
            </el-statistic>
          </el-col>
        </el-row>
        <!-- 服务端汇总提示（如：当前登录账号未绑定员工档案，分配销售员未自动填写） -->
        <el-alert
          v-if="importResult.message"
          :title="importResult.message"
          type="warning"
          :closable="false"
          class="import-error-alert"
        />
        <el-alert
          v-if="importResult.failCount > 0"
          :title="$tt('有') + ' ' + importResult.failCount + ' ' + $tt('条记录导入失败')"
          type="error"
          :closable="false"
          class="import-error-alert"
        >
          <div class="error-list">
            <div v-for="(err, idx) in (importResult.errors || []).slice(0, 10)" :key="idx" class="error-item">
              {{ $tt('第') }} {{ err.rowIndex }} {{ $tt('行') }} {{ err.fieldName ? '[' + err.fieldName + ']' : '' }}: {{ err.message }}
            </div>
            <div v-if="(importResult.errors || []).length > 10" class="error-more">
              ... {{ $tt('还有') }} {{ importResult.errors.length - 10 }} {{ $tt('条错误') }}
            </div>
          </div>
        </el-alert>
      </div>
    </div>
    <template #footer>
      <el-button v-permission="perm('dlgClose')" @click="importDialogVisible = false">{{ $tt('关闭') }}</el-button>
      <el-button type="primary" v-permission="perm('import')" @click="confirmImport" :loading="importing" :disabled="!selectedImportFile">
        <el-icon><Upload /></el-icon> {{ $tt('开始导入') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 新增/编辑弹窗 -->
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? `${$tt('编辑')}${$tf(title)}` : `${$tt('新增')}${$tf(title)}`"
    width="640px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <!-- 审计信息（仅编辑时显示） -->
    <div v-if="isEdit && hasAuditInfo" class="audit-info-bar">
      <div class="audit-item">
        <span class="audit-label">{{ $tt('创建人：') }}</span>
        <span class="audit-value">{{ formData.createdBy || '-' }}</span>
      </div>
      <div class="audit-item">
        <span class="audit-label">{{ $tt('创建时间：') }}</span>
        <span class="audit-value">{{ formatAuditDateTime(formData.createdAt) }}</span>
      </div>
      <div class="audit-item">
        <span class="audit-label">{{ $tt('修改人：') }}</span>
        <span class="audit-value">{{ formData.updatedBy || '-' }}</span>
      </div>
      <div class="audit-item">
        <span class="audit-label">{{ $tt('修改时间：') }}</span>
        <span class="audit-value">{{ formatAuditDateTime(formData.updatedAt) }}</span>
      </div>
    </div>

    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="erp-form-dialog">
      <template v-for="f in formFields" :key="f.prop">
        <el-form-item :label="$tf(f.label)" :prop="f.prop" v-if="!f.hidden">
          <el-select
            v-if="f.type === 'select'"
            v-model="formData[f.prop]"
            :placeholder="f.placeholder ? $tt(f.placeholder) : $tt('请选择')"
            :disabled="f.disabled"
            style="width: 100%"
          >
            <el-option v-for="o in f.options" :key="o.value" :label="$tf(o.label)" :value="o.value" />
          </el-select>
          <el-input-number
            v-else-if="f.type === 'number'"
            v-model="formData[f.prop]"
            :min="0"
            :precision="f.precision || 0"
            :disabled="f.disabled"
            style="width: 100%"
          />
          <el-switch
            v-else-if="f.type === 'switch'"
            v-model="formData[f.prop]"
            :active-value="f.activeValue ?? 1"
            :inactive-value="f.inactiveValue ?? 0"
            :disabled="f.disabled"
          />
          <el-date-picker
            v-else-if="f.type === 'date'"
            v-model="formData[f.prop]"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="f.disabled"
            style="width: 100%"
          />
          <el-input
            v-else
            v-model="formData[f.prop]"
            :type="f.textarea ? 'textarea' : 'text'"
            :placeholder="f.placeholder ? $tt(f.placeholder) : $tt('请输入')"
            :disabled="f.disabled"
            :rows="f.rows || 3"
          />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt('取消') }}</el-button>
      <el-button type="primary" v-permission="perm('dlgConfirm')" :loading="saving" @click="handleSave">{{ $tt('确定') }}</el-button>
    </template>
  </el-dialog>

  <!-- 打印预览弹窗 -->
  <PrintPreviewDialog
    v-model="printVisible"
    :bill-type="printBillType"
    :bill-id="printBillId"
  />

  <!-- 列设置抽屉 -->
  <ColumnSettingDrawer
    v-model="columnSettingVisible"
    :page-key="columnPageKey"
    :columns="mergedColumns"
    :title="$tt('列设置')"
    @change="onColumnSettingChange"
  />

  <!--
    自定义导出列弹窗已于 2026-09-24 下线：全站口径改为"页面上看到什么列，导出就带什么列"，
    不再让用户挑列（导出列 = 当前可见列，见 handleExport）。
  -->

  <!--
    批量删除确认弹窗（2026-09-16 新增）：
    仅在"部分记录不可删"时出现 —— 把"可删/不可删（含原因）"摊开给用户，由用户自己决定；
    「确定」= 只删除可删除的部分，「取消」= 本次操作全部不执行（可删的也不删）。
    全部可删时不弹本窗口（沿用一句确认框，不打扰）。
  -->
  <el-dialog
    v-model="delCheck.visible"
    :title="$tt('批量删除确认')"
    width="640px"
    :close-on-click-modal="false"
    append-to-body
  >
    <div v-if="delCheck.deletable.length" class="del-check-block">
      <el-alert type="success" :closable="false" show-icon>
        <template #title>{{ $tt('可删除') }} {{ delCheck.deletable.length }} {{ $tt('条') }}</template>
      </el-alert>
      <div class="del-check-list">
        <div v-for="(row, i) in delCheck.deletable" :key="'ok' + i">
          {{ rowLabel(row) }}
        </div>
      </div>
    </div>

    <div v-if="delCheck.blocked.length" class="del-check-block">
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>{{ $tt('不可删除') }} {{ delCheck.blocked.length }} {{ $tt('条（原因见下）') }}</template>
      </el-alert>
      <div class="del-check-list">
        <div v-for="(b, i) in delCheck.blocked" :key="'no' + i">
          <b>{{ b.label || ('#' + b.id) }}</b><span class="reason"> —— {{ b.reason }}</span>
        </div>
      </div>
    </div>

    <div class="del-check-tip">{{ $tt('「确定」只会删除上面"可删除"的部分，不可删除的记录保持不动；「取消」则本次全部不执行。') }}</div>

    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="delCheck.visible = false">{{ $tt('取消') }}</el-button>
      <el-button
        v-if="delCheck.deletable.length"
        type="danger"
        v-permission="perm('deleteRow')"
        :loading="batchDeleting"
        @click="confirmBatchDelete"
      >
        {{ $tt('确定（只删除可删除的') }} {{ delCheck.deletable.length }} {{ $tt('条）') }}
      </el-button>
      <el-button v-else type="primary" v-permission="perm('dlgClose')" @click="delCheck.visible = false">{{ $tt('知道了') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Refresh, Download, Upload, CircleCheck, CircleClose, Setting, Picture } from '@element-plus/icons-vue'
import ActionToolbar from './ActionToolbar.vue'
import PrintPreviewDialog from './PrintPreviewDialog.vue'
import ColumnSettingDrawer from './ColumnSettingDrawer.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { useTableSort } from '@/composables/useTableSort'
import { downloadTemplate, exportAsSeen, collectFilteredRows, columnsToExportFields, importData, exportSelectedRows } from '@/utils/exportUtil'
import { listEnabledDefinition, batchGetFieldValues } from '@/api/system/customField'
import { tt, tf } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const router = useRouter()
const route = useRoute()

const props = defineProps({
  title: { type: String, default: '记录' },
  // 列定义: [{ prop, label, width, minWidth, formatter, slot }]
  columns: { type: Array, default: () => [] },
  // 搜索字段: [{ prop, label, placeholder }]
  searchFields: { type: Array, default: () => [] },
  // 可选：进入页面时预置的搜索条件（如数据看板穿透 {"cf_region":"欧美"}），默认空=行为不变
  presetSearch: { type: Object, default: () => ({}) },
  // 表单字段: [{ prop, label, type, options, required, disabled, hidden, placeholder, precision, textarea, rows, activeValue, inactiveValue }]
  formFields: { type: Array, default: () => [] },
  // 数据加载函数: (params) => Promise<{records, total}>
  fetchData: { type: Function, required: true },
  // 保存函数: (formData, isEdit) => Promise
  onSave: { type: Function, required: true },
  // 删除函数: (row) => Promise
  onDelete: { type: Function, required: true },
  // 可选: 批量删除预检 (rows) => Promise<{ deletable: Row[], blocked: [{ id, label, reason }] }>
  // 传了才启用「预检 → 弹窗让用户决定 → 只删用户确认的部分」；不传则保持原有逐条删除（其余页面零影响）
  onBatchDeleteCheck: { type: Function, default: null },
  // 可选: 批量删除执行 (rows) => Promise，只接收用户确认要删的行（后端仍会重新校验）
  onBatchDelete: { type: Function, default: null },
  // 可选: 导入函数 (file) => Promise, 未传则提示未实现
  onImport: { type: Function, default: null },
  // 可选: 导出函数 (params) => Promise, 未传则提示未实现
  onExport: { type: Function, default: null },
  // 可选: 打印函数 (rows) => Promise, 未传则调用浏览器打印
  onPrint: { type: Function, default: null },
  // 权限码
  permAdd: { type: String, default: '' },
  permEdit: { type: String, default: '' },
  permDelete: { type: String, default: '' },
  permImport: { type: String, default: '' },
  permExport: { type: String, default: '' },
  permPrint: { type: String, default: '' },
  // 主键字段名
  idField: { type: String, default: 'id' },
  // 可选：新增跳转独立页面路由（如 '/basedata/material/new'），不传则用弹窗
  addRoute: { type: String, default: '' },
  // 可选：编辑跳转独立页面路由前缀（如 '/basedata/material/edit'，会自动拼接 /:id），不传则用弹窗
  editRoute: { type: String, default: '' },
  // 实体名称: 用于导入导出功能，如 'customer', 'unit' 等
  entityName: { type: String, default: '' },
  // 隐藏指定按钮 (如自动生成单据的页面隐藏新增按钮: ['add', 'edit', 'delete'])
  hideButtons: { type: Array, default: () => [] },
  // 「列设置」页面标识，默认用路由 name（2026-10-10 起仅列设置使用；工具栏按钮显隐已统一走角色按钮权限）
  toolbarPageKey: { type: String, default: '' },
  // 打印单据类型 (如 SALE_ORDER), 配置后打印按钮将调用后端打印模板渲染
  printBillType: { type: String, default: '' },
  // 是否在表格末尾追加审计列（创建人/创建时间/修改人/修改时间），默认开启
  showAuditColumns: { type: Boolean, default: true },
  // 自定义字段实体类型（如 'customer'/'material'），传入则列表自动追加 isListShow=1 的自定义列、
  // isSearch=1 的自定义搜索项，并把自定义字段值批量回填到行数据
  customFieldEntity: { type: String, default: '' }
})

/**
 * 工具栏按钮权限码：页面显式传入优先，未传则由当前路由解析成 `<模块:对象>:<动作>`
 * （perm 来自 usePagePerm，与单据页 BillHeaderActions 的 perm-prefix 派生口径一致）。
 * 修复：此前组件只透传页面传来的码，而 12 个基础资料页只传了 add/edit/delete →
 * 导入/导出/打印 的权限码为空串，v-permission 直接放行 → 设置中心「按钮权限」里
 * 取消勾选 导入/导出 后按钮仍然可见。
 */
const effImportPerm = computed(() => props.permImport || perm('import'))
const effExportPerm = computed(() => props.permExport || perm('export'))
const effPrintPerm = computed(() => props.permPrint || perm('print'))
const effQueryPerm = computed(() => perm('query'))

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const selectedRows = ref([])
/** 批量删除预检结果（仅"部分不可删"时弹窗，由用户决定删哪些） */
const delCheck = reactive({ visible: false, deletable: [], blocked: [] })
const batchDeleting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const formData = reactive({})
const searchForm = reactive({})
const page = reactive({ current: 1, size: 20, total: 0 })

// 导入弹窗相关
const importDialogVisible = ref(false)
const downloadingTemplate = ref(false)
const importing = ref(false)
const selectedImportFile = ref(null)
const importResult = ref(null)
const uploadRef = ref()

// 打印预览弹窗
const printVisible = ref(false)
const printBillId = ref(null)

// 自定义导出列弹窗
// 自定义导出列弹窗已于 2026-09-24 下线（全站口径：导出列 = 页面当前可见列），故不再需要开关状态

// 审计信息是否可显示（编辑模式且后端返回了审计字段）
const hasAuditInfo = computed(() =>
  isEdit.value && !!(formData.createdAt || formData.updatedAt || formData.createdBy || formData.updatedBy)
)

// 格式化日期时间（兼容 el-table-column formatter 签名: (row, column, cellValue, index)）
function formatDateTime(row, column, cellValue) {
  return formatAuditDateTime(cellValue)
}

// 审计时间格式化工具函数（可独立使用）
function formatAuditDateTime(val) {
  if (!val) return ''
  // LocalDateTime 数组序列化 [y,m,d,h,m,s,ns]
  if (Array.isArray(val)) {
    const [y, mo, d, h = 0, mi = 0, se = 0] = val
    return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(se)}`
  }
  // LocalDateTime 对象序列化 {year,monthValue,dayOfMonth,hour,minute,second,...}
  if (typeof val === 'object') {
    if (val.year) {
      return `${val.year}-${pad(val.monthValue ?? val.month)}-${pad(val.dayOfMonth ?? val.day)} ${pad(val.hour ?? 0)}:${pad(val.minute ?? 0)}:${pad(val.second ?? 0)}`
    }
    return ''
  }
  // 字符串形式 "2026-07-20T22:29:59.501268"
  const s = String(val)
  return s.length > 19 ? s.slice(0, 19).replace('T', ' ') : s.replace('T', ' ')
}

function pad(n) {
  return String(n ?? 0).padStart(2, '0')
}

// 列设置功能：基于 toolbarPageKey 或路由 name 作为 pageKey
const columnPageKey = computed(() => props.toolbarPageKey || route.name || 'crud-table')

// ===== 自定义字段（列表显示 + 可搜索 + 列设置适配）=====
// 加载自定义字段定义（isListShow=1 → 动态列；isSearch=1 → 动态搜索项）
const customFieldDefs = ref([])
const customSearchFields = ref([])

// 业务列 + 自定义列合并（列设置抽屉基于合并后的列数组，自定义列自动支持显隐/固定/排序）
const mergedColumns = computed(() => {
  const customCols = customFieldDefs.value
    .filter(f => f.isListShow === 1)
    .map(f => ({
      prop: f.fieldKey,
      label: f.fieldLabel,
      width: 140,
      customField: true,
      isConfigurable: true
    }))
  return [...props.columns, ...customCols]
})

const { visibleColumns, allColumns, columnSettingVisible, openSetting: openColumnSetting,
        onHeaderDragend, applySavedWidths, resetColWidths } = useColumnConfig(columnPageKey.value, mergedColumns)

/**
 * 列宽持久化（2026-09-22 新增）：本组件是基础资料列表页的通用表（自带 border，本来就能拖列宽），
 * 这里补上"拖完记住 + 下次打开恢复"，与单据列表页（BillListPage）口径完全一致。
 */
const tableRef = ref(null)
function restoreColumnWidths() {
  nextTick(() => applySavedWidths(tableRef))
}
// 列设置抽屉里「恢复默认列宽」需要本组件重排表格
function onColumnSettingChange(e) {
  if (e && e.type === 'resetWidths') nextTick(() => resetColWidths(tableRef))
}

// 合并业务搜索项 + 自定义字段搜索项（isSearch=1）
const mergedSearchFields = computed(() => {
  if (customSearchFields.value.length === 0) return props.searchFields
  const extra = customSearchFields.value.map(f => ({
    prop: `cf_${f.fieldKey}`,
    label: f.fieldLabel,
    placeholder: '请输入'
  }))
  return [...props.searchFields, ...extra]
})

// 加载自定义字段定义
async function loadCustomFieldDefs() {
  if (!props.customFieldEntity) return
  try {
    const defs = await listEnabledDefinition(props.customFieldEntity) || []
    customFieldDefs.value = defs
    customSearchFields.value = defs.filter(f => f.isSearch === 1)
    // 初始化自定义搜索项的表单字段
    customSearchFields.value.forEach(f => {
      if (!(searchForm[`cf_${f.fieldKey}`] in searchForm)) searchForm[`cf_${f.fieldKey}`] = ''
    })
  } catch (e) {
    console.warn('[CrudTable] 加载自定义字段定义失败:', e)
    customFieldDefs.value = []
    customSearchFields.value = []
  }
}

// 数据加载后批量回填自定义字段值到行
async function fillCustomFieldValues() {
  const listShowCols = customFieldDefs.value.filter(f => f.isListShow === 1)
  if (!props.customFieldEntity || listShowCols.length === 0 || tableData.value.length === 0) return
  const ids = tableData.value.map(r => r[props.idField]).filter(Boolean)
  if (ids.length === 0) return
  try {
    const valueMap = await batchGetFieldValues(props.customFieldEntity, ids) || {}
    tableData.value.forEach(row => {
      const vals = valueMap[row[props.idField]]
      if (vals) Object.assign(row, vals)
    })
  } catch (e) {
    console.warn('[CrudTable] 回填自定义字段值失败:', e)
  }
}

// 列头排序（后端执行，分页正确；列配置 sortable: true 开启）
const { handleSortChange, getSortParams } = useTableSort()
// 排序变化后立即重载数据
const onSortChange = (e) => { handleSortChange(e); loadData() }

// 审计列：自动追加在表格末尾
const auditColumns = [
  { prop: 'createdBy', label: '创建人', width: 100, noLink: true },
  { prop: 'createdAt', label: '创建时间', width: 160, noLink: true, formatter: formatDateTime },
  { prop: 'updatedBy', label: '修改人', width: 100, noLink: true },
  { prop: 'updatedAt', label: '修改时间', width: 160, noLink: true, formatter: formatDateTime }
]

// 实际渲染的列：业务列 + 审计列
const displayColumns = computed(() => {
  if (!props.showAuditColumns) return visibleColumns.value
  // 避免业务列已含审计字段时重复
  const existProps = new Set(visibleColumns.value.map(c => c.prop))
  const extra = auditColumns.filter(c => !existProps.has(c.prop))
  return [...visibleColumns.value, ...extra]
})

function isLinkColumn(idx) {
  let linkIdx = -1
  for (let i = 0; i < visibleColumns.value.length; i++) {
    const col = visibleColumns.value[i]
    if (!col.slot && !col.noLink) {
      linkIdx = i
      break
    }
  }
  return idx === linkIdx
}

// 给最后一列（操作列）加 className，用于CSS定位
function cellClassName({ row, column, rowIndex, columnIndex }) {
  // columnIndex=0是selection列, +1是数据列, 最后一列是操作列
  if (columnIndex === visibleColumns.value.length + 1) {
    return 'op-column-cell'
  }
  return ''
}

// 根据formFields自动生成校验规则
const formRules = computed(() => {
  const rules = {}
  props.formFields.forEach(f => {
    if (f.required) {
      rules[f.prop] = [{ required: true, message: `${f.label}${tt("不能为空")}`, trigger: f.type === 'select' ? 'change' : 'blur' }]
    }
  })
  return rules
})

/**
 * 列表查询参数（**加载与导出共用**，保证"导出的行 = 页面筛选后的行"）。
 * @param {boolean} includePaging 是否带分页参数（导出时要拉全量，故传 false）
 */
function buildListParams(includePaging = true) {
  // 提取自定义字段搜索条件（cf_xxx 前缀），打包成 customSearch JSON 传给后端
  const customSearch = {}
  Object.keys(searchForm).forEach(k => {
    if (k.startsWith('cf_') && searchForm[k] !== '' && searchForm[k] !== undefined && searchForm[k] !== null) {
      customSearch[k.slice(3)] = searchForm[k]
    }
  })
  const params = includePaging
    ? { current: page.current, size: page.size, ...searchForm, ...getSortParams() }
    : { ...searchForm, ...getSortParams() }
  // cf_ 前缀是前端内部字段，不透传后端；自定义条件单独打包
  Object.keys(params).forEach(k => { if (k.startsWith('cf_')) delete params[k] })
  if (Object.keys(customSearch).length > 0) {
    params.customSearch = JSON.stringify(customSearch)
  }
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await props.fetchData(buildListParams(true))
    tableData.value = res.records || res || []
    page.total = res.total || tableData.value.length
    // 回填自定义字段值（isListShow=1 的列显示用）
    await fillCustomFieldValues()
  } catch (e) {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function handleSearch() {
  page.current = 1
  loadData()
}

function handleReset() {
  Object.keys(searchForm).forEach(k => delete searchForm[k])
  page.current = 1
  loadData()
}

function handleAdd() {
  // 优先跳转独立新增页面
  if (props.addRoute) {
    router.push(props.addRoute)
    return
  }
  isEdit.value = false
  props.formFields.forEach(f => {
    formData[f.prop] = f.default !== undefined ? f.default : (f.type === 'switch' ? (f.inactiveValue ?? 0) : (f.type === 'number' ? 0 : ''))
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  // 验证ID字段是否有效
  const id = row[props.idField]
  if (id === undefined || id === null || id === '') {
    ElMessage.warning(tt('该记录缺少有效ID，无法编辑'))
    return
  }
  // 优先跳转独立编辑页面
  if (props.editRoute) {
    router.push(`${props.editRoute}/${id}`)
    return
  }
  isEdit.value = true
  props.formFields.forEach(f => {
    formData[f.prop] = row[f.prop] ?? formData[f.prop]
  })
  formData[props.idField] = row[props.idField]
  // 复制审计字段用于只读展示
  formData.createdAt = row.createdAt
  formData.updatedAt = row.updatedAt
  formData.createdBy = row.createdBy
  formData.updatedBy = row.updatedBy
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await props.onSave({ ...formData }, isEdit.value)
      ElMessage.success(isEdit.value ? tt('修改成功') : tt('新增成功'))
      dialogVisible.value = false
      loadData()
    } catch (e) {
      // 错误已在拦截器处理
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`${tt('确定删除该')}${tf(props.title)}${tt('吗？')}`, tt('提示'), { type: 'warning' })
    await props.onDelete(row)
    ElMessage.success(tt('删除成功'))
    loadData()
  } catch (e) {
    if (e !== 'cancel') {} // 拦截器已处理
  }
}

/**
 * 批量删除（工具栏唯一的删除入口）：
 * ① 页面提供了批量能力（onBatchDeleteCheck + onBatchDelete）→ 先"预检（只看不删）"，
 *    再把「可删 / 不可删（含原因）」摊开给用户自己决定：确定=只删可删的，取消=本次全部不执行；
 * ② 页面未提供 → 保持原有逐条删除行为（其余页面零影响）。
 */
async function handleBatchDelete() {
  if (!selectedRows.value.length) return
  const rows = [...selectedRows.value]

  // ① 页面未接入批量删除（无 onBatchDelete）：保持原有「逐条删除」行为，其余页面零影响
  if (!props.onBatchDelete) {
    try {
      await ElMessageBox.confirm(`${tt('确定删除选中的')} ${rows.length} ${tt('条记录吗？')}`, tt('提示'), { type: 'warning' })
      for (const row of rows) {
        await props.onDelete(row)
      }
      ElMessage.success(tt('批量删除成功'))
      loadData()
    } catch (e) {
      if (e !== 'cancel') {} // 拦截器已处理
    }
    return
  }

  // ② 有批量接口、但没有预检接口（2026-09-20 新增）：一句确认 + 一次请求批量删
  //    背景：原逻辑要求 onBatchDeleteCheck 与 onBatchDelete 同时存在才走批量，导致"只有批量删除接口、
  //    没有预检接口"的实体（除物料外的基础资料）一直退化成"逐条 N 次请求"且无预检。
  if (!props.onBatchDeleteCheck) {
    try {
      await ElMessageBox.confirm(`${tt('确定删除选中的')} ${rows.length} ${tt('条记录吗？')}`, tt('提示'), { type: 'warning' })
    } catch (e) {
      return
    }
    await doBatchDelete(rows)
    return
  }

  // ③ 预检（只看不删；结果仅用于展示，执行时后端会重新校验）
  let check = null
  try {
    check = await props.onBatchDeleteCheck(rows)
  } catch (e) {
    return // 错误提示由请求拦截器统一弹出
  }
  const deletable = (check && check.deletable) || []
  const blocked = (check && check.blocked) || []

  // 全部可删：沿用一句确认框，不打扰用户
  if (!blocked.length) {
    if (!deletable.length) {
      ElMessage.info(tt('没有可删除的记录'))
      return
    }
    try {
      await ElMessageBox.confirm(`${tt('确定删除选中的')} ${deletable.length} ${tt('条记录吗？')}`, tt('提示'), { type: 'warning' })
    } catch (e) {
      return
    }
    await doBatchDelete(deletable)
    return
  }

  // 部分（或全部）不可删：弹窗让用户自己决定，不替用户做决定
  delCheck.deletable = deletable
  delCheck.blocked = blocked
  delCheck.visible = true
}

/** 弹窗「确定」：只删除可删除的部分 */
async function confirmBatchDelete() {
  const rows = [...delCheck.deletable]
  delCheck.visible = false
  await doBatchDelete(rows)
}

/** 真正执行批量删除（一次请求；失败原因由请求拦截器提示，这里只负责刷新列表） */
async function doBatchDelete(rows) {
  if (!rows.length) return
  batchDeleting.value = true
  try {
    await props.onBatchDelete(rows)
    ElMessage.success(`${tt('已删除')} ${rows.length} ${tt('条')}`)
    selectedRows.value = []
  } catch (e) {
    // 含"部分未删除"等失败原因，拦截器已弹提示
  } finally {
    batchDeleting.value = false
    loadData()
  }
}

/** 弹窗里每行的展示文案：优先页面给的自定义标签，否则取第一个展示列的值 */
function rowLabel(row) {
  if (!row) return ''
  if (row._delLabel) return row._delLabel
  const first = (displayColumns.value || []).find(c => c && c.prop)
  const v = first ? row[first.prop] : ''
  return v === undefined || v === null || v === '' ? '' : String(v)
}

// 工具栏"修改"按钮: 编辑选中行(多选时取第一条)
function handleToolbarEdit() {
  if (!selectedRows.value.length) {
    ElMessage.warning(tt('请先勾选要修改的行'))
    return
  }
  if (selectedRows.value.length > 1) {
    ElMessage.warning(tt('一次只能修改一条记录，已自动选中第一条'))
  }
  handleEdit(selectedRows.value[0])
}

// 导入: 打开导入弹窗
function handleImport() {
  if (!props.entityName && !props.onImport) {
    ElMessage.info(tt('导入功能未实现，请传入 onImport 回调或 entityName 属性'))
    return
  }
  importDialogVisible.value = true
  importResult.value = null
  selectedImportFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

// 下载导入模板
async function downloadImportTemplate() {
  if (!props.entityName) {
    ElMessage.info(tt('未配置 entityName，无法下载模板'))
    return
  }
  downloadingTemplate.value = true
  try {
    await downloadTemplate(props.entityName)
    ElMessage.success(tt('模板下载成功'))
  } catch (err) {
    ElMessage.error(tt('模板下载失败'))
  } finally {
    downloadingTemplate.value = false
  }
}

// 文件选择变化
function handleFileChange(file) {
  selectedImportFile.value = file.raw
  importResult.value = null
}

// 确认导入
async function confirmImport() {
  if (!selectedImportFile.value) {
    ElMessage.warning(tt('请先选择文件'))
    return
  }

  importing.value = true
  try {
    let result
    if (props.onImport) {
      result = await props.onImport(selectedImportFile.value)
    } else {
      result = await importData(props.entityName, selectedImportFile.value)
    }
    importResult.value = result

    // 服务端汇总提示（如「当前账号未绑定员工档案」）：成功时弹窗会自动关闭，这里用通知兜住
    if (result.message) {
      ElNotification.warning({ title: tt('提示'), message: result.message, duration: 10000 })
    }

    if (result.failCount === 0) {
      ElMessage.success(`${tt('导入成功：共')} ${result.successCount} ${tt('条记录')}`)
      importDialogVisible.value = false
      loadData()
    } else {
      ElMessage.warning(`${tt('导入完成：成功')} ${result.successCount} ${tt('条，失败')} ${result.failCount} ${tt('条')}`)
      if (result.errors && result.errors.length > 0) {
        ElNotification.warning({
          title: tt('导入失败详情'),
          message: result.errors.slice(0, 5).map(e => `${tt('第')}${e.rowIndex}${tt('行')}${e.fieldName ? '[' + e.fieldName + ']' : ''}: ${e.message}`).join('<br/>'),
          duration: 10000,
          dangerouslyUseHTMLString: true
        })
      }
    }
  } catch (err) {
    ElMessage.error(tt('导入失败：') + (err.message || tt('未知错误')))
  } finally {
    importing.value = false
  }
}

// 导出: 调用 onExport 回调或使用默认导出工具
async function handleExport() {
  // 2026-09-20 新增：勾选了行 → 「导出选中」（一次请求，走后端通用导出 POST /api/export/data/{entityName}/selected）
  // 未勾选 → 保持原逻辑（页面 onExport 回调 / 自定义导出列弹窗 → 按当前筛选条件导全量）
  // 注：页面自带 onExport 回调时优先尊重页面的自定义导出（不抢它的行为）
  if (!props.onExport && props.entityName && selectedRows.value.length) {
    const ids = selectedRows.value
      .map(r => r[props.idField])
      .filter(v => v !== undefined && v !== null)
    if (!ids.length) {
      ElMessage.info(tt('选中的记录没有可导出的主键，请刷新后重试'))
      return
    }
    try {
      await exportSelectedRows(props.entityName, ids)
      ElMessage.success(`${tt('已导出选中')} ${ids.length} ${tt('条')}`)
    } catch (err) {
      // 错误已在 exportUtil / 请求拦截器内提示
    }
    return
  }

  if (props.onExport) {
    try {
      const params = { current: page.current, size: page.size, ...searchForm }
      await props.onExport(params)
      ElMessage.success(tt('导出成功'))
    } catch (err) {
      // 错误已在拦截器处理
    }
    return
  }

  if (!props.entityName) {
    ElMessage.info(tt('导出功能未实现，请传入 onExport 回调或 entityName 属性'))
    return
  }

  // 2026-09-24 全站口径：**页面上看到什么列，导出就带什么列**；行 = 当前筛选条件下的全部。
  // 列取 visibleColumns（表格实际渲染的可见列 = 业务列 + 自定义字段列），不是 props.columns，
  // 否则自定义字段列会漏导（实测：物料档案漏"保质期天数/型号"等）。
  try {
    const fields = columnsToExportFields(visibleColumns.value)
    const rows = await collectFilteredRows(props.fetchData, buildListParams(false))
    await exportAsSeen(props.entityName, fields, rows)
    ElMessage.success(tt('导出成功'))
  } catch (err) {
    // 失败原因由 exportUtil 统一提示
  }
}

// 打印: 优先使用后端打印模板, 其次调用 onPrint 回调, 最后浏览器打印
function handlePrint() {
  // 1. 配置了 printBillType 时, 调用后端打印模板
  if (props.printBillType) {
    const rows = selectedRows.value.length ? selectedRows.value : tableData.value
    if (!rows.length) {
      ElMessage.warning(tt('请选择要打印的数据'))
      return
    }
    if (selectedRows.value.length > 1) {
      ElMessage.warning(tt('请选择一条数据进行打印'))
      return
    }
    printBillId.value = rows[0][props.idField]
    printVisible.value = true
    return
  }
  // 2. 传入了 onPrint 回调
  if (props.onPrint) {
    props.onPrint(selectedRows.value.length ? selectedRows.value : tableData.value)
    return
  }
  // 3. 默认: 浏览器原生打印
  ElMessage.info(tt('调用浏览器打印，如需自定义打印请配置 printBillType 或传入 onPrint 回调'))
  window.print()
}

function resetForm() {
  formRef.value?.resetFields()
  Object.keys(formData).forEach(k => delete formData[k])
}

onMounted(() => {
  // 预置搜索条件（数据看板穿透等场景）；默认空对象 = 行为与原先完全一致
  if (props.presetSearch && Object.keys(props.presetSearch).length > 0) {
    Object.assign(searchForm, props.presetSearch)
  }
  loadCustomFieldDefs().finally(() => {
    loadData()
  })
  // 列宽恢复（2026-09-22）：挂载后与每次数据加载完成后各套一次已保存的列宽
  nextTick(restoreColumnWidths)
  watch(() => loading.value, (v) => { if (!v) nextTick(restoreColumnWidths) })
})

defineExpose({ loadData, handleAdd, selectedRows })
</script>

<style lang="scss">
/* ========== 全局表格自适应布局 ==========
   注意：此规则仅作用于「包含 CrudTable（.erp-card）」的页面外壳。
   禁止改回裸 .erp-page —— 全局 .erp-page 会污染所有以 .erp-page 为根的手写页面
   （如物料分类），导致切页后布局塌陷、搜索栏被挡（CrudTable 样式按需注入后才生效）。 */
.erp-page:has(.erp-card) {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  box-sizing: border-box;
  padding: 16px;
}

.erp-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 600px;
  width: 100%;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);
  padding: 16px;

  .el-table {
    flex: 1;
    width: 100% !important;
  }

  .el-table__inner-wrapper {
    width: 100%;
  }

  /* 操作列：flex横向紧凑排列 */
  .op-cell {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 2px 4px;
    min-height: 24px;

    .el-button {
      padding: 2px 6px !important;
      font-size: 12px;
      margin-left: 0 !important;
    }
  }

  /* 操作列td：减少内边距 */
  .el-table__row td.op-column-cell {
    padding: 4px 0;
  }
}

/* 批量删除确认弹窗（弹窗 append-to-body，用全局类名避免 scoped 失效） */
.del-check-block {
  margin-bottom: 12px;
}

.del-check-list {
  max-height: 180px;
  overflow: auto;
  margin-top: 6px;
  padding-left: 4px;
  font-size: 13px;
  line-height: 1.9;
  color: #303133;

  .reason {
    color: #909399;
  }
}

.del-check-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>

<style scoped lang="scss">
.import-dialog-content {
  .import-step {
    padding: 8px 0;

    .step-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;

      .el-icon {
        font-size: 18px;
        color: var(--erp-primary, #409EFF);
      }
    }

    .step-desc {
      font-size: 13px;
      color: #909399;
      margin-bottom: 12px;
    }
  }

  .import-uploader {
    :deep(.el-upload-dragger) {
      width: 100%;
      height: 140px;
    }
  }

  .import-result {
    .result-title {
      font-size: 15px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 12px;
    }

    .import-error-alert {
      margin-top: 12px;

      .error-list {
        max-height: 160px;
        overflow-y: auto;

        .error-item {
          font-size: 12px;
          padding: 2px 0;
          color: #F56C6C;
        }

        .error-more {
          font-size: 12px;
          color: #909399;
          padding-top: 4px;
        }
      }
    }
  }

  .import-tip-box {
    background: linear-gradient(135deg, #fffbe6 0%, #fff7cc 100%);
    border: 1px solid #ffe58f;
    border-radius: 6px;
    padding: 12px 16px;
    margin: 12px 0;

    .tip-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      font-weight: 600;
      color: #d48806;
      margin-bottom: 8px;
    }

    .tip-content {
      font-size: 12px;
      color: #614700;
      line-height: 1.6;

      p {
        margin: 4px 0;
      }

      code {
        background: #fff;
        border: 1px solid #ffe58f;
        border-radius: 3px;
        padding: 1px 4px;
        font-size: 11px;
        color: #d48806;
      }

      .tip-warning {
        color: #cf1322;
        font-weight: 500;
      }
    }
  }
}

/* 审计信息条 */
.audit-info-bar {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: linear-gradient(135deg, #f6f9ff 0%, #f0f5ff 100%);
  border: 1px solid #e6eefa;
  border-radius: 6px;
  font-size: 12px;

  .audit-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .audit-label {
    color: #909399;
    flex-shrink: 0;
  }

  .audit-value {
    color: #303133;
    font-weight: 500;
    word-break: break-all;
  }
}
</style>
