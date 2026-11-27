package com.erp.ai.imports;

import com.erp.ai.dto.AiDataScope;
import com.erp.config.service.SysConfigService;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 导入工具（import_ / material_import_）的**唯一**权限判定适配点。
 *
 * <p>与 {@code BillActionPermissionResolver} 同口径：读同一个 mode 开关 {@code ai.bill.permission.mode}
 * （默认 {@code menu}），放行优先级一致 —— 通配符 {@code '*'}/管理员角色 → 实体菜单码 → 模块码 →
 * （该实体无独立菜单码时）模块前缀兜底。
 *
 * <p>为何不复用 BillActionPermissionResolver：它面向"单据类型（SALE_ORDER）"，导入工具面向
 * "实体名（saleOrder）"，两套枚举不同；但判定逻辑与开关口径必须完全一致，故此处照同一套规则实现，
 * 只新增"实体名 → 菜单权限码"的映射（映射来源：库中 {@code sys_menu.permission_code} 实查值，逐一核实）。
 *
 * <p>mode=action 说明：导入没有"按钮权限"概念（sys_role_permission 的按钮码未覆盖导入），
 * 因此 action 模式下无法做更严的按钮级判定，按与单据一致的兜底策略回退到 menu 判定（见 {@link #allowed}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportPermissionResolver {

    /** sys_config 开关：权限判定模式（与单据动作共用同一开关，口径一致） */
    private static final String CONFIG_KEY_MODE = "ai.bill.permission.mode";
    private static final String MODE_MENU = "menu";
    private static final String MODE_ACTION = "action";

    /**
     * 「实体未登记任何权限映射」的一次性告警去重集。
     * 单例 Bean 且 allowed() 会被 SSE 线程池并发调用 → 必须用并发集合；
     * 仅按实体名去重，与用户/租户无关，不构成跨用户缓存污染。
     */
    private final Set<String> warnedUnmappedEntities = ConcurrentHashMap.newKeySet();

    private final SysConfigService sysConfigService;
    private final ExcelService excelService;

    /**
     * 实体名 → 菜单权限码（必须与 {@code sys_menu.permission_code} **逐字一致**）。
     *
     * <p>🔴 修复记录（2026-09-18，勿再凭猜测填写）：本表原来填的是
     * {@code basedata:customer:view} / {@code sale:order:view} 这类 **{@code :view} 结尾**的码，
     * 而数据库真实码是 {@code basedata:customer} / {@code sale:orders} 这类 **「模块:资源」**格式。
     * 实测 {@code SELECT COUNT(*) FROM sys_menu WHERE permission_code LIKE '%:view'} = 0
     * —— 一条都匹配不上，导致**除超管/管理员外，所有账号的 AI 导入工具（import_*）一律不可见**
     * （与 2026-09-14 单据动作权限码事故是同一个坑，导入侧当时漏改）。
     *
     * <p>码来源链路（务必按此对齐）：{@code AuthInterceptor.loadUserPermissions()} 用
     * {@code sys_role_permission.menu_id = sys_menu.route_path} 关联后取 {@code sys_menu.permission_code}
     * 注入 request attribute → {@link AiDataScope#getPermissions()}。**新增实体必须先查库确认码，禁止臆造。**
     */
    private static final Map<String, String> MENU_CODE = Map.ofEntries(
            // 基础资料
            Map.entry("customer", "basedata:customer"),
            Map.entry("supplier", "basedata:supplier"),
            Map.entry("material", "basedata:material"),
            Map.entry("materialCategory", "basedata:material-category"),
            Map.entry("unit", "basedata:unit"),
            Map.entry("warehouse", "basedata:warehouse"),
            Map.entry("location", "basedata:location"),
            Map.entry("department", "basedata:department"),
            Map.entry("employee", "basedata:employee"),
            Map.entry("currency", "basedata:currency"),
            Map.entry("paymentTerm", "basedata:payment-term"),
            Map.entry("priceLevel", "basedata:price-level"),
            Map.entry("settlementMode", "basedata:settle"),
            Map.entry("bankAccount", "basedata:bank-account"),
            Map.entry("bom", "masterdata:bom"),
            // CRM
            Map.entry("lead", "crm:lead"),
            Map.entry("opportunity", "crm:opportunity"),
            Map.entry("complaint", "crm:complaint"),
            Map.entry("survey", "crm:survey"),
            // 销售
            Map.entry("saleOrder", "sale:orders"),
            Map.entry("saleQuote", "sale:quotes"),
            Map.entry("saleContract", "sale:contracts"),
            Map.entry("saleDelivery", "sale:deliveries"),
            Map.entry("saleReturn", "sale:returns"),
            Map.entry("saleInvoice", "sale:invoices"),
            // 采购
            Map.entry("purchaseRequest", "purchase:requests"),
            Map.entry("purchaseOrder", "purchase:orders"),
            Map.entry("purchaseContract", "purchase:contracts"),
            Map.entry("purchaseInbound", "purchase:inbounds"),
            Map.entry("purchaseReturn", "purchase:returns"),
            Map.entry("purchaseInvoice", "purchase:invoices"),
            // 生产
            Map.entry("workOrder", "production:workorders"),
            Map.entry("productionReceipt", "production:receipts"),
            Map.entry("routing", "production:routings"),
            Map.entry("mrpPlan", "production:mrp"),
            // 库存
            Map.entry("otherReceipt", "inventory:other-receipts"),
            Map.entry("otherIssue", "inventory:other-issues"),
            Map.entry("stockTransfer", "inventory:stock-transfers"),
            Map.entry("stockCheck", "inventory:stock-checks"),
            Map.entry("materialRequisition", "inventory:material-requisitions"),
            // 财务
            Map.entry("paymentReceipt", "finance:receipt"),
            Map.entry("paymentVoucher", "finance:payment"),
            Map.entry("purchasePrePayment", "finance:purchase-pre-payments"),
            Map.entry("salePreReceipt", "finance:sale-pre-receipts"),
            Map.entry("operationExpense", "finance:operation-expense"),
            // 委外
            Map.entry("subcontract", "subcontract:orders"),
            Map.entry("subcontractPick", "subcontract:picks"),
            Map.entry("subcontractInbound", "subcontract:inbounds"),
            Map.entry("subcontractReturn", "subcontract:returns"),
            Map.entry("subcontractSupplement", "subcontract:supplements")
    );

    /**
     * 无独立菜单权限码的实体 → 仅登记的模块码。
     *
     * <p>两类：① {@code accountsReceivable/accountsPayable} 是"只导出不导入"（不在可导入白名单，
     * 仅留痕）；② {@code account(科目)/workCenter/workstation/operationReport}
     * 在 {@code sys_menu} 里本来就没有独立菜单码（界面也没有独立页面），
     * 按「模块前缀」兜底判定（与界面按模块授权一致），避免被误判成"仅管理员可用"。
     */
    private static final Map<String, String> MODULE_CODE_ONLY = Map.ofEntries(
            Map.entry("accountsReceivable", "finance"),
            Map.entry("accountsPayable", "finance"),
            Map.entry("account", "basedata"),
            Map.entry("workCenter", "production"),
            Map.entry("workstation", "production"),
            Map.entry("operationReport", "production")
    );

    /** 实体名 → 中文名（拒绝提示与模板清单展示用；取自 sys_menu.menu_name，逐一核实） */
    private static final Map<String, String> ENTITY_LABELS = Map.ofEntries(
            Map.entry("customer", "客户档案"),
            Map.entry("supplier", "供应商档案"),
            Map.entry("material", "物料档案"),
            Map.entry("unit", "计量单位"),
            Map.entry("department", "部门档案"),
            Map.entry("employee", "员工档案"),
            Map.entry("materialCategory", "物料分类"),
            Map.entry("warehouse", "仓库档案"),
            Map.entry("location", "货位档案"),
            Map.entry("account", "会计科目"),
            Map.entry("currency", "币种"),
            Map.entry("paymentTerm", "付款条件"),
            Map.entry("priceLevel", "价格等级"),
            Map.entry("settlementMode", "结算方式"),
            Map.entry("bankAccount", "银行账户"),
            Map.entry("lead", "销售线索"),
            Map.entry("opportunity", "商机管理"),
            Map.entry("complaint", "客户投诉"),
            Map.entry("survey", "满意度调查"),
            Map.entry("saleOrder", "销售订单"),
            Map.entry("saleQuote", "销售报价单"),
            Map.entry("saleContract", "销售合同"),
            Map.entry("saleDelivery", "销售出库单"),
            Map.entry("saleReturn", "销售退货"),
            Map.entry("saleInvoice", "销售发票"),
            Map.entry("purchaseRequest", "采购申请单"),
            Map.entry("purchaseOrder", "采购订单"),
            Map.entry("purchaseContract", "采购合同"),
            Map.entry("purchaseInbound", "采购入库单"),
            Map.entry("purchaseReturn", "采购退货单"),
            Map.entry("purchaseInvoice", "采购发票"),
            Map.entry("bom", "BOM清单"),
            Map.entry("workOrder", "生产工单"),
            Map.entry("productionReceipt", "生产入库单"),
            Map.entry("operationReport", "工序汇报单"),
            Map.entry("workCenter", "工作中心"),
            Map.entry("routing", "工艺路线"),
            Map.entry("workstation", "工序/工位"),
            Map.entry("mrpPlan", "MRP运算"),
            Map.entry("otherReceipt", "其他入库单"),
            Map.entry("otherIssue", "其他出库单"),
            Map.entry("stockTransfer", "库存调拨单"),
            Map.entry("stockCheck", "库存盘点单"),
            Map.entry("materialRequisition", "生产领料单"),
            Map.entry("paymentReceipt", "收款单"),
            Map.entry("paymentVoucher", "付款单"),
            Map.entry("purchasePrePayment", "采购预付款单"),
            Map.entry("salePreReceipt", "销售预收款单"),
            Map.entry("operationExpense", "运营费用"),
            Map.entry("subcontract", "委外订单"),
            Map.entry("subcontractPick", "委外发料单"),
            Map.entry("subcontractInbound", "委外入库单"),
            Map.entry("subcontractReturn", "委外退料单"),
            Map.entry("subcontractSupplement", "委外补料单"),
            Map.entry("accountsReceivable", "应收账款"),
            Map.entry("accountsPayable", "应付账款")
    );

    /**
     * 判定某实体的导入权限。
     *
     * @param entityName 实体名（必须在可导入白名单内）
     * @param scope      数据权限上下文
     * @return null=允许；非 null=拒绝原因
     */
    public String check(String entityName, AiDataScope scope) {
        if (scope == null || scope.getUserId() == null) {
            return "无法获取用户权限上下文，已拒绝执行";
        }
        if (scope.hasPermission("*") || scope.isAdmin()) {
            log.info("[AI导入] 权限判定 mode={} entity={} → 放行（{}）",
                    mode(), entityName, scope.hasPermission("*") ? "通配符*" : "管理员角色");
            return null;
        }
        if (!known(entityName)) {
            log.info("[AI导入] 权限判定 mode={} entity={} → 拒绝（不在可导入白名单）", mode(), entityName);
            return "不支持的导入实体: " + entityName;
        }
        if (allowed(entityName, scope)) {
            log.info("[AI导入] 权限判定 mode={} entity={} → 放行", mode(), entityName);
            return null;
        }
        log.info("[AI导入] 权限判定 mode={} entity={} → 拒绝（缺少菜单码/模块码）", mode(), entityName);
        return denyMessage(entityName);
    }

    /**
     * 只读清单类工具/暴露层用：只要用户对**任一**可导入实体有权限即放行。
     *
     * @return null=允许；非 null=拒绝原因
     */
    public String checkAny(AiDataScope scope) {
        if (scope == null || scope.getUserId() == null) {
            return "无法获取用户权限上下文，已拒绝执行";
        }
        if (scope.hasPermission("*") || scope.isAdmin()) {
            return null;
        }
        for (String entity : importableEntities()) {
            if (allowed(entity, scope)) {
                return null;
            }
        }
        return "无权限：需要对任一可导入实体（客户档案/供应商档案/物料档案/销售订单/采购订单等）的菜单权限，"
                + "请联系管理员在 设置中心 → 角色权限设置 中授权";
    }

    /** 是否对该实体有导入权限（静默判定，用于清单过滤，不产生日志） */
    public boolean allowed(String entityName, AiDataScope scope) {
        if (scope == null || scope.getUserId() == null || !known(entityName)) return false;
        if (scope.hasPermission("*") || scope.isAdmin()) return true;
        String menuCode = MENU_CODE.get(entityName);
        if (menuCode != null && scope.hasPermission(menuCode)) return true;
        String moduleCode = moduleCode(entityName);
        if (moduleCode != null && !moduleCode.isBlank() && scope.hasPermission(moduleCode)) return true;
        if (menuCode == null && (moduleCode == null || moduleCode.isBlank())) {
            // 未登记任何权限映射 → 从严拒绝，但必须留可排查日志（禁止静默：
            // 历史坑"工具注册成功却永远不暴露"就是这么查不出来）
            if (warnedUnmappedEntities.add(entityName)) {
                log.warn("[AI导入] 实体 {} 未登记菜单/模块权限码，导入工具对它一律拒绝；"
                        + "如需对 AI 开放，请在 ImportPermissionResolver.MENU_CODE / MODULE_CODE_ONLY 中登记", entityName);
            }
            return false;
        }
        // 兜底：该实体在 sys_menu 没有独立权限码时，用「模块前缀」判定，避免被误判成"仅管理员可用"
        return menuCode == null && scope.hasPermissionPrefix(moduleCode + ":");
    }

    /** 用户有权限导入的实体清单（暴露层/清单工具用） */
    public List<String> permittedEntities(AiDataScope scope) {
        List<String> result = new ArrayList<>();
        for (String entity : importableEntities()) {
            if (allowed(entity, scope)) {
                result.add(entity);
            }
        }
        return result;
    }

    public String entityLabel(String entityName) {
        if (entityName == null) return "?";
        return ENTITY_LABELS.getOrDefault(entityName, entityName);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private boolean known(String entityName) {
        return entityName != null && importableEntities().contains(entityName);
    }

    private Collection<String> importableEntities() {
        return excelService.getImportableEntities();
    }

    private String mode() {
        String v = sysConfigService.getValue(CONFIG_KEY_MODE, MODE_MENU);
        return MODE_ACTION.equalsIgnoreCase(v == null ? "" : v.trim()) ? MODE_ACTION : MODE_MENU;
    }

    /** 模块码 = 菜单码去掉最后一段（如 basedata:customer:view → basedata:customer）；无菜单码时取手工登记值 */
    private String moduleCode(String entityName) {
        if (entityName == null) return null;
        String code = MENU_CODE.get(entityName);
        if (code != null) {
            int idx = code.lastIndexOf(':');
            return idx > 0 ? code.substring(0, idx) : code;
        }
        return MODULE_CODE_ONLY.get(entityName);
    }

    private String denyMessage(String entityName) {
        return "无权限：需要「" + entityLabel(entityName) + "」权限，请联系管理员在 设置中心 → 角色权限设置 中授权";
    }
}
