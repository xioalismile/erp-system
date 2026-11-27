import request from '@/utils/request'

/**
 * 卡片式自定义看板（数据看板 → 自定义看板）+ 工作台自定义卡
 * 后端：CustomBoardController @RequestMapping("/api/board/custom")
 */
const BASE = '/board/custom'

/** 列表（boardType: board=自定义看板 / workbench=工作台自定义卡） */
export function listBoards(params) {
  return request({ url: `${BASE}/list`, method: 'get', params })
}

export function getBoardDetail(id) {
  return request({ url: `${BASE}/${id}`, method: 'get' })
}

/** 逐卡取数（单卡异常隔离） */
export function getBoardData(id, period = 'month') {
  return request({ url: `${BASE}/${id}/data`, method: 'get', params: { period } })
}

export function createBoard(data) {
  return request({ url: BASE, method: 'post', data })
}

export function updateBoard(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'put', data })
}

export function deleteBoard(id) {
  return request({ url: `${BASE}/${id}`, method: 'delete' })
}

/** 设计器数据源清单 */
export function getBoardSources() {
  return request({ url: `${BASE}/meta/sources`, method: 'get' })
}

/** 某数据源的字段清单（维度 / 数值 / 时间） */
export function getBoardFields(source) {
  return request({ url: `${BASE}/meta/fields`, method: 'get', params: { source } })
}

/** 设计器单卡预览（不落库）：公式实时预览与图表预览 */
export function previewCard(card, period = 'month') {
  return request({ url: `${BASE}/preview`, method: 'post', params: { period }, data: { card } })
}

/** 卡片下钻：点数字/图表点 → 单据级明细清单（dimLabel 为空=指标卡整期明细；itemIndex 用于多取数项卡） */
export function drillCard(card, period = 'month', dimLabel = '', limit = 200, itemIndex = null) {
  return request({
    url: `${BASE}/preview/drill`,
    method: 'post',
    params: {
      period,
      dimLabel: dimLabel || undefined,
      limit,
      itemIndex: itemIndex === null || itemIndex === undefined ? undefined : itemIndex
    },
    data: { card }
  })
}

/** 某数据源可关联的单据清单（多级关联：字段写法「关联标识.字段名」） */
export function getBoardJoins(source) {
  return request({ url: `${BASE}/meta/joins`, method: 'get', params: { source } })
}

/** 现有看板页（finance / sale / inventory / operation）的卡片布局：每登录人每页一份 */
export function getPageLayout(key) {
  return request({ url: `${BASE}/page`, method: 'get', params: { key } })
}

export function savePageLayout(key, cards) {
  return request({ url: `${BASE}/page`, method: 'put', params: { key }, data: { cards } })
}

/** 当前登录人的工作台自定义卡 */
export function getWorkbenchCards() {
  return request({ url: `${BASE}/workbench`, method: 'get' })
}

/** 保存当前登录人的工作台自定义卡（最多 6 张） */
export function saveWorkbenchCards(cards) {
  return request({ url: `${BASE}/workbench`, method: 'put', data: { cards } })
}
