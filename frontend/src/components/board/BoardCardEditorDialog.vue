<template>
  <el-dialog
    v-model="visible"
    :title="$tt('卡片取数设置')"
    width="900px"
    top="6vh"
    append-to-body
    :close-on-click-modal="false"
    @opened="onOpened"
  >
    <div class="bce-wrap">
      <div class="bce-form">
        <el-form label-width="86px" label-position="left" size="small">
          <el-form-item :label="$tt('卡片标题')">
            <el-input v-model="draft.title" :placeholder="$tt('例如 本月回款额')" @input="schedulePreview" />
          </el-form-item>
          <el-form-item :label="$tt('卡片类型')">
            <el-radio-group v-model="draft.type" @change="onTypeChange">
              <el-radio-button value="metric">{{ $tt("指标卡") }}</el-radio-button>
              <el-radio-button value="wave">{{ $tt("波浪图") }}</el-radio-button>
              <el-radio-button value="compare">{{ $tt("对比图") }}</el-radio-button>
              <el-radio-button value="ana">{{ $tt("分析图") }}</el-radio-button>
              <el-radio-button value="table">{{ $tt("明细表") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="!isMulti" :label="$tt('数据源')">
            <el-select v-model="draft.source" style="width: 100%" @change="onSourceChange">
              <el-option v-for="s in sources" :key="s.key" :label="s.name" :value="s.key" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!isMulti" :label="$tt('关联单据')">
            <el-select
              v-model="draft.joins"
              multiple
              collapse-tags
              collapse-tags-tooltip
              style="width: 100%"
              :placeholder="$tt('不关联则只统计本单据')"
              @change="onJoinsChange"
            >
              <el-option-group v-for="g in joinGroups" :key="g.key" :label="g.label">
                <el-option v-for="j in g.list" :key="j.key" :label="j.name" :value="j.key" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item v-if="!isMulti" :label="$tt('时间字段')">
            <el-select v-model="draft.timeField" style="width: 100%" @change="schedulePreview">
              <el-option v-for="f in timeFields" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
            </el-select>
          </el-form-item>

          <!-- 指标卡 -->
          <template v-if="draft.type === 'metric'">
            <el-form-item :label="$tt('取数方式')">
              <el-radio-group v-model="metricMode" @change="onMetricModeChange">
                <el-radio-button value="simple">{{ $tt("直接聚合") }}</el-radio-button>
                <el-radio-button value="formula">{{ $tt("单来源公式") }}</el-radio-button>
                <el-radio-button value="multi">{{ $tt("多来源公式") }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="metricMode === 'simple'">
              <el-form-item :label="$tt('聚合方式')">
                <el-select v-model="draft.agg" style="width: 100%" @change="schedulePreview">
                  <el-option v-for="a in aggOptions" :key="a.value" :label="a.label" :value="a.value" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$tt('数值字段')">
                <el-select v-model="draft.field" style="width: 100%" filterable @change="schedulePreview">
                  <el-option v-for="f in numericFields" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else-if="metricMode === 'multi'">
              <el-form-item :label="$tt('取数项')">
                <div class="bce-items">
                  <div v-for="(it, i) in draft.items" :key="i" class="bce-item">
                    <div class="bce-item-head">
                      <el-input v-model="it.name" size="small" :placeholder="$tt('取数项名称')" style="width: 160px" @input="schedulePreview" />
                      <el-button size="small" link type="danger" @click="removeItem(i)">{{ $tt("删除") }}</el-button>
                    </div>
                    <div class="bce-item-grid">
                      <el-select v-model="it.source" size="small" :placeholder="$tt('来源单据')" filterable @change="onItemSourceChange(it)">
                        <el-option v-for="s in sources" :key="s.key" :label="s.name" :value="s.key" />
                      </el-select>
                      <el-select v-model="it.agg" size="small" @change="schedulePreview">
                        <el-option v-for="a in aggOptions" :key="a.value" :label="a.label" :value="a.value" />
                      </el-select>
                      <el-select v-model="it.field" size="small" filterable :placeholder="$tt('数值字段')" @change="schedulePreview">
                        <el-option v-for="f in itemNumericFields(it)" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
                      </el-select>
                      <el-select v-model="it.timeField" size="small" :placeholder="$tt('时间字段')" @change="schedulePreview">
                        <el-option v-for="f in itemTimeFields(it)" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
                      </el-select>
                      <el-select
                        v-model="it.joins"
                        size="small"
                        multiple
                        collapse-tags
                        collapse-tags-tooltip
                        :placeholder="$tt('关联单据')"
                        class="bce-item-joins"
                        @change="schedulePreview"
                      >
                        <el-option-group v-for="g in itemJoinGroups(it)" :key="g.key" :label="g.label">
                          <el-option v-for="j in g.list" :key="j.key" :label="j.name" :value="j.key" />
                        </el-option-group>
                      </el-select>
                    </div>
                    <div class="bce-item-filters">
                      <div v-for="(f, fi) in it.filters" :key="fi" class="bce-filter-row">
                        <el-select v-model="f.field" size="small" filterable :placeholder="$tt('筛选字段')" @change="schedulePreview">
                          <el-option v-for="x in itemAllFields(it)" :key="x.field" :label="fieldLabel(x)" :value="x.field" />
                        </el-select>
                        <el-select v-model="f.op" size="small" style="width: 100px" @change="schedulePreview">
                          <el-option value="eq" label="等于" />
                          <el-option value="ne" label="不等于" />
                          <el-option value="gt" label="大于" />
                          <el-option value="ge" label="大于等于" />
                          <el-option value="lt" label="小于" />
                          <el-option value="le" label="小于等于" />
                          <el-option value="contains" label="包含" />
                          <el-option value="in" label="属于" />
                        </el-select>
                        <el-input v-model="f.value" size="small" :placeholder="$tt('值')" @input="schedulePreview" />
                        <el-button size="small" link type="danger" @click="removeFilter(it, fi)">{{ $tt("删除") }}</el-button>
                      </div>
                      <el-button size="small" link type="primary" @click="addFilter(it)">{{ $tt("添加筛选条件") }}</el-button>
                    </div>
                  </div>
                  <el-button size="small" @click="addItem">{{ $tt("添加取数项") }}</el-button>
                </div>
              </el-form-item>
              <el-form-item :label="$tt('计算公式')">
                <div class="bce-token-box">
                  <span v-if="!formulaTokens.length" class="bce-token-empty">{{ $tt("先添加取数项，再点下方变量与运算符拼公式") }}</span>
                  <span v-for="(t, i) in formulaTokens" :key="i" class="bce-token" :class="{ op: isFormulaOp(t) }">{{ t }}</span>
                </div>
              </el-form-item>
              <el-form-item :label="$tt('可用变量')">
                <div class="bce-chips">
                  <button v-for="(it, i) in draft.items" :key="i" type="button" class="bce-chip" @click="pushFormulaToken(it.name)">
                    {{ it.name || $tt("未命名") }}
                  </button>
                </div>
              </el-form-item>
              <el-form-item :label="$tt('运算符')">
                <div class="bce-chips">
                  <button v-for="op in operators" :key="op" type="button" class="bce-chip op" @click="pushFormulaToken(op)">{{ op }}</button>
                  <el-input v-model="constantValue" size="small" style="width: 90px" :placeholder="$tt('数值')" />
                  <button type="button" class="bce-chip" @click="insertConstant">{{ $tt("插入数值") }}</button>
                  <button type="button" class="bce-chip" @click="formulaTokens = []; syncFormula()">{{ $tt("清空") }}</button>
                  <button type="button" class="bce-chip" @click="popFormulaToken">{{ $tt("退一步") }}</button>
                </div>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item :label="$tt('计算公式')">
                <div class="bce-token-box">
                  <span v-if="!tokens.length" class="bce-token-empty">{{ $tt("点下方字段与运算符拼公式") }}</span>
                  <span v-for="(t, i) in tokens" :key="i" class="bce-token" :class="{ op: isOp(t) }">{{ tokenLabel(t) }}</span>
                </div>
              </el-form-item>
              <el-form-item :label="$tt('可用字段')">
                <div class="bce-chips">
                  <button v-for="f in numericFields" :key="f.field" type="button" class="bce-chip" @click="pushToken(f.field)">
                    {{ fieldLabel(f) }}
                  </button>
                </div>
              </el-form-item>
              <el-form-item :label="$tt('运算符')">
                <div class="bce-chips">
                  <button v-for="op in operators" :key="op" type="button" class="bce-chip op" @click="pushToken(op)">{{ op }}</button>
                  <button type="button" class="bce-chip" @click="tokens = []; syncTokens()">{{ $tt("清空") }}</button>
                  <button type="button" class="bce-chip" @click="popToken">{{ $tt("退一步") }}</button>
                </div>
              </el-form-item>
            </template>
            <el-form-item :label="$tt('小数位')">
              <el-select v-model="draft.decimals" style="width: 120px" @change="schedulePreview">
                <el-option :value="0" label="0" />
                <el-option :value="1" label="1" />
                <el-option :value="2" label="2" />
                <el-option :value="3" label="3" />
              </el-select>
            </el-form-item>
          </template>

          <!-- 图表卡 -->
          <template v-else-if="draft.type !== 'table'">
            <el-form-item :label="$tt('图表样式')">
              <el-select v-model="draft.style" style="width: 100%" @change="schedulePreview">
                <el-option v-for="s in styleOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$tt('统计维度')">
              <el-select v-model="dimKey" style="width: 100%" @change="schedulePreview">
                <el-option value="__time_month" :label="$tt('按月')" />
                <el-option value="__time_day" :label="$tt('按日')" />
                <el-option value="__time_week" :label="$tt('按周')" />
                <el-option v-for="f in dimFields" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$tt('指标字段')">
              <div class="bce-row">
                <el-select v-model="draft.field" style="flex: 1" filterable @change="schedulePreview">
                  <el-option v-for="f in numericFields" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
                </el-select>
                <el-select v-model="draft.agg" style="width: 96px" @change="schedulePreview">
                  <el-option v-for="a in aggOptions" :key="a.value" :label="a.label" :value="a.value" />
                </el-select>
              </div>
            </el-form-item>
            <el-form-item v-if="draft.type === 'compare'" :label="$tt('上期对比')">
              <el-switch v-model="draft.compare" @change="schedulePreview" />
            </el-form-item>
          </template>

          <!-- 明细表 -->
          <template v-else>
            <el-form-item :label="$tt('展示列')">
              <el-select v-model="draft.fields" multiple collapse-tags style="width: 100%" @change="schedulePreview">
                <el-option v-for="f in allFields" :key="f.field" :label="fieldLabel(f)" :value="f.field" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$tt('展示行数')">
              <el-select v-model="draft.rows" style="width: 120px" @change="schedulePreview">
                <el-option :value="5" label="5" />
                <el-option :value="10" label="10" />
                <el-option :value="20" label="20" />
              </el-select>
            </el-form-item>
          </template>

          <el-form-item :label="$tt('宽度')">
            <el-radio-group v-model="draft.size" @change="schedulePreview">
              <el-radio-button :value="3">{{ $tt("窄") }}</el-radio-button>
              <el-radio-button :value="4">1/3</el-radio-button>
              <el-radio-button :value="6">{{ $tt("半行") }}</el-radio-button>
              <el-radio-button :value="12">{{ $tt("整行") }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="$tt('配色')">
            <div class="bce-colors">
              <button
                v-for="col in colors"
                :key="col"
                type="button"
                class="bce-color"
                :class="{ on: draft.color === col }"
                :style="{ background: col }"
                @click="draft.color = col; schedulePreview()"
              ></button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <div class="bce-preview">
        <div class="bce-preview-title">{{ $tt("实时预览") }}</div>
        <div class="bce-preview-box">
          <BoardCard :card="draft" :data="previewData" :error="previewError" :drillable="false" />
        </div>
        <div class="bce-preview-tip">{{ previewTip }}</div>
        <div v-if="previewData.items && previewData.items.length" class="bce-item-result">
          <div v-for="(it, i) in previewData.items" :key="i" class="bce-item-result-row">
            <span class="bce-item-result-name">{{ it.name }}</span>
            <span :class="{ 'bce-item-result-err': it.error }">
              {{ it.error || fmtItemValue(it.value) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" :loading="previewing" @click="confirm">{{ $tt("确定") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import BoardCard from '@/components/board/BoardCard.vue'
import {
  getBoardSources,
  getBoardFields,
  getBoardJoins,
  previewCard
} from '@/api/board/customBoard'
import { pickNumericField } from '@/composables/useBoardLayout'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  card: { type: Object, default: null },
  defaultTitle: { type: String, default: '' },
  defaultType: { type: String, default: 'metric' },
  /** 预览用的时间维度，跟随所在看板当前选中的 今日/本周/本月/本季/本年 */
  period: { type: String, default: 'month' }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const sources = ref([])
const fieldsMap = ref({})
const joinsMap = ref({})
const draft = ref(blankCard())
const tokens = ref([])
const formulaTokens = ref([])
const constantValue = ref('')
const metricMode = ref('simple')
const previewData = ref({})
const previewError = ref('')
const previewing = ref(false)
let previewTimer = null
let inited = false

const isMulti = computed(() => draft.value.type === 'metric' && metricMode.value === 'multi')

const aggOptions = [
  { value: 'sum', label: '求和' },
  { value: 'avg', label: '平均' },
  { value: 'count', label: '计数' },
  { value: 'max', label: '最大' },
  { value: 'min', label: '最小' },
  { value: 'distinct', label: '去重计数' }
]
const operators = ['+', '−', '×', '÷', '(', ')']
const colors = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2']

const styleOptions = computed(() => {
  if (draft.value.type === 'wave') return [{ value: 'area', label: '面积波浪' }, { value: 'line', label: '折线' }]
  if (draft.value.type === 'compare') return [{ value: 'bar', label: '分组柱状' }, { value: 'hbar', label: '横向条形' }]
  if (draft.value.type === 'ana') return [{ value: 'pie', label: '饼图' }, { value: 'ring', label: '环形图' }]
  return []
})

const joinOptions = computed(() => joinsMap.value[draft.value.source] || [])
function groupJoins (list) {
  const order = ['表头', '基础资料', '来源单据']
  const labels = { 表头: '本单表头', 基础资料: '基础资料', 来源单据: '来源单据' }
  const map = new Map()
  for (const j of (list || [])) {
    const g = j.group || '基础资料'
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(j)
  }
  return [...map.entries()]
    .sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]))
    .map(([g, list]) => ({ key: g, label: labels[g] || g, list }))
}
const joinGroups = computed(() => groupJoins(joinOptions.value))
const baseFields = computed(() => fieldsMap.value[draft.value.source] || [])
const joinFields = computed(() => {
  const keys = draft.value.joins || []
  return joinOptions.value.filter(j => keys.includes(j.key)).flatMap(j => j.fields || [])
})
const allFields = computed(() => [...baseFields.value, ...joinFields.value])
const numericFields = computed(() => {
  const list = allFields.value.filter(f => f.numeric)
  return list.length ? list : allFields.value
})
const dimFields = computed(() => allFields.value.filter(f => !f.numeric || f.time))
const timeFields = computed(() => {
  const list = allFields.value.filter(f => f.time)
  const sorted = [...list].sort((a, b) => (b.preferred ? 1 : 0) - (a.preferred ? 1 : 0))
  return sorted.length ? sorted : [{ field: 'createdAt', label: '创建时间' }]
})

// ==================== 多取数项 ====================
function itemBaseFields (it) {
  return fieldsMap.value[it.source] || []
}
function itemJoinOptions (it) {
  return joinsMap.value[it.source] || []
}
function itemJoinGroups (it) {
  return groupJoins(itemJoinOptions(it))
}
function itemAllFields (it) {
  const keys = it.joins || []
  return [
    ...itemBaseFields(it),
    ...itemJoinOptions(it).filter(j => keys.includes(j.key)).flatMap(j => j.fields || [])
  ]
}
function itemNumericFields (it) {
  const list = itemAllFields(it).filter(f => f.numeric)
  return list.length ? list : itemAllFields(it)
}
function itemTimeFields (it) {
  const list = itemAllFields(it).filter(f => f.time)
  return [...list].sort((a, b) => (b.preferred ? 1 : 0) - (a.preferred ? 1 : 0))
}
function itemPreferredField (it) {
  const direct = itemBaseFields(it).find(f => f.preferred)
  if (direct) return direct
  for (const j of itemJoinOptions(it)) {
    const hit = (j.fields || []).find(f => f.preferred)
    if (hit) return { ...hit, joinKey: j.key }
  }
  return null
}
function newItem () {
  return { id: 'it' + Date.now() + Math.floor(Math.random() * 1000), name: '', source: '', joins: [], timeField: '', agg: 'sum', field: '', filters: [] }
}
function initItemDefaults (it) {
  const pref = itemPreferredField(it)
  if (pref && pref.joinKey && !(it.joins || []).includes(pref.joinKey)) {
    it.joins = [...(it.joins || []), pref.joinKey]
  }
  const all = itemAllFields(it)
  if (!it.timeField || !all.some(f => f.field === it.timeField)) {
    it.timeField = pref ? pref.field : (all.find(f => f.time)?.field || '')
  }
  if (!it.field || !all.some(f => f.field === it.field)) {
    it.field = pickNumericField(all)
  }
}
function addItem () {
  const it = newItem()
  it.name = '取数项' + ((draft.value.items || []).length + 1)
  if (!draft.value.items) draft.value.items = []
  draft.value.items.push(it)
  if (sources.value.length) it.source = sources.value[0].key
  ensureSource(it.source).then(() => {
    initItemDefaults(it)
    schedulePreview()
  })
}
function removeItem (i) {
  draft.value.items.splice(i, 1)
  schedulePreview()
}
function addFilter (it) {
  if (!Array.isArray(it.filters)) it.filters = []
  it.filters.push({ field: '', op: 'eq', value: '' })
  schedulePreview()
}
function removeFilter (it, i) {
  it.filters.splice(i, 1)
  schedulePreview()
}
function onItemSourceChange (it) {
  it.joins = []
  it.field = ''
  it.timeField = ''
  ensureSource(it.source).then(() => {
    initItemDefaults(it)
    schedulePreview()
  })
}
function isFormulaOp (t) {
  return operators.includes(t)
}
function pushFormulaToken (t) {
  if (t === null || t === undefined || t === '') return
  formulaTokens.value = [...formulaTokens.value, String(t)]
  syncFormula()
}
function popFormulaToken () {
  formulaTokens.value = formulaTokens.value.slice(0, -1)
  syncFormula()
}
function insertConstant () {
  const v = (constantValue.value || '').trim()
  if (!/^-?\d+(\.\d+)?$/.test(v)) {
    ElMessage.warning('请输入数字')
    return
  }
  pushFormulaToken(v)
}
function syncFormula () {
  draft.value.formula = formulaTokens.value.map(toApiToken)
  draft.value.mode = 'multi'
  schedulePreview()
}
function toApiToken (t) {
  return t === '−' ? '-' : t === '×' ? '*' : t === '÷' ? '/' : t
}
function fmtItemValue (v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

const dimKey = computed({
  get() {
    const d = draft.value.dim
    if (!d) return '__time_month'
    if (d.type === 'time') return `__time_${d.bucket || 'month'}`
    return d.field || '__time_month'
  },
  set(v) {
    if (String(v).startsWith('__time_')) draft.value.dim = { type: 'time', bucket: String(v).replace('__time_', '') }
    else draft.value.dim = { type: 'field', field: v }
  }
})

const previewTip = computed(() => {
  const d = previewData.value || {}
  const parts = []
  if (previewError.value) parts.push(previewError.value)
  if (d.error) parts.push(d.error)
  if (d.formulaError) parts.push(d.formulaError)
  if (d.emptyHint) parts.push(d.emptyHint)
  if (d.criteria) parts.push(`统计口径：${d.criteria}`)
  return parts.join(' · ')
})

function blankCard(type) {
  const t = type || 'metric'
  const base = {
    id: 'c' + Date.now() + Math.floor(Math.random() * 1000),
    type: t,
    title: '',
    source: '',
    joins: [],
    timeField: '',
    size: t === 'table' ? 12 : 6,
    color: '#2563eb',
    icon: t === 'wave' ? 'wave' : t === 'compare' ? 'cmp' : t === 'ana' ? 'pie' : t === 'table' ? 'table' : 'target'
  }
  if (t === 'metric') Object.assign(base, { mode: 'simple', items: [], formula: [], agg: 'sum', field: '', decimals: 2 })
  else if (t === 'table') Object.assign(base, { fields: [], rows: 10 })
  else Object.assign(base, { agg: 'sum', field: '', dim: { type: 'time', bucket: 'month' }, style: t === 'wave' ? 'area' : t === 'compare' ? 'bar' : 'pie' })
  return base
}

function fieldLabel(f) {
  if (!f.joinKey) return f.label
  let j = joinOptions.value.find(x => x.key === f.joinKey)
  if (!j) {
    for (const list of Object.values(joinsMap.value)) {
      j = (list || []).find(x => x.key === f.joinKey)
      if (j) break
    }
  }
  return j ? `${j.name}·${f.label}` : f.label
}

function isOp(t) {
  return operators.includes(t)
}
function tokenLabel(t) {
  const f = allFields.value.find(x => x.field === t)
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
function toApiTokens(list) {
  return list.map(t => (t === '−' ? '-' : t === '×' ? '*' : t === '÷' ? '/' : t))
}
function fromApiTokens(list) {
  return (list || []).map(t => (t === '-' ? '−' : t === '*' ? '×' : t === '/' ? '÷' : t))
}
function syncTokens() {
  draft.value.tokens = toApiTokens(tokens.value)
  draft.value.mode = 'formula'
  schedulePreview()
}
function onMetricModeChange(v) {
  const c = draft.value
  if (v === 'formula') {
    c.mode = 'formula'
    if (!tokens.value.length) tokens.value = fromApiTokens(c.tokens)
    formulaTokens.value = []
  } else if (v === 'multi') {
    c.mode = 'multi'
    if (!Array.isArray(c.items)) c.items = []
    if (!c.items.length) {
      const it = newItem()
      it.name = '取数项1'
      it.source = c.source || (sources.value[0] && sources.value[0].key) || ''
      it.field = c.field || ''
      it.timeField = c.timeField || ''
      it.joins = [...(c.joins || [])]
      c.items.push(it)
      ensureSource(it.source).then(() => {
        initItemDefaults(it)
        if (!formulaTokens.value.length) formulaTokens.value = [it.name]
        syncFormula()
      })
    } else {
      formulaTokens.value = (c.formula || []).map(t => (t === '-' ? '−' : t === '*' ? '×' : t === '/' ? '÷' : t))
      if (!formulaTokens.value.length) formulaTokens.value = [c.items[0].name]
      syncFormula()
    }
  } else {
    c.mode = 'simple'
    c.tokens = []
    tokens.value = []
    formulaTokens.value = []
  }
  schedulePreview()
}

async function ensureSource(source) {
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

function onSourceChange(v) {
  const c = draft.value
  c.joins = []
  c.field = ''
  c.timeField = ''
  c.dim = c.type === 'metric' || c.type === 'table' ? null : { type: 'time', bucket: 'month' }
  if (c.type === 'table') c.fields = []
  ensureSource(v).then(() => {
    pickDefaults()
    schedulePreview()
  })
}

function onJoinsChange() {
  const fields = allFields.value
  const valid = new Set(fields.map(f => f.field))
  if (draft.value.field && !valid.has(draft.value.field)) draft.value.field = ''
  if (draft.value.timeField && !valid.has(draft.value.timeField)) draft.value.timeField = ''
  if (draft.value.dim && draft.value.dim.type === 'field' && !valid.has(draft.value.dim.field)) {
    draft.value.dim = { type: 'time', bucket: 'month' }
  }
  if (draft.value.type === 'table' && Array.isArray(draft.value.fields)) {
    draft.value.fields = draft.value.fields.filter(f => valid.has(f))
  }
  pickDefaults()
  schedulePreview()
}

function onTypeChange(t) {
  const c = draft.value
  const next = blankCard(t)
  c.type = t
  c.size = next.size
  c.icon = next.icon
  if (t === 'metric') {
    c.dim = null
    c.style = undefined
    c.mode = 'simple'
    c.agg = c.agg || 'sum'
    c.decimals = c.decimals == null ? 2 : c.decimals
    c.tokens = []
    c.items = []
    c.formula = []
    c.fields = undefined
    tokens.value = []
    formulaTokens.value = []
    metricMode.value = 'simple'
  } else if (t === 'table') {
    c.dim = null
    c.tokens = []
    c.fields = []
    c.rows = c.rows || 10
    tokens.value = []
  } else {
    c.dim = c.dim || { type: 'time', bucket: 'month' }
    c.style = next.style
    c.agg = c.agg || 'sum'
    c.tokens = []
    c.fields = undefined
    tokens.value = []
  }
  pickDefaults()
  schedulePreview()
}

/** 业务日期优先：优先主表业务日期；在表头上时自动带上表头关联（明细数据源的默认日期在表头） */
function adoptPreferredTime() {
  const direct = baseFields.value.find(f => f.preferred)
  if (direct) return direct.field
  for (const j of joinOptions.value) {
    const hit = (j.fields || []).find(f => f.preferred)
    if (hit) {
      if (!(draft.value.joins || []).includes(j.key)) {
        draft.value.joins = [...(draft.value.joins || []), j.key]
      }
      return hit.field
    }
  }
  return ''
}

/** 换数据源 / 换关联后补齐失效字段（时间字段、数值字段、维度） */
function pickDefaults() {
  const c = draft.value
  const prefTime = adoptPreferredTime()
  const fs = allFields.value
  const tf = fs.filter(f => f.time)
  // 历史卡片曾默认落到“生产日期/交货日期/创建时间”这类非业务日期，打开时自动纠正为业务日期
  const weakTime = new Set(['productionDate', 'expiryDate', 'approvedAt', 'closedAt', 'voidedAt', 'createdAt', 'updatedAt'])
  const tail = String(c.timeField || '').split('.').pop()
  if (!c.timeField || !fs.some(f => f.field === c.timeField) || (prefTime && weakTime.has(tail))) {
    c.timeField = prefTime || (tf.length ? tf[0].field : c.timeField || '')
  }
  if (c.type === 'table') {
    if (!Array.isArray(c.fields)) c.fields = []
    return
  }
  const nf = fs.filter(f => f.numeric)
  if (!c.field || !nf.some(f => f.field === c.field)) {
    c.field = pickNumericField(fs)
  }
  if (c.type !== 'metric' && c.dim) {
    if (c.dim.type === 'field' && !fs.some(f => f.field === c.dim.field)) {
      const df = fs.filter(f => !f.numeric)
      c.dim = df.length ? { type: 'field', field: df[0].field } : { type: 'time', bucket: 'month' }
    }
  }
}

function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(runPreview, 450)
}

async function runPreview() {
  const c = draft.value
  if (!c.source) {
    previewData.value = {}
    previewError.value = ''
    return
  }
  previewing.value = true
  try {
    const res = await previewCard(c, props.period || 'month')
    previewData.value = res || {}
    previewError.value = ''
  } catch (e) {
    previewData.value = {}
    previewError.value = e?.message || '该配置暂时取不到数据'
  } finally {
    previewing.value = false
  }
}

async function onOpened() {
  try {
    const res = await getBoardSources()
    sources.value = Array.isArray(res) ? res : []
  } catch (e) {
    sources.value = []
  }
  draft.value = props.card ? JSON.parse(JSON.stringify(props.card)) : blankCard(props.defaultType)
  if (!draft.value.joins) draft.value.joins = []
  if (!draft.value.title) draft.value.title = props.defaultTitle || ''
  if (!draft.value.source && sources.value.length) draft.value.source = sources.value[0].key
  if (draft.value.type === 'metric' && draft.value.mode === 'multi') {
    metricMode.value = 'multi'
    if (!Array.isArray(draft.value.items)) draft.value.items = []
    for (const it of draft.value.items) {
      if (!Array.isArray(it.joins)) it.joins = []
      if (!Array.isArray(it.filters)) it.filters = []
    }
    formulaTokens.value = fromApiTokens(draft.value.formula)
    for (const it of draft.value.items) {
      if (it.source) await ensureSource(it.source)
    }
  } else {
    metricMode.value = draft.value.mode === 'formula' || (draft.value.tokens && draft.value.tokens.length) ? 'formula' : 'simple'
    tokens.value = fromApiTokens(draft.value.tokens)
    formulaTokens.value = []
  }
  await ensureSource(draft.value.source)
  pickDefaults()
  runPreview()
  inited = true
}

function confirm() {
  if (isMulti.value) {
    const items = draft.value.items || []
    if (!items.length) { ElMessage.warning('请至少添加一个取数项'); return }
    const names = items.map(i => (i.name || '').trim())
    if (names.some(n => !n)) { ElMessage.warning('取数项名称不能为空'); return }
    if (new Set(names).size !== names.length) { ElMessage.warning('取数项名称不能重复'); return }
    if (!formulaTokens.value.length) { ElMessage.warning('请设置计算公式'); return }
    const valid = new Set(names)
    const bad = formulaTokens.value.find(t =>
      !valid.has(t) && !operators.includes(t) && !/^-?\d+(\.\d+)?$/.test(String(t))
    )
    if (bad) { ElMessage.warning(`公式里的变量「${bad}」不存在，请删除或改名`); return }
    syncFormula()
  } else if (!draft.value.source) {
    ElMessage.warning('请先选择数据源')
    return
  }
  if (!draft.value.title) draft.value.title = props.defaultTitle || '未命名卡片'
  emit('confirm', JSON.parse(JSON.stringify(draft.value)))
  visible.value = false
}

watch(() => props.modelValue, v => {
  if (!v && previewTimer) clearTimeout(previewTimer)
})
</script>

<style scoped>
.bce-wrap {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 16px;
  align-items: start;
}
.bce-form {
  min-width: 0;
}
.bce-preview {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  background: #f8fafc;
  position: sticky;
  top: 0;
}
.bce-preview-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}
.bce-preview-box {
  min-height: 150px;
  background: #fff;
  border-radius: 8px;
}
.bce-preview-tip {
  font-size: 11.5px;
  color: #b45309;
  margin-top: 6px;
  line-height: 1.5;
}
.bce-row {
  display: flex;
  gap: 6px;
  width: 100%;
}
.bce-token-box {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  min-height: 32px;
  padding: 3px 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  width: 100%;
  background: #fbfdff;
}
.bce-token-empty {
  font-size: 11.5px;
  color: #94a3b8;
}
.bce-token {
  font-size: 11.5px;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 5px;
  padding: 2px 7px;
}
.bce-token.op {
  background: #fef3c7;
  color: #b45309;
}
.bce-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.bce-chip {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 999px;
  font-size: 11.5px;
  padding: 3px 9px;
  cursor: pointer;
  color: #1e293b;
}
.bce-chip:hover {
  border-color: #2563eb;
  color: #2563eb;
}
.bce-chip.op {
  background: #fff;
  min-width: 28px;
  padding: 3px 0;
  text-align: center;
}
.bce-colors {
  display: flex;
  gap: 6px;
}
.bce-items {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bce-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  background: #fbfdff;
}
.bce-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.bce-item-grid {
  display: grid;
  grid-template-columns: 1fr 92px 1fr 1fr;
  gap: 6px;
}
.bce-item-joins {
  grid-column: 1 / -1;
}
.bce-item-filters {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bce-filter-row {
  display: grid;
  grid-template-columns: 1fr 100px 1fr auto;
  gap: 6px;
  align-items: center;
}
.bce-item-result {
  margin-top: 8px;
  border-top: 1px dashed #e2e8f0;
  padding-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}
.bce-item-result-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #334155;
}
.bce-item-result-name {
  color: #64748b;
}
.bce-item-result-err {
  color: #cf1322;
}
.bce-color {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
}
.bce-color.on {
  border-color: #0f172a;
}
@media (max-width: 1100px) {
  .bce-wrap {
    grid-template-columns: 1fr;
  }
}
</style>
