package com.erp.common.bill;

/**
 * 单据「业务执行态」独立枚举（金蝶/用友三字段模型的第三个字段）。
 *
 * <p>背景：通用单据模型是 {@code DocumentStatus(status, 审批态) + CloseStatus(close_status, 完结标志)
 * + BusStatus(bus_status, 业务执行态)}。前两个字段已全站落地，BusStatus 此前只在
 * {@link BillStatusEnum} 的注释里提及、没有独立承载列 —— 于是「已下达 / 执行中 / 已完工 /
 * 部分入库 / 全部入库 / 已关闭」这些**业务执行态**被塞进了 {@code status}
 * （{@link BillStatusEnum} 里它们的 code 全是 2），造成 status 值域膨胀、与审批态语义混淆。</p>
 *
 * <p>本枚举与 {@code bus_status} 列（DDL：{@code deploy/sql/66_bus_status_columns.sql}
 * = {@code db/109_bus_status_columns.sql}）配套，口径：</p>
 * <ul>
 *   <li>{@code status} = 审批态：0 草稿 / 1 待审核 / 2 已生效 / 7 已作废（{@link BillStatusEnum}）</li>
 *   <li>{@code close_status} = 完结标志：0 未结案 / 1 已结案（{@link CloseStatusEnum}）</li>
 *   <li>{@code bus_status} = 业务执行态：本枚举</li>
 * </ul>
 *
 * <p><b>⚠️ 迁移状态（2026-09-20 · 双写期）</b>：实测 {@code status="已关闭"} 被 12+ 处消费点依赖
 * （库存 5 张单据的 reopen 守卫、采购申请/采购订单 dashboard 的"已关闭"计数、
 * 工作台终态判断 {@code MyDashboardHelper}、前端状态下拉与颜色映射等）。为满足守则 04 · 2.7.2-6
 * 「升级后用户看到的页面与旧版一模一样」，本轮采用 <b>同值双写</b>：</p>
 * <ol>
 *   <li>原有写 {@code status} 的代码<b>保持不变</b>（原有判定链路零影响）；</li>
 *   <li>同一处新增一行写入 {@code bus_status}，值为同一个执行态标签；</li>
 *   <li>存量数据 {@code bus_status} 为 NULL —— 前端显示仍取 {@code status}，用户无感知。</li>
 * </ol>
 * <p>待消费点全部改为按 {@code close_status} / {@code bus_status} 判定后，再停止向 {@code status}
 * 写执行态（届时 status 才真正收敛为"0/1/2/7"四值）。</p>
 */
public enum BusStatusEnum {

    NONE(0, "未开始"),
    ISSUED(1, "已下达"),
    IN_PROGRESS(2, "执行中"),
    COMPLETED(3, "已完工"),
    PARTIAL_INBOUND(4, "部分入库"),
    FULL_INBOUND(5, "全部入库"),
    CLOSED(6, "已关闭");

    private final int code;
    private final String label;

    BusStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    /** 标签 → 枚举；未知或空返回 null（调用方自行兜底，不猜） */
    public static BusStatusEnum fromLabel(String label) {
        if (label == null || label.isBlank()) return null;
        for (BusStatusEnum e : values()) {
            if (e.label.equals(label)) return e;
        }
        return null;
    }

    /** 序号 → 枚举；null 或越界返回 null */
    public static BusStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (BusStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    /** 是否为"业务已终止"的执行态 */
    public boolean isClosed() { return this == CLOSED; }
}
