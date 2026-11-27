package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.common.scope.DataScopeResolver;
import com.erp.permission.service.FieldPermissionOutputFilter;
import com.erp.report.controller.BillDetailReportCatalog;
import com.erp.report.detail.BillDetailReportConfig;
import com.erp.report.detail.BillDetailReportEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 只读工具：按报表中心口径一键出**固定报表**（单据明细报表族，40 张）。
 *
 * <p>报表目录<b>自动登记</b>（立项三期）：由同包目录桥 {@link BillDetailReportCatalog} 扫描全部
 * 报表控制器取报表中文名与菜单路由——新增一张明细报表只要按既有规范建控制器，本工具目录自动多一行。</p>
 *
 * <p>数据与权限口径与报表页面<b>完全同一条链路</b>：查询走
 * {@code BillDetailReportEngine.page(params, page, size, ctx, keys)}（同一份 SQL、同一份
 * f_* 白名单筛选、同一份统计卡/合计行）；数据权限按用户重新解析
 * （{@link DataScopeResolver#resolveForUser}，与页面 resolve(request) 同口径）；菜单权限按
 * 「/{模块}/report/{后缀}」路径判定（与 sys_role_permission.menu_id 存路由路径的授权口径一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportQueryTool implements AiTool {

    private final BillDetailReportCatalog catalog;
    private final DataScopeResolver dataScopeResolver;
    /**
     * 字段权限：小助手工具跑在线程池里，没有请求上下文，必须按用户显式解析
     * （与界面/导出同一份规则，见字段权限统一方案）。
     */
    private final FieldPermissionOutputFilter fieldPermissionOutputFilter;

    @Override
    public String getName() {
        return "report_query";
    }

    @Override
    public String getDescription() {
        return "【只读】按报表中心的固定报表一键出数（单据明细报表族：销售订单明细表/销售出库明细表/"
                + "采购入库明细表/生产领料明细表/收付款明细 等）。"
                + "report 不传 = 返回可用报表清单；传报表中文名 = 出数，支持日期区间与分页，"
                + "返回明细行 + 统计卡（单据数/数量合计/价税合计）+ 合计行。"
                + "数据口径、数据权限与报表页面完全一致。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("report", Map.of("type", "string",
                "description", "报表中文名（可先不传，返回可用报表清单）；如「销售订单明细报表」"));
        props.put("dateFrom", Map.of("type", "string",
                "description", "单据日期起（YYYY-MM-DD，含）；不传则不按日期过滤"));
        props.put("dateTo", Map.of("type", "string",
                "description", "单据日期止（YYYY-MM-DD，含）"));
        props.put("page", Map.of("type", "integer", "description", "页码，默认 1"));
        props.put("size", Map.of("type", "integer", "description", "每页行数，默认 20，最大 100"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            List<BillDetailReportCatalog.Entry> entries = catalog.entries();
            if (entries.isEmpty()) {
                return fail("暂未发现可用的固定报表（单据明细报表族）");
            }
            String report = str(params == null ? null : params.get("report"));
            if (report == null || report.isBlank()) {
                // 目录模式：返回可用报表清单
                List<Map<String, Object>> list = new ArrayList<>();
                for (BillDetailReportCatalog.Entry e : entries) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("报表", e.getTitle());
                    m.put("日期条件", e.getDateKey() != null ? "支持" : "无");
                    list.add(m);
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("success", true);
                out.put("message", "共 " + entries.size()
                        + " 张固定报表可查；传 report=报表中文名 出数，可带 dateFrom/dateTo");
                out.put("data", list);
                out.put("total", list.size());
                return out;
            }

            // 按名称解析（精确 → 包含唯一命中；多命中列候选，禁止替用户猜）
            BillDetailReportCatalog.Entry hit = catalog.find(report);
            if (hit == null) {
                List<String> candidates = catalog.findLike(report);
                if (candidates.size() == 1) {
                    hit = catalog.find(candidates.get(0));
                } else if (candidates.size() > 1) {
                    return fail("报表名不精确，命中最像的有多张：" + String.join("、", candidates) + "，请说全名");
                }
            }
            if (hit == null) {
                return fail("没有叫「" + report + "」的固定报表；可先不传 report 查看可用报表清单");
            }

            // 菜单权限：与界面同口径（菜单路由路径授权；管理员/通配符直通）
            if (scope != null && !scope.isAdmin() && !scope.hasPermission("*")
                    && !scope.hasMenuPath(hit.getRoute())) {
                log.info("[AI固定报表] 权限判定 → 拒绝 title={} route={}", hit.getTitle(), hit.getRoute());
                return fail("无权限：需要「" + hit.getTitle() + "」报表菜单权限，"
                        + "请联系管理员在 设置中心 → 角色权限设置 中授权");
            }

            // 组装引擎参数（f_* 白名单由引擎解析，未知键安全忽略）
            Map<String, String> engineParams = new LinkedHashMap<>();
            LocalDate from = parseDate(params, "dateFrom");
            LocalDate to = parseDate(params, "dateTo");
            if (hit.getDateKey() != null) {
                if (from != null) engineParams.put("f_" + hit.getDateKey() + "From", from.toString());
                if (to != null) engineParams.put("f_" + hit.getDateKey() + "To", to.toString());
            } else if (from != null || to != null) {
                log.info("[AI固定报表] 报表 {} 无单据日期筛选键，日期条件被忽略", hit.getTitle());
            }

            int page = intVal(params, "page", 1);
            int size = Math.min(intVal(params, "size", 20), 100);
            DataScopeResolver.DataScopeContext ctx = resolveScope(scope);
            BillDetailReportEngine eng = hit.getEngine();
            Map<String, Object> pageData = eng.page(engineParams, page, size, ctx, null);

            // 字段权限：与报表页面同一口径（页面键 = 报表配置主表），按用户显式解析后剔除
            Set<String> hidden = hiddenKeysOf(eng, scope);
            List<Map<String, Object>> records = castRecords(pageData.get("records"));
            fieldPermissionOutputFilter.filterRecords(hidden, records);

            List<String> keys = records.isEmpty() ? Collections.emptyList()
                    : new ArrayList<>(records.get(0).keySet());
            List<String> labels = eng.labelsOf(keys);
            Map<String, String> labelMap = new LinkedHashMap<>();
            for (int i = 0; i < keys.size() && i < labels.size(); i++) {
                labelMap.put(keys.get(i), labels.get(i));
            }
            labelMap = fieldPermissionOutputFilter.filterLabels(hidden, labelMap);

            Object stats = filterMap(hidden, pageData.get("stats"));
            Object totals = filterMap(hidden, pageData.get("totals"));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("message", "【" + hit.getTitle() + "】"
                    + (from != null || to != null
                        ? "日期 " + (from == null ? "…" : from) + " ~ " + (to == null ? "…" : to) + "，"
                        : "")
                    + "第 " + pageData.get("page") + " 页 / 共 " + pageData.get("total") + " 行；"
                    + statText(hidden, stats));
            out.put("data", records);
            out.put("total", pageData.get("total"));
            out.put("page", pageData.get("page"));
            out.put("size", pageData.get("size"));
            out.put("labels", labelMap);
            out.put("stats", stats);
            out.put("totals", totals);
            return out;
        } catch (DateTimeParseException e) {
            return fail("日期格式应为 YYYY-MM-DD，收到：" + e.getParsedString());
        } catch (Exception e) {
            log.error("[AI固定报表] 查询失败", e);
            return fail("查询失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 数据权限上下文：按用户解析（与页面 resolve(request) 同口径，非 Web 场景专用入口） */
    private DataScopeResolver.DataScopeContext resolveScope(AiDataScope scope) {
        if (scope == null || scope.getUserId() == null) {
            // 无用户上下文 → SELF 且条件恒假，防越权
            return dataScopeResolver.resolveForUser(null, null, null, null, Collections.emptyList());
        }
        return dataScopeResolver.resolveForUser(scope.getUsername(), scope.getUserId(),
                scope.getEmployeeId(), scope.getDeptId(), scope.getRoles());
    }

    private static LocalDate parseDate(Map<String, Object> params, String key) {
        Object v = params == null ? null : params.get(key);
        if (v == null || String.valueOf(v).isBlank()) return null;
        return LocalDate.parse(String.valueOf(v).trim(), DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRecords(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : Collections.emptyList();
    }

    /**
     * 报表当前用户的不可见字段集合：页面键取报表配置主表（与明细报表页面同一口径）。
     * 小助手跑在线程池，优先按 scope 显式解析；没有 scope 时回退请求上下文。
     */
    private Set<String> hiddenKeysOf(BillDetailReportEngine eng, AiDataScope scope) {
        String pageKey = null;
        try {
            BillDetailReportConfig cfg = eng.getConfig();
            if (cfg != null) pageKey = cfg.mainTable;
        } catch (Exception e) {
            log.warn("[AI固定报表] 读取报表主表失败，字段权限按不限制处理: {}", e.getMessage());
        }
        if (scope != null && scope.getUserId() != null) {
            return fieldPermissionOutputFilter.hiddenKeysForUser(scope.getUserId(), scope.isAdmin(), pageKey);
        }
        return fieldPermissionOutputFilter.hiddenKeys(pageKey);
    }

    /** 统计卡/合计行同样剔字段；统计卡键（totalNetAmount 等）在来源字段不可见时一并去掉 */
    private Object filterMap(Set<String> hidden, Object v) {
        if (hidden.isEmpty()) return v;
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> {
                if (k != null) out.put(String.valueOf(k), val);
            });
            out.keySet().removeIf(hidden::contains);
            if (!visibleStat(hidden, "orderCount")) out.remove("orderCount");
            if (!visibleStat(hidden, "quantity")) out.remove("totalQuantity");
            if (!visibleStat(hidden, "netAmount")) out.remove("totalNetAmount");
            return out;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(filterMap(hidden, item));
            return out;
        }
        return v;
    }

    /** 统计文案：只描述仍然可见的统计项（隐藏了金额就不提金额） */
    private static String statText(Set<String> hidden, Object statsObj) {
        if (!(statsObj instanceof Map<?, ?> stats)) return "";
        List<String> parts = new ArrayList<>();
        if (visibleStat(hidden, "orderCount") && stats.containsKey("orderCount")) {
            parts.add("单据 " + stats.get("orderCount") + " 张");
        }
        if (visibleStat(hidden, "quantity") && stats.containsKey("totalQuantity")) {
            parts.add("数量合计 " + stats.get("totalQuantity"));
        }
        if (visibleStat(hidden, "netAmount") && stats.containsKey("totalNetAmount")) {
            parts.add("价税合计 " + stats.get("totalNetAmount"));
        }
        return parts.isEmpty() ? "" : "统计：" + String.join("，", parts) + "；";
    }

    private static boolean visibleStat(Set<String> hidden, String sourceKey) {
        return !hidden.contains(sourceKey) && !hidden.contains(toSnake(sourceKey));
    }

    private static String toSnake(String key) {
        StringBuilder sb = new StringBuilder();
        for (char ch : key.toCharArray()) {
            if (Character.isUpperCase(ch)) sb.append('_').append(Character.toLowerCase(ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static int intVal(Map<String, Object> params, String key, int def) {
        Object v = params == null ? null : params.get(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
