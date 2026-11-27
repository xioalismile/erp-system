package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.system.entity.SysOperateLog;
import com.erp.system.entity.SysOperateLogDetail;
import com.erp.system.mapper.SysOperateLogDetailMapper;
import com.erp.system.mapper.SysOperateLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SysOperateLogServiceImpl extends ServiceImpl<SysOperateLogMapper, SysOperateLog> implements SysOperateLogService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "archive_date",
            "bill_no",
            "bill_type",
            "browser_info",
            "created_at",
            "dept_code",
            "dept_name",
            "duration",
            "fail_reason",
            "host_name",
            "ip_address",
            "log_id",
            "mac_address",
            "module_name",
            "operate_content",
            "operate_result",
            "operate_summary",
            "operate_time",
            "operate_type",
            "org_code",
            "real_name",
            "request_method",
            "request_params",
            "request_url",
            "session_id",
            "trace_id",
            "user_code",
            "user_name"
        );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SysOperateLogDetailMapper sysOperateLogDetailMapper;

    private final com.erp.common.bill.link.BillLinkRegistry billLinkRegistry;

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 真实姓名回填用（sys_user.real_name），历史日志的姓名是账号，需按账号回查 */
    private final com.erp.system.mapper.SysUserMapper sysUserMapper;

    @Override
    public PageResult<SysOperateLog> pageQuery(int current, int size, String keyword, String operateType, String moduleName, String startDate, String endDate, String sortBy, String sortOrder, String operateUser) {
        QueryWrapper<SysOperateLog> wrapper = new QueryWrapper<>();
        // 操作人筛选（账号 / 真实姓名 / 用户编码，模糊匹配）
        // 注意：历史日志的 real_name 存的是登录账号（真实姓名只在 sys_user 里），
        // 所以按"姓名"筛选时要先按姓名反查出账号，再一并纳入条件，否则输入"小李"会查不到。
        if (operateUser != null && !operateUser.isBlank()) {
            String kw = operateUser.trim();
            java.util.Set<String> codesByName = resolveUserCodesByName(kw);
            wrapper.and(w -> {
                w.like("user_code", kw)
                        .or().like("user_name", kw)
                        .or().like("real_name", kw);
                if (!codesByName.isEmpty()) {
                    w.or().in("user_code", codesByName);
                }
            });
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("user_name", keyword)
                    .or().like("real_name", keyword)
                    .or().like("user_code", keyword)
                    .or().like("module_name", keyword)
                    .or().like("operate_summary", keyword)
                    .or().like("bill_no", keyword));
        }
        if (operateType != null && !operateType.isBlank()) {
            wrapper.eq("operate_type", operateType);
        }
        // moduleName 支持: 单值精确匹配，逗号分隔多值用 IN 查询
        if (moduleName != null && !moduleName.isBlank()) {
            if (moduleName.contains(",")) {
                List<String> nameList = Arrays.stream(moduleName.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                if (!nameList.isEmpty()) {
                    wrapper.in("module_name", nameList);
                }
            } else {
                wrapper.eq("module_name", moduleName.trim());
            }
        }
        if (startDate != null && !startDate.isBlank()) {
            LocalDateTime start = LocalDate.parse(startDate, DATE_FMT).atStartOfDay();
            wrapper.ge("operate_time", start);
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDateTime end = LocalDate.parse(endDate, DATE_FMT).atTime(23, 59, 59);
            wrapper.le("operate_time", end);
        }
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SysOperateLog.class);
        Page<SysOperateLog> page = new Page<>(current, size);
        Page<SysOperateLog> result = page(page, wrapper);
        // 单据编号 → 目标单据主键（供前端把「单据编号」渲染成可点链接；反查不到则留空 = 纯文本）
        fillBillJumpRefs(result.getRecords());
        // 展示用真实姓名回填（老日志的"姓名"字段存的是登录账号，用户看不出是谁）
        fillRealNames(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 按「单据类型（中文名）+ 单据编号」反查目标单据主键，填充 billDocType / billId。
     * <p>操作日志表只存单据类型中文名与单号文本、不存主键，故只能回查；
     * 类型名经 BillLinkRegistry.docTypeByName 归一为注册表 docType（与前端 billRoute.js 同一套编码）。
     * 表名/列名取自注册表代码常量，单号仅作绑定参数，不拼接进 SQL。
     * 同一页里同类型同单号只查一次；单据已删或类型未登记时留空，不影响日志展示。</p>
     */
    private void fillBillJumpRefs(List<SysOperateLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        java.util.Map<String, Long> cache = new java.util.HashMap<>();
        for (SysOperateLog row : records) {
            String docType = billLinkRegistry.docTypeByName(row.getBillType());
            String no = row.getBillNo();
            if (docType == null || no == null || no.isBlank()) {
                continue;
            }
            com.erp.common.bill.link.BillLinkRegistry.DocDef def = billLinkRegistry.doc(docType);
            if (def == null || def.table == null || def.pkCol == null || def.noCol == null) {
                continue;
            }
            String cacheKey = docType + "|" + no;
            Long id = cache.get(cacheKey);
            if (id == null && !cache.containsKey(cacheKey)) {
                try {
                    id = jdbcTemplate.query(
                            "SELECT " + def.pkCol + " FROM " + def.table + " WHERE " + def.noCol + " = ? LIMIT 1",
                            rs -> rs.next() ? Long.valueOf(rs.getLong(1)) : null,
                            no);
                } catch (Exception e) {
                    log.warn("操作日志单据跳转反查失败 docType={} no={}: {}", docType, no, e.getMessage());
                    id = null;
                }
                cache.put(cacheKey, id);
            }
            if (id != null) {
                row.setBillDocType(docType);
                row.setBillId(id);
            }
        }
    }

    /**
     * 展示用「真实姓名」回填（2026-10-01 新增）。
     *
     * <p>背景：操作日志的 real_name 在写入时曾是登录账号（如 lyc123），
     * 用户看不出"是谁改的"。现在新日志写入已改为真实姓名，但历史日志不能重写库，
     * 因此在查询时按账号回查 sys_user.real_name 补上。</p>
     *
     * <p>口径：① real_name 为空、或 real_name 恰好等于账号（历史数据）→ 用真实姓名替换；
     * ② 含额外说明的记录（如"admin（AI助手代执行）"）原样保留，不覆盖；
     * ③ 查不到用户（账号已删）→ 保持原值，绝不留空白。
     * 一页只发一次 IN 查询，按账号去重；异常只记日志、不影响日志列表本身。</p>
     */
    private void fillRealNames(List<SysOperateLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        java.util.Set<String> needLookup = new java.util.HashSet<>();
        for (SysOperateLog row : records) {
            String code = accountOf(row);
            if (code == null) {
                continue;
            }
            if (accountSuffix(row.getRealName(), code) != null) {
                needLookup.add(code);
            }
        }
        java.util.Map<String, String> nameMap = new java.util.HashMap<>();
        if (!needLookup.isEmpty()) {
            try {
                List<com.erp.system.entity.SysUser> users = sysUserMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.erp.system.entity.SysUser>()
                                .select(com.erp.system.entity.SysUser::getUsername,
                                        com.erp.system.entity.SysUser::getRealName)
                                .in(com.erp.system.entity.SysUser::getUsername, needLookup));
                for (com.erp.system.entity.SysUser u : users) {
                    if (u.getUsername() != null && u.getRealName() != null && !u.getRealName().isBlank()) {
                        nameMap.put(u.getUsername(), u.getRealName());
                    }
                }
            } catch (Exception e) {
                log.warn("操作日志回填真实姓名失败: {}", e.getMessage());
            }
        }
        for (SysOperateLog row : records) {
            String code = accountOf(row);
            if (code == null) {
                continue;
            }
            String realName = row.getRealName();
            String suffix = accountSuffix(realName, code);
            if (suffix == null) {
                continue; // 已是独立姓名，保留
            }
            String name = nameMap.get(code);
            if (name != null && !name.isBlank()) {
                // 保留账号后面的说明（如"（AI助手代执行）"），只把账号部分换成姓名
                row.setRealName(name + suffix);
            } else if (realName == null || realName.isBlank()) {
                // 兜底：宁可显示账号，也不能空白
                row.setRealName(code);
            }
        }
    }

    /**
     * 按"姓名关键词"反查登录账号（用于操作人筛选）。
     *
     * <p>输入"小李"时，历史日志里存的账号是 lyc123，直接 LIKE real_name 查不到，
     * 因此先在 sys_user 里按真实姓名/账号模糊匹配取出账号，再把这些账号纳入筛选条件。</p>
     *
     * @return 命中的登录账号集合；查询失败返回空集合（不影响原有按账号筛选）
     */
    private java.util.Set<String> resolveUserCodesByName(String kw) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        try {
            List<com.erp.system.entity.SysUser> users = sysUserMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.erp.system.entity.SysUser>()
                            .select(com.erp.system.entity.SysUser::getUsername,
                                    com.erp.system.entity.SysUser::getRealName)
                            .and(w -> w.like(com.erp.system.entity.SysUser::getRealName, kw)
                                    .or().like(com.erp.system.entity.SysUser::getUsername, kw)));
            for (com.erp.system.entity.SysUser u : users) {
                if (u.getUsername() != null && !u.getUsername().isBlank()) {
                    codes.add(u.getUsername());
                }
            }
        } catch (Exception e) {
            log.warn("操作日志按姓名反查账号失败 kw={}: {}", kw, e.getMessage());
        }
        return codes;
    }

    /** 取该条日志用于回查姓名的账号：优先 user_code，其次 user_name */
    private String accountOf(SysOperateLog row) {
        String code = row.getUserCode();
        if (code == null || code.isBlank()) {
            code = row.getUserName();
        }
        return (code == null || code.isBlank()) ? null : code;
    }

    /**
     * 判断该条 real_name 是否"只是账号（可能带后缀说明）"、需要回查真实姓名。
     *
     * @return null = 已是独立姓名，无需回查；非 null = 需要回查，返回值是账号后面要保留的后缀
     *         （无后缀时为空串）
     */
    private String accountSuffix(String realName, String code) {
        if (realName == null || realName.isBlank() || realName.equals(code)) {
            return "";
        }
        // 形如 "lyc123（AI助手代执行）"：账号 + 括号说明 → 保留说明
        if (realName.startsWith(code) && realName.length() > code.length()
                && realName.charAt(code.length()) == '（') {
            return realName.substring(code.length());
        }
        return null;
    }

    @Override
    @Transactional
    public void saveLog(SysOperateLog operateLog) {
        if (operateLog.getOperateTime() == null) {
            operateLog.setOperateTime(LocalDateTime.now());
        }
        if (operateLog.getOperateResult() == null) {
            operateLog.setOperateResult("成功");
        }
        save(operateLog);
        log.info("记录操作日志: user={} module={} type={}",
                operateLog.getUserName(), operateLog.getModuleName(), operateLog.getOperateType());
    }

    @Override
    @Transactional
    public void saveBillLog(String billType, String billNo, String operateType,
                            String operateSummary, String userName, String realName,
                            String moduleName, List<FieldChange> changes) {
        saveBillLog(billType, billNo, operateType, operateSummary, userName, realName,
                moduleName, changes, userName, null, null, null, "成功", null);
    }

    @Override
    @Transactional
    public void saveBillLog(String billType, String billNo, String operateType,
                            String operateSummary, String userName, String realName,
                            String moduleName, List<FieldChange> changes,
                            String userCode, String ipAddress, String requestUrl, String requestMethod,
                            String operateResult, String failReason) {
        try {
            SysOperateLog opLog = new SysOperateLog();
            // user_code 在数据库中为 NOT NULL，未提供时用 userName 兜底
            opLog.setUserCode(userCode != null && !userCode.isBlank() ? userCode : userName);
            opLog.setUserName(userName);
            opLog.setRealName(realName);
            opLog.setOperateTime(LocalDateTime.now());
            opLog.setOperateType(operateType);
            // 【2026-09-15 修复】原先无论请求成功与否都写死「成功」→ 被业务拒绝的操作（如"必填项未填写"、
            // 409 编码已存在、404 记录不存在、抛异常）在日志里也显示成功。改为按真实结果记录 + 记失败原因。
            String realResult = (operateResult == null || operateResult.isBlank()) ? "成功" : operateResult;
            opLog.setOperateResult(realResult);
            opLog.setFailReason(truncate(failReason, 500));
            opLog.setBillType(billType);
            opLog.setBillNo(billNo);
            opLog.setOperateSummary(operateSummary);
            opLog.setModuleName(moduleName);
            opLog.setOperateContent(operateType + " " + billType + (billNo != null && !billNo.isBlank() ? " " + billNo : ""));
            opLog.setIpAddress(ipAddress);
            opLog.setRequestUrl(requestUrl);
            opLog.setRequestMethod(requestMethod);
            save(opLog);

            // 写入字段级变更明细（仅成功时写：明细语义是"实际发生的变更"，
            // 失败的操作未落库，若照写会出现"日志说有变更、实际没变"的误导）
            if ("成功".equals(realResult) && changes != null && !changes.isEmpty() && opLog.getLogId() != null) {
                for (FieldChange c : changes) {
                    SysOperateLogDetail d = new SysOperateLogDetail();
                    d.setLogId(opLog.getLogId());
                    d.setFieldDesc(c.fieldDesc);
                    d.setFieldName(c.fieldDesc); // field_name 用中文名，与前端展示一致
                    d.setOldValue(truncate(c.oldValue, 1000));
                    d.setNewValue(truncate(c.newValue, 1000));
                    d.setDataType(c.dataType != null ? c.dataType : "string");
                    sysOperateLogDetailMapper.insert(d);
                }
            }
        } catch (Exception e) {
            log.warn("保存单据操作日志失败 billType={} billNo={}", billType, billNo, e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    @Override
    public PageResult<SysOperateLog> getByBill(String billType, String billNo, int current, int size) {
        QueryWrapper<SysOperateLog> wrapper = new QueryWrapper<>();
        if (billType != null && !billType.isBlank()) {
            wrapper.eq("bill_type", billType);
        }
        if (billNo != null && !billNo.isBlank()) {
            wrapper.eq("bill_no", billNo);
        }
        // 【2026-09-15 修复】原先无排序 → 返回最旧在前；而单据页弹窗会自动展开第 1 条，
        // 用户看到的是最旧那条（早期记录没有字段级明细）→ 误判"改前改后没记录"。统一为最新在前。
        SortHelper.orderByNewest(wrapper, SysOperateLog.class);
        Page<SysOperateLog> page = new Page<>(current, size);
        Page<SysOperateLog> result = page(page, wrapper);
        // 单据页「操作日志」弹窗同样回填真实姓名（与列表页同一口径）
        fillRealNames(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }
}
