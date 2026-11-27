package com.erp.finance.cost.calculation.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.config.SysParamValidator;
import com.erp.config.service.SysConfigService;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper;
import com.erp.finance.mapper.OperationExpenseAllocationMapper;
import com.erp.finance.mapper.OperationExpenseMapper;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.mapper.InventoryTransactionMapper;
import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.WorkCenterMapper;
import com.erp.production.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 成本核算「生效快照不被重算覆盖」回归测试（纯单元测试，Mock 全部 Mapper，不连数据库）。
 *
 * <p>缺陷来源（2026-11-03 修复后暴露）：全站状态机的生效状态是「已生效」，而
 * {@code periodCalculate} 的"历史快照跳过"与"已有核算单更新"两处只认「已确认」。
 * 结果：已审核生效的核算单在用户再点一次「当期核算」时被静默重算覆盖
 * （unit_cost 被改写、状态回退成「已核算」、明细删除重建），且覆盖后状态不再属于生效态，
 * 物料「最新成本」随之回退/归零。</p>
 *
 * <p>修复口径：两处状态条件都同时覆盖「已确认」与「已生效」，与
 * {@code BillStatusUtils}（两者同属 APPROVED 家族）及 {@code getUnitCost} 保持一致。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("成本核算 - 生效快照保护（已确认/已生效）")
class CostCalculationStatusGuardTest {

    /** 用 Unicode 转义固定期望值，避免测试源码编码影响断言（已生效 / 已确认） */
    private static final String EFFECTIVE = "\u5DF2\u751F\u6548";
    private static final String CONFIRMED = "\u5DF2\u786E\u8BA4";

    @Mock private CostCalculationItemMapper itemMapper;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private MaterialMapper materialMapper;
    @Mock private MaterialRequisitionMapper materialRequisitionMapper;
    @Mock private MaterialRequisitionItemMapper materialRequisitionItemMapper;
    @Mock private OperationReportMapper operationReportMapper;
    @Mock private WorkCenterMapper workCenterMapper;
    @Mock private SysConfigService sysConfigService;
    @Mock private InventoryTransactionMapper inventoryTransactionMapper;
    @Mock private OperationExpenseMapper operationExpenseMapper;
    @Mock private OperationExpenseAllocationMapper operationExpenseAllocationMapper;
    @Mock private com.erp.production.mapper.ProductionReceiptMapper productionReceiptMapper;
    @Mock private com.erp.production.mapper.ProductionReceiptItemMapper productionReceiptItemMapper;
    @Mock private com.erp.purchase.mapper.PurchaseInboundItemMapper purchaseInboundItemMapper;
    @Mock private com.erp.sale.mapper.SaleReturnItemMapper saleReturnItemMapper;
    @Mock private BillCascadeDeleteService billCascadeDeleteService;
    @Mock private SysParamValidator sysParamValidator;

    private CostCalculationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new CostCalculationServiceImpl(itemMapper, workOrderMapper, materialMapper,
                materialRequisitionMapper, materialRequisitionItemMapper, operationReportMapper,
                workCenterMapper, inventoryTransactionMapper,
                operationExpenseMapper, operationExpenseAllocationMapper,
                productionReceiptMapper, productionReceiptItemMapper,
                purchaseInboundItemMapper, saleReturnItemMapper,
                billCascadeDeleteService, sysParamValidator));
        lenient().doReturn(true).when(service).updateById(any());
        lenient().doReturn(true).when(service).save(any());
        // ServiceImpl.getOne 依赖 baseMapper（单测未注入）→ 默认"查不到"，各用例按需覆写
        lenient().doReturn(null).when(service).getOne(any(Wrapper.class), anyBoolean());

        // 一期间内有一条已生效领料单，明细挂物料 100（产生 1 个待核算物料）
        MaterialRequisition req = new MaterialRequisition();
        req.setRequisitionId(10L);
        req.setRequisitionNo("LL-TEST-0001");
        MaterialRequisitionItem reqItem = new MaterialRequisitionItem();
        reqItem.setMaterialId(100L);
        reqItem.setMaterialCode("WL100");
        reqItem.setMaterialName("测试物料");
        reqItem.setQuantity(BigDecimal.ONE);
        reqItem.setUnitPrice(new BigDecimal("50"));
        reqItem.setAmount(new BigDecimal("50"));
        doReturn(List.of(req)).when(materialRequisitionMapper).selectList(any());
        doReturn(List.of(reqItem)).when(materialRequisitionItemMapper).selectList(any());
        doReturn(List.of()).when(operationReportMapper).selectList(any());
        doReturn(List.of()).when(operationExpenseMapper).selectList(any());
        lenient().doReturn(List.of()).when(inventoryTransactionMapper).selectList(any());

        Material material = new Material();
        material.setMaterialId(100L);
        material.setMaterialCode("WL100");
        material.setMaterialName("测试物料");
        doReturn(material).when(materialMapper).selectById(100L);
    }

    private CostCalculation existingCalc(String status) {
        CostCalculation calc = new CostCalculation();
        calc.setId(9L);
        calc.setStatus(status);
        calc.setMaterialId(100L);
        calc.setPeriod("2026-10");
        calc.setCalcDate(LocalDate.of(2026, 10, 31));
        calc.setUnitCost(new BigDecimal("66.5000"));
        return calc;
    }

    @Test
    @DisplayName("已生效核算单：当期核算必须整体跳过（不覆盖、不改状态、不重插明细），且快照查询同时覆盖两种生效状态")
    void periodCalculate_shouldSkipEffectiveSnapshot() {
        CostCalculation effective = existingCalc("已生效");
        java.util.List<Wrapper<?>> seen = new java.util.ArrayList<>();
        // 用 doAnswer 在"生产代码真正调用"的当下抓取查询条件（ArgumentCaptor 会混入打桩调用，不可靠）
        org.mockito.Mockito.doAnswer(inv -> {
            seen.add(inv.getArgument(0));
            return effective;
        }).when(service).getOne(any(Wrapper.class), anyBoolean());

        Map<String, Object> result = service.periodCalculate("2026-10");

        // 1) 生效快照一个字都不能改
        verify(service, never()).updateById(any());
        verify(service, never()).save(any());
        assertEquals(0, result.get("updatedCount"), "已生效快照不得计入更新数");
        assertEquals(0, result.get("createdCount"), "已生效快照不得新建第二张核算单");

        // 2) 快照查询条件必须同时包含「已确认」与「已生效」（缺陷本体：原先只认已确认）
        assertTrue(seen.size() == 1, "当期核算入口必须只查一次生效快照，实际查询次数: " + seen.size());
        Wrapper<?> snapshotQuery = seen.get(0);
        assertTrue(hasParam(snapshotQuery, EFFECTIVE),
                "查询条件必须包含「已生效」，实际: " + describe(snapshotQuery));
        assertTrue(hasParam(snapshotQuery, CONFIRMED),
                "查询条件必须保留「已确认」历史兼容，实际: " + describe(snapshotQuery));
        // 3) 跳过名单回传（页面据此提示哪些物料因已生效未重算）
        assertEquals(1, result.get("skippedConfirmedCount"));
    }

    /** 参数值判定：MyBatis-Plus 的 in()/eq() 走占位符，实际值在 paramNameValuePairs 里 */
    private static boolean hasParam(Wrapper<?> wrapper, String value) {
        if (wrapper instanceof QueryWrapper<?> qw) {
            // 注意：MP 的参数表要等 getCustomSqlSegment() 渲染分段后才可读，先取分段再读参数
            String sql = qw.getCustomSqlSegment();
            for (Object v : qw.getParamNameValuePairs().values()) {
                if (value.equals(String.valueOf(v))) {
                    return true;
                }
            }
            return sql.contains(value);
        }
        return false;
    }

    private static String describe(Wrapper<?> wrapper) {
        if (wrapper instanceof QueryWrapper<?> qw) {
            StringBuilder sb = new StringBuilder(qw.getCustomSqlSegment()).append(" 参数=");
            qw.getParamNameValuePairs().forEach((k, v) -> sb.append(k).append('[')
                    .append(v == null ? "null" : v.getClass().getSimpleName()).append("]=")
                    .append(codePoints(v)).append(' '));
            return sb.toString();
        }
        return String.valueOf(wrapper);
    }

    private static String codePoints(Object v) {
        if (v == null) {
            return "null";
        }
        String s = String.valueOf(v);
        StringBuilder sb = new StringBuilder(s).append('(');
        s.codePoints().forEach(cp -> sb.append(String.format("U+%04X ", cp)));
        return sb.append(')').toString();
    }

    @Test
    @DisplayName("未生效（已核算）核算单：仍按原口径重算覆盖，不能被保护逻辑误挡")
    void periodCalculate_shouldStillRecalculateNotYetEffectiveSnapshot() {
        CostCalculation calculated = existingCalc("已核算");
        // 第一次 getOne = 生效快照检查（无）→ null；第二次 = 已有核算单（已核算）→ 覆盖重算
        doReturn(null, calculated).when(service).getOne(any(Wrapper.class), anyBoolean());

        Map<String, Object> result = service.periodCalculate("2026-10");

        assertEquals(1, result.get("updatedCount"), "未生效核算单应照常更新");
        verify(service).updateById(calculated);
        // 入库流水为空 → 核算数量兜底 1，单位成本 = 50 / 1
        assertEquals(0, calculated.getUnitCost().compareTo(new BigDecimal("50.0000")),
                "单位成本应按本次归集重算，实际: " + calculated.getUnitCost());
    }

    @Test
    @DisplayName("成本报表：统计口径必须覆盖已生效核算单（否则生效期间的人工/制造费用整列为 0）")
    void costReport_shouldIncludeEffectiveCalculation() {
        java.util.List<Wrapper<?>> listQueries = new java.util.ArrayList<>();
        java.util.List<Wrapper<?>> oneQueries = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            listQueries.add(inv.getArgument(0));
            return List.of();
        }).when(service).list(any(Wrapper.class));
        org.mockito.Mockito.doAnswer(inv -> {
            oneQueries.add(inv.getArgument(0));
            return null;
        }).when(service).getOne(any(Wrapper.class), anyBoolean());
        doReturn(List.of()).when(inventoryTransactionMapper).selectList(any());
        Material material = new Material();
        material.setMaterialId(100L);
        material.setMaterialCode("WL100");
        material.setMaterialName("测试物料");
        doReturn(List.of(material)).when(materialMapper).selectList(any());

        service.getCostReport("2026-10", null);

        assertEquals(1, listQueries.size(), "报表物料收集必须只查一次");
        assertTrue(hasParam(listQueries.get(0), EFFECTIVE),
                "报表物料收集条件必须包含「已生效」，实际: " + describe(listQueries.get(0)));

        assertEquals(1, oneQueries.size(), "报表成本构成必须只查一次");
        assertTrue(hasParam(oneQueries.get(0), EFFECTIVE),
                "报表成本构成条件必须包含「已生效」，实际: " + describe(oneQueries.get(0)));
    }
}
