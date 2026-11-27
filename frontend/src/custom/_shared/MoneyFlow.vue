<template>
  <section class="money-flow">
    <header class="mf-head">
      <div class="mf-title">钱流链路</div>
      <div class="mf-hint">订单 → 收款 → 应收 → 逾期 → 高风险，点任意一段看钱卡在哪</div>
    </header>
    <div class="mf-track">
      <template v-for="(n, i) in nodes" :key="n.key">
        <button
          type="button"
          class="mf-node"
          :class="[n.tone, { active: active === n.key }]"
          @click="$emit('select', n.key)"
        >
          <span class="mf-label">{{ n.label }}</span>
          <span class="mf-value" v-if="n.value !== null && n.value !== undefined">
            <span class="mf-currency">¥</span>{{ fmtMoney(n.value) }}
          </span>
          <span class="mf-value mf-na" v-else>暂无数据</span>
          <span class="mf-rate" v-if="n.rate !== null">{{ n.rateLabel }} {{ fmtPercent(n.rate) }}</span>
          <span class="mf-rate muted" v-else>{{ n.rateLabel }}</span>
          <span class="mf-bar"><i :style="{ width: Math.max(3, Math.min(100, n.width)) + '%' }"></i></span>
        </button>
        <span class="mf-arrow" v-if="i < nodes.length - 1" aria-hidden="true">
          <svg viewBox="0 0 26 12"><path d="M1 6 H19 M14 1.5 L20 6 L14 10.5" /></svg>
        </span>
      </template>
    </div>
  </section>
</template>

<script setup>
import { fmtMoney, fmtPercent } from './useCockpit'

defineProps({
  nodes: { type: Array, default: () => [] },
  active: { type: String, default: '' }
})
defineEmits(['select'])
</script>

<style scoped>
.money-flow {
  background: linear-gradient(118deg, #0b1e3a 0%, #123a6b 100%);
  border-radius: 10px;
  padding: 16px 20px 18px;
  color: #e2f1ff;
  box-shadow: 0 8px 24px rgba(11, 30, 58, 0.18);
}
.mf-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}
.mf-title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #7dd3fc;
}
.mf-hint {
  font-size: 12px;
  color: #93b8d8;
}
.mf-track {
  display: flex;
  align-items: stretch;
  overflow-x: auto;
  padding-bottom: 2px;
}
.mf-node {
  flex: 1 1 0;
  min-width: 148px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  background: rgba(13, 42, 77, 0.72);
  border: 1px solid #1e4a7a;
  border-radius: 8px;
  color: #e2f1ff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s, background 0.18s, transform 0.18s;
}
.mf-node:hover {
  border-color: #2f6fa8;
  transform: translateY(-1px);
}
.mf-node.active {
  background: rgba(21, 76, 122, 0.9);
  border-color: #38bdf8;
  box-shadow: 0 0 0 1px #38bdf8, 0 8px 22px rgba(56, 189, 248, 0.22);
}
.mf-label {
  font-size: 12px;
  color: #93b8d8;
}
.mf-value {
  font-size: 23px;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  color: #38bdf8;
}
.mf-currency {
  font-size: 13px;
  font-weight: 500;
  margin-right: 2px;
  opacity: 0.8;
}
.mf-node.good .mf-value { color: #4ade80; }
.mf-node.warn .mf-value { color: #fcd34d; }
.mf-node.bad .mf-value { color: #fca5a5; }
.mf-node.neutral .mf-value { color: #e2f1ff; }
.mf-rate {
  font-size: 11px;
  color: #7dd3fc;
}
.mf-rate.muted {
  color: #64748b;
}
.mf-bar {
  display: block;
  width: 100%;
  height: 3px;
  margin-top: 6px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}
.mf-bar i {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: currentColor;
  transition: width 0.3s;
}
.mf-node.good .mf-bar { color: #4ade80; }
.mf-node.warn .mf-bar { color: #fcd34d; }
.mf-node.bad .mf-bar { color: #fca5a5; }
.mf-node.neutral .mf-bar { color: #93b8d8; }
.mf-node.base .mf-bar { color: #38bdf8; }
.mf-arrow {
  flex: 0 0 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #38bdf8;
  opacity: 0.65;
}
.mf-arrow svg {
  width: 22px;
  height: 11px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
