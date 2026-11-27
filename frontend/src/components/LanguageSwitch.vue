<template>
  <el-dropdown trigger="click" @command="onChange">
    <span class="lang-switch" :class="{ 'is-light': light }" title="语言 / Language">
      <el-icon class="lang-icon"><Switch /></el-icon>
      <span class="lang-label">{{ label }}</span>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="l in SUPPORTED_LANGS"
          :key="l"
          :command="l"
          :disabled="l === lang"
        >
          {{ LANG_LABELS[l] }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { lang, setLang, ensureLangPack, SUPPORTED_LANGS, LANG_LABELS } from '@/i18n'

defineProps({
  /** 深色背景（登录页）下用浅色文字 */
  light: { type: Boolean, default: false }
})

const label = computed(() => LANG_LABELS[lang.value] || lang.value)

async function onChange(l) {
  if (l === lang.value) return
  setLang(l)
  await ensureLangPack(l)
}
</script>

<style scoped>
.lang-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 14px;
  color: var(--erp-header-text, #303133);
  user-select: none;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.2s ease;
}
.lang-switch:hover {
  background: rgba(64, 158, 255, 0.08);
}
.lang-switch.is-light {
  color: #e6fffa;
}
.lang-switch.is-light:hover {
  background: rgba(255, 255, 255, 0.12);
}
.lang-icon {
  font-size: 16px;
}
</style>
