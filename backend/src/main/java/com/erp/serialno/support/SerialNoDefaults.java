package com.erp.serialno.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * 单据/档案编号规则 · 唯一种子清单（2026-10-12 立规收口）。
 *
 * <p><b>背景（121 生产实锤）</b>：「价格等级 / 结算方式 / 银行账户」等编号规则此前只靠
 * 一次性 SQL 在本地主库补过，从未进正式种子与启动自愈 → 服务器主库、租户模板库、
 * 克隆出的租户库全部缺失，新建价格等级报「单据类型 [BD_PRICE_LEVEL] 未配置编号规则」，
 * 典型的"本地没问题、发布到服务器就炸"。</p>
 *
 * <p><b>本类定位（唯一事实来源）</b>：系统内<b>所有</b>取编号的类型（严格取号 + 宽松兜底取号）
 * 全部登记在 {@link #DEFAULTS}；任何库（主库 / 逐租户库 / 租户模板库 / 建租户克隆后的新库）
 * 通过 {@link #ensureOn} 按"缺则补、有则不动"幂等补齐。<br>
 * 今后<b>新增取编号的功能，必须把规则加进 {@link #DEFAULTS}</b>，禁止再写一次性 SQL
 * 手工补库——那是本轮 121 事故的根因。</p>
 *
 * <p><b>安全性</b>：只补缺失行，绝不 UPDATE 已有行（不碰已有计数器 current_serial /
 * current_date / 前缀，避免回退服务器上已推进的流水）。取号侧自带两道防重
 * （业务表实况抬底 + 号段内最小空洞探测），因此种子统一从 0 起步也不会重号。</p>
 *
 * <p>列含义：docType / docName / prefix / dateFormat(空串=不带日期) / serialLength /
 * resetType(NONE|DAILY|MONTHLY|YEARLY) / separator / sortOrder / remark</p>
 */
public final class SerialNoDefaults {

    private SerialNoDefaults() {
    }

    /** 每条：{docType, docName, prefix, dateFormat, serialLength, resetType, separator, sortOrder, remark} */
    private static final String[][] DEFAULTS = {
            // ===== 销售链 =====
            {"SALE_ORDER",          "销售订单",     "SO",    "yyyyMMdd", "4", "DAILY",   "-", "1",  "销售订单编号规则：SO-yyyyMMdd-0001"},
            {"SALE_QUOTE",          "销售报价",     "QUT",   "yyyyMMdd", "4", "DAILY",   "-", "21", "销售报价单编号规则：QUT-yyyyMMdd-0001"},
            {"SALE_CONTRACT",       "销售合同",     "SCON",  "yyyyMMdd", "4", "DAILY",   "-", "22", "销售合同编号规则：SCON-yyyyMMdd-0001"},
            {"SALE_DELIVERY",       "销售出库单",   "SD",    "yyyyMMdd", "4", "DAILY",   "-", "3",  "销售出库单编号规则：SD-yyyyMMdd-0001"},
            {"SALE_RETURN",         "销售退货单",   "SR",    "yyyyMMdd", "4", "DAILY",   "-", "5",  "销售退货单编号规则：SR-yyyyMMdd-0001"},
            {"SALE_EXCHANGE",       "销售换货单",   "SEXC",  "yyyyMMdd", "4", "DAILY",   "-", "6",  "销售换货单编号规则：SEXC-yyyyMMdd-0001"},
            {"SALE_REPAIR",         "售后维修单",   "SVRV",  "yyyyMMdd", "4", "DAILY",   "-", "25", "售后维修单编号规则：SVRV-yyyyMMdd-0001"},
            {"SALE_INVOICE",        "销售发票",     "SI",    "yyyyMM",   "4", "MONTHLY", "-", "7",  "销售发票编号规则：SI-yyyyMM-0001"},
            // ===== 采购链 =====
            {"PURCHASE_ORDER",      "采购订单",     "PO",    "yyyyMMdd", "4", "DAILY",   "-", "2",  "采购订单编号规则：PO-yyyyMMdd-0001"},
            {"PURCHASE_REQUEST",    "采购申请",     "PR",    "yyyyMMdd", "4", "DAILY",   "-", "23", "采购申请编号规则：PR-yyyyMMdd-0001"},
            {"PURCHASE_CONTRACT",   "采购合同",     "PCON",  "yyyyMMdd", "4", "DAILY",   "-", "24", "采购合同编号规则：PCON-yyyyMMdd-0001"},
            {"PURCHASE_INBOUND",    "采购入库单",   "PIN",   "yyyyMMdd", "4", "DAILY",   "-", "4",  "采购入库单编号规则：PIN-yyyyMMdd-0001"},
            {"PURCHASE_RETURN",     "采购退货单",   "PRT",   "yyyyMMdd", "4", "DAILY",   "-", "6",  "采购退货单编号规则：PRT-yyyyMMdd-0001"},
            {"PURCHASE_INVOICE",    "采购发票",     "PI",    "yyyyMM",   "4", "MONTHLY", "-", "8",  "采购发票编号规则：PI-yyyyMM-0001"},
            // ===== 库存 =====
            {"STOCK_CHECK",         "库存盘点单",   "SC",    "yyyyMMdd", "4", "DAILY",   "-", "9",  "库存盘点单编号规则：SC-yyyyMMdd-0001"},
            {"STOCK_TRANSFER",      "库存调拨单",   "ST",    "yyyyMMdd", "4", "DAILY",   "-", "10", "库存调拨单编号规则：ST-yyyyMMdd-0001"},
            {"OTHER_RECEIPT",       "其他入库单",   "OR",    "yyyyMMdd", "4", "DAILY",   "-", "11", "其他入库单编号规则：OR-yyyyMMdd-0001"},
            {"OTHER_ISSUE",         "其他出库单",   "OI",    "yyyyMMdd", "4", "DAILY",   "-", "12", "其他出库单编号规则：OI-yyyyMMdd-0001"},
            {"MATERIAL_REQUISITION","领料单",       "MR",    "yyyyMMdd", "4", "DAILY",   "-", "13", "领料单编号规则：MR-yyyyMMdd-0001"},
            // ===== 生产 =====
            {"PRODUCTION_ORDER",    "生产订单",     "MO",    "yyyyMMdd", "4", "DAILY",   "-", "14", "生产订单编号规则：MO-yyyyMMdd-0001"},
            {"WORK_ORDER",          "工单",         "WO",    "yyyyMMdd", "4", "DAILY",   "-", "15", "工单编号规则：WO-yyyyMMdd-0001"},
            {"MRP_PLAN",            "MRP计划",      "MP",    "yyyyMMdd", "4", "DAILY",   "-", "30", "MRP计划编号规则：MP-yyyyMMdd-0001"},
            {"OPERATION_REPORT",    "报工单",       "ORP",   "yyyyMMdd", "4", "DAILY",   "-", "34", "报工单编号规则：ORP-yyyyMMdd-0001"},
            {"PRODUCTION_RECEIPT",  "生产入库单",   "PRS",   "yyyyMMdd", "4", "DAILY",   "-", "35", "生产入库单编号规则：PRS-yyyyMMdd-0001"},
            {"PRODUCE_ASSEMBLE",    "组装单",       "ASM",   "yyyyMMdd", "4", "DAILY",   "-", "36", "组装单编号规则：ASM-yyyyMMdd-0001"},
            {"PRODUCE_DISASSEMBLE", "拆卸单",       "DASM",  "yyyyMMdd", "4", "DAILY",   "-", "37", "拆卸单编号规则：DASM-yyyyMMdd-0001"},
            {"ROUTING_OP",          "工序",         "OP",    "yyyyMMdd", "4", "DAILY",   "-", "83", "工序编号：OP-日期-流水（工艺路线内每道工序自动生成）"},
            // ===== 委外 =====
            {"SUBCONTRACT_ORDER",      "委外订单",   "SCO", "yyyyMMdd", "4", "DAILY", "-", "40", "委外订单编号规则：SCO-yyyyMMdd-0001"},
            {"SUBCONTRACT_PICK",       "委外领料单", "SCP", "yyyyMMdd", "4", "DAILY", "-", "41", "委外领料单编号规则：SCP-yyyyMMdd-0001"},
            {"SUBCONTRACT_INBOUND",    "委外入库单", "SCI", "yyyyMMdd", "4", "DAILY", "-", "42", "委外入库单编号规则：SCI-yyyyMMdd-0001"},
            {"SUBCONTRACT_SUPPLEMENT", "委外补料单", "SCS", "yyyyMMdd", "4", "DAILY", "-", "43", "委外补料单编号规则：SCS-yyyyMMdd-0001"},
            {"SUBCONTRACT_RETURN",     "委外退料单", "SCR", "yyyyMMdd", "4", "DAILY", "-", "44", "委外退料单编号规则：SCR-yyyyMMdd-0001"},
            // ===== 财务 =====
            {"ACCOUNT_RECEIVABLE",  "应收单",       "AR",  "yyyyMMdd", "4", "DAILY", "-", "50", "应收单编号规则：AR-yyyyMMdd-0001"},
            {"ACCOUNT_PAYABLE",     "应付单",       "AP",  "yyyyMMdd", "4", "DAILY", "-", "51", "应付单编号规则：AP-yyyyMMdd-0001"},
            {"RECEIPT",             "收款单",       "RCP", "yyyyMMdd", "4", "DAILY", "-", "52", "收款单编号规则：RCP-yyyyMMdd-0001"},
            {"PAYMENT",             "付款单",       "PAY", "yyyyMMdd", "4", "DAILY", "-", "53", "付款单编号规则：PAY-yyyyMMdd-0001"},
            {"OPERATION_EXPENSE",   "费用分摊单",   "OE",  "yyyyMMdd", "4", "DAILY", "-", "54", "费用分摊单编号规则：OE-yyyyMMdd-0001"},
            {"SALE_EXPENSE",        "销售费用单",   "SE",  "yyyyMMdd", "4", "DAILY", "-", "55", "销售费用单编号规则：SE-yyyyMMdd-0001"},
            {"PURCHASE_EXPENSE",    "采购费用单",   "PE",  "yyyyMMdd", "4", "DAILY", "-", "56", "采购费用单编号规则：PE-yyyyMMdd-0001"},
            {"SALE_PRE_RECEIPT",    "预收款单",     "SPR", "yyyyMMdd", "4", "DAILY", "-", "57", "预收款单编号规则：SPR-yyyyMMdd-0001"},
            {"PURCHASE_PRE_PAYMENT","预付款单",     "PPP", "yyyyMMdd", "4", "DAILY", "-", "58", "预付款单编号规则：PPP-yyyyMMdd-0001"},
            {"WAGE_SETTLEMENT",     "工序工资核算", "WG",  "yyyyMMdd", "4", "DAILY", "-", "122", "工序工资核算单编号规则：WG-yyyyMMdd-0001（2026-10-12 收口补种）"},
            // ===== CRM / 获客 =====
            {"LEAD",             "线索",         "LD",  "yyyyMMdd", "4", "DAILY", "-", "60", "线索编号规则：LD-yyyyMMdd-0001"},
            {"OPPORTUNITY",      "商机",         "OPP", "yyyyMMdd", "4", "DAILY", "-", "61", "商机编号规则：OPP-yyyyMMdd-0001"},
            {"CONTACT",          "联系人",       "CT",  "",         "4", "NONE",  "",  "62", "联系人编号规则：CT0001"},
            {"FOLLOW_UP",        "跟进记录",     "FU",  "yyyyMMdd", "4", "DAILY", "-", "118", "跟进记录编号规则：FU-yyyyMMdd-0001（2026-10-12 收口补种）"},
            {"COMPLAINT",        "客户投诉",     "C",   "yyyyMMdd", "4", "DAILY", "-", "119", "客户投诉编号规则：C-yyyyMMdd-0001（2026-10-12 收口补种）"},
            {"SURVEY",           "调查问卷",     "SUR", "yyyyMMdd", "4", "DAILY", "-", "117", "调查问卷编号规则：SUR-yyyyMMdd-0001（2026-10-12 收口补种）"},
            {"CRM_PROMOTION",    "促销记录",     "PR",  "yyyyMMdd", "4", "DAILY", "-", "120", "促销记录编号规则：PR-yyyyMMdd-0001（2026-10-12 收口补种）"},
            {"ACQUISITION_TASK", "采集任务",     "ACQ", "yyyyMMdd", "4", "DAILY", "-", "121", "采集任务编号规则：ACQ-yyyyMMdd-0001（2026-10-12 收口补种）"},
            // ===== 打样 / 样品 =====
            {"SAMPLE_ORDER",      "样品单",     "SAMPLE", "yyyyMMdd", "4", "DAILY", "-", "70", "样品单编号规则：SAMPLE-yyyyMMdd-0001"},
            {"FREE_SAMPLE_ORDER", "免费样品单", "FS",     "yyyyMMdd", "4", "DAILY", "-", "20", "免费样品单编号规则：FS-yyyyMMdd-0001"},
            // ===== 基础资料 / 主档 =====
            {"CUSTOMER",             "客户档案",   "CUS",  "",         "4", "NONE",  "", "17",  "客户档案编号规则"},
            {"SUPPLIER",             "供应商档案", "SUP",  "",         "4", "NONE",  "", "18",  "供应商档案编号规则"},
            {"MATERIAL",             "物料档案",   "MAT",  "",         "4", "NONE",  "", "19",  "物料档案编号规则"},
            {"MD_CUSTOMER",          "客户档案",   "CUS",  "yyyyMMdd", "4", "DAILY", "-", "105", "客户编码规则：CUS-yyyyMMdd-0001"},
            {"MD_SUPPLIER",          "供应商档案", "SUP",  "yyyyMMdd", "4", "DAILY", "-", "106", "供应商编码规则：SUP-yyyyMMdd-0001"},
            {"MD_MATERIAL",          "物料档案",   "MAT",  "",         "6", "NONE",  "", "107", "物料编码（如 MAT000001）"},
            {"MD_BOM",               "BOM物料清单","BOM",  "yyyyMMdd", "4", "DAILY", "-", "111", "BOM单编码规则：BOM-yyyyMMdd-0001"},
            {"BOM",                  "物料清单",   "BOM",  "yyyy",     "4", "YEARLY","-", "16",  "物料清单编号规则"},
            {"BD_UNIT",              "计量单位",   "U",    "",         "3", "NONE",  "", "110", "计量单位编码（如 U001）"},
            {"BD_CURRENCY",          "币种档案",   "CUR",  "",         "3", "NONE",  "", "109", "币种编码（如 CUR001）"},
            {"BD_DEPARTMENT",        "部门档案",   "DEPT", "",         "4", "NONE",  "", "103", "部门编码（如 DEPT0001）"},
            {"BD_EMPLOYEE",          "员工档案",   "EMP",  "yyyyMMdd", "4", "DAILY", "-", "102", "员工工号编码规则：EMP-yyyyMMdd-0001"},
            {"EMPLOYEE",             "员工档案",   "EMP",  "",         "4", "NONE",  "", "84",  "员工编号规则：EMP0001（默认自动生成，可改为手动）"},
            {"BD_WAREHOUSE",         "仓库档案",   "WH",   "",         "3", "NONE",  "", "104", "仓库编码（如 WH001）"},
            {"BD_MATERIAL_CATEGORY", "物料分类",   "CAT",  "",         "4", "NONE",  "", "108", "物料分类编码（如 CAT0001）"},
            {"BD_SETTLE_MODE",       "结算方式",   "SETTLE", "",       "3", "NONE",  "", "113", "结算方式编码（如 SETTLE001）——2026-10-12 收口补种（此前只有一次性SQL，服务器全缺）"},
            {"BD_PAYMENT_TERM",      "付款条件",   "PT",   "",         "3", "NONE",  "", "114", "付款条件编码（如 PT001）——2026-10-12 收口补种（链路保留，防缺规则报错）"},
            {"BD_PRICE_LEVEL",       "价格等级",   "PL",   "",         "3", "NONE",  "", "115", "价格等级编码（如 PL001）——2026-10-12 收口补种（121 新租户实锤缺失）"},
            {"BANK_ACCOUNT",         "银行账户",   "BANK", "",         "4", "NONE",  "", "116", "银行账户编码（如 BANK0001）——2026-10-12 收口补种（同价格等级一起的隐形雷）"},
            // ===== 系统 =====
            {"SYS_ROLE",        "系统角色",     "ROLE", "", "4", "NONE", "", "101", "系统角色编码（如 ROLE0001）"},
            {"WF_APPROVAL_FLOW","审批流程定义", "FLOW", "", "4", "NONE", "", "112", "审批流程编码（如 FLOW0001）"},
    };

    /**
     * 把指定单据类型的「自动生成编号」开关对齐为默认值（默认开启）。
     *
     * <p><b>背景（2026-10-25 用户实锤）</b>：物料分类 BD_MATERIAL_CATEGORY 的规则行在历史库里
     * auto_generate=0（当年按"父级局部编号"设计刻意关掉），而 {@link #ensureOn} 是"有则不动"，
     * 于是模板库克隆出的每个新租户开箱仍是手动输入编码，与「初始化时编码全部默认自动生成」冲突。</p>
     *
     * <p><b>调用边界（重要）</b>：只允许在<b>建租户链路</b>与<b>租户模板库自愈</b>里调用，
     * 绝不对存量租户库执行——租户管理员在设置中心主动关掉的开关不能被启动自愈翻回去。</p>
     *
     * @return 本次实际被改回默认值的行数；表不存在等异常由调用方决定是否吞掉（本方法不吞）
     */
    public static int alignDefaultAutoGenerateOn(JdbcTemplate jdbc, String... docTypes) {
        if (docTypes == null || docTypes.length == 0) {
            return 0;
        }
        int fixed = 0;
        for (String docType : docTypes) {
            if (docType == null || docType.trim().isEmpty()) {
                continue;
            }
            fixed += jdbc.update(
                    "UPDATE sys_serial_no_config SET auto_generate = 1, updated_at = CURRENT_TIMESTAMP "
                          + "WHERE doc_type = ? AND (auto_generate IS NULL OR auto_generate = 0)",
                    docType.trim());
        }
        return fixed;
    }

    /**
     * 对指定库幂等补齐编号规则（缺则补、有则不动）。
     *
     * @return 本次实际新增的规则条数；表不存在等异常由调用方决定是否吞掉（本方法不吞，便于调用方统一告警）
     */
    public static int ensureOn(JdbcTemplate jdbc) {
        // 一次性取该库已有类型，逐条只补缺失（避免逐条 COUNT 往返）
        Set<String> existing = new HashSet<>();
        for (java.util.Map<String, Object> row : jdbc.queryForList(
                "SELECT doc_type FROM sys_serial_no_config")) {
            Object v = row.get("doc_type");
            if (v != null) {
                existing.add(String.valueOf(v).trim());
            }
        }
        int added = 0;
        for (String[] d : DEFAULTS) {
            if (existing.contains(d[0])) {
                continue; // 有则不动：绝不 UPDATE 已有规则与计数器
            }
            added += jdbc.update(
                    "INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, "
                          + "reset_type, separator, current_serial, \"current_date\", sort_order, enabled, "
                          + "auto_generate, remark) VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL, ?, 1, 1, ?)",
                    d[0], d[1], d[2], d[3], Integer.parseInt(d[4]), d[5], d[6],
                    Integer.parseInt(d[7]), d[8]);
        }
        return added;
    }

    /** 清单条数（供启动日志/自检用） */
    public static int size() {
        return DEFAULTS.length;
    }
}
