package com.erp.purchase.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;

import java.util.List;
import java.util.Map;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    void saveOrderWithItems(PurchaseOrder order, List<PurchaseOrderItem> items);

    PageResult<PurchaseOrder> pageQuery(int current, int size, String supplierName, String status, String orderNo);

    PurchaseOrder getDetail(Long id);

    /** 提交审核: 草稿 → 已确认 */
    void submitAudit(Long id, String auditor);

    /** 取消: 草稿/已确认 → 已取消 */
    void cancelOrder(Long id, String reason);

    /**
     * 从销售订单转采购订单
     * @param saleOrderId 销售订单ID
     * @param itemSelection 选中的明细(物料ID+数量+仓库), 如果为空则全部转
     * @param buyer 采购员
     * @return 生成的采购订单列表(可能按供应商拆分多个)
     */
    List<PurchaseOrder> convertFromSaleOrder(Long saleOrderId, List<Map<String, Object>> itemSelection, String buyer);

    /**
     * 从MRP采购建议生成采购订单
     * @param suggestions MRP采购建议列表
     * @param buyer 采购员
     * @return 生成的采购订单列表(按供应商分组)
     */
    List<PurchaseOrder> generateFromMrp(List<Map<String, Object>> suggestions, String buyer);

    /**
     * 采购入库 (增加库存)
     * @param inboundOrderId 采购订单ID
     * @param warehouse 入库仓库
     * @param operator 操作人
     */
    void inbound(Long inboundOrderId, String warehouse, String operator);

    String generateOrderNo();
}
