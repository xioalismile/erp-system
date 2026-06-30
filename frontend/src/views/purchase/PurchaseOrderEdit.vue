<template>
  <div>
    <el-card style="margin-bottom:16px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div>
          <el-button text @click="$router.push('/purchase/orders')">◀ 返回列表</el-button>
          <span style="font-size:16px;font-weight:600;margin-left:10px;">采购订单</span>
          <el-tag style="margin-left:10px;" :type="statusType(form.status)">{{ form.status }}</el-tag>
          <el-tag v-if="form.sourceType==='SALE_ORDER'" style="margin-left:5px;" type="warning" size="small">来源: {{ form.sourceOrderNo }}</el-tag>
        </div>
        <div>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button v-if="form.status==='草稿'" type="success" @click="submitAudit">提交审核</el-button>
          <el-button v-if="form.status==='已确认'" type="warning" @click="doInbound">入库</el-button>
          <el-button v-if="form.status==='草稿'||form.status==='已确认'" type="danger" plain @click="cancelDialog=true">取消</el-button>
        </div>
      </div>
    </el-card>

    <el-card style="margin-bottom:16px;">
      <template #header>供应商信息</template>
      <el-form :model="form" label-width="100px" :inline="true">
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" filterable placeholder="选择供应商" style="width:220px" @change="onSupplierChange">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="收货地址" style="width:100%;"><el-input v-model="form.deliveryAddress" style="width:500px" /></el-form-item>
        <el-form-item label="采购员"><el-input v-model="form.buyer" /></el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="form.paymentMethod" style="width:150px">
            <el-option label="款到发货" value="款到发货" />
            <el-option label="月结30天" value="月结30天" />
            <el-option label="月结60天" value="月结60天" />
            <el-option label="货到付款" value="货到付款" />
          </el-select>
        </el-form-item>
        <el-form-item label="交货日期"><el-date-picker v-model="form.expectedDeliveryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-bottom:16px;">
      <template #header>
        <div style="display:flex;justify-content:space-between;">
          <span>采购明细</span>
          <el-button text type="primary" @click="addItem">+ 新增行</el-button>
        </div>
      </template>
      <el-table :data="form.items" border size="small" show-summary :summary-method="getSummary">
        <el-table-column type="index" label="行" width="45" />
        <el-table-column label="物料" width="200">
          <template #default="{ row }">
            <el-select v-model="row.materialId" filterable placeholder="选择物料" @change="(v) => onMaterialChange(row, v)">
              <el-option v-for="m in materials" :key="m.id" :label="m.materialName" :value="m.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="specification" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="50" />
        <el-table-column label="仓库" width="100"><template #default="{ row }"><el-input v-model="row.warehouse" size="small" placeholder="主仓库" /></template></el-table-column>
        <el-table-column label="数量" width="90"><template #default="{ row }"><el-input-number v-model="row.quantity" :controls="false" :min="0" @change="calcRow(row)" /></template></el-table-column>
        <el-table-column label="单价" width="90"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :controls="false" :precision="4" @change="calcRow(row)" /></template></el-table-column>
        <el-table-column prop="amount" label="金额" width="100" />
        <el-table-column label="操作" width="70"><template #default="{ $index }"><el-button text type="danger" @click="form.items.splice($index, 1)">删</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header>汇总</template>
      <div style="text-align:right;font-size:14px;line-height:2;">
        <div>订单总额: <strong>{{ form.totalAmount }}</strong></div>
        <div>税额: <strong>{{ form.taxAmount }}</strong></div>
        <div style="font-size:18px;color:#3b82f6;">价税合计: <strong>{{ form.totalWithTax }}</strong></div>
      </div>
      <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" style="margin-top:10px;" />
    </el-card>

    <el-dialog v-model="inboundDialogVisible" title="采购入库" width="400px">
      <el-form label-width="80px"><el-form-item label="入库仓库"><el-input v-model="inboundWarehouse" placeholder="主仓库" /></el-form-item></el-form>
      <el-alert type="success" :closable="false" style="margin:10px 0">入库将自动增加库存</el-alert>
      <template #footer><el-button @click="inboundDialogVisible=false">取消</el-button><el-button type="primary" @click="confirmInbound">确认入库</el-button></template>
    </el-dialog>

    <el-dialog v-model="cancelDialog" title="取消采购单" width="400px">
      <el-input v-model="cancelReason" type="textarea" :rows="2" placeholder="取消原因" />
      <template #footer><el-button @click="cancelDialog=false">返回</el-button><el-button type="danger" @click="doCancel">确认取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../../api'

const route = useRoute()
const router = useRouter()
const suppliers = ref([])
const materials = ref([])
const inboundDialogVisible = ref(false)
const inboundWarehouse = ref('主仓库')
const cancelDialog = ref(false)
const cancelReason = ref('')

const form = ref({
  orderDate: new Date().toISOString().slice(0,10),
  supplierId: null, supplierName:'', contactPerson:'', contactPhone:'', deliveryAddress:'',
  buyer:'', paymentMethod:'月结30天', expectedDeliveryDate:'',
  totalAmount:0, taxAmount:0, totalWithTax:0,
  status:'草稿', inboundStatus:'未入库', sourceType:'MANUAL', remark:'', items:[]
})

const statusType = (s) => ({'草稿':'info','已确认':'','已入库':'warning','已完成':'success','已取消':'danger'}[s]||'info')

const onSupplierChange = (id) => {
  const s = suppliers.value.find(x => x.id === id)
  if (s) { form.value.supplierName=s.supplierName; form.value.supplierCode=s.supplierCode; form.value.contactPerson=s.contactPerson; form.value.contactPhone=s.contactPhone; form.value.paymentMethod=s.paymentMethod; form.value.expectedDeliveryDate=new Date(Date.now()+((s.leadTimeDays||7)*86400000)).toISOString().slice(0,10) }
}

const onMaterialChange = (row, id) => {
  const m = materials.value.find(x => x.id === id)
  if (m) { row.materialCode=m.materialCode; row.materialName=m.materialName; row.specification=m.specification; row.unit=m.unit; row.unitPrice=m.purchasePrice; row.taxRate=13; row.warehouse=m.defaultWarehouse||'主仓库'; calcRow(row) }
}

const calcRow = (row) => {
  row.amount = (row.unitPrice * row.quantity).toFixed(2)
  row.taxAmount = (row.amount * (row.taxRate||13) / 100).toFixed(2)
  row.totalAmount = (Number(row.amount) + Number(row.taxAmount)).toFixed(2)
  calcTotal()
}

const calcTotal = () => {
  let total=0, tax=0
  form.value.items.forEach(i => { total += Number(i.amount)||0; tax += Number(i.taxAmount)||0 })
  form.value.totalAmount = total.toFixed(2)
  form.value.taxAmount = tax.toFixed(2)
  form.value.totalWithTax = (total + tax).toFixed(2)
}

const getSummary = () => {
  const qty = form.value.items.reduce((s,i) => s + (Number(i.quantity)||0), 0)
  const amt = form.value.items.reduce((s,i) => s + (Number(i.amount)||0), 0)
  return ['合计','','','',qty,'','',amt.toFixed(2),'']
}

const addItem = () => { form.value.items.push({ quantity:1, unitPrice:0, taxRate:13, warehouse:'主仓库' }) }

const save = async () => {
  calcTotal()
  try {
    if (route.params.id) { await api.put(`/purchase/orders/${route.params.id}`, form.value) }
    else { await api.post('/purchase/orders', form.value) }
    ElMessage.success('保存成功'); router.push('/purchase/orders')
  } catch(e) {}
}

const submitAudit = async () => { try { await api.post(`/purchase/orders/${route.params.id}/submit`); ElMessage.success('已提交'); load() } catch(e) {} }
const doInbound = () => { inboundDialogVisible.value = true }
const confirmInbound = async () => { try { await api.post(`/purchase/orders/${route.params.id}/inbound`, { warehouse: inboundWarehouse.value }); ElMessage.success('入库成功'); inboundDialogVisible.value=false; load() } catch(e) {} }
const doCancel = async () => { try { await api.post(`/purchase/orders/${route.params.id}/cancel`, { reason: cancelReason.value }); ElMessage.success('已取消'); cancelDialog.value=false; load() } catch(e) {} }

const load = async () => {
  if (route.params.id) { const res = await api.get(`/purchase/orders/${route.params.id}`); if (res.code===200) form.value = res.data }
}

onMounted(async () => {
  const [supRes, matRes] = await Promise.all([api.get('/purchase/suppliers'), api.get('/sale/materials')])
  if (supRes.code===200) suppliers.value = supRes.data
  if (matRes.code===200) materials.value = matRes.data
  load()
})
</script>
