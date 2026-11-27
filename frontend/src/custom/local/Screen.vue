<template>
  <div class="screen">
    <header class="sc-head">
      <div class="sc-brand">
        <span class="sc-dot"></span>
        <h1>运营总览</h1>
      </div>
      <div class="sc-clock">
        <span class="sc-date">{{ clock }}</span>
        <span class="sc-updated">数据更新 {{ updatedAt || '--:--:--' }}</span>
      </div>
      <div class="sc-tools">
        <button type="button" class="sc-btn" @click="toggleFull">{{ isFull ? '退出全屏' : '全屏' }}</button>
        <button type="button" class="sc-btn ghost" @click="back">返回</button>
      </div>
    </header>

    <main class="sc-main">
      <MoneyFlow class="sc-flow" :nodes="flow" :active="activeFlow" @select="onSelect" />

      <div class="sc-cols">
        <section class="sc-panel">
          <header class="sc-panel-head">
            <span class="sc-panel-title">今天要做的动作</span>
            <span class="sc-panel-sub" v-if="actions.length">{{ actions.length }} 项<template v-if="actionAmount > 0"> · ¥{{ fmtMoney(actionAmount) }}</template></span>
            <span class="sc-panel-sub" v-else>暂无待办</span>
          </header>
          <div class="sc-act-list" v-if="actions.length">
            <div v-for="a in actions" :key="a.id" class="sc-act" :class="a.urgency">
              <span class="sc-act-type">{{ a.typeLabel }}</span>
              <span class="sc-act-title">{{ a.title }}</span>
              <span class="sc-act-meta">{{ a.meta }}</span>
              <span class="sc-act-amount" v-if="a.amount !== null">¥{{ fmtMoney(a.amount) }}</span>
            </div>
          </div>
          <div class="sc-empty" v-else>今天没有需要处理的动作</div>
        </section>

        <section class="sc-panel">
          <header class="sc-panel-head">
            <span class="sc-panel-title">{{ detail.title }}</span>
            <span class="sc-panel-sub">{{ detail.rows && detail.rows.length ? detail.rows.length + ' 条' : '' }}</span>
          </header>

          <div class="sc-metrics" v-if="detail.metrics">
            <div class="sc-metric" v-for="m in detail.metrics" :key="m.label">
              <span class="sc-metric-label">{{ m.label }}</span>
              <span class="sc-metric-value">
                <template v-if="m.value === null || m.value === undefined">暂无数据</template>
                <template v-else><span v-if="m.money" class="sc-currency">¥</span>{{ m.int ? fmtInt(m.value) : fmtMoney(m.value) }}</template>
              </span>
            </div>
          </div>

          <div class="sc-table-wrap" v-if="detail.rows && detail.rows.length">
            <table class="sc-table">
              <thead>
                <tr><th v-for="c in detail.columns" :key="c.key" :class="{ right: c.align === 'right' }">{{ c.label }}</th></tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in detail.rows" :key="i">
                  <td v-for="c in detail.columns" :key="c.key" :class="{ right: c.align === 'right', money: c.money }">
                    <template v-if="c.money">{{ r[c.key] === null || r[c.key] === undefined ? '暂无数据' : '¥' + fmtMoney(r[c.key]) }}</template>
                    <template v-else>{{ r[c.key] === null || r[c.key] === undefined || r[c.key] === '' ? '暂无数据' : r[c.key] }}{{ c.suffix || '' }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="sc-empty" v-else-if="detail.empty">{{ detail.empty }}</div>
        </section>
      </div>
    </main>

    <footer class="sc-foot">
      仅已生效单据 · 应收未收含历史未结清 · 每 30 秒自动刷新 · 数据更新 {{ updatedAt || '--:--:--' }}
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import MoneyFlow from '@custom-shared/MoneyFlow.vue'
import { useCockpit, fmtMoney, fmtInt } from '@custom-shared/useCockpit'

const router = useRouter()
const { activeFlow, flow, actions, detail, actionAmount, updatedAt, load, startAuto } = useCockpit()

const clock = ref('')
const isFull = ref(false)
let clockTimer = null

function tick() {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  clock.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 星期${week} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

onMounted(async () => {
  tick()
  clockTimer = setInterval(tick, 1000)
  await load()
  startAuto()
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  clockTimer = null
})

function onSelect(key) {
  activeFlow.value = key
}

async function toggleFull() {
  try {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen()
      isFull.value = true
    } else {
      await document.exitFullscreen()
      isFull.value = false
    }
  } catch (e) {
    isFull.value = !!document.fullscreenElement
  }
}

function back() {
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {})
  router.push({ path: '/board/cockpit' })
}
</script>

<style scoped>
.screen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  padding: 14px 20px 10px;
  box-sizing: border-box;
  color: #e2f1ff;
  background: radial-gradient(circle at 20% 0%, #123a6b 0%, #0b1e3a 55%, #081729 100%);
  overflow: hidden;
}
.sc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #1e4a7a;
}
.sc-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sc-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 10px #38bdf8;
}
.sc-brand h1 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 4px;
  font-weight: 700;
  color: #7dd3fc;
}
.sc-clock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.sc-date {
  font-size: 13px;
  color: #93b8d8;
  font-variant-numeric: tabular-nums;
}
.sc-updated {
  font-size: 11px;
  color: #64748b;
}
.sc-tools {
  display: flex;
  gap: 8px;
}
.sc-btn {
  background: rgba(56, 189, 248, 0.1);
  border: 1px solid #1e4a7a;
  color: #7dd3fc;
  font-size: 12px;
  padding: 5px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.16s, border-color 0.16s;
}
.sc-btn:hover {
  background: rgba(56, 189, 248, 0.2);
  border-color: #38bdf8;
}
.sc-btn.ghost {
  color: #93b8d8;
}
.sc-main {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
  min-height: 0;
}
.sc-flow {
  flex: 0 0 auto;
}
.sc-cols {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}
.sc-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: rgba(13, 42, 77, 0.66);
  border: 1px solid #1e4a7a;
  border-radius: 8px;
  padding: 12px 14px 14px;
}
.sc-panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(30, 74, 122, 0.8);
  margin-bottom: 10px;
}
.sc-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #7dd3fc;
  letter-spacing: 1px;
}
.sc-panel-sub {
  font-size: 11px;
  color: #93b8d8;
}
.sc-act-list {
  flex: 1 1 auto;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}
.sc-act {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  background: rgba(11, 30, 58, 0.55);
  border-left: 3px solid #38bdf8;
  border-radius: 4px;
}
.sc-act.bad { border-left-color: #fca5a5; }
.sc-act.warn { border-left-color: #fcd34d; }
.sc-act-type {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 9px;
  background: rgba(56, 189, 248, 0.12);
  color: #7dd3fc;
  white-space: nowrap;
}
.sc-act.bad .sc-act-type { background: rgba(252, 165, 165, 0.14); color: #fca5a5; }
.sc-act.warn .sc-act-type { background: rgba(252, 211, 77, 0.14); color: #fcd34d; }
.sc-act-title {
  font-size: 13px;
  color: #e2f1ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sc-act-meta {
  font-size: 11px;
  color: #93b8d8;
  white-space: nowrap;
}
.sc-act-amount {
  font-size: 14px;
  font-weight: 700;
  color: #fca5a5;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.sc-metrics {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.sc-metric {
  flex: 1 1 0;
  min-width: 110px;
  background: rgba(11, 30, 58, 0.55);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.sc-metric-label {
  font-size: 11px;
  color: #93b8d8;
}
.sc-metric-value {
  font-size: 16px;
  font-weight: 700;
  color: #38bdf8;
  font-variant-numeric: tabular-nums;
}
.sc-currency {
  font-size: 10px;
  margin-right: 1px;
  color: #64748b;
}
.sc-table-wrap {
  flex: 1 1 auto;
  overflow: auto;
  min-height: 0;
}
.sc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.sc-table th {
  position: sticky;
  top: 0;
  background: #0d2a4d;
  color: #93b8d8;
  font-weight: 500;
  text-align: left;
  padding: 7px 8px;
  border-bottom: 1px solid #1e4a7a;
  white-space: nowrap;
}
.sc-table td {
  padding: 7px 8px;
  border-bottom: 1px solid rgba(30, 74, 122, 0.5);
  color: #e2f1ff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}
.sc-table .right { text-align: right; }
.sc-table td.money { color: #fca5a5; font-variant-numeric: tabular-nums; }
.sc-empty {
  padding: 30px 12px;
  text-align: center;
  font-size: 12px;
  color: #64748b;
}
.sc-foot {
  flex: 0 0 auto;
  padding-top: 8px;
  text-align: center;
  font-size: 11px;
  color: #475569;
}
@media (max-width: 1100px) {
  .sc-cols { grid-template-columns: 1fr; }
}
</style>
