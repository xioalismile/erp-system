package com.erp.kai.service.adapter;

import com.erp.kai.entity.KaiExtConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演示系统适配器（FEAT-KAI-STANDALONE-001）。
 * 内置一套内存态"第三方 ERP"（物料 / 客户 / 供应商 / 销售订单），
 * 让客户零配置体验完整链路：查资料 → 导入建档 → 智能分类 → 开单 → 提交。
 * 数据仅存内存、重启即清，不碰任何真实库。
 */
@Slf4j
@Component
public class DemoErpAdapter implements KaiExtAdapter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, Map<String, Object>> materials = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> customers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> suppliers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> saleOrders = new ConcurrentHashMap<>();
    private final AtomicLong billSeq = new AtomicLong(0);
    private final AtomicLong recSeq = new AtomicLong(0);

    public DemoErpAdapter() {
        seed();
    }

    private void seed() {
        String[][] mats = {
                {"WL001", "304不锈钢板 1.5mm", "原材料", "张", "86.50"},
                {"WL002", "铝型材 4040", "原材料", "米", "22.00"},
                {"WL003", "M8x20 六角螺栓", "标准件", "个", "0.35"},
                {"WL004", "控制面板总成 A2", "成品", "台", "1280.00"},
                {"WL005", "密封圈 45x3", "辅料", "个", "1.20"},
                {"WL006", "包装纸箱 5号", "包材", "个", "3.80"}
        };
        for (String[] m : mats) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", m[0]);
            r.put("name", m[1]);
            r.put("category", m[2]);
            r.put("unit", m[3]);
            r.put("price", m[4]);
            r.put("createdAt", TS.format(LocalDateTime.now()));
            materials.put(m[0], r);
        }
        String[][] custs = {
                {"KH001", "华通机械有限公司", "王经理", "0512-66880011"},
                {"KH002", "永昌贸易商行", "李女士", "021-55667788"},
                {"KH003", "精工模具厂", "张工", "0755-26880022"},
                {"KH004", "蓝海电子科技", "陈总", "0577-88990033"}
        };
        for (String[] c : custs) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", c[0]);
            r.put("name", c[1]);
            r.put("contact", c[2]);
            r.put("phone", c[3]);
            r.put("createdAt", TS.format(LocalDateTime.now()));
            customers.put(c[0], r);
        }
        String[][] sups = {
                {"GYS001", "兴钢材批发", "赵经理"},
                {"GYS002", "紧固件直供中心", "孙先生"}
        };
        for (String[] s : sups) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", s[0]);
            r.put("name", s[1]);
            r.put("contact", s[2]);
            r.put("createdAt", TS.format(LocalDateTime.now()));
            suppliers.put(s[0], r);
        }
    }

    @Override
    public String type() {
        return "DEMO";
    }

    @Override
    public Map<String, Object> testConnection(KaiExtConnection conn) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", "演示系统连接正常（内置示例数据：物料 " + materials.size()
                + " 条、客户 " + customers.size() + " 条、供应商 " + suppliers.size() + " 条）");
        return out;
    }

    @Override
    public List<Map<String, Object>> listRecords(KaiExtConnection conn, String objectType, Map<String, Object> filters) {
        String keyword = filters == null || filters.get("keyword") == null ? ""
                : String.valueOf(filters.get("keyword")).trim().toLowerCase();
        int limit = filters != null && filters.get("limit") != null
                ? Math.min(200, Integer.parseInt(filters.get("limit").toString())) : 50;
        Collection<Map<String, Object>> source = sourceOf(objectType);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : source) {
            if (keyword.isEmpty() || matches(r, keyword)) {
                out.add(r);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    private boolean matches(Map<String, Object> r, String kw) {
        for (Object v : r.values()) {
            if (v != null && String.valueOf(v).toLowerCase().contains(kw)) return true;
        }
        return false;
    }

    private Collection<Map<String, Object>> sourceOf(String objectType) {
        return switch (objectType) {
            case "material" -> materials.values();
            case "customer" -> customers.values();
            case "supplier" -> suppliers.values();
            case "sale_order" -> saleOrders.values();
            default -> throw new IllegalArgumentException("演示系统暂不支持查询该对象类型：" + objectType);
        };
    }

    @Override
    public Map<String, Object> createRecords(KaiExtConnection conn, String objectType, List<Map<String, Object>> records) {
        if (!"material".equals(objectType) && !"customer".equals(objectType) && !"supplier".equals(objectType)) {
            throw new IllegalArgumentException("演示系统只支持建立物料/客户/供应商档案");
        }
        Map<String, Map<String, Object>> target =
                "material".equals(objectType) ? materials : "customer".equals(objectType) ? customers : suppliers;
        List<Map<String, Object>> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> rec : records) {
            i++;
            String code = rec.get("code") == null ? "" : String.valueOf(rec.get("code")).trim();
            String name = rec.get("name") == null ? "" : String.valueOf(rec.get("name")).trim();
            if (code.isEmpty() && name.isEmpty()) {
                failed.add(row(i, "(空行)", "编码与名称都为空"));
                continue;
            }
            // 查重：编码或名称任一已存在即跳过
            String dup = null;
            for (Map<String, Object> exist : target.values()) {
                if (!code.isEmpty() && code.equalsIgnoreCase(String.valueOf(exist.get("code")))) {
                    dup = "编码已存在（" + exist.get("code") + " " + exist.get("name") + "）";
                    break;
                }
                if (!name.isEmpty() && name.equals(String.valueOf(exist.get("name")))) {
                    dup = "名称已存在（" + exist.get("code") + " " + exist.get("name") + "）";
                    break;
                }
            }
            if (dup != null) {
                failed.add(row(i, code.isEmpty() ? name : code, dup));
                continue;
            }
            String finalCode = code.isEmpty() ? ("DEMO" + String.format("%04d", recSeq.incrementAndGet())) : code;
            Map<String, Object> saved = new LinkedHashMap<>(rec);
            saved.put("code", finalCode);
            saved.put("createdAt", TS.format(LocalDateTime.now()));
            target.put(finalCode, saved);
            Map<String, Object> ok = new LinkedHashMap<>(saved);
            ok.put("result", "建档成功");
            success.add(ok);
        }
        return summary(total(records), success, failed);
    }

    @Override
    public Map<String, Object> createBill(KaiExtConnection conn, Map<String, Object> bill) {
        String customerName = bill.get("customerName") == null ? "" : String.valueOf(bill.get("customerName")).trim();
        if (customerName.isEmpty()) {
            throw new IllegalArgumentException("开单缺少客户，请提供客户名称");
        }
        // 客户必须已存在（演示系统口径：先建档再开单）
        Map<String, Object> cust = customers.values().stream()
                .filter(c -> String.valueOf(c.get("name")).equals(customerName)
                        || String.valueOf(c.get("code")).equalsIgnoreCase(customerName))
                .findFirst().orElse(null);
        if (cust == null) {
            throw new IllegalArgumentException("演示系统里没有客户「" + customerName + "」，请先让小楷导入该客户档案");
        }
        Object itemsObj = bill.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalArgumentException("开单缺少明细行，至少要有一行物料与数量");
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        int idx = 0;
        for (Object o : items) {
            idx++;
            @SuppressWarnings("unchecked")
            Map<String, Object> it = (Map<String, Object>) o;
            String mName = it.get("materialName") == null ? "" : String.valueOf(it.get("materialName")).trim();
            Map<String, Object> mat = materials.values().stream()
                    .filter(m -> String.valueOf(m.get("name")).equals(mName)
                            || String.valueOf(m.get("code")).equalsIgnoreCase(mName))
                    .findFirst().orElse(null);
            if (mat == null) {
                throw new IllegalArgumentException("第 " + idx + " 行物料「" + (mName.isEmpty() ? "(空)" : mName)
                        + "」在演示系统里不存在，请先导入物料或核对名称");
            }
            java.math.BigDecimal qty = num(it.get("quantity"));
            if (qty == null || qty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("第 " + idx + " 行缺少数量或数量不大于 0");
            }
            java.math.BigDecimal price = num(it.get("unitPrice"));
            if (price == null) price = new java.math.BigDecimal(String.valueOf(mat.getOrDefault("price", "0")));
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialCode", mat.get("code"));
            line.put("materialName", mat.get("name"));
            line.put("unit", mat.get("unit"));
            line.put("quantity", qty);
            line.put("unitPrice", price);
            line.put("amount", qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP));
            lines.add(line);
        }
        java.math.BigDecimal total = lines.stream().map(l -> (java.math.BigDecimal) l.get("amount"))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        String billNo = "XSDD-" + String.format("%05d", billSeq.incrementAndGet());
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("billNo", billNo);
        saved.put("customerCode", cust.get("code"));
        saved.put("customerName", cust.get("name"));
        saved.put("orderDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        saved.put("status", "草稿");
        saved.put("items", lines);
        saved.put("totalAmount", total);
        saved.put("createdAt", TS.format(LocalDateTime.now()));
        saleOrders.put(billNo, saved);
        Map<String, Object> out = new LinkedHashMap<>(saved);
        out.put("result", "已创建销售订单草稿");
        return out;
    }

    @Override
    public Map<String, Object> executeAction(KaiExtConnection conn, String action, Map<String, Object> params) {
        return switch (action) {
            case "submit" -> {
                String billNo = params == null || params.get("billNo") == null ? "" : String.valueOf(params.get("billNo")).trim();
                if (billNo.isEmpty()) {
                    throw new IllegalArgumentException("请提供要操作的单据号");
                }
                Map<String, Object> bill = saleOrders.get(billNo);
                if (bill == null) {
                    throw new IllegalArgumentException("演示系统里没有单据 " + billNo);
                }
                if (!"草稿".equals(String.valueOf(bill.get("status")))) {
                    throw new IllegalArgumentException("单据 " + billNo + " 当前是「" + bill.get("status") + "」，只有草稿才能提交");
                }
                bill.put("status", "已提交");
                bill.put("submittedAt", TS.format(LocalDateTime.now()));
                Map<String, Object> out = new LinkedHashMap<>(bill);
                out.put("result", "单据 " + billNo + " 已提交");
                yield out;
            }
            case "categorize" -> categorize(params);
            default -> throw new IllegalArgumentException("演示系统暂不支持动作：" + action);
        };
    }

    /**
     * 智能分类（演示系统）：把一批已存在的物料改到指定分类。
     * params: {"objectType":"material", "assignments":[{"code":"WL001","category":"新分类"}]}
     */
    public Map<String, Object> categorize(Map<String, Object> params) {
        Object typeObj = params.get("objectType");
        if (typeObj == null || !"material".equals(String.valueOf(typeObj))) {
            throw new IllegalArgumentException("演示系统的智能分类目前只支持物料（objectType=material）");
        }
        Object asgObj = params.get("assignments");
        if (!(asgObj instanceof List<?> assignments) || assignments.isEmpty()) {
            throw new IllegalArgumentException("缺少分类清单（assignments：[{code, category}]）");
        }
        List<Map<String, Object>> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        int i = 0;
        for (Object o : assignments) {
            i++;
            if (!(o instanceof Map<?, ?> a)) continue;
            String code = a.get("code") == null ? "" : String.valueOf(a.get("code")).trim();
            String category = a.get("category") == null ? "" : String.valueOf(a.get("category")).trim();
            Map<String, Object> mat = materials.get(code);
            if (mat == null) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("index", i);
                f.put("name", code);
                f.put("reason", "演示系统里没有该物料");
                failed.add(f);
                continue;
            }
            if (category.isEmpty()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("index", i);
                f.put("name", code);
                f.put("reason", "缺少目标分类");
                failed.add(f);
                continue;
            }
            mat.put("category", category);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("code", code);
            ok.put("name", mat.get("name"));
            ok.put("category", category);
            ok.put("result", "分类已调整为「" + category + "」");
            success.add(ok);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", assignments.size());
        out.put("successCount", success.size());
        out.put("failCount", failed.size());
        out.put("success", success);
        out.put("failed", failed);
        return out;
    }

    @Override
    public Map<String, String> supportedObjectTypes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("material", "物料");
        m.put("customer", "客户");
        m.put("supplier", "供应商");
        m.put("sale_order", "销售订单");
        return m;
    }

    @Override
    public Map<String, String> writableFields(String objectType) {
        Map<String, String> m = new LinkedHashMap<>();
        switch (objectType == null ? "" : objectType) {
            case "material" -> {
                m.put("code", "编码（可留空自动生成）");
                m.put("name", "名称（必填）");
                m.put("category", "分类");
                m.put("unit", "单位");
                m.put("price", "单价");
            }
            case "customer", "supplier" -> {
                m.put("code", "编码（可留空自动生成）");
                m.put("name", "名称（必填）");
                m.put("contact", "联系人");
                m.put("phone", "电话");
            }
            case "sale_order" -> {
                m.put("customerName", "客户（必须已建档）");
                m.put("items", "明细行数组：materialName（物料）、quantity（数量）、unitPrice（单价，可留空取默认价）");
            }
            default -> { /* 未知类型给空 */ }
        }
        return m;
    }

    private Map<String, Object> row(int index, String name, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", index);
        m.put("name", name);
        m.put("reason", reason);
        return m;
    }

    private Map<String, Object> summary(int total, List<Map<String, Object>> success, List<Map<String, Object>> failed) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("successCount", success.size());
        out.put("failCount", failed.size());
        out.put("success", success);
        out.put("failed", failed);
        return out;
    }

    private int total(List<Map<String, Object>> records) {
        return records == null ? 0 : records.size();
    }

    private java.math.BigDecimal num(Object o) {
        if (o == null) return null;
        try {
            return new java.math.BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
