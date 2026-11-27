package com.erp.kai.service.adapter;

import com.erp.kai.entity.KaiExtConnection;

import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 第三方系统适配器统一接口（FEAT-KAI-STANDALONE-001）。
 * 四类标准动作：测试连接 / 查资料 / 建档（导入资料·智能分类）/ 开单与单据动作。
 * 每家第三方 ERP 一个实现；实现内部负责把标准动作翻译成对方 API。
 *
 * 对象类型第一期固定四种（objectType）：
 *   material=物料  customer=客户  supplier=供应商  sale_order=销售订单
 */
public interface KaiExtAdapter {

    /** 适配器类型标识（对应连接 type）：DEMO / GENERIC_REST */
    String type();

    /** 测试连接：能通返回说明信息，不通抛 IllegalArgumentException（中文原因）。 */
    Map<String, Object> testConnection(KaiExtConnection conn);

    /** 查资料：按对象类型 + 条件（keyword / limit 等）返回记录列表。 */
    List<Map<String, Object>> listRecords(KaiExtConnection conn, String objectType, Map<String, Object> filters);

    /**
     * 建档（导入资料 / 建分类）：批量写入，逐行查重。
     * 返回 {total, successCount, failCount, success:[...], failed:[{index, name, reason}]}。
     */
    Map<String, Object> createRecords(KaiExtConnection conn, String objectType, List<Map<String, Object>> records);

    /** 开单：创建草稿单据，返回 {billNo, status, ...}。 */
    Map<String, Object> createBill(KaiExtConnection conn, Map<String, Object> bill);

    /** 单据动作：如 submit（草稿→提交）。返回动作后的单据状态。 */
    Map<String, Object> executeAction(KaiExtConnection conn, String action, Map<String, Object> params);

    /** 该适配器支持的对象类型清单（objectType → 中文名）。 */
    Map<String, String> supportedObjectTypes();

    /** 该适配器某对象类型的可写字段说明（字段 → 中文名），供 AI 生成导入计划。 */
    Map<String, String> writableFields(String objectType);
}
