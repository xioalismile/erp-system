package com.erp.report.controller;

import com.erp.report.detail.BillDetailReportConfig;
import com.erp.report.detail.BillDetailReportEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表目录桥（2026-11-22 立项三期新增）。
 *
 * <p><b>为什么需要它</b>：单据明细报表族 38 个控制器的引擎与中文名都是 {@code protected}
 * （报表中文名来自 {@code exportFilePrefix()}）。AI 固定报表查询工具在 {@code com.erp.ai} 包，
 * 直接访问会编译失败；若把这 38 个控制器逐个改成 public，等于为了一处读取动全部报表类。
 * 本类放在<b>同一个包</b>（{@code com.erp.report.controller}），作为唯一公开出口，
 * 报表控制器一行不改。</p>
 *
 * <p>目录口径（自动登记，新报表零改动）：</p>
 * <ul>
 *   <li>报表中文名 = 导出文件名前缀（既有 38 个控制器都已实现）；</li>
 *   <li>菜单路由路径 = {@code /api/report/{模块}/{后缀}} → {@code /{模块}/report/{后缀}}
 *       （与 {@code sys_role_permission.menu_id} 存路由路径的授权口径一致，如
 *       {@code /purchase/report/purchase-return-detail}）；</li>
 *   <li>单据日期筛选键 = 报表配置的 {@code docDateFilterKey}（决定能不能按日期过滤）。</li>
 * </ul>
 */
@Component
public class BillDetailReportCatalog {

    private final ApplicationContext applicationContext;
    private final JdbcTemplate jdbcTemplate;

    /** 缓存的目录（首次构建后复用；报表控制器在运行期不会增减） */
    private volatile List<Entry> cache;

    @Autowired
    public BillDetailReportCatalog(ApplicationContext applicationContext, JdbcTemplate jdbcTemplate) {
        this.applicationContext = applicationContext;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动自检（守则 2.7 要求首次启动必须留可排查日志）：
     * 启动即打印"小楷能查哪些固定报表"，目录为 0 时明确告警，避免"工具在、永远查不到报表"的静默失败。
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUp() {
        List<Entry> list = entries();
        if (list.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(BillDetailReportCatalog.class)
                    .warn("[报表目录] 启动自检：未发现任何固定报表控制器，AI 固定报表查询将不可用");
        }
    }

    /** 一张报表的目录条目（只读） */
    public static class Entry {
        private final String title;
        private final String route;
        private final String dateKey;
        private final String apiBase;
        private final BillDetailReportEngine engine;

        Entry(String title, String route, String dateKey, String apiBase, BillDetailReportEngine engine) {
            this.title = title;
            this.route = route;
            this.dateKey = dateKey;
            this.apiBase = apiBase;
            this.engine = engine;
        }

        /** 报表中文名（=导出文件名前缀，如「销售订单明细报表」） */
        public String getTitle() {
            return title;
        }

        /** 报表菜单路由路径（权限判定用，如 /sale/report/sale-order-detail） */
        public String getRoute() {
            return route;
        }

        /** 报表接口基础路径（如 /api/report/sale/order-detail）；字段权限按它自动匹配 */
        public String getApiBase() {
            return apiBase;
        }

        /** 单据日期筛选键（f_&lt;key&gt;From/To；null = 该报表无日期筛选） */
        public String getDateKey() {
            return dateKey;
        }

        /** 报表查询引擎（与报表页面同一条查询链路） */
        public BillDetailReportEngine getEngine() {
            return engine;
        }
    }

    /** 目录（懒构建 + 缓存；空结果不缓存，避免启动早期扫描不到控制器后永远为空） */
    public List<Entry> entries() {
        List<Entry> local = cache;
        if (local != null && !local.isEmpty()) {
            return local;
        }
        synchronized (this) {
            if (cache != null && !cache.isEmpty()) {
                return cache;
            }
            cache = build();
            return cache;
        }
    }

    /** 按中文名精确查找（找不到返回 null） */
    public Entry find(String title) {
        if (title == null) {
            return null;
        }
        for (Entry e : entries()) {
            if (e.getTitle().equals(title.trim())) {
                return e;
            }
        }
        return null;
    }

    /** 按中文名模糊查找（包含匹配，用于"名字说不太全"的场景） */
    public List<String> findLike(String title) {
        List<String> out = new ArrayList<>();
        if (title == null || title.isBlank()) {
            return out;
        }
        for (Entry e : entries()) {
            if (e.getTitle().contains(title.trim())) {
                out.add(e.getTitle());
            }
        }
        return out;
    }

    /** 扫描同包全部报表控制器，构建目录（新报表按既有规范建控制器即自动进来） */
    private List<Entry> build() {
        List<Entry> out = new ArrayList<>();
        // 菜单真实路由优先：接口路径推出来的路由与 sys_menu 未必同形
        // （如 /api/report/sale/order-detail → /sale/report/sale-order-detail，仅靠字符串拼会错位，
        //  导致 AI 固定报表对非管理员永远判"无权限"）。按报表中文名 = 菜单名取真实路由。
        Map<String, String> menuRouteByTitle = loadMenuRoutes();
        Map<String, AbstractBillDetailReportController> beans =
                applicationContext.getBeansOfType(AbstractBillDetailReportController.class);
        for (AbstractBillDetailReportController c : beans.values()) {
            try {
                String title = c.exportFilePrefix();
                BillDetailReportEngine engine = c.engine();
                if (title == null || title.isBlank() || engine == null) {
                    continue;
                }
                String base = null;
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(c.getClass(), RequestMapping.class);
                if (rm != null && rm.value().length > 0) {
                    base = rm.value()[0];
                }
                String route = menuRouteByTitle.get(title);
                if (route == null || route.isBlank()) {
                    route = toMenuRoute(base);
                }
                BillDetailReportConfig cfg = engine.getConfig();
                String dateKey = cfg != null ? cfg.docDateFilterKey : null;
                out.add(new Entry(title, route, dateKey, base, engine));
            } catch (Exception e) {
                // 单个控制器登记失败不影响其余报表（与既有自检"缺列剔除不阻断启动"同一容错口径）
                org.slf4j.LoggerFactory.getLogger(BillDetailReportCatalog.class)
                        .warn("[报表目录] 控制器 {} 登记失败: {}",
                                c.getClass().getSimpleName(), e.getMessage());
            }
        }
        out.sort((a, b) -> a.getTitle().compareTo(b.getTitle()));
        // 供排障：目录条数与路由清单（业务侧表现为"小楷能查哪些固定报表"）
        Map<String, String> routeMap = new LinkedHashMap<>();
        for (Entry e : out) {
            routeMap.put(e.getTitle(), e.getRoute());
        }
        org.slf4j.LoggerFactory.getLogger(BillDetailReportCatalog.class)
                .info("[报表目录] 自动登记 {} 张固定报表（来自同包控制器扫描）: {}", out.size(), routeMap.keySet());
        return out;
    }

    /** 报表中文名 → 菜单真实路由（sys_menu.menu_name = 报表中文名）；取不到返回空表，回退路径推导 */
    private Map<String, String> loadMenuRoutes() {
        Map<String, String> map = new HashMap<>();
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                    "SELECT menu_name, route_path FROM sys_menu WHERE route_path LIKE '%/report/%'")) {
                Object name = row.get("menu_name");
                Object route = row.get("route_path");
                if (name != null && route != null) {
                    map.putIfAbsent(String.valueOf(name), String.valueOf(route));
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BillDetailReportCatalog.class)
                    .warn("[报表目录] 读取菜单路由失败，回退接口路径推导: {}", e.getMessage());
        }
        return map;
    }

    /** /api/report/{模块}/{后缀} → /{模块}/report/{后缀}（与菜单路由/授权路径同形） */
    private static String toMenuRoute(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        String rest = base.startsWith("/api/report/") ? base.substring("/api/report/".length()) : base;
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return "/report/" + rest;
        }
        return "/" + rest.substring(0, slash) + "/report/" + rest.substring(slash + 1);
    }
}
