<!-- @feature FEAT-BILL-BUTTON-PERM -->
<template>
  <!--
    列表页页头按钮通用组件（2026-09-19）
    目的：把"新增/打印/导出/导入/刷新"从各页手写 el-button 的重复劳动里抽出来，
         按钮码统一由 permPrefix（对象码，如 sale:orders）派生，权限控制一处生效、后续零适配成本。
    默认口径：v-permission 规则③——该对象下没有显式按钮配置时，命中菜单码即放行（能看菜单就有全部按钮）。
    各页用法：
      <template #header-actions="{ selectedRows: sel }">
        <BillHeaderActions perm-prefix="sale:orders" :add-text="$tt('新增订单')" :print-disabled="!sel.length"
          @add="$router.push('/sale/orders/new')" @print="handleBatchPrint" @export="handleExport" @import="handleImport" />
      </template>
  -->
  <el-button
    v-if="showAdd"
    type="primary"
    class="bill-header-add-glow"
    v-permission="perm('add')"
    @click="$emit('add')"
  >
    <el-icon><Plus /></el-icon>{{ addText }}
  </el-button>
  <el-button
    v-if="showPrint"
    :icon="Printer"
    v-permission="perm('print')"
    :disabled="printDisabled"
    @click="$emit('print')"
  >{{ $tt("打印") }}</el-button>
  <el-button
    v-if="showExport"
    :type="exportType"
    :icon="Download"
    v-permission="perm('export')"
    @click="$emit('export')"
  >{{ $tt("导出") }}</el-button>
  <el-button
    v-if="showImport"
    type="warning"
    :icon="Upload"
    v-permission="perm('import')"
    @click="$emit('import')"
  >{{ $tt("导入") }}</el-button>
  <!-- 刷新不是权限动作（看得到页面就能刷新），不加权限码 -->
  <el-button
    v-if="showRefresh"
    :icon="Refresh"
    @click="$emit('refresh')"
  >{{ $tt("刷新") }}</el-button>
  <!-- 页面特有的其它按钮直接塞进插槽（也会继承页头样式） -->
  <slot />
</template>

<script setup>
import { Plus, Printer, Download, Upload, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  /** 对象码（按钮码前缀），如 sale:orders；传空 = 该页未登记权限码，不做判定 */
  permPrefix: { type: String, default: '' },
  /** 新增按钮文案（如"新增订单"） */
  addText: { type: String, default: '' },
  showAdd: { type: Boolean, default: true },
  showPrint: { type: Boolean, default: true },
  showExport: { type: Boolean, default: true },
  showImport: { type: Boolean, default: true },
  showRefresh: { type: Boolean, default: false },
  /** 打印按钮禁用（通常 = 未勾选行） */
  printDisabled: { type: Boolean, default: false },
  /** 导出按钮类型（各页历史样式不一，默认 success 与销售订单页一致） */
  exportType: { type: String, default: 'success' }
})
defineEmits(['add', 'print', 'export', 'import', 'refresh'])

/**
 * 按钮码 = 对象码 + 动作（与设置中心 RoleList.buttonCode、后端 ACTION_BUTTON_SUFFIX 同源）。
 * 返回空串时 v-permission 不判定（放行）——保证无码页面按钮照常可见。
 */
function perm(action) {
  return props.permPrefix ? `${props.permPrefix}:${action}` : ''
}
</script>

<style scoped>
/*
  新增按钮的发光样式自带一份：
  本组件是**多根节点（fragment）**，父页面的 scoped 样式不会作用到组件内部元素
  （Vue 只把父 scoped 属性打在子组件根节点上，多根节点时无处可打），
  原先各页写在页面里的 `.btn-primary-glow` 会失效 → 故在组件内复刻，保证视觉与改造前一致。
*/
.bill-header-add-glow {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.bill-header-add-glow:hover {
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
  transform: translateY(-1px);
}
</style>
