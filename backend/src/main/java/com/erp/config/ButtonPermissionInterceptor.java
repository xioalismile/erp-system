package com.erp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按钮权限「接口级」拦截器（2026-10-12）。
 *
 * <p>背景：按钮权限此前只在前端做"按钮显隐"（v-permission），删除/提交/审核等接口本身不校验，
 * 只要按钮没被藏住（旧会话、绕过界面直调），点了就是真删。用户 2026-10-12 实报：
 * 给销售员关掉「销售出库单-删除」并重新登录后按钮仍可用。本拦截器把按钮权限升级为
 * 「前端显隐 + 后端拦截」双保险：关了就是不能用，与界面是否刷新无关。
 *
 * <p>判定口径与前端 v-permission 完全同源（三规则，2026-09-20 定稿）：
 * <ol>
 *   <li>权限含通配符 '*'，或角色为管理级（SUPER_ADMIN/ADMIN/ADMIN_* 版本管理员）→ 放行；</li>
 *   <li>该「模块:对象」下存在显式按钮配置（权限里有同前缀码）→ 严格模式：本动作码不在其中即拒绝；</li>
 *   <li>该对象下没有任何显式按钮配置 → 放行（默认口径：能看菜单 = 按钮全有）。</li>
 * </ol>
 * 因此历史角色（从未配过按钮权限）行为完全不变，不会"升级后全站按钮失效"。
 *
 * <p>覆盖范围：仅"单据动作类"接口（删除/提交/审核/驳回/重置草稿/作废/反作废/关闭/结案/反结案/下推）。
 * 列表查询、保存、导入导出不在本期（其显隐已受控）。未登记的 URL 前缀与未登记的动作段一律放行，
 * 与前端"无按钮码 = 不判定"口径一致；日后新增单据/动作，按「三端同源」约定在注册表补登记。
 *
 * <p>注册表必须与以下两处同源（改一处必须同步另两处）：
 * 前端 utils/billPermCode.js + utils/pagePerm.js 的菜单/动作词表；设置中心角色按钮权限的按钮码。
 */
@Slf4j
@Component
public class ButtonPermissionInterceptor implements HandlerInterceptor {

    /** URL 前缀（去掉 /api 后）→ 按钮权限对象码。前缀取自各单据 Controller 的 @RequestMapping 实测值。 */
    private static final Map<String, String> OBJECT_BY_PREFIX = new LinkedHashMap<>();
    /** URL 前缀 → 单据中文名（拒绝提示用） */
    private static final Map<String, String> LABEL_BY_PREFIX = new LinkedHashMap<>();
    /** 动作路径段（URL 原样，连字符）→ [按钮动作后缀, 动作中文名] */
    private static final Map<String, String[]> ACTION_BY_SEGMENT = new LinkedHashMap<>();

    private static void register(String prefix, String objectCode, String label) {
        OBJECT_BY_PREFIX.put(prefix, objectCode);
        LABEL_BY_PREFIX.put(prefix, label);
    }

    private static void action(String segment, String suffix, String label) {
        ACTION_BY_SEGMENT.put(segment, new String[]{suffix, label});
    }

    // ==================== 设置中心（2026-10-13 扩展） ====================
    /**
     * 设置中心注册表：URL 前缀（去掉 /api）→ [对象码, 页面中文名]。
     * 与单据注册表的关键差异——判定口径为「默认拒绝」（2026-10-08 用户拍板：
     * 必须在【角色权限设置→按钮权限】显式勾选该子页动作，未配置一律拒绝；管理员/通配除外）。
     * 用户 2026-10-13 口径：「核心是可以所有人都支持配置，关键是我有没有给权限」——
     * 勾了就能用（配合 30 秒权限轮询免重登），取消即按钮消失且接口 403。
     */
    private static final Map<String, String[]> SETTINGS_BY_PREFIX = new LinkedHashMap<>();
    /** 设置类特殊动作段（URL 原样）→ [动作码, 动作中文名]；未命中的写方法按"根保存"处理 */
    private static final Map<String, String[]> SETTINGS_ACTION_BY_SEGMENT = new LinkedHashMap<>();
    /**
     * 豁免前缀：虽落在设置前缀下、但属"业务共用接口"，一律放行——
     * 否则没勾设置按钮码的普通角色会在业务操作里被误拦（全站级事故）。
     */
    private static final List<String> SETTINGS_EXEMPT_PREFIXES = new java.util.ArrayList<>();
    /** 豁免动作段：设置页里的"单据流转"动作（提交/审核/驳回/作废等）本期不纳入设置拦截 */
    private static final List<String> SETTINGS_EXEMPT_SEGMENTS = new java.util.ArrayList<>();

    private static void exemptSetting(String prefix) {
        SETTINGS_EXEMPT_PREFIXES.add(prefix);
    }

    private static void exemptSettingSegment(String segment) {
        SETTINGS_EXEMPT_SEGMENTS.add(segment);
    }

    private static void registerSetting(String prefix, String objectCode, String label) {
        registerSetting(prefix, objectCode, label, "save");
    }

    private static void registerSetting(String prefix, String objectCode, String label, String rootAction) {
        SETTINGS_BY_PREFIX.put(prefix, new String[]{objectCode, label, rootAction});
    }

    private static void settingAction(String segment, String suffix, String label) {
        SETTINGS_ACTION_BY_SEGMENT.put(segment, new String[]{suffix, label});
    }

    static {
        // ===== 设置中心各子页（URL 前缀取自各控制器 @RequestMapping / 前端 api 封装实测） =====
        registerSetting("/system/role", "system:role-perm", "角色权限设置");
        registerSetting("/role-field-permission", "system:role-perm", "角色权限设置");
        registerSetting("/system/maintain", "system:data-maintain", "数据维护");
        registerSetting("/system/i18n", "system:i18n-manager", "语言管理");
        registerSetting("/serial-no-config", "system:serial-no", "自动编号设置");
        registerSetting("/print-template", "system:print-setting", "打印模板设置");
        registerSetting("/custom-field/definition", "system:custom-field", "自定义字段设置");
        registerSetting("/custom-field/refresh", "system:custom-field", "自定义字段设置", "refreshCache");
        registerSetting("/system/field-label", "system:field-name", "字段名称设置");
        registerSetting("/system/commission-rate", "system:commission-rate", "提成率配置");
        registerSetting("/inventory-warning/rule", "system:inventory-warning", "库存预警设置");
        registerSetting("/approval/flows", "system:approval", "审批设置");
        registerSetting("/price/sale-strategy", "system:sale-price", "销售价格策略");
        registerSetting("/price/purchase-strategy", "system:purchase-price", "采购价格策略");
        // 用户管理：页面入口已并入设置中心体系，接口保留独立对象码兜底（无配置卡片时普通角色默认拒绝）
        registerSetting("/system/user", "system:user", "用户管理");

        // ===== 设置类特殊动作段 =====
        settingAction("permissions", "apply", "分配权限");
        settingAction("button-permissions", "apply", "分配权限");
        settingAction("copy", "save", "复制");
        settingAction("default", "setDefault", "设为默认");
        settingAction("refresh", "refreshCache", "刷新缓存");
        settingAction("reset", "resetDefault", "恢复默认");
        settingAction("batch", "add", "批量新增");
        settingAction("import", "import", "导入");
        settingAction("toggle-status", "enable", "启用/停用");
        settingAction("clear-all", "clear", "清空");
        settingAction("clear-business", "clear", "清空");
        settingAction("save", "save", "保存");
        settingAction("reset-password", "resetPassword", "重置密码");
        settingAction("roles", "assignRole", "分配角色");

        // ===== 业务共用接口豁免（普通角色日常业务会调到，绝不能按设置按钮码拦） =====
        // 单据取号：新建任何单据都会调，没勾"自动编号设置"按钮码的角色必须照常取号
        exemptSetting("/serial-no-config/generate");
        exemptSetting("/serial-no-config/batch-generate");
        exemptSetting("/serial-no-config/by-doc-type");
        exemptSetting("/serial-no-config/is-auto-generate");
        // 自定义字段定义批量加载：单据编辑页打开时全站调用
        exemptSetting("/custom-field/definition/batch");
        // 打印模板设计态预览：只做只读渲染、不落库（2026-11-24，预览与打印一致改造）
        exemptSetting("/print-template/preview");

        // ===== 设置页里的"单据流转"动作豁免（本期不纳入设置拦截，维持既有行为） =====
        // 价格策略等设置子页的提交/审核/驳回/重置草稿/作废/反作废仍走单据动作口径
        exemptSettingSegment("submit");
        exemptSettingSegment("approve");
        exemptSettingSegment("audit");
        exemptSettingSegment("reject");
        exemptSettingSegment("reverse-approve");
        exemptSettingSegment("void");
        exemptSettingSegment("unvoid");
    }

    static {
        // ===== 销售 =====
        register("/sale/quotes", "sale:quotes", "销售报价单");
        register("/sale/orders", "sale:orders", "销售订单");
        register("/sale/contracts", "sale:contracts", "销售合同");
        register("/sale/deliveries", "sale:deliveries", "销售出库单");
        register("/sale/returns", "sale:returns", "销售退货单");
        register("/sale/invoices", "sale:invoices", "销售发票");
        // ===== 采购 =====
        register("/purchase/requests", "purchase:requests", "采购申请单");
        register("/purchase/orders", "purchase:orders", "采购订单");
        register("/purchase/contract", "purchase:contracts", "采购合同");
        register("/purchase/inbounds", "purchase:inbounds", "采购入库单");
        register("/purchase/returns", "purchase:returns", "采购退货单");
        register("/purchase/invoices", "purchase:invoices", "采购发票");
        // ===== 库存 =====
        register("/inventory/material-requisitions", "inventory:material-requisitions", "领料单");
        register("/inventory/stock-transfers", "inventory:stock-transfers", "调拨单");
        register("/inventory/stock-checks", "inventory:stock-checks", "盘点单");
        register("/inventory/other-receipts", "inventory:other-receipts", "其它入库单");
        register("/inventory/other-issues", "inventory:other-issues", "其它出库单");
        // ===== 生产 / 委外 / 打样相关（有按钮权限对象码的单据） =====
        register("/production/workorders", "production:workorders", "生产工单");
        register("/production/receipts", "production:receipts", "生产入库单");
        register("/production/mrp-plan", "production:mrp", "MRP计划");
        register("/production/operation-report", "production:opreport", "工序汇报单");
        register("/subcontract/orders", "subcontract:orders", "委外加工单");
        register("/subcontract/picks", "subcontract:picks", "委外发料单");
        register("/subcontract/inbounds", "subcontract:inbounds", "委外入库单");
        register("/subcontract/returns", "subcontract:returns", "委外退料单");
        register("/subcontract/supplements", "subcontract:supplements", "委外补料单");
        // ===== 财务 =====
        register("/finance/receipts", "finance:receipt", "收款单");
        register("/finance/vouchers", "finance:payment", "付款单");
        register("/finance/operation-expense", "finance:operation-expense", "运营费用单");
        register("/purchase-expense", "finance:operation-expense", "采购费用单");
        register("/sale-pre-receipt", "finance:sale-pre-receipts", "销售预收款单");
        register("/purchase-pre-payment", "finance:purchase-pre-payments", "采购预付款单");
        register("/finance/wage-settlements", "finance:wage-settlement", "工序工资核算");
        register("/sale-statement", "sale:report:customer-statement", "客户对账单");
        register("/purchase-statement", "purchase:report:supplier-statement", "供应商对账单");
        // ===== 基础资料 =====
        register("/masterdata/bom", "masterdata:bom", "BOM");

        // ===== 动作段 → 按钮码后缀（与 BillActionRegistry.ACTION_BUTTON_SUFFIX / 前端 billPermCode 同源） =====
        action("submit", "submit", "提交");
        action("approve", "audit", "审核");
        action("reject", "reject", "驳回");
        action("reverse-approve", "reverseApprove", "重置草稿");
        action("void", "void", "作废");
        action("unvoid", "unvoid", "反作废");
        action("close", "close", "关闭");
        action("close-order", "closeOrder", "结案");
        action("reopen", "reopen", "反结案");
        // push-to-*（下推到各目标单）统一对应"下推"按钮码，见 resolveAction 的前缀判定
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String method = request.getMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) return true;
            String path = request.getRequestURI();
            if (path == null || !path.startsWith("/api/")) return true;
            String rel = path.substring(4); // 去掉 "/api"

            // 最长前缀匹配（防 /purchase/contract 误吞 /purchase/contracts 无此问题——前缀按段匹配）
            String matched = null;
            for (String p : OBJECT_BY_PREFIX.keySet()) {
                if (rel.equals(p) || rel.startsWith(p + "/")) {
                    if (matched == null || p.length() > matched.length()) matched = p;
                }
            }

            // ===== 设置中心判定（2026-10-13）：优先于单据注册表，口径=默认拒绝 =====
            String settingMatched = null;
            for (String p : SETTINGS_BY_PREFIX.keySet()) {
                if (rel.equals(p) || rel.startsWith(p + "/")) {
                    if (settingMatched == null || p.length() > settingMatched.length()) settingMatched = p;
                }
            }
            if (settingMatched != null) {
                // 设置前缀可能嵌套在单据前缀下（互不重叠，取更长命中者；实测两组前缀无交集）
                if (matched == null || settingMatched.length() >= matched.length()) {
                    return checkSettings(request, response, method, rel, settingMatched);
                }
            }

            if (matched == null) return true;

            String[] act = resolveAction(method, rel.substring(matched.length()));
            if (act == null) return true; // 非管辖动作（查询/保存/导入导出/未登记动作）→ 不拦

            String objectCode = OBJECT_BY_PREFIX.get(matched);
            String suffix = act[0];
            String actionLabel = act[1];

            @SuppressWarnings("unchecked")
            List<String> perms = (List<String>) request.getAttribute("currentPermissions");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) request.getAttribute("currentRoles");
            if (perms == null) return true; // 鉴权层未注入（理论不可达）→ 交回鉴权层处理

            if (allowed(request, perms, roles, objectCode, suffix)) return true;

            String billLabel = LABEL_BY_PREFIX.get(matched);
            String username = String.valueOf(request.getAttribute("currentUsername"));
            log.warn("[按钮权限拦截] 拒绝 {} {} ：user={}, objectCode={}, 缺少按钮码={}, 角色={}",
                    method, path, username, objectCode, objectCode + ":" + suffix, roles);
            writeDenied(response, billLabel, actionLabel);
            return false;
        } catch (Exception e) {
            // 判定异常宁可放行（不误拦全站业务），但必须留痕排查
            log.warn("[按钮权限拦截] 判定异常，放行: path={}, err={}", request.getRequestURI(), e.getMessage());
            return true;
        }
    }

    /**
     * 解析动作：DELETE /{id} → 删除；POST|PUT|GET /{id}/{动作段} → 查动作表。
     * 未登记的动作段返回 null（不拦，与前端"无按钮码不判定"同口径）。
     */
    private String[] resolveAction(String method, String rest) {
        if (rest == null || rest.isEmpty()) return null;
        String[] segs = rest.split("/");
        if ("DELETE".equalsIgnoreCase(method)) {
            // 形如 /{id}（一段）= 删除该单
            if (segs.length == 2 && !segs[1].isBlank()) return new String[]{"delete", "删除"};
            return null;
        }
        // 形如 /{id}/{动作段}
        if (segs.length == 3 && segs[1].matches("\\d+")) {
            String seg = segs[2];
            String[] hit = ACTION_BY_SEGMENT.get(seg);
            if (hit != null) return hit;
            if (seg.startsWith("push-to")) return new String[]{"push", "下推"};
        }
        return null;
    }

    // ==================== 设置中心判定（默认拒绝口径） ====================

    /**
     * 设置中心写接口判定。
     * 只拦写方法（POST/PUT/DELETE）；GET 一律放行（列表/回显不拦）。
     * 动作解析：先按动作段表（任一段命中即用），未命中时 POST/PUT → 根动作（默认 save）、DELETE → delete。
     * 判定：通配/管理级放行 → 显式勾选该「子页:动作」码放行 → 其余一律拒绝（默认拒绝，与
     * canWriteSysConfig 同口径；未配置的写按钮前端同步隐藏）。
     */
    private boolean checkSettings(HttpServletRequest request, HttpServletResponse response,
                                  String method, String rel, String prefix) {
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        if ("GET".equalsIgnoreCase(method)) return true; // 查询/回显不拦

        // 业务共用接口豁免（取号/字段定义批量加载等），绝不按设置按钮码拦
        for (String ex : SETTINGS_EXEMPT_PREFIXES) {
            if (rel.equals(ex) || rel.startsWith(ex + "/")) return true;
        }
        // 设置页里的"单据流转"动作豁免（提交/审核/驳回等维持既有行为）
        String rest = rel.substring(prefix.length());
        if (rest != null && !rest.isEmpty()) {
            for (String seg : rest.split("/")) {
                if (seg != null && SETTINGS_EXEMPT_SEGMENTS.contains(seg)) return true;
            }
        }

        String[] reg = SETTINGS_BY_PREFIX.get(prefix);
        String objectCode = reg[0];
        String pageLabel = reg[1];
        String rootAction = reg.length > 2 ? reg[2] : "save";

        String[] act = resolveSettingAction(method, rest, rootAction);
        if (act == null) return true; // 非管辖（未识别写动作——保守放行并留 WARN）

        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) request.getAttribute("currentPermissions");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("currentRoles");
        if (perms == null) return true; // 鉴权层未注入（理论不可达）

        if (allowedSettings(request, perms, roles, objectCode, act[0])) return true;

        String username = String.valueOf(request.getAttribute("currentUsername"));
        log.warn("[按钮权限拦截·设置中心] 拒绝 {} {}：user={}, objectCode={}, 缺少按钮码={}, 角色={}",
                method, request.getRequestURI(), username, objectCode, objectCode + ":" + act[0], roles);
        writeDenied(response, pageLabel, act[1]);
        return false;
    }

    /** 设置类动作解析：扫描 rest 的每一段查动作段表；未命中时 POST/PUT → 根动作、DELETE → delete。 */
    private String[] resolveSettingAction(String method, String rest, String rootAction) {
        if (rest != null && !rest.isEmpty()) {
            for (String seg : rest.split("/")) {
                if (seg == null || seg.isBlank()) continue;
                String[] hit = SETTINGS_ACTION_BY_SEGMENT.get(seg);
                if (hit != null) return hit;
            }
        }
        if ("DELETE".equalsIgnoreCase(method)) return new String[]{"delete", "删除"};
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if ("delete".equals(rootAction)) return new String[]{"delete", "删除"};
            return new String[]{rootAction, rootAction.equals("refreshCache") ? "刷新缓存" : "保存"};
        }
        return null;
    }

    /**
     * 设置中心判定：默认拒绝。
     * ① 通配 '*' / 管理级角色 / 租户管理员（is_admin=1，与数据权限直通规则同口径）→ 放行；
     * ② 显式持有该「子页:动作」码 → 放行；
     * ③ 其余一律拒绝（用户 2026-10-13 口径：「关键是我有没有给权限」——没给就是不能用）。
     */
    private boolean allowedSettings(HttpServletRequest request, List<String> perms, List<String> roles,
                                    String objectCode, String suffix) {
        if (perms.contains("*")) return true;
        if (Boolean.TRUE.equals(request.getAttribute("currentUserIsAdmin"))) return true;
        if (roles != null) {
            for (String r : roles) {
                if (r == null) continue;
                if ("SUPER_ADMIN".equals(r) || "ADMIN".equals(r) || r.startsWith("ADMIN_")) return true;
            }
        }
        return perms.contains(objectCode + ":" + suffix);
    }

    /** 判定：与前端 v-permission 三规则同口径（通配放行 / 显式配置严格模式 / 无配置默认放行）。租户管理员（is_admin=1）同管理级放行。 */
    private boolean allowed(HttpServletRequest request, List<String> perms, List<String> roles, String objectCode, String suffix) {
        if (perms.contains("*")) return true;
        if (Boolean.TRUE.equals(request.getAttribute("currentUserIsAdmin"))) return true;
        if (roles != null) {
            for (String r : roles) {
                if (r == null) continue;
                if ("SUPER_ADMIN".equals(r) || "ADMIN".equals(r) || r.startsWith("ADMIN_")) return true;
            }
        }
        String code = objectCode + ":" + suffix;
        if (perms.contains(code)) return true;
        boolean hasExplicitButtons = false;
        for (String p : perms) {
            if (p != null && p.startsWith(objectCode + ":")) { hasExplicitButtons = true; break; }
        }
        return !hasExplicitButtons;
    }

    /** 拒绝响应：403 + 中文提示（与既有鉴权失败响应同结构），前端可直接弹出。 */
    private void writeDenied(HttpServletResponse response, String billLabel, String actionLabel) {
        try {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            String msg = "无权限执行该操作：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「"
                    + billLabel + "」的「" + actionLabel + "」权限";
            response.getWriter().write("{\"code\":403,\"message\":\"" + msg + "\",\"data\":null}");
            response.getWriter().flush();
        } catch (Exception e) {
            log.warn("[按钮权限拦截] 写拒绝响应异常: {}", e.getMessage());
        }
    }
}
