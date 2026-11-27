import request from '@/utils/request'
// 单据明细报表通用封装（列表页明细模式专属筛选条件元数据的来源，与明细报表页同一套接口）
import { createBillDetailReportApi } from '@/api/report/billDetailReport'

/** 生产领料单明细报表接口（/columns 元数据供列表页「筛选条件自定义」明细模式复用） */
export const materialRequisitionDetailApi = createBillDetailReportApi('/report/inventory/material-requisition-detail')

/** 生产退料单明细报表接口（与领料同表，fixedWhere bill_type=RETURN；生产模块 2026-10-14 筛选推广补登记） */
export const materialReturnDetailApi = createBillDetailReportApi('/report/production/material-return-detail')

/** 生产补料单明细报表接口（与领料同表，fixedWhere bill_type=SUPPLEMENT；同上） */
export const materialSupplementDetailApi = createBillDetailReportApi('/report/production/material-supplement-detail')

export function listMaterialRequisition(params) {
  return request({ url: '/inventory/material-requisitions/page', method: 'get', params })
}
// 列表页顶部统计卡片数据（与列表同数据权限口径）
// params 可传 { billType } 按单据类型统计（PICK/RETURN/SUPPLEMENT）；不传=统计全部类型
export function getMaterialRequisitionDashboard(params) {
  return request({ url: '/inventory/material-requisitions/dashboard', method: 'get', params })
}
export function listAllMaterialRequisition() {
  return request({ url: '/inventory/material-requisitions/list', method: 'get' })
}
// 按工单号查询已生效领料单（用于退料单关联原领料单）
// 优先调用 /approved 专用接口，若后端未提供则回退到分页接口加过滤条件
export async function listApprovedByWorkOrder(workOrderNo, billType = 'PICK') {
  try {
    return await request({
      url: '/inventory/material-requisitions/approved',
      method: 'get',
      params: { workOrderNo, billType }
    })
  } catch (e) {
    // 回退到分页查询
    const res = await listMaterialRequisition({
      current: 1, size: 200,
      workOrderNo,
      billType,
      status: '已生效'
    })
    return res?.records || res || []
  }
}
export function getMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}`, method: 'get' })
}
export function saveMaterialRequisition(data) {
  return request({ url: '/inventory/material-requisitions', method: 'post', data })
}
export function updateMaterialRequisition(id, data) {
  return request({ url: `/inventory/material-requisitions/${id}`, method: 'put', data })
}
export function deleteMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}`, method: 'delete' })
}
// 提交（领料单/补料单/退料单共用）：后端按审批开关决定"待审核"还是"提交即生效"
export function submitMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}/submit`, method: 'post' })
}
export function approveMaterialRequisition(id, approver) {
  return request({ url: `/inventory/material-requisitions/${id}/approve`, method: 'post', params: { approver } })
}
export function closeMaterialRequisition(id, reason) {
  return request({ url: `/inventory/material-requisitions/${id}/close`, method: 'post', params: { reason } })
}
// 反结案（领料单/退料单/补料单共用）: 已关闭 → 已生效
export function reopenMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}/reopen`, method: 'post' })
}
export function cancelMaterialRequisition(id, reason) {
  return request({ url: `/inventory/material-requisitions/${id}/cancel`, method: 'post', params: { reason } })
}

// ===== 以下为状态流转统一接口 =====
export function rejectMaterialRequisition(id, reason) {
  return request({ url: `/inventory/material-requisitions/${id}/reject`, method: 'post', data: { reason } })
}
export function reverseApproveMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}/reverse-approve`, method: 'post' })
}
export function voidMaterialRequisition(id, reason) {
  return request({ url: `/inventory/material-requisitions/${id}/void`, method: 'post', data: { reason } })
}
export function unvoidMaterialRequisition(id) {
  return request({ url: `/inventory/material-requisitions/${id}/unvoid`, method: 'post' })
}
