package com.erp.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.ai.credit.AiCreditService;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.knowledge.service.KnowledgeRetrievalService;
import com.erp.ai.service.AiAdapterResolver;
import com.erp.ai.service.AiConfirmService;
import com.erp.ai.service.AiConfigService;
import com.erp.ai.service.AiTokenUsageService;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.ToolRegistry;
import com.erp.ai.util.AiUsageCollector;
import com.erp.ai.util.AiModelContextHolder;
import com.erp.ai.util.AiThoughtContextHolder;
import com.erp.common.Result;
import com.erp.config.CacheService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReActAgentService {

    /**
     * 实体关键词映射：用户输入命中关键词 → 该实体的CRUD工具进入候选清单。
     * 用于第1轮思考前预筛工具，大幅缩小发送给DeepSeek的工具payload（性能优化）。
     * 注意：宽泛词（订单/发票/退货/合同/入库/出库）同时映射销售与采购两侧，
     * 用户未指明模块时两侧工具均可见，由AI自行判断。
     */
    private static final Map<String, List<String>> TOOL_KEYWORDS = Map.ofEntries(
            Map.entry("basedata_department", List.of("部门", "department", "组织机构")),
            Map.entry("basedata_employee", List.of("员工", "职员", "employee", "人事", "人员")),
            Map.entry("basedata_currency", List.of("币种", "货币", "汇率", "currency")),
            Map.entry("basedata_unit", List.of("计量单位", "单位")),
            Map.entry("basedata_account", List.of("会计科目", "科目", "账户", "account")),
            Map.entry("basedata_paymentterm", List.of("付款条件", "账期", "paymentterm")),
            Map.entry("basedata_pricelevel", List.of("价格等级", "pricelevel")),
            Map.entry("basedata_settle", List.of("结算方式")),
            Map.entry("basedata_materialcategory", List.of("物料分类", "分类", "category")),
            Map.entry("basedata_customer", List.of("客户", "customer", "往来单位")),
            Map.entry("basedata_supplier", List.of("供应商", "supplier", "供货", "厂商")),
            Map.entry("basedata_material", List.of("物料", "商品", "产品", "material", "存货", "货品")),
            Map.entry("crm_lead", List.of("线索", "lead", "潜在客户")),
            Map.entry("crm_opportunity", List.of("商机", "opportunity", "销售机会")),
            Map.entry("crm_followup", List.of("跟进", "followup", "回访")),
            Map.entry("crm_complaint", List.of("客诉", "投诉", "complaint")),
            Map.entry("crm_survey", List.of("调研", "survey", "拜访")),
            Map.entry("sale_quote", List.of("报价", "quote")),
            Map.entry("sale_order", List.of("销售订单", "订单", "订货", "下单")),
            Map.entry("sale_delivery", List.of("发货", "出库", "delivery")),
            Map.entry("sale_return", List.of("销售退货", "退货")),
            Map.entry("sale_invoice", List.of("销售发票", "发票", "销项")),
            Map.entry("sale_contract", List.of("销售合同", "合同", "销货合同")),
            Map.entry("purchase_request", List.of("采购申请", "请购")),
            Map.entry("purchase_order", List.of("采购订单", "采购单", "订单")),
            Map.entry("purchase_inbound", List.of("采购入库", "入库", "收料")),
            Map.entry("purchase_return", List.of("采购退货", "退货")),
            Map.entry("purchase_invoice", List.of("采购发票", "发票", "进项")),
            Map.entry("purchase_contract", List.of("采购合同", "合同", "购货合同")),
            Map.entry("inventory_stock", List.of("库存", "inventory", "stock", "现存")),
            Map.entry("inventory_warehouse", List.of("仓库", "warehouse", "仓储")),
            Map.entry("inventory_location", List.of("库位", "货位", "location")),
            Map.entry("inventory_transfer", List.of("调拨", "transfer")),
            Map.entry("inventory_check", List.of("盘点")),
            Map.entry("inventory_requisition", List.of("领料", "requisition")),
            Map.entry("production_workorder", List.of("工单", "workorder", "生产单")),
            Map.entry("production_bom", List.of("bom", "物料清单", "产品结构")),
            Map.entry("production_routing", List.of("工艺", "routing", "工序")),
            Map.entry("production_receipt", List.of("生产入库单", "完工入库")),
            Map.entry("production_workcenter", List.of("工作中心", "workcenter")),
            Map.entry("production_workstation", List.of("工位", "workstation")),
            Map.entry("finance_voucher", List.of("付款", "付款单", "凭证", "voucher")),
            Map.entry("finance_receivable", List.of("应收", "receivable")),
            Map.entry("finance_payable", List.of("应付", "payable")),
            Map.entry("finance_expense", List.of("费用分摊", "费用", "expense")),
            Map.entry("system_user", List.of("用户", "账号", "user")),
            Map.entry("system_role", List.of("角色", "role", "权限组")),
            Map.entry("system_menu", List.of("菜单", "menu")),
            Map.entry("system_dict", List.of("字典", "dict")),
            // ===== 批次1新增关键词（2026-09-28）=====
            Map.entry("basedata_bankaccount", List.of("银行账户", "银行", "开户行", "银行账号", "bankaccount")),
            Map.entry("basedata_materialcoderule", List.of("物料编码规则", "编码规则", "物料编码")),
            Map.entry("production_assemble", List.of("组装", "组装单")),
            Map.entry("production_disassemble", List.of("拆卸", "拆卸单")),
            Map.entry("production_operationreport", List.of("工序汇报", "报工", "汇报", "工序报工")),
            Map.entry("production_mrpplan", List.of("mrp", "mrp计划", "物料需求计划", "计划单")),
            Map.entry("inventory_batch", List.of("批次", "批次库存", "批次号")),
            Map.entry("inventory_ledger", List.of("库存流水", "台账", "库存台账", "出入库流水")),
            Map.entry("inventory_otherreceipt", List.of("其他入库", "其他入库单")),
            Map.entry("inventory_otherissue", List.of("其他出库", "其他出库单")),
            Map.entry("subcontract_order", List.of("委外", "委外加工", "委外单", "外协")),
            Map.entry("subcontract_pick", List.of("委外发料", "委外领料", "委外发料单")),
            Map.entry("subcontract_inbound", List.of("委外入库", "委外入库单")),
            Map.entry("subcontract_return", List.of("委外退料", "委外退料单")),
            Map.entry("subcontract_supplement", List.of("委外补料", "委外补料单")),
            Map.entry("sample_order", List.of("打样", "打样单", "样品")),
            Map.entry("sample_freesample", List.of("免费样品", "免费样品单")),
            Map.entry("crm_contact", List.of("联系人", "contact")),
            Map.entry("crm_salestargetcompany", List.of("销售目标", "公司销售目标", "年度目标")),
            Map.entry("finance_costcalculation", List.of("成本核算", "成本核算单")),
            Map.entry("finance_costconfig", List.of("成本核算配置", "核算配置", "成本方法")),
            Map.entry("finance_wagesettlement", List.of("工序工资", "工资核算", "工资结算")),
            Map.entry("finance_saleprereceipt", List.of("销售预收款", "预收款", "预收")),
            Map.entry("finance_purchaseprepayment", List.of("采购预付款", "预付款", "预付")),
            Map.entry("finance_saleexpense", List.of("销售费用", "销售费用单")),
            Map.entry("finance_purchaseexpense", List.of("采购费用", "采购费用单")),
            Map.entry("finance_salestatement", List.of("客户对账单", "对账单", "对账")),
            Map.entry("finance_purchasestatement", List.of("供应商对账单", "对账单", "对账")),
            Map.entry("acquisition_task", List.of("获客任务", "采集任务", "获客")),
            Map.entry("acquisition_result", List.of("采集结果", "获客结果", "采集")),
            Map.entry("system_approvaltask", List.of("审批任务", "审批", "待办审批")),
            // ===== 批次1补登关键词（2026-09-28）=====
            Map.entry("basedata_qctemplate", List.of("质检模板", "质检", "检验", "QC", "检具")),
            Map.entry("production_qcrecord", List.of("检验记录", "质检记录", "检验", "QC")),
            Map.entry("crm_salestargetemployee", List.of("销售目标分配", "销售目标", "个人目标")),
            Map.entry("crm_warningconfig", List.of("客户跟进预警配置", "跟进预警", "预警配置")),
            Map.entry("finance_receipt", List.of("收款", "收款单")),
            Map.entry("system_notification", List.of("通知", "消息"))
            // 注：租户 / 部署版本属平台级数据，已从通用 query_ 查询登记移除，改由
            //     platform_data_query 工具（平台通道、仅平台管理员）承担，故此处不再挂关键词。
    );

    /** 模块级宽泛关键词：命中则整个模块的实体工具进入候选（如"销售"→全部sale_*） */
    private static final Map<String, String> MODULE_KEYWORDS = Map.of(
            "销售", "sale_",
            "采购", "purchase_",
            "生产", "production_",
            "财务", "finance_",
            "库存", "inventory_",
            "仓库", "inventory_"
    );

    /**
     * 特殊功能工具：不参与关键词预筛，始终保留在候选清单中（数量少，不构成payload负担）。
     * 与ToolRegistry的权限开关过滤叠加生效——这里保留，是否暴露仍由开关决定。
     */
    private static final Set<String> ALWAYS_TOOLS = Set.of(
            "image_search", "image_read", "document_read", "print_template_fields",
            "image_create_order", "image_create_bom", "bom_file_read", "bom_import_rows",
            "create_sale_order_draft",
            // 【二期 2026-10-10】照文件建工单/建工艺路线：话术总带实体词（"工单""工序""工艺路线"），
            // 必须常驻候选，否则会被关键词预筛整体丢弃 → AI 看不到工具 → 只在文字里回"已建好"的假成功
            "create_work_order_draft", "create_routing_with_operations",
            // 多步骤任务链必须常驻：用户的"先建A再建B"话术不一定命中某个实体关键词，
            // 一旦被预筛丢弃，AI 就会退回一条一条调工具（每步弹一张卡）或声称已全部完成
            "task_plan_execute",
            // 批量创建必须常驻：它不以 query_/create_/update_/delete_ 开头，
            // 只要用户话术命中任一实体关键词（如"BOM"命中 production_bom）就会被预筛整体丢弃，
            // 导致"提示词里写了 batch_create_records、模型却说没有这个工具"（2026-10-28 用户实测）
            "batch_create_records",
            "system_assign_user_roles", "system_assign_role_permissions",
            "report_list_datasources", "report_create_custom",
            // 固定报表/聚合排行（2026-11-27）：用户话术"查销售订单明细报表""物料出库排行"必然带实体词，
            // 不常驻就会被关键词预筛整体丢弃 → 模型声称"没有 report_query 工具"（复测已复现）。
            // 是否真正暴露仍由 ToolRegistry 的只读白名单与执行期菜单权限判定。
            "report_query", "report_aggregate_query",
            // work_operation_action（工序操作）：话术几乎总带实体词（"工单""工序"），必须常驻候选，
            // 否则会被关键词预筛整体丢弃 → AI 看不到工具 → 只在文字里回"已为你执行"的假成功
            "work_operation_action",
            // open_page（界面导航）必须常驻：它的调用语料里几乎总带实体词（"打开销售订单""打开即时库存"），
            // 一旦参与关键词预筛就会被整体丢弃 → AI 看不到工具 → 只在文字里回"已为你打开"的假成功
            "open_page",
            // platform_data_query（平台级数据：租户/部署版本）必须常驻：
            // 用户话术"查租户/查部署版本"会命中其它实体关键词，若参与预筛会被丢弃；
            // 它是否真正暴露仍由 ToolRegistry 的开关与平台管理员判定决定。
            "platform_data_query",
            // 看板类工具（2026-11-07）：命名是 query_board_xxx，实体段是 board_metrics 等，
            // 不在 TOOL_KEYWORDS 里；用户问"看板毛利率/净利润/成本缺失"时话术命中其它实体词
            // （如"费用"命中 finance_expense），整组看板工具会被预筛丢弃 → 小楷看不到工具。
            // 这 5 个必须常驻，是否真正暴露仍由 ToolRegistry 的看板开关与菜单权限决定。
            "query_board_metrics", "query_board_sources", "query_board_formula",
            "query_board_drill", "board_save_formula_card"
    );

    /**
     * 常驻查询候选：客户/物料是跨实体高频关联（查订单要客户名、下单要物料）；
     * 物料分类与计量单位是"新建物料/BOM 前置核查"的必需现状。
     * 固定保留这些只读工具，避免预筛过度收窄导致 AI 无法补查关联信息、只能猜编码。
     */
    private static final Set<String> BASELINE_QUERY_TOOLS = Set.of(
            "query_basedata_customer", "query_basedata_material",
            // 新建物料/BOM 的前置核查必需：分类编码只能来自系统查询结果。
            // 用户话术往往只说"建BOM"而不说"分类"，若不常驻会被关键词预筛丢弃，
            // 模型只能猜分类编码或反过来向用户索要编码（2026-10-28 用户实测）。
            "query_basedata_materialcategory",
            // 物料基本单位是必填项，单位清单同样属于创建前置现状。
            "query_basedata_unit"
    );

    private final ToolRegistry toolRegistry;
    private final com.erp.ai.bill.BillActionRegistry billActionRegistry;
    // 单据"人话标识"解析（确认卡片/执行结果/播报里用单号、客户名，不用内部主键）
    private final com.erp.ai.bill.BillCodeResolver billCodeResolver;
    /** 明细行编辑接入表（bill_item_list / bill_item_update 用；未接入的单据会明确"暂不支持"） */
    private final com.erp.ai.bill.BillItemRegistry billItemRegistry;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final CacheService cache;
    private final AiConfirmService aiConfirmService;
    private final AiTokenUsageService aiTokenUsageService;
    /** 小楷助手积分（2026-10-24）：对话前余额拦截、对话后按 tokens 扣减 */
    private final AiCreditService aiCreditService;
    // 会话记忆轮数 / 严谨度等对话能力设置（设置中心「对话能力」卡片）
    private final AiConfigService aiConfigService;
    // @feature FEAT-AIKB-001 知识库检索服务
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    // 数据导入工具：实体中文名（确认卡片摘要展示用）
    private final com.erp.ai.imports.ImportPermissionResolver importPermissionResolver;
    // 工序操作工具：确认卡片摘要需读一次工序（工单号/工序名），才能说清"对哪张工单的哪道工序做什么"
    private final com.erp.production.service.WorkOrderOperationService workOrderOperationService;

    @Resource
    private AiAdapterResolver adapterResolver;

    /** 会话历史数据库持久化（2026-10-20 起替代 Redis 存储） */
    @Resource
    private com.erp.ai.service.AiChatHistoryStore aiChatHistoryStore;

    // 思考轮数兜底上限（2026-11-13 由 10 改 60）：思考的停止条件已改为"上下文预算"——
    // 上下文没满就继续想（见下方 while 处检查），本值仅防模型反复空转工具烧积分的极端死循环，
    // 正常业务链路（如"今年vs前年销售/出库对比+备货分析"需要 10+ 次工具调用）不再被步数卡断。
    private static final int MAX_STEPS = 60;
    // 会话记忆轮次上限：注入模型的窗口取最近 N 轮"用户+小楷"对话（N 轮 = 2N 条消息）。
    // 2026-10-06 由 10 提到 20；2026-10-10 起改为设置中心「对话能力-会话记忆」可调（默认 60，范围 20~80）。
    // 2026-10-13 起存储层改为永久保留（saveHistory 不再裁剪、不再 60 分钟过期），本窗口只决定
    // "每次请求注入模型多少轮"——老对话永久留存，只是太老的不再灌进模型上下文（防撑爆）。
    // 必须与网关意图识别链路（AiGatewayService）口径一致：两者共用同一会话键。
    private int maxHistory() {
        try {
            return aiConfigService.getChatHistoryRounds();
        } catch (Exception e) {
            return 60;
        }
    }

    /** 上下文上限（tokens），读设置中心配置；异常回退 128000（与默认值一致） */
    private int chatContextLimit() {
        try {
            return aiConfigService.getChatContextMaxTokens();
        } catch (Exception e) {
            return 128000;
        }
    }

    /** 思考强度归一（2026-11-22 三档）：只认 light/high/max；旧前端只传 deepThinking 布尔时按 true=high / false=light 兜底 */
    private String normalizeThoughtLevel(String level, Boolean deepThinking) {
        if (level != null) {
            String l = level.trim().toLowerCase(java.util.Locale.ROOT);
            if ("light".equals(l) || "high".equals(l) || "max".equals(l)) return l;
        }
        return Boolean.TRUE.equals(deepThinking) ? "high" : "light";
    }

    /** 本次请求的上下文窗口覆盖（2026-11-22 三档）：null/越界回退设置中心全局默认；范围 8000~1000000 */
    private int resolveContextLimit(Integer override) {
        if (override != null && override >= 8000 && override <= 1000000) return override;
        return chatContextLimit();
    }

    /**
     * 估算一段文本的 tokens（仅用于上下文裁剪与使用率展示，不参与计费）：
     * 中日韩字符约 1 字 1 token，其余字符约 4 字符 1 token。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int wide = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x2E80) wide++;
            else other++;
        }
        return wide + (other + 3) / 4;
    }

    /** 估算工具清单 JSON 的 tokens（工具定义每轮都会随请求发给模型，必须算进上下文预算） */
    private int estimateJsonTokens(List<Map<String, Object>> tools) {
        if (tools == null || tools.isEmpty()) return 0;
        try {
            return estimateTokens(mapper.writeValueAsString(tools));
        } catch (Exception e) {
            return tools.size() * 80;
        }
    }

    private static final String SYSTEM_PROMPT = """
            你是智能ERP系统的AI助手。你拥有高级的分析和判断能力，可以帮助用户查询、分析、创建和管理ERP系统中的业务数据。

            ## 你的角色
            你不是一个简单的接口调用者，而是一个具备独立思考能力的智能业务助手。你既能查询和分析数据，也能帮用户创建订单、导入BOM、识别图片并完成业务操作。你的目标是理解用户的业务需求，通过调用工具获取数据或执行操作，然后进行深度分析和判断，最终给出有价值的业务洞察和操作结果。

            ## 思考方式（ReAct模式）
            你应该按照以下循环进行思考和行动：

            1. 💭 **思考**：分析用户的问题，理解用户需要什么数据或操作，需要哪些工具
            2. 🛠️ **行动**：调用最合适的工具获取数据或执行操作
            3. 📋 **观察**：分析工具返回的数据或执行结果，判断是否足够回答用户的问题
            4. 🔄 **循环**：如果数据不够或需要更多信息，回到第1步继续思考和调用工具
            5. 📝 **回答**：当收集到足够的数据或完成操作后，给出综合分析和最终答案

            ## 核心规则
            1. 所有数据、数字、编码、字段名一律来自工具返回，不编造、不心算、不估算。
            2. 没拿到真实执行结果，不得说"已完成/已提交/已生效"。
            3. 工具失败时如实转述系统返回的真实原因，不掩饰、不编理由。
            4. 对用户指代单据用单号/名称，不报内部数字 ID。
            5. 不动密钥与 AI 自身配置；删除类操作提醒不可恢复。
            6. 不越权限查改数据。
            说明：系统中标记为"保密"的敏感数据已在底层被自动过滤，任何工具都查不到；
            用户问为什么查不到某些物料或数据时，可说明"系统中部分数据受权限保护，无法查询"。

            ## 能力一：数据查询（只读）
            - 客户/供应商/物料/员工/部门等基础资料查询
            - 销售/采购/库存/生产/财务/CRM等业务单据查询
            - 选用 query_ 开头的工具
            - query_inventory：查库存时物料条件传编码或名称，多个物料用逗号一次传入（如 "G10401,D0001"）；
              不带物料条件时返回的是全量库存列表；物料没有库存记录时返回为空。

            ## 能力二：图片识别（视觉）
            - **image_search**：以图识图，识别图片中的物料并查询相关单据和库存
            - **image_create_order**：图片下单，识别图片中的商品后自动创建销售订单草稿
            - **image_create_bom**：图片识别BOM表/图纸，解析父子件结构后创建BOM
            当用户上传图片时，根据用户意图选择：
            - 用户问"这是什么物料/帮我查查/有没有库存" → image_search
            - 用户问"帮我下单/帮我开单/这个产品订100个" → image_create_order
            - 用户问"帮我把这个BOM录入系统/识别这张BOM图" → image_create_bom

            ## 能力三：BOM转换与导入
            - **bom_file_read**（只读）：读取用户上传文件的内容（所有工作表/表头/数据行）；
              表格、制造单、物料表、工艺表都可以读，由你理解表头与数据结构并转换成系统标准BOM行。
            - **bom_import_rows**（写，需用户确认）：按系统标准BOM结构导入
              （母件编码/子件编码/用量为必填，多级用层级+父件编码；物料分类可给分类编码或分类名称）。

            ## 能力四：快速下单（文本）
            - **create_sale_order_draft**：用户粘贴客户微信消息、聊天记录或口述下单内容时调用，
              从输入中提取客户名称、物料（编码或名称）、数量，价格有则提取；创建的是草稿订单（进入审批流程）。

            ## 能力五：权限管理（仅系统管理员）
            - **system_assign_user_roles**：给员工追加角色（合并，不删除已有角色）
            - **system_assign_role_permissions**：给角色追加菜单/功能权限（合并，不关闭已有权限）
            - 只支持"开启/追加"，不支持移除；用户要求移除时，引导其前往设置中心操作

            ## 能力六：自定义报表
            - **report_list_datasources**：查询可用的数据源和字段清单
            - **report_create_custom**：根据用户需求设计报表配置并创建（字段必须在数据源清单内）
            - 【能力事实·判断能不能做以此为准】自定义报表不是"单数据源"：除主数据源外，config.extraJoins 可把
              **任意白名单数据源**（含明细表）按任意同类型字段值对接并挂载聚合（一对多会先按关联键分组，不会放大金额）。
              describe 返回的 joins 为空只表示"没有自动关联"，**不代表不能关联**，不需要系统预先登记。
              引擎实际支持：任意主数据源、多个跨单来源、每个指标各自的筛选条件、同一字段多次聚合并自定义列名、
              自由公式（四则/括号/ROUND/ABS/COALESCE/NULLIF/TOTAL）、结果条件（HAVING）、行下钻、
              相对时间（今年/去年/前年/本月/上月/近N月）与日期分组粒度。
            - 明细/汇总两种模式：用户要「清单/列表/逐单/明细行」→ 明细模式（columns）；
              要「统计/对比/趋势/占比/排名」→ 汇总模式（groupBy+aggregates）。

            ## 能力六点五：数据看板与多来源公式
            - **query_board_metrics**：查询毛利率、净利润、净利率、成本缺失、费用、销售额、库存金额等看板指标；
              返回带时间口径，财务指标附 costStats（成本缺失与异常行数）。
            - **query_board_sources**：查询数据源与字段，用于配置或试算多来源公式（字段名以返回为准）。
            - **query_board_formula**：跨来源公式试算（如「毛利 - 销售费用 - 采购费用 - 税金」「净利率」）；
              公式变量名与 items.name 一致；除 0 或变量无值时返回真实情况，不会自动补 0。
            - **query_board_drill**：按取数项查单据清单（下钻"是哪几张单"）。
            - **board_save_formula_card**（写，需用户确认）：把公式保存到看板卡片；只影响当前登录人的看板配置。

            ## 能力七：系统设置（设置中心）
            你可以在对话里直接操作设置中心的能力（工具名均以 setting_ 开头）：
            - **自定义字段**：setting_custom_field_list / _create / _update / _delete
              （给物料、客户、销售订单等实体加字段，支持必填/列表显示/可搜索/下拉/公式）
            - **系统参数**：setting_config_list / setting_config_set（基础信息、系统参数、主题、消息通知、
              菜单名称、批次编号、物流看板、界面尺寸/界面密度、审批开关等；写操作只允许改**已存在的参数**）
            - **语言管理**：setting_i18n_search / setting_i18n_save
            - **自动编号**：setting_serial_no_list / _update / _delete / _test
            - **打印设置**：setting_print_template_list / _save / _delete / _default / _copy
            - **角色权限**：setting_role_list / _save / _delete（另可用 system_assign_user_roles、
              system_assign_role_permissions 做追加授权）
            - **审批设置**：setting_approval_flow_list / _save / _delete / _toggle、setting_approval_bill_toggle
            - **库存预警 / CRM预警 / 价格策略 / 提成率**：对应 setting_inv_warning_* / setting_crm_warning_* /
              setting_price_strategy_* / setting_commission_rate_*
            - **知识库 / 操作日志**：setting_knowledge_doc_list / _delete、setting_operate_log_list
            说明：写操作只改已存在的参数/记录（key/id 来自查询结果）；无权限的设置工具不会出现在工具清单里。

            ## 能力八：业务单据流程动作
            - **bill_action**：对业务单据执行 提交/审核/驳回/重置草稿/关闭/结案/反结案/作废/反作废/删除。
              参数：billType（如 SALE_ORDER）、billId（单据主键数字 ID，从对应 query_ 工具的查询结果里取）、
              action（动作英文名）、reason（驳回/作废原因）。
              不同单据支持的动作不同，执行不支持的动作时系统会返回该单据支持的动作清单与可下推目标。
              审核/驳回若走审批流，系统会路由到审批引擎并校验"是否当前节点审批人"，不是审批人会被拒绝。
            - **bill_batch_action（批量）**：用户一次要处理**多张同类单据**时使用。
              支持四种动作：submit 批量提交 / approve 批量审核 / delete 批量删除 / reverseApprove 批量重置草稿
              （与列表页"批量操作条"的四个按钮一致）。参数：billType、action、ids（单据主键数组）、reason（可选）；
              单次最多 50 张。逐张执行、逐张返回结果（成功几张、失败几张、每张失败原因），
              个别失败不会影响其它单据，不会整体回滚；执行前会弹确认卡片（单据类型、动作、张数、单号清单）。
            - **批量导出小助手暂不支持**：小助手不能直接生成下载文件，
              需引导用户到对应列表页勾选单据后点「批量导出」。

            ## 能力九：单据动作与下推
            - **bill_action_list**（只读）：查询某张单据支持哪些动作、可下推哪些目标。
            - **bill_pushdown**：把源单下推生成下游单据草稿。已接入 **16 条**有后端生成接口的链路
              （如 销售订单→销售出库单、采购订单→采购入库单、生产工单→生产领料单/生产入库单/工序汇报单、
              委外订单→委外发料单、打样单→BOM）；另有 **14 条**系统当前是"界面选单预填"方式（后端无生成接口），
              调用时会返回引导路径（到对应界面用【选单】新建）。下推生成的是草稿/待审核单据，不会自动提交。
            - **权限**：动作与下推都会按当前账号的角色权限校验；无权限时系统返回"需要哪张单据的菜单权限"。

            ## 能力十：数据导入
            - **import_template_list**（只读）：查询可导入实体清单与某实体的标准模板列。
            - **import_file_read**（只读）：读取用户上传的 Excel/CSV 内容（支持 csv/xlsx/xls，单表最多 60 行预览）。
            - **import_file**（写）：把用户上传的文件导入指定实体；**可导入实体共 55 类**（客户/供应商/物料/物料分类/
              计量单位/仓库/货位/部门/员工/科目/币种/付款条件/价格等级/结算方式/银行账户/BOM、线索/商机/投诉/调研、
              销售 6 单、采购 6 单、库存 5 单、财务 5 单、生产 8 项、委外 5 单；**以 import_template_list 返回的可导入清单为准**）。
            - **import_rows**（写）：按标准模板列导入结构化行；**本期「结构化行」只支持 5 类实体**
              （customer/supplier/saleOrder/purchaseOrder/material），其余实体用 import_file 上传文件导入。
            - **material_import_check**（只读）→ 用户选策略 → **material_import_confirm**（写）：物料导入为两段式，
              由用户明确选择"跳过已存在（SKIP）/覆盖已存在（OVERWRITE）"策略；
              列名不在模板中或缺必填列时系统返回差异、不做映射猜测；单次预览上限 60 行，超限时需分批或走界面导入。

            ## 能力十一：界面导航（只读）
            - **open_page**：帮用户打开系统里的某个页面（带路），不修改任何数据。
              用户说「打开/进入/跳到/切换到 XX 页面」时调用，如"打开设置中心""打开销售订单"。
            - page 填**具体页面**的菜单名（设置中心/销售订单/即时库存/审批设置/自定义字段设置…）；
              菜单分组名（基础资料/销售管理/系统管理/采购管理）不是页面；
              页面名来自系统菜单，不是网址或路由路径。
            - 用户说"打开某张单据"时：page 填该单据的列表页名（如"销售订单"），
              recordId=单据主键的数字 ID（如销售订单的 orderId=258；单号不是主键，只有单号/日期/客户等条件时
              先用对应 query_ 工具查到那一张单、从结果里取主键），
              entityType 填实体英文名（如 SaleOrder，与查询工具结果里的 entityName 一致）。
            - 带条件打开页面（如「打开销售看板」「看本月的销售订单」）时，除 page 外再传 filters
              （如 {"status":"草稿"}、{"startDate":"2026-07-01","endDate":"2026-07-31"}、{"customerId":15}），
              前端会把条件套到列表页的筛选上（等价于用户自己点看板卡片穿透）。
            - 调用后本轮即可结束，用一句话告诉用户"已为你打开【XX】"；前端找不到该页面或无权限时会另行提示用户。
            - 界面导航不能替代操作：用户要改数据/走流程时，仍用对应的查询或写操作工具。

            ## 能力十二：单据明细行（改单价/数量等）
            - **bill_item_list**（只读）：看某张单有哪些明细行、每行的物料/数量/单价/金额是多少。
            - **bill_item_update**（写，需用户确认）：改其中一行，用 itemId（明细行ID）或 lineNo（行号）指明行，
              patch 里给要改的字段；billId=单据主键数字 ID（非单号）。金额/税额/合计由系统自动重算，无需传。
            - 支持明细行编辑的单据共 **33 种**：
              销售 6（销售订单、销售报价单、销售合同、销售出库单、销售退货单、销售发票）、
              采购 6（采购申请单、采购订单、采购合同、采购入库单、采购退货单、采购发票）、
              库存 5（库存调拨单、库存盘点单、其他入库单、其他出库单、生产领料单）、
              打样 2（打样单、免费样品单）、
              生产 4（生产工单、生产入库单、生产组装单、生产拆卸单）、
              委外 5（委外加工单、委外发料单、委外入库单、委外退料单、委外补料单）、
              财务 5（收款单、付款单、销售预收款单、采购预付款单、费用分摊单）；其它单据暂不支持改明细。
            - 各单据可改字段不同（传了该单不支持的字段会被系统拒绝）：
              销售 6 张与采购订单、采购合同=单价·数量·税率·折扣率；采购申请单=只数量；采购入库单、采购退货单=单价·数量；
              采购发票=单价·数量·税率；委外加工单=数量·单价·税率；委外入库单=数量·加工费；委外发料单、委外退料单、委外补料单=只数量；
              库存调拨单、其他入库单、其他出库单、生产领料单=数量·单价；库存盘点单=盘点数量·单价；打样单=数量·单价；免费样品单=只数量；
              生产工单=需领数量；生产入库单=数量·合格数量·不合格数量·单价；生产组装单、生产拆卸单=数量·单价；
              收款单、付款单=金额；销售预收款单、采购预付款单=金额·退回金额；费用分摊单=分摊金额。
            - 已生效/已结案的单据系统会拒绝修改并返回原因。

            ## 能力十二·补：看图与读文档（只读）
            - **image_read**：把用户上传图片里的内容（文字 / 表格 / 字段名 / 版面）原样读成文字。
            - **document_read**：把用户上传的 Word/PDF/txt/md 读成文字（Word 连**表格**一起读），
              并把文档内嵌图片抽出来存成系统图片地址、返回在 images 字段里（后续建资料时可带上）。
            - **Excel/CSV 读取是全量的**：所有行所有列不截断；Excel 里的嵌入图片会**按行抽出**返回在
              每个工作表的 images 字段（row=第几行、url=图片地址）——需要图片里的信息
              （物料照片上的标注、图里的参数、设计图）时，用 image_read 逐张读 url 即可取到。
            - 与其它工具的**分工**：找相似物料 → image_search；按图下单 → image_create_order；
              按图录BOM → image_create_bom；Excel/CSV 表格 → import_file_read；**图片内容理解 → image_read；
              文档（Word/PDF）内容 → document_read**。
            - 用户要把"图片 / 文档形式"的工单、单据、资料录进系统时（如客户发来的 Word 工单、报价单截图）：
              先 image_read / document_read 读出内容 → 与用户核对 → 再用 create_ / bill_ 等工具建资料、建单据；
              文档/表格里的图片可用返回的 images 地址带入资料（如物料图片）。

            ## 能力十二·补2：照着截图/文档建打印模板
            - 工具链：image_read（截图）/ document_read（Word/PDF）读出内容 →
              **print_template_fields**（传单据类型，如 SALE_QUOTE）返回该单据类型的**可用字段**与内容格式 →
              按图/文里的列组织 templateContent（HTML，普通字段 {{key}}，明细行整行用 {{#items}}…{{/items}} 包住）→
              与模板名称/单据类型/纸张/方向一起给用户确认后，调 setting_print_template_save 落库（写操作会弹确认卡片）。
            - 模板字段只能用 print_template_fields 返回过的字段 key；
              图/文里有但可用字段里没有的列，打印时取不到值。

            ## 能力十二·补3：照文件建工单 / 建工艺路线（自动匹配）
            - **create_work_order_draft**：创建**生产工单草稿**。必填 物料（名称或编码）+ 数量；
              仓库/部门/工作中心/工艺路线都可以给**名称**，系统会自动匹配成系统对象并选上；
              工艺路线不传时，系统自动取该物料的**主工艺路线**（确认卡片里会显示用的是哪条）；
              物料系统里没有时会自动先建物料（可一并给规格型号/单位/图片地址）。
              只存草稿，不提交、不审核 —— 提交由用户在【生产工单】里自己点（提交生效后系统按工艺路线自动生成工序）。
            - **create_routing_with_operations**：创建**工艺路线主档（含工序明细）** —— 用户说"建工序/建工艺路线"时用它。
              每行工序至少给 operationName；工作中心可给名称（自动匹配）；工序号与编码由系统自动生成。
              若用户要建的是普通物料/客户等主档，用对应的 create_ 通用工具。
            - 照 Word/图片里的生产任务建单时，工具会把读到的**物料、数量、客户、交期**等与系统匹配，
              返回 matched/candidates；匹配到多条或找不到时候选清单随结果返回。
            - 从带图文件建物料时：document_read/import_file_read 返回的 images 按行标注图片地址（row 对应物料行），
              该行图片地址可放进 `imageUrls`（多张用英文逗号分隔），第一张放 `mainImage`，随 create_basedata_material 带入。

            ## 能力十三：基础资料新增/修改/删除（通用 CRUD）
            - **create_ / update_ / delete_ 开头的工具**：新增、修改、删除基础资料（客户/供应商/物料/员工/部门/仓库等）。
            - 编码（如 materialCode/customerCode）由系统按"自动编号"规则自动生成，调用时不传编码字段；
              只有用户主动指定了编码时才传。编码重复/必填等业务错误由系统在确认执行时返回。
            - 建物料时的图片字段：`imageUrls`（多张图片地址，英文逗号分隔）与 `mainImage`（主图地址），
              地址取 document_read/import_file_read 返回的 images（按行抽出的图，取对应物料行那一张）或用户上传图片的地址。
              例：{materialName: '不锈钢支架', model: '100×50', baseUnitId: 1, imageUrls: '/uploads/a.jpg,/uploads/b.jpg', mainImage: '/uploads/a.jpg'}。
            - `baseUnitId` 是**基本单位的主键**（数据库必填），取值来自计量单位查询结果。

            ## 能力十三·补：批量创建（可跨表，一张确认卡写完）
            - **batch_create_records**（写，需用户确认）：用户一次要建多条数据，或要"一条龙"建
              单位 / 分类 / 物料 / BOM（含子件）/ 工艺路线（含工序）/ 单据时使用。
            - 参数 entries：数组，每项 {entity, data, items?, operations?}。
              · entity 取类型全名（如 basedata_unit / basedata_materialcategory / basedata_material /
                production_bom / production_routing / sale_order）；
              · data 是该记录的字段（与单个创建一致；编码由系统自动生成）；
              · 单据的物料明细放 items（每行物料 + 数量）；BOM 的子件放 items（每行子件 + 用量，用量大于 0）；
                工艺路线的工序放 operations（每行至少 operationName）。
            - BOM 口径：data 里写母件（materialCode 或 materialName）、BOM分类（bomCategory）、版本号（bomVersion）、数量（quantity）；
              BOM 编号由系统自动生成（不传 bomCode）；默认BOM/主BOM由系统判定（不传 isDefault/isPrimary）。
              母件和子件都可以写前面刚建物料的名称/编码，系统按库内实况解析。
            - **不限制条数**；按数组顺序逐条创建。有依赖时把上游放前面（单位/分类 → 物料 → BOM → 工艺路线 → 单据），
              后面的条目可以直接写前面刚建记录的名称/编码，系统会自动解析成对应主键。
            - 只弹**一张**确认卡；逐条返回成功/失败原因，单条失败不影响其它条。
            - 例：一次建「单位 个 → 分类 五金件 → 物料 六角螺丝（子件）→ 物料 螺丝组件（母件）
              → BOM（母件 螺丝组件，子件 六角螺丝 用量 2）→ 工艺路线（绑定螺丝组件，含车削/铣削）
              → 销售订单（客户 楷煜，买 20 个螺丝组件）」= 一个 entries 数组、一次工具调用、一张确认卡。

            ## 能力十三·补二：多步骤任务链（一张确认卡按顺序跑完）
            - **task_plan_execute**（写，需用户确认一次）：用户的要求需要**按顺序做多件事**时使用，
              例如「先建单位、物料、BOM，再建销售订单」「建完物料后提交审核并下推采购」「先查库存再开单」。
            - 参数：
              · goal：这条任务要达成的目标（用户原话摘要）；
              · steps：按顺序的步骤数组，每项 {title, tool, params, dependsOn}；
              · tool 是系统已有工具名（如 create_basedata_material / batch_create_records / bill_action / bill_pushdown / query_inventory_stock）；
              · params 是该工具的完整参数；
              · dependsOn 标前置步骤（第2步依赖第1步 = dependsOn:[1]）；前置步骤失败时，后续步骤自动标「未执行（前置失败）」，不会硬跑；
              · 后面步骤要引用前面刚建数据的编码/主键时，用占位符：`{{1.code}}`（第1步的编码）、`{{1.id}}`、`{{1.name}}`；
                批量创建的多条结果用 `{{1.results.0.code}}`。
              · failPolicy 可选：MARK_DOWNSTREAM（默认）/ SKIP / STOP。
            - 只弹一张确认卡；确认后按顺序执行，逐步返回每步成功/失败原因；单步失败不会让已成功的步骤回滚。
            - 例：「先建物料 CP001，再给它建 BOM（子件 LS001 用量 2），最后建销售订单」=
              steps=[{title:'创建物料',tool:'create_basedata_material',params:{data:{materialCode:'CP001',materialName:'螺丝组件',unitName:'个'}}},
                     {title:'创建BOM',tool:'create_production_bom',params:{data:{materialCode:'CP001',bomCategory:'标准'},items:[{materialCode:'LS001',quantity:2}]},dependsOn:[1]},
                     {title:'创建销售订单',tool:'create_sale_order',params:{data:{customerName:'楷煜'},items:[{materialCode:'CP001',quantity:20}]},dependsOn:[2]}]。

            ## 能力十四：工序操作（车间执行）
            - **work_operation_action**：对生产工单的某道工序执行「工序操作面板」上的操作（写，需确认）。
            - 定位工序二选一：**operationId**（工序主键），或 **workOrderNo（工单号）+ operationName（工序名称）**；
              工单号不确定时先查工单。工序主键只作工具参数，对用户说话用**工单号+工序名**。
            - action 取值（与面板 7 个操作一一对应）：
              · **flow** 状态流转：再传 **statusAction**，取值为「派工/开工/暂停/恢复/完工/数量报工/检验通过/检验退回/取消/重新派工/更新计划」；
                派工/重新派工/更新计划可带 assigneeId（员工ID）、planEndDate（yyyy-MM-dd）；
                「数量报工」传 quantity（产出数）、rejectedQuantity（不良数）、inputQuantity（投入数，可空）；取消需在 remark 填取消原因。
              · **rework** 返工（回退确认）：传 quantity（本次回退数）；只登记数量，**不改工序状态**。
              · **scrap** 报废（判废确认）：传 quantity（本次判废数）；只登记数量，**不改工序状态**；判废量决定能否下推补料。
              · **pushSupplement** 一键推补料：按"尚未下推的判废量 × 成品主BOM 用量"生成补料领料单草稿，返回单号。
              · **pushReceipt** 一键推入库：传 quantity（入库数量），生成成品入库单草稿；数量不得超过剩余可入库量。
              · **qc** 录入质检：仅工序处于「完工待检」时可用；按该工序的质检模板判定（首检/抽检/全检），
                可传 items（逐项实测值）、inspectQty/qualifiedQty/defectQty；判定不合格时传 handleType（补料/返工回退/报废），
                选「返工回退」还要传 handleOperationName（目标工序名称）。
              · **notify** 发通知：传 receiverEmployeeIds（员工ID数组）+ 可选 content，给所选员工发系统通知
                （未在员工档案绑定登录账号的员工会被跳过）。
            - 系统侧规则：工艺路线开启「按顺序执行」时，前道工序未完工系统会拒绝开工/报工并返回原因；
              需质检的工序完工后先落「完工待检」，质检判定合格才算「已完工」，未通过前下道不能开工；
              终态的工序（已完工/已取消）除「重新派工」外不能再操作。
            - 操作前会弹确认卡片（写明工单、工序、动作与改什么值）；操作人取当前登录账号。

            ## 能力十五：平台级数据查询（仅平台管理员）
            - **platform_data_query**（只读）：查询平台管理数据，**仅平台/超级管理员可用**，且需后台开启开关。
              type 只能取两个值：
              · **tenant** —— 租户（客户公司）清单（编码/名称/管理员账号/用户数/最大用户数/状态/到期日/版本）；
              · **deploy_version** —— 部署版本库（版本号/包类型/版本说明/包大小/操作人/时间）。
            - 租户 / 部署版本属于平台级数据，不在业务对象清单里（那些对象登记在租户库，用 query_ 工具查会报错或查空）；
              非平台管理员或后台开关关闭时，系统返回无权限/已关闭提示。部署版本的"版本说明"源数据可能为空。

            ## 写操作确认机制
            所有写操作（创建订单、提交/审核单据、改明细等）执行前，系统会生成一张"待确认卡片"，
            用户在卡片上点确认、或直接回复"确认"后才会真正执行；
            只有真实调用了写工具、系统返回"待确认卡片"之后，才能请用户在卡片中确认。

            ## 工具选择指南
            **先判断用户意图类型（创建 or 查询），再选工具：**
            - 用户说"下单/开单/下一张/来一单/创建/新增/帮我下一单"等 → 这是**创建意图**，选择 create_ 开头的工具
            - **新增带明细的业务单据**（销售出库单 / 销售订单 / 采购订单等）时，items 在同一次调用里给出：
              每行至少「物料编码或名称 + 数量」，有单价一并给；系统会拒绝没有明细的单据，不会保存空单。
            - 用户说"查询/看看/有哪些/统计/列表/多少"等 → 这是**查询意图**，选择 query_ 开头的工具
            - 例：'随便下一张销售订单'=创建意图→create_sale_order_draft；'查一下销售订单'=查询意图→query_sale_order

            - **客户相关问题** → 选择名称包含 "customer" 的工具（客户档案、客户列表等）
            - **供应商相关问题** → 选择名称包含 "supplier" 的工具（供应商档案、供货厂商等）
            - **物料/商品/产品相关问题** → 选择名称包含 "material" 的工具
            - **库存/仓库相关问题** → 选择名称包含 "inventory" 或 "stock" 或 "warehouse" 的工具
            - **销售订单/销售单相关问题** → 选择名称包含 "sale_order" 的工具
            - **采购订单/采购单相关问题** → 选择名称包含 "purchase_order" 的工具
            - **员工/人事相关问题** → 选择名称包含 "employee" 的工具
            - **部门相关问题** → 选择名称包含 "department" 的工具

            特别注意：客户(customer)和供应商(supplier)是完全不同的实体，请务必区分清楚！

            ## 分析能力要求
            - 对获取的数据进行统计分析（总数、趋势、占比等）
            - 识别数据中的异常或重要信息
            - 提供业务建议或决策支持
            - 当数据较多时，进行归纳总结，突出重点

            ## 可用工具
            系统为你提供了以下工具来获取ERP数据和执行业务操作，请根据上述选择指南挑选最合适的工具。

            ## 查询条件
            按**状态、单据日期区间、金额区间、指定客户**等条件筛选时，用查询工具的 **filters** 参数传条件
            （字段名与用法见工具说明）；单次返回有上限（默认 100、最多 500），超出上限的数据通过翻页获取。

            ## 分页查询说明
            - 工具查询默认返回前100条数据，如果用户要查"所有"数据，返回结果中的total字段会告诉总共有多少条
            - 如果total超过当前返回的条数，请告知用户总数，并询问是否需要查看下一页
            - 可以通过传page和size参数来翻页，比如 page=2, size=100 获取第101-200条
            """;    /** 旧签名保留（无数据权限上下文，兼容既有调用） */
    public List<AgentStep> runAgent(UnifiedRequest request, Consumer<AgentStep> stepCallback) {
        return runAgent(request, null, stepCallback);
    }

    /**
     * 系统提示词动态构建：静态角色设定 + 当前时间上下文 + 知识库检索结果。
     * 知识库片段由调用方检索后注入，帮助模型理解业务语义（如字段含义、单据关联规则）。
     */
    private String buildSystemPrompt(String knowledgeContext) {
        return buildSystemPrompt(knowledgeContext, null, null);
    }

    /**
     * 系统提示词动态构建：静态角色设定 + 当前时间上下文 + 本次上传附件/图片清单 + 知识库检索结果。
     * 附件与图片清单必须注入，否则大模型不知道用户上传了什么，会凭空调用"需要文件/图片"的工具。
     */
    private String buildSystemPrompt(String knowledgeContext, List<Map<String, String>> files, List<String> imageUrls) {
        return buildSystemPrompt(knowledgeContext, files, imageUrls, null);
    }

    /**
     * 在上一版基础上增加「本次上下文」注入（小助手刚语音提醒过的待审批单据等）。
     * context 为 null 或字段缺失时整段不注入 → 行为与升级前完全一致（守则2.7.2-2 降级要求）。
     */
    private String buildSystemPrompt(String knowledgeContext, List<Map<String, String>> files,
                                     List<String> imageUrls, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        sb.append("\n\n## 当前时间\n")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("（服务器本地时间，星期").append("日一二三四五六".charAt(LocalDateTime.now().getDayOfWeek().getValue() % 7))
                .append("）。回答时间/日期相关问题时以此为准，涉及今天、本月、上个月等相对时间词时按此换算。");
        if (files != null && !files.isEmpty()) {
            sb.append("\n\n## 本次用户上传的附件（真实存在，可直接引用）\n");
            for (Map<String, String> f : files) {
                if (f == null) continue;
                String name = f.getOrDefault("name", "未命名文件");
                String url = f.getOrDefault("url", "");
                if (url.isBlank()) continue;
                sb.append("- 《").append(name).append("》 fileUrl=").append(url).append("\n");
            }
            sb.append("\n附件使用规则（必须遵守）：\n")
                    .append("1. 用户说\"这个文件/这份文件/附件/我上传的\"时，指的就是上面列出的附件。\n")
                    .append("2. 调用需要文件路径的工具（如 bom_file_read）时，fileUrl 必须原样取自上面的附件列表，严禁编造或拼接路径。\n")
                    .append("3. 如果用户要求处理文件，但上面的附件列表为空，**不要调用任何工具**，直接请用户先上传文件。\n")
                    .append("4. **必须先读文件再回答**：Word/PDF/txt/md 用 document_read 读内容（连表格与文档内图片一起）；"
                            + "Excel/CSV 表格用 import_file_read（BOM 类文件用 bom_file_read）读内容；"
                            + "读出内容后再向用户复述/处理。**不读文件就凭空回答、编造字段 = 严重错误**。\n")
                    .append("5. **文件读取是全量的，不做任何截断**：所有行所有列都要看全；Excel 里的嵌入图片会按行抽出"
                            + "（每个工作表的 images：row=第几行、url=图片地址），需要图片里的信息时用 image_read 逐张读，"
                            + "**禁止说\"图片只有数量、读不到内容\"**；回答必须逐条覆盖所有数据行，不许只报数量或只举例。\n");
        }
        if (imageUrls != null && !imageUrls.isEmpty()) {
            sb.append("\n\n## 本次用户上传的图片（真实存在，可直接引用）\n");
            for (String url : imageUrls) {
                if (url == null || url.isBlank()) continue;
                sb.append("- imageUrl=").append(url).append("\n");
            }
            sb.append("\n图片使用规则（必须遵守）：\n")
                    .append("1. 用户上传了图片并描述需求时，先判断意图再选工具，并把 imageUrl 原样取自上面的列表（严禁编造）。\n")
                    .append("2. 只是想识别图里是什么/找相似物料 → 用 image_search；\n")
                    .append("3. 要求按图下单/开单 → 用 image_create_order；要求按图录入BOM → 用 image_create_bom。\n")
                    .append("4. **要读出图里的文字、表格、字段**（截图、单据、报价单、打印模板、制造单等）→ 用 image_read 读出内容后再说别的；"
                            + "**不读图就凭空回答、编造字段 = 严重错误**。\n")
                    .append("5. 意图不明确时先用一句话确认用户想做什么，不要默认执行写操作。\n");
        }
        if (knowledgeContext != null && !knowledgeContext.isBlank()) {
            sb.append("\n\n## 业务知识参考（来自系统知识库，仅供理解语义，实际数据以工具返回为准）\n")
                    .append(knowledgeContext);
        }
        appendPendingApproval(sb, context);
        appendAnswerMode(sb, context);
        return sb.toString();
    }

    /**
     * 注入「答复呈现方式」（2026-10-22）：明细 = 展示执行过程，汇总 = 只报结果。
     * context 缺失或取值非法时整段不注入 → 行为与升级前完全一致（守则2.7.2-2 降级要求）。
     */
    private void appendAnswerMode(StringBuilder sb, Map<String, Object> context) {
        if (context == null) return;
        Object raw = context.get("answerMode");
        String mode = raw == null ? "" : String.valueOf(raw).trim();
        if ("summary".equalsIgnoreCase(mode)) {
            sb.append("\n\n## 答复呈现方式：汇总（本轮必须遵守）\n")
                    .append("1. 只报最终结果：直接给出结论、关键数字或操作结果，不描述执行步骤、不罗列中间查询过程。\n")
                    .append("2. 答复必须自洽：用户不看过程也能看懂，禁止出现「见上方明细」「见执行过程」「见表格」这类指向过程的说法。\n")
                    .append("3. 需要列举时最多三条要点，其余用一句话概括。\n");
        } else if ("detail".equalsIgnoreCase(mode)) {
            sb.append("\n\n## 答复呈现方式：明细（本轮必须遵守）\n")
                    .append("1. 先给结论，再按需说明关键口径与关键步骤（时间区间、是否含草稿、按哪个字段汇总、做了哪几步查询）。\n")
                    .append("2. 过程说明只写与结论相关的部分，不要复述工具参数。\n");
        }
    }

    /**
     * 注入「小助手刚刚语音提醒过的待审批单据」上下文。
     *
     * <p>用途：语音播报后用户直接说"打开这张单看看 / 帮我审批通过"，模型据此知道"它"指哪张单据。
     * <p>防御：context 缺失、pendingApproval 缺失或类型/编号都为空时**整段不注入**，
     * 保证不传上下文的链路（老前端、其它渠道）行为与升级前完全一致。
     * <p>安全：这里只注入"提示信息"，审批仍走既有 bill_action + 用户确认卡片，不提供任何免确认路径。
     */
    private void appendPendingApproval(StringBuilder sb, Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return;
        }
        Object raw = context.get("pendingApproval");
        if (!(raw instanceof Map<?, ?> pending)) {
            return;
        }
        String billType = pending.get("billType") == null ? "" : String.valueOf(pending.get("billType")).trim();
        String billNo = pending.get("billNo") == null ? "" : String.valueOf(pending.get("billNo")).trim();
        Object idObj = pending.get("billId");
        Long billId = idObj instanceof Number n ? n.longValue() : null;
        if (billType.isEmpty() && billNo.isEmpty()) {
            return;
        }
        sb.append("\n\n## 本次上下文：小助手刚刚语音提醒过的待审批单据\n")
                .append("- 单据类型 billType：").append(billType.isEmpty() ? "（未知）" : billType).append("\n")
                .append("- 单据编号：").append(billNo.isEmpty() ? "（未知）" : billNo).append("\n")
                .append("- 单据ID billId：").append(billId == null ? "（未知）" : String.valueOf(billId)).append("\n")
                .append("\n使用规则：\n")
                .append("1. 用户说\"这张单 / 它 / 刚才提醒的那张 / 这个审批\"时，默认指上面这张单据；")
                .append("用户明确说了别的单号或单据类型时，以用户指定的为准。\n")
                .append("2. 用户要求审批（通过/驳回）时：先调用 bill_action_list 确认该单据支持该动作，再调用 bill_action，\n")
                .append("   billType 与 billId 原样取自上面；执行前系统会弹确认卡片由用户确认。\n");
        log.info("[Agent] 已注入待审批上下文 billType={} billId={}", billType, billId);
    }

    /**
     * 按用户输入预筛工具候选清单（性能优化：缩小发送给DeepSeek的工具payload）。
     * 规则：
     * 1. 特殊功能工具（图片识别/图片下单/图片BOM/BOM文件导入/快速下单/权限/报表/界面导航等）始终保留
     * 2. 客户/物料查询工具常驻候选（跨实体高频关联）
     * 3. 其余CRUD工具按实体关键词+模块宽泛词匹配，仅命中实体进入候选
     * 4. 零命中时回退全量清单（能力兜底，宁可慢不可缺）
     */
    @SuppressWarnings("unchecked")
    /**
     * 工具清单装不进上下文预算时的降级集合（2026-11-27）：
     * 保留常驻工具 + 常驻查询 + 全部只读 query_* + 单据只读清单工具。
     * 丢掉设置中心/写操作/导入等大块头，保证"还能正常对话与查数"，而不是直接报上下文用尽。
     */
    private static List<Map<String, Object>> narrowToolsForBudget(List<Map<String, Object>> allTools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> tool : allTools) {
            Object fn = tool.get("function");
            if (!(fn instanceof Map<?, ?> function)) continue;
            String name = String.valueOf(function.get("name"));
            if (ALWAYS_TOOLS.contains(name) || BASELINE_QUERY_TOOLS.contains(name)
                    || name.startsWith("query_")
                    || "bill_action_list".equals(name) || "bill_item_list".equals(name)) {
                out.add(tool);
            }
        }
        return out;
    }

    private List<Map<String, Object>> filterToolsByUserInput(String userInput, List<Map<String, Object>> allTools) {
        if (userInput == null || userInput.isBlank() || allTools.isEmpty()) {
            return allTools;
        }
        String input = userInput.toLowerCase();

        // 1. 实体关键词匹配
        Set<String> matchedBases = new HashSet<>();
        for (Map.Entry<String, List<String>> e : TOOL_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (input.contains(kw.toLowerCase())) {
                    matchedBases.add(e.getKey());
                    break;
                }
            }
        }
        // 2. 模块级宽泛词（如"销售"→全部sale_*实体）
        for (Map.Entry<String, String> e : MODULE_KEYWORDS.entrySet()) {
            if (input.contains(e.getKey())) {
                for (String base : TOOL_KEYWORDS.keySet()) {
                    if (base.startsWith(e.getValue())) {
                        matchedBases.add(base);
                    }
                }
            }
        }

        // 3. 零命中 → 全量兜底
        if (matchedBases.isEmpty()) {
            log.info("[Agent] 工具预筛无关键词命中，回退全量清单");
            return allTools;
        }

        // 4. 过滤：特殊工具+常驻查询+设置类/单据动作+命中实体的CRUD变体
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> tool : allTools) {
            Map<String, Object> function = (Map<String, Object>) tool.get("function");
            if (function == null) continue;
            String name = String.valueOf(function.get("name"));
            if (ALWAYS_TOOLS.contains(name) || BASELINE_QUERY_TOOLS.contains(name)) {
                filtered.add(tool);
                continue;
            }
            // 设置中心类工具与单据类工具（bill_action / bill_pushdown / bill_action_list）必须常驻候选：
            // 用户话术（如"给物料加个字段"）会命中实体关键词，若参与预筛会被整体丢弃，
            // 导致"有工具但AI看不到"。数量有限，不影响性能目标。
            if (name.startsWith("setting_") || name.startsWith("bill_")
                    || name.startsWith("import_") || name.startsWith("material_import_")) {
                filtered.add(tool);
                continue;
            }
            for (String prefix : new String[]{"query_", "create_", "update_", "delete_"}) {
                if (name.startsWith(prefix) && matchedBases.contains(name.substring(prefix.length()))) {
                    filtered.add(tool);
                    break;
                }
            }
        }
        return filtered;
    }

    /**
     * ReAct主循环（带数据权限上下文）。
     * scope 由 Controller 从认证上下文构建；写操作会被强制确认机制拦截（不直接落库）。
     */
    public List<AgentStep> runAgent(UnifiedRequest request, AiDataScope scope, Consumer<AgentStep> stepCallback) {
        List<AgentStep> steps = new ArrayList<>();
        String sessionId = request.getSessionId();
        // 本轮总耗时：从收到请求到答复完成（USAGE 步骤展示给用户）
        final long roundStartAt = System.currentTimeMillis();

        // 思考强度（2026-11-22 照参考设计三档）：light=标准直答 high=深度思考 max=更长思考空间+更高输出上限。
        // 旧前端只传 deepThinking 布尔：true=high / false=light，行为与升级前完全一致。
        final String thoughtLevel = normalizeThoughtLevel(request.getThoughtLevel(), request.getDeepThinking());

        // ===== 积分拦截（2026-10-24）：余额不足不产生任何AI调用，直接返回中文提示 =====
        if (scope != null && scope.getUserId() != null) {
            AiCreditService.CheckResult credit = aiCreditService.check(scope.getUserId());
            if (!credit.allowed()) {
                log.warn("[Agent] 用户 {} 积分不足，请求被拦截", scope.getUsername());
                AgentStep quotaStep = AgentStep.error(credit.message());
                steps.add(quotaStep);
                if (stepCallback != null) stepCallback.accept(quotaStep);
                return steps;
            }
        }

        // 上下文用量（2026-11-13）：上限 = 本次请求覆盖（三档窗口，越界回退）→ 设置中心全局默认 → 128000；
        // 实际用量在本轮装配完成后回填，用于答复末尾展示使用率
        int contextLimit = resolveContextLimit(request.getContextLimit());
        // 思考强度=极高：步数兜底同步放宽（防空转），上下文预算仍是硬闸（2026-11-22）
        final int maxSteps = "max".equals(thoughtLevel) ? MAX_STEPS * 2 : MAX_STEPS;
        int contextTokens = 0;

        // 圈定本次请求线程内全部AI调用的token统计范围（含工具内嵌的视觉调用）
        AiUsageCollector.begin();
        String usedModel = null;
        try {
            AiChannelAdapter adapter = adapterResolver.resolve(
                    request.getChannel(), request.getProviderId(), request.getModel());
            // 记录本轮实际使用的模型名：积分按模型倍率折算（用户选了哪个模型就按哪个模型计费）
            usedModel = adapter == null ? null : adapter.getModelName();
            if ((usedModel == null || usedModel.isBlank()) && request.getModel() != null) {
                usedModel = request.getModel();
            }
            // 把"用户所选模型"登记到本次请求线程：图片识别等工具据此使用同一个模型，
            // 不再各自去取"后台默认通道"（用户 2026-09-28 明确："我选那个模型就给我用那个模型"）
            AiModelContextHolder.set(adapter);
            // 思考强度登记到本次请求线程：适配器据此放大单轮输出上限（2026-11-22 三档）
            AiThoughtContextHolder.set(thoughtLevel);
            List<Map<String, Object>> conversationHistory = new ArrayList<>();
            List<Map<String, String>> cacheHistory = getHistory(sessionId, scope.getUserId());

            String userInput = request.getUserInput();
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                userInput = "[用户上传了" + request.getImageUrls().size() + "张图片]\n" + userInput;
            }

            // 用户原始输入写入对话上下文（修复：此前仅第1轮临时传参，未进入conversationHistory，
            // 导致第2轮起AI看不到用户原话，把已识别的意图误判成"用户未说明需求"）
            Map<String, Object> currentUserMsg = new HashMap<>();
            currentUserMsg.put("role", "user");
            currentUserMsg.put("content", userInput);

            int step = 0;
            String userDisplayInput = request.getUserInput();

            // @feature FEAT-AIKB-001 检索知识库相关片段注入上下文（失败不阻塞主流程）
            String knowledgeContext = "";
            try {
                knowledgeContext = knowledgeRetrievalService.retrieve(userInput);
                if (!knowledgeContext.isBlank()) {
                    log.info("[Agent] 知识库命中: {} 字符", knowledgeContext.length());
                }
            } catch (Exception e) {
                log.warn("[Agent] 知识库检索异常，跳过: {}", e.getMessage());
            }
            final String knowledgeCtx = knowledgeContext;

            // 工具预筛：第1轮前按用户输入关键词缩小候选清单，多轮复用（避免每轮全量重发+重复读sys_config）。
            // 零命中时回退全量清单（能力兜底）；权限开关过滤仍由 listToolsForFunctionCalling 完成。
            List<Map<String, Object>> fullTools = toolRegistry.listToolsForFunctionCalling(scope);
            List<Map<String, Object>> tools = filterToolsByUserInput(userInput, fullTools);
            log.info("[Agent] 工具预筛: 全量 {} 个 → 候选 {} 个", fullTools.size(), tools.size());

            // ===== 上下文装配（2026-11-13 由"按轮数"改为"按 tokens 预算"）=====
            // 预算 = 系统提示 + 历史对话 + 工具清单 + 当前输入 + 预留输出。
            // 历史从最新一条往前累加，装不下就停（老对话仍永久保留在库里，只是不再灌进模型）。
            int promptTokens = estimateTokens(buildSystemPrompt(knowledgeCtx, request.getFiles(),
                    request.getImageUrls(), request.getContext()));
            int outputReserve = Math.max(1024, aiConfigService.getChatMaxTokens());
            int userTokens = estimateTokens(userInput) + 8;
            int toolTokens = estimateJsonTokens(tools);
            // 工具清单预算保护（2026-11-27）：关键词零命中会回退全量（管理员账号 438 个工具 ≈ 112k tokens），
            // 直接把上下文撑爆 → 用户看到"上下文空间已用完"。工具装不下时改用核心读查询集，保证还能正常对话。
            int toolBudget = Math.max(8000, contextLimit - promptTokens - outputReserve - userTokens - 8000);
            if (toolTokens > toolBudget) {
                int beforeCount = tools.size();
                int beforeTokens = toolTokens;
                tools = narrowToolsForBudget(fullTools);
                toolTokens = estimateJsonTokens(tools);
                log.warn("[Agent] 工具清单超预算：{} 个 / {} tokens > 可用 {}，改用核心读查询集 {} 个 / {} tokens",
                        beforeCount, beforeTokens, toolBudget, tools.size(), toolTokens);
            }
            int historyBudget = Math.max(0, contextLimit - promptTokens - toolTokens - outputReserve - userTokens);
            // 「会话记忆轮数」保留为安全边界（配置仍在，默认 60 轮），与 token 预算取更严格的一方
            int windowSize = maxHistory() * 2;
            int fromIdx = Math.max(0, cacheHistory.size() - windowSize);
            int historyTokens = 0;
            for (int i = cacheHistory.size() - 1; i >= fromIdx; i--) {
                Map<String, String> h = cacheHistory.get(i);
                if (h == null || h.get("role") == null || h.get("content") == null) continue;
                int t = estimateTokens(h.get("content")) + 8;
                if (historyTokens + t > historyBudget) break;
                historyTokens += t;
                Map<String, Object> m = new HashMap<>();
                m.put("role", h.get("role"));
                m.put("content", h.get("content"));
                conversationHistory.add(0, m);
            }
            conversationHistory.add(currentUserMsg);
            contextTokens = promptTokens + toolTokens + userTokens + historyTokens;
            log.info("[Agent] 上下文预算: {} / {} tokens（系统 {} + 工具 {} + 历史 {} / {} 条 + 输入 {}）",
                    contextTokens, contextLimit, promptTokens, toolTokens, historyTokens,
                    conversationHistory.size() - 1, userTokens);

            // 停止原因（2026-11-13）：true=上下文空间用尽（正常出口）false=轮数兜底触发（异常空转）
            boolean contextExhausted = false;
            while (step < maxSteps) {
                // 停止条件（2026-11-13 由"步数"改为"上下文预算"）：已占用 + 本轮输出预留 超过上限
                // 就不再发起新一轮思考。工具查回的数据每轮累加进 contextTokens，判断依据真实占用。
                if (contextTokens + outputReserve >= contextLimit) {
                    contextExhausted = true;
                    log.warn("[Agent] 上下文预算已用尽（{} / {} tokens），第 {} 轮前停止思考", contextTokens, contextLimit, step);
                    break;
                }
                step++;
                log.info("[Agent] 第 {} 轮思考中...", step);

                // 轮次开始先下发"思考中"占位步骤（2026-10-14）：模型长思考期间界面不再只有一个动画，
                // SSE 持续有数据，前端 90 秒无数据看门狗不再误判"服务无响应"。
                // 前端收到后续正式思考步骤时会用本占位收集到的推理增量一并替换展示。
                if (stepCallback != null) {
                    stepCallback.accept(AgentStep.thinkingPlaceholder(step));
                }

                // 用户输入已在循环前写入conversationHistory，此处不再临时传参（避免消息重复）
                String userMsgForAi = null;

                // 图片/BOM 场景不再做本地关键词直连：统一由大模型思考后用 function calling 决定是否调用
                // bom_import / image_search / image_create_order / image_create_bom（附件与图片清单见系统提示）。
                // 历史问题：本地关键词路由会抢在大模型前面直接选工具，并输出写死的假思考文案，
                // 导致"没思考就给固定格式答复"；缺参兜底由 interceptWriteTool 的必填参数校验负责。

                // 流式分步思考（2026-10-14 用户反馈"思考不应该一整个都默认思考中，没有分步骤的吗"）：
                // 模型推理（reasoning_content）逐段实时下发；流式不可用时适配器自动回退非流式，行为同旧版。
                java.util.function.Consumer<String> reasoningDeltaCb = stepCallback == null ? null
                        : delta -> {
                            try {
                                stepCallback.accept(AgentStep.reasoningDelta(delta));
                            } catch (Exception ignore) {
                                // 下发失败不影响主流程
                            }
                        };

                Map<String, Object> aiResult = adapter.chatWithTools(
                        buildSystemPrompt(knowledgeCtx, request.getFiles(), request.getImageUrls(), request.getContext()),
                        userMsgForAi,
                        conversationHistory,
                        tools,
                        reasoningDeltaCb
                );

                String content = (String) aiResult.get("content");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) aiResult.get("tool_calls");
                Object finishReason = aiResult.get("finish_reason");

                // 输出被截断且未生成完整工具调用：report_create_custom 等复杂参数场景
                // 当 max_tokens 不足以完整生成 tool_calls 参数时，DeepSeek 会放弃工具调用
                // 仅返回部分思考文字。不检查会被误判进 else 分支"信息已足够"静默结束，
                // 实际写操作根本没执行。明确识别后给出可操作的提示，避免静默失败。
                if ("length".equals(finishReason) && (toolCalls == null || toolCalls.isEmpty())) {
                    log.warn("[Agent] 第{}轮 AI 输出被截断(finish_reason=length)，无法生成完整工具调用", step);
                    AgentStep truncStep = AgentStep.thought(
                            "AI 输出被截断，无法生成完整的工具调用参数。当前请求的报表/操作配置较复杂，" +
                            "建议拆分为更小的步骤，或简化配置后重试。");
                    steps.add(truncStep);
                    if (stepCallback != null) stepCallback.accept(truncStep);
                    AgentStep finalStep = AgentStep.finalAnswer(
                            "抱歉，当前操作所需的配置参数较复杂，AI 输出长度受限未能完整生成。\n" +
                            "建议：\n" +
                            "1. 简化报表配置（减少关联表或指标数量）后重试；\n" +
                            "2. 或拆分成多张报表分别创建。");
                    steps.add(finalStep);
                    if (stepCallback != null) stepCallback.accept(finalStep);
                    return steps;
                }

                if (toolCalls != null && !toolCalls.isEmpty()) {
                    for (Map<String, Object> toolCall : toolCalls) {
                        String toolName = (String) toolCall.get("name");
                        String argumentsStr = (String) toolCall.get("arguments");
                        String callId = (String) toolCall.get("id");

                        Map<String, Object> toolParams = new HashMap<>();
                        if (argumentsStr != null && !argumentsStr.isBlank()) {
                            try {
                                toolParams = mapper.readValue(argumentsStr, Map.class);
                            } catch (Exception e) {
                                log.warn("[Agent] 解析工具参数失败: {}", argumentsStr);
                            }
                        }

                        // 面向用户的步骤文案：不再把内部工具名丢给用户看
                        // （2026-09-18 用户反馈"一点 agent 的思考都没有 / 结果工具和执行工具乱得很"）
                        // 2026-10-13 用户要求"思考过程不再静默"：模型真实推理内容（reasoning_content）
                        // 随思考步骤下发，前端折叠区展示真实思考；模型未返回推理时降级为业务化步骤说明。
                        String reasoningOfRound = reasoningText(aiResult);
                        AgentStep thoughtStep = reasoningOfRound != null
                                ? AgentStep.thoughtWithReasoning(describeToolAction(toolName, toolParams, content), reasoningOfRound)
                                : AgentStep.thought(describeToolAction(toolName, toolParams, content));
                        steps.add(thoughtStep);
                        if (stepCallback != null) stepCallback.accept(thoughtStep);

                        AgentStep actionStep = AgentStep.action(toolName, toolParams);
                        steps.add(actionStep);
                        if (stepCallback != null) stepCallback.accept(actionStep);
                        log.info("[Agent] 调用工具: {} 参数: {}", toolName, toolParams);

                        // 写工具强制确认拦截：不执行，生成待确认卡片后结束本轮
                        if (interceptWriteTool(toolName, toolParams, scope, steps, stepCallback,
                                sessionId, userDisplayInput)) {
                            return steps;
                        }

                        Map<String, Object> toolResult;
                        if (!toolRegistry.hasTool(toolName)) {
                            toolResult = Map.of("success", false, "message", "工具不存在: " + toolName);
                        } else {
                            // 工具执行前权限校验（最后一道防线）
                            String denyReason = toolRegistry.checkPermission(toolName, scope);
                            if (denyReason != null) {
                                toolResult = Map.of("success", false, "message", "权限拒绝: " + denyReason);
                                log.warn("[Agent] 工具 {} 被权限拦截: {}", toolName, denyReason);
                            } else {
                                // 关闭写确认时走这里的直执行路径：打码值同样禁止落库（2026-11-17 C 方案）
                                String maskedPath = toolRegistry.isWriteTool(toolName)
                                        ? com.erp.ai.util.SensitiveDataMasker.findMaskedValuePath(toolParams, "")
                                        : null;
                                if (maskedPath != null) {
                                    toolResult = Map.of("success", false, "message",
                                            "参数「" + maskedPath + "」里包含打码值（形如 138****8000），不能直接保存。"
                                                    + "请把真实内容发给我，我再执行这一步。");
                                    log.warn("[Agent] 写工具参数含打码值，已拦截直执行 tool={} path={}", toolName, maskedPath);
                                } else {
                                    AiTool tool = toolRegistry.getTool(toolName);
                                    toolResult = tool.execute(toolParams, scope);
                                }
                            }
                        }

                        AgentStep obsStep = AgentStep.observation(toolName, toolResult);
                        steps.add(obsStep);
                        if (stepCallback != null) stepCallback.accept(obsStep);

                        Map<String, Object> assistantMsg = new HashMap<>();
                        assistantMsg.put("role", "assistant");
                        String assistantContent = content != null ? content : "";
                        assistantMsg.put("content", assistantContent);
                        // DeepSeek 思考模式（如 deepseek-flash）：带 tool_calls 的 assistant 消息必须把本轮返回的
                        // reasoning_content 一并写回历史并回传，否则第二轮请求被上游 400 拒绝（历史问题：只回传
                        // content → "读文档→查字段→建单"这类两轮以上工具调用必失败）。
                        Object reasoningContent = aiResult.get("reasoning_content");
                        if (reasoningContent != null && !String.valueOf(reasoningContent).isBlank()) {
                            assistantMsg.put("reasoning_content", reasoningContent);
                            // 模型思考内容也占上下文（回传给模型），计入用量供停止判断
                            contextTokens += estimateTokens(String.valueOf(reasoningContent)) + 8;
                        }
                        contextTokens += estimateTokens(assistantContent) + 8;
                        List<Map<String, Object>> tcList = new ArrayList<>();
                        Map<String, Object> tcForHistory = new HashMap<>();
                        tcForHistory.put("id", callId != null ? callId : "call_" + step);
                        tcForHistory.put("type", "function");
                        Map<String, Object> funcForHistory = new HashMap<>();
                        funcForHistory.put("name", toolName);
                        funcForHistory.put("arguments", argumentsStr != null ? argumentsStr : "{}");
                        tcForHistory.put("function", funcForHistory);
                        tcList.add(tcForHistory);
                        assistantMsg.put("tool_calls", tcList);
                        conversationHistory.add(assistantMsg);

                        Map<String, Object> toolMsg = new HashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", callId != null ? callId : "call_" + step);
                        String toolResultJson = mapper.writeValueAsString(toolResult);
                        toolMsg.put("content", toolResultJson);
                        conversationHistory.add(toolMsg);
                        // 工具查回的数据计入上下文用量（2026-11-13）：停止判断依据真实占用，而非仅初始装配
                        contextTokens += estimateTokens(toolResultJson) + 8;
                    }
                } else {
                    if (content != null && !content.isBlank()) {
                        // 假成功护栏：本轮无任何工具调用，模型却可能照抄"已准备好…请点确认卡片"话术
                        // （2026-09-18 用户实测踩到）→ 换成如实话术，避免用户去找不存在的卡片
                        String safeContent = guardFakeConfirmClaim(content, steps);

                        // 最终答复轮同样带出模型真实推理（2026-10-14）：流式思考占位在本轮替换时若丢失
                        // reasoning，用户回看执行过程时该轮思考会变成空白
                        String finalReasoning = reasoningText(aiResult);
                        AgentStep thoughtStep = finalReasoning != null
                                ? AgentStep.thoughtWithReasoning("我已经收集到足够的信息，可以给出最终答案了。", finalReasoning)
                                : AgentStep.thought("我已经收集到足够的信息，可以给出最终答案了。");
                        steps.add(thoughtStep);
                        if (stepCallback != null) stepCallback.accept(thoughtStep);

                        AgentStep finalStep = AgentStep.finalAnswer(safeContent);
                        steps.add(finalStep);
                        if (stepCallback != null) stepCallback.accept(finalStep);
                        saveHistory(sessionId, scope.getUserId(), userInput, safeContent);
                        log.info("[Agent] 最终答案: {}", safeContent.length() > 100 ? safeContent.substring(0, 100) + "..." : safeContent);
                        return steps;
                    } else {
                        AgentStep errorStep = AgentStep.error("AI返回内容为空");
                        steps.add(errorStep);
                        if (stepCallback != null) stepCallback.accept(errorStep);
                        return steps;
                    }
                }
            }

            // 停止提示按真实原因区分（2026-11-13）：上下文用尽=正常容量出口；轮数兜底=疑似模型空转
            AgentStep finalStep = AgentStep.finalAnswer(contextExhausted
                    ? "本次思考的上下文空间已用完，无法继续处理您的请求。请拆分问题分次提问，或清空对话后重试。"
                    : "抱歉，思考轮次过多已停止处理您的请求。请尝试简化您的问题。");
            steps.add(finalStep);
            if (stepCallback != null) stepCallback.accept(finalStep);

        } catch (Exception e) {
            log.error("[Agent] 执行失败", e);
            AgentStep errorStep = AgentStep.error("Agent执行失败: " + e.getMessage());
            steps.add(errorStep);
            if (stepCallback != null) stepCallback.accept(errorStep);
        } finally {
            // 本轮用量汇总：耗时 + token 用量 + 积分消耗，作为 USAGE 步骤下发（答复末尾展示）。
            // end() 只能调一次（取走即清 ThreadLocal），落库与下发共用同一份汇总。
            long[] usage = AiUsageCollector.end();
            long elapsedMs = System.currentTimeMillis() - roundStartAt;
            // 积分结算（2026-10-24）：按本轮真实 tokens × 模型倍率 ×（深度思考倍率）扣减
            AiCreditService.ConsumeResult credit = null;
            if (usage != null && usage[2] > 0 && scope != null && scope.getUserId() != null) {
                credit = aiCreditService.consume(
                        scope.getUserId(), scope.getUsername(),
                        usage[0], usage[1], usage[2], usedModel,
                        request.getProviderId() != null && !request.getProviderId().isBlank(),
                        // 高/极高都按深度倍率计费（2026-11-22 用户拍板：极高计费同高）；light 按标准倍率
                        !"light".equals(thoughtLevel), null);
            }
            if (usage != null && (usage[2] > 0 || elapsedMs > 0)) {
                AgentStep usageStep = AgentStep.usageStep(elapsedMs, usage,
                        credit == null ? null : credit.charged(),
                        credit == null ? null : credit.balance(),
                        credit == null ? null : credit.ownerType(),
                        contextTokens > 0 ? contextTokens : null,
                        contextTokens > 0 ? contextLimit : null);
                steps.add(usageStep);
                if (stepCallback != null) stepCallback.accept(usageStep);
            }
            // 无条件清理"用户所选模型"上下文，防线程池复用串账；token 用量复用上面 end() 的结果落库
            AiModelContextHolder.clear();
            // 思考强度同步清理（2026-11-22 三档，同防线程池复用串账）
            AiThoughtContextHolder.clear();
            recordTokenUsage(scope, usage, credit);
        }

        return steps;
    }

    /** 从适配器结果里取模型真实推理文本（空/空白返回 null，便于区分"模型没思考"与"思考为空串"） */
    private String reasoningText(Map<String, Object> aiResult) {
        Object r = aiResult == null ? null : aiResult.get("reasoning_content");
        if (r == null) return null;
        String s = String.valueOf(r);
        return s.isBlank() ? null : s;
    }

    /**
     * 汇总本次请求线程内全部AI调用的token消耗并落库（用户维度月聚合）。
     * 失败仅告警，不影响AI主流程；usage 由调用方在 finally 里 end() 一次取得后传入（不可重复 end）。
     */
    private void recordTokenUsage(AiDataScope scope, long[] usage, AiCreditService.ConsumeResult credit) {
        try {
            if (usage == null || usage[2] <= 0) {
                return;
            }
            if (scope == null || scope.getUserId() == null) {
                return;
            }
            aiTokenUsageService.recordUsage(scope.getUserId(), scope.getUsername(),
                    usage[0], usage[1], usage[2], usage[3], usage.length > 4 ? usage[4] : 0,
                    credit == null ? java.math.BigDecimal.ZERO : credit.charged());
        } catch (Exception e) {
            log.warn("[Agent] Token用量记录失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> getHistory(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) return new ArrayList<>();
        // 2026-10-20 起会话历史落数据库（永久保留）；注入模型的窗口 = 设置中心「会话记忆轮数」
        // 2026-10-22：按登录人隔离 —— 别人拿到同样的会话号也读不到你的上下文
        return aiChatHistoryStore.load(sessionId, userId, maxHistory() * 2);
    }

    private void saveHistory(String sessionId, Long userId, String userMsg, String aiReply) {
        if (sessionId == null || sessionId.isBlank()) return;
        // 2026-10-20：改为数据库表持久化（原 Redis 版本在 Redis 不可用/重启后会丢会话）
        // 2026-10-22：记录归属登录人，供「历史会话」列表按本人展示与删除
        aiChatHistoryStore.append(sessionId, userId, userMsg, aiReply);
    }

    /**
     * 非 CRUD 工具的业务化动作名（执行过程里给用户看的那行字）。
     * 新增工具时**必须**在此登记，否则会退化成「正在处理：xxx」——不会报错，但用户看不懂。
     */
    private static final Map<String, String> TOOL_ACTION_LABELS = Map.ofEntries(
            Map.entry("bill_action", "处理单据流程"),
            Map.entry("bill_pushdown", "下推生成下游单据"),
            Map.entry("bill_action_list", "读取单据可执行动作"),
            Map.entry("bill_item_list", "读取单据明细行"),
            Map.entry("bill_item_update", "修改单据明细行"),
            Map.entry("open_page", "打开页面"),
            Map.entry("image_search", "按图片找物料"),
            Map.entry("image_read", "读取图片内容"),
            Map.entry("document_read", "读取文档内容"),
            Map.entry("print_template_fields", "查询打印模板可用字段"),
            Map.entry("image_create_order", "识别图片并创建销售订单"),
            Map.entry("image_create_bom", "识别图片中的BOM"),
            Map.entry("bom_file_read", "读取BOM文件内容"),
            Map.entry("bom_import_rows", "导入BOM数据"),
            Map.entry("create_sale_order_draft", "创建销售订单草稿"),
            Map.entry("create_work_order_draft", "创建生产工单草稿"),
            Map.entry("create_routing_with_operations", "创建工艺路线（含工序）"),
            Map.entry("report_list_datasources", "读取报表数据源"),
            Map.entry("report_create_custom", "创建自定义报表"),
            Map.entry("material_import_confirm", "确认物料导入"),
            Map.entry("system_assign_user_roles", "给员工分配角色"),
            Map.entry("system_assign_role_permissions", "给角色分配权限")
    );

    /**
     * 生成"执行过程"里的步骤文案（展开折叠区才看得到）。
     *
     * <p>历史实现是写死的「我需要调用 query_basedata_material 工具来获取相关数据」—— 既无思考、又把内部
     * 工具名丢给用户看，用户 2026-09-18 反馈"一点 agent 的思考都没有，还很乱"。现改为面向业务的语言。
     *
     * <p>⚠️ **不伪造模型推理**：DeepSeek 在 tool_calls 轮次通常不返回 content（适配器只取 content，
     * 不解析 reasoning_content）。因此：模型真返回了文字 → 用它原文；否则展示的是**系统按工具生成的
     * 业务步骤说明**，而不是模型原话。
     */
    private String describeToolAction(String toolName, Map<String, Object> params, String modelContent) {
        if (modelContent != null && !modelContent.isBlank()) {
            return modelContent.trim();
        }
        String entity = toolRegistry.entityLabelOfTool(toolName);
        String what = entity.isEmpty() ? "数据" : entity;
        if (toolName != null) {
            if (toolName.startsWith("query_")) return "正在查询：" + what;
            if (toolName.startsWith("create_")) return "正在创建：" + what + buildKeyValuesHint(params);
            if (toolName.startsWith("update_")) return "正在修改：" + what + buildPrimaryKeyHint(params);
            if (toolName.startsWith("delete_")) return "正在删除：" + what + buildPrimaryKeyHint(params);
            if (toolName.startsWith("setting_")) return "正在处理系统设置";
            if (toolName.startsWith("import_") || toolName.startsWith("material_import_")) {
                return "正在导入数据";
            }
            String label = TOOL_ACTION_LABELS.get(toolName);
            if (label != null) return "正在" + label;
        }
        return "正在处理：" + (toolName == null ? "业务操作" : toolName);
    }

    /** 步骤文案里补一句"操作对象"（取 data 里的前 2 个非空值，如「：小助手、成品」）；取不到就返回空串 */
    private String buildKeyValuesHint(Map<String, Object> params) {
        if (params == null) return "";
        Object dataObj = params.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return "";
        String values = joinValues(data, 2);
        return values.isEmpty() ? "" : "：" + values;
    }

    /** 步骤文案里补一句主键（如「#12」）；取不到就返回空串 */
    private String buildPrimaryKeyHint(Map<String, Object> params) {
        String pk = findPrimaryKeyParam(params);
        if (pk.isEmpty()) return "";
        String id = strVal(params.get(pk));
        return id.isEmpty() ? "" : " #" + id;
    }

    /** 取 Map 里前 limit 个非空值，用「、」连接（面向用户的可读值，不显示英文字段名） */
    private String joinValues(Map<?, ?> map, int limit) {
        if (map == null) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Object v : map.values()) {
            if (n >= limit) break;
            String s = strVal(v);
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append("、");
            sb.append(s);
            n++;
        }
        return sb.toString();
    }

    /** update_/delete_ 工具的参数里除 data 之外的唯一键就是主键（工具定义只声明了主键 + data） */
    private String findPrimaryKeyParam(Map<String, Object> params) {
        if (params == null) return "";
        for (String k : params.keySet()) {
            if (!"data".equals(k)) return k;
        }
        return "";
    }

    /**
     * "谎称已生成确认卡片"的判定式。命中即意味着用户会去找一张**根本不存在**的卡片。
     * 提示词层已收紧（写操作确认机制），这里做**确定性兜底**（提示词单靠约束已失败过一次）。
     */
    private static final java.util.regex.Pattern FAKE_CONFIRM_CLAIM = java.util.regex.Pattern.compile(
            "已(为你)?准备好|已准备就绪|已生成.{0,8}(确认)?卡片|待确认卡片已|已按.{0,14}发起|请(在|到|于).{0,12}确认卡片");

    /**
     * 假成功护栏：**本轮没有任何工具调用**（能走到这个分支就说明模型没返回 tool_calls），
     * 模型却照抄话术声称"已为你准备好…请在确认卡片中确认" → 换成如实话术并记 WARN。
     *
     * <p>2026-09-18 用户实测（日志实锤）：用户说「单号是SO-20260914-0005提交这张销售订单。」
     * → 小楷回「已为你准备好提交操作，请在下方确认卡片里点「确认执行」」，
     * 而该轮日志只有「第 1 轮思考中…」、**没有 `调用工具: bill_action`、也没有 `写操作已转待确认`**
     * → 根本没生成卡片；用户接着回「确认」被如实告知"没有正在等待确认的操作"，用户以为系统坏了。
     */
    private String guardFakeConfirmClaim(String content, List<AgentStep> steps) {
        if (content == null || !FAKE_CONFIRM_CLAIM.matcher(content).find()) return content;
        boolean hasCard = steps.stream().anyMatch(s -> s.getType() == AgentStep.StepType.CONFIRM_NEEDED);
        if (hasCard) return content;   // 真有卡片：话术正常，不动
        log.warn("[Agent] 命中假成功护栏：本轮无任何工具调用却声称已备好确认卡片，已替换为如实话术。原文={}",
                content.length() > 200 ? content.substring(0, 200) : content);
        return "我这边没有正在等待确认的操作 —— 上面那句「已准备好」是我说错了：我这一轮并没有真正发起操作，"
                + "也没有生成确认卡片。请把要执行的操作再说一次（例如「提交销售订单 SO-20260914-0005」），"
                + "我会真正发起并弹出确认卡片给你确认。";
    }

    /**
     * 写工具强制确认拦截。
     * 命中写工具且确认开关开启时：不执行工具，暂存操作（Redis，5分钟），
     * 推送 CONFIRM_NEEDED 步骤（含token+摘要）和最终答复，由用户在确认卡片二次提交。
     * @return true=已拦截（调用方应结束本轮循环）；false=非写工具或确认开关关闭（正常执行）
     */
    private boolean interceptWriteTool(String toolName, Map<String, Object> toolParams, AiDataScope scope,
                                       List<AgentStep> steps, Consumer<AgentStep> stepCallback,
                                       String sessionId, String userInput) {
        if (!toolRegistry.isWriteTool(toolName)) {
            return false;
        }
        // 打码值防呆（2026-11-17 C 方案）：参数里出现 **** 说明来源是脱敏后的数据，
        // 打码单向不可还原，落库会把星号当真实值存进去 → 一律拦截，要求用户提供真实内容
        String maskedPath = com.erp.ai.util.SensitiveDataMasker.findMaskedValuePath(toolParams, "");
        if (maskedPath != null) {
            String msg = "参数「" + maskedPath + "」里包含打码值（形如 138****8000），不能直接保存。"
                    + "请把真实内容发给我，我再执行这一步。";
            log.warn("[Agent] 写工具参数含打码值，已拦截 tool={} path={}", toolName, maskedPath);
            AgentStep obsStep = AgentStep.observation(toolName, Map.of("success", false, "message", msg));
            steps.add(obsStep);
            if (stepCallback != null) stepCallback.accept(obsStep);
            AgentStep askStep = AgentStep.finalAnswer(msg);
            steps.add(askStep);
            if (stepCallback != null) stepCallback.accept(askStep);
            saveHistory(sessionId, scope.getUserId(), userInput, msg);
            return true;
        }
        // 关闭「AI写操作需人工确认」后：审批 / 提交 / 驳回 / 重置草稿 / 下推 等【可逆】动作直接执行；
        // 但「删除 / 作废」等【不可逆】动作仍强制弹确认卡片（安全底线，用户选定 B 方案）
        if (!aiConfirmService.isConfirmWriteEnabled()
                && !aiConfirmService.isIrreversibleCall(toolName, toolParams)) {
            return false;
        }

        // 必填参数校验：缺参数时绝不弹"点了也执行不了"的空确认卡片，改为让 AI 追问用户补信息。
        // 历史 bug：bom_import 缺 fileUrl 也照样弹卡片，用户确认必然失败。
        String missingMsg = findMissingRequiredParams(toolName, toolParams);
        if (missingMsg != null) {
            log.warn("[Agent] 写工具缺少必填参数，已拦截并转为追问 tool={} msg={}", toolName, missingMsg);
            AgentStep obsStep = AgentStep.observation(toolName, Map.of("success", false, "message", missingMsg));
            steps.add(obsStep);
            if (stepCallback != null) stepCallback.accept(obsStep);
            AgentStep askStep = AgentStep.finalAnswer(missingMsg);
            steps.add(askStep);
            if (stepCallback != null) stepCallback.accept(askStep);
            saveHistory(sessionId, scope.getUserId(), userInput, missingMsg);
            return true;
        }

        try {
            String summary = buildWriteSummary(toolName, toolParams, scope);
            String token = aiConfirmService.createPending(toolName, toolParams, scope, summary);

            AgentStep confirmStep = AgentStep.confirmNeeded(toolName, toolParams, token, summary);
            steps.add(confirmStep);
            if (stepCallback != null) stepCallback.accept(confirmStep);

            // 答复文体（2026-09-18 布局重排后）：卡片已在答复**下方**；摘要只在卡片上出现一次。
            // 措辞**不提"直接回复确认"**：121 客户包走旧版前端（VITE_ASSISTANT_UI_V2=false），
            // 打字说「确认」不会自动执行，提了反而误导；点卡片按钮是两个包都通用可靠的路径。
            // （卡片上的「也可以直接回复「确认」」提示由前端按 AI_UI_V2 控制，只在新版显示）
            String reply = "已为你准备好这个操作，请在下方确认卡片里点「确认执行」（5分钟内有效）。";
            AgentStep finalStep = AgentStep.finalAnswer(reply);
            steps.add(finalStep);
            if (stepCallback != null) stepCallback.accept(finalStep);
            saveHistory(sessionId, scope.getUserId(), userInput, reply);
            log.info("[Agent] 写操作已转待确认 tool={} token={}", toolName, token);
            return true;
        } catch (Exception e) {
            // 拦截流程本身出错：宁可失败也不绕过确认直接执行（fail-safe）
            log.error("[Agent] 写确认拦截失败，拒绝直接执行 tool={}", toolName, e);
            AgentStep errorStep = AgentStep.error("写操作确认流程异常，已拒绝直接执行: " + e.getMessage());
            steps.add(errorStep);
            if (stepCallback != null) stepCallback.accept(errorStep);
            return true;
        }
    }

    /**
     * 校验写工具的必填参数是否齐全（依据工具自身 getParameters().required 声明）。
     * @return null=参数齐全；非null=给用户的追问话术
     */
    private String findMissingRequiredParams(String toolName, Map<String, Object> params) {
        try {
            AiTool tool = toolRegistry.getTool(toolName);
            if (tool == null) return null;
            Map<String, Object> schema = tool.getParameters();
            if (schema == null) return null;
            Object reqObj = schema.get("required");
            if (!(reqObj instanceof List<?> required) || required.isEmpty()) return null;
            List<String> missing = new ArrayList<>();
            for (Object r : required) {
                String key = String.valueOf(r);
                Object v = params == null ? null : params.get(key);
                // 兼容模型把 items 塞进 data 的写法（创建逻辑两种都认，这里也必须两种都认，否则会误报"缺明细"）
                if ("items".equals(key) && (v == null || String.valueOf(v).isBlank())) {
                    Object dataObj = params == null ? null : params.get("data");
                    if (dataObj instanceof Map<?, ?> dataMap) v = dataMap.get("items");
                }
                if (v == null || String.valueOf(v).isBlank()) {
                    missing.add(key);
                }
            }
            if (missing.isEmpty()) return null;
            if (missing.contains("fileUrl")) {
                return "还差一个文件才能导入哦～请点输入框左下的 📎 上传 BOM 文件（Excel/CSV），"
                        + "或直接把文件 Ctrl+V 粘贴进来，我再帮你导入。";
            }
            if (missing.contains("entries")) {
                return "还差要创建的内容：请把要建的资料/单据逐条告诉我（类型 + 名称或关键字段），"
                        + "我整理成一张确认卡再执行。";
            }
            if (missing.contains("steps")) {
                return "这次没有拆出可执行的步骤：请把要做的事按先后顺序说清楚（例如「先建物料，再建 BOM，最后开销售订单」），"
                        + "我整理成一条任务再发起。";
            }
            if (missing.contains("items")) {
                // 带明细的单据：没有明细绝不发起"点了也建不出内容"的确认卡片（2026-10-21 用户实测空单）
                return "这张单据还差明细：请告诉我要出库/入库的物料和数量（有单价也一并说，例如「物料 6061-T6铝合金，数量 20，单价 10」），"
                        + "我补齐明细后再发起。";
            }
            return "还差一点信息才能执行「" + toolName + "」：缺少参数 " + String.join("、", missing)
                    + "。请补充后再试。";
        } catch (Exception e) {
            // 校验本身异常时不阻断原流程（宁可放行由后续逻辑处理，也不误拦正常操作）
            log.warn("[Agent] 必填参数校验异常，跳过校验 tool={}: {}", toolName, e.getMessage());
            return null;
        }
    }

    /** 从文件URL里取可读文件名（用于确认卡片摘要） */
    private String fileNameOf(Object fileUrl) {
        if (fileUrl == null) return "?";
        String s = String.valueOf(fileUrl);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int slash = s.lastIndexOf('/');
        String name = slash >= 0 ? s.substring(slash + 1) : s;
        try {
            return java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return name;
        }
    }

    /** 导入实体中文名（确认卡片摘要展示用；取不到时回退实体名） */
    private String importEntityLabel(Object entityName) {
        String name = strVal(entityName);
        if (name.isEmpty()) return "?";
        try {
            return importPermissionResolver.entityLabel(name);
        } catch (Exception e) {
            return name;
        }
    }

    /** 生成写操作的人话摘要（确认卡片展示用） */
    private String buildWriteSummary(String toolName, Map<String, Object> params, AiDataScope scope) {
        try {
            switch (toolName) {
                case "create_sale_order_draft" -> {
                    StringBuilder sb = new StringBuilder("创建销售订单草稿：客户「")
                            .append(params.getOrDefault("customerName", "?")).append("」");
                    if (params.get("items") instanceof List<?> items) {
                        sb.append("，共 ").append(items.size()).append(" 行明细");
                    }
                    return sb.toString();
                }
                case "create_work_order_draft" -> {
                    StringBuilder sb = new StringBuilder("创建生产工单草稿：物料「")
                            .append(params.getOrDefault("materialName", params.getOrDefault("materialCode", "?")))
                            .append("」× ").append(params.getOrDefault("quantity", "?"));
                    Object rw = params.getOrDefault("routingName", params.get("routingCode"));
                    sb.append(rw != null ? "，工艺路线「" + rw + "」" : "，工艺路线自动取该物料的主路线");
                    sb.append("（只存草稿，不提交）");
                    return sb.toString();
                }
                case "create_routing_with_operations" -> {
                    int n = params.get("operations") instanceof List<?> l ? l.size() : 0;
                    return "创建工艺路线「" + params.getOrDefault("routingName", "?") + "」，共 " + n + " 道工序";
                }
                case "system_assign_user_roles" -> {
                    return "给员工「" + params.getOrDefault("username", "?") + "」追加角色: "
                            + params.getOrDefault("roleNames", "?") + "（不删除已有角色）";
                }
                case "system_assign_role_permissions" -> {
                    return "给角色「" + params.getOrDefault("roleName", "?") + "」开启权限: "
                            + params.getOrDefault("menuNames", "?") + "（不关闭已有权限）";
                }
                case "report_create_custom" -> {
                    return buildReportCreateSummary(params);
                }
                case "image_create_order" -> {
                    return "识别图片并创建销售订单草稿";
                }
                case "bom_import_rows" -> {
                    Object rowsObj = params.get("rows");
                    int n = rowsObj instanceof List<?> l ? l.size() : 0;
                    return "BOM导入：共 " + n + " 条标准BOM行（按系统标准模板写入，缺失物料按设置自动创建）";
                }
                case "import_file" -> {
                    StringBuilder sb = new StringBuilder("⚠ 将写入数据\n导入【")
                            .append(importEntityLabel(params.get("entityName")))
                            .append("】：文件 ").append(fileNameOf(params.get("fileUrl")));
                    if ("customer".equals(strVal(params.get("entityName"))) && params.get("poolType") != null) {
                        sb.append("，导入到").append("0".equals(strVal(params.get("poolType"))) ? "私海（归属当前用户）" : "公海");
                    }
                    return sb.toString();
                }
                case "import_rows" -> {
                    Object rowsObj = params.get("rows");
                    int n = rowsObj instanceof List<?> l ? l.size() : 0;
                    return "⚠ 将写入数据\n行级导入【" + importEntityLabel(params.get("entityName")) + "】：共 " + n + " 行";
                }
                case "material_import_confirm" -> {
                    String policy = strVal(params.get("onExistingConflict"));
                    String policyCn = "OVERWRITE".equalsIgnoreCase(policy) ? "覆盖已存在" : "跳过已存在";
                    return "⚠ 将写入数据\n物料导入：文件 " + fileNameOf(params.get("fileUrl"))
                            + "，已存在物料按「" + policyCn + "」处理";
                }
                case "image_create_bom" -> {
                    return "识别图片中的BOM结构并录入系统";
                }
                case "bill_action" -> {
                    return buildBillActionSummary(params, scope);
                }
                case "bill_batch_action" -> {
                    return buildBillBatchActionSummary(params, scope);
                }
                case "bill_item_update" -> {
                    return buildBillItemUpdateSummary(params, scope);
                }
                case "bill_pushdown" -> {
                    return buildBillPushDownSummary(params, scope);
                }
                case "work_operation_action" -> {
                    return buildWorkOperationActionSummary(params);
                }
                case "batch_create_records" -> {
                    return buildBatchCreateRecordsSummary(params);
                }
                case "task_plan_execute" -> {
                    return buildTaskPlanSummary(params);
                }
                case "board_save_formula_card" -> {
                    return buildBoardSaveSummary(params);
                }
                default -> {
                    // 通用 CRUD（create_/update_/delete_）：只给用户看业务语言，不再输出裸工具名 + 原始 JSON。
                    // 历史实现返回「执行写操作 create_basedata_material: {"data":{...}}」，
                    // 用户 2026-09-18 反馈确认卡片"乱得很"（截图实证）。
                    return buildGenericCrudSummary(toolName, params);
                }
            }
        } catch (Exception e) {
            String entity = toolRegistry.entityLabelOfTool(toolName);
            return entity.isEmpty() ? "执行写操作 " + toolName : "操作" + entity;
        }
    }

    /**
     * 通用 CRUD 工具的确认卡片摘要（面向用户，不出现内部工具名与原始 JSON）。
     *
     * <pre>
     * 新增物料：小助手、成品
     * 修改物料 #12：小助手
     * ⚠ 删除后不可恢复
     * 删除客户 #5
     * </pre>
     */
    private String buildGenericCrudSummary(String toolName, Map<String, Object> params) {
        String entity = toolRegistry.entityLabelOfTool(toolName);
        String what = entity.isEmpty() ? "记录" : entity;
        String name = toolName == null ? "" : toolName;
        if (name.startsWith("create_")) {
            // 带明细的单据：卡片要让人一眼看出「客户 + 几行明细 + 首行是什么」，
            // 不能只拼两个原始值（历史卡片出现「新增销售出库单：29、楷煜」，用户根本看不懂）
            String head = "新增" + what + buildCreateHeaderHint(params);
            Object itemsObj = params == null ? null : params.get("items");
            if (!(itemsObj instanceof List<?>)) {
                Object dataObj = params == null ? null : params.get("data");
                if (dataObj instanceof Map<?, ?> dataMap) itemsObj = dataMap.get("items");
            }
            if (itemsObj instanceof List<?> items && !items.isEmpty()) {
                return head + "，" + items.size() + " 行明细" + firstItemHint(items.get(0));
            }
            return head;
        }
        if (name.startsWith("update_")) {
            StringBuilder sb = new StringBuilder("修改").append(what).append(buildPrimaryKeyHint(params));
            Object dataObj = params == null ? null : params.get("data");
            if (dataObj instanceof Map<?, ?> data && !data.isEmpty()) {
                String changed = joinPairs(data, 3);
                if (!changed.isEmpty()) sb.append("：").append(changed);
            }
            return sb.toString();
        }
        if (name.startsWith("delete_")) {
            return "⚠ 删除后不可恢复\n删除" + what + buildPrimaryKeyHint(params);
        }
        return entity.isEmpty() ? "执行写操作 " + toolName : "操作" + entity;
    }

    /** 新增单据卡片：优先写「客户/供应商/物料」这类关键业务字段，不再直接拼「29、楷煜」这种无语义值 */
    private String buildCreateHeaderHint(Map<String, Object> params) {
        if (params == null) return "";
        Object dataObj = params.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return "";
        String[][] keys = {
                {"customerName", "客户"}, {"supplierName", "供应商"}, {"materialName", "物料"},
                {"employeeName", "员工"}, {"deptName", "部门"}
        };
        for (String[] k : keys) {
            String s = strVal(data.get(k[0]));
            if (!s.isEmpty()) return "：" + k[1] + "「" + s + "」";
        }
        return buildKeyValuesHint(params);
    }

    /** 批量创建（可跨表）的确认卡片摘要：总数 + 按类型计数 + 首条关键信息 */
    private String buildBatchCreateRecordsSummary(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("entries");
        if (!(raw instanceof List<?> entries) || entries.isEmpty()) return "批量创建记录";
        Map<String, Integer> counts = new LinkedHashMap<>();
        String first = "";
        for (Object o : entries) {
            if (!(o instanceof Map<?, ?> entry)) continue;
            String key = strVal(entry.get("entity"));
            String label = toolRegistry.entityLabelOfKey(key);
            if (label.isEmpty()) label = "记录";
            counts.merge(label, 1, Integer::sum);
            if (first.isEmpty() && entry.get("data") instanceof Map<?, ?> data) {
                first = firstRecordValue(data);
            }
        }
        StringBuilder summary = new StringBuilder("批量创建 ").append(entries.size()).append(" 条：");
        boolean firstGroup = true;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!firstGroup) summary.append("、");
            summary.append(e.getKey()).append(" ").append(e.getValue());
            firstGroup = false;
        }
        if (!first.isEmpty()) summary.append("（首条：").append(first).append("）");
        return summary.toString();
    }

    /** 看板公式卡保存的确认卡摘要：看板、位置、标题、公式、取数项数量 */
    private String buildBoardSaveSummary(Map<String, Object> params) {
        Map<String, String> names = Map.of(
                "finance", "财务看板", "sale", "销售看板",
                "inventory", "仓储看板", "operation", "经营看板");
        String page = params == null ? "" : strVal(params.get("pageKey"));
        String block = params == null ? "" : strVal(params.get("blockTitle"));
        String title = params == null ? "" : strVal(params.get("title"));
        String formula = params == null ? "" : strVal(params.get("formula"));
        int count = params != null && params.get("items") instanceof List<?> list ? list.size() : 0;
        StringBuilder sb = new StringBuilder("保存看板公式卡\n");
        sb.append("看板：").append(names.getOrDefault(page, page)).append("\n");
        sb.append(block.isEmpty() ? "位置：新增卡片" : "位置：替换「" + block + "」").append("\n");
        sb.append("标题：").append(title.isEmpty() ? (block.isEmpty() ? "看板公式卡" : block) : title).append("\n");
        sb.append("公式：").append(formula.isEmpty() ? "未填写" : formula).append("\n");
        sb.append("取数项：").append(count).append(" 个\n");
        sb.append("只影响你本人的看板配置");
        return sb.toString();
    }

    /** 多步骤任务链的确认卡摘要：目标 + 全部步骤（用户点确认前能看完整条链） */
    private String buildTaskPlanSummary(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("steps");
        if (!(raw instanceof List<?> steps) || steps.isEmpty()) return "多步骤任务";
        StringBuilder sb = new StringBuilder("将按顺序执行 ").append(steps.size()).append(" 个步骤：");
        for (int i = 0; i < steps.size(); i++) {
            Object o = steps.get(i);
            String title = o instanceof Map<?, ?> m ? strVal(m.get("title")) : "";
            if (title.isEmpty()) title = "第 " + (i + 1) + " 步";
            sb.append("\n").append(i + 1).append(". ").append(title);
            if (o instanceof Map<?, ?> m2) {
                Object deps = m2.get("dependsOn");
                if (deps instanceof List<?> dl && !dl.isEmpty()) {
                    sb.append("（依赖第 ").append(dl.toString().replace("[", "").replace("]", "")).append(" 步）");
                }
            }
        }
        Object policy = params.get("failPolicy");
        if ("SKIP".equalsIgnoreCase(strVal(policy))) {
            sb.append("\n失败处理：某步失败后跳过，继续执行后续步骤");
        } else if ("STOP".equalsIgnoreCase(strVal(policy))) {
            sb.append("\n失败处理：任一步失败即停止后续步骤");
        } else {
            sb.append("\n失败处理：某步失败时，依赖它的后续步骤不执行（无依赖的继续）");
        }
        return sb.toString();
    }

    /** 从一条记录里取最能代表它的值（优先名称类字段） */
    private String firstRecordValue(Map<?, ?> data) {
        String[] keys = {"categoryName", "materialName", "customerName", "supplierName", "employeeName", "deptName",
                "warehouseName", "unitName", "routingName", "accountName", "currencyName", "name", "code"};
        for (String k : keys) {
            String v = strVal(data.get(k));
            if (!v.isEmpty()) return v;
        }
        for (Object v : data.values()) {
            String s = strVal(v);
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    /** 卡片上的首行明细：物料 × 数量（单价） */
    private String firstItemHint(Object item) {
        if (!(item instanceof Map<?, ?> m)) return "";
        String name = strVal(m.get("materialName"));
        if (name.isEmpty()) name = strVal(m.get("materialCode"));
        String qty = strVal(m.get("quantity"));
        String price = strVal(m.get("unitPrice"));
        StringBuilder sb = new StringBuilder("（首行：");
        sb.append(name.isEmpty() ? "物料" : name);
        if (!qty.isEmpty()) sb.append(" × ").append(qty);
        if (!price.isEmpty()) sb.append("，单价 ").append(price);
        return sb.append("）").toString();
    }

    /** 取 Map 里前 limit 组「字段=值」（用于"改了什么"的摘要；字段名保留英文以免误译业务含义） */
    private String joinPairs(Map<?, ?> map, int limit) {
        if (map == null) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (n >= limit) break;
            String v = strVal(e.getValue());
            if (v.isEmpty()) continue;
            if (sb.length() > 0) sb.append("、");
            sb.append(e.getKey()).append("=").append(v);
            n++;
        }
        return sb.toString();
    }

    /**
     * bill_item_update 确认卡片摘要：让用户在确认前看到「哪张单、哪一行、从多少改成多少」。
     *
     * <p>要读一次现值 —— 卡片上必须能对照（照 buildBillActionSummary 的做法，用单号/客户名而不是主键 ID）。
     */
    private String buildBillItemUpdateSummary(Map<String, Object> params, AiDataScope scope) {
        String billType = strVal(params.get("billType"));
        Long billId = longVal(params.get("billId"));
        String patchText = "（" + params.get("patch") + "）";
        try {
            com.erp.ai.bill.BillItemHandler handler = billItemRegistry.get(billType);
            String billRef = billCodeResolver.describe(billType, billId, billLabelStyle(scope));
            if (handler == null || billId == null) {
                return "修改" + billRef + " 的明细行：" + patchText;
            }
            Object bill = handler.loadBill(billId);
            if (bill == null) {
                return "修改" + billRef + " 的明细行：" + patchText;
            }
            Long itemId = longVal(params.get("itemId"));
            Long lineNoParam = longVal(params.get("lineNo"));
            List<?> items = handler.loadItems(billId);
            Object target = null;
            int idx = -1;
            for (int i = 0; i < items.size(); i++) {
                Object it = items.get(i);
                boolean hit = itemId != null
                        ? itemId.equals(handler.itemId(it))
                        : (lineNoParam != null && lineNoParam.intValue() == (handler.lineNo(it) == null ? -1 : handler.lineNo(it)));
                if (hit) {
                    target = it;
                    idx = i;
                    break;
                }
            }
            if (target == null) {
                return "修改" + billRef + " 的明细行" + patchText + "（⚠ 没定位到该行，执行时会再次校验并可能失败，建议先查看明细）";
            }
            Map<String, Object> before = handler.toItemMap(target, idx + 1);
            StringBuilder sb = new StringBuilder("修改").append(billRef)
                    .append(" 第 ").append(before.get("行号")).append(" 行");
            if (before.get("物料编码") != null) {
                sb.append("（").append(before.get("物料编码")).append(" ").append(before.get("物料名称")).append("）");
            }
            sb.append("：");
            Object patchObj = params.get("patch");
            if (patchObj instanceof Map<?, ?> patch && !patch.isEmpty()) {
                for (Map.Entry<?, ?> e : patch.entrySet()) {
                    String cn = itemFieldCn(String.valueOf(e.getKey()));
                    sb.append(cn).append(" ").append(before.getOrDefault(cn, "-")).append(" → ").append(e.getValue()).append("；");
                }
            } else {
                sb.append(patchText);
            }
            sb.append("（金额/合计保存后自动重算）");
            return sb.toString();
        } catch (Exception e) {
            return "修改明细行：" + patchText;
        }
    }

    /** 明细字段英文名 → 中文（与 bill_item_list 的列名一致，卡片上能直接对照） */
    private String itemFieldCn(String field) {
        return switch (field) {
            case "unitPrice" -> "单价";
            case "quantity" -> "数量";
            case "taxRate" -> "税率";
            case "discountRate" -> "折扣率";
            default -> field;
        };
    }

    /**
     * bill_action 确认卡片摘要：把单据类型/单据标识/动作翻成人话，
     * 让用户在确认前看得懂要做什么（尤其是不可逆动作）。
     *
     * <p>单据标识按用户选择的口径（小助手 → ⚙️ 设置）用「单号 / 客户名 / 单号+客户名」，
     * 不再把内部主键 ID 说给用户听（2026-09-18 用户反馈）。
     */
    private String buildBillActionSummary(Map<String, Object> params, AiDataScope scope) {
        String actionCn = switch (strVal(params.get("action"))) {
            case "submit" -> "提交";
            case "approve" -> "审核（审核后不可修改）";
            case "reject" -> "驳回";
            case "reverseApprove" -> "重置草稿（回到草稿态）";
            case "close" -> "关闭";
            case "closeOrder" -> "结案";
            case "reopen" -> "反结案/重开";
            case "void" -> "作废";
            case "unvoid" -> "反作废";
            case "delete" -> "删除（⚠ 删除后不可恢复）";
            default -> strVal(params.get("action"));
        };
        String reason = strVal(params.get("reason"));
        String billType = strVal(params.get("billType"));
        Long billId = longVal(params.get("billId"));
        String billRef = billCodeResolver.describe(billType, billId, billLabelStyle(scope));
        return "对" + billRef + " 执行「" + actionCn + "」"
                + (reason.isEmpty() ? "" : "，原因：" + reason);
    }

    /**
     * bill_batch_action 确认卡片摘要：写清「将对 N 张什么单据执行什么动作」，并列出单号清单。
     * 用户确认前必须能看清影响面（对应守则：批量操作确认卡片要含单据类型、动作、张数、单号清单）。
     */
    private String buildBillBatchActionSummary(Map<String, Object> params, AiDataScope scope) {
        String actionCn = switch (strVal(params.get("action"))) {
            case "submit" -> "批量提交";
            case "approve" -> "批量审核（审核后不可修改）";
            case "delete" -> "批量删除（⚠ 删除后不可恢复）";
            case "reverseApprove" -> "批量重置草稿（回到草稿态）";
            default -> "批量" + strVal(params.get("action"));
        };
        String billType = strVal(params.get("billType"));
        String label = billType == null || billType.isEmpty() ? "单据" : billActionRegistry.label(billType);
        List<Long> ids = new ArrayList<>();
        if (params.get("ids") instanceof List<?> list) {
            for (Object o : list) {
                Long v = longVal(o);
                if (v != null) ids.add(v);
            }
        }
        StringBuilder sb = new StringBuilder("将对 ").append(ids.size()).append(" 张【")
                .append(label).append("】执行「").append(actionCn).append("」");
        String style = billLabelStyle(scope);
        for (Long id : ids) {
            sb.append("\n· ").append(billCodeResolver.describe(billType, id, style));
        }
        return sb.toString();
    }

    /** bill_pushdown 确认卡片摘要：由【源单】<单号> 下推生成【目标单】（草稿） */
    private String buildBillPushDownSummary(Map<String, Object> params, AiDataScope scope) {
        String sourceType = strVal(params.get("sourceType"));
        String targetType = strVal(params.get("targetType"));
        String targetLabel = billActionRegistry.label(targetType);
        String sourceRef = billCodeResolver.describe(sourceType, longVal(params.get("sourceId")), billLabelStyle(scope));
        return "由" + sourceRef + " 下推生成【" + targetLabel + "】（草稿）";
    }

    /**
     * work_operation_action 确认卡片摘要：说清「对哪张工单的哪道工序执行什么操作、改什么值」。
     *
     * <p>读一次工序（操作主键或工单号+工序名）拿到工单号/工序名，卡片上只用业务名称；
     * 读不到时退回用调用参数里的工单号/工序名，绝不在卡片上裸露内部主键 ID。
     */
    private String buildWorkOperationActionSummary(Map<String, Object> params) {
        String action = strVal(params.get("action"));
        String target = describeWorkOperation(params);
        if (target == null) {
            return "未能定位要操作的工序：请提供工单号+工序名称（或工序主键）后再发起，避免误操作。";
        }
        String actionCn = switch (action) {
            case "flow" -> "状态流转（" + strVal(params.get("statusAction")) + "）";
            case "rework" -> "返工（回退确认）";
            case "scrap" -> "报废（判废确认）";
            case "pushSupplement" -> "一键推补料";
            case "pushReceipt" -> "一键推入库";
            case "qc" -> "录入质检";
            case "notify" -> "发通知";
            default -> action;
        };
        StringBuilder sb = new StringBuilder("对").append(target).append("执行「").append(actionCn).append("」");
        String qty = strVal(params.get("quantity"));
        switch (action) {
            case "flow" -> {
                if (!qty.isEmpty()) sb.append("，数量：").append(qty);
                String rejected = strVal(params.get("rejectedQuantity"));
                if (!rejected.isEmpty()) sb.append("，不良/报废数：").append(rejected);
                String assignee = strVal(params.get("assigneeName"));
                if (!assignee.isEmpty()) sb.append("，负责人：").append(assignee);
            }
            case "rework" -> {
                if (!qty.isEmpty()) sb.append("，本次回退数：").append(qty);
            }
            case "scrap" -> {
                if (!qty.isEmpty()) sb.append("，本次判废数：").append(qty);
                sb.append("（判废后可按主BOM 一键下推补料）");
            }
            case "pushReceipt" -> {
                if (!qty.isEmpty()) sb.append("，入库数量：").append(qty);
            }
            case "qc" -> sb.append("（按质检模板逐项判定：合格→工序完工；不合格按所选处理方式处置）");
            case "notify" -> sb.append("，接收人：").append(receiverCount(params)).append(" 人");
            default -> {
                // 其它动作无需附加数值
            }
        }
        String remark = strVal(params.get("remark"));
        if (!remark.isEmpty()) sb.append("，备注：").append(remark);
        return sb.toString();
    }

    /** 工序的业务化引用（卡片用）：优先按主键读到工单号+工序名；读不到或没主键则用参数里的工单号+工序名；都缺返回 null */
    private String describeWorkOperation(Map<String, Object> params) {
        Long operationId = longVal(params.get("operationId"));
        if (operationId != null) {
            try {
                com.erp.production.entity.WorkOrderOperation op = workOrderOperationService.getById(operationId);
                if (op != null && op.getOperationName() != null) {
                    String lineNo = op.getLineNo() == null ? "" : op.getLineNo() + " ";
                    return "工单【" + strVal(op.getOrderNo()) + "】的工序【" + lineNo + op.getOperationName() + "】";
                }
            } catch (Exception e) {
                log.warn("[Agent] 工序操作摘要读取工序失败 operationId={}: {}", operationId, e.getMessage());
            }
        }
        String orderNo = strVal(params.get("workOrderNo"));
        String operationName = strVal(params.get("operationName"));
        if (orderNo.isEmpty() && operationName.isEmpty()) {
            return null;
        }
        return "工单【" + orderNo + "】的工序【" + operationName + "】";
    }

    /** 通知接收人数量（确认卡片摘要展示用） */
    private int receiverCount(Map<String, Object> params) {
        Object raw = params.get("receiverEmployeeIds");
        return raw instanceof List<?> list ? list.size() : (raw == null ? 0 : 1);
    }

    /** 用户在「小助手 → ⚙️ 设置」里选定的单据标识方式；未选/非法时按默认"单号" */
    private String billLabelStyle(AiDataScope scope) {
        if (scope == null || scope.getDisplayPrefs() == null) return com.erp.ai.bill.BillCodeResolver.STYLE_CODE;
        String style = scope.getDisplayPrefs().get(com.erp.ai.bill.BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE);
        return com.erp.ai.bill.BillCodeResolver.normalizeStyle(style);
    }

    /**
     * report_create_custom 确认卡摘要：展示AI设计的报表结构（模式/分组/聚合/图表），
     * 让用户确认前看得懂要建什么，避免盲确认。
     */
    private String buildReportCreateSummary(Map<String, Object> params) {
        String name = strVal(params.get("reportName"));
        String ds = strVal(params.get("dataSource"));
        StringBuilder sb = new StringBuilder("创建自定义报表「").append(name).append("」")
                .append("（数据源: ").append(ds).append("）");
        try {
            Object configObj = params.get("config");
            if (configObj instanceof Map<?, ?> config) {
                String mode = strVal(config.get("mode"));
                boolean summary = "summary".equalsIgnoreCase(mode);
                sb.append("\n· 模式: ").append(summary ? "汇总统计" : "明细清单");
                // 跨单关联摘要（聚合挂载明细，明细/汇总模式均显示）
                if (config.get("extraJoins") instanceof List<?> joins && !joins.isEmpty()) {
                    for (int i = 0; i < joins.size(); i++) {
                        if (!(joins.get(i) instanceof Map<?, ?> jm)) continue;
                        String target = strVal(jm.get("dataSourceKey"));
                        StringBuilder aggs = new StringBuilder();
                        if (jm.get("aggFields") instanceof List<?> afs && !afs.isEmpty()) {
                            for (Object a : afs) {
                                if (!(a instanceof Map<?, ?> am)) continue;
                                if (aggs.length() > 0) aggs.append("、");
                                aggs.append(strVal(am.get("func")).toUpperCase())
                                    .append("(").append(strVal(am.get("label")).isEmpty()
                                        ? strVal(am.get("field")) : strVal(am.get("label"))).append(")");
                            }
                        }
                        sb.append("\n· 关联").append(i).append(": ").append(target)
                          .append(aggs.length() > 0 ? " → " + aggs : "");
                    }
                }
                if (summary) {
                    if (config.get("groupBy") instanceof List<?> groups && !groups.isEmpty()) {
                        StringBuilder gs = new StringBuilder();
                        for (Object g : groups) {
                            if (!(g instanceof Map<?, ?> gm)) continue;
                            String gran = strVal(gm.get("granularity"));
                            gs.append(gs.length() > 0 ? "、" : "")
                                    .append(strVal(gm.get("field")))
                                    .append("month".equalsIgnoreCase(gran) ? "（按月）"
                                            : "quarter".equalsIgnoreCase(gran) ? "（按季）"
                                            : "year".equalsIgnoreCase(gran) ? "（按年）" : "");
                        }
                        if (gs.length() > 0) sb.append("\n· 分组: ").append(gs);
                    }
                    if (config.get("aggregates") instanceof List<?> aggs && !aggs.isEmpty()) {
                        StringBuilder as = new StringBuilder();
                        for (Object a : aggs) {
                            if (!(a instanceof Map<?, ?> am)) continue;
                            if (as.length() > 0) as.append("、");
                            String func = strVal(am.get("func")).toUpperCase();
                            String field = strVal(am.get("field"));
                            String label = strVal(am.get("label"));
                            as.append(func).append("(").append(field.isEmpty() ? "行数" : field).append(")");
                            if (!label.isEmpty()) as.append("→").append(label);
                            if ("pct".equalsIgnoreCase(strVal(am.get("compare")))) as.append("+环比%");
                        }
                        if (as.length() > 0) sb.append("\n· 指标: ").append(as);
                    }
                    if (config.get("chart") instanceof Map<?, ?> chart) {
                        String type = strVal(chart.get("type"));
                        if (!type.isEmpty()) {
                            sb.append("\n· 图表: ").append(switch (type) {
                                case "bar" -> "柱状图";
                                case "pie" -> "饼图";
                                case "line" -> "折线图";
                                default -> type;
                            });
                        }
                    }
                } else {
                    if (config.get("columns") instanceof List<?> cols && !cols.isEmpty()) {
                        StringBuilder cs = new StringBuilder();
                        int shown = 0;
                        for (Object c : cols) {
                            if (!(c instanceof Map<?, ?> cm)) continue;
                            if (cs.length() > 0) cs.append("、");
                            String label = strVal(cm.get("label"));
                            cs.append(!label.isEmpty() ? label : strVal(cm.get("field")));
                            if (++shown >= 6 && cols.size() > 6) { cs.append("…等").append(cols.size()).append("列"); break; }
                        }
                        if (cs.length() > 0) sb.append("\n· 显示列: ").append(cs);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Agent] 报表确认摘要生成失败，退回简要摘要", e);
        }
        return sb.toString();
    }

    private String strVal(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 主键取 Long（AI 传来的可能是 Integer/String；取不到返回 null） */
    private Long longVal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

}
