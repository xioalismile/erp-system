<template>
  <div class="report-container">
    <!-- 顶部页面标题 -->
    <div class="page-header">
      <h2 class="page-title">{{ $tt("财务报表中心") }}</h2>
    </div>

    <!-- 全局筛选栏：日期范围 + 关键字搜索 -->
    <div class="filter-bar" style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
      <el-date-picker v-model="dateRange" type="daterange" :range-separator="$tt('至')"
        :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
        value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
      <el-input v-model="keyword" :placeholder="$tt('搜索关键字（单号/客户/供应商）')"
        clearable style="width: 240px" @keyup.enter="loadAll" />
      <el-button type="primary" :loading="loadingAll" @click="loadAll">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="resetFilter">{{ $tt("重置") }}</el-button>
    </div>

    <!-- 报表1：应收应付汇总 -->
    <el-card shadow="never" class="report-card" v-loading="loadingArAp">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("应收应付汇总") }}</span>
          <div class="card-header-right">
            <el-tag size="small" type="info">
              应收 {{ arApData.arCount || 0 }} 笔 / 应付 {{ arApData.apCount || 0 }} 笔
            </el-tag>
            <el-button size="small" type="primary" plain @click="openDetail('ar-ap-summary')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in arApKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 应收应付对比柱状图 -->
      <div ref="arApBarChart" class="chart-box"></div>
    </el-card>

    <!-- 报表2：账龄分析 -->
    <el-card shadow="never" class="report-card" v-loading="loadingAging">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("账龄分析") }}</span>
          <div class="card-header-right">
            <el-tag size="small" :type="agingData.overdueCount ? 'danger' : 'success'">
              逾期 {{ agingData.overdueCount || 0 }} 笔
            </el-tag>
            <el-button size="small" type="primary" plain @click="openDetail('aging')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in agingKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 账龄分桶柱状图 -->
      <div ref="agingBarChart" class="chart-box"></div>
      <!-- 账龄明细表格 -->
      <div class="table-title">{{ $tt("账龄明细") }}</div>
      <el-table :data="agingData.items || []" size="small" border stripe max-height="360">
        <el-table-column prop="arNo" :label="$tf('应收单号')" min-width="140" />
        <el-table-column prop="customerName" :label="$tf('客户')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="arDate" :label="$tf('应收日期')" width="120" />
        <el-table-column prop="dueDate" :label="$tf('到期日')" width="120" />
        <el-table-column :label="$tf('应收金额')" width="130" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('余额')" width="130" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.balanceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="ageDays" :label="$tf('账龄(天)')" width="100" align="right" />
        <el-table-column prop="bucket" :label="$tf('账龄分桶')" width="120" align="center" />
        <el-table-column :label="$tf('状态')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isOverdue ? 'danger' : 'success'" size="small">
              {{ row.isOverdue ? '逾期' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="$tt('暂无账龄数据')" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <!-- 报表3：利润分析 -->
    <el-card shadow="never" class="report-card" v-loading="loadingProfit">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("利润分析") }}</span>
          <div class="card-header-right">
            <el-tag size="small" type="info">
              销售单 {{ profitData.saleOrderCount || 0 }} / 采购单 {{ profitData.purchaseOrderCount || 0 }}
            </el-tag>
            <el-button size="small" type="primary" plain @click="openDetail('profit')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in profitKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 收入vs成本折线图 + 费用类型饼图 -->
      <el-row :gutter="16">
        <el-col :xs="24" :md="14">
          <div ref="profitLineChart" class="chart-box"></div>
        </el-col>
        <el-col :xs="24" :md="10">
          <div ref="expensePieChart" class="chart-box"></div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 报表4：费用分摊统计 -->
    <el-card shadow="never" class="report-card" v-loading="loadingExpense">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("费用分摊统计") }}</span>
          <div class="card-header-right">
            <el-tag size="small" type="info">共 {{ expenseData.totalCount || 0 }} 笔</el-tag>
            <el-button size="small" type="primary" plain @click="openDetail('expense-allocation')">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <!-- KPI 卡片行 -->
      <div class="kpi-row">
        <div
          v-for="kpi in expenseKpis"
          :key="kpi.label"
          class="kpi-box"
          :style="{ background: kpi.bg }"
        >
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <!-- 三个表格：按部门 / 按费用类型 / 按订单 -->
      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <div class="table-title">{{ $tt("按部门") }}</div>
          <el-table :data="expenseData.byDepartment || []" size="small" border stripe max-height="360">
            <el-table-column prop="departmentName" :label="$tf('部门')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="count" :label="$tf('笔数')" width="80" align="right" />
            <el-table-column :label="$tf('金额')" min-width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="90" align="right">
              <template #default="{ row }">{{ row.percentage }}%</template>
            </el-table-column>
            <template #empty>
              <el-empty :description="$tt('暂无数据')" :image-size="60" />
            </template>
          </el-table>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="table-title">{{ $tt("按费用类型") }}</div>
          <el-table :data="expenseData.byType || []" size="small" border stripe max-height="360">
            <el-table-column prop="expenseType" :label="$tf('费用类型')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="count" :label="$tf('笔数')" width="80" align="right" />
            <el-table-column :label="$tf('金额')" min-width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="90" align="right">
              <template #default="{ row }">{{ row.percentage }}%</template>
            </el-table-column>
            <template #empty>
              <el-empty :description="$tt('暂无数据')" :image-size="60" />
            </template>
          </el-table>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="table-title">{{ $tt("按订单") }}</div>
          <el-table :data="expenseData.byOrder || []" size="small" border stripe max-height="360">
            <el-table-column prop="orderNo" :label="$tf('订单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="orderType" :label="$tf('订单类型')" width="100" />
            <el-table-column prop="count" :label="$tf('笔数')" width="80" align="right" />
            <el-table-column :label="$tf('金额')" min-width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty :description="$tt('暂无数据')" :image-size="60" />
            </template>
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 报表5：月度开票与未开票 -->
    <el-card shadow="never" class="report-card" v-loading="loadingInvoiceMonth">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("月度开票与未开票") }}</span>
          <div class="card-header-right">
            <el-select v-model="invoiceYear" size="small" style="width: 110px" @change="loadInvoiceMonth">
              <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
            </el-select>
            <el-tag size="small" type="info">{{ invoiceData.invoiceCount || 0 }} 张发票</el-tag>
            <el-button size="small" type="primary" plain @click="openMonthly('invoice-month', defaultInvoiceMonth())">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <div class="kpi-row">
        <div v-for="kpi in invoiceMonthKpis" :key="kpi.label" class="kpi-box"
          :class="{ 'kpi-clickable': !!kpi.act }" :style="{ background: kpi.bg, cursor: kpi.act ? 'pointer' : 'default' }"
          :title="kpi.act ? '点击查看明细' : ''" @click="kpiClick(kpi)">
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <div ref="invoiceBarChart" class="chart-box"></div>
      <div class="table-title">{{ $tt("按月份（点击月份行查看该月开票/未开票明细）") }}</div>
      <el-table :data="invoiceData.byMonth || []" size="small" border stripe max-height="360"
        :row-class-name="clickableRow" @cell-click="onInvoiceMonthCellClick">
        <el-table-column prop="month" :label="$tf('月份')" width="110" />
        <el-table-column :label="$tf('开票金额')" min-width="130" align="right" prop="invoiceAmount">
          <template #default="{ row }"><span class="cell-link">{{ cellMoney(row.invoiceAmount) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('开票税额')" min-width="120" align="right" prop="invoiceTaxAmount">
          <template #default="{ row }"><span class="cell-link">{{ cellMoney(row.invoiceTaxAmount) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('发票张数')" width="90" align="right" prop="invoiceCount">
          <template #default="{ row }"><span class="cell-link">{{ row.invoiceCount || '' }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('未开票金额')" min-width="130" align="right" prop="uninvoicedAmount">
          <template #default="{ row }"><span class="cell-link">{{ cellMoney(row.uninvoicedAmount) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('本月回款')" min-width="130" align="right" prop="receiptAmount">
          <template #default="{ row }"><span class="cell-link">{{ cellMoney(row.receiptAmount) }}</span></template>
        </el-table-column>
        <template #empty><el-empty :description="$tt('暂无数据')" :image-size="60" /></template>
      </el-table>
    </el-card>

    <!-- 报表6：同事回款与未收 -->
    <el-card shadow="never" class="report-card" v-loading="loadingSalerMonth">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("同事回款与未收") }}</span>
          <div class="card-header-right">
            <el-date-picker v-model="salerMonth" type="month" value-format="YYYY-MM" size="small"
              :placeholder="$tt('选择月份')" style="width: 130px" @change="loadSalerMonth" />
            <el-tag size="small" type="info">未收余额 {{ moneyCompact(salerData.totalOpen) }}</el-tag>
            <el-button size="small" type="primary" plain @click="openMonthly('saler-month', salerMonth || currentYm())">
              <el-icon><View /></el-icon> 查看明细
            </el-button>
          </div>
        </div>
      </template>
      <div class="kpi-row">
        <div v-for="kpi in salerMonthKpis" :key="kpi.label" class="kpi-box"
          :class="{ 'kpi-clickable': !!kpi.act }" :style="{ background: kpi.bg, cursor: kpi.act ? 'pointer' : 'default' }"
          :title="kpi.act ? '点击查看明细' : ''" @click="kpiClick(kpi)">
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <div class="table-title">{{ $tt("按同事（点击行查看该同事应收/回款/未收明细）") }}</div>
      <el-table :data="salerData.rows || []" size="small" border stripe max-height="420"
        :row-class-name="clickableRow" @row-click="onSalerRowClick">
        <el-table-column prop="salerName" :label="$tf('业务员')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$tf('本月新开应收')" min-width="130" align="right">
          <template #default="{ row }"><span class="cell-link">{{ moneyCell(row.newArAmount) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('本月回款')" min-width="130" align="right">
          <template #default="{ row }"><span class="cell-link">{{ moneyCell(row.receivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('累计未收余额')" min-width="140" align="right">
          <template #default="{ row }"><span class="cell-link danger-cell">{{ moneyCell(row.openBalance) }}</span></template>
        </el-table-column>
        <el-table-column :label="$tf('未收笔数')" width="90" align="right">
          <template #default="{ row }">{{ row.openCount || '' }}</template>
        </el-table-column>
        <template #empty><el-empty :description="$tt('暂无数据')" :image-size="60" /></template>
      </el-table>
    </el-card>

    <!-- 报表7：月度收款渠道统计 -->
    <el-card shadow="never" class="report-card" v-loading="loadingChannelMonth">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $tt("月度收款渠道统计") }}</span>
          <div class="card-header-right">
            <el-select v-model="channelYear" size="small" style="width: 110px" @change="loadChannelMonth">
              <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
            </el-select>
            <el-tag size="small" type="info">年收入 {{ moneyCompact(channelData.totalAmount) }}</el-tag>
            <el-button size="small" type="primary" plain @click="openMonthly('channel-month', firstChannelMonth())">
              <el-icon><View /></el-icon> 查看流水
            </el-button>
          </div>
        </div>
      </template>
      <div class="kpi-row">
        <div v-for="kpi in channelMonthKpis" :key="kpi.label" class="kpi-box"
          :class="{ 'kpi-clickable': !!kpi.act }" :style="{ background: kpi.bg, cursor: kpi.act ? 'pointer' : 'default' }"
          :title="kpi.act ? '点击查看明细' : ''" @click="kpiClick(kpi)">
          <div class="kpi-label">{{ $tf(kpi.label) }}</div>
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
        </div>
      </div>
      <div class="table-title">{{ $tt("按月份 × 渠道（微信/支付宝/现金/各公司账户，点击数值查看当月流水）") }}</div>
      <el-table :data="channelData.rows || []" size="small" border stripe max-height="460"
        :row-class-name="clickableRow" @cell-click="onChannelCellClick" show-summary
        :summary-method="channelSummary">
        <el-table-column prop="month" :label="$tf('月份')" width="110" fixed="left" />
        <template v-for="(col, idx) in channelColumns" :key="col.key">
          <el-table-column :label="$tf(col.label)" min-width="128" align="right">
            <template #default="{ row }">
              <span class="cell-link">{{ cellMoney(row.cells && row.cells[idx]) }}</span>
            </template>
          </el-table-column>
        </template>
        <template #empty><el-empty :description="$tt('暂无数据')" :image-size="60" /></template>
      </el-table>
      <div class="table-title" style="margin-top:4px;">{{ $tt("合计行（show-summary 展示）：如需看某月某渠道明细，点击对应数值") }}</div>
    </el-card>

    <!-- 共享明细抽屉：按 currentReportKey 渲染不同列 -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="$tt(currentReportTitle) + ' - ' + $tt('明细数据')"
      direction="rtl"
      size="70%"
      :close-on-click-modal="false"
    >
      <div class="detail-drawer">
        <div class="detail-filter">
          <el-date-picker v-model="detailDateRange" type="daterange" :range-separator="$tt('至')"
            :start-placeholder="$tt('开始日期')" :end-placeholder="$tt('结束日期')"
            value-format="YYYY-MM-DD" unlink-panels clearable style="width: 280px" />
          <el-input v-model="detailKeyword" :placeholder="$tt('搜索关键字')" clearable
            style="width: 220px" @keyup.enter="loadDetail" />
          <el-button type="primary" :loading="detailLoading" @click="loadDetail">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetDetail">{{ $tt("重置") }}</el-button>
          <el-button type="success" plain :loading="detailLoading" @click="handleExportDetail">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <el-table :data="detailData" v-loading="detailLoading" border stripe size="small"
          max-height="560" style="margin-top: 12px;">
          <template v-if="currentReportKey === 'ar-ap-summary'">
            <el-table-column prop="billNo" :label="$tf('单号')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="billType" :label="$tf('类型')" width="100" align="center" />
            <el-table-column prop="customerName" :label="$tf('客户/供应商')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="$tf('金额')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('已收/已付')" align="right" width="130">
              <template #default="{ row }">¥{{ fmt(row.paidAmount) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="$tf('状态')" width="100" align="center">
              <template #default="{ row }">{{ $dt(row.status) }}</template>
            </el-table-column>
            <el-table-column prop="billDate" :label="$tf('日期')" width="110" />
          </template>
          <template v-else-if="currentReportKey === 'aging'">
            <el-table-column prop="customerName" :label="$tf('客户名称')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="agingBucket" :label="$tf('账期')" width="120" align="center" />
            <el-table-column :label="$tf('应收金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.receivableAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('未收金额')" align="right" width="140">
              <template #default="{ row }">
                <span :class="{ 'overdue-text': row.balanceAmount > 0 }">¥{{ fmt(row.balanceAmount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="overdueDays" :label="$tf('逾期天数')" width="110" align="right" />
          </template>
          <template v-else-if="currentReportKey === 'profit'">
            <el-table-column prop="month" :label="$tf('月份')" width="100" />
            <el-table-column :label="$tf('收入')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.income) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('成本')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.cost) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('费用')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.expense) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('利润')" align="right" width="140">
              <template #default="{ row }">
                <span :style="{ color: row.profit >= 0 ? '#67c23a' : '#f56c6c' }">¥{{ fmt(row.profit) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$tf('利润率')" align="center" width="110">
              <template #default="{ row }">{{ pct(row.profitRate) }}</template>
            </el-table-column>
          </template>
          <template v-else-if="currentReportKey === 'expense-allocation'">
            <el-table-column prop="deptName" :label="$tf('部门')" min-width="140" />
            <el-table-column prop="expenseType" :label="$tf('费用类型')" min-width="140" />
            <el-table-column :label="$tf('金额')" align="right" width="140">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$tf('占比')" width="110" align="right">
              <template #default="{ row }">{{ pct(row.percentage) }}</template>
            </el-table-column>
            <el-table-column prop="expenseDate" :label="$tf('日期')" width="120" />
          </template>
        </el-table>
        <div class="detail-pagination">
          <el-pagination v-model:current-page="detailPage" v-model:page-size="detailSize"
            :page-sizes="[10, 20, 50, 100]" :total="detailTotal"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="loadDetail" @current-change="loadDetail" />
        </div>
      </div>
    </el-drawer>

    <!-- 月度下钻抽屉：开票/同事/渠道 明细 -->
    <el-drawer
      v-model="monthlyDrawerVisible"
      :title="$tt(monthlyDrawerTitle)"
      direction="rtl"
      size="76%"
      :close-on-click-modal="false"
    >
      <div class="detail-drawer">
        <div class="detail-filter">
          <el-tag size="small" type="info">期间 {{ monthlyScopeText }}</el-tag>
          <el-radio-group v-if="monthlyDrawer.key === 'invoice-month' || monthlyDrawer.key === 'saler-month'"
            v-model="monthlySubType" size="small" @change="resetMonthlySearch">
            <template v-if="monthlyDrawer.key === 'invoice-month'">
              <el-radio-button label="invoiced">{{ $tt("已开票明细") }}</el-radio-button>
              <el-radio-button label="uninvoiced">{{ $tt("未开票明细") }}</el-radio-button>
            </template>
            <template v-else-if="monthlyDrawer.key === 'saler-month'">
              <el-radio-button label="new-ar">{{ $tt("本月新开应收") }}</el-radio-button>
              <el-radio-button label="received">{{ $tt("本月回款") }}</el-radio-button>
              <el-radio-button label="open">{{ $tt("累计未收明细") }}</el-radio-button>
            </template>
          </el-radio-group>
          <el-input v-model="monthlyKeyword" :placeholder="$tt('搜索单号/客户')" clearable
            style="width: 200px" @keyup.enter="loadMonthlyDetail" />
          <el-button type="primary" size="small" :loading="monthlyDetailLoading" @click="loadMonthlyDetail">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button size="small" @click="resetMonthlySearch">{{ $tt("重置") }}</el-button>
          <el-button type="success" size="small" plain :loading="monthlyDetailLoading" @click="handleExportMonthly">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </div>
        <el-table :data="monthlyDetailRows" v-loading="monthlyDetailLoading" border stripe size="small"
          max-height="560" style="margin-top: 12px;">
          <el-table-column v-for="c in monthlyColumns" :key="c.prop" :label="$tf(c.label)"
            :prop="c.prop" :width="c.width || undefined" :min-width="c.minWidth || 110" :align="c.align || 'left'">
            <template #default="{ row }">
              <span v-if="c.money">{{ moneyMaybe(row[c.prop]) }}</span>
              <span v-else>{{ row[c.prop] ?? '' }}</span>
            </template>
          </el-table-column>
          <template #empty><el-empty :description="$tt('暂无数据')" :image-size="80" /></template>
        </el-table>
        <div class="detail-pagination">
          <el-pagination v-model:current-page="monthlyPage" v-model:page-size="monthlySize"
            :page-sizes="[10, 20, 50, 100]" :total="monthlyTotal"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="loadMonthlyDetail" @current-change="loadMonthlyDetail" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import echarts from '@/utils/echartsCore'
import { ElMessage } from 'element-plus'
import { Search, View, Download } from '@element-plus/icons-vue'
import { exportCsv } from '@/utils/csvExport'
import {
  financeArApSummary,
  financeAging,
  financeProfit,
  financeExpenseAllocation,
  financeArApDetails,
  financeAgingDetails,
  financeProfitDetails,
  financeExpenseAllocationDetails,
  financeMonthlyInvoiceSummary,
  financeMonthlyInvoiceDetails,
  financeMonthlySalerSummary,
  financeMonthlySalerDetails,
  financeMonthlyChannelSummary,
  financeMonthlyChannelDetails
} from '@/api/report'
import { tt } from '@/i18n'

// 查询条件
const dateRange = ref([])
const keyword = ref('')
const loadingAll = ref(false)
const loadingArAp = ref(false)
const loadingAging = ref(false)
const loadingProfit = ref(false)
const loadingExpense = ref(false)

// 四张报表的数据
const arApData = ref({})
const agingData = ref({})
const profitData = ref({})
const expenseData = ref({})

// 图表 DOM 引用
const arApBarChart = ref(null)
const agingBarChart = ref(null)
const profitLineChart = ref(null)
const expensePieChart = ref(null)
// 图表实例
let arApBarChartInstance = null
let agingBarChartInstance = null
let profitLineChartInstance = null
let expensePieChartInstance = null

// 应收应付 KPI
const arApKpis = computed(() => {
  const d = arApData.value || {}
  return [
    { label: '应收', value: '¥' + formatMoney(d.totalReceivable), color: '#409eff', bg: '#ecf5ff' },
    { label: '已收', value: '¥' + formatMoney(d.totalReceived), color: '#67c23a', bg: '#f0f9eb' },
    { label: '应付', value: '¥' + formatMoney(d.totalPayable), color: '#e6a23c', bg: '#fdf6ec' },
    { label: '已付', value: '¥' + formatMoney(d.totalPaid), color: '#909399', bg: '#f4f4f5' },
    { label: '净现金流', value: '¥' + formatMoney(d.netCashFlow), color: '#f56c6c', bg: '#fef0f0' }
  ]
})

// 账龄 KPI
const agingKpis = computed(() => {
  const d = agingData.value || {}
  return [
    { label: '总余额', value: '¥' + formatMoney(d.totalBalance), color: '#409eff', bg: '#ecf5ff' },
    { label: '总笔数', value: d.totalCount ?? 0, color: '#909399', bg: '#f4f4f5' },
    { label: '逾期数', value: d.overdueCount ?? 0, color: '#e6a23c', bg: '#fdf6ec' },
    { label: '逾期金额', value: '¥' + formatMoney(d.overdueAmount), color: '#f56c6c', bg: '#fef0f0' }
  ]
})

// 利润 KPI
const profitKpis = computed(() => {
  const d = profitData.value || {}
  return [
    { label: '收入', value: '¥' + formatMoney(d.revenue), color: '#409eff', bg: '#ecf5ff' },
    { label: '采购成本', value: '¥' + formatMoney(d.purchaseCost), color: '#e6a23c', bg: '#fdf6ec' },
    { label: '费用分摊', value: '¥' + formatMoney(d.operatingExpense), color: '#909399', bg: '#f4f4f5' },
    { label: '毛利', value: '¥' + formatMoney(d.grossProfit), color: '#67c23a', bg: '#f0f9eb' },
    { label: '净利', value: '¥' + formatMoney(d.netProfit), color: '#67c23a', bg: '#f0f9eb' },
    { label: '毛利率', value: (d.grossMargin ?? 0) + '%', color: '#409eff', bg: '#ecf5ff' },
    { label: '净利率', value: (d.netMargin ?? 0) + '%', color: '#67c23a', bg: '#f0f9eb' }
  ]
})

// 费用分摊 KPI
const expenseKpis = computed(() => {
  const d = expenseData.value || {}
  return [
    { label: '总费用', value: '¥' + formatMoney(d.totalExpense), color: '#f56c6c', bg: '#fef0f0' },
    { label: '总笔数', value: d.totalCount ?? 0, color: '#409eff', bg: '#ecf5ff' }
  ]
})

// 金额格式化：¥ + 千分位 + 2位小数
function formatMoney(num) {
  if (num === null || num === undefined || num === '') return '0.00'
  return Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 构造查询参数
function buildParams() {
  const params = {}
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  if (keyword.value) params.keyword = keyword.value
  return params
}

// 加载应收应付汇总
async function loadArAp() {
  loadingArAp.value = true
  try {
    const data = await financeArApSummary(buildParams())
    arApData.value = data || {}
    await nextTick()
    renderArApBarChart()
  } catch (e) {
    console.error('加载应收应付汇总失败', e)
  } finally {
    loadingArAp.value = false
  }
}

// 加载账龄分析
async function loadAging() {
  loadingAging.value = true
  try {
    const data = await financeAging(buildParams())
    agingData.value = data || {}
    await nextTick()
    renderAgingBarChart()
  } catch (e) {
    console.error('加载账龄分析失败', e)
  } finally {
    loadingAging.value = false
  }
}

// 加载利润分析
async function loadProfit() {
  loadingProfit.value = true
  try {
    const data = await financeProfit(buildParams())
    profitData.value = data || {}
    await nextTick()
    renderProfitLineChart()
    renderExpensePieChart()
  } catch (e) {
    console.error('加载利润分析失败', e)
  } finally {
    loadingProfit.value = false
  }
}

// 加载费用分摊统计
async function loadExpense() {
  loadingExpense.value = true
  try {
    const data = await financeExpenseAllocation(buildParams())
    expenseData.value = data || {}
  } catch (e) {
    console.error('加载费用分摊统计失败', e)
  } finally {
    loadingExpense.value = false
  }
}

// 渲染应收应付对比柱状图
function renderArApBarChart() {
  if (!arApBarChart.value) return
  if (!arApBarChartInstance) {
    arApBarChartInstance = echarts.init(arApBarChart.value)
  }
  const d = arApData.value || {}
  arApBarChartInstance.setOption({
    title: { text: '应收应付对比', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', formatter: '{b}: ¥{c}' },
    legend: { bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '16%', containLabel: true },
    xAxis: { type: 'category', data: ['应收', '已收', '应付', '已付'] },
    yAxis: { type: 'value', axisLabel: { formatter: value => '¥' + formatMoney(value) } },
    series: [{
      type: 'bar',
      barWidth: '45%',
      data: [
        { value: Number(d.totalReceivable) || 0, itemStyle: { color: '#409eff' } },
        { value: Number(d.totalReceived) || 0, itemStyle: { color: '#67c23a' } },
        { value: Number(d.totalPayable) || 0, itemStyle: { color: '#e6a23c' } },
        { value: Number(d.totalPaid) || 0, itemStyle: { color: '#909399' } }
      ],
      label: { show: true, position: 'top', formatter: p => '¥' + formatMoney(p.value) }
    }]
  })
}

// 渲染账龄分桶柱状图
function renderAgingBarChart() {
  if (!agingBarChart.value) return
  if (!agingBarChartInstance) {
    agingBarChartInstance = echarts.init(agingBarChart.value)
  }
  const buckets = agingData.value.buckets || []
  agingBarChartInstance.setOption({
    title: { text: '账龄分桶统计', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>笔数: ${p[0].value} 笔<br/>金额: ¥${formatMoney(p[0].data && p[0].data.amount)}` },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '16%', containLabel: true },
    xAxis: { type: 'category', data: buckets.map(b => b.bucket) },
    yAxis: [
      { type: 'value', name: '笔数', position: 'left' },
      { type: 'value', name: '金额', position: 'right', axisLabel: { formatter: value => '¥' + formatMoney(value) } }
    ],
    series: [{
      type: 'bar',
      barWidth: '45%',
      data: buckets.map(b => ({
        value: Number(b.count) || 0,
        amount: Number(b.amount) || 0,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#79bbff' }
          ])
        }
      })),
      label: { show: true, position: 'top', formatter: p => p.value + '笔' }
    }]
  })
}

// 渲染收入vs成本折线图
function renderProfitLineChart() {
  if (!profitLineChart.value) return
  if (!profitLineChartInstance) {
    profitLineChartInstance = echarts.init(profitLineChart.value)
  }
  const d = profitData.value || {}
  // 后端返回的是汇总数据，按本期展示收入与成本对比
  const totalCost = (Number(d.purchaseCost) || 0) + (Number(d.operatingExpense) || 0)
  profitLineChartInstance.setOption({
    title: { text: '收入 vs 成本', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', formatter: '{a}: ¥{c}' },
    legend: { data: ['收入', '成本'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '16%', containLabel: true },
    xAxis: { type: 'category', data: ['本期'] },
    yAxis: { type: 'value', axisLabel: { formatter: value => '¥' + formatMoney(value) } },
    series: [
      {
        name: '收入',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: [Number(d.revenue) || 0],
        itemStyle: { color: '#409eff' },
        lineStyle: { width: 2, color: '#409eff' }
      },
      {
        name: '成本',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: [totalCost],
        itemStyle: { color: '#f56c6c' },
        lineStyle: { width: 2, color: '#f56c6c' }
      }
    ]
  })
}

// 渲染费用类型饼图
function renderExpensePieChart() {
  if (!expensePieChart.value) return
  if (!expensePieChartInstance) {
    expensePieChartInstance = echarts.init(expensePieChart.value)
  }
  const list = (profitData.value && profitData.value.expenseByType) || []
  const palette = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6', '#1abc9c', '#34495e']
  expensePieChartInstance.setOption({
    title: { text: '费用类型分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { type: 'scroll', bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: list.map((it, idx) => ({
        name: it.expenseType || '未分类',
        value: Number(it.amount) || 0,
        itemStyle: { color: palette[idx % palette.length] }
      }))
    }]
  })
}

// 加载全部报表数据
async function loadAll() {
  loadingAll.value = true
  try {
    await Promise.allSettled([
      loadArAp(), loadAging(), loadProfit(), loadExpense(),
      loadInvoiceMonth(), loadSalerMonth(), loadChannelMonth()
    ])
  } finally {
    loadingAll.value = false
  }
}

// 重置全局筛选并重新查询
function resetFilter() {
  dateRange.value = []
  keyword.value = ''
  loadAll()
}

// 明细抽屉复用的金额格式化（与 formatMoney 一致）
function fmt(num) {
  return formatMoney(num)
}

// 百分比格式化
function pct(num) {
  if (num === null || num === undefined || num === '') return '0%'
  return Number(num) + '%'
}

// ===== 共享明细抽屉 =====
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref([])
const detailTotal = ref(0)
const detailPage = ref(1)
const detailSize = ref(20)
const detailKeyword = ref('')
const detailDateRange = ref([])
const currentReportKey = ref('')
const currentReportTitle = ref('')

const reportTitleMap = {
  'ar-ap-summary': '应收应付汇总',
  'aging': '账龄分析',
  'profit': '利润分析',
  'expense-allocation': '费用分配'
}

const reportDetailApiMap = {
  'ar-ap-summary': financeArApDetails,
  'aging': financeAgingDetails,
  'profit': financeProfitDetails,
  'expense-allocation': financeExpenseAllocationDetails
}

// 打开明细抽屉：继承全局筛选条件
function openDetail(key) {
  currentReportKey.value = key
  currentReportTitle.value = reportTitleMap[key] || ''
  detailDateRange.value = dateRange.value && dateRange.value.length === 2 ? [...dateRange.value] : []
  detailKeyword.value = keyword.value || ''
  detailPage.value = 1
  detailSize.value = 20
  detailDrawerVisible.value = true
  loadDetail()
}

// 加载明细数据
async function loadDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  if (!api) return
  detailLoading.value = true
  try {
    const params = { page: detailPage.value, size: detailSize.value }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    if (Array.isArray(res)) {
      detailData.value = res
      detailTotal.value = res.length
    } else {
      detailData.value = res.records || []
      detailTotal.value = res.total || 0
    }
  } catch (e) {
    console.error('加载明细失败', e)
    ElMessage.error(tt('加载明细失败'))
    detailData.value = []
    detailTotal.value = 0
  } finally {
    detailLoading.value = false
  }
}

// 重置明细筛选
function resetDetail() {
  detailDateRange.value = []
  detailKeyword.value = ''
  detailPage.value = 1
  loadDetail()
}

// 明细抽屉导出列定义（与表格列一致）
const reportDetailColumnsMap = {
  'ar-ap-summary': [
    { label: '单号', prop: 'billNo' },
    { label: '类型', prop: 'billType' },
    { label: '客户/供应商', prop: 'customerName' },
    { label: '金额', prop: 'amount' },
    { label: '已收/已付', prop: 'paidAmount' },
    { label: '状态', prop: 'status' },
    { label: '日期', prop: 'billDate' }
  ],
  'aging': [
    { label: '客户名称', prop: 'customerName' },
    { label: '账期', prop: 'agingBucket' },
    { label: '应收金额', prop: 'receivableAmount' },
    { label: '未收金额', prop: 'balanceAmount' },
    { label: '逾期天数', prop: 'overdueDays' }
  ],
  'profit': [
    { label: '月份', prop: 'month' },
    { label: '收入', prop: 'income' },
    { label: '成本', prop: 'cost' },
    { label: '费用', prop: 'expense' },
    { label: '利润', prop: 'profit' },
    { label: '利润率', prop: 'profitRate' }
  ],
  'expense-allocation': [
    { label: '部门', prop: 'deptName' },
    { label: '费用类型', prop: 'expenseType' },
    { label: '金额', prop: 'amount' },
    { label: '占比', prop: 'percentage' },
    { label: '日期', prop: 'expenseDate' }
  ]
}

async function handleExportDetail() {
  const api = reportDetailApiMap[currentReportKey.value]
  const columns = reportDetailColumnsMap[currentReportKey.value]
  if (!api || !columns) { ElMessage.warning(tt('当前无可导出的报表数据')); return }
  try {
    detailLoading.value = true
    const params = { page: 1, size: 9999 }
    if (detailDateRange.value && detailDateRange.value.length === 2) {
      params.startDate = detailDateRange.value[0]
      params.endDate = detailDateRange.value[1]
    }
    if (detailKeyword.value) params.keyword = detailKeyword.value
    const res = await api(params)
    const rows = Array.isArray(res) ? res : (res.records || [])
    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(columns, rows, currentReportTitle.value || '财务报表明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    detailLoading.value = false
  }
}

// ==================== 月度报表：卡5/6/7 ====================
const nowYear = new Date().getFullYear()
const yearOptions = computed(() => {
  const list = []
  for (let i = 0; i < 6; i++) list.push(nowYear - i)
  return list
})
function currentYm() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
}
function moneyCompact(num) { return '¥' + formatMoney(num) }
function moneyMaybe(num) {
  if (num === null || num === undefined || num === '' || Number(num) === 0) return ''
  return '¥' + formatMoney(num)
}
function moneyCell(num) { return moneyMaybe(num) }
function cellMoney(num) { return moneyMaybe(num) }

// 卡5：月度开票与未开票
const invoiceYear = ref(nowYear)
const invoiceData = ref({})
const loadingInvoiceMonth = ref(false)
const invoiceBarChart = ref(null)
let invoiceBarChartInstance = null
const invoiceMonthKpis = computed(() => {
  const d = invoiceData.value || {}
  return [
    { label: '开票合计(含税)', value: '¥' + formatMoney(d.invoiceAmount), color: '#409eff', bg: '#ecf5ff', act: { kind: 'invoiceYear', type: 'invoiced' } },
    { label: '未开票合计', value: '¥' + formatMoney(d.uninvoicedAmount), color: '#e6a23c', bg: '#fdf6ec', act: { kind: 'invoiceYear', type: 'uninvoiced' } },
    { label: '回款合计', value: '¥' + formatMoney(d.receiptAmount), color: '#67c23a', bg: '#f0f9eb', act: { kind: 'channelYear', catKey: '' } },
    { label: '当前未收余额', value: '¥' + formatMoney(d.openBalance), color: '#f56c6c', bg: '#fef0f0', act: { kind: 'salerOpen' } }
  ]
})
async function loadInvoiceMonth() {
  loadingInvoiceMonth.value = true
  try {
    invoiceData.value = (await financeMonthlyInvoiceSummary({ year: invoiceYear.value })) || {}
    await nextTick()
    renderInvoiceBar()
  } catch (e) {
    console.error('加载月度开票失败', e)
    invoiceData.value = {}
  } finally {
    loadingInvoiceMonth.value = false
  }
}
function renderInvoiceBar() {
  if (!invoiceBarChart.value) return
  if (!invoiceBarChartInstance) invoiceBarChartInstance = echarts.init(invoiceBarChart.value)
  const rows = invoiceData.value.byMonth || []
  invoiceBarChartInstance.setOption({
    title: { text: '月度开票 vs 未开票', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>${p[0].marker}${p[0].seriesName}: ¥${formatMoney(p[0].value)}<br/>${p[1].marker}${p[1].seriesName}: ¥${formatMoney(p[1].value)}` },
    legend: { data: ['开票金额', '未开票金额'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '16%', containLabel: true },
    xAxis: { type: 'category', data: rows.map(r => r.month.substring(5) + '月') },
    yAxis: { type: 'value', axisLabel: { formatter: v => formatMoney(v) } },
    series: [
      { name: '开票金额', type: 'bar', data: rows.map(r => Number(r.invoiceAmount) || 0), itemStyle: { color: '#409eff' } },
      { name: '未开票金额', type: 'bar', data: rows.map(r => Number(r.uninvoicedAmount) || 0), itemStyle: { color: '#e6a23c' } }
    ]
  })
}
function defaultInvoiceMonth() {
  const rows = (invoiceData.value && invoiceData.value.byMonth) || []
  const hit = rows.find(r => (Number(r.invoiceAmount) || Number(r.uninvoicedAmount) || Number(r.receiptAmount)) !== 0)
  return hit ? hit.month : (invoiceYear.value + '-01')
}
function onInvoiceMonthCellClick(row, column) {
  if (!row || !row.month || !column || !column.property) return
  const prop = column.property
  if (prop === 'month' || prop === 'receiptAmount' || prop === 'invoiceCount') return
  const type = prop === 'uninvoicedAmount' ? 'uninvoiced' : 'invoiced'
  openMonthly('invoice-month', row.month, type)
}

// 卡6：同事回款与未收
const salerMonth = ref(currentYm())
const salerData = ref({})
const loadingSalerMonth = ref(false)
const salerMonthKpis = computed(() => {
  const d = salerData.value || {}
  return [
    { label: '本月新开应收', value: '¥' + formatMoney(d.totalNewAr), color: '#409eff', bg: '#ecf5ff', act: { kind: 'salerMonth', type: 'new-ar' } },
    { label: '本月回款(已归属)', value: '¥' + formatMoney(d.totalReceived), color: '#67c23a', bg: '#f0f9eb', act: { kind: 'salerMonth', type: 'received' } },
    { label: '累计未收余额', value: '¥' + formatMoney(d.totalOpen), color: '#f56c6c', bg: '#fef0f0', act: { kind: 'salerOpen' } }
  ]
})
async function loadSalerMonth() {
  loadingSalerMonth.value = true
  try {
    salerData.value = (await financeMonthlySalerSummary({ month: salerMonth.value || currentYm() })) || {}
  } catch (e) {
    console.error('加载同事回款失败', e)
    salerData.value = {}
  } finally {
    loadingSalerMonth.value = false
  }
}
function onSalerRowClick(row) {
  if (!row) return
  openMonthly('saler-month', salerMonth.value || currentYm(), 'open', row.salerKey)
}

// 卡7：月度收款渠道
const channelYear = ref(nowYear)
const channelData = ref({})
const loadingChannelMonth = ref(false)
const channelMonthKpis = computed(() => {
  const d = channelData.value || {}
  return [
    { label: '年收入合计', value: '¥' + formatMoney(d.totalAmount), color: '#67c23a', bg: '#f0f9eb', act: { kind: 'channelYear', catKey: '' } },
    { label: '总笔数', value: d.totalCount ?? 0, color: '#409eff', bg: '#ecf5ff', act: { kind: 'channelYear', catKey: '' } }
  ]
})
const channelColumns = computed(() => (channelData.value && channelData.value.columns) || [])
async function loadChannelMonth() {
  loadingChannelMonth.value = true
  try {
    channelData.value = (await financeMonthlyChannelSummary({ year: channelYear.value })) || {}
  } catch (e) {
    console.error('加载月度渠道失败', e)
    channelData.value = {}
  } finally {
    loadingChannelMonth.value = false
  }
}
function firstChannelMonth() {
  const rows = (channelData.value && channelData.value.rows) || []
  const hit = rows.find(r => Number(r.total) !== 0)
  return hit ? hit.month : (channelYear.value + '-01')
}
function onChannelCellClick(row, column) {
  if (!row || !row.month || !column) return
  const label = column.label
  const idx = channelColumns.value.findIndex(c => c.label === label)
  if (idx < 0) return
  openMonthly('channel-month', row.month, null, null, channelColumns.value[idx].key)
}
function channelSummary({ columns, data }) {
  const sums = ['合计']
  for (let i = 1; i < columns.length; i++) {
    const idx = channelColumns.value.findIndex(c => c.label === columns[i].label)
    let s = 0
    ;(data || []).forEach(r => {
      const v = r.cells && r.cells[idx]
      if (v) s += Number(v)
    })
    sums.push(s ? moneyCompact(s) : '')
  }
  return sums
}

// 通用可点击行样式
function clickableRow() { return 'row-clickable' }

// ===== 月度下钻抽屉 =====
const monthlyDrawerVisible = ref(false)
const monthlyDrawerTitle = ref('')
const monthlyDrawer = ref({ key: '', month: '', year: null, type: '', salerKey: null, categoryKey: '' })
const monthlyScopeText = computed(() => {
  const d = monthlyDrawer.value
  if (d.month) return d.month
  if (d.year) return d.year + ' 全年'
  return '—'
})
const monthlySubType = ref('invoiced')
const monthlyKeyword = ref('')
const monthlyDetailLoading = ref(false)
const monthlyDetailRows = ref([])
const monthlyTotal = ref(0)
const monthlyPage = ref(1)
const monthlySize = ref(20)

const monthlyTitleMap = {
  'invoice-month': '开票/未开票月度明细',
  'saler-month': '同事回款与未收明细',
  'channel-month': '月度收款渠道流水'
}
const monthlyApiMap = {
  'invoice-month': financeMonthlyInvoiceDetails,
  'saler-month': financeMonthlySalerDetails,
  'channel-month': financeMonthlyChannelDetails
}

function openMonthly(key, month, type, salerKey, categoryKey, year) {
  const isMonth = !!month
  const defType = type || (key === 'invoice-month' ? 'invoiced' : key === 'saler-month' ? 'open' : '')
  const baseYear = year || (key === 'invoice-month' ? invoiceYear.value : key === 'channel-month' ? channelYear.value : null)
  monthlyDrawer.value = {
    key,
    month: isMonth ? month : '',
    year: isMonth ? null : baseYear,
    type: defType,
    salerKey: salerKey !== undefined && salerKey !== null ? Number(salerKey) : -1,
    categoryKey: categoryKey || ''
  }
  monthlySubType.value = defType
  monthlyKeyword.value = ''
  monthlyPage.value = 1
  monthlySize.value = 20
  const title = monthlyTitleMap[key] || '月度明细'
  monthlyDrawerTitle.value = (!isMonth && baseYear) ? title + '（' + baseYear + ' 全年）' : title
  monthlyDrawerVisible.value = true
  loadMonthlyDetail()
}

// 顶部统计卡片（KPI）点击 → 打开对应明细
function kpiClick(kpi) {
  const a = kpi && kpi.act
  if (!a) return
  if (a.kind === 'invoiceYear') {
    openMonthly('invoice-month', '', a.type, undefined, '', invoiceYear.value)
  } else if (a.kind === 'channelYear') {
    openMonthly('channel-month', '', undefined, undefined, a.catKey || '', channelYear.value)
  } else if (a.kind === 'salerMonth') {
    openMonthly('saler-month', salerMonth.value || currentYm(), a.type, undefined, '')
  } else if (a.kind === 'salerOpen') {
    openMonthly('saler-month', salerMonth.value || currentYm(), 'open', undefined, '')
  }
}

const monthlyColumns = computed(() => {
  const key = monthlyDrawer.value.key
  const type = monthlySubType.value
  if (key === 'invoice-month') {
    if (type === 'uninvoiced') {
      return [
        { label: '订单号', prop: 'billNo', minWidth: 150 },
        { label: '订单日期', prop: 'billDate', width: 110 },
        { label: '客户', prop: 'customerName', minWidth: 150 },
        { label: '业务员', prop: 'salerName', minWidth: 100 },
        { label: '订单净额', prop: 'totalAmount', align: 'right', money: true, minWidth: 120 },
        { label: '已开票额', prop: 'invoicedAmount', align: 'right', money: true, minWidth: 120 },
        { label: '未开票额', prop: 'uninvoicedAmount', align: 'right', money: true, minWidth: 120 }
      ]
    }
    return [
      { label: '发票号', prop: 'billNo', minWidth: 150 },
      { label: '开票日期', prop: 'billDate', width: 110 },
      { label: '客户', prop: 'customerName', minWidth: 150 },
      { label: '业务员', prop: 'salerName', minWidth: 100 },
      { label: '含税金额', prop: 'totalAmount', align: 'right', money: true, minWidth: 120 },
      { label: '税额', prop: 'taxAmount', align: 'right', money: true, minWidth: 110 },
      { label: '发票类型', prop: 'invoiceType', width: 140 },
      { label: '蓝/红', prop: 'flag', width: 70, align: 'center' }
    ]
  }
  if (key === 'saler-month') {
    if (type === 'new-ar') {
      return [
        { label: '应收单号', prop: 'billNo', minWidth: 150 },
        { label: '应收日期', prop: 'billDate', width: 110 },
        { label: '客户', prop: 'customerName', minWidth: 160 },
        { label: '应收金额', prop: 'totalAmount', align: 'right', money: true, minWidth: 120 },
        { label: '已收', prop: 'paidAmount', align: 'right', money: true, minWidth: 120 },
        { label: '余额', prop: 'balanceAmount', align: 'right', money: true, minWidth: 120 },
        { label: '来源单号', prop: 'sourceBillNo', minWidth: 140 },
        { label: '状态', prop: 'status', width: 100, align: 'center' }
      ]
    }
    if (type === 'received') {
      return [
        { label: '收款单号', prop: 'billNo', minWidth: 150 },
        { label: '收款日期', prop: 'billDate', width: 110 },
        { label: '客户', prop: 'customerName', minWidth: 160 },
        { label: '业务员', prop: 'salerName', minWidth: 110 },
        { label: '渠道', prop: 'channel', width: 110 },
        { label: '金额', prop: 'amount', align: 'right', money: true, minWidth: 120 },
        { label: '来源', prop: 'source', width: 90, align: 'center' }
      ]
    }
    return [
      { label: '应收单号', prop: 'billNo', minWidth: 150 },
      { label: '应收日期', prop: 'billDate', width: 110 },
      { label: '客户', prop: 'customerName', minWidth: 160 },
      { label: '发票号', prop: 'invoiceNo', minWidth: 130 },
      { label: '到期日', prop: 'dueDate', width: 110 },
      { label: '应收金额', prop: 'totalAmount', align: 'right', money: true, minWidth: 120 },
      { label: '已收', prop: 'paidAmount', align: 'right', money: true, minWidth: 120 },
      { label: '余额', prop: 'balanceAmount', align: 'right', money: true, minWidth: 120 },
      { label: '状态', prop: 'status', width: 100, align: 'center' }
    ]
  }
  // channel
  return [
    { label: '来源', prop: 'source', width: 90, align: 'center' },
    { label: '单号', prop: 'billNo', minWidth: 160 },
    { label: '日期', prop: 'billDate', width: 110 },
    { label: '客户', prop: 'customerName', minWidth: 160 },
    { label: '渠道/账户', prop: 'channel', minWidth: 120 },
    { label: '账户', prop: 'account', minWidth: 140 },
    { label: '金额', prop: 'amount', align: 'right', money: true, minWidth: 130 }
  ]
})

async function loadMonthlyDetail() {
  const key = monthlyDrawer.value.key
  const api = monthlyApiMap[key]
  if (!api) return
  monthlyDetailLoading.value = true
  try {
    const params = { page: monthlyPage.value, size: monthlySize.value }
    if (monthlyDrawer.value.month) {
      params.month = monthlyDrawer.value.month
    } else if (monthlyDrawer.value.year) {
      params.year = monthlyDrawer.value.year
    }
    if (monthlyKeyword.value) params.keyword = monthlyKeyword.value
    if (key === 'invoice-month' || key === 'saler-month') params.type = monthlySubType.value
    if (key === 'saler-month' && monthlyDrawer.value.salerKey != null) params.salerKey = monthlyDrawer.value.salerKey
    if (key === 'channel-month' && monthlyDrawer.value.categoryKey) params.categoryKey = monthlyDrawer.value.categoryKey
    const res = await api(params)
    monthlyDetailRows.value = (res && res.records) || []
    monthlyTotal.value = (res && res.total) || 0
  } catch (e) {
    console.error('加载月度明细失败', e)
    monthlyDetailRows.value = []
    monthlyTotal.value = 0
  } finally {
    monthlyDetailLoading.value = false
  }
}

function resetMonthlySearch() {
  monthlyPage.value = 1
  loadMonthlyDetail()
}

async function handleExportMonthly() {
  const key = monthlyDrawer.value.key
  const api = monthlyApiMap[key]
  const columns = monthlyColumns.value
  if (!api || !columns.length) return
  try {
    monthlyDetailLoading.value = true
    const params = { page: 1, size: 9999 }
    if (monthlyDrawer.value.month) {
      params.month = monthlyDrawer.value.month
    } else if (monthlyDrawer.value.year) {
      params.year = monthlyDrawer.value.year
    }
    if (monthlyKeyword.value) params.keyword = monthlyKeyword.value
    if (key === 'invoice-month' || key === 'saler-month') params.type = monthlySubType.value
    if (key === 'saler-month' && monthlyDrawer.value.salerKey != null) params.salerKey = monthlyDrawer.value.salerKey
    if (key === 'channel-month' && monthlyDrawer.value.categoryKey) params.categoryKey = monthlyDrawer.value.categoryKey
    const res = await api(params)
    const rows = (res && res.records) || []
    if (!rows.length) { ElMessage.warning(tt('没有可导出的数据')); return }
    exportCsv(columns.map(c => ({ label: c.label, prop: c.prop })), rows, monthlyDrawerTitle.value || '月度明细')
    ElMessage.success(`${tt("已导出 ")}${rows.length}${tt(" 条数据")}`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error(e.message || tt('导出失败'))
  } finally {
    monthlyDetailLoading.value = false
  }
}

// 窗口尺寸变化时重绘图表
function handleResize() {
  arApBarChartInstance?.resize()
  agingBarChartInstance?.resize()
  profitLineChartInstance?.resize()
  expensePieChartInstance?.resize()
  invoiceBarChartInstance?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  arApBarChartInstance?.dispose()
  agingBarChartInstance?.dispose()
  profitLineChartInstance?.dispose()
  expensePieChartInstance?.dispose()
  invoiceBarChartInstance?.dispose()
})
</script>

<style scoped>
.report-container { padding: 20px; background: #f5f7fa; min-height: 100%; }
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; align-items: center; }
.report-card { margin-bottom: 16px; border-radius: 8px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 500;
}
.card-title { font-size: 15px; font-weight: 600; color: #303133; }
.kpi-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
}
.kpi-box {
  flex: 1;
  min-width: 140px;
  padding: 16px 18px;
  border-radius: 8px;
}
.kpi-label { font-size: 13px; color: #606266; margin-bottom: 8px; }
.kpi-value { font-size: 22px; font-weight: 600; word-break: break-all; }
.table-title {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  margin: 12px 0 8px;
}
.chart-box { width: 100%; height: 320px; margin-top: 10px; }
/* 卡片 header 右侧容器：标签 + 查看明细按钮 */
.card-header-right { display: flex; align-items: center; gap: 8px; }
/* 共享明细抽屉 */
.detail-drawer { padding: 0 16px 16px; }
.detail-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.detail-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.overdue-text {
  color: #F56C6C;
  font-weight: 600;
}
/* 月度报表：可点击行/单元格 */
.kpi-clickable { transition: box-shadow .15s, transform .15s; }
.kpi-clickable:hover { box-shadow: 0 2px 10px rgba(64, 158, 255, .25); transform: translateY(-1px); }
.kpi-clickable:hover .kpi-value { text-decoration: underline; }
.row-clickable { cursor: pointer; }
.row-clickable:hover td { background: #f0f7ff !important; }
.cell-link {
  color: #409eff;
  cursor: pointer;
}
.cell-link:hover { text-decoration: underline; }
.danger-cell { color: #f56c6c; }
</style>
