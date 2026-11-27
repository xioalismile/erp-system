// 客户包隐藏单据（构建期开关，2026-11-23）
//
// 背景：121 客户服务器明确不上「销售换货单 / 售后维修单」——要的是所有入口都看不到、点不到，
// 而不仅是数据库菜单标记隐藏（侧边栏由本前端静态菜单配置渲染，标记隐藏不生效）。
//
// 用法：.env.customer 里配置 VITE_HIDDEN_BILLS=SALE_EXCHANGE,SALE_REPAIR
//   - 侧边栏：menuConfig 过滤掉对应菜单项；
//   - 路由：/sale/exchanges、/sale/repairs 直接跳工作台（admin 也进不去）；
//   - 单据页按钮/页签/打印类型下拉：读到隐藏即不渲染。
// 不配置 = 与全量包完全一致（功能照常）。
const RAW = import.meta.env.VITE_HIDDEN_BILLS || ''

export const HIDDEN_BILLS = new Set(
  String(RAW)
    .split(',')
    .map(s => s.trim().toUpperCase())
    .filter(Boolean)
)

export function isBillHidden(code) {
  if (!code) return false
  return HIDDEN_BILLS.has(String(code).trim().toUpperCase())
}

/** 隐藏单据的页面路径前缀（路由守卫用） */
export const HIDDEN_BILL_ROUTE_PREFIXES = [
  ...(isBillHidden('SALE_EXCHANGE') ? ['/sale/exchanges'] : []),
  ...(isBillHidden('SALE_REPAIR') ? ['/sale/repairs'] : [])
]
