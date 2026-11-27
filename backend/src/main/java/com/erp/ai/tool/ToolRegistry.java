package com.erp.ai.tool;

import com.erp.ai.bill.BillActionPermissionResolver;
import com.erp.ai.bill.BillActionRegistry;
import com.erp.ai.dto.EntityCrudConfig;
import com.erp.ai.imports.ImportPermissionResolver;
import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.impl.GenericCreateTool;
import com.erp.ai.tool.impl.GenericDeleteTool;
import com.erp.ai.tool.impl.GenericQueryTool;
import com.erp.ai.tool.impl.GenericUpdateTool;
import com.erp.config.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final AiCrudAgentService aiCrudAgentService;
    private final SysConfigService sysConfigService;
    private final BillActionRegistry billActionRegistry;
    private final BillActionPermissionResolver billPermissionResolver;
    private final ImportPermissionResolver importPermissionResolver;
    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    /**
     * 只读工具白名单（用于 isWriteTool 从严判定）。
     * 除 query_ 前缀、setting_ 只读后缀、以及本名单外的工具，一律按写工具处理。
     */
    private static final Set<String> READ_ONLY_TOOLS = Set.of(
            "image_search", "image_read", "document_read", "print_template_fields",
            "report_list_datasources", "bom_file_read", "open_page", "platform_data_query",
            // 2026-11-22 立项三期：固定报表一键出数 / 聚合排行（均只读；数据权限在执行时按用户解析）
            "report_query", "report_aggregate_query");

    /**
     * 「工具未命中登记规则」的一次性告警去重集。
     * 说明：单例 Bean，且 listToolsForFunctionCalling/checkQueryPermission 会被 SSE 线程池并发调用，
     * 故必须用并发集合；本集仅按工具名/模块名去重，与用户、租户无关，不构成跨用户缓存污染。
     */
    private final Set<String> warnedUnmappedTools = ConcurrentHashMap.newKeySet();

    /** CRUD 工具名前缀（entityLabelOfTool 与动态注册共用同一份，避免两处口径漂移） */
    private static final List<String> CRUD_TOOL_PREFIXES = List.of("query_", "create_", "update_", "delete_");

    private static final Map<String, String> ENTITY_NAME_MAP = Map.ofEntries(
            Map.entry("BASEDATA_DEPARTMENT", "部门"),
            Map.entry("BASEDATA_EMPLOYEE", "员工"),
            Map.entry("BASEDATA_CURRENCY", "币种"),
            Map.entry("BASEDATA_UNIT", "计量单位"),
            Map.entry("BASEDATA_ACCOUNT", "会计科目"),
            Map.entry("BASEDATA_PAYMENTTERM", "付款条件"),
            Map.entry("BASEDATA_PRICELEVEL", "价格等级"),
            Map.entry("BASEDATA_SETTLE", "结算方式"),
            Map.entry("BASEDATA_MATERIALCATEGORY", "物料分类"),
            Map.entry("BASEDATA_CUSTOMER", "客户"),
            Map.entry("BASEDATA_SUPPLIER", "供应商"),
            Map.entry("BASEDATA_MATERIAL", "物料"),
            Map.entry("CRM_LEAD", "线索"),
            Map.entry("CRM_OPPORTUNITY", "商机"),
            Map.entry("CRM_FOLLOWUP", "跟进记录"),
            Map.entry("CRM_COMPLAINT", "客诉"),
            Map.entry("CRM_SURVEY", "客户调研"),
            Map.entry("SALE_QUOTE", "销售报价单"),
            Map.entry("SALE_ORDER", "销售订单"),
            Map.entry("SALE_DELIVERY", "销售出库单"),
            Map.entry("SALE_RETURN", "销售退货单"),
            Map.entry("SALE_EXCHANGE", "销售换货单"),
            Map.entry("SALE_REPAIR", "售后维修单"),
            Map.entry("SALE_INVOICE", "销售发票"),
            Map.entry("SALE_CONTRACT", "销售合同"),
            Map.entry("PURCHASE_REQUEST", "采购申请"),
            Map.entry("PURCHASE_ORDER", "采购订单"),
            Map.entry("PURCHASE_INBOUND", "采购入库单"),
            Map.entry("PURCHASE_RETURN", "采购退货单"),
            Map.entry("PURCHASE_INVOICE", "采购发票"),
            Map.entry("PURCHASE_CONTRACT", "采购合同"),
            Map.entry("INVENTORY_STOCK", "库存"),
            Map.entry("INVENTORY_WAREHOUSE", "仓库"),
            Map.entry("INVENTORY_LOCATION", "库位"),
            Map.entry("INVENTORY_TRANSFER", "库存调拨"),
            Map.entry("INVENTORY_CHECK", "库存盘点"),
            Map.entry("INVENTORY_REQUISITION", "领料单"),
            Map.entry("PRODUCTION_WORKORDER", "生产工单"),
            Map.entry("PRODUCTION_BOM", "物料清单BOM"),
            Map.entry("PRODUCTION_ROUTING", "工艺路线"),
            Map.entry("PRODUCTION_RECEIPT", "生产入库单"),
            Map.entry("PRODUCTION_WORKCENTER", "工作中心"),
            Map.entry("PRODUCTION_WORKSTATION", "工位"),
            Map.entry("FINANCE_RECEIVABLE", "应收账款"),
            Map.entry("FINANCE_PAYABLE", "应付账款"),
            Map.entry("FINANCE_EXPENSE", "费用分摊"),
            Map.entry("SYSTEM_USER", "系统用户"),
            Map.entry("SYSTEM_ROLE", "系统角色"),
            Map.entry("SYSTEM_MENU", "系统菜单"),
            Map.entry("SYSTEM_DICT", "数据字典"),
            // ===== 批次1新增（2026-09-28 查询对象全量登记）=====
            Map.entry("BASEDATA_BANKACCOUNT", "银行账户"),
            Map.entry("BASEDATA_MATERIALCODERULE", "物料编码规则"),
            Map.entry("PRODUCTION_ASSEMBLE", "生产组装单"),
            Map.entry("PRODUCTION_DISASSEMBLE", "生产拆卸单"),
            Map.entry("PRODUCTION_OPERATIONREPORT", "工序汇报单"),
            Map.entry("PRODUCTION_MRPPLAN", "MRP计划单"),
            Map.entry("INVENTORY_BATCH", "批次库存"),
            Map.entry("INVENTORY_LEDGER", "库存流水"),
            Map.entry("INVENTORY_OTHERRECEIPT", "其他入库单"),
            Map.entry("INVENTORY_OTHERISSUE", "其他出库单"),
            Map.entry("SUBCONTRACT_ORDER", "委外加工单"),
            Map.entry("SUBCONTRACT_PICK", "委外发料单"),
            Map.entry("SUBCONTRACT_INBOUND", "委外入库单"),
            Map.entry("SUBCONTRACT_RETURN", "委外退料单"),
            Map.entry("SUBCONTRACT_SUPPLEMENT", "委外补料单"),
            Map.entry("SAMPLE_ORDER", "打样单"),
            Map.entry("SAMPLE_FREESAMPLE", "免费样品单"),
            Map.entry("CRM_CONTACT", "联系人"),
            Map.entry("CRM_SALESTARGETCOMPANY", "公司销售目标"),
            Map.entry("FINANCE_COSTCALCULATION", "成本核算单"),
            Map.entry("FINANCE_COSTCONFIG", "成本核算配置"),
            Map.entry("FINANCE_WAGESETTLEMENT", "工序工资核算单"),
            Map.entry("FINANCE_SALEPRERECEIPT", "销售预收款单"),
            Map.entry("FINANCE_PURCHASEPREPAYMENT", "采购预付款单"),
            Map.entry("FINANCE_SALEEXPENSE", "销售费用单"),
            Map.entry("FINANCE_PURCHASEEXPENSE", "采购费用单"),
            Map.entry("FINANCE_SALESTATEMENT", "客户对账单"),
            Map.entry("FINANCE_PURCHASESTATEMENT", "供应商对账单"),
            Map.entry("ACQUISITION_TASK", "获客任务"),
            Map.entry("ACQUISITION_RESULT", "采集结果"),
            Map.entry("SYSTEM_APPROVALTASK", "审批任务"),
            // ===== 批次1补登（2026-09-28 查询对象全量登记）=====
            Map.entry("BASEDATA_QCTEMPLATE", "质检模板"),
            Map.entry("PRODUCTION_QCRECORD", "检验记录"),
            Map.entry("CRM_SALESTARGETEMPLOYEE", "销售目标分配"),
            Map.entry("CRM_WARNINGCONFIG", "客户跟进预警配置"),
            Map.entry("FINANCE_RECEIPT", "收款单"),
            Map.entry("FINANCE_VOUCHER", "付款单"),
            Map.entry("SYSTEM_NOTIFICATION", "消息通知"),
            Map.entry("SYSTEM_TENANT", "租户"),
            Map.entry("SYSTEM_DEPLOYVERSION", "部署版本")
    );

    @PostConstruct
    public void init() {
        Map<String, AiTool> beans = applicationContext.getBeansOfType(AiTool.class);
        int conflictCount = 0;
        for (AiTool tool : beans.values()) {
            String name = tool.getName();
            // 用 putIfAbsent 而非 put：后者会让新工具静默顶掉同名老工具，极难排查
            AiTool exists = tools.putIfAbsent(name, tool);
            if (exists != null) {
                conflictCount++;
                log.warn("[工具注册] 工具名冲突：{} 已被 {} 占用，本次注册的 {} 未生效（保留先注册者）；请改名后重新编译",
                        name, exists.getClass().getSimpleName(), tool.getClass().getSimpleName());
            } else {
                log.info("[工具注册] 已注册工具: {}", name);
            }
        }

        registerCrudTools();

        log.info("[工具注册] 共注册 {} 个工具（名称冲突已丢弃 {} 个）", tools.size(), conflictCount);
    }

    private void registerCrudTools() {
        Map<String, EntityCrudConfig> configs = aiCrudAgentService.getAllConfigs();
        int registered = 0;

        for (Map.Entry<String, EntityCrudConfig> entry : configs.entrySet()) {
            String intent = entry.getKey();
            EntityCrudConfig cfg = entry.getValue();

            String intentPrefix = cfg.getIntentPrefix();
            String cnName = ENTITY_NAME_MAP.getOrDefault(intentPrefix, cfg.getEntityClass().getSimpleName());
            String toolBaseName = toToolName(intentPrefix);

            // 查询工具
            String queryToolName = "query_" + toolBaseName;
            if (!tools.containsKey(queryToolName)) {
                String queryDesc = "【" + cnName + "查询】查询" + cnName + "列表和详情信息，支持按关键词模糊搜索。适用场景：用户问" + cnName + "有哪些、查一下" + cnName + "、" + cnName + "列表等。";
                GenericQueryTool queryTool = new GenericQueryTool(
                        queryToolName, queryDesc, intent,
                        cfg.getEntityClass().getSimpleName(),
                        cfg.getSearchFields(), cfg.getDisplayFields(),
                        cfg.getFilterFields(),
                        aiCrudAgentService
                );
                tools.put(queryToolName, queryTool);
                registered++;
                log.debug("[工具注册] 动态注册查询工具: {}", queryToolName);
            }

            // 新增工具
            String createToolName = "create_" + toolBaseName;
            if (!tools.containsKey(createToolName)) {
                // 带明细的单据（实体里有 items 列表）：工具必须把 items 一起声明为必填，
                // 否则模型只会给表头 → 明细为空却返回"创建成功"（2026-10-21 用户实测）
                boolean hasItems = hasItemsField(cfg.getEntityClass());
                String createDesc = "新增一条" + cnName + "记录。用于创建新的" + cnName + "数据。";
                if ("BASEDATA_MATERIAL".equals(cfg.getIntentPrefix())) {
                    createDesc += "建物料时可带图片：imageUrls（多张图片地址，英文逗号分隔）与 mainImage（主图地址），地址取自 document_read/import_file_read 返回的 images。";
                }
                GenericCreateTool createTool = new GenericCreateTool(
                        createToolName, createDesc, intent,
                        cnName, hasItems,
                        aiCrudAgentService
                );
                tools.put(createToolName, createTool);
                registered++;
                log.debug("[工具注册] 动态注册新增工具: {}", createToolName);
            }

            // 修改工具
            String updateToolName = "update_" + toolBaseName;
            if (!tools.containsKey(updateToolName)) {
                String updateDesc = "修改" + cnName + "信息。根据ID更新" + cnName + "的指定字段。";
                GenericUpdateTool updateTool = new GenericUpdateTool(
                        updateToolName, updateDesc, intent,
                        cfg.getEntityClass().getSimpleName(),
                        cfg.getPrimaryKeyField(),
                        aiCrudAgentService
                );
                tools.put(updateToolName, updateTool);
                registered++;
                log.debug("[工具注册] 动态注册修改工具: {}", updateToolName);
            }

            // 删除工具
            String deleteToolName = "delete_" + toolBaseName;
            if (!tools.containsKey(deleteToolName)) {
                String deleteDesc = "删除" + cnName + "记录。根据ID删除指定的" + cnName + "。";
                GenericDeleteTool deleteTool = new GenericDeleteTool(
                        deleteToolName, deleteDesc, intent,
                        cfg.getEntityClass().getSimpleName(),
                        cfg.getPrimaryKeyField(),
                        aiCrudAgentService
                );
                tools.put(deleteToolName, deleteTool);
                registered++;
                log.debug("[工具注册] 动态注册删除工具: {}", deleteToolName);
            }
        }

        log.info("[工具注册] 动态注册了 {} 个CRUD工具", registered);
    }

    private String toToolName(String intentPrefix) {
        // 禁止再写 replace("_", "_") 之类的空操作（下划线换下划线，等于没写）；
        // 必须用 Locale.ROOT，避免土耳其语环境下 I 被转成无点 ı 导致 INVENTORY → ınventory
        return intentPrefix.toLowerCase(Locale.ROOT);
    }

    /** 实体是否为"带明细的单据"：自身或父类声明了 items 列表字段 */
    private boolean hasItemsField(Class<?> entityClass) {
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField("items");
                return List.class.isAssignableFrom(f.getType());
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return false;
    }

    public AiTool getTool(String name) {
        return tools.get(name);
    }

    /** 实体键（basedata_material / material / sale_order…）→ 面向用户的中文名（批量创建卡片摘要用） */
    public String entityLabelOfKey(String entityKey) {
        try {
            String label = aiCrudAgentService.intentLabel(aiCrudAgentService.resolveIntent(entityKey));
            return label == null ? "" : label;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 按工具名取"业务中文名"：create_basedata_material → 物料；query_sale_order → 销售订单。
     * 非 CRUD 工具（bill_action / open_page 等）与未登记的实体返回空串，由调用方决定回退文案。
     *
     * <p>用途：生成**面向用户**的步骤文案与确认卡片摘要。历史实现把内部工具名直接写给用户看
     * （如「执行写操作 create_basedata_material: {"data":{...}}」），用户 2026-09-18 反馈"乱得很"。
     *
     * <p>单一来源 = {@link #ENTITY_NAME_MAP}（与工具注册时用的中文名同源），禁止另起一套映射，
     * 否则菜单/实体改名后必出两套口径（历史事故：权限码凭猜测填写导致 27 条全错）。
     */
    public String entityLabelOfTool(String toolName) {
        if (toolName == null) return "";
        for (String prefix : CRUD_TOOL_PREFIXES) {
            if (toolName.startsWith(prefix)) {
                String base = toolName.substring(prefix.length());
                return ENTITY_NAME_MAP.getOrDefault(base.toUpperCase(Locale.ROOT), "");
            }
        }
        return "";
    }

    /** 设置中心类工具统一前缀（统一开关与权限口径） */
    private static final String SETTING_TOOL_PREFIX = "setting_";

    /** 开账引导初始化工具（2026-10-11）：独立开关默认 true —— 只加数据不删改、强制确认卡、仅管理员 */
    private static final String SETTING_INITIALIZE_TOOL = "setting_initialize_system";
    private static final String INITIALIZE_SWITCH = "ai.tool.initialize.enabled";

    /** 看板公式卡保存工具（写；独立子开关默认 false，保存只影响当前登录人） */
    private static final String BOARD_SAVE_TOOL = "board_save_formula_card";
    private static final String BOARD_SAVE_SWITCH = "ai.tool.board_save.enabled";

    /** 设置中心类工具中"只读"的后缀；其余 setting_ 工具一律按写操作走强制确认 */
    private static final List<String> SETTING_READ_SUFFIXES = List.of("_list", "_search", "_get", "_current");

    /** 业务单据流程动作工具名（写） */
    private static final String BILL_ACTION_TOOL = "bill_action";

    /** 业务单据下推工具名（写） */
    private static final String BILL_PUSH_TOOL = "bill_pushdown";

    /** 单据明细行修改工具名（写；子开关 ai.tool.bill_item.enabled） */
    private static final String BILL_ITEM_UPDATE_TOOL = "bill_item_update";

    /** 批量单据动作工具名（写；子开关 ai.tool.bill_batch.enabled） */
    private static final String BILL_BATCH_ACTION_TOOL = "bill_batch_action";

    /** 工序操作工具名（写；子开关 ai.tool.work_operation.enabled）—— 生产「工序操作面板」的 7 个动作 */
    private static final String WORK_OPERATION_ACTION_TOOL = "work_operation_action";

    /** 批量创建（可跨表）工具名（写；走通用 CRUD 总开关 ai.tool.crud.enabled） */
    private static final String BATCH_CREATE_RECORDS_TOOL = "batch_create_records";

    /** 多步骤任务链（写；与 create_ 同口径走写总开关 + 通用 CRUD 开关） */
    private static final String TASK_PLAN_TOOL = "task_plan_execute";

    /** 业务单据能力查询工具名（只读） */
    private static final String BILL_ACTION_LIST_TOOL = "bill_action_list";

    /** 单据明细行查看工具名（只读） */
    private static final String BILL_ITEM_LIST_TOOL = "bill_item_list";

    /** 平台级数据查询工具名（只读；仅平台管理员；子开关 ai.tool.platform_query.enabled） */
    private static final String PLATFORM_DATA_QUERY_TOOL = "platform_data_query";
    /** 平台级数据查询工具总开关 */
    private static final String PLATFORM_DATA_QUERY_SWITCH = "ai.tool.platform_query.enabled";

    /** 单据类工具统一前缀（统一开关与权限口径） */
    private static final String BILL_TOOL_PREFIX = "bill_";

    /** 通用导入工具统一前缀 */
    private static final String IMPORT_TOOL_PREFIX = "import_";
    /** 物料导入工具统一前缀 */
    private static final String MATERIAL_IMPORT_TOOL_PREFIX = "material_import_";
    /** 导入工具中「写」的工具名（其余为只读，不消耗写开关） */
    private static final Set<String> IMPORT_WRITE_TOOLS =
            Set.of("import_file", "import_rows", "material_import_confirm");

    /** 是否为通用导入工具（import_ / material_import_ 两个前缀） */
    private static boolean isImportTool(String toolName) {
        return toolName != null
                && (toolName.startsWith(IMPORT_TOOL_PREFIX) || toolName.startsWith(MATERIAL_IMPORT_TOOL_PREFIX));
    }

    /** 是否为导入写工具 */
    private static boolean isImportWriteTool(String toolName) {
        return IMPORT_WRITE_TOOLS.contains(toolName);
    }

    /** 设置中心「语言管理」专用权限码（其余设置项统一用设置中心访问码） */
    private static final String SETTING_PERM_I18N = "system:i18n:manage";

    /** 设置中心访问权限码（sys_menu 中设置中心菜单的 permission_code） */
    private static final String SETTING_PERM_VIEW = "system:settings:view";

    /**
     * 设置中心的菜单路径（sys_role_permission.menu_id 里存的值 = sys_menu.route_path）。
     *
     * <p>为什么需要它（2026-09-18 事故根因）：界面侧对设置中心是「按菜单路径授权」，
     * 而菜单权限码 {@code system:settings:view} 会随菜单被重建（SchemaUpgradeEnsurer）而丢失；
     * 一旦丢失，AI 侧按权限码判定就会把 41 个 setting_* 工具全部静默隐藏 ——
     * 表现为用户报障「小助手没有系统参数/自定义字段工具」（李工 lyc123 实测：工具数 258 → 215）。
     * 因此这里改为以「与界面同源的菜单路径」为主判据，权限码仅作兼容保留。
     */
    private static final String SETTING_MENU_PATH = "/system/settings";

    /** 是否为设置中心只读工具 */
    public static boolean isSettingReadTool(String toolName) {
        if (toolName == null || !toolName.startsWith(SETTING_TOOL_PREFIX)) return false;
        return SETTING_READ_SUFFIXES.stream().anyMatch(toolName::endsWith);
    }

    /**
     * 设置中心类工具的权限判定（与设置中心页面同口径）：
     * 通配符 '*' / 管理员角色 / 设置中心菜单路径授权 / 该项权限码 四者任一即通过。
     *
     * <p>菜单路径判据与界面 1:1：用户在「角色权限设置」里勾了设置中心（或任一子项），
     * sys_role_permission.menu_id 就存有 {@code /system/settings[?item=xxx]}，与界面判可见同源。
     * 权限码 system:settings:view 只在菜单备注码存在时命中，作为兼容保留（不允许它成为单点故障）。
     *
     * @return null=允许；非null=拒绝原因
     */
    private String checkSettingPermission(String toolName, com.erp.ai.dto.AiDataScope scope) {
        if (scope == null || scope.getUserId() == null) {
            return "无法获取用户权限上下文，已拒绝执行";
        }
        if (scope.hasPermission("*") || scope.isAdmin()) return null;
        if (scope.hasMenuPath(SETTING_MENU_PATH)) return null;
        String code = SETTING_PERM_I18N.equals(settingPermissionCode(toolName))
                ? SETTING_PERM_I18N : SETTING_PERM_VIEW;
        if (scope.hasPermission(code)) return null;
        return "无权限：需要「设置中心」访问权限（菜单 " + SETTING_MENU_PATH
                + "），请联系管理员在 设置中心 → 角色权限设置 中授权";
    }

    /** 设置项对应权限码（语言管理单独一个，其余沿用设置中心访问码） */
    private String settingPermissionCode(String toolName) {
        return (toolName != null && toolName.startsWith(SETTING_TOOL_PREFIX + "i18n"))
                ? SETTING_PERM_I18N : SETTING_PERM_VIEW;
    }

    /** 单据类工具中「只读」的工具名（不消耗写开关） */
    private static boolean isBillReadTool(String toolName) {
        return BILL_ACTION_LIST_TOOL.equals(toolName) || BILL_ITEM_LIST_TOOL.equals(toolName);
    }

    /**
     * 单据类工具的权限判定：统一走 {@link BillActionPermissionResolver}（单一适配点）。
     * 暴露层/执行层用"对任一单据有权限即可"的口径；具体单据的精确判定在各工具执行时完成。
     */
    private String checkBillPermission(com.erp.ai.dto.AiDataScope scope) {
        return billPermissionResolver.checkList(scope, billActionRegistry.allBillTypes());
    }

    /**
     * 工序操作工具的权限判定：复用「生产工单」既有菜单权限（{@link BillActionPermissionResolver} 单一适配点），
     * 不新造权限码。暴露层与执行层同口径：能操作生产工单的角色即可通过小助手操作工序。
     */
    private String checkWorkOperationPermission(com.erp.ai.dto.AiDataScope scope) {
        return billPermissionResolver.check("PRODUCTION_ORDER", null, scope);
    }

    /**
     * 通用导入工具的权限判定：暴露层/执行层用"对任一可导入实体有权限即可"的口径；
     * 具体实体的精确判定在各工具执行时由 {@link ImportPermissionResolver#check} 完成。
     */
    private String checkImportPermission(com.erp.ai.dto.AiDataScope scope) {
        return importPermissionResolver.checkAny(scope);
    }

    /**
     * 查询工具的模块级权限开关映射：工具名第二段前缀 → sys_config key。
     * 例：query_finance_voucher → ai.tool.query.finance.enabled
     */
    private static final Map<String, String> QUERY_MODULE_SWITCHES = Map.ofEntries(
            Map.entry("basedata", "ai.tool.query.basedata.enabled"),
            Map.entry("crm", "ai.tool.query.crm.enabled"),
            Map.entry("sale", "ai.tool.query.sale.enabled"),
            Map.entry("purchase", "ai.tool.query.purchase.enabled"),
            Map.entry("inventory", "ai.tool.query.inventory.enabled"),
            Map.entry("production", "ai.tool.query.production.enabled"),
            Map.entry("finance", "ai.tool.query.finance.enabled"),
            Map.entry("system", "ai.tool.query.system.enabled"),
            Map.entry("subcontract", "ai.tool.query.subcontract.enabled"),
            Map.entry("sample", "ai.tool.query.sample.enabled"),
            Map.entry("acquisition", "ai.tool.query.acquisition.enabled"),
            Map.entry("board", "ai.tool.query.board.enabled")
    );

    /** 查询工具开关的中文模块名（拒绝提示用） */
    private static final Map<String, String> QUERY_MODULE_NAMES = Map.ofEntries(
            Map.entry("basedata", "基础资料"),
            Map.entry("crm", "CRM客户关系"),
            Map.entry("sale", "销售"),
            Map.entry("purchase", "采购"),
            Map.entry("inventory", "库存"),
            Map.entry("production", "生产"),
            Map.entry("finance", "财务"),
            Map.entry("system", "系统"),
            Map.entry("subcontract", "委外"),
            Map.entry("sample", "打样"),
            Map.entry("acquisition", "获客"),
            Map.entry("board", "看板")
    );

    /**
     * 命名不符合 query_模块_实体 约定的历史查询工具 → 模块段 的显式映射。
     *
     * 背景（2026-09-13 加固）：原实现靠 toolName.split("_") 取第 2 段判断模块，
     * 命名只有 2 段的工具会被直接判为"无模块开关"并放行。实测下列 5 个工具因此
     * 完全不受后台模块开关管控 —— 管理员关掉开关后 AI 仍能查询。此处逐条补齐，
     * 禁止再靠"命名段数"推断模块归属。
     */
    private static final Map<String, String> QUERY_TOOL_MODULE_OVERRIDES = Map.of(
            "query_customer", "basedata",
            "query_material", "basedata",
            "query_supplier", "basedata",
            "query_employee", "basedata",
            "query_inventory", "inventory"
    );

    /**
     * 查询工具模块权限校验。
     * @return null=允许；非null=拒绝原因
     */
    private String checkQueryPermission(String toolName) {
        // 1) 优先查显式映射（覆盖命名不规范的历史工具）
        String module = QUERY_TOOL_MODULE_OVERRIDES.get(toolName);
        // 2) 再按 query_模块_实体 约定取模块段
        if (module == null) {
            String[] parts = toolName.split("_");
            if (parts.length >= 3) {
                module = parts[1];
            }
        }
        // 3) 仍未匹配：保持放行（不改变现网行为），但必须留下可排查日志，禁止静默
        if (module == null) {
            if (warnedUnmappedTools.add("query:" + toolName)) {
                log.warn("[工具权限] 查询工具 {} 未匹配到任何模块开关，当前不受后台开关管控；"
                        + "如需管控，请在 QUERY_TOOL_MODULE_OVERRIDES 中登记其所属模块", toolName);
            }
            return null;
        }
        String switchKey = QUERY_MODULE_SWITCHES.get(module);
        if (switchKey != null && !sysConfigService.getBooleanValue(switchKey, true)) {
            return QUERY_MODULE_NAMES.getOrDefault(module, module) + "数据查询已被后台关闭（" + switchKey + "=false）";
        }
        return null;
    }

    /**
     * 平台管理员判定：与 AuthInterceptor 对 /api/admin/** 的 fullAdmin 口径一致
     * （SUPER_ADMIN / ADMIN 角色，或通配权限 '*'），<b>不新造权限码</b>。
     */
    private boolean isPlatformAdmin(com.erp.ai.dto.AiDataScope scope) {
        if (scope == null) return false;
        List<String> roles = scope.getRoles();
        if (roles != null && (roles.contains("SUPER_ADMIN") || roles.contains("ADMIN"))) return true;
        return scope.hasPermission("*");
    }

    /**
     * 平台级数据查询工具（platform_data_query）的权限判定：
     * 仅平台管理员可用 + 总开关 {@code ai.tool.platform_query.enabled} 必须开启。
     * @return null=允许；非null=拒绝原因
     */
    private String checkPlatformDataPermission(com.erp.ai.dto.AiDataScope scope) {
        if (!isPlatformAdmin(scope)) {
            return "平台级数据（租户/部署版本）仅平台管理员可查询";
        }
        if (!sysConfigService.getBooleanValue(PLATFORM_DATA_QUERY_SWITCH, false)) {
            return "平台级数据查询已被后台关闭（" + PLATFORM_DATA_QUERY_SWITCH + "=false）";
        }
        return null;
    }

    /** 看板类工具：管理员或拥有任一看板菜单权限才可用 */
    private String checkBoardPermission(com.erp.ai.dto.AiDataScope scope) {
        if (scope == null) return "无法识别当前登录账号";
        if (isPlatformAdmin(scope)) return null;
        for (String p : List.of("/databoard/finance", "/databoard/sale",
                "/databoard/inventory", "/databoard/operation")) {
            if (scope.hasMenuPath(p)) return null;
        }
        return "当前账号没有看板菜单权限，无法查询或保存看板数据";
    }

    /**
     * 暴露给AI Agent的工具列表（受后台 sys_config 权限开关控制）
     *
     * 配置项（sys_config 表 ai 分组）：
     * - ai.tool.write.enabled       总开关：是否允许AI做任何写操作（默认 false）
     * - ai.tool.image_order.enabled 图片下单（默认 false）
     * - ai.tool.bom_import.enabled  BOM结构化导入 bom_import_rows（默认 false；只读的 bom_file_read 不受此开关限制）
     * - ai.tool.image_bom.enabled   图片识别BOM（默认 false）
     * - ai.tool.crud.enabled        通用CRUD(create_/update_/delete_)（默认 false）
     * - ai.tool.order.enabled       创建销售订单草稿 create_sale_order_draft（默认 false）
     * - ai.tool.admin.enabled       超管权限管理工具（默认 false，且仅管理员可见可执行）
     * - ai.tool.report.enabled      AI创建自定义报表（默认 false）
     * - ai.tool.setting.enabled     系统设置类工具 setting_*（默认 false，且需角色拥有设置中心权限）
     * - ai.tool.bill_action.enabled 业务单据动作/下推 bill_action / bill_pushdown（默认 false，且需角色拥有对应单据权限；
     *   只读的 bill_action_list 不受此开关限制）
     * - ai.tool.bill_item.enabled   单据明细行修改 bill_item_update（默认 false，且需角色拥有对应单据权限；
     *   只读的 bill_item_list 不受此开关限制）
     * - ai.tool.bill_batch.enabled  批量单据动作 bill_batch_action（默认 false，且需角色拥有对应单据权限）
     * - ai.tool.work_operation.enabled 工序操作 work_operation_action（默认 false，且需角色拥有「生产工单」权限）
     * - ai.tool.import.enabled      数据导入 import_file / import_rows / material_import_confirm（默认 false，且需角色拥有对应实体权限；
     *   只读的 import_template_list / import_file_read / material_import_check 不受此开关限制）
     * - open_page                   界面导航（只读，始终可用，不受任何开关限制；不写库、不跳外链）
     * - ai.tool.platform_query.enabled 平台级数据查询 platform_data_query（默认 false，且仅平台管理员
     *   SUPER_ADMIN/ADMIN 可用）。用于查租户/部署版本——这两类数据不在租户库，不能走通用 query_ 查询
     * - ai.tool.query.<module>.enabled  查询工具模块级开关（basedata/crm/sale/purchase/
     *   inventory/production/finance/system/subcontract/sample/acquisition，默认 true）
     *
     * 规则：
     * 1. query_ 查询工具按模块开关暴露（默认全开）
     * 2. image_search 以图识图始终暴露（只读查询）
     * 3. report_list_datasources 始终暴露（只读元数据）
     * 4. 写操作工具需对应开关为 true 才暴露
     * 5. 总开关 ai.tool.write.enabled=false 时，所有写操作一律不暴露（无论子开关如何）
     * 6. system_assign_* 超管工具仅对 ADMIN/MANAGER/SUPER_ADMIN 暴露
     * 7. setting_* 设置类工具还需角色拥有设置中心权限（与设置中心页面同口径）
     */
    public List<Map<String, Object>> listToolsForFunctionCalling() {
        return listToolsForFunctionCalling(null);
    }

    public List<Map<String, Object>> listToolsForFunctionCalling(com.erp.ai.dto.AiDataScope scope) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 读取后台权限开关
        boolean writeEnabled = sysConfigService.getBooleanValue("ai.tool.write.enabled", false);
        boolean imageOrderEnabled = sysConfigService.getBooleanValue("ai.tool.image_order.enabled", false);
        boolean bomImportEnabled = sysConfigService.getBooleanValue("ai.tool.bom_import.enabled", false);
        boolean imageBomEnabled = sysConfigService.getBooleanValue("ai.tool.image_bom.enabled", false);
        boolean crudEnabled = sysConfigService.getBooleanValue("ai.tool.crud.enabled", false);
        boolean orderEnabled = sysConfigService.getBooleanValue("ai.tool.order.enabled", false);
        boolean adminEnabled = sysConfigService.getBooleanValue("ai.tool.admin.enabled", false);
        boolean reportEnabled = sysConfigService.getBooleanValue("ai.tool.report.enabled", false);
        boolean settingEnabled = sysConfigService.getBooleanValue("ai.tool.setting.enabled", false);
        boolean billActionEnabled = sysConfigService.getBooleanValue("ai.tool.bill_action.enabled", false);
        boolean billItemEnabled = sysConfigService.getBooleanValue("ai.tool.bill_item.enabled", false);
        boolean billBatchEnabled = sysConfigService.getBooleanValue("ai.tool.bill_batch.enabled", false);
        boolean importEnabled = sysConfigService.getBooleanValue("ai.tool.import.enabled", false);
        boolean workOperationEnabled = sysConfigService.getBooleanValue("ai.tool.work_operation.enabled", false);
        boolean boardSaveEnabled = sysConfigService.getBooleanValue(BOARD_SAVE_SWITCH, false);

        // 写操作子开关仅在总开关开启时生效
        imageOrderEnabled = writeEnabled && imageOrderEnabled;
        bomImportEnabled = writeEnabled && bomImportEnabled;
        imageBomEnabled = writeEnabled && imageBomEnabled;
        crudEnabled = writeEnabled && crudEnabled;
        orderEnabled = writeEnabled && orderEnabled;
        adminEnabled = writeEnabled && adminEnabled;
        reportEnabled = writeEnabled && reportEnabled;
        settingEnabled = writeEnabled && settingEnabled;
        billActionEnabled = writeEnabled && billActionEnabled;
        billItemEnabled = writeEnabled && billItemEnabled;
        billBatchEnabled = writeEnabled && billBatchEnabled;
        importEnabled = writeEnabled && importEnabled;
        workOperationEnabled = writeEnabled && workOperationEnabled;
        boardSaveEnabled = writeEnabled && boardSaveEnabled;

        // 超管工具仅管理员可见
        boolean isAdminUser = scope != null && scope.isAdmin();

        for (AiTool tool : tools.values()) {
            String toolName = tool.getName();
            boolean shouldExpose;

            if (toolName.startsWith("query_")) {
                // 查询工具：按模块开关暴露（默认全开，与历史行为一致）
                shouldExpose = checkQueryPermission(toolName) == null;
            } else if (toolName.equals("image_search")
                    || toolName.equals("image_read")
                    || toolName.equals("document_read")
                    || toolName.equals("print_template_fields")
                    || toolName.equals("report_list_datasources")
                    || toolName.equals("report_query")
                    || toolName.equals("report_aggregate_query")
                    || toolName.equals("bom_file_read")
                    || toolName.equals("open_page")) {
                // 其他只读工具：始终暴露（bom_file_read 只读文件内容，image_read/document_read 只读图片与文档内容，
                // open_page 只做界面导航，均不写库、不消耗写开关）
                // 2026-11-22 新增 report_query（固定报表一键出数）与 report_aggregate_query（聚合排行）：
                // 均为只读；数据权限在执行时按用户解析（与报表页面同口径），报表菜单权限在 report_query 执行时按菜单路径判定
                shouldExpose = true;
            } else if (PLATFORM_DATA_QUERY_TOOL.equals(toolName)) {
                // 平台级数据查询（租户/部署版本）：仅平台管理员可见，且需开关 ai.tool.platform_query.enabled 开启
                shouldExpose = checkPlatformDataPermission(scope) == null;
            } else if (toolName.equals("image_create_order")) {
                shouldExpose = imageOrderEnabled;
            } else if (toolName.equals("bom_import_rows")) {
                shouldExpose = bomImportEnabled;
            } else if (toolName.equals("image_create_bom")) {
                shouldExpose = imageBomEnabled;
            } else if (toolName.equals("create_sale_order_draft")) {
                shouldExpose = orderEnabled;
            } else if (toolName.equals("system_assign_user_roles")
                    || toolName.equals("system_assign_role_permissions")) {
                shouldExpose = adminEnabled && isAdminUser;
            } else if (toolName.equals("report_create_custom")) {
                shouldExpose = reportEnabled;
            } else if (BOARD_SAVE_TOOL.equals(toolName)) {
                // 看板公式卡保存：写总开关 + 独立子开关 + 看板菜单权限
                shouldExpose = boardSaveEnabled && checkBoardPermission(scope) == null;
            } else if (SETTING_INITIALIZE_TOOL.equals(toolName)) {
                // 开账引导初始化（2026-10-11）：独立开关（默认 true）+ 仅管理员可用；
                // 不走 setting_ 总开关 —— 新客户开账时写开关默认关闭，向导必须开箱即用
                shouldExpose = sysConfigService.getBooleanValue(INITIALIZE_SWITCH, true)
                        && scope != null && scope.isAdmin();
            } else if (toolName.startsWith(SETTING_TOOL_PREFIX)) {
                // 设置中心类工具：开关 + 用户权限（与设置中心页面同口径）
                // 只读工具不消耗写开关（如查询日志/价格策略/打印模板清单）
                boolean gate = isSettingReadTool(toolName) ? true : settingEnabled;
                shouldExpose = gate && checkSettingPermission(toolName, scope) == null;
            } else if (toolName.startsWith(BILL_TOOL_PREFIX)) {
                // 单据类工具：bill_action / bill_pushdown / bill_item_update 为写（需总开关+对应子开关）；
                // bill_action_list / bill_item_list 为只读（只走用户权限，不消耗写开关）
                // 2026-09-18 新增 bill_item_update：明细行修改用独立子开关 ai.tool.bill_item.enabled，
                // 便于"只开流程动作、暂不开明细行编辑"这类精细化管控
                // 2026-09-30 新增 bill_batch_action：批量动作用独立子开关 ai.tool.bill_batch.enabled
                boolean gate = isBillReadTool(toolName)
                        || (BILL_ITEM_UPDATE_TOOL.equals(toolName) ? billItemEnabled
                        : (BILL_BATCH_ACTION_TOOL.equals(toolName) ? billBatchEnabled : billActionEnabled));
                shouldExpose = gate && checkBillPermission(scope) == null;
            } else if (isImportTool(toolName)) {
                // 通用导入工具：import_file / import_rows / material_import_confirm 为写（需总开关+导入开关）；
                // import_template_list / import_file_read / material_import_check 为只读（不消耗写开关）。
                // 暴露层按"是否对任一实体有权限"放行，具体实体的精确判定在各工具执行时完成。
                boolean gate = isImportWriteTool(toolName) ? importEnabled : true;
                shouldExpose = gate && checkImportPermission(scope) == null;
            } else if (WORK_OPERATION_ACTION_TOOL.equals(toolName)) {
                // 工序操作工具：写（需总开关 + ai.tool.work_operation.enabled），权限复用「生产工单」既有菜单权限
                shouldExpose = workOperationEnabled && checkWorkOperationPermission(scope) == null;
            } else if (BATCH_CREATE_RECORDS_TOOL.equals(toolName)) {
                // 批量创建（可跨表）：与单条 create_ 同口径（写总开关 + 通用 CRUD 开关）
                shouldExpose = crudEnabled;
            } else if (TASK_PLAN_TOOL.equals(toolName)) {
                // 多步骤任务链：与 create_ 同口径（写总开关 + 通用 CRUD 开关）
                shouldExpose = crudEnabled;
            } else if (toolName.startsWith("create_") || toolName.startsWith("update_") || toolName.startsWith("delete_")) {
                // 通用CRUD写操作
                shouldExpose = crudEnabled;
            } else {
                // 其他未知工具默认不暴露（从严），但必须留下可排查日志
                // 历史坑：静默置 false 会让"工具注册成功却永远不生效"无法定位
                if (warnedUnmappedTools.add("expose:" + toolName)) {
                    log.warn("[工具权限] 工具 {} 未匹配任何暴露规则，已按「不暴露」处理；"
                            + "如需对 AI 开放，请在 ToolRegistry.listToolsForFunctionCalling() 中登记", toolName);
                }
                shouldExpose = false;
            }

            if (!shouldExpose) {
                continue;
            }
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParameters());
            func.put("function", function);
            result.add(func);
        }
        return result;
    }

    /**
     * 判断是否为写操作工具（用于强制确认拦截）。
     *
     * 口径（2026-09-13 由黑名单改为白名单从严）：
     * 只有明确声明的只读工具返回 false，其余一律按写工具处理。
     * 原因：原实现是黑名单 —— 新增写工具若忘记在此登记，就不会走二次确认，
     * 表现为"AI 未经确认直接写库"，且没有任何报错或日志。从严之后，漏登记的后果
     * 从"静默跳过确认"降级为"多弹一次确认卡"，属于可接受的失败方向。
     * 注：现有 251 个工具的判定结果与改动前逐条一致，仅对未登记的新工具生效。
     */
    public boolean isWriteTool(String toolName) {
        if (toolName == null) return false;
        // 查询工具：只读
        if (toolName.startsWith("query_")) return false;
        // 显式声明的只读工具
        if (READ_ONLY_TOOLS.contains(toolName)) return false;
        // 单据类工具：能力查询为只读；动作/下推为写
        if (isBillReadTool(toolName)) return false;
        if (BILL_ACTION_TOOL.equals(toolName) || BILL_PUSH_TOOL.equals(toolName)
                || BILL_BATCH_ACTION_TOOL.equals(toolName)) return true;
        // 工序操作工具：写（必须走强制确认）
        if (WORK_OPERATION_ACTION_TOOL.equals(toolName)) return true;
        // 通用导入工具：import_file / import_rows / material_import_confirm 为写；其余 import_/material_import_ 为只读
        if (isImportTool(toolName)) return isImportWriteTool(toolName);
        // 设置类工具：只读后缀之外的（新增/修改/删除/启停/生成等）一律走强制确认
        if (toolName.startsWith(SETTING_TOOL_PREFIX)) return !isSettingReadTool(toolName);
        // 从严兜底：未经声明的工具一律按写工具，必须走确认卡
        return true;
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 校验工具是否被允许执行（权限检查，作为执行前最后一道防线）
     * 与 listToolsForFunctionCalling() 的暴露规则保持一致
     * @return null=允许执行；非null=拒绝执行，返回的是拒绝原因
     */
    public String checkPermission(String toolName) {
        return checkPermission(toolName, null);
    }

    /** 带数据权限上下文的权限校验（超管工具需校验角色） */
    public String checkPermission(String toolName, com.erp.ai.dto.AiDataScope scope) {
        if (toolName == null) return "工具名不能为空";
        // 查询工具：模块级开关校验（默认全开）
        if (toolName.startsWith("query_")) {
            return checkQueryPermission(toolName);
        }
        // 其他只读工具始终允许
        if (toolName.equals("image_search")
                || toolName.equals("image_read")
                || toolName.equals("document_read")
                || toolName.equals("print_template_fields")
                || toolName.equals("report_list_datasources")
                // 2026-11-22 立项三期：两个报表只读工具在执行时自查（report_query 按报表菜单路径、
                // 聚合排行按数据源白名单 + 数据权限），此处放行以进入执行层
                || toolName.equals("report_query")
                || toolName.equals("report_aggregate_query")
                || toolName.equals("bom_file_read")
                || toolName.equals("open_page")) {
            return null;
        }
        // 平台级数据查询（租户/部署版本）：仅平台管理员 + 开关开启
        if (PLATFORM_DATA_QUERY_TOOL.equals(toolName)) {
            return checkPlatformDataPermission(scope);
        }
        // 看板公式卡保存：写总开关 + 独立子开关 + 看板菜单权限
        if (BOARD_SAVE_TOOL.equals(toolName)) {
            if (!sysConfigService.getBooleanValue("ai.tool.write.enabled", false)) {
                return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行: " + toolName;
            }
            if (!sysConfigService.getBooleanValue(BOARD_SAVE_SWITCH, false)) {
                return "AI保存看板公式卡已被后台关闭（" + BOARD_SAVE_SWITCH + "=false）";
            }
            return checkBoardPermission(scope);
        }
        // 开账引导初始化（2026-10-11）：独立开关（默认 true）+ 仅管理员（与暴露口径一致）
        if (SETTING_INITIALIZE_TOOL.equals(toolName)) {
            if (!sysConfigService.getBooleanValue(INITIALIZE_SWITCH, true)) {
                return "开账引导初始化已被后台关闭（" + INITIALIZE_SWITCH + "=false）";
            }
            if (scope == null || !scope.isAdmin()) {
                return "开账引导初始化仅管理员可执行";
            }
            return null;
        }
        // 设置中心类工具：先按用户权限判定（与设置中心页面同口径），再看开关（只读工具不消耗写开关）
        if (toolName.startsWith(SETTING_TOOL_PREFIX)) {
            String deny = checkSettingPermission(toolName, scope);
            if (deny != null) return deny;
            if (!isSettingReadTool(toolName)) {
                if (!sysConfigService.getBooleanValue("ai.tool.write.enabled", false)) {
                    return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行: " + toolName;
                }
                if (!sysConfigService.getBooleanValue("ai.tool.setting.enabled", false)) {
                    return "AI系统设置功能已被后台关闭（ai.tool.setting.enabled=false）";
                }
            }
            return null;
        }
        // 单据类工具：只读工具只走用户权限；写工具需总开关+对应子开关
        // （单据端点自身无门禁，此处 + 工具执行层的 resolver 判定是防线）
        if (toolName.startsWith(BILL_TOOL_PREFIX)) {
            if (!isBillReadTool(toolName)) {
                if (!sysConfigService.getBooleanValue("ai.tool.write.enabled", false)) {
                    return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行: " + toolName;
                }
                if (BILL_ITEM_UPDATE_TOOL.equals(toolName)) {
                    if (!sysConfigService.getBooleanValue("ai.tool.bill_item.enabled", false)) {
                        return "AI单据明细行编辑已被后台关闭（ai.tool.bill_item.enabled=false）";
                    }
                } else if (BILL_BATCH_ACTION_TOOL.equals(toolName)) {
                    if (!sysConfigService.getBooleanValue("ai.tool.bill_batch.enabled", false)) {
                        return "AI批量单据操作已被后台关闭（ai.tool.bill_batch.enabled=false）";
                    }
                } else if (!sysConfigService.getBooleanValue("ai.tool.bill_action.enabled", false)) {
                    return "AI单据动作/下推功能已被后台关闭（ai.tool.bill_action.enabled=false）";
                }
            }
            return checkBillPermission(scope);
        }
        // 通用导入工具：只读工具只走用户权限；写工具需总开关+导入子开关（再叠加用户实体权限）
        if (isImportTool(toolName)) {
            if (isImportWriteTool(toolName)) {
                if (!sysConfigService.getBooleanValue("ai.tool.write.enabled", false)) {
                    return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行: " + toolName;
                }
                if (!sysConfigService.getBooleanValue("ai.tool.import.enabled", false)) {
                    return "AI数据导入功能已被后台关闭（ai.tool.import.enabled=false）";
                }
            }
            return checkImportPermission(scope);
        }
        // 工序操作工具：写，需总开关 + 工序操作子开关，再叠加用户权限（复用「生产工单」菜单权限）
        if (WORK_OPERATION_ACTION_TOOL.equals(toolName)) {
            if (!sysConfigService.getBooleanValue("ai.tool.write.enabled", false)) {
                return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行: " + toolName;
            }
            if (!sysConfigService.getBooleanValue("ai.tool.work_operation.enabled", false)) {
                return "AI工序操作已被后台关闭（ai.tool.work_operation.enabled=false）";
            }
            return checkWorkOperationPermission(scope);
        }
        // 写操作工具需对应开关开启
        boolean writeEnabled = sysConfigService.getBooleanValue("ai.tool.write.enabled", false);
        if (!writeEnabled) {
            return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行写操作工具: " + toolName;
        }
        if (toolName.equals("image_create_order")) {
            if (!sysConfigService.getBooleanValue("ai.tool.image_order.enabled", false)) {
                return "图片下单功能已被后台关闭（ai.tool.image_order.enabled=false）";
            }
            return null;
        }
        if (toolName.equals("bom_import_rows")) {
            if (!sysConfigService.getBooleanValue("ai.tool.bom_import.enabled", false)) {
                return "BOM导入功能已被后台关闭（ai.tool.bom_import.enabled=false）";
            }
            return null;
        }
        if (toolName.equals("image_create_bom")) {
            if (!sysConfigService.getBooleanValue("ai.tool.image_bom.enabled", false)) {
                return "图片识别BOM功能已被后台关闭（ai.tool.image_bom.enabled=false）";
            }
            return null;
        }
        if (toolName.equals("create_sale_order_draft")) {
            if (!sysConfigService.getBooleanValue("ai.tool.order.enabled", false)) {
                return "AI快速下单功能已被后台关闭（ai.tool.order.enabled=false）";
            }
            return null;
        }
        if (toolName.equals("system_assign_user_roles") || toolName.equals("system_assign_role_permissions")) {
            if (!sysConfigService.getBooleanValue("ai.tool.admin.enabled", false)) {
                return "AI权限管理功能已被后台关闭（ai.tool.admin.enabled=false）";
            }
            if (scope == null || !scope.isAdmin()) {
                return "权限管理工具仅管理员可用";
            }
            return null;
        }
        if (toolName.equals("report_create_custom")) {
            if (!sysConfigService.getBooleanValue("ai.tool.report.enabled", false)) {
                return "AI创建报表功能已被后台关闭（ai.tool.report.enabled=false）";
            }
            return null;
        }
        if (BATCH_CREATE_RECORDS_TOOL.equals(toolName) || TASK_PLAN_TOOL.equals(toolName)) {
            if (!sysConfigService.getBooleanValue("ai.tool.write.enabled", false)) {
                return "AI写操作已被后台关闭（ai.tool.write.enabled=false），无法执行: " + toolName;
            }
            if (!sysConfigService.getBooleanValue("ai.tool.crud.enabled", false)) {
                return "通用增删改查功能已被后台关闭（ai.tool.crud.enabled=false）";
            }
            return null;
        }
        if (toolName.startsWith("create_") || toolName.startsWith("update_") || toolName.startsWith("delete_")) {
            if (!sysConfigService.getBooleanValue("ai.tool.crud.enabled", false)) {
                return "通用增删改查功能已被后台关闭（ai.tool.crud.enabled=false）";
            }
            return null;
        }
        return "未知的工具类型，默认拒绝: " + toolName;
    }

    public Collection<AiTool> getAllTools() {
        return tools.values();
    }
}
