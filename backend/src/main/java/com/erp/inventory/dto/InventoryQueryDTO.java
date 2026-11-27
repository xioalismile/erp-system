package com.erp.inventory.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 即时库存列表 / 导出的统一查询参数（2026-09-22）。
 *
 * <p><b>为什么要抽成一个对象</b>：本次修复要求"列表里看到什么，导出就必须带什么"。
 * 原先 {@code /inventory/list} 的十多个筛选参数写在 Controller 方法签名里、导出走另一条
 * 完全独立的取数（不套任何筛选）→ 两边必然漂移（历史现象：导出出现列表看不到的空行、
 * 且不带页面上的物料自定义字段列）。抽成 DTO 后列表与导出共用同一段条件构造代码，
 * 从结构上保证口径一致。</p>
 */
@Data
public class InventoryQueryDTO {

    /** 分页（仅列表用；导出固定取全量） */
    private Integer current;
    private Integer size;

    /** 仓库（必须落在当前用户可见仓库范围内） */
    private Long stockId;
    private Long materialId;
    private String batchNo;
    private String materialName;
    private String materialCode;
    /** 列表页通用口径：一个框同时模糊匹配物料编码 / 物料名称 */
    private String materialKeyword;
    /** 物料自定义字段搜索（JSON {fieldKey: 值}），走 sys_custom_field_value EAV 表 */
    private String customSearch;
    private String sortBy;
    private String sortOrder;
    /** 只看有库存（quantity > 0） */
    private Boolean onlyPositive;
    /** 只看零库存（quantity = 0） */
    private Boolean zeroOnly;
    /** 数据看板穿透参数 */
    private String materialType;
    private Long lineId;
    private Integer ageOver;
    private Boolean staleOnly;
    private Boolean belowMinStock;

    /**
     * 导出专用：导出列（按页面当前可见列的显示顺序）。
     * 每项结构 {"key":"物料编码字段名","label":"页面列名","custom":true/false}；
     * 为空时导出默认的固定 12 列（兼容老调用）。
     */
    private List<Map<String, Object>> fields;
}
