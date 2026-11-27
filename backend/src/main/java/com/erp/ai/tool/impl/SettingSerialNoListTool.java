package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.serialno.entity.SysSerialNoConfig;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询自动编号配置（设置中心 → 自动编号设置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingSerialNoListTool implements AiTool {

    private final SysSerialNoConfigService sysSerialNoConfigService;

    @Override
    public String getName() {
        return "setting_serial_no_list";
    }

    @Override
    public String getDescription() {
        return "查询自动编号配置列表（只读）。适用于：查看各单据类型的编号前缀/日期格式/流水位数/重置规则/是否自动生成。"
                + "参数：keyword(可选，按单据类型docType或单据名称docName模糊过滤)。"
                + "修改请用 setting_serial_no_update，删除请用 setting_serial_no_delete，试生成请用 setting_serial_no_test。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "可选，按 docType 或 docName 模糊过滤"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String keyword = SettingSupport.str(params == null ? null : params.get("keyword"));
            List<SysSerialNoConfig> list = sysSerialNoConfigService.listAll();
            List<Map<String, Object>> items = new ArrayList<>();
            if (list != null) {
                for (SysSerialNoConfig c : list) {
                    if (keyword != null) {
                        String kw = keyword.toLowerCase();
                        String docType = c.getDocType() == null ? "" : c.getDocType().toLowerCase();
                        String docName = c.getDocName() == null ? "" : c.getDocName();
                        if (!docType.contains(kw) && !docName.contains(keyword)) continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", c.getId());
                    item.put("docType", c.getDocType());
                    item.put("docName", c.getDocName());
                    item.put("prefix", c.getPrefix());
                    item.put("dateFormat", c.getDateFormat());
                    item.put("serialLength", c.getSerialLength());
                    item.put("resetType", c.getResetType());
                    item.put("separator", c.getSeparator());
                    item.put("currentSerial", c.getCurrentSerial());
                    item.put("currentDate", c.getCurrentDate());
                    item.put("sortOrder", c.getSortOrder());
                    item.put("enabled", c.getEnabled());
                    item.put("autoGenerate", c.getAutoGenerate());
                    item.put("remark", c.getRemark());
                    items.add(item);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", items.size());
            result.put("items", items);
            result.put("message", "共查到 " + items.size() + " 条自动编号配置。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询自动编号配置失败", e);
            return Map.of("success", false, "message", "查询自动编号配置失败: " + e.getMessage());
        }
    }
}
