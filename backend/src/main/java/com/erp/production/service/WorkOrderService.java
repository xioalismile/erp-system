package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.vo.WorkOrderProgressVO;
import com.erp.subcontract.order.entity.SubcontractOrder;

import java.util.List;

public interface WorkOrderService extends IService<WorkOrder> {

    /** 保存工单(含用料明细): 新增或编辑 */
    void saveOrderWithItems(WorkOrder order, List<WorkOrderMaterial> materials);

    /**
     * 基于BOM展开自动生成用料清单（按 order_id 先删后插，可重复调用）。
     * 生成/下推工单后需立即调用，否则草稿态工单的"原料明细"为空。
     */
    void generateMaterialsFromBom(WorkOrder order);

    /** 分页查询: keyword模糊搜order_no/material_code/material_name, status/priority过滤, 单据日期区间, 负责人(manager_id)；数据权限按角色 data_scope（统一数据权限） */
    PageResult<WorkOrder> pageQuery(int current, int size, String keyword, String status, String priority,
                                    String startDate, String endDate, String employeeId,
                                    String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<WorkOrder> pageQuery(int current, int size, String keyword, String status, String priority,
                                            String startDate, String endDate, String employeeId,
                                            String sortBy, String sortOrder, java.util.Map<String, String> headerFilters,
                                            String customSearch) {
        return pageQuery(current, size, keyword, status, priority, startDate, endDate, employeeId, sortBy, sortOrder);
    }

    /** 详情: 工单 + 用料明细 */
    WorkOrder getDetail(Long id);

    /**
     * 【只保存工单工序清单】（2026-09-27 第7期补充）：不改动单据其它字段。
     *
     * <p>存在的理由：已生效工单没有常规「保存」入口（常规保存要求草稿态），
     * 但用户明确要求"生效时一道工序都没配 → 允许补选"。所以补选场景必须有一个独立入口。</p>
     *
     * <p>规则：草稿 → 全量替换；已生效且表里无工序 → 补选追加；已生效且已有工序 → 抛业务异常（锁死）。</p>
     *
     * @param operations 工序清单（数组顺序即工序号顺序），只提交"选用"的行
     * @return 落库后的工单工序清单（按工序号升序）
     */
    List<WorkOrderOperation> saveOrderOperations(Long orderId, List<WorkOrderOperation> operations);

    /** 提交: 草稿 → 待审核；免审批时直接置「已生效」并基于工艺路线生成工序实例 */
    void submit(Long id, String operator);

    /** 审核: 待审核 → 已生效, 基于工艺路线生成工序实例（审批开关开启时由审批引擎回调触发） */
    void approve(Long id, String operator);

    /** 结案: 已生效且未结案 → close_status=1 + is_closed=1（status 保持「已生效」） */
    void close(Long id, String operator, String reason);

    /** 驳回: 待审核 → 草稿 */
    void rejectBill(Long id, String rejector, String reason);

    /** 重置草稿(撤销生效): 已生效且未结案 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废: 草稿/已生效类(未结案) → 已作废（记录作废前状态） */
    void voidBill(Long id, String operator, String reason);

    /** 反作废: 已作废 → 恢复作废前状态 */
    void unvoidBill(Long id, String operator);

    /** 反结案: 已结案 → 未结案, 清除 is_closed/close_status */
    void reopenOrder(Long id, String operator);

    /** 删除工单(仅草稿态可删且无下游已生效单据, 连同用料/成品/工序明细一起删除) */
    void deleteBill(Long id);

    /** 生成工单号: MO-yyyyMMdd-0001 */
    String generateOrderNo();

    /** 获取工单进度详情 */
    WorkOrderProgressVO getProgressDetail(Long orderId);

    // ===== 下推: 来源单 → 生产工单 =====

    /** 销售订单明细 → 生产工单 (一单一扣: 一个销售明细行生成一张工单) */
    WorkOrder convertFromSaleOrder(Long saleOrderId, Long saleOrderItemId);

    /** MRP计划明细 → 生产工单 (按建议生产数量生成工单) */
    WorkOrder convertFromMrp(Long mrpPlanItemId);

    // ===== 下推: 生产工单 → 下游单据 =====

    /** 生产工单 → 生产领料单 (基于工单用料明细下推) */
    MaterialRequisition pushToMaterialRequisition(Long orderId);

    /** 生产工单 → 生产入库单 (完工产品入库) */
    ProductionReceipt pushToProductionReceipt(Long orderId);

    /**
     * 生产工单 → 生产入库单 (完工产品入库)，可指定本次入库数量与来源工序行。
     * 第 11 期：入库类型工序「一键发起入库单」复用本方法（quantity 不为空时按传入数量入库，并记录 source_operation_id）。
     *
     * @param quantity          本次入库数量；为空/≤0 时回落旧口径（计划量 − 已入库）
     * @param sourceOperationId 来源工单工序行ID（work_order_operation.id）；为空表示工单级下推
     */
    ProductionReceipt pushToProductionReceipt(Long orderId, java.math.BigDecimal quantity, Long sourceOperationId);
}
