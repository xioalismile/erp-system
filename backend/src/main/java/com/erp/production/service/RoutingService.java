package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingOperation;

import java.util.List;

public interface RoutingService extends IService<Routing> {
    void saveRoutingWithOperations(Routing routing, List<RoutingOperation> operations);
    PageResult<Routing> pageQuery(int current, int size, String keyword, String status,
                                  String startDate, String endDate, String sortBy, String sortOrder);
    Routing getDetail(Long id);

    /**
     * 【兼容保留，已无实际作用】工艺路线 2026-09-23 起从"单据"降为"主档"：
     * 状态只有 启用 / 停用，不再走审核。此方法只抛业务提示，避免老前端/老书签调用时 404。
     */
    void approve(Long id, String approver);

    /** 启用：停用 → 启用 */
    void enable(Long id, String operator);

    /** 停用：启用 → 停用 */
    void disable(Long id, String operator);

    /** 删除工艺路线（连同工序明细一起删，避免孤儿行） */
    void deleteRouting(Long id);

    String generateRoutingCode();

    /**
     * 【2026-10-07】工艺路线里配置过的工序名（去重、按中文排序）。
     *
     * <p>供质检模板「适用工序」下拉取候选 —— 用户反馈"下拉没有自动带出工艺路线里面的工序"：
     * 原来候选只来自工序档案，工艺路线里实际用到、但还没登记档案的工序选不到。</p>
     */
    List<String> listAllOperationNames();

    /**
     * 【2026-11-26】某物料适用的工艺路线：直接绑定该物料、绑定了该物料所在分类/上级分类，
     * 以及未绑定任何物料和分类的通用路线。工单成品行选路线时按该行物料过滤。
     */
    List<Routing> listApplicableByMaterial(Long materialId);
}
