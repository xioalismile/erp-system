<template>
  <div class="ecom-order">
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('import')" type="primary" size="small" @click="openPull">{{ $tt("拉取平台订单") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadOrders">{{ $tt("刷新") }}</el-button>
      </div>
      <div class="kgw-search">
        <el-input v-model="searchText" :placeholder="$tt('平台单号/收件人/城市')" size="small" clearable @keyup.enter="loadOrders">
          <template #suffix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterPlatform" :placeholder="$tt('平台')" size="small" clearable style="width: 100px" @change="loadOrders">
          <el-option :label="$tf('亚马逊')" value="AMAZON" />
          <el-option :label="$tf('拼多多')" value="PDD" />
        </el-select>
        <el-select v-model="filterShopId" :placeholder="$tt('店铺')" size="small" clearable style="width: 150px" @change="loadOrders">
          <el-option v-for="s in shopList" :key="s.id" :label="shopLabel(s)" :value="s.id" />
        </el-select>
        <el-select v-model="filterConverted" :placeholder="$tt('转换状态')" size="small" clearable style="width: 110px" @change="loadOrders">
          <el-option :label="$tf('未转单')" :value="0" />
          <el-option :label="$tf('已转单')" :value="1" />
        </el-select>
        <el-button v-permission="perm('query')" type="primary" size="small" @click="loadOrders">{{ $tt("搜索") }}</el-button>
      </div>
    </div>

    <div class="kgw-body">
      <div v-if="!loading && tableData.length === 0" class="empty-guide">
        <div class="empty-title">{{ $tt("还没有平台订单") }}</div>
        <div class="empty-desc">{{ $tt("点左上角「拉取平台订单」选店铺和时间范围，把亚马逊/拼多多订单拉进来；拉单前请先在「商品映射」里建好货品对应关系。") }}</div>
      </div>

      <el-table v-show="tableData.length > 0" :data="tableData" border size="small" style="width:100%" v-loading="loading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="platformOrderId" :label="$tf('平台单号')" min-width="140" />
        <el-table-column :label="$tf('平台')" width="84" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.platform === 'PDD'" type="danger" size="small">{{ $tt('拼多多') }}</el-tag>
            <el-tag v-else type="primary" size="small">{{ $tt('亚马逊') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shopName" :label="$tf('店铺')" min-width="110" />
        <el-table-column :label="$tf('下单时间')" width="150" align="center">
          <template #default="{ row }">{{ row.purchaseDate ? String(row.purchaseDate).replace('T', ' ').slice(0, 19) : '' }}</template>
        </el-table-column>
        <el-table-column prop="orderStatus" :label="$tf('平台状态')" min-width="110" />
        <el-table-column :label="$tf('金额')" width="100" align="right">
          <template #default="{ row }">{{ formatAmount(row.orderTotal) }}</template>
        </el-table-column>
        <el-table-column prop="currencyCode" :label="$tf('币种')" width="66" align="center" />
        <el-table-column prop="itemCount" :label="$tf('件数')" width="66" align="center" />
        <el-table-column prop="shipName" :label="$tf('收件人')" min-width="100" />
        <el-table-column prop="shipCity" :label="$tf('收货城市')" min-width="90" />
        <el-table-column :label="$tf('转换状态')" width="150" align="center">
          <template #default="{ row }">
            <template v-if="row.converted === 1">
              <el-tag type="success" size="small">{{ $tt('已转单') }}</el-tag>
              <span class="converted-no">{{ row.convertedSaleOrderNo }}</span>
            </template>
            <el-tag v-else type="info" size="small">{{ $tt('未转单') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="perm('view')" type="primary" link size="small" @click="openDetail(row)">{{ $tt("查看明细") }}</el-button>
            <el-button v-permission="perm('convert')" v-if="row.converted !== 1" type="success" link size="small" @click="onConvert(row)">{{ $tt("转销售订单") }}</el-button>
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
          @current-change="loadOrders"
          @size-change="loadOrders"
        />
      </div>
    </div>

    <!-- 拉单弹窗（操作按钮在标题栏，不置底） -->
    <el-dialog v-model="pullVisible" width="480px" :close-on-click-modal="false">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('拉取平台订单') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="pullVisible = false">{{ $tt("取消") }}</el-button>
            <el-button size="small" type="primary" :loading="pulling" @click="onPull">{{ $tt("开始拉取") }}</el-button>
          </span>
        </div>
      </template>
      <el-form label-width="100px">
        <el-form-item :label="$tf('店铺')">
          <el-select v-model="pullForm.shopId" style="width:100%" :placeholder="$tt('不选 = 全部启用店铺')">
            <el-option v-for="s in shopList" :key="s.id" :label="shopLabel(s)" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$tf('往前看')">
          <el-select v-model="pullForm.days" style="width:180px">
            <el-option :label="$tf('最近 1 天')" :value="1" />
            <el-option :label="$tf('最近 3 天')" :value="3" />
            <el-option :label="$tf('最近 7 天')" :value="7" />
            <el-option :label="$tf('最近 30 天')" :value="30" />
            <el-option :label="$tf('最近 90 天')" :value="90" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="kgw-hint">{{ $tt("按平台“最近更新时间”向前拉取；已拉过的订单自动跳过，不会重复入库。") }}</div>
    </el-dialog>

    <!-- 明细弹窗（只读） -->
    <el-dialog v-model="detailVisible" width="960px" :close-on-click-modal="false" top="6vh">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ $tt('平台订单明细') }}{{ detailOrder.platformOrderId ? '：' + detailOrder.platformOrderId : '' }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="detailVisible = false">{{ $tt("关闭") }}</el-button>
          </span>
        </div>
      </template>

      <el-descriptions :column="3" size="small" border class="detail-desc">
        <el-descriptions-item :label="$tf('平台')">{{ detailOrder.platform === 'PDD' ? $tt('拼多多') : $tt('亚马逊') }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('店铺')">{{ detailOrder.shopName }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('下单时间')">{{ detailOrder.purchaseDate ? String(detailOrder.purchaseDate).replace('T', ' ').slice(0, 19) : '' }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('平台状态')">{{ detailOrder.orderStatus }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('收件人')">{{ detailOrder.shipName }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('订单金额')">{{ formatAmount(detailOrder.orderTotal) }} {{ detailOrder.currencyCode }}</el-descriptions-item>
        <el-descriptions-item :label="$tf('收货地址')" :span="3">{{ detailOrder.shipAddress }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detailItems" border size="small" style="width:100%; margin-top:12px" max-height="360">
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column prop="sellerSku" :label="$tf('卖家SKU')" min-width="120" />
        <el-table-column prop="asin" :label="$tf('平台货品编号')" min-width="110">
          <template #default="{ row }">{{ row.asin || (row.platformItemId && row.platformItemId.startsWith('PDD-SKU-') ? '' : row.platformItemId) }}</template>
        </el-table-column>
        <el-table-column prop="productName" :label="$tf('平台商品名称')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="quantity" :label="$tf('数量')" width="80" align="right" />
        <el-table-column :label="$tf('单价')" width="100" align="right">
          <template #default="{ row }">{{ formatAmount(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('金额')" width="100" align="right">
          <template #default="{ row }">{{ formatAmount(row.itemTotal) }}</template>
        </el-table-column>
        <el-table-column :label="$tf('映射物料')" min-width="150">
          <template #default="{ row }">
            <template v-if="row.mappedMaterialCode">{{ row.mappedMaterialCode }} {{ row.mappedMaterialName }}</template>
            <el-tag v-else type="danger" size="small">{{ $tt('未映射') }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
import { listEcomOrders, getEcomOrder, pullEcomOrders, convertEcomOrder } from '@/api/ecommerce/order'
import { listEnabledShops } from '@/api/ecommerce/shop'

const perm = usePagePerm('ecommerce:orders')

// ==================== 列表 ====================
const searchText = ref('')
const filterPlatform = ref(null)
const filterShopId = ref(null)
const filterConverted = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const tableData = ref([])
const shopList = ref([])

const formatAmount = (v) => (v == null || v === '' ? '' : Number(v).toFixed(2))
const shopLabel = (s) => (s.platform === 'PDD' ? `${s.shopName}（拼多多）` : `${s.shopName}（亚马逊）`)

async function loadOrders() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (searchText.value) params.keyword = searchText.value
    if (filterPlatform.value) params.platform = filterPlatform.value
    if (filterShopId.value) params.shopId = filterShopId.value
    if (filterConverted.value !== null && filterConverted.value !== '') params.converted = filterConverted.value
    const res = await listEcomOrders(params)
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

// ==================== 拉单 ====================
const pullVisible = ref(false)
const pulling = ref(false)
const pullForm = reactive({ shopId: null, days: 7 })

function openPull() {
  pullForm.shopId = null
  pullForm.days = 7
  pullVisible.value = true
}

async function onPull() {
  pulling.value = true
  try {
    const res = await pullEcomOrders({ ...pullForm })
    ElMessage.success(tt(`拉取完成：拉到 ${res.fetched} 张，新入库 ${res.added} 张，已存在跳过 ${res.skipped} 张`))
    pullVisible.value = false
    loadOrders()
  } catch (e) { /* 后端已给出中文原因 */ } finally {
    pulling.value = false
  }
}

// ==================== 明细 ====================
const detailVisible = ref(false)
const detailOrder = ref({})
const detailItems = ref([])

async function openDetail(row) {
  try {
    const res = await getEcomOrder(row.id)
    detailOrder.value = res || {}
    detailItems.value = (res && res.items) || []
    detailVisible.value = true
  } catch (e) { /* 后端已给出中文原因 */ }
}

// ==================== 转销售订单 ====================
async function onConvert(row) {
  const platformName = row.platform === 'PDD' ? tt('拼多多') : tt('亚马逊')
  try {
    await ElMessageBox.confirm(
      tt(`把${platformName}平台订单「${row.platformOrderId}」转成销售订单？将按商品映射生成一张草稿态销售订单，客户取店铺的关联客户。`),
      tt('转销售订单'), { type: 'info' })
  } catch (e) { return }
  try {
    const res = await convertEcomOrder(row.id)
    ElMessage.success(tt(`已生成销售订单「${res.saleOrderNo}」（草稿），请到 销售管理 → 销售订单 中查看`))
    loadOrders()
  } catch (e) { /* 后端已给出中文原因 */ }
}

onMounted(() => {
  loadOrders()
  loadShops()
})
</script>

<style scoped lang="scss">
.ecom-order {
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
.kgw-search { display: flex; gap: 8px; align-items: center; :deep(.el-input) { width: 190px; } }
.kgw-body { padding: 12px; flex: 1; }
.table-pagination { display: flex; justify-content: flex-end; padding: 12px 0 4px 0; }
.kgw-hint { font-size: 12px; color: #909399; margin-top: 6px; line-height: 1.5; }

.empty-guide { padding: 60px 0; text-align: center; }
.empty-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: #909399; }

.converted-no { margin-left: 4px; font-size: 12px; color: #606266; }
.detail-desc { margin-top: 4px; }

// 弹窗：操作按钮在标题栏右侧（不置底）
:deep(.el-dialog__header) { margin-right: 0; }
.dlg-header { display: flex; align-items: center; justify-content: space-between; }
.dlg-title { font-size: 15px; font-weight: 600; color: #303133; }
.dlg-header-actions { display: flex; gap: 8px; }

:deep(.el-button--primary) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary:hover) { background-color: #40a9ff; border-color: #40a9ff; }
</style>
