<template>
  <div class="erp-page log-page">
    <div class="log-layout">
      <!-- 左侧：模块分组导航 -->
      <div class="module-nav">
        <div class="nav-header">
          <span class="nav-title">{{ $tt("操作日志") }}</span>
        </div>
        <el-menu :default-active="activeModule" class="module-menu" @select="onSelectModule">
          <el-menu-item index="all">
            <el-icon><List /></el-icon>
            <span>{{ $tt("全部日志") }}</span>
          </el-menu-item>
          <el-sub-menu v-for="mod in moduleGroups" :key="mod.key" :index="mod.key">
            <template #title>
              <el-icon><component :is="mod.icon" /></el-icon>
              <span>{{ $tf(mod.label) }}</span>
            </template>
            <el-menu-item
              v-for="sub in mod.children"
              :key="sub.key"
              :index="sub.key"
            >
              {{ $tf(sub.label) }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>

      <!-- 右侧：日志列表 -->
      <div class="log-content">
        <div class="filter-bar">
          <span class="current-module-label">{{ currentModuleLabel }}</span>
          <el-input
            v-model="query.keyword"
            :placeholder="$tt('关键词')"
            class="filter-input"
            clearable
            @keyup.enter="onSearch"
          />
          <!-- 2026-10-01 新增：按操作人（姓名/账号/用户编码）筛选 -->
          <el-input
            v-model="query.operateUser"
            :placeholder="$tt('操作人')"
            class="filter-input"
            clearable
            @keyup.enter="onSearch"
          />
          <el-select v-model="query.operationType" :placeholder="$tt('操作类型')" clearable class="filter-select">
            <el-option :label="$tf('新增')" value="新增" />
            <el-option :label="$tf('编辑')" value="编辑" />
            <el-option :label="$tf('删除')" value="删除" />
            <el-option :label="$tf('审核')" value="审核" />
            <el-option :label="$tf('重置草稿')" value="重置草稿" />
            <el-option :label="$tf('登录')" value="登录" />
            <el-option :label="$tf('登出')" value="登出" />
            <el-option :label="$tf('导出')" value="导出" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$tt('至')"
            :start-placeholder="$tt('开始日期')"
            :end-placeholder="$tt('结束日期')"
            value-format="YYYY-MM-DD"
            class="filter-date"
          />
          <el-button v-permission="perm('query')" type="primary" @click="onSearch">{{ $tt("查询") }}</el-button>
          <el-button v-permission="perm('reset')" @click="onReset">{{ $tt("重置") }}</el-button>
        </div>

        <el-table :data="tableData" border v-loading="loading" stripe @sort-change="onSortChange" @expand-change="onExpandChange">
          <!-- 变更明细展开列：与单据页「操作日志」弹窗口径完全一致（同一接口 /system/log/detail/{logId}） -->
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="log-detail">
                <div v-if="row.failReason" class="log-detail-fail">
                  <el-icon style="margin-top:2px;"><WarningFilled /></el-icon>
                  <span>{{ $tt('失败原因：') }}{{ row.failReason }}</span>
                </div>
                <div v-if="loadingMap[row.logId]" class="log-detail-tip">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>{{ $tt('加载中...') }}</span>
                </div>
                <div v-else-if="detailErrorMap[row.logId]" class="log-detail-tip">
                  {{ $tt('（明细加载失败）') }}
                </div>
                <div v-else-if="(detailMap[row.logId] || []).length > 0" class="log-detail-table">
                  <table class="diff-table">
                    <thead>
                      <tr>
                        <th width="26%">{{ $tt('字段') }}</th>
                        <th width="28%">{{ $tt('修改前') }}</th>
                        <th width="28%">{{ $tt('修改后') }}</th>
                        <!-- 2026-10-01 新增：与单据页「操作日志」弹窗口径一致，每条变更都能看到是谁改的 -->
                        <th width="18%">{{ $tt('修改人') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="d in detailMap[row.logId]" :key="d.detailId">
                        <td class="diff-field">{{ d.fieldDesc || d.fieldName }}</td>
                        <td class="diff-old">
                          <span v-if="d.oldValue !== null && d.oldValue !== undefined && d.oldValue !== ''">{{ d.oldValue }}</span>
                          <span v-else class="diff-empty">{{ $tt('（空）') }}</span>
                        </td>
                        <td class="diff-new">
                          <span v-if="d.newValue !== null && d.newValue !== undefined && d.newValue !== ''">{{ d.newValue }}</span>
                          <span v-else class="diff-empty">{{ $tt('（空）') }}</span>
                        </td>
                        <td class="diff-user" :title="row.userName || ''">{{ row.realName || row.userName || '-' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div v-else-if="!row.failReason" class="log-detail-tip">
                  <el-icon><InfoFilled /></el-icon>
                  <span>{{ $tt('该操作无字段级变更明细') }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="operateTime" :label="$tf('操作时间')" min-width="160" sortable="custom"/>
          <el-table-column :label="$tf('操作人')" min-width="110">
            <template #default="{ row }">
              {{ row.realName || row.userName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="deptName" :label="$tf('部门')" min-width="120" sortable="custom"/>
          <el-table-column :label="$tf('操作类型')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="operationTagType(row.operateType)" size="small">
                {{ $dt(row.operateType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="moduleName" :label="$tf('模块名称')" min-width="120" sortable="custom"/>
          <el-table-column prop="operateContent" :label="$tf('操作内容')" min-width="220" show-overflow-tooltip sortable="custom"/>
          <el-table-column :label="$tf('操作结果')" width="120" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="row.failReason" :content="row.failReason" placement="top">
                <el-tag type="danger" size="small">{{ $dt(row.operateResult) }}</el-tag>
              </el-tooltip>
              <el-tag v-else :type="row.operateResult === '成功' ? 'success' : 'danger'" size="small">
                {{ $dt(row.operateResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="billNo" :label="$tf('单据编号')" min-width="140" sortable="custom">
            <template #default="{ row }">
              <!-- 单据编号可直接打开目标单据（billDocType/billId 由后端按类型+单号反查下发） -->
              <span
                :class="['bill-no-text', billJumpPath(row) ? 'is-link' : 'no-link']"
                @click="goToBill(row)"
              >{{ row.billNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="ipAddress" :label="$tf('IP地址')" min-width="130" sortable="custom"/>
          <el-table-column prop="duration" :label="$tf('时长(ms)')" width="110" align="right" sortable="custom"/>
        </el-table>

        <el-pagination
          class="pagination"
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePagePerm } from '@/utils/pagePerm'
const perm = usePagePerm('system:operate-log')
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  List, ShoppingCart, Goods, Box, Tools, Money, User, Setting,
  WarningFilled, InfoFilled, Loading
} from '@element-plus/icons-vue'
import { pageLog, getLogDetail } from '@/api/system/log'
import { resolveBillRoute } from '@/utils/billRoute'
import { tt } from '@/i18n'
import { useTableSort } from '@/composables/useTableSort'
const { handleSortChange, getSortParams } = useTableSort()

const router = useRouter()

/** 日志行 → 目标单据编辑页路由（后端已按「单据类型+单号」反查并下发 billDocType/billId） */
function billJumpPath(row) {
  return resolveBillRoute(row && row.billDocType, row && row.billId)
}

function goToBill(row) {
  const path = billJumpPath(row)
  // 单据已删除/无独立编辑页时给明确提示（2026-09-28：不再"点了没反应"）
  if (!path) { ElMessage.warning(tt('该单据已删除或无法打开')); return }
  router.push(path)
}


const onSortChange = (e) => { handleSortChange(e); loadData() }// 模块分组定义（对应酷管王日志分组）
const moduleGroups = [
  {
    key: 'purchase',
    label: '采购管理',
    icon: Goods,
    children: [
      { key: '采购申请单', label: '采购申请单' },
      { key: '采购合同', label: '采购合同' },
      { key: '采购订单', label: '采购订单' },
      { key: '采购入库单', label: '采购入库单' },
      { key: '采购退货单', label: '采购退货单' },
      { key: '采购发票', label: '采购发票' }
    ]
  },
  {
    key: 'sale',
    label: '销售管理',
    icon: ShoppingCart,
    children: [
      { key: '销售报价单', label: '销售报价单' },
      { key: '销售合同', label: '销售合同' },
      { key: '销售订单', label: '销售订单' },
      { key: '销售出库单', label: '销售出库单' },
      { key: '销售退货单', label: '销售退货单' },
      { key: '销售发票', label: '销售发票' }
    ]
  },
  {
    key: 'inventory',
    label: '库存管理',
    icon: Box,
    children: [
      { key: '其他入库单', label: '其他入库单' },
      { key: '其他出库单', label: '其他出库单' },
      { key: '库存调拨单', label: '库存调拨单' },
      { key: '库存盘点单', label: '库存盘点单' },
      { key: '生产领料单', label: '生产领料单' }
    ]
  },
  {
    key: 'production',
    label: '生产管理',
    icon: Tools,
    children: [
      { key: '生产工单', label: '生产工单' },
      { key: '生产入库单', label: '生产入库单' },
      { key: '生产退料单', label: '生产退料单' },
      { key: '委外加工单', label: '委外加工单' }
    ]
  },
  {
    key: 'finance',
    label: '收付款管理',
    icon: Money,
    children: [
      { key: '收款单', label: '收款单' },
      { key: '付款单', label: '付款单' },
      { key: '应收账款', label: '应收账款' },
      { key: '应付账款', label: '应付账款' }
    ]
  },
  {
    key: 'system',
    label: '系统管理',
    icon: Setting,
    children: [
      { key: '用户管理', label: '用户管理' },
      { key: '角色管理', label: '角色管理' },
      { key: '数据字典', label: '数据字典' },
      { key: '系统配置', label: '系统配置' }
    ]
  }
]

const activeModule = ref('all')
const query = reactive({
  current: 1,
  size: 20,
  keyword: '',
  // 操作人筛选（姓名/账号/用户编码），后端 operateUser 参数
  operateUser: '',
  operationType: '',
  moduleName: '',
  startDate: '',
  endDate: '',
  ...getSortParams()
})
const dateRange = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// ===== 变更明细（懒加载：点开才请求，已加载过的行用缓存）=====
const detailMap = ref({})       // logId -> 明细数组
const loadingMap = ref({})      // logId -> 加载中
const detailErrorMap = ref({})  // logId -> 加载失败

/** 展开/收起回调：仅展开且未加载过时才请求 */
async function onExpandChange(row, expandedRows) {
  if (!row || row.logId == null) return
  const expanded = Array.isArray(expandedRows) ? expandedRows.includes(row) : !!expandedRows
  if (!expanded) return
  if (detailMap.value[row.logId] || loadingMap.value[row.logId]) return
  await loadDetail(row.logId)
}

async function loadDetail(logId) {
  loadingMap.value[logId] = true
  try {
    const res = await getLogDetail(logId)
    detailMap.value[logId] = res || []
  } catch (e) {
    // 明细加载失败只影响该行展示，不影响列表本身
    detailErrorMap.value[logId] = true
  } finally {
    loadingMap.value[logId] = false
  }
}

const currentModuleLabel = computed(() => {
  if (activeModule.value === 'all') return '全部日志'
  for (const mod of moduleGroups) {
    const child = mod.children.find(c => c.key === activeModule.value)
    if (child) return `${mod.label} - ${child.label}`
    if (mod.key === activeModule.value) return mod.label
  }
  return '操作日志'
})

function onSelectModule(key) {
  activeModule.value = key
  // 如果选的是子项（具体单据名），按moduleName过滤；如果选的是父项（模块），按模块下所有单据过滤
  if (key === 'all') {
    query.moduleName = ''
  } else {
    const parentGroup = moduleGroups.find(g => g.key === key)
    if (parentGroup) {
      // 选的是父分组 - 用in查询（后端支持moduleNameList或者前端过滤）
      query.moduleName = parentGroup.children.map(c => c.key).join(',')
    } else {
      // 选的是具体单据
      query.moduleName = key
    }
  }
  query.current = 1
  loadData()
}

function operationTagType(type) {
  return {
    '新增': 'success',
    '编辑': 'primary',
    '删除': 'danger',
    '审核': 'warning',
    '重置草稿': 'warning',
    '登录': 'info',
    '登出': 'info',
    '导出': 'success'
  }[type] || 'info'
}

async function loadData() {
  loading.value = true
  // 换页/查询/重置后清空明细缓存，避免残留（展开状态随新数据一并重置）
  detailMap.value = {}
  loadingMap.value = {}
  detailErrorMap.value = {}
  try {
    if (dateRange.value && dateRange.value.length === 2) {
      query.startDate = dateRange.value[0]
      query.endDate = dateRange.value[1]
    } else {
      query.startDate = ''
      query.endDate = ''
    }
    const res = await pageLog({ ...query, ...getSortParams() })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.current = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.operateUser = ''
  query.operationType = ''
  query.startDate = ''
  query.endDate = ''
  query.moduleName = ''
  query.current = 1
  dateRange.value = []
  activeModule.value = 'all'
  loadData()
}

onMounted(loadData)
useAutoRefresh(loadData)
</script>

<style scoped>
.log-page { padding: 0; }
/* 单据编号：能跳到目标单据时是链接样式，否则普通文本（单据已删/无独立编辑页） */
.bill-no-text.is-link {
  color: #6366f1;
  font-weight: 500;
  cursor: pointer;
}
.bill-no-text.is-link:hover { color: #8b5cf6; text-decoration: underline; }
.bill-no-text.no-link { color: #374151; cursor: default; }
.log-layout {
  display: flex;
  gap: 16px;
}
.module-nav {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  overflow-y: auto;
  max-height: calc(100vh - 120px);
}
.nav-header {
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}
.nav-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.module-menu {
  border-right: none;
}
.log-content {
  flex: 1;
  min-width: 0;
}
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.current-module-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-right: auto;
}
.filter-input {
  width: 200px;
}
.filter-select {
  width: 140px;
}
.filter-date {
  width: 260px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

/* ===== 变更明细展开区（配色与单据页「操作日志」弹窗一致）===== */
.log-detail {
  padding: 10px 14px;
  background: #fff;
}
.log-detail-fail {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 8px;
  padding: 8px 10px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 13px;
  line-height: 1.5;
}
.log-detail-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 4px;
  color: #909399;
  font-size: 13px;
}
.log-detail-table { width: 100%; }
.diff-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.diff-table th,
.diff-table td {
  border: 1px solid #ebeef5;
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
}
.diff-table thead th {
  background: #f5f7fa;
  font-weight: 600;
  color: #303133;
  text-align: center;
}
.diff-table .diff-field {
  color: #606266;
  font-weight: 500;
  background: #fafcff;
}
.diff-table .diff-old {
  color: #f56c6c;
  background: #fef0f0;
  text-decoration: line-through;
}
.diff-table .diff-new {
  color: #67c23a;
  background: #f0f9eb;
  font-weight: 500;
}
/* 修改人：中性色，与"改前/改后"的红绿区分开 */
.diff-table .diff-user {
  color: #606266;
  background: #fff;
}
.diff-table .diff-empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
