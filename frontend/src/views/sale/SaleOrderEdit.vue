<template>
  <div>
    <!-- 顶部工具栏 -->
    <el-card style="margin-bottom:16px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div>
          <el-button text @click="$router.push('/sale/orders')">◀ 返回列表</el-button>
          <span style="font-size:16px;font-weight:600;margin-left:10px;">销售订单</span>
          <el-tag style="margin-left:10px;" :type="statusTagType(form.status)">{{ form.status }}</el-tag>
          <el-tag v-if="form.invoiceStatus" style="margin-left:5px;" type="warning" size="small">{{ form.invoiceStatus }}</el-tag>
          <el-tag v-if="form.paymentStatus" style="margin-left:5px;" type="success" size="small">{{ form.paymentStatus }}</el-tag>
        </div>
        <div>
          <el-button v-if="form.id" @click="copyOrder">复制订单</el-button>
          <el-button v-if="form.id && form.items && form.items.length > 0" type="info" plain @click="convertDialog=true">转采购</el-button>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button v-if="form.status==='草稿'" type="success" @click="submitAudit">提交审核</el-button>
          <el-button v-if="form.status==='已确认'" type="warning" @click="shipDialog=true">发货</el-button>
          <el-button v-if="form.status==='已发货'" @click="invoiceDialog=true">开票</el-button>
          <el-button v-if="form.status==='已开票'" type="success" @click="completeOrder">完成</el-button>
          <el-button v-if="form.status==='草稿'||form.status==='已确认'" type="danger" plain @click="cancelDialog=true">取消</el-button>
          <el-button v-if="form.status==='已发货'||form.status==='已完成'" type="danger" plain @click="returnDialog=true">退货</el-button>
        </div>
      </div>
    </el-card>

    <!-- 客户信息 -->
    <el-card style="margin-bottom:16px;">
      <template #header>客户信息</template>
      <el-form :model="form" label-width="100px" :inline="true">
        <el-form-item label="客户">
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width:220px" @change="onCustomerChange">
            <el-option v-for="c in customers" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="收货地址" style="width:100%;"><el-input v-model="form.deliveryAddress" style="width:500px" /></el-form-item>
        <el-form-item label="发货方式">
          <el-select v-model="form.deliveryMethod" style="width:120px">
            <el-option label="快递" value="快递" />
            <el-option label="物流" value="物流" />
            <el-option label="自提" value="自提" />
            <el-option label="客户自取" value="客户自取" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号"><el-input v-model="form.logisticsNo" style="width:180px" /></el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="form.paymentMethod" style="width:150px">
            <el-option label="款到发货" value="款到发货" />
            <el-option label="月结30天" value="月结30天" />
            <el-option label="月结60天" value="月结60天" />
            <el-option label="预付款" value="预付款" />
          </el-select>
        </el-form-item>
        <el-form-item label="交货日期"><el-date-picker v-model="form.expectedDeliveryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="币别">
          <el-select v-model="form.currency" style="width:80px">
            <el-option label="CNY" value="CNY" />
            <el-option label="USD" value="USD" />
          </el-select>
        </el-form-item>
        <el-form-item label="汇率"><el-input-number v-model="form.exchangeRate" :precision="6" :step="0.1" /></el-form-item>
      </el-form>
    </el-card>

    <!-- 商品明细 -->
    <el-card style="margin-bottom:16px;">
      <template #header>
        <div style="display:flex;justify-content:space-between;">
          <span>商品明细</span>
          <div>
            <el-button text type="primary" @click="addItem">+ 新增行</el-button>
          </div>
        </div>
      </template>
      <el-table :data="form.items" border size="small" :summary-method="getSummary" show-summary>
        <el-table-column type="index" label="行" width="45" />
        <el-table-column label="物料" width="180">
          <template #default="{ row }">
            <el-select v-model="row.materialId" filterable placeholder="选择物料" @change="(v) => onMaterialChange(row, v)">
              <el-option v-for="m in materials" :key="m.id" :label="m.materialName" :value="m.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="specification" label="规格" width="100" />
        <el-table-column prop="unit" label="单位" width="50" />
        <el-table-column label="仓库" width="100">
          <template #default="{ row }">
            <el-input v-model="row.warehouse" size="small" placeholder="主仓库" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="90"><template #default="{ row }"><el-input-number v-model="row.quantity" :controls="false" :min="0" @change="calcRow(row)" /></template></el-table-column>
        <el-table-column label="单价" width="90"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :controls="false" :precision="4" @change="calcRow(row)" /></template></el-table-column>
        <el-table-column label="折扣%" width="70"><template #default="{ row }"><el-input-number v-model="row.discountRate" :controls="false" :precision="2" @change="calcRow(row)" /></template></el-table-column>
        <el-table-column prop="amount" label="金额" width="100" />
        <el-table-column label="赠品" width="50"><template #default="{ row }"><el-checkbox v-model="row.isGift" :true-label="1" :false-label="0" /></template></el-table-column>
        <el-table-column label="操作" width="70"><template #default="{ $index }"><el-button text type="danger" @click="form.items.splice($index, 1)">删</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <!-- 费用与汇总 -->
    <el-card style="margin-bottom:16px;">
      <template #header>费用与汇总</template>
      <el-form :model="form" label-width="100px" :inline="true">
        <el-form-item label="运费"><el-input-number v-model="form.freight" :precision="2" :controls="false" @change="calcTotal" /></el-form-item>
        <el-form-item label="整单优惠"><el-input-number v-model="form.discountAmount" :precision="2" :controls="false" @change="calcTotal" /></el-form-item>
        <el-form-item label="备注" style="width:100%;"><el-input v-model="form.remark" type="textarea" :rows="1" style="width:400px" /></el-form-item>
      </el-form>
      <div style="text-align:right;font-size:14px;line-height:2;border-top:1px solid #eee;padding-top:10px;">
        <div>订单总额: <strong>{{ form.totalAmount }}</strong></div>
        <div>运费: <strong>{{ form.freight || 0 }}</strong> &nbsp; 整单优惠: <strong>-{{ form.discountAmount || 0 }}</strong></div>
        <div>税额: <strong>{{ form.taxAmount }}</strong></div>
        <div style="font-size:18px;color:#3b82f6;">价税合计: <strong>{{ form.totalWithTax }}</strong></div>
        <div v-if="form.receivedAmount > 0" style="color:#10b981;">已收: {{ form.receivedAmount }} &nbsp; 待收: {{ (Number(form.totalWithTax||0) - Number(form.receivedAmount||0)).toFixed(2) }}</div>
      </div>
    </el-card>

    <!-- 转采购弹窗 -->
    <el-dialog v-model="convertDialog" title="销售转采购" width="600px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px">选择需要转采购的明细，系统将自动生成采购订单</el-alert>
      <el-table :data="form.items" border size="small" @selection-change="convertSelection = $event">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="materialName" label="物料" />
        <el-table-column prop="quantity" label="订单数量" width="100" />
        <el-table-column label="采购数量" width="120">
          <template #default="{ row }"><el-input-number v-model="row.purchaseQty" :controls="false" :min="0" size="small" :placeholder="String(row.quantity)" /></template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" />
      </el-table>
      <template #footer>
        <el-button @click="convertDialog=false">取消</el-button>
        <el-button type="primary" @click="doConvert">生成采购单</el-button>
      </template>
    </el-dialog>

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipDialog" title="发货确认" width="400px">
      <el-form label-width="80px">
        <el-form-item label="发货方式"><el-select v-model="shipForm.deliveryMethod"><el-option label="快递" value="快递" /><el-option label="物流" value="物流" /><el-option label="自提" value="自提" /></el-select></el-form-item>
        <el-form-item label="物流单号"><el-input v-model="shipForm.logisticsNo" /></el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" style="margin:10px 0">发货将自动扣减库存，请确认库存充足</el-alert>
      <template #footer><el-button @click="shipDialog=false">取消</el-button><el-button type="primary" @click="doShip">确认发货</el-button></template>
    </el-dialog>

    <!-- 开票弹窗 -->
    <el-dialog v-model="invoiceDialog" title="开票" width="400px">
      <el-form label-width="80px"><el-form-item label="发票号"><el-input v-model="invoiceNo" /></el-form-item></el-form>
      <template #footer><el-button @click="invoiceDialog=false">取消</el-button><el-button type="primary" @click="doInvoice">确认开票</el-button></template>
    </el-dialog>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" title="取消订单" width="400px">
      <el-input v-model="cancelReason" type="textarea" :rows="2" placeholder="取消原因" />
      <template #footer><el-button @click="cancelDialog=false">返回</el-button><el-button type="danger" @click="doCancel">确认取消</el-button></template>
    </el-dialog>

    <!-- 退货弹窗 -->
    <el-dialog v-model="returnDialog" title="退货" width="400px">
      <el-input v-model="returnReason" type="textarea" :rows="2" placeholder="退货原因" />
      <el-alert type="warning" :closable="false" style="margin:10px 0">退货将自动回退库存</el-alert>
      <template #footer><el-button @click="returnDialog=false">返回</el-button><el-button type="danger" @click="doReturn">确认退货</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'

const route = useRoute()
const router = useRouter()
const customers = ref([])
const materials = ref([])
const convertDialog = ref(false)
const convertSelection = ref([])

const form = ref({
  orderDate: new Date().toISOString().slice(0,10),
  customerId: null, customerName:'', contactPerson:'', contactPhone:'', deliveryAddress:'',
  deliveryMethod:'快递', logisticsNo:'', salesperson:'', department:'',
  currency:'CNY', exchangeRate:1.0, paymentMethod:'月结30天', expectedDeliveryDate:'',
  freight:0, discountAmount:0, totalAmount:0, taxAmount:0, totalWithTax:0,
  status:'草稿', invoiceStatus:'未开票', paymentStatus:'未收款', receivedAmount:0,
  remark:'', items:[]
})

const shipDialog = ref(false)
const shipForm = ref({ deliveryMethod:'快递', logisticsNo:'' })
const invoiceDialog = ref(false)
const invoiceNo = ref('')
const cancelDialog = ref(false)
const cancelReason = ref('')
const returnDialog = ref(false)
const returnReason = ref('')

const statusTagType = (s) => ({'草稿':'info','已确认':'','已发货':'warning','已开票':'success','已完成':'success','已取消':'danger','已退货':'danger'}[s]||'info')

const onCustomerChange = (id) => {
  const c = customers.value.find(x => x.id === id)
  if (c) { form.value.customerName=c.customerName; form.value.customerCode=c.customerCode; form.value.contactPerson=c.contactPerson; form.value.contactPhone=c.contactPhone; form.value.deliveryAddress=c.address; form.value.paymentMethod=c.paymentMethod }
}

const onMaterialChange = (row, id) => {
  const m = materials.value.find(x => x.id === id)
  if (m) { row.materialCode=m.materialCode; row.materialName=m.materialName; row.specification=m.specification; row.unit=m.unit; row.unitPrice=m.salePrice; row.taxRate=13; row.discountRate=0; row.warehouse=m.defaultWarehouse||'主仓库'; calcRow(row) }
}

const calcRow = (row) => {
  row.discountedPrice = (row.unitPrice * (1 - (row.discountRate||0)/100)).toFixed(4)
  row.amount = (row.discountedPrice * row.quantity).toFixed(2)
  row.taxAmount = (row.amount * (row.taxRate||13) / 100).toFixed(2)
  row.totalAmount = (Number(row.amount) + Number(row.taxAmount)).toFixed(2)
  calcTotal()
}

const calcTotal = () => {
  let total = 0, tax = 0
  form.value.items.forEach(i => { total += Number(i.amount)||0; tax += Number(i.taxAmount)||0 })
  form.value.totalAmount = total.toFixed(2)
  form.value.taxAmount = tax.toFixed(2)
  form.value.totalWithTax = (total + Number(form.value.freight||0) - Number(form.value.discountAmount||0) + tax).toFixed(2)
}

const getSummary = () => {
  const qty = form.value.items.reduce((s,i) => s + (Number(i.quantity)||0), 0)
  const amt = form.value.items.reduce((s,i) => s + (Number(i.amount)||0), 0)
  return ['合计', '', '', '', '', qty, '', '', '', amt.toFixed(2), '']
}

const addItem = () => { form.value.items.push({ quantity:1, unitPrice:0, discountRate:0, taxRate:13, isGift:0, warehouse:'主仓库' }) }

const save = async () => {
  calcTotal()
  try {
    if (route.params.id) { await api.put(`/sale/orders/${route.params.id}`, form.value) }
    else { await api.post('/sale/orders', form.value) }
    ElMessage.success('保存成功')
    router.push('/sale/orders')
  } catch(e) {}
}

const submitAudit = async () => {
  try { await api.post(`/sale/orders/${route.params.id}/submit`); ElMessage.success('已提交审核'); load() } catch(e) {}
}
const doShip = async () => {
  try { await api.post(`/sale/orders/${route.params.id}/ship`, shipForm.value); ElMessage.success('发货成功，库存已扣减'); shipDialog.value=false; load() } catch(e) {}
}
const doInvoice = async () => {
  try { await api.post(`/sale/orders/${route.params.id}/invoice`, { invoiceNo: invoiceNo.value }); ElMessage.success('开票成功'); invoiceDialog.value=false; load() } catch(e) {}
}
const completeOrder = async () => {
  try { await api.post(`/sale/orders/${route.params.id}/complete`); ElMessage.success('订单已完成'); load() } catch(e) {}
}
const doCancel = async () => {
  try { await api.post(`/sale/orders/${route.params.id}/cancel`, { reason: cancelReason.value }); ElMessage.success('订单已取消'); cancelDialog.value=false; load() } catch(e) {}
}
const doReturn = async () => {
  try { await api.post(`/sale/orders/${route.params.id}/return`, { reason: returnReason.value }); ElMessage.success('退货成功，库存已回退'); returnDialog.value=false; load() } catch(e) {}
}
const copyOrder = async () => {
  try { const res = await api.post(`/sale/orders/${route.params.id}/copy`); ElMessage.success('复制成功，新订单: ' + res.data.orderNo); router.push(`/sale/orders/${res.data.id}`) } catch(e) {}
}

const doConvert = async () => {
  if (convertSelection.value.length === 0) { ElMessage.warning('请选择要转采购的明细'); return }
  try {
    const items = convertSelection.value.map(i => ({ saleOrderItemId: i.id, quantity: i.purchaseQty || i.quantity }))
    const res = await api.post(`/purchase/orders/convert-from-sale/${route.params.id}`, { items })
    if (res.code === 200) {
      const nos = res.data.map(o => o.orderNo).join(', ')
      ElMessage.success(`已生成采购订单: ${nos}`)
      convertDialog.value = false
      router.push('/purchase/orders')
    }
  } catch(e) {}
}

const load = async () => {
  if (route.params.id) {
    const res = await api.get(`/sale/orders/${route.params.id}`)
    if (res.code === 200) form.value = res.data
  }
}

onMounted(async () => {
  const [custRes, matRes] = await Promise.all([api.get('/sale/customers'), api.get('/sale/materials')])
  if (custRes.code===200) customers.value = custRes.data
  if (matRes.code===200) materials.value = matRes.data
  load()
})
</script>
