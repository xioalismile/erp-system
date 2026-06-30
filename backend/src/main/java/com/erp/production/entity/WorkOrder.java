package com.erp.production.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号: WO+年月日+4位流水 */
    private String workOrderNo;

    private LocalDate orderDate;

    /** 产品物料编码 */
    private String productCode;
    private String productName;
    private String specification;
    private String unit;

    /** 计划生产数量 */
    private BigDecimal plannedQuantity;

    /** 已完工数量 */
    private BigDecimal completedQuantity;

    /** 报废数量 */
    private BigDecimal scrapQuantity;

    /** 计划开始日期 */
    private LocalDate plannedStartDate;

    /** 计划完工日期 */
    private LocalDate plannedFinishDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际完工日期 */
    private LocalDate actualFinishDate;

    /** 来源: MANUAL手动 / SALE_ORDER销售驱动 / MRP MRP生成 */
    private String sourceType;

    /** 来源销售订单号 */
    private String sourceOrderNo;

    /** 车间/产线 */
    private String workshop;

    /** 状态: 待开工/已开工/已完工/已关闭/已取消 */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;

    @TableLogic
    private Integer deleted;

    /** 工单用料明细(非数据库字段) */
    @TableField(exist = false)
    private List<WorkOrderMaterial> materials;
}
