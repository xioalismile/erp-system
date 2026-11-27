package com.erp.report.custom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义仪表盘实体（表 custom_dashboard）
 *
 * layout_json 结构：{"cards":[{"reportId":123,"title":"覆盖名(可选)"}]}，上限 8 张卡片。
 * 所引用报表在保存与查看/取数时均做可见性双重校验（报表被删/停用/取消共享时对应卡片跳过并提示）。
 */
@Data
@TableName("custom_dashboard")
public class CustomDashboard {

    @TableId(value = "dash_id", type = IdType.AUTO)
    private Long dashId;

    /** 仪表盘编码：CD+日期+流水，自动生成 */
    @TableField("dash_code")
    private String dashCode;

    @TableField("dash_name")
    private String dashName;

    /** 卡片配置JSON：{cards:[{reportId,title}]} */
    @TableField("layout_json")
    private String layoutJson;

    /** 启用/停用 */
    @TableField("status")
    private String status;

    /** 私有/共享 */
    @TableField("share_scope")
    private String shareScope;

    /** 共享角色编码，逗号分隔（share_scope=共享时生效） */
    @TableField("share_roles")
    private String shareRoles;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}
