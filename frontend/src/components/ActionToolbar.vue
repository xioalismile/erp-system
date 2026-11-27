<template>
  <!--
    通用按钮工具栏组件 - 7 个标准按钮: 导入/导出/打印/增/删/改/查
    按钮显隐**只**受「设置中心 → 角色权限设置 → 按钮权限」控制（v-permission，按角色全站生效）。
    【2026-10-10 清理】原先还有一套「界面定制 → 按钮显隐」旧机制（按登录人存本机 localStorage、
    只有本组件读取、入口已无路由可达 = 死 UI），已随 stores/buttonConfig.js 一并删除。
    用法:
      <ActionToolbar
        :selected-rows="selectedRows"
        :perm-add="'basedata:unit:add'"
        :perm-edit="'basedata:unit:edit'"
        :perm-delete="'basedata:unit:delete'"
        @query="handleSearch"
        @add="handleAdd"
        @edit="handleEdit"
        @delete="handleBatchDelete"
        @import="handleImport"
        @export="handleExport"
        @print="handlePrint"
      />
  -->
  <div class="erp-toolbar">
    <div class="erp-toolbar-left">
      <template v-for="btn in visibleButtons" :key="btn.key">
        <el-button
          :type="btn.type"
          :icon="resolveIcon(btn.icon)"
          :disabled="isDisabled(btn)"
          v-permission="permMap[btn.permField]"
          @click="emitAction(btn.key)"
        >{{ $tf(btn.label) }}</el-button>
      </template>
      <!-- 额外插槽: 业务页面可放置自定义按钮 -->
      <slot name="extra" />
    </div>
    <div class="erp-toolbar-right">
      <el-button :icon="RefreshRight" circle v-permission="perm('refresh')" @click="emitAction('refresh')" />
      <slot name="right" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  Search, Plus, Edit, Delete, Upload, Download, Printer, RefreshRight
} from '@element-plus/icons-vue'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()

/**
 * 7 个标准按钮定义（顺序即展示顺序，permField 指向下面 permMap 的键）。
 * 原在 stores/buttonConfig.js，随该 store 清理一并内联（唯一消费方就是本组件）。
 */
const STANDARD_BUTTONS = [
  { key: 'query',   label: '查询', icon: 'Search',      type: 'primary', permField: 'permQuery'   },
  { key: 'add',     label: '新增', icon: 'Plus',        type: 'primary', permField: 'permAdd'     },
  { key: 'edit',    label: '修改', icon: 'Edit',        type: 'warning', permField: 'permEdit'    },
  { key: 'delete',  label: '删除', icon: 'Delete',      type: 'danger',  permField: 'permDelete'  },
  { key: 'import',  label: '导入', icon: 'Upload',      type: 'success', permField: 'permImport'  },
  { key: 'export',  label: '导出', icon: 'Download',    type: 'info',    permField: 'permExport'  },
  { key: 'print',   label: '打印', icon: 'Printer',     type: 'info',    permField: 'permPrint'   }
]

const props = defineProps({
  // 选中行 (用于禁用判断: 编辑/删除按钮在未选时禁用)
  selectedRows: { type: Array, default: () => [] },
  // 各按钮权限码
  permAdd:    { type: String, default: '' },
  permEdit:   { type: String, default: '' },
  permDelete: { type: String, default: '' },
  permImport: { type: String, default: '' },
  permExport: { type: String, default: '' },
  permPrint:  { type: String, default: '' },
  permQuery:  { type: String, default: '' },
  // 隐藏指定按钮 (如自动生成单据的页面隐藏新增按钮)
  hideButtons: { type: Array, default: () => [] }
})

const emit = defineEmits(['query', 'add', 'edit', 'delete', 'import', 'export', 'print', 'refresh'])

const permMap = computed(() => ({
  permAdd:    props.permAdd,
  permEdit:   props.permEdit,
  permDelete: props.permDelete,
  permImport: props.permImport,
  permExport: props.permExport,
  permPrint:  props.permPrint,
  permQuery:  props.permQuery
}))

// 图标映射
const ICON_MAP = {
  Search, Plus, Edit, Delete, Upload, Download, Printer
}
function resolveIcon(name) {
  return ICON_MAP[name]
}

// 排除页面显式隐藏的按钮；显隐交给 v-permission（角色按钮权限）
const visibleButtons = computed(() => {
  return STANDARD_BUTTONS.filter(b => !props.hideButtons.includes(b.key))
})

// 编辑/删除按钮在未选中行时禁用 (删除支持单行内按钮触发, 此处批量按钮才需禁用)
function isDisabled(btn) {
  if (btn.key === 'edit' || btn.key === 'delete') {
    return !props.selectedRows || props.selectedRows.length === 0
  }
  return false
}

function emitAction(key) {
  emit(key)
}
</script>
