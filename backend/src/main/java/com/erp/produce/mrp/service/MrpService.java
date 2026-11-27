package com.erp.produce.mrp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.produce.mrp.entity.MrpPlan;

import java.util.List;

public interface MrpService extends IService<MrpPlan> {

    PageResult<MrpPlan> pageQuery(int current, int size, String keyword, String status, String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<MrpPlan> pageQuery(int current, int size, String keyword, String status,
                                          String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder);
    }

    MrpPlan getDetail(Long id);

    void createPlan(MrpPlan plan);

    /** 更新计划基本信息(仅草稿态可改, 不允许改状态) */
    void updatePlan(Long id, MrpPlan plan);

    /** 审核: 草稿(0)/已运算(1) → 已生效(2) */
    void approvePlan(Long id, String operator);

    /** 执行: 已生效(2) → 已执行(3) */
    void executePlan(Long id, String operator);

    /** 驳回: 待审核(1) → 草稿(0) */
    void rejectPlan(Long id, String rejector, String reason);

    /** 重置草稿: 已生效(2) → 草稿(0) */
    void reverseApprovePlan(Long id, String operator);

    /** 作废: → plan_status=7, 记录 void 字段和作废前状态 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废: plan_status=7 → 恢复作废前状态 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效类(2/3) → close_status=1 */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    void runMrp(Long id);

    void confirmPlan(Long id);

    void generatePurchaseOrder(Long id, List<Long> itemIds);

    void generateProduceOrder(Long id, List<Long> itemIds);

    /**
     * 根据MRP计划生成委外订单
     * suggest_type=SUBCONTRACT 的明细生成委外订单，并回写销售订单的已转委外数量
     */
    void generateSubcontractOrder(Long id, List<Long> itemIds);

    void deletePlan(Long id);

    String generatePlanNo();
}
