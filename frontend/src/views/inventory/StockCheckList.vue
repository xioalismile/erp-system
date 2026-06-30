<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <div class="filter-bar">
      <el-input v-model="search.keyword" placeholder="盘点单号" style="width:200px" clearable @clear="load" />
      <el-select v-model="search.status" placeholder="状态" clearable style="width:140px">
        <el-option label="草稿" value="草稿" />
        <el-option label="已审核" value="已审核" />
        <el-option label="已关闭" value="已关闭" />
        <el-option label="已取消" value="已取消" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openCreate">新增盘点单</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="list" stripe border v-loading="loading">
      <el-table-column prop="checkNo" label="盘点单号" width="160" />
      <el-table-column prop="checkDate" label="盘点日期" width="120" />
      <el-table-column prop="warehouseId" label="仓库ID" width="90" />
      <el-table-column prop="stockType" label="盘点类型" width="110" />
      <el-table-column prop="bookAmount" label="账面金额" width="120" align="right" />
      <el-table-column prop="checkAmount" label="盘点金额" width="120" align="right" />
      <el-table-column prop="profitLossAmount" label="盈亏金额" width="120" align="right">
        <template #default="{ row }">
          <span :style="{ color: row.profitLossAmount > 0 ? '#67C23A' : row.profitLossAmount < 0 ? '#F56C6C' : '' }">
            {{ row.profitLossAmount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" :disabled="row.status !== '草稿'" @click="handleApprove(row)">审核</el-button>
          <el-button size="small" @click="handleClose(row)" :disabled="row.status !== '已审核'">关闭</el-button>
          <el-button size="small" type="info" @click="handleCancel(row)" :disabled="row.status !== '草稿'">取消</el-button>
          <el-button size="small" type="danger" :disabled="row.status !== '草稿'" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      style="margin-top:16px;justify-content:flex-end"
      :current-page="page.current"
      :page-size="page.size"
      :total="page.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="onCurrentChange"
      @size-change="onSizeChange"
    />

    <!-- 新增/编辑/查看 弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="1100px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px" :disabled="readonly">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="盘点单号">
              <el-input v-model="form.checkNo" placeholder="保存时自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="盘点日期">
              <el-date-picker v-model="form.checkDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库ID">
              <el-input-number v-model="form.warehouseId" :min="1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="盘点类型">
              <el-select v-model="form.stockType" style="width:100%">
                <el-option label="动态盘点" value="动态盘点" />
                <el-option label="全月盘点" value="全月盘点" />
                <el-option label="全年盘点" value="全年盘点" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="经办人ID">
              <el-input-number v-model="form.handlerId" :min="1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币别ID">
              <el-input-number v-model="form.currencyId" :min="1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="汇率">
              <el-input-number v-model="form.exchangeRate" :min="0" :precision="6" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="账面金额">
              <el-input v-model="form.bookAmount" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="盘点金额">
              <el-input v-model="form.checkAmount" readonly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="盈亏金额">
              <el-input v-model="form.profitLossAmount" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="1" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细表格 -->
        <div class="item-bar">
          <span class="item-title">盘点明细</span>
          <el-button v-if="!readonly" size="small" type="primary" @click="addItem">添加行</el-button>
        </div>
        <el-table :data="form.items" border size="small" max-height="320">
          <el-table-column label="序号" type="index" width="55" />
          <el-table-column label="物料ID" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.materialId" :min="1" :controls="false" size="small" :disabled="readonly" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="物料编码" width="130">
            <template #default="{ row }">
              <el-input v-model="row.materialCode" size="small" :disabled="readonly" />
            </template>
          </el-table-column>
          <el-table-column label="物料名称" width="160">
            <template #default="{ row }">
              <el-input v-model="row.materialName" size="small" :disabled="readonly" />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="140">
            <template #default="{ row }">
              <el-input v-model="row.specification" size="small" :disabled="readonly" />
            </template>
          </el-table-column>
          <el-table-column label="单位ID" width="80">
            <template #default="{ row }">
              <el-input-number v-model="row.unitId" :min="1" :controls="false" size="small" :disabled="readonly" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="库存ID" width="80">
            <template #default="{ row }">
              <el-input-number v-model="row.stockId" :min="1" :controls="false" size="small" :disabled="readonly" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="库位ID" width="80">
            <template #default="{ row }">
              <el-input-number v-model="row.binId" :min="1" :controls="false" size="small" :disabled="readonly" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="批号" width="120">
            <template #default="{ row }">
              <el-input v-model="row.batchNo" size="small" :disabled="readonly" />
            </template>
          </el-table-column>
          <el-table-column label="账面数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.bookQuantity" :min="0" :precision="4" :controls="false" size="small" :disabled="readonly" style="width:100%" @change="calcItem(row)" />
            </template>
          </el-table-column>
          <el-table-column label="盘点数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.checkQuantity" :min="0" :precision="4" :controls="false" size="small" :disabled="readonly" style="width:100%" @change="calcItem(row)" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false" size="small" :disabled="readonly" style="width:100%" @change="calcItem(row)" />
            </template>
          </el-table-column>
          <el-table-column label="盘盈数量" width="100" align="right">
            <template #default="{ row }">{{ row.profitQuantity }}</template>
          </el-table-column>
          <el-table-column label="盘亏数量" width="100" align="right">
            <template #default="{ row }">{{ row.lossQuantity }}</template>
          </el-table-column>
          <el-table-column label="盘点结果" width="90">
            <template #default="{ row }">
              <el-tag :type="resultTagType(row.checkResult)" size="small">{{ row.checkResult }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="原因" width="140">
            <template #default="{ row }">
              <el-input v-model="row.reason" size="small" :disabled="readonly" />
            </template>
          </el-table-column>
          <el-table-column v-if="!readonly" label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button size="small" type="danger" link @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listStockCheck, getStockCheck, saveStockCheck, updateStockCheck,
  deleteStockCheck, approveStockCheck, closeStockCheck, cancelStockCheck
} from '@/api/inventory/stockCheck'

const loading = ref(false)
const list = ref([])
const search = reactive({ keyword: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const dialogVisible = ref(false)
const readonly = ref(false)
const isEdit = ref(false)
const form = reactive({
  checkId: null,
  checkNo: '',
  checkDate: null,
  warehouseId: null,
  stockType: '动态盘点',
  handlerId: null,
  currencyId: null,
  exchangeRate: 1,
  bookAmount: 0,
  checkAmount: 0,
  profitLossAmount: 0,
  remark: '',
  items: []
})

const dialogTitle = ref('新增盘点单')

const statusTagType = (s) => ({
  '草稿': 'info',
  '已审核': 'success',
  '已关闭': 'warning',
  '已取消': 'danger'
}[s] || 'info')

const resultTagType = (r) => ({
  '盘平': 'info',
  '盘盈': 'success',
  '盘亏': 'danger'
}[r] || 'info')

const newItem = () => ({
  materialId: null, materialCode: '', materialName: '', specification: '',
  unitId: null, stockId: null, binId: null, batchNo: '',
  bookQuantity: 0, bookAmount: 0,
  checkQuantity: 0, checkAmount: 0,
  profitQuantity: 0, profitAmount: 0,
  lossQuantity: 0, lossAmount: 0,
  unitPrice: 0, checkResult: '盘平', reason: ''
})

const round2 = (n) => Number(Number(n || 0).toFixed(2))

/** 明细行盈亏计算 */
const calcItem = (row) => {
  const bookQty = Number(row.bookQuantity) || 0
  const checkQty = Number(row.checkQuantity) || 0
  const unitPrice = Number(row.unitPrice) || 0
  const diff = Number((checkQty - bookQty).toFixed(4))
  row.bookAmount = round2(bookQty * unitPrice)
  row.checkAmount = round2(checkQty * unitPrice)
  if (diff > 0) {
    row.profitQuantity = diff
    row.profitAmount = round2(diff * unitPrice)
    row.lossQuantity = 0
    row.lossAmount = 0
    row.checkResult = '盘盈'
  } else if (diff < 0) {
    row.lossQuantity = -diff
    row.lossAmount = round2(-diff * unitPrice)
    row.profitQuantity = 0
    row.profitAmount = 0
    row.checkResult = '盘亏'
  } else {
    row.profitQuantity = 0
    row.profitAmount = 0
    row.lossQuantity = 0
    row.lossAmount = 0
    row.checkResult = '盘平'
  }
  calcMaster()
}

/** 主单汇总 */
const calcMaster = () => {
  let book = 0, check = 0
  form.items.forEach(it => {
    book += Number(it.bookAmount) || 0
    check += Number(it.checkAmount) || 0
  })
  form.bookAmount = round2(book)
  form.checkAmount = round2(check)
  form.profitLossAmount = round2(check - book)
}

const addItem = () => {
  form.items.push(newItem())
}

const removeItem = (idx) => {
  form.items.splice(idx, 1)
  calcMaster()
}

const resetForm = () => {
  Object.assign(form, {
    checkId: null,
    checkNo: '',
    checkDate: null,
    warehouseId: null,
    stockType: '动态盘点',
    handlerId: null,
    currencyId: null,
    exchangeRate: 1,
    bookAmount: 0,
    checkAmount: 0,
    profitLossAmount: 0,
    remark: '',
    items: []
  })
}

const openCreate = () => {
  resetForm()
  isEdit.value = false
  readonly.value = false
  dialogTitle.value = '新增盘点单'
  dialogVisible.value = true
}

const openEdit = async (row) => {
  resetForm()
  isEdit.value = true
  readonly.value = false
  dialogTitle.value = '编辑盘点单'
  const data = await getStockCheck(row.checkId)
  Object.assign(form, data)
  form.items = (data.items || []).map(it => ({ ...it }))
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!form.items || form.items.length === 0) {
    ElMessage.warning('请至少添加一行盘点明细')
    return
  }
  try {
    if (isEdit.value) {
      await updateStockCheck(form.checkId, form)
      ElMessage.success('更新成功')
    } else {
      await saveStockCheck(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    // 错误已由拦截器提示
  }
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(
      '审核盘点单后将执行：盘盈物料入库 + 盘亏物料出库',
      '审核确认',
      { confirmButtonText: '确定审核', cancelButtonText: '取消', type: 'warning' }
    )
    const approver = localStorage.getItem('username') || ''
    await approveStockCheck(row.checkId, approver)
    ElMessage.success('审核成功')
    load()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

const handleClose = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭原因', '关闭盘点单', {
      confirmButtonText: '确定', cancelButtonText: '取消', inputType: 'textarea'
    })
    await closeStockCheck(row.checkId, value || '')
    ElMessage.success('关闭成功')
    load()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

const handleCancel = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消盘点单', {
      confirmButtonText: '确定', cancelButtonText: '取消', inputType: 'textarea'
    })
    await cancelStockCheck(row.checkId, value || '')
    ElMessage.success('取消成功')
    load()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除盘点单 ${row.checkNo} 吗?`, '提示', { type: 'warning' })
    await deleteStockCheck(row.checkId)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器提示
    }
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await listStockCheck({
      current: page.current,
      size: page.size,
      keyword: search.keyword,
      status: search.status
    })
    list.value = res.records || []
    page.total = res.total || 0
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

const onCurrentChange = (v) => { page.current = v; load() }
const onSizeChange = (v) => { page.size = v; page.current = 1; load() }

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}
.item-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0;
}
.item-title {
  font-weight: bold;
  font-size: 14px;
}
</style>
