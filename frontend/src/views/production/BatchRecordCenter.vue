<template>
  <div class="erp-page brc-page">
    <el-card shadow="never" class="erp-card">
      <div class="page-header">
        <div class="page-title">{{ tt('批记录中心') }}</div>
        <div class="header-actions">
          <el-button type="primary" @click="loadList">{{ tt('查询') }}</el-button>
          <el-button @click="resetQuery">{{ tt('重置') }}</el-button>
          <el-button @click="goQcTemplate">{{ tt('检验项设置') }}</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ================= 批次总览 ================= -->
        <el-tab-pane :label="tt('批次总览')" name="list">
          <div class="stat-row">
            <div
              v-for="card in statCards"
              :key="card.key"
              class="stat-card"
              :class="{ active: query.qcStatus === card.key }"
              @click="quickFilter(card.key)"
            >
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>

          <div class="filter-row">
            <el-input v-model="query.keyword" :placeholder="tt('批号 / 物料编码 / 物料名称')" clearable style="width: 240px" @keyup.enter="loadList" />
            <el-select v-model="query.qcStatus" :placeholder="tt('批次状态')" clearable style="width: 140px">
              <el-option v-for="s in QC_STATUS_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              :start-placeholder="tt('开始日期')"
              :end-placeholder="tt('结束日期')"
              style="width: 260px"
            />
            <el-button type="primary" @click="loadList">{{ tt('查询') }}</el-button>
            <el-button @click="resetQuery">{{ tt('重置') }}</el-button>
          </div>

          <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
            <el-table-column prop="materialCode" :label="tt('物料编码')" width="130" show-overflow-tooltip />
            <el-table-column prop="materialName" :label="tt('物料名称')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="batchNo" :label="tt('批号')" width="160" show-overflow-tooltip />
            <el-table-column prop="specification" :label="tt('规格型号')" width="120" show-overflow-tooltip />
            <el-table-column prop="quantity" :label="tt('数量')" width="100" align="right" />
            <el-table-column prop="supplierBatchNo" :label="tt('供应商批号')" width="140" show-overflow-tooltip />
            <el-table-column prop="productionDate" :label="tt('生产日期')" width="120" />
            <el-table-column prop="expiryDate" :label="tt('失效日期')" width="120" />
            <el-table-column :label="tt('批次状态')" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.qcStatus)" effect="light">{{ row.qcStatus || tt('未设置') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="tt('检验结论')" width="110">
              <template #default="{ row }">{{ row.qcResult || '' }}</template>
            </el-table-column>
            <el-table-column :label="tt('放行状态')" width="110">
              <template #default="{ row }">
                <el-tag :type="row.releaseCount > 0 ? 'success' : 'info'" effect="plain">
                  {{ row.releaseCount > 0 ? tt('已放行') : tt('未放行') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="tt('操作')" width="290" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTrace(row)">{{ tt('追溯') }}</el-button>
                <el-button link type="primary" @click="openInspect(row)">{{ tt('检验') }}</el-button>
                <el-button link type="primary" @click="openQcDetail(row)">{{ tt('查看检验') }}</el-button>
                <el-button link type="primary" @click="openRelease(row)">{{ tt('放行') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!loading && !tableData.length" class="empty-guide">
            {{ tt('还没有批次数据，请在采购入库或生产入库时填写批号') }}
          </div>

          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next"
            class="pager"
            @current-change="loadList"
            @size-change="loadList"
          />
        </el-tab-pane>

        <!-- ================= 检验项设置 ================= -->
        <el-tab-pane :label="tt('检验项设置')" name="qc">
          <div class="empty-guide">
            {{ tt('检验项统一在「基础资料 → 质检模板」维护，批记录检验按物料与分类自动匹配模板') }}
          </div>
          <div class="center-btn">
            <el-button type="primary" @click="goQcTemplate">{{ tt('打开质检模板') }}</el-button>
          </div>
        </el-tab-pane>

        <!-- ================= 留样台账 ================= -->
        <el-tab-pane :label="tt('留样台账')" name="sample">
          <div class="filter-row">
            <el-input v-model="sampleQuery.keyword" :placeholder="tt('批号 / 物料名称')" clearable style="width: 240px" @keyup.enter="loadSamples" />
            <el-select v-model="sampleQuery.remindStatus" :placeholder="tt('提醒状态')" clearable style="width: 140px">
              <el-option :label="tt('未到期')" value="未到期" />
              <el-option :label="tt('已提醒')" value="已提醒" />
            </el-select>
            <el-button type="primary" @click="loadSamples">{{ tt('查询') }}</el-button>
          </div>

          <el-table v-loading="sampleLoading" :data="sampleData" border stripe style="width: 100%">
            <el-table-column prop="retention_no" :label="tt('留样单号')" width="170" show-overflow-tooltip />
            <el-table-column prop="batch_no" :label="tt('批号')" width="160" show-overflow-tooltip />
            <el-table-column prop="material_name" :label="tt('物料名称')" min-width="150" show-overflow-tooltip />
            <el-table-column prop="retain_qty" :label="tt('留样量')" width="100" align="right" />
            <el-table-column prop="store_location" :label="tt('存放位置')" width="140" show-overflow-tooltip />
            <el-table-column prop="retain_date" :label="tt('留样日期')" width="120" />
            <el-table-column prop="keep_until" :label="tt('保存至')" width="120" />
            <el-table-column :label="tt('提醒状态')" width="110">
              <template #default="{ row }">
                <el-tag :type="row.remind_status === '已提醒' ? 'info' : 'warning'" effect="light">
                  {{ row.remind_status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!sampleLoading && !sampleData.length" class="empty-guide">
            {{ tt('还没有留样记录，成品放行生效后会自动生成留样任务') }}
          </div>

          <el-pagination
            v-model:current-page="samplePage.current"
            v-model:page-size="samplePage.size"
            :page-sizes="[10, 20, 50]"
            :total="samplePage.total"
            layout="total, sizes, prev, pager, next"
            class="pager"
            @current-change="loadSamples"
            @size-change="loadSamples"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 检验录入弹窗 -->
    <QcInspectDialog v-model:visible="inspectVisible" :batch-row="inspectRow" @done="loadList" />

    <!-- 检验记录只读详情（检验项 / 实测值 / 判定 / 检验人 / 复核人 / 手写签名图 / 更正链） -->
    <QcRecordDetailDialog v-model:visible="qcDetailVisible" :batch-id="qcDetailBatchId" />
  </div>
</template>

<script setup>
/**
 * 批记录中心（化妆品批记录）
 * 三页签：批次总览 / 检验项设置 / 留样台账；批次行提供 追溯 / 检验 / 放行 三个动作。
 * 检验项设置直接指向现有「基础资料 → 质检模板」，不重建一套检验项维护。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tt } from '@/i18n'
import {
  listBatchRecords,
  listSampleRetentions,
  getBatchStatusSummary,
  createReleaseFromBatch,
  submitReleaseOrder
} from '@/api/production/batchRecord'
import QcInspectDialog from '@/components/production/QcInspectDialog.vue'
import QcRecordDetailDialog from '@/components/production/QcRecordDetailDialog.vue'

const router = useRouter()
const QC_STATUS_OPTIONS = ['待检', '合格', '不合格', '已放行', '已冻结']

const activeTab = ref('list')
const loading = ref(false)
const tableData = ref([])
const dateRange = ref([])
const pagination = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({ keyword: '', qcStatus: '' })
const summary = ref({})

const sampleLoading = ref(false)
const sampleData = ref([])
const samplePage = reactive({ current: 1, size: 20, total: 0 })
const sampleQuery = reactive({ keyword: '', remindStatus: '' })

const inspectVisible = ref(false)
const inspectRow = ref(null)

/** 检验记录只读详情（按批次取最近一条检验） */
const qcDetailVisible = ref(false)
const qcDetailBatchId = ref(null)
function openQcDetail(row) {
  qcDetailBatchId.value = row?.batchId || null
  qcDetailVisible.value = true
}

const statCards = computed(() => {
  const keys = ['全部', '待检', '合格', '已放行', '不合格', '已冻结']
  return keys.map(k => ({ key: k === '全部' ? '' : k, label: k, value: summary.value[k] ?? 0 }))
})

function statusTagType(status) {
  if (status === '已放行') return 'success'
  if (status === '待检') return 'warning'
  if (status === '不合格') return 'danger'
  if (status === '已冻结') return 'info'
  return 'primary'
}

async function loadList() {
  loading.value = true
  try {
    const res = await listBatchRecords({
      current: pagination.current,
      size: pagination.size,
      keyword: query.keyword || undefined,
      qcStatus: query.qcStatus || undefined,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1]
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    ElMessage.error(e?.message || tt('查询失败'))
  } finally {
    loading.value = false
  }
  loadSummary()
}

async function loadSummary() {
  try {
    summary.value = (await getBatchStatusSummary()) || {}
  } catch (_) {
    summary.value = {}
  }
}

function quickFilter(status) {
  query.qcStatus = status
  pagination.current = 1
  loadList()
}

function resetQuery() {
  query.keyword = ''
  query.qcStatus = ''
  dateRange.value = []
  pagination.current = 1
  loadList()
}

async function loadSamples() {
  sampleLoading.value = true
  try {
    const res = await listSampleRetentions({
      current: samplePage.current,
      size: samplePage.size,
      keyword: sampleQuery.keyword || undefined,
      remindStatus: sampleQuery.remindStatus || undefined
    })
    sampleData.value = res?.records || []
    samplePage.total = res?.total || 0
  } catch (e) {
    ElMessage.error(e?.message || tt('查询失败'))
  } finally {
    sampleLoading.value = false
  }
}

function onTabChange(name) {
  if (name === 'sample') loadSamples()
}

function goQcTemplate() {
  router.push('/masterdata/qc-template')
}

function openTrace(row) {
  router.push({ name: 'BatchTraceView', params: { batchId: row.batchId } })
}

function openInspect(row) {
  inspectRow.value = row
  inspectVisible.value = true
}

async function openRelease(row) {
  try {
    const draft = await createReleaseFromBatch(row.batchId)
    await ElMessageBox.confirm(
      tt('已生成放行单 {no}，是否立即提交生效？').replace('{no}', draft?.releaseNo || ''),
      tt('批次放行'),
      { confirmButtonText: tt('提交生效'), cancelButtonText: tt('稍后处理'), type: 'warning' }
    )
    await submitReleaseOrder(draft.releaseId)
    ElMessage.success(tt('放行单已提交'))
    loadList()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || tt('放行操作失败'))
  }
}

onMounted(loadList)
</script>

<style scoped>
.brc-page .page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.brc-page .page-title {
  font-size: 16px;
  font-weight: 600;
}
.brc-page .header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.stat-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.stat-card {
  min-width: 110px;
  padding: 10px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.stat-card:hover,
.stat-card.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.empty-guide {
  padding: 24px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.center-btn {
  text-align: center;
  margin-top: 8px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
