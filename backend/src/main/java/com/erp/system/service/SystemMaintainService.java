package com.erp.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据维护 - 一键清空资料
 *
 * 两种模式：
 *   1. clearBusiness：清空全部业务单据（销售/采购/库存/生产/财务/委外/CRM 单据/日志等），
 *      保留基础档案（物料/客户/供应商/仓库/员工/部门等）与系统配置。
 *   2. clearAll：清空全部资料 = 业务单据 + 全部基础档案，
 *      保留系统用户(admin自动恢复)/角色/角色权限/菜单/字典/系统参数/打印模板/编号配置/自定义报表。
 *
 * 安全规范：
 *   - 表清单白名单硬编码，永不触碰 sys_user(自动恢复)、sys_role、sys_role_permission、sys_menu、
 *     sys_dict 系列表、sys_config(仅清 employee_perm_ 前缀)、custom_report、custom_dashboard、租户与 License 相关表
 *   - 逐表 TRUNCATE ... RESTART IDENTITY CASCADE，不存在的表自动跳过
 *   - 每次清空后写一条操作日志留痕（sys_operate_log 虽在清空列表内，清完补写本次记录）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMaintainService {

    private final JdbcTemplate jdbcTemplate;

    /** 业务单据表（两种模式都清） */
    private static final List<String> BUSINESS_TABLES = List.of(
            // 销售
            "sale_quote_item", "sale_quote", "sale_order_item", "sale_order",
            "sale_contract_item", "sale_contract",
            "sale_delivery_item", "sale_delivery", "sale_return_item", "sale_return",
            "sale_invoice_item", "sale_invoice",
            // 采购
            "purchase_request_item", "purchase_request", "purchase_order_item", "purchase_order",
            "purchase_receipt_item", "purchase_receipt", "purchase_return_item", "purchase_return",
            "purchase_invoice_item", "purchase_invoice", "purchase_contract_item", "purchase_contract",
            // 库存
            "inventory_current", "inventory_ledger", "inventory_summary", "inventory_freeze",
            "other_receipt_item", "other_receipt", "other_issue_item", "other_issue",
            "material_requisition_item", "material_requisition",
            "stock_transfer_item", "stock_transfer", "stock_check_item", "stock_check",
            "batch", "serial_number", "serial_number_trace", "batch_inventory", "batch_info",
            "inventory_warning", "inventory_warning_log",
            // 生产（含工单成品/工序明细 —— 原先漏项）
            "production_order_item", "work_order_product", "work_order_operation", "production_order",
            "process_card_item", "process_card",
            "operation_report_item", "operation_report", "production_receipt_item", "production_receipt",
            "quality_check_item", "quality_check",
            "mrp_demand", "mrp_supply", "mrp_plan_item", "mrp_plan",
            "produce_assemble_detail", "produce_assemble_bom", "produce_assemble",
            "produce_disassemble_detail", "produce_disassemble_bom", "produce_disassemble",
            // 财务
            "account_receivable_item", "account_receivable", "account_payable_item", "account_payable",
            "cash_receipt_item", "cash_receipt_writeoff", "cash_receipt",
            "cash_payment_item", "cash_payment_writeoff", "cash_payment",
            "bill_writeoff_detail",
            "voucher_entry", "voucher", "account_balance",
            "cost_calculation_item", "cost_calculation",
            "sale_statement_item", "sale_statement", "purchase_statement_item", "purchase_statement",
            // 预算
            "budget_item", "budget",
            // 资金调拨单（财务单据）
            "fund_transfer_item", "fund_transfer",
            // CRM 单据
            "crm_follow_up", "crm_lead", "crm_opportunity", "crm_complaint", "crm_survey",
            // 销售目标
            "sales_target_company", "sales_target_employee",
            // 价格策略
            "sale_price_strategy_item", "sale_price_strategy",
            "purchase_price_strategy_item", "purchase_price_strategy",
            // 费用
            "sale_expense_item", "sale_expense", "purchase_expense_item", "purchase_expense",
            "operation_expense_allocation", "operation_expense",
            // 预收预付
            "sale_pre_receipt_item", "sale_pre_receipt", "purchase_pre_payment_item", "purchase_pre_payment",
            // 委外
            "subcontract_order_item", "subcontract_order", "subcontract_pick_item", "subcontract_pick",
            "subcontract_supplement_item", "subcontract_supplement",
            "subcontract_inbound_item", "subcontract_inbound", "subcontract_return_item", "subcontract_return",
            // 样品
            "sample_order_item", "sample_order",
            // 客户驱动
            "customer_pool_claim_log", "customer_segment",
            "free_sample_order_item", "free_sample_order",
            // 信誉额度
            "credit_limit_log", "credit_limit",
            // 获客采集
            "acquisition_task", "acquisition_result",
            // 审批运行数据（审批流程配置 approval_flow/node/condition/node_approver 保留不动）
            "approval_task", "approval_log", "approval_notification",
            // 日志与过程数据
            "sys_operate_log", "sys_operate_log_detail", "ai_audit_log", "operation_log",
            "doc_trace", "data_version_record", "print_log", "sys_import_task",
            "ai_token_usage", "sys_notification", "sys_download_log"
    );

    /** 基础档案表（仅"清空全部资料"模式追加） */
    private static final List<String> BASEDATA_TABLES = List.of(
            "material", "material_category", "bom", "bom_item",
            "unit_of_measure", "currency", "payment_term", "settlement_mode",
            "price_level", "account", "bank_account", "location", "warehouse",
            "department", "employee",
            "customer", "supplier", "crm_contact", "fixed_asset",
            "work_center", "workstation", "routing", "routing_operation",
            "crm_customer_segment", "crm_customer_tag_relation"
    );

    /** admin 账号恢复用初始密码哈希（与部署基线一致：admin123） */
    private static final String ADMIN_DEFAULT_PASSWORD_HASH =
            "$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK";

    /** 清空业务单据（保留基础档案） */
    @Transactional
    public Map<String, Object> clearBusiness() {
        Map<String, Object> result = truncateTables(BUSINESS_TABLES);
        // 客户档案保留，但业务统计字段归零
        jdbcTemplate.update(
                "UPDATE customer SET follow_up_count=0, order_count=0, total_order_amount=0, " +
                "refund_amount=0, complaint_count=0, business_opportunity_count=0, " +
                "last_order_date=NULL, last_follow_up_date=NULL, pool_type=0, pool_enter_date=NULL, " +
                "satisfaction_score=0 WHERE pool_type IS NOT NULL OR follow_up_count>0 OR order_count>0");
        result.put("mode", "clearBusiness");
        log.warn("[数据维护] 清空业务单据完成: truncated={}, skipped={}, failed={}",
                result.get("truncated"), result.get("skipped"), result.get("failed"));
        return result;
    }

    /** 清空全部资料（业务单据 + 基础档案） */
    @Transactional
    public Map<String, Object> clearAll() {
        List<String> all = new ArrayList<>(BUSINESS_TABLES);
        all.addAll(BASEDATA_TABLES);
        Map<String, Object> result = truncateTables(all);
        // 员工权限补充配置随员工档案一并清理（config_key 前缀精确匹配）
        jdbcTemplate.update("DELETE FROM sys_config WHERE config_key LIKE 'employee_perm_%'");
        // TRUNCATE employee CASCADE 会级联清空 sys_user/sys_user_role，恢复 admin 及超管角色关联
        restoreAdminUser();
        result.put("mode", "clearAll");
        log.warn("[数据维护] 清空全部资料完成: truncated={}, skipped={}, failed={}",
                result.get("truncated"), result.get("skipped"), result.get("failed"));
        return result;
    }

    /** 逐表 TRUNCATE（RESTART IDENTITY CASCADE），不存在的表跳过，失败的表收集返回 */
    private Map<String, Object> truncateTables(List<String> tables) {
        int truncated = 0;
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String table : tables) {
            try {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = current_schema() AND table_name = ?",
                        Integer.class, table);
                if (exists == null || exists == 0) {
                    skipped.add(table);
                    continue;
                }
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
                truncated++;
            } catch (Exception e) {
                log.error("[数据维护] TRUNCATE 失败: {} - {}", table, e.getMessage());
                failed.add(table + ": " + e.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("truncated", truncated);
        r.put("skippedCount", skipped.size());
        r.put("skipped", skipped);
        r.put("failed", failed);
        return r;
    }

    /** 恢复 admin 系统账号与超管角色关联（参照 clear_business_data.sql 基线逻辑） */
    private void restoreAdminUser() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (user_id, user_code, username, password, real_name, user_type, is_admin, is_enabled, status) " +
                "VALUES (1, 'U001', 'admin', ?, '系统管理员', '系统用户', 1, 1, '启用') " +
                "ON CONFLICT (username) DO NOTHING", ADMIN_DEFAULT_PASSWORD_HASH);
        // 幂等恢复 admin 角色关联：不能只靠 ON CONFLICT DO NOTHING ——
        // 线上库缺少 DDL 声明的 UNIQUE(user_id, role_id)，该 INSERT 没有冲突目标，
        // 每次清空都会给 admin 重复插一遍角色（实测踩到）。故显式用 NOT EXISTS 去重。
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id) " +
                "SELECT 1, r.role_id FROM sys_role r " +
                "WHERE r.role_code IN ('SUPER_ADMIN','ADMIN') " +
                "  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur " +
                "                  WHERE ur.user_id = 1 AND ur.role_id = r.role_id)");
        // 注意：这是一条 SELECT，必须用 execute()，不能 use update()。
        // jdbcTemplate.update() 走 Statement.executeUpdate()，而 PG JDBC 驱动遇到
        // "会返回结果集却调用了 executeUpdate" 时会抛
        // "A result was returned when none was expected."（驱动 zh_CN 语言包译为
        // "传回预期之外的结果"），最终被包装成 DataIntegrityViolationException，
        // 表现为清空时报"数据保存失败：请检查必填字段是否完整"，且整个事务回滚、
        // 一张表都没清掉（实测踩到）。
        jdbcTemplate.execute(
                "SELECT setval(pg_get_serial_sequence('sys_user','user_id'), " +
                "GREATEST((SELECT MAX(user_id) FROM sys_user), 1), true)");
    }

    /** 清空完成后写一条操作日志留痕（sys_operate_log 已被清空，补写本次记录） */
    public void writeOperateLog(String mode, Map<String, Object> result, String username) {
        try {
            String content = String.format("一键清空(%s)：清空%d张表，跳过%d张，失败%d张",
                    "clearAll".equals(mode) ? "全部资料" : "业务单据",
                    (Integer) result.get("truncated"),
                    (Integer) result.get("skippedCount"),
                    ((List<?>) result.get("failed")).size());
            jdbcTemplate.update(
                    "INSERT INTO sys_operate_log (user_code, user_name, real_name, operate_time, " +
                    "operate_type, operate_result, module_name, operate_content, operate_summary, request_url, request_method) " +
                    "VALUES ('-', ?, ?, ?, '清空数据', '成功', '系统维护', ?, ?, '/api/system/maintain/" +
                    ("clearAll".equals(mode) ? "clear-all" : "clear-business") + "', 'POST')",
                    username, username, LocalDateTime.now(), content, content);
        } catch (Exception e) {
            log.error("[数据维护] 写操作日志失败: {}", e.getMessage());
        }
    }
}
