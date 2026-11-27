package com.erp.dashboard.service;

import com.erp.sale.entity.SaleOrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售成本取数（2026-11-06 新增，PRD《看板公式与多来源取数》第五节）。
 *
 * <p>单条销售明细按顺序取第一个可用成本：</p>
 * <ol>
 *   <li>单据上冻结的成本</li>
 *   <li>成本核算单（已生效 / 已确认，最新核算日期）</li>
 *   <li>库存移动平均成本（即时库存 总成本 / 总数量）</li>
 *   <li>最近采购价（已生效采购订单，优先不含税单价）</li>
 * </ol>
 *
 * <p>成本为空、≤0、或高于该行售价 3 倍，视为“成本缺失”，该行不参与毛利计算并计数；
 * 其中“单据成本有值但异常”的单独记为异常行，便于财务排查。</p>
 */
@Slf4j
@Service
public class SaleCostResolver {

    /** 成本异常阈值：单位成本高于售价的倍数 */
    private static final BigDecimal ABNORMAL_FACTOR = BigDecimal.valueOf(3);

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbc;

    /** 解析结果：与入参明细一一对应 */
    public static class Resolution {
        public final List<BigDecimal> unitCosts = new ArrayList<>();
        public final List<String> sources = new ArrayList<>();
        public final List<Boolean> included = new ArrayList<>();
        public int missingRows;
        public int abnormalRows;
        public int totalRows;
        public final Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        public BigDecimal validNetAmount = BigDecimal.ZERO;
        public BigDecimal totalCost = BigDecimal.ZERO;

        public boolean isAllMissing() {
            return totalRows > 0 && missingRows >= totalRows;
        }
    }

    public Resolution resolve(List<SaleOrderItem> items) {
        Resolution out = new Resolution();
        if (items == null || items.isEmpty()) return out;
        out.totalRows = items.size();

        List<Long> materialIds = items.stream()
                .map(SaleOrderItem::getMaterialId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BigDecimal> calcCost = materialIds.isEmpty() ? Map.of() : loadCalcCost(materialIds);
        Map<Long, BigDecimal> stockCost = materialIds.isEmpty() ? Map.of() : loadStockCost(materialIds);
        Map<Long, BigDecimal> purchasePrice = materialIds.isEmpty() ? Map.of() : loadPurchasePrice(materialIds);

        for (SaleOrderItem it : items) {
            BigDecimal priceRef = it.getPriceWithoutTax() != null ? it.getPriceWithoutTax() : it.getUnitPrice();
            BigDecimal own = it.getAvgCost();
            BigDecimal[] picked = pick(priceRef, own,
                    it.getMaterialId() == null ? null : calcCost.get(it.getMaterialId()),
                    it.getMaterialId() == null ? null : stockCost.get(it.getMaterialId()),
                    it.getMaterialId() == null ? null : purchasePrice.get(it.getMaterialId()));
            if (picked == null) {
                out.unitCosts.add(BigDecimal.ZERO);
                out.sources.add("缺失");
                out.included.add(false);
                out.missingRows++;
                if (own != null && own.compareTo(BigDecimal.ZERO) > 0) out.abnormalRows++;
            } else {
                BigDecimal unit = picked[0];
                String src = picked[1].intValue() == 0 ? "单据成本"
                        : picked[1].intValue() == 1 ? "核算成本"
                        : picked[1].intValue() == 2 ? "库存成本" : "采购价";
                out.unitCosts.add(unit);
                out.sources.add(src);
                out.included.add(true);
                out.sourceCounts.merge(src, 1, Integer::sum);
                if (own != null && own.compareTo(BigDecimal.ZERO) > 0 && own.compareTo(unit) != 0) {
                    out.abnormalRows++;
                }
                BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
                out.totalCost = out.totalCost.add(unit.multiply(qty));
                out.validNetAmount = out.validNetAmount.add(
                        it.getNetAmount() == null ? BigDecimal.ZERO : it.getNetAmount());
            }
        }
        return out;
    }

    /** 返回 [单位成本, 来源序号]，无可用值返回 null；序号 0=单据 1=核算 2=库存 3=采购 */
    private BigDecimal[] pick(BigDecimal priceRef, BigDecimal own, BigDecimal calc,
                              BigDecimal stock, BigDecimal purchase) {
        BigDecimal[] candidates = {own, calc, stock, purchase};
        for (int i = 0; i < candidates.length; i++) {
            BigDecimal c = candidates[i];
            if (c == null || c.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (priceRef != null && priceRef.compareTo(BigDecimal.ZERO) > 0
                    && c.compareTo(priceRef.multiply(ABNORMAL_FACTOR)) > 0) {
                continue; // 高于售价 3 倍，视为异常，尝试下一级兜底
            }
            return new BigDecimal[]{c, BigDecimal.valueOf(i)};
        }
        return null;
    }

    /** 最新已生效核算成本（单位成本） */
    private Map<Long, BigDecimal> loadCalcCost(List<Long> ids) {
        Map<Long, BigDecimal> map = new HashMap<>();
        try {
            jdbc.query("SELECT DISTINCT ON (material_id) material_id, unit_cost FROM cost_calculation"
                            + " WHERE status IN ('已生效','已确认') AND unit_cost > 0"
                            + " ORDER BY material_id, calc_date DESC, id DESC",
                    rs -> {
                        long mid = rs.getLong("material_id");
                        if (ids.contains(mid)) map.put(mid, rs.getBigDecimal("unit_cost"));
                    });
        } catch (Exception e) {
            log.warn("[销售成本] 读取核算成本失败: {}", e.getMessage());
        }
        return map;
    }

    /** 库存移动平均成本：总成本 / 总数量；数量为 0 取库存记录单位成本 */
    private Map<Long, BigDecimal> loadStockCost(List<Long> ids) {
        Map<Long, BigDecimal> map = new HashMap<>();
        try {
            jdbc.query("SELECT material_id, SUM(COALESCE(total_cost,0)) AS total_cost,"
                            + " SUM(COALESCE(quantity,0)) AS qty, MAX(unit_cost) AS unit_cost"
                            + " FROM inventory_current GROUP BY material_id",
                    rs -> {
                        long mid = rs.getLong("material_id");
                        if (!ids.contains(mid)) return;
                        BigDecimal qty = rs.getBigDecimal("qty");
                        BigDecimal total = rs.getBigDecimal("total_cost");
                        BigDecimal unit = rs.getBigDecimal("unit_cost");
                        BigDecimal v = (qty != null && qty.compareTo(BigDecimal.ZERO) > 0 && total != null)
                                ? total.divide(qty, 4, java.math.RoundingMode.HALF_UP) : unit;
                        if (v != null && v.compareTo(BigDecimal.ZERO) > 0) map.put(mid, v);
                    });
        } catch (Exception e) {
            log.warn("[销售成本] 读取库存成本失败: {}", e.getMessage());
        }
        return map;
    }

    /** 最近已生效采购价（优先不含税单价） */
    private Map<Long, BigDecimal> loadPurchasePrice(List<Long> ids) {
        Map<Long, BigDecimal> map = new HashMap<>();
        try {
            jdbc.query("SELECT DISTINCT ON (poi.material_id) poi.material_id,"
                            + " COALESCE(NULLIF(poi.price_without_tax,0), poi.unit_price) AS price"
                            + " FROM purchase_order_item poi"
                            + " JOIN purchase_order po ON poi.order_id = po.order_id"
                            + " WHERE po.status = '已生效'"
                            + " AND COALESCE(NULLIF(poi.price_without_tax,0), poi.unit_price) > 0"
                            + " ORDER BY poi.material_id, po.order_date DESC, poi.order_item_id DESC",
                    rs -> {
                        long mid = rs.getLong("material_id");
                        if (ids.contains(mid)) map.put(mid, rs.getBigDecimal("price"));
                    });
        } catch (Exception e) {
            log.warn("[销售成本] 读取采购价失败: {}", e.getMessage());
        }
        return map;
    }
}
