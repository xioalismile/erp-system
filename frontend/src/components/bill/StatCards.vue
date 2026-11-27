<template>
  <!--
    统计卡片（通用件）
    - 卡片数组元素约定：{ key, label, value, type, icon, filter }
      · key    ：卡片标识；与 activeKey 相同即"当前选中"（整卡高亮）
      · filter ：声明了 filter 的卡片可点击，点击后把该对象作为筛选条件下发给列表；未声明则不可点（无手型光标）
    - 本组件只负责渲染与点击透传，不持有筛选状态：选中态由调用方的 activeKey 控制（再点同一张卡＝取消由调用方处理）
    - 用法：<StatCards :stats="stats" :active-key="activeStatKey" @stat-click="onStatClick" />
  -->
  <div v-if="stats && stats.length" class="stat-cards">
    <div
      v-for="stat in stats"
      :key="stat.key || stat.label"
      class="stat-card"
      :class="{ 'is-clickable': !!stat.filter, 'is-active': !!stat.key && stat.key === activeKey }"
      @click="handleClick(stat)"
    >
      <div class="stat-icon" :class="stat.type">
        <el-icon><component :is="stat.icon" /></el-icon>
      </div>
      <div class="stat-info">
        <div class="stat-value">{{ stat.value }}</div>
        <div class="stat-label">{{ $tf(stat.label) }}</div>
      </div>
      <div class="stat-glow" :class="stat.type"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** 卡片数组：{ key, label, value, type, icon, filter }（filter 存在则可点击） */
  stats: { type: Array, default: () => [] },
  /** 当前选中的卡片 key（用于整卡高亮） */
  activeKey: { type: String, default: '' }
})

const emit = defineEmits(['stat-click'])

function handleClick(stat) {
  if (!stat || !stat.filter) return
  emit('stat-click', { key: stat.key || stat.label, filter: stat.filter })
}
</script>

<style scoped lang="scss">
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: all .3s;

  /* 可点击卡片（声明了筛选条件）：手型光标；当前激活的卡片高亮 */
  &.is-clickable {
    cursor: pointer;
  }

  /* 选中态：整卡背景变浅紫（只改边框看不出来） */
  &.is-active {
    background: #dfe4fd;
    border-color: #4f46e5;
    box-shadow: 0 6px 20px rgba(79, 70, 229, 0.22);

    .stat-value,
    .stat-label {
      color: #3730a3;
      font-weight: 600;
    }
  }

  &.is-active:hover {
    border-color: #4f46e5;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
    border-color: #c7d2fe;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    color: #fff;
    position: relative;
    z-index: 1;

    &.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
    &.warning { background: linear-gradient(135deg, #f59e0b, #f97316); }
    &.info { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
    &.success { background: linear-gradient(135deg, #10b981, #34d399); }
    &.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
  }

  .stat-info { flex: 1; }
  .stat-value {
    font-size: 24px;
    font-weight: 700;
    color: #111827;
    line-height: 1.2;
  }
  .stat-label {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .stat-glow {
    position: absolute;
    right: -20px;
    top: -20px;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    opacity: 0.1;
    filter: blur(20px);

    &.primary { background: #6366f1; }
    &.warning { background: #f59e0b; }
    &.info { background: #3b82f6; }
    &.success { background: #10b981; }
    &.danger { background: #ef4444; }
  }
}

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .stat-cards { grid-template-columns: 1fr; }
}
</style>
