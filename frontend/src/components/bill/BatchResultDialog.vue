<!-- @feature FEAT-BILL-BATCH -->
<template>
  <!-- 批量操作结果面板（2026-09-19）：成功 N / 失败 M / 跳过 K + 失败清单（可导出/可重试） -->
  <el-dialog
    v-model="visible"
    :title="title"
    width="720px"
    :close-on-click-modal="false"
    class="batch-result-dialog"
  >
    <div class="br-summary">
      <span class="br-item"><b class="ok">{{ okCount }}</b> {{ $tt('成功') }}</span>
      <span class="br-item"><b class="fail">{{ failCount }}</b> {{ $tt('失败') }}</span>
      <span class="br-item"><b class="skip">{{ skipCount }}</b> {{ $tt('跳过') }}</span>
      <span class="br-tip">{{ $tt('逐条独立执行，失败的记录不受影响，可重试') }}</span>
    </div>

    <template v-if="failCount > 0">
      <div class="br-sec-title">{{ $tt('失败清单') }}</div>
      <el-table :data="failRows" size="small" border max-height="240">
        <el-table-column type="index" label="#" width="56" />
        <el-table-column :label="$tt('单据')" min-width="180">
          <template #default="{ row }">{{ labelOf(row.row) }}</template>
        </el-table-column>
        <el-table-column prop="message" :label="$tt('失败原因')" min-width="260" show-overflow-tooltip />
      </el-table>
    </template>

    <template v-if="skipCount > 0">
      <div class="br-sec-title">{{ $tt('跳过清单') }}</div>
      <el-table :data="skipRows" size="small" border max-height="200">
        <el-table-column type="index" label="#" width="56" />
        <el-table-column :label="$tt('单据')" min-width="180">
          <template #default="{ row }">{{ labelOf(row.row) }}</template>
        </el-table-column>
        <el-table-column prop="reason" :label="$tt('跳过原因')" min-width="260" show-overflow-tooltip />
      </el-table>
    </template>

    <template #footer>
      <div class="br-footer">
        <el-button v-if="failCount > 0" v-permission="perm('export')" @click="$emit('export-fail')">{{ $tt('导出失败清单') }}</el-button>
        <el-button v-if="failCount > 0" type="primary" plain v-permission="perm('refresh')" :loading="retrying" @click="$emit('retry')">
          {{ $tt('重试失败') }} {{ failCount }} {{ $tt('条') }}
        </el-button>
        <el-button type="primary" v-permission="perm('dlgClose')" @click="visible = false">{{ $tt('知道了') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm()


const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** { actionLabel, ok:[{row}], fail:[{row,message}], skip:[{row,reason}] } */
  result: { type: Object, default: null },
  retrying: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'retry', 'export-fail'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const okCount = computed(() => (props.result && props.result.ok ? props.result.ok.length : 0))
const failCount = computed(() => (props.result && props.result.fail ? props.result.fail.length : 0))
const skipCount = computed(() => (props.result && props.result.skip ? props.result.skip.length : 0))
const failRows = computed(() => (props.result && props.result.fail ? props.result.fail : []))
const skipRows = computed(() => (props.result && props.result.skip ? props.result.skip : []))
const title = computed(() => (props.result && props.result.actionLabel ? props.result.actionLabel : tt('批量操作')) + tt('结果'))

/** 单据标识：按常见单号字段兜底，找不到就用主键 */
function labelOf(row) {
  if (!row || typeof row !== 'object') return String(row || '')
  return row.billNo || row.orderNo || row.contractNo || row.inboundNo || row.outboundNo
    || row.invoiceNo || row.requestNo || row.transferNo || row.returnNo || row.receiptNo
    || row.voucherNo || row.workOrderNo || row.no || row.code || row.id || ''
}
</script>

<style scoped>
.br-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; }
.br-item { font-size: 14px; color: #52525b; }
.br-item b { font-size: 16px; }
.br-item b.ok { color: #1dc981; }
.br-item b.fail { color: #e8463a; }
.br-item b.skip { color: #efaa17; }
.br-tip { margin-left: auto; font-size: 12px; color: #909399; }
.br-sec-title { margin: 8px 0; font-size: 14px; font-weight: 600; }
.br-footer { display: flex; justify-content: flex-end; gap: 12px; }
</style>
