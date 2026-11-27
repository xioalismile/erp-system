package com.erp.report.custom.service;

import com.erp.report.custom.entity.CustomReport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * 自定义报表服务
 */
public interface CustomReportService {

    /** 数据源元数据（设计器用） */
    Map<String, Object> listDataSources();

    Map<String, Object> describeDataSource(String dataSourceKey);

    /**
     * 数据源元数据（带字段权限）。
     * userId/admin 为 null 时按请求上下文解析（页面设计器）；AI 工具在无请求上下文的线程里执行，
     * 需传入当前用户显式过滤不可见字段。
     */
    Map<String, Object> describeDataSource(String dataSourceKey, Long userId, Boolean admin);

    /** 跨单据关联候选列（双方树中同名的可关联外键列，含受限角色跨模块过滤） */
    Map<String, Object> joinables(HttpServletRequest req, String dataSourceKey, String targetKey);

    /** 我的报表 + 共享给我的报表 */
    Map<String, Object> listMyReports(HttpServletRequest req, String keyword, int page, int size);

    /** 新建（编码自动生成） */
    Map<String, Object> create(HttpServletRequest req, CustomReport report);

    /** 新建（AI工具入口：由调用方传入操作人用户名，逻辑与create一致） */
    Map<String, Object> createForAi(String username, CustomReport report);

    /** 更新（仅创建人或管理员） */
    Map<String, Object> update(HttpServletRequest req, Long id, CustomReport report);

    /** 删除（仅创建人或管理员） */
    void delete(HttpServletRequest req, Long id);

    /** 复制 */
    Map<String, Object> copy(HttpServletRequest req, Long id);

    /** 详情（校验可见性） */
    Map<String, Object> detail(HttpServletRequest req, Long id);

    /** 运行报表（分页） */
    Map<String, Object> run(HttpServletRequest req, Long id, String startDate, String endDate,
                            String keyword, String status, int page, int size);

    /**
     * 运行报表（分页，支持运行时日期筛选字段覆盖）：
     * 报表未配置 dateField 时，查看页可用 dateSource/dateField 临时指定日期筛选字段（仅本次请求生效）
     */
    Map<String, Object> run(HttpServletRequest req, Long id, String startDate, String endDate,
                            String keyword, String status, int page, int size,
                            String dateSource, String dateField);

    /**
     * 运行报表（在上一重载基础上追加「任意字段过滤条件」）。
     *
     * @param conditionsJson 运行时过滤条件 JSON 数组
     *                       [{source,field,op,value,value2,values}]，null/空 = 无附加条件（旧行为）
     */
    Map<String, Object> run(HttpServletRequest req, Long id, String startDate, String endDate,
                            String keyword, String status, int page, int size,
                            String dateSource, String dateField, String conditionsJson);

    /** 取可见报表实体（存在 + 本人/共享角色/管理员，不校验状态；仪表盘等复用） */
    CustomReport getReportIfVisible(HttpServletRequest req, Long id);

    /** 取可运行报表实体（可见 + 启用 + 数据源有效；仪表盘数据卡片等复用） */
    CustomReport requireRunnable(HttpServletRequest req, Long id);

    /** 导出 Excel */
    void export(HttpServletRequest req, HttpServletResponse resp, Long id, String startDate, String endDate,
                String keyword, String status);

    /** 导出 Excel（支持运行时日期筛选字段覆盖，语义同 run 重载） */
    void export(HttpServletRequest req, HttpServletResponse resp, Long id, String startDate, String endDate,
                String keyword, String status, String dateSource, String dateField);

    /** 导出 Excel（在上一重载基础上追加「任意字段过滤条件」，conditionsJson 语义同 run 重载） */
    void export(HttpServletRequest req, HttpServletResponse resp, Long id, String startDate, String endDate,
                String keyword, String status, String dateSource, String dateField, String conditionsJson);
}
