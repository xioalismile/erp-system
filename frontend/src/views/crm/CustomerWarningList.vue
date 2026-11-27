<template>
  <div class="erp-page">
    <div class="page-title-bar">
      <div class="page-title">
        <el-icon style="margin-right:8px;color:#f59e0b;"><WarningFilled /></el-icon>
        销售预警
      </div>
      <div class="page-sub">{{ $tt("销售过程未达标 / 私海超期 / 样品未转化自动预警 · 黄橙红三级 + 处理时限 + 处理闭环") }}</div>
    </div>

    <!-- 销售预警中心（2026-10-07）：等级分档 + 处理时限 + 处理/关闭闭环 -->
    <el-card shadow="never" style="margin-bottom:12px;">
      <SalesWarningPanel />
    </el-card>

    <el-card shadow="never">
      <div class="erp-search-bar">
        <el-button type="warning" v-permission="perm('view')" @click="goToSettings">
          <el-icon style="margin-right:4px;"><Setting /></el-icon>预警配置
        </el-button>
        <el-button type="primary" v-permission="perm('refresh')" @click="loadData">{{ $tt("刷新") }}</el-button>
        <el-tooltip :content="$tt('列设置')" placement="top">
          <el-button :icon="Setting" circle v-permission="perm('columnSetting')" @click="openColumnSetting" />
        </el-tooltip>
        <div style="flex:1"></div>
        <el-tag v-if="thresholdSummary" size="small" type="info" effect="plain" class="threshold-tag">
          {{ thresholdSummary }}
        </el-tag>
        <span style="color:#909399;font-size:13px;margin-left:8px;">共 {{ tableData.length }} 条预警</span>
      </div>

      <!-- 预警列表 -->
      <el-table :data="tableData" v-loading="loading" border stripe :key="configVersion">
        <el-table-column type="index" :label="$tf('序号')" width="60" />
        <!-- 动态列渲染：列顺序由列设置拖拽配置驱动（sortedColumns），单元格按 col.type 分支渲染 -->
        <template v-for="col in visibleSortedColumns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="$tf(col.label)"
            :width="col.width"
            :min-width="col.minWidth"
            :align="col.align || 'left'"
            :sortable="col.sortable === 'local' ? true : (col.sortable ? 'custom' : false)"
            :show-overflow-tooltip="!!col.tooltip"
            :fixed="getColFixed(col.prop, col.fixed)"
          >
            <template v-if="col.type" #default="{ row }">
              <el-tag v-if="col.type === 'tag'" :type="col.tagType ? col.tagType(row) : 'info'" size="small">{{ col.tagText ? col.tagText(row) : (row[col.prop] || '-') }}</el-tag>
            </template>
          </el-table-column>
        </template>
        <AuditColumns />
      </el-table>
    </el-card>

    <!-- 重新分配弹窗 -->
    <el-dialog v-model="assignVisible" :title="$tf('重新分配销售员')" width="450px">
      <el-form label-width="100px">
        <el-form-item :label="$tf('客户')">
          <span>{{ currentRow?.customerName }}</span>
        </el-form-item>
        <el-form-item :label="$tf('销售员')" required>
          <el-select v-model="assignSalesId" :placeholder="$tt('请选择销售员')" filterable clearable style="width:100%">
            <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tf(opt.label)" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="assignVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" @click="confirmAssign" :loading="saving">{{ $tt("确认") }}</el-button>
      </template>
    </el-dialog>

    <!-- 列设置抽屉 -->
    <ColumnSettingDrawer v-model="columnSettingVisible" :page-key="'crm-warning'" :columns="allColumns" />
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import SalesWarningPanel from '@/components/crm/SalesWarningPanel.vue'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled, Setting } from '@element-plus/icons-vue'
import {
  warningList, listWarningConfig, batchAssign
} from '@/api/crm/pool'
import { listAllEmployee } from '@/api/basedata/employee'
import { getSysConfigByKey } from '@/api/system/sysConfig'
import ColumnSettingDrawer from '@/components/ColumnSettingDrawer.vue'
import AuditColumns from '@/components/AuditColumns.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('crm:warning')

const router = useRouter()

// 列定义（用于列设置抽屉+动态渲染）：sortable 'local'=本地排序；type 标记单元格渲染分支（tag 标签）
const allColumns = [
  { prop: 'customerNo', label: '客户编号', width: 140, sortable: 'local' },
  { prop: 'customerName', label: '客户名称', minWidth: 180, sortable: 'local', tooltip: true },
  { prop: 'lastFollowUpDate', label: '最后跟进日期', width: 130, sortable: 'local' },
  { prop: 'overdueDays', label: '超过天数', width: 110, align: 'right', sortable: 'local', type: 'tag', tagType: (row) => overdueTagType(row.overdueDays), tagText: (row) => row.overdueDays + ' 天' },
  { prop: 'assignedSalesName', label: '分配销售员', width: 130, sortable: 'local' },
  { prop: 'warningType', label: '预警类型', width: 120, sortable: 'local', type: 'tag', tagType: (row) => warningTypeTag(row.warningType), tagText: (row) => row.warningType || '跟进预警' },
  { prop: 'contactPhone', label: '联系电话', width: 140, sortable: 'local' }
]
const { columnSettingVisible, openColumnSetting, isColVisible, getColFixed, configVersion, sortedColumns } = useColumnSetting('crm-warning', allColumns)
// 可见且按列设置拖拽顺序排列的列（v-for 渲染用，避免 v-for+v-if 反模式）
const visibleSortedColumns = computed(() => sortedColumns.value.filter(col => isColVisible(col.prop)))

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const employeeOptions = ref([])

const warningConfigs = ref([])
const remindEnabled = ref(true)

const thresholdSummary = computed(() => {
  const parts = []
  warningConfigs.value.forEach(c => {
    if (c.enabled) {
      parts.push(`${c.warningType || '跟进'}≥${c.thresholdDays || 7}天`)
    }
  })
  return parts.length ? `当前阈值：${parts.join(' / ')}` : ''
})

async function loadWarnConfig() {
  try {
    const warnConfigs = await listWarningConfig()
    warningConfigs.value = Array.isArray(warnConfigs) ? warnConfigs : (warnConfigs?.records || [])
  } catch (e) {
    warningConfigs.value = [
      { warningType: '跟进预警', thresholdDays: 7, enabled: 1, level: 'warning' },
      { warningType: '开单预警', thresholdDays: 30, enabled: 1, level: 'danger' },
      { warningType: '流失预警', thresholdDays: 60, enabled: 1, level: 'danger' }
    ]
  }

  try {
    const remEnabled = await getSysConfigByKey('crm.warning.remind.enabled')
    remindEnabled.value = remEnabled ? remEnabled.configValue === 'true' : true
  } catch (e) { remindEnabled.value = true }
}

function goToSettings() {
  router.push({ path: '/system/settings', query: { group: 'business', item: 'crm-warning' } })
}

async function loadData() {
  loading.value = true
  try {
    const res = await warningList()
    tableData.value = Array.isArray(res) ? res : (res?.records || [])
  } catch (e) {
    ElMessage.error(e.message || tt('加载失败'))
  } finally {
    loading.value = false
  }
}

function overdueTagType(days) {
  if (days == null) return 'info'
  const lossConfig = warningConfigs.value.find(c => c.warningType === '流失预警')
  const orderConfig = warningConfigs.value.find(c => c.warningType === '开单预警')
  if (lossConfig && lossConfig.enabled && days >= (lossConfig.thresholdDays || 60)) {
    return lossConfig.level || 'danger'
  }
  if (orderConfig && orderConfig.enabled && days >= (orderConfig.thresholdDays || 30)) {
    return orderConfig.level || 'warning'
  }
  return 'info'
}

function warningTypeTag(t) {
  const map = { '跟进预警': 'warning', '开单预警': 'danger', '流失预警': 'danger' }
  return map[t] || 'info'
}

const assignVisible = ref(false)
const currentRow = ref(null)
const assignSalesId = ref(null)

async function confirmAssign() {
  if (!assignSalesId.value) {
    ElMessage.warning(tt('请选择销售员'))
    return
  }
  saving.value = true
  try {
    const id = currentRow.value.customerId || currentRow.value.id
    await batchAssign([id], assignSalesId.value)
    ElMessage.success(tt('分配成功'))
    assignVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || tt('分配失败'))
  } finally {
    saving.value = false
  }
}

async function loadEmployeeOptions() {
  try {
    const list = await listAllEmployee()
    employeeOptions.value = (Array.isArray(list) ? list : (list?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
  } catch (e) { console.warn('加载员工失败', e) }
}

onMounted(() => {
  loadWarnConfig()
  loadData()
  loadEmployeeOptions()
})
useAutoRefresh(() => {
  loadWarnConfig()
  loadData()
  loadEmployeeOptions()
})
</script>

<style scoped>
.erp-page { padding: 16px; }
.page-title-bar {
  margin-bottom: 16px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fffbf0 0%, #fff7ed 100%);
  border-left: 4px solid #f59e0b;
  border-radius: 8px;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  display: flex;
  align-items: center;
}
.page-sub {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  margin-left: 28px;
}
.erp-search-bar { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.threshold-tag { font-size: 12px; }
</style>
