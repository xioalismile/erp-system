<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <div class="filter-bar">
      <el-input v-model="filters.voucherNo" placeholder="凭证号" class="filter-input" @keyup.enter="loadList" />
      <el-select v-model="filters.voucherType" placeholder="凭证类型" clearable class="filter-input">
        <el-option label="记账凭证" value="记账凭证" />
        <el-option label="收款凭证" value="收款凭证" />
        <el-option label="付款凭证" value="付款凭证" />
        <el-option label="转账凭证" value="转账凭证" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable class="filter-input">
        <el-option label="待记账" value="待记账" />
        <el-option label="已记账" value="已记账" />
      </el-select>
      <el-input-number v-model="filters.periodYear" :min="2000" :max="2100" placeholder="年度" class="filter-input" />
      <el-input-number v-model="filters.periodMonth" :min="1" :max="12" placeholder="月份" class="filter-input" />
      <el-button type="primary" @click="loadList">查询</el-button>
      <el-button type="success" @click="showAdd">新增凭证</el-button>
    </div>

    <!-- 凭证列表 -->
    <el-table :data="list" border>
      <el-table-column prop="voucherNo" label="凭证号" width="160" />
      <el-table-column prop="voucherDate" label="日期" width="120" />
      <el-table-column prop="voucherType" label="类型" width="120" />
      <el-table-column label="借方合计" width="130">
        <template #default="{ row }">{{ formatMoney(row.totalDebit) }}</template>
      </el-table-column>
      <el-table-column label="贷方合计" width="130">
        <template #default="{ row }">{{ formatMoney(row.totalCredit) }}</template>
      </el-table-column>
      <el-table-column label="期间" width="100">
        <template #default="{ row }">{{ row.periodYear }}-{{ String(row.periodMonth).padStart(2, '0') }}</template>
      </el-table-column>
      <el-table-column prop="makerName" label="制单人" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getApprovalType(row.approvalStatus)">{{ row.approvalStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="info" @click="viewDetail(row)">详情</el-button>
          <el-button v-if="row.approvalStatus === '待审核'" size="small" text type="primary" @click="showEdit(row)">编辑</el-button>
          <el-button v-if="row.approvalStatus === '待审核'" size="small" text type="warning" @click="audit(row)">审核</el-button>
          <el-button v-if="row.status === '待记账' && row.approvalStatus === '已审核'" size="small" text type="success" @click="post(row)">记账</el-button>
          <el-button v-if="row.approvalStatus === '待审核'" size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      :total="total"
      v-model:current-page="page"
      :page-size="20"
      layout="total, prev, pager, next"
      @current-change="loadList"
      style="margin-top: 10px"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.voucherId ? '编辑凭证' : '新增凭证'" v-model="showDialog" width="1100px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="凭证日期" required>
              <el-date-picker v-model="form.voucherDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="onDateChange" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="凭证类型" required>
              <el-select v-model="form.voucherType" style="width: 100%">
                <el-option label="记账凭证" value="记账凭证" />
                <el-option label="收款凭证" value="收款凭证" />
                <el-option label="付款凭证" value="付款凭证" />
                <el-option label="转账凭证" value="转账凭证" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="会计期间">
              <el-input :model-value="`${form.periodYear || ''}-${String(form.periodMonth || '').padStart(2, '0')}`" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="制单人">
              <el-input v-model="form.makerName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="1" />
        </el-form-item>

        <!-- 分录表格 -->
        <div class="entries-header">
          <span class="entries-title">凭证分录</span>
          <el-button size="small" type="primary" @click="addEntry">+ 增加行</el-button>
        </div>
        <el-table :data="form.entries" border size="small">
          <el-table-column label="序号" width="60" type="index" />
          <el-table-column label="科目编码" width="140">
            <template #default="{ row }">
              <el-input v-model="row.accountCode" size="small" placeholder="科目编码" @change="onAccountChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="科目名称" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.accountName" size="small" placeholder="科目名称" />
            </template>
          </el-table-column>
          <el-table-column label="方向" width="80">
            <template #default="{ row }">
              <el-select v-model="row.accountDirection" size="small" style="width: 100%">
                <el-option label="借" value="借" />
                <el-option label="贷" value="贷" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="借方金额" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.debitAmount" :controls="false" :precision="2" :min="0" size="small" style="width: 100%"
                @change="onDebitChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="贷方金额" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.creditAmount" :controls="false" :precision="2" :min="0" size="small" style="width: 100%"
                @change="onCreditChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="摘要" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.summary" size="small" placeholder="摘要" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button size="small" text type="danger" @click="removeEntry($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 合计行 -->
        <div class="entries-summary">
          <div class="summary-item">
            <span>借方合计:</span>
            <span class="amount" :class="{ 'unbalanced': !isBalanced }">{{ formatMoney(totalDebit) }}</span>
          </div>
          <div class="summary-item">
            <span>贷方合计:</span>
            <span class="amount" :class="{ 'unbalanced': !isBalanced }">{{ formatMoney(totalCredit) }}</span>
          </div>
          <div class="summary-item">
            <span>差额:</span>
            <span class="amount" :class="{ 'unbalanced': !isBalanced }">{{ formatMoney(diffAmount) }}</span>
          </div>
          <div v-if="!isBalanced" class="unbalanced-tip">借贷不平衡，请检查！</div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!isBalanced" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="凭证详情" v-model="showDetailDialog" width="1000px">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="凭证号">{{ detail.voucherNo }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ detail.voucherDate }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.voucherType }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ detail.periodYear }}-{{ String(detail.periodMonth).padStart(2, '0') }}</el-descriptions-item>
          <el-descriptions-item label="制单人">{{ detail.makerName }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ detail.approvedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">{{ detail.approvalStatus }}</el-descriptions-item>
          <el-descriptions-item label="记账人">{{ detail.posterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="detail.entries || []" border size="small" style="margin-top: 15px">
          <el-table-column label="序号" width="60" type="index" />
          <el-table-column prop="accountCode" label="科目编码" width="140" />
          <el-table-column prop="accountName" label="科目名称" min-width="180" />
          <el-table-column prop="accountDirection" label="方向" width="80" />
          <el-table-column label="借方金额" width="140">
            <template #default="{ row }">{{ formatMoney(row.debitAmount) }}</template>
          </el-table-column>
          <el-table-column label="贷方金额" width="140">
            <template #default="{ row }">{{ formatMoney(row.creditAmount) }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="160" />
        </el-table>

        <div class="entries-summary">
          <div class="summary-item">
            <span>借方合计:</span>
            <span class="amount">{{ formatMoney(detail.totalDebit) }}</span>
          </div>
          <div class="summary-item">
            <span>贷方合计:</span>
            <span class="amount">{{ formatMoney(detail.totalCredit) }}</span>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageVoucher, getVoucher, createVoucher, updateVoucher, deleteVoucher,
  auditVoucher, postVoucher
} from '@/api/finance/voucher'

// 列表
const filters = reactive({ voucherNo: '', voucherType: '', status: '', periodYear: null, periodMonth: null })
const list = ref([])
const total = ref(0)
const page = ref(1)

const loadList = async () => {
  const params = { current: page.value, size: 20, ...filters }
  // 清理空值
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === null) delete params[k]
  })
  const res = await pageVoucher(params)
  list.value = res.records
  total.value = res.total
}

// 状态颜色
const getStatusType = (status) => {
  if (status === '已记账') return 'success'
  return 'info'
}
const getApprovalType = (status) => {
  if (status === '已审核') return 'success'
  if (status === '已拒绝') return 'danger'
  return 'warning'
}

const formatMoney = (val) => {
  const num = Number(val || 0)
  return num.toFixed(2)
}

// 新增/编辑
const showDialog = ref(false)
const form = reactive({
  voucherId: null,
  voucherNo: '',
  voucherDate: new Date().toISOString().slice(0, 10),
  voucherType: '记账凭证',
  periodYear: new Date().getFullYear(),
  periodMonth: new Date().getMonth() + 1,
  makerName: '',
  remark: '',
  entries: []
})

const todayStr = () => new Date().toISOString().slice(0, 10)

const onDateChange = (val) => {
  if (val) {
    const d = new Date(val)
    form.periodYear = d.getFullYear()
    form.periodMonth = d.getMonth() + 1
  }
}

const showAdd = () => {
  Object.assign(form, {
    voucherId: null,
    voucherNo: '',
    voucherDate: todayStr(),
    voucherType: '记账凭证',
    periodYear: new Date().getFullYear(),
    periodMonth: new Date().getMonth() + 1,
    makerName: localStorage.getItem('realName') || localStorage.getItem('username') || '',
    remark: '',
    entries: [createEmptyEntry()]
  })
  showDialog.value = true
}

const showEdit = async (row) => {
  const data = await getVoucher(row.voucherId)
  Object.assign(form, {
    voucherId: data.voucherId,
    voucherNo: data.voucherNo,
    voucherDate: data.voucherDate,
    voucherType: data.voucherType,
    periodYear: data.periodYear,
    periodMonth: data.periodMonth,
    makerName: data.makerName,
    remark: data.remark,
    entries: (data.entries || []).map(e => ({ ...e, debitAmount: Number(e.debitAmount || 0), creditAmount: Number(e.creditAmount || 0) }))
  })
  showDialog.value = true
}

const createEmptyEntry = () => ({
  accountCode: '',
  accountName: '',
  accountDirection: '借',
  debitAmount: 0,
  creditAmount: 0,
  summary: ''
})

const addEntry = () => {
  form.entries.push(createEmptyEntry())
}

const removeEntry = (idx) => {
  form.entries.splice(idx, 1)
}

const onAccountChange = (row) => {
  // 占位：可在此处通过科目编码查询科目信息并回填名称、方向
}

const onDebitChange = (row) => {
  if (Number(row.debitAmount || 0) > 0) {
    row.creditAmount = 0
    row.accountDirection = '借'
  }
}

const onCreditChange = (row) => {
  if (Number(row.creditAmount || 0) > 0) {
    row.debitAmount = 0
    row.accountDirection = '贷'
  }
}

// 合计计算
const totalDebit = computed(() => form.entries.reduce((s, e) => s + Number(e.debitAmount || 0), 0))
const totalCredit = computed(() => form.entries.reduce((s, e) => s + Number(e.creditAmount || 0), 0))
const diffAmount = computed(() => Math.abs(Number(totalDebit.value) - Number(totalCredit.value)))
const isBalanced = computed(() => Number(totalDebit.value) > 0 && Number(totalDebit.value) === Number(totalCredit.value))

const submit = async () => {
  if (!form.voucherDate) { ElMessage.warning('请选择凭证日期'); return }
  if (!form.entries || form.entries.length === 0) { ElMessage.warning('请至少添加一行分录'); return }
  if (!isBalanced.value) { ElMessage.warning('借贷不平衡'); return }
  // 校验科目编码
  for (const e of form.entries) {
    if (!e.accountCode || !e.accountName) {
      ElMessage.warning('请填写完整的科目编码和名称')
      return
    }
  }
  const payload = {
    voucherId: form.voucherId,
    voucherNo: form.voucherNo,
    voucherDate: form.voucherDate,
    voucherType: form.voucherType,
    periodYear: form.periodYear,
    periodMonth: form.periodMonth,
    makerName: form.makerName,
    remark: form.remark,
    entries: form.entries
  }
  if (form.voucherId) {
    await updateVoucher(form.voucherId, payload)
    ElMessage.success('更新成功')
  } else {
    await createVoucher(payload)
    ElMessage.success('创建成功')
  }
  showDialog.value = false
  loadList()
}

// 详情
const showDetailDialog = ref(false)
const detail = ref(null)
const viewDetail = async (row) => {
  detail.value = await getVoucher(row.voucherId)
  showDetailDialog.value = true
}

// 审核
const audit = async (row) => {
  try {
    await ElMessageBox.confirm(`确认审核凭证 ${row.voucherNo}?`, '提示')
    await auditVoucher(row.voucherId)
    ElMessage.success('审核成功')
    loadList()
  } catch (e) {}
}

// 记账
const post = async (row) => {
  try {
    await ElMessageBox.confirm(`确认记账凭证 ${row.voucherNo}?`, '提示')
    await postVoucher(row.voucherId)
    ElMessage.success('记账成功')
    loadList()
  } catch (e) {}
}

// 删除
const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除凭证 ${row.voucherNo}?`, '提示', { type: 'warning' })
    await deleteVoucher(row.voucherId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {}
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input {
  width: 160px;
}
.entries-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 15px 0 8px;
}
.entries-title {
  font-weight: bold;
  font-size: 14px;
}
.entries-summary {
  display: flex;
  gap: 30px;
  padding: 12px 0 0;
  margin-top: 10px;
  border-top: 1px dashed #ddd;
  align-items: center;
}
.summary-item {
  display: flex;
  gap: 8px;
  align-items: center;
}
.summary-item .amount {
  font-weight: bold;
  font-size: 15px;
  color: #303133;
}
.summary-item .amount.unbalanced {
  color: #F56C6C;
}
.unbalanced-tip {
  color: #F56C6C;
  font-size: 13px;
  margin-left: auto;
}
</style>
