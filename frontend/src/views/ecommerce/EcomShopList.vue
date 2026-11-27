<template>
  <div class="ecom-shop">
    <div class="kgw-toolbar">
      <div class="kgw-actions">
        <el-button v-permission="perm('add')" type="primary" size="small" @click="openAdd">{{ $tt("新增店铺") }}</el-button>
        <el-button v-permission="perm('refresh')" size="small" @click="loadShops">{{ $tt("刷新") }}</el-button>
      </div>
      <div class="kgw-search">
        <el-input v-model="searchText" :placeholder="$tt('店铺名称/卖家编号/关联客户')" size="small" clearable @keyup.enter="loadShops">
          <template #suffix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterStatus" :placeholder="$tt('状态')" size="small" clearable style="width: 96px" @change="loadShops">
          <el-option :label="$tf('启用')" :value="1" />
          <el-option :label="$tf('停用')" :value="0" />
        </el-select>
        <el-button v-permission="perm('query')" type="primary" size="small" @click="loadShops">{{ $tt("搜索") }}</el-button>
      </div>
    </div>

    <div class="kgw-body">
      <div v-if="!loading && tableData.length === 0" class="empty-guide">
        <div class="empty-title">{{ $tt("还没有授权任何店铺") }}</div>
        <div class="empty-desc">{{ $tt("点左上角「新增店铺」填写卖家平台凭据完成授权后，即可在「亚马逊订单」里拉取平台订单。") }}</div>
      </div>

      <el-table v-show="tableData.length > 0" :data="tableData" border size="small" style="width:100%" v-loading="loading">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="shopName" :label="$tf('店铺名称')" min-width="140" />
        <el-table-column :label="$tf('平台')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.platform === 'PDD'" type="danger" size="small">{{ $tt('拼多多') }}</el-tag>
            <el-tag v-else type="primary" size="small">{{ $tt('亚马逊') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('站点区域')" width="90" align="center">
          <template #default="{ row }">{{ regionLabel(row.region) }}</template>
        </el-table-column>
        <el-table-column prop="marketplaceId" :label="$tf('市场编号')" min-width="130" />
        <el-table-column prop="sellerId" :label="$tf('卖家编号')" min-width="130" />
        <el-table-column prop="associateCustomerName" :label="$tf('关联客户')" min-width="130" />
        <el-table-column :label="$tf('授权状态')" width="92" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.authStatus === '已授权'" type="success" size="small">{{ $tt('已授权') }}</el-tag>
            <el-tag v-else-if="row.authStatus === '已失效'" type="danger" size="small">{{ $tt('已失效') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $tt('未授权') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('最近授权时间')" width="150" align="center">
          <template #default="{ row }">{{ row.lastAuthTime ? String(row.lastAuthTime).replace('T', ' ').slice(0, 19) : '' }}</template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="76" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="onToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="perm('testConnection')" type="success" link size="small" @click="onTestAuth(row)">{{ $tt("授权测试") }}</el-button>
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
          @current-change="loadShops"
          @size-change="loadShops"
        />
      </div>
    </div>

    <!-- 新增 / 编辑店铺（操作按钮在弹窗标题栏，不置底） -->
    <el-dialog v-model="dialogVisible" width="720px" :close-on-click-modal="false" top="6vh">
      <template #header>
        <div class="dlg-header">
          <span class="dlg-title">{{ isEdit ? $tt('编辑店铺') : $tt('新增店铺') }}</span>
          <span class="dlg-header-actions">
            <el-button size="small" @click="dialogVisible = false">{{ $tt("取消") }}</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="onSave">{{ $tt("保存") }}</el-button>
          </span>
        </div>
      </template>

      <el-form label-width="110px" class="dlg-form">
        <el-form-item :label="$tf('店铺名称')" required>
          <el-input v-model="form.shopName" :placeholder="$tt('如：北美主店铺')" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$tf('平台')" required>
          <el-select v-model="form.platform" style="width:180px" @change="onPlatformChange">
            <el-option :label="$tf('亚马逊')" value="AMAZON" />
            <el-option :label="$tf('拼多多')" value="PDD" />
          </el-select>
          <span class="kgw-hint" style="margin-left:8px">{{ $tt('亚马逊=北美站直连；拼多多=国内商家自研应用') }}</span>
        </el-form-item>
        <template v-if="form.platform !== 'PDD'">
          <el-form-item :label="$tf('站点区域')" required>
            <el-select v-model="form.region" style="width:220px" @change="onRegionChange">
              <el-option :label="$tf('北美站')" value="NA" />
              <el-option :label="$tf('欧洲站')" value="EU" disabled />
              <el-option :label="$tf('日本站')" value="FE" disabled />
            </el-select>
            <span class="kgw-hint" style="margin-left:8px">{{ $tt('第一期先开北美站') }}</span>
          </el-form-item>
          <el-form-item :label="$tf('市场编号')" required>
            <el-input v-model="form.marketplaceId" style="width:220px" maxlength="50" />
            <span class="kgw-hint" style="margin-left:8px">{{ $tt('美国站默认 ATVPDKIKX0DER') }}</span>
          </el-form-item>
        </template>
        <el-form-item :label="$tf('卖家编号')">
          <el-input v-model="form.sellerId" :placeholder="form.platform === 'PDD' ? $tt('拼多多店铺账号（选填）') : $tt('卖家平台：设置 → 账户信息 里查看')" maxlength="100" />
        </el-form-item>
        <el-divider content-position="left">{{ $tt("平台凭据（加密保存，永不明文回显）") }}</el-divider>
        <el-form-item :label="$tf('客户端编号')" :required="!isEdit">
          <el-input v-model="form.clientId" :placeholder="isEdit ? $tt('留空 = 保持原值不变') : $tt('开放平台应用颁发')" maxlength="200" />
        </el-form-item>
        <el-form-item :label="$tf('客户端密钥')" :required="!isEdit">
          <el-input v-model="form.clientSecret" :placeholder="isEdit ? (editRow.clientSecretMasked ? ($tt('已保存：') + editRow.clientSecretMasked + '，留空 = 不修改') : $tt('留空 = 保持原值不变')) : $tt('开放平台应用密钥')" maxlength="200" show-password />
        </el-form-item>
        <template v-if="form.platform === 'PDD'">
          <el-form-item :label="$tf('访问令牌')" :required="!isEdit">
            <el-input v-model="form.accessToken" type="textarea" :rows="2" :placeholder="isEdit ? (editRow.accessTokenConfigured ? $tt('已保存，留空 = 不修改') : $tt('请填写访问令牌')) : $tt('应用授权后生成（商家自研上线后有效期一年）')" />
            <div class="kgw-hint">{{ $tt("获取方式：开放平台应用详情 → 授权管理 → 店铺授权成功后复制访问令牌；到期后系统会用刷新令牌自动续期。") }}</div>
          </el-form-item>
          <el-form-item :label="$tf('刷新令牌')">
            <el-input v-model="form.refreshToken" type="textarea" :rows="2" :placeholder="isEdit ? (editRow.refreshTokenConfigured ? $tt('已保存，留空 = 不修改') : $tt('选填：到期自动续期用')) : $tt('与访问令牌一同生成，选填')" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="$tf('刷新令牌')" :required="!isEdit">
            <el-input v-model="form.refreshToken" type="textarea" :rows="2" :placeholder="isEdit ? (editRow.refreshTokenConfigured ? $tt('已保存，留空 = 不修改') : $tt('请填写刷新令牌')) : $tt('卖家平台应用授权后获得')" />
          </el-form-item>
        </template>
        <el-form-item :label="$tf('关联客户')" required>
          <el-select v-model="form.associateCustomerId" filterable clearable style="width:100%" :placeholder="$tt('转销售订单时用作单据客户')">
            <el-option v-for="c in customerList" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
          </el-select>
          <div class="kgw-hint">{{ $tt("平台订单转销售订单时，客户取这里选择的客户；请先在客户管理里建档。") }}</div>
        </el-form-item>
        <el-form-item :label="$tf('备注')">
          <el-input v-model="form.remark" :placeholder="$tt('选填')" />
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'
import { tt } from '@/i18n'
import {
  listEcomShops, saveEcomShop, updateEcomShop, deleteEcomShop, testEcomShopAuth
} from '@/api/ecommerce/shop'
import { listAllCustomer } from '@/api/basedata/customer'

const perm = usePagePerm('ecommerce:shop')

// ==================== 列表 ====================
const searchText = ref('')
const filterStatus = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const tableData = ref([])

async function loadShops() {
  loading.value = true
  try {
    const params = { current: page.value, size: pageSize.value }
    if (searchText.value) params.keyword = searchText.value
    if (filterStatus.value !== null && filterStatus.value !== '') params.status = filterStatus.value
    const res = await listEcomShops(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const regionLabel = (r) => ({ NA: tt('北美站'), EU: tt('欧洲站'), FE: tt('日本站'), CN: tt('国内站') }[r] || r || '')

async function onToggle(row) {
  try {
    await updateEcomShop(row.id, { ...row })
    ElMessage.success(tt('操作成功'))
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(tt(`确定删除店铺「${row.shopName}」？已拉取过订单或建过商品映射的店铺不能删除。`), tt('提示'), { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteEcomShop(row.id)
    ElMessage.success(tt('删除成功'))
    loadShops()
  } catch (e) { /* 后端已给出中文原因 */ }
}

async function onTestAuth(row) {
  try {
    await ElMessageBox.confirm(tt(`用店铺「${row.shopName}」已保存的凭据向卖家平台换取访问令牌，确认测试？`), tt('授权测试'), { type: 'info' })
  } catch (e) { return }
  try {
    await testEcomShopAuth(row.id)
    ElMessage.success(tt('授权成功，店铺状态已更新为已授权'))
    loadShops()
  } catch (e) {
    loadShops()
  }
}

// ==================== 新增 / 编辑弹窗 ====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editRow = ref({})
const customerList = ref([])
const form = reactive({
  shopName: '', platform: 'AMAZON', region: 'NA', marketplaceId: 'ATVPDKIKX0DER', sellerId: '',
  clientId: '', clientSecret: '', refreshToken: '', accessToken: '',
  associateCustomerId: null, remark: ''
})

function openAdd() {
  isEdit.value = false
  editRow.value = {}
  Object.assign(form, {
    shopName: '', platform: 'AMAZON', region: 'NA', marketplaceId: 'ATVPDKIKX0DER', sellerId: '',
    clientId: '', clientSecret: '', refreshToken: '', accessToken: '',
    associateCustomerId: null, remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editRow.value = row
  Object.assign(form, {
    shopName: row.shopName,
    platform: row.platform || 'AMAZON',
    region: row.region || 'NA',
    marketplaceId: row.marketplaceId || 'ATVPDKIKX0DER',
    sellerId: row.sellerId || '',
    clientId: row.clientId || '',
    clientSecret: '',
    refreshToken: '',
    accessToken: '',
    associateCustomerId: row.associateCustomerId,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

function onPlatformChange(p) {
  if (p === 'PDD') {
    form.region = 'CN'
    form.marketplaceId = 'PDD'
  } else {
    form.region = 'NA'
    form.marketplaceId = 'ATVPDKIKX0DER'
  }
}

function onRegionChange(region) {
  if (region === 'NA' && !form.marketplaceId) form.marketplaceId = 'ATVPDKIKX0DER'
}

async function onSave() {
  if (!form.shopName || !form.shopName.trim()) { ElMessage.warning(tt('请填写店铺名称')); return }
  if (!form.associateCustomerId) { ElMessage.warning(tt('请选择关联客户')); return }
  if (!form.clientId || !form.clientId.trim()) { ElMessage.warning(tt('请填写客户端编号')); return }
  if (!form.clientSecret || !form.clientSecret.trim()) { ElMessage.warning(tt('请填写客户端密钥')); return }
  if (form.platform === 'PDD') {
    if (!isEdit.value && (!form.accessToken || !form.accessToken.trim())) { ElMessage.warning(tt('请填写拼多多访问令牌')); return }
  } else {
    if (!isEdit.value && (!form.refreshToken || !form.refreshToken.trim())) { ElMessage.warning(tt('请填写卖家平台刷新令牌')); return }
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateEcomShop(editRow.value.id, { ...form })
    } else {
      await saveEcomShop({ ...form })
    }
    ElMessage.success(tt('保存成功'))
    dialogVisible.value = false
    loadShops()
  } catch (e) { /* 后端已给出中文原因 */ } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadShops()
  listAllCustomer().then(res => { customerList.value = res || [] }).catch(() => { customerList.value = [] })
})
</script>

<style scoped lang="scss">
.ecom-shop {
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
.kgw-search { display: flex; gap: 8px; align-items: center; :deep(.el-input) { width: 200px; } }
.kgw-body { padding: 12px; flex: 1; }
.table-pagination { display: flex; justify-content: flex-end; padding: 12px 0 4px 0; }
.kgw-hint { font-size: 12px; color: #909399; margin-top: 2px; line-height: 1.5; }

.empty-guide { padding: 60px 0; text-align: center; }
.empty-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: #909399; }

// 弹窗：操作按钮在标题栏右侧（不置底）
:deep(.el-dialog__header) { margin-right: 0; }
.dlg-header { display: flex; align-items: center; justify-content: space-between; }
.dlg-title { font-size: 15px; font-weight: 600; color: #303133; }
.dlg-header-actions { display: flex; gap: 8px; }
.dlg-form { margin-bottom: 4px; }

:deep(.el-button--primary) { background-color: #1890ff; border-color: #1890ff; }
:deep(.el-button--primary:hover) { background-color: #40a9ff; border-color: #40a9ff; }
:deep(.el-switch.is-checked .el-switch__core) { background-color: #1890ff; border-color: #1890ff; }
</style>
