/**
 * 会话（身份/公司）相关的进程内缓存统一清理与重装载（2026-09-23 立）。
 *
 * 【为什么集中在一个模块】登出 / 登录 / 切换公司都是 SPA 内路由跳转（页面不刷新），
 * 所有 Pinia store 与模块级缓存都还活着。散落在各处"记得清一下"是本次线上缺陷的根因
 * （用户实报：重新登录后上一个用户打开的单据/基础资料页签还在；要求设置中心的配置永远不能串）。
 * 这里做唯一入口，由 stores/user.js 在三个时机调用：
 *   ① 登出（reset）       → resetUserScopedCaches()（此时身份已清空 → 落到 anon 作用域 = 默认值）
 *   ② 登录成功            → resetUserScopedCaches() + refreshTenantScopedCaches()
 *   ③ 身份/公司发生变化    → 同上（覆盖 Login.vue / TenantSelect.vue 各自的登录流程）
 *
 * 注意：调用前必须确保 localStorage 里的身份（userId/username/tenantId/tenantCode）
 *      已经是"目标身份"，否则会按旧作用域装载。
 */
import { useTagsViewStore } from '@/stores/tagsView'
import { useColumnConfigStore } from '@/stores/columnConfig'
import { useUiSizeStore } from '@/stores/uiSize'
import { useThemeStore } from '@/stores/theme'
import { useAppConfigStore } from '@/stores/appConfig'
import { useSysConfig } from '@/composables/useSysConfig'
import { clearDictCache } from '@/composables/useDict'
import { clearFieldPermissionCache } from '@/composables/useFieldPermission'
import { clearFieldRequiredMarkCache } from '@/utils/fieldRequiredMark'
import { clearApprovalGateCache } from '@/utils/approval-gate'
import { clearUnitConvertCache } from '@/composables/useUnitConvert'
import { reloadLangForScope, reloadFieldLabelsForScope, clearFieldLabelOverrides } from '@/i18n'
import { reloadAiDisplayPrefsForScope } from '@/composables/useAiDisplayPrefs'

/**
 * 清掉"跨用户会串"的进程内缓存，并按当前身份重装用户级偏好。
 * 登出时身份已清空 → 各偏好回到默认值；换用户/换公司 → 装载对方自己的那一份。
 */
export function resetUserScopedCaches() {
  // 页签 + keep-alive 组件实例 + 未保存草稿标记（只保留 affix 固定页签）
  try { useTagsViewStore().resetAll() } catch (e) { /* ignore */ }
  // 字段权限 / 必填星号（按角色+字段配置派生，跨用户必串）
  try { clearFieldPermissionCache() } catch (e) { /* ignore */ }
  try { clearFieldRequiredMarkCache() } catch (e) { /* ignore */ }
  // 多单位换算：换算行按物料缓存 + 「启用浮动单位」开关缓存（业务数据按租户一份，跨用户/跨公司必串）
  try { clearUnitConvertCache() } catch (e) { /* ignore */ }
  // 用户级偏好：内存里的那份必须按新作用域重新装载（只换存储 key 不改内存 = 页面还显示上一个人的值）
  try { useColumnConfigStore().reloadForScope() } catch (e) { /* ignore */ }
  try { reloadLangForScope() } catch (e) { /* ignore */ }
  try { clearFieldLabelOverrides() } catch (e) { /* ignore */ }
  try { reloadAiDisplayPrefsForScope() } catch (e) { /* ignore */ }
}

/**
 * 重新拉取"按公司（租户）一份"的配置：切换公司后必须重拉，否则整站还是上一家公司的
 * 主题/界面尺寸/系统参数/字典/审批开关（"多租户还共用一份"的另一种表现）。
 * 只在有有效登录态时调用（登录成功后 / 切公司后）。
 */
export function refreshTenantScopedCaches() {
  // 主题 / 界面尺寸：先按当前公司作用域重装本地缓存并立即套用，再向后端要这家公司的值
  try {
    const theme = useThemeStore()
    theme.reloadForScope()
    theme.loadFromServer().catch(() => {})
  } catch (e) { /* ignore */ }
  try {
    const ui = useUiSizeStore()
    ui.reloadForScope()
    ui.loadFromServer().catch(() => {})
  } catch (e) { /* ignore */ }
  // 系统参数 / 品牌信息 / 字典 / 审批开关 / 字段显示名覆盖（每租户一份，切公司必须重拉）
  try { useSysConfig().loadSysConfig(true).catch(() => {}) } catch (e) { /* ignore */ }
  try { useAppConfigStore().refreshConfig().catch(() => {}) } catch (e) { /* ignore */ }
  try { clearDictCache() } catch (e) { /* ignore */ }
  try { clearApprovalGateCache() } catch (e) { /* ignore */ }
  // 多单位换算：换算行与「启用浮动单位」开关都是每租户一份，切公司必须清掉重拉
  try { clearUnitConvertCache() } catch (e) { /* ignore */ }
  try { reloadFieldLabelsForScope() } catch (e) { /* ignore */ }
}
