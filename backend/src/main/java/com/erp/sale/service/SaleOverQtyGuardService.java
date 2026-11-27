package com.erp.sale.service;

import com.erp.config.SysParamValidator;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.entity.SaleReturnItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售模块「超过来源单数量」三态管控（设置中心 → 销售参数）。
 *
 * <p>固定三种策略：不控制 / 预警提示 / 不允许；由 {@link SysParamValidator#getOverQtyPolicy(String)} 统一归一化，
 * 历史值「不检测」「警告提示」自动按新口径处理。</p>
 *
 * <p>口径：来源单数量按「来源明细原始数量」统计；已占用量按同来源明细、排除作废/取消单据实时汇总，
 * 本单数量再加总后与来源数量比较。只校验能定位到来源明细的行（手工新增、允许增加来源单货品的行不参与）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleOverQtyGuardService {

    public static final String KEY_SALE_ORDER = "sale.order.over.qty";
    public static final String KEY_SALE_DELIVERY = "sale.outbound.over.qty";
    public static final String KEY_SALE_RETURN = "sale.return.over.qty";

    private final JdbcTemplate jdbcTemplate;
    private final SysParamValidator sysParamValidator;

    /** 销售订单可选的来源类型（报价单 / 销售合同 / 打样单） */
    private static final List<SourceSpec> ORDER_SOURCES = List.of(
            new SourceSpec("报价", "SaleQuote", "sale_quote_item", "quote_item_id", "quantity",
                    "sale_quote", "quote_id", "quote_no", "quote_id", "line_no"),
            new SourceSpec("合同", "SaleContract", "sale_contract_item", "item_id", "quantity",
                    "sale_contract", "contract_id", "contract_no", "contract_id", "sort_no"),
            new SourceSpec("打样", "SampleOrder", "sample_order_item", "item_id", "base_qty",
                    "sample_order", "sample_id", "sample_no", "sample_id", "line_no")
    );

    /** 销售退货可选的来源类型（销售出库单 / 销售订单 / 销售发票） */
    private static final List<SourceSpec> RETURN_SOURCES = List.of(
            new SourceSpec("出库", "SaleDelivery", "sale_delivery_item", "delivery_item_id", "quantity",
                    "sale_delivery", "delivery_id", "delivery_no", "delivery_id", "line_no"),
            new SourceSpec("订单", "SaleOrder", "sale_order_item", "order_item_id", "quantity",
                    "sale_order", "order_id", "order_no", "order_id", "line_no"),
            new SourceSpec("发票", "SaleInvoice", "sale_invoice_item", "invoice_item_id", "quantity",
                    "sale_invoice", "invoice_id", "invoice_no", "invoice_id", "line_no")
    );

    private static final TargetSpec ORDER_TARGET =
            new TargetSpec("sale_order_item", "order_id", "sale_order", "order_id");
    private static final TargetSpec RETURN_TARGET =
            new TargetSpec("sale_return_item", "return_id", "sale_return", "return_id");

    /**
     * 销售订单：超过来源单数量下单。
     * 在「提交」严格校验阶段调用（保存仍按全站口径宽松放行）。
     */
    public void validateSaleOrder(SaleOrder order, List<SaleOrderItem> items) {
        if (items == null || items.isEmpty()) return;
        if (sysParamValidator.isOverQtyNoControl(KEY_SALE_ORDER)) return;

        String fallbackBillNo = order == null ? null : order.getSourceBillNo();
        String fallbackBillType = order == null ? null : order.getSourceBillType();
        Long currentBillId = order == null ? null : order.getOrderId();
        List<SourceLine> lines = new ArrayList<>();
        int rowNo = 1;
        for (SaleOrderItem item : items) {
            if (item == null) continue;
            lines.add(new SourceLine(
                    nonBlank(item.getSourceBillType()) ? item.getSourceBillType() : fallbackBillType,
                    nonBlank(item.getSourceBillNo()) ? item.getSourceBillNo() : fallbackBillNo,
                    item.getSourceItemId(),
                    item.getSourceLineNo(),
                    item.getQuantity(),
                    materialLabel(item.getMaterialCode(), item.getMaterialName()),
                    item.getLineNo() == null ? rowNo : item.getLineNo()));
            rowNo++;
        }
        check(KEY_SALE_ORDER, "销售订单", lines, ORDER_SOURCES, ORDER_TARGET, currentBillId, "已下单");
    }

    /**
     * 销售退货：超过来源单数量退货。
     * 在「提交」与「审核」双重调用，防止绕过提交直接审核。
     */
    public void validateSaleReturn(SaleReturn saleReturn, List<SaleReturnItem> items) {
        if (items == null || items.isEmpty()) return;
        if (sysParamValidator.isOverQtyNoControl(KEY_SALE_RETURN)) return;

        String fallbackBillNo = saleReturn == null ? null : saleReturn.getSourceBillNo();
        String fallbackBillType = saleReturn == null ? null : saleReturn.getSourceBillType();
        Long currentBillId = saleReturn == null ? null : saleReturn.getReturnId();
        List<SourceLine> lines = new ArrayList<>();
        int rowNo = 1;
        for (SaleReturnItem item : items) {
            if (item == null) continue;
            lines.add(new SourceLine(
                    nonBlank(item.getSourceBillType()) ? item.getSourceBillType() : fallbackBillType,
                    nonBlank(item.getSourceBillNo()) ? item.getSourceBillNo() : fallbackBillNo,
                    item.getSourceItemId(),
                    item.getSourceLineNo(),
                    item.getQuantity(),
                    materialLabel(item.getMaterialCode(), item.getMaterialName()),
                    item.getLineNo() == null ? rowNo : item.getLineNo()));
            rowNo++;
        }
        check(KEY_SALE_RETURN, "销售退货单", lines, RETURN_SOURCES, RETURN_TARGET, currentBillId, "已退货");
    }

    private void check(String configKey, String billTypeName, List<SourceLine> lines,
                       List<SourceSpec> specs, TargetSpec target, Long currentBillId, String usedLabel) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (SourceLine line : lines) {
            if (line.quantity == null || line.quantity.signum() <= 0) continue;
            SourceSpec spec = matchSpec(line.sourceBillType, specs);
            if (spec == null) continue;

            Resolved resolved = resolveSource(spec, line.sourceItemId, line.sourceBillNo, line.sourceLineNo);
            if (resolved == null || resolved.quantity == null) continue;

            String key = spec.keyword + "|" + resolved.itemId;
            Group group = groups.get(key);
            if (group == null) {
                group = new Group();
                group.spec = spec;
                group.sourceItemId = resolved.itemId;
                group.sourceQty = resolved.quantity;
                group.sourceBillNo = line.sourceBillNo;
                group.lineNo = line.lineNo;
                group.materialLabel = line.materialLabel;
                groups.put(key, group);
            }
            group.currentQty = group.currentQty.add(line.quantity);
        }

        for (Group group : groups.values()) {
            BigDecimal used = queryUsed(group, target, currentBillId);
            BigDecimal total = used.add(group.currentQty);
            boolean exceeded = group.sourceQty != null && total.compareTo(group.sourceQty) > 0;
            if (!exceeded) continue;

            String detail = String.format("物料[%s] 来源单[%s] 来源数量 %s，%s %s，本次 %s，合计 %s",
                    blankTo(group.materialLabel, "未命名物料"),
                    blankTo(group.sourceBillNo, "—"),
                    formatQty(group.sourceQty),
                    usedLabel,
                    formatQty(used),
                    formatQty(group.currentQty),
                    formatQty(total));
            sysParamValidator.applyOverQtyPolicy(configKey, billTypeName, group.lineNo, true, detail);
        }
    }

    /** 按来源明细主键或「来源单号 + 来源行号」定位来源明细，取原始数量 */
    private Resolved resolveSource(SourceSpec spec, Long sourceItemId, String sourceBillNo, Integer sourceLineNo) {
        if (sourceItemId != null) {
            try {
                BigDecimal qty = jdbcTemplate.queryForObject(
                        "SELECT " + spec.qtyCol + " FROM " + spec.itemTable + " WHERE " + spec.itemPk + " = ?",
                        BigDecimal.class, sourceItemId);
                if (qty != null) return new Resolved(sourceItemId, qty);
            } catch (Exception e) {
                log.debug("按来源明细主键取数量失败 {}.{}#{}: {}", spec.itemTable, spec.itemPk, sourceItemId, e.getMessage());
            }
        }
        if (sourceBillNo == null || sourceBillNo.isBlank() || sourceLineNo == null) return null;
        try {
            String sql = "SELECT i." + spec.itemPk + " AS itemId, i." + spec.qtyCol + " AS qty"
                    + " FROM " + spec.itemTable + " i"
                    + " JOIN " + spec.headerTable + " h ON h." + spec.headerPk + " = i." + spec.headerFk
                    + " WHERE h." + spec.headerNoCol + " = ? AND i." + spec.lineCol + " = ?"
                    + " LIMIT 1";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, sourceBillNo, sourceLineNo);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                Long itemId = toLong(row.get("itemId"));
                BigDecimal qty = toDecimal(row.get("qty"));
                if (itemId != null && qty != null) return new Resolved(itemId, qty);
            }
        } catch (Exception e) {
            log.debug("按来源单号+行号取数量失败 {}.{} {}#{}: {}",
                    spec.itemTable, spec.lineCol, sourceBillNo, sourceLineNo, e.getMessage());
        }
        return null;
    }

    /** 已占用量：同来源明细、未作废/取消、排除当前单据 */
    private BigDecimal queryUsed(Group group, TargetSpec target, Long currentBillId) {
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(i.quantity), 0)")
                .append(" FROM ").append(target.itemTable).append(" i")
                .append(" JOIN ").append(target.headerTable).append(" h")
                .append(" ON h.").append(target.headerPk).append(" = i.").append(target.headerFk)
                .append(" WHERE i.source_item_id = ?")
                .append(" AND (i.source_bill_type LIKE ? OR i.source_bill_type LIKE ?)")
                .append(" AND (h.status IS NULL OR h.status NOT IN ('已作废', '已取消'))");
        List<Object> args = new ArrayList<>();
        args.add(group.sourceItemId);
        args.add("%" + group.spec.keyword + "%");
        args.add("%" + group.spec.code + "%");
        if (currentBillId != null) {
            sql.append(" AND h.").append(target.headerPk).append(" <> ?");
            args.add(currentBillId);
        }
        // 【2026-11-17 替换货】换货生成的出库单不计入普通出库"已占用"（它有自己的 H1/H2 硬约束）
        if ("sale_delivery_item".equals(target.itemTable)) {
            sql.append(" AND (h.source_bill_type IS NULL OR h.source_bill_type NOT LIKE '%销售换货单%')");
        }
        try {
            BigDecimal used = jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, args.toArray());
            if (used == null) used = BigDecimal.ZERO;
            // 【2026-11-17 替换货】普通出库"已占用"改净口径：减去该订单行已退货数量
            // 解决"退 10 再补 10 被误拦"；退货作废时会负数回冲该字段，口径天然自愈
            if ("sale_delivery_item".equals(target.itemTable) && group.sourceItemId != null) {
                try {
                    BigDecimal returned = jdbcTemplate.queryForObject(
                            "SELECT COALESCE(turned_return_quantity, 0) FROM sale_order_item WHERE order_item_id = ?",
                            BigDecimal.class, group.sourceItemId);
                    if (returned != null) used = used.subtract(returned).max(BigDecimal.ZERO);
                } catch (Exception ignore) {
                    // 取不到退货数量时维持原口径，不阻断校验
                }
            }
            return used;
        } catch (Exception e) {
            log.warn("汇总来源明细已占用量失败 {}#{}: {}", target.itemTable, group.sourceItemId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private static SourceSpec matchSpec(String sourceBillType, List<SourceSpec> specs) {
        if (sourceBillType == null || sourceBillType.isBlank()) return null;
        String label = sourceBillType.trim();
        String lower = label.toLowerCase();
        for (SourceSpec spec : specs) {
            if (label.contains(spec.keyword) || lower.contains(spec.code.toLowerCase())) {
                return spec;
            }
        }
        return null;
    }

    private static String materialLabel(String code, String name) {
        String c = code == null ? "" : code.trim();
        String n = name == null ? "" : name.trim();
        if (c.isEmpty()) return n;
        if (n.isEmpty()) return c;
        return c + " " + n;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankTo(String value, String defaultValue) {
        return nonBlank(value) ? value : defaultValue;
    }

    private static String formatQty(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static class SourceSpec {
        final String keyword;
        final String code;
        final String itemTable;
        final String itemPk;
        final String qtyCol;
        final String headerTable;
        final String headerPk;
        final String headerNoCol;
        final String headerFk;
        final String lineCol;

        SourceSpec(String keyword, String code, String itemTable, String itemPk, String qtyCol,
                   String headerTable, String headerPk, String headerNoCol, String headerFk, String lineCol) {
            this.keyword = keyword;
            this.code = code;
            this.itemTable = itemTable;
            this.itemPk = itemPk;
            this.qtyCol = qtyCol;
            this.headerTable = headerTable;
            this.headerPk = headerPk;
            this.headerNoCol = headerNoCol;
            this.headerFk = headerFk;
            this.lineCol = lineCol;
        }
    }

    private static class TargetSpec {
        final String itemTable;
        final String headerFk;
        final String headerTable;
        final String headerPk;

        TargetSpec(String itemTable, String headerFk, String headerTable, String headerPk) {
            this.itemTable = itemTable;
            this.headerFk = headerFk;
            this.headerTable = headerTable;
            this.headerPk = headerPk;
        }
    }

    private static class SourceLine {
        final String sourceBillType;
        final String sourceBillNo;
        final Long sourceItemId;
        final Integer sourceLineNo;
        final BigDecimal quantity;
        final String materialLabel;
        final int lineNo;

        SourceLine(String sourceBillType, String sourceBillNo, Long sourceItemId, Integer sourceLineNo,
                   BigDecimal quantity, String materialLabel, int lineNo) {
            this.sourceBillType = sourceBillType;
            this.sourceBillNo = sourceBillNo;
            this.sourceItemId = sourceItemId;
            this.sourceLineNo = sourceLineNo;
            this.quantity = quantity;
            this.materialLabel = materialLabel;
            this.lineNo = lineNo;
        }
    }

    private static class Group {
        SourceSpec spec;
        Long sourceItemId;
        BigDecimal sourceQty;
        String sourceBillNo;
        String materialLabel;
        BigDecimal currentQty = BigDecimal.ZERO;
        int lineNo = 1;
    }

    private static class Resolved {
        final Long itemId;
        final BigDecimal quantity;

        Resolved(Long itemId, BigDecimal quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
