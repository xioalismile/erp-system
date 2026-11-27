package com.erp.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.erp.export.annotation.ExportField;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CRM联系人管理 - 客户可拥有多个联系人
 * 对标钉钉天元CRM联系人模块
 */
@Data
@TableName("crm_contact")
public class Contact {

    @TableId(value = "contact_id", type = IdType.AUTO)
    @ExportField(label = "联系人ID", order = 0, hidden = true)
    private Long contactId;

    @TableField("contact_no")
    @ExportField(label = "联系人编号", order = 1, required = true)
    private String contactNo;

    @TableField("customer_id")
    @ExportField(label = "客户ID", order = 2, required = true)
    private Long customerId;

    @TableField("customer_name")
    @ExportField(label = "客户名称", order = 3)
    private String customerName;

    @TableField("contact_name")
    @ExportField(label = "姓名", order = 4, required = true)
    private String contactName;

    @TableField("gender")
    @ExportField(label = "性别", order = 5)
    private String gender;

    @TableField("position")
    @ExportField(label = "职务", order = 6)
    private String position;

    @TableField("department")
    @ExportField(label = "部门", order = 7)
    private String department;

    @TableField("mobile")
    @ExportField(label = "手机", order = 8)
    private String mobile;

    @TableField("phone")
    @ExportField(label = "座机", order = 9)
    private String phone;

    @TableField("email")
    @ExportField(label = "邮箱", order = 10)
    private String email;

    @TableField("wechat")
    @ExportField(label = "微信号", order = 11)
    private String wechat;

    @TableField("address")
    @ExportField(label = "联系地址", order = 12)
    private String address;

    @TableField("is_primary")
    @ExportField(label = "是否主联系人", order = 13)
    private Integer isPrimary;

    @TableField("birthday")
    @ExportField(label = "生日", order = 14, dateFormat = "yyyy-MM-dd")
    private LocalDate birthday;

    @TableField("hobby")
    @ExportField(label = "兴趣爱好", order = 15)
    private String hobby;

    @TableField("remark")
    @ExportField(label = "备注", order = 16)
    private String remark;

    @TableField("creator_id")
    @ExportField(label = "创建人ID", order = 100, hidden = true)
    private Long creatorId;

    @TableField(value = "create_date", fill = FieldFill.INSERT)
    @ExportField(label = "创建时间", order = 101, hidden = true)
    private LocalDateTime createDate;

    /** 创建人（数据权限过滤维度，AuditMetaObjectHandler 自动填充） */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @TableField("modifier_id")
    @ExportField(label = "修改人ID", order = 102, hidden = true)
    private Long modifierId;

    @TableField(value = "modify_date", fill = FieldFill.INSERT_UPDATE)
    @ExportField(label = "修改时间", order = 103, hidden = true)
    private LocalDateTime modifyDate;
}
