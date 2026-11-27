package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.masterdata.entity.Customer;

import java.time.LocalDate;
import java.util.List;

public interface CustomerService extends IService<Customer> {
    IPage<Customer> findPage(int page, int size, String keyword);

    /**
     * 按客户<b>名称 + 简称</b>模糊匹配，返回客户主键清单。
     *
     * <p>用途：销售类单据列表（报价 / 合同 / 出库 / 退货 / 发票）只有 {@code customer_id}、
     * 没有客户名称列，列表页关键词要按说明书 2.2 契约「单据编号 + 客户名称」搜索，
     * 必须先由名称解析出客户主键，再作为关键词 OR 分组里的一支（见 ListQuerySupport#applyKeywordGroupWithIds）。</p>
     *
     * <p><b>本方法不套用私海 / 公海等客户数据权限</b>：它服务于单据列表的关键词匹配，
     * 若按客户档案的可见范围裁剪集合，会出现"单据明明挂在该客户下、按客户名却搜不到"的诡异现象；
     * 单据的可见性由单据列表自身的数据权限负责，与客户档案可见性无关。</p>
     *
     * @param keyword 关键词；为空直接返回空集合（不查库）
     * @param limit   返回条数上限，由调用方传入（建议 ListQuerySupport#ID_LOOKUP_LIMIT）；≤0 时取默认 500
     */
    List<Long> findIdsByNameLike(String keyword, int limit);

    Customer findByCustomerCode(String customerCode);
    boolean existsByCustomerCode(String customerCode);
    boolean existsByCustomerCodeExcludeId(String customerCode, Long customerId);

    /**
     * CRM客户分页查询：支持按CRM状态/销售阶段/意向等级/黑名单/分配销售员/标签/池类型筛选
     * 数据权限（2026-09-02 配置驱动）：scopeOwnerUserIds 非空时仅查询这些用户负责的客户+公海客户；
     * whitelistCustomerIds 为员工权限补充白名单客户（额外可见）；两者为空表示不过滤（全部数据）
     */
    IPage<Customer> crmFindPage(int page, int size, String keyword,
                                String crmStatus, String salesStage,
                                String intentionLevel, Integer isBlacklist,
                                Long assignedSalesId, String tag, Integer poolType,
                                Long currentUserId, List<Long> scopeOwnerUserIds,
                                List<Long> whitelistCustomerIds,
                                String sortBy, String sortOrder);

    /**
     * 私海可见性条件（<b>私海列表与私海卡片必须共用本方法</b>，禁止各自拼条件——
     * 历史 BUG：同一页面卡片显示 2、列表只有 1，就是两套口径造成的）。
     *
     * <p>命中范围 = 未删除 + 私海(pool_type=0) + 数据权限归属人 + 员工权限补充白名单。</p>
     *
     * @param w                      查询条件
     * @param scopeOwnerUserIds      数据权限可见归属人（用户ID）集合；null/空 = 不过滤（全部数据）
     * @param whitelistCustomerIds   员工权限补充白名单客户ID集合；空 = 无白名单
     */
    void applyPrivatePoolScope(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Customer> w,
                               List<Long> scopeOwnerUserIds,
                               List<Long> whitelistCustomerIds);

    /** 客户公海池分页查询 */
    IPage<Customer> poolFindPage(int page, int size, String keyword);

    /** 领取公海客户到自己私海 */
    boolean claimFromPool(Long customerId, Long userId);

    /** 释放客户到公海 */
    boolean releaseToPool(Long customerId, String reason);

    /** 批量分配客户给销售员 */
    int batchAssign(List<Long> customerIds, Long salesId);

    /** 离职人员客户转移：将离职销售名下所有客户转给接替人或公海 */
    int transferByResignation(Long fromSalesId, Long toSalesId, boolean toPool);

    /** 切换置顶状态 */
    boolean toggleTop(Long customerId, Integer isTop);

    /** 触发公海池自动回收（定时任务调用） */
    int autoRecycleToPool();

    /** 触发客户跟进预警检查（定时任务调用），返回预警客户列表 */
    List<Customer> checkFollowUpWarning();

    /** 防造假检查：根据电话/税号/地址检测重复客户 */
    List<Customer> checkDuplicate(String phone, String taxNo, String address);

    /** 更新客户最后开单日期（销售订单审核通过时调用） */
    boolean updateLastOrderDate(Long customerId, LocalDate orderDate);
}
