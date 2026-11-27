package com.erp.freesample.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.freesample.entity.FreeSampleOrder;

/**
 * 免费样品订单 Service
 * 业务场景：业务员录入免费样品订单（金额为0），仓库接单后直接发货，关联客户跟进记录
 * 状态流转: 草稿 → 待审核 → 已生效 → 已发货 → 已完成
 */
public interface FreeSampleOrderService extends IService<FreeSampleOrder> {

    /** 分页查询 */
    PageResult<FreeSampleOrder> pageList(int current, int size, String keyword, String status, String customSearch);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<FreeSampleOrder> pageList(int current, int size, String keyword, String status, String customSearch, java.util.Map<String, String> headerFilters) {
        return pageList(current, size, keyword, status, customSearch);
    }

    /** 详情（含明细） */
    FreeSampleOrder getById(Long id);

    /**
     * 保存（新增，含明细）
     * - 自动生成单号 FS-yyyyMMdd-0001
     * - 免费样品金额强制为 0.00
     * - 同步更新 crm_follow_up.related_sample_order_id
     */
    FreeSampleOrder saveWithItems(FreeSampleOrder order);

    /** 修改（含明细）：先删除旧明细再插入新明细 */
    FreeSampleOrder update(Long id, FreeSampleOrder order);

    /** 删除：仅草稿状态可删 */
    void delete(Long id);

    /** 提交审核: 草稿 → 待审核 */
    void submit(Long id, String operator);

    /** 审核: 待审核 → 已生效 */
    void approve(Long id, String approver);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效 → close_status=1（status 保持已生效） */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /**
     * 发货: 已生效 → 已发货
     * - 填快递单号
     * - 设置 delivery_date
     * - 根据明细自动调用库存出库（InventoryService.deductStock），如不可用则跳过库存扣减，仅记录状态
     */
    void ship(Long id, String expressNo, String expressCompany, String operator);

    /** 完成: 已发货 → 已完成 */
    void complete(Long id, String operator);

    /** 生成单号 FS-yyyyMMdd-0001（查询当天最大流水号+1） */
    String generateOrderNo();
}
