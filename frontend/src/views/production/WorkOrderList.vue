<template>
  <div class="erp-page">
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="工单号/产品编码/名称" class="filter-input" clearable @keyup.enter="loadData" />
      <el-select v-model="query.status" placeholder="状态" clearable class="filter-select">
        <el-option label="草稿" value="草稿" />
        <el-option label="已下达" value="已下达" />
        <el-option label="已完工" value="已完工" />
        <el-option label="已关闭" value="已关闭" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="success" @click="onAdd">新增工单</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading">
      <el-table-column prop="orderNo" label="工单号" min-width="140" />
      <el-table-column prop="materialCode" label="产品编码" min-width="110" />
      <el-table-column prop="materialName" label="产品名称" min-width="160" />
      <el-table-column prop="quantity" label="计划数量" width="110" align="right" />
      <el-table-column prop="completedQuantity" label="完工数量" width="110" align="right" />
      <el-table-column prop="rejectedQuantity" label="报废数量" width="100" align="right" />
      <el-table-column prop="planStartDate" label="计划开始" width="120" />
      <el-table-column prop="planEndDate" label="计划完工" width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="onDetail(row)">详情</el-button>
          <el-button v-if="row.status === '草稿'" size="small" @click="onEdit(row)">编辑</el-button>
          <el-button v-if="row.status === '草稿'" size="small" type="success" @click="onRelease(row)">下达</el-button>
          <el-button v-if="row.status === '已下达'" size="small" type="primary" @click="onComplete(row)">完工</el-button>
          <el-button v-if="row.status === '已完工'" size="small" type="warning" @click="onClose(row)">关闭</el-button>
        </template>
      </el-table-column>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="isEdit ? '编辑工单' : '新增工单'" v-model="dialogVisible" width="780px" @closed="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单号">
              <el-input v-model="form.orderNo" placeholder="保存时自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单日期">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="产品" prop="materialId">
              <el-select v-model="form.materialId" filterable placeholder="选择产品" style="width: 100%" @change="onMaterialChange">
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialName} (${m.materialCode})`" :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="BOM">
              <el-select v-model="form.bomId" filterable placeholder="选择BOM(可选)" style="width: 100%">
                <el-option v-for="b in bomOptions" :key="b.bomId" :label="`${b.bomCode} - ${b.bomName || b.materialName}`" :value="b.bomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺路线">
              <el-select v-model="form.routingId" filterable placeholder="选择工艺路线(可选)" style="width: 100%">
                <el-option v-for="r in routingOptions" :key="r.routingId" :label="`${r.routingCode} - ${r.routingName}`" :value="r.routingId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划完工">
              <el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="仓库">
              <el-select v-model="form.warehouseId" filterable placeholder="选择仓库" style="width: 100%">
                <el-option v-for="w in warehouseOptions" :key="w.stockId" :label="w.stockName" :value="w.stockId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" style="width: 100%">
                <el-option label="普通" value="普通" />
                <el-option label="加急" value="加急" />
                <el-option label="紧急" value="紧急" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog title="工单详情" v-model="detailVisible" width="860px">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="工单日期">{{ detail.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)" size="small">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ detail.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detail.materialName }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ detail.quantity }}</el-descriptions-item>
        <el-descriptions-item label="完工数量">{{ detail.completedQuantity }}</el-descriptions-item>
        <el-descriptions-item label="报废数量">{{ detail.rejectedQuantity }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ detail.planStartDate }}</el-descriptions-item>
        <el-descriptions-item label="计划完工">{{ detail.planEndDate }}</el-descriptions-item>
        <el-descriptions-item label="实际完工">{{ detail.actualEndDate }}</el-descriptions-item>
      </el-descriptions>
      <h4 v-if="detail.materials && detail.materials.length" style="margin: 16px 0 8px;">用料明细</h4>
      <el-table v-if="detail.materials && detail.materials.length" :data="detail.materials" border size="small">
        <el-table-column prop="lineNo" label="行" width="60" />
        <el-table-column prop="materialCode" label="物料编码" min-width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="specification" label="规格" min-width="120" />
        <el-table-column prop="requiredQuantity" label="需求数量" width="110" align="right" />
        <el-table-column prop="issuedQuantity" label="已领料" width="100" align="right" />
      </el-table>
    </el-dialog>

    <!-- 关闭工单弹窗 -->
    <el-dialog title="关闭工单" v-model="closeVisible" width="420px">
      <el-form label-width="80px">
        <el-form-item label="关闭原因">
          <el-input v-model="closeReason" type="textarea" :rows="3" placeholder="请输入关闭原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="warning" :loading="acting" @click="doClose">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageWorkOrder, getWorkOrder, createWorkOrder, updateWorkOrder,
  releaseWorkOrder, completeWorkOrder, closeWorkOrder
} from '@/api/production/workOrder'
import { listAllMaterial } from '@/api/basedata/material'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import request from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const acting = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, keyword: '', status: '' })

const materialOptions = ref([])
const warehouseOptions = ref([])
const bomOptions = ref([])
const routingOptions = ref([])

const dialogVisible = ref(false)
const detailVisible = ref(false)
const closeVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const closeReason = ref('')

const form = reactive({
  orderId: null, orderNo: '', orderDate: new Date().toISOString().slice(0, 10),
  bomId: null, materialId: null, materialCode: '', materialName: '',
  unitId: null, quantity: 1, warehouseId: null, routingId: null,
  priority: '普通', planStartDate: '', planEndDate: '',
  sourceType: 'MANUAL', remark: ''
})

const detail = reactive({})

const rules = {
  materialId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}

const statusType = (s) => ({
  '草稿': 'info', '已下达': 'warning', '已完工': 'success', '已关闭': ''
}[s] || 'info')

async function loadData() {
  loading.value = true
  try {
    const res = await pageWorkOrder({
      current: query.current, size: query.size,
      keyword: query.keyword, status: query.status
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function onSearch() { query.current = 1; loadData() }
function onReset() { query.keyword = ''; query.status = ''; query.current = 1; loadData() }

async function loadOptions() {
  try {
    const [mats, whs] = await Promise.all([
      listAllMaterial(), listAllWarehouse()
    ])
    materialOptions.value = mats || []
    warehouseOptions.value = whs || []
    // BOM 列表和工艺路线列表
    const [boms, routings] = await Promise.all([
      request.get('/masterdata/bom/list'),
      request.get('/production/routings/list')
    ])
    bomOptions.value = boms || []
    routingOptions.value = routings || []
  } catch (e) { /* 忽略选项加载失败 */ }
}

function onAdd() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function onEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    orderId: row.orderId, orderNo: row.orderNo, orderDate: row.orderDate,
    bomId: row.bomId, materialId: row.materialId, materialCode: row.materialCode,
    materialName: row.materialName, unitId: row.unitId, quantity: row.quantity,
    warehouseId: row.warehouseId, routingId: row.routingId, priority: row.priority || '普通',
    planStartDate: row.planStartDate, planEndDate: row.planEndDate,
    sourceType: row.sourceType || 'MANUAL', remark: row.remark || ''
  })
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    orderId: null, orderNo: '', orderDate: new Date().toISOString().slice(0, 10),
    bomId: null, materialId: null, materialCode: '', materialName: '',
    unitId: null, quantity: 1, warehouseId: null, routingId: null,
    priority: '普通', planStartDate: '', planEndDate: '',
    sourceType: 'MANUAL', remark: ''
  })
}

function onMaterialChange(materialId) {
  const m = materialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    form.materialCode = m.materialCode
    form.materialName = m.materialName
    form.unitId = m.baseUnitId
  }
}

async function onSave() {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateWorkOrder(form.orderId, form)
      } else {
        await createWorkOrder(form)
      }
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      dialogVisible.value = false
      loadData()
    } catch (e) { /* 拦截器已提示 */ } finally {
      saving.value = false
    }
  })
}

async function onDetail(row) {
  try {
    const res = await getWorkOrder(row.orderId)
    Object.assign(detail, res)
    detailVisible.value = true
  } catch (e) { /* 拦截器已提示 */ }
}

async function onRelease(row) {
  await ElMessageBox.confirm(`确认下达工单 ${row.orderNo}？下达后不可修改`, '确认', { type: 'warning' })
  acting.value = true
  try {
    await releaseWorkOrder(row.orderId)
    ElMessage.success('工单已下达')
    loadData()
  } catch (e) { /* 拦截器已提示 */ } finally {
    acting.value = false
  }
}

async function onComplete(row) {
  await ElMessageBox.confirm(`确认完工工单 ${row.orderNo}？`, '确认', { type: 'warning' })
  acting.value = true
  try {
    await completeWorkOrder(row.orderId)
    ElMessage.success('工单已完工')
    loadData()
  } catch (e) { /* 拦截器已提示 */ } finally {
    acting.value = false
  }
}

function onClose(row) {
  closeReason.value = ''
  closeVisible.value = true
  detail.orderId = row.orderId
  detail.orderNo = row.orderNo
}

async function doClose() {
  acting.value = true
  try {
    await closeWorkOrder(detail.orderId, closeReason.value)
    ElMessage.success('工单已关闭')
    closeVisible.value = false
    loadData()
  } catch (e) { /* 拦截器已提示 */ } finally {
    acting.value = false
  }
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped lang="scss">
.filter-bar { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
.filter-input { width: 240px; }
.filter-select { width: 140px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
