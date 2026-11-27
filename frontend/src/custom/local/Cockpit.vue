<template>
  <div class="ov">
    <header class="ov-head">
      <div class="ov-title-wrap">
        <h2 class="ov-title">运营总览</h2>
        <span class="ov-sub">数据更新 {{ updatedAt || '--:--:--' }}<span v-if="loading"> · 加载中</span></span>
      </div>
      <div class="ov-tools">
        <div class="ov-periods">
          <button v-for="p in PERIODS" :key="p.key" type="button" class="ov-pill" :class="{ on: period === p.key }" @click="onPeriod(p.key)">{{ p.label }}</button>
        </div>
        <label class="ov-auto">
          <el-switch :model-value="autoRefresh" size="small" @change="onAuto" />
          <span>30 秒刷新</span>
        </label>
        <el-button size="small" @click="reload">刷新</el-button>
        <el-button size="small" type="primary" plain @click="toScreen">投屏</el-button>
      </div>
    </header>

    <section class="ov-kpis">
      <div v-for="k in kpis" :key="k.key" class="kpi" :class="k.tone">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value">
          <template v-if="k.value === null || k.value === undefined">暂无数据</template>
          <template v-else-if="k.money"><span class="kpi-cur">¥</span>{{ fmtMoney(k.value) }}</template>
          <template v-else-if="k.percent">{{ fmtPercent(k.value) }}</template>
          <template v-else>{{ fmtInt(k.value) }}</template>
        </div>
        <div class="kpi-sub">{{ k.sub }}</div>
      </div>
    </section>

    <div class="ov-body">
      <aside class="ov-side">
        <div class="side-block">
          <div class="side-title">业务分组</div>
          <button
            v-for="t in tables"
            :key="t.key"
            type="button"
            class="side-item"
            :class="{ on: activeTable === t.key }"
            @click="activeTable = t.key"
          >
            <span>{{ t.title }}</span>
            <em>{{ t.rows.length }}</em>
          </button>
        </div>
        <div class="side-block">
          <div class="side-title">待处理</div>
          <div class="side-todo" v-for="s in todoSummary" :key="s.label">
            <span>{{ s.label }}</span>
            <em :class="s.tone">{{ s.count }}</em>
          </div>
        </div>
        <div class="side-block">
          <div class="side-title">口径</div>
          <p class="side-note">仅已生效单据；应收未收含历史未结清；库存按可见仓库。</p>
        </div>
      </aside>

      <div class="ov-tables">
        <section
          v-for="t in visibleTables"
          :key="t.key"
          class="tbl-card"
        >
          <header class="tbl-head">
            <span class="tbl-title">{{ t.title }}</span>
            <span class="tbl-count">{{ t.rows.length }} 条</span>
            <button type="button" class="tbl-all" @click="goList(t)">查看全部</button>
          </header>
          <div class="tbl-wrap">
            <table class="tbl">
              <thead>
                <tr>
                  <th v-for="c in t.columns" :key="c.key" :style="{ width: c.width }" :class="{ right: c.align === 'right' }">{{ c.label }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(r, i) in t.rows"
                  :key="r[t.idKey] || i"
                  :class="{ on: isSelected(t, r) }"
                  @click="select(t, r)"
                >
                  <td v-for="c in t.columns" :key="c.key" :class="{ right: c.align === 'right', link: c.link }">
                    <span v-if="c.status" class="tag" :class="statusClass(r[c.key])">{{ r[c.key] || '未知' }}</span>
                    <template v-else-if="c.money">{{ r[c.key] === null || r[c.key] === undefined ? '' : '¥' + fmtMoney(r[c.key]) }}</template>
                    <template v-else-if="c.date">{{ fmtDate(r[c.key]) }}</template>
                    <template v-else>{{ r[c.key] === null || r[c.key] === undefined || r[c.key] === '' ? '' : r[c.key] }}</template>
                  </td>
                </tr>
                <tr v-if="!t.rows.length">
                  <td :colspan="t.columns.length" class="tbl-empty">暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <aside class="ov-detail">
        <template v-if="selected">
          <header class="dt-head">
            <span class="tag" :class="statusClass(selected.row.status)">{{ selected.row.status || '未知状态' }}</span>
            <span class="dt-type">{{ selected.table.title }}</span>
          </header>
          <div class="dt-no">{{ selected.row[selected.table.idLabelKey] || selected.row[selected.table.columns[0].key] }}</div>
          <dl class="dt-list">
            <div v-for="c in selected.table.columns" :key="c.key" class="dt-row">
              <dt>{{ c.label }}</dt>
              <dd>
                <span v-if="c.status" class="tag" :class="statusClass(selected.row[c.key])">{{ selected.row[c.key] || '未知' }}</span>
                <template v-else-if="c.money">{{ selected.row[c.key] === null || selected.row[c.key] === undefined ? '' : '¥' + fmtMoney(selected.row[c.key]) }}</template>
                <template v-else-if="c.date">{{ fmtDate(selected.row[c.key]) }}</template>
                <template v-else>{{ selected.row[c.key] === null || selected.row[c.key] === undefined || selected.row[c.key] === '' ? '' : selected.row[c.key] }}</template>
              </dd>
            </div>
          </dl>
          <div class="dt-remark" v-if="selected.row.remark">
            <span class="dt-remark-label">备注</span>
            <span>{{ selected.row.remark }}</span>
          </div>
          <div class="dt-actions">
            <el-button type="primary" size="small" @click="openBill">打开单据</el-button>
            <el-button size="small" @click="goList(selected.table)">去列表</el-button>
          </div>
        </template>
        <div v-else class="dt-empty">
          <div class="dt-empty-title">点左边任意一行</div>
          <div class="dt-empty-desc">这里显示这一条的完整信息，可直接打开单据处理。</div>
        </div>
      </aside>
    </div>

    <footer class="ov-foot">
      统计口径：仅已生效单据，按业务日期统计；应收未收含历史未结清单据；库存按当前账号可见仓库统计。数据更新至 {{ updatedAt || '--:--:--' }}。
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { resolveBillRoute } from '@/utils/billRoute'
import {
  useCockpit, PERIODS, fmtMoney, fmtInt, fmtPercent, fmtDate, statusClass
} from '@custom-shared/useCockpit'

const router = useRouter()
const {
  period, loading, updatedAt, autoRefresh,
  kpis, actions, saleOrders, purchaseOrders, workOrders, inventoryRows,
  selected, load, startAuto, setAuto
} = useCockpit()

const activeTable = ref('')

const tables = computed(() => [
  {
    key: 'saleOrders',
    title: '销售订单',
    docType: 'SALE_ORDER',
    idKey: 'orderId',
    idLabelKey: 'orderNo',
    listPath: '/sale/orders',
    rows: saleOrders.value,
    columns: [
      { key: 'orderNo', label: '单号', width: '148px', link: true },
      { key: 'customerName', label: '客户' },
      { key: 'salerName', label: '销售员', width: '86px' },
      { key: 'orderDate', label: '订单日期', width: '98px', date: true },
      { key: 'netAmount', label: '价税合计', width: '112px', money: true, align: 'right' },
      { key: 'status', label: '状态', width: '84px', status: true }
    ]
  },
  {
    key: 'purchaseOrders',
    title: '采购订单',
    docType: 'PURCHASE_ORDER',
    idKey: 'orderId',
    idLabelKey: 'orderNo',
    listPath: '/purchase/orders',
    rows: purchaseOrders.value,
    columns: [
      { key: 'orderNo', label: '单号', width: '148px', link: true },
      { key: 'supplierName', label: '供应商' },
      { key: 'buyerName', label: '采购员', width: '86px' },
      { key: 'orderDate', label: '订单日期', width: '98px', date: true },
      { key: 'netAmount', label: '价税合计', width: '112px', money: true, align: 'right' },
      { key: 'status', label: '状态', width: '84px', status: true }
    ]
  },
  {
    key: 'workOrders',
    title: '生产工单',
    docType: 'WORK_ORDER',
    idKey: 'orderId',
    idLabelKey: 'orderNo',
    listPath: '/production/workorders',
    rows: workOrders.value,
    columns: [
      { key: 'orderNo', label: '单号', width: '148px', link: true },
      { key: 'materialName', label: '产品' },
      { key: 'quantity', label: '计划数量', width: '92px', align: 'right' },
      { key: 'completedQuantity', label: '已完工', width: '86px', align: 'right' },
      { key: 'planEndDate', label: '计划完工', width: '98px', date: true },
      { key: 'status', label: '状态', width: '84px', status: true }
    ]
  },
  {
    key: 'inventoryRows',
    title: '即时库存',
    docType: '',
    idKey: 'inventoryId',
    idLabelKey: 'materialName',
    listPath: '/inventory/current',
    rows: inventoryRows.value,
    columns: [
      { key: 'materialCode', label: '物料编码', width: '118px' },
      { key: 'materialName', label: '物料名称' },
      { key: 'stockName', label: '仓库', width: '100px' },
      { key: 'quantity', label: '数量', width: '82px', align: 'right' },
      { key: 'availableQuantity', label: '可用', width: '82px', align: 'right' },
      { key: 'totalCost', label: '金额', width: '108px', money: true, align: 'right' }
    ]
  }
])

const visibleTables = computed(() => {
  if (!activeTable.value) return tables.value
  return tables.value.filter(t => t.key === activeTable.value)
})

const todoSummary = computed(() => {
  const map = { collect: { label: '逾期客户', count: 0, tone: 'bad' }, restock: { label: '缺货物料', count: 0, tone: 'warn' }, clear: { label: '呆滞物料', count: 0, tone: 'normal' }, chase: { label: '延期工单', count: 0, tone: 'bad' }, qc: { label: '不合格批次', count: 0, tone: 'bad' } }
  actions.value.forEach(a => { if (map[a.type]) map[a.type].count += 1 })
  return Object.values(map)
})

onMounted(async () => {
  await load()
  startAuto()
})

function onPeriod(key) {
  if (period.value === key) return
  period.value = key
  load()
}

function onAuto(on) {
  setAuto(on)
}

async function reload() {
  await load()
}

function toScreen() {
  router.push({ path: '/board/cockpit/screen' })
}

function select(table, row) {
  selected.value = { table, row }
}

function isSelected(table, row) {
  if (!selected.value) return false
  if (selected.value.table.key !== table.key) return false
  return selected.value.row[table.idKey] === row[table.idKey]
}

function goList(table) {
  if (table.listPath) router.push({ path: table.listPath })
}

function openBill() {
  if (!selected.value) return
  const { table, row } = selected.value
  const target = resolveBillRoute(table.docType, row[table.idKey])
  if (target) router.push({ path: target })
  else goList(table)
}
</script>

<style scoped>
.ov {
  padding: 14px 16px 18px;
  background: #f5f7fa;
  min-height: calc(100vh - 84px);
  box-sizing: border-box;
}
.ov-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.ov-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 0.5px;
}
.ov-sub {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.ov-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.ov-periods {
  display: inline-flex;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 15px;
  padding: 2px;
}
.ov-pill {
  border: 0;
  background: transparent;
  color: #606266;
  font-size: 12px;
  padding: 3px 11px;
  border-radius: 13px;
  cursor: pointer;
}
.ov-pill:hover { color: #409eff; }
.ov-pill.on { background: #409eff; color: #fff; }
.ov-auto {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}
.ov-kpis {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(178px, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}
.kpi {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 9px 12px 10px;
  border-top: 2px solid #409eff;
}
.kpi.good { border-top-color: #67c23a; }
.kpi.warn { border-top-color: #e6a23c; }
.kpi.bad { border-top-color: #f56c6c; }
.kpi-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 3px;
}
.kpi-value {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kpi.bad .kpi-value { color: #f56c6c; }
.kpi.warn .kpi-value { color: #e6a23c; }
.kpi.good .kpi-value { color: #529b2e; }
.kpi-cur {
  font-size: 12px;
  font-weight: 500;
  color: #909399;
  margin-right: 1px;
}
.kpi-sub {
  margin-top: 3px;
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ov-body {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr) 274px;
  gap: 10px;
  align-items: start;
}
.ov-side,
.ov-detail {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px 12px;
}
.side-block + .side-block {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f2f4f7;
}
.side-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
.side-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  text-align: left;
}
.side-item:hover { background: #f5f7fa; }
.side-item.on { background: #ecf5ff; color: #409eff; font-weight: 600; }
.side-item em {
  font-style: normal;
  font-size: 11px;
  color: #909399;
}
.side-todo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  padding: 4px 8px;
}
.side-todo em {
  font-style: normal;
  font-weight: 600;
  color: #909399;
}
.side-todo em.bad { color: #f56c6c; }
.side-todo em.warn { color: #e6a23c; }
.side-note {
  margin: 0;
  font-size: 11px;
  color: #909399;
  line-height: 1.65;
}
.ov-tables {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.tbl-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.tbl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #f2f4f7;
  background: #fcfcfd;
}
.tbl-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.tbl-count {
  font-size: 11px;
  color: #909399;
}
.tbl-all {
  margin-left: auto;
  border: 0;
  background: transparent;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
}
.tbl-all:hover { text-decoration: underline; }
.tbl-wrap {
  overflow-x: auto;
}
.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  table-layout: fixed;
}
.tbl th {
  text-align: left;
  font-weight: 500;
  color: #909399;
  background: #fafafa;
  padding: 6px 10px;
  border-bottom: 1px solid #f0f2f5;
  white-space: nowrap;
}
.tbl td {
  padding: 6px 10px;
  border-bottom: 1px solid #f7f8fa;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tbl tbody tr {
  cursor: pointer;
}
.tbl tbody tr:hover td {
  background: #f5faff;
}
.tbl tbody tr.on td {
  background: #ecf5ff;
}
.tbl .right { text-align: right; }
.tbl td.link { color: #409eff; }
.tbl-empty {
  text-align: center;
  color: #909399;
  padding: 18px 10px;
}
.tag {
  display: inline-block;
  font-size: 11px;
  line-height: 18px;
  padding: 0 7px;
  border-radius: 9px;
  background: #f4f4f5;
  color: #909399;
}
.tag.draft { background: #f4f4f5; color: #909399; }
.tag.pending { background: #fdf6ec; color: #b88230; }
.tag.approved { background: #ecf5ff; color: #2b7cd3; }
.tag.done { background: #f0f9eb; color: #529b2e; }
.tag.partial { background: #e6fffb; color: #08979c; }
.tag.void { background: #fef0f0; color: #c45656; }
.tag.closed { background: #f4f4f5; color: #909399; }
.dt-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.dt-type {
  font-size: 12px;
  color: #909399;
}
.dt-no {
  font-size: 14px;
  font-weight: 700;
  color: #303133;
  padding-bottom: 8px;
  border-bottom: 1px solid #f2f4f7;
  margin-bottom: 6px;
  word-break: break-all;
}
.dt-list {
  margin: 0;
}
.dt-row {
  display: flex;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed #f5f7fa;
  font-size: 12px;
}
.dt-row dt {
  flex: 0 0 76px;
  color: #909399;
}
.dt-row dd {
  margin: 0;
  flex: 1 1 auto;
  color: #303133;
  text-align: right;
  word-break: break-all;
}
.dt-remark {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
}
.dt-remark-label {
  color: #909399;
  margin-right: 6px;
}
.dt-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.dt-actions .el-button {
  flex: 1 1 0;
}
.dt-empty {
  padding: 40px 12px;
  text-align: center;
}
.dt-empty-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.dt-empty-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
}
.ov-foot {
  margin-top: 10px;
  font-size: 11px;
  color: #909399;
  line-height: 1.7;
}
@media (max-width: 1280px) {
  .ov-body { grid-template-columns: 150px minmax(0, 1fr) 250px; }
}
@media (max-width: 1100px) {
  .ov-body { grid-template-columns: 1fr; }
  .ov-side { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }
  .side-block + .side-block { margin-top: 0; padding-top: 0; border-top: 0; }
}
</style>
