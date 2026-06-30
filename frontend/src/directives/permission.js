// ====================================================================
// v-permission 指令 - 按钮级权限控制
// 用法: v-permission="'customer:add'" 或 v-permission="['customer:add','customer:edit']"
// ====================================================================
import { useUserStore } from '@/stores/user'

export const permissionDirective = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const required = binding.value
    if (!required) return

    const perms = userStore.permissions || []
    // 超级管理员拥有全部权限
    if (perms.includes('*')) return

    const requiredList = Array.isArray(required) ? required : [required]
    const hasPerm = requiredList.some(p => perms.includes(p))
    if (!hasPerm) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
