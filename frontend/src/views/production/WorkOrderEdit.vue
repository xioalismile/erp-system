<template>
  <div>
    <el-card style="margin-bottom:16px;">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <div>
          <el-button text @click="$router.push('/production/workorders')">◀ 返回列表</el-button>
          <span style="font-size:16px;font-weight:600;margin-left:10px;">生产工单</span>
          <el-tag style="margin-left:10px;" :type="statusType(form.status)">{{ form.status }}</el-tag>
        </div>
        <div>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button v-if="form.status==='待开工'" type="success" @click="startWork">开工</el-button>
          <el-button v-if="form.status==='已开工'" type="warning" @click="reportDialog=true">报工</el-button>
          <el-button v-if="form.status==='已开工'" type="primary" @click="finishWork">完工入库</el-button>
          <el-button v-if="form.status!=='已完工'&&form.status!=='已取消'" type="danger" plain @click="cancelDialog=true">取消</el-button>
        </div>
      </div>
    </el-card>

    <el-card style="margin-bottom:16px;">
      <template #header>工单信息</template>
      <el-form :model="form" label-width="100px" :inline="true">
        <el-form-item label="产品">
          <el-select v-model="form.productCode" filterable placeholder="选择产品" style="width:300px" @change="onProductChange">
            <el-option v-for="m in materials" :key="m.id" :label="m.materialName + ' (' + m.materialCode + ')'" :value="m.materialCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划数量"><el-input-number v-model="form.plannedQuantity" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="车间"><el-input v-model="form.workshop" placeholder="主车间" /></el-form-item>
        <el-form-item label="计划开始"><el-date-picker v-model="form.plannedStartDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="计划完工"><el-date-picker v-model="form.plannedFinishDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" style="width:300px" /></el-form-item>
      </el-form>
      <div v-if="form.completedQuantity > 0" style="margin-top:10px;color:#10b981;">
        已完工: {{ form.completedQuantity }} {{ form.unit }} &nbsp; 报废: {{ form.scrapQuantity }} {{ form.unit }}
      </div>
    </el-card>

    <el-card>
      <template #header>用料明细 (BOM自动展开)</template>
      <el-table :data="form.materials" border size="small">
        <el-table-column prop="lineNo" label="行" width="50" />
        <el-table-column prop="materialCode" label="物料编码" width="120" />
        <el-table-column prop="materialName" label="物料名称" />
        <el-table-column prop="specification" label="规格" width="120" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="requiredQuantity" label="需求数量" width="100" />
        <el-table-column prop="issuedQuantity" label="已领料" width="80" />
        <el-table-column prop="warehouse" label="仓库" width="80" />
      </el-table>
    </el-card>

    <el-dialog v-model="reportDialog" title="报工" width="400px">
      <el-form label-width="80px">
        <el-form-item label="完工数量"><el-input-number v-model="reportForm.completedQty" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="报废数量"><el-input-number v-model="reportForm.scrapQty" :min="0" :precision="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportDialog=false">取消</el-button><el-button type="primary" @click="doReport">确认报工</el-button></template>
    </el-dialog>

    <el-dialog v-model="cancelDialog" title="取消工单" width="400px">
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
const materials = ref([])
const reportDialog = ref(false)
const reportForm = ref({ completedQty:0, scrapQty:0 })
const cancelDialog = ref(false)
const cancelReason = ref('')

const form = ref({
  orderDate: new Date().toISOString().slice(0,10),
  productCode:'', productName:'', specification:'', unit:'',
  plannedQuantity:1, completedQuantity:0, scrapQuantity:0,
  plannedStartDate:'', plannedFinishDate:'',
  sourceType:'MANUAL', status:'待开工', workshop:'主车间', remark:'',
  materials:[]
})

const statusType = (s) => ({'待开工':'info','已开工':'warning','已完工':'success','已取消':'danger'}[s]||'info')

const onProductChange = (code) => {
  const m = materials.value.find(x => x.materialCode === code)
  if (m) { form.value.productName=m.materialName; form.value.specification=m.specification; form.value.unit=m.unit }
}

const save = async () => {
  try {
    if (route.params.id) { await api.put(`/production/workorders/${route.params.id}`, form.value) }
    else { await api.post('/production/workorders', form.value) }
    ElMessage.success('保存成功'); router.push('/production/workorders')
  } catch(e) {}
}

const startWork = async () => {
  try { await api.post(`/production/workorders/${route.params.id}/start`); ElMessage.success('已开工，原材料已扣减'); load() } catch(e) {}
}
const doReport = async () => {
  try { await api.post(`/production/workorders/${route.params.id}/report`, reportForm.value); ElMessage.success('报工成功'); reportDialog.value=false; load() } catch(e) {}
}
const finishWork = async () => {
  try { await api.post(`/production/workorders/${route.params.id}/finish`); ElMessage.success('完工入库成功'); load() } catch(e) {}
}
const doCancel = async () => {
  try { await api.post(`/production/workorders/${route.params.id}/cancel`, { reason: cancelReason.value }); ElMessage.success('已取消'); cancelDialog.value=false; load() } catch(e) {}
}

const load = async () => {
  if (route.params.id) { const res = await api.get(`/production/workorders/${route.params.id}`); if (res.code===200) form.value = res.data }
}

onMounted(async () => {
  const matRes = await api.get('/sale/materials', { params: { limit: 100 } })
  if (matRes.code===200) materials.value = matRes.data
  load()
})
</script>
