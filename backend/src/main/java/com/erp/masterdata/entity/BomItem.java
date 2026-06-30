package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("bom_item")
public class BomItem {

    @TableId(value = "bom_item_id", type = IdType.AUTO)
    private Long bomItemId;

    @TableField("bom_id")
    private Long bomId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_code")
    private String materialCode;

    @TableField("material_name")
    private String materialName;

    @TableField("specification")
    private String specification;

    @TableField("unit_id")
    private Long unitId;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("scrap_rate")
    private BigDecimal scrapRate;

    @TableField("position_no")
    private String positionNo;

    @TableField("workstation_id")
    private Long workstationId;

    @TableField("is_optional")
    private Short isOptional;

    @TableField("parent_line_no")
    private Integer parentLineNo;

    @TableField("substitute_line_no")
    private Integer substituteLineNo;

    @TableField("remark")
    private String remark;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
