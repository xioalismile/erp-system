<template>
  <div class="qc-report-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <div class="back-btn" @click="$router.push('/production')">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ $tt("返回") }}</span>
        </div>
        <div class="title-group">
          <div class="page-title">
            {{ $tt("生产工序质检报表") }}
            <span class="title-badge">QC REPORT</span>
          </div>
          <div class="page-sub">{{ $tt("按工序检验记录统计：检验批次、合格率与不合格处理情况，可按日期 / 质检类型 / 判定结果 / 工单 / 工序 / 检验员筛选") }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">{{ $tt("刷新") }}</el-button>
      </div>
    </div>

    <!-- 汇总条（与「经营看板 → 质量」块同口径） -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-value">{{ fmtInt(summary.batchCount) }}</div>
          <div class="stat-label">{{ $tt("检验批次") }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-value success">{{ fmtInt(summary.qualifiedBatchCount) }}</div>
          <div class="stat-label">{{ $tt("合格批次") }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-value danger">{{ fmtInt(summary.defectBatchCount) }}</div>
          <div class="stat-label">{{ $tt("不合格批次") }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-value">{{ fmtPct(summary.passRate) }}</div>
          <div class="stat-label">{{ $tt("合格率") }}</div>
        </div>
      </div>
    </div>

    <!-- 筛选 + 表格 -->
    <div class="content-card">
      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="~"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="flt-date"
        />
        <el-select v-model="filters.qcType" clearable placeholder="质检类型" class="flt-sel">
          <el-option label="首检" value="首检" />
          <el-option label="抽检" value="抽检" />
          <el-option label="全检" value="全检" />
        </el-select>
        <el-select v-model="filters.result" clearable placeholder="判定结果" class="flt-sel">
          <el-option label="合格" value="合格" />
          <el-option label="不合格" value="不合格" />
        </el-select>
        <el-input v-model="filters.orderNo" clearable placeholder="工单号" class="flt-input" @keyup.enter="onSearch" />
        <el-input v-model="filters.operationName" clearable placeholder="工序" class="flt-input" @keyup.enter="onSearch" />
        <el-input v-model="filters.inspectorName" clearable placeholder="检验员" class="flt-input" @keyup.enter="onSearch" />
        <el-input v-model="filters.keyword" clearable placeholder="检验单号 / 工单号" class="flt-input" @keyup.enter="onSearch" />
        <el-button type="primary" :icon="Search" @click="onSearch">{{ $tt("查询") }}</el-button>
        <el-button :icon="RefreshLeft" @click="onReset">{{ $tt("重置") }}</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe border class="qc-table">
        <el-table-column type="index" :label="$tt('#')" width="56" align="center" />
        <el-table-column prop="recordNo" :label="$tt('检验单号')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="orderNo" :label="$tt('工单号')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operationName" :label="$tt('工序')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="qcType" :label="$tt('质检类型')" width="100" align="center" />
        <el-table-column :label="$tt('检验数量')" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.inspectQty) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('合格数')" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQty) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('不合格数')" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectQty) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('判定结果')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.result" size="small" effect="light" :type="row.result === '不合格' ? 'danger' : 'success'">
              {{ $tf(row.result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tt('不合格处理方式')" min-width="130">
          <template #default="{ row }">{{ row.handleType || '' }}</template>
        </el-table-column>
        <el-table-column :label="$tt('处理转序')" min-width="120">
          <template #default="{ row }">{{ row.handleOperationName || '' }}</template>
        </el-table-column>
        <el-table-column prop="inspectorName" :label="$tt('检验员')" width="110" show-overflow-tooltip />
        <el-table-column :label="$tt('检验时间')" width="150">
          <template #default="{ row }">{{ fmtTime(row.inspectTime) }}</template>
        </el-table-column>
        <el-table-column :label="$tt('备注')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '' }}</template>
        </el-table-column>
        <el-table-column :label="$tt('操作')" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openItems(row)">{{ $tt("查看明细") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 检验记录只读详情（检验项 / 实测值 / 判定 / 检验人 / 复核人 / 手写签名图 / 更正链） -->
      <QcRecordDetailDialog v-model:visible="itemsVisible" :record-id="itemsRecord.recordId" :header="itemsRecord" />

      <div class="pagination-wrap">
        <el-pagination
          :current-page="pagination.current"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 生产工序质检报表：只读展示工序检验记录（首检/抽检/全检）及不合格处理情况。
 * 汇总口径与「经营看板 → 质量」块一致；「经营看板」质量块点击后带本期日期范围穿透进来，作为本页初始筛选。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft, Search, ArrowLeft } from '@element-plus/icons-vue'
import { pageQcRecords, summaryQcRecords } from '@/api/qc/qcRecord'
import QcRecordDetailDialog from '@/components/production/QcRecordDetailDialog.vue'

const route = useRoute()
const loading = ref(false)
const tableData = ref([])
const summary = ref({})
/** 日期范围（YYYY-MM-DD 字符串数组）；空 = 不限 */
const dateRange = ref([])
const filters = reactive({ qcType: '', result: '', orderNo: '', operationName: '', inspectorName: '', keyword: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })

// ===== 检验记录只读详情弹窗（检验项 / 实测值 / 判定 / 检验人 / 复核人 / 手写签名图 / 更正链） =====
const itemsVisible = ref(false)
const itemsRecord = ref({})
function openItems (row) {
  itemsRecord.value = row || {}
  itemsVisible.value = true
}

/** 列表与汇总共用同一套筛选条件，避免两处口径漂移 */
function buildParams() {
  const params = { ...filters }
  if (Array.isArray(dateRange.value) && dateRange.value.length === 2 && dateRange.value[0] && dateRange.value[1]) {
    params.dateFrom = dateRange.value[0]
    params.dateTo = dateRange.value[1]
  }
  return params
}

async function loadList() {
  loading.value = true
  try {
    const res = await pageQcRecords({ current: pagination.current, size: pagination.size, ...buildParams() })
    tableData.value = (res && res.records) || []
    pagination.total = (res && res.total) || 0
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    summary.value = (await summaryQcRecords(buildParams())) || {}
  } catch (e) {
    summary.value = {}
  }
}

function loadAll() {
  loadList()
  loadSummary()
}

function onSearch() {
  pagination.current = 1
  loadAll()
}

function onReset() {
  dateRange.value = []
  Object.assign(filters, { qcType: '', result: '', orderNo: '', operationName: '', inspectorName: '', keyword: '' })
  pagination.current = 1
  loadAll()
}

function onPageChange(p) {
  pagination.current = p
  loadList()
}

function onSizeChange(s) {
  pagination.size = s
  pagination.current = 1
  loadList()
}

// ---------------- 显示格式化（无值一律留白，禁止 - / 0% / NaN） ----------------
function fmtInt(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : ''
}

/** 数量：整数不带小数、小数最多 4 位；空值留白（不显示 0 冒充） */
function fmtQty(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (!Number.isFinite(n)) return ''
  return String(Number.isInteger(n) ? n : Number(n.toFixed(4)))
}

function fmtPct(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n + '%' : ''
}

function fmtTime(v) {
  if (!v) return ''
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

onMounted(() => {
  // 「经营看板 → 质量」块穿透：携带本期日期范围作为初始筛选
  const q = route.query || {}
  const startDate = q.startDate ? String(q.startDate) : ''
  const endDate = q.endDate ? String(q.endDate) : ''
  if (startDate && endDate) dateRange.value = [startDate, endDate]
  loadAll()
})
</script>

<style scoped lang="scss">
.qc-report-page {
  padding: 20px 24px;
  background: linear-gradient(135deg, #f8faff 0%, #f0f4ff 50%, #f8fcff 100%);
  min-height: calc(100vh - 60px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .header-left { display: flex; align-items: center; gap: 16px; }

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
    &:hover { color: #6366f1; border-color: #c7d2fe; }
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
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 18px 20px;
  transition: all .3s;

  &:hover {
    box-shadow: 0 6px 18px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
    &.success { color: #10b981; }
    &.danger { color: #ef4444; }
  }

  .stat-label { font-size: 12px; color: #9ca3af; margin-top: 4px; }
}

.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;

  .flt-date { width: 260px; }
  .flt-sel { width: 130px; }
  .flt-input { width: 160px; }
}

.qc-table {
  width: 100%;

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

/* 【第 19 期】检验明细弹窗 */
.items-head {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #6b7280;
}
.ev-list { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.ev-img {
  width: 34px;
  height: 34px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
  cursor: pointer;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
  border-top: 1px solid #f3f4f6;
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .qc-report-page { padding: 12px; }
  .stat-cards { grid-template-columns: 1fr; }
  .filter-bar { flex-direction: column; align-items: stretch; }
  .filter-bar .flt-date,
  .filter-bar .flt-sel,
  .filter-bar .flt-input { width: 100%; }
}
</style>
