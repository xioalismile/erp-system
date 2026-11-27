package com.erp.log.operation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("log_no")
    private String logNo;

    @TableField("module")
    private String module;

    @TableField("module_name")
    private String moduleName;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_name")
    private String operationName;

    @TableField("doc_id")
    private Long docId;

    @TableField("doc_no")
    private String docNo;

    @TableField("doc_type")
    private String docType;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("operation_time")
    private LocalDateTime operationTime;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_url")
    private String requestUrl;

    @TableField("request_params")
    private String requestParams;

    @TableField("result_code")
    private Integer resultCode;

    @TableField("result_message")
    private String resultMessage;

    @TableField("cost_time")
    private Long costTime;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
