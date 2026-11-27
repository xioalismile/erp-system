package com.erp.common.bill;

/** 结案标志（与 status 独立，金蝶/用友标准：一个业务字段+一个完结标志双字段） */
public enum CloseStatusEnum {

    OPEN(0, "未结案"),
    CLOSED(1, "已结案");

    private final int code;
    private final String label;

    CloseStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public static CloseStatusEnum fromCode(Integer code) {
        if (code == null) return OPEN;
        return code == 1 ? CLOSED : OPEN;
    }

    public static boolean isClosed(Integer code) {
        return fromCode(code) == CLOSED;
    }
}
