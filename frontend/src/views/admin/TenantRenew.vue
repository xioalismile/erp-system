<template>
  <el-dialog
    v-model="visible"
    :title="$tf('客户续费')"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="renew-info">
      <p>{{ $tt("客户名称：") }}<b>{{ row?.tenant_name }}</b></p>
      <p>{{ $tt("当前到期日期：") }}<b :class="{ 'text-danger': isExpired }">{{ currentExpire }}</b>
        <el-tag v-if="isExpired" type="danger" size="small" style="margin-left: 8px">{{ $tt("已过期") }}</el-tag>
      </p>
      <p v-if="isExpired" class="text-muted">{{ $tt("已过期客户续费后将从今天起计算，并自动恢复为正常状态") }}</p>
      <p v-else class="text-muted">{{ $tt("续费将从当前到期日期顺延") }}</p>
    </div>
    <el-form label-width="90px">
      <el-form-item :label="$tf('续费时长')">
        <el-select v-model="months" style="width: 100%">
          <el-option :label="$tf('1 个月')" :value="1" />
          <el-option :label="$tf('3 个月')" :value="3" />
          <el-option :label="$tf('6 个月')" :value="6" />
          <el-option :label="$tf('12 个月（1年）')" :value="12" />
          <el-option :label="$tf('24 个月（2年）')" :value="24" />
          <el-option :label="$tf('36 个月（3年）')" :value="36" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$tf('续费后到期')">
        <span class="new-expire">{{ newExpire }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-permission="perm('dlgCancel')" @click="visible = false">{{ $tt("取消") }}</el-button>
      <el-button type="primary" v-permission="perm('dlgConfirm')" :loading="submitting" @click="handleSubmit">{{ $tt("确认续费") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { renewTenant } from '@/api/admin/tenant'
import { tt } from '@/i18n'
import { usePagePerm } from '@/utils/pagePerm'

const perm = usePagePerm('platform:tenant-manage')

const emit = defineEmits(['success'])
const visible = ref(false)
const submitting = ref(false)
const row = ref(null)
const months = ref(12)

const currentExpire = computed(() => {
  const d = row.value?.expire_date
  return d ? String(d).slice(0, 10) : '未设置'
})

const isExpired = computed(() => {
  if (!row.value?.expire_date) return false
  return new Date(row.value.expire_date) < new Date(new Date().toDateString())
})

const newExpire = computed(() => {
  const base = isExpired.value ? new Date() : (row.value?.expire_date ? new Date(row.value.expire_date) : new Date())
  base.setMonth(base.getMonth() + months.value)
  const y = base.getFullYear()
  const m = String(base.getMonth() + 1).padStart(2, '0')
  const d = String(base.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
})

function open(r) {
  row.value = r
  months.value = 12
  visible.value = true
}

async function handleSubmit() {
  submitting.value = true
  try {
    await renewTenant(row.value.tenant_id, months.value)
    ElMessage.success(`${tt("续费成功，新到期日期：")}${newExpire.value}`)
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.renew-info p {
  margin: 6px 0;
  line-height: 1.6;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
.text-danger {
  color: #f56c6c;
}
.new-expire {
  font-weight: 600;
  color: #409eff;
}
</style>
