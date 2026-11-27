package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 推广记录（销售过程动作量化 · 2026-10-07）
 *
 * <p>支撑「每周朋友圈推广 3 次」与「RFID 素材覆盖率 100%」两条过程指标。
 * 朋友圈本期按"截图必填 + 主管抽查"判定（企业微信自动判断列入后续单独立项）。</p>
 */
@Data
@TableName("crm_promotion_record")
public class PromotionRecord {

    @TableId(value = "promotion_id", type = IdType.AUTO)
    @ExportField(label = "记录ID", order = 0, hidden = true)
    private Long promotionId;

    @TableField("promotion_no")
    @ExportField(label = "记录编号", order = 1)
    private String promotionNo;

    @TableField("promotion_date")
    @ExportField(label = "推广日期", order = 2, dateFormat = "yyyy-MM-dd")
    private LocalDate promotionDate;

    @TableField("staff_id")
    @ExportField(label = "推广人ID", order = 3, hidden = true)
    private Long staffId;

    @TableField("staff_name")
    @ExportField(label = "推广人", order = 4)
    private String staffName;

    /** 素材类型：朋友圈图片/朋友圈视频/产品图/车间视频/RFID素材（写死 5 项，与方案逐条对应） */
    @TableField("material_type")
    @ExportField(label = "素材类型", order = 5, required = true)
    private String materialType;

    @TableField("material_name")
    @ExportField(label = "素材名称", order = 6)
    private String materialName;

    /** 发送对象类型：客户/客户群 */
    @TableField("target_type")
    @ExportField(label = "发送对象类型", order = 7)
    private String targetType;

    /** 发送对象数量（由明细自动汇总） */
    @TableField("target_count")
    @ExportField(label = "发送对象数量", order = 8)
    private Integer targetCount;

    /** 截图（图片地址）；为空不计入周推广次数 */
    @TableField("attachment")
    @ExportField(label = "截图", order = 9)
    private String attachment;

    /** 是否达标：1=是 0=否 */
    @TableField("is_qualified")
    @ExportField(label = "是否达标", order = 10)
    private Integer isQualified;

    @TableField("remark")
    @ExportField(label = "备注", order = 11)
    private String remark;

    @TableField("creator_id")
    @ExportField(label = "创建人ID", order = 100, hidden = true)
    private Long creatorId;

    /** 创建人（数据权限过滤维度，AuditMetaObjectHandler 自动填充） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createDate;

    @TableField("modifier_id")
    private Long modifierId;

    @TableField(value = "modify_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime modifyDate;

    // ===== 非库字段：明细（发送对象客户清单） =====

    /** 发送对象客户（前端提交时带上；落 crm_promotion_target） */
    @TableField(exist = false)
    private java.util.List<Long> customerIds;

    /** 发送对象客户名称（列表展示用） */
    @TableField(exist = false)
    private String customerNames;
}
