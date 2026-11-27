package com.erp.permission.config;

import com.erp.common.bill.link.BillLinkRegistry;
import com.erp.report.controller.BillDetailReportCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.annotation.TableName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口路径 → 字段权限页面键（page_key）的统一登记。
 *
 * <p>2026-11-27 之前只有 4 张单据手写在 {@link FieldPermissionResponseAdvice} 里，
 * 其余单据配了字段权限也不生效（实报：一张单生效、下一张不生效）。现改为两段式：</p>
 * <ol>
 *   <li><b>列表接口</b>：这里集中登记一次（新增单据在此补一行）；</li>
 *   <li><b>明细报表接口</b>：不再登记——启动时扫描全部明细报表控制器，
 *       用报表配置的 mainTable 自动得出 page_key（含"全明细"合并报表，以后新增报表零改动）。</li>
 * </ol>
 *
 * <p>匹配规则：路径等于前缀或以前缀 + "/" 开头；多个前缀命中时取最长的（避免父路径抢先）。</p>
 */
@Component
@RequiredArgsConstructor
public class BillPermissionPathRegistry {

    private final BillDetailReportCatalog reportCatalog;
    private final BillLinkRegistry linkRegistry;
    /** 报表接口前缀 → page_key 缓存（mainTable 是静态配置，解析一次即可；避免每次请求重建合并配置） */
    private final Map<String, String> reportBaseCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 实体类 → page_key（首选，系统性口径）：读实体上的 @TableName，不再依赖接口路径登记。
     * 没有 @TableName 的（DTO/Map 视图）返回 null，由调用方回退路径登记。
     */
    public String fromEntityClass(Class<?> entityClass) {
        if (entityClass == null) return null;
        TableName tn = AnnotatedElementUtils.findMergedAnnotation(entityClass, TableName.class);
        if (tn != null && tn.value() != null && !tn.value().isBlank()) return tn.value();
        return null;
    }

    /** 单据类型 → page_key（打印等只有 billType 的通道用）：与来源去向登记表同源 */
    public String fromBillType(String billType) {
        return linkRegistry.tableOf(billType);
    }

    /** 列表接口前缀 → page_key（= 单据主表名；与 sys_role_field_permission.page_key 同口径） */
    private static final Map<String, String> LIST_PATHS = new LinkedHashMap<>();

    static {
        // 销售
        put("/api/sale/quotes", "sale_quote");
        put("/api/sale/contracts", "sale_contract");
        put("/api/sale/orders", "sale_order");
        put("/api/sale/deliveries", "sale_delivery");
        put("/api/sale/returns", "sale_return");
        put("/api/sale/exchanges", "sale_exchange");
        put("/api/sale/repairs", "sale_repair");
        put("/api/sale/invoices", "sale_invoice");
        put("/api/sale/price-strategy", "sale_price_strategy");
        // 采购
        put("/api/purchase/requests", "purchase_request");
        put("/api/purchase/contracts", "purchase_contract");
        put("/api/purchase/orders", "purchase_order");
        put("/api/purchase/inbounds", "purchase_receipt");
        put("/api/purchase/returns", "purchase_return");
        put("/api/purchase/invoices", "purchase_invoice");
        put("/api/purchase/price-strategy", "purchase_price_strategy");
        // 库存
        put("/api/inventory/material-requisitions", "material_requisition");
        put("/api/inventory/other-receipts", "other_receipt");
        put("/api/inventory/other-issues", "other_issue");
        put("/api/inventory/stock-transfers", "stock_transfer");
        put("/api/inventory/stock-checks", "stock_check");
        // 生产 / 组装拆卸
        put("/api/production/workorders", "production_order");
        put("/api/production/receipts", "production_receipt");
        put("/api/production/returns", "material_requisition");
        put("/api/production/supplements", "material_requisition");
        put("/api/production/operation-report", "operation_report");
        put("/api/production/mrp-plan", "mrp_plan");
        put("/api/produce/assemble", "produce_assemble");
        put("/api/produce/disassemble", "produce_disassemble");
        // 委外
        put("/api/subcontract/orders", "subcontract_order");
        put("/api/subcontract/picks", "subcontract_pick");
        put("/api/subcontract/inbounds", "subcontract_inbound");
        put("/api/subcontract/returns", "subcontract_return");
        put("/api/subcontract/supplements", "subcontract_supplement");
        // 财务
        put("/api/finance/receipts", "cash_receipt");
        put("/api/finance/vouchers", "cash_payment");
        put("/api/finance/operation-expense", "operation_expense");
        put("/api/finance/wage-settlements", "wage_settlement");
        put("/api/sale-pre-receipt", "sale_pre_receipt");
        put("/api/sale-pre-receipts", "sale_pre_receipt");
        put("/api/purchase-pre-payment", "purchase_pre_payment");
        put("/api/purchase-pre-payments", "purchase_pre_payment");
        put("/api/cost-calculation", "cost_calculation");
        // 打样
        put("/api/sample/orders", "sample_order");
        put("/api/free-sample", "free_sample_order");
        // 基础资料（字段权限同样按 page_key 配置，这里一并覆盖）
        put("/api/basedata/customer", "customer");
        put("/api/basedata/supplier", "supplier");
        put("/api/basedata/material", "material");
        put("/api/basedata/material-category", "material_category");
        put("/api/basedata/employee", "employee");
        put("/api/basedata/department", "department");
        put("/api/basedata/warehouse", "warehouse");
        put("/api/basedata/location", "location");
        put("/api/basedata/currency", "currency");
        put("/api/basedata/settle", "settle");
        put("/api/basedata/price-level", "price_level");
        put("/api/basedata/unit", "unit");
        put("/api/basedata/account", "account");
        put("/api/basedata/bank-account", "bank_account");
        put("/api/masterdata/bom", "bom");
    }

    private static void put(String path, String pageKey) {
        LIST_PATHS.put(path, pageKey);
    }

    /** 列表接口兜底登记条数（自检日志用） */
    public int listPathCount() {
        return LIST_PATHS.size();
    }

    /** 请求路径 → page_key；未登记返回 null（字段权限不生效，保持旧行为） */
    public String resolve(String path) {
        if (path == null || path.isBlank()) return null;

        String bestKey = null;
        int bestLen = -1;
        for (Map.Entry<String, String> e : LIST_PATHS.entrySet()) {
            String prefix = e.getKey();
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                if (prefix.length() > bestLen) {
                    bestLen = prefix.length();
                    bestKey = e.getValue();
                }
            }
        }
        if (bestKey != null) return bestKey;

        // 明细报表：启动扫描到的全部报表控制器，按接口路径自动对应到配置的主表
        List<BillDetailReportCatalog.Entry> sorted = new ArrayList<>(reportCatalog.entries());
        sorted.sort(Comparator.comparingInt((BillDetailReportCatalog.Entry e) ->
                e.getApiBase() == null ? 0 : e.getApiBase().length()).reversed());
        for (BillDetailReportCatalog.Entry e : sorted) {
            String base = e.getApiBase();
            if (base == null || base.isBlank()) continue;
            if (!(path.equals(base) || path.startsWith(base + "/"))) continue;
            String cachedKey = reportBaseCache.get(base);
            if (cachedKey != null) return cachedKey;
            try {
                var cfg = e.getEngine().getConfig();
                if (cfg != null && cfg.mainTable != null) {
                    reportBaseCache.put(base, cfg.mainTable);
                    return cfg.mainTable;
                }
            } catch (Exception ignore) {
                // 单个报表配置异常不影响其它报表匹配
            }
        }
        return null;
    }
}
