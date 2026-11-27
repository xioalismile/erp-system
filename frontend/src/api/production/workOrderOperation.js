// 工单工序（车间执行）API：工序操作面板的全部取数与动作
import request from '@/utils/request'

/** 某工单的全部工序实例 */
export function listWorkOrderOperation(orderId) {
  return request({ url: '/production/work-order-operation/list', method: 'get', params: { orderId } })
}

/**
 * 车间看板（线路图）数据：按「工作中心 + 工单」或「工单」分组的在制工序线路。只读接口。
 * @param {object} params {
 *   view: 'workcenter'|'order',   // workcenter=车间排程看板 / order=车间进度跟踪
 *   workCenterId?,                // 工作中心筛选（仅 workcenter 视图）
 *   includeFinished?,             // 是否含已完工/已取消
 *   assigneeFilter?,              // 派工状态：'all'(默认，不传=全量) | 'assigned' | 'unassigned'
 *   keyword?,                     // 模糊匹配 工单号/物料编码/物料名称（车间进度跟踪筛选条）
 *   status?,                      // 工序状态精确匹配（8 值之一）
 *   planEndFrom?, planEndTo?,     // 计划完工区间 yyyy-MM-dd（工序级）
 *   sortField?, sortOrder?        // 列头排序：orderNo/materialCode/materialName/status/planEndDate + asc/desc
 * }  以上新增参数全部可选，不传 = 改造前行为
 * @returns {Promise<Array<{key, name, productName, orderId, orderNo,
 *   materialCode, materialName, specification, workCenterId, workCenterName, ops: Array}>>}
 *   每个 op 含 unassigned(boolean)：true=未派工（状态=待派工 或 负责人为空），口径由后端算，前端只渲染。
 */
export function getOperationBoard(params) {
  return request({ url: '/production/work-order-operation/board', method: 'get', params })
}

/**
 * APS 排程看板（泳道 + 负载热力 + 任务块 + 物料齐套 + 未派标识）：只读。
 * 一条泳道 = 一个工作中心；块 = 某工单某道工序占用该中心的时段。
 * @param {object} params {
 *   days?: number,                 // 时间轴跨度 7/14/30，默认 7
 *   workCenterId?: number,         // 只看某个工作中心
 *   includeFinished?: boolean,     // 是否含已完工/已取消
 *   assigneeFilter?: string        // 'all'(默认) | 'assigned' | 'unassigned'
 * }
 * @returns {Promise<{rangeStart, days, metrics:{orderCount,overloadedCenters,unassignedCount,avgLoadPercent},
 *   alerts: Array<{workCenterId,workCenterName,date,hours,capacity,overloadHours}>,
 *   lanes: Array<{workCenterId,workCenterName,capacityPerDay,capacityUnit,efficiency,capacityMissing,loadPercent,
 *     days: Array<{date,hours,loadPercent,overloaded}>,
 *     blocks: Array<{operationRowId,orderId,orderNo,lineNo,operationName,seqIndex,seqTotal,assigneeId,assigneeName,
 *       unassigned,planStartDate,planEndDate,occupancyDays,hours,overloaded,overloadHours,
 *       kittingStatus,kittingDate,kittingShortageCount,waitDays,waitFrom}>}>}}
 *   kittingStatus: READY(已齐套·实际入库) / EXPECTED(预计齐套) / SHORT(未齐套) / NO_BOM(无 BOM)
 */
export function getScheduleBoard(params) {
  return request({ url: '/production/work-order-operation/schedule-board', method: 'get', params })
}

/**
 * 应用自动重排：把所选工单的工序计划开工/完工日期按"最近空档日"重排并回写。
 * @param {object} data { orderIds: number[], days: number }
 * @returns {Promise<{updated: number, details: Array}>}
 */
export function applyReschedule(data) {
  return request({ url: '/production/work-order-operation/apply-reschedule', method: 'post', data })
}

/**
 * 我今天要做的工序（个人工作台卡片，只读）：当前登录人 = 工序负责人、工序未完工/未取消、所属工单已生效。
 * @param {object} params { days?: number } 只取「计划完工日期 ≤ 今天 + days 天」，默认 7
 */
export function getMyOpenOperations(params) {
  return request({ url: '/production/work-order-operation/my-open-operations', method: 'get', params })
}

/** 某道工序的操作日志（时间倒序） */
export function getOperationLogs(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}/logs`, method: 'get' })
}

/**
 * 【第 4 版 2026-10-08】「谁可以改负责人」判定：返回该工序档位 + 当前登录人能否改负责人。
 * 工序操作面板据此决定「负责人」下拉是"可改"还是"只读"（后端才是最终门槛）。
 * @param {number} operationRowId 工单工序行ID
 * @returns {Promise<{scope: string, canAssign: boolean}>} scope: 全部人/当前负责人/指定角色/工单开单人
 */
export function getOperationAssignPermission(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}/assign-permission`, method: 'get' })
}

/**
 * 状态与动作元数据（状态清单 + 每个状态允许的动作）：按钮/动作下拉渲染依据由后端下发。
 * @param {string} [operationType] 工序类型（状态类型/数量类型/入库类型/金额类型）。
 *   传了则按工序类型过滤动作：只有「数量类型」保留「数量报工」，其余类型剔除；不传则与改造前一致。
 */
export function getOperationStatusMeta(operationType) {
  const params = {}
  if (operationType) params.operationType = operationType
  return request({ url: '/production/work-order-operation/status-meta', method: 'get', params })
}

/**
 * 执行工序动作：派工 / 开工 / 暂停 / 恢复 / 完工 / 检验通过 / 检验退回 / 取消 / 数量报工 / 更新计划
 * @param {number} operationRowId 工单工序行ID
 * @param {object} data { action, assigneeId?, assigneeName?, quantity?, rejectedQuantity?, planEndDate?, remark? }
 */
export function actOperation(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/action`, method: 'post', data })
}

// ---------------- 第 4 期：回退确认 / 判废确认 / 下推补料 ----------------
// 这三个动作不改工序状态（只记数量 + 留痕 / 生成补料单），所以走独立端点，不走 /action 状态机入口

/** 回退确认：下道工序退回本道的数量（累计） @param {object} data { quantity, remark } */
export function confirmOperationRework(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/rework`, method: 'post', data })
}

/** 判废确认：本道师傅判定"回退料中不能用"的数量（累计） @param {object} data { quantity, remark } */
export function confirmOperationScrap(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/scrap`, method: 'post', data })
}

/** 下推补料：按"尚未下推的判废数量 × 主BOM 子件用量"生成补料领料单，返回 { billNo } */
export function pushOperationSupplement(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/push-supplement`, method: 'post', data: data || {} })
}

// ---------------- 第 11 期：入库类型工序一键发起入库单 / 删除已取消工序 ----------------

/**
 * 入库类型工序：一键发起成品入库单（草稿），按"剩余可入库量 = 工序合格产出 − 已下推入库量"防重复。
 * @param {number} operationRowId 工单工序行ID
 * @param {object} data { quantity: 入库数量 }
 * @returns {Promise<{ billNo: string, operation: object }>}
 */
export function pushOperationReceipt(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/push-receipt`, method: 'post', data: data || {} })
}

/** 删除一道工序（仅允许「已取消」的行）：用户口径的第二条回程「删掉重新添加」 */
export function deleteWorkOrderOperation(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}`, method: 'delete' })
}

// ---------------- 第 12 期：工序检验（质检模板 + 填质检） ----------------

/**
 * 取该工序应使用的质检模板：工单工序显式绑定 → 按工序绑定 → 按物料绑定；都没有返回 null。
 * @returns {Promise<object|null>} 模板（含 items 检验项明细）
 */
export function getOperationQcTemplate(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}/qc-template`, method: 'get' })
}

/**
 * 提交工序检验（仅「完工待检」状态）：按质检类型自动判定，合格 → 已完工；不合格 → 按处理方式处置后退回。
 * @param {number} operationRowId 工单工序行ID
 * @param {object} data { qcType, inspectQty, qualifiedQty, defectQty, handleType, handleOperationName, handleRemark, remark,
 *                        items: [{ itemName, measuredValue }] }
 * @returns {Promise<{ operation: object, record: object, result: string, handleBillNo: string|null }>}
 */
export function submitOperationQc(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/qc`, method: 'post', data: data || {} })
}

// ---------------- 2026-09-28：工序通知（@下道工序负责人 / 手选员工） ----------------

/**
 * 工序通知：把一条系统通知发给所选员工（员工档案未绑定登录账号的会被跳过），落到对方消息中心铃铛 + 工作台。
 * @param {number} operationRowId 工单工序行ID
 * @param {object} data { receiverEmployeeIds: number[], content?: string } content 为空则用默认模板
 * @returns {Promise<{ sent: number, receivers: string[], skippedEmployeeIds: number[] }>}
 */
export function notifyOperation(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/notify`, method: 'post', data: data || {} })
}

// ---------------- 2026-10-25：扫码认领与按人报工 ----------------

/**
 * 工序详情（扫码/面板用）：含在岗共同负责人、我的认领数量/已报/剩余、能否认领及原因。
 * @returns {Promise<object>} 工序行（workers / canClaim / claimDisabledReason / myClaimQuantity /
 *   myReportedQuantity / myRemainingQuantity / remainingClaimable / allowScanClaim）
 */
export function getOperationWorkerDetail(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}/detail`, method: 'get' })
}

/** 在岗共同负责人（主负责人不在参与人表时补一行，primaryAssignee=true） */
export function listOperationWorkers(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}/workers`, method: 'get' })
}

/** 【2026-11-26】负责人多选：整份名单提交，第一位为主负责人，其余为共同负责人 */
export function setOperationAssignees(operationRowId, employeeIds) {
  return request({
    url: `/production/work-order-operation/${operationRowId}/assignees`,
    method: 'put',
    data: { employeeIds: employeeIds || [] }
  })
}

/** 扫码认领「我来做」：加入为共同负责人；认领数量可空（空 = 不设个人上限） */
export function claimOperation(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/claim`, method: 'post', data: data || {} })
}

/** 退出认领：已报数量保留，未报份额释放 */
export function unclaimOperation(operationRowId) {
  return request({ url: `/production/work-order-operation/${operationRowId}/unclaim`, method: 'post', data: {} })
}

/**
 * 扫码报工：直接填本次完成数量（不打开任何单据）。
 * @param {object} data { quantity, rejectedQuantity?, employeeId?, employeeName?, idemKey?, remark? }
 * @returns {Promise<{ operation: object, flow: object }>}
 */
export function reportOperation(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/report`, method: 'post', data: data || {} })
}

/** 撤销报工：本人撤自己当班最近一笔；传 flowId 由管理员撤指定一笔 */
export function undoOperationReport(operationRowId, data) {
  return request({ url: `/production/work-order-operation/${operationRowId}/undo-report`, method: 'post', data: data || {} })
}
