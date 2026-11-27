package com.erp.report.custom.service;

import com.erp.report.custom.entity.CustomDashboard;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 自定义仪表盘服务
 *
 * 安全要点：
 * - 保存时校验每张卡片引用的报表对当前用户可见（防引用他人私有报表）
 * - 查看/取数时逐卡片再校验（报表被删/停用/取消共享时该卡片跳过并提示）
 * - 编辑/删除：仅创建人或 ADMIN、MANAGER（与自定义报表口径一致）
 */
public interface CustomDashboardService {

    /** 我的仪表盘 + 共享给我的 */
    Map<String, Object> listMyDashboards(HttpServletRequest req, String keyword, int page, int size);

    /** 新建（编码 CD+日期+流水自动生成） */
    Map<String, Object> create(HttpServletRequest req, CustomDashboard dashboard);

    /** 更新（仅创建人或管理员） */
    Map<String, Object> update(HttpServletRequest req, Long id, CustomDashboard dashboard);

    /** 删除（仅创建人或管理员） */
    void delete(HttpServletRequest req, Long id);

    /** 详情（含卡片解析：报表名/模式/图表类型/是否可访问） */
    Map<String, Object> detail(HttpServletRequest req, Long id);

    /** 查看数据：逐卡片执行报表（汇总=前500条+合计，明细=前10行+总数），单卡片失败不影响其它 */
    Map<String, Object> data(HttpServletRequest req, Long id);
}
