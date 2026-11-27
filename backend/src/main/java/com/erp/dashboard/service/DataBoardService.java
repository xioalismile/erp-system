package com.erp.dashboard.service;

import java.util.Map;

/**
 * 数据看板服务（广州日晖智能科技 ERP 数据看板：财务/销售/仓储三大板块）。
 *
 * <p>口径说明（全部取自已生效的真实单据，不编造数据）：
 * <ul>
 *     <li>营收/销售额 = 已生效销售订单（sale_order.status='已生效'）金额合计</li>
 *     <li>毛利 = 订单明细不含税金额(net_amount) - 销售成本；销售成本按「单据成本 → 成本核算单 → 库存移动平均成本 → 最近采购价」取第一个可用值，0/负数/高于售价 3 倍视为缺失，缺失明细不参与毛利</li>
 *     <li>费用 = 销售费用/采购费用/运营费用三张费用单（已生效）</li>
 *     <li>应收/逾期 = account_receivable 未结清余额及账龄</li>
 *     <li>现金流 = 收款单(已确认) - 付款单(已确认)</li>
 *     <li>库存 = inventory_current 现存量 × total_cost；周转 = inventory_ledger 出库金额</li>
 *     <li>产品线 = material_category 一级分类（动物系列/工业系列等）</li>
 * </ul>
 */
public interface DataBoardService {

    /** 财务看板：period = month | quarter | year（默认 month）
     *  scopeCtx：数据权限上下文（2026-09-02 配置驱动，null=不过滤） */
    Map<String, Object> getFinanceBoard(String period, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx);

    /** 销售看板（scopeCtx 同上） */
    Map<String, Object> getSaleBoard(String period, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx);

    /** 仓储/库存看板（scopeCtx 同上，另按可见仓库过滤库存口径） */
    Map<String, Object> getInventoryBoard(String period, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx);
}
