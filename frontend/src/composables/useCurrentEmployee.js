/**
 * 获取当前登录用户对应的员工信息
 * 用途：单据编辑页"销售员/采购员/经办人/领料人"等字段默认填当前用户，减少操作
 * 依赖：员工档案绑定系统账号（employee.user_id），/auth/userinfo 返回 userId
 * 返回 { employeeId, employeeName } 或 null（账号未绑定员工档案时留空手选，不报错）
 */
import { useUserStore } from '@/stores/user'
import { listAllEmployee } from '@/api/basedata/employee'

export async function getCurrentEmployee() {
  try {
    const store = useUserStore()
    if (!store.token) return null
    // 老会话 localStorage 可能还没有 userId：拉一次 userinfo 补齐
    if (store.userId == null) await store.fetchUserinfo()
    const userId = Number(store.userId)
    if (!userId) return null
    const res = await listAllEmployee()
    const arr = Array.isArray(res) ? res : (res?.records || [])
    const me = arr.find(e => Number(e.userId) === userId)
    if (!me) return null
    return { employeeId: me.employeeId, employeeName: me.employeeName || '' }
  } catch (e) {
    // 任何失败都静默降级：不设默认值，用户手动选择
    console.warn('获取当前用户员工信息失败', e)
    return null
  }
}
