<template>
  <div class="app-container">
    <div class="filter-bar">
      <el-input v-model="workOrderNo" placeholder="工单号" class="filter-input" @keyup.enter="loadData" />
      <el-input v-model="productCode" placeholder="产品编码" class="filter-input" @keyup.enter="loadData" />
      <el-select v-model="status" placeholder="状态">
        <el-option label="全部" value="" />
        <el-option label="待开工" value="待开工" />
        <el-option label="已开工" value="已开工" />
        <el-option label="已完工" value="已完工" />
        <el-option label="已关闭" value="已关闭" />
        <el-option label="已取消" value="已取消" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="showAddModal = true">新增工单</el-button>
      <el-button type="warning" @click="openMrpModal">MRP计算</el-button>
    </div>

    <el-table :data="tableData" border>
      <el-table-column prop="workOrderNo" label="工单号" />
      <el-table-column prop="productCode" label="产品编码" />
      <el-table-column prop="productName" label="产品名称" />
      <el-table-column prop="specification" label="规格" />
      <el-table-column prop="plannedQuantity" label="计划数量" />
      <el-table-column prop="completedQuantity" label="完工数量" />
      <el-table-column prop="scrapQuantity" label="报废数量" />
      <el-table-column prop="workshop" label="车间" />
      <el-table-column prop="plannedStartDate" label="计划开始" />
      <el-table-column prop="plannedFinishDate" label="计划完工" />
      <el-table-column prop="status" label="状态">
        <template #default="scope">
          <el-tag :type="getStatusTagType(scope.row.status)">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row)">详情</el-button>
          <el-button size="small" @click="editRow(scope.row)">编辑</el-button>
          <el-button v-if="scope.row.status === '待开工'" size="small" type="success" @click="startWork(scope.row)">开工</el-button>
          <el-button v-if="scope.row.status === '已开工'" size="small" type="primary" @click="openReportModal(scope.row)">报工</el-button>
          <el-button v-if="scope.row.status === '已开工'" size="small" type="success" @click="finishWork(scope.row)">完工</el-button>
          <el-button v-if="scope.row.status === '已完工'" size="small" type="warning" @click="closeWork(scope.row)">关闭</el-button>
          <el-button v-if="scope.row.status !== '已完工' && scope.row.status !== '已关闭'" size="small" type="danger" @click="cancelWork(scope.row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="工单详情" :visible.sync="showDetailModal" width="800px">
      <div v-if="detailData">
        <div class="detail-header">
          <span class="work-order-no">{{ detailData.workOrderNo }}</span>
          <el-tag :type="getStatusTagType(detailData.status)" class="status-tag">{{ detailData.status }}</el-tag>
        </div>
        <el-divider />
        <el-row>
          <el-col :span="8">产品: {{ detailData.productCode }} - {{ detailData.productName }}</el-col>
          <el-col :span="8">规格: {{ detailData.specification }}</el-col>
          <el-col :span="8">单位: {{ detailData.unit }}</el-col>
        </el-row>
        <el-row>
          <el-col :span="8">计划数量: {{ detailData.plannedQuantity }}</el-col>
          <el-col :span="8">完工数量: {{ detailData.completedQuantity }}</el-col>
          <el-col :span="8">报废数量: {{ detailData.scrapQuantity }}</el-col>
        </el-row>
        <el-row>
          <el-col :span="8">车间: {{ detailData.workshop }}</el-col>
          <el-col :span="8">计划开始: {{ detailData.plannedStartDate }}</el-col>
          <el-col :span="8">计划完工: {{ detailData.plannedFinishDate }}</el-col>
        </el-row>
        <el-divider />
        <h4>用料明细</h4>
        <el-table :data="detailData.materials" border>
          <el-table-column prop="lineNo" label="行号" width="60" />
          <el-table-column prop="materialCode" label="物料编码" />
          <el-table-column prop="materialName" label="物料名称" />
          <el-table-column prop="specification" label="规格" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="requiredQuantity" label="需求数量" />
          <el-table-column prop="issuedQuantity" label="已领数量" />
          <el-table-column prop="warehouse" label="仓库" />
          <el-table-column label="操作">
            <template #default="scope">
              <el-button v-if="detailData.status === '待开工' || detailData.status === '已开工'" size="mini" @click="issueMaterial(scope.row)">领料</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div slot="footer">
        <el-button @click="showDetailModal = false">关闭</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="isEdit ? '编辑工单' : '新增工单'" :visible.sync="showAddModal">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="formData.productCode" />
        </el-form-item>
        <el-form-item label="计划数量" prop="plannedQuantity">
          <el-input type="number" v-model="formData.plannedQuantity" />
        </el-form-item>
        <el-form-item label="车间" prop="workshop">
          <el-input v-model="formData.workshop" />
        </el-form-item>
        <el-form-item label="计划开始日期" prop="plannedStartDate">
          <el-date-picker v-model="formData.plannedStartDate" type="date" />
        </el-form-item>
        <el-form-item label="计划完工日期" prop="plannedFinishDate">
          <el-date-picker v-model="formData.plannedFinishDate" type="date" />
        </el-form-item>
        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="formData.sourceType">
            <el-option label="手动创建" value="MANUAL" />
            <el-option label="销售驱动" value="SALE_ORDER" />
            <el-option label="MRP生成" value="MRP" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input type="textarea" v-model="formData.remark" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showAddModal = false">取消</el-button>
        <el-button type="primary" @click="saveData">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="报工" :visible.sync="showReportModal">
      <el-form :model="reportData" label-width="100px">
        <el-form-item label="完工数量" prop="completedQty">
          <el-input type="number" v-model="reportData.completedQty" />
        </el-form-item>
        <el-form-item label="报废数量" prop="scrapQty">
          <el-input type="number" v-model="reportData.scrapQty" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showReportModal = false">取消</el-button>
        <el-button type="primary" @click="submitReport">确认报工</el-button>
      </div>
    </el-dialog>

    <el-dialog title="MRP计算" :visible.sync="showMrpModal" width="900px">
      <el-tabs>
        <el-tab-pane label="单产品计算">
          <el-form :model="mrpData" label-width="100px">
            <el-form-item label="产品编码" prop="materialCode">
              <el-input v-model="mrpData.materialCode" />
            </el-form-item>
            <el-form-item label="需求数量" prop="quantity">
              <el-input type="number" v-model="mrpData.quantity" />
            </el-form-item>
            <el-form-item label="需求日期" prop="demandDate">
              <el-date-picker v-model="mrpData.demandDate" type="date" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="批量计算">
          <div class="batch-toolbar">
            <el-button size="small" @click="addMrpDemand">+ 添加产品</el-button>
          </div>
          <el-table :data="mrpBatchData" border size="small" max-height="200">
            <el-table-column label="产品编码" width="180">
              <template #default="scope">
                <el-input v-model="scope.row.materialCode" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="需求数量" width="120">
              <template #default="scope">
                <el-input type="number" v-model="scope.row.quantity" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="需求日期" width="160">
              <template #default="scope">
                <el-date-picker v-model="scope.row.demandDate" type="date" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="scope">
                <el-button size="small" type="danger" @click="removeMrpDemand(scope.$index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <div v-if="mrpResult && mrpResult.length > 0">
        <el-divider />
        <h4>物料需求汇总</h4>
        <el-table :data="aggregatedRequirements" border>
          <el-table-column prop="materialCode" label="物料编码" />
          <el-table-column prop="materialName" label="物料名称" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column prop="grossRequirement" label="毛需求" />
          <el-table-column prop="onHandQuantity" label="现有库存" />
          <el-table-column prop="netRequirement" label="净需求" />
        </el-table>
        <h4>采购建议</h4>
        <el-table :data="aggregatedPurchases" border>
          <el-table-column prop="materialCode" label="物料编码" />
          <el-table-column prop="materialName" label="物料名称" />
          <el-table-column prop="quantity" label="数量" />
          <el-table-column prop="suggestedOrderDate" label="建议下单" />
        </el-table>
        <h4>生产建议</h4>
        <el-table :data="aggregatedProductions" border>
          <el-table-column prop="materialCode" label="产品编码" />
          <el-table-column prop="materialName" label="产品名称" />
          <el-table-column prop="quantity" label="数量" />
          <el-table-column prop="plannedStartDate" label="计划开始" />
          <el-table-column prop="plannedFinishDate" label="计划完工" />
        </el-table>
        <div class="action-buttons">
          <el-button type="success" @click="generateWorkOrdersFromMrp">生成工单</el-button>
          <el-button type="warning" @click="generatePurchaseFromMrp" :disabled="aggregatedPurchases.length === 0">生成采购订单</el-button>
        </div>
      </div>
      <div slot="footer">
        <el-button @click="showMrpModal = false">取消</el-button>
        <el-button type="primary" @click="runMrp">执行MRP计算</el-button>
      </div>
    </el-dialog>

    <el-dialog title="领料" :visible.sync="showIssueModal">
      <el-form :model="issueData" label-width="100px">
        <el-form-item label="物料" :label-width="120px">
          <span>{{ issueData.materialCode }} - {{ issueData.materialName }}</span>
        </el-form-item>
        <el-form-item label="需求数量" :label-width="120px">
          <span>{{ issueData.requiredQuantity }}</span>
        </el-form-item>
        <el-form-item label="已领数量" :label-width="120px">
          <span>{{ issueData.issuedQuantity }}</span>
        </el-form-item>
        <el-form-item label="本次领料数量" prop="quantity">
          <el-input type="number" v-model="issueData.quantity" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showIssueModal = false">取消</el-button>
        <el-button type="primary" @click="submitIssue">确认领料</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { api } from '@/api'
import { ElMessage } from 'element-plus'

const workOrderNo = ref('')
const productCode = ref('')
const status = ref('')
const tableData = ref([])

const showDetailModal = ref(false)
const showAddModal = ref(false)
const showReportModal = ref(false)
const showMrpModal = ref(false)
const showIssueModal = ref(false)
const isEdit = ref(false)

const detailData = reactive({})
const formData = reactive({
  id: null,
  productCode: '',
  plannedQuantity: 0,
  workshop: '主车间',
  plannedStartDate: '',
  plannedFinishDate: '',
  sourceType: 'MANUAL',
  remark: ''
})
const reportData = reactive({
  workOrderId: null,
  completedQty: 0,
  scrapQty: 0
})
const mrpData = reactive({
  materialCode: '',
  quantity: 1,
  demandDate: ''
})
const mrpBatchData = ref([])
const mrpResult = ref([])
const aggregatedRequirements = computed(() => {
  const map = new Map()
  for (const result of mrpResult.value) {
    if (result.requirements) {
      for (const req of result.requirements) {
        if (map.has(req.materialCode)) {
          const existing = map.get(req.materialCode)
          existing.grossRequirement += req.grossRequirement
          existing.netRequirement += req.netRequirement
        } else {
          map.set(req.materialCode, { ...req })
        }
      }
    }
  }
  return Array.from(map.values())
})
const aggregatedPurchases = computed(() => {
  const map = new Map()
  for (const result of mrpResult.value) {
    if (result.purchaseSuggestions) {
      for (const sug of result.purchaseSuggestions) {
        if (map.has(sug.materialCode)) {
          const existing = map.get(sug.materialCode)
          existing.quantity += sug.quantity
        } else {
          map.set(sug.materialCode, { ...sug })
        }
      }
    }
  }
  return Array.from(map.values())
})
const aggregatedProductions = computed(() => {
  const map = new Map()
  for (const result of mrpResult.value) {
    if (result.productionSuggestions) {
      for (const sug of result.productionSuggestions) {
        if (map.has(sug.materialCode)) {
          const existing = map.get(sug.materialCode)
          existing.quantity += sug.quantity
        } else {
          map.set(sug.materialCode, { ...sug })
        }
      }
    }
  }
  return Array.from(map.values())
})
const issueData = reactive({
  workOrderId: null,
  materialId: null,
  materialCode: '',
  materialName: '',
  requiredQuantity: 0,
  issuedQuantity: 0,
  quantity: 0
})

const loadData = async () => {
  const res = await api.get('/api/production/workorders', {
    params: { workOrderNo: workOrderNo.value, productCode: productCode.value, status: status.value }
  })
  tableData.value = res.data.records
}

const getStatusTagType = (status) => {
  const types = { '待开工': 'info', '已开工': 'warning', '已完工': 'success', '已关闭': 'primary', '已取消': 'danger' }
  return types[status] || 'info'
}

const viewDetail = async (row) => {
  const res = await api.get(`/api/production/workorders/${row.id}`)
  Object.assign(detailData, res.data)
  showDetailModal.value = true
}

const editRow = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  showAddModal.value = true
}

const saveData = async () => {
  if (isEdit.value) {
    await api.put(`/api/production/workorders/${formData.id}`, formData)
  } else {
    await api.post('/api/production/workorders', formData)
  }
  showAddModal.value = false
  loadData()
}

const startWork = async (row) => {
  if (await confirm(`确定开工工单 ${row.workOrderNo} 吗？`)) {
    await api.post(`/api/production/workorders/${row.id}/start`)
    loadData()
  }
}

const openReportModal = (row) => {
  reportData.workOrderId = row.id
  reportData.completedQty = 0
  reportData.scrapQty = 0
  showReportModal.value = true
}

const submitReport = async () => {
  await api.post(`/api/production/workorders/${reportData.workOrderId}/report`, reportData)
  showReportModal.value = false
  loadData()
}

const finishWork = async (row) => {
  if (await confirm(`确定完工工单 ${row.workOrderNo} 吗？`)) {
    await api.post(`/api/production/workorders/${row.id}/finish`)
    loadData()
  }
}

const closeWork = async (row) => {
  if (await confirm(`确定关闭工单 ${row.workOrderNo} 吗？`)) {
    await api.post(`/api/production/workorders/${row.id}/close`)
    loadData()
  }
}

const cancelWork = async (row) => {
  const reason = prompt('请输入取消原因')
  if (reason) {
    await api.post(`/api/production/workorders/${row.id}/cancel`, { reason })
    loadData()
  }
}

const issueMaterial = (material) => {
  issueData.workOrderId = detailData.id
  issueData.materialId = material.id
  issueData.materialCode = material.materialCode
  issueData.materialName = material.materialName
  issueData.requiredQuantity = material.requiredQuantity
  issueData.issuedQuantity = material.issuedQuantity
  issueData.quantity = issueData.requiredQuantity - issueData.issuedQuantity
  showIssueModal.value = true
}

const submitIssue = async () => {
  await api.post(`/api/production/workorders/${issueData.workOrderId}/issue`, {
    materialId: issueData.materialId,
    quantity: issueData.quantity
  })
  showIssueModal.value = false
  viewDetail({ id: issueData.workOrderId })
}

const addMrpDemand = () => {
  mrpBatchData.value.push({
    materialCode: '',
    quantity: 1,
    demandDate: new Date().toISOString().split('T')[0]
  })
}

const removeMrpDemand = (index) => {
  mrpBatchData.value.splice(index, 1)
}

const runMrp = async () => {
  if (mrpBatchData.value.length > 0) {
    const demands = mrpBatchData.value.map(d => ({
      materialCode: d.materialCode,
      quantity: d.quantity,
      demandDate: d.demandDate
    }))
    const res = await api.post('/api/production/mrp/calculate-batch', { demands })
    mrpResult.value = res.data
    ElMessage.success('MRP计算完成')
  } else {
    const res = await api.post('/api/production/mrp/calculate', mrpData)
    mrpResult.value = [res.data]
  }
}

const generateWorkOrdersFromMrp = async () => {
  const suggestions = aggregatedProductions.value
  if (suggestions.length === 0) {
    ElMessage.warning('没有生产建议')
    return
  }
  await api.post('/api/production/mrp/generate-workorders', {
    suggestions: suggestions,
    demandDate: mrpData.demandDate || new Date().toISOString().split('T')[0]
  })
  ElMessage.success('工单生成成功')
  showMrpModal.value = false
  loadData()
}

const generatePurchaseFromMrp = async () => {
  const purchases = aggregatedPurchases.value
  if (purchases.length === 0) {
    ElMessage.warning('没有采购建议')
    return
  }
  try {
    const res = await api.post('/api/purchase/orders/generate-from-mrp', {
      suggestions: purchases
    })
    if (res.data && res.data.length > 0) {
      ElMessage.success(`已生成 ${res.data.length} 个采购订单`)
      showMrpModal.value = false
    } else {
      ElMessage.info('没有生成采购订单')
    }
  } catch (e) {
    ElMessage.error('生成采购订单失败')
  }
}

const initMrpModal = () => {
  mrpResult.value = []
  mrpBatchData.value = []
  mrpData.materialCode = ''
  mrpData.quantity = 1
  mrpData.demandDate = ''
}

loadData()

const openMrpModal = () => {
  initMrpModal()
  showMrpModal.value = true
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}
.filter-input {
  width: 200px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.work-order-no {
  font-size: 18px;
  font-weight: bold;
}
.status-tag {
  font-size: 14px;
}
.batch-toolbar {
  margin-bottom: 10px;
}
.action-buttons {
  margin-top: 15px;
  display: flex;
  gap: 10px;
}
</style>