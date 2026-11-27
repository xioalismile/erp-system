/**
 * 页面按钮权限码工具（2026-09-20 全站按钮组件化）
 *
 * 目的：让**系统内所有页面按钮**统一受「设置中心 → 角色权限设置 → 按钮权限」控制，
 * 口径与单据页完全一致：`<模块:对象>:<动作>`，例如 `crm:pool:query`、`system:sys-param:save`。
 *
 * 默认口径（用户 2026-09-19 明确）：**能看见菜单 = 该菜单下按钮全都有**。
 * 由 `v-permission` 的规则③保证 —— 该「模块:对象」下没有任何显式按钮配置时，
 * 命中菜单码即放行；只有管理员在设置中心勾过该页面的按钮，该页才进入严格模式。
 *
 * 本文件是**全站唯一动作词表**：RoleList（设置中心界面）与所有页面都从这里取动作清单，
 * 避免两边各写一份导致「页面用了某动作、设置中心没有这一项」→ 该按钮在严格模式下被静默隐藏。
 *
 * ⚠️ 维护约定：
 *   1. 页面新增动作时，必须先在 `BUTTON_ACTION_GROUPS` 里登记，再在页面里用；
 *   2. `perm(action)` 对未登记的动作返回空串 → `v-permission` 放行（宁可不管，也不能误隐藏）。
 */

import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MENU_GROUPS } from '@/config/menuConfig'

/**
 * 全站动作词表（分组仅用于设置中心排版；`code` 即权限码最后一段）。
 * 注意：`close` / `confirm` 已被单据流转占用（结案 / 确认），弹窗按钮另用 dlg* 前缀，避免撞码。
 */
export const BUTTON_ACTION_GROUPS = [
  {
    label: '基础操作',
    key: 'base',
    actions: [
      { code: 'query', label: '查询' },
      { code: 'reset', label: '重置' },
      { code: 'refresh', label: '刷新' },
      { code: 'view', label: '查看' },
      { code: 'add', label: '新增' },
      { code: 'edit', label: '编辑' },
      { code: 'delete', label: '删除' },
      { code: 'export', label: '导出' },
      { code: 'import', label: '导入' },
      { code: 'print', label: '打印' },
      { code: 'columnSetting', label: '列设置' },
      { code: 'filterSetting', label: '筛选设置' },
      // 提权动作（2026-10-13）：仅对限定对象出现在配置页（见 ACTION_LIMITED_TO_PREFIXES），
      // 判定口径=默认隐藏（管理员或有显式勾选才显示），与后端「管理员或显式按钮码」同源
      { code: 'resetPassword', label: '重置密码' },
      { code: 'assignRole', label: '分配角色' }
    ]
  },
  {
    label: '编辑与保存',
    key: 'edit',
    actions: [
      { code: 'save', label: '保存' },
      { code: 'saveDraft', label: '保存草稿' },
      { code: 'saveAndBack', label: '保存并返回' },
      { code: 'copy', label: '复制' },
      { code: 'upload', label: '上传' },
      { code: 'download', label: '下载' },
      { code: 'downloadTemplate', label: '下载模板' },
      { code: 'pick', label: '选择/选单' },
      { code: 'addRow', label: '添加行' },
      { code: 'deleteRow', label: '删除行' },
      { code: 'replace', label: '替代出库' },
      { code: 'selectAll', label: '全选' },
      { code: 'invertSelect', label: '反选' },
      { code: 'fill', label: '批量填充' },
      { code: 'expand', label: '展开' },
      { code: 'collapse', label: '收起' }
    ]
  },
  {
    label: '弹窗按钮',
    key: 'dialog',
    actions: [
      { code: 'dlgConfirm', label: '确定' },
      { code: 'dlgCancel', label: '取消' },
      { code: 'dlgClose', label: '关闭' }
    ]
  },
  {
    label: '单据流转',
    key: 'flow',
    actions: [
      { code: 'submit', label: '提交' },
      { code: 'audit', label: '审核' },
      { code: 'reject', label: '驳回' },
      { code: 'reverseApprove', label: '重置草稿' },
      { code: 'close', label: '结案' },
      { code: 'reopen', label: '反结案' },
      { code: 'void', label: '作废' },
      { code: 'unvoid', label: '反作废' },
      { code: 'confirm', label: '确认' },
      { code: 'release', label: '下达' },
      { code: 'start', label: '开始' },
      { code: 'complete', label: '完工' },
      { code: 'startCheck', label: '开始盘点' },
      { code: 'finishCheck', label: '完成盘点' },
      { code: 'issue', label: '发料' },
      { code: 'ship', label: '发货' },
      { code: 'closeOrder', label: '关单' },
      { code: 'allocateCost', label: '分摊成本' },
      { code: 'writeOff', label: '核销' },
      { code: 'reverseWriteOff', label: '反核销' },
      { code: 'enable', label: '启用' },
      { code: 'disable', label: '停用' },
      { code: 'setDefault', label: '设为默认' },
      { code: 'startSample', label: '开始打样' },
      { code: 'completeSample', label: '完成打样' },
      { code: 'confirmSample', label: '确认打样' },
      { code: 'pushTo', label: '下推' }
    ]
  },
  {
    label: '业务扩展',
    key: 'biz',
    actions: [
      { code: 'assign', label: '分配' },
      { code: 'claim', label: '领取' },
      { code: 'recycle', label: '回收' },
      { code: 'convert', label: '转单' },
      { code: 'setPrimary', label: '设为主联系人' },
      { code: 'generatePurchase', label: '生成采购订单' },
      { code: 'generateWorkOrder', label: '生成生产订单' },
      { code: 'generateSubcontract', label: '生成委外订单' },
      { code: 'markRead', label: '标记已读' },
      { code: 'preview', label: '预览' },
      { code: 'apply', label: '应用' },
      { code: 'resetDefault', label: '恢复默认' },
      { code: 'testConnection', label: '测试连接' },
      { code: 'refreshCache', label: '刷新缓存' },
      { code: 'enableAll', label: '全部开启' },
      { code: 'disableAll', label: '全部关闭' },
      { code: 'clear', label: '清空' },
      { code: 'deploy', label: '部署' },
      { code: 'rollback', label: '回滚' },
      { code: 'pack', label: '打包' },
      // 销售过程动作量化 / 销售预警（2026-10-07）新增动作词
      { code: 'promotion', label: '推广登记' },
      { code: 'batchAdd', label: '批量录入' },
      { code: 'handle', label: '处理' },
      { code: 'closeWarning', label: '关闭预警' },
      { code: 'reassign', label: '重新分配' },
      { code: 'override', label: '手工覆盖月目标' },
      // 售后维修过程动作（2026-11-19）：与后端维修单按钮码同口径
      { code: 'receive', label: '收件入库' },
      { code: 'issueParts', label: '配件领用' },
      { code: 'return', label: '返还客户' },
      { code: 'transferExchange', label: '转换货' },
      { code: 'transferReturn', label: '转退货' }
    ]
  },
  {
    label: '设备管理',
    key: 'device',
    actions: [
      { code: 'genKey', label: '生成接入凭证' },
      { code: 'rotateKey', label: '轮换密钥' },
      { code: 'revokeKey', label: '吊销凭证' },
      { code: 'testRead', label: '点位试读' },
      { code: 'simulate', label: '模拟上报' },
      { code: 'dispatch', label: '下发任务' },
      { code: 'writeParams', label: '参数下发' },
      { code: 'confirmReport', label: '确认报工' },
      { code: 'rejectReport', label: '驳回报工' },
      { code: 'rebind', label: '改绑工序' },
      { code: 'unbind', label: '解除关联' },
      { code: 'qrcode', label: '设备二维码' },
      { code: 'toRepair', label: '转维修' },
      { code: 'ignore', label: '忽略报警' },
      { code: 'startRepair', label: '开始维修' },
      { code: 'finishRepair', label: '维修完工' },
      { code: 'acceptRepair', label: '验收维修' },
      { code: 'replacePart', label: '更换备件' }
    ]
  }
]

/** 扁平动作清单（校验用） */
export const BUTTON_ACTION_CODES = BUTTON_ACTION_GROUPS.flatMap(g => g.actions.map(a => a.code))

/**
 * 动作 → 仅哪些「模块:对象」在配置页展示（2026-10-13）。
 * 未登记的动作对所有对象展示。提权动作只对真正有该按钮的页面出现，
 * 避免全站 200+ 页面卡片都冒出"重置密码/分配角色"勾选行。
 */
export const ACTION_LIMITED_TO_PREFIXES = {
  resetPassword: ['basedata:employee', 'system:user'],
  assignRole: ['system:user']
}

/**
 * 设置中心（模块 key = 'system'，含各设置子页）**专用动作集**。
 * 设置页不是单据，不需要"提交/审核/驳回/作废/结案/分配/领取"这类流转与业务扩展动作；
 * 在「角色权限设置 → 按钮权限」里，设置中心各页只展示下面这些动作（2026-10-08 用户反馈：
 * 设置中心的按钮不应和单据一模一样）。
 */
export const SETTINGS_ACTION_CODES = [
  'view', 'refresh', 'reset', 'save', 'edit', 'add', 'delete',
  'export', 'import', 'upload', 'download', 'downloadTemplate',
  'preview', 'apply', 'resetDefault', 'testConnection', 'refreshCache',
  'enable', 'disable', 'setDefault', 'enableAll', 'disableAll', 'clear',
  'expand', 'collapse', 'columnSetting', 'filterSetting'
]

/**
 * 设置中心「写动作」集合（2026-10-13）。
 * 这些动作在设置中心（system:* 对象）的判定口径 = 默认隐藏：
 * 管理员或有显式勾选才显示/可点，与后端拦截器「默认拒绝」同源。
 * 只读动作（view/refresh/reset/columnSetting/filterSetting/preview/expand/collapse/download/downloadTemplate）不在其列。
 */
export const SETTINGS_WRITE_ACTIONS = [
  'save', 'edit', 'add', 'delete', 'import', 'export', 'upload',
  'apply', 'resetDefault', 'testConnection', 'refreshCache',
  'enable', 'disable', 'setDefault', 'enableAll', 'disableAll', 'clear'
]

/**
 * 提权动作集合（2026-10-13）：全对象默认隐藏，管理员或有显式勾选才显示。
 * 与后端「管理员或显式按钮码」守卫同源（员工档案重置密码、用户管理分配角色等）。
 */
export const PRIVILEGED_ACTIONS = ['resetPassword', 'assignRole']

/**
 * 默认可见动作（2026-10-19 用户口径：默认勾选，但必须能真实关闭）：
 * 未显式关闭 → 放行；设置中心取消勾选并保存后写入"关闭标记" → 真正隐藏。
 * 关闭标记 = `<模块:对象>:<动作>Off`（形如 sale:orders:filterSettingOff），
 * 与普通按钮码同样存 sys_role_permission（permission_type='按钮'），可持久化、重登仍生效。
 */
export const DEFAULT_VISIBLE_ACTIONS = ['filterSetting']

/** 默认可见动作的"关闭标记"码 */
export function defaultVisibleOffCode (prefix, action) {
  return `${prefix}:${action}Off`
}

/** 判断一个权限码是否为默认可见动作的"关闭标记" */
export function isDefaultVisibleOffCode (code) {
  return DEFAULT_VISIBLE_ACTIONS.some(a => typeof code === 'string' && code.endsWith(`:${a}Off`))
}

// ---------------------------------------------------------------------------
// 路由 / 页面 → 权限前缀（`模块:对象`）
// ---------------------------------------------------------------------------

/**
 * 手工补登记：菜单树里没有独立菜单、但确实需要按钮权限的页面。
 * 值 = 「模块:对象」前缀（必须与设置中心已有的对象码同源，禁止臆造）。
 */
const EXTRA_PREFIX_BY_PATH = {
  // 设置中心子项以外的二级/三级页面：沿用其父页面对象码
  '/basedata/material/new': 'basedata:material',
  '/basedata/material/edit': 'basedata:material',
  '/report/custom/designer': 'report:custom',
  '/report/dashboard/list': 'report:custom',
  '/report/dashboard/view': 'report:custom',
  '/admin/tenant/form': 'platform:tenant-manage',
  '/admin/tenant/renew': 'platform:tenant-manage'
}

function walkItems (items, groupKey, out) {
  for (const item of (items || [])) {
    if (item.itemKey && item.path) {
      out.set(item.path, `${groupKey}:${item.itemKey}`)
    }
    if (item.children && item.children.length) walkItems(item.children, groupKey, out)
  }
}

const PATH_PREFIX_MAP = (() => {
  const map = new Map()
  for (const g of MENU_GROUPS) walkItems(g.items, g.key, map)
  return map
})()

/**
 * 由路由解析「模块:对象」前缀。
 * 顺序：① 显式补登记 → ② 设置中心子项精确匹配 → ③ 菜单路径精确匹配 → ④ 最长菜单路径前缀匹配
 * （④ 让编辑页/详情页/子页面自动落到父页面对象码，例如 `/sale/orders/edit/12` → `sale:orders`）。
 * @param {string} path 路由 path（不含 query）
 * @param {string} itemQuery 路由 query 里的 item（设置中心子项用）
 */
export function prefixFromRoute (path, itemQuery) {
  if (!path) return ''
  if (EXTRA_PREFIX_BY_PATH[path]) return EXTRA_PREFIX_BY_PATH[path]
  // 设置中心子项：/system/settings?item=xxx
  if (itemQuery) {
    const hit = PATH_PREFIX_MAP.get(`${path}?item=${itemQuery}`)
    if (hit) return hit
  }
  const exact = PATH_PREFIX_MAP.get(path)
  if (exact) return exact
  // 最长前缀匹配（编辑页 / 子页面 / 详情页）
  let best = ''
  let bestPrefix = ''
  for (const [menuPath, prefix] of PATH_PREFIX_MAP) {
    if (menuPath.includes('?item=')) continue
    if (path === menuPath || path.startsWith(menuPath + '/')) {
      if (menuPath.length > best.length) {
        best = menuPath
        bestPrefix = prefix
      }
    }
  }
  return bestPrefix
}

/**
 * 页面内取权限码的入口。
 *
 * 用法（在 `<script setup>` 里）：
 *   import { usePagePerm } from '@/utils/pagePerm'
 *   const perm = usePagePerm()            // 自动按当前路由解析前缀
 *   const perm = usePagePerm('crm:pool')  // 子页面/组件显式指定前缀
 *
 * 模板里： `<el-button v-permission="perm('query')">查询</el-button>`
 *
 * @returns {(action: string) => string} 传入动作码，返回完整按钮码；无法解析时返回空串（v-permission 放行）
 */
export function usePagePerm (explicitPrefix) {
  const route = useRoute()
  const prefix = computed(() => {
    if (explicitPrefix) return explicitPrefix
    return prefixFromRoute(route.path, route.query && route.query.item)
  })
  return function perm (action) {
    if (!prefix.value || !action) return ''
    if (!BUTTON_ACTION_CODES.includes(action)) return ''
    return `${prefix.value}:${action}`
  }
}

/**
 * 无组件上下文（纯函数）场景下拼码。
 * @param {string} prefix 「模块:对象」
 * @param {string} action 动作
 */
export function permCode (prefix, action) {
  if (!prefix || !action || !BUTTON_ACTION_CODES.includes(action)) return ''
  return `${prefix}:${action}`
}
