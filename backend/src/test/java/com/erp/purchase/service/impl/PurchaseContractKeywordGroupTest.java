package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseContract;
import com.erp.purchase.mapper.PurchaseContractItemMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：采购合同列表关键词搜索的「条件作用域」。
 *
 * <p>Bug（2026-09-15 用户报障，改造前既有）：{@code wrapper.like("contract_no", kw).or().like("supplier_short_name", kw)}
 * 之后追加的 status / 数据权限 / 自定义字段 EXISTS，在 SQL 里被 AND/OR 优先级吃掉 ——
 * MyBatis-Plus 不会给 OR 分组补括号，实际语义是
 * {@code contract_no LIKE ? OR (supplier_short_name LIKE ? AND status = ? AND 数据权限 AND EXISTS)}，
 * 于是「命中合同号」的行绕过全部后续条件（含数据权限 → 可越权看到他人合同）。</p>
 *
 * <p>修复口径：把关键词两支包成一组 {@code wrapper.and(w -> w.like(..).or().like(..))}，
 * 与其它单据列表页统一。本测试即为该口径的回归守门用例。</p>
 *
 * <p>注：数据权限部分用 mock 模拟 {@link DataScopeResolver.DataScopeContext#applyTo} 的贡献
 * （真实实现同样是 {@code qw.and(w -> ...)} 自带括号，见 DataScopeResolver L96-L118），
 * 被测对象是「这些条件与关键词 OR 组之间的 AND/OR 组合关系」。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("采购合同 - 关键词 OR 组括号（过滤条件作用域）")
class PurchaseContractKeywordGroupTest {

    @Mock private PurchaseContractItemMapper itemMapper;
    @Mock private SupplierService supplierService;
    @Mock private EmployeeService employeeService;
    @Mock private CurrencyService currencyService;
    @Mock private WarehouseService warehouseService;
    @Mock private MaterialService materialService;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @Mock private ApprovalGateHelper approvalGateHelper;
    @Mock private SysSerialNoConfigService sysSerialNoConfigService;
    @Mock private com.erp.common.bill.BillApprovalAdapter billApprovalAdapter;
    @Mock private com.erp.config.SysParamValidator sysParamValidator;

    private PurchaseContractServiceImpl service;
    /** 捕获 pageQuery 真正交给 Mapper 的 WHERE 段 */
    private final String[] capturedSql = new String[1];

    @BeforeEach
    void setUp() {
        service = spy(new PurchaseContractServiceImpl(itemMapper, supplierService, employeeService, currencyService,
                warehouseService, materialService, unitOfMeasureService, sysSerialNoConfigService, sysParamValidator));
        // 单测不落库：拦掉分页执行，只取出 wrapper 的 SQL 段（records 为空、total 0 即可）
        doAnswer(inv -> {
            capturedSql[0] = ((QueryWrapper<?>) inv.getArgument(1)).getSqlSegment();
            return new Page<PurchaseContract>(1, 10);
        }).when(service).page(any(Page.class), any(Wrapper.class));
    }

    /**
     * @return 本次 pageQuery 生成的 WHERE 段（含 orderBy，MP 会把它拼在 where 之后）
     */
    private String captureSqlSegment(String keyword, String status,
                                     DataScopeResolver.DataScopeContext ctx, String customSearch) {
        capturedSql[0] = null;
        // 新增的 4 个筛选维度（日期区间/供应商/员工）本用例不涉及，统一传 null 保持断言口径不变
        service.pageQuery(1, 10, keyword, status, null, null, null, null, ctx, null, null, customSearch);
        assertNotNull(capturedSql[0], "pageQuery 未走到分页执行，取不到 SQL 段");
        return capturedSql[0];
    }

    @Test
    @DisplayName("关键词 + 状态：OR 组带括号，status 落在括号之外（修复前 status 被 OR 吞掉）")
    void keywordGroup_isParenthesized_statusStaysOutside() {
        String sql = captureSqlSegment("HT-2026", "草稿", null, null);

        assertTrue(sql.matches("(?s).*\\(contract_no LIKE [^\\s)]+ OR supplier_short_name LIKE [^\\s)]+\\) AND status = .*"),
                "关键词两个字段必须同组并带括号、且后面以 AND 接 status，实际 SQL 段: " + sql);
        assertFalse(sql.matches("(?s).*OR supplier_short_name LIKE [^\\s)]+ AND status = .*"),
                "出现「OR 分支后直接 AND」= OR 组未闭合（修复前形态），实际 SQL 段: " + sql);
    }

    @Test
    @DisplayName("关键词 + 状态 + 数据权限 + 自定义字段：后三者都在 OR 组之外，AND 串联")
    void allOtherConditions_stayOutsideKeywordGroup() {
        DataScopeResolver.DataScopeContext ctx = mock(DataScopeResolver.DataScopeContext.class);
        doAnswer(inv -> {
            // 与真实 DataScopeResolver 的自我条件形态一致（自带括号）
            ((QueryWrapper<?>) inv.getArgument(0)).and(w -> w.eq("created_by", "lyc123").or().eq("buyer_id", 7L));
            return null;
        }).when(ctx).applyTo(any(QueryWrapper.class), any());

        String sql = captureSqlSegment("HT-2026", "已生效", ctx, "{\"cf_test\":\"abc\"}");

        assertTrue(sql.matches("(?s).*\\(contract_no LIKE [^\\s)]+ OR supplier_short_name LIKE [^\\s)]+\\)"
                        + " AND status = [^\\s]+ AND \\(created_by = [^\\s]+ OR buyer_id = [^\\s]+\\) AND EXISTS \\(.*"),
                "status / 数据权限 / EXISTS 必须依次以 AND 接在关键词 OR 组之后，实际 SQL 段: " + sql);
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM sys_custom_field_value"),
                "自定义字段条件必须是 EXISTS 子查询，实际 SQL 段: " + sql);
    }

    @Test
    @DisplayName("关键词为空：不生成 OR 组（与改造前一致）")
    void blankKeyword_noOrGroup() {
        String sql = captureSqlSegment("  ", "草稿", null, null);

        assertFalse(sql.contains(" OR "), "关键词为空不应出现 OR 分组，实际 SQL 段: " + sql);
        assertTrue(sql.contains("status = "), "状态条件仍应生效，实际 SQL 段: " + sql);
    }

    @Test
    @DisplayName("关键词匹配范围不变：仍然同时匹配 合同号 与 供应商简称")
    void keywordStillMatchesBothColumns() {
        String sql = captureSqlSegment("PCON", null, null, null);

        assertTrue(sql.contains("contract_no LIKE"), "应仍匹配合同号，实际 SQL 段: " + sql);
        assertTrue(sql.contains("supplier_short_name LIKE"), "应仍匹配供应商简称，实际 SQL 段: " + sql);
    }
}
