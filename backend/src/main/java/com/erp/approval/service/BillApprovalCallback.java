package com.erp.approval.service;

import java.util.Collection;
import java.util.List;

/**
 * 审批引擎 → 单据Service 回调桥接。
 *
 * <p>解决问题：审批流程结束（全部节点通过 / 驳回）后，审批结果无法回写单据状态。
 * 所有单据Service（{@link com.erp.common.bill.AbstractBillService} 子类）实现本接口，
 * 审批引擎在流程终结时按 billType 路由回调：
 * <ul>
 *     <li>全部节点通过 → {@link #onApprovalApproved}：单据置「已生效」并触发业务影响</li>
 *     <li>驳回 → {@link #onApprovalRejected}：单据退回「草稿」</li>
 * </ul>
 * </p>
 *
 * <p>接口定义在审批包内、实现在单据包，保证依赖方向单一（单据 → 审批），无循环依赖。</p>
 */
public interface BillApprovalCallback {

    /** 单据类型编码（与设置中心审批开关一致，如 SALE_ORDER）；null = 未接入审批体系 */
    String getBillType();

    /**
     * 本回调负责处理的全部单据身份（与设置中心审批开关编码一致）。
     *
     * <p>默认返回单个 {@link #getBillType()}，保证既有实现类（销售订单/采购订单/销售合同/盘点/调拨/其他入库…）
     * <b>零改动</b>继续保持原有行为。</p>
     *
     * <p>一张表/一套 Service 承载多个身份时（例：生产领料单/生产退料单/生产补料单共用 material_requisition 表，
     * 身份由单据自身 bill_type 字段区分），重写本方法返回全部身份，审批引擎即可按身份把审批结果路由回同一实现。</p>
     */
    default Collection<String> supportedBillTypes() {
        String t = getBillType();
        return (t == null || t.isBlank()) ? List.of() : List.of(t);
    }

    /** 审批流程全部节点通过：回写单据「已生效」 */
    void onApprovalApproved(Long billId, String approver, String comment);

    /** 审批驳回：单据退回「草稿」 */
    void onApprovalRejected(Long billId, String rejector, String comment);

    /**
     * 审批流程全部节点通过（带单据身份）。
     *
     * <p>一表/一套 Service 承载多个身份时（收款单/付款单、生产领料/退料/补料），
     * 单据主键可能同号，仅靠 billId 无法判断身份。审批引擎终结流程时把
     * {@code task.getBillType()} 一并传入，实现类据此明确分流。</p>
     *
     * <p>默认实现忽略身份、转发到旧方法，保证既有实现（销售订单/采购订单…）
     * <b>零改动</b>继续保持原有行为。</p>
     */
    default void onApprovalApproved(String billType, Long billId, String operator, String comment) {
        onApprovalApproved(billId, operator, comment);
    }

    /** 审批驳回（带单据身份），默认实现忽略身份、转发到旧方法。 */
    default void onApprovalRejected(String billType, Long billId, String operator, String comment) {
        onApprovalRejected(billId, operator, comment);
    }
}
