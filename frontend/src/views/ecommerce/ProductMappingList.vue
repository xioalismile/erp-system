<template>
  <div class="ecom-mapping">
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('add')" type="primary" size="small" @click="openAdd">{{ $tt("新增映射") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadMappings">{{ $tt("刷新") }}</el-button>
      </div>
      <div class="kgw-search">
        <el-input v-model="searchText" :placeholder="$tt('卖家SKU/ASIN/物料编码/名称')" size="small" clearable @keyup.enter="loadMappings">
          <template #suffix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterShopId" :placeholder="$tt('店铺')" size="small" clearable style="width: 140px" @change="loadMappings">
          <el-option v-for="s in shopList" :key="s.id" :label="s.shopName" :value="s.id" />
        </el-select>
        <el-button v-permission="perm('query')" type="primary" size="small" @click="loadMappings">{{ $tt("搜索") }}</el-button>
      </div>
    </div>

    <div class="kgw-body">
      <div v-if="!loading && tableData.length === 0" class="empty-guide">
        <div class="empty-title">{{ $tt("还没有商品映射") }}</div>
        <div class="empty-desc">{{ $tt("把平台卖家SKU对应到系统物料：点「新增映射」，一个平台SKU对应一个物料；未映射的SKU不能转销售订单。") }}</div>
      </div>

      <el-table v-show="tableData.length > 0" :data="tableData" border size="small" style="width:100%" v-loading="loading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="shopName" :label="$tf('店铺')" min-width="120" />
        <el-table-column prop="sellerSku" :label="$tf('卖家SKU')" min-width="130" />
        <el-table-column prop="asin" :label="$tf('ASIN')" min-width="120" />
        <el-table-column prop="productName" :label="$tf('平台商品名称')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="120" />
        <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="160" />
        <el-table-column :label="$tf('操作')" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="perm('edit')" type="primary" link size="small" @click="openEdit(row)">{{ $tt("编辑") }}</el-button>
            <el-button v-permission="perm('delete')" type="danger" link size="small" @click="onDelete(row)">{{ $tt("删除") }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          small
          background
          @current-change="loadMappings"
          @size-change="loadMappings"
        />
      </div>
    </div>

    <!-- 新增 / 编辑映射（操作按钮在标题栏，不置底；选物料走通用物料库弹窗） -->
    <el-dialog v-model="dialogVisible" width="640px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ isEdit ? $tt('编辑映射') : $tt('新增映射') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
          </span>
        </div>
      </template>

      <el-form label-width="110px" class="dlg-form">
        <el-form-item :label="$tf('所属店铺')" required>
          <el-select v-model="form.shopId" style="width:100%" :placeholder="$tt('选择亚马逊店铺')">
            <el-option v-for="s in shopList" :key="s.id" :label="s.shopName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('卖家SKU')" required>
          <el-input v-model="form.sellerSku" :placeholder="$tt('平台后台的SKU编码')" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$tf('ASIN')">
          <el-input v-model="form.asin" placeholder="B0XXXXXXXXX" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$tf('平台商品名称')">
          <el-input v-model="form.productName" :placeholder="$tt('选填，便于识别')" maxlength="500" />
        </el-form-item>
        <el-form-item :label="$tf('对应物料')" required>
          <div class="material-row">
            <el-input :model-value="form.materialCode ? (form.materialCode + ' ' + (form.materialName || '')) : ''"
                      :placeholder="$tt('从物料库选择')" readonly />
            <el-button v-permission="perm('pick')" size="small" type="primary" plain @click="pickerVisible = true">{{ $tt("选择物料") }}</el-button>
            <el-button v-if="form.materialId" size="small" @click="clearMaterial">{{ $tt("清除") }}</el-button>
          </div>
          <div class="kgw-hint">{{ $tt("转销售订单时，这一行平台货品会按这里选的物料写入单据明细。") }}</div>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 通用物料库弹窗（全站统一选料组件） -->
    <MaterialPickerDialog v-model="pickerVisible" :title="$tt('从物料库选择')" @confirm="onMaterialPicked" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { listProductMappings, saveProductMapping, updateProductMapping, deleteProductMapping } from '@/api/ecommerce/productMapping'
import { listEnabledShops } from '@/api/ecommerce/shop'

const perm = usePagePerm('ecommerce:mapping')

// ==================== 列表 ====================
const searchText = ref('')
const filterShopId = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const tableData = ref([])
const shopList = ref([])

async function loadMappings() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (searchText.value) params.keyword = searchText.value
    if (filterShopId.value) params.shopId = filterShopId.value
    const res = await listProductMappings(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

async function loadShops() {
  try {
    shopList.value = await listEnabledShops() || []
  } catch (e) {
    shopList.value = []
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(tt(`确定删除「${row.sellerSku}」的映射？删除后该平台货品将无法转销售订单。`), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteProductMapping(row.id)
    ElMessage.success(tt('删除成功'))
    loadMappings()
  } catch (e) { /* 后端已给出中文原因 */ }
}

// ==================== 新增 / 编辑弹窗 ====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editRow = ref({})
const pickerVisible = ref(false)
const form = reactive({ shopId: null, sellerSku: '', asin: '', productName: '', materialId: null, materialCode: '', materialName: '' })

function openAdd() {
  isEdit.value = false
  editRow.value = {}
  Object.assign(form, { shopId: null, sellerSku: '', asin: '', productName: '', materialId: null, materialCode: '', materialName: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editRow.value = row
  Object.assign(form, {
    shopId: row.shopId, sellerSku: row.sellerSku, asin: row.asin || '',
    productName: row.productName || '', materialId: row.materialId,
    materialCode: row.materialCode || '', materialName: row.materialName || ''
  })
  dialogVisible.value = true
}

function onMaterialPicked(payload) {
  const rows = (payload && payload.rows) || []
  if (rows.length === 0) return
  const m = rows[0]
  form.materialId = m.materialId
  form.materialCode = m.materialCode
  form.materialName = m.materialName
  pickerVisible.value = false
}

function clearMaterial() {
  form.materialId = null
  form.materialCode = ''
  form.materialName = ''
}

async function onSave() {
  if (!form.shopId) { ElMessage.warning(tt('请选择所属店铺')); return }
  if (!form.sellerSku || !form.sellerSku.trim()) { ElMessage.warning(tt('请填写卖家SKU')); return }
  if (!form.materialId) { ElMessage.warning(tt('请选择对应物料')); return }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateProductMapping(editRow.value.id, { ...form })
    } else {
      await saveProductMapping({ ...form })
    }
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    loadMappings()
  } catch (e) { /* 后端已给出中文原因 */ } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadMappings()
  loadShops()
})
</script>

<style scoped lang="scss">
.ecom-mapping {
  background: #fff;
  min-height: 100%;
  font-family: Tahoma, 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
}
.kgw-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
  height: 48px;
  flex-shrink: 0;
  gap: 12px;
}
.kgw-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.kgw-search { display: flex; gap: 8px; align-items: center; :deep(.el-input) { width: 210px; } }
.kgw-body { padding: 12px; flex: 1; }
.table-pagination { display: flex; justify-content: flex-end; padding: 12px 0 4px 0; }
.kgw-hint { font-size: 12px; color: #909399; margin-top: 2px; line-height: 1.5; }

.empty-guide { padding: 60px 0; text-align: center; }
.empty-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: #909399; }

.material-row { display: flex; gap: 8px; width: 100%; align-items: center; }

// 弹窗：操作按钮在标题栏右侧（不置底）
:deep(.el-dialog__header) { margin-right: 0; }
.dlg-header { display: flex; align-items: center; justify-content: space-between; }
.dlg-title { font-size: 15px; font-weight: 600; color: #303133; }
.dlg-header-actions { display: flex; gap: 8px; }
.dlg-form { margin-bottom: 4px; }

:deep(.el-button--primary) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary:hover) { background-color: #40a9ff; border-color: #40a9ff; }
</style>
