import request from '@/utils/request'

/**
 * 单据明细报表 API（销售各单据共用同一套封装）
 *
 * 各报表的后端接口形状完全一致（同一 Controller 基类），只有基础路径不同：
 *   /report/sale/order-detail     销售订单明细
 *   /report/sale/delivery-detail  销售出库明细
 *   /report/sale/invoice-detail   销售发票明细
 *   /report/sale/quote-detail     销售报价明细（2026-10-14 列表页筛选条件自定义推广补登记）
 *   /report/sale/contract-detail  销售合同明细（同上）
 *   /report/sale/return-detail    销售退货明细（同上）
 *
 * 之所以用工厂而不是写多份：多份封装一旦有人只改其中一份，各报表就会不一致 ——
 * 用户明确要求"各报表功能一模一样"。
 */
export function createBillDetailReportApi(base) {
  return {
    /** 全部可选列（系统字段 + 自定义字段）+ 全部可筛选条件（列设置/筛选设置的唯一来源） */
    getColumns: () => request.get(base + '/columns'),

    /**
     * 分页查询（一行 = 一条商品明细）
     * params: { page, size, sortBy, sortOrder, cols, f_*（筛选条件，入参名见 /columns 的 paramKeys） }
     * 返回: { records, total, page, size, stats, totals }
     */
    query: (params) => request.get(base, { params }),

    /** 按当前筛选条件导出 Excel（列 = 当前可见列），返回 blob 流 */
    exportFile: (params) => request.get(base + '/export', { params, responseType: 'blob' })
  }
}

export const saleOrderDetailApi = createBillDetailReportApi('/report/sale/order-detail')
export const saleDeliveryDetailApi = createBillDetailReportApi('/report/sale/delivery-detail')
export const saleInvoiceDetailApi = createBillDetailReportApi('/report/sale/invoice-detail')
export const saleQuoteDetailApi = createBillDetailReportApi('/report/sale/quote-detail')
export const saleContractDetailApi = createBillDetailReportApi('/report/sale/contract-detail')
export const saleReturnDetailApi = createBillDetailReportApi('/report/sale/return-detail')
// 销售换货明细（2026-11-27 补：列表页明细模式专属筛选条件此前未接 /columns）
export const saleExchangeDetailApi = createBillDetailReportApi('/report/sale/exchange-detail')
// 售后维修全明细（2026-11-27 补：维修明细 + 配件领用合并，编辑页两张明细终于进明细模式）
export const saleRepairDetailApi = createBillDetailReportApi('/report/sale/repair-all-detail')
