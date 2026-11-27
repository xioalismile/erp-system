<template>
  <!--
    通用弹窗底部按钮（2026-09-20 全站按钮组件化）
    同一页面所有弹窗的「取消 / 确定 / 关闭」共用一个动作码（dlgCancel / dlgConfirm / dlgClose），
    避免一个页面十几个弹窗各占一个权限项。

    用法（放在 `<el-dialog>` 的 `<template #footer>` 内）：
      <DialogActions perm-prefix="crm:pool" :loading="saving" @confirm="doSave" @cancel="visible = false" />
    额外按钮用默认插槽：
      <DialogActions ...><el-button @click="xxx">其他</el-button></DialogActions>
  -->
  <div class="dialog-actions">
    <slot />
    <el-button
      v-permission="codeOf(cancelAction)"
      @click="onCancel"
    >{{ $tf(cancelText) }}</el-button>
    <el-button
      v-if="showConfirm"
      v-permission="codeOf(confirmAction)"
      :type="confirmType"
      :loading="loading"
      :disabled="confirmDisabled"
      @click="$emit('confirm')"
    >{{ $tf(confirmText) }}</el-button>
  </div>
</template>

<script setup>
import { permCode } from '@/utils/pagePerm'

const props = defineProps({
  permPrefix: { type: String, default: '' },
  cancelAction: { type: String, default: 'dlgCancel' },
  confirmAction: { type: String, default: 'dlgConfirm' },
  cancelText: { type: String, default: '取消' },
  confirmText: { type: String, default: '确定' },
  confirmType: { type: String, default: 'primary' },
  showConfirm: { type: Boolean, default: true },
  loading: { type: Boolean, default: false },
  confirmDisabled: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm', 'cancel'])

function codeOf (action) {
  return permCode(props.permPrefix, action)
}

function onCancel () {
  emit('cancel')
}
</script>

<style scoped>
.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
</style>
