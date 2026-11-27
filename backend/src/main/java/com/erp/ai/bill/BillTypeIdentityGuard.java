package com.erp.ai.bill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 单据身份校验（2026-11-22 二期补登的防越权配套）。
 *
 * <p>背景：生产领料单 / 生产退料单 / 生产补料单共用 material_requisition 一张表，
 * 靠 bill_type（PICK / RETURN / SUPPLEMENT）区分，三者在菜单与角色权限里是三个独立权限码。
 * AI 工具（bill_action / bill_item_list / bill_item_update）此前只按调用方声明的 billType
 * 做权限判定，然后直接用主键操作记录 —— 不核对记录自身的 bill_type，就会出现
 * 「声明生产退料单，实际改/作废了生产领料单」：既是越权，也会改错单据。
 *
 * <p>本类只对**一表多身份**的单据做校验；未登记的类型直接放行，行为与升级前一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillTypeIdentityGuard {

    /** 一表多身份的单据规格：表名 / 主键列 / 类型列 / 期望类型值 / 中文名 */
    private record Rule(String table, String pkColumn, String typeColumn, String expected, String label) {
    }

    /** aiBillType → 规格（表名与列名均为代码常量，不接受任何外部输入拼接） */
    private static final Map<String, Rule> RULES = Map.of(
            "PRODUCTION_RETURN", new Rule("material_requisition", "requisition_id", "bill_type", "RETURN", "生产退料单"),
            "PRODUCTION_SUPPLEMENT", new Rule("material_requisition", "requisition_id", "bill_type", "SUPPLEMENT", "生产补料单")
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 校验「声明的单据类型」与「记录自身的类型」是否一致。
     *
     * @return null = 放行（无需校验 / 记录不存在 / 类型一致）；否则为可直接展示给用户的中文拒绝原因
     */
    public String check(String billType, Long billId) {
        Rule rule = billType == null ? null : RULES.get(billType);
        if (rule == null || billId == null) return null;
        try {
            List<String> actual = jdbcTemplate.queryForList(
                    "SELECT " + rule.typeColumn() + " FROM " + rule.table()
                            + " WHERE " + rule.pkColumn() + " = ?",
                    String.class, billId);
            // 记录不存在：交给各工具原有的「没找到该单据」分支，不在这里改变提示口径
            if (actual.isEmpty()) return null;
            return assess(billType, billId, actual.get(0));
        } catch (Exception e) {
            log.warn("[AI单据身份] 校验失败，已拒绝执行 billType={} billId={}: {}", billType, billId, e.getMessage());
            return "单据身份校验失败，已拒绝执行本次操作，请稍后重试";
        }
    }

    /** 纯判定逻辑：抽出以便单元测试直接覆盖，不依赖数据库 */
    String assess(String billType, Long billId, String actualBillType) {
        Rule rule = billType == null ? null : RULES.get(billType);
        if (rule == null) return null;
        String actual = actualBillType == null ? "" : actualBillType.trim();
        if (rule.expected().equalsIgnoreCase(actual)) return null;
        return "单据类型不一致：主键 " + billId + " 不是「" + rule.label()
                + "」，已拒绝执行，请确认单据类型与主键是否对应";
    }
}
