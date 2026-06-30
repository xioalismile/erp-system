package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.WorkOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface WorkOrderService extends IService<WorkOrder> {

    /** 创建工单(自动从BOM展开用料明细) */
    WorkOrder createWorkOrder(WorkOrder workOrder);

    /** 更新工单 */
    void updateWorkOrder(WorkOrder workOrder);

    PageResult<WorkOrder> pageQuery(int current, int size, String productCode, String status, String workOrderNo);

    WorkOrder getDetail(Long id);

    /** 开工: 待开工 → 已开工 (扣减原材料库存) */
    void startWork(Long id);

    /** 报工: 记录完工数量 */
    void reportCompletion(Long id, BigDecimal completedQty, BigDecimal scrapQty);

    /** 完工入库: 已开工 → 已完工 (成品入库) */
    void finishWork(Long id);

    /** 取消 */
    void cancelWork(Long id, String reason);

    /**
     * 从MRP生产建议生成工单
     * @param mrpSuggestions MRP生产建议列表
     * @param demandDate 需求日期
     */
    List<WorkOrder> generateFromMrp(List<Map<String, Object>> mrpSuggestions, LocalDate demandDate);

    /**
     * 从销售订单生成生产工单
     * @param saleOrderId 销售订单ID
     * @param itemIds 要生成工单的销售明细ID列表（为空则全部）
     */
    List<WorkOrder> generateFromSaleOrder(Long saleOrderId, List<Long> itemIds);

    String generateWorkOrderNo();

    /** 获取工单进度 */
    Map<String, Object> getProgress(Long id);

    /** 发料 */
    void issueMaterial(Long workOrderId, Long materialId, BigDecimal quantity);

    /** 关闭工单 */
    void closeWork(Long id);
}
