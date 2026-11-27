<template>
  <el-table-column
    prop="createdBy"
    :label="$tf('创建人')"
    width="100"
    v-if="visible"
    :show-overflow-tooltip="true"
  />
  <el-table-column
    prop="createdAt"
    :label="$tf('创建时间')"
    width="160"
    v-if="visible"
    :show-overflow-tooltip="true"
  >
    <template #default="{ row }">{{ formatAuditDateTime(row.createdAt) }}</template>
  </el-table-column>
  <el-table-column
    prop="updatedBy"
    :label="$tf('修改人')"
    width="100"
    v-if="visible"
    :show-overflow-tooltip="true"
  />
  <el-table-column
    prop="updatedAt"
    :label="$tf('修改时间')"
    width="160"
    v-if="visible"
    :show-overflow-tooltip="true"
  >
    <template #default="{ row }">{{ formatAuditDateTime(row.updatedAt) }}</template>
  </el-table-column>
</template>

<script setup>
/**
 * 审计列组件 - 在 el-table 中插入 4 列审计信息（创建人/创建时间/修改人/修改时间）
 *
 * 用法：
 *   <el-table ...>
 *     <el-table-column prop="xxx" ... />
 *     <AuditColumns />
 *     <el-table-column :label="$tf('操作')" fixed="right" ...>...</el-table-column>
 *   </el-table>
 *
 * 注意：必须作为 el-table 的直接子节点（在模板层面），Vue 3 fragment 会自动展开为多个 el-table-column
 */
defineProps({
  visible: { type: Boolean, default: true }
})

function formatAuditDateTime(val) {
  if (!val) return ''
  // LocalDateTime 数组序列化 [y,m,d,h,m,s,ns]
  if (Array.isArray(val)) {
    const [y, mo, d, h = 0, mi = 0, se = 0] = val
    return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(se)}`
  }
  // LocalDateTime 对象序列化 {year,monthValue,dayOfMonth,hour,minute,second,...}
  if (typeof val === 'object') {
    if (val.year) {
      return `${val.year}-${pad(val.monthValue ?? val.month)}-${pad(val.dayOfMonth ?? val.day)} ${pad(val.hour ?? 0)}:${pad(val.minute ?? 0)}:${pad(val.second ?? 0)}`
    }
    return ''
  }
  // 字符串形式 "2026-07-20T22:29:59.501268"
  const s = String(val)
  return s.length > 19 ? s.slice(0, 19).replace('T', ' ') : s.replace('T', ' ')
}

function pad(n) {
  return String(n ?? 0).padStart(2, '0')
}
</script>
