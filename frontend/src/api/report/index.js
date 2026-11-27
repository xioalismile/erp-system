import request from '@/utils/request'

// ==================== CRM报表中心 ====================
export function crmFunnel(params) {
  return request.get('/report/crm/funnel', { params })
}
export function crmSalesRanking(params) {
  return request.get('/report/crm/sales-ranking', { params })
}
export function crmCustomerAnalysis(params) {
  return request.get('/report/crm/customer-analysis', { params })
}
export function crmPaymentAnalysis(params) {
  return request.get('/report/crm/payment-analysis', { params })
}
// CRM 明细
export function crmFunnelDetails(params) {
  return request.get('/report/crm/funnel-details', { params })
}
export function crmSalesRankingDetails(params) {
  return request.get('/report/crm/sales-ranking-details', { params })
}
export function crmCustomerAnalysisDetails(params) {
  return request.get('/report/crm/customer-analysis-details', { params })
}
export function crmPaymentAnalysisDetails(params) {
  return request.get('/report/crm/payment-analysis-details', { params })
}

// ==================== 销售报表中心 ====================
export function saleTargetCompletion(params) {
  return request.get('/report/sale/target-completion', { params })
}
export function saleOrderTrend(params) {
  return request.get('/report/sale/order-trend', { params })
}
export function saleShare(params) {
  return request.get('/report/sale/sales-share', { params })
}
export function saleGrossProfit(params) {
  return request.get('/report/sale/gross-profit', { params })
}
export function saleCommission(params) {
  return request.get('/report/sale/commission', { params })
}
// 销售 明细
export function saleTargetCompletionDetails(params) {
  return request.get('/report/sale/target-completion-details', { params })
}
export function saleOrderTrendDetails(params) {
  return request.get('/report/sale/order-trend-details', { params })
}
export function saleShareDetails(params) {
  return request.get('/report/sale/sales-share-details', { params })
}
export function saleGrossProfitDetails(params) {
  return request.get('/report/sale/gross-profit-details', { params })
}
export function saleCommissionDetails(params) {
  return request.get('/report/sale/commission-details', { params })
}

// ==================== 销售发票报表 ====================
export function saleInvoiceOverview(params) {
  return request.get('/report/sale/invoice/overview', { params })
}
export function saleInvoiceTrend(params) {
  return request.get('/report/sale/invoice/trend', { params })
}
export function saleInvoiceCustomerAnalysis(params) {
  return request.get('/report/sale/invoice/customer-analysis', { params })
}
export function saleInvoiceTaxAnalysis(params) {
  return request.get('/report/sale/invoice/tax-analysis', { params })
}
export function saleInvoiceDetails(params) {
  return request.get('/report/sale/invoice/details', { params })
}

// ==================== 采购报表中心 ====================
export function purchasePriceTrend(params) {
  return request.get('/report/purchase/price-trend', { params })
}
export function purchaseSupplierAnalysis(params) {
  return request.get('/report/purchase/supplier-analysis', { params })
}
export function purchaseExecution(params) {
  return request.get('/report/purchase/execution', { params })
}
// 采购 明细
export function purchasePriceTrendDetails(params) {
  return request.get('/report/purchase/price-trend-details', { params })
}
export function purchaseSupplierAnalysisDetails(params) {
  return request.get('/report/purchase/supplier-analysis-details', { params })
}
export function purchaseExecutionDetails(params) {
  return request.get('/report/purchase/execution-details', { params })
}

// ==================== 采购发票报表 ====================
export function purchaseSupplierInvoiceAnalysis(params) {
  return request.get('/report/purchase/invoice/supplier-analysis', { params })
}
export function purchaseInvoicePaymentStatus(params) {
  return request.get('/report/purchase/invoice/payment-status', { params })
}
export function purchaseInvoiceTaxAnalysis(params) {
  return request.get('/report/purchase/invoice/tax-analysis', { params })
}
export function purchaseInvoiceDetails(params) {
  return request.get('/report/purchase/invoice/details', { params })
}

// ==================== 库存报表中心 ====================
export function inventoryTurnover(params) {
  return request.get('/report/inventory/turnover', { params })
}
export function inventorySafetyWarning(params) {
  return request.get('/report/inventory/safety-warning', { params })
}
export function inventoryBatchAging(params) {
  return request.get('/report/inventory/batch-aging', { params })
}
export function inventoryShortage(params) {
  return request.get('/report/inventory/shortage', { params })
}
// 库存 明细
export function inventoryTurnoverDetails(params) {
  return request.get('/report/inventory/turnover-details', { params })
}
export function inventorySafetyWarningDetails(params) {
  return request.get('/report/inventory/safety-warning-details', { params })
}
export function inventoryBatchAgingDetails(params) {
  return request.get('/report/inventory/batch-aging-details', { params })
}
export function inventoryShortageDetails(params) {
  return request.get('/report/inventory/shortage-details', { params })
}

// ==================== 生产报表中心 ====================
export function productionWorkOrderCompletion(params) {
  return request.get('/report/production/work-order-completion', { params })
}
export function productionEfficiency(params) {
  return request.get('/report/production/efficiency', { params })
}
export function productionCostAnalysis(params) {
  return request.get('/report/production/cost-analysis', { params })
}
export function productionWorkHours(params) {
  return request.get('/report/production/work-hours', { params })
}
// 生产 明细
export function productionWorkOrderCompletionDetails(params) {
  return request.get('/report/production/work-order-completion-details', { params })
}
export function productionEfficiencyDetails(params) {
  return request.get('/report/production/efficiency-details', { params })
}
export function productionCostAnalysisDetails(params) {
  return request.get('/report/production/cost-analysis-details', { params })
}

// ==================== 财务报表中心 ====================
export function financeArApSummary(params) {
  return request.get('/report/finance/ar-ap-summary', { params })
}
export function financeAging(params) {
  return request.get('/report/finance/aging', { params })
}
export function financeProfit(params) {
  return request.get('/report/finance/profit', { params })
}
export function financeExpenseAllocation(params) {
  return request.get('/report/finance/expense-allocation', { params })
}
// 财务 明细
export function financeArApDetails(params) {
  return request.get('/report/finance/ar-ap-details', { params })
}
export function financeAgingDetails(params) {
  return request.get('/report/finance/aging-details', { params })
}
export function financeProfitDetails(params) {
  return request.get('/report/finance/profit-details', { params })
}
export function financeExpenseAllocationDetails(params) {
  return request.get('/report/finance/expense-allocation-details', { params })
}

// ==================== 财务月度分析报表（2026-09-07 立项） ====================
export function financeMonthlyInvoiceSummary(params) {
  return request.get('/report/finance/monthly/invoice-summary', { params })
}
export function financeMonthlyInvoiceDetails(params) {
  return request.get('/report/finance/monthly/invoice-details', { params })
}
export function financeMonthlySalerSummary(params) {
  return request.get('/report/finance/monthly/saler-summary', { params })
}
export function financeMonthlySalerDetails(params) {
  return request.get('/report/finance/monthly/saler-details', { params })
}
export function financeMonthlyChannelSummary(params) {
  return request.get('/report/finance/monthly/channel-summary', { params })
}
export function financeMonthlyChannelDetails(params) {
  return request.get('/report/finance/monthly/channel-details', { params })
}
