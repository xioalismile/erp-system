<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- 应收账款 -->
      <el-tab-pane label="应收账款" name="ar">
        <div class="filter-bar">
          <el-input v-model="arFilters.arNo" placeholder="应收单号" class="filter-input" @keyup.enter="loadAr" />
          <el-input v-model="arFilters.customerName" placeholder="客户名称" class="filter-input" @keyup.enter="loadAr" />
          <el-select v-model="arFilters.status" placeholder="状态" clearable class="filter-input">
            <el-option label="待收款" value="待收款" />
            <el-option label="部分收款" value="部分收款" />
            <el-option label="已结清" value="已结清" />
          </el-select>
          <el-button type="primary" @click="loadAr">查询</el-button>
        </div>

        <el-table :data="arList" border>
          <el-table-column prop="arNo" label="应收单号" width="150" />
          <el-table-column prop="sourceBillNo" label="来源单据" width="130" />
          <el-table-column prop="customerName" label="客户名称" />
          <el-table-column label="应收金额" width="120">
            <template #default="{ row }">{{ row.totalAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="已收金额" width="120">
            <template #default="{ row }">{{ row.paidAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="余额" width="120">
            <template #default="{ row }">{{ row.balanceAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="dueDate" label="预计收款日期" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getArStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="receivePayment(row)">收款</el-button>
              <el-button size="small" text type="info" @click="viewArDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="arTotal > 0"
          :total="arTotal"
          v-model:current-page="arPage"
          :page-size="20"
          layout="total, prev, pager, next"
          @current-change="loadAr"
          style="margin-top: 10px"
        />
      </el-tab-pane>

      <!-- 应付账款 -->
      <el-tab-pane label="应付账款" name="ap">
        <div class="filter-bar">
          <el-input v-model="apFilters.apNo" placeholder="应付单号" class="filter-input" @keyup.enter="loadAp" />
          <el-input v-model="apFilters.supplierName" placeholder="供应商名称" class="filter-input" @keyup.enter="loadAp" />
          <el-select v-model="apFilters.status" placeholder="状态" clearable class="filter-input">
            <el-option label="待付款" value="待付款" />
            <el-option label="部分付款" value="部分付款" />
            <el-option label="已结清" value="已结清" />
          </el-select>
          <el-button type="primary" @click="loadAp">查询</el-button>
        </div>

        <el-table :data="apList" border>
          <el-table-column prop="apNo" label="应付单号" width="150" />
          <el-table-column prop="sourceBillNo" label="来源单据" width="130" />
          <el-table-column prop="supplierName" label="供应商名称" />
          <el-table-column label="应付金额" width="120">
            <template #default="{ row }">{{ row.totalAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="已付金额" width="120">
            <template #default="{ row }">{{ row.paidAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="余额" width="120">
            <template #default="{ row }">{{ row.balanceAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="dueDate" label="预计付款日期" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getApStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="warning" @click="makePayment(row)">付款</el-button>
              <el-button size="small" text type="info" @click="viewApDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="apTotal > 0"
          :total="apTotal"
          v-model:current-page="apPage"
          :page-size="20"
          layout="total, prev, pager, next"
          @current-change="loadAp"
          style="margin-top: 10px"
        />
      </el-tab-pane>

      <!-- 收款记录 -->
      <el-tab-pane label="收款记录" name="receipts">
        <div class="filter-bar">
          <el-input v-model="receiptFilters.receiptNo" placeholder="收款单号" class="filter-input" />
          <el-input v-model="receiptFilters.customerName" placeholder="客户名称" class="filter-input" />
          <el-select v-model="receiptFilters.status" placeholder="状态" clearable class="filter-input">
            <el-option label="待确认" value="待确认" />
            <el-option label="已确认" value="已确认" />
            <el-option label="已作废" value="已作废" />
          </el-select>
          <el-button type="primary" @click="loadReceipts">查询</el-button>
          <el-button type="success" @click="showAddReceipt">新增收款</el-button>
        </div>

        <el-table :data="receiptList" border>
          <el-table-column prop="receiptNo" label="收款单号" width="150" />
          <el-table-column prop="receiptDate" label="收款日期" width="120" />
          <el-table-column prop="customerName" label="客户名称" />
          <el-table-column prop="sourceBillNo" label="应收单号" width="150" />
          <el-table-column label="收款金额" width="120">
            <template #default="{ row }">{{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="settleModeId" label="收款方式" width="100" />
          <el-table-column prop="documentNo" label="交易流水" width="150" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === '已确认' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status === '待确认'" size="small" text type="success" @click="confirmReceipt(row)">确认</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="receiptTotal > 0"
          :total="receiptTotal"
          v-model:current-page="receiptPage"
          :page-size="20"
          layout="total, prev, pager, next"
          @current-change="loadReceipts"
          style="margin-top: 10px"
        />
      </el-tab-pane>

      <!-- 付款记录 -->
      <el-tab-pane label="付款记录" name="vouchers">
        <div class="filter-bar">
          <el-input v-model="voucherFilters.paymentNo" placeholder="付款单号" class="filter-input" />
          <el-input v-model="voucherFilters.supplierName" placeholder="供应商名称" class="filter-input" />
          <el-select v-model="voucherFilters.status" placeholder="状态" clearable class="filter-input">
            <el-option label="待确认" value="待确认" />
            <el-option label="已确认" value="已确认" />
            <el-option label="已作废" value="已作废" />
          </el-select>
          <el-button type="primary" @click="loadVouchers">查询</el-button>
          <el-button type="success" @click="showAddVoucher">新增付款</el-button>
        </div>

        <el-table :data="voucherList" border>
          <el-table-column prop="paymentNo" label="付款单号" width="150" />
          <el-table-column prop="paymentDate" label="付款日期" width="120" />
          <el-table-column prop="supplierName" label="供应商名称" />
          <el-table-column prop="sourceBillNo" label="应付单号" width="150" />
          <el-table-column label="付款金额" width="120">
            <template #default="{ row }">{{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="settleModeId" label="付款方式" width="100" />
          <el-table-column prop="documentNo" label="交易流水" width="150" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === '已确认' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status === '待确认'" size="small" text type="success" @click="confirmVoucher(row)">确认</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="voucherTotal > 0"
          :total="voucherTotal"
          v-model:current-page="voucherPage"
          :page-size="20"
          layout="total, prev, pager, next"
          @current-change="loadVouchers"
          style="margin-top: 10px"
        />
      </el-tab-pane>

      <!-- 财务报表 -->
      <el-tab-pane label="财务报表" name="report">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>应收账款</template>
              <div class="summary-item">
                <span class="label">总应收:</span>
                <span class="value">{{ summary.totalAr?.toFixed(2) }}</span>
              </div>
              <div class="summary-item">
                <span class="label">已收:</span>
                <span class="value success">{{ summary.receivedAr?.toFixed(2) }}</span>
              </div>
              <div class="summary-item">
                <span class="label">应收余额:</span>
                <span class="value warning">{{ summary.balanceAr?.toFixed(2) }}</span>
              </div>
              <div class="summary-item">
                <span class="label">逾期数量:</span>
                <span class="value danger">{{ summary.overdueArCount }}</span>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>应付账款</template>
              <div class="summary-item">
                <span class="label">总应付:</span>
                <span class="value">{{ summary.totalAp?.toFixed(2) }}</span>
              </div>
              <div class="summary-item">
                <span class="label">已付:</span>
                <span class="value success">{{ summary.paidAp?.toFixed(2) }}</span>
              </div>
              <div class="summary-item">
                <span class="label">应付余额:</span>
                <span class="value warning">{{ summary.balanceAp?.toFixed(2) }}</span>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>资金状况</template>
              <div class="summary-item">
                <span class="label">净应收:</span>
                <span class="value" :class="{ danger: summary.netReceivable < 0 }">{{ summary.netReceivable?.toFixed(2) }}</span>
              </div>
              <p class="tip">净应收 = 应收账款余额 - 应付账款余额</p>
            </el-card>
          </el-col>
        </el-row>

        <el-divider />

        <h3>应收账款账龄分析</h3>
        <el-table :data="agingList" border>
          <el-table-column prop="bucket" label="账龄区间" />
          <el-table-column prop="count" label="单据数量" />
          <el-table-column label="金额">
            <template #default="{ row }">{{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 收款对话框 -->
    <el-dialog title="收款登记" v-model="showReceiveDialog" width="400px">
      <el-form :model="receiveForm" label-width="100px">
        <el-form-item label="应收单号">{{ receiveForm.arNo }}</el-form-item>
        <el-form-item label="客户名称">{{ receiveForm.customerName }}</el-form-item>
        <el-form-item label="应收余额">{{ receiveForm.balanceAmount?.toFixed(2) }}</el-form-item>
        <el-form-item label="收款金额" required>
          <el-input type="number" v-model="receiveForm.amount" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-select v-model="receiveForm.settleModeId">
            <el-option label="银行转账" :value="1" />
            <el-option label="现金" :value="2" />
            <el-option label="支票" :value="3" />
            <el-option label="汇票" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易流水号">
          <el-input v-model="receiveForm.documentNo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReceiveDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReceive">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 付款对话框 -->
    <el-dialog title="付款登记" v-model="showPaymentDialog" width="400px">
      <el-form :model="paymentForm" label-width="100px">
        <el-form-item label="应付单号">{{ paymentForm.apNo }}</el-form-item>
        <el-form-item label="供应商名称">{{ paymentForm.supplierName }}</el-form-item>
        <el-form-item label="应付余额">{{ paymentForm.balanceAmount?.toFixed(2) }}</el-form-item>
        <el-form-item label="付款金额" required>
          <el-input type="number" v-model="paymentForm.amount" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="paymentForm.settleModeId">
            <el-option label="银行转账" :value="1" />
            <el-option label="现金" :value="2" />
            <el-option label="支票" :value="3" />
            <el-option label="汇票" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易流水号">
          <el-input v-model="paymentForm.documentNo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPaymentDialog = false">取消</el-button>
        <el-button type="primary" @click="submitPayment">确认付款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('ar')

// AR filters
const arFilters = reactive({ arNo: '', customerName: '', status: '' })
const arList = ref([])
const arTotal = ref(0)
const arPage = ref(1)

// AP filters
const apFilters = reactive({ apNo: '', supplierName: '', status: '' })
const apList = ref([])
const apTotal = ref(0)
const apPage = ref(1)

// Receipt filters
const receiptFilters = reactive({ receiptNo: '', customerName: '', status: '' })
const receiptList = ref([])
const receiptTotal = ref(0)
const receiptPage = ref(1)

// Voucher filters
const voucherFilters = reactive({ paymentNo: '', supplierName: '', status: '' })
const voucherList = ref([])
const voucherTotal = ref(0)
const voucherPage = ref(1)

// Summary
const summary = ref({})
const agingList = ref([])

// Dialogs
const showReceiveDialog = ref(false)
const receiveForm = reactive({ arId: null, arNo: '', customerName: '', balanceAmount: 0, amount: 0, settleModeId: 1, documentNo: '' })

const showPaymentDialog = ref(false)
const paymentForm = reactive({ apId: null, apNo: '', supplierName: '', balanceAmount: 0, amount: 0, settleModeId: 1, documentNo: '' })

// Load AR
const loadAr = async () => {
  const res = await api.get('/finance/ar', { params: { current: arPage.value, size: 20, ...arFilters } })
  arList.value = res.data.records
  arTotal.value = res.data.total
}

// Load AP
const loadAp = async () => {
  const res = await api.get('/finance/ap', { params: { current: apPage.value, size: 20, ...apFilters } })
  apList.value = res.data.records
  apTotal.value = res.data.total
}

// Load Receipts
const loadReceipts = async () => {
  const res = await api.get('/finance/receipts', { params: { current: receiptPage.value, size: 20, ...receiptFilters } })
  receiptList.value = res.data.records
  receiptTotal.value = res.data.total
}

// Load Vouchers
const loadVouchers = async () => {
  const res = await api.get('/finance/vouchers', { params: { current: voucherPage.value, size: 20, ...voucherFilters } })
  voucherList.value = res.data.records
  voucherTotal.value = res.data.total
}

// Load Summary
const loadSummary = async () => {
  const res = await api.get('/finance/summary')
  summary.value = res.data
  const agingRes = await api.get('/finance/aging')
  agingList.value = agingRes.data
}

// Status types
const getArStatusType = (status) => {
  if (status === '已结清') return 'success'
  if (status === '部分收款') return 'warning'
  return 'info'
}

const getApStatusType = (status) => {
  if (status === '已结清') return 'success'
  if (status === '部分付款') return 'warning'
  return 'info'
}

// Receive payment
const receivePayment = (row) => {
  Object.assign(receiveForm, { arId: row.arId, arNo: row.arNo, customerName: row.customerName, balanceAmount: row.balanceAmount, amount: row.balanceAmount, settleModeId: 1, documentNo: '' })
  showReceiveDialog.value = true
}

const submitReceive = async () => {
  if (!receiveForm.amount || receiveForm.amount <= 0) {
    ElMessage.warning('请输入正确的收款金额')
    return
  }
  await api.post(`/finance/ar/${receiveForm.arId}/receive`, {
    amount: receiveForm.amount,
    settleModeId: receiveForm.settleModeId,
    documentNo: receiveForm.documentNo
  })
  ElMessage.success('收款成功')
  showReceiveDialog.value = false
  loadAr()
  loadSummary()
}

// Make payment
const makePayment = (row) => {
  Object.assign(paymentForm, { apId: row.apId, apNo: row.apNo, supplierName: row.supplierName, balanceAmount: row.balanceAmount, amount: row.balanceAmount, settleModeId: 1, documentNo: '' })
  showPaymentDialog.value = true
}

const submitPayment = async () => {
  if (!paymentForm.amount || paymentForm.amount <= 0) {
    ElMessage.warning('请输入正确的付款金额')
    return
  }
  await api.post(`/finance/ap/${paymentForm.apId}/pay`, {
    amount: paymentForm.amount,
    settleModeId: paymentForm.settleModeId,
    documentNo: paymentForm.documentNo
  })
  ElMessage.success('付款成功')
  showPaymentDialog.value = false
  loadAp()
  loadSummary()
}

// View detail
const viewArDetail = (row) => {
  ElMessageBox.alert(`应收单号: ${row.arNo}<br>客户: ${row.customerName}<br>应收金额: ${row.totalAmount}<br>已收金额: ${row.paidAmount}<br>余额: ${row.balanceAmount}<br>预计收款日期: ${row.dueDate || '未指定'}`, '应收单详情', { dangerouslyUseHTMLString: true })
}

const viewApDetail = (row) => {
  ElMessageBox.alert(`应付单号: ${row.apNo}<br>供应商: ${row.supplierName}<br>应付金额: ${row.totalAmount}<br>已付金额: ${row.paidAmount}<br>余额: ${row.balanceAmount}<br>预计付款日期: ${row.dueDate || '未指定'}`, '应付单详情', { dangerouslyUseHTMLString: true })
}

// Confirm receipt
const confirmReceipt = async (row) => {
  try {
    await ElMessageBox.confirm('确认此收款记录?', '提示')
    await api.post(`/finance/receipts/${row.receiptId}/confirm`)
    ElMessage.success('确认成功')
    loadReceipts()
  } catch (e) {}
}

// Confirm voucher
const confirmVoucher = async (row) => {
  try {
    await ElMessageBox.confirm('确认此付款记录?', '提示')
    await api.post(`/finance/vouchers/${row.paymentId}/confirm`)
    ElMessage.success('确认成功')
    loadVouchers()
  } catch (e) {}
}

// Show add dialogs
const showAddReceipt = () => {
  ElMessage.info('请从应收单详情页发起收款')
}

const showAddVoucher = () => {
  ElMessage.info('请从应付单详情页发起付款')
}

onMounted(() => {
  loadAr()
  loadAp()
  loadReceipts()
  loadVouchers()
  loadSummary()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
  flex-wrap: wrap;
}
.filter-input {
  width: 160px;
}
.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #eee;
}
.summary-item:last-child {
  border-bottom: none;
}
.summary-item .label {
  color: #666;
}
.summary-item .value {
  font-weight: bold;
  font-size: 16px;
}
.summary-item .value.success { color: #67C23A; }
.summary-item .value.warning { color: #E6A23C; }
.summary-item .value.danger { color: #F56C6C; }
.tip {
  color: #999;
  font-size: 12px;
  margin-top: 10px;
}
</style>
