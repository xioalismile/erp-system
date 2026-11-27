<template>
  <div class="erp-page">
    <el-card shadow="never" class="assistant-settings-card">
      <div class="page-header">
        <h2 class="page-title">{{ $tt("AI 助手设置") }}</h2>
        <div class="page-desc">
          管理小楷助手使用的模型服务商、密钥与展示播报偏好。
        </div>
      </div>
      <AiAssistantSettingsPanel />
    </el-card>
  </div>
</template>

<script setup>
/**
 * 设置中心 → AI → AI 助手设置：薄壳页面。
 * 面板内容与小助手 ⚙️ 设置弹窗共用同一组件，保证两处完全一致。
 */
import AiAssistantSettingsPanel from '@/components/Ai/AiAssistantSettingsPanel.vue'

// 设置中心以 <component :is="..." :embedded /> 内嵌渲染，声明该 prop 避免落到 DOM 属性上
defineProps({ embedded: { type: Boolean, default: false } })
</script>

<style scoped lang="scss">
.erp-page { padding: 0; }
.assistant-settings-card {
  max-width: 1000px;
  margin: 20px auto;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 22px;
  color: #303133;
  margin: 0 0 8px 0;
  font-weight: 600;
}
.page-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
</style>
