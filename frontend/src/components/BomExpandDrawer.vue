<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="72%"
    destroy-on-close
    class="bom-expand-drawer"
  >
    <div class="expand-toolbar">
      <el-radio-group v-model="viewMode">
        <el-radio-button value="tree">{{ $tt("树形展开") }}</el-radio-button>
        <el-radio-button value="flat">{{ $tt("扁平汇总") }}</el-radio-button>
      </el-radio-group>
      <div class="qty-box">
        <span class="qty-label">{{ $tt("母件数量：") }}</span>
        <el-input-number v-model="quantity" :min="0.0001" :step="1" :precision="scaleFor('qty')" size="small"
          style="width:140px" @change="loadData" />
      </div>
      <div v-if="rootInfo" class="root-info">
        母件：<b>{{ rootInfo.materialCode }}</b> {{ rootInfo.materialName }}
        <el-tag v-if="rootInfo.bomCode" size="small" type="primary" style="margin-left:8px">BOM: {{ rootInfo.bomCode }}</el-tag>
      </div>
    </div>

    <div v-loading="loading" class="expand-body">
      <!-- 树形展开 -->
      <template v-if="viewMode === 'tree'">
        <el-empty v-if="!loading && treeData.length === 0" :description="$tt('该物料暂无生效BOM结构')" />
        <el-table v-else-if="!loading" :data="treeData" row-key="rowKey" border
          default-expand-all :tree-props="{ children: 'children' }" size="small"
          :indent="18" max-height="calc(100vh - 220px)">
          <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="150" />
          <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="160" show-overflow-tooltip />
          <el-table-column prop="specification" :label="$tf('规格型号')" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.specification || '' }}</template>
          </el-table-column>
          <el-table-column :label="$tf('属性')" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.level === 1" type="success" size="small">{{ $tt("母件") }}</el-tag>
              <el-tag v-else-if="row.hasChildren" type="warning" size="small">{{ $tt("半成品") }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $tt("子件") }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bomNo" :label="$tf('该件BOM编号')" min-width="150">
            <template #default="{ row }">
              <span v-if="row.bomNo" class="bom-no-link">{{ row.bomNo }}</span>
              <span v-else style="color:#c0c4cc"></span>
            </template>
          </el-table-column>
          <el-table-column prop="usageQuantity" :label="$tf('单件用量')" width="100" align="right">
            <template #default="{ row }">{{ formatQty(row.usageQuantity) }}</template>
          </el-table-column>
          <el-table-column prop="totalQuantity" :label="$tf('合计用量')" width="110" align="right">
            <template #default="{ row }">{{ formatQty(row.totalQuantity) }}</template>
          </el-table-column>
          <el-table-column prop="unit" :label="$tf('单位')" width="70" align="center">
            <template #default="{ row }">{{ row.unit || '' }}</template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 扁平汇总 -->
      <template v-else>
        <el-empty v-if="!loading && flatData.length === 0" :description="$tt('该物料暂无生效BOM结构')" />
        <el-table v-else-if="!loading" :data="flatData" border size="small"
          max-height="calc(100vh - 220px)" show-summary :summary-method="flatSummary">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="materialCode" :label="$tf('物料编码')" min-width="150" />
          <el-table-column prop="materialName" :label="$tf('物料名称')" min-width="160" show-overflow-tooltip />
          <el-table-column prop="specification" :label="$tf('规格型号')" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.specification || '' }}</template>
          </el-table-column>
          <el-table-column prop="totalQuantity" :label="$tf('合计用量')" width="120" align="right">
            <template #default="{ row }">{{ formatQty(row.totalQuantity) }}</template>
          </el-table-column>
          <el-table-column prop="unit" :label="$tf('单位')" width="70" align="center">
            <template #default="{ row }">{{ row.unit || '' }}</template>
          </el-table-column>
          <el-table-column prop="purchasePrice" :label="$tf('采购单价')" width="100" align="right">
            <template #default="{ row }">{{ row.purchasePrice != null ? Number(row.purchasePrice).toFixed(2) : '' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { tt } from '@/i18n'
import { usePrecision } from '@/composables/usePrecision'

// 数量显示位数统一读设置中心「数量小数位数」（2026-11-10）
const { scaleFor, formatWith, trimZero } = usePrecision()

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 母件物料编码（与bomId二选一） */
  materialCode: { type: String, default: '' },
  /** 指定BOM ID（从列表展开当前行BOM时传入，优先于物料编码） */
  bomId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const viewMode = ref('tree')
const quantity = ref(1)
const loading = ref(false)
const treeData = ref([])
const flatData = ref([])
const rootInfo = ref(null)
let loadSeq = 0

const drawerTitle = computed(() => `BOM展开 ${rootInfo.value ? '- ' + (rootInfo.value.materialName || rootInfo.value.materialCode) : ''}`)

watch(() => props.modelValue, (v) => {
  if (v && (props.materialCode || props.bomId)) {
    quantity.value = 1
    viewMode.value = 'tree'
    loadData()
  }
})

async function loadData() {
  if (!props.materialCode && !props.bomId) return
  const seq = ++loadSeq
  loading.value = true
  try {
    const params = { quantity: quantity.value }
    if (props.bomId) {
      params.bomId = props.bomId
    } else {
      params.materialCode = props.materialCode
    }
    const [treeRes, flatRes] = await Promise.all([
      api.get('/masterdata/bom/expand', { params }),
      api.get('/masterdata/bom/flatten', { params: { materialCode: props.materialCode || '', quantity: quantity.value } })
    ])
    if (seq !== loadSeq) return
    treeData.value = normalizeTree(treeRes, 1, '')
    rootInfo.value = treeRes && (treeRes.materialCode || treeRes.bomCode) ? treeRes : null
    flatData.value = flatRes || []
    if (!treeRes || Object.keys(treeRes).length === 0) {
      ElMessage.info(tt('未找到该物料的BOM结构'))
    }
  } catch (e) {
    if (seq === loadSeq) {
      ElMessage.error(e?.response?.data?.message || tt('BOM展开失败'))
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

/** 后端树 → el-table 树数据：统一 bomNo 字段并生成唯一 rowKey */
function normalizeTree(node, level, parentKey) {
  if (!node || !node.materialCode) return []
  const isRoot = level === 1
  const rowKey = `${parentKey}|${node.materialCode}`
  const row = {
    rowKey,
    materialCode: node.materialCode,
    materialName: node.materialName || '',
    specification: node.specification || '',
    unit: node.unit || '',
    level: node.level || level,
    usageQuantity: isRoot ? null : node.usageQuantity,
    totalQuantity: isRoot ? node.quantity : node.totalQuantity,
    bomNo: isRoot ? (node.bomCode || '') : (node.childBomCode || ''),
    children: []
  }
  const children = node.children || []
  row.children = children.map(c => normalizeTree(c, level + 1, rowKey)).filter(r => r)
  row.hasChildren = row.children.length > 0
  if (!row.hasChildren) delete row.children
  return row
}

function formatQty(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return ''
  return formatWith(n, scaleFor('qty'), trimZero.value)
}

function flatSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    if (col.property === 'totalQuantity') {
      sums[i] = formatWith(data.reduce((s, r) => s + Number(r.totalQuantity || 0), 0), scaleFor('qty'), trimZero.value)
    } else {
      sums[i] = ''
    }
  })
  return sums
}
</script>

<style scoped>
.expand-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.qty-box {
  display: flex;
  align-items: center;
}
.qty-label {
  font-size: 13px;
  color: #606266;
}
.root-info {
  font-size: 13px;
  color: #606266;
  margin-left: auto;
}
.expand-body {
  min-height: 200px;
}
.bom-no-link {
  color: #409eff;
  font-family: monospace;
}
:deep(.el-table .el-table__row--level-1 .el-table__cell) {
  background: #fafcff;
}
:deep(.el-table .el-table__row--level-2 .el-table__cell) {
  background: #f7f9fc;
}
</style>
