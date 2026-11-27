package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下载日志表 - 对齐 PG sys_download_log 表
 * 供设置中心「下载日志」页面展示导出/下载留痕
 */
@Data
@TableName("sys_download_log")
public class SysDownloadLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人 */
    @TableField("operator")
    private String operator;

    /** 文件名 */
    @TableField("file_name")
    private String fileName;

    /** 文件类型：excel/pdf/image/zip */
    @TableField("file_type")
    private String fileType;

    /** 文件大小（字节） */
    @TableField("file_size")
    private Long fileSize;

    /** 下载来源 IP */
    @TableField("ip")
    private String ip;

    /** 下载时间 */
    @TableField("download_time")
    private LocalDateTime downloadTime;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 前端展示字段：下载时间字符串 yyyy-MM-dd HH:mm:ss（非表字段，查询后填充） */
    @TableField(exist = false)
    private String time;
}
