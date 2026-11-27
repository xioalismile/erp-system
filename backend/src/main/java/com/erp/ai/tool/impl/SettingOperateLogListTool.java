package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.common.PageResult;
import com.erp.system.entity.SysOperateLog;
import com.erp.system.service.SysOperateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询操作日志（设置中心 → 操作日志）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingOperateLogListTool implements AiTool {

    private final SysOperateLogService sysOperateLogService;

    @Override
    public String getName() {
        return "setting_operate_log_list";
    }

    @Override
    public String getDescription() {
        return "查询操作日志（分页，只读）。适用于：审计谁在什么时候做了什么操作、排查单据变更历史。"
                + "参数：keyword(可选，按用户/内容关键词过滤)、operateType(可选，操作类型如 新增/修改/删除/审核)、"
                + "moduleName(可选，模块名如 销售管理)、startDate(可选，起始日期 yyyy-MM-dd)、"
                + "endDate(可选，结束日期 yyyy-MM-dd)、page(默认1)、size(默认20)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "可选，按用户/内容关键词过滤"));
        props.put("operateType", Map.of("type", "string", "description", "可选，操作类型：新增/修改/删除/审核等"));
        props.put("moduleName", Map.of("type", "string", "description", "可选，模块名，如 销售管理"));
        props.put("startDate", Map.of("type", "string", "description", "可选，起始日期 yyyy-MM-dd"));
        props.put("endDate", Map.of("type", "string", "description", "可选，结束日期 yyyy-MM-dd"));
        props.put("page", Map.of("type", "integer", "description", "页码，默认1"));
        props.put("size", Map.of("type", "integer", "description", "每页条数，默认20"));
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
            Map<String, Object> p = params == null ? Map.of() : params;
            String keyword = SettingSupport.str(p.get("keyword"));
            String operateType = SettingSupport.str(p.get("operateType"));
            String moduleName = SettingSupport.str(p.get("moduleName"));
            String startDate = SettingSupport.str(p.get("startDate"));
            String endDate = SettingSupport.str(p.get("endDate"));
            Integer page = SettingSupport.integer(p.get("page"));
            Integer size = SettingSupport.integer(p.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;

            PageResult<SysOperateLog> pr = sysOperateLogService.pageQuery(
                    current, pageSize, keyword, operateType, moduleName, startDate, endDate, null, null, null);
            List<Map<String, Object>> items = new ArrayList<>();
            if (pr != null && pr.getRecords() != null) {
                for (SysOperateLog l : pr.getRecords()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("logId", l.getLogId());
                    item.put("userName", l.getUserName());
                    item.put("realName", l.getRealName());
                    item.put("operateTime", l.getOperateTime());
                    item.put("operateType", l.getOperateType());
                    item.put("operateResult", l.getOperateResult());
                    item.put("moduleName", l.getModuleName());
                    item.put("billType", l.getBillType());
                    item.put("billNo", l.getBillNo());
                    item.put("operateContent", l.getOperateContent());
                    item.put("operateSummary", l.getOperateSummary());
                    item.put("ipAddress", l.getIpAddress());
                    items.add(item);
                }
            }
            long total = pr == null ? 0 : pr.getTotal();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", total);
            result.put("items", items);
            result.put("message", "共查到 " + total + " 条操作日志（第 " + current + " 页）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询操作日志失败", e);
            return Map.of("success", false, "message", "查询操作日志失败: " + e.getMessage());
        }
    }
}
