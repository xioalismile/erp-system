// ====================================================================
// v-permission 指令 - 按钮级权限控制
// 用法: v-permission="'customer:add'" 或 v-permission="['customer:add','customer:edit']"
// ====================================================================
import { useUserStore } from '@/stores/user'
import { SETTINGS_WRITE_ACTIONS, PRIVILEGED_ACTIONS, DEFAULT_VISIBLE_ACTIONS, defaultVisibleOffCode } from '@/utils/pagePerm'

/** 租户管理员判定（2026-10-13）：与后端「管理级放行」同口径。
 *  租户管理员（sys_user.is_admin=1）角色编码不一定是 ADMIN_*、权限也无 '*'
 *  （8cloud 测试租户实测 Lyc123），按钮显隐必须同样放行。 */
function isTenantAdmin() {
  try { return localStorage.getItem('isAdmin') === '1' } catch (e) { return false }
}

export const permissionDirective = {
  mounted(el, binding) {
    const required = binding.value
    if (!required) return  // 未配置权限码, 直接放行

    // 优先从 store 读取, 兜底直接读 localStorage(防止时序问题)
    const userStore = useUserStore()
    let perms = userStore.permissions || []
    if (!perms.length) {
      try { perms = JSON.parse(localStorage.getItem('permissions') || '[]') } catch (e) { perms = [] }
    }

    // 超级管理员 / 租户管理员拥有全部权限（与后端拦截器同口径）
    if (perms.includes('*') || isTenantAdmin()) return

    // 权限列表为空(尚未加载)时, 不移除元素, 避免误删按钮
    if (!perms.length) {
      console.warn('[v-permission] 权限列表为空, 暂时放行:', required)
      return
    }

    const requiredList = Array.isArray(required) ? required : [required]
    const hasPerm = requiredList.some(p => checkPerm(p, perms))
    if (!hasPerm) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

// 权限检查：按「模块:对象:动作」三段码判定（2026-09-02 统一口径，与后端同一套码）
// 规则（2026-09-20 全站按钮组件化后收敛为"只看显式配置"）：
//   ① 精确命中 → 放行；
//   ② 该「模块:对象」下已存在显式按钮配置（perms 里有同前缀码）→ 严格执行，未勾即不放行；
//   ③ 该「模块:对象」下**没有任何按钮配置** → 默认口径「能看菜单 = 该菜单下按钮全都有」→ 放行。
//   ④ 设置中心（system:*）的**写动作**（2026-10-13）：默认隐藏，显式勾选才显示 ——
//      与后端拦截器「设置中心默认拒绝」同源（勾了 ≤30 秒轮询后按钮自动出现，无需重登；
//      取消勾选按钮即消失，接口同时 403）。修复用户反馈的「关闭开关还存在」。
//   ⑤ 提权动作（重置密码/分配角色，2026-10-13）：全对象默认隐藏，显式勾选才显示 ——
//      与后端「管理员或显式按钮码」守卫同源。
//   ⑥ 默认可见动作（筛选设置，2026-10-19 用户口径：默认勾选、但必须能关闭）：
//      未显式关闭 → 放行；设置中心取消勾选并保存 → 库里写入 `<对象>:<动作>Off` 关闭标记 →
//      本指令命中标记即隐藏，刷新/重登后仍生效。设置中心配置项与其它按钮同一处、同一套开/关链路。
//
// 说明：后端 /auth/userinfo 与 AuthInterceptor 已把 sys_role_permission 里
//       permission_type='按钮' 的码并入 permissions，故 ② 能生效。
function checkPerm(required, perms) {
  if (perms.includes(required)) return true
  const idx = required.lastIndexOf(':')
  if (idx <= 0) return false
  const objectCode = required.substring(0, idx)
  const action = required.substring(idx + 1)

  // 规则⑤：提权动作默认隐藏（管理员含 '*' 已在上方放行）
  if (PRIVILEGED_ACTIONS.includes(action)) return false

  // 规则④：设置中心写动作默认隐藏（与后端设置拦截「默认拒绝」同源）
  const isSettingsObject = objectCode === 'system' || objectCode.startsWith('system:')
  if (isSettingsObject && SETTINGS_WRITE_ACTIONS.includes(action)) return false

  // 规则⑥：默认可见动作 —— 有"关闭标记"就隐藏，没有标记就放行
  if (DEFAULT_VISIBLE_ACTIONS.includes(action)) {
    return !perms.includes(defaultVisibleOffCode(objectCode, action))
  }

  const hasExplicitButtons = perms.some(p => typeof p === 'string' && p.startsWith(objectCode + ':'))
  if (hasExplicitButtons) return false
  return true
}
