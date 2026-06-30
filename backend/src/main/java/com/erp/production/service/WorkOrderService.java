package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;

import java.util.List;

public interface WorkOrderService extends IService<WorkOrder> {

    /** 保存工单(含用料明细): 新增或编辑 */
    void saveOrderWithItems(WorkOrder order, List<WorkOrderMaterial> materials);

    /** 分页查询: keyword模糊搜order_no/material_code/material_name, status过滤 */
    PageResult<WorkOrder> pageQuery(int current, int size, String keyword, String status);

    /** 详情: 工单 + 用料明细 */
    WorkOrder getDetail(Long id);

    /** 下达: 草稿 → 已下达 */
    void release(Long id, String operator);

    /** 完工: 已下达 → 已完工 */
    void complete(Long id, String operator);

    /** 关闭: 已完工 → 已关闭 */
    void close(Long id, String operator, String reason);

    /** 生成工单号: WO + yyyyMMdd + 0001递增 */
    String generateOrderNo();
}
