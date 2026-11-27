package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.credit.CreditGuardService;
import com.erp.common.scope.DataScopeResolver;
import com.erp.config.SysParamValidator;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.ReferenceCheckService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController("masterdataCustomerController")
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    private final CustomerService customerService;
    private final JdbcTemplate jdbcTemplate;
    private final SysParamValidator sysParamValidator;
    private final ReferenceCheckService referenceCheckService;
    private final DataScopeResolver dataScopeResolver;
    private final CreditGuardService creditGuardService;

    /**
     * 客户信用额度信息（客户档案只读展示）：
     * creditLimit 额度 / usedCredit 已用额度(应收未收实时聚合) / availableCredit 剩余额度(=额度-已用，可为负表示超额)
     * creditApprovalStatus 授信状态 / creditApprovedBy、creditApprovedAt 授信信息
     */
    @GetMapping("/{id}/credit-summary")
    public Result<Map<String, Object>> creditSummary(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) return Result.error("客户不存在");
        BigDecimal creditLimit = customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal used = creditGuardService.queryUsedCredit(id);
        Map<String, Object> m = new HashMap<>();
        m.put("customerId", customer.getCustomerId());
        m.put("customerCode", customer.getCustomerCode());
        m.put("customerName", customer.getCustomerName());
        m.put("creditLimit", creditLimit);
        m.put("usedCredit", used);
        m.put("availableCredit", creditLimit.subtract(used));
        m.put("creditApprovalStatus", customer.getCreditApprovalStatus());
        m.put("creditApprovedBy", customer.getCreditApprovedBy());
        m.put("creditApprovedAt", customer.getCreditApprovedAt());
        return Result.success(m);
    }

    @PostMapping
    public Result<Customer> create(@Valid @RequestBody CustomerDTO dto, jakarta.servlet.http.HttpServletRequest req) {
        // 客户编号统一由「设置中心 → 自定义 → 自动编号设置」的「客户档案」规则决定：
        //   · 该规则「自动生成」开启 → 前端编号框只读并留空提交，由 CustomerService.save 按规则取号；
        //   · 关闭 → 前端要求手填，留空时 CustomerService.save 抛"请手动输入编码"。
        // 历史上这里还有一条基于系统参数 sys.auto.customer.no 的本地取号（CUST+日期+序号），
        // 与专用页规则重复且会盖掉用户在前缀里的配置，已移除，避免两套口径打架。
        // 编码留空 = 交由后端按编号规则生成，不能拿空串查重（库中存在历史空码行会误判已存在）
        if (dto.getCustomerCode() != null && !dto.getCustomerCode().isBlank()
                && customerService.existsByCustomerCode(dto.getCustomerCode())) {
            return Result.error("客户编码已存在");
        }

        // === 设置中心：客户名称查重（sys.customer.duplicate.check）===
        if (dto.getCustomerName() != null && !dto.getCustomerName().isBlank()) {
            long dupCount = customerService.count(new QueryWrapper<Customer>()
                    .eq("customer_name", dto.getCustomerName()));
            sysParamValidator.validateCustomerNameDuplicate(dto.getCustomerName(), dupCount > 0);
        }

        Customer customer = new Customer();
        copyDtoToEntity(dto, customer);
        customer.setStatus("正常");
        applyCrmDefaults(customer);

        // === 私海归属隔离：新建客户默认归属当前登录用户，确保私海列表按 owner_user_id 过滤可见 ===
        Long currentUserId = (Long) req.getAttribute("currentUserId");
        if (currentUserId != null && customer.getOwnerUserId() == null) {
            customer.setOwnerUserId(currentUserId);
        }

        customerService.save(customer);
        log.info("新增客户成功: {}, ownerUserId={}", customer.getCustomerCode(), customer.getOwnerUserId());
        return Result.success(customer);
    }

    @PutMapping("/{id}")
    public Result<Customer> update(@PathVariable Long id, @Valid @RequestBody CustomerDTO dto) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }

        if (customerService.existsByCustomerCodeExcludeId(dto.getCustomerCode(), id)) {
            return Result.error("客户编码已存在");
        }

        // === 设置中心：客户名称查重（sys.customer.duplicate.check）===
        if (dto.getCustomerName() != null && !dto.getCustomerName().isBlank()) {
            long dupCount = customerService.count(new QueryWrapper<Customer>()
                    .eq("customer_name", dto.getCustomerName())
                    .ne("customer_id", id));
            sysParamValidator.validateCustomerNameDuplicate(dto.getCustomerName(), dupCount > 0);
        }

        copyDtoToEntity(dto, customer);
        customerService.updateById(customer);
        log.info("修改客户成功: {}", customer.getCustomerCode());
        return Result.success(customer);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }

        // 删除前校验：被业务数据引用的客户禁止删除（防止单据悬空引用）
        String refErr = referenceCheckService.checkCustomer(id);
        if (refErr != null) {
            return Result.error(409, refErr);
        }

        customerService.removeById(id);
        log.info("删除客户成功: {}", customer.getCustomerCode());
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Customer> getById(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        if (customer == null) {
            return Result.error("客户不存在");
        }
        return Result.success(customer);
    }

    @GetMapping
    public Result<PageResult<Customer>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {

        IPage<Customer> pageResult = customerService.findPage(page, size, keyword);
        PageResult<Customer> result = PageResult.of(
                pageResult.getRecords(),
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize()
        );
        return Result.success(result);
    }

    // ===========================================================================
    // CRM 模块接口 (第七部分 7.1 客户档案CRM扩展)
    // ===========================================================================

    /** CRM客户分页查询：支持按CRM状态/销售阶段/意向等级/黑名单/分配销售员/标签/池类型筛选
     *  数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见私海范围 + 员工白名单叠加
     */
    @GetMapping("/crm")
    public Result<PageResult<Customer>> crmList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String crmStatus,
            @RequestParam(required = false) String salesStage,
            @RequestParam(required = false) String intentionLevel,
            @RequestParam(required = false) Integer isBlacklist,
            @RequestParam(required = false) Long assignedSalesId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer poolType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            jakarta.servlet.http.HttpServletRequest req) {

        Long currentUserId = (Long) req.getAttribute("currentUserId");
        DataScopeResolver.DataScopeContext ctx = dataScopeResolver.resolve(req);

        // 可见私海 owner 集合：全部数据→null（不过滤）；部门数据→部门用户集合；仅本人→自己
        // 解析口径统一收敛到 DataScopeResolver.visibleOwnerUserIds（私海卡片 crmDashboard 调用同一方法，
        // 禁止各调用方自己拼——历史 BUG：卡片与列表两套口径，同一页面出现 2 与 1 打架）
        List<Long> scopeOwnerUserIds = dataScopeResolver.visibleOwnerUserIds(ctx, currentUserId);
        List<Long> whitelist = ctx.getCustomerWhitelist();

        IPage<Customer> pageResult = customerService.crmFindPage(
                page, size, keyword, crmStatus, salesStage, intentionLevel, isBlacklist,
                assignedSalesId, tag, poolType, currentUserId, scopeOwnerUserIds, whitelist, sortBy, sortOrder);
        PageResult<Customer> result = PageResult.of(
                pageResult.getRecords(),
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize()
        );
        return Result.success(result);
    }

    /** 更新CRM状态: 潜在客户/意向客户/正式客户/休眠客户/已流失 */
    @PutMapping("/{id}/crm-status")
    public Result<Void> updateCrmStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer customer = customerService.getById(id);
        if (customer == null) return Result.error("客户不存在");

        String crmStatus = body.get("crmStatus");
        if (crmStatus == null || crmStatus.isBlank()) return Result.error("CRM状态不能为空");

        customer.setCrmStatus(crmStatus);
        // CRM状态联动销售阶段: 已流失 → sales_stage=已流失, 正式客户 → sales_stage=成交
        if ("已流失".equals(crmStatus)) {
            customer.setSalesStage("已流失");
        } else if ("正式客户".equals(crmStatus) && (customer.getSalesStage() == null || customer.getSalesStage().isBlank())) {
            customer.setSalesStage("成交");
        }
        customerService.updateById(customer);
        log.info("更新CRM状态: {} → {}", customer.getCustomerCode(), crmStatus);
        return Result.success();
    }

    /** 更新销售阶段: 初步接触/需求确认/方案报价/商务谈判/成交/已流失 */
    @PutMapping("/{id}/sales-stage")
    public Result<Void> updateSalesStage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer customer = customerService.getById(id);
        if (customer == null) return Result.error("客户不存在");

        String salesStage = body.get("salesStage");
        if (salesStage == null || salesStage.isBlank()) return Result.error("销售阶段不能为空");

        customer.setSalesStage(salesStage);
        if ("成交".equals(salesStage)) {
            customer.setCrmStatus("正式客户");
        } else if ("已流失".equals(salesStage)) {
            customer.setCrmStatus("已流失");
        }
        customerService.updateById(customer);
        log.info("更新销售阶段: {} → {}", customer.getCustomerCode(), salesStage);
        return Result.success();
    }

    /** 更新意向等级: A/B/C/D */
    @PutMapping("/{id}/intention-level")
    public Result<Void> updateIntentionLevel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer customer = customerService.getById(id);
        if (customer == null) return Result.error("客户不存在");

        String level = body.get("intentionLevel");
        if (level == null || level.isBlank()) return Result.error("意向等级不能为空");

        customer.setIntentionLevel(level);
        customerService.updateById(customer);
        log.info("更新意向等级: {} → {}", customer.getCustomerCode(), level);
        return Result.success();
    }

    /** 切换黑名单状态: 0-否, 1-是 */
    @PutMapping("/{id}/blacklist")
    public Result<Void> toggleBlacklist(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Customer customer = customerService.getById(id);
        if (customer == null) return Result.error("客户不存在");

        Integer isBlacklist = body.get("isBlacklist");
        if (isBlacklist == null || (isBlacklist != 0 && isBlacklist != 1)) {
            return Result.error("黑名单状态值无效(0或1)");
        }
        customer.setIsBlacklist(isBlacklist);
        customerService.updateById(customer);
        log.info("更新黑名单状态: {} → {}", customer.getCustomerCode(), isBlacklist);
        return Result.success();
    }

    /** 客户跟进登记：更新最后联系日期/下次联系日期/跟进次数，并同步CRM状态 */
    @PutMapping("/{id}/follow-up")
    public Result<Void> registerFollowUp(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Customer customer = customerService.getById(id);
        if (customer == null) return Result.error("客户不存在");

        LocalDate lastDate = body.get("lastContactDate") != null
                ? LocalDate.parse(body.get("lastContactDate").toString()) : LocalDate.now();
        Object nextDateObj = body.get("nextContactDate");
        LocalDate nextDate = nextDateObj != null ? LocalDate.parse(nextDateObj.toString()) : null;

        customer.setLastContactDate(lastDate);
        if (nextDate != null) customer.setNextContactDate(nextDate);

        // 跟进次数 +1
        int currentCount = customer.getFollowUpCount() != null ? customer.getFollowUpCount() : 0;
        customer.setFollowUpCount(currentCount + 1);

        // 潜在客户首次跟进自动转为意向客户
        if ("潜在客户".equals(customer.getCrmStatus())) {
            customer.setCrmStatus("意向客户");
            if (customer.getSalesStage() == null || customer.getSalesStage().isBlank()) {
                customer.setSalesStage("初步接触");
            }
        }

        customerService.updateById(customer);
        log.info("客户跟进登记: {} 跟进次数: {}", customer.getCustomerCode(), customer.getFollowUpCount());
        return Result.success();
    }

    /**
     * CRM仪表盘：按CRM状态/销售阶段/意向等级统计<b>私海</b>客户分布。
     *
     * <p><b>口径铁律</b>：必须与私海列表（/customer/crm?poolType=0）逐字同口径 ——
     * 统一走 CustomerService#applyPrivatePoolScope（未删除 + 私海 pool_type=0 + 数据权限归属人 + 员工白名单）。
     * 历史 BUG：本方法原先写成 (created_by=当前用户名 OR owner_user_id=当前用户的员工ID)，
     * 且不过滤公海/已删除 → 与列表口径不同，同一页面出现"卡片 2 / 列表 1"。</p>
     */
    @GetMapping("/crm/dashboard")
    public Result<Map<String, Object>> crmDashboard(jakarta.servlet.http.HttpServletRequest req) {
        DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(req);
        Long currentUserId = (Long) req.getAttribute("currentUserId");
        List<Long> ownerIds = dataScopeResolver.visibleOwnerUserIds(scopeCtx, currentUserId);
        List<Long> whitelist = scopeCtx.getCustomerWhitelist();
        Map<String, Object> data = new HashMap<>();

        // CRM状态分布
        Map<String, Long> statusDist = new HashMap<>();
        for (String s : List.of("潜在客户", "意向客户", "正式客户", "休眠客户", "已流失")) {
            QueryWrapper<Customer> w = new QueryWrapper<Customer>().eq("crm_status", s);
            customerService.applyPrivatePoolScope(w, ownerIds, whitelist);
            statusDist.put(s, customerService.count(w));
        }
        data.put("crmStatusDist", statusDist);

        // 销售阶段分布
        Map<String, Long> stageDist = new HashMap<>();
        for (String s : List.of("初步接触", "需求确认", "方案报价", "商务谈判", "成交", "已流失")) {
            QueryWrapper<Customer> w = new QueryWrapper<Customer>().eq("sales_stage", s);
            customerService.applyPrivatePoolScope(w, ownerIds, whitelist);
            stageDist.put(s, customerService.count(w));
        }
        data.put("salesStageDist", stageDist);

        // 意向等级分布
        Map<String, Long> intentionDist = new HashMap<>();
        for (String s : List.of("A", "B", "C", "D")) {
            QueryWrapper<Customer> w = new QueryWrapper<Customer>().eq("intention_level", s);
            customerService.applyPrivatePoolScope(w, ownerIds, whitelist);
            intentionDist.put(s, customerService.count(w));
        }
        data.put("intentionLevelDist", intentionDist);

        // 黑名单数量（私海内）
        QueryWrapper<Customer> blackW = new QueryWrapper<Customer>().eq("is_blacklist", 1);
        customerService.applyPrivatePoolScope(blackW, ownerIds, whitelist);
        data.put("blacklistCount", customerService.count(blackW));

        // 私海客户总数
        QueryWrapper<Customer> totalW = new QueryWrapper<>();
        customerService.applyPrivatePoolScope(totalW, ownerIds, whitelist);
        data.put("totalCustomers", customerService.count(totalW));

        // 今日待跟进（私海内 next_contact_date = today）
        QueryWrapper<Customer> todayW = new QueryWrapper<Customer>().eq("next_contact_date", LocalDate.now());
        customerService.applyPrivatePoolScope(todayW, ownerIds, whitelist);
        data.put("todayFollowUps", customerService.count(todayW));

        return Result.success(data);
    }

    private void applyCrmDefaults(Customer customer) {
        if (customer.getCrmStatus() == null || customer.getCrmStatus().isBlank()) {
            customer.setCrmStatus("潜在客户");
        }
        if (customer.getImportantLevel() == null || customer.getImportantLevel().isBlank()) {
            customer.setImportantLevel("普通");
        }
        if (customer.getSatisfactionScore() == null) customer.setSatisfactionScore(0);
        if (customer.getComplaintCount() == null) customer.setComplaintCount(0);
        if (customer.getBusinessOpportunityCount() == null) customer.setBusinessOpportunityCount(0);
        if (customer.getOrderCount() == null) customer.setOrderCount(0);
        if (customer.getFollowUpCount() == null) customer.setFollowUpCount(0);
        if (customer.getIsBlacklist() == null) customer.setIsBlacklist(0);
        if (customer.getAnnualRevenue() == null) customer.setAnnualRevenue(java.math.BigDecimal.ZERO);
        if (customer.getPotentialValue() == null) customer.setPotentialValue(java.math.BigDecimal.ZERO);
        if (customer.getRefundAmount() == null) customer.setRefundAmount(java.math.BigDecimal.ZERO);
        if (customer.getTotalOrderAmount() == null) customer.setTotalOrderAmount(java.math.BigDecimal.ZERO);
    }

    private void copyDtoToEntity(CustomerDTO dto, Customer customer) {
        customer.setCustomerCode(dto.getCustomerCode());
        customer.setCustomerName(dto.getCustomerName());
        customer.setShortName(dto.getShortName());
        customer.setCustomerType(dto.getCustomerType());
        customer.setCustomerLevel(dto.getCustomerLevel());
        customer.setTaxNo(dto.getTaxNo());
        customer.setContactPerson(dto.getContactPerson());
        customer.setContactPhone(dto.getContactPhone());
        customer.setEmail(dto.getEmail());
        customer.setAddress(dto.getAddress());
        customer.setBankName(dto.getBankName());
        customer.setBankAccount(dto.getBankAccount());
        customer.setCreditLimit(dto.getCreditLimit());
        customer.setPaymentCategory(dto.getPaymentCategory());
        customer.setPaymentTermId(dto.getPaymentTermId());
        customer.setCurrencyId(dto.getCurrencyId());
        customer.setDefaultSalerId(dto.getDefaultSalerId());
        customer.setDefaultPriceLevelId(dto.getDefaultPriceLevelId());
        // 备注：此前 DTO 未登记该字段，CRM 私海/公海保存的备注被静默丢弃（列表备注列恒空）
        customer.setRemark(dto.getRemark());

        // CRM扩展字段
        customer.setSourceType(dto.getSourceType());
        customer.setSourceDetail(dto.getSourceDetail());
        customer.setIndustry(dto.getIndustry());
        customer.setCompanyScale(dto.getCompanyScale());
        customer.setAnnualRevenue(dto.getAnnualRevenue());
        customer.setWechat(dto.getWechat());
        customer.setWebsite(dto.getWebsite());
        customer.setCrmStatus(dto.getCrmStatus());
        customer.setLastContactDate(dto.getLastContactDate());
        customer.setNextContactDate(dto.getNextContactDate());
        customer.setImportantLevel(dto.getImportantLevel());
        customer.setPotentialValue(dto.getPotentialValue());
        customer.setSatisfactionScore(dto.getSatisfactionScore());
        customer.setTag(dto.getTag());
        customer.setSalesStage(dto.getSalesStage());
        customer.setIntentionLevel(dto.getIntentionLevel());
        customer.setAssignedSalesId(dto.getAssignedSalesId());
        if (dto.getIsBlacklist() != null) customer.setIsBlacklist(dto.getIsBlacklist());

        // 客户录音访谈新增扩展字段
        customer.setMainBusiness(dto.getMainBusiness());
        customer.setProjectType(dto.getProjectType());
        customer.setCustomerDemand(dto.getCustomerDemand());
        customer.setCustomerPreference(dto.getCustomerPreference());
        customer.setFamilyBackground(dto.getFamilyBackground());
        if (dto.getPoolType() != null) customer.setPoolType(dto.getPoolType());
        if (dto.getIsTop() != null) customer.setIsTop(dto.getIsTop());
    }

    @lombok.Data
    public static class CustomerDTO {
        // customerCode 不加 @NotBlank，以便支持设置中心"自动生成客户编号"开关
        private String customerCode;

        @NotBlank(message = "客户名称不能为空")
        private String customerName;

        private String shortName;

        private String customerType = "企业客户";

        private String customerLevel = "普通客户";

        private String taxNo;

        private String contactPerson;

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "联系电话格式不正确")
        private String contactPhone;

        private String email;

        private String address;

        private String bankName;

        private String bankAccount;

        private java.math.BigDecimal creditLimit;

        /** 付款管控分类：现金客户/月结客户/账期客户/招投标客户/特批客户 */
        private String paymentCategory;

        private Long paymentTermId;

        private Long currencyId;

        private Long defaultSalerId;

        private Long defaultPriceLevelId;

        /** 备注（CRM 私海/公海列表备注列绑定字段，必须登记否则前端传值被丢弃） */
        private String remark;

        // ===== CRM扩展字段 =====
        private String sourceType;
        private String sourceDetail;
        private String industry;
        private String companyScale;
        private java.math.BigDecimal annualRevenue;
        private String wechat;
        private String website;
        private String crmStatus = "潜在客户";
        private LocalDate lastContactDate;
        private LocalDate nextContactDate;
        private String importantLevel = "普通";
        private java.math.BigDecimal potentialValue;
        private Integer satisfactionScore;
        private String tag;
        private String salesStage;
        private String intentionLevel;
        private Long assignedSalesId;
        private Integer isBlacklist = 0;

        // 客户录音访谈新增字段
        private Integer poolType = 0;
        private Integer isTop = 0;
        private String mainBusiness;
        private String projectType;
        private String customerDemand;
        private String customerPreference;
        private String familyBackground;
    }
}
