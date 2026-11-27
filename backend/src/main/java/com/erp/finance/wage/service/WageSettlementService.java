package com.erp.finance.wage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.wage.dto.WageSummaryDTO;
import com.erp.finance.wage.entity.WageSettlement;
import com.erp.finance.wage.entity.WageSettlementItem;

import java.util.List;

/**
 * 工序工资核算服务
 */
public interface WageSettlementService extends IService<WageSettlement> {

    /** 按员工聚合的汇总行（页面主表格数据源）：期间 + 可选部门/员工过滤 */
    List<WageSummaryDTO> summary(String period, Long deptId, Long employeeId);

    /** 已生成工资核算单分页 */
    PageResult<WageSettlement> pageQuery(int current, int size, String period, String status, String keyword);

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<WageSettlement> pageQuery(int current, int size, String period, String status, String keyword, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, period, status, keyword);
    }

    /** 单据详情（含明细） */
    WageSettlement getDetail(Long id);

    /** 单据明细 */
    List<WageSettlementItem> getItems(Long id);

    /** 按期间生成工资核算单（一张单 = 一个期间，明细行 = 员工），保存为草稿 */
    WageSettlement generate(String period, Long deptId, Long employeeId, String operator);

    /** 提交：草稿 → 已生效（免审批时提交即生效） */
    void submit(Long id, String operator);

    /** 作废：已生效 → 已作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废：已作废 → 已生效 */
    void unvoidBill(Long id, String operator);
}
