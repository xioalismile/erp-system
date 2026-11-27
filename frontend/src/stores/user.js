// ====================================================================
// 用户Store - 管理登录态、用户信息、权限列表、菜单权限
// 说明：Pinia state 初始化时只从 localStorage 读取一次；
//       当 Login / TenantSelect 等组件外部直接写 localStorage 后，
//       必须把值显式写回 store（或调用 rehydrate / fetchUserinfo），
//       否则 computed（菜单组、权限判断）不会响应式更新。
// ====================================================================
import { defineStore } from 'pinia'
import request from '@/utils/request'
// 会话（身份/公司）相关缓存的统一清理与重装载：
// 页签 / keep-alive 实例 / 字段权限 / 必填星号 / 用户级偏好（列设置、语言、AI 偏好）
// + 切换公司后重拉租户级配置（主题、界面尺寸、系统参数、字典、审批开关）。
// 集中一处是为了不要再出现"某处忘了清"→ 用户实报的跨用户/跨公司串味缺陷。
import { resetUserScopedCaches, refreshTenantScopedCaches } from '@/utils/sessionCacheReset'

function safeParseArray(raw, fallback = []) {
  if (!raw) return fallback
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v : fallback
  } catch (_) {
    return fallback
  }
}

export const useUserStore = defineStore('user', {
  state: () => {
    const token = localStorage.getItem('token') || ''
    const username = localStorage.getItem('username') || ''
    const realName = localStorage.getItem('realName') || ''
    const userId = localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null
    const permissions = safeParseArray(localStorage.getItem('permissions'))
    const roles = safeParseArray(localStorage.getItem('roles'))
    const menuIds = safeParseArray(localStorage.getItem('menuIds'))
    const menuRoutePaths = safeParseArray(localStorage.getItem('menuRoutePaths'))
    const tenantName = localStorage.getItem('tenantName') || ''
    const tenantVersion = localStorage.getItem('tenantVersion') || ''
    // 租户管理员标记（sys_user.is_admin=1，2026-10-13）：与后端按钮权限"管理级放行"同口径。
    // 租户管理员角色编码不一定是 ADMIN_*、权限也无 '*'（8cloud 测试租户实测 Lyc123），
    // 前端按钮显隐必须同样认这个标记，否则管理员的写按钮被当普通角色隐藏。
    const isAdmin = localStorage.getItem('isAdmin') === '1'

    // 注意：不做"登录即全权限"的伪造兜底——权限以登录/切换公司时
    // /auth/userinfo 返回的真实数据为准，未分配就是未分配。
    return { token, username, realName, userId, permissions, roles, menuIds, menuRoutePaths, tenantName, tenantVersion, isAdmin }
  },
  actions: {
    async login(payload) {
      const data = await request.post('/auth/login', payload)
      this.token = data.token
      this.username = data.username
      this.realName = data.realName || data.username
      localStorage.setItem('token', this.token)
      localStorage.setItem('username', this.username)
      localStorage.setItem('realName', this.realName)
      // 登录成功后立即拉取真实权限（见 fetchUserinfo）
      await this.fetchUserinfo()
      // 新登录一律按当前身份重装缓存：① 清上个用户的内存痕迹（页签/字段权限/必填星号）
      // ② 用户级偏好（列设置/语言/AI 偏好）装载新用户自己那份
      // ③ 重拉租户级配置（主题/界面尺寸/系统参数/字典/审批开关），不吃上一家公司的
      // 必须放在 fetchUserinfo 之后：本方法在调用前已把 this.username 写成新用户，
      // 所以 fetchUserinfo 里的"身份变化"判定在本路径比不出差异，这里显式做一次。
      resetUserScopedCaches()
      refreshTenantScopedCaches()
      return data
    },
    /**
     * 拉取 /auth/userinfo 并写入 state 与 localStorage。
     * 供 login 内部调用，也可在 Login / TenantSelect 等外部登录流程中单独调用。
     * userinfo 失败：权限未知，保持空（菜单显示"无权限"空态），不伪造管理员。
     */
    async fetchUserinfo() {
      // 调用前的身份（用户名 + 公司）：登出后 store 里是空 → 任何新登录都会判定为"换了用户"
      // 注意：只比用户名与公司，不比 userId —— userId 可能只是被补写（老会话），不该因此清页签
      const prevIdentity = `${this.username || ''}|${this.tenantName || ''}`
      try {
        const info = await request.get('/auth/userinfo')
        // 权限以接口返回为准（admin 后端返回 '*'，普通用户返回勾选的菜单列表）
        this.permissions = Array.isArray(info.permissions) ? info.permissions : []
        this.roles = Array.isArray(info.roles) ? info.roles : []
        this.menuIds = Array.isArray(info.menuIds) ? info.menuIds : []
        this.menuRoutePaths = Array.isArray(info.menuRoutePaths) ? info.menuRoutePaths : []
        // userId 用于"销售员/采购员/经办人默认当前用户"等场景
        this.userId = info.userId != null ? Number(info.userId) : null
        if (this.userId != null) localStorage.setItem('userId', String(this.userId))
        else localStorage.removeItem('userId')
        
        // 租户信息
        this.tenantName = info.tenantName || ''
        this.tenantVersion = info.tenantVersion || ''
        this.isAdmin = info.isAdmin === true || info.isAdmin === 1 || String(info.isAdmin) === '1'
        if (this.tenantName) localStorage.setItem('tenantName', this.tenantName)
        if (this.tenantVersion) localStorage.setItem('tenantVersion', this.tenantVersion)
        localStorage.setItem('isAdmin', this.isAdmin ? '1' : '0')
        
        localStorage.setItem('permissions', JSON.stringify(this.permissions))
        localStorage.setItem('roles', JSON.stringify(this.roles))
        localStorage.setItem('menuIds', JSON.stringify(this.menuIds))
        localStorage.setItem('menuRoutePaths', JSON.stringify(this.menuRoutePaths))
        // 身份/公司发生变化（登录、切换公司）→ 清掉跨用户会串的进程内缓存（页签等）、
        // 按新作用域重装用户级偏好，并重拉当前公司的租户级配置。
        // 同一次会话内重复调用（如补 userId）身份不变，不会误清用户正在用的页签。
        const newName = info.username || this.username || localStorage.getItem('username') || ''
        if (`${newName}|${this.tenantName || ''}` !== prevIdentity) {
          resetUserScopedCaches()
          refreshTenantScopedCaches()
        }
        // 权限变更监视（2026-10-12）：管理员改按钮权限后，各端 30 秒内自动拿到新权限，
        // 不再要求"重新登录才生效"。详见 startPermWatcher。
        this.startPermWatcher()
        return info
      } catch (e) {
        // userinfo 失败：权限未知，保持空（菜单显示"无权限"空态），不伪造管理员
        this.permissions = []
        this.roles = []
        this.menuIds = []
        this.menuRoutePaths = []
        this.tenantName = ''
        this.tenantVersion = ''
        localStorage.setItem('permissions', JSON.stringify(this.permissions))
        localStorage.setItem('roles', JSON.stringify(this.roles))
        localStorage.setItem('menuIds', JSON.stringify(this.menuIds))
        localStorage.setItem('menuRoutePaths', JSON.stringify(this.menuRoutePaths))
        localStorage.removeItem('tenantName')
        localStorage.removeItem('tenantVersion')
        return null
      }
    },
    async logout() {
      try { await request.post('/auth/logout') } catch (e) {}
      this.stopPermWatcher()
      this.reset()
    },
    /**
     * 权限变更监视（2026-10-12）：每 30 秒向后端取一次"权限版本号"，
     * 发现版本变化（管理员保存了角色/按钮权限）→ 立即重拉 userinfo 更新本地权限。
     * 效果：按钮权限的开/关**无需重新登录**即生效——已打开的页面在下一次进入/渲染时
     * 按钮显隐随之变化；当前页若仍显示旧按钮，点击会被后端拦截并给出中文提示（双保险）。
     * 首轮轮询只记基线不触发刷新，避免登录瞬间多拉一次。
     */
    startPermWatcher() {
      if (this._permTimer) return
      this._permVersionSeen = null
      // 启动即取一次当基线：这样管理员保存后，最迟下一个轮询周期（30 秒）就能发现变化，
      // 不会出现"保存落在首轮轮询之后 → 首轮只能当基线 → 要等第二轮（约 60 秒）"的空窗。
      request.get('/auth/perm-version').then(d => {
        if (d && d.version != null && this._permVersionSeen == null) this._permVersionSeen = Number(d.version)
      }).catch(() => {})
      this._permTimer = setInterval(async () => {
        if (!this.token) return
        try {
          const data = await request.get('/auth/perm-version')
          const v = data && data.version != null ? Number(data.version) : null
          if (v == null) return
          if (this._permVersionSeen == null) { this._permVersionSeen = v; return }
          if (v !== this._permVersionSeen) {
            this._permVersionSeen = v
            await this.fetchUserinfo()
          }
        } catch (e) { /* 单次失败静默，下一轮重试 */ }
      }, 30000)
    },
    stopPermWatcher() {
      if (this._permTimer) { clearInterval(this._permTimer); this._permTimer = null }
      this._permVersionSeen = null
    },
    reset() {
      this.stopPermWatcher()
      this.token = ''
      this.username = ''
      this.realName = ''
      this.userId = null
      this.permissions = []
      this.roles = []
      this.menuIds = []
      this.menuRoutePaths = []
      this.tenantName = ''
      this.tenantVersion = ''
      this.isAdmin = false
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('realName')
      localStorage.removeItem('userId')
      localStorage.removeItem('permissions')
      localStorage.removeItem('roles')
      localStorage.removeItem('menuIds')
      localStorage.removeItem('menuRoutePaths')
      localStorage.removeItem('tenantName')
      localStorage.removeItem('tenantVersion')
      localStorage.removeItem('isAdmin')
      // 【顺序要紧】身份必须**先**清干净再清缓存：
      // 用户级偏好按「用户 + 公司」作用域重装，身份还在的话会把上一个用户的那份又装回来。
      resetUserScopedCaches()
    },
    /** 从 localStorage 重新同步字段到 state（页面刷新/外部写 LS 后保证一致），不修改权限内容 */
    rehydrate() {
      const token = localStorage.getItem('token') || ''
      const username = localStorage.getItem('username') || ''
      const realName = localStorage.getItem('realName') || ''
      const userId = localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null
      const permissions = safeParseArray(localStorage.getItem('permissions'))
      const roles = safeParseArray(localStorage.getItem('roles'))
      const menuIds = safeParseArray(localStorage.getItem('menuIds'))
      const menuRoutePaths = safeParseArray(localStorage.getItem('menuRoutePaths'))
      const tenantName = localStorage.getItem('tenantName') || ''
      const tenantVersion = localStorage.getItem('tenantVersion') || ''
      const isAdmin = localStorage.getItem('isAdmin') === '1'
      if (this.token !== token) this.token = token
      if (this.username !== username) this.username = username
      if (this.realName !== realName) this.realName = realName
      if (this.userId !== userId) this.userId = userId
      if (JSON.stringify(this.permissions) !== JSON.stringify(permissions)) this.permissions = permissions
      if (JSON.stringify(this.roles) !== JSON.stringify(roles)) this.roles = roles
      if (JSON.stringify(this.menuIds) !== JSON.stringify(menuIds)) this.menuIds = menuIds
      if (JSON.stringify(this.menuRoutePaths) !== JSON.stringify(menuRoutePaths)) this.menuRoutePaths = menuRoutePaths
      if (this.tenantName !== tenantName) this.tenantName = tenantName
      if (this.tenantVersion !== tenantVersion) this.tenantVersion = tenantVersion
      this.isAdmin = isAdmin
      // 权限变更监视（2026-10-13 补口）：刷新页面后 JS 上下文重建、旧的轮询定时器已不存在，
      // 而刷新路径只走 rehydrate（权限从 localStorage 恢复、不会触发 fetchUserinfo）——
      // 不在这里重启监视器，「改权限免重登生效」在刷新后的会话里就失效了（实测 80 秒零轮询）。
      // startPermWatcher 自带防重（_permTimer 已在跑则直接返回），登录路径重复调用无副作用。
      if (this.token) this.startPermWatcher()
    }
  }
})