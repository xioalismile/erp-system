<!-- @feature FEAT-BILL-BATCH -->
<template>
  <!-- 列表页批量操作条（2026-09-19）：勾选行后出现，未勾选时不渲染（不占高度）
       位置：搜索条与表格之间；页头的新增/打印/导出/导入按钮与搜索条一行完全不动 -->
  <div v-if="selectedRows.length" class="bill-batch-bar">
    <span class="bb-count">
      {{ $tt('已选') }} <b>{{ selectedRows.length }}</b> {{ $tt('条') }}
    </span>
    <el-button link type="primary" class="bb-link" @click="$emit('select-all')">{{ $tt('全选本页') }}</el-button>
    <el-button link class="bb-link" @click="$emit('clear')">{{ $tt('清空') }}</el-button>
    <span class="bb-sep"></span>

    <template v-for="a in actions" :key="a.key">
      <el-tooltip :content="reasonOf(a)" :disabled="!reasonOf(a)" placement="top">
        <span class="bb-btn-wrap">
          <el-button
            :type="a.type || 'default'"
            :disabled="!!reasonOf(a) || loading"
            v-permission="a.perm || ''"
            @click="$emit('action', a.key)"
          >{{ $tf(a.label) }}</el-button>
        </span>
      </el-tooltip>
    </template>

    <span v-if="loading && progress && progress.total" class="bb-progress">
      {{ $tt('处理中') }} {{ progress.done }}/{{ progress.total }}
    </span>
  </div>
</template>

<script setup>
/**
 * 批量操作条通用组件。
 *
 * 使用方式（页面只需声明动作，不含 UI）：
 *   <BillListPage :batch-actions="batchActions" @batch-action="onBatchAction">
 *
 * 动作声明结构：
 *   { key:'submit', label:'批量提交', type:'primary', perm:'sale:orders:audit',
 *     disabledReason: (rows) => rows.some(...) ? '原因' : '' }
 *
 * 口径说明（与立项登记表一致）：
 *  - 按钮按权限显隐：perm 为空 = 不校验（沿用现状）；有码则用 v-permission 判定，无权限直接移除
 *  - 状态冲突：disabledReason(rows) 返回非空 → 按钮置灰 + 悬浮说明原因
 *    （批量场景下这是"隐藏偏好"的有意例外：一批数据状态是混的，全隐藏会让用户找不到入口）
 */
const props = defineProps({
  selectedRows: { type: Array, default: () => [] },
  actions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  progress: { type: Object, default: () => ({ done: 0, total: 0 }) }
})
const emit = defineEmits(['action', 'clear', 'select-all'])

/** 计算某动作在当前选中行下的禁用原因（空字符串 = 可用） */
function reasonOf(action) {
  if (!action || typeof action.disabledReason !== 'function') return ''
  try {
    return String(action.disabledReason(props.selectedRows || []) || '')
  } catch (e) {
    return ''
  }
}
</script>

<style scoped>
.bill-batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--erp-toolbar-gap, 6px);
  margin-bottom: var(--erp-toolbar-mb, 8px);
  padding: var(--erp-table-cell-pad-y, 5px) var(--erp-card-padding, 10px);
  border: 1px solid #a9c4ff;
  border-radius: 8px;
  background: #f2f7ff;
}
.bb-count { font-size: 14px; color: #1a1759; }
.bb-count b { font-size: 16px; }
.bb-link { padding: 0; }
.bb-sep { width: 1px; height: 16px; background: #d9dde6; }
.bb-btn-wrap { display: inline-flex; }
.bb-progress { margin-left: auto; font-size: 12px; color: #52525b; }
</style>
