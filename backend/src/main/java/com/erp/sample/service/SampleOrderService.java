package com.erp.sample.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.masterdata.entity.Bom;
import com.erp.sample.entity.SampleOrder;

import java.util.List;

/**
 * 打样单 Service
 */
public interface SampleOrderService extends IService<SampleOrder> {

    /** 保存打样单（主表+明细） */
    void saveSampleWithItems(SampleOrder sample, List<com.erp.sample.entity.SampleOrderItem> items);

    /** 分页查询 */
    PageResult<SampleOrder> pageQuery(int current, int size, String keyword, String status, String customSearch, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SampleOrder> pageQuery(int current, int size, String keyword, String status, String customSearch, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, customSearch, scopeCtx, sortBy, sortOrder);
    }

    /** 获取详情（含明细） */
    SampleOrder getDetail(Long id);

    /** 提交审核: 草稿 → 待审核 */
    void submit(Long id, String operator);

    /** 审核: 待审核 → 已生效 */
    void approve(Long id, String approver);

    /** 重置草稿: 已生效 → 草稿 */
    void reverseApprove(Long id, String operator);

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 结案: 已生效类 → close_status=1 (status 保持不变, 前端按 closeStatus 渲染已结案) */
    void closeOrder(Long id, String operator);

    /** 反结案: close_status=0 */
    void reopenOrder(Long id, String operator);

    /** 开始打样: 已生效 → 打样中 */
    void startSample(Long id, String operator);

    /** 完成打样: 打样中 → 已完成 */
    void completeSample(Long id, String operator);

    /** 确认样品: 已完成 → 已确认 */
    void confirmSample(Long id, String operator);

    /** 取消: → 已取消 */
    void cancel(Long id, String operator, String reason);

    /**
     * 打样转BOM：已确认 → 已转BOM
     * 核心业务：以打样单表头物料/样品名称为父项，表体子件清单为BOM子项生成正式BOM
     * - 若父项物料不存在则自动创建物料档案
     * - 回写打样单 is_bom_generated=1, generated_bom_id
     * - 已转BOM的打样单不可重复转
     *
     * @return 生成的BOM对象
     */
    Bom convertToBom(Long id, String operator);

    /** 生成打样单号 */
    String generateSampleNo();

    /**
     * 更新打样进度
     * 业务规则：
     * 1. 已生成BOM（is_bom_generated=1）时进度锁定，提示先删除BOM
     * 2. 状态为已生效（已生效）且进度改为"已完成"时，自动推送生成一张草稿状态的BOM
     *
     * @return 提示语（进度已更新 / 已自动生成BOM草稿及编号）
     */
    String updateProgress(Long id, String progress, String operator);
}
