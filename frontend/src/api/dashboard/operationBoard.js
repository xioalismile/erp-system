// 经营看板（总经理视角 · 2026-10-07）
// 后端：com.erp.databoard.controller.OperationBoardController
// 说明：本接口只提供「生产 / 采购 / 质量 / 人力」；「销售 / 利润 / 回款 / 库存」复用既有数据看板三接口。
import request from '@/utils/request'

const BASE = '/data-board/operation'

export function getOperationBoard(period) {
  return request.get(BASE, { params: { period } })
}
