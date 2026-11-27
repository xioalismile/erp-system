<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="900px"
    top="6vh"
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="bdd-tip">
      <div v-if="multiItems.length > 1" class="bdd-item">
        <span>取数项</span>
        <el-select v-model="itemIndex" size="small" style="width: 180px" @change="load">
          <el-option v-for="(it, i) in multiItems" :key="i" :label="it.name || ('取数项' + (i + 1))" :value="i" />
        </el-select>
      </div>
      <span v-if="criteria">统计口径：{{ criteria }}</span>
      <span v-if="truncated">共 {{ total }} 条，当前显示前 {{ rows.length }} 条</span>
      <span v-if="!rows.length && !loading">本期没有符合条件的单据</span>
    </div>
    <el-table v-loading="loading" :data="rows" size="small" border height="440" style="width: 100%">
      <el-table-column
        v-for="c in columns"
        :key="c.field"
        :label="c.label"
        :min-width="colWidth(c)"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <el-link
            v-if="c.field === 'billNo' && row.billId && canJump"
            type="primary"
            :underline="false"
            @click="openBill(row)"
          >{{ row.billNo }}</el-link>
          <span v-else>{{ cellText(row[c.field], c.type) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { drillCard } from '@/api/board/customBoard'
import { resolveBillRoute } from '@/utils/billRoute'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  card: { type: Object, default: null },
  period: { type: String, default: 'month' },
  dimLabel: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])
const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref([])
const columns = ref([])
const total = ref(0)
const truncated = ref(false)
const docType = ref('')
const docName = ref('')
const criteria = ref('')
const itemIndex = ref(0)

const multiItems = computed(() => {
  const c = props.card || {}
  return c.mode === 'multi' && Array.isArray(c.items) ? c.items : []
})

const title = computed(() => (docName.value ? `${docName.value}明细` : '明细清单'))
const canJump = computed(() => !!resolveBillRoute(docType.value, 1))

function colWidth(c) {
  if (c.field === 'billNo') return 150
  if (c.field === 'billDate') return 110
  if (c.field === 'status') return 90
  if (c.field === 'value') return 120
  return 130
}

function cellText(v, type) {
  if (v === null || v === undefined || v === '') return ''
  if (type === 'amount' || type === 'number') {
    const n = Number(v)
    if (!Number.isNaN(n)) return n.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
  }
  if (type === 'date' || type === 'datetime') {
    return String(v).replace('T', ' ').slice(0, type === 'date' ? 10 : 19)
  }
  if (type === 'bool') return v === true || v === 'true' || v === 1 ? '是' : '否'
  return String(v)
}

async function load() {
  if (!props.card) return
  if (!props.card.source && props.card.mode !== 'multi') return
  loading.value = true
  rows.value = []
  try {
    const res = await drillCard(
      props.card,
      props.period || 'month',
      props.dimLabel || '',
      200,
      props.card && props.card.mode === 'multi' ? itemIndex.value : null
    )
    const d = res || {}
    rows.value = Array.isArray(d.rows) ? d.rows : []
    columns.value = Array.isArray(d.columns) ? d.columns : []
    total.value = Number(d.total) || rows.value.length
    truncated.value = !!d.truncated
    docType.value = d.docType || ''
    docName.value = d.docName || ''
    criteria.value = d.criteria || ''
  } catch (e) {
    rows.value = []
    columns.value = []
    total.value = 0
    truncated.value = false
    ElMessage.error(e?.message || '明细暂时取不到')
  } finally {
    loading.value = false
  }
}

function openBill(row) {
  const route = resolveBillRoute(docType.value, row.billId)
  if (!route) {
    ElMessage.warning('该单据暂无可打开的编辑页')
    return
  }
  router.push(route)
}

watch(() => props.modelValue, v => {
  if (v) {
    if (itemIndex.value >= multiItems.value.length) itemIndex.value = 0
    load()
  }
})
</script>

<style scoped>
.bdd-tip {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
  min-height: 18px;
}
.bdd-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
