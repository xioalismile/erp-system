<template>
  <div class="erp-page">
    <el-card>
      <!-- 搜索栏 -->
      <div class="erp-search-bar">
        <el-input v-model="search.keyword" placeholder="入库单号" clearable style="width:200px" />
        <el-select v-model="search.status" placeholder="状态" clearable style="width:120px">
          <el-option label="草稿" value="草稿" />
          <el-option label="已审核" value="已审核" />
          <el-option label="已关闭" value="已关闭" />
          <el-option label="已取消" value="已取消" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleAdd">新增</el-button>
      </div>
      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="receiptNo" label="入库单号" width="160" />
        <el-table-column prop="receiptDate" label="入库日期" width="120" />
        <el-table-column prop="productionOrderNo" label="生产订单号" width="140" />
        <el-table-column prop="warehouseId" label="仓库ID" width="100" />
        <el-table-column prop="totalQuantity" label="总数量" width="120" align="right" />
        <el-table-column prop="qualifiedQuantity" label="合格数量" width="120" align="right" />
        <el-table-column prop="rejectedQuantity" label="不合格数量" width="120" align="right" />
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="制单人" width="100" />
        <el-table-column prop="createdAt" label="制单时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="success" v-if="row.status==='草稿'" @click="handleApprove(row)">审核</el-button>
            <el-button size="small" type="warning" v-if="row.status==='已审核'" @click="handleClose(row)">关闭</el-button>
            <el-button size="small" type="danger" v-if="row.status==='草稿'" @click="handleCancel(row)">取消</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
        style="margin-top:16px;justify-content:flex-end;display:flex"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1300px" @close="resetForm">
      <el-form :model="form" label-width="110px" :rules="rules" ref="formRef">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="入库单号" prop="receiptNo">
              <el-input v-model="form.receiptNo" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="入库日期" prop="receiptDate">
              <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="仓库ID" prop="warehouseId">
              <el-input-number v-model="form.warehouseId" :controls="false" style="width:100%" placeholder="仓库ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="生产订单ID">
              <el-input-number v-model="form.productionOrderId" :controls="false" style="width:100%" placeholder="生产订单ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="生产订单号">
              <el-input v-model="form.productionOrderNo" placeholder="生产订单号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="部门ID">
              <el-input-number v-model="form.departmentId" :controls="false" style="width:100%" placeholder="部门ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="经手人ID">
              <el-input-number v-model="form.handlerId" :controls="false" style="width:100%" placeholder="经手人ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="币种ID">
              <el-input-number v-model="form.currencyId" :controls="false" style="width:100%" placeholder="币种ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="汇率">
              <el-input-number v-model="form.exchangeRate" :precision="6" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="总金额">
              <el-input-number v-model="form.totalAmount" :controls="false" :precision="2" disabled style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="总数量">
              <el-input-number v-model="form.totalQuantity" :controls="false" :precision="4" disabled style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="合格数量">
              <el-input-number v-model="form.qualifiedQuantity" :controls="false" :precision="4" disabled style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="不合格数量">
              <el-input-number v-model="form.rejectedQuantity" :controls="false" :precision="4" disabled style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <!-- 明细表格 -->
        <el-divider content-position="left">入库明细</el-divider>
        <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">添加行</el-button>
        <el-table :data="form.items" border size="small">
          <el-table-column type="index" label="序号" width="55" />
          <el-table-column label="物料ID" width="110">
            <template #default="{row}"><el-input-number v-model="row.materialId" :controls="false" size="small" /></template>
          </el-table-column>
          <el-table-column label="物料编码" width="120">
            <template #default="{row}"><el-input v-model="row.materialCode" size="small" /></template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="150">
            <template #default="{row}"><el-input v-model="row.materialName" size="small" /></template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="{row}"><el-input v-model="row.specification" size="small" /></template>
          </el-table-column>
          <el-table-column label="单位ID" width="90">
            <template #default="{row}"><el-input-number v-model="row.unitId" :controls="false" size="small" /></template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{row}"><el-input-number v-model="row.quantity" :controls="false" :precision="4" :min="0" size="small" @change="calcRow(row)" /></template>
          </el-table-column>
          <el-table-column label="合格数量" width="110">
            <template #default="{row}"><el-input-number v-model="row.qualifiedQuantity" :controls="false" :precision="4" :min="0" size="small" @change="calcSummary" /></template>
          </el-table-column>
          <el-table-column label="不合格数量" width="110">
            <template #default="{row}"><el-input-number v-model="row.rejectedQuantity" :controls="false" :precision="4" :min="0" size="small" @change="calcSummary" /></template>
          </el-table-column>
          <el-table-column label="单价" width="110">
            <template #default="{row}"><el-input-number v-model="row.unitPrice" :controls="false" :precision="4" :min="0" size="small" @change="calcRow(row)" /></template>
          </el-table-column>
          <el-table-column label="金额" width="110">
            <template #default="{row}"><span>{{ row.amount }}</span></template>
          </el-table-column>
          <el-table-column label="仓库ID" width="100">
            <template #default="{row}"><el-input-number v-model="row.stockId" :controls="false" size="small" /></template>
          </el-table-column>
          <el-table-column label="货位ID" width="100">
            <template #default="{row}"><el-input-number v-model="row.binId" :controls="false" size="small" /></template>
          </el-table-column>
          <el-table-column label="批次号" width="120">
            <template #default="{row}"><el-input v-model="row.batchNo" size="small" /></template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{$index}">
              <el-button size="small" type="danger" @click="form.items.splice($index,1); calcSummary()">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="text-align:right;margin-top:8px">
          <span>合计数量: <b>{{ form.totalQuantity }}</b> | 合格: <b>{{ form.qualifiedQuantity }}</b> | 不合格: <b>{{ form.rejectedQuantity }}</b> | 合计金额: <b>{{ form.totalAmount }}</b></span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProductionReceipt, getProductionReceipt, saveProductionReceipt, updateProductionReceipt,
  deleteProductionReceipt, approveProductionReceipt, closeProductionReceipt, cancelProductionReceipt
} from '@/api/production/productionReceipt'

const loading = ref(false)
const tableData = ref([])
const search = reactive({ keyword: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const form = reactive({
  receiptId: null, receiptNo: '', receiptDate: new Date().toISOString().slice(0, 10),
  warehouseId: null, productionOrderId: null, productionOrderNo: '',
  departmentId: null, handlerId: null, currencyId: null,
  exchangeRate: 1, totalAmount: 0, totalQuantity: 0, qualifiedQuantity: 0, rejectedQuantity: 0,
  remark: '', items: []
})
const rules = {
  receiptDate: [{ required: true, message: '请选择入库日期', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请输入仓库ID', trigger: 'blur' }]
}

function statusTagType(s) {
  return { '草稿': 'info', '已审核': 'success', '已关闭': 'warning', '已取消': 'danger' }[s] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listProductionReceipt({ current: pagination.current, size: pagination.size, ...search })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function handleSearch() { pagination.current = 1; fetchData() }
function handleReset() { search.keyword = ''; search.status = ''; pagination.current = 1; fetchData() }

function handleAdd() {
  Object.assign(form, {
    receiptId: null, receiptNo: '', receiptDate: new Date().toISOString().slice(0, 10),
    warehouseId: null, productionOrderId: null, productionOrderNo: '',
    departmentId: null, handlerId: null, currencyId: null,
    exchangeRate: 1, totalAmount: 0, totalQuantity: 0, qualifiedQuantity: 0, rejectedQuantity: 0,
    remark: '', items: []
  })
  dialogTitle.value = '新增生产入库单'
  dialogVisible.value = true
}

async function handleEdit(row) {
  const data = await getProductionReceipt(row.receiptId)
  Object.assign(form, data)
  if (!form.items) form.items = []
  calcSummary()
  dialogTitle.value = '编辑生产入库单'
  dialogVisible.value = true
}

function addItem() {
  form.items.push({
    materialId: null, materialCode: '', materialName: '', specification: '',
    unitId: null, quantity: 0, qualifiedQuantity: 0, rejectedQuantity: 0,
    unitPrice: 0, amount: 0, stockId: form.warehouseId, binId: null, batchNo: ''
  })
}

function calcRow(row) {
  row.amount = (Number(row.quantity || 0) * Number(row.unitPrice || 0)).toFixed(2)
  calcSummary()
}

function calcSummary() {
  form.totalAmount = form.items.reduce((sum, r) => sum + Number(r.amount || 0), 0).toFixed(2)
  form.totalQuantity = form.items.reduce((sum, r) => sum + Number(r.quantity || 0), 0).toFixed(4)
  form.qualifiedQuantity = form.items.reduce((sum, r) => sum + Number(r.qualifiedQuantity || 0), 0).toFixed(4)
  form.rejectedQuantity = form.items.reduce((sum, r) => sum + Number(r.rejectedQuantity || 0), 0).toFixed(4)
}

async function handleSave() {
  await formRef.value.validate()
  const payload = { ...form }
  if (form.receiptId) {
    await updateProductionReceipt(form.receiptId, payload)
  } else {
    await saveProductionReceipt(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchData()
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`确认审核生产入库单 ${row.receiptNo}? 审核生产入库单后将增加库存`, '提示', { type: 'warning' })
  await approveProductionReceipt(row.receiptId)
  ElMessage.success('审核成功')
  fetchData()
}

async function handleClose(row) {
  const { value } = await ElMessageBox.prompt('请输入关闭原因', '关闭', { type: 'warning' })
  await closeProductionReceipt(row.receiptId, value)
  ElMessage.success('关闭成功')
  fetchData()
}

async function handleCancel(row) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消', { type: 'warning' })
  await cancelProductionReceipt(row.receiptId, value)
  ElMessage.success('取消成功')
  fetchData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除生产入库单 ${row.receiptNo}?`, '提示', { type: 'warning' })
  await deleteProductionReceipt(row.receiptId)
  ElMessage.success('删除成功')
  fetchData()
}

function resetForm() {
  formRef.value && formRef.value.resetFields()
}

onMounted(fetchData)
</script>

<style scoped>
.erp-page { padding: 16px; }
.erp-search-bar { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
</style>
