package com.erp.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户分群规则 */
@Data
@TableName("customer_segment")
public class CustomerSegment {

    @TableId(value = "segment_id", type = IdType.AUTO)
    @ExportField(label = "分群ID", order = 0, hidden = true)
    private Long segmentId;

    @TableField("segment_name")
    @ExportField(label = "分群名称", order = 1, required = true)
    private String segmentName;

    @TableField("segment_desc")
    @ExportField(label = "分群描述", order = 2)
    private String segmentDesc;

    /** 规则JSON，如：[{"field":"crm_status","op":"eq","value":"意向客户"}] */
    @TableField("rule_json")
    @ExportField(label = "规则配置", order = 3, hidden = true)
    private String ruleJson;

    @TableField("is_dynamic")
    @ExportField(label = "是否动态分群", order = 4)
    private Integer isDynamic;

    /** 备注 */
    @TableField("remark")
    @ExportField(label = "备注", order = 6)
    private String remark;

    @TableField("created_by")
    @ExportField(label = "创建人ID", order = 5, hidden = true)
    private Long createdBy;

    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 100, hidden = true)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    @ExportField(label = "更新时间", order = 101, hidden = true)
    private LocalDateTime updatedAt;
}
