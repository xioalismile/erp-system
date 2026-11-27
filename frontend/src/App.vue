<template>
  <el-config-provider :locale="elLocale" :value-on-clear="null">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { computed, watch, onMounted } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import { lang, ensureLangPack } from '@/i18n'
import { useThemeStore } from '@/stores/theme'

// 多语言（L1）：ElementPlus 组件内置文案随语言切换（弹窗按钮/分页/空态等）
const elLocale = computed(() => (lang.value === 'en' ? en : zhCn))
watch(lang, (l) => { ensureLangPack(l) })

onMounted(() => {
  // 应用本地主题配置（防止页面加载时出现闪烁）
  const themeStore = useThemeStore()
  themeStore.applyTheme()
})
</script>

<style>
body {
  margin: 0;
  font-family: "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
  background: var(--erp-bg-page, #f5f7fa);
}
/* 全局空状态文案优化：el-table / el-select 等组件无数据时统一中文展示 */
.el-table__empty-text {
  color: #909399;
}
.el-select__popper.el-popper .el-select-dropdown__empty {
  padding: 12px 0;
  color: #909399;
  font-size: 13px;
}
</style>
