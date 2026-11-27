<template>
  <div class="ai-overview">
    <div v-if="items && items.length > 0" class="overview-grid">
      <div
        v-for="it in items"
        :key="it.key"
        class="overview-card"
        @click="emit('open', it.key)"
      >
        <span v-if="it.isNew" class="new-tag">新增</span>
        <div class="card-title">{{ it.label }}</div>
        <div class="card-desc">{{ it.desc }}</div>
        <div class="card-enter">进入 →</div>
      </div>
    </div>
    <el-empty v-else :description="$tt('暂无可用的 AI 设置项')" />
  </div>
</template>

<script setup>
/**
 * AI 设置总览卡片组件：展示 AI 分组下的各项设置入口。
 * 不做跳转路由，点击卡片仅 emit open(key) 由父组件（设置中心）切换右侧内容。
 */
defineProps({
  items: { type: Array, default: () => [] }
})
const emit = defineEmits(['open'])
</script>

<style scoped lang="scss">
.ai-overview {
  padding: 16px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.overview-card {
  position: relative;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
  &:hover {
    border-color: #409eff;
    box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
  }
}
.new-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 12px;
  line-height: 18px;
  padding: 0 6px;
  color: #fff;
  background: #f56c6c;
  border-radius: 999px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.card-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  min-height: 36px;
}
.card-enter {
  margin-top: 10px;
  font-size: 13px;
  color: #409eff;
}
</style>
