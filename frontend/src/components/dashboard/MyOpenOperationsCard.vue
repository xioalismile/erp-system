<template>
  <el-card class="my-op-card" shadow="never" v-loading="loading">
    <template #header>
      <div class="card-header">
        <div class="title-wrap" @click="$emit('toggle-collapse')">
          <el-icon class="title-icon" color="#0ea5e9"><List /></el-icon>
          <span class="card-title">{{ $tt("我今天要做的工序") }}</span>
          <span class="op-count">{{ $tt("共") }} {{ rows.length }} {{ $tt("道") }}</span>
          <span v-if="overdueCount" class="op-overdue">{{ $tt("超期") }} {{ overdueCount }} {{ $tt("道") }}</span>
        </div>
        <div class="header-right-wrap">
          <el-icon class="collapse-caret" @click.stop="$emit('toggle-collapse')">
            <component :is="collapsed ? ArrowDown : ArrowUp" />
          </el-icon>
        </div>
      </div>
    </template>

    <div v-show="!collapsed">
      <div v-if="rows.length === 0" class="empty-tip">
        <el-empty
          :description="$tt('你今天没有待做的工序（工序负责人是你且未完工的工序会出现在这里）')"
          :image-size="80"
        />
      </div>
      <el-table v-else :data="rows" size="small" border style="width: 100%">
        <el-table-column prop="orderNo" :label="$tf('工单号')" width="150" show-overflow-tooltip />
        <el-table-column prop="productName" :label="$tf('成品')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operationName" :label="$tf('工序')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="workCenterName" :label="$tf('工作中心')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$tf('计划完工')" width="110">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.overdue }">{{ row.planEndDate || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('状态')" width="110">
          <template #default="{ row }">
            <span :style="{ color: opStatusColor(row.operationStatus), fontWeight: 600 }">
              {{ $dt(row.operationStatus || '待派工') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('是否超期')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" type="danger" size="small" effect="dark">{{ $tt("已超期") }}</el-tag>
            <span v-else class="text-normal">{{ $tt("正常") }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$tf('操作')" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openOperation(row)">{{ $tt("操作") }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 工序操作面板（点击行内「操作」打开：派工/开工/报工/暂停/完工/检验/回退/判废/下推补料） -->
    <OperationPanel v-model="panelVisible" :operation="currentOp" @changed="load" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ArrowUp, ArrowDown, List } from '@element-plus/icons-vue'
import { getMyOpenOperations, listWorkOrderOperation } from '@/api/production/workOrderOperation'
import { opStatusColor } from '@/utils/operationStatus'
import OperationPanel from '@/components/production/OperationPanel.vue'

defineProps({
  collapsed: { type: Boolean, default: false }
})
defineEmits(['toggle-collapse'])

const loading = ref(false)
const rows = ref([])
const overdueCount = computed(() => rows.value.filter(r => r.overdue).length)

const panelVisible = ref(false)
const currentOp = ref(null)

/** 加载"我今天要做的工序"（只读接口；无员工档案时后端返回空列表） */
async function load () {
  loading.value = true
  try {
    const list = await getMyOpenOperations({ days: 7 })
    rows.value = Array.isArray(list) ? list : (list?.records || [])
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 打开工序操作面板：面板需要完整的工序行（数量/负责人/是否质检...），
 * 卡片列表只带展示字段，故按工单取一次完整工序行并按 id 命中；取不到时用卡片行兜底（仍可操作状态流转）。
 */
async function openOperation (row) {
  let full = null
  try {
    const list = await listWorkOrderOperation(row.orderId) || []
    full = list.find(o => o.id === row.operationRowId)
  } catch (e) { /* 兜底：拿不到完整行也不阻断打开面板 */ }
  currentOp.value = full
    ? { ...full, id: full.id }
    : {
        id: row.operationRowId,
        operationName: row.operationName,
        operationStatus: row.operationStatus,
        planQuantity: row.planQuantity
      }
  panelVisible.value = true
}

onMounted(load)
</script>

<style scoped lang="scss">
.my-op-card {
  border-radius: 12px;
  border: none;
  margin-bottom: 0;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f5f7fa;
  }

  :deep(.el-card__body) { padding: 8px 0; }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
  }

  .title-icon { font-size: 18px; }
  .card-title { font-size: 16px; font-weight: 600; color: #303133; }
  .op-count { font-size: 12px; color: #909399; }
  .op-overdue { font-size: 12px; color: #f56c6c; font-weight: 600; }

  .header-right-wrap { display: flex; align-items: center; gap: 12px; }
  .collapse-caret { cursor: pointer; color: #909399; font-size: 16px; }
  .collapse-caret:hover { color: #409eff; }

  .empty-tip { padding: 30px 20px; }

  .text-danger { color: #f56c6c; font-weight: 600; }
  .text-normal { color: #909399; }
}
</style>
