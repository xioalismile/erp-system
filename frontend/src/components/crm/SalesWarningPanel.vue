<template>
  <div class="sw-panel">
    <!-- 汇总条：点一下即按该项筛选（原来只是展示，点了没反应） -->
    <div class="sw-bar">
      <div class="sw-item clickable" :class="{ on: !search.status && !search.level }" @click="quickFilter('', '')">
        <div class="sw-label">{{ $tt("未处理") }}</div>
        <div class="sw-val">{{ summary['未处理'] || 0 }}</div>
      </div>
      <div class="sw-item clickable" :class="{ on: search.level === '红' }" @click="quickFilter('红', '')">
        <div class="sw-label">{{ $tt("红色 · 严重") }}</div>
        <div class="sw-val red">{{ summary['红'] || 0 }}</div>
      </div>
      <div class="sw-item clickable" :class="{ on: search.level === '橙' }" @click="quickFilter('橙', '')">
        <div class="sw-label">{{ $tt("橙色 · 警告") }}</div>
        <div class="sw-val orange">{{ summary['橙'] || 0 }}</div>
      </div>
      <div class="sw-item clickable" :class="{ on: search.level === '黄' }" @click="quickFilter('黄', '')">
        <div class="sw-label">{{ $tt("黄色 · 提醒") }}</div>
        <div class="sw-val yellow">{{ summary['黄'] || 0 }}</div>
      </div>
      <div class="sw-item clickable" :class="{ on: search.status === '已处理待确认' }" @click="quickFilter('', '已处理待确认')">
        <div class="sw-label">{{ $tt("已处理待确认") }}</div>
        <div class="sw-val">{{ summary['已处理待确认'] || 0 }}</div>
      </div>
      <div style="flex:1"></div>
      <div class="sw-actions">
        <el-button type="primary" size="small" :loading="busy" @click="onGenerate">{{ $tt("立即结算") }}</el-button>
        <el-button size="small" :loading="busy" @click="onEscalate">{{ $tt("超时升级") }}</el-button>
        <el-button size="small" @click="loadAll">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="erp-search-bar">
      <el-select v-model="search.level" :placeholder="$tt('等级')" style="width:120px;" clearable>
        <el-option v-for="l in ['红','橙','黄']" :key="l" :label="l" :value="l" />
      </el-select>
      <el-select v-model="search.status" :placeholder="$tt('状态')" style="width:140px;" clearable>
        <el-option v-for="s in ['待处理','处理中','已处理待确认','已关闭']" :key="s" :label="s" :value="s" />
      </el-select>
      <!-- 文档 4.2 页头要求：类型筛选 -->
      <el-select v-model="search.warningType" :placeholder="$tt('预警类型')" style="width:170px;" clearable filterable>
        <el-option v-for="t in WARNING_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input v-model="search.keyword" :placeholder="$tt('预警编号/对象/负责人')" style="width:200px;" clearable />
      <el-button type="primary" @click="handleSearch">{{ $tt("查询") }}</el-button>
      <el-button @click="handleReset">{{ $tt("重置") }}</el-button>
      <!-- 文档 4.2 页头要求：预警配置入口 -->
      <el-button @click="goConfig">{{ $tt("预警配置") }}</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="records" v-loading="loading" border stripe size="small">
      <el-table-column :label="$tf('等级')" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="lvType(row.warningLevel)">{{ row.warningLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="warningType" :label="$tf('预警类型')" width="160" />
      <el-table-column prop="targetName" :label="$tf('对象')" min-width="150" show-overflow-tooltip />
      <el-table-column prop="triggerValue" :label="$tf('触发值')" width="100" />
      <el-table-column prop="thresholdValue" :label="$tf('阈值')" width="100" />
      <el-table-column prop="pushTo" :label="$tf('推送对象')" width="150" show-overflow-tooltip />
      <el-table-column :label="$tf('处理时限')" width="160">
        <template #default="{ row }">
          <span :class="remainClass(row)">{{ deadlineText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$tf('状态')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === '已关闭' ? 'info' : (row.status === '已处理待确认' ? 'success' : 'warning')">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$tf('操作')" width="320" fixed="right" align="center">
        <template #default="{ row }">
          <template v-if="row.status !== '已关闭'">
            <el-tooltip :content="handleTip(row)" placement="top">
              <el-button type="primary" link size="small" v-permission="perm('handle')" @click="goHandle(row)">{{ $tt("去处理") }}</el-button>
            </el-tooltip>
            <el-button v-if="row.status === '待处理'" type="warning" link size="small" @click="onClaim(row)">{{ $tt("认领") }}</el-button>
            <el-button v-if="row.status !== '已处理待确认'" type="success" link size="small" v-permission="perm('handle')" @click="openHandle(row)">{{ $tt("登记结果") }}</el-button>
            <!-- 关闭 = 主管确认结案，文档规定「仅主管/管理员」；本人不能确认自己的预警 -->
            <el-button v-if="canClose" type="danger" link size="small" v-permission="perm('closeWarning')" @click="openClose(row)">{{ $tt("关闭") }}</el-button>
            <!-- 重新分配销售员（文档 4.2）：仅主管/管理员；对象非「客户」的预警禁用并给出中文说明 -->
            <el-tooltip v-if="canClose" :content="reassignTip(row)" placement="top">
              <span style="margin-left:8px;">
                <el-button type="warning" link size="small" :disabled="!canReassign(row)" v-permission="perm('reassign')" @click="openReassign(row)">{{ $tt("重新分配") }}</el-button>
              </span>
            </el-tooltip>
            <span v-if="!canClose && row.status === '已处理待确认'" class="await-tip">{{ $tt("待主管确认") }}</span>
          </template>
          <span v-else class="muted">{{ $tt("已闭环") }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <div class="empty-tip">{{ $tt("当前没有待处理的销售预警") }}</div>
      </template>
    </el-table>

    <div style="margin-top:12px;display:flex;justify-content:flex-end;">
      <el-pagination v-model:current-page="page.current" v-model:page-size="page.size"
        :page-sizes="[10, 20, 50, 100]" :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData" @current-change="loadData" />
    </div>

    <div class="legend">
      {{ $tt("黄 = 本人 / 当日 · 橙 = 本人 + 市场总监 / 24 小时 · 红 = 本人 + 总监 + 总经理 / 48 小时 · 超时未处理自动升级一级并重新计时") }}
    </div>

    <!-- 处理 / 关闭弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'handle' ? '登记处理结果' : '关闭预警'" width="620px" :close-on-click-modal="false">
      <el-alert v-if="dialogMode === 'handle'" type="info" :closable="false" style="margin-bottom:12px;"
                :title="$tt('处理 = 回到出问题的页面把数据补上')"
                :description="handleTip(current || {})" />
      <el-form label-width="90px">
        <el-form-item :label="$tf('预警')">
          <span>{{ current?.warningType }} · {{ current?.targetName }}（{{ current?.warningLevel }}）</span>
        </el-form-item>
        <el-form-item :label="$tf('处理说明')" required>
          <el-input v-model="form.remark" type="textarea" :rows="3"
                    :placeholder="dialogMode === 'handle' ? '写清在哪个页面补了什么、结果如何' : '写清关闭原因（已沟通、客户已流失等）'" />
        </el-form-item>
        <el-form-item :label="$tf('凭证')">
          <ImageUploader v-model="form.attachment" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button v-if="dialogMode === 'handle'" @click="goHandle(current)">{{ $tt("先去处理") }}</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ $tt("确定") }}</el-button>
      </template>
    </el-dialog>

    <!-- 重新分配销售员（沿用「销售预警」宿主页已有的客户重新分配能力） -->
    <el-dialog v-model="reassignVisible" :title="$tt('重新分配销售员')" width="460px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item :label="$tf('预警')">
          <span>{{ reassignRow?.warningType }} · {{ reassignRow?.targetName }}（{{ reassignRow?.warningLevel }}）</span>
        </el-form-item>
        <el-form-item :label="$tf('客户')">
          <span>{{ reassignRow?.targetName || '' }}</span>
        </el-form-item>
        <el-form-item :label="$tf('销售员')" required>
          <el-select v-model="reassignSalesId" :placeholder="$tt('请选择销售员')" filterable clearable style="width:100%">
            <el-option v-for="opt in employeeOptions" :key="opt.value" :label="$tt(opt.label)" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-permission="perm('dlgCancel')" @click="reassignVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" v-permission="perm('dlgConfirm')" :loading="saving" @click="confirmReassign">{{ $tt("确定") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  listSalesWarning, salesWarningSummary, generateSalesWarning,
  escalateSalesWarning, handleSalesWarning, closeSalesWarning, claimSalesWarning
} from '@/api/crm/salesWarning'
import ImageUploader from '@/components/ImageUploader.vue'
import { tt } from '@/i18n'
import { useRouter } from 'vue-router'
import { usePagePerm } from '@/utils/pagePerm'
import { batchAssign } from '@/api/crm/pool'
import { listAllEmployee } from '@/api/basedata/employee'

const router = useRouter()
// 页面对象 crm:warning（按钮权限，默认口径：能看见菜单即全部按钮可见）
const perm = usePagePerm('crm:warning')

/**
 * 预警处理 = 回到出问题的页面把数据补上（2026-10-07 用户口径）
 * 本页只承担「登记处理结果」，不能把处理简化成写一句话。
 *
 * 2026-10-09 补齐：
 *  ① 键名与后端真实预警类型对齐 —— 后端写的是「私海30天未联系 / 私海45天未联系」，
 *     原键写成「私海超期未联系」→ 这两类点「去处理」只会弹"没有配置跳转入口"；
 *  ② 补「物流：轨迹24h无更新 / 拒收签收失败 / 在途超期未签收」三类（跳物流监控看板）；
 *  ③ 每个类型标记 kw = 是否把该预警的对象（客户名 / 单据号）作为条件带到目标页。
 */
const HANDLE_ROUTE = {
  '日回访未达标':        { path: '/crm/follow-up', kw: true,  tip: '去「跟进记录」补今天的回访（通话≥30秒或微信有实质回复）' },
  '日新增微信未达标':    { path: '/crm/customer',   kw: false, tip: '去「我的私海」新增客户并填写微信号' },
  '日深入交流未达标':    { path: '/crm/follow-up',  kw: true,  tip: '去「跟进记录」补一条深入交流（通话≥5分钟或微信≥10句）' },
  '周样品未寄出':        { path: '/freesample/edit', kw: false, tip: '去「免费样品」新建样品单并填写快递单号' },
  '朋友圈推广未达标':    { path: '/crm/sales-activity', kw: false, query: { tab: 'promotion' }, tip: '去「销售过程 → 推广记录」新增一条并上传截图' },
  'RFID 覆盖未达标':     { path: '/crm/sales-activity', kw: false, query: { tab: 'promotion' }, tip: '去「推广记录」新增 RFID素材 推广并选择发送对象' },
  '私海30天未联系':      { path: '/crm/customer',   kw: true,  tip: '去「我的私海」找到该客户补一次联系并写跟进' },
  '私海45天未联系':      { path: '/crm/customer',   kw: true,  tip: '去「我的私海」找到该客户补一次联系并写跟进' },
  '新客户7天未联系':     { path: '/crm/customer',   kw: true,  tip: '去「我的私海」找到该客户补一次联系并写跟进' },
  '未分配客户':          { path: '/crm/customer',   kw: true,  tip: '去「我的私海」给该客户分配销售员（重新分配）' },
  '样品15天未转化':      { path: '/crm/follow-up',  kw: true,  tip: '去「跟进记录」对已收样客户做一次回访' },
  '报价7天未跟进':       { path: '/sale/quotes',     kw: true,  tip: '去「销售报价单」跟进该客户' },
  '回款逾期':            { path: '/finance',         kw: false, tip: '去「财务管理」查看应收并安排催收' },
  '物流：轨迹24h无更新': { path: '/logistics/dashboard', kw: true, tip: '去「物流监控看板」看该单轨迹并催件' },
  '物流：拒收/签收失败': { path: '/logistics/dashboard', kw: true, tip: '去「物流监控看板」处理拒收/签收失败' },
  '物流：在途超期未签收': { path: '/logistics/dashboard', kw: true, tip: '去「物流监控看板」联系承运商催签收' }
}

/**
 * 取预警类型对应的处理入口。
 * 先精确匹配；匹配不到再按前 4 个字关键词匹配（预警类型文案微调时不至于跳转失效）。
 */
function routeOf(type) {
  if (!type) return null
  if (HANDLE_ROUTE[type]) return HANDLE_ROUTE[type]
  const key = Object.keys(HANDLE_ROUTE).find(k =>
    type.includes(k.slice(0, 4)) || k.includes(type.slice(0, 4)))
  return key ? HANDLE_ROUTE[key] : null
}

function handleTip(row) {
  const r = routeOf(row.warningType)
  return r ? r.tip : tt('去对应页面处理')
}

function goHandle(row) {
  const r = routeOf(row.warningType)
  if (!r) { ElMessage.info(tt('该预警类型没有配置跳转入口，请手工处理')); return }
  const query = { ...(r.query || {}) }
  // 带上"去看哪个对象"的条件：目标页按 kw 定位该客户 / 该单据（2026-10-09）
  const kw = row.targetName || row.triggerValue || ''
  if (r.kw && kw) query.kw = kw
  router.push({ path: r.path, query })
}

const loading = ref(false)
const saving = ref(false)
const busy = ref(false)
const records = ref([])
const summary = reactive({})
const page = reactive({ current: 1, size: 20, total: 0 })
/** 文档第八节的预警类型清单（类型筛选用） */
const WARNING_TYPES = [
  '日回访未达标', '日新增微信未达标', '日深入交流未达标', '周样品未寄出',
  '私海30天未联系', '私海45天未联系', '朋友圈推广未达标', 'RFID 覆盖未达标',
  '样品15天未转化', '报价7天未跟进', '新客户7天未联系', '回款逾期',
  '物流：轨迹24h无更新', '物流：拒收/签收失败', '物流：在途超期未签收'
]
const search = reactive({ level: '', status: '', warningType: '', keyword: '' })

const dialogVisible = ref(false)
const dialogMode = ref('handle')
const current = ref(null)
const form = reactive({ remark: '', attachment: '' })

// 重新分配销售员（复用「客户重新分配」现有接口 /customer/pool/batch-assign）
const reassignVisible = ref(false)
const reassignRow = ref(null)
const reassignSalesId = ref(null)
const employeeOptions = ref([])

function lvType(l) {
  return l === '红' ? 'danger' : (l === '橙' ? 'warning' : 'info')
}
function remainClass(row) {
  if (row.status === '已关闭') return 'muted'
  if (row.remainSeconds === null || row.remainSeconds === undefined) return ''
  return row.remainSeconds < 0 ? 'overdue' : (row.remainSeconds < 3600 ? 'soon' : '')
}
function deadlineText(row) {
  if (row.status === '已关闭') return '已闭环'
  if (row.remainSeconds === null || row.remainSeconds === undefined) return ''
  if (row.remainSeconds < 0) return '已超时 ' + Math.abs(Math.round(row.remainSeconds / 3600)) + ' 小时'
  const h = Math.floor(row.remainSeconds / 3600)
  return h > 0 ? ('剩余 ' + h + ' 小时') : ('剩余 ' + Math.round(row.remainSeconds / 60) + ' 分钟')
}

async function loadData() {
  loading.value = true
  try {
    const res = await listSalesWarning({
      current: page.current, size: page.size,
      level: search.level || undefined,
      status: search.status || undefined,
      warningType: search.warningType || undefined,
      keyword: search.keyword || undefined
    })
    records.value = res?.records || []
    page.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  const res = await salesWarningSummary()
  Object.assign(summary, res || {})
}

function loadAll() { loadData(); loadSummary() }
function handleSearch() { page.current = 1; loadData() }
function handleReset() { search.level = ''; search.status = ''; search.warningType = ''; search.keyword = ''; handleSearch() }

/** 预警配置入口：跳设置中心 → CRM 预警设置（文档 4.2） */
function goConfig() {
  router.push('/system/settings?group=business&item=crm-warning')
}

/** 点汇总卡片即筛选（再点一次同一张 = 取消该筛选） */
function quickFilter(level, status) {
  const same = search.level === level && search.status === status
  search.level = same ? '' : level
  search.status = same ? '' : status
  search.keyword = ''
  handleSearch()
}

/**
 * 关闭（= 主管确认结案）的权限。
 * 立项登记表第 177 行规定：「关闭（仅主管/管理员，转已关闭）」——本人不得确认自己的预警。
 */
const userStore = useUserStore()
const canClose = computed(() => {
  const roles = Array.isArray(userStore.roles) ? userStore.roles : []
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN') || roles.includes('SALES_MANAGER')
})

/** 仅「对象类型 = 客户」且带客户ID的预警才可重新分配销售员（文档 4.2） */
function canReassign(row) {
  return !!row && row.targetType === '客户' && row.targetId != null
}
function reassignTip(row) {
  if (canReassign(row)) return tt('把该客户改派给其他销售员（沿用客户重新分配）')
  return tt('该预警的对象不是客户（当前为「') + (row?.targetType || '空') + tt('」），不支持重新分配销售员')
}
function openReassign(row) {
  if (!canReassign(row)) { ElMessage.warning(reassignTip(row)); return }
  reassignRow.value = row
  reassignSalesId.value = null
  reassignVisible.value = true
  if (!employeeOptions.value.length) loadEmployeeOptions()
}
async function confirmReassign() {
  if (!reassignSalesId.value) { ElMessage.warning(tt('请选择销售员')); return }
  saving.value = true
  try {
    await batchAssign([reassignRow.value.targetId], reassignSalesId.value)
    ElMessage.success(tt('已重新分配销售员'))
    reassignVisible.value = false
    loadAll()
  } catch (e) {
    ElMessage.error(e.message || tt('重新分配失败'))
  } finally {
    saving.value = false
  }
}
async function loadEmployeeOptions() {
  try {
    const list = await listAllEmployee()
    employeeOptions.value = (Array.isArray(list) ? list : (list?.records || []))
      .map(e => ({ value: e.employeeId, label: e.employeeName + (e.deptName ? ' (' + e.deptName + ')' : '') }))
      .filter(o => o.value != null)
  } catch (e) { employeeOptions.value = [] }
}

/** 认领：待处理 → 处理中（闭环状态机第二态，文档第八节） */
async function onClaim(row) {
  try {
    await claimSalesWarning(row.warningId)
    ElMessage.success(tt('已认领，状态转为处理中'))
    loadAll()
  } catch (e) {
    ElMessage.error(e.message || tt('认领失败'))
  }
}

async function onGenerate() {
  busy.value = true
  try {
    const res = await generateSalesWarning()
    if (res?.skipped) {
      ElMessage.warning(res.skipped)
    } else {
      ElMessage.success(tt('结算完成，新增预警 ') + (res?.created || 0) + tt(' 条'))
    }
    loadAll()
  } finally {
    busy.value = false
  }
}

async function onEscalate() {
  busy.value = true
  try {
    const res = await escalateSalesWarning()
    ElMessage.success(tt('超时升级完成，升级 ') + (res?.escalated || 0) + tt(' 条'))
    loadAll()
  } finally {
    busy.value = false
  }
}

function openHandle(row) {
  dialogMode.value = 'handle'
  current.value = row
  form.remark = ''
  form.attachment = ''
  dialogVisible.value = true
}
function openClose(row) {
  dialogMode.value = 'close'
  current.value = row
  form.remark = ''
  form.attachment = ''
  dialogVisible.value = true
}

async function submit() {
  if (!form.remark || !form.remark.trim()) {
    ElMessage.warning(tt('请填写处理说明'))
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'handle') {
      await handleSalesWarning(current.value.warningId, { remark: form.remark, attachment: form.attachment })
      ElMessage.success(tt('已处理，等待主管确认'))
    } else {
      await closeSalesWarning(current.value.warningId, { remark: form.remark })
      ElMessage.success(tt('已关闭'))
    }
    dialogVisible.value = false
    loadAll()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadAll()
  loadEmployeeOptions()
})
</script>

<style scoped>
.sw-panel { padding-top: 4px; }
.sw-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.sw-item { min-width: 110px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px 12px; }
.sw-item.clickable { cursor: pointer; transition: all .15s; }
.sw-item.clickable:hover { border-color: var(--el-color-primary); }
.sw-item.on { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.sw-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.sw-val { font-size: 18px; font-weight: 600; font-variant-numeric: tabular-nums; }
.sw-val.red { color: #f56c6c; }
.sw-val.orange { color: #e6a23c; }
.sw-val.yellow { color: #b88230; }
.sw-actions { display: flex; gap: 8px; }
.erp-search-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.overdue { color: #f56c6c; font-weight: 600; }
.soon { color: #e6a23c; }
.muted { color: #c0c4cc; }
.await-tip { color: #e6a23c; font-size: 12px; }
.empty-tip { padding: 20px 0; color: #909399; text-align: center; font-size: 13px; }
.legend { margin-top: 10px; font-size: 12px; color: #909399; }
</style>
