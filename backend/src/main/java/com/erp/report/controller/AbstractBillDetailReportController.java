package com.erp.report.controller;

import com.erp.common.Result;
import com.erp.report.detail.BillDetailReportConfig;
import com.erp.report.detail.BillDetailReportEngine;
import com.erp.report.util.StandardExcelUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 单据明细报表 Controller 基类（销售订单 / 销售出库 / 销售发票 三个接口共用这三个方法体）。
 *
 * <p>三个报表的接口形状完全一致：
 * <ul>
 *   <li>GET {base}/columns —— 全部可选列（系统字段 + 自定义字段）+ 全部可筛选条件</li>
 *   <li>GET {base} —— 分页查询（一行 = 一条商品明细，附统计卡与合计行）</li>
 *   <li>GET {base}/export —— 按当前筛选条件导出 Excel（列 = 前端可见列）</li>
 * </ul>
 *
 * <p>筛选入参统一收进 {@code @RequestParam Map<String,String>}（{@code f_*} 前缀），
 * 由引擎按列白名单解析 —— "哪些字段能筛"的唯一事实来源是配置里的列定义，Controller 不需要跟着改。
 */
abstract class AbstractBillDetailReportController {

    /** 字段权限：明细报表导出与列表响应共用同一份不可见字段规则 */
    @Autowired
    protected com.erp.permission.service.FieldPermissionOutputFilter fieldPermissionOutputFilter;

    protected abstract BillDetailReportEngine engine();

    /** 导出文件名前缀（如「销售订单明细报表」） */
    protected abstract String exportFilePrefix();

    protected Result<Map<String, Object>> doColumns() {
        return Result.success(engine().describeColumns());
    }

    protected Result<Map<String, Object>> doPage(HttpServletRequest request,
                                                 Map<String, String> params,
                                                 Integer current,
                                                 Integer page,
                                                 Integer size,
                                                 String cols) {
        int p = current != null ? current : (page != null ? page : 1);
        int s = size != null ? size : 50;
        BillDetailReportEngine eng = engine();
        return Result.success(eng.page(params, p, s, eng.resolveScope(request), splitCols(cols)));
    }

    protected void doExport(HttpServletRequest request, HttpServletResponse response,
                            Map<String, String> params, String cols) {
        BillDetailReportEngine eng = engine();
        List<String> keys = eng.sanitizeKeys(splitCols(cols));
        List<Map<String, Object>> records = eng.exportRows(params, eng.resolveScope(request), keys);
        // 字段权限：导出文件与列表/报表响应同一份口径（按报表配置主表自动对应页面键）
        try {
            BillDetailReportConfig cfg = eng.getConfig();
            String pageKey = cfg == null ? null : cfg.mainTable;
            if (pageKey != null) {
                java.util.Set<String> hidden = fieldPermissionOutputFilter.hiddenKeys(pageKey);
                if (!hidden.isEmpty()) {
                    List<String> visibleKeys = new ArrayList<>();
                    for (String k : keys) if (!hidden.contains(k)) visibleKeys.add(k);
                    keys = visibleKeys;
                    fieldPermissionOutputFilter.filterRecords(pageKey, records);
                }
            }
        } catch (Exception e) {
            // 过滤失败不阻断导出（与响应过滤同一容错口径）
        }
        List<String> labels = eng.labelsOf(keys);

        List<String[]> headers = new ArrayList<>();
        for (int i = 0; i < keys.size() && i < labels.size(); i++) {
            headers.add(new String[]{labels.get(i), keys.get(i)});
        }
        String fileName = exportFilePrefix() + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        StandardExcelUtil.export(response, fileName, headers, records);
    }

    /** 逗号分隔的列 key → 列表（空 = 用默认可见列） */
    protected static List<String> splitCols(String cols) {
        if (cols == null || cols.trim().isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(cols.split(","))) {
            String t = s == null ? "" : s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
