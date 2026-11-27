package com.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 操作日志表 - 对齐 PG sys_operate_log 表
 */
@Data
@TableName("sys_operate_log")
public class SysOperateLog {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    @TableField("user_code")
    private String userCode;

    @TableField("user_name")
    private String userName;

    @TableField("real_name")
    private String realName;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("operate_time")
    private LocalDateTime operateTime;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("mac_address")
    private String macAddress;

    @TableField("host_name")
    private String hostName;

    @TableField("browser_info")
    private String browserInfo;

    @TableField("operate_type")
    private String operateType;

    @TableField("operate_result")
    private String operateResult;

    @TableField("fail_reason")
    private String failReason;

    @TableField("bill_type")
    private String billType;

    @TableField("bill_no")
    private String billNo;

    /**
     * 单据跳转信息（非库字段，列表查询时按 bill_type + bill_no 反查填充）：
     * 前端据此把「单据编号」渲染成可点链接（见 frontend/src/utils/billRoute.js）。
     * 反查不到（单据已删/无独立编辑页）时留空 → 前端退化为纯文本。
     */
    @TableField(exist = false)
    private String billDocType;

    @TableField(exist = false)
    private Long billId;

    @TableField("operate_content")
    private String operateContent;

    @TableField("operate_summary")
    private String operateSummary;

    @TableField("module_name")
    private String moduleName;

    @TableField("request_url")
    private String requestUrl;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_params")
    private String requestParams;

    @TableField("session_id")
    private String sessionId;

    @TableField("duration")
    private Integer duration;

    @TableField("org_code")
    private String orgCode;

    @TableField("trace_id")
    private String traceId;

    @TableField("archive_date")
    private LocalDate archiveDate;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
