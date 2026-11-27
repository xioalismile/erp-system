package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.util.SortHelper;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.entity.SalesTargetCompany;
import com.erp.crm.entity.SalesTargetEmployee;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.crm.mapper.SalesTargetCompanyMapper;
import com.erp.crm.mapper.SalesTargetEmployeeMapper;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.mapper.SaleOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售目标管理 Service 实现
 *
 * 核心业务规则：
 *  1. 公司年度目标设置后，下一年度目标自动按增长率(默认30%)递增
 *  2. 销售目标需在年度开始前（如今年7-8月）分配到每位销售
 *  3. 完成情况由成交商机(actual_close_date 在目标年度内)自动汇总
 *  4. 未分配目标 = 公司目标 - 已分配目标，提示需要招聘新销售
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesTargetServiceImpl extends ServiceImpl<SalesTargetCompanyMapper, SalesTargetCompany>
        implements SalesTargetService {

    private final SalesTargetEmployeeMapper employeeTargetMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final CustomerMapper customerMapper;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤，创建人列 create_by */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 默认增长率 30% */
    private static final BigDecimal DEFAULT_GROWTH_RATE = new BigDecimal("30.00");

    /** 公司目标可排序列白名单（allocatedAmount 等为虚拟字段，不可排序） */
    private static final Set<String> COMPANY_SORTABLE = Set.of(
            "annual_amount",
            "auto_generate_next_year",
            "create_by",
            "create_date",
            "description",
            "growth_rate",
            "id",
            "status",
            "target_year",
            "update_by",
            "update_date"
        );

    /** 销售员目标可排序列白名单（季度实际完成等为运行期填充字段，不可排序） */
    private static final Set<String> EMPLOYEE_SORTABLE = Set.of(
            "actual_amount",
            "allocation_status",
            "annual_target_amount",
            "completion_rate",
            "create_by",
            "create_date",
            "dept_id",
            "dept_name",
            "employee_id",
            "employee_name",
            "id",
            "new_customer_actual",
            "new_customer_target",
            "order_count_actual",
            "order_count_target",
            "q1_target",
            "q2_target",
            "q3_target",
            "q4_target",
            "remark",
            "target_year",
            "update_by",
            "update_date"
        );

    @Override
    public PageResult<SalesTargetCompany> pageQueryCompany(int current, int size, Integer year, String status,
                                                           String sortBy, String sortOrder) {
        QueryWrapper<SalesTargetCompany> wrapper = new QueryWrapper<>();
        if (year != null) {
            wrapper.eq("target_year", year);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 公司年度目标属公司公共信息，不按创建人过滤（创建人是硬编码 admin，过滤会导致销售员看不到目标）
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, COMPANY_SORTABLE)) {
            wrapper.orderByDesc("target_year");
        }
        Page<SalesTargetCompany> page = new Page<>(current, size);
        Page<SalesTargetCompany> result = page(page, wrapper);
        // 填充分配情况
        for (SalesTargetCompany t : result.getRecords()) {
            fillAllocationInfo(t);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PageResult<SalesTargetEmployee> pageQueryEmployee(int current, int size, Integer year, Long employeeId,
                                                              Long deptId, String allocationStatus, Integer quarter,
                                                              String sortBy, String sortOrder) {
        QueryWrapper<SalesTargetEmployee> wrapper = new QueryWrapper<>();
        if (year != null) {
            wrapper.eq("target_year", year);
        }
        if (employeeId != null) {
            wrapper.eq("employee_id", employeeId);
        }
        if (deptId != null) {
            wrapper.eq("dept_id", deptId);
        }
        if (allocationStatus != null && !allocationStatus.isBlank()) {
            wrapper.eq("allocation_status", allocationStatus);
        }
        // 数据权限（按角色 data_scope）：员工级目标按"被考核员工"归属过滤
        dataScopeResolver.applyIfLimited(wrapper, "employee_id", "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, EMPLOYEE_SORTABLE)) {
            wrapper.orderByDesc("completion_rate");
        }
        Page<SalesTargetEmployee> page = new Page<>(current, size);
        Page<SalesTargetEmployee> result = employeeTargetMapper.selectPage(page, wrapper);
        if (quarter != null && quarter >= 1 && quarter <= 4) {
            fillQuarterMetrics(result.getRecords(), year == null ? Year.now().getValue() : year, quarter);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /** 为分页记录填充季度口径指标（金额/订单数/新客户数/目标/完成率） */
    private void fillQuarterMetrics(List<SalesTargetEmployee> records, int year, int quarter) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(SalesTargetEmployee::getEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        LocalDate[] range = quarterRange(year, quarter);
        LocalDate qStart = range[0];
        LocalDate qEnd = range[1];
        // 季度内有效销售订单：已生效/已结案/已关闭且未作废
        QueryWrapper<SaleOrder> ow = new QueryWrapper<>();
        ow.select("saler_id AS sid", "COALESCE(SUM(total_amount),0) AS amt", "COUNT(*) AS cnt");
        ow.in("saler_id", ids);
        ow.between("order_date", qStart, qEnd);
        ow.isNull("voided_by");
        ow.and(w -> w.eq("approval_status", "已生效")
                .or().eq("status", "已生效")
                .or().eq("status", "已结案")
                .or().eq("close_status", 1));
        ow.groupBy("saler_id");
        Map<Long, BigDecimal> amtMap = new HashMap<>();
        Map<Long, Integer> ordCntMap = new HashMap<>();
        for (Map<String, Object> m : saleOrderMapper.selectMaps(ow)) {
            Long sid = ((Number) m.get("sid")).longValue();
            amtMap.put(sid, new BigDecimal(String.valueOf(m.get("amt"))));
            ordCntMap.put(sid, ((Number) m.get("cnt")).intValue());
        }
        // 季度内新客户：assigned_sales_id 为该员工且非作废
        QueryWrapper<com.erp.masterdata.entity.Customer> cw = new QueryWrapper<>();
        cw.select("assigned_sales_id AS sid", "COUNT(*) AS cnt");
        cw.in("assigned_sales_id", ids);
        cw.between("created_at", LocalDateTime.of(qStart, LocalTime.MIN), LocalDateTime.of(qEnd, LocalTime.MAX));
        cw.ne("status", "作废");
        cw.groupBy("assigned_sales_id");
        Map<Long, Integer> custCntMap = new HashMap<>();
        for (Map<String, Object> m : customerMapper.selectMaps(cw)) {
            custCntMap.put(((Number) m.get("sid")).longValue(), ((Number) m.get("cnt")).intValue());
        }
        for (SalesTargetEmployee ste : records) {
            Long eid = ste.getEmployeeId();
            BigDecimal amt = amtMap.getOrDefault(eid, BigDecimal.ZERO);
            ste.setQuarterActualAmount(amt);
            ste.setQuarterOrderCountActual(ordCntMap.getOrDefault(eid, 0));
            ste.setQuarterNewCustomerActual(custCntMap.getOrDefault(eid, 0));
            BigDecimal qTarget = quarterTargetOf(ste, quarter);
            ste.setQuarterTargetAmount(qTarget);
            ste.setQuarterCompletionRate(qTarget != null && qTarget.compareTo(BigDecimal.ZERO) > 0
                    ? amt.multiply(new BigDecimal("100")).divide(qTarget, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }
    }

    /** 取对应季度的目标金额 */
    private BigDecimal quarterTargetOf(SalesTargetEmployee ste, int quarter) {
        switch (quarter) {
            case 1: return ste.getQ1Target();
            case 2: return ste.getQ2Target();
            case 3: return ste.getQ3Target();
            case 4: return ste.getQ4Target();
        }
        return BigDecimal.ZERO;
    }

    /** 计算季度起止日期（含首尾） */
    private LocalDate[] quarterRange(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end = LocalDate.of(year, startMonth + 2, 1).plusMonths(1).minusDays(1);
        return new LocalDate[]{start, end};
    }

    @Override
    @Transactional
    public SalesTargetCompany createCompanyTarget(SalesTargetCompany target) {
        // 输入验证
        if (target.getTargetYear() == null) {
            throw new BusinessException("目标年度不能为空");
        }
        if (target.getAnnualAmount() == null || target.getAnnualAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("年度目标金额必须大于0");
        }
        // 检查年度唯一性
        SalesTargetCompany existing = getOne(new QueryWrapper<SalesTargetCompany>()
                .eq("target_year", target.getTargetYear()));
        if (existing != null) {
            throw new BusinessException("年度 " + target.getTargetYear() + " 的目标已存在");
        }
        // 默认值
        if (target.getGrowthRate() == null) {
            target.setGrowthRate(DEFAULT_GROWTH_RATE);
        }
        if (target.getAutoGenerateNextYear() == null) {
            target.setAutoGenerateNextYear(1);
        }
        if (target.getStatus() == null) {
            target.setStatus("启用");
        }
        target.setCreateDate(LocalDateTime.now());
        target.setUpdateDate(LocalDateTime.now());
        target.setCreateBy("admin");
        target.setUpdateBy("admin");
        save(target);
        log.info("公司年度销售目标创建: 年度={} 金额={} 增长率={}%",
                target.getTargetYear(), target.getAnnualAmount(), target.getGrowthRate());
        return target;
    }

    @Override
    @Transactional
    public void updateCompanyTarget(Long id, SalesTargetCompany target) {
        SalesTargetCompany existing = getById(id);
        if (existing == null) {
            throw new BusinessException("公司目标不存在");
        }
        target.setId(id);
        // 年度变更需检查唯一性
        if (target.getTargetYear() != null && !target.getTargetYear().equals(existing.getTargetYear())) {
            SalesTargetCompany dup = getOne(new QueryWrapper<SalesTargetCompany>()
                    .eq("target_year", target.getTargetYear()).ne("id", id));
            if (dup != null) {
                throw new BusinessException("年度 " + target.getTargetYear() + " 的目标已存在");
            }
        }
        target.setUpdateDate(LocalDateTime.now());
        target.setUpdateBy("admin");
        updateById(target);
        log.info("公司年度销售目标更新: id={}", id);
    }

    @Override
    @Transactional
    public void deleteCompanyTarget(Long id) {
        SalesTargetCompany existing = getById(id);
        if (existing == null) {
            throw new BusinessException("公司目标不存在");
        }
        // 删除公司目标时，对应销售员目标也一并删除
        employeeTargetMapper.delete(new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", existing.getTargetYear()));
        removeById(id);
        log.info("公司年度销售目标删除: id={} year={}", id, existing.getTargetYear());
    }

    @Override
    @Transactional
    public SalesTargetCompany generateNextYearTarget(Long currentYearId) {
        SalesTargetCompany current = getById(currentYearId);
        if (current == null) {
            throw new BusinessException("当前年度目标不存在");
        }
        int nextYear = current.getTargetYear() + 1;
        // 检查下年度是否已存在
        SalesTargetCompany existing = getOne(new QueryWrapper<SalesTargetCompany>()
                .eq("target_year", nextYear));
        if (existing != null) {
            throw new BusinessException("下年度(" + nextYear + ")目标已存在，不能重复生成");
        }
        // 按增长率计算下年度目标金额 = 当前金额 * (1 + 增长率/100)
        BigDecimal growthRate = current.getGrowthRate() != null ? current.getGrowthRate() : DEFAULT_GROWTH_RATE;
        BigDecimal multiplier = BigDecimal.ONE.add(growthRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal nextAmount = current.getAnnualAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        SalesTargetCompany next = new SalesTargetCompany();
        next.setTargetYear(nextYear);
        next.setAnnualAmount(nextAmount);
        next.setGrowthRate(growthRate); // 继承增长率
        next.setAutoGenerateNextYear(current.getAutoGenerateNextYear());
        next.setStatus("启用");
        next.setDescription("由 " + current.getTargetYear() + " 年度目标自动生成（增长 " + growthRate + "%）");
        next.setCreateDate(LocalDateTime.now());
        next.setUpdateDate(LocalDateTime.now());
        next.setCreateBy("admin");
        next.setUpdateBy("admin");
        save(next);
        log.info("自动生成下年度目标: {}年 → {}年, 金额 {} → {} (增长{}%)",
                current.getTargetYear(), nextYear, current.getAnnualAmount(), nextAmount, growthRate);
        return next;
    }

    @Override
    @Transactional
    public SalesTargetEmployee allocateEmployeeTarget(SalesTargetEmployee target) {
        // 输入验证
        if (target.getTargetYear() == null) {
            throw new BusinessException("目标年度不能为空");
        }
        if (target.getEmployeeId() == null) {
            throw new BusinessException("销售员ID不能为空");
        }
        if (target.getAnnualTargetAmount() == null || target.getAnnualTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("年度目标金额必须大于0");
        }
        // 检查同年度同销售员是否已分配
        SalesTargetEmployee existing = employeeTargetMapper.selectOne(new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", target.getTargetYear())
                .eq("employee_id", target.getEmployeeId()));
        if (existing != null) {
            throw new BusinessException("销售员 " + target.getEmployeeName() + " 在 " + target.getTargetYear() + " 年已有目标，请使用更新接口");
        }
        // 校验销售员年度目标合计不能超过公司年度目标
        SalesTargetCompany company = getOne(new QueryWrapper<SalesTargetCompany>()
                .eq("target_year", target.getTargetYear()));
        if (company != null) {
            BigDecimal allocated = employeeTargetMapper.sumAllocatedAmount(target.getTargetYear());
            BigDecimal afterAllocation = allocated.add(target.getAnnualTargetAmount());
            if (afterAllocation.compareTo(company.getAnnualAmount()) > 0) {
                throw new BusinessException("分配后合计 " + afterAllocation + " 超过公司年度目标 " + company.getAnnualAmount());
            }
        }
        // 默认值
        if (target.getQ1Target() == null) target.setQ1Target(BigDecimal.ZERO);
        if (target.getQ2Target() == null) target.setQ2Target(BigDecimal.ZERO);
        if (target.getQ3Target() == null) target.setQ3Target(BigDecimal.ZERO);
        if (target.getQ4Target() == null) target.setQ4Target(BigDecimal.ZERO);
        if (target.getNewCustomerTarget() == null) target.setNewCustomerTarget(0);
        if (target.getNewCustomerActual() == null) target.setNewCustomerActual(0);
        if (target.getOrderCountTarget() == null) target.setOrderCountTarget(0);
        if (target.getOrderCountActual() == null) target.setOrderCountActual(0);
        if (target.getActualAmount() == null) target.setActualAmount(BigDecimal.ZERO);
        if (target.getCompletionRate() == null) target.setCompletionRate(BigDecimal.ZERO);
        if (target.getAllocationStatus() == null) target.setAllocationStatus("已分配");
        target.setCreateDate(LocalDateTime.now());
        target.setUpdateDate(LocalDateTime.now());
        target.setCreateBy("admin");
        target.setUpdateBy("admin");
        employeeTargetMapper.insert(target);
        log.info("销售员目标分配: 年度={} 销售员={} 目标金额={}",
                target.getTargetYear(), target.getEmployeeName(), target.getAnnualTargetAmount());
        return target;
    }

    @Override
    @Transactional
    public int batchAllocate(List<SalesTargetEmployee> targets) {
        if (targets == null || targets.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SalesTargetEmployee t : targets) {
            allocateEmployeeTarget(t);
            count++;
        }
        log.info("批量分配销售员目标: 共 {} 条", count);
        return count;
    }

    @Override
    @Transactional
    public void updateEmployeeTarget(Long id, SalesTargetEmployee target) {
        SalesTargetEmployee existing = employeeTargetMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("销售员目标不存在");
        }
        target.setId(id);
        target.setUpdateDate(LocalDateTime.now());
        target.setUpdateBy("admin");
        employeeTargetMapper.updateById(target);
        // 更新后重新计算完成率
        recalcCompletionRate(id);
        log.info("销售员目标更新: id={}", id);
    }

    @Override
    @Transactional
    public void deleteEmployeeTarget(Long id) {
        SalesTargetEmployee existing = employeeTargetMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("销售员目标不存在");
        }
        employeeTargetMapper.deleteById(id);
        log.info("销售员目标删除: id={}", id);
    }

    @Override
    public BigDecimal refreshActualAmount(Integer year, Long employeeId) {
        Map<String, Object> m = refreshActualMetrics(year, employeeId, null);
        Object v = m.get("actualAmount");
        return v instanceof BigDecimal ? (BigDecimal) v : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public Map<String, Object> refreshActualMetrics(Integer year, Long employeeId, Integer quarter) {
        boolean byQuarter = quarter != null && quarter >= 1 && quarter <= 4;
        if (year == null || employeeId == null) {
            return Map.of("actualAmount", BigDecimal.ZERO, "orderCountActual", 0,
                    "newCustomerActual", 0, "quarter", byQuarter ? quarter : null);
        }
        LocalDate yStart;
        LocalDate yEnd;
        if (byQuarter) {
            LocalDate[] qr = quarterRange(year, quarter);
            yStart = qr[0];
            yEnd = qr[1];
        } else {
            yStart = LocalDate.of(year, 1, 1);
            yEnd = LocalDate.of(year, 12, 31);
        }
        // 有效销售订单：已生效/已结案/已关闭且未作废
        QueryWrapper<SaleOrder> soWrapper = new QueryWrapper<>();
        soWrapper.eq("saler_id", employeeId);
        soWrapper.between("order_date", yStart, yEnd);
        soWrapper.and(w -> w.isNull("voided_by"));
        soWrapper.and(w -> w.eq("approval_status", "已生效")
                .or().eq("status", "已生效")
                .or().eq("status", "已结案")
                .or().eq("close_status", 1));
        List<SaleOrder> validOrders = saleOrderMapper.selectList(soWrapper);
        BigDecimal actualAmount = BigDecimal.ZERO;
        for (SaleOrder so : validOrders) {
            if (so.getTotalAmount() == null) continue;
            actualAmount = actualAmount.add(so.getTotalAmount());
        }
        int orderCountActual = validOrders.size();
        // 新客户：年度/季度内 assigned_sales_id 为该员工且非作废
        QueryWrapper<com.erp.masterdata.entity.Customer> custWrapper = new QueryWrapper<>();
        custWrapper.eq("assigned_sales_id", employeeId);
        custWrapper.between("created_at", LocalDateTime.of(yStart, LocalTime.MIN), LocalDateTime.of(yEnd, LocalTime.MAX));
        custWrapper.ne("status", "作废");
        Long ncLong = customerMapper.selectCount(custWrapper);
        int nc = ncLong == null ? 0 : ncLong.intValue();
        // 年度口径写回数据库；季度口径仅用于计算和展示，不落库
        if (!byQuarter) {
            SalesTargetEmployee ste = employeeTargetMapper.selectOne(new QueryWrapper<SalesTargetEmployee>()
                    .eq("target_year", year)
                    .eq("employee_id", employeeId));
            if (ste != null) {
                ste.setActualAmount(actualAmount);
                ste.setOrderCountActual(orderCountActual);
                ste.setNewCustomerActual(nc);
                BigDecimal rate = ste.getAnnualTargetAmount() != null && ste.getAnnualTargetAmount().compareTo(BigDecimal.ZERO) > 0
                        ? actualAmount.multiply(new BigDecimal("100")).divide(ste.getAnnualTargetAmount(), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                ste.setCompletionRate(rate);
                ste.setUpdateDate(LocalDateTime.now());
                employeeTargetMapper.updateById(ste);
            }
        }
        log.info("刷新销售员实际完成指标: year={} quarter={} employeeId={} actualAmount={} orderCountActual={} newCustomerActual={} 写回快照={}",
                year, byQuarter ? "Q" + quarter : "全年", employeeId, actualAmount, orderCountActual, nc, !byQuarter);
        Map<String, Object> result = new HashMap<>();
        result.put("actualAmount", actualAmount);
        result.put("orderCountActual", orderCountActual);
        result.put("newCustomerActual", nc);
        result.put("quarter", byQuarter ? quarter : null);
        result.put("rangeStart", yStart.toString());
        result.put("rangeEnd", yEnd.toString());
        return result;
    }

    /** 重新计算完成率 */
    private void recalcCompletionRate(Long id) {
        SalesTargetEmployee ste = employeeTargetMapper.selectById(id);
        if (ste == null) return;
        BigDecimal actual = ste.getActualAmount() != null ? ste.getActualAmount() : BigDecimal.ZERO;
        BigDecimal targetAmount = ste.getAnnualTargetAmount();
        BigDecimal rate = targetAmount.compareTo(BigDecimal.ZERO) > 0
                ? actual.multiply(new BigDecimal("100")).divide(targetAmount, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        ste.setCompletionRate(rate);
        employeeTargetMapper.updateById(ste);
    }

    @Override
    public Map<String, Object> getAllocationDashboard(Integer year) {
        if (year == null) {
            year = Year.now().getValue();
        }
        Map<String, Object> dashboard = new HashMap<>();
        // 公司年度目标属公司公共信息，不按创建人过滤（创建人是硬编码 admin，过滤会导致销售员看不到目标）
        QueryWrapper<SalesTargetCompany> companyWrapper = new QueryWrapper<SalesTargetCompany>().eq("target_year", year);
        SalesTargetCompany company = getOne(companyWrapper);
        if (company == null) {
            dashboard.put("hasCompanyTarget", false);
            dashboard.put("message", "未设置 " + year + " 年度公司目标");
            return dashboard;
        }
        BigDecimal allocated = employeeTargetMapper.sumAllocatedAmount(year);
        Integer allocatedCount = employeeTargetMapper.countAllocatedEmployees(year);
        BigDecimal unallocated = company.getAnnualAmount().subtract(allocated);
        // 缺口人数 = 未分配金额 / 平均销售目标（按已分配平均；如无已分配则按公司目标/5 估算）
        BigDecimal avgPerPerson;
        if (allocatedCount != null && allocatedCount > 0) {
            avgPerPerson = allocated.divide(new BigDecimal(allocatedCount), 2, RoundingMode.HALF_UP);
        } else {
            avgPerPerson = company.getAnnualAmount().divide(new BigDecimal("5"), 2, RoundingMode.HALF_UP);
        }
        int shortageCount = avgPerPerson.compareTo(BigDecimal.ZERO) > 0
                ? unallocated.divide(avgPerPerson, 0, RoundingMode.UP).intValue()
                : 0;

        dashboard.put("hasCompanyTarget", true);
        dashboard.put("year", year);
        dashboard.put("companyTarget", company.getAnnualAmount());
        dashboard.put("growthRate", company.getGrowthRate());
        dashboard.put("allocatedAmount", allocated);
        dashboard.put("unallocatedAmount", unallocated);
        dashboard.put("allocatedCount", allocatedCount);
        dashboard.put("shortageCount", shortageCount);
        dashboard.put("allocationRate", company.getAnnualAmount().compareTo(BigDecimal.ZERO) > 0
                ? allocated.multiply(new BigDecimal("100")).divide(company.getAnnualAmount(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        dashboard.put("needRecruit", shortageCount > 0);
        dashboard.put("message", shortageCount > 0
                ? "还需招聘 " + shortageCount + " 名销售员才能完成 " + year + " 年度目标"
                : "目标分配充足");
        return dashboard;
    }

    @Override
    public List<Map<String, Object>> getCompletionRanking(Integer year) {
        if (year == null) {
            year = Year.now().getValue();
        }
        // 员工级目标按"被考核员工"归属过滤
        QueryWrapper<SalesTargetEmployee> rankingWrapper = new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", year)
                .orderByDesc("completion_rate");
        dataScopeResolver.applyIfLimited(rankingWrapper, "employee_id", "create_by");
        List<SalesTargetEmployee> list = employeeTargetMapper.selectList(rankingWrapper);
        // 先刷新所有销售员的实际完成金额
        for (SalesTargetEmployee ste : list) {
            refreshActualAmount(year, ste.getEmployeeId());
        }
        // 重新查询
        list = employeeTargetMapper.selectList(rankingWrapper);
        List<Map<String, Object>> ranking = new ArrayList<>();
        int rank = 1;
        for (SalesTargetEmployee ste : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank++);
            item.put("employeeId", ste.getEmployeeId());
            item.put("employeeName", ste.getEmployeeName());
            item.put("deptName", ste.getDeptName());
            item.put("annualTarget", ste.getAnnualTargetAmount());
            item.put("actualAmount", ste.getActualAmount());
            item.put("completionRate", ste.getCompletionRate());
            item.put("allocationStatus", ste.getAllocationStatus());
            ranking.add(item);
        }
        return ranking;
    }

    @Override
    public SalesTargetCompany getCompanyTargetDetail(Long id) {
        SalesTargetCompany target = getById(id);
        if (target == null) {
            throw new BusinessException("公司目标不存在");
        }
        fillAllocationInfo(target);
        return target;
    }

    /** 填充分配情况 */
    private void fillAllocationInfo(SalesTargetCompany t) {
        BigDecimal allocated = employeeTargetMapper.sumAllocatedAmount(t.getTargetYear());
        Integer count = employeeTargetMapper.countAllocatedEmployees(t.getTargetYear());
        t.setAllocatedAmount(allocated != null ? allocated : BigDecimal.ZERO);
        t.setUnallocatedAmount(t.getAnnualAmount().subtract(t.getAllocatedAmount()));
        t.setAllocatedCount(count != null ? count : 0);
    }
}
