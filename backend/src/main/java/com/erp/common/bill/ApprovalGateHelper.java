package com.erp.common.bill;

import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 单据审批开关统一网关。
 *
 * <p>规则（与设置中心「单据审批开关」页面语义一致）：
 * <ul>
 *   <li>全局开关 approval.enabled = false → 所有无审批（提交即生效）</li>
 *   <li>单据级开关 approval.bill.{BILL_TYPE}.enabled = true → 需要审批（提交后待审核）</li>
 *   <li>单据级开关默认 false → 免审批（提交即生效）</li>
 * </ul>
 * </p>
 *
 * <p>所有单据 Service 在「提交」时调用 {@link #needApproval(String)} 判断：
 * 免审批单据提交后直接置「已生效」生效（复用各自 approve 的业务影响逻辑）；
 * 启用审批的单据才进入「待审核」。</p>
 */
@Component
@RequiredArgsConstructor
public class ApprovalGateHelper {

    private final SysConfigService sysConfigService;

    /**
     * 判断某单据类型当前是否需要走人工审批。
     *
     * @param billType 单据类型英文编码，如 SALE_ORDER / PURCHASE_REQUEST（与设置中心一致）
     * @return true=需要审批（提交后待审核）；false=免审批（提交即生效）
     */
    public boolean needApproval(String billType) {
        if (billType == null || billType.isBlank()) return false; // 未接入开关体系的单据一律免审批
        boolean globalEnabled = sysConfigService.getBooleanValue("approval.enabled", true);
        if (!globalEnabled) return false;
        return sysConfigService.getBooleanValue("approval.bill." + billType + ".enabled", false);
    }
}
