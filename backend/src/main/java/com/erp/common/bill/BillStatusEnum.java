package com.erp.common.bill;

import java.util.HashMap;
import java.util.Map;

/**
 * 单据主状态统一数字枚举（金蝶K/3标准：0草稿 1待审 2已生效 7作废 + close_status独立结案标志）
 *
 * 与 CloseStatusEnum (0=未结案 1=已结案) 双字段配合使用，符合金蝶/用友标准：
 *   DocumentStatus(审批状态) + CloseStatus(完结标志) + BusStatus(业务执行态) 三字段解耦。
 *
 * 口径（2026-09-19 用户拍板）：终态显示名统一为「已生效」（原「已审核」退役为历史别名）。
 * 解析侧 BillStatusUtils 同时接受「已审核」，保证存量数据与旧调用不炸。
 */
public enum BillStatusEnum {

    DRAFT(0, "草稿"),
    REJECTED(0, "已驳回"),
    CANCELED(0, "已取消"),

    PENDING(1, "待审核"),

    APPROVED(2, "已生效"),
    CONFIRMED(2, "已确认"),
    ISSUED(2, "已下达"),
    IN_PROGRESS(2, "执行中"),
    COMPLETED(2, "已完工"),
    PARTIAL_INBOUND(2, "部分入库"),
    FULL_INBOUND(2, "全部入库"),
    CLOSED(2, "已关闭"),

    VOIDED(7, "已作废");

    private static final Map<String, BillStatusEnum> BY_LABEL = new HashMap<>();
    private static final Map<Integer, BillStatusEnum> BY_CODE_PRIMARY = new HashMap<>();

    static {
        for (BillStatusEnum e : values()) {
            BY_LABEL.put(e.label, e);
            BY_CODE_PRIMARY.putIfAbsent(e.code, e);
        }
    }

    private final int code;
    private final String label;

    BillStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public static BillStatusEnum fromCode(int code) {
        BillStatusEnum e = BY_CODE_PRIMARY.get(code);
        if (e != null) return e;
        if (code == 3 || code == 4 || code == 5 || code == 6) return APPROVED;
        throw new IllegalArgumentException("Unknown bill status code=" + code);
    }

    public static BillStatusEnum fromLabel(String label) {
        if (label == null || label.isEmpty()) return DRAFT;
        BillStatusEnum e = BY_LABEL.get(label);
        if (e != null) return e;
        if ("未审核".equals(label) || "驳回".equals(label)) return DRAFT;
        if ("待审批".equals(label) || "审批中".equals(label)) return PENDING;
        if ("审批通过".equals(label)) return APPROVED;
        if ("作废".equals(label) || "已废弃".equals(label)) return VOIDED;
        return APPROVED;
    }

    public static boolean isDraftClass(int code) { return code == 0; }
    public static boolean isPendingClass(int code) { return code == 1; }
    public static boolean isApprovedClass(int code) { return code == 2 || code == 3 || code == 4 || code == 5 || code == 6; }
    public static boolean isVoidedClass(int code) { return code == 7; }
}