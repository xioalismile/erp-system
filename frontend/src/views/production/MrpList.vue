<template>
  <div class="erp-page">
    <div class="page-header">
      <h3>MRP 物料需求运算</h3>
      <div class="header-tip">净需求 = 毛需求 - 现有库存 - 在途量 + 安全库存（若 &lt; 0 则置 0）</div>
    </div>

    <!-- 需求录入区 -->
    <el-card shadow="never" class="demand-card">
      <template #header>
        <div class="card-header">
          <span>需求录入</span>
          <el-radio-group v-model="mode" size="small">
            <el-radio-button label="single">单产品</el-radio-button>
            <el-radio-button label="batch">批量</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <!-- 单产品模式 -->
      <el-form v-if="mode === 'single'" :model="singleDemand" label-width="110px" inline>
        <el-form-item label="产品编码">
          <el-input v-model="singleDemand.materialCode" placeholder="如 P001" style="width: 200px" />
        </el-form-item>
        <el-form-item label="需求数量">
          <el-input-number v-model="singleDemand.quantity" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item label="需求日期">
          <el-date-picker v-model="singleDemand.demandDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="calculating" @click="runSingle">执行计算</el-button>
          <el-button @click="resetSingle">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 批量模式 -->
      <div v-else>
        <div class="batch-toolbar">
          <el-button type="success" size="small" @click="addBatchDemand">+ 添加产品</el-button>
          <el-button type="primary" :loading="calculating" @click="runBatch">批量计算</el-button>
          <el-button size="small" @click="batchDemands = []">清空</el-button>
        </div>
        <el-table :data="batchDemands" border size="small" max-height="240">
          <el-table-column label="序号" type="index" width="60" />
          <el-table-column label="产品编码" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.materialCode" placeholder="如 P001" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="需求数量" min-width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="4" controls-position="right" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="需求日期" min-width="170">
            <template #default="{ row }">
              <el-date-picker v-model="row.demandDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 150px" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ $index }">
              <el-button size="small" type="danger" @click="batchDemands.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 计算结果 -->
    <el-card v-if="results.length > 0" shadow="never" class="result-card">
      <template #header>
        <div class="card-header">
          <span>计算结果（共 {{ results.length }} 个产品）</span>
          <el-button size="small" @click="results = []">清空结果</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 物料需求明细 -->
        <el-tab-pane label="物料需求明细" name="requirements">
          <el-table :data="allRequirements" border size="small" show-summary :summary-method="getReqSummary">
            <el-table-column prop="materialCode" label="物料编码" min-width="120" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="specification" label="规格" min-width="120" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="grossRequirement" label="毛需求" width="110" align="right" />
            <el-table-column prop="onHandQuantity" label="现有库存" width="110" align="right" />
            <el-table-column prop="safetyStock" label="安全库存" width="110" align="right" />
            <el-table-column prop="inTransitQuantity" label="在途量" width="100" align="right" />
            <el-table-column prop="allocatedQuantity" label="已分配" width="100" align="right" />
            <el-table-column prop="netRequirement" label="净需求" width="110" align="right">
              <template #default="{ row }">
                <span :class="{ 'net-positive': row.netRequirement > 0 }">{{ row.netRequirement }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="leadTimeDays" label="提前期(天)" width="100" align="center" />
            <el-table-column prop="plannedOrderDate" label="计划下单日" width="120" />
          </el-table>
        </el-tab-pane>

        <!-- 采购建议 -->
        <el-tab-pane :label="`采购建议（${allPurchaseSuggestions.length}）`" name="purchases">
          <el-table :data="allPurchaseSuggestions" border size="small" show-summary :summary-method="getPurSummary">
            <el-table-column prop="materialCode" label="物料编码" min-width="120" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="quantity" label="建议采购量" width="120" align="right" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="suggestedOrderDate" label="建议下单日" width="120" />
            <el-table-column prop="suggestedDeliveryDate" label="建议交货日" width="120" />
            <el-table-column prop="estimatedAmount" label="预估金额" width="130" align="right" />
          </el-table>
        </el-tab-pane>

        <!-- 生产建议 -->
        <el-tab-pane :label="`生产建议（${allProductionSuggestions.length}）`" name="productions">
          <el-table :data="allProductionSuggestions" border size="small">
            <el-table-column prop="materialCode" label="物料编码" min-width="120" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="quantity" label="建议生产量" width="120" align="right" />
            <el-table-column prop="plannedStartDate" label="计划开工日" width="120" />
            <el-table-column prop="plannedFinishDate" label="计划完工日" width="120" />
            <el-table-column prop="subMaterialCount" label="子件数量" width="100" align="center" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- BOM 展开 -->
    <el-card shadow="never" class="bom-card">
      <template #header>
        <div class="card-header">
          <span>BOM 展开（树形预览）</span>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="产品编码">
          <el-input v-model="bomExpandCode" placeholder="如 P001" style="width: 200px" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="bomExpandQty" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="expanding" @click="runExpand">展开</el-button>
        </el-form-item>
      </el-form>
      <el-tree
        v-if="bomTree && Object.keys(bomTree).length > 0"
        :data="[bomTree]"
        :props="treeProps"
        default-expand-all
        node-key="materialCode"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.materialCode }} - {{ data.materialName }}</span>
            <span class="tree-qty">用量: {{ data.usageQuantity }} | 小计: {{ data.totalQuantity }}</span>
          </span>
        </template>
      </el-tree>
      <el-empty v-else description="输入产品编码后点击展开查看 BOM 层级" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { calculateMrp, calculateMrpBatch, expandBom } from '@/api/production/mrp'

const mode = ref('single')
const calculating = ref(false)

// 单产品需求
const singleDemand = reactive({
  materialCode: '',
  quantity: 1,
  demandDate: ''
})

// 批量需求
const batchDemands = ref([])

// 计算结果
const results = ref([])
const activeTab = ref('requirements')

// BOM 展开
const bomExpandCode = ref('')
const bomExpandQty = ref(1)
const expanding = ref(false)
const bomTree = ref({})
const treeProps = { children: 'children', label: 'materialName' }

// 汇总所有产品的需求/建议
const allRequirements = computed(() => {
  const list = []
  for (const r of results.value) {
    if (r.requirements) list.push(...r.requirements)
  }
  return list
})
const allPurchaseSuggestions = computed(() => {
  const list = []
  for (const r of results.value) {
    if (r.purchaseSuggestions) list.push(...r.purchaseSuggestions)
  }
  return list
})
const allProductionSuggestions = computed(() => {
  const list = []
  for (const r of results.value) {
    if (r.productionSuggestions) list.push(...r.productionSuggestions)
  }
  return list
})

function resetSingle() {
  singleDemand.materialCode = ''
  singleDemand.quantity = 1
  singleDemand.demandDate = ''
}

function addBatchDemand() {
  batchDemands.value.push({
    materialCode: '',
    quantity: 1,
    demandDate: ''
  })
}

async function runSingle() {
  if (!singleDemand.materialCode) {
    ElMessage.warning('请输入产品编码')
    return
  }
  calculating.value = true
  try {
    const data = {
      materialCode: singleDemand.materialCode,
      quantity: singleDemand.quantity,
      demandDate: singleDemand.demandDate || undefined
    }
    const res = await calculateMrp(data)
    results.value = [res]
    activeTab.value = 'requirements'
    ElMessage.success('MRP 计算完成')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    calculating.value = false
  }
}

async function runBatch() {
  const valid = batchDemands.value.filter(d => d.materialCode)
  if (valid.length === 0) {
    ElMessage.warning('请至少添加一条有效需求')
    return
  }
  calculating.value = true
  try {
    const demands = valid.map(d => ({
      materialCode: d.materialCode,
      quantity: d.quantity,
      demandDate: d.demandDate || undefined
    }))
    const res = await calculateMrpBatch(demands)
    results.value = res || []
    activeTab.value = 'requirements'
    ElMessage.success(`批量 MRP 计算完成，共 ${results.value.length} 个产品`)
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    calculating.value = false
  }
}

async function runExpand() {
  if (!bomExpandCode.value) {
    ElMessage.warning('请输入产品编码')
    return
  }
  expanding.value = true
  try {
    const data = {
      materialCode: bomExpandCode.value,
      quantity: bomExpandQty.value
    }
    bomTree.value = await expandBom(data)
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    expanding.value = false
  }
}

function getReqSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, index) => {
    if (index === 0) { sums[index] = '合计'; return }
    const prop = col.property
    if (['grossRequirement', 'onHandQuantity', 'safetyStock', 'inTransitQuantity', 'allocatedQuantity', 'netRequirement'].includes(prop)) {
      sums[index] = data.reduce((prev, curr) => {
        const v = Number(curr[prop])
        return isNaN(v) ? prev : prev + v
      }, 0)
      sums[index] = Number(sums[index].toFixed(4))
    } else {
      sums[index] = ''
    }
  })
  return sums
}

function getPurSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, index) => {
    if (index === 0) { sums[index] = '合计'; return }
    const prop = col.property
    if (['quantity', 'estimatedAmount'].includes(prop)) {
      sums[index] = data.reduce((prev, curr) => {
        const v = Number(curr[prop])
        return isNaN(v) ? prev : prev + v
      }, 0)
      sums[index] = Number(sums[index].toFixed(2))
    } else {
      sums[index] = ''
    }
  })
  return sums
}
</script>

<style scoped lang="scss">
.page-header {
  margin-bottom: 16px;
  h3 { margin: 0 0 4px 0; font-size: 18px; }
  .header-tip { color: #909399; font-size: 13px; }
}
.demand-card, .result-card, .bom-card { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }
.batch-toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
.net-positive { color: #F56C6C; font-weight: 600; }
.tree-node { display: flex; justify-content: space-between; width: 100%; padding-right: 16px; }
.tree-qty { color: #909399; font-size: 13px; }
</style>
