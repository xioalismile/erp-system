package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.report.custom.service.CustomReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 只读工具：列出可用报表数据源及其字段元数据，供AI对话式设计报表时先了解可用字段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportListDataSourcesTool implements AiTool {

    private final CustomReportService customReportService;

    @Override
    public String getName() {
        return "report_list_datasources";
    }

    @Override
    public String getDescription() {
        return "列出系统自定义报表可用的全部数据源（含每个数据源的字段清单、字段类型和标签）。"
                + "用户想通过AI创建报表时，必须先调用本工具了解可用的数据源和字段，"
                + "再根据字段设计报表列/筛选/排序。可用 dataSourceKey 调用 describe 参数进一步查看字段详情。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "dataSourceKey", Map.of("type", "string",
                                "description", "可选。传入则返回该数据源的字段详情；不传返回全部数据源列表")),
                "required", java.util.List.of()
        );
    }

    /**
     * 跨单关联提示：直接放进工具返回里，避免模型看到 describe 的 joins 为空就误判"不能关联"。
     */
    private static final String CROSS_JOIN_HINT =
            "任意两个白名单数据源都可通过 report_create_custom 的 extraJoins 按字段值对接（不要求系统预先登记关联，"
            + "也不要求两个数据源彼此有关联）。以物料档案(material)为主、同时挂销售订单明细(sale_order_item)与"
            + "销售出库明细(sale_delivery_item)（按 materialCode 对接）是支持的标准写法；"
            + "describe 返回的 joins 为空只表示没有自动关联，不表示不能关联。";

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        try {
            String key = params.get("dataSourceKey") == null ? null : params.get("dataSourceKey").toString();
            if (key != null && !key.isBlank()) {
                Map<String, Object> detail = customReportService.describeDataSource(key.trim());
                return Map.of("success", true, "dataSourceKey", key.trim(),
                        "detail", detail, "crossJoinHint", CROSS_JOIN_HINT);
            }
            return Map.of("success", true, "dataSources", customReportService.listDataSources(),
                    "crossJoinHint", CROSS_JOIN_HINT);
        } catch (Exception e) {
            log.error("[AI报表] 查询数据源失败", e);
            return Map.of("success", false, "message", "查询数据源失败: " + e.getMessage());
        }
    }

    /**
     * 带权限上下文：字段清单与设计器同一口径，不可见字段不下发给模型
     * （小助手跑在线程池，没有请求上下文，必须按 scope 显式解析）。
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String key = params == null || params.get("dataSourceKey") == null
                    ? null : params.get("dataSourceKey").toString();
            if (key != null && !key.isBlank()) {
                Map<String, Object> detail = customReportService.describeDataSource(key.trim(),
                        scope == null ? null : scope.getUserId(),
                        scope == null ? null : scope.isAdmin());
                return Map.of("success", true, "dataSourceKey", key.trim(),
                        "detail", detail, "crossJoinHint", CROSS_JOIN_HINT);
            }
            return Map.of("success", true, "dataSources", customReportService.listDataSources(),
                    "crossJoinHint", CROSS_JOIN_HINT);
        } catch (Exception e) {
            log.error("[AI报表] 查询数据源失败", e);
            return Map.of("success", false, "message", "查询数据源失败: " + e.getMessage());
        }
    }
}
