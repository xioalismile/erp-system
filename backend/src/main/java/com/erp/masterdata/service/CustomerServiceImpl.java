package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.CustomerPoolConfig;
import com.erp.masterdata.entity.CustomerWarningConfig;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.masterdata.mapper.CustomerPoolConfigMapper;
import com.erp.masterdata.mapper.CustomerWarningConfigMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import com.erp.common.util.SortHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    /** CRM客户列表可排序列白名单（数据库列名） */
    private static final Set<String> CRM_SORTABLE_COLUMNS = Set.of(
            "address",
            "annual_revenue",
            "assigned_sales_id",
            "bank_account",
            "bank_name",
            "business_opportunity_count",
            "company_scale",
            "complaint_count",
            "contact_person",
            "contact_phone",
            "created_at",
            "created_by",
            "credit_limit",
            "crm_status",
            "currency_id",
            "customer_code",
            "customer_demand",
            "customer_id",
            "customer_level",
            "customer_name",
            "customer_preference",
            "customer_type",
            "default_price_level_id",
            "default_saler_id",
            "email",
            "ext_data",
            "family_background",
            "follow_up_count",
            "important_level",
            "industry",
            "intention_level",
            "is_blacklist",
            "is_top",
            "last_contact_date",
            "last_follow_up_date",
            "last_order_date",
            "legal_person",
            "main_business",
            "next_contact_date",
            "order_count",
            "owner_user_id",
            "payment_term_id",
            "pool_enter_date",
            "pool_type",
            "potential_value",
            "project_type",
            "refund_amount",
            "remark",
            "sales_stage",
            "satisfaction_score",
            "short_name",
            "source_detail",
            "source_type",
            "status",
            "tag",
            "tax_no",
            "tax_register_no",
            "top_time",
            "total_order_amount",
            "updated_at",
            "updated_by",
            "website",
            "wechat"
        );

    private final CustomerPoolConfigMapper poolConfigMapper;
    private final CustomerWarningConfigMapper warningConfigMapper;
    private final SysConfigService sysConfigService;
    private final SysSerialNoConfigService serialNoService;

    /**
     * 员工ID ↔ 用户ID 转换器。
     * 前端"分配销售员"下拉给的是<b>员工ID</b>，而客户归属列 owner_user_id 存的是<b>用户ID</b>，
     * 写库前必须转换（否则"分配给我的客户"不在我的私海里、卡片与列表数字对不上）。
     */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @Override
    public boolean save(Customer entity) {
        if (entity.getCustomerCode() == null || entity.getCustomerCode().isBlank()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("MD_CUSTOMER")) {
                throw new BusinessException("客户已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setCustomerCode(serialNoService.generateSerialNo("MD_CUSTOMER", this::nextCustomerCode,
                    SegmentCodeProbe.of(baseMapper, "customer_code")));
        }
        if (existsByCustomerCode(entity.getCustomerCode())) {
            throw new BusinessException("客户编码已存在: " + entity.getCustomerCode());
        }
        // 新建时微信非空 → 记录"首次由空变非空"时间（"新增意向微信"当日口径，2026-10-07）
        if (entity.getWechat() != null && !entity.getWechat().isBlank()) {
            entity.setWechatSetAt(LocalDateTime.now());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询客户档案表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextCustomerCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Customer last = getOne(new QueryWrapper<Customer>().likeRight("customer_code", prefix)
                .orderByDesc("customer_code").last("LIMIT 1"));
        if (last == null || last.getCustomerCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getCustomerCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 客户档案更新钩子（2026-09-07 新增授信联动）：
     * 付款管控分类（payment_category）发生变更时，客户授信状态自动失效——
     * 若此前已通过授信（月结免逐单），变更为现结后授权作废；改回月结需重新走首单授信审批。
     * 同时若存在进行中的授信审批任务（CUSTOMER_CREDIT），由引擎按"系统驳回"终结，
     * 其挂起的首单销售合同随回调退回草稿。审批历史保留在 approval_log/task，永久可查。
     */
    @Override
    public boolean updateById(Customer entity) {
        Customer old = (entity != null && entity.getCustomerId() != null) ? getById(entity.getCustomerId()) : null;
        boolean categoryChanged = old != null
                && entity.getPaymentCategory() != null
                && !Objects.equals(old.getPaymentCategory(), entity.getPaymentCategory());
        if (categoryChanged) {
            boolean hadCredit = old.getCreditApprovalStatus() != null
                    && !Customer.CREDIT_STATUS_NONE.equals(old.getCreditApprovalStatus());
            if (hadCredit) {
                terminateRunningCustomerCreditApproval(entity.getCustomerId());
                // 置为已失效：改回月结后仍需重新发起授信审批（审批通过才恢复免逐单）
                entity.setCreditApprovalStatus(Customer.CREDIT_STATUS_INVALID);
            }
        }
        // 微信字段由空变非空 → 记录"首次由空变非空"时间（"新增意向微信"当日口径，2026-10-07）
        if (old != null && entity.getWechat() != null && !entity.getWechat().isBlank()
                && (old.getWechat() == null || old.getWechat().isBlank())) {
            entity.setWechatSetAt(LocalDateTime.now());
        }
        return super.updateById(entity);
    }

    /**
     * 终结该客户进行中的授信审批任务（分类变更联动）。
     */
    private void terminateRunningCustomerCreditApproval(Long customerId) {
        try {
            if (approvalRuntimeService == null || customerId == null) return;
            com.erp.approval.entity.ApprovalTask running =
                    approvalRuntimeService.getRunningTask("CUSTOMER_CREDIT", customerId);
            if (running != null) {
                approvalRuntimeService.autoFinishReject(running.getTaskId(),
                        "system(分类变更失效)", "客户付款管控分类已变更，授信审批自动失效（可重新发起）");
                log.info("客户付款分类变更，终结进行中的授信审批: customerId={}, taskId={}", customerId, running.getTaskId());
            }
        } catch (Exception e) {
            // 授信任务终结失败不阻断客户档案保存，状态失效由上方赋值保证
            log.warn("客户付款分类变更时终结授信审批失败（不阻断保存）: customerId={}, err={}", customerId, e.getMessage());
        }
    }

    /** 审批运行时引擎（@Lazy：引擎构造需收集 BillApprovalCallback → CustomerCreditCallback → 本 Service，环依赖由字段懒注入解除） */
    @Autowired
    @Lazy
    private ApprovalRuntimeService approvalRuntimeService;

    /** 公海每日领取上限配置 key */
    private static final String CFG_DAILY_CLAIM_LIMIT = "crm.pool.daily.claim.limit";

    @Override
    public IPage<Customer> findPage(int page, int size, String keyword) {
        Page<Customer> pageRequest = new Page<>(page, size);
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        if (keyword != null && !keyword.isBlank()) {
            // 关键字必须成组 and(...)：平铺 or() 会吃掉前面的 deleted 条件与后面追加的数据权限条件（历史实锤坑）
            wrapper.and(w -> w.like("customer_code", keyword).or().like("customer_name", keyword));
        }
        // 2026-10-09 权限收口：与客户档案同口径（数据范围 + 客户白名单 + 仅正式客户）
        dataScopeResolver.applyVisibleCustomers(wrapper);
        SortHelper.orderByNewest(wrapper, Customer.class);
        return baseMapper.selectPage(pageRequest, wrapper);
    }

    /**
     * 按客户名称 / 简称解析客户主键（单据列表关键词用）。
     *
     * <p>直查 baseMapper、不套用私海 / 公海等数据权限：原因见
     * {@link CustomerService#findIdsByNameLike(String, int)}。只取 customer_id 一列，
     * 命中条数受 limit 限制（≤0 取默认 500），避免宽泛模糊词把客户表整表回捞。</p>
     */
    @Override
    public List<Long> findIdsByNameLike(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        int max = limit > 0 ? limit : 500;
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.select("customer_id")
                .eq("deleted", 0)
                .and(w -> w.like("customer_name", keyword).or().like("short_name", keyword))
                .last("LIMIT " + max);
        return list(wrapper).stream()
                .map(Customer::getCustomerId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Customer findByCustomerCode(String customerCode) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_code", customerCode).eq("deleted", 0);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public boolean existsByCustomerCode(String customerCode) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_code", customerCode).eq("deleted", 0);
        return baseMapper.exists(wrapper);
    }

    @Override
    public boolean existsByCustomerCodeExcludeId(String customerCode, Long customerId) {
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_code", customerCode).ne("customer_id", customerId).eq("deleted", 0);
        return baseMapper.exists(wrapper);
    }

    @Override
    public IPage<Customer> crmFindPage(int page, int size, String keyword,
                                        String crmStatus, String salesStage,
                                        String intentionLevel, Integer isBlacklist,
                                        Long assignedSalesId, String tag, Integer poolType,
                                        Long currentUserId, List<Long> scopeOwnerUserIds,
                                        List<Long> whitelistCustomerIds,
                                        String sortBy, String sortOrder) {
        Page<Customer> pageRequest = new Page<>(page, size);
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);

        // 数据权限（2026-09-02 配置驱动，替代 is_admin 硬编码）：
        // ① 私海(poolType=0)：走 applyPrivatePoolScope —— 与私海卡片 crmDashboard 共用同一处条件，
        //    保证同一页面的"卡片数字 = 列表条数"（历史 BUG：卡片 2 / 列表 1 就是两套口径造成的）；
        // ② 公海(poolType=1)/综合(null)：scopeOwnerUserIds 非空 → 只看这些用户负责的客户 + 公海客户；
        //    白名单客户（员工权限补充）额外可见；两者皆空 → 全部数据
        if (poolType != null && poolType == 0) {
            applyPrivatePoolScope(wrapper, scopeOwnerUserIds, whitelistCustomerIds);
        } else {
            boolean limitedScope = scopeOwnerUserIds != null && !scopeOwnerUserIds.isEmpty();
            boolean hasWhitelist = whitelistCustomerIds != null && !whitelistCustomerIds.isEmpty();
            if (limitedScope || hasWhitelist) {
                wrapper.and(w -> {
                    if (limitedScope) {
                        w.in("owner_user_id", scopeOwnerUserIds).or().eq("pool_type", 1);
                    }
                    if (hasWhitelist) {
                        if (limitedScope) {
                            w.or().in("customer_id", whitelistCustomerIds);
                        } else {
                            w.in("customer_id", whitelistCustomerIds);
                        }
                    }
                });
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("customer_code", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("short_name", keyword)
                    .or().like("contact_person", keyword)
                    .or().like("contact_phone", keyword));
        }
        if (crmStatus != null && !crmStatus.isBlank()) {
            wrapper.eq("crm_status", crmStatus);
        }
        if (salesStage != null && !salesStage.isBlank()) {
            wrapper.eq("sales_stage", salesStage);
        }
        if (intentionLevel != null && !intentionLevel.isBlank()) {
            wrapper.eq("intention_level", intentionLevel);
        }
        if (isBlacklist != null) {
            wrapper.eq("is_blacklist", isBlacklist);
        }
        if (assignedSalesId != null) {
            wrapper.eq("assigned_sales_id", assignedSalesId);
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.like("tag", tag);
        }
        // poolType=0 已由 applyPrivatePoolScope 写入条件，避免重复 eq
        if (poolType != null && poolType != 0) {
            wrapper.eq("pool_type", poolType);
        }

        // 用户点击列头排序时按白名单列排序，否则默认：置顶优先 + 待跟进客户优先
        boolean sorted = SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, CRM_SORTABLE_COLUMNS);
        if (!sorted) {
            wrapper.orderByDesc("is_top", "top_time");
            wrapper.orderByAsc("next_contact_date");
            SortHelper.orderByNewest(wrapper, Customer.class);
        }
        return baseMapper.selectPage(pageRequest, wrapper);
    }

    /**
     * 私海可见性条件（唯一实现）：未删除 + 私海(pool_type=0) + 数据权限归属人 + 员工白名单。
     * 私海列表(crmFindPage poolType=0) 与私海卡片(CustomerController#crmDashboard) 都调用本方法。
     */
    @Override
    public void applyPrivatePoolScope(QueryWrapper<Customer> w,
                                      List<Long> scopeOwnerUserIds,
                                      List<Long> whitelistCustomerIds) {
        w.eq("deleted", 0).eq("pool_type", 0);
        boolean limitedScope = scopeOwnerUserIds != null && !scopeOwnerUserIds.isEmpty();
        boolean hasWhitelist = whitelistCustomerIds != null && !whitelistCustomerIds.isEmpty();
        if (!limitedScope && !hasWhitelist) {
            // 全部数据（管理员等）：不过滤
            return;
        }
        w.and(x -> {
            if (limitedScope) {
                x.in("owner_user_id", scopeOwnerUserIds);
            }
            if (hasWhitelist) {
                if (limitedScope) {
                    x.or().in("customer_id", whitelistCustomerIds);
                } else {
                    x.in("customer_id", whitelistCustomerIds);
                }
            }
        });
    }

    @Override
    public IPage<Customer> poolFindPage(int page, int size, String keyword) {
        Page<Customer> pageRequest = new Page<>(page, size);
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).eq("pool_type", 1);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("customer_code", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("contact_person", keyword)
                    .or().like("contact_phone", keyword));
        }

        wrapper.orderByDesc("pool_enter_date");
        return baseMapper.selectPage(pageRequest, wrapper);
    }

    @Override
    public boolean claimFromPool(Long customerId, Long userId) {
        Customer customer = getById(customerId);
        if (customer == null) return false;
        if (customer.getPoolType() == null || customer.getPoolType() != 1) {
            return false;
        }

        // 每日领取上限校验：统计当前用户今日已领取的公海客户数
        int limit = sysConfigService.getIntValue(CFG_DAILY_CLAIM_LIMIT, 5);
        if (limit > 0) {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            QueryWrapper<Customer> cq = new QueryWrapper<>();
            cq.eq("owner_user_id", userId)
                    .eq("pool_type", 0)
                    .ge("updated_at", todayStart);
            long todayClaimed = count(cq);
            if (todayClaimed >= limit) {
                throw new BusinessException("今日已领取 " + todayClaimed + " 个公海客户，已达每日领取上限 " + limit + " 个");
            }
        }

        // owner_user_id = 用户ID（私海按用户ID匹配）；assigned_sales_id = 员工ID（该列口径是员工，
        // 「离职转移」按员工ID匹配，写用户ID会漏掉领取来的客户）
        Long employeeId = dataScopeResolver.employeeIdByUserId(userId);
        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("customer_id", customerId)
                .set("pool_type", 0)
                .set("pool_enter_date", null)
                .set("owner_user_id", userId)
                .set("updated_at", LocalDateTime.now());
        if (employeeId != null) {
            uw.set("assigned_sales_id", employeeId);
        }
        boolean ok = update(uw);
        if (ok) {
            log.info("公海客户领取成功: customerId={}, userId={}, 今日已领取将+1", customerId, userId);
        }
        return ok;
    }

    @Override
    public boolean releaseToPool(Long customerId, String reason) {
        Customer customer = getById(customerId);
        if (customer == null) return false;

        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("customer_id", customerId)
                .set("pool_type", 1)
                .set("pool_enter_date", LocalDateTime.now())
                .set("owner_user_id", null);
        return update(uw);
    }

    @Override
    public int batchAssign(List<Long> customerIds, Long salesId) {
        if (customerIds == null || customerIds.isEmpty() || salesId == null) return 0;
        // salesId = 员工ID（前端「分配销售员」下拉取的是员工列表）：
        //   assigned_sales_id 存员工ID（该列口径就是员工）；owner_user_id 存用户ID（私海可见性按用户ID匹配）
        // 历史 BUG：直接把员工ID写进 owner_user_id → 被分配人的私海里看不到该客户，卡片数字与列表打架。
        Long ownerUserId = dataScopeResolver.userIdByEmployeeId(salesId);
        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.in("customer_id", customerIds)
                .set("assigned_sales_id", salesId)
                .set("pool_type", 0)
                .set("pool_enter_date", null);
        if (ownerUserId != null) {
            uw.set("owner_user_id", ownerUserId);
        } else {
            // 该员工没有绑定登录账号：不得拿员工ID冒充用户ID（否则会被"同号的其他用户"认领）
            log.warn("批量分配：员工ID {} 未绑定用户账号，owner_user_id 保持原值", salesId);
        }
        boolean ok = update(uw);
        return ok ? customerIds.size() : 0;
    }

    @Override
    public int transferByResignation(Long fromSalesId, Long toSalesId, boolean toPool) {
        if (fromSalesId == null) return 0;
        QueryWrapper<Customer> qw = new QueryWrapper<>();
        qw.eq("assigned_sales_id", fromSalesId).eq("deleted", 0);
        List<Customer> customers = list(qw);
        if (customers.isEmpty()) return 0;

        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("assigned_sales_id", fromSalesId).eq("deleted", 0);
        if (toPool) {
            uw.set("pool_type", 1)
                    .set("pool_enter_date", LocalDateTime.now())
                    .set("assigned_sales_id", null)
                    .set("owner_user_id", null);
        } else {
            // toSalesId = 员工ID；owner_user_id 必须转成用户ID（与批量分配同一口径）
            Long ownerUserId = dataScopeResolver.userIdByEmployeeId(toSalesId);
            uw.set("assigned_sales_id", toSalesId);
            if (ownerUserId != null) {
                uw.set("owner_user_id", ownerUserId);
            } else {
                log.warn("离职转移：员工ID {} 未绑定用户账号，owner_user_id 保持原值", toSalesId);
            }
        }
        update(uw);
        return customers.size();
    }

    @Override
    public boolean toggleTop(Long customerId, Integer isTop) {
        if (isTop == null || (isTop != 0 && isTop != 1)) return false;
        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("customer_id", customerId)
                .set("is_top", isTop)
                .set("top_time", isTop == 1 ? LocalDateTime.now() : null);
        return update(uw);
    }

    @Override
    public int autoRecycleToPool() {
        // 读取设置中心开关：是否启用公海自动回收
        boolean recycleEnabled = sysConfigService.getBooleanValue("crm.pool.recycle.enabled", true);
        if (!recycleEnabled) {
            log.info("【设置中心】公海自动回收已关闭，跳过执行");
            return 0;
        }
        // 读取公海池配置
        QueryWrapper<CustomerPoolConfig> cq = new QueryWrapper<>();
        cq.eq("is_enabled", 1).orderByDesc("config_id").last("LIMIT 1");
        CustomerPoolConfig config = poolConfigMapper.selectOne(cq);
        if (config == null) {
            log.warn("公海池未启用或未配置，跳过自动回收");
            return 0;
        }

        LocalDate today = LocalDate.now();
        int noFollowDays = config.getNoFollowDays() != null ? config.getNoFollowDays() : 90;
        int noOrderDays = config.getNoOrderDays() != null ? config.getNoOrderDays() : 180;

        LocalDate followDeadline = today.minusDays(noFollowDays);
        LocalDate orderDeadline = today.minusDays(noOrderDays);

        // 私海客户：满足以下任一条件则回收
        // 1) 最后跟进日期早于跟进阈值
        // 2) 从未跟进且创建日期早于跟进阈值
        // 3) 最后开单日期早于开单阈值
        // 4) 从未开单且创建日期早于开单阈值
        QueryWrapper<Customer> qw = new QueryWrapper<>();
        qw.eq("deleted", 0).eq("pool_type", 0);
        qw.and(w -> w
                .and(w1 -> w1.isNotNull("last_follow_up_date").lt("last_follow_up_date", followDeadline))
                .or(w2 -> w2.isNull("last_follow_up_date").lt("created_at", followDeadline))
                .or(w3 -> w3.isNotNull("last_order_date").lt("last_order_date", orderDeadline))
                .or(w4 -> w4.isNull("last_order_date").lt("created_at", orderDeadline))
        );
        List<Customer> toRecycle = list(qw);

        if (toRecycle.isEmpty()) {
            log.info("公海池自动回收: 无可回收客户");
            return 0;
        }

        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("deleted", 0).eq("pool_type", 0);
        uw.and(w -> w
                .and(w1 -> w1.isNotNull("last_follow_up_date").lt("last_follow_up_date", followDeadline))
                .or(w2 -> w2.isNull("last_follow_up_date").lt("created_at", followDeadline))
                .or(w3 -> w3.isNotNull("last_order_date").lt("last_order_date", orderDeadline))
                .or(w4 -> w4.isNull("last_order_date").lt("created_at", orderDeadline))
        );
        uw.set("pool_type", 1)
                .set("pool_enter_date", LocalDateTime.now())
                .set("owner_user_id", null);

        update(uw);
        log.info("公海池自动回收: {} 个客户已转入公海", toRecycle.size());
        return toRecycle.size();
    }

    @Override
    public List<Customer> checkFollowUpWarning() {
        // 读取设置中心开关：是否启用跟进预警
        boolean followWarnEnabled = sysConfigService.getBooleanValue("crm.warning.follow.enabled", true);
        if (!followWarnEnabled) {
            log.info("【设置中心】跟进预警已关闭，跳过执行");
            return List.of();
        }
        QueryWrapper<CustomerWarningConfig> cq = new QueryWrapper<>();
        cq.eq("is_enabled", 1).eq("warning_type", "no_follow_up");
        // 用 selectList 取第一条，避免多条记录导致 selectOne 报错
        List<CustomerWarningConfig> configs = warningConfigMapper.selectList(cq);
        if (configs.isEmpty()) return List.of();
        CustomerWarningConfig config = configs.get(0);

        int threshold = config.getThresholdDays() != null ? config.getThresholdDays() : 90;
        LocalDate deadline = LocalDate.now().minusDays(threshold);

        QueryWrapper<Customer> qw = new QueryWrapper<>();
        qw.eq("deleted", 0).eq("pool_type", 0);
        qw.and(w -> w
                .and(w1 -> w1.isNotNull("last_follow_up_date").lt("last_follow_up_date", deadline))
                .or(w2 -> w2.isNull("last_follow_up_date").lt("created_at", deadline))
        );
        return list(qw);
    }

    @Override
    public List<Customer> checkDuplicate(String phone, String taxNo, String address) {
        QueryWrapper<Customer> qw = new QueryWrapper<>();
        qw.eq("deleted", 0);
        boolean hasCond = false;
        if (phone != null && !phone.isBlank()) {
            qw.eq("contact_phone", phone);
            hasCond = true;
        }
        if (taxNo != null && !taxNo.isBlank()) {
            if (hasCond) qw.or();
            qw.eq("tax_no", taxNo);
            hasCond = true;
        }
        if (address != null && !address.isBlank() && address.length() >= 6) {
            if (hasCond) qw.or();
            qw.like("address", address);
            hasCond = true;
        }
        if (!hasCond) return List.of();
        return list(qw);
    }

    @Override
    public boolean updateLastOrderDate(Long customerId, LocalDate orderDate) {
        if (customerId == null || orderDate == null) return false;
        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("customer_id", customerId)
                .and(w -> w.isNull("last_order_date").or().lt("last_order_date", orderDate))
                .set("last_order_date", orderDate);
        return baseMapper.update(null, uw) > 0;
    }

    /** 获取当前生效的公海池配置（取最新一条记录，不限制 is_enabled，确保关闭状态也能读取） */
    public CustomerPoolConfig getActivePoolConfig() {
        QueryWrapper<CustomerPoolConfig> cq = new QueryWrapper<>();
        cq.orderByDesc("config_id").last("LIMIT 1");
        return poolConfigMapper.selectOne(cq);
    }
}
