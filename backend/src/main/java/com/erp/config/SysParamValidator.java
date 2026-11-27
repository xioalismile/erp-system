package com.erp.config;

import com.erp.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 设置中心业务规则校验器
 * <p>
 * 集中封装 sys_config 中各业务校验开关的读取与校验逻辑。
 * 各业务Service在保存单据/档案前调用对应方法完成设置中心规则校验，
 * 校验失败抛出 {@link BusinessException}，错误信息以"【设置中心-XXX】"开头。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysParamValidator {

    private final SysConfigService sysConfigService;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    // ====================== 优先级1：核心业务规则 ======================

    /**
     * 来源单号校验（优先级1.1）
     * <p>
     * 当配置项为 true 时，若单据没有来源单号则阻止保存。
     * </p>
     *
     * @param configKey   配置项 key（如 sale.order.require.source）
     * @param billTypeName 单据类型中文名（如 "销售订单"），用于错误提示
     * @param sourceBillNo 单据的来源单号
     */
    public void validateRequireSource(String configKey, String billTypeName, String sourceBillNo) {
        boolean require = sysConfigService.getBooleanValue(configKey, false);
        if (require && (sourceBillNo == null || sourceBillNo.isBlank())) {
            throw new BusinessException("【设置中心-来源单号校验】" + billTypeName + "需要有来源单号才能保存");
        }
    }

    /**
     * 客户名称查重（优先级1.2）
     * <p>
     * 当 sys.customer.duplicate.check 为 true 时，若客户名称已存在则阻止保存。
     * 调用方需先查询是否存在同名客户（排除自身ID），将结果传入 duplicateExists。
     * </p>
     *
     * @param customerName  客户名称
     * @param duplicateExists 是否已存在同名客户
     */
    public void validateCustomerNameDuplicate(String customerName, boolean duplicateExists) {
        boolean checkEnabled = sysConfigService.getBooleanValue("sys.customer.duplicate.check", true);
        if (checkEnabled && duplicateExists) {
            throw new BusinessException("【设置中心-客户查重】客户名称[" + customerName + "]已存在，不允许保存");
        }
    }

    /**
     * 货品名称查重（优先级1.3）
     * <p>
     * 当 sys.material.duplicate.check 为 true 时，若货品名称已存在则阻止保存。
     * </p>
     *
     * @param materialName  货品名称
     * @param duplicateExists 是否已存在同名货品
     */
    public void validateMaterialNameDuplicate(String materialName, boolean duplicateExists) {
        boolean checkEnabled = sysConfigService.getBooleanValue("sys.material.duplicate.check", true);
        if (checkEnabled && duplicateExists) {
            throw new BusinessException("【设置中心-货品查重】货品名称[" + materialName + "]已存在，不允许保存");
        }
    }

    /**
     * 供应商名称查重
     * 当 sys.supplier.duplicate.check 为 true 时，若供应商名称已存在则阻止保存。
     */
    public void validateSupplierNameDuplicate(String supplierName, boolean duplicateExists) {
        boolean checkEnabled = sysConfigService.getBooleanValue("sys.supplier.duplicate.check", true);
        if (checkEnabled && duplicateExists) {
            throw new BusinessException("【设置中心-供应商查重】供应商名称[" + supplierName + "]已存在，不允许保存");
        }
    }

    /**
     * 仓库必填校验
     * 当 kgw_warehouse_required 为 true 时，warehouseId 为空则阻止保存。
     */
    public void validateWarehouseRequired(String billTypeName, Long warehouseId) {
        boolean required = sysConfigService.getBooleanValue("kgw_warehouse_required", true);
        if (required && warehouseId == null) {
            throw new BusinessException("【设置中心-仓库必填】" + billTypeName + "需要选择仓库才能保存");
        }
    }

    /**
     * 采购侧 KGW 审批校验
     * 当 kgw_approval_enabled 为 true 且净金额 >= kgw_approval_amount_limit 时，
     * 将单据状态从"草稿"升级为"待审核"。
     *
     * @param billTypeName 单据类型中文名
     * @param status       单据状态（可被修改）
     * @param approvalStatus 审批状态（可被修改）
     * @param netAmount    净金额
     * @return true=已升级为待审核, false=未触发
     */
    public boolean applyKgwApprovalForPurchase(String billTypeName,
                                                java.util.function.Consumer<String> statusSetter,
                                                java.util.function.Consumer<String> approvalStatusSetter,
                                                String currentStatus,
                                                java.math.BigDecimal netAmount) {
        boolean enabled = sysConfigService.getBooleanValue("kgw_approval_enabled", false);
        if (!enabled) return false;
        if (netAmount == null) return false;
        int limit = sysConfigService.getIntValue("kgw_approval_amount_limit", 50000);
        if (netAmount.compareTo(new BigDecimal(limit)) >= 0) {
            if (currentStatus == null || "草稿".equals(currentStatus)) {
                statusSetter.accept("待审核");
                approvalStatusSetter.accept("待审核");
                log.info("【设置中心-KGW审批】{}净金额 {} >= 阈值 {}，已升级为待审核", billTypeName, netAmount, limit);
                return true;
            }
        }
        return false;
    }

    /**
     * 单据号码查重（优先级1.4）
     * <p>
     * 当 sys.bill.no.duplicate.check 为 true 时，若单据号已存在则阻止保存。
     * 调用方需先查询是否存在相同单据号（排除自身ID），将结果传入 duplicateExists。
     * </p>
     *
     * @param billNo         单据号
     * @param billTypeName   单据类型中文名（用于错误提示）
     * @param duplicateExists 是否已存在相同单据号
     */
    public void validateBillNoDuplicate(String billNo, String billTypeName, boolean duplicateExists) {
        boolean checkEnabled = sysConfigService.getBooleanValue("sys.bill.no.duplicate.check", false);
        if (checkEnabled && duplicateExists) {
            throw new BusinessException("【设置中心-单据号查重】" + billTypeName + "单据号[" + billNo + "]已存在，不允许保存");
        }
    }

    /**
     * 允许单价为0校验（优先级1.5）
     * <p>
     * 当配置项为 false 时，若明细行单价为0则阻止保存。
     * 默认值为 true（允许单价为0），与前端 defaultCfg 一致。
     * </p>
     *
     * @param configKey     配置项 key（如 sale.order.allow.zero.price）
     * @param billTypeName   单据类型中文名（用于错误提示）
     * @param unitPrice      明细行单价
     * @param lineNo         行号
     */
    public void validateAllowZeroPrice(String configKey, String billTypeName, BigDecimal unitPrice, int lineNo) {
        boolean allowZero = sysConfigService.getBooleanValue(configKey, true);
        if (!allowZero && unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("【设置中心-单价校验】" + billTypeName + "第" + lineNo + "行单价不能为0");
        }
    }

    // ====================== 优先级2：重要业务规则 ======================

    /**
     * 含税业务-是否启用（优先级2.6）
     */
    public boolean isTaxEnabled() {
        return sysConfigService.getBooleanValue("sys.tax.enabled", false);
    }

    /**
     * 含税业务-税率（优先级2.6）
     * <p>默认税率 13%</p>
     */
    public BigDecimal getTaxRate() {
        String rate = sysConfigService.getValue("sys.tax.rate", "13");
        try {
            return new BigDecimal(rate);
        } catch (NumberFormatException e) {
            log.warn("无效的税率配置: {}，使用默认 13", rate);
            return new BigDecimal("13");
        }
    }

    /**
     * 含税业务开关-税额调整
     * <p>
     * 当 sys.tax.enabled=false 时，将税额清零并重新计算价税合计。
     * 调用方在 recalculateXxx 方法末尾调用此方法，
     * 传入3个setter：税额setter、净额setter、净额计算所需2个值（折后金额+原税额）。
     * </p>
     *
     * @param billTypeName 单据类型名（用于日志）
     * @param taxAmountSetter 税额setter
     * @param netAmountSetter 净额setter
     * @param discountedAmount 折后金额（不含税）
     * @param originalTaxAmount 原税额
     * @param baseTaxSetter 本位币税额setter（可null）
     * @param baseNetSetter 本位币净额setter（可null）
     * @param exchangeRate 汇率
     */
    public void adjustTaxIfDisabled(String billTypeName,
                                     java.util.function.Consumer<BigDecimal> taxAmountSetter,
                                     java.util.function.Consumer<BigDecimal> netAmountSetter,
                                     BigDecimal discountedAmount,
                                     BigDecimal originalTaxAmount) {
        if (isTaxEnabled()) return; // 启用含税，不调整
        // 未启用含税：税额置0，净额=折后金额
        taxAmountSetter.accept(BigDecimal.ZERO.setScale(SCALE));
        BigDecimal newNet = discountedAmount != null ? discountedAmount.setScale(SCALE, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(SCALE);
        netAmountSetter.accept(newNet);
        log.info("【设置中心-含税业务】{}未启用含税业务，税额已置0，价税合计=折后金额={}", billTypeName, newNet);
    }

    /**
     * 含税业务-计税方式（优先级2.6）
     * <p>exclusive=价外税(默认), inclusive=价内税</p>
     */
    public String getTaxMethod() {
        return sysConfigService.getValue("sys.tax.method", "exclusive");
    }

    /**
     * 外币业务-是否启用（设置中心 → 系统参数 → 基础设置）
     * <p>关闭时单据只用本位币记账：汇率强制为 1，币种锁定为本位币。</p>
     */
    public boolean isForeignCurrencyEnabled() {
        return sysConfigService.getBooleanValue("sys.foreign.currency.enabled", false);
    }

    /**
     * 外币业务关闭时把汇率归一为 1（本位币记账）；开启时保留传入值（null 视为 1）。
     */
    public BigDecimal normalizeExchangeRate(BigDecimal rate) {
        if (!isForeignCurrencyEnabled()) return BigDecimal.ONE;
        return rate == null ? BigDecimal.ONE : rate;
    }

    /**
     * 含税业务-默认计税方式（界面口径：含税价 / 不含税价 / 不计税）
     * <p>用于单据表头「计税方式」的默认值；未配置时返回「不含税价」（与历史"一律按不含税算"一致）。</p>
     */
    public String getDefaultTaxMethod() {
        String v = sysConfigService.getValue("sys.tax.method", "不含税价");
        return (v == null || v.isBlank()) ? "不含税价" : v;
    }

    /**
     * 发票金额录入方式（设置中心 → 系统参数 → 发票录入口径）
     * <p>
     * qty_price = 按数量单价（默认，升级前行为：开票金额 = 数量 × 单价）；
     * amount    = 按发票金额（金额说了算：数量单价只读、由金额自动反算，允许分次开票）。
     * 参照金蝶「发票只校验金额」「单价×数量与不含税金额不一致时自动反算单价」。
     * </p>
     */
    public boolean isInvoiceAmountMode() {
        String v = sysConfigService.getValue("sys.invoice.entry.mode", "qty_price");
        return "amount".equalsIgnoreCase(v == null ? "" : v.trim());
    }

    /**
     * 不允许修改单据日期（优先级2.8）
     */
    public boolean isLockBillDate() {
        return sysConfigService.getBooleanValue("sys.lock.bill.date", false);
    }

    /**
     * 锁单据日期校验（优先级2.8）
     * <p>
     * 当 sys.lock.bill.date 为 true 且单据已生效（非草稿）时，
     * 若修改了单据日期则阻止保存。
     * </p>
     *
     * @param billTypeName   单据类型中文名
     * @param oldDate         原单据日期
     * @param newDate         新单据日期
     * @param billStatus      单据状态
     */
    public void validateLockBillDate(String billTypeName,
                                     java.time.LocalDate oldDate,
                                     java.time.LocalDate newDate,
                                     String billStatus) {
        if (!isLockBillDate()) return;
        // 仅已生效单据锁定日期（草稿状态允许修改）
        if (billStatus == null || "草稿".equals(billStatus)) return;
        if (oldDate == null || newDate == null) return;
        if (!oldDate.equals(newDate)) {
            throw new BusinessException("【设置中心-锁单据日期】" + billTypeName + "已生效，不允许修改单据日期");
        }
    }

    // ====================== 优先级3：其他规则 ======================

    /** 超过来源单数量：不控制（完全不校验） */
    public static final String OVER_QTY_NO_CONTROL = "不控制";
    /** 超过来源单数量：预警提示（允许保存，但给用户可见提示） */
    public static final String OVER_QTY_WARN = "预警提示";
    /** 超过来源单数量：不允许（阻断保存） */
    public static final String OVER_QTY_DENY = "不允许";

    /**
     * 超过来源单数量处理策略（优先级3.9）
     * <p>返回值固定为三种：不控制 / 预警提示 / 不允许。</p>
     * <p>兼容历史值：不检测 → 不控制；警告提示 → 预警提示。</p>
     *
     * @param configKey 配置项 key（如 sale.order.over.qty）
     */
    public String getOverQtyPolicy(String configKey) {
        String value = sysConfigService.getValue(configKey, OVER_QTY_WARN);
        if (value == null || value.isBlank()) return OVER_QTY_WARN;
        String policy = value.trim();
        if (OVER_QTY_DENY.equals(policy)) return OVER_QTY_DENY;
        if (OVER_QTY_NO_CONTROL.equals(policy) || "不检测".equals(policy)) return OVER_QTY_NO_CONTROL;
        // 新值「预警提示」+ 历史值「警告提示」都按允许保存并提示处理
        return OVER_QTY_WARN;
    }

    /** 当前配置是否为「不控制」（业务侧可据此跳过整段来源数量计算） */
    public boolean isOverQtyNoControl(String configKey) {
        return OVER_QTY_NO_CONTROL.equals(getOverQtyPolicy(configKey));
    }

    /**
     * 超量策略落地：超量时按三种策略分别处理。
     * <ul>
     *   <li>不控制：直接放行；</li>
     *   <li>不允许：抛业务异常，阻断保存/提交；</li>
     *   <li>预警提示：放行，但通过响应头把用户可见预警带回前端。</li>
     * </ul>
     *
     * @param configKey  配置项 key
     * @param billTypeName 单据类型中文名
     * @param lineNo     行号
     * @param exceeded   是否超量
     * @param detail     超量明细（物料、来源单、各数量等）
     */
    public void applyOverQtyPolicy(String configKey, String billTypeName, int lineNo,
                                   boolean exceeded, String detail) {
        if (!exceeded) return;
        String policy = getOverQtyPolicy(configKey);
        if (OVER_QTY_NO_CONTROL.equals(policy)) return;

        String detailText = (detail == null || detail.isBlank()) ? "" : detail;
        String message = String.format("【设置中心-超量校验】%s第%d行%s", billTypeName, lineNo, detailText);
        if (OVER_QTY_DENY.equals(policy)) {
            throw new BusinessException(message + "，不允许保存");
        }
        log.warn("{}，已预警放行", message);
        RequestWarningUtils.warn(message + "，已预警放行");
    }

    /**
     * 超量数量校验（优先级3.9）
     * <p>数量超过来源单可转数量时，按配置执行 不控制 / 预警提示 / 不允许。</p>
     *
     * @param configKey       配置项 key
     * @param billTypeName     单据类型中文名
     * @param lineNo           行号
     * @param orderQty         本单数量
     * @param availableQty     来源单可转数量
     */
    public void validateOverQty(String configKey, String billTypeName, int lineNo,
                                BigDecimal orderQty, BigDecimal availableQty) {
        if (orderQty == null || availableQty == null) return;
        applyOverQtyPolicy(configKey, billTypeName, lineNo, orderQty.compareTo(availableQty) > 0,
                String.format("本次数量 %s 超过来源单可转数量 %s",
                        formatQty(orderQty), formatQty(availableQty)));
    }

    private static String formatQty(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 是否允许增加来源单货品（优先级3.10）
     *
     * @param configKey 配置项 key（如 sale.order.allow.add.material）
     */
    public boolean isAllowAddMaterial(String configKey) {
        return sysConfigService.getBooleanValue(configKey, true);
    }
}
