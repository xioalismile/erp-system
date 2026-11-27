<template>
  <div class="cd-page">
    <div class="cd-head">
      <div class="cd-head-left">
        <el-button :icon="ArrowLeft" text @click="goBack">{{ $tt("返回") }}</el-button>
        <span class="cd-title">{{ $tt("自定义看板 · 设计器") }}</span>
        <el-input v-model="boardName" class="cd-name" :placeholder="$tt('看板名称')" />
      </div>
      <div class="cd-head-right">
        <span class="cd-hint" v-if="mode === 'workbench'">{{ $tt("工作台自定义卡") }} {{ cards.length }} / {{ maxCards }}</span>
        <el-button :loading="previewing" @click="previewAll">{{ $tt("刷新预览") }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $tt("保存") }}</el-button>
      </div>
    </div>

    <div class="cd-layout">
      <!-- 卡片类型库 -->
      <div class="cd-lib">
        <div class="lib-title">{{ $tt("卡片类型库") }}</div>
        <div
          v-for="t in cardTypes"
          :key="t.type"
          class="lib-item"
          @click="addCard(t.type)"
        >
          <span class="lib-icon" :style="{ background: t.color }" v-html="iconSvg(t.icon)"></span>
          <span class="lib-text">
            <span class="lib-name">{{ t.name }}</span>
            <span class="lib-desc">{{ t.desc }}</span>
          </span>
        </div>
        <div class="lib-tip">{{ $tt("点击添加卡片；画布内可拖拽排序，选中后在右侧配置。") }}</div>
      </div>

      <!-- 画布 -->
      <div class="cd-canvas-wrap">
        <div v-if="!cards.length" class="cd-canvas-empty">
          {{ $tt("从左侧卡片类型库点击添加第一张卡片") }}
        </div>
        <div v-else class="cd-canvas"
             @dragover.prevent
             @drop="onCanvasDrop">
          <div
            v-for="(c, idx) in cards"
            :key="c.id"
            class="cd-cell"
            :class="{ sel: selId === c.id, dragging: dragIdx === idx }"
            :style="{ gridColumn: `span ${c.size || 6}` }"
            draggable="true"
            @dragstart="dragIdx = idx"
            @dragover.prevent
            @drop.stop="onDrop(idx)"
            @click="select(c.id)"
          >
            <BoardCard :card="c" :data="previewMap[c.id]?.data || {}" :error="previewMap[c.id]?.error || ''">
              <template #extra>
                <button class="cd-del" @click.stop="removeCard(c.id)" :title="$tt('删除卡片')">×</button>
              </template>
            </BoardCard>
          </div>
        </div>
      </div>

      <!-- 配置面板 -->
      <div class="cd-panel">
        <div class="panel-title">
          {{ $tt("卡片配置") }}
          <span class="panel-card-name">{{ selCard ? selCard.title : $tt("未选中") }}</span>
        </div>
        <div v-if="!selCard" class="panel-empty">
          {{ $tt("从左侧添加卡片，或点击画布中的卡片进行配置。") }}
        </div>
        <template v-else>
          <div class="fld">
            <div class="fld-label">{{ $tt("标题") }}</div>
            <el-input v-model="selCard.title" size="small" @input="schedulePreview" />
          </div>

          <div class="fld">
            <div class="fld-label">{{ $tt("数据源") }}</div>
            <el-select v-model="selCard.source" size="small" style="width: 100%" @change="onSourceChange">
              <el-option v-for="s in sources" :key="s.key" :label="s.name" :value="s.key" />
            </el-select>
          </div>

          <div class="fld">
            <div class="fld-label">
              {{ $tt("关联单据") }}
              <span class="fld-tip">{{ $tt("可多选，选后可用关联单的字段穿插统计") }}</span>
            </div>
            <el-select
              v-model="selCard.joins"
              size="small"
              style="width: 100%"
              multiple
              collapse-tags
              collapse-tags-tooltip
              :placeholder="$tt('不关联则只统计本单据')"
              @change="onJoinsChange"
            >
              <el-option v-for="j in joinOptions" :key="j.key" :label="j.name" :value="j.key" />
            </el-select>
          </div>

            <div class="fld">
              <div class="fld-label">{{ $tt("时间字段") }}</div>
              <el-select v-model="selCard.timeField" size="small" style="width: 100%" @change="schedulePreview">
                <el-option v-for="f in timeFields" :key="f.field" :label="f.label" :value="f.field" />
              </el-select>
            </div>

          <!-- 指标卡 -->
          <template v-if="selCard.type === 'metric'">
            <div class="fld">
              <div class="fld-label">{{ $tt("取数方式") }}</div>
              <el-radio-group v-model="metricMode" size="small" @change="onMetricModeChange">
                <el-radio-button value="simple">{{ $tt("简单聚合") }}</el-radio-button>
                <el-radio-button value="formula">{{ $tt("自定义公式") }}</el-radio-button>
              </el-radio-group>
            </div>
            <template v-if="metricMode === 'simple'">
              <div class="fld">
                <div class="fld-label">{{ $tt("聚合方式") }}</div>
                <el-select v-model="selCard.agg" size="small" style="width: 100%" @change="schedulePreview">
                  <el-option v-for="a in aggOptions" :key="a.value" :label="a.label" :value="a.value" />
                </el-select>
              </div>
              <div class="fld">
                <div class="fld-label">{{ $tt("数值字段") }}</div>
                <el-select v-model="selCard.field" size="small" style="width: 100%" @change="schedulePreview">
                  <el-option v-for="f in numericFields" :key="f.field" :label="f.label" :value="f.field" />
                </el-select>
              </div>
            </template>
            <template v-else>
              <div class="fld">
                <div class="fld-label">
                  {{ $tt("计算公式") }}
                  <span class="fld-tip">{{ $tt("点字段与运算符拼公式，实时预览") }}</span>
                </div>
                <div class="token-box">
                  <span v-if="!tokens.length" class="token-empty">{{ $tt("尚未拼公式") }}</span>
                  <span
                    v-for="(t, i) in tokens"
                    :key="i"
                    class="token"
                    :class="{ op: isOp(t) }"
                  >{{ tokenLabel(t) }}</span>
                </div>
                <div class="token-fields">
                  <button v-for="f in numericFields" :key="f.field" class="chip" @click="pushToken(f.field)">
                    {{ f.label }}
                  </button>
                </div>
                <div class="token-ops">
                  <button v-for="op in operators" :key="op" class="chip op" @click="pushToken(op)">{{ op }}</button>
                  <button class="chip" @click="tokens = []; syncTokens()">{{ $tt("清空") }}</button>
                  <button class="chip" @click="popToken">{{ $tt("退一步") }}</button>
                </div>
                <div class="token-preview">
                  {{ $tt("预览") }}：
                  <b>{{ previewText }}</b>
                  <span class="token-hint">{{ $tt("分母为 0 显示空白") }}</span>
                </div>
              </div>
            </template>
            <div class="fld">
              <div class="fld-label">{{ $tt("小数位") }}</div>
              <el-select v-model="selCard.decimals" size="small" style="width: 100%" @change="schedulePreview">
                <el-option :value="0" label="0" />
                <el-option :value="1" label="1" />
                <el-option :value="2" label="2" />
                <el-option :value="3" label="3" />
              </el-select>
            </div>
          </template>

          <!-- 图表卡 -->
          <template v-else-if="selCard.type !== 'table'">
            <div class="fld">
              <div class="fld-label">{{ $tt("图表样式") }}</div>
              <el-select v-model="selCard.style" size="small" style="width: 100%" @change="schedulePreview">
                <el-option v-for="s in styleOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </div>
            <div class="fld">
              <div class="fld-label">{{ $tt("维度") }}</div>
              <el-select v-model="dimKey" size="small" style="width: 100%" @change="onDimChange">
                <el-option value="__time_month" :label="$tTime('月')" />
                <el-option value="__time_day" :label="$tTime('日')" />
                <el-option value="__time_week" :label="$tTime('周')" />
                <el-option v-for="f in dimFields" :key="f.field" :label="f.label" :value="f.field" />
              </el-select>
            </div>
            <div class="fld">
              <div class="fld-label">{{ $tt("指标字段与聚合") }}</div>
              <div class="fld-row">
                <el-select v-model="selCard.field" size="small" style="flex: 1" @change="schedulePreview">
                  <el-option v-for="f in numericFields" :key="f.field" :label="f.label" :value="f.field" />
                </el-select>
                <el-select v-model="selCard.agg" size="small" style="width: 96px" @change="schedulePreview">
                  <el-option v-for="a in aggOptions" :key="a.value" :label="a.label" :value="a.value" />
                </el-select>
              </div>
            </div>
            <div class="fld" v-if="selCard.type === 'compare'">
              <el-checkbox v-model="selCard.compare" @change="schedulePreview">{{ $tt("叠加对比上期") }}</el-checkbox>
            </div>
          </template>

          <!-- 明细表 -->
          <template v-else>
            <div class="fld">
              <div class="fld-label">{{ $tt("展示列") }}</div>
              <el-select v-model="selCard.fields" size="small" style="width: 100%" multiple collapse-tags @change="schedulePreview">
                <el-option v-for="f in allFields" :key="f.field" :label="f.label" :value="f.field" />
              </el-select>
            </div>
            <div class="fld">
              <div class="fld-label">{{ $tt("展示行数") }}</div>
              <el-select v-model="selCard.rows" size="small" style="width: 100%" @change="schedulePreview">
                <el-option :value="5" label="5" />
                <el-option :value="10" label="10" />
                <el-option :value="20" label="20" />
              </el-select>
            </div>
          </template>

          <!-- 外观 -->
          <div class="fld">
            <div class="fld-label">{{ $tt("图标") }}</div>
            <div class="icon-row">
              <button
                v-for="k in iconKeys"
                :key="k"
                class="icon-opt"
                :class="{ on: selCard.icon === k }"
                @click="selCard.icon = k"
                v-html="iconSvg(k, '#334155')"
              ></button>
            </div>
          </div>
          <div class="fld">
            <div class="fld-label">{{ $tt("配色") }}</div>
            <div class="color-row">
              <button
                v-for="col in colors"
                :key="col"
                class="color-opt"
                :class="{ on: selCard.color === col }"
                :style="{ background: col }"
                @click="selCard.color = col"
              ></button>
            </div>
          </div>
          <div class="fld">
            <div class="fld-label">{{ $tt("尺寸") }}</div>
            <el-radio-group v-model="selCard.size" size="small">
              <el-radio-button :value="3">{{ $tt("小") }}</el-radio-button>
              <el-radio-button :value="6">{{ $tt("中") }}</el-radio-button>
              <el-radio-button :value="12">{{ $tt("大") }}</el-radio-button>
            </el-radio-group>
          </div>

          <el-button class="del-btn" type="danger" plain size="small" @click="removeCard(selCard.id)">
            {{ $tt("删除这张卡片") }}
          </el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BoardCard from '@/components/board/BoardCard.vue'
import {
  getBoardDetail,
  updateBoard,
  getBoardSources,
  getBoardFields,
  getBoardJoins,
  previewCard,
  getWorkbenchCards,
  saveWorkbenchCards
} from '@/api/board/customBoard'
import { pickNumericField } from '@/composables/useBoardLayout'

const route = useRoute()
const router = useRouter()

const mode = route.query.type === 'workbench' ? 'workbench' : 'board'
const boardId = route.query.id ? Number(route.query.id) : null
const maxCards = ref(mode === 'workbench' ? 6 : 24)

const boardName = ref('')
const cards = ref([])
const selId = ref(null)
const selCard = computed(() => cards.value.find(c => c.id === selId.value) || null)

const sources = ref([])
const fieldsMap = ref({})
const joinsMap = ref({})
const previewMap = ref({})
const saving = ref(false)
const previewing = ref(false)
const dragIdx = ref(-1)
let previewTimer = null

const cardTypes = [
  { type: 'metric', name: '指标数值卡', desc: '单个指标或自定义公式', icon: 'target', color: '#2563eb' },
  { type: 'wave', name: '波浪图', desc: '趋势变化（面积/折线）', icon: 'wave', color: '#0891b2' },
  { type: 'compare', name: '对比图', desc: '本期 vs 上期', icon: 'cmp', color: '#16a34a' },
  { type: 'ana', name: '分析图', desc: '构成占比（饼/环）', icon: 'pie', color: '#d97706' },
  { type: 'table', name: '明细表格卡', desc: '最近明细列表', icon: 'table', color: '#dc2626' }
]
const aggOptions = [
  { value: 'sum', label: '求和' },
  { value: 'avg', label: '平均' },
  { value: 'count', label: '计数' },
  { value: 'max', label: '最大' },
  { value: 'min', label: '最小' },
  { value: 'distinct', label: '去重计数' }
]
const operators = ['+', '−', '×', '÷', '(', ')']
const iconKeys = ['target', 'wave', 'cmp', 'pie', 'table', 'alert', 'gear', 'star']
const colors = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2']

const ICON_PATHS = {
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/>',
  wave: '<path d="M2 12c3-6 6-6 9 0s6 6 11-2"/>',
  cmp: '<path d="M5 20V10"/><path d="M12 20V4"/><path d="M19 20v-7"/>',
  pie: '<path d="M12 3a9 9 0 1 0 9 9h-9z"/>',
  table: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M9 4v16"/>',
  alert: '<path d="M12 3 2 20h20z"/><path d="M12 10v4"/>',
  gear: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
  star: '<path d="m12 3 2.7 5.8 6.3.8-4.6 4.3 1.2 6.1L12 17l-5.6 3 1.2-6.1L3 9.6l6.3-.8z"/>'
}
function iconSvg(key, color) {
  const stroke = color || '#fff'
  return `<svg viewBox="0 0 24 24" fill="none" stroke="${stroke}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICON_PATHS[key] || ICON_PATHS.target}</svg>`
}

const joinOptions = computed(() => joinsMap.value[selCard.value?.source] || [])
/** 主表字段 + 已勾选关联单的字段（字段写法「关联标识.字段名」） */
const curFields = computed(() => {
  const keys = selCard.value?.joins || []
  const jf = joinOptions.value
    .filter(j => keys.includes(j.key))
    .flatMap(j => j.fields || [])
  return [...(fieldsMap.value[selCard.value?.source] || []), ...jf]
})
const allFields = computed(() => curFields.value)
const numericFields = computed(() => {
  const list = curFields.value.filter(f => f.numeric)
  return list.length ? list : curFields.value
})
const dimFields = computed(() => curFields.value.filter(f => !f.numeric || f.time))
/** 时间字段候选（卡片的时间筛选列，缺省 = 创建时间） */
const timeFields = computed(() => {
  const list = curFields.value.filter(f => f.time)
  return list.length ? list : [{ field: 'createdAt', label: '创建时间' }]
})
const metricMode = ref('simple')
const tokens = ref([])

const styleOptions = computed(() => {
  const t = selCard.value?.type
  if (t === 'wave') return [{ value: 'area', label: '面积波浪' }, { value: 'line', label: '折线' }]
  if (t === 'compare') return [{ value: 'bar', label: '分组柱状' }, { value: 'hbar', label: '横向条形' }]
  if (t === 'ana') return [{ value: 'pie', label: '饼图' }, { value: 'ring', label: '环形图' }]
  return []
})

const dimKey = computed({
  get() {
    const d = selCard.value?.dim
    if (!d) return '__time_month'
    if (d.type === 'time') return `__time_${d.bucket || 'month'}`
    return d.field || '__time_month'
  },
  set(v) {
    if (!selCard.value) return
    if (String(v).startsWith('__time_')) {
      selCard.value.dim = { type: 'time', bucket: String(v).replace('__time_', '') }
    } else {
      selCard.value.dim = { type: 'field', field: v }
    }
  }
})

const previewText = computed(() => {
  const d = previewMap.value[selCard.value?.id]
  if (!d) return '—'
  if (d.error) return d.error
  const v = d.data?.value
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 3 })
})

function $tTime(s) {
  return `按时间（${s}）`
}

function isOp(t) {
  return operators.includes(t)
}
function tokenLabel(t) {
  const f = curFields.value.find(x => x.field === t)
  return f ? f.label : t
}
function pushToken(t) {
  tokens.value = [...tokens.value, t]
  syncTokens()
}
function popToken() {
  tokens.value = tokens.value.slice(0, -1)
  syncTokens()
}
/** 前端显示用 +/−/×/÷；提交给后端换成 + - * / */
function toApiTokens(list) {
  return list.map(t => (t === '−' ? '-' : t === '×' ? '*' : t === '÷' ? '/' : t))
}

function syncTokens() {
  if (!selCard.value) return
  selCard.value.tokens = toApiTokens(tokens.value)
  selCard.value.mode = 'formula'
  schedulePreview()
}

function onMetricModeChange(v) {
  if (!selCard.value) return
  const c = selCard.value
  c.mode = v
  if (v === 'formula') {
    // 优先恢复已存公式；切走前暂存的公式优先于"自动生成"（自动生成会挑到不相干字段，实测出过错公式）
    const saved = (c.tokens && c.tokens.length) ? c.tokens : (c._lastTokens || [])
    tokens.value = saved.map(t => (t === '-' ? '−' : t === '*' ? '×' : t === '/' ? '÷' : t))
    // 必须写回卡片：只恢复编辑器里的令牌不算数，取数仍会按简单聚合算（实测预览停在求和值）
    if (tokens.value.length) {
      c.tokens = toApiTokens(tokens.value)
      c.mode = 'formula'
    } else {
      c.tokens = []
    }
  } else {
    // 切到简单聚合前把公式暂存，切回时原样恢复
    if (c.tokens && c.tokens.length) c._lastTokens = [...c.tokens]
    c.tokens = []
  }
  schedulePreview()
}

function select(id) {
  selId.value = id
  const c = cards.value.find(x => x.id === id)
  if (!c) return
  if (!Array.isArray(c.joins)) c.joins = []
  metricMode.value = c.mode === 'formula' || (c.tokens && c.tokens.length) ? 'formula' : 'simple'
  tokens.value = (c.tokens || []).map(t => (t === '-' ? '−' : t === '*' ? '×' : t === '/' ? '÷' : t))
  ensureFields(c.source).then(() => {
    // 老卡片没有时间字段：补一个合理默认（质检数据优先「检验时间」）
    if (!c.timeField) {
      const tf = (fieldsMap.value[c.source] || []).filter(f => f.time)
      c.timeField = tf.some(f => f.field === 'inspectTime')
        ? 'inspectTime'
        : (tf.length ? tf[0].field : '')
    }
  })
}

function newCard(type) {
  const base = {
    id: 'c' + Date.now() + Math.floor(Math.random() * 1000),
    type,
    source: 'qc_record',
    joins: [],
    // 质检数据的时间筛选列默认取「检验时间」（创建时间可能为空，会把历史单据漏掉）
    timeField: 'inspectTime',
    size: type === 'table' ? 12 : 6,
    icon: 'target',
    color: '#2563eb'
  }
  if (type === 'metric') {
    Object.assign(base, { title: '新指标卡', size: 3, mode: 'simple', agg: 'count', field: 'recordNo', decimals: 0, color: '#2563eb' })
  } else if (type === 'wave') {
    Object.assign(base, { title: '新波浪图', icon: 'wave', color: '#0891b2', agg: 'count', field: 'recordNo', dim: { type: 'time', bucket: 'month' }, style: 'area' })
  } else if (type === 'compare') {
    Object.assign(base, { title: '新对比图', icon: 'cmp', color: '#16a34a', agg: 'sum', field: 'defectQty', dim: { type: 'time', bucket: 'month' }, style: 'bar', compare: true })
  } else if (type === 'ana') {
    Object.assign(base, { title: '新分析图', icon: 'pie', color: '#d97706', agg: 'sum', field: 'defectQty', dim: { type: 'field', field: 'result' }, style: 'pie' })
  } else {
    Object.assign(base, { title: '新明细表格卡', icon: 'table', color: '#dc2626', rows: 5, fields: [] })
  }
  return base
}

function addCard(type) {
  if (mode === 'workbench' && cards.value.length >= maxCards.value) {
    ElMessage.warning(`工作台自定义卡片最多 ${maxCards.value} 张`)
    return
  }
  const c = newCard(type)
  cards.value = [...cards.value, c]
  select(c.id)
  schedulePreview()
}

function removeCard(id) {
  cards.value = cards.value.filter(c => c.id !== id)
  if (selId.value === id) selId.value = null
  const m = { ...previewMap.value }
  delete m[id]
  previewMap.value = m
}

function onDrop(idx) {
  const from = dragIdx.value
  dragIdx.value = -1
  if (from < 0 || from === idx) return
  const list = [...cards.value]
  const [m] = list.splice(from, 1)
  list.splice(idx, 0, m)
  cards.value = list
}

function onCanvasDrop() {
  dragIdx.value = -1
}

function onDimChange() {
  schedulePreview()
}

async function ensureFields(source) {
  if (!source) return
  if (!fieldsMap.value[source]) {
    try {
      const res = await getBoardFields(source)
      fieldsMap.value = { ...fieldsMap.value, [source]: Array.isArray(res) ? res : [] }
    } catch (e) {
      fieldsMap.value = { ...fieldsMap.value, [source]: [] }
    }
  }
  if (!joinsMap.value[source]) {
    try {
      const res = await getBoardJoins(source)
      joinsMap.value = { ...joinsMap.value, [source]: Array.isArray(res) ? res : [] }
    } catch (e) {
      joinsMap.value = { ...joinsMap.value, [source]: [] }
    }
  }
}

/** 关联单据变化：清掉已失效的字段，避免取数报「字段不存在」 */
function onJoinsChange() {
  if (!selCard.value) return
  const c = selCard.value
  const valid = new Set(curFields.value.map(f => f.field))
  if (c.field && !valid.has(c.field)) c.field = pickNumericField(curFields.value)
  if (c.timeField && !valid.has(c.timeField)) {
    const tf = curFields.value.filter(f => f.time)
    c.timeField = tf.length ? tf[0].field : ''
  }
  if (c.dim && c.dim.type === 'field' && !valid.has(c.dim.field)) {
    const df = curFields.value.filter(f => !f.numeric)
    c.dim = df.length ? { type: 'field', field: df[0].field } : { type: 'time', bucket: 'month' }
  }
  if (c.type === 'table' && Array.isArray(c.fields)) c.fields = c.fields.filter(f => valid.has(f))
  schedulePreview()
}

async function onSourceChange(v) {
  await ensureFields(v)
  if (!selCard.value) return
  selCard.value.joins = []
  selCard.value.field = pickNumericField(fieldsMap.value[v] || [])
  const tf = (fieldsMap.value[v] || []).filter(f => f.time)
  selCard.value.timeField = tf.length ? tf[0].field : ''
  if (selCard.value.dim && selCard.value.dim.type === 'field') {
    const df = (fieldsMap.value[v] || []).filter(f => !f.numeric)
    selCard.value.dim = { type: 'field', field: df.length ? df[0].field : '' }
  }
  if (selCard.value.type === 'table') selCard.value.fields = []
  schedulePreview()
}

function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => previewAll(), 500)
}

async function previewAll() {
  if (!cards.value.length) return
  previewing.value = true
  const map = { ...previewMap.value }
  for (const c of cards.value) {
    try {
      const res = await previewCard(c, 'month')
      map[c.id] = { data: res || {}, error: '' }
    } catch (e) {
      map[c.id] = { data: {}, error: e?.message || '数据暂时无法获取' }
    }
  }
  previewMap.value = map
  previewing.value = false
}

async function save() {
  if (!cards.value.length) {
    ElMessage.warning('至少添加一张卡片再保存')
    return
  }
  saving.value = true
  try {
    if (mode === 'workbench') {
      await saveWorkbenchCards(cards.value)
    } else {
      await updateBoard(boardId, { boardName: boardName.value || '未命名看板', cards: cards.value })
    }
    ElMessage.success('已保存：刷新、重开、重新登录后均保持')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({ path: '/dashboard' })
}

onMounted(async () => {
  try {
    const res = await getBoardSources()
    sources.value = Array.isArray(res) ? res : []
  } catch (e) {
    sources.value = []
  }
  if (mode === 'workbench') {
    try {
      const res = await getWorkbenchCards()
      cards.value = res?.cards || []
      maxCards.value = res?.max || 6
      boardName.value = '工作台自定义卡'
      await ensureFields('qc_record')
      await nextTick()
      previewAll()
    } catch (e) {
      ElMessage.error(e?.message || '加载失败')
    }
    return
  }
  if (!boardId) {
    ElMessage.error('缺少看板编号')
    router.push({ path: '/dashboard' })
    return
  }
  try {
    const d = await getBoardDetail(boardId)
    boardName.value = d?.boardName || ''
    cards.value = d?.cards || []
    for (const c of cards.value) await ensureFields(c.source)
    await nextTick()
    previewAll()
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  }
})

onBeforeUnmount(() => {
  if (previewTimer) clearTimeout(previewTimer)
})
</script>

<style scoped>
.cd-page {
  padding: 0 0 24px;
}
.cd-head {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.cd-head-left,
.cd-head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cd-head-right {
  margin-left: auto;
}
.cd-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}
.cd-name {
  width: 220px;
}
.cd-hint {
  font-size: 12px;
  color: #64748b;
}
.cd-layout {
  display: grid;
  grid-template-columns: 178px 1fr 300px;
  gap: 12px;
  align-items: start;
}
.cd-lib {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
}
.lib-title {
  font-size: 12.5px;
  color: #64748b;
  margin-bottom: 10px;
}
.lib-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 9px;
  margin-bottom: 8px;
  cursor: pointer;
  background: #f8fafc;
  transition: all 0.15s;
}
.lib-item:hover {
  border-color: #2563eb;
  background: #eff6ff;
}
.lib-icon {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.lib-icon :deep(svg) {
  width: 14px;
  height: 14px;
}
.lib-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.lib-name {
  font-size: 12.5px;
  color: #1e293b;
}
.lib-desc {
  font-size: 10.5px;
  color: #94a3b8;
}
.lib-tip {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.6;
  margin-top: 4px;
}
.cd-canvas-wrap {
  min-height: 420px;
}
.cd-canvas {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 12px;
  background: #eef2f8;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 12px;
  min-height: 420px;
}
.cd-cell {
  min-width: 0;
  cursor: pointer;
  border-radius: 12px;
  position: relative;
}
.cd-cell.sel {
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.35);
}
.cd-cell.dragging {
  opacity: 0.5;
}
.cd-del {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: #cbd5e1;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.cd-del:hover {
  color: #dc2626;
}
.cd-canvas-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 420px;
  background: #eef2f8;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  color: #94a3b8;
  font-size: 13px;
}
.cd-panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  position: sticky;
  top: 10px;
  max-height: calc(100vh - 120px);
  overflow: auto;
}
.panel-title {
  font-size: 12.5px;
  color: #64748b;
  margin-bottom: 10px;
}
.panel-card-name {
  margin-left: 8px;
  font-size: 11px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  padding: 1px 6px;
  color: #64748b;
}
.panel-empty {
  font-size: 12.5px;
  color: #94a3b8;
  line-height: 1.7;
}
.fld {
  margin-bottom: 10px;
}
.fld-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 5px;
}
.fld-tip {
  font-size: 10.5px;
  color: #94a3b8;
  margin-left: 6px;
}
.fld-row {
  display: flex;
  gap: 6px;
}
.token-box {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  min-height: 34px;
  padding: 4px 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
  background: #fbfdff;
}
.token-empty {
  font-size: 11.5px;
  color: #94a3b8;
}
.token {
  font-size: 11.5px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 5px;
  padding: 2px 7px;
}
.token.op {
  background: #fef3c7;
  color: #b45309;
}
.token-fields,
.token-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 6px;
}
.chip {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 999px;
  font-size: 11.5px;
  padding: 3px 9px;
  cursor: pointer;
  color: #1e293b;
}
.chip:hover {
  border-color: #2563eb;
  color: #2563eb;
}
.chip.op {
  background: #fff;
  min-width: 28px;
  padding: 3px 0;
  text-align: center;
}
.token-preview {
  font-size: 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 6px 10px;
  color: #166534;
}
.token-preview b {
  font-size: 15px;
}
.token-hint {
  font-size: 10.5px;
  color: #15803d;
  margin-left: 6px;
}
.icon-row,
.color-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.icon-opt {
  width: 28px;
  height: 28px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  cursor: pointer;
}
.icon-opt.on {
  border-color: #2563eb;
  background: #eff6ff;
}
.icon-opt :deep(svg) {
  width: 15px;
  height: 15px;
}
.color-opt {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
}
.color-opt.on {
  border-color: #0f172a;
}
.del-btn {
  width: 100%;
  margin-top: 4px;
}
@media (max-width: 1300px) {
  .cd-layout {
    grid-template-columns: 1fr;
  }
  .cd-panel {
    position: static;
    max-height: none;
  }
}
</style>
