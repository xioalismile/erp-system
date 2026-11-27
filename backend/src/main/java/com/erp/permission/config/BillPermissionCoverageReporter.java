package com.erp.permission.config;

import com.erp.export.registry.BillExcelRegistry;
import com.erp.report.controller.BillDetailReportCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段权限覆盖自检（启动日志，不阻断）。
 *
 * <p>系统性改造后的口径：字段权限的页面键不再逐接口手写，而是按
 * 「实体类 @TableName / 报表配置主表 / 单据类型主表」自动推导。启动时把"推导不出来"的实体点名，
 * 避免新单据漏一个注解就悄悄绕过字段权限。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillPermissionCoverageReporter {

    private final BillPermissionPathRegistry resolver;
    private final BillDetailReportCatalog reportCatalog;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void report() {
        List<String> noTableName = new ArrayList<>();
        int total = 0;
        for (BillExcelRegistry.Bill bill : BillExcelRegistry.all()) {
            total++;
            if (resolver.fromEntityClass(bill.entityClass()) == null) {
                noTableName.add(bill.name() + "(" + bill.code() + ")");
            }
        }
        if (noTableName.isEmpty()) {
            log.info("[字段权限] 自动对应自检通过：{} 个实体均可由 @TableName 得出页面键", total);
        } else {
            log.warn("[字段权限] 以下 {} 个实体缺 @TableName，无法自动对应页面键（字段权限/导出剔列将不生效）：{}",
                    noTableName.size(), noTableName);
        }
        log.info("[字段权限] 明细报表 {} 个按配置主表自动对应（含全明细报表）；列表接口另有 {} 条路径兜底登记",
                reportCatalog.entries().size(), resolver.listPathCount());
    }
}
