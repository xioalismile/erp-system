package com.erp.ai.bill;

import com.erp.ai.dto.AiDataScope;
import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单据类工具（bill_action / bill_pushdown / bill_action_list）的**唯一**权限判定适配点。
 *
 * <p>设计动机（立项登记表 5.0/5.1）：
 * <ul>
 *   <li>界面当前授权模型是"菜单级"——能打开该单据页面即可操作，按钮权限码虽在
 *       {@code sys_role_permission} 中存了（{@code permission_type='按钮'}），但当前无人消费（死配置）；</li>
 *   <li>若小助手改用硬编码模块前缀（如 {@code sale:}），将来按钮权限接上后会**越权**
 *       （界面没有的按钮，AI 却能执行）。</li>
 * </ul>
 * 因此改为"单一适配点 + 模式开关"：默认 {@code menu} 与界面口径 1:1；将来把
 * {@code ai.bill.permission.mode} 切成 {@code action} 即自动与按钮权限同步变严，
 * 不改一行业务代码。动作 → 按钮码的映射只写在本类的静态表里。
 *
 * <p>放行优先级（两模式共有）：权限码含 {@code '*'} 或管理员角色 → 直接放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillActionPermissionResolver {

    /** sys_config 开关：单据动作权限判定模式（menu=按菜单权限；action=按角色按钮权限） */
    private static final String CONFIG_KEY_MODE = "ai.bill.permission.mode";
    private static final String MODE_MENU = "menu";
    private static final String MODE_ACTION = "action";

    private final SysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;
    private final BillActionRegistry billActionRegistry;

    /**
     * billType → 菜单权限码（必须与数据库 {@code sys_menu.permission_code} 完全一致）。
     *
     * <p>🔴 事故记录（2026-09-14，勿再凭猜测填写）：
     * 本表原来填的是 {@code sale:order:view} / {@code purchase:order:view} 这类"xxx:yyy:view"格式，
     * 而数据库真实权限码是 **{@code sale:orders} / {@code purchase:orders}** 这类"模块:资源"格式
     * （经 {@code SELECT permission_code FROM sys_menu} 实测：105 条码中**没有一条**是 {@code :view} 结尾；
     * {@code SELECT COUNT(*) FROM sys_menu WHERE permission_code='sale:order:view'} = 0）。
     * 导致除管理员（{@code '*'}/{@code is_admin}）外，**所有正常账号**调用 AI 单据动作**一律被拒**，
     * 现象是"设置中心明明给了全部模块权限，小助手却说无权限"。
     *
     * <p>权限码来源链路（务必按此对齐）：{@code AuthInterceptor.loadUserPermissions()} 用
     * {@code sys_role_permission.menu_id = sys_menu.route_path} 关联后取 {@code sys_menu.permission_code}
     * 注入 request attribute → {@link AiDataScope#getPermissions()}。所以这里的码必须等于 permission_code。
     *
     * <p>本表各条已尽量用 SQL 与 {@code sys_menu.permission_code} 逐条校验（菜单名与 billType 语义对应）；
     * 2026-09-28 批次3 新增的 6 条已逐条查库核实（见交付回报），另有"销售费用单"因库中无独立菜单，
     * 改登记在 {@link #MODULE_CODE_ONLY} 的模块码兜底表。
     * **日后新增单据类型，必须先查库确认 permission_code，禁止臆造。**
     */
    private static final Map<String, String> MENU_CODE = Map.ofEntries(
            Map.entry("SALE_QUOTE", "sale:quotes"),
            Map.entry("SALE_ORDER", "sale:orders"),
            Map.entry("SALE_CONTRACT", "sale:contracts"),
            Map.entry("SALE_DELIVERY", "sale:deliveries"),
            Map.entry("SALE_RETURN", "sale:returns"),
            Map.entry("SALE_INVOICE", "sale:invoices"),
            Map.entry("PURCHASE_REQUEST", "purchase:requests"),
            Map.entry("PURCHASE_ORDER", "purchase:orders"),
            Map.entry("PURCHASE_CONTRACT", "purchase:contracts"),
            Map.entry("PURCHASE_INBOUND", "purchase:inbounds"),
            Map.entry("PURCHASE_RETURN", "purchase:returns"),
            Map.entry("PURCHASE_INVOICE", "purchase:invoices"),
            Map.entry("PRODUCTION_ORDER", "production:workorders"),
            Map.entry("PRODUCTION_RECEIPT", "production:receipts"),
            Map.entry("MATERIAL_REQUISITION", "inventory:material-requisitions"),
            Map.entry("STOCK_TRANSFER", "inventory:stock-transfers"),
            Map.entry("STOCK_CHECK", "inventory:stock-checks"),
            Map.entry("OTHER_RECEIPT", "inventory:other-receipts"),
            Map.entry("OTHER_ISSUE", "inventory:other-issues"),
            Map.entry("SUBCONTRACT_ORDER", "subcontract:orders"),
            Map.entry("SUBCONTRACT_DELIVERY", "subcontract:picks"),
            Map.entry("SUBCONTRACT_RECEIPT", "subcontract:inbounds"),
            Map.entry("PAYMENT_RECEIPT", "finance:receipt"),
            Map.entry("PAYMENT_VOUCHER", "finance:payment"),
            Map.entry("PURCHASE_EXPENSE", "finance:operation-expense"),
            Map.entry("PURCHASE_PRE_PAYMENT", "finance:purchase-pre-payments"),
            Map.entry("BOM", "masterdata:bom"),
            // 2026-09-20 补登（与前端 utils/billPermCode.js 同源）
            Map.entry("OPERATION_REPORT", "production:opreport"),
            Map.entry("MRP_PLAN", "production:mrp"),
            // 2026-09-28 修正（已对库核实）：库里真实码是复数 subcontract:returns / subcontract:supplements，
            // 前端 委外退料单/补料单 列表页用的也是复数；原先单数写法会让普通账号一律被拒
            Map.entry("SUBCONTRACT_RETURN", "subcontract:returns"),
            Map.entry("SUBCONTRACT_SUPPLEMENT", "subcontract:supplements"),
            // 2026-09-28 批次3 补登（逐条 SELECT permission_code FROM sys_menu 核实，见交付回报）
            Map.entry("SALE_PRE_RECEIPT", "finance:sale-pre-receipts"),
            Map.entry("OPERATION_EXPENSE", "finance:operation-expense"),
            Map.entry("COST_CALCULATION", "finance:cost-calculation"),
            Map.entry("WAGE_SETTLEMENT", "finance:wage-settlement"),
            Map.entry("SALE_STATEMENT", "sale:report:customer-statement"),
            Map.entry("PURCHASE_STATEMENT", "purchase:report:supplier-statement"),
            // 2026-11-18 补登：销售换货单（sys_menu.permission_code = sale:exchanges，见 SchemaUpgradeEnsurer 菜单初始化）
            Map.entry("SALE_EXCHANGE", "sale:exchanges"),
            // 2026-11-18 补登：售后维修单（sys_menu.permission_code = sale:repairs）
            Map.entry("SALE_REPAIR", "sale:repairs"),
            // 2026-11-22 二期补登（已连库核实 8cloud erp_db sys_menu：生产退料单=production:returns、生产补料单=production:supplements）
            Map.entry("PRODUCTION_RETURN", "production:returns"),
            Map.entry("PRODUCTION_SUPPLEMENT", "production:supplements")
    );

    /**
     * 无菜单权限码的 billType → 仅登记的模块码。
     * 说明：sys_menu 未收录打样单/免费样品单菜单，故只能登记模块码（详见交付回报）；
     * 2026-09-28 批次3：销售费用单在 sys_menu 中同样没有独立菜单（库中仅有 finance:* 系列码），
     * 故按模块码 finance 兜底（menu 模式下命中任一 finance: 权限即放行）。
     */
    private static final Map<String, String> MODULE_CODE_ONLY = Map.of(
            "SAMPLE_ORDER", "sample",
            "FREE_SAMPLE", "sample",
            "SALE_EXPENSE", "finance"
    );

    /**
     * 判定单个单据动作的权限。
     *
     * @param billType 单据类型
     * @param action   动作英文名（可为 null：只读清单工具用，走菜单判定）
     * @param scope    数据权限上下文
     * @return null=允许；非 null=拒绝原因
     */
    public String check(String billType, String action, AiDataScope scope) {
        if (scope == null || scope.getUserId() == null) {
            return "无法获取用户权限上下文，已拒绝执行";
        }
        if (scope.hasPermission("*") || scope.isAdmin()) {
            log.info("[AI单据动作] 权限判定 mode={} billType={} action={} → 放行（{}）",
                    mode(), billType, action, scope.hasPermission("*") ? "通配符*" : "管理员角色");
            return null;
        }
        String menuCode = menuCode(billType);
        String moduleCode = moduleCode(billType);

        if (MODE_ACTION.equals(mode())) {
            return checkByAction(billType, action, scope, menuCode, moduleCode);
        }
        return checkByMenu(billType, action, scope, menuCode, moduleCode);
    }

    /**
     * 只读清单工具用：只要用户对**任一**传入 billType 有权限即放行。
     *
     * @return null=允许；非 null=拒绝原因
     */
    public String checkList(AiDataScope scope, Collection<String> billTypes) {
        if (scope == null || scope.getUserId() == null) {
            return "无法获取用户权限上下文，已拒绝执行";
        }
        if (scope.hasPermission("*") || scope.isAdmin()) {
            return null;
        }
        List<String> types = billTypes == null ? Collections.emptyList() : new ArrayList<>(billTypes);
        if (types.isEmpty()) {
            return "无权限：无可访问的单据类型";
        }
        for (String bt : types) {
            if (check(bt, null, scope) == null) {
                return null;
            }
        }
        String names = types.stream()
                .map(t -> "「" + billActionRegistry.label(t) + "」")
                .distinct()
                .limit(8)
                .reduce((a, b) -> a + "、" + b).orElse("");
        return "无权限：需要以下任一单据的菜单权限：" + names
                + "，请联系管理员在 设置中心 → 角色权限设置 中授权";
    }

    // ==================================================================
    // menu 模式
    // ==================================================================

    private String checkByMenu(String billType, String action, AiDataScope scope,
                               String menuCode, String moduleCode) {
        if (menuCode != null && scope.hasPermission(menuCode)) {
            log.info("[AI单据动作] 权限判定 mode=menu billType={} action={} → 放行（菜单码 {}）",
                    billType, action, menuCode);
            return null;
        }
        if (moduleCode != null && scope.hasPermission(moduleCode)) {
            log.info("[AI单据动作] 权限判定 mode=menu billType={} action={} → 放行（模块码 {}）",
                    billType, action, moduleCode);
            return null;
        }
        // 兜底：该单据在 sys_menu 中没有独立权限码（如打样单/免费样品单），
        // 用「模块前缀」判定，避免这些单据在 menu 模式下被误判成"仅管理员可用"。
        if (menuCode == null && moduleCode != null && !moduleCode.isBlank()
                && scope.hasPermissionPrefix(moduleCode + ":")) {
            log.info("[AI单据动作] 权限判定 mode=menu billType={} action={} → 放行（模块前缀 {}:，该单据无独立菜单码）",
                    billType, action, moduleCode);
            return null;
        }
        log.info("[AI单据动作] 权限判定 mode=menu billType={} action={} → 拒绝（无菜单码/模块码）",
                billType, action);
        return denyMessage(billType);
    }

    // ==================================================================
    // action 模式
    // ==================================================================

    private String checkByAction(String billType, String action, AiDataScope scope,
                                 String menuCode, String moduleCode) {
        // 防"升级后全员被拒"兜底：按钮权限当前是死配置，任何角色都还没有按钮码；
        // 若该用户一条按钮权限记录都没有，整体回退 menu 模式，避免"升级即事故"。
        List<String> buttonCodes = loadButtonCodes(scope.getUserId());
        if (buttonCodes.isEmpty()) {
            log.warn("[AI单据动作] 用户无任何按钮权限配置，已回退菜单权限模式 billType={} action={}",
                    billType, action);
            return checkByMenu(billType, action, scope, menuCode, moduleCode);
        }

        String suffix = action == null ? null : BillActionRegistry.ACTION_BUTTON_SUFFIX.get(action);
        if (suffix == null) {
            log.warn("[AI单据动作] 该动作无对应按钮码，已回退菜单码 billType={} action={}", billType, action);
            return checkByMenu(billType, action, scope, menuCode, moduleCode);
        }
        if (moduleCode != null) {
            String code = moduleCode + ":" + suffix;
            if (buttonCodes.contains(code)) {
                log.info("[AI单据动作] 权限判定 mode=action billType={} action={} → 放行（按钮码 {}）",
                        billType, action, code);
                return null;
            }
        }
        log.info("[AI单据动作] 权限判定 mode=action billType={} action={} → 拒绝（缺少按钮码 {}）",
                billType, action, moduleCode == null ? "?" : moduleCode + ":" + suffix);
        return denyMessage(billType);
    }

    /** 查用户角色拥有的按钮权限码（与 AuthController 同源做法） */
    private List<String> loadButtonCodes(Long userId) {
        try {
            String sql = "SELECT DISTINCT rp.menu_id FROM sys_role_permission rp "
                    + "JOIN sys_user_role ur ON ur.role_id = rp.role_id "
                    + "WHERE ur.user_id = ? AND rp.permission_type = '按钮'";
            List<String> codes = jdbcTemplate.queryForList(sql, String.class, userId);
            return codes == null ? Collections.emptyList() : codes;
        } catch (Exception e) {
            log.warn("[AI单据动作] 查询按钮权限失败，本次回退菜单权限模式 userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private String mode() {
        String v = sysConfigService.getValue(CONFIG_KEY_MODE, MODE_MENU);
        return MODE_ACTION.equalsIgnoreCase(v == null ? "" : v.trim()) ? MODE_ACTION : MODE_MENU;
    }

    private String menuCode(String billType) {
        return billType == null ? null : MENU_CODE.get(billType);
    }

    /**
     * 模块码 = 菜单码去掉最后一段（如 sale:orders → sale、inventory:material-requisitions → inventory）；
     * 无菜单码时取手工登记值（如 sample）。
     *
     * <p>用途：① menu 模式下对"该单据无独立菜单码"的情况做前缀兜底判定；
     * ② action 模式下拼按钮码。**注意**：action 模式默认关闭，且其按钮码真实格式是
     * {@code 模块:资源:动作}（如 {@code sale:orders:add}），与本方法产出的两段码并不一致 ——
     * 启用 action 模式前必须先修正该拼接，详见交付说明。
     */
    private String moduleCode(String billType) {
        if (billType == null) return null;
        String code = MENU_CODE.get(billType);
        if (code != null) {
            int idx = code.lastIndexOf(':');
            return idx > 0 ? code.substring(0, idx) : code;
        }
        return MODULE_CODE_ONLY.get(billType);
    }

    private String denyMessage(String billType) {
        String label = billActionRegistry.label(billType);
        return "无权限：需要「" + label + "」菜单权限，请联系管理员在 设置中心 → 角色权限设置 中授权";
    }
}
