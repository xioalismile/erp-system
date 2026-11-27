package com.erp.production.service;

import com.erp.production.entity.WorkOrderShare;

import java.util.List;
import java.util.Map;

/**
 * 生产工单进度对外分享服务（2026-11-25）。
 *
 * <p>职责：生成 / 查询 / 停止分享，以及免登录公开读取（令牌校验 + 过期校验 + 租户定位）。
 * 公开读取只返回生产进度相关字段，不返回客户、价格、成本、附件等敏感信息。</p>
 */
public interface WorkOrderShareService {

    /** 生成分享（校验工单已生效 + 当前用户数据权限） */
    WorkOrderShare create(Long orderId, String title, String remark, Integer days,
                          Boolean showAssignee, Boolean showPlanDate, Boolean showMaterialCode,
                          Long userId, String userName);

    /** 某工单的分享记录（按创建时间倒序，最多 50 条） */
    List<WorkOrderShare> listByOrder(Long orderId);

    /** 停止分享（置为已停止，公开页立即失效） */
    void revoke(Long shareId);

    /**
     * 免登录公开读取。
     *
     * <p>返回结构固定含 {@code state}：OK / EXPIRED / STOPPED / NOT_FOUND / ERROR；
     * 非 OK 时只带中文提示，不带任何工单数据。</p>
     */
    Map<String, Object> publicView(String token, jakarta.servlet.http.HttpServletRequest request);
}
