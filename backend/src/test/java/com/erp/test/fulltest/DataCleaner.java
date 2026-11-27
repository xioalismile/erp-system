package com.erp.test.fulltest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * 数据清理工具
 * 在测试前/后清理测试产生的数据, 确保测试隔离
 *
 * ============ 多租户铁律（2026-09-20 改造）============
 * 本类通过 JDBC **直连**数据库，因此存在"清错库"的致命风险：
 *   - 原来写死 `jdbc:postgresql://localhost:5432/erp_db`；
 *   - 当测试目标是 8cloud（kaiyu.pro）时，这条连接会连到**本机的 erp_db**（开发主库），
 *     把本机数据当成被测环境的测试数据删掉。
 *
 * 处置：**默认关闭**。只有显式配置 `erp.test.db-url` 时才执行直连清理。
 *   跑测试租户时，清理请用 HTTP 侧脚本（frontend/tests/smoke/reset-tenant-data.mjs）
 *   或 SSH 到目标库执行，不要把本机库当目标。
 *
 * 配置示例：
 *   -Derp.test.db-url=jdbc:postgresql://localhost:5432/erp_tenant_autotest
 *   -Derp.test.db-user=erp_user  -Derp.test.db-password=xxxxxx
 */
public class DataCleaner {

    private static final String DB_URL = prop("erp.test.db-url", "ERP_TEST_DB_URL", "");
    private static final String DB_USER = prop("erp.test.db-user", "ERP_TEST_DB_USER", "");
    private static final String DB_PWD = prop("erp.test.db-password", "ERP_TEST_DB_PASSWORD", "");

    /** 未配置 db-url 即视为"不可直连清理"，所有方法转为显式告警后跳过 */
    private static final boolean DIRECT_DB_ENABLED = !DB_URL.isEmpty();

    private static String prop(String sysKey, String envKey, String dflt) {
        String v = System.getProperty(sysKey);
        if (v == null || v.isEmpty()) v = System.getenv(envKey);
        return (v == null || v.isEmpty()) ? dflt : v;
    }

    private static void warnDisabled(String action) {
        System.out.println("[DataCleaner] 跳过" + action + "：未配置 erp.test.db-url（直连清理默认关闭，"
                + "防止把本机 erp_db 当成被测环境而误删）。测试租户请改用 tests/smoke/reset-tenant-data.mjs。");
    }

    /** 清理所有测试数据(按前缀匹配), 在 @AfterAll 中调用 */
    public void cleanupByPrefix(String prefix) {
        if (!DIRECT_DB_ENABLED) { warnDisabled("按前缀清理 prefix=" + prefix); return; }
        List<String> sqls = Arrays.asList(
                // 按编码前缀清理, 顺序: 子表先于主表
                "DELETE FROM cost_calculation_item WHERE calc_id IN (SELECT id FROM cost_calculation WHERE calc_no LIKE '%" + prefix + "%')",
                "DELETE FROM cost_calculation WHERE calc_no LIKE '%" + prefix + "%'",
                "DELETE FROM operation_expense_allocation WHERE expense_id IN (SELECT expense_id FROM operation_expense WHERE expense_no LIKE '%" + prefix + "%')",
                "DELETE FROM operation_expense WHERE expense_no LIKE '%" + prefix + "%'",
                "DELETE FROM sale_order_item WHERE order_id IN (SELECT order_id FROM sale_order WHERE order_no LIKE '%" + prefix + "%')",
                "DELETE FROM sale_order WHERE order_no LIKE '%" + prefix + "%'",
                "DELETE FROM sale_quote_item WHERE quote_id IN (SELECT quote_id FROM sale_quote WHERE quote_no LIKE '%" + prefix + "%')",
                "DELETE FROM sale_quote WHERE quote_no LIKE '%" + prefix + "%'",
                "DELETE FROM purchase_order_item WHERE order_id IN (SELECT order_id FROM purchase_order WHERE order_no LIKE '%" + prefix + "%')",
                "DELETE FROM purchase_order WHERE order_no LIKE '%" + prefix + "%'",
                "DELETE FROM purchase_request_item WHERE request_id IN (SELECT request_id FROM purchase_request WHERE request_no LIKE '%" + prefix + "%')",
                "DELETE FROM purchase_request WHERE request_no LIKE '%" + prefix + "%'",
                "DELETE FROM material_requisition_item WHERE requisition_id IN (SELECT requisition_id FROM material_requisition WHERE requisition_no LIKE '%" + prefix + "%')",
                "DELETE FROM material_requisition WHERE requisition_no LIKE '%" + prefix + "%'",
                "DELETE FROM inventory_transaction WHERE remark LIKE '%" + prefix + "%'",
                "DELETE FROM material WHERE material_code LIKE '%" + prefix + "%'",
                "DELETE FROM customer WHERE customer_code LIKE '%" + prefix + "%'",
                "DELETE FROM supplier WHERE supplier_code LIKE '%" + prefix + "%'",
                "DELETE FROM crm_lead WHERE lead_no LIKE '%" + prefix + "%'",
                "DELETE FROM crm_opportunity WHERE opp_no LIKE '%" + prefix + "%'",
                "DELETE FROM work_order WHERE order_no LIKE '%" + prefix + "%'",
                "DELETE FROM unit_of_measure WHERE unit_name LIKE '%" + prefix + "%'",
                "DELETE FROM warehouse WHERE warehouse_code LIKE '%" + prefix + "%'",
                "DELETE FROM department WHERE dept_code LIKE '%" + prefix + "%'",
                "DELETE FROM employee WHERE emp_code LIKE '%" + prefix + "%'"
        );
        executeBatch(sqls);
        System.out.println("[DataCleaner] 按前缀清理完成: " + prefix);
    }

    /** 清理指定表中的指定ID记录 */
    public void cleanupById(String table, String idColumn, Long id) {
        if (id == null) return;
        if (!DIRECT_DB_ENABLED) { warnDisabled("按ID清理 " + table + "." + idColumn + "=" + id); return; }
        execute("DELETE FROM " + table + " WHERE " + idColumn + " = " + id);
    }

    /** 执行单条SQL */
    private void execute(String sql) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PWD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            // 忽略表不存在的错误
            if (!e.getMessage().contains("does not exist")) {
                System.err.println("[DataCleaner] SQL执行失败: " + sql + " -> " + e.getMessage());
            }
        }
    }

    /** 批量执行SQL */
    private void executeBatch(List<String> sqls) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PWD)) {
            for (String sql : sqls) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.executeUpdate();
                } catch (SQLException e) {
                    // 忽略单条失败, 继续执行
                }
            }
        } catch (SQLException e) {
            System.err.println("[DataCleaner] 数据库连接失败: " + e.getMessage());
        }
    }
}
