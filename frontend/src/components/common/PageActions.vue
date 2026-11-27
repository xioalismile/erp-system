<template>
  <!--
    通用页头操作条（2026-09-20 全站按钮组件化）
    非单据页（CRM / 系统设置 / 库存查询 / 财务配置…）的页头按钮统一走这里，
    每个按钮的权限码由 `permPrefix + action` 自动拼出 → 一处生效于设置中心。

    用法：
      <PageActions
        perm-prefix="crm:pool"
        :actions="[
          { action: 'query',   label: '查询',   icon: 'Search',  type: 'primary', onClick: loadData },
          { action: 'reset',   label: '重置',   icon: 'Refresh', onClick: handleReset },
          { action: 'export',  label: '导出',   icon: 'Download', type: 'success', onClick: handleExport }
        ]"
      />
  -->
  <div class="page-actions" :class="{ 'is-right': align === 'right' }">
    <template v-for="btn in actions" :key="btn.action || btn.label">
      <el-tooltip v-if="btn.tooltip" :content="btn.tooltip" placement="top">
        <el-button
          v-permission="codeOf(btn.action)"
          :type="btn.type || ''"
          :plain="btn.plain === true"
          :link="btn.link === true"
          :circle="btn.circle === true"
          :icon="resolveIcon(btn.icon)"
          :disabled="btn.disabled === true"
          :loading="btn.loading === true"
          @click="btn.onClick && btn.onClick()"
        >{{ btn.circle ? '' : $tf(btn.label) }}</el-button>
      </el-tooltip>
      <el-button
        v-else
        v-permission="codeOf(btn.action)"
        :type="btn.type || ''"
        :plain="btn.plain === true"
        :link="btn.link === true"
        :circle="btn.circle === true"
        :icon="resolveIcon(btn.icon)"
        :disabled="btn.disabled === true"
        :loading="btn.loading === true"
        @click="btn.onClick && btn.onClick()"
      >{{ btn.circle ? '' : $tf(btn.label) }}</el-button>
    </template>
    <slot />
  </div>
</template>

<script setup>
import * as ElIcons from '@element-plus/icons-vue'
import { permCode } from '@/utils/pagePerm'

const props = defineProps({
  // 「模块:对象」前缀（如 crm:pool / system:sys-param）
  permPrefix: { type: String, default: '' },
  // 按钮声明：{ action, label, icon, type, plain, link, circle, disabled, loading, tooltip, onClick }
  actions: { type: Array, default: () => [] },
  align: { type: String, default: 'left' }
})

function resolveIcon (name) {
  if (!name) return undefined
  return ElIcons[name] || undefined
}

function codeOf (action) {
  return permCode(props.permPrefix, action)
}
</script>

<style scoped>
.page-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.page-actions.is-right {
  justify-content: flex-end;
}
</style>
