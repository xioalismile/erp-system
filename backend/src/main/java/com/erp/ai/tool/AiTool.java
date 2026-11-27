package com.erp.ai.tool;

import com.erp.ai.dto.AiDataScope;

import java.util.Map;

public interface AiTool {

    String getName();

    String getDescription();

    Map<String, Object> getParameters();

    Map<String, Object> execute(Map<String, Object> params, Long userId);

    /**
     * 带数据权限上下文的执行入口（ReAct链路统一调用此方法）。
     * 默认委托给旧签名；需要数据权限过滤或超管校验的工具覆写此方法。
     */
    default Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        return execute(params, scope != null ? scope.getUserId() : null);
    }
}
