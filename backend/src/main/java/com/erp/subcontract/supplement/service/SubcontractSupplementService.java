package com.erp.subcontract.supplement.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.subcontract.supplement.entity.SubcontractSupplement;
import com.erp.subcontract.supplement.entity.SubcontractSupplementItem;

import java.util.List;

public interface SubcontractSupplementService extends IService<SubcontractSupplement> {

    void saveSupplementWithItems(SubcontractSupplement supplement, List<SubcontractSupplementItem> items);

    PageResult<SubcontractSupplement> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder);

    /**
     * 在既有条件之上再叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广 · 委外模块批次）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SubcontractSupplement> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                        java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上再叠加「自定义字段搜索」（2026-11-24 列表页按自定义字段筛选）。
     * customSearch = JSON {fieldKey: 值}，由 CustomFieldSearchHelper 转 EXISTS 子查询；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    default PageResult<SubcontractSupplement> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                        String customSearch, java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder);
    }

    SubcontractSupplement getDetail(Long id);

    void submitForApproval(Long id);

    void approve(Long id, String approver);

    void reverseApprove(Long id, String operator);

    String generateSupplementNo();

    /** 驳回: 待审核 → 草稿 */
    void reject(Long id, String rejector, String reason);

    /** 作废 */
    void voidBill(Long id, String operator, String reason);

    /** 反作废 */
    void unvoidBill(Long id, String operator);

    /** 删除（统一状态机：草稿直接删；已生效先对冲库存再删） */
    void deleteBill(Long id);
}
