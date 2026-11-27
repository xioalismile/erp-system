/**
 * 单据按钮权限码工具（2026-09-19 新增）
 *
 * 目的：让**页面上的按钮全部受"设置中心 → 角色权限 → 按钮权限"控制**，
 * 且默认口径为「能看见菜单 = 该菜单下按钮全都有」（由 v-permission 的规则③保证：
 * 该对象下没有任何显式按钮配置时，命中菜单码即放行）。
 *
 * 按钮码格式：`<模块:对象>:<动作后缀>`，例如 `sale:orders:audit`、`inventory:stock-transfers:delete`。
 * 与设置中心 `RoleList.vue` 的 `buttonCode(moduleKey, itemKey, action)`（= `模块:对象:动作`）完全同源，
 * 也与后端 `BillActionPermissionResolver.MENU_CODE`（2026-09-20 起 32 条，已用 SQL 与 `sys_menu.permission_code` 逐条校验）一致。
 *
 * ⚠️ 维护约定：新增单据类型时，先查库确认 `sys_menu.permission_code`，**禁止臆造**；
 * 查不到菜单码就不要写 perm（不校验也比被静默隐藏强）。
 */

/** 单据类型编码（与设置中心「单据审批开关」一致）→ 菜单码（=按钮码前缀） */
export const BILL_TYPE_MENU_CODE = {
  SALE_QUOTE: 'sale:quotes',
  SALE_ORDER: 'sale:orders',
  SALE_CONTRACT: 'sale:contracts',
  SALE_DELIVERY: 'sale:deliveries',
  SALE_RETURN: 'sale:returns',
  // 2026-11-18：销售换货单（菜单 permission_code = sale:exchanges，与后端菜单初始化一致）
  SALE_EXCHANGE: 'sale:exchanges',
  // 2026-11-18：售后维修单（菜单 permission_code = sale:repairs）
  SALE_REPAIR: 'sale:repairs',
  SALE_INVOICE: 'sale:invoices',
  PURCHASE_REQUEST: 'purchase:requests',
  PURCHASE_ORDER: 'purchase:orders',
  PURCHASE_CONTRACT: 'purchase:contracts',
  PURCHASE_INBOUND: 'purchase:inbounds',
  PURCHASE_RETURN: 'purchase:returns',
  PURCHASE_INVOICE: 'purchase:invoices',
  PRODUCTION_ORDER: 'production:workorders',
  PRODUCTION_RECEIPT: 'production:receipts',
  MATERIAL_REQUISITION: 'inventory:material-requisitions',
  STOCK_TRANSFER: 'inventory:stock-transfers',
  STOCK_CHECK: 'inventory:stock-checks',
  OTHER_RECEIPT: 'inventory:other-receipts',
  OTHER_ISSUE: 'inventory:other-issues',
  SUBCONTRACT_ORDER: 'subcontract:orders',
  SUBCONTRACT_DELIVERY: 'subcontract:picks',
  SUBCONTRACT_RECEIPT: 'subcontract:inbounds',
  PAYMENT_RECEIPT: 'finance:receipt',
  PAYMENT_VOUCHER: 'finance:payment',
  PURCHASE_EXPENSE: 'finance:operation-expense',
  PURCHASE_PRE_PAYMENT: 'finance:purchase-pre-payments',
  BOM: 'masterdata:bom',
  // 2026-09-20 补登（菜单码取自 25_cloud_init_data.sql 的 sys_menu.permission_code，去掉 :view 后缀）

  OPERATION_REPORT: 'production:opreport',
  MRP_PLAN: 'production:mrp',
  SUBCONTRACT_RETURN: 'subcontract:returns',
  SUBCONTRACT_SUPPLEMENT: 'subcontract:supplements',
  // 2026-10-14 补登（页面在用但映射表缺失 → 编辑页/行内按钮权限码为空 = 开关不生效）；
  // 对象码与 menuConfig 模块:itemKey 逐字对齐（配置页所见 = 页面所用）
  SALE_PRE_RECEIPT: 'finance:sale-pre-receipts',
  OPERATION_EXPENSE: 'finance:operation-expense',
  COST_CALCULATION: 'finance:cost-calculation',
  SAMPLE_ORDER: 'sample:list',
  FREE_SAMPLE: 'sample:freesample-list'
}

/**
 * 页面动作 → 按钮码后缀（与后端 `BillActionRegistry.ACTION_BUTTON_SUFFIX` 对齐）。
 * 值为 null 表示"该动作当前没有按钮码"（如保存/复制）→ 不做权限判定，保持可见。
 */
export const ACTION_BUTTON_SUFFIX = {
  // 基础操作（设置中心已有的 7 个动作）
  add: 'add',
  edit: 'edit',
  delete: 'delete',
  export: 'export',
  import: 'import',
  print: 'print',
  audit: 'audit',
  // 单据动作（后端已预留后缀，本次一并接入设置中心）
  submit: 'submit',
  approve: 'audit',
  reject: 'reject',
  reverseApprove: 'reverseApprove',
  close: 'close',
  closeOrder: 'closeOrder',
  reopen: 'reopen',
  void: 'void',
  unvoid: 'unvoid',
  confirm: 'confirm',
  release: 'release',
  start: 'start',
  complete: 'complete',
  startCheck: 'startCheck',
  finishCheck: 'finishCheck',
  issue: 'issue',
  ship: 'ship',
  allocateCost: 'allocateCost',
  writeOff: 'writeOff',
  reverseWriteOff: 'reverseWriteOff',
  enable: 'enable',
  disable: 'disable',
  setDefault: 'setDefault',
  startSample: 'startSample',
  completeSample: 'completeSample',
  confirmSample: 'confirmSample',
  pushTo: 'push',
  // 无按钮码的动作（保存/复制）
  save: null,
  copy: null
}

/** 取单据类型的菜单码（按钮码前缀）；未登记返回空串（= 不做权限判定） */
export function menuCodeOf(billType) {
  if (!billType) return ''
  return BILL_TYPE_MENU_CODE[billType] || ''
}

/**
 * 生成某单据动作的按钮码。
 * @param {string} billType 单据类型（如 SALE_ORDER）
 * @param {string} action 动作（如 submit / approve / delete）
 * @returns {string} 按钮码；无法生成时返回空串（v-permission 收到空值 = 放行）
 */
export function billPermCode(billType, action) {
  const prefix = menuCodeOf(billType)
  const suffix = ACTION_BUTTON_SUFFIX[action]
  if (!prefix || !suffix) return ''
  return `${prefix}:${suffix}`
}
