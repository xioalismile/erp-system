package com.erp.ai.bill;

import java.util.List;
import java.util.Map;

/**
 * 单据明细行（子表）适配器 —— 每种单据一份，负责"读明细 / 改一行 / 走原 Service 整单保存"。
 *
 * <p><b>为什么必须走原 Service</b>：单据明细是"单价→金额→税额→合计"一条链，
 * 直连子表改一列会让主表金额与明细对不上。走 {@code saveXxxWithItems} 才能保证
 * 重算、校验（含设置中心各项校验）、状态检查（已生效/已完结不可改）与界面完全一致。
 *
 * <p>接入新单据时：实现本接口 + 标 {@code @Component} 即可（{@link BillItemRegistry} 自动收集），
 * 不需要改工具代码。
 */
public interface BillItemHandler {

    /** 与 {@link BillActionRegistry} 的 billType 一一对应（如 SALE_ORDER） */
    String billType();

    /** 明细实体中文名（确认卡片/提示用），如"销售订单明细" */
    String itemLabel();

    /** 明细行主键字段名（如 orderItemId） */
    String itemIdField();

    /** 明细行号字段名（如 lineNo；没有则返回 null） */
    default String lineNoField() {
        return "lineNo";
    }

    /** 允许 AI 修改的字段白名单（实体真实字段名；金额/税额等派生列**不在其中**） */
    List<String> editableFields();

    /** 读主表（不存在返回 null） */
    Object loadBill(Long billId);

    /** 读明细行（按父单主键，按行号升序） */
    List<?> loadItems(Long billId);

    /** 主表上的单据标识（单号），用于确认卡片与答复 */
    String billNo(Object bill);

    /** 主表关键信息（改后回显用，如 合计/状态/客户），键为中文 */
    Map<String, Object> billSummary(Object bill);

    /** 明细行 → 可读 Map（给 AI 展示 / 前端表格） */
    Map<String, Object> toItemMap(Object item, int lineNo);

    /** 取明细行主键 */
    Long itemId(Object item);

    /** 取明细行号（可为 null） */
    Integer lineNo(Object item);

    /** 把 patch 应用到明细行（调用前已校验字段在白名单内） */
    void applyPatch(Object item, Map<String, Object> patch);

    /** 整单保存（必须调原 Service 的 saveXxxWithItems，保证重算与校验） */
    void save(Object bill, List<?> items);
}
