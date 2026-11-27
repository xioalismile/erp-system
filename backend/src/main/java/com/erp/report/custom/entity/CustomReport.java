package com.erp.report.custom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义报表定义实体（表 custom_report）
 *
 * data_source 必须是后端 EntityMetadataRegistry 白名单注册的 key；
 * config_json 为报表配置（列/筛选字段/排序），保存与执行前均经白名单校验。
 */
@Data
@TableName("custom_report")
public class CustomReport {

    @TableId(value = "report_id", type = IdType.AUTO)
    private Long reportId;

    /** 报表编码：CR+yyyyMMdd+4位流水，自动生成 */
    @TableField("report_code")
    private String reportCode;

    @TableField("report_name")
    private String reportName;

    /** 数据源key（白名单），如 sale_order_item */
    @TableField("data_source")
    private String dataSource;

    /** 配置JSON：{columns:[{field,label,source}],dateField,keywordField,statusField,orderBy:[{field,dir}]} */
    @TableField("config_json")
    private String configJson;

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
