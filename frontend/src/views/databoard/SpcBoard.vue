<template>
  <div class="spc-page">
    <!-- 科技风头区：深色渐变 + 发光 KPI -->
    <div class="spc-head">
      <div class="spc-head-top">
        <div class="spc-title">
          <span class="spc-title-text">{{ $tt("SPC质量看板") }}</span>
          <span class="spc-badge">{{ $tt("统计过程控制 · 标准公式") }}</span>
        </div>
        <div class="spc-sub">{{ $tt("监控工序质量波动 · 控制限判异 · 过程能力评价（Cp / Cpk 由服务端按标准公式统一计算）") }}</div>
      </div>

      <div class="spc-filters">
        <label class="f-label">{{ $tt("检验项") }}</label>
        <el-select v-model="item" size="default" class="f-item" :placeholder="$tt('请选择检验项')" @change="loadAnalysis">
          <el-option v-for="it in items" :key="it.item" :label="it.item" :value="it.item">
            <span>{{ it.item }}</span>
            <span class="f-opt-hint">
              {{ it.count }} {{ $tt("条") }}
              <template v-if="it.usl !== null || it.lsl !== null">
                · {{ it.lsl !== null ? it.lsl : '—' }} ~ {{ it.usl !== null ? it.usl : '—' }}
              </template>
            </span>
          </el-option>
        </el-select>
        <el-radio-group v-model="period" size="default" @change="reload">
          <el-radio-button value="day">{{ $tt("今日") }}</el-radio-button>
          <el-radio-button value="week">{{ $tt("本周") }}</el-radio-button>
          <el-radio-button value="month">{{ $tt("本月") }}</el-radio-button>
          <el-radio-button value="quarter">{{ $tt("本季") }}</el-radio-button>
          <el-radio-button value="year">{{ $tt("本年") }}</el-radio-button>
        </el-radio-group>
        <el-button type="primary" plain :icon="Refresh" :loading="loading" @click="reload">{{ $tt("刷新") }}</el-button>
      </div>

      <el-row :gutter="12" class="kpi-row">
        <el-col :span="6">
          <div class="kpi">
            <div class="kpi-label">{{ $tt("检验批次") }}</div>
            <div class="kpi-value">{{ fmtInt(kpi.batches) }}</div>
            <div class="kpi-foot">{{ periodLabel }} · {{ $tt("质检记录数") }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi">
            <div class="kpi-label">{{ $tt("批次合格率") }}</div>
            <div class="kpi-value">{{ fmtPct(kpi.passRate) }}</div>
            <div class="kpi-foot">{{ $tt("合格批次 ÷ 检验批次") }}<template v-if="kpi.judgedBatches !== undefined && kpi.judgedBatches !== null">（{{ $tt("已判定") }} {{ fmtInt(kpi.judgedBatches) }} {{ $tt("批") }}）</template></div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi">
            <div class="kpi-label">{{ $tt("不合格批次") }}</div>
            <div class="kpi-value" :class="{ 'kpi-warn': nvl(kpi.defectBatches) > 0 }">{{ fmtInt(kpi.defectBatches) }}</div>
            <div class="kpi-foot">{{ $tt("判定不合格 · 含不合格数") }} {{ fmtQty(kpi.defectQty) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi">
            <div class="kpi-label">{{ $tt("过程能力 Cpk") }}</div>
            <div class="kpi-value">{{ cpk === null ? "—" : fmtNum(cpk, 2) }}</div>
            <div class="kpi-foot">
              <span v-if="cp !== null">Cp {{ fmtNum(cp, 2) }} · </span>
              <span :class="levelClass">{{ cpkLevel || $tt("不适用") }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 空态引导 -->
    <div v-if="!loading && !items.length" class="empty-wrap">
      <div class="empty-card">
        <h3>{{ $tt("当前筛选范围内还没有可分析的检验记录") }}</h3>
        <p>
          {{ $tt("SPC 看板的数据来自工序质检：先在生产工单的工序面板完成报工并填写质检，检验项有数值型实测值后即可出控制图。") }}<br />
          {{ $tt("文本型检验项（如外观描述）不参与 SPC 计算，可在「工序质检报表」中查看。") }}
        </p>
        <el-button type="primary" @click="widen">{{ $tt("放宽到本年再试") }}</el-button>
      </div>
    </div>

    <!-- 主体 -->
    <div v-else class="spc-body">
      <el-row :gutter="12">
        <el-col :span="16">
          <div class="card">
            <div class="card-head">
              <span class="card-title">{{ $tt("控制图") }}</span>
              <span class="card-sub">{{ chartModeText }}</span>
              <div class="chart-tabs">
                <button :class="{ on: chartTab === 'xbar' }" @click="switchTab('xbar')">{{ $tt("均值-极差图 X̄-R") }}</button>
                <button :class="{ on: chartTab === 'imr' }" @click="switchTab('imr')">{{ $tt("单值-移动极差 I-MR") }}</button>
                <button :class="{ on: chartTab === 'p' }" @click="switchTab('p')">{{ $tt("不合格品率 P图") }}</button>
              </div>
            </div>
            <div v-if="chartTab === 'xbar'">
              <div ref="mainChartEl" class="chart-box"></div>
              <div ref="rangeChartEl" class="chart-box chart-box-sm"></div>
              <div class="chart-note">{{ $tt("上：均值图（中心线 X̄̄，控制限 X̄̄±A₂R̄）") }}　{{ $tt("下：极差图（控制限 D₃R̄ ~ D₄R̄）") }}</div>
            </div>
            <div v-else-if="chartTab === 'imr'">
              <div ref="mainChartEl" class="chart-box"></div>
              <div ref="rangeChartEl" class="chart-box chart-box-sm"></div>
              <div class="chart-note">{{ $tt("上：单值图（控制限 X̄±2.66·MR̄）") }}　{{ $tt("下：移动极差图（UCL=3.267·MR̄）") }}</div>
            </div>
            <div v-else>
              <div ref="mainChartEl" class="chart-box"></div>
              <div class="chart-note">{{ $tt("各批次不合格品率与逐批控制限 p̄±3√(p̄(1−p̄)/n)，n 不同则限不同") }}</div>
            </div>
            <div class="chart-note" v-if="skippedDirty > 0">
              {{ $tt("已剔除无法解析为数字的实测值") }} {{ skippedDirty }} {{ $tt("条，避免拉偏控制限") }}
            </div>
          </div>

          <div class="card" style="margin-top: 12px">
            <div class="card-head">
              <span class="card-title">{{ $tt("实测值趋势") }}</span>
              <span class="card-sub">{{ $tt("逐检验批次实测值 · 波浪趋势") }}（{{ points.length }} {{ $tt("点") }}）</span>
            </div>
            <div ref="waveChartEl" class="chart-box"></div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="card">
            <div class="card-head">
              <span class="card-title">{{ $tt("过程能力") }}</span>
              <span class="card-sub">{{ $tt("σ̂=R̄/d₂") }}</span>
            </div>
            <div class="cap-grid">
              <span class="cap-label">Cp</span><span class="cap-value">{{ cp === null ? "—" : fmtNum(cp, 2) }}</span>
              <span class="cap-label">Cpk</span><span class="cap-value">{{ cpk === null ? "—" : fmtNum(cpk, 2) }}</span>
              <span class="cap-label">{{ $tt("能力等级") }}</span>
              <span><span class="lv-chip" :class="levelClass">{{ cpkLevel || $tt("不适用") }}</span></span>
            </div>
            <div ref="histChartEl" class="chart-box chart-box-sm"></div>
            <div class="chart-note">
              {{ $tt("实测值分布与规格限（紫虚线：下限 / 上限）") }}
              <template v-if="usl === null && lsl === null">　{{ $tt("该检验项未设置规格上下限") }}</template>
            </div>
          </div>

          <div class="card" style="margin-top: 12px">
            <div class="card-head">
              <span class="card-title">{{ $tt("判异提示") }}</span>
              <span class="card-sub">{{ $tt("八条判异规则 · 出界自动标注") }}</span>
            </div>
            <div v-if="!findings.length" class="rule-ok">{{ $tt("未触发判异规则，过程受控") }}</div>
            <div v-for="(f, idx) in findings" :key="idx" class="rule-item">
              <div class="rule-name">{{ f.ruleName }}</div>
              <div class="rule-detail">
                {{ $tt("第") }} {{ f.idx + 1 }} {{ $tt("点") }}
                <template v-if="f.label"> · {{ f.label }}</template>
                · {{ $tt("数值") }} {{ fmtNum(f.value, 3) }}
                <template v-if="f.ucl !== undefined && f.ucl !== null">
                  （{{ $tt("限") }} {{ fmtNum(f.ucl, 3) }} / {{ fmtNum(f.lcl, 3) }}）
                </template>
              </div>
            </div>
          </div>

          <div class="card" style="margin-top: 12px">
            <div class="card-head">
              <span class="card-title">{{ $tt("检验项信息") }}</span>
              <span class="card-sub">{{ $tt("来自质检模板") }}</span>
            </div>
            <div class="meta-row"><span>{{ $tt("检验项") }}</span><span>{{ item || "—" }}</span></div>
            <div class="meta-row"><span>{{ $tt("规格上限") }}</span><span>{{ usl === null ? "—" : fmtNum(usl, 3) }}</span></div>
            <div class="meta-row"><span>{{ $tt("规格下限") }}</span><span>{{ lsl === null ? "—" : fmtNum(lsl, 3) }}</span></div>
            <div class="meta-row"><span>{{ $tt("子组大小") }}</span><span>{{ subgroup || 5 }}</span></div>
            <div class="meta-row"><span>{{ $tt("数据来源") }}</span><span>{{ $tt("质检记录 · 检验明细实测值") }}</span></div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import echarts from '@/utils/echartsCore'
import { getSpcItems, getSpcAnalysis } from '@/api/dashboard/spc'

const PERIOD_LABEL = { day: '今日', week: '本周', month: '本月', quarter: '本季', year: '本年' }

const period = ref('month')
const item = ref('')
const items = ref([])
const data = ref({})
const loading = ref(false)
const chartTab = ref('xbar')

const mainChartEl = ref(null)
const rangeChartEl = ref(null)
const waveChartEl = ref(null)
const histChartEl = ref(null)
let charts = {}

const kpi = computed(() => data.value.kpi || {})
const cpk = computed(() => (data.value.cpk === undefined ? null : data.value.cpk))
const cp = computed(() => (data.value.cp === undefined ? null : data.value.cp))
const cpkLevel = computed(() => data.value.cpkLevel || '')
const usl = computed(() => (data.value.usl === undefined ? null : data.value.usl))
const lsl = computed(() => (data.value.lsl === undefined ? null : data.value.lsl))
const points = computed(() => data.value.points || [])
const skippedDirty = computed(() => data.value.skippedDirty || 0)
const subgroup = computed(() => {
  const x = data.value.xbarR
  return x ? x.subgroup : null
})
const periodLabel = computed(() => PERIOD_LABEL[period.value] || '本月')
const chartModeText = computed(() => {
  const mode = data.value.chartMode
  if (mode === 'xbarR') return `${$ttLocal('子组大小 n=')}${data.value.xbarR?.subgroup || 5} · ${$ttLocal('均值-极差受控图')}`
  if (mode === 'imr') return $ttLocal('有效点不足以分组，已自动降级为单值-移动极差图')
  return ''
})
const levelClass = computed(() => {
  const v = cpk.value
  if (v === null) return 'lv-none'
  if (v >= 1.33) return 'lv-ok'
  if (v >= 1.0) return 'lv-mid'
  return 'lv-bad'
})
const findings = computed(() => {
  const mode = data.value.chartMode
  if (chartTab.value === 'p') return (data.value.pchart && data.value.pchart.findings) || []
  if (chartTab.value === 'imr' && data.value.imr) return data.value.imr.findings || []
  if (data.value.xbarR) return data.value.xbarR.findings || []
  if (data.value.imr) return data.value.imr.findings || []
  return []
})

function $ttLocal(s) {
  // 图表标题类纯文本（不进 i18n 词表，避免新增键影响其它语言包）
  return s
}

function nvl(v) {
  return v === null || v === undefined ? 0 : Number(v)
}
function fmtInt(v) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toLocaleString('zh-CN')
}
function fmtQty(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  return Number.isInteger(n) ? n.toLocaleString('zh-CN') : n.toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}
function fmtNum(v, d = 2) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toFixed(d)
}
function fmtPct(v) {
  if (v === null || v === undefined || v === '') return '—'
  return `${Number(v).toFixed(1)}%`
}

async function loadItems() {
  const res = await getSpcItems({ period: period.value })
  items.value = Array.isArray(res) ? res : res?.records || []
  if (items.value.length && !items.value.some(i => i.item === item.value)) {
    item.value = items.value[0].item
  }
  if (!items.value.length) {
    item.value = ''
    data.value = {}
  }
}

async function loadAnalysis() {
  if (!item.value) {
    data.value = {}
    return
  }
  loading.value = true
  try {
    const res = await getSpcAnalysis({ item: item.value, period: period.value })
    data.value = res || {}
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

async function reload() {
  await loadItems()
  await loadAnalysis()
}

function widen() {
  period.value = 'year'
  reload()
}

function switchTab(tab) {
  chartTab.value = tab
  nextTick(renderAll)
}

// ==================== 图表 ====================

function ensureChart(el, key) {
  if (!el) return null
  const cached = charts[key]
  // v-if 切换图页签会重建 DOM：旧实例仍指着已被移除的容器 → 必须按 DOM 身份判断，否则新容器永远空画布
  if (cached && !cached.isDisposed() && cached.getDom() === el) return cached
  if (cached && !cached.isDisposed()) cached.dispose()
  charts[key] = echarts.init(el)
  return charts[key]
}

function disposeAll() {
  Object.values(charts).forEach(c => {
    if (c && !c.isDisposed()) c.dispose()
  })
  charts = {}
}

const OUT_COLOR = '#dc2626'
const MAIN_COLOR = '#2563eb'
const CL_COLOR = '#64748b'
const LIM_COLOR = '#dc2626'

/** 控制图通用配置：中心线 + 上下控制限 + 出界点红色 */
function controlOption(values, labels, cl, ucl, lcl, name, decimals = 3) {
  const series = values.map((v, i) => {
    const out = (ucl !== null && ucl !== undefined && v > ucl) || (lcl !== null && lcl !== undefined && v < lcl)
    return {
      value: v,
      itemStyle: { color: out ? OUT_COLOR : '#fff', borderColor: out ? '#b91c1c' : MAIN_COLOR, borderWidth: 1.6 }
    }
  })
  const markLines = []
  if (cl !== null && cl !== undefined) markLines.push({ yAxis: cl, label: { formatter: `CL ${Number(cl).toFixed(decimals)}`, position: 'insideEndTop' }, lineStyle: { color: CL_COLOR, width: 1 } })
  if (ucl !== null && ucl !== undefined) markLines.push({ yAxis: ucl, label: { formatter: `UCL ${Number(ucl).toFixed(decimals)}`, position: 'insideEndTop' }, lineStyle: { color: LIM_COLOR, width: 1, type: 'dashed' } })
  if (lcl !== null && lcl !== undefined) markLines.push({ yAxis: lcl, label: { formatter: `LCL ${Number(lcl).toFixed(decimals)}`, position: 'insideEndBottom' }, lineStyle: { color: LIM_COLOR, width: 1, type: 'dashed' } })
  // 纵轴必须把中心线与上下控制限一并框进来：只按数据点自动缩放时，控制限常在可视范围外被裁掉，
  // 用户看到的就是"红线不见了"（2026-11-06 浏览器实测踩到）
  const bounds = values.filter(v => v !== null && v !== undefined && !Number.isNaN(Number(v))).map(Number)
  ;[cl, ucl, lcl].forEach(v => {
    if (v !== null && v !== undefined && !Number.isNaN(Number(v))) bounds.push(Number(v))
  })
  let yMin = null
  let yMax = null
  if (bounds.length) {
    const lo = Math.min(...bounds)
    const hi = Math.max(...bounds)
    const pad = (hi - lo) * 0.12 || Math.abs(hi) * 0.02 || 1
    yMin = lo - pad
    yMax = hi + pad
  }
  return {
    grid: { left: 58, right: 92, top: 22, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      formatter: params => {
        const p = params[0]
        return `${labels[p.dataIndex] || ''}<br/>${name}：${Number(p.value).toFixed(decimals)}`
      }
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { fontSize: 10, color: '#94a3b8', hideOverlap: true },
      axisLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: {
      type: 'value',
      min: yMin,
      max: yMax,
      axisLabel: { fontSize: 10, color: '#94a3b8', formatter: v => Number(v).toFixed(decimals) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      {
        name,
        type: 'line',
        data: series,
        symbolSize: 6,
        lineStyle: { color: MAIN_COLOR, width: 2 },
        markLine: { silent: true, symbol: 'none', data: markLines }
      }
    ]
  }
}

function renderAll() {
  if (!item.value || !data.value || !data.value.kpi) {
    disposeAll()
    return
  }
  renderControl()
  renderWave()
  renderHist()
}

function renderControl() {
  const d = data.value
  if (chartTab.value === 'p') {
    const p = d.pchart
    const el = mainChartEl.value
    const chart = ensureChart(el, 'main')
    if (!chart) return
    if (!p) {
      chart.clear()
      chart.setOption({ title: { text: '暂无整批检验数据', left: 'center', top: 'middle', textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 'normal' } } })
      return
    }
    // 逐批控制限不同：用上下两条折线表示
    const labels = p.labels || []
    const series = p.rates.map((v, i) => {
      const out = (p.ucls[i] !== null && v > p.ucls[i]) || (p.lcls[i] !== null && v < p.lcls[i])
      return {
        value: v,
        itemStyle: { color: out ? OUT_COLOR : '#fff', borderColor: out ? '#b91c1c' : MAIN_COLOR, borderWidth: 1.6 }
      }
    })
    chart.clear()
    chart.setOption({
      grid: { left: 58, right: 92, top: 22, bottom: 28 },
      tooltip: {
        trigger: 'axis',
        formatter: params => {
          const i = params[0].dataIndex
          return `${labels[i] || ''}<br/>不合格品率：${(params[0].value * 100).toFixed(2)}%<br/>控制限：${(p.lcls[i] * 100).toFixed(2)}% ~ ${(p.ucls[i] * 100).toFixed(2)}%`
        }
      },
      xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 10, color: '#94a3b8', hideOverlap: true } },
      yAxis: { type: 'value', scale: true, axisLabel: { fontSize: 10, color: '#94a3b8', formatter: v => `${(v * 100).toFixed(1)}%` }, splitLine: { lineStyle: { color: '#eef2f7' } } },
      series: [
        {
          name: '不合格品率',
          type: 'line',
          data: series,
          symbolSize: 6,
          lineStyle: { color: MAIN_COLOR, width: 2 },
          markLine: {
            silent: true,
            symbol: 'none',
            data: [
              { yAxis: p.pb, label: { formatter: `p̄ ${(p.pb * 100).toFixed(2)}%`, position: 'insideEndTop' }, lineStyle: { color: CL_COLOR, width: 1 } }
            ]
          }
        },
        {
          name: 'UCL',
          type: 'line',
          data: p.ucls,
          symbol: 'none',
          lineStyle: { color: LIM_COLOR, width: 1, type: 'dashed' },
          tooltip: { show: false }
        },
        {
          name: 'LCL',
          type: 'line',
          data: p.lcls,
          symbol: 'none',
          lineStyle: { color: LIM_COLOR, width: 1, type: 'dashed' },
          tooltip: { show: false }
        }
      ]
    }, true)
    if (rangeChartEl.value && charts.range) {
      charts.range.dispose()
      delete charts.range
    }
    return
  }

  const isXbar = chartTab.value === 'xbar'
  const src = isXbar ? d.xbarR : d.imr
  const main = ensureChart(mainChartEl.value, 'main')
  const range = ensureChart(rangeChartEl.value, 'range')
  if (!main || !range) return
  if (!src) {
    main.clear()
    main.setOption({ title: { text: '数据不足，暂无法出图', left: 'center', top: 'middle', textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 'normal' } } })
    range.clear()
    return
  }
  if (isXbar) {
    main.setOption(controlOption(src.means, src.groupLabels || [], src.xbb, src.uclX, src.lclX, '子组均值'), true)
    range.setOption(controlOption(src.ranges, src.groupLabels || [], src.rb, src.uclR, src.lclR, '子组极差'), true)
  } else {
    const labels = src.groupLabels || []
    main.setOption(controlOption(src.values, labels, src.xb, src.uclI, src.lclI, '单值'), true)
    range.setOption(controlOption(src.mrs, labels.slice(1), src.mrb, src.uclMr, 0, '移动极差'), true)
  }
}

function renderWave() {
  const chart = ensureChart(waveChartEl.value, 'wave')
  if (!chart) return
  const pts = points.value
  if (!pts.length) {
    chart.clear()
    chart.setOption({ title: { text: '暂无实测值', left: 'center', top: 'middle', textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 'normal' } } })
    return
  }
  const labels = pts.map(p => p.time || '')
  const values = pts.map(p => p.value)
  chart.setOption({
    grid: { left: 58, right: 24, top: 20, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      formatter: params => {
        const i = params[0].dataIndex
        const p = pts[i] || {}
        return [
          p.time || '',
          `实测值：${Number(p.value).toFixed(3)}`,
          p.recordNo ? `检验单号：${p.recordNo}` : '',
          p.operation ? `工序：${p.operation}` : ''
        ].filter(Boolean).join('<br/>')
      }
    },
    xAxis: { type: 'category', data: labels, boundaryGap: false, axisLabel: { fontSize: 10, color: '#94a3b8', hideOverlap: true }, axisLine: { lineStyle: { color: '#e2e8f0' } } },
    yAxis: { type: 'value', scale: true, axisLabel: { fontSize: 10, color: '#94a3b8', formatter: v => Number(v).toFixed(3) }, splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [
      {
        name: '实测值',
        type: 'line',
        smooth: true,
        symbolSize: 4,
        data: values,
        lineStyle: { color: '#0891b2', width: 2 },
        itemStyle: { color: '#0891b2' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(8,145,178,0.28)' },
            { offset: 1, color: 'rgba(8,145,178,0)' }
          ])
        }
      }
    ]
  }, true)
}

function renderHist() {
  const chart = ensureChart(histChartEl.value, 'hist')
  if (!chart) return
  const values = points.value.map(p => p.value)
  if (!values.length) {
    chart.clear()
    return
  }
  const u = usl.value
  const l = lsl.value
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (l !== null) min = Math.min(min, l)
  if (u !== null) max = Math.max(max, u)
  const bins = 9
  const span = max - min || 1
  const counts = new Array(bins).fill(0)
  values.forEach(v => {
    let b = Math.floor(((v - min) / span) * bins)
    if (b >= bins) b = bins - 1
    if (b < 0) b = 0
    counts[b] += 1
  })
  const labels = counts.map((_, i) => (min + (span * (i + 0.5)) / bins).toFixed(3))
  const markLines = []
  if (l !== null) markLines.push({ xAxis: labels[Math.min(bins - 1, Math.max(0, Math.floor(((l - min) / span) * bins)))], label: { formatter: `LSL ${Number(l).toFixed(2)}`, position: 'insideEndTop' }, lineStyle: { color: '#7c3aed', type: 'dashed', width: 1 } })
  if (u !== null) markLines.push({ xAxis: labels[Math.min(bins - 1, Math.max(0, Math.floor(((u - min) / span) * bins)))], label: { formatter: `USL ${Number(u).toFixed(2)}`, position: 'insideEndTop' }, lineStyle: { color: '#7c3aed', type: 'dashed', width: 1 } })
  chart.setOption({
    grid: { left: 8, right: 8, top: 24, bottom: 22, containLabel: true },
    tooltip: { trigger: 'axis', formatter: params => `${params[0].axisValue}<br/>频次：${params[0].value}` },
    xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 9, color: '#94a3b8', hideOverlap: true } },
    yAxis: { type: 'value', axisLabel: { fontSize: 9, color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [{ type: 'bar', data: counts, barWidth: '70%', itemStyle: { color: 'rgba(37,99,235,0.55)', borderRadius: [2, 2, 0, 0] }, markLine: { silent: true, symbol: 'none', data: markLines } }]
  }, true)
}

function resizeAll() {
  Object.values(charts).forEach(c => {
    if (c && !c.isDisposed()) c.resize()
  })
}

watch(chartTab, () => nextTick(renderControl))

onMounted(async () => {
  await reload()
  window.addEventListener('resize', resizeAll)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAll)
  disposeAll()
})
</script>

<style scoped>
.spc-page {
  padding: 0 0 24px;
}
.spc-head {
  background: linear-gradient(135deg, #0b1e3a 0%, #123a6b 100%);
  border-radius: 12px;
  padding: 18px 20px 16px;
  color: #e2f1ff;
}
.spc-head-top {
  margin-bottom: 14px;
}
.spc-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.spc-title-text {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.spc-badge {
  font-size: 11px;
  color: #7dd3fc;
  border: 1px solid rgba(125, 211, 252, 0.4);
  border-radius: 999px;
  padding: 2px 9px;
}
.spc-sub {
  font-size: 12px;
  color: #93b8d8;
  margin-top: 4px;
}
.spc-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.f-label {
  font-size: 12px;
  color: #9db8d4;
}
.f-item {
  width: 220px;
}
.f-opt-hint {
  float: right;
  font-size: 11px;
  color: #94a3b8;
  margin-left: 12px;
}
.kpi-row {
  margin-top: 2px;
}
.kpi {
  position: relative;
  border-radius: 10px;
  padding: 12px 14px;
  background: #0d2a4d;
  border: 1px solid #1e4a7a;
  overflow: hidden;
}
.kpi::after {
  content: '';
  position: absolute;
  inset: -40% -55% auto auto;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, #38bdf8, transparent 70%);
  opacity: 0.28;
}
.kpi-label {
  font-size: 12px;
  color: #9db8d4;
}
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 2px;
  letter-spacing: 0.5px;
}
.kpi-value.kpi-warn {
  color: #fca5a5;
}
.kpi-foot {
  font-size: 11.5px;
  color: #9db8d4;
  margin-top: 2px;
}
.spc-body {
  margin-top: 12px;
}
.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.card-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #1e293b;
}
.card-sub {
  font-size: 11.5px;
  color: #94a3b8;
}
.chart-tabs {
  margin-left: auto;
  display: flex;
  gap: 6px;
}
.chart-tabs button {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 12px;
  padding: 4px 10px;
  color: #64748b;
  cursor: pointer;
}
.chart-tabs button.on {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.chart-box {
  width: 100%;
  height: 240px;
}
.chart-box-sm {
  height: 140px;
}
.chart-note {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  line-height: 1.6;
}
.cap-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 12px;
  align-items: center;
  margin-bottom: 6px;
}
.cap-label {
  font-size: 12px;
  color: #64748b;
}
.cap-value {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
}
.lv-chip {
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 9px;
  display: inline-block;
}
.lv-bad {
  background: #fee2e2;
  color: #b91c1c;
}
.lv-ok {
  background: #dcfce7;
  color: #15803d;
}
.lv-mid {
  background: #fef3c7;
  color: #b45309;
}
.lv-none {
  background: #f1f5f9;
  color: #64748b;
}
.rule-ok {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12.5px;
}
.rule-item {
  border: 1px solid #fecaca;
  background: #fef2f2;
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.rule-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #b91c1c;
}
.rule-detail {
  font-size: 12px;
  color: #7f1d1d;
  margin-top: 2px;
  line-height: 1.6;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  padding: 5px 0;
  border-bottom: 1px dashed #eef2f7;
  color: #1e293b;
}
.meta-row:last-child {
  border-bottom: 0;
}
.meta-row span:first-child {
  color: #64748b;
}
.empty-wrap {
  display: flex;
  justify-content: center;
  padding: 48px 24px;
}
.empty-card {
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 28px 30px;
  max-width: 560px;
  text-align: center;
}
.empty-card h3 {
  font-size: 16px;
  color: #1e293b;
  margin-bottom: 8px;
}
.empty-card p {
  font-size: 13px;
  color: #64748b;
  line-height: 1.8;
  margin-bottom: 14px;
}
</style>
