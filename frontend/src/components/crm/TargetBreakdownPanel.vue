<template>
  <div class="tb-panel">
    <div class="tb-head">
      <span class="tb-title">{{ $tt("销售目标 · 五级分解") }}</span>
      <el-select v-model="employeeId" filterable :placeholder="$tt('请选择销售人员')" style="width:220px;" @change="load">
        <el-option v-for="e in employees" :key="e.employeeId" :label="e.employeeName" :value="e.employeeId" />
      </el-select>
      <el-input-number v-model="year" :min="2020" :max="2100" controls-position="right" style="width:130px;" @change="load" />
      <el-button @click="load">{{ $tt("刷新") }}</el-button>
      <div style="flex:1"></div>
      <span class="tb-note">{{ $tt("月可手工覆盖；覆盖后周/日目标自动重算，尾差补到该月最后一个周") }}</span>
    </div>

    <el-table v-if="data.available" :data="data.rows" border size="small">
      <el-table-column prop="level" :label="$tf('层级')" min-width="200" />
      <el-table-column :label="$tf('目标')" width="140" align="right">
        <template #default="{ row }">{{ money(row.target) }}</template>
      </el-table-column>
      <el-table-column :label="$tf('已完成')" width="140" align="right">
        <template #default="{ row }">{{ money(row.actual) }}</template>
      </el-table-column>
      <el-table-column :label="$tf('完成率')" width="120">
        <template #default="{ row }">
          <span :class="rateClass(row.status)">{{ row.rate }}%</span>
        </template>
      </el-table-column>
      <el-table-column :label="$tf('状态')" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="tagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="data.available" class="tb-months">
      <div class="tb-months-title">{{ $tt("各月目标（点击可手工覆盖）") }}</div>
      <div class="tb-month-grid">
        <div v-for="m in data.months" :key="m.month" class="tb-month" :class="{ manual: m.manual }"
             @click="openEdit(m)">
          <div class="tb-month-no">{{ m.month }} 月</div>
          <div class="tb-month-val">{{ money(m.target) }}</div>
          <div class="tb-month-tag">{{ m.manual ? '手工' : '公式' }}</div>
        </div>
      </div>
    </div>

    <div v-if="!data.available" class="tb-empty">{{ data.reason || $tt("请选择销售人员") }}</div>

    <!-- 页脚：分解公式与尾差规则（文档 4.4 要求） -->
    <div class="tb-foot">
      {{ $tt("分解公式与尾差规则：季之和 = 年；月 = 季 ÷ 3；周 = 月 ÷ 当月周数（尾差补到该月最后一个周）；日 = 周 ÷ 工作日数（尾差补到最后一个工作日）。") }}
    </div>

    <el-dialog v-model="dialogVisible" :title="$tt('手工覆盖月度目标')" width="460px">
      <el-form label-width="110px">
        <el-form-item :label="$tf('月份')">
          <span>{{ editRow?.month }} 月（当前 {{ money(editRow?.target) }}）</span>
        </el-form-item>
        <el-form-item :label="$tf('目标金额')">
          <el-input-number v-model="editAmount" :min="0" :step="10000" controls-position="right" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('override')" @click="clearMonth" :loading="saving">{{ $tt("取消手工覆盖") }}</el-button>
        <el-button v-permission="perm('dlgCancel')" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('override')" :loading="saving" @click="saveMonth">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getBreakdown, saveMonthTarget, clearMonthTarget } from '@/api/crm/salesTargetBreakdown'
import { listAllEmployee } from '@/api/basedata/employee'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

// 本组件挂在「销售目标」页，沿用该页页面对象码（按钮权限）
const perm = usePagePerm('crm:sales-target')

const employees = ref([])
const employeeId = ref(null)
const year = ref(new Date().getFullYear())
const data = reactive({ available: false, rows: [], months: [] })

const dialogVisible = ref(false)
const saving = ref(false)
const editRow = ref(null)
const editAmount = ref(0)

function money(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function rateClass(s) { return s === '绿' ? 'ok' : (s === '黄' ? 'warn' : 'bad') }
function tagType(s) { return s === '绿' ? 'success' : (s === '黄' ? 'warning' : 'danger') }
function statusText(s) { return s === '绿' ? '达成' : (s === '黄' ? '接近达成' : '缺口较大') }

async function load() {
  if (!employeeId.value) { data.available = false; data.reason = tt('请选择销售人员'); return }
  const res = await getBreakdown({ year: year.value, employeeId: employeeId.value })
  Object.assign(data, res || {})
}

async function loadEmployees() {
  try {
    const res = await listAllEmployee()
    employees.value = Array.isArray(res) ? res : (res?.records || [])
    if (employees.value.length && !employeeId.value) {
      employeeId.value = employees.value[0].employeeId
      await load()
    }
  } catch (e) { /* 忽略 */ }
}

function openEdit(m) {
  editRow.value = m
  editAmount.value = Number(m.target || 0)
  dialogVisible.value = true
}

async function saveMonth() {
  saving.value = true
  try {
    await saveMonthTarget({ year: year.value, employeeId: employeeId.value, monthNo: editRow.value.month, amount: editAmount.value })
    ElMessage.success(tt('已保存，周/日目标已按新月份目标重算'))
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}

async function clearMonth() {
  saving.value = true
  try {
    await clearMonthTarget({ year: year.value, employeeId: employeeId.value, monthNo: editRow.value.month })
    ElMessage.success(tt('已取消手工覆盖，回到 季÷3'))
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}

onMounted(loadEmployees)
</script>

<style scoped>
.tb-panel { padding-top: 4px; }
.tb-head { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.tb-title { font-size: 15px; font-weight: 600; }
.tb-note { font-size: 12px; color: #909399; }
.tb-months { margin-top: 14px; }
.tb-months-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.tb-month-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.tb-month { width: 110px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px; cursor: pointer; }
.tb-month.manual { border-color: var(--el-color-primary); }
.tb-month-no { font-size: 12px; color: #909399; }
.tb-month-val { font-size: 13px; font-weight: 600; margin: 4px 0; font-variant-numeric: tabular-nums; }
.tb-month-tag { font-size: 11px; color: #909399; }
.tb-month.manual .tb-month-tag { color: var(--el-color-primary); }
.tb-empty { padding: 24px 0; text-align: center; color: #909399; font-size: 13px; }
.tb-foot { margin-top: 14px; padding-top: 10px; border-top: 1px dashed var(--el-border-color-lighter); font-size: 12px; color: #909399; line-height: 1.6; }
.ok { color: #67c23a; font-weight: 600; }
.warn { color: #e6a23c; font-weight: 600; }
.bad { color: #f56c6c; font-weight: 600; }
</style>
