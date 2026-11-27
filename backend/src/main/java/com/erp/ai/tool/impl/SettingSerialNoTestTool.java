package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：试生成编号（设置中心 → 自动编号设置），走强制确认。
 * 注意：会真实推进并消耗一个流水号，不可回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingSerialNoTestTool implements AiTool {

    private final SysSerialNoConfigService sysSerialNoConfigService;

    @Override
    public String getName() {
        return "setting_serial_no_test";
    }

    @Override
    public String getDescription() {
        return "试生成一个编号，用于验证自动编号规则效果（执行前会弹确认卡片）。"
                + "重要：会真实消耗一个流水号（计数器前移且不可回滚），请勿频繁调用。"
                + "必填参数：docType(单据类型，如 SALE_ORDER，先通过 setting_serial_no_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("docType", Map.of("type", "string", "description", "单据类型，必填，如 SALE_ORDER"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("docType"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String docType = SettingSupport.str(params == null ? null : params.get("docType"));
            if (docType == null) {
                return Map.of("success", false, "message", "参数缺失：需要 docType");
            }
            String serialNo = sysSerialNoConfigService.generateSerialNo(docType);
            log.info("[AI设置] 用户{}试生成编号 docType={} serialNo={}（已消耗一个流水号）",
                    scope != null ? scope.getUsername() : "?", docType, serialNo);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("serialNo", serialNo);
            result.put("message", "按规则生成的编号为：" + serialNo + "（注意：已真实消耗一个流水号，不可回滚）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 试生成编号失败", e);
            return Map.of("success", false, "message", "试生成编号失败: " + e.getMessage());
        }
    }
}
