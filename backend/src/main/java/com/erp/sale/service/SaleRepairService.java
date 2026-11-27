package com.erp.sale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.sale.entity.SaleRepair;
import com.erp.sale.entity.SaleRepairItem;
import com.erp.sale.entity.SaleRepairPart;

import java.util.List;
import java.util.Map;

/**
 * 售后维修：收件—维修—配件领用—返还—收费。
 * 库存动作全部通过生成「其他入库单/其他出库单」落地。
 */
public interface SaleRepairService extends IService<SaleRepair> {

    PageResult<SaleRepair> pageQuery(int current, int size, String keyword, String status,
                                     String startDate, String endDate, String customerId,
                                     com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                     String sortBy, String sortOrder, String customSearch);

    SaleRepair getDetail(Long id);

    SaleRepair saveRepairWithItems(SaleRepair bill, List<SaleRepairItem> items, List<SaleRepairPart> parts);

    String generateRepairNo();

    void submitForApproval(Long id);

    void approve(Long id, String approver);

    void reject(Long id, String rejector, String reason);

    void reverseApprove(Long id, String operator);

    void voidBill(Long id, String operator, String reason);

    void deleteBill(Long id);

    /** 开始维修：已受理 → 维修中 */
    void startRepair(Long id, String operator);

    /** 维修完成：维修中 → 维修完成；保内费用强制 0 */
    void completeRepair(Long id, String operator, String result);

    /** 收件入库：生成其他入库单草稿（维修仓，批次=维修单号） */
    void receiveIn(Long id, String operator);

    /** 配件领用：生成其他出库单草稿（备件仓） */
    void issueParts(Long id, String operator);

    /** 返还客户：生成其他出库单草稿（维修仓） */
    void returnToCustomer(Long id, String operator);

    /** 转换新：由来源销售订单生成换货单；lines=[{repairItemId, substituteMaterialId}] */
    SaleRepair transferToExchange(Long id, List<Map<String, Object>> lines, String operator);

    /** 转退货：生成销售退货单草稿 */
    SaleRepair transferToReturn(Long id, String operator);

    /** 保修判定（保存时自动执行） */
    void applyWarranty(SaleRepair bill, List<SaleRepairItem> items);
}
