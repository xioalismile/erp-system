package com.erp.log.trace.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_trace")
public class DocTrace {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("trace_no")
    private String traceNo;

    @TableField("source_doc_id")
    private Long sourceDocId;

    @TableField("source_doc_no")
    private String sourceDocNo;

    @TableField("source_doc_type")
    private String sourceDocType;

    @TableField("target_doc_id")
    private Long targetDocId;

    @TableField("target_doc_no")
    private String targetDocNo;

    @TableField("target_doc_type")
    private String targetDocType;

    @TableField("source_item_id")
    private Long sourceItemId;

    @TableField("target_item_id")
    private Long targetItemId;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_name")
    private String materialName;

    @TableField("quantity")
    private java.math.BigDecimal quantity;

    @TableField("trace_direction")
    private String traceDirection;

    @TableField("create_time")
    private LocalDateTime createTime;
}
