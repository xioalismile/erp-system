package com.erp.ai.dto;

import lombok.Data;
import java.util.Map;

/**
 * 统一AI响应
 */
@Data
public class UnifiedResponse {
    private String traceId;
    /** 回复文本 */
    private String replyText;
    /** 识别到的意图: CREATE_ORDER / QUERY_ORDER / QUERY_INVENTORY / GENERATE_REPORT / FAQ / UNKNOWN */
    private String intent;
    /** AI解析出的业务参数(JSON) */
    private Map<String, Object> extractedParams;
    /** 是否需要人工确认(写操作默认true) */
    private boolean needConfirm;
    /** 确认令牌(6位验证码) */
    private String confirmToken;
    /** 业务执行结果 */
    private Map<String, Object> actionResult;
    /** 是否成功 */
    private boolean success;
    /** 错误信息 */
    private String errorMessage;

    public static UnifiedResponse ok(String reply) {
        UnifiedResponse r = new UnifiedResponse();
        r.replyText = reply;
        r.success = true;
        return r;
    }

    public static UnifiedResponse error(String msg) {
        UnifiedResponse r = new UnifiedResponse();
        r.success = false;
        r.errorMessage = msg;
        return r;
    }
}
