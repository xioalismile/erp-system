<template>
  <div class="promo-panel">
    <!-- 本周统计条 -->
    <div class="wk-bar">
      <div class="wk-item">
        <div class="wk-label">{{ $tt("本周朋友圈推广") }}</div>
        <div class="wk-val" :class="week.promotionCount >= week.weeklyPromotionTarget ? 'ok' : 'warn'">
          {{ week.promotionCount || 0 }}<span class="wk-unit">/{{ week.weeklyPromotionTarget || 3 }} 次</span>
        </div>
      </div>
      <div class="wk-item">
        <div class="wk-label">{{ $tt("本周推广记录") }}</div>
        <div class="wk-val">{{ week.recordCount || 0 }}<span class="wk-unit">条</span></div>
      </div>
      <div class="wk-item">
        <div class="wk-label">{{ $tt("覆盖客户（去重）") }}</div>
        <div class="wk-val">{{ week.coveredCustomerCount || 0 }}<span class="wk-unit">家</span></div>
      </div>
      <div class="wk-item">
        <div class="wk-label">{{ $tt("RFID 素材覆盖客户") }}</div>
        <div class="wk-val">{{ week.rfidCoveredCustomerCount || 0 }}<span class="wk-unit">家</span></div>
      </div>
      <div class="wk-item">
        <div class="wk-label">{{ $tt("本周区间") }}</div>
        <div class="wk-range">{{ week.weekStart }} ~ {{ week.weekEnd }}</div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="erp-search-bar">
      <el-input v-model="search.keyword" :placeholder="$tt('记录编号/素材名称/推广人/备注')" style="width:240px;" clearable />
      <el-select v-model="search.materialType" :placeholder="$tt('素材类型')" style="width:150px;" clearable>
        <el-option v-for="t in materialTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-date-picker v-model="search.dateRange" type="daterange" value-format="YYYY-MM-DD"
        start-placeholder="开始日期" end-placeholder="结束日期" style="width:250px;" />
      <el-button type="primary" @click="handleSearch">{{ $tt("查询") }}</el-button>
      <el-button @click="handleReset">{{ $tt("重置") }}</el-button>
      <div style="flex:1"></div>
      <el-button type="primary" v-permission="perm('promotion')" @click="handleAdd">{{ $tt("+ 新增推广") }}</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column prop="promotionNo" :label="$tf('记录编号')" width="160" />
      <el-table-column prop="promotionDate" :label="$tf('推广日期')" width="115" />
      <el-table-column prop="staffName" :label="$tf('推广人')" width="100" />
      <el-table-column prop="materialType" :label="$tf('素材类型')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.materialType === 'RFID素材' ? 'warning' : 'info'">{{ row.materialType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="materialName" :label="$tf('素材名称')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="targetCount" :label="$tf('发送对象数')" width="100" align="right" />
      <el-table-column prop="customerNames" :label="$tf('发送对象')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.customerNames || '' }}</template>
      </el-table-column>
      <el-table-column :label="$tf('截图')" width="80" align="center">
        <template #default="{ row }">
          <el-image v-if="row.attachment" :src="firstImage(row.attachment)" :preview-src-list="splitImages(row.attachment)"
            fit="cover" style="width:36px;height:36px;border-radius:4px;" preview-teleported />
          <span v-else class="muted">{{ $tt("无") }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$tf('是否达标')" width="95" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.isQualified === 1 ? 'success' : 'danger'">{{ row.isQualified === 1 ? '达标' : '未达标' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" :label="$tf('备注')" min-width="120" show-overflow-tooltip />
      <el-table-column :label="$tf('操作')" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">{{ $tt("编辑") }}</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">{{ $tt("删除") }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div style="padding:24px 0;color:#909399;text-align:center;">
          {{ $tt("本周还没有推广记录 —— 点右上角【+ 新增推广】上传截图即可计入") }}
        </div>
      </template>
    </el-table>

    <div style="margin-top:16px;display:flex;justify-content:flex-end;">
      <el-pagination v-model:current-page="page.current" v-model:page-size="page.size"
        :page-sizes="[10, 20, 50, 100]" :total="page.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData" @current-change="loadData" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑推广记录' : '新增推广记录'" width="720px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$tf('推广日期')" prop="promotionDate">
              <el-date-picker v-model="form.promotionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$tf('素材类型')" prop="materialType">
              <el-select v-model="form.materialType" style="width:100%">
                <el-option v-for="t in materialTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$tf('素材名称')">
          <el-input v-model="form.materialName" :placeholder="$tt('如：10月新品车间视频')" />
        </el-form-item>
        <el-form-item :label="$tf('发送对象')">
          <el-select v-model="form.customerIds" multiple filterable collapse-tags collapse-tags-tooltip
            :placeholder="$tt('从我的私海客户中选择（可多选）')" style="width:100%">
            <el-option v-for="c in customers" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('截图')" prop="attachment">
          <div style="width:100%">
            <ImageUploader v-model="form.attachment" />
            <div class="tip">{{ $tt("朋友圈类推广必须上传截图，否则不计入本周推广次数") }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">{{ $tt("保存") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPromotion, savePromotion, updatePromotion, deletePromotion, getWeekStats } from '@/api/crm/promotion'
import { listCrmCustomer } from '@/api/crm/customer'
import ImageUploader from '@/components/ImageUploader.vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

// 本组件挂在「销售过程」页，沿用该页页面对象码（按钮权限）
const perm = usePagePerm('crm:sales-activity')

const materialTypes = ['朋友圈图片', '朋友圈视频', '产品图', '车间视频', 'RFID素材']

const loading = ref(false)
const saving = ref(false)
const records = ref([])
const customers = ref([])
const week = reactive({})
const page = reactive({ current: 1, size: 20, total: 0 })
const search = reactive({ keyword: '', materialType: '', dateRange: [] })

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
  promotionId: null, promotionDate: '', materialType: '朋友圈图片',
  materialName: '', customerIds: [], attachment: '', remark: ''
})
const rules = {
  promotionDate: [{ required: true, message: tt('请选择推广日期'), trigger: 'change' }],
  materialType: [{ required: true, message: tt('请选择素材类型'), trigger: 'change' }]
}

function splitImages(v) { return (v || '').split(',').filter(Boolean) }
function firstImage(v) { return splitImages(v)[0] }

async function loadData() {
  loading.value = true
  try {
    const res = await listPromotion({
      current: page.current, size: page.size,
      keyword: search.keyword || undefined,
      materialType: search.materialType || undefined,
      startDate: search.dateRange?.[0] || undefined,
      endDate: search.dateRange?.[1] || undefined
    })
    records.value = res?.records || []
    page.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadWeek() {
  const res = await getWeekStats({})
  Object.assign(week, res || {})
}

async function loadCustomers() {
  try {
    const res = await listCrmCustomer({ poolType: 0, page: 1, size: 9999 })
    customers.value = res?.records || []
  } catch (e) {
    customers.value = []
  }
}

function handleSearch() { page.current = 1; loadData() }
function handleReset() { search.keyword = ''; search.materialType = ''; search.dateRange = []; handleSearch() }

function resetForm() {
  form.promotionId = null
  form.promotionDate = new Date().toISOString().slice(0, 10)
  form.materialType = '朋友圈图片'
  form.materialName = ''
  form.customerIds = []
  form.attachment = ''
  form.remark = ''
}

function handleAdd() { isEdit.value = false; resetForm(); dialogVisible.value = true }

async function handleEdit(row) {
  isEdit.value = true
  const d = await import('@/api/crm/promotion').then(m => m.getPromotion(row.promotionId))
  Object.assign(form, {
    promotionId: d.promotionId,
    promotionDate: d.promotionDate,
    materialType: d.materialType || '朋友圈图片',
    materialName: d.materialName || '',
    customerIds: d.customerIds || [],
    attachment: d.attachment || '',
    remark: d.remark || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updatePromotion(form.promotionId, { ...form })
      ElMessage.success(tt('已保存'))
    } else {
      await savePromotion({ ...form })
      ElMessage.success(tt('已新增'))
    }
    dialogVisible.value = false
    await loadData()
    await loadWeek()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(tt('确认删除该推广记录？删除后本周推广次数会同步减少。'), tt('提示'), { type: 'warning' })
  await deletePromotion(row.promotionId)
  ElMessage.success(tt('已删除'))
  await loadData()
  await loadWeek()
}

onMounted(() => { loadData(); loadWeek(); loadCustomers() })
</script>

<style scoped>
.promo-panel { padding-top: 4px; }
.wk-bar { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.wk-item { flex: 0 0 auto; min-width: 150px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 10px 14px; background: var(--el-fill-color-blank); }
.wk-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.wk-val { font-size: 18px; font-weight: 600; font-variant-numeric: tabular-nums; }
.wk-val.ok { color: #67c23a; }
.wk-val.warn { color: #e6a23c; }
.wk-unit { font-size: 12px; font-weight: 400; color: #909399; margin-left: 2px; }
.wk-range { font-size: 13px; color: #606266; padding-top: 4px; }
.erp-search-bar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 12px; }
.tip { font-size: 12px; color: #909399; margin-top: 4px; }
.muted { color: #c0c4cc; }
</style>
